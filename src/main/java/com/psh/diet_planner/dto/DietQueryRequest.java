package com.psh.diet_planner.dto;

import com.psh.diet_planner.model.SearchIntent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DietQueryRequest {
    @NotBlank(message = "食材名称不能为空")
    private String foodName;

    @NotNull(message = "检索意图不能为空")
    private SearchIntent intent;

    @Min(1)
    @Max(50)
    private Integer topK = 5;

    private boolean includeAiAdvice = false;

    /** 当前用户 ID（可选），传入后启用记忆增强推荐 */
    private Long userId;

    public SearchIntent getIntent() {
        return intent;
    }

}
