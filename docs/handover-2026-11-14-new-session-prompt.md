# 新会话交接 Prompt（Astral Dice / multi-dev-next / 26.4-SNAPSHOT.32）

你是资深 Minecraft Java 多平台模组开发工程师，接手 Astral Dice 的 dev 分支。先读这份 prompt，再读仓库里的交接正本 docs/handover-2026-11-14-z3000-devnext.md；旧交接文档 docs/handover-2026-11-12-new-session-prompt.md 与 docs/handover-new-session-context.md 只作历史参考，不要按它们的 .13 / 2026-11-11 口径下结论。

## 一、当前事实（2026-11-14 01:30 快照，接手后必须实读复核）

- 工作树：F:/MCProject/astral_dice_multiloader-next
- 分支：multi-dev-next
- HEAD：43f821adf0779320e2efe752aefa76cfd5711eb7
- origin/multi-dev-next：同哈希（未 push 新提交）
- 五线版本：26.4-SNAPSHOT.32+neoforge_1.21.1 / +forge_1.20.1 / +neoforge_26.1.2 / +fabric_1.20.1 / +fabric_1.21.1
- tracked 工作树：165 个修改，diff +2024/-1197
- 未跟踪：63 个条目，其中 39 个是本轮产品文件（Java / json / png），包括三线 GrapplingHookEntity、GrapplingHookRenderer、CounterStateManager、Z3000SignItem、1.21.1 GrapplingHookCommand、Z3000 / Vacuum 资源与生成配方
- P0 .32 产物：
  - 1.21.1 astral_dice-26.4-SNAPSHOT.32+neoforge_1.21.1.jar，SHA-256 c4ff8df0e6348d039e86d83670f3548f59d6a18ea7c11b467fcb111d0c3130ca
  - 1.20.1 astral_dice-26.4-SNAPSHOT.32+forge_1.20.1.jar，SHA-256 9aa7b89dc525ce2b33c58ce411344afcfec469ba04f3271b46ec669e34818cfc
  - 26.1.2 astral_dice-26.4-SNAPSHOT.32+neoforge_26.1.2.jar，SHA-256 b1646981ca216380104ef0bbc048dcba8996708f3b3fbe49ecc1439380fee28a
- Fabric 两线没有 .32 产物（build/libs 最后 .30）；P0 .32 没有 .sha256；根 build/libs 混有 .24 / .30 / .32，不是发布集。
- 运行态：25565 / 25575 / 25585 无 LISTENING；F:/MCProject/.agent-locks/ 不存在；只剩 3 个 Gradle daemon java（22368 / 22392 / 31780）。没有实机在跑。

## 二、先读什么

1. 根 AGENTS.md 与 docs/agent/AGENTS-dsh.md（DSH 规则正本）
2. docs/handover-2026-11-14-z3000-devnext.md（本轮完整交接：版本表、欠账、证据、接手顺序）
3. docs/reports/2026-11-14-z3000-acceptance.md（最新实机报告）
4. 需要时再读：docs/reports/2026-11-12-counter-p0-acceptance.md、2026-11-13-grappling-hook-live.md、2026-11-12-26.1.2-acceptance.md、2026-11-12-fabric-entity-acceptance.md

## 三、接手后第一条回复（只做只读核对，不启动游戏、不改代码）

先实际执行命令，不要照抄本 prompt 的数字：

~~~bash
git rev-parse --abbrev-ref HEAD
git rev-parse HEAD
git status --short
grep -H '^mod_version' */gradle.properties
netstat -ano | grep -E ':(25565|25575|25585) .*LISTENING'
ls -la F:/MCProject/.agent-locks 2>&1
~~~

然后用一段话回报：

1. 实读到的 HEAD、分支、五线 mod_version、tracked / 未跟踪脏度；
2. 端口与锁检查结果；
3. 你建议先做哪一步（见第五节），以及是否需要用户现在授权：
   - 选择性本地 checkpoint 提交；
   - 开某一条实机线；
   - 只做文档 / 门禁。
4. 明确说出“已读 docs/handover-2026-11-14-z3000-devnext.md”。

在用户确认前，只做只读核对、文档、门禁和非产品脚本；不启动游戏，不改产品代码，不 commit / push / tag / CurseForge。

## 四、本轮已完成，不要重做

### 4.1 版本主线

