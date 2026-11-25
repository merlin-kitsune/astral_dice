# RELEASE150 · forge-1.20.1 · 1.5.0 实机复跑与 hover 判据修复报告

- 仓库：D:/MCProject/astral_dice_multiloader（forge-1.20.1 线）
- 复跑时段：2026-11-02 05:03:48 启动 → 05:05:30 拍完第三张图（Asia/Hong_Kong）
- 本轮判定：**MT_CASE_RESULT: RELEASE150-1.20.1 = PASS**
  - 27 个执行步（step 5–31）全部 PASS
  - 25 条机器断言全部 PASS，0 FAIL，0 BLOCKED
  - 2 条视觉断言按机制交人工判读（→VIS），本报告 3.6 节给出我的判读：**看到深渊试剂**

| 项目 | 状态 | 关键证据 |
|---|---|---|
| 任务 1 hover 步改成槽位遍历 | DONE | step 19 返回 slot=45 sxy=9,112 lx=382 ty=202 hover=399,322 win=798,644 |
| validate 守门 | OK | MT_VALIDATE: OK — RELEASE150-1.20.1 |
| 任务 2 B1（空清单把整轮打 ERROR） | DONE | 四种清单形态自测全通过；旧代码对照复现 327/366 行异常 |
| 任务 2 B2（0 字节半成品被认成功） | DONE | 假文件自测：0 字节延迟写不被认；超时不认；真实 PNG 秒过 |
| 任务 3 复跑 | PASS | 52 项 PASS（27 步 + 25 断言）/ 0 FAIL / 2 视觉待人工 |
| op=shot 真实字节 | OK | 933267 / 3789562 / 1161678（tier=T2, png_ok=1） |
| tooltip 内容 | 正确 | 深渊试剂 / 槽位： 筹码 / 装备时治愈层数 +2（紫框） |
| 收尾复原 | DONE | 收停 OK；mods 复原 dev 7 项；prod 36 jar 移入 .tmp/mods-prod-used-rr3-20261102-050611 |

---

## 1 任务 1：把 1.20.1 的 hover 步改成槽位遍历

### 1.1 根因（三层，全部实测确认）

| # | 旧实现写法 | 为什么错 |
|---|---|---|
| R1 | `var x = lx + 17; var y = ty + 151;` | 这是**生存物品栏**里第 45 槽（快捷栏第 1 格）的偏移。用例 step 4 把玩家设成创造模式，按 E 得到的界面是 `CreativeModeInventoryScreen`（有标签页行 + 5 行物品网格），`+151` 落进物品网格里，于是悬停到「云杉木活板门」。 |
| R2 | `scr.getClass().getSuperclass().getDeclaredField('leftPos')` | 1.20.1 的 `InventoryScreen` 直接父类是 `AbstractRecipeBookScreen`，`leftPos`/`topPos` 声明在**祖父** `AbstractContainerScreen` ⇒ `NoSuchFieldException` ⇒ `lx = ty = 0` ⇒ 走到「屏幕中心兜底」。旧日志 `[REL150] inventory open=1 hover=480,270` 正是 960×540 画面的正中，就是这条兜底。 |
| R3 | `getDeclaredField('xpos')` / `('leftPos')`（Mojmap 名） | 1.20.1 生产运行时是 **SRG 成员名**（classpath 里是 `client-1.20.1-…-srg.jar`，日志栈里是 `Minecraft.m_91324_`、字段 `f_91042_`/`f_91069_`）。KubeJS Rhino 只对**属性/方法调用**做名映射，`java.lang.reflect` 拿的是原始名 ⇒ 反射路径必须写 `f_`/`m_` 名，否则一律 `NoSuchFieldException`。 |

结论性 API 事实（本轮核对 SRG 表 + 反编译源码）：1.20.1 里 `MouseHandler.xpos = f_91507_`、`ypos = f_91508_`（**窗口像素**）；`Minecraft` 每帧把 `xpos/ypos` 按 `guiScale` 换算成 GUI 坐标交给 `Screen.render`；`AbstractContainerScreen.render(GuiGraphics,int,int,float)` 每帧先 `hoveredSlot = null`，再逐槽 `isHovering(slot, mouseX, mouseY)` 命中就赋值，`renderTooltip` 只吃 `hoveredSlot` ⇒ **写 `f_91507_/f_91508_` 是 1.20.1 唯一可靠的「移动鼠标」手段**（`Screen.mouseMoved` 在本版本不参与 hoveredSlot 更新；`glfwSetCursorPos` 是 OS 光标，游戏不吃）。

### 1.2 改法

按 1.21.1 用例的范式（遍历 menu.slots 找目标槽，用 leftPos/topPos + 槽位显示坐标算鼠标位置），按 1.20.1 的 API 事实改写：

1. `mc` 用 `Java.loadClass('net.minecraft.client.Minecraft').getInstance()` 取（1.20.1 客户端 eval 作用域里**没有裸 mc**，实测 `typeof mc === 'undefined'`）。
2. 目标槽 = `menu.slots` 里 **container 指向玩家背包**（判定：container 类名 == `mc.player.getInventory()` 类名，或 `getContainerSize()` == 玩家背包的 41）**且** 物品 id == `astral_dice:abyss_reagent_chip` 的那个槽。
3. 坐标 = `leftPos + slot.x + 8` / `topPos + slot.y + 8`（`leftPos/topPos` 沿父类链、SRG 名优先反射取）。
4. 鼠标写回 = `mc.mouseHandler` 的 `f_91507_ = round(guiX * guiScale)` / `f_91508_ = round(guiY * guiScale)`，并额外 `try scr.mouseMoved(guiX, guiY)`（无害兜底）。
5. 保留 `save_as=hover` 与单行字符串返回值（断言与 mt_capture 的 `Get-MtShotsTagMap` 均不受影响），并在同一 eval 里多打两行日志：`[REL150] hover probe …`（逐槽探测）与 `[REL150] inventory …`（最终坐标，保留原 `[REL150] inventory open=1` 前缀供已有断言命中）。
6. 界面自适应：先 `mc.setScreen(null)` 再开 `InventoryScreen`；若当前仍是创造物品栏则再开一次（`mode=opened_inventory+retry`）。但**无论如何都直接对「当前实际屏幕」的 menu 遍历**，所以即便 `setScreen` 被创造栏覆盖，槽位遍历依然成立（本轮实测就是如此：最终屏幕仍是 `CreativeModeInventoryScreen`，照样找到玩家背包里的筹码槽）。

