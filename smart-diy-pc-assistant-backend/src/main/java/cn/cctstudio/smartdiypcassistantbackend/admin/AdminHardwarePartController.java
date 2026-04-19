package cn.cctstudio.smartdiypcassistantbackend.admin;

import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartService;
import cn.cctstudio.smartdiypcassistantbackend.hardware.dto.HardwarePartRequest;
import cn.cctstudio.smartdiypcassistantbackend.hardware.dto.HardwarePartResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/hardware-parts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminHardwarePartController {

    private final HardwarePartService hardwarePartService;

    public AdminHardwarePartController(HardwarePartService hardwarePartService) {
        this.hardwarePartService = hardwarePartService;
    }

    @GetMapping
    public ApiResponse<List<HardwarePartResponse>> list() {
        return ApiResponse.ok(hardwarePartService.listAdmin());
    }

    @PostMapping
    public ApiResponse<HardwarePartResponse> create(@Valid @RequestBody HardwarePartRequest request) {
        return ApiResponse.ok(hardwarePartService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<HardwarePartResponse> update(@PathVariable Long id, @Valid @RequestBody HardwarePartRequest request) {
        return ApiResponse.ok(hardwarePartService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        hardwarePartService.delete(id);
        return ApiResponse.ok();
    }
}