- .14 修 ChipShopService.grantRefresh 封顶判断（P0 三线值比较）；只构建未实机。
- .15 随机发牌收口到 RandomCardHandler（8 张 BATTLE / 3 张 HEALING / 固定入口）；五线构建未实机。
- .16 蛟龙→金鲤联动、金鲤主动重写、大侦探 / 秘密侦探配方、tooltip / 手册清理、仙酿图标；仅 1.21.1 实机，无独立报告。
- .17 肾上腺素筹码 20% / 40% 闪避，仅怪物近战；五线构建未实机。
- .18 反击改 10 秒近战状态，金鲤 4 层修复，安全气囊纳入；P0 三线实机，女仆仅 1.21.1 PASS。
- .19–.23 吸尘管道原型，1.21.1 实机后废弃；.24 删除 VacuumPipeManager / VacuumCommand。
- .24–.30 抓钩拖怪：.29 实机 9.5→1.5 格，用户目视确认；.30 删除 Confluence 派生声音 / 模型 / 许可并重写，五线构建未实机。
- .31 Z3000 P0 三线落地；1.21.1 / 1.20.1 实机。
- .32 修 26.1.2 GrapplingHookRenderer 构造期 NPE（ItemStack 惰性创建）；26.1.2 全套实机 PASS；1.21.1 / 1.20.1 未用 .32 重跑。

### 4.2 Z3000 口径（以代码为准）

- 主动：16 格内敌对目标，拖到玩家面前 1.5 格，5 点技能伤害；这次伤害直接击杀则 +1 层反击。
- 被动：每击杀敌对目标，主动剩余冷却 -33%，每 1200 tick 最多一次；每击杀 2 个最大生命 >=20 的敌对目标，攻击 +1，上限 +12。
- 与金鲤共享 COUNTER_MAX=4；RenShieldManager 的清理不会抹掉 Z3000 层。
- 配方 HRH / ISI / TDT；三线注册、资源、三语 tooltip、手册、recipe / advancement 已落地。
- Fabric 两线没有 Z3000，只有版本号。

## 五、剩余工作优先级

### 第 0 步：只读核对（本次接手第一条回复）

见第三节。

### 第 1 步：checkpoint（需用户明确授权）

当前最大的风险不是功能，而是 165 个 tracked 修改 + 39 个未跟踪产品文件没有任何 commit。建议按版本 / 主题拆本地提交：

- 先提交一个“当前可构建状态”的 checkpoint（含三线新 Java、资源、生成配方、gradle.properties、双 CHANGELOG）；
- 再按版本拆：.14 / .15 / .16 / .17 / .18 / .19–.23 / .24–.30 / .31–.32；
- 文档与报告单独一个提交。

绝对不要 git add -A。排除：

- models/（他人 bbmodel）
- work/audit/、work/souri/（他人审计 / 用户文件）
- 根目录 立牌计划表.txt
- work/confluence/（84 MB 第三方取证克隆）
- run/（日志、截图、世界）
- 任何不是本轮创建的文件

提交信息必须写清版本、改动面、验证状态和未实机项。默认仍不 push。

### 第 2 步：文档 / 门禁非产品收口

1. 命名门禁 FAIL 19 条（python tools/verify_sign_display_names.py，exit 1）：
   - 三线 item.astral_dice.z3000_sign 的 en / ja 名未登记到 tools/sign_display_names.json；
   - 五线 tooltip.astral_dice.sign.mamushi_note 的 en / ja 片段校验失败；
   - 三线 lang 多出 z3000 键。
   先按 astral-dice-sign-i18n-naming 从 wiki 取权威名，再更新权威表与五线 lang。
2. 更新 docs/features/sign-roadmap.md：Z3000 已实现；Luka 仍未实现；§2.2 的“>=30 血 / +10”与代码“>=20 血 / +12”冲突，先问用户以哪个为准，不要擅自改代码或文档。
3. 补 docs/features/z3000-sign-spec.md；更新 AGENTS 的立牌 / 效果清单；把 z3000 加入 tools/sign_display_names.json。
4. 修双 CHANGELOG .16 的“未实机”表述，或明确写“1.21.1 实机、其余未跑”。
5. 运行 python tools/verify_effect_icons.py（当前 PASS，四线 293 个效果）与 python scripts/verify/verify_crafting_recipe_uniqueness.py（当前 PASS，三线 collisions=0），确认没有回归。
6. python scripts/verify/verify_content_library.py 当前 exit 1（1.2.0 冻结快照漂移，git-only 35 项、四组标签计数不一致）；脚本自述非常规门禁，是否处理先问用户。

### 第 3 步：补齐 .32 产物与证据（需用户授权实机）

1. 构建 Fabric 两线 .32，生成五线 .sha256。
2. 1.21.1 / 1.20.1 用 .32 重跑 Z3000 核心用例：
   - 9 格拖拽：HP1000→995，目标到玩家面前 1.5 格；
   - 17 格拒绝：AP_Z3000_REJECT reason=too_far；
   - 16.00 格可施放；
   - 拖拽击杀 +1 反击；
   - 冷却 -33% 且每分钟只一次；
   - 攻击叠层到 12、attack 到 13 后不再涨；
   - 最大生命 <20 的击杀不计层。
3. 若需要改产品代码，先五线 bump 到 26.4-SNAPSHOT.33 并写双 CHANGELOG，再构建；不得在 .32 上跑新回归。

### 第 4 步：P1

