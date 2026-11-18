# 交接 prompt（Windows / DSH）— 复制即用

> 配套文档：`docs/handover-2026-11-01-windows-ysm-handoff.md`（详细版）
> 生成时间：2026-11-01，来自 Linux 真机会话

```text
你是 DSH，宿主是 Windows 原生（git bash + PowerShell 7）。仓库在 F:\MCProject\astral_dice_multiloader（同一块 NTFS 盘的 Linux 侧是 /mnt/f/MCProject/astral_dice_multiloader，两边是同一份文件）。

先做三件事，再动手：
1) 读 docs/agent/AGENTS-dsh.md（Windows 规则正本）与 AGENTS.md 的共同红线。核心口径：测试台唯一口径是 PowerShell 7（pwsh -NoProfile -File <绝对路径>），不用 Windows PowerShell 5.1、不写 .sh；编译用 gradlew.bat :<线>:classes|jar；实机用 pwsh -NoProfile -File scripts\test\mt.ps1 --version <线> --phase <env|run|stop>；每轮测试收尾必须 --phase stop --force，并在报告里贴 MT_STOP: OK 与 Get-Process java 计数。
2) 读 docs/handover-2026-11-01-windows-ysm-handoff.md（本次交接：会话总结、仓库状态、部署通道、YSM 兼容待办与建议方案、转 Windows 的脚本核验结果、风险）。
3) 需要细节再读 docs/migration-26.1.2-rc7-mapping.md（26.1.2 移植主账本：§17 收尾一致性验证、§17.5 半透明实现）。

当前任务（承接 Linux 会话）：
- YSM（[是，史蒂夫模型] ysm-2.6.5-neoforge+mc26.1）已放进 run\26.1.2\mods。它在 Linux 下自检失败（[YSM] Unsatisfied runtime environment: err: 54），所以兼容实现与验证改到 Windows。
- 步骤 1：确认 Windows 下 YSM 能接管玩家模型（启动日志无该报错；第三人称能看到 YSM 模型）。
- 步骤 2：复现「带铁处女 / 摸鱼的玩家不再半透明」。我们的半透明在 26.1.2 是「render state 标记 + RenderTypes.entityTranslucent + ARGB 顶点色 alpha」；YSM 用 @Overwrite 覆盖 LivingEntityRenderer#submit 并从 EntityRenderDispatcher 自绘，把我们的 getRenderType / submit 钩子绕过了。
- 步骤 3：实现 YSM 兼容层。建议：单独的 astral_dice.ysm.mixins.json（required:false，只放该 mixin，不要塞进必选的 astral_dice.mixins.json）；钩子挂在原版接缝——EntityRenderDispatcher#submit HEAD 记录「当前实体是否带 ALANA_IRON_MAIDEN / DEEPSEEK_FISHING」的标记并在 TAIL 清理，或 SubmitNodeCollector 实现的 submitModel 给传入 ARGB 乘 alpha；禁止直接 hook YSM 混淆类（类名如 o0oOO0oOOO0oOO0o0ooO0o0o，版本敏感）；YSM 缺席时必须零影响。
- 步骤 4：gradlew.bat :neoforge-26.1.2:build 通过；实机 ON/OFF 对照截图；latest.log 里 Mixin apply for mod astral_dice failed = 0；然后提交并在报告里写明部署去向（见交接 §3.1：1.20.1 用 build\devlibs，1.21.1 / 26.1.2 用 build\libs；部署目标是 run\<版本>\mods）。

红线：
- 不 push、不改 Windows 侧既有配置与脚本约定。
- 启动实机属本任务授权范围；但每轮结束必须 stop --force 并核对无 java 客户端残留。
- 提交前先看 git status，若出现不属于自己的改动（含未跟踪文件）立即停手报告。
- 不要用 run\*.sh（Linux 专用：readlink -f / pgrep / /proc / /usr/lib/jvm），Windows 用同名 .bat。
```

## 补充：本次会话产出的提交

- `fd990c15` fix(26.1.2): 补齐收尾验证发现的混入缺口，并把立牌半透明改成 26.1.2 两段式实现
- `ed7c56d4` feat(villager): 采用改良版卡牌商人模型（cardshop_villager_1）并撤掉无帽特例
- `6680e3cf` fix(26.1.2): 筹码商人绑定回 chip_trader 交易集，不再领卡牌
- `9192a0bc` fix(26.1.2): 补齐移植时被合并吞掉的 8 组实现，恢复 1.5.0 行为对齐
- `c3757626` fix(26.1.2): 补齐 Curios 标签、立牌 tooltip、方块物品名与筹码商店文字
- `31103af8` fix(26.1.2): 补回被合并截断的三处界面/文本，修手册皮肤与村民名

（均为本地提交，**未 push**。）
