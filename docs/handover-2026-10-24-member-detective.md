# 交接：会员推荐信手牌门 + 秘密侦探主动标记/返币（2026-10-24）

## 0. 快照与边界

- 工作树：`F:\MCProject\astral_dice_multiloader`；分支 `multi-main`；接手 HEAD `4f6140c5`。
- 四线 `mod_version` 均为 `1.5.0-pre.5+<加载器后缀>`，`starengine_version=1.1.0-pre.2`；本批未升版本。
- 本批只改 P0：`neoforge-1.21.1` + `forge-1.20.1`；P1（`neoforge-26.1.2` / `fabric-1.20.1`）未改。
- 用户正在本地审阅和修正语言文件；本批未改任何 `lang/**`，也未以语言守门结果作为本批验收依据。
- 未运行实机、部署、上传、commit、push 或打 tag；未重投 jar。

## 1. 已实现

### 1.1 会员推荐信

- 两线 `item/chip/MemberRecommendationChipItem.java`：
  - `HAND_CARD_LIMIT = 5`（两线 `:21`）。
  - `countHandCards` 按主背包 `items` + 副手 `offhand` 遍历，使用 `ModItems.isCardItem` 与 `ItemStack#getCount` 累加（两线 `:47-56`）。
  - `onBlessingStart` 在装备判断之后、发牌之前，以 `countHandCards(player) >= HAND_CARD_LIMIT` 早退（两线 `:59-64`）。即 0/4 张给牌，5 张及以上不给。
- 数量门只在给牌入口；`isEquipped` 与 `shopPriceReduction` 未加入数量判断，降价仍只由装备状态决定（两线 `:28-44`）。

### 1.2 秘密侦探立牌（Bonnie）

- 两线 `item/sign/BonnieSignItem.java` 主动成功路径：
  1. 施加「隐匿调查」（`:78-79`）；
  2. `int markLevel = MarkManager.apply(target)` 追加 1 层标记并取得施加后的当前层数（`:81`）；
  3. 以 `markLevel * 2` 返还星币（`:82-84`）。
- 已带隐匿调查的早退、冷却、电流核心、ActionBar 与风扇调用位置未改；P1 未移植。

## 2. 新增回归守门

- `tools/verify_member_recommendation_hand_gate.py`：P0 两线 14 项，求值证明 0/4 发、5/6/36 不发，并检查计数口径、给牌门位置与降价独立性。
- `tools/verify_bonnie_active_order.py`：P0 两线 12 项，检查隐匿调查 → `MarkManager.apply` → 施加后层数返币的顺序，禁止裸写 `MARKED` 或以 `markLevel > 0` 包裹返币。
- TDD 取证：旧代码上两手门均因功能缺失 exit 1；实现后分别 `GATE: PASS (14 项)` / `GATE: PASS (12 项)`。
- 独立审查发现手牌守门失败消息少一个 `%s`，导致比较符变异时 `TypeError`；已修 `tools/verify_member_recommendation_hand_gate.py:337`，复查 `>`/`<` 变异均为干净 FAIL、后续检查继续执行，无新 Critical/Important。

## 3. 验证记录

- `python tools/verify_member_recommendation_hand_gate.py -v` → PASS 14 项，exit 0。
- `python tools/verify_bonnie_active_order.py -v` → PASS 12 项，exit 0。
- `python tools/verify_selector_key_ownership.py` → `GATE: PASS`。
- `pwsh -NoProfile -File scripts/verify/verify_sign_registration.ps1` → `SIGN_REG: ALL OK`（四线注册检查；不代表 P1 功能已移植）。
- `./gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes --console=plain` → `BUILD SUCCESSFUL in 13s`，8 tasks（3 executed / 5 up-to-date）。只有既有 deprecation/mixin 警告，无编译错误。
- `git diff --check`（本批 Java 与守门路径）→ exit 0。
- 全新独立子代理功能审查最终通过；审查覆盖 R1–R5、旧代码 RED、新代码 GREEN、比较符与顺序变异。

## 4. 未验证 / 后续事项

- 未实机：仍需用户明确下达后验证 4→5 张边界、5 张不再发牌、降价不受限，以及隐匿调查与标记实际生效后返币数额。
- 静态守门证明源码判据与调用顺序，不证明其他模组拦截 `addEffect`、目标选择器运行期调用频率或钱包入账表现。
- 语言文案与其它 P1 行为不在本批；用户完成语言审阅后另行同步/验证。
- 本批只跑 `classes`，未生成或投放新 jar。

## 5. 工作树既有事项（不属本批）

- 本批开始前已有：`AGENTS.md`、`docs/agent/AGENT-SPLIT.md` 修改；`docs/agent/AGENTS-dsh.md`、`docs/agent/AGENTS-kimi.md` 新文件；`models/astral_anvil.bbmodel` 未跟踪素材；用户正在审阅的 `neoforge-1.21.1/.../lang/zh_cn.json` 修改。以上均未由本批改动。
- 之前的 AGENTS 整理仍处于未完成状态：`PROJECT-REFERENCE.md`、新版 `docs/agent/README.md`、校验脚本迁移和 `handover-2026-10-24-kimi-takeover.md` 尚未完成；不要把当前状态当成该整理批次的最终交付。
- 本批新增交接即本文；未改名、覆盖或原地重写旧交接。
