# 交接正本 — 1.5.0-pre.3（multi-main）

## 0 速览（一屏）

1. 分支 `multi-main`，功能末次提交 `3e200a9b`（本文档自身是其后一条 `docs(handover)` 提交，`git log -1` 应显示它），开工时 `git status --short -uall` 为空（干净）。
2. 未推送提交 **16** 个（`git rev-list --count origin/multi-main..HEAD`，本文档提交前实测；本文档提交后为 **17**，含本条），**无** `1.5.0-pre.3` tag（`git tag --list '*1.5.0-pre.3*'` 为空）。
3. 四线 `mod_version` 均为 `1.5.0-pre.3+<加载器后缀>`；`starengine_version=1.1.0-pre.2`（四线一致）。
4. jar 内硬前置：`modId="starengine"` / `versionRange="[1.1.0-pre.2,2.0.0)"`，`META-INF/jarjar/` 计数 **0**（不再内嵌库）。
5. 可交付范围：**只发 P0**（`neoforge-1.21.1` + `forge-1.20.1`）。P1 两线版本号同为 pre.3，但内容不含 1.5.0。
6. P0 双 jar 已定稿并投放两个整合包（sha256 与 `build/libs` 一致），库 jar 同名在位。
7. 守门 8 跑 7 绿；唯一红 = `verify_probe_class_refs`（既有基线，见 §5-1）。2026-10-22 已修该门三处自身缺陷，
   红点从 6 处降到 **1 处**（26.1.2 产物陈旧，P1 待重建）。
8. 实机：1.20.1 本批 6/6 PASS、1.21.1 5 条 FIXBATCH 判据全命中、末条 49/49 PASS。
9. 最大风险：**创造模式扣卡修复（1.20.1 三处）尚未实机复验**；且 `run/<线>/mods` 里的 jar 与 `build/libs` 不一致（§6-1）。

## 1 环境与路径

- 仓库（唯一工作树）：`F:\MCProject\astral_dice_multiloader`，分支 `multi-main`；git 一律用 **git bash 的 `git`** ——
  写全 `git -C F:\MCProject\astral_dice_multiloader <子命令>`，或先 `cd` 进工作树；**不需要** `--git-dir=`，**不需要** `cmd.exe` 中转（原「WSL 报 `not a git repository`」问题不存在，2026-10-22 起失效）。
- 四子项目同树：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`。
- 编译（**Windows 原生**，原「WSL 只编译」口径 2026-10-22 起作废）：在工作树根执行 `gradlew.bat :<线>:classes|jar`；
  JDK = `C:\Program Files\Zulu\zulu-21`（21.0.12），`forge-1.20.1` 的 `toolchain 17` 由 Gradle 从 `%USERPROFILE%\.gradle\jdks` 取
  （已缓存 Adoptium 17.0.20），**不要手改**。
- 实机与脚本：一律 `pwsh -NoProfile -File <绝对路径>.ps1`（**禁** `bash` 跑测试、**禁** `.sh`、**禁** Windows PowerShell 5.1、
  **禁** `wsl.exe` 与 `/mnt/...` 路径）；bash 只承担 git。
- 测试台：`scripts/test/mt.ps1`（`--version <线> --phase env|run|stop`）。
- 整合包投放：
  - neo → `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`
  - forge → `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods`
- 库 jar 与之同目录：`starengine-neoforge-1.21.1-1.1.0-pre.2.jar`（98,733 B）/ `starengine-forge-1.20.1-1.1.0-pre.2.jar`（103,604 B）。
- 委派：DSH 侧派活走 `subagent` / teammate（明文 brief、禁轮询、结果只从返回值或落盘文件验收）；
  Codex 侧的委派服务在 `C:\Users\xmace\.codex\mcp\delegation\server.mjs`，日志 `…\delegation\server.log`（仅 Codex 用）。
- 环境注入：**无** `/etc/profile.d` 注入脚本（`99-wsl-proxy.sh` / `99-wsl-github.sh`）、**无** `wsl-env-restore.sh`、**无** `/opt/zulu`+`use-jdk`
  （2026-10-22 起全部失效）；Windows 侧代理走系统环境变量（Clash `http://127.0.0.1:7897`），仓库级 `http.proxy` / `https.proxy` 已配好。

## 2 版本与坐标

