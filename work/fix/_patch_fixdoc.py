# -*- coding: utf-8 -*-
import io, sys
P = r"F:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/CombatPowerHud.java"
old = """     * <p>气泡行的 y 也一并核过：{@code l2 = (height - 39) - 10}（offset 313-317），饱食度行被画时
     * {@code l2 -= 10}（offset 752 {@code iinc 18, -10}），随后
     * {@code l2 -= (getVisibleVehicleHeartRows(k5) - 1) * 10}（offset 804-822）。无载具血量时
     * {@code k5 = 0} ⇒ 该差值抵消为一行 ⇒ 净结果仍是 {@code height-49}，与「攻击条让开
     * {@code height-49}」的前提一致。"""
new = """     * <p>气泡行的 y 也一并核过：{@code l2 = (height - 39) - 10}（offset 313-317）；饱食度行被画时
     * {@code l2 -= 10}（offset 752 {@code iinc 18, -10}）；随后
     * {@code l2 -= (getVisibleVehicleHeartRows(k5) - 1) * 10}（offset 804-822，其中
     * {@code getVisibleVehicleHeartRows(x) = ceil(x / 10.0)}）。非骑乘时 {@code k5 = 0} ⇒
     * {@code rows - 1 = -1} ⇒ 又把上面那 10 加回来，净结果仍是 {@code height-49} —— 与
     * 「气泡行占 height-49、攻击条必须让开该行」的前提一致。"""
raw = io.open(P, "r", encoding="utf-8", newline="").read()
t = raw.replace("\r\n", "\n")
assert t.count(old) == 1, t.count(old)
t = t.replace(old, new)
io.open(P, "w", encoding="utf-8", newline="").write(t.replace("\n", "\r\n"))
print("ok")
