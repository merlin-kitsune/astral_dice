#!/usr/bin/env python3
"""convert_cases_autojs.py — legacy 用例 → AutoJS 通道的机械化迁移器。

映射表（每条都可复算，不做语义猜测）：
  inject_command  → eval_js(side=server)：同一个命令字符串交给服务端命令分发器
                    （performPrefixedCommand + suppressOutput），**不再走聊天注入**
  inject_key      → eval_js(side=client)：按 GLFW 键码走 KeyMapping.click 真映射路径
                    （旧 alias 表：chat=t / skill=j / cancel=esc / debug=f3 / inventory=e / card=h）
  inject_mouse    → eval_js(side=client)：Screen.mouseClicked(屏幕中心, button)
  screenshot      → 删除（截图不再是判据来源；像素判据见 vision 断言的分档说明）
  kubejs_reload   → 删除（channel=autojs 下该原语已退役）
  wait            → 保留（迁移期兼容；执行器会打 deprecation 警告）

不做的转换（避免造假判据）：
  · log / absent / crash / kubejs / mixin 断言 **原样保留**（日志通道仍是这些事实的唯一来源）
  · vision 断言原样保留 ⇒ 运行时记 BLOCKED（WSL 下无截图语义通道），在报告里点名

用法：
  python3 scripts/test/convert_cases_autojs.py --cases-dir scripts/test/cases --dry-run
  python3 scripts/test/convert_cases_autojs.py --cases-dir scripts/test/cases
"""
from __future__ import annotations

import argparse
import glob
import json
import os

HOOKS = {
    "1.21.1": "net.neoforged.neoforge.server.ServerLifecycleHooks",
    "26.1.2": "net.neoforged.neoforge.server.ServerLifecycleHooks",
    "1.20.1": "net.minecraftforge.server.ServerLifecycleHooks",
    "fabric-1.20.1": "net.minecraftforge.server.ServerLifecycleHooks",
}

ALIAS = {"chat": 84, "skill": 74, "cancel": 256, "debug": 292, "inventory": 69, "card": 72}
GLFW = {"escape": 256, "enter": 257, "tab": 258, "space": 32, "shift": 340, "ctrl": 341}
for _i in range(10):
    GLFW[str(_i)] = 48 + _i
for _c in "abcdefghijklmnopqrstuvwxyz":
    GLFW[_c] = ord(_c.upper())
for _i in range(1, 13):
    GLFW[f"f{_i}"] = 289 + _i


def key_code(name: str) -> int | None:
    n = str(name).strip().lower()
    if n in ALIAS:
        return ALIAS[n]
    if n in GLFW:
        return GLFW[n]
    return None


def js_str(s: str) -> str:
    return json.dumps(s, ensure_ascii=False)


def cmd_code(command: str, hooks: str) -> str:
    # 源必须是**玩家**：legacy 的聊天注入让探针拿到 player 上下文，且探针用 sendFailure
    # 回显（→ 客户端聊天 → latest.log）；换成控制台源或 withSuppressedOutput 都会让 AP_* 行消失。
    return (
        "(function(){\n"
        f"  var SH = Java.loadClass('{hooks}');\n"
        "  var s = SH.getCurrentServer();\n"
        "  if (s === null) { return { ok: false, reason: 'no-server' }; }\n"
        "  var pl = s.getPlayerList().getPlayers();\n"
        "  var src = (pl.size() > 0) ? pl.get(0).createCommandSourceStack() : s.createCommandSourceStack();\n"
        f"  var cmd = {js_str(command)};\n"
        "  s.getCommands().performPrefixedCommand(src, cmd);\n"
        "  return { ok: true, cmd: cmd };\n"
        "})()"
    )


def key_code_js(code: int) -> str:
    return (
        "(function(){\n"
        "  var IC = Java.loadClass('com.mojang.blaze3d.platform.InputConstants');\n"
        "  var KM = Java.loadClass('net.minecraft.client.KeyMapping');\n"
        f"  var k = IC.Type.KEYSYM.getOrCreate({code});\n"
        "  KM.click(k);\n"
        f"  return {{ ok: true, keysym: {code} }};\n"
        "})()"
    )


