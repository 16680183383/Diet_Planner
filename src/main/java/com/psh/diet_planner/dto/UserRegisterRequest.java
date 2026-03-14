package com.psh.diet_planner.dto;

import lombok.Data;

@Data
public class UserRegisterRequest {
    private String username;
    private String password;
}