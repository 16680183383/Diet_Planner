package com.psh.diet_planner.service;

import com.psh.diet_planner.dto.TrainingProgress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ModelTrainingService {

    private static final int OUTPUT_LIMIT = 2000;
    private static final Pattern EPOCH_PATTERN = Pattern.compile("Epoch\\s+(\\d+)");

    private final String pythonCommand;
    private final String condaEnv;
    private final String neo4jUri;
    private final String neo4jUsername;
    private final String neo4jPassword;
    private final Path metapath2VecDir;
    private final Path graphSageDir;

    public ModelTrainingService(
            @Value("${training.python-command:python}") String pythonCommand,
            @Value("${training.conda-env:}") String condaEnv,
            @Value("${spring.neo4j.uri:}") String neo4jUri,
            @Value("${spring.neo4j.authentication.username:}") String neo4jUsername,
            @Value("${spring.neo4j.authentication.password:}") String neo4jPassword,
            @Value("${training.metapath2vec.dir:../Metapath2Vec}") String metapath2VecDir,
            @Value("${training.graphsage.dir:../GraphSAGE}") String graphSageDir) {
        this.pythonCommand = pythonCommand;
        this.condaEnv = condaEnv;
        this.neo4jUri = neo4jUri;
        this.neo4jUsername = neo4jUsername;
        this.neo4jPassword = neo4jPassword;
        this.metapath2VecDir = Paths.get(metapath2VecDir).toAbsolutePath().normalize();
        this.graphSageDir = Paths.get(graphSageDir).toAbsolutePath().normalize();
    }

    public void trainMetapath2Vec(TrainingProgress progress) {
        Path workingDir = ensureDirectory(metapath2VecDir, "Metapath2Vec 工作目录不存在");
        progress.setTotalSteps(2);
        updateProgress(progress, 0, "准备执行 Metapath2Vec 训练");
        runScript(workingDir, "build_graph.py", progress, 5, 50, "metapath-build");
        progress.setCurrentStep(1);
        runScript(workingDir, "train_metapath_model.py", progress, 55, 98, "metapath-train");
        progress.setCurrentStep(2);
        progress.setResult("Metapath2Vec 训练完成");
    }

    public void trainGraphSage(TrainingProgress progress) {
        Path workingDir = ensureDirectory(graphSageDir, "GraphSAGE 工作目录不存在");
        progress.setTotalSteps(2);
        updateProgress(progress, 0, "准备执行 GraphSAGE 训练");
        runScript(workingDir, "diet_preparer.py", progress, 5, 45, "graphsage-prepare");
        progress.setCurrentStep(1);
        runScript(workingDir, "train_rgcn.py", progress, 50, 96, "graphsage-train");
        progress.setCurrentStep(2);
        progress.setResult("GraphSAGE 训练完成");
    }

    private Path ensureDirectory(Path dir, String errorMessage) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException(errorMessage + ": " + dir);
        }
        return dir;
    }

    private void runScript(Path workingDir, String scriptName, TrainingProgress progress,
                           int startProgress, int endProgress, String stage) {
        Path scriptPath = workingDir.resolve(scriptName);
        if (!Files.exists(scriptPath)) {
            throw new IllegalArgumentException("脚本不存在: " + scriptPath);
        }
        List<String> command = new ArrayList<>();
        if (condaEnv != null && !condaEnv.isBlank()) {
            command.addAll(List.of("conda", "run", "--no-capture-output", "-n", condaEnv, pythonCommand, "-u", scriptName));
        } else {
            command.addAll(List.of(pythonCommand, "-u", scriptName));
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("CONDA_NO_PLUGINS", "true");
        pb.environment().put("CONDA_REPORT_ERRORS", "false");
        pb.environment().put("PYTHONUNBUFFERED", "1");
        // Keep Python scripts aligned with the active Spring Neo4j configuration.
        if (neo4jUri != null && !neo4jUri.isBlank()) {
            pb.environment().put("NEO4J_URI", neo4jUri);
        }
        if (neo4jUsername != null && !neo4jUsername.isBlank()) {
            pb.environment().put("NEO4J_USER", neo4jUsername);
        }
        if (neo4jPassword != null && !neo4jPassword.isBlank()) {
            pb.environment().put("NEO4J_PASSWORD", neo4jPassword);
        }
        pb.redirectErrorStream(true);
        updateProgress(progress, startProgress, "正在执行 " + scriptName);
        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (!output.isEmpty()) {
                        output.append(System.lineSeparator());
                    }
                    output.append(line);
                    progress.appendLog(line);
                    updateStageProgress(progress, stage, line, startProgress, endProgress);
                }
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IllegalStateException(scriptName + " 退出码 " + exitCode + "，输出: " + abbreviate(output.toString()));
                }
            }
            updateProgress(progress, endProgress, scriptName + " 执行完成");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(scriptName + " 被中断", ie);
        } catch (IOException e) {
            throw new IllegalStateException(scriptName + " 执行失败: " + e.getMessage(), e);
        }
    }

    private void updateStageProgress(TrainingProgress progress, String stage, String line,
                                     int startProgress, int endProgress) {
        if (line == null || line.isBlank()) {
            return;
        }
        String trimmed = line.trim();
        switch (stage) {
            case "metapath-build" -> updateMetapathBuildProgress(progress, trimmed, startProgress, endProgress);
            case "metapath-train" -> updateMetapathTrainProgress(progress, trimmed, startProgress, endProgress);
            case "graphsage-prepare" -> updateGraphSagePrepareProgress(progress, trimmed, startProgress, endProgress);
            case "graphsage-train" -> updateGraphSageTrainProgress(progress, trimmed, startProgress, endProgress);
            default -> updateProgress(progress, startProgress, trimmed);
        }
    }

    private void updateMetapathBuildProgress(TrainingProgress progress, String line,
                                             int startProgress, int endProgress) {
        if (line.contains("导出图数据")) {
            updateProgress(progress, Math.max(startProgress, 10), "正在从 Neo4j 导出图数据");
        } else if (line.contains("图构建完成")) {
            updateProgress(progress, Math.max(startProgress, 22), "图结构构建完成");
        } else if (line.contains("正在生成 walks_comp")) {
            updateProgress(progress, 30, "正在生成互补关系游走");
        } else if (line.contains("正在生成 walks_incomp")) {
            updateProgress(progress, 36, "正在生成相克关系游走");
        } else if (line.contains("正在生成 walks_overlap")) {
            updateProgress(progress, 42, "正在生成重叠关系游走");
        } else if (line.contains("正在生成 walks_rfr")) {
            updateProgress(progress, 48, "正在生成食谱-食材游走");
        } else if (line.contains("所有任务处理完毕")) {
            updateProgress(progress, endProgress, "游走文件生成完成");
        }
    }

    private void updateMetapathTrainProgress(TrainingProgress progress, String line,
                                             int startProgress, int endProgress) {
        if (line.contains("--- 正在训练元路径:")) {
            if (line.contains("RFR")) {
                updateProgress(progress, 60, "正在训练 RFR 元路径");
            } else if (line.contains("COMP")) {
                updateProgress(progress, 68, "正在训练 COMP 元路径");
            } else if (line.contains("INCOMP")) {
                updateProgress(progress, 76, "正在训练 INCOMP 元路径");
            } else if (line.contains("OVERLAP")) {
                updateProgress(progress, 84, "正在训练 OVERLAP 元路径");
            }
        } else if (line.contains("--- 正在写回") && line.contains("RFR")) {
            updateProgress(progress, 88, "正在写回 RFR 向量到 Neo4j");
        } else if (line.contains("--- 正在写回") && line.contains("COMP")) {
            updateProgress(progress, 91, "正在写回 COMP 向量到 Neo4j");
        } else if (line.contains("--- 正在写回") && line.contains("INCOMP")) {
            updateProgress(progress, 94, "正在写回 INCOMP 向量到 Neo4j");
        } else if (line.contains("--- 正在写回") && line.contains("OVERLAP")) {
            updateProgress(progress, 97, "正在写回 OVERLAP 向量到 Neo4j");
        } else if (line.contains("所有嵌入向量写回 Neo4j 完成")) {
            updateProgress(progress, endProgress, "Metapath2Vec 向量写回完成");
        } else if (line.contains("跳过")) {
            updateProgress(progress, Math.max(progress.getProgress(), startProgress), line);
        }
    }

    private void updateGraphSagePrepareProgress(TrainingProgress progress, String line,
                                                int startProgress, int endProgress) {
        if (line.contains("载入") && line.contains("向量")) {
            updateProgress(progress, 18, "正在读取 Metapath2Vec 向量");
        } else if (line.contains("数据准备完毕")) {
            updateProgress(progress, endProgress, "GraphSAGE 训练数据准备完成");
        }
    }

    private void updateGraphSageTrainProgress(TrainingProgress progress, String line,
                                              int startProgress, int endProgress) {
        if (line.contains("开始训练")) {
            updateProgress(progress, 58, "GraphSAGE 训练已开始");
            return;
        }
        Matcher matcher = EPOCH_PATTERN.matcher(line);
        if (matcher.find()) {
            int epoch = Integer.parseInt(matcher.group(1));
            int mapped = 60 + (int) Math.round(Math.min(epoch, 200) * 30.0 / 200.0);
            updateProgress(progress, mapped, "训练中: " + line);
            return;
        }
        if (line.contains("模型权重已保存")) {
            updateProgress(progress, 93, "模型权重已保存");
        } else if (line.contains("向量结果已保存")) {
            updateProgress(progress, 95, "GraphSAGE 向量结果已保存");
        } else if (line.contains("ONNX 推理模型已导出")) {
            updateProgress(progress, endProgress, "ONNX 推理模型已导出");
        }
    }

    private void updateProgress(TrainingProgress progress, int nextProgress, String phase) {
        if (progress == null) {
            return;
        }
        if (nextProgress > progress.getProgress()) {
            progress.setProgress(nextProgress);
        }
        if (phase != null && !phase.isBlank()) {
            progress.setPhase(phase);
        }
    }

    private String abbreviate(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= OUTPUT_LIMIT) {
            return text;
        }
        return text.substring(text.length() - OUTPUT_LIMIT);
    }
}
