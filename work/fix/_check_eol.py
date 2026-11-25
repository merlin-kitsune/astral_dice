import io
paths = [
 r"D:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/CombatPowerHud.java",
 r"D:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/client/gui/overlay/ForgeGui.java",
 r"D:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/client/FabricClientBridges.java",
]
for p in paths:
    b = open(p, "rb").read()
    crlf = b.count(b"\r\n")
    lf = b.count(b"\n")
    bare_lf = lf - crlf
    crlf_cr = b.count(b"\r\r\n")
    print("%s bytes=%d CRLF=%d bare_LF=%d CRCRLF=%d endswith_CRLF=%s" % (p.split("/")[-1], len(b), crlf, bare_lf, crlf_cr, b.endswith(b"\r\n")))
