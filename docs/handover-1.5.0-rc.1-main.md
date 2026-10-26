# 交接正本 — 1.5.0-rc.1（multi-main）

> **本文件自 `1.5.0-rc.1`（2026-10-25）起继续为滚动交接正本**（原 `docs/handover-1.5.0-pre.6-main.md` 于同批 `git mv` 改名；pre.6 的批次、实机读数、遗留与坑**原样保留在本文档内**，不另建文件）。pre.5 及更早的批次一律回 `docs/handover-1.5.0-pre.5-main.md`（§0 速览 / §3 批次表 / §4 实机读数 / §5 遗留 / §6 坑 / §7 路径）。
> 数字均为 2026-10-24 快照；**开工先实读** `git log -1 --oneline` + `git status --short -uall` + 四线 `gradle.properties`。

## 0 速览（一屏）

1. 分支 `multi-main`，HEAD = 本批提交（`release(rc.2): 1.5.0-rc.1 -> 1.5.0-rc.2`，含调酒师治疗按张数 + 多萝西【交际花】双版本实机）。**已 push + CI 自动建 tag**（2026-10-25 23:1x，用户下达「执行 push」）：`git push origin multi-main` ⇒ `8dcdb715..13a45619`，`rev-list --count origin/multi-main..HEAD` = **0**；CI（`.github/workflows/build.yml` 的 auto-tag 步骤）自动建注解 tag **`1.5.0-rc.2`**（指向 `13a45619`）并发布 prerelease（附件 = 刚构建的 P0 两个 rc.2 jar，正文取 `release/1.5.0-rc.2/`）。前置库仓 `origin..HEAD` = **0**。
2. 四线 `mod_version = 1.5.0-rc.2+<加载器后缀>`；四线 `starengine_version = 1.1.0`（前置库同批由 `1.1.0-pre.2` 转**正式版**，库内 Java 源码零改动）；硬前置区间随之展开为 `[1.1.0,2.0.0)`。
3. 本批收编 **Kimi 上一轮四批未提交改动**（member-detective / misaki 立牌 / 筹码商店刷新源 / 规则分派），并完成 **DSH 三语语言同步** + **地雷女备注区染色修复**。
4. 判据：P0 两线 `:classes` `BUILD SUCCESSFUL in 4s`；`check_lang_sync` EXIT=0；`audit_patchouli_keys` 悬空 0；`verify-split` EXIT=0；三道专项 python 判据 PASS 26/12/14；`verify_chip_shop_ui` `CHIPUI_TOTAL_FAIL: 0`。
5. **rc.1 产物已重建并投放**（2026-10-25 15:3x，`:neoforge-1.21.1:build :forge-1.20.1:build` 触发 `pushToDevRun/pushToGame/pushToRootBuild`）——四个目标（`run/1.21.1/mods`、`run/1.20.1/mods`=dev named、两个 P0 整合包）逐处 sha256 **MATCH**；**前置库 jar 成对换成 `1.1.0`**（四处旧 `1.1.0-pre.2` 已删），读数见 §2 与 §4.1f。
6. 本批实机：**星光兑换 5 组 + 筹码商店刷新源 / 文案（38 tooltip + 21 手册）/ `ame_note` 染色 + 强化拒止物品形态 7 项 + owner 闸门四态，双版本全 PASS**（2026-10-25，§4.1 / §4.1b / §4.1c）。**rc.1 版本升级已双版本冒烟实机 PASS**（§4.1f-a：两线 mod/库版本号正确、`Missing or unsupported mandatory dependencies` 计数 0、1.20.1 放置强化拒止实体 hp 50）（只做构建 / 打包 / 投放 / 静态判据，§4.1f）。`scripts/test/cases/CHIP-REGEN-*.json`（等 tick 自动 +1）随 ticker 删除失效，**已按用户裁决退役（两文件删除）**（§5-3）。
7. 规则分派已落地：根 `AGENTS.md` 为 41 行入口，Kimi → `docs/agent/AGENTS-kimi.md`，DSH → `docs/agent/AGENTS-dsh.md`，Codex → `docs/agent/AGENT-SPLIT.md`（只读）。
8. ~~未处置：陈旧锁 `F:\MCProject\.agent-locks\kimi.lock`~~ → **已结案（2026-10-25 用户裁决「删」）**：实测 `.agent-locks` 目录**已空**（`Get-ChildItem -Force` 无条目），`kimi.lock`（原 `PID=26580` / `TASK=misaki-chip-shop-balance-p0`）已不存在，无需再删；该目录从此只放当轮活锁。
9. **HUD 攻击力口径已统一**（2026-10-25，§4.1g）：攻击修饰器链上 23（neo）/ 24（forge）处客户端模板守卫收敛为 `skipOnClient(ctx)` + 显示口径标记 `DiceCombatContext.displaySnapshot` ⇒ HUD 现在含全部攻击力修饰器，只排除手持武器与骰战随机点数；新增守门 `python tools/verify_hud_attack_parity.py` ⇒ `GATE: PASS (21 项全部满足)`，两线已 `:build` 并把六处产物刷到新 jar；**已双版本实机 PASS**（§4.1i：装电流剑 + 充能 8 ⇒ client 显示 3；清充能回 1；主手持剑仍 3）。
10. **防御力折算已改为 1:1**（2026-10-25，§4.1h）：所有「防御力 +X」的站点改走单一入口 `armorValueForDefensePoints(points, side)`（= 点数 ÷ 库护甲系数，玩家 ≈3.333 护甲/点、怪物 2.5）——此前写死 `× 2.0` 只兑现 60%（玩家）/ 80%（怪物），属平衡性红线；同时把 12 份指南 lang 的基数 4 改为 2。新增守门 `python tools/verify_defense_unit_parity.py` ⇒ `GATE: PASS（42 项全部满足）`（新增 D9 取整口径），两线 `:classes` 通过、`:build` 已重建并投放四目标，**双版本实机 PASS**（§4.1i：+8 防御力 ⇒ 防御力 10；同批修掉 `getArmorValue()` 取整吞点）；玩家侧实际强度是上一版的 **×1.667**。
11. **创造栏收尾（2026-10-25，§4.1j）**：强化拒止**物品**已从创造栏与 JEI 隐藏（JEI 物品列表直接取自创造栏，**未加任何 JEI 代码**）；筹码在组序不变前提下改为**组内稀有度升序**（67 个零增删）。守门 `tools/verify_creative_tab_chip_order.py` PASS + 变异测试证非假绿；产物已重建并投放四目标（neo `366D68C8…` / forge reobf `FD7B01D7…`），jar 内 `ModItems.REPULSION` 引用 0 处；**未实机**。
12. **护法立牌减费卡面（2026-10-25，§4.1k）**：佩戴 `misaki_sign` 时商店货架上的「名刀·嘎呜切」改画 `attack_card_meito_discount.png`（纯客户端只读 Curios 缓存、缺素材自动回退），价格与购买链路零改动；守门 `python tools/verify_meito_discount_face.py` ⇒ `GATE: PASS（D1-D6）` + 变异测试证非假绿；产物已重建并投放四目标（neo `59E12108…` / forge reobf `33717FA9…`），**双版本实机 PASS（2026-10-25 18:12–18:23，见 §4.1k）**。

13. **发布收口（2026-10-25 18:2x–18:5x，§4.1l）**：`1.5.0-rc.1` 与前置库 `1.1.0` **已提交并 push**（本体 `6ee75a34`、库 `16b48c4`，两仓 `origin..HEAD` 均 **0**）；**CurseForge 已上传 6 个文件** —— 本体两线 `beta`（neo fileID `9320026` / forge `9320028`），库四平台 `release`（`9320029` / `9320030` / `9320031` / `9320032`）；`tools/curseforge.json` 的 P0 两线**补登 `starengine(1755415)=requiredDependency`**（lib 自 1.1.0 起不再内嵌，此前 CF 关系缺这一条 ⇒ 玩家会直接缺库起不来）。
14. **玩家更新日志（1.3.7→1.5.0 全量）**：新建 `release/1.5.0-rc.1/PLAYER_CHANGELOG_ZH.md`（218 行 / 24,201 B）+ `PLAYER_CHANGELOG.md`（242 行 / 23,492 B），**全部 CRLF、裸 LF 0**；CF 本体两文件即用英文版（23,134 字符）作 `--changelog`。

15. **`1.5.0-rc.2` 双版本实机 PASS（2026-10-25 21:2x–21:3x，§4.1o）**：【交际花】在 1.21.1 / 1.20.1 双线实测成立 —— 队友 ATK **1→2** 且拿到效果（`dur=1200` / `amp=0` / 可见）、重复获得**只刷时长不叠层**、主动窗口结束后队友效果**不回收**（`windowEnd=0` 时队友仍余 675t）、施法者自身 `dorothy_bond_attack` 按窗口 +1（窗口结束归 0）；**节流确已删除**（同一窗口内第二个队友 40 tick 内即入账，旧 10 秒节流会挡 200t）；客户端效果名 = **交际花**、图标 448 B。构建 `BUILD SUCCESSFUL in 13s`，四投放点均为 rc.2 且库成对、jar 内 `version=1.5.0-rc.2+<后缀>` / `META-INF/jarjar/`=0 / `DorothySocialiteEffect.class` 与图标各 1。

16. **`1.5.0-rc.2` 已上传 CurseForge（2026-10-26 11:4x，§4.1p）**：本体 P0 两线 **beta** 渠道 —— neo `fileID=9329558` / forge `9329560`，均 `HTTP 200` 且随上传带 relations；次源判据 = ForgeCDN `HEAD` 200 且 `Content-Length` 与本地 jar 逐字节相等（2,589,502 / 2,634,295）。cfwidget 镜像仍滞后（38 文件、无 rc.2），不作为失败依据。

17. **玩家侧更新日志补齐「本版变化」（2026-10-26 11:5x，§4.1q）**：`release/1.5.0-rc.2/` 三份（ZH / EN / MCMOD）此前是 rc.1 的**整份拷贝**（只在正文各处被就地改成 rc.2 口径），本轮各新增一节「本版（rc.2）相对 rc.1 的变化」= 多萝西【交际花】（同伴 ATK +1 / 固定 1:00 / 与窗口解耦 / 不叠层 / 删 10 秒节流）+ 调酒师治疗按张数（2 / 4 / 6）+ 立牌中文名订正（熊猫人→上班族 · 提尔→教主 · 酒保→调酒师）；MCMOD 版 h2 编号顺延（原 一~八 → 二~九）。md 两份全 CRLF、MCMOD 版按既有口径仍是 **LF-only**。**纯文档，无代码改动、无实机**。

## 1 环境与路径

路径与命令**沿用 pre.5 正本 §1**（Windows 原生：git bash 的 `git`、`pwsh -NoProfile -File`、`JAVA_HOME=C:\Program Files\Zulu\zulu-21`、工作树直接 `gradlew.bat`、测试台 `scripts\test\mt.ps1`）。两条补充：

- 仓库多了一整套**规则分派**文件：根 `AGENTS.md`（入口/红线/索引）· `docs/agent/AGENTS-kimi.md` · `docs/agent/AGENTS-dsh.md`（旧根逐字节留存 + 开头 DSH 段）· `docs/agent/PROJECT-REFERENCE.md`（工程拓扑）· `docs/agent/AGENT-SPLIT.md`（Codex/DSH 分工 + §六 Kimi 兼容）· `AGENTS-full.md`（逐批历史，未变）。
- 锁：`F:\MCProject\.agent-locks\` 现**为空**（2026-10-25 实测，`kimi.lock` 已不存在）；开工建 `dsh.lock`、收尾删除，动工作树前先看该目录。

## 2 版本与坐标

| 项 | 值 | 落点 |
|---|---|---|
| neo `mod_version` | `1.5.0-rc.2+neoforge_1.21.1` | `neoforge-1.21.1/gradle.properties` |
| forge `mod_version` | `1.5.0-rc.2+forge_1.20.1` | `forge-1.20.1/gradle.properties` |
| 26.1.2 `mod_version` | `1.5.0-rc.2+neoforge_26.1.2`（源码 bump，**P1 不构建不验证**） | `neoforge-26.1.2/gradle.properties` |
| fabric `mod_version` | `1.5.0-rc.2+fabric_1.20.1`（源码 bump，**P1 不构建不验证**） | `fabric-1.20.1/gradle.properties` |
| `starengine_version` | `1.1.0`（四线，**正式版**）；fabric 另有 `starengine_version_range=>=1.1.0` | 各线 `gradle.properties` |
| 硬前置区间 | `[1.1.0,2.0.0)`（模板插值 `[${starengine_version},2.0.0)`，改 `gradle.properties` 一处即随动）；`META-INF/jarjar/` 计数 **0** | `neoforge-1.21.1/src/main/templates/META-INF/neoforge.mods.toml:143` · `forge-1.20.1/src/main/templates/META-INF/mods.toml:157` |

**产物现状（2026-10-25 21:3x 重建，`1.5.0-rc.2`；源码 = rc.1 + 调酒师治疗按张数 + 多萝西【交际花】）**：
neo `astral_dice-1.5.0-rc.2+neoforge_1.21.1.jar` **2,589,502 B** / sha256 `b562974217bc726bff838e03fded6a77159e683484da01df44e2d2f9155687c6`（`run/1.21.1/mods`、仓库根 `build/libs`、「狐の航空学 Voxy Edition」**同一份**）；
forge reobf `astral_dice-1.5.0-rc.2+forge_1.20.1.jar` **2,634,295 B** / `3c7b089de70c0d7d6b0a7ed19ff06655c2e54d6b7a1501b7120aefee42e3dba8`（`build/libs` + 「1.20.1-Forge 模组测试」）；
forge dev **named** **2,604,839 B** / `f11a613d1ee441f2e309360212fb49760cfe722ce659804ba348049e942034aa`（`run/1.20.1/mods`）。
四目标 21:3x 逐处核过：每处**只有**一个 `astral_dice-*.jar`（rc.1 旧 jar 已被投放任务覆盖，无并存）；jar 内判据（两线两件实测）：`version="1.5.0-rc.2+<后缀>"`、`META-INF/jarjar/` 计数 **0**、`com/merlinkitsune/astral_dice/effect/DorothySocialiteEffect.class` 与 `assets/astral_dice/textures/mob_effect/dorothy_socialite.png`（448 B）各 **1**；两 P0 整合包内库 jar 仍在（neo `starengine-neoforge-1.21.1-1.1.0.jar` `62c92595…` / forge `starengine-forge-1.20.1-1.1.0.jar` `73da2e59…`）。
> 上一轮（2026-10-25 15:3x，`1.5.0-rc.1`）读数（历史，已被本批覆盖）：
neo `astral_dice-1.5.0-rc.1+neoforge_1.21.1.jar` **2,586,701 B** / sha256 `65a22d700926fbddedf5ced803930c9ef74a709426a614a3f3f311e410450610`（`run/1.21.1/mods` 与「狐の航空学 Voxy Edition」同一份）；
forge `astral_dice-1.5.0-rc.1+forge_1.20.1.jar` **2,631,740 B** / `7ad926ca072b1c4c9e87218b366da4e70de0746fb2dd218c3ca2b2ff08b2d8dd`（reobf → 1.20.1-Forge 模组测试）；
forge dev **named** **2,602,271 B** / `926800675c850d2fdea51bd94439e66f6ec96c766629e2104624907e46878123`（`run/1.20.1/mods`）。
四目标 2026-10-25 15:3x 逐处 sha256 MATCH；jar 内判据（两线两件实测）：`version="1.5.0-rc.1+<后缀>"`、`modId="starengine"` 的 `versionRange="[1.1.0,2.0.0)"`、`META-INF/jarjar/` 计数 **0**。
> 上一轮（15:5x，pre.6 收尾）读数：neo 2,586,610 B / `4686ec6d…c53fb4`；forge reobf 2,631,683 B / `dbccaf2c…2535cd0`；forge dev 2,602,214 B / `19bff331…43f2e45` —— 已被本批覆盖。
**前置库 jar（同批换成 `1.1.0`，与 mod 成对，四处旧 `1.1.0-pre.2` 已删）**：
neo `starengine-neoforge-1.21.1-1.1.0.jar` 98,734 B / `62c92595b012929d2fba6098cc2d71e6ab2a9a14ddaac52869e2bbec6b476ac8`（`run/1.21.1/mods` + 「狐の航空学 Voxy Edition」，同一份）；
forge reobf 103,605 B / `73da2e599e60dab047e3640eb396f1db6f6eedd5a0dd95494afde471f43af304`（「1.20.1-Forge 模组测试」）；
forge dev named 102,013 B / `e280aeab6c4b37c43468f7cce32a8bf64c70b6bdbcd2203f6c5600776f987e0e`（`run/1.20.1/mods`）；
另 fabric 112,634 B / `4f5cb0eca44c48840355ecaca4a752f0de1c3d0d89c8363b46c3a0283f3e6955`、26.1.2 98,970 B / `625057e19c114f7f1f452ba176a588d8e58a303b9d48002b6438c031b7dff8c8` 已构建并 `publishToMavenLocal`，**不投放**（P1 不验证）。

## 3 本批批次表

| 提交 | 主题 | 关键落点 |
|---|---|---|
| `e2aed527` | bump pre.6 + 收编 Kimi 四批 + 三语语言同步（53 文件，+994/−981） | 见下 7 条 |
| `1ff14106` | feat(starlight)：星光兑换改为「装备时发放 + 每次兑换后按佩戴补星」（24 文件 +1380/−217） | `resource/ResourceConversion`（兑换后 `replenishOnExchange`）· `item/StarLightManager`（`grantedStarlight` 4bit 账本 / `grantStarlightOnEquip` / `revokeStarlightOnUnequip` / `replenishOnExchange`）· 四枚星光筹码 `onEquip`/`onChipUnequip` · 经商立牌 +50% 保留在 `CardShopMenu#tryExchange` · 新增 `tools/verify_starlight_exchange_refill.py`（`GATE: PASS (196 项)`） |
| `9321d624` | feat(repulsion)：强化拒止改为「近战 5 次回收成物品形态」（32 文件 +973/−172） | 新增 `item/RepulsionItem`（三份状态 + 耐久条）/ `item/RepulsionPlacement`（卡与物品共用落点解析）· `entity/RepulsionEntity`（`MAX_MELEE_HITS = 5` / `recoverIntoItem` / `restoreState` / NBT `RepulsionMeleeHits`）· `component/ModDataComponents` 三个 INT 键 · 两线三语 +11 键 · 贴图 + 生成模型 |
| 本文件所在提交（追加） | fix(guide)：战斗系统介绍页版本号 `2.0.0`→`1.5.0` + 条目图标骰子→铁剑（10 文件） | 两线 × 三语 lang（`guide.entry.battle_system_changes.1`）· 两线 `getting_started/battle_system_changes.json` 的 `"icon"` · `scripts/test/cases/PMFIX-TIP-1.20.1/1.21.1.json:221` 陈旧断言 |
| `669b0150` | fix(repulsion)：耐久条改原版工具口径（6 文件 +14/−10） | 两线 `item/RepulsionItem#isBarVisible`（满耐久不画条）+ CHANGELOG 双文件 + 本文件 + session-context |
| 本文件所在提交（追加） | docs(handover)：强化拒止双版本实机 7 项判据 + 实机侧方法可见性坑 | 本文件 §4.1c / §5-8 / §0-6 |
| 本文件所在提交 | docs：Kimi 交接单 + 工程参考 + pre.6 正本 + 新会话上下文 | `docs/handover-2026-10-24-kimi-takeover.md` · `docs/agent/PROJECT-REFERENCE.md` · 本文件 · `docs/handover-new-session-context.md` |
| `16b48c4`（**前置库仓** `F:\MCProject\starengine`） | release(lib)：`1.1.0-pre.2` → **`1.1.0`** 正式版（8 文件 +63/−32） | 四子项目 `gradle.properties`（`lib_version` / `mod_version`）· `README.md`/`README_ZH.md`（坐标表 + 依赖示例 + 当前版本）· `CHANGELOG.md`/`CHANGELOG_ZH.md` 新增 `## 1.1.0` 小节 · 四平台 `publishToMavenLocal` |
| 本文件所在提交（追加） | release(rc.1)：`1.5.0-pre.6` → **`1.5.0-rc.1`** + 前置库坐标 `1.1.0` | 四线 `gradle.properties`（`mod_version` / `starengine_version` / fabric `starengine_version_range`）· 两线 toml 模板注释（预发布期「不要写 `[1.1.0,2.0.0)`」改成正式版口径）· `.github/workflows/build.yml:105` 库 ref SHA `6d2a96d…`→`16b48c4…` · `README*.md`（版本列 `1.5.0-pre.5`→`rc.1`、库版本 `1.1.0`）· `CHANGELOG*.md`（库版本串）· 本文件 `git mv` pre.6→rc.1 + §0/§2/§3/§4.1f · `docs/handover-new-session-context.md` · 5 处旧文件名引用 |
| 本文件所在提交（追加） | feat(hud)：攻击力显示口径统一（4 文件 + 新增守门脚本） | 两线 `combat/DiceCombatContext`（+`displaySnapshot`）· 两线 `combat/DiceCombatModifiers`（`skipOnClient` 收敛 23/24 处模板守卫 · 攻击卡在显示口径下不掷 · `attackPowerBase` 5 参重载）· 新增 `tools/verify_hud_attack_parity.py`（H1–H6，`GATE: PASS (21 项)`） |
| 本文件所在提交（追加） | feat(shop)：护法立牌减费卡面（8 文件 +228/−6） | 两线 `screen/CardShopScreen#cardFaceTexture`（非名刀/未佩戴/素材缺失回退原图）· 两线 `textures/gui/shop/card/attack_card_meito_discount.png`（904 B）· `tools/verify_meito_discount_face.py`（D1-D6）· CHANGELOG 双文件 · 本文件 §0-12 / §4.1k / §5-Minor 7 |

