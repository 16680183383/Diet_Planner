package com.psh.diet_planner.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyCheckRequest {

    private Long userId;

    @NotEmpty(message = "食材列表不能为空")
    @Size(min = 2, message = "至少提供 2 种食材以检测相克")
    private List<String> foods;
}