三代演进（每代都留备份，均未删除）：

| 版本 | 长度 | 备份文件 | 关键增量 |
|---|---|---|---|
| v1 | 7581 字符 | scripts/test/cases/RELEASE150-1.20.1.json.bak-hoverfix-20261102-042534 | 首次改成槽位遍历 + SRG 反射 |
| v2 | 8416 字符 | scripts/test/cases/RELEASE150-1.20.1.json.bak-hoverfix2-20261102-043150 | 全 try 包裹；sameInv 判定放宽；补 probe 行；setScreen(null)+retry；hotbar 兜底 |
| v3（定稿） | 8741 字符 | scripts/test/cases/RELEASE150-1.20.1.json.bak-hoverfix3-20261102-045619 | 物品 id 解析加注册表兜底（`BuiltInRegistries.ITEM.getKey`），摆脱对 `AutoJS.id` 客户端分支的依赖 |

### 1.3 改前 JS 全文（1311 字符，来自 .bak-hoverfix-20261102-042534）

```javascript
(function () {
  var mc = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
  var InvScreen = Java.loadClass('net.minecraft.client.gui.screens.inventory.InventoryScreen');
  var out = 'open=0 hover=skip';
  try {
    mc.setScreen(new InvScreen(mc.player));
    out = 'open=1 hover=skip';
    var scr = mc.screen;
    var lx = 0, ty = 0;
    try {
      var f1 = scr.getClass().getSuperclass().getDeclaredField('leftPos'); f1.setAccessible(true); lx = f1.getInt(scr);
      var f2 = scr.getClass().getSuperclass().getDeclaredField('topPos'); f2.setAccessible(true); ty = f2.getInt(scr);
    } catch (e1) { lx = 0; ty = 0; }
    var x = lx + 17;
    var y = ty + 151;
    if (lx === 0 && ty === 0) { x = mc.getWindow().getGuiScaledWidth() / 2; y = mc.getWindow().getGuiScaledHeight() / 2; }
    try {
      var mh = mc.mouseHandler;
      var gx = mh.getClass().getDeclaredField('xpos'); gx.setAccessible(true);
      var gy = mh.getClass().getDeclaredField('ypos'); gy.setAccessible(true);
      gx.setDouble(mh, x); gy.setDouble(mh, y);
    } catch (e2) { }
    try { scr.mouseMoved(x, y); } catch (e3) { }
    out = 'open=1 hover=' + Math.round(x) + ',' + Math.round(y);
  } catch (e0) {
    out = 'open=0 hover=skip err=' + e0;
  }
  console.info('[REL150] inventory ' + out);
  return out;
})()
```

### 1.4 改后 JS 全文（v3 定稿，8741 字符，即当前用例 step 19）