| 项 | 实测值 | 证据 |
|---|---|---|
| neo `mod_version` | `1.5.0-pre.3+neoforge_1.21.1` | `neoforge-1.21.1/gradle.properties:37` |
| forge `mod_version` | `1.5.0-pre.3+forge_1.20.1` | `forge-1.20.1/gradle.properties:41` |
| 26.1.2 `mod_version` | `1.5.0-pre.3+neoforge_26.1.2` | `neoforge-26.1.2/gradle.properties:54` |
| fabric `mod_version` | `1.5.0-pre.3+fabric_1.20.1` | `fabric-1.20.1/gradle.properties:29` |
| `starengine_version` | `1.1.0-pre.2`（四线一致） | `fabric:40` / `forge:114` / `neo:43` 起的段落 |
| jar 内依赖区间 | `[1.1.0-pre.2,2.0.0)`，`type=required`（neo）/ `mandatory=true`（forge） | `META-INF/neoforge.mods.toml:141-144` / `META-INF/mods.toml:154-158` |
| 内嵌库 | `META-INF/jarjar/` 计数 **0**（两线） | `unzip -l <jar> \| grep -c 'META-INF/jarjar/'` |

P0 产物（`build/libs`，2026-10-22 14:25）：

- neo `astral_dice-1.5.0-pre.3+neoforge_1.21.1.jar` — **2,555,453 B** — `09980b2e0927f99a9086084f7b426d09c6ab038fdc43a5d7e95107b13e849a02`
- forge `astral_dice-1.5.0-pre.3+forge_1.20.1.jar` — **2,601,019 B** — `70b347ba2367e13823a66789c22b05172f3a8ca8d3228060049d29f3c9695507`
- 两个整合包内同名 jar 的 sha256 与上表**逐字一致**（实测）。
- `run/1.21.1/mods` 副本 `4e5b498b…`（2,555,455 B，10-22 00:22）、`run/1.20.1/mods` 副本 `07a2e2db…`（2,571,445 B = `build/devlibs` named jar）—— **与 build/libs 不一致**，见 §6-1。

## 3 本批批次表（一行一批：提交号 / 主题 / 关键落点）

| 提交 | 主题 | 关键落点 |
|---|---|---|
| `d11690d5` | release(main): 1.5.0-pre.2 → pre.3 | 四线 `mod_version` 升 pre.3 |
| `44a8fde3` | balance(batch-A) | 修女/多萝西配方、双立牌降稀有、摩托头盔数值 |
| `fe4aa05f` | feat(balance+ui) | 路障/拒止 20:00 寿命、电击手套含主目标、JEI 筹码商店、筹码无边框图标 |
| `9cf48dc6` | feat(fx+fix) | 激光/轨道炮改客户端光线渲染、转账音效、青之诅咒豁免 |
| `ab360c70` | fix(card) | 手牌抹杀只丢 1 张 |
| `5c80ede9` | feat(sign+event) | 强化拒止充能计入放置者、拉怪逐 tick 收敛、全员事件 1 秒硬间隔 |
| `a924dfee` | fix(meta) | StarEngine 改硬前置（required/mandatory + 版本下限） |
| `b2887776` | fix(shop) | 买卡优先并入同类栈，不另起一格 |
| `f2054fa5` | refactor(chip) | 额外电池冷却折算限定为立牌主动技能 |
| `0938a60d` | feat(scope+shop+meta) | 电池折算限立牌主动冷却、商店并栈收口、抽奖盒投入冷却、硬前置补强 |
| `36d3c43c` | test(cases) | 修正筹码商店用例口径 + 新增 5 条批次行为用例（两线实机全绿） |
| `3e200a9b` | fix(card+docs) | 创造模式两线扣卡对齐、CHANGELOG 库版本订正、补 `docs/compat-1.5.0-porting.md` |

## 4 实机读数（原文摘录）

### 4.1 MC 1.20.1（本轮，forge 线）

用例：`FIXBATCH-EVENT-GATE` / `FIXBATCH-REPULSION-ENDER` / `FIXBATCH-REPULSION-CHARGE` /
`FIXBATCH-GLOVE-MAINTARGET` / `FIXBATCH-SHOP-STACK-BLANK` + `CHIP-LOGIC` 回归 ⇒ **6/6 PASS**。

关键读数原文：

```
G4Z_GATE0:a1=1:a2=0:remain2=38:a3=1:t3=88
END_WATCH:max=1.19:samples=200:jumps=0
cg/charge 2→4→5→5
G1_GREAD:dmg=6,2
CARDSLOT:slots=1:count=3     blank_insert denied:remain=2400
```

