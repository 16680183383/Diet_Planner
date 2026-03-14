package com.psh.diet_planner.repository;

import com.psh.diet_planner.model.IngredientPreference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngredientPreferenceRepository extends JpaRepository<IngredientPreference, Long> {
    Optional<IngredientPreference> findByUserIdAndIngredientName(String userId, String ingredientName);
    List<IngredientPreference> findByUserId(String userId);
}