| `6ee75a34` | docs(release)：1.5.0-rc.1 玩家更新日志（中英，2 文件 +460/−0） | `release/1.5.0-rc.1/PLAYER_CHANGELOG_ZH.md`（1.3.7→1.5.0 全量：安装与前置 / 新增内容 / 战斗系统重做 / 平衡与数值 / BUG 修复 / 文案与手册 / 许可变更）+ 英文镜像 |
| 本文件所在提交（追加） | chore(tools)+docs：CF 关系补 starengine + 发布收口 | `tools/curseforge.json`（P0 两线 +`starengine(1755415)=requiredDependency`）· 本文件 §0-13/14 · §4.1l · §3 两行 · §5-Minor 8 · §6 坑 23/24 |

`e2aed527` 的七条内容：

1. **规则分派（Kimi）**：根 `AGENTS.md` 收为 41 行入口（2,452 字符）；`docs/agent/AGENTS-dsh.md`（旧根逐字节留存，原 sha256 `c018c8b6e116ef05f2c24106c97a5f3622ff5a30ab7b41cfa97810b6f4bb3e79`）开头加 DSH〈using-superpowers〉段；新建 `docs/agent/AGENTS-kimi.md`；`docs/agent/verify-split.py` 重写四项；`AGENT-SPLIT.md` 追加 §六（Kimi 锁兼容）；`docs/agent/README.md` 一行同步。
2. **member-detective（Kimi）**：`MemberRecommendationChipItem` `HAND_CARD_LIMIT = 5`（主背包 items + 副手计数，不含 Curios）；`BonnieSignItem` 改为先 `MarkManager.apply(target)` 再按 `markLevel * 2` 返币；新增 `tools/verify_member_recommendation_hand_gate.py`(14) / `tools/verify_bonnie_active_order.py`(12)。
3. **misaki 立牌（Kimi）**：两线 `MISAKI_SIGN` `epic()`；配方 `STAR_PLATE`（datagen + 生成 JSON）；死亡不清剑气（`clearSignData` 卸下清零保留）；`astral_rews.json` `EPIC`；新增 `tools/verify_misaki_balance.py`(26)。
4. **筹码商店刷新源（Kimi）**：两线删 `shop/ChipShopRegenTicker.java`；`needsReroll` 去 `diceKey`；`withRefreshGranted()`（不重置、封顶 5）；唯一授予点 `event/AnvilUpgradeHandler`（取走升星成品 +1；升星花费 0/1/2★=15/20/25 与 preview 共用）；死亡保留 neo `.copyOnDeath()` + forge `AstralData` 白名单 `ModAttachments.CHIP_SHOP_STATE.name()`；`verify_chip_shop_ui.ps1` 加 G18c..G18h。
5. **三语语言同步（DSH，用户 m07951/m08212 下达）**：用户手改 39 条（38 `tooltip.*` + `msg.astral_dice.card_shop_no_starlight`）为权威；按新语义改写 21 条 `astral_dice.guide.entry.*` 手册正文；`zh_cn.json` 同步到 forge 线（按 forge 自身键序只换 60 个值，**禁止整文件复制**）；`en_us.json`/`ja_jp.json` 两线各 60 键重译，格式保真 234/234 §码 · `%%` 10/10 · `%s` 6/6 · \n 39/39 · `" | "` 3/3。
6. **`tooltip.astral_dice.sign.ame_note` 染色（DSH）**：备注区底色 `§d`（`event/ModTooltipHandler#addSignNoteLines`），补 7 对染色（`§e` 数值 / `§9` 时间 / `§d` 回落），正文未改（仅删一多余空格）；6 文件（两线 × 三语）。`tooltip_color_audit` 中 `ame_note` 40 → 0。
7. **版本 bump（DSH）**：四线 `mod_version` → `1.5.0-pre.6`。

## 4 实机读数

### 4.1 pre.6 星光兑换（2026-10-25）—— 双版本实机 5 组判据全 PASS

判据正本：`resource/ResourceConversion#starlightToStarCoins`（两线同文件 L29-48）+ `item/StarLightManager#grantStarlightOnEquip / revokeStarlightOnUnequip / replenishOnExchange`（neo L91/L106/L140；forge L94/L109/L143）+ `shop/CardShopMenu#tryExchange`（neo `:551`、forge `:553`，入口 = `clickMenuButton(player, 17)`）。
驱动面：`pwsh -NoProfile -File scripts\test\mt.ps1 --version <线> --phase env|launch` + AutoJS server `eval_js`（**两线都有 AutoJS**：1.21.1 由 `MT_ENV_AUTOJS` 投放；1.20.1 走 `run\1.20.1\mods\AutoJS-forge-1.20.1-1.0.0-dev.jar`，启动日志 `MT_LAUNCH: OK — AutoJS 端点已就绪`）+ 探针 `/astralshop {reset,star,openkeep,exchange,close}`（输出去 `run\<线>\logs\latest.log` 的 `[CHAT] AP_<TAG>_*`，**不是** `kubejs_log`）。

| # | 场景 | 1.21.1（neo）读数 | 1.20.1（forge）读数 |
|---|---|---|---|
| A | 下界之星骰（4 槽）装四枚星光筹码（锤 5 + 卡少 4 + 卡多 7 + 卡无限 3）后点兑换 | `AP_S2_EXCH:ret=1:star=19->20:bal=0->9`（19 扣 18 ⇒ 9 枚，余 1，再按佩戴补 19 回 20） | `AP_T1_EXCH:ret=1:star=19->20:bal=0->9` |
| B | 60 秒共享冷却：立刻再点一次 | `AP_S2_EXCH:star=20->20:bal=9->9`（`cdBefore=10068 / now=8868` ⇒ 1200t） | `AP_T1_EXCH:star=20->20:bal=9->9`（`cdBefore=2438 / now=1238`） |
| C | 无筹码时兑换 5 点 | `AP_S3_EXCH:ret=1:star=5->1:bal=0->2`（**无补星**） | `AP_T2_EXCH:ret=1:star=5->1:bal=0->2` |
| D | 经商立牌 `parunan_sign` +50%：20 点兑换 | `AP_S4_EXCH:ret=1:star=20->0:bal=1->16`（10 枚 + `max(1,(int)(10*0.5))`=5） | `AP_T3_EXCH:ret=1:star=20->0:bal=1->16` |
| E | 封顶边界 + 账本严格扣除（银行卡-余额多 7，cap=32） | `star=30` → 装备 → `32`；卸下 → `30`（只扣**实际** 2） | `star=30` → `32` → `30`（同左） |

补充实读：卸下四枚筹码后 `star` 由 20 回落到 **1**（按账本扣 19）；全程背包内 `astral_dice:star_coin` 计数 **0**（钱直接进钱包 `StarEngineEconomy.getBalance`）；两线 `CardShopMenu` 的兑换按钮都返回 `ret=1` 且 `star/bal` 前后对照相符。

### 4.1b 筹码商店刷新源 + 文案 + `ame_note` 染色（2026-10-25）—— 双版本实机全 PASS

判据正本：`shop/ChipShopCatalog.java:89-92`（`REFRESH_INITIAL=3` / `REFRESH_MAX=5` / `REFRESH_REGEN_TICKS=36000`，`:89` 注释「不得据此常量新增自动 +1」）+ `shop/ChipShopState.java:174-178`（`withRefreshGranted` = `min(MAX, left+1)`，唯一调用点 `event/AnvilUpgradeHandler.java:91`，日志 `CHIP_SHOP_REFRESH_GRANTED` 在 `:93`）+ `shop/ChipShopMenu.java:428-446`（`handleRefresh`）+ `shop/ChipShopOpener.java:39-62`（`open`）。
驱动面：`mt.ps1 --phase env|launch` + AutoJS server `eval_js` 直调 `ChipShopOpener.open(p)` / `ChipShopMenu#handleRefresh(p)` / `ChipShopState.get(p)`；铁砧走探针真实结算链 `/astralprobe anvilstar "astral_dice:golden_dice" ANV`；死亡用 `/kill @s`。

| # | 判据 | 1.21.1（neo） | 1.20.1（forge） |
|---|---|---|---|
| R0 | 初值 | `refreshes=3 anchor=0 offers="-"`（tick 1015） | `refreshes=3 anchor=0 offers="-"`（tick 1955） |
| R1 | 时间流逝不自增 | 1642 t（≈82 s）后仍 `3 / 0 / "-"` | 首次刷新前仍 `3 / 0`；`ChipShopRegenTicker` 两线 jar 内均 **0 条目**（neo 2002 / forge dev 2016、reobf 2016） |
| R2 | 刷新消耗 + anchor 首写 | `3→2→1`，`anchor=38865`（=2865+36000）首刷写入、二次不变；日志 `CHIP_SHOP_REFRESH refreshes=2/5`、`1/5` | `3→2→1`，`anchor=37955`（=1955+36000），同左 |
| R3 | 升星取走 +1 | `1→2`（同 tick）；日志 `CHIP_SHOP_REFRESH_GRANTED player=Dev star=0->1 coins=18 refreshes=2/5` | 同左（`1→2`、`refreshes=2/5`） |
| R4 | 铁砧真实结算链 | `AP_ANV_TAKE:clicked:30:20` / `AP_ANV_RESULT:astral_dice:golden_dice:1` / `AP_ANV_STAR:0:1` / `AP_ANV_DONE` | 同左（逐字相同） |
| R5 | 死亡不自愈 | `kill @s` → 4 s 后重生 `refreshes=2`、`anchor` 不变 | 同左（`refreshes=2`、`anchor=37955`） |

文案（本批 **60 键 = 38 `tooltip.*` + 21 `astral_dice.guide.*` + 1 `msg.astral_dice.card_shop_no_starlight`**，两线 lang 逐字相同；old/new 落盘 `temp/lang_changed_neo.json`）：

- tooltip：两线各 dump 全 **156** 个 `astral_dice` 物品的 `Screen.getTooltipFromItem` 渲染行（保留字面 § 码），与 lang 逐键比对 ⇒ **37/38 命中，两线一致**。唯一未显示 = `tooltip.astral_dice.sign.misaki_enigmatic`：`event/ModTooltipHandler.java:686-700` 以 `ModList.get().isLoaded("enigmaticlegacyplus")` 为条件，两线 dev 均未装 ⇒ **正确地不出现**。
- 换行口径：`komachi_passive` 的「␠␠缩进」渲染成 `- ` 项目符；`investigation_desc` 续行**去掉前导两空格且无项目符**（渲染原文 `§dI：自身与施加者获得 §9隐匿 (1:00)§r`，不是缺陷）；其余多行键逐行渲染。
- 两线 tooltip 全量对照：**11 个物品仅行序不同、行集完全相同**（`curios.modifiers.*` + 属性行在 neo 前置 1 行块、forge 后置），属平台差异、非本批回归（`moto_helmet ×3` / `sandwich ×3` / `speed_skates ×3` / `obsidian_dice` / `perpetual_motion_chip`）。
- 手册：21 键两线均 **21/21** 解析成功，17 条逐字一致、4 条只差 `%%`→`%`（`battle_system_changes.3` / `komachi_sign.2` / `rin_sign.1` / `extra_battery_chip.1`），**0 条含显式 `\n`**（折行由 Patchouli 自动）。
- `ame_note` 染色：两线逐字一致 —— `§d`（备注体）+ `§e`（数值）+ `§9`（时间），行首区段前缀由 `addSignNoteLines` 注入，符合 tooltip 染色规则；证据 = 上述渲染行原文（未取截图）。

### 4.1c 强化拒止物品形态（2026-10-25）—— 双版本实机 7 项判据全 PASS

物品图标 `textures/item/repulsion.png`（两线同一份）= **用户提供的美术稿 `images/强化拒止物品.png` 逐字节复制**（32×32 RGBA / 344 B / sha256 `c12248b7431f087b14f6c175aeef3e450d30a8635c1fdfe83db0963d4522bf9f`，2026-10-25 用户裁决；与效果牌图标「`images/强化拒止.png`（32×32）逐字节复制」同一口径，见 `docs/features/repulsion-card-spec.md` §4）。32×32 直接可用：MC 按 UV 把贴图铺满 16×16 的 quad ⇒ 纹素密度 2×，比 16×16 更清晰。

> 历史（已被上面取代）：上一轮「用实体模型 + 实体贴图生成」的 12×12 正视板 + 2×3 短柄稿（181 B / `dcfe5129…`）作废。取证结论留档备用：实体 `textures/entity/repulsion.png` 头块北面 UV = (4,10)-(28,34)（128 空间）、杆子北面 = (2,2)-(50,6)、`LayerDefinition(...,64,64)` ⇒ 128 PNG 里 2 倍纹素密度；按实体 `HEAD_TWIST=+45°` 渲染成菱形时牌面对角只有 ~9 px、蓝边框塌成亚像素（三版对比全不可读），故当时改取正视口径。

**耐久显示（= 关联血量）已在两处**（2026-10-25 复核 + 双版本实机）：

- 物品栏 / 快捷栏**耐久条**：`item/RepulsionItem` 覆写 `isBarVisible`（**原版工具口径**，2026-10-25 用户裁决：`return getDurability(stack) < MAX_DURABILITY;` —— 满耐久不显示、掉血后才出现，等价于 `Item#isBarVisible` 的默认实现 `stack.isDamaged()`）、`getBarWidth = max(1, round(13 × health/50))`、`getBarColor = Mth.hsvToRgb(ratio/3, 1, 1)`（与 `ItemStack#getBarColor` 同一渐变）。合法性有源码判据：1.21.1 `GuiGraphics#renderItemDecorations` 直接读这三个钩子（`if (stack.isBarVisible()) { int l = stack.getBarWidth(); int i = stack.getBarColor(); … }`，见 `neoforge-21.1.235-sources.jar` 的 `net/minecraft/client/gui/GuiGraphics.java` 1343-1349 行）；forge 1.20.1 同构。
- **tooltip 三行**：`event/ModTooltipHandler` 的 `if (stack.is(ModItems.REPULSION.get()))` 块 = `tooltip.astral_dice.item.repulsion`（静态说明）+ `_durability`（`耐久：§eX§7 / §e50§r`）/ `_lifetime`（`剩余寿命：§9M:SS§r`）/ `_charge`（`已提供充能：§eX§7 / §e5§r`）+ `_owner`（紫）。
- 单一数据源：耐久 / 已存活 tick / 已提供充能都存 `ModDataComponents.REPULSION_HEALTH|AGE|CHARGE`（neo = `Codec.INT` + `networkSynchronized(ByteBufCodecs.INT)`；forge = `ItemDataKey<Integer>` 落 NBT 随栈同步）⇒ 客户端读到的是真值而非缺省 50。
- 满耐久 50/50 时**不画条**（原版工具口径）；受损后条长 = `13 × 血量/50`、配色随血量由绿转红；`RepulsionItem.MAX_DURABILITY = 50` 与 `event/ModEntityEvents` 注册的 `Attributes.MAX_HEALTH = 50.0D` 同值。

