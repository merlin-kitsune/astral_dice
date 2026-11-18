# 附录：neoforge-26.1.2 复跑「准备阶段」取证与端口门自审
（2026-11-02，stamp `20261102-040635`；子会话记录）

> 本文是 `work/release150-26.1.2-rerun.md`（lead 主报告，426 行）的**附页**，不替代它。
> 归属：本子会话**只执行了第 1 阶段（准备）**；第 2 阶段（实机跑测）**由 lead 会话完成**（见主报告 §3–§7）。本文不重复主报告的判定结论，也不改判任何断言。

## 1. 结论

| 项 | 本子会话结果 |
|---|---|
| 准备阶段（备份 / 装配 / 查重 / 新件核验 / 轮转 / dry-run） | **完成**（§2、§5） |
| 实机跑测 | **未执行**——我的端口门实现误报占用，白等 60 分钟（§4，自审） |
| 我的装配缺陷 | **2 处**：漏 `rhino`、漏 `better-advanced-tooltips`（§3），已被 lead 修复并在主报告 §3.1/§3.2 记录 |
| 收尾复原 | **完成并逐文件核验**（§6） |
| MT_CASE / 断言 / 探针 / 截图 | 本子会话**无产出**；一律以主报告为准 |

## 2. 准备阶段取证（本子会话执行，全部留痕）

### 2.1 备份

- 源 `run/26.1.2/prodgame/mods` = **17 项** → 备份 `run/26.1.2/.tmp/mods-prodgame-prev-20261102-040635`（17 项，清单逐一相同）。
- 两份 rc.7 `.bak` 原样在位：`astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar.bak-20261102-033248`（2,875,013 B / `6b278f07…`）、`…jar.compat-on.bak`（2,875,164 B / `7e2c56ae…`）。

### 2.2 装配（我的版本，后被 lead 修正）

