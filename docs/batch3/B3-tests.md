# B3 — 测试资产修复（ENDER 致死注入 + NANCY 探针 Rhino 异常）+ 1.20.1 全量实跑

- 执行者：测试资产实施者（子代理）
- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-1.20.1-1.21.1`，HEAD(开工) `a67caa7`（工作树干净）
- 改动范围（硬约束）：**只动测试资产** `scripts/test/**`。产品源码（`*/src/main/java`）、lang、
  CHANGELOG、`AGENTS.md`、`build.gradle`、`tools/**` 一律不动。
- 上游证据：`docs/batch3/B2-tests-fix.md`（工具链 4 项 + 探针重载修复 + 用例时序修复 + 新增 C1/C4 + ANVIL 修复）

---

## 0 开工前的两条关键取证（决定了任务 1 的路线）

### 0.1 `NANCY-LU-PEARL-IMMUNE` 的 Rhino 异常：根因已定位到**具体一行**

`run/1.21.1/logs/kubejs/server.log`（20:00 会话）里 20 条同一异常：

```
java.lang.IllegalStateException: Missing key in ResourceKey[minecraft:root / minecraft:damage_type]:
    ResourceKey[minecraft:damage_type / minecraft:5.0]
  at net.minecraft.world.damagesource.DamageSources.source(DamageSources.java:74)
  at …kubejs.plugin.builtin.wrapper.DamageSourceWrapper.wrap(DamageSourceWrapper.java:17)
  at …rhino.Context.internalJsToJava / NativeJavaMethod.call
```

字节码取证（`javap` 两个 jar）：

- `DamageSourceWrapper.wrap(RegistryAccessContainer, Object)`：`instanceof DamageSource` → 原样返回；
  `instanceof Player/LivingEntity` → 转攻击源；**否则** `ID.mc(obj)` → `ResourceLocation` →
  `DamageSources.source(…)` ⇒ 把 `5.0` 解析成 `minecraft:5.0` 伤害类型 id 并抛 `IllegalStateException`。
- `kubejs-neoforge-2101.7.2` 的 `dev.latvian.mods.kubejs.core.EntityKJS`：
  `kjs$damage(float)`、**`kjs$damage(float, DamageSource)`**、`kjs$attack(float)`、
  **`kjs$attack(DamageSource, float)`**（`@RemapPrefixForJS("kjs$")` ⇒ JS 侧去前缀）。

⇒ **KubeJS 的 `damage` 参数顺序是 `(amount, source)`，与 `attack` 的 `(source, amount)` 相反。**
探针 `applyFallDamage` 里写的是 `p.damage(src, amount)`（`FALL_DAMAGE_AMOUNT = 5.0`）⇒
`src` 落到 `float` 形参、**`5.0` 落进 `DamageSource` 形参** ⇒ 与日志里的 `minecraft:5.0` 逐字吻合。
B2 文档 `:1661-1663` 把这一条归因写成「`p.hurt(src, number)` 不可用」是**误记**：
`hurt(DamageSource, float)` 是原版方法、顺序本就正确；真正错序的是 `damage`。

同时可解释「同一异常重复 20 次」：珍珠实体在命中后仍存活约 20 tick（= 1 秒，日志时间戳同为一秒），
tick 观察窗每 tick 走到同一句。

### 0.2 任务 1 的前置更正：`/kill`（= `generic_kill`）**打不出末影骰保命**

- 标签取证（`neoforge-21.1.235-client-extra-aka-minecraft-resources.jar` 内
  `data/minecraft/tags/damage_type/bypasses_invulnerability.json`）：
  **只含** `minecraft:out_of_world` 与 `minecraft:generic_kill`。
- 产品源码 `neoforge-1.21.1/.../event/EnderDiceHandler.java:165`（`1.20.1` 同款 `:164`）：
  ```java
  if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
  ```
  与原版 `LivingEntity#checkTotemDeathProtection` 一致 ⇒ `/kill` 会被本模组**故意**跳过
  （AIRBAG 那条是**另一条口径**：气囊改成了「连绕过无敌的伤害也拦」，故 `/kill` 对它有效）。
- 结论：`/kill` 用于末影骰保命相位会**直接杀死玩家**（死亡界面 ⇒ 后续注入全失效）。
  按用户「若 `/kill` 路线仍打不出保命就停下来报告、不得弱化断言」的要求，本批**如实报告该前提更正**，
  致死注入改用 AIRBAG 用例里**已实证的那条直接 Java 调用路线**
  （`airbagApplyKill` 的 `entity.hurt(src, 1000.0)` 分支；同样不经 `/damage`、不经 `causeFallDamage`、
  不经 KubeJS 的 `damage`），伤害源换成**不在 bypass 标签内**的 `minecraft:generic`。
  断言**一条不弱化**：反而新增 `bypass=`（诊断）与 `fired=1`（保命确实触发）两条更强判据。

---

## 1 进度

- [x] 0 取证
- [ ] 1 ENDER-DICE-TOTEM-GLOWING 改致死注入
- [ ] 2 NANCY-LU-PEARL-IMMUNE 修 Rhino 异常
- [ ] 3 实跑 1.21.1 受影响条目
- [ ] 4 实跑 1.20.1 全量
- [ ] 5 收尾（--phase stop / 本地提交）
