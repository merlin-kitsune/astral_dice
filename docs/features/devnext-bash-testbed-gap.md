# dev-next 测试台：pwsh → bash/python 迁移缺口与补充清单

> 状态：**盘点完成，待用户确认删除节奏**（2026-11-02，DSH）。本文只记录「已有什么 / 缺什么 / 补什么」，不重复交接文档里的合并口径。
> 事实源：`scripts/test/**` 实读（`git ls-files` + 行数），非文档推断。

## 0 一句话

主线带过来的 bash 层已经覆盖 **env / build / launch / stop / live** 与 **fabric 线的 cases / report / preflight**，但 **P0 两线（neoforge-1.21.1 + forge-1.20.1）与 26.1.2 的 cases / report / preflight 仍委托 pwsh**；要「全量删除 pwsh」，必须先补这三块的原生实现，否则 P0 测试台（发布前必跑）会失能。

## 1 已有 bash/python 资产（合并后已在索引里）

| 文件 | 行数 | 职责 |
|---|---|---|
| `scripts/test/win/mt.sh` | 348 | 编排入口（阶段分派、开关、退出码） |
| `scripts/test/win/env.sh` | 484 | E 阶段：run 目录、整合包、实例准备 |
| `scripts/test/win/launch.sh` | 484 | L 阶段：启动（gradle / production / native 三通道） |
| `scripts/test/win/stop.sh` | 398 | 收停、放端口、清存档 |
| `scripts/test/win/live.sh` | 345 | 运行期通道（AutoJS / 日志跟随） |
| `scripts/test/win/lib/common.sh` | 629 | 公共（日志、机器行、路径、进程） |
| `scripts/test/win/lib/phases.sh` | 469 | 阶段实现 + **三处 pwsh 委托** |
| `scripts/test/win/lib/fabric.sh` | 952 | fabric 线原生 cases / report / preflight（**移植蓝本**） |
| `scripts/test/linux/{env,launch,stop}.sh` | 362 / 459 / 222 | Linux 真机测试台 |
| `scripts/test/lib/mt_case.py` | 562 | AutoJS 用例执行器的**残缺** Python 版（依赖缺失的 `mt_autojs`，当前 `python … validate` 直接 ImportError） |
| `scripts/test/prod_smoke.py` | 354 | 生产实例冒烟启动器（**可用**，按实例 json 组装 classpath 与参数） |

## 2 缺口（按 P0 流程的必需程度排序）

| 编号 | 能力 | 现状 | 必需性 | 补充形态 |
|---|---|---|---|---|
| G1 | **cases**（用例执行 + 断言） | P0/n26 委托 `mt_case.ps1`(3332) + `mt_assert.ps1`(1537) + `Mt.*.psm1`(≈5700)；fabric 线已原生 | **P0 必需** | Python：`lib/mt_autojs.py`（端点客户端）+ `lib/mt_assert.py`（断言引擎）+ `lib/mt_case_native.py`（用例执行器） |
| G2 | **preflight** | 委托 `mt_preflight.ps1`(763) | **P0 必需** | Python：`lib/mt_preflight.py` |
| G3 | **report**（mark / summary） | 委托 `mt_report.ps1`(1016) | **P0 必需** | Python：`lib/mt_report.py`；或并入 `mt_case_native.py` 的子命令 |
| G4 | native 启动通道（窗口 origin / guiScale / 键鼠注入 / IME） | 委托 `mt_launch.ps1`(1226) + `Mt.Win32.psm1`(1229) + `mt_ime.ps1`(427) | 非 gradle 实例才用；P0 常规流程用 gradle 通道 | 待定：Python + ctypes（Win32） |
| G5 | 运行期取证（截图 / 注入 / 看门狗 / 清理 / 用例生成 / loader 门控） | `mt_capture.ps1` 482、`mt_inject.ps1` 950、`mt_watchdog.ps1` 465、`mt_cleanup.ps1` 470、`mt_gen_case.ps1` 493、`mt_loadergate.ps1` 558 | `vision` 类断言（17 处）与部分 `crash` 取证依赖 | 分批：先 清理/看门狗，后 截图/注入 |
| G6 | 静态守门 `scripts/verify/*.ps1` | 12 个（chip / bountiful / resource / sign / loader-gate / probe / event-registry / crafting-uniqueness / mutate_chip_shop_ui） | **CI 依赖 2 个**：`tools/check_lang_sync.ps1`、`scripts/verify/verify_chip_acquisition.ps1` | Python 重写 + CI 改指 |
| G7 | `scripts/devtools`(4) / `tools`(3) / `scripts/maintenance`(1) / `scripts/audit`(1) | 全 pwsh | 低频 | Python 重写（`tools/check_lang_sync.py`、`check_changelog_parity.py` 已有） |
| G8 | fabric 生产通道 `ft_*.ps1`(11) | 全 pwsh | fabric 线 cases 已原生；`prod_smoke.py` 覆盖启动 | 删除前核对 fabric.sh 是否仍引用 |

