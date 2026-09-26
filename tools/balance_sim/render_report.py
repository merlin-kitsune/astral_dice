# -*- coding: utf-8 -*-
"""
骰战数值仿真报告渲染器
======================
⚠️ 已过期（2026-09-26 标注）：本脚本内置的报告正文与表格仍描述**重标定前**的骰战模型
   （护甲统一除数 2 / 玩家韧性系数 1.4 / 生物 1.125 / 敌对初始防御 2 / 敌对初始攻击 5）。
   现行库口径 = 玩家 `4 + 护甲×0.30 + 0.85×韧性`、生物 `初始 + 护甲×0.40 + 1.0×韧性`
   （两侧均无 20 上限）、敌对初始 防御 0 / 攻击 4、中立初始攻击 3 —— 详见 AGENTS.md
   「骰战闪避与防御规范」。重标定前请勿据其中结论做新判断。
读取 dice_combat_sim.py 产出的 JSON，生成：
  - dice-combat-simulation.html （浅色主题、内联 SVG 图表，可直接预览）
  - dice-combat-simulation.md   （仓库原生文本版，含全部数据表）

全部图形为手写内联 SVG，不依赖任何外链资源（离线可看）。
"""

from __future__ import annotations

import argparse
import json
import math
import os
from typing import Dict, List, Sequence, Tuple

# ---------------------------------------------------------------- 主题（浅色）
C_BG = "#ffffff"
C_PANEL = "#f7f8fa"
C_BORDER = "#e3e6ea"
C_TEXT = "#1f2328"
C_MUTED = "#6b7480"
C_GRID = "#eceff3"

SERIES_COLORS = {
    "vanilla": "#4a7fe0",
    "vanilla_sharp": "#9fb4d8",
    "dice_bare": "#e08a4a",
    "dice_cards": "#b8452f",
    "dice_nodice": "#b48ead",
    "dice_dice": "#e08a4a",
    "dice_dcards": "#7d5ba6",
}


