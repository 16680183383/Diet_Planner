package com.psh.diet_planner.ai;

import com.psh.diet_planner.dto.DietSearchResponse;
import com.psh.diet_planner.model.SearchIntent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

@Service
public class RecipeAiService {

    private static final String DIET_EXPERT_PROMPT = """
    你是一位严谨且亲切的智能膳食营养专家。我会提供【目标食材】及知识图谱检索出的【相关食材列表】（含向量相似度分数及物理关系标签）。

    【已知事实清单】：
    - 目标食材：{targetFood}
    - 检索意图：{intentDesc}
    - 候选关联数据：{contextData}

    【任务要求】：
    1. **关系判定**：基于已知事实。若数据中包含 'INCOMPATIBLE' 标签或相克维度相似度极高，必须执行“风险阻断”逻辑。
    2. **警示语优化**：
       - **语气控制**：针对相克关系，语气应从“建议”转变为“警示”，使用确定性词汇（如：避免、不宜、风险）。
       - **结构化输出**：风险预警必须置顶，使用【⚠️风险预警】开头，紧接着给出科学依据（如：草酸与钙结合、性质冲突等）。
    3. **场景化建议**：
       - **互补(COMP)**：给出 1 个具体的烹饪组合及营养增益点。
       - **重叠(OVERLAP)**：说明替代时的口感差异或功效适配度。
    4. **保守度原则**：
       - Score >= 0.85：表述为“确定的临床/烹饪共识”。
       - 0.7 <= Score < 0.85：表述为“普遍的饮食习惯建议”。
       - Score < 0.7：使用“潜在可能”、“尚需更多观察”等审慎词汇。

    {userProfile}

    【约束】：回答字数控制在 500 字以内，严禁输出未经证实的偏方。
    """;

    private static final double HIGH_CONFIDENCE = 0.85;
    private static final double MEDIUM_CONFIDENCE = 0.7;
    private static final int MAX_CONTEXT_ITEMS = 8;

    private final ChatClient chatClient;

    public RecipeAiService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    
    public String optimizeRecipe(Object request) {
        // 根据用户反馈优化菜谱
        return "";
    }

    public String generateDietAdvice(String baseFood, SearchIntent intent,
                                     List<DietSearchResponse> responses, UserMemoryContext memoryContext) {
        if (baseFood == null || baseFood.isBlank()) {
            return "未提供目标食材，无法生成建议";
        }
        if (responses == null || responses.isEmpty()) {
            return String.format("未找到与 %s 相关的可靠数据，建议补充知识库后再试", baseFood);
        }
        PromptTemplate template = new PromptTemplate(DIET_EXPERT_PROMPT);
        Map<String, Object> variables = new HashMap<>();
        variables.put("targetFood", baseFood);
        variables.put("intentDesc", translateIntent(intent));
        variables.put("contextData", buildContext(responses));
        variables.put("userProfile",
                memoryContext != null && !memoryContext.isEmpty()
                        ? memoryContext.toPromptSection()
                        : "");
        String userPrompt = template.create(variables).getContents();
        String content = chatClient.prompt()
            .system("请用中文输出，并保持内容专业、简洁。")
            .user(userPrompt)
            .call()
            .content();
        return content == null || content.isBlank()
            ? String.format("%s 的分析暂未返回结果，请稍后重试", baseFood)
            : content.trim();
    }

    private String buildContext(List<DietSearchResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return "暂无可靠候选数据";
        }
        return responses.stream()
            .sorted((a, b) -> Double.compare(
                Optional.ofNullable(b.getSimilarityScore()).orElse(0d),
                Optional.ofNullable(a.getSimilarityScore()).orElse(0d)))
            .limit(MAX_CONTEXT_ITEMS)
            .map(resp -> String.format("食材:%s (相似度分数:%s, 已知属性:%s)",
                resp.getName(),
                formatScore(resp.getSimilarityScore()),
                resp.getKnownRelations() == null ? List.of() : resp.getKnownRelations()))
            .collect(Collectors.joining("; "));
    }

    private String translateIntent(SearchIntent intent) {
        return switch (intent) {
            case COMP -> "寻找营养/口感互补的搭档";
            case INCOMP -> "排查饮食禁忌与相克冲突";
            case OVERLAP -> "寻找功效相似的替代食材";
            case RECIPE -> "通用语义关联分析";
            case ALL -> "全域综合分析";
        };
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "未知";
        }
        double s = Math.max(0d, Math.min(1d, score));
        String level = s >= HIGH_CONFIDENCE
            ? "确定性高"
            : (s >= MEDIUM_CONFIDENCE ? "常见建议" : "待观察");
        return String.format("%.2f(%s)", s, level);
    }
}