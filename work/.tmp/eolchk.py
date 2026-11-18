
import os
base = r"F:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice"
files = [
 "client/CombatPowerHud.java",
 "platform/client/FabricClientBridges.java",
 "platform/client/gui/overlay/ForgeGui.java",
]
for f in files:
    p = os.path.join(base, f)
    raw = open(p, encoding="utf-8", newline="").read()
    crlf = raw.count("\r\n")
    rest = raw.replace("\r\n", "")
    print(f, "bytes", len(raw.encode("utf-8")), "lines", raw.count("\r\n")+ (0 if raw.endswith("\r\n") else 1), "CRLF", crlf, "bareLF", rest.count("\n"), "loneCR", rest.count("\r"))
    for i, line in enumerate(raw.split("\r\n"), 1):
        s = line
        if ("leftSeed" in s and "public static int" in s) or "VANILLA_HEALTH_BASE =" in s or "VANILLA_ROW_HEIGHT =" in s or "RESET_RIGHT =" in s or "RESET_RIGHT_NO_AIR =" in s or "leftHeight = leftSeedOfCameraPlayer" in s or "private static int leftSeedOfCameraPlayer" in s or "y = height - gui.leftHeight" in s or "gui.leftHeight += 10" in s:
            print("   ", i, s.strip()[:100])
