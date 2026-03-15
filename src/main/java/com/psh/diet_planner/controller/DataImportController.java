package com.psh.diet_planner.controller;

import com.psh.diet_planner.dto.ApiResponse;
import com.psh.diet_planner.dto.DataStatsResponse;
import com.psh.diet_planner.dto.ImportProgress;
import com.psh.diet_planner.dto.TrainingProgress;
import com.psh.diet_planner.service.FoodDataService;
import com.psh.diet_planner.service.ImportTaskService;
import com.psh.diet_planner.service.ModelTrainingService;
import com.psh.diet_planner.service.OnnxInferenceService;
import com.psh.diet_planner.service.TrainingTaskService;
import com.psh.diet_planner.service.support.mcp.McpHealthCheckService;
import com.psh.diet_planner.service.support.mcp.Neo4jMcpAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/data")
public class DataImportController {
    
    @Autowired
    private FoodDataService foodDataService;
    @Autowired
    private ModelTrainingService modelTrainingService;
    @Autowired
    private ImportTaskService importTaskService;
    @Autowired
    private OnnxInferenceService onnxInferenceService;
    @Autowired
    private TrainingTaskService trainingTaskService;
    @Autowired
    @Qualifier("importTrainingTaskExecutor")
    private TaskExecutor taskExecutor;
    @Autowired
    private Neo4jMcpAdapter neo4jMcpAdapter;
    @Autowired
    private McpHealthCheckService mcpHealthCheckService;
    
    @Value("${food.data.path:./data/foods}")
    private String foodDataPath;

