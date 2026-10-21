package com.jeladastudios.lostsignal.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A puff of a smoke column seen from a long way off: the big smoke's own sprites, drawn as large as the distance needs
 * for a column to read as one across a valley, grey, rising and spreading slowly as it fades, and through the fog,
 * which at the edge of the view would hide the very thing a lookout is there to see.
 */
public class FarSmoke extends TextureSheetParticle {

    private final SpriteSet sprites;

    FarSmoke(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        setSpriteFromAge(sprites);
        xd = dx;
        yd = dy;
        zd = dz;
        gravity = 0f;
        hasPhysics = false;
        lifetime = 90 + random.nextInt(40);
        rCol = gCol = bCol = shade * 0.6f;
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distance = Math.sqrt(camera.distanceToSqr(x, y, z));
        quadSize = (float) Math.max(1.5, distance / 40.0) * (0.8f + random.nextFloat() * 0.4f);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        float f = (float) age / lifetime;
        alpha = 0.55f * (f < 0.15f ? f / 0.15f : 1f - (f - 0.15f) / 0.85f);
        quadSize *= 1.006f;
        // As light as the sky is: grey by day, dark at night. Lit by itself, not by the ground under it, which far
        // off is not loaded, and would draw it black.
        // The sky's brightness, from a fifth at night to whole by day.
        float day = level.getSkyDarken(1f);
        float grey = shade * (0.2f + 0.8f * day);
        rCol = grey;
        gCol = grey;
        bCol = grey * 0.97f;
    }

    private final float shade = 0.5f + random.nextFloat() * 0.15f;

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return HAZE;
    }

    /** Drawn as smoke is, blended over what is behind it, but not lost in the fog. */
    static final ParticleRenderType HAZE = new ParticleRenderType() {
        private float fogStart;

        @Override
        public void begin(com.mojang.blaze3d.vertex.BufferBuilder builder, net.minecraft.client.renderer.texture.TextureManager textures) {
            com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            fogStart = com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart();
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            builder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(com.mojang.blaze3d.vertex.Tesselator tesselator) {
            tesselator.end();
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(fogStart);
            com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "fts_lostsignal:haze";
        }
    };

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new FarSmoke(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
