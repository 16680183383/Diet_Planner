package com.psh.diet_planner.dto;

import java.util.List;
import lombok.Data;

/**
 * 登录态同步响应：用户登录后自动加载的历史记忆数据
 */
@Data
public class UserMemoryResponse {
    private Long userId;
    private String username;

    // ========== 用户基础信息 ==========
    private Integer age;
    private String gender;
    private Double weight;
    private Double height;
    private String activityLevel;
    private String goal;
    private String flavorPref;
    private String preferences;
    private String restrictions;

    // ========== 过敏原 & 疾病（关键安全字段） ==========
    private List<String> allergens;
    private List<String> illnesses;

    // ========== 食材偏好 ==========
    private List<IngredientPrefItem> ingredientPreferences;
    // ========== 菜谱偏好 ==========
    private List<RecipePrefItem> recipePreferences;
    // ========== 食材评价历史 ==========
    private List<FoodReviewResponse> foodReviews;
    // ========== 菜谱评价历史 ==========
    private List<RecipeReviewResponse> recipeReviews;

    // ========== 统计摘要 ==========
    private int totalFoodReviews;
    private int totalRecipeReviews;
    private int totalIngredientPrefs;
    private int totalRecipePrefs;

    @Data
    public static class IngredientPrefItem {
        private String ingredientName;
        private String preference;
        private String reason;
    }

    @Data
    public static class RecipePrefItem {
        private String recipeName;
        private String preference;
        private String reason;
    }
}
