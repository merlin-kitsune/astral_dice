# forge-1.20.1：`build/libs` 与 `run/1.20.1/mods` 的 jar 不一致 —— 调查结论

- 仓库：`F:\MCProject\astral_dice_multiloader`
- 分支：`multi-1.20.1-1.21.1`，HEAD `a67caa7`（工作树干净）
- 调查时间：本次双版本构建之后（jar mtime 2026-09-15 20:04:52 / 20:05:10-11）
- 调查者：构建产物调查者（subagent）
- **结论先行**：**这是有意为之的设计行为，不是缺陷。** 差异 100% 等价于「reobf（SRG 名）↔ 未 reobf（Mojmap 名）」+ remap 过程重建常量池带来的指令编码差异；两份 jar 是**同一个程序**。`runClient` 实测加载的是 `run/1.20.1/mods` 里的**那份 dev（未 reobf）jar**，不是 `build/libs` 的发货 jar——但这不影响 1.20.1 游戏内测试结论的有效性（论证见 §6/§7）。

---

## ① 现象与哈希

| 文件 | 字节数 | SHA256 | mtime | 身份 |
|---|---|---|---|---|
| `forge-1.20.1/build/libs/astral_dice-1.2.1+forge_1.20.1.jar` | 979097 | `60F8393881359EC1B11266E4FF8369DE69948BDA344BFBC4EED6A35E0EA4F784` | 20:05:11 | **生产/reobf（SRG）** = 发货产物 |
| `run/1.20.1/mods/astral_dice-1.2.1+forge_1.20.1.jar` | 961403 | `F5918EE507C62D1AF9851E7481DD134F02F6DEC42F86F48E646E7A791DFF82E7` | 20:05:11 | **开发/未 reobf（Mojmap）** |
| `forge-1.20.1/build/devlibs/astral_dice-1.2.1+forge_1.20.1.jar` | 961403 | `F5918EE507C62D1AF9851E7481DD134F02F6DEC42F86F48E646E7A791DFF82E7` | 20:05:10 | 同上（**同一份字节**） |
| `neoforge-1.21.1/build/libs/astral_dice-1.2.1+neoforge_1.21.1.jar` | 934880 | `977B1439…` | 20:04:52 | 生产 = 开发（NeoForge 无 reobf） |
| `run/1.21.1/mods/astral_dice-1.2.1+neoforge_1.21.1.jar` | 934880 | `977B1439…` | 20:04:52 | 同一份字节 |
| `D:/.minecraft/versions/1.20.1 模组测试/mods/…` | 979097 | `60F83938…` | — | = `build/libs` ✅ |
| `D:/.minecraft/versions/狐の航空学 Voxy Edition/mods/…` | 934880 | `977B1439…` | — | = `build/libs` ✅ |

**第一个硬结论**：`run/1.20.1/mods` 里的 jar 与 `forge-1.20.1/build/devlibs/` 里的 jar **哈希完全相同**（`F5918EE5…`，961403 B）。即 `pushToDevRun` 复制的是 Gradle `jar` 任务（dev）的产物，而 `pushToGame`/`pushToRootBuild` 复制的是 `reobfJar` 任务（生产）的产物。两个真整合包目标都命中 `reobfJar`，所以**都对**。

偏差 `979097 − 961403 = 17694 B` 全部来自 reobf 与常量池重建（见 §2/§3）。

---

## ② 条目差异前 20

两份 jar 的 ZIP 条目清单**完全同构**：

```
prod entries: 1118   dev entries: 1118
--- only in PROD (0) ---
--- only in DEV  (0) ---
--- same name, different size (185)  ← 同条目名、字节数不同
```

**没有任何新增/缺失条目**，条目名集合逐字相同（266 个 `.class` + 852 个资源）。资源类条目（assets / data / `META-INF/mods.toml` / `MANIFEST.MF`）**逐字节相同**；差异 100% 落在 `.class` 上。

同条目名但字节数不同的前 20 条（按 |Δ| 降序）：

