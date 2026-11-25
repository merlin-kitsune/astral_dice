# 01 · 仓库状态取证（1.5.0 正式版发布前置）

- 采集时间：2026-11-02T01:37:43+08:00 之后（Asia/Hong_Kong，本机 2026-11-01 23:5x 时段的工作树）
- 工作树：`D:\MCProject\astral_dice_multiloader`（分支 `multi-main`）
- 取证方式：只读命令（git / ls / find / grep / du）。**未跑 gradlew、未启动游戏、未修改仓库内任何文件**（唯一写入是本报告）。
- 口径：每条结论后附「文件:行号」或命令原文片段；取不到证据处明确写「无法判定」。

---

## 1. git 现状

### 1.1 命令原文

```
$ git -C D:\MCProject\astral_dice_multiloader log -1 --oneline
fd990c15 fix(26.1.2): 补齐收尾验证发现的混入缺口，并把立牌半透明改成 26.1.2 两段式实现

$ git -C ... rev-parse --short HEAD
fd990c15

$ git -C ... branch --show-current
multi-main

$ git -C ... rev-parse --abbrev-ref --symbolic-full-name @{u}
origin/multi-main

$ git -C ... rev-list --left-right --count origin/multi-main...HEAD
0	24

$ git -C ... describe --tags --always
1.5.0-rc.6-34-gfd990c15
```

### 1.2 结论

- **HEAD = `fd990c15`**，分支 `multi-main`，上游 `origin/multi-main`（远端 ref = `7c44cfbf`）。
- **与 `origin/multi-main` 的关系：领先 24 个提交，落后 0 个**（`0	24`）。即这 24 个提交（26.1.2 移植收尾 + 村民模型 + 立牌半透明）**尚未推送**。
- **本地没有 `1.5.0-rc.7` tag**：`git tag -l '1.5.0*'` 只返回 `1.5.0-rc.1` … `1.5.0-rc.6`。但远端有：

```
$ git ls-remote --tags origin 'refs/tags/1.5.0*'
ab8215d77851bde9ed1eab49d599ec946fa123b0	refs/tags/1.5.0-rc.7
58c51fd82b8ced3b69ee49ea1a9febb297d2d9cb	refs/tags/1.5.0-rc.7^{}
```

  ⇒ 远端 `1.5.0-rc.7` 是 annotated tag（tag 对象 `ab8215d7`），指向 commit `58c51fd8` = `fix(combat): 四线补属性缺席守卫，修复骰战生物近战崩溃（N27）`。该 tag **本地未 fetch**，本地 describe 才落在 `rc.6` 后 34 个提交处。**发 1.5.0 前必须先 `git fetch --tags` 对齐基线**。
- **stash 有 4 条历史残留**（都会在 `git stash list` 里干扰判断，发布前建议确认后再清）：

```
stash@{0} On multi-dev-next: devnext dirty build.gradle (VM-era mcHomePath, 2026-10-31) - superseded by rc.7 commit c31d36f6
stash@{1} On multi-main: dsh: rollback /review rules (user 2026-10-28)
stash@{2} On multi-dev-next: pre-migration local edits (build.yml, tools/modrinth.json) 2026-10-26
stash@{3} On 1.20.1-fabric: wip: 自研五项修复（改用 dev-next 已修方案，保留备查）
```

- **没有 staged 内容**（`git diff --cached --stat` 为空）；工作树未提交 diff = `276 files changed, 6528 insertions(+), 3203 deletions(-)`（不含未跟踪文件）。

### 1.3 领先的 24 个提交（`origin/multi-main..HEAD`，新→旧）

```
fd990c15 fix(26.1.2): 补齐收尾验证发现的混入缺口，并把立牌半透明改成 26.1.2 两段式实现
ed7c56d4 feat(villager): 采用改良版卡牌商人模型（cardshop_villager_1）并撤掉无帽特例
fb6caa82 Revert "fix(26.1.2): 卡牌/筹码商人村民改用无帽模型（关掉原版 hat + hat_rim）"
5bf389b8 Revert "fix(render): 无帽村民只作用于卡牌商人，并把同一修复移植到 1.21.1 / 1.20.1"
6680e3cf fix(26.1.2): 筹码商人绑定回 chip_trader 交易集，不再领卡牌
eeb6e5e7 fix(render): 无帽村民只作用于卡牌商人，并把同一修复移植到 1.21.1 / 1.20.1
9192a0bc fix(26.1.2): 补齐移植时被合并吞掉的 8 组实现，恢复 1.5.0 行为对齐
c3757626 fix(26.1.2): 补齐 Curios 标签、立牌 tooltip、方块物品名与筹码商店文字
31103af8 fix(26.1.2): 补回被合并截断的三处界面/文本，修手册皮肤与村民名
ae812915 fix(26.1.2): 卡牌/筹码商人村民改用无帽模型（关掉原版 hat + hat_rim）
9180409e Revert "feat(26.1.2): 卡牌/筹码商人村民补齐 Blockbench 的摊位几何（柜台 + 遮阳棚）"
8dd2bf71 feat(26.1.2): 卡牌/筹码商人村民补齐 Blockbench 的摊位几何（柜台 + 遮阳棚）
e9504696 fix(26.1.2): 数据包按 26.1.2 口径重写，服务端实跑 0 解析错误
e2a5ebf4 feat(26.1.2): 村民交易改为数据驱动并跑通 datagen（218 条报价）
ff4d017b feat(26.1.2): 跑通双端 datagen，产物入库（316 + 217 文件）
f109d07f feat(26.1.2): 同步 1.21.1 的资源与语言键（278 文件 + 253×3 键）
c6e74268 feat(26.1.2): 打通 rc.7 移植的编译层（EXIT=0，545 个 class）
ee6493ba wip(26.1.2): 移植 1.5.0-rc.7 结构层（240 文件合并 / 149 类整文件 / 机械 API 迁移）
f11ec162 docs(handover): 记录本批提交哈希（464e29f8 / 06fef75a / d0a084b8），未 push
d0a084b8 docs(agent,handover): 出网口径改 TUN 直连；属主坑改为已修复口径
06fef75a build(gradle): 投放根 Linux 分支改 /mnt/d/.minecraft（与 Windows 共用同一块 NTFS 盘）
464e29f8 docs(agent,handover): 规则正本迁原生 Linux，清 hgfs 硬编码；新增真机迁移交接
c31d36f6 build(gradle): 整合包投放根改 mcHomePath 解析；Jade 插件键两线补齐
96895771 docs(agent,handover): Linux 规则正本 + 10-31 交接，补文件级清单与附录 A 记录
```

