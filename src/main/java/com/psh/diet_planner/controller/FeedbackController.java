package com.psh.diet_planner.controller;

import com.psh.diet_planner.dto.ApiResponse;
import com.psh.diet_planner.dto.FoodReviewRequest;
import com.psh.diet_planner.dto.FoodReviewResponse;
import com.psh.diet_planner.dto.RecipeReviewRequest;
import com.psh.diet_planner.dto.RecipeReviewResponse;
import com.psh.diet_planner.service.ReviewService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class FeedbackController {

    private final ReviewService reviewService;

    public FeedbackController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // ==================== 食材评价 ====================

    @PostMapping("/food")
    public ApiResponse<FoodReviewResponse> submitFoodReview(@RequestBody FoodReviewRequest request) {
        try {
            FoodReviewResponse response = reviewService.submitFoodReview(request);
            return ApiResponse.success(response, "食材评价提交成功");
        } catch (Exception e) {
            return ApiResponse.error("食材评价提交失败: " + e.getMessage());
        }
    }

    @PutMapping("/food/{reviewId}")
    public ApiResponse<FoodReviewResponse> updateFoodReview(@PathVariable Long reviewId,
                                                            @RequestBody FoodReviewRequest request) {
        try {
            FoodReviewResponse response = reviewService.updateFoodReview(reviewId, request);
            return ApiResponse.success(response, "食材评价更新成功");
        } catch (Exception e) {
            return ApiResponse.error("食材评价更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/food/{reviewId}")
    public ApiResponse<Void> deleteFoodReview(@PathVariable Long reviewId) {
        try {
            reviewService.deleteFoodReview(reviewId);
            return ApiResponse.success(null, "食材评价删除成功");
        } catch (Exception e) {
            return ApiResponse.error("食材评价删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/food/user")
    public ApiResponse<List<FoodReviewResponse>> getFoodReviewsByUser(@RequestParam Long userId) {
        return ApiResponse.success(reviewService.getFoodReviewsByUser(userId), "获取用户食材评价成功");
    }

    @GetMapping("/food/item")
    public ApiResponse<List<FoodReviewResponse>> getFoodReviewsByFood(@RequestParam String foodName) {
        return ApiResponse.success(reviewService.getFoodReviewsByFood(foodName), "获取食材评价成功");
    }

    @GetMapping("/food/detail")
    public ApiResponse<FoodReviewResponse> getUserFoodReview(@RequestParam Long userId,
                                                             @RequestParam String foodName) {
        FoodReviewResponse resp = reviewService.getUserFoodReview(userId, foodName);
        if (resp != null) {
            return ApiResponse.success(resp, "获取评价成功");
        }
        return ApiResponse.error("未找到该评价");
    }

    // ==================== 菜谱评价 ====================

    @PostMapping("/recipe")
    public ApiResponse<RecipeReviewResponse> submitRecipeReview(@RequestBody RecipeReviewRequest request) {
        try {
            RecipeReviewResponse response = reviewService.submitRecipeReview(request);
            return ApiResponse.success(response, "菜谱评价提交成功");
        } catch (Exception e) {
            return ApiResponse.error("菜谱评价提交失败: " + e.getMessage());
        }
    }

    @PutMapping("/recipe/{reviewId}")
    public ApiResponse<RecipeReviewResponse> updateRecipeReview(@PathVariable Long reviewId,
                                                                @RequestBody RecipeReviewRequest request) {
        try {
            RecipeReviewResponse response = reviewService.updateRecipeReview(reviewId, request);
            return ApiResponse.success(response, "菜谱评价更新成功");
        } catch (Exception e) {
            return ApiResponse.error("菜谱评价更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/recipe/{reviewId}")
    public ApiResponse<Void> deleteRecipeReview(@PathVariable Long reviewId) {
        try {
            reviewService.deleteRecipeReview(reviewId);
            return ApiResponse.success(null, "菜谱评价删除成功");
        } catch (Exception e) {
            return ApiResponse.error("菜谱评价删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/recipe/user")
    public ApiResponse<List<RecipeReviewResponse>> getRecipeReviewsByUser(@RequestParam Long userId) {
        return ApiResponse.success(reviewService.getRecipeReviewsByUser(userId), "获取用户菜谱评价成功");
    }

    @GetMapping("/recipe/item")
    public ApiResponse<List<RecipeReviewResponse>> getRecipeReviewsByRecipe(@RequestParam String recipeName) {
        return ApiResponse.success(reviewService.getRecipeReviewsByRecipe(recipeName), "获取菜谱评价成功");
    }

    @GetMapping("/recipe/detail")
    public ApiResponse<RecipeReviewResponse> getUserRecipeReview(@RequestParam Long userId,
                                                                 @RequestParam String recipeName) {
        RecipeReviewResponse resp = reviewService.getUserRecipeReview(userId, recipeName);
        if (resp != null) {
            return ApiResponse.success(resp, "获取评价成功");
        }
        return ApiResponse.error("未找到该评价");
    }
}