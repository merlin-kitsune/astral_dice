# 「星币钱包」前置调研：Magic Coins + SG-Economy 的钱包实现分析

> 调研日期 2026-09-21。分析对象：**magic_coins 1.1.3** 与 **sg_economy 1.0.5**（整合包 `狐の航空学 Voxy Edition` 内实测版本，
> NeoForge 1.21.1 / Java 21）；另参考 MagicCoins 官方仓库 `V1.21.1` 分支源码（更新的重构版，用于看演进方向）。
>
> ⚠️ **许可约束（硬红线）**：两者均为 **SirGrantd License**（≈ ARR）。该许可**明确允许**「阅读源码、研究方法、脚本等学习用途」，
> 但**禁止**修改 / 再分发 / 商业复用，且**禁止任何形式的 rework、改编或衍生作品**。
> ⇒ 本项目只做**功能层模仿**：自有类名、自有数据结构、独立编写，**不得粘贴/改写其任何代码、注释、标识符命名或文件结构**。
> 本调研文档只描述**机制与行为**，不含对方代码；`temp/ref/`（克隆源码 + 反编译产物）**永不入版本库**（`temp/` 已被 `.gitignore` 忽略）。

## 一、职责划分（两模组）

| 模组 | 定位 | 关键资产 |
|---|---|---|
| `sg_economy`（SG Economy API）| **余额账本 + 对外 API + 同步 + 命令**（钱包本体，与"钱币"解耦）| 玩家级余额附件、`EconomyEventProvider` 接口、C2S/S2C 同步、`/coins` 命令族、服务端配置、原版库存界面的余额浮层 |
| `magic_coins` | **实体钱币 + 钱包操作 UI + 面额兑换 + 护符** | 3 种面额钱币 + 护符、注入原版物品栏的按钮、按钮 → 网络包 → 服务端动作、战利品注入、客户端配置 |

依赖：`magic_coins` 硬依赖 `sg_economy`（`[1.0.5,)`）与 `curios`（`[9.4.2+1.21.1,)`）。
⚠️ 仓库 `libs/` 里带的编译期依赖是 `sg_economy-1.1.0.jar` 与 `celesthyd-1.0.0.jar`（第三方库，**1.1.3 尚未依赖 celesthyd**，分支 head 才用）。

## 二、SG-Economy：余额是怎么存、怎么同步的

### 2.1 存储载体
- 用 **NeoForge `AttachmentType`** 挂玩家实体：`AttachmentType.serializable(() -> new CoinsInBag()).build()`。
- ⚠️ 注册命名空间写死为 **`magic_coins`**（API 模组替消费方注册），附件 id = `magic_coins:coins_in_bag`，
  落盘位置 = 玩家 `.dat` 的 **`neoforge:attachments` → `magic_coins:coins_in_bag`**（这一点很关键：离线也能直接读）。

### 2.2 数据模型（单一余额 + 三条字段）
| 字段（NBT 键）| 类型 | 语义 |
|---|---|---|
| `ValueTotalInCoins` | int | **整数币系统**下的余额（配置为整数货币时使用）|
| `ValueTotalInCurrency` | long | **小数币系统**下的余额，单位 = **分（×100 定点）**；`getBalance()` 时 `/100.0` |
| `IsCoinsLostOnDeath` | boolean | 该玩家**是否**在死亡时失去余额（由护符等外部切换）|

- 反序列化保留了 **legacy 兼容**：若该键是 double（NBT 类型 6）⇒ `Math.round(legacy × 100)` 迁移为分。
- 也就是**两套并行表示**：整数币模式只用 int 那条，小数模式只用 long 那条；由服务端配置在**全局**二选一。

### 2.3 同步
- 自定义包 `magic_coins:coins_in_bag_sync`（S2C），`StreamCodec` 直接以 **NBT** 编解码（`writeNbt`/`readNbt`）。
- 发送时机：**登录 / 重生 / 跨维度**各发一次；客户端收到后写入自己实体上的同名附件（客户端镜像，供 UI 读）。
- ⚠️ 服务端每次**写余额都会立即推一次同步**（repository 的每个写方法末尾都调用 sync）——量大但简单可靠。

