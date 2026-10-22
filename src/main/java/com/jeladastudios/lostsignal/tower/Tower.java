package com.jeladastudios.lostsignal.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * What is going on at one lookout, as the world goes on: whether anyone keeps it, who, how they are, what they need.
 * Known by its place on the map (x and z: the height the siting gives may be a few blocks off the one built).
 */
public final class Tower {

    /** Who is there. */
    public enum State {
        /** No one this season; its log on the lectern, its stove cold. */
        EMPTY,
        /** A keeper lives in it and answers the radio. */
        KEPT,
        /** Left in a hurry: its door open, its keeper gone, and not of their own will. */
        ABANDONED,
        /** No one is posted there; but someone is in it, and its lamp burns at night. */
        STRANGER
    }

    /** How a keeper is: what they talk about, and how. */
    public enum Temper { CHATTY, TERSE, NERVOUS }

    /** What a keeper wants of a neighbour. */
    public enum Need { NONE, COAL, CHECK }

    public final int x, z;
    /** The foot's height as the siting puts it; the cab is some twenty blocks over it. */
    public int y;
    public State state = State.EMPTY;
    /** When it came to be as it is, in game ticks. */
    public long since;
    /** Its number, as the District calls it: Lookout 17. */
    public int number;

    // The keeper, while it is kept.
    public String keeper = "";
    public Temper temper = Temper.CHATTY;
    /** Their voice's pitch, so the one recorded voice is many people. */
    public float pitch = 1f;
    public int kit;
    /** How far they have come to trust the player who keeps the lookout nearest: help given, talks had. */
    public int trust;
    public Need need = Need.NONE;
    /** The lookout they asked to be looked in on, by its place. */
    public long checkOn;
    /** When they last met the stranger, or saw him, in game ticks; -1 never. */
    public long metStranger = -1;
    /** How many times he has been driven off from it, hurt, and not yet come back for it: he comes sooner, and worse. */
    public int anger;

    /** Whether the world has been made to look as it is: the door left open, the candles. */
    public boolean dressed;
    /** Whether a lookout built before its stair came up outside the cab has had its hatch and lanterns put in. */
    public boolean fitted;
    /** Whether FT's Geology has been asked to keep its earthquakes off it. */
    public boolean sheltered;
    /** The lookouts round it, by their feet, once worked out (see Towers.find); null until then. */
    public java.util.List<BlockPos> near;

    /** Who was let stay the night with its keeper, and which night; and the night they were last told to lock up. */
    public transient java.util.UUID guest;
    public transient long guestNight = -1, nightSaid = -1;

    /** A supply crate the District has sent up, still to be set down at the foot of the trail. */
    public boolean supplyPending;
    /** When the last crate was sent, in game ticks. */
    public long lastSupply;

    /** What was left of its keeper, still to be set out near it when its ground is next loaded. */
    public boolean remainsPending;

    public Tower(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public long key() {
        return key(x, z);
    }

    public static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public BlockPos foot() {
        return new BlockPos(x, y, z);
    }

    /** Somewhere about its cab, for a far light or a voice on the radio. */
    public net.minecraft.world.phys.Vec3 cab() {
        return new net.minecraft.world.phys.Vec3(x + 0.5, y + 22.5, z + 0.5);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("X", x);
        t.putInt("Y", y);
        t.putInt("Z", z);
        t.putString("State", state.name());
        t.putLong("Since", since);
        t.putInt("Number", number);
        t.putString("Keeper", keeper);
        t.putString("Temper", temper.name());
        t.putFloat("Pitch", pitch);
        t.putInt("Kit", kit);
        t.putInt("Trust", trust);
        t.putString("Need", need.name());
        t.putLong("CheckOn", checkOn);
        t.putLong("MetStranger", metStranger);
        t.putInt("Anger", anger);
        t.putBoolean("Dressed", dressed);
        t.putBoolean("Fitted", fitted);
        t.putBoolean("Sheltered", sheltered);
        t.putBoolean("RemainsPending", remainsPending);
        t.putBoolean("SupplyPending", supplyPending);
        t.putLong("LastSupply", lastSupply);
        if (near != null) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (BlockPos p : near) list.add(net.minecraft.nbt.NbtUtils.writeBlockPos(p));
            t.put("Near", list);
        }
        return t;
    }

    static Tower load(CompoundTag t) {
        Tower tower = new Tower(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
        tower.state = parse(State.class, t.getString("State"), State.EMPTY);
        tower.since = t.getLong("Since");
        tower.number = t.getInt("Number");
        tower.keeper = t.getString("Keeper");
        tower.temper = parse(Temper.class, t.getString("Temper"), Temper.CHATTY);
        tower.pitch = t.contains("Pitch") ? t.getFloat("Pitch") : 1f;
        tower.kit = t.getInt("Kit");
        tower.trust = t.getInt("Trust");
        tower.need = parse(Need.class, t.getString("Need"), Need.NONE);
        tower.checkOn = t.getLong("CheckOn");
        tower.metStranger = t.contains("MetStranger") ? t.getLong("MetStranger") : -1;
        tower.anger = t.getInt("Anger");
        tower.dressed = t.getBoolean("Dressed");
        tower.fitted = t.getBoolean("Fitted");
        tower.sheltered = t.getBoolean("Sheltered");
        tower.remainsPending = t.getBoolean("RemainsPending");
        tower.supplyPending = t.getBoolean("SupplyPending");
        tower.lastSupply = t.getLong("LastSupply");
        if (t.contains("Near")) {
            tower.near = new java.util.ArrayList<>();
            for (net.minecraft.nbt.Tag e : t.getList("Near", net.minecraft.nbt.Tag.TAG_COMPOUND)) tower.near.add(net.minecraft.nbt.NbtUtils.readBlockPos((CompoundTag) e));
        }
        return tower;
    }

    static <E extends Enum<E>> E parse(Class<E> type, String name, E otherwise) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return otherwise;
        }
    }
}
