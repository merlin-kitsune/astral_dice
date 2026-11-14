# 新会话交接 Prompt — Astral Dice multi-dev-next（2026-11-12）

> 用法：新会话第一轮，把下面分隔线之间的内容整段粘贴进去。
> 本文是 2026-11-12 的交接正本，取代 `docs/handover-2026-11-11-devnext-session.md` 中 `.11 / e1d1e922 / P1 三线` 的旧口径；旧文只作历史参考。

---

你接手 `F:\MCProject\astral_dice_multiloader-next` 的 Astral Dice 多加载器工程。先加载技能 `using-superpowers`，再读根 `AGENTS.md` 与 `docs/agent/AGENTS-dsh.md`（当前是 Windows 宿主；`AGENTS-dsh-linux.md` 只是 Linux 历史正本），然后按本文核对现状。不要凭本文数字直接下结论，分支 / HEAD / 版本 / 产物一律先实读。

## 一、当前事实（2026-11-12 01:42 交接快照）

- 工作树：`F:\MCProject\astral_dice_multiloader-next`，分支 `multi-dev-next`。
- HEAD：`43f821adf0779320e2efe752aefa76cfd5711eb7`；`origin/multi-dev-next` 与本地同哈希，代码已 push。
- Tracked 工作树干净。未跟踪仅：6 个 `models/*.bbmodel`、`work/audit/`、`work/souri/`、根目录 `立牌计划表.txt` —— 一律不动，不 add / 不删。
- 五线版本均为 `26.4-SNAPSHOT.13+<加载器后缀>`：
  - P0：`neoforge-1.21.1`、`forge-1.20.1`、`neoforge-26.1.2`
  - P1：`fabric-1.20.1`、`fabric-1.21.1`
- `.13` 是 2026-11-12 01:10 从 `a248f13e` 构建的 **仅审计构建**：五线 `:build` PASS，根 `build/libs` 五枚 jar 与 `.sha256` 已更新；**未实机、不可作为发布版本**。未打 tag、未发 Release、未上传 CurseForge。
- 当前没有实机在跑。接手后先复核端口 `25585`（AutoJS）/ `25565`（游戏）/ `25575`（fabric RCON）是否为空，并检查 `F:/MCProject/.agent-locks/` 是否有他人的活跃锁。

## 二、本轮已完成、不要重做

- P0/P1 已按 2026-11-11 用户裁决重新划级：P0 三线 = forge 1.20.1 + neoforge 1.21.1 + neoforge 26.1.2；P1 两线 = fabric 1.20.1 + fabric 1.21.1。理由：forge/neoforge 实现更接近，fabric 差异较大。规则、feature specs、CF 上传白名单、相关 Java 注释均已同步。
- 26.1.2 已补：`compat/playerlike/PlayerLikeWallet.java`、`PlayerLikeWallets.java`、`shop/ChipShopService.java`；`ChipShopState` / `ChipShopRoller` / `ChipShopOpener` 已从 `Player` 泛化到 `LivingEntity`，非玩家走 `PlayerLikeTargets.giveItem`，失败按钱包 `deposit` 退款；玩家菜单路径不变。**只做过编译/构建，未实机。**
- 26.1.2 立项修复：Bonnie 立牌补回「目标已带 `UNDERCOVER_INVESTIGATION` ⇒ 记录 `AP_UNDERCOVER_REJECT` 并 return」；会员推荐信 `countHandCards` 改为主背包一次 + `Inventory.SLOT_OFFHAND` 一次。**只做过编译/构建，未实机。**
- 赏金池：neo21 / forge 的 `astral_rews.json` 已补 `al_sign` / `inkshadow_sign` / `souri_sign` / `sykes_sign`；26.1.2 无 Bountiful，跳过。
- 门禁修正已落盘：chip-shop 兼容 `LivingEntity`、bountiful 去掉 26.1.2、forge loader 对齐 `47.1.47` / `[47.1.47,48)`、content-library 去掉 `moses_ready` 假阳性。
- `.12` 已实机 PASS 的部分：fabric 两线与 26.1.2 的小猎手传送门同维度 / 跨维度放置与穿越 / 6.5s 不弹回 / 走远再回门解锁；1.21.1 与 1.20.1 的 anti-farm；fabric 1.20.1 / 1.21.1 跨维度放置与穿越。
- 26.1.2 已有 `TeruSignItem.onAttackCardsEquipped` 接线（`CardInventoryMenu.saveToDice`），编译通过。

## 三、剩余工作（按优先级）

### A. P0 26.1.2 实机验收（最高优先，发布阻塞）

