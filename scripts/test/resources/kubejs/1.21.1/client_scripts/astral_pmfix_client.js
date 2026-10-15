// ════════════════════════════════════════════════════════════════════════════
//  astral_pmfix_client.js —— .123 批客户端取证（1.21.1 / 1.20.1 同构两份）
//    · 三张伤害效果牌 tooltip 精简：对怪激光 / 对怪板砖 / 轨道炮
//    · 试剂-深渊 tooltip 去括注
//    · 手册条目文案（lang 键）：battle_system_changes.name / .1 + abyss_reagent_chip.1
//
//  【为什么必须在客户端】tooltip 文本 = lang 解析 + ModTooltipHandler(ItemTooltipEvent) 组装，
//  只有客户端语言环境（options.txt 的 lang:zh_cn）能给出玩家实际看到的那几行。
//  【为什么走 Screen#getTooltipFromItem】它就是原版 UI 自己组装物品提示框的入口：
//    内部 = `stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, 正确的 TooltipFlag)`
//    （1.20.1 为 `getTooltipLines(mc.player, flag)`）—— 因此**不需要**在 JS 里自己构造 TooltipFlag
//    （实测：KubeJS 的类包装下 `TooltipFlag$Default.NORMAL` 取不到，会静默拿到 undefined）。
//    签名取源（实物）：1.21.1 `Screen.java` 源码 `public static List<Component> getTooltipFromItem(Minecraft, ItemStack)`；
//    1.20.1 Forge 补丁后的 `Screen.class` 常量池同一描述符
//    `(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;`。
//  【为什么能拿到模组追加的行】取源（实物 jar 字节扫描）：
//    · 1.21.1：`net/minecraft/world/item/ItemStack.class` 常量池含 `onItemTooltip` /
//      `ItemTooltipEvent`（NeoForge 补丁在 getTooltipLines 里派发）；
//    · 1.20.1：`ItemStack.class` 常量池含 `ForgeEventFactory.onItemTooltip`，描述符
//      `(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Ljava/util/List;
//      Lnet/minecraft/world/item/TooltipFlag;)Lnet/minecraftforge/event/entity/player/ItemTooltipEvent;`
//      ⇒ 两条线的 getTooltipLines 都会派发 ItemTooltipEvent，本脚本据此取到模组追加的行。
//  【只读】只构造 ItemStack 调 getTooltipLines / Component.translatable，不改任何游戏状态。
//  【节拍】每 40 tick（≈2s）重播一次全部读数（用例断言窗口自本用例起算，读数必须落在窗口内）。
// ════════════════════════════════════════════════════════════════════════════

function pmfLog(s) { try { console.info("AP_PMFIX|" + s); } catch (e) { /* 忽略：回报失败不得影响诊断 */ } }

var PMF_LOAD_ERR = [];
function pmfTryLoad(name) {
    try { return Java.loadClass(name); } catch (e) { PMF_LOAD_ERR.push(name + " -> " + e); return null; }
}
var PMF_MC = pmfTryLoad("net.minecraft.client.Minecraft");
var PMF_BIR = pmfTryLoad("net.minecraft.core.registries.BuiltInRegistries");
var PMF_IS = pmfTryLoad("net.minecraft.world.item.ItemStack");
var PMF_COMPONENT = pmfTryLoad("net.minecraft.network.chat.Component");
var PMF_RL = pmfTryLoad("net.minecraft.resources.ResourceLocation");
var PMF_ITEM = pmfTryLoad("net.minecraft.world.item.Item");
var PMF_TTCTX = pmfTryLoad("net.minecraft.world.item.Item$TooltipContext");
var PMF_SCREEN = pmfTryLoad("net.minecraft.client.gui.screens.Screen");

/** 被测物品（三条 tooltip + 一枚筹码） */
var PMF_ITEMS = [
    { k: "laser",   id: "astral_dice:effect_card_monster_laser" },
    { k: "brick",   id: "astral_dice:effect_card_monster_brick" },
    { k: "orbital", id: "astral_dice:effect_card_orbital_strike" },
    { k: "abyss",   id: "astral_dice:abyss_reagent_chip" }
];

/** 手册条目文案（lang 键 ⇒ 玩家在书页上看到的文本；手册条目 json 引用的就是这些键） */
var PMF_LANGS = [
    "astral_dice.guide.entry.battle_system_changes.name",
    "astral_dice.guide.entry.battle_system_changes.1",
    "astral_dice.guide.entry.abyss_reagent_chip.1"
];

