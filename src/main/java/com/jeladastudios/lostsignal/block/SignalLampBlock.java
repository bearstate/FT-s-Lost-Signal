package com.jeladastudios.lostsignal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The lookout's signal lamp, on the walkway: a bright lamp behind glass, switched on and off by hand. Lit at night it
 * is seen from the whole country round (the light on the hill a lost hiker is told to walk towards) and switched on
 * and off it says something to anyone watching. Drawn far off by the server as a point of light, as the hikers' own
 * signals are.
 */
public class SignalLampBlock extends Block {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** When each lamp was last switched, in game ticks. */
    private static final java.util.Map<BlockPos, Long> SWITCHED = new java.util.HashMap<>();

    /** A server stopped: when each lamp was switched is its world's, not the next one's. */
    public static void forget() {
        SWITCHED.clear();
    }

    private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 1, 12), Block.box(4.5, 1, 4.5, 11.5, 9, 11.5),
            Block.box(4, 9, 4, 12, 10, 12));

    public SignalLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        // Once a press: a hand held on the switch does not flicker it on and off.
        Long last = SWITCHED.get(pos);
        if (last != null && level.getGameTime() - last < 8) return InteractionResult.CONSUME;
        SWITCHED.put(pos.immutable(), level.getGameTime());
        boolean lit = !state.getValue(LIT);
        level.setBlock(pos, state.setValue(LIT, lit), 3);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, lit ? 1.1f : 0.8f);
        if (level instanceof ServerLevel server) com.jeladastudios.lostsignal.finder.Lamps.switched(server, pos, lit);
        return InteractionResult.CONSUME;
    }
}