判据（2026-10-25 14:5x 重建 + 投放，图标换用户美术稿 + 耐久条改原版口径后）：两线 `build` EXIT=0（`BUILD SUCCESSFUL in 9s`）；四目标 jar 逐处 sha256 相同（neo `27d6799f…` 2,585,488 B；forge reobf `706ceca0…` 2,630,575 B；forge dev `6bfa91e1…` 2,601,109 B）；贴图仍为 344 B 用户稿；**字节码判据**（`javap -p -c` 取 jar 内 `com/merlinkitsune/astral_dice/item/RepulsionItem.class` 的 `isBarVisible`）：neo 与 forge dev 均为 `aload_1 → invokestatic getDurability → bipush 50 → if_icmpge → iconst_1/iconst_0 → ireturn`；forge reobf 同体但方法名被重映射为 `m_142522_`（**覆写原版方法在 SRG 生产包必然改名，属正常**，查条时别按 `isBarVisible` 找）。

改动（P0 两线同批）：玩家近战命中已放置力场第 5 次 ⇒ 回收成**新物品** `astral_dice:repulsion`（不是效果牌），携带耐久（= 关联血量）/ 已存活 tick / 已提供充能三份状态；再次放置按与效果牌**同一份**落点解析（`item/RepulsionPlacement.resolve`）恢复三份状态。

已成立的静态判据：

| 判据 | 命令 | 读数 |
|---|---|---|
| 两线编译 | `gradlew.bat :neoforge-1.21.1:classes` / `:forge-1.20.1:classes` | `BUILD SUCCESSFUL in 4s` / `in 11s`（EXIT 0，零新警告） |
| 两线打包 + 投放 | `gradlew.bat :<线>:build` | `BUILD SUCCESSFUL`，四目标 sha256 MATCH（§2） |
| 模型链 | `runData`（两线） | 生成 `src/generated/.../models/item/repulsion.json`（`parent: minecraft:item/generated`，`layer0: astral_dice:item/repulsion`） |
| 资源完整性 | `scripts/verify/verify_resource_integrity.ps1` | `RESULT: PASS`；两线注册物品 157，模型 157/157、贴图 157/157 |
| 语言同步 | `tools/check_lang_sync.ps1` | EXIT 0；P0 各 1058 键一致（3 条既有 WARN） |
| 稀有度一致性 | `tools/verify_rarity_consistency.py` | `GATE: PASS` |
| 规则分派 | `python -B docs/agent/verify-split.py` | 8 项全 `[OK]` |

补充拦截（2026-10-25 用户裁决「增加一层拦截：玩家只能破坏自己放置的强化拒止」）：

`RepulsionEntity#hurt` 在「只认玩家本人」之后再加一道 owner 闸门 —— `this.ownerUUID != null && !this.ownerUUID.equals(player.getUUID())` ⇒ 动作栏红字 `msg.astral_dice.repulsion_not_owner`（「这个强化拒止不属于你」）并 `return false`（**不计 `meleeHits`、不播受击红闪 / 受击音、不扣耐久**）；`ownerUUID == null`（管理命令 / 老存档）时对所有人开放。两线同批；三语 lang 四处同步（`tooltip.astral_dice.card.repulsion` 末行、`tooltip.astral_dice.item.repulsion` 第三行、`tooltip.astral_dice.item.repulsion_owner`、`astral_dice.guide.entry.effect_card_repulsion.2`）——「玩家近战」一律改为「**放置者**近战」，并补「其他人无法回收」；`_owner` 行由「仅获得者可以放置」改为「仅获得者可以放置**与回收**」。

判据（2026-10-25 15:2x）：两线 `:classes` + `:build` EXIT=0（`BUILD SUCCESSFUL in 9s` / `in 2s`）；产物 neo **2,585,715 B** `5a93e3fa…`、forge reobf **2,630,787 B** `61f6fce2…`、forge dev **2,601,318 B** `2f0969fd…`，四目标 sha256 MATCH；`javap -p -c` 取 jar 内 `RepulsionEntity.class` 的 `hurt`：`getfield ownerUUID → ifnull → Player.getUUID → UUID.equals → ifne` 之后 `ldc_w` 载入 `msg.astral_dice.repulsion_not_owner`（闸门已落盘）；`check_lang_sync` RC=0（P0 各 1058 键）；`tooltip_color_audit` 仍 144/9 键（repulsion 0 处）；`verify_rarity_consistency` PASS；`verify_resource_integrity` `RESULT: PASS`（157/157 模型与贴图）；`verify-split` RC=0 九项 `[OK]`。**owner 闸门已实机验证**（四态读数见下方「补充拦截 · owner 闸门实机」）。
实机读数（2026-10-25 15:0x，双版本，驱动 = `mt.ps1 --phase env|launch` + AutoJS `eval_js`，判据值全部逐条实测）：

| 判据 | 1.21.1 | 1.20.1 |
|---|---|---|
| A 满耐久物品 | `dur 50 / bar false`；`use` = SUCCESS；**创造不消耗**（`countAfterUse 1`）；实体 `hp 50/50 / charge 0` | 同（`dur 50 / bar false`、SUCCESS、实体 `hp 50 / charge 0`） |
| B 近战 5 次 | `meleeHits` 1→2→3→4→5，**每次回读 hp 恒 31**；第 5 次后实体消失、地面 1 个物品 | 同（1..5，hp 恒 31，实体消失、1 个物品） |
| C 回收物三份状态 | `dur 31 / elapsed 591 / remaining 23409 / charge 3 / bar true / barWidth 8` | `dur 31 / elapsed 0 / remaining 24000 / charge 3 / bar true / barWidth 8`（同一 tick 内完成 ⇒ elapsed 0） |
| D 重放恢复 | 实体 `hp 31 / max 50 / charge 3 / owner set / meleeHits 0` | 同 + `createdAt` 与回收前完全一致（elapsed 0 精确保留） |
| E 剩余寿命 | `createRecovered(40, 23990, 0, null)` ⇒ 放置后 `createdAt = gameTime − 23990`（只剩 10 tick），**2.5 s 后自行消失** | `createdAt = -23135`，**2.6 s 后复查 `repAlive 0`** |
| F 非 owner 拒止 | 外来 UUID ⇒ `use` = FAIL、实体 0、物品仍在手；未绑定 owner 的同款 ⇒ SUCCESS、实体 1 | 同（外来 FAIL、实体 0） |
| G 客户端耐久条 + tooltip | `barVisible true / barWidth 8 / barColor 12713728`（0xC1FF00 黄绿）；满耐久件 `fullBarVisible false`；tooltip 九行逐字正确（`耐久：§e31§7 / §e50§r`、`剩余寿命：§919:31§r`、`已提供充能：§e3§7 / §e5§r`） | 同（`barColor 12713728`、九行逐字相同），**唯一差异**：末行模组名 1.21.1 是纯文本 `Astral Dice`、1.20.1 是 `§9§oAstral Dice`（平台展示差异，非本批改动） |

视觉证据：`temp/bar-hotbar-zoom.png`（1.21.1）与 `temp/bar-hotbar-zoom-1201.png`（1.20.1）= 窗口截图裁底部热键栏 4× 放大，均可见槽 0 图标下方黄绿色耐久条（宽 8/13）。

#### 补充拦截 · owner 闸门实机（2026-10-25 15:1x–15:2x，双版本 PASS）

驱动：`temp/owner_neo.js` / `temp/owner_forge.js` + `temp/autojs_eval.ps1`（`mt.ps1 --version <线> --phase env|launch` 起客户端，AutoJS `eval_js` 求值）。命中走真实伤害链 —— `/damage @e[type=astral_dice:repulsion,limit=1,sort=nearest] 2 minecraft:player_attack by @s`（`directEntity` = 玩家）；命中计数与 owner 从实体 NBT 读（`e.saveWithoutId(t)` 的 `RepulsionMeleeHits` / `RepulsionOwner`（longArray 2 = 非空））。

| 判据 | 1.21.1 | 1.20.1 |
|---|---|---|
| A 放置 | `hp 50 / ownerTags 2 / hits 0` | 同 |
| B 放置者本人命中 1 次 | `hits 1`、hp 50 | 同 |
| C 改成外来 owner 后连打 3 次 | **`hits` 仍 1**、hp 仍 50、ownerTags 2 ⇒ 三次全被拒 | 同（`hits` 仍 1、hp 50、ownerTags 2） |
| D `setOwnerUUID(null)` 后命中 | `hits 2`、ownerTags 0 ⇒ 无主件对所有人开放 | 同 |
| E 还原原 owner 后连打 | `hits` 3 → 4 → 第 5 次后 `repsLeft 0`；回收物进背包 `dur 50 / elapsed 421 / charge 0 / owner Optional[380df991-…]` | `hits` 3 → 4 → 第 5 次后 `repLeft 0`；地面回收物 `dur 50 / elapsed 231`（未被拾取，双通道都要查） |

结论：非放置者的命中被「不计命中 / 不扣耐久 / 不播受击反馈」地拒绝，放置者第 5 次仍按原语义回收成物品；两线一致。

实机侧三条脚本坑（两线通用）：

1. `sp.getUUID()` 在 Rhino 里不可用（`Cannot find function getUUID`）⇒ 「记住原 owner」改用实体 NBT 的 `RepulsionOwner` longArray 往返（`saveWithoutId` → 之后 `putLongArray` / `put` 还原 → `load`）。
2. `ItemStack.save(CompoundTag)` 在 1.21.1 不可用（已改 `save(holder,counter)`）⇒ 物品状态一律读 `RepulsionItem.getDurability / getElapsedTicks / getChargeGiven` 静态方法。
3. 1.20.1 侧 `CompoundTag.putLongArray(String, long[])` 会被 Rhino 判 ambiguous（候选 `(String,long[])` / `(String,List)`）⇒ 还原 owner 用 `tE.put('RepulsionOwner', t0.get('RepulsionOwner'))` 复制 Tag 元素。

收尾（2026-10-25 15:3x）：1.21.1 `--phase stop --force` ⇒ `MT_STOP: OK (1s)`；1.20.1 `--phase stop --force --purge-saves` ⇒ `MT_CLEANUP_SAVES: PURGED 1` / `MT_CLEANUP_RESULT: OK` / `MT_STOP: OK (0s)`；`Get-CimInstance Win32_Process -Filter "Name='java.exe'"` 仅剩 Gradle 守护 70556（Zulu 21）与 32252（toolchain 17），无 Minecraft 客户端残留。

实机侧两个**方法可见性坑**（`1.21.1` 与 `1.20.1` 同）：Rhino 里 `sp.setXRot` / `sp.getXRot` / `sp.getUUID` / `entity.hurt(DamageSource,float)` / `lvl.getGameTime()` 全部 `Cannot find function`（哪怕 javap 是 public）⇒ 俯角用 `tp @s ~ ~ ~ ~ 70`、伤害用 `/damage … minecraft:player_attack by @s`（走真实伤害链，`directEntity` = 玩家）、改实体内部值走 NBT `saveWithoutId → putInt → load`。

### 4.1d 手册文案与图标修正（2026-10-25）—— 只读判据全绿，实机未做

用户指出的两处错误（战斗系统介绍页）：

1. 正文误写 **`2.0.0` 开发线** —— 实际版本是 `1.5.0`；
2. 该条目的书本图标是 **`astral_dice:dice`**，与「骰神赐福」条目图标撞车 —— 改为 **`minecraft:iron_sword`**（铁剑）。

| 落点 | 改动 | 判据 |
|---|---|---|
| 两线 × 三语 lang（6 文件） | `astral_dice.guide.entry.battle_system_changes.1` 正文改 `1.5.0`：zh「1.5.0 对战斗结算做了一轮系统化对齐。」/ en「Version 1.5.0 realigns combat resolution as a whole;」/ ja「1.5.0 で戦闘の計算を全面的に見直した。」 | `check_lang_sync` EXIT 0（P0 各 1058 键）；jar 内 `zh_cn.json` 命中新句、`2.0.0 开发线` 计数 0 |
| 两线 `assets/astral_dice/patchouli_books/astral_guide/en_us/entries/getting_started/battle_system_changes.json` | `"icon": "astral_dice:dice"` → `"icon": "minecraft:iron_sword"` | 开 jar 核：`minecraft:iron_sword=True` / `astral_dice:dice=False`（两线） |
| `scripts/test/cases/PMFIX-TIP-{1.20.1,1.21.1}.json:221` | 陈旧断言跟着改：`strip=2\.0\.0 开发线…` → `strip=1\.5\.0 …`（否则该两条用例必红） | 锚点唯一命中 1 处/文件 |

守门：`verify-split.py` 8 项 `[OK]`；`check_lang_sync` EXIT 0；`audit_patchouli_keys` 引用 1694 处 / 悬空 **0**。产物已重建并按四目标投放（§2），jar 内两项内容均已生效。

**未做**：实机打开手册页看图标与正文（需用户下达）。
### 4.1e 手册补充：强化拒止物品形态（2026-10-25，用户裁决「连带补充帕秋莉手册，路障不动」）

在既有条目 `en_us/entries/cards_effect/effect_card_repulsion.json` 追加**第 4、5 页**（不新建条目 —— 物品形态与效果牌是同一机制，放同一条目内更易读）：

| 页 | 类型 | 内容 |
|---|---|---|
| 4 | `patchouli:spotlight`（`item: astral_dice:repulsion`） | 物品形态即效果牌召唤的同一力场：落点判据共用一份（4.5 格 / 需可依附实心面 / 该处不能已有强化拒止）；耐久 · 剩余寿命 · 已提供充能随物品保存，重放即恢复 |
| 5 | `patchouli:text` | 获取方式（放置者本人近战 5 次；弹射物不算）、放置与回收仅属获得者（其他人只会收到「这个强化拒止不属于你」）、悬停查看耐久（满耐久不显示条）/ 剩余寿命 / 已提供充能 |

落点：两线同一个 `effect_card_repulsion.json`（3 → 5 页，两线文件逐字节相同）+ 两线 × 三语 lang 各 +2 键（`astral_dice.guide.entry.effect_card_repulsion.4` / `.5`）⇒ P0 键数 1058 → **1060**。

判据（2026-10-25 15:5x）：`python tools/audit_patchouli_keys.py` ⇒ 四线引用 **1698** 处 / 悬空 **0**（EXIT 0）；`check_lang_sync.ps1` EXIT 0（P0 各 **1060** 键一致，P1 836 / 844 未受影响，3 条既有 WARN 照旧）；`tooltip_color_audit` 仍 **144 处 / 9 键**（本批不新增 tooltip）；`verify-split.py` 8 项 `[OK]`；两线 `build` EXIT 0 并按四目标投放，开 jar 核：条目 page `type` 计数 **5**、spotlight 的 item 为 `astral_dice:repulsion`、`zh_cn.json` 命中 `effect_card_repulsion.4` / `.5`，四目标 sha256 全 MATCH。

产物（15:5x 重建）：neo **2,586,610 B** `4686ec6de2b637d22f8a9688e87c88180dad36f9f68f6d8a5a522714a8c53fb4`；forge reobf **2,631,683 B** `dbccaf2c16112681fe9e36494072021662ec0d119952401a52e4aeebb2535cd0`；forge dev **2,602,214 B** `19bff3310bb540beb7f2a74b6aa673e22eaac04458723d5a45f34741ffca2e45`。

**未做**：实机翻开该条目看第 4/5 页排版（需用户下达）。**路障不动** —— `card.barricade` 的 6 处 `§e20:00§7` 按用户本次裁决保持原样，`tooltip_color_audit` 的 144 处存量全部继续保留。

### 4.1f 版本升级判据（`1.5.0-rc.1` + 前置库 `1.1.0`，2026-10-25）—— 静态全绿，**未实机**

**前置库侧（`F:\MCProject\starengine`，提交 `16b48c4`）**

- 四子项目 `gradle.properties` 由 `lib_version=1.1.0-pre.2` / `mod_version=1.1.0-pre.2+<suffix>` 升为 `1.1.0` / `1.1.0+<suffix>`；`README*.md` 坐标表、依赖示例、Fabric `depends` 串、当前版本段同步。
- `gradlew.bat publishToMavenLocal`（四平台）+ 各线 `build`：`BUILD SUCCESSFUL`；产物 `starengine-neoforge-1.21.1-1.1.0.jar` 98,734 B / `starengine-forge-1.20.1-1.1.0.jar` 103,605 B（reobf）/ dev named 102,013 B / `starengine-neoforge-26.1.2-1.1.0.jar` 98,970 B / `starengine-fabric-1.20.1-1.1.0.jar` 112,634 B，sha256 见 §2。
- jar 内实测：`version="1.1.0+<平台后缀>"`、`license="GPL-3.0-only"`。**库内 Java 源码零改动** —— `1.1.0-pre.2` 之后只有许可切换（`c545b75`）与文档（`413cc39`），故 1.1.0 是 pre.2 的定版。

**mod 侧**

- 四线 `mod_version = 1.5.0-rc.1+<后缀>`；四线 `starengine_version = 1.1.0`；fabric `starengine_version_range=>=1.1.0`。
- 硬前置区间由模板插值自动成为 `[1.1.0,2.0.0)`（两线 toml 的 `versionRange="[${starengine_version},2.0.0)"` 未改字面量）；两线模板注释里「⚠️ 不要写成 `[1.1.0,2.0.0)`」的**预发布期禁令**改为正式版口径（保留历史原因：`ComparableVersion` 把未知限定符 `pre` 排在正式版之后，预发布件会污染下界判定）。
- `.github/workflows/build.yml:105` 库 `ref` 由 `6d2a96df…`（pre.2）改为 `16b48c413d92bcd830d97c0a80b0df4e0c47ba6c`（1.1.0），注释同步（与四线 `starengine_version` 同提交更新）。
- `README*.md`：四线版本列 `1.5.0-pre.5` → `1.5.0-rc.1`（4 行/文件）、库版本 `1.1.0-pre.2` → `1.1.0`；`CHANGELOG.md:38` / `CHANGELOG_ZH.md:35` 的库版本串同步。

**打包 / 投放判据**

- 两线 jar 内：`version="1.5.0-rc.1+<后缀>"`、`modId="starengine"` 的 `versionRange="[1.1.0,2.0.0)"`、`META-INF/jarjar/` 计数 **0**（`pushToRootBuild` 自检亦输出「无 META-INF/jarjar/(符合「不内置」口径)」）。
- 四目标 sha256 MATCH：`run/1.21.1/mods`、`run/1.20.1/mods`（dev named）、`D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`、`D:\.minecraft\versions\1.20.1-Forge 模组测试\mods`。
- **库 jar 成对替换**：四处 `starengine-*-1.1.0-pre.2.jar` 已删除，换成 `1.1.0`（neo 用生产 jar；`run/1.20.1` 用 dev named；forge 整合包用 reobf）；复制后逐处 sha256 与源一致，目标目录内 `starengine*.jar` 只剩 1 个。

