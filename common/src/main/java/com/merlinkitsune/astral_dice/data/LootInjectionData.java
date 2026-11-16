package com.merlinkitsune.astral_dice.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 战利品注入数据（{@code data/<namespace>/astral_dice/loot/*.json}，schema_version = 1）。
 *
 * <p>一个文件 = 一批规则；加载时各文件的 rules 合并，**非法单条跳过并只记一次 WARN**，不让整包崩。
 * 同一 id 只在全局出现一次（先到先得），避免数据包叠加时同一条规则注入两个池。
 */
public final class LootInjectionData {

    public static final int SCHEMA_VERSION = 1;

    private static final LootInjectionData EMPTY = new LootInjectionData(SCHEMA_VERSION, List.of());

    private final int schemaVersion;
    private final List<LootRule> rules;

    private LootInjectionData(int schemaVersion, List<LootRule> rules) {
        this.schemaVersion = schemaVersion;
        this.rules = List.copyOf(rules);
    }

    /** 未加载时的安全空快照（零规则 = 不注入任何东西，不抛异常）。 */
    public static LootInjectionData empty() {
        return EMPTY;
    }

    public static LootInjectionData of(List<LootRule> rules) {
        return new LootInjectionData(SCHEMA_VERSION, rules);
    }

    /**
     * 解析一份战利品注入文件。
     *
     * @param warn 单条规则非法时的告警回调（每个非法条目调用一次）
     * @throws IllegalArgumentException 顶层结构错误（版本不符 / rules 不是数组），调用方应整份跳过
     */
    public static LootInjectionData parse(JsonObject json, Consumer<String> warn) {
        if (json == null) {
            throw new IllegalArgumentException("文件内容不是 JSON 对象");
        }
        int schemaVersion = SCHEMA_VERSION;
        JsonElement schema = json.get("schema_version");
        if (schema != null && !schema.isJsonNull()) {
            if (!schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("schema_version 必须是整数");
            }
            schemaVersion = schema.getAsInt();
            if (schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("不支持的 schema_version=" + schemaVersion + "（本模组只支持 " + SCHEMA_VERSION + "）");
            }
        }
        JsonElement rulesElement = json.get("rules");
        if (rulesElement == null || !rulesElement.isJsonArray()) {
            throw new IllegalArgumentException("rules 必须是数组");
        }
        List<LootRule> parsedRules = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int index = 0;
        for (JsonElement element : rulesElement.getAsJsonArray()) {
            index++;
            if (element == null || !element.isJsonObject()) {
                warn.accept("rules[" + index + "] 不是 JSON 对象，已跳过");
                continue;
            }
            try {
                LootRule rule = LootRule.parse(element.getAsJsonObject());
                if (!ids.add(rule.id())) {
                    warn.accept("rules[" + index + "] 的 id '" + rule.id() + "' 在本文件内重复，已跳过");
                    continue;
                }
                parsedRules.add(rule);
            } catch (RuntimeException ex) {
                warn.accept("rules[" + index + "] 已跳过: " + ex.getMessage());
            }
        }
        return new LootInjectionData(schemaVersion, parsedRules);
    }

    public int schemaVersion() {
        return this.schemaVersion;
    }

    public List<LootRule> rules() {
        return this.rules;
    }

    public boolean isEmpty() {
        return this.rules.isEmpty();
    }

    /**
     * 某个通道里覆盖该表、优先级最高的规则。
     *
     * @param kind    {@link LootRule#KIND_CHEST} / {@link LootRule#KIND_ENTITY} / {@link LootRule#KIND_MONSTER}
     * @param tableId 当前正在加载的原版表 id
     * @return 命中的最高优先级规则；无命中 ⇒ {@code null}
     */
    public LootRule bestRule(String kind, String tableId) {
        return bestRule(kind, tableId, false);
    }

    /**
     * 某个通道里覆盖该表、优先级最高的规则。
     *
     * @param kind    {@link LootRule#KIND_CHEST} / {@link LootRule#KIND_ENTITY} / {@link LootRule#KIND_MONSTER}
     * @param tableId 当前正在加载的原版表 id
     * @param monster 该表对应的实体是否为 {@code MobCategory.MONSTER}；
     *                {@link LootRule#monsterOnly()} 为 true 的规则在此为 false 时不参与匹配
     * @return 命中的最高优先级规则；无命中 ⇒ {@code null}
     */
    public LootRule bestRule(String kind, String tableId, boolean monster) {
        LootRule best = null;
        for (LootRule rule : this.rules) {
            if (!rule.targetKind().equals(kind) || !rule.matches(tableId)) {
                continue;
            }
            // monster 通道本身就是「只对敌对生物表生效」：目标表不是 MONSTER 时整条规则跳过，
            // 否则 target_kind=monster + targets=[minecraft:entities/*] 会把动物也算进去。
            if (LootRule.KIND_MONSTER.equals(kind) && !monster) {
                continue;
            }
            if (rule.monsterOnly() && !monster) {
                continue;
            }
            if (best == null || rule.priority() > best.priority()) {
                best = rule;
            }
        }
        return best;
    }

    @Override
    public String toString() {
        return "LootInjectionData[schema=" + this.schemaVersion + ", rules=" + this.rules.size() + "]";
    }

    /** 只读视图（给日志/调试用）。 */
    public List<LootRule> rulesView() {
        return Collections.unmodifiableList(this.rules);
    }
}
