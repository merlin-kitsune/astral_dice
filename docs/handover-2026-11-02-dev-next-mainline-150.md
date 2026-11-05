# multi-dev-next 线交接：主线 1.5.0 正式版之后（2026-11-02）

> **一句话**：dev 线（`26.4-SNAPSHOT.5`）的基线停在主线 **1.5.0-rc.7 时期**（合并点 `06fef75a`）；主线已于 2026-11-02 发布 **1.5.0 正式版**（tag `1.5.0` = `6fe38138`）。本文件给出「哪 36 个提交待吸收、吸收时要守住什么、吸收后怎么验」。
> **位置**：`F:/MCProject/astral_dice_multiloader-next` = 主仓的 **git worktree**（分支 `multi-dev-next`），不是独立 clone；`.git` 用**相对路径** gitdir（Windows / Linux 双端可解析），细节见 `docs/handover-2026-10-31-dev-next-linux-sync.md（已删除 2026-11-05）` §2。
> **与主线文档的关系**：`docs/handover-1.5.0-main.md`、`docs/handover-new-session-context.md` 描述的是**主线**（`multi-main` / 1.5.0）；其中版本号、库引脚、发布节奏三块**对本线不成立**，一律以本文件与 dev 线自己的 changelog 为准。

## 0 接手第一步

1. 读根 `AGENTS.md` 认领宿主：Windows 读 `docs/agent/AGENTS-dsh.md`，原生 Linux 读 `docs/agent/AGENTS-dsh-linux.md`（后者是本线当前正本）。
2. 读本文件 → `docs/handover-2026-10-31-dev-next-linux-sync.md（已删除 2026-11-05）`（worktree 指针、Linux 测试台、权限坑）。
3. **实读核对**（本文所有数字都是 2026-11-02 13:2x 的快照，随时会漂）：

```bash
git -C F:/MCProject/astral_dice_multiloader-next rev-parse HEAD
git -C F:/MCProject/astral_dice_multiloader-next status --short -uall
git -C F:/MCProject/astral_dice_multiloader-next rev-list --left-right --count origin/multi-main...HEAD
```

## 1 实读快照（2026-11-02 13:2x）

| 项 | dev-next | 主线 multi-main |
|---|---|---|
| 工作树 | `F:/MCProject/astral_dice_multiloader-next` | `F:/MCProject/astral_dice_multiloader` |
| 分支 | `multi-dev-next` | `multi-main` |
| HEAD | **`7efefac5`** | **`6fe38138`**（= tag `1.5.0`） |
| 版本 | 四线 `26.4-SNAPSHOT.5+<line>` | 四线 `1.5.0+<line>` |
| StarEngine | `26.4-SNAPSHOT.1`（fabric 另有 `starengine_version_range=>=26.4-SNAPSHOT.1`） | `1.1.0`，**外部前置**（区间 `[1.1.0,2.0.0)` / `>=1.1.0 <2.0.0`） |
| 工作树状态 | 干净，未跟踪只有：`models/astral_anvil.bbmodel`、`models/sykestentacle/`（他人资产，勿动）**+ 本文件本身** | 大量**他人**未提交改动（`docs/agent/*` 等），勿 add / commit |
| 相对位置 | 领先主线 **8** 提交 / 落后主线 **36** 提交 | — |
| 合并来源 | `85987213` 的第二个父 = `06fef75a`（rc.7 线） | — |

## 2 dev-next 独有的 8 个提交（反同步时**不得丢**）

| 提交 | 内容 |
|---|---|
| `56208923` | fast-forward 到 rc.4 内容 + 定下 dev 版本方案 `<year>.<quarter>-SNAPSHOT.<n>` |
| `ba0ff2aa` | merge：反同步 rc.5 线（38 提交） |
| `e0e89ec3` | bump 到 `26.4-SNAPSHOT.2` |
| `9be228f6` | **mob 侧骰战参与者与特殊骰能力注册接口**（四线） |
| `a5291f2e` | **GeckoLib 硬前置 + 动画实体 `astral_dice:sykestentacle`**（四线，`26.4-SNAPSHOT.4`） |
| `85987213` | merge：反同步 rc.7 线（18 提交，**99 文件 / +28,151 / −18,914**，22 处冲突人工处置；数字引自该提交 message） |
| `7b7356c2` | docs：TUN 直连与属主修复口径 |
| `7efefac5` | docs：交接 §8 记录上面两个合并 |

