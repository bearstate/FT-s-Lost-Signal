package com.jeladastudios.lostsignal.signal;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.block.ModBlocks;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Signals a player makes by hand. A lantern held up and swung over their head (used at the air), seen from far off at
 * night as a lost hiker's lamp is, and by the lookouts round. And the flares the keepers and the crates gave before
 * there were flares of their own, rockets by another name, made flares where they are carried.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Held {

    private Held() {}

    /** How long a lantern is swung for, in seconds. */
    static final int SWUNG = 10;

    @SubscribeEvent
    public static void swing(PlayerInteractEvent.RightClickItem event) {
        ItemStack held = event.getItemStack();
        if (!held.is(Items.LANTERN) && !held.is(Items.SOUL_LANTERN)) return;
        Player player = event.getEntity();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        // Not again while it is still being swung.
        player.getCooldowns().addCooldown(held.getItem(), SWUNG * 20);
        if (player instanceof ServerPlayer p) {
            Signals.lamp(p.serverLevel(), p, SWUNG);
            com.jeladastudios.lostsignal.tower.Guide.fired(p, "lamp", p.position().add(0, 2.2, 0), 60);
        }
    }

    @SubscribeEvent
    public static void carried(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 100 != 0) return;
        var inventory = p.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(Items.FIREWORK_ROCKET) && s.hasCustomHoverName() && s.getHoverName().getContents() instanceof TranslatableContents t
                    && t.getKey().equals("item.fts_lostsignal.flare")) {
                inventory.setItem(i, new ItemStack(ModBlocks.FLARE.get(), s.getCount()));
            }
        }
    }
}