**守门**

| 判据 | 读数 |
|---|---|
| `python -B docs/agent/verify-split.py` | `全部通过`（8 项 [OK]，EXIT 0） |
| `pwsh -NoProfile -File tools/check_lang_sync.ps1` | EXIT 0；P0 每条 1060 键 / 26.1.2 836 / fabric 844；3 条既有 WARN |
| `python tools/audit_patchouli_keys.py` | 引用 1698 处 / 悬空 **0** |
| `python tools/verify_rarity_consistency.py` | `GATE: PASS`（C1/C2/C3） |
| `pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1` | `FAIL（144 处）` —— **既有基线债**，repulsion 相关 0 处（§5 Major-4） |
| `pwsh -NoProfile -File scripts/verify/verify_resource_integrity.ps1` | `RESULT: PASS`；rc.1 两线各 157 物品、模型/贴图 157/157（26.1.2 旧 jar 137/137 属 P1 陈旧产物） |

**未做（如实登记）**

1. **rc.1 未实机**：本批只改版本号与前置库坐标，行为等价 pre.6（pre.6 的实机读数见 §4.1–§4.1c）。唯一新增风险面是**版本门槛**——需在实机里确认「mod rc.1 + 库 1.1.0」能过 FML 版本检查、且旧 `1.1.0-pre.2` 库会被 `[1.1.0,2.0.0)` 正确拒绝。
2. **P1 两线（26.1.2 / fabric）只做源码 bump，未构建未验证**（库侧四平台 jar 已构建并发布 mavenLocal，mod 侧 P1 产物未重建）。
3. 推送状态（2026-10-25 23:1x 更新）：本批**已 push**（`13a45619`），无遗留未推送提交；tag `1.5.0-rc.2` 由 CI 自动创建。

#### 4.1f-a 双版本冒烟实机（2026-10-25 15:39–15:43）—— 全 PASS

- **1.21.1**：`--phase launch` ⇒ `MT_LAUNCH: OK (54s)`；mod 列表 `Astral Dice 1.5.0-rc.1+neoforge_1.21.1`（`latest.log:24`）/ `StarEngine 1.1.0+neoforge_1.21.1`（`:46`）。AutoJS `server/eval_js`：`astral=1.5.0-rc.1+neoforge_1.21.1`、`lib=1.1.0+neoforge_1.21.1`、`repReg=true`、`cardReg=true`、`/give astral_dice:repulsion` 后读 `dur=50 / age=0 / charge=0`。
- **1.20.1**：`--phase env` ⇒ `MT_WORLD: OK — 种子快恢复`；`--phase launch` ⇒ `MT_LAUNCH: OK (52s)`。AutoJS：`astral=1.5.0-rc.1+forge_1.20.1`、`lib=1.1.0+forge_1.20.1`、`repReg/cardReg=true`；背包槽 1 得到 `repulsion ×1 / dur=50`，`use(...)` 放置成功 ⇒ **实体 1 个、hp 50**，创造模式不消耗（手持仍 `repulsion ×1`）。
- **版本门槛判据**：两线 `latest.log` 里 `Missing or unsupported mandatory dependencies` / `MissingDependency` / `Incompatible mods` 计数 **0** ⇒ `[1.1.0,2.0.0)` 对库 `1.1.0` 正确放行。
- **已知非阻断告警（来源是 testworld 种子模板，不是 rc.1）**：1.20.1 `latest.log:259-262` Forge 打印 `The following mods have version differences that were not resolved: astral_dice (1.3.2+forge_1.20.1 -> 1.5.0-rc.1+forge_1.20.1) / starengine (1.0.4+forge_1.20.1 -> 1.1.0+forge_1.20.1)`，随后 `Injecting existing registry data` 与一批 `Object did not get ID it asked for`；种子模板的 `level.dat` 记录的是旧版本，registry 重新注入后正常进世界。
- **收尾**：1.21.1 `MT_STOP: OK (1s)`；1.20.1 `--phase stop --force --purge-saves` ⇒ `MT_CLEANUP_SAVES: PURGED 1` / `MT_CLEANUP_RESULT: OK` / `MT_STOP: OK (1s)`；`java.exe` 仅剩两个 Gradle 守护（70556 Zulu 21 / 32252 toolchain 17），`.agent-locks` 空。

### 4.1g HUD 攻击力显示口径统一（2026-10-25）—— 静态判据全绿，**双版本已实机（见 §4.1i）**

- **用户裁决**：「所有攻击力加入显示，除手持武器本身与骰战中的随机攻击力数值加成」。
- **根因**：`combat/DiceCombatModifiers.java` 的攻击修饰器链上有 23（neo）/ 24（forge）处**逐批复制粘贴**的 `if (ctx.attacker.level().isClientSide()) return ap;` 模板守卫 ⇒ 客户端 `CombatPowerHud` 求值时整链被跳过，HUD 只含 10 块修饰器（电流剑等 13 块静默消失）。
- **修法（不逐处删守卫）**：新增显示口径标记 `DiceCombatContext.displaySnapshot`（客户端求值只可能为了显示 ⇒ 放行；服务端战斗上下文恒 false），守卫统一收敛为 `skipOnClient(ctx)` = `attacker.level().isClientSide() && !ctx.displaySnapshot`；`attackPowerBase` 增 5 参重载（末参 `boolean displaySnapshot`），原 4 参版按 `player.level().isClientSide()` 委托，3 参版与既有调用点（`attackPowerOf` / `getDisplayAttackRange`）零改动。
- **随机点数排除**：攻击卡掷骰修饰器（`CardRegistry.roll`）在 `ctx.displaySnapshot` 下直接 `return ap`（不掷卡、不写 `ctx.attackCardSum`）。
- **手持武器**：仍由 `attackDamageExcludingHeldWeapon`（主手 + 副手属性修饰器逐项重算）剔除，附魔加伤随武器一起不计。
- **判据（实跑）**：源码不变量 `bareGuards=0`、`skipOnClient=23/24`、`cardSkip/flagSet/overload5=true`（两线）；`python tools/verify_hud_attack_parity.py` ⇒ `GATE: PASS  (21 项全部满足)`；`gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes` ⇒ `BUILD SUCCESSFUL in 25s`；`gradlew.bat :neoforge-1.21.1:build :forge-1.20.1:build` ⇒ `BUILD SUCCESSFUL in 5s` 并触发 `pushToRootBuild` / `pushToDevRun` / `pushToGame` 四条推送。
- **产物（2026-10-25 16:2x）**：neo **2,586,892 B** / sha256 `5c4a6207…38ed0ac`（`build/libs` = `run/1.21.1/mods` = 「狐の航空学 Voxy Edition」同一份）；forge reobf **2,631,933 B** / `576430bb…adefbbe4`（`build/libs` = 「1.20.1-Forge 模组测试」）；forge dev named **2,602,467 B** / `cc0f4341…6758f892`（`run/1.20.1/mods`）。六处 jar 内 `com/merlinkitsune/astral_dice/combat/DiceCombatModifiers.class` 全部命中 `skipOnClient` 与 `displaySnapshot` 字符串（字节码判据）。
- **未覆盖的边界**（登记在 §5-Minor 5）：目标依赖型加成在客户端无目标 ⇒ HUD 只能是「无目标」口径；P1 两线未移植。

### 4.1h 防御力口径修正（2026-10-25）—— 静态判据全绿，**已重建产物并双版本实机（见 §4.1i）**

- **用户裁决**：先解防御口径问题，再跑实机。依据 `docs/agent/AGENTS-full.md:584-613`〈平衡性红线〉——「任何使既有数值口径失真的实现」属恶性 BUG，必须当批修复，不得按「设计取舍 / 已知限制 / 待裁决」放过。
- **两个失真点**：① **折算写死 `× 2.0`**：所有「防御力 +X」的来源都按 `X × 2.0` 加护甲，经 0.30 / 0.40 系数后只兑现 **0.6X（玩家）/ 0.8X（怪物）**；根因是旧公式「2 + 护甲÷2」时代的换算（2026-09-26 系数 0.5 → 0.30 时未同步）。② **指南基数 4 vs 代码 2**：库 `CombatFormula.PLAYER_BASE_DEFENSE = 2.0`（注释：2026-10-21 用户裁决，2 点 = 1 面盾），而 12 份指南 lang（四线 × 三语）仍写「玩家 防御 = 4 + 护甲×0.30」。
- **修法（单一真值）**：两线 `combat/DiceCombatModifiers.java` 新增 `PLAYER_ARMOR_PER_DEFENSE = 1.0 / CombatFormula.PLAYER_ARMOR_COEF`（≈3.333）、`MOB_ARMOR_PER_DEFENSE = 1.0 / CombatFormula.MOB_ARMOR_COEF`（2.5）、`armorValueForDefensePoints(points, side|entity)` 与反函数 `defensePointsForArmor(armor, side)`；10 处站点全部改走它（两个 `setDefenseArmorBonus`、`defensePowerBase`、`defensePowerOf`（floor 带 `+1.0E-6D` 浮点尘埃容差）、骰战 `effectiveArmor`、摩托头盔、黑曜石骰、多萝西暖意、Jill 防御牌、雷暴将至生物护甲）。多萝西主动技读「当前防御力」原来写死 `armor / 2.0`，改为 `defensePointsForArmor(armor, true)`（保留 `getAttributeValue` 取 double，避免 `getArmorValue()` 先取整丢 0.2 点）。注释口径同步归一 51 组（两线各 ~21 文件）；12 份指南 lang 改 2 基数 + 「1 防御力 = 1÷护甲系数 护甲值（玩家约 3.33、怪物 2.5）」。
- **库不动**：`CombatFormula` 的系数与基数都是现成 public 常量 ⇒ 库无改动、无版本 bump。
- **判据（实跑）**：`python tools/verify_defense_unit_parity.py` ⇒ `GATE: PASS（34 项全部满足）`（D1 旧字面量 0 处 / D2 折算常量同源 / D3 defensePower* 走折算 / D4 骰战 / D5 五处站点 / D6 12 份 lang / D7 ARMOR 写点白名单 / D8 P1 只统计）；数值自检「玩家 +2 防御力 ⇒ +6.6667 护甲 ⇒ 折算回 2.0000」；`git grep` 两线旧字面量 `defensePoints * 2.0|modifierDefense * 2.0|defense * 4.0D|stacks * 2.0D` = 0 命中；`gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes` ⇒ `BUILD_EXIT=0`（仅既有 deprecation 警告）。
- **平衡影响（必须如实登记）**：玩家侧防御力加成实际强度是上一版的 **×1.667**（怪物侧 ×1.25）；公式其余部分未动。若要回退，只需把 `armorValueForDefensePoints` 的两常量改回 2.0 / 2.0（其它站点都走单一入口）。
- **未做**：未重建 jar、未投放四目标、未实机。P1 两线仍为旧 `× 2.0` 折算（各 5 处），已登记 `docs/compat-1.5.0-porting.md` §2 主题行 + §4 第 4 条。


### 4.1i 双版本实机：HUD 攻击力（§4.1g）+ 防御力 1:1（§4.1h）+ 取整 off-by-one 修补（2026-10-25 16:5x–17:1x）

- **用户下达**（m11563）：「先解决防御口径问题，再运行实机测试（验证所有遗留项）」。本节是 rc.1 批次（mod `1.5.0-rc.1` + 库 `1.1.0`）的实机读数，全部由 AutoJS `eval_js` 的 server / client 侧直接取证（不靠截图）。
- **HUD 攻击力 = PASS ⇒ §5-Minor 5 销账**：两条线上 `/astralprobe equipslot` 都**不会真正落槽**（dice 槽仍空、chip 槽 0 格，命令本身不报错）⇒ 必须用 Curios API 直写：1.21.1 用 `getCuriosInventory(p).get()` / `getStacksHandler('dice').get()`，1.20.1 用 `getCuriosInventory(p).orElse(null)` / `getStacksHandler('dice').orElse(null)`（Curios 5.14 返回 Optional/LazyOptional）；0 星 `golden_dice` 经 `DiceCurioItem.refreshChipSlotCount(p)` 得 **1** 个 chip 槽后才装得进筹码。
  - **1.21.1**：`electric_sword_chip` 已装 + `ChargeManager.addStacks(p, 8)`（`CHARGE_PER_ATTACK = 4`）⇒ `getAttackBonus = 2`；server `attackPowerOf = 3`、`attackPowerDisplayOf = 3`；**client `attackPowerDisplayOf = 3`**（修复前该值恒 1 —— 23 处客户端模板守卫把整链跳过）。对照 A：`ChargeManager.removeAll(p)` ⇒ client `bonus 0 / display 1`（数字确由筹码驱动）；对照 B：主手写 `diamond_sword` ⇒ client display 仍 3（手持武器按裁决不进 HUD）。
  - **1.20.1**：同套装扮 ⇒ server `{eq:true, charge:8, bonus:2, disp:3, full:3}`、client `{eq:true, bonus:2, disp:3}` ⇒ **两线一致**（forge 的 24 处守卫收敛同样生效）。
- **防御力 1:1 = PASS**：裸装（护甲 0 / 韧性 0，基数 `CombatFormula.PLAYER_BASE_DEFENSE = 2`）下 `setDefenseArmorBonus(p, tag, 8)` ⇒ `ARMOR 属性 = 26.666666666666668`、`defensePowerOf = 10`；`+3` ⇒ `ARMOR = 10`、`dp = 5`；清除后回到 `dp = 2`；生物系数 `armorValueForDefensePoints(8, false) = 20.0`。**1.21.1 与 1.20.1 逐项一致**。
- **实机暴露并当批修掉的残留缺陷：取整吞点（floor off-by-one）**。`LivingEntity#getArmorValue()` = `Mth.floor(ARMOR)` ⇒ 26.6667 被截成 26 ⇒ `2 + 26 × 0.30 = 9.8` ⇒ `floor 9`，于是「防御力 +8」只兑现 **+7**（`+3` 恰好整数故无感，正好构成对照）。修法：两线新增单一入口 `DiceCombatModifiers#effectiveArmorPoints(LivingEntity)`（读 `Attributes.ARMOR` 的 **double 终值**，`max(0, …)`），4 处取甲改走它 —— `defensePowerBase`（neo 原 :1051 / forge 原 :1049）、`DiceCombatEvents` 骰战（neo 原 :759 / forge 原 :757）、`DiceCombatEvents` 怪物→玩家（neo 原 :1091 / forge 原 :1087）、`PiercingGunChipItem:27`（两线），注释同步 6 处。修后复验：1.21.1 `{dp0:2, armorRaw8:26.666666666666668, armor8:26, dp8:10, dp3:5, dpBack:2}`、1.20.1 同值 ⇒ **+8 兑现 10**。
  **未改**（真护甲语义站点）：`UnwaveringEffect`(+8 护甲/层)、`DragonRoarBreakEffect`(-8)、`BlueCurse`(-20%)、`JasmineSweep`(-30%)、`DorothySignItem`（本来就用 `getAttributeValue`）、`MosesEnigmaticLink`、`jade/ArmorValueProviderMixin`（mob 整数口径）。
- **产物（2026-10-25 17:0x 重建 + 四目标投放，sha256 实测）**：

  | 目标 | 字节 | sha256 | 字节码 `effectiveArmorPoints` |
  |---|---|---|---|
  | `run/1.21.1/mods`（neo） | 2,587,472 | `600E29E9BA473FFB5251F1E3B2921A1ABF37E0E626581BA8A6FFA1B0A8264B1D` | True |
  | `run/1.20.1/mods`（forge **devlibs named**） | 2,603,042 | `E0C6D70FA527E75F7451DBD71A9C238E9A88DBA7DAF762DF47F04CDEC7DBA47A` | True |
  | `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`（neo） | 2,587,472 | 同 `600E29E9…`（与 run 同一份） | True |
  | `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods`（forge **reobf**） | 2,632,435 | `AD987CC7A64DF8043B50EF388A456DE938BFD4330A5E6123275C4E465F8BB106` | True |

  构建：`gradlew.bat :neoforge-1.21.1:build :forge-1.20.1:build --console=plain` ⇒ `BUILD SUCCESSFUL in 7s`，六条 `pushTo{Game,DevRun,RootBuild}` 全命中；库 jar 成对在位（`starengine-neoforge-1.21.1-1.1.0.jar` / `starengine-forge-1.20.1-1.1.0.jar`）。
- **守门（实跑）**：`python tools/verify_defense_unit_parity.py` ⇒ `GATE: PASS（42 项全部满足）`（新增 **D9** 四项：入口存在且读 `getAttributeValue(Attributes.ARMOR)` / `defensePowerBase` 走它 / 骰战走它 / `combat/**` 与穿甲枪里 `getArmorValue()` 调用 0 处）；`python tools/verify_hud_attack_parity.py` ⇒ `GATE: PASS (21 项全部满足)`；`pwsh tools/check_lang_sync.ps1` ⇒ EXIT 0（四线键集一致）；`python docs/agent/verify-split.py` ⇒ 全部通过；`pwsh scripts/audit/tooltip_color_audit.ps1` ⇒ 仍 **144 处**（既有基线债，用户已裁决路障不动）。
- **仍未实机 / 未做**：§5-Major 5 的 8 条「代码与文案不符」（2026-10-25 用户裁决「不管（黑箱）」⇒ 不修、不再列待办）、§5-Minor 3 `misaki_sign` datagen 逐字节对等（下次 `runData` 后 diff）、P1 两线未移植。
- **本轮坑补充**：① 1.21.1 客户端未停就 launch 1.20.1 ⇒ 25585 端口冲突 + `MT_INJECT_FOCUS: FAIL — 目标窗口无法置前台`（§6 坑 15），必须先对另一线 `--phase stop --force`；② `/astralprobe equipslot` 在两条线都不落槽，装备类实验一律走 Curios API；③ 1.21.1 的 Curios 9.5.1 写入槽位后需 1–2 tick 再读（§6 坑 8），本轮客户端读数前已 sleep 1.5–1.8 s。

### 4.1j 创造栏收尾：强化拒止物品隐藏 + 筹码按稀有度重排（2026-10-25）—— 静态判据全绿，**本条无实机**

