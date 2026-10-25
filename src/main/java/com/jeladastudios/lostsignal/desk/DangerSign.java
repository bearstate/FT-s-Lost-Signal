package com.jeladastudios.lostsignal.desk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The fire danger sign by the foot of the trail: a board on a post, its arrow over one of five colours, low to extreme.
 * Used, the arrow moves on a step. Set each morning to the District's rating, as a keeper's first job of the day.
 */
public class DangerSign extends HorizontalDirectionalBlock {

    public static final IntegerProperty RATING = IntegerProperty.create("rating", 0, 4);
    /** How near their lookout a keeper's sign must be to be theirs, in blocks. */
    static final double AT_TOWER = 64;

    static final VoxelShape NS = Shapes.or(Block.box(7, 0, 7, 9, 5, 10), Block.box(1, 4, 7, 15, 16, 9));
    static final VoxelShape EW = Shapes.or(Block.box(6, 0, 7, 9, 5, 9), Block.box(7, 4, 1, 9, 16, 15));

    public DangerSign(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RATING, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RATING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        int next = (state.getValue(RATING) + 1) % 5;
        level.setBlock(pos, state.setValue(RATING, next), 3);
        level.playSound(null, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.7f, 0.9f);
        if (player instanceof ServerPlayer p && level instanceof ServerLevel server) set(server, p, pos, next);
        return InteractionResult.CONSUME;
    }

    /** The sign at a keeper's lookout set to the day's rating: a word for it, and a little to their credit once a day. */
    static void set(ServerLevel level, ServerPlayer p, BlockPos pos, int rating) {
        Post post = Desk.posted(p);
        if (post == null) return;
        var tower = com.jeladastudios.lostsignal.tower.Towers.known(level, post.tower);
        if (tower == null || Math.hypot(pos.getX() - tower.x, pos.getZ() - tower.z) > AT_TOWER) return;
        Danger.Rating today = Danger.today(level);
        p.displayClientMessage(Component.translatable("desk.fts_lostsignal.sign.shows", Component.translatable("desk.fts_lostsignal.danger." + Danger.Rating.values()[rating].key())), true);
        long day = level.getDayTime() / 24000;
        if (rating == today.ordinal() && post.signDay != day) {
            post.signDay = day;
            Desk.earn(p, "desk.fts_lostsignal.bonus.sign", Desk.SIGN_PAY);
            Desk.of(level).setDirty();
        }
    }
}
