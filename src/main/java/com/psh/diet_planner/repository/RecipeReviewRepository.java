package com.psh.diet_planner.repository;

import com.psh.diet_planner.model.RecipeReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeReviewRepository extends JpaRepository<RecipeReview, Long> {

    List<RecipeReview> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<RecipeReview> findByRecipeIdOrderByUpdatedAtDesc(String recipeId);

    Optional<RecipeReview> findByUserIdAndRecipeId(Long userId, String recipeId);

    long countByUserId(Long userId);
}
