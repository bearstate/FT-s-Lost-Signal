package com.jeladastudios.lostsignal.remains;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A dark mark on the ground where something happened: a splash, a smear where something was dragged, the print of a
 * long foot. Flat on the ground, nothing to walk into, gone under a step; it dries and fades over a few days, and is
 * gone.
 */
public class StainBlock extends HorizontalDirectionalBlock {

    /**
     * What the mark is: six splashes; a drag smear across the block toward its facing (two of them), turning left or
     * right out of it, or ending in it; a print, its toes to its facing. The smear pieces meet at their edges, so a
     * run of them reads as one smear.
     */
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 11);
    public static final int SPLASHES = 6, STRAIGHT = 6, LEFT = 8, RIGHT = 9, END = 10, PRINT = 11;
    /** Fresh, dried, faint. */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);

    /**
     * One step older at about one in this many random ticks: a block's random tick comes about once a game hour, so a
     * step is a day or so, and the mark is gone in four.
     */
    static final int AGES = 24;

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 0.25, 16);

    public StainBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KIND, 0).setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KIND, AGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction from, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return from == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(AGES) != 0) return;
        int age = state.getValue(AGE);
        if (age < 2) level.setBlock(pos, state.setValue(AGE, age + 1), 2);
        else level.removeBlock(pos, false);
    }
}