⇒ 24 个提交里 18 个是 **neoforge-26.1.2 的 rc.7 移植收尾**，其余是村民模型、立牌半透明与文档。

---

## 2. 工作树未提交改动分布（644 条）

### 2.1 总量与按顶层目录统计

`git status --porcelain` 共 **644 条** = 未跟踪 `??` **365** + 修改 ` M` **234** + 删除 ` D` **45**。

| 顶层路径 | 条数 | 说明 |
|---|---:|---|
| `fabric-1.20.1/` | 473 | 移植尾批（229 ?? + 199 M + 45 D） |
| `porting/` | 65 | 全部未跟踪（移植过程报告） |
| `scripts/` | 38 | 测试台 + 用例 + 守门脚本 |
| `docs/` | 13 | 交接/规格/归档 |
| `neoforge-26.1.2/` | 12 | 立牌半透明 + YSM alpha + 审计缺陷修复 |
| `forge-1.20.1/` | 9 | 立牌半透明 + lang |
| `neoforge-1.21.1/` | 5 | 立牌半透明 |
| `tools/` | 3 | 守门脚本 |
| `models/` | 2 | 新 bbmodel（素材，红线：不得 add） |
| `gradle/` | 1 | wrapper 版本 |
| 根目录单文件 | 22 | 临时脚本/日志/备份 |
| **合计** | **644** | |

### 2.2 fabric-1.20.1 明细（473 条）

- 未跟踪 229 = `src/main` 194 + `src/generated` 35
- 修改 199 = `src/main/resources` 99 + `src/main/java` 99 + `build.gradle` 1
- 删除 45

### 2.3 45 个删除的构成（全部在 fabric-1.20.1）

- **22 个** `fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/*.json`
- **22 个** `fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/*.json`
  - 以上 44 个全部是 `attack_card_*` / `defense_card_*` / `effect_card_*` 的卡牌配方与解锁成就
- **1 个** `fabric-1.20.1/src/main/resources/assets/astral_dice/textures/mob_effect/railgun_ready.png`

### 2.4 判定：「是不是 datagen 重新生成导致文件名变化？」

**部分成立，但方向要说清楚**：

1. `fabric-1.20.1/src/generated` 的确在 **2026-11-01 19:31:52** 全量重新生成过。
2. 盘上 `data/astral_dice/recipes` 现有 **108** 个文件，与 `forge-1.20.1/src/generated` 的同名目录 **108 个文件名完全一致**（`diff` 无差异）。
3. git 侧跟踪 **118** 个 ⇒ `118 − 22(删) + 12(新增) = 108`。
4. **被删的 22 个卡牌配方不是「改名」而是「陈旧残留被清掉」**：这些文件名（如 `attack_card_charge.json`）在**其他三条线也都不存在**（`forge-1.20.1` / `neoforge-1.21.1` / `neoforge-26.1.2` 均无），对应 `docs/archive/card-recipes-2026-10-09` 那次「卡牌配方移除」；fabric 当时没跟，这次 datagen 重跑顺手清了。
5. **12 个新增 recipes**：`abyss_reagent_chip` / `alana_sign` / `ame_sign` / `dorothy_sign` / `extra_battery_chip` / `flashlight_burst_chip` / `fortune_stick_chip` / `infinite_grid_chip` / `jill_sign` / `kangel_sign` / `lingling_sign` / `sensor_chip`。
6. **17 个新增 models/item**：`abyss_reagent_chip` / `alana_sign` / `ame_sign` / `deepseek_sign` / `dorothy_sign` / `effect_card_barricade` / `effect_card_repulsion` / `event_ticket` / `extra_battery_chip` / `flashlight_burst_chip` / `fortune_stick_chip` / `infinite_grid_chip` / `jill_sign` / `kangel_sign` / `lingling_sign` / `repulsion` / `sensor_chip`。

### 2.5 fabric 与 forge 的 generated 树仍不等价（**发布前必须处理**）

