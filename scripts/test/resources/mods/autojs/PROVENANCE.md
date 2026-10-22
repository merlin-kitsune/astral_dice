# AutoJS 测试库来源记录（PROVENANCE）

本文件记录测试库类 jar 的来源与本地分发坐标；各投放步骤只**追加**自己的记录，不覆盖他人内容。

### AutoJS Maven 分发记录（publish_autojs_maven.ps1）

由 scripts/test/tools/publish_autojs_maven.ps1 写入（本地 mavenLocal 分发；**不是** Curse/Modrinth 发布件）。

- `cc.sighs.autojs:AutoJS-forge-1.20.1:1.0.0`
  - jar: `scripts/test/resources/mods/autojs/AutoJS-forge-1.20.1-1.0.0.jar`（SHA256 `3fc90d40204d87aae097016a99cce3eb0f9a497a62c2f4113bd3e15dd3680987`）
  - 构建来源: `temp/AutoJS-src/targets/forge-1.20.1/build/libs/AutoJS-forge-1.20.1-1.0.0.jar`
  - 装载方式: ModDevGradle legacyforge: modRuntimeOnly 解析期 SRG->named
- `cc.sighs.autojs:AutoJS-fabric-1.20.1:1.0.0`
  - jar: `scripts/test/resources/mods/autojs/AutoJS-fabric-1.20.1-1.0.0.jar`（SHA256 `66fc361f4a030bc43bf152d29cbc24ca0860bb8d86d46ee71127fc83c661cda6`）
  - 构建来源: `temp/AutoJS-src/targets/fabric-1.20.1/build/libs/AutoJS-fabric-1.20.1-1.0.0.jar`
  - 装载方式: Fabric Loom: modRuntimeOnly 解析期 intermediary->named


### AutoJS vendor 记录（mt_env.ps1 的手投放路线，2026-10-17 批次）

由 `scripts/test/mt_env.ps1` 的 `Install-MtAutoJs` 使用：把 jar 直接复制进 `run/<版本>/mods`，
**不经** Maven（`publish_autojs_maven.ps1` 那条路走的是 mavenLocal + modRuntimeOnly，见上节）。

- 上游仓库：https://github.com/MerrySnow404/AutoJS
- 许可证：**GPL-3.0**（jar 内 `META-INF/neoforge.mods.toml` 的 `license` 字段同为 GPL-3.0）
- 克隆提交：`eb4d971d088cb2bde119a8393f97e8209a8702d5`（2026-10-17，`Expand AutoJS testing guidance`）
  —— 本地克隆目录 `temp/AutoJS-src`（**不入库**）
- 构建命令（在 `temp/AutoJS-src/targets/<target>` 下）：
  `.\gradlew.bat --no-daemon --console=plain build`（不传 `-Pautojs.localToolchain`，见 spec §1）
- **为什么不从 CurseForge / Modrinth 下载**：上游不发布现成 jar（没有 CurseForge / Modrinth 项目，
  也没有 GitHub Release 资产），只能从源码构建；而测试台要求在仓库内就能拿到**确定的**产物
  （否则每台机器 / 每次 CI 都得先能构建仓库之外的工程）。因此把构建产物当只读测试资产管理，
  钉死来源与哈希。

可直接手投放（生产映射 mojmap）的两条线：

| 文件 | 字节 | SHA256 | 投放目标 |
| --- | --- | --- | --- |
| `AutoJS-neoforge-1.21.1-1.0.0.jar` | 163541 | `75eae0435372ecda34c18d15cfd175c5fa9dd8efe6517c51ce7f1d84c0062688` | `run/1.21.1/mods` |
| `AutoJS-neoforge-26.1-1.0.0.jar` | 164568 | `28e24fa867ea1493cc887e1a36248f7ee7260b73357b1848364dfd2694161144` | `run/26.1.2/mods` |

`mt_preflight.ps1` 的 `MT_PREFLIGHT_AUTOJS` 会逐个复核上表的 SHA256。

前置（rhino）：AutoJS 在自身 `mods.toml` 里把 `rhino` 声明为 **required**（两线 `versionRange` 均为
`[2101,)`），故**不额外 vendor rhino** —— 两条线的 `run/<版本>/mods` 里本来就有 KubeJS 带来的
`rhino-2101.2.8-build.91.jar`，重复投放会触发 FML `Duplicate mods:` 拒绝启动。
`Install-MtAutoJs` 投放后会复核「恰好一个 rhino 且满足区间」（区间从 AutoJS 的 jar 内读取，不硬编码）。

## forge-1.20.1 双产物口径（2026-10-22，dev=named / 整合包=reobf）

- **dev 运行**（`run/1.20.1/mods`）用 `build/devlibs/` 的 **named** jar，落地文件名 `*-dev.jar`。
- **整合包**（`1.20.1-Forge 模组测试/mods`）用 `build/libs/` 的 **reobf** jar。
- 两者**不可同目录共存**（同 modId 两份 ⇒ duplicate mod 拒绝启动），旧 jar 只能改名保留（`.jar.bak-*` / `.jar.reobf-*`）。
- 依据：`forgeclientuserdev` 运行环境发布**官方名（Mojmap）**，而 `build/libs` 是 reobf（SRG）——
  jar 内按 SRG 名调用 MC 的类会抛 `NoSuchMethodError`；`Error` 不被 MCP 的 `catch(Exception)` 兜住，
  故表现为启动期硬失败。取证：reobf jar 362 处 `m_xxxxx_` 调用点，devlibs jar 0 处。