    private Path saveTempUpload(MultipartFile file, String prefix) throws Exception {
        Path tempFile = Files.createTempFile(prefix, ".json");
        file.transferTo(tempFile.toFile());
        return tempFile;
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import-food-file")
    public ApiResponse<String> importFoodFile(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "autoEmbed", required = false, defaultValue = "false") boolean autoEmbed) {
        Path tempFile = null;
        try {
            tempFile = saveTempUpload(file, "food_");
            foodDataService.importFoodDataFromJson(tempFile.toString(), null, autoEmbed);
            return ApiResponse.success("食物数据导入成功", "食物数据导入成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("导入失败: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import-food-path")
    public ApiResponse<String> importFoodByPath(@RequestParam("path") String filePath,
                                                @RequestParam(value = "autoEmbed", required = false, defaultValue = "false") boolean autoEmbed) {
        ImportProgress running = importTaskService.getRunningTask();
        if (running != null) {
            return ApiResponse.error("已有导入任务正在运行（任务ID: " + running.getTaskId() + "），请等待完成或取消后再试");
        }
        ImportProgress progress = importTaskService.createTask();
        try {
            taskExecutor.execute(() -> {
            try {
                int itemCount = foodDataService.countItemsInJsonFile(filePath);
                progress.setTotalFiles(1);
                progress.setProcessedFiles(0);
                progress.setTotalItems(itemCount);
                progress.setCurrentFileItems(itemCount);
                progress.setCurrentFileProcessedItems(0);
                progress.setPhase("正在导入文件: " + Path.of(filePath).getFileName());
                foodDataService.importFoodDataFromJson(filePath, progress, autoEmbed);
                if (progress.isCancelled()) {
                    progress.setStatus("CANCELLED");
                    progress.setPhase("导入已取消");
                } else {
                    progress.setProcessedFiles(1);
                    progress.setStatus("COMPLETED");
                    progress.setPhase("食材导入完成");
                }
            } catch (Exception e) {
                progress.setStatus("FAILED");
                progress.setPhase("导入失败: " + e.getMessage());
                progress.addError(e.getMessage());
            }
        });
        } catch (RejectedExecutionException e) {
            importTaskService.removeTask(progress.getTaskId());
            return ApiResponse.error("导入任务提交失败，线程池繁忙: " + e.getMessage());
        }
        return ApiResponse.success(progress.getTaskId(), "食材导入任务已启动，任务ID: " + progress.getTaskId());
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import-recipe-path")
    public ApiResponse<String> importRecipeByPath(@RequestParam("path") String filePath) {
        ImportProgress running = importTaskService.getRunningTask();
        if (running != null) {
            return ApiResponse.error("已有导入任务正在运行（任务ID: " + running.getTaskId() + "），请等待完成或取消后再试");
        }
        ImportProgress progress = importTaskService.createTask();
        try {
            taskExecutor.execute(() -> {
            try {
                int itemCount = foodDataService.countItemsInJsonFile(filePath);
                progress.setTotalFiles(1);
                progress.setProcessedFiles(0);
                progress.setTotalItems(itemCount);
                progress.setCurrentFileItems(itemCount);
                progress.setCurrentFileProcessedItems(0);
                progress.setPhase("正在导入文件: " + Path.of(filePath).getFileName());
                foodDataService.importRecipesFromJson(filePath, progress);
                if (progress.isCancelled()) {
                    progress.setStatus("CANCELLED");
                    progress.setPhase("导入已取消");
                } else {
                    progress.setProcessedFiles(1);
                    progress.setStatus("COMPLETED");
                    progress.setPhase("食谱导入完成");
                }
            } catch (Exception e) {
                progress.setStatus("FAILED");
                progress.setPhase("导入失败: " + e.getMessage());
                progress.addError(e.getMessage());
            }
        });
        } catch (RejectedExecutionException e) {
            importTaskService.removeTask(progress.getTaskId());
            return ApiResponse.error("导入任务提交失败，线程池繁忙: " + e.getMessage());
        }
        return ApiResponse.success(progress.getTaskId(), "食谱导入任务已启动，任务ID: " + progress.getTaskId());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import-all")
    public ApiResponse<String> importAll(@RequestParam(value = "path", required = false) String path,
                                         @RequestParam(value = "autoEmbed", required = false, defaultValue = "false") boolean autoEmbed) {
        // 防止并发导入：检查是否有正在运行的任务
        ImportProgress running = importTaskService.getRunningTask();
        if (running != null) {
            return ApiResponse.error("已有导入任务正在运行（任务ID: " + running.getTaskId() + "），请等待完成或取消后再试");
        }
        String dataDir = (path != null && !path.isEmpty()) ? path : foodDataPath;
        ImportProgress progress = importTaskService.createTask();
        // 异步执行导入
        try {
            taskExecutor.execute(() -> {
            try {
                foodDataService.importAllFromDataDir(dataDir, progress, autoEmbed);
                if (progress.isCancelled()) {
                    progress.setStatus("CANCELLED");
                    progress.setPhase("导入已取消");
                } else {
                    progress.setStatus("COMPLETED");
                    progress.setPhase("全部导入完成");
                }
            } catch (Exception e) {
                progress.setStatus("FAILED");
                progress.setPhase("导入失败: " + e.getMessage());
                progress.addError(e.getMessage());
            }
        });
        } catch (RejectedExecutionException e) {
            importTaskService.removeTask(progress.getTaskId());
            return ApiResponse.error("导入任务提交失败，线程池繁忙: " + e.getMessage());
        }
        return ApiResponse.success(progress.getTaskId(), "导入任务已启动，任务ID: " + progress.getTaskId());
    }

    @GetMapping("/import-progress")
    public ApiResponse<ImportProgress> importProgress(@RequestParam("taskId") String taskId) {
        ImportProgress progress = importTaskService.getTask(taskId);
        if (progress == null) {
            return ApiResponse.error("任务不存在: " + taskId);
        }
        return ApiResponse.success(progress, "查询成功");
    }

    @GetMapping("/import-running")
    public ApiResponse<ImportProgress> importRunning() {
        ImportProgress progress = importTaskService.getRunningTask();
        return ApiResponse.success(progress, progress == null ? "当前无运行中的导入任务" : "查询成功");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import-cancel")
    public ApiResponse<String> importCancel(@RequestParam("taskId") String taskId) {
        ImportProgress progress = importTaskService.getTask(taskId);
        if (progress == null) {
            return ApiResponse.error("任务不存在: " + taskId);
        }
        if (!"RUNNING".equals(progress.getStatus())) {
            return ApiResponse.error("任务已结束，状态: " + progress.getStatus());
        }
        progress.cancel();
        return ApiResponse.success(taskId, "取消指令已发送，任务将在当前条目处理完成后停止");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/train/metapath2vec")
    public ApiResponse<String> trainMetapath2Vec() {
        TrainingProgress running = trainingTaskService.getRunningTask();
        if (running != null) {
            return ApiResponse.error("已有训练任务正在运行（任务ID: " + running.getTaskId() + "），请等待完成后再试");
        }
        TrainingProgress progress = trainingTaskService.createTask("metapath2vec");
        try {
            taskExecutor.execute(() -> {
            try {
                modelTrainingService.trainMetapath2Vec(progress);
                progress.setStatus("COMPLETED");
                progress.setProgress(100);
                progress.setPhase("Metapath2Vec 训练完成");
                progress.appendLog("Metapath2Vec 训练流程结束");
            } catch (Exception e) {
                progress.setStatus("FAILED");
                progress.setPhase("Metapath2Vec 训练失败: " + e.getMessage());
                progress.setResult(progress.getPhase());
                progress.appendLog(progress.getPhase());
            }
        });
        } catch (RejectedExecutionException e) {
            progress.setStatus("FAILED");
            progress.setPhase("训练任务提交失败，线程池繁忙: " + e.getMessage());
            progress.setResult(progress.getPhase());
            progress.appendLog(progress.getPhase());
            return ApiResponse.error(progress.getPhase());
        }
        return ApiResponse.success(progress.getTaskId(), "Metapath2Vec 训练任务已启动，任务ID: " + progress.getTaskId());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/train/graphsage")
    public ApiResponse<String> trainGraphSage() {
        TrainingProgress running = trainingTaskService.getRunningTask();
        if (running != null) {
            return ApiResponse.error("已有训练任务正在运行（任务ID: " + running.getTaskId() + "），请等待完成后再试");
        }
        TrainingProgress progress = trainingTaskService.createTask("graphsage");
        try {
            taskExecutor.execute(() -> {
            try {
                modelTrainingService.trainGraphSage(progress);
                progress.setPhase("正在重新加载 ONNX 模型");
                progress.setProgress(98);
                onnxInferenceService.loadModel();
                boolean onnxOk = onnxInferenceService.isModelLoaded();
                progress.appendLog(onnxOk ? "ONNX 模型重载成功" : "未找到 ONNX 模型文件");
                if (!onnxOk) {
                    progress.setStatus("FAILED");
                    progress.setPhase("GraphSAGE 训练完成，但 ONNX 模型重载失败");
                    progress.setResult(progress.getPhase());
                    progress.appendLog(progress.getPhase());
                    return;
                }
                progress.setStatus("COMPLETED");
                progress.setProgress(100);
                progress.setPhase("GraphSAGE 训练完成");
            } catch (Exception e) {
                progress.setStatus("FAILED");
                progress.setPhase("GraphSAGE 训练失败: " + e.getMessage());
                progress.setResult(progress.getPhase());
                progress.appendLog(progress.getPhase());
            }
        });
        } catch (RejectedExecutionException e) {
            progress.setStatus("FAILED");
            progress.setPhase("训练任务提交失败，线程池繁忙: " + e.getMessage());
            progress.setResult(progress.getPhase());
            progress.appendLog(progress.getPhase());
            return ApiResponse.error(progress.getPhase());
        }
        return ApiResponse.success(progress.getTaskId(), "GraphSAGE 训练任务已启动，任务ID: " + progress.getTaskId());
    }

    @GetMapping("/train-progress")
    public ApiResponse<TrainingProgress> trainProgress(@RequestParam("taskId") String taskId) {
        TrainingProgress progress = trainingTaskService.getTask(taskId);
        if (progress == null) {
            return ApiResponse.error("训练任务不存在: " + taskId);
        }
        return ApiResponse.success(progress, "查询成功");
    }

    @GetMapping("/train-running")
    public ApiResponse<TrainingProgress> trainRunning() {
        TrainingProgress progress = trainingTaskService.getRunningTask();
        return ApiResponse.success(progress, progress == null ? "当前无运行中的训练任务" : "查询成功");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/generate-embedding")
    public ApiResponse<String> generateEmbedding(@RequestParam("name") String foodName) {
        try {
            var emb = onnxInferenceService.generateAndSave(foodName);
            if (emb == null) {
                return ApiResponse.error("生成失败，请检查 ONNX 模型是否已加载");
            }
            return ApiResponse.success(foodName, "已为 [" + foodName + "] 生成 sage_embedding (128 维)");
        } catch (Exception e) {
            return ApiResponse.error("生成 embedding 失败: " + e.getMessage());
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/generate-embedding-missing")
    public ApiResponse<String> generateMissing() {
        try {
            int count = onnxInferenceService.generateForMissing();
            return ApiResponse.success(count + " 个", "已为 " + count + " 个缺失节点生成 sage_embedding");
        } catch (Exception e) {
            return ApiResponse.error("批量生成失败: " + e.getMessage());
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reload-onnx")
    public ApiResponse<String> reloadOnnx() {
        onnxInferenceService.loadModel();
        return ApiResponse.success(
            onnxInferenceService.isModelLoaded() ? "已加载" : "未找到",
            onnxInferenceService.isModelLoaded() ? "ONNX 模型重新加载成功" : "ONNX 模型文件不存在"
        );
    }

    

    @GetMapping("/stats")
    public ApiResponse<DataStatsResponse> stats() {
        try {
            long foodCount = neo4jMcpAdapter.countFoods();
            long recipeCount = neo4jMcpAdapter.countRecipes();
            long complementaryCount = neo4jMcpAdapter.countComplementary();
            long incompatibleCount = neo4jMcpAdapter.countIncompatible();
            long containsCount = neo4jMcpAdapter.countContainsRelations();
            DataStatsResponse stats = new DataStatsResponse(foodCount, recipeCount, complementaryCount, incompatibleCount, containsCount);
            return ApiResponse.success(stats, "统计成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("统计失败: " + e.getMessage());
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/mcp-health")
    public ApiResponse<Map<String, Object>> mcpHealth() {
        Map<String, Object> health = mcpHealthCheckService.check();
        boolean up = "UP".equals(String.valueOf(health.get("status")));
        return up
            ? ApiResponse.success(health, "MCP 连通正常")
            : new ApiResponse<>(health, "MCP 连通异常: " + String.valueOf(health.get("message")), false);
    }

}
