package com.jeladastudios.lostsignal.desk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The lookout's terminal: a beige tube monitor and its keyboard on the desk, wired to the satellite modem and run off
 * the station's power. With a powered radio station within a few blocks its screen is lit, and used it opens; with
 * the generator cold, it is dark.
 */
public class TerminalBlock extends HorizontalDirectionalBlock {

    public static final BooleanProperty ON = BooleanProperty.create("on");
    /** How near a powered station must be for the terminal to have power, in blocks. */
    static final int WIRED = 8;

    /** The monitor at the back, the keyboard before it; facing north, and turned. */
    static final VoxelShape NORTH = Shapes.or(Block.box(2, 0, 5, 14, 12, 15), Block.box(1, 0, 0, 15, 2, 5));
    static final VoxelShape SOUTH = Shapes.or(Block.box(2, 0, 1, 14, 12, 11), Block.box(1, 0, 11, 15, 2, 16));
    static final VoxelShape EAST = Shapes.or(Block.box(1, 0, 2, 11, 12, 14), Block.box(11, 0, 1, 16, 2, 15));
    static final VoxelShape WEST = Shapes.or(Block.box(5, 0, 2, 15, 12, 14), Block.box(0, 0, 1, 5, 2, 15));

    public TerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The screen towards whoever set it down.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 20);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        lit(level, pos, state, powered(level, pos));
        level.scheduleTick(pos, this, 100);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer p)) return InteractionResult.CONSUME;
        boolean powered = powered(server, pos);
        lit(server, pos, state, powered);
        if (!powered || !com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.DESK)) {
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, 1.6f);
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable(powered ? "desk.fts_lostsignal.off" : "desk.fts_lostsignal.dark"), true);
            return InteractionResult.CONSUME;
        }
        Terminal.open(p, pos);
        return InteractionResult.CONSUME;
    }

    static void lit(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
        if (state.getValue(ON) != on) level.setBlock(pos, state.setValue(ON, on), 3);
    }

    /** Whether a powered radio station is near enough for the terminal to run off its power. */
    public static boolean powered(ServerLevel level, BlockPos pos) {
        return com.jeladastudios.lostsignal.rescue.Stations.poweredNear(level, pos, WIRED) != null;
    }
}
