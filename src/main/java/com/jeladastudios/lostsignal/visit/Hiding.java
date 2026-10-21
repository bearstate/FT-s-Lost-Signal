package com.jeladastudios.lostsignal.visit;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hiding under the bed: crouched at a bed, a right click on it and the player is down on the floor beside it, flat,
 * still, out of sight of the door: the camera at the floorboards. Crouching again, they come out. What comes to the
 * door in the night does not find someone hidden so.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Hiding {

    private Hiding() {}

    private record Hidden(BlockPos bed, Vec3 at, long since) {}

    private static final Map<UUID, Hidden> HIDDEN = new HashMap<>();
    private static final java.util.Set<UUID> CROUCHED = new java.util.HashSet<>();

    public static boolean hidden(ServerPlayer p) {
        return HIDDEN.containsKey(p.getUUID());
    }

    @SubscribeEvent
    public static void click(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        BlockState s = p.level().getBlockState(event.getPos());
        if (!(s.getBlock() instanceof BedBlock)) return;
        // Lying under it, a hand on the bed does nothing: above all, it does not get them into it.
        if (hidden(p)) {
            event.setCanceled(true);
            return;
        }
        if (!p.isShiftKeyDown()) return;
        event.setCanceled(true);
        hide(p, event.getPos(), s);
    }

    /** Down on the floor beside the bed, on the side away from the door if it can be told. */
    public static void hide(ServerPlayer p, BlockPos bed, BlockState s) {
        Direction along = s.getValue(BedBlock.FACING);
        BlockPos spot = null;
        double floor = 0;
        // Beside either half of it.
        BlockPos other = bed.relative(BedBlock.getConnectedDirection(s));
        for (BlockPos half : new BlockPos[]{bed, other}) {
            for (Direction side : new Direction[]{along.getClockWise(), along.getCounterClockWise()}) {
                BlockPos q = half.relative(side);
                // Room to lie: nothing there, or only something low on the floor: a rug, a pressure plate.
                var shape = p.level().getBlockState(q).getCollisionShape(p.level(), q);
                if ((shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.1875)
                        && p.level().getBlockState(q.above()).getCollisionShape(p.level(), q.above()).isEmpty()) {
                    spot = q;
                    floor = shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
                    break;
                }
            }
            if (spot != null) break;
        }
        if (spot == null) {
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("HIDE no room by {}", bed.toShortString());
            p.displayClientMessage(Component.translatable("hide.fts_lostsignal.no_room"), true);
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(spot).add(0, floor, 0);
        p.setForcedPose(Pose.SWIMMING);
        p.teleportTo(at.x, at.y, at.z);
        p.setYRot(along.getOpposite().toYRot());
        HIDDEN.put(p.getUUID(), new Hidden(bed, at, p.level().getGameTime()));
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("HIDE in: {} by {}", p.getScoreboardName(), bed.toShortString());
        com.jeladastudios.lostsignal.net.Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new com.jeladastudios.lostsignal.net.Net.Lie(true));
        p.displayClientMessage(Component.translatable("hide.fts_lostsignal.hidden"), true);
    }

    public static void unhide(ServerPlayer p) {
        if (HIDDEN.remove(p.getUUID()) == null) return;
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("HIDE out: {}", p.getScoreboardName());
        p.setForcedPose(null);
        p.displayClientMessage(Component.translatable("hide.fts_lostsignal.out"), true);
        com.jeladastudios.lostsignal.net.Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new com.jeladastudios.lostsignal.net.Net.Lie(false));
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Hidden h = HIDDEN.get(p.getUUID());
        if (h == null) return;
        // Out again on crouching, once they have lain a moment; or if the bed is gone.
        boolean gone = !(p.level().getBlockState(h.bed()).getBlock() instanceof BedBlock);
        // A fresh crouch, not the one they lay down with.
        boolean crouch = p.isShiftKeyDown(), pressed = crouch && !CROUCHED.contains(p.getUUID());
        if (crouch) CROUCHED.add(p.getUUID()); else CROUCHED.remove(p.getUUID());
        if (gone || (pressed && p.level().getGameTime() - h.since() > 20)) {
            unhide(p);
            return;
        }
        // Still: they stay where they lie.
        if (p.position().distanceToSqr(h.at()) > 0.04) p.teleportTo(h.at().x, h.at().y, h.at().z);
        p.setForcedPose(Pose.SWIMMING);
    }

    /** Gone out of the world, or died, or gone through a portal: no longer lying under anything. */
    @SubscribeEvent
    public static void loggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        HIDDEN.remove(event.getEntity().getUUID());
        CROUCHED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void respawned(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) unhide(p);
    }

    @SubscribeEvent
    public static void changedDimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) unhide(p);
    }

    /** A server stopped: nothing of its kept for the next world. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void forget(net.minecraftforge.event.server.ServerStoppedEvent event) {
        HIDDEN.clear();
        CROUCHED.clear();
    }
}
