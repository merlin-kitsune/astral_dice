package com.merlinkitsune.astral_dice.init;

import java.util.ArrayList;
import java.util.List;

import com.merlinkitsune.astral_dice.economy.StarCoinCurrency;

import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;

/**
 * 不兼容模组黑名单：命中即拒绝启动（抛 {@link ModLoadingException}
 * ⇒ 游戏停在加载错误界面并**把下面的提示原文显示出来**，不会进入主菜单）。
 *
 * <p><b>为什么是硬拒绝而不是「功能自动关闭」</b>：本模组的「星币钱包」已经把这两个模组的能力内置
 * （星币 / 星币袋的面额折算、玩家级余额账本、存入与取出、获得星币直接入钱包），而它们各自也在玩家身上
 * 维护一份货币数据 —— 同时安装会让同一枚星币被两套系统各自记账（双份余额、双份拾取判定、
 * 两套按钮叠在同一个物品栏界面上），不存在安全共存的方式。
 *
 * <p><b>⚠️ 前提条件：仅当「星币钱包」处于启用状态时才拒绝</b>（2026-09-22 用户裁决）：
 * {@code config/astral_dice-common.toml} 的 {@code [star_coin_wallet] enable_star_coin_wallet}
 * 为 {@code true}（默认值）⇒ 拒绝启动，并提示两条出路（关掉钱包，或移除上述模组）；
 * 为 {@code false} ⇒ **完全放行**，不报任何错。
 * 理由：钱包一关，本模组就不再维护玩家级货币账本，「同一枚星币被两套系统记账」的前提随之消失。
 *
 * <p><b>⚠️ 必须在 common setup 调用，不能在 mod 构造阶段调用</b>：配置是在**构造阶段之后**才加载的 ——
 * {@code CommonModLoader.begin} 的顺序为「构造 mod → 注册表初始化 → Config loading」，
 * 故构造阶段执行 {@code ConfigValue#get()} 会抛「配置尚未加载」
 * （{@code ModCommonConfig#snapshot()} 的 javadoc 亦据此要求调用者先完成注册）。
 * 改挂在 {@code FMLCommonSetupEvent}（位于 Config loading 之后）后，**提示上屏的链路完全不变**：
 * {@code ModLoader.addLoadingIssuesFromException} 对 {@link ModLoadingException}
 * **原样采用其 issues**（{@code modLoadingException.getIssues()}），因此玩家看到的仍是本文这段文案。
 * ⚠️ 同理**不要**改挂 {@code ModConfigEvent} —— 该事件在 CONFIG_LOAD 阶段触发，而 Forge 侧该阶段走
 * {@code handleInlineTransition}（**没有 try/catch**），异常会退化成上游的「There was a crash during startup」，
 * 本模组的提示就消失了。
 *
 * <p><b>提示为什么能上屏</b>：{@link ModLoadingIssue#error(String, Object...)} 的第一个参数是
 * **翻译 key**；错误界面用 {@code Component.translatable(key, args)} 渲染，而该 key 在语言文件里
 * **不存在** ⇒ 按原版行为**原样显示 key 本身**，于是我们传什么文案就显示什么文案
 * （也因此**不要**把这几个 key 真的写进 lang，否则会被翻译覆盖）。
 * ⚠️ 文案里**不要出现 `%` 与 `{}`**（避免被当格式化占位符）。
 *
 * <p><b>为什么两版都走 Java 检查、而不是写进 mods.toml</b>：NeoForge 的 {@code [[dependencies]]}
 * 支持 {@code type="incompatible"}，但 <b>Forge 1.20.1 不支持</b>（其依赖解析只认 {@code mandatory}
 * 布尔字段，见 {@code ModInfo$ModVersion} 的 {@code Missing required field mandatory in dependency}），
 * 且声明式命中的提示文案由 FML 生成、不受本模组控制 ⇒ 两版统一在本类检查，行为与提示逐字一致。
 * 平台差异只有抛出的异常形态（本版 {@link ModLoadingIssue}；Forge 侧为
 * {@code ModLoadingException(IModInfo, ModLoadingStage, String, Throwable)}）。
 */
