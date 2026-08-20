# Changelog

## 1.0-SNAPSHOT.23

### 物品 ID 与 Tag
- 效果牌统一为 `effect_card_*`。
- 筹码统一为 `*_chip`。
- 活体书页改为 `effect_card_living_page`。
- 新增物品 Tag：`dices`、`combat_cards`、`effect_cards`、`is_exclusive`、`signs`、`chips`、`materials`。

### 配方
- 基础骰子改为红石 + 石英块。
- 黄金/钻石/下界合金骰子统一为 `dice_upgrade` 有序升级；移除下界合金骰子锻造配方。
- 调整大量卡牌、效果牌、立牌、筹码配方。
- 筹码通用升级模板：
  - 蓝→紫：`LGL/GTG/PPP`
  - 紫→金：`RDR/DTD/GGG`
- 定向爆破改为有序对称配方。

### 战利品
- 星盘可从所有原版宝箱开出。
- 黄金星盘可从 `minecraft:chests/trial_chambers/reward_ominous` 开出。

### 创造栏
- 材料移至最前端。
- 调整效果牌与活体书页顺序。

### 数值与 Tooltip
- 拳击手套攻击：+2/+4/+8。
- 速度轮滑移速：+5%/+15%/+25%。
- 摩托头盔防御：+2/+4/+6；高级额外盔甲韧性 +2。
- 夹心饼干生命：+4/+8/+12。
- 缓冲盾牌冷却改为 30 秒。
- 手电筒：每 4 点星光 +1 攻击力。
- 效果牌 Tooltip 新增出牌数与当前周期伤害加成提示。