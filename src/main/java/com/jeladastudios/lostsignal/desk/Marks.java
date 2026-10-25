package com.jeladastudios.lostsignal.desk;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The lookout's advancements: a tab of their own, each one given by the game when it happens (they have no trigger of
 * their own to be met; see data/fts_lostsignal/advancements/lookout).
 */
public final class Marks {

    private Marks() {}

    /** An advancement given to a player, if they do not have it already. */
    public static void award(ServerPlayer p, String id) {
        var advancement = p.server.getAdvancements().getAdvancement(new ResourceLocation(LostSignal.MODID, "lookout/" + id));
        if (advancement == null) return;
        var progress = p.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) return;
        for (String criterion : progress.getRemainingCriteria()) p.getAdvancements().award(advancement, criterion);
    }
}
