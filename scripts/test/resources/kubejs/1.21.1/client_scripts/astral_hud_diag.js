// ════════════════════════════════════════════════════════════════════════════
//  astral_hud_diag.js —— 战斗数值 HUD（防御力条 / 攻击力条）客户端侧读数探针
//                      （2026-09-26 新增；本文件是**模板**，由 mt_env.ps1 的
//                       Sync-MtEnvKubejs 同步到 run/1.21.1/kubejs/client_scripts/）
//
//  【为什么必须走客户端通道】
//    `CombatPowerHud` 是纯客户端渲染层：它的行位量 `Gui.leftHeight / rightHeight`
//    与显隐门控 `options.hideGui` 都**不写任何日志**，服务端探针完全看不到。
//    截图能看「画出来了没有」，但看不出「占了几行」——而本功能的两个核心断言恰恰是行为量：
//      ① 原版护甲行被取消后，我们的防御条**恰好补位一行**（不是 0 行、也不是 2 行）；
//      ② 攻击条落在饱食度**正上方**（= 用原版 rightHeight 累加量而非屏幕常量）。
//    这两个量在 `Gui.render` 收尾后是有确定值的（每次 render 开头无条件
//    `leftHeight = rightHeight = 39;`，见 1.21.1 源 `Gui.java:257-260`），
//    故在客户端 tick 里读到的就是**上一帧的最终累计值**，可直接当断言判据。
//
//  【预期值（源码推导，非经验值）】
//    生存态、20 最大生命、无水肺、不骑乘、无吸收：
//      leftHeight  = 39 + 10(生命) + 10(本模组防御条) = **59**
//                    —— 原版护甲层被 `RenderGuiLayerEvent.Pre` 取消 ⇒ 它那句
//                       `if (getArmorValue() > 0) leftHeight += 10` 一并跳过
//                       （源 `Gui.java:1011-1013`）；本模组防御条**无条件**补上这 10。
//                       ⇒ 无论身上有没有护甲，都恒为 59；**若是 69 就说明两行都占了**（缺陷）。
//      rightHeight = 39 + 10(饱食度) + 10(本模组攻击条) = **59**
//                    —— 本模组注册在 FOOD_LEVEL 之上（源 `Gui.java:1025-1027` 先
//                       `rightHeight += 10`）⇒ 我们的 y 正好是饱食度上一行。
//      F1（`options.hideGui=true`）⇒ 整个生存层组被跳过 ⇒ **lh=39、rh=39**
//                    （`Gui.render` 的重置在守卫之外，源 `Gui.java:248` vs `257-260`）。
//
//  【只读性】
//    本探针**不写**任何游戏状态；副作用只有 `console.info` 一行（落 `logs/kubejs/client.log`）。
// ════════════════════════════════════════════════════════════════════════════

var HUD_Mc = Java.loadClass("net.minecraft.client.Minecraft");
// 仅为「类是否可见」的自检（读数走实例字段，不走静态）
var HUD_Gui = Java.loadClass("net.minecraft.client.gui.Gui");

var HUD_EVERY = 20;        // 每 20 tick（1 秒）一条读数
var HUD_BOOT_WAIT = 40;    // 进世界后等 40 tick 再打首条（避开加载期的半初始化帧）

var hudTick = 0;
var hudBoot = 0;

/** 输出出口：KubeJS 客户端脚本的 console ⇒ logs/kubejs/client.log */
function hud(s) {
    try { console.info("AP_HUD:" + s); } catch (e) { /* 忽略：不得因回报失败影响取数 */ }
}

/** gameTime（与该实现同源：levelData.getGameTime；见 astral_client_diag.js 头部约束） */
function hudGameTime(mc) {
    try { return mc.level.getLevelData().getGameTime(); } catch (e) { return -1; }
}

