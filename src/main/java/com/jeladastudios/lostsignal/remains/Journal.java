package com.jeladastudios.lostsignal.remains;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A hiker's journal, taken from their body: the day they set out, losing the trail, the radio, and the last page. Its
 * words are in the language files, so each reads it in their own.
 */
public final class Journal {

    private Journal() {}

    /** What happened to them, as far as the journal tells it. */
    public static final int UNANSWERED = 0, ANSWERED = 1, KEEPER = 2;

    /** The names hikers go by; one is drawn for each, from its own number. */
    static final String[] NAMES = {"Dana", "Sam", "Alex", "Jordan", "Casey", "Robin", "Morgan", "Jamie", "Riley", "Avery",
            "Quinn", "Taylor"};

    public static String name(long seed) {
        return NAMES[(int) Math.floorMod(seed, NAMES.length)];
    }

    public static ItemStack of(String name, int how) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", how == KEEPER ? "Lookout Log" : "Journal");
        tag.putString("author", name);
        tag.putBoolean("resolved", true);
        ListTag pages = new ListTag();
        String[] keys = how == KEEPER ? new String[]{"keeper_start", "keeper_man", "keeper_last"}
                : new String[]{"start", "lost", how == ANSWERED ? "answered" : "unanswered", "last"};
        for (String key : keys) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("journal.fts_lostsignal." + key))));
        }
        tag.put("pages", pages);
        book.setHoverName(Component.translatable("item.fts_lostsignal.journal", name));
        return book;
    }
}
