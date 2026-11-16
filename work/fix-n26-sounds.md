# neoforge-26.1.2 事件音效声明补齐（sounds.json）

- 仓库：`F:\MCProject\astral_dice_multiloader`
- 分支 / HEAD：`multi-main` / `fd990c15`
- 执行时间：2026-11-02 03:51–03:56 (+08:00)
- 唯一改动文件：`neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds.json`

## 0. 结论（一句话）

8 条 `event_*` 事件音效声明已按 `neoforge-1.21.1` 同名条目补齐（键集合与 1.21.1 完全相同、8 个 ogg 全部存在），且 `neoforge-26.1.2` 的 jar 已重新构建成功并已包含这 8 条（新 jar 2,877,947 B / sha256 `b422d57d…4878d`）。

## 1. 取证：四线键集比对

命令（`python -c`，读原始字节以同时核对行尾）：

```python
import json, os
lines = ["neoforge-1.21.1","forge-1.20.1","fabric-1.20.1","neoforge-26.1.2"]
base = "F:/MCProject/astral_dice_multiloader"
sets = {}
for l in lines:
    p = base + "/" + l + "/src/main/resources/assets/astral_dice/sounds.json"
    raw = open(p,"rb").read()
    crlf = raw.count(b"\r\n"); lf = raw.count(b"\n")
    d = json.loads(raw.decode("utf-8"))
    ks = sorted(d.keys())
    ev = sorted(k for k in ks if k.startswith("event_"))
    sets[l] = set(ks)
    print("== %-16s exists=%s bytes=%d crlf=%d lf_total=%d keys=%d event_keys=%d" % (l, os.path.exists(p), len(raw), crlf, lf, len(ks), len(ev)))
    print("   keys: " + ", ".join(ks))
ref = sets["neoforge-1.21.1"]
for l in lines:
    if l == "neoforge-1.21.1": continue
    print("-- diff vs neoforge-1.21.1 for %s" % l)
    print("   only_in_1.21.1 : " + ", ".join(sorted(ref - sets[l])))
    print("   only_in_%s: %s" % (l, ", ".join(sorted(sets[l] - ref))))
print("all four key-sets equal:", all(sets[l]==ref for l in lines))
```

原文输出：

```
== neoforge-1.21.1  exists=True bytes=1806 crlf=71 lf_total=71 keys=23 event_keys=8
   keys: boost_effect_card_use, coin_bag_withdraw, coin_withdraw, damage_effect_card_bighit, damage_effect_card_cast, damage_effect_card_hit, directional_blast_hit, effect_card_use, event_bonus, event_damage, event_fade_in, event_fade_out, event_heal, event_poisoning, event_tick, event_transport, glass_dice_broken, megas_blast, monster_brick_hit, monster_laser_hit, orbital_strike_hit, skill_use, wallet_deposit
== forge-1.20.1     exists=True bytes=1806 crlf=71 lf_total=71 keys=23 event_keys=8
   keys: boost_effect_card_use, coin_bag_withdraw, coin_withdraw, damage_effect_card_bighit, damage_effect_card_cast, damage_effect_card_hit, directional_blast_hit, effect_card_use, event_bonus, event_damage, event_fade_in, event_fade_out, event_heal, event_poisoning, event_tick, event_transport, glass_dice_broken, megas_blast, monster_brick_hit, monster_laser_hit, orbital_strike_hit, skill_use, wallet_deposit
== fabric-1.20.1    exists=True bytes=2153 crlf=117 lf_total=117 keys=23 event_keys=8
   keys: boost_effect_card_use, coin_bag_withdraw, coin_withdraw, damage_effect_card_bighit, damage_effect_card_cast, damage_effect_card_hit, directional_blast_hit, effect_card_use, event_bonus, event_damage, event_fade_in, event_fade_out, event_heal, event_poisoning, event_tick, event_transport, glass_dice_broken, megas_blast, monster_brick_hit, monster_laser_hit, orbital_strike_hit, skill_use, wallet_deposit
== neoforge-26.1.2  exists=True bytes=1465 crlf=77 lf_total=77 keys=15 event_keys=0
   keys: boost_effect_card_use, coin_bag_withdraw, coin_withdraw, damage_effect_card_bighit, damage_effect_card_cast, damage_effect_card_hit, directional_blast_hit, effect_card_use, glass_dice_broken, megas_blast, monster_brick_hit, monster_laser_hit, orbital_strike_hit, skill_use, wallet_deposit

-- diff vs neoforge-1.21.1 for forge-1.20.1
   only_in_1.21.1 : 
   only_in_forge-1.20.1: 
-- diff vs neoforge-1.21.1 for fabric-1.20.1
   only_in_1.21.1 : 
   only_in_fabric-1.20.1: 
-- diff vs neoforge-1.21.1 for neoforge-26.1.2
   only_in_1.21.1 : event_bonus, event_damage, event_fade_in, event_fade_out, event_heal, event_poisoning, event_tick, event_transport
   only_in_neoforge-26.1.2: 

all four key-sets equal: False
```