| # | 条目 | prod(SRG) | dev(Mojmap) | Δ |
|---|---|---|---|---|
| 1 | `com/merlinkitsune/astral_dice/combat/DiceCombatEvents.class` | 41485 | 41665 | −180 |
| 2 | `com/merlinkitsune/astral_dice/item/sign/BaseSignItem.class` | 17432 | 17607 | −175 |
| 3 | `com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.class` | 29678 | 29832 | −154 |
| 4 | `com/merlinkitsune/astral_dice/combat/DiceCombatModifiers.class` | 23117 | 23245 | −128 |
| 5 | `com/merlinkitsune/astral_dice/effect/ChargeEffect.class` | 2807 | 2903 | −96 |
| 6 | `com/merlinkitsune/astral_dice/item/dice/NetherStarDiceItem.class` | 5093 | 5176 | −83 |
| 7 | `com/merlinkitsune/astral_dice/item/card/UnwaveringCardItem.class` | 2539 | 2621 | −82 |
| 8 | `com/merlinkitsune/astral_dice/client/ModClientEvents$DamageNumberOverlay.class` | 6648 | 6569 | +79 |
| 9 | `com/merlinkitsune/astral_dice/item/chip/FanBigChipItem.class` | 4699 | 4774 | −75 |
| 10 | `com/merlinkitsune/astral_dice/item/card/BerserkCardItem.class` | 2254 | 2327 | −73 |
| 11 | `com/merlinkitsune/astral_dice/item/sign/PandamanSignItem.class` | 12285 | 12358 | −73 |
| 12 | `com/merlinkitsune/astral_dice/combat/SpellDamageRegistry.class` | 9219 | 9147 | +72 |
| 13 | `com/merlinkitsune/astral_dice/item/card/EffectCardItem.class` | 2466 | 2536 | −70 |
| 14 | `com/merlinkitsune/astral_dice/item/HealingManager.class` | 6669 | 6739 | −70 |
| 15 | `com/merlinkitsune/astral_dice/damage/ModDamageTypes.class` | 2861 | 2929 | −68 |
| 16 | `com/merlinkitsune/astral_dice/item/card/EffectCardPeriod.class` | 12540 | 12605 | −65 |
| 17 | `com/merlinkitsune/astral_dice/item/sign/MosesSignItem.class` | 9127 | 9192 | −65 |
| 18 | `com/merlinkitsune/astral_dice/item/ChargeManager.class` | 2937 | 3002 | −65 |
| 19 | `com/merlinkitsune/astral_dice/item/card/LivingPageItem.class` | 2372 | 2437 | −65 |
| 20 | `com/merlinkitsune/astral_dice/item/chip/CursedSwordChipItem.class` | 7573 | 7633 | −60 |

> 说明：条目**大小差**不是判据（Mojmap 名与 SRG 名长度各异，且 remap 会重建常量池使得部分条目**大小相同但内容不同**——185 条大小不同、81 条大小相同但字节不同，合起来 266 个 class **全部**字节不同）。

`META-INF/MANIFEST.MF` 与 `META-INF/mods.toml` **逐字节相同**（后者 SHA256 = `6572B0FB2B2433D2F98D8A15F74461456B3B06D1E84542999FC272C839848D19`），两者都带 `MixinConfigs: astral_dice.mixins.json`——**排除**「清单属性/元数据不同」这一可能。

---

## ③ reobf 判定与证据

### 判据一：SRG token 计数（全局，一票定音）

对每份 jar 的全部 `.class` 统计 SRG 成员名 token（正则 `\b[fm]_\d+_`）：

| jar | class 数 | 含 `m_/f_` 的 class 数 | SRG token 总数 |
|---|---|---|---|
| `forge-1.20.1/build/libs/…` | 266 | **179** | **1451** |
| `run/1.20.1/mods/…` | 266 | **0** | **0** |
| `neoforge-1.21.1/build/libs/…` | 254 | 0 | 0 |
| `run/1.21.1/mods/…` | 254 | 0 | 0 |

⇒ **`build/libs` 那份是 reobf（SRG/intermediate）产物；`run/1.20.1/mods` 那份是 dev（Mojmap 官方名）产物。**

### 判据二：`javap -p -c` 同类 class 对照

`com/merlinkitsune/astral_dice/command/AstralPartyCommand.class`：

