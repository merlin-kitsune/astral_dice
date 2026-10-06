# StarEngine Lib 1.0.12

内嵌（JarJar）于 **星之骰戏（Astral Dice）** 的共享前置库。本版让动作栏文本跟随原版的动态位置，
在状态条变高后不再与物品名提示重叠。

> 你**不需要**手动安装本库 —— 每个星之骰戏的 jar 都已内嵌它。
> 只有在你要针对它编译或调试它时，单独放一份才有意义。

## 修复

- **玩家身上有黄心（吸收心）时，动作栏文本可能与物品名提示重叠。**
  `client/ActionBarManager` 此前把文本画在固定的 `guiHeight - 68`，该值与原版规则只有在
  `max(leftHeight, rightHeight) <= 59` 时才等价。一旦左侧被堆高（黄心 ≥ 3 行、或「护甲 + 黄心」等组合），
  原版物品名会**上抬**而动作栏不动 ⇒ 两者压盖。现在改为复刻原版 `Gui#renderOverlayMessage`：

  - **两条 NeoForge 线**（`neoforge-1.21.1` / `neoforge-26.1.2`）：
    `yShift = max(leftHeight, rightHeight) + (68 - 59)`、`y = guiHeight - max(yShift, 68)`。
    NeoForge 已把 `Gui.leftHeight` / `Gui.rightHeight` patch 为 `public`，库可直接读取。
  - **`forge-1.20.1` / `fabric-1.20.1`**：**行为不变**。原版 1.20.1 没有这套机制 ——
    物品名与动作栏都处于固定位置，无从对齐。

## 变更

- 四平台版本 `1.0.11` -> `1.0.12`（库版本在 1.21.1 / 1.20.1 / 26.1.2 / Fabric 之间是**共用**的；
  只有 Fabric 子项目的**模组**版本保留独立的 `-alpha.N` 后缀）。
