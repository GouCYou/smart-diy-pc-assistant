package cn.cctstudio.smartdiypcassistantbackend.file;

import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.file.dto.FileUploadResponse;
import cn.cctstudio.smartdiypcassistantbackend.file.dto.UploadTokenRequest;
import cn.cctstudio.smartdiypcassistantbackend.file.dto.UploadTokenResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final QiniuService qiniuService;

    public FileController(QiniuService qiniuService) {
        this.qiniuService = qiniuService;
    }

    @PostMapping("/upload-token")
    public ApiResponse<UploadTokenResponse> uploadToken(@Valid @RequestBody UploadTokenRequest request) {
        return ApiResponse.ok(qiniuService.createUploadToken(request.filename()));
    }

    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "usage", required = false) String usage
    ) {
        return ApiResponse.ok(qiniuService.upload(file, usage));
    }
}
