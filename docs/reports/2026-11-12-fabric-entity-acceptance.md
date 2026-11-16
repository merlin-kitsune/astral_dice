# fabric 两线实体级立牌验收报告（B 阶段）

日期：2026-11-12
分支：multi-dev-next
HEAD：43f821adf0779320e2efe752aefa76cfd5711eb7
版本：五线仍为 26.4-SNAPSHOT.13（本轮未改产品代码，未 bump）

## 1. 结论

fabric-1.20.1 与 fabric-1.21.1 的**实体级主动技能**已实机验证：

- Misaki 立牌对僵尸调用 activateFor：返回 true，施加 MISAKI_BURST 2400t，层数 3 → 1（扣 2 层）。
- Bonnie 立牌对僵尸调用 activateFor：返回 false，日志为 AP_SIGN_ACTIVE_ENTITY_DENIED: sign=bonnie:reason=needs_player_session。
- 僵尸 stand 饰品槽通过临时数据包 zztest 生效（CuriosApi.getStacksHandler(...).isPresent()=true）。
- 两线均无任何立牌覆写 onKillEntity / onHurtEntity；BaseSignItem 的实体级 kill/hurt 管线只在 killer/entity 为 Player 时委派，因此**非玩家击杀/受击路径按设计为 no-op**，本报告不声称任何具体立牌的非玩家击杀效果生效。

本结果只覆盖实体级主动技能与拒绝路径，不等同于“发布版本”；版本仍是审计构建 .13。

## 2. 测试方法

- 客户端集成服务器 + AutoJS server 侧 eval（http://127.0.0.1:25585/mcp）。
- 临时数据包：
  - fabric-1.20.1/run/client/saves/testworld/datapacks/zztest/pack.mcmeta（pack_format 15）
  - data/trinkets/entities/zztest.json = {"entities":["minecraft:zombie"],"slots":["astral_dice/stand"]}
  - fabric-1.21.1 的 testworld 已有同款（pack_format 48）
- 给僵尸装备立牌：CuriosApi.getStacksHandler(z, "stand").get().getStacks().setStackInSlot(0, stack)。
- 1.20.1 组件写入：ItemDataKey 为 NBT shim，直接 stack.getOrCreateTag().putInt("misaki_sign_stacks", 3)。
- 1.21.1 组件写入：ItemDataKey 走 CustomData，构造 CompoundTag 后
  stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))，tag.putInt("misaki_sign_stacks", 3)。
- 触发：stack.getItem().activateFor(zombie)。

## 3. fabric-1.20.1 实机结果

启动：
```
bash scripts/test/win/launch.sh --version fabric-1.20.1 --side client --enable-autojs --quickplay testworld --timeout 900
```
- READY + WORLD_READY；game_pid=11276；日志 scripts/test/win/logs/fabric-1.20.1-20261112-122055.log。

Misaki（僵尸 745，stand=true）：
- before：1 astral_dice:misaki_sign | nbt={misaki_sign_stacks:3}
- activateFor 返回 true，hasEffect(MISAKI_BURST)=true，duration=2400
- after：1 astral_dice:misaki_sign | nbt={misaki_sign_stacks:1}
- 日志：fabric-1.20.1/run/client/logs/latest.log:1083
  `[12:28:37] [Server thread/INFO] (SignEntitySkills) AP_SIGN_ACTIVE_ENTITY: sign=misaki:owner=僵尸:duration=2400`

Bonnie：
- activateFor 返回 false；PlayerLikeTargets.find(z) == null（无 handler）
- 日志：fabric-1.20.1/run/client/logs/latest.log:1126
  `[12:29:29] ... AP_SIGN_ACTIVE_ENTITY_DENIED: sign=bonnie:reason=needs_player_session:owner=僵尸`
- latest.log 无 AP_PLAYER_LIKE / AP_CHIP_SHOP 行。

## 4. fabric-1.21.1 实机结果

启动：
```
bash scripts/test/win/launch.sh --version fabric-1.21.1 --side client --enable-autojs --quickplay testworld --timeout 900
```
- READY + WORLD_READY；game_pid=8700；日志 scripts/test/win/logs/fabric-1.21.1-20261112-123113.log。
- 1.21.1 未安装 KubeJS，AutoJS 走自有 Rhino；Java.loadClass 可用（typeof Java.loadClass == "function"），Packages/Java.type 不可用。

Misaki（僵尸 99，stand=true）：
- before：1 astral_dice:misaki_sign | nbt={misaki_sign_stacks:3}
- activateFor 返回 true，hasEffect(MISAKI_BURST)=true，duration=2400
- after：1 astral_dice:misaki_sign | nbt={misaki_sign_stacks:1}
- 读取效果须使用 Holder（Effects.holder(ModEffects.MISAKI_BURST.get())）；直接传 get() 会报 hasEffect(MisakiBurstEffect) 找不到。

Bonnie（僵尸 101）：
- activateFor 返回 false；PlayerLikeTargets.find(z) == null
- 日志：fabric-1.21.1/run/client/logs/latest.log:577
  `[13:44:03] ... AP_SIGN_ACTIVE_ENTITY_DENIED: sign=bonnie:reason=needs_player_session:owner=僵尸`

Misaki 日志：
- latest.log:476 `[13:42:51] ... AP_SIGN_ACTIVE_ENTITY: sign=misaki:owner=僵尸:duration=2400`
- latest.log:535 `[13:43:23] ... AP_SIGN_ACTIVE_ENTITY: sign=misaki:owner=僵尸:duration=2400`

## 5. 非玩家 kill/hurt（代码层结论）

- fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java:668 onKill 为空、:676 onHurt 为空。
- 同文件 :679-685：onKillEntity(killer, killed) 仅当 killer instanceof Player 才委派 onKill；onHurtEntity(entity, source, amount) 仅当 entity instanceof Player 才委派 onHurt。
- 同文件 :718-741：invokeKillHooks(LivingEntity, LivingEntity) / invokeHurtHooks(LivingEntity, ...) 对非玩家同样只会走到上述 Player 判断。
- fabric-1.20.1 与 fabric-1.21.1 全仓 grep onKillEntity(/onHurtEntity( 只有 BaseSignItem 定义与调用，没有任何立牌覆写。

结论：实体级 kill/hurt 只能证明基类管线，非玩家路径为 no-op；不得把“某立牌对非玩家击杀生效”写入结论。

## 6. 未覆盖 / 阻塞

- 两线未注册任何 PlayerLikeTargets handler；Misaki 的层数达标后名刀经 PlayerLikeTargets.giveItem 交付，在无 handler 时返回 false（未交付，属当前设计边界）。本轮未伪造交付 PASS。
- 未测非玩家 kill/hurt 的具体立牌效果（代码层无覆写，按设计不可达）。
- 1.21.1 的 KubeJS 缺失导致事件/脚本能力受限；本轮仅用 AutoJS 自有 Rhino 的 eval 与 Java.loadClass。

## 7. 收尾

- 1.20.1 客户端已收停（stop.sh 报 pending，PowerShell Stop-Process 11276/10568），25565/25575/25585 一度全 FREE。
- 1.21.1 客户端已收停（stop.sh 报 pending，PowerShell Stop-Process 8700/32024），复核 25565/25575/25585 全 FREE。
- 本轮未改产品代码、未 bump mod_version、未 commit/push/tag、未上传 CurseForge。
- 磁盘仍只有既有文档/脚本改动与未跟踪测试数据包；未动 models/、images/、sound/ 与他人文件。
