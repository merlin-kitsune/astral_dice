你接手的是 MC 模组仓库 F:\MCProject\astral_dice_multiloader，分支 multi-main，版本 1.5.0-pre.5。
你是 DeepSeek Harness（DSH），在 Windows 原生环境工作（只用 git bash + PowerShell 7），直接在本机工作树动手。

## 第 0 步（必须先做）：修正你自己的规则

把 `AGENTS.md`（红线区）与 `docs/agent/AGENTS-full.md` 里所有 WSL 专用条款标注**失效**或改写为 Windows 口径，一条不漏，口径如下表：

| WSL 条款（原文位置：AGENTS.md 红线区 / 全局 `~/.codex` 规则） | 改写为 |
|---|---|
| `/etc/profile.d` 代理注入（`99-wsl-proxy.sh` 等）、`wsl-env-restore.sh` | Windows 侧代理走系统环境变量（Clash 127.0.0.1:7897），无注入脚本、无需恢复 |
| `/opt/zulu` + `use-jdk` 切换 JDK | JDK 用 `C:\Program Files\Zulu\zulu-21`（21.0.12）；1.20.1 的 toolchain 17 由 Gradle 从 `%USERPROFILE%\.gradle\jdks` 取（已缓存 Adoptium 17.0.20） |
| `rsync` 到 `~/work/adm-*` 副本再编译 | 副本口径作废：直接在工作树 `F:\MCProject\astral_dice_multiloader` 编译，产物与提交同源 |
| WSL git 对 worktree 报 `not a git repository` 的变通（`--git-dir=` / 改走 `cmd.exe`） | 该问题不存在：git 一律用 git bash 的 `git`，`git -C F:\...` 或先 `cd` 进工作树 |
| 「bash 里没有 pwsh / 只能 sh 跑测试」类兼容说明 | 作废：测试台一律 `pwsh -NoProfile -File <绝对路径>`，bash 只承担 git |

