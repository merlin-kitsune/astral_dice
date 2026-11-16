# 交接：multi-dev-next 2026-11-11 会话（小猎手立牌终态 + P1 三线移植 + 跨区块/跨维度修复）

> 本文件是 **2026-11-09～2026-11-11 这一轮 DSH(Linux 原生) 会话的交接正本**，取代此前任何「dev-next 还没做小猎手/P1 还没落地」的口径。
> 事实源纪律不变：分支 / HEAD / 提交数 / 产物哈希一律**实读命令**为准，本文数字只是 2026-11-11 14:2x 的快照。

## §0 接手第一步

1. 根 `AGENTS.md` 认领宿主：本机 = 原生 Linux 真机 ⇒ 规则正本 `docs/agent/AGENTS-dsh-linux.md`（388 行左右；`docs/agent/AGENTS-dsh.md` 是 Windows 历史正本，只对照）。
2. 加载技能 `using-superpowers`（规则正本硬要求）。
3. 读规则正本全文 + 本文 + `docs/handover-1.5.0-main.md` 的 `## 5 遗留与欠账`（历史欠账清单，仍有效）。
4. 动手前跑 `bash scripts/test/linux/env.sh` 与 `git status --short -uall` 核对现状；实机前查 `ss -ltnp | grep -E ':(25585|25565|25575)'` 与 `/mnt/f/MCProject/.agent-locks/`。

## §1 实读现状（2026-11-11 14:2x 快照）

| 项 | 值 |
|---|---|
| 工作树 | `/mnt/f/MCProject/astral_dice_multiloader-next`（git worktree，`.git` 是文件，指向主树 `.git/worktrees/astral_dice_multiloader-next`） |
| 分支 / HEAD | `multi-dev-next` / `e1d1e922`（本批最后一条代码提交；文档提交在其后） |
| 与远端 | 本轮最后一次 push 是 `bd721cc0..c02c6b57`；其后 28+ 个提交在本次会话末已 push（见 §6） |
| 版本 | 五线 `mod_version = 26.4-SNAPSHOT.11+<加载器后缀>` |
| starengine 引脚 | neo21 `.2` / forge `.2` / neo26 `.2` / fabric-1.20.1 `.11` / fabric-1.21.1 `.9`；`~/.m2` 五平台制品已齐全（`starengine_next` 五条 `publishToMavenLocal` 已跑过） |
| 产物 | 五线 2026-11-11T14:15 按 `e1d1e922` 重建；P0 两枚 `build-info.json` 指向 `e1d1e922`；两枚 P0 `.sha256` 旁证已按最终字节重生成（`<line>/build/libs` + 根 `build/libs` 镜像） |
| 工作树脏度 | tracked 干净；未跟踪 716 条（`-uall`）= 6 个 `models/*.bbmodel` + `work/audit/` + `work/souri/` + 仓库根 `立牌计划表.txt`（红线 4，一律不动） |
| 网络 | clash TUN 直连；`~/.gradle/gradle.properties` 已无代理五行。内核未起时全部 gradle 需 `--offline`（判据 `ss -ltn | grep 7897`） |

## §2 本轮落地（按时间序，均有本地提交）

### 2.1 规则与环境（2026-11-10 上午起）
- `docs/agent/AGENTS-dsh-linux.md` 按真机校准重写：Gradle **9.5.1**（dists 9.2.1+9.5.1）、`~/.gradle` 非空、`build/moddev/artifacts` 两树都在、JDK 21.0.13.0.3、内核 43、AppArmor enabled、`ydotool` socket 存在、`autojs` MCP 已注册（`http://127.0.0.1:25585/mcp`）、红线区块改 L2–L130、委派/复核口径（`code_review_*` 已弃用，改一次性独立 `subagent`）。
- 2026-11-10 用户裁决：**Linux 只有 TUN 直连、不走代理服务器** ⇒ 删除 `~/.gradle/gradle.properties` 代理五行（`--offline` 口径保留），远程 git 用 `git -c http.proxy= -c https.proxy=`。
- 2026-11-11 用户裁决（m05435）：**实机期间必须阻止 KDE 锁屏与睡眠**，收尾关闭 —— `systemd-inhibit --what=idle:sleep:handle-lid-switch ... sleep 7200 &` + `dbus-send ... org.freedesktop.ScreenSaver.Inhibit`（记 cookie），收尾 `kill` + `UnInhibit`。已写入规则正本。
- 实机启动必须：`export DISPLAY=:0` **且** `export XAUTHORITY=$(pgrep -a Xwayland | grep -o '/run/user/[0-9]*/xauth_[A-Za-z0-9]*' | head -1)`；显示器休眠时截图全黑、NeoForge 1.21.1 early display 会交接失败。