- **用户裁决**（m12164）：① 把「强化拒止」**物品**从创造栏与 JEI 隐藏；② 筹码在**保持流派分组**的前提下按**稀有度**重排。
- **隐藏机制（JEI 侧零代码）**：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/init/ModCreativeTabs.java` 删掉 `output.accept(ModItems.REPULSION.get());`（neo 原 `:88`、forge 原 `:89`，两处一并换成 3 行说明注释）。JEI 不另写代码的依据是**实装 jar 字节码**：`jei-238222-8512040.jar` 的 `mezz/jei/library/plugins/vanilla/ingredients/ItemStackListFactory` 在 `javap -p -c` 里命中 `net/minecraft/world/item/CreativeModeTabs.allTabs()`（偏移 201）与 `CreativeModeTab.getDisplayItems()`（偏移 311）⇒ 物品列表就是创造栏 displayItems。同字节码里那条「从物品注册表补加」的分支只在 JEI 客户端配置 `showHiddenIngredients`（调试项「显示隐藏物品」）为 true 时执行，默认关闭；`mezz.jei.api.IModPlugin` 的方法列表里也没有 registerItemBlacklist 一类接口。
- **物品本体零改动**：仍注册、仍是「放置者近战命中 5 次把力场打回物品形式」的产物、力场碎裂时仍掉落（`entity/RepulsionEntity`）；效果牌 `effect_card_repulsion` 仍在创造栏（`ModCreativeTabs.java:87`）。
- **筹码重排**：五组与组序**不变**（星光 → 治愈 → 标记 → 充能 → 无流派），组内改为**稀有度升序**（稀有 → 史诗 → 传奇；本类只用这 3 档），同档沿用原有相对顺序；67 个筹码**零增删、零改档**；每组每档加了小标题注释（`// -- 稀有（RARE） (n) --`）便于后续插入。
- **判据（实跑）**：新增 `tools/verify_creative_tab_chip_order.py`（D1 物品不在创造栏且效果牌仍在 / D2 组序 / D3 组内稀有度非降序 / D4 筹码数 67 且无重复 / D5 两线筹码段空白规范化后完全一致）⇒ `GATE: PASS (D1-D5 全部满足，两线)`；**变异测试**：把 forge 的 `EIGHT_SIDED_DICE` 与 `STAR_COIN_HAMMER` 对调后重跑 ⇒ `GATE: FAIL (2 项)`（D3 组内乱序 + D5 两线不一致），按字节还原后再次 `PASS` ⇒ 判据不是假绿。编译 `:neoforge-1.21.1:classes :forge-1.20.1:classes` ⇒ `BUILD SUCCESSFUL in 7s`。
- **产物与投放（17:32 已完成）**：`:neoforge-1.21.1:build :forge-1.20.1:build` ⇒ `BUILD SUCCESSFUL in 2s`，四条 `pushTo*` 全命中 —— neo `2,587,467 B` sha256 `366D68C86DDFB86229F5AB437EBF5FC386859BD6658A7B0FD45B8E70F97B642D`（`build\libs` = `run\1.21.1\mods` = 整合包，同一份）；forge reobf `2,632,431 B` `FD7B01D79FEA43080E3744F81FAF752F7526770D1F45FBB3BC0A7CB84DAFB4D9`（`build\libs` = 整合包）；`run\1.20.1\mods` 为 dev 的 named jar `2,603,035 B` `A3741D16F03A0BE11A2CFCF640D44A6634D395E6A5804A7650D729B2C9D29FE2`。**jar 内字节码复核**：解 `run\1.21.1\mods` 的 `com/merlinkitsune/astral_dice/init/ModCreativeTabs.class` 做 `javap -p -c` ⇒ `ModItems.REPULSION` 引用 **0 处**、`EFFECT_CARD_REPULSION` **1 处** ⇒ 隐藏已进产物（顺序改动在字节码里不可见，由 D3/D5 守门 + 源码结构核对覆盖）。
- **未做**：**未实机**（本条只改展示层可见性与顺序，无行为变化）。P1 两线未改（§5-Minor 6）。

### 4.1k 护法立牌减费卡面（2026-10-25）—— 静态判据全绿 + **双版本实机 PASS**

- **用户裁决**（m12348）：装备护法立牌（`misaki_sign`）时，商店货架上的「名刀·嘎呜切」（`astral_dice:attack_card_meito`）卡面换成减费版素材。
- **为什么只能换图**：该立牌被动把名刀的战斗费用 4→3 点（服务端 `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/MisakiSignItem.java:64-66` `hasMisakiEquipped(player) ? 3 : AppliedStone.cost("meito")`；forge 同 `:66`），而 64×64 卡面素材右下角的费用点是**画在贴图里的**；商店实际价格与它无关（`shop/CardShopMenu.java:667` `prices[i] = CardShopShelf.priceOf(true, memberReduction);`）⇒ 纯展示层换图，价格与购买链路零改动。
- **实现**：两线 `screen/CardShopScreen.java` 新增 `private ResourceLocation cardFaceTexture(ItemStack stack)`（插在 `drawCardFace` 之前），`drawCardFace` 改为 `ResourceLocation face = cardFaceTexture(stack);`（neo 现 `:720`）。判定链：非 `ATTACK_CARD_MEITO` ⇒ 原图；`MisakiSignItem.hasMisakiEquipped(this.minecraft.player)` 为假（含 `minecraft`/`player` 为 null）⇒ 原图；否则用 `<id>_discount.png`，`faceExists()` 为假仍回退原图。两线唯一差异：neo `ResourceLocation.fromNamespaceAndPath(...)` / forge `new ResourceLocation(...)`。
- **客户端只读**：沿用同文件 `displayStack(ItemStack)` 的先例（怪力乱神佩戴时把路障显示成强化拒止），只读 Curios 双端同步缓存，不写状态、不伪造物品。
- **素材**：`images/shop/card+event/嘎呜切减费.png`（904 B / 64×64）逐字节拷成两线 `src/main/resources/assets/astral_dice/textures/gui/shop/card/attack_card_meito_discount.png`。
- **判据（实跑）**：新增 `F:\MCProject\astral_dice_multiloader\tools\verify_meito_discount_face.py`（D1 源素材 64×64 / D2 两线贴图与源素材逐字节一致 / D3 两线有 `cardFaceTexture` 且 `drawCardFace` 走它 / D4 方法体四要素 `ATTACK_CARD_MEITO`·`hasMisakiEquipped`·`_discount.png`·`faceExists(discount) ? discount : base` / D5 两线 `getMeitoCost` 语义唯一 / D6 贴图名只出现在两线 `CardShopScreen`）⇒ `GATE: PASS（D1-D6）`；**变异测试**把 `"_discount.png"` 改成 `"_discountX.png"` ⇒ `GATE: FAIL (1 项) - D4`，还原后 PASS（证非假绿）。
- **产物与投放（已实测）**：`:neoforge-1.21.1:build :forge-1.20.1:build` ⇒ `BUILD SUCCESSFUL in 4s`，六条 `pushTo*` 全命中；四目标 jar 内 `attack_card_meito_discount.png` 均有 1 份 —— `run/1.21.1/mods` 与「狐の航空学 Voxy Edition」同为 `2,588,804 B` sha256 `59E12108CF9681928661BE0C86FEA203A15642502095AECE1AB6BC9BD2F625D0`；`run/1.20.1/mods`（devlibs named）`2,604,380 B` `CB21FA244F7EBBE2B87A9CE269F8A7D620CE3A1AD4096A652DAEE6BA2A4A5CAB`；「1.20.1-Forge 模组测试」（reobf）`2,633,788 B` `33717FA935133587AC65711C6AC9B0BBBD6562BABE65DC18B502DE4220911702`。
- **实机（双版本 PASS，2026-10-25 18:12–18:23）**：`--phase launch`（1.21.1 `MT_LAUNCH: OK (55s)` / 1.20.1 `MT_LAUNCH: OK (49s)`，AutoJS 端点 `http://127.0.0.1:25585/mcp` 就绪）。驱动口径：`/astralshop equip MK stand "astral_dice:misaki_sign"`（chat `AP_MK_EQUIP:slot=stand:...:err=none:got=astral_dice:misaki_sign`）+ 服务端 `CardShopShelf.store(player, 强制货架)` 把首格钉成 `astral_dice:attack_card_meito` + `/astralshop openkeep SP1|SP2|SP3` 开商店 GUI；客户端 `MisakiSignItem.hasMisakiEquipped(player)` 实测 `true`（佩戴）/`false`（卸下），`mc.screen = ...screen.CardShopScreen`。
- **判据（像素级，不看肉眼）**：对「佩戴 vs 卸下」两张全屏截图取卡面格 82×120 区域逐像素比较 —— **1.21.1 差 347 px / 1.20.1 差 360 px**，差异 bbox 两线同为 region-local `(10,82)-(85,119)`（费用点行 + 卡名行；卡名是超宽标签的水平滚动，故卡名行也有差）；费用点行可辨 **3 点（佩戴）vs 4 点（卸下）**，放大 8× crop 见 `temp/crop_sign_yes.png` / `temp/crop_sign_no.png`（1.21.1）与 `temp/crop120_sign_yes.png` / `temp/crop120_sign_no.png`（1.20.1）；贴图本体差异 bbox（64×64 内 `(20,50)-(29,61)`、88 px，正常不透明 `(26,26,26,255)` vs 减费透明）与屏上差异位置一致 ⇒ 佩戴时确实换成 `attack_card_meito_discount.png`。
- **收尾**：1.21.1 `--phase stop --force` ⇒ `MT_STOP: OK`；1.20.1 `--phase stop --force --purge-saves` ⇒ `MT_CLEANUP_SAVES: PURGED 1` / `MT_CLEANUP_RESULT: OK` / `MT_STOP: OK (1s)`；`Get-Process java` 仅剩 Gradle 守护（9984 / 12776）。
- **未做**：P1 两线（`neoforge-26.1.2` / `fabric-1.20.1`）未移植（§5-Minor 7 同口径）；购买链路与价格未做回归 —— 本条硬约束是「价格与购买链路零改动」，由源码口径保证（`CardShopMenu.java:667` 未动）。

### 4.1l 交付与发布：rc.1 + 库 1.1.0 推送 + CurseForge 上传（2026-10-25 18:2x–18:5x）—— 全部成功

- **提交与推送**：本体 `6ee75a34`（docs(release)：玩家更新日志，2 文件 +460）⇒ `git push origin multi-main`，远端 `origin/multi-main` = `6ee75a34b…`，`rev-list --count origin/multi-main..HEAD` = **0**（此前累计 28 个未推送提交一并推送）。前置库仓 `F:\MCProject\starengine`：`16b48c4` 推送 ⇒ `413cc39..16b48c4  main -> main`，`origin/main..HEAD` = **0**。无 tag（用户未下达打 tag）。
- **玩家更新日志（1.3.7→1.5.0 全量，中英）**：`release/1.5.0-rc.1/PLAYER_CHANGELOG_ZH.md` **218 行 / 24,201 B** + `PLAYER_CHANGELOG.md` **242 行 / 23,492 B**（全 CRLF、裸 LF 0）。H2 顺序：安装与前置（必读）/ 新增内容（商店与村民 · 事件体系 · 立牌与效果牌 · 界面与表现）/ 战斗系统重做 / 平衡与数值调整 / BUG 修复 / 1.3.7-hotfix 的修复 / 文案与手册 / 许可变更。素材 = `CHANGELOG_ZH.md` 的 `## 1.5.0`（L7–L773）+ `## 1.3.7-hotfix`（L774–L832）+ `release/1.3.7/PLAYER_CHANGELOG_ZH.md` 的既有房格。
- **CF 关系补登（本轮真缺口）**：`tools/curseforge.json` 的 `neoforge_1.21.1` / `forge_1.20.1` 两线在 curios（forge 另有 mixinbooster）之后追加 `{"slug":"starengine","projectID":1755415,"type":"requiredDependency"}`，并在 `_relationsNote` 追加「2026-10-25 起 P0 两线 StarEngine 独立前置、P1 两线仍内嵌」说明；`json.load` 自检通过，文件 5,233 → 5,736 B（LF）。理由：lib 自 1.1.0 起从 P0 两线 jar 内移除（`META-INF/jarjar/` 计数 0），不登记关系则 CF 玩家下载后直接 `Missing or unsupported mandatory dependencies`。
- **CF 上传（`tools/curseforge_upload.py`，6 个文件全部 `HTTP 200`；每次均打印「Cloudflare 拦截 urllib（HTTP 403）⇒ 改用 curl」，即 curl 回退仍在生效）**：

  | 项目 | jar | 大小 | fileID | 渠道 |
  |---|---|---|---|---|
  | astral-dice `1662159` | `astral_dice-1.5.0-rc.1+neoforge_1.21.1.jar` | 2,588,804 B | **`9320026`** | **beta** |
  | astral-dice `1662159` | `astral_dice-1.5.0-rc.1+forge_1.20.1.jar` | 2,633,788 B | **`9320028`** | **beta** |
  | starengine `1755415` | `starengine-neoforge-1.21.1-1.1.0.jar` | 98,734 B | **`9320029`** | **release** |
  | starengine `1755415` | `starengine-forge-1.20.1-1.1.0.jar` | 103,605 B | **`9320030`** | **release** |
  | starengine `1755415` | `starengine-neoforge-26.1.2-1.1.0.jar` | 98,970 B | **`9320031`** | **release** |
  | starengine `1755415` | `starengine-fabric-1.20.1-1.1.0.jar` | 112,634 B | **`9320032`** | **release** |

- **gameVersions（离线表解析）**：neo 1.21.1 `[9638,9639,11779,10150]`；forge 1.20.1 `[9638,9639,9990,7498]`；neo 26.1.2 `[9638,9639,16082,10150]`；fabric 1.20.1 `[9638,9639,9990,7499]`（9638=Client / 9639=Server）。
- **CF 更新日志入参**：本体两文件用 `release/1.5.0-rc.1/PLAYER_CHANGELOG.md`（英文 23,134 字符，与 1.3.x 的「三条生产线共用英文版」惯例一致）；库四文件用 `temp/cf-changelog-starengine-1.1.0.md`（1,335 字符 = 库 `CHANGELOG.md` 的 `## 1.1.0` 段 + 安装说明；temp 不入库）。
- **库上传必须 `--project-id 1755415 --no-relations`**：`find_line()` 只按 loader+mc 匹配 `curseforge.json` 的线，库 jar 会命中 astral 的 `neoforge_1.21.1` / `forge_1.20.1` ⇒ 不带 `--no-relations` 会把 curios / mixinbooster 挂到库文件上（详见 §6 坑 23）。
- **判据**：两仓 `rev-list --count origin/...` = 0 / 0；6 次上传均打印 `[OK] 上传 … fileID=<id> HTTP 200`（CF 不回显，判成功以 Upload API 返回 fileID 为准；cfwidget 列表缓存会滞后，勿据此判失败）。**未做**：`update-info`（项目简介 / 分类未改）、tag / GitHub Release（用户未下达）、P1 两线未上传（正式版前不验证）。

### 4.1m 调酒师立牌治疗效果改为按张数（2026-10-25 21:0x–21:3x）—— 静态判据全绿，**本条无实机**

- **问题定性（用户裁决「修复调酒师立牌的计算错误」）**：`JillSignItem#resolve` 里攻击牌 `attack * 2.0D`、防御牌 `defense * 2 * DiceCombatModifiers.PLAYER_ARMOR_PER_DEFENSE` 都按张数叠加，唯独治疗是常量 `target.heal(HEAL_AMOUNT)`（`HEAL_AMOUNT = 2.0F`）⇒ 选 1 张还是 3 张效果牌都只回 2 点，属**计算错误**。
- **改动**：两线 `item/sign/JillSignItem.java` 改 `target.heal(HEAL_AMOUNT * effect)`（1 张 2 点 / 3 张 6 点），保留 `if (effect > 0)` 守卫与「全效果牌不给计时器/图标」分支；仍为**一次** heal ⇒ 治疗事件只触发 1 次（多萝西「温暖」只 +1 层、赵赐福溢出/治疗失效事件各只结算一次）。javadoc / 常量注释 / 行内注释同步。forge 线同形（仅 `Operation.ADDITION` 与 `JILL_MIXED.get()` 的平台差异）。
- **文案六处 + 三份玩家日志 + 两份 CHANGELOG**：`tooltip.astral_dice.sign.jill_active`（JIL-L1）与 `astral_dice.guide.entry.jill_sign.1`（JIL-L2）在 neo/forge × zh_cn/en_us/ja_jp 共 6 份 lang 里改为「每张效果牌 2 点、按张数叠加（3 张共 6 点）」；ja 另把 `jill_sign.2` 的「固定回復のみ」改成「回復のみ」、en 的 "only the flat heal" 改成 "only the heal"；`release/1.5.0-rc.1/PLAYER_CHANGELOG_ZH.md` / `PLAYER_CHANGELOG.md` / `PLAYER_CHANGELOG_MCMOD.html` 同句订正；`CHANGELOG_ZH.md`（`#### 立牌与技能`）与 `CHANGELOG.md`（`#### Signs & Skills`）各追加 1 条。
- **守门同步**：`tools/verify_sign_balance_changes.py` 的 JIL-1 由「固定治疗 `target.heal(HEAL_AMOUNT)`」改为「按张数叠加 `target.heal(HEAL_AMOUNT * effect)`」并新增 forbid 旧写法；`jill_lang_specs()` 三语 req/forbid 换成「每张 2 点 / 按张数叠加 / stacks per card / 枚数分加算」，旧文案「固定 2 点」「不按张数叠加」「flat 2」「枚数分ではない」列入 forbid。
- **判据（实测）**：`python tools/verify_sign_balance_changes.py` = `GATE: PASS (760 项全部满足)`；`python tools/verify_defense_unit_parity.py` = `GATE: PASS（42 项）`；`pwsh -NoProfile -File tools/check_lang_sync.ps1` exit 0（四线 key 全一致：1,060 / 1,060 / 836 / 844）；`python -B docs/agent/verify-split.py` 8 项全 `[OK]`；`gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes` **BUILD SUCCESSFUL**。⚠️ `jill_active` 的既有结构标记 WARN（彩色码计数 zh 16 / en 12）**HEAD 就已存在**（HEAD 为 zh 14 / en 10，本轮两语各 +2，差值不变），非本轮引入。
- **同轮多萝西只读取证（未改代码）**：队友那 1 点攻击力**没有状态效果**，只是瞬态属性修饰符 `astral_dice:dorothy_partner_attack`（`ATTACK_DAMAGE` +1 / `ADD_VALUE` / `addTransientModifier`，无图标、不进效果栏、死亡重登即丢）；**有效期 = 施放者主动窗口结束**（`WINDOW_TICKS = 1200` = 1:00，`DorothySignItem.java:35`），**不是「获得后 1:00」** ⇒ 只有施放瞬间触发的队友才吃满 1:00，越晚触发剩得越少；`applyTempAttack(...)` 的 `until` 形参未被使用（`DorothySignItem.java:144-148`），清理全靠施放者 `onCurioTick` 的窗口结束分支（`:123-134` → `clearPartnerBuffs` `:151-160`）与 `onUnequip`；唯一可见状态效果 = **温暖 `astral_dice:dorothy_warmth`**（被动叠层，与本加成无关）。⚠️ 本段是**改动前**的只读结论，随后用户裁决改为【交际花】效果 ⇒ 现状见 **§4.1n**。

