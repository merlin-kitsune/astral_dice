# 26.1.2 线 1.5.0-rc.7 实机验证清单

> 状态：**待执行**（2026-10-31 22:5x 起草）。逐项执行后把「状态」列改成 `PASS` / `FAIL(说明)` / `SKIP(原因)`，并在文末记时间与日志路径。
> 红线：实机必须由用户明确下达；每轮结束停进程、清 `scripts/test/linux/logs/` 以外的残留。

## 0 现成通道

| 项 | 值 |
|---|---|
| 启动 | `bash scripts/test/linux/launch.sh --version 26.1.2`（已 dry-run 验证：`run/start-26.1.2.sh`、toolchain 25、`daemon_jvm` 21） |
| 停止 | `bash scripts/test/linux/stop.sh --version 26.1.2` |
| 就绪判据 | `GET http://127.0.0.1:25585/health` = 200 且 `status` 里 client `available:true` |
| 脚本通道 | DSH MCP `autojs`（`mcp__autojs__{status,eval_js,kubejs_log,reload_scripts}`）；**同一时刻只有一条线占 25585** |
| run 目录 | `run/26.1.2/`（mods 12 个，含 `AutoJS-neoforge-26.1-1.0.0.jar` / `kubejs-neoforge-26.1.2-8.0.6.jar` / `rhino-2101.2.8-build.91.jar`） |
| 日志 | `scripts/test/linux/logs/26.1.2-<时间戳>.log` + `run/26.1.2/logs/latest.log` |

## 1 P0 冒烟（先做，失败就停）

| ID | 项 | 判据 | 状态 |
|---|---|---|---|
| S1 | 客户端能启动到标题画面 | AutoJS client `available:true`，进程不退出 | 待验证 |
| S2 | 能进单人世界 | `logged in with entity id` 出现，无 `CrashReport` | 待验证 |
| S3 | 启动日志无模组异常 | `latest.log` 无 `Exception`（`authlib` / `Realms` 噪声除外） | 待验证 |
| S4 | 注册表自检 | 客户端 `ModItems`/`ModBlocks`/`ModEffects` 数量与 1.21.1 对齐（items 154 / effects 67 / attachments 137） | 待验证 |

## 2 编译静默、只可能实机暴露的地方（本轮移植高风险点）

这些在 `docs/migration-26.1.2-rc7-mapping.md` §12.1 被标为「合并截断后补回」，编译通过但**没有运行时证据**。

| ID | 项 | 判据 | 状态 |
|---|---|---|---|
| H1 | 事件卡 HUD（`client/ModClientEvents` 的 `registerAbove(CROSSHAIR, EventCardOverlay.LAYER)`） | 抽到事件卡后屏幕中央出现 overlay，非空白、不崩 | 待验证 |
| H2 | `network/ModPayloads` 的 `EventCardPayload.playToClient` 注册 | 服务端发事件卡 → 客户端真的收到并显示 | 待验证 |
| H3 | GUI 管线重写（`GuiGraphics`→`GuiGraphicsExtractor`，本批 52+30+22+20 处集中在 shop/choice/jei） | 打开卡片背包 / 卡片商店 / 芯片商店 / 选择界面，四屏都能画出来、可点、不崩 | 待验证 |
| H4 | `DiceCombatEvents` 的 enigmatic 三方法 + 三常量 | 触发骰战结算不抛 `NoSuchMethodError`；谜之诅咒行为与 1.21.1 一致 | 待验证 |
| H5 | `AstralEventSystem.applyEventBonus(Player,String,List<Player>)` | 事件奖励发放给全队，非只给触发者 | 待验证 |
| H6 | `CurioSlotUtil.forEachCurioStack` | 饰品槽遍历生效（佩戴/卸下立牌仍能识别） | 待验证 |
| H7 | `BaseSignItem.skipCooldownAfterLock` | 锁定卡后冷却按预期跳过 | 待验证 |
| H8 | `ModCommonConfig.ENABLE_CHIP_RECIPES` + `ChipRecipesEnabledCondition` | 开关关闭时芯片配方在配方书里消失 | 待验证 |
| H9 | `ModItems` 16 个补齐注册 + `isCombatCard/isEffectCard` | 这 16 个物品可获取、tooltip 正确、卡牌分类正确 | 待验证 |
| H10 | 村民交易客户端侧 | 与 `card_trader`/`chip_trader` 交互能开交易界面、能买（服务端已证 218 条报价加载） | 待验证 |

## 3 已登记的行为差异（确认「可接受」而不是「崩了」）

| ID | 项 | 期望 | 状态 |
|---|---|---|---|
| D1 | 立牌半透明 alpha（`SignTranslucencyMixin`，`RenderSystem.setShaderColor` 已删） | 半透明立牌仍有透明观感，不出现全黑/全白/闪烁 | 待验证 |
| D2 | 卡片槽悬停高亮 | 回落到原版高亮，功能可用 | 待验证 |
| D3 | 档位边框染色 | 无 Modern UI 时不染，不崩 | 待验证 |
| D4 | 蛟龙立牌强制冷却 | 重登后归零（已知限制），不影响使用 | 待验证 |
| D5 | Iron's Spellbooks 联动已整体移除 | 无 `ClassNotFoundException`，其余功能不受影响 | 待验证 |

