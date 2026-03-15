package com.psh.diet_planner.dto;

import lombok.Data;

@Data
public class PreferenceRequest {
    private String userId;
    private String recipeName;
    private String ingredientName;
    private String reason;
}
