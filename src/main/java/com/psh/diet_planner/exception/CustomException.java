package com.psh.diet_planner.exception;

public class CustomException extends RuntimeException {
    
    private int errorCode;
    
    public CustomException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public int getErrorCode() {
        return errorCode;
    }
}