def esc(s: object) -> str:
    return (str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


# ---------------------------------------------------------------- SVG 工具
def _nice_max(v: float) -> float:
    if v <= 0:
        return 1.0
    exp = math.floor(math.log10(v))
    base = 10 ** exp
    for m in (1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10):
        if v <= m * base:
            return m * base
    return 10 * base


def _nice_range(lo: float, hi: float, target: int = 5) -> Tuple[float, float, float]:
    """把 [lo, hi] 外扩到「整数刻度」上，返回 (lo', hi', step)。要求 lo<=0<=hi。"""
    if hi <= lo:
        return lo, lo + 1.0, 1.0
    raw = (hi - lo) / target
    base = 10.0 ** math.floor(math.log10(raw))
    step = base
    for m in (1.0, 2.0, 2.5, 5.0, 10.0):
        step = m * base
        if (hi - lo) / step <= target + 1e-9:
            break
    return math.floor(lo / step) * step, math.ceil(hi / step) * step, step


def svg_grouped_bars(cats: Sequence[str], series: Sequence[Tuple[str, Sequence[float], str]],
                     *, width: int = 900, height: int = 360, ylabel: str = "",
                     fmt: str = "{:.2f}", ystep: int = 5, diverging: bool = False) -> str:
    """分组柱状图。series = [(名称, [每个类目的值], 颜色), ...]

    ``diverging=True`` 时按**有符号**渲染：以 0 为基线向上下两侧伸柱、刻度自动取整，
    适用于 Δ%（可为负）这类数据；默认 False 时基线固定在底部、按 ``ystep`` 等分。
    """
    ml, mr, mt, mb = 62, 16, 16, 62
    pw, ph = width - ml - mr, height - mt - mb
    n_s = len(series)
    n_c = len(cats)
    slot = pw / max(n_c, 1)
    bw = min(28.0, slot / (n_s + 1.0) * 1.0)
    if diverging:
        allv = [v for s in series for v in s[1]]
        vmax, vmin = (max(allv), min(allv)) if allv else (0.0, 0.0)
        hi = _nice_max(vmax) if vmax > 0 else 0.0
        lo = -_nice_max(-vmin) if vmin < 0 else 0.0
        if hi <= lo:                      # 全 0 或空：给退化区间，避免除零
            hi = lo + 1.0
        lo, hi, step = _nice_range(lo, hi)
        ticks = [lo + step * i for i in range(int(round((hi - lo) / step)) + 1)]
    else:
        hi = _nice_max(max((max(s[1]) for s in series if len(s[1])), default=1.0))
        lo, step = 0.0, hi / ystep
        ticks = [lo + step * i for i in range(ystep + 1)]

    def ty(v: float) -> float:
        return mt + ph - ph * (v - lo) / (hi - lo)

    out = [f'<svg viewBox="0 0 {width} {height}" width="100%" role="img" '
           f'style="font-family:Segoe UI,Microsoft YaHei,sans-serif">']
    # 网格 + Y 轴刻度
    for v in ticks:
        y = ty(v)
        lab = fmt.format(v)
        if abs(v) < 1e-9 and lab.startswith("+"):
            lab = lab[1:]
        out.append(f'<line x1="{ml}" y1="{y:.1f}" x2="{ml+pw}" y2="{y:.1f}" stroke="{C_GRID}" stroke-width="1"/>')
        out.append(f'<text x="{ml-8}" y="{y+4:.1f}" font-size="11" fill="{C_MUTED}" text-anchor="end">{lab}</text>')
    # 零基线（仅发散图且跨零时强调）
    y_zero = ty(0.0)
    if diverging and lo < 0.0 < hi:
        out.append(f'<line x1="{ml}" y1="{y_zero:.1f}" x2="{ml+pw}" y2="{y_zero:.1f}" '
                   f'stroke="{C_BORDER}" stroke-width="1.5"/>')
    # 柱
    for ci, c in enumerate(cats):
        x0 = ml + slot * ci
        group_w = bw * n_s
        sx = x0 + (slot - group_w) / 2.0
        for si, (name, vals, color) in enumerate(series):
            v = vals[ci] if ci < len(vals) else 0.0
            y_v = ty(v)
            top, bot = min(y_v, y_zero), max(y_v, y_zero)
            x = sx + si * bw
            out.append(f'<rect x="{x:.1f}" y="{top:.1f}" width="{max(bw-2.0,1.0):.1f}" '
                       f'height="{max(bot-top,0.6):.1f}" fill="{color}" rx="1.5"/>')
        out.append(f'<text x="{x0+slot/2:.1f}" y="{mt+ph+18:.1f}" font-size="11.5" fill="{C_TEXT}" '
                   f'text-anchor="middle">{esc(c)}</text>')
    # 轴
    out.append(f'<line x1="{ml}" y1="{mt+ph}" x2="{ml+pw}" y2="{mt+ph}" stroke="{C_BORDER}" stroke-width="1.2"/>')
    if ylabel:
        out.append(f'<text x="14" y="{mt+ph/2:.1f}" font-size="11.5" fill="{C_MUTED}" text-anchor="middle" '
                   f'transform="rotate(-90 14 {mt+ph/2:.1f})">{esc(ylabel)}</text>')
    out.append('</svg>')
    return "".join(out)


def svg_lines(xcats: Sequence[str], series: Sequence[Tuple[str, Sequence[float], str]],
              *, width: int = 900, height: int = 380, ylabel: str = "",
              fmt: str = "{:.2f}", ystep: int = 5, log_scale: bool = False) -> str:
    """折线图。series = [(名称, [每个类目的值], 颜色), ...]"""
    ml, mr, mt, mb = 62, 16, 16, 62
    pw, ph = width - ml - mr, height - mt - mb
    allv = [v for s in series for v in s[1]]
    if log_scale:
        lo = max(min(allv), 0.01) if allv else 0.01
        hi = max(allv) if allv else 1.0
        lg_lo, lg_hi = math.log10(lo * 0.7), math.log10(hi * 1.4)

        def ty(v: float) -> float:
            v = max(v, 1e-6)
            return mt + ph - ph * (math.log10(v) - lg_lo) / (lg_hi - lg_lo)
    else:
        gmax = _nice_max(max(allv) if allv else 1.0)
        lo, hi = 0.0, gmax

        def ty(v: float) -> float:
            return mt + ph - ph * (v - lo) / (hi - lo)
    n_c = len(xcats)
    slot = pw / max(n_c, 1)
    out = [f'<svg viewBox="0 0 {width} {height}" width="100%" role="img" '
           f'style="font-family:Segoe UI,Microsoft YaHei,sans-serif">']
    # 网格
    ticks = []
    if log_scale:
        e = math.floor(lg_lo)
        while e <= math.ceil(lg_hi):
            v = 10.0 ** e
            if lo <= v <= hi:
                ticks.append(v)
            e += 1
    else:
        ticks = [gmax * i / ystep for i in range(ystep + 1)]
    for v in ticks:
        y = ty(v)
        out.append(f'<line x1="{ml}" y1="{y:.1f}" x2="{ml+pw}" y2="{y:.1f}" stroke="{C_GRID}" stroke-width="1"/>')
        out.append(f'<text x="{ml-8}" y="{y+4:.1f}" font-size="11" fill="{C_MUTED}" text-anchor="end">{fmt.format(v)}</text>')
    # 折线
    for name, vals, color in series:
        pts = " ".join(f"{ml+slot*(i+0.5):.1f},{ty(v):.1f}" for i, v in enumerate(vals))
        out.append(f'<polyline points="{pts}" fill="none" stroke="{color}" stroke-width="2.2" '
                   f'stroke-linejoin="round" stroke-linecap="round"/>')
        for i, v in enumerate(vals):
            out.append(f'<circle cx="{ml+slot*(i+0.5):.1f}" cy="{ty(v):.1f}" r="3.2" fill="{color}"/>')
    # X 轴标签
    for i, c in enumerate(xcats):
        out.append(f'<text x="{ml+slot*(i+0.5):.1f}" y="{mt+ph+18:.1f}" font-size="11.5" fill="{C_TEXT}" '
                   f'text-anchor="middle">{esc(c)}</text>')
    out.append(f'<line x1="{ml}" y1="{mt+ph}" x2="{ml+pw}" y2="{mt+ph}" stroke="{C_BORDER}" stroke-width="1.2"/>')
    if ylabel:
        out.append(f'<text x="14" y="{mt+ph/2:.1f}" font-size="11.5" fill="{C_MUTED}" text-anchor="middle" '
                   f'transform="rotate(-90 14 {mt+ph/2:.1f})">{esc(ylabel)}</text>')
    out.append('</svg>')
    return "".join(out)


def legend(items: Sequence[Tuple[str, str]]) -> str:
    parts = ['<div class="legend">']
    for name, color in items:
        parts.append(f'<span><i style="background:{color}"></i>{esc(name)}</span>')
    parts.append('</div>')
    return "".join(parts)


def table(headers: Sequence[str], rows: Sequence[Sequence[object]], cls: str = "") -> str:
    p = [f'<table class="{cls}"><thead><tr>']
    for h in headers:
        p.append(f'<th>{esc(h)}</th>')
    p.append('</tr></thead><tbody>')
    for r in rows:
        p.append('<tr>' + "".join(f'<td>{c}</td>' if isinstance(c, str) and c.startswith("<") else f'<td>{esc(c)}</td>' for c in r) + '</tr>')
    p.append('</tbody></table>')
    return "".join(p)


# ---------------------------------------------------------------- 报告
def build(js: Dict) -> Tuple[str, str]:
    meta = js["meta"]
    exp1 = js["exp1_player_output"]["rows"]
    exp2 = js["exp2_player_intake"]["rows"]
    exp3 = js["exp3_modifier_matrix"]
    exp4 = js["exp4_curse"]

    def row1(mob: str, weapon: str) -> Dict:
        for r in exp1:
            if r["mob"] == mob and r["weapon"] == weapon:
                return r
        raise KeyError((mob, weapon))

    def row2(armor: str, mob: str) -> Dict:
        for r in exp2:
            if r["armor_set"] == armor and r["mob"] == mob:
                return r
        raise KeyError((armor, mob))

    # ---------------- 图 1：玩家→生物 每击伤害（僵尸）
    weapons = list(js["weapons"].keys())
    z = [row1("僵尸", w) for w in weapons]
    f1 = svg_grouped_bars(
        weapons,
        [("原版（无附魔）", [r["vanilla"] for r in z], SERIES_COLORS["vanilla"]),
         ("原版 + 锋利 V", [r["vanilla_sharp5"] for r in z], SERIES_COLORS["vanilla_sharp"]),
         ("骰战（仅骰子）", [r["dice_bare_mean"] for r in z], SERIES_COLORS["dice_bare"]),
         ("骰战（3×特大攻击牌）", [r["dice_epic3_mean"] for r in z], SERIES_COLORS["dice_cards"])],
        ylabel="每击期望伤害")

    # ---------------- 图 2：TTK
    mobs_ttk = ["僵尸", "末影人", "劫掠兽", "监守者"]
    ttk_v, ttk_d, ttk_dc = [], [], []
    for mob in mobs_ttk:
        r = row1(mob, "钻石剑")
        ttk_v.append(float(r["vanilla_ttk"]))
        ttk_d.append(r["dice_bare_ttk"])
        ttk_dc.append(r["dice_epic3_ttk"])
    f2 = svg_grouped_bars(
        mobs_ttk,
        [("原版（钻石剑）", ttk_v, SERIES_COLORS["vanilla"]),
         ("骰战（仅骰子）", ttk_d, SERIES_COLORS["dice_bare"]),
         ("骰战（3×特大攻击牌）", ttk_dc, SERIES_COLORS["dice_cards"])],
        ylabel="击杀所需击数（越低越快）", fmt="{:.0f}", ystep=5)

    # ---------------- 图 3：生物→玩家 承伤曲线（僵尸 / 末影人 / 监守者）
    armors = list(js["armor_sets"].keys())
    panels = []
    for mob in ("僵尸", "末影人", "监守者"):
        rs = [row2(a, mob) for a in armors]
        panels.append(svg_lines(
            armors,
            [("原版", [r["vanilla"] for r in rs], SERIES_COLORS["vanilla"]),
             ("骰战（未装备骰子）", [r["dice_nodice_mean"] for r in rs], SERIES_COLORS["dice_nodice"]),
             ("骰战（装备骰子）", [r["dice_dice_mean"] for r in rs], SERIES_COLORS["dice_dice"]),
             ("骰战（装备骰子 + 3×特级防御牌）", [r["dice_dice_cards_mean"] for r in rs], SERIES_COLORS["dice_dcards"])],
            height=300, ylabel=f"{mob} 每击期望伤害"))
    f3 = panels

    # ---------------- 图 4：修饰器传导 Δ%
    in_rows = exp3["intake_rows"]
    f4 = svg_grouped_bars(
        [r["modifier"] for r in in_rows],
        [("原版 Δ% · 强基准", [r["strong_vanilla_delta_pct"] for r in in_rows], SERIES_COLORS["vanilla"]),
         ("骰战 Δ% · 强基准", [r["strong_dice_delta_pct"] for r in in_rows], SERIES_COLORS["dice_dice"]),
         ("原版 Δ% · 弱基准", [r["weak_vanilla_delta_pct"] for r in in_rows], SERIES_COLORS["vanilla_sharp"]),
         ("骰战 Δ% · 弱基准", [r["weak_dice_delta_pct"] for r in in_rows], SERIES_COLORS["dice_bare"])],
        ylabel="相对基准的变化（%）", fmt="{:+.0f}", height=380, ystep=4, diverging=True)

    out_rows = exp3["output_rows"]
    f5 = svg_grouped_bars(
        [r["modifier"] for r in out_rows],
        [("原版 Δ%", [r["vanilla_delta_pct"] for r in out_rows], SERIES_COLORS["vanilla"]),
         ("骰战 Δ%", [r["dice_delta_pct"] for r in out_rows], SERIES_COLORS["dice_dice"])],
        ylabel="相对基准的变化（%）", fmt="{:+.0f}", height=340, ystep=4, diverging=True)

    # ---------------- 图 5：七咒之戒 承伤
    curse_rows = exp4["armor_rows"]
    mobs_c = ["僵尸", "末影人", "劫掠兽", "监守者", "铁傀儡"]
    f6 = svg_grouped_bars(
        armors,
        [("有戒指·原版", [next(r["vanilla_after"] for r in curse_rows if r["armor_set"] == a and r["mob"] == "僵尸") for a in armors],
          SERIES_COLORS["vanilla"]),
         ("无戒指·原版", [next(r["vanilla_before"] for r in curse_rows if r["armor_set"] == a and r["mob"] == "僵尸") for a in armors],
          SERIES_COLORS["vanilla_sharp"]),
         ("有戒指·骰战", [next(r["dice_after"] for r in curse_rows if r["armor_set"] == a and r["mob"] == "僵尸") for a in armors],
          SERIES_COLORS["dice_cards"]),
         ("无戒指·骰战", [next(r["dice_before"] for r in curse_rows if r["armor_set"] == a and r["mob"] == "僵尸") for a in armors],
          SERIES_COLORS["dice_bare"])],
        ylabel="僵尸每击期望伤害", height=340)

    # ================= 汇总统计 =================
    ratios_out = [row1(m, "钻石剑")["dice_bare_mean"] / row1(m, "钻石剑")["vanilla"]
                  for m in ("僵尸", "末影人", "劫掠兽", "监守者") if row1(m, "钻石剑")["vanilla"] > 0]
    avg_ratio_out = sum(ratios_out) / len(ratios_out)
    naked = [row2("裸装", m) for m in ("僵尸", "末影人", "监守者")]
    naked_ratio = sum(r["dice_dice_mean"] / r["vanilla"] for r in naked) / len(naked)
    full = [row2("钻石全套", m) for m in ("僵尸", "末影人", "监守者")]
    full_ratio = sum(r["dice_dice_mean"] / r["vanilla"] for r in full) / len(full)

    H: List[str] = []
    A = H.append

    A(f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<title>骰战 vs 原版战斗 · 数值仿真报告（1.21.1）</title>
<style>
  :root {{ --bg:{C_BG}; --panel:{C_PANEL}; --bd:{C_BORDER}; --tx:{C_TEXT}; --mu:{C_MUTED}; }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; background:var(--bg); color:var(--tx);
         font-family:"Segoe UI","Microsoft YaHei",system-ui,sans-serif; line-height:1.7; }}
  .wrap {{ max-width:1080px; margin:0 auto; padding:34px 26px 80px; }}
  h1 {{ font-size:26px; margin:0 0 6px; letter-spacing:.2px; }}
  h2 {{ font-size:19px; margin:40px 0 12px; padding-bottom:7px; border-bottom:2px solid var(--bd); }}
  h3 {{ font-size:15.5px; margin:24px 0 8px; color:#2f3742; }}
  .sub {{ color:var(--mu); font-size:13px; margin:0 0 22px; }}
  .card {{ background:var(--panel); border:1px solid var(--bd); border-radius:10px; padding:18px 20px; margin:16px 0; }}
  .kpis {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(215px,1fr)); gap:12px; margin:18px 0 6px; }}
  .kpi {{ background:var(--panel); border:1px solid var(--bd); border-radius:10px; padding:14px 16px; }}
  .kpi .v {{ font-size:24px; font-weight:650; letter-spacing:.3px; }}
  .kpi .k {{ font-size:12.5px; color:var(--mu); margin-top:3px; }}
  table {{ border-collapse:collapse; width:100%; font-size:13px; margin:10px 0 4px; }}
  th,td {{ border:1px solid var(--bd); padding:6px 9px; text-align:right; }}
  th:first-child,td:first-child {{ text-align:left; }}
  thead th {{ background:#eef1f5; font-weight:600; }}
  tbody tr:nth-child(even) {{ background:#fafbfc; }}
  .legend {{ display:flex; flex-wrap:wrap; gap:16px; font-size:12.5px; color:#3c444f; margin:6px 0 2px; }}
  .legend i {{ display:inline-block; width:11px; height:11px; border-radius:2px; margin-right:6px; vertical-align:-1px; }}
  .chart {{ margin:8px 0 4px; }}
  code {{ background:#eef1f5; padding:1px 5px; border-radius:4px; font-size:12.5px; }}
  .warn {{ border-left:4px solid #d9534f; background:#fdf1f0; }}
  .ok {{ border-left:4px solid #3f9d63; background:#f0f8f3; }}
  .note {{ border-left:4px solid #b98b3a; background:#fdf8ee; }}
  ul {{ padding-left:20px; margin:8px 0; }}
  li {{ margin:4px 0; }}
  .mono {{ font-family:Consolas,Menlo,monospace; font-size:12.5px; }}
  .grid2 {{ display:grid; grid-template-columns:1fr; gap:6px; }}
  footer {{ margin-top:44px; color:var(--mu); font-size:12px; border-top:1px solid var(--bd); padding-top:14px; }}
</style></head><body><div class="wrap">""")

    A(f"""<h1>骰战 vs 原版战斗 · 数值仿真报告</h1>
<p class="sub">基准版本 Minecraft 1.21.1 / NeoForge 21.1.235 · 蒙特卡洛 {meta['n_samples']:,} 采样/单元 ·
TTK 模拟 {meta['ttk_trials']:,} 次 · 随机种子 {meta['seed']} · 公式事实源：库 <code>CombatFormula</code> +
模组 <code>DiceCombatEvents</code> + 原版 <code>CombatRules</code>/<code>LivingEntity</code></p>""")

    # ---------- 摘要 ----------
    A(f"""<div class="kpis">
<div class="kpi"><div class="v">{avg_ratio_out:.2f}×</div><div class="k">玩家输出：骰战 ÷ 原版（钻石剑，四类生物均值）</div></div>
<div class="kpi"><div class="v">{naked_ratio:.2f}×</div><div class="k">承伤（裸装）：骰战 ÷ 原版</div></div>
<div class="kpi"><div class="v">{full_ratio:.2f}×</div><div class="k">承伤（钻石全套）：骰战 ÷ 原版</div></div>
<div class="kpi"><div class="v">2 次</div><div class="k">生物→玩家路径中护甲的计入次数</div></div>
</div>""")

    A("""<div class="card warn"><b>三条硬结论（详见第 3–5 节）</b><ul>
<li><b>骰战完全旁路原版护甲减免公式</b>：骰战以 <code>setNewDamage</code> 覆盖式写入自算值，原版
<code>CombatRules.getDamageAfterAbsorb</code>（含 20 点上限、<code>护甲/5</code> 下限）算出的那份值被整段丢弃，
护甲只经自定义公式 <code>护甲÷2</code> 参与一次。</li>
<li><b>生物→玩家路径把护甲计入两次、并让保护附魔与抗性提升「泄漏」进攻击项</b>：该路径把
<code>event.getNewDamage()</code>（<i>已含</i>护甲/抗性/保护的伤害值）当作<b>加项</b>塞进攻击点，
同时又用 <code>playerDefense</code>（含 <code>护甲÷2 + 1.4×韧性</code>）再减一次。</li>
<li><b>难度曲线被显著拉陡</b>：裸装承伤最高升到原版的约 3 倍，而满配护甲被压到 1 点下限；玩家输出侧
则因骰点/卡牌而抬高，但附魔、攻击冷却、暴击三项原版加成对骰战<b>完全无效</b>。</li>
</ul></div>""")

    # ---------- 1 方法与证据 ----------
    A("""<h2>1. 方法与证据基础</h2>
<div class="card"><b>结算次序（原版 1.21.1，已从映射源码逐行核对）</b>
<pre class="mono">LivingEntity.actuallyHurt  L1785-1807
  L1787  护甲 + 韧性   CombatRules.getDamageAfterAbsorb
  L1788  抗性提升 → 保护附魔   LivingEntity.getDamageAfterMagicAbsorb
  L1789  LivingDamageEvent.Pre  ← 本模组骰战在此覆盖式写入自算伤害
  L1790  吸收（黄心）
  L1799  setHealth(health − 剩余)</pre>
<b>关键判据</b>：NeoForge <code>DamageContainer.setReduction(type, amount)</code> 会<b>就地扣减</b>
<code>newDamage</code>（<code>this.newDamage -= modifiedReduction</code>），因此
<code>LivingDamageEvent.Pre#getNewDamage()</code> 拿到的是<b>已经过护甲 / 抗性 / 保护三重减免之后</b>的数值。
这一条同时解释了「为什么骰战能旁路原版护甲」与「为什么生物→玩家路径会双重计入」。</div>""")

    A(table(
        ["环节", "原版", "骰战（玩家→生物）", "骰战（生物→玩家）"],
        [["护甲值 / 韧性", "非线性公式，护甲上限 20，下限 护甲×0.2", "仅 <code>护甲÷2</code>（玩家 1.4×韧性 / 生物 1.125×韧性取整）",
          "<b>计入两次</b>：<code>getNewDamage()</code> 加项 + <code>playerDefense</code>"],
         ["保护附魔", "作用于魔法减免阶段（每级 1 点，clamp 20）", "不生效", "经 <code>getNewDamage()</code> 部分泄漏"],
         ["抗性提升", "每级 5%（clamp 20）", "不生效", "经 <code>getNewDamage()</code> 部分泄漏"],
         ["吸收（黄心）", "减免后扣除", "同样在 Pre 之后扣除（不受影响）", "同左"],
         ["武器附魔加伤（锋利）", "<code>getEnchantedDamage</code> 单独一项", "不生效（只读 <code>ATTACK_DAMAGE</code> 属性）", "—（生物武器附魔经 <code>getNewDamage()</code> 泄漏）"],
         ["攻击冷却 / 暴击", "冷却缩放 <code>0.2+cd²×0.8</code>；暴击 ×1.5", "完全不读（连击不衰减）", "—"],
         ["伤害下限", "无（可到 0）", "<b>骰战层保底 1 点</b>（随后扣减免可到 0）", "同左"]],
        cls=""))

    # ---------- 2 输出曲线 ----------
    A(f"""<h2>2. 难度曲线 A：玩家 → 生物（输出）</h2>
<h3>2.1 每击期望伤害 —— 以「僵尸」（护甲 2）为靶</h3>
{legend([("原版（无附魔）", SERIES_COLORS["vanilla"]), ("原版 + 锋利 V", SERIES_COLORS["vanilla_sharp"]),
         ("骰战（仅骰子）", SERIES_COLORS["dice_bare"]), ("骰战（3×特大攻击牌）", SERIES_COLORS["dice_cards"])])}
<div class="chart">{f1}</div>""")

    A(table(["武器", "属性攻击力", "原版", "原版+锋利V", "骰战裸", "骰战 3×特大", "骰战/原版"],
            [[r["weapon"], f'{r["atk_attr"]:.0f}', f'{r["vanilla"]:.2f}', f'{r["vanilla_sharp5"]:.2f}',
              f'{r["dice_bare_mean"]:.2f}', f'{r["dice_epic3_mean"]:.2f}',
              f'{(r["dice_bare_mean"]/r["vanilla"]):.2f}×'] for r in z]))

    A(f"""<h3>2.2 击杀所需击数（TTK，越低越快）</h3>
{legend([("原版（钻石剑）", SERIES_COLORS["vanilla"]), ("骰战（仅骰子）", SERIES_COLORS["dice_bare"]),
         ("骰战（3×特大攻击牌）", SERIES_COLORS["dice_cards"])])}
<div class="chart">{f2}</div>""")

    A(table(["生物", "生命", "护甲", "原版每击", "原版 TTK", "骰战裸每击", "骰战裸 TTK", "骰战 3×特大每击", "骰战卡 TTK"],
            [[r["mob"], f'{r["hp"]:.0f}', f'{r["mob_armor"]:.0f}', f'{r["vanilla"]:.2f}', f'{r["vanilla_ttk"]}',
              f'{r["dice_bare_mean"]:.2f}', f'{r["dice_bare_ttk"]:.1f}',
              f'{r["dice_epic3_mean"]:.2f}', f'{r["dice_epic3_ttk"]:.1f}']
             for r in exp1 if r["weapon"] == "钻石剑"]))

    A("""<div class="card note"><b>读法</b>：<code>骰战 3×特大</code> 一列把「骰神赐福 + 卡牌」的完整形态算进来，
对应满配角色的实战输出；<code>骰战裸</code> 是只装备骰子、空卡牌栏的地板值。两者的巨大差距
（僵尸 4.1 → 25.4 每击）说明新体系的输出几乎全部由骰点与卡牌驱动，而武器等级只贡献起点项。</div>""")

    # ---------- 3 承伤曲线 ----------
    A(f"""<h2>3. 难度曲线 B：生物 → 玩家（承伤）</h2>
<p class="sub">生命 20、不建模回血与无敌帧；「骰战（装备骰子）」= 对骰成立（双方各掷 1d6）。</p>
{legend([("原版", SERIES_COLORS["vanilla"]), ("骰战（未装备骰子）", SERIES_COLORS["dice_nodice"]),
         ("骰战（装备骰子）", SERIES_COLORS["dice_dice"]), ("骰战（装备骰子 + 3×特级防御牌）", SERIES_COLORS["dice_dcards"])])}
<div class="chart">{f3[0]}</div>
<div class="chart">{f3[1]}</div>
<div class="chart">{f3[2]}</div>""")

    A(table(["护甲档", "护甲/韧性", "生物", "原版每击", "骰战·无骰", "骰战·有骰", "骰战·+3防御牌",
             "原版可承击", "骰战可承击"],
            [[r["armor_set"], f'{r["player_armor"]:.0f}/{r["player_tough"]:.0f}', r["mob"],
              f'{r["vanilla"]:.2f}', f'{r["dice_nodice_mean"]:.2f}', f'{r["dice_dice_mean"]:.2f}',
              f'{r["dice_dice_cards_mean"]:.2f}',
              ("∞" if r["vanilla_hits"] > 900 else f'{r["vanilla_hits"]:.1f}'),
              f'{r["dice_dice_hits"]:.1f}']
             for r in exp2 if r["mob"] in ("僵尸", "末影人", "监守者")]))

    A("""<div class="card warn"><b>曲线形状变了</b>：原版承伤随护甲单调平滑下降；骰战下
<b>裸装显著更痛</b>（僵尸 3.0 → 8.9）、<b>满配被压到 1 点下限</b>（僵尸 / 末影人对钻石全套恒为 1.00）。
原因是生物攻击点 = <code>初始攻击力 + 属性值 + getNewDamage()</code>：护甲为 0 时 <code>getNewDamage()</code>
不折扣，三项直接相加（3+3+5=11）再减去仅 2 点的 <code>playerDefense</code>。护甲越高，这一加项被压得越低，
同时 <code>playerDefense</code> 又线性增长 ⇒ 两条同向叠加，曲线因此比原版陡得多。
「3×特级防御牌」一列显示：只要带满防御牌（期望 +21.5 防御点），几乎所有生物攻击都被压到 1 点。</div>""")

    # ---------- 4 修饰器矩阵 ----------
    def in_row(prefix: str) -> Dict:
        for r in in_rows:
            if r["modifier"].startswith(prefix):
                return r
        raise KeyError(prefix)

    r_prot, r_res = in_row("保护 IV"), in_row("抗性提升")
    hi_def = [r for r in in_rows if r["modifier"].startswith("护甲 +5")][0]
    hi_tgh = [r for r in in_rows if r["modifier"].startswith("盔甲韧性")][0]

    A(f"""<h2>4. 原版伤害修饰器对骰战的传导矩阵</h2>
<p class="sub">承伤取<b>两组基准</b>，缺一不可：<b>强基准</b>「监守者（基础攻击 30）→ 钻石全套（甲 20 / 韧 8）」——
修饰器效应可充分展开，<b>不触底</b>；<b>弱基准</b>「僵尸（基础攻击 3）→ 铁全套（甲 15 / 韧 0）」——
紧贴骰战的 1 点下限。若只报单组基准，骰战侧会因为「结果被 1 点下限吃掉」而让几乎所有修饰器的
Δ 恒等于 0%，无法区分「机制不生效」与「生效但被下限掩蔽」这两类完全不同的结论。</p>
<h3>4.1 承伤方向 · 强基准（监守者 → 钻石全套，不触底）</h3>
{legend([("原版 Δ%", SERIES_COLORS["vanilla"]), ("骰战 Δ%", SERIES_COLORS["dice_dice"]),
         ("原版 Δ%（弱基准，对照）", SERIES_COLORS["vanilla_sharp"]), ("骰战 Δ%（弱基准，对照）", SERIES_COLORS["dice_bare"])])}
<div class="chart">{f4}</div>""")
    A(table(["修饰器", "原版", "骰战", "原版 Δ%", "骰战 Δ%", "说明"],
            [[r["modifier"], f'{r["strong_vanilla"]:.3f}', f'{r["strong_dice"]:.3f}',
              f'{r["strong_vanilla_delta_pct"]:+.1f}%', f'{r["strong_dice_delta_pct"]:+.1f}%', r["note"]]
             for r in in_rows]))

    A("""<h3>4.2 承伤方向 · 弱基准（僵尸 → 铁全套，贴近下限）</h3>""")
    A(table(["修饰器", "原版", "骰战", "原版 Δ%", "骰战 Δ%", "说明"],
            [[r["modifier"], f'{r["weak_vanilla"]:.3f}', f'{r["weak_dice"]:.3f}',
              f'{r["weak_vanilla_delta_pct"]:+.1f}%', f'{r["weak_dice_delta_pct"]:+.1f}%', r["note"]]
             for r in in_rows]))

    A(f"""<h3>4.3 输出方向（钻石剑玩家 → 僵尸）</h3>
{legend([("原版 Δ%", SERIES_COLORS["vanilla"]), ("骰战 Δ%", SERIES_COLORS["dice_dice"])])}
<div class="chart">{f5}</div>""")
    A(table(["修饰器", "原版", "骰战", "原版 Δ%", "骰战 Δ%", "说明"],
            [[r["modifier"], f'{r["vanilla"]:.3f}', f'{r["dice"]:.3f}',
              f'{r["vanilla_delta_pct"]:+.1f}%', f'{r["dice_delta_pct"]:+.1f}%', r["note"]]
             for r in out_rows]))

    A(f"""<div class="card warn"><b>「是否在预期」的判定</b>
<ul>
<li><b>在预期</b>：护甲值 / 盔甲韧性的<b>属性终值</b>进入骰战（<code>getArmorValue()</code> /
<code>getAttributeValue(ARMOR_TOUGHNESS)</code>）—— 这正是「第三方模组先改护甲、本模组只读数」的兼容性设计。
强基准下护甲 +5 让骰战承伤 Δ = <b>{hi_def['strong_dice_delta_pct']:+.1f}%</b>、韧性 +4 让
Δ = <b>{hi_tgh['strong_dice_delta_pct']:+.1f}%</b>（原版分别为
{hi_def['strong_vanilla_delta_pct']:+.1f}% / {hi_tgh['strong_vanilla_delta_pct']:+.1f}%），
两条通道都在动且方向正确。吸收（黄心）在两条路径上都在伤害落地后照常扣除。
护甲 20 上限的移除也确实生效（原版公式内部仍 clamp 到 20，骰战不再 clamp）。</li>
<li><b>不在预期（需裁决）</b>：
  <ul>
  <li><b>保护附魔 / 抗性提升对「玩家攻击生物」完全失效</b>（Δ = 0.0%）：它们只在原版管线产生作用，
      而这份值被骰战整段覆盖。生物因此无法用附魔 / 药水提升抗打能力。</li>
  <li><b>同一对效果在「生物攻击玩家」时却部分生效</b>：强基准下
      保护 IV → {r_prot['strong_dice_delta_pct']:+.1f}%、抗性提升 II → {r_res['strong_dice_delta_pct']:+.1f}%
      （原版 {r_prot['strong_vanilla_delta_pct']:+.1f}% / {r_res['strong_vanilla_delta_pct']:+.1f}%）；
      弱基准下 {r_prot['weak_dice_delta_pct']:+.1f}% / {r_res['weak_dice_delta_pct']:+.1f}%。
      生效的原因是该路径把 <code>getNewDamage()</code> 当<b>加项</b>——
      同一机制在两条路径上一生效一失效，属结构性不对称。</li>
  <li><b>锋利 V / 攻击冷却 / 暴击对玩家输出 Δ = 0.0%</b>：骰战只读 <code>ATTACK_DAMAGE</code> 属性值，
      不含 <code>getEnchantedDamage</code> 的附魔加伤，也不读 <code>getAttackStrengthScale</code>、不读暴击。
      ⇒ 连击（无蓄力）在骰战下与蓄满力等价，附魔武器在骰战中失去约 25–40% 的潜在输出。</li>
  </ul></li>
</ul></div>""")

    # ---------- 5 七咒 ----------
    k = exp4["consts"]
    A(f"""<h2>5. 《神秘遗物+》七咒之戒</h2>
<div class="card"><b>已确证的机制（jar 反汇编 + 实例配置）</b>
<pre class="mono">CursedRing#getArmorModifiers(LivingEntity)
    double d = -0.01 × CursedRing.armorDebuff.getAsInt();     // 实例配置 armorDebuff = 30 → d = -0.30
    if (hasCurio(entity, EnigmaticItems.DIMNESS_CHARM)) d *= 0.6;   // 再佩戴「晦暗护符」→ -0.18
    put(Attributes.ARMOR,           new AttributeModifier(id, d, ADD_MULTIPLIED_TOTAL));
    put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, d, ADD_MULTIPLIED_TOTAL));</pre>
⇒ 第三诅咒把 <b>护甲值与盔甲韧性同时乘以 0.70</b>（属性层面，只在该模组的 Curios 属性接口内生效）。
配置来源：<code>enigmaticlegacyplus-server.toml</code> <code>[sevenCurses] armorDebuff = 30</code> /
<code>monsterDamageDebuff = 40</code> / <code>painMultiplier = 200</code>。</div>""")

    A(f"""<h3>5.1 对骰战承伤的实际影响（僵尸 / 监守者 × 各护甲档）</h3>
{legend([("有戒指·原版", SERIES_COLORS["vanilla"]), ("无戒指·原版", SERIES_COLORS["vanilla_sharp"]),
         ("有戒指·骰战", SERIES_COLORS["dice_cards"]), ("无戒指·骰战", SERIES_COLORS["dice_bare"])])}
<div class="chart">{f6}</div>""")

    A(table(["护甲档", "护甲/韧性（前）", "护甲/韧性（后）", "防御力（前）", "防御力（后）", "生物",
             "原版 Δ%", "骰战 Δ%"],
            [[r["armor_set"], f'{r["armor_before"]:.0f}/{r["tough_before"]:.0f}',
              f'{r["armor_after"]:.1f}/{r["tough_after"]:.1f}',
              f'{r["player_def_before"]:.2f}', f'{r["player_def_after"]:.2f}', r["mob"],
              ("—" if math.isnan(r["vanilla_delta_pct"]) else f'{r["vanilla_delta_pct"]:+.1f}%'),
              ("—" if math.isnan(r["dice_delta_pct"]) else f'{r["dice_delta_pct"]:+.1f}%')]
             for r in curse_rows if r["mob"] in ("僵尸", "监守者")]))

    cr_z = [r for r in curse_rows if r["mob"] == "僵尸"]
    # 裸装是退化档（护甲 0 乘 0.7 仍为 0，戒指在数学上无可降）⇒ 单独列出，不混入「轻甲档」
    light = [r for r in cr_z if r["armor_set"] in ("皮革全套", "锁链全套", "铁全套")]
    heavy = [r for r in cr_z if r["armor_set"] in ("钻石全套", "下界合金全套")]
    degen = [r for r in cr_z if r["armor_set"] == "裸装"]
    light_txt = "；".join(f'{r["armor_set"]} 原版 {r["vanilla_delta_pct"]:+.1f}% / 骰战 {r["dice_delta_pct"]:+.1f}%'
                          for r in light)
    heavy_txt = "；".join(f'{r["armor_set"]} 原版 {r["vanilla_delta_pct"]:+.1f}% / 骰战 {r["dice_delta_pct"]:+.1f}%'
                          for r in heavy)
    degen_pct = degen[0]["dice_delta_pct"] if degen else 0.0
    hv = max(heavy, key=lambda x: x["vanilla_delta_pct"]) if heavy else {"vanilla_delta_pct": 0.0}
    A(f"""<div class="card note"><b>读这张表必须先看下限</b>。七咒之戒降护甲后，骰战承伤是
<b>「轻甲档放大、重甲档被 1 点下限吃掉」</b>的两段式结构 —— 这不是「重甲档不受影响」，而是
<b>下限掩蔽（floor masking）导致的读数失真</b>，不能据此认为戒指对重甲无效。
<ul>
<li><b>退化档（无可降）</b>：裸装 骰战 Δ = {degen_pct:+.1f}% —— 护甲为 0 时 <code>0 × 0.70 = 0</code>，
戒指在数学上无可影响对象；该行仅作基准锚点，不代表「戒指无效」。</li>
<li><b>轻甲档（可观测）</b>：{light_txt}。
轻甲档骰战 Δ 远大于原版，原因是骰战生物攻击点 = <code>基础攻击力 + 属性值 + getNewDamage()</code>，
护甲下降会让 <code>getNewDamage()</code> 这一加项<b>同时变大</b>，而 <code>playerDefense</code>（含 <code>护甲÷2</code>）
又同时变小 ⇒ 两个方向同向叠加，放大率因此高于原版单通道的 <code>CombatRules</code>。</li>
<li><b>重甲档（读数失真，已标注）</b>：{heavy_txt}。
钻石 / 下界合金档在无戒指时，僵尸的骰战承伤就已经落到 1 点下限；戒指进一步降甲后
骰战侧仍为 1.00（Δ = 0.0%），而原版侧因不触底仍显示
{hv["vanilla_delta_pct"]:+.1f}% 量级的上升。⇒ <b>重甲档的「骰战 Δ = 0%」是下限伪影，不可作为结论引用。</b>
实机中重甲 + 戒指的真实差异要等更高攻击力的生物（见监守者行）才会显现。</li>
</ul>
判定口径：<b>第三诅咒对骰战确实生效</b>（纯属性传导，机制层已由强基准表与监守者行确证），
但<b>其可观测幅度依赖护甲档位</b>；引用数值时必须连同护甲档一起给出。</div>""")

    A("""<div class="card"><b>已移除的「旧版七咒影响模型」= 代码中不存在对护甲路径的七咒建模</b>。
全仓已检索：模组只做两件事 ——
① <code>applyCurseToDicePoints</code>：对<b>骰点 + 卡牌点数</b>乘 <code>(1 − monsterDamageDebuff)</code>（第四诅咒）；
② <code>EXTERNAL_DAMAGE_FACTORS</code> 里搬运第一诅咒的受伤倍率。
<b>没有任何</b>按护甲值 / 护甲百分比的二次换算。因此第三诅咒（护甲 −30%）现在是「纯属性传导」：
戒指改属性 → 骰战读 <code>getArmorValue()</code> → 自动生效，与本轮「最大模组兼容性」设计一致。</div>""")

    A(f"""<h3>5.2 对输出的影响（第四诅咒 −40% 骰点与卡牌）</h3>
<p class="sub">注意：该诅咒<b>仍然存在于代码中</b>（<code>DiceCombatEvents.applyCurseToDicePoints</code>），
并非已移除项。下表给出它的量级，供与「纯护甲传导」区分。</p>""")
    A(table(["武器", "卡牌栏", "无戒指", "有戒指", "Δ%"],
            [[r["weapon"], r["cards"], f'{r["dice_before"]:.2f}', f'{r["dice_after"]:.2f}', f'{r["delta_pct"]:+.1f}%']
             for r in exp4["output_rows"]]))

    A("""<h3>5.3 第一诅咒（受伤 ×2）在两条路径上的传导差异</h3>""")
    A(table(["护甲档", "生物", "原版 无/有戒指", "原版倍率", "骰战 无/有戒指", "骰战倍率"],
            [[r["armor_set"], r["mob"],
              f'{r["vanilla_before"]:.2f} / {r["vanilla_after"]:.2f}',
              ("∞" if math.isinf(r["vanilla_ratio"]) else f'{r["vanilla_ratio"]:.2f}×'),
              f'{r["dice_before"]:.2f} / {r["dice_after"]:.2f}',
              f'{r["dice_ratio"]:.2f}×']
             for r in exp4["pain_rows"]]))
    A("""<div class="card note">原版把「受伤 ×2」作用于<b>原始伤害</b>，最终伤害近似翻倍；
骰战（生物→玩家路径）中它只通过 <code>getNewDamage()</code> 进入<b>加项</b>，
翻倍的是「一个已经被护甲削过、且只占攻击点一部分的项」⇒ 实际倍率被显著压缩（裸装仍接近 2×，重甲远低于 2×）。
※ 玩家攻击「佩戴戒指的玩家」时走的是另一条通道（<code>EXTERNAL_DAMAGE_FACTORS</code> 直接乘最终伤害），不受此压缩影响。</div>""")

    # ---------- 6 复算 ----------
    A(f"""<h2>6. 复算与复现</h2>
<div class="card">
<ul>
<li>仿真器：<code>tools/balance_sim/dice_combat_sim.py</code>（仅标准库）</li>
<li>数据：<code>docs/balance/dice-combat-simulation.json</code></li>
<li>复现：<code class="mono">python tools/balance_sim/dice_combat_sim.py --out docs/balance/dice-combat-simulation.json --n 40000 --ttk 2000 --seed 20260926</code></li>
<li>渲染：<code class="mono">python tools/balance_sim/render_report.py</code></li>
</ul>
<b>卡牌点数校验</b>（解析均值 vs 蒙特卡洛）：""")
    fp = js["card_fingerprint"]["cards"]
    A(table(["卡牌", "类型", "参数", "解析均值", "MC 均值", "区间"],
            [[k2, v["type"], v["param"], f'{v["analytic_mean"]:.4f}', f'{v["mc_mean"]:.4f}',
              f'{v["min"]}–{v["max"]}'] for k2, v in fp.items()]))
    A("""</div>
<footer>本报告由 <code>tools/balance_sim/</code> 生成；所有公式均标注了源码位置，数值可用同一脚本独立复算。
第三方模组结论来自 jar 反汇编与实例配置，仓库内未保留任何第三方反编译产物。</footer>
</div></body></html>""")

    html = "".join(H)

    # ================================================================ Markdown
    M: List[str] = []
    B = M.append
    B("# 骰战 vs 原版战斗 · 数值仿真报告（1.21.1）\n")
    B(f"> 蒙特卡洛 {meta['n_samples']:,} 采样/单元 · TTK {meta['ttk_trials']:,} 次 · 种子 {meta['seed']}")
    B("> 仿真器 `tools/balance_sim/dice_combat_sim.py` · 数据 `docs/balance/dice-combat-simulation.json`\n")

    B("## 0. 结论摘要\n")
    B(f"- 玩家输出（钻石剑，四类生物均值）：骰战裸 / 原版 = **{avg_ratio_out:.2f}×**")
    B(f"- 承伤（裸装，三类生物均值）：骰战 / 原版 = **{naked_ratio:.2f}×**")
    B(f"- 承伤（钻石全套）：骰战 / 原版 = **{full_ratio:.2f}×**")
    B("- 骰战完全旁路原版护甲减免公式（含 20 上限 / `护甲×0.2` 下限）；护甲只经 `护甲÷2` 参与一次")
    B("- 生物→玩家路径：护甲**计入两次**，且保护附魔 / 抗性提升经 `getNewDamage()` 泄漏进攻击项")
    B("- 锋利附魔 / 攻击冷却 / 暴击对骰战输出 **Δ = 0%**")
    B("- 七咒之戒（第三诅咒 护甲/韧性 ×0.70）对骰战**生效**，但可观测幅度**依赖护甲档**：")
    B("  轻甲档放大率高于原版、重甲档被骰战 1 点下限掩盖（详见 5.1 的下限说明）")
    B("- 承伤矩阵取强 / 弱两组基准：强基准保证修饰器效应可展开，弱基准暴露 1 点下限的掩蔽效应\n")

    B("## 1. 结算次序与证据\n")
    B("```")
    B("LivingEntity.actuallyHurt  L1785-1807")
    B("  L1787  护甲 + 韧性   CombatRules.getDamageAfterAbsorb")
    B("  L1788  抗性提升 → 保护附魔   getDamageAfterMagicAbsorb")
    B("  L1789  LivingDamageEvent.Pre  ← 骰战在此覆盖式写入自算伤害")
    B("  L1790  吸收（黄心）")
    B("  L1799  setHealth(health − 剩余)")
    B("```")
    B("NeoForge `DamageContainer.setReduction(type, amount)` 就地扣减 `newDamage` ⇒ `Pre#getNewDamage()`")
    B("已是「护甲 / 抗性 / 保护」三重减免之后的值。\n")
    B("| 环节 | 原版 | 骰战（玩家→生物） | 骰战（生物→玩家） |")
    B("|---|---|---|---|")
    B("| 护甲值 / 韧性 | 非线性，上限 20，下限 护甲×0.2 | 仅 `护甲÷2`（玩家 1.4×韧性 / 生物 1.125×韧性取整） | **计入两次** |")
    B("| 保护附魔 | 魔法减免阶段 | 不生效 | 部分泄漏 |")
    B("| 抗性提升 | 每级 5% | 不生效 | 部分泄漏 |")
    B("| 吸收（黄心） | 减免后扣 | 同左（不受影响） | 同左 |")
    B("| 锋利附魔加伤 | 单独一项 | 不生效 | —（生物武器附魔泄漏） |")
    B("| 攻击冷却 / 暴击 | `0.2+cd²×0.8` / ×1.5 | 完全不读 | — |")
    B("| 伤害下限 | 无 | 骰战层保底 1 点 | 同左 |\n")

    B("## 2. 难度曲线 A：玩家 → 生物（输出）\n")
    B("### 2.1 僵尸（护甲 2）每击期望伤害\n")
    B("| 武器 | 属性攻击力 | 原版 | 原版+锋利V | 骰战裸 | 骰战 3×特大 | 骰战/原版 |")
    B("|---|---|---|---|---|---|---|")
    for r in z:
        B(f'| {r["weapon"]} | {r["atk_attr"]:.0f} | {r["vanilla"]:.2f} | {r["vanilla_sharp5"]:.2f} | '
          f'{r["dice_bare_mean"]:.2f} | {r["dice_epic3_mean"]:.2f} | {r["dice_bare_mean"]/r["vanilla"]:.2f}× |')
    B("\n### 2.2 击杀所需击数（钻石剑）\n")
    B("| 生物 | 生命 | 护甲 | 原版每击 | 原版 TTK | 骰战裸每击 | 骰战裸 TTK | 骰战 3×特大每击 | 骰战卡 TTK |")
    B("|---|---|---|---|---|---|---|---|---|")
    for r in exp1:
        if r["weapon"] == "钻石剑":
            B(f'| {r["mob"]} | {r["hp"]:.0f} | {r["mob_armor"]:.0f} | {r["vanilla"]:.2f} | {r["vanilla_ttk"]} | '
              f'{r["dice_bare_mean"]:.2f} | {r["dice_bare_ttk"]:.1f} | {r["dice_epic3_mean"]:.2f} | {r["dice_epic3_ttk"]:.1f} |')

    B("\n## 3. 难度曲线 B：生物 → 玩家（承伤，生命 20）\n")
    B("| 护甲档 | 护甲/韧性 | 生物 | 原版 | 骰战·无骰 | 骰战·有骰 | 骰战·+3防御牌 | 原版可承击 | 骰战可承击 |")
    B("|---|---|---|---|---|---|---|---|---|")
    for r in exp2:
        if r["mob"] in ("僵尸", "末影人", "监守者"):
            B(f'| {r["armor_set"]} | {r["player_armor"]:.0f}/{r["player_tough"]:.0f} | {r["mob"]} | '
              f'{r["vanilla"]:.2f} | {r["dice_nodice_mean"]:.2f} | {r["dice_dice_mean"]:.2f} | '
              f'{r["dice_dice_cards_mean"]:.2f} | '
              + ("∞" if r["vanilla_hits"] > 900 else f'{r["vanilla_hits"]:.1f}') +
              f' | {r["dice_dice_hits"]:.1f} |')

    B("\n## 4. 原版伤害修饰器传导矩阵\n")
    B("承伤取**两组基准**（缺一不可）：**强基准**「监守者（基础攻击 30）→ 钻石全套（甲 20 / 韧 8）」不触底；")
    B("**弱基准**「僵尸（基础攻击 3）→ 铁全套（甲 15 / 韧 0）」贴近骰战的 1 点下限。")
    B("只报单组基准时，骰战侧会因「结果被 1 点下限吃掉」而让几乎所有修饰器的 Δ 恒为 0%，")
    B("无法区分「机制不生效」与「生效但被下限掩蔽」。\n")
    B("### 4.1 承伤方向 · 强基准（监守者 → 钻石全套，不触底）\n")
    B("| 修饰器 | 原版 | 骰战 | 原版 Δ% | 骰战 Δ% | 说明 |")
    B("|---|---|---|---|---|---|")
    for r in in_rows:
        B(f'| {r["modifier"]} | {r["strong_vanilla"]:.3f} | {r["strong_dice"]:.3f} | '
          f'{r["strong_vanilla_delta_pct"]:+.1f}% | {r["strong_dice_delta_pct"]:+.1f}% | {r["note"]} |')
    B("\n### 4.2 承伤方向 · 弱基准（僵尸 → 铁全套，贴近下限）\n")
    B("| 修饰器 | 原版 | 骰战 | 原版 Δ% | 骰战 Δ% | 说明 |")
    B("|---|---|---|---|---|---|")
    for r in in_rows:
        B(f'| {r["modifier"]} | {r["weak_vanilla"]:.3f} | {r["weak_dice"]:.3f} | '
          f'{r["weak_vanilla_delta_pct"]:+.1f}% | {r["weak_dice_delta_pct"]:+.1f}% | {r["note"]} |')
    B("\n**判定**：护甲值 / 韧性的**属性终值**进入骰战（`getArmorValue()` / `getAttributeValue(ARMOR_TOUGHNESS)`）")
    B("⇒ **在预期**（第三方先改护甲、本模组只读；护甲 20 上限确已移除）。")
    B("但 ① 保护附魔 / 抗性提升对「玩家→生物」Δ = 0.0%（骰战覆盖整段）同时")
    B("② 在「生物→玩家」却因 `getNewDamage()` 被当加项而部分生效 ⇒ **结构性不对称**；")
    B("③ 锋利 V / 攻击冷却 / 暴击对玩家输出 Δ = 0.0% ⇒ **不在预期，需裁决**。\n")
    B("### 4.3 输出方向（钻石剑玩家 → 僵尸）\n")
    B("| 修饰器 | 原版 | 骰战 | 原版 Δ% | 骰战 Δ% | 说明 |")
    B("|---|---|---|---|---|---|")
    for r in out_rows:
        B(f'| {r["modifier"]} | {r["vanilla"]:.3f} | {r["dice"]:.3f} | {r["vanilla_delta_pct"]:+.1f}% | '
          f'{r["dice_delta_pct"]:+.1f}% | {r["note"]} |')

    B("\n## 5. 七咒之戒\n")
    B("`CursedRing#getArmorModifiers`：`d = -0.01 × armorDebuff`（实例 = 30 ⇒ −0.30），")
    B("同时挂到 `Attributes.ARMOR` 与 `Attributes.ARMOR_TOUGHNESS`（`ADD_MULTIPLIED_TOTAL`）")
    B("⇒ 第三诅咒 = 护甲与韧性同时 ×0.70。\n")
    B("### 5.1 对骰战承伤的实际影响（僵尸 / 监守者 × 各护甲档）\n")
    B("| 护甲档 | 护甲/韧性（前→后） | 防御力（前→后） | 生物 | 原版 Δ% | 骰战 Δ% |")
    B("|---|---|---|---|---|---|")
    for r in curse_rows:
        if r["mob"] in ("僵尸", "监守者"):
            B(f'| {r["armor_set"]} | {r["armor_before"]:.0f}/{r["tough_before"]:.0f} → '
              f'{r["armor_after"]:.1f}/{r["tough_after"]:.1f} | {r["player_def_before"]:.2f} → '
              f'{r["player_def_after"]:.2f} | {r["mob"]} | '
              + ("—" if math.isnan(r["vanilla_delta_pct"]) else f'{r["vanilla_delta_pct"]:+.1f}%') + " | "
              + ("—" if math.isnan(r["dice_delta_pct"]) else f'{r["dice_delta_pct"]:+.1f}%') + " |")
    B("\n> **必须连护甲档一起读**：七咒之戒降甲后，骰战承伤呈「轻甲档放大、重甲档被 1 点下限吃掉」的两段式结构。")
    B(f"> - **退化档（无可降）**：裸装 骰战 Δ = {degen_pct:+.1f}% —— 护甲为 0 时 `0 × 0.70 = 0`，")
    B(">   戒指在数学上无可影响对象；该行仅作基准锚点，不代表「戒指无效」。")
    B(f"> - **轻甲档（可观测）**：{light_txt}。骰战 Δ 远大于原版 —— 生物攻击点 = 基础攻击力 + 属性值 + `getNewDamage()`，")
    B(">   护甲下降让该加项变大、`playerDefense`（含 `护甲÷2`）变小的**两个方向同向叠加**，故放大率高于原版单通道。")
    B(f"> - **重甲档（读数失真）**：{heavy_txt}。无戒指时僵尸的骰战承伤已落至 1 点下限，")
    B(f">   降甲后仍为 1.00（Δ = 0.0%），原版不触底故仍显示 {hv['vanilla_delta_pct']:+.1f}% 量级上升。")
    B(">   ⇒ **重甲档的「骰战 Δ = 0%」是下限伪影，不可作为结论引用**；真实差异需更高攻击力生物（见监守者行）才显现。")
    B("> - **结论**：第三诅咒对骰战**确实生效**（纯属性传导），但其**可观测幅度依赖护甲档位**，引用时必须附档位。")
    B(">")
    B("> 注：表中前后两次为**配对采样**（同种子同抽点顺序），故退化档 Δ 精确为 0.0%，不含 MC 噪声。")
    B("\n### 5.2 第四诅咒（−40% 骰点与卡牌，仍然存在于代码中）\n")
    B("| 武器 | 卡牌栏 | 无戒指 | 有戒指 | Δ% |")
    B("|---|---|---|---|---|")
    for r in exp4["output_rows"]:
        B(f'| {r["weapon"]} | {r["cards"]} | {r["dice_before"]:.2f} | {r["dice_after"]:.2f} | {r["delta_pct"]:+.1f}% |')
    B("\n### 5.3 第一诅咒（受伤 ×2）的传导差异\n")
    B("| 护甲档 | 生物 | 原版 无/有 | 原版倍率 | 骰战 无/有 | 骰战倍率 |")
    B("|---|---|---|---|---|---|")
    for r in exp4["pain_rows"]:
        B(f'| {r["armor_set"]} | {r["mob"]} | {r["vanilla_before"]:.2f} / {r["vanilla_after"]:.2f} | '
          + ("∞" if math.isinf(r["vanilla_ratio"]) else f'{r["vanilla_ratio"]:.2f}×') + " | "
          f'{r["dice_before"]:.2f} / {r["dice_after"]:.2f} | {r["dice_ratio"]:.2f}× |')

    B("\n## 6. 复现\n")
    B("```bash")
    B("python tools/balance_sim/dice_combat_sim.py --out docs/balance/dice-combat-simulation.json \\")
    B("       --n 40000 --ttk 2000 --seed 20260926")
    B("python tools/balance_sim/render_report.py")
    B("```")
    return html, "\n".join(M) + "\n"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="docs/balance/dice-combat-simulation.json")
    ap.add_argument("--html", default="docs/balance/dice-combat-simulation.html")
    ap.add_argument("--md", default="docs/balance/dice-combat-simulation.md")
    args = ap.parse_args()
    with open(args.data, "r", encoding="utf-8") as f:
        js = json.load(f)
    html, md = build(js)
    for path, content in ((args.html, html), (args.md, md)):
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(content)
        print(f"[render] written {path} ({len(content)} chars)")


if __name__ == "__main__":
    main()
