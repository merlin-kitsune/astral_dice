# 交给 TLMDice 会话的欠账处理 prompt（2026-11-09）

> 复制下面整段即可。上游仓库：`F:/MCProject/astral_dice_multiloader-next`（分支 `multi-dev-next`）；
> 本 prompt 依据上游 `26.4-SNAPSHOT.7`（含 `83e601ce` / `ed46bf8b` / `047cfce4` 之后的 HEAD）。

---

## 0. 背景（只读，不要先改代码）

- 上游 `26.4-SNAPSHOT.7` 起，**forge-1.20.1 与 neoforge-1.21.1 两条线**都已提供类玩家能力面：
  `target/PlayerLikeTargets`、`compat/playerlike/{PlayerLikeWallet,PlayerLikeWallets,PlayerLikeAbilities}`、
  `shop/ChipShopService`、`shop/ChipShopDiscounts`、`item/chip/ChipEquip`、`item/card/CardUseService`、
  `item/sign/CardGivenHooks`；契约见上游 `docs/features/player-like-entities-api.md`（§6 有四线差异表）。
- 因此 TLMDice 现有欠账 **TD-14「上游只有 neo 源码」已过期**，可以开始接线。
- 硬性要求：只改 TLMDice 仓库；不改上游；不 push；每个可独立验证的单元单独本地提交。

## 1. 必做项（按顺序，逐项一个提交）

1. **R1 注册类玩家 handler**（新文件 `combat/MaidPlayerLikeHandler.java`，两线各一份）
   - 调 `PlayerLikeTargets.register(new ResourceLocation("touhou_little_maid_astra_dice","maid"), handler)`
     （1.20.1 用 `new ResourceLocation`，1.21.1 允许 `fromNamespaceAndPath`）。
   - `matches(LivingEntity)`: 是否为 `touhou_little_maid:maid` 且**已装骰子**（与 `MaidDiceParticipants` 的谓词同一判据，禁止两套）。
   - `giveCard(ServerPlayer, LivingEntity, ItemStack)` / `giveItem(...)`: 写入 TLM 官方背包容器；失败返回 false（由上游决定落地/丢弃）。
   - `supportsSigns/ChipsShop` 全返回 true；`wallet(...)` 返回下方 `MaidWallet`。
   - 注册点必须放在 `TLMDice#onCommonSetup` 的 `enqueueWork` 内（与 `MaidDiceParticipants` 同批）。

2. **R2 主人账户代扣 `MaidWallet`**
   - 实现 `PlayerLikeWallet`：`isAvailable()` 看主人是否在线；`balance(maid)` 读主人余额；
     `withdraw(maid, n)` 从主人账户扣。
   - **禁止**给女仆自建账户、禁止「余额不足也放行」的免费兜底。

3. **R3 商店**：`MaidShopIntent` 去掉 `BLOCKED_BY="B-07"`，改为 `ChipShopService.purchase(maid, offerIndex)`；
   `isAvailable()` 改为「handler 已注册 + 主人在线」。失败结果按 `ChipShopService.Result` 逐项映射文案。

4. **R4 立牌主动**：`MaidActiveSkillIntent` 去掉 `BLOCKED_BY`，改为对女仆装备的立牌调
   `BaseSignItem.activateFor(maid)`；返回 false 时按「未装备/冷却中」分类提示。

5. **R5 转移**：`MaidTransferIntent` 改为 `PlayerLikeTargets.giveCard(...)` / `giveItem(...)`。

6. **R6 筹码槽即时补齐（新增，2026-11-09 上游行为变化）**
   - 上游 G6 起：参与者**首次参战**后会由 `DiceCurioItem.refreshChipSlotCountForEntity(maid)` 自动补筹码槽
     （Curios 只给玩家派发 `curioTick`，所以上游是在攻击/防御参与者分支里补）。
   - 建议 TLMDice 在「女仆装/卸骰子」时主动调用一次同一方法，避免玩家必须先打一拳才看见筹码栏。

7. **R7 文档**：更新 `docs/status/*` 与 `docs/design/04-manual-verification-checklist.md`：
   TD-14 改「上游已提供，正在接线」；R1–R6 各自写验收项（可只写静态判据）。

## 2. 环境可复现性（TD-04 / TD-05 / TD-06 的落地要求）

forge dev 是 Mojmap，**生产件（SRG refmap）必崩**；已验证可用的做法是取构建期重映射产物：

- rhino：`~/.gradle/caches/*/transforms/*/transformed/rhino-416294-6186971.jar`（`RhinoModForge` 带 `@Mod`、refmap 是空映射）→ 复制为 `run/<line>/mods/rhino-dev.jar`。
- collective / superflat：从 MDG/FG 的 `transformed/collective-*.jar`、`transformed/superflat-world-no-slimes-*.jar` 取（类体 0 处 `m_`/`f_` 才是去混淆件）。
- 要求：新增 `scripts/test/bootstrap`（或 bench 的 env 阶段）做**存在性 + sha256 + 明确失败信息**，并在 README 登记这些件的 sha256；**不要**再把「手工从缓存拷」当默认流程。

## 3. 验收（纯静态 + 一次启动）

- 两线 `compileJava` 通过；`grep -r "BLOCKED_BY" src` 对 R3/R4/R5 三个 intent 为 0 命中；
- 注册调用都在 `enqueueWork` 内（`grep -n "PlayerLikeTargets.register" -A3` 人工确认）；
- 行尾：Java/JSON 保持 CRLF、`*.md` LF（按上游 `.gitattributes` 口径）；
- `run/<line>/mods` 只放重映射件，启动后 `ModList` 里 rhino/autojs/astral/curios/TLM 全在；
- 行为验收仍按 `docs/design/04-manual-verification-checklist.md`，本批不要求跑完 A4–A11。

## 4. 明确不要做

- 不要改上游 astral 的接口签名；需要新能力先在上游开 issue/commit。
- 不要把 maid 当 `ServerPlayer` 强行转型；一切走 `PlayerLikeTargets` / `ChipShopService`。
- 不要为测试方便给女仆自建钱包账户。
