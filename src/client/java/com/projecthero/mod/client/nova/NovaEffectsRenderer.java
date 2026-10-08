package com.projecthero.mod.client.nova;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.flight.LightTrail;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.shield.ForceBubbleRenderer;
import com.projecthero.mod.nova.Nova;
import com.projecthero.mod.shield.ForceBubble;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.nova.NovaConfig;
import com.projecthero.mod.nova.data.NovaState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.15.13: Nova's world effects, drawn from the synced {@link NovaState} for every viewer:
 * <ul>
 *   <li><b>Nova Blast</b> -- a golden beam from the outstretched hand to whatever it hits (a soft gold glow round a white-gold
 *       additive core with a cyan thread), a burning spot where it lands;</li>
 *   <li><b>Force Field</b> -- the shared {@link ForceBubble} in gold, while Z is held (flickering when the Nova Force is
 *       about to run dry);</li>
 *   <li><b>the flight trail</b> (v0.15.15) -- Green Lantern's {@link LightTrail} in Nova's gold, from the feet;</li>
 *   <li><b>Gravity Well</b> -- a black singularity with a golden accretion ring spinning round it, the rings tightening as
 *       it nears collapse.</li>
 * </ul>
 * Soft glows go in {@code debugQuads} (ordinary alpha), hot cores in {@code lightning} (additive); every glow first and
 * every core second, as {@link BeamDraw} explains.
 */
public final class NovaEffectsRenderer {
	private static final int GOLD = 0xFFC83C;
	private static final int GOLD_HOT = 0xFFF0B0;
	private static final int CYAN = 0x8BF8FF;

