package com.jeladastudios.lostsignal.desk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The lookout guide the District sends every new keeper, and the page someone left folded into an old copy of it. Their
 * pages are keys of the language files, so each reads in the reader's own language. Every rule in either holds in the
 * game as it is written.
 */
public final class Handbook {

    private Handbook() {}

    static final int GUIDE_PAGES = 14, PAGE_PAGES = 2;

    public static ItemStack guide() {
        return book("guide", "Lookout Guide", "Cold Fork Ranger District", GUIDE_PAGES);
    }

    public static ItemStack page() {
        return book("page", "Folded page", "C.", PAGE_PAGES);
    }

    static ItemStack book(String what, String title, String author, int pages) {
        ItemStack s = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag t = s.getOrCreateTag();
        t.putString("title", title);
        t.putString("author", author);
        ListTag list = new ListTag();
        for (int i = 1; i <= pages; i++) {
            list.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("handbook.fts_lostsignal." + what + "." + i))));
        }
        t.put("pages", list);
        t.putBoolean("resolved", true);
        s.setHoverName(Component.translatable("handbook.fts_lostsignal." + what + ".name"));
        return s;
    }
}
