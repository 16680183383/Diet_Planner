package com.psh.diet_planner.controller;

import com.psh.diet_planner.dto.ApiResponse;
import com.psh.diet_planner.dto.CreativeCookingRequest;
import com.psh.diet_planner.dto.CreativeCookingResponse;
import com.psh.diet_planner.dto.FoodPairingRequest;
import com.psh.diet_planner.dto.PairingInsightResponse;
import com.psh.diet_planner.dto.PairingResponse;
import com.psh.diet_planner.dto.SafetyCheckRequest;
import com.psh.diet_planner.dto.SafetyCheckResponse;
import com.psh.diet_planner.dto.SemanticSearchResponse;
import com.psh.diet_planner.dto.SmartMealPlanRequest;
import com.psh.diet_planner.dto.SmartMealPlanResponse;
import com.psh.diet_planner.service.FoodGraphService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FoodGraphController {

    private final FoodGraphService foodGraphService;

    @GetMapping("/food/search/semantic")
    public ResponseEntity<ApiResponse<List<SemanticSearchResponse>>> semanticSearch(
            @RequestParam("name") String foodName,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        List<SemanticSearchResponse> data = foodGraphService.searchSemanticSubstitutes(foodName, limit);
        return ResponseEntity.ok(ApiResponse.success(data, "语义替换结果生成成功"));
    }

    @PostMapping("/food/pairing")
    public ResponseEntity<ApiResponse<PairingResponse>> smartPairing(@Valid @RequestBody FoodPairingRequest request) {
        PairingResponse response = foodGraphService.recommendPairings(request);
        return ResponseEntity.ok(ApiResponse.success(response, "智能配伍生成成功"));
    }

    @PostMapping("/food/pairing/detail")
    public ResponseEntity<ApiResponse<PairingInsightResponse>> smartPairingWithReason(
            @Valid @RequestBody FoodPairingRequest request) {
        PairingInsightResponse response = foodGraphService.recommendPairingsWithReason(request);
        return ResponseEntity.ok(ApiResponse.success(response, "智能搭档详单生成成功"));
    }

    @PostMapping("/recommend/meal-plan")
    public ResponseEntity<ApiResponse<SmartMealPlanResponse>> mealPlan(@Valid @RequestBody SmartMealPlanRequest request) {
        SmartMealPlanResponse response = foodGraphService.generateMealPlan(request);
        return ResponseEntity.ok(ApiResponse.success(response, "智能组菜方案已生成"));
    }

    @PostMapping("/safety/check")
    public ResponseEntity<ApiResponse<SafetyCheckResponse>> safetyCheck(@Valid @RequestBody SafetyCheckRequest request) {
        SafetyCheckResponse response = foodGraphService.checkSafety(request);
        return ResponseEntity.ok(ApiResponse.success(response, "安全禁忌检测完成"));
    }

    @PostMapping("/recommend/creative")
    public ResponseEntity<ApiResponse<CreativeCookingResponse>> creative(@Valid @RequestBody CreativeCookingRequest request) {
        CreativeCookingResponse response = foodGraphService.createCreativeIdea(request);
        return ResponseEntity.ok(ApiResponse.success(response, "创意菜谱生成成功"));
    }
}