	private record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha) {
	}

	private record Ribbon(Vec3 a, Vec3 b, float width, int rgb, float alpha) {
	}

	private record Bubble(Vec3 center, float alpha, boolean self) {
	}

	/** v0.15.15: each flier's feet position at the end of each of the last few client ticks (newest first). */
	private static final Map<UUID, LightTrail.History> TRAILS = new HashMap<>();

	private NovaEffectsRenderer() {
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(NovaEffectsRenderer::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> tickTrails(client.level));
	}

	// ---------------------------------------------------------------- the flight trail (v0.15.15)

	/**
	 * Records every flying Nova's feet once a client tick; a landed one's trail shrinks away a point a tick. The soles sit
	 * on the entity position in every flight pose (the body lean pivots round the feet -- {@code PlayerRendererMixin}),
	 * so the trail is pinned there, sprint-flying flat out included.
	 */
	private static void tickTrails(ClientLevel level) {
		if (level == null) {
			TRAILS.clear();
			return;
		}
		long now = level.getGameTime();
		Set<UUID> seen = new HashSet<>();
		for (Player player : level.players()) {
			UUID id = player.getUUID();
			LightTrail.History h = TRAILS.get(id);
			if (Nova.isFlying(player)) {
				seen.add(id);
				if (h == null) {
					h = new LightTrail.History();
					TRAILS.put(id, h);
				}
				h.sample(player.position().add(0, 0.05, 0), now);
				// a few sparks shed just behind the feet
				Vec3 feet = player.position();
				Vec3 prev = player.getPosition(0f);
				if (feet.distanceToSqr(prev) > 0.04 && level.random.nextInt(2) == 0) {
					level.addParticle(ParticleTypes.END_ROD, prev.x, prev.y + 0.05, prev.z, 0.0, 0.0, 0.0);
				}
			} else if (h != null) {
				h.prune(now); // landed: what is left fades out
				if (!h.isEmpty()) {
					seen.add(id);
				}
			}
		}
		TRAILS.keySet().retainAll(seen);
	}

	/**
	 * v0.15.15 (user: "give the same trail to Nova please but just give it his colours"): Green Lantern's light trail
	 * ({@link LightTrail}) in Nova's gold with a pale warm-gold core and his cyan in the strands, from the feet, whenever
	 * he flies -- fainter cruising, full strength sprint-flying. Replaces the old single gold ribbon.
	 */
	private static void drawTrails(PoseStack poseStack, MultiBufferSource consumers, Vec3 cam, ClientLevel level, float partial,
			long now, float time) {
		for (java.util.Map.Entry<UUID, LightTrail.History> e : TRAILS.entrySet()) {
			Player player = level.getPlayerByUUID(e.getKey());
			if (player == null || player.isInvisible()) {
				continue;
			}
			boolean flying = Nova.isFlying(player);
			float strength = flying && player.isSprinting() ? 1f : 0.6f;
			Vec3 head = flying ? player.getPosition(partial).add(0, 0.05, 0) : null;
			e.getValue().draw(poseStack, consumers, cam, head, partial, now, time, LightTrail.NOVA, strength);
		}
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		PoseStack poseStack = context.matrixStack();
		MultiBufferSource consumers = context.consumers();
		if (level == null || poseStack == null || consumers == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = level.getGameTime();
		float time = now + partial;
		Vec3 cam = context.camera().getPosition();
		List<Ribbon> glow = new ArrayList<>();
		List<Ribbon> core = new ArrayList<>();
		List<Quad> soft = new ArrayList<>();
		List<Quad> hot = new ArrayList<>();
		List<Bubble> bubbles = new ArrayList<>();
		for (Player player : level.players()) {
			NovaState s = player.getAttachedOrElse(ModAttachments.NOVA_STATE, null);
			if (s == null || !s.hasPower || !s.suited) {
				continue;
			}
			boolean self = player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
			if (s.blasting) {
				beam(mc, level, player, partial, time, self, glow, core);
			}
			if (s.shieldUntil > now) {
				// v0.15.15: the held Force Field -- the shared ForceBubble, drawn after this batch; it flickers when the
				// Nova Force is about to run dry (under one second of upkeep left)
				boolean low = s.overloadUntil <= now && s.force < NovaConfig.SHIELD_COST_PER_SECOND;
				float flicker = low ? (Mth.sin(time * 2.2f) > 0 ? 1f : 0.35f) : 1f;
				bubbles.add(new Bubble(player.getPosition(partial).add(0, player.getBbHeight() * 0.5, 0), flicker, self));
			}

			if (s.overloadUntil > now && !self) {
				// NOVA OVERLOAD: a pulsing golden corona round the body
				Vec3 c = player.getPosition(partial).add(0, player.getBbHeight() * 0.5, 0);
				float k = 0.5f + 0.5f * Mth.sin(time * 0.35f);
				sphere(hot, c, 1.25 + 0.1 * k, 12, 16, GOLD, 0.05f + 0.04f * k);
				sphere(hot, c, 0.95, 10, 14, GOLD_HOT, 0.05f);
				for (int i = 0; i < 2; i++) {
					ring(core, c, 1.3, 1.1 + i * 0.9, time * 0.2 + i, 30, 0.03f, i == 0 ? GOLD_HOT : CYAN, 0.6f);
				}
			}
			if (s.wellUntil > now && s.wellPos.size() == 3) {
				Vec3 c = new Vec3(s.wellPos.get(0), s.wellPos.get(1), s.wellPos.get(2));
				float age = NovaConfig.WELL_TICKS - (s.wellUntil - now - partial);
				float t = Mth.clamp(age / NovaConfig.WELL_TICKS, 0f, 1f);
				sphere(soft, c, 0.45 + 0.25 * t, 10, 14, 0x08040C, 0.95f);
				sphere(hot, c, 0.62 + 0.25 * t, 10, 14, 0x8A50FF, 0.22f);
				for (int i = 0; i < 4; i++) {
					double r = (2.4 - 1.4 * t) * (1.0 - i * 0.18);
					ring(core, c, r, 1.25 + i * 0.12, time * (0.25 + i * 0.07), 36, 0.05f + 0.02f * i, i % 2 == 0 ? GOLD_HOT : CYAN,
							0.75f - i * 0.12f);
				}
				sphere(hot, c, 3.0 - 1.6 * t, 12, 16, GOLD, 0.06f); // additive: the rings inside must still show
			}
		}
		drawTrails(poseStack, consumers, cam, level, partial, now, time); // v0.15.15: the shared light trail, in gold
		for (Bubble b : bubbles) {
			ForceBubbleRenderer.draw(poseStack, consumers, cam, b.center(), ForceBubble.Style.NOVA, time, b.alpha(), b.self());
		}
		if (glow.isEmpty() && core.isEmpty() && soft.isEmpty() && hot.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose pose = poseStack.last();
		VertexConsumer vc = consumers.getBuffer(RenderType.debugQuads());
		for (Quad q : soft) {
			quad(vc, pose.pose(), q);
		}
		for (Ribbon r : glow) {
			BeamDraw.segment(vc, pose, r.a(), r.b(), cam, r.width(), r.width(), r.rgb(), r.alpha(), r.alpha());
		}
		VertexConsumer add = consumers.getBuffer(RenderType.lightning());
		for (Quad q : hot) {
			quad(add, pose.pose(), q);
		}
		for (Ribbon r : core) {
			BeamDraw.segment(add, pose, r.a(), r.b(), cam, r.width(), r.width(), r.rgb(), r.alpha(), r.alpha());
		}
		poseStack.popPose();
	}

	// ---------------------------------------------------------------- the beam

	/** The outstretched hand the beam leaves from (third person), or just below-right of the camera (first person). */
	static Vec3 handPos(Player player, float partial, boolean firstPerson) {
		Vec3 look = player.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		boolean rightHand = player.getMainArm() == HumanoidArm.RIGHT;
		Vec3 side = rightHand ? right : right.scale(-1);
		Vec3 eye = player.getEyePosition(partial);
		if (firstPerson) {
			return eye.add(look.scale(0.55)).add(side.scale(0.3)).add(0, -0.24, 0);
		}
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 bodyRight = new Vec3(-Math.cos(bodyYaw), 0, -Math.sin(bodyYaw));
		Vec3 shoulder = player.getPosition(partial).add(0, player.isCrouching() ? 1.05 : 1.33, 0)
				.add(bodyRight.scale(rightHand ? 0.33 : -0.33));
		return shoulder.add(look.scale(0.72));
	}

	private static void beam(Minecraft mc, ClientLevel level, Player player, float partial, float time, boolean firstPerson,
			List<Ribbon> glow, List<Ribbon> core) {
		Vec3 eye = player.getEyePosition(partial);
		Vec3 dir = player.getViewVector(partial);
		Vec3 end = hitPoint(level, player, eye, dir);
		Vec3 from = handPos(player, partial, firstPerson);
		float pulse = 0.85f + 0.15f * Mth.sin(time * 1.4f);
		float w = firstPerson ? 0.7f : 1f;
		glow.add(new Ribbon(from, end, 0.17f * pulse * w, GOLD, 0.45f * pulse * (firstPerson ? 0.7f : 1f)));
		core.add(new Ribbon(from, end, 0.075f * pulse * w, GOLD_HOT, 0.95f));
		core.add(new Ribbon(from, end, 0.022f * w, CYAN, 0.9f));
		// a few spiralling strands round the beam
		Vec3 d = end.subtract(from);
		double len = d.length();
		if (len > 0.5) {
			Vec3 n = d.normalize();
			Vec3 a = n.cross(new Vec3(0, 1, 0));
			a = a.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : a.normalize();
			Vec3 b = a.cross(n).normalize();
			int steps = (int) Math.min(80, len * 3);
			Vec3 prev = null;
			for (int i = 0; i <= steps; i++) {
				double t = i / (double) steps;
				double ang = t * len * 2.2 - time * 0.6;
				Vec3 p = from.add(d.scale(t)).add(a.scale(Math.cos(ang) * 0.14)).add(b.scale(Math.sin(ang) * 0.14));
				if (prev != null) {
					core.add(new Ribbon(prev, p, 0.015f, GOLD, 0.6f));
				}
				prev = p;
			}
		}
		// the burning spot
		float flare = 0.32f * pulse;
		Vec3 up = new Vec3(0, 1, 0);
		glow.add(new Ribbon(end.subtract(up.scale(flare)), end.add(up.scale(flare)), flare, GOLD, 0.4f));
		core.add(new Ribbon(end.subtract(up.scale(flare * 0.45)), end.add(up.scale(flare * 0.45)), flare * 0.45f, GOLD_HOT, 0.85f));
		// the glow at the palm
		core.add(new Ribbon(from.subtract(up.scale(0.09)), from.add(up.scale(0.09)), 0.09f, GOLD_HOT, 0.8f));
	}

	private static Vec3 hitPoint(ClientLevel level, Player player, Vec3 from, Vec3 dir) {
		Vec3 to = from.add(dir.scale(NovaConfig.BLAST_RANGE));
		BlockHitResult bhr = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? to : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(player, from, end, new AABB(from, end).inflate(1.0),
				e -> e != player && e.isPickable() && e instanceof LivingEntity && !(e instanceof ArmorStand) && !e.isSpectator(),
				from.distanceToSqr(end));
		return ehr != null ? ehr.getLocation() : end;
	}

	// ---------------------------------------------------------------- shapes

	private static void sphere(List<Quad> out, Vec3 c, double r, int lat, int lon, int rgb, float alpha) {
		for (int i = 0; i < lat; i++) {
			double t0 = Math.PI * i / lat;
			double t1 = Math.PI * (i + 1) / lat;
			for (int j = 0; j < lon; j++) {
				double p0 = 2 * Math.PI * j / lon;
				double p1 = 2 * Math.PI * (j + 1) / lon;
				out.add(new Quad(pt(c, r, t0, p0), pt(c, r, t1, p0), pt(c, r, t1, p1), pt(c, r, t0, p1), rgb, alpha));
			}
		}
	}

	private static Vec3 pt(Vec3 c, double r, double theta, double phi) {
		return c.add(r * Math.sin(theta) * Math.cos(phi), r * Math.cos(theta), r * Math.sin(theta) * Math.sin(phi));
	}

	/** A ring of ribbons of radius {@code r} round {@code c}, tilted by {@code tilt} about X then spun {@code spin} about Y. */
	private static void ring(List<Ribbon> out, Vec3 c, double r, double tilt, double spin, int seg, float width, int rgb, float alpha) {
		Vec3 prev = null;
		for (int i = 0; i <= seg; i++) {
			double a = 2 * Math.PI * i / seg;
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			double y = z * Math.sin(tilt);
			z = z * Math.cos(tilt);
			double xs = x * Math.cos(spin) - z * Math.sin(spin);
			double zs = x * Math.sin(spin) + z * Math.cos(spin);
			Vec3 p = c.add(xs, y, zs);
			if (prev != null) {
				out.add(new Ribbon(prev, p, width, rgb, alpha));
			}
			prev = p;
		}
	}

	private static void quad(VertexConsumer vc, Matrix4f m, Quad q) {
		int r = (q.rgb() >> 16) & 0xFF;
		int g = (q.rgb() >> 8) & 0xFF;
		int b = q.rgb() & 0xFF;
		int a = Math.max(0, Math.min(255, (int) (q.alpha() * 255)));
		Vec3[] v = { q.a(), q.b(), q.c(), q.d() };
		for (Vec3 p : v) {
			vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
		}
		for (int i = 3; i >= 0; i--) {
			vc.addVertex(m, (float) v[i].x, (float) v[i].y, (float) v[i].z).setColor(r, g, b, a);
		}
	}
}