| 动作 | 结果 |
|---|---|
| 复制玩家实例 95 jar | 含 `[帕秋莉手册] patchouli-neoforge-26.1-94.jar`（672,884 B / `00f9e611…`） |
| 旧 `astral_dice` 移出 | 到 `run/26.1.2/.tmp/mods-astral-old-20261102-040635\`，2,875,237 B / `dd22295c…` |
| 换入新构建 | 2,877,947 B / `b422d57d2103219522eef792822da1656a1f9e197f91c6b0d8e82d381ec4878d` |
| 测试注入 | `kubejs-neoforge-26.1.2-8.0.6.jar`（2,214,228 B / `36df2626…`）、`AutoJS-neoforge-26.1-1.0.0.jar`（164,801 B / `f867715a…`） |
| 合计 | 97 项 / 264,322,990 B |

逐文件清单（name/size/sha256）：`run/26.1.2/.tmp/assembly-20261102-040635.json`。

### 2.3 重复模块名检查（结论：干净，未删任何文件）

| 检查 | 结果 |
|---|---|
| sha256 分组重复 | **0 组** |
| `Automatic-Module-Name` 重复 | **0**（上一轮 `me.decce.ixeris.neoforge vs ixeris_dummy` 型冲突不可能复现） |
| `[[mods]]` 声明 modId 重复 | 97 jar → **96 个不同 modId，0 重复**；`kotlinforforge-6.3.0-all.jar` 无 modId（语言提供器） |
| Ixeris | 仅 `Ixeris-4.6.8+26.1.2-neoforge.jar` 一份（683,698 B / `50038faa…`） |

> 两条假阳性（供后续复用）：宽正则扫 `[[dependencies.*]]` 的 `modId` 会把 `minecraft`/`neoforge` 误判成重复；文件名词干 `supermartijn` 命中 `supermartijn642configlib` 与 `…corelib` 两个真实模组。

### 2.4 新件核验（从已落盘 jar 内读 `sounds.json`）

键数 **23**，`event_*` **8 条**：`event_bonus`、`event_damage`、`event_fade_in`、`event_fade_out`、`event_heal`、`event_poisoning`、`event_tick`、`event_transport`；`.ogg` 实物 23 个、上述 8 条齐全。取证：`run/26.1.2/.tmp/modids-and-sounds.json`。

## 3. 我的装配缺陷（如实记录）

我的 97 项装配 = 玩家实例 95 + `kubejs` + `AutoJS`，**缺两个隐式依赖**：

1. **`rhino-2101.2.8-build.91.jar` 缺失**——KubeJS / AutoJS 都要它（dev 环境由 `build.gradle` 提供，玩家实例集没有）。表现：FML 直接拒绝，`Mod kubejs requires rhino 2101.2.8-build.91 or above`。
2. **`better-advanced-tooltips-2601.1.0-build.10.jar` 缺失**——dev 版 KubeJS 8.0.6 有 4 个类硬引用 `dev/latvian/mods/betteradvancedtooltips/BATIcons`（`KubeJSErrorScreen$Entry`、`KubeJSCommands`、`TextIcons`、`ChangesForChat`）。表现：`RegisterEvent` 阶段 `NoClassDefFoundError` 崩溃。

lead 的修复（主报告 §3.1/§3.2）：补回 `rhino` 与 `better-advanced-tooltips`，并把玩家实例的 `mru-1.0.43+26.1.2-neoforge.jar` + 其唯一依赖方 `sounds-2.6.0+26.1.2-neoforge.jar` 成对移出（`mru` 的 toml 写死 `neoforge [26.1.2.114,)`，测试台只有 26.1.2.109），移出件与理由留在 `run/26.1.2/.tmp/_rerun-20261102/mods-excluded/`。

**教训（写给下一轮）**：「dev 目录的 KubeJS」注入「玩家实例 95 jar」时，注入件与全集之间至少缺 `rhino` 与 `better-advanced-tooltips` 两个隐式依赖，必须一起补；同时玩家实例里可能存在要求更高 `neoforge` 构建的模组（本例 `mru`），需按依赖闭包成对处置。

## 4. 端口门自审（本轮最大的流程失误）

- 我的门脚本：`run/26.1.2/.tmp/_gate.ps1`，判定 `GATE=OPEN/BLOCKED`，输出 `OCC port … pid=…` 与 `PROC pid=…`。
- 04:06 → 05:06 每 30 s 轮询（后台 job `bash-c48af7a74abd4a6a`，110 轮全 BLOCKED，收尾 `GATE_TIMEOUT elapsed=3504s`，exit 2），据此判定「被 fabric-1.20.1 占用」并等满 60 分钟额度。
- **但 lead 主报告 §2.2 给出反证**：04:21 用 `Get-NetTCPConnection -State Listen` 过滤 25565/25575/25585 得到**0 行**，门实际是开的，lead 于 04:22 正常起跑；并且起跑后 04:31:19 AutoJS 已在 25585 上监听，而 `-State Listen` 仍返回 0 行（本机 TCP 栈下该端点不以 LISTEN 行呈现）。
- 我的脚本把**非 LISTEN 的连接行**（`TIME_WAIT` / 我方客户端的出站连接）也算成「占用」，因此产生**假阳性**，把一条本来可用的测试窗口整个让掉了。

**更正口径（下一轮直接用）**：
1. 不要用「端口有没有行」当就绪门；**就绪门 = AutoJS status（server available + client available + world loaded）**。
2. 冲突检测只认「本方 game dir 内的 java」与「同名端口的 `-State Listen`」，且要区分 `ESTABLISHED/TIME_WAIT` 与 `LISTEN`。
3. 他人进程一律不 kill；但既然门可能假阳性，「等 60 分钟」这种策略应当先被证伪再用。

## 5. 轮转与 dry-run（原文）

轮转（全部改名，无删除）：

| 对象 | 大小 | mtime | 去向 |
|---|---|---|---|
| `run/26.1.2/prodgame/logs/latest.log` | 148,358 B | 2026-11-02T03:45:06 | `latest.log.prev-20261102-040635` |
| `run/26.1.2/.tmp/prod-run.log` | 137,562 B | 03:45:06 | `prod-run.log.prev-20261102-040635` |
| `screenshots/abyss_reagent_tooltip.png` | 157,975 B | 03:45:03 | `…png.prev-20261102-040635` |
| `screenshots/chip_shop_ui.png` | 131,150 B | 03:45:04 | `…png.prev-20261102-040635` |
| `screenshots/release150_guide.png` | 109,430 B | 03:45:06 | `…png.prev-20261102-040635` |
| `screenshots/.mt_shots.json` | 1,797 B（15 条） | 03:45:06 | `.mt_shots.json.bak-before-rerun-20261102-040635`；只移除 `abyss_reagent_tooltip`/`chip_shop_ui`/`release150_guide` 三 tag，15 → 12 条 |

`dry-run`（`python run/26.1.2/.tmp/prodlaunch.py --quickplay testworld --log run/26.1.2/.tmp/prod-run.log --dry-run`，exit 0）关键行：

```
gamedir   : F:\MCProject\astral_dice_multiloader\run\26.1.2\prodgame
classpath entries: 102 missing: 0
unresolved placeholders: none
argv entries: 48 cmd head: C:\Program Files\Zulu\zulu-25\bin\java.exe -Xms1G -Xmx4G …
ARG| --quickPlaySingleplayer=testworld
DRY-RUN ok
```

`--quickplay` 已被脚本合并为 `--quickPlaySingleplayer=testworld`（带空格写法会静默停在主菜单）。

## 6. 收尾复原（已完成并核验）

- `run/26.1.2/prodgame/mods` = **17 项 / 106,916,161 B**，17 个文件 size + sha256 **与备份清单逐一相同**；`astral_dice` 回到旧构建 2,875,237 B / `dd22295c…`。
- 当时在位的全集（**lead 修正后的 97 项**：含 `rhino`、`better-advanced-tooltips`，不含 `mru`/`sounds`，`astral_dice` = `b422d57d…`）**未删除**，整体改名到 `run/26.1.2/.tmp/mods-fullset-run-aborted-20261102-040635`（97 项，已复核）。
- 注意命名：该目录名带 `run-aborted`，但内容其实是**可复用的成功装配集**，不是失败件。
- 辅助脚本（ASCII，幂等）：
  - `run/26.1.2/.tmp/rearm-fullset.ps1` —— 备份当前 → 让位 → 复制上述 97 集 → 打印条目数与 `astral_dice` sha256（期望 97 + `b422d57d…`）。
  - `run/26.1.2/.tmp/restore-17jar.ps1` —— 复原到 17 项基线（期望 17 + `dd22295c…`）。
- 「测试注入的两个 jar」的表述：它们随全集整目录撤除，**不是**从 17 项里单独删掉的；而 17 项原状**本身就含** `kubejs` 与 `AutoJS`（轮前 dev 测试集），别写成「这两个 jar 已消失」。

## 7. 并发写入提示（给 lead 的协调项）

本子会话在 05:06 执行复原时，`run/26.1.2/.tmp` 里仍有并发的写者在落文件（`probe-00-precheck.ps1` … `probe-05-logs.ps1`，05:06:34–05:07:34；`_rerun-20261102` 相关脚本；`_shot_audit.txt` 05:03:10）。同一 game dir 还有一次 04:45:49–05:03:27 的客户端运行（`run/26.1.2/.tmp/prod-run-r2.log`，392,528 B；`prod-run.pid`=33508），并在 `run/26.1.2/screenshots/` 落了 `probe_channel_r2` 与三张同名图（04:58:23/29/35）。

**归属未核实**：这次 04:45 的运行与 05:06–05:07 的脚本，可能是 lead 在主报告之后继续做的通道复测轮次，也可能是另一个并行会话；本文不做断言。可确定的是：它**没有**走 `mt_case.ps1`（无 `MT_CASE` / `MT_CHANNEL` 行），因此**不能**替代主报告 §4 的 27 条断言结论，也不计入主报告 04:39 那个证据窗口。

建议：同一 `run/26.1.2` 下**同一时间只允许一个会话**持有 `prodgame/mods` 与 `.tmp/prod-run*`；下一轮起跑前先确认 `.tmp/prod-run.pid` 对应的进程已不存在、且没有他人正在写 `.tmp`。