### 4.2 MC 1.21.1（本轮，neo 线）

5 条 FIXBATCH 判据全命中：`G4A_TICKET2:after1=3:after2=3`、`END_WATCH:jumps=0`、`charge 2→4→5`、
`equip=1:cd=1:charge=2:dmg=6,2`、`slots=1:count=3`；末条 **49/49 PASS**。

### 4.3 创造模式扣卡（改前实测）

- 1.21.1 创造：扣 **0** 张（`live.consume(1, player)` 按 instabuild 豁免，`BaseEffectCardItem:338`/`:423`、`CardChoiceMenu:130`）。
- 1.20.1 创造：扣 **1** 张（裸 `shrink`）⇒ 已改三处为 `if (!player.isCreative())`。

## 5 遗留与欠账

### Blocker

（无）P0 双 jar 与整合包已就位，判据见 §2。

### Major

1. **`verify_probe_class_refs` 6 处红点已定性（2026-10-22），守门自身三处缺陷已修，只剩 1 条产物陈旧项**：
   原 6 处**无一是源码真缺口**，逐条取证 ——
   - `mezz.jei.common.Internal`：真类在，JEI 走 **Curse Maven**（`neoforge-1.21.1/build.gradle:187`
     `curse.maven:jei-238222:8512040` / `forge-1.20.1/build.gradle:272` `curse.maven:jei-238222:8778011`），
     缓存 jar 内含 `mezz/jei/common/Internal.class`；脚本原只解析 `maven.modrinth` ⇒ 盲区。
   - `org.lwjgl.glfw.GLFW`：真类在（gradle 缓存 `lwjgl-glfw-3.3.x.jar`），LWJGL 由加载器/启动器运行期提供、不进 `build.gradle`。
   - `net.minecraftforge.registries.ForgeRegistries`：**被判红的 2 处**都在 `kubejs/1.21.1/server_scripts/astral_bugfix_probe.js:16817`/`:16833`
     的 **JSDoc 反例说明**里（该线跑 NeoForge，本无此类）⇒ 正则把注释当真引用 = 假红。
     （另：`kubejs/1.20.1/server_scripts/astral_bugfix_probe.js:2789` 有**真代码** `Java.loadClass` 同名类，1.20.1 本来就是 Forge、可解析，不在红点内。）
   - `client.DamageNumberStore`：类在源码（三条线都有；该文件最早由 `46986687`（2026-09-26）加入，10-20 的 `15d5ff25` 只是最近一次触及），
     红因 **26.1.2 产物与源码不同源**：`neoforge-26.1.2/build/libs` 最新仍是 `astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`（2026-10-05 11:55），
     该 jar 内 `DamageNumberStore` 条目 **0**（30 个 `client/*` 类可正常枚举）⇒ P1 待重建，非代码缺失。
   **守门修正**（`scripts/verify/verify_probe_class_refs.ps1`）：① 新增 `$script:CurseRe` 并解析
   `~/.gradle/caches/modules-2/files-2.1/curse.maven/<group>/<fileId>/**/*.jar`；② 白名单加 `org.lwjgl.`；
   ③ 新增 `Remove-JsComments`（**单趟、字符串感知**扫描：字符串字面量原样保留 → `/*…*/` 换成等量换行保行号 → `//` 丢到行尾；
    并对 `build.gradle` 也先剥注释，避免把**被注释掉的依赖**当成未解析）；④ 依赖解析补 `curse.maven` 与 `org.lwjgl.` 之后
    该门对 1.20.1 的 `maven.modrinth:immediatelyfast:NJ17fqEK` 误报也消失（该坐标在 `forge-1.20.1/build.gradle:325` 本是注释行）。
   **修正后实跑**：1.21.1 = 21 jar / 34,081 条目 / **150 处** / **不可解析 0**；1.20.1 = 21 jar / 31,270 / **142 处** / **0**；
   26.1.2 = 19 jar / 46,346 / **108 处** / **1**（仅 `DamageNumberStore`）⇒ 末行仍 `RESULT: FAIL（1 处）`。
   （处数比修前多 2/1/1：旧版剥注释实现会**误删真代码**，被删掉的 `Java.loadClass` 不参与判据。）
   ⚠️ 该脚本判 FAIL 时 **`exit 1`**（2026-10-22 实测，`scripts/verify/verify_probe_class_refs.ps1:527-529`），判据以末行 `RESULT` 为准。
