package com.psh.diet_planner.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "recipe_preferences", indexes = {
    @Index(name = "idx_recipe_pref_user", columnList = "user_id")
})
public class RecipePreference {

    public enum PreferenceType {
        LIKE,
        NOT_INTERESTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "recipe_id", nullable = false)
    private String recipeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PreferenceType preference;

    @Column(length = 255)
    private String reason;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
