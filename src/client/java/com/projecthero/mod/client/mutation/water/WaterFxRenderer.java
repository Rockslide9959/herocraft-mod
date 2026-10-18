package com.projecthero.mod.client.mutation.water;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.WaterClient;
import com.projecthero.mod.hero.power.p25.GeyserEntity;
import com.projecthero.mod.hero.power.p25.HealingWaterEntity;
import com.projecthero.mod.hero.power.p25.RainCallEntity;
import com.projecthero.mod.hero.power.p25.TidalWaveEntity;
import com.projecthero.mod.hero.power.p25.WaterDragonEntity;
import com.projecthero.mod.hero.power.p25.WhirlpoolEntity;
import com.projecthero.mod.hero.power.p25.WaterBoltEntity;
import com.projecthero.mod.hero.power.p25.WaterFxEntity;
import com.projecthero.mod.hero.power.p25.WaterHandlers;
import com.projecthero.mod.hero.power.p25.WaterPrisonEntity;
import com.projecthero.mod.hero.power.p25.WaterWhipEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Water Manipulation rework: the translucent, animated water models of the power's water bodies, built from
 * {@link WaterMesh} in the vanilla water texture:
 * <ul>
 * <li>the Water Shot bolt -- a wobbling drop stretched along its flight, a bright core and a tapering spray trail;</li>
 * <li>the Water Whip -- a rope of water uncurling from the caster's hand along a travelling S-curve, cracking at the tip;</li>
 * <li>the Water Prison -- a rippling ball of water round the target with bubbles rising through it;</li>
 * <li>the Tidal Wave -- a 7-wide curling wall with a foaming crest that rears up, rolls and breaks;</li>
 * <li>the Geyser -- a bubbling pool, then a churning column with a spray crown that rises, holds and falls back;</li>
 * <li>Healing Water -- twin ribbons spiralling up round the body, and the ring that sweeps out over its 6-block reach.</li>
 * </ul>
 */
public class WaterFxRenderer<T extends WaterFxEntity> extends EntityRenderer<T> {
	public WaterFxRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public boolean shouldRender(T e, Frustum frustum, double x, double y, double z) {
		return e.distanceToSqr(x, y, z) < 128.0 * 128.0; // big, oddly shaped bodies: no box culling
	}