- 文件数：fabric **364** / forge **371**
- forge 独有：`resources/.cache/137fcbd0…`、`.cache/9fb1092f…`（fabric 的 `src/generated/resources/.cache` 为 **0** 个；`neoforge-1.21.1` / `neoforge-26.1.2` 各有 2 个）
- forge 独有：6 个 sign 解锁成就 `advancements/recipes/misc/{alana,ame,dorothy,jill,kangel,lingling}_sign.json` —— **fabric 缺**
- fabric 独有：`assets/astral_dice/models/item/event_ticket.json`（forge 无）
- `recipes` 逐字节只有 **6/108** 相同，根因是 **chip 配方的条件块**：
  - forge 侧 `forge-1.20.1/src/generated/resources/data/astral_dice/recipes/sensor_chip.json` 带 `"conditions": [{"type": "astral_dice:chip_recipes_enabled"}]`，**67 个 chip 配方带该条件**
  - fabric 侧同名文件**全部不带**（0 个）
  - fabric 另有 2 个文件用 `"type": "astral_dice:nbt_shaped"`

⇒ 若 fabric 线与 forge 线的 chip 获取途径要有意不同，这属于**分叉**要在文档里写清；若无此意，则是 datagen 条件注册缺失（`chip_recipes_enabled` 条件在 fabric 线未注册），属移植缺口。**本报告只取证，不下裁决。**

### 2.6 改动归属：「这些改动是谁的、什么性质」

**不是 fabric datagen 单独产物**，主体是三股并行工作：

| 股 | 内容 | 证据（示例文件） |
|---|---|---|
| (a) 立牌半透明 / YSM alpha 渲染改造 | 四线 `SignTranslucencyMixin` + 新增 `ClientAlpha` / `DispatcherAlphaMixin` / `RenderTypeAlphaMixin` + 26.1.2 独有 `ysm` 包与 `AstralYsmMixinPlugin` | `neoforge-1.21.1/src/main/java/.../ClientAlpha.java`(??)、`.../mixin/client/SignTranslucencyMixin.java`(M)、`neoforge-26.1.2/src/main/java/.../mixin/AstralYsmMixinPlugin.java`(??)、`neoforge-26.1.2/src/main/resources/astral_dice.ysm.mixins.json`(??) |
| (b) fabric-1.20.1 移植尾批 | 65 份 porting 报告 + scripts 测试台/用例 + fabric 99+99 文件改写 + generated 重生成 | `porting/fabric-1.20.1/ALIGNMENT-AUDIT-2026-11-01.md`、`scripts/test/cases/*.json`(23 个 ??)、`fabric-1.20.1/build.gradle`(M) |
| (c) 文档与工具脚本 | 交接、规格、守门脚本 | `docs/handover-2026-11-01-windows-ysm-*.md`(??)、`tools/verify_sign_display_names.py`(M) |

- 另有 **2 个新 bbmodel**：`models/astral_anvil.bbmodel`、`models/cardshop_villager_1.bbmodel` —— 属素材/用户侧，按 `AGENTS.md` 共同红线第 4 条**不得 add / commit / stash / checkout**。

---

## 3. 四条线是否都能构建（**未跑 gradlew**，纯产物取证）

### 3.1 各线 `build/libs` 最新 jar

```
$ ls -la --time-style=full-iso neoforge-1.21.1/build/libs
-rw-r--r-- 2607086  2026-10-29 18:07:59.140124400  astral_dice-1.5.0-rc.6+neoforge_1.21.1.jar
-rw-r--r-- 2611872  2026-11-01 23:43:45.216606000  astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar

$ ls -la --time-style=full-iso forge-1.20.1/build/libs
-rw-r--r-- 2660152  2026-11-01 23:43:54.796693600  astral_dice-1.5.0-rc.7+forge_1.20.1.jar

$ ls -la --time-style=full-iso neoforge-26.1.2/build/libs   (共 13 个 jar，最新一个)
-rw-r--r-- 2875210  2026-11-01 23:31:44.282385000  astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar

$ ls -la --time-style=full-iso fabric-1.20.1/build/libs
-rw-r--r-- 3027461  2026-11-01 23:16:45.660385400  astral_dice-1.5.0-rc.7+fabric_1.20.1.jar
```

### 3.2 jar 与源码是否同步（`find <线>/src -type f -newer <该线 rc.7 jar>`）

| 线 | 比 rc.7 jar 更新的 src 文件数 | 明细 |
|---|---:|---|
| neoforge-1.21.1 | 0 | — |
| forge-1.20.1 | 0 | — |
| neoforge-26.1.2 | **1** | `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/client/BeamFxRenderer.java`（2026-11-01 23:32；jar 23:31:44） |
| fabric-1.20.1 | 0 | — |

- 该唯一不同步文件的 `git diff` 是**纯 javadoc 注释**：`{@code BEAM_PARTICLES} 枚发光尘` → `84 枚发光尘`（1 行 +1/−1），**不影响 class 产物** ⇒ 可判为同一构建。

各线 src 最后改动时间（max）：

```
n21  2026-11-01 23:43:18.962  .../mixin/client/SignTranslucencyMixin.java
fg   2026-11-01 23:43:18.963  .../mixin/client/SignTranslucencyMixin.java
n26  2026-11-01 23:32:43.312  .../client/BeamFxRenderer.java
fb   2026-11-01 23:15:54.397  .../item/card/BaseEffectCardItem.java
```

### 3.3 编译中断残留检查

- **`_fabric_remap.log`（501 B / 12 行，2026-11-01 21:43:33）内容摘要**：

```
Reusing configuration cache.
> Task :fabric-1.20.1:remapJar
BUILD SUCCESSFUL in 7s
5 actionable tasks: 1 executed, 4 up-to-date
```

  ⇒ 是**成功**记录，不是错误日志。fabric 线的 remapJar 已跑通。

- **根目录 14 个 `javac.*.args`**（2026-11-01 18:45:25 ~ 18:49:10，49~95 B）内容是 javac 的 @argfile 指针，全部指向 fabric 线的临时目录：

