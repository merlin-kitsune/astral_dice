# 工程参考（PROJECT-REFERENCE）

> 面向所有宿主（Kimi Code / DSH / Codex）的**工程拓扑速查**。规则文件与红线不在这里：公共分派见根 `AGENTS.md`，宿主规则见 `docs/agent/AGENTS-kimi.md` / `docs/agent/AGENTS-dsh.md` / `docs/agent/AGENT-SPLIT.md`。
> 数字均为 2026-10-24 快照，**一律以实读命令为准**。

## 1. 仓库与分支

- 工作树：`F:\MCProject\astral_dice_multiloader`（**单工作树四子项目同树**），分支 `multi-main`。
- 前置库源码：`F:\MCProject\starengine`（独立仓，分支 `main`，产物发布到 mavenLocal 供本仓消费）。
- 另一个常驻工作树：`F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`，2.0.0 开发线）—— **与本树互不镜像**。
- `F:\MCProject\.agent-locks\`：宿主互斥锁目录（`kimi.lock` / `dsh.lock`，内容为 `PID=` / `TIME=` / `TASK=` 三行）。

## 2. 四条线基线

| 子项目 | MC | 加载器 | Java | 构建插件 | 交付优先级 |
|---|---|---|---|---|---|
| `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | 21 | ModDevGradle `net.neoforged.moddev` | **P0** |
| `forge-1.20.1` | 1.20.1 | Forge 47.4.10 | 17 | `net.neoforged.moddev.legacyforge` | **P0** |
| `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | 25 | ModDevGradle | P1（`1.5.0` 正式版前不验证、不移植） |
| `fabric-1.20.1` | 1.20.1 | Fabric Loader 0.19.5 + FAPI 0.92.12 | 17 | Fabric Loom | P1（同上） |

- 版本：四线 `mod_version = 1.5.0-pre.6+<加载器后缀>`（`neoforge_1.21.1` / `forge_1.20.1` / `neoforge_26.1.2` / `fabric_1.20.1`，下划线分隔）；四线 `starengine_version = 1.1.0-pre.2`（fabric 另有 `starengine_version_range`）。
- jar 内硬前置区间 `[1.1.0-pre.2,2.0.0)`，`META-INF/jarjar/` 计数 0（库**不内嵌**，必须成对投放）。
- 包根：`com.merlinkitsune.astral_dice`；库包根 `com.merlinkitsune.starengine`。

## 3. 目录职责

| 路径 | 职责 |
|---|---|
| `<子项目>/src/main/java` | 产品源码（`item/` 物品与立牌/筹码 · `combat/` 骰战结算 · `event/` 事件订阅 · `effect/` 状态效果 · `shop/` 卡牌与筹码商店 · `target/` 目标选择 · `resource/` 星光/治愈/充能 · `entity/` 自定义实体） |
| `<子项目>/src/main/resources/assets/astral_dice/lang/` | 三语 `zh_cn.json` / `en_us.json` / `ja_jp.json`（P0 各 1,046 键；UTF-8 无 BOM、CRLF、一行一键、2 空格缩进） |
| `<子项目>/src/generated/resources` | datagen 产物（`recipe*` / `recipes` 注意 neo 用 `recipe/`、forge 用 `recipes/`） |
| `scripts/test/` | 测试台（`mt.ps1` 唯一入口 + `cases/*.json` + `resources/kubejs/` 探针 + `TESTING-SPEC.md`） |
| `scripts/verify/` · `tools/` | 守门脚本（见 §5） |
| `docs/` | 交接正本 `handover-*.md`、规则库 `docs/agent/`、批次记录 `docs/features/`、兼容映射 `docs/compat-*.md` |
| `run/<线>/` | dev 运行目录（`mods/` 由构建任务投放；`--purge-saves` 会删 testworld） |
| `build/libs/` | 产物（唯一交付来源）；`build/devlibs/` forge 的 **named** jar 供 dev 用 |
| `models/` `images/` `sound/` | 用户素材目录，**随时更新**，不得擅自 add/commit/改 |

## 4. 常用命令（Windows 原生）

```powershell
# 状态（收尾应只剩用户素材）
git -C F:\MCProject\astral_dice_multiloader status --short -uall
git -C F:\MCProject\astral_dice_multiloader log --oneline -5

# 编译 / 产物（JAVA_HOME = C:\Program Files\Zulu\zulu-21）
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
.\gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes --console=plain
.\gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar --console=plain
.\gradlew.bat :neoforge-1.21.1:runData          # datagen（改配方/物品后必须）

# 测试台（唯一入口，禁 bash/.sh/PS 5.1）
pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase env
pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.20.1 --phase run --case <用例名>
pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.20.1 --phase stop --force --purge-saves
```

- forge dev 必须用 `build/devlibs` 的 **named** jar（`AutoJS-forge-1.20.1-1.0.0-dev.jar` 同理），与整合包用的 reobf jar **不可同目录**。
- `--purge-saves` 会删 `testworld`：下轮实机前先 `--phase env`。
- 禁用 Windows PowerShell 5.1（不支持三元 / `??` / `-AsHashtable`）。

## 5. 守门脚本（P0 两线必跑）

| 脚本 | 判据 |
|---|---|
| `tools/check_lang_sync.ps1` | 四线 key 集一致 + 颜色码/占位符结构一致（WARN 级） |
| `tools/audit_patchouli_keys.py` | 手册引用 1,694 处，悬空必须 0 |
| `docs/agent/verify-split.py` | 规则文件四项自检（快照保序包含 / AGENTS.md 结构 / docs 引用 / 体量） |
| `scripts/audit/tooltip_color_audit.ps1` | tooltip 染色规则（R0 回落码 = 行底色 · R1 数值 §e · R2 时间 §9 · R3 效果名+时间整段蓝） |
| `scripts/verify/verify_chip_shop_ui.ps1` | 筹码商店帧/逻辑（G1..G18h） |
| `scripts/verify/verify_probe_class_refs.ps1` | 探针 javaclass 引用可解析（既有 1 红点 = 26.1.2 产物陈旧） |
| `tools/verify_misaki_balance.py` · `verify_bonnie_active_order.py` · `verify_member_recommendation_hand_gate.py` | 2026-10-24 批次的三道专项判据 |

## 6. 投放目标（成对：mod jar + `starengine-*-1.1.0-pre.2.jar`）

- neo → `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`
- forge → `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods`
- dev → `run\1.21.1\mods` / `run\1.20.1\mods`（forge 用 named jar）

缺库即启动失败 `Missing or unsupported mandatory dependencies`。投放后必须逐份核 sha256。

## 7. 交接文件

- DSH 滚动正本：`docs/handover-1.5.0-pre.6-main.md`（pre.5 及更早：`docs/handover-1.5.0-pre.5-main.md`）。
- 新会话寒暄提示词：`docs/handover-new-session-context.md`。
- Kimi 接手：`docs/handover-<日期>-kimi-<主题>.md`（每批新建）。
- 逐批历史：`docs/agent/AGENTS-full.md` 末尾「待办交接各批」（只追加不改旧条）。
