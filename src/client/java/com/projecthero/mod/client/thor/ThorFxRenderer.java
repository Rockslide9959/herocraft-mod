package com.projecthero.mod.client.thor;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.thorarmor.ThorArmor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4: Thor's ability effects drawn in code, read off each player's synced {@link ThorFx} so every viewer sees them:
 * <ul>
 *   <li>Thunderclap: a shockwave ring that races out over the ground with a wall of light on its crest and lightning
 *   crawling out along the ground under it, timed to the slam in {@code ThorPose}.</li>
 *   <li>Lightning Strike: a small blast ring where the bolt lands. God of Thunder's Wrath: two huge rings rolling out
 *   from the impact, ground lightning and a fading blaze of light at the centre.</li>
 *   <li>The Wrath charge: a ball of lightning swelling on the raised hammer, arcs lashing out from it round the caster,
 *   a crackling ring at their feet, and -- past half charge -- bolts reaching down from the sky into the hammer.</li>
 *   <li>The suit-up: a bolt from the sky onto each later piece of Thor's Armour as it arrives (the boots get the real
 *   vanilla bolt the server calls down).</li>
 * </ul>
 */
public final class ThorFxRenderer {
	private static final double MAX_DISTANCE_SQ = 128.0 * 128.0;
	private static final int CLAP_DELAY = 2;
	private static final int CLAP_TICKS = 16;
	private static final int STRIKE_TICKS = 10;
	private static final int WRATH_TICKS = 26;
	private static final int SUIT_BOLT_TICKS = 5;

	private ThorFxRenderer() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(ThorFxRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		double now = client.level.getGameTime() + partial;
		Vec3 cam = context.camera().getPosition();
		PoseStack poseStack = context.matrixStack();
		MultiBufferSource consumers = context.consumers();
		VertexConsumer vc = null;
		for (Player player : client.level.players()) {
			ThorFx fx = player.getAttachedOrElse(ModAttachments.THOR_FX, null);
			if (fx == null || fx.equals(ThorFx.EMPTY) || player.distanceToSqr(cam) > MAX_DISTANCE_SQ) {
				continue;
			}
			if (vc == null) {
				vc = ThorDraw.buffer(consumers);
			}
			Vec3 feet = new Vec3(Mth.lerp(partial, player.xo, player.getX()), Mth.lerp(partial, player.yo, player.getY()),
					Mth.lerp(partial, player.zo, player.getZ()));
			double seed = player.getId() * 7.31;
			double sinceAnim = now - fx.animStart();
			Vec3 at = new Vec3(fx.x(), fx.y(), fx.z());
			switch (fx.anim()) {
				case ThorFx.ANIM_THUNDERCLAP -> thunderclap(poseStack, vc, feet.subtract(cam), sinceAnim - CLAP_DELAY, seed);
				case ThorFx.ANIM_STRIKE -> strike(poseStack, vc, at.subtract(cam), sinceAnim);
				case ThorFx.ANIM_WRATH -> wrath(poseStack, vc, at.subtract(cam), sinceAnim, seed);
				case ThorFx.ANIM_TWIN_THUNDER -> twinThunder(poseStack, vc, player, feet, cam, sinceAnim, seed, partial);
				default -> {
				}
			}
			// v0.16.1: the Thunder Field lying on the ground, and the storm buff's crackle round him
			if (now >= fx.fieldStart() && now <= fx.fieldEnd() + 8) {
				thunderField(poseStack, vc, fx, cam, now, seed);
			}
			if (fx.stormPct() > 0 && !ThorLightningArcClient.firstPersonSelf(player)) {
				stormCrackle(poseStack, vc, feet.subtract(cam), player.getBbHeight(), now, seed);
			}
			if (fx.has(ThorFx.CH_WRATH)) {
				wrathCharge(poseStack, vc, player, feet, cam, now - fx.chargeStart(), now, seed, partial);
			}
			if (fx.suitDir() == ThorFx.SUIT_UP) {
				suitBolts(poseStack, vc, feet.subtract(cam), now - fx.suitStart(), seed);
			}
		}
	}

	// ---------------- thunderclap ----------------

