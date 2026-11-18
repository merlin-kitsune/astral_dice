# 测试台判据修复记录（1.5.0 四线实机验收前置）

- 时间：2026-11-02（本机时区 +08:00）
- 仓库：F:\MCProject\astral_dice_multiloader，HEAD=fd990c15（工作树含前序未提交改动，本批不回退）
- 改动面：仅 scripts/test/**；未触碰模组源码 / lang / build.gradle / gradle.properties / .github / run/**
- 未启动任何游戏、未跑 gradle（26.1.2 实机用例仍在跑，25565/25585 被占用）
- 备份：每个被改文件先留 .bak-judgement-20261102-034500（见 §7）
- 原则：修判据 = 加严。本批没有降低任何阈值（唯一新增的数值门 20480 B 是新增下限），也没有删除任何断言
  —— 用例断言数与正则由 work/_probe/asserts_unchanged.ps1 逐条核对（§4.9）。

## 0. 结论表

| # | 缺陷 | 状态 | 落点 |
|---|---|---|---|
| 1 | op:screenshot 在 KubeJS 下失效（java() 已移除） | 已修（离线可证无 java( 调用；跨线探测六档） | scripts/test/lib/Mt.Channel.psm1:761-873 / 875-1031 |
| 2 | vision 无时效/大小校验，旧图可判 PASS | 已修（四道硬门） | scripts/test/lib/Mt.Paths.psm1:345-394；scripts/test/mt_case.ps1:1940-2008 |
| 3 | 截图 op 失败被静默吞掉 | 已修（失败登记 → vision BLOCKED） | scripts/test/mt_case.ps1:191-215、2187、2192、2212、1955-1959 |
| 4 | log 断言命中 KubeJS 回显源码行 | 已修（逐物理行 + 强/弱三分类） | scripts/test/mt_assert.ps1:405-448、450-480、643-717 |
| 5 | crash 断言在目录不存在时直接 PASS | 已修（INCONCLUSIVE） | scripts/test/mt_assert.ps1:786-796；scripts/test/mt_case.ps1:1786-1788 |
| 6 | recipes 只数复数目录 ⇒ neo 线恒 0 | 已修（两目录都数） | scripts/test/mt_case.ps1:1700-1701、1716-1719 |
| 7 | 用例 note 过期 | 已修（两处按实测改写） | scripts/test/cases/RELEASE150-1.20.1.json:22；RELEASE150-1.21.1.json:22 |

未修 / 残留项见 §5（其中 tools/lp_offline_assert.ps1 的语义分叉不在本批 7 项内，已如实标注）。

## 1. 贯穿性改动（多需求共用）

### 1.1 截图失败登记表（需求 3 的载体）

scripts/test/mt_case.ps1:191-215 新增 Register-MtCaseShotFailure：把「本用例内某 tag 没有产出有效证据」按 tag
记进 $script:CaseShotFailures。作用域是单条用例：mt_case.ps1:2944 在每条用例开头
`$script:CaseShotFailures = @{}`（紧邻窗口起点赋值），所以不存在跨用例串味。

### 1.2 用例证据时间基准（需求 2 的载体）

scripts/test/mt_case.ps1:2939-2947（赋值在 :2945）：

    $script:CaseShotFailures = @{}
    $script:CaseWindowStartUnix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0
    Write-MtInfo ("CASE_EVIDENCE_WINDOW: {0} 截图证据窗口起点 {1}" -f $caseId, ...)

取在「本用例窗口快照」之前，因此本用例内任何写文件都必然 ≥ 它；显式不采用 .mt_active_run 的 run_id 还原
（active_run 粘性、跨用例不轮换，秒级时间戳与用例起点可差很远 —— 这正是 D2 的机制）。

## 2. 逐项：改前 / 改后 / 理由

### 需求 1：op:screenshot 不再依赖 java()

改前（scripts/test/lib/Mt.Channel.psm1.bak-judgement-20261102-034500:760 起，JS 在 :811-834）：

    var F, S, Ck;
    try {
      F = Java.loadClass('java.io.File');
      S = Java.loadClass('net.minecraft.client.Screenshot');
      Ck = Java.loadClass('java.util.function.Consumer');
    } catch (eL) {
      F = java.io.File; S = net.minecraft.client.Screenshot; Ck = java.util.function.Consumer;
    }
    var work = new F(__MT_WORK__);
    ...
    var rt = null;
    try { rt = Client.getMainRenderTarget(); } catch (e0) { rt = null; }

实机后果（1.20.1 本轮 stdout 原文）：
MT_CHANNEL op=shot channel=delegated status=delegated detail=<tag> — Screenshot.grab 未产出 <tag>.png：LegacyError: Line 4: java() is no longer supported! Read more on wiki: https://kubejs.com/kjs6
⇒ 不产 PNG、不登记 .mt_shots.json，vision 断言无任何证据面。

改后（scripts/test/lib/Mt.Channel.psm1:761-873 + 875-1031）：

1) 新增 Get-MtChannelShotJs（:761），生成的脚本只用 Java.loadClass(全限定名)；:870-873 校验 -NameJsLiteral
   必须是带引号的 JS 字符串字面量并做占位替换：

        if ($NameJsLiteral -notmatch '^([''""])[\s\S]*\1$') { throw ... }
        return ($js.Replace('__MT_TIER__', [string]$Tier).Replace('__MT_NAME__', $NameJsLiteral))

   （实际源码 :870 是单引号串内成对双写：'^([''"])[\s\S]*\1$'；上面为 Markdown 可读化写法。）

   JS 内关键取法（不再 new java.io.File，也不需要裸 java 包名）：

        var mc = null; try { if (typeof Client !== 'undefined' && Client !== null) { mc = Client; } } catch (e0) {}
        // 取不到再用 Java.loadClass('net.minecraft.client.Minecraft') 的 getInstance / m_91087_
        var work = pick(mc, ['gameDirectory','f_91069_','getGameDirectory']);
        var rt   = pick(mc, ['mainRenderTarget','f_91042_','getMainRenderTarget','m_91385_']);
        var consumer = new JavaAdapter(Java.loadClass('java.util.function.Consumer'), { accept: function (x) {} });
        var S = Java.loadClass('net.minecraft.client.Screenshot');
        if (!hasFn(S, 'grab')) { out.err = 'Screenshot.grab 不存在'; return out; }

2) 六档签名（:938-945）先探测存在再调用，绝不猜：

    T1 grab(File,String,RenderTarget,Consumer)          // 1.21.1/26.1.2 Mojmap 命名
    T2 grab(File,String,RenderTarget,int,Consumer)      // 26.x 带 downscale
    T3 m_92289_(File,RenderTarget,Consumer)             // 1.20.1 SRG 匿名（实机已打通）
    T4 grab(File,RenderTarget,Consumer)                 // Mojmap 匿名
    T5 m_92295_(File,String,RenderTarget,Consumer)      // 1.20.1 SRG 命名（调用成功但不落文件）
    T6 grab(File)                                       // 最简档

3) 成功判据是落盘而不是返回值（:946-1013）：先快照 @($shotDir,$workDir) 里既有 PNG，eval 后最多轮询 4 s，
   只认「eval 前不存在且 mtime ≥ eval 前 now-2 s」的文件，命中后 Move-Item 到 <tag>.png，再

        [void](Add-MtShot -Paths $paths -FilePath $targetFile -Tag $tagText -RunId $runId -Mode $mode -Path $targetFile)   // :1011

   并写 MT_CHANNEL ok 行（:1017）detail=file=… mode=… bytes=… tier=T<n> sig=<签名>。T5 这种「返回成功但不落文件」
   的档位会被轮询自然证伪（:998 记 called_no_file）。

4) 清单条目补取证字段（scripts/test/lib/Mt.Paths.psm1:319-336）：Add-MtShot 增 -Mode/-Path，每条写成
   ordered@{file;tag;mode;path;ts}，ts = 登记时刻（unix 秒）。注释明确：ts 是登记时刻，不得当新鲜度依据
   （新鲜度一律看 PNG 自身 LastWriteTime）。

理由：java() 是 Rhino/KubeJS 老式调用形式，KubeJS 6 已移除（LegacyError 即其专用报错），所以任何 java.
前缀写法都不可能跨线工作；Java.loadClass 是唯一跨线形式。1.20.1 无 grab(File)，必须走 SRG m_92289_；
1.21.1/26.1.2 是 Mojmap 且不同小版本签名有差异 ⇒ 六档探测 + 落盘验证比写死单签名稳。同时把「调用成功」
与「真的落盘」解耦（m_92295_ 的实测教训）。

### 需求 2：vision 断言必须有本轮强证据

改前（Mt.Paths.psm1.bak…:338-352 / mt_case.ps1.bak…:1898-1935）：Get-MtCurrentShots 只有 -Paths/-RunId；run_id
粘性命中时只按文件名去重 + 文件存在即返回，不看 mtime；vision 只比对「清单里有这个 tag」+「文件在」，末行直接

    return (New-MtPair 'DELEGATED' ("VISION_PATH={0} | {1}" -f (Join-Path $Paths.shot_dir $target), $question))

⇒ 上一轮旧图只要还在 screenshots/ 且清单条目没被清，就能让本轮 vision 过（D2/S6；1.20.1 本轮实测两张图
mtime 03:24:47 / 03:22:17 早于窗口起点 03:28:22 仍被判通过）。

改后：

(a) Mt.Paths.psm1:345-394 —— Get-MtCurrentShots 增 [double]$SinceUnix = 0（:362），粘性分支逐条过滤（:379-380）：

        if ($SinceUnix -gt 0 -and ($item.LastWriteTime.ToFileTime() / 10000000.0 - 11644473600) -lt $SinceUnix) { continue }

    回落分支把起点抬高（:389）：if ($SinceUnix -gt 0 -and $SinceUnix -gt $start) { $start = $SinceUnix }。
    默认 0 = 旧行为是硬约束：mt_capture.ps1 的 prune 用同一函数算 keepSet，默认收紧会扩大删除面。

(b) mt_case.ps1:1940-2008 —— vision 现在是四道硬门（任一不过即 BLOCKED，不放行成通过）：
    ① 该 tag 无截图失败登记（:1955-1959）；
    ② 传入 -SinceUnix 的当代清单里按 basename（去扩展名、大小写不敏感）匹配（:1963-1987）；
    ③ 文件存在（:1992-1994）；
    ④ 大小 ≥ 20480 B（:1996-1998）+ mtime ≥ $script:CaseWindowStartUnix（:1999-2005）：

        if ([long]$shotInfo.Length -lt 20480) { return (New-MtPair 'BLOCKED' ("截图文件过小（{0} B < 20480 B，视为无效/空帧）：{1}" -f ...)) }
        if ($shotUnix -lt $script:CaseWindowStartUnix) { return (New-MtPair 'BLOCKED' ("截图不是本轮产物（mtime={0:yyyy-MM-dd HH:mm:ss.fff} 早于用例窗口起点 {1}）：{2}" -f ...)) }

理由：vision 是 DELEGATED 类断言（交给视觉判定），前置取证必须先由机器证明「这张图是本轮新产出的、且不是
空帧」。20 KB 阈值对应「空帧/纯背景基本 < 20 KB」的经验下界；这是新增下限，不涉及任何既有阈值。

### 需求 3：截图 op 失败不得静默通过

改前（mt_case.ps1.bak…:2099-2111）：autojs 通道只在 catch 里 New-MtPair 'ERROR'；native 通道失败只返回
ERROR/TIMEOUT —— 没有任何「该 tag 本轮没证据」的登记，vision 仍可命中旧图。

改后（mt_case.ps1:2172-2213）：两条通道失败都登记，tag 提前解析（:2178）：

    $pairShot = ConvertTo-MtCaseChannelPair -Result $rShot -Label 'screenshot'
    if ([string]$pairShot[0] -ne 'PASS') { Register-MtCaseShotFailure -Tag $shotTag -Reason ([string]$pairShot[1]) }   // :2186-2187
    } catch { $exMsg = 'MT_CHANNEL 调用失败：' + $_.Exception.Message
              Register-MtCaseShotFailure -Tag $shotTag -Reason $exMsg; ... }                                        // :2191-2192
    if ($oc -ne 'PASS') { Register-MtCaseShotFailure -Tag $shotTag -Reason ([string]$tail) }                          // :2212

理由：截图 op 的返回值与 vision 断言是两条独立判据链，中间必须有「失败事实」的传递通道，否则失败只体现在
op 那一行，vision 仍拿旧图判通过（真实发生在 1.20.1 本轮）。

### 需求 4：log 断言剔除回显源码行

改前（mt_assert.ps1.bak…:594-600）：

    $hits = $pat.Matches($text).Count
    if ($hits -gt 0) { Write-MtLine "MT_ASSERT_LOG: PASS — /$PatternText/ 命中 $hits 次（$label）" ... }

对整段增量文本求值 ⇒ ① 跨行/重叠命中糊成一个数，无法取证；② KubeJS 把 eval 源码整行回显，源码本身命中
断言（实测 1.21.1 #12/#13、1.20.1 #12 各 2 次命中而真证据 1 条）。

改后：

(a) mt_assert.ps1:405-430 Test-MtPlatformLine —— 强证据形状 = 行首 [时间戳] + [线程/LEVEL] + 可选 [来源] + 冒号：

        return [regex]::IsMatch($Line, '^\uFEFF?\[[^\]]+\]\s*\[[^\]]+/[A-Za-z]+\](\s*\[[^\]]*\])?\s*:')

(b) mt_assert.ps1:432-448 Test-MtEvalSourceEcho —— 第三类伪证据（平台行形状但载荷是 eval 源码）：

        return [regex]::IsMatch($Line, '\[AutoJS/Eval/\]\s*:.*JSON\.stringify\(\(')

    实测依据：
    [0211月2026 03:04:09.692] [AutoJS-Mcp/INFO] [AutoJS/Eval/]: [client] JSON.stringify(((function(args){ ... console.info('[REL150] screen_closed=1') ...
    行首完全是标准平台行，载荷却是脚本正文；而真证据是
    [0211月2026 03:04:08.057] [Server thread/INFO] [KubeJS Server/]: autojs-eval#23: [REL150] ver=1.21.1 ...（载荷是运行期值）。

(c) mt_assert.ps1:450-480 Show-MtHitLines —— 打印逐行命中明细 L<行号> [platform|platform-source-echo|echo] <前160字符>。

(d) mt_assert.ps1:643-717 Invoke-MtAssertLog —— 逐物理行匹配 + 三分类 + 只有平台行计强证据：

    foreach ($rawLine in @($text -split "`n")) { ... if (-not $pat.IsMatch($line)) { continue }
        $isSourceEcho = Test-MtEvalSourceEcho -Line $line
        $isStrong = (-not $isSourceEcho) -and (Test-MtPlatformLine -Line $line)
        $kind = if ($isStrong) { 'platform' } elseif ($isSourceEcho) { 'platform-source-echo' } else { 'echo' } ... }
    if ($hits -gt 0 -and $strongHits -gt 0) { PASS — 命中 N 行（平台输出行 M 行） }
    if ($hits -gt 0) { FAIL(SOURCE_ECHO) — 命中 N 行但无一是平台输出行 ... ; return 1 }

理由：断言要证明的是「运行期真的发生了」，不是「脚本里写了这段」。因此强证据必须是日志框架真的写出来的行；
回显/续行/平台行内的源码载荷一律不计。加严方向：以前「2 次命中 ⇒ PASS」，现在「1 条真证据 + 1 条回显 ⇒ PASS
且明细可查」，「只有回显 ⇒ FAIL(SOURCE_ECHO) exit 1」。

### 需求 5：crash 断言的弱证据

改前（mt_assert.ps1.bak…:679-682）：

    if (-not (Test-Path -LiteralPath $p.crash_dir -PathType Container)) {
        Write-MtLine 'MT_ASSERT_CRASH: PASS — 无 crash-reports 目录'
        return 0
    }

改后（mt_assert.ps1:786-796）：

    Write-MtLine ('MT_ASSERT_CRASH: INCONCLUSIVE — crash-reports 目录不存在（{0}）' -f $p.crash_dir)
    Write-MtLine '    「目录不存在」≠「目录存在且无新增」：本轮从未建立崩溃报告目录 ⇒ 无法断言无崩溃（不记 PASS）。'
    Write-MtWarn 'MT_ASSERT_INCONCLUSIVE: WARN — crash-reports 目录不存在 ⇒ 本条记为不可判（不计 PASS / 不计 FAIL），需人工确认该线是否会写 crash-reports'
    return 0

配套：mt_case.ps1:1783-1788 在子进程 stdout 里识别 MT_ASSERT_CRASH: INCONCLUSIVE 行，映射成步骤状态
INCONCLUSIVE（不再被 Get-MtVerdict 归成 PASS）。「目录存在且无新增」仍走原 PASS 行（:804）。

理由：1.21.1 整轮 run/1.21.1/crash-reports 从不生成，旧口径把「取证面缺失」读成「没有崩溃」，本质是空集
判据。改成 INCONCLUSIVE 后既不诬告也无假绿。

### 需求 6：recipes 计数口径

改前（mt_case.ps1.bak…:1685）：

    if ($t.StartsWith('data/astral_dice/recipes/') -and $t.EndsWith('.json')) { $recipes++ }

改后（mt_case.ps1:1716-1719，NOTES 同步在 :1700-1701）：

    # 口径修复 2026-11-02：1.20.1（Forge）用 data/<ns>/recipes/（复数），1.21+ 改成
    # data/<ns>/recipe/（单数）——旧实现只数复数目录，于是 neo 三线 recipes 恒为 0（D6）。
    # 两个目录都数：不同加载器/版本各自的目录布局都覆盖到，阈值本身不放宽。
    if ($t.EndsWith('.json') -and ($t.StartsWith('data/astral_dice/recipes/') -or $t.StartsWith('data/astral_dice/recipe/'))) { $recipes++ }

理由：实测 1.21.1 jar 内是 data/astral_dice/recipe/（单数，125 个）⇒ 旧口径恒 0，等于该断言对 neo 线空转。
两目录相加不改变阈值语义（阈值仍是「≥ N 条配方」），只是把分母改对。

### 需求 7：用例 note 订正

- scripts/test/cases/RELEASE150-1.20.1.json:22：旧 note 称版本行在 latest.log:144、版本 1.5.0-rc.7；
  实测 :144 = [0211月2026 03:13:18.960] [Render thread/INFO] [Arc3D/]: Use NV_conservative_raster（驱动行），
  :375 = [0211月2026 03:13:21.630] [modloading-worker-0/INFO] [astral_dice-version-gate/]: [Astral Dice] 版本互通门槛(Version Gate):mod_version=1.5.0+forge_1.20.1 → 网络互通号=1.5，
  全日志无 rc.7。新 note 写明 :375 与 1.5.0+forge_1.20.1，并补 1.20.1 的光标定位实现（InventoryScreen 反射读
  父类 leftPos/topPos，取 lx+17 / ty+151，退到屏幕中心，反射写 MouseHandler xpos/ypos 后 scr.mouseMoved）。
- scripts/test/cases/RELEASE150-1.21.1.json:22：新 note 写明 step 18 实际是 InventoryScreen 打开后遍历
  scr.getMenu().slots 找持有 astral_dice:abyss_reagent_chip 的槽位，原点 lx=floor((w-195)/2)/ty=floor((h-136)/2)，
  GUI 坐标 = 原点 + 槽位 x/y + 8，再乘 getGuiScale() —— 不是按生存栏布局硬算坐标。

理由：note 是判据的说明书；说明书与实现分叉会让下一轮复跑的人按错行号/错算法核对，进而误判。只改 note，
不动断言（§4.9 逐条证明）。

## 3. 改动清单（文件 × 行号）

    scripts/test/lib/Mt.Channel.psm1        938 → 1077 行（+139）
      +761-873   Get-MtChannelShotJs（六档 JS 生成 + -NameJsLiteral 校验）
      ~875-1031  Invoke-MtChannelShot（逐档驱动 + 落盘轮询 + Add-MtShot -Mode/-Path + detail 取证）
      ~885       文档：三档 → 六档签名
    scripts/test/lib/Mt.Paths.psm1          750 → 767 行（+17）
      ~308-343   Add-MtShot（+ -Mode / -Path，条目加 mode/path/ts，注释说明 ts 语义）
      ~345-394   Get-MtCurrentShots（+ -SinceUnix，粘性分支 mtime 过滤，回落分支抬高起点）
    scripts/test/mt_assert.ps1             1372 → 1486 行（+114）
      +405-448   Test-MtPlatformLine / Test-MtEvalSourceEcho
      +450-480   Show-MtHitLines
      ~643-717   Invoke-MtAssertLog（逐物理行 + 三分类 + FAIL(SOURCE_ECHO)）
      ~775-806   Invoke-MtAssertCrash（目录不存在 → INCONCLUSIVE）
    scripts/test/mt_case.ps1               3233 → 3332 行（+99）
      +191-215   Register-MtCaseShotFailure
      +258-263   CaseWindowStartUnix / CaseShotFailures 的声明与说明
      ~1700-1719 recipes 口径（NOTES + 两目录）
      ~1783-1788 crash INCONCLUSIVE 映射
      ~1940-2008 vision 四道硬门
      ~2172-2213 screenshot op 失败登记（三条路径）
      +2939-2947 用例证据窗口起点（:2945 赋值）
    scripts/test/cases/RELEASE150-1.20.1.json   :22 note 重写
    scripts/test/cases/RELEASE150-1.21.1.json   :22 note 重写

判据面零变化：两用例 asserts 各 27 条，type|pattern|window|source 逐条一致；steps 各 31 条不变（§4.9）。

## 4. 离线验证（输出原文）

### 4.1 语法（work/_probe/parse_check.ps1）

    PARSE_OK scripts/test/mt_assert.ps1
    PARSE_OK scripts/test/mt_case.ps1
    PARSE_OK scripts/test/lib/Mt.Paths.psm1
    PARSE_OK scripts/test/lib/Mt.Channel.psm1
    exit=0

### 4.2 截图 JS 静态断言 + 变异测试（work/_probe/shotjs_check.ps1）

检测两条正则（先剥离 JS 字符串字面量，避免误杀提示文本）：java 裸调用 (?<![A-Za-z0-9_.$])java\s*\(、
java 包访问器 (?<![A-Za-z0-9_.$])java\.[A-Za-z_]。逐档生成并检查：

    T1 sig=grab(File,String,RenderTarget,Consumer) bytes=4253 java_call=0 java_pkg=0 TIER_NAME=True loadClass=True hasFn=True => PASS
    T2 sig=grab(File,String,RenderTarget,int,Consumer) bytes=4253 java_call=0 java_pkg=0 TIER_NAME=True loadClass=True hasFn=True => PASS
    T3 sig=m_92289_(File,RenderTarget,Consumer) bytes=4253 java_call=0 java_pkg=0 TIER_NAME=True loadClass=True hasFn=True => PASS
    T4 sig=grab(File,RenderTarget,Consumer) bytes=4253 java_call=0 java_pkg=0 TIER_NAME=True loadClass=True hasFn=True => PASS
    T5 sig=m_92295_(File,String,RenderTarget,Consumer) bytes=4253 java_call=0 java_pkg=0 TIER_NAME=True loadClass=True hasFn=True => PASS
    T6 sig=grab(File) bytes=4253 java_call=0 java_pkg=0 TIER_NAME=True loadClass=True hasFn=True => PASS
    MUTATION java() -> call=1 pkg=0
    MUTATION java.io.File -> call=0 pkg=1
    MUTATION: PASS（两种旧写法都能被抓到）
    MUTATION_OLD backup java_pkg_accessor_lines=3 hits=3
      legacy: var dir = new java.io.File(__MT_DIR__);
      legacy: try { consumer = new JavaAdapter(java.util.function.Consumer, { accept: function (x) { } }); } catch (e1) { consumer = null; }
      legacy: var f = new java.io.File(dir, name);
    SOURCE Mt.Channel.psm1 code_bare_java_calls=0
    TIER_DISTINCT=6/6
    NEGATIVE 裸名被拒绝 => -NameJsLiteral 必须是带引号的 JS 字符串字面量（例：'a.png'）；收到：rel150_probe.png
    RESULT: PASS (fail=0)   exit=0

（六档 JS 同时落 work/_probe/out/shotjs-T{1..6}.js，每份 4253 B，仅 var TIER = n; 不同；变异测试证明检测器
非空转：旧备份里的 3 行 java 包访问器全部被抓出。）

### 4.3 四用例 validate（改动后）

    MT_VALIDATE: OK — RELEASE150-1.20.1
    MT_VALIDATE: OK — RELEASE150-1.21.1
    MT_VALIDATE: OK — RELEASE150-26.1.2
    MT_VALIDATE: OK — RELEASE150-fabric-1.20.1
    exit=0

### 4.4 通道五态（work/_probe/verify-channel.ps1）

    mods_dir=F:\MCProject\astral_dice_multiloader\fabric-1.20.1\run\client\mods
    A（无状态文件）     mode=autojs deployed=False command=delegated key=delegated mouse=delegated shot=delegated reload=delegated
    B（写入 autojs=1）  deployed=True command=autojs key=autojs mouse=autojs shot=autojs reload=autojs
    C（autojs=0）       deployed=False 五面全 delegated
    D（删除状态文件）   deployed=False 五面全 delegated
    E（对照 1.21.1）    deployed=True  五面全 autojs

与 fabric 通道修复后的预期一致（fabric 走 -PautojsTestMod=1 注入、mods/ 无 jar 属正常）。

### 4.5 vision 硬门离线实测（work/_probe/vision_gate_check.ps1）

做法：用标记把 mt_case.ps1 里 vision 块的真实源码原文切出来执行（不重写逻辑），真品 Import-Module
lib/Mt.Paths.psm1；real 模式走真 Get-MtCurrentShots -SinceUnix，stub 模式 stub 掉过期过滤以专门打硬门自身。

    == vision gate check mode=real windowStart=2026-11-02 04:03:17.455
    PASS [real] image=fresh expect=DELEGATED got=DELEGATED
          detail=VISION_PATH=F:\MCProject\astral_dice_multiloader\work\_probe\tmp_vision\fresh.png | 请判定截图中的视觉断言是否成立
    PASS [real] image=stale expect=BLOCKED got=BLOCKED
          detail=截图 'stale' 不属于当前世代 (世代内: ['broken.png', 'fresh.png', 'tiny.png'], tag 表: ['broken', 'fresh', 'ghost', 'stale', 'tiny'])
    PASS [real] image=tiny expect=BLOCKED got=BLOCKED
          detail=截图文件过小（1000 B < 20480 B，视为无效/空帧）：F:\MCProject\astral_dice_multiloader\work\_probe\tmp_vision\tiny.png
    PASS [real] image=ghost expect=BLOCKED got=BLOCKED
          detail=截图 'ghost' 不属于当前世代 (世代内: ['broken.png', 'fresh.png', 'tiny.png'], tag 表: ['broken', 'fresh', 'ghost', 'stale', 'tiny'])
    PASS [real] image=broken expect=BLOCKED got=BLOCKED
          detail=截图 'broken' 本轮未产出证据：channel=autojs status=DELEGATED 各档签名均未产出 broken.png
    RESULT=PASS mode=real 5/5
    == vision gate check mode=stub windowStart=2026-11-02 04:03:20.516
    PASS [stub] image=fresh expect=DELEGATED got=DELEGATED
    PASS [stub] image=stale expect=BLOCKED got=BLOCKED
          detail=截图不是本轮产物（mtime=2026-11-02 02:03:25.536 早于用例窗口起点 2026-11-02 04:03:20.516）：F:\MCProject\astral_dice_multiloader\work\_probe\tmp_vision\stale.png
    PASS [stub] image=tiny expect=BLOCKED got=BLOCKED
    PASS [stub] image=ghost expect=BLOCKED got=BLOCKED
          detail=截图文件不存在：F:\MCProject\astral_dice_multiloader\work\_probe\tmp_vision\ghost.png
    PASS [stub] image=broken expect=BLOCKED got=BLOCKED
    RESULT=PASS mode=stub 5/5

（首版探针两次 FAIL 全因探针自己把窗口起点设在写图之后 ⇒ fresh 被时代门拦；已修，记录在此以免误读。）

### 4.6 平台行/回显识别器单测（work/_probe/platform_line_check.ps1）

用 PowerShell AST 从 mt_assert.ps1 取 Test-MtPlatformLine 与 Test-MtEvalSourceEcho 的定义原文执行，10 条实测行：

    PASS [1.21.1 真证据（KubeJS 通道带时间戳）] expect=True got=True category=platform :: [0211月2026 03:05:20.828] [Render thread/INFO] [KubeJS Client/]: autojs-eval#31: [REL
    PASS [1.20.1 版本互通门槛行] expect=True got=True category=platform :: [0211月2026 03:13:21.630] [modloading-worker-0/INFO] [astral_dice-version-gate/]: [As
    PASS [无 mod 来源段的平台行] expect=True got=True category=platform :: [03:05:19] [Server thread/INFO]: Done (12.3s)! For help, type help
    PASS [无来源段的平台行] expect=True got=True category=platform :: [0211月2026 03:05:19.000] [Render thread/WARN]: something
    PASS [KubeJS 回显源码行（两空格缩进）] expect=False got=False category=echo ::   console.info('[REL150] guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text
    PASS [模组清单续行（Tab 缩进）] expect=False got=False category=echo :: 		Astral Dice 1.5.0+neoforge_1.21.1 (astral_dice)
    PASS [异常栈帧] expect=False got=False category=echo :: 	at net.minecraft.client.Minecraft.run(Minecraft.java:1000)
    PASS [回显的脚本变量行] expect=False got=False category=echo ::   var mc = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
    PASS [AutoJS-Mcp eval 源码载荷（平台行形状）] expect=False got=False category=platform-source-echo :: [0211月2026 03:04:09.692] [AutoJS-Mcp/INFO] [AutoJS/Eval/]: [client] JSON.stringify((
    PASS [KubeJS 执行结果行（真证据）] expect=True got=True category=platform :: [0211月2026 03:04:08.057] [Server thread/INFO] [KubeJS Server/]: autojs-eval#23: [REL
    RESULT=PASS platform-line 10/10

### 4.7 真实日志端到端（只读 run/<ver>/logs/latest.log，不启动游戏）

    # A1：run/1.21.1，--window whole，既有断言原样跑（源码回显 vs 运行期值同在一份日志里）
    MT_WARN: 窗口 = whole：对全文件求值（可能被历史标记污染）
    MT_ASSERT_LOG: PASS — /\[REL150\] inventory open=1/ 命中 4 行（平台输出行 2 行）（latest:latest.log@0B(win=whole)）
        L3839 [echo] console.info('[REL150] inventory open=1 hover=' + out.guiX + ',' + out.guiY + ' slot=' + out.slot + ' win=' + out.winX + ',' + out.winY);
        L3849 [platform] [0211月2026 03:04:08.344] [Render thread/INFO] [KubeJS Client/]: autojs-eval#25: [REL150] inventory open=1 hover=399,322 slot=45 win=798,644
        L7785 [echo] console.info('[REL150] inventory open=1 hover=' + out.guiX + ',' + out.guiY + ' slot=' + out.slot + ' win=' + out.winX + ',' + out.winY);
        L7795 [platform] [0211月2026 03:05:20.888] [Render thread/INFO] [KubeJS Client/]: autojs-eval#25: [REL150] inventory open=1 hover=399,322 slot=45 win=798,644
    exit 0

    # A2：run/1.20.1，只要求运行期值存在（回显行里同样含这串字面量，口径分开计数）
    MT_WARN: 窗口 = whole：对全文件求值（可能被历史标记污染）
    MT_ASSERT_LOG: PASS — /\[REL150\] screen_closed=1/ 命中 10 行（平台输出行 5 行）（latest:latest.log@0B(win=whole)）
        L30977 [platform-source-echo] [0211月2026 03:25:16.709] [AutoJS-Mcp/INFO] [AutoJS/Eval/]: [client] JSON.stringify(((function(args){ return ((function () { var mc = Java.loadClass('net.minecra
        L30978 [platform] [0211月2026 03:25:16.723] [Render thread/INFO] [KubeJS Client/]: autojs-eval#1: [REL150] screen_closed=1
        ...（其余 8 行同形：L5171/L5172、L34468/L34469、L37825/L37826、L40640/L40641）
    exit 0

    # A3：run/1.20.1，把断言写成「源码里的 console.info(...) 字样」（这正是旧口径会被骗的写法）
    MT_WARN: 窗口 = whole：对全文件求值（可能被历史标记污染）
    MT_ASSERT_LOG: FAIL(SOURCE_ECHO) — /console\.info\(.\[REL150\] screen_closed/ 命中 5 行但无一是平台输出行（回显/续行/平台行内的 eval 源码载荷都不构成证据）（latest:latest.log@0B(win=whole)）
        L5171 [platform-source-echo] [0211月2026 03:16:32.918] [AutoJS-Mcp/INFO] [AutoJS/Eval/]: [client] JSON.stringify(((function(args){ return ((function () { var mc = Java.loadClass('net.minecra
        L30977 [platform-source-echo] ...（另 L34468 / L37825 / L40640 同形）
    exit 1

即需求 4 的三个实测缺口都被堵住：命中次数不再是「整段 Matches 计数」而是逐物理行；回显行（echo）与平台行内载荷
（platform-source-echo）都不算强证据；「只有回显」不可能 PASS。A3 若用旧实现（整段 Matches().Count）会因 5 次命中直接 PASS。

### 4.8 crash 断言三线实测

    $ pwsh mt_assert.ps1 crash --version 1.21.1
    MT_ASSERT_CRASH: INCONCLUSIVE — crash-reports 目录不存在（F:\MCProject\astral_dice_multiloader\run\1.21.1\crash-reports）
        「目录不存在」≠「目录存在且无新增」：本轮从未建立崩溃报告目录 ⇒ 无法断言无崩溃（不记 PASS）。
    MT_WARN: MT_ASSERT_INCONCLUSIVE: WARN — ...
    exit 0

    $ pwsh mt_assert.ps1 crash --version 1.20.1
    MT_ASSERT_CRASH: PASS — 本次运行无新增崩溃报告（基线 5 个）
    exit 0

    $ pwsh mt_assert.ps1 crash --version 26.1.2
    MT_ASSERT_CRASH: PASS — 本次运行无新增崩溃报告（基线 0 个）
    exit 0

⇒ 「目录存在且无新增」这条路仍工作（1.20.1 / 26.1.2），只有 1.21.1 变成 INCONCLUSIVE。

### 4.9 断言零删除 / 判据未放宽（work/_probe/asserts_unchanged.ps1）

逐条比较 .bak-judgement-20261102-034500 与当前用例的 type|pattern|window|source：

    RELEASE150-1.20.1: asserts bak=27 cur=27 count_equal=True changed=0
      steps bak=31 cur=31
    RELEASE150-1.21.1: asserts bak=27 cur=27 count_equal=True changed=0
      steps bak=31 cur=31
    RESULT: PASS (fail=0)   exit=0

### 4.10 记录一个会炸取证的 PowerShell 坑（已修 + 已加注释）

对 System.Collections.Generic.List[object] 用 @($list) 会抛 Argument types do not match（PowerShell 7.6.9 实测，
work/_probe/listwrap_check.ps1：@($l) → ERR；@($l.ToArray()) / @($l | %{$_}) → ok）。因此 Show-MtHitLines 的两个
调用点必须写 -Hits ($hitLines.ToArray())；另外不要写 $h.PSObject.Properties['Kind'] -and [string]$h.Kind
（PSPropertyInfo 参与 -and 同样抛错），已改成两步判断（mt_assert.ps1:469-476）。

## 5. 未修项 / 残留风险（如实记录）

1) scripts/test/tools/lp_offline_assert.ps1:30-33 语义未与 mt_assert 对齐：仍用 [regex]::IsMatch($text, pattern)
   对整份日志判定，既不按本轮窗口截取、也不区分平台行/回显行。后果：它可能对「只命中回显」的断言报 PASS，与
   修好的 mt_assert 分叉。不在本批 7 项内（属审计 R1-R10 之外的旁路工具），未改；建议下批复用
   Test-MtPlatformLine / Test-MtEvalSourceEcho 与 Read-MtLogDelta 的窗口语义。
2) 1.21.1 / 26.1.2 的 Mojmap 截图签名无法离线确证：本机没有可用游戏/映射源（且禁止启动游戏），只能保证
   「探测存在再调用」而不是「知道确切签名为 grab(File,String,RenderTarget,Consumer)」。若实机六档全失败，
   MT_CHANNEL error 行会逐档列出 sig，需要按实机报错补档（不改判据，只加档）。
3) vision 判定本身仍是 DELEGATED：本轮只加强前置取证（存在/大小/新鲜度/失败登记），视觉结论仍交人工或视觉
   模型；没有把 DELEGATED 改成自动 PASS/FAIL —— 那属于流程变更，不在本批授权内。
4) 行号漂移：本报告行号对应当前工作树（mt_case.ps1 3332 行等）。后续若再改同文件，以函数名 + 关键代码行为
   锚点核对，不要只信行号。
5) 未做：四线实机复跑（明确禁止）。因此「1.20.1 的 T3 在真实 KubeJS 下能落盘」只有人工历史证据
   （release150-1.20.1.md 的打通记录），本批是用静态断言 + 落盘轮询逻辑证明不再走 java()。

## 6. 下一轮四线实机复跑时重点观察什么

1) 截图 op 的取证行（1.20.1 / 1.21.1 / 26.1.2 / fabric 各至少一条）：

       MT_CHANNEL op=shot channel=autojs status=ok detail=file=...\screenshots\<tag>.png mode=<f2|f5> bytes=<N> tier=T<n> sig=<签名>

   - 期望：1.20.1 → T3（m_92289_）；1.21.1 → T1 或 T4（grab 命名/匿名）；26.1.2 → T1 或 T2。
   - bytes 应显著 > 20 KB（真实画面通常数百 KB ~ 数 MB）；若 bytes 极小说明抓到空帧（grab 成功但渲染目标未就绪），
     此时按需求 2 会被 BLOCKED —— 这是期望行为，不要为了过而调低 20480。
   - 若出现 tier=T5 called_no_file 说明 m_92295_ 又「返回成功不落盘」（1.20.1 已知），应继续往后档走而不是判成功。
2) .mt_shots.json 条目：每张图应有 {file, tag, mode, path, ts}，ts ≈ 当时时刻；ts 只作登记时刻，判据看 PNG mtime。
3) vision 断言：本轮新图 → DELEGATED 且 detail 里 VISION_PATH 为绝对路径；若拿上一轮旧图（清空/复用 run 目录时
   最易发生）→ 必须 BLOCKED「不属于当前世代」或「不是本轮产物（mtime … 早于用例窗口起点 …）」。出现 PASS 即回归。
4) log 断言命中明细：明细现在逐行打印 L<行号> [platform|platform-source-echo|echo]。
   - 只有 [platform] 才计强证据；若一条断言只靠 [platform-source-echo] 就失败，说明它测的是「源码里写了」而不是
     「运行期发生了」，应改用运行期输出的标记（例如 autojs-eval#N: [REL150] ... 那类结果行）。
   - 关注 #12/#13（1.21.1）与 #12（1.20.1）是否从「2 次命中」变成「1 行平台 + 1 行 echo」。
5) crash 断言：1.21.1 应稳定 INCONCLUSIVE（若某轮突然出现 crash-reports 目录且 PASS，说明该线行为变了，需复核）；
   1.20.1 / 26.1.2 应 PASS 并给出基线个数。
6) recipes 计数：neo 三线必须 > 0（1.21.1 实测 125 个 recipe/*.json）；若仍 0，检查产物 jar 路径是否指向了错的 jar。
7) 每用例一行 CASE_EVIDENCE_WINDOW: <caseId> 截图证据窗口起点 <yyyy-MM-dd HH:mm:ss.fff>：若某用例的 vision 恒定
   BLOCKED 且时间显示为很久以前，说明 CaseWindowStartUnix 没刷新（回归）。
8) 1.20.1 note 的行号：本轮已订正为 latest.log:375 与 1.5.0+forge_1.20.1；实跑若日志前移/后移，只订正 note，
   不要反过来放宽断言。
9) fabric 线：通道走注入（.mt_autojs_injected），五面都应是 autojs；截图 JS 与三线同一份，同样看 T 档。

## 7. 备份与回滚

六份备份（改前原样，CRLF）：

    scripts/test/lib/Mt.Paths.psm1.bak-judgement-20261102-034500
    scripts/test/lib/Mt.Channel.psm1.bak-judgement-20261102-034500
    scripts/test/mt_assert.ps1.bak-judgement-20261102-034500
    scripts/test/mt_case.ps1.bak-judgement-20261102-034500
    scripts/test/cases/RELEASE150-1.20.1.json.bak-judgement-20261102-034500
    scripts/test/cases/RELEASE150-1.21.1.json.bak-judgement-20261102-034500

回滚单文件示例（PowerShell）：

    Copy-Item -LiteralPath scripts/test/mt_assert.ps1.bak-judgement-20261102-034500 -Destination scripts/test/mt_assert.ps1 -Force

离线探针（可重复执行，均在 work/_probe/ 下）：

    parse_check.ps1            四份脚本语法（PARSE_OK）
    shotjs_check.ps1           六档截图 JS + 变异测试 + 裸名拒绝
    platform_line_check.ps1    平台行/回显识别器 10 条实测行
    vision_gate_check.ps1      vision 四道硬门（real / stub 两模式）
    asserts_unchanged.ps1      断言零删除核对（对 .bak）
    listwrap_check.ps1         @(List[object]) 抛错的最小复现
    verify-channel.ps1         fabric 通道五态（既有脚本，本批复跑）
