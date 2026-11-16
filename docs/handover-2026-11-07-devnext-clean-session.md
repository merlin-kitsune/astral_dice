# multi-dev-next 交接（2026-11-07 18:1x）
<!-- 历史记录：文中旧 shell 命令已于 2026-11-09 全部废弃，现用 bash 测试台（scripts/test/win/mt.sh）与 windows MCP，仅作溯源。 -->

> **一句话**：`multi-dev-next` 分支上，**fabric 两条线的 tooltip 边框第三方兼容已收口并实机验收**
> （背包 + JEI 面板都跟随稀有度档位色、换物品不串色），本地有一串未 push 的提交，工作树只剩两个未跟踪文件。
> 本文面向「干净会话 + 标准模式」接手：先读 §3 的红线，再按 §7 实读核对，最后从 §6 挑活干。

## 1. 位置与身份

| 项 | 值 |
|---|---|
| 工作树 | `F:/MCProject/astral_dice_multiloader-next`（主仓 `astral_dice_multiloader` 的 **git worktree**） |
| 分支 | `multi-dev-next` |
| HEAD | `b7d71a1c`（2026-11-07 18:1x，本地提交；**未 push**） |
| 版本 | 五线 `mod_version=26.4-SNAPSHOT.6+<line>` |
| 前置库 | `starengine_version=26.4-SNAPSHOT.1`，区间 `starengine_version_range=>=26.4-SNAPSHOT.1 <27.0.0`（源码在 `F:/MCProject/starengine_next`，分支 `next`） |
| 五条自包含线 | `forge-1.20.1` / `neoforge-1.21.1`（P0）、`neoforge-26.1.2` / `fabric-1.20.1` / `fabric-1.21.1`（P1） |
| 与主线关系 | 主线 1.5.0 的 36 个提交已反同步完成（`docs/handover-2026-11-02-dev-next-mainline-150.md` 是那轮的交接） |

## 2. 实读快照（2026-11-07 18:16）

```
git -C F:/MCProject/astral_dice_multiloader-next rev-parse --abbrev-ref HEAD   # multi-dev-next
git -C F:/MCProject/astral_dice_multiloader-next log --oneline -1              # b7d71a1c
git -C F:/MCProject/astral_dice_multiloader-next log --oneline origin/multi-dev-next..HEAD | wc -l   # 10（未 push）
git -C F:/MCProject/astral_dice_multiloader-next status --short -uall
#   ?? docs/features/tooltip-frame-third-party-compat.md   ← 本会话新增文档（下一步要提交）
#   ?? 立牌计划表.txt                                        ← 一直未裁决是否入库，**别 add**
```

## 3. 规则正本与红线（先读这一节，别跳过）

- 规则正本：根 `AGENTS.md` → `docs/agent/AGENTS-dsh.md`（Windows 宿主）。
  `docs/agent/AGENTS-full.md` 的红线区被 `docs/agent/verify-split.py` 逐字镜像校验；TOC 由 `tools/gen_agents_toc.py` 生成。
- **本周期新增/裁决的三条规则**（`2c2c9bcd`）：
  1. 派子代理**不再要求**它加载 `using-superpowers`（去角色扮演化）；
  2. 解释器一律写裸名（`bash` / `python`），**旧 shell 全废弃**；禁绝对路径写解释器；
  3. **一个文件同一时刻只一个写者**；发现别的实机（别的会话/别的线）在跑，**必须搁置并告知用户**。
- **提交纪律**（`838bcf69` 用户裁决）：完成一个可独立验证的单元就**立即本地提交**；
  **默认不 push**（要用户明确放行，远程走代理 `http://127.0.0.1:7897`）。
- 红线本身不变：**默认不启动实机 / 不部署 / 不 commit-push 未获授权 / 保护素材与他人改动 / 改文件或跑 Gradle 前查 `F:/MCProject/.agent-locks/`**。
- 默认编辑器口径：`docs/*.md` 与 HTML/MAPPING 用 **LF**；`CHANGELOG*` 与 `release/*.md` 用 **CRLF**；Java / json / gradle 用 **CRLF**。
  改完用 `python -c` 数 CRLF 与裸 LF 复核。

## 4. 本周期（2026-11-05 → 11-07）完成的工作

**工程结构**
- 反同步主线 36 提交落地（版本线与 `starengine` 引脚按本线保持，未被主线覆盖）。
- 测试台 **旧 shell → bash + python** 全量切换：入口 `bash scripts/test/win/mt.sh --version <线> --phase <env|build|launch|cases|report|stop>`，
  能力层 `scripts/test/win/*.sh` + `scripts/test/mt_*.py` + `scripts/test/lib/mt_*.py`；
  `AGENTS-full.md` 的「自动化测试流程」整章重写（604 行 → 523 行），`verify-split.py` 加「章节排除」。
- 清理 83 份废弃 md（判据：被规则索引或源码引用者保留），悬空引用机械打墓碑「（已删除 2026-11-05）」。