（forge-1.20.1 / fabric-1.20.1 的 `only_in_` 双向差集均为空 ⇒ 与 1.21.1 键集合完全相同；因键集相同，排序后的 keys 行内容与 1.21.1 一致。）

判定：`neoforge-26.1.2` 与其余三线的差异**恰好**是 8 条 `event_*`，**无任何额外差异**（`only_in_neoforge-26.1.2` 为空）。

旁证（只读，不改动）：`neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/audio/ModSounds.java` 第 106–113 行注册了 8 个 SoundEvent（`register("event_fade_in")` … `register("event_tick")`），与 8 个缺失声明一一对应。

## 2. 改前状态与备份

| 项 | 改前 |
|---|---|
| 文件字节 | 1,465 |
| 行尾 | CRLF（`crlf=77 / lf_total=77`，裸 LF = 0） |
| 键数 / `event_*` | 15 / 0 |
| sha256 | `6d1b4fd6642a8b2d7f98cac06f30f6ac0687f88f08d7e1bf4c914d10bea8a7bf` |

备份（`shutil.copy2`，保 mtime）：

- 生成路径：`neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds.json.bak-soundsfix-20261102T035123`
- **最终路径**：`work/sounds.json.bak-soundsfix-20261102T035123`（1,465 B，sha256 同上）

**为什么把备份移出 `src/main/resources`**：`neoforge-26.1.2/build.gradle` 的 `sourceSets.main.resources` 只 `exclude` 了 `**/*.bbmodel` / `**/.cache` / `INSTRUCTIONS.md` / `mc-src/**`，没有 `*.bak*` 排除项 ⇒ 备份若留在 `assets/astral_dice/` 会被 `processResources` 原样打进 jar。移到 `work/` 后已用 jar 内断言复核：`any .bak inside jar: []`。

## 3. 改动

### 3.1 风格决策

1.21.1 的 8 条是**紧凑内联**写法（`"sounds": ["astral_dice:event_bonus"]` 一行），而本文件既有 15 条是**多行**写法（`"sounds": [` / 6 空格元素 / `]`），且 `coin_bag_withdraw` 带 `{"name": ..., "volume": 0.6}`。

按任务要求「逐字节复制该 8 条的值结构，同时保持本文件既有的缩进风格与行尾」，采用：**值结构与 1.21.1 完全一致（`sounds` 为单元素字符串数组、元素为 `astral_dice:<key>`），排版沿用本文件的多行 2/4/6 空格缩进，行尾 CRLF**，且 8 条的键序取 1.21.1 文件中的出现顺序（`event_bonus, event_damage, event_fade_in, event_fade_out, event_heal, event_poisoning, event_tick, event_transport`）。

程序化约束（改前后都已断言，避免手抄）：

