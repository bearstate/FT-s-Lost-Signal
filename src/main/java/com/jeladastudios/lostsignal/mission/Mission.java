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

    public enum Stage { CALLING, IN_CONTACT, FOUND, OVER }

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
        return m;
    }
}
