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
		return new float[] { 0f, 1.3f, -2.1f, 2.6f }; // v0.17.2 (user: "raise our badge by like 0.1 pixel please")
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

	/**
	 * Per player: the roll so far (radians), the heading the face points (degrees, the yaw that turns local +Z to it), where the ball
	 * was last frame and when.
	 */
	private static final class Roll {
		double x, z;
		float angle, yaw, age;
		boolean seen;
	}

	private static final Map<AbstractClientPlayer, Roll> ROLLS = new WeakHashMap<>();

	/**
	 * v0.17.2 (user: "the head should be used for the ball form, please make the ball form rotate and the front of the head (the face)
	 * should be the front of the ball"): the skin's head, rounded into a ball. The head's front faces where he rolls -- or, standing
	 * still, where he looks -- and the ball turns over forward with the distance it covers, settling face-up again when he stops.
	 */
	@Override
	public void renderBody(AbstractClientPlayer p, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		boolean ball = (Omnitrix.state(p).kitFlags & CannonboltKit.F_BALL) != 0;
		// a curl mid-move (standing hitbox) is drawn the size of his body's width; Ball Mode fills the ball hitbox
		float radius = ball ? p.getBbHeight() * 0.5f : Math.max(p.getBbWidth() * 0.85f, 0.62f); // v0.17.2: Ball Mode is 1.75 blocks tall
		Roll r = ROLLS.computeIfAbsent(p, k -> new Roll());
		double x = Mth.lerp(partial, p.xo, p.getX()), z = Mth.lerp(partial, p.zo, p.getZ());
		float age = p.tickCount + partial;
		float look = -Mth.lerp(partial, p.yRotO, p.getYRot());
		if (!r.seen) {
			r.yaw = look;
		} else {
			float dt = Mth.clamp(age - r.age, 0f, 5f);
			double dx = x - r.x, dz = z - r.z, d = Math.sqrt(dx * dx + dz * dz);
			boolean rolling = d > 0.02 * Math.max(dt, 0.05f) && d < 4;
			float target = rolling ? (float) Math.toDegrees(Mth.atan2(dx, dz)) : look;
			// the face swings round to the way he rolls (quickly) or looks (gently)
			r.yaw += Mth.wrapDegrees(target - r.yaw) * (1f - (float) Math.exp(-dt * (rolling ? 0.9f : 0.35f)));
			if (d < 4) {
				r.angle += (float) (d / radius);
			}
			if (!rolling) {
				// stopped: the ball rocks back so his face is at the front again
				float upright = (float) (Math.round(r.angle / (2 * Math.PI)) * 2 * Math.PI);
				r.angle += (upright - r.angle) * (1f - (float) Math.exp(-dt * 0.25f));
			}
		}
		r.seen = true;
		r.x = x;
		r.z = z;
		r.age = age;
		boolean spinning = (Omnitrix.state(p).kitFlags & CannonboltKit.F_SPIN) != 0;

		pose.pushPose();
		pose.translate(0, radius, 0);
		pose.mulPose(Axis.YP.rotationDegrees(spinning ? r.yaw + age * 50f : r.yaw));
		pose.mulPose(Axis.XP.rotation(spinning ? 0f : r.angle));
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(BALL));
		headBall(pose, vc, radius, 0, light); // the head
		headBall(pose, vc, radius * 1.07f, 32, light); // its hat layer, just outside
		pose.popPose();
	}

	/** Cube-sphere subdivisions per head face (each of the head's 8x8 faces spread over N x N quads). */
	private static final int N = 12;

	/**
	 * One head face: its outward normal, the way its texture's u runs and the way its v runs (down the texture), and the face's 8x8
	 * texel corner on the 64x64 skin -- for a head facing +Z (the ball's front), its right hand at -X.
	 */
	private static final float[][] FACES = {
			{ 0, 0, 1, 1, 0, 0, 0, -1, 0, 8, 8 }, // front (the face)
			{ 0, 0, -1, -1, 0, 0, 0, -1, 0, 24, 8 }, // back
			{ -1, 0, 0, 0, 0, 1, 0, -1, 0, 0, 8 }, // his right side
			{ 1, 0, 0, 0, 0, -1, 0, -1, 0, 16, 8 }, // his left side
			{ 0, 1, 0, 1, 0, 0, 0, 0, 1, 8, 0 }, // top (its bottom edge meets the front)
			{ 0, -1, 0, 1, 0, 0, 0, 0, -1, 16, 0 }, // bottom (its top edge meets the front)
	};

	/**
	 * The head's six faces wrapped round a ball of {@code radius} blocks: each face's grid pushed out onto the sphere (a cube-sphere),
	 * so every texel keeps its place and the faces meet seamlessly. {@code du} shifts the texture to the hat layer (+32).
	 */
	private static void headBall(PoseStack pose, VertexConsumer vc, float radius, int du, int light) {
		PoseStack.Pose last = pose.last();
		for (float[] f : FACES) {
			for (int i = 0; i < N; i++) {
				for (int j = 0; j < N; j++) {
					float s0 = -1f + 2f * j / N, s1 = -1f + 2f * (j + 1) / N, t0 = -1f + 2f * i / N, t1 = -1f + 2f * (i + 1) / N;
					corner(vc, last, f, s0, t0, radius, du, light);
					corner(vc, last, f, s1, t0, radius, du, light);
					corner(vc, last, f, s1, t1, radius, du, light);
					corner(vc, last, f, s0, t1, radius, du, light);
				}
			}
		}
	}

	private static void corner(VertexConsumer vc, PoseStack.Pose last, float[] f, float s, float t, float radius, int du, int light) {
		float px = f[0] + s * f[3] + t * f[6], py = f[1] + s * f[4] + t * f[7], pz = f[2] + s * f[5] + t * f[8];
		float len = Mth.sqrt(px * px + py * py + pz * pz);
		px /= len;
		py /= len;
		pz /= len;
		float u = (f[9] + du + (s + 1f) * 4f) / 64f, v = (f[10] + (t + 1f) * 4f) / 64f;
		vc.addVertex(last, px * radius, py * radius, pz * radius).setColor(255, 255, 255, 255).setUv(u, v)
				.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, px, py, pz);
	}

	@Override
	public boolean renderFirstPersonHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, boolean right) {
		return true; // rolled up: no arms to show
	}
}
