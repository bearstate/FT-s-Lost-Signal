package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.LostSignal;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** The key that opens the rescue panel, at a radio station. */
public final class Keys {

    private Keys() {}

    public static final KeyMapping PANEL = new KeyMapping("key.fts_lostsignal.panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
            "key.categories.fts_lostsignal");

    @Mod.EventBusSubscriber(modid = LostSignal.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        private Register() {}

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(PANEL);
        }
    }

    @Mod.EventBusSubscriber(modid = LostSignal.MODID, value = Dist.CLIENT)
    public static final class Press {
        private Press() {}

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (PANEL.consumeClick()) {
                if (mc.player == null || mc.level == null || mc.screen != null) continue;
                if (nearStation(mc)) mc.setScreen(new RescueScreen(false));
                // Away from a station: a handheld on the frequency, as far as it carries.
                else if (!com.jeladastudios.lostsignal.rescue.Handheld.tuned(mc.player).isEmpty()) mc.setScreen(new RescueScreen(true));
                else if (com.jeladastudios.lostsignal.rescue.Handheld.onlyPmr(mc.player)) mc.player.displayClientMessage(Component.translatable("rescue.fts_lostsignal.pmr"), true);
                else mc.player.displayClientMessage(Component.translatable("rescue.fts_lostsignal.no_station"), true);
            }
        }

        static boolean nearStation(Minecraft mc) {
            BlockPos at = mc.player.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-6, -3, -6), at.offset(6, 3, 6))) {
                var id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(mc.level.getBlockState(p).getBlock());
                if (id != null && id.getNamespace().equals("simpleradio") && id.getPath().equals("station")) return true;
            }
            return false;
        }
    }
}
