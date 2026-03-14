package com.psh.diet_planner.scheduler;

import com.psh.diet_planner.service.FoodDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeUpdateScheduler {
    
    @Autowired
    private FoodDataService foodDataService;
    
    @Value("${food.data.path:/data/foods}")
    private String foodDataPath;
    
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    public void updateKnowledge() {
        // 更新知识库
        try {
            // 尝试批量导入食物数据
            foodDataService.importBatchFoodData(foodDataPath);
        } catch (Exception e) {
            // 记录日志
            e.printStackTrace();
        }
    }
    
    // 提供手动触发的方法
    public void manualUpdate(String path) {
        try {
            foodDataService.importBatchFoodData(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}