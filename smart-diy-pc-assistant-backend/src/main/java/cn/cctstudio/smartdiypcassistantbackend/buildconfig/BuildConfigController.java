package cn.cctstudio.smartdiypcassistantbackend.buildconfig;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigRequest;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.FavoriteRequest;
import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/build-configs")
public class BuildConfigController {

    private final BuildConfigService buildConfigService;

    public BuildConfigController(BuildConfigService buildConfigService) {
        this.buildConfigService = buildConfigService;
    }

    @GetMapping
    public ApiResponse<List<BuildConfigResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "mine") String segment
    ) {
        return ApiResponse.ok(buildConfigService.list(principal.userId(), segment));
    }

    @GetMapping("/public/{id}")
    public ApiResponse<BuildConfigResponse> publicDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        Long viewerUserId = principal == null ? null : principal.userId();
        return ApiResponse.ok(buildConfigService.getPublic(viewerUserId, id));
    }

    @GetMapping("/{id}")
    public ApiResponse<BuildConfigResponse> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(buildConfigService.get(principal.userId(), id));
    }

    @PostMapping
    public ApiResponse<BuildConfigResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BuildConfigRequest request
    ) {
        return ApiResponse.ok(buildConfigService.create(principal.userId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<BuildConfigResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody BuildConfigRequest request
    ) {
        return ApiResponse.ok(buildConfigService.update(principal.userId(), id, request));
    }

    @PatchMapping("/{id}/favorite")
    public ApiResponse<BuildConfigResponse> favorite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody FavoriteRequest request
    ) {
        return ApiResponse.ok(buildConfigService.updateFavorite(principal.userId(), id, request.favorite()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        buildConfigService.delete(principal.userId(), id);
        return ApiResponse.ok();
    }
}