**加载器扩展**
- `fabric-1.21.1` 新线从零落地：Trinkets-only（`fabric.mod.json` 的 `breaks` 写 accessories + accessories_compat_layer）、
  JEI 钉 `19.56.0.441`、sodium `0.6.13` + iris `1.8.8`、modmenu `11.0.5` + cloth-config `15.0.140`；
  与 `neoforge-1.21.1` 做过注册表/配方/lang/tooltip 键集全等核查（有意放弃 irons_spellbooks、enigmaticlegacyplus）。
- Forge 47.1.47 降级：14 处 `ResourceLocation.fromNamespaceAndPath` 改二参构造，
  `registerDisplayTest` 改 `registerExtensionPoint(IExtensionPoint.DisplayTest.class, …)`（库侧同步降级并 publishToMavenLocal）。

**内容**
- 魔渊幼体立牌 `astral_dice:sykes_sign`（传奇）+ 魔渊触须：`AbyssMarkManager`（1200 tick、上限 20 层、`APPLY_COOLDOWN_TICKS=200`）、
  `SykeTentacleEntity`（`ATTACK_INTERVAL_TICKS=120` / `ATTACK_HIT_DELAY_TICKS=12` / `ZONE_HALF=2.5` / `AGGRO_RADIUS=4.0` /
  `STRIKE_DAMAGE=4.0` / `SLOW_TICKS=600` / `LIFETIME_TICKS=12000`）、占格方块 `astral_dice:abyss_tentacle_collision`；五线实机验收通过。
- 星币拾取修复：Puzzles `ItemEntityEvents.PICKUP` 在 `Inventory#add` 之后读到 count=0 ⇒ 改接 `ItemEntityEvents.TOUCH`。
- 饰品种类槽 tooltip 三语补齐（`astral_dice.{chip,dice,stand}`）；tooltip 标点全量归一（五线 × 三语）+ 守门 `tools/verify_tooltip_format.py`。

**UI**
- CardChoice / CardShop / ChipShop / CardInventory 五线补背景暗化（1.20.1 双线手调 `renderBackground`，
  26.1.2 调 `extractTransparentBackground`，1.21.1 两线原版自带）。
- 26.1.2 筹码图标错用 10 参 `blit`（源尺寸被吞成 w/h ⇒ 只采样左上 24×24）改 12 参并显式传 32。
- 26.1.2 tooltip 边框改 NeoForge 官方 sprite 通道（`RenderTooltipEvent.Texture#setTexture`），
  52 张贴图在 `assets/astral_dice/textures/gui/sprites/tooltip/`；彩虹档因九宫格**平铺**（非拉伸）会有色带断层
  ⇒ 改单色 + 30 相位随时间流动（`RAINBOW_PHASES = 30`，`rainbow00..rainbow29`）。

**fabric tooltip 第三方兼容（本周期最后一段，已收口）**
- 基础桥：`platform/client/TooltipFrameColors`（ThreadLocal 栈）+ `mixin/bridge/{ClientTooltipBridgeMixin, ContainerTooltipBridgeMixin,
  TooltipRenderUtilColorMixin, TooltipBackgroundSpriteMixin}` + `client/ModernUITooltipCompat`（反射写 `TooltipRenderer.sStrokeColor` 四槽、
  临时 `sAdaptiveColors=false`、`enterTooltipStack/exitTooltipStack` 写/还原 `GuiGraphics#modernUI_MC$tooltipStack`）。
- JEI 桥：`fabric-1.21.1` 挂 `mezz.jei.fabric.platform.RenderHelper#renderTooltip` 6 参/7 参；
  `fabric-1.20.1`（JEI 15.74.0.234）**那两个方法都 `astore_0` 复用 this 槽 ⇒ Mixin 注入必 `VerifyError`**，
  改挂 `mezz.jei.common.gui.JeiTooltip#draw(...)`。完整证据链与判据见
  **`docs/features/tooltip-frame-third-party-compat.md`**（含两份 VerifyError 原文与 javap 片段）。

**环境**
- YSM 已从四个整合包 `mods` 移除（移到 `D:/.minecraft/_ysm_parked_20261106/`）⇒ `neoforge-26.1.2` 现在**可以 dev 形态启动**，
  原来的「YSM err54 只能 production 启动」口径作废（仍保留 production 形态脚本 `run/26.1.2/.tmp/prodlaunch.py`）。

## 5. 本地未推送的 10 个提交

```
b7d71a4c fix(ui): fabric 两线 tooltip 边框在 JEI 里恢复档位色（含 Modern UI 栈注入）
3b7ad1f1 fix(ui): tooltip 彩虹边框改单色相位，消除九宫格平铺造成的色带断层
80a14179 fix(ui): 统一 tooltip 边框为九宫格贴图通道——fabric 补 JEI 物品栈桥、彩虹档改 30 相位
2c2c9bcd docs(rules): 子代理工程规范去角色扮演、环境配置固化、新增单写者/实机互斥红线
838bcf69 docs(rules): 明确「早提交、勤提交」（2026-11-07 用户裁决）
482c3f07 feat(sign,entity): 魔渊幼体立牌 + 魔渊触须（五线）+ P1 三线 tooltip 边框染色
fcd9b31c docs(tooltip): 规则 5「标点与留空」入规范 + 新守门 verify_tooltip_format.py
b1fddf61 style(lang): tooltip 标点全量归一（五线 × 三语）+ sykes 文案终稿
34f5a8f3 feat(abyss): 新增立牌「魔渊幼体」与「魔渊触须」，P0 两线落地
52430794 fix(testbed): MSYS2 下 cmd //c 被路径转换破坏 → 统一走 mt_cmdwin
```

