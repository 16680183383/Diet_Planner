package com.psh.diet_planner.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartMealPlanResponse {
    private String mainIngredient;
    private List<String> recommendedPairings;
    private List<String> suggestedRecipes;
    private String nutritionExpertAdvice;
}
