package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.entity.Hiker;
import com.jeladastudios.lostsignal.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Talking with a lost hiker face to face: what one may tell them, as buttons; their answers are spoken, and subtitled. */
public class HikerScreen extends Screen {

    private final int entity;
    private final boolean lookoutKnown;

    HikerScreen(Net.Meet meet) {
        super(Component.translatable("hiker.fts_lostsignal.title", meet.name()));
        this.entity = meet.entity();
        this.lookoutKnown = meet.lookoutKnown();
    }

    public static void open(Net.Meet meet) {
        Minecraft.getInstance().setScreen(new HikerScreen(meet));
    }

    @Override
    protected void init() {
        int w = 200, h = 20, gap = 4;
        int x = width / 2 - w / 2, y = height / 2 - 50;
        for (Hiker.Bid bid : Hiker.Bid.values()) {
            // Sent to the lookout only where they know of one.
            if (bid == Hiker.Bid.LOOKOUT && !lookoutKnown) continue;
            addRenderableWidget(Button.builder(Component.translatable("hiker.fts_lostsignal.bid." + bid.name().toLowerCase(java.util.Locale.ROOT)), b -> {
                Net.CHANNEL.sendToServer(new Net.Bid(entity, bid.ordinal()));
                onClose();
            }).bounds(x, y, w, h).build());
            y += h + gap;
        }
        addRenderableWidget(Button.builder(Component.translatable("hiker.fts_lostsignal.bye"), b -> onClose()).bounds(x, y, w, h).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, height / 2 - 70, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
