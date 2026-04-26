package cn.cctstudio.smartdiypcassistantbackend.commerce;

import cn.cctstudio.smartdiypcassistantbackend.commerce.dto.JdProductResponse;
import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jd-products")
public class JdProductController {

    private final JdProductSearchService jdProductSearchService;

    public JdProductController(JdProductSearchService jdProductSearchService) {
        this.jdProductSearchService = jdProductSearchService;
    }

    @GetMapping("/search")
    public ApiResponse<List<JdProductResponse>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) HardwarePartType type,
            @RequestParam(required = false) Long partId
    ) {
        return ApiResponse.ok(jdProductSearchService.search(keyword, type, partId));
    }
}
