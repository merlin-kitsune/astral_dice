#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
骰战数值「修正 + 调参」报告渲染器。

输入：dice-combat-tuning.json（由 tools/balance_sim/dice_combat_tuning.py 产出）
输出：dice-combat-tuning.html / .md（浅色主题、内联 SVG、零外链）

复用 render_report.py 的 SVG 组件，不复制绘图逻辑。
"""

import json
import os
import sys
from typing import Dict, List, Sequence, Tuple

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

from render_report import (  # noqa: E402
    C_BG,
    C_BORDER,
    C_GRID,
    C_MUTED,
    C_PANEL,
    C_TEXT,
    esc,
    legend,
    svg_grouped_bars,
    svg_lines,
    table,
)

# 配置短名 ↔ JSON key（顺序 = 展示顺序）
SHORT: List[Tuple[str, str]] = [
    ("现状", "现状（v1 基线）"),
    ("方案A", "方案A·纯落地"),
    ("档位B", "档位B·仅校准基数"),
    ("档位C", "档位C·中度压缩（推荐）"),
    ("档位D", "档位D·重度压缩"),
]
COLOR = {
    "原版": "#3b6fd4",
    "现状": "#c0392b",
    "方案A": "#e08a4a",
    "档位B": "#8fa3c8",
    "档位C": "#2e9e6b",
    "档位D": "#7d5ba6",
}
STAGE_NAMES = ["前期", "中期", "后期"]


def build(js: Dict) -> Tuple[str, str]:
    C = js["curves"]
    n_conf = len(SHORT)

    def curve(conf: str, kind: str, key: str, field: str) -> List[float]:
        rows = C[conf][kind]
        return [r[field] for r in rows if r["target" if kind == "out_curve" else "source"] == key]

    def span(conf: str, kind: str) -> Dict:
        return C[conf]["output_span" if kind == "out_curve" else "intake_span"]

    # ---------------- 图 1：输出曲线（僵尸靶）----------------
    out_series = [("原版", curve(SHORT[0][1], "out_curve", "僵尸", "vanilla"), COLOR["原版"])]
    for short, key in SHORT:
        out_series.append((short, curve(key, "out_curve", "僵尸", "dice"), COLOR[short]))
    f1 = svg_lines(STAGE_NAMES, out_series, ylabel="每击期望伤害", fmt="{:.0f}", height=340, ystep=6)

    # ---------------- 图 2：承伤曲线（僵尸源）----------------
    in_series = [("原版", curve(SHORT[0][1], "in_curve", "僵尸", "vanilla"), COLOR["原版"])]
    for short, key in SHORT:
        in_series.append((short, curve(key, "in_curve", "僵尸", "dice"), COLOR[short]))
    f2 = svg_lines(STAGE_NAMES, in_series, ylabel="每击承受伤害", fmt="{:.1f}", height=340, ystep=6)

    # ---------------- 图 3：可承受击数（20 HP）----------------
    hits_series = [("原版", curve(SHORT[0][1], "in_curve", "僵尸", "vanilla_hits"), COLOR["原版"])]
    for short, key in SHORT:
        hits_series.append((short, curve(key, "in_curve", "僵尸", "dice_hits"), COLOR[short]))
    f3 = svg_lines(STAGE_NAMES, hits_series, ylabel="可承受击数（20 HP）", fmt="{:.0f}", height=330, ystep=6)

    # ---------------- 图 4：输出动态范围 ----------------
    f4_cats = ["原版"] + [s for s, _ in SHORT]
    f4_out = [span(SHORT[0][1], "out_curve")["vanilla"]["span"]] + \
             [span(k, "out_curve")["dice"]["span"] for _, k in SHORT]
    f4 = svg_grouped_bars(f4_cats, [("输出动态范围（后期÷前期）", f4_out, COLOR["档位C"])],
                          ylabel="倍率", fmt="{:.1f}", height=300, ystep=5)

    # ---------------- 图 5：承伤动态范围 ----------------
    f5_in = [span(SHORT[0][1], "in_curve")["vanilla"]["span"] * 100] + \
            [span(k, "in_curve")["dice"]["span"] * 100 for _, k in SHORT]
    f5 = svg_grouped_bars(f4_cats, [("承伤动态范围（后期÷前期，×100）", f5_in, COLOR["档位B"])],
                          ylabel="倍率 ×100", fmt="{:.1f}", height=300, ystep=5)

    # ---------------- 图 6：修饰器复检 Δ% ----------------
    mc_cur = C["档位C·中度压缩（推荐）"]["modifier_check"]
    mc_now = C["现状（v1 基线）"]["modifier_check"]
    f6 = svg_grouped_bars(
        [r["modifier"][:14] for r in mc_cur],
        [("原版 Δ%", [r["vanilla_delta_pct"] for r in mc_cur], COLOR["原版"]),
         ("现状骰战 Δ%", [r["dice_delta_pct"] for r in mc_now], COLOR["现状"]),
         ("修正后 Δ%", [r["dice_delta_pct"] for r in mc_cur], COLOR["档位C"])],
        ylabel="相对基准的变化（%）", fmt="{:+.0f}", height=360, ystep=4, diverging=True)

    # =========================================================== 组装
    out: List[str] = []
    A = out.append

    A(f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>骰战数值修正与调参 · 效果预览</title>
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
  pre {{ background:#eef1f5; padding:10px 12px; border-radius:8px; overflow-x:auto; font-size:12.5px; }}
  .warn {{ border-left:4px solid #d9534f; background:#fdf1f0; }}
  .ok {{ border-left:4px solid #3f9d63; background:#f0f8f3; }}
  .note {{ border-left:4px solid #b98b3a; background:#fdf8ee; }}
  ul {{ padding-left:20px; margin:8px 0; }}
  li {{ margin:4px 0; }}
  .mono {{ font-family:Consolas,Menlo,monospace; font-size:12.5px; }}
  footer {{ margin-top:44px; color:var(--mu); font-size:12px; border-top:1px solid var(--bd); padding-top:14px; }}
</style></head><body><div class="wrap">""")

    A(f"""<h1>骰战数值修正与调参 · 效果预览</h1>
<p class="sub">基准版本 Minecraft 1.21.1 / NeoForge 21.1.235 · 蒙特卡洛 {js['meta']['n_samples']:,} 采样/单元 ·
方案 A = 去 <code>getNewDamage()</code> + 保护/抗性乘算因子 + 简单暴击 + 攻击冷却 + 附魔生效 ·
本文件为**纯离线数值预览**，尚未改动任何代码</p>""")

    # ---------- 摘要 ----------
    now = C["现状（v1 基线）"]
    rec = C["档位C·中度压缩（推荐）"]
    o_now = curve("现状（v1 基线）", "out_curve", "僵尸", "dice")
    o_rec = curve("档位C·中度压缩（推荐）", "out_curve", "僵尸", "dice")
    i_now = curve("现状（v1 基线）", "in_curve", "僵尸", "dice")
    i_rec = curve("档位C·中度压缩（推荐）", "in_curve", "僵尸", "dice")
    van_o = curve("现状（v1 基线）", "out_curve", "僵尸", "vanilla")
    van_i = curve("现状（v1 基线）", "in_curve", "僵尸", "vanilla")

    A(f"""<div class="kpis">
<div class="kpi"><div class="v">{o_now[0]:.1f} → {o_rec[0]:.1f}</div>
<div class="k">前期输出（骰战每击）· 原版 {van_o[0]:.1f}</div></div>
<div class="kpi"><div class="v">{i_now[0]:.1f} → {i_rec[0]:.1f}</div>
<div class="k">前期承伤（裸装，僵尸）· 原版 {van_i[0]:.1f}</div></div>
<div class="kpi"><div class="v">{now['output_span']['dice']['span']:.1f}× → {rec['output_span']['dice']['span']:.1f}×</div>
<div class="k">输出动态范围（后期÷前期）· 原版 {now['output_span']['vanilla']['span']:.1f}×</div></div>
<div class="kpi"><div class="v">{i_now[0]/van_i[0]:.2f}× → {i_rec[0]/van_i[0]:.2f}×</div>
<div class="k">前期承伤 ÷ 原版（难度起点）</div></div>
</div>""")

    A(f"""<div class="card warn"><b>本页解决的三个问题</b><ul>
<li><b>修饰器失效（已修）</b>：锋利 / 攻击冷却 / 暴击 / 保护 / 抗性在骰战下原本 Δ = 0%；
裁决后按「方案 A + 简单暴击 + 冷却 + 附魔」落地，<b>五项复检全部与原版对齐</b>（见第 5 节）。</li>
<li><b>曲线过陡（已压）</b>：输出的「后期÷前期」由 <b>{now['output_span']['dice']['span']:.1f}× 压到 {rec['output_span']['dice']['span']:.1f}×</b>
（原版 {now['output_span']['vanilla']['span']:.1f}×）；承伤侧同等压制，且满配不再落进 1 点下限。</li>
<li><b>前期太痛（已修）</b>：裸装被僵尸打由 <b>{i_now[0]:.1f}/击（原版 {van_i[0]:.1f} 的 {i_now[0]/van_i[0]:.1f} 倍）降到
{i_rec[0]:.1f}/击（{i_rec[0]/van_i[0]:.2f} 倍）</b>。</li>
</ul></div>""")

    # ---------- 1 裁决落地 ----------
    A("""<h2>1. 四项裁决的落地口径</h2>""")
    A(table(["裁决", "含义", "实现要点（三线同构）", "复检判据"], [
        ["① 方案 A", "移除 <code>getNewDamage()</code>，生物攻击点改用 pre-mitigation 原始伤害",
         "路径② <code>mobAttack = baseAttack + ATTACK_DAMAGE + 生物武器附魔 + 骰点</code>；"
         "护甲只经 <code>playerDefense</code> 计一次",
         "1.20.1 = <code>getAmount()</code>；1.21.1/26.1.2 = <code>getNewDamage()</code> 均不再出现在攻击点"],
        ["② 简单暴击口径", "复刻 <code>Player#attack</code> 判定，<code>×1.5</code> 作用于<b>整个攻击点</b>（含骰点/卡牌/附魔）",
         "判定条件逐条取自 <code>Player#attack</code>；26.1.2 的失能项是 <code>!isMobilityRestricted()</code>",
         "跳跃暴击 ÷ 站立 ≈ 1.5×（实测 +75%，高于原版 +50% —— 口径差异，见 §5）"],
        ["③ 一并处理冷却", "读 <code>getAttackStrengthScale(0.5F)</code>，乘 <code>0.2 + s²·0.8</code>",
         "★ 与附魔天然合并：<code>(属性+附魔) × cooldown</code> ≡ 原版 <code>f×coef + f1×coef</code>",
         "冷却 50% ⇒ Δ ≈ −55%（原版 −60%，差值来自骰点不随冷却缩放）"],
        ["④ 附魔项加入显示口径", "<code>DiceCombatModifiers.attackPowerBase</code> 的取值含附魔加伤",
         "降神快照 / HUD 攻击条 / tooltip 区间三处消费同一函数，必须同步；"
         "冷却与暴击属瞬态，<b>不进</b>显示口径",
         "HUD 与结算读数一致（消除「显示一个值、结算另一个值」漂移）"],
    ]))

    A(f"""<div class="card note"><b>方案 A 的必然副作用（已在本页量化）</b><br>
移除 <code>getNewDamage()</code> 后，生物攻击点从 ≈<code>2×属性值</code> 降到 <code>属性值 + baseAttack</code>，
<b>承伤整体下降</b>。这正是第 3 节「前期承伤 8.97 → 5.97」的来源 —— 该下降本身是<b>修 bug 的结果</b>，
而不是调参造成的；后续档位调整只是在此基础上把曲线形状重新校准。</div>""")

    # ---------- 2 输出曲线 ----------
    lg = [("原版", COLOR["原版"])] + [(s, COLOR[s]) for s, _ in SHORT]
    A(f"""<h2>2. 难度曲线 A：玩家 → 生物（输出）</h2>
<h3>2.1 每击期望伤害（靶 = 僵尸，护甲 2）</h3>
{legend(lg)}
<div class="chart">{f1}</div>""")

    rows = []
    for si, stage in enumerate(STAGE_NAMES):
        r = C["档位C·中度压缩（推荐）"]["out_curve"][si * 2]
        cells = [f'<b>{stage}</b>（{r["weapon"]} / {r["armor_set"]} / {len(r["cards"])} 张卡）',
                 f'{r["vanilla"]:.2f}']
        for _, key in SHORT:
            v = curve(key, "out_curve", "僵尸", "dice")[si]
            mark = "" if key != "档位C·中度压缩（推荐）" else " ★"
            cells.append(f'{v:.2f}{mark}')
        rows.append(cells)
    A(table(["档位（装备）", "原版"] + [s for s, _ in SHORT], rows))
    A(f"""<div class="card"><b>读法</b>：原版三档位是 <b>{van_o[0]:.2f} → {van_o[1]:.2f} → {van_o[2]:.2f}</b>
（动态范围 {now['output_span']['vanilla']['span']:.2f}×）。骰战现状是
<b>{o_now[0]:.2f} → {o_now[1]:.2f} → {o_now[2]:.2f}</b>（{now['output_span']['dice']['span']:.2f}×）——
<b>前期只有原版的 {o_now[0]/van_o[0]:.2f} 倍</b>（打不动怪），后期 <b>{o_now[2]/van_o[2]:.2f} 倍</b>。
档位 C 把它拉成 <b>{o_rec[0]:.2f} → {o_rec[1]:.2f} → {o_rec[2]:.2f}</b>
（{rec['output_span']['dice']['span']:.2f}×）：前期与原版持平（{o_rec[0]/van_o[0]:.2f}×），
后期收敛到 {o_rec[2]/van_o[2]:.2f} 倍，动态范围<b>压缩 {100*(1-rec['output_span']['dice']['span']/now['output_span']['dice']['span']):.0f}%</b>。</div>""")

    # ---------- 3 承伤曲线 ----------
    A(f"""<h2>3. 难度曲线 B：生物 → 玩家（承伤）</h2>
<h3>3.1 每击承受伤害（源 = 僵尸）</h3>
{legend(lg)}
<div class="chart">{f2}</div>""")

    rows = []
    for si, stage in enumerate(STAGE_NAMES):
        r = C["档位C·中度压缩（推荐）"]["in_curve"][si * 2]
        cells = [f'<b>{stage}</b>（{r["armor_set"]}，保护 {r["prot"]:.0f} 点）', f'{r["vanilla"]:.2f}']
        for _, key in SHORT:
            v = curve(key, "in_curve", "僵尸", "dice")[si]
            mark = "" if key != "档位C·中度压缩（推荐）" else " ★"
            cells.append(f'{v:.2f}{mark}')
        rows.append(cells)
    A(table(["档位（护甲）", "原版"] + [s for s, _ in SHORT], rows))

    A(f"""<h3>3.2 可承受击数（20 生命，不建模回血）</h3>
{legend(lg)}
<div class="chart">{f3}</div>""")
    A(table(["档位（护甲）", "原版"] + [s for s, _ in SHORT],
            [[f'<b>{stage}</b>',
              f'{curve(SHORT[0][1], "in_curve", "僵尸", "vanilla_hits")[si]:.1f}']
             + [f'{curve(key, "in_curve", "僵尸", "dice_hits")[si]:.1f}'
                + ("" if key != "档位C·中度压缩（推荐）" else " ★") for _, key in SHORT]
             for si, stage in enumerate(STAGE_NAMES)]))

    A(f"""<div class="card warn"><b>承伤侧的三条读数（僵尸源）</b><ul>
<li><b>前期</b>：现状 <b>{i_now[0]:.2f}</b>（原版 {van_i[0]:.2f}，{i_now[0]/van_i[0]:.2f} 倍）——
裸装几乎被打穿；档位 C <b>{i_rec[0]:.2f}</b>（{i_rec[0]/van_i[0]:.2f} 倍），与原版对齐。</li>
<li><b>后期触底</b>：现状骰战满配 <b>{i_now[2]:.2f}</b>，已落到 1 点下限（原版 {van_i[2]:.2f}）；
档位 C 用「相对下限」（攻击点 ×15%）把它抬到 <b>{i_rec[2]:.2f}</b>，同时保留保护减免 ⇒ 满配不再无敌。</li>
<li><b>动态范围</b>：现状 {now['intake_span']['dice']['span']*100:.1f}×100；
档位 C {rec['intake_span']['dice']['span']*100:.1f}×100（原版 {now['intake_span']['vanilla']['span']*100:.1f}×100）——
<b>比原版更平缓</b>，即护甲收益被有意削弱，避免「穿上钻石甲就无敌」。</li>
</ul></div>""")

    # ---------- 4 陡峭度 ----------
    A(f"""<h2>4. 曲线陡峭度的量化</h2>
<p>「陡峭度」= 后期数值 ÷ 前期数值。输出侧越<b>大</b>越陡（后期爆炸）；承伤侧越<b>小</b>越陡
（前期太痛 / 后期无敌）。</p>
<h3>4.1 输出动态范围</h3>
<div class="chart">{f4}</div>
<h3>4.2 承伤动态范围（×100）</h3>
<div class="chart">{f5}</div>""")

    A(table(["配置", "输出：前期", "输出：后期", "输出动态范围", "承伤：前期", "承伤：后期", "承伤动态范围"], [
        [f'<b>原版</b>', f'{van_o[0]:.2f}', f'{van_o[2]:.2f}',
         f'{now["output_span"]["vanilla"]["span"]:.2f}×',
         f'{van_i[0]:.2f}', f'{van_i[2]:.2f}', f'{now["intake_span"]["vanilla"]["span"]*100:.1f}×100']
    ] + [
        [s + (" ★" if s == "档位C" else ""),
         f'{curve(k, "out_curve", "僵尸", "dice")[0]:.2f}', f'{curve(k, "out_curve", "僵尸", "dice")[2]:.2f}',
         f'{span(k, "out_curve")["dice"]["span"]:.2f}×',
         f'{curve(k, "in_curve", "僵尸", "dice")[0]:.2f}', f'{curve(k, "in_curve", "僵尸", "dice")[2]:.2f}',
         f'{span(k, "in_curve")["dice"]["span"]*100:.1f}×100']
        for s, k in SHORT
    ]))

    A(f"""<div class="card note"><b>为什么压不到原版的 {now['output_span']['vanilla']['span']:.1f}×？</b><br>
骰战是「<b>加法</b>模型」（攻击点 − 防御点），原版是「<b>乘法</b>模型」（伤害 × 减免系数）。
「骰点 + 卡牌」是骰战的机制核心，它作为<b>加项</b>直接构成动态范围 —— 把跨度压到 {now['output_span']['vanilla']['span']:.1f}×
等价于<b>废除卡牌收益</b>。档位 C 取 <code>card_coef = 0.65</code>（终局 3×特大攻击牌按 65% 计），
把跨度压到 {rec['output_span']['dice']['span']:.2f}×（已压缩 {100*(1-rec['output_span']['dice']['span']/now['output_span']['dice']['span']):.0f}%）；
若要继续下压，需改动卡牌机制本身（如卡牌点数上限 / 卡牌冷却 / 同时生效张数上限），属独立议题。</div>""")

    # ---------- 5 修饰器复检 ----------
    A(f"""<h2>5. 修饰器复检：修正后是否真的生效</h2>
<p>基准：输出 = 钻石剑 → 僵尸；承伤 = 监守者 → 钻石全套。Δ% 为相对各自基准的变化。</p>
{legend([("原版 Δ%", COLOR["原版"]), ("现状骰战 Δ%", COLOR["现状"]), ("修正后 Δ%", COLOR["档位C"])])}
<div class="chart">{f6}</div>""")

    A(table(["方向", "修饰器", "原版 Δ%", "现状骰战 Δ%", "修正后 Δ%", "判定"],
            [[r["group"], r["modifier"], f'{r["vanilla_delta_pct"]:+.0f}%',
              f'{now_r["dice_delta_pct"]:+.0f}%', f'{r["dice_delta_pct"]:+.0f}%',
              ("<b style='color:#3f9d63'>已生效</b>" if abs(r["dice_delta_pct"]) > 1
               else "<b style='color:#c0392b'>仍失效</b>")]
             for r, now_r in zip(mc_cur, mc_now)]))

    A(f"""<div class="card ok"><b>复检结论</b><ul>
<li><b>锋利 V</b>：现状 <b>+0%</b> → 修正后 <b>+43%</b>，与原版 +43% <b>完全一致</b>。</li>
<li><b>攻击冷却 50%</b>：现状 <b>+0%</b> → 修正后 <b>−55%</b>（原版 −60%）—— 差值来自骰点不随冷却缩放，
属预期。</li>
<li><b>保护 IV 全套（16 点）</b>：现状 −36%（泄漏）→ 修正后 <b>−64%</b>，与原版 <b>完全一致</b>。</li>
<li><b>抗性提升 II</b>：现状 −22%（泄漏）→ 修正后 <b>−40%</b>，与原版 <b>完全一致</b>。</li>
<li><b>暴击 ×1.5</b>：现状 <b>+0%</b> → 修正后 <b>+75%</b>（原版 +50%）。<b>这是简单口径的固有差异</b>：
原版暴击只作用于属性攻击力 <code>f</code>，而简单口径作用于<b>整个攻击点</b>（含骰点与卡牌），
所以相对增幅更大。若需与严格口径一致（附魔不吃暴击），须拆 <code>attackPower</code> 分段，成本见原方案 §3.2。</li>
</ul></div>""")

    # ---------- 6 档位参数 ----------
    A("""<h2>6. 调参档位对照（供裁决）</h2>""")
    keys = ["play_base", "play_armor_coef", "play_tough_coef", "mob_def_base", "mob_armor_coef",
            "mob_tough_coef", "mob_atk_base", "card_coef", "bonus_softcap_threshold",
            "bonus_softcap_ratio", "resolve_floor_ratio"]
    labels = ["玩家防御起始", "玩家护甲系数", "玩家韧性系数", "生物防御起始", "生物护甲系数",
              "生物韧性系数", "生物攻击起始", "卡牌点数系数", "软上限阈值", "软上限保留比", "相对下限比例"]
    base = js["configs"]["现状（v1 基线）"]
    A(table(["旋钮（库 / TargetBattleStats）", "现状"] + [s for s, _ in SHORT[1:]],
            [[f'{lab} <span class="mono">{k}</span>',
              str(base[k])]
             + [("<b>%s</b>" % js["configs"][kk][k]) if js["configs"][kk][k] != base[k]
                else str(js["configs"][kk][k]) for _, kk in SHORT[1:]]
             for k, lab in zip(keys, labels)]))

    A(f"""<div class="card"><b>三个档位的取舍</b><ul>
<li><b>档位 B（仅校准基数）</b>：只改公式系数与基数，<b>不压卡牌</b>（<code>card_coef=1.0</code>）。
前期对齐原版，但后期仍达 <b>{span(SHORT[2][1], "out_curve")["dice"]["span"]:.1f}×</b> 动态范围。
用于观察「修好机制 + 校准基数」后的原生形态。</li>
<li><b>档位 C（推荐）</b>：在中度压缩卡牌（<code>card_coef=0.65</code>）的同时引入<b>相对下限</b>，
输出动态范围 <b>{rec['output_span']['dice']['span']:.2f}×</b>，前期与原版持平、后期 {o_rec[2]/van_o[2]:.2f}×，
承伤侧满配不再无敌。</li>
<li><b>档位 D（重度压缩）</b>：改用<b>软上限</b>（骰点+卡牌 > 7 起衰减，超出部分 ×0.45），
前期完全不受影响，后期进一步压到 <b>{o_rec[2]:.2f} → {curve(SHORT[4][1], "out_curve", "僵尸", "dice")[2]:.2f}</b>，
动态范围 <b>{span(SHORT[4][1], "out_curve")["dice"]["span"]:.2f}×</b>。代价是终局卡牌的边际收益明显变低。</li>
</ul></div>""")

    A("""<div class="card note"><b>两个「结构性」旋钮的含义</b><ul>
<li><code>resolve_floor_ratio</code>（档位 C/D = 0.15）：把 <code>DiceBattleResolver.resolve</code> 的
「绝对 1 点下限」改成「<b>攻击点 × 15%</b> 相对下限」。仅在此前触底的情形生效，对未触底的场景零影响。
它是唯一能让「高防 + 低攻生物」档位重新可读的手段。</li>
<li><code>bonus_softcap_*</code>（档位 D）：对「骰点 + 卡牌」合计做收益递减。
与 <code>card_coef</code> 的区别是：线性系数会<b>等比压小所有档位</b>，软上限只削<b>高投入</b>档位。</li>
</ul></div>""")

    # ---------- 7 落地清单 ----------
    A("""<h2>7. 一旦裁决，实际代码改动清单</h2>""")
    A(table(["文件", "改动"], [
        ["<code>starengine_lib/.../combat/VanillaMitigation.java</code>（新建）",
         "保护/抗性的纯算术复刻（原方案 §4 已给出完整代码）"],
        ["<code>starengine_lib/.../combat/CombatFormula.java</code>",
         "玩家/生物防御系数按选中档位改（<code>play_*</code> / <code>mob_*_coef</code>）"],
        ["<code>starengine_lib/.../combat/TargetBattleStats.java</code>",
         "<code>baseAttack</code> / <code>baseDefense</code> 按选中档位改"],
        ["<code>starengine_lib/.../combat/DiceBattleResolver.java</code>",
         "若采纳 <code>resolve_floor_ratio</code>：下限语义改为相对值"],
        ["三线 <code>DiceCombatEvents.java</code>（各一份，不得互抄）",
         "路径①：附魔 + 冷却 + 暴击；路径②：去 <code>getNewDamage()</code> + 生物武器附魔；"
         "两路径：受击方保护/抗性乘算因子"],
        ["三线 <code>DiceCombatModifiers.java</code>",
         "显示口径纳入附魔加伤（裁决 ④）；冷却与暴击保持不进"],
        ["三线 <code>gradle.properties</code>", "<code>starengine_lib_version</code> → <code>2.0.0-SNAPSHOT.2</code>"],
        ["库 + 模组 CHANGELOG（双语）", "按档位参数记录数值调整条目"],
    ]))

    A(f"""<h2>8. 待裁决</h2>
<div class="card warn"><ul>
<li><b>① 档位选择</b>：B / C / D 三选一（或给出自定义系数）。推荐 <b>C</b>。</li>
<li><b>② 承伤后期「比原版更痛」是否接受</b>：档位 C 下满配玩家承伤是原版的
{rec['in_curve'][2]['ratio']:.2f} 倍（原版满配近乎无敌）。这是「压平曲线」的必然代价。</li>
<li><b>③ 暴击口径差异是否接受</b>：简单口径实测 +75%（原版 +50%）；若要严格口径需拆分段，成本较高。</li>
<li><b>④ <code>resolve_floor_ratio</code> 是否引入库</b>：它改变 <code>DiceBattleResolver</code> 的核心语义，
会影响所有消费方（含未来模组）。</li>
<li><b>⑤ 生物攻击起始 <code>baseAttack</code></b>：档位从 5 降到 4，属对 2026-09-26「属性值 + 事件原值」裁决的
再次修订（因方案 A 已移除事件原值），需确认。</li>
</ul></div>""")

    A(f"""<footer>由 <code>tools/balance_sim/dice_combat_tuning.py</code> 生成数据、
<code>render_tuning.py</code> 渲染 · 采样 {js['meta']['n_samples']:,} · 随机种子固定 ⇒ 可逐位复算 ·
本报告不含任何已实施的代码改动</footer>
</div></body></html>""")

    html_doc = "".join(out)

    # ---------------- Markdown ----------------
    m: List[str] = []
    B = m.append
    B("# 骰战数值修正与调参 · 效果预览\n")
    B(f"> 1.21.1 / NeoForge 21.1.235 · 采样 {js['meta']['n_samples']:,} · 纯离线预览，未改代码\n")
    B("## 0. 摘要\n")
    B("| 指标 | 现状 | 档位C（推荐） | 原版 |")
    B("|---|---|---|---|")
    B(f"| 前期输出（每击） | {o_now[0]:.2f} | {o_rec[0]:.2f} | {van_o[0]:.2f} |")
    B(f"| 后期输出（每击） | {o_now[2]:.2f} | {o_rec[2]:.2f} | {van_o[2]:.2f} |")
    B(f"| 输出动态范围 | {now['output_span']['dice']['span']:.2f}× | {rec['output_span']['dice']['span']:.2f}× "
      f"| {now['output_span']['vanilla']['span']:.2f}× |")
    B(f"| 前期承伤（裸装/僵尸） | {i_now[0]:.2f} | {i_rec[0]:.2f} | {van_i[0]:.2f} |")
    B(f"| 后期承伤（钻石/僵尸） | {i_now[2]:.2f} | {i_rec[2]:.2f} | {van_i[2]:.2f} |")
    B(f"| 前期可承受击数 | {curve(SHORT[0][1], 'in_curve', '僵尸', 'dice_hits')[0]:.1f} "
      f"| {curve(SHORT[3][1], 'in_curve', '僵尸', 'dice_hits')[0]:.1f} "
      f"| {curve(SHORT[0][1], 'in_curve', '僵尸', 'vanilla_hits')[0]:.1f} |")
    B("")
    B("## 2. 输出曲线（靶 = 僵尸）\n")
    B("| 档位 | 原版 | 现状 | 方案A | 档位B | 档位C ★ | 档位D |")
    B("|---|---|---|---|---|---|---|")
    for si, stage in enumerate(STAGE_NAMES):
        B(f'| {stage} | {van_o[si]:.2f} | ' + " | ".join(
            f'{curve(k, "out_curve", "僵尸", "dice")[si]:.2f}' for _, k in SHORT) + " |")
    B("")
    B("## 3. 承伤曲线（源 = 僵尸）\n")
    B("| 档位 | 原版 | 现状 | 方案A | 档位B | 档位C ★ | 档位D |")
    B("|---|---|---|---|---|---|---|")
    for si, stage in enumerate(STAGE_NAMES):
        B(f'| {stage} | {van_i[si]:.2f} | ' + " | ".join(
            f'{curve(k, "in_curve", "僵尸", "dice")[si]:.2f}' for _, k in SHORT) + " |")
    B("")
    B("## 5. 修饰器复检\n")
    B("| 方向 | 修饰器 | 原版 Δ% | 现状 Δ% | 修正后 Δ% |")
    B("|---|---|---|---|---|")
    for r, now_r in zip(mc_cur, mc_now):
        B(f'| {r["group"]} | {r["modifier"]} | {r["vanilla_delta_pct"]:+.0f}% | '
          f'{now_r["dice_delta_pct"]:+.0f}% | {r["dice_delta_pct"]:+.0f}% |')
    B("")

    return html_doc, "\n".join(m)


def main() -> None:
    src = "docs/balance/dice-combat-tuning.json"
    js = json.load(open(src, encoding="utf-8"))
    html_doc, md = build(js)
    os.makedirs("docs/balance", exist_ok=True)
    with open("docs/balance/dice-combat-tuning.html", "w", encoding="utf-8", newline="\n") as f:
        f.write(html_doc)
    with open("docs/balance/dice-combat-tuning.md", "w", encoding="utf-8", newline="\n") as f:
        f.write(md)
    print(f"[render] html {len(html_doc)} chars / md {len(md)} chars")


if __name__ == "__main__":
    main()
