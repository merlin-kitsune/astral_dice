# sykestentacle 模型与动画调用规则

> 资产目录：`models/sykestentacle/`（2026-10-29 起已接入模组，成为动画实体 `astral_dice:sykestentacle`；代码侧事实见第 6 节）。  
> 解析环境：Blockbench 5.2.2 desktop + Blockbench MCP 1.10.1，模型经 `Codecs.project.load(..., { no_file: true })` 载入（97 cubes / 22 groups / 2 anims / 1 texture；重导后 geo 22 骨，见 §4）。  
> 记录日期：2026-10-29（dev 线 `multi-dev-next`）。

## 1. 动画调用规则（本文件核心）

| 动画名 | 用途 | 时长 | 循环标记 | 调用时机 |
|---|---|---|---|---|
| `animation.tentacleidle` | 触手**待机**动画 | 4.11 s | `loop: true` | 空闲态常驻循环 |
| `animation.tentacleattack` | 触手**攻击**动画 | 2.46 s | `loop: true` | 进入攻击动作时播放一次，播完显式切回待机 |

- 动画 ID 带 `animation.` 前缀，取用时要写全（Bedrock / GeckoLib 均按同名索引）。
- 两条动画都标了 `loop: true`：`tentacleidle` 正是靠它自循环；`tentacleattack` **语义上按一次性攻击使用**，播放器必须在 2.46 s 结束点显式切回 `animation.tentacleidle`，不能依赖 loop 标记（否则会一直重复挥击）。
- `animation.tentacleidle` 驱动 21 根骨骼、`animation.tentacleattack` 驱动全部 22 根（含 2026-10-29 新增的空父骨 `bone`）；切换时不需要按骨骼挑 blend 名单，直接整体过渡即可。
- 除 `animation.tentacleattack` 的 `bone2` 有位移（`position.y` 0 → −3 → 0）外，其余通道位置恒 0、缩放恒 1，所以**过渡只需要处理旋转混合**。

## 2. 动画内容

| 动画 | rotation 关键帧总数 | 驱动骨骼 | 峰值动作 | 首尾接缝 |
|---|---|---|---|---|
| `animation.tentacleidle` | 1319 | 21/22（不含空父骨 `bone`；XIPAN 吸盘与 bone13 近静止） | Z 轴摆动沿链递增，bone10/11 峰值 ≈43° | 0.00°（无缝循环） |
| `animation.tentacleattack` | 617 | 22/22 | `bone2` 绕 X 连续旋转 −438.4°（≈1.22 圈，t=0.79 s） | 362.0°（≡ −2°，视觉同向） |

### 2.1 `animation.tentacleidle`（待机，4.11 s）

一条**从根部传到末端的行波**：链上各段在三轴小幅摆动，Z 分量最大；幅度沿链递增、峰值时刻沿链依次延后。

| 骨骼 | rotation kf | 最大摆角 X / Y / Z (°) |
|---|---|---|
| bone2 | 100 | 0.41 / 0.21 / 1.19 |
| bone3 | 100 | 0.90 / 0.35 / 2.69 |
| bone4 | 100 | 1.61 / 0.20 / 4.68 |
| bone5 | 100 | 2.93 / 2.02 / 7.48 |
| bone6 | 100 | 5.35 / 5.93 / 11.20 |
| bone7 | 100 | 8.27 / 6.72 / 17.40 |
| bone8 | 100 | 13.90 / 5.27 / 31.66 |
| bone9 | 100 | 16.18 / 3.62 / 35.28 |
| bone10 | 100 | 21.93 / 10.13 / 43.28 |
| bone11 | 100 | 22.05 / 22.80 / 42.16 |
| bone12 | 100 | 23.82 / 40.43 / 36.87 |
| bone13 | 65 | ≤0.01（近静止） |
| XIPAN1 / XIPAN_origin2 / 3 / 4 | 15 / 28 / 42 / 59 | ≤0.001 / ≤0.002 / ≤0.002 / ≤0.005（吸盘几乎不形变） |
| root / bone14 / bone16 / bone17 / bone18 | 2 | 0（完全静止） |

### 2.2 `animation.tentacleattack`（攻击，2.46 s）

