package net.ragnar.fmscraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.item.SinKeyItem;
import net.ragnar.fmscraft.sin.Sin;

/**
 * The lock in the Envy maze's safe room: a carved stone eye. Use the Envy Key on it and it opens its green eye,
 * and a 3x3 portal fills the copper frame 4 blocks behind it. It stays open (lit) for as long as the fight runs.
 */
public class EnviousEyeBlock extends Block {
    public static final MapCodec<EnviousEyeBlock> CODEC = createCodec(EnviousEyeBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = Properties.LIT;
    public static final int PORTAL_DISTANCE = 4;
    public static final int PORTAL_SIZE = 3;

    public EnviousEyeBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                             Hand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof SinKeyItem key)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;

        if (key.getSin() != Sin.ENVY) {
            player.sendMessage(Text.translatable("message.fmscraft.wrong_key").formatted(Formatting.GRAY), true);
            world.playSound(null, pos, SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 1.0f, 0.7f);
            return ItemActionResult.CONSUME;
        }

        Direction facing = state.get(FACING);
        Direction.Axis axis = facing.rotateYClockwise().getAxis();
        BlockPos center = pos.offset(facing.getOpposite(), PORTAL_DISTANCE);
        BlockPos base = center.offset(BossFights.along(axis).getOpposite(), PORTAL_SIZE / 2);
        if (!BossFights.open(Sin.ENVY, (ServerWorld) world, base, axis, BossFights.JOIN_TICKS, PORTAL_SIZE, PORTAL_SIZE)) {
            player.sendMessage(Text.translatable("message.fmscraft.eye_open").formatted(Formatting.GRAY), true);
            return ItemActionResult.CONSUME;
        }

        stack.decrementUnlessCreative(1, player);
        world.setBlockState(pos, state.with(LIT, true));
        world.scheduleBlockTick(pos, this, 20);
        world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0f, 0.6f);
        world.playSound(null, pos, SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.BLOCKS, 1.0f, 0.5f);
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && !state.get(LIT)) {
            player.sendMessage(Text.translatable("message.fmscraft.eye_hungers").formatted(Formatting.DARK_GREEN), true);
            world.playSound(null, pos, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.BLOCKS, 0.4f, 1.6f);
        }
        return ActionResult.SUCCESS;
    }

    /** Closes the eye again once the fight is over. */
    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!state.get(LIT)) return;
        if (BossFights.session(Sin.ENVY) == null) {
            world.setBlockState(pos, state.with(LIT, false));
            world.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 0.6f, 1.4f);
        } else {
            world.scheduleBlockTick(pos, this, 20);
        }
    }
}
