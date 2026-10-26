package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.finder.FireFinder;
import com.jeladastudios.lostsignal.mission.Lookouts;
import com.jeladastudios.lostsignal.mission.Missions;
import com.jeladastudios.lostsignal.rescue.Lines;
import com.jeladastudios.lostsignal.signal.Signals;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fires in the forest: what the lookouts are for. A lightning strike in a storm (now and then on a dry afternoon
 * too) and a column of smoke stands up somewhere a few hundred blocks off. The keeper who sees it takes its bearing
 * with the fire finder and calls it in; another lookout's keeper crosses it with their own, the District has a fix,
 * and a crew goes out. Called in, it is out within a few minutes; not, it burns itself out in a while.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Fires {

    private Fires() {}

    /** The chance, every half minute in a thunderstorm, of a strike setting a fire within sight of a kept lookout; and on a dry afternoon. */
    static final float STORM = 0.12f, DRY = 0.004f;
    /**
     * How far off a fire starts, in blocks (within the furthest the game sends a particle, with room for the column);
     * how long it burns uncalled, and once a crew is on it, in seconds.
     */
    static final int NEAR = 200, FAR = 450, BURNS = 15 * 60, CREW = 3 * 60;

    /**
     * A fire: where, when it started, whether it has been called in; whether it is one burning in the world; and, for
     * the season's last night's, the columns beside its own and who is told when it is called in.
     */
    static final class Fire {
        final Vec3 at;
        final Signals.Burning smoke;
        final boolean real;
        boolean called;
        final List<Signals.Burning> beside = new java.util.ArrayList<>();
        Runnable onCalled;

        Fire(Vec3 at, Signals.Burning smoke, boolean real) {
            this.at = at;
            this.smoke = smoke;
            this.real = real;
        }
    }

    private static final List<Fire> FIRES = new CopyOnWriteArrayList<>();
    private static boolean starting;

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        for (Fire f : FIRES) {
            if (f.smoke.burning()) continue;
            FIRES.remove(f);
            for (Signals.Burning b : f.beside) b.out();
            // Burnt itself out, never called in (the last night's goes by the last night).
            if (!f.called && !f.real && f.onCalled == null) com.jeladastudios.lostsignal.desk.Costs.burnedOut(level, f.at);
        }
        if (!com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.FIRES)) return;
        scan(level);
        // A real fire burnt itself out: its smoke goes too.
        if (level.getGameTime() % 100 == 31) for (Fire f : FIRES) if (f.real && level.isLoaded(BlockPos.containing(f.at)) && !stillBurning(level, f.at)) f.smoke.out();
        if (level.getGameTime() % 600 != 77 || starting) return;
        long time = level.getDayTime() % 24000;
        float chance = level.isThundering() ? STORM : !level.isRaining() && time > 6000 && time < 11000 ? DRY : 0;
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) chance *= 2;
        chance *= com.jeladastudios.lostsignal.Config.scale(com.jeladastudios.lostsignal.Config.FIRE_CHANCE);
        // The day's fire danger, where the District rates it: a dry afternoon goes by it, a storm a little.
        if (com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.DESK)) {
            var danger = com.jeladastudios.lostsignal.desk.Danger.today(level);
            chance *= level.isThundering() ? (danger.ordinal() >= 2 ? 1.25f : danger.ordinal() == 0 ? 0.6f : 1f) : danger.fires;
        }
        if (chance == 0) return;
        for (ServerPlayer p : level.players()) {
            BlockPos tower = Missions.towerAt(level, p);
            if (tower == null || level.random.nextFloat() >= chance) continue;
            boolean near = false;
            for (Fire f : FIRES) if (f.at.distanceTo(Vec3.atCenterOf(tower)) < FAR * 2) near = true;
            if (!near) start(level, tower);
            return;
        }
    }

    /** A fire started somewhere a few hundred blocks from a lookout: its ground worked out off the server's thread. */
    public static void start(ServerLevel level, BlockPos tower) {
        long seed = level.random.nextLong();
        starting = true;
        Lookouts.offThread(level, () -> spots(level, tower, new java.util.Random(seed)), found -> {
            starting = false;
            if (found == null || found.isEmpty()) return;
            // One the lookout can see the smoke of: what a lookout is there for. None, and no fire this time.
            Vec3 cab = Vec3.atCenterOf(tower.above(22));
            Vec3 at = null;
            for (Vec3 s : found) {
                if (com.jeladastudios.lostsignal.signal.Sightline.clear(level, cab, s.add(0, 20, 0))) {
                    at = s;
                    break;
                }
            }
            if (at == null) return;
            FIRES.add(new Fire(at, Signals.fire(level, at, BURNS), false));
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("FIRE at {}", BlockPos.containing(at).toShortString());
        });
    }

    /** The season's last night's fire, as the lookout it is seen from has it: where it is once it has started, and whether it has been called in. */
    public static final class Big {
        Fire fire;

        public boolean started() {
            return fire != null;
        }

        public boolean called() {
            return fire != null && fire.called;
        }

        public Vec3 at() {
            return fire == null ? null : fire.at;
        }

        /** Its smoke gone, the columns beside it too. */
        public void out() {
            if (fire == null) return;
            fire.smoke.out();
            for (Signals.Burning b : fire.beside) b.out();
        }
    }

    /** How long the last night's fire burns, in seconds: from dusk to past dawn. */
    static final int BIG_BURNS = 13 * 60;

    /**
     * The season's last night's fire: a big one, three columns across, far off in forest the lookout can see, burning
     * until past dawn; called in, whoever is waiting on it is told.
     */
    public static Big big(ServerLevel level, BlockPos tower, Runnable onCalled) {
        Big big = new Big();
        long seed = level.random.nextLong();
        Lookouts.offThread(level, () -> spots(level, tower, new java.util.Random(seed)), found -> {
            if (found == null || found.isEmpty()) return;
            Vec3 cab = Vec3.atCenterOf(tower.above(22));
            // The furthest it can be seen from the cab: a long way from anyone, it seems.
            Vec3 at = null;
            for (Vec3 s : found) {
                if (com.jeladastudios.lostsignal.signal.Sightline.clear(level, cab, s.add(0, 20, 0)) && (at == null || s.distanceTo(cab) > at.distanceTo(cab))) at = s;
            }
            if (at == null) at = found.get(0);
            Fire f = new Fire(at, Signals.fire(level, at, BIG_BURNS), false);
            for (int[] o : new int[][]{{16, 7}, {-11, 14}}) {
                int x = (int) Math.floor(at.x) + o[0], z = (int) Math.floor(at.z) + o[1];
                Integer y = ground(level, x, z);
                f.beside.add(Signals.fire(level, new Vec3(x + 0.5, y == null ? at.y : y, z + 0.5), BIG_BURNS));
            }
            f.onCalled = onCalled;
            big.fire = f;
            FIRES.add(f);
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("FIRE the last night's at {}", BlockPos.containing(at).toShortString());
        });
        return big;
    }

    /**
     * Where a fire may start: a few hundred blocks from the lookout, in forest (a meadow or bare rock does not go up
     * like a stand of pines), on ground well over the sea and any lake. Empty where none is found.
     */
    static List<Vec3> spots(ServerLevel level, BlockPos tower, java.util.Random random) {
        List<Vec3> found = new java.util.ArrayList<>();
        int sea = level.getSeaLevel();
        for (int tries = 0; tries < 40; tries++) {
            double a = random.nextDouble() * Math.PI * 2, d = NEAR + random.nextDouble() * (FAR - NEAR);
            int x = (int) Math.floor(tower.getX() + Math.cos(a) * d), z = (int) Math.floor(tower.getZ() + Math.sin(a) * d);
            Integer y = ground(level, x, z);
            if (y == null || y < sea + 4 || !forested(level, x, y, z)) continue;
            found.add(new Vec3(x + 0.5, y, z + 0.5));
        }
        return found;
    }

    /** Whether the country at a place is forest, by its biome as the world is made (asked off the server's thread). */
    static boolean forested(ServerLevel level, int x, int y, int z) {
        var source = level.getChunkSource();
        var biome = source.getGenerator().getBiomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x),
                net.minecraft.core.QuartPos.fromBlock(y), net.minecraft.core.QuartPos.fromBlock(z), source.randomState().sampler());
        if (biome.is(net.minecraft.tags.BiomeTags.IS_FOREST) || biome.is(net.minecraft.tags.BiomeTags.IS_TAIGA)
                || biome.is(net.minecraft.tags.BiomeTags.IS_JUNGLE)) return true;
        String name = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        for (String w : new String[]{"forest", "taiga", "wood", "grove", "pine", "conifer", "jungle", "spruce", "fir"}) {
            if (name.contains(w)) return true;
        }
        return false;
    }

    // === Fires burning in the world =============================================

    /** How far round a kept lookout fire is looked for in the loaded ground, in chunks; and how many chunks a tick. */
    static final int SCANS = 16, SCANNED = 6;
    /** How long a real fire's smoke stands after it is found, at most, in seconds: as long as it burns, really. */
    static final int REAL_BURNS = 60 * 60;
    /** The chunks round a lookout, nearest first, and where each player's look has got to among them. */
    private static final List<int[]> RING = new java.util.ArrayList<>();
    private static final java.util.Map<java.util.UUID, Integer> CURSOR = new java.util.HashMap<>();

    static {
        for (int dx = -SCANS; dx <= SCANS; dx++) for (int dz = -SCANS; dz <= SCANS; dz++) if (dx * dx + dz * dz <= SCANS * SCANS) RING.add(new int[]{dx, dz});
        RING.sort(java.util.Comparator.comparingInt(c -> c[0] * c[0] + c[1] * c[1]));
    }

    /**
     * Fire burning in the world round a lookout someone is at (lightning, a camper's fire got away, another mod's
     * wildfires): a few chunks looked over a tick, the top of each column, and a fire found made one the lookouts see,
     * read, call in and have put out.
     */
    static void scan(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            BlockPos tower = Missions.towerAt(level, p);
            if (tower == null) continue;
            int cursor = CURSOR.getOrDefault(p.getUUID(), 0);
            int cx0 = tower.getX() >> 4, cz0 = tower.getZ() >> 4;
            for (int n = 0; n < SCANNED; n++, cursor = (cursor + 1) % RING.size()) {
                int[] c = RING.get(cursor);
                var chunk = level.getChunkSource().getChunkNow(cx0 + c[0], cz0 + c[1]);
                if (chunk == null) continue;
                BlockPos found = burning(chunk);
                if (found != null) found(level, found);
            }
            CURSOR.put(p.getUUID(), cursor);
        }
    }

    /** A block on fire at the top of a chunk's columns, or null. */
    static BlockPos burning(net.minecraft.world.level.chunk.LevelChunk chunk) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int y = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                for (int dy = 0; dy >= -1; dy--) {
                    m.set(x0 + x, y + dy, z0 + z);
                    if (chunk.getBlockState(m).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) return m.immutable();
                }
            }
        }
        return null;
    }

    /** A fire found burning: one more for the lookouts, unless it is part of one they already have. */
    static void found(ServerLevel level, BlockPos at) {
        Vec3 v = Vec3.atBottomCenterOf(at);
        for (Fire f : FIRES) if (f.at.distanceTo(v) < 48) return;
        FIRES.add(new Fire(v, Signals.fire(level, v, REAL_BURNS), true));
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("FIRE burning found at {}", at.toShortString());
    }

    /** A real fire's ground looked over: any fire left in it. */
    static boolean stillBurning(ServerLevel level, Vec3 at) {
        BlockPos c = BlockPos.containing(at);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-POUT, -10, -POUT), c.offset(POUT, 14, POUT))) {
            if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) return true;
        }
        return false;
    }

    /** How far round a real fire the crew puts out what burns, in blocks. */
    static final int POUT = 28;

    /** The crew on a real fire: every flame in the ground round it out, and the hiss of it. */
    static void putOut(ServerLevel level, Vec3 at) {
        BlockPos c = BlockPos.containing(at);
        int out = 0;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-POUT, -10, -POUT), c.offset(POUT, 14, POUT))) {
            if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) {
                level.removeBlock(p, false);
                out++;
            }
        }
        if (out > 0) level.playSound(null, c, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 2f, 0.8f);
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("FIRE at {} put out: {} blocks", c.toShortString(), out);
    }

    /** The ground's height at a column, as the world is made: FT's Geology's model where it gives it, else the generator's. */
    public static Integer ground(ServerLevel level, int x, int z) {
        try {
            double h = com.jeladastudios.feelthenature.GeologyBridge.groundHeight(level, x, z);
            if (!Double.isNaN(h)) return (int) Math.round(h);
        } catch (LinkageError e) {
            // Not there: the generator's own.
        }
        var source = level.getChunkSource();
        return source.getGenerator().getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, source.randomState());
    }

    /**
     * A fire called in from the panel, by its bearing off the fire finder: crossed with another keeper's bearing where
     * one is posted near, a crew sent, and it is out in a few minutes. Told to read it first, where there is no reading.
     */
    public static void report(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        FireFinder.Reading r = FireFinder.last(p);
        Fire fire = null;
        if (r != null && r.kind().equals("fire")) {
            for (Fire f : FIRES) if (f.at.distanceTo(r.at()) < 20 && !f.called) fire = f;
        }
        if (fire == null) {
            p.displayClientMessage(Component.translatable("fire.fts_lostsignal.read_first"), true);
            return;
        }
        called(p, fire, com.jeladastudios.lostsignal.desk.Desk.FIRE_PAY, false);
    }

    /** For trying: every fire's smoke gone now, as if each had burnt itself out. */
    public static void burnOutAll() {
        for (Fire f : FIRES) f.smoke.out();
    }

    /** How near a fire's true bearing a reported one must be, in degrees; and its distance, as a share of the true one. */
    static final double REPORT_BEARING = 4, REPORT_DISTANCE = 0.35;
    /** How near both must be for the report to count as an exact one. */
    static final double EXACT_BEARING = 1, EXACT_DISTANCE = 0.15;

    /**
     * A smoke report sent from the terminal: the bearing and distance the keeper read off the fire finder and typed in,
     * from the lookout they are at. Matched to a fire burning on that bearing (and about that far, where a distance is
     * given): called in as from the panel, and earning more, the more so for a report right to a degree. "none" where no
     * fire is there, "called" where it already has been, "ok" or "exact" where it is taken.
     */
    public static String reportFrom(ServerPlayer p, BlockPos foot, int bearing, int distance) {
        Vec3 cab = Vec3.atCenterOf(foot.above(22));
        Fire best = null;
        double bestOff = Double.MAX_VALUE, bestShare = 0;
        boolean already = false;
        for (Fire f : FIRES) {
            Vec3 to = f.at.subtract(cab);
            double flat = Math.hypot(to.x, to.z);
            int truth = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(to.x, -to.z))), 360);
            double off = Math.abs(net.minecraft.util.Mth.wrapDegrees(bearing - truth));
            double share = distance > 0 ? Math.abs(distance - flat) / flat : 0;
            if (off > REPORT_BEARING || share > REPORT_DISTANCE) continue;
            if (f.called) {
                already = true;
                continue;
            }
            if (off < bestOff) {
                best = f;
                bestOff = off;
                bestShare = share;
            }
        }
        if (best == null) return already ? "called" : "none";
        boolean exact = bestOff <= EXACT_BEARING && distance > 0 && bestShare <= EXACT_DISTANCE;
        called(p, best, com.jeladastudios.lostsignal.desk.Desk.REPORT_PAY, exact);
        return exact ? "exact" : "ok";
    }

    /**
     * A fire called in: crossed with another keeper's bearing where one is posted near, a crew sent, out in a few
     * minutes; to the caller's credit and record.
     */
    static void called(ServerPlayer p, Fire fire, int pay, boolean exact) {
        ServerLevel level = p.serverLevel();
        fire.called = true;
        Fire called = fire;
        com.jeladastudios.lostsignal.desk.Marks.award(p, "first_smoke");
        com.jeladastudios.lostsignal.desk.Desk.count(p, "fires");
        com.jeladastudios.lostsignal.desk.Desk.earn(p, "desk.fts_lostsignal.bonus.fire", pay);
        if (exact) com.jeladastudios.lostsignal.desk.Desk.earn(p, "desk.fts_lostsignal.bonus.exact", com.jeladastudios.lostsignal.desk.Desk.PRECISE_PAY);
        Vec3 from = p.position();
        BlockPos foot = Missions.towerAt(level, p);
        Tower home = foot == null ? null : Towers.at(level, foot);
        // Another keeper who can see it, to cross the bearing with.
        Tower crossing = null;
        if (home != null) {
            for (Tower t : Towers.around(level, home)) {
                if (t.state == Tower.State.KEPT && t.cab().distanceTo(called.at) < 3000) {
                    crossing = t;
                    break;
                }
            }
        }
        p.displayClientMessage(Component.translatable("call.fts_lostsignal.calling_district"), true);
        int at = 40;
        Speech.after(level, at, () -> Lines.say(level, from, Lines.Speaker.DISTRICT, "D_ANSWER", 1f));
        at += Speech.length("D_ANSWER");
        if (crossing != null) {
            Tower t = crossing;
            com.jeladastudios.lostsignal.desk.Marks.award(p, "cross_bearing");
            Speech.after(level, at, () -> {
                Lines.sayAs(level, t.cab(), Calls.lookout(t, t.keeper), "K_SMOKE", 1f, t.pitch, Calls.REACH);
                Vec3 to = called.at.subtract(t.cab());
                int bearing = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(to.x, -to.z))), 360);
                p.sendSystemMessage(Component.translatable("fire.fts_lostsignal.crossed", t.number, bearing).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                t.trust++;
                Towers.of(level).setDirty();
            });
            at += Speech.length("K_SMOKE");
        }
        String done = crossing != null ? "D_SMOKE_CROSS" : "D_SMOKE_01";
        Speech.after(level, at, () -> Lines.say(level, from, Lines.Speaker.DISTRICT, done, 1f));
        // The crew sent: on the radio once the District has said so, out for as long as the fire takes.
        Crew.send(level, p, called.at, foot == null ? from : Vec3.atCenterOf(foot.above(22)), at + Speech.length(done) + 40, CREW * 20);
        if (called.onCalled != null) called.onCalled.run();
        // The crew on it: out in a few minutes. And the District remembers good eyes: supplies come up, once it is said.
        Speech.after(level, CREW * 20, () -> {
            called.smoke.out();
            for (Signals.Burning b : called.beside) b.out();
            if (called.real) putOut(level, called.at);
        });
        if (home != null && com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.SUPPLIES)) {
            Tower h = home;
            Speech.after(level, at + Speech.length(done) + 60, () -> {
                h.supplyPending = true;
                Towers.of(level).setDirty();
            });
        }
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("FIRE called in, crossed by {}", crossing == null ? "no one" : crossing.number);
    }

    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        FIRES.clear();
        CURSOR.clear();
        starting = false;
    }
}
