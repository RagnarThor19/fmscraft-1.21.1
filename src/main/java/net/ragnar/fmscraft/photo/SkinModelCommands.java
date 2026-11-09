package net.ragnar.fmscraft.photo;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.ragnar.fmscraft.entity.ModEntities;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Temporary, for staging the title screen panoramas:
 *   /gummss_skin pose3 NoGravity   GUMMSS's skin in pose 3, floating where you stand, facing where you face
 *   /ragnar_skin pose2 Gravity     (Gravity is the default) drops to the ground
 *   /raggimel2 pose26              the boss in his second phase: giant, eyes burning, his aura round him
 *                                  (also /gummss2, /lalli2, /leonard2, /mugen2, /ornamix2, /ricky2)
 *   /skin_model_poses              lists the poses
 *   /skin_model_clear              removes every skin model within 128 blocks (or punch one)
 * Whatever you hold in your hands, the model holds too.
 */
public final class SkinModelCommands {
    private static final SimpleCommandExceptionType BAD_POSE = new SimpleCommandExceptionType(
            Text.literal("Unknown pose: use pose1 to pose" + SkinModelPose.POSES.length));

    private SkinModelCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            for (SkinModelSkin skin : SkinModelSkin.values()) {
                dispatcher.register(literal(skin.command()).requires(source -> source.hasPermissionLevel(2))
                        .then(argument("pose", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                        IntStream.rangeClosed(1, SkinModelPose.POSES.length).mapToObj(i -> "pose" + i), builder))
                                .executes(ctx -> summon(ctx, skin, false))
                                .then(argument("gravity", StringArgumentType.word())
                                        .suggests((ctx, builder) -> CommandSource.suggestMatching(List.of("Gravity", "NoGravity"), builder))
                                        .executes(ctx -> summon(ctx, skin,
                                                StringArgumentType.getString(ctx, "gravity").equalsIgnoreCase("nogravity"))))));
            }
            dispatcher.register(literal("skin_model_poses").requires(source -> source.hasPermissionLevel(2)).executes(ctx -> {
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < SkinModelPose.POSES.length; i++) lines.add("pose" + (i + 1) + ": " + SkinModelPose.POSES[i].name());
                ctx.getSource().sendFeedback(() -> Text.literal(String.join("\n", lines)), false);
                return 1;
            }));
            dispatcher.register(literal("skin_model_clear").requires(source -> source.hasPermissionLevel(2)).executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                var models = player.getServerWorld().getEntitiesByType(ModEntities.SKIN_MODEL,
                        player.getBoundingBox().expand(128), e -> true);
                models.forEach(e -> e.discard());
                ctx.getSource().sendFeedback(() -> Text.literal("Removed " + models.size() + " skin models"), false);
                return models.size();
            }));
        });
    }

    private static int summon(CommandContext<ServerCommandSource> ctx, SkinModelSkin skin, boolean noGravity) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String poseArg = StringArgumentType.getString(ctx, "pose").toLowerCase(Locale.ROOT);
        int pose;
        try {
            pose = Integer.parseInt(poseArg.startsWith("pose") ? poseArg.substring(4) : poseArg) - 1;
        } catch (NumberFormatException e) {
            throw BAD_POSE.create();
        }
        if (pose < 0 || pose >= SkinModelPose.POSES.length) throw BAD_POSE.create();

        SkinModelEntity model = ModEntities.SKIN_MODEL.create(player.getServerWorld());
        if (model == null) return 0;
        model.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0);
        model.setHeadYaw(player.getYaw());
        model.setBodyYaw(player.getYaw());
        model.setSkin(skin);
        model.setPoseIndex(pose);
        model.setNoGravity(noGravity);
        var scale = model.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (scale != null) scale.setBaseValue(skin.scale());
        model.equipStack(EquipmentSlot.MAINHAND, player.getMainHandStack().copy());
        model.equipStack(EquipmentSlot.OFFHAND, player.getOffHandStack().copy());
        player.getServerWorld().spawnEntity(model);
        String poseName = SkinModelPose.POSES[pose].name();
        ctx.getSource().sendFeedback(() -> Text.literal("Placed " + skin.command() + " (" + poseName + ")"), false);
        return 1;
    }
}
