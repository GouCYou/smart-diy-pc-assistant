package cn.cctstudio.smartdiypcassistantbackend.file;

import cn.cctstudio.smartdiypcassistantbackend.config.properties.QiniuProperties;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.file.dto.FileUploadResponse;
import cn.cctstudio.smartdiypcassistantbackend.file.dto.UploadTokenResponse;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class QiniuService {

    private final QiniuProperties properties;
    private final MediaUrlService mediaUrlService;

    public QiniuService(QiniuProperties properties, MediaUrlService mediaUrlService) {
        this.properties = properties;
        this.mediaUrlService = mediaUrlService;
    }

    public UploadTokenResponse createUploadToken(String filename) {
        if (!StringUtils.hasText(properties.accessKey())
                || !StringUtils.hasText(properties.secretKey())
                || !StringUtils.hasText(properties.bucket())
                || !StringUtils.hasText(properties.domain())) {
            throw new BusinessException("七牛云对象存储尚未配置完整");
        }
        String key = "uploads/%s/%s-%s".formatted(LocalDate.now(), UUID.randomUUID(), sanitize(filename));
        String token = Auth.create(properties.accessKey(), properties.secretKey())
                .uploadToken(properties.bucket(), key, 3600, null);
        return new UploadTokenResponse(token, key, properties.domain(), uploadUrl());
    }

    public FileUploadResponse upload(MultipartFile file) {
        return upload(file, "");
    }

    public FileUploadResponse upload(MultipartFile file, String usage) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!contentType.startsWith("image/")) {
            throw new BusinessException("只能上传图片文件");
        }
        if (isAvatarUsage(usage) && isGif(file, contentType)) {
            throw new BusinessException("头像不支持上传动图，请选择 JPG、PNG 或 WebP 等静态图片");
        }
        if (!hasQiniuConfig()) {
            throw new BusinessException("对象存储尚未配置完整，不能上传图片");
        }

        String key = "uploads/%s/%s-%s".formatted(
                LocalDate.now(),
                UUID.randomUUID(),
                sanitize(file.getOriginalFilename() == null ? "avatar.jpg" : file.getOriginalFilename())
        );
        String token = Auth.create(properties.accessKey(), properties.secretKey())
                .uploadToken(properties.bucket(), key, 3600, null);
        UploadManager uploadManager = new UploadManager(new Configuration(Region.autoRegion()));
        try {
            byte[] uploadBytes = optimizeImageBytes(file.getBytes(), contentType);
            Response response = uploadManager.put(uploadBytes, key, token);
            if (!response.isOK()) {
                throw new BusinessException("七牛云上传失败：" + response.statusCode + " " + response.error);
            }
            return new FileUploadResponse(mediaUrlService.toPublicUrl(key), key, properties.domain());
        } catch (QiniuException exception) {
            Response response = exception.response;
            String detail = response == null ? exception.getMessage() : response.statusCode + " " + response.error;
            throw new BusinessException("七牛云上传失败：" + detail);
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败");
        }
    }

    public FileUploadResponse uploadNewsImage(byte[] bytes,String mime) {
        if(!hasQiniuConfig() || bytes.length==0 || bytes.length>5000000 || !mime.matches("image/(jpeg|jpg|png|gif|webp|avif)"))throw new BusinessException("资讯图片无效或对象存储未配置");
        String key="news-import/"+UUID.randomUUID()+"."+mime.substring(6).replace("jpeg","jpg");
        String token=Auth.create(properties.accessKey(),properties.secretKey()).uploadToken(properties.bucket(),key,3600,null);
        String boundary="SmartDIY"+UUID.randomUUID();
        String prefix="--"+boundary+"\r\nContent-Disposition: form-data; name=\"key\"\r\n\r\n"+key+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"token\"\r\n\r\n"+token+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"image\"\r\nContent-Type: "+mime+"\r\n\r\n";
        try {
            var client=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
            var body=java.net.http.HttpRequest.BodyPublishers.ofByteArrays(java.util.List.of(prefix.getBytes(java.nio.charset.StandardCharsets.UTF_8),bytes,("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(uploadUrl().replaceFirst("^http:","https:"))).timeout(java.time.Duration.ofSeconds(45)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(body).build();
            var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()!=200)throw new BusinessException("资讯图片上传失败 HTTP "+response.statusCode());
            return new FileUploadResponse(mediaUrlService.toPublicUrl(key),key,properties.domain());
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new BusinessException("资讯图片上传中断");}
        catch(IOException e){throw new BusinessException("资讯图片上传连接失败");}
    }
    public void deleteNewsImage(String key) {
        if(!key.matches("news-import/[a-zA-Z0-9.-]+"))throw new BusinessException("非资讯专属对象不能自动删除");
        try {Configuration config=new Configuration(Region.region2());config.useHttpsDomains=true;new com.qiniu.storage.BucketManager(Auth.create(properties.accessKey(),properties.secretKey()),config).delete(properties.bucket(),key);}
        catch(QiniuException e){if(e.response==null || e.response.statusCode!=612)throw new BusinessException("资讯对象删除失败，稍后重试");}
    }

    private boolean hasQiniuConfig() {
        return StringUtils.hasText(properties.accessKey())
                && StringUtils.hasText(properties.secretKey())
                && StringUtils.hasText(properties.bucket())
                && StringUtils.hasText(properties.domain());
    }

    private boolean isAvatarUsage(String usage) {
        return StringUtils.hasText(usage) && "avatar".equalsIgnoreCase(usage.trim());
    }

    private boolean isGif(MultipartFile file, String contentType) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        return "image/gif".equalsIgnoreCase(contentType) || filename.endsWith(".gif");
    }

    private byte[] optimizeImageBytes(byte[] original, String contentType) {
        if (!isJpeg(contentType)) {
            return original;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
            if (image == null) {
                return original;
            }
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                return original;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageWriter writer = writers.next();
            try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(imageOutput);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.88f);
                }
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
            byte[] compressed = output.toByteArray();
            return compressed.length > 0 && compressed.length < original.length ? compressed : original;
        } catch (IOException ignored) {
            return original;
        }
    }

    private boolean isJpeg(String contentType) {
        return "image/jpeg".equalsIgnoreCase(contentType) || "image/jpg".equalsIgnoreCase(contentType);
    }

    private String uploadUrl() {
        if (StringUtils.hasText(properties.uploadUrl())) {
            return properties.uploadUrl();
        }
        return "https://upload-z2.qiniup.com";
    }

    private String sanitize(String filename) {
        String sanitized = filename.replace("\\", "_")
                .replace("/", "_")
                .replace("..", "_")
                .trim();
        return StringUtils.hasText(sanitized) ? sanitized : "image.jpg";
    }
}