```
prod : 2: invokestatic #109  // Method net/minecraft/commands/Commands.m_82127_:(Ljava/lang/String;)Lcom/.../LiteralArgumentBuilder;
prod : 45: invokestatic #156 // Method net/minecraft/commands/arguments/EntityArgument.m_91470_:()Lnet/.../EntityArgument;

dev  : 2: invokestatic #11   // Method net/minecraft/commands/Commands.literal:(Ljava/lang/String;)Lcom/.../LiteralArgumentBuilder;
dev  : 45: invokestatic #39  // Method net/minecraft/commands/arguments/EntityArgument.players:()Lnet/.../EntityArgument;
```

`Commands.m_82127_` 与 `Commands.literal` 是同一个方法。用本机 SRG 映射表交叉验证：

```
forge-1.20.1/build/moddev/artifacts/intermediateToNamed.srg:
MD: net/minecraft/commands/Commands/m_82127_ (Ljava/lang/String;)Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;
    net/minecraft/commands/Commands/literal (Ljava/lang/String;)Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;
```

⇒ **判定：`build/libs` = reobf 产物（SRG 名，面向生产环境——生产原版类字段就是 SRG 名）；`run/1.20.1/mods` = dev/未 reobf 产物（Mojmap 名，面向 `forgeclientuserdev` dev 运行时）。**

### 判据三：把 prod 的 SRG 名按映射表还原回 Mojmap 后逐类比对常量池

脚本 `temp/jarinv/normalize_diff.py`（读 `intermediateToNamed.srg`，266 类逐一还原比对）：

```
[map] methods=34980 fields=32368
[classes] total=266 ... remap-only=221  OTHERWISE-DIFFERENT=45
```

45 条「仍不同」的样本呈现**单向特征**：`only-in-prod(normalized)` **全部为空**，`only-in-dev` 只列出 `com/mojang/brigadier/Command`、`java/lang/Integer`、`com/merlinkitsune/astral_dice/AstralDiceMod` 之类**常量池条目形态差异**。抽查确认这类名字在 prod 里**以完整描述符形式存在**（例：prod `AstralPartyCommand` 的 CP 第 144 项就是 `()Lcom/mojang/brigadier/Command;`，只是 ASM 重建常量池时**丢掉了独立的 Class 常量**）。

⇒ 残余差异是 **remap 重建常量池的副产物**，不是语义差异。

### 判据四（金标准）：归一化反汇编 + 控制流/操作数等价性逐类比对

脚本 `temp/jarinv/javap_compare.py`：对**全部 266 个字节不同的 class** 跑 `javap -p -c`，归一化（① `#\d+` → `#` 抹掉常量池索引；② SRG 名 → Mojmap 名）后逐行比对：

```
[info] total=266 differing-bytes=266
[result] normalized-disasm identical=244  DIFFERENT=22

!! com/merlinkitsune/astral_dice/AstralDiceMod.class  (prod 247 lines vs dev 247 lines)
   line 133:  prod: 8: ldc_w # // String config_version\s*=\s*(\d+)
              dev : 8: ldc   # // String config_version\s*=\s*(\d+)

!! com/merlinkitsune/astral_dice/client/ModClientEvents$DamageNumberOverlay.class
   line 48:   prod: 97: ifeq 608
              dev : 97: ifeq 605
```

**244/266 在归一化后逐指令完全一致**；剩余 22 条只在 `ldc`↔`ldc_w` 与由此引起的字节偏移平移上不同。

脚本 `temp/jarinv/classify_cfg.py` 做**最严格的一步**：按方法切分 `javap -c`，建立「(方法, 字节偏移) → 指令序号」映射，然后
- 把 `ldc_w` 归一为 `ldc`（同一条指令、同一个操作数，仅常量池索引宽度不同）；
- 把**每一条分支/跳转的绝对字节偏移换算为目标指令序号**；
- 把 `Exception table` 的 `from/to/target` 字节偏移同样换算为指令序号；
- 再逐类比对指令序列与操作数。

```
[classes] total=266
  byte-identical                          : 0
  control-flow & operand equivalent       : 266      ← 全部
  NOT equivalent                          : 0
  total ldc<->ldc_w widenings (benign)    : 173
  unmapped branch targets                 : 0
EXIT=0
```

**⇒ 全部 266 个 class 在「控制流 + 操作数」意义上完全等价**，差异归结为两类纯编码现象：

