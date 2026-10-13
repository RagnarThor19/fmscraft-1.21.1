package net.ragnar.fmscraft.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.structure.envy.EnvyMazeLayout;
import net.ragnar.fmscraft.structure.envy.EnvyMazePiece;
import net.ragnar.fmscraft.structure.envy.EnvyMazes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The statue of Envy that patrols the Envy maze. It stands still until a player comes within
 * {@link EnvyMazes#WAKE_DISTANCE} blocks of its maze. Then it has no animation and never stops: it slides exactly 5 blocks
 * (one maze unit) at a time along the middle of the walkway, and at a junction, turn or dead end it slowly grinds
 * round to face its new direction. Anything it touches takes heavy damage and is thrown aside.
 *
 * A statue from a maze knows that maze's layout and only ever moves into its corridors, so it can never leave the
 * maze or pass through a wall. On top of that, any gap with 3 or more solid blocks (like the 1-wide safe room tunnel,
 * or natural stone) stops it; fewer than that (a block or two players put in its way) gets smashed.
 */
public class EnvyStatueEntity extends Entity {
    public static final int UNIT = 5;
    public static final int SLIDE_TICKS = 12;
    public static final int TICKS_PER_QUARTER_TURN = 30;
    public static final float DAMAGE = 20f;
    /** How many of the 12 blocks between two units must be solid before the statue treats it as a wall. */
    public static final int WALL_THRESHOLD = 3;
    private static final RegistryKey<Structure> MAZE = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of(FmsCraft.MOD_ID, "envy_maze"));
    public static final RegistryKey<DamageType> CRUSHED =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(FmsCraft.MOD_ID, "envy_statue"));

    public static final int STATE_SLIDING = 1;
    public static final int STATE_TURNING = 2;
    private static final TrackedData<Integer> STATE = DataTracker.registerData(EnvyStatueEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> AWAKE = DataTracker.registerData(EnvyStatueEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** Set by the client entrypoint: grinding sound and dust. Kept as a hook so this class never touches client code. */
    public static Consumer<EnvyStatueEntity> clientTick = statue -> { };
    public boolean clientSoundStarted;

    private Direction facing = Direction.SOUTH;
    private Direction nextFacing = Direction.SOUTH;
    private Vec3d from;
    private int progress;
    private float turnFrom;
    private float turnTo;
    private int turnTicks;
    /** The maze this statue belongs to (its north-west corner and seed), or null for a statue placed by hand. */
    private BlockPos mazeOrigin;
    private long mazeSeed;
    private boolean mazeLookedUp;
    private boolean checkedIn;

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYaw;

    public EnvyStatueEntity(EntityType<? extends EnvyStatueEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        setNoGravity(true);
    }

    /** Puts the statue on the center of a maze walkway; from then on it only ever moves along that maze's corridors. */
    public void place(BlockPos center, Direction facing, BlockPos mazeOrigin, long mazeSeed) {
        this.mazeOrigin = mazeOrigin;
        this.mazeSeed = mazeSeed;
        this.mazeLookedUp = true;
        place(center, facing);
    }

    /** Puts the statue on the center of a walkway, facing the way it should start sliding. */
    public void place(BlockPos center, Direction facing) {
        this.facing = facing;
        this.nextFacing = facing;
        float yaw = facing.asRotation();
        refreshPositionAndAngles(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, yaw, 0f);
        this.from = getPos();
        // No sound here: this runs during world generation, off the server thread
        progress = 0;
        dataTracker.set(STATE, STATE_SLIDING);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(STATE, 0);
        builder.add(AWAKE, true);
    }

    public int getState() {
        return dataTracker.get(STATE);
    }

    /** False while no player is near its maze: it waits where it is, silent. Statues placed by hand are always awake. */
    public boolean isAwake() {
        return dataTracker.get(AWAKE);
    }

    // ---- Ticking ----

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            if (lerpSteps > 0) {
                double x = getX() + (lerpX - getX()) / lerpSteps;
                double y = getY() + (lerpY - getY()) / lerpSteps;
                double z = getZ() + (lerpZ - getZ()) / lerpSteps;
                setYaw(getYaw() + MathHelper.wrapDegrees(lerpYaw - getYaw()) / lerpSteps);
                lerpSteps--;
                setPosition(x, y, z);
            }
            if (getState() == STATE_SLIDING) {
                // It only ever slides straight along a walkway, so it always faces exactly along it
                float square = Math.round(getYaw() / 90f) * 90f;
                setYaw(square);
                lerpYaw = square;
            }
            clientTick.accept(this);
            return;
        }

        if (from == null) {
            // Summoned by hand: snap to the middle of the block we're on and work out where to go
            BlockPos at = getBlockPos();
            from = new Vec3d(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            facing = Direction.fromRotation(getYaw());
            decide();
        }
        if (!mazeLookedUp) {
            mazeLookedUp = true;
            findMyMaze();
        }
        if (!checkedIn && mazeOrigin != null && getWorld() instanceof ServerWorld world) {
            checkedIn = true;
            // Each maze keeps exactly 4; one too many (from an older save) crumbles away
            if (!EnvyMazes.claimSlot(world, mazeOrigin, getUuid())) {
                world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK,
                        net.minecraft.block.Blocks.MOSSY_STONE_BRICKS.getDefaultState()), getX(), getY() + 1.5, getZ(), 60, 1.0, 1.2, 1.0, 0.1);
                discard();
                return;
            }
        }

        if (mazeOrigin != null && getWorld() instanceof ServerWorld world) {
            dataTracker.set(AWAKE, EnvyMazes.playerNear(world, mazeOrigin));
            if (!isAwake()) return;
        }

        if (getState() == STATE_SLIDING) {
            progress++;
            double t = Math.min(1.0, (double) progress / SLIDE_TICKS);
            setYaw(facing.asRotation());
            setPosition(from.add(Vec3d.of(facing.getVector()).multiply(UNIT * t)));
            smashAhead();
            if (progress % 6 == 0) playSound(SoundEvents.BLOCK_GRINDSTONE_USE, 0.7f, 0.45f);
            if (progress >= SLIDE_TICKS) {
                from = from.add(Vec3d.of(facing.getVector()).multiply(UNIT));
                setPosition(from);
                decide();
            }
        } else if (getState() == STATE_TURNING) {
            progress++;
            float t = Math.min(1f, (float) progress / turnTicks);
            setYaw(MathHelper.lerpAngleDegrees(t, turnFrom, turnTo));
            if (progress % 8 == 0) playSound(SoundEvents.BLOCK_GRINDSTONE_USE, 0.6f, 0.3f);
            if (progress >= turnTicks) {
                setYaw(turnTo);
                facing = nextFacing;
                if (canEnter(facing)) startSlide();
                else decide();
            }
        } else {
            decide();
        }

        crushTouching();
    }

    /** Picks where to go from the center of a walkway. Never stops: it either slides on or starts turning. */
    private void decide() {
        List<Direction> options = new ArrayList<>();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (canEnter(dir)) options.add(dir);
        }
        if (options.size() > 1) options.remove(facing.getOpposite());

        Direction choice;
        if (options.isEmpty()) {
            choice = facing.rotateYClockwise(); // boxed in: keep grinding round until a way opens
        } else if (options.contains(facing) && random.nextFloat() < 0.55f) {
            choice = facing;
        } else {
            choice = options.get(random.nextInt(options.size()));
        }

        if (choice == facing && !options.isEmpty()) startSlide();
        else startTurn(choice);
    }

    private void startSlide() {
        progress = 0;
        dataTracker.set(STATE, STATE_SLIDING);
        playSound(SoundEvents.BLOCK_PISTON_EXTEND, 0.8f, 0.5f);
    }

    private void startTurn(Direction to) {
        progress = 0;
        nextFacing = to;
        turnFrom = getYaw();
        turnTo = to.asRotation();
        int quarters = Math.round(Math.abs(MathHelper.wrapDegrees(turnTo - turnFrom)) / 90f);
        turnTicks = Math.max(1, quarters) * TICKS_PER_QUARTER_TURN;
        dataTracker.set(STATE, STATE_TURNING);
        playSound(SoundEvents.BLOCK_PISTON_CONTRACT, 0.8f, 0.4f);
    }

    /**
     * Whether the next unit in {@code dir} is a walkway it may slide into. A maze statue checks its maze's layout
     * first, so it never leaves the maze or goes through a wall. Then the 3-wide, 4-high slice where the units meet
     * must have fewer than 3 solid blocks, and there must be floor to slide on.
     */
    public boolean canEnter(Direction dir) {
        BlockPos center = BlockPos.ofFloored(from);
        if (mazeOrigin != null) {
            int ux = Math.floorDiv(center.getX() - mazeOrigin.getX(), UNIT) + dir.getOffsetX();
            int uz = Math.floorDiv(center.getZ() - mazeOrigin.getZ(), UNIT) + dir.getOffsetZ();
            if (!layout().isPath(ux, uz)) return false;
        }
        BlockPos edge = center.offset(dir, 3);
        Direction side = dir.rotateYClockwise();
        int solid = 0;
        for (int lateral = -1; lateral <= 1; lateral++) {
            for (int h = 0; h <= 3; h++) {
                BlockPos pos = edge.offset(side, lateral).up(h);
                if (!getWorld().isChunkLoaded(pos)) return false;
                BlockState state = getWorld().getBlockState(pos);
                if (state.getHardness(getWorld(), pos) < 0) return false;
                if (!state.getCollisionShape(getWorld(), pos).isEmpty()) solid++;
            }
        }
        return solid < WALL_THRESHOLD && isFloor(edge.down()) && isFloor(center.offset(dir, UNIT).down());
    }

    private EnvyMazeLayout layout() {
        return EnvyMazes.layout(mazeSeed);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && mazeOrigin != null && getWorld() instanceof ServerWorld world) {
            EnvyMazes.release(world, mazeOrigin, getUuid());
        }
        super.remove(reason);
    }

    /**
     * For statues from before they remembered their maze: find the maze they're standing in and, if they've
     * wandered off the corridors, put them back on the nearest one.
     */
    private void findMyMaze() {
        if (!(getWorld() instanceof ServerWorld world)) return;
        Structure maze = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(MAZE);
        if (maze == null) return;
        StructureStart start = world.getStructureAccessor().getStructureContaining(BlockPos.ofFloored(from), maze);
        if (!start.hasChildren() || !(start.getChildren().get(0) instanceof EnvyMazePiece piece)) return;
        mazeOrigin = piece.origin();
        mazeSeed = piece.seed();

        BlockPos at = BlockPos.ofFloored(from);
        int lx = at.getX() - mazeOrigin.getX(), lz = at.getZ() - mazeOrigin.getZ();
        int ux = Math.floorDiv(lx, UNIT), uz = Math.floorDiv(lz, UNIT);
        boolean onCorridor = layout().isPath(ux, uz) && Math.floorMod(lx, UNIT) == 2 && Math.floorMod(lz, UNIT) == 2
                && at.getY() == EnvyMazePiece.FLOOR_Y + 1;
        if (onCorridor) return;

        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int x = 1; x < EnvyMazeLayout.N; x += 2) {
            for (int z = 1; z < EnvyMazeLayout.N; z += 2) {
                int d = Math.abs(x - ux) + Math.abs(z - uz);
                if (layout().isPath(x, z) && d < bestDistance) {
                    bestDistance = d;
                    best = new int[]{x, z};
                }
            }
        }
        if (best == null) return;
        place(piece.unitCenter(best[0], best[1]), facing);
        decide();
    }

    private boolean isFloor(BlockPos pos) {
        return getWorld().isChunkLoaded(pos) && !getWorld().getBlockState(pos).getCollisionShape(getWorld(), pos).isEmpty();
    }

    /** Breaks anything players have put in the walkway ahead, but never past where this slide ends (dead-end chests). */
    private void smashAhead() {
        double remaining = UNIT * (1.0 - Math.min(1.0, (double) progress / SLIDE_TICKS));
        Box ahead = getBoundingBox().stretch(Vec3d.of(facing.getVector()).multiply(Math.min(0.6, remaining))).contract(0.05);
        int floorY = MathHelper.floor(getY());
        for (BlockPos pos : BlockPos.iterate(MathHelper.floor(ahead.minX), floorY, MathHelper.floor(ahead.minZ),
                MathHelper.floor(ahead.maxX), MathHelper.floor(ahead.maxY), MathHelper.floor(ahead.maxZ))) {
            BlockState state = getWorld().getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.getHardness(getWorld(), pos) < 0) continue;
            getWorld().breakBlock(pos, true, this);
        }
    }

    private void crushTouching() {
        DamageSource source = new DamageSource(getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(CRUSHED), this);
        for (LivingEntity victim : getWorld().getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(0.15),
                e -> e.isAlive() && !e.isSpectator())) {
            if (!victim.damage(source, DAMAGE)) continue;
            Vec3d away = victim.getPos().subtract(getPos()).multiply(1, 0, 1);
            if (away.lengthSquared() < 1e-4) away = Vec3d.of(facing.getVector());
            Vec3d push = away.normalize().multiply(1.1).add(Vec3d.of(facing.getVector()).multiply(getState() == STATE_SLIDING ? 0.7 : 0));
            victim.addVelocity(push.x, 0.45, push.z);
            victim.velocityModified = true;
            playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.7f, 0.6f);
        }
    }

    // ---- It's a statue ----

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!getWorld().isClient && source.getAttacker() instanceof PlayerEntity) {
            playSound(SoundEvents.BLOCK_STONE_HIT, 1.0f, 0.6f);
        }
        return false;
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isImmuneToExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean canUsePortals(boolean allowVehicles) {
        return false;
    }

    // Movement-only updates reuse these as the target; without them a turn in progress would freeze part way (a tilt)
    @Override
    public double getLerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double getLerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double getLerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public float getLerpTargetYaw() {
        return lerpSteps > 0 ? lerpYaw : getYaw();
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        lerpSteps = 3;
    }

    // ---- Saving ----

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("Facing", facing.asString());
        nbt.putString("NextFacing", nextFacing.asString());
        nbt.putInt("State", getState());
        nbt.putInt("Progress", progress);
        nbt.putFloat("TurnFrom", turnFrom);
        nbt.putFloat("TurnTo", turnTo);
        nbt.putInt("TurnTicks", turnTicks);
        if (mazeOrigin != null) {
            nbt.putLong("MazeOrigin", mazeOrigin.asLong());
            nbt.putLong("MazeSeed", mazeSeed);
        }
        if (from != null) {
            nbt.putDouble("FromX", from.x);
            nbt.putDouble("FromY", from.y);
            nbt.putDouble("FromZ", from.z);
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        Direction saved = Direction.byName(nbt.getString("Facing"));
        if (saved != null && saved.getAxis().isHorizontal()) facing = saved;
        Direction next = Direction.byName(nbt.getString("NextFacing"));
        nextFacing = next != null && next.getAxis().isHorizontal() ? next : facing;
        dataTracker.set(STATE, nbt.getInt("State"));
        progress = nbt.getInt("Progress");
        turnFrom = nbt.getFloat("TurnFrom");
        turnTo = nbt.getFloat("TurnTo");
        turnTicks = Math.max(1, nbt.getInt("TurnTicks"));
        if (nbt.contains("MazeOrigin")) {
            mazeOrigin = BlockPos.fromLong(nbt.getLong("MazeOrigin"));
            mazeSeed = nbt.getLong("MazeSeed");
            mazeLookedUp = true;
        }
        if (nbt.contains("FromX")) from = new Vec3d(nbt.getDouble("FromX"), nbt.getDouble("FromY"), nbt.getDouble("FromZ"));
    }

    public Direction getFacing() {
        return facing;
    }

    public Vec3d getAnchor() {
        return from;
    }
}
