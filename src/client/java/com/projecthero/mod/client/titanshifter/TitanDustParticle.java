package com.projecthero.mod.client.titanshifter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.16.7 (user, Titan revamp) -- one pixel of a dissolving Titan, blowing away: a small square in the colour of the
 * texel it came off, carried by the corpse's wind (one steady direction per corpse) with a little lift and gusting,
 * tumbling smaller and fading out. Client-only: created straight into the particle engine, no particle type needed.
 * It is drawn from the block atlas (a plain white concrete square, tinted), so it costs nothing to register.
 */
public class TitanDustParticle extends TextureSheetParticle {
	private static TextureAtlasSprite sprite;
	private final double windX;
	private final double windZ;
	private final float size0;
	private final float phase;
	private final float uo;
	private final float vo;

	public TitanDustParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
			float r, float g, float b, double windX, double windZ, float size, int life) {
		super(level, x, y, z);
		this.setSprite(sprite());
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;
		this.windX = windX;
		this.windZ = windZ;
		this.rCol = r;
		this.gCol = g;
		this.bCol = b;
		this.hasPhysics = false;
		this.gravity = 0.0f;
		this.friction = 0.95f;
		this.lifetime = life;
		this.size0 = size;
		this.quadSize = size;
		this.phase = this.random.nextFloat() * 6.28f;
		this.uo = this.random.nextFloat() * 3.0f;
		this.vo = this.random.nextFloat() * 3.0f;
	}

	static TextureAtlasSprite sprite() {
		if (sprite == null) {
			TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
			sprite = atlas.getSprite(ResourceLocation.withDefaultNamespace("block/white_concrete"));
		}
		return sprite;
	}

	/** Resource reloads rebuild the block atlas. */
	static void resetSprite() {
		sprite = null;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.removed) {
			return;
		}
		float t = (float) this.age / this.lifetime;
		// the wind picks the pixel up and carries it off, gusting a little
		double gust = 0.8 + 0.4 * Math.sin(this.age * 0.21 + phase);
		this.xd += windX * 0.0065 * gust;
		this.zd += windZ * 0.0065 * gust;
		this.yd += 0.0032 + 0.0015 * Math.sin(this.age * 0.33 + phase);
		this.alpha = t < 0.55f ? 1.0f : 1.0f - (t - 0.55f) / 0.45f;
	}

	@Override
	public float getQuadSize(float partialTick) {
		float t = (this.age + partialTick) / this.lifetime;
		return size0 * (1.0f - 0.6f * t);
	}

	// a 4x4 corner of the (plain) concrete sprite: flat colour, like a texel
	@Override
	protected float getU0() {
		return this.sprite.getU((uo + 0.5f) / 16.0f);
	}

	@Override
	protected float getU1() {
		return this.sprite.getU((uo + 1.5f) / 16.0f);
	}

	@Override
	protected float getV0() {
		return this.sprite.getV((vo + 0.5f) / 16.0f);
	}

	@Override
	protected float getV1() {
		return this.sprite.getV((vo + 1.5f) / 16.0f);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.TERRAIN_SHEET;
	}

	@Override
	protected int getLightColor(float partialTick) {
		int base = super.getLightColor(partialTick);
		return Math.max(base & 0xFFFF, 0xA0) | (base & 0xFFFF0000);
	}

	static float clamp01(float v) {
		return Mth.clamp(v, 0.0f, 1.0f);
	}
}
