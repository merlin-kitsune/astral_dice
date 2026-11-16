# fabric-1.20.1 开跑命令序列（AutoJS 通道）

> 生成时间 2026-11-02T03:3x +08:00 · 仓库 `F:\MCProject\astral_dice_multiloader` · 全部命令都在仓库根执行（bash = Git Bash / MSYS，PowerShell = pwsh 7.6.9）
> 配套证据文档：`work/fabric-channel-fix.md`（部署证据 / 判据行号 / 27 项断言审计）

## 0. 前置事实（开跑前先知道）

| 项 | 值 | 依据 |
| --- | --- | --- |
| 通道方案 | `-PautojsTestMod=1` → mavenLocal 的 `AutoJS-fabric-1.20.1-1.0.0.jar` 走 **modRuntimeOnly 运行期 classpath**（Loom 解析期 intermediary→named），**不进 mods/** | `scripts/test/TESTING-RULES-OVERVIEW.md:191` 规则 6；`fabric-1.20.1/build.gradle:199-201` |
| 严禁 | 任何 `AutoJS-*.jar` 出现在 `fabric-1.20.1/run/*/mods`（会同时触发 Loom 注入 ⇒ 同 modId 双载 ⇒ 启动崩；本会话已在 `launch.sh` 加了 fail-closed 守卫，真启动会 exit 11 `fabric_autojs_duplicate`） | `scripts/test/win/launch.sh:191-210`（改后） |
| 端点 | `http://127.0.0.1:25585/mcp`，探活 `GET /health`（200） | `scripts/test/win/lib/common.sh:426-428` |
| 客户端基线 | 当前 `run/client` 可正常起客户端：2026-11-02 02:28 那次 `Sound engine started`（L265）、`Total time to load game and open world was 16.16 s`（L365），modlist 行 `- astral_dice 1.5.0+fabric_1.20.1`（L8） | `fabric-1.20.1/run/client/logs/latest.log` |
| 断言 a1 的版本串 | 用例要求 `- astral_dice 1\.5\.0\+fabric_1\.20\.1`；当前 `fabric-1.20.1/gradle.properties:29 mod_version=1.5.0+fabric_1.20.1` 匹配（旧日志 22:55 那次是 `1.5.0-rc.7+…`，不匹配 ⇒ a1 是版本钉死的） | 用例 `:177` |

## 1. 端口预检（必做，别占别的线的端口）

```bash
# 用途：确认 25565（服务器）/25585（AutoJS）/25575（RCON）三条端口都没被占
# 风险：25585 被占 = 上一次 AutoJS 进程没清干净，先跑第 8 步 stop，别直接启动
pwsh -NoProfile -ExecutionPolicy Bypass -File work/_probe/ports.ps1
```

2026-11-02T03:29 实测：`PORT 25565 free` / `PORT 25585 free` / `PORT 25575 free`。

## 2. 轮转日志（必做，否则 log 断言会假绿）

```bash
# 用途：用例的 log 断言 scope=whole 直接读 latest.log；不轮转会读到上一轮遗留行（尤其 :275 的 absent 断言）
# 风险：mv 失败（文件被占用）说明上一轮客户端还活着 ⇒ 回到第 8 步 stop
mv fabric-1.20.1/run/client/logs/latest.log fabric-1.20.1/run/client/logs/latest-$(date +%Y%m%d-%H%M%S).log
mv fabric-1.20.1/run/server/logs/latest.log fabric-1.20.1/run/server/logs/latest-$(date +%Y%m%d-%H%M%S).log
```

注：本线跑的是**集成客户端**（`runClient` + `-Pquickplay=testworld`），集成服务端与客户端写同一个 `run/client/logs/latest.log`；`run/server/logs/latest.log` 是分离式服务端专用，轮转它只是防止串线。

## 3. 环境体检（不跑构建、不起游戏）

```bash
# 用途：JDK / run 目录 / mods 统计 / 端点 / RCON 诊断；纯只读
# 风险：fabric 行会打印 autojs=0（mods 无 jar）——这是**正常**（env.sh:207-208 注释），不要据此判断通道不可用；判据在 launch.sh
bash scripts/test/win/mt.sh --version fabric-1.20.1 --phase env
```

## 4. 构建（jar 断言需要 build/libs 产物）

```bash
# 用途：编译 + 出 jar；用例 :279 jar 断言扫 fabric-1.20.1/build/libs/astral_dice-*.jar
# 风险：会起 gradle daemon、吃 CPU/磁盘；与实机测试并行时可能拖慢别的线。单 phase 不受 --allow-auto 闸门限制
bash scripts/test/win/mt.sh --version fabric-1.20.1 --phase build
cmd //c "gradlew.bat :fabric-1.20.1:jar --console=plain"
```

（本会话被禁止执行 gradle，此步由实机线执行。只跑 `--phase build` 只到 classes，**不产 jar**，所以补一条 `jar`。）

## 5. 启动（唯一的通道注入点）