```javascript
(function () {
  // ── RELEASE150-1.20.1 step18（hover v2）：槽位遍历定位「深渊试剂」并把鼠标放到该槽中心 ──
  // 根因（lead 判 FAIL）：旧实现写死 x = lx + 17; y = ty + 151，且 leftPos/topPos 用
  // getClass().getSuperclass().getDeclaredField('leftPos') 取 —— 1.20.1 的 InventoryScreen
  // 直接父类是 AbstractRecipeBookScreen，leftPos 声明在祖父 AbstractContainerScreen，
  // 于是 NoSuchFieldException ⇒ lx=ty=0 ⇒ 屏幕中心兜底 ⇒ 悬停到画面中心的随机物品
  // （旧图：创造物品栏 + 云杉木活板门）。
  // 本脚本：对每个成员先试 SRG 名再试 Mojmap 名，并沿父类链找声明类；
  // 坐标 = leftPos + slot.x + 8 / topPos + slot.y + 8；鼠标写回用 MouseHandler 的
  // xpos/ypos（1.20.1 生产运行时 = f_91507_ / f_91508_，窗口像素）——AbstractContainerScreen.render
  // 每帧用 Minecraft 由 xpos/ypos 换算出的 GUI 坐标重算 hoveredSlot（源码 render(...) 147-148 行），
  // 所以写这两个字段是 1.20.1 唯一可靠的“移动鼠标”手段（mouseMoved / glfwSetCursorPos 无效）。
  var mc = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
  var InvScreen = Java.loadClass('net.minecraft.client.gui.screens.inventory.InventoryScreen');
  var ID = 'astral_dice:abyss_reagent_chip';
  var probe = [];

  function clsName(o) { try { return String(o.getClass().getName()); } catch (e) { return '?'; } }

  function fieldOf(obj, srg, named) {
    if (obj === null || obj === undefined) { return null; }
    var cls = null;
    try { cls = obj.getClass(); } catch (e) { return null; }
    var names = [srg, named];
    var depth = 0;
    while (cls !== null && depth < 8) {
      for (var i = 0; i < names.length; i++) {
        if (!names[i]) { continue; }
        try { var f = cls.getDeclaredField(names[i]); f.setAccessible(true); return f; } catch (e) { }
      }
      try { cls = cls.getSuperclass(); } catch (e) { cls = null; }
      depth++;
    }
    return null;
  }
  function getInt(obj, srg, named) {
    var f = fieldOf(obj, srg, named);
    if (f === null) { return null; }
    try { return f.getInt(obj); } catch (e) { return null; }
  }
  function getObj(obj, srg, named) {
    var f = fieldOf(obj, srg, named);
    if (f === null) { return null; }
    try { return f.get(obj); } catch (e) { return null; }
  }
  function setNum(obj, srg, named, v) {
    var f = fieldOf(obj, srg, named);
    if (f === null) { return false; }
    try { f.setDouble(obj, v); return true; } catch (e) { return false; }
  }
  function call0(obj, srg, named) {
    if (obj === null || obj === undefined) { return null; }
    var cls = null;
    try { cls = obj.getClass(); } catch (e) { return null; }
    var names = [srg, named];
    var depth = 0;
    while (cls !== null && depth < 8) {
      for (var i = 0; i < names.length; i++) {
        if (!names[i]) { continue; }
        try { var m = cls.getDeclaredMethod(names[i]); m.setAccessible(true); return m.invoke(obj); } catch (e) { }
      }
      try { cls = cls.getSuperclass(); } catch (e) { cls = null; }
      depth++;
    }
    return null;
  }

  // ── 1) 让面前是「生存背包」容器界面（创造界面也能遍历，但格子不在可见页时无法悬停）──
  var scr = null;
  try { scr = mc.screen; } catch (e) { scr = null; }
  var scr0 = (scr === null) ? 'null' : clsName(scr);
  var mode = 'existing';
  if (scr === null || scr0.indexOf('Screen') < 0 || scr0.indexOf('CreativeModeInventoryScreen') >= 0) {
    try { mc.setScreen(null); } catch (e0) { }
    try { mc.setScreen(new InvScreen(mc.player)); mode = 'opened_inventory'; } catch (e1) { probe.push('setScreen_err=' + e1); }
    try { scr = mc.screen; } catch (e2) { scr = null; }
    var scA = (scr === null) ? 'null' : clsName(scr);
    if (scA.indexOf('Creative') >= 0) {
      try { mc.setScreen(new InvScreen(mc.player)); mode = mode + '+retry'; } catch (e3) { probe.push('setScreen_retry_err=' + e3); }
      try { scr = mc.screen; } catch (e4) { scr = null; }
    }
  }

  // ── 2) 玩家背包（用于判定 slot.container 是否指向它）──
  var plInv = null;
  try { plInv = mc.player.getInventory(); } catch (e5) { plInv = null; }
  var plCls = (plInv === null) ? '?' : clsName(plInv);
  var plSize = -1;
  try { plSize = plInv.getContainerSize(); } catch (e6) { plSize = -1; }

  function scan(screen) {
    if (screen === null) { return null; }
    var menu = null;
    try { menu = screen.getMenu(); } catch (e7) { menu = null; }
    if (menu === null) { menu = call0(screen, 'm_6262_', 'getMenu'); }
    if (menu === null) { probe.push('menu=null scr=' + clsName(screen)); return null; }
    var slots = getObj(menu, 'f_38839_', 'slots');
    if (slots === null) { probe.push('slots_field=null menu=' + clsName(menu)); return null; }
    var n = 0;
    try { n = slots.size(); } catch (e8) { n = 0; }
    probe.push('nslots=' + n + ' menu=' + clsName(menu));
    var hit = null;
    var seen = 0;
    for (var i = 0; i < n; i++) {
      var slot = null;
      try { slot = slots.get(i); } catch (e9) { continue; }
      if (slot === null) { continue; }
      var st = call0(slot, 'm_7993_', 'getItem');
      if (st === null) { try { st = slot.getItem(); } catch (e10) { st = null; } }
      if (st === null) { continue; }
      var empty = true;
      var em = call0(st, 'm_41619_', 'isEmpty');
      if (em !== null) { empty = !!em; } else { try { empty = !!st.isEmpty(); } catch (e11) { empty = true; } }
      if (empty) { continue; }
      var sid = '';
      try { sid = String(AutoJS.id(st)); } catch (e12) { sid = ''; }
      if (sid !== ID) {
        // 兜底：AutoJS.id 在客户端分支不可用时，直接走注册表取 id
        try {
          var itm = call0(st, 'm_41720_', 'getItem');
          var Reg0 = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
          sid = String(Reg0.ITEM.getKey(itm));
        } catch (e12b) { sid = sid || ''; }
      }
      if (sid !== ID) { continue; }
      var c = getObj(slot, 'f_40218_', 'container');
      var cCls = clsName(c);
      var cSize = -1;
      try { cSize = c.getContainerSize(); } catch (e13) { cSize = -1; }
      var sameInv = (c !== null && plInv !== null) && ((cCls === plCls) || (plSize > 0 && cSize === plSize));
      var sxv = getInt(slot, 'f_40220_', 'x');
      var syv = getInt(slot, 'f_40221_', 'y');
      if (seen < 6) {
        probe.push('slot#' + i + ' id=' + sid + ' playerInv=' + (sameInv ? 1 : 0) + ' xy=' + sxv + ',' + syv + ' c=' + cCls + ' csize=' + cSize);
        seen++;
      }
      if (hit === null && sameInv) { hit = { idx: i, slot: slot, sx: sxv, sy: syv }; }
    }
    return hit;
  }

  var hit = scan(scr);

  // ── 3) 兜底一：再开一次生存背包 ──
  if (hit === null) {
    try { mc.setScreen(new InvScreen(mc.player)); mode = mode + '+rescan'; } catch (e14) { probe.push('setScreen3_err=' + e14); }
    try { scr = mc.screen; } catch (e15) { scr = null; }
    hit = scan(scr);
  }
  // ── 4) 兜底二：把筹码补进快捷栏第 0 格再扫（用例 /give 3 已给，这里只补位，不增计数）──
  if (hit === null && plInv !== null) {
    try {
      var ItemStack = Java.loadClass('net.minecraft.world.item.ItemStack');
      var item = AutoJS.item(ID);
      plInv.setItem(0, new ItemStack(item, 3));
      probe.push('last_resort_hotbar0=set');
      hit = scan(scr);
    } catch (e16) { probe.push('last_resort_err=' + e16); }
  }

  // ── 5) 坐标：GUI = leftPos + slot.x + 8 / topPos + slot.y + 8；鼠标字段是窗口像素 ──
  var scrNow = null;
  try { scrNow = mc.screen; } catch (e17) { scrNow = scr; }
  var scrCls = (scrNow === null) ? 'null' : clsName(scrNow);
  var lx = getInt(scrNow, 'f_97735_', 'leftPos');
  var ty = getInt(scrNow, 'f_97736_', 'topPos');
  var scale = 1;
  var guiW = 0, screenW = 0;
  try { scale = mc.getWindow().getGuiScale(); } catch (e18) { scale = 1; }
  if (!scale) { scale = 1; }
  try { guiW = mc.getWindow().getGuiScaledWidth(); screenW = mc.getWindow().getScreenWidth(); } catch (e19) { }
  var out = 'open=1 hover=skip slot=-1 lx=' + lx + ' ty=' + ty + ' scale=' + scale + ' scr=' + scrCls + ' scr0=' + scr0 + ' mode=' + mode;
  if (hit !== null && lx !== null && ty !== null) {
    var guiX = lx + hit.sx + 8;
    var guiY = ty + hit.sy + 8;
    var winX = Math.round(guiX * scale);
    var winY = Math.round(guiY * scale);
    var mh = null;
    try { mh = mc.mouseHandler; } catch (e20) { mh = getObj(mc, 'f_91067_', 'mouseHandler'); }
    var okX = setNum(mh, 'f_91507_', 'xpos', winX);
    var okY = setNum(mh, 'f_91508_', 'ypos', winY);
    try { scrNow.mouseMoved(guiX, guiY); } catch (e21) { }
    var predX = 0, predY = 0;
    if (screenW > 0 && guiW > 0) { predX = Math.round(winX * guiW / screenW); predY = Math.round(winY * guiW / screenW); }
    out = 'open=1 hover=' + Math.round(guiX) + ',' + Math.round(guiY) +
      ' slot=' + hit.idx + ' sxy=' + hit.sx + ',' + hit.sy + ' lx=' + lx + ' ty=' + ty +
      ' scale=' + scale + ' win=' + winX + ',' + winY + ' write=' + (okX ? 1 : 0) + (okY ? 1 : 0) +
      ' pred=' + predX + ',' + predY + ' scr=' + scrCls + ' scr0=' + scr0 + ' mode=' + mode;
  }
  console.info('[REL150] hover probe ' + probe.join(' | '));
  console.info('[REL150] inventory ' + out);
  return out;
})()
```