### 4.1n 多萝西立牌队友加成改为状态效果【交际花】+ 删除触发节流（2026-10-25 21:1x–21:4x）—— 静态判据全绿，**本条无实机**

- **用户裁决（m14537）**：「为多萝西主动为队友提供的攻击力加成增加状态效果【交际花】（使用多萝西立牌图标）：其有效期限定为 1:00，与多萝西主动技能无关联。同时移除多萝西主动技能接触队友获得攻击力加成的间隔（实际上每个人只能获得一次，多萝西自己叠层，不会出现反复刷的情况）」。
- **新增效果**：两线 `effect/DorothySocialiteEffect.java`（`dorothy_socialite`，BENEFICIAL，`0xFF7FA8`，`DURATION_TICKS = 1200`、`ATTACK_BONUS = 1.0D`）——加成的**唯一事实源**是效果自带的 `ATTACK_DAMAGE` 属性修饰符：Neo `ResourceLocation.fromNamespaceAndPath(MODID, "dorothy_socialite_attack")` + `Operation.ADD_VALUE`（`amp -> ATTACK_BONUS`）；Forge UUID `d4b20452-7270-5185-ab1e-46ce1b3325ab`（= `astral_dice:dorothy_socialite_attack` 的 URL 命名空间 UUIDv5，口径已用 `unwavering_armor`=`a941d5ed-605a-55c2-834c-4cf3ba28dab0` 反证）+ `Operation.ADDITION` + `getAttributeModifierValue` 覆写。`effect/ModEffects.java` 两线在 `DOROTHY_WARMTH` 后注册（neo `DeferredHolder` / forge `RegistryObject`，调用处 neo 直接传 holder、forge 用 `.get()`）。
- **图标**：`textures/mob_effect/dorothy_socialite.png` = `textures/item/dorothy_sign.png` 逐字节复制（448 B / 32×32），两 P0 线各一份。
- **时序解耦**：`DorothySignItem#applySocialite(Player)` 只 `addEffect(MobEffectInstance(DOROTHY_SOCIALITE, 1200, 0, false, true, true))` ⇒ 从**获得时**起算 1:00，主动窗口结束**不再回收**队友加成（删掉窗口结束分支里的 `clearPartnerBuffs`），重复获得只刷满时长、不叠层。施法者自身仍是可叠加的 `dorothy_bond_attack`（窗口结束清零）。
- **删节流**：`ModAttachments` 删除 `DOROTHY_PARTNERS`（uuid=tick 节流表）与 `dorothyPartnerUuids`，`tryTriggerDorothyPartner` 收敛为 `(Player, UUID)` 只保留「每人每窗口一次」；`DorothySignItem` 删 `DOROTHY_THROTTLE_TICKS`、`applyTempAttack`、`clearPartnerBuffs` 与 `"dorothy_partner_attack"` 修饰符 key。被动「每 10 秒回 2 血」（`PASSIVE_HEAL_COOLDOWN = 200`）**不动**。
- **文案**：6 份 lang（两线 × zh_cn/en_us/ja_jp）新增 `effect.astral_dice.dorothy_socialite`（交际花 / Socialite / 社交家）+ `.description`，并改写 `tooltip.astral_dice.sign.dorothy_active`（→ 自身 +1 攻仍受 1:00 窗口、队友走【交际花】固定 1:00 与窗口无关）与 `astral_dice.guide.entry.dorothy_sign.1`（同口径，去掉「每 10 秒一次」）。被动两条键（`dorothy_passive` / `dorothy_sign.2`）未动。
- **守门**：`tools/verify_sign_balance_changes.py` 新增 DOR-4 组（效果类存在 + `DURATION_TICKS = 1200` + `ATTACK_BONUS = 1` + 属性修饰符 key/UUID + `ModEffects` 注册 + 2 处 `applySocialite(other)` + 两参 `tryTriggerDorothyPartner` + 图标存在 + 旧的 `DOROTHY_THROTTLE_TICKS`/`dorothy_partner_attack`/`applyTempAttack`/`clearPartnerBuffs`/`DOROTHY_PARTNERS`/`dorothyPartnerUuids` 全部 forbid），docstring 同步；`docs/agent/AGENTS-full.md` 效果计数行按实测订正为 **67 / 67 / 53 / 53**。
- **判据（实测）**：`gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes` **BUILD SUCCESSFUL**；`python tools/verify_sign_balance_changes.py` = `GATE: PASS (801 项全部满足)`（变异测试：移走 forge 图标 ⇒ `FAIL DOR-4 [forge-1.20.1] 缺少图标…` / `GATE: FAIL (1 项 / 共 180)`，证明 DOR-4 非空转）；`python tools/verify_effect_icons.py` = `RESULT: PASS`（四线 240 个注册、覆盖 100%，P0 两线各 `registered=67 covered=67 missing=0`）；`pwsh -NoProfile -File tools/check_lang_sync.ps1` exit 0（四线 key 全一致，P0 两线各 **1,062** 键；`dorothy_passive` / `jill_active` 三条 WARN 为既有彩色码计数差）；`python -B docs/agent/verify-split.py` 8 项全 `[OK]`（AGENTS-full 4,085 行 / 红线区 13,404 字符 / AGENTS-dsh 25,915 字符）。
- **文档**：`CHANGELOG_ZH.md`（`#### 立牌与技能`）与 `CHANGELOG.md`（`#### Signs & Skills`）各追加 1 条；`release/1.5.0-rc.1/PLAYER_CHANGELOG_ZH.md` / `PLAYER_CHANGELOG.md` / `PLAYER_CHANGELOG_MCMOD.html` 的「多萝西立牌 · 主动」段同句订正。
- **P1 两线未移植**（26.1.2 / fabric 均无 `DorothySignItem`）⇒ 本项无 P1 欠账。

### 4.1o 双版本实机：【交际花】状态效果 + 删节流（2026-10-25 21:22–21:31）—— 1.21.1 / 1.20.1 全 PASS

- 环境：1.21.1 `--phase env` ⇒ `MT_WORLD: OK — 世界重建`；`--phase launch` ⇒ `MT_LAUNCH: OK (54s)`（AutoJS 端点 `http://127.0.0.1:25585/mcp`）。1.20.1 `--phase env` ⇒ `MT_WORLD: OK — 种子快恢复`（`MT_ENV_AUTOJS: 跳过 — 1.20.1 线未接入 AutoJS`，但 `AUTOJS_MOD=detected — AutoJS-forge-1.20.1-1.0.0-dev.jar` 仍在 `run/1.20.1/mods`）⇒ `--phase launch` `MT_LAUNCH: OK (49s)`。两线全部判据都取自 AutoJS `server/eval_js` 运行时读数（非截图）。
- 角色铺设（两线同一套）：Curios 槽位实测只有 `dice` / `stand` / `chip`（**立牌槽 = `stand`**）；`dice` 槽塞 `astral_dice:golden_dice`、`stand` 槽塞 `astral_dice:dorothy_sign`；Carpet 假人 `player Bot spawn` + `team add socialite` + `team join socialite Dev/Bot` + `tp Bot Dev` ⇒ `PartyRelations.isSameTeam(dev, bot)=true`、`distanceToSqr=0`。
- **两道闸门（务必记住）**：① 立牌 `curioTick` 有总闸门 `CurioSlotUtil.hasDiceEquipped`（`item/sign/BaseSignItem.java:47-49`）——**只装立牌不装骰子时 `onCurioTick` 整段不跑**（首轮现象：窗口到期而队友零效果）；② `/astralprobe equipslot stand …` 对该槽**无效**（slot0 仍 air、latest.log 无 `AP_DSW` 行），必须走 `CuriosApi.getCuriosInventory(p).get()/resolve().setEquippedCurio("stand", 0, stack)`。
- 主动窗口用附件模拟真实施放：`ModAttachments.setDorothyWindowEnd(dev, now + 1200)`（与技能走同一真值）。

| 判据 | 1.21.1（neo） | 1.20.1（forge） |
|---|---|---|
| 效果注册 | `found=true` / `effect.astral_dice.dorothy_socialite` / `BENEFICIAL` | 同（`BuiltInRegistries.MOB_EFFECT.get(...)`） |
| 队友获得效果 | `botHas=true`、`dur 1200`（254t 后 946）、`amp=0` | `botHas=true`、`dur=1056`、`amp=0`、`visible=true` |
| 队友攻击力 | `1 → 2`（修饰符 `astral_dice:dorothy_socialite_attack` / `amount=1.0` / `ADD_VALUE`） | `1 → 2`（`ADDITION`；**id = `d4b20452-7270-5185-ab1e-46ce1b3325ab`**，与设计 UUIDv5 一致） |
| 重复获得 | `dur 946 → 1200`、`amp` 仍 0（只刷时长不叠层） | 新窗口内 `dur 675 → 1161`、`amp` 仍 0 |
| 施法者自身 | `dorothy_bond_attack` +1（`atk 1→2`） | 同（`atk 2`，修饰符 `dorothy_bond_attack`） |
| 窗口解耦 | 窗口到期（`windowEnd=0` / `devBonus=0` / `atk` 回 1）后**队友仍余 1140t 效果** | 同：`windowEnd=0` / `atk` 回 1，队友仍 `dur=675` / `atk=2` |
| 删除节流 | 第二个假人 `Bot2` 入队后 **40t 内**即入账（`devBonus 1→2`、`atk 3`；两队友 `dur≈1160 / 904`、`amp=0`）⇒ 旧 200t（10 秒）节流确已不存在 | 未重复跑（两线代码同形，已由 1.21.1 证） |
| 客户端呈现 | `getDisplayName()` = **交际花**；两线 jar 内 `textures/mob_effect/dorothy_socialite.png` 448 B | 同 |

- 收尾：1.21.1 `--phase stop --force` ⇒ `MT_STOP: OK (1s) — 收停完成，无本流程残留进程`；1.20.1 同（`已收停 2 个本流程进程`、`MT_STOP: OK (1s)`）；`Get-Process java` 仅剩 Gradle 守护（pid 1760 / 8548）。
- **未覆盖（如实登记）**：① 真实按键施放（本轮用附件模拟窗口，未按键走 `BaseSignItem` 主动链路）；② 玩家**死亡 / 重登**后队友效果与自身加成是否按预期清空（瞬态效果默认随死亡清除，未实测）；③ 同一窗口内反复进出（「每人每窗口一次」已由 `Bot2` 一处证实，进出重入未逐帧验）；④ 视觉截图（本轮以运行时读数 + 客户端 `getDisplayName()` 为准）。

### 4.1p `1.5.0-rc.2` 上传 CurseForge（2026-10-26 11:3x–11:5x）—— 两线全 `HTTP 200`

- 入口（一条命令两线）：`python tools/curseforge_upload.py push --apply --proxy http://127.0.0.1:7897 --changelog release/1.5.0-rc.2/PLAYER_CHANGELOG.md --jar neoforge-1.21.1/build/libs/astral_dice-1.5.0-rc.2+neoforge_1.21.1.jar --jar forge-1.20.1/build/libs/astral_dice-1.5.0-rc.2+forge_1.20.1.jar`；凭据 = 本机 `.curseforge/token`（36 B legacy upload token），`x-api-key` 仍回退同一 token（本轮没跑 `update-info`，用不到）。
- 结果：`[1] [OK  ] 上传 astral_dice-1.5.0-rc.2+neoforge_1.21.1.jar  fileID=9329558  HTTP 200` / `[3] [OK  ] 上传 astral_dice-1.5.0-rc.2+forge_1.20.1.jar  fileID=9329560  HTTP 200`；两次都打印「Cloudflare 拦截 urllib（HTTP 403）⇒ 改用 curl 上传」（curl 回退仍生效）。渠道 **beta**（`-rc` 自动派生）；gameVersions 四件套 = Client / Server / `1.21.1`|`1.20.1` / neoforge|forge；relations 随同一次 POST 带上（neo = curios + starengine；forge = curios + mixinbooster + starengine）。
- **次源判据（本轮新增，独立于 CF 的返回值）**：ForgeCDN `HEAD https://mediafilez.forgecdn.net/files/9329/558/astral_dice-1.5.0-rc.2+neoforge_1.21.1.jar` ⇒ **200 / Content-Length 2,589,502**；`…/files/9329/560/astral_dice-1.5.0-rc.2+forge_1.20.1.jar` ⇒ **200 / 2,634,295** —— 与本地 jar 字节数逐一相等（sha256 neo `b562974217bc726bff838e03fded6a77159e683484da01df44e2d2f9155687c6`、forge `3c7b089de70c0d7d6b0a7ed19ff06655c2e54d6b7a1501b7120aefee42e3dba8`）。
- **cfwidget 镜像滞后**：上传后 10 分钟内 `api.cfwidget.com/minecraft/mc-mods/astral-dice` 仍是 38 个文件、不含 rc.2（rc.1 的两件也是隔天才出现）⇒ **不能据此判失败**，与 §4.1l 同口径；`https://www.curseforge.com/…/files/<id>` 对 rc.2 与 rc.1 对照件一律 Cloudflare 403，同样不作判据。
- 失败前置（第一跑 400，改参数重跑，**无副作用**）：`{"errorCode":1002,"errorMessage":"Error in field metadata: Required properties are missing from object: changelog. …"}` —— 根因见 §6-28（`pick_changelog` 把 `1.5.0-rc.2` 截成 `1.5.0` 去找目录）。
- 未做：`update-info`（项目简介/说明不变）、P1 两线（按发布红线不传）、CF 侧「已发布」人工复核（需作者后台）。

### 4.1q 玩家侧更新日志：补齐「本版变化」+ MCMOD 版重出（2026-10-26 11:5x–12:0x）—— 纯文档，无代码 / 无实机

- **起因**：`release/1.5.0-rc.2/` 三份玩家侧文件（`PLAYER_CHANGELOG_ZH.md` / `PLAYER_CHANGELOG.md` / `PLAYER_CHANGELOG_MCMOD.html`）是 `51c6c97e chore(release)` 从 rc.1 **整份拷贝**的 —— 与 `release/1.5.0-rc.1/` 同名文件字节数完全相同、`diff` 只有 4 行版本号串（标题 / `已发布：` / 两个 jar 名）。rc.2 的三处真改动（`85c9d2f6` 【交际花】、`e9dab567` 调酒师按张数、`b58e1df4` 立牌名订正）是**先改了 rc.1 的这三份再被拷贝**，所以正文口径是对的，但**缺一段「本版相对上一版的变化」** ⇒ 从 rc.1 升上来的玩家看不出改了什么（CF 上传的 `--changelog` 与 GitHub Release 正文都直接取这份文件）。
- **改动**：三份各新增一节，位置 = 「两个构建」说明之后、`安装与前置` 之前 —— ZH `## 本版（1.5.0-rc.2）相对 1.5.0-rc.1 的变化`（自 :17，14 行）/ EN `## Changes in 1.5.0-rc.2 since 1.5.0-rc.1`（自 :17，18 行）/ MCMOD `[h2=一、本版（rc.2）相对 rc.1 的变化]`（自 :15，7 行，含 3 个 `<li>`）。三条内容 = ① 多萝西【交际花】：同伴 **+1 ATK**、**固定 1:00**、**从获得时计时**且与施放者主动窗口**解耦**（窗口结束不回收）、重复只刷时长**不叠层**、**删 10 秒触发节流**，多萝西自身 +1/人与被动「每 10 秒回 2 血」不变；② 调酒师主动治疗**按效果牌张数叠加**（1 张 2 / 2 张 4 / 3 张 6，仍只结算一次 heal）；③ 立牌中文名订正（**熊猫人 → 上班族** Padman Sign、**提尔 → 教主** Cult Leader Sign、**酒保 → 调酒师** Bartender Sign，纯文案）。
- **MCMOD 版编号重排**：新节占「一、」，原 `一、安装与前置` … `八、许可变更` 顺延为 `二、`…`九、`（脚本按旧编号**倒序**替换，共 8 处）；重排后 h2 序列 = 一（本版变化）/ 二 安装与前置 / 三 新增内容 / 四 战斗系统重做 / 五 平衡与数值调整 / 六 BUG 修复 / 七 已知问题与设计说明 / 八 文案与手册 / 九 许可变更。
- **判据**：`PLAYER_CHANGELOG_ZH.md` **38,695 B / 358 行**、`PLAYER_CHANGELOG.md` **38,817 B / 369 行**，两者**全 CRLF、裸 LF = 0**；`PLAYER_CHANGELOG_MCMOD.html` **41,192 B / 275 行**，按 rc.1 起的既有口径保持 **LF-only**（`\r\n` 计数 0，三份里只有它是 LF；`.gitattributes` 未给 html 钉 `eol`、仓库 `core.autocrlf=true` ⇒ 对象库里仍存 LF、新克隆检出会变 CRLF，对 mcmod 粘贴无影响）；三份都命中「交际花 / Socialite」「上班族 / Padman」「按张数叠加 / per effect card」三组关键词（`grep -c` 均 ≥ 1）。
- **未做（如实登记）**：仓库里**没有** MCMOD 生成脚本（`grep` `mcmod|MCMOD` 在 `tools/*.py` 命中 0；该文件自 rc.1 起就是手工维护的 mcmod.cn 语法：`[h1=]/[h2=]/[h3=]` 标题 + 内联 HTML 正文，且正文与 ZH 版**并非逐句一一对应**，是手工压缩版）⇒ 本轮用脚本做「插入 + 编号重排」，**未**做全量重新生成。若要让 MCMOD 版永远跟 ZH 版逐句同步，需另开一批写 `tools/gen_player_changelog_mcmod.py`（判据可用「用 rc.1 的 ZH 重生成 rc.1 的 html 并逐字节比对」验证转换器正确性）。

