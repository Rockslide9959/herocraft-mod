package com.projecthero.mod.client.wolverine;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.wolverine.Wolverine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.16.1: the claw VFX, all client side and blood-free.
 * <ul>
 * <li><b>Slash trails</b>: only during the snap of a cut ({@link WolverineClawPose#trailing}) each blade tip is sampled
 * every frame and its path drawn as a thin, camera-facing additive arc -- pale steel-blue (red-tinged in Rage), a few
 * pixels wide, tapering toward its tail and gone {@link #TRAIL_TICKS} ticks later. Third person samples come from the
 * claws layer in world space (drawn after translucents); first person samples are taken and drawn in the hand's own
 * camera space so they line up with the first-person arm.</li>
 * <li><b>Strike sparks</b>: the tick a move strikes home, a few adamantium sparks burst in front of the
 * chest. Throttled: one burst per strike, a handful of particles, nothing
 * beyond {@link #FX_RANGE} blocks, halved on Decreased particles and skipped on Minimal.</li>
 * </ul>
 */
public final class WolverineClawFx {
	private static final float TRAIL_TICKS = 4.5f;
	private static final int MAX_SAMPLES = 32;
	private static final double FX_RANGE = 32.0;
	/** Arc half-width at its head: first person (view units) and third person (blocks). */
	private static final float WIDTH_FP = 0.0042f;
	private static final float WIDTH_WORLD = 0.02f;

	/** One frame's sample: the time and the three blade tips. */
	private record Sample(float time, Vector3f[] tips) {
	}

	private static final class Trail {
		final ArrayDeque<Sample> samples = new ArrayDeque<>();
		boolean rage;
	}

	/** World-space trails (third person), keyed by entity id * 2 + (right arm ? 1 : 0). */
	private static final Map<Integer, Trail> WORLD = new HashMap<>();
	/** The local player's first-person trails, per arm, in the hand's camera space. */
	private static final Trail[] FIRST_PERSON = {new Trail(), new Trail()};
	private static boolean inWorldRender;
	/** Per entity: the last tick's move progress, so a strike bursts exactly once. */
	private static final Map<Integer, Float> LAST_T = new HashMap<>();

	private WolverineClawFx() {
	}

	public static void initialize() {
		WorldRenderEvents.START.register(ctx -> inWorldRender = true);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(WolverineClawFx::renderWorld);
		WorldRenderEvents.END.register(ctx -> inWorldRender = false);
		ClientTickEvents.END_CLIENT_TICK.register(WolverineClawFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
			WORLD.clear();
			LAST_T.clear();
			FIRST_PERSON[0].samples.clear();
			FIRST_PERSON[1].samples.clear();
			WolverineClawCombo.clear();
		});
	}

	private static float now() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0.0f : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
	}

	private static Sample sample(Matrix4f m, boolean right, boolean slim, float ext, float length, Vec3 offset) {
		Vector3f[] tips = new Vector3f[3];
		for (int i = 0; i < 3; i++) {
			tips[i] = m.transformPosition(WolverineClawsModel.bladePoint(right, slim, ext, length, i, 1.0f));
			if (offset != null) {
				tips[i].add((float) offset.x, (float) offset.y, (float) offset.z);
			}
		}
		return new Sample(now(), tips);
	}

	private static void push(Trail trail, Sample s, boolean rage) {
		trail.rage = rage;
		trail.samples.addLast(s);
		while (trail.samples.size() > MAX_SAMPLES) {
			trail.samples.removeFirst();
		}
	}

	/** Third person: called by the claws layer with the arm-bone pose (camera-relative) for each hand. */
	public static void recordWorld(AbstractClientPlayer player, boolean right, boolean active, Matrix4f armPose, boolean slim,
			float ext, float length) {
		Minecraft mc = Minecraft.getInstance();
		if (!inWorldRender || !active || (player == mc.player && mc.options.getCameraType().isFirstPerson())) {
			return; // not in the world (GUI preview), not cutting, or first person (which has its own trail)
		}
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		int key = player.getId() * 2 + (right ? 1 : 0);
		push(WORLD.computeIfAbsent(key, k -> new Trail()), sample(armPose, right, slim, ext, length, cam), Wolverine.raging(player));
	}

	/** First person: sample and draw this hand's trail in the hand's camera space (the camera is the origin). */
	public static void firstPerson(AbstractClientPlayer player, boolean right, Matrix4f armPose, boolean slim, float ext,
			MultiBufferSource buffers) {
		Trail trail = FIRST_PERSON[right ? 1 : 0];
		HumanoidArm arm = right ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
		if (WolverineClawPose.trailing(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false), arm)) {
			push(trail, sample(armPose, right, slim, ext, WolverineClawsModel.FIRST_PERSON_LENGTH, null), Wolverine.raging(player));
		}
		expire(trail, now());
		if (trail.samples.size() < 2) {
			return;
		}
		draw(buffers.getBuffer(RenderType.lightning()), new PoseStack().last(), trail, now(), Vec3.ZERO, WIDTH_FP, 0.9f);
	}

	private static void expire(Trail trail, float now) {
		while (!trail.samples.isEmpty() && (now - trail.samples.peekFirst().time() > TRAIL_TICKS
				|| now < trail.samples.peekFirst().time() - 1.0f)) {
			trail.samples.removeFirst();
		}
	}

	private static void renderWorld(WorldRenderContext ctx) {
		if (WORLD.isEmpty() || ctx.consumers() == null) {
			return;
		}
		float t = now();
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		VertexConsumer vc = ctx.consumers().getBuffer(RenderType.lightning());
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		Iterator<Trail> it = WORLD.values().iterator();
		while (it.hasNext()) {
			Trail trail = it.next();
			expire(trail, t);
			if (trail.samples.isEmpty()) {
				it.remove();
				continue;
			}
			draw(vc, pose.last(), trail, t, cam, WIDTH_WORLD, 0.85f);
		}
		pose.popPose();
	}

	/** Each blade tip's path as a thin camera-facing arc: widest and brightest at the head, thinning and fading behind. */
	private static void draw(VertexConsumer vc, PoseStack.Pose pose, Trail trail, float now, Vec3 camera, float width,
			float strength) {
		if (trail.samples.size() < 2) {
			return;
		}
		int rgb = trail.rage ? 0xFF9C9C : 0xB4DCFF;
		Sample prev = null;
		for (Sample s : trail.samples) {
			if (prev != null) {
				float f0 = life(now - prev.time());
				float f1 = life(now - s.time());
				for (int i = 0; i < 3; i++) {
					// the middle blade's arc is the brightest; the outer two are fainter so they read as one set of marks
					float k = (i == 1 ? 1.0f : 0.6f) * strength;
					BeamDraw.segment(vc, pose, vec(prev.tips()[i]), vec(s.tips()[i]), camera, width * f0, width * f1, rgb,
							k * f0 * f0, k * f1 * f1);
				}
			}
			prev = s;
		}
	}

	private static Vec3 vec(Vector3f p) {
		return new Vec3(p.x, p.y, p.z);
	}

	/** 1 when fresh, 0 when {@link #TRAIL_TICKS} old. */
	private static float life(float age) {
		return Math.max(0.0f, 1.0f - age / TRAIL_TICKS);
	}

	// ------------------------------------------------------------------ strike sparks

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused()) {
			return;
		}
		ParticleStatus particles = mc.options.particles().get();
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		for (AbstractClientPlayer p : level.players()) {
			if (!Wolverine.hasPower(p)) {
				continue;
			}
			WolverineClawPose.Move m = WolverineClawPose.current(p, 0.0f);
			Float prev = LAST_T.get(p.getId());
			float t = m.kind() == WolverineClawPose.Kind.NONE ? -1.0f : m.t();
			LAST_T.put(p.getId(), t);
			if (m.kind() == WolverineClawPose.Kind.NONE || m.kind() == WolverineClawPose.Kind.CHARGE
					|| m.kind() == WolverineClawPose.Kind.ROAR || m.kind() == WolverineClawPose.Kind.LEAP
					|| m.kind() == WolverineClawPose.Kind.PLUNGE) {
				continue;
			}
			float impact = WolverineClawPose.impactAt(m.kind());
			// a fresh move restarts t near 0: treat anything that went backwards as "before the strike"
			float before = prev == null || prev > t ? -1.0f : prev;
			if (!(before < impact && t >= impact) || particles == ParticleStatus.MINIMAL
					|| p.position().distanceToSqr(cam) > FX_RANGE * FX_RANGE) {
				continue;
			}
			burst(level, p, m, particles == ParticleStatus.DECREASED);
		}
		if (LAST_T.size() > 64) {
			LAST_T.keySet().removeIf(id -> level.getEntity(id) == null);
		}
	}

	/** A few sparks where the claws finish, in front of the chest. */
	private static void burst(ClientLevel level, Player p, WolverineClawPose.Move m, boolean fewer) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0, flat.x);
		Vec3 centre = p.getEyePosition().add(look.scale(1.25)).add(0, -0.35, 0);
		int sparks = fewer ? 2 : 5;
		boolean both = m.kind() == WolverineClawPose.Kind.CROSS || m.kind() == WolverineClawPose.Kind.THRUST
				|| m.kind() == WolverineClawPose.Kind.WHIRL || m.kind() == WolverineClawPose.Kind.SLAM;
		for (HumanoidArm arm : HumanoidArm.values()) {
			if (!both && arm != m.arm()) {
				continue;
			}
			// the strike ends across the body: the right claws finish on the left and vice versa
			double side = (arm == HumanoidArm.RIGHT ? -0.35 : 0.35) * (m.kind() == WolverineClawPose.Kind.THRUST ? 0.3 : 1.0);
			Vec3 at = centre.add(right.scale(side));
			for (int i = 0; i < sparks; i++) {
				double vx = (level.random.nextDouble() - 0.5) * 0.3 + look.x * 0.1;
				double vy = level.random.nextDouble() * 0.15;
				double vz = (level.random.nextDouble() - 0.5) * 0.3 + look.z * 0.1;
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, vx, vy, vz);
			}
			level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, look.x * 0.2, 0.05, look.z * 0.2);
		}
	}
}
