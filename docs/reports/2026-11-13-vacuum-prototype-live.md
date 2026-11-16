# 垃圾桶立牌动态管道原型：1.21.1 实机验证（26.4-SNAPSHOT.20）

- 日期：2026-11-13
- 分支：`multi-dev-next`
- 版本：五线 `26.4-SNAPSHOT.20+`；产品代码仅 `neoforge-1.21.1` 有本原型改动
- 范围：垃圾桶立牌动态吸尘管道原型（隐藏物品 `astral_dice:vacuum_piece` + OP 2 命令 `/astralvacuum`），**不是完整立牌**

## 结论

**机制链路 PASS（仅 neoforge-1.21.1 原型）**：管道生成、8 tick 伸出、每 tick 0.35 格牵引、1.5 格到位、基础 5.0 技能伤害、显示实体回收均已在实机日志中出现。**视觉（长度/弯曲/贴地/头部朝向）仍以用户目视为准，本轮没有截图取证。**

**伤害归属未完全隔离**：`AP_VACUUM_DAMAGE` 记录的是传入 `target.hurt` 的基础值 5.0；目标实际掉血 1000→972（28 点）。仓库里的伤害折叠效果牌会在 `LivingIncomingDamageEvent` 加伤，疑似来源，但本轮没有关掉效果牌做对照，因此**不能声称“最终伤害正好 5 点”**。

## 环境与启动

- 启动：`bash scripts/test/win/launch.sh --version 1.21.1 --quickplay "新的世界" --wait-world 30 --timeout 600 --kill-on-timeout`
- wrapper 日志：`scripts/test/win/logs/1.21.1-20261113-005724.log`
- `game_pid=44892`；AutoJS 端点 `127.0.0.1:25585`
- 世界：`新的世界`，生成器 `net.minecraft.world.level.levelgen.FlatLevelSource`（超平坦）
- 客户端读 `AutoJS.modVersion("astral_dice")` = `26.4-SNAPSHOT.20+neoforge_1.21.1`
- 启动前 25565/25575/25585 均空闲；测试时无其他实机线

## 测试步骤

1. 清理旧测试实体：`/kill @e[tag=vacuum_test]`。
2. 在玩家视线前方 8 格生成一只僵尸：服务端 Java 直接 `new Zombie(EntityType.ZOMBIE, level)`，`setPos`、`setNoAi(true)`、`setSilent(true)`、`setPersistenceRequired()`，生命上限与当前生命都设为 1000，tag `vacuum_test`。
3. 执行：`/execute as Dev at Dev run astralvacuum @e[tag=vacuum_test,limit=1]`。
4. 用 AutoJS 读取同 tick 的 `minecraft:item_display` 数量与位置；约 2 秒后读取目标 HP 与残留 display。

## 实机读数

### 生成与牵引

- 同 tick 生成 **13 个 `minecraft:item_display`**（12 节管 + 1 个头），目标 HP 仍为 1000。
- `run/neoforge-1.21.1/logs/latest.log:20909`：

```text
AP_VACUUM_START owner=Dev target=僵尸 dist=8.00 segments=12
```

- `latest.log:20926`：`AP_VACUUM_PULL age=9 target=僵尸 dist=7.65`
- `latest.log:20942`：`AP_VACUUM_PULL age=19 target=僵尸 dist=4.15`

### 到位与伤害

- `latest.log:21019`：

```text
AP_VACUUM_DAMAGE target=僵尸 damage=5.0 dist=1.50
```

- `latest.log:21020`：

```text
AP_VACUUM_END reason=arrived age=27
```

- 约 2 秒后：目标实体 id `4993`、类型 `minecraft:zombie`、tag `[vacuum_test]`，HP 从 1000 降到 **972**；场上 display 已全部回收。

### .19 对照（同一用例，旧阈值）

`.19` 在出发 8 格、12 节的同一用例中，日志停在：

- `latest.log:32959 AP_VACUUM_PULL age=29 ... dist=1.50`
- `latest.log:33147 AP_VACUUM_END reason=timeout age=80 dist=1.50`
- 无 `AP_VACUUM_DAMAGE`

根因与修复见 `CHANGELOG_ZH.md` 的 `.20` 段：`VacuumPipeManager.java:75` 的 `ARRIVE_DISTANCE` 从 1.35 改为 1.60，使其大于 `FRONT_OFFSET=1.5`。

## 限制与风险

1. **没有截图**：视觉证据缺位，管道粗细节、弯曲是否顺、头部 +Z 朝向、贴地高度需用户目视确认。
2. **实际伤害 28 vs 基础 5.0**：未隔离伤害折叠效果牌；不得把 28 当作管道固有伤害。
3. 24 节上限、Display 同步量、急弯关节缝隙、长时间多目标叠加均未测。
4. 仅 `neoforge-1.21.1`；另外四线只是版本号同步，没有本原型代码。
5. 仍是原型：没有垃圾桶立牌物品、主动/被动、配方、tooltip、手册，未 commit/push。

## 产物

- 五线 `.20` 构建：`./gradlew.bat :neoforge-1.21.1:build :forge-1.20.1:build :neoforge-26.1.2:build :fabric-1.20.1:build :fabric-1.21.1:build --console=plain` → `BUILD SUCCESSFUL in 7s`
- 1.21.1 jar：`build/libs/astral_dice-26.4-SNAPSHOT.20+neoforge_1.21.1.jar`，SHA-256 `3a71e5e9c85c9d85ae32aed231195437714bd5a3b335795a8c441e79483fa6c7`
- 同哈希副本在 `run/neoforge-1.21.1/mods/`

## 下一步建议

1. 先让用户目视确认管道外观；若无问题，可把 A（Display 阵列）定为正式渲染方案。
2. 若视觉不可接受，再评估 B（自定义实体 + renderer）；本轮不预先切换。
3. 正式垃圾桶立牌的主动/被动、资源与五线移植待视觉裁决后开始。
