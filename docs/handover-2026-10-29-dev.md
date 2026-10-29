# 交接 — 2026-10-29 dev 线（GeckoLib 接入 + sykestentacle，未提交）

> 本文件是 **dev 线（`multi-dev-next`）** 的交接正本，记于 2026-10-29 20:45（Asia/Hong_Kong）。
> 发布线 `multi-main` 的正本仍是 `docs/handover-1.5.0-main.md`，本文件**不替代**它；两条线源码目前零差（见 §1）。

## 0. 新会话提示词（可整段复制给下一个会话）

```text
你接手 Astral Dice 的 dev 线（多加载器单仓四线：neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / fabric-1.20.1）。
工作树 F:\MCProject\astral_dice_multiloader-next，分支 multi-dev-next，HEAD 9be228f6。

先只读三步，不要改任何文件：
1) 读根 AGENTS.md，按分派读 docs/agent/AGENTS-dsh.md（DSH 规则正本）；
2) 读交接正本 docs/handover-2026-10-29-dev.md（未提交改动在 §3、证据在 §4、待裁决在 §5）；
3) 实读 git -C F:/MCProject/astral_dice_multiloader-next rev-parse HEAD 与 git status --porcelain -uall，核对与交接是否一致；不一致以实物为准并先报告差异。

现状摘要：
- 已提交 9be228f6（mod_version 26.4-SNAPSHOT.3）：mob 侧骰战参与者 DiceCombatParticipants + 特殊骰能力 DiceCombatAbilities，四线；1.21.1 测试台 TLMDICE-ABILITY 26/26、TLMDICE-PARTICIPANT 58/58 PASS。本地领先 origin/multi-dev-next 41 提交，未 push。
- 未提交（本批）：GeckoLib 四线硬前置 + 实体 astral_dice:sykestentacle（animation.tentacleidle 循环 / animation.tentacleattack 受伤触发一次）+ README 前置表 + docs/features/sykestentacle-animation-spec.md。四线合并 compileJava 与打包均 EXITCODE=0。
- 未做：实机验证（红线：默认不启）、geo/anim 镜像未裁决、未 commit / 未 push、AGENTS-full 批次条与收班文档未刷。

待用户裁决（不要自行决定）：① 是否实机验 sykestentacle；② geo/anim 是否从 bbmodel 重导；③ 是否提交本批（按 dev 版本规则提交时四线整批 +1 到 26.4-SNAPSHOT.4，并同步 starengine pin / fabric range / 两线 mods.toml versionRange 上界 / CI 库 ref）；④ 是否 push。

硬约束：dev 任务只在 -next 树落地（主线 F:/MCProject/astral_dice_multiloader 保持封包）；git 命令一律带 -C 绝对路径；models/** 是用户素材禁止 add/改；不自行 commit/push/tag；不启实机除非用户明确下达；Gradle 与测试台只走 pwsh 通道（ops_bash 的 bash 子进程对 ~/.gradle、run/、build/、%TEMP% 写盘被拒）；文本文件一律 CRLF、裸 LF=0、无 BOM。
动手前先加载 using-superpowers；派子代理时 brief 必须写清「你是一位……，不要使用任何人设/角色扮演/语气修饰/昵称自称，按职业身份以中性技术口吻输出」。
```

## 1. 环境与事实源（2026-10-29 20:45 实读）

| 项 | 值 |
|---|---|
| 工作树 | `F:/MCProject/astral_dice_multiloader-next`（dev 线；主线 `F:/MCProject/astral_dice_multiloader` 保持封包） |
| 分支 / HEAD | `multi-dev-next` / `9be228f6 feat(combat): mob 侧骰战参与者与特殊骰能力注册接口（四线）` |
| 未推送 | 领先 `origin/multi-dev-next` **41 提交**（全部本地） |
| 工作树 | **32 M + 28 ??**（本批 GeckoLib 改动 + 用户素材 `models/**`，见 §3） |
| 版本 | 四线 `mod_version=26.4-SNAPSHOT.3+<平台后缀>`；`starengine_version=26.4-SNAPSHOT.1`（fabric 另有 `starengine_version_range=>=26.4-SNAPSHOT.1`） |
| 构建通道 | **pwsh 通道**跑 Gradle；`ops_bash` 的 bash 子进程对 `~/.gradle`、`run/`、`build/`、`%TEMP%` 写盘被拒 |
| 实机红线 | 默认不启动任何客户端/服务端；`scripts/test/mt.ps1` 全流程被禁，仅放行 `build/env/launch/stop` 与单条 `--phase cases --case cases/X.json` |
| 素材红线 | `models/`、`images/`、`sound/` 随时更新、未跟踪，**禁止 add / commit / 删除** |