	private static void thunderclap(PoseStack poseStack, VertexConsumer vc, Vec3 feet, double t, double seed) {
		if (t < 0 || t >= CLAP_TICKS) {
			return;
		}
		float k = (float) (t / CLAP_TICKS);
		float grow = 1f - (1f - k) * (1f - k) * (1f - k);
		float r = 0.6f + 7.4f * grow;
		float fade = 1f - k;
		Vec3 c = feet.add(0, 0.06, 0);
		PoseStack.Pose pose = poseStack.last();
		ThorDraw.ring(vc, pose, c, Math.max(0f, r - 1.4f), r, ThorDraw.GLOW, 0.75f * fade, 56);
		ThorDraw.ring(vc, pose, c, Math.max(0f, r - 0.35f), r + 0.1f, ThorDraw.CORE, 0.8f * fade, 56);
		ThorDraw.wall(vc, pose, c, r, 1.1f * fade + 0.2f, ThorDraw.GLOW, 0.45f * fade, 56);
		// the inner, slower ring of dust and light
		float r2 = 0.4f + 4.2f * grow;
		ThorDraw.ring(vc, pose, c, Math.max(0f, r2 - 1.0f), r2, ThorDraw.HALO, 0.35f * fade, 40);
		groundArcs(vc, pose, c, r, t < 8 ? (float) (1.0 - t / 8.0) : 0f, 8, seed + Math.floor(t * 0.5) * 11.0);
	}

	/** Lightning crawling out over the ground from {@code c} to radius {@code r}. */
	private static void groundArcs(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, float r, float alpha, int count, double seed) {
		if (alpha <= 0.01f) {
			return;
		}
		for (int i = 0; i < count; i++) {
			double ang = Math.PI * 2.0 * (i + ThorDraw.hash(seed + i) * 0.6) / count;
			double len = r * (0.65 + 0.35 * ThorDraw.hash(seed + i * 3.1));
			Vec3 end = c.add(Math.cos(ang) * len, 0.05 + ThorDraw.hash(seed + i * 5.3) * 0.2, Math.sin(ang) * len);
			Vec3[] path = ThorDraw.jagged(c.add(0, 0.1, 0), end, 6, 0.35, seed + i * 17.0);
			ThorDraw.bolt(vc, pose, path, 0.6f, alpha);
		}
	}

	// ---------------- lightning strike / god of thunder's wrath ----------------

	private static void strike(PoseStack poseStack, VertexConsumer vc, Vec3 at, double t) {
		if (t < 0 || t >= STRIKE_TICKS) {
			return;
		}
		float k = (float) (t / STRIKE_TICKS);
		float fade = 1f - k;
		float r = 0.4f + 2.8f * (1f - (1f - k) * (1f - k));
		Vec3 c = at.add(0, 0.08, 0);
		PoseStack.Pose pose = poseStack.last();
		ThorDraw.ring(vc, pose, c, Math.max(0f, r - 0.8f), r, ThorDraw.GLOW, 0.8f * fade, 36);
		ThorDraw.wall(vc, pose, c, r, 0.6f * fade, ThorDraw.GLOW, 0.4f * fade, 36);
		ThorDraw.flare(vc, poseStack, c.add(0, 0.3, 0), 0.5f * fade + 0.1f, (float) t * 30f, 0.8f * fade);
	}

	private static void wrath(PoseStack poseStack, VertexConsumer vc, Vec3 at, double t, double seed) {
		if (t < 0 || t >= WRATH_TICKS) {
			return;
		}
		PoseStack.Pose pose = poseStack.last();
		Vec3 c = at.add(0, 0.1, 0);
		for (int wave = 0; wave < 2; wave++) {
			double tw = t - wave * 4;
			if (tw < 0 || tw >= WRATH_TICKS - wave * 4) {
				continue;
			}
			float k = (float) (tw / (WRATH_TICKS - wave * 4));
			float fade = 1f - k;
			float r = 1f + (wave == 0 ? 10f : 7f) * (1f - (1f - k) * (1f - k) * (1f - k));
			ThorDraw.ring(vc, pose, c, Math.max(0f, r - 2.0f), r, ThorDraw.GLOW, 0.8f * fade, 72);
			ThorDraw.ring(vc, pose, c, Math.max(0f, r - 0.45f), r + 0.15f, ThorDraw.CORE, 0.85f * fade, 72);
			ThorDraw.wall(vc, pose, c, r, (wave == 0 ? 2.6f : 1.6f) * fade + 0.3f, ThorDraw.GLOW, 0.5f * fade, 72);
		}
		float k = (float) (t / WRATH_TICKS);
		groundArcs(vc, pose, c, 8.5f, t < 12 ? (float) (1.0 - t / 12.0) : 0f, 12, seed + Math.floor(t * 0.5) * 7.0);
		ThorDraw.flare(vc, poseStack, c.add(0, 0.6, 0), 1.6f * (1f - k) + 0.2f, (float) t * 25f, 0.9f * (1f - k));
	}

