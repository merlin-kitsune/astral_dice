# 实机验证报告：kangel 拆双 id（`5695832e`）+ 1.20.1 一心同体复核

- 被测版本：`2.0.0-SNAPSHOT.128`；HEAD `5695832e281a9eb270ed4f4badcc4764bdc5a1c0`（2026-10-17 13:24:28 +0800）
- 工作树：`/mnt/f/mcproject/astral_dice_multiloader-next`（分支 `multi-dev-next`）
- 两线 jar 重建：`run/1.21.1/mods/astral_dice-2.0.0-SNAPSHOT.128+neoforge_1.21.1.jar` mtime **13:26:58**、`run/1.20.1/mods/astral_dice-2.0.0-SNAPSHOT.128+forge_1.20.1.jar` mtime **13:27:02**（均晚于提交时间 13:24:28）
- 测试台：`scripts/test/mt.ps1`（pwsh 7，经 `cmd.exe /c` 互操作）；世界 `testworld`；靶子非亡灵（`minecraft:pillager`）；假人 = Carpet 真 `ServerPlayer`，**非 bot 前缀**（`Fake1`/`Fake2`/`Fake3`/`Fake4`）
- 证据留档：`temp/kame-20261017/{1.21.1,1.20.1}/`（`latest.log`、`kubejs_server.log`、`client.log`、`sha256.txt`）

---

## 0. 结论速览

| 用例 | 1.21.1 | 1.20.1 |
|---|---|---|
| A. 一心同体双向（非 bot 假人） | **PASS** | **PASS**（`tm=2`） |
| B1. 单人标记回归（fanE 有值 / markE=none） | **PASS** | **PASS** |
| B2. PvP 互殴（指示器不误判 −1；真粉丝 −1 仍在） | **PASS** | **PASS** |
| B3. 计数镜像不清目标标记 | **PASS** | **PASS**（抽查） |
| B4. 卸下保留他人标记 | **PASS** | **PASS** |
| B5. 客户端 HUD（金边图标等级=粉丝数） | **PASS** | 未跑（1.20.1 无 KAME-CLIENT 用例） |
| B6. 死亡保留 + 重生重建指示器 | **PASS** | 未跑（超出 1/2/4 抽查范围） |
| B7. 一心同体（并入 A） | **PASS** | **PASS** |
| 对照：`ChipDamageHandler` −1 仍生效 | **PASS**（`cutX100=100`） | **PASS**（`cutX100=100`） |

**判定口径说明（PvP）**：任务文字「B 打 A：伤害不减 1」按拆 id 缺陷 ①（攻击方只有**指示器**时不得被当成粉丝）落实为：**同队互殴**下，戴 kangel 有指示器的 Dev 与不戴立牌的 Fake3 对同一目标造成**相同伤害**（`diffX100=0`）；「真粉丝 −1 仍在」由 `seq` 臂（同一攻击方，第 2 击前施加 `kangel_fan` 真标记）与 `mit` 判据直读共同证明（规格 §8.2：PvP 粉丝 −1 同样生效）。

---

## 1. 用例 A：1.20.1 一心同体复核（非 bot 前缀假人）— **PASS**

队伍 `red`：Dev（kangel + 钻石骰）、Fake1 / Fake2（ame + 钻石骰）。判据全部来自服务端探针 `/astralkame state`。

| 断言 | 证据（`temp/kame-20261017/1.20.1/latest.log`） |
|---|---|
| `collectTeamPlayers` 对假人可见（**teamN=2**，非 0） | `24951:[14:56:20.649] AP_T0_ST:Dev:...:team=y:tm=2:...` |
| kangel 受伤 ⇒ 全队 ame 各 +1 爱 | `25255:[14:56:31.370] AP_D1_ST:Fake1:...:love=1:...`、同刻 `AP_D1_ST:Fake2:...:love=1:...` |
| ame 受伤 ⇒ 队内 kangel +1 粉丝 | `AP_D1_ST:Dev:...:fans=1:ftg=1`（14:56:31）→ `25526:[14:56:41.946] AP_D2_ST:Dev:...:fans=2:ftg=2` |
| Dev 仅得 6 防御（护甲 +12）且不攒爱 | `24951 ...:arm=12:modL=none:modK=12:...:love=0:...` |
| 减伤判据直读（ame 侧 / kangel 侧） | `25897:[14:56:55.854] AP_MIT1_MIT:who=Fake1:attacker=Fake4:active=1`；`25961 ...MIT2 Fake2 active=1`；`26054 ...MIT3 Dev active=1`；`26119 ...MIT4 Fake4 active=0`（无立牌对照） |