同时把 `docs/handover-1.5.0-pre.5-main.md` 的 §1（路径与命令）改成 Windows 口径（`F:\` 路径、`gradlew.bat`、`pwsh`）。
改完必跑 `python docs/agent/verify-split.py`，四项 `[OK]`（快照 / 红线区逐字 / 索引无空洞 / 体量）全绿才算过。

## 第 1 步：先读（按顺序，实读，不凭记忆）

1. `docs/handover-1.5.0-pre.5-main.md` —— §1 路径与命令、§4 实机读数、§5 遗留、§6 坑。
2. `AGENTS.md` 红线区 + `docs/agent/AGENT-SPLIT.md`（Codex / DSH 分工与互斥，见下节）。
3. `docs/agent/AGENTS-full.md` 按主题 `sed -n '<a>,<b>p'` 取用；行号总表 `docs/agent/AGENTS-TOC.md`。
4. `scripts/test/TESTING-SPEC.md`、`docs/compat-1.5.0-porting.md`。

## 基本盘（版本/坐标/交付面/投放/测试台，全部 Windows 路径）

- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-main`，交接基线 `7df4dab2`；开工先 `git status --short -uall`（应干净）与 `git rev-parse --short HEAD`。四子项目同树：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`。
- 版本：四线 `mod_version = 1.5.0-pre.5+<加载器后缀>`；`starengine_version = 1.1.0-pre.2`；jar 内硬前置 `[1.1.0-pre.2,2.0.0)`、`META-INF/jarjar/` 计数 0。
- 交付面：**只 P0** = `neoforge-1.21.1` + `forge-1.20.1`；P1（26.1.2 / fabric）**1.5.0 正式版前不验证、不移植**。
- P0 产物（各自 `build\libs`，**neo 2026-10-23 13:49 / forge 2026-10-23 15:09**；两者均含「雷暴将至」修复 + 立即刷怪波 + 村民职业专用工作方块标签，forge 另含 Jade 图标 mixin 非 static 修复）：
  - neo：`astral_dice-1.5.0-pre.5+neoforge_1.21.1.jar`，2 570 891 B，sha256 `ad65a8d59149d11c21fefd0b37fd7e3b829ca0bf6348fd1cee843cb0d3419161`
  - forge：`astral_dice-1.5.0-pre.5+forge_1.20.1.jar`，2 616 424 B，sha256 `e6081853bda7f14107a4914bbfb2f00878eff56e4ca2357a7cedb08610b536d0`
  - （forge dev 用 `build\devlibs` 的 named jar：2 586 937 B，`49c3b6436a5fea5439f76721e6591217d802f0fad10a4a208da049caffe2a329`）
- 投放（**成对**放 `astral_dice-*.jar` + `starengine-*-1.1.0-pre.2.jar`，缺库即启动失败 `Missing or unsupported mandatory dependencies`）：
  - neo → `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`
  - forge → `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods`
- 测试台：`scripts\test\mt.ps1`，`--version <线> --phase env|run|stop`。
- 未推送：**随本批提交后 1 个**（2026-10-22 pre.4 与 `353cfed4` 已 push；实况以 `git rev-list --count origin/multi-main..HEAD` 为准）；**无** tag；默认**不 push / 不打 tag**（需用户放行）。
- 守门：**8 跑 7 绿**，唯一红 `verify_probe_class_refs`。⚠️ 2026-10-22 已查明：红点原有 6 处、**无一是源码缺口**
  （JEI 走 Curse Maven 未被脚本解析 / LWJGL 由加载器运行期提供 / 一处只出现在 JS 注释里 / 26.1.2 产物陈旧）；
  守门脚本已补 Curse Maven 解析 + `org.lwjgl.` 白名单 + 剥 JS 注释，现只剩 **1 处**（26.1.2 `build/libs` 停在
  `astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`，2026-10-05）。详见交接正本 §5-1。

## 工具集（git bash / pwsh / JDK / gradlew.bat 的具体调用方式与自检命令）

- git（git bash）：`"C:\Program Files\Git\bin\bash.exe" -lc 'cd /f/MCProject/astral_dice_multiloader && git status --short -uall'`；PowerShell 里直接 `git -C F:\MCProject\astral_dice_multiloader status --short -uall`。git bash 内盘符写 `/f/`。
- pwsh：`C:\Program Files\PowerShell\7\pwsh.exe`；一律 `pwsh -NoProfile -File <绝对路径>.ps1`，禁用 Windows PowerShell 5.1（不支持三元 / `??` / `-AsHashtable`）。
- JDK：`JAVA_HOME=C:\Program Files\Zulu\zulu-21`（21.0.12，P0 主线用）；forge-1.20.1 的 `toolchain 17` 由 Gradle 走 `settings.gradle` 的 foojay resolver，已缓存在 `%USERPROFILE%\.gradle\jdks`，不要手改。
- 编译：在 `F:\MCProject\astral_dice_multiloader` 执行 `gradlew.bat :neoforge-1.21.1:classes`（或 `:jar`）；`.sh` 测试入口已删除，不要找也不要再造。
- 自检三连：`where java` / `java -version`（应为 Zulu 21.0.12）/ `gradlew.bat -v`（Gradle 版本 + JVM 一屏确认）。
- 收尾（每轮必做）：`pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version <线> --phase stop --force`；归档再加 `--purge-saves`，判据 `MT_STOP: OK` / `MT_CLEANUP_SAVES: PURGED <n>` / `MT_CLEANUP_RESULT: OK`，并核对 `Get-Process java` 无本流程残留。

## 分工与互斥（Codex vs DSH，见 docs/agent/AGENT-SPLIT.md）

- **DSH（你）= 唯一执行者**：写源码、编译、`git commit`（push 仍需用户放行）、实机 / `mt.ps1`、投放整合包。
- **Codex（Linux/容器侧）= 只读验证**：读源码、写 `docs/**`、跑 `dsh_code_review`；禁写源码、禁 commit、禁实机。
- 锁：动工作树前在 `F:\MCProject\.agent-locks\` 建 `dsh.lock`（PID + 时间 + 任务名），结束删除；同一工作树同时只允许一个 agent 跑 gradle。
- `git status` 出现**不属于自己**的改动 ⇒ 立即停手，在 `docs/agent/CONFLICT.md` 记一行（时间/文件/推测归属），交用户裁决。
- 每轮结束更新 `docs/handover-1.5.0-pre.5-main.md` §4（实机读数）与 §5（遗留）；Codex 只消费不回写。

## 立即待办（2026-10-22 pre.5 收尾后，按优先级）

**2026-10-22 19:00–19:40 已做过一轮 pre.4 全量实机（用户下达）：P0 双线各 10 条用例 = 9 条机器判据 PASS + 1 条截图取证**（`PRE4-CHIPSHOP-FRAME-*` 的 vision 断言在测试台是 DELEGATED，不判红绿），读数见正本 §4.8（新增 §4.8 小节）；下面第 1/7/8/9 条的「待用户放行实机」已全部落地（其中 7 的观感、8 的计时、9 的外框都已按机器行/vision 判定；音效与粒子「好不好看/好不好听」仍属人工观感项）。

1. ✅ **已完成（2026-10-22 实机）** pre.4 实体拆除口径：强化拒止应「只吃玩家近战伤害、可按实际伤害拆掉、归零同一 tick 消失且无生物式躺倒动画」；
   路障拆除同理。判据面：`/astralparty killrepulsion all` / `killbarricade all`（权限 2，落 `AFTERM|type=…|killed=…|…` 机器行）+ 玩家实砍。
   静态判据已全绿（`kill()` / `hurt` / `getHurtSound` 字节码、图标 md5、lang 1045 键），见交接正本 §5-13。
2. ✅ **已完成（2026-10-22）** 1.20.1 创造模式出牌扣卡复验 **PASS**：创造扣 **0** 张、生存对照扣 **1** 张；
   用例 `scripts/test/cases/CARD-NOCOST-CREATIVE-1.20.1.json`。`:423`（自身牌 `use()`）与 `CardChoiceMenu:134` 仍只有静态判据（轻度欠账）。
3. ✅ **已完成（2026-10-22）**：`run/<线>/mods` 与两个 P0 整合包已重投 pre.4 并逐份核 sha256（正本 §2）。
4. ✅ **已完成（2026-10-22）**：`verify_probe_class_refs` 六处红点定性为「守门自身缺陷 / 产物陈旧」，残留 1 处 = 26.1.2 旧 jar（P1 待重建）。
5. ✅ **已完成（2026-10-22）**：立牌冷却补齐 **9 个**「起冷却」子类（两线各 9 文件 × +2 行），双线编译绿；`JasmineSignItem` / `KomachiSignItem` 故意不改。
6. P1 欠账登记维护：JEI 筹码 / `BeamFx` / `GlobalEventGate` / `RepulsionEntity` / `CardShopMenu#planPlacement` / `EventDrawBlock`；
   外加 **P1 两线筹码效果图标仍是带边框版**（pre.4 只换 P0 两线 11 张，正本 §5-14），以及 **P1 两线史莱姆被动仍是 20 tick(1 秒)、文案未改**（正本 §5-16：`neoforge-26.1.2/.../LuluSignItem.java:170`、`fabric-1.20.1/.../LuluSignItem.java:161`）。
   另：**强化拒止整张牌尚未移植到 P1 两线**（`grep -rni repulsion` 在两线 `src` 各 0 命中，正本 §5-19⑥）⇒ 随 P1 批次整体移植，勿按「改一行 rarity」估工。
7. ✅ **触发面已实机（2026-10-22）** 电击手套命中 FX：命中目标冒冷色电火花（`comet_azur_filament`×2 / `glintstone_spark`×7 / `frost_spark`×4）+ 圆心一次 `lightning_bolt_impact`（0.5 / 1.5）；只要「真正吃到伤害」的目标才有电光，音效只播一次。实现 `combat/ElectricShockFx.java`（P0 两线）+ `SoundPlayback` 新 7 参重载，判据见正本 §5-20（静态全绿，无实机）。
8. ✅ **已完成（2026-10-22 实机）** 史莱姆立牌被动 15 秒：P0 两线 `LuluSignItem.HURT_COOLDOWN_TICKS = 300`（原 20 = 1 秒），
   三语文案末尾均加「每 15 秒触发一次」；判据 = 受击两次间隔 <15s 时只有第一次生效（`AP_FX_*_HURT` 或治愈点数不增）。静态判据已绿（javap 常量 300 / lang 1045 键 / 三语 § 数相等），见正本 §5-15。
9. ✅ **外框已实机 / 音效到调用层（2026-10-22）** 筹码商店三色品质外框 + 卡牌购买钱包音效：2026-10-22 用户报障「外框丢了 / 买卡没 wallet 音效」；根因 = 提交 `fe4aa05f`（10-21 换筹码图标素材时连外框绘制一起删了），本批把 `screen/ChipShopScreen` 的 `TEX_QUALITY_1/2/3` + `qualityTex(int)` + `renderOffers` blit 恢复（`qualityCode` 优先 / `qualityFrameIndexOf` 兜底 / `NONE` 跳过），`shop/CardShopMenu#handleBuy` 成功尾段补 `SoundPlayback.playTo(buyer, ModSounds.WALLET_DEPOSIT.get())`。守门 `verify_chip_shop_ui.ps1` 的 G2b 已由反向改写为**正向**（旧反向断言在外框被误删时照样全绿），变异套件 M38/M39 同步反转（`MUT_TOTAL: 39，判红 39`）。判据见正本 §5-21（静态全绿，**无实机**）。

10. ✅ **已完成（2026-10-22）** pre.4 全量实机测试：P0 双线各 10/10 PASS，机器行原文见正本 §4.8；测试资产改动 = 两份 `astral_fixbatch_probe.js`（新增 `/astralfixbatch hurtby`、`fbxNow` 修正）+ 两份 `astral_bugfix_probe.js`（新增 `/astralprobe barplace`、`doLuluPrep` 补装金骰子）+ 6 条用例（`PRE4-*` 8 条新建 / `LULU-SIGN-*` 订正 2 条陈旧断言）。**产品源码零改动**。新坑见正本 §6 坑 23–26（Rhino 调不到 `p.hurt` 且 1.20.1 没有 `/damage`；裸 summon 路障必自毁；陈旧用例把规格变更误报成回归；长效增益用例必须 `/effect clear @s`）。

11. **pre.5 指示器批（2026-10-22 22:3x，已提交；正本 §5-24）**：删电磁炮「就位」指示器（效果 `railgun_ready` 整体删除，生效中指示器 6 → 5 枚）；磨刀石 / 安全气囊**保命触发后**新增同名倒计时指示器（`whetstone_guard_cd` / `airbag_guard_cd`，时长 = 剩余冷却，`PlayerTickEvents#updateChipCooldownIndicators` 每 tick 同步）；四线升 `1.5.0-pre.5`。判据全静态（编译 / 图标守门 66-66-53-53 / `gen_chip_effect_icons --check` 24-0 / lang 1046 键 / 四投 sha256 MATCH / jar 内探针 0），**待实机看倒计时数字与图标观感**。
12. **激光「卡一下」已定性（2026-10-22，正本 §4.10 / §5-25）：不是光束渲染**。真整合包（614 mod + Iris/Complementary + Voxy + Veil）内全帧统计：有光束帧 avg **6.05 ms** vs 无光束 **6.24 ms**，绘制本体 **4–66 µs**，\>20 ms 的帧 14/10677 且**全在光束窗口之外**。所以本批**产品源码零改动**；若再感知卡顿，需要用户给场景（位置 / 朝向 / 光影 / 是否多人 / F3 读数）。
13. **玩家侧状态效果保护批（2026-10-23，提交 `a344fc46`；正本 §5-26 / §5-27）**：用户裁决 = 玩家身上的效果**除非被声明可清除（牛奶 / 蜂蜜瓶）否则不受任何外部影响**，mob 侧**保持原版**（不搬计时表，避免女巫 / 诡术师反复喝药叠加 tick）。新增两线 `event/PlayerEffectProtection`（`apply` 统一入口 + 玩家快照 / `onExternalRemoval` LOWEST 延后补回 / `tick` 按余量重施 / `removeNow` 主动移除唯一入口 / `clear` 死亡·登出·重生），`ModEffectEvents` HIGH+LOWEST+`PlayerLoggedOutEvent`+`PlayerEvent.Clone`，两线各 26 文件 `EffectTimerGuard.apply` 收口（含 7 处原版裸施 + 卡牌默认入口 + 6 处 `removeEffect`）。审计正本 `docs/features/effect-client-server-split-audit.md`。复核 `dsh_code_review` run `c20953f4` 三条 finding 已修（`EnderDiceHandler` 改 `removeNow`、Clone 清场、52 分解订正）。判据：两线 `:classes` + jar BUILD SUCCESSFUL / 4 处投放 + 根 `build/libs` sha256 MATCH / jar 内 `PlayerEffectProtection` 2 类。**无实机读数**：补回时序、牛奶可清除（`marked`）、mob 不受影响三项待用户下达。
14. **Jade 生物护甲改「防御力」批（2026-10-23 用户裁决 A，正本 §5-28）**：审计（m04669）确认骰战计算链准确无双计（玩家 `2+护甲×0.30+0.85×韧性` / 生物 `floor(0+护甲×0.40+1.0×韧性)`，四类基数均 0，只影响攻击力 4/3/0/0），但 Jade 玩家分支显示防御力、生物分支回落原版护甲 ⇒ 两个单位（铁甲僵尸 armor 15 → 实际 6 / Jade 8 图标）。两线各改 1 文件 `mixin/jade/ArmorValueProviderMixin.java`：非玩家分支改 `CombatFormula.mobDefenseInt(TargetCategory.classify(e), e.getArmorValue(), 韧性)`（forge 处理器必须非 static）。判据：两线 `:classes` BUILD SUCCESSFUL、两 jar javap 命中 `classify`@18 + `mobDefenseInt`@33、`META-INF/jarjar/` 0、4 处投放 + 根 `build/libs` sha256 MATCH。**未做**：tooltip 追加「防御力 X」一行（单位已统一、玩家侧从不显示数字，待裁决）。**无实机读数**；P1 两线（fabric / 26.1.2 的同名 mixin）**仍是旧行为**，随 P1 批次移植。
15. **「雷暴将至」修复 + 立即刷怪波（2026-10-23，正本 §5-29）**：×2 钩子补 `MobSpawnType.NATURAL` 白名单 + `isCanceled`/`isSpawnCancelled` 早退 + 复制体 `finalizeSpawn`（neo 4 参 / forge 5 参分叉）+ 7 点落位校验；新增 `spawnStormWave`（施放瞬间在玩家 8–16 格刷 5–7 只僵尸/骷髅/蜘蛛，僵尸与骷髅强制铁头盔（**不可破坏**、掉落率 0），15% 追加女巫，半径 32 格内只刷一波）；两线各改 `event/EventEffects.java`。产物 13:40 重建 + 4 处投放 MATCH；独立复核 `dsh_code_review` run `e7302744…` 三条 finding 全部已修（`WAVE_ORIGINS` 加维度键 / 头盔不可破坏 / 本文件产物块刷新）。**无实机读数**。

16. **村民职业工作方块指向错乱（2026-10-23 用户报障 + 裁决，正本 §5-30）**：JEI 风格两卡片「卡牌交易员」/「筹码商人」显示的工作方块完全相同，因为两职业 `acquirableJobSite` 都传 `VillagerProfession.ALL_ACQUIRABLE_JOBS`（原版共享标签），而 JEP（Just Enough Professions 4.0.5）正是按它枚举展示项。修法 = 两线 `world/ModPoiTypes.java` 新增职业专用 `TagKey<PoiType>`（`CARD_TRADER_JOB_SITES` / `CHIP_TRADER_JOB_SITES`）+ `ModVillagerProfessions.java` 的 `acquirableJobSite` 改用它们 + 新增 4 个 `data/astral_dice/tags/point_of_interest_type/*.json`；**原版共享标签必须保留**（无业村民靠它发现工作站，转职判定仍用 `heldJobSite`）。顺带补 `chip_shop` 进两线 `mineable/axe`（1.21.1 目录 `block` / 1.20.1 `blocks`）。判据：两线 `:classes` BUILD SUCCESSFUL、字节码 lambda 里两个 `TagKey` 各 1 且 `ALL_ACQUIRABLE_JOBS` 0、产物 13:49 重建 + 4 处投放 MATCH。**无实机读数**（JEP 面板是否正确、村民认领是否仍正常待放行）；P1 两线未移植。

17. **实机欠账验证批（2026-10-23，用户下达「补所有欠账实机验证」，正本 §5-31 / §4.11–§4.16）**：P0 双线各 5 条 `DEBT-*` 用例 **10 跑 10 PASS**，五类欠账全部销账 —— ① 玩家侧效果保护（原版效果被 `/effect clear` 后按余量补回 `present=1:dur=427`、`astral_dice:marked` 可清 `present=0`、自定义效果移除被取消 `present=1`、mob 侧不保护 `present=0`）；② pre.5 冷却指示器（`AP_IC8_INDREAD` 的 `wPresent=0` / `aPresent=1:aDur=1138`）；③ 「雷暴将至」（波怪 `want=5:placed=5` / `want=6:placed=6`、8–16 格、`helmet>0`、白天 `onfire=0`；×2 只认 `MobSpawnType.NATURAL` —— 刷怪笼/召唤 15–19 只怪时 `storm_night_spawn_x2` 0 行，而自然刷怪的 skeleton/creeper 各被复制 2 次）；④ Jade 生物防御力（图标已换：neo 1 半 / forge 8 满 + 1 半；`AP_G1_DEF:armor=4:tough=0:cat=HOSTILE:def=1`）；⑤ 村民工作方块（`AP_D1_POI` 两线逐字相同：专用标签各 1 项 / 共享标签 15 项 / 职业谓词对角矩阵）。**过程中修掉 1 个真缺陷**：forge `mixin/jade/ArmorElementIconMixin` 的 `@Redirect` 处理器写成 `static`，与 Mixin 0.8.5 的 `checkTargetModifiers(target, true)` 冲突 ⇒ `InvalidInjectionException`，因 `required: false` **静默失效**（Jade 护甲图标一直是原版）；改为非 static 后重建 forge 产物（15:09）。测试资产：两线探针新增 10 条 `debt*` 子命令（forge 另有 `debtdef`）+ 10 条 `DEBT-*` 用例；**产品源码只改 1 行**。限制：村民转职因 `astral_test_noai.js` 强制 NoAI 在测试台内不可观测；×2 正面证据目前只有 forge 一次样本。

18. **村民带 AI 轮 + Jade 护甲图标尺寸口径（2026-10-23，正本 §4.17 / §4.18 / §5-34 / §6-38）**：
  (a) 为验证「村民认领工作站 / 转职」补一轮**带 AI** 实机 —— `scripts/test/resources/kubejs/<线>/server_scripts/astral_test_noai.js` 原有硬闸门恒强制 `setNoAi(true)`，本轮加**显式豁免**：实体带标签 `astral_allow_ai` 即跳过（`var NOAI_ALLOW_TAG = "astral_allow_ai";` + `noaiKeepAi(e)`，两线各 7,785 → 8,013 字符，CRLF 保留；不带标签的实体语义不变）。
  (b) 新用例 `scripts/test/cases/DEBT-POI-AI-{1.21.1,1.20.1}.json`（24 步 / 4 断言，两线同构）：`/reload` → wait 12000 → `/setblock ~8 ~ ~8 astral_dice:card_shop` → `/execute if block ~8 ~ ~8 astral_dice:card_shop run say AP_VSHOP:ok=1` → `/summon villager ~8 ~ ~10 {NoAI:0b,Tags:["astral_allow_ai"],PersistenceRequired:1b}` → `debtvill V0/V1/V2` → screenshot。**双线 PASS**，三读全为 `AP_V*_VILL:ok=1:n=1:prof=astral_dice:card_trader:lv=1:noai=0` ⇒ 带 AI 的村民认领 `astral_dice:card_shop` 并转职 `astral_dice:card_trader`。
  (c) **Jade 护甲图标「正确大小」已取证**（用户自己改代码，本批只给数字）：neo Jade 15.10.6 的 `ArmorElement#render` 用 `blitSprite(sprite, x+xOffset, y+yOffset, 9, 9)`、横向步距 `8`；forge Jade 11.13.3 用 `renderIcon(gui, x, y, 8, 8, IconUI.ARMOR|HALF_ARMOR|EMPTY_ARMOR)`、步距 `8`、换行 `y += 10`；两线原版素材格本身都是 **9×9 图集格 / 有效 8×8**。⇒ 标准口径 = **画布 9×9、有效内容 8×8、步距 8 px**。
  (d) **我方溢出根因**：两线 `assets/astral_dice/textures/gui/defense_{full,half,empty}.png` 都是 9×9 **满画布无透明 padding**（第 0 / 8 列、第 0 / 8 行均为不透明描边）⇒ 8 px 步距下相邻图标互相压 1 px 描边、整行看起来变宽粘连；forge 侧 `ArmorElementIconMixin.java:105` 的 `blit(ours, x, y, 0, 0, sx, sy, 9, 9)` 入参 `sx=sy=8` 还会额外裁掉右/下 1 px。**修法**：把图标本体画在 9×9 画布的左上 8×8，第 9 列与第 9 行全透明（alpha 0）。
  (e) 环境事实：`/setblock` 会静默失败（旧位置被占）⇒ 必须 `execute if block … run say` 自证；用例 JSON 缺 `case_id`/`title` 直接 `MT_CASE: ERROR`，**steps 一条都不执行**。
19. **Java 源代码许可 MIT → GPL-3.0-only（2026-10-23 用户裁决，正本 §5-35 / §6-39）**：用户原文「将Java源代码许可协议从MIT改为GPL-3.0，1.3.7及以前版本仍保持MIT协议」。落地 = ① `LICENSE` 换成 GPL-3.0 全文（取自 gnu.org，35,149 字符，CRLF 归一后 39,383 字符；本批追加 StarEngine 指针后 39,913 字符 / 755 行）+ 末尾〈Project licensing notes〉四节（源码 **GPL-3.0-only** / ≤1.3.7 仍 MIT / 资产 CC BY-NC-SA 4.0 / 第三方 ier 粒子库 MIT）；② **不保留 `LICENSE-MIT`**（2026-10-23 同日二次裁决直接删除，该文件只存在于 `7e4e4f17`），≤1.3.7 的 MIT 授权仍在 `LICENSE` §2，原文指向对应发布 tag 的 `LICENSE`；③ `README.md` / `README_ZH.md` 的 `## License` / `## 许可` 改写为六条（GPL-3.0-only 源码 / 1.3.7 及以前 MIT / 资产 CC BY-NC-SA 4.0 / 数据与 lang 随代码 / 商用需授权 / 第三方例外）；④ 四线 `gradle.properties` 的 `mod_license=` 与 `fabric-1.20.1/src/main/resources/fabric.mod.json:11` 的 `"license"` 改为 `GPL-3.0-only (code) / CC BY-NC-SA 4.0 (assets)`；⑤ 两份 CHANGELOG 各加〈许可与授权 / Licensing〉段。**已发出的 jar 元数据仍是 MIT（未重建）**，是否重建 P0 两线 + 重投 4 处待用户裁决；GPL 与 MC 闭源映射 / EULA 的争议已如实提示用户。
20. **许可二次裁决（2026-10-23 同日，原文：`StarEngine库也需要重新声明为GPL-3.0`、`LICENSE-MIT不保留，直接删除`）**：① 本仓 `LICENSE-MIT` **已删除**（只存在于 `7e4e4f17`），引用清理 9 处（`LICENSE` §2 + `README.md`/`README_ZH.md` 各 1 + 两份 CHANGELOG 各 1 + 本正本 §0-23/§5-35 与本文第 19 条）；`LICENSE` §1 新增一段说明前置库 StarEngine 是独立项目、同为 GPL-3.0-only、不由本文件重新授权。② **前置库仓 `F:\MCProject\starengine`（`main`）提交 `53db33b`**：`LICENSE` = GPL-3.0 全文 +〈Project licensing notes〉三节，四平台 `mod_license` 与 `fabric.mod.json` 改 `GPL-3.0-only`，两 README §License 与两份 CHANGELOG 同步；边界 = `1.0.0`–`1.0.12` 与已发布 `1.1.0-pre.x` 仍 MIT。③ **两仓都未重建产物、未 push**：本仓 pre.5 jar 与前置库 1.1.0-pre.2 jar 的 `mods.toml` 仍写 `MIT License`，对外发布前必须重建并成对重投。④ `F:\MCProject\starengine_next` 是坏掉的 worktree（`.git` 指向 `/mnt/f/...` 的 WSL 路径），本批未动。

## 硬约束（Windows 版）

- 禁止项（无例外）：`wsl.exe`；`/mnt/...` 路径（含 `/mnt/f/...`）；`~/work/adm-*` 编译副本；`~/.codex` 里的 WSL 注入脚本（代理 / `GH_TOKEN` / JDK）—— 上述一律视为已失效，出现在旧文档里只当历史背景。
- 源码只在 `F:\MCProject\astral_dice_multiloader` 工作树内改；动 `AGENTS.md` 或 `docs/agent/AGENTS-full.md` 后必须跑 `python docs/agent/verify-split.py`，四项 `[OK]` 才算过。
- dev 的 AutoJS：forge 1.20.1 dev 必须用 `build\devlibs` 的 **named** jar（`AutoJS-forge-1.20.1-1.0.0-dev.jar`），整合包用 reobf jar；**两者不可同目录**。
- `--purge-saves` 会删 `testworld`：下轮实机前先 `--phase env`。
- P1 不碰：26.1.2 / fabric 的实现、验证、移植一律等用户下达。
- **`models/` / `images/` / `sound/` 是素材目录（2026-10-22 用户裁决）**：随时更新、有新文件属正常，`git status` 里出现这三处的未跟踪 / 变动项**不算冲突、也不算工作树脏**，但**不得删除 / 移动 / 重命名 / 擅自纳入提交**（需要入库先问用户）；`models/astral_anvil.bbmodel` 是用户下一步更新的素材，**只读**。提交一律显式路径，禁 `git add -A` / `git add .`。
- 不 push、不打 tag、不擅自装工具（`apt`/`npm`/`pip`/新 JDK 等一律先征得用户许可）。
- 独立复核用 DSH 插件 `@michengai/dsh-code-review`（工具 `dsh_code_review({ input })`：`input` 空 ⇒ 原生范围选择，非空 ⇒ 整段原样作为审查要求，范围写自然语言；报告以 GFM 回本会话）。**派活只用 DSH 自带子智能体**：一次性活 ⇒ `subagent`（全新 context，brief 完全自包含）；接续上下文 ⇒ `subagent_fork`；多人并行 ⇒ `spawn_teammate` + `team_task_*`；结果由子代理 `send_message` 回报，**禁轮询**。
- **派子代理一律屏蔽人格注入（2026-10-22 用户裁决）**：brief 除职业声明外必须原文带「不要使用任何人设 / 角色扮演 / 语气修饰 / 昵称自称，按你的职业身份以中性技术口吻输出」；审查 / 取证类任务用全新上下文的 `subagent`（`subagent_fork` 会继承带人设的对话；复核不在此列，复核只用 `dsh_code_review`）；子代理回报出现自称 / 卖萌 / 情绪表演即不合规，要求重发中性版。人设只作用于对用户的表达层。**禁止**用 Codex 的 `delegate_task` / `fork_turns="none"`（属另一宿主，DSH 不再引用）；主 Agent 只做识别需求 → 分配工作 → 验收成果。