## 6. 未完成 / 待裁决

1. **push**：10 个提交等用户放行（远程走代理）。放行前不要动 `origin`。
2. **`立牌计划表.txt`**：是否入库仍未裁决（当前未跟踪）。
3. **`temp/` 约 4.09 GB** 清理、**`.gitattributes` 行尾策略**两项旧待办仍在。
4. `docs/features/` 下还有一批「立牌规格」未实现（`kangel-ame-sign-spec`、`lingling-sign-spec`、`mamushi-sign-spec`、
   `nardis-sign-spec`、`fengshui-sign-spec`），是否实现由用户点单。
5. 本分支**不做 CurseForge 操作**（2026-11-02 用户明确：这条线忽略 CurseForge 相关内容）。

## 7. 接手第一步（照抄执行）

```bash
cd /f/MCProject/astral_dice_multiloader-next
git rev-parse --abbrev-ref HEAD                    # 期望 multi-dev-next
git log --oneline -3
git status --short -uall
grep -h '^mod_version' */gradle.properties          # 期望五线 26.4-SNAPSHOT.6+<line>
grep -h '^starengine_version' */gradle.properties | head -4   # 期望 26.4-SNAPSHOT.1
netstat -ano | grep -E ':(25585|25565) ' | head    # 有输出 ⇒ 已有实机在跑，先问用户再动
```

**别急着动手**：先确认三件事 —— ① 工作树干净（只允许 `立牌计划表.txt` 未跟踪）；
② 没有别的实机占着 `25585/25565`；③ 用户这次要动哪条线（P0 还是 P1）。

## 8. 常用命令（bash 口径，Windows 宿主）

```bash
# 构建（必须后台跑，前台 600s 会被截断）
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :<line>:build -x test --console=plain

# 起客户端
bash scripts/test/win/launch.sh --version fabric-1.20.1 --side client --enable-autojs --wait-world 600 --timeout 900

# 收停（stop.sh 对 //PID 的转义在本机常失败，残留时补刀）
bash scripts/test/win/stop.sh --version fabric-1.20.1
参数转译开关='*' cmd /c 'windows MCP 的 `Process(mode:"kill", pid=…)` /PID <pid> /T /F'

# AutoJS 探针（无参构造；eval_js 会把代码包成 JSON.stringify(...)，多语句写成 (function(){...})()）
python - <<'PY'
import sys; sys.path.insert(0,'scripts/test/lib')
from mt_autojs import AutoJs
c = AutoJs(); print(c.eval_js('(function(){return 1+1})()', side='client', mode='json'))
PY
```

## 9. 本周期踩过的坑（工具/环境向）

- **`cmd //c` 被 MSYS2 路径转换破坏** ⇒ 统一走 `mt_cmdwin`；补刀杀进程要 `参数转译开关='*'`。
- **AutoJS 的 `Java.loadClass("...").class` 取不到 `.class`**（Rhino 报 has no public instance field）
  ⇒ 要反射类，用 `Java.loadClass("java.lang.Class").forName("...")`。
- **`VerifyError` 是类加载期错误，而 dev run 的类加载是懒的** ⇒ 「客户端起来了」不能当作 mixin 生效的判据，
  必须显式 `Class.forName` 触发加载（本次两次崩溃都是这样提前抓到的）。
- **`require = 0` 只管匹配次数，救不了字节码非法**（`astore_0` 复用 this 槽那类问题）。
- **fabric 线必须 `--side client`**；launch.sh **不透传** `-PtestSodium=false`，
  Sodium 版本不匹配会崩，需要时直接跑 gradlew 带该参数。
- 26.1.2 线若要走 production 形态：`python run/26.1.2/.tmp/prodlaunch.py --quickplay testworld --log <log>`。

## 10. 关联文档

| 需要什么 | 去哪里 |
|---|---|
| 本周期 tooltip 三方兼容的完整证据链 | `docs/features/tooltip-frame-third-party-compat.md` |
| 主线 1.5.0 反同步那轮的交接 | `docs/handover-2026-11-02-dev-next-mainline-150.md` |
| 测试台（bash 口径）决策与 旧 shell 退役登记 | `docs/features/devnext-bash-testbed-decisions.md`、`docs/features/devnext-test-asset-retirement-register.md` |
| 用例 schema 权威 | `scripts/test/TESTING-SPEC.md` |
| 立牌英日命名规则 | `docs/agent/AGENTS-full.md`〈立牌 / 物品英日命名规则〉+ `tools/sign_display_names.json` |

---

交接作者：DSH（multi-dev-next 会话，2026-11-07 18:1x）；所有数字实读，漂移以命令输出为准。