### 2.2 金鲤 `souri_sign`
- P1 三线（fabric-1.20.1 / fabric-1.21.1 / neoforge-26.1.2）移植 → datagen 跑通 → 三线串行实机 PASS → 提交；规格 `docs/features/souri-sign-spec.md`。
- 顺手修 P0 缺陷：`forge-1.20.1` 缺 `assets/astral_dice/animations/souridrink.animation.json`。
- datagen 两坑（已写进规则正本 §5.1）：①跑前必须 park `run/<线>/mods/astral_dice-*.jar`（否则跑旧 jar，provider 改动不生效，还会删掉 generated 文件）；②`runData` 收尾 JVM 不退出（判据 = `HashCache` 行 + `git status <line>/src/generated`，然后 `pkill -f dataRunProgramArgs`，且 pattern 不能出现在同一条 `bash -c` 命令行里）。

### 2.3 少主 `al_sign`
- P1 三线移植（含 `CardChoiceManager` 的 `minSlots` 变长下限基建、`CardChoiceMenu.OpenData` 编解码、`ChipShopDiscounts`、`PlayerLikeTargets`、`CardGivenHooks`）→ datagen → 三线串行实机 PASS → 提交；规格 `docs/features/al-sign-spec.md`。
- 修 P0 缺陷：AL 立牌不在创造栏（`init/ModCreativeTabs.java` 补 `output.accept(ModItems.AL_SIGN.get())`）。

### 2.4 小猎手 `inkshadow_sign`（本轮主线，共四轮验收）
- **定名**：id `inkshadow`（用户 m04852：英文 InkShadow / 日文 スミカゲ）；立牌名 InkShadow Sign / スミカゲのスタンド；实体 InkShadow Portal / スミカゲポータル；zh 名「小猎手立牌」。
- 五线全部落地（P0 两线 → P1 三线），规格 `docs/features/inkshadow-sign-spec.md`（含 §1 常量表、§7.1a–§7.1i 全部实机读数、§8 复跑清单）。
- 语义要点（可调值都集中在 `InkShadowSignItem.java`）：窗口 `WINDOW_TICKS=600`（30 s）、门对存活 `PAIR_LIFETIME_TICKS=3600`（3:00）、放置半径 8 格、放置期 +10% 移速并禁攻击/切武器/交互、站定 `STAND_TICKS=30`（1.5 s）后传送、`TELEPORT_COOLDOWN_TICKS=100`（5 s）、主人保险 `OWNER_ARM_RADIUS=4.0`、敌对 2.5 格内每 20 tick 2.0 技能伤害、被动按「自己名下存活门对」给攻击层数（每层 +2，每名玩家含自身仅一次）。
- 冷却走**全局立牌规则**（默认 180 s，含充能封顶与电池减免），不再写死 2400。
- 音效：门本身每 90 tick 播 `SoundEvents.PORTAL_AMBIENT`；首次站定播 `PORTAL_TRIGGER`；跨维度出发侧补 `PORTAL_TRAVEL`。
- 品质稀有（`ASTRAL_DICE_RARE`），配方 `BXB/KCK/XDX`（B=末影珍珠、X=星币、K=黄色染料、C=空白筹码、D=普通骰子）。
- 模型：`ink128.bbmodel` → `tools/bbmodel_to_geo.py` → `geo/inkshadoweffect_ink128.geo.json`；门面紫色星盘是渲染器单独画的地面 quad（`entityTranslucentEmissive` + `FULL_BRIGHT`，仅 ACTIVE 渲染）。
- **跨区块/跨维度修复（`4ee66b81`）**：附件 `INKSHADOW_PENDING_POS` 记起点坐标兜底 + 门实体自钉区块（放置即 `setChunkForced`、每 tick `forceOwnChunk()`、`remove()` 释放）。实机：forge 跨区块/跨维度双向 PASS；1.21.1 跨维度放置 + 穿越 PASS；26.1.2 跨维度放置 PASS。