2. **创造模式扣卡修复未实机复验**：改动已落盘（`forge-1.20.1/.../item/card/BaseEffectCardItem.java:337`、
   `:423`，`forge-1.20.1/.../screen/CardChoiceMenu.java:134`），但 1.20.1 创造出牌仍待实机确认扣 0 张。
3. **P1 两线整体欠账**（26.1.2 / fabric 均 0 命中，1.21.1 为基准）：JEI 筹码（`compat/jei` 9 文件）、
   `BeamFx`（4）、`GlobalEventGate`（3）、`RepulsionEntity`（10）、`CardShopMenu#planPlacement`（1）、
   `EventDrawBlock`（6）。⇒ 1.5.0 正式版前不验证、不移植（用户裁决）；映射表见 `docs/compat-1.5.0-porting.md`。
4. **提交未推 + 无 tag**：`origin/multi-main..HEAD` = 16，`git tag --list '*1.5.0-pre.3*'` 为空。按用户口径默认不 push。

### Minor

0. **WSL 口径已全面失效（2026-10-22，本轮已完成）**：`AGENTS.md`「回滚裁决」1–6 条改写为 Windows 原生口径（含第 7 条收尾关闭游戏），
   `docs/agent/AGENTS-full.md` 末尾新增〈WSL 口径失效对照〉（5 行口径表）+ 就地标注 L2861 / 第 37 条 ⑥ 失效；
   本文档 §1 / §7 改 Windows 口径（`F:\` 路径、`gradlew.bat`、`pwsh`、PowerShell 判据命令）。
   判据：`python docs/agent/verify-split.py` ⇒ 四项全 `[OK]`（快照 3,522 行 / 红线区 11,520 字符逐字一致 / 索引覆盖 L121–L3522 / AGENTS.md 21,874 字符）。

5. **立牌冷却口径：9 个「起冷却」子类已补 `setSignActiveMaxCooldown`（2026-10-22 完成，两线对等）**。
   补齐清单（`item/sign/`，四条线里 P0 两条同改）：`BonnieSignItem`、`DorothySignItem`、`HaiqingSignItem`、
   `JillSignItem`、`MegasSignItem`、`MosesSignItem`、`RenSignItem`、`TeruSignItem`、`ZhaoSignItem`
   （每个文件 +2 行：一行注释 + 一行 `setSignActiveMaxCooldown(player, (long) (<与 CooldownEnd 同一表达式>))`）。
   ⚠️ **不是 11 个**：原清单里的 `JasmineSignItem:121` 与 `KomachiSignItem:150` 只**缩短已有冷却**
   （`setSignActiveCooldownEnd(now + 剩余×比例)`），它们**是 max 的消费者而非生产者**（Jasmine 还读 `maxCooldown`
   算减半量）⇒ 在那里写 max 会把基准改错，**故意不改**。
   判据：`:neoforge-1.21.1:classes` + `:forge-1.20.1:classes` 均 BUILD SUCCESSFUL；两线各 9 个立牌的匿名内部类
   （`BonnieSignItem$1.class` 等）字节码内含 `setSignActiveMaxCooldown` 引用（外层类不含，调用在 action 内部类里）；
   `git diff` 中 **18 个 java 文件各 +2 行**（整份 diff 是 23 文件：18 java + 4 文档 + 1 脚本）。
6. **26.1.2 线与测试环境都停在旧 jar**：`run/26.1.2/mods/astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`（10-06），
   且 `neoforge-26.1.2/build/libs` 最新也只到 `astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`（2026-10-05 11:55）
   ⇒ 该线守门第 4 条红点（`client.DamageNumberStore`）就是这个陈旧产物造成的；P1 不重建（用户裁决），重建后自动转绿。
7. **AutoJS 两个本地补丁未上游化**：根级 `pack.mcmeta`（`pack_format 15`）+ `status` 反射兜底。
8. **`testworld` 会被 `--purge-saves` 删除**：现存仅 `run/1.21.1/saves/testworld`；下轮实机前必须先 `--phase env`。
9. **`run/1.21.1/mods` 未装 JEI**：该目录实测 **13 个 jar / 14 个条目**（architectury / AstralDice / AutoJS×2 / collective /
   ferritecore / ImmediatelyFast / iris / kubejs / modernfix / carpet / rhino / sodium / superflat…），**没有** JEI 与 LWJGL；
   这正是脚本必须再补 gradle/Maven 依赖 classpath 的原因（§5-1）。

10. **源码领先于产物（2026-10-22 立牌冷却补丁）**：`build\libs` 的两份 pre.3 jar 是 14:25 构建，**不含**本轮的
    `setSignActiveMaxCooldown` 补丁；`run/<线>/mods` 与两个整合包同源同理。⇒ 若要实机验立牌冷却或对外交付，
    必须先 `gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar` 重建、再投放并**重录 §2 的 sha256**（现值会变）。
    （创造模式扣卡修复在 `14:25` 之前就已进 jar，故待办 #1 的实机不受此影响。）
11. **代码审查工具链已换插件（2026-10-22 用户裁决，本轮已完成）**：旧 DSH `code_review_*` 六件套已不在工具面上，
    现行 = 插件 `@michengai/dsh-code-review`（v1.0.1）的单一工具 `dsh_code_review({ input })`：`input` 空 ⇒ 原生范围选择，
    非空 ⇒ **整段原样**作为自定义审查要求（不解析 CLI 选项，范围须写自然语言）；报告以 GFM 回本会话，`/review-status` 查进度、`/review-cancel` 取消。
    同步位置：`AGENTS.md` 红线区〈二次验证规范〉+〈子代理与测试环境硬规则〉第 4/5/7 条、契约 §C 第 6 条、索引末行；
    `docs/agent/AGENTS-full.md`（红线区同文 + 新增〈代码审查工具链更新〉节 + §5-1/§5-2 规则行）；`docs/agent/AGENT-SPLIT.md:19`；
    `docs/handover-new-session-context.md:57`/`:85`；`scripts/test/TESTING-SPEC.md` 附录 A 续 55；本文档 §6 坑 9。
    判据：`python docs/agent/verify-split.py` 四项 `[OK]`；`grep -c 'code_review_'` 在 AGENTS.md 与 AGENTS-full 红线区 = 0。
    复核（`dsh_code_review` 首跑 `fb01cfcf-ffce-4b0c-80a5-feb1c4016ef0`，patch is correct）提 4 条文档精度问题，**已全部就地修**：① AGENTS-full 新节「历史条目读法」清单误列第 37 条、漏第 33/38/39 条 ⇒ 实测命中为第 25 / 27 / 30 / 33 / 38 / 39 / 41 条（`git grep -n 'code_review' -- docs/agent/AGENTS-full.md` 对回条目号）；② 新节「本文档 §6 坑 9」指向 AGENTS-full 内不存在的章节 ⇒ 改为 `docs/handover-1.5.0-pre.3-main.md` §6 坑 9；③ 新节未写报告落盘位置 ⇒ 表内补 `reportDirectory`（默认 `$DSH_HOME/code-review` 即 `C:\Users\xmace\.dsh\code-review`，文件名 `sha256(会话).json`，`lib/journal.js` 临时文件 + rename 原子写）与「`input` 为空需宿主交互问答器，非交互环境必须给非空 input」；④ `scripts/test/TESTING-SPEC.md:1524` 自标「现行」却仍用 `code_review` 且写作「由主 Agent 亲自编写」 ⇒ 改 `dsh_code_review` 复核并订正为「派子代理编写」。改完复跑 verify-split 四项 `[OK]`，full 行数不变（3,541），索引末行无需再动。
## 6 坑（踩过的，后续别重踩）

1. **`run/<线>/mods` 的 jar ≠ `build/libs`**：neo 是 00:22 的旧构建（同名、2 B 差异），forge 是 `build/devlibs`
   的 named jar（dev 必需，reobf 的 SRG 调用在 dev 会抛 `NoSuchMethodError`，且 `Error` 不被 MCP 的
   `catch(Exception)` 兜住）。⇒ 实机前必须重新投放，别拿 `ls` 到同名就当成新构建。
2. **`--purge-saves` 会删 `testworld`** ⇒ 下轮实机前先 `pwsh -NoProfile -File scripts/test/mt.ps1 --version <线> --phase env`。
3. **1.20.1 dev 必须用 `build/devlibs` 的 named jar**；整合包仍用 reobf jar（两者不可互换）。
4. **AutoJS 已按上游 `7138d72` 重编**（包根 `cc.sighs` → `com.sighs`）；仓库内 Maven 坐标仍是
   `cc.sighs.autojs:*`（groupId），别把 groupId 当成包根去改。
5. **StarEngine 不再内嵌**：漏装库 jar 会以 `Missing or unsupported mandatory dependencies` 直接启动失败；
   区间上界固定 `2.0.0`，且**不能**写成 `[1.1.0,2.0.0)`（Maven 把 `pre` 排在正式版之后）。
6. **抽奖盒「投入空白筹码」只清购买冷却**（无刷新机制），与筹码商店「刷新批次 +1」语义不同且**有意**（用户裁决）。
7. **探针 `Java.loadClass` 的类被搬走 ⇒ 该线所有 kubejs 断言全红**（报「KubeJS 有错误」，极易误判为模组 bug）。
8. **`verify_probe_class_refs` 的假红有三个来源**（2026-10-22 全部实测）：① JS **注释**里的类名被当引用；
   ② 依赖只写 `curse.maven` 而脚本只解析 `maven.modrinth`；③ 加载器运行期提供的库（LWJGL）不在 `build.gradle`。
   修完仍剩红点时，先查**该线产物是否陈旧**（jar 早于源码 `mtime`），别急着往源码里补类。
9. **旧 `code_review_*` 接口已作废（2026-10-22 用户裁决）**：`code_review_execute` 撞 cell 600s 墙钟、`preview` 有时效、
   `code_review_start` 后还会卡在 `protocol-correction` —— **一律不要再调用**。现行 = 插件 `@michengai/dsh-code-review` 的
   `dsh_code_review({ input })`（`input` 空 ⇒ 原生范围选择；非空 ⇒ 整段原样作为审查要求，范围要写自然语言）。

## 7 关键路径（命令与文件）

```powershell
# 现状（git 一律 git bash 的 git；-C 显式指定工作树）
git -C F:\MCProject\astral_dice_multiloader status --short -uall
git -C F:\MCProject\astral_dice_multiloader rev-parse --short HEAD
git -C F:\MCProject\astral_dice_multiloader rev-list --count origin/multi-main..HEAD

