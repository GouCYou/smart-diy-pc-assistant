package cn.cctstudio.smartdiypcassistantbackend.commerce;

import cn.cctstudio.smartdiypcassistantbackend.commerce.dto.MarketOverviewResponse;
import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketInsightService marketInsightService;

    public MarketController(MarketInsightService marketInsightService) {
        this.marketInsightService = marketInsightService;
    }

    @GetMapping("/overview")
    public ApiResponse<MarketOverviewResponse> overview(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(marketInsightService.overview(principal.userId()));
    }

    @PostMapping("/analyze")
    public ApiResponse<MarketOverviewResponse> analyze(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(marketInsightService.analyze(principal.userId()));
    }

    @PostMapping("/refresh")
    public ApiResponse<MarketOverviewResponse> refresh(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(marketInsightService.refreshTrackedParts(principal.userId()));
    }
}