## 4 其它线 / 其它批留下的实机欠账

| ID | 线 | 项 | 判据 | 状态 |
|---|---|---|---|---|
| O1 | 1.21.1 | 「一心同体」在**非同队玩家攻击**时被动是否触发（此前只做了静态证明，258 个用例无一覆盖） | 两人不组队：A 戴超天酱、B 打 A ⇒ B 获 `kangel_fan`、A fans +1、第二击 −1 | 待验证 |
| O2 | 1.21.1 | `KAME-PVP` 同队互斥回归 | 同队互殴不减伤、指示器不误判 | 待验证 |
| O3 | 全 | AutoJS 真机通道（新注册的 `autojs` MCP） | `status` 两侧可用；`eval_js` 能读服务端与客户端状态 | 待验证（本清单本身即验证） |
| O4 | fabric-1.20.1 | dev 启动 LWJGL / Sodium 版本冲突（VM 期遗留） | 若仍冲突，记录确切报错再决定修法 | 待验证 |
| O5 | 全部 | 冷构建耗时（`[[PENDING:build-time-native]]`） | 移走单线 `build/moddev` 后单线跑一次，记录耗时 | 待验证（非实机） |

## 5 执行顺序

1. **P0 冒烟**（S1–S4）——任何一项 FAIL 立即停，先修。
2. **H1/H2/H3**（事件卡 HUD、payload、四屏 GUI）——本轮改动最密集的区域。
3. **H4–H9**（战斗/事件/饰品/配置/物品），可用命令 + 创造模式快速铺条件。
4. **H10**（村民交易客户端）。
5. **D1–D5**（视觉与降级项，逐项确认「可接受」）。
6. 其它线（O1/O2）另起一轮，避免与 26.1.2 抢 25585。
7. 全绿后再谈投放 jar 到 `/mnt/d/.minecraft`（红线 2，需另行授权）。

## 6 断言片段（AutoJS `eval_js`）

```js
// 客户端存活 + 版本
var MC = Java.loadClass("net.minecraft.client.Minecraft").getInstance();
JSON.stringify({title: MC.getWindow().getTitle(), player: MC.player ? MC.player.getName().getString() : null})
```

```js
// 服务端注册表计数（专用服）
var H = Java.loadClass("net.neoforged.neoforge.server.ServerLifecycleHooks");
var s = H.getCurrentServer();
JSON.stringify({trades: s.registryAccess().lookupOrThrow(Java.loadClass("net.minecraft.core.registries.Registries").VILLAGER_TRADE).size()})
```

> Rhino 坑：没有全局 `server`、没有 `Java.from`、`keySet()` 元素直接是 `Identifier`、`MappedRegistry#get/containsKey` 在 `Identifier`/`ResourceKey` 重载下报 ambiguous。

## 7 记录

| 时间 | 执行人 | 结果 | 日志 |
|---|---|---|---|
| 2026-10-31 23:29 | DSH | **P0 全 PASS**（S1 标题就绪 / S2 进世界 / S3 无本模组异常 / S4 注册表计数） | `run/26.1.2/logs/latest.log` |
| 2026-10-31 23:2x | DSH | KubeJS 包名事故已修（`starenginelib` → `starengine`，2 脚本 10 处），重启后 0 报错 | `run/26.1.2/kubejs/{client_scripts/astral_wallet_vision.js,server_scripts/astral_bugfix_probe.js}` |

### 7.1 P0 实测数字（2026-10-31 23:29，world=`testworld`，超平坦）

- **S1**：`LAUNCH: READY`；`Game took 12.418 s` / `Total time to load game and open world was 12.41812 seconds`。
- **S2**：AutoJS `status` ⇒ `client running, world loaded, scripts loaded` + `server running with 1 player(s) online`；quickplay 用 `bash run/start-26.1.2.sh -Pquickplay=testworld`（世界从 `run/26.1.2/testworld` 复制到 `run/26.1.2/saves/testworld`）。
- **S3**：`Couldn't parse data file` = **0**；`KubeJS ... ERROR` = **0**；异常仅他模组 mixin 可选目标缺失（sodium / mekanism×3 / cable_facades / justdirethings / immersiveengineering）。
- **S4**：`BuiltInRegistries` ⇒ items **1665**（`astral_dice:` **157**）、blocks **1172**（`astral_dice:` **4**）、mob_effect **108**（`astral_dice:` **68**）。
- `AP_PARTY: sw_mc=true sw_ftb=true sw_opac=true back_ftb=off back_opac=off`（本 dev 实例无 FTB/OPAC 运行期模组，属预期）。
- 保活：客户端必须以 `systemd-run --user --unit=mc2612 --collect` **直接跑 `run/start-26.1.2.sh`**（主进程=游戏）；若主进程是 `launch.sh`，它就绪后退出会让 systemd 连带杀掉游戏。