1. Fabric 两线的 10 秒反击：当前仍是旧语义；按 .18 P0 方案移植 CounterStateManager，并补金鲤 4 层、安全气囊、护盾不摘状态；实机。
2. Fabric 两线的 Z3000：代码、资源、三语、配方、实机全部没有；落地后单独验收。
3. .14 / .15 / .17 的实机（若用户认为必要）。
4. .16 其余四线、.18 女仆 1.20.1 / 26.1.2（需要 TLM 测试环境）。

### 第 5 步：功能债 / 历史债

- Luka（摩托车手）立牌。
- Fabric 附属 / 玩家类目标 API 不完整；用户已明确“当前优先添加新功能”，不要先做 API 大改。
- 8 条“代码与文案不符”仍挂账；未获裁决前不动。
- 抓钩头偏扁（源模型 Y 只有 1.8 px）；是否加厚模型需用户裁决。
- /astralhook 只在 1.21.1 存在；保留 / 移植 / 删除需裁决。

## 六、硬约束

- 不自行开实机：必须用户明确下达；同一时刻只允许一条实机线；发现别的实机在跑立即停手并告知用户。
- 不 commit / 不 push / 不打 tag / 不上传 CurseForge，除非用户明确要求。远程 git 走本地代理 http://127.0.0.1:7897。
- 不碰 models/、images/、sound/ 与他人未跟踪文件；不删任何非自己创建的文件。
- 单写者：一个文件同一时刻只允许一个写者；并行任务切不重叠的写作用域。
- 任何“完成 / PASS / 已修”的结论都要附可复核命令、日志行或文件行号；没有证据就写“未验证”。
- 改产品代码并重新编译后，先把五线 mod_version bump 到下一个 26.4-SNAPSHOT.N，并在双 CHANGELOG 记录，再启动实机；不得在旧版本上跑新回归。
- 没有详细测试与实机取证，不得称为“可发布”。当前 .32 只是开发构建。
- DSH 压缩走 billion-context 代理，不调用原生 compress；详见 docs/agent/AGENTS-dsh.md。

## 七、已知坑（接手前先看，避免重复踩）

1. 旧报告里的 latest.log:NNNN 行号已被后续实机覆盖（1.21.1 / 1.20.1 / 26.1.2 的 latest.log 现在都是 Z3000 运行日志）。报告本身是历史留档；要复核请按报告里的时间 / 特征串重新找，不要直接按行号断言。
2. 测试世界：
   - 1.21.1 用 新的世界（FlatLevelSource）；旧的 run/neoforge-1.21.1/testworld 是 NoiseBasedChunkGenerator，不是超平坦。
   - 1.20.1 用 testworld（FlatLevelSource）。
   - 26.1.2 用 flatworld（从服务端 testworld 复制，FlatLevelSource；服务端显示名仍可能是 testworld）。
   - 世界内可能残留测试实体；重启后先清 item_display / armor_stand / 命名目标。
3. 26.1.2 命令源：server.createCommandSourceStack().withSuppressedOutput()；玩家 createCommandSourceStack().withPermission(4) 在 26 会报找不到方法。
4. 26.1.2 渲染器不能在构造器里 new ItemStack；组件未绑定会抛 Components not bound yet。当前 GrapplingHookRenderer 已改惰性创建；别再改回构造器初始化。
5. 26.1.2 的 `:runClientData` 曾因 `Missing item model definitions for: [astral_dice:vacuum_piece]` 失败（2026-11-14 00:48）。当前已在 ModItemModelProvider.java:43 补 `declareCustomModelItem(ModItems.VACUUM_PIECE.get())`；.32 未重跑 datagen，发布前补一次 `:neoforge-26.1.2:runClientData` 证据，不要只跑 build。
6. Fabric 非玩家 kill / hurt 目前是 no-op：BaseSignItem 只在 Player 时委派，全仓没有立牌覆写；不要声称某个立牌的非玩家击杀效果已生效。
7. Fabric 的 PlayerLikeTargets handler 未注册；非玩家商店 / Misaki 名刀交付都缺这条链，不能伪造 PASS。
8. Z3000 与金鲤共享 COUNTER_MAX=4；反击层满 4 后继续击杀不会涨，这是上限，不是未生效。
9. RenShieldManager 的 cleanupStale / voidShield 已为 Z3000 加保留条件；改护盾逻辑时不要把它删掉。
10. 抓钩渲染已在 .29 改为相对实体原点；EntityRenderDispatcher 已平移到实体位置，渲染器不能再叠加世界坐标。
11. 抓钩音效 / 模型 / 许可已在 .30 删除；不要从 work/confluence 重新拷回派生资源。

## 八、第一条回复的格式建议

一段话即可，包含：

- 实读 HEAD / 分支 / 五线版本 / 脏度；
- 端口与锁结果；
- 你建议先做 checkpoint 还是先做文档 / 门禁，以及是否需要用户授权实机；
- 一句“未验证项不会写成 PASS”。

在用户确认前，不要启动游戏，不要改产品代码，不要提交。
