package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.ModEntities;
import com.jeladastudios.lostsignal.entity.Stranger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * A lookout's dog: every lookout should have one, and now and then a family sends one up with their thanks. A dog of
 * anyone's knows the stranger: it growls at him when he is near, by day or by night, and looks his way, wherever he
 * stands (behind the door, out in the trees), before anyone sees him.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Dogs {

    private Dogs() {}

    /** How far off a dog knows him, in blocks. */
    static final double KNOWS = 28;
    /** The names a sent dog is given. */
    static final String[] NAMES = {"Pine", "Juniper", "Cinder", "Tamarack", "Birch", "Scout", "Ash", "Ranger"};

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 20 != 7) return;
        for (Stranger s : level.getEntities(ModEntities.STRANGER.get(), e -> e.isAlive())) {
            for (Wolf w : level.getEntitiesOfClass(Wolf.class, new AABB(s.blockPosition()).inflate(KNOWS), w -> w.isTame() && w.isAlive())) {
                w.getLookControl().setLookAt(s, 30f, 30f);
                if (level.random.nextInt(3) == 0) {
                    level.playSound(null, w.getX(), w.getY(), w.getZ(), SoundEvents.WOLF_GROWL, SoundSource.NEUTRAL, 1f, w.isBaby() ? 1.4f : 0.9f);
                }
            }
        }
    }

    /** A pup sent up with a letter: put down by the crate at the foot of the trail, its keeper's, named. */
    static boolean pup(ServerLevel level, net.minecraft.core.BlockPos at, UUID owner) {
        Wolf w = EntityType.WOLF.create(level);
        if (w == null) return false;
        w.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        w.setBaby(true);
        w.setTame(true);
        w.setOwnerUUID(owner);
        w.setCustomName(net.minecraft.network.chat.Component.literal(NAMES[level.random.nextInt(NAMES.length)]));
        w.setPersistenceRequired();
        level.addFreshEntity(w);
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("DOG {} put down at {}", w.getCustomName().getString(), at.toShortString());
        return true;
    }
}
