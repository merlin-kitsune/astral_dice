# 新会话提示词（复制整段发送）

下面这段可直接整份贴进新 Codex 会话。

```text
你接手的是 MC 模组仓库 /mnt/f/MCProject/astral_dice_multiloader，分支 multi-main，版本 1.5.0-pre.3，
功能末次提交 3e200a9b（若 git log -1 显示一条 docs(handover) 提交属正常），工作树应干净（开工先跑 git -C /mnt/f/MCProject/astral_dice_multiloader status --short -uall
与 git -C <repo> rev-parse --short HEAD 核对）。四线 mod_version 均为 1.5.0-pre.3+<加载器后缀>，
前置库 starengine_version=1.1.0-pre.2，jar 内硬前置区间 [1.1.0-pre.2,2.0.0)，META-INF/jarjar/ 计数 0。

## 先读（按顺序，必须实读）
1. docs/handover-1.5.0-pre.3-main.md
2. AGENTS.md（红线区）
3. scripts/test/TESTING-SPEC.md
4. docs/compat-1.5.0-porting.md

## 基本盘
- P0（唯一交付面）= neoforge-1.21.1 + forge-1.20.1。P1 = neoforge-26.1.2 + fabric-1.20.1：
  版本号同为 pre.3，但内容不含 1.5.0，1.5.0 正式版前不验证、不移植（用户裁决）。
- 当前无 1.5.0-pre.3 tag，未推送提交 17 个（交接正本文档提交后实测；提交前为 16）；默认不 push、不打 tag，需用户放行。
- P0 产物（build/libs，2026-10-22 14:25）：
  neo   2,555,453 B  09980b2e0927f99a9086084f7b426d09c6ab038fdc43a5d7e95107b13e849a02
  forge 2,601,019 B  70b347ba2367e13823a66789c22b05172f3a8ca8d3228060049d29f3c9695507
- 投放位置：D:\.minecraft\versions\狐の航空学 Voxy Edition\mods（neo）与
  D:\.minecraft\versions\1.20.1-Forge 模组测试\mods（forge），两处须同时放同名 astral_dice jar 与
  starengine-{neoforge-1.21.1,forge-1.20.1}-1.1.0-pre.2.jar。
- 测试台：scripts/test/mt.ps1，一律 pwsh -NoProfile -File <绝对路径>；WSL 只编译（gradlew :<线>:classes|jar），
  实机只在 Windows 跑。
- 收尾纪律（每轮必做）：
  pwsh -NoProfile -File scripts/test/mt.ps1 --version <线> --phase stop --force
  pwsh -NoProfile -File scripts/test/mt.ps1 --version <线> --phase stop --purge-saves
  成功判据 MT_STOP: OK / MT_CLEANUP_SAVES: PURGED <n> / MT_CLEANUP_RESULT: OK，并核对 Get-Process java 无本流程残留。
- 守门：8 跑 7 绿，唯一红 = verify_probe_class_refs（既有基线，run/1.21.1/mods 未装 JEI/LWJGL）。
- 委派：用 mcp__code_review__delegate_task（明文 brief，一次一件事，timeout ≤1800s）；
  独立复核用 mcp__code_review__code_review。禁止 fork_turns="none"。

## 立即待办（按优先级）
1. 1.20.1 创造模式出牌扣卡复验：期望扣 0 张（改动在 BaseEffectCardItem:337 / :423、CardChoiceMenu:134）。
2. 重投 run/<线>/mods 与两个整合包后再开实机（run 内 jar 与 build/libs 不一致，见交接正本 §6-1）。
3. 处理 verify_probe_class_refs 红点：判明是探针引用缺失（JEI/LWJGL）还是真缺口，给出证据链。
4. 补 11 个立牌子类的 setSignActiveMaxCooldown（BonnieSignItem:88 等），保持冷却口径一致。
5. P1 欠账登记维护：JEI 筹码 / BeamFx / GlobalEventGate / RepulsionEntity / CardShopMenu#planPlacement / EventDrawBlock。

## 硬约束
- dev 不装 reobf AutoJS：forge 1.20.1 dev 必须用 build/devlibs 的 named jar，整合包用 reobf jar，两者不可互换。
- purge 前先 env：--purge-saves 会删 testworld，下轮实机前先 --phase env 重建。
- 委派一次一件事、≤1800s，禁轮询；结果只从 delegate_task 返回值或落盘文件验收。
- P1 不碰：26.1.2 / fabric 的实现、验证、移植一律等用户下达。
- 只发 P0；不 push、不打 tag、不改 AGENTS.md 与 docs/agent/**（除非用户明确要求）。
- 交付前后跑 python3 docs/agent/verify-split.py，四项全 OK 才算过。
```
