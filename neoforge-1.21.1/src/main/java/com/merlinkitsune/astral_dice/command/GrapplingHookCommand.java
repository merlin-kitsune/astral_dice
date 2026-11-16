package com.merlinkitsune.astral_dice.command;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.GrapplingHookEntity;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code /astralhook <target>}：抓钩原型的手动触发入口（仅 OP，权限级 2）。
 *
 * <p>这是临时演示命令，不是最终玩法入口；最终应接到立牌主动技能。它只接受 {@link Mob}，
 * 避免把玩家当成拖拽目标，也不做伤害结算。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class GrapplingHookCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrapplingHookCommand.class);
    private static final int REQUIRED_PERMISSION_LEVEL = 2;
    private static final String ROOT_LITERAL = "astralhook";
    private static final String ARG_TARGET = "target";

    private static final String KEY_START = "command.astral_dice.astralhook.start";
    private static final String KEY_NOT_MOB = "command.astral_dice.astralhook.not_mob";
    private static final String KEY_TOO_FAR = "command.astral_dice.astralhook.too_far";
    private static final String KEY_OTHER_DIMENSION = "command.astral_dice.astralhook.other_dimension";

    private GrapplingHookCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(ROOT_LITERAL)
                .requires(source -> source.hasPermission(REQUIRED_PERMISSION_LEVEL))
                .then(Commands.argument(ARG_TARGET, EntityArgument.entity())
                        .executes(GrapplingHookCommand::fire)));
    }

    private static int fire(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Entity entity = EntityArgument.getEntity(ctx, ARG_TARGET);

        if (!(entity instanceof Mob target)) {
            source.sendFailure(Component.translatable(KEY_NOT_MOB));
            return 0;
        }
        if (target.level() != player.level()) {
            source.sendFailure(Component.translatable(KEY_OTHER_DIMENSION));
            return 0;
        }
        if (!target.isAlive() || target.distanceTo(player) > GrapplingHookEntity.RANGE) {
            source.sendFailure(Component.translatable(KEY_TOO_FAR, GrapplingHookEntity.RANGE));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        GrapplingHookEntity hook = new GrapplingHookEntity(level, player, target);
        level.addFreshEntity(hook);
        LOGGER.info("AP_GRAPPLE_START owner={} target={} dist={}",
                player.getName().getString(), target.getName().getString(),
                String.format(java.util.Locale.ROOT, "%.2f", target.distanceTo(player)));
        source.sendSuccess(() -> Component.translatable(KEY_START, target.getName()), true);
        return Command.SINGLE_SUCCESS;
    }
}
