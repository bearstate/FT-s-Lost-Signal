package com.jeladastudios.lostsignal.entity;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The mod's creatures. */
@Mod.EventBusSubscriber(modid = LostSignal.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {

    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, LostSignal.MODID);

    /**
     * The watcher: as tall as a tall man, a little taller. Sent to players ten chunks out, as far as it is ever put from
     * them, so it is there to be seen at the edge of the trees.
     */
    public static final RegistryObject<EntityType<Watcher>> WATCHER = ENTITIES.register("watcher",
            () -> EntityType.Builder.of(Watcher::new, MobCategory.MISC)
                    .sized(0.6f, 2.1f)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("watcher"));

    /** A lost hiker: a person's size; sent to players twelve chunks out, so a bright jacket is seen from a lookout. */
    public static final RegistryObject<EntityType<Hiker>> HIKER = ENTITIES.register("hiker",
            () -> EntityType.Builder.of(Hiker::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(12)
                    .updateInterval(3)
                    .build("hiker"));

    /** What is left of a hiker: lying, so low and long; seen from not far off, as a thing on the ground is. */
    public static final RegistryObject<EntityType<com.jeladastudios.lostsignal.remains.Remains>> REMAINS = ENTITIES.register("remains",
            () -> EntityType.Builder.<com.jeladastudios.lostsignal.remains.Remains>of(com.jeladastudios.lostsignal.remains.Remains::new, MobCategory.MISC)
                    .sized(1.0f, 0.4f)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("remains"));

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(WATCHER.get(), Watcher.attributes().build());
        event.put(HIKER.get(), Hiker.attributes().build());
    }
}