public final class ModCompatibilityCheck {

    /**
     * 黑名单单项。
     *
     * @param modId                   注册 modId
     * @param displayName             玩家可读名称（进提示文案）
     * @param onlyWhenWalletEnabled   是否**仅在「星币钱包」启用时**才算不兼容；{@code false} 表示无条件拒绝
     */
    private record Entry(String modId, String displayName, boolean onlyWhenWalletEnabled) {
    }

    /**
     * 黑名单：命中任意一项、且该项的前提条件成立，即拒绝启动。
     *
     * <p>两项都挂「钱包启用」这个前提：SG-Economy 是 Magic Coins 的前置（二者总是一起安装），
     * 只放行 Magic Coins 而硬拒 SG-Economy 等于两条路都走不通，故二者同条件。
     */
    private static final List<Entry> BLACKLIST = List.of(
            new Entry("magic_coins", "Magic Coins", true),
            new Entry("sg_economy", "SG-Economy", true));

    private ModCompatibilityCheck() {
    }

    /**
     * 在 **common setup**（{@code FMLCommonSetupEvent} 处理器内）调用。
     *
     * @throws ModLoadingException 检测到不兼容组合（黑名单模组存在 **且**「星币钱包」启用）时抛出；
     *                             异常携带的即为给玩家的提示文本
     */
    public static void verifyOrThrow() {
        // 配置在此阶段已加载（见类头说明）；读取失败时 StarCoinCurrency 退回默认值 true = 启用，
        // 与「改这一版之前的行为」一致（宁可拒绝，也不放行一个可能真在双记账的组合）。
        final boolean walletEnabled = StarCoinCurrency.isWalletEnabled();

        List<String> found = new ArrayList<>();
        for (Entry entry : BLACKLIST) {
            if (entry.onlyWhenWalletEnabled() && !walletEnabled) {
                continue; // 「星币钱包」已关闭 ⇒ 本项放行
            }
            if (ModList.get().isLoaded(entry.modId())) {
                found.add(entry.displayName() + "（" + entry.modId() + "）");
            }
        }
        if (found.isEmpty()) {
            return;
        }
        throw new ModLoadingException(ModLoadingIssue.error(buildMessage(found)));
    }

    /** 拼装给玩家看的提示：说清「检测到什么」「为什么不能共存」「两条出路分别怎么做」。 */
    private static String buildMessage(List<String> found) {
        StringBuilder sb = new StringBuilder();
        sb.append("[星之骰戏] 检测到不兼容模组：").append(String.join("、", found)).append("。\n\n")
                .append("本模组的「星币钱包」功能（星币与星币袋的面额折算、玩家级余额账本、")
                .append("存入 / 取出、获得星币直接入钱包）与上述模组的功能完全重叠，")
                .append("同时安装会让同一枚星币被两套系统各自记账；")
                .append("而「星币钱包」当前处于启用状态，因此无法共同启动。\n\n")
                .append("两条出路，任选其一：\n\n")
                .append("【一】不使用「星币钱包」⇒ 可以共存：\n")
                .append("  打开配置文件 config/astral_dice-common.toml，在 [star_coin_wallet] 段内把\n")
                .append("  enable_star_coin_wallet 改为 false，保存后重新启动游戏即可。\n")
                .append("  届时本模组不再维护玩家级货币账本，与上述模组不再冲突；\n")
                .append("  星币与星币袋维持普通物品行为（与内置钱包之前一致）。\n\n")
                .append("【二】继续使用「星币钱包」⇒ 请从 mods 目录中移除下列模组后重新启动游戏：");
        for (String name : found) {
            sb.append("\n  - ").append(name);
        }
        sb.append("\n\n（SG-Economy 是 Magic Coins 的前置，通常一并移除即可。）");
        return sb.toString();
    }
}
