package com.psh.diet_planner.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodPairingRequest {

    private Long userId;

    @NotBlank(message = "主食材名称不能为空")
    private String foodName;

    @Min(value = 2, message = "推荐数量至少为 2")
    private Integer limit;
}
