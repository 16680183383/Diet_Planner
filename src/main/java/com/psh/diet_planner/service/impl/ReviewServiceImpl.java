package com.psh.diet_planner.service.impl;

import com.psh.diet_planner.dto.FoodReviewRequest;
import com.psh.diet_planner.dto.FoodReviewResponse;
import com.psh.diet_planner.dto.RecipeReviewRequest;
import com.psh.diet_planner.dto.RecipeReviewResponse;
import com.psh.diet_planner.dto.UserMemoryResponse;
import com.psh.diet_planner.model.FoodReview;
import com.psh.diet_planner.model.IngredientPreference;
import com.psh.diet_planner.model.RecipePreference;
import com.psh.diet_planner.model.RecipeReview;
import com.psh.diet_planner.model.UserEntity;
import com.psh.diet_planner.repository.FoodReviewRepository;
import com.psh.diet_planner.repository.IngredientPreferenceRepository;
import com.psh.diet_planner.repository.RecipePreferenceRepository;
import com.psh.diet_planner.repository.RecipeReviewRepository;
import com.psh.diet_planner.repository.UserJpaRepository;
import com.psh.diet_planner.service.ReviewService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final FoodReviewRepository foodReviewRepository;
    private final RecipeReviewRepository recipeReviewRepository;
    private final IngredientPreferenceRepository ingredientPreferenceRepository;
    private final RecipePreferenceRepository recipePreferenceRepository;
    private final UserJpaRepository userJpaRepository;

    public ReviewServiceImpl(FoodReviewRepository foodReviewRepository,
                             RecipeReviewRepository recipeReviewRepository,
                             IngredientPreferenceRepository ingredientPreferenceRepository,
                             RecipePreferenceRepository recipePreferenceRepository,
                             UserJpaRepository userJpaRepository) {
        this.foodReviewRepository = foodReviewRepository;
        this.recipeReviewRepository = recipeReviewRepository;
        this.ingredientPreferenceRepository = ingredientPreferenceRepository;
        this.recipePreferenceRepository = recipePreferenceRepository;
        this.userJpaRepository = userJpaRepository;
    }

    // ==================== 食材评价 ====================

    @Override
    @Transactional
    public FoodReviewResponse submitFoodReview(FoodReviewRequest request) {
        // 如果已有该用户对该食材的评价，更新而非新增
        FoodReview review = foodReviewRepository
                .findByUserIdAndFoodName(request.getUserId(), request.getFoodName())
                .orElseGet(FoodReview::new);

        review.setUserId(request.getUserId());
        review.setFoodName(request.getFoodName());
        review.setTasteRating(request.getTasteRating());
        review.setNutritionRating(request.getNutritionRating());
        review.setFreshnessRating(request.getFreshnessRating());
        review.setCostRating(request.getCostRating());
        review.setOverallRating(request.getOverallRating());
        review.setComment(request.getComment());
        LocalDateTime now = LocalDateTime.now();
        if (review.getCreatedAt() == null) {
            review.setCreatedAt(now);
        }
        review.setUpdatedAt(now);

        return toFoodReviewResponse(foodReviewRepository.save(review));
    }

    @Override
    @Transactional
    public FoodReviewResponse updateFoodReview(Long reviewId, FoodReviewRequest request) {
        FoodReview review = foodReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("食材评价不存在: " + reviewId));

        if (request.getTasteRating() != null) review.setTasteRating(request.getTasteRating());
        if (request.getNutritionRating() != null) review.setNutritionRating(request.getNutritionRating());
        if (request.getFreshnessRating() != null) review.setFreshnessRating(request.getFreshnessRating());
        if (request.getCostRating() != null) review.setCostRating(request.getCostRating());
        if (request.getOverallRating() != null) review.setOverallRating(request.getOverallRating());
        if (request.getComment() != null) review.setComment(request.getComment());
        review.setUpdatedAt(LocalDateTime.now());

        return toFoodReviewResponse(foodReviewRepository.save(review));
    }

    @Override
    @Transactional
    public void deleteFoodReview(Long reviewId) {
        foodReviewRepository.deleteById(reviewId);
    }

    @Override
    public List<FoodReviewResponse> getFoodReviewsByUser(Long userId) {
        return foodReviewRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream().map(this::toFoodReviewResponse).collect(Collectors.toList());
    }

    @Override
    public List<FoodReviewResponse> getFoodReviewsByFood(String foodName) {
        return foodReviewRepository.findByFoodNameOrderByUpdatedAtDesc(foodName)
                .stream().map(this::toFoodReviewResponse).collect(Collectors.toList());
    }

    @Override
    public FoodReviewResponse getUserFoodReview(Long userId, String foodName) {
        return foodReviewRepository.findByUserIdAndFoodName(userId, foodName)
                .map(this::toFoodReviewResponse).orElse(null);
    }

    // ==================== 菜谱评价 ====================

    @Override
    @Transactional
    public RecipeReviewResponse submitRecipeReview(RecipeReviewRequest request) {
        RecipeReview review = recipeReviewRepository
                .findByUserIdAndRecipeName(request.getUserId(), request.getRecipeName())
                .orElseGet(RecipeReview::new);

        review.setUserId(request.getUserId());
        review.setRecipeName(request.getRecipeName());
        review.setTasteRating(request.getTasteRating());
        review.setDifficultyRating(request.getDifficultyRating());
        review.setNutritionRating(request.getNutritionRating());
        review.setPresentationRating(request.getPresentationRating());
        review.setOverallRating(request.getOverallRating());
        review.setComment(request.getComment());
        LocalDateTime now = LocalDateTime.now();
        if (review.getCreatedAt() == null) {
            review.setCreatedAt(now);
        }
        review.setUpdatedAt(now);

        return toRecipeReviewResponse(recipeReviewRepository.save(review));
    }

    @Override
    @Transactional
    public RecipeReviewResponse updateRecipeReview(Long reviewId, RecipeReviewRequest request) {
        RecipeReview review = recipeReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("菜谱评价不存在: " + reviewId));

        if (request.getTasteRating() != null) review.setTasteRating(request.getTasteRating());
        if (request.getDifficultyRating() != null) review.setDifficultyRating(request.getDifficultyRating());
        if (request.getNutritionRating() != null) review.setNutritionRating(request.getNutritionRating());
        if (request.getPresentationRating() != null) review.setPresentationRating(request.getPresentationRating());
        if (request.getOverallRating() != null) review.setOverallRating(request.getOverallRating());
        if (request.getComment() != null) review.setComment(request.getComment());
        review.setUpdatedAt(LocalDateTime.now());

        return toRecipeReviewResponse(recipeReviewRepository.save(review));
    }

    @Override
    @Transactional
    public void deleteRecipeReview(Long reviewId) {
        recipeReviewRepository.deleteById(reviewId);
    }

    @Override
    public List<RecipeReviewResponse> getRecipeReviewsByUser(Long userId) {
        return recipeReviewRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream().map(this::toRecipeReviewResponse).collect(Collectors.toList());
    }

    @Override
    public List<RecipeReviewResponse> getRecipeReviewsByRecipe(String recipeName) {
        return recipeReviewRepository.findByRecipeNameOrderByUpdatedAtDesc(recipeName)
                .stream().map(this::toRecipeReviewResponse).collect(Collectors.toList());
    }

    @Override
    public RecipeReviewResponse getUserRecipeReview(Long userId, String recipeName) {
        return recipeReviewRepository.findByUserIdAndRecipeName(userId, recipeName)
                .map(this::toRecipeReviewResponse).orElse(null);
    }

    // ==================== 登录态同步：加载用户完整记忆 ====================

    @Override
    public UserMemoryResponse loadUserMemory(Long userId) {
        UserEntity user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        UserMemoryResponse memory = new UserMemoryResponse();
        memory.setUserId(userId);
        memory.setUsername(user.getUsername());

        // 0. 用户基础信息 & 健康档案
        memory.setAge(user.getAge());
        memory.setGender(user.getGender());
        memory.setWeight(user.getWeight());
        memory.setHeight(user.getHeight());
        memory.setActivityLevel(user.getActivityLevel());
        memory.setGoal(user.getGoal());
        memory.setFlavorPref(user.getFlavorPref());
        memory.setPreferences(user.getPreferences());
        memory.setRestrictions(user.getRestrictions());
        memory.setAllergens(user.getAllergens() != null ? user.getAllergens() : List.of());
        memory.setIllnesses(user.getIllnesses() != null ? user.getIllnesses() : List.of());

        // 1. 食材偏好
        String uid = String.valueOf(userId);
        List<IngredientPreference> ingPrefs = ingredientPreferenceRepository.findByUserId(uid);
        memory.setIngredientPreferences(ingPrefs.stream().map(p -> {
            UserMemoryResponse.IngredientPrefItem item = new UserMemoryResponse.IngredientPrefItem();
            item.setIngredientName(p.getIngredientName());
            item.setPreference(p.getPreference().name());
            item.setReason(p.getReason());
            return item;
        }).collect(Collectors.toList()));

        // 2. 菜谱偏好
        List<RecipePreference> recPrefs = recipePreferenceRepository.findByUserId(uid);
        memory.setRecipePreferences(recPrefs.stream().map(p -> {
            UserMemoryResponse.RecipePrefItem item = new UserMemoryResponse.RecipePrefItem();
            item.setRecipeName(p.getRecipeName());
            item.setPreference(p.getPreference().name());
            item.setReason(p.getReason());
            return item;
        }).collect(Collectors.toList()));

        // 3. 食材评价历史
        List<FoodReviewResponse> foodReviews = getFoodReviewsByUser(userId);
        memory.setFoodReviews(foodReviews);

        // 4. 菜谱评价历史
        List<RecipeReviewResponse> recipeReviews = getRecipeReviewsByUser(userId);
        memory.setRecipeReviews(recipeReviews);

        // 5. 统计摘要
        memory.setTotalFoodReviews(foodReviews.size());
        memory.setTotalRecipeReviews(recipeReviews.size());
        memory.setTotalIngredientPrefs(ingPrefs.size());
        memory.setTotalRecipePrefs(recPrefs.size());

        return memory;
    }

    // ==================== 转换方法 ====================

    private FoodReviewResponse toFoodReviewResponse(FoodReview review) {
        FoodReviewResponse resp = new FoodReviewResponse();
        resp.setId(review.getId());
        resp.setUserId(review.getUserId());
        resp.setFoodName(review.getFoodName());
        resp.setTasteRating(review.getTasteRating());
        resp.setNutritionRating(review.getNutritionRating());
        resp.setFreshnessRating(review.getFreshnessRating());
        resp.setCostRating(review.getCostRating());
        resp.setOverallRating(review.getOverallRating());
        resp.setComment(review.getComment());
        resp.setCreatedAt(review.getCreatedAt());
        resp.setUpdatedAt(review.getUpdatedAt());
        return resp;
    }

    private RecipeReviewResponse toRecipeReviewResponse(RecipeReview review) {
        RecipeReviewResponse resp = new RecipeReviewResponse();
        resp.setId(review.getId());
        resp.setUserId(review.getUserId());
        resp.setRecipeId(review.getRecipeName());
        resp.setRecipeName(review.getRecipeName());
        resp.setTasteRating(review.getTasteRating());
        resp.setDifficultyRating(review.getDifficultyRating());
        resp.setNutritionRating(review.getNutritionRating());
        resp.setPresentationRating(review.getPresentationRating());
        resp.setOverallRating(review.getOverallRating());
        resp.setComment(review.getComment());
        resp.setCreatedAt(review.getCreatedAt());
        resp.setUpdatedAt(review.getUpdatedAt());
        return resp;
    }
}
