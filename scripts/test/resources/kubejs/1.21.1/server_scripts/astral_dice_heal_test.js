// Astral Dice 自动化测试:为玩家施加 32 层治愈(用于验证效果等级角标 9→99)
// 用法: /astralheal32
// 机制: 治愈点设为 32(上限) + 治愈计时器 60 秒,再由 HealingManager 刷新"治愈"效果。
//       效果等级 = 层数 = amplifier+1,32 层 → 等级 32,物品栏效果面板角标应显示 XXXII。
ServerEvents.commandRegistry(event => {
    const { commands: Commands } = event;
    const ModAttachments = Java.loadClass("com.merlinkitsune.astral_dice.component.ModAttachments");
    const HealingManager = Java.loadClass("com.merlinkitsune.astral_dice.item.HealingManager");
    const ComponentClass = Java.loadClass("net.minecraft.network.chat.Component");

    event.register(
        Commands.literal("astralheal32")
            .requires(src => src.hasPermission(2))
            .executes(ctx => {
                var player = ctx.source.getPlayerOrException();
                var now = player.level().getGameTime();
                ModAttachments.setHealingPoints(player, 32);
                ModAttachments.setHealingTimerEnd(player, now + 1200);
                HealingManager.updateEffect(player);
                ctx.source.sendFailure(ComponentClass.literal("HEAL32_APPLIED points=" + HealingManager.getPoints(player)));
                return 1;
            })
    );
});
