# 主线开发 · 开工简报（astral dice）

> **用途**：WorkBuddy 没有「复制会话」功能（详见文末）。要在**同一工作空间**开一条「astral dice 主线开发」的会话，
> 做法是「新建任务」+ 把下面这段正文当作**第一条消息**发出去，新会话即带着正确上下文开工。

---

## — 简报正文（把这一段整段复制）

你是《星之骰戏》(`astral_dice`) 多加载器模组的主线开发代理。
工作空间 = `F:\MCProject\astral_dice_multiloader`（git 分支 `multi-1.20.1-1.21.1`，**发布线**）。

### 一、先读这些（本地不入库，动手前必须读）

1. `AGENTS.md` —— 权威规范，红线最全。
2. `.workbuddy/memory/MEMORY.md` —— 长期红线索引。
3. `.workbuddy/memory/2026-09-22.md` 及近期日志 —— 最新进展。
4. 需要时再读 `docs/` 下的兼容性文档（如 `docs/compat-26.1.2-neoforge.md`、`docs/i18n/`）。

### 二、仓库结构（单仓三线自包含，**无 common 源码**）

- `neoforge-1.21.1`　NeoForge 21.1.235 / Java 21
- `forge-1.20.1`　　Forge 47.4.10 / Java 17
- `neoforge-26.1.2`　NeoForge 26.1.2.109 / Java 25（2026-09-22 起转正式迁移线）
- 包名 `com.merlinkitsune.astral_dice`
- ⚠️ 另有开发线工作树 `F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）：
  **当前大量未提交改动都在那棵树**；两棵树各有自己的 `.workbuddy/memory`，会分叉。

### 三、最近已完成（别重做）

- 26.1.2 移植收口：贴图 / 模型 / 配方零缺失；`BaseSignItem` 离线补偿、`ModPayloads` 载荷注册等 3 处真实缺陷修复。
- 目标选择器的 ActionBar 提示（取消 / 超时）三线落地。
- 日语本地化 `ja_jp.json` 三线各 805 键全量生成（`temp/ja/gen_ja_lang.py`），静态 + 守门 + 1.21.1 实机验证通过。
- 三线 × 三语 lang **逐键对齐（diff = 0）**：删掉 26.1.2 单侧 3 处实现细节注释；
  `gen_ja_lang.py` 的 `neoforge-26.1.2` 覆盖块已删；记录见 `docs/i18n/lang-align-audit-2026-09-22.md`。

### 四、待办 / 待用户裁决

1. **键序是否对齐**：26.1.2 把 `hud.astral_dice.target_select.*`（6 键）与 `msg.astral_dice.target_select.*`（38 键）
   两段放在文件后段，双发布线在中段。收口须**同时重排该线 zh + en，再重跑日语生成器**。
2. **`ja_jp` 尚无 CHANGELOG 条目** —— 两份 CHANGELOG 都要补，中英条目数与顺序必须一致。
3. 26.1.2 的 `ModTooltipHandler.translationString` **仍未加 `escapeStrayPercents` 兜底**（发布线两处已加）
   ⇒ 该线新文案写裸 `%` 仍会崩客户端。
4. 单人化改造后的用例里仍有少量红项（LULU-SIGN / MAMUSHI-REG 等）待收口。
5. 5 个无流派筹码的生存获取途径待决策。
6. **收尾未执行**：两 CHANGELOG → 双版本构建 → 部署 → 本地提交（默认不 push）。

### 五、与用户的既有协作约定

- 对用户统一称呼「**用户**」。
- 新增/修改 tooltip 文案：**唯一基准 = 用户创建内容时给出的主动/被动原文，逐字照搬**；
  「补充说明 / 备注」与代理自选数值**不进 tooltip**。
- 改完按项目规矩跑守门再收尾：`tools/check_lang_sync.ps1`、`scripts/verify/*`、
  `scripts/audit/tooltip_color_audit.ps1`。
- 提交前必须 `git status --porcelain -uall`；**禁 `git add -A`**；默认只做本地提交、不 push
  （确需出网走代理 `127.0.0.1:7897`）。

**请先读完上面四份材料，用一句话向我确认你理解的主线目标与下一步，再动手。**

---

## 附：为什么不能用「复制会话」

已核对 WorkBuddy 官方文档（`任务管理` / `任务对话` 两页）：

| 入口 | 可用操作 |
|---|---|
| 任务行右键（或悬停 `⋯`） | 置顶 / 打开文件夹 / **重命名** / 保存到工作空间 / 分享任务 / 删除 / 归档 |
| 工作空间分组右键 | **新建任务** / 打开文件夹 / 重命名 / 从列表中移除 |
| 对话区顶部 | 对话内搜索 / 分享任务 / 历史提问 / 显示详情面板 |

**没有「复制任务 / 复制会话 / 派生会话」**。所以「同空间 + 同名副本」只能走
「新建任务 + 贴简报」这条等效路径。

### 本地状态（供参考，**不要手工改**）

本机会话状态分散在四处，缺一即半可见：

- SQLite `~/.workbuddy/workbuddy.db` → `sessions` 表（`id` / `cwd` / `title` / `custom_title` / `expert_id` / `mode` …）
- `~/.workbuddy/projects/<项目名>/<sessionId>.jsonl` → 对话正文
- `~/.workbuddy/projects/<项目名>/<sessionId>.meta.json` / `.file-rollback.ndjson`
- 按会话 id 派生的索引目录：`artifact-index` / `assistant-display` / `changes-detail` / `changes-index` /
  `file-history` / `file-tree-manifests` / `tasks/` / `workspace/sessions/`

⚠️ 手工伪造副本属**非受支持的写入**：可能造成会话半可见或元数据不一致，且`.file-rollback.ndjson`
会复制真实文件的回滚历史。**不建议**；需要的话请先明确确认。
