package com.psh.diet_planner.service;

import com.psh.diet_planner.dto.RecipeDTO;
import com.psh.diet_planner.model.Recipe;

import java.util.List;

public interface RecipeService {
    List<Recipe> recommendRecipes(RecipeDTO recipeDTO);
    List<Recipe> recommendPathBased(String userId, int limit);
    List<Recipe> recommendEmbeddingBased(String userId, int limit, int searchLimit);
    List<Recipe> searchRecipes(String keyword);
    Recipe getRecipeById(String id);
    List<Recipe> getRecommendationHistory(Long userId);
}