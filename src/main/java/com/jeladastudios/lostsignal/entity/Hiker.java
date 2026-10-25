package com.jeladastudios.lostsignal.entity;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A hiker lost on the mountain: a walker of the ordinary kind who left the trail and cannot find it. Walks as a lost
 * person does (on in roughly the way they were going, downhill more often than up, round what is too steep or too
 * deep) tires and rests, and when night comes stops and makes camp with a fire. Told where to go (by the lookout's
 * keeper, over the radio), walks there instead, as far as the ground lets them.
 *
 * <p>Saved with the world: a hiker is someone, and does not vanish when the player looks away.</p>
 */
public class Hiker extends PathfinderMob {

    public enum State { LOST, GOING, RESTING, CAMPING, RESCUED, FOLLOWING }

    private static final EntityDataAccessor<Integer> KIT = SynchedEntityData.defineId(Hiker.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(Hiker.class, EntityDataSerializers.INT);

    /** The kits the textures come in (tools/textures/Hiker.java). */
    public static final int KITS = 4;
    /** How tired walking makes them, a tick, and how rested a tick of rest. */
    static final float TIRES = 1f / (20 * 240), RESTS = 1f / (20 * 40);
    /** Their walking pace, as a share of the attribute's: an unhurried walk over rough ground. */
    static final double PACE = 0.85;

    private float fatigue;
    private double heading = Double.NaN;
    private BlockPos goal;
    private BlockPos fire;

    /** What they were told to follow, leg after leg, until they reach something or give it up. */
    public enum Guide { NONE, WATER, TRAIL, TOWER }

    private Guide guide = Guide.NONE;
    private int legs;
    /** Whether they carry a compass: half of them do. Set once, the first time they are asked. */
    private Boolean compass;
    private BlockPos tower;
    private boolean towerSought;
    /** Being looked up now, off the server's thread. */
    private transient boolean seeking;
    /** Told to stay where they are: they wait, rested or not, until told otherwise. */
    private boolean staying;

    public Hiker(EntityType<? extends Hiker> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setMaxUpStep(1.0f);
        // Through doors, as anyone goes: a lookout's cab is entered by its door.
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) ground.setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(KIT, 0);
        entityData.define(STATE, State.LOST.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // From the night's monsters they run, as anyone would; not on a broken ankle.
        goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(this, net.minecraft.world.entity.monster.Monster.class,
                12f, 0.9, 1.15) {
            @Override
            public boolean canUse() {
                return hurt < 2 && super.canUse();
            }
        });
        goalSelector.addGoal(2, new Walk());
        goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /** Hurt by something: a cry, and the night told of it; and if it was the last of them, the night over. */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hurtNow = super.hurt(source, amount);
        if (hurtNow && isAlive() && level() instanceof net.minecraft.server.level.ServerLevel server
                && source.getEntity() instanceof net.minecraft.world.entity.monster.Monster) {
            com.jeladastudios.lostsignal.mission.Missions.attacked(server, this);
        }
        return hurtNow;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (level() instanceof net.minecraft.server.level.ServerLevel server) com.jeladastudios.lostsignal.mission.Missions.killed(server, this);
    }

    public int kit() {
        return entityData.get(KIT);
    }

    public void kit(int kit) {
        entityData.set(KIT, Math.floorMod(kit, KITS));
    }

    public State state() {
        return State.values()[entityData.get(STATE)];
    }

