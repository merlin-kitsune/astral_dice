import zipfile
J = r"D:/MCProject/astral_dice_multiloader/forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar"
with zipfile.ZipFile(J) as z:
    src = z.read("net/minecraft/client/gui/Gui.java").decode("utf-8", "replace")
lines = src.splitlines()
for i in range(839, 1100):
    if i < len(lines):
        print(i + 1, lines[i])
