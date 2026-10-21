package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.entity.Keeper;
import com.jeladastudios.lostsignal.finder.FireFinder;
import com.jeladastudios.lostsignal.radio.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What a keeper says, face to face or over the radio: their news, what they have seen, what they need, whether one may
 * stay the night. Each answer comes of how things are -- the lookouts round them, the stranger, the weather -- and of
 * who they are: a talker, a keeper of few words, or someone who has not slept.
 */
public final class Talk {

    private Talk() {}

    /** What a keeper may be asked. */
    public enum Topic { NEWS, SEEN, HELP, STAY, BYE }

    /** How recent a meeting with the stranger is still on a keeper's mind: three days. */
    static final long ON_MIND = 72_000;
    /** How much coal a keeper short of it asks for. */
    static final int COAL = 4;

    /** Asked face to face. */
    public static void asked(ServerPlayer p, Keeper k, Topic topic) {
        Tower t = k.record();
        if (t == null || !(p.level() instanceof ServerLevel level)) return;
        String line = answer(level, t, topic, false);
        Voice.say(k, Component.literal(t.keeper), line, 16, t.pitch);
        if (topic == Topic.HELP && t.need == Tower.Need.CHECK) {
            where(level, p, t);
            Towers.of(level).checking.put(p.getUUID(), t.checkOn);
        }
        if (topic == Topic.STAY && "K_STAY_YES".equals(line)) {
            t.trust = Math.max(t.trust, 1);
            t.guest = p.getUUID();
            t.guestNight = level.getDayTime() / 24000;
        }
    }

    /** The line a keeper answers a topic with, as things are. */
    public static String answer(ServerLevel level, Tower t, Topic topic, boolean radio) {
        long now = level.getGameTime();
        boolean night = level.getDayTime() % 24000 > 13000;
        boolean metHim = t.metStranger >= 0 && now - t.metStranger < ON_MIND;
        var random = level.random;
        return switch (topic) {
            case NEWS -> {
                if (night && radio && random.nextBoolean()) yield "K_NIGHT";
                yield switch (t.temper) {
                    case CHATTY -> random.nextBoolean() ? "K_CHATTY" : level.isRaining() ? "K_WEATHER" : "K_QUIET";
                    case TERSE -> radio ? "K_TERSE" : "K_QUIET";
                    case NERVOUS -> metHim ? "K_NERVOUS_02" : "K_NERVOUS_01";
                };
            }
            case SEEN -> {
                if (metHim) yield radio ? "K_SAW_MAN" : "K_MET_HIM";
                for (Tower other : Towers.around(level, t)) if (other.state == Tower.State.STRANGER) yield "K_SAW_LIGHT";
                yield random.nextFloat() < 0.3f ? "K_HEARD_WHISTLE" : "K_QUIET";
            }
            case HELP -> switch (t.need) {
                case COAL -> "K_NEED_COAL";
                case CHECK -> "K_NEED_CHECK";
                case NONE -> "K_QUIET";
            };
            case STAY -> t.trust >= 1 || t.temper == Tower.Temper.CHATTY ? "K_STAY_YES" : "K_STAY_NO";
            case BYE -> "K_BYE";
        };
    }

    /** Which lookout a keeper asks to be looked in on, and where it is from theirs: as a note under their words. */
    public static void where(ServerLevel level, ServerPlayer p, Tower t) {
        Tower other = Towers.known(level, t.checkOn);
        if (other == null) return;
        int dx = other.x - t.x, dz = other.z - t.z;
        int bearing = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz))), 360);
        int far = (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10;
        p.sendSystemMessage(Component.translatable("tower.fts_lostsignal.check_on", other.number, far,
                Component.translatable("radio.fts_lostsignal.point." + FireFinder.point(bearing)), bearing)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** Coal held out to a keeper who asked for it: taken, thanked for, and something given back. */
    public static boolean takeCoal(Keeper k, ServerPlayer p, Tower t, ItemStack held) {
        if (!held.is(ItemTags.COALS) || held.getCount() < COAL && !p.getAbilities().instabuild) return false;
        if (!p.getAbilities().instabuild) held.shrink(COAL);
        t.need = Tower.Need.NONE;
        t.trust++;
        Towers.of((ServerLevel) p.level()).setDirty();
        Voice.say(k, Component.literal(t.keeper), "K_THANKS", 16, t.pitch);
        // What a keeper has to spare: bread, and a flare for the walk home.
        give(p, new ItemStack(Items.BREAD, 3));
        give(p, new ItemStack(Items.FIREWORK_ROCKET).setHoverName(Component.translatable("item.fts_lostsignal.flare")));
        return true;
    }

    static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }
}