- 键名/value 直接由 `json.load(1.21.1)` 解析结果生成，并断言 `list(v.keys()) == ["sounds"]`、`len(v["sounds"]) == 1`、`v["sounds"][0] == "astral_dice:" + k`；
- 插入锚点 `"astral_dice:effect_card_use"\r\n    ]\r\n  },\r\n` 命中次数 `== 1`；
- 断言改后 `new.count("\r\n") == old.count("\r\n") + 40` 且 `new.count("\n") == new.count("\r\n")`（无裸 LF）。

插入位置：`effect_card_use` 之后、`skill_use` 之前（与 1.21.1 中 `event_*` 紧随 `effect_card_use` 的相对位置一致）。结果是**纯插入 40 行、0 行删除/修改**，其余 15 条一字未动。

### 3.2 diff（逐条，`git diff` 原文）

```diff
diff --git a/neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds.json b/neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds.json
index 3d8172ca..db863d50 100644
--- a/neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds.json
+++ b/neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds.json
@@ -34,6 +34,46 @@
       "astral_dice:effect_card_use"
     ]
   },
+  "event_bonus": {
+    "sounds": [
+      "astral_dice:event_bonus"
+    ]
+  },
+  "event_damage": {
+    "sounds": [
+      "astral_dice:event_damage"
+    ]
+  },
+  "event_fade_in": {
+    "sounds": [
+      "astral_dice:event_fade_in"
+    ]
+  },
+  "event_fade_out": {
+    "sounds": [
+      "astral_dice:event_fade_out"
+    ]
+  },
+  "event_heal": {
+    "sounds": [
+      "astral_dice:event_heal"
+    ]
+  },
+  "event_poisoning": {
+    "sounds": [
+      "astral_dice:event_poisoning"
+    ]
+  },
+  "event_tick": {
+    "sounds": [
+      "astral_dice:event_tick"
+    ]
+  },
+  "event_transport": {
+    "sounds": [
+      "astral_dice:event_transport"
+    ]
+  },
   "skill_use": {
     "sounds": [
       "astral_dice:skill_use"
```

逐条（8 条新增，各 5 行）：`event_bonus` / `event_damage` / `event_fade_in` / `event_fade_out` / `event_heal` / `event_poisoning` / `event_tick` / `event_transport`，每条 `"sounds": ["astral_dice:<同名 id>"]`。

### 3.3 改后文件

| 项 | 改后 |
|---|---|
| 文件字节 | 2,153（+688） |
| 行尾 | CRLF，`crlf=117 / lf=117`，裸 LF = 0，文件以 CRLF 结尾 |
| 键数 / `event_*` | 23 / 8 |
| sha256 | `1c250a316f9b088dbc9bdea67836ac89d3c797a529a1365e1972f4e0f98d8a53` |

## 4. 校验输出

### 4.1 JSON 语法

```
$ python -m json.tool neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds.json > /dev/null && echo "json.tool OK exit=$?"
json.tool OK exit=0
```

### 4.2 键数 / 集合相等 / 值结构 / ogg 存在性（`python -c` 断言，原文输出）

```
key count: 23
event_ count: 8
event_ keys: ['event_bonus', 'event_damage', 'event_fade_in', 'event_fade_out', 'event_heal', 'event_poisoning', 'event_tick', 'event_transport']
set == 1.21.1 set: True
event value structures identical to 1.21.1 (per key): True
non-event values untouched (identical to 1.21.1 where comparable):  {'coin_bag_withdraw': False}

ogg existence assertions (event_*):
   os.path.exists  neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds/event_bonus.ogg -> True
   os.path.exists  neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds/event_damage.ogg -> True
   os.path.exists  neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds/event_fade_in.ogg -> True
   os.path.exists  neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds/event_fade_out.ogg -> True
   os.path.exists  neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds/event_heal.ogg -> True
   os.path.exists  neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds/event_poisoning.ogg -> True
   os.path.exists  neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds/event_tick.ogg -> True
   os.path.exists  neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds/event_transport.ogg -> True
all 8 event_* ogg assertions passed

ogg existence (all 23 refs):
   refs checked: 23  missing: []

file bytes: 2153 crlf: 117 lf: 117 bare LF: 0
ends with CRLF: True
sha256: 1c250a316f9b088dbc9bdea67836ac89d3c797a529a1365e1972f4e0f98d8a53
byte-identical to fabric-1.20.1 sounds.json: False
```

