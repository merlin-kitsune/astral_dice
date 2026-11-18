# release150 · fabric-1.20.1 实机验收（最终）

生成：2026-11-02 06:1x，执行者：lead（本会话）
结论：**PASS** —— 27 条断言（24 机器 + 1 crash + 2 vision）；24 机器断言全 PASS，crash 断言 INCONCLUSIVE（目录不存在），2 条 vision 由 lead 逐张看图判定通过。

## 1. 受测物
| 项 | 值 |
|---|---|
| jar | `fabric-1.20.1/build/libs/astral_dice-1.5.0+fabric_1.20.1.jar` |
| 大小 / sha256 | 3,035,274 B / `5e0dd9869700ae3cacd34bb20818a6d3a3152bd0acdf74281a8afd64ed639bc2` |
| 构建时间 | 2026-11-02 05:30 |
| 加载模组数 | 87（`Loading 87 mods`） |
| 版本门证据 | `fabric-1.20.1/run/client/logs/latest.log:143` `[05:50:02] [Render thread/INFO] (astral_dice-version-gate) [Astral Dice] 版本互通门槛(Version Gate):mod_version=1.5.0+fabric_1.20.1 → 网络互通号=1.5` |

注：`fabric-1.20.1/build/libs` 的 5e0dd986 与**仓根 build/libs 的 662e8280（3,031,074 B）不同**——旧根件缺 i18n tooltip 修复与 trinkets mixin 等 5 处内容（详见 §6），发布前必须以 5e0dd986 覆盖根件。

## 2. 运行方式
- 客户端：`AUTOJS_PORT=25585 cmd //c gradlew.bat :fabric-1.20.1:runClient -PautojsTestMod=1 -PtestSodium=false -Pquickplay=testworld -PdevUsername=AstralTester --no-configuration-cache --console=plain`
- 起跑前必须 `rm -f fabric-1.20.1/build/loom-cache/argFiles/runClient`（否则 Loom 复用陈旧 argfile，AutoJS 不进 classpath）
- 用例：`pwsh -NoProfile -File scripts/test/mt_case.ps1 run --version fabric-1.20.1 --case scripts/test/cases/RELEASE150-fabric-1.20.1.json`
- 日志：`fabric-1.20.1/.tmp-case-hoverfix3.log`

## 3. 结果
- `MT_CASE_RESULT: RELEASE150-fabric-1.20.1 = PASS`
- `MT_CASES_SUMMARY: RELEASE150-fabric-1.20.1=PASS`
- `MT_CHANNEL: delegated_count=2`（两条 vision）
- 逐条 PASS 共 52 行（32 steps + 27 asserts 中可机器判定者）
- 探针原文（`[06:07:5x] [Server thread/INFO] (KubeJS Server) autojs-eval#5x`）：
  `[REL150] ver=fabric-1.20.1 gamemode=creative pos=100,105,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+fabric_1.20.1`
- 筹码探针：`[REL150] chip id=astral_dice:abyss_reagent_chip count=3 name=深渊试剂 tipn=7 path=inventory slot=0`
- hover 探针：`[REL150] inventory open=1 hover=159,187 slot=45 lx=142 ty=67 scale=4 mh=636,748`
- crash：`INCONCLUSIVE — crash-reports 目录不存在`（fabric 客户端从未崩溃，目录不建；非缺陷）

## 4. 三张截图（lead 逐张 read_image 核对，副本在 `work/release150-fabric-1.20.1-shots/`）
| 文件 | 字节 | sha256 | 画面内容 |
|---|---|---|---|
| `abyss_reagent_tooltip.png` | 595,030 | `0b1116ea…2d643f` | 创造物品栏 + 紫框 tooltip「深渊试剂 / 星之殷戏 / Astral Dice / 槽位: 筹码 / 装备时治愈层数 +2。触发治愈回血时: 获得实际治疗量 × 2 的伤害吸收 / 当前治愈: 0 / 32」 |
| `chip_shop_ui.png` | 460,397 | `c091e394…25d0e0` | 筹码商店：电击手套 / 友情徽章 / 度轮滑一 / 3 |
| `release150_guide.png` | 326,558 | `66f64238…3e44ea` | 帕秋莉「恋的规则书 / 类别」首页 |