### 4.2 历史读数

pre.5 及更早全部实机读数见 `docs/handover-1.5.0-pre.5-main.md` §4（含 4.8 pre.4 全量双线 10 跑 10 PASS、4.11–4.17 pre.5 各批、4.19 Jade 图标留白版）。

## 5 遗留与欠账

### Blocker

- 无（编译/守门全绿）。

### Major

1. ~~pre.6 产物未建未投~~ → **已完成**（2026-10-25 11:55）：`:neoforge-1.21.1:build :forge-1.20.1:build` 触发 `pushTo*`，四目标 sha256 全 MATCH（§2）；库 jar 成对在位（neo 98,737 B / forge 103,609 B，`1.1.0-pre.2`）。
2. ~~本批局部实机~~ → **已完成**（2026-10-25）：星光兑换 5 组（§4.1）+ 筹码商店刷新源 R0–R5 / 38 tooltip + 21 手册 / `ame_note` 染色（§4.1b），**双版本全 PASS**。
3. ~~`CHIP-REGEN-*.json` 已失效~~ → **已退役**（2026-10-25 用户裁决「旧用例直接退役」）：`scripts/test/cases/CHIP-REGEN-1.20.1.json` / `CHIP-REGEN-1.21.1.json` **两文件已删除**（它们断言「摆形态 → 等 tick → 自动 +1」，随 `ChipShopRegenTicker` 删除永久不成立）。新语义（刷新 3→2→1 + anchor 首写 + 升星取走 1→2 + 死亡不自愈）已在 §4.1b R2/R3/R5 双线实机取证；若日后要**自动化**回归再按这套判据重写，勿复活旧文件。探针 `/astralshop cregen` 仍保留（只摆状态、不再指望 ticker 推进）。
4. **`tooltip_color_audit` 存量 144 处**（9 键，2026-10-25 复跑）：`jill_active` 36 / `storm_approaching.description` 18 / `problem_delivery.description` 18 / `dorothy_active` 18 / `red_heat_warning.description` 16 / `fanny_active` 12 / `jill_passive` 8 / `card.barricade` 6 / `deepseek_passive` 6（+ 若干零散项）。原 `card.repulsion` 的 6 处 `§e20:00§7` 已按染色规则改为 `§920:00§7`（**时间值必须 §9**），复跑后 repulsion 相关 **0 处**；其余 9 键属既有基线债，仍未动。
5. **8 条「代码与文案都不符」**（已报用户，未改 tooltip）：bite 觉醒按撕咬张数 / huo_card = `CREATURE_OR_RIVAL` / magic_quiver 真闸门 = 目标已有标记 + 先用过伤害牌 / railgun 只 `bypasses_armor` / 三张卡 `NON_HOSTILE` / member_recommendation 只数主背包+副手 / repulsion 单次最多 5 点耐久 / airbag 掉虚空救不了。 **2026-10-25 用户裁决：不管（黑箱）** —— 不修文案也不改行为，仅作事实登记，不再列为待办、不再每轮提示。
6. **P1 两线欠账**（正式版前不移植，仅登记）：JEI 筹码 / `BeamFx` / `GlobalEventGate` / `RepulsionEntity` / `CardShopMenu#planPlacement` / `EventDrawBlock`；筹码效果图标仍带边框；史莱姆被动仍 20 tick；misaki 仍 `legendary()` + 黄金星盘 + 死亡清零剑气；pre.5 冷却指示器未移植。 **2026-10-25 用户裁决：继续欠着，等把正式版做完了再考虑移植的事。**
7. ~~陈旧锁 `kimi.lock`~~ → **已结案（2026-10-25 用户裁决「删」）**：实测 `.agent-locks` 目录已空、该文件不存在（`PID=26580` 的进程早已退出），无需操作。
8. ~~强化拒止物品形态未实机~~ → **已完成**（2026-10-25 15:0x，双版本 7 项判据全 PASS，§4.1c）。owner 闸门（非放置者命中被拒）也已双版本实机四态 PASS（§4.1c 末节，2026-10-25 15:3x）。仍有两个**待用户裁决**的小项：~~(a) 是否给新物品加帕秋莉手册第 4 页~~ → **已完成**（2026-10-25 §4.1e：同条目追加第 4/5 页，两线 × 三语）；(b) `card.barricade` 的 6 处 `§e20:00§7` —— **用户裁决「路障不动」，保持原样**（2026-10-25），`tooltip_color_audit` 的 144 处存量继续保留。


9. ~~`mt_env` 的 modernfix 钉子在整合包升版后 BLOCKED~~ → **已修复（放宽，2026-10-25 用户裁决「放宽」）**：`scripts/test/mt_env.ps1:48` 钉子改为 `*modernfix-neoforge*5.27.*.jar` + `Target` 兜底 `modernfix-neoforge-5.27.27+mc1.21.1.jar` + 新增 `Cleanup = 'modernfix-neoforge-*'`；复制循环改为**落位名按源文件名派生**（剥整合包的 `[中文] ` 前缀，与 `Install-MtProbeHost` 同口径）并先清同族旧版本（两个 ModernFix 同目录会让 FML 起不来）。判据（实测 2026-10-25 19:2x）：`--phase env` **EXIT 0**，日志含 `MT_MODS: 清理旧同族 modernfix-neoforge-5.27.26+mc1.21.1.jar` 与 `MT_MODS: OK — Sodium/Iris/ModernFix 新装 1 个`，`MT_WORLD: OK — 世界重建`；`run/1.21.1/mods` 只剩 `modernfix-neoforge-5.27.27+mc1.21.1.jar`（614,138 B）。
10. ~~防御力折算 1:1 已修，但产物未重建 / 未实机~~ → **已完成**（2026-10-25 17:0x，§4.1i）：`:build` 重建 + 四目标 sha256 实测 MATCH，两线实机 `+8 防御力 ⇒ 防御力 10`；**同批修掉实机暴露的取整吞点**（新增 `effectiveArmorPoints`，守门加 D9，42 项 PASS）。P1 两线仍是旧 `× 2.0` 折算（各 5 处），已登记 `docs/compat-1.5.0-porting.md` §2 主题行 + §4 第 4 条。
11. **GitHub 发布清理 + 「只发 P0」发布红线（2026-10-25 用户裁决）**：用户裁决「除非有要求，否则默认只上传 `forge-1.20.1` 与 `neoforge-1.21.1`」——P1 两线（`neoforge-26.1.2` / `fabric-1.20.1`）**从未获授权移植**，其编译产物一律视为不可用（用户已手工删掉误传的 CurseForge 内容）。本轮落地四件：
    - 红线区〈子项目优先级与默认规则〉长行末尾追加「⛔ 发布投放红线（必须遵守,2026-10-25 用户裁决…）」句 —— **红线区行数不变**，`verify-split.py` 八项仍全 [OK]。
    - `.github/workflows/build.yml` 现 **255 行**：**整段删除** `Create/Update Fabric release (pre-release)` 步骤（那是「每次 multi-main push 都建一个 fabric pre-release」的根源）、Release 附件 `JARS=(...)` 去掉 `neoforge-26.1.2`、artifact 步骤合并为 `Upload Astral Dice JARs (P0 only)` 并删掉 fabric artifact 步骤（计数复核：`neoforge-26.1.2/build/libs` = 0、`astral_dice-jar-fabric` = 0）。
    - `tools/curseforge_upload.py` 新增 `P0_UPLOAD_LINES = {("neoforge", "1.21.1"), ("forge", "1.20.1")}` 与 `guard_p0_only(loader, mc, jar, allow_p1)`，在 `build_jobs()` 解析出 `loader`/`mc` 后立即调用；P1 jar 默认 `[BLOCKED]` + `SystemExit`（exit 1），仅显式 `--allow-p1` 放行（`push` 与 `update-info` 两个子命令 parser 各加一处）。
    - 两仓 Release/tag **全清**：`merlin-kitsune/astral_dice` **28 releases + 31 tags → 0/0**；`merlin-kitsune/starengine` **4 releases + 7 tags → 0/0**；`git -C <repo> ls-remote --tags origin` 计数 **0**。删除口径 = `gh api -X DELETE repos/<repo>/releases/<id>` 逐条 + `repos/<repo>/git/refs/tags/<name>`（tag 名先 `[uri]::EscapeDataString()` 编码）。
    - 判据（实测）：`python -m py_compile tools/curseforge_upload.py` EXIT 0；fabric jar `push --dry-run` ⇒ `[BLOCKED] astral_dice-1.3.5-alpha.1+fabric_1.20.1.jar 属于 P1 线（fabric / MC 1.20.1）` / EXIT 1；P0 neo jar `push --dry-run` ⇒ 正常出计划（projectId 1662159 / token 就绪）。
    - CI 实证（2026-10-25 19:0x）：push `4848caf7` 触发的 run **40186748662 失败** —— `build.yml` 首两行被脚本污染（见坑 26）⇒ YAML 解析失败、一个 job 都没起（`gh api .../runs/<id>` 的 `jobs` 为空、run `name` 显示成文件路径）。已由 **`fb930c61`** 修复（从 `4848caf7^` 取原件重打补丁，并 `js-yaml` 校验：`jobs=build` / `steps=18` / `branches` 三条 / Release 只剩 P0 两步 / `JARS` 只含 P0 两 jar）。修复后 run **40186866765 success**，artifact 仅 `astral_dice-jars`（4,474,594 B，解包实测只有 `astral_dice-1.5.0-rc.1+forge_1.20.1.jar` 2,632,606 B 与 `astral_dice-1.5.0-rc.1+neoforge_1.21.1.jar` 2,587,570 B，**无 fabric artifact**），全程 **releases=0 / tags=0**（rc 版本按规则不自动打 tag，Release 步骤因 tag 为空跳过）。

12. **GitHub Release 新规则：tag 只按 mod 版本（2026-10-25 用户裁决）**：tag **不得**按游戏平台命名（`fabric-1.5.0-rc.1` / `neoforge-…` 一律禁止）、禁止 `v` 前缀、禁止 `+加载器` 后缀；同一版本的**多平台 jar 放进同一个 Release**；带 `-rc` / `-pre` / `-alpha` / `-beta` 的版本**照样打 tag**（rc.1 与 rc.2 是两个版本），并标 `--prerelease` / `--latest=false`（只有正式版才 `--latest`）。落地：① 红线区〈子项目优先级与默认规则〉长行末尾追加「② tag 与 Release 命名红线」（`AGENTS-dsh.md` 与 `AGENTS-full.md` 同文、**行数不变** ⇒ `verify-split.py` 九项全 [OK]，红线区 12,723 → **13,404** 字符）；② `.github/workflows/build.yml`：`Read base version` 只剥 `+加载器` 后缀（保留 `-rc`）、守门正则改 `^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z][0-9A-Za-z.-]*)?$`、新增 `is_prerelease` 输出；`Create Git tag` **删掉**「版本含 `-` 就跳过」的分支（旧口径导致 rc/pre 永远没有 tag）；Release 步骤按 `is_prerelease` 传 `--prerelease --latest=false`（正式版 `--latest --prerelease=false`），附件仍是 P0 两 jar 挂在**同一个** Release。判据：`js-yaml` 解析 OK（`jobs=build` / `steps=18` / 无 tab / `tagTriggers=["[0-9]*"]` 能匹配 `1.5.0-rc.1`）。

### Minor

1. `docs/handover-2026-10-24-member-detective.md` 等 Kimi 批次文档已入库，措辞按 2026-10-24 事实；后续改动若改变其中数字需回填。
2. 仓库根 `undefined/temp/`（2026-10-23 历史临时脚本残留，被 `temp/` 规则忽略）未清理。
3. `misaki_sign.json` 是手改的（与 datagen 是否逐字节一致未验）；下次 `runData` 后须 diff。
4. **子代理模型口径已收紧为「唯一」**（2026-10-22 用户裁决）：一律 `provider: "deepseek-official"` + `model: "deepseek-flash"`（DeepSeek-V4.1-Flash），不得换更强 / 其它 provider 的 model；原「确需深推理才换更强 model」「其它模型仅作备用」两种口径**作废**；`list_subagent_models` 只核注册表、不换模型；该路由失效 ⇒ 停下报用户。同步文件 4 个：`docs/agent/AGENTS-dsh.md`（§A 第 6 条 / §B ③ / 红线区第 7 条⑧）、`docs/agent/AGENTS-full.md`（红线区第 7 条⑧同文 + 子项目修改默认规则实施分工 + 第 3 节纪律第 6 条）、`docs/agent/delegation-runbook.md`（§2 派活）、`docs/agent/AGENTS-kimi.md`（§0 第 2 条，使用 Kimi 路由 `DeepSeek/deepseek-flash`）。
5. **HUD 攻击力显示口径**（2026-10-25，§4.1g）——**已双版本实机 PASS**（§4.1i：装电流剑 + 充能 8 ⇒ client 显示 3；removeAll 后回到 1；主手换钻石剑仍 3）。余下两处已知边界：
   - **目标依赖型修饰器**（秘密侦探「目标带标记 +3」= `MarkManager.getLevel(ctx.target)`、调查阶段依赖 `isBlessingTarget(ctx.target, ctx.attacker)`）在客户端求值时上下文的目标位只能填**玩家自身**（`attackPowerBase` 构造 `DiceCombatContext` 的占位）⇒ HUD 无法体现「对某类目标的加成」，只显示不依赖目标的那部分。属显示层固有上限，不是缺陷。
   - **骰战随机点数已排除**（`ctx.displaySnapshot` 下攻击卡不掷）⇒ HUD 不含随机加成，符合裁决。
    - **P1 两线未移植**（26.1.2 / fabric）—— 2026-10-25 用户裁决「继续欠着，等把正式版做完了再考虑移植的事」（§5-Major 6）。

6. **创造栏收尾仅落 P0 两线**（2026-10-25，§4.1j）：P1（`neoforge-26.1.2` / `fabric-1.20.1`）的 `ModCreativeTabs.java` 仍列出强化拒止物品、筹码仍是旧顺序。⚠️ 移植时必须**同批调整** `tools/verify_fabric_assets.py` 第 7 项（`check_creative_tabs`：要求「所有注册物品都在创造栏」——与本条的隐藏口径相反，只对 fabric 线生效，暂未改）。
7. **护法减费卡面仅落 P0 两线**（2026-10-25，§4.1k）：P1（`neoforge-26.1.2` / `fabric-1.20.1`）的 `CardShopScreen` 没有 `cardFaceTexture` 分支、也没有 `attack_card_meito_discount.png` ⇒ 那两线仍显示原卡面（4 点）。移植时同批拷贴图 + 抄 `cardFaceTexture`（fabric 用 `new ResourceLocation(...)` 口径，26.1.2 按其 API 复核），并把 `tools/verify_meito_discount_face.py` 的 D2/D3/D4 扩到四线。

8. **CF 关系口径变更（2026-10-25，§4.1l）**：`tools/curseforge.json` 的 P0 两线（`neoforge_1.21.1` / `forge_1.20.1`）新增 `starengine(1755415)=requiredDependency`（lib 自 1.1.0 起不再内嵌）；P1 两线（26.1.2 / fabric）**未加**（它们仍内嵌库 jar）—— 若日后 P1 也改为独立前置，必须同批补登，否则同样缺库起不来。
9. **玩家版更新日志已整份重写（2026-10-25 19:4x，提交 `e185595c`）**：`release/1.5.0-rc.1/PLAYER_CHANGELOG_ZH.md`（287 行 / 30,207 B）与 `PLAYER_CHANGELOG.md`（293 行 / 30,349 B）按 `CHANGELOG_ZH.md` / `CHANGELOG.md` 的 1.5.0 段（L7–L773 / L7–L953）逐条重写，补齐卡牌商店与筹码商店全部经济参数、村民价格表、15% 伤害下限、跳字红绿口径、星币/星盘掉落、16 件配方换料、看板娘 25→100、筹码商店 5:00 冷却与空白筹码解冷却、抽奖盒 5 星币/5:00 等；删除全部 P1（26.1.2 / fabric）陈述；修正 `Bank Card-Restless` → `Bank Card-Unlimited`。重传走 CI（tag `1.5.0-rc.1` 已存在 ⇒ `gh release edit --notes-file` + `upload --clobber`）：run `40190519908` 全绿，Release `updated_at` 11:51:59Z、正文 43,140 字符、附件两份 rc.1 jar 已覆盖。
10. **CF 项目说明草稿的事实错误已就地修正（2026-10-25）**：`tools/curseforge-description.md` 第 31 / 93 行原写「Six new signs / 六张新立牌 = KAngel + Ame + 酒保 + 多萝西 + 修女 + 大肥鱼」——经 CHANGELOG_ZH.md 全文核对，1.5.0 段**只**新增 KAngel 与 Ame（`修女`/`酒保`/`大肥鱼` 全文 0 命中），已改为「两张新立牌 / Two new signs」并注明其余角色早于本版存在；同句「一身同体」订正为「一心同体」。该文件仍为**未提交修改**（CF 描述因 403 只能由作者后台手贴，等用户粘贴后决定是否入库）。
11. **P0 两个整合包投放核对（2026-10-25 20:0x，用户要求「确认所有最新构建版本已推到整合包中」）**：逐处核 sha256 ⇒ `run/1.21.1/mods`（astral rc.1 neo `59E12108…` + `starengine-neoforge-1.21.1-1.1.0.jar` `62C92595…`）、`run/1.20.1/mods`（astral rc.1 **devlibs named** `CB21FA24…` + `starengine-forge-1.20.1-1.1.0.jar` `E280AEAB…`）、`1.20.1-Forge 模组测试\mods`（astral rc.1 **reobf** `33717FA9…` + lib forge `73DA2E59…`）、仓库根 `build/libs`（两线 rc.1 同名同哈希）**全部一致**。但 `狐の航空学 Voxy Edition\mods` 查出**两处投放缺陷**并已就地修好：① **库 jar 缺失**（该目录无任何 `starengine*.jar` —— 1.5.0 起库不再内嵌，缺库必报 `Missing or unsupported mandatory dependencies`），已补 `starengine-neoforge-1.21.1-1.1.0.jar`（取自 `F:\MCProject\starengine\neoforge-1.21.1\build\libs`，`62C92595…`）；② **混入错误加载器的 jar** `astral_dice-1.5.0-rc.1+forge_1.20.1.jar`（1.20.1 Forge 产物躺在 1.21.1 NeoForge 整合包里），已**移入隔离区** `temp/quarantine/`（保留可回滚，未删除）。修复后该包 `mods` 内与本模组相关的文件恰为 2 个：astral rc.1 neo `59E12108…` + lib `62C92595…`。静态依赖判据：两线产物 `mods.toml` 内 `starengine` 依赖 `type=required` / `versionRange="[1.1.0,2.0.0)"`、`META-INF/jarjar/` 计数 **0**，库侧 `version=1.1.0+<加载器>` ⇒ 区间成立。本次**未启动游戏**（实机需用户放行）。

