# 功能测试流程 — astral_dice forge-1.20.1 dev 环境

> ⚠️ **本文档已弃用（历史存档，请勿照此执行）**
>
> 文中引用的 `inject_input.ps1` / `prepare_world_forge.ps1` / `collect_report_forge.ps1` /
> `functional_test_forge.ps1` 等 **PowerShell 脚本已全部下架并归档到
> `temp/legacy_scripts_20260912.zip`**（含 MANIFEST，可解压取回），按本文执行会直接失败。
>
> **现行流程**：见 `AGENTS.md` →「自动化测试流程（Automated Testing）」，
> 唯一入口 `bash scripts/test/mt.sh`（bash + python 工具链，阶段 P/B/E/L/C/R）。
> 本文保留仅为查阅历史用例口径（TC 清单）与当年的环境约定。

> 对象版本:multi-1.20.1/1.21.1 HEAD(卡牌格数仅由星级决定:0★=4 / 1★=6 / 2★=8 / 3★=12,攻防各半)
> 环境:dev runClient(`run/1.20.1`,Mojmap 命名;模组全部经 `modImplementation` curse maven 引入)
> 光影:ComplementaryUnbound_r5.9(1.20.1 与 1.21.1 两环境均替换,r5.8.1 已删除)
> 复用工具:`scripts/test/inject_input.ps1`(新增 h / 1-9 / shift-rclick)、`prepare_world_forge.ps1`、`collect_report_forge.ps1`

## 1. 环境阶段
| 阶段 | 命令 | 通过条件 |
|---|---|---|
| P0 构建 | `.\gradlew.bat :forge-1.20.1:classes` | BUILD SUCCESSFUL |
| P1 准备世界 | `powershell -ExecutionPolicy Bypass -File scripts\test\prepare_world_forge.ps1` | `run/1.20.1/saves/testworld/level.dat` 存在 |
| P2 启动 | `powershell -ExecutionPolicy Bypass -File scripts\test\functional_test_forge.ps1 -Phase launch` | latest.log 出现 `logged in with entity`,无 crash-reports |
| P3 停止 | 同上 `-Phase stop` | 无 java 残留 |
| P4 报告 | 同上 `-Phase report` | reports/<日期>_function_1.20.1.md 生成 |

## 2. 功能测试用例(TC)

前置:聊天命令可用(测试世界 allow-cheats)。注入工具向 Minecraft 窗口发送按键/命令;
每次截图用 F2(存 `run/1.20.1/screenshots/`)或 Harness WGC 截图。

星级 NBT(1.20.1 ItemStack tag 直接写 `weapon_enhancement`):
`/give Dev astral_dice:dice{weapon_enhancement:{used_cost:0,max_cost:3,used_defense_cost:0,max_defense_cost:3,star_level:N,applied_stones:[]}}`
(先 `/clear Dev astral_dice:dice` 清场,避免同物品重复装备限制)

### TC0 启动与渲染栈
- 步骤:按 P1/P2 启动,进入 testworld。
- 断言:日志无 `Mixin apply failed`/FATAL;`Astral Dice mod loaded.`;Oculus r5.9 相关 WARN 记录(不阻断)。
- 截图:world 画面。

### TC1 装备 0★ 骰子 + 卡牌栏 4 格(攻 2/防 2)
1. `/give` 0★ dice → 按 `1` 选中快捷栏槽 1 → `shift-rclick`(潜行右键自动装备)
2. 按 `h` 打开卡牌界面
3. 断言:截图顶部攻击行 2 个格位 + 防御行 2 个格位,合计 4;无其他可用格位
4. `escape` 关闭

### TC2 1★ → 6 格(攻 3/防 3)
同 TC1,NBT `star_level:1`;断言合计 6。

### TC3 2★ → 8 格(攻 4/防 4)
同 TC1,NBT `star_level:2`;断言合计 8。

### TC4 3★ → 12 格(攻 6/防 6)
同 TC1,NBT `star_level:3`;断言合计 12。

> 每次换星级前:先打开物品栏(E)把 Curios dice 槽中的骰子点回物品栏,再 `/clear` + 重新 give/equip。
> 或直接 `/clear` 后使用新骰子(需先卸下旧骰子,见 TC 步骤)。

### TC5 无隐藏可用格(回归)
- 对每个星级截图核对:格位总数 = 星级表;点选"第 N+1 个"不存在的格位(点击暗区)无任何响应/无拾取到格。
- 断言:界面暗区(格位边界外)不可放入卡牌。

### TC6 特殊卡图标同步(forge 与 neoforge 视觉一致)
- `/give` 一张特殊卡(如 `astral_dice:attack_card_shadow_strike`)放入攻位(可经由卡牌选择器/拖拽)。
- 断言:特殊卡图标按标准格位尺寸渲染(无 0.8x 放大);与 neoforge 1e0047f 同步说明一致。

### TC7 运行时稳定性
- 游戏内停留 ≥3 分钟(可切世界/走动/开关界面)。
- 断言:无新 crash-report;latest.log 无 ERROR/FATAL。

## 3. 报告产物
- 报告:`scripts/test/reports/<日期>_function_1.20.1.md`
- 截图:`run/1.20.1/screenshots/` 下 TC*.png
- 日志:`run/1.20.1/logs/latest.log`、`run/launch-1201.out.log`、`run/1.20.1/runclient_launch.log`


## 4. 实测补充(2026-09-03 首轮执行)
- 世界准备:functional_test_forge.ps1 -Phase prepare 优先从
  scripts/test/resources/testworld-seed-1.20.1.zip 恢复(规避 oculus 无 side=CLIENT
  导致 dev runServer 崩溃的问题);无种子时才走 prepare_world_forge.ps1 全量重建
  (此时需临时注释 build.gradle 中 oculus modImplementation 行)。
- 自动化断言命令(dev 环境 KubeJS 脚本 run/1.20.1/kubejs/server_scripts/astral_functional_test.js):
  - /astraltest equip <0-3>   将对应星级骰子写入 Curios dice 槽(绕过 GUI)
  - /astraltest slotcheck0..3 构造 CardInventoryMenu 并回显可见槽数(权威数值断言)
- 实测结果(服务端数值):0★ card=4 atk=2 def=2;1★ card=6 atk=3 def=3;
  2★ card=8 atk=4 def=4;3★ card=12 atk=6 def=6 —— 与星级表一致 ✅
- 观测项:
  1) 启动时 RecipeManager 报 "Parsing error loading recipe astral_dice:cursed_sword_chip"
     (patchouli 提示 recipe not found)——疑似配方文件问题,待修(非本次卡牌格数范围)。
  2) Oculus r5.9 在 1.20.1 仍有 BIOME_PALE_GARDEN 等新群系 uniform 解析警告(捕获后回退,不阻断)。
  3) 键盘焦点:Harness 下真实按键可能不达 MC,统一用 inject_input.ps1(PostMessage)。
- 证据目录:run/1.20.1/screenshots/evidence/(
  evidence_slotcheck_star01.png、evidence_slotcheck_star23.png、evidence_cardgui_star3.png、latest.log、kubejs_server.log)
