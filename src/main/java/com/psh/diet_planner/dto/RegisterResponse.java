package com.psh.diet_planner.dto;

import lombok.Data;

@Data
public class RegisterResponse {
    private Long userId;
    private String message;
    private boolean success;
}