1. **`ldc` ↔ `ldc_w`（共 173 处）**：ASM 重建常量池后，字符串/Class/float 常量落在索引 ≤255 还是 >255 不同，于是 1 字节索引的 `ldc` 与 2 字节索引的 `ldc_w` 互现。**同一条指令、同一个操作数**（两个方向都出现过，例如 `BaseSignItem` 是 prod 用 `ldc`、dev 用 `ldc_w`；`JasmineSignItem` 的 `float 300.0f` 亦然）。
2. **字节偏移重编号**：每处 `ldc`→`ldc_w` 使该方法后续指令整体 +1 字节，故分支目标偏移（`ifeq 608` vs `ifeq 605`）与 `Exception table` 的 `from/to/target`（如 prod `0 38 39` vs dev `0 37 38`）随之平移——**换算成指令序号后完全一致**（`unmapped branch targets: 0`）。

**⇒ 两份 jar 是同一个程序。除了「成员命名域（SRG ↔ Mojmap）」与「remap 重建常量池导致的指令宽度/偏移重编号」之外，不存在任何代码、资源、元数据差异。**

---

## ④ pushToGame / pushToDevRun 任务链

### 1.20.1（LegacyForge，**双 jar 产物**）

`forge-1.20.1/build.gradle`：

```groovy
// L263-270（设计意图原文，逐字引用）
// 产物来源按环境区分(MDG LegacyForge 双产物):
//   - pushToDevRun(开发 runClient 环境 root run/1.20.1/mods):jar.archiveFile = build/devlibs
//     的未重混淆 jar(Mojmap 名)——forgeclientuserdev 运行时加载 Mojmap 原版类,
//     推 SRG 版反而报 NoSuchFieldError(f_256929_)。
//   - pushToGame(生产/整合包):reobfJar.archiveFile = build/libs 的 SRG 重混淆 jar——
//     生产原版类字段是 SRG 名(含 static final 常量),未重混淆 jar 报 NoSuchFieldError。
def devJarArchiveProvider  = tasks.named('jar').flatMap       { it.archiveFile }   // L269
def prodJarArchiveProvider = tasks.named('reobfJar').flatMap  { it.archiveFile }   // L270
```

| 任务 | 定义行 | `dependsOn` | 输入 provider | 目标目录 | 复制的 jar |
|---|---|---|---|---|---|
| `pushToDevRun` | L272-293 | `jar`（L275） | `devJarArchiveProvider`＝`jar`（L269，L277 取用） | `run/1.20.1/mods`（L255） | **`build/devlibs`（Mojmap，未 reobf）** |
| `pushToGame` | L296-323 | `reobfJar`（L299） | `prodJarArchiveProvider`＝`reobfJar`（L270，L301 取用） | `D:/.minecraft/versions/1.20.1 模组测试/mods`（L256） | **`build/libs`（SRG，reobf）** |
| `pushToRootBuild` | L328-350 | `reobfJar`（L331） | `prodJarArchiveProvider`（L333） | 仓库根 `build/libs`（L326） | **`build/libs`（SRG，reobf）** |

三者均随 `build` 自动触发（L352-358 `build { finalizedBy pushToDevRun / pushToGame / pushToRootBuild }`）。

**因此 `run/1.20.1/mods` 与 `build/libs` 不一致是这套代码的必然结果，而不是某个任务拿错了输入。**

### 1.20.1 的 jar 类任务与依赖关系（问答④）

`.\gradlew :forge-1.20.1:reobfJar --dry-run --offline` 输出的真实任务图：

```
:forge-1.20.1:createMinecraftArtifacts SKIPPED
:forge-1.20.1:compileJava          SKIPPED
:forge-1.20.1:generateModMetadata  SKIPPED
:forge-1.20.1:processResources     SKIPPED
:forge-1.20.1:classes              SKIPPED
:forge-1.20.1:jarJar               SKIPPED
:forge-1.20.1:jar                  SKIPPED
:forge-1.20.1:reobfJar             SKIPPED
BUILD SUCCESSFUL in 662ms
```

