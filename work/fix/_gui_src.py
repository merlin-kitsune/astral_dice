import zipfile
J = r"F:/MCProject/astral_dice_multiloader/forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar"
with zipfile.ZipFile(J) as z:
    names = [n for n in z.namelist() if n.endswith("client/gui/Gui.java")]
    print("ENTRIES:", names)
    src = z.read(names[0]).decode("utf-8", "replace")
lines = src.splitlines()
start = None
for i, l in enumerate(lines):
    if "renderPlayerHealth" in l:
        print(i + 1, l.strip()[:160])
print("TOTAL LINES", len(lines))