| 时间段 | 内容 |
|---|---|
| 0.00 – 0.21 s | 起手：`bone2` X 0 → −62.7°，`position.y` 开始下压 |
| 0.21 – 0.79 s | 主挥：`bone2` X 冲到 −438.4°、Z 到 −143.4°，`position.y` 压到 −3；链上各段在 0.54 s 附近到 30–35° 峰值后反向甩回 |
| 0.79 – 1.00 s | 回收：`bone2` 回到 −370.0°，位移在 1.01 s 归 0 |
| 1.00 – 2.08 s | 余波：远端 bone8–bone12 依次在 1.21–1.50 s 到达峰值（鞭梢延迟 0.7–1.0 s）后收敛 |
| 2.08 – 2.46 s | 静止保持 0.38 s，结束时 `bone2` 停在 −362.0°（≡ −2°） |

| 骨骼 | attack kf | 峰值 \|X\| @时刻 | 最后一次变化 |
|---|---|---|---|
| bone2 | 38 | 438.4 @0.79 s | 1.46 s |
| bone3 | 43 | 33.4 @0.54 s | 1.58 s |
| bone4 | 46 | 35.0 @0.54 s | 1.83 s |
| bone5 | 50 | 33.2 @0.54 s | 2.04 s |
| bone6 | 51 | 31.7 @0.71 s | 2.04 s |
| bone7 | 55 | 23.9 @0.75 s | 2.21 s |
| bone8 | 57 | 22.3 @1.21 s | 2.25 s |
| bone9 | 60 | 21.3 @1.29 s | 2.46 s |
| bone10 | 60 | 21.7 @1.38 s | 2.42 s |
| bone11 | 60 | 22.1 @1.46 s | 2.46 s |
| bone12 | 60 | 26.6 @1.50 s | 2.46 s |
| bone13 / XIPAN1 / XIPAN_origin2–4 / bone14 / 16 / 17 / 18 / root | 2–8 | ≈0（不参与） | — |

## 3. 模型结构摘要

| 项 | 值 |
|---|---|
| 骨骼 | 22（1 root + 1 空父骨 `bone` + 12 段主链 bone2…bone13〔`bone2` 挂在 `bone` 下〕 + 4 组 XIPAN 吸盘 + 4 个静态子骨骼 bone14/16/17/18） |
| 立方体 | 97（底盘 1 + 主链 12×4 + 吸盘 4×8 + 静态子骨骼 4×4），全部 6 面，共 582 面 |
| 贴图 | `textures/texture.png` 512×512，UV 空间 256×256（2 px/单位） |
| 尺寸 | 通高 ≈47.3 单位 ≈2.96 格；底盘 39.2×0.1×36 单位 ≈2.45×2.25 格 |
| 可见边界 | 宽 5 / 高 4.5 格，offset y=1.75 |
| 工程格式 | Blockbench 4.10 / `model_format: bedrock` / `bedrock_animation_mode: entity` |

## 4. 接入前必须处理的问题

**两套导出物坐标系不一致**：`models/main.geo.json` 的 pivot X 与旋转 `rx` 相对 `sykestentacle.bbmodel` 整体取反（例 XIPAN1：geo `[1.682, 7.4, -2.827]` / `[90, -5, 25]` vs bbmodel `[-1.682, 7.4, -2.827]` / `[-90, 5, 25]`），而 `animations/default.animation.json` 的关键帧数值与 bbmodel **同号**（`bone2` attack t=0.04 s：json −4.958 vs bbmodel −5.0）。

⇒ 「geo + anim」这一对不能保证与 Blockbench 里看到的姿态同向。**2026-10-29 已从 bbmodel 重导过一次**（见 §6.2）：重导结果与旧 geo 逐值一致（唯一差异是新增空父骨 `bone`）⇒ 这条 X 取反**不是导出过期，而是导出器固有的镜像约定** —— `bedrock` 格式导出时整模 X 镜像（实测 bbmodel cube `root` 的 `from.x = -17.8` / `to.x = 21.4` 变成 geo 的 `origin.x = -21.4`（= −`to.x`）/ `size.x = 39.2`），而动画关键帧不镜像。故**重导修不好这一对**：只能在实机渲染里复核左右倾向（尤其 `animation.tentacleattack` 的主挥方向）；若确认镜像，需在 Blockbench 里把模型刻意镜像后再导，而不是简单重导。

## 5. 取证方式（可复现）

- 启动：`Start-Process 'C:/Users/xmace/AppData/Local/Programs/Blockbench/Blockbench.exe'`，端口 `3000`（端点 `/bb-mcp`）约 2 s 就绪。
- 载入：`risky_eval` 里 `Codecs.project.load(JSON.parse(文件内容), { path: <绝对路径>, no_file: true })`。
- 注意：同一 MCP 会话里工具面会变化（本次曾先见 17 个工具、重开会话后 77 个），且 `tools/call` 必须带请求头 `MCP-Protocol-Version: 2025-06-18`，否则服务端回 `Parse error: Invalid JSON-RPC message`。


