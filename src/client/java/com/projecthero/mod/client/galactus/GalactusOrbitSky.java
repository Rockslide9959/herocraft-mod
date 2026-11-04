package com.projecthero.mod.client.galactus;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.projecthero.mod.galactus.orbit.GalactusOrbit;

import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.2 Galactus raid v2: the sky of the orbit ({@code projecthero:galactus_orbit}) -- a deep violet-black void with a
 * dense starfield (a static buffer, plus a bright set that twinkles), two drifting nebula bands, a white sun with a wide
 * glow and, filling the view below the arena, <b>the Earth</b>: a lit sphere (day side toward the sun, a soft blue
 * atmosphere rim) with its own slowly turning cloud layer. As World Consumption climbs (Stage 2) a drained, cracked grey
 * Earth fades in over the living one, and a violet "feed" ribbon rises from it. Every texture is generated here at start-up
 * from noise, so there are no image assets to keep in step.
 */
public final class GalactusOrbitSky {
	private static VertexBuffer stars;
	private static VertexBuffer brightStars;
	private static ResourceLocation earthTex, cloudTex, consumedTex, nebulaTex, glowTex;
	/** The direction the sun is in, and the Earth's centre direction (sky space, unit) and size. */
	static final Vector3f SUN = new Vector3f(0.55f, 0.35f, -0.76f).normalize();
	static final Vector3f EARTH = new Vector3f(-0.15f, -0.86f, 0.48f).normalize();
	static final float EARTH_DIST = 100f;
	static final float EARTH_R = 58f;
	/** Set by the raid: 0..1 World Consumption (the grey Earth), and the feed ribbon's strength. */
	public static float consumption;
	public static float feed;

	private GalactusOrbitSky() {
	}

	public static void initialize() {
		DimensionRenderingRegistry.registerDimensionEffects(GalactusOrbit.EFFECTS, new Effects());
		DimensionRenderingRegistry.registerSkyRenderer(GalactusOrbit.DIMENSION, GalactusOrbitSky::render);
		DimensionRenderingRegistry.registerCloudRenderer(GalactusOrbit.DIMENSION, ctx -> {
		});
		DimensionRenderingRegistry.registerWeatherRenderer(GalactusOrbit.DIMENSION, ctx -> {
		});
	}

	/** No clouds, no ground, no sunrise -- the sky draws everything itself. */
	static final class Effects extends DimensionSpecialEffects {
		Effects() {
			super(Float.NaN, false, SkyType.NONE, false, false);
		}

		@Override
		public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
			return new Vec3(0.02, 0.01, 0.04);
		}

		@Override
		public boolean isFoggyAt(int x, int y) {
			return false;
		}

