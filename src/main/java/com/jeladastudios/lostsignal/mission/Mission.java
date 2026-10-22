package com.jeladastudios.lostsignal.mission;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import java.util.UUID;

/**
 * A night's rescue at one lookout: a hiker lost somewhere within a few hundred blocks of it, calling on the rescue
 * frequency until the keeper answers, and the end of it: found, come in, or lost to the night.
 */
public final class Mission {

    /** PENDING: the hiker's ground still being found and loaded; they are not out there yet. */
    public enum Stage { PENDING, CALLING, IN_CONTACT, FOUND, TAKEN, OVER }

    public final BlockPos tower;
    public UUID hiker;
    public Stage stage = Stage.CALLING;
    public long started;
    /** When the hiker last called out unanswered. */
    public long lastCall;
    /** When they may next call out aloud to someone near, and cry out at something on them; not kept. */
    public transient long nextShout, nextCry;
    /** Whether they have told the keeper they are camping, and that their battery is low, this night; not kept. */
    public transient boolean saidCamp, saidBattery;
    /** How the night ended: "found", "came_in", "lost". */
    public String ending = "";
    /** Where the hiker was last seen loaded, to keep their ground loaded from. */
    public BlockPos at;
    /** Whether the keeper ever answered them. */
    public boolean answered;
    /** Whether the small hours have been looked at for what comes for them; when it comes, on the day's clock (-1: not). */
    public boolean rolled;
    public long takeAt = -1;
    /** The chance it was looked at with: when the time comes, it is looked at again as things then stand. */
    public float takeChance;
    /** Whether they were heard to be afraid, before. */
    public boolean warned;
    /** Whether the stranger came to them and offered to walk them down; when; and what the keeper told them (0 nothing yet, 1 stay, 2 go). */
    public boolean offered;
    /** Whether he will come to them first, decided once with the rest. */
    public boolean offerComes;
    /** For trying: the offer made the next moment it may be. */
    public transient boolean offerNow;
    public long offerAt;
    public int offerAnswer;
    /** When they were taken (on the day's clock), where the scene is, and whether the District has sent the keeper to look. */
    public long takenAt;
    public BlockPos scene;
    public boolean told;
    /** Found, and gone home in the morning. */
    public boolean leaving;
    /** What they last said they could see, the last thing they were told to do, whether they heard the whistling. */
    public String lastSeen = "", lastOrder = "";
    public boolean heardWhistle;

    public Mission(BlockPos tower) {
        this.tower = tower;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Tower", NbtUtils.writeBlockPos(tower));
        if (hiker != null) tag.putUUID("Hiker", hiker);
        tag.putString("Stage", stage.name());
        tag.putLong("Started", started);
        tag.putLong("LastCall", lastCall);
        tag.putString("Ending", ending);
        if (at != null) tag.put("At", NbtUtils.writeBlockPos(at));
        tag.putBoolean("Answered", answered);
        tag.putBoolean("Rolled", rolled);
        tag.putLong("TakeAt", takeAt);
        tag.putFloat("TakeChance", takeChance);
        tag.putBoolean("Warned", warned);
        tag.putBoolean("Offered", offered);
        tag.putBoolean("OfferComes", offerComes);
        tag.putLong("OfferAt", offerAt);
        tag.putInt("OfferAnswer", offerAnswer);
        tag.putLong("TakenAt", takenAt);
        if (scene != null) tag.put("Scene", NbtUtils.writeBlockPos(scene));
        tag.putBoolean("Told", told);
        tag.putBoolean("Leaving", leaving);
        tag.putString("LastSeen", lastSeen);
        tag.putString("LastOrder", lastOrder);
        tag.putBoolean("HeardWhistle", heardWhistle);
        return tag;
    }

    static Mission load(CompoundTag tag) {
        Mission m = new Mission(NbtUtils.readBlockPos(tag.getCompound("Tower")));
        if (tag.hasUUID("Hiker")) m.hiker = tag.getUUID("Hiker");
        try {
            m.stage = Stage.valueOf(tag.getString("Stage"));
        } catch (IllegalArgumentException e) {
            m.stage = Stage.OVER;
        }
        m.started = tag.getLong("Started");
        m.lastCall = tag.getLong("LastCall");
        m.ending = tag.getString("Ending");
        if (tag.contains("At")) m.at = NbtUtils.readBlockPos(tag.getCompound("At"));
        m.answered = tag.getBoolean("Answered");
        m.rolled = tag.getBoolean("Rolled");
        m.takeAt = tag.contains("TakeAt") ? tag.getLong("TakeAt") : -1;
        m.takeChance = tag.getFloat("TakeChance");
        m.warned = tag.getBoolean("Warned");
        m.offered = tag.getBoolean("Offered");
        m.offerComes = tag.getBoolean("OfferComes");
        m.offerAt = tag.getLong("OfferAt");
        m.offerAnswer = tag.getInt("OfferAnswer");
        m.takenAt = tag.getLong("TakenAt");
        if (tag.contains("Scene")) m.scene = NbtUtils.readBlockPos(tag.getCompound("Scene"));
        m.told = tag.getBoolean("Told");
        m.leaving = tag.getBoolean("Leaving");
        m.lastSeen = tag.getString("LastSeen");
        m.lastOrder = tag.getString("LastOrder");
        m.heardWhistle = tag.getBoolean("HeardWhistle");
        return m;
    }
}
