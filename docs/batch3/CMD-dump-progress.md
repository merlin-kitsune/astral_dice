# 批 3 / 追加子命令 `/astralparty dump`(只读转储)— 进度

分支 `multi-1.20.1-1.21.1`,HEAD `7b9e711`。上一位执行者的 `/astralparty`(3 子命令 + 2 别名)为**未提交工作树改动**,
本次在其上**追加**第 4 个子命令 `dump`,**未回退/重写任何既有内容**。

## 0. 构建/提交闸门(首要)

启动时检查 `Get-CimInstance Win32_Process -Filter "Name='java.exe'"`:

- PID 15020:`gradlew :neoforge-1.21.1:runClient -Pquickplay=testworld`(wrapper)
- PID 30468:Gradle daemon 9.2.1
- **PID 11896:`net.caffeinemc.sodium / net.minecraft.client.main.Main /` ← Minecraft 客户端在跑**

结论:**已停在构建前** —— 只完成源码/资源/文档改动,**未执行 `gradlew build`**(`pushToGame` 会把新 jar 推进
`run/*/mods`,污染正在进行的实机测试),**未提交**。`mt_build.ps1` 未运行。

## 1. 改动清单(两版本同步)

| 文件 | 改动 |
|---|---|
| `neoforge-1.21.1/.../item/card/EffectCardPeriod.java` | 新增只读入口 `effectPendingSources()` / `effectPendingSourceIds()` + 私有 `pendingSourceId()`(效果驱动来源取 `Holder#unwrapKey()` 的 id 路径) |
| `forge-1.20.1/.../item/card/EffectCardPeriod.java` | 同上(forge 取 `BuiltInRegistries.MOB_EFFECT.getKey(effect).getPath()`) |
| `neoforge-1.21.1/.../command/AstralPartyCommand.java` | 新增 `dump` 子命令:注册、常量、`dumpState`/`buildDumpLines`/`row`/`derivedRow`/`appendLockRaw`/`appendLockDerived`/`appendSign`/`appendEffects`/`appendPending`/`effectId`;`LOGGER`;javadoc 同步 |
| `forge-1.20.1/.../command/AstralPartyCommand.java` | 同上(差异见 §4) |
| 4 × `lang/{zh_cn,en_us}.json` | +1 key `command.astral_dice.astralparty.dump.summary`(人类可读摘要) |
| `CHANGELOG_ZH.md` / `CHANGELOG.md` | 合并进未发布(1.2.1)既有 `/astralparty` 条目(不改条目数) |
| `AGENTS.md` | 只在 `## 管理员调试命令 /astralparty` 小节内追加 `dump` 表格行 + 详细约定,并同步该小节内 3 处措辞(能力边界、只读入口、API 差异) |

## 2. 只读性(硬约束逐条)

- 全部取值只经 getter 与 `is*` 判定(`ModAttachments.getX`、`EffectCardPeriod.isBurstFull/isCooldownActive/
  isEffectPending/isBlocked/getMaxAllowed/getRemainingBlockTicks/getBonusPlays`、`BaseSignItem.isSignActiveLocked`、
  `ModEffects.ALL` 遍历 + `player.getEffect`、`EffectPendingSource#isActive`)。
- **零写入**:不调用任何 `set*`/`remove`/`addEffect`/`hurt`/`give`。已逐行核对新增代码,无写调用。
- 唯一的"非本模组取值" = `player.level().getGameTime()`(时间基准)。理由:`effect_card_cooldown_end` /
  `sign_active_lock_end` 等是**绝对 tick**,没有基准则 LOCKRAW 组不可断言 ⇒ 放在 `LOCKRAW` 组内并标注用途。
  它不泄露任何玩家/世界状态(不输出血量、经验、背包、原版或其它模组效果)。
- 越界防护:效果清单**只**遍历 `ModEffects.ALL`(本模组注册集合),非本模组效果一行都不输出。
- 权限:`requires(src -> src.hasPermission(2))`,根字面量与 `dump` 节点各一次;服务端 `@EventBusSubscriber` 注册;
  无任何 dev/测试开关或后门。非玩家 + 无选择器 ⇒ `sendFailure(KEY_NO_PLAYER)`,不抛异常。

