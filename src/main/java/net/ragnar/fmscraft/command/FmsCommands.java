package net.ragnar.fmscraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.util.Arrays;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Operator commands for testing until the sin structures exist:
 *   /fmscraft fight <sin>     open that sin's portal in front of you
 *   /fmscraft end <sin>       end a running fight, everyone sent back
 *   /fmscraft status          which sins are defeated
 *   /fmscraft undefeat <sin>  clear a sin's defeated flag
 */
public final class FmsCommands {
    private FmsCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("fmscraft").requires(source -> source.hasPermissionLevel(2))
                        .then(literal("fight").then(sinArgument().executes(FmsCommands::fight)))
                        .then(literal("end").then(sinArgument().executes(ctx -> {
                            BossFights.forceEnd(ctx.getSource().getServer(), sin(ctx));
                            return 1;
                        })))
                        .then(literal("undefeat").then(sinArgument().executes(ctx -> {
                            FmsWorldState.get(ctx.getSource().getServer()).clearDefeated(sin(ctx));
                            return 1;
                        })))
                        .then(literal("status").executes(FmsCommands::status))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, String> sinArgument() {
        return argument("sin", StringArgumentType.word())
                .suggests((ctx, builder) -> CommandSource.suggestMatching(Arrays.stream(Sin.values()).map(Sin::id), builder));
    }

    private static Sin sin(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Sin sin = Sin.byId(StringArgumentType.getString(ctx, "sin"));
        if (sin == null) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Text.literal("Unknown sin")).create();
        return sin;
    }

    private static int fight(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Sin sin = sin(ctx);
        Direction facing = player.getHorizontalFacing();
        BlockPos base = player.getBlockPos().offset(facing, 3);
        Direction.Axis axis = facing.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
        if (!BossFights.open(sin, player.getServerWorld(), base, axis)) {
            ctx.getSource().sendError(Text.literal("The " + sin.displayName() + " fight is already running"));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Opened the " + sin.displayName() + " portal for 30 seconds"), true);
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> ctx) {
        FmsWorldState state = FmsWorldState.get(ctx.getSource().getServer());
        String text = Arrays.stream(Sin.values())
                .map(sin -> sin.displayName() + ": " + (state.isDefeated(sin) ? "defeated" : "undefeated"))
                .collect(Collectors.joining(", "));
        ctx.getSource().sendFeedback(() -> Text.literal(text), false);
        return 1;
    }
}
