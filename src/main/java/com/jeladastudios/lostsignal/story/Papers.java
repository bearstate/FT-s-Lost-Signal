package com.jeladastudios.lostsignal.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The papers the story is found in, as books whose pages are keys of the language files (story.fts_lostsignal.*):
 * Arthur Lund's Hollow Peak log of 1979, the Board of Review's summary of the Hollow Fire, the newspaper clipping of
 * 1980, and Silas's ledger, its last page this world's own. See HIKAYE.md.
 */
public final class Papers {

    private Papers() {}

    /** The pages of each; the log's last one left blank, as Arthur left it. */
    static final int LOG_PAGES = 9, REPORT_PAGES = 5, CLIPPING_PAGES = 3, LEDGER_PAGES = 3;

    public static ItemStack log() {
        return book("log", "Hollow Peak L.O. 1979", "A. Lund", LOG_PAGES);
    }

    public static ItemStack report() {
        return book("report", "Board of Review", "Cold Fork Ranger District", REPORT_PAGES);
    }

    public static ItemStack clipping() {
        return book("clipping", "Clipping", "The Cold Fork Courier", CLIPPING_PAGES);
    }

    /**
     * Silas's ledger: the old years; then this world's, a season to a year, each with the names of those given in it;
     * and this year's last, the fires put out in it besides.
     */
    public static ItemStack ledger(java.util.SortedMap<Integer, List<String>> bySeason, int season, int fires) {
        ItemStack s = book("ledger", "Owed and Given", "S.L.", LEDGER_PAGES);
        ListTag pages = s.getOrCreateTag().getList("pages", net.minecraft.nbt.Tag.TAG_STRING);
        for (var e : bySeason.entrySet()) {
            int ago = season - e.getKey();
            if (ago <= 0) continue;
            year(pages, e.getValue(), names -> ago == 1 ? Component.translatable("story.fts_lostsignal.ledger.last", names)
                    : Component.translatable("story.fts_lostsignal.ledger.ago", ago, names));
        }
        List<String> now = bySeason.getOrDefault(season, List.of());
        if (now.isEmpty()) pages.add(page(Component.translatable("story.fts_lostsignal.ledger.now_owed", fires)));
        else year(pages, now, names -> Component.translatable("story.fts_lostsignal.ledger.now", fires, names));
        s.getOrCreateTag().put("pages", pages);
        return s;
    }

    /** A year's names: nine to its first page, under its heading; twelve to each after. */
    static void year(ListTag pages, List<String> names, java.util.function.Function<String, Component> heading) {
        for (int from = 0; from < names.size(); from += from == 0 ? 9 : 12) {
            String some = String.join("\n", names.subList(from, Math.min(names.size(), from + (from == 0 ? 9 : 12))));
            pages.add(page(from == 0 ? heading.apply(some) : Component.literal(some)));
        }
    }

    static ItemStack book(String what, String title, String author, int pages) {
        ItemStack s = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag t = s.getOrCreateTag();
        t.putString("title", title);
        t.putString("author", author);
        ListTag list = new ListTag();
        for (int i = 1; i <= pages; i++) list.add(page(Component.translatable("story.fts_lostsignal." + what + "." + i)));
        t.put("pages", list);
        t.putBoolean("resolved", true);
        s.setHoverName(Component.translatable("story.fts_lostsignal." + what + ".name"));
        return s;
    }

    static StringTag page(Component c) {
        return StringTag.valueOf(Component.Serializer.toJson(c));
    }
}