## 3. `APDUMP` 输出格式与最终键名清单

每玩家一段,段首 `APDUMP|HEAD|<玩家名>=<UUID>`;其后 `APDUMP|<组>|<键>=<值>`;行内**无颜色码**;
`APDUMP` 正文**不走 lang key**;同时 `source.sendSuccess(Component.literal(line), false)` + `LOGGER.info("{}", line)`。

| 组 | 行形状 | 键名(固定序) |
|---|---|---|
| `HEAD` | `APDUMP\|HEAD\|<name>=<uuid>` | —— |
| `LOCKRAW` | `APDUMP\|LOCKRAW\|<k>=<v>` | `game_time`, `effect_card_cooldown_end`, `effect_card_play_count`, `effect_card_bonus_plays`, `living_page_cycle_bonus`, `max_allowed`, `remaining_block_ticks` |
| `LOCKDERIVED` | `APDUMP\|LOCKDERIVED\|<k>=<v>\|assert=forbidden` | `is_burst_full`, `is_cooldown_active`, `is_effect_pending`, `is_blocked` |
| `SIGN` | `APDUMP\|SIGN\|<k>=<v>` | `sign_active_lock_sign`, `sign_active_lock_end`, `sign_active_reduction_pool`, `sign_active_lock_grace_end`, `sign_active_lock_played`, `sign_active_cooldown_end`, `sign_active_max_cooldown`, `sign_ready_type`, `sign_ready_expire`, `is_sign_active_locked`(**带 `\|assert=forbidden`**) |
| `EFFECTS` | `APDUMP\|EFFECTS\|effect=<regid>\|amplifier=<n>\|duration=<n>` | 仅玩家实际携带者;按注册 id **序数升序**排序 |
| `PENDING` | `APDUMP\|PENDING\|source=<id>\|effect=<regid\|none>\|is_active=<bool>\|remaining=<n>` | 注册顺序(9 条,顺序稳定) |

- `LOCKDERIVED` 4 行与 `is_sign_active_locked` 行尾固定 `|assert=forbidden` —— **判定入口字段,禁止作为断言落点**。
- **行序稳定**:逐组写死;EFFECTS 显式排序(不依赖 `DeferredRegister#getEntries` 的集合迭代序);PENDING 用注册顺序。
- 人类可读摘要另发一行,`Component.translatable(KEY_DUMP_SUMMARY, count)`,**不带 `APDUMP|` 前缀**。

## 4. 两版本差异(与已记录的口径一致)

1. `ModEffects.ALL` 元素类型:`DeferredHolder<MobEffect, ? extends MobEffect>`(即 `Holder`)vs `RegistryObject<MobEffect>`(需 `.get()`,id 走 `.getId()`)。
2. 效果待定来源 `effect()` 返回类型:`Holder<MobEffect>`(id 走 `unwrapKey()`)vs `MobEffect`(id 走 `BuiltInRegistries.MOB_EFFECT.getKey`)。**已用本机 moddev 反编译源码核对**:`RegistryObject` 确有 `getId()`;1.20.1 `Registry#getKey(T)` 返回 `ResourceLocation`;`BuiltInRegistries.MOB_EFFECT` 存在;1.21.1 `Holder#unwrapKey()` 返回 `Optional<ResourceKey<T>>`、`DeferredHolder<R, T extends R> implements Holder<R>`。
3. `player.getEffect/getEffect(Holder)` vs `getEffect(MobEffect)`。
4. `LOGGER` 在 `AstralDiceMod` 为 private ⇒ 两侧命令类各自 `LoggerFactory.getLogger(AstralPartyCommand.class)`。
命令树、权限、参数形态、文案与 **APDUMP 组名/键名/行序**两版本逐字一致。

## 5. 关于「E 组非效果状态来源」的核实结论

`EFFECT_PENDING_SOURCES` 的 9 条来源**全部**经 `registerEffectPendingSource(Holder/MobEffect)` 注册,
`isActive` = `player.hasEffect(effect)`(纯 getter 判定),**不存在依赖非效果状态的来源** ⇒ 按任务要求"若存在则一并输出"
的条件不成立,无需额外原始值。已在两侧 `appendPending` 的 javadoc 与 AGENTS.md 写明将来新增这类来源时必须补输出。
`effectPendingSources()`/`effectPendingSourceIds()` 为**新增**只读入口(既有 `EFFECT_PENDING_SOURCES` 的可见性/类型/既有循环**未改动**,
零行为变化;`effectPendingEffects()` 未改)。

