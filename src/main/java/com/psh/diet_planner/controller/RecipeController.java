package com.psh.diet_planner.controller;

import com.psh.diet_planner.dto.ApiResponse;
import com.psh.diet_planner.dto.RecipeListResponse;
import com.psh.diet_planner.dto.RecipeResponse;
import com.psh.diet_planner.model.Food;
import com.psh.diet_planner.model.Recipe;
import com.psh.diet_planner.service.RecipeService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {
    
    @Autowired
    private RecipeService recipeService;
    
    @GetMapping("/recommend")
    public ApiResponse<RecipeListResponse> recommendRecipes(
            @RequestParam String userId,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        List<Recipe> recipes = recipeService.recommendPathBased(userId, limit);
        RecipeListResponse response = new RecipeListResponse();
        response.setRecipes(recipes.stream().map(this::toRecipeResponse).collect(Collectors.toList()));
        return ApiResponse.success(response, "菜谱推荐成功");
    }

    @GetMapping("/recommend/path")
    public ApiResponse<RecipeListResponse> recommendPathBased(
            @RequestParam String userId,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        List<Recipe> recipes = recipeService.recommendPathBased(userId, limit);
        RecipeListResponse response = new RecipeListResponse();
        response.setRecipes(recipes.stream().map(this::toRecipeResponse).collect(Collectors.toList()));
        return ApiResponse.success(response, "基于路径的菜谱推荐成功");
    }

    @GetMapping("/recommend/embedding")
    public ApiResponse<RecipeListResponse> recommendEmbeddingBased(
            @RequestParam String userId,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "searchLimit", defaultValue = "50") int searchLimit) {
        List<Recipe> recipes = recipeService.recommendEmbeddingBased(userId, limit, searchLimit);
        RecipeListResponse response = new RecipeListResponse();
        response.setRecipes(recipes.stream().map(this::toRecipeResponse).collect(Collectors.toList()));
        return ApiResponse.success(response, "基于向量相似度的菜谱推荐成功");
    }
    
    @GetMapping("/search")
    public ApiResponse<RecipeListResponse> searchRecipes(@RequestParam String keyword) {
        List<Recipe> recipes = recipeService.searchRecipes(keyword);
        RecipeListResponse response = new RecipeListResponse();
        response.setRecipes(recipes.stream().map(this::toRecipeResponse).collect(Collectors.toList()));
        return ApiResponse.success(response, "菜谱搜索成功");
    }
    
    @GetMapping("/detail")
    public ApiResponse<RecipeResponse> getRecipe(@RequestParam String id) {
        Recipe recipe = recipeService.getRecipeById(id);
        if (recipe != null) {
            RecipeResponse response = toRecipeResponse(recipe);
            return ApiResponse.success(response, "获取菜谱详情成功");
        } else {
            return ApiResponse.error("菜谱不存在");
        }
    }
    
    @GetMapping("/history")
    public ApiResponse<RecipeListResponse> getRecommendationHistory(@RequestParam("userId") Long userId) {
        List<Recipe> recipes = recipeService.getRecommendationHistory(userId);
        RecipeListResponse response = new RecipeListResponse();
        response.setRecipes(recipes.stream().map(this::toRecipeResponse).collect(Collectors.toList()));
        return ApiResponse.success(response, "获取推荐历史成功");
    }
    
    private RecipeResponse toRecipeResponse(Recipe recipe) {
        RecipeResponse response = new RecipeResponse();
        // 现有 Neo4j Recipe 节点以 name 属性为业务主键，id 为 Spring Data 生成的 UUID（旧数据可能为 null）
        // 统一用 name 作为前端可用的菜谱标识符
        response.setRecipeId(recipe.getName());
        response.setName(recipe.getName());
        response.setIngredients(recipe.getIngredients());
        response.setNutritionInfo(recipe.getDetailedIngredients());
        response.setSteps(recipe.getSteps());
        return response;
    }
}