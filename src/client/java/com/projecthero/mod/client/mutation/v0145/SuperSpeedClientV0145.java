package com.projecthero.mod.client.mutation.v0145;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.gui.AbilityHudExtras;
import com.projecthero.mod.client.mutation.MutationPose;
import com.projecthero.mod.client.thor.ThorDraw;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.hero.revamp.v0145.SuperSpeedV0145;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.network.SpeedStreakPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.5 Super Speed rework: client registration (v0.14.7 additions marked).
 *
 * <ul>
 *   <li>HUD: black-and-gray theme, and the current speed mode ("Regular" / "Speed" / "Overdrive") right of the
 *       power name.</li>
 *   <li>After-image trail: every tick a running speedster with the {@code p04.trail} flag (Speed Mode or
 *       Overdrive) leaves a translucent copy of their body behind -- yellow, red in Overdrive -- that fades out
 *       over one second. Every client records its own copies for every such player it can see. v0.14.7: the copies
 *       keep the speedster run pose ({@link SpeedRunPose}) they were left in, wall runs leave them too, and in
 *       Overdrive red-and-white lightning crackles between them.</li>
 *   <li>v0.14.7: {@link SpeedStreakPayload} -- lines of after-images along a Blitz / Speed Sweep zip or round a Speed
 *       Vortex.</li>
 *   <li>Time Slow: the client mirror of the server's 1-in-20 entity ticks ({@link #skipClientTick}); v0.14.7: the
 *       screen effect is {@link TimeSlowOverlay}.</li>
 * </ul>
 */
public final class SuperSpeedClientV0145 {
	private static final int TRAIL_EVERY = 1; // v0.14.5: every tick, for a continuous trail (was 5)
	private static final int TRAIL_LIFE = 20;
	private static final int YELLOW = 0xFFD83A;
	private static final int RED = 0xFF3030;

	/**
	 * One after-image. {@code fixed} copies (streaks, the decoy) are drawn wherever they are; trail copies only once
	 * the speedster has left them behind.
	 */
	private record Snapshot(long time, int life, float alpha, double x, double y, double z, float bodyYaw, float headYaw,
			float pitch, float limbPos, float limbSpeed, boolean crouching, int rgb, float run, boolean overdrive,
			boolean fixed) {
	}

	private static final Map<UUID, Deque<Snapshot>> TRAILS = new HashMap<>();

	private SuperSpeedClientV0145() {
	}

	public static void init() {
		AbilityHudExtras.mono(SuperSpeedHandlers.KEY);
		AbilityHudExtras.registerDecor(SuperSpeedHandlers.KEY, (g, client, state, x, y) -> {
			long now = client.level != null ? client.level.getGameTime() : 0L;
			Float until = state.resources.get(SuperSpeedHandlers.KEY + "/" + SuperSpeedHandlers.OVERDRIVE_UNTIL);
			boolean overdrive = until != null && until > now;
			boolean speed = state.activeToggles.contains(SuperSpeedHandlers.KEY + "/speed_mode");
			String text = overdrive ? "Overdrive" : speed ? "Speed" : "Regular";
			int color = overdrive ? 0xFFFF4040 : speed ? 0xFFFFD83A : 0xFF9A9A9A;
			// v0.14.7: Speed Carry (N) has no box on the HUD -- say so here while you are carrying something
			if (state.resources.getOrDefault(SuperSpeedHandlers.KEY + "/carry_id", 0f) > 0.5f) {
				text += " - Carrying";
			}
			g.drawString(client.font, "- " + text, x, y, color);
		});
		ClientTickEvents.END_CLIENT_TICK.register(SuperSpeedClientV0145::tick);
		ClientTickEvents.END_CLIENT_TICK.register(SpeedRunPose::tick);
		TimeSlowClient.init();
		SpeedPhaseFx.init(); // v0.14.8: Phase vibration
		WorldRenderEvents.AFTER_ENTITIES.register(SuperSpeedClientV0145::renderTrails);
		ClientPlayNetworking.registerGlobalReceiver(SpeedStreakPayload.TYPE,
				(payload, context) -> context.client().execute(() -> acceptStreak(payload)));
		registerPoses();
	}

	// ---- v0.14.7 move poses: {tick, rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX, headX} ----

