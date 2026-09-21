package com.merlinkitsune.astral_dice.config;

import com.merlinkitsune.starenginelib.component.GameplayConfigValues;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 公共配置:仅保留少量仍允许玩家调整的选项。
 * 其余玩法数值已固定为库 {@code com.merlinkitsune.starenginelib.component.GameplayConstants}
 * 的常量,不再写入配置文件。
 *
 * <p>配置文件的读写与配置 GUI 全部留在本模组:Forge 的 {@link ForgeConfigSpec} 与 NeoForge 的
 * {@code ModConfigSpec} 不通用,无法下沉到共享库 starengine_lib;库只提供平台无关的值快照
 * seam —— 本类把当前值打成 {@link GameplayConfigValues}(见 {@link #snapshot()}),
 * 由 {@code AstralDiceMod} 在配置加载完成后推给
 * {@code GameplayConstants.applyConfig(GameplayConfigValues)}。
 *
 * <b>配置版本号规则:只有修改了配置项(增/删/改)时才 +1</b>,`CONFIG_VERSION` 是旧配置自动备份的判据。
 * v2 已用于「移除事件范围与女仆开关」;本次「移除星光上限/标记上限/效果牌公共冷却/功能效果牌叠层上限/手持风扇-大范围
 * (全部回归 GameplayConstants 常量)」**按用户裁决不递增版本号**(当前版本不修改)。
 * v3 用于「新增 `allow_firearm_damage`(枪弹/炮弹类伤害是否计入法伤)」。
 * v4 用于「新增星币钱包 7 项配置(`enable_star_coin_wallet` / `deposit_star_coin_on_obtain` +
 * 钱包与两个兑换按钮的 x/y 偏移)」。
 */
public final class ModCommonConfig {
    public static final int CONFIG_VERSION = 4;

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue GIVE_GUIDE_BOOK_ON_FIRST_JOIN;
    public static final ForgeConfigSpec.BooleanValue ALLOW_FIREARM_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue EVENT_APPLY_MC_TEAM;
    public static final ForgeConfigSpec.BooleanValue EVENT_APPLY_FTB_TEAM;
    public static final ForgeConfigSpec.BooleanValue EVENT_APPLY_OPAC;
    public static final ForgeConfigSpec.IntValue ACTIONBAR_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue ACTIONBAR_FADE_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_STAR_COIN_WALLET;
    public static final ForgeConfigSpec.BooleanValue DEPOSIT_STAR_COIN_ON_OBTAIN;
    public static final ForgeConfigSpec.IntValue STAR_COIN_WALLET_OFFSET_X;
    public static final ForgeConfigSpec.IntValue STAR_COIN_WALLET_OFFSET_Y;
    public static final ForgeConfigSpec.IntValue STAR_COIN_CONVERT_OFFSET_X;
    public static final ForgeConfigSpec.IntValue STAR_COIN_CONVERT_OFFSET_Y;
    public static final ForgeConfigSpec.IntValue STAR_COIN_BAG_CONVERT_OFFSET_X;
    public static final ForgeConfigSpec.IntValue STAR_COIN_BAG_CONVERT_OFFSET_Y;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("配置文件版本号:用于旧配置自动备份(CONFIG_VERSION),请勿手改")
                .define("config_version", CONFIG_VERSION);

        GIVE_GUIDE_BOOK_ON_FIRST_JOIN = builder.comment("是否在玩家第一次加入世界时给予《恋的规则书》(默认：true; 每个玩家每个世界只发放一次)")
                .define("give_guide_book_on_first_join", true);

        // 枪弹/炮弹类伤害是否计入法伤(默认 false 即维持既有行为:军火类伤害一律不计入法伤)。
        ALLOW_FIREARM_DAMAGE = builder.comment("是否允许枪弹/炮弹类伤害计入法伤(默认：false)",
                        "false = 屏蔽枪弹/炮弹等军火类伤害,不计入法伤(与既有行为一致);",
                        "true = 允许伤害类型或弹丸类名关键词命中的军火类伤害进入法伤白名单判定")
                .define("allow_firearm_damage", false);

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

        // === 星币钱包 ===
        // 面额口径固定为「1 星币 = 1、1 星币袋 = 9」(常量在 item/StarCoinCurrency,不开放配置)。
        // 余额本体由前置库 starengine_lib 的 economy 账本承载(玩家持久化数据,不受死亡掉落影响)。
        builder.push("star_coin_wallet").comment("=== 星币钱包 ===");
        ENABLE_STAR_COIN_WALLET = builder.comment("是否启用星币钱包(默认：true)",
                        "false = 物品栏不出现钱包按钮,星币/星币袋维持普通物品行为(与改动前一致);",
                        "true = 物品栏(生存/创造)左上角出现星币钱包按钮,点击可把物品栏与副手持有的",
                        "       星币/星币袋全部折算存入钱包")
                .define("enable_star_coin_wallet", true);
        DEPOSIT_STAR_COIN_ON_OBTAIN = builder.comment("获得星币时是否直接存入钱包(默认：true)",
                        "**仅在 enable_star_coin_wallet = true 时生效**(钱包关闭时该选项无意义);",
                        "true = 新获得的星币/星币袋(发放、拾取)直接折算入钱包,不进物品栏;",
                        "false = 新获得的星币/星币袋照常进物品栏")
                .define("deposit_star_coin_on_obtain", true);
        STAR_COIN_WALLET_OFFSET_X = builder.comment("星币钱包按钮位置偏移 X(像素,默认：0; 基座坐标由客户端按生存/创造分别取,见 StarCoinWalletButtons)")
                .defineInRange("star_coin_wallet_offset_x", 0, -2000, 2000);
        STAR_COIN_WALLET_OFFSET_Y = builder.comment("星币钱包按钮位置偏移 Y(像素,默认：0)")
                .defineInRange("star_coin_wallet_offset_y", 0, -2000, 2000);
        STAR_COIN_CONVERT_OFFSET_X = builder.comment("星币兑换(取出)按钮位置偏移 X(像素,默认：0)")
                .defineInRange("star_coin_convert_offset_x", 0, -2000, 2000);
        STAR_COIN_CONVERT_OFFSET_Y = builder.comment("星币兑换(取出)按钮位置偏移 Y(像素,默认：0)")
                .defineInRange("star_coin_convert_offset_y", 0, -2000, 2000);
        STAR_COIN_BAG_CONVERT_OFFSET_X = builder.comment("星币袋兑换(取出)按钮位置偏移 X(像素,默认：0)")
                .defineInRange("star_coin_bag_convert_offset_x", 0, -2000, 2000);
        STAR_COIN_BAG_CONVERT_OFFSET_Y = builder.comment("星币袋兑换(取出)按钮位置偏移 Y(像素,默认：0)")
                .defineInRange("star_coin_bag_convert_offset_y", 0, -2000, 2000);
        builder.pop();

        SPEC = builder.build();
    }

    private ModCommonConfig() {
    }

    /**
     * 把当前配置值打成平台无关快照,供库的
     * {@code GameplayConstants.applyConfig(GameplayConfigValues)} 使用。
     *
     * <p>调用前必须已完成配置注册(注册后 Forge 会在配置加载阶段填入真实值),
     * 否则各 {@code ConfigValue#get()} 会因配置尚未加载而抛异常。
     */
    public static GameplayConfigValues snapshot() {
        return new GameplayConfigValues(
                GIVE_GUIDE_BOOK_ON_FIRST_JOIN.get(),
                EVENT_APPLY_MC_TEAM.get(),
                EVENT_APPLY_FTB_TEAM.get(),
                EVENT_APPLY_OPAC.get(),
                ACTIONBAR_DURATION_TICKS.get(),
                ACTIONBAR_FADE_TICKS.get(),
                ALLOW_FIREARM_DAMAGE.get());
    }
}
