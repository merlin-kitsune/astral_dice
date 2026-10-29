# 交接 —— sykestentacle 动画实体批（2026-10-29，dev 线）

> 工作树 `F:/MCProject/astral_dice_multiloader-next`，分支 `multi-dev-next`。本批之前的 HEAD = `9be228f6`（`26.4-SNAPSHOT.3`）；本批四线版本号 +1 到 `26.4-SNAPSHOT.4`，**未 push**（用户 2026-10-29 裁决）。
> 本文件 = 本批的交接正本。线级快照见上一份 `docs/handover-2026-10-29-dev.md`；规则正本见 `docs/agent/AGENTS-dsh.md`；功能规格见 `docs/features/sykestentacle-animation-spec.md`。

## 1. 用户裁决（按时间顺序，逐条可追溯）

| # | 裁决 | 落点 |
|---|---|---|
| 1 | 「更新 sykestentacle 的 bbmodel 的内容」→ 二次确认为 A 选项：按 bbmodel 重导 geo + anim 并更新四线资源，**不动 bbmodel 本身** | §3 |
| 2 | 「将 26.1.2 和 fabric 设置为（搁置）线，只完成 forge 和 neo 线。待上游将 1.5.0 完整移植 26.1.2 和 fabric 并合并 dev 之后再跟进 P0」 | §5 |
| 3 | 「不做 push，其余点头」：刷新 spec 文档 / P1 搁置写进文档留痕 / 四线 +1 到 `26.4-SNAPSHOT.4` 并本地提交 | §4 §5 §6 |

> 裁决 2 的「再跟进 P0」按仓内口径读作「这两条线暂不追 P0 功能对齐」——P0 指 `neoforge-1.21.1` + `forge-1.20.1`（`docs/AGENT-HANDOFF.md:163-164`、`docs/agent/AGENTS-dsh.md` L532–L582），P1 指 `neoforge-26.1.2` + `fabric-1.20.1`。

## 2. 本批改了什么（四线同套；P0 已构建验收，P1 仅落盘）

- **GeckoLib 硬前置**：四线 `build.gradle`（Modrinth version id：neo 1.21.1 `Grwn5rUB`、forge 1.20.1 `aC5KMoNg`、fabric 1.20.1 `SD2lOScS`、26.1.2 `xfVfPcoC`）+ 元数据区间；不 JarJar 内嵌，由玩家自备。
- **实体 `astral_dice:sykestentacle`**：`entity/SykeTentacleEntity.java`、`client/SykeTentacleModel.java`、`client/SykeTentacleRenderer.java`（四线新建）；注册走 `entity/ModEntityTypes.java` / `event/ModEntityEvents.java` / `client/ModClientEvents.java`；四份元数据模板（`META-INF/{mods.toml,neoforge.mods.toml}`、`fabric.mod.json`）加 geckolib 依赖。
- **资源**：`assets/astral_dice/{geo/sykestentacle.geo.json, animations/sykestentacle.animation.json, textures/entity/sykestentacle.png}`（四线）。
- **文档**：`README.md` / `README_ZH.md` 前置表、`docs/features/sykestentacle-animation-spec.md` 刷新、两份 CHANGELOG 新增 `26.4-SNAPSHOT.4`。
- **未触碰**：`models/**`（用户素材，全程不 stage）；starengine 版本、fabric 区间、两线 `versionRange` 上界、CI 库 ref（本批无库变更，均不动）。

## 3. 重导结论（关键：以后别再往错的方向修）

| 项 | 结论 |
|---|---|
| geo `models/sykestentacle/models/main.geo.json` | **需要重导，已重导**。新旧**唯一**差异 = 新增空父骨 `bone`（`parent: root`、`pivot [0,0,0]`、无 cube；`bone2` 改为挂在它下面）。共用 21 根骨**逐值 0 处差异**，97 cubes、desc 全同。旧 geo 缺这根骨 ⇒ 新 `animation.tentacleattack` 的 `bone` 轨会被 GeckoLib **静默丢弃**（不报错，只少一段运动），这才是本轮真正的修复点。 |
| anim `models/sykestentacle/animations/default.animation.json` | **不需要重写**。重编译 vs 用户 21:02 那份导出：`tentacleidle` 差 0、`tentacleattack` 最大 \|Δ\| = 1e-5（导出只保留 6 位有效数字，纯舍入）⇒ 21:02 那份就是等价导出，源文件保持不变。 |
| geo/anim 的 X 镜像（spec §4） | **不是导出过期**。重导前后 geo 逐值一致；`bedrock` 导出时整模 X 镜像（bbmodel cube `root` `from.x=-17.8` / `to.x=21.4` → geo `origin.x=-21.4`（= −`to.x`）/ `size.x=39.2`），而动画关键帧不镜像。**重导修不好这一对**：只能实机看左右倾向，或在 Blockbench 里刻意镜像模型后再导。 |
| 新增不变量 | `动画骨名 ⊆ 几何骨名`（geo 22 骨 / idle 21 / attack 22，四线缺失集合为空）。自查脚本见 §7。 |

## 4. 证据（P0 两线，构建于 2026-10-29 22:02）