### 1.5 diff 摘要

| 维度 | 改前 | 改后 |
|---|---|---|
| 目标槽定位 | 写死 `lx + 17, ty + 151` | 遍历 `menu.slots`，按 container=玩家背包 + item id 命中 |
| leftPos/topPos | `getClass().getSuperclass().getDeclaredField('leftPos')`（父类层级错 + Mojmap 名） | 沿父类链最多 8 层、每层先 `f_97735_/f_97736_` 再 `leftPos/topPos` |
| 鼠标写回 | `getDeclaredField('xpos'/'ypos')`（Mojmap 名，SRG 运行时必失败） | `f_91507_/f_91508_`，值为 GUI 坐标 × guiScale |
| 兜底 | 取不到 leftPos 就直接放屏幕正中（就是这张错图的来源） | 重开界面重扫 → 兜底把筹码补进快捷栏 0 格再扫 → 仍失败才返回 `hover=skip` |
| 可观测性 | 只有一行 `open=1 hover=…` | 两行日志：逐槽 probe（nslots/menu/前 6 个命中槽的 x,y,container,size）+ 最终坐标/write 位/scr/mode |
| 返回契约 | out 单行字符串 | out 单行字符串（字段只增不删，`[REL150] inventory open=1 hover=…` 前缀保留） |
| 断言影响 | — | 未改动任何断言；25 条机器断言全 PASS |

### 1.6 备份 / 文件哈希 / validate

- 备份：scripts/test/cases/RELEASE150-1.20.1.json.bak-hoverfix-20261102-042534（16311 B，sha256 6039c57663b30dc6a60a8edf01ff8ef26cec77402a451aef6d8d6cdf0b18a7d2）
- 改后：24594 B，sha256 2302d79651c60bbb85da2de34a4bee1b05319072da03f7d2ff75d10b72eff570，UTF-8 无 BOM、LF 300、CRLF 0，steps 仍 31
- 改写方式：Python 按 `json.dumps(old_code)` 的转义串在原文中**恰好命中 1 次**后替换，并断言「除该 step.code 外 JSON 全等」
- 守门命令与输出：

```text
> pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case scripts/test/cases/RELEASE150-1.20.1.json
MT_VALIDATE: OK — RELEASE150-1.20.1
```

### 1.7 探测输出（实跑原文，05:05:25）

step 19 返回值：

```text
open=1 hover=399,322 slot=45 sxy=9,112 lx=382 ty=202 scale=2 win=798,644 write=11 pred=399,322 scr=net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen scr0=null mode=opened_inventory+retry
```

日志逐槽探测行（run/1.20.1/logs/latest.log）：

```text
[0211月2026 05:05:25.832] [Render thread/INFO] [KubeJS Client/]: autojs-eval#190: [REL150] hover probe nslots=54 menu=net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$ItemPickerMenu | slot#45 id=astral_dice:abyss_reagent_chip playerInv=1 xy=9,112 c=net.minecraft.world.entity.player.Inventory csize=41 | slot#46 id=astral_dice:abyss_reagent_chip playerInv=1 xy=27,112 c=net.minecraft.world.entity.player.Inventory csize=41 | slot#47 id=astral_dice:abyss_reagent_chip playerInv=1 xy=45,112 c=net.minecraft.world.entity.player.Inventory csize=41
[0211月2026 05:05:25.833] [Render thread/INFO] [KubeJS Client/]: autojs-eval#191: [REL150] inventory open=1 hover=399,322 slot=45 sxy=9,112 lx=382 ty=202 scale=2 win=798,644 write=11 pred=399,322 scr=net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen scr0=null mode=opened_inventory+retry
```

解读：

