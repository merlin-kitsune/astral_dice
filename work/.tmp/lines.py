
import io
base = r"F:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice"
p = base + "/platform/client/FabricClientBridges.java"
for i, l in enumerate(open(p, encoding="utf-8", newline="").read().split("\r\n"), 1):
    if l.startswith("import net.minecraft") or l.startswith("import net.fabricmc"):
        print(i, l)
print("---- CombatPowerHud 100-120 ----")
p2 = base + "/client/CombatPowerHud.java"
for i, l in enumerate(open(p2, encoding="utf-8", newline="").read().split("\r\n"), 1):
    if 100 <= i <= 118: print(i, l)
