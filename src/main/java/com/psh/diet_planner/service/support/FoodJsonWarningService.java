package com.psh.diet_planner.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FoodJsonWarningService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodJsonWarningService.class);

    private final ObjectMapper objectMapper;
    private final Path dataFilePath;

    public FoodJsonWarningService(ObjectMapper objectMapper,
                                  @Value("${food.data.path:./data/foods}") String dataDir) {
        this.objectMapper = objectMapper;
        this.dataFilePath = Paths.get(dataDir).resolve("final_merged_food.json");
    }

    public List<String> findWarnings(List<String> foods) {
        if (foods == null || foods.size() < 2) {
            return List.of();
        }
        Set<String> warnings = new LinkedHashSet<>();
        Set<String> pairKeys = new LinkedHashSet<>();
        scanFileForWarnings(foods, warnings, pairKeys);
        return warnings.isEmpty() ? List.of() : new ArrayList<>(warnings);
    }

    private void scanFileForWarnings(List<String> foods, Set<String> warnings, Set<String> pairKeys) {
        if (!Files.exists(dataFilePath)) {
            LOGGER.warn("Food data file not found at {}", dataFilePath.toAbsolutePath());
            return;
        }
        Set<String> normalizedFoods = foods.stream()
            .map(this::normalize)
            .filter(StringUtils::hasText)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (normalizedFoods.size() < 2) {
            return;
        }

        try (InputStream in = Files.newInputStream(dataFilePath)) {
            ObjectReader reader = objectMapper.readerFor(JsonNode.class);
            MappingIterator<JsonNode> it = reader.readValues(in);
            while (it.hasNext()) {
                JsonNode node = it.next();
                if (node.isArray()) {
                    for (JsonNode item : node) {
                        handleFoodNode(item, normalizedFoods, warnings, pairKeys);
                    }
                } else {
                    handleFoodNode(node, normalizedFoods, warnings, pairKeys);
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to scan food incompatibility data: {}", ex.getMessage());
        }
    }

    private void handleFoodNode(JsonNode node, Set<String> normalizedFoods, Set<String> warnings, Set<String> pairKeys) {
        String foodName = normalize(node.path("食物名称").asText(null));
        if (foodName == null || !normalizedFoods.contains(foodName)) {
            return;
        }
        JsonNode incompatible = node.path("食物关系").path("互斥");
        if (!incompatible.isArray()) {
            return;
        }
        for (JsonNode relation : incompatible) {
            String targetName = normalize(relation.path("食物名称").asText(null));
            if (targetName == null || !normalizedFoods.contains(targetName)) {
                continue;
            }
            String pairKey = buildPairKey(foodName, targetName);
            if (pairKeys.contains(pairKey)) {
                continue;
            }
            String description = relation.path("描述").asText("");
            String reason = StringUtils.hasText(description) ? description : "暂无具体原因";
            warnings.add(foodName + "与" + targetName + "相克：" + reason);
            pairKeys.add(pairKey);
        }
    }

    private String buildPairKey(String a, String b) {
        if (a.compareTo(b) <= 0) {
            return a + "#" + b;
        }
        return b + "#" + a;
    }

    private String normalize(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.trim();
    }
}
