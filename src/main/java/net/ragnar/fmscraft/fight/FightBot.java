package net.ragnar.fmscraft.fight;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.ragnar.fmscraft.boss.SinBossEntity;
import net.ragnar.fmscraft.sin.Sin;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A stand-in friend for testing boss fights alone ({@code /fmscraft fight <sin> AI|NoAI <count> <canDie>}).
 * <p>
 * It's a real server player, joined with a connection that goes nowhere, so the bosses, their player scaling,
 * deaths, death chests and being sent home all treat it exactly like a friend. With AI on it ambles about very slowly,
 * keeps out of the way of real players, looks at the boss and eats a golden apple when it's low. Without AI it just
 * stands there. A bot that can't die is left on half a heart instead, and is put back on the arena if it falls off.
 */
public class FightBot extends ServerPlayerEntity {
    /** Walking input: 1 is a normal walk (~4.3 blocks/s), so this is well under a block a second. */
    private static final float WALK = 0.15f;
    private static final float EAT_AT = 10f;
    private static final int APPLES = 32;
    /** Real players are given this much room. */
    private static final double PERSONAL_SPACE = 4.0;

    final Sin sin;
    final boolean ai;
    final boolean canDie;
    /** Server tick to step into the fight on (-1 once in), when it died, and when its fight was over (-1 if not). */
    long enterAt;
    long diedAt = -1;
    long fightOverAt = -1;

    private Vec3d target;
    private int repickIn;

    private FightBot(MinecraftServer server, ServerWorld world, GameProfile profile, SyncedClientOptions options,
                     Sin sin, boolean ai, boolean canDie) {
        super(server, world, profile, options);
        this.sin = sin;
        this.ai = ai;
        this.canDie = canDie;
    }

    /** Joins a bot to the server (it shows in the player list like anyone else) and stands it at {@code at}. */
    static FightBot join(ServerWorld world, Vec3d at, float yaw, String name, Sin sin, boolean ai, boolean canDie) {
        MinecraftServer server = world.getServer();
        // The same UUID an offline server would give that name, so each bot keeps one player file
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(id, name);
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        FightBot bot = new FightBot(server, world, profile, data.syncedOptions(), sin, ai, canDie);
        ClientConnection connection = new NowhereConnection();
        new EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, bot, data);