## 2. 本会话完成的两批

### 2.1 已提交：`9be228f6`（26.4-SNAPSHOT.3，四线）

- mob 侧骰战参与者 `combat/DiceCombatParticipants`（`registerExtraAttacker(String, Predicate<LivingEntity>)` / `isParticipant` / `clearForTests`）；
- 特殊骰能力注册表 `combat/DiceCombatAbilities`（`RollContext` / `DiceRollAbility` / `register(Predicate<ItemStack>, DiceRollAbility)` / 包内 `applyRoll`）；
- 入口闸门从「只有 mob 打 Player」放宽为「mob 打 Player，或该 mob 是已注册参与者」；未注册时逐值一致；
- 1.21.1 测试台两条用例全绿：`TLMDICE-ABILITY-1.21.1` 26/26、`TLMDICE-PARTICIPANT-1.21.1` 58/58；
- 契约文档 `docs/features/tlmdice-participant-support.md`。

### 2.2 未提交（本批）：GeckoLib 四线接入 + `sykestentacle`

**决策**：GeckoLib 放**主模组**、不进 StarEngine 库。理由：v4(`software.bernie.geckolib.*`) 与 v5(`com.geckolib.*`) API 不同，库要喂四线写不出一份共用代码；库目前零第三方前置，加 required 会经 TLMDice 传染给第三方及其玩家；资源本就按线各一份；26.1.2 的库里 jarJar 内嵌带 mixin/AT 的 GeckoLib 不可取。

| 线 | Gradle 坐标 | 元数据区间 | 声明位置 |
|---|---|---|---|
| neoforge-1.21.1 | `maven.modrinth:geckolib:Grwn5rUB`（4.9.3，`implementation`） | `[4.9,5)` | `build.gradle` 184-191 / `neoforge.mods.toml` 115-124 |
| forge-1.20.1 | `maven.modrinth:geckolib:aC5KMoNg`（4.8.4，`modImplementation`） | `[4.8,5)` | `build.gradle` 240-244 / `mods.toml` 162-172（`mandatory=true`，**不可省 versionRange**） |
| fabric-1.20.1 | `maven.modrinth:geckolib:SD2lOScS`（4.8.4，`modImplementation`） | `>=4.8.4` | `build.gradle` 149-154 / `fabric.mod.json` depends |
| neoforge-26.1.2 | `maven.modrinth:geckolib:xfVfPcoC`（5.5.2，`implementation`） | `[5,6)`（模板里手写） | `build.gradle` 225-228 / `neoforge.mods.toml` 138-149 |

**必须写 Modrinth version id**：同一版本号下同时挂 fabric/neoforge/forge 三份变体，写 `4.9.3` 会解析到 fabric 包（实测 664463 B / sha1 b3a6aff6…，只有 fabric.mod.json）。GeckoLib 不内嵌（26.1.2 的 `META-INF/jarjar/` 仍只有 starengine）。

**实体** `astral_dice:sykestentacle`：`LivingEntity implements GeoEntity`，`MISC`，`sized(2.6F,3.0F)`，`clientTrackingRange(10)`，`MAX_HEALTH 20` + `KNOCKBACK_RESISTANCE 1.0`，无刷怪蛋 / 无 AI / 无自然生成，只能 `/summon`。
控制器：`idle`（过渡 5 tick，`thenLoop("animation.tentacleidle")` 常驻）+ `action`（基础 `PlayState.STOP`，`triggerableAnim("attack", thenPlay("animation.tentacleattack"))`），`hurt` 成功返回 true 时 `triggerAnim("action","attack")`，播一次后回落 idle。

**资源**（四线同字节）：`assets/astral_dice/{geo,animations,textures/entity}/sykestentacle.*`，来源 `models/sykestentacle/{models/main.geo.json, animations/default.animation.json, textures/texture.png}`；json 源为纯 LF，拷贝副本转 CRLF，CRLF→LF 归一化后 sha256 与源相同；PNG 逐字节相同。