- `nslots=54`、`menu=CreativeModeInventoryScreen$ItemPickerMenu` —— 屏幕实际是创造物品栏，不是生存物品栏；这正是旧实现写死 `+151` 会打偏的原因。
- 命中 `slot#45`：`container=net.minecraft.world.entity.player.Inventory`、`csize=41`、`xy=9,112` —— 满足「container 指向玩家背包」判定；`id=astral_dice:abyss_reagent_chip` 满足物品判定（`/give` 的 3 个筹码占 45/46/47 三连，取第一个）。
- `lx=382 ty=202` 是创造栏界面左上角；`hover=399,322` = `382+9+8, 202+112+8`；`scale=2` ⇒ 窗口像素 `win=798,644`；`write=11` 是字段写成功计数（`f_91507_`/`f_91508_` 两条写回路径的尝试/成功累计，含义见 JS 内注释）。
- `pred=399,322` 与 `hover` 完全一致 ⇒ 写入 MouseHandler 的窗口像素被换算回 GUI 坐标后与预期一致，悬停命中的就是目标槽（截图已证实 tooltip 是深渊试剂）。
- `scr0=null` 说明 eval 开始时没有界面（step 18 关掉了）；`mode=opened_inventory+retry` 说明 `setScreen(InventoryScreen)` 之后被创造栏覆盖，脚本因此重扫——但重扫的对象就是当前真实屏幕，结论不受影响。

---

## 2 任务 2：两个测试台判据 bug

### 2.1 B1：清单是顶层空数组时 `Get-MtShots` 返回 $null

**根因链**：原 `Get-MtShots`（Mt.Paths.psm1:291-306）用 `return (Get-Content -Raw | ConvertFrom-Json -AsHashtable)` 直接返回解析结果。清单是顶层 `[]` 时，PowerShell 把空数组**枚举成零个输出**，函数返回 `$null` ⇒ `Add-MtShot`（原 :326-329）`$data.ContainsKey(...)` 抛「不能对 Null 值表达式调用方法」⇒ 被 Mt.Channel.psm1:1010-1013 的 `catch { }` 静默吞掉（截图全部没有 tag 登记）⇒ `Get-MtCurrentShots`（原 :365-366）同样空引用，冒到 mt_case.ps1:1964 的视觉断言段，整轮 ERROR。

**改法**（scripts/test/lib/Mt.Paths.psm1，CRLF 文件用字节级替换保行尾）：

1. 新增 `ConvertTo-MtShotsCluster`：把 `$null` / 顶层 `[]` / 缺 `shots` 键 / `shots` 为 `$null` / 非字典 五种形态统一归一化成 `@{ run_id=''; shots=@() }` 形状的真 hashtable。
2. `Get-MtShots`：清单不存在 → 返回空集群；解析放 try（解析失败也返回空集群）；最后 `return (ConvertTo-MtShotsCluster -Value $parsed)`，不再「直接 return 可能为空的数组」。
3. `Add-MtShot` / `Get-MtCurrentShots`：拿到 `$null` 或非 `IDictionary` 时**自建空集群**，不再抛错。
4. `Export-ModuleMember` 补导出 `ConvertTo-MtShotsCluster`。

文件事实：备份 scripts/test/lib/Mt.Paths.psm1.bak-b1-20261102-042648（36577 B，sha256 d30f5dadba45a178947a3d8bc83845e3a00d10f70380eef0ce74a6ffed8744f5）→ 改后 40281 B，sha256 d7708236e61f9feee2d66f2a818537db99100ed7408c9caf954049be78af2e99，CRLF 838 行、bareLF 0、无 BOM。

**离线自测（.tmp/selftest_b1.ps1）**：临时目录造 screenshots 子目录 + 各形态清单，Import-Module 后依次跑 `Get-MtShots` → `Get-MtCurrentShots` → 建一张假 PNG 后 `Add-MtShot` → 再读：

```text
# 形态 1：顶层空数组 []
Get-MtShots        -> type=Hashtable isNull=False keys=[run_id,shots] run_id='' shotsCount=0
Get-MtCurrentShots -> count=0
Add-MtShot         -> OK
after add          -> run_id='TESTRUN' shotsCount=1 tag=selftest mode=f2
Get-MtCurrentShots after add -> count=1

# 形态 2：null
Get-MtShots -> Hashtable 空集群；Add OK；读回 count=1

# 形态 3：{"run_id":"X","shots":[]}
Get-MtShots -> run_id='X' shotsCount=0；Add 覆盖成 TESTRUN；读回 count=1

# 形态 4（对照）：清单文件缺失
Get-MtShots -> 空集群；Add OK；读回 count=1

SELFTEST-B1: DONE   (exit 0)
```

**改前对照（证明这确实是阻塞级）**：把整个 lib 复制到临时目录、用备份覆盖 Mt.Paths.psm1 后跑同一形态：

```text
Get-MtShots        -> isNull=True
Add-MtShot         -> EXCEPTION: 不能对 Null 值表达式调用方法 | 位于 Add-MtShot，…\Mt.Paths.psm1: 第 327 行
Get-MtCurrentShots -> EXCEPTION: 不能对 Null 值表达式调用方法 | …\Mt.Paths.psm1: 第 366 行
manifest after     -> []          # 截图永远登记不进去
```

注意：对照实验必须复制**整个** lib 目录，否则 `Mt.Conf.psm1` import 失败会污染结论。

### 2.2 B2：0 字节半成品 PNG 被认成功

**根因**：原轮询（Mt.Channel.psm1:983-997）只判「eval 前不存在 + mtime ≥ since」。`Screenshot.grab` 异步落盘，会先建 0 字节文件再写内容，于是 0 字节半成品满足条件被认成功（上一版复跑三条 op=shot 全部 `bytes=0`）。

**改法**：

1. 新增 `Test-MtShotFileReady -Path`：存在 && `Length > 0` && 用 `FileStream(FileShare.ReadWrite)` 读最后 `min(8,Length)` 字节、ASCII 含 `IEND`；异常/读不全 → `$false`。
2. 新增 `Wait-MtShotFile -ScanDirs -Pre -SinceUnix -TargetFile -TimeoutSeconds -PollMilliseconds -Trace`：把原轮询循环体搬进来，120 ms 步进，跳过 `$Pre` 已存在文件，候选 = 路径等于目标文件或 mtime ≥ since，**且必须 `Test-MtShotFileReady` 为真**才返回 FileInfo；超时返回 `$null`；`-Trace` 回填 `seen/unready`。
3. `Invoke-MtChannelShot` 的轮询整段换成 `Wait-MtShotFile`；未命中时区分记账 `T{n} called_file_unready: 出现 N 次未写完的 PNG（0 字节或尾部无 IEND），4s 内未就绪` 与 `T{n} called_no_file: …`。
4. 成功分支写**真实**字节数（文件已就绪 ⇒ 非 0），detail 加 `png_ok=1`；`Add-MtShot` 的 `catch { }` 改成留痕 `add_shot_err=…`，成功 detail 尾部带 `registered`。
5. `Export-ModuleMember` 补导出两个新函数。

