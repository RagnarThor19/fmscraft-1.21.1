package net.ragnar.fmscraft.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.FightBots;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.util.Arrays;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Operator commands for testing until the sin structures exist:
 *   /fmscraft fight <sin>     open that sin's portal in front of you
 *   /fmscraft fight <sin> AI|NoAI <count> <canDie>
 *                             the same, with up to 6 bot players who come in with you (see FightBots). AI bots amble
 *                             about very slowly and eat golden apples; NoAI ones stand still. If canDie is false they
 *                             can't die, so only you can lose the fight.
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
                        .then(literal("fight").then(sinArgument().executes(FmsCommands::fight)
                                .then(argument("mode", StringArgumentType.word())
                                        .suggests((ctx, builder) -> CommandSource.suggestMatching(new String[]{"AI", "NoAI"}, builder))
                                        .then(argument("count", IntegerArgumentType.integer(0, FightBots.MAX))
                                                .then(argument("canDie", BoolArgumentType.bool())
                                                        .executes(FmsCommands::fightWithBots))))))
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

    private static int fightWithBots(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Sin sin = sin(ctx);
        String mode = StringArgumentType.getString(ctx, "mode");
        if (!mode.equalsIgnoreCase("AI") && !mode.equalsIgnoreCase("NoAI")) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Text.literal("Mode must be AI or NoAI")).create();
        }
        boolean ai = mode.equalsIgnoreCase("AI");
        int count = IntegerArgumentType.getInteger(ctx, "count");
        boolean canDie = BoolArgumentType.getBool(ctx, "canDie");

        Sin busy = FightBots.busyWith();
        if (busy != null) {
            ctx.getSource().sendError(Text.literal("Bots are still in the " + busy.displayName() + " fight: /fmscraft end " + busy.id() + " first"));
            return 0;
        }
        if (fight(ctx) == 0) return 0;

        // Beside you, on the side away from the portal; they walk in one after another
        Vec3d near = player.getPos().add(Vec3d.of(player.getHorizontalFacing().getOpposite().getVector()).multiply(2));
        int joined = FightBots.start(player.getServerWorld(), near, player.getYaw(), sin, count, ai, canDie).size();
        ctx.getSource().sendFeedback(() -> Text.literal(joined + (ai ? " AI" : " NoAI") + " bot" + (joined == 1 ? "" : "s")
                + " joining you" + (canDie ? " (they can die)" : " (they can't die)")), true);
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