1. 类玩家商店：
   - 先确认 26.1.2 是否有可用的 `PlayerLikeTargets` handler 注册；源码里没有现成注册，外部附属需自行 `register`。没有 handler 时，**不要伪造非玩家 PASS**，先做玩家路径回归，并把非玩家路径记为“需 handler 注册”的阻塞项。
   - 玩家路径：`ChipShopOpener` / `ChipShopMenu` 打开、报价、购买、余额扣除、刷新、折扣，行为需与 1.21.1 基准一致。
   - 非玩家路径（有 handler 才做）：`ChipShopService.quote` / `purchase` / `prepareOffers`，物品经 `PlayerLikeTargets.giveItem` 交付；背包满时 `spawnAtLocation` 或按实现退款；日志锚点 `AP_CHIP_SHOP_SERVICE`。
2. Bonnie 立牌：对已带 `UNDERCOVER_INVESTIGATION` 的目标再次主动施放，必须出现 `AP_UNDERCOVER_REJECT: already_marked` 且不二次扣费 / 不二次施加。
3. 会员推荐信：主背包 4 张 + 副手 1 张 = 5 张时必须拒绝发牌；主背包 4 张 + 副手 0 张才可发。分别构造两种背包状态取证。
4. 如本轮改动了产品代码并重新编译：先把五线 `mod_version` bump 到 `.14`，并在 `CHANGELOG.md` / `CHANGELOG_ZH.md` 的 `.14` 段记录，再启动实机；不得在旧 `.13` 上跑新回归。

### B. P1 fabric 两线实体级验收

- 代码现状：两线 `BaseSignItem` 已有 `onKillEntity` / `onHurtEntity` / `invokeKillHooks(LivingEntity)` / `invokeHurtHooks(LivingEntity)` / `activateFor(LivingEntity)` / `activateForEntity`；35 个立牌已覆写 `activateForEntity`。
- 但两线**没有**立牌特有的非玩家 `onKill` / `onHurt` 覆写；实体级 kill/hurt 目前只能证明基类管线，不要声称某个具体立牌的非玩家击杀效果已生效。
- 非玩家测试需要临时数据包给僵尸 `stand` 饰品槽：
  - fabric：`data/trinkets/entities/zztest.json`，entities `[minecraft:zombie]`，slots `[astral_dice/stand]`；
  - 26.1.2（若也测）：`data/zztest/curios/entities/zombie.json`，entities `[minecraft:zombie]`，slots `[stand]`。
- Misaki：`activateForEntity` 先施 2400t 气爆，再找 `MISAKI_SIGN`，层数 >=3 扣 2 层并经 `PlayerLikeTargets.giveItem` 给名刀。Rhino 的 JavaAdapter 对 default 方法有限制；若无法直接构造 handler，记录为测试工具阻塞，不要改产品语义去迁就测试。

### C. 后续功能债（P0/P1 实机闭环后再动）

- fabric 两线的附属 / 玩家类目标 API 仍不完整；用户已明确“当前优先添加新功能”，不要先做 API 化大改。
- `docs/features/sign-roadmap.md` 仍剩 Z3000（垃圾桶）与 Luka（摩托车手）未实现。
- `scripts/verify/verify_content_library.py` 仍有 9 条 1.2.0 冻结快照漂移；脚本自述为一次性验收项，不是常规发布门禁。是否处理先问用户。
- 8 条“代码与文案不符”仍挂账；未获裁决前不动。

## 四、硬约束

- 不自行开实机：实机必须用户明确下达；同一时刻只允许一条实机线，发现别的实机在跑立即停手并告知用户。
- 不 commit / 不 push / 不打 tag / 不上传 CurseForge，除非用户明确要求。远程 git 走本地代理 `http://127.0.0.1:7897`。
- 不碰 `models/`、`images/`、`sound/` 与他人未跟踪文件；不删任何非自己创建的文件。
- 没有详细测试与实机取证，不得把任何版本称为“可发布”。`.13` 现在只是审计构建。
- 单写者：一个文件同一时刻只允许一个写者；并行任务必须切分不重叠的写作用域。
- 任何“完成 / PASS / 已修”的结论都要附可复核命令、日志行或文件行号；没有证据就写“未验证”。

## 五、接手后第一条回复

请先用一段话回报：
1. 实读到的 HEAD、分支、五线 `mod_version`、工作树脏度；
2. 端口与锁检查结果；
3. 你建议先做 A / B / C 中的哪一项，以及是否需要用户现在授权开实机。

在用户确认前，只做只读核对、构建和文档，不启动游戏。

---
