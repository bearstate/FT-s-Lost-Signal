package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.net.Net;
import com.jeladastudios.lostsignal.rescue.Order;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The rescue panel at a lookout's radio: what the keeper can ask a lost hiker and tell them, as buttons. Pressing one
 * says it on the air; the answer comes back on the radio, its words in the chat. The keeper may speak it too, with the
 * radio's own push-to-talk -- the panel is what the hiker understands.
 */
public class RescueScreen extends Screen {

    private EditBox bearing;

    /** The last reading off the fire finder, and when it came, in the client's ticks; null where none. */
    private static Net.Reading last;
    private static long lastAt;
    /** How long a reading stays in the panel: five minutes. */
    static final long KEEPS = 20 * 60 * 5;

    public static void reading(Net.Reading r) {
        last = r;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        lastAt = level == null ? 0 : level.getGameTime();
    }

    private static Net.Reading fresh() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return last != null && level != null && level.getGameTime() - lastAt < KEEPS ? last : null;
    }

    public RescueScreen() {
        super(Component.translatable("rescue.fts_lostsignal.title"));
        // Which lookouts are round, for the calls: asked of the server as the panel opens.
        Net.CHANNEL.sendToServer(new Net.Panel());
    }

    /** The lookouts round the player's, as the server last told them. */
    private static java.util.List<Net.Neighbour> round = java.util.List.of();

    public static void neighbours(java.util.List<Net.Neighbour> list) {
        round = list;
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof RescueScreen s) s.rebuildWidgets();
    }

    /** The calls: the District, and the lookouts round, each with its bearing and distance. */
    private void calls(int left, int y, int width, int h, int gap) {
        int n = 1 + round.size(), w = (width - (n - 1) * gap) / n, x = left;
        addRenderableWidget(Button.builder(Component.translatable("call.fts_lostsignal.district"),
                b -> Net.CHANNEL.sendToServer(new Net.Call(0))).bounds(x, y, w, h).build());
        for (Net.Neighbour nb : round) {
            x += w + gap;
            String point = com.jeladastudios.lostsignal.finder.FireFinder.point(nb.bearing());
            addRenderableWidget(Button.builder(Component.translatable("call.fts_lostsignal.button", nb.number(),
                            Component.translatable("radio.fts_lostsignal.point_short." + point), nb.distance()),
                    b -> Net.CHANNEL.sendToServer(new Net.Call(nb.key()))).bounds(x, y, w, h).build());
        }
    }

    @Override
    protected void init() {
        headings.clear();
        int w = 150, h = 20, gap = 4;
        int left = width / 2 - w * 3 / 2 - gap, top = height / 2 - 70;
        column(left, top, w, h, gap, "rescue.fts_lostsignal.ask", Order.WHERE, Order.HOW, Order.COMPASS);
        column(left + w + gap, top, w, h, gap, "rescue.fts_lostsignal.tell",
                Order.FOLLOW_WATER, Order.FOLLOW_TRAIL, Order.FOLLOW_SIGN, Order.TOWARD_LIGHT, Order.STAY);
        column(left + 2 * (w + gap), top, w, h, gap, "rescue.fts_lostsignal.signal", Order.FLARE, Order.SMOKE, Order.LAMP);
        // A bearing in degrees, as a compass reads it, and the button to send it.
        int y = top + 6 * (h + gap) + 8;
        bearing = new EditBox(font, left + 2 * (w + gap), y, w / 3 - 2, h, Component.translatable("rescue.fts_lostsignal.bearing"));
        bearing.setMaxLength(3);
        bearing.setFilter(s -> s.matches("\\d{0,3}"));
        // The way back from the last signal read off the fire finder, ready to send; else north.
        Net.Reading r = fresh();
        bearing.setValue(r == null ? "0" : String.valueOf(Math.floorMod(r.bearing() + 180, 360)));
        addRenderableWidget(bearing);
        addRenderableWidget(Button.builder(Component.translatable("rescue.fts_lostsignal.order.bearing"), b -> {
            int deg = bearing.getValue().isEmpty() ? 0 : Math.floorMod(Integer.parseInt(bearing.getValue()), 360);
            Net.CHANNEL.sendToServer(new Net.Say(Order.BEARING, deg));
        }).bounds(left + 2 * (w + gap) + w / 3 + 2, y, w - w / 3 - 2, h).build());
        // Calling out: a row under the rest.
        headings.add(new Object[]{"call.fts_lostsignal.heading", left, y + h + 6});
        calls(left, y + h + 18, 3 * w + 2 * gap, h, gap);
    }

    private void column(int x, int y, int w, int h, int gap, String heading, Order... orders) {
        int row = 1;
        for (Order o : orders) {
            addRenderableWidget(Button.builder(Component.translatable("rescue.fts_lostsignal.order." + o.name().toLowerCase(java.util.Locale.ROOT)),
                    b -> Net.CHANNEL.sendToServer(new Net.Say(o, 0))).bounds(x, y + row * (h + gap), w, h).build());
            row++;
        }
        headings.add(new Object[]{heading, x, y + 6});
    }

    private final java.util.List<Object[]> headings = new java.util.ArrayList<>();

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, height / 2 - 96, 0xFFFFFF);
        for (Object[] hd : headings) g.drawString(font, Component.translatable((String) hd[0]), (int) hd[1], (int) hd[2], 0xA0A0A0);
        Net.Reading r = fresh();
        if (r != null) {
            g.drawCenteredString(font, Component.translatable("rescue.fts_lostsignal.last_reading",
                    Component.translatable("finder.fts_lostsignal.kind." + r.kind()), r.bearing(), r.distance(),
                    Math.floorMod(r.bearing() + 180, 360)), width / 2, height / 2 + 150, 0xE0C080);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