### 2.4 API 面（供其它模组调用）
`SGEconomyApi.get()` 拿到 `EconomyEventProvider`：
`isDecimalSystem()` / `hasCoinsBag(entity)` / `hasBalance(entity, amount)` / `getBalance` / `setBalance` /
`depositBalance` / `withdrawBalance` / `transferBalance(from,to,amount)` / `balanceLostOnDeath` /
`setBalanceLostOnDeath` / `getPercentageBalanceSaveOnDeath()`；另有 `*AsInt` 的 default 便捷重载。
- 语义细节：负数一律拒绝（返回 false 并 log warn）；`withdraw`/`transfer` 先查余额；`balance` 全部以 **double** 对外。
- `EconomyServices.get()` 是内部同形入口（与 `SGEconomyApi` 同一单例实现），供模组内/死亡事件使用。

### 2.5 持久化仓库（Repository 层）
- 每个写方法都是**钳到 ≥ 0**（`Math.max(0, …)`）后立即 sync；读方法只取值。
- `hasCoinsBag(entity)` = 附件实例非 null（**恒真**，因此它其实是"实体是否支持经济"的弱判据）。

### 2.6 死亡规则
- `LivingDeathEvent` 里：若 `IsCoinsLostOnDeath = true` ⇒ 只保留 `percentageCoinsSaveOnDeath`%，
  否则**全保留**；百分比是**服务端配置**（默认 **100**，即默认"死了也不掉"）。
- `PlayerEvent.Clone` 把三条字段**无条件**复制到新实体（与 `isWasDeath` 无关）。
- ⇒ 「掉钱」实际由：`IsCoinsLostOnDeath`（每玩家开关，护符控制）× `percentageCoinsSaveOnDeath`（服务端百分比）共同决定。

### 2.7 命令族（`/coins`）
| 命令 | 权限 | 说明 |
|---|---|---|
| `add \| set \| remove <players> <amount>` | 2 | 批量加/设/减；`remove` 余额不足则报错不扣 |
| `get [player]` | 0 | 显示余额 |
| `pay <target> <amount>` | 0 | 玩家间转账，受配置 `isActivePayCommand` 总开关约束 |
| `rank [page]` | 0 | **排行榜**：在线玩家读实时余额；**离线玩家直接解析其 `.dat`**（从 `neoforge:attachments` 取 `magic_coins:coins_in_bag`，同时兼容 long 分值与 legacy double），按余额降序分页（每页 10）|

### 2.8 配置（服务端，加载/重载时 bake 并**广播给所有客户端**）
| 键 | 默认 | 说明 |
|---|---|---|
| `decimalCurrency` | `false` | 小数货币开关（改为 true 会影响既有玩家余额换算）|
| `percentageCoinsSaveOnDeath` | `100`（0..100）| 死亡保留百分比 |
| `isActivePayCommand` | `true` | `/coins pay` 总开关 |

### 2.9 客户端余额显示
- `ScreenEvent.Render.Post` 钩子：在**原版物品栏 / 创造模式物品栏 / Curios 界面**上绘制余额文本（`activeDisplayCurrency` + xy 偏移可配）。
- ⇒ 钱包没有独立 GUI 屏，"看余额"是在库存界面里叠一行数字。

## 三、Magic Coins：钱币与钱包操作 UI

### 3.1 物品（4 件）
`silver_coin` / `gold_coin` / `crystal_coin` / `prosperity_amulet`。
- 钱币：堆叠 **64**、**防火**、**恒带附魔光效**（`isFoil = true`，作为"这是货币"的视觉标识）。
- 护符：堆叠 1、防火、稀有度 RARE、`ICurioItem`（饰品），带光效。

### 3.2 面额与数值（服务端配置，客户端由同步副本读取）
| 键 | 默认值 | 说明 |
|---|---|---|
| `silverMagicValue` | **1.0** | 银币价值（0.01 ~ 1e6）|
| `goldMagicValue` | **50.0** | ⇒ 1 金 = 50 银 |
| `crystalMagicValue` | **2500.0** | ⇒ 1 水晶 = 50 金 = 2500 银 |
| `coinsLootChests` | `true` | 是否把硬币注入宝箱战利品 |
- ⚠️ 配置注释明确警告：**整数货币系统下数值会被向上取整**（如 1.5 → 2）。
- ⚠️ 1.1.3 的**兑换按钮用「面额值之比」当枚数比**（银→金按 `÷50`），这隐含假设 **银币价值 = 1**；若整合包把银币改成 3，
  兑换比例就会失真（分支 head 改成按价值整除，属**已修**的设计瑕疵）。

