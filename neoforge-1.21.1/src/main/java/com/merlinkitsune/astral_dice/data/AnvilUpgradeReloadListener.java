package com.merlinkitsune.astral_dice.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;

/**
 * 扫描 {@code data/<namespace>/astral_dice/anvil/*.json} 并落到 {@link AstralDiceDataStore}（1.21.1 / NeoForge 21.1.265）。
 *
 * <p>口径：
 * <ul>
 *   <li>单文件非法（版本不符 / 字段类型错）⇒ 记一次 WARN 跳过该文件，**不影响其它文件、不崩整包**；</li>
 *   <li>多文件同时存在 ⇒ 按资源 id 字典序取最后一个有效文件，并记一次 WARN（避免多数据包静默互相覆盖）；</li>
 *   <li>一个有效文件都没有 ⇒ {@link AstralDiceDataStore#clearAnvil()}，回到内置兜底数值（= 旧硬编码）。</li>
 * </ul>
 */
public final class AnvilUpgradeReloadListener extends SimpleJsonResourceReloadListener {

    /** 数据包目录（相对 {@code data/<namespace>/}）。 */
    public static final String DIRECTORY = "astral_dice/anvil";

    private static final Logger LOGGER = LoggerFactory.getLogger(AnvilUpgradeReloadListener.class);

    public AnvilUpgradeReloadListener() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        AnvilUpgradeData chosen = null;
        ResourceLocation chosenId = null;
        for (Map.Entry<ResourceLocation, JsonElement> entry : new TreeMap<>(files).entrySet()) {
            try {
                JsonElement element = entry.getValue();
                if (element == null || !element.isJsonObject()) {
                    throw new IllegalArgumentException("文件内容不是 JSON 对象");
                }
                AnvilUpgradeData parsed = AnvilUpgradeData.parse(element.getAsJsonObject());
                if (chosen != null) {
                    LOGGER.warn("[astral_dice] 存在多份铁砧数据，按资源 id 字典序只采用最后一份: 忽略 {} 采用 {}", chosenId, entry.getKey());
                }
                chosen = parsed;
                chosenId = entry.getKey();
            } catch (RuntimeException ex) {
                LOGGER.warn("[astral_dice] 跳过非法铁砧数据 {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        if (chosen == null) {
            AstralDiceDataStore.clearAnvil();
            LOGGER.info("[astral_dice] 未找到有效的 data/*/astral_dice/anvil/*.json，星愿铁砧回退内置兜底数值");
            return;
        }
        AstralDiceDataStore.replaceAnvil(chosen);
        LOGGER.info("[astral_dice] 铁砧数据已加载 {} -> {}", chosenId, chosen);
    }
}
