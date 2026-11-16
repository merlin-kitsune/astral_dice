# P0 反击改造三线实机验收报告

日期：2026-11-12
分支：`multi-dev-next`
HEAD：`43f821adf0779320e2efe752aefa76cfd5711eb7`
版本：五线 `26.4-SNAPSHOT.18+<加载器>`（本轮未再改产品代码）

## 1. 结论

- `neoforge-1.21.1`：超平坦世界全套 P0 用例 PASS（含车万女仆友方路径）。
- `forge-1.20.1`：除「车万女仆友方」外全套 PASS；测试环境未安装车万女仆，**该分支未验证，不写 PASS**。
- `neoforge-26.1.2`：除「车万女仆友方」外全套 PASS；测试环境未安装车万女仆，**该分支未验证，不写 PASS**。
- 本轮只做实机验证、报告与 CHANGELOG，不再改产品代码；三线核心的 10 秒近战反击、免费多击、200t 到期、魔法/技能/远程排除、金鲤 4 层、金鳞只在激活时 +1、护盾消失不摘状态、安全气囊近战/魔法分支均已实测。
- **不是发布版本**：两条线女仆路径缺测试环境，fabric 两线仍保持 P1 前的旧语义。

## 2. 测试方法

- 客户端集成服务器 + AutoJS server 侧 eval（`http://127.0.0.1:25585/mcp`）。
- 超平坦世界取证：
  - 1.21.1：`新的世界`，`FlatLevelSource`，玩家 `Dev`，出生点 y=-60；启动日志 `scripts/test/win/logs/1.21.1-20261112-213646.log`。
  - 1.20.1：`testworld`，`FlatLevelSource`；启动日志 `scripts/test/win/logs/1.20.1-20261112-215304.log`。
  - 26.1.2：`flatworld`（由服务端目录 `run/neoforge-26.1.2/testworld` 复制到 `saves/flatworld`），实测 `FlatLevelSource`；服务端 LevelName 仍显示 `ServerLevel[testworld]`，只是显示名，不是生成器判据；启动日志 `scripts/test/win/logs/26.1.2-20261112-221354.log`。
- 近战：1.21.1 / 1.20.1 用 `zombie.doHurtTarget(player)`；26.1.2 用两参 `husk.doHurtTarget(level, player)`。目标用 `NoAI` + `CustomName` + `PersistenceRequired`，26.1.2 用 `minecraft:husk` 避免白天烧死。
- 非近战：1.21.1 / 1.20.1 用服务端 `/damage Dev 1 <type>`（magic / `astral_dice:skill_damage` / arrow）；26.1.2 直接 `player.hurtServer(level, source, 1.0F)`，并先 `setAbsorptionAmount(0)`。
- 安全气囊：装备 `airbag_chip` + `golden_dice`，给 6 充能后调用 `AirbagChipItem.tryNegateFatal(player, source)`；近战源 `damageSources().mobAttack(target)`，魔法源 `damageSources().magic()`。
- 护盾/状态读取：`ModAttachments.getRenCounterCharges`、`SouriSignManager.getGoldScales`、`CounterStateManager.isActive/remainingTicks`、`RenShieldManager.isShielded`。

## 3. neoforge-1.21.1

### 3.1 金鲤 4 层与护盾消失

- `SMI.addCounter(p,4); RSM.grantShield(p,false)` 后：`{charges:4, shielded:true, gold:0}`。
- 僵尸命中后：`{active:true, rem:200, charges:3, gold:1, shielded:true, playerHp:20, zombieHp:1023.06}`。
- `RSM.voidShield(p,'test-shield-clear-after-activate')` 后：`{active:true, rem:200, charges:3, gold:1, shielded:false}`。
- 结论：4 层不被压回 1；护盾清空不摘 REN_COUNTER，也不清层/金鳞。PASS。
- 日志：`scripts/test/win/logs/1.21.1-20261112-213646.log:19349` `AP_COUNTER_ACTIVATE player=Dev souri=true duration=200`，:19350 `AP_COUNTER_FIRE ... attacker=僵尸 free=false`。

### 3.2 跨 tick 免费多击与 200t 到期

- hit1：charges 4→3、gold 0→1、active=true、rem=200、zombieHp 1024→1023.06。
- hit2（+2.2s）：charges 3、gold 1、active=true、rem=153、zombieHp 1022.12。
- hit3（+2.2s）：charges 3、gold 1、active=true、rem=107、zombieHp 1021.18。
- 等 11s：`{active:false, rem:0, charges:3, gold:1}`。
- 结论：状态内免费多击，不重复扣层/加金鳞，200t 后自然结束。PASS。
- 日志：wrapper:25562 activate、:25563 `free=false`、:25769 `free=true`、:25969 `free=true`。

