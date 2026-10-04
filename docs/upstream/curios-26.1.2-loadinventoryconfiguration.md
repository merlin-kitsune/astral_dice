# 【上游缺陷报告】Curios 15.0.0（26.1.2 分支）：`loadInventoryConfiguration()` 按「数据包原始基础尺寸」搬移物品，导致 base size 为 0 的栏位重登后内容全部被退回玩家背包

> 本文件是**待提交给上游的缺陷报告 + 补丁**的本地底稿（`docs/` 不入库）。
> 补丁：`temp/curios-fix-26.1.2-loadinv-size.patch`（`git format-patch` 产物，可 `git am` 直接应用）。
> 复现与取证工程：本仓库的 26.1.2 子项目（`neoforge-26.1.2`）+ `scripts/test` 工具链。

---

## 1. 环境

| 项 | 值 |
|---|---|
| Curios | 15.0.0，26.1.2 分支，commit `8f2f132`（`git log -1 --format=%H` 于 `TheIllusiveC4/Curios`） |
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.109 |
| Java | 25 |
| 现象平台 | 单机（内嵌服务端）与专用服务端均复现 |

## 2. 现象

玩家**退出重进**（或服务端执行 `/reload` 触发数据包同步）后：

- 饰品栏中**内容仍然合法**（物品在 `#curios:<槽位id>` 标签内、`isItemValid` 返回 true）的物品被**搬进玩家背包**；
- 栏位的**槽位数量本身正常**（不是「尺寸算错」的外观问题）；
- 物品**不会丢失**，也不会掉在地上——总是出现在主物品栏（由 `CurioInventoryCapability#handleInvalidStacks()` 归还）。

关键点：**只有当该栏位的数据包 `size`（base size）小于玩家实际拥有的槽位数时才会中招**。以本模组的筹码栏（`data/<modid>/curios/slots/chip.json` 写 `"size": 0`，尺寸完全由骰子饰品给出的槽位修饰符决定）为例，2 格筹码栏里放 2 个筹码，重登后 2 个筹码都被退回背包。

## 3. 根因

`top.theillusivec4.curios.common.capability.CurioInventory#loadInventoryConfiguration()`（26.1.2 分支，`8f2f132`）：

```java
120        ICurioStacksHandler curioStacksHandler = defaultInventory.get(slotType);
121        int defaultSize = curioStacksHandler.getSlots();     // ← 此刻 dataLoaded == false
122        int oldSize = prevStacksHandler.getSlots();
123        curioStacksHandler.copyModifiers(prevStacksHandler);
...
132        while (index < curioStacksHandler.getSlots()          // ← 同样被 dataLoaded 守卫
133                   && index < prevStacksHandler.getSlots()) {
...
160        while (index < prevStacksHandler.getSlots()) {        // ← 剩下的全部当「非法栈」
161          this.invalidStacks.add(prevStacksHandler.getStacks().getStackInSlot(index));
162          this.invalidStacks.add(prevStacksHandler.getCosmeticStacks().getStackInSlot(index));
163          index++;
164        }
...
206          curioStacksHandler.setDataLoaded();                 // ← 本方法末尾**才**放行
```

`CurioStacksHandler#update()` 的第一行是 `if (this.dataLoaded)`：

```java
460  public void update() {
462    if (this.dataLoaded) {
```

而 `getSlots()` 依赖 `update()` 才会把尺寸算成「基础尺寸 + Σ 修饰符」：

```java
280  public int getSlots() {
281    this.update();
282    return this.stackHandler.getSlots();
283  }

505  private void resize(int newSize) { ... }   // update() 内部按 base + 修饰符调用
```

`createDefaultInventory()` 新建的 `CurioStacksHandler` 的 `dataLoaded` 为 `false`，而 `setDataLoaded()` 直到第 206 行（循环结束后的落库步骤）才被调用 ⇒ **第 121 与第 132 行的 `getSlots()` 都退化为构造函数里的原始基础尺寸**（`new CurioStacksHandler(this, id, slotType.getSize(), ...)` 传给 `DynamicStackHandler` 的 `size`），与第 123 行刚复制过来的修饰符完全无关。

于是对 `size: 0` 的栏位：

1. 第 132 行的搬移上界 = `min(0, oldSize)` = **0** ⇒ 一个槽位都不搬；
2. 第 160 行的收尾循环把旧栏位 `0..oldSize-1` 的内容**全量**塞进 `invalidStacks`；
3. `CurioInventoryCapability#handleInvalidStacks()` 在 tick 末把它们交还玩家背包 ⇒ 用户看到的「重登掉饰品」。

