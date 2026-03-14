package com.psh.diet_planner.dto;

import lombok.Data;

@Data
public class FoodReviewRequest {
    private Long userId;
    private String foodName;
    private Integer tasteRating;
    private Integer nutritionRating;
    private Integer freshnessRating;
    private Integer costRating;
    private Integer overallRating;
    private String comment;
}