### 3.3 同 tick 一次挥击只结算一次

- before：`{charges:4, gold:0, active:false}`。
- hit1：`{charges:3, gold:1, active:true, rem:200, zombieHp:1023.06}`。
- hit2（同 tick）：`{charges:3, gold:1, active:true, rem:200, zombieHp:1023.06}`（不再掉血）。
- 结论：`DeathTriggerGuard.claimPair` 拦截第二次注入。PASS。
- 日志：wrapper:45580 activate、:45581 `free=false`、:45582 `free=true`。

### 3.4 魔法 / 技能 / 远程不激活

- `/damage Dev 1 minecraft:magic`：`{charges:4, gold:0, active:false}`。
- `/damage Dev 1 astral_dice:skill_damage`：`{charges:4, gold:0, active:false}`。
- `/damage Dev 1 minecraft:arrow`：`{charges:4, gold:0, active:false}`。
- `CounterStateManager.isFriendlyTarget(player)=true`。
- 结论：三类伤害都不激活 10 秒状态。PASS（无激活日志，符合预期）。

### 3.5 车万女仆友方

- 召唤 `touhou_little_maid:maid`，`isFriendlyTarget(maid)=true`，女仆 `ATTACK_DAMAGE=2`。
- 女仆 `doHurtTarget(player)`：`{charges:4, gold:0, active:false, playerHp:18.95, maidHp:20}`。
- 结论：玩家真实掉血，但女仆作为友方既不激活、也不被反击。PASS。

### 3.6 安全气囊

- 近战：`tryNegateFatal(p, mobAttack(zombie))` → `{ok:true, active:true, rem:200, counterCharges:0, gold:1, stacks:0, airbagEquipped:true, zombieHp:1023.06}`。
- 魔法：`tryNegateFatal(p, magic())` → `{ok:true, active:false, rem:0, counterCharges:0, gold:0, stacks:0, zombieHpBefore:1024, zombieHpAfter:1024}`。
- 结论：近战致命伤无效化并激活/反击，不扣层；魔法致命伤无效化但不激活/不反击。PASS。
- 日志：wrapper:36933 activate（安全气囊近战）。

## 4. forge-1.20.1

### 4.1 金鲤 4 层与护盾消失

- 先 `SMI.addCounter(p,4); RSM.grantShield(p,false)` → `{charges:4, shielded:true, gold:0}`。
- 僵尸命中后 → `{active:true, rem:200, charges:3, gold:1, shielded:true, playerHp:20.0, zombieHp:1023.06}`。
- `RSM.voidShield` 后 → `{active:true, rem:200, charges:3, gold:1, shielded:false}`。PASS。
- 日志：`run/forge-1.20.1/logs/latest.log:9317` activate、:9319 `free=false`。

### 4.2 跨 tick 免费多击与到期

- 命名僵尸 `CounterTarget`（id 245）：
  - hit1：charges 4→3、gold 0→1、active=true、rem=200、zombieHp 1024→1023.06。
  - hit2（+2.2s）：charges 3、gold 1、rem=154、zombieHp 1022.12。
  - hit3（+2.2s）：charges 3、gold 1、rem=108、zombieHp 1021.18。
  - 等 11s：active=false、rem=0、charges=3、gold=1。PASS。
- 日志：`latest.log:16816` activate、:16817 `free=false`、:16964 / :17111 `free=true`。

### 4.3 同 tick 一次挥击只结算一次

- before 4/0/false；hit1 `3/1/true/200`、zombieHp 1023.06、playerHp 15；hit2 `3/1/true/200`、zombieHp 1023.06（不再掉），playerHp 16。PASS。
- 日志：`latest.log:19059` activate、:19060 `free=false`、:19061 `free=true`。

### 4.4 魔法 / 技能 / 远程不激活

- `/damage Dev 1 minecraft:magic`、`/damage Dev 1 astral_dice:skill_damage`、`/damage Dev 1 minecraft:arrow` 后均 `charges=4, gold=0, active=false`；magic/skill 后 playerHp=19，arrow 后 playerHp=20；`isFriendlyTarget(player)=true`。PASS。
- 限制：1.20.1 的 arrow 那次没有造成可见 hp 变化，因此「arrow 伤害不激活状态」只证明了未激活；arrow 实际伤害路径以 1.21.1/26.1.2 的掉血读数为准。

### 4.5 车万女仆友方

- 1.20.1 测试环境未安装车万女仆；`summon touhou_little_maid:maid` 报 `无法找到类型为“minecraft:entity_type”的元素“touhou_little_maid:maid”`（`run/forge-1.20.1/logs/latest.log:20871`）。
- **未验证，不能写 PASS**；玩家友方分支由第 4.4 节的 `isFriendlyTarget(player)=true` 覆盖。

