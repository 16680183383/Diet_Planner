package com.psh.diet_planner.dto;

import lombok.Data;

@Data
public class FeedbackResponse {
    private Long userId;
    private String recipeId;
    private Integer rating;
    private String comments;
    private Long timestamp;
}