说明：`coin_bag_withdraw: False` 是**改前既有的平台差异**（本线 `{"name": "astral_dice:coin_bag_withdraw", "volume": 0.6}`，1.21.1 为纯字符串），不在本次授权范围，未改动、已在此报告。`byte-identical to fabric` 为 False 亦仅因键序不同（同为 23 键 2,153 B），非缺陷。

## 5. 构建

命令（pwsh 7.6.9，`pwsh -NoProfile -File work/_n26_build.ps1`；脚本内容见 `work/_n26_build.ps1`）：

```powershell
$ErrorActionPreference = 'Continue'
Set-Location -LiteralPath 'F:\MCProject\astral_dice_multiloader'
$log = 'F:\MCProject\astral_dice_multiloader\work\_n26_build.log'
& .\gradlew.bat ':neoforge-26.1.2:build' -x ':neoforge-26.1.2:pushToGame' -x ':neoforge-26.1.2:pushToDevRun' -x ':neoforge-26.1.2:pushToRootBuild' --console=plain 2>&1 | Tee-Object -FilePath $log
exit $LASTEXITCODE
```

**排除任务的依据（为何必须加 `-x`）**：`neoforge-26.1.2/build.gradle:505-511` 中 `tasks.named('build')` 有 `finalizedBy pushToDevRun / pushToGame / pushToRootBuild`，而 `pushToGame` 会把 jar 写到 `D:/.minecraft/...`（构建日志首行也确认 `整合包根目录 (MC_HOME): D:/.minecraft`）。本次显式排除上述 3 个 finalizer，**未推送任何整合包 / 根 build/ 目录**（日志中无 `pushTo*` 行）。

结果尾部（`work/_n26_build.log` 原文）：

```
> Task :neoforge-26.1.2:processTestResources NO-SOURCE
> Task :neoforge-26.1.2:generateModMetadata UP-TO-DATE
> Task :neoforge-26.1.2:jarJar UP-TO-DATE
> Task :neoforge-26.1.2:createMinecraftArtifacts UP-TO-DATE
> Task :neoforge-26.1.2:processResources
> Task :neoforge-26.1.2:compileJava
> Task :neoforge-26.1.2:classes
> Task :neoforge-26.1.2:compileTestJava NO-SOURCE
> Task :neoforge-26.1.2:testClasses UP-TO-DATE
> Task :neoforge-26.1.2:test NO-SOURCE
> Task :neoforge-26.1.2:check UP-TO-DATE
> Task :neoforge-26.1.2:jar
> Task :neoforge-26.1.2:assemble
> Task :neoforge-26.1.2:build

[Incubating] Problems report is available at: file:///F:/MCProject/astral_dice_multiloader/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.5.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 3s
6 actionable tasks: 3 executed, 3 up-to-date
Configuration cache entry stored.
EXITCODE=0
```

（`processResources` 因 sounds.json 变化重跑，`jar` 随之下线重打；`compileJava` 无改动源码但参与了重编。）

## 6. 产物核验（jar 内断言）

目标产物：`neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar`（新，mtime 2026-11-02 03:51）

