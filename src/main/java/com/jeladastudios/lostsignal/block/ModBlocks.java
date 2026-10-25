package com.jeladastudios.lostsignal.block;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.remains.StainBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's blocks, and the items of those that can be had: the marks left on the ground (put there by the world, not
 * to be had), the lookout's signal lamp, terminal, lightning stool and fire danger sign; and the hand flare.
 */
public final class ModBlocks {

    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, LostSignal.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LostSignal.MODID);

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

    public static final RegistryObject<SignalLampBlock> SIGNAL_LAMP = BLOCKS.register("signal_lamp",
            () -> new SignalLampBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5f)
                    .sound(SoundType.LANTERN)
                    .noOcclusion()
                    .lightLevel(s -> s.getValue(SignalLampBlock.LIT) ? 15 : 0)));

    public static final RegistryObject<BlockItem> SIGNAL_LAMP_ITEM = ITEMS.register("signal_lamp",
            () -> new BlockItem(SIGNAL_LAMP.get(), new Item.Properties()));

    public static final RegistryObject<com.jeladastudios.lostsignal.desk.TerminalBlock> TERMINAL = BLOCKS.register("terminal",
            () -> new com.jeladastudios.lostsignal.desk.TerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .strength(1.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(s -> s.getValue(com.jeladastudios.lostsignal.desk.TerminalBlock.ON) ? 5 : 0)));

    public static final RegistryObject<BlockItem> TERMINAL_ITEM = ITEMS.register("terminal",
            () -> new BlockItem(TERMINAL.get(), new Item.Properties()));

    public static final RegistryObject<StoolBlock> STOOL = BLOCKS.register("lightning_stool",
            () -> new StoolBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<BlockItem> STOOL_ITEM = ITEMS.register("lightning_stool",
            () -> new BlockItem(STOOL.get(), new Item.Properties()));

    public static final RegistryObject<com.jeladastudios.lostsignal.desk.DangerSign> DANGER_SIGN = BLOCKS.register("fire_danger_sign",
            () -> new com.jeladastudios.lostsignal.desk.DangerSign(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<BlockItem> DANGER_SIGN_ITEM = ITEMS.register("fire_danger_sign",
            () -> new BlockItem(DANGER_SIGN.get(), new Item.Properties()));

    public static final RegistryObject<Item> FLARE = ITEMS.register("flare",
            () -> new com.jeladastudios.lostsignal.item.FlareItem(new Item.Properties().stacksTo(16)));
}
