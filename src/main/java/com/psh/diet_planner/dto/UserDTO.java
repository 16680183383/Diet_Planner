package com.psh.diet_planner.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatarUrl;
    private Integer age;
    private String gender;
    private Double weight;
    private Double height;
    private String preferences;
    private String restrictions;

    private String activityLevel;
    private String goal;
    private List<String> allergens;
    private List<String> illnesses;
    private String flavorPref;
}