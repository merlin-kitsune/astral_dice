
import os, subprocess, hashlib, shutil, glob
root = r"F:/MCProject/astral_dice_multiloader"
src = os.path.join(root, "fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/client/gui/overlay/ForgeGui.java")
raw = open(src, encoding="utf-8", newline="").read()
t = raw.replace("\r\n", "\n")
newblock = '''     * {@code net/minecraft/client/gui/Gui.java};括号内的「偏移」取自 fabric 线**实际运行**的那份
     * 合并 jar —— {@code fabric-1.20.1/build/loom-cache/argFiles/runClient} 的 classpath 条目
     * {@code .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-83c93f4af0/
     * 1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-83c93f4af0-....jar}
     * (sha256 {@code d9128e827b7c385c...},与 {@code minecraft-merged-0b0f9d549f} 逐字节相同),
     * 由 {@code javap -p -c -l net.minecraft.client.gui.Gui} 逐条读出):'''
oldblock = '''     * {@code net/minecraft/client/gui/Gui.java};括号内是 fabric 线**实际运行**映射
     * {@code .gradle/loom-cache/minecraftMaven/.../minecraft-merged-2a6f3185c8-....jar} 的
     * {@code Gui#renderPlayerHealth} 字节码偏移):'''
assert t.count(newblock) == 1
pre = t.replace(newblock, oldblock)
tmp = os.path.join(root, ".tmp/cmppatch")
shutil.rmtree(tmp, ignore_errors=True)
for d in ("a","b"): os.makedirs(os.path.join(tmp,d), exist_ok=True)
def w(p, s):
    open(p, "w", encoding="utf-8", newline="\n").write(s)
w(os.path.join(tmp,"a","ForgeGui.java"), pre)
w(os.path.join(tmp,"b","ForgeGui.java"), t)
jar = glob.glob(os.path.join(root, ".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-83c93f4af0/*/*.jar"))[0]
javac = r"C:/Program Files/Zulu/zulu-21/bin/javac.exe"
for d in ("a","b"):
    out = os.path.join(tmp, d, "out"); os.makedirs(out, exist_ok=True)
    r = subprocess.run([javac, "-nowarn", "-cp", jar, "-d", out, os.path.join(tmp,d,"ForgeGui.java")], capture_output=True, text=True, encoding="utf-8", errors="replace")
    print(d, "rc", r.returncode, r.stderr[:300])
def code(p):
    jp = r"C:/Program Files/Zulu/zulu-21/bin/javap.exe"
    r = subprocess.run([jp, "-p", "-c", "-cp", os.path.join(tmp,p,"out"), "com.merlinkitsune.astral_dice.platform.client.gui.overlay.ForgeGui"], capture_output=True, text=True, encoding="utf-8", errors="replace")
    return r.stdout
ca, cb = code("a"), code("b")
print("instr-equal:", ca == cb, len(ca), len(cb))
def rawsha(p):
    f = os.path.join(tmp,p,"out","com/merlinkitsune/astral_dice/platform/client/gui/overlay/ForgeGui.class")
    return hashlib.sha256(open(f,'rb').read()).hexdigest()[:16]
print("classfile sha a/b:", rawsha("a"), rawsha("b"))
