package com.psh.diet_planner.repository;

import com.psh.diet_planner.model.RecipePreference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipePreferenceRepository extends JpaRepository<RecipePreference, Long> {
    Optional<RecipePreference> findByUserIdAndRecipeName(String userId, String recipeName);
    List<RecipePreference> findByUserId(String userId);
}
