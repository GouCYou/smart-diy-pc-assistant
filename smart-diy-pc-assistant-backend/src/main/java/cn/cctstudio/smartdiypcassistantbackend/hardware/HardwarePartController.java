package cn.cctstudio.smartdiypcassistantbackend.hardware;

import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.hardware.dto.HardwarePartResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hardware-parts")
public class HardwarePartController {

    private final HardwarePartService hardwarePartService;

    public HardwarePartController(HardwarePartService hardwarePartService) {
        this.hardwarePartService = hardwarePartService;
    }

    @GetMapping
    public ApiResponse<List<HardwarePartResponse>> list(
            @RequestParam(required = false) HardwarePartType type,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(hardwarePartService.list(type, keyword));
    }
}
