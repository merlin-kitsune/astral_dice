package com.merlinkitsune.astral_dice.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 单条战利品注入规则（{@code data/<namespace>/astral_dice/loot/*.json} 的 {@code rules[]} 元素）。
 *
 * <p>匹配口径：
 * <ul>
 *   <li>{@code target_kind} 决定注入通道：{@code chest} 只对 {@code minecraft:chests/*} 生效，
 *       {@code entity} / {@code monster} 只对 {@code minecraft:entities/*} 生效；</li>
 *   <li>{@code targets} 支持精确 id（{@code minecraft:chests/end_city_treasure}）与
 *       **后缀通配**（{@code minecraft:chests/*} = 命名空间 + 路径前缀）；</li>
 *   <li>{@code table} 是注入到目标表里的「表引用」目标（原版 NestedLootTable / loot_table 条目）；</li>
 *   <li>{@code require_player_kill} 为 true 时，注入的池额外加 killed_by_player 条件；</li>
 *   <li>{@code monster_only} 为 true 时，只有目标表对应实体是 {@code MobCategory.MONSTER} 才命中
 *       （用于「其余 Monster 0.3%」这类宽泛规则，避免误伤动物/村民）。</li>
 * </ul>
 *
 * <p>同一通道同一张表命中多条时，取 {@code priority} 最大者（同值取先出现的）。
 */
public final class LootRule {

    public static final String KIND_CHEST = "chest";
    public static final String KIND_ENTITY = "entity";
    public static final String KIND_MONSTER = "monster";

    private final String id;
    private final int priority;
    private final String targetKind;
    private final List<String> targets;
    private final String table;
    private final boolean requirePlayerKill;
    private final boolean monsterOnly;

    private LootRule(String id, int priority, String targetKind, List<String> targets,
                     String table, boolean requirePlayerKill, boolean monsterOnly) {
        this.id = id;
        this.priority = priority;
        this.targetKind = targetKind;
        this.targets = List.copyOf(targets);
        this.table = table;
        this.requirePlayerKill = requirePlayerKill;
        this.monsterOnly = monsterOnly;
    }

    /**
     * 解析一条规则。
     *
     * @throws IllegalArgumentException 字段缺失或类型错误；调用方应跳过该条并记 WARN
     */
    public static LootRule parse(JsonObject json) {
        if (json == null) {
            throw new IllegalArgumentException("规则不是 JSON 对象");
        }
        String id = requireString(json, "id");
        if (!isValidId(id)) {
            throw new IllegalArgumentException("id 只能是小写字母 / 数字 / _ / - / .，实际: " + id);
        }
        int priority = json.has("priority") && !json.get("priority").isJsonNull() ? requireInt(json, "priority") : 0;
        String kind = requireString(json, "target_kind").toLowerCase(Locale.ROOT);
        if (!KIND_CHEST.equals(kind) && !KIND_ENTITY.equals(kind) && !KIND_MONSTER.equals(kind)) {
            throw new IllegalArgumentException("target_kind 只能是 chest / entity / monster，实际: " + kind);
        }
        List<String> targets = requireStringList(json, "targets");
        String table = ResourceId.require(requireString(json, "table"), "table");
        boolean requirePlayerKill = false;
        JsonElement kill = json.get("require_player_kill");
        if (kill != null && !kill.isJsonNull()) {
            if (!kill.isJsonPrimitive() || !kill.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("require_player_kill 必须是布尔值");
            }
            requirePlayerKill = kill.getAsBoolean();
        }
        boolean monsterOnly = false;
        JsonElement monster = json.get("monster_only");
        if (monster != null && !monster.isJsonNull()) {
            if (!monster.isJsonPrimitive() || !monster.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("monster_only 必须是布尔值");
            }
            monsterOnly = monster.getAsBoolean();
        }
        if (KIND_CHEST.equals(kind) && requirePlayerKill) {
            throw new IllegalArgumentException("target_kind=chest 不支持 require_player_kill（箱子没有击杀者）");
        }
        return new LootRule(id, priority, kind, targets, table, requirePlayerKill, monsterOnly);
    }

    public String id() {
        return this.id;
    }

    public int priority() {
        return this.priority;
    }

    public String targetKind() {
        return this.targetKind;
    }

    public List<String> targets() {
        return this.targets;
    }

    public String table() {
        return this.table;
    }

    public boolean requirePlayerKill() {
        return this.requirePlayerKill;
    }

    public boolean monsterOnly() {
        return this.monsterOnly;
    }

    /** 本规则的 targets 是否覆盖某张具体的表 id（精确匹配或 namespace:path/* 前缀通配）。 */
    public boolean matches(String tableId) {
        if (tableId == null) {
            return false;
        }
        String actual = tableId;
        for (String pattern : this.targets) {
            if (pattern.endsWith("/*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (actual.startsWith(prefix)) {
                    return true;
                }
            } else if (actual.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "LootRule[" + this.id + ", priority=" + this.priority + ", kind=" + this.targetKind
                + ", targets=" + this.targets + ", table=" + this.table + ", require_player_kill=" + this.requirePlayerKill
                + ", monster_only=" + this.monsterOnly + "]";
    }

    // ---------------------------------------------------------------- 解析辅助

    private static boolean isValidId(String id) {
        if (id.isEmpty() || id.length() > 64) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static String requireString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " 缺失或不是字符串");
        }
        return element.getAsString();
    }

    private static int requireInt(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " 不是整数");
        }
        return element.getAsInt();
    }

    private static List<String> requireStringList(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            throw new IllegalArgumentException(key + " 必须是字符串数组");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            throw new IllegalArgumentException(key + " 不能为空数组");
        }
        List<String> values = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + "[" + i + "] 不是字符串");
            }
            String value = item.getAsString();
            if (value.isEmpty() || !value.contains(":")) {
                throw new IllegalArgumentException(key + "[" + i + "] 不是合法的目标 id: " + value);
            }
            values.add(value);
        }
        return values;
    }

}