	// ---------------- the wrath charge ----------------

	private static void wrathCharge(PoseStack poseStack, VertexConsumer vc, Player player, Vec3 feet, Vec3 cam, double held,
			double now, double seed, float partial) {
		float b = (float) Math.max(0.0, Math.min(1.0, held / 100.0));
		float in = (float) Math.min(1.0, held / 6.0);
		Vec3 head = hammerRaised(player, feet, partial).subtract(cam);
		Vec3 base = feet.subtract(cam);
		PoseStack.Pose pose = poseStack.last();
		double frame = Math.floor(now * 0.4);
		float spin = (float) (now * 30.0 % 360.0);
		ThorDraw.flare(vc, poseStack, head, (0.14f + 0.36f * b) * (0.85f + 0.15f * (float) ThorDraw.hash(seed + frame)), spin, in);

		// arcs lashing out from the hammer to points circling the caster
		int arcs = 2 + (int) (4 * b);
		for (int i = 0; i < arcs; i++) {
			double ang = now * 0.12 + Math.PI * 2.0 * i / arcs + ThorDraw.hash(seed + frame + i) * 0.7;
			double rad = 1.2 + 0.5 * ThorDraw.hash(seed + frame * 3.0 + i);
			double y = 0.2 + 1.9 * ThorDraw.hash(seed + frame * 5.0 + i * 2.0);
			Vec3 end = base.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
			ThorDraw.bolt(vc, pose, ThorDraw.jagged(head, end, 6, 0.3, seed + frame * 13.0 + i * 7.0), 0.55f, 0.75f * in);
		}
		// a crackling ring round their feet, brighter as it builds
		ThorDraw.ring(vc, pose, base.add(0, 0.05, 0), 1.2f, 1.9f, ThorDraw.GLOW, (0.2f + 0.5f * b) * in, 40);
		// past half charge the sky answers: bolts reaching down into the hammer
		if (b > 0.5f && ThorDraw.hash(seed + frame * 2.0 + 0.3) < 0.35 + 0.5 * b) {
			double jx = (ThorDraw.hash(seed + frame * 1.1) - 0.5) * 6.0;
			double jz = (ThorDraw.hash(seed + frame * 1.9) - 0.5) * 6.0;
			Vec3 sky = head.add(jx, 18.0, jz);
			ThorDraw.bolt(vc, pose, ThorDraw.jagged(sky, head, 14, 1.4, seed + frame * 17.0), 1.1f, (b - 0.5f) * 2f);
		}
	}

	/** Roughly where Mjolnir's head is while it is held straight up (the Wrath charge pose). */
	private static Vec3 hammerRaised(Player player, Vec3 feet, float partial) {
		float yaw = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		HumanoidArm arm = player.getMainArm();
		if (!player.getMainHandItem().is(ModItems.MJOLNIR) && player.getOffhandItem().is(ModItems.MJOLNIR)) {
			arm = arm.getOpposite();
		}
		double side = arm == HumanoidArm.RIGHT ? 0.36 : -0.36;
		return feet.add(0.0, player.getBbHeight() * 1.38, 0.0).add(right.scale(side));
	}

	// ---------------- v0.16.1: Twin Thunder Strike ----------------

	private static final int TWIN_BOLT = com.projecthero.mod.power.ThorTwinThunder.BOLT_TICK;
	private static final int TWIN_SLAM = com.projecthero.mod.power.ThorTwinThunder.SLAM_TICK;
	private static final int TWIN_WAVE_TICKS = 18;

	/** Roughly where the two raised weapon heads are in the Twin Thunder pose (both arms up in a V). */
	private static Vec3[] twinHeads(Player player, Vec3 feet, float partial) {
		float yaw = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Vec3 top = feet.add(0.0, player.getBbHeight() * 1.42, 0.0);
		return new Vec3[] { top.add(right.scale(0.8)), top.add(right.scale(-0.8)) };
	}

