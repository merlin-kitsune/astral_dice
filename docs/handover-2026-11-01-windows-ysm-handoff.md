# 交接：立牌半透明 × YSM 兼容（由 Linux 真机转 Windows）

> 生成：2026-11-01 14:5x（Asia/Shanghai），DSH on Linux 原生真机
> 上一份同源交接：`docs/handover-2026-10-31-native-linux-migration.md`（宿主迁移）、`docs/migration-26.1.2-rc7-mapping.md`（26.1.2 移植主账本）

## §0 一句话结论

**YSM（是，史蒂夫模型 2.6.5）在 Linux 下自检失败**（`[YSM] Unsatisfied runtime environment: err: 54`，抛点 `com.elfmcys.yesstevemodel.OoOO0oO00OoOO0O0ooOo0Ooo.OO0OoO00ooOOo0o00O000OoO` ← `YesSteveModel.<init>`），玩家模型不会被替换 ⇒ **YSM 半透明兼容必须在 Windows 侧实现与验证**。Linux 侧的 26.1.2 移植与半透明主实现已经完成并提交。

## §1 本次 Linux 会话做了什么

| 阶段 | 内容 | 落点 |
|---|---|---|
| 1 | 26.1.2 移植收尾：对 1.21.1 做六层比对（Java 文件集 / 方法集 / 注册表 / 资源 / 数据包 / 运行时），结论与数字全表 | `docs/migration-26.1.2-rc7-mapping.md` §17.1 |
| 2 | 补齐 4 类「文件在、没接上」的缺口：多萝西两个 mixin 未注册且签名过期、死 mixin 文件、死附件 | 提交 `fd990c15`，§17.2 |
| 3 | 立牌半透明改用 26.1.2 两段式实现（state 标记 + `entityTranslucent` + ARGB 顶点色 alpha），实机 50% 透明通过 | 提交 `fd990c15`，§17.5 |
| 4 | 村民模型换 `cardshop_villager_1`（撤无帽特例）+ 三线同步；摸清三线部署通道 | 提交 `ed7c56d4` 等 |
| 5 | 导入 YSM 到 `run/26.1.2/mods`，撞到 Linux 自检失败 | 本文件 §0 |

期间被用户裁决回退的两件事（**不要重做**）：
- 「给村民补 blockbench 多出的柜台/遮阳棚几何」→ 用户否决并回退（`9180409e`）；后来改为换模型。
- 「无帽渲染器」方案 → 换新模型后整套撤销（`5bf389b8` / `fb6caa82`），三线 `client/render/TraderVillagerRenderer*` 已删除。

## §2 仓库状态（交接时实读）

- 分支 `multi-main`，HEAD **`fd990c15`** `fix(26.1.2): 补齐收尾验证发现的混入缺口，并把立牌半透明改成 26.1.2 两段式实现`。
- 未提交：`AGENTS.md`、`docs/agent/AGENTS-dsh-linux.md`（Linux 侧 autojs 说明）＋ 新文件 `docs/migration-26.1.2-live-checklist.md` ＋ 用户个人资产（`models/astral_anvil.bbmodel`、`models/cardshop_villager_1.bbmodel`、`scripts/test/cases/TMP-ATTRLESS-HYDRA-1.21.1.json`、`scripts/test/resources/kubejs/1.21.1/server_scripts/astral_batch3_probe.js`、`zz_screen_probe.py`）。
- **未 push**（本地领先 origin 多个提交）。

## §3 已确定、Windows 可直接复用的事实

### 3.1 部署通道（这条最容易被坑）

开发客户端**不读源码树**，读 `run/<版本>/mods/*.jar`；改完源码/资源必须重新构建并覆盖。

| 线 | 取哪个 jar |
|---|---|
| `neoforge-26.1.2` | `neoforge-26.1.2/build/libs/astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar` |
| `neoforge-1.21.1` | `neoforge-1.21.1/build/libs/...jar`（NeoForge 不做 reobf） |
| `forge-1.20.1` | **`forge-1.20.1/build/devlibs/...jar`**（`build/libs` 是 reobf 生产 jar，放进 dev 客户端会 `NoSuchFieldError: f_256929_`） |

