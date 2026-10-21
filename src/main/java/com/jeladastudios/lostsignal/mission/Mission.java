package com.jeladastudios.lostsignal.mission;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import java.util.UUID;

/**
 * A night's rescue at one lookout: a hiker lost somewhere within a few hundred blocks of it, calling on the rescue
 * frequency until the keeper answers, and the end of it -- found, come in, or lost to the night.
 */
public final class Mission {

    public enum Stage { CALLING, IN_CONTACT, FOUND, TAKEN, OVER }

    public final BlockPos tower;
    public UUID hiker;
    public Stage stage = Stage.CALLING;
    public long started;
    /** When the hiker last called out unanswered. */
    public long lastCall;
    /** How the night ended: "found", "came_in", "lost". */
    public String ending = "";
    /** Where the hiker was last seen loaded, to keep their ground loaded from. */
    public BlockPos at;
    /** Whether the keeper ever answered them. */
    public boolean answered;
    /** Whether the small hours have been looked at for what comes for them; when it comes, if it does (-1: not). */
    public boolean rolled;
    public long takeAt = -1;
    /** The chance it was looked at with: when the time comes, it is looked at again as things then stand. */
    public float takeChance;
    /** Whether they were heard to be afraid, before. */
    public boolean warned;
    /** When they were taken, where the scene is, and whether the District has sent the keeper to look. */
    public long takenAt;
    public BlockPos scene;
    public boolean told;
    /** Found, and gone home in the morning. */
    public boolean leaving;

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
        tag.putLong("TakenAt", takenAt);
        if (scene != null) tag.put("Scene", NbtUtils.writeBlockPos(scene));
        tag.putBoolean("Told", told);
        tag.putBoolean("Leaving", leaving);
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
        m.takenAt = tag.getLong("TakenAt");
        if (tag.contains("Scene")) m.scene = NbtUtils.readBlockPos(tag.getCompound("Scene"));
        m.told = tag.getBoolean("Told");
        m.leaving = tag.getBoolean("Leaving");
        return m;
    }
}
