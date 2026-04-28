package cn.cctstudio.smartdiypcassistantbackend.admin;

import cn.cctstudio.smartdiypcassistantbackend.ai.AiSettingsService;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.AiSettingsRequest;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.AiSettingsResponse;
import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai-settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiSettingsController {

    private final AiSettingsService aiSettingsService;

    public AdminAiSettingsController(AiSettingsService aiSettingsService) {
        this.aiSettingsService = aiSettingsService;
    }

    @GetMapping
    public ApiResponse<AiSettingsResponse> get() {
        return ApiResponse.ok(aiSettingsService.get());
    }

    @PutMapping
    public ApiResponse<AiSettingsResponse> update(@Valid @RequestBody AiSettingsRequest request) {
        return ApiResponse.ok(aiSettingsService.update(request));
    }
}