	@Override
	public void render(T e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = e.tickCount + partial;
		VertexConsumer vc = WaterMesh.buffer(buffers);
		int lit = Math.max(light & 0xFFFF, 0x90) | (light & 0xFFFF0000); // water catches the light a little
		pose.pushPose();
		if (e instanceof WaterBoltEntity b) {
			bolt(b, t, partial, pose, vc, lit);
		} else if (e instanceof WaterWhipEntity w) {
			whip(w, t, partial, pose, vc, lit);
		} else if (e instanceof WaterPrisonEntity p) {
			prison(p, t, pose, vc, lit);
		} else if (e instanceof TidalWaveEntity tw) {
			tidal(tw, t, partial, pose, vc, lit);
		} else if (e instanceof GeyserEntity g) {
			geyser(g, t, pose, vc, lit);
		} else if (e instanceof HealingWaterEntity h) {
			healing(h, t, pose, vc, lit);
		} else if (e instanceof WaterDragonEntity d) {
			WaterHoldFx.dragon(d, t, partial, pose, vc, lit);
		} else if (e instanceof WhirlpoolEntity w) {
			WaterHoldFx.whirlpool(w, t, pose, vc, lit);
		} else if (e instanceof RainCallEntity r) {
			WaterHoldFx.rain(r, t, partial, pose, vc, lit);
		}
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	private static Vec3 lerpPos(WaterFxEntity e, float partial) {
		return new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ()));
	}

	// ---------------------------------------------------------------- R: the bolt

	private static void bolt(WaterBoltEntity e, float t, float partial, PoseStack pose, VertexConsumer vc, int light) {
		Vec3 mv = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
		Vec3 dir = mv.lengthSqr() > 1.0e-4 ? mv.normalize() : Vec3.directionFromRotation(e.getXRot(), e.getYRot());
		TextureAtlasSprite still = WaterMesh.still();
		TextureAtlasSprite flow = WaterMesh.flow();
		float grow = Math.min(1f, t / 2f);
		// the trail: a tapering stream back along the flight
		int n = 9;
		Vec3[] pts = new Vec3[n];
		float[] radii = new float[n];
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		for (int i = 0; i < n; i++) {
			float s = i / (float) (n - 1);
			double back = -s * 1.9 * grow;
			double sway = Mth.sin(s * 6f - t * 1.3f) * 0.06 * s;
			pts[i] = dir.scale(back).add(side.scale(sway)).add(0, -s * s * 0.15, 0);
			radii[i] = (0.2f * (1f - s) + 0.02f) * grow;
		}
		WaterMesh.tube(vc, pose, pts, radii, 8, 0.12f, t, WaterMesh.LIGHT, light, flow);
		// the drop, stretched along the flight
		pose.pushPose();
		float yawR = (float) Math.atan2(dir.x, dir.z);
		float pitchR = (float) -Math.asin(Mth.clamp(dir.y, -1, 1));
		pose.mulPose(Axis.YP.rotation(yawR));
		pose.mulPose(Axis.XP.rotation(pitchR));
		pose.scale(1f, 1f, 1.7f);
		WaterMesh.sphere(vc, pose, 0.28f * grow, 1f, 0.12f, t * 3f, WaterMesh.BODY, light, still);
		WaterMesh.sphere(vc, pose, 0.15f * grow, 1f, 0.2f, t * 4f, WaterMesh.FOAM, light, flow);
		pose.popPose();
		// two droplets spinning round it
		for (int k = 0; k < 2; k++) {
			float a = t * 0.9f + k * Mth.PI;
			Vec3 up = side.cross(dir);
			Vec3 at = side.scale(Mth.cos(a) * 0.36).add(up.scale(Mth.sin(a) * 0.36)).add(dir.scale(-0.25));
			pose.pushPose();
			pose.translate(at.x, at.y, at.z);
			WaterMesh.sphere(vc, pose, 0.07f, 1f, 0.1f, t, WaterMesh.LIGHT, light, still);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- G: the whip

	/** How far out (0..1) the lash is at age {@code t}: uncurls in 3 ticks, holds, pulls back from tick 6. */
	public static float whipExtension(float t) {
		float ext = t < 3f ? t / 3f : t < 6f ? 1f : Math.max(0f, 1f - (t - 6f) / 6f);
		return ext * ext * (3f - 2f * ext);
	}

	private static void whip(WaterWhipEntity e, float t, float partial, PoseStack pose, VertexConsumer vc, int light) {
		if (!(e.level().getEntity(e.ownerId()) instanceof Player owner)) {
			return;
		}
		Vec3 origin = lerpPos(e, partial);
		Vec3 hand = WaterClient.handPos(owner, partial, true).subtract(origin);
		Vec3 tip = Vec3.ZERO;
		float ext = whipExtension(t);
		if (ext < 0.01f) {
			return;
		}
		Vec3 line = tip.subtract(hand);
		double len = line.length();
		if (len < 0.2) {
			return;
		}
		Vec3 dir = line.scale(1.0 / len);
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		int n = Math.max(12, (int) (len * 3));
		Vec3[] pts = new Vec3[n];
		float[] radii = new float[n];
		float slack = 1f - 0.65f * ext; // the rope straightens as it lands
		for (int i = 0; i < n; i++) {
			float s = i / (float) (n - 1) * ext;
			float wave = Mth.sin(s * Mth.PI * 3f - t * 1.6f) * 0.75f * Mth.sin(Mth.PI * s) * slack;
			float arc = Mth.sin(Mth.PI * s) * (0.9f * slack + 0.15f);
			pts[i] = hand.add(dir.scale(len * s)).add(side.scale(wave)).add(up.scale(arc));
			radii[i] = 0.14f * (1f - s) + 0.06f;
		}
		WaterMesh.tube(vc, pose, pts, radii, 8, 0.15f, t * 2f, WaterMesh.BODY, light, WaterMesh.flow());
		float[] core = new float[n];
		for (int i = 0; i < n; i++) {
			core[i] = radii[i] * 0.45f;
		}
		WaterMesh.tube(vc, pose, pts, core, 6, 0.2f, t * 2f, WaterMesh.FOAM, light, WaterMesh.still());
		// the crack at the tip
		if (t > 2f && t < 7f) {
			float k = (t - 2f) / 5f;
			pose.pushPose();
			Vec3 at = pts[n - 1];
			pose.translate(at.x, at.y, at.z);
			WaterMesh.sphere(vc, pose, 0.25f + 0.45f * k, 0.8f, 0.3f, t * 3f, WaterMesh.fade(WaterMesh.FOAM, 1f - k), light, WaterMesh.flow());
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- Shift+G: the prison

	private static void prison(WaterPrisonEntity e, float t, PoseStack pose, VertexConsumer vc, int light) {
		float r = e.size();
		float in = Math.min(1f, t / 5f);
		float s = in < 1f ? 1f + 0.25f * Mth.sin(in * Mth.PI) - (1f - in) : 1f; // swells in with a little overshoot
		if (t > WaterHandlers.PRISON_TICKS - 5) {
			s *= 1f + (t - (WaterHandlers.PRISON_TICKS - 5)) * 0.05f; // straining just before it bursts
		}
		s = Math.max(0.05f, s);
		TextureAtlasSprite still = WaterMesh.still();
		TextureAtlasSprite flow = WaterMesh.flow();
		// bubbles rising through it
		for (int k = 0; k < 8; k++) {
			float ph = (t * 0.035f + k / 8f) % 1f;
			float a = k * 2.4f + t * 0.05f;
			float rad = r * 0.55f * Mth.sin(ph * Mth.PI);
			pose.pushPose();
			pose.translate(Mth.cos(a) * rad, (ph * 2f - 1f) * r * 0.85f * s, Mth.sin(a) * rad);
			WaterMesh.sphere(vc, pose, 0.06f + 0.04f * (k % 3), 1f, 0.1f, t, WaterMesh.FOAM, light, still);
			pose.popPose();
		}
		WaterMesh.sphere(vc, pose, r * s, 1f, 0.06f, t, WaterMesh.BODY, light, still);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(t * 3f));
		WaterMesh.sphere(vc, pose, r * s * 1.05f, 1f, 0.08f, t * 1.3f, WaterMesh.SHELL, light, flow);
		pose.popPose();
	}

	// ---------------------------------------------------------------- Z: the tidal wave

	/** The cross-section of a curling wave (z forward, y up) at s 0..1, from the back of its base over the crest to the lip. */
	private static final float[][] CURL = { { -1.5f, 0f }, { -1.0f, 0.9f }, { -0.45f, 1.85f }, { 0.15f, 2.55f }, { 0.7f, 2.78f },
			{ 1.15f, 2.55f }, { 1.38f, 2.05f }, { 1.22f, 1.55f } };

	/** A point on the curl: s 0..1 along {@link #CURL}, {@code h} its height scale, {@code fall} 0..1 how far it has broken. */
	public static Vec3 curl(float s, float h, float len, float fall) {
		float f = Mth.clamp(s, 0f, 1f) * (CURL.length - 1);
		int i = Math.min(CURL.length - 2, (int) f);
		float k = f - i;
		float z = Mth.lerp(k, CURL[i][0], CURL[i + 1][0]) * len;
		float y = Mth.lerp(k, CURL[i][1], CURL[i + 1][1]) * h;
		// breaking: the face slumps forward and down into spray
		z += fall * (0.6f + 1.4f * s);
		y *= 1f - fall * (0.55f + 0.4f * s);
		return new Vec3(0, y, z);
	}

	private static void tidal(TidalWaveEntity e, float t, float partial, PoseStack pose, VertexConsumer vc, int light) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		// v0.16.6: the same wall scaled to the wave's size -- the Tidal Wave (7 x 3) or the Tsunami (12 x 6)
		float k = (float) (e.height() / TidalWaveEntity.HEIGHT);
		float rise = Math.min(1f, t / e.rise());
		rise = rise * rise * (3f - 2f * rise);
		int broken = e.brokenFor(partial);
		float fall = broken < 0 ? 0f : Math.min(1f, (broken + partial) / e.collapse());
		float alpha = 1f - fall * fall;
		float h = (0.15f + 0.85f * rise) * k;
		float half = (float) e.halfWidth();
		int cols = Math.max(14, (int) (half * 2f * 1.4f));
		float wobK = 0.6f + 0.4f * k;
		WaterMesh.Profile body = (x, s) -> {
			float edge = Mth.clamp((Math.abs(x) - (half - 1.2f * k)) / (1.2f * k), 0f, 1f);
			float taper = 1f - 0.7f * edge * edge;
			Vec3 p = curl(s, h * taper, (0.8f + 0.2f * taper) * k, fall);
			float wob = (Mth.sin(x * 1.35f / wobK + t * 0.55f + s * 3f) * 0.13f * s + Mth.sin(x * 0.6f - t * 0.3f) * 0.08f) * wobK;
			return new Vec3(x, p.y + wob, p.z + Mth.sin(x * 0.9f / wobK - t * 0.4f) * 0.12f * s * wobK);
		};
		WaterMesh.sheet(vc, pose, half, cols, 14 + (int) (4 * (k - 1)), body, WaterMesh.fade(WaterMesh.BODY, alpha), light, WaterMesh.still());
		// the foaming crest, riding just outside the top of the face
		WaterMesh.Profile crest = (x, s) -> {
			Vec3 p = body.at(x, 0.5f + 0.5f * s);
			return new Vec3(x, p.y + 0.05f * k, p.z + 0.04f * k);
		};
		WaterMesh.sheet(vc, pose, half - 0.2f, cols, 6, crest, WaterMesh.fade(WaterMesh.FOAM, alpha * 0.85f), light, WaterMesh.flow());
		// the churning foot running ahead of it
		WaterMesh.Profile foot = (x, s) -> new Vec3(x, 0.06f + 0.12f * k * Mth.sin(s * Mth.PI) * (1f + Mth.sin(x * 2f + t)),
				(0.6f + s * (1.2f + fall * 1.5f)) * k);
		WaterMesh.sheet(vc, pose, half, Math.max(10, cols * 2 / 3), 3, foot, WaterMesh.fade(WaterMesh.LIGHT, alpha * 0.8f), light,
				WaterMesh.flow());
		if (k > 1.2f) {
			// the Tsunami: spray streaming off the lip of the crest
			for (int i = 0; i < 9; i++) {
				float x = -half + 1f + (2f * half - 2f) * i / 8f;
				Vec3 lip = body.at(x, 0.97f);
				int n = 6;
				Vec3[] pts = new Vec3[n];
				float[] radii = new float[n];
				for (int j = 0; j < n; j++) {
					float s = j / (float) (n - 1);
					pts[j] = lip.add(Mth.sin(t * 0.3f + i) * 0.2f * s, s * 0.6f * k - s * s * 1.4f, s * 1.3f * k);
					radii[j] = (0.22f * (1f - s) + 0.04f) * k * 0.6f;
				}
				WaterMesh.tube(vc, pose, pts, radii, 6, 0.25f, t * 2f + i, WaterMesh.fade(WaterMesh.FOAM, alpha * 0.7f), light, WaterMesh.flow());
			}
		}
	}

	// ---------------------------------------------------------------- Shift+Z: the geyser

	private static void geyser(GeyserEntity e, float t, PoseStack pose, VertexConsumer vc, int light) {
		TextureAtlasSprite still = WaterMesh.still();
		TextureAtlasSprite flow = WaterMesh.flow();
		float pool = Math.min(1f, t / 3f);
		if (t < GeyserEntity.TELEGRAPH) {
			// the ground bubbles first
			WaterMesh.disc(vc, pose, 0.95f * pool, 0.04f, t * 3f, WaterMesh.LIGHT, light, flow);
			for (int k = 0; k < 5; k++) {
				float ph = (t * 0.25f + k / 5f) % 1f;
				float a = k * 1.3f;
				pose.pushPose();
				pose.translate(Mth.cos(a) * 0.5f, 0.05f + ph * 0.4f, Mth.sin(a) * 0.5f);
				WaterMesh.sphere(vc, pose, 0.08f * (1f - ph) + 0.03f, 1f, 0.1f, t, WaterMesh.FOAM, light, still);
				pose.popPose();
			}
			return;
		}
		float age = t - GeyserEntity.TELEGRAPH;
		float life = GeyserEntity.LIFE - GeyserEntity.TELEGRAPH;
		float up = Math.min(1f, age / 4f);
		float down = age > life - 10f ? Math.max(0f, (life - age) / 10f) : 1f;
		float height = (float) WaterHandlers.GEYSER_HEIGHT * up * (0.25f + 0.75f * down);
		if (height < 0.2f) {
			return;
		}
		int n = Math.max(3, (int) (height * 2f) + 1);
		Vec3[] pts = new Vec3[n];
		float[] outer = new float[n];
		float[] inner = new float[n];
		for (int i = 0; i < n; i++) {
			float y = height * i / (n - 1);
			pts[i] = new Vec3(Mth.sin(y * 0.6f + t * 0.3f) * 0.06f, y, Mth.cos(y * 0.5f - t * 0.25f) * 0.06f);
			float flare = Math.max(0f, 1f - y / 1.6f);
			outer[i] = (0.62f + 0.1f * Mth.sin(y * 1.7f - t * 1.4f) + 0.45f * flare * flare) * (0.6f + 0.4f * down);
			inner[i] = outer[i] * 0.5f;
		}
		WaterMesh.tube(vc, pose, pts, outer, 12, 0.08f, t * 2f, WaterMesh.BODY, light, flow);
		WaterMesh.tube(vc, pose, pts, inner, 8, 0.12f, t * 2.5f, WaterMesh.FOAM, light, still);
		// the spray crown on top, and the ring of splash round the foot
		pose.pushPose();
		pose.translate(pts[n - 1].x, height, pts[n - 1].z);
		pose.mulPose(Axis.YP.rotationDegrees(t * 6f));
		WaterMesh.sphere(vc, pose, 1.0f * (0.7f + 0.3f * down), 0.45f, 0.25f, t * 3f, WaterMesh.FOAM, light, flow);
		WaterMesh.ring(vc, pose, 1.15f, 0.18f, 16, 0.3f, t * 2f, WaterMesh.LIGHT, light, still);
		pose.popPose();
		WaterMesh.ring(vc, pose, 1.25f + 0.1f * Mth.sin(t * 0.8f), 0.2f, 18, 0.25f, t * 2f, WaterMesh.LIGHT, light, flow);
		WaterMesh.disc(vc, pose, 1.3f, 0.03f, t * 2f, WaterMesh.fade(WaterMesh.LIGHT, 0.8f), light, flow);
	}

	// ---------------------------------------------------------------- Shift+V: healing water

	private static void healing(HealingWaterEntity e, float t, PoseStack pose, VertexConsumer vc, int light) {
		TextureAtlasSprite still = WaterMesh.still();
		TextureAtlasSprite flow = WaterMesh.flow();
		float fade = Math.min(1f, t / 6f) * Math.min(1f, (HealingWaterEntity.LIFE - t) / 14f);
		if (fade <= 0.02f) {
			return;
		}
		LivingEntity body = e.target();
		float h = body == null ? 1.8f : body.getBbHeight();
		float w = body == null ? 0.6f : body.getBbWidth();
		float rr = w * 0.75f + 0.28f;
		int col = WaterMesh.fade(WaterMesh.HEAL, fade);
		for (int strand = 0; strand < 2; strand++) {
			int n = 28;
			Vec3[] pts = new Vec3[n];
			float[] radii = new float[n];
			for (int i = 0; i < n; i++) {
				float s = i / (float) (n - 1);
				float a = s * Mth.PI * 3.5f + t * 0.24f + strand * Mth.PI;
				float r = rr * (1f + 0.12f * Mth.sin(s * 9f + t * 0.4f));
				pts[i] = new Vec3(Mth.cos(a) * r, 0.05f + s * h * 1.05f, Mth.sin(a) * r);
				radii[i] = 0.035f + 0.045f * Mth.sin(Mth.PI * s);
			}
			WaterMesh.tube(vc, pose, pts, radii, 6, 0.15f, t * 2f, col, light, flow);
			// a drop running up each strand
			for (int k = 0; k < 3; k++) {
				float s = (t * 0.025f + k / 3f + strand * 0.15f) % 1f;
				int i = Math.min(n - 1, (int) (s * (n - 1)));
				pose.pushPose();
				pose.translate(pts[i].x, pts[i].y, pts[i].z);
				WaterMesh.sphere(vc, pose, 0.07f, 1f, 0.1f, t, WaterMesh.fade(WaterMesh.FOAM, fade), light, still);
				pose.popPose();
			}
		}
		// the caster's: a ring of water sweeping out over the 6-block reach, a second one chasing it
		float reach = e.size();
		if (reach > 0.1f && t < 30f) {
			for (int k = 0; k < 2; k++) {
				float a = Math.max(0f, t - k * 4f);
				float out = 1f - (1f - Math.min(1f, a / 14f)) * (1f - Math.min(1f, a / 14f));
				float ringFade = Math.max(0f, 1f - a / 28f);
				if (out > 0.02f && ringFade > 0.02f) {
					pose.pushPose();
					pose.translate(0, 0.12f + 0.05f * k, 0);
					WaterMesh.ring(vc, pose, reach * out, 0.13f - 0.04f * k, 40, 0.3f, t * 2f, WaterMesh.fade(WaterMesh.HEAL, ringFade), light,
							still);
					pose.popPose();
				}
			}
		}
	}

	@Override
	public ResourceLocation getTextureLocation(T e) {
		return TextureAtlas.LOCATION_BLOCKS;
	}

	/** Packed full-bright light, for the bodies drawn round players (see {@link WaterClient}). */
	public static final int FULL = LightTexture.FULL_BRIGHT;
}
