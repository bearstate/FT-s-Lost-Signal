package com.jeladastudios.lostsignal.item;

import com.jeladastudios.lostsignal.signal.Signals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A hand flare, as the keepers and the supply crates give them: struck and held up, it goes up some forty blocks and
 * comes down slowly under its parachute, burning red, as a lost hiker's does. Seen from the lookouts round, whose
 * keepers answer it, and read off a fire finder like any other.
 */
public class FlareItem extends Item {

    /** How long after one flare before the next can be struck, in ticks. */
    static final int COOLDOWN = 40;
    /** How long a flare takes to the top of its climb, in ticks, and how high that is over where it was fired. */
    static final int CLIMB = 45;
    static final double TOP = 45;

    public FlareItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 3f, 0.75f);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer p) {
            Vec3 from = player.position();
            Signals.flare(server, from);
            com.jeladastudios.lostsignal.tower.Guide.fired(p, "flare", from.add(0, TOP, 0), CLIMB);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
