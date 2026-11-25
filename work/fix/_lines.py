# -*- coding: utf-8 -*-
import io, sys
files = [
 r"D:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/CombatPowerHud.java",
 r"D:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/client/gui/overlay/ForgeGui.java",
 r"D:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/client/FabricClientBridges.java",
]
for f in files:
    b = open(f, "rb").read()
    crlf = b.count(b"\r\n"); lf = b.count(b"\n"); cr = b.count(b"\r")
    print(f.split("/")[-1], "bytes=%d CRLF=%d bareLF=%d CR=%d endsCRLF=%s" % (len(b), crlf, lf-crlf, cr-crlf, b.endswith(b"\r\n")))
    t = b.decode("utf-8")
    for i, line in enumerate(t.split("\r\n"), 1):
        s = line.strip()
        if any(k in line for k in ("RESET_RIGHT", "airRowShown", "int base =", "int y = height", "rightHeight", "import net.minecraft.tags.FluidTags")):
            print("   %4d: %s" % (i, s[:150]))
