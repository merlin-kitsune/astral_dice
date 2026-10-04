# Tooltip 染色规则（v2，1.2.0 起生效）

> 权威副本：`*/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java` 类头 javadoc。
> 本文档为可读版说明；两者必须同步修改。
> 适用对象：**物品 tooltip**（`ItemTooltipEvent`，唯一监听点 `ModTooltipHandler.onItemTooltip`）。
> 不适用：帕秋莉手册正文（`astral_dice.guide.entry.*`）、HUD/聊天/动作栏消息。

> **v2 变更（1.2.0 hotfix）**：新增「规则 0：回落码 = 本行底色码」（`§r` / `§7` 都不是「恢复本行底色」）
> 与「规则 4：含 `%%` 的文案必须走 `tt(...)`」；v1 的「`§r` 保持原样」例外**作废**。

---

## 0. 基调

| 角色 | 颜色码 | 说明 |
|---|---|---|
| 正文（普通文本） | `§7` 灰 | 最常见的行底色；**不是**通用「回落码」——回落码必须等于本行底色（见 §1） |
| 数值 | `§e` 黄 | 非时间类的所有数字 |
| 时间 | `§9` 蓝 | 时长 / 冷却 / 间隔 |
| 负面 / 状态警示 | `§c` 红 | 保留既有语义（见 §6 例外） |
| 标题 | 行级 `withStyle(GOLD)` | 立牌「主动技能（X）」等标题行 |
| 备注 | `§d` 粉紫 | `addSignNoteLines` 统一替换 `§7` → `§d` |
| 按键提示 | `§f` 白 | `sign.key_hint`，其行级基础色即 WHITE |

**颜色即语义**：黄只出现在「数值」上，蓝只出现在「时间」上。行级 `withStyle(...)`
（标题金、负面行红、骰子词条各行的绿/金/紫等）属于「词条语义色」，与本规则并行、互不覆盖。

**本行底色（base）** 由该行 `tt(...)` / `Component.literal(...)` 之后的 `.withStyle(ChatFormatting.X)` 决定；
下文所有「回落」都指回到这个底色。

---

## 1. 规则 0：回落码 = 本行底色码

行内高亮值（数值 / 时间）结束后，**必须写回本行底色的色码**，而不是 `§r`：

| 本行底色 | 回落码 |
|---|---|
| 灰 GRAY | `§7` |
| 金 GOLD | `§6` |
| 粉紫 LIGHT_PURPLE | `§d` |
| 青 AQUA | `§b` |
| 蓝 BLUE | `§9` |
| 红 RED | `§c` |
| 绿 GREEN | `§a` |
| 深绿 DARK_GREEN | `§2` |
| 黄 YELLOW | `§e` |
| 白 WHITE | `§f` |

**为什么不能用 `§r`**（1.21.1 Mojang 映射源码证据）：

```
tooltip 渲染路径
  Tooltip.splitTooltip(component)                  -> Font.split(component, 170)
  Font.java:328                                    -> splitter.splitLines(text, maxWidth, Style.EMPTY)
  StringSplitter.java:259                          -> StringDecomposer.iterateFormatted(
                                                        contents, 0, 组件自身 style, p_style /* = Style.EMPTY */, sink)
  StringDecomposer.java:114                        -> style = (code == RESET) ? defaultStyle : style.applyLegacyFormat(code)
```

即 `§r` 复位成 **`Style.EMPTY`（无颜色 → 渲染为白）**，`§7` 复位成灰；**两者都不等于本行底色**。
在非灰底的行上，任何行内 `§r` / `§7` 都会让后续文字掉成白 / 灰 —— 这正是 1.2.0 两轮 tooltip 染色
BUG（下界之星骰子、末影骰子）的根因：第一次把 `§7` 改成 `§r` 只是把「灰溢出」换成了「白溢出」。

**正例**：

```
金底：受到致命伤害时触发不死图腾效果（冷却 §95:00§6）
粉底：末影骰子自身或原版不死图腾触发后，瞬移到 §e16§d 格内安全地面
红底：雨中/水下受到的伤害 §c+40%%§r      ← 值串末尾，其后无文本，保留 §r 无影响
```

**值串末尾**（其后没有任何文本，含 `\n` 之后的文本也没有）的回落码不影响显示，保持原样即可；
其余位置的 `§r` / `§7` 与底色不符即违规（含用 `\n` 连接的多行值）。

---

## 2. 规则 1：非时间数值 → 黄 `§e`

**覆盖**：点数 / 层数 / 次数 / 格数 / 区间 / 百分比 / 星级 / 倍率 / 距离 / 费用 / 兑换比例。

**必须连同其前后紧邻的符号一起染黄**（符号与数值同色，不拆开）：

```
+3   → §e+3§7        -2   → §e-2§7
50%  → §e50%%§7      ×2   → §e×2§7
★3   → §e★3§7        1~10 → §e1~10§7
2:1（兑换比例）→ §e2:1§7
```

**回落**：值后按规则 0 写回本行底色码（灰底即 `§7`，金底即 `§6` ……）。