```
javac.20261101_184525.args -> @fabric-1.20.1/build/b2-6-before/args-before.txt
javac.20261101_184606.args -> @D:/MCProject/astral_dice_multiloader/fabric-1.20.1/build/b2-6-before/args-before.txt  [-verbose]
javac.20261101_184616.args -> @F:/...\fabric-1.20.1\build\javac-check\args_jade_plain.txt
javac.20261101_184632.args -> @F:\...\fabric-1.20.1\build\javac-check\args.txt
javac.20261101_184910.args -> @F:\...\fabric-1.20.1\build\javac-check\args.txt
```

  ⇒ 手工 javac 探针（B2-6 / javac-check 批）残留，**不是**编译中断标记。

- **`hs_err` 共 4 份，全在 `run/` 下、全是 2026-10-25 的游戏端 JVM 崩溃**（与编译无关）：

| 文件 | mtime | Problematic frame |
|---|---|---|
| `run/1.20.1/hs_err_pid2924.log` | 2026-10-25 17:01:47 | `glfw.dll+0xf8ab` |
| `run/1.20.1/hs_err_pid75264.log` | 2026-10-25 12:26:32 | `glfw.dll+0xf8ab` |
| `run/1.21.1/hs_err_pid18040.log` | 2026-10-25 17:01:47 | `glfw.dll+0x101ab` |
| `run/1.21.1/hs_err_pid37036.log` | 2026-10-25 12:26:31 | `glfw.dll+0x101ab` |

  （4 份均为 `EXCEPTION_ACCESS_VIOLATION (0xc0000005)`，Temurin 17 / Zulu 21，显卡驱动层崩在 GLFW）

### 3.4 结论

**四条线都能构建，且 jar 与源码同步**：
- 每条线都有 2026-11-01 23:xx 的新 rc.7 jar；
- 没有任何线存在比 jar 更新的实质源码（n26 那 1 个只差注释）；
- 无编译中断残留（唯一疑似的 `_fabric_remap.log` 是成功记录，`hs_err` 全是游戏端 GLFW 崩溃）。

⚠️ 但注意「能构建」≠「已推送/已发布」：24 个提交未 push，本地无 rc.7 tag。

---

## 4. fabric-1.20.1 移植是否已完成

### 4.1 `porting/fabric-1.20.1/ALIGNMENT-AUDIT-2026-11-01.md`

文件：48,815 B / 737 行，mtime **2026-11-01 23:07:53**，git 未跟踪。

**§1 结论总表（line 97-115，R1-R16）；line 116 原文计数**：
> 「对齐 **10 项**（R1–R4、R6–R8、R10–R13）；分叉(有理由) **1 项**（R11）；缺陷 **5 项**（R5、R9、R14、R15、R16，展开为 §3 的 **9 条**：fabric 4 条 + 第三线 5 条）；无法判定 **9 项**（§4）」

**§3 缺陷清单（line 632-642 表）**：

| ID | 级别 | 侧 | 问题 | 当前工作树状态（grep 实测） |
|---|---|---|---|---|
| C-1 | 高 | fabric | 阿兰娜立牌「每次赐福 +5 攻击」完全不可达 | **已修**（`DiceCombatEvents.java:596`） |
| C-2 | 高 | fabric | 缺 `TargetSelectClose` 关闭包，事件卡动画窗后客户端可能停在选择模式 | **已修**（`ModNetwork.java:127-128` / `:483`） |
| C-3 | 中 | fabric | `SignHookDispatcher` 11 个 `fire*` 全无调用者（整类死代码） | **未修**（外部调用者 = 0） |
| C-4 | 低 | fabric | 8 个类被 LoaderBus 登记但零处理器 | 未核对（本批未查） |
| P-1 | 高 | 26.1.2 | `BankCardUnlimitedChipItem` 缺 ALLY_RANGE / 距离判定 | **已修**（`ALLY_RANGE=32.0`） |
| P-2 | 高 | 26.1.2 | `CardChoice*Payload` 有类未注册却被发送 | **已修**（`ModPayloads.java:101-108`） |
| P-3 | 中 | 26.1.2 | 阿兰娜被动同样不可达 + dispatcher 死代码 | **部分**（`fire*` 已有 9 处调用者） |
| P-4 | 低 | 26.1.2 | `BEAM_PARTICLES=84` 死常量（原在 `combat/DamageCardFxScheduler.java:114`） | **已清**（全量 grep = 0） |
| P-5 | 低 | 26.1.2 | 创造栏缺 `ATTACK_CARD_BITE` / `ATTACK_CARD_DRAGON_ROAR`、效果多 `railgun_ready` | **未动**（产品裁决项） |

**§4 无法判定 9 项（line 700-714，U1-U9）**：激光观感 / 光束去重 / 动态槽位 resize / MergedHandler 读写归属 / 蹲右键落点 / 死亡不掉落 / C-2 客户端是否卡在选择模式 / 跨线协议兼容 / n26 未注册 payload 抛点 —— **全部需实机**。

**§5 建议批次（line 718-728）**：B1 修 C-1、B2 修 C-2、B3 清 C-3（推荐把 fabric 直调改回 `fire*` 或删干净）、B4 清 C-4、B5 另开 26.1.2 批次修 P-1..P-5。

**该文档自述（line 734-736）**：全部为文本级静态取证，未 javap、未构建、未实机；唯一写入是该报告文件本身。

### 4.2 `porting/fabric-1.20.1/B-BUILD-LOOM-REPORT.md`