| 任务 | 输出目录 | 产物 | 说明 |
|---|---|---|---|
| `jar` | `build/devlibs/` | `astral_dice-1.2.1+forge_1.20.1.jar`（961403 B，Mojmap） | 由 `processResources`+`classes` 打包；MDG LegacyForge 把 dev jar 定向到 `devlibs` |
| `jarJar` | （`jar` 的前置，无嵌套 jar 时直通） | — | `jar` 依赖它 |
| `reobfJar` | `build/libs/` | `astral_dice-1.2.1+forge_1.20.1.jar`（979097 B，SRG） | `RemapJar extends Jar`，**输入＝`jar` 的产物**，做 Mojmap→SRG 重混淆 |

- **`build/libs` 里那份属于 `reobfJar`**（生产/发货产物）。
- **不存在** `shadowJar` / `remapJar`（`remapJar` 是 Fabric Loom 的命名）；本仓 `reobfJar` 即等价角色。`jarJar` 是 Forge 的嵌套 jar 任务，此处无嵌套依赖，不产生独立产物。
- **两者同源**：`reobfJar` 以 `jar` 的输出为输入，故二者出自**同一次 `compileJava`**（`build/classes/.../AstralDiceMod.class` mtime 20:05:09、`build/resources/main/META-INF/mods.toml` 20:05:07，均早于两份 jar 的 20:05:10/11）。**不存在「一份是陈旧产物」的情况。**
- **CI 侧交叉验证发货产物就是 `reobfJar` 的输出**：`.github/workflows/build.yml` L55-57 上传 `neoforge-1.21.1/build/libs/astral_dice-*.jar` 与 **`forge-1.20.1/build/libs/astral_dice-*.jar`**，L111-117 也用同一路径组 `gh release upload`。即**发布给用户的 1.20.1 jar = SRG 重混淆产物**，与两个整合包目标一致。

---

## ⑤ 为什么 1.21.1 一致

`neoforge-1.21.1/build.gradle` 只有**一个** jar 任务，且**三个推送任务全部指向同一个 provider**：

```groovy
// L237-238
def jarArchiveProvider = tasks.named('jar').flatMap { it.archiveFile }
```

| 任务 | 定义行 | `dependsOn` | 输入 |
|---|---|---|---|
| `pushToDevRun` | L240-263 | `jar`（L243） | `jarArchiveProvider`（L245） |
| `pushToGame` | L266-295 | `jar`（L269） | `jarArchiveProvider`（L271） |
| `pushToRootBuild` | L300-324 | `jar`（L303） | `jarArchiveProvider`（L305） |

**根因**：NeoForge 1.21.1 **运行时本身就是 Mojmap 命名**，不存在 reobf/remap 环节——MDG 的 `net.neoforged.moddev` 不注册 `reobfJar` 之类的第二个 jar 任务，`jar` 的输出**直接就是生产产物**。所以：

```
build/libs/…neoforge_1.21.1.jar  ==  run/1.21.1/mods/…jar  ==  整合包 mods/…jar
       934880 B / 977B1439…         934880 B / 977B1439…        934880 B / 977B1439…
```

而 1.20.1 的 LegacyForge **必须**做 Mojmap→SRG 重混淆（Forge 1.20.1 生产环境的原版类字段是 SRG 名），于是**天然存在两个产物**：dev（`build/devlibs`，给 `forgeclientuserdev` 用）与 prod（`build/libs`，发货用）。`pushToDevRun` 与 `pushToGame` 按**运行环境**各取所需——这正是 L263-270 注释写明的事情。

> 换句话说：**1.21.1 一致是因为它只有一种命名域（只有一个 jar）；1.20.1 不一致是因为它有两种命名域（两个 jar，分别服务 dev 与 prod）。这是加载器架构差异的必然投影，不是配置疏漏。**

---

## ⑥ `runClient` 实际加载哪一份

**结论：`runClient` 加载的是 `run/1.20.1/mods/astral_dice-1.2.1+forge_1.20.1.jar`（dev/Mojmap 那份）；`build/classes` 虽然也在 classpath 上，但在 modid 去重中被 jar 抢先，不参与加载。** —— 有**实测运行日志**，不是推断。

### 证据：`run/1.20.1/runclient_launch.log`（上一次 `runClient`，2026-09-15 14:01:11）

