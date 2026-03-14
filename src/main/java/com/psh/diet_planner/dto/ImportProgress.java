package com.psh.diet_planner.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ImportProgress {
    private String taskId;
    private String status;      // RUNNING, COMPLETED, FAILED, CANCELLED
    private String phase;       // 当前阶段描述
    private int totalFiles;
    private int processedFiles;
    private int failedFiles;
    private int totalItems;
    private int currentFileItems;
    private int currentFileProcessedItems;
    private int successCount;
    private int failCount;
    private volatile boolean cancelled = false;
    private List<String> errors = new ArrayList<>();

    public void cancel() {
        this.cancelled = true;
    }

    public void addError(String error) {
        if (errors.size() < 100) {  // 最多记录100条错误
            errors.add(error);
        }
    }

    public int getPercent() {
        if (totalFiles > 0) {
            double completedFilePortion = processedFiles / (double) totalFiles;
            double currentFilePortion = 0.0;
            if (processedFiles < totalFiles && currentFileItems > 0) {
                currentFilePortion = (currentFileProcessedItems / (double) currentFileItems) / totalFiles;
            }
            double percent = (completedFilePortion + currentFilePortion) * 100.0;
            return (int) Math.min(100, Math.max(0, percent));
        }
        if (totalItems == 0) return 0;
        return (int) ((successCount + failCount) * 100.0 / totalItems);
    }
}