		@Override
		public float[] getSunriseColor(float timeOfDay, float partialTick) {
			return null;
		}
	}

	// ---------------------------------------------------------------- render

	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		ensure(mc);
		Matrix4f view = new Matrix4f(ctx.positionMatrix());
		Matrix4f proj = new Matrix4f(ctx.projectionMatrix());
		float time = (mc.level == null ? 0 : mc.level.getGameTime()) + ctx.tickCounter().getGameTimeDeltaPartialTick(false);

		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();

		// the void's colour wash (a faint violet glow round the horizon)
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
		bb.addVertex(view, 0, -100, 0).setColor(6, 3, 14, 255);
		for (int i = 0; i <= 32; i++) {
			float a = i * Mth.TWO_PI / 32;
			bb.addVertex(view, Mth.cos(a) * 100, 0, Mth.sin(a) * 100).setColor(14, 8, 30, 255);
		}
		draw(bb);
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
		bb.addVertex(view, 0, 100, 0).setColor(2, 1, 6, 255);
		for (int i = 0; i <= 32; i++) {
			float a = -i * Mth.TWO_PI / 32;
			bb.addVertex(view, Mth.cos(a) * 100, 0, Mth.sin(a) * 100).setColor(14, 8, 30, 255);
		}
		draw(bb);

		// nebulae (additive)
		RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, nebulaTex);
		nebulaBand(view, time, 0f, 0.35f, 0xB070FF, 0.55f);
		nebulaBand(view, time, 1.9f, -0.15f, 0x40D8D0, 0.32f);

		// stars
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		stars.bind();
		stars.drawWithShader(view, proj, GameRenderer.getPositionColorShader());
		float tw = 0.65f + 0.35f * Mth.sin(time * 0.07f);
		RenderSystem.setShaderColor(1f, 1f, 1f, tw);
		brightStars.bind();
		brightStars.drawWithShader(view, proj, GameRenderer.getPositionColorShader());
		VertexBuffer.unbind();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

		// the sun and its glow
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, glowTex);
		billboard(view, SUN, 100f, 34f, 255, 236, 200, 200);
		billboard(view, SUN, 100f, 9f, 255, 255, 255, 255);

		// the Earth (opaque blending for the body)
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderTexture(0, earthTex);
		sphere(view, EARTH, EARTH_DIST, EARTH_R, time * 0.00035f, 1f, true);
		if (consumption > 0.01f) {
			RenderSystem.setShaderTexture(0, consumedTex);
			sphere(view, EARTH, EARTH_DIST - 0.05f, EARTH_R + 0.02f, time * 0.00035f, Mth.clamp(consumption, 0f, 1f), true);
		}
		RenderSystem.setShaderTexture(0, cloudTex);
		sphere(view, EARTH, EARTH_DIST - 0.4f, EARTH_R + 0.6f, time * 0.0006f, 0.85f * (1f - consumption * 0.8f), true);
		// the atmosphere rim (additive)
		RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
		RenderSystem.setShaderTexture(0, glowTex);
		float blue = 1f - consumption * 0.7f;
		billboard(view, EARTH, EARTH_DIST + 2f, EARTH_R * 1.32f, (int) (90 + 120 * consumption), (int) (160 * blue), (int) (255 * blue), 130);
		if (feed > 0.01f) {
			feedRibbon(view, time, feed);
		}

		RenderSystem.defaultBlendFunc();
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	private static void draw(BufferBuilder bb) {
		MeshData data = bb.build();
		if (data != null) {
			BufferUploader.drawWithShader(data);
		}
	}

	/** A soft band of nebula across the sky, tilted by {@code tilt}, turning slowly. */
	private static void nebulaBand(Matrix4f view, float time, float phase, float tilt, int rgb, float alpha) {
		int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		int seg = 24;
		float drift = time * 0.00002f;
		for (int i = 0; i < seg; i++) {
			float a0 = phase + drift + i * Mth.TWO_PI / seg, a1 = phase + drift + (i + 1) * Mth.TWO_PI / seg;
			float u0 = i / (float) seg * 3f, u1 = (i + 1) / (float) seg * 3f;
			for (int band = 0; band < 2; band++) {
				float h0 = band == 0 ? -22f : 0f, h1 = band == 0 ? 0f : 22f;
				float v0 = band == 0 ? 0f : 0.5f, v1 = band == 0 ? 0.5f : 1f;
				int al0 = band == 0 ? 0 : (int) (alpha * 255), al1 = band == 0 ? (int) (alpha * 255) : 0;
				vtx(bb, view, a0, h0, tilt).setUv(u0, v0).setColor(r, g, b, al0);
				vtx(bb, view, a1, h0, tilt).setUv(u1, v0).setColor(r, g, b, al0);
				vtx(bb, view, a1, h1, tilt).setUv(u1, v1).setColor(r, g, b, al1);
				vtx(bb, view, a0, h1, tilt).setUv(u0, v1).setColor(r, g, b, al1);
			}
		}
		draw(bb);
	}

	private static com.mojang.blaze3d.vertex.VertexConsumer vtx(BufferBuilder bb, Matrix4f view, float a, float h, float tilt) {
		float x = Mth.cos(a) * 100f, z = Mth.sin(a) * 100f, y = h;
		// tilt the band round the X axis
		float ct = Mth.cos(tilt), st = Mth.sin(tilt);
		float y2 = y * ct - z * st, z2 = y * st + z * ct;
		return bb.addVertex(view, x, y2, z2);
	}

	/** A camera-facing square of {@code size} at {@code dist} along {@code dir}. */
	private static void billboard(Matrix4f view, Vector3f dir, float dist, float size, int r, int g, int b, int a) {
		Vector3f c = new Vector3f(dir).mul(dist);
		Vector3f up = Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
		Vector3f right = new Vector3f(dir).cross(up).normalize().mul(size);
		Vector3f up2 = new Vector3f(right).cross(dir).normalize().mul(size);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		bb.addVertex(view, c.x - right.x - up2.x, c.y - right.y - up2.y, c.z - right.z - up2.z).setUv(0, 0).setColor(r, g, b, a);
		bb.addVertex(view, c.x + right.x - up2.x, c.y + right.y - up2.y, c.z + right.z - up2.z).setUv(1, 0).setColor(r, g, b, a);
		bb.addVertex(view, c.x + right.x + up2.x, c.y + right.y + up2.y, c.z + right.z + up2.z).setUv(1, 1).setColor(r, g, b, a);
		bb.addVertex(view, c.x - right.x + up2.x, c.y - right.y + up2.y, c.z - right.z + up2.z).setUv(0, 1).setColor(r, g, b, a);
		draw(bb);
	}

	/** A lit, textured sphere (lat/long), turned round its own axis by {@code spin} (texture u offset). */
	private static void sphere(Matrix4f view, Vector3f dir, float dist, float radius, float spin, float alpha, boolean lit) {
		Vector3f c = new Vector3f(dir).mul(dist);
		int lon = 40, lat = 20;
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		// the planet's axis is tilted a little toward the viewer
		Vector3f axis = new Vector3f(0.3f, 0.48f, 0.86f).normalize(); // across the view, so the poles sit at the rim
		Vector3f e1 = new Vector3f(axis).cross(new Vector3f(0, 1, 0)).normalize();
		Vector3f e2 = new Vector3f(axis).cross(e1).normalize();
		int al = (int) (Mth.clamp(alpha, 0f, 1f) * 255);
		for (int i = 0; i < lon; i++) {
			for (int j = 0; j < lat; j++) {
				float[][] q = { { i, j }, { i + 1, j }, { i + 1, j + 1 }, { i, j + 1 } };
				for (float[] v : q) {
					float th = v[0] / lon * Mth.TWO_PI;
					float ph = v[1] / lat * Mth.PI;
					float sx = Mth.sin(ph) * Mth.cos(th), sy = Mth.cos(ph), sz = Mth.sin(ph) * Mth.sin(th);
					Vector3f n = new Vector3f(e1).mul(sx).add(new Vector3f(axis).mul(sy)).add(new Vector3f(e2).mul(sz));
					float light = 1f;
					if (lit) {
						float d = n.dot(SUN);
						light = Mth.clamp(0.08f + 1.1f * Mth.clamp(d + 0.15f, 0f, 1f), 0.08f, 1f);
					}
					int shade = (int) (light * 255);
					bb.addVertex(view, c.x + n.x * radius, c.y + n.y * radius, c.z + n.z * radius).setUv(v[0] / lon + spin, v[1] / lat)
							.setColor(shade, shade, shade, al);
				}
			}
		}
		draw(bb);
	}

	/** Stage 2: a violet river of light rising from the Earth toward the arena. */
	private static void feedRibbon(Matrix4f view, float time, float strength) {
		RenderSystem.setShaderTexture(0, nebulaTex);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		Vector3f from = new Vector3f(EARTH).mul(EARTH_DIST - EARTH_R * 0.6f);
		Vector3f to = new Vector3f(0, -0.2f, -1f).normalize().mul(100f);
		int seg = 16;
		for (int i = 0; i < seg; i++) {
			float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
			Vector3f p0 = new Vector3f(from).lerp(to, t0), p1 = new Vector3f(from).lerp(to, t1);
			float w0 = Mth.lerp(t0, 9f, 3f), w1 = Mth.lerp(t1, 9f, 3f);
			Vector3f side = new Vector3f(p1).sub(p0).cross(new Vector3f(p0)).normalize();
			float v0 = t0 * 2 - time * 0.01f, v1 = t1 * 2 - time * 0.01f;
			int a = (int) (strength * 200 * (1 - t0 * 0.6f));
			bb.addVertex(view, p0.x - side.x * w0, p0.y - side.y * w0, p0.z - side.z * w0).setUv(0, v0).setColor(190, 120, 255, a);
			bb.addVertex(view, p0.x + side.x * w0, p0.y + side.y * w0, p0.z + side.z * w0).setUv(1, v0).setColor(190, 120, 255, a);
			bb.addVertex(view, p1.x + side.x * w1, p1.y + side.y * w1, p1.z + side.z * w1).setUv(1, v1).setColor(190, 120, 255, a);
			bb.addVertex(view, p1.x - side.x * w1, p1.y - side.y * w1, p1.z - side.z * w1).setUv(0, v1).setColor(190, 120, 255, a);
		}
		draw(bb);
	}

	// ---------------------------------------------------------------- start-up: buffers and textures

	private static void ensure(Minecraft mc) {
		if (stars != null) {
			return;
		}
		stars = starBuffer(4200, 0.12f, 0.32f, 9001L, false);
		brightStars = starBuffer(260, 0.3f, 0.55f, 4242L, true);
		earthTex = register(mc, "earth", earth(512, 256, false));
		consumedTex = register(mc, "earth_consumed", earth(512, 256, true));
		cloudTex = register(mc, "earth_clouds", clouds(512, 256));
		nebulaTex = register(mc, "nebula", nebula(256, 128));
		glowTex = register(mc, "glow", glow(128));
	}

	private static ResourceLocation register(Minecraft mc, String name, NativeImage img) {
		DynamicTexture t = new DynamicTexture(img);
		t.setFilter(true, false);
		return mc.getTextureManager().register("projecthero_orbit_" + name, t);
	}

	private static VertexBuffer starBuffer(int count, float minSize, float maxSize, long seed, boolean bright) {
		RandomSource r = RandomSource.create(seed);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		for (int i = 0; i < count; i++) {
			float x = r.nextFloat() * 2 - 1, y = r.nextFloat() * 2 - 1, z = r.nextFloat() * 2 - 1;
			float l = x * x + y * y + z * z;
			if (l < 0.01f || l > 1f) {
				i--;
				continue;
			}
			Vector3f d = new Vector3f(x, y, z).normalize();
			// fewer stars where the Earth is
			if (d.dot(EARTH) > 0.86f) {
				continue;
			}
			float s = minSize + r.nextFloat() * (maxSize - minSize);
			Vector3f c = new Vector3f(d).mul(100f);
			Vector3f up = Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
			Vector3f a = new Vector3f(d).cross(up).normalize();
			Vector3f b = new Vector3f(a).cross(d).normalize();
			float rot = r.nextFloat() * Mth.TWO_PI;
			Vector3f a2 = new Vector3f(a).mul(Mth.cos(rot)).add(new Vector3f(b).mul(Mth.sin(rot))).mul(s);
			Vector3f b2 = new Vector3f(b).mul(Mth.cos(rot)).sub(new Vector3f(a).mul(Mth.sin(rot))).mul(s);
			float tint = r.nextFloat();
			int cr, cg, cb;
			if (tint < 0.15f) {
				cr = 170; cg = 200; cb = 255; // blue-white
			} else if (tint < 0.25f) {
				cr = 255; cg = 210; cb = 160; // warm
			} else if (tint < 0.3f) {
				cr = 220; cg = 170; cb = 255; // violet
			} else {
				cr = 240; cg = 240; cb = 255;
			}
			int al = bright ? 255 : 120 + r.nextInt(135);
			bb.addVertex(c.x - a2.x - b2.x, c.y - a2.y - b2.y, c.z - a2.z - b2.z).setColor(cr, cg, cb, al);
			bb.addVertex(c.x + a2.x - b2.x, c.y + a2.y - b2.y, c.z + a2.z - b2.z).setColor(cr, cg, cb, al);
			bb.addVertex(c.x + a2.x + b2.x, c.y + a2.y + b2.y, c.z + a2.z + b2.z).setColor(cr, cg, cb, al);
			bb.addVertex(c.x - a2.x + b2.x, c.y - a2.y + b2.y, c.z - a2.z + b2.z).setColor(cr, cg, cb, al);
		}
		VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
		vb.bind();
		vb.upload(bb.buildOrThrow());
		VertexBuffer.unbind();
		return vb;
	}

	// ---------------------------------------------------------------- noise textures

	private static int abgr(int r, int g, int b, int a) {
		return (a & 255) << 24 | (b & 255) << 16 | (g & 255) << 8 | r & 255;
	}

	private static double hash(int x, int y, int s) {
		long h = x * 374761393L + y * 668265263L + s * 2147483647L;
		h = (h ^ (h >>> 13)) * 1274126177L;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}

	/** Tileable value noise (period {@code px} x {@code py}). */
	private static double noise(double x, double y, int px, int py, int seed) {
		int x0 = Mth.floor(x), y0 = Mth.floor(y);
		double fx = x - x0, fy = y - y0;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		double a = hash(Math.floorMod(x0, px), Math.floorMod(y0, py), seed);
		double b = hash(Math.floorMod(x0 + 1, px), Math.floorMod(y0, py), seed);
		double c = hash(Math.floorMod(x0, px), Math.floorMod(y0 + 1, py), seed);
		double d = hash(Math.floorMod(x0 + 1, px), Math.floorMod(y0 + 1, py), seed);
		return Mth.lerp(fy, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
	}

	private static double fbm(double u, double v, int base, int octaves, int seed) {
		double sum = 0, amp = 0.5, norm = 0;
		int f = base;
		for (int o = 0; o < octaves; o++) {
			sum += amp * noise(u * f, v * f, f, f, seed + o * 17);
			norm += amp;
			amp *= 0.5;
			f *= 2;
		}
		return sum / norm;
	}

	private static NativeImage earth(int w, int h, boolean consumed) {
		NativeImage img = new NativeImage(w, h, true);
		for (int y = 0; y < h; y++) {
			double lat = Math.abs(y / (double) h - 0.5) * 2; // 0 equator .. 1 pole
			for (int x = 0; x < w; x++) {
				double u = x / (double) w, v = y / (double) h;
				double n = fbm(u, v * 0.5, 4, 6, 11);
				boolean land = n > 0.52;
				int r, g, b, a = 255;
				if (consumed) {
					double cr = fbm(u, v * 0.5, 16, 4, 77);
					boolean crack = Math.abs(cr - 0.5) < 0.018;
					int grey = land ? (int) (95 + n * 60) : (int) (52 + n * 40);
					r = grey;
					g = grey - 6;
					b = grey + 10;
					if (crack) {
						r = 210;
						g = 140;
						b = 255;
					}
					// a ragged edge of the draining: alpha varies so the grey spreads in patches
					a = 255;
				} else if (lat > 0.86 || (lat > 0.78 && n > 0.47)) {
					r = 235; g = 242; b = 250; // ice
				} else if (land) {
					double m = fbm(u, v * 0.5, 12, 4, 31);
					double t = (n - 0.52) / 0.48;
					if (lat < 0.35 && m < 0.45) {
						r = 196; g = 168; b = 112; // desert
					} else if (t > 0.35) {
						r = 92; g = 104; b = 74; // highland
					} else {
						r = (int) (52 + m * 40); g = (int) (112 + m * 40); b = (int) (48 + m * 20); // forest / grass
					}
				} else {
					double d = (0.52 - n) / 0.52;
					r = (int) (14 + 20 * (1 - d)); g = (int) (52 + 60 * (1 - d)); b = (int) (120 + 70 * (1 - d)); // ocean, shallows lighter
				}
				img.setPixelRGBA(x, y, abgr(r, g, b, a));
			}
		}
		return img;
	}

	private static NativeImage clouds(int w, int h) {
		NativeImage img = new NativeImage(w, h, true);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				double n = fbm(x / (double) w, y / (double) h * 0.5, 6, 6, 101);
				double c = Mth.clamp((n - 0.5) * 3.2, 0, 1);
				c *= Math.sin(y / (double) h * Math.PI); // thin toward the poles (no pinched streaks)
				img.setPixelRGBA(x, y, abgr(255, 255, 255, (int) (c * 235)));
			}
		}
		return img;
	}

	private static NativeImage nebula(int w, int h) {
		NativeImage img = new NativeImage(w, h, true);
		for (int y = 0; y < h; y++) {
			double vy = Math.sin(y / (double) h * Math.PI);
			for (int x = 0; x < w; x++) {
				double n = fbm(x / (double) w, y / (double) h, 4, 6, 303);
				double wisp = fbm(x / (double) w, y / (double) h, 8, 4, 909);
				double a = Mth.clamp((n - 0.35) * 2.2, 0, 1) * vy * (0.6 + 0.4 * wisp);
				int bright = (int) (180 + 75 * wisp);
				img.setPixelRGBA(x, y, abgr(bright, bright, bright, (int) (a * 255)));
			}
		}
		return img;
	}

	private static NativeImage glow(int size) {
		NativeImage img = new NativeImage(size, size, true);
		double c = (size - 1) / 2.0;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				double d = Math.sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c;
				double a = Mth.clamp(1 - d, 0, 1);
				a = a * a * (0.4 + 0.6 * a);
				img.setPixelRGBA(x, y, abgr(255, 255, 255, (int) (a * 255)));
			}
		}
		return img;
	}
}
