package com.psh.diet_planner.service.impl;

import com.psh.diet_planner.dto.UserDTO;
import com.psh.diet_planner.model.UserEntity;
import com.psh.diet_planner.repository.UserJpaRepository;
import com.psh.diet_planner.service.UserService;
import com.psh.diet_planner.util.JwtUtils;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    
    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public UserServiceImpl(UserJpaRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }
    
    @Override
    public UserEntity registerUser(UserDTO userDTO) {
        Optional<UserEntity> exists = userRepository.findByUsername(userDTO.getUsername());
        if (exists.isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(userDTO.getUsername());
        user.setPasswordHash(passwordEncoder.encode(userDTO.getPassword()));
        user.setRole(UserEntity.Role.USER);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }
    
    @Override
    public String login(String username, String password) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole().name());
    }
    
    @Override
    public UserEntity getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }
    
    @Override
    public UserEntity updateUser(Long id, UserDTO userDTO) {
        UserEntity user = userRepository.findById(id).orElse(null);
        if (user != null) {
            // 更新用户信息
            if (userDTO.getNickname() != null) user.setNickname(userDTO.getNickname());
            if (userDTO.getAvatarUrl() != null) user.setAvatarUrl(userDTO.getAvatarUrl());
            if (userDTO.getAge() != null) user.setAge(userDTO.getAge());
            if (userDTO.getGender() != null) user.setGender(userDTO.getGender());
            if (userDTO.getWeight() != null) user.setWeight(userDTO.getWeight());
            if (userDTO.getHeight() != null) user.setHeight(userDTO.getHeight());
            if (userDTO.getPreferences() != null) user.setPreferences(userDTO.getPreferences());
            if (userDTO.getRestrictions() != null) user.setRestrictions(userDTO.getRestrictions());
            if (userDTO.getActivityLevel() != null) user.setActivityLevel(userDTO.getActivityLevel());
            if (userDTO.getGoal() != null) user.setGoal(userDTO.getGoal());
            if (userDTO.getAllergens() != null) user.setAllergens(new ArrayList<>(userDTO.getAllergens()));
            if (userDTO.getIllnesses() != null) user.setIllnesses(new ArrayList<>(userDTO.getIllnesses()));
            if (userDTO.getFlavorPref() != null) user.setFlavorPref(userDTO.getFlavorPref());
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user);
        }
        return null;
    }
    
    @Override
    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @Override
    public List<UserEntity> listAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public void changeRole(Long userId, String role) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        try {
            user.setRole(UserEntity.Role.valueOf(role.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效角色: " + role + "，可选值: ADMIN, USER");
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}