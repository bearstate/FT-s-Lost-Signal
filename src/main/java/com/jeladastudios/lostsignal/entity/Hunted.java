package com.jeladastudios.lostsignal.entity;

import com.jeladastudios.lostsignal.Config;
import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The night's monsters after the lost hikers as they are after villagers: zombies and skeletons, and spiders in the
 * dark. A world's settings can keep them off (hikers.monsters).
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Hunted {

    private Hunted() {}

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !Config.on(Config.HUNTED)) return;
        if (mob instanceof Zombie || mob instanceof AbstractSkeleton) {
            mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, Hiker.class, true));
        } else if (mob instanceof Spider) {
            // A spider hunts only where it is dark.
            mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, Hiker.class, 10, true, false,
                    h -> mob.getLightLevelDependentMagicValue() < 0.5f));
        }
    }
}