`run/<版本>/mods` 与整合包 `<实例>/mods` 是同一套部署目的地，覆盖前先确认类型。

### 3.2 26.1.2 渲染与半透明实现（现状）

- 渲染两段式：`extractRenderState → submit(state, PoseStack, SubmitNodeCollector, CameraRenderState)`；`render(...)`、`RenderSystem#setShaderColor` 都已删除。
- 我们的实现（`fd990c15`）：
  - `client/render/TranslucentRenderState` + `mixin/client/LivingEntityRenderStateMixin`：给原版 `LivingEntityRenderState` 挂 `@Unique` 标记（**接口不能放 `...astral_dice.mixin` 包**，否则 `IllegalClassLoadError`）；
  - `SignTranslucencyMixin#astral$markTranslucent`：`extractRenderState` TAIL 写标记（`ALANA_IRON_MAIDEN` / `DEEPSEEK_FISHING`）；
  - `getRenderType` HEAD：命中时 `RenderTypes.entityTranslucent(texture)`；
  - `submit` HEAD/TAIL 维护 `AlphaContext`，`@Redirect` 把 `ARGB.multiply(baseColor, getModelTint(state))` 再 `ARGB.multiplyAlpha(color, 0.5F)`。

### 3.3 YSM 为什么会吃掉半透明（静态结论，待 Windows 实机确认）

- YSM `mixin/client/LivingRendererMixin` 用 **`@Overwrite`** 覆盖 `LivingEntityRenderer#submit`（body 只调 `super.submit`），`mixin/client/EntityRenderDispatcherMixin` 再走自己的管线画玩家模型。
- 结果：我们的 `getRenderType` 覆盖与 `submit` 里的 ARGB 重定向**都被绕过**（`extractRenderState` 的标记仍会写，但没人读）。
- YSM 2.6.5-neoforge+mc26.1 的类名大量混淆（`o0oOO0oOOO0oOO0o0ooO0o0o` 等）⇒ **不要直接 hook 它的内部类**，应挂在原版接缝上。

## §4 Windows 侧待办（按顺序）

1. **读规则**：`docs/agent/AGENTS-dsh.md`（Windows 正本）。核心口径：PowerShell **7** 执行一切脚本（`pwsh -NoProfile -File <绝对路径>`）；编译 `gradlew.bat :<线>:classes|jar`；实机 `pwsh -NoProfile -File scripts\test\mt.ps1 --version <线> --phase <env|run|stop>`；**每轮测试收尾必须 `--phase stop --force`** 并贴 `MT_STOP: OK` 与 `Get-Process java` 计数。
2. **确认 YSM 在 Windows 可用**：`run\start-26.1.2.bat`（或 gradlew）启动，日志不应再出现 `Unsatisfied runtime environment`；第三人称确认玩家模型被替换。
3. **复现**：`/effect give @p astral_dice:alana_iron_maiden 600 0`，第三人称观察是否仍不透明（预期：失效）。
4. **实现兼容层**（建议方案，未验证）：
   - 新建 `neoforge-26.1.2/src/main/resources/astral_dice.ysm.mixins.json`（`required: false`，只放这一个 mixin），在 `neoforge.mods.toml`/`build.gradle` 里按需注册；
   - 钩子建议挂在 **`EntityRenderDispatcher#submit`**（HEAD 记录「当前实体是否带两个效果」到 ThreadLocal，TAIL 清理）或 **`SubmitNodeCollector` 实现的 `submitModel`**（把传入的 ARGB 颜色乘 alpha）；
   - 不要引用 YSM 混淆类；YSM 缺席时该 mixin 不生效（`required:false` + 只在类存在时应用）。
   - 注意：若 YSM 用自己的 `RenderType`（非 `entityTranslucent`），仅改 alpha 可能被 alpha-test 丢掉，需要同时确认它的 render type；必要时在 HEAD 里把 render type 也换掉（该参数在 `submitModel` 上）。
