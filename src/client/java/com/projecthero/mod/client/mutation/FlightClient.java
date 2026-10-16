package com.projecthero.mod.client.mutation;

import static com.projecthero.mod.client.mutation.MutationPose.AIM;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.flight.LightTrail;
import com.projecthero.mod.client.greenlantern.HardLightRibbon;
import com.projecthero.mod.client.revamp.batcha.FlightFxRenderer;
import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.flight.DirectionalFlightModel.Tune;
import com.projecthero.mod.hero.power.p03.FlightHandlers;
import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Flight rework -- the client half, on every client from the synced {@code p03.*} visual values
 * ({@link FlightHandlers#registerVisuals}) and the flier's own Lift resources:
 * <ul>
 * <li>the flight tune: 20 b/s cruising, 50 sprinting on Lift, x1.6 Afterburner, x1.5 Slipstream, Hover dead still
 * ({@link #tune}); a squadmate flying in a Slipstream's wake goes 50% faster ({@link #drafting});</li>
 * <li>the move poses (punch, dive, landing, carry, throw, overhead lift, dash, barrel roll, sonic boom, takeoff, hover,
 * updraft), the body laid along a dive / dash ({@link #adjustLean}) and the barrel roll's spin ({@link #barrelRoll});</li>
 * <li>world models: the Afterburner contrail, the Slipstream's wind funnel and the Hover's air rings under the feet.</li>
 * </ul>
 * The bursts (sonic boom cone, crater, columns, rings) are {@code FlightFxEntity} models ({@link FlightFxRenderer}).
 */
public final class FlightClient {
	private static final String KEY = FlightHandlers.KEY;
	private static final float[] Z = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	/** A white vapour contrail (sheet, core, twisting strands, glow). */
	private static final LightTrail.Palette CONTRAIL = new LightTrail.Palette(0xF4F8FC, 0xFFFFFF, 0xD6E4F2, 0x8FB2D6);

	/** Per player: the eased lean override and the movement it reads. */
	private static final class Body {
		double lx, ly, lz;
		boolean hasLast;
		Vec3 motion = Vec3.ZERO;
		float w, wPrev, target = 90f;
		final LightTrail.History trail = new LightTrail.History();
		final LightTrail.History trail2 = new LightTrail.History();
	}

	private static final Map<Integer, Body> BODIES = new HashMap<>();

	private FlightClient() {
	}

	/** From {@code RevampClientA.init()}. */
	public static void init() {
		com.projecthero.mod.client.gui.AbilityHudExtras.mono(KEY);
		registerPoses();
		ClientTickEvents.END_CLIENT_TICK.register(FlightClient::clientTick);
		WorldRenderEvents.AFTER_ENTITIES.register(FlightClient::renderWorld);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BODIES.clear());
	}

	// ================================================================== the flight tune

	private static float res(Player p, String name) {
		return FlightHandlers.resAny(p, name);
	}

	/** The Flight power's directional-flight numbers this tick (local player). */
	public static Tune tune(LocalPlayer p, boolean sprint) {
		return DirectionalFlightModel.flightPower(sprint, res(p, "lift") >= 0.5f || p.getAbilities().instabuild,
				res(p, "burn") > 0.5f, res(p, "slip") > 0.5f, res(p, "hover") > 0.5f);
	}

	/** The horizontal ceiling (blocks a second) for the old non-directional clamp in {@code LocalPlayerMixin}. */
	public static double capBps(LocalPlayer p) {
		return DirectionalFlightModel.flightPowerBps(p.isSprinting(), res(p, "lift") >= 0.5f, res(p, "burn") > 0.5f,
				res(p, "slip") > 0.5f);
	}

	/**
	 * Slipstream drafting: the local player, flying any flight, within 16 blocks <em>behind</em> a squadmate whose
	 * Slipstream is up goes 50% faster. (The Slipstream's owner gets theirs in {@link #tune}.)
	 */
	public static Tune drafting(LocalPlayer self, Tune tune) {
		if (tune.speed() <= 0.0 || !com.projecthero.mod.client.squad.SquadClient.inSquad() || self.level() == null) {
			return tune;
		}
		var members = com.projecthero.mod.client.squad.SquadClient.get().members();
		for (Player leader : self.level().players()) {
			if (leader == self || !MutationVisuals.hasFlag(leader, "p03.slip") || leader.distanceToSqr(self) > 16.0 * 16.0) {
				continue;
			}
			boolean mate = false;
			for (var m : members) {
				if (m.id().equals(leader.getUUID())) {
					mate = true;
					break;
				}
			}
			if (!mate) {
				continue;
			}
			Vec3 ahead = leader.getLookAngle();
			Vec3 toMe = self.position().subtract(leader.position());
			if (toMe.lengthSqr() < 1.0e-4 || toMe.normalize().dot(ahead) < -0.4) {
				double k = FlightHandlers.SLIPSTREAM_MULT;
				return new Tune(tune.speed() * k, tune.verticalSpeed(), tune.strafeScale(), tune.acceleration(), tune.reverse(),
						tune.idle(), tune.maxHorizontal() * k, tune.forcedForward() * k, tune.sneakBoosts(), tune.pushCarryTicks(),
						tune.adoptPushes());
			}
		}
		return tune;
	}

	// ================================================================== poses

	private static float[] at(int tick, float[] pose) {
		float[] f = pose.clone();
		f[0] = tick;
		return f;
	}

	private static float[] zero(int tick) {
		return at(tick, Z);
	}

	/** {rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX, headX} -- see {@link MutationPose}. */
	private static float[] p(float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY, float rL, float lL,
			float h) {
		return new float[] { 0, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	private static void registerPoses() {
		// R Flying Punch: the fist drawn back, then thrown along the look as he shoots in; the other arm swept back
		float[] cock = p(0.9f, 0.3f, 0.25f, -0.5f, 0.2f, -0.15f, 0.1f, 0.45f, 0.1f, -0.1f, 0);
		float[] thrust = p(AIM, AIM, 0, 0.7f, 0.1f, -0.25f, 0.2f, -0.35f, 0.45f, 0.15f, 0);
		MutationPose.register("p03.punch", new float[][] { zero(0), at(2, cock), at(4, thrust), at(11, thrust), zero(15) });
		// Shift+R Dive Bomb: both fists out ahead (overhead), head up -- the body is laid along the dive (adjustLean)
		float[] dive = p(-3.05f, 0, -0.12f, -3.05f, 0, 0.12f, 0, 0, 0.1f, 0.1f, 0.4f);
		float[] dive2 = p(-3.1f, 0, -0.06f, -3.1f, 0, 0.06f, 0, 0, 0.14f, 0.05f, 0.4f);
		MutationPose.registerLoop("p03.dive", 3, new float[][] { zero(0), at(3, dive), at(5, dive2), at(7, dive) });
		// the three-point landing out of the dive
		float[] land = p(-0.35f, 0.1f, 0.15f, 0.6f, 0, -0.7f, 0.6f, 0.2f, -1.25f, 0.65f, -0.35f);
		MutationPose.register("p03.land", new float[][] { zero(0), at(2, land), at(16, land), zero(24) });
		// G Grab & Carry: both hands out along the look, holding the creature
		float[] hold = p(AIM, AIM, 0.08f, AIM, AIM, -0.08f, 0.05f, 0, 0.15f, 0.25f, 0);
		float[] hold2 = p(AIM, AIM, 0.12f, AIM, AIM, -0.12f, 0.06f, 0, 0.2f, 0.2f, 0);
		MutationPose.registerLoop("p03.carry", 4, new float[][] { zero(0), at(4, hold), at(16, hold2), at(28, hold) });
		// the throw: both arms back overhead, then hurled forward
		float[] wind = p(-2.9f, 0, 0.35f, -2.9f, 0, -0.35f, -0.2f, 0, 0.2f, -0.1f, -0.15f);
		float[] hurl = p(-1.2f, -0.2f, 0.1f, -1.2f, 0.2f, -0.1f, 0.3f, 0, -0.2f, 0.3f, 0.1f);
		MutationPose.register("p03.throw", new float[][] { zero(0), at(2, wind), at(5, hurl), at(9, hurl), zero(13) });
		// Shift+G Orbital Drop: rocketing up with the creature held overhead in both hands
		float[] lift = p(-3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.05f, 0, 0.05f, -0.05f, -0.3f);
		float[] lift2 = p(-3.05f, 0, 0.15f, -3.05f, 0, -0.15f, -0.05f, 0, -0.05f, 0.05f, -0.3f);
		MutationPose.registerLoop("p03.orbit", 3, new float[][] { zero(0), at(3, lift), at(6, lift2), at(9, lift) });
		// ... and let go at the top: the arms fling open
		float[] open = p(-2.4f, 0, 0.9f, -2.4f, 0, -0.9f, -0.1f, 0, 0.1f, -0.1f, 0.2f);
		MutationPose.register("p03.release", new float[][] { at(0, lift), at(3, open), at(10, open), zero(16) });
		// X Air Dash: arms tight back at the sides, streamlined
		float[] dash = p(0.45f, 0, 0.12f, 0.45f, 0, -0.12f, 0.25f, 0, 0.25f, 0.35f, -0.1f);
		MutationPose.register("p03.dash", new float[][] { zero(0), at(1, dash), at(8, dash), zero(12) });
		// Shift+X Barrel Roll: arms tucked in over the chest for the spin (the body turns in PlayerRendererMixin)
		float[] tuck = p(-0.7f, -0.85f, 0, -0.7f, 0.85f, 0, 0.1f, 0, 0.05f, 0.05f, 0);
		MutationPose.register("p03.roll_right", new float[][] { zero(0), at(1, tuck), at(9, tuck), zero(13) });
		MutationPose.register("p03.roll_left", new float[][] { zero(0), at(1, tuck), at(9, tuck), zero(13) });
		// Z Sonic Boom: chest thrust out, arms flung back as the barrier breaks
		float[] brace = p(-0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.25f, 0, 0.2f, 0.2f, 0);
		float[] boom = p(0.95f, 0, 0.55f, 0.95f, 0, -0.55f, -0.2f, 0, 0.35f, 0.25f, -0.2f);
		MutationPose.register("p03.boom", new float[][] { zero(0), at(2, brace), at(4, boom), at(12, boom), zero(16) });
		// Shift+Z Takeoff Blast: the classic one-fist-up launch
		float[] rise = p(-3.1f, 0, 0.05f, 0.15f, 0, -0.12f, -0.05f, 0, 0.05f, -0.05f, -0.35f);
		float[] rise2 = p(-3.05f, 0, 0.08f, 0.2f, 0, -0.15f, -0.05f, 0, -0.05f, 0.05f, -0.35f);
		MutationPose.register("p03.rise", new float[][] { zero(0), at(2, rise), at(5, rise2), at(9, rise), at(16, rise2), zero(22) });
		// flying: legs together with one knee trailing, arms back at the sides, a slow sway
		float[] fly = p(0.12f, 0, 0.07f, 0.12f, 0, -0.07f, 0, 0, 0.1f, 0.32f, 0);
		float[] fly2 = p(0.16f, 0, 0.09f, 0.16f, 0, -0.09f, 0, 0, 0.16f, 0.26f, 0);
		MutationPose.registerLoop("p03.fly", 4, new float[][] { zero(0), at(4, fly), at(24, fly2), at(44, fly) });
		// V Hover: arms a little out and down, legs hanging loose, a slow bob
		float[] hov = p(-0.15f, 0, 0.35f, -0.15f, 0, -0.35f, 0, 0, 0.12f, -0.05f, 0);
		float[] hov2 = p(-0.1f, 0, 0.42f, -0.1f, 0, -0.42f, 0, 0, 0.05f, 0.04f, 0);
		MutationPose.registerLoop("p03.hover", 5, new float[][] { zero(0), at(5, hov), at(25, hov2), at(45, hov) });
		// Shift+C Updraft: both arms sweep up from low, palms lifting the air
		float[] low = p(0.3f, 0, 0.7f, 0.3f, 0, -0.7f, 0.15f, 0, 0, 0, 0.1f);
		float[] high = p(-2.8f, 0, 0.45f, -2.8f, 0, -0.45f, -0.1f, 0, 0, 0, -0.2f);
		MutationPose.register("p03.updraft", new float[][] { zero(0), at(3, low), at(8, high), at(14, high), zero(19) });
	}

	/** A Flight move pose is playing on this player (the shared hero-flight arm pin must not undo it). */
	public static boolean posing(Player player) {
		MutationVisualState s = MutationVisuals.state(player);
		String anim = s.anim();
		if (!anim.startsWith("p03.")) {
			return false;
		}
		MutationPose.Def def = MutationPose.get(anim);
		if (def == null) {
			return false;
		}
		return def.loops() || player.level().getGameTime() - s.animStart() < def.end();
	}

	// ================================================================== body: lean along a dive / dash, the roll

	private static int driveKind(Player p) {
		return Math.round(MutationVisuals.state(p).value("p03.drive", 0.0f));
	}

	private static void clientTick(Minecraft mc) {
		if (mc.level == null) {
			BODIES.clear();
			return;
		}
		java.util.Set<Integer> present = new java.util.HashSet<>();
		long now = mc.level.getGameTime();
		for (Player pl : mc.level.players()) {
			present.add(pl.getId());
			Body b = BODIES.computeIfAbsent(pl.getId(), id -> new Body());
			b.motion = b.hasLast ? new Vec3(pl.getX() - b.lx, pl.getY() - b.ly, pl.getZ() - b.lz) : Vec3.ZERO;
			b.lx = pl.getX();
			b.ly = pl.getY();
			b.lz = pl.getZ();
			b.hasLast = true;
			// the lean override: a dive lies head-first along the fall, a dash / punch along its line
			int kind = driveKind(pl);
			float target = Float.NaN;
			double h = Math.sqrt(b.motion.x * b.motion.x + b.motion.z * b.motion.z);
			if (kind == FlightHandlers.DRIVE_DIVE) {
				target = (float) Mth.clamp(90.0 + Math.toDegrees(Math.atan2(-b.motion.y, Math.max(h, 1.0e-3))), 100.0, 175.0);
			} else if ((kind == FlightHandlers.DRIVE_PUNCH || kind == FlightHandlers.DRIVE_DASH || kind == FlightHandlers.DRIVE_BOOM)
					&& !pl.onGround()) {
				target = (float) Mth.clamp(90.0 + Math.toDegrees(Math.atan2(-b.motion.y, Math.max(h, 1.0e-3))), 20.0, 170.0);
			}
			b.wPrev = b.w;
			if (!Float.isNaN(target)) {
				b.target = target;
				b.w = Math.min(1f, b.w + 0.4f);
			} else {
				b.w = Math.max(0f, b.w - 0.2f);
			}
			// the Afterburner contrail: two streams off the shoulders
			if (MutationVisuals.hasFlag(pl, "p03.burn")) {
				Vec3[] tips = shoulderPoints(pl, 1.0f);
				b.trail.sample(tips[0], now);
				b.trail2.sample(tips[1], now);
			} else {
				b.trail.prune(now);
				b.trail2.prune(now);
			}
		}
		BODIES.keySet().retainAll(present);
	}

	/** The body's lean (degrees, as {@code FlightPoseHelper#lean}) with a dive / dash override eased over it. */
	public static float adjustLean(Player player, float base, float partial) {
		Body b = BODIES.get(player.getId());
		if (b == null) {
			return base;
		}
		float w = Mth.lerp(partial, b.wPrev, b.w);
		if (w <= 0.001f) {
			return base;
		}
		float smooth = w * w * (3f - 2f * w);
		return Mth.lerp(smooth, base, b.target);
	}

	/** The Barrel Roll's spin, degrees (+ = rolling right), for {@code PlayerRendererMixin}'s body roll. */
	public static float barrelRoll(Player player, float partial) {
		MutationVisualState s = MutationVisuals.state(player);
		String anim = s.anim();
		boolean right = "p03.roll_right".equals(anim);
		if (!right && !"p03.roll_left".equals(anim)) {
			return 0f;
		}
		float t = (player.level().getGameTime() - s.animStart() + partial) / 10f;
		if (t <= 0f || t >= 1f) {
			return 0f;
		}
		float e = t * t * (3f - 2f * t);
		return (right ? 360f : -360f) * e;
	}

	/** Two points out at the flier's shoulders, along the body (laid flat sprint-flying). */
	private static Vec3[] shoulderPoints(Player pl, float partial) {
		float lean = com.projecthero.mod.client.FlightPoseHelper.lean(pl, partial) * Mth.DEG_TO_RAD;
		float yaw = Mth.lerp(partial, pl.yBodyRotO, pl.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		// the body's "up" axis tipped forward by the lean; the shoulders sit 1.4 up it
		Vec3 up = new Vec3(0, Mth.cos(lean), 0).add(fwd.scale(Mth.sin(lean)));
		Vec3 base = pl.getPosition(partial).add(up.scale(1.3));
		return new Vec3[] { base.add(right.scale(0.42)), base.subtract(right.scale(0.42)) };
	}

	// ================================================================== world models

	private static void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		long now = mc.level.getGameTime();
		float time = now + partial;
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		for (Player pl : mc.level.players()) {
			Body b = BODIES.get(pl.getId());
			if (b == null || pl.isInvisible()) {
				continue;
			}
			// the Afterburner contrails
			if (!b.trail.isEmpty()) {
				boolean live = MutationVisuals.hasFlag(pl, "p03.burn");
				Vec3[] tips = live ? shoulderPoints(pl, partial) : null;
				b.trail.draw(pose, context.consumers(), cam, tips == null ? null : tips[0], partial, now, time, CONTRAIL, 1.25f);
				b.trail2.draw(pose, context.consumers(), cam, tips == null ? null : tips[1], partial, now, time, CONTRAIL, 1.25f);
			}
			boolean self = pl == mc.player && firstPerson;
			// the Slipstream: a funnel of wind streaming back off the flier
			if (MutationVisuals.hasFlag(pl, "p03.slip")) {
				Vec3 dir = pl.getViewVector(partial);
				Vec3 at = pl.getPosition(partial).add(0, 0.9, 0);
				pose.pushPose();
				pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
				orient(pose, dir.scale(-1));
				PoseStack.Pose last = pose.last();
				VertexConsumer vc = HardLightRibbon.translucent(context.consumers());
				float start = self ? 1.6f : 0.6f;
				FlightFxRenderer.frustum(vc, last, start, 7.5f, 0.6f, 1.9f, 0xCFE8FF, 0.13f, 0f, 28, time * 0.15f);
				FlightFxRenderer.helix(vc, last, start, 7.5f, 0.7f, 1.8f, 4, 1.2f, -time * 0.4f, 0.1f, 0xFFFFFF, 0.38f);
				pose.popPose();
			}
			// Hover: two slowly turning rings of disturbed air under the feet
			if (MutationVisuals.hasFlag(pl, "p03.hover")) {
				Vec3 at = pl.getPosition(partial);
				pose.pushPose();
				pose.translate(at.x - cam.x, at.y - cam.y - 0.1, at.z - cam.z);
				pose.mulPose(Axis.XP.rotationDegrees(-90f)); // local +Z up
				PoseStack.Pose last = pose.last();
				for (int i = 0; i < 3; i++) {
					float k = ((time * 0.04f) + i / 3f) % 1f;
					float rr = 0.4f + k * 1.6f;
					FlightFxRenderer.ring(HardLightRibbon.translucent(context.consumers()), last, -k * 0.5f, rr, 0.18f, 0xDCEEFF, 0.45f * (1f - k), 32, 0f);
				}
				FlightFxRenderer.ring(HardLightRibbon.additive(context.consumers()), last, 0f, 0.55f + 0.05f * Mth.sin(time * 0.3f), 0.08f, 0x9CC8F0, 0.5f, 32, 0f);
				pose.popPose();
			}
		}
	}

	/** Turns the stack so local +Z points along {@code dir}. */
	private static void orient(PoseStack pose, Vec3 dir) {
		Vec3 d = dir.normalize();
		float yaw = (float) Math.atan2(-d.x, d.z);
		float pitch = (float) -Math.asin(Mth.clamp(d.y, -1.0, 1.0));
		pose.mulPose(Axis.YP.rotation(-yaw));
		pose.mulPose(Axis.XP.rotation(pitch));
	}

	/** True if {@code p} is a Flight hero flying (for callers outside this class). */
	public static boolean flying(Player p) {
		return FlightHandlers.owns(p) && p.getAttachedOrElse(ModAttachments.HERO_FLYING, false);
	}
}
