package com.jeladastudios.lostsignal.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The weather as a lookout sends it to the District: read off FT's Geology's weather terminal in the cab (the
 * thermometer, hygrometer, barometer, anemometer and rain gauge it reads), what each instrument there gives; with none
 * reading, what the sky looks like.
 */
public final class WeatherReport {

    private WeatherReport() {}

    /** How far from the station the terminal is looked for: the cab and a little more. */
    static final int LOOKS = 8;

    static Component of(ServerLevel level, BlockPos station) {
        CompoundTag now = reading(level, station);
        List<Component> parts = new ArrayList<>();
        if (now != null) {
            if (now.contains("Temperature")) parts.add(Component.translatable("weather.fts_lostsignal.temperature", whole(now.getFloat("Temperature"))));
            if (now.contains("Humidity")) parts.add(Component.translatable("weather.fts_lostsignal.humidity", whole(now.getFloat("Humidity"))));
            if (now.contains("Pressure")) {
                float tendency = now.getFloat("Tendency");
                String trend = tendency <= -0.5f ? "falling" : tendency >= 0.5f ? "rising" : "steady";
                parts.add(Component.translatable("weather.fts_lostsignal.pressure", whole(now.getFloat("Pressure")),
                        Component.translatable("weather.fts_lostsignal.tendency." + trend)));
            }
            if (now.contains("Wind")) {
                float knots = now.getFloat("Wind");
                parts.add(knots < 1 ? Component.translatable("weather.fts_lostsignal.calm") : Component.translatable("weather.fts_lostsignal.wind", whole(knots),
                        Component.translatable("radio.fts_lostsignal.point." + com.jeladastudios.lostsignal.finder.FireFinder.point(Math.round(now.getFloat("WindFrom"))))));
            }
            if (now.contains("RainToday")) {
                float mm = now.getFloat("RainToday");
                parts.add(mm >= 0.1f ? Component.translatable("weather.fts_lostsignal.rain", String.format(Locale.ROOT, "%.1f", mm))
                        : Component.translatable("weather.fts_lostsignal.dry"));
            }
        }
        if (parts.isEmpty()) {
            parts.add(Component.translatable("weather.fts_lostsignal.sky." + (level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear")));
        }
        MutableComponent all = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) all.append(", ");
            all.append(parts.get(i));
        }
        return all;
    }

    /** What the cab's weather terminal reads now, or null where there is none (or FT's Geology reads differently). */
    public static CompoundTag reading(ServerLevel level, BlockPos station) {
        try {
            for (BlockPos p : BlockPos.betweenClosed(station.offset(-LOOKS, -3, -LOOKS), station.offset(LOOKS, 3, LOOKS))) {
                if (level.getBlockEntity(p) instanceof com.jeladastudios.ftsgeology.blockentity.WeatherTerminalBlockEntity terminal) {
                    return terminal.data(level).getCompound("Now");
                }
            }
        } catch (LinkageError e) {
            // A version of FT's Geology whose terminal is not read this way: the sky, then.
        }
        return null;
    }

    private static String whole(float value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
