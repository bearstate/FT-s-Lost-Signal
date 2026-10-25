package com.jeladastudios.lostsignal.desk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * A message on the lookout's terminal: from the District, or from no one at all. Kept as the language files' keys and
 * their arguments, not as words, so each player reads it in their own language; an argument starting with "#" is a
 * key itself.
 */
public final class Mail {

    /** One line of a message: its key and arguments. */
    public record Line(String key, List<String> args) {

        public static Line of(String key, Object... args) {
            List<String> a = new ArrayList<>();
            for (Object o : args) a.add(String.valueOf(o));
            return new Line(key, a);
        }

        public MutableComponent text() {
            Object[] a = new Object[args.size()];
            for (int i = 0; i < a.length; i++) {
                String s = args.get(i);
                a[i] = s.startsWith("#") ? Component.translatable(s.substring(1)) : s;
            }
            return Component.translatable(key, a);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Key", key);
            ListTag list = new ListTag();
            for (String s : args) list.add(StringTag.valueOf(s));
            t.put("Args", list);
            return t;
        }

        static Line load(CompoundTag t) {
            List<String> a = new ArrayList<>();
            for (Tag s : t.getList("Args", Tag.TAG_STRING)) a.add(s.getAsString());
            return new Line(t.getString("Key"), a);
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(key, 128);
            buf.writeVarInt(args.size());
            for (String s : args) buf.writeUtf(s, 256);
        }

        static Line read(FriendlyByteBuf buf) {
            String key = buf.readUtf(128);
            int n = Math.min(buf.readVarInt(), 16);
            List<String> a = new ArrayList<>();
            for (int i = 0; i < n; i++) a.add(buf.readUtf(256));
            return new Line(key, a);
        }
    }

    /** Who it is from, as a key ("" for no one), its subject, its lines, when (game day and time of day), whether read. */
    public final long id;
    public final String from;
    public final Line subject;
    public final List<Line> body;
    public final long day;
    public final int time;
    public boolean read;

    public Mail(long id, String from, Line subject, List<Line> body, long day, int time, boolean read) {
        this.id = id;
        this.from = from;
        this.subject = subject;
        this.body = body;
        this.day = day;
        this.time = time;
        this.read = read;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("Id", id);
        t.putString("From", from);
        t.put("Subject", subject.save());
        ListTag lines = new ListTag();
        for (Line l : body) lines.add(l.save());
        t.put("Body", lines);
        t.putLong("Day", day);
        t.putInt("Time", time);
        t.putBoolean("Read", read);
        return t;
    }

    static Mail load(CompoundTag t) {
        List<Line> body = new ArrayList<>();
        for (Tag l : t.getList("Body", Tag.TAG_COMPOUND)) body.add(Line.load((CompoundTag) l));
        return new Mail(t.getLong("Id"), t.getString("From"), Line.load(t.getCompound("Subject")), body, t.getLong("Day"), t.getInt("Time"), t.getBoolean("Read"));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(id);
        buf.writeUtf(from, 64);
        subject.write(buf);
        buf.writeVarInt(body.size());
        for (Line l : body) l.write(buf);
        buf.writeVarLong(day);
        buf.writeVarInt(time);
        buf.writeBoolean(read);
    }

    public static Mail read(FriendlyByteBuf buf) {
        long id = buf.readLong();
        String from = buf.readUtf(64);
        Line subject = Line.read(buf);
        int n = Math.min(buf.readVarInt(), 32);
        List<Line> body = new ArrayList<>();
        for (int i = 0; i < n; i++) body.add(Line.read(buf));
        return new Mail(id, from, subject, body, buf.readVarLong(), buf.readVarInt(), buf.readBoolean());
    }
}
