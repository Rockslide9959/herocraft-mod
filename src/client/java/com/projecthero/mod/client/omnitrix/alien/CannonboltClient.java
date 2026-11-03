package com.projecthero.mod.client.omnitrix.alien;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltKit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 Cannonbolt's client kit (user: "build canonbolt alien for the prototype omnitrix please. Use this skin: ...
 * planetminecraft.com/skin/cannonbolt-original-series"). Standing he is the user's skin on the player body at 1.3 -- headless, his
 * face on his chest, like the show -- with a heavy, arms-out stance and a pose per move. Rolled up (Ball Mode, or curled mid-move)
 * the player model is replaced by his ball: a rounded shell of gold plates ({@code cannonbolt_ball.png}) that rolls with the
 * distance he travels, spins on the spot for the Shell Spin, and fills his ball hitbox.
 */
public final class CannonboltClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/cannonbolt.png");
	private static final ResourceLocation BALL = ProjectHeroMod.id("textures/entity/omnitrix/cannonbolt_ball.png");

	public static void register() {
		AlienClients.register(Aliens.CANNONBOLT.id(), new CannonboltClient());
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX)
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	private static final float[][] BASH = { QuirkKeyframes.rest(0), f(2, -0.4f, 0, 0.6f, 0.3f, 0, -0.2f, 0.35f), f(6, -0.3f, 0, 0.5f, 0.3f, 0, -0.2f, 0.3f),
			QuirkKeyframes.rest(11) };
	private static final float[][] SLAM = { QuirkKeyframes.rest(0), f(3, -2.6f, 0, 0.3f, -2.6f, 0, -0.3f, -0.2f), f(7, -1.4f, 0, 0.2f, -1.4f, 0, -0.2f, 0.9f),
			f(14, -1.3f, 0, 0.2f, -1.3f, 0, -0.2f, 0.85f), QuirkKeyframes.rest(22) };
	private static final float[][] CURL = { QuirkKeyframes.rest(0), f(3, -1.2f, 0, -0.5f, -1.2f, 0, 0.5f, 0.6f), f(8, -1.2f, 0, -0.5f, -1.2f, 0, 0.5f, 0.6f),
			QuirkKeyframes.rest(12) };
	private static final float[][] GUARD = { QuirkKeyframes.rest(0), f(3, -1.5f, 0, -0.9f, -1.5f, 0, 0.9f, 0.2f), f(60, -1.5f, 0, -0.9f, -1.5f, 0, 0.9f, 0.2f),
			QuirkKeyframes.rest(66) };
	private static final float[][] POUND = { QuirkKeyframes.rest(0), f(4, -2.9f, 0, 0.25f, -2.9f, 0, -0.25f, -0.15f), f(7, -0.5f, 0, 0.2f, -0.5f, 0, -0.2f, 0.55f),
			f(12, -0.5f, 0, 0.2f, -0.5f, 0, -0.2f, 0.5f), QuirkKeyframes.rest(18) };
	private static final float[][] UNCURL = { f(0, -1.2f, 0, -0.5f, -1.2f, 0, 0.5f, 0.6f), f(4, 0.3f, 0, 0.9f, 0.3f, 0, -0.9f, -0.1f), QuirkKeyframes.rest(10) };
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, -1.6f, 0.2f, 0, 0, 0, 0, 0.15f), QuirkKeyframes.rest(7) };

	private static final FirstPersonClip FP_BASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -10, 30, 10, 0.2f, 0.05f, -0.3f),
			FirstPersonClip.f(9, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_DOWN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -70, 10, 0, 0.05f, 0.5f, 0.1f),
			FirstPersonClip.f(7, 40, 10, 0, 0.05f, -0.45f, 0.2f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GUARD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -40, -30, 30, -0.1f, 0.25f, 0.05f),
			FirstPersonClip.f(60, -40, -30, 30, -0.1f, 0.25f, 0.05f), FirstPersonClip.f(66, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 25, 0, 0.15f, 0.08f, -0.25f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		// the small grey disc the skin paints at the top of his chest, between the shoulder plates
		return new float[] { 0f, 1.4f, -2.1f, 2.6f };
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case CannonboltKit.ANIM_BASH -> BASH;
			case CannonboltKit.ANIM_SLAM -> SLAM;
			case CannonboltKit.ANIM_CURL -> CURL;
			case CannonboltKit.ANIM_GUARD -> GUARD;
			case CannonboltKit.ANIM_POUND -> POUND;
			case CannonboltKit.ANIM_UNCURL -> UNCURL;
			case CannonboltKit.ANIM_PUNCH -> PUNCH;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played) {
			// top-heavy: the big shoulder plates hold his arms out from his round body
			m.rightArm.zRot += 0.28f;
			m.leftArm.zRot -= 0.28f;
			m.rightArm.xRot -= 0.1f;
			m.leftArm.xRot -= 0.1f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case CannonboltKit.ANIM_BASH -> FP_BASH;
			case CannonboltKit.ANIM_SLAM, CannonboltKit.ANIM_POUND -> FP_DOWN;
			case CannonboltKit.ANIM_GUARD -> FP_GUARD;
			case CannonboltKit.ANIM_PUNCH -> FP_PUNCH;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the ball

	/** Whether {@code p} is drawn as the ball right now (synced). */
	public static boolean round(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & CannonboltKit.F_ROUND) != 0;
	}

	@Override
	public boolean customBody(AbstractClientPlayer p) {
		return round(p);
	}

	/** Per player: the roll so far (radians), the travel heading (degrees), and where the ball was last frame. */
	private static final class Roll {
		double x, z;
		float angle, yaw;
		boolean seen;
	}

	private static final Map<AbstractClientPlayer, Roll> ROLLS = new WeakHashMap<>();

	@Override
	public void renderBody(AbstractClientPlayer p, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		boolean ball = (Omnitrix.state(p).kitFlags & CannonboltKit.F_BALL) != 0;
		// a curl mid-move (standing hitbox) is drawn the size of his body's width; Ball Mode fills the ball hitbox
		float radius = ball ? p.getBbWidth() * 0.52f : Math.max(p.getBbWidth() * 0.85f, 0.62f);
		Roll r = ROLLS.computeIfAbsent(p, k -> new Roll());
		double x = Mth.lerp(partial, p.xo, p.getX()), z = Mth.lerp(partial, p.zo, p.getZ());
		if (r.seen) {
			double dx = x - r.x, dz = z - r.z, d = Math.sqrt(dx * dx + dz * dz);
			if (d > 1.0e-3 && d < 4) {
				r.yaw = (float) Math.toDegrees(Mth.atan2(dx, dz));
				r.angle += (float) (d / radius);
			}
		} else {
			r.yaw = -p.yBodyRot + 180f;
		}
		r.seen = true;
		r.x = x;
		r.z = z;
		boolean spinning = (Omnitrix.state(p).kitFlags & CannonboltKit.F_SPIN) != 0;
		float age = p.tickCount + partial;

		pose.pushPose();
		pose.translate(0, radius, 0);
		pose.mulPose(Axis.YP.rotationDegrees(spinning ? age * 50f : r.yaw));
		pose.mulPose(Axis.XP.rotation(spinning ? 0f : r.angle));
		// the shell wraps round on its side (the plate bands circle the rolling direction, like the show's ball)
		pose.mulPose(Axis.ZP.rotationDegrees(90f));
		sphere(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(BALL)), radius, light);
		pose.popPose();
	}

	private static final int SEGMENTS = 40, RINGS = 24; // v0.17.2 (user: the reference ball): smooth enough for the curved seams

	/**
	 * A UV sphere of {@code radius} blocks on the wrap texture (u round the equator, v pole to pole), every vertex lit by its own
	 * normal so the shading is smooth (no rings).
	 */
	private static void sphere(PoseStack pose, VertexConsumer vc, float radius, int light) {
		PoseStack.Pose last = pose.last();
		for (int i = 0; i < RINGS; i++) {
			float v0 = i / (float) RINGS, v1 = (i + 1) / (float) RINGS;
			float t0 = (float) Math.PI * v0, t1 = (float) Math.PI * v1;
			for (int j = 0; j < SEGMENTS; j++) {
				float u0 = j / (float) SEGMENTS, u1 = (j + 1) / (float) SEGMENTS;
				float p0 = (float) (2 * Math.PI) * u0, p1 = (float) (2 * Math.PI) * u1;
				vertex(vc, last, point(t0, p0), radius, u0, v0, light);
				vertex(vc, last, point(t0, p1), radius, u1, v0, light);
				vertex(vc, last, point(t1, p1), radius, u1, v1, light);
				vertex(vc, last, point(t1, p0), radius, u0, v1, light);
			}
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose last, float[] n, float radius, float u, float v, int light) {
		vc.addVertex(last, n[0] * radius, n[1] * radius, n[2] * radius).setColor(255, 255, 255, 255).setUv(u, v)
				.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, n[0], n[1], n[2]);
	}

	/** The unit-sphere point at polar angle {@code theta} (0 = top) and azimuth {@code phi}. */
	private static float[] point(float theta, float phi) {
		float s = Mth.sin(theta);
		return new float[] { s * Mth.cos(phi), -Mth.cos(theta), s * Mth.sin(phi) };
	}

	@Override
	public boolean renderFirstPersonHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, boolean right) {
		return true; // rolled up: no arms to show
	}
}
