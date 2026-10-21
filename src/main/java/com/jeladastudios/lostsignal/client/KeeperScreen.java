package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.net.Net;
import com.jeladastudios.lostsignal.tower.Talk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Talking with a keeper face to face: what one may ask them, as buttons; their answers are spoken, and subtitled. */
public class KeeperScreen extends Screen {

    private final int entity;

    KeeperScreen(Net.Talk talk) {
        super(Component.translatable("talk.fts_lostsignal.title", talk.name(), talk.number()));
        this.entity = talk.entity();
    }

    public static void open(Net.Talk talk) {
        Minecraft.getInstance().setScreen(new KeeperScreen(talk));
    }

    @Override
    protected void init() {
        int w = 200, h = 20, gap = 4;
        int x = width / 2 - w / 2, y = height / 2 - 50;
        for (Talk.Topic topic : Talk.Topic.values()) {
            addRenderableWidget(Button.builder(Component.translatable("talk.fts_lostsignal.topic." + topic.name().toLowerCase(java.util.Locale.ROOT)), b -> {
                Net.CHANNEL.sendToServer(new Net.Ask(entity, topic.ordinal()));
                if (topic == Talk.Topic.BYE) onClose();
            }).bounds(x, y, w, h).build());
            y += h + gap;
        }
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
