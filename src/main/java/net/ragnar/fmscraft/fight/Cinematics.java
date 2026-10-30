package net.ragnar.fmscraft.fight;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.network.BossScreenPayload;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Boss fight cutscenes: freezing players in place and covering their screens.
 * <p>
 * A frozen player can't walk, jump, hit, use anything or be hurt (their client also stops the mouse turning the
 * camera while the screen is covered). The freeze is two temporary attribute modifiers, which are never saved, so a
 * crash mid-cutscene can't leave anyone stuck.
 */
public final class Cinematics {
    private static final Identifier FREEZE = Identifier.of(FmsCraft.MOD_ID, "cinematic_freeze");
    private static final Set<UUID> FROZEN = new HashSet<>();

    private Cinematics() {
    }

    public static void register() {
        // The void still kills: a cutscene can't save someone who already fell off
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof PlayerEntity player && isFrozen(player)) || source.isOf(DamageTypes.OUT_OF_WORLD));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) ->
                isFrozen(player) ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FROZEN.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FROZEN.clear());
    }

    public static boolean isFrozen(PlayerEntity player) {
        return !player.getWorld().isClient && FROZEN.contains(player.getUuid());
    }

    public static void freeze(ServerPlayerEntity player) {
        if (!FROZEN.add(player.getUuid())) return;
        addModifier(player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED));
        addModifier(player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH));
        player.setSprinting(false);
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
    }

    public static void unfreeze(ServerPlayerEntity player) {
        FROZEN.remove(player.getUuid());
        removeModifier(player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED));
        removeModifier(player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH));
    }

    /** Unfreezes the player and takes anything off their screen at once. */
    public static void release(ServerPlayerEntity player) {
        unfreeze(player);
        screen(player, BossScreenPayload.CLEAR, 0, 0);
    }

    public static void screen(ServerPlayerEntity player, String screen, int elapsed, int duration) {
        if (ServerPlayNetworking.canSend(player, BossScreenPayload.ID)) {
            ServerPlayNetworking.send(player, new BossScreenPayload(screen, elapsed, duration));
        }
    }

    /** A white flash, {@code strength} 0-1, fading over {@code ticks}. */
    public static void flash(ServerPlayerEntity player, float strength, int ticks) {
        screen(player, BossScreenPayload.FLASH, Math.round(Math.min(1f, strength) * 100), ticks);
    }

    private static void addModifier(EntityAttributeInstance attribute) {
        if (attribute != null && !attribute.hasModifier(FREEZE)) {
            attribute.addTemporaryModifier(new EntityAttributeModifier(FREEZE, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeModifier(EntityAttributeInstance attribute) {
        if (attribute != null) attribute.removeModifier(FREEZE);
    }
}