## 3 主线待吸收的 36 个提交（按主题分组）

**A. 版本与文本（本线的「相同改动」也要再验一遍）**
- `ae4c5187` 四线 `mod_version=1.5.0`（去测试后缀）。
- `054b1a4e` **改名**：`试剂-深渊` → `深渊试剂`，同时把立牌技能名从 Java 字面量搬进 lang 键（`tooltip.astral_dice.sign.<id>.active|passive`）。
- `e7ee78a9` Java 注释跟随新名。

**B. 四线与 26.1.2 收尾**
- `c47ad8a3` **fabric-1.20.1 第四条线落地**（移植 / compat / datagen / assets）。本线 fabric 是 rc.7 之前的旧快照：`386` 个 java 文件 vs 主线 `525`。
- `12de194e` 26.1.2 事件音效、Balm 通路点事件、补回两张蛟龙战斗牌。
- `8c905578` 立牌半透明渲染（n21 / forge 两线）。
- `fd990c15` + `9192a0bc` + `c3757626` + `31103af8` + `e9504696` + `e2a5ebf4` 等 26.1.2 补齐批（本线 n26 `280` java 文件 vs 主线 `426`）。

**C. 外置前置 StarEngine（改动方向与本线相反，最容易合错）**
- `d1ebd5ba` 把 **n26 / fabric 两线**的 StarEngine 从「内嵌（jarJar / include）」改成「**外部前置**」（n21 / forge 两线本就外置），并补版本下界：n26 `neoforge.mods.toml` `versionRange="[1.1.0,2.0.0)"`，fabric `fabric.mod.json` `starengine: ">=1.1.0 <2.0.0"`。
- 本线现状仍是内嵌：`neoforge-26.1.2/build.gradle:221` 有 `jarJar(...)`、`fabric-1.20.1/build.gradle:89` 有 `include "..."` ⇒ 合并必须让**删除**生效，并把本线自己的 `starengine_version`（`26.4-SNAPSHOT.1`）接进新的区间写法。

**D. CI 与发布通道**
- `3bb6a819` 四线 jar 硬校验 + prerelease 显式判定。
- `276f5c2e` Release 资产一次性重发通道（提交信息带 `[republish-assets]` 才删同名附件重传）。

**E. 测试台与用例**
- `2c0cdbdb` 发布验收用例、fabric 通道支持、判定修正；`f38b564e` 1.21.1 用例改探试剂槽位 + 对齐 version-gate 行。

**F. 文档**
- `92e5a746` 1.5.0 changelog / readme 矩阵 / 玩家侧说明 / mapping；`e496f6a1` mapping 订正；`33fe2f5e` 26.1.2 的 ysm mixin 配置接线；`72d2048a` fabric 补 7 张新立牌 tooltip；`6fe38138` changelog 记录。

## 4 实测差距（复制即可复现，读数为 2026-11-02）

```bash
cd F:/MCProject/astral_dice_multiloader-next
# 1) 立牌 tooltip 分支数（dev-next / 主线）
for d in neoforge-1.21.1 forge-1.20.1 neoforge-26.1.2 fabric-1.20.1; do
  printf "%s dev-next=%s main=%s\n" "$d" \
    "$(grep -c 'addSignActiveTitle(tooltip, "' $d/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java)" \
    "$(grep -c 'addSignActiveTitle(tooltip, "' ../astral_dice_multiloader/$d/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java)";
done
```

