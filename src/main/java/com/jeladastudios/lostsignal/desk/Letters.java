package com.jeladastudios.lostsignal.desk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The thanks that come up the mountain: a few days after a hiker is brought in, a letter from them in the crate at the
 * foot of the trail, in their own words, and something with it (cookies, a cake, their compass, a photograph, a cheque
 * for the lookout's fund). The letter is a book, its pages keys of the language files with the names in them.
 */
public final class Letters {

    private Letters() {}

    /** A letter on its way: to whom, from which hiker, the day it comes, how it is written and what comes with it. */
    record Letter(UUID to, String from, long due, int way, int gift) {

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("To", to);
            t.putString("From", from);
            t.putLong("Due", due);
            t.putInt("Way", way);
            t.putInt("Gift", gift);
            return t;
        }

        static Letter load(CompoundTag t) {
            return new Letter(t.getUUID("To"), t.getString("From"), t.getLong("Due"), t.getInt("Way"), t.getInt("Gift"));
        }
    }

    /** How many ways a letter is written, and what can come with one. */
    static final int WAYS = 3;
    static final String[] GIFTS = {"cookies", "cake", "compass", "photo", "cheque", "pup"};
    /** How often what comes is a pup: every lookout should have a dog. */
    static final float PUP = 0.12f;
    /** The cheque, in pay credit. */
    static final int CHEQUE = 40;

    /** A hiker brought in by a player: their letter on its way, two to four days off. */
    public static void owed(ServerPlayer p, String hiker) {
        if (!com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.DESK)) return;
        ServerLevel level = p.serverLevel();
        Desk desk = Desk.of(level);
        long due = level.getDayTime() / 24000 + 2 + level.random.nextInt(3);
        int gift = level.random.nextFloat() < PUP ? GIFTS.length - 1 : level.random.nextInt(GIFTS.length - 1);
        desk.letters.add(new Letter(p.getUUID(), hiker, due, level.random.nextInt(WAYS), gift));
        desk.setDirty();
    }

    /** The letters due by today, sent up to their keepers' lookouts with the next crate; a word on the terminal. */
    static void send(ServerLevel level, Desk desk) {
        long day = level.getDayTime() / 24000;
        for (Letter l : new ArrayList<>(desk.letters)) {
            if (l.due() > day) continue;
            desk.letters.remove(l);
            Post post = desk.posts.get(l.to());
            if (post == null || post.tower == 0) continue;
            var tower = com.jeladastudios.lostsignal.tower.Towers.known(level, post.tower);
            if (tower == null) continue;
            List<ItemStack> items = new ArrayList<>();
            items.add(letter(l, post));
            String gift = GIFTS[l.gift()];
            ItemStack thing = gift(gift, l.from());
            if (thing != null) items.add(thing);
            else if (gift.equals("pup")) tower.pups.add(l.to());
            else post.credit += CHEQUE;
            Desk.send(level, tower, items, day);
            desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("desk.fts_lostsignal.letter.subject", l.from()), List.of(
                    Mail.Line.of("desk.fts_lostsignal.letter.body", l.from()),
                    Mail.Line.of(gift.equals("cheque") ? "desk.fts_lostsignal.letter.cheque" : gift.equals("pup") ? "desk.fts_lostsignal.letter.pup"
                            : "desk.fts_lostsignal.letter.gift", CHEQUE)));
            post.count("letters");
        }
        desk.setDirty();
    }

    /** The letter itself: a book in the hiker's hand, to the keeper by name and lookout. */
    static ItemStack letter(Letter l, Post post) {
        ItemStack s = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag t = s.getOrCreateTag();
        t.putString("title", "Letter");
        t.putString("author", l.from());
        ListTag pages = new ListTag();
        for (int i = 1; i <= 2; i++) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("letter.fts_lostsignal." + (l.way() + 1) + "." + i,
                    l.from(), post.name, String.valueOf(post.number)))));
        }
        t.put("pages", pages);
        t.putBoolean("resolved", true);
        s.setHoverName(Component.translatable("letter.fts_lostsignal.name", l.from()));
        return s;
    }

    /** What comes with it; null for a cheque, which goes to the keeper's credit. */
    static ItemStack gift(String gift, String from) {
        ItemStack s = switch (gift) {
            case "cookies" -> new ItemStack(Items.COOKIE, 12);
            case "cake" -> new ItemStack(Items.CAKE);
            case "compass" -> new ItemStack(Items.COMPASS);
            case "photo" -> new ItemStack(Items.PAINTING);
            // A pup, or a cheque: nothing for the crate.
            default -> null;
        };
        if (s != null) s.setHoverName(Component.translatable("letter.fts_lostsignal.gift." + gift, from));
        return s;
    }
}