1.21.1 同款假人交叉复核：`temp/kame-20261017/1.21.1/latest.log`
`17806:[14:11:11.406] AP_T0_ST:Dev:...:tm=2:...:arm=12:modK=12`；`18115/18116:[14:11:22] D1 Fake1 love=1 / Dev fans=1`；`18424:[14:11:32] D2 Dev fans=2:ftg=2`；`18879:[14:11:46] MIT1 active=1`。

### 1.1 上一轮 `teamN=0` 的根因（本轮实测，已排除「bot 前缀」假设）

- **直接证据**：Carpet 假人的实际 profile 名大小写可能与命令输入不一致。`/team join blue Fake3` 写入的是 `Fake3`，而该 bot 的真实 profile 是 `FAKE3` ⇒ `getTeam()` 返回 null，`collectTeamPlayers` 看不到她。
  - `temp/kame-20261017/1.20.1/latest.log:17330 [14:51:30.322] AP_JT_TEAM:who=Fake3:profileName=FAKE3:joined=blue`
  - 同一轮早前用字面名 `/team join ... Fake3` 时，`AP_B1_ST:FAKE3:...:team=n:tm=4`（未入队）。
- **修法验证**：改用探针按 `getGameProfile().getName()` 的真实名入队后，`AP_JT_TEAM:who=Fake2:profileName=fake2:joined=red`（`23542:[14:55:26.853]`），`AP_T0_ST:Dev ... tm=2`（`24951`）。
- ⇒ 结论：**不是「bot 前缀」导致不可见**，而是「入队名 / 计分板名大小写不一致」。本轮 `Fake1`/`Fake2` 正常可见（tm=2），1.20.1 的 `collectTeamPlayers` 对非 bot 假人工作正常。

---

## 2. 用例 B：拆双 id 回归（逐项）

### 2.1 1.21.1（全做）

| # | 项 | 判定 | 一句话证据（`temp/kame-20261017/1.21.1/latest.log`） |
|---|---|---|---|
| 1 | 单人标记回归 | **PASS** | KAME-K-FAN 103/103；`AP_B1_ST:Dev ... fanE=amp=0;dur=-1;icon=1;vis=0:markE=none:` |
| 2 | PvP 互殴（指示器不误判；真粉丝 −1） | **PASS** | `4904:[14:02:37.260] AP_B1_PAIR:a1=Fake3:d1X100=161:a2=Dev:d2X100=161:diffX100=0:a1MarkAfter=none:tgtMarkAfter=none`；`5313:[14:02:57.416] AP_Q1_SEQ:...:cutX100=100`；`AP_E1_PVP ... atkMark=amp=0`（真受击入口）、`AP_E2_PVP ... tgtMark=amp=0;dur=-1` |
| 3 | 计数镜像不清标记 | **PASS** | `5751:[14:03:04.083] AP_M2_MARK ... mark=amp=0;dur=-1;...:ind=amp=1` → `5954:[14:03:13.975] AP_M2b_ST:fake2:...:fans=2:...:fanE=amp=1;dur=-1;...:markE=amp=0;dur=-1` |
| 4 | 卸下保留他人标记 | **PASS** | `6114:[14:03:20.894] AP_U1_ST:fake2:...:sign=-:...:fanE=none:markE=amp=0;dur=-1` |
| 5 | 客户端 HUD | **PASS** | `24496:[14:14:56.516] AP_KAMEC:evt=chg:tick=19128:...:list=effect.astral_dice.kangel_fan_indicator:9:-1:1:0`（KAME-CLIENT 107/107） |
| 6 | 死亡保留 / 重生重建 | **PASS** | `6671:[14:03:49.183] AP_F1_ST:Dev:...:fans=5:...:fanE=amp=4;dur=-1`（`/kill` 后重生；`F0` 同为 amp=4） |
| 7 | 一心同体双向 | **PASS** | `AP_T0_ST:Dev ... tm=2:arm=12:modK=12`、`AP_D1_ST:Fake1 love=1`、`AP_D2_ST:Dev fans=2`、`AP_MIT1/2/3 active=1` |
| 对照 | `ChipDamageHandler` −1 | **PASS** | `AP_Q1_SEQ ... d1X100=161:d2X100=61:cutX100=100` |

