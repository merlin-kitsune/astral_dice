# 26.1.2 —— 需第二玩家的用例（当前无法执行）

这 12 条用例**与 1.21.1 / 1.20.1 两线成对存在、断言逐字相同**，但在 26.1.2 线上
**当前无法执行**，因此**不放在 `cases/` 下**（`mt_case.ps1` 的目录枚举是非递归的
`Get-ChildItem -LiteralPath <dir> -Filter *.json`，子目录不会被扫到）。

> 结论摘要：**26.1.2 这条 MC×加载器组合上，目前拿不到「第二玩家」能力。**
> 这不是流程疏漏，而是三条独立路径全部走不通（见下）。除非上游发生变化，
> 这 12 条只能以 BLOCKED 记账，**不得**改写成单人简化版（那是改断言迁就环境）。

## 这 12 条用例在验什么

用 `/player Bot1 spawn at ~2 ~ ~` 造一个**真 `ServerPlayer`** 作第二玩家，再用
`/clear Bot1`、`/astralprobe … Bot1` 等**按玩家名**取它。判据本身以第二玩家为前提
（治疗目标、非同队玩家可打、厄运层数按持有者计…），单人版验的是另一件事。

清单：BOT-2P / FUHUO-CARD / LULU-SIGN / MAMUSHI-ACTIVE / MAMUSHI-COMBAT / MAMUSHI-DRAGON /
MAMUSHI-GUARD / MAMUSHI-PASSIVE / MAMUSHI-REG / TERU-EXTRA-ATTACK / TERU-SIGN / ZHAO-SIGN

---

## 路径 A：Carpet: NeoForged（NeoForge 原生）—— **无 26.1.x 构建**

1.21.1 / 1.20.1 两线用的就是它。Modrinth 实查（2026-09-22）：`neoforge-carpet`
的 `game_versions` **只到 `1.21.1`**，没有 `26.1 / 26.1.1 / 26.1.2 / 26.2`。
（搜索 `carpet` + `neoforge` 分类的其余命中全是装饰性羊毛方块模组，与 bot 无关。）

## 路径 B：Sinytra Connector + Fabric 版 Carpet —— **实测致命失败**

Carpet 官方只有 **Fabric** 构建（Modrinth `loaders: ['fabric']`），故尝试经 Connector 转发。
四件套均已实现进工具链（`mt_env.ps1` 的 `$script:ConnectorStack2612`），**但默认关闭**，
需显式 `MT_CARPET_VIA_CONNECTOR=1` 才装。

| 组件 | 版本 | 来源 |
|---|---|---|
| Launchpad | `1.9.2+26.1.2` | Modrinth Maven `maven.modrinth:launchpad` |
| Sinytra Connector | `3.0.0-beta.6+26.1.2` | `maven.modrinth:connector` |
| Forgified Fabric API | `0.155.2+26.1.2+3.5.5` | `maven.modrinth:forgified-fabric-api` |
| fabric-carpet | `26.1+v260402`（`game_versions` 含 26.1.2） | `maven.modrinth:carpet` |

**实测结果（2026-09-22，NeoForge 26.1.2.109）**：

- ✅ 四件套**能下载、能被 FML 识别、Connector 转译器能起来**
  （日志可见 `ConnectorPlugin from mods/connector-….jar` 与 53 个 JiJ 依赖，
  Connector 还自行拉取了 `minecraft-26.1.2-server.jar` 到 `run/26.1.2/.cache/connector/`）；
- ❌ **Carpet 的 mixin 全部无法注入 ⇒ 专用服务器启动致命失败**（`mt_env world` 直接 BLOCKED）：
  ```
  MixinTransformerError: An unexpected critical error was encountered
  Caused by: InjectionError: Critical injection failure: Constant modifier method
    addFillUpdatesInt(I)I in carpet.mixins.json:Level_fillUpdatesMixin from mod carpet
    failed injection check, (0/1) succeeded. Scanned 0 target(s). No refMap loaded.
  ```
- **根因**：`carpet.mixins.json` **不含 `refmap` 字段**（实测 `refmap=None`；
  `fabric.mod.json` 的 `mixins` 段也只是裸文件名 `["carpet.mixins.json"]`）。
  它的 171 个 mixin 的方法/字段引用以 Fabric **intermediary** 名硬编码；Fabric 环境下由
  Fabric Loader 的运行时反向映射兜底，而 **Connector 3.0.0-beta.6 未能为它生成/应用 refmap**
  ⇒ 目标方法名解析不到（`Scanned 0 target(s)`）。
- ⚠️ 日志里另有一条 `Reference map '' for adapter.init.mixins.json could not be read`
  —— 那是 Sinytra **Mixin Adapter 自己的**配置（`runtime-1.0.0+26.1.2.jar` 内），
  **故意不带 refmap**，属良性告警，**不是**本故障的原因（曾据此误判一次，已排除）。
- **影响面是阻断性的**：世界生成阶段即崩 ⇒ 整条 26.1.2 测试线（含原本可跑的 10 条单机用例）
  都会变得不可运行。故该栈**默认必须关闭**。
- `connector-extras` **不解决**该问题：它是第三方 API 桥接（能量/REI/…），与 mixin 重映射无关。

## 路径 C：26.1.2 原版 bot 管理命令 —— **不存在**

项目里原有的裁决依据是「26.1.2 用 Mojang 自带 bot 管理指令」。经核**该说法不成立**：
`minecraft-patched-26.1.2.109-sources.jar` 的 `net/minecraft/server/commands/` 下
**80 个命令文件里没有任何 player / bot 管理命令**；全库搜 `FakePlayer` 只命中
NeoForge 的 `net.neoforged.neoforge.common.util.FakePlayer`，而它**不进 `PlayerList`**
⇒ 探针「按玩家名取玩家」的路径（`getPlayerByName`）全部失效。

---

## 解除条件（任一条成立即可全部启用）

1. NeoForge 原生移植 `Carpet: NeoForged` 发布 26.1.x 构建 ⇒ 把 `$script:CarpetByVersion['26.1.2']`
   改成 `neoforge-carpet` 规格、并把 `mt_launch.ps1` 的 `$carpetGateOn` 恢复为无条件 true；
2. Connector 修好「无 refmap 的 Fabric 模组」的 mixin 重映射，或 Carpet 改为携带 refmap 发布
   ⇒ 直接 `MT_CARPET_VIA_CONNECTOR=1` 复跑 `mt_env world`，能过再启用用例；
3. 在探针里自建 bot 通道（`FakePlayerFactory` 造 `ServerPlayer` **并注册进 `PlayerList`**）
   —— 但这会让探针不再是「与另两线逐字同尺」，**必须同步登记为口径差异**。

> 复测入口：`pwsh -File scripts/test/mt.ps1 --version 26.1.2 --phase env`（已设
> `MT_CARPET_VIA_CONNECTOR=1` 时）→ 看 `MT_WORLD` 是否还 BLOCKED。