文件：12,954 B / 135 行，mtime **2026-11-01 18:46:21**。

**头部（line 3-5）原文**：
> 「**状态：未完成（阻塞）**。目标 Loom 版本已在 `fabric-1.20.1/build.gradle` 落盘，但 "JEI 重新进入 compileClasspath" **没有在真实环境跑通**：本仓共享的 Gradle wrapper 是 **9.2.1**，而任何 ≥1.17 的 Loom 都要求 Gradle ≥9.5.0。该 wrapper 由四条线共用，改它属跨线决策，本批**未执行**」

- line 8-12：声明跑过插件解析 / 配置期 / Loom 1.19.3 加载，**没跑过** `dependencies` / `tasks --all` / 编译 / `remapJar`（沙箱 `Files.isWritable()` 全 false ⇒ Loom merge 抛 `ReadOnlyFileSystemException`）。
- §2.4（line 82-88）：Loom 1.19.3 与 build.gradle DSL 完全兼容，真门槛是**构建工具链**不是 MC 兼容性；三路线 A（wrapper→9.5.1 + Loom 1.17.21，JDK21）/ B（9.8.2 + 1.19.3 + JDK25）/ C（不升 wrapper ⇒ 停在 Loom ≤1.15.x，8 个 compat/jei 类无法恢复）。
- §5（line 130-135）：8 个 compat/jei 类**本批未写**；下一批 B-JEI-COMPAT 的前置门槛 = ① `:fabric-1.20.1:dependencies --configuration compileClasspath | grep -i jei` 有命中；② `tasks --all` 里 `runClient/runServer/runDatagen/push*` 全在。

**该报告的「阻塞」已被后续工作解除**（工作树实测）：

```
gradle/wrapper/gradle-wrapper.properties:3
  distributionUrl=https\://mirrors.aliyun.com/gradle/distributions/v9.5.1/gradle-9.5.1-bin.zip
  （git diff：v9.2.1 → v9.5.1，即走了路线 A）

fabric-1.20.1/build.gradle:14   id 'fabric-loom' version '1.17.21'
fabric-1.20.1/build.gradle:179  modImplementation "maven.modrinth:jei:pQiaGCAN"
  （:172-178 注释：JEI 15.62.0.216，Modrinth version id pQiaGCAN；forge 变体 id 是 p5jzDqhi）
```

8 个 compat/jei 类**已存在**于 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/compat/jei/`：`CardShopCategory` / `CardShopJeiPlugin` / `CardShopJeiRecipes` / `CardShopRecipe` / `ChipShopCategory` / `ChipShopJeiPlugin` / `ChipShopJeiRecipes` / `ChipShopRecipe`。

### 4.3 「已完成 / 未完成 / 待办」汇总

**已完成**
- Gradle 工具链阻塞解除：wrapper 9.5.1 + fabric-loom 1.17.21，`remapJar` BUILD SUCCESSFUL（`_fabric_remap.log`），产出 `astral_dice-1.5.0-rc.7+fabric_1.20.1.jar`（3,027,461 B，2026-11-01 23:16:45）。
- JEI 重新进入 compileClasspath，8 个 `compat/jei` 类全部落地。
- C-1（阿兰娜立牌被动）、C-2（TargetSelectClose）已修。
- 26.1.2 侧 P-1 / P-2 / P-4 已修，P-3 大部分已修。
- datagen 重新生成并对齐文件名（108 recipes 与 forge 同名同集合）。

**未完成 / 待办**
1. **C-3 未修**：`grep -rn 'SignHookDispatcher\.fire' fabric-1.20.1/src/main/java | grep -v SignHookDispatcher.java | wc -l` = **0**（fabric 侧 dispatcher 仍是死代码；26.1.2 侧已有 9 处调用者）。
2. **C-4 未核**（8 个空登记的类：`AlanaSignItem` / `AstralDiceMod` / `ClientEvents` / `ClientKeyNames` / `DeepseekSignItem` / `DorothySignItem` / `FannySignItem` / `JillSignItem`）。
3. **P-5 未裁决**（创造栏缺 `ATTACK_CARD_BITE` / `ATTACK_CARD_DRAGON_ROAR`、效果多 `railgun_ready`）。
4. **U1–U9 全部未实机**（9 项无法判定）。
5. **generated 树的三处不齐**：缺 `.cache`（0 vs 2）、缺 6 个 sign 解锁成就、chip 配方缺 `chip_recipes_enabled` 条件块。
6. **fabric 线 rc.7 从未投放进 `run/`**：`run/fabric-1.20.1/mods` 只有 `astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar`（2026-10-06），`fabric-1.20.1/run/{client,server}/mods` 无本模组 jar ⇒ **fabric 线实机从未按 rc.7 验证过**。
7. 两份 porting 报告都**落后于工作树**（快照 23:07 / 18:46，修复在之后落地），发布前应重跑一份新的对齐审计。

### 4.4 版本号与 jar 吻合性

`fabric-1.20.1/gradle.properties:29` = `mod_version=1.5.0-rc.7+fabric_1.20.1`，与 `fabric-1.20.1/build/libs/astral_dice-1.5.0-rc.7+fabric_1.20.1.jar` **完全吻合**。

---

## 5. neoforge-26.1.2 是否产出过 1.5.0 系列 jar

**产出过，且只有 rc.7 这一个**：

```
$ ls -la neoforge-26.1.2/build/libs    （13 个 jar，1.5.0 系列仅 1 个）
-rw-r--r-- 2875210  2026-11-01 23:31:44.282385000  astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar
其余 12 个为 2026-09-17 ~ 2026-10-05 的旧 beta：
  1.2.1-beta / 1.2.1-beta.2 / 1.3.0-beta.1 / 1.3.1-beta.1 / 1.3.2-beta.1 / 1.3.3-beta.1
  / 1.3.4-beta.1 / 1.3.5-beta.1 / 1.3.5-beta.2 / 1.3.6-beta.1 / 1.3.7-beta.1 / 1.3.7-beta.2
