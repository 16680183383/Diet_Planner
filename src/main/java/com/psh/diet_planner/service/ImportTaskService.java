package com.psh.diet_planner.service;

import com.psh.diet_planner.dto.ImportProgress;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImportTaskService {

    private final Map<String, ImportProgress> tasks = new ConcurrentHashMap<>();

    public ImportProgress createTask() {
        ImportProgress progress = new ImportProgress();
        progress.setTaskId(UUID.randomUUID().toString().substring(0, 8));
        progress.setStatus("RUNNING");
        tasks.put(progress.getTaskId(), progress);
        return progress;
    }

    public ImportProgress getTask(String taskId) {
        return tasks.get(taskId);
    }

    public ImportProgress getRunningTask() {
        return tasks.values().stream()
                .filter(p -> "RUNNING".equals(p.getStatus()))
                .findFirst().orElse(null);
    }

    public void removeTask(String taskId) {
        tasks.remove(taskId);
    }
}
