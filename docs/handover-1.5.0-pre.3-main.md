# 交接正本 — 1.5.0-pre.3（multi-main）

## 0 速览（一屏）

1. 分支 `multi-main`，功能末次提交 `3e200a9b`（本文档自身是其后一条 `docs(handover)` 提交，`git log -1` 应显示它），开工时 `git status --short -uall` 为空（干净）。
2. 未推送提交 **16** 个（`git rev-list --count origin/multi-main..HEAD`，本文档提交前实测；本文档提交后为 **17**，含本条），**无** `1.5.0-pre.3` tag（`git tag --list '*1.5.0-pre.3*'` 为空）。
3. 四线 `mod_version` 均为 `1.5.0-pre.3+<加载器后缀>`；`starengine_version=1.1.0-pre.2`（四线一致）。
4. jar 内硬前置：`modId="starengine"` / `versionRange="[1.1.0-pre.2,2.0.0)"`，`META-INF/jarjar/` 计数 **0**（不再内嵌库）。
5. 可交付范围：**只发 P0**（`neoforge-1.21.1` + `forge-1.20.1`）。P1 两线版本号同为 pre.3，但内容不含 1.5.0。
6. P0 双 jar 已定稿并投放两个整合包（sha256 与 `build/libs` 一致），库 jar 同名在位。
7. 守门 8 跑 7 绿；唯一红 = `verify_probe_class_refs`（既有基线条目，见 §5-1）。
8. 实机：1.20.1 本批 6/6 PASS、1.21.1 5 条 FIXBATCH 判据全命中、末条 49/49 PASS。
9. 最大风险：**创造模式扣卡修复（1.20.1 三处）尚未实机复验**；且 `run/<线>/mods` 里的 jar 与 `build/libs` 不一致（§6-1）。

## 1 环境与路径