**本批补的语言键**：`entity.astral_dice.sykestentacle`（zh `触手` / en `Tentacle` / ja `触手`），四线三语键集完全对齐。

## 3. 未提交改动的精确清单

修改（32）：
- 根：`README.md`、`README_ZH.md`（依赖表 + Fabric 前置表各 +2 行）。
- 四线各一组：`build.gradle`；`lang/{zh_cn,en_us,ja_jp}.json`；实体注册类（neo/forge 为 `entity/ModEntityTypes.java`，26.1.2 与 fabric 为新建）；属性类 `event/ModEntityEvents.java`（26.1.2 为新建）；客户端渲染注册（`client/ModClientEvents.java`；fabric 为 `AstralDiceClient.java`；26.1.2 另改 `AstralDiceMod.java`）；元数据模板（三线 `mods.toml`、fabric `fabric.mod.json`）。

新增（28，含素材）：
- 每线：`SykeTentacleEntity.java`、`SykeTentacleModel.java`、`SykeTentacleRenderer.java` + geo/animations/png 三资源。
- `docs/features/sykestentacle-animation-spec.md`（139 行；§6 = GeckoLib 接入）。
- **用户素材，禁 add**：`models/sykestentacle/*`（6 个文件）、`models/astral_anvil.bbmodel`。

**逐线代码锚点（2026-10-29 实读）**：

| 锚点 | neo-1.21.1 | forge-1.20.1 | fabric-1.20.1 | neo-26.1.2 |
|---|---|---|---|---|
| 控制器注册（idle + action） | `SykeTentacleEntity.java` 72-77 | 87-92 | 78-83 | 84-90 |
| `triggerAnim` 触发点 | :96 | :111 | :102 | :110 |
| 实体注册常量 | `ModEntityTypes.java` :63 | :78 | :42 | :34 |
| 渲染器注册 | `ModClientEvents.java` :59 | :79 | `AstralDiceClient.java` :57-59 | `ModClientEvents.java` :114 |
| 属性表 | `ModEntityEvents.java` | `ModEntityEvents.java` | `ModEntityTypes.java` :59 | `ModEntityEvents.java` :30 |

## 4. 验证证据（全部实跑，2026-10-29）

- 四线合并编译：`./gradlew.bat :neoforge-1.21.1:compileJava :forge-1.20.1:compileJava :neoforge-26.1.2:compileJava :fabric-1.20.1:compileJava --rerun-tasks` ⇒ `BUILD SUCCESSFUL in 13s`、`EXITCODE=0`、7 tasks executed。
- 打包：`:neoforge-1.21.1:jar :forge-1.20.1:jar :neoforge-26.1.2:jar :fabric-1.20.1:remapJar` ⇒ `EXITCODE=0`。

| 产物（`build/libs`） | 字节 | sha256 前 16 |
|---|---|---|
| `astral_dice-26.4-SNAPSHOT.3+neoforge_1.21.1.jar` | 2 648 558 | `4633f3f0929e2550` |
| `astral_dice-26.4-SNAPSHOT.3+forge_1.20.1.jar` | 2 697 735 | `cd609dcd5653f54b` |
| `astral_dice-26.4-SNAPSHOT.3+neoforge_26.1.2.jar` | 1 939 383 | `3914fe3d2e9d5888` |
| `astral_dice-26.4-SNAPSHOT.3+fabric_1.20.1.jar` | 2 220 605 | `62aefd676ae7304a` |

- 每个 jar 各含 4 个触手类条目 + 三份资源；三语 jar 内各 1 处 `sykestentacle` 键；四 jar 内 geckolib 块逐一核对为 required（fabric 走 depends）。
- `scripts/verify/verify_resource_integrity.ps1` ⇒ `RESULT: PASS`。
- 语言键集：zh/en/ja 各线相等（1067 / 845 / 837 键）。

## 5. 待用户裁决（未决，不要自行推进）

