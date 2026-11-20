package net.ragnar.fmscraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.ragnar.fmscraft.ending.TrueEnding;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;

/**
 * One of the seven sockets in the True Ending shrine: the end portal frame's shape in tuff, trimmed in its sin's color.
 * Use that sin's soul on it and the soul stands in the socket for good, the socket lights up and a beacon beam in the
 * sin's color rises out of it (drawn by SoulPedestalRenderer). When the seventh goes in, {@link TrueEnding} ends the game.
 */
public class SoulPedestalBlock extends BlockWithEntity {
    public static final MapCodec<SoulPedestalBlock> CODEC = createCodec(SoulPedestalBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<Sin> SIN = SinPortalBlock.SIN;
    public static final BooleanProperty SOUL = BooleanProperty.of("soul");
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 13, 16);

    public SoulPedestalBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(SIN, Sin.GREED).with(SOUL, false));
    }

    public static BlockState of(Sin sin, Direction facing) {
        return ModBlocks.SOUL_PEDESTAL.getDefaultState().with(SIN, sin).with(FACING, facing);
    }

    /** The sin whose soul this is, or null for anything else. */
    @Nullable
    public static Sin soulOf(ItemStack stack) {
        Item item = stack.getItem();
        for (Map.Entry<Sin, Item> soul : ModItems.SOULS.entrySet()) if (soul.getValue() == item) return soul.getKey();
        return null;
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIN, SOUL);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SoulPedestalBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected boolean hasSidedTransparency(BlockState state) {
        return true;
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                             Hand hand, BlockHitResult hit) {
        Sin soul = soulOf(stack);
        if (soul == null) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;

        Sin sin = state.get(SIN);
        if (state.get(SOUL)) {
            player.sendMessage(Text.translatable("message.fmscraft.pedestal_full", sin.displayName()).withColor(sin.color()), true);
            return ItemActionResult.CONSUME;
        }
        if (soul != sin) {
            player.sendMessage(Text.translatable("message.fmscraft.pedestal_wrong_soul", sin.displayName()).formatted(Formatting.GRAY), true);
            world.playSound(null, pos, SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 1.0f, 0.7f);
            return ItemActionResult.CONSUME;
        }

        stack.decrementUnlessCreative(1, player);
        world.setBlockState(pos, state.with(SOUL, true));
        world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0f, 0.8f);
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.0f);
        ((ServerWorld) world).spawnParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                12, 0.25, 0.3, 0.25, 0.03);
        TrueEnding.onSoulPlaced((ServerWorld) world, pos, sin, (ServerPlayerEntity) player);
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            Sin sin = state.get(SIN);
            String key = state.get(SOUL) ? "message.fmscraft.pedestal_full" : "message.fmscraft.pedestal_awaits";
            player.sendMessage(Text.translatable(key, sin.displayName()).withColor(sin.color()), true);
            if (!state.get(SOUL)) world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 0.3f, 1.6f);
        }
        return ActionResult.SUCCESS;
    }

    /** Specks of the sin's color drift out of the socket: a few while it waits, and souls with them once it's filled. */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        int color = state.get(SIN).color();
        Vector3f rgb = new Vector3f(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f)
                .lerp(new Vector3f(1, 1, 1), 0.3f);
        DustParticleEffect dust = new DustParticleEffect(rgb, 0.6f);
        boolean soul = state.get(SOUL);
        int count = soul ? 3 : random.nextInt(3) == 0 ? 1 : 0;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2, r = 0.05 + random.nextDouble() * 0.25;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            world.addParticle(dust, pos.getX() + 0.5 + dx * r, pos.getY() + 0.85, pos.getZ() + 0.5 + dz * r, dx, 0.5, dz);
        }
        if (soul && random.nextInt(4) == 0) {
            world.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.9,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4, 0, 0.04, 0);
        }
    }
}
