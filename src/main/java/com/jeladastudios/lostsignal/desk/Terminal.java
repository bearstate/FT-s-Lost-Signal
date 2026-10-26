package com.jeladastudios.lostsignal.desk;

import com.jeladastudios.lostsignal.mission.Missions;
import com.jeladastudios.lostsignal.net.Net;
import com.jeladastudios.lostsignal.tower.Tower;
import com.jeladastudios.lostsignal.tower.Towers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The terminal's side on the server: what its screen shows (the keeper's post, the day's duties, the week's pay, the
 * messages, the order list, the record) and what is sent from it: a message read, an observation, a smoke report, an
 * order, a posting moved. Only from a terminal the keeper is at, and while it has power.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.jeladastudios.lostsignal.LostSignal.MODID)
public final class Terminal {

    private Terminal() {}

    public static final int READ = 0, OBSERVE = 1, SMOKE = 2, ORDER = 3, TRANSFER = 4, REFRESH = 5, REPLY = 6;
    /** The smoke report's choices, flavour for the District's log. */
    public static final String[] COLOURS = {"white", "grey", "brown", "black"}, SHAPES = {"column", "leaning", "spread"}, SIZES = {"small", "medium", "large"};

    /** The terminal each player last opened. */
    private static final Map<UUID, BlockPos> AT = new HashMap<>();

    /** The terminal opened: the keeper posted there if they are at a lookout and posted nowhere yet, and its screen sent. */
    public static void open(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        AT.put(p.getUUID(), pos.immutable());
        Desk desk = Desk.of(level);
        Post post = desk.post(p);
        if (post.tower == 0 && Season.phase(level) != Season.Phase.CLOSED) {
            BlockPos foot = Missions.towerAt(level, p);
            if (foot != null) desk.postAt(level, p, post, Towers.at(level, foot));
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.4f, 1.9f);
        send(p, true);
    }

    /** Something sent from the terminal. */
    public static void act(ServerPlayer p, int action, long id, int[] v) {
        ServerLevel level = p.serverLevel();
        BlockPos pos = AT.get(p.getUUID());
        if (pos == null || p.position().distanceTo(Vec3.atCenterOf(pos)) > 6 || !(level.getBlockState(pos).getBlock() instanceof TerminalBlock)
                || !TerminalBlock.powered(level, pos)) return;
        Desk desk = Desk.of(level);
        Post post = desk.post(p);
        switch (action) {
            case READ -> {
                for (Mail m : post.inbox) if (m.id == id && !m.read) {
                    m.read = true;
                    desk.setDirty();
                }
            }
            case OBSERVE -> Observation.send(p, pos, v);
            case SMOKE -> smoke(p, post, v);
            case ORDER -> {
                if (v.length >= 2) Catalog.order(p, v[0], v[1]);
            }
            case REPLY -> {
                if (v.length >= 1) Marion.reply(p, id, v[0]);
            }
            case TRANSFER -> {
                BlockPos foot = Missions.towerAt(level, p);
                if (foot != null && Season.phase(level) != Season.Phase.CLOSED) {
                    Tower here = Towers.at(level, foot);
                    if (here.key() != post.tower) desk.postAt(level, p, post, here);
                }
            }
            default -> {
            }
        }
        send(p, false);
    }

    /** A smoke report: bearing, distance, colour, shape, size, from the lookout the terminal is at. */
    static void smoke(ServerPlayer p, Post post, int[] v) {
        ServerLevel level = p.serverLevel();
        if (v.length != 5 || Desk.posted(p) == null) return;
        Desk desk = Desk.of(level);
        BlockPos foot = Missions.towerAt(level, p);
        long time = level.getDayTime() % 24000;
        List<Mail.Line> lines = new ArrayList<>();
        int bearing = Math.floorMod(v[0], 360), distance = Math.max(0, v[1]);
        lines.add(Mail.Line.of("desk.fts_lostsignal.smoke.echo", bearing, distance > 0 ? String.valueOf(distance) : "-",
                "#desk.fts_lostsignal.smoke.colour." + pick(COLOURS, v[2]), "#desk.fts_lostsignal.smoke.shape." + pick(SHAPES, v[3]),
                "#desk.fts_lostsignal.smoke.size." + pick(SIZES, v[4])));
        String result = foot == null ? "away" : com.jeladastudios.lostsignal.tower.Fires.reportFrom(p, foot, bearing, distance);
        lines.add(Mail.Line.of("desk.fts_lostsignal.smoke.result." + result));
        if (result.equals("ok") || result.equals("exact")) post.count("reports");
        desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("desk.fts_lostsignal.smoke.subject", Observation.clock(time)), lines);
    }

    static String pick(String[] choices, int i) {
        return i >= 0 && i < choices.length ? choices[i] : choices[0];
    }

    /** The screen, as it is now, to the player. */
    public static void send(ServerPlayer p, boolean open) {
        ServerLevel level = p.serverLevel();
        Desk desk = Desk.of(level);
        Post post = desk.post(p);
        long day = level.getDayTime() / 24000, time = level.getDayTime() % 24000;
        BlockPos foot = Missions.towerAt(level, p);
        int here = foot == null ? -1 : Towers.at(level, foot).number;
        int applies = 0;
        for (Duty d : Duty.values()) if (Desk.applies(d)) applies |= d.bit();
        int bonus = 0;
        for (Post.Bonus b : post.bonuses) bonus += b.amount();
        int nextPay = post.lastPay < 0 ? -1 : (int) Math.max(0, Desk.PAY_EVERY - (day - post.lastPay));
        List<Net.Line> catalog = new ArrayList<>();
        for (Catalog.Entry e : Catalog.LIST) {
            var key = ForgeRegistries.ITEMS.getKey(e.item().get());
            // The rank it asks for, after the item's name.
            catalog.add(new Net.Line((key == null ? "minecraft:air" : key.toString()) + "|" + e.rank(), e.count(), e.price()));
        }
        List<Net.Line> stats = new ArrayList<>();
        for (var s : post.stats.entrySet()) stats.add(new Net.Line(s.getKey(), s.getValue(), 0));
        stats.add(new Net.Line("rank", post.rank, Desk.service(post)));
        List<Mail> inbox = new ArrayList<>(post.inbox);
        Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new Net.TermState(open, AT.getOrDefault(p.getUUID(), BlockPos.ZERO).asLong(), post.tower != 0, post.number, here,
                post.name, post.credit, desk.season, Season.phase(level).ordinal(), Season.daysLeft(level), desk.rating, day, (int) time, post.present, post.done,
                post.missed, applies, post.observed, post.observedRight, post.daysOnDuty, post.dutiesDone, bonus, nextPay, inbox, stats, catalog));
    }

    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        AT.clear();
    }
}
