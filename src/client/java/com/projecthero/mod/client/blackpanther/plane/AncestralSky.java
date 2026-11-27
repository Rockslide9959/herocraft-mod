package com.projecthero.mod.client.blackpanther.plane;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.blackpanther.plane.PlaneRealm;

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

/**
 * v0.17.7 Black Panther Ancestral Plane: <b>the sky</b> of {@code projecthero:ancestral_plane}, like the films' plane -- a vast
 * warm sunset: a deep violet zenith falling through magenta and rose to a blazing orange-gold horizon, brightest round the huge
 * sun sitting low in the east (+X, behind the ancestors, the shrine and the end of the spirit path, so they stand dark against
 * it), long thin clouds lit pink and gold near the horizon, slow purple aurora curtains rippling high overhead, and a few faint
 * stars at the dark top of the sky. The fog is the horizon's orange, so the endless savanna melts into the glow. No clouds, no
 * weather. Everything is generated here (the one glow texture too); presentation only.
 */
public final class AncestralSky {
	public static final ResourceLocation EFFECTS = ProjectHeroMod.id("ancestral_plane");
	/** The sun: east, 4 degrees up. */
	static final Vector3f SUN = new Vector3f(1f, (float) Math.sin(Math.toRadians(4)), 0.12f).normalize();
	/** The fog / far horizon colour. */
	public static final float FOG_R = 0.95f, FOG_G = 0.58f, FOG_B = 0.33f;
	private static ResourceLocation glowTex;
	private static float[][] stars;

	private AncestralSky() {
	}

	public static void initialize() {
		DimensionRenderingRegistry.registerDimensionEffects(EFFECTS, new Effects());
		DimensionRenderingRegistry.registerSkyRenderer(PlaneRealm.DIMENSION, AncestralSky::render);
		DimensionRenderingRegistry.registerCloudRenderer(PlaneRealm.DIMENSION, ctx -> {
		});
		DimensionRenderingRegistry.registerWeatherRenderer(PlaneRealm.DIMENSION, ctx -> {
		});
	}

	static final class Effects extends DimensionSpecialEffects {
		Effects() {
			super(Float.NaN, false, SkyType.NONE, false, false);
		}

		@Override
		public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
			return new Vec3(FOG_R, FOG_G, FOG_B);
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

	// ---------------------------------------------------------------- colours

	private static final float[][] STOPS = {
			// elevation (deg), r, g, b
			{ -90, 0.42f, 0.22f, 0.14f }, { -6, 0.86f, 0.50f, 0.28f }, { 0, FOG_R, FOG_G, FOG_B }, { 6, 0.96f, 0.47f, 0.30f },
			{ 16, 0.84f, 0.32f, 0.36f }, { 30, 0.60f, 0.21f, 0.42f }, { 50, 0.34f, 0.12f, 0.38f }, { 72, 0.18f, 0.07f, 0.28f },
			{ 90, 0.12f, 0.05f, 0.22f } };

	/** The sky's colour looking along unit {@code d}. */
	static float[] colour(Vector3f d) {
		float e = (float) Math.toDegrees(Math.asin(Mth.clamp(d.y, -1f, 1f)));
		float r = 0, g = 0, b = 0;
		for (int i = 0; i < STOPS.length - 1; i++) {
			float[] a = STOPS[i], c = STOPS[i + 1];
			if (e >= a[0] && e <= c[0]) {
				float k = (e - a[0]) / (c[0] - a[0]);
				k = k * k * (3 - 2 * k);
				r = Mth.lerp(k, a[1], c[1]);
				g = Mth.lerp(k, a[2], c[2]);
				b = Mth.lerp(k, a[3], c[3]);
				break;
			}
		}
		// toward the sun: a golden bloom; away from it: deeper violet
		float sd = d.dot(SUN);
		float bloom = (float) Math.pow(Math.max(0f, sd), 5) * Mth.clamp(1f - Math.abs(e) / 55f, 0f, 1f);
		r = Mth.lerp(bloom * 0.85f, r, 1.0f);
		g = Mth.lerp(bloom * 0.85f, g, 0.80f);
		b = Mth.lerp(bloom * 0.85f, b, 0.46f);
		float away = Math.max(0f, -sd) * Mth.clamp(e / 25f, 0f, 1f) * 0.35f;
		r = Mth.lerp(away, r, 0.30f);
		g = Mth.lerp(away, g, 0.12f);
		b = Mth.lerp(away, b, 0.40f);
		return new float[] { r, g, b };
	}

	private static Vector3f dir(float azDeg, float elDeg) {
		float az = (float) Math.toRadians(azDeg), el = (float) Math.toRadians(elDeg);
		return new Vector3f(Mth.cos(el) * Mth.cos(az), Mth.sin(el), Mth.cos(el) * Mth.sin(az));
	}