### 2.5 其它修复
- `astral_dice:abyss_tentacle_collision` 五线缺 blockstate/model（只有 26.1.2 datagen 补出）⇒ 按 `barricade_collision` 同手法补 invisible 模型。
- 26.1.2 GeckoLib 5 eager bake 空动画报错 ⇒ 补最小 idle 动画。
- 测试台 `scripts/test/linux/{launch,stop,env}.sh` 支持两条 fabric 线（`run/client` 目录、`-PautojsTestMod=1`、版本表）。
- `tools/gen_agents_toc.py` 重建 TOC（现 262 标题 / 4174 行）；`docs/agent/AGENTS-full.md` 追加批次 41–49。

## §3 本轮实机取证汇总（全部串行、每轮 `stop.sh` 后端口清零）

| 线 | 日期/日志 | 结论 |
|---|---|---|
| neoforge-1.21.1 | `1.21.1-20261110-132958.log`、`1.21.1-20261111-132958.log` | 金鲤/少主 tooltip 全行；abyss 碰撞块 quads=0；小猎手视觉 + 跨维度放置/穿越 PASS |
| forge-1.20.1 | `1.20.1-20261111-130421.log` | 小猎手跨区块/跨维度双向 PASS；tooltip 只能静态取证（Rhino 取不到 `TooltipFlag$Default.NORMAL`） |
| fabric-1.20.1 | `fabric-1.20.1-20261111-014318.log`、`-134215.log` | 金鲤/少主/小猎手三牌齐、门对 ACTIVE、视觉正常；跨维度放置未测 |
| fabric-1.21.1 | `fabric-1.21.1-20261111-015857.log`、`-134657.log` | 同上（自有 Rhino，无 KubeJS/Utils）；视觉正常 |
| neoforge-26.1.2 | `26.1.2-20261111-020546.log`、`-135118.log` | 三牌齐；小猎手跨维度放置 PASS；视觉正常 |

## §4 本轮欠账验证结果（2026-11-11 14:0x–14:2x 实读）

| 欠账 | 状态 | 依据 |
|---|---|---|
| 产物滞后 HEAD | **已闭环** | 五线 14:15 按 `e1d1e922` 重建，P0 两枚 build-info 已指向该提交，`.sha256` 重生成，README 溯源表换真值 |
| P1「六项欠账」（JEI 筹码 / BeamFx / GlobalEventGate / RepulsionEntity / `planPlacement` / EventDrawBlock） | **已闭环** | 三条 P1 线均有 `compat/jei/ChipShopJei*`；`BeamFx`/`GlobalEventGate`/`RepulsionEntity`/`EventDrawBlock` 类在三条线各 1–2 处；`planPlacement` 在 `CardShopMenu` 各 5 处 |
| Linux VM 四项（fabric Sodium/LWJGL、26.1.2 KubeJSErrorScreen 等） | **已不复现** | 三条 P1 线本轮 `launch.sh` 均 READY + WORLD_READY |
| `tooltip_color_audit` 存量违规 | **仍欠，且变多** | `python3 scripts/audit/tooltip_color_audit.py` ⇒ `FAIL（302 处违规）`（旧记录 144 处；覆盖 4 线 × 三语 12 文件，规则 R1/R3） |
| 8 条「代码与文案都不符」 | 未处理 | 仅在 `handover-1.5.0-main.md` §5 Major 5 登记，无代码/文案改动 |
| 强化拒止残留（tooltip「所有者：」客户端渲染取证、规则④跨容器） | 未做 | 需实机，本轮未获指令 |
| `pushToGame` | 仍未真投 | 本轮重建打印 `pushToGame: skipped — 分支 'multi-dev-next' 不在整合包推送白名单 [multi-main] 内`（符合红线 3，只有 `-PdeployToPack` 才会真投） |
| 未实现立牌 | 仍剩 2 个 | `docs/features/sign-roadmap.md`：Z3000（垃圾桶，109）、Luka（摩托车手，119）；小猎手已五线落地 |
| 716 条未跟踪 | 保留不动 | 6 个 `models/*.bbmodel` + `work/audit/` + `work/souri/` + `立牌计划表.txt`（红线 4：他人/素材） |
| `temp/` 体积 | 4.8 GB | 被 `.gitignore` 忽略；可随时清（含 `temp/datagen`、`temp/live-*` 报告） |
| P1 三线无 `activateForEntity` | 仍欠（已知舍取） | P0 两线各 36 处引用；P1 三线各 1 处（仅注释）。只影响「非玩家持牌生物主动施放」，玩家路径等价 |
| 26.1.2 未纳入命名门禁 | 仍欠，已量化 | 把 `neoforge-26.1.2` 加进 `tools/verify_sign_display_names.py` 的 `LINES` ⇒ **FAIL 40 处**旧职称（en/ja，如 `Kanban Musume Sign` / `Guardian Sign` / `Gunsmith Sign`）；已回退，门禁仍 `PASS 111,963` |
| fabric 两线跨维度放置 | 未实机 | 视觉已取证，逻辑与其余三线同源 |
| 26.1.2 datagen SIGBUS | 仅 datagen 路径 | `runClient` 本轮未复现；记在规则正本（跑前 park sodium/iris） |

