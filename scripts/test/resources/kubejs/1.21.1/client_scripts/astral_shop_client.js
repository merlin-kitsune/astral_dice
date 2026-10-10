// ════════════════════════════════════════════════════════════════════════════
//  astral_shop_client.js —— 卡牌商店 GUI 的**客户端驱动 + 取证**探针（1.21.1 专用）
//  2026-10-09 新增（任务G 实机冒烟）。
//
//  【为什么必须是客户端脚本】
//    · 服务端探针 /astralshop 靠聊天注入驱动，而**界面开着时 T 键被 GUI 吞掉**
//      ⇒ 开店之后的任何命令都到不了服务端（SHOP-GUI 首跑实测教训）。
//    · mt_inject.ps1 的真鼠标注入只能打**窗口中心**（无坐标参数）⇒ 打不到卡框/转账按钮。
//    · 于是：先在服务端用 /astralshop openkeep 把界面**留在屏幕上**，再由本脚本
//      调 CardShopScreen#mouseClicked/mouseReleased 传**绝对 GUI 坐标**（leftPos+相对值）
//      来驱动真代码路径（起拖→落点→买；进转账面板→选行→选金额→确认）。
//
//  【只读性】不改任何游戏状态；副作用只有 console.info（→ logs/kubejs/client.log）。
//  除「关闭界面」外不做任何非 UI 调用；所有动作都走界面自己的公开事件入口。
//
//  【坐标口径】isHovering 内部加 leftPos/topPos ⇒ 传入绝对坐标 = getGuiLeft()/getGuiTop() + 相对值。
//    · 卡框0          = (29,58)          相对 (7+22, 28+30)
//    · 快捷栏第0格落点 = (125,168)        相对 (117+18*0+9, 160+8)
//    · 转账按钮        = (64,125)         相对 (48+16, 109+16)
//    · 返回键          = (183,34)         相对 (81+94+8, 26+0+8)
//    · 候选行r         = (121,59+18r)     相对 (81+2+38, 26+24+9+18r)
//    · 金额档i         = (187,93+15i)     相对 (81+94+12, 26+60+15i+7)
//    · 确认键          = (191,61)         相对 (81+94+16, 26+24+11)
//
//  【哨兵 / 关屏纪律（2026-10-09 P0 修复；任务L 扩 779/780/781）】
//    · 777 = 脚本化序列(csRunFirst)：驱动真点击/拖拽/转账，112t 后自关。
//    · 778 = 静置保持(csHold)：只观测 + 每 40t 心跳，600t(30s) 后自关（验收「打开后静置 ≥20s 不自动关闭」）。
//    · 779 = 任务L 主序列(csRunPoll)：单击选中/空位无效/双击移入/沙漏倒序(5→1→0)/就绪保持/
//            余额不足拒绝/购买后重新计时/轮询中转账被拒/转账面板与沙漏一致/大酬宾只补差额牌（不刷架、不换批、沙漏不动）/截图。
//    · 780 = 余额不足闪烁(csRunDeny)：每 tick 记红白相位 + 自截图（红像素计数）+ 周期性重放拒绝拉长观察窗口。
//    · 781 = P0 对照重演(csP0Reenact)：**故意**在第 60t 复刻旧版 janitor 的无条件关屏，
//            打出同格式的 AP_SHOPCLOSE 机器行 ⇒ 证明探针能抓住这个失效模式，且它在 778 里已不复现。
//    · 其它（含**人手动打开**）= 绝不关屏：只落 evt=humankeep / evt=idlekeep 心跳。
//      ⚠️ 旧版对任何非 777 的商店界面在 60t(≈3s) 无条件关屏 ⇒ 用户报「打开商店几秒后被强制
//         关闭」的**根因就是本探针的 janitor**（证据：temp/t87/P0-EVIDENCE.md）。
// ════════════════════════════════════════════════════════════════════════════

// ════════════════════════════════════════════════════════════════════════════
//  【任务M 新增（2026-10-09）：探针护栏 + 场景哨兵 783/784/785】
//   §1.3 护栏：本脚本被 mt_env 同步进 run/<版本>/kubejs/ = **玩家的运行环境**，
//     所以默认必须完全惰性 —— 未武装时除三行只读诊断（boot 40t / cvis 60t / jei 200t）
//     外不做任何事：不关屏、不派发鼠标事件、不发业务载荷、不发命令、不截图、
//     不改玩家物品/余额/相机。玩家手工游玩因此不受任何影响。
//   武装通道（任一成立即可）：
//     ① run/<版本>/.probe_armed 标记文件存在（JsonIO.isRegularFile，每 40t 复查）
//     ② 客户端可见余额落在保留哨兵带 [770,799]（由 /astralshop bal <tag> <哨兵> 设置，
//        即「本模组的一条自定义命令」）
//   粘滞：一旦武装就保持（买卡会扣余额 ⇒ 若按余额实时判定会在买完那一刻自废），
//     仅当「无标记文件 且 余额离开保留带 [600,999] 且 当前没有开屏」时解除。
//   场景哨兵：783 = 跨 tick 真实屏幕事件拖拽（§8）/ 784 = 沙漏去数字取证（§7）/
//     785 = 几何窗口（给 OS 级真鼠标拖拽脚本换算像素坐标，只报几何、零动作）。
//  【任务N 新增（2026-10-09）：场景哨兵 786】
//   786 = 任务N 三处修复取证：① 主视图按钮顺序（转账/星光兑换不再交叉）
//         ② 转账面板整体 2× + 面板内全部真实控件的命中（点击/焦点）③ 返回键不再有多余的 `<` 字符。
//   坐标口径（GUI 相对）：主视图仍是 1×（左按钮中心 27,125 / 中按钮中心 64,125）；
//     面板内一律 csTp(设计坐标) = (18 + 设计x*2, -41 + 设计y*2) —— 与 CardShopScreen
//     TP_X/TP_Y/TP_SCALE 同口径，且**独立复算**后与 mod 的 transferGeometry() 逐项比对。
//     由此得到：返回键 (222,-25)、确认键 (238,29)、候选行0 (98,25)、金额档3 (230,183)、
//     搜索框 (98,-21)、刷新热区 x 大于等于 164、金额框 (238,65)、滚动条中点 (186,15)。
// ════════════════════════════════════════════════════════════════════════════