| 判据 | dev-next | 主线 | 含义 |
|---|---|---|---|
| 立牌分支数 n21 / forge / n26 / fabric | **31 / 31 / 24 / 24** | 31 / 31 / 31 / 31 | n26 与 fabric 各缺 **7** 张新立牌（alana / ame / deepseek / dorothy / jill / kangel / lingling） |
| java 文件数 n21 / forge / n26 / fabric | 416 / 408 / **280** / **386** | 414 / 406 / 426 / 525 | n26 与 fabric 明显滞后 |
| 旧名「试剂-深渊」 | **仅** n21 / forge 两线仍在（`neoforge-1.21.1/.../zh_cn.json:380`、`forge-1.20.1/.../zh_cn.json:386`）；n26 / fabric 该键**根本不存在**（见下行） | 四线 0 命中 | 改名未吸收（两线） |
| `abyss_reagent` 引用 | n21 / forge 各 26 处；**n26 / fabric 全 0**（物品与治愈体系在这两线整条未合入，不是改名问题） | 四线齐备 | 内容缺口，合并后自然补齐 |
| 内嵌库 | `neoforge-26.1.2/build.gradle:221` `jarJar(...)`；`fabric-1.20.1/build.gradle:89` `include "..."` | 0 命中（外置前置） | 方向相反，见 §3-C |
| 发布验收用例 | `scripts/test/cases/*.json` 共 **258** 个，`RELEASE150-*.json` **0** 个 | **283** 个，含 4 个 `RELEASE150-*` | 用例未吸收 |

> 注：旧名残留排查时 `grep -rl 试剂-深渊` 会命中了 `.superpowers/sdd/**` 下的**历史任务快照**，那是技能留档、不是产品代码；判据要限定 `*/src`。

## 5 反同步操作手册（推荐顺序）

1. **先取数**：`git -C <next> fetch origin` 后确认 `origin/multi-main = 6fe38138`；记下本线 8 个独有提交（§2）。
2. **四棵树比对定基线**（本仓惯用判据）：合并后 `HEAD` 的文件集合必须满足 `HEAD == ours ∪ theirs`，`extra` / `lost` 双空。本仓删除单文件在 NTFS 上曾有病理慢（单文件约 80ms、极端 26 分钟；此为历史经验、未留书面记录，无法二次取证），批量删除型合并优先走 plumbing 剧本（`merge-tree --write-tree` + 临时索引 + `commit-tree` + 只 checkout 变化文件）。
3. **合并**：`git merge --no-ff origin/multi-main`（从 `multi-dev-next` 上做），冲突热点与处置见下表。
4. **版本纪律（合并后立刻验）**：四行 `^mod_version` 必须仍是 `26.4-SNAPSHOT.5+<line>`；`starengine_version` 必须仍是 `26.4-SNAPSHOT.1`。主线带进来的是 `1.5.0` / `1.1.0`，**不许覆盖本线**。
5. **构建**：四线各 `build` 一次；fabric 线 `-x pushToGame` 以免往整合包乱写。
6. **跑 §6 的 9 条复核**，全绿才 push。

| 冲突热点 | 本线应保留 | 应吃进主线 |
|---|---|---|
| `*/gradle.properties` 的 `mod_version` | `26.4-SNAPSHOT.5+<line>` | 无 |
| `starengine_version` / `starengine_version_range` | `26.4-SNAPSHOT.1` | 新的**区间写法**（`[x,2.0.0)` / `>=x <2.0.0`）|
| `build.gradle` 的 `jarJar(...)` / `include "..."` | 无（本线也要外置） | **删除** |
| `CHANGELOG.md` / `CHANGELOG_ZH.md` | 本线 `26.4-SNAPSHOT.*` 段 | 1.5.0 段落（历史保留）|
| `docs/handover-*` | 本线交接文件 | 主线新增文档 |
| `scripts/test/cases/*` | 本线用例 | `RELEASE150-*` 四例 |

## 6 合并后必须复核的 9 条

