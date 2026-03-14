package com.psh.diet_planner.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String role;
    private String nickname;
    private String avatarUrl;
    private String message;
    /** 登录态同步：用户登录后自动加载的历史记忆摘要 */
    private UserMemoryResponse memory;
}