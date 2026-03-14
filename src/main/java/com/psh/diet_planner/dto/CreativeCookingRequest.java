package com.psh.diet_planner.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreativeCookingRequest {

    @NotEmpty(message = "请至少提供一种剩余食材")
    private List<String> ingredients;

    /** 当前用户 ID（可选），传入后启用记忆增强推荐 */
    private Long userId;
}
