package com.merlinkitsune.astral_dice.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.merlinkitsune.astral_dice.event.LootInjectionHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 扫描 {@code data/<namespace>/astral_dice/loot/*.json} 并把各文件的 rules 合并到 {@link AstralDiceDataStore}。
 *
 * <p>口径：
 * <ul>
 *   <li>单条规则非法 ⇒ 只记一次 WARN 跳过该条，其余规则照常生效；</li>
 *   <li>顶层结构错误 ⇒ 整份文件跳过并记一次 WARN；</li>
 *   <li>规则 id 全局唯一（跨文件）：先出现的保留，后出现的记 WARN 忽略，防止同一规则注入两个池；</li>
 *   <li>一个有效文件都没有 ⇒ {@link AstralDiceDataStore#clearLoot()}（零规则 = 不注入，绝不抛异常）。</li>
 * </ul>
 */
public final class LootInjectionReloadListener extends SimpleJsonResourceReloadListener {

    /** 数据包目录（相对 {@code data/<namespace>/}）。 */
    public static final String DIRECTORY = "astral_dice/loot";

    private static final Logger LOGGER = LoggerFactory.getLogger(LootInjectionReloadListener.class);

    /** {@code AddReloadListenerEvent} 时从 {@link LootInjectionHandler#drainPending()} 取到的本次重载表。 */
    private final List<LootInjectionHandler.PendingTable> pending;

    public LootInjectionReloadListener(List<LootInjectionHandler.PendingTable> pending) {
        super(new Gson(), DIRECTORY);
        this.pending = List.copyOf(pending);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, LootRule> merged = new LinkedHashMap<>();
        int validFiles = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : new TreeMap<>(files).entrySet()) {
            final ResourceLocation fileId = entry.getKey();
            try {
                JsonElement element = entry.getValue();
                if (element == null || !element.isJsonObject()) {
                    throw new IllegalArgumentException("文件内容不是 JSON 对象");
                }
                LootInjectionData parsed = LootInjectionData.parse(
                        element.getAsJsonObject(),
                        message -> LOGGER.warn("[astral_dice] 战利品数据 {} 的 {} ", fileId, message));
                validFiles++;
                for (LootRule rule : parsed.rules()) {
                    LootRule previous = merged.putIfAbsent(rule.id(), rule);
                    if (previous != null) {
                        LOGGER.warn("[astral_dice] 战利品规则 id 重复，保留先出现的一条: {} (规则 {} 与 {})", rule.id(), previous, rule);
                    }
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("[astral_dice] 跳过非法战利品数据 {}: {}", fileId, ex.getMessage());
            }
        }
        if (validFiles == 0) {
            AstralDiceDataStore.clearLoot();
            LOGGER.info("[astral_dice] 未找到有效的 data/*/astral_dice/loot/*.json，战利品注入停用（零规则）");
            return;
        }
        AstralDiceDataStore.replaceLoot(LootInjectionData.of(new ArrayList<>(merged.values())));
        LootInjectionHandler.injectPending(this.pending, AstralDiceDataStore.loot());
        LOGGER.info("[astral_dice] 战利品注入规则已加载 {} 条（来自 {} 个文件，回填 {} 张表）",
                merged.size(), validFiles, this.pending.size());
    }
}
