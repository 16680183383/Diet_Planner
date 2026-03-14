package com.psh.diet_planner.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "shopping_list_items", indexes = {
    @Index(name = "idx_shopping_user", columnList = "user_id")
})
public class ShoppingListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipe_name", length = 256)
    private String recipeName;

    @Column(name = "ingredient_name", nullable = false, length = 128)
    private String ingredientName;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
