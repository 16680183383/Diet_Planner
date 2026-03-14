package com.psh.diet_planner.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true)
})
public class UserEntity {

    public enum Role {
        ADMIN,
        USER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 64)
    private String nickname;

    @Column(length = 255)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;

    private Integer age;
    private String gender;
    private Double weight;
    private Double height;
    private String preferences;
    private String restrictions;

    @Column(length = 32)
    private String activityLevel;

    @Column(length = 32)
    private String goal;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_allergens", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "allergen", length = 64)
    private List<String> allergens = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_illnesses", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "illness", length = 64)
    private List<String> illnesses = new ArrayList<>();

    @Column(length = 32)
    private String flavorPref;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