        bot.changeGameMode(GameMode.SURVIVAL);
        bot.teleport(world, at.x, at.y, at.z, yaw, 0f);
        bot.onTeleportationDone();
        bot.freshStart();
        return bot;
    }

    /** Whatever the player file remembered from last time is wiped: full health, fed, and the kit. */
    private void freshStart() {
        getInventory().clear();
        clearStatusEffects();
        extinguish();
        deathTime = 0;
        fallDistance = 0;
        setAbsorptionAmount(0);
        setHealth(getMaxHealth());
        getHungerManager().setFoodLevel(20);
        getHungerManager().setSaturationLevel(5f);

        Registry<Enchantment> enchantments = getServerWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        armor(enchantments, EquipmentSlot.HEAD, Items.NETHERITE_HELMET);
        armor(enchantments, EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE);
        armor(enchantments, EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS);
        armor(enchantments, EquipmentSlot.FEET, Items.NETHERITE_BOOTS);
        getInventory().setStack(0, new ItemStack(Items.NETHERITE_SWORD));
        getInventory().setStack(1, new ItemStack(Items.GOLDEN_APPLE, APPLES));
        getInventory().selectedSlot = 0;
    }

    private void armor(Registry<Enchantment> enchantments, EquipmentSlot slot, Item item) {
        ItemStack stack = new ItemStack(item);
        enchantments.getEntry(Enchantments.PROTECTION).ifPresent(entry -> stack.addEnchantment(entry, 4));
        equipStack(slot, stack);
    }

    @Override
    public void setHealth(float health) {
        // Called from the constructor too, before canDie is set; that only ever sets full health
        super.setHealth(!canDie && health < 1f ? 1f : health);
    }

    @Override
    public void tick() {
        // A real client confirms each teleport; until it does, players can't be hurt
        if (isInTeleportationState()) onTeleportationDone();
        super.tick();
        if (isAlive()) think();
        // A real player's connection runs this; it's what moves them, heals them and finishes eating
        playerTick();
    }

    // ---- What it does ----

    private void think() {
        forwardSpeed = 0f;
        sidewaysSpeed = 0f;
        setJumping(false);
        ServerWorld world = getServerWorld();
        boolean inArena = Arenas.sinOf(world) == sin;

        if (!canDie && inArena && getY() < world.getBottomY() + 4) {
            Vec3d spawn = Arenas.layout(sin).playerSpawn();
            teleport(world, spawn.x, spawn.y, spawn.z, getYaw(), 0f);
            setVelocity(Vec3d.ZERO);
            fallDistance = 0;
            return;
        }
        if (!ai || Cinematics.isFrozen(this)) return;

        if (isUsingItem()) return; // stands still while eating
        if (getInventory().selectedSlot != 0) getInventory().selectedSlot = 0; // back to the sword after a bite
        if (getHealth() <= EAT_AT && eat()) return;
        if (!inArena) return;

        SinBossEntity boss = nearestBoss(world);
        PlayerEntity crowding = nearestRealPlayer(world, PERSONAL_SPACE);
        if (crowding != null) {
            // Step back out of a real player's way
            Vec3d away = getPos().subtract(crowding.getPos()).multiply(1, 0, 1);
            if (away.lengthSquared() < 1.0e-4) away = new Vec3d(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            target = getPos().add(away.normalize().multiply(3));
            repickIn = 40;
        } else if (target == null || --repickIn <= 0 || horizontalDistanceTo(target) < 0.5) {
            pickTarget(world);
        }

        if (target != null && horizontalDistanceTo(target) >= 0.5) {
            float walkYaw = yawTowards(target);
            Vec3d ahead = getPos().add(Vec3d.fromPolar(0f, walkYaw).multiply(0.8));
            if (safeToStand(world, BlockPos.ofFloored(ahead))) {
                setYaw(walkYaw);
                setBodyYaw(walkYaw);
                forwardSpeed = WALK;
                if (horizontalCollision && isOnGround()) setJumping(true);
            } else {
                target = null; // an edge or something hot: think again next tick
            }
        }

        if (boss != null) {
            Vec3d eyes = boss.getEyePos();
            setHeadYaw(yawTowards(eyes));
            double flat = Math.sqrt(squaredHorizontal(eyes));
            setPitch((float) -(MathHelper.atan2(eyes.y - getEyeY(), flat) * MathHelper.DEGREES_PER_RADIAN));
            if (forwardSpeed == 0f) {
                setYaw(getHeadYaw());
                setBodyYaw(getHeadYaw());
            }
        } else {
            setHeadYaw(getYaw());
        }
    }

    /** Bites a golden apple from the hotbar the way a player would (so a cutscene's freeze still stops it). */
    private boolean eat() {
        for (int slot = 0; slot < 9; slot++) {
            if (!getInventory().getStack(slot).isOf(Items.GOLDEN_APPLE)) continue;
            getInventory().selectedSlot = slot;
            return interactionManager.interactItem(this, getServerWorld(), getMainHandStack(), Hand.MAIN_HAND).isAccepted();
        }
        return false;
    }

    /** A spot a few blocks away with ground under it, not too close to a real player. */
    private void pickTarget(ServerWorld world) {
        target = null;
        repickIn = 80 + random.nextInt(80);
        for (int tries = 0; tries < 12; tries++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 3 + random.nextDouble() * 5;
            BlockPos column = BlockPos.ofFloored(getX() + Math.cos(angle) * distance, getY(), getZ() + Math.sin(angle) * distance);
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos feet = column.up(dy);
                if (!safeToStand(world, feet)) continue;
                Vec3d spot = Vec3d.ofBottomCenter(feet);
                if (nearestRealPlayer(world, spot, PERSONAL_SPACE + 1) == null) {
                    target = spot;
                    return;
                }
            }
        }
    }

    /** Room to stand at {@code feet}, and solid ground no more than 3 blocks below it. Never fire, lava or the void. */
    private static boolean safeToStand(ServerWorld world, BlockPos feet) {
        if (hot(world, feet) || hot(world, feet.up())) return false;
        for (int down = 1; down <= 3; down++) {
            BlockPos pos = feet.down(down);
            if (hot(world, pos)) return false;
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) return true;
        }
        return false;
    }

    private static boolean hot(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        FluidState fluid = world.getFluidState(pos);
        return state.isIn(BlockTags.FIRE) || state.isIn(BlockTags.CAMPFIRES) || fluid.isIn(FluidTags.LAVA)
                || state.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK) || state.isOf(net.minecraft.block.Blocks.CACTUS)
                || state.isOf(net.minecraft.block.Blocks.SWEET_BERRY_BUSH) || state.isOf(net.minecraft.block.Blocks.POWDER_SNOW);
    }

    @Nullable
    private SinBossEntity nearestBoss(ServerWorld world) {
        SinBossEntity nearest = null;
        double best = 128 * 128;
        for (SinBossEntity boss : world.getEntitiesByClass(SinBossEntity.class, getBoundingBox().expand(128), SinBossEntity::isAlive)) {
            double d = squaredDistanceTo(boss);
            if (d < best) {
                best = d;
                nearest = boss;
            }
        }
        return nearest;
    }

    @Nullable
    private PlayerEntity nearestRealPlayer(ServerWorld world, double within) {
        return nearestRealPlayer(world, getPos(), within);
    }

    @Nullable
    private static PlayerEntity nearestRealPlayer(ServerWorld world, Vec3d at, double within) {
        PlayerEntity nearest = null;
        double best = within * within;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player instanceof FightBot || player.isSpectator() || !player.isAlive()) continue;
            double d = player.squaredDistanceTo(at);
            if (d < best) {
                best = d;
                nearest = player;
            }
        }
        return nearest;
    }

    private float yawTowards(Vec3d to) {
        return (float) (MathHelper.atan2(to.z - getZ(), to.x - getX()) * MathHelper.DEGREES_PER_RADIAN) - 90f;
    }

    private double squaredHorizontal(Vec3d to) {
        double dx = to.x - getX(), dz = to.z - getZ();
        return dx * dx + dz * dz;
    }

    private double horizontalDistanceTo(Vec3d to) {
        return Math.sqrt(squaredHorizontal(to));
    }

    /**
     * A connection with no one on the other end: everything sent to it is dropped (an embedded channel would keep it
     * all in memory for the length of the fight).
     */
    private static final class NowhereConnection extends ClientConnection {
        NowhereConnection() {
            super(NetworkSide.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketCallbacks callbacks, boolean flush) {
        }

        @Override
        public void flush() {
        }
    }
}