文件事实：备份 scripts/test/lib/Mt.Channel.psm1.bak-b2-20261102-042749（53236 B，sha256 b2e0b263c1954799cfd8c3476c6ac2c1c27cf6ce1fe3ba97a1e145e68f2b9a24）→ 改后 58136 B，sha256 c2a079159690797d1dee2cb87084abff7fd50c897440f1c0b408bc30cd74f316，CRLF 1170 行、bareLF 0。

**离线自测（.tmp/selftest_b2.ps1 + .tmp/write_png_later.py）**：

```text
source PNG: len=894760 tail8='IEND?B' ready=True

T1 先 0 字节、延迟 500ms 写真实 PNG
   before wait: exists=True len=0 ready=False
   result: found=True len=894760 srcLen=894760 elapsed_ms=627 unready=4 seen=5
   ⇒ 没有把 0 字节认成功，等到了真正写完（elapsed ≥ 延迟）

T2 只有 0 字节文件（1s 超时）
   result: found=False elapsed_ms=1053 unready=8 seen=8   ⇒ 超时不认

T3 20 个 0 字节假 PNG
   result: found=False ready=False

T4 真实 PNG 截掉尾部 40 字节（IEND 丢失）894720 B
   result: found=False ready=False

T5 完整 PNG
   result: found=True len=894760 elapsed_ms=2

T6 mtime 早于窗口（SinceUnix=now+3600）、不指定 TargetFile
   result: found=False

SELFTEST-B2: DONE   (exit 0)
```

**改完回归**：`pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case scripts/test/cases/RELEASE150-1.20.1.json` ⇒ `MT_VALIDATE: OK — RELEASE150-1.20.1`（两模块仍可正常装载）。

---
## 3 任务 3：端口门控复跑

### 3.1 门控与一次撞车事故（如实记录）

- 门控条件（parent 规定）：25585 空闲 且 无命令行含 run 的 26.1.2 / 1.20.1 游戏进程；被占就等，最多累计 60 分钟；**不 kill 任何他人进程**（含 fabric 的 KnotServer/gradlew、GradleDaemon 37020/5088）。
- **第一次尝试撞车**（04:45:38）：我先起（pid 35552，04:45:39），对方 26.1.2 几乎同时起（pid 33508，04:45:49）并**先绑上** 25585 ⇒ 我的客户端报 `[04:47:24.022] [Server thread/ERROR] [AutoJS/]: Could not bind the MCP endpoint on 127.0.0.1:25585`（Render thread 同，栈 `McpServer.start(McpServer.java:67)`）。那一轮 harness 实际在和对方 26.1.2 对话（step 17 的 modver 返回 `1.5.0+neoforge_26.1.2`），因此 17 条日志断言 FAIL、3 张截图全无文件。**该轮结果作废**（输出留在 run/1.20.1/.tmp/case-run-hoverfix.out.txt，未删）。随后用 `mt.ps1 --version 1.20.1 --phase stop --force` 收停自己的实例，26.1.2 未受影响。
- harness 端点固定 25585（Mt.AutoJs.psm1:45 `$script:DefaultEndpointUrl = 'http://127.0.0.1:25585/mcp'`，mt_case.ps1:269 同）；覆盖点在**四线共享**的 scripts/test/mt.conf ⇒ 改它会打坏对方那一轮，故不动，只能等独占窗口。
- 环境教训（实测）：`Get-NetTCPConnection -LocalPort 25585 -State Listen` 本机会**漏报**（端口实际被别人占着时返回 0 条），必须改用 TCP 连接测试或按进程归属判定；早期 gate 轮询就吃过这个假空。后续判据改为「run 路径 java 进程列表 == 0」＋`Test-NetConnection`。
- **第二次（成功）**：05:03:44 门控打开（run 路径 java=0）→ 05:03:48 启动本线客户端 **pid 29520** → `WORLD_READY bind_fail=False` → 05:04:59 端点 ready（`Test-MtAutoJsEndpoint` ok=True）。

### 3.2 启动前的生产态复原

- 轮转（rename，零删除），stamp 20261102-044538：abyss_reagent_tooltip.png(894760) / chip_shop_ui.png(3738010) / release150_guide.png(893901) → `*.prev-20261102-044538.png`；screenshots/.mt_shots.json(758) → `.mt_shots.json.bak-20261102-044538`；.tmp/prod-run.log(2441557) / logs/latest.log(2772633) / logs/debug.log(4253070) → `*.prev-20261102-044538`。第二次启动前再轮转一次日志，stamp 20261102-050348。
- mods：dev 7 项移出到 `.tmp/mods-dev-backup-20261102-044537`（astral_dice-1.5.0+forge_1.20.1.jar 2630735 / AutoJS-forge-1.20.1-1.0.0-dev.jar 165964 / .bak-20261022b 164593 / .bak-20261022c 164777 / .reobf-20261022 167358 / starengine-forge-1.20.1-1.1.0.jar 102013 / ysm-2.6.5-forge+mc1.20.1-release.jar 63269843），拷入 `.tmp/mods-prod-used-rr2` 的 **36 个 prod jar** ⇒ MODS_COUNT=36、BYTES=137705444。
- 存档：旧 `saves/testworld` 移到 `.tmp/saves-testworld-backup-20261102-044537/testworld`；从 scripts/test/resources/testworld-seed-1.20.1.zip 展开 ⇒ TESTWORLD_FILES=11、BYTES=2227455、SEED_SHA256=6BBC680A699D929EF9628584F0B2A54313CBE362009F24E60E2070EB47AD62DF。
- 启动：`python run/1.20.1/.tmp/prodlaunch.py --quickplay testworld --log run/1.20.1/.tmp/prod-run.log`（Zulu 21，-Xms2G -Xmx8G，classpath 94 项 missing 0，argv 62，brand=prodlaunch-1.20.1，gameDir=run/1.20.1）。