/**
 * 客户端自己算一遍战斗数值。
 * 与 `CombatPowerHud.refreshValues` 调的是**同一个静态方法**，故它既证明
 * 「客户端侧该类可用」，也给截图里的图标数量一个可对照的数字。
 *
 * <p>2026-09-26 补三通道原始读数（`hand` / `attr` / `defraw`）：
 * 冒烟中发现 `atk` 从空手到持铁剑**恒为 1**，而同一时刻**服务端** probe 的 `ap=6`
 * （`AP_H2_SWORD:...:ap=6:hand=minecraft:iron_sword`）⇒ 必须区分两种可能：
 *   ① 客户端**背包不同步**（本地主手还是空的）—— 那 `atk=1` 是「读到了正确的旧状态」；
 *   ② 客户端**属性不同步**（主手已是铁剑，但 `LocalPlayer` 的 `ATTACK_DAMAGE` 属性
 *      不含主手装备修饰器）—— 那 `atk=1` 是 HUD 的**真缺陷**。
 * `hand` 给 ① 的判据，`attr` 给 ② 的判据（两者同时可读即唯一确定）。
 * ⚠️ `attr` 必须走 `getAttributes().getValue(...)` 的原版 API；KubeJS 7 里
 * `Attributes.ATTACK_DAMAGE` 是 `Holder<Attribute>` 注册表项，可直接传。
 *
 * <p>2026-09-26 第二轮口径变更（用户需求「手持武器不参与攻击力条数值显示」）：
 * `atk` 改为 **HUD 实际调用的** `attackPowerDisplayOf`（显示口径，剔除主手 + 副手武器），
 * 新增 `atkraw` = `attackPowerOf`（战斗口径，含武器）作对照。
 * ⚠️ 客户端这两个值**都是 1**（`ATTACK_DAMAGE` 未开客户端同步 ⇒ 本地属性恒为基值，
 * 客户端也不本地重算装备修饰器）⇒ **本探针无法区分新旧口径**，此处的价值只是
 * 「证明 HUD 调用的那个方法在客户端可执行、且结果为期望的 1」。
 * **判据在服务端**：见 `astral_bugfix_probe.js` 的 `doHudRead`（`d = atk - atkd`）。
 */
function hudDefAtk(mc) {
    var out = "def";
    try {
        var p = mc.player;
        if (p == null) return "def=-:atk=-";
        var C = Java.loadClass("com.merlinkitsune.astral_dice.combat.DiceCombatModifiers");
        return "def=" + C.defensePowerOf(p) + ":atk=" + C.attackPowerDisplayOf(p)
            + ":atkraw=" + C.attackPowerOf(p)
            + ":" + hudRaw(mc, p);
    } catch (e) { return out + "=ERR:" + ("" + e).substring(0, 120); }
}

/** 客户端侧的原版原始读数：主手物品 / ATTACK_DAMAGE 属性值 / 护甲值。 */
function hudRaw(mc, p) {
    var hand = "ERR", attr = "ERR", defraw = "ERR";
    try {
        var st = p.getMainHandItem();
        hand = st.isEmpty() ? "empty" : ("" + st.getItem());
    } catch (eH) { hand = "ERR"; }
    try {
        var Attr = Java.loadClass("net.minecraft.world.entity.ai.attributes.Attributes");
        attr = p.getAttributeValue(Attr.ATTACK_DAMAGE);
    } catch (eA) { attr = "ERR"; }
    try { defraw = p.getArmorValue(); } catch (eD) { defraw = "ERR"; }
    return "hand=" + hand + ":attr=" + attr + ":armor=" + defraw;
}

ClientEvents.tick(event => {
    hudTick++;
    var mc = null;
    try { mc = HUD_Mc.getInstance(); } catch (e0) { return; }
    if (mc == null || mc.level == null) return;

    var t = hudGameTime(mc);

    if (hudBoot === 0) {
        hudBoot = 1;
        var guiOk = "err";
        try { guiOk = (HUD_Gui == null) ? "null" : "ok"; } catch (eG) { guiOk = "" + eG; }
        hud("evt=boot:gt=" + t + ":tick=" + hudTick + ":gui=" + guiOk);
    }

    if (hudTick < HUD_BOOT_WAIT) return;
    if ((hudTick - HUD_BOOT_WAIT) % HUD_EVERY !== 0) return;

    var lh = "ERR", rh = "ERR", hide = "ERR", scr = "ERR";
    try { lh = mc.gui.leftHeight; } catch (e1) { lh = "ERR"; }
    try { rh = mc.gui.rightHeight; } catch (e2) { rh = "ERR"; }
    try { hide = mc.options.hideGui ? 1 : 0; } catch (e3) { hide = "ERR"; }
    try { scr = (mc.screen == null) ? "-" : "open"; } catch (e4) { scr = "ERR"; }

    hud("evt=tick:gt=" + t + ":t=" + hudTick + ":lh=" + lh + ":rh=" + rh
        + ":hide=" + hide + ":scr=" + scr + ":" + hudDefAtk(mc));
});
