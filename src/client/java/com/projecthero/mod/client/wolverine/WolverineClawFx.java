package com.projecthero.mod.client.wolverine;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
 * <li><b>Slash trails</b>: while a claw move is in its cutting phase ({@link WolverineClawPose#trailing}), the outer
 * half of each blade is sampled every frame and the swept surface between samples is drawn as a thin additive ribbon --
 * a pale steel-blue arc (red-tinged during Rage) that fades out over {@link #TRAIL_TICKS} ticks. Third person samples
 * come from the claws layer in world space (drawn after translucents); first person samples are taken and drawn in the
 * hand's own camera space so they line up with the first-person arm.</li>
 * <li><b>Strike sparks</b>: the tick a move strikes home, a few adamantium sparks (and, for a left-click slash, one
 * vanilla sweep arc) burst at the claw tips. Throttled: one burst per strike, a handful of particles, nothing beyond
 * {@link #FX_RANGE} blocks, halved on Decreased particles and skipped on Minimal.</li>
 * </ul>
 */
public final class WolverineClawFx {
	private static final float TRAIL_TICKS = 3.5f;
	private static final int MAX_SAMPLES = 24;
	private static final double FX_RANGE = 32.0;
	/** Where along a blade the ribbon starts (0 root .. 1 tip). */
	private static final float RIBBON_FROM = 0.4f;

	/** One frame's sample: the time and, per blade, the ribbon's inner and outer point. */
	private record Sample(float time, Vector3f[] inner, Vector3f[] outer) {
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
	/** Per entity: the last tick's move, so a strike bursts exactly once. */
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
		Vector3f[] inner = new Vector3f[3];
		Vector3f[] outer = new Vector3f[3];
		for (int i = 0; i < 3; i++) {
			inner[i] = m.transformPosition(WolverineClawsModel.bladePoint(right, slim, ext, length, i, RIBBON_FROM));
			outer[i] = m.transformPosition(WolverineClawsModel.bladePoint(right, slim, ext, length, i, 1.0f));
			if (offset != null) {
				inner[i].add((float) offset.x, (float) offset.y, (float) offset.z);
				outer[i].add((float) offset.x, (float) offset.y, (float) offset.z);
			}
		}
		return new Sample(now(), inner, outer);
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
		if (!inWorldRender || (player == mc.player && mc.options.getCameraType().isFirstPerson())) {
			return; // the inventory / GUI player preview is not in the world; first person has its own trail
		}
		int key = player.getId() * 2 + (right ? 1 : 0);
		if (!active) {
			return; // the samples already taken fade out on their own
		}
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		push(WORLD.computeIfAbsent(key, k -> new Trail()), sample(armPose, right, slim, ext, length, cam), Wolverine.raging(player));
	}

	/** First person: sample and draw this hand's trail in the hand's camera space. */
	public static void firstPerson(AbstractClientPlayer player, boolean right, Matrix4f armPose, boolean slim, float ext,
			MultiBufferSource buffers) {
		Trail trail = FIRST_PERSON[right ? 1 : 0];
		HumanoidArm arm = right ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
		if (WolverineClawPose.trailing(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false), arm)) {
			push(trail, sample(armPose, right, slim, ext, WolverineClawsModel.FIRST_PERSON_LENGTH, null), Wolverine.raging(player));
		}
		if (trail.samples.size() < 2) {
			expire(trail, now());
			return;
		}
		draw(buffers.getBuffer(RenderType.lightning()), new PoseStack().last(), trail, now(), 0.5f);
	}

	private static void expire(Trail trail, float now) {
		while (!trail.samples.isEmpty() && (now - trail.samples.peekFirst().time() > TRAIL_TICKS || now < trail.samples.peekFirst().time() - 1.0f)) {
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
			draw(vc, pose.last(), trail, t, 0.42f);
		}
		pose.popPose();
	}

	/** The ribbon: for each pair of consecutive samples, per blade, a quad from inner to outer point, fading with age. */
	private static void draw(VertexConsumer vc, PoseStack.Pose pose, Trail trail, float now, float strength) {
		expire(trail, now);
		if (trail.samples.size() < 2) {
			return;
		}
		int r = trail.rage ? 255 : 150;
		int g = trail.rage ? 140 : 200;
		int b = trail.rage ? 150 : 255;
		Matrix4f m = pose.pose();
		Sample prev = null;
		for (Sample s : trail.samples) {
			if (prev != null) {
				float a0 = alpha(now - prev.time(), strength);
				float a1 = alpha(now - s.time(), strength);
				for (int i = 0; i < 3; i++) {
					quad(vc, m, prev.inner()[i], prev.outer()[i], s.outer()[i], s.inner()[i], r, g, b, a0, a1);
				}
			}
			prev = s;
		}
	}

	private static float alpha(float age, float strength) {
		float f = 1.0f - age / TRAIL_TICKS;
		return Math.max(0.0f, f * f) * strength;
	}

	/** Both windings (the lightning buffer culls back faces); the inner edge is transparent, the tip edge bright. */
	private static void quad(VertexConsumer vc, Matrix4f m, Vector3f in0, Vector3f out0, Vector3f out1, Vector3f in1,
			int r, int g, int b, float a0, float a1) {
		int i0 = 0;
		int o0 = (int) (255 * Math.min(1.0f, a0));
		int o1 = (int) (255 * Math.min(1.0f, a1));
		vc.addVertex(m, in0.x, in0.y, in0.z).setColor(r, g, b, i0);
		vc.addVertex(m, out0.x, out0.y, out0.z).setColor(r, g, b, o0);
		vc.addVertex(m, out1.x, out1.y, out1.z).setColor(r, g, b, o1);
		vc.addVertex(m, in1.x, in1.y, in1.z).setColor(r, g, b, i0);
		vc.addVertex(m, in1.x, in1.y, in1.z).setColor(r, g, b, i0);
		vc.addVertex(m, out1.x, out1.y, out1.z).setColor(r, g, b, o1);
		vc.addVertex(m, out0.x, out0.y, out0.z).setColor(r, g, b, o0);
		vc.addVertex(m, in0.x, in0.y, in0.z).setColor(r, g, b, i0);
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
					|| m.kind() == WolverineClawPose.Kind.ROAR) {
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

	/** A few sparks at the claw tips, in front of the chest on the striking side(s); plus a sweep arc for a combo slash. */
	private static void burst(ClientLevel level, Player p, WolverineClawPose.Move m, boolean fewer) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0, flat.x);
		Vec3 centre = p.getEyePosition().add(look.scale(1.25)).add(0, -0.35, 0);
		int sparks = fewer ? 2 : 5;
		boolean both = m.kind() == WolverineClawPose.Kind.CROSS || m.kind() == WolverineClawPose.Kind.THRUST;
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
		if (m.kind() == WolverineClawPose.Kind.SLASH || m.kind() == WolverineClawPose.Kind.CROSS) {
			level.addParticle(ParticleTypes.SWEEP_ATTACK, centre.x, centre.y, centre.z, 0.0, 0.0, 0.0);
		}
	}
}
