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

import java.io.IOException;
import java.time.LocalDate;
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
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!contentType.startsWith("image/")) {
            throw new BusinessException("只能上传图片文件");
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
            Response response = uploadManager.put(file.getBytes(), key, token);
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

    private boolean hasQiniuConfig() {
        return StringUtils.hasText(properties.accessKey())
                && StringUtils.hasText(properties.secretKey())
                && StringUtils.hasText(properties.bucket())
                && StringUtils.hasText(properties.domain());
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
