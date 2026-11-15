package com.merlinkitsune.astral_dice.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 商店报价池的数据包模型（{@code data/<namespace>/astral_dice/shop/*.json}，schema_version = 1）。
 *
 * <p>只承载**池成员**：卡牌池的每张卡与筹码池的每枚筹码。价格、品质概率、刷新等玩法常数仍留在代码里。
 * 平台目录在重载后调用 {@code applyData}；空列表表示该侧继续使用内置默认池。
 */
public final class ShopData {

    public static final int SCHEMA_VERSION = 1;

    public record Chip(String id, int tier, String school, String category) {
    }

    public record Card(String item, int weight, boolean combat, int minStarLevel) {
    }

    private final List<Chip> chips;
    private final List<Card> cards;

    private ShopData(List<Chip> chips, List<Card> cards) {
        this.chips = List.copyOf(chips);
        this.cards = List.copyOf(cards);
    }

    public static ShopData empty() {
        return new ShopData(List.of(), List.of());
    }

    public static ShopData of(List<Chip> chips, List<Card> cards) {
        return new ShopData(chips, cards);
    }

    public static ShopData parse(JsonObject json) {
        if (json == null) {
            throw new IllegalArgumentException("商店数据不是 JSON 对象");
        }
        int schema = json.has("schema_version") ? json.get("schema_version").getAsInt() : 0;
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的商店 schema_version: " + schema);
        }
        List<Chip> chips = new ArrayList<>();
        List<Card> cards = new ArrayList<>();
        if (json.has("chips")) {
            JsonArray array = json.getAsJsonArray("chips");
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                String id = requireString(entry, "id");
                if (!id.matches("[a-z0-9_]+")) {
                    throw new IllegalArgumentException("筹码 id 非法: " + id);
                }
                int tier = requireInt(entry, "tier");
                if (tier < 1 || tier > 3) {
                    throw new IllegalArgumentException("筹码 " + id + " 的 tier 必须在 1..3");
                }
                chips.add(new Chip(id, tier, requireString(entry, "school"), requireString(entry, "category")));
            }
        }
        if (json.has("cards")) {
            JsonArray array = json.getAsJsonArray("cards");
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                String item = ResourceId.require(requireString(entry, "item"), "商店卡牌 id");
                int weight = requireInt(entry, "weight");
                if (weight < 1) {
                    throw new IllegalArgumentException("卡牌 " + item + " 的 weight 必须 >= 1");
                }
                boolean combat = entry.has("combat") && entry.get("combat").getAsBoolean();
                int minStar = entry.has("min_star_level") ? entry.get("min_star_level").getAsInt() : 0;
                if (minStar < 0) {
                    throw new IllegalArgumentException("卡牌 " + item + " 的 min_star_level 不能为负");
                }
                cards.add(new Card(item, weight, combat, minStar));
            }
        }
        return new ShopData(chips, cards);
    }

    private static String requireString(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("缺少字符串字段 " + key);
        }
        String value = object.get(key).getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("字段 " + key + " 不能为空");
        }
        return value;
    }

    private static int requireInt(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("缺少整数字段 " + key);
        }
        return object.get(key).getAsInt();
    }

    public List<Chip> chips() {
        return this.chips;
    }

    public List<Card> cards() {
        return this.cards;
    }

    public boolean isEmpty() {
        return this.chips.isEmpty() && this.cards.isEmpty();
    }

    @Override
    public String toString() {
        return "ShopData[chips=" + this.chips.size() + ", cards=" + this.cards.size() + "]";
    }
}
