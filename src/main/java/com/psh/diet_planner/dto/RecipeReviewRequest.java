package com.psh.diet_planner.dto;

import lombok.Data;

@Data
public class RecipeReviewRequest {
    private Long userId;
    private String recipeId;
    private String recipeName;
    private Integer tasteRating;
    private Integer difficultyRating;
    private Integer nutritionRating;
    private Integer presentationRating;
    private Integer overallRating;
    private String comment;
}
