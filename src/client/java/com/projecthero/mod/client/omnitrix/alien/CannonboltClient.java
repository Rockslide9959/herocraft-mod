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
import com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltKit;
import com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltRoll;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 Cannonbolt's client kit (user: "build canonbolt alien for the prototype omnitrix please. Use this skin: ...
 * planetminecraft.com/skin/cannonbolt-original-series"). Standing he is the user's skin on the player body, 2 blocks tall -- headless,
 * his face on his chest, like the show -- with a heavy, arms-out stance and a pose per move. Rolled up (Ball Mode, or curled mid-move)
 * the player model is replaced by his ball: a rounded shell of gold plates ({@code cannonbolt_ball.png}) that rolls with the
 * distance he travels, spins on the spot for the Shell Spin and the Spin Charge, and fills his 2-block ball hitbox.
 *
 * <p>v0.17.2 round 3 (user: "when player goes into ball form make an animation for them rolling and curling into the ball form please,
 * make it smooth and make sense"): every curl and uncurl runs through {@link #drawCurl}: he tucks his knees and arms, somersaults
 * forward and the shell plates close over him from his back, and uncurling plays it backwards. It is driven from synced state alone
 * ({@code F_ROUND}), so every curl of every move looks the same on every client.
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

	/** A full frame: arms, the body's lean and twist, and the legs' swing. */
	private static float[] g(float t, float rax, float raz, float lax, float laz, float bx, float by, float rlx, float llx) {
		return QuirkKeyframes.f(t, rax, 0, raz, lax, 0, laz, bx, by, rlx, 0, 0, llx, 0, 0, 0);
	}

	/** Set the feet, swing the shoulder plate round and drive through it off the back leg. */
	private static final float[][] BASH = { QuirkKeyframes.rest(0), g(3, 0.35f, 0.9f, 0.5f, -0.3f, -0.1f, 0.65f, 0.3f, -0.3f),
			g(6, -0.55f, 0.75f, 0.45f, -0.3f, 0.45f, -0.55f, -0.75f, 0.55f), g(10, -0.5f, 0.7f, 0.4f, -0.3f, 0.4f, -0.5f, -0.65f, 0.5f), QuirkKeyframes.rest(16) };
	/** Rear up with the arms overhead, then topple the whole shell forward onto it. */
	private static final float[][] SLAM = { QuirkKeyframes.rest(0), g(5, -2.8f, 0.3f, -2.8f, -0.3f, -0.3f, 0, 0.25f, -0.25f),
			g(9, -1.3f, 0.2f, -1.3f, -0.2f, 0.95f, 0, -0.75f, 0.55f), g(16, -1.25f, 0.2f, -1.25f, -0.2f, 0.9f, 0, -0.7f, 0.5f), QuirkKeyframes.rest(25) };
	/** A hunch into a crouch before the jump, knees coming up (the Curl Smash on the way up). */
	private static final float[][] CURL = { QuirkKeyframes.rest(0), g(3, -1.3f, -0.5f, -1.3f, 0.5f, 0.65f, 0, -0.9f, -0.9f), g(8, -1.3f, -0.5f, -1.3f, 0.5f, 0.65f, 0, -1.2f, -1.2f),
			QuirkKeyframes.rest(12) };
	/** Forearms locked across his chest behind the plates, a braced stance; held as long as he holds the key. */
	private static float[] guard(float t) {
		return QuirkKeyframes.f(t, -1.5f, -0.8f, 0, -1.4f, 0.8f, 0, 0.22f, 0, -0.3f, 0, 0, 0.3f, 0, 0, 0);
	}

	private static final float[][] GUARD = { QuirkKeyframes.rest(0), guard(4), guard(100000) };
	private static final float[][] GUARD_END = { guard(0), QuirkKeyframes.rest(7) };
	/** Both fists overhead, then down between his feet, knees giving under the blow. */
	private static final float[][] POUND = { QuirkKeyframes.rest(0), g(5, -3.0f, 0.25f, -3.0f, -0.25f, -0.2f, 0, 0.15f, -0.15f), g(8, -0.4f, 0.2f, -0.4f, -0.2f, 0.75f, 0, -0.5f, 0.3f),
			g(14, -0.45f, 0.2f, -0.45f, -0.2f, 0.7f, 0, -0.45f, 0.28f), QuirkKeyframes.rest(21) };
	/** Stepping out of the ball: arms thrown wide, then the weight settles. */
	private static final float[][] UNCURL = { g(0, 0.2f, 1.0f, 0.2f, -1.0f, -0.15f, 0, 0.3f, -0.3f), QuirkKeyframes.rest(9) };
	/** A wide, heavy hook off the back foot. */
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), g(2, -0.6f, 0.5f, 0.2f, 0, 0, 0.45f, 0.2f, -0.2f), g(4, -1.7f, 0, 0.3f, 0, 0.2f, -0.5f, -0.3f, 0.25f), QuirkKeyframes.rest(10) };

	private static final FirstPersonClip FP_BASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 10, -20, 0, -0.1f, -0.08f, 0.15f),
			FirstPersonClip.f(6, -15, 35, 15, 0.25f, 0.05f, -0.35f), FirstPersonClip.f(10, -12, 32, 12, 0.22f, 0.05f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_DOWN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, -75, 10, 0, 0.05f, 0.55f, 0.1f),
			FirstPersonClip.f(8, 45, 12, 0, 0.08f, -0.5f, -0.2f), FirstPersonClip.f(14, 40, 12, 0, 0.08f, -0.45f, -0.15f), FirstPersonClip.f(21, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GUARD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -45, 35, 30, 0.28f, 0.28f, -0.1f),
			FirstPersonClip.f(100000, -45, 35, 30, 0.28f, 0.28f, -0.1f)).both();
	private static final FirstPersonClip FP_GUARD_END = FirstPersonClip.of(FirstPersonClip.f(0, -45, 35, 30, 0.28f, 0.28f, -0.1f), FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 5, -15, 0, -0.08f, 0, 0.12f),
			FirstPersonClip.f(4, -22, 28, 0, 0.18f, 0.1f, -0.32f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	/** His arms wrap in across his chest and drop out of sight as the shell closes. */
	private static final FirstPersonClip FP_CURL = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -35, 40, 25, 0.3f, 0.15f, -0.05f),
			FirstPersonClip.f(9, 50, 45, 25, 0.35f, -0.7f, 0.1f), FirstPersonClip.f(12, 50, 45, 25, 0.35f, -0.7f, 0.1f)).both();
	/** Coming out of the ball, the arms swing up from below and out. */
	private static final FirstPersonClip FP_UNCURL = FirstPersonClip.of(FirstPersonClip.f(0, 50, 45, 25, 0.35f, -0.7f, 0.1f), FirstPersonClip.f(6, -10, -15, 0, -0.1f, 0.05f, 0),
			FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();

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
		if ((Omnitrix.state(p).kitFlags & CannonboltKit.F_GUARD) != 0) {
			// held as long as the key is: another clip starting over it (a punch) doesn't drop his guard
			QuirkKeyframes.play(m, GUARD, animId == CannonboltKit.ANIM_GUARD ? ticks : 50f, 1f);
			return true;
		}
		float[][] clip = switch (animId) {
			case CannonboltKit.ANIM_BASH -> BASH;
			case CannonboltKit.ANIM_SLAM -> SLAM;
			case CannonboltKit.ANIM_CURL -> CURL;
			case CannonboltKit.ANIM_GUARD_END -> GUARD_END;
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
			case CannonboltKit.ANIM_GUARD_END -> FP_GUARD_END;
			case CannonboltKit.ANIM_PUNCH -> FP_PUNCH;
			case CannonboltKit.ANIM_CURL -> FP_CURL;
			case CannonboltKit.ANIM_UNCURL -> FP_UNCURL;
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
		if (round(p)) {
			return true;
		}
		Roll r = ROLLS.get(p);
		return r != null && (r.curl > 0f || r.curlO > 0f); // still unfurling
	}

	/** Ticks of a whole curl or uncurl. */
	private static final float CURL_TICKS = 11f;
	/** The ball's radius, its hat layer's. The hat layer's outside is what makes him 2 blocks tall. */
	private static final float RADIUS = 1f / 1.07f, OUTER = 1f;

	/** One tick for everyone in view: how far curled he is, the wind-up and the spikes, and his dust. */
	@Override
	public void tick(AbstractClientPlayer p) {
		Roll r = ROLLS.computeIfAbsent(p, k -> new Roll());
		int flags = Omnitrix.state(p).kitFlags;
		r.curlO = r.curl;
		r.curl = Mth.approach(r.curl, (flags & CannonboltKit.F_ROUND) != 0 ? 1f : 0f, 1f / CURL_TICKS);
		r.spikesO = r.spikes;
		r.spikes = Mth.approach(r.spikes, (flags & CannonboltKit.F_SPIKES) != 0 ? 1f : 0f, 1f / 10f);
		boolean winding = (flags & (CannonboltKit.F_CHARGING | CannonboltKit.F_WIND)) != 0;
		r.wind = Mth.approach(r.wind, winding ? 1f : 0f, winding ? 1f / CannonboltKit.SPIN_CHARGE_FILL : 1f / 12f);
		if (p.isLocalPlayer() && (flags & CannonboltKit.F_BALL) == 0) {
			CannonboltRoll.reset();
		}
		if ((flags & CannonboltKit.F_ROUND) == 0) {
			return;
		}
		// the roll's speed, read off the motion so it is right for other players too
		double dx = p.getX() - p.xo, dz = p.getZ() - p.zo, speed = Math.sqrt(dx * dx + dz * dz);
		var l = p.level();
		if (speed > 0.75 && p.onGround()) {
			l.addParticle(ParticleTypes.CLOUD, p.getX() - dx * 0.8, p.getY() + 0.1, p.getZ() - dz * 0.8, -dx * 0.05, 0.02, -dz * 0.05);
		}
		if (speed > 1.15) {
			// top speed: the sparks off the shell and the streaks of air
			l.addParticle(ParticleTypes.CRIT, p.getX() - dx * 0.8, p.getY() + 0.2, p.getZ() - dz * 0.8, -dx * 0.4, 0.15, -dz * 0.4);
		}
		if ((flags & CannonboltKit.F_WIND) != 0) {
			double a = p.tickCount * 0.8;
			l.addParticle(ParticleTypes.CLOUD, p.getX() + Math.cos(a) * 2.2, p.getY() + 1.0, p.getZ() + Math.sin(a) * 2.2, -Math.sin(a) * 0.3, 0.05, Math.cos(a) * 0.3);
		}
	}

	/**
	 * Per player: the roll so far (radians), the heading the face points (degrees, the yaw that turns local +Z to it), where the ball
	 * was last frame and when.
	 */
	private static final class Roll {
		double x, y, z;
		float angle, yaw, age;
		boolean seen;
		/** How far curled up he is (0 standing, 1 the closed ball), and last tick's, for the lerp. */
		float curl, curlO;
		/** How far the ultimate's spikes are out, and how wound-up a spin is (both 0..1). */
		float spikes, spikesO, wind;
	}

	private static final Map<AbstractClientPlayer, Roll> ROLLS = new WeakHashMap<>();

	/**
	 * v0.17.2 (user: "the head should be used for the ball form, please make the ball form rotate and the front of the head (the face)
	 * should be the front of the ball"): the skin's head, rounded into a ball. The head's front faces where he rolls -- or, standing
	 * still, where he looks -- and the ball turns over forward with the distance it covers, settling face-up again when he stops.
	 */
	@Override
	public void renderBody(AbstractClientPlayer p, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.getVehicle() == p && mc.options.getCameraType().isFirstPerson()) {
			return; // a mate riding inside looks out through him
		}
		int flags = Omnitrix.state(p).kitFlags;
		float radius = OUTER; // every curl, in Ball Mode or mid-move, is the same 2-block ball
		Roll r = ROLLS.computeIfAbsent(p, k -> new Roll());
		double x = Mth.lerp(partial, p.xo, p.getX()), y = Mth.lerp(partial, p.yo, p.getY()), z = Mth.lerp(partial, p.zo, p.getZ());
		float age = p.tickCount + partial;
		float look = -Mth.lerp(partial, p.yRotO, p.getYRot());
		if (!r.seen) {
			r.yaw = look;
		} else {
			float dt = Mth.clamp(age - r.age, 0f, 5f);
			double dx = x - r.x, dy = y - r.y, dz = z - r.z, d = Math.sqrt(dx * dx + dz * dz);
			boolean spun = (flags & (CannonboltKit.F_CHARGING | CannonboltKit.F_WIND)) != 0 || r.wind > 0f;
			boolean rolling = d > 0.02 * Math.max(dt, 0.05f) && d < 4;
			float target = rolling ? (float) Math.toDegrees(Mth.atan2(dx, dz)) : look;
			// the face swings round to the way he rolls (quickly) or looks (gently)
			r.yaw += Mth.wrapDegrees(target - r.yaw) * (1f - (float) Math.exp(-dt * (rolling ? 0.9f : 0.35f)));
			if (d < 4) {
				// a flight turns him over too, the Rebound Strike's and the Rampage's alike
				r.angle += (float) (Math.sqrt(d * d + (rolling ? 0 : dy * dy)) / radius);
			}
			if (spun) {
				r.angle += dt * (0.25f + 1.3f * r.wind); // wheels spinning on the spot
			} else if (!rolling) {
				// stopped: the ball rocks back so his face is at the front again
				float upright = (float) (Math.round(r.angle / (2 * Math.PI)) * 2 * Math.PI);
				r.angle += (upright - r.angle) * (1f - (float) Math.exp(-dt * 0.25f));
			}
		}
		r.seen = true;
		r.x = x;
		r.y = y;
		r.z = z;
		r.age = age;
		boolean spinning = (flags & CannonboltKit.F_SPIN) != 0;
		float wind = r.wind;

		pose.pushPose();
		pose.translate(0, radius, 0);
		if (wind > 0f) {
			// the wind-up strains at the ground
			pose.translate(Mth.sin(age * 2.7f) * 0.025f * wind, Math.abs(Mth.sin(age * 1.9f)) * 0.03f * wind, Mth.cos(age * 3.1f) * 0.025f * wind);
		}
		pose.mulPose(Axis.YP.rotationDegrees(spinning ? r.yaw + age * 50f : r.yaw));
		float c = Mth.lerp(partial, r.curlO, r.curl);
		if (c >= 0.999f) {
			pose.mulPose(Axis.XP.rotation(spinning ? 0f : r.angle));
			shell(pose, buffers, -2f, light);
			float s = Mth.lerp(partial, r.spikesO, r.spikes);
			if (s > 0f) {
				spikes(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(BALL)), s, light);
			}
		} else {
			drawCurl(p, pose, buffers, ease(c), r.angle, light);
		}
		pose.popPose();
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private static float stage(float t, float from, float to) {
		return ease((t - from) / (to - from));
	}

	/** The head layer and the hat layer; {@code cap} as in {@link #headBall}. */
	private static void shell(PoseStack pose, MultiBufferSource buffers, float cap, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(BALL));
		int n = cap < -1.5f ? N : N * 2; // the closing edge wants a finer grid than the finished ball
		headBall(pose, vc, RADIUS, 0, light, cap, n); // the head
		headBall(pose, vc, OUTER, 32, light, cap, n); // its hat layer, just outside
	}

	private static PlayerModel<AbstractClientPlayer> figure;

	/** The body of the curl: drawn by hand so it can somersault as a whole, which the player model's own transforms can't. */
	private static PlayerModel<AbstractClientPlayer> figure() {
		if (figure == null) {
			figure = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
			figure.head.visible = false;
			figure.hat.visible = false;
		}
		return figure;
	}

	/**
	 * Halfway between standing and the ball ({@code t}, 0 standing, 1 closed), called with the pose at the ball's centre, turned to the
	 * way he faces. Three things overlap: he tucks (knees to his chest, arms round them), he somersaults forward, shrinking into the
	 * space inside the shell, and the shell's plates sweep round from his back to close in front of him, the face last. The turn
	 * ends where the ball's own roll picks up, so nothing jumps; uncurling is the same thing played backwards.
	 */
	private void drawCurl(AbstractClientPlayer p, PoseStack pose, MultiBufferSource buffers, float t, float roll, int light) {
		float tuck = stage(t, 0f, 0.5f), over = stage(t, 0.15f, 1f), close = stage(t, 0.3f, 1f);
		pose.pushPose();
		pose.mulPose(Axis.XP.rotation(roll + (over - 1f) * Mth.TWO_PI));
		pose.pushPose();
		float shrink = Mth.lerp(over, 1f, 0.62f);
		pose.scale(shrink, shrink, shrink);
		pose.translate(0, -Mth.lerp(tuck, 1f, 1.22f), 0); // to his feet, from a pivot that rises as his legs come up
		pose.mulPose(Axis.YP.rotationDegrees(180f));
		pose.scale(-1f, -1f, 1f);
		float s = p.getScale() * 0.9375f;
		pose.scale(s, s, s);
		pose.translate(0, -1.501f, 0);
		PlayerModel<AbstractClientPlayer> m = figure();
		m.body.xRot = 0.55f * tuck;
		m.body.setPos(0, 0, 0);
		m.rightArm.setPos(-5f, 2f + 1.5f * tuck, -1.5f * tuck);
		m.leftArm.setPos(5f, 2f + 1.5f * tuck, -1.5f * tuck);
		m.rightArm.setRotation(-1.7f * tuck, 0, -0.1f - 0.45f * tuck + 0.28f * (1f - tuck));
		m.leftArm.setRotation(-1.7f * tuck, 0, 0.1f + 0.45f * tuck - 0.28f * (1f - tuck));
		m.rightLeg.setPos(-1.9f, 12f - 5f * tuck, -3f * tuck);
		m.leftLeg.setPos(1.9f, 12f - 5f * tuck, -3f * tuck);
		m.rightLeg.setRotation(-2.0f * tuck, 0, 0.1f * tuck);
		m.leftLeg.setRotation(-2.0f * tuck, 0, -0.1f * tuck);
		m.jacket.copyFrom(m.body);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightPants.copyFrom(m.rightLeg);
		m.leftPants.copyFrom(m.leftLeg);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN)), light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
		if (close > 0f) {
			shell(pose, buffers, Mth.cos(close * Mth.PI), light);
		}
		pose.popPose();
	}

	/** Where the spikes stand on the ball: evenly round it (a Fibonacci sphere). */
	private static final float[][] SPIKES = new float[34][];
	static {
		float gold = Mth.PI * (3f - Mth.sqrt(5f));
		for (int i = 0; i < SPIKES.length; i++) {
			float y = 1f - 2f * (i + 0.5f) / SPIKES.length, ring = Mth.sqrt(1f - y * y);
			SPIKES[i] = new float[] { Mth.cos(i * gold) * ring, y, Mth.sin(i * gold) * ring };
		}
	}

	/** Ultimate Cannonbolt's spikes, {@code out} of the way out: a four-sided cone off the shell at each point. */
	private static void spikes(PoseStack pose, VertexConsumer vc, float out, int light) {
		PoseStack.Pose last = pose.last();
		float len = 0.55f * out, base = 0.17f;
		for (float[] d : SPIKES) {
			float ax = Math.abs(d[1]) > 0.9f ? 1f : 0f, ay = 1f - ax;
			// two tangents of the sphere here
			float ux = d[1] * 0f - d[2] * ay, uy = d[2] * ax, uz = d[0] * ay - d[1] * ax;
			float ul = Mth.sqrt(ux * ux + uy * uy + uz * uz);
			ux /= ul;
			uy /= ul;
			uz /= ul;
			float vx = d[1] * uz - d[2] * uy, vy = d[2] * ux - d[0] * uz, vz = d[0] * uy - d[1] * ux;
			float[][] ring = new float[4][];
			for (int i = 0; i < 4; i++) {
				float a = i == 0 || i == 3 ? 1f : -1f, b = i < 2 ? 1f : -1f;
				ring[i] = new float[] { d[0] * RADIUS + (ux * a + vx * b) * base, d[1] * RADIUS + (uy * a + vy * b) * base, d[2] * RADIUS + (uz * a + vz * b) * base };
			}
			float tx = d[0] * (OUTER + len), ty = d[1] * (OUTER + len), tz = d[2] * (OUTER + len);
			for (int i = 0; i < 4; i++) {
				float[] e = ring[i], f = ring[(i + 1) % 4];
				float mx = e[0] + f[0] - 2f * tx * 0f, my = e[1] + f[1], mz = e[2] + f[2];
				float ml = Mth.sqrt(mx * mx + my * my + mz * mz);
				spikeCorner(vc, last, e, mx / ml, my / ml, mz / ml, light, 0xC8);
				spikeCorner(vc, last, f, mx / ml, my / ml, mz / ml, light, 0xC8);
				spikeCorner(vc, last, new float[] { tx, ty, tz }, mx / ml, my / ml, mz / ml, light, 0xFF);
				spikeCorner(vc, last, new float[] { tx, ty, tz }, mx / ml, my / ml, mz / ml, light, 0xFF);
			}
		}
	}

	private static void spikeCorner(VertexConsumer vc, PoseStack.Pose last, float[] at, float nx, float ny, float nz, int light, int shade) {
		vc.addVertex(last, at[0], at[1], at[2]).setColor(shade, shade, shade, 255).setUv(12.5f / 64f, 4.5f / 64f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(last, nx, ny, nz);
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
	 * so every texel keeps its place and the faces meet seamlessly. {@code du} shifts the texture to the hat layer (+32); {@code n} is
	 * the grid; {@code cap} below -1.5 draws the whole ball, otherwise only the part of it {@code cap} (a cosine) round his back.
	 */
	private static void headBall(PoseStack pose, VertexConsumer vc, float radius, int du, int light, float cap, int n) {
		PoseStack.Pose last = pose.last();
		for (float[] f : FACES) {
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					float s0 = -1f + 2f * j / n, s1 = -1f + 2f * (j + 1) / n, t0 = -1f + 2f * i / n, t1 = -1f + 2f * (i + 1) / n;
					if (cap > -1.5f) {
						// the plates still closing: only the cap round his back (-Z), out to the angle whose cosine is {@code cap}
						float sm = (s0 + s1) * 0.5f, tm = (t0 + t1) * 0.5f;
						float bx = f[0] + sm * f[3] + tm * f[6], by = f[1] + sm * f[4] + tm * f[7], bz = f[2] + sm * f[5] + tm * f[8];
						if (-bz / Mth.sqrt(bx * bx + by * by + bz * bz) < cap) {
							continue;
						}
					}
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

	/**
	 * Rolled up he has no arms to show: what he sees of himself is the top of his own shell turning over under his view, its speed the
	 * roll's, so every move in the ball reads in first person too. Until the shell has closed the arms are still there, playing the
	 * curl's clip.
	 */
	@Override
	public boolean renderFirstPersonHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, boolean right) {
		Roll r = ROLLS.get(p);
		if (r == null || r.curl < 0.65f) {
			return false;
		}
		if (right) {
			pose.pushPose();
			pose.last().pose().set(OmnitrixFirstPerson.HAND_BASE);
			pose.last().normal().set(OmnitrixFirstPerson.HAND_BASE_NORMAL);
			float rise = ease((r.curl - 0.65f) / 0.35f);
			pose.translate(0, -1.62f + 0.32f * rise, -0.95f);
			pose.mulPose(Axis.XP.rotation(r.angle));
			float out = Math.max(0f, r.spikes);
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(BALL));
			headBall(pose, vc, 0.84f, 0, light, -2f, N);
			headBall(pose, vc, 0.9f, 32, light, -2f, N);
			if (out > 0f) {
				pose.scale(0.9f, 0.9f, 0.9f);
				spikes(pose, vc, out * 0.55f, light);
			}
			pose.popPose();
		}
		return true;
	}
}