### 4.6 安全气囊

- 近战 `tryNegateFatal(p, mobAttack(zombie))` → `{ok:true, active:true, rem:200, counterCharges:0, gold:1, stacks:0, playerHp:20, zombieHp:1023.06}`。
- 魔法 `tryNegateFatal(p, magic())` → `{ok:true, active:false, rem:0, gold:0, stacks:0, zombieHp:1024→1024}`。PASS。
- 日志：`latest.log:26291` activate。

## 5. neoforge-26.1.2

### 5.1 金鲤 4 层与护盾消失

- `afterGrant={charges:4, shielded:true, gold:0}`。
- husk 命中后：`{active:true, rem:200, charges:3, gold:1, shielded:true, playerHp:20, huskHp:19.06}`。
- `RSM.voidShield` 后：`{active:true, rem:200, charges:3, gold:1, shielded:false}`。PASS。
- 日志：`run/neoforge-26.1.2/logs/latest.log:1313` activate、:1314 `free=false`。

### 5.2 跨 tick 免费多击与到期

- hit1：charges 4→3、gold 0→1、active=true、rem=200、huskHp 19.06。
- hit2（+2.3s）：charges 3、gold 1、rem=152、huskHp 18.119999。
- hit3（+2.3s）：charges 3、gold 1、rem=104、huskHp 17.179998。
- 等 11s：active=false、rem=0、charges=3、gold=1。PASS。
- 日志：`latest.log:2168` activate、:2169 `free=false`、:2217 / :2265 `free=true`。

### 5.3 同 tick 一次挥击只结算一次

- before 4/0/false；hit1 `3/1/true/200`、huskHp 19.06；hit2 `3/1/true/200`、huskHp 19.06（不再掉）。PASS。
- 日志：`latest.log:2817` activate、:2818 `free=false`、:2819 `free=true`。

### 5.4 魔法 / 技能 / 远程不激活

- 无护盾、charges=4、佩戴金鲤、吸收清零，直接 `hurtServer`：
  - `indirectMagic(husk,husk)`：`{ok:true, playerHp:20→12, charges:4, gold:0, active:false}`。
  - `ModDamageTypes.skillDamage(lvl,husk)`：`{ok:true, playerHp:20→19, charges:4, gold:0, active:false}`。
  - `DamageTypes.ARROW`（source entity=husk）：`{ok:true, playerHp:20→14, charges:4, gold:0, active:false}`。
- 结论：三类伤害都真实掉血，但都不进入 10 秒状态。PASS。

### 5.5 车万女仆友方

- 26.1.2 测试环境未安装车万女仆；`summon touhou_little_maid:maid` 未生成实体，`run/neoforge-26.1.2/mods` 无 TLM。
- **未验证，不能写 PASS**。

### 5.6 安全气囊

- 近战 `tryNegateFatal(p, mobAttack(husk))` → `{ok:true, active:true, rem:200, counterCharges:0, gold:1, stacks:0, airbagEquipped:true, huskHp:20→19.06}`。
- 魔法 `tryNegateFatal(p, magic())` → `{ok:true, active:false, rem:0, counterCharges:0, gold:0, stacks:0, huskHp:19.06→19.06}`。PASS。
- 日志：`latest.log:9285` activate（安全气囊近战）。

## 6. 限制与未验证项

- 1.20.1、26.1.2 未安装车万女仆，友方女仆路径未实测；不把「玩家友方」等同于「女仆友方」。
- 26.1.2 未用第二玩家触发玩家攻击者路径；只验证了 `isFriendlyTarget(player)=true`（代码层）与 1.21.1 女仆实机路径。
- 26.1.2 的 `flatworld` 是测试资产：从服务端目录 `run/neoforge-26.1.2/testworld` 复制到 `saves/flatworld`，不是产品改动；原有 `saves/testworld` 仍是 noise 世界，不要拿它当超平坦判据。
- 26.1.2 玩家吸收值会干扰 `/damage` 读数，非近战用例改用 `hurtServer` 并清零吸收；这不改变产品逻辑。
- 1.21.1 / 1.20.1 / 26.1.2 的安全气囊用直接 `tryNegateFatal` 调用，未模拟完整「致命伤害事件 + 6 充能」流程；事件接线在 1.21.1 代码与构建中已存在，本报告只对方法级行为下结论。
- 本轮未 commit / 未 push / 未打 tag / 未上传 CurseForge；五线仍是 .18 构建产物。

## 7. 收尾

- 三条实机客户端均已停止；`netstat` 中 25565 / 25575 / 25585 无 LISTENING，仅有 TIME_WAIT 残留。
- 测试脚本均在 `C:/Users/xmace/AppData/Local/Temp/`，未进仓库。