1. **实机验证 sykestentacle**：`/summon astral_dice:sykestentacle`，看 idle 是否在动、打一下是否播 attack、血量 20、渲染是否正常。红线：需用户明确下达。
2. **geo/anim 可能整体镜像**：`models/main.geo.json` 的 pivot X 与 `rx` 相对 bbmodel 取反，而动画数值未取反；本批按原样接入未纠正。若实机看到镜像/前劈反向，从 bbmodel 重导一对资源即可，代码不用改。
3. **attack 触发语义**：当前挂「自身受伤成功」（触手无攻击行为）；若应改为「它打别人时播」，等有攻击逻辑时改一行。
4. **是否提交本批**：提交前按 dev 版本规则四线整批升 `26.4-SNAPSHOT.4`，同步 starengine pin / fabric range / 两线 mods.toml versionRange 上界 / CI 库 checkout ref。
5. **是否 push**（当前领先远端 41 提交）。
6. 发布面提醒：GeckoLib 现在是玩家必须自备的前置 —— CurseForge 商店页与整合包都要登记，否则 FML 加载期硬拒。

## 6. 坑与可复用结论

1. **Modrinth 坐标必须用 version id**：写版本号会解析到别名下别的加载器变体。
2. **GeckoLib API 两代三分**：4.8.4 动画类在 `core.*`（`core.animation.*`、`core.object.PlayState`）；4.9.3 去掉 `core.`；5.x 换 `com.geckolib.*`，`AnimationController` 构造器**不再收实体实例**、`GeoModel` 返回 `Identifier`、`GeoEntityRenderer` 多一个类型参数。三条 1.20.1/1.21.1 线之间不能照抄 import。
3. **26.1.2 平台差异**：`EntityType.Builder` 只剩 `build(ResourceKey)`（用 `register(String, Function<Identifier,T>)`）；`Entity#hurt` 已 final，须覆写 `hurtServer(ServerLevel, DamageSource, float)`；`LivingEntity` 唯一抽象方法 `getMainArm`；`ResourceLocation` 已改名 `Identifier`。
4. **fabric 无实体基建**：复用 `platform/registry` 垫片（`DeferredRegister` + `RegistryObject`），属性走 `FabricDefaultAttributeRegistry`（须在实体类型提交后调用），渲染器在 `AstralDiceClient.onInitializeClient()`。
5. **forge 必须 `modImplementation`**（`implementation` 会 SRG 崩溃，本仓 build.gradle 有实测注释）；`versionRange` 不可省（FML 缺省取 UNBOUNDED ⇒ 恒判不满足）。
6. **测试台**：`mt.ps1` 全流程被禁；单条用例 `--phase cases --case cases/X.json`；收尾必须 `--phase stop --purge-saves`（失败轮加 `--force`）；KubeJS 探针里 `level` 只能当属性（`player.level`），不能 `.level()`；测「掉血」前先把目标 `MAX_HEALTH` 抬到伤害之上，否则读数被钳。
7. **Blockbench MCP**：端点 `http://127.0.0.1:3000/bb-mcp`，`tools/call` 必须带 `MCP-Protocol-Version: 2025-06-18`；`/health` 可无客户端探测；冷启动约 2 s；载入 bbmodel 用 `Codecs.project.load(data,{path,no_file:true})`。

## 7. 若继续做（建议顺序）

1. 与用户确认 §5 六项；2. 若实机：`--phase build → env → launch`，`/summon` 观察后 `stop --purge-saves`；3. 若重导模型：替换四线三份资源并重跑 §4；4. 提交：版本 bump（26.4-SNAPSHOT.4）+ 四线同批 + `git add` 精确路径（**排除 models/**）→ commit；5. 收班：AGENTS-full 批次条 + verify-split + 刷新 `docs/handover-new-session-context.md`。

## 8. 相关索引

- 规则：根 `AGENTS.md`、`docs/agent/AGENTS-dsh.md`（DSH 正本）、`docs/agent/AGENTS-full.md`、`docs/agent/AGENTS-TOC.md`。
- 功能契约：`docs/features/tlmdice-participant-support.md`、`docs/features/sykestentacle-animation-spec.md`。
- 测试台：`scripts/test/TESTING-SPEC.md`、`scripts/test/mt.ps1`；用例 `scripts/test/cases/TLMDICE-*.json`。
- 主线交接正本：`docs/handover-1.5.0-main.md`（发布线口径，dev 树里是反同步副本）。

