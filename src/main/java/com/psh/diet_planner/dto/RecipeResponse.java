package com.psh.diet_planner.dto;

import lombok.Data;
import java.util.List;

@Data
public class RecipeResponse {
    private String recipeId;
    private String name;
    private String ingredients;
    private String nutritionInfo;
    private String steps;
    private List<RecipeResponse> relatedRecipes;
}