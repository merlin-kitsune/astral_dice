package com.merlinkitsune.astral_dice.init;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;

/**
 * 不兼容模组黑名单：**命中即拒绝启动**（在 mod 构造阶段抛 {@link ModLoadingException}
 * ⇒ 游戏停在加载错误界面并**把下面的提示原文显示出来**，不会进入主菜单）。
 *
 * <p><b>为什么是硬拒绝而不是「功能自动关闭」</b>：本模组的「星币钱包」已经把这两个模组的能力内置
 * （星币 / 星币袋的面额折算、玩家级余额账本、存入与取出、获得星币直接入钱包），而它们各自也在玩家身上
 * 维护一份货币数据 —— 同时安装会让同一枚星币被两套系统各自记账（双份余额、双份拾取判定、
 * 两套按钮叠在同一个物品栏界面上），不存在安全共存的方式。故直接拒绝，并明确告诉玩家该移除什么。
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

    /** 黑名单：{@code {modId, 玩家可读名称}}。命中任意一项即拒绝启动。 */
    private static final String[][] INCOMPATIBLE_MODS = {
            {"magic_coins", "Magic Coins"},
            {"sg_economy", "SG-Economy"},
    };

    private ModCompatibilityCheck() {
    }

    /**
     * 在 mod 构造阶段调用（**越早越好**：放在一切注册之前，避免注册做到一半才失败）。
     *
     * @throws ModLoadingException 检测到黑名单模组时抛出；异常携带的即为给玩家的提示文本
     */
    public static void verifyOrThrow() {
        List<String> found = new ArrayList<>();
        for (String[] entry : INCOMPATIBLE_MODS) {
            if (ModList.get().isLoaded(entry[0])) {
                found.add(entry[1] + "（" + entry[0] + "）");
            }
        }
        if (found.isEmpty()) {
            return;
        }
        throw new ModLoadingException(ModLoadingIssue.error(buildMessage(found)));
    }

    /** 拼装给玩家看的提示：说清「检测到什么」「为什么不能共存」「该怎么做」。 */
    private static String buildMessage(List<String> found) {
        StringBuilder sb = new StringBuilder();
        sb.append("[星之骰戏] 检测到不兼容模组：").append(String.join("、", found)).append("。\n\n")
                .append("本模组已内置「星币钱包」功能（星币与星币袋的面额折算、玩家级余额账本、")
                .append("存入 / 取出、获得星币直接入钱包），与上述模组的功能完全重叠；")
                .append("同时安装会让同一枚星币被两套系统各自记账，因此无法共同启动。\n\n")
                .append("请从 mods 目录中移除下列模组后重新启动游戏：");
        for (String name : found) {
            sb.append("\n  - ").append(name);
        }
        sb.append("\n\n（SG-Economy 是 Magic Coins 的前置，通常一并移除即可。）");
        return sb.toString();
    }
}
