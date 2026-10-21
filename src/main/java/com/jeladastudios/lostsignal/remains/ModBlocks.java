package com.jeladastudios.lostsignal.remains;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The mod's blocks: only the marks left on the ground. Not to be had as items; the world puts them there. */
public final class ModBlocks {

    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, LostSignal.MODID);

    public static final RegistryObject<StainBlock> STAIN = BLOCKS.register("stain",
            () -> new StainBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .replaceable()
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .noLootTable()
                    .sound(SoundType.MUD)
                    .pushReaction(PushReaction.DESTROY)));
}