### 3.1 同源的第二个副作用（`size_shift` 重复计算）

第 121 行的 `defaultSize` 读在 `copyModifiers()` **之前**，因此它是「基础尺寸」，而 `oldSize`（旧 handler，`dataLoaded == true`）是「基础尺寸 + 修饰符」。当尺寸完全由**持久化修饰符**决定时（本模组：`astral_dice:chip_slots`，`AttributeModifier.Operation.ADD_VALUE`，每级骰子星级 +1），`oldSize != defaultSize` 恒成立 ⇒ 第 135~139 行会额外追加一个 `curios:size_shift = oldSize - defaultSize` 的**瞬态**修饰符，与已复制的持久修饰符**重复叠加**：

- 登录瞬间尺寸虚高（实测 base 0 + 持久 +2 + `size_shift` +2 = **4** 格，玩家实际只有 2 格）；
- 随后 `CuriosCommonEvents` 每 tick 的 `clearCachedSlotModifiers()` 清掉瞬态修饰符 → 尺寸掉回 2。

也就是说，即使把搬移循环的上界修正，`size_shift` 的语义仍然应该是「**无法由修饰符表达**的那部分尺寸差」，即 `oldSize - (base + 已复制修饰符)`。

## 4. 最小复现

1. 数据包注册一个 `"size": 0` 的槽位类型（`data/<modid>/curios/slots/<id>.json`）；
2. 用标准途径给它加槽位：`ICurioStacksHandler#addPermanentModifier(new AttributeModifier(id, 2, ADD_VALUE))`（或任何装备驱动的 `SlotAttribute` 修饰符）；
3. 往槽里放 2 个通过 `#curios:<id>` 校验的物品；
4. 保存 → 退出 → 重进（或 `/reload`）→ 2 个物品出现在玩家主物品栏，栏位为空。

对照：把数据包 `"size"` 改成 `2`（并去掉修饰符）后不再复现 ⇒ 与标签、校验器、序列化都无关，只与「搬移上界取的是基础尺寸」有关。

## 5. 建议的修法（本仓库已实现并验证，见补丁）

把「放行数据加载」提前到复制修饰符之后、读取 `defaultSize` 之前：

```diff
         ICurioStacksHandler curioStacksHandler = defaultInventory.get(slotType);
-        int defaultSize = curioStacksHandler.getSlots();
         int oldSize = prevStacksHandler.getSlots();
         curioStacksHandler.copyModifiers(prevStacksHandler);
+        // 必须先放行数据加载，下面的 getSlots() 才会按「基础尺寸 + 已复制修饰符」重算。
+        // CurioStacksHandler#update() 的首行是 `if (this.dataLoaded)`，而 setDataLoaded() 原本只在
+        // 本方法末尾才调用 ⇒ 在此之前 getSlots() 一直返回构造函数里的原始基础尺寸：
+        //   ① defaultSize 少算了刚复制的修饰符（尺寸完全由修饰符给出的栏位会算错 size_shift）；
+        //   ② 更要命的是下面搬移循环的上界退化成基础尺寸，base size 为 0 的栏位一个槽位都搬不过来，
+        //      旧内容全部落进 invalidStacks 并被 handleInvalidStacks() 退回玩家物品栏。
+        if (curioStacksHandler instanceof CurioStacksHandler handler) {
+          handler.setDataLoaded();
+        }
+        int defaultSize = curioStacksHandler.getSlots();
 
         if (oldSize != defaultSize) {
```

为什么是这两处、而不是「把循环上界改成 `oldSize` 了事」：

- 循环上界必须取**新 handler 的真实尺寸**。写死 `oldSize` 会在数据包**缩小**基础尺寸时让物品写进不存在的槽位（越界 → `resize()`/`loseStacks()` 语义混乱）；`setDataLoaded()` 让 `getSlots()` 自己给出正确值，两个方向都安全。
- `defaultSize` 必须取「已含复制修饰符」的尺寸，否则 `size_shift` 会与修饰符重复叠加（第 3.1 节），每次登录都会瞬时虚高一次。
- `setDataLoaded()` 不在 `ICurioStacksHandler` 接口上，故用 `instanceof CurioStacksHandler` 判定；`defaultInventory` 的实例恒由 `createDefaultInventory()` 构造，判定必然成立，其它实现（若将来出现）退化为原行为。
- 副作用评估：提前放行后 `getSlots()`/`getRenders()`/`getActiveStates()` 都会走 `update()`，其中 `resize()` 只会在「基础尺寸 + 修饰符」与当前尺寸不同时才动，且此时新 handler 尚未装载任何物品，收缩不会丢物品（`loseStacks` 作用于空栏位）；`update()` 末尾的 `curiosMenu.resetSlots()` 仅在玩家正开着 Curios 界面时执行，属期望行为；`flagUpdate()` 已由 `copyModifiers()` 触发，`updates` 集合状态无变化。

