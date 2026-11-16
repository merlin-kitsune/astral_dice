# 抓钩拖怪原型实机记录（neoforge-1.21.1）

- 日期：2026-11-13
- 当前版本：`26.4-SNAPSHOT.30+neoforge_1.21.1`；下文的实机截图/日志来自 `.29`，`.30` 只做第三方派生内容清理与重写，未实机。
- 范围：仅 `neoforge-1.21.1` 单线原型；其他四线只做了版本号同步，没有产品代码改动。
- 需求演进：用户先要求抓钩只抓怪、拖到玩家面前（`.25/`.26`），随后要求保留动画、把钩头换成吸尘器模型、铁链换成白色管道（`.27`）。`.27/`.28` 实机看不到管道，`.29` 修复两个渲染根因后可见；`.30` 按要求删除所有第三方派生的声音/模型/许可/残余代码，只保留拖拽效果。
- 结论：**管道与头部已可见，目标从 9 格被拖到玩家正前方 1.5 格；用户已确认效果。`.30` 清理完成，未 commit / 未 push / 未打 tag / 未上传 CurseForge。**

## 构建

`.29` 实机构建：

```text
./gradlew.bat :neoforge-1.21.1:build --console=plain
BUILD SUCCESSFUL in 10s
build/libs/astral_dice-26.4-SNAPSHOT.29+neoforge_1.21.1.jar
sha256 53088bf36c0e62e7d4b54ec7029c263bab072ce5aa8fb5c3be75997a501569ab
```

`.30` 清理后五线构建：

```text
./gradlew.bat :neoforge-1.21.1:build :forge-1.20.1:build :neoforge-26.1.2:build :fabric-1.20.1:build :fabric-1.21.1:build --console=plain --no-daemon
BUILD SUCCESSFUL in 12s
build/libs/astral_dice-26.4-SNAPSHOT.30+neoforge_1.21.1.jar
sha256 fdea2efe2e62c5588e1f443654d61ef00180ced33980fa19804ab9f63dee6dcf
```

五枚 `.30` jar 与 `.sha256` 已生成；`run/neoforge-1.21.1/mods/` 已同步 `.30`。

## 渲染链路（.27–.30）

- `GrapplingHookRenderer` 不再画锁链方块：`pipeStack = new ItemStack(ModItems.VACUUM_PIECE.get())`，`headStack` 同物品并加 `CustomModelData(1)` 走 `vacuum_piece.json` 的 override 到 `vacuum_head`。
- 管道每 1 格一节，从玩家身高 65% 处连到钩子实体；头部回退距离 `HEAD_BACK=0.55`、`HEAD_SCALE_XZ=1.8`、`HEAD_SCALE_Y=2.6`。
- 删除 `GrapplingHookModel` 与对应 `LayerDefinition` 注册；钩头/管道全部由物品模型渲染。
- `.28`：客户端 `Projectile#getOwner()` 不解析 `ownerUUID`，新增同步的 `DATA_OWNER_ID` 与 `getRenderOwner()`。
- `.29`：`EntityRenderDispatcher` 已把 PoseStack 平移到实体位置，渲染器又叠加世界坐标；改为相对实体原点计算 `from/to/head`，管道与头部才显示。

## 第三方派生内容清理（.30）

- 删除 6 个 `grappling_hook_{attach,shoot}_0..2.ogg`、`textures/entity/hook/grappling_hook.png`、`sounds.json` 的两组声音与三语字幕键、根目录第三方声明文件与 `licenses/` 下的第三方许可文件。
- `GrapplingHookEntity` / `GrapplingHookRenderer` 重写为项目自有实现：保留拖拽行为，继续用 `VACUUM_PIECE` 的 `vacuum_piece` / `vacuum_head` 模型；不再带任何 SPDX/第三方许可头。
- `GrapplingHookCommand` 删除音效播放；`ModSounds` 删除两个抓钩音效注册；`ModEntityTypes` 删除移植来源注释。
- 验证：五线 `:build` `BUILD SUCCESSFUL in 12s`，exit 0；jar 内不再有 `grappling_hook` 音效/实体贴图/许可文件，`javap` 确认实体只有 `getRenderOwner`/`getHookedTarget`/`placeTarget` 等新方法，`GrapplingHookModel` 已不存在。

## 实机环境

- 启动：`bash scripts/test/win/launch.sh --version 1.21.1 --quickplay 新的世界`
- 世界：`FlatLevelSource`（真超平坦；玩家 y=-60）
- 玩家：`Dev`，`(0.5, -60, 0.5)`，面向 -Z
- 目标：`minecraft:husk`，`NoAI/Silent/PersistenceRequired/Glowing`，`max_health=1000`，`Health=1000`，初始 `(0.5,-60,-9)`
- 侧视相机：临时 `armor_stand`（Invisible/Marker/NoGravity），`(7.0,-59.5,-4.0)`，`Rotation:[90f,0f]`；客户端 `mc.setCameraEntity(cam)`
- 触发：客户端 `mc.player.connection.sendCommand("astralhook @e[type=minecraft:husk,limit=1]")`
- 截图：`Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), consumer)`，`ScheduledExecutorService` 固定时刻抓帧