### 3.3 用例执行与逐条状态

命令：`pwsh -NoProfile -File scripts/test/mt_case.ps1 run --version 1.20.1 --case scripts/test/cases/RELEASE150-1.20.1.json`
完整输出：run/1.20.1/.tmp/case-run-hoverfix2.out.txt（95 行）。

**27 个执行步（step 5–31）**：

| step | 内容 | 状态 |
|---|---|---|
| 5 | inject_command /gamemode creative @a | PASS |
| 6 | inject_command /clear @a | PASS |
| 7 | inject_command /effect clear @a | PASS |
| 8 | inject_command /tp @a 100 120 100 0 0 | PASS |
| 9 | inject_command /time set 1000 | PASS |
| 10 | inject_command /xp set @a 30 levels | PASS |
| 11 | inject_command /effect give @a minecraft:speed 600 1 true | PASS |
| 12 | inject_command /give @a astral_dice:abyss_reagent_chip 3 | PASS |
| 13 | inject_command /give @a astral_dice:dice 1 | PASS |
| 14 | inject_command /give @a astral_dice:attack_card_medium 1 | PASS |
| 15 | inject_command /give @a astral_dice:jill_sign 1 | PASS |
| 16 | wait 800ms | PASS |
| 17 | eval server（state） | PASS |
| 18 | eval client（chip） | PASS |
| 19 | eval client（**hover，本轮改写的步骤**） | PASS |
| 20 | wait 900ms | PASS |
| 21 | screenshot abyss_reagent_tooltip | PASS |
| 22 | eval client（closed） | PASS |
| 23 | eval server（shop） | PASS |
| 24 | wait 1400ms | PASS |
| 25 | eval client（screen） | PASS |
| 26 | screenshot chip_shop_ui | PASS |
| 27 | eval client（guide） | PASS |
| 28 | wait 1400ms | PASS |
| 29 | screenshot release150_guide | PASS |
| 30 | eval server（sys） | PASS |
| 31 | wait 400ms | PASS |

step 1–4 为 3 条 note + 1 条 jar_stats（jar=astral_dice-1.5.0+forge_1.20.1.jar entries=2032 models=158 recipes=125）。

**25 条机器断言（step 32–56）全部 PASS**（节选原文）：

```text
[ PASS] 32. assert — MT_ASSERT_LOG: PASS — /mod_version=1\.5\.0\+forge_1\.20\.1/ 命中 1 行
[ PASS] 41. assert — MT_ASSERT_LOG: PASS — /\[REL150\] chip id=astral_dice:abyss_reagent_chip count=3 name=(深渊试剂|Abyss Reagent)/ …
[ PASS] 44. assert — /\[REL150\] inventory open=1/ 命中 1 行
[ PASS] 45. assert — /CHIP_SHOP_OPEN player=/ 命中 1 行
[ PASS] 46. assert — /\[REL150\] screen=\S*ChipShopScreen/ 命中 1 行
[ PASS] 47-50. assert — /\[REL150\] sys id=astral_dice:(dice|attack_card_medium|abyss_reagent_chip|jill_sign) ok=1/ 各命中 1 行
[ PASS] 51-53. assert — MT_ASSERT_EVAL: PASS（state / chip / screen 三条 eval 断言）
[ PASS] 54. assert — MT_ASSERT_ABSENT: PASS（Unknown or incomplete command / No player was found / No entity was found …）
```

**2 条视觉断言（step 57–58）按机制交人工**：`VISION_PATH=…/abyss_reagent_tooltip.png` 与 `VISION_PATH=…/chip_shop_ui.png`，判读见 3.6。

最终行：

```text
MT_CASE_RESULT: RELEASE150-1.20.1 = PASS
MT_CHANNEL: delegated_count=2
```

### 3.4 op=shot 原文（三张图，真实非零字节）

```text
MT_CHANNEL: op=shot channel=autojs status=ok detail=file=abyss_reagent_tooltip.png mode=f2 bytes=933267 tier=T2 sig=grab(File,String,Target,Consumer) png_ok=1 rt=com.mojang.blaze3d.pipeline.MainTarget registered src=direct attempts=T1 not_called: InternalError: Can't find method net.minecraft.client.Screenshot.m_92295_(java.io.File,string,com.mojang.blaze3d.pipeline.MainTarget,…
MT_CHANNEL: op=shot channel=autojs status=ok detail=file=chip_shop_ui.png mode=f2 bytes=3789562 tier=T2 sig=grab(File,String,Target,Consumer) png_ok=1 rt=com.mojang.blaze3d.pipeline.MainTarget registered src=direct attempts=T1 not_called: InternalError: Can't find method net.minecraft.client.Screenshot.m_92295_(java.io.File,…
MT_CHANNEL: op=shot channel=autojs status=ok detail=file=release150_guide.png mode=f2 bytes=1161678 tier=T2 sig=grab(File,String,Target,Consumer) png_ok=1 rt=com.mojang.blaze3d.pipeline.MainTarget registered src=direct attempts=T1 not_called: InternalError: Can't find method net.minecraft.client.Screenshot.m_92295_(java.io.File,…
```

- tier / sig：**T2 / grab(File,String,Target,Consumer)**（六档 T6→T5→T2→T4→T1→T3 按序试，1.20.1 命中 T2；T1 的 `m_92295_` 不存在属正常）
- bytes：933267 / 3789562 / 1161678 —— 全部真实非零，且 `png_ok=1`（新轮询的 IEND 校验通过）
- `registered` 出现 ⇒ B1 修复在真实链路生效（截图进 .mt_shots.json 并带 tag）

### 3.5 新图（路径 / 字节 / mtime / sha256）

