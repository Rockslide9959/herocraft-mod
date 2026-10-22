package com.projecthero.mod.client.ironman;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.network.IronManPosePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.26: Iron Man attack animations. The server says which ability a player is using ({@link IronManPosePayload});
 * this keeps the latest one per player and poses the model for it -- after the flight pose, so a move made mid-air
 * overrides the flight arms. Every pose eases in over 2 ticks and out over its last 2.
 * <ul>
 *   <li>Repulsor: the right palm thrown out at the crosshair with a recoil kick; Charged: both palms, a bigger kick.</li>
 *   <li>Charging: right palm aimed, left hand bracing the right wrist.</li>
 *   <li>Shield: both palms forward, spread. Unibeam: arms swept back and down, chest out.</li>
 *   <li>Flamethrower / wrist laser: the right arm aimed and held. Rocket: aim with the right, left arm back.</li>
 *   <li>Strong punch: a wind-up then a full-reach straight right with a body twist.</li>
 *   <li>Flare: both arms thrown up and out. Micro-missiles: arms out from the sides, shoulders open (the pods).</li>
 * </ul>
 */
public final class IronManAbilityPose {
	/** anim, start game time (with the partial added at read), length. */
	record Play(int anim, long start, int ticks) {
	}

	private static final Map<Integer, Play> PLAYS = new HashMap<>();

	private IronManAbilityPose() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(IronManPosePayload.TYPE, (payload, context) -> context.client().execute(() -> {
			Minecraft mc = context.client();
			if (mc.level == null) {
				return;
			}
			long now = mc.level.getGameTime();
			if (IronManAbilityFx.isFallLanding(payload.anim())) {
				// v0.16.7 (user): a long fall landed -- the superhero landing pose (scaled), not an ability pose
				IronManFlightPose.onFallLanding(mc.level, payload.playerId(), payload.anim() - IronManAbilityFx.IM_LAND_BASE);
				return;
			}
			Play prev = PLAYS.get(payload.playerId());
			// a held ability refreshing itself keeps its original start (so the ease-in doesn't restart)
			long start = prev != null && prev.anim() == payload.anim() && now <= prev.start() + prev.ticks() ? prev.start() : now;
			PLAYS.put(payload.playerId(), new Play(payload.anim(), start, (int) (now - start) + payload.ticks()));
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> PLAYS.clear());
	}

	/** The ability {@code player} is animating right now and how far into it (ticks, with partial), or null. */
	public static Play current(Player player) {
		Minecraft mc = Minecraft.getInstance();
		Play p = PLAYS.get(player.getId());
		if (p == null || mc.level == null) {
			return null;
		}
		if (mc.level.getGameTime() > p.start() + p.ticks()) {
			PLAYS.remove(player.getId());
			return null;
		}
		return p;
	}

	public static float age(Play p) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0f : mc.level.getGameTime() - p.start() + mc.getTimer().getGameTimeDeltaPartialTick(false);
	}

