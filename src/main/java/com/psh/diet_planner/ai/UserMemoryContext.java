package com.psh.diet_planner.ai;

import com.psh.diet_planner.dto.FoodReviewResponse;
import com.psh.diet_planner.dto.RecipeReviewResponse;
import com.psh.diet_planner.dto.UserMemoryResponse;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 将用户历史记忆（个人信息 + 健康档案 + 偏好 + 评价）转化为 LLM 可理解的精炼上下文文本。
 */
public class UserMemoryContext {

    private static final int MAX_REVIEW_ITEMS = 5;

    private final String contextText;
    private final UserMemoryResponse rawMemory;

    private UserMemoryContext(String contextText, UserMemoryResponse rawMemory) {
        this.contextText = contextText;
        this.rawMemory = rawMemory;
    }

    /** 无用户数据时返回的空上下文 */
    public static UserMemoryContext empty() {
        return new UserMemoryContext("", null);
    }

    /** 获取原始用户记忆数据（用于程序化判断，如过敏原匹配） */
    public UserMemoryResponse getRawMemory() {
        return rawMemory;
    }

    /** 从 UserMemoryResponse 构建上下文 */
    public static UserMemoryContext fromMemory(UserMemoryResponse memory) {
        if (memory == null) {
            return empty();
        }
        StringBuilder sb = new StringBuilder();

        // 0. 用户基础身体信息
        appendPhysicalProfile(sb, memory);

        // 1. ⚠️过敏原（最高优先级安全字段）
        appendAllergens(sb, memory.getAllergens());

        // 2. ⚠️疾病史（最高优先级安全字段）
        appendIllnesses(sb, memory.getIllnesses());

        // 3. 饮食目标 & 限制
        appendGoalAndRestrictions(sb, memory);

        // 4. 口味偏好
        appendFlavorPref(sb, memory.getFlavorPref());

        // 5. 食材偏好——喜欢 / 不喜欢 / 过敏
        appendIngredientPrefs(sb, memory.getIngredientPreferences());

        // 6. 菜谱偏好
        appendRecipePrefs(sb, memory.getRecipePreferences());

        // 7. 历史食材评价（取最近高分 / 低分各若干条）
        appendFoodReviews(sb, memory.getFoodReviews());

        // 8. 历史菜谱评价
        appendRecipeReviews(sb, memory.getRecipeReviews());

        return new UserMemoryContext(sb.toString().trim(), memory);
    }

    public boolean isEmpty() {
        return contextText == null || contextText.isBlank();
    }

    public String toPromptSection() {
        if (isEmpty()) {
            return "";
        }
        return "\n\n【用户个性化画像】：\n" + contextText
                + "\n请严格遵守以上安全约束（过敏原与疾病禁忌），在建议中优先推荐用户喜爱的食材/菜谱，"
                + "规避用户不喜欢或低评分的食材，结合用户身体数据和健康目标给出个性化调整，并适当解释理由。";
    }

    @Override
    public String toString() {
        return contextText;
    }

    // ==================== 内部构建逻辑 ====================

    private static void appendPhysicalProfile(StringBuilder sb, UserMemoryResponse m) {
        boolean hasAny = m.getAge() != null || m.getGender() != null
                || m.getWeight() != null || m.getHeight() != null
                || hasText(m.getActivityLevel());
        if (!hasAny) {
            return;
        }
        sb.append("【身体信息】");
        if (m.getGender() != null) sb.append(" 性别:").append(m.getGender());
        if (m.getAge() != null) sb.append(" 年龄:").append(m.getAge()).append("岁");
        if (m.getHeight() != null) sb.append(" 身高:").append(m.getHeight()).append("cm");
        if (m.getWeight() != null) sb.append(" 体重:").append(m.getWeight()).append("kg");
        if (hasText(m.getActivityLevel())) sb.append(" 活动量:").append(m.getActivityLevel());
        sb.append("\n");
    }

    private static void appendAllergens(StringBuilder sb, List<String> allergens) {
        if (allergens == null || allergens.isEmpty()) {
            return;
        }
        sb.append("- ⚠️【过敏原（必须完全规避）】：").append(String.join("、", allergens)).append("\n");
    }

    private static void appendIllnesses(StringBuilder sb, List<String> illnesses) {
        if (illnesses == null || illnesses.isEmpty()) {
            return;
        }
        sb.append("- ⚠️【疾病史（需严格遵循相关饮食禁忌）】：").append(String.join("、", illnesses)).append("\n");
    }

    private static void appendGoalAndRestrictions(StringBuilder sb, UserMemoryResponse m) {
        if (hasText(m.getGoal())) {
            sb.append("- 饮食目标：").append(m.getGoal()).append("\n");
        }
        if (hasText(m.getRestrictions())) {
            sb.append("- 饮食限制/禁忌：").append(m.getRestrictions()).append("\n");
        }
        if (hasText(m.getPreferences())) {
            sb.append("- 饮食偏好描述：").append(m.getPreferences()).append("\n");
        }
    }