	private static void twinThunder(PoseStack poseStack, VertexConsumer vc, Player player, Vec3 feet, Vec3 cam, double t,
			double seed, float partial) {
		PoseStack.Pose pose = poseStack.last();
		if (t >= TWIN_BOLT - 3 && t < TWIN_SLAM + 1) {
			// lightning arcing between the two raised weapons, building from a flicker to a torrent once struck
			Vec3[] h = twinHeads(player, feet, partial);
			Vec3 a = h[0].subtract(cam);
			Vec3 b = h[1].subtract(cam);
			double frame = Math.floor((t + 100.0) * 0.6);
			boolean struck = t >= TWIN_BOLT;
			int arcs = struck ? 4 : 1;
			float alpha = struck ? 0.95f : (float) Math.max(0.0, (t - (TWIN_BOLT - 3)) / 3.0) * 0.6f;
			for (int i = 0; i < arcs; i++) {
				Vec3 mid = a.lerp(b, 0.5).add(0.0, 0.25 + 0.35 * ThorDraw.hash(seed + frame + i * 3.0), 0.0);
				Vec3[] p1 = ThorDraw.jagged(a, mid, 5, 0.18, seed + frame * 7.0 + i * 11.0);
				Vec3[] p2 = ThorDraw.jagged(mid, b, 5, 0.18, seed + frame * 9.0 + i * 13.0);
				ThorDraw.bolt(vc, pose, p1, struck ? 0.75f : 0.45f, alpha);
				ThorDraw.bolt(vc, pose, p2, struck ? 0.75f : 0.45f, alpha);
			}
			if (struck) {
				float spin = (float) (t * 35.0 % 360.0);
				ThorDraw.flare(vc, poseStack, a, 0.38f, spin, 0.9f);
				ThorDraw.flare(vc, poseStack, b, 0.38f, -spin, 0.9f);
				// stray arcs lashing down from the weapons round him
				for (int i = 0; i < 3; i++) {
					double ang = Math.PI * 2.0 * ThorDraw.hash(seed + frame * 5.0 + i);
					Vec3 end = feet.subtract(cam).add(Math.cos(ang) * 1.4, 0.1 + ThorDraw.hash(seed + frame + i * 2.0) * 1.2,
							Math.sin(ang) * 1.4);
					ThorDraw.bolt(vc, pose, ThorDraw.jagged(i % 2 == 0 ? a : b, end, 6, 0.3, seed + frame * 3.0 + i * 17.0),
							0.5f, 0.7f);
				}
			}
		}
		double ts = t - TWIN_SLAM;
		if (ts >= 0 && ts < TWIN_WAVE_TICKS + 8) {
			Vec3 c = feet.subtract(cam).add(0, 0.06, 0);
			float k = (float) Math.min(1.0, ts / TWIN_WAVE_TICKS);
			float grow = 1f - (1f - k) * (1f - k) * (1f - k);
			float fade = 1f - k;
			float r = 0.8f + 7.2f * grow;
			if (ts < TWIN_WAVE_TICKS) {
				ThorDraw.ring(vc, pose, c, Math.max(0f, r - 1.8f), r, ThorDraw.GLOW, 0.85f * fade, 72);
				ThorDraw.ring(vc, pose, c, Math.max(0f, r - 0.4f), r + 0.12f, ThorDraw.CORE, 0.9f * fade, 72);
				ThorDraw.wall(vc, pose, c, r, 1.8f * fade + 0.25f, ThorDraw.GLOW, 0.5f * fade, 72);
				groundArcs(vc, pose, c, 6.5f, ts < 10 ? (float) (1.0 - ts / 10.0) : 0f, 14, seed + Math.floor(ts * 0.5) * 9.0);
				ThorDraw.flare(vc, poseStack, c.add(0, 0.5, 0), 1.8f * fade + 0.2f, (float) ts * 25f, 0.95f * fade);
			}
			// the ground cracks: glowing fissures radiating out to the edge of the blast, cooling over the last ticks
			float crackA = (float) (ts < 6 ? 1.0 : Math.max(0.0, 1.0 - (ts - 6) / (TWIN_WAVE_TICKS + 2.0)));
			int cracks = 12;
			for (int i = 0; i < cracks; i++) {
				double ang = Math.PI * 2.0 * (i + ThorDraw.hash(seed + i * 1.7) * 0.5) / cracks;
				double len = (2.6 + 3.8 * ThorDraw.hash(seed + i * 2.9)) * Math.min(1.0, ts / 3.0 + 0.3);
				Vec3 end = c.add(Math.cos(ang) * len, 0.02, Math.sin(ang) * len);
				Vec3[] path = ThorDraw.jagged(c.add(Math.cos(ang) * 0.5, 0.02, Math.sin(ang) * 0.5), end, 7, 0.28,
						seed + i * 31.0);
				for (int n = 0; n < path.length; n++) {
					path[n] = new Vec3(path[n].x, c.y + 0.02, path[n].z); // flat on the ground
				}
				ThorDraw.ribbon(vc, pose, path, 0.12f, ThorDraw.GLOW, 0.55f * crackA);
				ThorDraw.ribbon(vc, pose, path, 0.04f, ThorDraw.CORE, 0.85f * crackA);
			}
		}
	}