```bash
# 用途：起集成客户端 + 注入 AutoJS；等到 world marker 与端点就绪；成功后写 .mt_autojs_injected
# 参数：--wait-world 180 = marker 未到再多等 180s；--timeout 600 = 就绪等待总预算
# 风险 1：exit 0 可能是「marker 兜底」——AutoJS 端点从未应答时 launch.sh 仍 exit 0（:416-419）。必须看输出里的
#         autojs_line（期望 autojs_deployed=1 / probe_enabled=1 / fabric_injected=1）与 ready 行是否含「marker 兜底」
# 风险 2：exit 11 + blockers 含 fabric_autojs_duplicate ⇒ mods 里混进了 AutoJS jar，删掉再启
# 风险 3：exit 12 TIMEOUT ⇒ health 200 但 status 没报 client available，通道起来了但客户端侧没注册
bash scripts/test/win/launch.sh --version fabric-1.20.1 --side client --enable-autojs --wait-world 180 --timeout 600
```

## 6. 通道自证（本会话唯一无法静态验证的一步，放最前面当第一检查点）

```bash
# 用途 A：确认 25585 真在服务
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:25585/health     # 期望 200

# 用途 B：确认 PowerShell 侧五个操作面都判给 autojs（而不是 delegated）
# 期望：command=autojs key=autojs mouse=autojs shot=autojs reload=autojs
pwsh -NoProfile -ExecutionPolicy Bypass -File work/_probe/verify-channel.ps1
```

判据：`verify-channel.ps1` 读到 `fabric-1.20.1/run/client/.mt_autojs_injected` 含 `autojs=1` ⇒ deployed=True。若仍 False，说明客户端不是经 `launch.sh --side client --enable-autojs` 起的（state 文件没写），或 `--disable-autojs`/`MT_FAB_AUTOJS=0` 生效了。

## 7. 跑用例

```bash
# 用途：跑 RELEASE150 的 27 条断言（19 log + 3 eval + 1 absent + 1 jar + 1 crash + 2 vision）
# 为什么不用 mt.sh：phases.sh:355-360 把 fabric 的 --phase cases 路由到 bash 原生跑器，
#   而那个跑器 eval_js/screenshot 恒 BLOCKED（fabric.sh:711-757）⇒ 用例在第一条 eval_js 就 BLOCKED，0 条断言可判。
#   直接调 pwsh mt_case.ps1 才走 autojs 通道；`run --case` 不受 --allow-auto 闸门限制。
# 风险 1：需要客户端存活（needsClient 自动判定）；客户端不在跑 ⇒ ERROR/BLOCKED，不是 PASS
# 风险 2：2 条 vision 断言按设计是 DELEGATED（VISION_PATH=… 交视觉模型），不会自动 PASS/FAIL
# 风险 3：jar 断言需要第 4 步产物，否则 BLOCKED
pwsh -NoProfile -File scripts/test/mt_case.ps1 run --case scripts/test/cases/RELEASE150-fabric-1.20.1.json --version fabric-1.20.1 --case-timeout 600
```

判定口径：`MT_CASE` 汇总里 PASS=25 + DELEGATED=2（vision）＝本轮机器判据全绿；发生 FAIL/ERROR/TIMEOUT 才是真问题；BLOCKED 表示判据没跑起来（≠通过）。

## 8. 停止（**禁用** `--version 1.21.1`）

```bash
# 用途：按命令行匹配杀 fabric 的 java 进程 + 删 pidfile + 删 .mt_autojs_injected
# 风险 1（严重）：scripts/test/win/lib/common.sh:252-272 的 mt_cmdline_matches_version 对 fabric 进程**无版本守卫**，
#   任何带 :fabric-1.20.1:runClient 的命令行都会匹配 *fabric-1.20.1*run*client* ⇒ 用 --version 1.21.1 会命中它。
#   而 mt_rows_for_version 按 1.21.1 优先枚举 ⇒ fabric 进程会被标成 1.21.1 线。**禁止** stop.sh --version 1.21.1。
# 风险 2：不加 --purge-saves（它会按白名单批量删 saves/*），改为第 9 步手工删单个副本
bash scripts/test/win/stop.sh --version fabric-1.20.1
```

## 9. 手工清 testworld 副本（替代 --purge-saves）

```bash
# 用途：只删这一份可丢弃的世界副本，不碰 mods/config/logs
# 风险：路径必须精确到 testworld；**不要** rm -rf saves
rm -rf fabric-1.20.1/run/client/saves/testworld
```

（现况：`fabric-1.20.1/run/client/saves/` 下只有 `testworld` 一项，2026-11-02T03:29 实测。）

## 10. 收尾核对

```bash
# 用途 A：三端口回到 free
pwsh -NoProfile -ExecutionPolicy Bypass -File work/_probe/ports.ps1

# 用途 B：stop 的只读预演，期望 would-kill 0 条
bash scripts/test/win/stop.sh --version fabric-1.20.1 --dry-run
```

同时确认 `fabric-1.20.1/run/client/.mt_autojs_injected` 已不存在（stop.sh 的 2c 块会删）。

## 附：三条最容易踩的坑

1. **不要**为了让通道“看起来生效”而把 jar 丢进 mods —— 违反 `TESTING-RULES-OVERVIEW.md:191` 规则 6，且 intermediary jar 在 named 运行期会 `NoSuchMethodError`/类错位。
2. **不要**用 `mt.sh --phase cases` 判 fabric —— 那条路对 eval_js/screenshot 恒 BLOCKED（历史上会被读成“跑过了”）。
3. **不要**用 `stop.sh --version 1.21.1` 停 fabric —— matcher bug 会顺带命中 fabric 进程并误标线。