命令：`.\gradlew.bat --console=plain :neoforge-1.21.1:jar :forge-1.20.1:jar` ⇒ `BUILD SUCCESSFUL in 7s`，`11 actionable tasks: 7 executed, 4 up-to-date`，`EXITCODE=0`。

| 线 | 产物（`<线>/build/libs/`） | 字节 | sha256 |
|---|---|---|---|
| neoforge-1.21.1 | `astral_dice-26.4-SNAPSHOT.4+neoforge_1.21.1.jar` | 2 648 410 | `8313d731891c3b4279fd538942e9202c070905a7dc42efa8171c1f49fbf25159` |
| forge-1.20.1 | `astral_dice-26.4-SNAPSHOT.4+forge_1.20.1.jar` | 2 697 589 | `8a484c22aa6a187497965ec05960f4a9b89085decc57f16181169702fcf9e642` |

- **jar 内资源**：geo 51 417 B / 归一化 sha `9c7ceed1b811d089`、anim 93 726 B / `e26c5ec3c5a30811`、png 5 647 B / `89a8da3f74672df8`（三者均与 `models/` 源一致，json 为 CRLF、裸 LF = 0、无 BOM）；`SykeTentacleEntity` / `SykeTentacleModel` / `SykeTentacleRenderer` 三类两线齐全。
- **元数据**：`version=26.4-SNAPSHOT.4+<后缀>`；依赖 modId（neo）= `astral_dice / curios / enigmaticlegacyplus / **geckolib** / minecraft / neoforge / patchouli / starengine`，（forge）= `astral_dice / curios / forge / **geckolib** / minecraft / mixinbooster / starengine`。
- **守门**：`scripts/verify/verify_resource_integrity.ps1 -Version 1.21.1` 与 `-Version 1.20.1` ⇒ 均 **RESULT: PASS**（157/157 物品模型 + 贴图，读的是 `.4` jar）；`tools/check_lang_sync.ps1` ⇒ **exit 0**（P0 两线 zh/en/ja 各 1067 键一致；3 条**既有** WARN 在 `tooltip.astral_dice.sign.{jill_active,dorothy_passive}`，与本批无关）。

## 5. P1 两线现状（搁置 —— 别当缺陷报）

- 代码与资源已与 P0 同套落盘（随本批一起提交），但**不构建、不验证**。
- `neoforge-26.1.2` / `fabric-1.20.1` 的 `build/libs` 仍是 2026-10-29 19:16 的 `.3` 产物，**内含旧 geo/anim**（旧 geo 缺 `bone` 骨）。
- 复活入口（上游把 1.5.0 完整移植进这两线并合并 dev 之后）：① 按 `docs/compat-1.5.0-porting.md` 对差；② 用 §3 的结论核 geo/anim（**直接沿用，不要再重导**）；③ 跑 `:neoforge-26.1.2:jar` / `:fabric-1.20.1:remapJar` + 两条守门；④ 版本号随当时批次再 +1。**P1 产物仍禁止发布。**

## 6. 欠账 / 下一步

1. **实机未验**（红线：默认不启动）。要验必须用户明确下达，走 `scripts/test/mt.ps1` 阶段化测试台，收尾 `--phase stop --purge-saves`；测试靶不得用亡灵生物。
2. geo/anim 的 X 镜像取向仍只有静态结论，需实机看左右倾向（尤其 `animation.tentacleattack` 的主挥方向）。
3. 根 `build/libs` 未刷新：本批只跑 `:jar`，交付前需再跑 `:pushToRootBuild`（否则根目录那份还是旧的，且不含本批资源）。
4. 未 push：本地领先 `origin/multi-dev-next` 的提交数已变（本批 +1），远程同步由用户另行决定（走本地代理 `http://127.0.0.1:7897`）。

## 7. 环境坑（本批踩过，别重复）

- **这块 F: 盘上不要「先查后写」**：`os.path.isfile` / `find` 对已存在的文件会返回不存在（实测 4 个既有动画副本被误判，导致覆盖前没进备份；已从 `.3` jar 里补齐到 `F:/MCProject/.bbmodel-backup/pre-reexport/`）。要写就直接写 + 事后校验。
- **根 `build/libs` 与各线 `build/libs` 不是一回事**：根目录是 `pushToRootBuild` 的产物（2026-10-28 21:00，不含 sykestentacle 资源）。判产物只认 `<线>/build/libs/`。
- **Blockbench MCP**：必须协商 `2025-06-18` 才有 `Codecs` / 导出面；`Codecs.bedrock.compile({}, 'main.geo.json')` 返回**字符串**，`Animation.prototype.compileBedrockAnimation()` 返回**对象**（外层 `{format_version:'1.8.0', animations:{...}}` 需自己拼）；载入用 `Codecs.project.load(json, {path, no_file:true})`，不改动源文件。
- **守门入口**：资源完整性 `scripts/verify/verify_resource_integrity.ps1 -Version <1.21.1|1.20.1>`；语言一致性 `tools/check_lang_sync.ps1`；四线资源 sha + 骨名覆盖自查 `F:/MCProject/.bbmodel-backup/verify.py`（仓外）。
- **Gradle 只走 PowerShell 通道**：本会话里 `pwsh` 工具被路由到 bash，实际以 `powershell.exe -NoProfile -Command` 调起（等价，命令体内仍是用 `gradlew.bat`）。