5. **验证**：`gradlew.bat :neoforge-26.1.2:build` → EXIT=0；实机 ON/OFF 对照截图；`latest.log` 里 `Mixin apply for mod astral_dice failed` = 0。
6. **提交 + 部署**：`deploy.ps1` 或手工把 jar 覆盖到 `run\<版本>\mods`（见 §3.1 的类型约定）。

## §5 转 Windows 的脚本核验结果（本次在 Linux 侧能做的部分）

| 检查项 | 结果 | 证据 |
|---|---|---|
| `run\start-*.bat` 存在且 CRLF | ✅ | `file` ⇒ `DOS batch file, ASCII text, with CRLF line terminators`；脚本用 `cd /d "%~dp0.."` + `gradlew.bat :<线>:runClient`，盘符无关 |
| `gradlew.bat` / wrapper | ✅ | `gradlew.bat` 在根目录；`gradle/wrapper/gradle-wrapper.jar` 43,764 B；`distributionUrl` 走 aliyun 镜像 |
| `*.ps1` CRLF + PowerShell 7 | ✅ | `file` ⇒ UTF-8 CRLF；`mt.ps1` / `mt_env.ps1` / `deploy.ps1` 首行均 `#Requires -Version 7.0` |
| `*.ps1` 里的 Linux 路径 | ✅ 仅 1 处且无害 | `scripts/verify/verify_probe_class_refs.ps1:383` 的 `/mnt/c/Users/<user>/.m2/repository` 是 **WSL 兜底候选**，Windows 上不会命中 |
| git worktree 指针（dev-next / starengine_next） | ✅ 已是**相对路径** | `astral_dice_multiloader-next/.git` = `gitdir: ../astral_dice_multiloader/.git/worktrees/astral_dice_multiloader-next`；admin `gitdir` = `../../../../astral_dice_multiloader-next/.git` ⇒ 两个系统都能解析 |
| `run\*.sh`（git bash 会不会用到） | ⚠️ **Linux 专用，Windows 别用** | 内含 `readlink -f` / `pgrep` / `/proc/<pid>/cwd`，且 `DEFAULT_JDK="/usr/lib/jvm/java-21-openjdk-amd64"`；Windows 一律用同名 `.bat` |
| `scripts\test\linux\*.sh` | ⚠️ Linux 专用 | 用 `findmnt` / `systemctl --user`；Windows 用 `scripts\test\mt*.ps1` |
| `scripts\dev\rsync-original.sh` | ⚠️ 已停用 | 非 `ALLOW_LEGACY_HGFS_RSYNC=1` 时 `exit 3`（hgfs 时代遗留） |
| `.gitattributes` 对脚本的行尾策略 | ✅ 已覆盖关键类型 | `*.sh text eol=lf`、`*.py text eol=lf`、`gradlew text eol=lf`、`src/generated/**/*.json eol=lf`；`*.bat` / `*.ps1` 未写规则，靠 `core.autocrlf=true`（本仓实读 = true）检出为 CRLF，磁盘现状已确认 CRLF |
| `run/` 是否污染 git | ✅ 不污染 | `.gitignore:61 run/` ⇒ `run/26.1.2/mods/*.jar`（含 63 MB 的 YSM）不会进 `git status` |
| git hooks（post-commit/checkout/merge） | ✅ 双平台自适应 | hook 内 `command -v pwsh` 优先、`/mnt/c/Program Files/PowerShell/7/pwsh.exe` 兜底，`case "$exe" in /mnt/*)` 才走 `wslpath` 翻译；原生 Windows git bash 直接命中 `pwsh` 分支，路径由 MSYS 自动转换 |
| `.git/config` / 工作树指针里的 Linux 绝对路径 | ✅ 无 | `git config` 无 `/mnt`、`/home`；两个 worktree 指针都是相对路径（见上一行） |
| 本机能否实跑 pwsh | ❌ 不能 | Linux 侧无 `pwsh`/`powershell`，`.ps1` 只能做静态核验；实际执行请在 Windows 侧跑并回贴输出 |