## 3 迁移纪律（写进每个补充件的验收）

1. **等价优先**：不改用例 JSON 语义；`channel=autojs|kubejs` 两套原语与 expect 算子（`min|max|gte|lte|regex|set|contains|len`）逐条对齐 pwsh 版。
2. **机器行不变**：`MT_CASE_RESULT: <case> = PASS|FAIL|ERROR|BLOCKED|TIMEOUT`、退出码 0/1/2/11/12 必须逐字一致，否则既有 CI 与人工判读全废。
3. **不得假绿**：能力缺失（如 `vision` 无等价物、端点未起）一律 `BLOCKED(11)`，禁止记 PASS。
4. **删除前先自证**：新实现能在 `validate` / `--dry-run` / 无游戏环境下跑通，且对 285 个用例 JSON 的静态校验结果与 pwsh 版一致（同错误集），才允许删对应 pwsh。
5. **CI 同步**：`.github/workflows/build.yml` 里 `pwsh -File tools/check_lang_sync.ps1`（4 处）与 `scripts/verify/verify_chip_acquisition.ps1`（1 处）必须在删除同一提交里改指 python，否则 CI 红。

## 4 建议的落地顺序（每步独立可验）

| 步 | 内容 | 完成判据 |
|---|---|---|
| S1 | `lib/mt_autojs.py` + `lib/mt_assert.py` + `lib/mt_case_native.py`（G1） | 285 个用例 `validate` 与 pwsh 版同结论；fabric 线既有原生 cases 不回归 |
| S2 | `lib/mt_preflight.py` + `lib/mt_report.py`（G2/G3）；`win/lib/phases.sh` 三处委托改指 python | `mt.sh --phase preflight` / `--phase cases` / `--phase report` 在无 pwsh 的 PATH 下可跑 |
| S3 | `scripts/verify/*.ps1` → python + CI 改指（G6） | CI 五个 pwsh 调用点归零 |
| S4 | 剩余 pwsh（G4/G5/G7/G8）逐个改写或明确裁撤（如 native 通道改由 python+ctypes 承担） | `git ls-files '*.ps1' '*.psm1'` = 0 |
| S5 | 文档与规则：`docs/agent/AGENTS-dsh.md`（Windows 正本仍写「测试台唯一口径 = PowerShell 7」）、`scripts/test/TESTING-SPEC.md`、`scripts/test/win/README.md` | 规则文本与实现一致，无「用 pwsh」残留指令 |

## 5 待用户裁决

1. **删除节奏**：按 S1→S4 分批（每批删对应 pwsh，P0 测试台全程可用）还是「先全删、后补」（P0 测试台会有一段时间不可用）。
2. **G4 native 启动通道**是否保留能力（保留 ⇒ 需 python+ctypes 重写 Win32 注入；裁撤 ⇒ 只走 gradle / production 启动）。
3. **G5 截图/vision**：`vision` 类断言（17 处）在 python 侧是否需要等价物，还是统一记 BLOCKED 并改用例。