	// ---------------- v0.16.1: Thunder Field ----------------

	private static void thunderField(PoseStack poseStack, VertexConsumer vc, ThorFx fx, Vec3 cam, double now, double seed) {
		double life = now - fx.fieldStart();
		double left = fx.fieldEnd() - now;
		float alpha = (float) Math.min(1.0, Math.min(life / 3.0, Math.max(0.0, (left + 8.0) / 8.0)));
		if (alpha <= 0.01f) {
			return;
		}
		PoseStack.Pose pose = poseStack.last();
		float r = fx.fieldRadius();
		Vec3 c = new Vec3(fx.fieldX(), fx.fieldY(), fx.fieldZ()).subtract(cam).add(0, 0.06, 0);
		double s = seed + fx.fieldStart() * 0.37;
		// the electrified ground: a faint glow over the disc and a crackling bright rim
		float pulse = 0.85f + 0.15f * (float) Math.sin(now * 0.9);
		ThorDraw.ring(vc, pose, c, 0f, r, ThorDraw.HALO, 0.22f * alpha * pulse, 48);
		ThorDraw.ring(vc, pose, c, Math.max(0f, r - 0.7f), r + 0.05f, ThorDraw.GLOW, 0.6f * alpha * pulse, 64);
		ThorDraw.wall(vc, pose, c, r, 0.35f + 0.15f * pulse, ThorDraw.GLOW, 0.25f * alpha, 64);
		// arcs crawling across the ground between points of the disc, re-struck every couple of ticks
		double frame = Math.floor(now * 0.5);
		int arcs = 9;
		for (int i = 0; i < arcs; i++) {
			double a0 = Math.PI * 2.0 * ThorDraw.hash(s + frame * 1.3 + i * 4.1);
			double r0 = r * Math.sqrt(ThorDraw.hash(s + frame * 2.7 + i * 5.3)) * 0.9;
			double a1 = a0 + (ThorDraw.hash(s + frame * 3.1 + i * 6.7) - 0.5) * 2.4;
			double r1 = r * Math.sqrt(ThorDraw.hash(s + frame * 4.9 + i * 7.9)) * 0.95;
			Vec3 p0 = c.add(Math.cos(a0) * r0, 0.05, Math.sin(a0) * r0);
			Vec3 p1 = c.add(Math.cos(a1) * r1, 0.05, Math.sin(a1) * r1);
			Vec3[] path = ThorDraw.jagged(p0, p1, 6, 0.35, s + frame * 11.0 + i * 17.0);
			for (int n = 1; n < path.length - 1; n++) {
				path[n] = new Vec3(path[n].x, c.y + 0.05 + Math.abs(path[n].y - c.y - 0.05) * 0.4, path[n].z);
			}
			ThorDraw.bolt(vc, pose, path, 0.5f, 0.85f * alpha);
		}
		// arcs from the weapon at the centre out to the rim
		for (int i = 0; i < 4; i++) {
			double ang = Math.PI * 2.0 * ThorDraw.hash(s + frame * 2.1 + i * 9.3);
			Vec3 end = c.add(Math.cos(ang) * r * 0.95, 0.05, Math.sin(ang) * r * 0.95);
			ThorDraw.bolt(vc, pose, ThorDraw.jagged(c.add(0, 0.6, 0), end, 7, 0.4, s + frame * 13.0 + i * 3.0), 0.6f,
					0.8f * alpha);
		}
		// small bolts out of the air onto the field
		double boltFrame = Math.floor(now / 4.0);
		double age = now - boltFrame * 4.0;
		if (age < 2.5) {
			for (int i = 0; i < 2; i++) {
				double ang = Math.PI * 2.0 * ThorDraw.hash(s + boltFrame * 3.3 + i * 5.0);
				double rr = r * Math.sqrt(ThorDraw.hash(s + boltFrame * 4.4 + i * 7.0));
				Vec3 hit = c.add(Math.cos(ang) * rr, 0.0, Math.sin(ang) * rr);
				Vec3 top = hit.add((ThorDraw.hash(s + boltFrame + i) - 0.5) * 1.5, 3.5 + ThorDraw.hash(s + boltFrame * 2.0 + i) * 1.5,
						(ThorDraw.hash(s + boltFrame * 1.7 + i) - 0.5) * 1.5);
				float fa = (float) (1.0 - age / 2.5) * alpha;
				ThorDraw.bolt(vc, pose, ThorDraw.jagged(top, hit, 8, 0.45, s + boltFrame * 19.0 + i * 23.0), 0.7f, fa);
				ThorDraw.flare(vc, poseStack, hit.add(0, 0.15, 0), 0.4f * fa + 0.05f, (float) (now * 30.0), fa);
			}
		}
	}

