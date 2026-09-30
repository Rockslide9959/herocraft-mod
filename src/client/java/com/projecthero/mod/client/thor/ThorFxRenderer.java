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
				default -> {
				}
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