```
jar bytes: 2877947
jar sha256: b422d57d2103219522eef792822da1656a1f9e197f91c6b0d8e82d381ec4878d
contains assets/astral_dice/sounds.json : True
jar sounds.json key count: 23
jar event_ count: 8
jar event_ keys: ['event_bonus', 'event_damage', 'event_fade_in', 'event_fade_out', 'event_heal', 'event_poisoning', 'event_tick', 'event_transport']
jar event_* value assertion: OK (each sounds[0] == astral_dice:<key>)
jar CRLF preserved: 117 of 117
any .bak inside jar: []
ogg present in jar (event_*):
    assets/astral_dice/sounds/event_bonus.ogg True
    assets/astral_dice/sounds/event_damage.ogg True
    assets/astral_dice/sounds/event_fade_in.ogg True
    assets/astral_dice/sounds/event_fade_out.ogg True
    assets/astral_dice/sounds/event_heal.ogg True
    assets/astral_dice/sounds/event_poisoning.ogg True
    assets/astral_dice/sounds/event_tick.ogg True
    assets/astral_dice/sounds/event_transport.ogg True
```

防回退断言（`zh_cn.json` 内的物品名仍为 `深渊试剂`）：

```
zh_cn item.astral_dice.abyss_reagent_chip = 深渊试剂 | codepoints: ['0x6df1', '0x6e0a', '0x8bd5', '0x5242']
equals 深渊试剂: True
source zh_cn same value: True
```

jar 内条目与源文件逐字节一致（排除资源过滤改写）：

```
jar entry bytes: 2153  sha256: 1c250a316f9b088dbc9bdea67836ac89d3c797a529a1365e1972f4e0f98d8a53
source    bytes: 2153  sha256: 1c250a316f9b088dbc9bdea67836ac89d3c797a529a1365e1972f4e0f98d8a53
jar entry == source bytes: True
```

参考（非本改动单独可归因）：上一次推到仓库根 `build/libs` 的同名 jar 为 2,875,237 B（02:01），本次为 2,877,947 B（+2,710 B）；期间 `lang/*.json` 有其它 agent 的并行改动，故 jar 体量差不能全部算作本次 sounds.json 的贡献（本文件自身 +688 B）。

## 7. 静态自查

```
$ git diff --stat -- neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds.json
 .../main/resources/assets/astral_dice/sounds.json  | 40 ++++++++++++++++++++++
 1 file changed, 40 insertions(+)
```

```
$ git status --porcelain=v1 -- neoforge-26.1.2/src/main/resources/assets/astral_dice/
 M neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/en_us.json
 M neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/ja_jp.json
 M neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/zh_cn.json
 M neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds.json
```

- 本次 diff **只有这 1 个文件、40 行纯新增、0 删除**，未夹带其它文件。
- 上面 3 个 `lang/*.json` 的 `M` 在**本次开工前就已存在**（开工首次 `git status` 已见），属其它 agent 的并行改动，本次未读写其内容以外的任何操作（仅只读用于 `abyss_reagent_chip` 防回退核对）。
- `scripts/test/**` 全程未触碰（该目录当前有大量他人 `M`/`??` 改动，无冲突）。

## 8. 纪律执行记录

- 只改 `neoforge-26.1.2/src/main/resources/assets/astral_dice/sounds.json` 一个 json；未碰源码 / lang / 其它三线 / `build.gradle` / `run/**` / `scripts/test/**`。
- 未执行 `git add` / `commit` / `push` / `tag`。
- 未启动游戏、未占端口、未跑实机用例。
- 未执行 `pushToGame` / `pushToDevRun` / `pushToRootBuild`（已 `-x` 显式排除），未写 `D:` 盘。
- 新增的辅助/证据文件（均在 `work/`，非源码树）：`work/fix-n26-sounds.md`（本报告）、`work/_n26_build.ps1`、`work/_n26_build.log`、`work/sounds.json.bak-soundsfix-20261102T035123`（改前备份）。
- 互斥锁：开工时 `F:/MCProject/.agent-locks/` 存在 `dsh.lock`（`PID=26899 / TIME=2026-10-31T23:19:07+08:00 / TASK=2612-live-verify`），为两日前同宿主任务留下的陈旧锁；本次未创建、未删除任何锁文件。若后续需严格互斥，请由锁属主清理该陈旧锁。