### 2.2 1.20.1（抽查 1 / 2 / 4；另含 3 与减伤判据）

| # | 项 | 判定 | 一句话证据（`temp/kame-20261017/1.20.1/latest.log`） |
|---|---|---|---|
| 1 | 单人标记回归 | **PASS** | `18545:[14:52:16.979] AP_B1_HIT:who=Dev:...:fan=amp=0;dur=-1`；`18645:[14:52:20.530] AP_B1_ST:Dev:...:fans=2:ftg=1:...:fanE=amp=1;...:markE=none`（fans 基线=1） |
| 2 | PvP 互殴 | **PASS** | `18747:[14:52:23.898] AP_B2_PAIR:a1=Fake3:d1X100=161:a2=Dev:d2X100=161:diffX100=0:...:tgtMarkAfter=none`；`19286:[14:52:44.116] AP_Q1_SEQ:...:cutX100=100` |
| 3 | 计数镜像不清标记 | **PASS** | `19454:[14:52:50.725] AP_M2_MARK ...` → `19722:[14:53:00.795] AP_M2b_ST:fake2:...:fanE=amp=1;...:markE=amp=0;dur=-1` |
| 4 | 卸下保留他人标记 | **PASS** | `19923:[14:53:07.624] AP_U1_ST:fake2:...:sign=-:fans=0:...:fanE=none:markE=amp=0;dur=-1` |
| 对照 | `ChipDamageHandler` −1 | **PASS** | `AP_Q1_SEQ ... cutX100=100`；`AP_MIT1/2/3 active=1`、`MIT4 active=0` |

---

## 3. 已知限制 / 说明（不判 FAIL）

1. **PvP 臂 `dmgX100=0`**：E1/E2（敌对互殴）在无骰子 + 防御 4 的骰战结算下伤害为 0；因此「−1」的数值证明统一取 `seq` 臂（同一攻击方、同一受害者，第 2 击前施加真标记）与 `mit` 判据直读，二者均通过。不是产品缺陷。
2. **1.20.1 `state` / `set`**：本轮读数正常（`T0`/`D1`/`D2` 均有输出），上一版「无输出」限制在本构建未复现。
3. **假人跨用例残留**：Carpet 假人的 `kangel_fan` / 指示器会随玩家数据跨 disconnect 保留；本轮用例 setup 用 `astralpvp clearfx`（`ModEffectRemoval` 内部通道）+ `resetfans` 归一化，保证可重跑。
4. **未实机项**：`KAME-AME-LIFE`（爱自然到期 3 次结算的已知 FAIL）不在本批范围，未复跑；1.20.1 客户端 HUD（B5）与死亡重建（B6）未在 1.20.1 抽查，按任务口径只做 1/2/4。

---

## 4. 测试资产与收尾

- 新增用例：`scripts/test/cases/KAME-PVP-1.21.1.json`、`KAME-TEAM-1.21.1.json`、`KAME-PVP-1.20.1.json`、`KAME-TEAM-1.20.1.json`
- 新增正式探针：`scripts/test/resources/kubejs/{1.21.1,1.20.1}/server_scripts/astral_kame_pvp_probe.js`（`/astralpvp pvp|seq|pair|mark|mit|clearfx|jointeam|leaveteam`；本轮临时名 `*_tmp.js` 与 `run/**` 运行副本已删除）
- 收尾（两线）：`MT_STOP: OK` + `MT_CLEANUP_SAVES: PURGED 1` + `MT_CLEANUP_RESULT: OK`；**无测试客户端残留**（仅 Gradle 守护进程 PID 31428 / 20560，非客户端）
- 本地提交（未 push）：`test(kame): 实机回归 kangel 拆双 id + 1.20.1 一心同体（非 bot 假人，.128）`；`git status --short -uall` 仅余既有 6 行（4 个 `%SystemDrive%` 缓存 + 2 个 `models/*.bbmodel`）
- 备注：提交后 `.git/hooks/post-commit` 在 WSL 下报 `pwsh: not found`（hook 未执行；提交本身成功，属环境提示）
- 证据留档（先于 stop 复制）：`temp/kame-20261017/1.21.1/{latest.log,kubejs_client.log,kubejs_server.log,sha256.txt}`、`temp/kame-20261017/1.20.1/{latest.log,kubejs_server.log,sha256.txt}`