## 6. 验证

- 本仓库 26.1.2 子项目以**上游未打补丁**的 Curios 15.0.0 复现缺陷，并用「本模组自管迁移」（`event/ChipSlotMigrationHandler`，HIGHEST 快照+清空 / LOWEST 还原）作为**产品侧兜底**，行为用例 `scripts/test/cases/CHIP-RELOG-{A,B}-26.1.2.json` 覆盖「0 号位与 1 号位筹码跨重登留存」。
- 本补丁在 `temp/curios_src`（clone 自 `8f2f132`）上以分支 `fix/26.1.2-loadinv-size` 提交，`git format-patch` 产物见 `temp/curios-fix-26.1.2-loadinv-size.patch`。
  **未在本机编译 Curios 本体**（本地只有源码 clone，无其 CI 环境）；改动为「移动一行 + 插入一次 `setDataLoaded()` 调用 + 新增注释」，不涉及新符号与新依赖，类型均已在其自身源码内确认（`CurioStacksHandler` 已在文件顶部 import）。

## 7. 推送 / 提 PR 的具体命令（本机无 token、无 `gh`，需要人工执行）

```bash
# 0) 前提：本机 Git 网络须经代理（本仓库约定）
git config --global http.proxy  http://127.0.0.1:7897
git config --global https.proxy http://127.0.0.1:7897

# 1) 在已有的 Curios clone 上（本机路径 temp/curios_src）：
cd temp/curios_src
git checkout fix/26.1.2-loadinv-size     # 已含提交 9704c4c
git remote -v                            # 期望：origin 指向 https://github.com/TheIllusiveC4/Curios

# 2) 推到自己的 fork（先 fork，再把 origin 换成自己的 fork；或用下面的 upstream 形式）
git remote add fork https://github.com/<你的账号>/Curios.git
git push fork fix/26.1.2-loadinv-size

# 3) 建 PR（无 gh 时用网页）：
#    https://github.com/TheIllusiveC4/Curios/compare/26.1.2...<你的账号>:Curios:fix/26.1.2-loadinv-size
#    标题：Fix slot migration using the pre-modifier base size (items ejected on relog)
#    正文：直接粘贴本文件第 3、4、5 节

# 4) 提 issue（同样无 gh 时用网页）：
#    https://github.com/TheIllusiveC4/Curios/issues/new
#    标题：Slots whose size comes from modifiers (datapack size 0) are emptied on relog
#    正文：第 2、3、4 节 + 第 3.1 节的 size_shift 副作用；附上本文件的补丁链接
```

> 若希望用 `gh` 一把梭：
> ```bash
> gh auth login                      # 需要 token
> gh pr create --repo TheIllusiveC4/Curios --base 26.1.2 --head <你的账号>:fix/26.1.2-loadinv-size \
>   --title "Fix slot migration using the pre-modifier base size (items ejected on relog)" --body-file docs/upstream/curios-26.1.2-loadinventoryconfiguration.md
> ```

## 8. 附：本模组的产品侧兜底（即使上游不修也不会掉筹码）

`event/ChipSlotMigrationHandler`（26.1.2 子项目）：

- `OnDatapackSyncEvent` **HIGHEST**（早于 Curios 的 NORMAL 处理器）：把筹码栏（功能槽 + 装饰槽）内容快照为 `ItemStack#copy()` 并清空栏位 ⇒ Curios 的搬移循环既看不到待搬出的物品、也不会把它们塞进 `invalidStacks`；
- `OnDatapackSyncEvent` **LOWEST**（晚于 Curios）：先按当前佩戴的骰子重算栏位尺寸，再按**原索引**放回快照；放不下或不再通过校验的（例如刚换过骰子、槽位变少）交还玩家背包，与 Curios 的 `invalidStacks` 语义一致，**绝不静默丢弃**；
- 快照取在清空之前、恢复按索引覆盖写、残留快照在下次 HIGHEST 先交还玩家 ⇒ 既不复制也不丢失；
- 只碰筹码栏，其余栏位完全交给 Curios；整段 try/catch(Throwable)，异常只记日志，绝不让登录失败。

该兜底在上游修好之后仍然正确（对空槽是幂等的）。
