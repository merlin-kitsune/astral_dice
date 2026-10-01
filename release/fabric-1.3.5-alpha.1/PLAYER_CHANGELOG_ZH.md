# 星之骰戏 —— Fabric 1.20.1（移植线）

**版本 `1.3.5-alpha.1`** · alpha 渠道（预发布）· Minecraft 1.20.1 · Fabric

> ⚠️ **这是 Fabric 移植线，不是正式版。** 正式版面向 NeoForge 1.21.1 / Forge 1.20.1 / NeoForge 26.1.2。
> 本线使用**独立版本号**（`-alpha.N`，与 26.1.2 线的 `-beta.N` 同构）⇒ 永不占用正式版的版本号。

## 必需前置

- **Fabric Loader 0.19.5+**
- **Fabric API**（1.20.1 线，0.92.12）
- **StarEngine Lib —— 已内嵌，请勿另行安装。** 通过 Jar-in-Jar 内嵌（`1.0.6-alpha.1`）。
  若 mods 目录里仍有 `starengine_lib-*.jar`，请删除：散装文件优先级高于内嵌件，会被拿来顶替内嵌版本。
- **Puzzles Lib 8.1.33+**（事件桥；请连同它自己的前置 **Forge Config API Port 8.0.3** 一起装）
- **饰品栏提供者 —— Trinkets 3.7.2+ 或 Accessories 1.0.0-beta.48+ 二选一。** Fabric 的 `depends`
  是 AND 语义、无法表达「二选一」，因此两者都只声明为可选；模组在启动时自行检查，
  **只有两者都不在**才会拒绝启动。

可选：**Patchouli**（游戏内手册）。1.20.1 没有 Fabric 版 Bountiful，故本线没有赏金板。

## 本版更新

- **队友 / 队伍判定现在真的生效了。** 内嵌的 StarEngine Lib 有一处反射缺陷：FTB Teams 与
  Open Parties and Claims 两处联动指向的类与方法**根本不存在**，而且失败被静默吞掉 ——
  装了这两个模组中任意一个的玩家会被当作「没有队伍」，退化成「全服皆友方」。
  反射目标已按两个模组**真实的接口**逐条改正，并且每条后端现在都会打印一行**启动日志**
  （`back_ftb` / `back_opac` 显示 `on` 或 `off`），「装了却没生效」一眼可见。

## 本线的已知限制

- **存档与玩家数据与其他线不互通。** Fabric 与 Forge/NeoForge 的存储方式不同，这是平台差异、不会修复。
- **本线没有**：Curios 系饰品（请改用 Trinkets / Accessories）、Bountiful、Iron's Spells 'n Spellbooks。
- **1.3.3 那批改动尚未移植到本线**（例如隐匿效果）。
- 两个需要**指定药水**的配方按**严格匹配**（含 NBT）。部分配方查看器只显示基础药水；
  约束在服务端强制执行，并会同步到客户端的配方书。
