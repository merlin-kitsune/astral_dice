package com.merlinkitsune.astral_dice.config;

import com.merlinkitsune.starenginelib.component.GameplayConfigValues;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 公共配置:仅保留少量仍允许玩家调整的选项。
 * 其余玩法数值已固定为库 {@code com.merlinkitsune.starenginelib.component.GameplayConstants}
 * 的常量,不再写入配置文件。
 *
 * <p>配置文件的读写与配置 GUI 全部留在本模组:NeoForge 的 {@link ModConfigSpec} 与 Forge 的
 * {@code ForgeConfigSpec} 不通用,无法下沉到共享库 starengine_lib;库只提供平台无关的值快照
 * seam —— 本类把当前值打成 {@link GameplayConfigValues}(见 {@link #snapshot()}),
 * 由 {@code AstralDiceMod} 在配置加载完成后推给
 * {@code GameplayConstants.applyConfig(GameplayConfigValues)}。
 *
 * <b>配置版本号规则:只有修改了配置项(增/删/改)时才 +1</b>,`CONFIG_VERSION` 是旧配置自动备份的判据。
 * v2 已用于「移除事件范围与女仆开关」;本次「移除星光上限/标记上限/效果牌公共冷却/功能效果牌叠层上限/手持风扇-大范围
 * (全部回归 GameplayConstants 常量)」**按用户裁决不递增版本号**(当前版本不修改)。
 */
public final class ModCommonConfig {
    public static final int CONFIG_VERSION = 2;

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue GIVE_GUIDE_BOOK_ON_FIRST_JOIN;
    public static final ModConfigSpec.BooleanValue EVENT_APPLY_MC_TEAM;
    public static final ModConfigSpec.BooleanValue EVENT_APPLY_FTB_TEAM;
    public static final ModConfigSpec.BooleanValue EVENT_APPLY_OPAC;
    public static final ModConfigSpec.IntValue ACTIONBAR_DURATION_TICKS;
    public static final ModConfigSpec.IntValue ACTIONBAR_FADE_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("配置文件版本号:用于旧配置自动备份(CONFIG_VERSION),请勿手改")
                .define("config_version", CONFIG_VERSION);

        GIVE_GUIDE_BOOK_ON_FIRST_JOIN = builder.comment("是否在玩家第一次加入世界时给予《恋的规则书》(默认：true; 每个玩家每个世界只发放一次)")
                .define("give_guide_book_on_first_join", true);

        builder.push("event_system").comment("=== 事件系统 ===");
        EVENT_APPLY_MC_TEAM = builder.comment("事件是否作用于 Minecraft 同队玩家")
                .define("event_apply_mc_team", true);
        EVENT_APPLY_FTB_TEAM = builder.comment("事件是否作用于 FTB Teams 队友(需安装 FTB Teams,API 不符时自动跳过)")
                .define("event_apply_ftb_team", true);
        EVENT_APPLY_OPAC = builder.comment("事件是否作用于 OPAC 队伍(需安装 Open Parties and Claims,API 不符时自动跳过)")
                .define("event_apply_opac", true);
        builder.pop();


        builder.push("actionbar").comment("=== actionbar ===");
        ACTIONBAR_DURATION_TICKS = builder.comment("actionbar 消息显示总时长上限(单位: tick,默认：3 秒; 任何消息最多显示该时长)")
                .defineInRange("actionbar_duration_ticks", 60, 20, 200);
        ACTIONBAR_FADE_TICKS = builder.comment("actionbar 消息最后淡出时长(单位: tick,默认：1 秒)")
                .defineInRange("actionbar_fade_ticks", 20, 1, 60);
        builder.pop();

        SPEC = builder.build();
    }

    private ModCommonConfig() {
    }

    /**
     * 把当前配置值打成平台无关快照,供库的
     * {@code GameplayConstants.applyConfig(GameplayConfigValues)} 使用。
     *
     * <p>调用前必须已完成配置注册({@code modContainer.registerConfig(...)}),
     * 否则各 {@code ConfigValue#get()} 会因配置尚未加载而抛异常。
     */
    public static GameplayConfigValues snapshot() {
        return new GameplayConfigValues(
                GIVE_GUIDE_BOOK_ON_FIRST_JOIN.get(),
                EVENT_APPLY_MC_TEAM.get(),
                EVENT_APPLY_FTB_TEAM.get(),
                EVENT_APPLY_OPAC.get(),
                ACTIONBAR_DURATION_TICKS.get(),
                ACTIONBAR_FADE_TICKS.get());
    }
}
