
import io
p = r"F:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/client/gui/overlay/ForgeGui.java"
raw = open(p, 'r', encoding='utf-8', newline='').read()
t = raw.replace('\r\n', '\n')
old = '''     * {@code net/minecraft/client/gui/Gui.java};括号内是 fabric 线**实际运行**映射
     * {@code .gradle/loom-cache/minecraftMaven/.../minecraft-merged-2a6f3185c8-....jar} 的
     * {@code Gui#renderPlayerHealth} 字节码偏移):'''
new = '''     * {@code net/minecraft/client/gui/Gui.java};括号内的「偏移」取自 fabric 线**实际运行**的那份
     * 合并 jar —— {@code fabric-1.20.1/build/loom-cache/argFiles/runClient} 的 classpath 条目
     * {@code .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-83c93f4af0/
     * 1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-83c93f4af0-....jar}
     * (sha256 {@code d9128e827b7c385c...},与 {@code minecraft-merged-0b0f9d549f} 逐字节相同),
     * 由 {@code javap -p -c -l net.minecraft.client.gui.Gui} 逐条读出):'''
assert t.count(old) == 1, t.count(old)
t = t.replace(old, new)
out = t.replace('\n', '\r\n')
open(p, 'w', encoding='utf-8', newline='').write(out)
print("CRLF", out.count('\r\n'), "bareLF", out.replace('\r\n','').count('\n'), "loneCR", out.replace('\r\n','').count('\r'))
print("lines", len(out.split('\r\n')))