	// ---------------- v0.16.1: storm-charged ----------------

	/** Little arcs crackling over his body now and then while the storm charges him. */
	private static void stormCrackle(PoseStack poseStack, VertexConsumer vc, Vec3 feet, float height, double now, double seed) {
		double frame = Math.floor(now * 0.5);
		if (ThorDraw.hash(seed + frame * 0.77) > 0.75) {
			return;
		}
		PoseStack.Pose pose = poseStack.last();
		int n = 2 + (int) (ThorDraw.hash(seed + frame * 1.9) * 2.0);
		for (int i = 0; i < n; i++) {
			double a0 = Math.PI * 2.0 * ThorDraw.hash(seed + frame * 2.3 + i);
			double a1 = a0 + 0.6 + ThorDraw.hash(seed + frame * 3.7 + i) * 1.2;
			double y0 = height * (0.15 + 0.75 * ThorDraw.hash(seed + frame * 4.1 + i));
			double y1 = y0 + (ThorDraw.hash(seed + frame * 5.3 + i) - 0.5) * 0.8;
			Vec3 p0 = feet.add(Math.cos(a0) * 0.45, y0, Math.sin(a0) * 0.45);
			Vec3 p1 = feet.add(Math.cos(a1) * 0.5, y1, Math.sin(a1) * 0.5);
			ThorDraw.bolt(vc, pose, ThorDraw.jagged(p0, p1, 6, 0.2, seed + frame * 7.0 + i * 3.0), 0.45f, 0.95f);
		}
	}

	// ---------------- the suit-up ----------------

	private static void suitBolts(PoseStack poseStack, VertexConsumer vc, Vec3 feet, double t, double seed) {
		PoseStack.Pose pose = poseStack.last();
		EquipmentSlot[] later = { EquipmentSlot.LEGS, EquipmentSlot.CHEST };
		double[] height = { 0.75, 1.35 };
		for (int i = 0; i < later.length; i++) {
			double since = t - ThorArmor.arrivalTick(later[i]);
			if (since < 0 || since >= SUIT_BOLT_TICKS) {
				continue;
			}
			float fade = (float) (1.0 - since / SUIT_BOLT_TICKS);
			double frame = Math.floor(since * 1.5);
			Vec3 target = feet.add(0, height[i], 0);
			Vec3 sky = target.add((ThorDraw.hash(seed + i) - 0.5) * 4.0, 16.0, (ThorDraw.hash(seed + i * 3.0) - 0.5) * 4.0);
			ThorDraw.bolt(vc, pose, ThorDraw.jagged(sky, target, 12, 1.1, seed + i * 29.0 + frame * 5.0), 1.0f, fade);
			ThorDraw.flare(vc, poseStack, target, 0.45f * fade + 0.1f, (float) since * 40f, fade);
		}
	}
}
