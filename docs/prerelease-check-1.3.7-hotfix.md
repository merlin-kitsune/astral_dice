# 1.3.7-hotfix 发布前核查报告

> 核查时间：2026-10-05（GMT+8）
> 核查对象：`multi-main` @ `a8dc51ee`（工作树干净；四线自包含：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`）
> **结论：功能与产物侧通过；存在 1 项发布阻塞（库未推送），必须先处理。**

## 1. 结论速览

| 维度 | 结论 | 关键证据 |
|---|---|---|
| 四线号源 | ✅ | `1.3.7-hotfix`(neo 1.21.1 / forge 1.20.1)、`1.3.7-beta.2`(26.1.2)、`1.3.7-alpha.2`(fabric) |
| 构建产物 | ✅ | 四线 `build/libs` 11:55 重建；开包核对 `version` 与文件名一致 |
| 内嵌前置库 | ✅ | 四线均内嵌 `starengine_lib` **1.0.12**（`META-INF/jarjar/*` 与 fabric `META-INF/jars/*`） |
| CHANGELOG | ✅ | 两份均含 `## 1.3.7-hotfix`（玩家可见变更 + 工程） |
| Release 玩家日志 | ✅ | `release/1.3.7/` 与 `release/fabric-1.3.7-alpha.2/` 各中英两份，均含 hotfix 小节 |
| 守门 | ✅ | 22/22 全绿（修后）；lang 841 / 841 / 831 / 839 三语一致 |
| 部署 | ✅ | root `build/libs`、`run/{1.21.1,1.20.1,26.1.2,fabric-1.20.1}/mods`、四个整合包（统一 11:56:39） |
| 双站配置 | ✅ | Modrinth 渠道派生口径已归一、`.modrinth/token` 就位；CF 上传脚本可用 |
| **CI 前置（库）** | ❌ **阻塞** | 见 §2 |
| 客户端实机表现 | ⚠️ 未覆盖 | 见 §5 |

## 2. 🚨 发布阻塞：CI 引用的库提交未推送

消费方 CI 的 `Checkout StarEngine Lib` 用**提交 SHA 钉值**：

```
ref: 4c653231750830824535d5debcfb36f9457278bb   # 库 1.0.12
```

实测（2026-10-05）：

- 本地库仓 `starengine_lib` 的 `main` = `4c65323`（库 1.0.12），**领先远端 1 个提交**；
- 远端 `refs/heads/main` = `8b24542`（库 1.0.11）；远端最高 tag = `1.0.11`；
- GitHub API 直查该 SHA ⇒ **422 `No commit found for SHA`**。

**后果**：现在 push `multi-main`，CI 会在 `Checkout StarEngine Lib` 一步直接失败 ⇒
**不打 tag、不建 Release、不上传 Modrinth**（消费方四线 build 与部署不受影响，仅 CI 链路断）。

**处置**：见 §6 发布顺序（**先推库**）。

## 3. 核查中发现并已修复（本次两笔提交）

| # | 问题 | 性质 | 修法 |
|---|---|---|---|
| 1 | `tools/verify_firearm_detection.py` 把近战「显式纳入清单」的两口饕餮之锅写成**四线同构**硬判据；2026-10-04 的联动裁决已把 `enigmaticlegacy:eldritch_pan` 从 `fabric-1.20.1`（`45405f0e`）与 `neoforge-26.1.2`（`4ec86207`）移除，而两个裁决提交都未碰守门脚本 ⇒ **该守门自 10-04 起恒 FAIL** | 判据滞后于裁决（**源码正确**） | 改为平台期望矩阵 `PAN_EXPECT`（缺期望项 = FAIL / 非期望项 = note）；补两条负控反证；登记 **KI-E4** |
| 2 | `tooltip.astral_dice.sign.nancy_lu_active` 的**英文**漏写「×2」（代码 `NancyLuSignItem.ACTIVE_BONUS_MULTIPLIER = 2`，即该牌费用 ×2 加攻；中/日文与手册条目本就写对） | 玩家可见文案错（英文玩家看到少一倍的数值） | 四线 `en_us` 补 `§e×2§7`（对齐既有 bonnie tooltip 句式） |
| 3 | 手册条目 `guide.entry.ren_sign.1` 仍含「（不会影响红心）」 | 文案口径（用户裁决） | 四线 × 三语删该括注，保留「5 黄心 = 10 点吸收」换算 |
| 4 | `tools/curseforge-description.md` 版本表停在 1.3.6、内嵌库写 1.0.11 | 发布配套滞后 | 更新为 1.3.7-* / 1.0.12（**CF 页面不可经 API 改，需手动粘贴**） |
| 5 | `README{,_ZH}.md` 版本表停在 1.3.6；顶部「本版更新」停在 **1.3.3** | 发布配套滞后 | 更新版本表 + fabric 引用；「本版更新」段重写为 1.3.7 |
| 6 | `release/starengine-lib/1.0.12/` 缺失（约定：目录名 = 库版本） | 机械依赖 | 新建中英 `PLAYER_CHANGELOG{,_ZH}.md`；否则 Modrinth **库项目**该版本说明**静默为空** |
| 7 | `KNOWN-ISSUES.md` §11 缺 2026-10-05 变更记录行 | 文档纪律（§0 第 4 条要求） | 补日期行 |

提交：`e7d875fa`（守门判据 + CHANGELOG / KI-E4 登记）、`a8dc51ee`（lang × 12 + 手册 + 6 份日志 + 3 份发布文档 + 库日志）。
**两项均不改变玩法数值。**

## 4. 已确认通过（含复核方式）

- **号源一致**：四线 `gradle.properties` 的 `mod_version` 与 jar 内 `mods.toml` / `fabric.mod.json` 的 `version` 逐字一致（开包核对）。
- **守门 22 项全绿**（`scripts/verify/*` 8 项 + `scripts/audit/tooltip_color_audit` + `tools/` 13 项），其中：
  - `check_lang_sync`：841 / 841 / 831 / 839 keys，三语 key 集完全一致；**修 2 后原先唯一那条结构标记 WARN 已消失**；
  - `audit_patchouli_keys`：四线合计 1540 处引用、**悬空 0**；
  - `verify_firearm_detection`：正例 PASS + 双负控（删 `forge-1.20.1` 期望项 ⇒ FAIL 且指名；给非期望线加回 ⇒ note + rc=0）；
  - `verify_chip_recipes`：筹码 61 / 一致 61 / 不一致 0；`verify_bountiful_pools`：objs 16 / rews 121；
  - `verify_resource_integrity`：三线物品模型 + 贴图 137/137；`verify_effect_icons`：四线 53 图标 100% 覆盖。
- **tag 现状**：`1.3.7` 已存在（远端 `551bdb73`）⇒ `-hotfix` 按规范**复用同一 tag 并刷新该 Release**，需**强推 tag**。
- **独立子代理只读复核**：7 项全通过（改动集合、判据非永真门、负控、22 项守门、文档表述与 `git log -S` 断言、CRLF），并另外抓出上表第 7 项。

## 5. 未覆盖 / 待实机验证（不阻塞发布，但请知悉）

| 项 | 状态 | 说明 |
|---|---|---|
| 主动技能键行为（J 不再取消选择） | 未实机 | 静态守门 `verify_selector_key_ownership` K1~K3 通过；**实机需前台键盘焦点**，本会话未验证 |
| 有黄心时 ActionBar 是否随状态条上抬 | 未实机 | 库 `1.0.12` 改动已入包并内嵌；同样需实机目视 |
| 溅射伤害多目标表现 | 未实机 | 递归防护以穷举 + 字节码判定「结构上不成立」，`verify_aoe_spread_invariants` 四线通过 |
| KI-F25②(b) 偶发 `CancellationException` | 未定位 | 已登记为**未定位观察项**（不得写成「非产品缺陷」） |

## 6. 发布顺序（建议）

1. **先推库仓** `starengine_lib`：`main` 推上 `4c65323` ⇒ 其 CI 自动打 tag `1.0.12` 并发布库 Release；
2. **确认可达**：远端 API 查 `4c65323` 返回 200（否则消费方 CI 仍会 422）；
3. **再推消费方** `multi-main`（含本次 `e7d875fa` / `a8dc51ee` 及既有未推提交）⇒ CI 四线构建 → 强推 tag `1.3.7` 刷新 Release → Modrinth 自动上传；
4. CI 同时产出 **fabric 预发布**（独立 tag `fabric-1.3.7-alpha.2`，标记 `--prerelease`）；
5. **CurseForge 手动上传**（4 个 jar；fabric 的 `--changelog` 需**单独一次调用**，因为该参数是全局的）；
6. 把更新后的 `tools/curseforge-description.md` **手动粘贴**到 CF 项目说明（该字段不可经 API 修改）；
7. 收尾回查：Modrinth 两项目版本列表 + CF 文件列表 + Release 正文（取自 `release/1.3.7/PLAYER_CHANGELOG*.md`）。

> ⚠️ 顺序不可颠倒：先推消费方 ⇒ 第一次 CI 必然红，需重跑。