	private static float[] f(float tick, float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY,
			float rL, float lL, float h) {
		return new float[] { tick, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	private static float[] z(float tick) {
		return new float[] { tick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	}

	private static float[] at(float tick, float[] pose) {
		float[] p = pose.clone();
		p[0] = tick;
		return p;
	}

	private static void registerPoses() {
		// G Blitz: arrive mid-lunge with a straight right
		float[] blitz = f(0, -1.65f, -0.1f, 0, 0.5f, 0.2f, -0.1f, 0.2f, -0.45f, -0.55f, 0.55f, -0.1f);
		MutationPose.register("p04.blitz", new float[][] { at(0, blitz), at(6, blitz), z(11) });
		// Shift+R Mach Punch: coil (fist back, body turned), then the punch thrown through with the whole body
		float[] coil = f(0, 0.95f, 0.3f, 0.15f, -0.9f, 0.3f, -0.1f, 0.1f, 0.55f, -0.35f, 0.35f, 0);
		float[] mach = f(0, -1.75f, -0.05f, 0, 0.7f, 0.15f, -0.2f, 0.3f, -0.65f, 0.6f, -0.6f, -0.15f);
		MutationPose.register("p04.mach_punch", new float[][] { z(0), at(2, coil), at(4, mach), at(12, mach), z(17) });
		// Shift+G Speed Vortex: running tight circles -- leaned in, arms swept back, legs blurring
		float[] vA = f(0, 1.05f, 0, 0.3f, 1.05f, 0, -0.3f, 0.4f, 0.25f, 1.0f, -1.0f, -0.25f);
		float[] vB = f(0, 1.05f, 0, 0.3f, 1.05f, 0, -0.3f, 0.4f, 0.25f, -1.0f, 1.0f, -0.25f);
		MutationPose.registerLoop("p04.vortex", 2, new float[][] { z(0), at(2, vA), at(4, vB), at(6, vA) });
		// Shift+X Speed Sweep: alternating straights at every stop
		float[] sR = f(0, -1.6f, -0.1f, 0, 0.4f, 0.2f, 0, 0.25f, -0.4f, -0.3f, 0.3f, 0);
		float[] sL = f(0, 0.4f, -0.2f, 0, -1.6f, 0.1f, 0, 0.25f, 0.4f, 0.3f, -0.3f, 0);
		MutationPose.registerLoop("p04.sweep", 1, new float[][] { z(0), at(1, sR), at(3, sL), at(5, sR) });
		// v0.14.8 Z held: bracing to charge Time Slow -- low, leaned in, fists clenched at the hips, a shaking build-up
		float[] brace = f(0, 0.55f, 0.1f, 0.35f, 0.55f, -0.1f, -0.35f, 0.42f, 0f, -0.45f, 0.35f, -0.25f);
		float[] braceB = f(0, 0.65f, 0.1f, 0.42f, 0.45f, -0.1f, -0.3f, 0.45f, 0.04f, -0.42f, 0.38f, -0.28f);
		MutationPose.registerLoop(SuperSpeedHandlers.ANIM_TS_CHARGE, 4, new float[][] { z(0), at(4, brace), at(5, braceB), at(6, brace) });
	}

	// ---- Time Slow (the caster's own client) --------------------------------------------------------

	/**
	 * v0.14.7, game-wide Time Slow: every other client simply runs at the synced 1 tick/s. The caster's client keeps
	 * its 20 tick/s timer ({@code TimeSlowClient}), so here everything but the caster (and what they ride / carry) is
	 * stepped once every 20 of its ticks -- the same pace the server runs them at. A skipped living thing's "previous"
	 * pose is pinned to the current one so frames in between never flicker.
	 */
	public static boolean skipClientTick(Entity e) {
		if (!TimeSlowClient.skipWorldTick()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		Player me = mc.player;
		return me == null || (e != me && e != me.getVehicle() && !e.hasPassenger(me) && e.getVehicle() != me);
	}

	/** Things the client simulates on its own between server updates: they glide instead of stepping. */
	private static boolean glides(Entity e) {
		return e instanceof net.minecraft.world.entity.projectile.Projectile
				|| e instanceof net.minecraft.world.entity.item.FallingBlockEntity
				|| e instanceof net.minecraft.world.entity.item.PrimedTnt
				|| e instanceof net.minecraft.world.entity.item.ItemEntity
				|| e instanceof net.minecraft.world.entity.ExperienceOrb;
	}

	/**
	 * On the caster's client, projectiles, falling blocks, TNT, items and XP orbs glide 1/20th of their velocity on
	 * every skipped tick (exactly the distance the server moves them per real tick) instead of stepping once a
	 * second; the server's position / velocity updates keep them honest. Returns true when it handled the tick.
	 */
	public static boolean glideClientTick(Entity e) {
		if (!glides(e) || e.isPassenger() || !skipClientTick(e)) {
			return false;
		}
		e.setOldPosAndRot();
		Vec3 v = e.getDeltaMovement();
		double k = 1.0 / SuperSpeedTimeSlow.TICK_DIVISOR;
		e.setPos(e.getX() + v.x * k, e.getY() + v.y * k, e.getZ() + v.z * k);
		return true;
	}

	/** Particles this close to the caster keep their pace -- the speedster's own sparks and bursts. */
	private static final double PARTICLE_EXEMPT_SQ = 2.5 * 2.5;

	/**
	 * On the caster's client, particles tick once every 20 client ticks like the rest of the world; on the ticks in
	 * between they creep 1/20th of their velocity so the slow motion stays smooth. False straight away otherwise.
	 */
	public static boolean slowParticle(net.minecraft.client.particle.Particle particle) {
		if (!TimeSlowClient.skipWorldTick()) {
			return false;
		}
		Player me = Minecraft.getInstance().player;
		com.projecthero.mod.client.mixin.SuperSpeedParticleAccessor a =
				(com.projecthero.mod.client.mixin.SuperSpeedParticleAccessor) particle;
		double x = a.projecthero$x();
		double y = a.projecthero$y();
		double z = a.projecthero$z();
		if (me != null && me.distanceToSqr(x, y, z) <= PARTICLE_EXEMPT_SQ) {
			return false;
		}
		a.projecthero$setXo(x);
		a.projecthero$setYo(y);
		a.projecthero$setZo(z);
		double k = 1.0 / SuperSpeedTimeSlow.TICK_DIVISOR;
		particle.move(a.projecthero$xd() * k, a.projecthero$yd() * k, a.projecthero$zd() * k);
		return true;
	}

	// ---- After-image trail ----------------------------------------------------------------------

	private static void tick(Minecraft mc) {
		if (mc.level == null) {
			TRAILS.clear();
			return;
		}
		long now = mc.level.getGameTime();
		java.util.Set<UUID> present = new java.util.HashSet<>();
		for (AbstractClientPlayer p : mc.level.players()) {
			present.add(p.getUUID());
			if (!MutationVisuals.hasFlag(p, SuperSpeedV0145.TRAIL) || p.isSpectator() || p.tickCount % TRAIL_EVERY != 0) {
				continue;
			}
			double dx = p.getX() - p.xo;
			double dy = p.getY() - p.yo;
			double dz = p.getZ() - p.zo;
			// v0.14.7: 3-D, so running up a wall leaves after-images on it too
			if (dx * dx + dz * dz < 0.05 * 0.05 && Math.abs(dy) < 0.25) {
				continue; // only while actually moving
			}
			boolean red = SpeedRunPose.overdrive(p);
			// an after-image is left where the player WAS (last tick), never where they are about to be -- the rendered
			// player is interpolated between xo and x, so a copy at x would pop up just ahead of them
			TRAILS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>()).addLast(new Snapshot(now, TRAIL_LIFE, 0.4f,
					p.xo, p.yo, p.zo, p.yBodyRotO, p.yHeadRotO, p.xRotO, p.walkAnimation.position(0f),
					p.walkAnimation.speed(0f), p.isCrouching(), red ? RED : YELLOW, SpeedRunPose.weight(p, 0f), red, false));
		}
		for (Iterator<Map.Entry<UUID, Deque<Snapshot>>> it = TRAILS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Deque<Snapshot>> e = it.next();
			Deque<Snapshot> q = e.getValue();
			q.removeIf(s -> now - s.time() > s.life());
			if (q.isEmpty() || !present.contains(e.getKey())) {
				it.remove();
			}
		}
	}

	/** v0.14.7: a streak of after-images along a zip. */
	private static void acceptStreak(SpeedStreakPayload msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !(mc.level.getEntity(msg.playerId()) instanceof AbstractClientPlayer p)) {
			return;
		}
		long now = mc.level.getGameTime();
		Deque<Snapshot> q = TRAILS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
		boolean red = msg.rgb() == RED;
		float limb = p.walkAnimation.position(0f);
		Vec3 from = new Vec3(msg.fx(), msg.fy(), msg.fz());
		Vec3 to = new Vec3(msg.tx(), msg.ty(), msg.tz());
		double len = from.distanceTo(to);
		int n = (int) Math.max(2, Math.min(40, Math.round(len / 0.8)));
		for (int i = 0; i < n; i++) {
			double t = n == 1 ? 1.0 : i / (double) (n - 1);
			Vec3 at = from.lerp(to, t);
			// copies nearer the start fade first, so the zip reads as a blur racing toward its end
			int life = Math.max(3, Math.round(msg.life() * (0.45f + 0.55f * (float) t)));
			q.addLast(new Snapshot(now, life, 0.45f, at.x, at.y, at.z, msg.yaw(), msg.yaw(), 0f, limb + i * 1.1f, 1.0f,
					false, msg.rgb(), 1.0f, red, true));
		}
	}

	private static void renderTrails(WorldRenderContext context) {
		if (TRAILS.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		if (mc.level == null || pose == null || buffers == null) {
			return;
		}
		Vec3 cam = context.camera().getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		List<Vec3[]> arcs = new ArrayList<>();
		List<Float> arcAlpha = new ArrayList<>();
		for (Map.Entry<UUID, Deque<Snapshot>> e : TRAILS.entrySet()) {
			Player player = mc.level.getPlayerByUUID(e.getKey());
			if (!(player instanceof AbstractClientPlayer acp)) {
				continue;
			}
			EntityRenderer<? super AbstractClientPlayer> r = mc.getEntityRenderDispatcher().getRenderer(acp);
			if (!(r instanceof PlayerRenderer pr)) {
				continue;
			}
			PlayerModel<AbstractClientPlayer> model = pr.getModel();
			ResourceLocation skin = acp.getSkin().texture();
			boolean own = player == mc.player;
			Vec3 at = acp.getPosition(partial);
			Vec3 heading = at.subtract(acp.xo, acp.yo, acp.zo);
			Snapshot prevRed = null;
			int redIndex = 0;
			for (Snapshot s : e.getValue()) {
				if (!s.fixed()) {
					// only copies the player has left BEHIND: at least 0.6 blocks back, never ahead along their movement
					Vec3 off = new Vec3(s.x(), s.y(), s.z()).subtract(at);
					if (off.lengthSqr() < 0.36 || (heading.lengthSqr() > 1.0e-4 && off.dot(heading) > 0.0)) {
						continue;
					}
				}
				float age = (now - s.time()) + partial;
				float fade = 1.0f - age / s.life();
				if (fade <= 0.0f) {
					continue;
				}
				// never draw a fresh copy over your own first-person camera
				if (own && firstPerson && cam.distanceToSqr(s.x(), s.y() + 1.0, s.z()) < 2.25) {
					continue;
				}
				// v0.14.8 Overdrive lightning trail: jagged yellow / orange-white bolts strung between every pair of
				// consecutive trail copies (so only ever BEHIND the runner), two per gap at different heights, re-shaped
				// several times a tick and randomly dropping out (the flicker), fading with the copies over a second
				if (s.overdrive() && !s.fixed()) {
					if (prevRed != null && Math.abs(s.time() - prevRed.time()) <= 3
							&& new Vec3(s.x() - prevRed.x(), s.y() - prevRed.y(), s.z() - prevRed.z()).lengthSqr() < 16.0) {
						double frame = Math.floor((now + partial) * 3.0);
						for (int bolt = 0; bolt < 2; bolt++) {
							double seed = frame * 3.7 + s.time() * 0.91 + bolt * 17.3;
							if (ThorDraw.hash(seed) < 0.3) {
								continue;
							}
							Vec3 a = new Vec3(prevRed.x(), prevRed.y() + 0.25 + ThorDraw.hash(seed + 1) * 1.45, prevRed.z()).subtract(cam);
							Vec3 b = new Vec3(s.x(), s.y() + 0.25 + ThorDraw.hash(seed + 2) * 1.45, s.z()).subtract(cam);
							arcs.add(ThorDraw.jagged(a, b, 5, 0.22, seed));
							arcAlpha.add(Math.min(1.0f, fade * 1.3f));
						}
					}
					prevRed = s;
					redIndex++;
				}
				int alpha = Math.round(Math.min(1.0f, fade) * s.alpha() * 255.0f);
				int color = (alpha << 24) | s.rgb();
				pose.pushPose();
				pose.translate(s.x() - cam.x, s.y() - cam.y, s.z() - cam.z);
				pose.mulPose(Axis.YP.rotationDegrees(180.0f - s.bodyYaw()));
				pose.scale(-1.0f, -1.0f, 1.0f);
				pose.scale(0.9375f, 0.9375f, 0.9375f);
				pose.translate(0.0f, -1.501f, 0.0f);
				model.attackTime = 0.0f;
				model.riding = false;
				model.young = false;
				model.crouching = s.crouching();
				SpeedRunPose.overrideWeight = s.run();
				SpeedRunPose.overrideOverdrive = s.overdrive();
				try {
					model.setupAnim(acp, s.limbPos(), s.limbSpeed(), acp.tickCount + partial,
							s.headYaw() - s.bodyYaw(), s.pitch());
				} finally {
					SpeedRunPose.overrideWeight = Float.NaN;
				}
				model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)),
						LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
				pose.popPose();
			}
		}
		if (!arcs.isEmpty()) {
			VertexConsumer vc = ThorDraw.buffer(buffers);
			PoseStack.Pose last = pose.last();
			for (int i = 0; i < arcs.size(); i++) {
				Vec3[] path = arcs.get(i);
				float a = arcAlpha.get(i);
				ThorDraw.ribbon(vc, last, path, 0.15f, 0xFF9A1A, 0.3f * a);
				ThorDraw.ribbon(vc, last, path, 0.06f, 0xFFD84A, 0.65f * a);
				ThorDraw.ribbon(vc, last, path, 0.022f, 0xFFFBEA, 0.95f * a);
			}
		}
	}
}