- 仓库（唯一工作树）：`/mnt/f/MCProject/astral_dice_multiloader`，分支 `multi-main`，可直接用 WSL git。
- 四子项目同树：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`。
- 编译：`./gradlew :<线>:classes|jar`（WSL 只编译）；实机只在 Windows 侧跑，脚本一律 `pwsh -NoProfile -File <绝对路径>`。
- 测试台：`scripts/test/mt.ps1`（`--version <线> --phase env|run|stop`）。
- 整合包投放：
  - neo → `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`
  - forge → `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods`
- 库 jar 与之同目录：`starengine-neoforge-1.21.1-1.1.0-pre.2.jar`（98,733 B）/ `starengine-forge-1.20.1-1.1.0-pre.2.jar`（103,604 B）。
- 委派服务：`/mnt/c/Users/xmace/.codex/mcp/delegation/server.mjs`；日志 `…/delegation/server.log`。

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

1. **`verify_probe_class_refs` FAIL（既有基线，非本批引入）**：`docs/audit/1.3.2-release-audit-2026-09-27.md:50`
   有该门的基线定性（1.3.2 时 17 处、根因「脚本不扫 JarJar 内嵌库」）。本轮红点为 6 处不可解析，
   代表项：`mezz.jei.common.Internal`（`scripts/test/resources/kubejs/*/client_scripts/astral_shop_client.js`）、
   `org.lwjgl.glfw.GLFW`（同文件）、`net.minecraftforge.registries.ForgeRegistries`
   （`kubejs/1.20.1/server_scripts/astral_bugfix_probe.js`）、`...client.DamageNumberStore`（`astral_dnum_diag.js`）。
   原因：`run/1.21.1/mods` 未装 JEI/LWJGL 等外部件，探针引用的类不在被扫描的 5 处 classpath 内。
2. **创造模式扣卡修复未实机复验**：改动已落盘（`forge-1.20.1/.../item/card/BaseEffectCardItem.java:337`、
   `:423`，`forge-1.20.1/.../screen/CardChoiceMenu.java:134`），但 1.20.1 创造出牌仍待实机确认扣 0 张。
3. **P1 两线整体欠账**（26.1.2 / fabric 均 0 命中，1.21.1 为基准）：JEI 筹码（`compat/jei` 9 文件）、
   `BeamFx`（4）、`GlobalEventGate`（3）、`RepulsionEntity`（10）、`CardShopMenu#planPlacement`（1）、
   `EventDrawBlock`（6）。⇒ 1.5.0 正式版前不验证、不移植（用户裁决）；映射表见 `docs/compat-1.5.0-porting.md`。
4. **提交未推 + 无 tag**：`origin/multi-main..HEAD` = 16，`git tag --list '*1.5.0-pre.3*'` 为空。按用户口径默认不 push。

### Minor

5. **11 个立牌子类只写 `sign_active_cooldown_end`、不写 `setSignActiveMaxCooldown`**（`BonnieSignItem:88` 等；
   `LuluSignItem` 与 `BaseSignItem` 两处会写）。实测清单见 §7 的判据命令。
6. **26.1.2 测试环境仍是旧 jar**：`run/26.1.2/mods/astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`（10-06）。
7. **AutoJS 两个本地补丁未上游化**：根级 `pack.mcmeta`（`pack_format 15`）+ `status` 反射兜底。
8. **`testworld` 会被 `--purge-saves` 删除**：现存仅 `run/1.21.1/saves/testworld`；下轮实机前必须先 `--phase env`。
9. **`run/1.21.1/mods` 未装 JEI**：直接导致 §5-1 的部分探针不可解析（该目录仅 astral_dice 一个 jar）。

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

## 7 关键路径（命令与文件）

```bash
# 现状
git -C /mnt/f/MCProject/astral_dice_multiloader status --short -uall
git -C /mnt/f/MCProject/astral_dice_multiloader rev-parse --short HEAD
git -C /mnt/f/MCProject/astral_dice_multiloader rev-list --count origin/multi-main..HEAD

# 版本 / 依赖
grep -n 'mod_version' */gradle.properties
unzip -p neoforge-1.21.1/build/libs/astral_dice-1.5.0-pre.3+neoforge_1.21.1.jar META-INF/neoforge.mods.toml | grep -n -A4 'modId="starengine"'
unzip -l <jar> | grep -c 'META-INF/jarjar/'

# 产物一致性（三处：build/libs / run 副本 / 整合包）
sha256sum neoforge-1.21.1/build/libs/astral_dice-1.5.0-pre.3+neoforge_1.21.1.jar \
          forge-1.20.1/build/libs/astral_dice-1.5.0-pre.3+forge_1.20.1.jar
ls -l "/mnt/d/.minecraft/versions/狐の航空学 Voxy Edition/mods" | grep -Ei 'astral_dice|starengine'
ls -l "/mnt/d/.minecraft/versions/1.20.1-Forge 模组测试/mods" | grep -Ei 'astral_dice|starengine'

# P1 缺口
for f in BeamFx GlobalEventGate RepulsionEntity EventDrawBlock planPlacement; do
  grep -rl "$f" neoforge-26.1.2/src fabric-1.20.1/src | wc -l; done

# 立牌冷却口径
grep -rn 'setSignActiveCooldownEnd\|setSignActiveMaxCooldown' --include=*SignItem.java neoforge-1.21.1/src/main/java

# 收尾
pwsh -NoProfile -File scripts/test/mt.ps1 --version <线> --phase stop --force
pwsh -NoProfile -File scripts/test/mt.ps1 --version <线> --phase stop --purge-saves
python3 docs/agent/verify-split.py
```

关键文件：`docs/compat-1.5.0-porting.md`（P1 映射）、`docs/compat-1.20.1-forge.md`、
`scripts/test/TESTING-SPEC.md`、`scripts/test/cases/FIXBATCH-*.json`、
`scripts/verify/verify_probe_class_refs.ps1`、`docs/audit/1.3.2-release-audit-2026-09-27.md:50`。
