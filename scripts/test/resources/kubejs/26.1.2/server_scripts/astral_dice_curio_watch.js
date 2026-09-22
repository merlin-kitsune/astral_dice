// 调试脚本(26.1.2 版):监听 CurioChangeEvent 的**两个具体子类**,输出槽位变化详情
//
//  ⚠️ 26.1.2(Curios 15)迁移要点:1.21.1 线(Curios 9.x)的 `CurioChangeEvent` 是**具体类**,
//  可直接订阅;Curios 12.0.0 起它变成 `public abstract class`,直接订阅会抛
//      IllegalArgumentException: Cannot register listeners for abstract class
//        top.theillusivec4.curios.api.event.CurioChangeEvent.
//        Register a listener to one of its subclasses instead!
//  26.1.2 实际派发的是两个子类,**必须各订阅一次**才能覆盖 1.21.1 原语义:
//    · CurioChangeEvent\$Item  —— 物品本身变了(isSameItem 为 false)
//    · CurioChangeEvent\$State —— 数量/组件变了(matches 为 false 但 isSameItem 为 true)
//  依据:curios-dRnRThvD-sources.jar 的 top/theillusivec4/curios/api/event/CurioChangeEvent.java
//
// 用法: 修改后执行 /kubejs reload server-scripts + /reload
// 注意: Rhino 中 const 提升到脚本全局作用域,多次回调会 redeclaration 报错,必须全部用 var
// CurioChangeEvent API: getEntity()/getSlotContext()/getIdentifier()/getSlotIndex()/getFrom()/getTo()
var CURIO_WATCH_ITEM = 'top.theillusivec4.curios.api.event.CurioChangeEvent$Item';
var CURIO_WATCH_STATE = 'top.theillusivec4.curios.api.event.CurioChangeEvent$State';

function curioWatchEmit(kind, event) {
    try {
        var entity = event.getEntity();
        var slotId = event.getIdentifier();
        var idx = event.getSlotIndex();
        var from = event.getFrom();
        var to = event.getTo();
        var fromName = from.isEmpty() ? "empty" : from.getItem().toString();
        var toName = to.isEmpty() ? "empty" : to.getItem().toString();
        if (entity != null) {
            console.log("CURIO_CHANGE: kind=" + kind
                + " entity=" + entity.getName().getString()
                + " slot=" + slotId + " idx=" + idx
                + " from=" + fromName + " to=" + toName);
        }
    } catch (e) {
        console.log("CURIO_WATCH_ERROR: " + e);
    }
}

NativeEvents.onEvent(CURIO_WATCH_ITEM, event => curioWatchEmit('item', event));
NativeEvents.onEvent(CURIO_WATCH_STATE, event => curioWatchEmit('state', event));
