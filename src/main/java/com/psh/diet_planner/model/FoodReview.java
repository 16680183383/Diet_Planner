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
 * MySQL 食材评价实体：支持用户对历史消费过的食材进行多维度评分与文字反馈
 */
@Getter
@Setter
@Entity
@Table(name = "food_reviews", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "food_name"})
}, indexes = {
    @Index(name = "idx_food_review_user", columnList = "user_id")
})
public class FoodReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "food_name", nullable = false, length = 128)
    private String foodName;

    /** 口味评分 1-5 */
    @Column(name = "taste_rating")
    private Integer tasteRating;

    /** 营养价值评分 1-5 */
    @Column(name = "nutrition_rating")
    private Integer nutritionRating;

    /** 新鲜度评分 1-5 */
    @Column(name = "freshness_rating")
    private Integer freshnessRating;

    /** 性价比评分 1-5 */
    @Column(name = "cost_rating")
    private Integer costRating;

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