### 3.3 钱币的"右键即存入"
- `use(...)`：`存入 = 面额值 × 本次数量`（**Shift = 只存 1 枚**，避免误存整堆），成功则 `shrink(数量)` 并播放音效。
- 小数/整数两套模式分别走 `depositBalance(double)` / `depositBalanceAsInt(int)`。
- ⇒ **钱币是"可存入的实体代币"，钱包里存的是价值（数值），不是逐枚的物品栈**。

### 3.4 钱包 UI = 注入原版库存界面的按钮
- 客户端 `ScreenEvent.Init.Post`：在 **InventoryScreen / CreativeModeInventoryScreen / Curios 界面**上**动态添加按钮**
  （不是自建 Screen），每个按钮有**独立的启用开关 + xy 偏移**，且**创造模式用另一套偏移**。
- 1.1.3 的按钮：`COLLECT_COINS`（收硬币，18×20）+ 银/金/水晶 三枚按钮（各 13×13，位于背包格右侧）。
- 点击 ⇒ 发 **C2S 包**（含按钮名 + 坐标）⇒ 服务端**映射到枚举动作**执行 ⇒ 余额变动后再 S2C 同步给客户端 ⇒ 浮层数字更新。
  （⇒ 全链路**服务端权威**，客户端只发"意图"。）

### 3.5 按钮动作枚举（1.1.3，共 8 个）
`COLLECT_COINS`、`WITHDRAW_SILVER|GOLD|CRYSTAL`、`CONVERT_SILVER_FOR_GOLD`、`CONVERT_GOLD_FOR_CRYSTAL`、
`CONVERT_GOLD_FOR_SILVER`、`CONVERT_CRYSTAL_FOR_GOLD`。

### 3.6 各动作的确切语义（已逐条核对）
- **收硬币（COLLECT / DepositAll）**：清点背包里三种钱币枚数 → `总价值 = Σ(枚数 × 面额值)` → **一次** `depositBalance(总价值)`
  → 成功才从背包移除对应枚数。
- **取钱（WITHDRAW_*）**：① 由余额算出可取的枚数 `floor(余额 / 面额值 + ε)`；
  ② 用 `可放枚数 = min(该面额可取的枚数, 背包还能放下的枚数)` 截断（**背包空格是硬约束**）；
  ③ `withdrawBalance(枚数 × 面额值)` 成功后才把物品加进背包。**Shift = 取尽可能多（上限 64）**，否则只取 1 枚。
- **存钱（对应按钮/钱币右键）**：枚数 ≤ 64，Shift = 全存，否则 1 枚。
- **取全（分支 head 的 Bag 右键）**：按**水晶 → 金 → 银**贪心顺序取，每档都被背包空间截断，最后**一次性** `withdrawBalance(合计)`。
- **兑换（1.1.3）**：**只动背包里的钱币**（不进不出余额），按"低面额 → 高面额"整除换发（余数留在背包）；背包放不下就**掉在地上**。
- **压缩（分支 head 的 Bag + Shift）**：把背包里三种钱币合并成总价值，再按**大面额优先**重新发币（属"整理钱袋"功能，替代了 1.1.3 的四个兑换按钮）。

### 3.7 繁荣护符（Curio）——"钱包保护"位
- `onEquip` ⇒ `setBalanceLostOnDeath(entity, false)`；`onUnequip` ⇒ 置回 `true`（**装它就不会因死亡掉钱**）。
- 附加效果：`LivingDamageEvent.Post` 里，**当服务端本就是 100% 保留**（即护符"没用"）**且**玩家血量 ≤ 50% 时，
  给 **3 秒再生 I**（60 ticks）——即"护符没用时给点补偿"。
- 提示行会**按服务端配置**切换成"有效果 / 无效果"两套文案。

## 四、机制要点提炼（=> 我们独立实现时的功能清单，非代码）

