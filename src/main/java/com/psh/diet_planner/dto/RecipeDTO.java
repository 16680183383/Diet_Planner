package com.psh.diet_planner.dto;

import lombok.Data;

@Data
public class RecipeDTO {
    private String recipeId;
    private String name;
    private String steps;
}