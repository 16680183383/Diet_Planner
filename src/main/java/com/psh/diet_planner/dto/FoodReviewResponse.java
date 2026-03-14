package com.psh.diet_planner.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class FoodReviewResponse {
    private Long id;
    private Long userId;
    private String foodName;
    private Integer tasteRating;
    private Integer nutritionRating;
    private Integer freshnessRating;
    private Integer costRating;
    private Integer overallRating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