```

**该线是否做完？** —— 「代码/构建层已完成，验收层未完成」：
- 编译层：`c6e74268 feat(26.1.2): 打通 rc.7 移植的编译层（EXIT=0，545 个 class）`；
- 数据层：`ff4d017b` / `e2a5ebf4`（datagen 316+217 文件、218 条报价）/ `e9504696`（服务端实跑 0 解析错误）；
- 收尾缺口：`9192a0bc`「补齐移植时被合并吞掉的 8 组实现」+ `fd990c15`「补齐收尾验证发现的混入缺口」；
- 但审计文档仍列 P-5 未裁决、U1–U9 未实机；且该线 **从未有过 1.5.0 正式版/更早的 rc jar**，rc.7 是这条线的**第一个 1.5.0 产物**。

---

## 6. 四条线的 `mod_version` 现值与版本复述点

### 6.1 单点源（精确行号）

```
neoforge-1.21.1/gradle.properties:37   mod_version=1.5.0-rc.7+neoforge_1.21.1
forge-1.20.1/gradle.properties:41      mod_version=1.5.0-rc.7+forge_1.20.1
neoforge-26.1.2/gradle.properties:54   mod_version=1.5.0-rc.7+neoforge_26.1.2
fabric-1.20.1/gradle.properties:29     mod_version=1.5.0-rc.7+fabric_1.20.1
```

上下文键（同文件）：
- `neoforge-1.21.1`: `:15 minecraft_version=1.21.1`、`:31 mod_id=astral_dice`、`:126 starengine_version=1.1.0`
- `forge-1.20.1`: `:14 minecraft_version=1.20.1`、`:34 mod_id`、`:114 starengine_version=1.1.0`
- `neoforge-26.1.2`: `:17 minecraft_version=26.1.2`、`:44 mod_id`、`:113 starengine_version=1.1.0`
- `fabric-1.20.1`: `:10 minecraft_version=1.20.1`、`:17 loader_version=0.19.5`、`:21 mod_id`、`:40 starengine_version=1.1.0`、`:42 starengine_version_range=>=1.1.0`、`:86 jei_version=15.62.0.216`

### 6.2 版本号在别处的复述点（**结论：mod 元数据全部用占位符，不写死**）

```
neoforge-1.21.1/src/main/templates/META-INF/neoforge.mods.toml:17   version="${mod_version}"
neoforge-26.1.2/src/main/templates/META-INF/neoforge.mods.toml:17  version="${mod_version}"
forge-1.20.1/src/main/templates/META-INF/mods.toml:21              version="${mod_version}"
fabric-1.20.1/src/main/resources/fabric.mod.json:4                "version": "${version}"
```

build 侧：`version = mod_version`（`neoforge-1.21.1/build.gradle:10` / `forge-1.20.1/build.gradle:9` / `neoforge-26.1.2/build.gradle:11` / `fabric-1.20.1/build.gradle:18`），`processResources` expand 的 `mod_version` 键（`neoforge-1.21.1/build.gradle:222` / `forge-1.20.1/build.gradle:182` / `neoforge-26.1.2/build.gradle:258`）。⇒ **改版本只需改 4 个 `gradle.properties`**。

### 6.3 `grep -rn "1.5.0-rc.7"` 命中清单

（排除 `build/`、`.git/`、`.gradle/`、`work/`、`.cache`）共 **165 个文件**。分类：

| 类别 | 命中点 |
|---|---|
| **代码/元数据（真实生效）** | 4 个 `gradle.properties`（见 6.1）；`fabric-1.20.1/run/**/logs/*.log`（运行期日志，非配置） |
| **发布说明** | `release/1.5.0-rc.7/PLAYER_CHANGELOG.md:1,3,9,10,44`；`release/1.5.0-rc.7/PLAYER_CHANGELOG_ZH.md:1,3,9,10,43` |
| **docs（交接/规格/归档）** | `docs/agent/AGENTS-dsh-linux.md:180`；`docs/features/ysm-compat-err54-and-verification.md:105`；`docs/handover-1.5.0-main.md:15,17,1130`；`docs/handover-2026-10-31-linux-vm-adoption.md:41`；`docs/handover-2026-10-31-local-disk-workspace.md:15`；`docs/handover-2026-10-31-native-linux-migration.md:110,111`；`docs/handover-2026-11-01-windows-ysm-handoff.md:38`；`docs/handover-new-session-context.md:19`；`docs/migration-26.1.2-live-checklist.md:1`；`docs/migration-26.1.2-rc7-mapping.md:1,9,597`；`docs/superpowers/plans/2026-10-29-event-choice-force-first.md:21` |
| **porting 报告（大量）** | `porting/**` 的 `B20A`/`B21`/`B23`/`B24`/`B25`/`B26`/`B27`/`B28`/`B29`/`B31`/`B33`/`B34`/`B35`/`B36` 与 `LIVE-TEST-LIST-2026-11-02.md`（其中多数是「`build/libs` 内 jar 版本 1.3.7-alpha.2 与 `mod_version` 1.5.0-rc.7 不符」的历史守门提示） |

只 grep 根 md 的 `1.5.0`：

```
CHANGELOG.md:7      ## 1.5.0
CHANGELOG.md:800    - **Source code is now GPL-3.0-only**: from **1.5.0** onward ...
CHANGELOG_ZH.md:7   ## 1.5.0
CHANGELOG_ZH.md:657 - **源代码改为 GPL-3.0-only**：从 **1.5.0** 起 ...
README.md:31        ## What's new in 1.5.0
README.md:33        本节覆盖 1.5.0 全部预发布（pre）与候选（rc）构建已交付的内容 ...
README_ZH.md:31     ## 1.5.0 更新内容
README_ZH.md:33     本节覆盖 1.5.0 全部预发布（pre）与候选（rc）构建已交付的内容 ...
```

根 md 文件共 6 个：`AGENTS.md` / `CHANGELOG.md` / `CHANGELOG_ZH.md` / `KNOWN-ISSUES.md` / `README.md` / `README_ZH.md`。

⚠️ **口径冲突（发布前必须统一）**：`CHANGELOG.md` 的 `#### Platform & Compatibility` 段写「**New Fabric 1.20.1 build**: the mod now ships for **four platform lines with full feature parity** — NeoForge 1.21.1 / Forge 1.20.1 / NeoForge 26.1.2 / Fabric 1.20.1」，而 `release/1.5.0-rc.7/PLAYER_CHANGELOG.md:9-10` 明写「本预发布只提供两个构建 … 其它平台线本版不提供构建」。

---

## 7. `release/` 结构

### 7.1 目录清单

```
release/1.2.0  1.2.1  1.3.0  1.3.1  1.3.2  1.3.3  1.3.4  1.3.5  1.3.6  1.3.7
release/1.5.0-rc.1 ... 1.5.0-rc.7
release/fabric-1.3.5-alpha.1  fabric-1.3.6-alpha.1  fabric-1.3.7-alpha.2
release/starengine-lib        （空目录）
```

- **没有 `release/1.5.0/`**（正式版目录尚未创建）。

### 7.2 `release/1.5.0-rc.7/` 内容

```
PLAYER_CHANGELOG.md      5,925 B
PLAYER_CHANGELOG_ZH.md   5,350 B
```

- **`PLAYER_CHANGELOG*.md` 存在**（中英各一份）。
- **缺 `PLAYER_CHANGELOG_MCMOD.html`**：`1.5.0-rc.1` ~ `rc.6` 每一个目录都有该文件（40,569 ~ 53,069 B），`rc.7` **没有** ⇒ 若正式版要求 HTML 版说明，需要补。

---

## 8. 临时资产 / 旧 jar 清单（供后续清理）

### 8.1 仓库根未跟踪单文件（22 个）

```
javac.20261101_184525.args … javac.20261101_184910.args   （14 个，共约 1 KB）
_b20a_jarinfo.py        _fabric_remap.log（501 B）      _sched.bat
_tmp_b11_bytes.py       _tmp_b11_fix.py                 zz_screen_probe.py（450 B）
acl-backup-20261101-153946.txt（2,120 B）                tmptekih3ja.sh（23,128 B）
```

### 8.2 仓库根 `build/libs`（4 个 jar）

```
astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar   2026-10-06 13:56:30  1,836,403   旧
astral_dice-1.5.0-rc.7+forge_1.20.1.jar       2026-11-01 02:11:13  2,657,661   ← 落后于线内 23:43 版
astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar    2026-11-01 02:11:13  2,609,323   ← 落后于线内 23:43 版
astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar    2026-11-01 23:31:44  2,875,210   与线内同
```

⇒ 根 `build/libs` 是**分发副本**，其 neo/forge 两个 rc.7 是 02:11 的旧构建，**打包发布前必须从各线 `build/libs` 重新同步**。

### 8.3 各线 `build/libs` 旧版本 jar

| 线 | 非 rc.7 的 jar 数 | 明细 |
|---|---:|---|
| neoforge-1.21.1 | 1 | `astral_dice-1.5.0-rc.6+neoforge_1.21.1.jar`（2026-10-29 18:07:59） |
| forge-1.20.1 | 0 | — （`build/devlibs` 另有 1.3.0 / 1.3.1 / 1.3.2 旧 dev jar） |
| neoforge-26.1.2 | **12** | `1.2.1-beta`、`1.2.1-beta.2`、`1.3.0-beta.1`、`1.3.1-beta.1`、`1.3.2-beta.1`、`1.3.3-beta.1`、`1.3.4-beta.1`、`1.3.5-beta.1`、`1.3.5-beta.2`、`1.3.6-beta.1`、`1.3.7-beta.1`、`1.3.7-beta.2`（2026-09-17 ~ 2026-10-05） |
| fabric-1.20.1 | 0 | — （`build/devlibs` 有 `astral_dice-1.5.0-rc.7+fabric_1.20.1-dev.jar`） |
| **合计** | **13** | |

### 8.4 `run/` 下 mods 里的 jar（旧包 / .bak）

| 目录 | 文件 | 时间 | 大小 |
|---|---|---|---|
| `run/1.20.1/mods` | `astral_dice-1.5.0-rc.7+forge_1.20.1.jar` | 23:46:32 | 2,630,753（⚠️ 与线内 2,660,152 不同，是更早一次构建） |
| | `starengine-forge-1.20.1-1.1.0.jar` | — | — |
| `run/1.21.1/mods` | `astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar` | 23:46:32 | 2,611,872（= 线内同尺寸） |
| | `starengine-neoforge-1.21.1-1.1.0.jar` | — | — |
| `run/26.1.2/mods` | `astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar` | 23:31:44 | 2,875,210 |
| | `...jar.old-20261101-2240.bak` | 22:39:26 | 2,875,164 |
| | `...old-20261101.jar.bak` | 14:40:03 | 2,866,366 |
| `run/26.1.2/prodgame/mods` | `astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar` | 22:39:26 | 2,875,013 |
| | `...jar.compat-on.bak` | 22:10:38 | 2,875,164 |
| | `starengine-neoforge-26.1.2-1.1.0.jar` | — | — |
| `run/better-mc-bmc4-1.20.1/mods` | `astral_dice-1.3.2-hotfix+forge_1.20.1.jar` | 2026-09-29 00:36:46 | 1,510,954（**旧包**） |
| `run/fabric-1.20.1/mods` | `astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar` | 2026-10-06 13:56:30 | 1,836,403（**旧包**，fabric rc.7 **从未投放**） |
| `fabric-1.20.1/run/client/mods` | 无 astral_dice / starengine jar | — | — |
| `fabric-1.20.1/run/server/mods` | 无 astral_dice / starengine jar | — | — |

### 8.5 目录体积总量

```
build  530 M   （_gtest 304 M / reports 133 M / _ilcache 30 M / _merged-1.20.1-backup.jar 26 M
                / _ilout 11 M / libs 9.6 M / _jei_versions.json 7.1 M / _loomex 4.6 M
                / _jei_fabric.jar 2.2 M / _jadeprobe 1.9 M）
temp   617 M   （pre6-p0-live-evidence 387 M / autojs-mcp-skill-verification 21 M / t02 14 M
                / cfcheck 13 M / elx 9.2 M / elplus_x 8.3 M / tester1211_evidence 8.1 M
                / ci-artifact 6.8 M / shot-f3.png 6.4 M / shot-full.png 5.0 M
                / reobf-check 5.0 M / mods-bak-datagen 5.0 M）
run    6.0 G
work   26 K    （work/verify-denial/: check.py + N_1x.png / plateN_x10.png / plateS_x10.png
                / S_1x.png / texture_rects.png / texture_x4.png）
仓库总体（排除 .git） 8.9 G
```

- `.gitignore` 已覆盖：`build/`（:2, :60）、`run/`（:36, :61）、`temp/`（:42, :62, :107）、`.gradle`（:2）。
- `work/` **不在 `.gitignore` 里**，但当前 `git status --porcelain work` 为空（现有 `work/verify-denial/` 未产生未跟踪噪声，原因无法判定）。本报告写入 `work/recon/` 后会新增未跟踪文件，属任务允许的唯一写入。

---

## 9. 总览结论（发 1.5.0 正式版前的现实状态）

**四条线的构建状态：全部可用**

| 线 | rc.7 jar | 时间 | 大小 | 源码同步 | 版本号吻合 |
|---|---|---|---|---|---|
| neoforge-1.21.1 | ✅ | 2026-11-01 23:43:45 | 2,611,872 | ✅ | ✅ |
| forge-1.20.1 | ✅ | 2026-11-01 23:43:54 | 2,660,152 | ✅ | ✅ |
| neoforge-26.1.2 | ✅ | 2026-11-01 23:31:44 | 2,875,210 | ✅（1 个注释文件之后改过） | ✅ |
| fabric-1.20.1 | ✅ | 2026-11-01 23:16:45 | 3,027,461 | ✅ | ✅ |

**发布前的 9 个待办（按优先级）**

1. `git fetch --tags` 对齐远端 `1.5.0-rc.7`（本地缺失）；决定 24 个领先提交是否随正式版一起 push。
2. 统一版本口径：4 个 `gradle.properties` 从 `1.5.0-rc.7+<platform>` 升到 `1.5.0+<platform>`（单点源，无需改 mods.toml / fabric.mod.json）。
3. 创建 `release/1.5.0/` 目录，产出 `PLAYER_CHANGELOG.md` + `PLAYER_CHANGELOG_ZH.md`（rc.7 还缺 `PLAYER_CHANGELOG_MCMOD.html`）。
4. 解决 **README/CHANGELOG「四线全功能对等」vs rc.7 发布说明「只提供两个构建」** 的口径冲突。
5. 清 fabric-1.20.1 的 **C-3（SignHookDispatcher 死代码）** 与核对 **C-4（8 个空登记类）**。
6. 裁决 **P-5**（26.1.2 创造栏缺两张攻击卡、效果多 `railgun_ready`）。
7. 处理 fabric generated 与 forge 的三处不齐：`.cache`（0 vs 2）、6 个 sign 成就、chip 配方 `chip_recipes_enabled` 条件块。
8. **fabric 线从未在 rc.7 上实机验证**（`run/` 里只有 1.3.7-alpha.2）；U1–U9 共 9 项也全需实机。
9. 清理临时资产：根目录 22 个未跟踪文件 + 各线 13 个旧 jar + `run/` 下 4 个 `.bak`/旧包 + 根 `build/libs` 里 1 个旧包与 2 个落后副本（`build/` 530 M、`temp/` 617 M、`run/` 6.0 G 可选清）。

**无法判定项（必须实机，不允许脑补）**：审计文档 §4 的 U1–U9，以及 fabric / 26.1.2 两线的运行时行为对等性。
