package com.jeladastudios.lostsignal.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A point of light far off: a flare's red burn or a lamp's warm one. Lit by itself, not by where it is (which may be
 * in country the client has not loaded) and larger the further off it is, so it stays a point of light and does not
 * shrink to nothing.
 */
public class SignalParticle extends TextureSheetParticle {

    SignalParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, float size, int life) {
        this(level, x, y, z, dx, dy, dz, sprites, size, life, 36);
    }

    /** With how far off it starts to be drawn larger than it is, so as not to shrink below a point: {@code grows} blocks. */
    SignalParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, float size, int life, double grows) {
        super(level, x, y, z, dx, dy, dz);
        pickSprite(sprites);
        xd = dx;
        yd = dy;
        zd = dz;
        gravity = 0f;
        hasPhysics = false;
        lifetime = life + random.nextInt(4);
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distance = Math.sqrt(camera.distanceToSqr(x, y, z));
        quadSize = size * (float) Math.max(1.0, distance / grows);
    }

    @Override
    public void tick() {
        super.tick();
        float f = (float) age / lifetime;
        alpha = f < 0.7f ? 1f : (1f - f) / 0.3f;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return GLOW;
    }

    /**
     * Drawn as light is: added to what is behind it, and through the fog, which at the edge of the view distance hides
     * everything, and a flare over a far ridge is exactly what should not be hidden.
     */
    static final ParticleRenderType GLOW = new ParticleRenderType() {
        private float fogStart;

        @Override
        public void begin(com.mojang.blaze3d.vertex.BufferBuilder builder, net.minecraft.client.renderer.texture.TextureManager textures) {
            com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            fogStart = com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart();
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            builder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(com.mojang.blaze3d.vertex.Tesselator tesselator) {
            tesselator.end();
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(fogStart);
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "fts_lostsignal:glow";
        }
    };

    public record Flare(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new SignalParticle(level, x, y, z, dx, dy, dz, sprites, 0.6f, 10);
        }
    }

    /** A fire's glow at night under its smoke: broad, dull orange, flickering as it comes and goes. */
    public record Glow(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            SignalParticle p = new SignalParticle(level, x, y, z, dx, dy, dz, sprites, 0.9f, 14, 30);
            p.setColor(1f, 0.45f + level.random.nextFloat() * 0.15f, 0.12f);
            p.setAlpha(0.7f);
            return p;
        }
    }

    public record Lamp(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            // A lamp is a smaller light than a flare, but waved to be seen: kept a few pixels across however far off.
            return new SignalParticle(level, x, y, z, dx, dy, dz, sprites, 0.25f, 9, 22);
        }
    }
}