/**
 * 读数行的**行尾终止符**：`|END`。
 *
 * <p>为什么需要它：断言引擎是在**整个用例窗口文本**上做正则匹配（不是逐行），`.NET` 的 `$` 因此等于
 * 「窗口末尾」而不是「该行末尾」—— 首跑实测：带 `$` 的文案断言全部读空。故文案断言一律改为
 * 「`|正文字|END`」的全串锚定（下一字符必须是 `|`）⇒ 旧文案（更长）不会误命中，也不依赖 `$`。
 */
var PMF_EOL = "|END";
function pmfFlat(s) {
    var out = "";
    for (var i = 0; i < s.length; i++) {
        var c = s.charAt(i);
        if (c === "\n") { out += "~n~"; continue; }
        if (c === "\r") continue;
        out += c;
    }
    return out;
}

/** 去掉 §x 染色码后的纯文本（行尾 `$` 断言用；染色码不参与文案比对） */
function pmfStrip(s) {
    var out = "";
    for (var i = 0; i < s.length; i++) {
        var c = s.charAt(i);
        if (c === "\u00a7") { i++; continue; }
        if (c === "\n") { out += "~n~"; continue; }
        if (c === "\r") continue;
        out += c;
    }
    return out;
}

function pmfItemStack(id) {
    if (PMF_BIR === null || PMF_IS === null || PMF_RL === null) return null;
    var item = null;
    try { item = PMF_BIR.ITEM.get(PMF_RL.parse(id)); }
    catch (e0) { try { item = PMF_BIR.ITEM.get(new PMF_RL(id)); } catch (e1) { return null; } }
    if (item == null) return null;
    return new PMF_IS(item);
}

/**
 * 取 tooltip 行：首选原版 UI 自己的入口 `Screen#getTooltipFromItem(Minecraft, ItemStack)`
 * （内部自取 TooltipContext / TooltipFlag / 客户端玩家，并**必然**派发 ItemTooltipEvent）。
 * 兜底：手写 getTooltipLines 两条签名（1.21.1 三参 / 1.20.1 两参），复用上面的 flag——取不到就不走。
 */
function pmfTooltipLines(stack, mc) {
    var tried = [];
    if (PMF_SCREEN !== null) {
        try { return { lines: PMF_SCREEN.getTooltipFromItem(mc, stack), path: "screen" }; }
        catch (e0) { tried.push("screen:" + e0); }
    } else {
        tried.push("screen:no_class");
    }
    return { lines: null, path: "none", err: tried.join(" | ") };
}

function pmfDump(mc) {
    pmfLog("dump|items=" + PMF_ITEMS.length + "|loaderr=" + PMF_LOAD_ERR.length);
    for (var i = 0; i < PMF_ITEMS.length; i++) {
        var it = PMF_ITEMS[i];
        var stack = pmfItemStack(it.id);
        if (stack == null) { pmfLog("tip|" + it.k + "|ERR:no_stack:" + it.id); continue; }
        var r = pmfTooltipLines(stack, mc);
        if (r.lines == null) { pmfLog("tip|" + it.k + "|id=" + it.id + "|ERR:lines:" + r.err); continue; }
        var n = r.lines.size();
        pmfLog("tip|" + it.k + "|id=" + it.id + "|n=" + n + "|path=" + r.path);
        for (var j = 0; j < n; j++) {
            var raw = "" + r.lines.get(j).getString();
            pmfLog("tip|" + it.k + "|i=" + j + "|strip=" + pmfStrip(raw) + "|END");
        }
    }
    if (PMF_COMPONENT !== null) {
        for (var m = 0; m < PMF_LANGS.length; m++) {
            var key = PMF_LANGS[m], txt = "?";
            try { txt = "" + PMF_COMPONENT.translatable(key).getString(); }
            catch (e2) { txt = "ERR:" + e2; }
            pmfLog("lang|" + key + "|strip=" + pmfStrip(txt) + "|END");
        }
    }
}

var pmfTick = 0;
ClientEvents.tick(event => {
    if (PMF_MC === null) return;
    var mc = null;
    try { mc = PMF_MC.getInstance(); } catch (e0) { return; }
    if (mc === null) return;
    var ok = false;
    try { ok = (mc.player !== null) && (mc.level !== null); } catch (e1) { ok = false; }
    if (!ok) return;
    pmfTick++;
    if (pmfTick % 40 !== 0) return;
    try { pmfDump(mc); } catch (e2) { pmfLog("evt=ERR:" + e2); }
});
