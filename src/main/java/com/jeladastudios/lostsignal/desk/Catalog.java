package com.jeladastudios.lostsignal.desk;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What a keeper can order from the District against their pay credit, from the terminal: sent up with the next
 * morning's crate to the foot of their lookout's trail.
 */
public final class Catalog {

    private Catalog() {}

    /** One line of the list: what, how many to a lot, at what price a lot. */
    public record Entry(Supplier<Item> item, int count, int price) {

        public ItemStack stack() {
            return new ItemStack(item.get(), count);
        }
    }

    /** The radio fork's transceiver, where there is one. */
    static Item transceiver() {
        Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation("simpleradio", "transceiver"));
        return i == null ? Items.AIR : i;
    }

    public static final List<Entry> LIST = List.of(
            new Entry(() -> Items.COAL, 16, 20),
            new Entry(() -> Items.BREAD, 6, 8),
            new Entry(() -> Items.COOKED_BEEF, 6, 15),
            new Entry(() -> Items.BAKED_POTATO, 8, 10),
            new Entry(() -> com.jeladastudios.lostsignal.block.ModBlocks.FLARE.get(), 2, 15),
            new Entry(() -> Items.LANTERN, 1, 10),
            new Entry(() -> Items.CANDLE, 4, 6),
            new Entry(() -> Items.COMPASS, 1, 25),
            new Entry(() -> Items.MAP, 1, 15),
            new Entry(() -> Items.SPYGLASS, 1, 45),
            new Entry(() -> Items.WRITABLE_BOOK, 1, 12),
            new Entry(() -> com.jeladastudios.lostsignal.block.ModBlocks.SIGNAL_LAMP_ITEM.get(), 1, 40),
            new Entry(() -> com.jeladastudios.lostsignal.block.ModBlocks.TERMINAL_ITEM.get(), 1, 120),
            new Entry(Catalog::transceiver, 1, 150));

    /** How many lots of one thing can be ordered at once. */
    static final int MOST = 8;

    /** An order: paid for out of the keeper's credit, sent up with tomorrow's crate, and said on their terminal. */
    public static void order(ServerPlayer p, int index, int lots) {
        ServerLevel level = p.serverLevel();
        Post post = Desk.posted(p);
        if (post == null || index < 0 || index >= LIST.size() || lots < 1 || lots > MOST) return;
        Entry e = LIST.get(index);
        if (e.item().get() == Items.AIR) return;
        int cost = e.price() * lots;
        Desk desk = Desk.of(level);
        if (post.credit < cost) {
            desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("desk.fts_lostsignal.order.short.subject"),
                    List.of(Mail.Line.of("desk.fts_lostsignal.order.short.body", cost, post.credit)));
            return;
        }
        var tower = com.jeladastudios.lostsignal.tower.Towers.known(level, post.tower);
        if (tower == null) return;
        post.credit -= cost;
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < lots; i++) items.add(e.stack());
        long tomorrow = level.getDayTime() / 24000 + 1;
        Desk.send(level, tower, items, tomorrow);
        desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("desk.fts_lostsignal.order.taken.subject"), List.of(
                Mail.Line.of("desk.fts_lostsignal.order.taken.body", lots * e.count(), "#" + e.item().get().getDescriptionId(), cost, post.credit),
                Mail.Line.of("desk.fts_lostsignal.order.taken.when")));
        desk.setDirty();
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("DESK order by {}: {} x{} for {}, {} left", p.getName().getString(),
                e.item().get(), lots, cost, post.credit);
    }
}