    private static void appendFlavorPref(StringBuilder sb, String flavorPref) {
        if (hasText(flavorPref)) {
            sb.append("- 偏好口味：").append(flavorPref).append("\n");
        }
    }

    private static void appendIngredientPrefs(StringBuilder sb, List<UserMemoryResponse.IngredientPrefItem> prefs) {
        if (prefs == null || prefs.isEmpty()) {
            return;
        }
        List<String> liked = prefs.stream()
                .filter(p -> "FAVORITE".equalsIgnoreCase(p.getPreference()))
                .map(UserMemoryResponse.IngredientPrefItem::getIngredientName)
                .collect(Collectors.toList());
        List<String> disliked = prefs.stream()
                .filter(p -> "DISLIKES".equalsIgnoreCase(p.getPreference()))
                .map(p -> {
                    String name = p.getIngredientName();
                    return p.getReason() != null && !p.getReason().isBlank()
                            ? name + "(" + p.getReason() + ")"
                            : name;
                })
                .collect(Collectors.toList());
        List<String> allergic = prefs.stream()
                .filter(p -> "ALLERGIC".equalsIgnoreCase(p.getPreference()))
                .map(UserMemoryResponse.IngredientPrefItem::getIngredientName)
                .collect(Collectors.toList());

        if (!liked.isEmpty()) {
            sb.append("- 喜爱的食材：").append(String.join("、", liked)).append("\n");
        }
        if (!disliked.isEmpty()) {
            sb.append("- 不喜欢的食材：").append(String.join("、", disliked)).append("\n");
        }
        if (!allergic.isEmpty()) {
            sb.append("- ⚠️过敏食材（必须规避）：").append(String.join("、", allergic)).append("\n");
        }
    }

    private static void appendRecipePrefs(StringBuilder sb, List<UserMemoryResponse.RecipePrefItem> prefs) {
        if (prefs == null || prefs.isEmpty()) {
            return;
        }
        List<String> liked = prefs.stream()
                .filter(p -> "LIKE".equalsIgnoreCase(p.getPreference()))
                .map(UserMemoryResponse.RecipePrefItem::getRecipeName)
                .collect(Collectors.toList());
        List<String> disliked = prefs.stream()
                .filter(p -> "NOT_INTERESTED".equalsIgnoreCase(p.getPreference()))
                .map(UserMemoryResponse.RecipePrefItem::getRecipeName)
                .collect(Collectors.toList());

        if (!liked.isEmpty()) {
            sb.append("- 偏爱的菜谱风格/类型：").append(String.join("、", liked)).append("\n");
        }
        if (!disliked.isEmpty()) {
            sb.append("- 不偏好的菜谱：").append(String.join("、", disliked)).append("\n");
        }
    }

    private static void appendFoodReviews(StringBuilder sb, List<FoodReviewResponse> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return;
        }
        List<String> highRated = reviews.stream()
                .filter(r -> r.getOverallRating() != null && r.getOverallRating() >= 4)
                .limit(MAX_REVIEW_ITEMS)
                .map(r -> r.getFoodName() + "(评分" + r.getOverallRating() + ")")
                .collect(Collectors.toList());
        List<String> lowRated = reviews.stream()
                .filter(r -> r.getOverallRating() != null && r.getOverallRating() <= 2)
                .limit(MAX_REVIEW_ITEMS)
                .map(r -> {
                    String s = r.getFoodName() + "(评分" + r.getOverallRating() + ")";
                    if (r.getComment() != null && !r.getComment().isBlank()) {
                        s += "[" + truncate(r.getComment(), 20) + "]";
                    }
                    return s;
                })
                .collect(Collectors.toList());

        if (!highRated.isEmpty()) {
            sb.append("- 高评分食材：").append(String.join("、", highRated)).append("\n");
        }
        if (!lowRated.isEmpty()) {
            sb.append("- 低评分食材：").append(String.join("、", lowRated)).append("\n");
        }
    }

    private static void appendRecipeReviews(StringBuilder sb, List<RecipeReviewResponse> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return;
        }
        List<String> highRated = reviews.stream()
                .filter(r -> r.getOverallRating() != null && r.getOverallRating() >= 4)
                .limit(MAX_REVIEW_ITEMS)
                .map(r -> {
                    String name = r.getRecipeName() != null ? r.getRecipeName() : r.getRecipeId();
                    return name + "(评分" + r.getOverallRating() + ")";
                })
                .collect(Collectors.toList());
        List<String> lowRated = reviews.stream()
                .filter(r -> r.getOverallRating() != null && r.getOverallRating() <= 2)
                .limit(MAX_REVIEW_ITEMS)
                .map(r -> {
                    String name = r.getRecipeName() != null ? r.getRecipeName() : r.getRecipeId();
                    return name + "(评分" + r.getOverallRating() + ")";
                })
                .collect(Collectors.toList());

        if (!highRated.isEmpty()) {
            sb.append("- 喜爱的菜谱：").append(String.join("、", highRated)).append("\n");
        }
        if (!lowRated.isEmpty()) {
            sb.append("- 不满意的菜谱：").append(String.join("、", lowRated)).append("\n");
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
