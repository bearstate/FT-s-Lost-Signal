package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.desk.Mail;
import com.jeladastudios.lostsignal.desk.Observation;
import com.jeladastudios.lostsignal.desk.Terminal;
import com.jeladastudios.lostsignal.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The lookout terminal's screen: a 1994 tube monitor in a beige case, green phosphor on black, scan lines, a little
 * flicker, the words a shade blurred. Pages for the messages, the duty roster, the one o'clock weather observation,
 * the smoke report, orders, and the keeper's record. Worked with the mouse or wholly from the keyboard: the number keys
 * turn the pages (with Ctrl while typing in a field), Tab moves between fields, the arrows choose, Enter sends.
 */
public class TerminalScreen extends Screen {

    static final int GREEN = 0xFF66FF88, DIM = 0xFF2F9A4A, BRIGHT = 0xFFC2FFD0, BACK = 0xFF061209, BEZEL = 0xFFC9BFA3, BEZEL_DARK = 0xFF8F866E;
    static final String[] TABS = {"inbox", "duties", "weather", "smoke", "orders", "record"};

    /** The last the server said of the terminal; the page shown; what is chosen on each; what has been typed. */
    static Net.TermState state;
    static int tab;
    static int mail, item, lots = 1;
    static final String[] typed = new String[8];
    static int tendency = 1, from, sky, colour = 1, shape, size;
    /** Whether the terminal has been switched on yet this session: the first time, the whole boot. */
    static boolean warm;

    private int x0, y0, w, h, sx, sy, sw, sh;
    private int age;
    private final int boot;

    public TerminalScreen() {
        super(Component.translatable("desk.fts_lostsignal.term.title"));
        boot = warm ? 8 : 30;
        warm = true;
    }

    /** The server's word: opened, or updated after something sent. */
    public static void state(Net.TermState s) {
        int before = state == null ? 0 : unread(state);
        state = s;
        Minecraft mc = Minecraft.getInstance();
        if (s.open()) mc.setScreen(new TerminalScreen());
        else if (mc.screen instanceof TerminalScreen t) t.rebuildWidgets();
        if (unread(s) > before && !s.open()) beep(1.6f);
    }

    static int unread(Net.TermState s) {
        int n = 0;
        for (Mail m : s.inbox()) if (!m.read) n++;
        return n;
    }

