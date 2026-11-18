# -*- coding: utf-8 -*-
import os, sys, time, shutil, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import aj

ROOT = r"F:/MCProject/astral_dice_multiloader"
OUT = os.path.join(ROOT, "work/shots/fabric-defensebar")
SRC_DIR = os.path.join(ROOT, "fabric-1.20.1/run/client/screenshots")

PREP = '''var MCQ = Java.loadClass("net.minecraft.client.Minecraft");
var mc = MCQ.getInstance();
mc.options.pauseOnLostFocus = false;
mc.setScreen(null);
mc.gui.getChat().clearMessages(true);
"prep-ok";'''

GRAB = '''var MCQ = Java.loadClass("net.minecraft.client.Minecraft");
var mc = MCQ.getInstance();
var Scr = Java.loadClass("net.minecraft.client.Screenshot");
Scr.grab(mc.gameDirectory, "__NAME__", mc.getMainRenderTarget(), function(c){ });
JSON.stringify({dir: String(mc.gameDirectory), name: "__NAME__"});'''

def evaljs(code):
    return aj.call("eval_js", {"side": "client", "code": code})

name = sys.argv[1]
if not name.endswith(".png"): name += ".png"
os.makedirs(OUT, exist_ok=True)
r1 = evaljs(PREP)
print("prep:", json.dumps(r1.get("value", r1), ensure_ascii=False)[:200])
time.sleep(0.6)
r2 = evaljs(GRAB.replace("__NAME__", name))
print("grab:", json.dumps(r2.get("value", r2), ensure_ascii=False)[:400])
src = os.path.join(SRC_DIR, name)
for _ in range(30):
    time.sleep(0.5)
    if os.path.exists(src) and os.path.getsize(src) > 0:
        break
if not (os.path.exists(src) and os.path.getsize(src) > 0):
    print("FAIL: screenshot not written:", src)
    sys.exit(1)
dst = os.path.join(OUT, name)
shutil.copy2(src, dst)
print("saved:", dst, os.path.getsize(dst), "B")
