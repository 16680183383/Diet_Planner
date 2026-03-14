package com.psh.diet_planner.controller;

import com.psh.diet_planner.dto.AllAspectsResponse;
import com.psh.diet_planner.dto.ApiResponse;
import com.psh.diet_planner.dto.DietQueryRequest;
import com.psh.diet_planner.dto.DietSearchResponse;
import com.psh.diet_planner.dto.RecipeDTO;
import com.psh.diet_planner.dto.RelationExploreRequest;
import com.psh.diet_planner.dto.RelationExploreResponse;
import com.psh.diet_planner.service.DietSearchService;

import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diet")
@RequiredArgsConstructor
public class DietController {

    private final DietSearchService dietSearchService;

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<List<DietSearchResponse>>> analyze(@Valid @RequestBody DietQueryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(dietSearchService.comprehensiveSearch(request), "智能食材分析完成"));
    }

    @PostMapping("/all")
    public ResponseEntity<ApiResponse<AllAspectsResponse>> analyzeAll(@Valid @RequestBody DietQueryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(dietSearchService.fullAnalysis(request), "全维度智能分析完成"));
    }

    @GetMapping("/recipes/similar")
    public ResponseEntity<ApiResponse<List<RecipeDTO>>> similarRecipes(@RequestParam("recipeName") String recipeName) {
        return ResponseEntity.ok(ApiResponse.success(dietSearchService.findSimilarRecipes(recipeName), "相似菜谱查询完成"));
    }

    @PostMapping("/relations/explore")
    public ResponseEntity<ApiResponse<RelationExploreResponse>> explore(@Valid @RequestBody RelationExploreRequest request) {
        return ResponseEntity.ok(ApiResponse.success(dietSearchService.exploreRelations(request), "关联探索完成"));
    }
}