## 6. GeckoLib 接入（2026-10-29 落地；P0 两线已构建验收，P1 两线搁置）

模型已接进模组，成为实体 `astral_dice:sykestentacle`。本节记录**代码侧的调用事实**，与 1–5 节的资产侧事实互补。

### 6.1 实体与控制器

| 项 | 值 |
|---|---|
| 实体 id | `astral_dice:sykestentacle`（`/summon astral_dice:sykestentacle`，无刷怪蛋、无自然生成） |
| 基类 | `LivingEntity implements GeoEntity`，`MobCategory.MISC`，`sized(2.6F, 3.0F)`，`clientTrackingRange(10)` |
| 属性 | `MAX_HEALTH = 20`、`KNOCKBACK_RESISTANCE = 1.0`（`EntityAttributeCreationEvent` 里提交） |
| 待机控制器 | id `idle`，过渡 5 tick，`RawAnimation.begin().thenLoop("animation.tentacleidle")` —— 常驻循环 |
| 攻击控制器 | id `action`，基础状态 `PlayState.STOP`，`triggerableAnim("attack", RawAnimation.begin().thenPlay("animation.tentacleattack"))` |
| 触发点 | `hurt(DamageSource, float)` 里 `super.hurt(...)` 返回 `true` 时 `triggerAnim("action", "attack")`（即**受伤成功**触发一次，播完回落到 `idle`） |

控制器常量在实体类里是 `CONTROLLER_IDLE = "idle"` / `CONTROLLER_ACTION = "action"` / `ANIM_IDLE = "animation.tentacleidle"` / `ANIM_ATTACK = "animation.tentacleattack"`，与第 1 节的调用规则一一对应；动画 ID 保留 `animation.` 前缀（anim json 里的键名就是这个全名）。

### 6.2 资源落点（四线同一套）

| 资源 | 路径 |
|---|---|
| 几何 | `assets/astral_dice/geo/sykestentacle.geo.json` ← `models/sykestentacle/models/main.geo.json` |
| 动画 | `assets/astral_dice/animations/sykestentacle.animation.json` ← `models/sykestentacle/animations/default.animation.json` |
| 贴图 | `assets/astral_dice/textures/entity/sykestentacle.png` ← `models/sykestentacle/textures/texture.png` |

两份 json 的源文件是纯 LF，拷进资源目录时按仓库纪律转成 CRLF；「CRLF→LF 归一化」后的 sha256 与 `models/` 源相同，PNG 逐字节相同 ⇒ 资源零内容改动。

### 6.3 前置与版本（GeckoLib 已升为硬前置）

| 线 | 依赖坐标 | 元数据区间 |
|---|---|---|
| neoforge-1.21.1 | `maven.modrinth:geckolib:Grwn5rUB`（4.9.3） | `[4.9,5)` |
| forge-1.20.1 | `maven.modrinth:geckolib:aC5KMoNg`（4.8.4，`modImplementation`） | `[4.8,5)` |
| fabric-1.20.1 | `maven.modrinth:geckolib:SD2lOScS`（4.8.4，`modImplementation`） | `>=4.8.4`（`fabric.mod.json` depends） |
| neoforge-26.1.2 | `maven.modrinth:geckolib:xfVfPcoC`（5.5.2） | `[5,6)` |

- **必须写 Modrinth version id，不能写版本号**：同一版本号下同时挂着 fabric / neoforge / forge 三份变体，写 `4.9.3` 会解析到 fabric 包（只有 `fabric.mod.json`），Forge/NeoForge 起不来。
- GeckoLib **不内嵌**（无 JarJar）：26.1.2 的 jar 里 `META-INF/jarjar/` 仍只有 starengine。
- API 分水岭：4.8.4 的动画类在 `software.bernie.geckolib.core.*` 子包，4.9.3 去掉了 `core.`；26.1.2 的 5.x 换成 `com.geckolib.*`，且 `AnimationController` 构造器不再接收实体实例。三条 1.20.1/1.21.1 线之间**不能照抄 import**。

### 6.4 仍未解决的一项

第 4 节记录的 geo/anim 坐标系不一致**没有在本批修复**，且 2026-10-29 的重导证明**它不是「导出过期」**（重导前后逐值一致，X 取反是 `bedrock` 导出器的固有镜像）。⇒ 若实机看到触手整体镜像或攻击前劈方向相反，**重导无效**，只能在 Blockbench 里先把模型刻意镜像、再导出一对 geo/anim 替换三个资源文件；代码侧不需要改。判据仍只能来自实机（本轮未启动游戏）。
