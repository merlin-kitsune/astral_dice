package com.merlinkitsune.astral_dice.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 星愿铁砧的数据包模型（{@code data/<namespace>/astral_dice/anvil/*.json}，schema_version = 1）。
 *
 * <p>字段口径：
 * <ul>
 *   <li>{@code star_coin_costs}：按星阶索引的升星材料面额总量（0★→第 0 项）；索引越界或 >= max_star 视为不可升；</li>
 *   <li>{@code xp_costs}：实验模式按骰子分级 dice_t0..dice_t4 索引的经验等级成本（与星级无关，沿用 2026-11-15 之前的语义）；</li>
 *   <li>{@code xp_cost_normal}：非实验模式的固定经验等级成本（缺省 10，与旧硬编码一致）；</li>
 *   <li>{@code max_star}：可升到的最高星级（默认数据文件为 3）；</li>
 *   <li>{@code material_tag}：材料槽的标签口径（改这个字段即可替换槽位过滤）；</li>
 *   <li>{@code materials}：物品 id → 单件面额；只被标签收录、未列入本表的物品按面额 1 计；</li>
 *   <li>{@code reset_on_craft}：升阶合成是否归还母体卡牌；{@code null}（缺省）= 回退配置项 experimental_star_upgrade；</li>
 *   <li>{@code overrides}：保留区，本版不消费任何键，仅原样保存，便于数据包留自定义字段。</li>
 * </ul>
 *
 * <p>本类只做**解析与只读快照**，不持有注册表引用，因此可被五条加载器线共用：id 只保存字符串，
 * 由各平台在边界上转换成自己的 id 类型（1.20.1/1.21.1 的 ResourceLocation、26.1.2 的 Identifier）。
 */
public final class AnvilUpgradeData {

    /** 本模型支持的 schema 版本；数据包写别的版本整份跳过（记一次 WARN）。 */
    public static final int SCHEMA_VERSION = 1;

    /** 默认材料标签（与 ModTags.Items.ASTRAL_ANVIL_MATERIALS 同值）。 */
    public static final String DEFAULT_MATERIAL_TAG = "astral_dice:astral_anvil_materials";

    /** 默认星币物品 id（兜底面额表用）。 */
    private static final String DEFAULT_MATERIAL_ITEM = "astral_dice:star_coin";

    /** 未加载数据包时的兜底快照：数值与 2026-11-15 之前的硬编码逐字一致，保证行为不变。 */
    private static final AnvilUpgradeData FALLBACK = new AnvilUpgradeData(
            SCHEMA_VERSION,
            List.of(15, 20, 25),
            List.of(5, 10, 15, 20, 30),
            10,
            3,
            ResourceId.require(DEFAULT_MATERIAL_TAG, "内置 material_tag"),
            Map.of(ResourceId.require(DEFAULT_MATERIAL_ITEM, "内置 materials"), 1),
            null,
            Map.of());

    private final int schemaVersion;
    private final List<Integer> starCoinCosts;
    private final List<Integer> xpCosts;
    private final int xpCostNormal;
    private final int maxStar;
    private final String materialTag;
    private final Map<String, Integer> materials;
    private final Boolean resetOnCraft;
    private final Map<String, JsonElement> overrides;

    private AnvilUpgradeData(int schemaVersion,
                             List<Integer> starCoinCosts,
                             List<Integer> xpCosts,
                             int xpCostNormal,
                             int maxStar,
                             String materialTag,
                             Map<String, Integer> materials,
                             Boolean resetOnCraft,
                             Map<String, JsonElement> overrides) {
        this.schemaVersion = schemaVersion;
        this.starCoinCosts = List.copyOf(starCoinCosts);
        this.xpCosts = List.copyOf(xpCosts);
        this.xpCostNormal = xpCostNormal;
        this.maxStar = maxStar;
        this.materialTag = materialTag;
        this.materials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
        this.resetOnCraft = resetOnCraft;
        this.overrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
    }

    /** 未加载数据包时的兜底快照（数值 = 旧硬编码）。 */
    public static AnvilUpgradeData fallback() {
        return FALLBACK;
    }

    /**
     * 解析一份铁砧数据文件。
     *
     * @throws IllegalArgumentException 结构性错误（版本不符、字段类型错、id 非法、空列表等）；调用方应整份跳过并记 WARN
     */
    public static AnvilUpgradeData parse(JsonObject json) {
        if (json == null) {
            throw new IllegalArgumentException("文件内容不是 JSON 对象");
        }
        int schemaVersion = optInt(json, "schema_version", SCHEMA_VERSION);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的 schema_version=" + schemaVersion + "（本模组只支持 " + SCHEMA_VERSION + "）");
        }
        List<Integer> starCoinCosts = readIntList(json, "star_coin_costs", FALLBACK.starCoinCosts, 1);
        List<Integer> xpCosts = readIntList(json, "xp_costs", FALLBACK.xpCosts, 0);
        int xpCostNormal = optInt(json, "xp_cost_normal", FALLBACK.xpCostNormal);
        if (xpCostNormal < 1) {
            throw new IllegalArgumentException("xp_cost_normal 必须 >= 1，实际 " + xpCostNormal);
        }
        int maxStar = optInt(json, "max_star", FALLBACK.maxStar);
        if (maxStar < 1) {
            throw new IllegalArgumentException("max_star 必须 >= 1，实际 " + maxStar);
        }
        String materialTag = ResourceId.require(optString(json, "material_tag", DEFAULT_MATERIAL_TAG), "material_tag");
        Map<String, Integer> materials = readMaterials(json.get("materials"));
        Boolean resetOnCraft = readNullableBoolean(json, "reset_on_craft");
        Map<String, JsonElement> overrides = readOverrides(json.get("overrides"));
        return new AnvilUpgradeData(schemaVersion, starCoinCosts, xpCosts, xpCostNormal, maxStar, materialTag, materials, resetOnCraft, overrides);
    }

    public int schemaVersion() {
        return this.schemaVersion;
    }

    public List<Integer> starCoinCosts() {
        return this.starCoinCosts;
    }

    public List<Integer> xpCosts() {
        return this.xpCosts;
    }

    /** 非实验模式的固定经验等级成本；默认 10。 */
    public int xpCostNormal() {
        return this.xpCostNormal;
    }

    public int maxStar() {
        return this.maxStar;
    }

    public String materialTag() {
        return this.materialTag;
    }

    public Map<String, Integer> materials() {
        return this.materials;
    }

    /** {@code null} = 数据包未表态，回退配置项语义。 */
    public Boolean resetOnCraft() {
        return this.resetOnCraft;
    }

    public Map<String, JsonElement> overrides() {
        return this.overrides;
    }

    /**
     * 某个星阶升星所需的材料面额总量。
     *
     * @return 正数为成本；{@code -1} = 不可升（星阶非法、超出 max_star 或超出 star_coin_costs 长度）
     */
    public int starCoinCostFor(int starLevel) {
        if (starLevel < 0 || starLevel >= this.maxStar || starLevel >= this.starCoinCosts.size()) {
            return -1;
        }
        return this.starCoinCosts.get(starLevel);
    }

    /**
     * 某个骰子分级（dice_t0..dice_t4 ⇒ 0..4）的经验成本；未命中任何分级（-1）按第 0 项（既有的「未分级 = 5 级」口径）。
     */
    public int xpCostForTier(int tier) {
        if (this.xpCosts.isEmpty()) {
            return 0;
        }
        int index = tier < 0 ? 0 : Math.min(tier, this.xpCosts.size() - 1);
        return this.xpCosts.get(index);
    }

    /** 物品 id 的单件面额；未列入 materials ⇒ 0（是否按面额 1 兜底由调用方决定）。 */
    public int denomination(String itemId) {
        if (itemId == null) {
            return 0;
        }
        Integer value = this.materials.get(itemId);
        return value == null ? 0 : value;
    }

    @Override
    public String toString() {
        return "AnvilUpgradeData[schema=" + this.schemaVersion + ", star_coin_costs=" + this.starCoinCosts
                + ", xp_costs=" + this.xpCosts + ", xp_cost_normal=" + this.xpCostNormal + ", max_star=" + this.maxStar + ", material_tag=" + this.materialTag
                + ", materials=" + this.materials + ", reset_on_craft=" + this.resetOnCraft + "]";
    }

    // ---------------------------------------------------------------- 解析辅助

    private static List<Integer> readIntList(JsonObject json, String key, List<Integer> fallback, int minValue) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(key + " 必须是整数数组");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            throw new IllegalArgumentException(key + " 不能为空数组");
        }
        List<Integer> values = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(key + "[" + i + "] 不是整数");
            }
            int value = item.getAsInt();
            if (value < minValue) {
                throw new IllegalArgumentException(key + "[" + i + "] = " + value + " 小于下限 " + minValue);
            }
            values.add(value);
        }
        return values;
    }

    private static Map<String, Integer> readMaterials(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return FALLBACK.materials;
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("materials 必须是 {物品id: 面额} 对象");
        }
        Map<String, Integer> materials = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String id = ResourceId.require(entry.getKey(), "materials 的键");
            JsonElement amount = entry.getValue();
            if (amount == null || !amount.isJsonPrimitive() || !amount.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("materials[" + entry.getKey() + "] 不是整数");
            }
            int value = amount.getAsInt();
            if (value <= 0) {
                throw new IllegalArgumentException("materials[" + entry.getKey() + "] = " + value + " 必须 > 0");
            }
            materials.put(id, value);
        }
        return materials;
    }

    private static Boolean readNullableBoolean(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " 必须是布尔值或 null");
        }
        return element.getAsBoolean();
    }

    private static Map<String, JsonElement> readOverrides(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("overrides 必须是 JSON 对象");
        }
        Map<String, JsonElement> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            overrides.put(entry.getKey(), entry.getValue());
        }
        return overrides;
    }

    private static int optInt(JsonObject json, String key, int fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " 必须是整数");
        }
        return element.getAsInt();
    }

    private static String optString(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " 必须是字符串");
        }
        return element.getAsString();
    }

}