两条候选同时被 FML 发现：

```
L91  ModDiscoverer: Found Mod Locators : (mixin-booster-locator…),(mods folder:null),(maven libs:null),
                                        (exploded directory:null),(minecraft:null),(userdev classpath:null)
L95  Considering mod file candidate …\run\1.20.1\mods\astral_dice-1.2.1+forge_1.20.1.jar
L96  Found valid mod file astral_dice-1.2.1+forge_1.20.1.jar with {astral_dice} mods - versions {1.2.1+forge_1.20.1}

L97  CommonLaunchHandler: Got mod coordinates astral_dice%%…\forge-1.20.1\build\classes\java\main;
                                            astral_dice%%…\forge-1.20.1\build\resources\main from env
L98  Found supplied mod coordinates [{astral_dice=[…\build\classes\java\main, …\build\resources\main]}]
L108 Considering mod file candidate …\forge-1.20.1\build\classes\java\main
L109 Found valid mod file main with {astral_dice} mods - versions {1.2.1+forge_1.20.1}
```

**去重裁决（决定性）：**

```
L166 UniqueModListBuilder: Found 2 mods for first modid astral_dice, selecting most recent based on version data
L167 UniqueModListBuilder: Selected file astral_dice-1.2.1+forge_1.20.1.jar for modid astral_dice with version 1.2.1+forge_1.20.1
L228 Considering mod file candidate …\run\1.20.1\mods\astral_dice-1.2.1+forge_1.20.1.jar
L230 Loading mod file …\run\1.20.1\mods\astral_dice-1.2.1+forge_1.20.1.jar with languages [LanguageSpec[languageName=javafml, acceptedVersions=[47,48)]]
```

⇒ **加载的是 jar，不是 `build/classes`。** 这与 `AGENTS.md`「Gradle 构建守护规则」第 7 条描述的**遮蔽（shadowing）**现象完全吻合，并给出了它的机制。

### 机制（为什么是 jar 赢）

`forge-1.20.1/build/moddev/artifacts` → `fmlloader-1.20.1-47.4.10-sources.jar` 内
`net/minecraftforge/fml/loading/UniqueModListBuilder.java:117-125`：

```java
private ModFile selectNewestModInfo(Map.Entry<String, List<ModFile>> fullList) {
    List<ModFile> modInfoList = fullList.getValue();
    if (modInfoList.size() > 1) {
        LOGGER.debug("Found {} mods for first modid {}, selecting most recent based on version data", …);
        modInfoList.sort(Comparator.comparing(this::getVersion).reversed());   // L121
        LOGGER.debug("Selected file {} for modid {} with version {}", modInfoList.get(0).getFileName(), …);
    }
    return modInfoList.get(0);                                                  // L124
}
```

关键点：
- 判据只有**版本号**。本次两份候选版本**完全相同**（都是 `1.2.1+forge_1.20.1`）。
- `List.sort` 是**稳定排序**（TimSort），版本相同时**保持发现顺序**。
- 发现顺序取决于 L91 的 locator 顺序：`mods folder` **排在** `exploded directory` **之前**。

⇒ **`run/<版本>/mods` 里的 jar 只要存在，就永远赢过 `build/classes`**——**这是确定性的，不是偶然**。（这也解释了为什么 L166 对**每一个**同时在 mods 目录与 classpath 上的模组都打印同一句话并选中 `.jar`：kubejs、architectury、jei、curios、patchouli、oculus、embeddium… 全部如此。）

### 1.21.1 同样如此（对称验证）

`run/1.21.1/runclient_launch.log`：

```
L213 UniqueModListBuilder: Found 2 mods for first modid astral_dice, selecting most recent based on version data
L214 UniqueModListBuilder: Selected file astral_dice-1.2.1+neoforge_1.21.1.jar for modid astral_dice with version 1.2.1+neoforge_1.21.1
```

⇒ **两个版本的加载机制完全相同**：都是「jar 遮蔽 `build/classes`」。区别仅在于 1.21.1 的那份 jar 恰好与发货 jar **同字节**，而 1.20.1 的那份是 **dev 命名域的 jar**。