# 版本 / 依赖
Get-ChildItem -Recurse -Filter gradle.properties | Select-String -Pattern 'mod_version'
tar -xOf 'neoforge-1.21.1\build\libs\astral_dice-1.5.0-pre.3+neoforge_1.21.1.jar' 'META-INF/neoforge.mods.toml' | Select-String -Pattern 'modId="starengine"' -Context 0,4
(tar -tf <jar> | Select-String -Pattern 'META-INF/jarjar/').Count

# 产物一致性（三处：build/libs / run 副本 / 整合包）
Get-FileHash -Algorithm SHA256 'neoforge-1.21.1\build\libs\astral_dice-1.5.0-pre.3+neoforge_1.21.1.jar',
                            'forge-1.20.1\build\libs\astral_dice-1.5.0-pre.3+forge_1.20.1.jar' | Format-List
Get-ChildItem 'D:\.minecraft\versions\狐の航空学 Voxy Edition\mods' | Where-Object Name -match 'astral_dice|starengine' | Select-Object Name,Length,LastWriteTime
Get-ChildItem 'D:\.minecraft\versions\1.20.1-Forge 模组测试\mods'     | Where-Object Name -match 'astral_dice|starengine' | Select-Object Name,Length,LastWriteTime

# P1 缺口
foreach ($f in 'BeamFx','GlobalEventGate','RepulsionEntity','EventDrawBlock','planPlacement') {
  "$f = " + (Get-ChildItem neoforge-26.1.2/src,fabric-1.20.1/src -Recurse -File | Select-String -Pattern $f -List | Measure-Object).Count }

# 立牌冷却口径
Get-ChildItem neoforge-1.21.1/src/main/java -Recurse -Filter *SignItem.java | Select-String -Pattern 'setSignActiveCooldownEnd|setSignActiveMaxCooldown'

# 收尾
pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version <线> --phase stop --force
pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version <线> --phase stop --purge-saves
python F:\MCProject\astral_dice_multiloader\docs\agent\verify-split.py
```

关键文件：`docs/compat-1.5.0-porting.md`（P1 映射）、`docs/compat-1.20.1-forge.md`、
`scripts/test/TESTING-SPEC.md`、`scripts/test/cases/FIXBATCH-*.json`、
`scripts/verify/verify_probe_class_refs.ps1`、`docs/audit/1.3.2-release-audit-2026-09-27.md:50`。