	// ---------------------------------------------------------------- render

	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		ensure(mc);
		Matrix4f view = new Matrix4f(ctx.positionMatrix());
		float time = (mc.level == null ? 0 : mc.level.getGameTime()) + ctx.tickCounter().getGameTimeDeltaPartialTick(false);

		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

		// the dome
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		float[] els = { -90, -30, -12, -6, -3, 0, 2, 4, 7, 10, 14, 19, 25, 32, 40, 50, 62, 75, 90 };
		int segs = 64;
		for (int j = 0; j < els.length - 1; j++) {
			for (int i = 0; i < segs; i++) {
				float a0 = i * 360f / segs, a1 = (i + 1) * 360f / segs;
				domeVertex(bb, view, a0, els[j]);
				domeVertex(bb, view, a1, els[j]);
				domeVertex(bb, view, a1, els[j + 1]);
				domeVertex(bb, view, a0, els[j + 1]);
			}
		}
		draw(bb);

		// faint stars at the dark top of the sky, away from the sun
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		for (int i = 0; i < stars.length; i++) {
			float[] st = stars[i];
			Vector3f d = new Vector3f(st[0], st[1], st[2]);
			float e = (float) Math.toDegrees(Math.asin(d.y));
			float vis = Mth.clamp((e - 38f) / 30f, 0f, 1f) * Mth.clamp(0.6f - d.dot(SUN), 0f, 1f);
			if (vis <= 0.02f) {
				continue;
			}
			float tw = 0.6f + 0.4f * Mth.sin(time * 0.06f + i * 1.7f);
			quad(bb, view, d, 99f, st[3], 255, 236, 250, (int) (vis * tw * 200));
		}
		draw(bb);

		// long thin clouds near the horizon, lit pink and gold (brighter toward the sun)
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		for (int band = 0; band < 6; band++) {
			float baseEl = 3.5f + band * 1.9f + (band % 2) * 0.7f;
			float thick = 0.7f + (band % 3) * 0.35f;
			float off = band * 47f + time * 0.0008f * (band % 2 == 0 ? 1 : -1);
			for (int i = 0; i < 90; i++) {
				float a0 = i * 4f, a1 = (i + 1) * 4f;
				float n0 = cloudAlpha(a0 + off, band), n1 = cloudAlpha(a1 + off, band);
				if (n0 <= 0.01f && n1 <= 0.01f) {
					continue;
				}
				cloudVertex(bb, view, a0, baseEl - thick, n0 * 0.5f);
				cloudVertex(bb, view, a1, baseEl - thick, n1 * 0.5f);
				cloudVertex(bb, view, a1, baseEl + thick, n1);
				cloudVertex(bb, view, a0, baseEl + thick, n0);
			}
		}
		draw(bb);

		// the purple aurora: curtains rippling high overhead (additive)
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		for (int ribbon = 0; ribbon < 4; ribbon++) {
			float start = 120f + ribbon * 38f, span = 150f;
			float phase = ribbon * 1.9f;
			int n = 72;
			for (int i = 0; i < n; i++) {
				float u0 = i / (float) n, u1 = (i + 1) / (float) n;
				float az0 = start + span * u0, az1 = start + span * u1;
				float e0 = auroraEl(u0, ribbon, phase, time), e1 = auroraEl(u1, ribbon, phase, time);
				float h = 9f + ribbon * 2.5f;
				float al0 = auroraAlpha(u0, ribbon, time), al1 = auroraAlpha(u1, ribbon, time);
				int r = ribbon % 2 == 0 ? 170 : 210, g = ribbon % 2 == 0 ? 90 : 80, b = 255;
				vtx(bb, view, dir(az0, e0), 98f).setColor(r, g, b, (int) (al0 * 150));
				vtx(bb, view, dir(az1, e1), 98f).setColor(r, g, b, (int) (al1 * 150));
				vtx(bb, view, dir(az1, e1 + h), 98f).setColor(120, 60, 230, 0);
				vtx(bb, view, dir(az0, e0 + h), 98f).setColor(120, 60, 230, 0);
				// a thin bright hem under the curtain
				vtx(bb, view, dir(az0, e0 - 0.8f), 98f).setColor(255, 170, 255, 0);
				vtx(bb, view, dir(az1, e1 - 0.8f), 98f).setColor(255, 170, 255, 0);
				vtx(bb, view, dir(az1, e1), 98f).setColor(240, 150, 255, (int) (al1 * 120));
				vtx(bb, view, dir(az0, e0), 98f).setColor(240, 150, 255, (int) (al0 * 120));
			}
		}
		draw(bb);

		// the sun: a wide warm halo, a bright glow, the disc (additive)
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, glowTex);
		billboard(view, SUN, 100f, 70f, 255, 120, 60, 120);
		billboard(view, SUN, 100f, 30f, 255, 170, 90, 200);
		billboard(view, SUN, 100f, 13f, 255, 225, 160, 255);
		billboard(view, SUN, 100f, 7.5f, 255, 250, 225, 255);
		billboard(view, SUN, 100f, 7.5f, 255, 250, 225, 255);