var Mc_CLASS = Java.loadClass("net.minecraft.client.Minecraft");
var ShopScreen_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.screen.CardShopScreen");
var ClientState_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.client.CardShopClientState");
var WalletState_CLASS = Java.loadClass("com.merlinkitsune.starenginelib.economy.StarCoinWalletState");
var ItemStack_CLASS = Java.loadClass("net.minecraft.world.item.ItemStack");
var BuyPayload_CLASS = null;
var PacketDist_CLASS = null;
try { BuyPayload_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.network.CardShopBuyPayload"); } catch (eB1) { }
try { PacketDist_CLASS = Java.loadClass("net.neoforged.neoforge.network.PacketDistributor"); } catch (eB2) { }
var JeiInternal_CLASS = null;
var JeiCategory_CLASS = null;
try { JeiInternal_CLASS = Java.loadClass("mezz.jei.common.Internal"); } catch (eJ1) { }
try { JeiCategory_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.compat.jei.CardShopCategory"); } catch (eJ2) { }
var ShopMenu_CLASS = null;
try { ShopMenu_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.shop.CardShopMenu"); } catch (eS1) { }
var Screenshot_CLASS = null;
var NativeImage_CLASS = null;
try { Screenshot_CLASS = Java.loadClass("net.minecraft.client.Screenshot"); } catch (eSc) { }
try { NativeImage_CLASS = Java.loadClass("com.mojang.blaze3d.platform.NativeImage"); } catch (eNi) { }
// 任务L 自截图落盘目录（验收要求 temp/t87/shots/*.png）
var CS_SHOTS = "F:\\MCProject\\astral_dice_multiloader-next\\temp\\t87\\shots";   // 归档目录（收尾时从 run/<版本>/screenshots 拷过来；java.io 被类过滤器拒绝，探针无法直接写这里）
// ⚠️ KubeJS 客户端脚本里**没有** `java` 全局：`new java.io.File(...)` 会抛 ReferenceError
//    ⇒ 必须先 Java.loadClass 拿到 class 再 new（否则 csShot 在 takeScreenshot 之前就中断，实测 err=shot:ReferenceError）
var File_CLASS = null;
try { File_CLASS = Java.loadClass("java.io.File"); } catch (eFi) { }
var csLastDst = -1;

var csTick = 0;
var csArmed = false;
var csRun = false;              // 哨兵闸门: 只有余额==777 的那次开店才跑脚本化序列
var csGate = 777;               // 777 = 脚本化序列
var csHoldGate = 778;           // 778 = 静置保持窗口（只观测，不自关）
var csOpenBal = -1;             // 本次开屏时读到的余额哨兵
var csHold = false;             // 本次开屏是否处于静置保持模式
var csHumanKeepLogged = false;  // 人手动开屏只记一次 humankeep
var csJeiLast = -9999;
var csStep = 0;
var csOpens = 0;
var csSlot0Before = "?";
var csBalBefore = -1;
// 任务L 新增哨兵：779 = GUI 主序列；780 = 余额不足闪烁；781 = P0 对照重演
var csPoll = false;
var csPollGate = 779;
var csDeny = false;
var csDenyGate = 780;
var csP0 = false;
var csP0Gate = 781;
var csCloseBy = "";        // 主动自关时标记调用方（关屏归因用）
var csShotSeq = 0;         // 自截图序号
var csDenyRearms = 0;      // 780 里重放拒绝的次数
var csWinBal = -1;         // 双击窗口实测的基准余额
var csShelf0Id = "?";      // 双击移入前记下的货架0物品 id（买完后槽会变空 ⇒ 必须买前记）
var csNat = false;         // 782 = 自然 60s 轮询窗口（不做任何 cycleset 干预）
var csNatGate = 782;
var csNatSoldCsv = "";     // 782 里 t0 时刻的已售位图（用于比对「未刷架」）
var csNatFp = "";          // 782 里 t0 时刻的货架指纹（sold+id）——比对「有没有刷架」
var csNatIds = ["?", "?", "?", "?", "?", "?"];  // 782 里 t0 时刻逐格物品 id（抗「外部购买」干扰的比对基线）
var csDenyLastRed = -1;    // 780 闪烁相位翻转检测（只在翻转那一拍抓帧）
var csDenyShots = 0;       // 780 已抓帧数（上限 14，避免刷屏）

// ══════════════════ 任务M 新增（2026-10-09）：护栏武装 + 场景哨兵 783/784/785 ══════════════════
var CS_ARM_LO = 770, CS_ARM_HI = 799;      // 武装哨兵带（与既有 777..782 闸门值兼容）
var CS_KEEP_LO = 600, CS_KEEP_HI = 999;    // 粘滞保留带（买卡扣钱后仍视为已武装）
var csLive = false;        // 当前是否已武装（false = 完全惰性，除只读诊断外零动作）
var csArmWhy = "init";     // 武装原因记账
var csArmFile = "?";       // .probe_armed 探测结果：yes/no/no_jsonio/no_mcdir/EX:…
var csArmChecked = -9999;  // 上次检查标记文件的 tick
var JsonIO_CLASS = null;
try { JsonIO_CLASS = Java.loadClass("dev.latvian.mods.kubejs.util.JsonIO"); } catch (eJi) { JsonIO_CLASS = null; }
var csShotTag = "t87";     // 截图文件名前缀（任务M 场景切到 t88）
var csM = false; var csMGate = 783;    // 783 = 跨 tick 真实屏幕事件拖拽（§8）
// 任务N（2026-10-09）：786 = 主视图按钮顺序 + 转账面板 2× + 返回键去掉多余符号
var csN = false; var csNGate = 786;
var csNBal = -1;      // 开屏余额
var csNViewLeft = -1; // 点左按钮(星光兑换)后的 view（期望 0 = 没进转账面板）
var csNTpSeq = 0;     // 面板内已派发的真实事件数（记账用）
var csNPhase = 0;     // 786 内部阶段：0=等 cd 归零 / 1=面板时序 / 2=收尾 / -1=超时兜底
var csNBase = -1;     // cd 归零那一拍的 step（面板内动作全部相对它排程）
// 面板局部设计坐标 → GUI 相对坐标：屏幕 = 面板原点 + 设计坐标 × 1（与 mod 的 tpX/tpY 独立复算）
// 任务O §1.1（2026-10-09）：2× 放大已**回滚** ⇒ 面板原点回到居中口径 (288-126)/2=81、(187-135)/2=26，缩放 = 1。
// 任务P §2.2（2026-10-09）：新加大版素材（底图 107x183 / 搜索条 99x14 / 滑轨 6x154 / 滑块 12x17）
//   ⇒ 面板包围盒 141x183 居中 ⇒ 原点 (288-141)/2 = 73、(187-183)/2 = 2；缩放仍为 1（尺寸一律不变）。
var CS_TP_X = 73, CS_TP_Y = 2, CS_TP_S = 1;
function csTp(dx, dy) { return [CS_TP_X + dx * CS_TP_S, CS_TP_Y + dy * CS_TP_S]; }
function csTpX(dx) { return CS_TP_X + dx * CS_TP_S; }
function csTpY(dy) { return CS_TP_Y + dy * CS_TP_S; }
function csStr(fn) { try { var v = fn(); return (v === null || v === undefined) ? "null" : ("" + v); } catch (e) { return "EX:" + ("" + e).substring(0, 60); } }
// 任务P 新布局的设计坐标（与 CardShopScreen 的 TP_* 常量逐项对应；改这里必须同步改那边）：
//   back 中心 (109+8, 0+8) · confirm (109+16, 24+11) · limit0 (109+12, 60+7) · limit3 (109+12, 60+45+7)
//   row0 (2+(93-2)/2, 29+9) · search (4+3+83/2, 4+2+10/2) · refresh (90, 4+14/2)
//   amount (109+16, 46+7) · slide (93+6, 25+17/2) · bg 107x183
function csTpCalc() {
    return "scale=" + CS_TP_S + ":panel=" + CS_TP_X + "," + CS_TP_Y
        + ":back=" + csTpX(117) + "," + csTpY(8)
        + ":confirm=" + csTpX(125) + "," + csTpY(35)
        + ":limit0=" + csTpX(121) + "," + csTpY(67)
        + ":limit3=" + csTpX(121) + "," + csTpY(112)
        + ":row0=" + csTpX(47) + "," + csTpY(38)
        + ":search=" + csTpX(39) + "," + csTpY(11)
        + ":refresh=" + csTpX(92) + "," + csTpY(11)
        + ":amount=" + csTpX(125) + "," + csTpY(53)
        + ":slide=" + csTpX(99) + "," + csTpY(33)
        + ":bg=" + (107 * CS_TP_S) + "x" + (183 * CS_TP_S);
}
var csH = false; var csHGate = 784;    // 784 = 沙漏去数字取证（§7）
var csG = false; var csGGate = 785;    // 785 = 几何窗口（OS 级真鼠标校准用）
// 787 = 任务N′ §4 实机重测（悬停/选中贴图、沙漏 4 份、冷却灰化、拖拽三连+反例、面板 2×/越界、创造栏排序、JEI 配方）
var csN2 = false; var csN2Gate = 787; var csN2SelT = -1;
// ══ 任务O（2026-10-09）：788 = §1.1 回滚 + §1.2 删文本 + §1.4 兑换冷却 + ⑥ 沙漏；789 = §1.3 货架持久化 ══
var csO = false; var csOGate = 788;      // 788 = 单次开屏的静态取证（转账面板/兑换冷却/沙漏）
var csOB = false; var csOBGate = 789;    // 789 = 跨开屏的货架持久化取证（关店/重开/跨 0 点）
// ══ 任务P（2026-10-09）：790 = 新加大版素材布局 / 层级遮挡 / 删金额输入 / 折扣角标移位 / 头像对齐 ══
var csP = false; var csPGate = 790;
var csPP = 0;                            // 790 阶段：0=主视图+开面板+控件交互 / 1=出折扣卡 / 9=收尾取证 / 99=等关屏 / 100=已结束
var csPT = 0;                            // 790 阶段内 tick 计数
var csPPromoHit = -1;                    // 790 命中的折扣格下标（-1 = 本批货架没有折扣卡）
var csPPromoTry = 0;                     // 790 兜底重抽次数
// ══════════════════ 任务Q（2026-10-09）：791 = 转账页完全独立 / 按钮去文字 / tooltip 只留名字 / 居中与对齐 / 立牌新文案 / 经商刷钱封堵 ══════════════════
//  阶段 csQP（**跨开屏保留**，与 789 csOBP 同构）：
//    0 = 视觉取证（主视图基线 / 两按钮 tooltip 只留名字 / 攻击牌无说明 / 立牌新文案 / 转账页完全独立）
//    1 = 封堵①③：同批卡反复「服务端收屏 → 重开」100 次，只允许第一次 +1 星币
//    2 = 封堵②：沙漏归 0 换新批（cycleset 0）后重开，新批第一次 +1、第二次不再 +1
//    9 = 收尾关屏 / 100 = 已结束
var csQ = false; var csQGate = 791; var csQGateMax = 891;   // 余额**带**：立牌返现会让余额 +1，不能用等值匹配
var csQP = 0;              // 791 阶段（跨开屏保留）
var csQT = 0;              // 791 阶段内 tick 计数（每次开屏归零）
var csQS = 0;              // 791 阶段 0 子步
var csQP2 = 0;             // 791 阶段 2 子步
var csQStarted = false;    // 791 是否已初始化过阶段机
var csQOpens = 0;          // 791 本场景累计开屏数
var csQCycles = 0;         // 791 阶段 1 已完成的开关轮数
var csQBalBase = -1;       // 791 阶段 1 的余额基线（= 装立牌前那一次开屏的余额）
var csQLastBal = -1;       // 791 阶段 2 的余额基线（逐次链式更新）
var csQDeltaSeq = "";      // 791 阶段 1 逐轮 Δ 序列（逗号分隔）
var csQHoverLabel = "";    // 791 正在等待真光标的标签（"" = 无请求）
var csQHoverT = 0;         // 791 等真光标的 tick 数
var csQW = 0;              // 791 通用等待计数
var csQOut = false;        // 791 是否处于「关店期间」（由 tick 钩子推进）
var csQGuide = -1;         // 791 阶段 100：手册页取证子状态（-1 未开始 / 1 待开书 / 2 已开书待截图）
var csQGuideT = 0;         // 开书后的等待 tick（等 Patchouli 把页面渲染完再截图）
var csQOutc = 0;           // 791 店外累计 tick
// ── 792 任务V（2026-10-10）：立即补差取证（界面开着时装备/卸下经商立牌、大酬宾）──
// ⚠️ 哨兵值必须**避开** csQ 的余额带 [791,891]（csQGate/csQGateMax）——分派顺序里 `else if (csQ)` 在 `else if (csV)` 之前，
//    若用 792 会被 csQ 抢走（2026-10-10 实机实测：余额 792 时跑的是 q_cal 场景，792 阶段机一次都没进）。
// ⚠️ 哨兵值还必须落在**武装带** CS_ARM_LO..CS_ARM_HI = [770,799] 之内（见 csRefreshArm）——先用 920，
//    结果 csLive 恒 false（心跳全程 live=0 armed=0 step=0）、探针完全惰性、整个 792 阶段机一次都没进
//    （2026-10-10 v6 实机实测）。⇒ 取带内且未被占用的 775。
var csV = false; var csVGate = 775;
var csVP = 0;              // 792 阶段（**跨开屏保留**）：0 基线/1 卸下/2 沙漏归 0/3 开屏大酬宾/4 窗口过期重排/5 关屏/6 店外大酬宾/7 重开/8 坏档/100 结束
var csVT = 0;              // 792 阶段内 tick 计数（每次开屏/切阶段归零）
var csVOut = false;        // 792 是否处于「关店期间」（由 tick 钩子推进）
var csVOutc = 0;           // 792 店外累计 tick
var csVOpens = 0;          // 792 本场景累计开屏数
var csOP = 0;                            // 788 阶段：0=面板 / 1=兑换冷却 / 2=沙漏 / 3=收尾 / 9=已结束
var csOT = 0;                            // 788 阶段内 tick 计数（每次开屏与切阶段归零）
var csOEx2 = 0;                          // 788 冷却中第二次点击：0 = 未点；>0 = 点击时的 csOT；<0 = 等真实光标超时（值 = -csOT）
var csOHoverDeadline = -1;               // 等真实光标停到兑换键的截止 step
var csORej0 = -1;                        // 第二次点击前的拒绝计数（判断有没有真的早退）
var csExBal0 = -1; var csExStar0 = "?";  // 兑换前后对照基准
var csExBal2 = -1; var csExStar2 = "?";  // 任务O 修正：冷却中第二次点击「点击前一刻」的基线（applied 判据必须用它）
var csOBP = 0;                           // 789 阶段（**跨开屏保留**）：0=一开 / 1=一关(店外) / 2=二开 / 3=准备二关 / 4=二关(店外) / 5=三开 / 9=收尾
var csOBT = 0;                           // 789 阶段内 tick 计数（每次开屏/切阶段归零）
var csOBOut = false;                     // 789 是否处于「关店期间」子阶段（走 csRunOBOut 钩子）
var csOBOutc = 0;                        // 789 店外累计 tick（记账）
var csOBIds1 = null, csOBIds2 = null, csOBIds3 = null, csOBIds5 = null;
var csOBFp1 = "?";
// 任务O §1.3 取证：逐格「id#售价#折扣位#已售位」明细（换牌判据不能只看物品 id，要看整条货架状态）
var csOBDet1 = "?"; var csOBDet3 = "?"; var csOBDet5 = "?";
var csExIds = "?";
/** 逐格比对两批 6 格货架 id（1 = 逐格全等）；nil/异常一律判不等 */
function csObSame(a, b) {
    try {
        if (a == null || b == null) { return 0; }
        for (var i = 0; i < 6; i++) { if (a[i] !== b[i]) { return 0; } }
        return 1;
    } catch (e) { return 0; }
}
var csMCard = 0;
var csMCardId = "?";
var csMBal = -1;
var csMPreSold = "";
var csMPreInv = "";
var csMDropSlot = -1;
var csMDropX = -1; var csMDropY = -1;   // 落点（GUI 相对坐标）
var csMGeoX = -1; var csMGeoY = -1;     // 卡框中心（GUI 相对坐标）
var csPrevX = -1; var csPrevY = -1;     // 上一次已派发鼠标位置（算 mouseDragged 位移用）
var csObsSel = -2; var csObsDrag = -2; var csObsCarried = "?"; var csObsSold = "";
// 784 场景的「面板 + cd>0」瞬态取证（任务M §7 面板一处）：cycleset 之后每 tick 采样 view/cd，
//   命中 view=1 且 cd>0 的那一帧立刻截图（csHShot 只截一次），最后在 step80 汇总 seen/ticks。
var csHBurst = false; var csHSeen = 0; var csHTk = 0; var csHShot = false;

function cs(s) {
    try { console.info("AP_CSC:" + s); } catch (e) { }
}

function csNum(fn) { try { return fn(); } catch (e) { return -999; } }

function csId(stack) {
    try {
        if (stack == null || stack.isEmpty()) return "empty";
        var n = "" + stack.getItem();
        var m = /(?:^|[.\s])([A-Za-z0-9_$]+)@[0-9a-fA-F]+/.exec(n);
        var raw = (m != null) ? m[1] : n;
        var mm = /([a-z0-9_]+:[a-z0-9_\/]+)/.exec("" + stack);
        return (mm != null) ? mm[1] : raw;
    } catch (e) { return "<ex>"; }
}

function csCnt(stack) { try { return stack.getCount(); } catch (e) { return -1; } }

/** 类名：Rhino 里 `obj.getClass()` 不可用（TypeError: Cannot find function getClass）⇒ 用 toString 的 "全类名@hash" 解析 */
function csClsName(o) {
    try {
        if (o == null) { return "null"; }
        var s = "" + o;
        var m = /([A-Za-z_$][A-Za-z0-9_$.]*)@[0-9a-fA-F]+/.exec(s);
        if (m != null) { var full = m[1]; return full.substring(full.lastIndexOf(".") + 1); }
        return s.substring(0, 60);
    } catch (e) { return "?"; }
}
/** 异常文本（比 csStr 长，定位 Java 侧异常要看全类名） */
function csErr(e, n) { try { return ("" + e).substring(0, (n === undefined ? 200 : n)); } catch (e2) { return "?"; } }

/** 商店界面状态一行（货架/余额/会话位/冷却/候选名单） */
function csState(mc) {
    var scr = mc.screen;
    var gx = csNum(function () { return scr.getGuiLeft(); });
    var gy = csNum(function () { return scr.getGuiTop(); });
    var bal = csNum(function () { return WalletState_CLASS.balance(); });
    var menu = null;
    try { menu = scr.getMenu(); } catch (e) { menu = null; }
    var shelf = "";
    var used = -1;
    var cd = -1;
    if (menu != null) {
        for (var i = 0; i < 3; i++) {
            var id = "<ex>"; var pr = -1; var sold = -1;
            try { id = csId(menu.getSlot(i).getItem()); } catch (e1) { id = "<ex>"; }
            try { pr = menu.priceOf(i); } catch (e2) { pr = -1; }
            try { sold = menu.isSold(i) ? 1 : 0; } catch (e3) { sold = -1; }
            shelf += (i > 0 ? "," : "") + i + "=" + id + "@" + pr + (sold === 1 ? "(sold)" : "");
        }
        used = csNum(function () { return menu.isTransferUsedThisSession() ? 1 : 0; });
        cd = csNum(function () { return menu.transferCooldownSeconds(); });
    }
    var cands = csNum(function () { return ClientState_CLASS.transferCandidates().size(); });
    var cand0 = "-";
    try {
        var list = ClientState_CLASS.transferCandidates();
        if (list.size() > 0) { cand0 = "" + list.get(0).name() + "/" + list.get(0).balance(); }
    } catch (e4) { cand0 = "<ex>"; }
    var inv0 = "<ex>";
    try { inv0 = csId(mc.player.getInventory().getItem(0)) + "x" + csCnt(mc.player.getInventory().getItem(0)); } catch (e5) { }
    var inv1 = "<ex>";
    try { inv1 = csId(mc.player.getInventory().getItem(1)) + "x" + csCnt(mc.player.getInventory().getItem(1)); } catch (e6) { }
    var scrCls = (mc.screen == null) ? "null" : ("" + mc.screen);
    return "gx=" + gx + ":gy=" + gy + ":bal=" + bal + ":used=" + used + ":cd=" + cd
        + ":shelf=[" + shelf + "]:cands=" + cands + ":cand0=" + cand0
        + ":inv0=" + inv0 + ":inv1=" + inv1 + ":scr=" + scrCls;
}

/** 点一下（绝对坐标 = gx+dx, gy+dy）；返回 mouseClicked 的布尔。每一步都落一行日志 */
function csClick(mc, dx, dy, what, step) {
    var scr = mc.screen;
    var gx = csNum(function () { return scr.getGuiLeft(); });
    var gy = csNum(function () { return scr.getGuiTop(); });
    var ret = "?";
    try { ret = scr.mouseClicked(gx + dx, gy + dy, 0) ? 1 : 0; }
    catch (e) { ret = "EX:" + e; }
    cs("evt=click:step=" + step + ":what=" + what + ":abs=" + (gx + dx) + "," + (gy + dy) + ":ret=" + ret);
    return ret;
}

function csRelease(mc, dx, dy, what, step) {
    var scr = mc.screen;
    var gx = csNum(function () { return scr.getGuiLeft(); });
    var gy = csNum(function () { return scr.getGuiTop(); });
    var ret = "?";
    try { ret = scr.mouseReleased(gx + dx, gy + dy, 0) ? 1 : 0; }
    catch (e) { ret = "EX:" + e; }
    cs("evt=release:step=" + step + ":what=" + what + ":abs=" + (gx + dx) + "," + (gy + dy) + ":ret=" + ret);
    return ret;
}
/** 找一个空的玩家物品栏槽(服务端菜单里 Slot.index/坐标), 返回 [slotIdx, gx+中心x, gy+中心y] 或 null=没找到 */
function csEmptyInvSlot(mc) {
    var out = null;
    try {
        var m = mc.screen.getMenu();
        var n = 0;
        try { n = m.slots.size(); } catch (e0) { try { n = m.slots.length; } catch (e1) { n = 0; } }
        for (var i = 0; i < n; i++) {
            try {
                var s = m.slots.get(i);
                var st = s.getItem();
                if (st == null || !st.isEmpty()) { continue; }
                var ccl = "" + s.container;
                if (ccl.indexOf("Inventory") < 0 && ccl.indexOf("Player") < 0) { continue; }
                var idx = -1;
                try { idx = s.index; } catch (e2) { idx = -1; }
                if (idx < 0) { continue; }
                var sx = csNum(function () { return s.x; });
                var sy = csNum(function () { return s.y; });
                if (sx < 0 || sy < 0) { continue; }
                var gx = csNum(function () { return mc.screen.getGuiLeft(); });
                var gy = csNum(function () { return mc.screen.getGuiTop(); });
                out = [idx, gx + sx + 8, gy + sy + 8];
                break;
            } catch (e3) { }
        }
    } catch (e) { out = null; }
    return out;
}

/** 按 CardShopMenu/Screen 的官方网格常量找一个**空的玩家物品栏格**; 返回 [invIndex, relX, relY] 或 null。
 *  返回值是**绝对屏幕坐标**(鼠标事件用)。口径与 CardShopScreen#inventoryCellIndexAt 同源: 主物品栏 3 行 (inv 9..35) + 快捷栏 1 行 (inv 0..8);
 *  槽序号 = SHELF_SIZE + CardShopMenu.playerSlotOffset(inv)。避开 getClass/container 等白名单外方法。 */
function csEmptyInvCell(mc) {
    var IX = 117, IY = 102, IHY = 160, PITCH = 18, CELL = 16;
    var m = null;
    try { m = mc.screen.getMenu(); } catch (e0) { return null; }
    if (m == null) { return null; }
    var shelf = 3;
    try { shelf = ShopMenu_CLASS.SHELF_SIZE; } catch (e1) { shelf = 3; }
    var gx = 0, gy = 0;
    try { gx = csNum(function () { return mc.screen.getGuiLeft(); }); } catch (eG1) { gx = 0; }
    try { gy = csNum(function () { return mc.screen.getGuiTop(); }); } catch (eG2) { gy = 0; }
    for (var row = 0; row < 4; row++) {
        for (var col = 0; col < 9; col++) {
            var inv = (row === 3) ? col : (9 + row * 9 + col);
            var slotIdx = -1;
            try { slotIdx = shelf + ShopMenu_CLASS.playerSlotOffset(inv); } catch (e2) { continue; }
            var empty = false;
            try { var st = m.slots.get(slotIdx).getItem(); empty = (st == null) || st.isEmpty(); } catch (e3) { continue; }
            if (!empty) { continue; }
            var cellY = (row === 3) ? IHY : (IY + row * PITCH);
            var cellX = IX + col * PITCH;
            return [inv, gx + cellX + 8, gy + cellY + 8];
        }
    }
    return null;
}
/** 读客户端物品栏第 i 格 (0..8 快捷栏 / 9..35 主物品栏) */
function csInvItem(mc, i) {
    try { if (i < 0) { return "<na>"; } var st = mc.player.getInventory().getItem(i); return csId(st) + "x" + csCnt(st); } catch (e) { return "<ex>"; }
}

/** 真·拖拽: 按下卡框后先把鼠标拖到空的物品栏槽(this = 幽灵跟手), 再松手 → 屏内 mouseReleased 发 CardShopBuyPayload */
function csDragSlot(mc, what, step) {
    var t = csEmptyInvCell(mc);
    var ret = "?";
    if (t == null) { cs("evt=drag:step=" + step + ":what=" + what + ":target=none"); return; }
    try {
        var scr = mc.screen;
        ret = scr.mouseDragged(t[1], t[2], 0, t[1] - 0, t[2] - 0) ? 1 : 0;
    } catch (e) { ret = "EX:" + e; }
    cs("evt=drag:step=" + step + ":what=" + what + ":target=inv" + t[0] + ":abs=" + t[1] + "," + t[2] + ":ret=" + ret);
}

/** 在**计算出的空物品栏槽**上松手(而不是硬编码坐标) */
function csReleaseSlot(mc, what, step) {
    var t = csEmptyInvCell(mc);
    csLastDst = (t == null) ? -1 : t[0];
    var ret = "?";
    var abs = "none";
    try {
        var scr = mc.screen;
        var gx = csNum(function () { return scr.getGuiLeft(); });
        var gy = csNum(function () { return scr.getGuiTop(); });
        var px = (t == null) ? (gx + 125) : t[1];
        var py = (t == null) ? (gy + 168) : t[2];
        abs = px + "," + py;
        ret = scr.mouseReleased(px, py, 0) ? 1 : 0;
    } catch (e) { ret = "EX:" + e; }
    cs("evt=release:step=" + step + ":what=" + what + ":target=" + ((t == null) ? "fallback" : ("inv" + t[0])) + ":abs=" + abs + ":ret=" + ret);
    return ret;
}

/** 拖拽落点诊断: 客户端菜单 slot3(主物品栏首格) 与玩家背包 inv9 的实际内容 + 屏内 dragIndex 可读性 */
function csDropDiag(mc, phase) {
    var out = [];
    function t(n, fn) { try { var v = fn(); out.push(n + "=" + ((v === null || v === undefined) ? "null" : ("" + v).replace(/[\r\n]+/g, " ").substring(0, 40))); } catch (e) { out.push(n + "=EX:" + ("" + e).substring(0, 40)); } }
    t("slot3", function () { return csId(mc.screen.getMenu().getSlot(3).getItem()); });
    t("inv9", function () { return csId(mc.player.getInventory().getItem(9)); });
    t("dragIndex", function () { return mc.screen.dragIndex; });
    t("bal", function () { return WalletState_CLASS.balance(); });
    cs("evt=dropdiag:" + phase + ":" + out.join(":"));
}

/** 二分取证: 绕开屏幕拖拽, 直接从客户端发一次购买载荷(判定失败点在拖拽计算还是载荷/handleBuy 链路) */
function csDirectBuy(mc, shelf, slotL, step) {
    var ret = "?";
    try {
        if (BuyPayload_CLASS == null || PacketDist_CLASS == null) { ret = "cls=null"; }
        else { PacketDist_CLASS.sendToServer(new BuyPayload_CLASS(shelf, slotL)); ret = "sent"; }
    } catch (e) { ret = "EX:" + e; }
    cs("evt=bissect:step=" + step + ":how=directBuyPayload:shelf=" + shelf + ":slot=" + slotL + ":ret=" + ret);
}

/** JEI 运行时取证: 类别是否注册 + 记录数 + 是否混入排除项 */
function csJei() {
    if (JeiInternal_CLASS == null || JeiCategory_CLASS == null) { cs("evt=jei:cls=null"); return; }
    var rt = null;
    try { rt = JeiInternal_CLASS.getJeiRuntime(); } catch (e0) { cs("evt=jei:rt=EX:" + e0); return; }
    if (rt == null) { cs("evt=jei:rt=null"); return; }
    var rm = null;
    try { rm = rt.getRecipeManager(); } catch (e1) { cs("evt=jei:rm=EX:" + e1); return; }
    var type = JeiCategory_CLASS.RECIPE_TYPE;
    var uid = "?";
    try { uid = "" + type.getUid(); } catch (e2) { uid = "EX:" + e2; }
    var cat = "none";
    try { cat = (rm.getRecipeCategory(type) == null) ? "null" : "ok"; } catch (e3) { cat = "EX:" + e3; }
    var n = -1;
    var ids = "none";
    var bad = "none";
    var EXCL = ["astral_dice:effect_card_living_page", "astral_dice:effect_card_fate_guidance", "astral_dice:fu_card", "astral_dice:huo_card", "astral_dice:attack_card_bite", "astral_dice:attack_card_dragon_roar", "astral_dice:attack_card_full_power"];
    try {
        var list = rm.createRecipeLookup(type).get().toList();
        n = list.size();
        var arr = [];
        var badArr = [];
        for (var i = 0; i < n; i++) {
            var cid = "?";
            try { cid = csId(new ItemStack_CLASS(list.get(i).card())); } catch (e4) { cid = "<ex>"; }
            arr.push(cid);
            for (var j = 0; j < EXCL.length; j++) { if (EXCL[j] === cid) { badArr.push(cid); } }
        }
        ids = arr.join("|");
        bad = (badArr.length === 0) ? "none" : badArr.join("|");
    } catch (e5) { ids = "EX:" + e5; }
    cs("evt=jei:cat=" + cat + ":uid=" + uid + ":recipes=" + n + ":excluded=" + bad + ":ids=" + ids);
}

/** 关掉当前屏幕(客户端侧): closeContainer 会发关闭包给服务端(正确路径), setScreen(null) 是兜底。 */
function csClose(mc) {
    var how = [];
    try { mc.player.closeContainer(); how.push('closeContainer'); } catch (e) { how.push('closeContainer=EX'); }
    try { mc.setScreen(null); how.push('setScreen'); } catch (e2) { how.push('setScreen=EX'); }
    return how.join('+');
}

/** 客户端可见性一次性体检(tick 60 跑一次;此时未开任何容器,两处调用都是无害空操作)。 */
function csVis(mc) {
    var out = [];
    function t(n, fn) { try { var v = fn(); out.push(n + '=' + ((v === null || v === undefined) ? 'null' : ('' + v).replace(/[\r\n]+/g, ' ').substring(0, 56))); } catch (e) { out.push(n + '=EX:' + ('' + e).substring(0, 56)); } }
    t('mc', function () { return av1(mc); });
    // 任务M 护栏：原先这里会调 setScreen(null) + closeContainer() —— 属「未武装也改游戏状态」，
    //  已改为**纯读**（只读屏类名 / 容器类名），任何调用方都改不动游戏状态。
    t('screen', function () { return (mc.screen == null) ? 'null' : ('' + mc.screen); });
    t('p_closeCont', function () { return (mc.player.containerMenu == null) ? 'null' : ('' + mc.player.containerMenu); });
    t('p_getInv', function () { return csId(mc.player.getInventory().getItem(0)); });
    t('p_containerMenu', function () { return '' + mc.player.containerMenu; });
    t('gameMode', function () { return mc.gameMode == null ? 'null' : 'ok'; });
    t('jeiRt', function () { return (JeiInternal_CLASS == null) ? 'clsnull' : ((JeiInternal_CLASS.getJeiRuntime() == null) ? 'null' : 'ok'); });
    t('shopCls', function () { return ShopScreen_CLASS == null ? 'null' : 'ok'; });
    t('getGuiLeft', function () { return 'M=' + (mc.screen == null ? 'noscreen' : 'has'); });
    cs('evt=cvis:' + out.join('|'));
}
function av1(mc) { try { return '' + mc; } catch (e) { return 'ex'; } }

// ══════════════════ 任务L 新增（2026-10-09）：关屏取证 / 命令通道 / 自截图 / 779·780·781 序列 ══════════════════

/** 原样落一行（不加 AP_CSC: 前缀）——供 AP_SHOPCLOSE 这类规定格式的机器行使用 */
function csRaw(s) { try { console.info(s); } catch (e) { } }

/** §1 关屏归因机器行：side/reason/caller/tick/step/opens + 现场(容器/屏类/是否本轮探针自己关的) */
function csShopClose(mc, reason, caller, extra) {
    var m = "?", s = "null";
    try { m = "" + mc.player.containerMenu; } catch (e) { m = "ex"; }
    try { s = (mc.screen == null) ? "null" : ("" + mc.screen); } catch (e2) { s = "ex"; }
    csRaw("AP_SHOPCLOSE:side=client:reason=" + reason + ":caller=" + caller + ":tick=" + csTick
        + ":step=" + csStep + ":opens=" + csOpens + ":menu=" + m + ":scr=" + s + ":" + extra);
}

/** 商店屏消失时判因：容器还是 CardShopMenu ⇒ 只是本地清了屏(P0 特征)；否则=容器被关(服务端侧) */
function csCloseForensics(mc) {
    var m = "?";
    try { m = "" + mc.player.containerMenu; } catch (e) { m = "ex"; }
    var self = (csCloseBy !== "");
    var reason = self ? "probe_self_close" : ((m.indexOf("CardShopMenu") >= 0) ? "screen_cleared_local_container_kept" : "container_closed");
    csShopClose(mc, reason, self ? csCloseBy : "detector", "selfclose=" + (self ? 1 : 0));
    csCloseBy = "";
}

/** 关屏并标记调用方（所有主动自关都走这里，保证 AP_SHOPCLOSE 的 caller 可归因） */
function csCloseBy_(mc, by) { csCloseBy = by; return csClose(mc); }

/** 客户端侧发命令（GUI 开着时 T 键被吞，只能用这条通道；⚠️ 不要带前导斜杠） */
function csCmd(mc, cmd, what, step) {
    var ret = "?";
    try { mc.player.connection.sendCommand(cmd); ret = "sent"; }
    catch (e) { ret = "EX:" + ("" + e).substring(0, 90); }
    cs("evt=cmd:step=" + step + ":what=" + what + ":cmd=" + cmd + ":ret=" + ret);
}

/** 货架「已售位图」：固定 6 格逐格 0(未售)/1(已售)，用于证明「cd>0 期间不刷架 / 到 0 才刷架」。
 *  ⚠️ 不能用 menu.shelfSize()：那是**服务端**字段（客户端菜单只同步 DataSlot 的 sold/discount/price），
 *     客户端恒读到 0 ⇒ 实测 sold=?（2026-10-09 第二轮）。isSold(i) 走 DataSlot，客户端可读且越界安全。 */
function csSoldCsv(mc) {
    try {
        var m = mc.screen.getMenu();
        var s = "";
        for (var i = 0; i < 6; i++) { s += (m.isSold(i) ? "1" : "0"); }
        return s;
    } catch (e) { return "<ex>"; }
}

/** 货架指纹：已售位图 + 每格物品 id（重 roll 会同时换掉两者）⇒「有没有刷架」的双重判据 */
function csShelfFp(mc) {
    try {
        var m = mc.screen.getMenu();
        var s = csSoldCsv(mc);
        for (var i = 0; i < 6; i++) {
            var st = null;
            try { st = m.getSlot(i).getItem(); } catch (eS) { st = null; }
            s += "," + ((st == null) ? "empty" : (st.isEmpty() ? "empty" : csId(st)));
        }
        return s;
    } catch (e) { return "<ex>"; }
}

/** 逐格物品 id 数组（空 → "empty"）；返回数组而非字符串，便于按格比对 */
function csShelfIds(mc) {
    var out = [];
    for (var i = 0; i < 6; i++) {
        var v = "?";
        try {
            var st = mc.screen.getMenu().getSlot(i).getItem();
            v = (st == null || st.isEmpty()) ? "empty" : csId(st);
        } catch (e) { v = "?"; }
        out.push(v);
    }
    return out;
}

/** 货架明细（任务O §1.3）：逐格 `id#售价#折扣位#已售位`。
 *  「重开商店是否换了货架」必须整条状态逐格比对，只看物品 id 会漏掉折扣/售价变化。 */
function csShelfDetail(mc) {
    var out = [];
    for (var i = 0; i < 6; i++) {
        var v = "?";
        try {
            var m = mc.screen.getMenu();
            var st = m.getSlot(i).getItem();
            var idS = (st == null || st.isEmpty()) ? "empty" : csId(st);
            v = idS + "#" + csNum(function () { return m.priceOf(i); })
                + "#" + (m.isDiscounted(i) ? 1 : 0) + "#" + (m.isSold(i) ? 1 : 0);
        } catch (e) { v = "?"; }
        out.push(v);
    }
    return out.join(",");
}

/** 屏内探针状态一行（sel/drag/view/ready/hour 全是 CardShopScreen 的公开 getter ⇒ 读到的=画出来的） */
function csScreen(mc, tag) {
    var scr = mc.screen;
    var menu = null;
    try { menu = scr.getMenu(); } catch (eM) { menu = null; }
    var o = [];
    function t(n, fn) {
        try { var v = fn(); o.push(n + "=" + ((v === null || v === undefined) ? "null" : ("" + v))); }
        catch (e) { o.push(n + "=EX"); }
    }
    t("promo", function () { return menu.isPromotionActive() ? 1 : 0; });
    t("cd", function () { return menu.transferCooldownSeconds(); });
    t("used", function () { return menu.isTransferUsedThisSession() ? 1 : 0; });
    t("sel", function () { return scr.selectedCardIndex(); });
    t("drag", function () { return scr.draggingCardIndex(); });
    t("view", function () { return scr.transferViewVisible() ? 1 : 0; });
    t("ready", function () { return scr.transferReady() ? 1 : 0; });
    t("hour", function () { return scr.hourglassFrame(); });
    t("denyT", function () { return ClientState_CLASS.denyTicks(); });
    t("denyRed", function () { return ClientState_CLASS.denyBlinkRed() ? 1 : 0; });
    t("denyR", function () { return ClientState_CLASS.denyReason(); });
    t("sold", function () { return csSoldCsv(mc); });
    cs("evt=scr:" + tag + ":" + o.join(":"));
}

/** 任务V §1.3-D5：货架**形状**读数 —— 格数 + 逐格 id@售价 + 与上一次读数的对照。
 *  一次落一行，三个判据齐全：n（格数，证「+1 团队格 / +2 促销格」）、same（前 prevN 格逐格全等 ⇒
 *  证「不换批 / 原牌不动」）、sameHour（沙漏帧与上一次相同 ⇒ 证「计时器不动」）。 */
var csShapePrev = null;                 // [n, [逐格 "i=id@price", …], hour]
function csShape(mc, tag) {
    var m = null;
    try { m = mc.screen.getMenu(); } catch (e) { m = null; }
    var n = 0; var csv = [];
    for (var i = 0; i < 6; i++) {
        var id = "-"; var pr = -1;
        try {
            var st = m.getSlot(i).getItem();
            if (st != null && !st.isEmpty()) { n++; id = csId(st); }
        } catch (e1) { id = "<ex>"; }
        try { pr = m.priceOf(i); } catch (e2) { pr = -1; }
        var sd = -1;
        try { sd = m.isSold(i) ? 1 : 0; } catch (e3) { sd = -1; }
        // 任务V ①：售罄位也进对照串 ⇒「补差不动已售状态」有机器判据
        csv.push(i + "=" + id + "@" + pr + "#" + sd);
    }
    var same = -1; var sameHour = -1; var prevN = -1;
    if (csShapePrev != null) {
        prevN = csShapePrev[0]; same = 1;
        for (var k = 0; k < prevN; k++) { if (csv[k] !== csShapePrev[1][k]) { same = 0; break; } }
    }
    var hour = csNum(function () { return mc.screen.hourglassFrame(); });
    if (csShapePrev != null) { sameHour = (hour === csShapePrev[2]) ? 1 : 0; }
    cs("evt=shape:" + tag + ":n=" + n + ":prevN=" + prevN + ":same=" + same + ":sameHour=" + sameHour
        + ":hour=" + hour + ":ct=" + csTick + ":csv=[" + csv.join(",") + "]");
    csShapePrev = [n, csv, hour];
}

/** 货架第 i 格是否已售（窗口实测里判「有没有买成」） */
function csShelfSold(mc, i) { try { return mc.screen.getMenu().isSold(i) ? 1 : 0; } catch (e) { return -1; } }

/** 物品栏 36 格非空清单（证明「双击快速移入」真的落进背包） */
function csInvDump(mc, tag) {
    var n = 0; var s = "";
    for (var i = 0; i < 36; i++) {
        var it = null;
        try { var st = mc.player.getInventory().getItem(i); if (st != null && !st.isEmpty()) { it = csId(st) + "x" + csCnt(st); } } catch (e) { it = null; }
        if (it == null) { continue; }
        n++; s += (s.length > 0 ? "," : "") + i + ":" + it;
    }
    cs("evt=inv:" + tag + ":n=" + n + ":items=" + s);
}

/** 客户端自截图（§5④）：
 *  ① Screenshot.takeScreenshot(rt) 抓当前帧做**像素计数**（余额数字区红/白像素 ⇒ 机器可判「确实抓到红帧」）；
 *  ② Screenshot.grab(mc.gameDirectory, rt, fn) 把同一帧**写成 PNG**，自动落 run/<版本>/screenshots/。
 *  ⚠️ 不能走 java.io.File + NativeImage#writeToFile：KubeJS 类过滤器整段拒绝 java.io/java.nio
 *     （Java.loadClass("java.io.File") 返回 null ⇒ 实测 err=…|wr:TypeError: null is not a function）。
 *     grab 的三参形式（File 由 mc.gameDirectory 提供，无需自己 new）是仓库内 astral_client_diag.js:128 已验证的写法。
 *  ⚠️ 不用 Screenshot.grab(…, null)：consumer 为 null 会在 event.isCanceled() 分支 NPE 崩渲染线程 ⇒ 传空函数。
 *  无论成败都落一行 evt=shot（含 redPx/whitePx/red/grab/err）⇒ 用例可断言「确实抓到过红帧且 PNG 真写了」。 */
function csShot(mc, what, step) {
    csShotSeq++;
    var name = csShotTag + "_" + what + "_" + csShotSeq + "_s" + step + ".png";   // 前缀由场景决定（任务M = t88）
    var redPx = -1; var whitePx = -1; var amtPx = -1; var err = "-"; var grab = "-"; var dw = -1; var dh = -1;
    var img = null;
    try {
        if (Screenshot_CLASS == null) { err = "clsnull"; }
        else {
            img = Screenshot_CLASS.takeScreenshot(mc.getMainRenderTarget());
            dw = img.getWidth(); dh = img.getHeight();
            try {
                var gs = mc.getWindow().getGuiScale();
                var gx = csNum(function () { return mc.screen.getGuiLeft(); });
                var gy = csNum(function () { return mc.screen.getGuiTop(); });
                // 余额数字区：CardShopScreen BALANCE_X=11 / BALANCE_TEXT_RIGHT=90 / BALANCE_TEXT_DY=9
                //  ⇒ 右对齐锚点 x=leftPos+101、墨迹行 y=topPos+154..163。取 x∈[+56,+108]、y∈[+150,+168]。
                //  ⚠️ 旧实现只扫到 +95，正好切掉右对齐数字（实测白像素只剩 16、红像素恒 0）。
                var x0 = Math.max(0, Math.round((gx + 56) * gs)); var x1 = Math.min(dw, Math.round((gx + 108) * gs));
                var y0 = Math.max(0, Math.round((gy + 150) * gs)); var y1 = Math.min(dh, Math.round((gy + 168) * gs));
                var red = 0; var white = 0;
                for (var y = y0; y < y1; y++) {
                    for (var x = x0; x < x1; x++) {
                        var px = img.getPixelRGBA(x, y);
                        var r = px & 255; var g = (px >> 8) & 255; var b = (px >> 16) & 255;
                        // getPixelRGBA 返回 ABGR 打包 int ⇒ r=低8位; COLOR_RED=0xFFFF5555
                        if (r > 180 && g < 110 && b < 110) { red++; }
                        else if (r > 200 && g > 200 && b > 200) { white++; }
                    }
                }
                redPx = red; whitePx = white;
                // 任务P §2.4：转账面板「金额显示框」区域白像素计数（证明金额数字基色 = 白，且框内没有输入框光标）
                //  金额框设计坐标 = 面板内 (TP_AMOUNT_X 109, TP_AMOUNT_Y 46) 32x14，面板原点 (TP_X 73, TP_Y 2)
                //  ⇒ GUI 相对 (182,48)-(214,62)；数字由 drawCenteredString 画在框内 y=49（面板相对）
                var ax0 = Math.max(0, Math.round((gx + 182) * gs)); var ax1 = Math.min(dw, Math.round((gx + 214) * gs));
                var ay0 = Math.max(0, Math.round((gy + 48) * gs)); var ay1 = Math.min(dh, Math.round((gy + 62) * gs));
                var amt = 0;
                for (var ya = ay0; ya < ay1; ya++) {
                    for (var xa = ax0; xa < ax1; xa++) {
                        var pxa = img.getPixelRGBA(xa, ya);
                        var ra = pxa & 255; var ga = (pxa >> 8) & 255; var ba = (pxa >> 16) & 255;
                        if (ra > 200 && ga > 200 && ba > 200) { amt++; }
                    }
                }
                amtPx = amt;
            } catch (e2) { err = "px:" + ("" + e2).substring(0, 90); }
            try { Screenshot_CLASS.grab(mc.gameDirectory, mc.getMainRenderTarget(), function (c) { }); grab = "ok"; }
            catch (e3) { grab = "ex:" + ("" + e3).substring(0, 90); }
        }
    } catch (e) { err = "shot:" + ("" + e).substring(0, 120); }
    try { if (img != null) { img.close(); } } catch (e4) { }
    var dred = csNum(function () { return ClientState_CLASS.denyBlinkRed() ? 1 : 0; });
    // gs/gx/gy 一并落盘：事后用 Python 从全屏 PNG 里精确裁出 GUI 区域做像素取证（不需要猜 guiScale）
    var gsV = csNum(function () { return mc.getWindow().getGuiScale(); });
    var gxV = csNum(function () { return mc.screen.getGuiLeft(); });
    var gyV = csNum(function () { return mc.screen.getGuiTop(); });
    cs("evt=shot:step=" + step + ":what=" + what + ":file=" + name + ":px=" + dw + "x" + dh
        + ":redPx=" + redPx + ":whitePx=" + whitePx + ":amtPx=" + amtPx
        + ":gs=" + gsV + ":gx=" + gxV + ":gy=" + gyV + ":red=" + dred + ":grab=" + grab + ":err=" + err);
}

/** 779：任务L GUI 主序列（单击只选中 / 空位无效 / 双击移入 / 余额不足 / 沙漏倒序 5→1→0 与就绪保持 /
 *  购买后重新计时 / 轮询中转账被拒 / 转账面板与沙漏一致 / 大酬宾只补差额牌（不刷架、不换批、沙漏不动）/ 双击判定窗口实测 / 截图）。
 *  每步都落机器行，坐标全部相对 GUI 左上（csClick 内部加 getGuiLeft/Top）。 */
function csRunPoll(mc, step) {
    if (step === 2) {
        cs("evt=poll_open:" + csState(mc)); csScreen(mc, "open"); csInvDump(mc, "open"); csShot(mc, "poll_open", step);
    } else if (step === 6) {
        csClick(mc, 29, 58, "c0_single", step);                  // 单击卡框0：只应选中
    } else if (step === 8) {
        csRelease(mc, 29, 58, "c0_single", step);
    } else if (step === 10) {
        csScreen(mc, "after_single_c0");                          // 期望 sel=0
        csShot(mc, "poll_sel0", step);
    } else if (step === 14) {
        csClick(mc, 213, 58, "c4_empty", step);                   // 空牌位(frame4 = 7+46*4=191..235)：点击应无效
    } else if (step === 16) {
        csRelease(mc, 213, 58, "c4_empty", step);
    } else if (step === 18) {
        csScreen(mc, "after_empty_c4");                           // 期望 sel 仍是 0（空位不改变选中）
    } else if (step === 22) {
        csCmd(mc, "astralshop bal CD0 1", "bal_1", step);          // 余额压到 1 ⇒ 买不起
    } else if (step === 30) {
        cs("evt=poll_bal1:" + csState(mc));
    } else if (step === 34) {
        csClick(mc, 29, 58, "c0_dbl_p1", step);                    // 双击第 1 次按下
    } else if (step === 36) {
        csRelease(mc, 29, 58, "c0_dbl_p1", step);
    } else if (step === 38) {
        csClick(mc, 29, 58, "c0_dbl_p2", step);                    // 距上次按下 4t=200ms < 280ms ⇒ 判双击
    } else if (step === 40) {
        csRelease(mc, 29, 58, "c0_dbl_p2", step);
    } else if (step === 44) {
        csScreen(mc, "after_dbl_nocoin");                          // 期望 denyR=0(余额不足)、denyT>0
    } else if (step === 48) {
        cs("evt=poll_shelf_after_nocoin:" + csState(mc));          // 期望货架不变(未售出)、余额仍 1
        csShot(mc, "poll_nocoin", step);
    } else if (step === 52) {
        csScreen(mc, "deny_phase_mid");
    } else if (step === 58) {
        csCmd(mc, "astralshop bal CD1 999", "bal_999", step);
    } else if (step === 66) {
        cs("evt=poll_bal999:" + csState(mc));
        // ⚠️ 必须在购买**之前**记下货架0的 id：双击买走后槽位会变成 empty，买完再读就只能读到 empty(实测 bug)
        try { csShelf0Id = csId(mc.screen.getMenu().getSlot(0).getItem()); } catch (eS0) { csShelf0Id = "?"; }
        cs("evt=qm_pre:card=" + csShelf0Id);
    } else if (step === 70) {
        csClick(mc, 29, 58, "c0_dbl2_p1", step);                   // 双击移入（余额够）
    } else if (step === 72) {
        csRelease(mc, 29, 58, "c0_dbl2_p1", step);
    } else if (step === 74) {
        csClick(mc, 29, 58, "c0_dbl2_p2", step);
    } else if (step === 76) {
        csRelease(mc, 29, 58, "c0_dbl2_p2", step);
    } else if (step === 84) {
        cs("evt=poll_after_quickmove:" + csState(mc));             // 期望 shelf0(sold) + cd>0 + used=1 + bal 减少
        try {
            var cardId = (csShelf0Id !== "?" && csShelf0Id !== "empty") ? csShelf0Id : csId(mc.screen.getMenu().getSlot(0).getItem());
            var has = 0;
            for (var i = 0; i < 36; i++) {
                var st = mc.player.getInventory().getItem(i);
                if (st != null && !st.isEmpty() && csId(st) === cardId) { has = 1; break; }
            }
            cs("evt=qm_check:card=" + cardId + ":invHas=" + has);   // 期望 invHas=1（卡真的进了背包）
        } catch (eQ) { cs("evt=qm_check:ex=" + eQ); }
        csInvDump(mc, "quickmove");
    } else if (step === 88) {
        csScreen(mc, "cycle_started");                             // 期望 hour=4 ready=0（周期刚起 ⇒ 60s ⇒ 4 档）
        csShot(mc, "poll_hour5_shot", step);
    } else if (step === 92) {
        csClick(mc, 64, 125, "xfer_btn_cooling", step);            // 轮询进行中 ⇒ 应打不开面板
    } else if (step === 96) {
        csScreen(mc, "xfer_btn_cooling");                          // 期望 view=0 ready=0
    } else if (step === 100) {
        csCmd(mc, "astralshop txtry TH0 Bot1 10", "tx_during_cycle", step);  // 轮询中转账 ⇒ 期望 applied=0
    } else if (step === 112) {
        csCmd(mc, "astralshop cycleset CY4 55", "cyc55", step);    // 剩 55s ⇒ 沙漏 4（4 档 = 每档 15s）
    } else if (step === 122) {
        csScreen(mc, "hour_expect4");
    } else if (step === 126) {
        csCmd(mc, "astralshop cycleset CY3 42", "cyc42", step);    // 42s ⇒ 3（ceil(42/15)=3）
    } else if (step === 136) {
        csScreen(mc, "hour_expect3");
    } else if (step === 140) {
        csCmd(mc, "astralshop cycleset CY2 18", "cyc18", step);    // 18s ⇒ 2
    } else if (step === 150) {
        csScreen(mc, "hour_expect2");
    } else if (step === 154) {
        csCmd(mc, "astralshop cycleset CY1 6", "cyc6", step);      // 6s ⇒ 1
    } else if (step === 164) {
        csScreen(mc, "hour_expect1");
    } else if (step === 168) {
        csCmd(mc, "astralshop cycleset CY0 0", "cyc0", step);      // 到 0 ⇒ 就绪 + 服务端沿触发重 roll
    } else if (step === 186) {
        csScreen(mc, "hour_expect0_ready");                        // 期望 hour=0 ready=1
        cs("evt=poll_rerolled:" + csState(mc));                    // 期望货架已重 roll（sold 位清空）
        csShot(mc, "poll_ready", step);
    } else if (step === 200) {
        csScreen(mc, "ready_hold_200");
    } else if (step === 260) {
        csScreen(mc, "ready_hold_260");                            // 期望仍未重新计时（就绪保持）
    } else if (step === 264) {
        csClick(mc, 64, 125, "xfer_btn_ready", step);              // 就绪 ⇒ 面板应打开
    } else if (step === 272) {
        csScreen(mc, "xfer_view_open");                            // 期望 view=1
        csShot(mc, "poll_xfer_view", step);
    } else if (step === 276) {
        csClick(mc, 120, 40, "xfer_row0", step);   // 面板原点 (73,2)：行区 y∈[31,49]
    } else if (step === 280) {
        csClick(mc, 194, 84, "xfer_limit10", step); // 4 档行 r1=[77,92]（LIMITS[1]=10）
    } else if (step === 284) {
        csClick(mc, 198, 37, "xfer_confirm", step);  // 确认键 y∈[26,48]
    } else if (step === 300) {
        cs("evt=poll_after_xfer:" + csState(mc));                  // 期望 used=1 + cd>0（重新计时）
    } else if (step === 304) {
        csClick(mc, 190, 13, "back_btn", step);      // 返回键 y∈[5,21]
    } else if (step === 310) {
        csScreen(mc, "back_to_main");                              // 期望 view=0 hour=4（转账后重新计时 ⇒ 60s ⇒ 4 档）
        csShot(mc, "poll_back_hour5", step);
    } else if (step === 314) {
        csShape(mc, "promo_before");                               // 任务V D5：大酬宾前基线（期望 n=3）
        csCmd(mc, "astralshop promo PR0 1200", "promo", step);     // 任务V D5 订正：大酬宾**只补差额牌**（+2 促销格），不刷架、不换批、**沙漏不动**
    } else if (step === 328) {
        csScreen(mc, "promo_active");                              // 期望 promo=1 ready=0 hour 不变（U′：计时器不归零 ⇒ 不就绪）
        csShape(mc, "promo_after");                                // 期望 n=5 prevN=3 same=1 sameHour=1（只多 2 格、原 3 格原样、沙漏未动）
        cs("evt=poll_promo:" + csState(mc));
        csShot(mc, "poll_promo", step);
    } else if (step === 330) {
        csCmd(mc, "astralshop menuraw PR1", "promo_menu", step);   // 服务端权威：menuraw 只读不自动开店
    } else if (step === 340) {
        csScreen(mc, "promo_ready_hold");
    } else if (step === 344) {
        csInvDump(mc, "final");
        csCmd(mc, "astralshop inv IV1", "inv_dump", step);         // 服务端权威物品栏清单(AP_IV1_INV)
    } else if (step === 348) {
        csWinBal = csNum(function () { return WalletState_CLASS.balance(); });
        csClick(mc, 75, 58, "c1_single_win", step);                // 双击窗口实测：先单击卡框1（基准）
    } else if (step === 350) {
        csRelease(mc, 75, 58, "c1_single_win", step);
    } else if (step === 358) {
        csClick(mc, 75, 58, "c1_500ms", step);                     // 距上次按下 10t=500ms > 280ms ⇒ 只算单击（不该买）
    } else if (step === 360) {
        csRelease(mc, 75, 58, "c1_500ms", step);
    } else if (step === 362) {
        var b5 = csNum(function () { return WalletState_CLASS.balance(); });
        cs("evt=win_500ms:dt=500ms:delta=" + (b5 - csWinBal) + ":sold1=" + csShelfSold(mc, 1)
            + ":sel=" + csNum(function () { return mc.screen.selectedCardIndex(); }) + ":" + csState(mc));
    } else if (step === 366) {
        csClick(mc, 75, 58, "c1_400ms", step);                     // 距上次按下 8t=400ms > 280ms ⇒ 仍是单击（window 外）
    } else if (step === 368) {
        csRelease(mc, 75, 58, "c1_400ms", step);
    } else if (step === 370) {
        csClick(mc, 75, 58, "c1_200ms_p2", step);                  // 距上次按下 4t=200ms < 280ms ⇒ 判双击买下（window 内）
    } else if (step === 372) {
        csRelease(mc, 75, 58, "c1_200ms_p2", step);
    } else if (step === 382) {
        var b2 = csNum(function () { return WalletState_CLASS.balance(); });
        cs("evt=win_200ms:dt=200ms:delta=" + (b2 - csWinBal) + ":sold1=" + csShelfSold(mc, 1)
            + ":sel=" + csNum(function () { return mc.screen.selectedCardIndex(); }) + ":" + csState(mc));
        csInvDump(mc, "win200");
        csShot(mc, "poll_win200", step);
    } else if (step === 386) {
        csScreen(mc, "final");
    }
}

/** 780：余额不足 ⇒ 金额数字红白闪烁 3 次（§4）。每 tick 记相位(ticks/red)，首个 30t 周期即铁证；
 *  每 20t 重放一次「同 tick 双击」把闪烁窗口拉长到 ~9s（供测试台截图）；每个周期内多次自截图（含红像素计数）。 */
function csRunDeny(mc, step) {
    if (step === 2) {
        cs("evt=deny_open:" + csState(mc)); csScreen(mc, "open"); csShot(mc, "deny_open", step);
    } else if (step === 6) {
        csCmd(mc, "astralshop bal DY0 1", "bal_1", step);
    } else if (step === 16) {
        cs("evt=deny_bal:" + csState(mc));
    } else if (step >= 20 && step <= 190 && (step % 40) === 0) {
        // 同 tick 内 点-放-点-放：两次按下相隔 0ms ⇒ 必判双击 ⇒ 服务端按余额不足拒绝 ⇒ 重启 30t 闪烁
        csDenyRearms++;
        csCmd(mc, "astralshop bal DY0 1", "rearm_bal", step);
        csClick(mc, 29, 58, "deny_p1", step);
        csRelease(mc, 29, 58, "deny_p1", step);
        csClick(mc, 29, 58, "deny_p2", step);
        csRelease(mc, 29, 58, "deny_p2", step);
    }
    // 逐 tick 相位（30..26 红 / 25..21 白 / 20..16 红 / 15..11 白 / 10..6 红 / 5..1 白）
    if (step >= 20 && step <= 230) {
        var dt = csNum(function () { return ClientState_CLASS.denyTicks(); });
        var dr = csNum(function () { return ClientState_CLASS.denyBlinkRed() ? 1 : 0; });
        if (dt > 0) { cs("evt=deny:t=" + step + ":ticks=" + dt + ":red=" + dr); }
    }
    // 自截图：只在**相位翻转**那一拍抓帧（红→白 / 白→红）⇒ 天然得到红白交替的帧序列，
    //  每帧都带 redPx 计数 ⇒ 「红帧 redPx>0」= §4 红白交替闪烁的像素级证据。上限 14 帧。
    {
        var dt2 = csNum(function () { return ClientState_CLASS.denyTicks(); });
        var dr2 = csNum(function () { return ClientState_CLASS.denyBlinkRed() ? 1 : 0; });
        if (step >= 20 && step <= 230 && dt2 > 0 && dr2 !== csDenyLastRed && csDenyShots < 14) {
            csDenyLastRed = dr2; csDenyShots++;
            csShot(mc, "deny", step);
        }
    }
}

/** 781：P0 对照重演 —— **故意**复刻旧版 janitor「60t 无条件关屏」，打出同格式 AP_SHOPCLOSE，
 *  证明探针抓得住这个失效模式（对照：778 静置窗口里到 440t 都不出现它）。 */
function csP0Reenact(mc, step) {
    if (step === 2) {
        cs("evt=p0_open:" + csState(mc)); csScreen(mc, "open");
    } else if (step === 30) {
        cs("evt=p0_alive30:" + csState(mc));
    } else if (step === 60) {
        csShopClose(mc, "p0_reenact_old_janitor", "csP0Reenact(legacy_janitor)", "reenact=60t_unconditional_close");
        cs("evt=p0_reclose:how=" + csCloseBy_(mc, "csP0Reenact"));
    }
}

/** opens==1 的脚本化序列（步号 = tick 计数，给服务端往返留时间） */
function csRunFirst(mc, step) {
    if (step === 4) {
        cs("evt=stateA:" + csState(mc));
        csSlot0Before = "<read>";
        csBalBefore = csNum(function () { return WalletState_CLASS.balance(); });
    } else if (step === 8) {
        // ⚠️ 必须在**同一 tick** 内 click→drag→release: CardShopScreen#containerTick(:364-368) 有「物理左键未按下 ⇒ dragIndex 复位」兜底,
        // 跨 tick 的合成手势会被它清掉(实测 v15: ret=1 但从不发载荷)。同 tick 时序与真人「按下-拖动-松开」经过的屏内代码完全一致。
        csDropDiag(mc, "before");                       // 起拖前: 空槽/屏内状态诊断
        csClick(mc, 29, 58, "card0_frame", step);       // 起拖第 1 个卡框(dragIndex=0)
        csDragSlot(mc, "invEmpty", step);               // 幽灵跟手到空物品栏槽
        csReleaseSlot(mc, "invEmpty", step);            // 同一 tick 松手 ⇒ 屏内发 CardShopBuyPayload
        csDropDiag(mc, "after");
    } else if (step === 10) {
        cs("evt=stateG_onetickdrag:" + csState(mc) + ":inv9=" + csInvItem(mc, 9));  // 同 tick 手势后立刻取状态
    } else if (step === 12) {
        cs("evt=stateA1_afterdrag:" + csState(mc));     // 拖拽后立刻取状态(判定拖拽是否结算)
    } else if (step === 14) {
        csDirectBuy(mc, 1, 1, step);                    // 直接发载荷: 货架1 -> 快捷栏1
    } else if (step === 18) {
        cs("evt=stateA2_afterdirect:" + csState(mc));   // 直接载荷后取状态
    } else if (step === 20) {
        cs("evt=stateB_afterbuy:" + csState(mc) + ":dst=" + csLastDst + ":dstItem=" + csInvItem(mc, csLastDst));
    } else if (step === 22) {
        // 货架0 已由同 tick 手势买走 ⇒ 这里不再发替代载荷, 只留观察点(证明手势结算落到了主物品栏首格)
        cs("evt=stateF_afterdrag:" + csState(mc) + ":inv9=" + csInvItem(mc, 9));
    } else if (step === 26) {
        csClick(mc, 64, 125, "transfer_btn", step);        // 进二级转账面板（服务端回发候选名单）
    } else if (step === 36) {
        cs("evt=stateC_panel:" + csState(mc));
    } else if (step === 40) {
        csClick(mc, 121, 59, "row0", step);                // 选中第 1 行候选
    } else if (step === 44) {
        csClick(mc, 187, 108, "limit10", step);            // 金额档 LIMITS[1]=10
    } else if (step === 48) {
        csClick(mc, 191, 61, "confirm", step);             // 确认转账
    } else if (step === 62) {
        cs("evt=stateD_afterxfer:" + csState(mc));
    } else if (step === 66) {
        csClick(mc, 190, 13, "back_btn", step);      // 返回键 y∈[5,21]            // 若有面板则退回主视图（主视图点此处无副作用）
    } else if (step === 70) {
        csClick(mc, 64, 125, "transfer_btn_again", step);  // 会话已用 ⇒ 不应进面板
    } else if (step === 76) {
        csClick(mc, 191, 61, "confirm_again", step);       // 不应产生第二次转账
    } else if (step === 86) {
        cs("evt=stateE_repeat:" + csState(mc));
    } else if (step === 90) {
        cs("evt=leaving:how=" + csClose(mc));              // 离开商店 ⇒ 服务端 removed() 顺延冷却
    }
}

/** 782：**自然** 60 秒轮询（不做任何 cycleset 干预）——证明「固定 1 分钟轮询、与开关界面无关、
 *  到期才刷架、刷完保持就绪」；hours 全部读服务端同步过来的 DataSlot(客户端无本地计时)。
 *  步号=客户端 tick：400≈20s / 800≈40s / 1160≈58s / 1250≈61s。
 *  ⚠️ 判据必须抗「外部真实鼠标输入」干扰（2026-10-09 第三轮实测到过：窗口内出现非探针的拖动购买，
 *     使 fp 全等判据 same=0 假失败）⇒ 改用 cleared(基线已售位被清=重 roll 铁证) + sameUnsold(只比仍未售格)。 */
function csNatCmp(mc, at) {
    var sold = csSoldCsv(mc);
    var idsNow = csShelfIds(mc);
    var cleared = 0;        // 基线里的「已售位」被清成未售 ⇒ **只可能**由重 roll（刷架）造成
    var sameUnsold = 1;     // 只比「当前仍未售」的格子：外部购买只会让该格变 sold（被跳过），重 roll 会换掉所有 id
    var diff = "";
    for (var i = 0; i < 6; i++) {
        var wasSold = (csNatSoldCsv.length === 6 && csNatSoldCsv.charAt(i) === "1");
        var isSold = (sold.length === 6 && sold.charAt(i) === "1");
        if (wasSold && !isSold) { cleared = 1; }
        if (!isSold && idsNow[i] !== csNatIds[i]) { sameUnsold = 0; diff += (diff.length ? "," : "") + i; }
    }
    var cd = csNum(function () { return mc.screen.getMenu().transferCooldownSeconds(); });
    return "evt=nat_cmp:at=" + at + ":sold=" + sold + ":base=" + csNatSoldCsv
         + ":cleared=" + cleared + ":sameUnsold=" + sameUnsold + ":diff=" + (diff.length ? diff : "-") + ":cd=" + cd;
}

function csRunNat(mc, step) {
    if (step === 2) {
        cs("evt=nat_open:" + csState(mc)); csScreen(mc, "nat_open"); csShot(mc, "nat_open", step);
    } else if (step === 6) {
        csCmd(mc, "astralshop cycleset NA0 60", "cyc60", step);    // 只为把周期对齐到整 60s；之后完全靠自然流逝
    } else if (step === 10) {
        csScreen(mc, "nat_pre");                                   // 期望 hour=4 ready=0（周期刚对齐 ⇒ 60s）
    } else if (step === 20) {
        // 先双击买下货架 0（两次按下相距 4t=200ms < 280ms 判定窗口）⇒ sold[0]=1，给「到 0 才刷架」造确定性判据
        csClick(mc, 29, 58, "nat_buy_p1", step);
    } else if (step === 22) {
        csRelease(mc, 29, 58, "nat_buy_p1", step);
    } else if (step === 24) {
        csClick(mc, 29, 58, "nat_buy_p2", step);
    } else if (step === 26) {
        csRelease(mc, 29, 58, "nat_buy_p2", step);
    } else if (step === 40) {
        csNatSoldCsv = csSoldCsv(mc); csNatFp = csShelfFp(mc); csNatIds = csShelfIds(mc);
        csScreen(mc, "nat_t0");                                    // 期望 hour=4 ready=0 sold=100000（剩≈59s）
        cs("evt=nat_t0_sold:sold=" + csNatSoldCsv + ":fp=" + csNatFp);
    } else if (step === 400) {
        csScreen(mc, "nat_t20"); cs(csNatCmp(mc, 400));             // 期望 hour=3 same=1（剩≈41s；未到 0 不刷架）
    } else if (step === 800) {
        csScreen(mc, "nat_t40"); cs(csNatCmp(mc, 800));             // 期望 hour=2 same=1
    } else if (step === 1160) {
        csScreen(mc, "nat_t58"); cs(csNatCmp(mc, 1160));            // 期望 hour=1 same=1
    } else if (step === 1250) {
        csScreen(mc, "nat_t61"); cs(csNatCmp(mc, 1250));            // 期望 hour=0 ready=1 same=0（到 0 才刷架）
        cs("evt=nat_rerolled:" + csState(mc));                     // 期望货架已重 roll
        csShot(mc, "nat_ready", step);
    } else if (step === 1290) {
        csScreen(mc, "nat_hold");                                  // 期望仍 ready=1 hour=0（就绪保持）
    }
}

// ══════════════════ 任务M 新增（2026-10-09）：护栏武装 / 真实屏幕事件派发 / 场景 783·784·785 ══════════════════

/** 探测 run/<版本>/.probe_armed 标记文件。
 *  ⚠️ KubeJS 类过滤器整段拒绝 java.io/java.nio（Java.loadClass("java.io.File") 返回 null），
 *     但 mc.gameDirectory 本身已是 File 对象 ⇒ toPath().resolve(...) 后交给全局 JsonIO（KubeJS 自带绑定）。
 *     返回 yes/no/no_jsonio/no_mcdir/EX:…（探测结果原样记进日志，便于判「哪条通道生效」）。 */
function csArmFileProbe(mc) {
    try {
        if (JsonIO_CLASS == null) { return "no_jsonio"; }
        var dir = mc.gameDirectory;
        if (dir == null) { return "no_mcdir"; }
        var p = dir.toPath().resolve(".probe_armed");
        return JsonIO_CLASS.isRegularFile(p) ? "yes" : "no";
    } catch (e) { return "EX:" + ("" + e).substring(0, 60); }
}

/** 每 tick 求值武装状态（只在 false→true / true→false 翻转时落一行日志）。
 *  未武装 ⇒ 主循环除只读诊断外立即 return ⇒ 探针不可能改变任何游戏状态。 */
function csRefreshArm(mc) {
    var bal = csNum(function () { return WalletState_CLASS.balance(); });
    if (csArmChecked < 0 || (csTick - csArmChecked) >= 40) { csArmChecked = csTick; csArmFile = csArmFileProbe(mc); }
    var band = (bal >= CS_ARM_LO && bal <= CS_ARM_HI);
    var file = (csArmFile === "yes");
    if (!csLive) {
        if (band || file) {
            csLive = true;
            csArmWhy = band ? ("balance_band:" + bal) : "armed_file";
            cs("evt=arm:why=" + csArmWhy + ":file=" + csArmFile + ":tick=" + csTick + ":bal=" + bal);
        }
    } else if (!file && !band && (bal < CS_KEEP_LO || bal > CS_KEEP_HI) && mc.screen == null) {
        csLive = false; csArmWhy = "disarmed:" + bal;
        cs("evt=disarm:why=" + csArmWhy + ":tick=" + csTick + ":file=" + csArmFile);
    }
    return csLive;
}

/** 心跳开关（任务N 诊断）：只在「商店屏打开」或「本探针已在该屏武装」时输出心跳，
 *  关屏后立即静音 ⇒ 既能定位「csStep 冻结」类故障，又不会在挂机时刷屏。 */
function csHbOn(mc) {
    if (csArmed) { return true; }
    try { return (mc.screen != null) && (("" + mc.screen).indexOf("CardShopScreen") >= 0); } catch (e) { return false; }
}

/** 光标携带物（原版拖拽机制 AbstractContainerMenu#getCarried）。
 *  货架 ShelfSlot 是隐藏槽(-10000)且 mayPickup/mayPlace=false ⇒ 真人拖拽时这里应恒为 empty：
 *  这正是「真人手势必须由 CardShopScreen 自绘拖拽承接」的证据。 */
function csCarried(mc) {
    try {
        var m = mc.player.containerMenu;
        if (m == null) { return "nomenu"; }
        var st = m.getCarried();
        if (st == null) { return "null"; }
        return (st.isEmpty() ? "empty" : (csId(st) + "x" + csCnt(st)));
    } catch (e) { return "EX:" + ("" + e).substring(0, 40); }
}

/** 卡框 i 中心（**GUI 相对坐标**）：FRAME_X0=7 + FRAME_PITCH 46*i + FRAME_W/2 22 = 29+46i；FRAME_Y=28 + 61/2 ≈ 58 */
function csCardCenter(mc, i) { return [29 + 46 * i, 58]; }

/** 物品栏 36 格非空清单（买前/买后逐格对照用） */
function csInvCsv(mc) {
    var s = "";
    for (var i = 0; i < 36; i++) {
        var it = null;
        try { var st = mc.player.getInventory().getItem(i); if (st != null && !st.isEmpty()) { it = csId(st) + "x" + csCnt(st); } } catch (e) { it = null; }
        if (it == null) { continue; }
        s += (s.length > 0 ? "," : "") + i + ":" + it;
    }
    return s;
}

/** 派发**真实屏幕事件**到 CardShopScreen（任务M §1.4）：click/drag/release 三个入口正是
 *  MouseHandler 收到真实鼠标后调用的方法（AbstractContainerScreen 覆写），坐标 = GUI 相对坐标。
 *  每次落一行 evt=m_<kind>（含 ret / sel / dragNow / carried）⇒ 修前修后可直接逐行对照。 */
function csEvt(mc, kind, relX, relY, what, step) {
    var scr = mc.screen;
    var gx = csNum(function () { return scr.getGuiLeft(); });
    var gy = csNum(function () { return scr.getGuiTop(); });
    var absX = gx + relX; var absY = gy + relY;
    var dx = (csPrevX < 0) ? 0 : (absX - csPrevX);
    var dy = (csPrevY < 0) ? 0 : (absY - csPrevY);
    var ret = "?";
    try {
        if (kind === "click") { ret = scr.mouseClicked(absX, absY, 0) ? 1 : 0; }
        else if (kind === "drag") { ret = scr.mouseDragged(absX, absY, 0, dx, dy) ? 1 : 0; }
        else { ret = scr.mouseReleased(absX, absY, 0) ? 1 : 0; }
    } catch (e) { ret = "EX:" + ("" + e).substring(0, 70); }
    csPrevX = absX; csPrevY = absY;
    cs("evt=m_" + kind + ":step=" + step + ":what=" + what + ":rel=" + relX + "," + relY + ":abs=" + absX + "," + absY
        + ":d=" + dx + "," + dy + ":ret=" + ret
        + ":sel=" + csNum(function () { return scr.selectedCardIndex(); })
        + ":dragNow=" + csNum(function () { return scr.draggingCardIndex(); })
        + ":carried=" + csCarried(mc));
    return ret;
}

/** 手势观察器（只读）：**真人**鼠标事件不会经过探针，但它的效果会出现在屏内公开 getter 上。
 *  逐 tick 比对 sel/dragNow/carried/sold，任一变化就落一行 evt=m_obs：
 *  修前真人拖拽的形态 = dragNow 由 -1 跳到卡位后**下一 tick 立刻回 -1**、sold 位不变；
 *  修后 = dragNow 保持到松手、松手后 sold 位变 1。只在已武装时执行（未武装零输出）。 */
function csObs(mc) {
    var isShop = 0;
    try { isShop = (mc.screen != null && (mc.screen instanceof ShopScreen_CLASS)) ? 1 : 0; } catch (e) { return; }
    if (isShop !== 1) { return; }
    var sel = csNum(function () { return mc.screen.selectedCardIndex(); });
    var dr = csNum(function () { return mc.screen.draggingCardIndex(); });
    var ca = csCarried(mc);
    var sold = csSoldCsv(mc);
    if (sel !== csObsSel || dr !== csObsDrag || ca !== csObsCarried || sold !== csObsSold) {
        cs("evt=m_obs:tick=" + csTick + ":sel=" + sel + ":dragNow=" + dr + ":carried=" + ca + ":sold=" + sold
            + ":prevSel=" + csObsSel + ":prevDrag=" + csObsDrag + ":prevCarried=" + csObsCarried + ":prevSold=" + csObsSold);
        csObsSel = sel; csObsDrag = dr; csObsCarried = ca; csObsSold = sold;
    }
}

/** 几何机器行（只读）：给 OS 级真鼠标脚本换算像素坐标（SetCursorPos 用窗口客户区像素）。
 *  同时报 live cursor（MouseHandler.xpos/ypos = 窗口客户区像素）⇒ runner 可自校准。 */
function csGeo(mc, tag) {
    var out = [];
    function t(n, fn) { try { var v = fn(); out.push(n + "=" + ((v === null || v === undefined) ? "null" : ("" + v))); } catch (e) { out.push(n + "=EX"); } }
    t("gs", function () { return mc.getWindow().getGuiScale(); });
    t("win", function () { return mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight(); });
    t("scr", function () { return mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight(); });
    t("gx", function () { return mc.screen.getGuiLeft(); });
    t("gy", function () { return mc.screen.getGuiTop(); });
    t("card0rel", function () { var c = csCardCenter(mc, 0); return c[0] + "," + c[1]; });
    t("card0abs", function () { var c = csCardCenter(mc, 0); var gx = csNum(function () { return mc.screen.getGuiLeft(); }); var gy = csNum(function () { return mc.screen.getGuiTop(); }); return (gx + c[0]) + "," + (gy + c[1]); });
    t("card0px", function () { var c = csCardCenter(mc, 0); var gx = csNum(function () { return mc.screen.getGuiLeft(); }); var gy = csNum(function () { return mc.screen.getGuiTop(); }); var g = mc.getWindow().getGuiScale(); return Math.round((gx + c[0]) * g) + "," + Math.round((gy + c[1]) * g); });
    t("dropSlot", function () { var t2 = csEmptyInvCell(mc); return (t2 == null) ? -1 : t2[0]; });
    t("dropAbs", function () { var t2 = csEmptyInvCell(mc); return (t2 == null) ? "none" : (t2[1] + "," + t2[2]); });
    t("dropPx", function () { var t2 = csEmptyInvCell(mc); if (t2 == null) { return "none"; } var g = mc.getWindow().getGuiScale(); return Math.round(t2[1] * g) + "," + Math.round(t2[2] * g); });
    t("cardId", function () { return csShelfIds(mc)[0]; });
    t("price", function () { return mc.screen.getMenu().priceOf(0); });
    t("bal", function () { return WalletState_CLASS.balance(); });
    t("cursorPx", function () { return Math.round(mc.mouseHandler.xpos()) + "," + Math.round(mc.mouseHandler.ypos()); });
    t("armed", function () { return csLive ? 1 : 0; });
    // 任务M 诊断（只读）：OS 级真鼠标拖拽的现场证据
    //   act=窗口是否 active（MouseHandler.handleAccumulatedMovement 只在 active 时派发 mouseDragged）
    //   focus=GLFW 焦点 / btn=原版 isLeftPressed（1.21.1 开屏时恒 false，§8 根因）/ drag|sel=手势状态
    t("act", function () { return mc.isWindowActive() ? 1 : 0; });
    t("focus", function () { return mc.getWindow().isFocused() ? 1 : 0; });
    t("btn", function () { return mc.mouseHandler.isLeftPressed() ? 1 : 0; });
    t("sel", function () { return mc.screen.selectedCardIndex(); });
    t("drag", function () { return mc.screen.draggingCardIndex(); });
    t("carried", function () { var s = mc.player.containerMenu.getCarried(); return (s == null || s.isEmpty()) ? "empty" : ("" + s.getCount()); });
    cs("evt=geo:" + tag + ":" + out.join(":"));
}

/** 外来容器屏兜底关闭
 *  只认 MerchantScreen(绝不能关 ChatScreen —— 聊天注入全靠它)。 */
function csForeignJanitor(mc) {
    // 任务M 护栏（§1.3）：未武装时绝不关任何屏幕（本探针曾是 P0「商店几秒后自己关」的元凶）
    if (!csLive) { return; }
    try {
        var s = mc.screen;
        if (s == null) return;
        var n = "" + s;
        if (n.indexOf("MerchantScreen") < 0) return;
        cs("evt=foreign:cls=MerchantScreen:how=" + csCloseBy_(mc, "csForeignJanitor"));
    } catch (e) { cs("evt=foreign:ex=" + e); }
}

// ══════════════════ 任务M 场景 783 / 784 / 785（2026-10-09） ══════════════════

/** 783：**跨 tick** 的真实屏幕事件拖拽购买（§8 主证）。
 *  修前的失效链（源码级确证）：屏幕开着时 MouseHandler.isLeftPressed 恒为 false（1.21.1
 *  MouseHandler:119 只在 screen==null 时才更新它），而 CardShopScreen#containerTick 有兜底
 *  「!isLeftPressed() ⇒ dragIndex=-1」⇒ 真人按下后 mouseDragged 刚把 dragIndex 设上，
 *  **下一 tick 立刻被清成 -1**（且 pressedIndex 已被置 -1，无法再升格）⇒ 松手时 dragIndex<0
 *  ⇒ 一个购买载荷都不发。本序列刻意复刻真人时序（按下 step8 → 拖动 step12/16 → 松手 step20，
 *  全部跨 tick）；修后应看到 step18 仍 dragNow=0、松手后 sold 位变 1 且目标格出现该卡。 */
function csRunM(mc, step) {
    if (step === 2) {
        var gx = csNum(function () { return mc.screen.getGuiLeft(); });
        var gy = csNum(function () { return mc.screen.getGuiTop(); });
        csMCard = 0;
        csMCardId = csShelfIds(mc)[0];
        csMBal = csNum(function () { return WalletState_CLASS.balance(); });
        csMPreSold = csSoldCsv(mc);
        csMPreInv = csInvCsv(mc);
        var t2 = csEmptyInvCell(mc);
        csMDropSlot = (t2 == null) ? -1 : t2[0];
        csMDropX = (t2 == null) ? 125 : (t2[1] - gx);
        csMDropY = (t2 == null) ? 110 : (t2[2] - gy);
        var c = csCardCenter(mc, csMCard);
        csMGeoX = c[0]; csMGeoY = c[1];
        cs("evt=m_pre:step=" + step + ":bal=" + csMBal + ":card=" + csMCard + ":cardId=" + csMCardId
            + ":sold=" + csMPreSold + ":dropSlot=" + csMDropSlot + ":dropRel=" + csMDropX + "," + csMDropY
            + ":cardRel=" + csMGeoX + "," + csMGeoY + ":carried=" + csCarried(mc) + ":inv=" + csMPreInv);
        csScreen(mc, "m_pre");
        csShot(mc, "m_pre", step);
    } else if (step === 8) {
        csEvt(mc, "click", csMGeoX, csMGeoY, "card" + csMCard, step);      // 真实 mousePressed（卡框0）
    } else if (step === 10) {
        csScreen(mc, "m_press");                                          // 期望 sel=0（单击只选中）
        csShot(mc, "m_sel", step);                                        // ★截图①：选中态
    } else if (step === 12) {
        var mx = Math.round((csMGeoX + csMDropX) / 2);
        var my = Math.round((csMGeoY + csMDropY) / 2);
        csEvt(mc, "drag", mx, my, "mid", step);                           // 真实 mouseDragged（跨 tick）
    } else if (step === 16) {
        csEvt(mc, "drag", csMDropX, csMDropY, "over_drop", step);         // 拖到落点上空
    } else if (step === 18) {
        csScreen(mc, "m_drag");                                           // 修前这里 dragNow 已被清成 -1
        csShot(mc, "m_drag", step);                                       // ★截图②：拖拽中跟随
    } else if (step === 20) {
        csEvt(mc, "release", csMDropX, csMDropY, "drop", step);           // 真实 mouseReleased ⇒ 发购买意图
    } else if (step === 26) {
        var inv = csInvCsv(mc);
        cs("evt=m_after:step=" + step + ":bal=" + csNum(function () { return WalletState_CLASS.balance(); })
            + ":sold=" + csSoldCsv(mc) + ":cardSold=" + csShelfSold(mc, csMCard)
            + ":invHasCard=" + ((inv.indexOf(csMCardId) >= 0) ? 1 : 0) + ":carried=" + csCarried(mc) + ":inv=" + inv);
        csScreen(mc, "m_after");
        csInvDump(mc, "m_after");
    } else if (step === 28) {
        csShot(mc, "m_drop", step);                                       // ★截图③：落包后（货架 soldout）
    } else if (step === 40) {
        cs("evt=m_end:how=" + csCloseBy_(mc, "csRunM"));
    }
}

/** 784：沙漏去数字取证（§7）。主视图与转账面板**两处**都不得再出现倒计时数字/秒数文本。
 *  修前：主视图沙漏上有金色数字（CardShopScreen:653-658）、转账面板确认键下有红色「冷却 N 秒」(:722-727)。
 *  坐标：中按钮(转账) = 相对 (64,125)；返回键 = 相对 (183,34)。 */
function csRunH(mc, step) {
    // 「面板 + cd>0」逐 tick 采样（任务M §7 面板一处）：探针的 ClientEvents.tick 早于同 tick 的
    //   screen.tick()/渲染；若这里观测到 view=1 且 cd>0，而紧随其后的 containerTick（CardShopScreen.java:393-399）
    //   会因 isCycleRunning() 为真立刻 closeTransferView()，则面板倒计时文本（修前的 :722-727）来不及被渲染。
    if (csHBurst && step > 46 && step <= 72) {
        var hv = 0; var hcd = -1;
        try {
            var s0 = mc.screen;
            if (s0 != null && s0.getClass().getName() === ShopScreen_CLASS) {
                hv = s0.transferViewVisible() ? 1 : 0;
                hcd = s0.menu.transferCooldownSeconds();
            }
        } catch (eH) { hcd = -2; }
        csHTk++;
        var both = (hv === 1 && hcd > 0) ? 1 : 0;
        if (both === 1) { csHSeen++; }
        cs("evt=h_t:step=" + step + ":cd=" + hcd + ":view=" + hv + ":panelcd=" + both);
        if (both === 1 && !csHShot) { csHShot = true; csShot(mc, "h_panel_live_cd", step); csScreen(mc, "h_panel_live_cd"); }
    }
    if (step === 2) {
        cs("evt=h_open:" + csState(mc)); csScreen(mc, "h_open");
    } else if (step === 6) {
        csCmd(mc, "astralshop cycleset H0 40", "cyc40", step);        // cd>0 ⇒ 沙漏图形（修前叠加数字）
    } else if (step === 14) {
        csScreen(mc, "h_main_cd");                                    // 期望 cd>0
        csShot(mc, "h_main_cd", step);                                // ★主视图沙漏（有/无数字）
    } else if (step === 20) {
        csCmd(mc, "astralshop cycleset H0 0", "cyc0", step);          // 归零 ⇒ 转账就绪，才进得去面板
    } else if (step === 26) {
        csScreen(mc, "h_ready"); csShot(mc, "h_main_ready", step);    // 就绪态（沙漏 frame=0）
    } else if (step === 30) {
        csEvt(mc, "click", 64, 125, "transfer_btn", step);
    } else if (step === 34) {
        csEvt(mc, "release", 64, 125, "transfer_btn", step);
    } else if (step === 40) {
        csScreen(mc, "h_panel_open");                                 // 期望 view=1（cd=0 才能进面板）
        csShot(mc, "h_panel_open", step);                             // ★转账面板正常态（图形/文本基线）
    } else if (step === 46) {
        csHBurst = true;                                              // 开始逐 tick 采样（配合下方 csRunH 头部）
        csCmd(mc, "astralshop cycleset H0 30", "cyc30", step);        // 面板开着时 cd>0
    } else if (step === 54) {
        csScreen(mc, "h_panel_cd");                                   // 实测：cd>0 时面板已被 containerTick 自动退回（view=0）
        csShot(mc, "h_cd_aftercyc", step);                            // ★cd>0 画面（面板已退回主视图）
    } else if (step === 60) {
        csEvt(mc, "click", 183, 34, "back_btn", step);
    } else if (step === 64) {
        csEvt(mc, "release", 183, 34, "back_btn", step);
    } else if (step === 70) {
        csScreen(mc, "h_main_cd2");                                   // 回到主视图且 cd>0
        csShot(mc, "h_main_cd2", step);                               // ★主视图第二次（复核无数字）
    } else if (step === 80) {
        cs("evt=h_burst:ticks=" + csHTk + ":seen_view_cd=" + csHSeen + ":shot=" + (csHShot ? 1 : 0));
        cs("evt=h_end:how=" + csCloseBy_(mc, "csRunH"));
    }
}

/** 785：几何窗口（给 temp/t88 的 OS 级真鼠标拖拽脚本用）——只报 evt=geo + 只读心跳，零动作。
 *  屏**保持开着**（不自关）：runner 在用例跑完后用真实 SetCursorPos + SendInput 拖拽，
 *  再按真实 ESC 关屏，最后跑「未武装 30s」护栏窗口。 */
function csRunG(mc, step) {
    if (step === 2) {
        cs("evt=g_open:" + csState(mc)); csScreen(mc, "g_open");
        csGeo(mc, "g");
    } else if (step % 100 === 0) {
        csGeo(mc, "g_t" + step);      // 每 100t 报一次（含 live cursorPx），供 runner 校准
    }
}

/** 786：任务N 三处修复取证（① 主视图按钮顺序 / ② 转账面板整体 2× 与面板内控件命中 / ③ 返回键无多余符号）。
 *  全部动作走 csEvt = CardShopScreen#mouseClicked/mouseReleased 的**真实屏幕事件**路径，不用合成业务载荷。
 *  坐标：主视图 1× 不变；面板内一律 csTp(设计坐标)（见文件头）。
 *  时序：2 开屏(cd>0 主视图) → 6/8 点左按钮(星光兑换) → 12 记 view → **等用例把 cd 归零**（每 tick 轮询，
 *  不用固定 step，避免自截图耗时造成的墙钟漂移把注入打偏）→ 归零那一拍截主视图并记 csNBase，
 *  其后一律按 k = step - csNBase 排程：k6 点中按钮 → k16 面板几何自检+全貌截图 → k22 选候选行0
 *  → k36 选第4档金额 → k50 点搜索框 → k62 点金额框 → k74 点滚动条轨道下部(翻页) → k88 点返回键
 *  → k96 返回后主视图截图 → k102 自关。 */
function csRunN(mc, step) {
    if (step === 2) {
        csNBal = csNum(function () { return WalletState_CLASS.balance(); });
        cs("evt=n_pre:step=" + step + ":bal=" + csNBal + ":tpX=" + CS_TP_X + ":tpY=" + CS_TP_Y + ":scale=" + CS_TP_S);
        csScreen(mc, "n_main_cd");          // cd>0：沙漏非零帧 + 中按钮(转账)禁用态 —— 截图①a
        csShot(mc, "n_main_cd", step);
        return;
    }
    if (step === 6) {
        csEvt(mc, "click", 27, 125, "btn_left_starlight", step);     // 左按钮 = 星光兑换（对调后）
        return;
    }
    if (step === 8) {
        csEvt(mc, "release", 27, 125, "btn_left_starlight", step);
        return;
    }
    if (step === 12) {
        csNViewLeft = csNum(function () { return mc.screen.transferViewVisible() ? 1 : 0; });
        csScreen(mc, "n_left");            // 期望 view=0：左按钮不是转账 ⇒ 贴图与 hit-test 同侧
        cs("evt=n_left:view=" + csNViewLeft + ":bal=" + csNum(function () { return WalletState_CLASS.balance(); }));
        return;
    }
    if (csNPhase === 0) {
        // 等 cd 归零（用例注入 /astralshop cycleset N0 0）；超时兜底防挂死
        if (step > 400) {
            cs("evt=n_nocd:step=" + step + ":how=" + csCloseBy_(mc, "csRunN"));
            csNPhase = -1;
            return;
        }
        if (step < 14) { return; }
        var cdNow = csNum(function () { return mc.screen.getMenu().transferCooldownSeconds(); });
        if (step % 20 === 0) { cs("evt=n_poll:step=" + step + ":cd=" + cdNow); }   // 诊断：轮询值（-999 = 表达式抛异常）
        if (cdNow !== 0) { return; }
        csNBase = step; csNPhase = 1;
        csScreen(mc, "n_cd0");             // 归零：cd=0 / ready=1 / hour=0 —— 截图①b
        csShot(mc, "n_main", step);
        return;
    }
    if (csNPhase !== 1) { return; }
    var k = step - csNBase;
    if (k === 6) {
        csEvt(mc, "click", 64, 125, "btn_mid_transfer", step);       // 中按钮 = 转账
    } else if (k === 8) {
        csEvt(mc, "release", 64, 125, "btn_mid_transfer", step);
    } else if (k === 16) {
        csScreen(mc, "n_tp_open");         // 期望 view=1 —— 截图②（2× 全貌）
        // 几何自证：mod 段来自 mod 侧的 tpX/tpY，calc 段是探针**独立复算**的同一套公式；
        // mod 里的 rel 段（GUI 相对坐标）必须与 calc 段逐字符相等 ⇒ agree=1 才证明「画/点同一套换算」。
        var modGeo = csStr(function () { return mc.screen.transferGeometry(); });
        var calcGeo = csTpCalc();
        var gi = modGeo.indexOf(":rel=");
        var absGeo = (gi >= 0) ? modGeo.substring(0, gi) : modGeo;
        var relGeo = (gi >= 0) ? modGeo.substring(gi + 5) : "none";
        cs("evt=n_geo:mod=" + absGeo + ":calc=" + calcGeo + ":agree=" + ((relGeo === calcGeo) ? 1 : 0));
        csGeo(mc, "n");
        csShot(mc, "n_tp_open", step);
    } else if (k === 22) {
        var r0 = csTp(40, 33);
        csEvt(mc, "click", r0[0], r0[1], "row0", step);             // 候选行0（面板内命中）
    } else if (k === 24) {
        var r0b = csTp(40, 33);
        csEvt(mc, "release", r0b[0], r0b[1], "row0", step);
    } else if (k === 30) {
        csScreen(mc, "n_row");
        cs("evt=n_row:sel=" + csNum(function () { return mc.screen.transferSelectedIndex(); }) + ":view=1");
        csShot(mc, "n_row", step);
    } else if (k === 36) {
        var l3 = csTp(106, 112);
        csEvt(mc, "click", l3[0], l3[1], "limit3", step);           // 第4档金额（20）
    } else if (k === 38) {
        var l3b = csTp(106, 112);
        csEvt(mc, "release", l3b[0], l3b[1], "limit3", step);
    } else if (k === 44) {
        csScreen(mc, "n_limit");
        cs("evt=n_limit:amount=" + csNum(function () { return mc.screen.transferAmountValue(); }));
        csShot(mc, "n_limit", step);
    } else if (k === 50) {
        var s1 = csTp(40, 10);
        csEvt(mc, "click", s1[0], s1[1], "search_box", step);        // 搜索输入框（真实 EditBox）
    } else if (k === 52) {
        var s1b = csTp(40, 10);
        csEvt(mc, "release", s1b[0], s1b[1], "search_box", step);
    } else if (k === 56) {
        cs("evt=n_focus:what=search:kind=" + csStr(function () { return mc.screen.transferFocusKind(); }));
        csShot(mc, "n_focus_search", step);
    } else if (k === 62) {
        var a1 = csTp(110, 53);
        csEvt(mc, "click", a1[0], a1[1], "amount_box", step);        // 金额输入框（真实 EditBox）
    } else if (k === 64) {
        var a1b = csTp(110, 53);
        csEvt(mc, "release", a1b[0], a1b[1], "amount_box", step);
    } else if (k === 68) {
        cs("evt=n_focus:what=amount:kind=" + csStr(function () { return mc.screen.transferFocusKind(); }));
    } else if (k === 74) {
        var rl = csTp(84, 128);
        csEvt(mc, "click", rl[0], rl[1], "scroll_rail_low", step);   // 自定义滚动条：点轨道下部 ⇒ 翻页
    } else if (k === 76) {
        var rlb = csTp(84, 128);
        csEvt(mc, "release", rlb[0], rlb[1], "scroll_rail_low", step);
    } else if (k === 82) {
        csScreen(mc, "n_scroll");
        csShot(mc, "n_scroll", step);
    } else if (k === 88) {
        var bk = csTp(102, 8);
        csEvt(mc, "click", bk[0], bk[1], "back", step);             // 返回键（面板内命中）
    } else if (k === 90) {
        var bkb = csTp(102, 8);
        csEvt(mc, "release", bkb[0], bkb[1], "back", step);
    } else if (k === 96) {
        csScreen(mc, "n_back");            // 期望 view=0（返回键真的关掉了面板）—— 截图③
        csShot(mc, "n_back", step);
    } else if (k === 102) {
        cs("evt=n_end:bal=" + csNum(function () { return WalletState_CLASS.balance(); })
            + ":how=" + csCloseBy_(mc, "csRunN"));
        csNPhase = 2;
    }
}
// ══════════════════ 任务N′（2026-10-09 子代理）：场景 787 = §4 实机重测 ══════════════════
//  787 一次性覆盖（用户任务书 §4 的 ①..⑦）：
//   ① 主视图两按钮新位置（星光兑换在左 11,109 / 转账在右 48,109）
//   ② 转账面板 2× 全貌 + 越界自检（面板 252x270 是否超出 GUI）
//   ③ 返回键（面板内无多余 "<"）+ 面板内真实控件换算（搜索框焦点）
//   ④ 悬停 = shop_card_cursor / 点击选中 = shop_card_select
//   ⑤ 沙漏 4→1→0（含就绪保持）
//   ⑥ 冷却/不可用态：转账 shop_transfer_button_unavailable(_push) / 星光 starlight_exchange_unavailable(_push)
//   ⑦ 拖拽三连（单击选中 / 原版 16x16 小图标跟随 / 落包后 soldout + 物品栏出现该卡）+ 反例（落点不在格内 ⇒ 不发包、无扣款）
//  ⑧⑨ 由 csTail（商店屏之外）接管：创造栏排序 + JEI 配方查看器 TTT/BBB/MMM
//  ⚠️ ④⑥ 需要**真实 OS 光标**停在控件上（探针的 lastHover* 来自 render 传入的真实光标坐标），
//     由外部脚本 temp/t91/real_cursor.ps1 并发驱动：探针落 evt=n2_need_<label> ⇒ 外部把真光标移过去 ⇒
//     探针轮询 scr.hoverState() 确认命中后继续；每阶段都有超时兜底（绝不卡死，超时记 n2_to_*）。
var csN2P = 0;         // 阶段机
var csN2T = 0;         // 本阶段已过 tick
var csN2K0 = -1;       // 沙漏归 0 那一拍
var csN2To = 0;        // 超时兜底次数
var csN2HoverDeadline = -1;
var csN2Cells = null;  // 空物品栏格 [inv, relX, relY]（GUI 相对坐标，csEvt 需要）
var csN2CardId = "?";
var csN2Card1Id = "?";
var csN2NegOk = -1;
var csN2BalPre = -1;

function csN2Tex(mc) { return csStr(function () { return mc.screen.mainIconTextures(); }); }
function csN2Hover(mc) { return csStr(function () { return mc.screen.hoverState(); }); }

/** 真光标悬停现场诊断（任务O 第 6 轮）：游戏侧游标像素 / 窗口 active / 鼠标是否被抓 / 屏幕本帧收到的 mouseX,mouseY。
 *  用途：真光标停在键上却读到 ex=0 时，用 cur/mp 判定「移动没被游戏收到」还是「收到但不在键上」。 */
function csCurDiag(mc) {
    return "cur=" + csStr(function () { return Math.round(mc.mouseHandler.xpos()) + "," + Math.round(mc.mouseHandler.ypos()); })
        + ":act=" + (mc.isWindowActive() ? 1 : 0)
        + ":grab=" + csNum(function () { return mc.mouseHandler.isMouseGrabbed() ? 1 : 0; })
        + ":mp=" + csStr(function () { return mc.screen.mouseArgs(); });
}
function csN2Cd(mc) { return csNum(function () { return mc.screen.getMenu().transferCooldownSeconds(); }); }
function csN2Hour(mc) { return csNum(function () { return mc.screen.hourglassFrame(); }); }
function csN2View(mc) { return csNum(function () { return mc.screen.transferViewVisible() ? 1 : 0; }); }
function csN2Sel(mc) { return csNum(function () { return mc.screen.selectedCardIndex(); }); }
function csN2ExReady(mc) { return csNum(function () { return mc.screen.exchangeReady() ? 1 : 0; }); }
function csN2TpReady(mc) { return csNum(function () { return mc.screen.transferReady() ? 1 : 0; }); }
function csN2Bal() { return csNum(function () { return WalletState_CLASS.balance(); }); }
function csN2Star(mc) { return csStr(function () { return mc.screen.starlightState(); }); }
// 任务O 修正：星光快照串里带 :cd=（冷却秒数，每秒都在变），比较「有没有被兑换」时必须去掉它，
// 否则恒判 applied=1（第 8 轮实测复现：bal=798->798:balSame=1 但 cd=43->42 ⇒ applied=1 假阳性）。
function csStarCore(s) { var i = ("" + s).indexOf(":cd="); return i < 0 ? ("" + s) : ("" + s).substring(0, i); }
/** 主视图图标一行（探针读数 = 画出来的贴图名） */
function csN2Icons(mc) { return csN2Tex(mc) + ":exReady=" + csN2ExReady(mc) + ":tpReady=" + csN2TpReady(mc) + ":cd=" + csN2Cd(mc); }
/** 面板越界自检：面板 = 面板原点(TP_X=81,TP_Y=26) + 设计尺寸 126x135 × TP_SCALE=1 = 126x135
 *  任务O §1.1（2026-10-09）：2× 已回滚 ⇒ 尺寸与原点都回到原值（126x135 / 81,26），越界必然为 0。 */
function csN2Bounds(mc) {
    try {
        var gx = csNum(function () { return mc.screen.getGuiLeft(); });
        var gy = csNum(function () { return mc.screen.getGuiTop(); });
        var sw = mc.getWindow().getGuiScaledWidth();
        var sh = mc.getWindow().getGuiScaledHeight();
        var px = gx + 81, py = gy + 26, pw = 126, ph = 135;
        var of = (px < 0 || py < 0 || (px + pw) > sw || (py + ph) > sh) ? 1 : 0;
        return "gui=" + sw + "x" + sh + ":abs=" + px + "," + py + ":size=" + pw + "x" + ph + ":overflow=" + of;
    } catch (e) { return "EX:" + ("" + e).substring(0, 50); }
}
/** 需要真实光标的阶段：先落 n2_need_<label> 标记，再逐 tick 轮询到命中（超时返回 2 并记账） */
function csN2WaitHover(mc, fn, limit, label) {
    if (csN2T === 1) {
        cs("evt=n2_need_" + label + ":phase=" + csN2P + ":step=" + csStep + ":" + csN2Hover(mc));
        csN2HoverDeadline = csStep + limit;
    }
    if (fn()) { return 1; }
    if (csStep > csN2HoverDeadline) {
        cs("evt=n2_to_hover:label=" + label + ":phase=" + csN2P + ":step=" + csStep + ":" + csN2Hover(mc));
        csN2To++; return 2;
    }
    return 0;
}
function csN2Step(mc, step) {
    csN2T++;
    if (step % 40 === 0) { csGeo(mc, "n2_cal"); }   // 周期 geo（外部真鼠标两点定标 + 回读校验）
    var P = csN2P;
    if (P === 0) {
        // ── ① 主视图：两按钮新位置（左=星光兑换 11,109 / 右=转账 48,109）+ 前置数据 ──
        csScreen(mc, "n2_main"); csShot(mc, "n2_main", step);
        cs("evt=n2_icons_main:" + csN2Icons(mc));
        cs("evt=n2_hover_main:" + csN2Hover(mc));
        csN2BalPre = csN2Bal();
        cs("evt=n2_pre:bal=" + csN2BalPre + ":star=" + csN2Star(mc)
            + ":sold=" + csSoldCsv(mc) + ":ready=" + csN2TpReady(mc) + ":exReady=" + csN2ExReady(mc)
            + ":geom=" + csStr(function () { return mc.screen.transferGeometry(); }));
        csInvDump(mc, "n2_pre");
        csN2P = 1; csN2T = 0; return;
    }
    if (P === 1) {
        // ── ⑥左 星光不可用：/astralshop star 0（可支配星光 < 2）⇒ 灰化常态 + 按下态 ──
        if (csN2T === 1) { csCmd(mc, "astralshop star N2 0", "star0", step); return; }
        if (csN2T < 12) { return; }
        cs("evt=n2_star0:" + csN2Star(mc) + ":exReady=" + csN2ExReady(mc));
        csN2P = 2; csN2T = 0; return;
    }
    if (P === 2) {
        var r2 = csN2WaitHover(mc, function () { return csN2Hover(mc).indexOf("ex=1") === 0; }, 700, "ex_gray");
        if (r2 === 0) { return; }
        csShot(mc, "n2_ex_gray", step);
        cs("evt=n2_ex_gray:r=" + r2 + ":hover=" + csN2Hover(mc) + ":" + csN2Icons(mc) + ":star=" + csN2Star(mc));
        csN2P = 3; csN2T = 0; return;
    }
    if (P === 3) {
        // 真光标悬停着点一下 ⇒ 6 tick 内画 _push（画的就是按下的那一张）
        if (csN2T === 1) { csEvt(mc, "click", 27, 125, "exchange_gray", step); return; }
        if (csN2T === 2) { csEvt(mc, "release", 27, 125, "exchange_gray", step); return; }
        if (csN2T === 3) { csShot(mc, "n2_ex_gray_push", step); cs("evt=n2_ex_gray_push:" + csN2Icons(mc) + ":hover=" + csN2Hover(mc)); return; }
        if (csN2T < 20) { return; }
        csN2P = 4; csN2T = 0; return;
    }
    if (P === 4) {
        // 星光恢复 ⇒ 可用态图标必须保持原样（starlight_exchange.png / _push.png）
        if (csN2T === 1) { csCmd(mc, "astralshop star N2 20", "star20", step); return; }
        if (csN2T < 12) { return; }
        cs("evt=n2_star20:" + csN2Star(mc) + ":exReady=" + csN2ExReady(mc) + ":" + csN2Icons(mc));
        csN2P = 5; csN2T = 0; return;
    }
    if (P === 5) {
        if (csN2T >= 4) { csN2P = 6; csN2T = 0; }
        return;
    }
    if (P === 6) {
        // ── ④ 悬停 = shop_card_cursor（真光标停在 0 号卡框中心 (29,58)） ──
        var r6 = csN2WaitHover(mc, function () { return csN2Hover(mc).indexOf("card=0") > 0; }, 700, "card0");
        if (r6 === 0) { return; }
        csShot(mc, "n2_hover", step);
        cs("evt=n2_hover:r=" + r6 + ":hover=" + csN2Hover(mc)
            + ":tex0=" + csStr(function () { return mc.screen.shelfTextureKind(0); }) + ":sel=" + csN2Sel(mc));
        csN2P = 7; csN2T = 0; return;
    }
    if (P === 7) {
        // 真光标已在卡框上 ⇒ 外部脚本送**真实左键按下/抬起**；本阶段轮询 selectedCardIndex 确认落地
        if (csN2T === 1) { cs("evt=n2_need_click_card0:phase=7:step=" + step + ":" + csN2Hover(mc)); csN2HoverDeadline = csStep + 700; return; }
        if (csN2Sel(mc) === 0) {
            // ⚠ 2026-10-09 修（SHOP-N2 首跑实测）：真左键落地的**同一 tick** 内 renderShelf 还没跑，
            //   shelfTextureKind(0) 读到的仍是上一帧的「悬停=cursor」⇒ 必须再等 3 tick 让 render
            //   把「选中=select」刷进探针，否则 n2_click 的 tex0 永远判 cursor（假阴性）。
            if (csN2SelT < 0) { csN2SelT = csStep; return; }
            if (csStep - csN2SelT < 3) { return; }
            csShot(mc, "n2_click", step);
            cs("evt=n2_click:how=real_os:sel=" + csN2Sel(mc) + ":tex0=" + csStr(function () { return mc.screen.shelfTextureKind(0); }) + ":hover=" + csN2Hover(mc));
            csN2P = 8; csN2T = 0; return;
        }
        if (csStep > csN2HoverDeadline) {
            cs("evt=n2_to_click:step=" + csStep + ":sel=" + csN2Sel(mc) + ":hover=" + csN2Hover(mc));
            csN2To++;
            csEvt(mc, "click", 29, 58, "card0_synth", step); csEvt(mc, "release", 29, 58, "card0_synth", step);
            csN2P = 71; csN2T = 0; return;
        }
        return;
    }
    if (P === 71) {   // 兜底：合成事件选中后补图（报告须注明该张图非真实点击驱动）
        if (csN2T >= 3) {
            csShot(mc, "n2_click", step);
            cs("evt=n2_click:how=synth_fallback:sel=" + csN2Sel(mc) + ":tex0=" + csStr(function () { return mc.screen.shelfTextureKind(0); }));
            csN2P = 8; csN2T = 0;
        }
        return;
    }
    if (P === 8) {
        // ── ⑦ 拖拽反例：落点 (100,178) 不在任何物品栏格内 ⇒ 不发包、无扣款 ──
        if (csN2T === 1) {
            var t = csEmptyInvCell(mc);
            var gx = csNum(function () { return mc.screen.getGuiLeft(); });
            var gy = csNum(function () { return mc.screen.getGuiTop(); });
            // csEmptyInvCell 返回**绝对屏幕坐标**，而 csEvt 内部会再加 guiLeft/guiTop ⇒ 这里必须先转成 GUI 相对
            // （与 csRunM :1093-1096 同口径）；万一没有空格子，兜底用快捷栏 0 号格中心（相对 124,167）。
            csN2Cells = (t == null) ? [0, 124, 167] : [t[0], t[1] - gx, t[2] - gy];
            csN2BalPre = csN2Bal();
            csN2CardId = csShelfIds(mc)[0]; csN2Card1Id = csShelfIds(mc)[1];
            cs("evt=n2_drag_pre:bal=" + csN2BalPre + ":sold=" + csSoldCsv(mc) + ":card0=" + csN2CardId + ":card1=" + csN2Card1Id
                + ":cell=" + ((csN2Cells == null) ? "null" : (csN2Cells[0] + "@" + csN2Cells[1] + "," + csN2Cells[2])) + ":inv=" + csInvCsv(mc));
            csInvDump(mc, "n2_drag_pre");
            return;
        }
        if (csN2T === 3) { csEvt(mc, "click", 29 + 46, 58, "card1_neg", step); return; }
        if (csN2T === 5) { csEvt(mc, "drag", 29 + 46, 58, "card1_neg_mid", step); return; }
        if (csN2T === 7) { csEvt(mc, "drag", 100, 178, "outside", step); return; }
        if (csN2T === 9) { csEvt(mc, "release", 100, 178, "outside", step); return; }
        if (csN2T === 12) {
            var bal = csN2Bal(); var sold1 = csShelfSold(mc, 1);
            csN2NegOk = (bal === csN2BalPre) ? 1 : 0;
            cs("evt=n2_neg:drop=outside100,178:bal_before=" + csN2BalPre + ":bal_after=" + bal + ":sold1=" + sold1
                + ":sel=" + csN2Sel(mc) + ":dragNow=" + csNum(function () { return mc.screen.draggingCardIndex(); }) + ":nocharge=" + csN2NegOk);
            csN2P = 9; csN2T = 0; return;
        }
        return;
    }
    if (P === 9) {
        // ── ⑦ 正例：单击选中 0 号卡 → 拖到中点（截图看原版 16x16 小图标跟随）→ 拖到空格 → 抬起落包 ──
        if (csN2T === 1) { csN2BalPre = csN2Bal(); cs("evt=n2_buy_pre:bal=" + csN2BalPre + ":sold=" + csSoldCsv(mc) + ":card0=" + csN2CardId + ":inv=" + csInvCsv(mc)); return; }
        if (csN2T === 3) { csEvt(mc, "click", 29, 58, "card0", step); return; }
        if (csN2T === 5) { csEvt(mc, "drag", csN2Cells[1], csN2Cells[2] - 40, "mid", step); return; }
        if (csN2T === 7) { csShot(mc, "n2_drag_ghost", step); cs("evt=n2_drag_ghost:dragNow=" + csNum(function () { return mc.screen.draggingCardIndex(); }) + ":carried=" + csCarried(mc)); return; }
        if (csN2T === 9) { csEvt(mc, "drag", csN2Cells[1], csN2Cells[2], "cell", step); return; }
        if (csN2T === 11) { csShot(mc, "n2_drag_cell", step); return; }
        if (csN2T === 13) { csEvt(mc, "release", csN2Cells[1], csN2Cells[2], "cell", step); return; }
        if (csN2T === 18) {
            var inv = csInvCsv(mc);
            cs("evt=n2_buy:drop=inv" + csN2Cells[0] + ":bal_before=" + csN2BalPre + ":bal_after=" + csN2Bal()
                + ":sold=" + csSoldCsv(mc) + ":sold0=" + csShelfSold(mc, 0) + ":invHasCard=" + ((inv.indexOf(csN2CardId) >= 0) ? 1 : 0) + ":inv=" + inv);
            csInvDump(mc, "n2_drag_post");
            csScreen(mc, "n2_buy"); csShot(mc, "n2_drop", step);
            csN2P = 10; csN2T = 0; return;
        }
        return;
    }
    if (P === 10) {
        // ── ⑤ 沙漏 4（60 秒 4 份、每份 15 秒）+ ⑥中 冷却态转账灰化 ──
        if (csN2T === 1) { csCmd(mc, "astralshop cycleset N2 55", "cyc55", step); return; }
        if (csN2T === 6) { csShot(mc, "n2_hour4", step); cs("evt=n2_hour4:cd=" + csN2Cd(mc) + ":frame=" + csN2Hour(mc) + ":" + csN2Icons(mc)); return; }
        if (csN2T >= 8) { csN2P = 11; csN2T = 0; }
        return;
    }
    if (P === 11) {
        var r11 = csN2WaitHover(mc, function () { return csN2Hover(mc).indexOf("tp=1") > 0; }, 700, "tp_gray");
        if (r11 === 0) { return; }
        csShot(mc, "n2_tp_gray", step);
        cs("evt=n2_tp_gray:r=" + r11 + ":hover=" + csN2Hover(mc) + ":" + csN2Icons(mc));
        csN2P = 12; csN2T = 0; return;
    }
    if (P === 12) {
        if (csN2T === 1) { csEvt(mc, "click", 64, 125, "transfer_gray", step); return; }
        if (csN2T === 2) { csEvt(mc, "release", 64, 125, "transfer_gray", step); return; }
        if (csN2T === 3) { csShot(mc, "n2_tp_gray_push", step); cs("evt=n2_tp_gray_push:" + csN2Icons(mc) + ":hover=" + csN2Hover(mc) + ":view=" + csN2View(mc)); return; }
        if (csN2T < 20) { return; }
        csN2P = 13; csN2T = 0; return;
    }
    if (P === 13) {
        if (csN2T === 1) { csCmd(mc, "astralshop cycleset N2 12", "cyc12", step); return; }
        if (csN2T === 6) { csShot(mc, "n2_hour1", step); cs("evt=n2_hour1:cd=" + csN2Cd(mc) + ":frame=" + csN2Hour(mc) + ":" + csN2Icons(mc)); return; }
        if (csN2T >= 8) { csN2P = 14; csN2T = 0; }
        return;
    }
    if (P === 14) {
        if (csN2T === 1) { csCmd(mc, "astralshop cycleset N2 0", "cyc0", step); return; }
        if (csN2T === 6) { csShot(mc, "n2_hour0", step); cs("evt=n2_hour0:cd=" + csN2Cd(mc) + ":frame=" + csN2Hour(mc) + ":" + csN2Icons(mc)); csN2K0 = csStep; return; }
        if (csN2K0 > 0 && (csStep - csN2K0) >= 40) {
            csShot(mc, "n2_hour0_keep", step);
            cs("evt=n2_hour0_keep:cd=" + csN2Cd(mc) + ":frame=" + csN2Hour(mc) + ":keepTicks=" + (csStep - csN2K0));
            csN2P = 15; csN2T = 0;
        }
        return;
    }
    if (P === 15) {
        // ── ② 打开转账面板（2× 全貌 + 越界自检 + 真实控件换算） ──
        if (csN2T === 1) { cs("evt=n2_ready_open:tpReady=" + csN2TpReady(mc) + ":cd=" + csN2Cd(mc) + ":bal=" + csN2Bal()); return; }
        if (csN2T === 3) { csEvt(mc, "click", 64, 125, "transfer_open", step); return; }
        if (csN2T === 4) { csEvt(mc, "release", 64, 125, "transfer_open", step); return; }
        if (csN2T >= 6 && csN2View(mc) === 1) {
            csScreen(mc, "n2_tp");
            cs("evt=n2_geo:mod=" + csStr(function () { return mc.screen.transferGeometry(); }) + ":calc=" + csTpCalc() + ":bounds=" + csN2Bounds(mc));
            csGeo(mc, "n2_tp");
            csShot(mc, "n2_tp_view", step);
            csShot(mc, "n2_back_btn", step);   // ③ 返回键在面板内的样子（无多余 "<"）
            csN2P = 16; csN2T = 0; return;
        }
        if (csN2T > 30) { cs("evt=n2_to_tpopen:view=" + csN2View(mc) + ":tpReady=" + csN2TpReady(mc)); csN2To++; csN2P = 16; csN2T = 0; }
        return;
    }
    if (P === 16) {
        // 面板内真实控件换算：点搜索框（设计坐标 40,10 ⇒ 屏幕 = 面板原点 + 设计坐标×2）⇒ 焦点必须落在 search
        if (csN2T === 1) { var s = csTp(40, 10); csEvt(mc, "click", s[0], s[1], "search", step); return; }
        if (csN2T === 2) { var s2 = csTp(40, 10); csEvt(mc, "release", s2[0], s2[1], "search", step); return; }
        if (csN2T === 4) {
            cs("evt=n2_focus_search:kind=" + csStr(function () { return mc.screen.transferFocusKind(); }));
            csN2P = 17; csN2T = 0; return;
        }
        return;
    }
    if (P === 17) {
        // ③ 点返回键（设计坐标 102,8）⇒ 面板关闭（view=0），返回主视图
        if (csN2T === 1) { var b = csTp(102, 8); csEvt(mc, "click", b[0], b[1], "back", step); return; }
        if (csN2T === 2) { var b2 = csTp(102, 8); csEvt(mc, "release", b2[0], b2[1], "back", step); return; }
        if (csN2T >= 4) {
            csScreen(mc, "n2_back");
            cs("evt=n2_back:view=" + csN2View(mc) + ":sel=" + csN2Sel(mc));
            csShot(mc, "n2_back", step);
            csN2P = 18; csN2T = 0; return;
        }
        return;
    }
    if (P === 18) {
        // ── 收尾：复原余额/星光 → 关屏 → 交给 csTail（⑧创造栏 + ⑨JEI） ──
        if (csN2T === 1) { csCmd(mc, "astralshop bal N2 787", "bal787", step); return; }
        if (csN2T === 3) { csCmd(mc, "astralshop star N2 20", "star20b", step); return; }
        if (csN2T >= 10) {
            csScreen(mc, "n2_end");
            cs("evt=n2_end:bal=" + csN2Bal() + ":sold=" + csSoldCsv(mc) + ":neg=" + csN2NegOk + ":to=" + csN2To + ":how=" + csCloseBy_(mc, "csRunN2"));
            csTail = true; csTailTick = 0; csTailP = 0; csTailT = 0;
            csN2P = 99; csN2T = 0;
        }
        return;
    }
    if (P === 99) {
        if (csN2T % 40 === 1) { cs("evt=n2_idle:step=" + step + ":to=" + csN2To); }
        return;
    }
}

// ══════════════════ 任务N′（2026-10-09）：csTail = 商店屏之外的收尾取证（⑧创造栏排序 / ⑨JEI 配方查看器）══════════════════
//  这两个取证必须在商店屏**之外**做，而主循环的 inShop!=1 分支会 return ⇒ 本分支挂在
//  `if (!live) return;` 之后、inShop 判定之前，用独立开关 csTail 武装，与商店场景互不干扰。
var csTail = false;
var csTailTick = 0;
var csTailP = 0;
var csTailT = 0;
var csTailFirst = "?";
var csTailRecId = "?";

function csTailLoad(cls) { try { return Java.loadClass(cls); } catch (e) { return null; } }
/** ⑧ 创造栏排序机器取证：astral_dice:dice_tab 的 displayItems 首项必须是 astral_dice:card_shop */
function csTailCreativeOrder() {
    try {
        var BIR = csTailLoad("net.minecraft.core.registries.BuiltInRegistries");
        var RL = csTailLoad("net.minecraft.resources.ResourceLocation");
        if (BIR == null || RL == null) { return "ERR:cls"; }
        var rl = RL.fromNamespaceAndPath("astral_dice", "dice_tab");
        var tab = BIR.CREATIVE_MODE_TAB.getOptional(rl).orElse(null);
        if (tab == null) { return "ERR:notab"; }
        var items = tab.getDisplayItems();
        var n = items.size();
        var arr = [];
        // ⚠ 首轮血泪坑：Collection 上没有 get(int)，Rhino 解析到别的重载 ⇒ ClassCastException(Double)。
        //   统一改用 iterator()（对 Collection/List/NonNullList 都安全）。
        var it = items.iterator();
        for (var i = 0; i < n && i < 6 && it.hasNext(); i++) {
            var st = it.next();
            arr.push(csId(st) + "x" + csCnt(st));
        }
        csTailFirst = arr.length > 0 ? arr[0] : "EMPTY";
        return "size=" + n + ":first6=" + arr.join("|");
    } catch (e) { return "EX:" + csErr(e, 200); }
}
/** ⑧ 创造栏当前页的**创造物品网格首行**（CreativeModeInventoryMenu 里第一个 CreativeSlot 起 6 格）
 *  ⇒ 自证「真的切到了 dice_tab 且 card_shop 排在最前」；只走 iterator，绝不 .get(int)（Rhino 会 ClassCastException）。*/
function csTailCreativeGrid(mc) {
    try {
        var slots = mc.screen.getMenu().slots;      // public final List<Slot>
        var total = slots.size();
        var it = slots.iterator(), idx = 0, start = -1, arr = [];
        while (it.hasNext()) {
            var sl = it.next();
            if (start < 0 && csClsName(sl).indexOf("CreativeSlot") >= 0) { start = idx; }
            if (start >= 0 && arr.length < 6) { var st = sl.getItem(); arr.push(csId(st) + "x" + csCnt(st)); }
            idx++;
        }
        if (start < 0) { return "ERR:nocreativeslot:slots=" + total; }
        return "slotStart=" + start + ":slots=" + total + ":first6=" + arr.join("|");
    } catch (e) { return "EX:" + csErr(e, 200); }
}
function csTailMenuFirst(mc) { return csTailCreativeGrid(mc); }
/** ⑨ 配方字母判定（T=smooth_stone_slab / B=star_coin / M=#minecraft:logs）
 *  2026-10-09 修：SHOP-N2 首跑实测 `Ingredient#getItems()` 在 Rhino 里报
 *  「TypeError: Cannot find function getItems in object ...Ingredient」⇒ 改为三级回退，
 *  任一级成功都产出**同一口径**的字母码（避免断言随实现漂移）：
 *    ① Ingredient#test(ItemStack)（Predicate 公开接口方法）
 *    ② Ingredient#getItems() → 按物品 id / #minecraft:logs 标签判字母
 *    ③ Ingredient#getValues() → Value#getItems()（Collection<ItemStack>）
 *  csTailStrat 记录每级实际命中情况，供报告如实披露（不是每个格都能走一级）。
 */
var csTailStrat = "";
function csTailLetterOfStack(st) {
    try {
        var id = csId(st);
        if (id === "minecraft:smooth_stone_slab") { return "T"; }
        if (id === "astral_dice:star_coin") { return "B"; }
    } catch (e0) { }
    try { var LT = csTailLoad("net.minecraft.tags.ItemTags"); if (st.is(LT.LOGS)) { return "M"; } } catch (e1) { }
    return "";
}
function csTailLettersOf(arr) {
    var s = "";
    for (var k = 0; k < arr.length; k++) {
        var c = csTailLetterOfStack(arr[k]);
        if (c.length > 0 && s.indexOf(c) < 0) { s += c; }
    }
    return (s.length > 0) ? s : "?";
}
function csTailLetter(ig, slab, coin, log) {
    if (ig == null || slab == null) { return "?"; }
    try {
        var s = "";
        if (ig.test(slab)) { s += "T"; }
        if (ig.test(coin)) { s += "B"; }
        if (ig.test(log)) { s += "M"; }
        if (s.length > 0) { if (csTailStrat.indexOf("A") < 0) { csTailStrat += "A"; } return s; }
    } catch (e1) { if (csTailStrat.indexOf("!A") < 0) { csTailStrat += "!A"; } }
    try {
        var arr = ig.getItems();
        if (arr != null && arr.length > 0) { if (csTailStrat.indexOf("B") < 0) { csTailStrat += "B"; } return csTailLettersOf(arr); }
    } catch (e2) { if (csTailStrat.indexOf("!B") < 0) { csTailStrat += "!B"; } }
    try {
        var vs = ig.getValues();
        if (vs != null && vs.length > 0) {
            var col = vs[0].getItems();
            if (col != null) {
                var it3 = col.iterator();
                var out3 = [];
                while (it3.hasNext()) { out3.push(it3.next()); }
                var s3 = csTailLettersOf(out3);
                if (s3 !== "?") { if (csTailStrat.indexOf("C") < 0) { csTailStrat += "C"; } return s3; }
            }
        }
    } catch (e3) { if (csTailStrat.indexOf("!C") < 0) { csTailStrat += "!C"; } }
    return "?";
}
/** ⑨ 配方机器取证：data/astral_dice/recipe/card_shop.json 在运行期展开后的 9 宫格（TTT/BBB/MMM） */
function csTailRecipe(mc) {
    try {
        var RL = csTailLoad("net.minecraft.resources.ResourceLocation");
        var rm = mc.level.getRecipeManager();
        var holder = rm.byKey(RL.fromNamespaceAndPath("astral_dice", "card_shop")).orElse(null);
        if (holder == null) { return "ERR:nokey"; }
        var rec = holder.value();
        csTailRecId = csClsName(rec);
        var out = "cls=" + csTailRecId;
        try { out += ":recId=" + ("" + holder.id()); } catch (e9) { out += ":recId=EX"; }
        try { var res = rec.getResultItem(mc.level.registryAccess()); out += ":result=" + csId(res) + "x" + csCnt(res); } catch (e0) { out += ":result=EX"; }
        var slab = null, coin = null, log = null;
        try {
            var BIRc = csTailLoad("net.minecraft.core.registries.BuiltInRegistries");
            var ItemC = csTailLoad("net.minecraft.world.item.Items");
            slab = new ItemStack_CLASS(ItemC.SMOOTH_STONE_SLAB);
            coin = new ItemStack_CLASS(BIRc.ITEM.get(RL.fromNamespaceAndPath("astral_dice", "star_coin")));
            log = new ItemStack_CLASS(ItemC.OAK_LOG);
        } catch (eI) { }
        try { out += ":wh=" + rec.getWidth() + "x" + rec.getHeight(); } catch (eW) { out += ":wh=EX"; }
        try {
            var ings = rec.getIngredients();
            var n = ings.size();
            out += ":ingN=" + n + ":strat=" + csTailStrat;
            var seg = [];
            var it2 = ings.iterator();      // ⚠ 不用 .get(int)（Rhino 会把 int 装箱成 Double ⇒ ClassCastException）
            for (var i = 0; i < n && it2.hasNext(); i++) {
                seg.push("i" + i + "=" + csTailLetter(it2.next(), slab, coin, log));
            }
            out += ":cells=" + seg.join(",");
        } catch (e2) { out += ":ing=EX:" + csErr(e2, 160); }
        return out;
    } catch (e) { return "EX:" + csErr(e, 200); }
}
/** ⑨ 打开 JEI 配方查看器并聚焦 astral_dice:card_shop 的合成配方 */
function csTailJei(mc) {
    try {
        var rt = null;
        try { rt = JeiInternal_CLASS.getJeiRuntime(); } catch (e0) { rt = null; }
        if (rt == null) { return "ERR:nort"; }
        var RIR = csTailLoad("mezz.jei.api.recipe.RecipeIngredientRole");
        var VT = csTailLoad("mezz.jei.api.constants.VanillaTypes");
        var BIR = csTailLoad("net.minecraft.core.registries.BuiltInRegistries");
        var RL = csTailLoad("net.minecraft.resources.ResourceLocation");
        if (RIR == null || VT == null || BIR == null || RL == null) { return "ERR:cls"; }
        var item = BIR.ITEM.getOptional(RL.fromNamespaceAndPath("astral_dice", "card_shop")).orElse(null);
        if (item == null) { return "ERR:noitem"; }
        var stack = new ItemStack_CLASS(item);
        var ff = rt.getJeiHelpers().getFocusFactory();
        var focus = ff.createFocus(RIR.OUTPUT, VT.ITEM_STACK, stack);
        // ⚠ 首轮血泪坑：RecipesGui.show 有多个重载 ⇒ `show(focus)` 报「The choice of Java method ... is ambiguous」，
        //   用带签名（擦除后形参类型）的中括号调用强制选定 IRecipesGui.show(IFocus)。
        rt.getRecipesGui()["show(mezz.jei.api.recipe.IFocus)"](focus);
        return "ok:item=" + csId(stack);
    } catch (e) { return "EX:" + csErr(e, 200); }
}
/** ⑧ 创造栏分页按钮：NeoForge 在 init() 里用 addRenderableWidget 加了 "<"/">"（CreativeModeInventoryScreen.java:366-367），
 *  它们是真正的 widget ⇒ 可读坐标、可派发点击（tab 按钮本身不是 widget，只能按矩形算，见 csTailTabRect）。*/
function csTailPageButton(mc, label) {
    try {
        var it = mc.screen.children().iterator();
        while (it.hasNext()) {
            var w = it.next();
            try { if (("" + w.getMessage().getString()) === label) { return w; } } catch (e0) { }
        }
    } catch (e) { }
    return null;
}
/** 派发一次「真实屏幕事件」（同一坐标 mousePressed + mouseReleased），与用例注入的 csEvt 同一条路径 */
function csTailScreenClick(mc, x, y) {
    var a = "na", b = "na";
    try { a = mc.screen.mouseClicked(x, y, 0) ? 1 : 0; } catch (e1) { a = "EX"; }
    try { b = mc.screen.mouseReleased(x, y, 0) ? 1 : 0; } catch (e2) { b = "EX"; }
    return "down=" + a + ":up=" + b;
}
/** 创造栏页签矩形（与 CreativeModeInventoryScreen#checkTabClicked 判据同源，:875-901）：
 *  相对 x = 27*column（右对齐：imageWidth-27*(7-col)+1）、相对 y = isTop ? -32 : +imageHeight；
 *  容器 leftPos/topPos = (width-195)/2 / (height-136)/2（AbstractContainerScreen.init 的标准算法）。*/
function csTailTabRect(scr, tab) {
    try {
        var pg = scr.getCurrentPage();
        var col = pg.getColumn(tab);
        var top = pg.isTop(tab);
        var lp = Math.floor((scr.width - 195) / 2);
        var tp = Math.floor((scr.height - 136) / 2);
        var rx = tab.isAlignedRight() ? (195 - 27 * (7 - col) + 1) : (27 * col);
        var ry = top ? -32 : 136;
        return { left: lp + rx, top: tp + ry, col: col, isTop: top ? 1 : 0, x: lp + rx + 13, y: tp + ry + 16 };
    } catch (e) { return null; }
}
var csTailTab = null;
var csTailTabClicked = 0;
var csTailPageFlips = 0;
var csTailTabFound = -1;
/** 当前创造页第一行是否已出现 astral_dice:card_shop */
function csTailGridHasCardShop(mc) { return (csTailCreativeGrid(mc).indexOf("astral_dice:card_shop") >= 0) ? 1 : 0; }

function csTailStep(mc, step) {
    csTailTick++;   // 由 tick 钩子以 csTailTick+1 调用，这里自增后与 step 对齐
    csTailT++;
    var P = csTailP;
    if (P === 0) {
        if (csTailT === 1) { cs("evt=n2_tail_beg:tick=" + csTailTick); return; }
        if (csTailT < 8) { return; }
        cs("evt=n2_ctab:how=registry_display_items:" + csTailCreativeOrder());   // ⑧ 机器判据
        csTailP = 1; csTailT = 0; return;
    }
    if (P === 1) {
        // 真开一次创造栏：截图⑧ 与机器读数同源（创造模式物品栏的排序）
        try {
            var CS = csTailLoad("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen");
            var FF = csTailLoad("net.minecraft.world.flag.FeatureFlags");
            var scr = new CS(mc.player, FF.VANILLA_SET, false);
            mc.setScreen(scr);
            cs("evt=n2_creative_open:ok=1:scr=" + csClsName(scr));
        } catch (e) { cs("evt=n2_creative_open:ok=0:err=" + ("" + e).substring(0, 110)); csTailP = 5; csTailT = 0; return; }
        csTailP = 2; csTailT = 0; return;
    }
    if (P === 2) {
        // 2026-10-09 二次修：tab 按钮不是 widget（首轮 tabBtns=0、截图停在「建筑方块」页）⇒
        // 改为「按 NeoForge 的页签分页按钮翻页 + 按 checkTabClicked 的矩形算坐标派发真实屏幕事件」。
        if (csTailT < 4) { return; }
        try {
            var CMT = csTailLoad("net.neoforged.neoforge.common.CreativeModeTabRegistry");
            var RLt = csTailLoad("net.minecraft.resources.ResourceLocation");
            csTailTab = (CMT == null || RLt == null) ? null : CMT.getTab(RLt.fromNamespaceAndPath("astral_dice", "dice_tab"));
        } catch (e5) { csTailTab = null; }
        var inPage = -1;
        try { inPage = mc.screen.getCurrentPage().getVisibleTabs().contains(csTailTab) ? 1 : 0; } catch (e6) { inPage = -1; }
        csTailTabClicked = 0; csTailPageFlips = 0; csTailTabFound = -1;
        cs("evt=n2_ctab_pick:scr=" + csClsName(mc.screen) + ":tab=" + ((csTailTab == null) ? "null" : "ok") + ":inPage=" + inPage + ":" + csTailCreativeGrid(mc));
        csTailP = 3; csTailT = 0; return;
    }
    if (P === 3) {
        // 目标：创造栏切到 astral_dice:dice_tab ⇒ 截图⑧ 与 evt=n2_ctab 的排序判据同源
        if (csTailT < 4) { return; }
        if (csTailGridHasCardShop(mc) === 1) {
            csTailTabFound = 1;
            cs("evt=n2_ctab_found:flips=" + csTailPageFlips + ":" + csTailCreativeGrid(mc));
            csTailP = 4; csTailT = 0; return;
        }
        var r = (csTailTab == null) ? null : csTailTabRect(mc.screen, csTailTab);
        var inPage2 = 0;
        try { inPage2 = mc.screen.getCurrentPage().getVisibleTabs().contains(csTailTab) ? 1 : 0; } catch (e7) { }
        if (r != null && inPage2 === 1) {
            if (csTailTabClicked === 1) { return; }   // 已点过，等网格刷新后再判
            csTailTabClicked = 1;
            cs("evt=n2_ctab_click:col=" + r.col + ":top=" + r.isTop + ":rect=" + r.left + "," + r.top + ":at=" + r.x + "," + r.y + ":" + csTailScreenClick(mc, r.x, r.y));
            csTailT = 0; return;
        }
        if (csTailPageFlips < 10) {
            var btn = csTailPageButton(mc, ">");
            if (btn != null) {
                var bx = Math.round(btn.getX() + btn.getWidth() / 2);
                var by = Math.round(btn.getY() + btn.getHeight() / 2);
                csTailPageFlips++;
                cs("evt=n2_ctab_page:dir=next:flips=" + csTailPageFlips + ":at=" + bx + "," + by + ":" + csTailScreenClick(mc, bx, by));
                csTailT = 0; return;
            }
        }
        cs("evt=n2_ctab_scan_end:found=-1:flips=" + csTailPageFlips + ":pageBtn=" + ((csTailPageButton(mc, ">") == null) ? "none" : "ok") + ":tab=" + ((csTailTab == null) ? "null" : "ok") + ":" + csTailCreativeGrid(mc));
        csTailP = 4; csTailT = 0; return;
    }
    if (P === 4) {
        // 保持 100 tick 给外部窗口截图；每 20 tick 复读一次（证明画面没被别的屏顶掉）
        var mf = csTailCreativeGrid(mc);
        if (csTailT === 1) {
            cs("evt=n2_creative_state:found=" + csTailTabFound + ":menu=" + mf + ":order_first=" + csTailFirst);
            cs("evt=n2_creative_ready:step=" + step + ":menu=" + mf);
        }
        if (csTailT % 20 === 0) { cs("evt=n2_creative_hold:t=" + csTailT + ":menu=" + mf); }
        if (csTailT >= 100) {
            cs("evt=n2_creative_done:menu=" + mf);
            try { mc.setScreen(null); } catch (e3) { }
            csTailP = 5; csTailT = 0;
        }
        return;
    }
    if (P === 5) {
        if (csTailT < 4) { return; }
        cs("evt=n2_recipe:how=recipe_manager:" + csTailRecipe(mc));   // ⑨ 机器判据（TTT/BBB/MMM）
        csTailP = 6; csTailT = 0; return;
    }
    if (P === 6) {
        if (csTailT < 6) { return; }
        cs("evt=n2_jei:how=focus_output:" + csTailJei(mc));
        csTailP = 7; csTailT = 0; return;
    }
    if (P === 7) {
        if (csTailT === 6) { cs("evt=n2_jei_ready:step=" + step + ":scr=" + csStr(function () { return csClsName(mc.screen); })); }
        if (csTailT % 20 === 0) { cs("evt=n2_jei_hold:t=" + csTailT + ":scr=" + csStr(function () { return csClsName(mc.screen); })); }
        if (csTailT >= 100) {
            try { mc.setScreen(null); } catch (e4) { }
            cs("evt=n2_tail_end:step=" + step + ":to=" + csN2To);
            csTail = false; csTailP = 0; csTailT = 0;
            csArmed = false; csN2 = false; csN2P = 99;
        }
        return;
    }
}
// ════════════════════════════════════════════════════════════════════════════
// 任务O（2026-10-09）实机取证场景（用户裁决最高优先，见任务书 m00001）
//   788 = csRunO ：§1.1 转账面板 2× 回滚（scale=1 / panel=81,26）
//                 + §1.2 转账面板无多余文本（逐键白名单，不得含 cooldown/cooldown_hint）
//                 + §1.4 星光兑换冷却（就绪 → 兑换 → 冷却中点击＝不发包）
//                 + ⑥ 沙漏 4→1→0 就绪保持
//   789 = csRunOB：§1.3 货架持久化（关店重开同批卡 / 店内跨 0 点必换 / 关店期间跨 0 点也换）
// 证据三通道：AP_CSC 机器行（run/1.21.1/logs/kubejs/client.log）、t92_*.png 截图
//            、服务端 [astral_dice] CARD_SHOP_CYCLE 行（run/1.21.1/logs/latest.log）
// ════════════════════════════════════════════════════════════════════════════

/** 安全取当前屏幕（异常返回 null）——场景函数里绝不直接解引用 mc.screen */
function csOScr(mc) { try { return mc.screen; } catch (e) { return null; } }

/** 788：单次开屏的静态取证。阶段机见 csOP（0 = 面板 / 1 = 兑换冷却 / 2 = 沙漏 / 3 = 收尾） */
function csRunO(mc, step) {
    csOT++;
    // 周期 geo：外部真光标脚本四点仿射定标用。⚠ 2026-10-09 第 6 轮实测：间隔 40 tick（2s）时，
    // 驱动的每个采样点要等最多 2s 才有「新鲜」geo 行 ⇒ 5 点校准耗时 14s，整个校准与探针的 15s
    // 悬停窗口重叠，真光标停车全部发生在窗口关闭之后（o_to_hover:cur=960,210 = 校准点）⇒ 改成 10 tick（0.5s）。
    if (csOT % 10 === 0) { csGeo(mc, "o_cal"); }
    var P = csOP;
    if (P === 0) {
        // ── §1.1 回滚后的转账面板全貌 + §1.2 面板文本清单 ──
        if (csOT === 1) {
            csScreen(mc, "o_main");
            cs("evt=o_main:geom=" + csStr(function () { return csOScr(mc).transferGeometry(); })
                + ":calc=" + csTpCalc() + ":icons=" + csN2Icons(mc));
            csShot(mc, "o_main", csOT);
        } else if (csOT === 3) {
            csClick(mc, 64, 125, "o_open_tp", csOT);      // 转账键中心（TRANSFER_X 48,Y 109,尺寸 32）
        } else if (csOT === 10) {
            csScreen(mc, "o_tp");
            cs("evt=o_tp_view:view=" + csN2View(mc)
                + ":geom=" + csStr(function () { return csOScr(mc).transferGeometry(); })
                + ":calc=" + csTpCalc());
            csShot(mc, "o_tp_view", csOT);
        } else if (csOT === 12) {
            cs("evt=o_tp_text:keys=" + csStr(function () { return csOScr(mc).transferTextKeysCsv(); })
                + ":view=" + csN2View(mc) + ":cd=" + csN2Cd(mc));
        } else if (csOT === 15) {
            csClick(mc, 183, 34, "o_back", csOT);         // 返回键中心（TP_BACK 94,0 16x16 ⇒ 屏幕设计 183,34）
        } else if (csOT === 22) {
            csScreen(mc, "o_back");
            cs("evt=o_back_done:view=" + csN2View(mc) + ":sel=" + csN2Sel(mc));
            csOP = 1; csOT = 0;
        }
        return;
    }
    if (P === 1) {
        // ── §1.4 星光兑换冷却：先让本轮就绪（cd=0）→ 兑换一次 → 冷却中再点（应被客户端早退拦下、不发包）──
        if (csOT === 1) {
            csCmd(mc, "astralshop cycleset O 0", "o_cycle_ready", csOT);   // 立刻到点 ⇒ cd 归 0
        } else if (csOT === 6) {
            csORej0 = csNum(function () { return csOScr(mc).exchangeRejectClicks(); });
            csExBal0 = csN2Bal();
            csExStar0 = csN2Star(mc);
            csScreen(mc, "o_ex_ready");
            cs("evt=o_ex_ready:sl=[" + csN2Star(mc) + "]:cd=" + csN2Cd(mc) + ":ready=" + csN2ExReady(mc)
                + ":rej=" + csORej0 + ":icons=" + csN2Icons(mc));
            csShot(mc, "o_ex_ready", csOT);
        } else if (csOT === 8) {
            csClick(mc, 27, 125, "o_ex_first", csOT);     // 星光兑换键中心（EXCHANGE_X 11,Y 109,尺寸 32）
        } else if (csOT === 14) {
            csScreen(mc, "o_ex_first_done");
            cs("evt=o_ex_first_done:bal=" + csExBal0 + "->" + csN2Bal() + ":star=[" + csExStar0 + "->" + csN2Star(mc) + "]"
                + ":cd=" + csN2Cd(mc) + ":ready=" + csN2ExReady(mc));
            csCmd(mc, "astralshop star O 6", "o_ex_refill", csOT);   // 补星光：让「冷却」成为唯一不可用原因
        } else if (csOT === 22) {
            csORej0 = csNum(function () { return csOScr(mc).exchangeRejectClicks(); });
            csScreen(mc, "o_ex_cdonly");
            cs("evt=o_ex_cdonly:sl=[" + csN2Star(mc) + "]:cd=" + csN2Cd(mc) + ":ready=" + csN2ExReady(mc)
                + ":rej=" + csORej0 + ":icons=" + csN2Icons(mc));
            cs("evt=o_need_hover_ex");                       // 请真光标停到兑换键中心
            csOHoverDeadline = csStep + 600;   // 30s：吸收驱动校准 + A/B 双口径重试的余量
        } else if (csOT > 22 && csOEx2 === 0) {
            if (csStep % 20 === 0) {
                // 现场证据：每 20 tick 一行，超时后也能从日志判定根因（focus / grab / 移动丢失）
                cs("evt=o_hover_wait:step=" + csStep + ":hover=" + csN2Hover(mc) + ":" + csCurDiag(mc));
                if (csNum(function () { return mc.mouseHandler.isMouseGrabbed() ? 1 : 0; }) === 1) {
                    // 开屏态鼠标本应已释放（vanilla 开屏即 releaseMouse）；若被重新抓取，真光标位置对游戏无效 ⇒ 主动释放
                    try { mc.mouseHandler.releaseMouse(); cs("evt=o_hover_ungrab:step=" + csStep); } catch (e) { cs("evt=o_hover_ungrab_fail:" + e); }
                }
            }
            if (csN2Hover(mc).indexOf("ex=1") === 0) {
                cs("evt=o_hover_ex_ok:hover=" + csN2Hover(mc));
                // 任务O 修正（2026-10-09）：applied 的基线必须取「点击前一刻」——旧版用 csOT=6 时的基准，
                // 中间夹着第一次成功兑换与补星光，导致 applied=1 的假阳性（实测复现）。
                csExBal2 = csN2Bal(); csExStar2 = csN2Star(mc);
                csClick(mc, 27, 125, "o_ex_second", csOT);   // 冷却中的第二次点击（客户端应早退、不发包）
                csOEx2 = csOT;   // 按下态贴图是「点击后那一帧渲染」才记录的 ⇒ 下一 tick 再取证
            } else if (csStep > csOHoverDeadline) {
                cs("evt=o_to_hover:label=o_need_hover_ex:step=" + csStep + ":hover=" + csN2Hover(mc) + ":" + csCurDiag(mc));
                csOEx2 = -csOT;   // 负值 = 超时（仍继续推进；断言会因缺 o_hover_ex_ok 失败，但整段不卡死）
            }
        } else if (csOEx2 > 0 && csOT === csOEx2 + 1) {
            // 按下态取证：真实光标仍停在键上 + exchangePressTicks>0 ⇒ 渲染记录到的就是 unavailable_push
            cs("evt=o_ex_press:hover=" + csN2Hover(mc) + ":" + csN2Icons(mc));
            csShot(mc, "o_ex_push", csOT);
        } else if ((csOEx2 > 0 && csOT >= csOEx2 + 10) || (csOEx2 < 0 && csOT >= -csOEx2 + 5)) {
            csScreen(mc, "o_ex_reject");
            var exBal1 = csN2Bal();
            var exStar1 = csN2Star(mc);
            // applied 是**算出来的**（余额与星光都没变 ⇒ 服务端没执行兑换），不是写死的结论
            var exApplied = (exBal1 === csExBal2 && csStarCore(exStar1) === csStarCore(csExStar2)) ? 0 : 1;
            cs("evt=o_ex_reject:rej=" + csORej0 + "->" + csNum(function () { return csOScr(mc).exchangeRejectClicks(); })
                + ":bal=" + csExBal2 + "->" + exBal1 + ":balSame=" + (exBal1 === csExBal2 ? 1 : 0)
                + ":star=[" + csExStar2 + "->" + exStar1 + "]"
                + ":applied=" + exApplied + ":cd=" + csN2Cd(mc)
                + ":ready=" + csN2ExReady(mc) + ":icons=" + csN2Icons(mc));
            csShot(mc, "o_ex_gray", csOT);
            csOP = 2; csOT = 0;
        }
        return;
    }
    if (P === 2) {
        // ── ⑥ 沙漏 4→1→0：cd 55s ⇒ 帧4 / 12s ⇒ 帧1 / 0 ⇒ 帧0 且就绪，并保持 ──
        if (csOT === 1) {
            csCmd(mc, "astralshop cycleset O 55", "o_hour4", csOT);
        } else if (csOT === 5) {
            csScreen(mc, "o_hour4");
            cs("evt=o_hour4:cd=" + csN2Cd(mc) + ":frame=" + csN2Hour(mc) + ":ready=" + csN2TpReady(mc)
                + ":icons=" + csN2Icons(mc));
            csShot(mc, "o_hour4", csOT);
            csCmd(mc, "astralshop cycleset O 12", "o_hour1", csOT);
        } else if (csOT === 10) {
            csScreen(mc, "o_hour1");
            cs("evt=o_hour1:cd=" + csN2Cd(mc) + ":frame=" + csN2Hour(mc) + ":ready=" + csN2TpReady(mc));
            csShot(mc, "o_hour1", csOT);
            csCmd(mc, "astralshop cycleset O 0", "o_hour0", csOT);
        } else if (csOT === 20) {
            csScreen(mc, "o_hour0");
            cs("evt=o_hour0:cd=" + csN2Cd(mc) + ":frame=" + csN2Hour(mc) + ":ready=" + csN2TpReady(mc));
            csShot(mc, "o_ready", csOT);
        } else if (csOT === 90) {
            csScreen(mc, "o_hour0_keep");
            cs("evt=o_hour0_keep:cd=" + csN2Cd(mc) + ":frame=" + csN2Hour(mc) + ":ready=" + csN2TpReady(mc)
                + ":ticks=" + (csOT - 20));
            csShot(mc, "o_ready_keep", csOT);
            csOP = 3; csOT = 0;
        }
        return;
    }
    if (P === 3) {
        if (csOT === 1) {
            csCloseBy_(mc, "o_end");
            cs("evt=o_end:how=o_end");
            csOP = 9;
        }
        return;
    }
}

/** 任务P 790 辅助：折扣角标探针（与 renderShelf 的绘制点同源） */
function csPPromoOffset(mc, i) { return csStr(function () { return csOScr(mc).promotionOffset(i); }); }
/** 6 格逐格报「x偏移,y偏移」或 none ⇒ 一次看清哪一格有折扣角标、它被画在卡面的哪个角 */
function csPPromoCsv(mc) {
    var a = [];
    for (var i = 0; i < 6; i++) { a.push(i + ":" + csPPromoOffset(mc, i)); }
    return a.join("|");
}
/** 第一个有折扣角标的格下标（-1 = 本批货架没有折扣卡） */
function csPPromoFirst(mc) {
    for (var i = 0; i < 6; i++) { if (csPPromoOffset(mc, i) !== "none") { return i; } }
    return -1;
}
function csPAmount(mc) { return csNum(function () { return csOScr(mc).transferAmountValue(); }); }
function csPFocus(mc) { return csStr(function () { return csOScr(mc).transferFocusKind(); }); }
function csPMain(mc) { return csNum(function () { return csOScr(mc).mainViewPainted() ? 1 : 0; }); }
function csPSel(mc) { return csNum(function () { return csOScr(mc).transferSelectedIndex(); }); }
function csPGeom(mc) { return csStr(function () { return csOScr(mc).transferGeometry(); }); }
function csPKeys(mc) { return csStr(function () { return csOScr(mc).transferTextKeysCsv(); }); }

/** 790：任务P 五条取证（坐标全部相对 GUI 左上；csClick 内部加 getGuiLeft/Top）。
 *  阶段 csPP：0 = 主视图基线 + 开转账面板 + 控件交互（4 档 / 旧金额框位置 / 搜索框 / 候选行 / 确认）
 *           1 = 出折扣卡（优先「转账后强制降价」；未成功则 equip 经商立牌 + promo 重抽兜底）
 *           9 = 折扣角标取证截图 / 99 = 等 120t 后收尾关屏 / 100 = 已结束 */
function csRunP(mc, step) {
    csPT++;
    var P = csPP;
    if (P === 0) {
        if (csPT === 1) {
            // ── §2.1 基线 + §2.2 几何（geom = mod 侧实测、calc = 探针侧独立复算）──
            csScreen(mc, "p_main");
            cs("evt=p_main:view=" + csN2View(mc) + ":main=" + csPMain(mc) + ":promo=" + csPPromoCsv(mc)
                + ":geom=" + csPGeom(mc) + ":calc=" + csTpCalc());
            csShot(mc, "p_main", csPT);
            // 任务P §2.1 前提：折扣角标只在「经商立牌被动」下出现（CardShopShelf:131 teamBonus = teamHasParunan）
            //  ⇒ 开屏后再装立牌（开屏时装会触发「进店 +1 星币」把余额 790 抬到 791，gate 就失配了）。
            //  转账时服务端 forceDiscountOnOwnShelf() 会查 isEquipped(player)，此刻必已生效。
            // ⚠️ item 参数是 Brigadier StringArgumentType.string()（服务端 :1614），必须以引号开头
            //    ⇒ 裸 id 会抛 ERROR_EXPECTED_START_OF_QUOTE、命令根本到不了 sp_doEquip（第二轮实测教训）。
            csCmd(mc, 'astralshop equip O stand "astral_dice:parunan_sign"', "p_equip", csPT);
        } else if (csPT === 3) {
            csClick(mc, 64, 125, "p_open_tp", csPT);      // 主视图转账键中心（TRANSFER_X 48,Y 109,尺寸 32 ⇒ +16）
        } else if (csPT === 10) {
            csScreen(mc, "p_tp");
            cs("evt=p_tp:view=" + csN2View(mc) + ":main=" + csPMain(mc) + ":keys=" + csPKeys(mc)
                + ":amount=" + csPAmount(mc) + ":focus=" + csPFocus(mc)
                + ":geom=" + csPGeom(mc) + ":calc=" + csTpCalc());
            csShot(mc, "p_tp", csPT);
        } else if (csPT === 12) {
            csClick(mc, 194, 69, "p_limit0", csPT);       // 档位 5 中心（设计 x 109+12, y 60+7）
        } else if (csPT === 15) {
            cs("evt=p_limit0:amount=" + csPAmount(mc) + ":focus=" + csPFocus(mc));
        } else if (csPT === 17) {
            csClick(mc, 194, 114, "p_limit3", csPT);      // 档位 20 中心（设计 x 109+12, y 60+3*15+7）
        } else if (csPT === 20) {
            cs("evt=p_limit3:amount=" + csPAmount(mc));
        } else if (csPT === 22) {
            csClick(mc, 198, 55, "p_amount_click", csPT); // 金额显示框中心（旧 EditBox 位置 ⇒ 应无任何反应）
        } else if (csPT === 25) {
            cs("evt=p_amount_click:amount=" + csPAmount(mc) + ":focus=" + csPFocus(mc));
        } else if (csPT === 27) {
            csClick(mc, 112, 13, "p_search_click", csPT); // 新搜索条输入区中心（设计 x 4+3+65/2, y 4+2+10/2）
        } else if (csPT === 30) {
            cs("evt=p_search_click:focus=" + csPFocus(mc) + ":amount=" + csPAmount(mc));
        } else if (csPT === 32) {
            csClick(mc, 120, 40, "p_row0", csPT);         // 候选行 0 中心（设计 x 2+(93-2)/2, y 29+18/2）
        } else if (csPT === 35) {
            cs("evt=p_row0:sel=" + csPSel(mc) + ":focus=" + csPFocus(mc));
        } else if (csPT === 37) {
            csClick(mc, 194, 69, "p_limit0b", csPT);      // 金额回 5（≤ 余额 ⇒ 确认可用）
        } else if (csPT === 40) {
            cs("evt=p_pre_confirm:amount=" + csPAmount(mc) + ":sel=" + csPSel(mc) + ":focus=" + csPFocus(mc));
            csShot(mc, "p_tp_full", csPT);
            csClick(mc, 198, 37, "p_confirm", csPT);      // 确认键中心（设计 x 109+16, y 24+11）
        } else if (csPT === 55) {
            csScreen(mc, "p_after");
            cs("evt=p_after:view=" + csN2View(mc) + ":main=" + csPMain(mc) + ":promo=" + csPPromoCsv(mc)
                + ":bal=" + csN2Bal());
            csShot(mc, "p_after", csPT);
            csPP = 1; csPT = 0;
        }
        return;
    }
    if (P === 1) {
        // ── §2.1 折扣角标：转账成功必然把第一张未降价牌降到 1 星币（经商被动第 4 条，CardShopMenu:508-518）；
        //    若转账没成功（无候选 / 被拒），则装备经商立牌后用 /astralshop promo 重抽兜底 ──
        if (csPT === 1) {
            csPPromoHit = csPPromoFirst(mc);
            if (csPPromoHit >= 0) {
                cs("evt=p_promo_ok:via=transfer:idx=" + csPPromoHit + ":off=" + csPPromoOffset(mc, csPPromoHit));
                csPP = 9; csPT = 0; return;
            }
            cs("evt=p_promo_miss:via=transfer:promo=" + csPPromoCsv(mc));
            csCmd(mc, 'astralshop equip O stand "astral_dice:parunan_sign"', "p_equip", csPT);
            csCmd(mc, "astralshop curio O", "p_curio", csPT);
        } else if (csPT === 5) {
            csCmd(mc, "astralshop promo O 0", "p_promo_reroll", csPT);
        } else if (csPT > 5 && csPT % 6 === 0) {
            csPPromoHit = csPPromoFirst(mc);
            if (csPPromoHit >= 0) {
                cs("evt=p_promo_ok:via=promo:try=" + csPPromoTry + ":idx=" + csPPromoHit
                    + ":off=" + csPPromoOffset(mc, csPPromoHit));
                csPP = 9; csPT = 0; return;
            }
            csPPromoTry++;
            if (csPPromoTry > 14) {
                cs("evt=p_promo_fail:tries=" + csPPromoTry + ":promo=" + csPPromoCsv(mc));
                csPP = 9; csPT = 0; return;
            }
            csCmd(mc, "astralshop promo O 0", "p_promo_reroll", csPT);
        }
        return;
    }
    if (P === 9) {
        if (csPT === 1) {
            // 折扣角标取证：必须回到主视图（角标画在主视图的货架上）
            if (csN2View(mc) === 1) { cs("evt=p_promo_wrongview:view=1"); }
            csScreen(mc, "p_promo");
            csPPromoHit = csPPromoFirst(mc);
            cs("evt=p_promo_final:idx=" + csPPromoHit
                + ":off=" + (csPPromoHit < 0 ? "none" : csPPromoOffset(mc, csPPromoHit))
                + ":promo=" + csPPromoCsv(mc) + ":view=" + csN2View(mc) + ":main=" + csPMain(mc));
            csShot(mc, "p_promo", csPT);
            csPP = 99; csPT = 0;
        }
        return;
    }
    if (P === 99) {
        if (csPT === 120) {
            var how = csCloseBy_(mc, "p_end");
            cs("evt=p_end:how=" + how + ":opens=" + csOpens);
            csPP = 100;
        }
        return;
    }
}

/** 789：货架持久化（§1.3）。阶段 csOBP 必须**跨开屏**保留：0=一开 / 2=二开 / 3=准备二关 / 5=三开 */
function csRunOB(mc, step) {
    csOBT++;
    if (csOBT % 40 === 0) { csGeo(mc, "o_b_cal"); }
    var P = csOBP;
    if (P === 0) {
        // ── ① 首次开店：记下当前货架 ──
        if (csOBT === 1) {
            csOBIds1 = csShelfIds(mc);
            csOBFp1 = csShelfFp(mc);
            csScreen(mc, "o_b_open1");
            csOBDet1 = csShelfDetail(mc);
            cs("evt=o_b_open1:ids=" + csOBIds1.join(",") + ":fp=" + csOBFp1 + ":cd=" + csN2Cd(mc)
                + ":detail=" + csOBDet1);
            csShot(mc, "o_b_open1", csOBT);
        } else if (csOBT === 8) {
            cs("evt=o_b_close1:how=csCloseBy_");
            csCloseBy_(mc, "o_b_close1");      // 关店 ⇒ 下一 tick 进 csRunOBOut(P=1)
            csOBP = 1; csOBOutc = 0;
        }
        return;
    }
    if (P === 2) {
        // ── ③ 立刻重开：必须还是同一批卡（唯一致换时机 = 沙漏归 0）──
        if (csOBT === 1) {
            csOBIds2 = csShelfIds(mc);
            csScreen(mc, "o_b_open2");
            var det2 = csShelfDetail(mc);
            cs("evt=o_b_open2:ids=" + csOBIds2.join(",") + ":same=" + csObSame(csOBIds1, csOBIds2)
                + ":fpSame=" + (csShelfFp(mc) === csOBFp1 ? 1 : 0) + ":cd=" + csN2Cd(mc)
                + ":detailSame=" + (det2 === csOBDet1 ? 1 : 0) + ":detail=" + det2);
            csShot(mc, "o_b_open2", csOBT);
        } else if (csOBT === 6) {
            csOBIds3 = csShelfIds(mc);
            csOBDet3 = csShelfDetail(mc);
            cs("evt=o_b_arm_cycle:ids=" + csOBIds3.join(",") + ":used=" + csSoldCsv(mc) + ":detail=" + csOBDet3);
            csCmd(mc, "astralshop cycleset O 6", "o_b_cycle6", csOBT);   // 6 秒后到点
        } else if (csOBT === 140) {
            // 跨过 0 点：货架必须已换（由服务端 CardShopCycleTicker 驱动，与界面开关无关）
            var ids4 = csShelfIds(mc);
            csScreen(mc, "o_b_reroll");
            cs("evt=o_b_reroll:before=" + csOBIds3.join(",") + ":after=" + ids4.join(",")
                + ":changed=" + (csObSame(csOBIds3, ids4) ? 0 : 1) + ":cd=" + csN2Cd(mc) + ":sold=" + csSoldCsv(mc)
                + ":detailChanged=" + (csShelfDetail(mc) === csOBDet3 ? 0 : 1) + ":detail=" + csShelfDetail(mc));
            csShot(mc, "o_b_reroll", csOBT);
            csOBP = 3; csOBT = 0;
        }
        return;
    }
    if (P === 3) {
        // ── ② 关店期间跨 0 点：关店前先预约 6 秒后的到点 ──
        if (csOBT === 1) {
            csOBIds5 = csShelfIds(mc);
            csOBDet5 = csShelfDetail(mc);
            cs("evt=o_b_close2_pre:ids=" + csOBIds5.join(",") + ":sold=" + csSoldCsv(mc) + ":detail=" + csOBDet5);
        } else if (csOBT === 6) {
            var h2 = csStr(function () { return csCloseBy_(mc, "o_b_close2"); });
            cs("evt=o_b_close2:how=csCloseBy_" + h2);
            // ⚠ 周期**不在**这里武装：第 10 轮实测客户端关屏后服务端 containerMenu 仍是 CardShopMenu
            //   ⇒ 必须等「服务端 shut 收屏 + menuraw 自证 cls=none」之后，在 P4 里再武装。
            csOBP = 4; csOBOutc = 0;
        }
        return;
    }
    if (P === 5) {
        // ── ③ 三开（店外已跨 0 点）：必须是新的一批卡 ──
        if (csOBT === 1) {
            var ids6 = csShelfIds(mc);
            csScreen(mc, "o_b_open3");
            cs("evt=o_b_open3:ids=" + ids6.join(",") + ":prev=" + csOBIds5.join(",")
                + ":changed=" + (csObSame(csOBIds5, ids6) ? 0 : 1) + ":cd=" + csN2Cd(mc)
                + ":detailChanged=" + (csShelfDetail(mc) === csOBDet5 ? 0 : 1) + ":detail=" + csShelfDetail(mc));
            csShot(mc, "o_b_open3", csOBT);
        } else if (csOBT === 10) {
            csCloseBy_(mc, "o_b_end");
            cs("evt=o_b_end:how=o_b_end");
            csOBP = 9;
        }
        return;
    }
}

/** 789「关店期间」子阶段：本函数在 tick 循环里位于 inShop 判定**之前** —— 关店时也能推进。
 *  事实依据：到点重抽由服务端 CardShopCycleTicker 驱动，与客户端是否开着界面无关，这正是 §1.3 要证的。 */
function csRunOBOut(mc) {
    csOBOutc++;
    var P = csOBP;
    if (P === 1) {
        if (csOBOutc === 1) {
            cs("evt=o_b_out1:bal=" + csN2Bal());
            csCmd(mc, "astralshop bal O 789", "o_b_bal_re", csOBOutc);   // 复位哨兵（重开时必须仍是 789）
        } else if (csOBOutc === 8) {
            csCmd(mc, "astralshop openkeep O", "o_b_reopen", csOBOutc);
            csOBOut = false; csOBP = 2; csOBT = 0;
        } else if (csOBOutc > 80) {
            cs("evt=o_b_out_to:phase=1:ticks=" + csOBOutc);
            csOBOut = false; csOBP = 9;
        }
        return;
    }
    if (P === 4) {
        if (csOBOutc === 1) {
            cs("evt=o_b_out_wait_begin:bal=" + csN2Bal());
            // 任务O §1.3（2026-10-09 第 10 轮教训）：客户端关屏没能让服务端 containerMenu 归位
            //  ⇒ 先用服务端命令 shut **只收屏不开户**收掉，再用 menuraw 自证 cls=none，最后才武装本轮周期。
            csCmd(mc, "astralshop shut O", "o_b_out_shut", csOBOutc);
        } else if (csOBOutc === 10) {
            csCmd(mc, "astralshop menuraw O", "o_b_out_menuraw", csOBOutc);   // 只读：期望 AP_O_MENU:cls=none
        } else if (csOBOutc === 20) {
            csCmd(mc, "astralshop cycleset O 6", "o_b_cycle6c", csOBOutc);    // 收屏确认后才武装（6 秒后到点）
        }
        if (csOBOutc % 40 === 0) { cs("evt=o_b_out_wait:t=" + csOBOutc + ":bal=" + csN2Bal()); }
        if (csOBOutc === 170) {
            // 8.5s：服务端 tick 早已在**关店状态**下跨过 0 点（证据在 latest.log 的 CARD_SHOP_CYCLE 行）
            cs("evt=o_b_out_done:ticks=" + csOBOutc + ":bal=" + csN2Bal());
            csCmd(mc, "astralshop openkeep O", "o_b_reopen3", csOBOutc);
            csOBOut = false; csOBP = 5; csOBT = 0;
        } else if (csOBOutc > 300) {
            cs("evt=o_b_out_to:phase=4:ticks=" + csOBOutc);
            csOBOut = false; csOBP = 9;
        }
        return;
    }
}

// ══════════════════ 任务Q 791（2026-10-09）：转账页完全独立 / 按钮去文字 / tooltip 只留名字 / 居中与对齐 / 立牌新文案 / 经商刷钱封堵 ══════════════════
/** 转账页探针安全读取（异常一律返回 EX:…/-999，绝不抛；screen 必须真是商店屏） */
function csQIso(mc) { return csStr(function () { return mc.screen.transferIsolationProbe(); }); }
function csQLay(mc) { return csStr(function () { return mc.screen.transferLayoutProbe(); }); }
function csQKeys(mc) { return csStr(function () { return mc.screen.transferTextKeysCsv(); }); }
function csQHov(mc) { return csStr(function () { return mc.screen.hoverState(); }); }
function csQMp(mc) { return csStr(function () { return mc.screen.mouseArgs(); }); }
function csQView(mc) { return csNum(function () { return mc.screen.transferViewVisible() ? 1 : 0; }); }
function csQMain(mc) { return csNum(function () { return mc.screen.mainViewPainted() ? 1 : 0; }); }
function csQFocus(mc) { return csStr(function () { return mc.screen.transferFocusKind(); }); }
function csQTex(mc) { return csStr(function () { return mc.screen.mainIconTextures(); }); }
function csQHour(mc) { return csNum(function () { return mc.screen.hourglassFrame(); }); }
/** 真光标下的菜单槽位（AbstractContainerScreen#getSlotUnderMouse 是 public ⇒ 不必改 Java） */
function csQSlotUnder(mc) {
    try {
        var s = mc.screen.getSlotUnderMouse();
        if (s == null) { return "none"; }
        var id = "empty";
        try { var st = s.getItem(); if (st != null && !st.isEmpty()) { id = csId(st); } } catch (e1) { id = "EX"; }
        return "idx" + csNum(function () { return s.index; }) + ":" + id;
    } catch (e) { return "EX"; }
}
/** 菜单里「物品 id 含 needle」的第一个槽的 GUI 相对中心 ⇒ 真光标停靠点由产品侧真实槽位算出（不猜常量） */
function csQFindSlot(mc, needle) {
    try {
        var m = mc.screen.getMenu();
        var n = csNum(function () { return m.slots.size(); });
        if (n < 0) { return null; }
        for (var i = 0; i < n; i++) {
            var st = m.slots.get(i).getItem();
            if (st == null || st.isEmpty()) { continue; }
            if (csId(st).indexOf(needle) >= 0) {
                var sx = csNum(function () { return m.slots.get(i).x; });
                var sy = csNum(function () { return m.slots.get(i).y; });
                // ⚠ 任务Q 首跑实测：货架卡本身也是菜单槽位（CardShopMenu:70-71 HIDDEN_X/HIDDEN_Y = -10000，:143 addSlot(ShelfSlot…)）
                //   ⇒ 循环会先命中它并算出 -9992，真光标被停到屏幕外。只认屏内槽位（玩家物品栏 x,y ∈ [0,300)）。
                if (sx < 0 || sy < 0 || sx > 400 || sy > 400) { continue; }
                return [sx + 8, sy + 8];
            }
        }
    } catch (e) { }
    return null;
}
function csQSlotUnderHas(mc, needle) {
    try {
        var s = mc.screen.getSlotUnderMouse();
        if (s == null) { return false; }
        var st = s.getItem();
        if (st == null || st.isEmpty()) { return false; }
        return (csId(st).indexOf(needle) >= 0);
    } catch (e) { return false; }
}
/** 真光标悬停请求：先落 evt=q_need_hover（OS 脚本据此停靠），逐 tick 轮询命中判据，命中即截图 */
function csQHoverWait(mc, label, dx, dy, kind) {
    // ⚠ Rhino 作用域踩坑（任务Q 首跑实测）：var 声明放在 if 块内却在块外使用 ⇒
    //   ReferenceError: "nd" is not defined，异常每 tick 复现 ⇒ csRunQ 永卡在该步（阶段 1/2 全不执行）。
    //   故所有局部变量声明一律提到函数体顶部。
    var nd = (label === "atk") ? "attack_card" : "parunan_sign";
    if (csQHoverLabel !== label) {
        csQHoverLabel = label; csQHoverT = 0;
        if (kind === "item") {
            var t = csQFindSlot(mc, nd);
            if (t == null) {
                // 找不到（物品未真进背包 / 槽位全被过滤）⇒ 不发假停靠点，直接跳过该步，
                //   避免真光标被停到 (0,0) 之后空等超时、连带拖死整个阶段。
                cs("evt=q_slot_missing:label=" + label + ":needle=" + nd + ":slot=" + csQSlotUnder(mc));
                csQHoverLabel = "";
                return -1;
            }
            dx = t[0]; dy = t[1];
        }
        cs("evt=q_need_hover:label=" + label + ":dx=" + dx + ":dy=" + dy + ":kind=" + kind
            + ":mp=" + csQMp(mc) + ":slot=" + csQSlotUnder(mc));
        return 0;
    }
    csQHoverT++;
    var ok = false;
    if (kind === "ex") { ok = (csQHov(mc).indexOf("ex=1") >= 0); }
    else if (kind === "tp") { ok = (csQHov(mc).indexOf("tp=1") >= 0); }
    else if (kind === "neutral") {
        // 「真光标已离开一切可交互区域」判据（**与坐标口径无关**：只看游戏侧本帧 render 收到的 GUI 空间鼠标）：
        //  x >= 216（转账面板右界 = TP_X+TP_PANEL_W = 214 之外）、y >= 180（物品栏末行 160..176 之下）。
        //  主视图再叠一层硬条件：两个按钮/货架卡框/任何菜单槽位都不许悬停（否则截图会带 tooltip 或高亮）。
        //  ⚠ 空间口径（任务Q 首跑实测校准）：csQMp 的 mouseArgs() 与 getGuiLeft/getGuiTop **同空间**（缩放后屏幕坐标），
        //   面板右界 = gx + TP_X + TP_PANEL_W = gx + 214，物品栏末行底 = gy + 176 ⇒ 判据必须带上 gx/gy。
        var ngx0 = csNum(function () { return mc.screen.getGuiLeft(); });
        var ngy0 = csNum(function () { return mc.screen.getGuiTop(); });
        var nps = ("" + csQMp(mc)).split(",");
        var ngx = parseFloat(nps[0]); var ngy = parseFloat(nps[1]);
        ok = (ngx >= ngx0 + 216 && ngy >= ngy0 + 180);
        if (ok && csQView(mc) !== 1) {
            var nhv = csQHov(mc);
            ok = (nhv.indexOf("ex=0") >= 0 && nhv.indexOf("tp=0") >= 0 && nhv.indexOf("card=-1") >= 0
                  && csQSlotUnder(mc) === "none");
        }
    }
    else { ok = csQSlotUnderHas(mc, nd); }
    if (ok) {
        csShot(mc, "q_hover_" + label, csQHoverT);
        cs("evt=q_hover_ok:label=" + label + ":t=" + csQHoverT + ":hover=" + csQHov(mc)
            + ":mp=" + csQMp(mc) + ":slot=" + csQSlotUnder(mc));
        csQHoverLabel = "";
        return 1;
    }
    if (csQHoverT > 400) {   // 400t = 20s：四点自校准约需 4~8 次停靠，此窗口足够；超时即放弃该步（不阻塞后续步骤）
        cs("evt=q_hover_timeout:label=" + label + ":hover=" + csQHov(mc) + ":mp=" + csQMp(mc)
            + ":slot=" + csQSlotUnder(mc));
        csQHoverLabel = "";
        return -1;
    }
    return 0;
}
/** 791 主场景（跨开屏阶段机；csQP 保留、csQT 每次开屏归零） */
function csRunQ(mc, step) {
    // 真光标脚本自校准用：每 10t 报一次 geo（含 live cursorPx = MouseHandler 窗口客户区像素）
    if (csQT % 10 === 0) { csGeo(mc, "q_cal"); }
    // ── 阶段 0：视觉取证（主视图 → 两个按钮 tooltip → 攻击牌/立牌 tooltip → 转账页 → 返回）──
    if (csQP === 0) {
        if (csQS === 0) {
            // 先请 OS 脚本把真光标停到「空白区」（GUI 右下角 280,182）并**确认到达**，再截主视图基线：
            //  真光标脚本的四点自校准会在 GUI 上逐点停靠，若不等它停稳，基线截图可能带出无关卡面 tooltip。
            if (csQHoverWait(mc, "neutral0", 280, 182, "neutral") !== 0) {
            csQBalBase = csN2Bal();     // 装立牌**前**的余额基线（阶段 1 的 Δ 全部相对它）
            csScreen(mc, "q_main");
            cs("evt=q_main:opens=" + csQOpens + ":view=" + csQView(mc) + ":main=" + csQMain(mc) + ":iso=" + csQIso(mc)
                + ":lay=" + csQLay(mc) + ":keys=" + csQKeys(mc) + ":hover=" + csQHov(mc) + ":mp=" + csQMp(mc)
                + ":tex=" + csQTex(mc) + ":hour=" + csQHour(mc) + ":bal=" + csN2Bal());
            csShot(mc, "q_main", csQT);
            csQS = 1;
            }
            return;
        }
        if (csQS === 1) { if (csQHoverWait(mc, "ex", 27, 125, "ex") !== 0) { csQS = 2; } return; }
        if (csQS === 2) { if (csQHoverWait(mc, "tp", 64, 125, "tp") !== 0) { csQS = 3; } return; }
        if (csQS === 3) { csCmd(mc, "give @s astral_dice:attack_card_epic 1", "q_give_atk", csQT); csQS = 4; csQW = 0; return; }
        if (csQS === 4) { csQW++; if (csQW < 12) { return; } if (csQHoverWait(mc, "atk", 0, 0, "item") !== 0) { csQS = 5; } return; }
        if (csQS === 5) { csCmd(mc, "give @s astral_dice:parunan_sign 1", "q_give_sign", csQT); csQS = 6; csQW = 0; return; }
        if (csQS === 6) { csQW++; if (csQW < 12) { return; } if (csQHoverWait(mc, "sign", 0, 0, "item") !== 0) { csQS = 7; } return; }
        if (csQS === 7) { csClick(mc, 64, 125, "q_open_tp", csQT); csQS = 8; csQW = 0; return; }
        if (csQS === 8) {
            csQW++;
            if (csQW < 5) { return; }
            // 转账页基线同样要求真光标先离开面板：面板右界 = TP_X + TP_PANEL_W = 73 + 141 = 214，
            //  停在 (280,182) 既在面板外、又在物品栏格位之外 ⇒ 面板上不会有任何 tooltip / 悬停高亮。
            if (csQHoverWait(mc, "neutralTp", 280, 182, "neutral") !== 0) { csQS = 9; }
            return;
        }
        if (csQS === 9) {
            csScreen(mc, "q_tp");
            cs("evt=q_tp:view=" + csQView(mc) + ":main=" + csQMain(mc) + ":iso=" + csQIso(mc) + ":lay=" + csQLay(mc)
                + ":keys=" + csQKeys(mc) + ":focus=" + csQFocus(mc) + ":hover=" + csQHov(mc) + ":mp=" + csQMp(mc)
                + ":bal=" + csN2Bal());
            csShot(mc, "q_tp", csQT);
            csQS = 10; csQW = 0; return;
        }
        if (csQS === 10) { csClick(mc, 190, 13, "q_back", csQT); csQS = 11; csQW = 0; return; }
        if (csQS === 11) {
            csQW++;
            if (csQW < 4) { return; }
            csScreen(mc, "q_back");
            cs("evt=q_back:view=" + csQView(mc) + ":main=" + csQMain(mc) + ":iso=" + csQIso(mc) + ":keys=" + csQKeys(mc) + ":bal=" + csN2Bal());
            csShot(mc, "q_back", csQT);
            // 立牌功能的**总闸门**：必须先佩戴骰子（CurioSlotUtil.hasDiceEquipped），再往 stand 槽放立牌
            csCmd(mc, 'astralshop equip O dice "astral_dice:dice"', "q_equip_dice", csQT);
            csQS = 12; csQW = 0; return;
        }
        if (csQS === 12) {
            csQW++;
            if (csQW < 3) { return; }
            csCmd(mc, 'astralshop equip O stand "astral_dice:parunan_sign"', "q_equip_stand", csQT);
            csQS = 13; csQW = 0; return;
        }
        if (csQS === 13) {
            csQW++;
            if (csQW < 3) { return; }
            csCmd(mc, "astralshop curio O", "q_curio", csQT);   // AP_O_CURIO 机器行 = 佩戴证据
            csQS = 14; csQW = 0; return;
        }
        if (csQS === 14) {
            csQW++;
            if (csQW < 3) { return; }
            csQP = 1; csQT = 0; csQCycles = 0; csQDeltaSeq = ""; csQLastBal = -1;
            cs("evt=q_phase1_beg:opens=" + csQOpens + ":base=" + csQBalBase + ":bal=" + csN2Bal() + ":shelf=" + csShelfDetail(mc));
            csCmd(mc, "astralshop shut O", "q_p1_shut", csQT);   // 服务端收屏 ⇒ 下一次开店会重建菜单（结算进店奖励）
            return;
        }
        return;
    }
    // ── 阶段 1：同批卡反复「收屏 → 重开」100 轮（只允许第 1 轮 +1）──
    if (csQP === 1) {
        csQT++;
        if (csQT === 3) {
            var b = csN2Bal();
            var d = b - csQBalBase;
            csQCycles++;
            csQDeltaSeq += (csQDeltaSeq === "" ? "" : ",") + d;
            cs("evt=q_cyc:n=" + csQCycles + ":open=" + csQOpens + ":bal=" + b + ":delta=" + d
                + ":shelf=" + csShelfDetail(mc) + ":hour=" + csQHour(mc));
            return;
        }
        if (csQT === 5) {
            if (csQCycles >= 100) {
                cs("evt=q_phase1_end:cycles=" + csQCycles + ":opens=" + csQOpens + ":base=" + csQBalBase
                    + ":bal=" + csN2Bal() + ":delta=" + csQDeltaSeq);
                csQP = 2; csQT = 0; csQP2 = 0;
                return;
            }
            csCmd(mc, "astralshop shut O", "q_p1_shut", csQT);
            return;
        }
        return;
    }
    // ── 阶段 2：沙漏归 0 换新批（要求 14 ②）──
    if (csQP === 2) {
        csQT++;
        if (csQP2 === 0) {
            if (csQT === 1) { csCmd(mc, "astralshop cycleset O 0", "q_p2_cyc0", csQT); return; }
            if (csQT === 25) {
                csQLastBal = csN2Bal();
                cs("evt=q_newbatch:shelf=" + csShelfDetail(mc) + ":hour=" + csQHour(mc) + ":bal=" + csQLastBal);
                csShot(mc, "q_newbatch", csQT);
                csCmd(mc, "astralshop shut O", "q_p2_shut0", csQT);
                return;
            }
            return;
        }
        if (csQP2 === 1) {
            if (csQT === 3) {
                var b1 = csN2Bal();
                cs("evt=q_p2_open1:bal=" + b1 + ":delta=" + (b1 - csQLastBal) + ":shelf=" + csShelfDetail(mc));
                csQLastBal = b1;
                return;
            }
            if (csQT === 5) { csCmd(mc, "astralshop shut O", "q_p2_shut1", csQT); return; }
            return;
        }
        if (csQP2 === 2) {
            if (csQT === 3) {
                var b2 = csN2Bal();
                cs("evt=q_p2_open2:bal=" + b2 + ":delta=" + (b2 - csQLastBal) + ":shelf=" + csShelfDetail(mc));
                csQLastBal = b2;
                return;
            }
            if (csQT === 5) { csQP = 9; csQT = 0; return; }   // 不关屏：阶段 9 要在店内收尾
            return;
        }
        return;
    }
    // ── 阶段 9：收尾 ──
    if (csQP === 9) {
        csQT++;
        if (csQT === 1) {
            cs("evt=q_end:opens=" + csQOpens + ":cycles=" + csQCycles + ":base=" + csQBalBase + ":bal=" + csN2Bal()
                + ":delta=" + csQDeltaSeq + ":shelf=" + csShelfDetail(mc));
            return;
        }
        // 收尾用**客户端**关屏（不再发服务端 shut：shut 会让屏幕先关掉 ⇒ csRunQ 不再被调用，csQT===20 永远到不了）
        if (csQT === 20) { cs("evt=q_close:how=" + csCloseBy_(mc, "q_end")); csQP = 100; return; }
        return;
    }
}
/** 791 店外钩子：关店期间每 tick 推进（负责重开与阶段记账） */
function csRunQOut(mc) {
    csQOutc++;
    if (csQOutc === 1) { cs("evt=q_out:phase=" + csQP + ":bal=" + csN2Bal()); }
    // ── 任务Q 阶段 100：手册页取证（要求 7 的「手册同页无黑箱说明」）──
    //   手册页必须由 Patchouli GUI 打开，而 Patchouli 屏不是商店屏 ⇒ csRunQ 不再被调用，只能在本钩子里推进。
    //   入口 = PatchouliAPI.openBookEntry(book, entry, page)，词条 = 攻击牌（特大）astral_dice:cards_attack/attack_card_epic。
    if (csQP === 100) {
        if (csQGuide < 0) { csQGuide = 1; }
        if (csQGuide === 1) {
            var gOk = 0, gErr = "-";
            try {
                var API_CLASS = Java.loadClass("vazkii.patchouli.api.PatchouliAPI");
                var RL_CLASS = Java.loadClass("net.minecraft.resources.ResourceLocation");
                API_CLASS.get().openBookEntry(
                    RL_CLASS.fromNamespaceAndPath("astral_dice", "astral_guide"),
                    RL_CLASS.fromNamespaceAndPath("astral_dice", "cards_attack/attack_card_epic"), 0);
                gOk = 1;
            } catch (eG) { gErr = String(eG); }
            cs("evt=q_guide_open:ok=" + gOk + ":err=" + gErr + ":scr=" + csClsName(mc.screen));
            csQGuide = 2; csQGuideT = 0;
            return;
        }
        csQGuideT++;
        if (csQGuideT === 40) {
            csShot(mc, "q_guide", -1);
            cs("evt=q_guide_shot:scr=" + csClsName(mc.screen) + ":bal=" + csN2Bal());
            csQGuide = 3;
        }
        return;
    }
    if (csQOutc >= 4) {
        csQOut = false;
        if (csQP === 1 || csQP === 2) {
            if (csQP === 2) { csQP2++; }
            csCmd(mc, "astralshop openkeep O", "q_reopen", -1);
            return;
        }
        cs("evt=q_out_end:phase=" + csQP + ":bal=" + csN2Bal());
        return;
    }
}

/** 792 任务V（2026-10-10）：立即补差取证 —— 阶段 0..5 与 7..8 在**界面开着**时推进，
 *  只有阶段 6 在店外（证「界面关着时拿到大酬宾也会立即补差」）。
 *  用户裁决口径：大酬宾只补差额牌、不归零计时器；立牌取下**不缩架**，重排只发生在沙漏归 0。 */
function csRunV(mc, step) {
    if (csVP === 100) { return; }
    csVT++;
    // ── 阶段 0：开屏基线 → 界面开着时装备经商立牌 ──
    if (csVP === 0) {
        if (csVT === 5) { csShape(mc, "v_open"); return; }   // 开屏后 5t 再读（同 tick 客户端货架可能尚未同步）
        // ⚠️ item 参数是 Brigadier string ⇒ 必须带引号（裸 id 会 ERROR_EXPECTED_START_OF_QUOTE）
        if (csVT === 10) { csCmd(mc, 'astralshop equip VE0 stand "astral_dice:parunan_sign"', "v_equip", csVT); return; }
        if (csVT === 25) {   // 装备后 15t（>1 个 10t 复检周期）⇒ 界面开着时的补差必须已完成
            csShape(mc, "v_equip");
            csCmd(mc, "astralshop menuraw VM1", "v_menu1", csVT);
            csCmd(mc, "astralshop shelfmix VM1", "v_mix1", csVT);
            csVP = 1; csVT = 0; return;
        }
        return;
    }
    // ── 阶段 1：界面开着时卸下 ⇒ **不缩架** ──
    if (csVP === 1) {
        if (csVT === 1) { csCmd(mc, "astralshop equip VE1 stand none", "v_unequip", csVT); return; }
        if (csVT === 30) {
            csShape(mc, "v_unequip");
            csCmd(mc, "astralshop menuraw VM2", "v_menu2", csVT);
            csVP = 2; csVT = 0; return;
        }
        return;
    }
    // ── 阶段 2：沙漏归 0 ⇒ 才按当时口径（无立牌）重排回 3 格 ──
    if (csVP === 2) {
        if (csVT === 1) { csCmd(mc, "astralshop cycleset VC0 0", "v_cycle", csVT); return; }
        if (csVT === 30) {
            csShape(mc, "v_cycle");
            csCmd(mc, "astralshop menuraw VM3", "v_menu3", csVT);
            csVP = 3; csVT = 0; return;
        }
        return;
    }
    // ── 阶段 3：界面**开着**时拿到大酬宾 ⇒ 立即补 2 促销格（计时器不归零）──
    if (csVP === 3) {
        if (csVT === 1) { csCmd(mc, "astralshop promo VP0 1200", "v_promo_open", csVT); return; }
        if (csVT === 20) {
            csShape(mc, "v_promo_open");
            csCmd(mc, "astralshop menuraw VM4", "v_menu4", csVT);
            csCmd(mc, "astralshop shelfmix VM4", "v_mix4", csVT);
            csVP = 4; csVT = 0; return;
        }
        return;
    }
    // ── 阶段 4：把促销窗口压到 20t 并等它过期 ⇒ 沙漏归 0 重排回 3 格 ──
    if (csVP === 4) {
        if (csVT === 1) { csCmd(mc, "astralshop promo VP1 20", "v_promo_short", csVT); return; }
        if (csVT === 80) { csCmd(mc, "astralshop cycleset VC1 0", "v_cycle2", csVT); return; }
        if (csVT === 110) {
            csShape(mc, "v_promo_expired");
            csCmd(mc, "astralshop menuraw VM5", "v_menu5", csVT);
            csVP = 5; csVT = 0; return;
        }
        return;
    }
    // ── 阶段 5：客户端关屏 ⇒ 交给店外钩子 ──
    if (csVP === 5) {
        if (csVT === 1) { cs("evt=v_inshop_end:opens=" + csVOpens + ":shelf=" + csShelfDetail(mc)); }
        if (csVT === 5) { cs("evt=v_close:how=" + csCloseBy_(mc, "v_end")); csVP = 6; csVT = 0; return; }
        return;
    }
    if (csVP === 6) { return; }              // 店外阶段由 csRunVOut 推进
    // ── 阶段 7：店外已补差 ⇒ 重开界面，客户端应读到 5 格 ──
    if (csVP === 7) {
        if (csVT === 6) { csShape(mc, "v_promo_closed"); return; }
        if (csVT === 20) { csCmd(mc, "astralshop menuraw VM6", "v_menu6", csVT); csVP = 8; csVT = 0; return; }
        return;
    }
    // ── 阶段 8：畸形存档（四列表长度不一致）不再 IOOBE（任务V §1.3-D3/D4）──
    if (csVP === 8) {
        if (csVT === 1) { csCmd(mc, "astralshop shelfbreak VB1 short", "v_break_short", csVT); return; }
        if (csVT === 10) { csCmd(mc, "astralshop shelfbreak VB2 long", "v_break_long", csVT); return; }
        if (csVT === 20) { csCmd(mc, "astralshop shelfbreak VB3 mix", "v_break_mix", csVT); return; }
        if (csVT === 30) { csCmd(mc, "astralshop cycleset VC2 0", "v_cycle3", csVT); return; }
        if (csVT === 70) {
            csShape(mc, "v_after_break");
            cs("evt=v_end:opens=" + csVOpens + ":shelf=" + csShelfDetail(mc));
            csVP = 100; return;
        }
        return;
    }
}
/** 792 店外钩子：关店期间推进「店外拿大酬宾 ⇒ 立即补差 ⇒ 重开界面」 */
function csRunVOut(mc) {
    csVOutc++;
    if (csVOutc === 1) { cs("evt=v_out:phase=" + csVP + ":bal=" + csN2Bal()); }
    if (csVP !== 6) { return; }
    csVT++;
    if (csVT === 1) { csCmd(mc, "astralshop promo VP2 1200", "v_promo_out", csVT); return; }
    if (csVT === 40) {
        csVP = 7; csVT = 0; csVOut = false; csVOutc = 0;
        csCmd(mc, "astralshop openkeep V", "v_reopen", 40);   // ⚠️ 必须是 openkeep：open 会立即自关屏
        return;
    }
}

ClientEvents.tick(event => {
    csTick++;
    var mc = null;
    try { mc = Mc_CLASS.getInstance(); } catch (e0) { return; }
    if (mc == null || mc.level == null) return;
    // ── 任务M 护栏（§1.3）：先算武装状态；未武装时除下面三行**只读诊断**外一律 return ──
    var live = csRefreshArm(mc);
    // ── 任务N 诊断打点（2026-10-09）──
    //  目的：第 2 次 SHOP-N 用例在 step=12 之后「AP_CSC 行全静默」但 csTick 仍推进（jei 行仍在），
    //  需要区分「tick 处理器在 if(!live) 处提前返回 ⇒ csStep 冻结」与「csRunN 内部静默 return」。
    //  仅在本探针已武装/已开屏时每 20 tick 落一行；未武装时完全不输出（保持惰性语义）。
    if (csTick % 20 === 0 && csHbOn(mc)) {
        var hbScr = "null";
        try { hbScr = csClsName(mc.screen); } catch (ehb) { hbScr = "EX"; }
        cs("evt=hb:tick=" + csTick + ":live=" + (live ? 1 : 0) + ":armed=" + (csArmed ? 1 : 0)
            + ":step=" + csStep + ":n=" + (csN ? 1 : 0) + ":phase=" + csNPhase + ":base=" + csNBase
            + ":bal=" + csNum(function () { return WalletState_CLASS.balance(); }) + ":scr=" + hbScr);
    }
    if (csTick === 40) {
        cs("evt=boot:tick=" + csTick + ":shopCls=" + (ShopScreen_CLASS == null ? "null" : "ok")
            + ":armed=" + (live ? 1 : 0) + ":file=" + csArmFile + ":why=" + csArmWhy);
    }
    if ((csTick === 60 || csTick % 600 === 0) && mc.screen == null) { csVis(mc); }
    if (csTick === 200 || (csTick - csJeiLast) >= 1200) { csJeiLast = csTick; csJei(); }
    if (!live) { return; }                      // 未武装：不关屏、不派发事件、不发载荷、不发命令、不截图
    // ── 任务N′ §4 ⑧⑨：创造栏排序 / JEI 配方查看器的取证必须在**商店屏之外**做（inShop!=1 分支会 return）──
    if (csTail) { csTailStep(mc, csTailTick + 1); return; }
    // 任务O §1.3 关键：789 的「关店期间」子阶段必须在商店屏**不在**时也推进（证「到点即刷」与是否在店内无关）
    if (csOB && csOBOut) { csRunOBOut(mc); return; }
    // 任务Q 791：同构钩子（关店期间推进「重开 / 阶段记账」）
    if (csQ && csQOut) { csRunQOut(mc); return; }
    // 任务V 792：店外拿到大酬宾也必须立即补差（用户裁决：只有一种「补差」判定）
    if (csV && csVOut) { csRunVOut(mc); return; }
    csForeignJanitor(mc);
    csObs(mc);                                  // 只读手势观察器（真人鼠标事件的可见后果）
    var inShop = 0;
    try { inShop = (mc.screen != null && (mc.screen instanceof ShopScreen_CLASS)) ? 1 : 0; } catch (e1) { inShop = -1; }
    if (inShop !== 1) {
        if (csArmed) {
            // §1 取证：商店屏消失时判因（本地清屏 vs 容器被关），落规定格式机器行 AP_SHOPCLOSE
            csArmed = false; csRun = false; csPoll = false; csDeny = false; csP0 = false; csHold = false; csNat = false;
            csM = false; csH = false; csG = false; csN = false; csN2 = false; csP = false;
            csDenyLastRed = -1; csDenyShots = 0;
            if (csOB) { csOBOut = true; csOBOutc = 0; }   // 任务O 789：进入「关店期间」子阶段（csOB 本身**不清**，阶段机跨开屏保留）
            if (csQ) { csQOut = true; csQOutc = 0; }       // 任务Q 791：同构（csQ / csQP 同样跨开屏保留）
            if (csV) { csVOut = true; csVOutc = 0; }       // 任务V 792：同构（csV / csVP 跨开屏保留）
            csCloseForensics(mc);
            cs("evt=closed:tick=" + csTick + ":step=" + csStep + ":opens=" + csOpens);
        }
        return;
    }
    if (!csArmed) {
        csArmed = true;
        csOpens++;
        csStep = 0;
        csHumanKeepLogged = false;
        csOpenBal = csNum(function () { return WalletState_CLASS.balance(); });
        csRun = (csOpenBal === csGate);          // 777 = 脚本化序列
        csHold = (csOpenBal === csHoldGate);     // 778 = 静置保持窗口
        csPoll = (csOpenBal === csPollGate);     // 779 = 任务L 主序列
        csDeny = (csOpenBal === csDenyGate);     // 780 = 余额不足闪烁
        csP0 = (csOpenBal === csP0Gate);         // 781 = P0 对照重演
        csNat = (csOpenBal === csNatGate);       // 782 = 自然 60s 轮询
        csM = (csOpenBal === csMGate);           // 783 = 任务M 跨 tick 真实屏幕事件拖拽
        csH = (csOpenBal === csHGate);           // 784 = 任务M 沙漏去数字取证
        csG = (csOpenBal === csGGate);           // 785 = 任务M 几何窗口
        csN = (csOpenBal === csNGate);           // 786 = 任务N 主视图按钮/转账面板 2×/返回键
        csN2 = (csOpenBal === csN2Gate); csN2SelT = -1;         // 787 = 任务N′ §4 实机重测（子代理 2026-10-09）
        csO = (csOpenBal === csOGate);                          // 788 = 任务O 静态取证
        csOB = (csOpenBal === csOBGate);                        // 789 = 任务O 货架持久化（阶段机跨开屏保留）
        csP = (csOpenBal === csPGate);                          // 790 = 任务P（新布局/层级/删金额/角标/对齐）
        // 任务Q 791：立牌返现会让余额 +1 ⇒ 只能用**余额带**匹配（791..891），不能用等值
        csQ = (csOpenBal >= csQGate && csOpenBal <= csQGateMax);
        csQT = 0; csQHoverLabel = ""; csQHoverT = 0; csQW = 0; csQOut = false;
        // 任务V 792：本场景余额全程 920（避开 csQ 的 [791,891] 带、也不触发立牌返现）⇒ 等值匹配即可
        csV = (csOpenBal === csVGate);
        csVT = 0; csVOut = false;
        if (csV) { csVOpens++; }
        if (csQ && (csQP >= 100 || !csQStarted)) { csQStarted = true; csQP = 0; csQS = 0; csQP2 = 0; csQCycles = 0; csQOpens = 0; csQDeltaSeq = ""; csQBalBase = -1; }
        if (csQ) { csQOpens++; }
        csOT = 0; csOP = 0; csOEx2 = 0; csOHoverDeadline = -1;   // 788 每次开屏复位（只开一次；⚠ 必须是数字 0，false !== 0 会让悬停轮询分支永不进入）
        if (csOB) { csOBT = 0; }                                // 789 **只**复位阶段内 tick（csOBP 必须跨开屏保留）
        csPP = 0; csPT = 0; csPPromoHit = -1; csPPromoTry = 0;    // 790 每次开屏复位
        csShotTag = csQ ? "t94" : (csP ? "t93" : ((csO || csOB) ? "t92" : (csN2 ? "t91" : (csN ? "t90" : ((csM || csH || csG) ? "t88" : "t87")))));
        // 任务N：跨开屏复位（否则上一轮的 n_nocd 把 phase 置 -1 ⇒ 本轮 step>12 后全程静默、什么证据都没有）
        csNPhase = 0; csNBase = -1; csNTpSeq = 0; csNViewLeft = -1;
        csObsSel = -2; csObsDrag = -2; csObsCarried = "?"; csObsSold = "";   // 手势观察器重新基线
        csHBurst = false; csHSeen = 0; csHTk = 0; csHShot = false;               // 面板+cd>0 瞬态取证复位
        cs("evt=open:tick=" + csTick + ":opens=" + csOpens + ":gate=" + csOpenBal + ":run=" + (csRun ? 1 : 0)
            + ":hold=" + (csHold ? 1 : 0) + ":poll=" + (csPoll ? 1 : 0) + ":deny=" + (csDeny ? 1 : 0)
            + ":p0=" + (csP0 ? 1 : 0) + ":nat=" + (csNat ? 1 : 0)
            + ":m=" + (csM ? 1 : 0) + ":h=" + (csH ? 1 : 0) + ":g=" + (csG ? 1 : 0)
            + ":n=" + (csN ? 1 : 0) + ":n2=" + (csN2 ? 1 : 0)
            + ":o=" + (csO ? 1 : 0) + ":ob=" + (csOB ? 1 : 0) + ":p=" + (csP ? 1 : 0)
            + ":q=" + (csQ ? 1 : 0) + ":" + csState(mc));
    }
    csStep++;
    if (csRun) {
        csRunFirst(mc, csStep);
        if (csStep === 112) { cs("evt=leaveauto:how=" + csCloseBy_(mc, "csRunFirst")); }
    } else if (csPoll) {
        csRunPoll(mc, csStep);
        if (csStep === 392) { cs("evt=poll_end:how=" + csCloseBy_(mc, "csRunPoll")); }
    } else if (csDeny) {
        csRunDeny(mc, csStep);
        if (csStep === 236) { cs("evt=deny_end:rearmer=" + csDenyRearms + ":how=" + csCloseBy_(mc, "csRunDeny")); }
    } else if (csP0) {
        csP0Reenact(mc, csStep);
    } else if (csNat) {
        csRunNat(mc, csStep);
        if (csStep === 1320) { cs("evt=nat_end:how=" + csCloseBy_(mc, "csRunNat")); }
    } else if (csM) {
        csRunM(mc, csStep);
    } else if (csH) {
        csRunH(mc, csStep);
    } else if (csG) {
        csRunG(mc, csStep);
    } else if (csN) {
        csRunN(mc, csStep);
    } else if (csN2) {
        csN2Step(mc, csStep);
    } else if (csO) {                       // 任务O 788
        csRunO(mc, csStep);
    } else if (csOB) {                      // 任务O 789
        csRunOB(mc, csStep);
    } else if (csP) {                       // 任务P 790
        csRunP(mc, csStep);
    } else if (csQ) {                       // 任务Q 791
        csRunQ(mc, csStep);
    } else if (csV) {                       // 任务V 792
        csRunV(mc, csStep);
    } else if (csHold) {
        // 静置保持(哨兵 778): 本模式**不**做 60t 兜底关屏, 用于验收「打开商店后静置不会自己关」
        if (csStep === 600) { cs("evt=holdend:opens=" + csOpens + ":step=" + csStep + ":how=" + csCloseBy_(mc, "csHold")); }
        else if (csStep % 40 === 0) { cs("evt=hold:opens=" + csOpens + ":step=" + csStep + ":" + csState(mc)); }
    } else if (!csHumanKeepLogged) {
        // ⚠️ P0 修复(2026-10-09): 旧版此处对**任何**非 777 的商店界面在 60t(≈3s) 无条件关屏,
        //    把「人手动打开的商店」3 秒后关掉(= 用户报的「打开几秒后被强制关闭」)。
        //    现在: 无哨兵(人手动) ⇒ 探针绝不代玩家关屏, 只记账; 只有显式武装(csRun/csHold)才自关。
        if (csStep === 60) {
            csHumanKeepLogged = true;
            cs("evt=humankeep:opens=" + csOpens + ":step=" + csStep + ":" + csState(mc));
        }
    } else if (csStep % 200 === 0) {
        cs("evt=idlekeep:opens=" + csOpens + ":step=" + csStep);
    }
});
