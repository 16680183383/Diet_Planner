package com.psh.diet_planner.controller;

import com.psh.diet_planner.dto.*;
import com.psh.diet_planner.model.UserEntity;
import com.psh.diet_planner.repository.UserJpaRepository;
import com.psh.diet_planner.service.ReviewService;
import com.psh.diet_planner.service.UserService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private ReviewService reviewService;
    
    @PostMapping("/register")
    public ApiResponse<RegisterResponse> registerUser(@RequestBody UserRegisterRequest userRegisterRequest) {
        try {
            UserDTO userDTO = new UserDTO();
            userDTO.setUsername(userRegisterRequest.getUsername());
            userDTO.setPassword(userRegisterRequest.getPassword());
            
            UserEntity user = userService.registerUser(userDTO);
            RegisterResponse response = new RegisterResponse();
            response.setUserId(user.getId());
            response.setMessage("用户注册成功");
            response.setSuccess(true);
            return new ApiResponse<>(response, "用户注册成功", true);
        } catch (Exception e) {
            return ApiResponse.error("用户注册失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody UserDTO userDTO) {
        try {
            String token = userService.login(userDTO.getUsername(), userDTO.getPassword());
            UserEntity user = userJpaRepository.findByUsername(userDTO.getUsername()).orElse(null);
            LoginResponse response = new LoginResponse();
            response.setToken(token);
            if (user != null) {
                response.setUserId(user.getId());
                response.setUsername(user.getUsername());
                response.setRole(user.getRole().name());
                response.setNickname(user.getNickname());
                response.setAvatarUrl(user.getAvatarUrl());
                // 登录态同步：自动从 MySQL 读取历史记忆数据
                try {
                    response.setMemory(reviewService.loadUserMemory(user.getId()));
                } catch (Exception ignored) {
                    // 记忆加载失败不影响登录
                }
            }
            response.setMessage("登录成功");
            return new ApiResponse<>(response, "登录成功", true);
        } catch (Exception e) {
            return ApiResponse.error("登录失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/detail")
    public ApiResponse<UserEntity> getUser(@RequestParam Long id) {
        UserEntity user = userService.getUserById(id);
        if (user != null) {
            return ApiResponse.success(user, "获取用户信息成功");
        } else {
            return ApiResponse.error("用户不存在");
        }
    }
    
    @PutMapping("/update")
    public ApiResponse<MessageResponse> updateUser(@RequestParam Long id, @RequestBody UserUpdateRequest userUpdateRequest) {
        try {
            // 校验：只允许当前用户修改自身、或管理员修改任何人
            if (!isOwnerOrAdmin(id)) {
                return ApiResponse.error("无权修改他人资料");
            }
            UserDTO userDTO = new UserDTO();
            userDTO.setAge(userUpdateRequest.getAge());
            userDTO.setGender(userUpdateRequest.getGender());
            userDTO.setWeight(userUpdateRequest.getWeight());
            userDTO.setHeight(userUpdateRequest.getHeight());
            userDTO.setPreferences(userUpdateRequest.getPreferences());
            userDTO.setRestrictions(userUpdateRequest.getRestrictions());
            userDTO.setActivityLevel(userUpdateRequest.getActivityLevel());
            userDTO.setGoal(userUpdateRequest.getGoal());
            userDTO.setAllergens(userUpdateRequest.getAllergens());
            userDTO.setIllnesses(userUpdateRequest.getIllnesses());
            userDTO.setFlavorPref(userUpdateRequest.getFlavorPref());
            userDTO.setNickname(userUpdateRequest.getNickname());
            
            UserEntity user = userService.updateUser(id, userDTO);
            if (user != null) {
                MessageResponse response = new MessageResponse();
                response.setMessage("用户信息更新成功");
                response.setSuccess(true);
                return new ApiResponse<>(response, "用户信息更新成功", true);
            } else {
                return ApiResponse.error("用户不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("用户信息更新失败: " + e.getMessage());
        }
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/delete")
    public ApiResponse<MessageResponse> deleteUser(@RequestParam Long id) {
        try {
            boolean result = userService.deleteUser(id);
            if (result) {
                MessageResponse response = new MessageResponse();
                response.setMessage("用户删除成功");
                response.setSuccess(true);
                return new ApiResponse<>(response, "用户删除成功", true);
            } else {
                return ApiResponse.error("用户不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("用户删除失败: " + e.getMessage());
        }
    }

    /**
     * 登录态同步：用户登录后自动加载历史记忆数据
     * 包含食材/菜谱偏好 + 食材/菜谱评价历史 + 统计摘要
     */
    @GetMapping("/memory")
    public ApiResponse<UserMemoryResponse> getUserMemory(@RequestParam Long userId) {
        try {
            UserMemoryResponse memory = reviewService.loadUserMemory(userId);
            return ApiResponse.success(memory, "用户记忆数据加载成功");
        } catch (Exception e) {
            return ApiResponse.error("加载用户记忆数据失败: " + e.getMessage());
        }
    }

    // ==================== 管理员专属接口 ====================

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/list")
    public ApiResponse<List<UserEntity>> listAllUsers() {
        List<UserEntity> users = userService.listAllUsers();
        return ApiResponse.success(users, "获取用户列表成功");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/role")
    public ApiResponse<MessageResponse> changeUserRole(@RequestParam Long userId,
                                                       @RequestParam String role) {
        try {
            userService.changeRole(userId, role);
            MessageResponse response = new MessageResponse();
            response.setMessage("用户角色已更新为: " + role);
            response.setSuccess(true);
            return ApiResponse.success(response, "角色更新成功");
        } catch (Exception e) {
            return ApiResponse.error("角色更新失败: " + e.getMessage());
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/detail")
    public ApiResponse<UserEntity> getAnyUser(@RequestParam Long id) {
        UserEntity user = userService.getUserById(id);
        if (user != null) {
            return ApiResponse.success(user, "获取用户信息成功");
        }
        return ApiResponse.error("用户不存在");
    }

    /** 判断当前登录用户是否是目标 userId 的所有者，或拥有 ADMIN 角色 */
    private boolean isOwnerOrAdmin(Long targetId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        // 管理员直接放行
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) return true;
        // 普通用户：用 JWT 中的 username 反查 id，比对
        String currentUsername = auth.getName();
        return userJpaRepository.findByUsername(currentUsername)
                .map(u -> u.getId().equals(targetId))
                .orElse(false);
    }
}