**直接后果（对测试可信度）**：1.20.1 的 `runClient` 跑的是 **`run/1.20.1/mods` 里的 jar**，所以——
- 若只 `compileJava`/`classes` 而**没有重新 `jar`/`build`**，则跑的是**旧代码**（`AGENTS.md` 规则 7 的告警场景）；
- 若正常执行 `gradlew build`（本次就是这样），则 `jar` 与 `reobfJar` 同源同刻产出，测试执行的是**当次源码编译结果**。

---

## ⑦ 结论：是设计行为，还是缺陷？

### 判定：**设计行为（intentional），不是缺陷。**

证据链：

1. **代码里写明了意图**，且**自 monorepo 导入以来从未改动**：`forge-1.20.1/build.gradle:263-270` 用 4 行注释区分 `pushToDevRun`（devlibs / Mojmap）与 `pushToGame`（libs / SRG），并**点明原因**——「forgeclientuserdev 运行时加载 Mojmap 原版类，推 SRG 版反而报 `NoSuchFieldError(f_256929_)`」。
2. **取证**：`git log -S "devlibs" -- forge-1.20.1/build.gradle` → 只有 **`8916aba chore: import multiloader monorepo from astra_dice branch matrix`**。即这段逻辑在导入时就已经是这个样子；`git log -p` 显示后续提交（含 `d90e8c5 build: 默认自动推送整合包`）**只改了触发方式**（把三个推送任务挂到 `build` 的 `finalizedBy`），**没有改过产物来源**。
   ```
   $ git log -S "pushToDevRun" --oneline -- forge-1.20.1/build.gradle
   d90e8c5 build: 默认自动推送整合包
   8916aba chore: import multiloader monorepo from astra_dice branch matrix
   $ git log -S "devlibs" --oneline -- forge-1.20.1/build.gradle
   8916aba chore: import multiloader monorepo from astra_dice branch matrix
   ```
3. **技术必要性**：把 `reobfJar`（SRG）产物推进 `run/1.20.1/mods` 会**直接让 `runClient` 起不来**。dev 运行时（`forgeclientuserdev`，本机 `--launchTarget forgeclientuserdev`）加载的是 **Mojmap 命名的原版类**；SRG 版 jar 引用 `m_/f_` 名，会以 `NoSuchFieldError`/`NoSuchMethodError` 崩溃——这正是注释所写。**因此现状是唯一可工作的配置。**
4. **两个真整合包目标都正确命中 `reobfJar`**，即实际发货字节码无误。

### 但必须写明的一点：**1.20.1 的 `runClient` 测试跑的不是发货字节码**

- 1.20.1 的 B1/B2 游戏内测试，**执行的是 `run/1.20.1/mods` 里的 dev（Mojmap、未 reobf）jar**（§6 实测）。
- 它与发货的 `build/libs`（SRG、reobf）jar **不是同一份字节**，SHA256 不同（`F5918EE5…` vs `60F83938…`）。
- **然而测试结论依然有效**，因为经四重取证（§3：SRG token 1451 vs 0；SRG↔Mojmap 映射交叉验证；常量池归一化；**全部 266 个 class 的归一化反汇编 244 条逐指令一致、其余 22 条仅 `ldc`/`ldc_w` 宽度与分支偏移差**），两份 jar 是**同一个程序**，差别仅在命名域与 remap 的编码副产物。
- **真正的风险不在「命名域」，而在「时间戳 / 陈旧性」**：由于 §6 证明 jar 会**确定性遮蔽** `build/classes`，
  - ✅ 正常 `gradlew build` 后测试 → 测试的就是当次源码（本次情形，两份 jar mtime 20:05:10/11，同源于 20:05:07-09 的 classes/resources）。
  - ⚠️ **只编译不打包**（只 `compileJava`/`classes`，或 `runData` 前忘了移走 jar）就 `runClient` → 测的是**上一轮的旧 jar**，且**不报任何错**。这是 `AGENTS.md` 规则 7 明确警告过的场景，**也是本次「1.20.1 测试可信度」唯一实质风险点**。
  - 缓解：跑游戏内测试前一律以 `gradlew build`（而非 `classes`）为最后一步，使 `jar` 与 `build/classes` 同步刷新。

