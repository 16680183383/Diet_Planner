package com.psh.diet_planner.dto;

import lombok.Data;

@Data
public class PreferenceRequest {
    private String userId;
    private String recipeId;
    private String ingredientName;
    private String reason;
}
