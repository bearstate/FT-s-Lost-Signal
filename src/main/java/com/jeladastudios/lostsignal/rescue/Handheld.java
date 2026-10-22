package com.jeladastudios.lostsignal.rescue;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A handheld on the rescue frequency: the radio fork's transceiver, switched on, with charge in it, tuned to 145.500,
 * in a hand or on the belt (the hotbar). With one, a keeper away from the station's set still hears the frequency and
 * can answer on it, as far as five watts and the hills let them. A PMR walkie-talkie cannot be tuned there.
 */
public final class Handheld {

    private Handheld() {}

    /** The transceiver a player has on the rescue frequency, or empty. */
    public static ItemStack tuned(Player p) {
        try {
            for (ItemStack s : carried(p)) {
                if (!(s.getItem() instanceof com.codinglitch.simpleradio.core.registry.items.TransceiverItem)
                        || s.getItem() instanceof com.codinglitch.simpleradio.core.registry.items.WalkieTalkieItem) continue;
                var tag = s.getTag();
                if (tag == null || tag.contains("switched_on") && !tag.getBoolean("switched_on")) continue;
                if (tag.contains("battery") && tag.getInt("battery") <= 0) continue;
                String f = com.codinglitch.simpleradio.radio.HamFrequency.normalize(tag.getString("frequency"));
                if (com.jeladastudios.lostsignal.radio.Broadcast.RESCUE.equals(f)) return s;
            }
        } catch (LinkageError e) {
            // A fork without these: no handhelds to go by.
        }
        return ItemStack.EMPTY;
    }

    /** Whether all they have is a PMR walkie-talkie, which cannot be put on the rescue frequency. */
    public static boolean onlyPmr(Player p) {
        try {
            for (ItemStack s : carried(p)) {
                if (s.getItem() instanceof com.codinglitch.simpleradio.core.registry.items.WalkieTalkieItem) return true;
            }
        } catch (LinkageError e) {
            // As above.
        }
        return false;
    }

    static java.util.List<ItemStack> carried(Player p) {
        java.util.List<ItemStack> all = new java.util.ArrayList<>();
        all.add(p.getMainHandItem());
        all.add(p.getOffhandItem());
        for (int i = 0; i < 9; i++) all.add(p.getInventory().getItem(i));
        return all;
    }
}
