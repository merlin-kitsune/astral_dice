package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.starenginelib.target.TargetSelectionAction;
import com.merlinkitsune.astral_dice.target.TargetSelectionManager;
import com.merlinkitsune.starenginelib.target.TargetSelectionRegistry;
import com.merlinkitsune.starenginelib.target.TargetType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * 目标选择器端到端验证命令（测试钩子，非正式功能）：
 * {@code /astral_dice targetselect <player|enemy|living>}
 * 需要 2 级权限（OP）。以演示动作 test_echo_* 触发选择模式，确认后仅回显目标名
 * （ActionBar 由 {@link TargetSelectionManager} 统一发送），不施加任何玩法效果。
 * 后续接入真实立牌/效果牌后可移除本命令。
 *
 * <h2>26.1.2 平台改写点</h2>
 * <ul>
 *   <li><b>权限 API 换代（必须改写）</b>：1.21.1 的 {@code CommandSourceStack#hasPermission(int)}
 *       在 26.1.2 已不存在（数字权限等级被 {@code PermissionSet}/{@code Permission} 取代），
 *       等价写法 = {@code source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)}
 *       （{@code COMMANDS_GAMEMASTER} 即原「2 级权限」；同版本既有 {@code command/AstralPartyCommand}
 *       第 99-100/184-186 行已实证）。</li>
 *   <li>{@code @EventBusSubscriber(modid = …)} 无 {@code bus=}（1.21.1 侧本来也没有）。</li>
 *   <li>其余（{@code RegisterCommandsEvent#getDispatcher}、{@code Commands.literal/argument}、
 *       {@code StringArgumentType}、{@code ctx.getSource().getEntity()}、{@code TargetSelectionRegistry}
 *       三个 {@code test_echo_*} 演示动作注册）在 26.1.2 逐字可用。</li>
 * </ul>
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class TargetSelectionTestCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetSelectionTestCommand.class);

    /** 26.1.2：原 1.21.1 的「2 级权限」= {@code Permissions.COMMANDS_GAMEMASTER} */
    private static final net.minecraft.server.permissions.Permission REQUIRED_PERMISSION =
            net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER;

    private TargetSelectionTestCommand() {
    }

    static {
        TargetSelectionRegistry.register(demoAction("test_echo_player", TargetType.PLAYER));
        TargetSelectionRegistry.register(demoAction("test_echo_enemy", TargetType.ENEMY));
        TargetSelectionRegistry.register(demoAction("test_echo_living", TargetType.LIVING));
    }

    private static TargetSelectionAction demoAction(String id, TargetType type) {
        return new TargetSelectionAction() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public TargetType targetType() {
                return type;
            }

            @Override
            public void apply(ServerPlayer player, LivingEntity target) {
                LOGGER.debug("[Astral Dice][TargetSelection] test_echo applied to {}({}) by {}",
                        target.getId(), target.getName().getString(), player.getName().getString());
            }
        };
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("astral_dice")
                .then(Commands.literal("targetselect")
                        .requires(TargetSelectionTestCommand::hasPermission)
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("player");
                                    builder.suggest("enemy");
                                    builder.suggest("living");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "type"))))));
    }

    /** 权限判据（26.1.2 形态，见类注释） */
    private static boolean hasPermission(CommandSourceStack source) {
        return source.permissions().hasPermission(REQUIRED_PERMISSION);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String type) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        String actionId = switch (type.toLowerCase(Locale.ROOT)) {
            case "player" -> "test_echo_player";
            case "enemy" -> "test_echo_enemy";
            default -> "test_echo_living";
        };
        LOGGER.debug("[Astral Dice][TargetSelectionTestCommand] test command type={} action={} radius={}",
                type, actionId, GameplayConstants.TARGET_SELECT_RADIUS);
        return TargetSelectionManager.start(player, actionId) ? 1 : 0;
    }
}