    static void beep(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), pitch, 0.35f));
    }

    static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(),
                1.7f + (float) Math.random() * 0.4f, 0.12f));
    }

    static void send(int action, long id, int... values) {
        Net.CHANNEL.sendToServer(new Net.TermAct(action, id, values));
    }

    // === Layout =================================================================

    @Override
    protected void init() {
        w = Math.min(width - 16, 470);
        h = Math.min(height - 16, 290);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        sx = x0 + 12;
        sy = y0 + 12;
        sw = w - 24;
        sh = h - 30;
        // The page tabs, along the top.
        int x = sx + 4;
        for (int i = 0; i < TABS.length; i++) {
            Component label = tabLabel(i);
            int tw = font.width(label) + 6;
            int index = i;
            addRenderableWidget(new Plain(x, sy + 14, tw, 11, label, () -> {
                tab = index;
                rebuildWidgets();
                if (index == 0 && state != null && state.posted()) markRead();
            }, () -> tab == index));
            x += tw + 3;
        }
        if (state == null || !state.posted()) return;
        int top = sy + 34;
        switch (TABS[tab]) {
            case "weather" -> weather(top);
            case "smoke" -> smoke(top);
            case "record" -> record(top);
            default -> {
            }
        }
    }

    Component tabLabel(int i) {
        MutableComponent c = Component.literal((i + 1) + " ").append(Component.translatable("desk.fts_lostsignal.term.tab." + TABS[i]));
        if (i == 0 && state != null && unread(state) > 0) c.append(" (" + unread(state) + ")");
        return c;
    }

    void weather(int top) {
        int lx = sx + 8, fx = sx + 118, rx = sx + sw / 2 + 8, rfx = sx + sw / 2 + 118;
        field(0, fx, top, 40, 4);
        field(1, rfx, top, 40, 3);
        field(2, fx, top + 15, 48, 4);
        addRenderableWidget(new Choice(rfx, top + 15, 90, Observation.TENDENCIES, "desk.fts_lostsignal.obs.tendency.", () -> tendency, v -> tendency = v));
        field(4, fx, top + 30, 40, 3);
        addRenderableWidget(new Choice(rfx, top + 30, 90, new String[]{"n", "ne", "e", "se", "s", "sw", "w", "nw"}, "radio.fts_lostsignal.point.", () -> from, v -> from = v));
        field(6, fx, top + 45, 40, 3);
        addRenderableWidget(new Choice(rfx, top + 45, 90, Observation.SKIES, "desk.fts_lostsignal.obs.sky.", () -> sky, v -> sky = v));
        addRenderableWidget(new Plain(lx, top + 66, font.width(sendLabel()) + 6, 11, sendLabel(), this::sendWeather, () -> false));
    }

    void smoke(int top) {
        int fx = sx + 118, rfx = sx + sw / 2 + 118;
        field(0, fx, top, 40, 3, true);
        field(1, rfx, top, 48, 4, true);
        addRenderableWidget(new Choice(fx, top + 15, 90, Terminal.COLOURS, "desk.fts_lostsignal.smoke.colour.", () -> colour, v -> colour = v));
        addRenderableWidget(new Choice(rfx, top + 15, 90, Terminal.SHAPES, "desk.fts_lostsignal.smoke.shape.", () -> shape, v -> shape = v));
        addRenderableWidget(new Choice(fx, top + 30, 90, Terminal.SIZES, "desk.fts_lostsignal.smoke.size.", () -> size, v -> size = v));
        addRenderableWidget(new Plain(sx + 8, top + 51, font.width(sendLabel()) + 6, 11, sendLabel(), this::sendSmoke, () -> false));
    }

    void record(int top) {
        if (state.here() >= 0 && state.here() != state.number()) {
            Component move = Component.translatable("desk.fts_lostsignal.term.move", state.here());
            addRenderableWidget(new Plain(sx + 8, sy + sh - 30, font.width(move) + 6, 11, move, () -> send(Terminal.TRANSFER, 0), () -> false));
        }
    }

    Component sendLabel() {
        return Component.translatable("desk.fts_lostsignal.term.send");
    }

    void field(int index, int x, int y, int width, int length) {
        field(index, x, y, width, length, false);
    }

    /** A number typed in: the observation's, or the smoke report's (kept apart, each where it was left). */
    void field(int index, int x, int y, int width, int length, boolean smoke) {
        String[] store = smoke ? SMOKE : typed;
        int at = index;
        EditBox box = new EditBox(font, x + 2, y + 1, width, 10, Component.empty());
        box.setBordered(false);
        box.setTextColor(BRIGHT);
        box.setMaxLength(length);
        box.setFilter(s -> s.matches("-?\\d*"));
        box.setValue(store[at] == null ? "" : store[at]);
        box.setResponder(s -> store[at] = s);
        addRenderableWidget(box);
    }

    static final String[] SMOKE = new String[2];

    // === Sending ================================================================

    static int number(String s) {
        if (s == null || s.isEmpty() || s.equals("-")) return Observation.EMPTY;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return Observation.EMPTY;
        }
    }

    void sendWeather() {
        int[] v = new int[Observation.FIELDS];
        v[Observation.TEMPERATURE] = number(typed[0]);
        v[Observation.HUMIDITY] = number(typed[1]);
        v[Observation.PRESSURE] = number(typed[2]);
        v[Observation.TENDENCY] = tendency;
        v[Observation.WIND] = number(typed[4]);
        v[Observation.FROM] = from;
        v[Observation.RAIN] = number(typed[6]);
        v[Observation.SKY] = sky;
        send(Terminal.OBSERVE, 0, v);
        beep(1.2f);
        tab = 0;
        rebuildWidgets();
    }

    void sendSmoke() {
        int bearing = number(SMOKE[0]), distance = number(SMOKE[1]);
        if (bearing == Observation.EMPTY) return;
        send(Terminal.SMOKE, 0, bearing, distance == Observation.EMPTY ? 0 : distance, colour, shape, size);
        beep(1.2f);
        tab = 0;
        rebuildWidgets();
    }

    // === Keys ===================================================================

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (age < boot && key != GLFW.GLFW_KEY_ESCAPE) return true;
        boolean typing = getFocused() instanceof EditBox e && e.isFocused();
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_6 && (!typing || hasControlDown())) {
            tab = key - GLFW.GLFW_KEY_1;
            click();
            rebuildWidgets();
            if (tab == 0 && state != null && state.posted()) markRead();
            return true;
        }
        if (state != null && state.posted() && !typing) {
            switch (TABS[tab]) {
                case "inbox" -> {
                    if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                        mail = Math.max(0, Math.min(state.inbox().size() - 1, mail + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
                        click();
                        markRead();
                        return true;
                    }
                }
                case "orders" -> {
                    int n = state.catalog().size();
                    if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                        item = Math.max(0, Math.min(n - 1, item + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
                        lots = 1;
                        click();
                        return true;
                    }
                    if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_EQUAL) {
                        lots = Math.max(1, Math.min(8, lots + (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_EQUAL ? 1 : -1)));
                        click();
                        return true;
                    }
                    if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                        send(Terminal.ORDER, 0, item, lots);
                        beep(1.2f);
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && typing) {
            if (TABS[tab].equals("weather")) sendWeather();
            else if (TABS[tab].equals("smoke")) sendSmoke();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        boolean took = super.charTyped(c, modifiers);
        if (took) click();
        return took;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (state == null || !state.posted()) return false;
        int top = sy + 34, row = (int) Math.floor((my - top) / 10);
        if (TABS[tab].equals("inbox") && mx >= sx + 4 && mx < sx + sw * 2 / 5 && row >= 0 && inboxFirst() + row < state.inbox().size()) {
            mail = inboxFirst() + row;
            click();
            markRead();
            return true;
        }
        if (TABS[tab].equals("orders") && mx >= sx + 4 && mx < sx + sw - 4 && row >= 0 && row < state.catalog().size()) {
            if (item == row) lots = Math.min(8, lots + 1);
            else {
                item = row;
                lots = 1;
            }
            click();
            return true;
        }
        return false;
    }

    /** The message chosen, newest first, read now. */
    void markRead() {
        Mail m = chosenMail();
        if (m != null && !m.read) {
            m.read = true;
            send(Terminal.READ, m.id);
        }
    }

    Mail chosenMail() {
        List<Mail> list = state.inbox();
        if (list.isEmpty()) return null;
        int i = Math.max(0, Math.min(list.size() - 1, mail));
        return list.get(list.size() - 1 - i);
    }

    @Override
    public void tick() {
        age++;
        if (age == boot && TABS[tab].equals("inbox")) markRead();
        // The power gone (the generator cold), or the keeper walked away from it: the screen goes dark.
        Minecraft mc = Minecraft.getInstance();
        if (state == null || mc.level == null || mc.player == null || age % 10 != 0) return;
        net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.of(state.pos());
        var block = mc.level.getBlockState(at);
        boolean lit = block.getBlock() instanceof com.jeladastudios.lostsignal.desk.TerminalBlock && block.getValue(com.jeladastudios.lostsignal.desk.TerminalBlock.ON);
        if (!lit || mc.player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(at)) > 6) {
            if (!lit) mc.player.displayClientMessage(Component.translatable("desk.fts_lostsignal.dark"), true);
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // === Drawing ================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        // The case: beige, its screen sunk in it, a maker's line under the glass and a green power light.
        g.fill(x0, y0, x0 + w, y0 + h, BEZEL);
        g.renderOutline(x0, y0, w, h, BEZEL_DARK);
        g.fill(sx - 3, sy - 3, sx + sw + 3, sy + sh + 3, BEZEL_DARK);
        g.fill(sx, sy, sx + sw, sy + sh, BACK);
        g.drawString(font, "LOOKNET 2.1", x0 + 14, y0 + h - 13, 0xFF6B6452, false);
        g.fill(x0 + w - 20, y0 + h - 11, x0 + w - 15, y0 + h - 8, state != null ? 0xFF3CFF64 : 0xFF2A3A2E);
        if (age < boot) bootText(g);
        else page(g, mouseX, mouseY, partialTick);
        glass(g);
    }

    void bootText(GuiGraphics g) {
        String[] lines = {"desk.fts_lostsignal.term.boot.1", "desk.fts_lostsignal.term.boot.2", "desk.fts_lostsignal.term.boot.3", "desk.fts_lostsignal.term.boot.4"};
        int shown = Math.min(lines.length, age * lines.length / Math.max(1, boot - 4) + 1);
        for (int i = 0; i < shown; i++) text(g, Component.translatable(lines[i]), sx + 6, sy + 8 + i * 11, i == 0 ? BRIGHT : GREEN);
        if (age / 4 % 2 == 0) g.fill(sx + 6, sy + 8 + shown * 11, sx + 11, sy + 16 + shown * 11, GREEN);
    }

    void page(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (state == null) return;
        // The top line: who, where, when, and the credit.
        Component left = state.posted() ? Component.translatable("desk.fts_lostsignal.term.head", state.number(), state.name())
                : Component.translatable("desk.fts_lostsignal.term.head_unposted", state.name());
        text(g, left, sx + 6, sy + 3, BRIGHT);
        Component right = Component.translatable("desk.fts_lostsignal.term.clock", state.day() + 1, Observation.clock(state.time()), state.credit());
        text(g, right, sx + sw - 6 - font.width(right), sy + 3, BRIGHT);
        g.fill(sx + 4, sy + 28, sx + sw - 4, sy + 29, DIM);
        for (var r : renderables) r.render(g, mouseX, mouseY, partialTick);
        int top = sy + 34;
        if (!state.posted()) {
            wrap(g, Component.translatable("desk.fts_lostsignal.term.unposted"), sx + 8, top, sw - 16, GREEN);
            return;
        }
        switch (TABS[tab]) {
            case "inbox" -> inbox(g, top);
            case "duties" -> duties(g, top);
            case "weather" -> weatherLabels(g, top);
            case "smoke" -> smokeLabels(g, top);
            case "orders" -> orders(g, top);
            case "record" -> recordText(g, top);
            default -> {
            }
        }
        // What the keys do, at the foot.
        text(g, Component.translatable("desk.fts_lostsignal.term.keys." + TABS[tab]), sx + 6, sy + sh - 12, DIM);
    }

    /** The first message shown in the list: the chosen one kept in view. */
    int inboxFirst() {
        int rows = (sh - 52) / 10;
        int chosen = Math.max(0, Math.min(state.inbox().size() - 1, mail));
        return Math.max(0, chosen - rows + 1);
    }

    void inbox(GuiGraphics g, int top) {
        List<Mail> list = state.inbox();
        if (list.isEmpty()) {
            text(g, Component.translatable("desk.fts_lostsignal.term.no_mail"), sx + 8, top, GREEN);
            return;
        }
        int split = sx + sw * 2 / 5, rows = (sh - 52) / 10;
        int chosen = Math.max(0, Math.min(list.size() - 1, mail));
        int first = inboxFirst();
        for (int i = first; i < list.size() && i < first + rows; i++) {
            Mail m = list.get(list.size() - 1 - i);
            int y = top + (i - first) * 10;
            String head = (m.read ? "  " : "* ") + (m.day + 1) + " " + Observation.clock(m.time) + " ";
            String subject = head + m.subject.text().getString();
            subject = font.plainSubstrByWidth(subject, split - sx - 10);
            if (i == chosen) {
                g.fill(sx + 4, y - 1, split - 4, y + 9, GREEN);
                g.drawString(font, subject, sx + 6, y, BACK, false);
            } else text(g, Component.literal(subject), sx + 6, y, m.read ? DIM : GREEN);
        }
        g.fill(split, top - 2, split + 1, sy + sh - 16, DIM);
        Mail m = list.get(list.size() - 1 - chosen);
        int x = split + 6, width = sx + sw - 6 - x, y = top;
        y = wrap(g, m.subject.text(), x, y, width, BRIGHT);
        Component who = m.from.isEmpty() ? Component.literal("") : Component.translatable(m.from);
        text(g, Component.translatable("desk.fts_lostsignal.term.from", who, m.day + 1, Observation.clock(m.time)), x, y, DIM);
        y += 13;
        for (Mail.Line line : m.body) {
            if (y > sy + sh - 26) break;
            y = wrap(g, line.text(), x, y, width, GREEN) + 3;
        }
    }

    void duties(GuiGraphics g, int top) {
        String phase = new String[]{"opening", "peak", "closing", "closed"}[Math.max(0, Math.min(3, state.phase()))];
        Component season = state.daysLeft() >= 0
                ? Component.translatable("desk.fts_lostsignal.term.season_days", state.season(), Component.translatable("desk.fts_lostsignal.phase." + phase), state.daysLeft())
                : Component.translatable("desk.fts_lostsignal.term.season", state.season(), Component.translatable("desk.fts_lostsignal.phase." + phase));
        text(g, season, sx + 8, top, BRIGHT);
        int y = top + 16;
        var duties = com.jeladastudios.lostsignal.desk.Duty.values();
        for (var d : duties) {
            String status;
            if ((state.applies() & d.bit()) == 0) status = "none";
            else if ((state.done() & d.bit()) != 0) status = "done";
            else if ((state.missed() & d.bit()) != 0) status = "missed";
            else if (state.phase() == 3) status = "closed";
            else if (state.time() < d.opens) status = "later";
            else if (d.open(state.time())) status = (state.present() & d.bit()) != 0 ? "open" : "later";
            else status = "away";
            text(g, Component.literal(d.clock), sx + 8, y, GREEN);
            text(g, Component.translatable("desk.fts_lostsignal.duty." + d.name().toLowerCase(java.util.Locale.ROOT)), sx + 44, y, GREEN);
            Component st = Component.translatable("desk.fts_lostsignal.term.status." + status);
            int colour = status.equals("done") ? BRIGHT : status.equals("missed") ? 0xFFFFB060 : status.equals("open") ? GREEN : DIM;
            text(g, st, sx + sw - 10 - font.width(st), y, colour);
            y += 12;
        }
        y += 8;
        text(g, Component.translatable("desk.fts_lostsignal.term.week", state.daysOnDuty(), state.dutiesDone(), state.bonus()), sx + 8, y, GREEN);
        if (state.nextPay() >= 0) text(g, Component.translatable("desk.fts_lostsignal.term.next_pay", state.nextPay()), sx + 8, y + 12, DIM);
    }

    void weatherLabels(GuiGraphics g, int top) {
        int lx = sx + 8, rx = sx + sw / 2 + 8, fx = sx + 118, rfx = sx + sw / 2 + 118;
        String[] left = {"temperature", "pressure", "wind", "rain"}, right = {"humidity", "tendency", "from", "sky"};
        int[] leftField = {0, 2, 4, 6};
        for (int i = 0; i < 4; i++) {
            text(g, Component.translatable("desk.fts_lostsignal.obs.label." + left[i]), lx, top + 1 + i * 15, GREEN);
            text(g, Component.translatable("desk.fts_lostsignal.obs.label." + right[i]), rx, top + 1 + i * 15, GREEN);
            brackets(g, fx, top + i * 15, leftField[i] == 2 ? 52 : 44);
        }
        brackets(g, rfx, top, 44);
        Component status = Component.translatable(state.observedRight() ? "desk.fts_lostsignal.term.obs_right"
                : state.observed() ? "desk.fts_lostsignal.term.obs_taken" : "desk.fts_lostsignal.term.obs_none");
        text(g, status, sx + 8, top + 84, state.observedRight() ? BRIGHT : GREEN);
        wrap(g, Component.translatable("desk.fts_lostsignal.term.obs_hint"), sx + 8, top + 98, sw - 16, DIM);
    }

    void smokeLabels(GuiGraphics g, int top) {
        int lx = sx + 8, rx = sx + sw / 2 + 8, fx = sx + 118, rfx = sx + sw / 2 + 118;
        text(g, Component.translatable("desk.fts_lostsignal.smoke.label.bearing"), lx, top + 1, GREEN);
        text(g, Component.translatable("desk.fts_lostsignal.smoke.label.distance"), rx, top + 1, GREEN);
        text(g, Component.translatable("desk.fts_lostsignal.smoke.label.colour"), lx, top + 16, GREEN);
        text(g, Component.translatable("desk.fts_lostsignal.smoke.label.shape"), rx, top + 16, GREEN);
        text(g, Component.translatable("desk.fts_lostsignal.smoke.label.size"), lx, top + 31, GREEN);
        brackets(g, fx, top, 44);
        brackets(g, rfx, top, 52);
        wrap(g, Component.translatable("desk.fts_lostsignal.term.smoke_hint"), sx + 8, top + 70, sw - 16, DIM);
    }

    void orders(GuiGraphics g, int top) {
        List<Net.Line> list = state.catalog();
        int rows = Math.min(list.size(), (sh - 64) / 10);
        int first = Math.max(0, Math.min(item - rows + 1, list.size() - rows));
        for (int i = first; i < first + rows; i++) {
            Net.Line l = list.get(i);
            var it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(l.key()));
            String name = it == null ? l.key() : new ItemStack(it).getHoverName().getString();
            String left = name + " x" + l.a();
            String price = String.valueOf(l.b());
            int y = top + (i - first) * 10;
            if (i == item) {
                g.fill(sx + 4, y - 1, sx + sw - 4, y + 9, GREEN);
                g.drawString(font, left, sx + 8, y, BACK, false);
                g.drawString(font, price, sx + sw - 8 - font.width(price), y, BACK, false);
            } else {
                text(g, Component.literal(left), sx + 8, y, GREEN);
                text(g, Component.literal(price), sx + sw - 8 - font.width(price), y, GREEN);
            }
        }
        if (item < list.size()) {
            int cost = list.get(item).b() * lots;
            Component sum = Component.translatable("desk.fts_lostsignal.term.order_sum", lots, cost, state.credit());
            text(g, sum, sx + 8, sy + sh - 26, cost > state.credit() ? 0xFFFFB060 : BRIGHT);
        }
    }

    void recordText(GuiGraphics g, int top) {
        text(g, Component.translatable("desk.fts_lostsignal.term.posted", state.number(), state.season()), sx + 8, top, BRIGHT);
        text(g, Component.translatable("desk.fts_lostsignal.term.credit", state.credit()), sx + 8, top + 12, GREEN);
        String[] keys = {"duties", "checks", "observations", "good_observations", "fires", "reports", "rescued", "nights"};
        int y = top + 30;
        for (int i = 0; i < keys.length; i++) {
            int value = 0;
            for (Net.Line l : state.stats()) if (l.key().equals(keys[i])) value = l.a();
            int x = i % 2 == 0 ? sx + 8 : sx + sw / 2 + 8;
            Component c = Component.translatable("desk.fts_lostsignal.stat." + keys[i]);
            text(g, c, x, y, GREEN);
            String v = String.valueOf(value);
            text(g, Component.literal(v), x + sw / 2 - 24 - font.width(v), y, BRIGHT);
            if (i % 2 == 1) y += 12;
        }
    }

    /** The glass over it all: scan lines, a vignette darkening the corners, and now and then a flicker. */
    void glass(GuiGraphics g) {
        for (int y = sy; y < sy + sh; y += 2) g.fill(sx, y, sx + sw, y + 1, 0x22000000);
        for (int i = 0; i < 6; i++) {
            int a = (6 - i) * 0x0A;
            int c = a << 24;
            g.fill(sx + i, sy + i, sx + sw - i, sy + i + 1, c);
            g.fill(sx + i, sy + sh - i - 1, sx + sw - i, sy + sh - i, c);
            g.fill(sx + i, sy + i, sx + i + 1, sy + sh - i, c);
            g.fill(sx + sw - i - 1, sy + i, sx + sw - i, sy + sh - i, c);
        }
        if (Math.random() < 0.03) g.fill(sx, sy, sx + sw, sy + sh, 0x0C66FF88);
    }

    void brackets(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y + 10, x + width, y + 11, DIM);
    }

    /** Green words with a faint bloom about them, as phosphor glows. */
    void text(GuiGraphics g, Component c, int x, int y, int colour) {
        int bloom = (colour & 0xFFFFFF) | 0x28000000;
        g.drawString(font, c, x + 1, y, bloom, false);
        g.drawString(font, c, x, y + 1, bloom, false);
        g.drawString(font, c, x, y, colour, false);
    }

    /** Words wrapped to a width; where the next line would start. */
    int wrap(GuiGraphics g, Component c, int x, int y, int width, int colour) {
        for (FormattedCharSequence line : font.split(c, width)) {
            int bloom = (colour & 0xFFFFFF) | 0x28000000;
            g.drawString(font, line, x + 1, y, bloom, false);
            g.drawString(font, line, x, y, colour, false);
            y += 10;
        }
        return y;
    }

    // === Widgets ================================================================

    /** A word on the screen to press: a page's tab, or "send". Lit when chosen or under the pointer. */
    class Plain extends AbstractWidget {
        final Runnable press;
        final java.util.function.BooleanSupplier chosen;

        Plain(int x, int y, int w, int h, Component label, Runnable press, java.util.function.BooleanSupplier chosen) {
            super(x, y, w, h, label);
            this.press = press;
            this.chosen = chosen;
        }

        @Override
        public void onClick(double mx, double my) {
            click();
            press.run();
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers) {
            if (isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER)) {
                click();
                press.run();
                return true;
            }
            return false;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean lit = chosen.getAsBoolean() || isHoveredOrFocused();
            if (lit) {
                g.fill(getX(), getY() - 1, getX() + width, getY() + height - 1, GREEN);
                g.drawString(font, getMessage(), getX() + 3, getY() + 1, BACK, false);
            } else {
                g.renderOutline(getX(), getY() - 1, width, height, DIM);
                text(g, getMessage(), getX() + 3, getY() + 1, GREEN);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** One of a few choices, turned with a click or the arrow keys: < STEADY >. */
    class Choice extends AbstractWidget {
        final String[] keys;
        final String prefix;
        final java.util.function.IntSupplier get;
        final java.util.function.IntConsumer set;

        Choice(int x, int y, int w, String[] keys, String prefix, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
            super(x, y, w, 11, Component.empty());
            this.keys = keys;
            this.prefix = prefix;
            this.get = get;
            this.set = set;
        }

        void turn(int by) {
            set.accept(Math.floorMod(get.getAsInt() + by, keys.length));
            click();
        }

        @Override
        public void onClick(double mx, double my) {
            turn(mx < getX() + width / 2.0 ? -1 : 1);
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers) {
            if (!isFocused()) return false;
            if (key == GLFW.GLFW_KEY_LEFT) {
                turn(-1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_SPACE) {
                turn(1);
                return true;
            }
            return false;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Component label = Component.literal("< ").append(Component.translatable(prefix + keys[get.getAsInt()])).append(" >");
            int colour = isHoveredOrFocused() ? BRIGHT : GREEN;
            if (isFocused()) g.fill(getX(), getY() + 10, getX() + width, getY() + 11, GREEN);
            text(g, label, getX() + 2, getY() + 1, colour);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
