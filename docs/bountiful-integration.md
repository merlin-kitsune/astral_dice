# Bountiful「赏金」Mod 联动说明

Astral Dice 与 [Bountiful](https://www.curseforge.com/minecraft/mc-mods/bountiful)(赏金板)为**可选前置联动**:安装 Bountiful 后,本 mod 的物品自动加入赏金板的需求池与奖励池;未安装时数据文件静默存在,无任何副作用。

## 自动生效方式(mod 自带数据包)

mod jar 内置 Bountiful 数据,随 mod 自动加载(无需配置):

| 文件 | 作用 |
|---|---|
| `data/bountiful/bounty_pools/bountiful/astral_objs.json` | 需求材料池:**非传奇骰子** + 货币(星币/袋装星币/星盘/黄金星盘) |
| `data/bountiful/bounty_pools/bountiful/astral_rews.json` | 可兑换奖励池:上述 + 全部卡牌 + 非传奇筹码 + 非传奇立牌 |
| `data/bountiful/bounty_pools/bountiful/astral_currency.json` | **填充货币池**(`currency: true`):星币/袋装星币/星盘/黄金星盘,供奖励价值不足时补齐差额 |
| `data/bountiful/bounty_decrees/bountiful/astral.json` | 悬赏令:需求池 × 奖励池组合,可在赏金板刷新出现 |

> ⚠️ `astral_currency.json` 是 `currency: true` 的填充货币池。要让它生效,整合包 `config/bountiful/bountiful.json` 的 `bounty.fillerCurrencyPool` 必须设为 `"astral_currency"`(默认 `null` = 无填充货币 ⇒ 高价值材料无法凑齐奖励价值)。这是**整合包级**配置,mod 数据包管不到。

条目数与内容由 `scripts/verify/verify_bountiful_pools.ps1` 守门(0 = 与规则一致):当前 `astral_objs` 13 条 / `astral_rews` 92 条。

游戏内 `/reload` 后生效。

## 稀有度映射(两层表示)

本 mod 物品品质按「白=普通 / 浅蓝=稀有 / 粉紫=史诗 / 金=传奇 / 亮红=巅峰」划分(2026-09-25 起 4 个自有档扩展进原版 `Rarity`),映射如下:
⚠️ 巅峰档**没有**数据层对应值 ⇒ 巅峰物品不得写入任何赏金池。

| 品质 | 物品层(Minecraft Rarity) | 赏金板数据层(Bountiful rarity) |
|---|---|---|
| 白=普通 | `Rarity.COMMON` | `COMMON` |
| 浅蓝=稀有 | `ASTRAL_DICE_RARE` | `RARE` |
| 粉紫=史诗 | `ASTRAL_DICE_EPIC` | `EPIC` |
| 金=传奇 | `ASTRAL_DICE_LEGENDARY`(2026-09-25 起为自有档) | **`LEGENDARY`**(金色,出现权重最低,声望要求最高) |

**品质规则**:
- 品质越高,`unitWorth`(价值)越高 → 需求材料价值更高(条件更苛刻);
- 品质越高,Bountiful rarity 权重越低 → 出现概率越低(权重:COMMON 1024 / RARE 256 / EPIC 128 / LEGENDARY 6);
- **传奇品质(金)的骰子、筹码与立牌不进入任何赏金池**(`astral_rews.json` 已排除 25 项)——
  - 骰子:下界合金骰子、绯红骰子、末影骰子、下界之星骰子;
  - 筹码:银行卡-用不完、拳击手套-高、速度轮滑-高、摩托头盔-高、夹心饼干-美味、美工刀-锋利、鹰眼瞄具、忍术飞镖、医疗箱-完备、星币锤、探天卫星、贯穿之铳、永动机、大碗炖肉、电磁炮、原初核心、肾上腺素-高效;
  - 立牌:邦妮、芬、美咲、凛;
  - **例外**:货币(黄金星盘为传奇但属货币,仍在池中)与卡牌(传奇卡不受排除)。

**价值带约定**(新增条目须落在同类同品质既有价值带内):
- 骰子按**阶层**(`astral_objs` / `astral_rews`):基础(T0)800/700、T1(黄金档 `golden_dice`/`glass_dice`/`netherrack_dice`)3000/2800、T2(钻石档 `diamond_dice`/`emerald_dice`/`obsidian_dice`/`weird_dice`/`amethyst_dice`)8000/7500;T3/T4 为传奇不入池;
- 货币:星币 250/220、袋装星币 = 9×星币(2250/1980)、星盘 4000/3800、黄金星盘 16000/9500;
- 卡牌:中 300、稀有 1500~1800、史诗 4000~6000、传奇 9000~10000;筹码:稀有 1200~1500、史诗 3500~4500、传奇 10000;立牌:稀有 1200~1600、史诗 3500~4500、传奇 10000。
  ⚠️ 2026-09-25 重排:卡牌/筹码/立牌价值带按「每档不重叠、与骰子品质对齐」重排(原卡牌稀有 600~1800/史诗 1500~4500/传奇 2000~6500 与骰子带严重重叠,导致「材料品质↔兑换物品质」脱钩)。

**条目顺序**:按 `ModItems` 注册序分组排列(骰子 → 货币 → 卡牌 → 立牌 → 筹码);新增条目插在同类别既有条目之间,**不重排既有条目**。文件始终 UTF-8 / **CRLF** / Tab 缩进 / 末尾换行,双版本逐字节一致。

**价值平衡规则**(Bountiful 加载时校验,违反会告警 `top value rewards cannot be matched`):
- 校验式:`需求池最高价值条目(1 条,按 amount.max × unitWorth) ≥ 奖励池最高价值条目(2 条)之和 × 0.9`;不满足时 Bountiful 告警"会产生不均等的赏金"(仅告警,不阻断加载);
- 当前 `astral_objs` 顶值条目:黄金星盘 16000(1 个);`astral_rews` 顶值 2 条:黄金星盘 9500 + 钻石骰子 7500 = 17000 → 16000 ≥ 17000 × 0.9 = 15300 ✓;
- 调整奖励/需求价值时保持该式成立,否则赏金两侧价值无法匹配(如奖励侧 11000+ 无法用需求侧条目凑齐)。

## 自定义覆盖方式(整合包侧)

Bountiful 支持「config pack」:在 `config/bountiful/` 下创建同名文件即可增补或覆盖 mod 数据(无需 KubeJS)。

### 增补条目
在 `config/bountiful/bounty_pools/astral_rews.json` 写入新增条目,会自动合并进现有池:

```json
{
    "content": {
        "my_custom_reward": {
            "type": "item",
            "content": "minecraft:diamond",
            "amount": { "min": 1, "max": 2 },
            "unitWorth": 2000,
            "rarity": "EPIC"
        }
    }
}
```

### 整体替换
加 `"replace": true` 则完全覆盖该池(丢弃 mod 数据):

```json
{
    "replace": true,
    "content": { ... }
}
```

### 移除条目
将条目 id 设为 `null`:

```json
{
    "content": {
        "netherite_dice": null
    }
}
```

### 调整概率/声望
修改条目的 `rarity`(COMMON/UNCOMMON/RARE/EPIC/LEGENDARY)或 `weightMult`(额外权重系数)、`repRequired`(赏金板声望硬门槛,仅奖励有效),保存后 `/reload`。

### 排除原版赏金池
编辑 `config/bountiful/bountiful.json` → `general.dataPathsToExclude`,例如排除全部原版池只保留本 mod 联动:

```json
"dataPathsToExclude": [
    "bounty_pools/bountiful/*"
]
```

## 故障排查

- 赏金板刷不出本 mod 物品:检查 `config/bountiful/errors.log`,若有 `Pool 'astral_xxx' has been loaded, but is not attached to any existing data!` 说明 decree 引用缺失(正常安装不会出现);
- 修改数据后未生效:确保在游戏内执行 `/reload`(或重进世界);
- **赏金板机制(重要)**:Bountiful 的赏金板只为「摆在板上的悬赏令(decree)」生成赏金——新放置的赏金板会从**所有 `canSpawn` 悬赏令中随机抽 1 个**(约 1/14,含本 mod 的 astral),且不会随时间重抽。因此单块赏金板刷出本 mod 赏金的概率约为 1/14,属正常机制而非数据缺失;
- **想让本 mod 物品出现在每块赏金板**:用 config pack 把 `astral_objs`/`astral_rews` 合并进各职业 decree(如 `config/bountiful/bounty_decrees/farmer.json` 写 `{"objectives":["farmer_objs","astral_objs"],"rewards":["farmer_rews","astral_rews"],"replace":false}`),或把 `general.dataPathsToExclude` 设为 `["bounty_decrees/bountiful/*"]` 后仅保留自己的 decree(此时全板都刷 astral)。

### 已应用的整合包配置(狐の航空学 Voxy Edition)

为让**每块赏金板都能刷出本 mod 物品**,已在整合包侧创建 12 个 decree 合并文件(`config/bountiful/bounty_decrees/{armorer,butcher,cleric,farmer,fisherman,fletcher,inventor,leatherer,librarian,mapper,shepherd,toolsmith}.json`),每个文件内容:

```json
{
	"objectives": ["astral_objs"],
	"rewards": ["astral_rews"]
}
```

- 不带 `"replace"` → 与 Bountiful jar 内同名 decree **合并**(objectives/rewards 取并集,`Decree.merged` 确认),原版职业池与 Bountiful 内置兼容池(锦致装饰 supplementaries 等)全部保留;
- 效果:无论赏金板抽到哪个 decree,需求/奖励池都包含 `astral_objs`/`astral_rews`,本 mod 物品可出现在每块板上;
- 池内容仍以 mod jar 内 `astral_objs.json`/`astral_rews.json` 为准,后续更新 mod 自动生效,无需改这些 config 文件;
- 已校验:合并后各 decree 的价值平衡检查仍通过(共享池 `_all_objs` 含铁砧 144000 等大额条目,需求侧顶值远高于奖励侧顶值),不会新增 `top value rewards` 告警;
- 生效方式:游戏内 `/reload`(或重进世界)即可,无需新放赏金板。

### 已应用的整合包配置:排除锦致装饰(Supplementaries)物品

整合包 `config/bountiful/bountiful.json` → `general.dataPathsToExclude` 追加了 `"bounty_pools/supplementaries/*"`,Bountiful 加载时会把 `data/bountiful/bounty_pools/supplementaries/` 下全部池排除(模式 `*` 按正则 `([A-Za-z_/]+)` 全串匹配,已用实际池路径验证):

- 效果:锦致装饰(及 Bountiful 为其内置的所有兼容池条目)不再出现在任何赏金板上;各职业池/Bountiful 自身池/本 mod 的 astral 池不受影响;
- **注意:`bountiful.json` 仅在游戏启动时读取一次**(`BountifulIO.configData` 静态缓存),`/reload` 不会重新加载该配置——修改后必须**完全重启游戏**;
- 兜底:**`config/bountiful/bounty_pools/` 下另建 14 个池覆盖文件**,把锦致装饰的全部 25 个条目(`supp_rope`/`supp_bottle`/`supp_sack`/`supp_popper`/`supp_safe`/`supp_wrench`/`supp_key`/`supp_goblet`/`supp_pancake`/`supp_candy`/`supp_basket`/`supp_lumi_bottle`/`supp_statue`/`supp_lumi_bucket`/`supp_cage`/`supp_quiver`/`supp_altimeter`/`supp_bubb`/`supp_pulley`/`supp_faucet`/`supp_bellows`/`wind_vane`/`supp_cannon`/`supp_illuminator`/`supp_clock`/`supp_globe`/`supp_hourglass`/`supp_ant_ink`/`supp_slingshot`)设为 `null`——config 池文件与 `dataPathsToExclude` 相互独立,即使排除机制失效,条目也会在合并时被移除(`Pool.merged` 对已存在条目的 null 值执行移除);
- 旧赏金槽:重启后**已生成的旧赏金会保留到过期/清空**,新刷新(45 秒周期)或新放置的赏金板才会应用新池。
