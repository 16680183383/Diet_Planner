package com.psh.diet_planner.dto;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private T data;
    private String message;
    private boolean success;
    private String aiAnalysis;
    
    public ApiResponse() {}
    
    public ApiResponse(T data, String message, boolean success) {
        this.data = data;
        this.message = message;
        this.success = success;
    }
    
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(data, message, true);
    }

    public static <T> ApiResponse<T> successWithAi(T data, String message, String aiAnalysis) {
        ApiResponse<T> r = new ApiResponse<>(data, message, true);
        r.setAiAnalysis(aiAnalysis);
        return r;
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(null, message, false);
    }
}