**一句话**：`run/1.20.1/mods` 放的不是「错 jar」，而是**dev 运行环境唯一正确的那份 jar**；发货链路的 `build/libs` 与两个整合包目标也都正确。**没有改任何一个字节的必要。**

---

## ⑧ 建议的最小改法与利弊

按任务给定的处置授权：「若判为有意设计，**不要改**，只给方案与利弊」。

### 建议：**不改**（`build.gradle` 未被修改，无 commit）

理由见 §7 第 3 点：改成 `reobfJar` 会让 `runClient` 直接崩溃；这不是「更正确」，而是「更坏」。

### 若确实想消除「两份 jar 不同」的困惑，可选方案（按代价升序，均**不推荐**）

| 方案 | 做法 | 利 | 弊 |
|---|---|---|---|
| A. **仅加文档/日志**（零风险，推荐度最高） | 在 `pushToDevRun` 的 `logger.lifecycle` 里补一句「此 jar 为 dev/Mojmap 命名，与 `build/libs` 的 reobf 发货 jar **不同字节但同程序**」 | 消除「哈希不一致 = 有问题」的误判，不动任何功能 | 无 |
| B. 在 `run/1.20.1/mods` 旁附一份 `.sha256` 或改名加 `-dev` 后缀 | 复制时改名为 `…+forge_1.20.1-dev.jar` | 一眼可辨 | 需同步改 `staleJarMatcher`（L259-261）与 `AGENTS.md` 规则 7 的正则；改名可能影响 `mods.toml` 之外的加载器判定（一般无影响，但有回归面） |
| C. 让 `pushToDevRun` 改用 `reobfJar` | 一行改动（`prodJarArchiveProvider`） | 三处哈希一致 | ❌ **`runClient` 直接 `NoSuchFieldError` 崩溃**（§7 第 3 点 + L265-266 注释）。**禁止** |
| D. 取消 `run/1.20.1/mods` 里的 jar，只靠 `build/classes` | 删除 `pushToDevRun` | 测试必为当次源码 | ❌ 与 `AGENTS.md` 规则 3/7 的既定流程冲突；且 `runClient` 会失去 jar 里的 `META-INF/mods.toml`/`MixinConfigs` 通道（dev 下靠 `--mixin.config` 兜底，但 `mods folder` 被打空的语义变化需另测）；**牵动测试流程，不属「小而安全」** |
| E. 改 1.21.1 侧以「对齐」 | — | — | ❌ 1.21.1 **本来就是对的**（NeoForge 无 reobf）；改它只会引入缺陷 |

**唯一值得做的**是方案 A（纯注释），但它属「改善可读性」而非「修缺陷」；鉴于本任务禁止改 `AGENTS.md`、且 `build.gradle` 的注释**已经写明了这件事**（L263-270），**本次不做任何改动**。

---

## 附：本次取证用到的命令与脚本

| 用途 | 位置 |
|---|---|
| 条目清单差集 | `System.IO.Compression.ZipFile` + PowerShell（见 §2） |
| SRG token 计数 | Python + `zipfile`（正则 `\b[fm]_\d+_`，Latin-1 读取 class 字节） |
| SRG→Mojmap 归一化比对 | `temp/jarinv/normalize_diff.py`（映射源 `forge-1.20.1/build/moddev/artifacts/intermediateToNamed.srg`，34980 方法 / 32368 字段） |
| 归一化反汇编逐指令比对 | `temp/jarinv/javap_compare.py`（`javap -p -c` + `#\d+`→`#` + SRG→Mojmap） |
| 控制流/操作数等价性判定（金标准） | `temp/jarinv/classify_cfg.py`（按方法切分 + 分支目标与异常表偏移 → 指令序号 + `ldc_w`→`ldc`） |
| 任务图 | `.\gradlew :forge-1.20.1:reobfJar --dry-run --offline` |
| 运行时加载来源 | `run/1.20.1/runclient_launch.log`、`run/1.21.1/runclient_launch.log` |
| FML 去重算法源码 | `fmlloader-1.20.1-47.4.10-sources.jar` → `net/minecraftforge/fml/loading/UniqueModListBuilder.java` |

> `temp/` 在 `.gitignore` 内（`.gitignore:42,60`），`docs/` 亦在 `.gitignore` 内（`.gitignore:58`）。
