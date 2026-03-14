package com.psh.diet_planner.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TrainingProgress {
    private static final int MAX_LOG_LINES = 80;

    private String taskId;
    private String model;
    private String status;
    private String phase;
    private int progress;
    private int currentStep;
    private int totalSteps;
    private String lastLog;
    private String result;
    private List<String> logs = new ArrayList<>();

    public void appendLog(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        lastLog = line;
        if (logs.size() >= MAX_LOG_LINES) {
            logs.remove(0);
        }
        logs.add(line);
    }

    public boolean isDone() {
        return !"RUNNING".equals(status);
    }
}