| # | 判据 | 通过线 |
|---|---|---|
| 1 | 立牌 tooltip 分支数 | 四线均 **31** |
| 2 | 旧名「试剂-深渊」 | `grep -rl --include=*.json --include=*.java 试剂-深渊 */src` = 0 |
| 3 | 内嵌库 | `jarJar(` / `include "` 在 n26 / fabric `build.gradle` 0 命中；产物 jar 内 `META-INF/jarjar/`、`META-INF/jars/*.jar` 无 starengine |
| 4 | 语言文件 | `python tools/check_lang_sync.py` 四线 `[OK]`（主线读数 1142 / 1142 / 1166 / 1151，本线会因新内容更高） |
| 5 | changelog 对等 | `python tools/check_changelog_parity.py` ⇒ `CHANGELOG_PARITY: OK`，sections / entries 两侧相等 |
| 6 | 四线构建 | 四条线 `build` 各自 BUILD SUCCESSFUL，jar 落在 `<line>/build/libs/` 且在仓根 `build/libs/` |
| 7 | fabric 客户端 | 直接 `gradlew :fabric-1.20.1:runClient --console=plain -PautojsTestMod=1 -PtestSodium=false -Pquickplay=testworld`（`launch.sh` **不透传** `-PtestSodium=false`，而 Sodium 版本不匹配会崩） |
| 8 | 26.1.2 启动 | 只能走 production 形态 `python run/26.1.2/.tmp/prodlaunch.py --quickplay testworld --log <log>`（dev/userdev 形态必触发 YSM err54）。**该路径只在主线树存在**，dev-next 需先合并或自行补引导器 |
| 9 | 用例合法性 | `pwsh -File scripts/test/mt_case.ps1 validate --case <json>` ⇒ `MT_VALIDATE: OK` |

## 7 已知坑（本次实战踩出来的，直接抄）

> 本节行号以**主线树** `astral_dice_multiloader` 为准，括号内给出 dev-next 对应行；两树行号不同是因为 dev-next 少了 1.5.0 批次的内容。

**文件与编码**
- Python 改 **CRLF** 文件必须 `read(newline="")` → 打补丁时归一成 LF → 写回时换回 CRLF；否则带换行的锚点全部匹配失败，还会留下裸 LF。
- JSON 里多行文本的换行是**转义的两个字符** `\n`，替换要按转义串匹配，不能按真换行。
- 本仓行尾口径：`README*` 纯 LF、`CHANGELOG*` 与 `release/*.md` 纯 CRLF、`docs/*.md` 与 HTML/MAPPING 纯 LF。改完用 `python3 -c` 数 CRLF / 裸 LF。

**git / 发布**
- **CC Safety Net 会 BLOCK 裸 `git push --force`**（拦截规则名 `git.push-force`；本条来自发布会话实战，未取证到规则文件本身）⇒ 必须 `git push --force-with-lease=refs/tags/<tag>:<旧 sha>`，且整条复合命令会被整段拒绝，要拆成独立命令。
- tag 只按 mod 版本号（`1.5.0`），禁平台前缀 / `v` 前缀 / `+加载器` 后缀；同版本多平台 jar 进**同一个** Release；带 `rc` / `pre` / `alpha` / `beta` 才标 prerelease。
- Release 步骤有**资产冻结**：已存在的同名附件**绝不覆盖**（2026-10-30 裁决），只补缺失。一次性重发靠提交信息带 `[republish-assets]`（`276f5c2e` 加的通道）。

**CurseForge**
- 无 API Key：`api.curseforge.com` 拿 `.curseforge/token` 当 `x-api-key` 会 **403**（那是上传 token）；`tools/curseforge_upload.py` **没有列文件能力**。
- `mediafilez.forgecdn.net` / `edge.forgecdn.net` 对**文件名含 `+`** 的请求一律 403（连确认在线的件也 403）⇒ **403 不能判定文件不存在**；`api.cfwidget.com` 缓存滞后同样不能当失败依据。
- 上传 P1 两线（26.1.2 / fabric）必须 `--allow-p1`，否则脚本判 BLOCKED；库 jar 会自动改道到库项目 `1755415` 并自动 `--no-relations`。