RCLICK_JS = (
    "(function(){\n"
    "  var MC = Java.loadClass('net.minecraft.client.Minecraft');\n"
    "  var mc = MC.getInstance();\n"
    "  if (mc.screen !== null) {\n"
    "    var w = mc.getWindow().getGuiScaledWidth();\n"
    "    var h = mc.getWindow().getGuiScaledHeight();\n"
    "    return { ok: true, path: 'screen', consumed: !!mc.screen.mouseClicked(w / 2, h / 2, 1) };\n"
    "  }\n"
    "  var IH = Java.loadClass('net.minecraft.world.InteractionHand');\n"
    "  var res = mc.gameMode.useItem(mc.player, IH.MAIN_HAND);\n"
    "  return { ok: true, path: 'world', result: String(res) };\n"
    "})()"
)


def mouse_code_js(button: str) -> str:
    btn = 1 if str(button).lower().startswith("r") else 0
    return (
        "(function(){\n"
        "  var MC = Java.loadClass('net.minecraft.client.Minecraft');\n"
        "  var mc = MC.getInstance();\n"
        "  if (mc.screen === null) { return { ok: false, reason: 'no-screen' }; }\n"
        "  var w = mc.getWindow().getGuiScaledWidth();\n"
        "  var h = mc.getWindow().getGuiScaledHeight();\n"
        f"  var consumed = mc.screen.mouseClicked(w / 2, h / 2, {btn});\n"
        f"  return {{ ok: true, button: {btn}, consumed: !!consumed }};\n"
        "})()"
    )


def migrate(doc: dict, hooks: str, stats: dict, path: str) -> dict:
    steps = []
    for step in doc.get("steps", []):
        op = step.get("op")
        if op == "inject_command":
            cmd = str(step.get("command", ""))
            steps.append({"op": "eval_js", "side": "server", "timeout_ms": 20000,
                          "code": cmd_code(cmd, hooks)})
            stats["inject_command"] += 1
        elif op == "inject_key":
            name = str(step.get("key", ""))
            if name.strip().lower() == "rclick":
                steps.append({"op": "eval_js", "side": "client", "timeout_ms": 20000,
                              "code": RCLICK_JS})
                stats["inject_key"] += 1
                continue
            code = key_code(name)
            if code is None:
                stats["unmapped"].append(f"{path}: inject_key {name!r} 无键码映射（已删除该步）")
                continue
            steps.append({"op": "eval_js", "side": "client", "timeout_ms": 20000,
                          "code": key_code_js(code)})
            stats["inject_key"] += 1
        elif op == "inject_mouse":
            steps.append({"op": "eval_js", "side": "client", "timeout_ms": 20000,
                          "code": mouse_code_js(step.get("button", "left"))})
            stats["inject_mouse"] += 1
        elif op == "screenshot":
            stats["screenshot"] += 1
        elif op == "kubejs_reload":
            stats["kubejs_reload"] += 1
        else:
            steps.append(step)
    out = {}
    for k, v in doc.items():
        if k == "steps":
            out["steps"] = steps
            if "channel" not in doc:
                out["channel"] = "autojs"
        else:
            out[k] = v
    if "channel" not in out:
        out["channel"] = "autojs"
    out.setdefault("migration", {"from": "legacy-kubejs", "at": "2026-10-18",
                                 "tool": "scripts/test/convert_cases_autojs.py"})
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--cases-dir", default="scripts/test/cases")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    files = sorted(glob.glob(os.path.join(a.cases_dir, "**", "*.json"), recursive=True))
    files = [f for f in files if not os.path.basename(f).startswith(".mt_") and ".mt_results" not in f]
    stats = {"inject_command": 0, "inject_key": 0, "inject_mouse": 0,
             "screenshot": 0, "kubejs_reload": 0, "files": 0, "skipped": 0, "unmapped": []}
    for f in files:
        doc = json.load(open(f, encoding="utf-8"))
        if doc.get("channel") == "autojs":
            stats["skipped"] += 1
            continue
        ver = str(doc.get("version", ""))
        hooks = HOOKS.get(ver, HOOKS["1.21.1"])
        new = migrate(doc, hooks, stats, os.path.basename(f))
        stats["files"] += 1
        if not a.dry_run:
            with open(f, "w", encoding="utf-8", newline="\n") as fh:
                json.dump(new, fh, ensure_ascii=False, indent=2)
                fh.write("\n")
    print(json.dumps({k: v for k, v in stats.items() if k != "unmapped"},
                     ensure_ascii=False, indent=1))
    for line in stats["unmapped"]:
        print("UNMAPPED " + line)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
