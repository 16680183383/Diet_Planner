package com.psh.diet_planner.service;

import com.psh.diet_planner.dto.TrainingProgress;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrainingTaskService {

    private final Map<String, TrainingProgress> tasks = new ConcurrentHashMap<>();

    public TrainingProgress createTask(String model) {
        TrainingProgress progress = new TrainingProgress();
        progress.setTaskId(UUID.randomUUID().toString().substring(0, 8));
        progress.setModel(model);
        progress.setStatus("RUNNING");
        progress.setPhase("任务已创建，等待执行");
        tasks.put(progress.getTaskId(), progress);
        return progress;
    }

    public TrainingProgress getTask(String taskId) {
        return tasks.get(taskId);
    }

    public TrainingProgress getRunningTask() {
        return tasks.values().stream()
                .filter(task -> "RUNNING".equals(task.getStatus()))
                .findFirst()
                .orElse(null);
    }
}