// 临时调试脚本:监听 CurioChangeEvent,输出槽位变化详情
// 用法: 修改后执行 /kubejs reload server-scripts + /reload
// 注意: Rhino 中 const 提升到脚本全局作用域,多次回调会 redeclaration 报错,必须全部用 var
// CurioChangeEvent API: getEntity()/getIdentifier()/getSlotIndex()/getFrom()/getTo()
NativeEvents.onEvent('top.theillusivec4.curios.api.event.CurioChangeEvent', event => {
    try {
        var entity = event.getEntity();
        var slotId = event.getIdentifier();
        var idx = event.getSlotIndex();
        var from = event.getFrom();
        var to = event.getTo();
        var fromName = from.isEmpty() ? "empty" : from.getItem().toString();
        var toName = to.isEmpty() ? "empty" : to.getItem().toString();
        if (entity != null) {
            console.log("CURIO_CHANGE: entity=" + entity.getName().getString()
                + " slot=" + slotId + " idx=" + idx
                + " from=" + fromName + " to=" + toName);
        }
    } catch (e) {
        console.log("CURIO_WATCH_ERROR: " + e);
    }
});
