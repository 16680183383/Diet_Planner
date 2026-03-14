package com.psh.diet_planner.repository;

import com.psh.diet_planner.model.FoodReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodReviewRepository extends JpaRepository<FoodReview, Long> {

    List<FoodReview> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<FoodReview> findByFoodNameOrderByUpdatedAtDesc(String foodName);

    Optional<FoodReview> findByUserIdAndFoodName(Long userId, String foodName);

    long countByUserId(Long userId);
}