	public static void apply(Player player, HumanoidModel<?> m, float ageInTicks) {
		Play p = current(player);
		if (p == null) {
			return;
		}
		float t = age(p);
		float w = Mth.clamp(Math.min(t / 2f, (p.ticks() - t) / 2f), 0f, 1f);
		if (w <= 0f) {
			return;
		}
		float aimX = -Mth.HALF_PI + m.head.xRot;
		float aimY = m.head.yRot;
		switch (p.anim()) {
			case IronManAbilityFx.REPULSOR -> {
				float kick = t < 4 ? (4 - t) / 4f * 0.35f : 0f;
				set(m.rightArm, w, aimX - kick, aimY - 0.05f, 0f);
			}
			case IronManAbilityFx.CHARGED -> {
				float kick = t < 6 ? (6 - t) / 6f * 0.55f : 0f;
				set(m.rightArm, w, aimX - kick, aimY - 0.18f, 0f);
				set(m.leftArm, w, aimX - kick, aimY + 0.18f, 0f);
				com.projecthero.mod.client.HipBend.pitch(m, w, -kick * 0.3f);
			}
			case IronManAbilityFx.CHARGING -> {
				float tremble = Mth.sin(ageInTicks * 2.1f) * 0.02f;
				set(m.rightArm, w, aimX + tremble, aimY - 0.05f, 0f);
				set(m.leftArm, w, aimX + 0.15f, aimY + 0.55f, 0f);
			}
			case IronManAbilityFx.BARRIER -> {
				set(m.rightArm, w, aimX - 0.1f, aimY - 0.38f, 0f);
				set(m.leftArm, w, aimX - 0.1f, aimY + 0.38f, 0f);
			}
			case IronManAbilityFx.UNIBEAM -> {
				set(m.rightArm, w, 0.55f, 0f, 0.45f);
				set(m.leftArm, w, 0.55f, 0f, -0.45f);
				com.projecthero.mod.client.HipBend.pitch(m, w, -0.12f);
			}
			case IronManAbilityFx.FLAME, IronManAbilityFx.LASER -> set(m.rightArm, w, aimX, aimY - 0.05f, 0f);
			case IronManAbilityFx.ROCKET -> {
				set(m.rightArm, w, aimX, aimY - 0.05f, 0f);
				set(m.leftArm, w, 0.6f, 0f, -0.2f);
			}
			case IronManAbilityFx.PUNCH -> {
				if (t < 3f) {
					set(m.rightArm, w, 0.9f, 0.3f, 0f);
					m.body.yRot = Mth.lerp(w, m.body.yRot, 0.35f);
				} else {
					float k = Mth.clamp((t - 3f) / 2f, 0f, 1f);
					set(m.rightArm, w, Mth.lerp(k, 0.9f, aimX), Mth.lerp(k, 0.3f, aimY - 0.1f), 0f);
					m.body.yRot = Mth.lerp(w, m.body.yRot, Mth.lerp(k, 0.35f, -0.3f));
				}
			}
			case IronManAbilityFx.FLARE -> {
				set(m.rightArm, w, -0.5f, 0f, 1.5f);
				set(m.leftArm, w, -0.5f, 0f, -1.5f);
			}
			case IronManAbilityFx.MINIGUN -> {
				// v0.14.27: Mark III shoulder miniguns -- both forearms braced forward on the aim line, rattling with recoil
				float recoil = Mth.sin(ageInTicks * 3.3f) * 0.04f;
				set(m.rightArm, w, aimX + recoil, aimY - 0.12f, 0f);
				set(m.leftArm, w, aimX - recoil, aimY + 0.12f, 0f);
				com.projecthero.mod.client.HipBend.pitch(m, w, 0.04f);
			}
			case IronManAbilityFx.MISSILES -> {
				set(m.rightArm, w, 0.25f, 0f, 0.55f);
				set(m.leftArm, w, 0.25f, 0f, -0.55f);
				com.projecthero.mod.client.HipBend.pitch(m, w, -0.05f);
			}
			case IronManAbilityFx.DASH -> {
				// v0.14.27: palms thrown back like thrusters, body leaning hard into the dash
				set(m.rightArm, w, 0.45f, 0f, 0.35f);
				set(m.leftArm, w, 0.45f, 0f, -0.35f);
				com.projecthero.mod.client.HipBend.pitch(m, w, 0.35f);
			}
			case IronManAbilityFx.SONIC_CLAP -> {
				// v0.14.27: arms flung wide, then slammed together in front
				if (t < 4f) {
					set(m.rightArm, w, aimX, 0f, 1.25f);
					set(m.leftArm, w, aimX, 0f, -1.25f);
				} else {
					float k = Mth.clamp((t - 4f) / 2f, 0f, 1f);
					set(m.rightArm, w, aimX, aimY + Mth.lerp(k, -0.9f, 0.12f), Mth.lerp(k, 1.25f, 0f));
					set(m.leftArm, w, aimX, aimY + Mth.lerp(k, 0.9f, -0.12f), Mth.lerp(k, -1.25f, 0f));
				}
			}
			case IronManAbilityFx.GROUND_POUND -> {
				// v0.15.11: Mark 1 Shift+R -- both fists flung up overhead, then hammered down to the ground, body hunched over
				if (t < 2f) {
					set(m.rightArm, w, -2.9f, 0f, 0.15f);
					set(m.leftArm, w, -2.9f, 0f, -0.15f);
				} else {
					float k = Mth.clamp((t - 2f) / 2f, 0f, 1f);
					set(m.rightArm, w, Mth.lerp(k, -2.9f, -0.35f), 0f, Mth.lerp(k, 0.15f, 0.05f));
					set(m.leftArm, w, Mth.lerp(k, -2.9f, -0.35f), 0f, Mth.lerp(k, -0.15f, -0.05f));
					com.projecthero.mod.client.HipBend.pitch(m, w, 0.25f * k);
					m.head.xRot = Mth.lerp(w * k, m.head.xRot, 0.35f);
				}
			}
			// ---------------- v0.16.1 comic kit poses
			case IronManAbilityFx.COMIC_MAGNET -> {
				// both palms thrust forward and spread, fingers wide (magnets, pressor, tractor, gravity)
				set(m.rightArm, w, aimX - 0.05f, aimY - 0.45f, 0f);
				set(m.leftArm, w, aimX - 0.05f, aimY + 0.45f, 0f);
				com.projecthero.mod.client.HipBend.pitch(m, w, -0.06f);
			}
			case IronManAbilityFx.COMIC_LAUNCH -> {
				// a crouch, then both arms flung down and back like thrusters
				float k = Mth.clamp(t / 4f, 0f, 1f);
				set(m.rightArm, w, Mth.lerp(k, -0.4f, 0.6f), 0f, Mth.lerp(k, 0.1f, 0.35f));
				set(m.leftArm, w, Mth.lerp(k, -0.4f, 0.6f), 0f, Mth.lerp(k, -0.1f, -0.35f));
				com.projecthero.mod.client.HipBend.pitch(m, w, Mth.lerp(k, 0.3f, -0.05f));
			}
			case IronManAbilityFx.COMIC_GLIDE -> {
				// arms spread like wings, body pitched forward
				set(m.rightArm, w, 0.1f, 0f, 1.45f);
				set(m.leftArm, w, 0.1f, 0f, -1.45f);
				com.projecthero.mod.client.HipBend.pitch(m, w, 0.25f);
			}
			case IronManAbilityFx.COMIC_SPIN -> {
				// a full turn with the right leg kicked out
				float k = Mth.clamp(t / 10f, 0f, 1f);
				m.body.yRot = Mth.lerp(w, m.body.yRot, k * Mth.TWO_PI);
				set(m.rightLeg, w, -1.2f, 0f, 0.6f);
				set(m.rightArm, w, 0f, 0f, 1.2f);
				set(m.leftArm, w, 0f, 0f, -1.2f);
			}
			case IronManAbilityFx.COMIC_GUARD -> {
				// forearms crossed in front of the face
				set(m.rightArm, w, -1.75f, 0.55f, 0f);
				set(m.leftArm, w, -1.75f, -0.55f, 0f);
				com.projecthero.mod.client.HipBend.pitch(m, w, 0.1f);
			}
			case IronManAbilityFx.COMIC_GRAB -> {
				// both hands out at chest height, holding something big
				set(m.rightArm, w, -1.35f, -0.25f, 0f);
				set(m.leftArm, w, -1.35f, 0.25f, 0f);
			}
			case IronManAbilityFx.COMIC_THROW -> {
				// wound back overhead, then whipped forward
				float k = Mth.clamp((t - 2f) / 3f, 0f, 1f);
				set(m.rightArm, w, Mth.lerp(k, -2.8f, aimX + 0.2f), aimY, 0f);
				m.body.yRot = Mth.lerp(w, m.body.yRot, Mth.lerp(k, 0.4f, -0.3f));
			}
			case IronManAbilityFx.COMIC_SKY -> {
				// the right arm straight up at the sky, the left at the hip
				set(m.rightArm, w, -3.0f, 0f, 0.1f);
				set(m.leftArm, w, 0.2f, 0f, -0.3f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, -0.6f);
			}
			case IronManAbilityFx.COMIC_DIVE -> {
				// arms swept back along the body, head down, a falling meteor
				set(m.rightArm, w, 0.4f, 0f, 0.2f);
				set(m.leftArm, w, 0.4f, 0f, -0.2f);
				com.projecthero.mod.client.HipBend.pitch(m, w, 0.45f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, 0.6f);
			}
			case IronManAbilityFx.COMIC_STOMP -> {
				// the right knee lifted high, then stamped down
				float k = Mth.clamp((t - 4f) / 3f, 0f, 1f);
				set(m.rightLeg, w, Mth.lerp(k, -1.4f, 0.1f), 0f, 0f);
				set(m.rightArm, w, -0.3f, 0f, 0.6f);
				set(m.leftArm, w, -0.3f, 0f, -0.6f);
			}
			case IronManAbilityFx.COMIC_POUND -> {
				// alternating hammer fists
				boolean right = ((int) (t / 4f)) % 2 == 0;
				float k = (t % 4f) / 4f;
				set(right ? m.rightArm : m.leftArm, w, Mth.lerp(k, -2.6f, -0.6f), 0f, 0f);
				set(right ? m.leftArm : m.rightArm, w, -1.2f, 0f, 0f);
			}
			case IronManAbilityFx.COMIC_FLARE -> {
				// arms flung out to the sides, chest thrust forward
				set(m.rightArm, w, 0.2f, 0f, 1.2f);
				set(m.leftArm, w, 0.2f, 0f, -1.2f);
				com.projecthero.mod.client.HipBend.pitch(m, w, -0.15f);
			}
			// ---------------- v0.16.7 (user) Era II C rework: the new attacks
			case IronManAbilityFx.COMIC_LASER -> {
				// Mk VI Wrist Laser: the right arm locked straight out on the aim, the left hand clamped round the right
				// wrist to steady it, shoulders squared, a small kick as it fires
				float kick = t < 3 ? (3 - t) / 3f * 0.12f : 0f;
				set(m.rightArm, w, aimX - kick, aimY - 0.08f, 0f);
				set(m.leftArm, w, aimX + 0.25f, aimY + 0.62f, 0f);
				m.body.yRot = Mth.lerp(w, m.body.yRot, -0.22f);
				com.projecthero.mod.client.HipBend.pitch(m, w, 0.05f);
			}
			case IronManAbilityFx.COMIC_BLADE_DEPLOY -> {
				// Mk VII Deploy Blades: both forearms snapped up in front, fists clenched (0-4), then flicked out and down to
				// the sides as the blades slide out (4-10), head dipped
				if (t < 4f) {
					float k = Mth.clamp(t / 2f, 0f, 1f);
					set(m.rightArm, w, Mth.lerp(k, 0f, -1.55f), 0.45f, 0f);
					set(m.leftArm, w, Mth.lerp(k, 0f, -1.55f), -0.45f, 0f);
					m.head.xRot = Mth.lerp(w, m.head.xRot, 0.2f);
				} else {
					float k = Mth.clamp((t - 4f) / 3f, 0f, 1f);
					float ease = k * k * (3f - 2f * k);
					set(m.rightArm, w, Mth.lerp(ease, -1.55f, -0.35f), Mth.lerp(ease, 0.45f, 0f), Mth.lerp(ease, 0f, 0.55f));
					set(m.leftArm, w, Mth.lerp(ease, -1.55f, -0.35f), Mth.lerp(ease, -0.45f, 0f), Mth.lerp(ease, 0f, -0.55f));
					com.projecthero.mod.client.HipBend.pitch(m, w, 0.1f * ease);
				}
			}
			case IronManAbilityFx.COMIC_BLADE_WHIRL -> {
				// Mk VII Blade Whirl: a low lunge with both blades drawn back (0-4), then the body whipped round a full turn
				// with both arms flung out level, blades leading (4-12), then settling
				if (t < 4f) {
					float k = Mth.clamp(t / 3f, 0f, 1f);
					set(m.rightArm, w, Mth.lerp(k, 0f, 0.6f), 0.3f, 0.35f);
					set(m.leftArm, w, Mth.lerp(k, 0f, -1.3f), -0.4f, -0.1f);
					com.projecthero.mod.client.HipBend.pitch(m, w, 0.35f * k);
					set(m.rightLeg, w * k, 0.45f, 0f, 0.05f);
					set(m.leftLeg, w * k, -0.55f, 0f, -0.05f);
				} else {
					// two scything slashes (the server's two hits at 5 and 10): arms flung out level, the torso and both
					// blades swept hard one way then back the other (a sine twist, the arms leading the body)
					float k = Mth.clamp((t - 4f) / 8f, 0f, 1f);
					float twist = Mth.sin(k * Mth.TWO_PI - Mth.HALF_PI) * -0.75f;
					m.body.yRot = Mth.lerp(w, m.body.yRot, twist);
					set(m.rightArm, w, -0.2f, twist * 1.3f, 1.45f);
					set(m.leftArm, w, -0.2f, twist * 1.3f, -1.45f);
					com.projecthero.mod.client.HipBend.pitch(m, w, 0.18f * (1f - k));
					set(m.rightLeg, w * (1f - k), 0.3f, 0f, 0.12f);
					set(m.leftLeg, w * (1f - k), -0.3f, 0f, -0.12f);
				}
			}
			case IronManAbilityFx.COMIC_SINGULARITY -> {
				// Mk VIII Singularity: the right hand cupped high behind the shoulder holding the orb, the left arm pointing
				// at the spot (0-3), then an overhand pitch through the aim with the body turning into it
				float k = Mth.clamp((t - 3f) / 3f, 0f, 1f);
				set(m.rightArm, w, Mth.lerp(k, -2.9f, aimX + 0.35f), Mth.lerp(k, 0.35f, aimY), Mth.lerp(k, 0.25f, 0f));
				set(m.leftArm, w, Mth.lerp(k, aimX, 0.3f), Mth.lerp(k, aimY + 0.2f, 0f), Mth.lerp(k, 0f, -0.2f));
				m.body.yRot = Mth.lerp(w, m.body.yRot, Mth.lerp(k, 0.45f, -0.3f));
				com.projecthero.mod.client.HipBend.pitch(m, w, Mth.lerp(k, -0.1f, 0.2f));
			}
			case IronManAbilityFx.MK9_LASH -> {
				// Mk IX Molten Lash: the right arm cocked high and back over the shoulder (0-4), then whipped down and
				// across the body to the left, the body twisting with it, the left arm thrown back for balance
				float k = Mth.clamp((t - 4f) / 2.5f, 0f, 1f);
				float ease = 1f - (1f - k) * (1f - k);
				set(m.rightArm, w, Mth.lerp(ease, -2.7f, -0.9f), Mth.lerp(ease, 0.6f, -0.9f), Mth.lerp(ease, 0.4f, 0.15f));
				set(m.leftArm, w, Mth.lerp(ease, -0.6f, 0.5f), 0f, Mth.lerp(ease, -0.3f, -0.5f));
				m.body.yRot = Mth.lerp(w, m.body.yRot, Mth.lerp(ease, 0.55f, -0.5f));
				com.projecthero.mod.client.HipBend.pitch(m, w, Mth.lerp(ease, -0.1f, 0.18f));
			}
			// ---------------- v0.16.7 (user) Era II stage 3: the Mk IX Inferno
			case IronManAbilityFx.MK9_FIRE_BLAST -> {
				// right palm punched out on the aim with a hard recoil, the left hand pulled back to the hip
				float kick = t < 4 ? (4 - t) / 4f * 0.5f : 0f;
				set(m.rightArm, w, aimX - kick, aimY - 0.05f, 0f);
				set(m.leftArm, w, 0.55f, 0f, -0.25f);
				m.body.yRot = Mth.lerp(w, m.body.yRot, -0.15f + kick * 0.2f);
				com.projecthero.mod.client.HipBend.pitch(m, w, -kick * 0.2f);
			}
			case IronManAbilityFx.MK9_MAGMA_FIST -> {
				if (t < 3f) {
					// wound back low, body turned, the left forearm up as a guard
					set(m.rightArm, w, 0.85f, 0.4f, 0.2f);
					set(m.leftArm, w, -1.2f, -0.3f, 0f);
					m.body.yRot = Mth.lerp(w, m.body.yRot, 0.45f);
				} else {
					// a rising straight through the target, the body whipped round
					float k = Mth.clamp((t - 3f) / 2f, 0f, 1f);
					set(m.rightArm, w, Mth.lerp(k, 0.85f, aimX - 0.25f), Mth.lerp(k, 0.4f, aimY - 0.1f), Mth.lerp(k, 0.2f, 0f));
					set(m.leftArm, w, Mth.lerp(k, -1.2f, 0.4f), Mth.lerp(k, -0.3f, 0f), Mth.lerp(k, 0f, -0.2f));
					m.body.yRot = Mth.lerp(w, m.body.yRot, Mth.lerp(k, 0.45f, -0.35f));
					com.projecthero.mod.client.HipBend.pitch(m, w, 0.12f * k);
				}
			}
			case IronManAbilityFx.MK9_ERUPTION -> {
				if (t < 5f) {
					// both fists raised high overhead, chin up
					float k = Mth.clamp(t / 3f, 0f, 1f);
					set(m.rightArm, w, Mth.lerp(k, 0f, -3.0f), 0f, 0.25f);
					set(m.leftArm, w, Mth.lerp(k, 0f, -3.0f), 0f, -0.25f);
					m.head.xRot = Mth.lerp(w, m.head.xRot, -0.3f);
				} else {
					// hammered down into the ground, hunched over, knees bent
					float k = Mth.clamp((t - 5f) / 2f, 0f, 1f);
					set(m.rightArm, w, Mth.lerp(k, -3.0f, -0.45f), 0f, Mth.lerp(k, 0.25f, 0.1f));
					set(m.leftArm, w, Mth.lerp(k, -3.0f, -0.45f), 0f, Mth.lerp(k, -0.25f, -0.1f));
					com.projecthero.mod.client.HipBend.pitch(m, w, 0.45f * k);
					m.head.xRot = Mth.lerp(w * k, m.head.xRot, 0.4f);
					set(m.rightLeg, w * k, -0.35f, 0f, 0.08f);
					set(m.leftLeg, w * k, 0.25f, 0f, -0.08f);
				}
			}
			case IronManAbilityFx.MK9_LOB -> {
				// an underarm lob: the right arm swung back low, then up and through
				float k = Mth.clamp((t - 3f) / 3f, 0f, 1f);
				set(m.rightArm, w, Mth.lerp(k, 0.9f, -1.9f), Mth.lerp(k, 0f, aimY), 0.15f);
				set(m.leftArm, w, -0.45f, 0f, -0.3f);
				com.projecthero.mod.client.HipBend.pitch(m, w, Mth.lerp(k, 0.2f, -0.05f));
			}
			case IronManAbilityFx.MK9_HEAT_DUMP -> {
				if (t < 4f) {
					// curled in: forearms crossed over the chest, hunched, head down -- the heat building
					set(m.rightArm, w, -1.4f, 0.6f, 0f);
					set(m.leftArm, w, -1.4f, -0.6f, 0f);
					com.projecthero.mod.client.HipBend.pitch(m, w, 0.3f);
					m.head.xRot = Mth.lerp(w, m.head.xRot, 0.3f);
				} else {
					// thrown open: arms flung wide and back, chest out, head back -- the vent
					float k = Mth.clamp((t - 4f) / 2f, 0f, 1f);
					set(m.rightArm, w, Mth.lerp(k, -1.4f, 0.35f), Mth.lerp(k, 0.6f, 0f), Mth.lerp(k, 0f, 1.55f));
					set(m.leftArm, w, Mth.lerp(k, -1.4f, 0.35f), Mth.lerp(k, -0.6f, 0f), Mth.lerp(k, 0f, -1.55f));
					com.projecthero.mod.client.HipBend.pitch(m, w, Mth.lerp(k, 0.3f, -0.25f));
					m.head.xRot = Mth.lerp(w, m.head.xRot, Mth.lerp(k, 0.3f, -0.35f));
				}
			}
			case IronManAbilityFx.MK9_SIPHON -> {
				// arms spread low, palms down, a faint tremble -- drawing the heat out of everything round you
				float tremble = Mth.sin(ageInTicks * 1.9f) * 0.03f;
				set(m.rightArm, w, -0.45f + tremble, 0f, 0.95f);
				set(m.leftArm, w, -0.45f - tremble, 0f, -0.95f);
				com.projecthero.mod.client.HipBend.pitch(m, w, -0.1f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, -0.2f);
			}
			default -> {
			}
		}
	}

	private static void set(net.minecraft.client.model.geom.ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
