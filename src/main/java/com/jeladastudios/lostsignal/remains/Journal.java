package com.jeladastudios.lostsignal.remains;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A hiker's journal, taken from their body: the day they set out, losing the trail, the radio, the night it happened,
 * and the last page. Each page one of a few ways it went, by whose it is, so no two read alike; the night's page from
 * that night (the weather, where they said they were, the last thing the lookout told them, a whistle in the dark).
 * Its words are in the language files, so each reads it in their own.
 */
public final class Journal {

    private Journal() {}

    /** What happened to them, as far as the journal tells it. */
    public static final int UNANSWERED = 0, ANSWERED = 1, KEEPER = 2;

    /** The names hikers go by; one is drawn for each, from its own number. */
    static final String[] NAMES = {"Dana", "Sam", "Alex", "Jordan", "Casey", "Robin", "Morgan", "Jamie", "Riley", "Avery",
            "Quinn", "Taylor"};

    /** How many ways each page is written: the first under its own key, the others under the key and a number. */
    static final int HIKER_PAGES = 4, KEEPER_PAGES = 3;

    public static String name(long seed) {
        return NAMES[(int) Math.floorMod(seed, NAMES.length)];
    }

    /**
     * Their last night, as they wrote it: the weather ("clear", "rain", "thunder", "snow"), what they last told the
     * lookout they could see (a line of theirs), the last thing they were told to do (an order), whether they heard
     * the whistling. Kept with what is left of them until the journal is found.
     */
    public record Night(String weather, String seen, String order, boolean whistle) {

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Weather", weather);
            t.putString("Seen", seen == null ? "" : seen);
            t.putString("Order", order == null ? "" : order);
            t.putBoolean("Whistle", whistle);
            return t;
        }

        public static Night load(CompoundTag t) {
            return t.isEmpty() ? null : new Night(t.getString("Weather"), t.getString("Seen"), t.getString("Order"), t.getBoolean("Whistle"));
        }

        Component page() {
            Component sky = Component.translatable("journal.fts_lostsignal.night.weather." + (weather.isEmpty() ? "clear" : weather));
            Component where = seen == null || seen.isEmpty() ? Component.translatable("journal.fts_lostsignal.night.no_place")
                    : Component.translatable("journal.fts_lostsignal.night.place", Component.translatable("radio.fts_lostsignal.line." + seen));
            Component told = order == null || order.isEmpty() ? Component.translatable("journal.fts_lostsignal.night.no_order")
                    : Component.translatable("journal.fts_lostsignal.night.order", Component.translatable("rescue.fts_lostsignal.order." + order));
            Component heard = whistle ? Component.translatable("journal.fts_lostsignal.night.whistle") : Component.empty();
            return Component.translatable("journal.fts_lostsignal.night", sky, where, told, heard);
        }
    }

    public static ItemStack of(String name, int how) {
        return of(name, how, null);
    }

    public static ItemStack of(String name, int how, Night night) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", how == KEEPER ? "Lookout Log" : "Journal");
        tag.putString("author", name);
        tag.putBoolean("resolved", true);
        ListTag pages = new ListTag();
        String[] keys = how == KEEPER ? new String[]{"keeper_start", "keeper_man", "keeper_last"}
                : new String[]{"start", "lost", how == ANSWERED ? "answered" : "unanswered", "night", "last"};
        int ways = how == KEEPER ? KEEPER_PAGES : HIKER_PAGES;
        for (int i = 0; i < keys.length; i++) {
            Component page;
            if (keys[i].equals("night")) {
                if (night == null) continue;
                page = night.page();
            } else {
                // Which way this page went for them: from their name and the page, the same each time it is read.
                int way = Math.floorMod(name.hashCode() * 31 + i * 7, ways);
                page = Component.translatable("journal.fts_lostsignal." + keys[i] + (way == 0 ? "" : "." + way));
            }
            pages.add(StringTag.valueOf(Component.Serializer.toJson(page)));
        }
        tag.put("pages", pages);
        book.setHoverName(Component.translatable("item.fts_lostsignal.journal", name));
        return book;
    }
}
