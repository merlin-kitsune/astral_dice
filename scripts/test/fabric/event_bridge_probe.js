// 事件桥取证探针（Fabric 1.20.1 线）—— 证明「桥不只是装了，而是真的会派发」。
//
// 背景：无玩家时派发统计会显示 32 类事件 0 次 —— 这既可能是「桥没接」也可能是
// 「确实没发生」。本探针主动制造交互来区分两者：
//   1. 生成僵尸 → 施加伤害 ⇒ LivingAttackEvent / LivingHurtEvent / LivingDamageEvent
//   2. 致命伤害            ⇒ LivingDeathEvent / LivingDropsEvent
//   3. 施加/移除状态效果    ⇒ MobEffectEvent.Added / Remove
//
// ⚠️ 全部用 `var` 而非 `const`/`let`：回调在每个 tick 都会执行一次，而 KubeJS 用的
//    Rhino 会在第二次执行时报 `redeclaration of var X`（实测 2026-09-29）。
//
// 用法：放进 <runDir>/kubejs/server_scripts/ 再启动服务端；结果看日志里的
// `[event-probe]` 行与随后的 `事件派发统计`。

var PROBE_TICK = 300; // 开局 300 tick(15s)后执行，留足世界与数据包加载时间

ServerEvents.tick(function (event) {
  var server = event.server;
  if (!server || server.tickCount !== PROBE_TICK) {
    return;
  }
  console.info('[event-probe] 开始事件桥取证');

  var level = server.getLevel('minecraft:overworld');
  if (!level) {
    console.error('[event-probe] 拿不到 overworld，跳过');
    return;
  }

  try {
    var zombie = level.createEntity('minecraft:zombie');
    zombie.setPosition(0, 100, 0);
    level.addFreshEntity(zombie);
    console.info('[event-probe] 已生成僵尸');

    var src = level.damageSources().generic();
    zombie.attack(src, 2.0);
    console.info('[event-probe] 已施加 2.0 伤害(期望 Attack/Hurt/Damage)');

    zombie.attack(src, 9999.0);
    console.info('[event-probe] 已施加致命伤害(期望 Death/Drops)');
    console.info('[event-probe] 僵尸存活=' + zombie.isAlive());
  } catch (err) {
    console.error('[event-probe] 伤害/死亡探针异常: ' + err);
  }

  try {
    var cow = level.createEntity('minecraft:cow');
    cow.setPosition(3, 100, 0);
    level.addFreshEntity(cow);
    cow.potionEffects.add('minecraft:poison', 40, 0);
    console.info('[event-probe] 已上中毒(期望 MobEffectEvent.Added)');
    // ⚠️ 2026-09-29 实测修正:`potionEffects.remove(id)` **不存在** ——
    //    KubeJS 的 `EntityPotionEffectsJS` 只有 add / clear 一类方法,没有按 id 移除;
    //    旧写法让本探针在 228 行抛 `TypeError: Cannot find function remove in object
    //    …EntityPotionEffectsJS` ⇒ MobEffectEvent.Remove 的举证**从未完成**。
    //    改走原版方法 `removeAllEffects()`(LivingEntity 自带,KubeJS 代理原版方法名)。
    cow.removeAllEffects();
    console.info('[event-probe] 已移除全部效果(期望 MobEffectEvent.Remove)');
  } catch (err) {
    console.error('[event-probe] 效果探针异常: ' + err);
  }

  console.info('[event-probe] 取证脚本执行完毕');
});
