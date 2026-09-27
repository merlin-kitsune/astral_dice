// ════════════════════════════════════════════════════════════════════════════
//  astral_rarity_probe.js —— 「客户端侧稀有度是不是真值」读数探针（2026-09-25 新增）
//
//  【要回答的问题】
//    原版 `Rarity` 的 `CODEC`（StringRepresentable.fromValues ⇒ **立即**建名字表）与
//    `BY_ID`（ByIdMap.continuous(values()) ⇒ **立即**建 id 表）都是**静态字段初始化器**；
//    而 FML 的枚举扩展代码是插在 `<clinit>` 的 `$VALUES = $values()` **之前**
//    （`RuntimeEnumExtender` 第 117-120 行，javac 分支）。两者谁先跑没有现成文档 ⇒ 只能实测：
//      · ① 物品**注册时**设的档位（`Item.Properties#rarity`）—— 客户端拿到的是不是真值？
//        （这决定「提示框物品名染色」在本模组是否真的生效）
//      · ② 经**数据组件** `minecraft:rarity` 覆盖的档位 —— 客户端解出来是什么？
//        （这决定扩展档位能不能被 `/give`/`/item`/NBT 携带）
//
//  【读什么】`ItemStack#getRarity()` → `name()`（枚举常量名）/ `getSerializedName()`（astral_dice:xxx），
//    以及 `getRarity().getStyleModifier().apply(Style.EMPTY).getColor()` 的 ARGB。最后这一项**等价于
//    提示框首行的真实颜色** —— 因为 `ItemStack#getTooltipLines` 给物品名套的就是这条 styleModifier。
//    若该档位是「奇特」（彩虹档），再补 `rainbow=`/`rb=`/`spin=`：`rb` 是库函数算出的边框起始色
//    （与产品客户端钩子 `client/RarityTooltipFrame` 用的是同一个方法），`spin=1` = 色环确实在随时间推进。
//
//  【为什么不用 `net.minecraft.Util#getMillis`】
//    ⚠️ KubeJS 的**类过滤器会拒绝 `net.minecraft.Util`**（实测：`Failed to load Java class
//    'net.minecraft.Util': Class is not allowed by class filter!`），而且它在**脚本加载期**抛异常 ⇒
//    **整个客户端脚本都不注册**（探针一条输出都没有，表现为「读数为空」而不是报错）。故时间源用纯 JS 的
//    `Date.now()`；并且下面所有 `Java.loadClass` 都包了「失败即上报」，绝不再让一个类把整个探针拖死。
//
//  【只读性】不改任何状态；副作用只有一行 console.info（→ logs/kubejs/client.log）。
// ════════════════════════════════════════════════════════════════════════════

function rrLog(s) { try { console.info("AP_CRAR:" + s); } catch (e) { /* 忽略：不得因回报失败影响诊断 */ } }

var rrLoadErrors = [];

function rrTryLoad(name) {
    try {
        return Java.loadClass(name);
    } catch (e) {
        rrLoadErrors.push(name + " -> " + e);
        return null;
    }
}

var RR_Mc = rrTryLoad("net.minecraft.client.Minecraft");
var RR_Style = rrTryLoad("net.minecraft.network.chat.Style");
var RR_BIR = rrTryLoad("net.minecraft.core.registries.BuiltInRegistries");
var RR_Lib = rrTryLoad("com.merlinkitsune.starenginelib.item.Rarity");

if (rrLoadErrors.length > 0) {
    rrLog("evt=loaderr:count=" + rrLoadErrors.length +":" + rrLoadErrors.join(" | "));
}

function rrHex(v) {
    var h = (v >>> 0).toString(16);
    while (h.length < 8) h = "0" + h;
    return h;
}

function rrRead(mc) {
    var st, id;
    try { st = mc.player.getMainHandItem(); } catch (e) { return "item=ERR_getMainHand(" + e + ")"; }
    try {
        id = st.isEmpty() ? "empty" : String(RR_BIR.ITEM.getKey(st.getItem()));
    } catch (e1) { id = "ERR_id(" + e1 + ")"; }
    var nm = "?", sn = "?", rgb = "?";
    var extra = "";
    try {
        var r = st.getRarity();
        nm = String(r.name());
        sn = String(r.getSerializedName());
        var c = r.getStyleModifier().apply(RR_Style.EMPTY).getColor();
        rgb = (c === null) ? "none" : rrHex(c.getValue());
        // 彩虹档（奇特）：顺带证明「色环真的在流动」—— 取当前与 1 秒后的边框起始色，必须不同。
        // ⚠️ 这两个值由**库里的唯一权威函数**算出，与产品客户端钩子 `client/RarityTooltipFrame`
        //    调用的是同一个方法 ⇒ 二者颜色必然一致。
        if (sn === "astral_dice:bizarre") {
            var now = Date.now();
            var c1 = RR_Lib.BIZARRE.rainbowBorderStart(now);
            var c2 = RR_Lib.BIZARRE.rainbowBorderStart(now + 1000);
            extra = ":rainbow=1:rb=" + rrHex(c1) + ":spin=" + ((c1 === c2) ? 0 : 1);
        }
    } catch (e2) { nm = sn = rgb = "ERR(" + e2 + ")"; extra = ":extraERR(" + e2 + ")"; }
    return "item=" + id + ":enum=" + nm + ":sn=" + sn + ":rgb=" + rgb + extra;
}

var rrKey = "__init__";
var rrTick = 0;
var RR_HEARTBEAT = 40;

ClientEvents.tick(event => {
    if (RR_Mc === null) return;
    var mc = null;
    try { mc = RR_Mc.getInstance(); } catch (e0) { return; }
    var ok = false;
    try { ok = (mc !== null) && (mc.player !== null); } catch (e) { ok = false; }
    if (!ok) return;

    rrTick++;
    var body = rrRead(mc);
    if (body !== rrKey) {
        rrKey = body;
        rrLog("evt=chg:ticks=" + rrTick + ":" + body);
        return;
    }
    if (rrTick % RR_HEARTBEAT === 0) rrLog("evt=hb:ticks=" + rrTick + ":" + body);
});