## 日志证据（`run/neoforge-1.21.1/logs/latest.log`）

```text
7803  AP_GRAPPLE_START owner=Dev target=尸壳 dist=9.50
7882  AP_GRAPPLE_ATTACH owner=Dev target=尸壳
7890  AP_GRAPPLE_PULL owner=Dev target=尸壳 dist=8.27
7914  AP_GRAPPLE_PULL owner=Dev target=尸壳 dist=4.78
7994  AP_GRAPPLE_END owner=Dev reason=arrived age=53
```

实机结束后服务端读数：

```text
player  (0.5, -60, 0.5)
husk    (0.5, -60, -1.0)   HP 1000
grappling_hook 实体：无
```

玩家 z=0.5、目标 z=-1.0，水平距离正好 **1.5 格**；目标没有受伤（HP 仍 1000）。

## 截图证据

目录：`run/neoforge-1.21.1/screenshots/`

| 文件 | 时刻 | 内容 | SHA-256 |
|---|---|---|---|
| `vac29-700.png` | 0.7 s | 白色管道从玩家伸向尸壳，末端橙红头部 | `47dfb3770975ecea813525d24d562d9f0bc96a1ae89caa1be36cfbac0152e930` |
| `vac29-1100.png` | 1.1 s | 管道变长，头部接近目标 | `fe0bab1630526c45b48d620eec8c3b89a20f4ee355af05e7dd7ebf135300e034` |
| `vac29-1500.png` | 1.5 s | 目标开始被拉动，管道仍连接 | `a1e331f04357fb30c9e9561cf1af1c1cc831d54581bd2cab8063fe7c58e46c2b` |
| `vac29-3000.png` | 3.0 s | 目标已被拖到玩家正前方，管道消失 | `23bcb4d46ddc1e823854b6dd155c985ce7b55cd8e9eafabe476d954af94b6d5f` |
| `vac29c-600.png` | 0.6 s | 近距侧视：管道与头部连接处 | `01a800836ac892b867c0d60763ca63bae98cc6c96dc634ab43bcc6edd72a01ef` |
| `vac29c-1000.png` | 1.0 s | 近距侧视：头部贴近目标 | `5b13b7d1e01c76edb3b00c1a2c69d3846d44ba1db13809dff9293fe755fca981` |
| `vac29c-1400.png` | 1.4 s | 近距侧视：目标被拉近 | `03e5b09fed6b621fb360ff442971f382ed9eb6c276e43e6672ec89969cc5c5e3` |

截图均为 1920x1080；实机画面为超平坦草原，玩家在左、尸壳在右，侧视相机固定。

## 视觉观察

- 白色管道清晰、水平，贴着地面高度从玩家连到目标；目标移动时管道逐节缩短，没有旧吸尘器管道的贝塞尔侧弯。
- 头部可见为橙红色块，侧面看仍是扁板：源 `vacuum_head` 模型 Y 方向只有 1.8 像素（`models/vacuumhead.bbmodel` 与生成后的 `vacuum_head.json` 一致），当前 `HEAD_SCALE_Y=2.6` 只能把它拉到约 0.29 格高；不是渲染器压扁。
- 第三人称玩家背后视角会被玩家自己的头挡住钩头，本轮证据全部使用侧视相机。
- 未观察到目标受伤、击退或额外粒子/音效异常；本轮只验证拖拽与显示。

## 限制与未完成

- 仅 `neoforge-1.21.1`；其他四线尚未移植，也没有实机。
- 仍是原型：入口是 OP 2 的 `/astralhook <target>`，只接受 `Mob`，没有物品、配方、立牌主动/被动、tooltip 或手册。
- 不结算伤害；目标 HP 保持 1000，符合当前原型范围。
- 第三方派生声音/模型/许可已在 `.30` 删除；现在只剩项目自有的 `vacuum_piece` / `vacuum_head` 模型与无音效的拖拽效果。
- 头部比例是否加厚（提高 `HEAD_SCALE_Y` 或改模型）由用户后续裁决；`.30` 未改视觉效果。
- 用户已确认效果；清理后等待下一步指示，不向其他四线推送，不 commit / push / tag / 上传 CurseForge。

## 复现命令

1. 启动：`bash scripts/test/win/launch.sh --version 1.21.1 --quickplay 新的世界`
2. 服务端布置目标与相机：
   ```text
   /tp @e[type=minecraft:husk,limit=1] 0.5 -60 -9 0 0
   /data merge entity @e[type=minecraft:husk,limit=1] {Health:1000f}
   /tp Dev 0.5 -60 0.5 180 0
   /summon minecraft:armor_stand 7.0 -59.5 -4.0 {Invisible:1b,Marker:1b,NoGravity:1b,Rotation:[90f,0f]}
   ```
3. 客户端把相机设到该 armor_stand，并执行：
   ```text
   /astralhook @e[type=minecraft:husk,limit=1]
   ```
4. 预期：9.5 格距离，白色管道伸出、目标被拖到玩家正前方 1.5 格，日志以 `AP_GRAPPLE_END reason=arrived age=53` 结束。
