package net.ragnar.fmscraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
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
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.item.SinKeyItem;
import net.ragnar.fmscraft.sin.Sin;

/**
 * The key lock in every sin's structure, built on the vanilla end portal frame: same end stone body, but a keyhole
 * where the eye would go and trim in its sin's color. Use that sin's key on it and the key slots in, standing up out
 * of the keyhole, and a 3x3 portal fills the frame {@link #PORTAL_DISTANCE} blocks behind it. The key stays in for
 * as long as the fight runs, then crumbles away.
 */
public class SinPortalFrameBlock extends HorizontalFacingBlock {
    public static final MapCodec<SinPortalFrameBlock> CODEC = createCodec(SinPortalFrameBlock::new);
    public static final EnumProperty<Sin> SIN = SinPortalBlock.SIN;
    public static final BooleanProperty KEY = BooleanProperty.of("key");
    public static final int PORTAL_DISTANCE = 4;
    public static final int PORTAL_SIZE = 3;
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 13, 16);

    public SinPortalFrameBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(SIN, Sin.GREED).with(KEY, false));
    }

    /** The frame for {@code sin}, facing the way players stand to use it (its portal opens behind it). */
    public static BlockState of(Sin sin, Direction facing) {
        return ModBlocks.SIN_PORTAL_FRAME.getDefaultState().with(SIN, sin).with(FACING, facing);
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIN, KEY);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
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
        if (!(stack.getItem() instanceof SinKeyItem key)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;

        Sin sin = state.get(SIN);
        if (key.getSin() != sin) {
            player.sendMessage(Text.translatable("message.fmscraft.wrong_key").formatted(Formatting.GRAY), true);
            world.playSound(null, pos, SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 1.0f, 0.7f);
            return ItemActionResult.CONSUME;
        }

        Direction facing = state.get(FACING);
        Direction.Axis axis = facing.rotateYClockwise().getAxis();
        BlockPos center = pos.offset(facing.getOpposite(), PORTAL_DISTANCE);
        BlockPos base = center.offset(BossFights.along(axis).getOpposite(), PORTAL_SIZE / 2);
        if (!BossFights.open(sin, (ServerWorld) world, base, axis, BossFights.JOIN_TICKS, PORTAL_SIZE, PORTAL_SIZE)) {
            player.sendMessage(Text.translatable("message.fmscraft.frame_open").formatted(Formatting.GRAY), true);
            return ItemActionResult.CONSUME;
        }

        stack.decrementUnlessCreative(1, player);
        world.setBlockState(pos, state.with(KEY, true));
        world.scheduleBlockTick(pos, this, 20);
        world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0f, 0.8f);
        world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 0.8f, 0.6f);
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && !state.get(KEY)) {
            Sin sin = state.get(SIN);
            player.sendMessage(Text.translatable("message.fmscraft.frame_awaits", sin.displayName()).withColor(sin.color()), true);
            world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 0.3f, 1.6f);
        }
        return ActionResult.SUCCESS;
    }

    /** Once the fight is over the key crumbles out of the lock. */
    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!state.get(KEY)) return;
        if (BossFights.session(state.get(SIN)) == null) {
            world.setBlockState(pos, state.with(KEY, false));
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 0.8f, 0.6f);
        } else {
            world.scheduleBlockTick(pos, this, 20);
        }
    }
}
