#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
骰战数值「修正 + 调参」报告渲染器（终版口径）。

输入：dice-combat-tuning.json（由 tools/balance_sim/dice_combat_tuning.py 产出）
输出：dice-combat-tuning.html / .md（浅色主题、内联 SVG、零外链）

复用 render_report.py 的 SVG 组件，不复制绘图逻辑。

终版口径（2026-09-26 裁决）：
  ① 方案 A（去掉 getNewDamage()）
  ② 暴击 = **分拆口径**（×1.5 只作用于原版基础段，与 Player#attack 同域）
  ③ 攻击冷却一并处理
  ④ 附魔加伤进入显示口径
  ⑤ **卡牌点数基数不压缩**（card_coef = 1.0）
  ⑥ DiceBattleResolver 下限改为相对值（入库）
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

KEY_NOW = "现状（v1 基线）"
KEY_A = "方案A·纯落地"
KEY_B = "档位B·仅校准基数"
KEY_C = "档位C·保留卡牌基数（推荐）"

# 配置短名 ↔ JSON key（顺序 = 展示顺序）
SHORT: List[Tuple[str, str]] = [
    ("现状", KEY_NOW),
    ("方案A", KEY_A),
    ("档位B", KEY_B),
    ("档位C", KEY_C),
]
COLOR = {
    "原版": "#3b6fd4",
    "现状": "#c0392b",
    "方案A": "#e08a4a",
    "档位B": "#8fa3c8",
    "档位C": "#2e9e6b",
}
STAGE_NAMES = ["前期", "中期", "后期"]

# 修饰器短名（柱状图 x 轴）
SHORT_MOD = {
    "基准（钻石剑，无修饰器）": "基准",
    "锋利 V（+3）": "锋利V",
    "攻击冷却 50%": "冷却50%",
    "暴击 ×1.5（采纳·分拆口径）": "暴击·分拆",
    "暴击 ×1.5（对照·整点口径）": "暴击·整点",
    "基准（监守者→钻石全套）": "基准",
    "保护 IV 全套（16 点）": "保护IV",
    "抗性提升 II": "抗性II",
}


