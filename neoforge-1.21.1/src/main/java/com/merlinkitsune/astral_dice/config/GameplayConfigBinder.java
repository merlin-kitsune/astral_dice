package com.merlinkitsune.astral_dice.config;

import com.merlinkitsune.starengine.component.GameplayConfigValues;
import com.merlinkitsune.starengine.component.GameplayConstants;

/**
 * 配置 → 共享常量的绑定器(1.21.1 侧)。
 *
 * <p>共享库的 {@link GameplayConstants} 不能直接读配置——1.20.1 用
 * {@code ForgeConfigSpec}、1.21.1 用 {@code ModConfigSpec},类型不通用。
 * 因此由各平台侧把本 mod 配置系统的值读出来,包成平台无关的
 * {@link GameplayConfigValues} 交给共享库。
 */
public final class GameplayConfigBinder {

    /** 在配置加载完成后(FMLCommonSetup)调用,把配置值写入共享常量。 */
    public static void refresh() {
        GameplayConstants.applyConfig(new GameplayConfigValues(
                ModCommonConfig.MAX_STARLIGHT.get(),
                ModCommonConfig.MAX_MARKER.get(),
                ModCommonConfig.EFFECT_CARD_COOLDOWN_SECONDS.get(),
                ModCommonConfig.MAX_EFFECT_STACKS.get(),
                ModCommonConfig.GIVE_GUIDE_BOOK_ON_FIRST_JOIN.get(),
                ModCommonConfig.EVENT_RANGE.get(),
                ModCommonConfig.EVENT_APPLY_MC_TEAM.get(),
                ModCommonConfig.EVENT_APPLY_FTB_TEAM.get(),
                ModCommonConfig.EVENT_APPLY_OPAC.get(),
                ModCommonConfig.EVENT_APPLY_MAID.get(),
                ModCommonConfig.HAND_FAN_BIG_RANGE.get(),
                ModCommonConfig.ACTIONBAR_DURATION_TICKS.get(),
                ModCommonConfig.ACTIONBAR_FADE_TICKS.get()));
    }

    private GameplayConfigBinder() {
    }
}