1. **单一数值余额**（不是逐枚存栈）：面额只是"价值倍数"，余额是唯一真值 ⇒ 天然不会出现"钱袋里塞满钱币"的爆仓问题。
2. **两种数值域**：整数币 / 两位小数（分）——由**服务端配置全局二选一**，禁用浮点直接存余额（权威值恒为定点整数）。
3. **面额换算一律先算价值再动余额**，并用 `ε = 1e-6` 抑制浮点/取整误差；受→付时**先校验余额**、失败即不改状态。
4. **物理钱币 ↔ 虚拟余额**的桥是"背包空格"：取钱必须同时受**余额**与**可放枚数**双重约束。
5. **服务端权威 + 客户端只发意图**：UI 点击 → C2S 意图包 → 服务端校验并落账 → S2C 回推余额 → 客户端重绘。
6. **死亡规则可配且可被饰品覆盖**：全局百分比 + 每玩家开关（饰品 onEquip/onUnequip 切换）。
7. **管理面**：命令族（add/set/remove/get/pay/rank）+ **离线玩家排行榜**（直读 `.dat` 的附件段）。
8. **视觉标识**：钱币恒发光、防火、可堆叠 64（避免"货币与普通物品混淆"）。
9. **UI 挂载在既有界面**（库存/Curios）而非新屏：改动面小、无自定义容器同步负担。

## 五、与我们（astral_dice）现有资产的关系（**待裁决，先不动手**）

- 我们已有货币类物品：`star_coin`、`star_coin_bag`（星币袋）、`star_plate`（星盘）、`golden_star_plate`（黄金星盘）、
  `star_coin_dust`（星币尘，材料）；已有换算入口 `resource/ResourceConversion`（**2 点星光 = 1 个星币**）。
- 目前**没有任何** wallet / 钱袋 / 钱包 实现（全仓 grep 无命中）⇒ 「星币钱包」是全新功能，不与既有资产冲突。
- 若按上述机制仿写，需要我们先拍板的语义（见下节）与既有口径的**唯一真值源**冲突点：星币现在既是"物品"又是"星币袋 9 枚价值"的
  计价单位（`VerifyBountiful` 的 `unitWorth` 也用星币计价）⇒ 钱包若引入"虚拟余额"，必须明确它与 `star_coin` 物品、
  `star_coin_bag`（袋 = 9 枚）之间的关系。

## 六、下一步需要你裁决的问题

1. **钱包本质**：纯虚拟余额（存入后物品消失，查看/取出走 UI），还是"实体星币容器"（保留物品、只是扩容）？
2. **面额体系**：沿用现有 `star_coin` / `star_coin_bag`（1 袋 = 9 枚）+ 星盘/黄金星盘，还是新设面额与倍数？
3. **入口形态**：像它一样把按钮注入原版物品栏 / Curios 界面，还是做成**我们自己的 Curios 物品 + 独立界面**（例如"星币钱包"饰品位，右键打开）？
4. **死亡规则**：默认掉不掉？是否需要"钱包保护"类饰品？
5. **管理面**：是否需要 `/astralcoins` 类管理命令与排行榜（含离线玩家）？
6. **与星光的联动**：钱包是否参与既有 `星币 ↔ 星光` 换算（`STARLIGHT_PER_COIN = 2`）？
7. **双版本**：钱包是同时在 1.21.1 / 1.20.1 落地，还是先只做 1.21.1（26.1.2 线随后另批）？

## 七、环境与产物位置（均在 `temp/`，不入库）

| 内容 | 路径 |
|---|---|
| MagicCoins 源码（`V1.21.1` 分支，`--depth 1`）| `temp/ref/MagicCoins`（含 `LICENSE.md` 原文、`libs/`）|
| 反编译产物 | `temp/ref/dec/magic_coins-1.1.3`（39 类）、`temp/ref/dec/sg_economy-1.0.5`（19 类）|
| 反编译器 | `temp/ref/vineflower.jar`（1.12.0，Maven Central）|
| 本地 jar（实测版本）| `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods\{magic_coins-1.1.3.jar, sg_economy-1.0.5.jar}` |

**尚未完成**：`celesthyd`（分支 head 依赖的第三方库）未分析；1.1.3 客户端 `MagicButton` 的左/右键→动作**逐键映射**未逐条展开
（服务端 8 个动作语义已全部核对）。