def build(js: Dict) -> Tuple[str, str]:
    C = js["curves"]

    def curve(conf: str, kind: str, key: str, field: str) -> List[float]:
        rows = C[conf][kind]
        return [r[field] for r in rows if r["target" if kind == "out_curve" else "source"] == key]

    def span(conf: str, kind: str) -> Dict:
        return C[conf]["output_span" if kind == "out_curve" else "intake_span"]

    # ---------------- 图 1：输出曲线（僵尸靶）----------------
    out_series = [("原版", curve(KEY_NOW, "out_curve", "僵尸", "vanilla"), COLOR["原版"])]
    for short, key in SHORT:
        out_series.append((short, curve(key, "out_curve", "僵尸", "dice"), COLOR[short]))
    f1 = svg_lines(STAGE_NAMES, out_series, ylabel="每击期望伤害", fmt="{:.0f}", height=340, ystep=6)

    # ---------------- 图 2：承伤曲线（僵尸源）----------------
    in_series = [("原版", curve(KEY_NOW, "in_curve", "僵尸", "vanilla"), COLOR["原版"])]
    for short, key in SHORT:
        in_series.append((short, curve(key, "in_curve", "僵尸", "dice"), COLOR[short]))
    f2 = svg_lines(STAGE_NAMES, in_series, ylabel="每击承受伤害", fmt="{:.1f}", height=340, ystep=6)

    # ---------------- 图 3：可承受击数（20 HP）----------------
    hits_series = [("原版", curve(KEY_NOW, "in_curve", "僵尸", "vanilla_hits"), COLOR["原版"])]
    for short, key in SHORT:
        hits_series.append((short, curve(key, "in_curve", "僵尸", "dice_hits"), COLOR[short]))
    f3 = svg_lines(STAGE_NAMES, hits_series, ylabel="可承受击数（20 HP）", fmt="{:.0f}", height=330, ystep=6)

    # ---------------- 图 4 / 5：动态范围 ----------------
    f45_cats = ["原版"] + [s for s, _ in SHORT]
    f4_out = [span(KEY_NOW, "out_curve")["vanilla"]["span"]] + \
             [span(k, "out_curve")["dice"]["span"] for _, k in SHORT]
    f4 = svg_grouped_bars(f45_cats, [("输出动态范围（后期÷前期）", f4_out, COLOR["档位C"])],
                          ylabel="倍率", fmt="{:.1f}", height=300, ystep=5)

    f5_in = [span(KEY_NOW, "in_curve")["vanilla"]["span"] * 100] + \
            [span(k, "in_curve")["dice"]["span"] * 100 for _, k in SHORT]
    f5 = svg_grouped_bars(f45_cats, [("承伤动态范围（后期÷前期，×100）", f5_in, COLOR["档位B"])],
                          ylabel="倍率 ×100", fmt="{:.1f}", height=300, ystep=5)

    # ---------------- 图 6：修饰器复检 Δ% ----------------
    mc_c = C[KEY_C]["modifier_check"]
    mc_a = C[KEY_A]["modifier_check"]
    mc_now = C[KEY_NOW]["modifier_check"]
    f6 = svg_grouped_bars(
        [SHORT_MOD.get(r["modifier"], r["modifier"][:7]) for r in mc_c],
        [("原版 Δ%", [r["vanilla_delta_pct"] for r in mc_c], COLOR["原版"]),
         ("现状骰战 Δ%", [r["dice_delta_pct"] for r in mc_now], COLOR["现状"]),
         ("方案A（未校基数）Δ%", [r["dice_delta_pct"] for r in mc_a], COLOR["方案A"]),
         ("终版 C Δ%", [r["dice_delta_pct"] for r in mc_c], COLOR["档位C"])],
        ylabel="相对基准的变化（%）", fmt="{:+.0f}", height=380, ystep=4, diverging=True)

    # =========================================================== 组装
    out: List[str] = []
    A = out.append

    A(f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>骰战数值修正与调参 · 终版口径</title>
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

    A(f"""<h1>骰战数值修正与调参 · 终版口径</h1>
<p class="sub">基准版本 Minecraft 1.21.1 / NeoForge 21.1.235 · 蒙特卡洛 {js['meta']['n_samples']:,} 采样/单元 ·
口径 = 方案 A（去 <code>getNewDamage()</code>）+ <b>分拆暴击</b> + 攻击冷却 + 附魔生效 + <b>卡牌基数不压缩</b> ·
<code>resolve</code> 下限改为相对值（入库）</p>""")

    # ---------- 摘要 ----------
    now = C[KEY_NOW]
    rec = C[KEY_C]
    o_now = curve(KEY_NOW, "out_curve", "僵尸", "dice")
    o_rec = curve(KEY_C, "out_curve", "僵尸", "dice")
    i_now = curve(KEY_NOW, "in_curve", "僵尸", "dice")
    i_rec = curve(KEY_C, "in_curve", "僵尸", "dice")
    van_o = curve(KEY_NOW, "out_curve", "僵尸", "vanilla")
    van_i = curve(KEY_NOW, "in_curve", "僵尸", "vanilla")

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

    A(f"""<div class="card ok"><b>终版口径解决的三个问题</b><ul>
<li><b>修饰器失效（已修）</b>：锋利 / 攻击冷却 / 暴击 / 保护 / 抗性在骰战下原本 Δ = 0%，
终版落地后 <b>四项与原版逐一对齐、暴击因改成「分拆口径」后也精确对齐</b>（见第 5 节）。</li>
<li><b>前期太痛（已修）</b>：裸装被僵尸打由 <b>{i_now[0]:.1f}/击（原版 {van_i[0]:.1f} 的 {i_now[0]/van_i[0]:.2f} 倍）降到
{i_rec[0]:.1f}/击（{i_rec[0]/van_i[0]:.2f} 倍）</b>，前期可承受击数由 2 下回到 6 下。</li>
<li><b>后期触底（已修）</b>：<code>resolve</code> 下限由「绝对 1 点」改为「攻击点 ×15%」，
满配不再恒落 1.0 下限 ⇒ 承伤档位重新可读。</li>
</ul>
<b>未解决（已按裁决放弃该路径）</b>：输出动态范围仍是原版的 {rec['output_span']['dice']['span']/now['output_span']['vanilla']['span']:.1f} 倍量级 ——
因为「不压缩卡牌点数」直接保留了这个跨度，见第 4.3 节。</div>""")

    # ---------- 1 裁决落地 ----------
    A("""<h2>1. 裁决的落地口径</h2>""")
    A(table(["裁决", "含义", "实现要点（三线同构）", "复检判据"], [
        ["① 方案 A", "移除 <code>getNewDamage()</code>，生物攻击点改用 pre-mitigation 原始伤害",
         "路径② <code>mobAttack = baseAttack + ATTACK_DAMAGE + 生物武器附魔 + 骰点</code>；"
         "护甲只经 <code>playerDefense</code> 计一次",
         "1.20.1 = <code>getAmount()</code> / 1.21.1 = <code>getNewDamage()</code> / "
         "26.1.2 = <code>getHealthDamage()</code>，三者均<b>不再出现在攻击点</b>"],
        ["② 分拆暴击口径", "<code>×1.5</code> 只作用于<b>原版基础段</b>（属性值×冷却缩放 + 攻击修饰器），"
         "骰点与卡牌<b>不吃暴击</b>",
         "严格复刻 <code>Player#attack</code>：<code>f *= 1.5</code> 在 <code>f3 = f + f1</code> <b>之前</b>，"
         "所以附魔增量与骰战加项都不在暴击域内",
         "暴击 Δ <b>+50%</b>，与原版 <b>完全一致</b>（整点口径为 +75%，仅作对照保留）"],
        ["③ 一并处理冷却", "读 <code>getAttackStrengthScale(0.5F)</code>，基础段乘 <code>0.2 + s²·0.8</code>、"
         "附魔增量乘 <code>s</code>",
         "两段的缩放系数<b>不同</b>（基础 <code>0.2+s²·0.8</code>、附魔 <code>s</code>）—— 逐行照搬源码，不做等价合并",
         "冷却 50% ⇒ Δ ≈ −55%（原版 −60%，差值来自骰点不随冷却缩放）"],
        ["④ 附魔项加入", "<code>DiceCombatModifiers.attackPowerBase</code> 的取值含附魔加伤",
         "降神快照 / HUD 攻击条 / tooltip 区间三处消费同一函数，必须同步；"
         "冷却与暴击属瞬态，<b>不进</b>显示口径",
         "HUD 与结算读数一致（消除「显示一个值、结算另一个值」漂移）"],
        ["⑤ 卡牌基数不压缩", "<code>card_coef = 1.0</code> 保持不变 —— 卡牌是<b>每次攻击 1~10 随机</b>的"
         "临时性爆发加成，运气权重高，不参与曲线压缩",
         "不引入任何卡牌点数系数 / 软上限；只调整<b>公式基数与斜率</b>",
         "输出跨度保持 {:.2f}×（不接受以削弱卡牌为代价的压缩）".format(rec["output_span"]["dice"]["span"])],
        ["⑥ <code>resolve</code> 入库", "下限语义由「绝对 1 点」改为「攻击点 × 15%」相对下限",
         "<code>DiceBattleResolver.resolve()</code> 新增比例参数；属<b>基本玩法的大型机制调整</b>，"
         "需 bump 库版本 + 双 CHANGELOG 记录 + 影响全部消费方",
         "满配承伤不再恒为 1.0；未触底场景读数零变化"],
    ]))

    # ---------- 2 输出曲线 ----------
    lg = [("原版", COLOR["原版"])] + [(s, COLOR[s]) for s, _ in SHORT]
    A(f"""<h2>2. 难度曲线 A：玩家 → 生物（输出）</h2>
<h3>2.1 每击期望伤害（靶 = 僵尸，护甲 2）</h3>
{legend(lg)}
<div class="chart">{f1}</div>""")

    rows = []
    for si, stage in enumerate(STAGE_NAMES):
        r = C[KEY_C]["out_curve"][si * 2]
        cells = [f'<b>{stage}</b>（{r["weapon"]} / {r["armor_set"]} / {len(r["cards"])} 张卡）',
                 f'{r["vanilla"]:.2f}']
        for _, key in SHORT:
            v = curve(key, "out_curve", "僵尸", "dice")[si]
            mark = " ★" if key == KEY_C else ""
            cells.append(f'{v:.2f}{mark}')
        rows.append(cells)
    A(table(["档位（装备）", "原版"] + [s for s, _ in SHORT], rows))
    A(f"""<div class="card"><b>读法</b>：原版三档位是 <b>{van_o[0]:.2f} → {van_o[1]:.2f} → {van_o[2]:.2f}</b>
（动态范围 {now['output_span']['vanilla']['span']:.2f}×）。骰战现状是
<b>{o_now[0]:.2f} → {o_now[1]:.2f} → {o_now[2]:.2f}</b>（{now['output_span']['dice']['span']:.2f}×）——
<b>前期只有原版的 {o_now[0]/van_o[0]:.2f} 倍</b>（打不动怪），后期 <b>{o_now[2]/van_o[2]:.2f} 倍</b>。
终版 C 把它拉成 <b>{o_rec[0]:.2f} → {o_rec[1]:.2f} → {o_rec[2]:.2f}</b>
（{rec['output_span']['dice']['span']:.2f}×）：<b>前期与原版持平（{o_rec[0]/van_o[0]:.2f}×）</b>，
后期 {o_rec[2]/van_o[2]:.2f} 倍 —— 跨度相比现状压缩
<b>{100*(1-rec['output_span']['dice']['span']/now['output_span']['dice']['span']):.0f}%</b>，
但因卡牌基数不压缩，剩余跨度即为卡牌的固有随机范围。</div>""")

    # ---------- 3 承伤曲线 ----------
    A(f"""<h2>3. 难度曲线 B：生物 → 玩家（承伤）</h2>
<h3>3.1 每击承受伤害（源 = 僵尸）</h3>
{legend(lg)}
<div class="chart">{f2}</div>""")

    rows = []
    for si, stage in enumerate(STAGE_NAMES):
        r = C[KEY_C]["in_curve"][si * 2]
        cells = [f'<b>{stage}</b>（{r["armor_set"]}，保护 {r["prot"]:.0f} 点）', f'{r["vanilla"]:.2f}']
        for _, key in SHORT:
            v = curve(key, "in_curve", "僵尸", "dice")[si]
            mark = " ★" if key == KEY_C else ""
            cells.append(f'{v:.2f}{mark}')
        rows.append(cells)
    A(table(["档位（护甲）", "原版"] + [s for s, _ in SHORT], rows))

    A(f"""<h3>3.2 可承受击数（20 生命，不建模回血）</h3>
{legend(lg)}
<div class="chart">{f3}</div>""")
    A(table(["档位（护甲）", "原版"] + [s for s, _ in SHORT],
            [[f'<b>{stage}</b>',
              f'{curve(KEY_NOW, "in_curve", "僵尸", "vanilla_hits")[si]:.1f}']
             + [f'{curve(key, "in_curve", "僵尸", "dice_hits")[si]:.1f}'
                + (" ★" if key == KEY_C else "") for _, key in SHORT]
             for si, stage in enumerate(STAGE_NAMES)]))

    A(f"""<div class="card note"><b>承伤侧的三条读数（僵尸源）</b><ul>
<li><b>前期</b>：现状 <b>{i_now[0]:.2f}</b>（原版 {van_i[0]:.2f}，{i_now[0]/van_i[0]:.2f} 倍）——
裸装几乎被打穿；终版 C <b>{i_rec[0]:.2f}</b>（{i_rec[0]/van_i[0]:.2f} 倍），与原版对齐。</li>
<li><b>后期触底</b>：现状骰战满配 <b>{i_now[2]:.2f}</b>，已落到 1 点下限（原版 {van_i[2]:.2f}）；
终版 C 用「相对下限」（攻击点 ×15%）把它抬到 <b>{i_rec[2]:.2f}</b>，同时保留保护减免 ⇒ 满配不再无敌。</li>
<li><b>动态范围</b>：现状 {now['intake_span']['dice']['span']*100:.1f}×100；
终版 C {rec['intake_span']['dice']['span']*100:.1f}×100（原版 {now['intake_span']['vanilla']['span']*100:.1f}×100）——
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
        ["<b>原版</b>", f'{van_o[0]:.2f}', f'{van_o[2]:.2f}',
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

    A(f"""<h3>4.3 为什么输出跨度压不到原版的 {now['output_span']['vanilla']['span']:.1f}×？</h3>
<div class="card warn">
骰战是「<b>加法</b>模型」（攻击点 − 防御点），原版是「<b>乘法</b>模型」（伤害 × 减免系数）。
「骰点 + 卡牌」是骰战的机制核心，它作为<b>加项</b>直接构成动态范围。
<p><b>本次裁决明确不压缩卡牌点数</b>（<code>card_coef = 1.0</code>），理由是：卡牌是
每次攻击 <b>1~10 随机</b>的临时性爆发加成，运气成分占主导 ⇒ 它<b>不是</b>一条可被线性缩放的
「装备强度曲线」。因此：</p>
<ul>
<li>终版 C 的跨度 <b>{rec['output_span']['dice']['span']:.2f}×</b> 中，公式侧（基数 / 斜率）已调到最平；
剩余部分<b>全部</b>来自卡牌与骰点的固有随机范围。</li>
<li>把跨度继续压向 {now['output_span']['vanilla']['span']:.2f}× 只有三条路，<b>都不在本次裁决范围内</b>：
① 压卡牌点数（已否决）；② 给卡牌加冷却 / 限制同时生效张数（机制改动）；
③ 把卡牌改成乘算（会同时改变「后期太高」与「前期运气爆发」两种体验）。</li>
<li>若日后要处理「后期太高」，建议走 ② 而非 ① —— 它削的是<b>频率</b>而非单次<b>数值</b>，
不违背「卡牌是临时性随机加成」的定性。</li>
</ul></div>""")

    # ---------- 5 修饰器复检 ----------
    A(f"""<h2>5. 修饰器复检：修正后是否真的生效</h2>
<p>基准：输出 = 钻石剑 → 僵尸；承伤 = 监守者 → 钻石全套。Δ% 为相对各自基准的变化。
「方案A（未校基数）」一列用于说明<b>为什么必须先校基数再看 Δ</b>。</p>
{legend([("原版 Δ%", COLOR["原版"]), ("现状骰战 Δ%", COLOR["现状"]),
         ("方案A（未校基数）Δ%", COLOR["方案A"]), ("终版 C Δ%", COLOR["档位C"])])}
<div class="chart">{f6}</div>""")

    A(table(["方向", "修饰器", "原版 Δ%", "现状骰战 Δ%", "方案A Δ%", "终版 C Δ%", "判定"],
            [[r["group"], r["modifier"], f'{r["vanilla_delta_pct"]:+.0f}%',
              f'{now_r["dice_delta_pct"]:+.0f}%', f'{a_r["dice_delta_pct"]:+.0f}%',
              f'{r["dice_delta_pct"]:+.0f}%',
              ("<b style='color:#3f9d63'>已生效</b>" if abs(r["dice_delta_pct"]) > 1
               else "<b style='color:#c0392b'>仍失效</b>")]
             for r, now_r, a_r in zip(mc_c, mc_now, mc_a)]))

    A(f"""<div class="card ok"><b>复检结论（终版 C 列）</b><ul>
<li><b>锋利 V</b>：现状 <b>+0%</b> → 终版 <b>+43%</b>，与原版 +43% <b>完全一致</b>。</li>
<li><b>攻击冷却 50%</b>：现状 <b>+0%</b> → 终版 <b>−55%</b>（原版 −60%）。差值来自骰点不随冷却缩放 ——
这是加法模型的固有项，<b>与原版对齐的前提是骰点也随冷却缩放</b>（不建议，会破坏骰战手感）。</li>
<li><b>暴击 ×1.5（分拆口径）</b>：现状 <b>+0%</b> → 终版 <b>+50%</b>，与原版 <b>完全一致</b>。
（对照的整点口径为 +75%，已按裁决弃用。）</li>
<li><b>保护 IV 全套（16 点）</b>：现状 −36%（泄漏）→ 终版 <b>−64%</b>，与原版 <b>完全一致</b>。</li>
<li><b>抗性提升 II</b>：现状 −22%（泄漏）→ 终版 <b>−40%</b>，与原版 <b>完全一致</b>。</li>
</ul></div>""")

    A(f"""<div class="card note"><b>「方案A（未校基数）」列为什么全线偏高？</b><br>
该列 <code>mob_def_base</code> 仍为 2.0、<code>play_base</code> 仍为 2.0，基准值只有
<b>{curve(KEY_A, 'out_curve', '僵尸', 'dice')[0]:.2f}</b>，已逼近 <code>resolve</code> 的 1 点下限。
低基数会把任何<b>加项</b>的相对增幅放大（锋利 +70%、暴击 +83%），
所以<b>修饰器的 Δ% 只有在基数校准后才与原版可比</b>。这也说明：修传导与调基数必须同批落地。</div>""")

    # ---------- 6 档位参数 ----------
    A("""<h2>6. 调参档位对照</h2>""")
    keys = ["play_base", "play_armor_coef", "play_tough_coef", "mob_def_base", "mob_armor_coef",
            "mob_tough_coef", "mob_atk_base", "card_coef", "resolve_floor_ratio"]
    labels = ["玩家防御起始", "玩家护甲系数", "玩家韧性系数", "生物防御起始", "生物护甲系数",
              "生物韧性系数", "生物攻击起始", "卡牌点数系数", "相对下限比例"]
    base = js["configs"][KEY_NOW]
    A(table(["旋钮（库 / TargetBattleStats）", "现状"] + [s for s, _ in SHORT[1:]],
            [[f'{lab} <span class="mono">{k}</span>', str(base[k])]
             + [("<b>%s</b>" % js["configs"][kk][k]) if js["configs"][kk][k] != base[k]
                else str(js["configs"][kk][k]) for _, kk in SHORT[1:]]
             for k, lab in zip(keys, labels)]))

    A(f"""<div class="card"><b>终版 C 的三个杠杆（卡牌不参与）</b><ul>
<li><b>基数</b>（<code>play_base</code> 2→4、<code>mob_atk_base</code> 5→4、<code>mob_def_base</code> 2→0）：
整条曲线<b>平移</b> —— 治「前期太痛 / 打不动怪」。</li>
<li><b>斜率</b>（<code>play_armor_coef</code> 0.5→0.30、<code>play_tough_coef</code> 1.4→0.85、
<code>mob_armor_coef</code> 0.5→0.40）：<b>凹化</b>「裸装→满配」的跨度 —— 治「穿上好甲就无敌」。</li>
<li><b>下限语义</b>（<code>resolve_floor_ratio</code> 0 → 0.15）：消除「高防 + 低攻生物 ⇒ 恒触底」的读数失真。</li>
</ul>
与档位 B 的唯一差别就是「斜率 + 下限」这两组（B 保留旧斜率与绝对下限）——
输出侧两者完全相同，因为<b>任何卡牌系数都没动</b>。</div>""")

    # ---------- 7 落地清单 ----------
    A("""<h2>7. 实际代码改动清单（本次落地）</h2>""")
    A(table(["文件", "改动"], [
        ["<code>starengine_lib/.../combat/VanillaMitigation.java</code>（新建）",
         "保护 / 抗性的纯算术复刻，供三线复用（原方案 §4 已给出完整代码）"],
        ["<code>starengine_lib/.../combat/CombatFormula.java</code>",
         "<code>play_base</code> 4.0 / <code>play_armor_coef</code> 0.30 / <code>play_tough_coef</code> 0.85 / "
         "<code>mob_armor_coef</code> 0.40 / <code>mob_tough_coef</code> 1.0"],
        ["<code>starengine_lib/.../combat/TargetBattleStats.java</code>",
         "<code>baseAttack</code> 敌对 5 → 4（中立同步 −1）；<code>mobDefenseInt</code> 起始 2 → 0"],
        ["<code>starengine_lib/.../combat/DiceBattleResolver.java</code>",
         "<b>（裁决⑥）</b>下限改为 <code>max(raw, 1, 攻击点 × 0.15)</code> 相对下限"],
        ["三线 <code>DiceCombatEvents.java</code>（各一份，不得互抄）",
         "路径①：附魔（<code>EnchantmentHelper.modifyDamage</code>）+ 冷却（基础段 <code>0.2+s²·0.8</code> / "
         "附魔段 <code>s</code>）+ 分拆暴击；路径②：去 <code>getNewDamage()</code> + 生物武器附魔；"
         "两路径：受击方保护/抗性乘算因子"],
        ["三线 <code>DiceCombatModifiers.java</code>",
         "显示口径纳入附魔加伤（裁决④）；冷却与暴击保持不进"],
        ["三线 <code>gradle.properties</code>", "<code>starengine_lib_version</code> → <code>2.0.0-SNAPSHOT.2</code>（库 + 模组均 bump）"],
        ["库 + 模组 CHANGELOG（双语）", "记录机制调整（resolve 下限）与数值调整（基数 / 斜率）两组条目"],
    ]))

    A(f"""<div class="card note"><b>仍未确认的两项（不阻塞落地，但会影响数值口径复核）</b><ul>
<li><b>承伤后期「比原版更痛」</b>：终版 C 下满配玩家承伤是原版的
{rec['in_curve'][2]['ratio']:.2f} 倍（原版满配近乎无敌）。这是「压平承伤曲线」的必然代价。</li>
<li><b>生物攻击起始 <code>baseAttack</code> 5 → 4</b>：因方案 A 已移除事件原值，属对 2026-09-26
「属性值 + 事件原值」裁决的再次修订 —— 若不改，前期承伤会高出约 1 点/击。</li>
</ul></div>""")

    A(f"""<footer>由 <code>tools/balance_sim/dice_combat_tuning.py</code> 生成数据、
<code>render_tuning.py</code> 渲染 · 采样 {js['meta']['n_samples']:,} · 随机种子固定 ⇒ 可逐位复算</footer>
</div></body></html>""")

    html_doc = "".join(out)

    # ---------------- Markdown ----------------
    m: List[str] = []
    B = m.append
    B("# 骰战数值修正与调参 · 终版口径\n")
    B(f"> 1.21.1 / NeoForge 21.1.235 · 采样 {js['meta']['n_samples']:,} · 口径 = 方案A + 分拆暴击 + 冷却 + 附魔 + 卡牌基数不压缩 + 相对下限\n")
    B("## 0. 摘要\n")
    B("| 指标 | 现状 | 终版C | 原版 |")
    B("|---|---|---|---|")
    B(f"| 前期输出（每击） | {o_now[0]:.2f} | {o_rec[0]:.2f} | {van_o[0]:.2f} |")
    B(f"| 后期输出（每击） | {o_now[2]:.2f} | {o_rec[2]:.2f} | {van_o[2]:.2f} |")
    B(f"| 输出动态范围 | {now['output_span']['dice']['span']:.2f}× | {rec['output_span']['dice']['span']:.2f}× "
      f"| {now['output_span']['vanilla']['span']:.2f}× |")
    B(f"| 前期承伤（裸装/僵尸） | {i_now[0]:.2f} | {i_rec[0]:.2f} | {van_i[0]:.2f} |")
    B(f"| 后期承伤（钻石/僵尸） | {i_now[2]:.2f} | {i_rec[2]:.2f} | {van_i[2]:.2f} |")
    B(f"| 前期可承受击数 | {curve(KEY_NOW, 'in_curve', '僵尸', 'dice_hits')[0]:.1f} "
      f"| {curve(KEY_C, 'in_curve', '僵尸', 'dice_hits')[0]:.1f} "
      f"| {curve(KEY_NOW, 'in_curve', '僵尸', 'vanilla_hits')[0]:.1f} |")
    B("")
    B("## 2. 输出曲线（靶 = 僵尸）\n")
    B("| 档位 | 原版 | 现状 | 方案A | 档位B | 档位C ★ |")
    B("|---|---|---|---|---|---|")
    for si, stage in enumerate(STAGE_NAMES):
        B(f'| {stage} | {van_o[si]:.2f} | ' + " | ".join(
            f'{curve(k, "out_curve", "僵尸", "dice")[si]:.2f}' for _, k in SHORT) + " |")
    B("")
    B("## 3. 承伤曲线（源 = 僵尸）\n")
    B("| 档位 | 原版 | 现状 | 方案A | 档位B | 档位C ★ |")
    B("|---|---|---|---|---|---|")
    for si, stage in enumerate(STAGE_NAMES):
        B(f'| {stage} | {van_i[si]:.2f} | ' + " | ".join(
            f'{curve(k, "in_curve", "僵尸", "dice")[si]:.2f}' for _, k in SHORT) + " |")
    B("")
    B("## 4. 动态范围\n")
    B("| 配置 | 输出跨度 | 承伤跨度（×100） |")
    B("|---|---|---|")
    B(f"| 原版 | {now['output_span']['vanilla']['span']:.2f}× | {now['intake_span']['vanilla']['span']*100:.1f} |")
    for s, k in SHORT:
        B(f'| {s} | {span(k, "out_curve")["dice"]["span"]:.2f}× '
          f'| {span(k, "in_curve")["dice"]["span"]*100:.1f} |')
    B("")
    B("## 5. 修饰器复检\n")
    B("| 方向 | 修饰器 | 原版 Δ% | 现状 Δ% | 方案A Δ% | 终版C Δ% |")
    B("|---|---|---|---|---|---|")
    for r, now_r, a_r in zip(mc_c, mc_now, mc_a):
        B(f'| {r["group"]} | {r["modifier"]} | {r["vanilla_delta_pct"]:+.0f}% | '
          f'{now_r["dice_delta_pct"]:+.0f}% | {a_r["dice_delta_pct"]:+.0f}% | {r["dice_delta_pct"]:+.0f}% |')
    B("")
    B("## 6. 终版参数\n")
    B("| 旋钮 | 现状 | 终版C |")
    B("|---|---|---|")
    for k, lab in zip(keys, labels):
        B(f'| {lab} `{k}` | {base[k]} | {js["configs"][KEY_C][k]} ★ |')
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