**实机 / 测试台**
- AutoJS 端点 `25585` 在脚本里**四线硬编码**（`mt_launch.ps1:1094`(主线) / `:999`(dev)；`mt_assert.ps1:140`(两树同)；`mt_case.ps1:269`(主线) / `:149`(dev)）⇒ **同一时刻只能一条线占着**；`25585` / `25565` 被占时**绝不能 launch**，否则注入打进别人那台游戏，产生跨线污染的假绿。
- AutoJS 的 `command()` 走 `MinecraftServer.createCommandSourceStack()` ⇒ `@s` 解析成 console、没有玩家，**选择器一律 `@a`**。
- `--phase preflight` 不是合法单阶段（`mt.ps1:552`(主线) / `:499`(dev) 不认），正确入口是 `scripts/test/mt_preflight.ps1 --version <线>`；`-?` 是陷阱，要 `--help`。
- `mt_env.ps1 world` 默认 180s（`Invoke-MtEnvWorld [int]$Timeout = 180`），大世界易 BLOCKED；`mt.ps1:478`(主线) / `:429`(dev) 调它时**不转发 `--timeout`**，需要时直接跑 `mt_env.ps1 world --version <线> --timeout 900`。
- 每轮结束必须 `mt.ps1 --version <线> --phase stop --force --purge-saves` 放端口。
- 截图 `Screenshot.grab(...)` 是**异步落盘**的，grab 之后要等 ~2s 再复制，否则拿到 0 字节；F2 截图的 tag→文件名映射只在 `run/<ver>/screenshots/.mt_shots.json`，断言必须经 `Get-MtCurrentShots(run_id)`，不许 glob。
- 鼠标 hover 取证：屏幕坐标 = 窗口 origin + gui 坐标 × guiScale（本次实例 origin `(320,180)`、guiScale `4`），`AbstractContainerScreen` 的 `leftPos/topPos/hoveredSlot` 可反射读。

**平台特定的坑（26.1.2 / fabric）**
- 26.1.2 的 **YSM err54** 不是环境缺陷，是 YSM 原生库对 dev/userdev 启动的主动拒绝 ⇒ 只能 production 形态启动；换 JVM / 伪装属性全部无效，Gradle 侧绕不过去。
- production 形态下 KubeJS 的 `WorldLoaderMixin` 会因 `No refMap loaded` 抛 `InjectionError`，那属测试环境问题（栈内**无** `com.merlinkitsune` 帧），判断时别算到本模组头上。
- fabric 客户端另有 `-PtestSodium=false` 必需（见 §6-7），且启动前 `rm -f fabric-1.20.1/build/loom-cache/argFiles/runClient` 避免旧参数缓存。

## 8 本线自己的口径（别被主线文档带跑）

- **版本方案**：`<year>.<quarter>-SNAPSHOT.<n>`（当前 `26.4-SNAPSHOT.5`），按提交递增，不跟 1.5.0 那套发版节奏。
- **StarEngine**：走 `starengine_next` worktree 的 `26.4-SNAPSHOT.1`，与主线 `1.1.0` 是**两套东西**，引脚与区间都要按本线改。
- **独有功能**：GeckoLib 硬前置（要写 Modrinth **version id**，写纯版本号会解析错平台变体）、动画实体 `astral_dice:sykestentacle`、mob 侧骰战参与者与特殊骰能力注册接口。
- **规则正本**：`docs/agent/AGENTS-dsh-linux.md`（原生 Linux）。主线文档里的 Windows 口径（pwsh 绝对路径、`F:\` 盘符等）在本线只作对照。

## 9 未完成 / 待裁决（从主线侧承接过来）

1. **CF 后台归档**：`9402507`（n26，内嵌库旧件）、`9402509`（fabric，内嵌库旧件）、`9402707`（fabric，外置前置的**首版**，已被 `9402802` 取代）等用户在 CurseForge 后台确认归档；主线侧已无可用探测通道（见 §7）。
2. **MC 百科版本日志**：`release/1.5.0/PLAYER_CHANGELOG_MCMOD.html` 需人工粘贴上传（自包含 HTML 57,903 B，`href` / `script` / `http` / `table` / `style=` / `src=` 命中均为 0）。**文件在主线树**，dev-next 没有。
3. **主线 1.5.0 实机证据链**：截图在主线树 `run/1.21.1/screenshots/abyss_reagent_tooltip.png`（158 KB，11-02 06:52）、`run/fabric-1.20.1/screenshots/{abyss_reagent_tooltip,chip_shop_ui,release150_guide}.png`（11-02 12:12）与 `work/shots/fabric-*_sign.png`（6 张立牌 tooltip）；**`scripts/test/reports/` 下没有 1.5.0 批次目录**（报告未入库），结论靠探针日志 + 上述截图 + Release/CF 实物支撑。后续回归可复跑 `RELEASE150-*` 用例。
4. **本次反同步本身**：还没做——本文件只交接口径与判据，未在 `multi-dev-next` 上执行任何合并。

---

交接作者：DSH（主线 1.5.0 发布会话）；快照时间 2026-11-02 13:2x；所有数字均实读，若漂移以命令输出为准。