| 图 | 路径 | 字节 | mtime | sha256 |
|---|---|---|---|---|
| tooltip | run/1.20.1/screenshots/abyss_reagent_tooltip.png | 933267 | 2026-11-02 05:05:26.972672 +0800 | 9cfc2d2b685fa4c7076d651476ff41a9fe3996747ad2fa32a0c54ba165c9e658 |
| 商店 | run/1.20.1/screenshots/chip_shop_ui.png | 3789562 | 2026-11-02 05:05:28.874431 +0800 | 26b95a5db3ff45288fed4c34259fda75fe2de1fd48482b16da588c7b68bab448 |
| 手册 | run/1.20.1/screenshots/release150_guide.png | 1161678 | 2026-11-02 05:05:30.504833 +0800 | 8511c9f9cfee03fb69d84deeebcb1fd8d2eaa68a44df7add164ca345b217e43d |

三张图都是 1920×1080。`run/1.20.1/screenshots/.mt_shots.json` 已登记三条（file/tag/mode/path/ts 齐全，run_id=20260926-230116）——这是 B1 修复后的正常形态（旧清单已轮转，新清单走「清单缺失 → 空集群 → Add-MtShot 自建」路径）。

### 3.6 我对 tooltip 内容的判断

**看到深渊试剂。** 新图中央提示框自上而下是：

1. 标题行：**深渊试剂**（紫框，Astral Dice 稀有度色）
2. **槽位： 筹码**
3. **装备时治愈层数 +2。触发治愈回血时：获得实际治疗量 × 2 的伤害吸收**
4. **当前治愈： 0 / 32**
5. 页脚 **Astral Dice**

对照 lead 要求的三行（深渊试剂 / 槽位： 筹码 / 装备时治愈层数 +2）**逐行一致**；这 5 行也与日志 `[REL150] tip i=0..6` 的 7 行文本吻合（i=2、i=4 为空行，i=6 为 `Astral Dice`）。

画面背景仍是创造模式物品栏（step 4 把玩家设为创造，`setScreen(InventoryScreen)` 被创造栏接管，`mode=opened_inventory+retry`），但**鼠标悬停的确实是玩家背包里的深渊试剂槽**（probe 行 slot#45，container=玩家 Inventory），tooltip 画在光标旁 —— 与旧图「悬停在云杉木活板门上、tooltip 是活板门」是两回事。

另两张：chip_shop_ui.png 为筹码商店界面（`ChipShopScreen`，与 step 25 返回值 `com.merlinkitsune.astral_dice.screen.ChipShopScreen` 一致）；release150_guide.png 为帕秋莉手册条目页。

### 3.7 收尾与复原

- 收停：`pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.20.1 --phase stop --force`（未加 --purge-saves）

```text
MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
MT_CLEANUP_SAVES: KEPT（未指定 --purge-saves）
MT_CLEANUP: 已收停 1 个本流程进程（范围：1.20.1）
MT_CLEANUP_RESULT: OK
MT_STOP: OK (1s) — 收停完成，无本流程残留进程
```

- 收停后复核：run 路径 java 进程 **0**；25585 `Test-NetConnection` = **False**。
- mods 复原：mods 目录 → **dev 7 项**（合 66665283 B）；prod 36 jar → **.tmp/mods-prod-used-rr3-20261102-050611**（137705444 B）。
- 零删除：dev 备份目录 .tmp/mods-dev-backup-20261102-044537 仍保留 7 份副本；存档备份 .tmp/saves-testworld-backup-20261102-044537/testworld 保留；旧图/旧日志全部是改名轮转。

---

## 4 事故与教训（备查）

1. **共享端口 25585 的抢绑**：四条线共用 scripts/test/mt.conf 端点，两个实例同时起时后绑的客户端只在自己的 latest.log 里留一行 `Could not bind the MCP endpoint`，而 harness 会**静默地和别人的游戏对话**（本轮实测：modver 返回 26.1.2、`ChipShopScreen` 没有 `getClass`、`Screenshot.grab` 签名不匹配）。复跑前必须先确认绑定归属：新轮转后的 latest.log 无 bind 报错 + 端点返回本线 modver。
2. **`Get-NetTCPConnection -State Listen` 在本机漏报**：不能用来判端口占用，改用 TCP 连接测试或进程归属。
3. **`Test-MtAutoJsEndpoint` 返回 PSCustomObject（不是 hashtable）**：取字段要遍历 `PSObject.Properties`，`.ContainsKey` 会抛错。
4. **游戏运行中读 logs/latest.log**：`[System.IO.File]::ReadAllText` 会因文件被占用失败，要用 `FileStream(FileShare.ReadWrite)`。
5. **PowerShell 内联 `(if (...) {...})`** 不是合法表达式。
6. **mt_case.ps1 即使判定 FAIL，退出码仍是 0**：判据看 `MT_CASE_RESULT` 行。

---

## 5 附录

### A 最终 hover JS（v3）全文

见 1.4 节（与用例 step 19 内嵌代码逐字符相等，8741 字符）。

### B 关键命令

```text
# 静态守门
pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case scripts/test/cases/RELEASE150-1.20.1.json

# 离线自测（B1 / B2）
pwsh -NoProfile -File .tmp/selftest_b1.ps1
pwsh -NoProfile -File .tmp/selftest_b2.ps1

# 门控 + 轮转 + 启动 + 端点就绪
pwsh -NoProfile -File run/1.20.1/.tmp/launch_verify_1201.ps1

# 复跑
pwsh -NoProfile -File scripts/test/mt_case.ps1 run --version 1.20.1 --case scripts/test/cases/RELEASE150-1.20.1.json

# 收停
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.20.1 --phase stop --force
```

### C 纪律自查

- 未改模组源码 / lang / 资源 / build.gradle。本轮只改：scripts/test/cases/RELEASE150-1.20.1.json 的 hover step、scripts/test/lib/Mt.Paths.psm1、scripts/test/lib/Mt.Channel.psm1，以及 .tmp 下的脚本与备份。
- 未动 run/1.21.1、run/26.1.2、fabric-1.20.1。
- 未 kill 任何他人进程（26.1.2 与 fabric 的 KnotServer/gradlew、GradleDaemon 全程未触碰）。
- 未执行任何 git add / commit / push / tag。
- 断言一条未删、一条未放宽。
