package com.psh.diet_planner.service;

import com.psh.diet_planner.dto.FoodReviewRequest;
import com.psh.diet_planner.dto.FoodReviewResponse;
import com.psh.diet_planner.dto.RecipeReviewRequest;
import com.psh.diet_planner.dto.RecipeReviewResponse;
import com.psh.diet_planner.dto.UserMemoryResponse;
import java.util.List;

public interface ReviewService {

    // ========== 食材评价 ==========
    FoodReviewResponse submitFoodReview(FoodReviewRequest request);
    FoodReviewResponse updateFoodReview(Long reviewId, FoodReviewRequest request);
    void deleteFoodReview(Long reviewId);
    List<FoodReviewResponse> getFoodReviewsByUser(Long userId);
    List<FoodReviewResponse> getFoodReviewsByFood(String foodName);
    FoodReviewResponse getUserFoodReview(Long userId, String foodName);

    // ========== 菜谱评价 ==========
    RecipeReviewResponse submitRecipeReview(RecipeReviewRequest request);
    RecipeReviewResponse updateRecipeReview(Long reviewId, RecipeReviewRequest request);
    void deleteRecipeReview(Long reviewId);
    List<RecipeReviewResponse> getRecipeReviewsByUser(Long userId);
    List<RecipeReviewResponse> getRecipeReviewsByRecipe(String recipeId);
    RecipeReviewResponse getUserRecipeReview(Long userId, String recipeId);

    // ========== 登录态同步：加载用户历史记忆 ==========
    UserMemoryResponse loadUserMemory(Long userId);
}
