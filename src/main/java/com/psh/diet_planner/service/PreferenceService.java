package com.psh.diet_planner.service;

import com.psh.diet_planner.model.IngredientPreference;
import com.psh.diet_planner.model.RecipePreference;
import java.util.List;
import java.util.Map;

public interface PreferenceService {
    void likeRecipe(String userId, String recipeName);
    void dislikeRecipe(String userId, String recipeName);
    void favoriteIngredient(String userId, String ingredientName);
    void dislikeIngredient(String userId, String ingredientName, String reason);

    /** 返回用户已保存的所有菜谱和食材偏好 */
    Map<String, Object> getUserPreferences(String userId);
}