## §6 交接 prompt（复制进 Windows 的 DSH 会话）

```text
你是 DSH，宿主是 Windows 原生（git bash + PowerShell 7）。仓库在 F:\MCProject\astral_dice_multiloader（同一块 NTFS 盘的 Linux 侧是 /mnt/f/MCProject/astral_dice_multiloader，两边是同一份文件）。

先做三件事，再动手：
1) 读 docs/agent/AGENTS-dsh.md（Windows 规则正本）与 AGENTS.md 的共同红线；测试台唯一口径是 PowerShell 7（pwsh -NoProfile -File <绝对路径>），编译用 gradlew.bat，实机用 pwsh -NoProfile -File scripts\test\mt.ps1 --version <线> --phase <env|run|stop>；每轮测试收尾必须 --phase stop --force 并贴 MT_STOP: OK 与 Get-Process java 计数。
2) 读 docs/handover-2026-11-01-windows-ysm-handoff.md（本次交接：会话总结、仓库状态、部署通道、YSM 兼容的待办与建议方案、转 Windows 的脚本核验结果）。
3) 需要细节时再读 docs/migration-26.1.2-rc7-mapping.md（26.1.2 移植主账本，§17 是收尾一致性验证，§17.5 是半透明实现）。

当前任务（承接 Linux 会话）：
- YSM（[是，史蒂夫模型] ysm-2.6.5-neoforge+mc26.1）已放进 run\26.1.2\mods；它在 Linux 下自检失败（Unsatisfied runtime environment: err: 54），所以兼容与验证改到 Windows 做。
- 第一步先确认 Windows 下 YSM 能正常接管玩家模型（启动日志无该报错、第三人称看得到 YSM 模型）。
- 第二步复现「带铁处女/摸鱼的玩家不再半透明」（我们的半透明在 26.1.2 是 render state 标记 + entityTranslucent + ARGB 顶点色 alpha，YSM 用 @Overwrite 覆盖 LivingEntityRenderer#submit 并从 EntityRenderDispatcher 自绘，把我们的 getRenderType/submit 钩子绕过了）。
- 第三步实现 YSM 兼容层：单独的 astral_dice.ysm.mixins.json（required:false），挂在原版接缝（EntityRenderDispatcher#submit 记录当前实体标记，或 SubmitNodeCollector 实现的 submitModel 给 ARGB 乘 alpha），禁止直接 hook YSM 的混淆类；YSM 缺席时必须零影响。
- 第四步：gradlew.bat :neoforge-26.1.2:build 通过、实机 ON/OFF 对照截图、日志 Mixin apply for mod astral_dice failed = 0，然后提交并部署（部署类型见交接文档 §3.1：1.20.1 用 build\devlibs，1.21.1/26.1.2 用 build\libs；部署目标是 run\<版本>\mods）。

红线：不 push；不启动实机除非用户明确要求（本任务已包含实机，属用户下达）；不改 Windows 侧既有配置；提交前先看 git status 里有没有不属于自己的改动。
```

## §7 风险与注意

- **别用 `run\*.sh`**：那是 Linux 版，Windows 上会因 `readlink -f` / `/proc` / `/usr/lib/jvm` 失败。
- **别把 reobf jar 塞进 1.20.1 dev 客户端**（`NoSuchFieldError: f_256929_`），用 `build\devlibs`。
- YSM 的类名混淆且版本敏感；一旦要升级 YSM，兼容层只依赖原版接缝才安全。
- 26.1.2 的 `astral_dice.mixins.json` 现在 client=7（含 `LivingEntityRenderStateMixin` / `SignTranslucencyMixin`），新增 YSM 配置请单独一个 json，避免把软依赖塞进必选配置。