		RenderSystem.defaultBlendFunc();
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	private static void domeVertex(BufferBuilder bb, Matrix4f view, float az, float el) {
		Vector3f d = dir(az, el);
		float[] c = colour(d);
		bb.addVertex(view, d.x * 100f, d.y * 100f, d.z * 100f).setColor(c[0], c[1], c[2], 1f);
	}

	private static float cloudAlpha(float az, int band) {
		float a = (float) (Math.sin(Math.toRadians(az * 2.3 + band * 31)) * 0.6 + Math.sin(Math.toRadians(az * 5.1 + band * 77)) * 0.4);
		return Mth.clamp((a - 0.25f) * 1.6f, 0f, 1f) * 0.55f;
	}

	private static void cloudVertex(BufferBuilder bb, Matrix4f view, float az, float el, float alpha) {
		Vector3f d = dir(az, el);
		float sd = Math.max(0f, d.dot(SUN));
		float r = 1.0f, g = Mth.lerp(sd, 0.52f, 0.86f), b = Mth.lerp(sd, 0.50f, 0.56f);
		bb.addVertex(view, d.x * 99.5f, d.y * 99.5f, d.z * 99.5f).setColor(r, g, b, alpha);
	}

	private static float auroraEl(float u, int ribbon, float phase, float time) {
		return 34f + ribbon * 7f + 7f * Mth.sin(u * 7.0f + phase + time * 0.004f) + 3f * Mth.sin(u * 17f - time * 0.007f + ribbon);
	}

	private static float auroraAlpha(float u, int ribbon, float time) {
		float edge = Mth.clamp(u * 6f, 0f, 1f) * Mth.clamp((1f - u) * 6f, 0f, 1f);
		float streak = 0.55f + 0.45f * Mth.sin(u * 40f + time * 0.02f + ribbon * 2.3f);
		float breathe = 0.7f + 0.3f * Mth.sin(time * 0.01f + ribbon);
		return edge * streak * breathe;
	}

	private static com.mojang.blaze3d.vertex.VertexConsumer vtx(BufferBuilder bb, Matrix4f view, Vector3f d, float dist) {
		return bb.addVertex(view, d.x * dist, d.y * dist, d.z * dist);
	}

	private static void quad(BufferBuilder bb, Matrix4f view, Vector3f d, float dist, float size, int r, int g, int b, int a) {
		Vector3f c = new Vector3f(d).mul(dist);
		Vector3f up = Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
		Vector3f right = new Vector3f(d).cross(up).normalize().mul(size);
		Vector3f up2 = new Vector3f(right).cross(d).normalize().mul(size);
		bb.addVertex(view, c.x - right.x - up2.x, c.y - right.y - up2.y, c.z - right.z - up2.z).setColor(r, g, b, a);
		bb.addVertex(view, c.x + right.x - up2.x, c.y + right.y - up2.y, c.z + right.z - up2.z).setColor(r, g, b, a);
		bb.addVertex(view, c.x + right.x + up2.x, c.y + right.y + up2.y, c.z + right.z + up2.z).setColor(r, g, b, a);
		bb.addVertex(view, c.x - right.x + up2.x, c.y - right.y + up2.y, c.z - right.z + up2.z).setColor(r, g, b, a);
	}

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

	private static void draw(BufferBuilder bb) {
		MeshData data = bb.build();
		if (data != null) {
			BufferUploader.drawWithShader(data);
		}
	}

	// ---------------------------------------------------------------- start-up

	private static void ensure(Minecraft mc) {
		if (glowTex != null) {
			return;
		}
		NativeImage img = new NativeImage(128, 128, false);
		for (int y = 0; y < 128; y++) {
			for (int x = 0; x < 128; x++) {
				float dx = (x + 0.5f) / 64f - 1f, dy = (y + 0.5f) / 64f - 1f;
				float r = Mth.sqrt(dx * dx + dy * dy);
				float a = Mth.clamp(1f - r, 0f, 1f);
				a = a * a * (0.6f + 0.4f * a);
				int al = (int) (a * 255);
				img.setPixelRGBA(x, y, (al << 24) | 0xFFFFFF);
			}
		}
		DynamicTexture t = new DynamicTexture(img);
		t.setFilter(true, false);
		glowTex = mc.getTextureManager().register("projecthero_ancestral_glow", t);
		RandomSource rs = RandomSource.create(7177L);
		stars = new float[260][];
		for (int i = 0; i < stars.length; i++) {
			Vector3f d;
			do {
				d = new Vector3f(rs.nextFloat() * 2 - 1, rs.nextFloat(), rs.nextFloat() * 2 - 1);
			} while (d.lengthSquared() < 0.05f || d.lengthSquared() > 1f);
			d.normalize();
			stars[i] = new float[] { d.x, d.y, d.z, 0.12f + rs.nextFloat() * 0.22f };
		}
	}
}