**不染色（结构标记，非取值）**：
- 行首列表序号 `1.` `2.` `3.` `4.`
- 标签序号 `第一诅咒`、`Curse 1`、`T4`
- 纯分隔符 `/`、`|`、以及连接词性质的 `+`（如 `攻击力 §e+3§7 + §9黑暗 (0:03)§7` 中间那个 `+`）

---

## 3. 规则 2：时间 → 蓝 `§9`

**形态**：`M:SS`（`1:00` `0:30` `5:00`）与 `N 秒` / `Ns` / `N seconds`。

**必须连同其前后符号一起染蓝**：

```
-10秒 → §9-10秒§7     180 秒 → §9180 秒§7
5:00  → §95:00§7      0:30   → §90:30§7
```

> 上例均在**灰底行**上；若该行底色非灰，末尾的 `§7` 要换成该行底色码（规则 0）。

**不等于时间**：`2:1`（兑换比例）按规则 1 走黄色。

`formatSignTime(int)` 输出 `§9M:SS§7`（灰底行专用）；秒数进入**非灰底**行时用 `formatMmSs(int)`
（只给 `M:SS` 文本，不带色码，由所在行的 `§9` 染色区与规则 0 的回落码负责着色）。

---

## 4. 规则 3：「<效果名> (<效果时间>)」整段 → 蓝 `§9`

当一段文本形如 `效果名 (时间)`（或 `效果名(时间)`），**名称与时间必须同色，整段蓝**，
不允许出现「名称灰 + 时间蓝」的割裂写法：

```
迅捷 (0:30)     → §9迅捷 (0:30)§7
黑暗 (0:03)     → §9黑暗 (0:03)§7
虚弱印记(5:00)  → §9虚弱印记(5:00)§7
Speed II (1:00) → §9Speed II (1:00)§7
```

**反例（禁止）**：`黑暗(§93秒§7)` —— 名称未着色，违反整段同色。

---

## 5. 规则 4：含 `%` 的文案必须走 `tt(...)`

`Component.translatable(...)` 走原版 `TranslatableContents`，其 `decomposeTemplate`
（`TranslatableContents.java:124-171`）按 `FORMAT_PATTERN = %(?:(\d+)\$)?([A-Za-z%]|$)` 把译文**拆成多个片段**：
`%%` 会变成独立的 `TEXT_PERCENT`（`FormattedText.of("%")`，**不含任何 `§` 码**），该片段只能继承行底色，
于是落在黄色区间里的 `%` 会掉色。

实测（1.2.0 星币锤 tooltip）：值 `...总数的 §e30%%§7 提升攻击力...` 经该路径后
`30` 渲染为黄 `(252,252,84)`、`%` 渲染为底色灰 `(168,168,168)`。

`ModTooltipHandler.translationString()` 先用 `String.format` 把 `%%` 收成 `%`，再整体放进
`Component.literal(...)`，颜色不丢。**凡 lang 值内含 `%%` 的行必须用 `tt(<key>)`**，
不得直接 `tooltip.add(Component.translatable(<key>)...)`。

---

## 6. 例外（优先级：例外 > 规则 3 > 规则 2 > 规则 1）

1. **负面红保留**：条目已用 `§c` 的，`§c` 优先，规则 1/2/3 不再改写它。
   - 负面效果条目：`§c中毒 (0:15)§7`、`§c虚弱 (0:15)§7`、`§c饥饿 (0:30)§7` 等保持红色
     （时间虽为时间，但不改蓝）。
   - 负面数值：红色行上的数值保持红色（如 `雨中/水下受到的伤害 §c+40%%§r`），
     行内回落码仍按规则 0 写回本行红 `§c`。
   - 状态警示行：`冷却中: §c%s§c 秒` 保持红色（该行行级底色即 RED）。
   - 名称类红色：`§c青之诅咒§7`、`§c全力攻击`、`§c瞬间伤害§7` 保持红色。
2. **行级语义色优先于行内**：整行 `.withStyle(RED)` 等不作为改写对象；行内仍需按规则着色的
   数值/时间照常着色（如 `效果牌冷却: §9%s 秒§7` 落在 RED 行上）。
3. **行内回落码一律按规则 0 处理**（v1 的「`§r` 保持原样」已作废）；仅值串末尾无后续文本者不动。
4. **`§f` 保留**：仅 `sign.key_hint`（白色行）。

---

## 7. 自检

改动后跑（全部退出码 0 才算通过）：

```bash
# 语言/手册一致性（两子项目各一次）
pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir neoforge-1.21.1/src/main/resources/assets/astral_dice/lang
pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir forge-1.20.1/src/main/resources/assets/astral_dice/lang

# tooltip 染色审计（R1/R1b/R2/R3 + R0 回落码、R4 tt() 路径）
pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1 --root .
```

审计脚本 `scripts/audit/tooltip_color_audit.ps1`（位置感知，能识别 `§e1§7 秒` 这类跨码形态）
同时从 `ModTooltipHandler.java` 解析「lang key → 行底色」映射，用于校验规则 0。