12. **调酒师治疗效果口径 = 一次 heal(总数)**（2026-10-25，§4.1m）：落地选择 `heal(HEAL_AMOUNT * effect)`（3 张 = 一次 6 点），**不是**「三次 heal(2)」——因此治疗事件只 1 次，多萝西「温暖」不会按张数叠层。若日后要求「每张各触发一次治疗事件」，需同批改 3 处：`JillSignItem` 的 heal 循环、`tools/verify_sign_balance_changes.py` 的 JIL-1、以及三语文案（每张独立结算的表述）。P1 两线（26.1.2 / fabric）**没有** `JillSignItem`，本项无需登记移植欠账。
13. **多萝西队友加成 = 状态效果【交际花】，不再是瞬态修饰符**（2026-10-25，§4.1n）：唯一事实源改为 `DorothySocialiteEffect` 自带的 `ATTACK_DAMAGE +1` 修饰符，**固定 1:00、从获得时起算、与主动窗口解耦**；窗口结束不回收、重复获得只刷时长。旧的 `dorothy_partner_attack` 修饰符、`applyTempAttack`、`clearPartnerBuffs`、`DOROTHY_PARTNERS` 节流表均已删除（§5 里若有旧描述按本条读）。P1 两线无该立牌，无移植欠账。

14. **`1.5.0-rc.2` 版本已落盘（2026-10-25，§4.1o）**：四线 `mod_version = 1.5.0-rc.2+<后缀>`、`release/1.5.0-rc.2/` 三份玩家更新日志（版本串已换，内容 = rc.1 + 调酒师治疗按张数 + 【交际花】）、`README*.md` 版本列同步；P0 两线已重建并投放四目标（§2 产物现状）。**已 push（`13a45619`）**，CI 自动建 tag `1.5.0-rc.2` 并从 `release/1.5.0-rc.2/` 取正文发布 prerelease（实测页面 200，两个 P0 jar 均在附件里）。**实机未覆盖项见 §4.1o 末行**（真实按键施放 / 死亡重登 / 窗口内反复进出）。

15. **`tools/curseforge-description.md` 已删除（2026-10-25，用户裁决）**：CF 项目说明已由用户**手动粘贴**到作者后台（`https://authors.curseforge.com/#/projects/1662159/description`），仓库内草稿（含本轮中英两版全文 + 一句话 Summary）随之删除，避免与线上正文双份漂移。**CF 的 Description 字段至今无法经 API 修改**（`.curseforge/token` 只是 legacy upload token，Core API 也没有该端点，见 `AGENTS-full.md:2294`），今后要改说明一律在 CF 作者后台直接改，仓库不再留副本。历史文档里对 `tools/curseforge-description.md` 的引用（`docs/AGENT-HANDOFF.md:106` / `:593` / `:617`、`docs/agent/AGENTS-full.md:4034`、`docs/prerelease-check-1.3.7-hotfix.md:48` / `:84`、`docs/handover-1.5.0-pre.5-main.md:654`）一律读作**历史记录**，文件本身已不存在。

## 6 坑（本批新增，后续继续追加）

1. **`git show HEAD:<file>` 是 LF**（仓库存 LF、工作树 CRLF 由 autocrlf 转换）⇒ 行级改写先 `replace(/\r\n/g,"\n").split("\n")` 再 `join("\r\n")`。
2. **forge 与 neo 的 lang 键序在 index 955 起分叉**（`block.astral_dice.barricade_collision` vs `block.astral_dice.chip_shop`；forge 另有 6 行缺 2 空格缩进、保留用户删掉的空行）⇒ **禁止整文件复制**，只按目标文件键序换值。
3. **`tools.edit` 插多行会把整文件转 LF** ⇒ 大文件改写用 node `fs` 全量读改写。
4. **子代理对 `temp/` 无写/删权限**，残留须主会话用 `pwsh` 清；`.gitignore` 的 `temp/` 是任意层级匹配。
5. **`withRefreshGranted` 不可是「重置」**（`min(MAX, left+1)`），且**死亡必须保留**（否则重生 = 变相补满）；forge 侧走 `AstralData` 白名单而非 `copyOnDeath`。
6. **`MemberRecommendationChipItem` 只数主背包 + 副手**，不含 Curios 卡牌栏（与用户文案一致）。
7. **备注区（`*_note` 键）回落码必须 `§d`**（`addSignNoteLines` 会把行内 `§7` 替换成 `§d`），否则踩 R0。
8. **`ICuriosItemHandler#setEquippedCurio` 在 Curios 9.5.1（1.21.1）不触发 `onEquip`**（字节码只做 `setStackInSlot`）⇒ 装备类效果（星光发放 / 筹码槽扩容）必须**等 1–2 个 tick** 再读，否则读出「什么都没发生」的假阴性；1.20.1 的 Curios 5.x 反而当场生效。另：**1.20.1 `CuriosApi.getCuriosInventory` 返回 Forge `LazyOptional`（`.orElse(null)`），1.21.1 返回 `Optional`（`.get()`）**；`getStacksHandler` 在 1.20.1 也要 `.isPresent()/.get()`。
9. **探针 `open` 会开店后立刻关**（否则 GUI 吞掉后续聊天命令）⇒ 要连点「星光兑换」必须在**同一次 AutoJS eval 内**顺序 `openkeep → exchange → close`（`performPrefixedCommand` 不走 GUI，不受影响）。
10. **探针输出通道 = 聊天** ⇒ `run\<线>\logs\latest.log` 的 `[CHAT] AP_...`；`mcp__autojs__kubejs_log` 返回 0 entries 属正常，别据此判失败。
11. **AutoJS/Rhino 口径**（1.21.1 rhino 2101 / 1.20.1 rhino 2001）：无 `server` 全局（用 `AutoJS.server()/AutoJS.player()`）；同一 eval 内不能 `function f(){}`（用 `var f = function(){}`）；`obj.getClass()` 不可用；`p.level` 是属性、`p.level.getGameTime()` 报错；返回值必须 `JSON.stringify((function(){...})())`。`StarLightManager` **没有** `getCap(player)`（实测 `Can't find method ... getCap(ServerPlayer)`）。
12. **`/astralshop reset` 只清 cd/余额/星光，不释放筹码的发放账本位** ⇒ 兑换前必须「卸下 → 等 tick → 装回」重新申领，否则读到 `star=0` 的空转结果（首轮 GUI 验证就栽在这里）。
14. **探针的 `StringArg.string()` 参数必须加引号**：`/astralprobe anvilstar "astral_dice:golden_dice" ANV`（`dice` = `StringArg.string()`、`tag` = `StringArg.word()`）；不加引号只在聊天里留一行被截断的 `...stral_dice:golden_dice ANV<--[此处]`，日志里几乎认不出是参数错。另 **`performPrefixedCommand` 在 Rhino 里返回 `undefined`**，不能当失败判据 —— 用 `say TESTPFC12` 能进日志即证明通道通。
15. **同一时刻只能一条线占用 AutoJS 的 25585**：1.21.1 未停就 launch 1.20.1 ⇒ 客户端日志 `[AutoJS]: Could not bind the MCP endpoint on 127.0.0.1:25585`（`java.net.BindException`），紧接着 preflight 报 `MT_INJECT_FOCUS: FAIL — 目标窗口无法置前台` 与「客户端未在运行」 ⇒ **先 `--phase stop --force` 另一线再 launch**。
16. **1.20.1 的 AutoJS 是 `run\1.20.1\mods\AutoJS-forge-1.20.1-1.0.0-dev.jar`（named）**：`MT_ENV_AUTOJS: 跳过 — 1.20.1 线未接入` 只表示**不再自动投放**，运行时照样有端点；判据看启动日志 `MT_LAUNCH: OK — AutoJS 端点已就绪`。
17. **`run/<线>/mods` 里的 astral_dice jar 会遮蔽 dev classes ⇒ datagen 静默不产出**（2026-10-25 实测）：`runData` 日志里 `Item Models: astral_dice finished` 与 `HashCache new count ... written: 0/1` 都正常，但新 `basicItem(...)` 对应的 `src/generated/.../models/item/xxx.json` **不存在**。根因：`run/1.21.1/mods/astral_dice-1.5.0-pre.6+neoforge_1.21.1.jar` 与 `neoforge-1.21.1/build/classes/java/main` 都声明 modid `astral_dice`，`UniqueModListBuilder` 选的是 **jar**（日志 `Selected file astral_dice-...jar for modid astral_dice`）⇒ 跑的是旧 jar 里的旧 `ModItemModelProvider`。修法：先把 `run/<线>/mods/astral_dice-*.jar` 移出（本轮移到 `temp/mods-bak-datagen/`），跑 `--rerun-tasks :<线>:runData`，再把 jar 拷回。
18. **`run_code` 自带 120s 程序级 deadline，`tools.pwsh` 的 `timeoutMs` 覆盖不到它** ⇒ 长任务（两线 `runData`、双线 `build`）必须同时给 `run_code` 的 `timeoutMs`，否则进程在中途被杀、日志看似「跑完了」。另：**bundled python 在本会话无法写文件**（PIL `im.save()` 对 `temp/` 与 `src/main/resources/` 均 `PermissionError: [Errno 13]`）⇒ 生成 png/二进制一律用 node（`fs` + `zlib` 手写 PNG 编码器），不要走 python 写盘。
13. pre.5 及更早的 32 条坑见 `docs/handover-1.5.0-pre.5-main.md` §6。

19. **`mt_env` 的 modernfix 钉子会因整合包升版而 BLOCKED（2026-10-25 实测）**：`scripts/test/mt_env.ps1:48` 的 `$script:NeoForgeMods` 写死 `*modernfix-neoforge*5.27.26*.jar`（Target `modernfix-neoforge-5.27.26+mc1.21.1.jar`），而整合包已升到 **5.27.27** ⇒ `--phase env` 直接 `MT_MODS: BLOCKED — 整合包缺少 *modernfix-neoforge*5.27.26*.jar`（EXIT **11**），整条 env 中断（Sodium/Iris/KubeJS 都还没走到）。本轮绕过：跳过 env 直接 `--phase launch`（`run/1.21.1/mods` 已有完整 14 jar 且库为 1.1.0）。**修法待裁决**：把钉子与 Target 同步到 5.27.27，或改成不带补丁号的宽匹配（`*modernfix-neoforge*5.27.*.jar`）。

20. **同一次批量改写里两文件的 EOL 可能不同**（2026-10-25 实测）：`combat/DiceCombatModifiers.java` 是 **LF**，同一提交里的 `combat/DiceCombatContext.java` 是 **CRLF**（仓库历史原因，不由 autocrlf 统一）⇒ 改写脚本必须**逐文件探测** `text.includes("\r\n")` 再拼多行锚点/插入块；固定用 CRLF 拼锚点会 `anchor missing`（本轮首跑即失败、文件未被写入，属安全失败）。

21. **Rhino 跨语言装箱**（1.20.1 KubeJS 侧）：`List<Integer>` 里塞 JS 数字会变成 `Double`，`CardShopShelf.store` 序列化时抛 `ClassCastException: Double cannot be cast to Integer`（`prices.add(4)` / `Integer.valueOf(4)` / `Integer.parseInt("4")` 三种写法都踩）；绕法 = 传**空** prices 列表 → `ensureLengths()`（Java 侧补 0）→ `withDiscounted(0, 4)`（形参是 `int`，跨界不装箱）。
22. **服务端 `CardShopOpener.open(player)` 不一定上屏**：1.20.1 实测 client `mc.screen` 一度是 `CardShopScreen`，随后被容器关闭包清成 `null`（而 `containerMenu` 仍是 `CardShopMenu`）⇒ 截图前改用探针 `/astralshop openkeep <tag>` 重开并当拍即截；1.21.1 无此现象。

23. **`--project-id` 只改目标项目、不改关系来源（2026-10-25 实测）**：`tools/curseforge_upload.py` 的 `find_line()` 只按 **loader + MC 版本** 匹配 `tools/curseforge.json` 的 `lines` ⇒ 上传 starengine（库）jar 时会命中 astral 的 `neoforge_1.21.1` / `forge_1.20.1` 线，把 curios / mixinbooster 作为 requiredDependency 挂到**库文件**上。**库/前置类产物一律带 `--no-relations`**（本轮 4 个库文件已带）。
24. **`run_code` 的 120s 程序级 deadline 也会掐断 `git push`（2026-10-25 实测）**：本轮首次 `push` 因未给 `run_code` 的 `timeoutMs` 而在 120s 被中断（实际已推完，重跑得到 `Everything up-to-date` 才知道）⇒ 推送 / 批量上传这类网络长任务必须同时把 `run_code` 的 `timeoutMs` 设到 ≥540000，`tools.pwsh` 自己的 `timeoutMs` 不够。

25. **`String.Replace` 是「全部替换」——用它做规则追加会写进每一处同锚点（2026-10-25 实测）**：把发布红线追加到 `默认不执行 `git push`**。` 这个锚点时，`docs/agent/AGENTS-full.md` 里该串出现 **2 次**（红线区 L3 + 版本流程第 5 条），而 `docs/agent/AGENTS-dsh.md` 只 1 次 ⇒ 后缀被写进两处，`verify-split.py` 第 2 项（红线区逐字一致）必 FAIL。正确做法：先 `[regex]::Matches($s,[regex]::Escape($anchor)).Count` 断言期望次数，再只按**行号**改目标行（或 `IndexOf` + 精确拼接）。

26. **PowerShell 函数里的 `Write-Output` 会混进返回值（2026-10-25 实测）**：`function Rep { Write-Output '  ok'; return $src.Replace(...) }` 在 `$t = Rep ...` 处拿到的是 **Object[]**（日志行 + 结果行），写回文件后首行变成 `ok: arg-push ok: ...#!/usr/bin/env python3`，`py_compile` 报 `IndentationError: unexpected indent (line 1)`。修法：函数内只用 `Write-Host` 打日志、`return` 只返回目标字符串（必要时 `[string]` 强转）；已写坏的文件用 `git checkout -- <file>` 复原再重跑。（同一轮该 bug 还写坏了 `.github/workflows/build.yml`：首两行变成 `  replaced: release-jars` / `  replaced: ... name: Build` ⇒ YAML 解析失败，push 只换来一个「**没有任何 job**」的空 CI run —— `gh api repos/<r>/actions/runs/<id>` 里 `jobs` 数组为空、run 的 `name` 退化成文件路径 `.github/workflows/build.yml`；修法 = 从 `HEAD^` 取原始文件重打补丁，再用 `require('C:/Users/xmace/.dsh/profiles/desktop/node_modules/js-yaml')` 校验结构。）

27. **测试资产里的模组版本钉子必须写成「家族通配 + 落位名派生」（2026-10-25 用户裁决「放宽」）**：`scripts/test/mt_env.ps1:48` 的 `$script:NeoForgeMods` 原来把 ModernFix 钉成 `*modernfix-neoforge*5.27.26*.jar` 且 `Target` 定死同名 ⇒ ① 整合包一升到 5.27.27 就 `MT_MODS: BLOCKED` / EXIT 11；② 即使放宽通配，`Target` 也会把 5.27.27 的内容写成 `...5.27.26...jar`（骗人 jar）。现行口径：`Pattern` 用「模组家族 + 主.次版本 + `.*`」通配，`Target` 只作派生失败兜底，落位名从源文件名派生（剥整合包 `[中文] ` 前缀），并在复制前按 `Cleanup` 前缀清掉同族旧版本。

28. **`pick_changelog` 对预发布版本解析失败 ⇒ 上传被 CF 400 拒（2026-10-26 实测）**：`tools/curseforge_upload.py:638` 的 `base = ver.split("-")[0]` 会把 `1.5.0-rc.2` 截成 `1.5.0`，于是去 `release/1.5.0/PLAYER_CHANGELOG.md` 找（不存在）⇒ 返回 `None`；`build_jobs` 里 `if job["_changelog"]:` 才写 `metadata["changelog"]`，字段直接缺失，CF 侧回 `HTTP 400 {"errorCode":1002,"errorMessage":"Error in field metadata: Required properties are missing from object: changelog."}`。dry-run 只打印「更新日志 = （无，留空）」且**不读文件**，所以计划态看不出来。**绕法**：显式 `--changelog release/<完整版本>/PLAYER_CHANGELOG.md`（本轮即此）。**未落地的修法（待裁决）**：`base` 改为「先按完整版本目录找，找不到再退回 `re.sub(r"-(?:alpha|beta|pre|rc).*$", "", ver)`」，或 `None` 时直接 `SystemExit` 报错而不是静默留空。

## 7 关键路径（命令）

```powershell
# 状态 / 版本
git -C F:\MCProject\astral_dice_multiloader log -1 --oneline
git -C F:\MCProject\astral_dice_multiloader status --short -uall
git -C F:\MCProject\astral_dice_multiloader rev-list --count origin/multi-main..HEAD
Select-String -Path F:\MCProject\astral_dice_multiloader\*-1.*\gradle.properties -Pattern '^mod_version|^starengine_version'

# 编译 / 产物
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
Set-Location F:\MCProject\astral_dice_multiloader
.\gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes --console=plain
.\gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar --console=plain

# 守门
pwsh -NoProfile -File tools\check_lang_sync.ps1
python tools\audit_patchouli_keys.py
python docs\agent\verify-split.py
pwsh -NoProfile -File scripts\audit\tooltip_color_audit.ps1
pwsh -NoProfile -File scripts\verify\verify_chip_shop_ui.ps1
python tools\verify_misaki_balance.py
python tools\verify_bonnie_active_order.py
python tools\verify_member_recommendation_hand_gate.py
python tools\verify_hud_attack_parity.py

# 测试台（实机须用户下达）
pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.20.1 --phase env
pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.20.1 --phase stop --force --purge-saves
```