⇒ 「深渊试剂」改名在 fabric 线**画面可见**；tooltip 内容与 1.20.1 forge 线一致（措辞「槽位: 筹码」同平台差异）。

## 5. hover 取证的三次迭代（工具链知识，供以后复用）
1. 尝试 1：`scr.getGuiLeft()` → `TypeError: Cannot find function getGuiLeft in object …CreativeModeInventoryScreen`。事实：创造模式下 `new InventoryScreen(player)` 落地为 **CreativeModeInventoryScreen**，该类只有 `puzzleslib$getGuiLeft/getGuiTop`。
2. 尝试 2：反射取 `leftPos/topPos`（自 screen 类下钻 2 层到 AbstractContainerScreen）+ `mouseMoved(x,y)` → 坐标算对（159,187 / slot 45 / lx 142 / ty 67），但截图仍无 tooltip。
3. 尝试 3（最终，已落盘）：**1.20.1 的 hover 判定不读 Screen.mouseX，而读 `MouseHandler.xpos()/ypos()`（单位=屏幕像素）** ⇒ 必须写 `mouseHandler` 的 `xpos/ypos = GUI 坐标 × guiScale`（本例 ×4 → 636/748），随后 `mouseMoved` 兜底。改后 tooltip 立即出现。
- 用例改动：`work/_patch_fabric_hover3.py`；备份 `RELEASE150-fabric-1.20.1.json.bak-fabrichover3-20261102-060747`（另有 `-060349` / `-060528`）；结构仍 32 steps / 27 asserts；`MT_VALIDATE: OK`；**未删/未放宽任何断言**。

## 6. 本轮修掉的工具链缺陷（判据侧，未放宽）
1. `scripts/test/lib/Mt.Proc.psm1:894 Test-MtClientProcess` 不认 fabric 主类 ⇒ 补「路径③」（`-Dfabric.dli.env=client` + `devlaunchinjector.Main`/`KnotClient`），备份 `.bak-clientdetect-*`。
2. `scripts/test/mt_assert.ps1:405 Test-MtPlatformLine` 只认 neo/forge 形状 ⇒ 增 fabric 圆括号来源与 FabricLoader 清单续行两条判据（只增不减），备份 `.bak-fabricplatform-20261102-055800`。
3. `scripts/test/win/launch.sh` 不透传 `-PtestSodium=false` ⇒ 直跑 gradle 绕过（Sodium 3.3.2-snapshot vs 3.3.1），**该缺陷仍未修**。
4. AutoJS 注入失效真因 = 陈旧 `build/loom-cache/argFiles/runClient` ⇒ 删文件 + `--no-configuration-cache`。

## 7. 归因与残留
- 本模组 6 条 WARN 全良性：refmap dev 常态 / version-gate 设计内降级 / 原版 Missing data pack 提示。
- 3 条 ERROR 全非本模组：enigmaticlegacy 配方 ×2、`SyncEntireContainer` 属 io.wispforest.accessories。
- 残留风险：① crash 断言本线恒 INCONCLUSIVE（未崩过即无目录）；② 用例 JSON 因 `json.dumps(indent=2)` 整份重排，diff 面较大（内容等价）；③ hover 依赖 guiScale 换算，换分辨率时自动跟随（scale 由 window 实时取）。
- 环境备注：本轮期间仓库存在**另一并发工作流**反复起 `gradlew :fabric-1.20.1:runServer/runClient`（共用 build/ 与 run/client/，是 argfile 陈旧与端口争用的来源）；本轮只 kill 自己派生的 PID，未动对方进程。