## §5 环境与工具坑（本轮新增，已写进规则正本）

1. **实机前**：`DISPLAY=:0` + `XAUTHORITY`；跑前 park 本线 `astral_dice-*.jar`；`systemd-inhibit` + ScreenSaver.Inhibit。
2. **AutoJS 探针**（各线口径不同）：1.21.1 用 `ServerLifecycleHooks.getCurrentServer()` + `p.serverLevel()`；fabric-1.20.1 用 `Utils.getServer()`；fabric-1.21.1 用 `Minecraft.getInstance().getSingleplayerServer()`；26.1.2 用 `p.level` **属性**（`serverLevel()` 不存在）、`getForcedChunks()` 已改名、`level.getEntities()` 直接可迭代。
3. **Rhino 通病**：无 `getClass()`（用 `String(e.type)` 过滤）；`teleportTo(level,...)` 二义（用 `p.connection.teleport(x,y,z,yaw,pitch)`）；`Item.of("id")` 静默返回 air（用 `BR.ITEM.get(RL).getDefaultInstance()`）。
4. **git 提交信息**：不要用 JS 模板串拼 `\n` 再写文件；用数组 `join("\n")` 写 `/tmp/*.txt` 再 `git commit -F`（`pkill -f` 的 pattern 也不能出现在同一条 `bash -c` 里）。
5. **gradle**：`--no-configuration-cache` 是 NeoForge `createMinecraftArtifacts` 的稳定解法；内核未起时加 `--offline`。

## §6 本批提交与推送

- 本次会话最后一次 push：把 `bd721cc0..` 之后的本批提交推到 `origin/multi-dev-next`（含 .11 升版、金鲤/少主/小猎手五线实现、跨区块修复、规则与文档）。
- 提交风格：一个可独立验证的单元一条（红线 8），信息用 `git commit -F`；**默认不 push**，本轮按用户明确指令推送。
- 收尾清单（每轮实机后）：`bash scripts/test/linux/stop.sh --version <线>`、端口三查、`parked` jar 归位、`systemd-inhibit` kill + `UnInhibit`、删 `/mnt/f/MCProject/.agent-locks/dsh.lock`、`gradlew --stop`。

## §7 下一步候选（按优先序）

1. **26.1.2 命名门禁对齐**：把 `neoforge-26.1.2` 加进 `LINES`，按 `tools/sign_display_names.json` 逐条修 40 处旧职称（en/ja 三语），门禁恢复全五线。
2. **fabric 两线跨维度放置实机**：补齐后五线全部有跨维度证据。
3. **`tooltip_color_audit` 302 处**：先按规则批次清理，再决定是否进 CI。
4. **剩余两个立牌**：Z3000（109）→ Luka（119）；开工前先定名（`tools/sign_display_names.json` + wiki 核实）。
5. **P1 `activateForEntity`**：若要非玩家持牌生物技能，需在 P1 三线补 `SignEntitySkills` 等价基建。
