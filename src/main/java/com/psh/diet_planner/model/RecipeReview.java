package com.psh.diet_planner.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * MySQL 菜谱评价实体：支持用户对历史消费过的菜谱进行多维度评分与文字反馈
 */
@Getter
@Setter
@Entity
@Table(name = "recipe_reviews", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "recipe_name"})
}, indexes = {
    @Index(name = "idx_recipe_review_user", columnList = "user_id")
})
public class RecipeReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipe_name", nullable = false, length = 255)
    private String recipeName;

    /** 口味评分 1-5 */
    @Column(name = "taste_rating")
    private Integer tasteRating;

    /** 制作难度评分 1-5 */
    @Column(name = "difficulty_rating")
    private Integer difficultyRating;

    /** 营养均衡评分 1-5 */
    @Column(name = "nutrition_rating")
    private Integer nutritionRating;

    /** 色香味评分 1-5 */
    @Column(name = "presentation_rating")
    private Integer presentationRating;

    /** 综合评分 1-5 */
    @Column(name = "overall_rating", nullable = false)
    private Integer overallRating;

    /** 文字评价 */
    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
