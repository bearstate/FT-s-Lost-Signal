package com.jeladastudios.lostsignal.net;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.rescue.Order;
import com.jeladastudios.lostsignal.rescue.Rescue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** The mod's own messages between client and server: the keeper's words from the rescue panel. */
public final class Net {

    private Net() {}

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LostSignal.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, Say.class, Say::write, Say::read, Say::handle);
        CHANNEL.registerMessage(1, Lie.class, Lie::write, Lie::read, Lie::handle);
    }

    /**
     * The player told to lie flat, or to get up: the client keeps its own player's pose, so it is told as the server is.
     */
    public record Lie(boolean down) {

        static void write(Lie m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.down);
        }

        static Lie read(FriendlyByteBuf buf) {
            return new Lie(buf.readBoolean());
        }

        static void handle(Lie m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.jeladastudios.lostsignal.client.ClientHiding.lie(m.down)));
            c.setPacketHandled(true);
        }
    }

    /** The keeper says something from the panel: an order, and its number where it takes one (a bearing). */
    public record Say(Order order, int value) {

        static void write(Say m, FriendlyByteBuf buf) {
            buf.writeEnum(m.order);
            buf.writeVarInt(m.value);
        }

        static Say read(FriendlyByteBuf buf) {
            return new Say(buf.readEnum(Order.class), buf.readVarInt());
        }

        static void handle(Say m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> {
                if (c.getSender() != null) Rescue.said(c.getSender(), m.order, m.value);
            });
            c.setPacketHandled(true);
        }
    }
}