    void state(State s) {
        if (s == state()) return;
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
            LostSignal.LOGGER.info("HIKER {} {} -> {} at {} {} {} fatigue {}", getId(), state(), s, getBlockX(), getBlockY(), getBlockZ(),
                    String.format(java.util.Locale.ROOT, "%.2f", fatigue));
        }
        if (state() == State.CAMPING && s != State.CAMPING) breakCamp();
        entityData.set(STATE, s.ordinal());
    }

    /** Sent somewhere: walks there, as near as the ground lets, and stops. */
    public void goTo(BlockPos where) {
        staying = false;
        goal = where;
        getNavigation().stop();
        state(State.GOING);
    }

    /** Asked to stay where they are: they rest. */
    public void stay() {
        staying = true;
        guide = Guide.NONE;
        goal = null;
        getNavigation().stop();
        state(State.RESTING);
    }

    public float fatigue() {
        return fatigue;
    }

    // === A night at a lookout ===================================================

    /** Keeps the ground round a hiker on a rescue loaded and alive, wherever the players are: moved with them. */
    public static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> KEPT =
            net.minecraft.server.level.TicketType.create("fts_lostsignal_hiker", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 300);

    private boolean mission;
    private net.minecraft.world.level.ChunkPos keptAt;

    /** Lost tonight near this lookout, its keeper the one they talk to. */
    public void onMission(BlockPos lookout) {
        mission = true;
        tower = lookout;
        towerSought = true;
    }

    /** Found, or come in: they walk to the lookout's foot and wait there for the morning. */
    public void rescued(BlockPos lookout) {
        // Brought in for the first time: to the credit of whoever is at the lookout, or with them.
        if (state() != State.RESCUED && level() instanceof net.minecraft.server.level.ServerLevel server) {
            for (net.minecraft.server.level.ServerPlayer p : server.players()) {
                if (p.isSpectator() || p.distanceTo(this) > 32 && Math.hypot(p.getX() - lookout.getX(), p.getZ() - lookout.getZ()) > 96) continue;
                com.jeladastudios.lostsignal.desk.Marks.award(p, "brought_home");
                com.jeladastudios.lostsignal.desk.Desk.count(p, "rescued");
                com.jeladastudios.lostsignal.desk.Desk.earn(p, "desk.fts_lostsignal.bonus.rescue", com.jeladastudios.lostsignal.desk.Desk.RESCUE_PAY);
            }
        }
        staying = false;
        guide = Guide.NONE;
        goal = lookout;
        leader = null;
        state(State.RESCUED);
    }

    // === Met on the mountain ====================================================

    /** Whom they walk behind, met on the mountain; null while they follow no one. */
    private java.util.UUID leader;

    /** Met by someone who knows the way: they go behind them, close, as far as the lookout. */
    public void follow(net.minecraft.world.entity.player.Player p) {
        staying = false;
        guide = Guide.NONE;
        goal = null;
        leader = p.getUUID();
        getNavigation().stop();
        state(State.FOLLOWING);
    }

    /** Something said aloud, to whoever stands near: heard from them, not over the radio. */
    public void sayAloud(String line) {
        // Face to face they are someone, by name, as the talk with them is headed.
        com.jeladastudios.lostsignal.radio.Voice.say(this, net.minecraft.network.chat.Component.literal(
                com.jeladastudios.lostsignal.remains.Journal.name(getUUID().getLeastSignificantBits())), line, 24, 1f);
    }

    /** Spoken to face to face: the talk opens; and if they were lost, they are found. */
    @Override
    public net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (level().isClientSide()) return net.minecraft.world.InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer p) || leaving >= 0) return net.minecraft.world.InteractionResult.PASS;
        com.jeladastudios.lostsignal.mission.Missions.met((net.minecraft.server.level.ServerLevel) level(), this, p);
        com.jeladastudios.lostsignal.net.Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.jeladastudios.lostsignal.net.Net.Meet(getId(), com.jeladastudios.lostsignal.remains.Journal.name(getUUID().getLeastSignificantBits()),
                        tower != null));
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /** What one may ask of them face to face, as the talk lists it. */
    public enum Bid { FOLLOW, WAIT, HOW, LOOKOUT }

    /** Asked face to face: they do it, and say so aloud. */
    public void bid(net.minecraft.server.level.ServerPlayer p, Bid what) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) return;
        String ok = getRandom().nextBoolean() ? "H_OK_01" : "H_OK_02";
        switch (what) {
            case FOLLOW -> {
                if (hurt >= 2) {
                    sayAloud("H_HURT_02");
                    return;
                }
                follow(p);
                sayAloud(ok);
            }
            case WAIT -> {
                stay();
                sayAloud("H_STAY");
            }
            case HOW -> sayAloud(com.jeladastudios.lostsignal.rescue.Rescue.answer(server, this, com.jeladastudios.lostsignal.rescue.Order.HOW, 0));
            case LOOKOUT -> {
                if (tower == null || hurt >= 2) {
                    sayAloud(hurt >= 2 ? "H_HURT_02" : "H_WHICH_WAY");
                    return;
                }
                rescued(tower);
                sayAloud(ok);
            }
        }
    }

    // === Hurt ===================================================================

    /** How badly hurt: 0 not, 1 a twisted ankle (they walk, slowly), 2 a broken one (they cannot walk). */
    private int hurt;

    /** The chance, each minute walking over steep ground, of a fall; and how often a fall is a bad one. */
    static final float FALLS = 0.04f, BADLY = 0.2f;

    public int hurt() {
        return hurt;
    }

    /**
     * How far from a place across the ground, not counting height: a goal out on unloaded ground has its height from
     * the ground model, which may lie a few blocks off the ground as it is.
     */
    double flatDistanceSqr(BlockPos to) {
        double dx = getX() - (to.getX() + 0.5), dz = getZ() - (to.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    /** Their pace as they are: half, with a twisted ankle. */
    double pace() {
        return (hurt == 1 ? PACE * 0.45 : PACE) * (fatigue > 0.7f ? 0.75 : 1);
    }

    /** Walking over steep ground, now and then a fall: said on the radio to whoever is listening. */
    void mayFall(net.minecraft.server.level.ServerLevel level) {
        if (hurt >= 2 || !mission || tickCount % 1200 != 0 || !getNavigation().isInProgress() || !com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.FALLS)) return;
        if (com.jeladastudios.lostsignal.rescue.Sight.drop(level, blockPosition()) < 3) return;
        if (getRandom().nextFloat() >= FALLS) return;
        hurt = Math.min(2, hurt + (getRandom().nextFloat() < BADLY ? 2 : 1));
        if (hurt >= 2) {
            getNavigation().stop();
            goal = null;
            guide = Guide.NONE;
            state(State.RESTING);
        }
        com.jeladastudios.lostsignal.rescue.Lines.say(level, getEyePosition(), com.jeladastudios.lostsignal.rescue.Lines.Speaker.HIKER,
                hurt >= 2 ? "H_HURT_02" : "H_HURT", 0.15f);
    }

    /** How many legs in a row found no way on toward where they were sent, and whether they have said why. */
    private int stuck;
    private boolean saidStuck;

    /**
     * No way on toward where they were sent, three tries running: they say what is in the way, once for this way: water
     * running across it, or ground too steep to go down.
     */
    void blocked() {
        if (saidStuck || !mission || !(level() instanceof net.minecraft.server.level.ServerLevel server)) return;
        saidStuck = true;
        boolean water = false;
        for (int d = 3; d <= 16 && !water; d++) {
            int x = (int) Math.floor(getX() + Math.cos(heading) * d), z = (int) Math.floor(getZ() + Math.sin(heading) * d);
            BlockPos top = new BlockPos(x, level().getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
            water = !level().getFluidState(top).isEmpty();
        }
        com.jeladastudios.lostsignal.rescue.Lines.say(server, getEyePosition(), com.jeladastudios.lostsignal.rescue.Lines.Speaker.HIKER,
                water ? "H_WATER" : "H_STEEP", 0.15f);
    }

    /** How long they have stood not finding a way after whoever they follow, and when they may next say so; not kept. */
    private int lagging, nextCannot;

    /** Whether there is water on the ground between them and a place. */
    boolean waterToward(net.minecraft.world.phys.Vec3 to) {
        for (int i = 1; i <= 8; i++) {
            double f = i / 9.0;
            int x = (int) Math.floor(Mth.lerp(f, getX(), to.x)), z = (int) Math.floor(Mth.lerp(f, getZ(), to.z));
            BlockPos top = new BlockPos(x, level().getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
            if (!level().getFluidState(top).isEmpty()) return true;
        }
        return false;
    }

    /**
     * At the lookout's foot: up its stair, along the walkway and in at the door, to a spot on the cab's floor. Only a
     * lookout whose stair comes out on the walkway: one with a hatch over an old stairwell they cannot open, and they
     * wait at the foot.
     */
    void indoors() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server) || goal == null) return;
        BlockPos foot = com.jeladastudios.lostsignal.mission.Missions.standing(server, goal);
        if (foot == null || getY() > foot.getY() + 15) return;
        var door = com.jeladastudios.lostsignal.visit.Visit.doorOf(server, foot);
        if (door == null) return;
        Direction out = door.out();
        if (!com.jeladastudios.lostsignal.tower.Stair.walkable(server, foot, out)) return;
        var room = com.jeladastudios.lostsignal.tower.Towers.room(server, foot.above(20));
        if (room.isEmpty()) return;
        lookout = foot;
        way.clear();
        way.addAll(com.jeladastudios.lostsignal.tower.Stair.up(foot, out));
        way.add(room.get(getRandom().nextInt(room.size())));
    }

    /** The places they are walking through, in order, on a lookout's stair; not kept. */
    private final java.util.ArrayDeque<BlockPos> way = new java.util.ArrayDeque<>();
    /** The lookout whose stair they went up, by its foot; not kept. */
    private BlockPos lookout;
    private int wayTries;

    /** A leg of the way: to the next place on it, and on from there; given up if it cannot be walked. */
    void onTheWay(boolean moving) {
        BlockPos next = way.peek();
        if (next == null) return;
        if (distanceToSqr(Vec3.atBottomCenterOf(next)) < 2.5) {
            way.poll();
            wayTries = 0;
            return;
        }
        if (moving) return;
        var path = getNavigation().createPath(next, 0);
        if (path != null && path.canReach()) {
            getNavigation().moveTo(path, pace());
            wayTries = 0;
        } else if (++wayTries > 3) {
            // Something in the way: given up, and tried again from wherever they are, a while later.
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("HIKER {} gave up the way at {} for {}", getId(), blockPosition().toShortString(), next.toShortString());
            way.clear();
            if (leaving < 0) lookout = null;
        }
    }

    /** When they set off home, in game ticks; -1 while they have not. */
    private long leaving = -1;

    /** Morning: they thank the keeper and go, down off the mountain, the way they are sent. */
    public void leave(BlockPos away) {
        goal = away;
        leaving = level().getGameTime();
        state(State.RESCUED);
        // Out of the cab first, the way they came up, if they are up in it.
        way.clear();
        if (lookout != null && getY() > lookout.getY() + 15 && level() instanceof net.minecraft.server.level.ServerLevel server) {
            var door = com.jeladastudios.lostsignal.visit.Visit.doorOf(server, lookout);
            if (door != null) {
                way.add(com.jeladastudios.lostsignal.tower.Stair.outside(lookout, door.out()));
                way.addAll(com.jeladastudios.lostsignal.tower.Stair.down(lookout, door.out()));
            }
        }
    }

    /** When their breath is next heard by someone walking with them, in ticks; not kept. */
    private int nextBreath;

    /** Walking, with someone a few steps off: their breath, heard from where they are, now and then. */
    void breathe(net.minecraft.server.level.ServerLevel level) {
        if (tickCount < nextBreath || tickCount % 20 != 0 || state() != State.GOING) return;
        if (level.getNearestPlayer(this, 10) == null) return;
        nextBreath = tickCount + 20 * (16 + getRandom().nextInt(10));
        com.jeladastudios.lostsignal.radio.Voice.sound(this, "X_BREATH_WALK", 14);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof net.minecraft.server.level.ServerLevel s) {
            mayFall(s);
            breathe(s);
            lamp();
            // Scrapes and bites mend, slowly, while nothing is at them.
            if (tickCount % 200 == 0 && getHealth() < getMaxHealth() && tickCount - getLastHurtByMobTimestamp() > 200) heal(1f);
            // Out on a night that is over (loaded back after it ended) they are not out there any more.
            if (mission && tickCount > 400 && tickCount % 200 == 0 && !com.jeladastudios.lostsignal.mission.Missions.onNight(s, getUUID())) {
                discard();
                return;
            }
        }
        if (mission && level() instanceof net.minecraft.server.level.ServerLevel server && (tickCount % 100 == 0 || keptAt == null
                || !keptAt.equals(chunkPosition()))) {
            var chunks = server.getChunkSource();
            if (keptAt != null && !keptAt.equals(chunkPosition())) chunks.removeRegionTicket(KEPT, keptAt, 3, keptAt);
            keptAt = chunkPosition();
            chunks.addRegionTicket(KEPT, keptAt, 3, keptAt);
        }
    }

    /** Sent on, leg after leg, along what they follow: water downhill, or a trail up to the lookout. */
    public void follow(Guide what, BlockPos first) {
        guide = what;
        legs = 0;
        stuck = 0;
        saidStuck = false;
        goTo(first);
    }

    public boolean hasCompass() {
        if (compass == null) compass = getRandom().nextBoolean();
        return compass;
    }

    /** The lookout nearest where they are lost: the one whose keeper they talk to. Sought once, then kept. */
    public BlockPos tower() {
        if (!towerSought && !seeking && level() instanceof net.minecraft.server.level.ServerLevel server) {
            seeking = true;
            // Looked up off the server's thread: the siting of new ground takes seconds. Not known until then.
            BlockPos at = blockPosition();
            com.jeladastudios.lostsignal.mission.Lookouts.offThread(server, () -> com.jeladastudios.lostsignal.mission.Lookouts.nearOffThread(server, at, 3),
                    found -> {
                        tower = found;
                        towerSought = true;
                        seeking = false;
                    });
        }
        return tower;
    }

    public BlockPos goal() {
        return goal;
    }

    // === Walking ================================================================

    /** The whole of their walking: lost, sent, resting, camping. Holds the movement while it runs. */
    class Walk extends Goal {

        private int pause;

        Walk() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Level level = level();
            boolean night = !level.isDay() && level.dimensionType().hasSkyLight();
            boolean moving = getNavigation().isInProgress();
            // A broken ankle: they stay where they fell, with a fire at night if they can make one.
            if (hurt >= 2) {
                getNavigation().stop();
                if (night && fire == null) makeCamp();
                return;
            }
            fatigue = Mth.clamp(fatigue + (moving ? TIRES : -RESTS), 0f, 1.2f);
            switch (state()) {
                case CAMPING -> {
                    // Told to go somewhere, they go on through the night once rested.
                    if (goal != null && !staying && fatigue <= 0.3f) state(State.GOING);
                    else if (!night) state(staying ? State.RESTING : State.LOST);
                }
                case RESTING -> {
                    if (staying) {
                        if (night && fire == null) makeCamp();
                    } else if (goal != null) {
                        if (fatigue <= 0.3f) state(State.GOING);
                    } else if (night && fire == null) makeCamp();
                    else if (fatigue <= 0.05f && goal == null && !night) state(State.LOST);
                    else if (fatigue <= 0.05f && goal != null) state(State.GOING);
                }
                case GOING -> {
                    if (goal == null) {
                        state(State.LOST);
                    } else if (fatigue >= 1f) {
                        getNavigation().stop();
                        state(State.RESTING);
                    } else if (flatDistanceSqr(goal) < 9) {
                        BlockPos next = guide == Guide.NONE || ++legs > 12 ? null
                                : com.jeladastudios.lostsignal.rescue.Rescue.nextLeg((net.minecraft.server.level.ServerLevel) level, Hiker.this, guide);
                        if (next != null) {
                            goal = next;
                        } else {
                            goal = null;
                            guide = Guide.NONE;
                            getNavigation().stop();
                            state(State.RESTING);
                        }
                    } else if (!moving && --pause <= 0) {
                        if (walkToward(goal)) stuck = 0;
                        else {
                            pause = 40;
                            if (++stuck == 3) blocked();
                        }
                    }
                }
                case LOST -> {
                    if (night) {
                        getNavigation().stop();
                        makeCamp();
                        if (level instanceof net.minecraft.server.level.ServerLevel server) {
                            com.jeladastudios.lostsignal.mission.Missions.camped(server, Hiker.this);
                        }
                    } else if (fatigue >= 1f) {
                        getNavigation().stop();
                        state(State.RESTING);
                    } else if (!moving && --pause <= 0) {
                        // A lost walker stops, looks round, and goes on.
                        pause = 40 + getRandom().nextInt(160);
                        wander();
                    }
                }
                case FOLLOWING -> {
                    var p = leader == null ? null : level.getPlayerByUUID(leader);
                    if (p == null || p.isSpectator() || distanceTo(p) > 64) {
                        // Left behind: they wait where they are, as they were told to before.
                        leader = null;
                        stay();
                    } else if (tower != null && flatDistanceSqr(tower) < 12 * 12) {
                        // The lookout reached: they wait at its foot for the morning.
                        rescued(tower);
                        sayAloud("H_THANKS_02");
                    } else {
                        double d = distanceTo(p);
                        if (d > 3.5) {
                            if (tickCount % (moving ? 10 : 5) == 0) getNavigation().moveTo(p, d > 12 ? pace() * 1.3 : pace() * 1.05);
                            // No way after them on foot: over water, down a drop, up rock. Said aloud, now and then.
                            if (d > 8 && !moving) {
                                if (++lagging >= 100 && tickCount >= nextCannot) {
                                    nextCannot = tickCount + 900;
                                    sayAloud(waterToward(p.position()) ? "H_WATER" : p.getY() < getY() - 2 ? "H_STEEP" : "H_WHICH_WAY");
                                }
                            } else lagging = 0;
                        } else if (moving) getNavigation().stop();
                    }
                }
                case RESCUED -> {
                    // To the lookout, up its stair and in at the door, for the night; in the morning, away down the
                    // mountain.
                    if (!way.isEmpty()) {
                        if (tickCount % 5 == 0) onTheWay(moving);
                    } else if (goal != null && flatDistanceSqr(goal) > 36 && !moving && --pause <= 0) {
                        if (!walkToward(goal)) pause = 40;
                    } else if (leaving < 0 && goal != null && lookout == null && !moving && --pause <= 0) {
                        pause = 100;
                        indoors();
                    }
                    if (leaving >= 0 && level instanceof net.minecraft.server.level.ServerLevel server && tickCount % 40 == 0) {
                        // Gone down out of sight: there, or long enough on the way, with no one near to see them go.
                        boolean there = goal == null || flatDistanceSqr(goal) < 64;
                        boolean overdue = level.getGameTime() - leaving > 20 * 60 * 3;
                        if ((there || overdue) && server.getNearestPlayer(Hiker.this, 40) == null) discard();
                    }
                }
            }
        }
    }

    /**
     * On in roughly the way they were going: of a handful of spots ten to twenty blocks out within sixty degrees of
     * that way, the lowest that a path reaches: the lost go down, towards water and valleys.
     */
    void wander() {
        if (Double.isNaN(heading)) heading = getRandom().nextDouble() * Math.PI * 2;
        Level level = level();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE, bestHeading = heading;
        for (int i = 0; i < 6; i++) {
            double a = heading + Math.toRadians((getRandom().nextDouble() * 2 - 1) * 60);
            double d = 10 + getRandom().nextDouble() * 10;
            int x = (int) Math.floor(getX() + Math.cos(a) * d), z = (int) Math.floor(getZ() + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!level.getFluidState(at.below()).isEmpty() || !level.getFluidState(at).isEmpty()) continue;
            double score = y + getRandom().nextDouble() * 4;
            if (score < bestScore && getNavigation().createPath(at, 1) != null) {
                bestScore = score;
                best = at;
                bestHeading = a;
            }
        }
        if (best == null) {
            // Nowhere on that way: they turn.
            heading += Math.PI * (0.5 + getRandom().nextDouble());
            return;
        }
        heading = bestHeading;
        getNavigation().moveTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, pace());
    }

    /** A leg of the way to somewhere further off than a path is found to: twenty-four blocks along the line, on the ground. */
    boolean walkToward(BlockPos to) {
        Vec3 from = position();
        Vec3 line = Vec3.atBottomCenterOf(to).subtract(from);
        double length = Math.sqrt(line.x * line.x + line.z * line.z);
        double straight = Math.atan2(line.z, line.x);
        heading = straight;
        if (length <= 24) {
            var path = getNavigation().createPath(to, 1);
            if (path != null && getNavigation().moveTo(path, pace())) return true;
        }
        // A leg of the way: straight on where the ground lets them, else round (further to the side, and shorter,
        // each time the way ahead is a drop or a wall) as long as it brings them nearer.
        double far = Math.sqrt(distanceToSqr(Vec3.atBottomCenterOf(to)));
        for (int side : new int[]{0, 25, -25, 50, -50, 80, -80}) {
            for (int reach : new int[]{24, 14, 8}) {
                if (reach > length) continue;
                double a = straight + Math.toRadians(side);
                int x = (int) Math.floor(from.x + Math.cos(a) * reach), z = (int) Math.floor(from.z + Math.sin(a) * reach);
                BlockPos step = new BlockPos(x, level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                var path = getNavigation().createPath(step, 1);
                if (path == null) continue;
                var end = path.getEndNode();
                if (end == null || Math.sqrt(end.asBlockPos().distSqr(to)) > far - 3) continue;
                if (getNavigation().moveTo(path, pace())) return true;
            }
        }
        return false;
    }

    // === Signals ================================================================

    private int flares = 2;

    /** A flare fired up from where they stand, if they have one left. */
    public boolean fireFlare() {
        if (flares <= 0 || !(level() instanceof net.minecraft.server.level.ServerLevel server)) return false;
        flares--;
        com.jeladastudios.lostsignal.signal.Signals.flare(server, position());
        playSound(net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.5f, 0.8f);
        return true;
    }

    /** Their fire made to smoke (green wood on it) for a few minutes; a fire made first where there is none. */
    public void smoke() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) return;
        if (fire == null) {
            makeCamp();
            if (state() == State.CAMPING && level().isDay()) state(State.RESTING);
        }
        if (fire == null) return;
        BlockState s = level().getBlockState(fire);
        if (s.is(Blocks.CAMPFIRE)) level().setBlock(fire, s.setValue(CampfireBlock.SIGNAL_FIRE, true), 3);
        com.jeladastudios.lostsignal.signal.Signals.smoke(server, Vec3.atCenterOf(fire), 180);
    }

    /** Their lamp held up and waved, for half a minute: a lantern in their hand, the arm swinging it over their head. */
    public void waveLamp() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) return;
        com.jeladastudios.lostsignal.signal.Signals.lamp(server, this, LAMP_SECONDS);
        lampUntil = tickCount + LAMP_SECONDS * 20;
        setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LANTERN));
    }

    static final int LAMP_SECONDS = 30;
    /** Until when they wave it, in their ticks; not kept. */
    private int lampUntil = -1;

    /** The lamp in their hand swung, and put away when they are done. */
    void lamp() {
        if (lampUntil < 0) return;
        if (tickCount < lampUntil) {
            if (tickCount % 10 == 0) swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }
        lampUntil = -1;
        if (getMainHandItem().is(net.minecraft.world.item.Items.LANTERN)) setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
    }

    public int flares() {
        return flares;
    }

    // === Camp ===================================================================

    /** A fire on a free spot beside them, for the night; put out and gone when they move on. */
    void makeCamp() {
        state(State.CAMPING);
        if (fire != null) return;
        Level level = level();
        for (BlockPos p : BlockPos.betweenClosed(blockPosition().offset(-2, -1, -2), blockPosition().offset(2, 1, 2))) {
            if (p.closerThan(blockPosition(), 1.5)) continue;
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) continue;
            if (!level.getFluidState(p.below()).isEmpty()) continue;
            level.setBlock(p, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
            fire = p.immutable();
            return;
        }
    }

    /** Their fire, left where it is when they go: it is no longer theirs to put out. */
    public BlockPos leaveCamp() {
        BlockPos was = fire;
        fire = null;
        return was;
    }

    void breakCamp() {
        if (fire == null) return;
        BlockState s = level().getBlockState(fire);
        if (s.is(Blocks.CAMPFIRE)) level().removeBlock(fire, false);
        fire = null;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy()) breakCamp();
        if (keptAt != null && level() instanceof net.minecraft.server.level.ServerLevel server) server.getChunkSource().removeRegionTicket(KEPT, keptAt, 3, keptAt);
        super.remove(reason);
    }

    // === Saving =================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kit", kit());
        tag.putString("State", state().name());
        tag.putFloat("Fatigue", fatigue);
        tag.putInt("Flares", flares);
        tag.putString("Guide", guide.name());
        tag.putInt("Legs", legs);
        if (compass != null) tag.putBoolean("Compass", compass);
        if (tower != null) tag.put("Tower", NbtUtils.writeBlockPos(tower));
        tag.putBoolean("TowerSought", towerSought);
        tag.putBoolean("Staying", staying);
        tag.putBoolean("Mission", mission);
        tag.putLong("Leaving", leaving);
        if (leader != null) tag.putUUID("Leader", leader);
        tag.putInt("Hurt", hurt);
        if (!Double.isNaN(heading)) tag.putDouble("Heading", heading);
        if (goal != null) tag.put("Goal", NbtUtils.writeBlockPos(goal));
        if (fire != null) tag.put("Fire", NbtUtils.writeBlockPos(fire));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        kit(tag.getInt("Kit"));
        try {
            entityData.set(STATE, State.valueOf(tag.getString("State")).ordinal());
        } catch (IllegalArgumentException e) {
            entityData.set(STATE, State.LOST.ordinal());
        }
        fatigue = tag.getFloat("Fatigue");
        flares = tag.contains("Flares") ? tag.getInt("Flares") : 2;
        try {
            guide = Guide.valueOf(tag.getString("Guide"));
        } catch (IllegalArgumentException e) {
            guide = Guide.NONE;
        }
        legs = tag.getInt("Legs");
        compass = tag.contains("Compass") ? tag.getBoolean("Compass") : null;
        tower = tag.contains("Tower") ? NbtUtils.readBlockPos(tag.getCompound("Tower")) : null;
        towerSought = tag.getBoolean("TowerSought");
        staying = tag.getBoolean("Staying");
        mission = tag.getBoolean("Mission");
        leaving = tag.contains("Leaving") ? tag.getLong("Leaving") : -1;
        leader = tag.hasUUID("Leader") ? tag.getUUID("Leader") : null;
        hurt = tag.getInt("Hurt");
        heading = tag.contains("Heading") ? tag.getDouble("Heading") : Double.NaN;
        goal = tag.contains("Goal") ? NbtUtils.readBlockPos(tag.getCompound("Goal")) : null;
        fire = tag.contains("Fire") ? NbtUtils.readBlockPos(tag.getCompound("Fire")) : null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        // A bright jacket seen from a lookout across a valley.
        return d < 192.0 * 192.0;
    }
}
