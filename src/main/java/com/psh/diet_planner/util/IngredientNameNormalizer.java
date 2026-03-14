package com.psh.diet_planner.util;

import java.util.HashMap;
import java.util.Map;

public class IngredientNameNormalizer {
    private static final Map<String, String> ALIAS = new HashMap<>();

    static {
        // 常见别名映射（示例，可根据数据再扩充）
        ALIAS.put("番茄", "西红柿");
        ALIAS.put("蕃茄", "西红柿");
        ALIAS.put("鸡胸肉", "鸡肉");
        ALIAS.put("葱花", "葱");
        ALIAS.put("蒜末", "大蒜");
        ALIAS.put("香菇", "蘑菇");
        ALIAS.put("土豆", "马铃薯");
        // 叶菜类
        ALIAS.put("白菜", "大白菜");
        ALIAS.put("小白菜", "大白菜");
        ALIAS.put("油麦菜", "生菜");
        ALIAS.put("香葱", "葱");
        ALIAS.put("大葱", "葱");
        ALIAS.put("小葱", "葱");
        // 根茎类
        ALIAS.put("红萝卜", "胡萝卜");
        ALIAS.put("白萝卜", "萝卜");
        ALIAS.put("紫洋葱", "洋葱");
        ALIAS.put("蒜头", "大蒜");
        ALIAS.put("蒜瓣", "大蒜");
        ALIAS.put("姜", "生姜");
        // 菌菇类
        ALIAS.put("口蘑", "蘑菇");
        ALIAS.put("平菇", "蘑菇");
        ALIAS.put("香信", "香菇");
        // 蛋/奶类
        ALIAS.put("鸡蛋液", "鸡蛋");
        ALIAS.put("蛋黄", "鸡蛋");
        ALIAS.put("蛋清", "鸡蛋");
        // 肉类
        ALIAS.put("鸡腿肉", "鸡肉");
        ALIAS.put("鸡翅", "鸡肉");
        ALIAS.put("里脊", "猪里脊");
        ALIAS.put("五花肉", "猪肉");
        ALIAS.put("瘦肉", "猪肉");
        ALIAS.put("牛里脊", "牛肉");
        // 水产类
        ALIAS.put("虾仁", "虾");
        ALIAS.put("鲜虾", "虾");
        ALIAS.put("大虾", "虾");
        ALIAS.put("基围虾", "虾");
        ALIAS.put("花蛤", "蛤蜊");
        ALIAS.put("鲫鱼", "鱼");
        ALIAS.put("三文鱼", "鲑鱼");
        // 豆制品
        ALIAS.put("老豆腐", "豆腐");
        ALIAS.put("嫩豆腐", "豆腐");
        ALIAS.put("豆干", "豆腐干");
        ALIAS.put("豆皮", "豆腐皮");
        // 主食/杂粮
        ALIAS.put("米饭", "大米");
        ALIAS.put("面条", "小麦面条");
        ALIAS.put("挂面", "小麦面条");
        ALIAS.put("意面", "意大利面");
        // 调味/配料（只用于规范名称）
        ALIAS.put("白砂糖", "糖");
        ALIAS.put("冰糖", "糖");
        ALIAS.put("食盐", "盐");
        ALIAS.put("海盐", "盐");
        ALIAS.put("酱油", "生抽");
        ALIAS.put("胡椒粉", "胡椒");
        ALIAS.put("黑胡椒", "胡椒");
        ALIAS.put("白胡椒", "胡椒");
        ALIAS.put("辣椒粉", "辣椒");
        ALIAS.put("小米椒", "辣椒");
        ALIAS.put("孜然粉", "孜然");
        ALIAS.put("孜然粒", "孜然");
        ALIAS.put("香油", "芝麻油");
    }

    /**
     * 标准化食材名称：
     * - 去除括号及其内容
     * - 去除数量单位与数字（如 100g, 2 个, 适量 等）
     * - 去除常见标点和空白
     * - 应用别名映射
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return s;

        // 去除中文/英文括号内容
        s = s.replaceAll("（[^）]*）", "");
        s = s.replaceAll("\u0028[^\u0029]*\u0029", ""); // () 英文括号

        // 去除数量和单位（简单规则）：数字 + 可选空格 + 常见单位词/个/克/g/斤/千克/毫升/ml/汤匙/适量 等
        s = s.replaceAll("[0-9]+\s*(个|枚|颗|克|g|斤|千克|公斤|毫升|ml|汤匙|勺|茶匙|适量|少许)", "");
        // 去除形容词/切法等常见词（可扩展）
        s = s.replaceAll("(切片|切条|切丁|切末|切段|去皮|去骨|洗净|拍碎|剁碎|焯水|备用)", "");

        // 去除标点与多余空白
        s = s.replaceAll("[，,；;。·\t\r\n]", "");
        s = s.replaceAll("\s+", "");

        // 应用别名映射（统一用全名）
        String lower = s;
        String canonical = ALIAS.getOrDefault(lower, s);
        return canonical;
    }
}
