package com.jeladastudios.lostsignal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The lightning stool every fire lookout had: a wooden stool on four glass insulators. In a lightning storm the keeper
 * stands on it, off the floor, and a strike on the tower goes by them.
 */
public class StoolBlock extends Block {

    /** The seat, and the legs and their glass feet under it. */
    static final VoxelShape SHAPE = Shapes.or(Block.box(3, 9, 3, 13, 10.5, 13), Block.box(3.5, 0, 3.5, 5, 9, 5), Block.box(11, 0, 3.5, 12.5, 9, 5),
            Block.box(3.5, 0, 11, 5, 9, 12.5), Block.box(11, 0, 11, 12.5, 9, 12.5));

    public StoolBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Whether an entity stands on a stool: on its seat, or the stool just under their feet. */
    public static boolean on(net.minecraft.world.entity.Entity e) {
        var level = e.level();
        BlockPos at = BlockPos.containing(e.getX(), e.getY() - 0.05, e.getZ());
        return level.getBlockState(at).getBlock() instanceof StoolBlock && e.onGround();
    }
}