## 6. lang / CHANGELOG 校验结果

- 4 份 lang:`keys = 622`(原 621 + 1),`CRLF = 0`(纯 LF),末字节 `}`(**文件末尾无换行**),无 BOM,一行一键,**未使用 `json.dump`**(逐行 edit)。
- `tools/check_lang_sync.ps1`:`neoforge-1.21.1` → `OK: zh_cn.json(622 keys) 与 en_us.json(622 keys) key 完全一致。` exit 0;
  `forge-1.20.1` → 同上 exit 0(无任何 `[WARN]`)。
- CHANGELOG:未发布(1.2.1)`- ` 条目数 **48 / 48**(改动前后一致,`dump` 按「合并约定」并入既有 `/astralparty` 条目,未新增条目、未内联双语)。

## 7. 未完成 / 待办(交给持有构建权的一方)

1. **未构建、未提交** —— 原因见 §0(客户端在跑)。解除后需:先 `--version 1.21.1` 再 `--version 1.20.1`,
   以日志出现 `BUILD SUCCESSFUL` **且**产物 jar 时间戳更新为准(上一批 `/astralparty` 也尚未编译过,本次构建同时验证两批)。
2. **静态语法自检**:见 §8(仅 javac 解析级,无法替代真编译)。
3. 提交时只能 `git add` 本节列出的产品文件;`scripts/test/**` 的改动属另一位执行者,**不得**纳入。
4. 未跑任何用例、未启动游戏。

## 8. 静态编译自检结果(**真 javac,非仅语法**)

不跑 Gradle(避免 pushToGame 与守护进程锁),改用**直接 `javac`**(输出全部写进 `%TEMP%`,对仓库零写入),
类路径 = 各子项目 `build/moddev/clientLegacyClasspath.txt`(ModDevGradle 生成,107/… 条 jar)+
`build/moddev/artifacts/*.jar` + `build/classes/java/main`(旧编译产物,供未改动类解析)+ 对应加载器的
brigadier(1.21.1 → 1.3.10 / 1.20.1 → 1.1.8)与 curios(1.21.1 → `curios-yohfFbgD.jar`,1.20.1 → `curios-IPQlZkz1.jar`)。

| 编译单元 | 结果 |
|---|---|
| neoforge:`AstralPartyCommand.java` + `EffectCardPeriod.java` + `ModEffects.java` | **exit 0**(0 error;`Note: ... deprecated API` 为既有无害提示) |
| forge:`AstralPartyCommand.java` + `EffectCardPeriod.java` + `ModEffects.java` | **exit 0**(同上) |

- 结论:两版本的 `dump` 实现、`AstralPartyCommand` 的既有子命令、以及新增的 `EffectCardPeriod` 只读入口
  **在真实类路径下均无编译错误**(同时顺带验证了上一批 `/astralparty` 从未编译过的代码)。
- 无类路径的空跑(`javac` 仅给源码)会报 `reference to effectId is ambiguous` / `reference to registerEffectPendingSource is ambiguous`,
  经核实**是缺失类路径导致的伪报**(解析失败后形参退化为 error type,两个重载同时"适用"),带真实类路径后消失。
- **只读性机械核对**:dump 代码块(neoforge 行 281–445)内不存在任何 `ModAttachments.set*` / `EffectCardPeriod.forceResetRound` /
  `removeEffect` / `addEffect` / `hurt` / `give` 调用;块内仅出现 `get*`/`is*` 取值与 `sendSuccess`(玩家 chat)、`LOGGER.info`(日志)两个输出通道
  (命中"写调用"关键词的唯一一处位于**既有** removal 路径的 javadoc 注释里)。

## 9. 未做(明确记录)

- 未执行 `gradlew` / `mt_build.ps1`;**未提交**;未启动游戏;未跑任何用例。
- 未改动 `scripts/test/**`(属另一位执行者),提交时也不得纳入。
