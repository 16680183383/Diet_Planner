package com.psh.diet_planner.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RecipeReviewResponse {
    private Long id;
    private Long userId;
    private String recipeId;
    private String recipeName;
    private Integer tasteRating;
    private Integer difficultyRating;
    private Integer nutritionRating;
    private Integer presentationRating;
    private Integer overallRating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
