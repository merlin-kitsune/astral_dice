# -*- coding: utf-8 -*-
import io, sys

ROOT = r"D:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice"

def load(p):
    with io.open(p, "r", encoding="utf-8", newline="") as f:
        return f.read()

def save(p, text):
    with io.open(p, "w", encoding="utf-8", newline="") as f:
        f.write(text.replace("\n", "\r\n"))

def patch(p, pairs):
    raw = load(p)
    had_crlf = "\r\n" in raw
    t = raw.replace("\r\n", "\n")
    for i, (old, new) in enumerate(pairs):
        n = t.count(old)
        if n != 1:
            print("!! ANCHOR %d count=%d in %s" % (i, n, p))
            sys.exit(2)
        t = t.replace(old, new)
    save(p, t)
    print("PATCHED %s (was_crlf=%s, anchors=%d)" % (p, had_crlf, len(pairs)))

HUD = ROOT + r"/client/CombatPowerHud.java"
FG  = ROOT + r"/platform/client/gui/overlay/ForgeGui.java"
FCB = ROOT + r"/platform/client/FabricClientBridges.java"

# ---------------- CombatPowerHud.java ----------------
hud_pairs = [
# 1) import
("import net.minecraft.resources.ResourceLocation;\nimport net.minecraft.world.entity.Entity;",
 "import net.minecraft.resources.ResourceLocation;\nimport net.minecraft.tags.FluidTags;\nimport net.minecraft.world.entity.Entity;"),

# 2) header bullet 2 tail + bullet 3
(""" *       （见其类头：每帧复位为 49 / 59，本类的 overlay **先读后增**）。</li>
 *   <li><b>攻击条比 Forge 高一行</b>：本线的原版空气条固定画在 {@code height-49} 且不参与
 *       累加，攻击条取 {@code height-59} 以避开它（Forge 侧则是把空气条一并推走）。</li>""",
 """ *       （见其类头：左列每帧复位为 49；右列复位为 59 这个**上限基准**，本类的 overlay 再按
 *       本帧原版实况取值并写回累加量）。</li>
 *   <li><b>攻击条的基准按「原版此刻是否占气泡行」动态取</b>：本线的原版气泡行固定画在
 *       {@code height-49} 且不参与本模组的累加 ⇒ 原版画气泡时攻击条让到 {@code height-59}
 *       （在气泡之上，不盖住气泡）；原版不画气泡时那一行是空的，攻击条贴回 {@code height-49}
 *       （紧贴饱食度上方一行，与 forge / neoforge 三线一致）。判定与原版
 *       {@code Gui#renderPlayerHealth} 的「是否画气泡行」条件逐字等价，见 {@link #airRowShown}。</li>"""),

# 3) header 坐标来源 attack bullet
(""" *   <li><b>攻击条</b>：{@code y = height - gui.rightHeight}（起点 59 = 饱食度与空气行之上的那一行），
 *       画完 {@code rightHeight += 10}。用累加量而非屏幕常量 ⇒ 后续新增的 overlay 会被排到本条**上面**。</li>""",
 """ *   <li><b>攻击条</b>：基准 {@code B} 由「原版这一帧是否占气泡行」决定 —— 占
 *       （{@code height-49} 是气泡行）⇒ {@code B = 59}；不占 ⇒ {@code B = 49}（紧贴饱食度上方一行）。
 *       {@code y = height - B}，画完把 {@code B + 10} 写回 {@code gui.rightHeight}（保持「已占高度」
 *       累加量语义）⇒ 后续新增的 overlay 会被排到本条**上面**。</li>"""),

# 4) ATTACK_OVERLAY body
("""        refreshValues(player);
        int y = height - gui.rightHeight;
        drawRow(guiGraphics, ATTACK_FULL, ATTACK_HALF, width / 2 + 91, y, attack, true);
        gui.rightHeight += ROW_HEIGHT;""",
 """        refreshValues(player);
        // 基准 = 原版这一帧右列**已经占掉**的高度(从屏幕底边算)。原版右列只可能占两处:
        //   饱食度行恒在 height-39;气泡行(仅当原版决定画时)在 height-49。
        // ⇒ 画气泡 ⇒ 基准 59(攻击条落在气泡行之上,不遮气泡);
        //    不画气泡 ⇒ 基准 49(紧贴饱食度上方一行,与 forge / neoforge 三线一致)。
        int base = airRowShown(player) ? ForgeGui.RESET_RIGHT : ForgeGui.RESET_RIGHT_NO_AIR;
        int y = height - base;
        drawRow(guiGraphics, ATTACK_FULL, ATTACK_HALF, width / 2 + 91, y, attack, true);
        // 本行占掉一行 ⇒ 把本帧真实基准 + 行高写回累加量(后续 overlay 从这里继续往上排)。
        gui.rightHeight = base + ROW_HEIGHT;"""),

# 5) helper method before drawRow javadoc
("""    /**
     * 画一行战斗数值图标。""",
 """    /**
     * 原版这一帧是否会画**气泡行**（{@code Gui#renderPlayerHealth} 的 {@code "air"} 段）。
     *
     * <p>条件与 1.20.1 原版**逐字等价**（不是「有氧气值」之类的近似条件）：
     * <pre>
     *   int l5 = player.getMaxAirSupply();
     *   int i6 = Math.min(player.getAirSupply(), l5);
     *   if (player.isEyeInFluid(FluidTags.WATER) || i6 &lt; l5) { ... }
     * </pre>
     *
     * <p>取证（Mojang 映射，非记忆）：
     * <ul>
     *   <li>反编译源码 {@code forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar}
     *       → {@code net/minecraft/client/gui/Gui.java:933-936}（原文见上）；</li>
     *   <li>字节码（本线实际运行的那份映射）
     *       {@code .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*...jar}
     *       → {@code net.minecraft.client.gui.Gui#renderPlayerHealth}：offset 770-782
     *       {@code getMaxAirSupply} / {@code Math.min(getAirSupply(), l5)}、offset 787-801
     *       {@code isEyeInFluid(FluidTags.WATER)} + {@code if_icmpge}。</li>
     * </ul>
     *
     * <p>气泡行的 y 也一并核过：{@code l2 = (height - 39) - 10}（offset 313-317），饱食度行被画时
     * {@code l2 -= 10}（offset 752 {@code iinc 18, -10}），随后
     * {@code l2 -= (getVisibleVehicleHeartRows(k5) - 1) * 10}（offset 804-822）。无载具血量时
     * {@code k5 = 0} ⇒ 该差值抵消为一行 ⇒ 净结果仍是 {@code height-49}，与「攻击条让开
     * {@code height-49}」的前提一致。
     */
    private static boolean airRowShown(LocalPlayer player) {
        int maxAir = player.getMaxAirSupply();
        int air = Math.min(player.getAirSupply(), maxAir);
        return player.isEyeInFluid(FluidTags.WATER) || air < maxAir;
    }

    /**
     * 画一行战斗数值图标。"""),
]
patch(HUD, hud_pairs)
