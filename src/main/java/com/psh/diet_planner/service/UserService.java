package com.psh.diet_planner.service;

import com.psh.diet_planner.dto.UserDTO;
import com.psh.diet_planner.model.UserEntity;
import java.util.List;

public interface UserService {
    UserEntity registerUser(UserDTO userDTO);
    String login(String username, String password);
    UserEntity getUserById(Long id);
    UserEntity updateUser(Long id, UserDTO userDTO);
    boolean deleteUser(Long id);
    List<UserEntity> listAllUsers();
    void changeRole(Long userId, String role);
}