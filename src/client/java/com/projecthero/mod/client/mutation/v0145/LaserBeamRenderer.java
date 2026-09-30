package com.projecthero.mod.client.mutation.v0145;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;
import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
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

/**
 * v0.14.5 Laser Vision: the eye beams as <b>geometry</b> instead of particle streams -- two camera-facing ribbons
 * from the eyes to whatever they hit, each a translucent red outer glow ({@code debugQuads}, alpha-blended so it
 * stays red over the sky) with a bright additive core ({@code lightning}) down the middle. Maximum Output adds a
 * pulsing white-hot centre and a wide halo.
 *
 * <p>Driven only by the synced {@link MutationVisualState} ({@code p02.*} animation + the {@code p02.max} flag), so
 * every player sees everyone's beams; each client ray-casts the far end itself along the shooter's interpolated
 * look, which keeps the beam glued to their aim frame by frame.
 *
 * <p>In first person the beams start a little below and ahead of the camera and fade / narrow toward it, so they
 * frame the crosshair instead of covering it: the target stays visible through the thin, translucent far end.
 */
public final class LaserBeamRenderer {
	private static final int RED = 0xFF2412;
	private static final int CORE = 0xFF5A36;
	private static final int WHITE = 0xFFE6D8;
	private static final int HALO = 0xFF1A0A;

	/** One ribbon to draw. Widths are half-widths; alphas at the near and far end. */
	private record Ribbon(Vec3 a, Vec3 b, float widthA, float widthB, int rgb, float alphaA, float alphaB, boolean glow) {
	}

	private LaserBeamRenderer() {
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(LaserBeamRenderer::render);
	}

	private enum Kind {
		BEAM(0.030f, 0.080f), PIERCE(0.045f, 0.12f), SWEEP(0.034f, 0.090f), RECOIL(0.060f, 0.16f),
		IGNITE(0.018f, 0.045f), MAX(0.075f, 0.20f);

		final float core;
		final float glow;

		Kind(float core, float glow) {
			this.core = core;
			this.glow = glow;
		}
	}

	private static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		PoseStack poseStack = context.matrixStack();
		MultiBufferSource consumers = context.consumers();
		if (level == null || poseStack == null || consumers == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long gameTime = level.getGameTime();
		List<Ribbon> ribbons = new ArrayList<>();
		for (Player player : level.players()) {
			MutationVisualState s = MutationVisuals.state(player);
			String anim = s.anim();
			if (anim.isEmpty() && !s.has("p02.max")) {
				continue;
			}
			float age = (gameTime - s.animStart()) + partial;
			int shot = LaserVisionHandlers.oneShotTicks(anim);
			boolean shotLive = shot > 0 && age >= 0 && age < shot;
			if (anim.equals(LaserVisionHandlers.ANIM_MAX) || (s.has("p02.max") && shotLive)) {
				beam(client, level, player, partial, Kind.MAX, look(player, partial), 1f, age, ribbons);
			} else if (anim.equals(LaserVisionHandlers.ANIM_BEAM)) {
				beam(client, level, player, partial, Kind.BEAM, look(player, partial), 1f, age, ribbons);
			}
			if (!shotLive) {
				continue;
			}
			float fade = 1f - age / shot;
			switch (anim) {
				case LaserVisionHandlers.ANIM_PIERCE ->
						beam(client, level, player, partial, Kind.PIERCE, look(player, partial), fade, age, ribbons);
				case LaserVisionHandlers.ANIM_RECOIL ->
						beam(client, level, player, partial, Kind.RECOIL, look(player, partial), fade, age, ribbons);
				case LaserVisionHandlers.ANIM_IGNITE ->
						beam(client, level, player, partial, Kind.IGNITE, look(player, partial), fade, age, ribbons);
				case LaserVisionHandlers.ANIM_SWEEP -> {
					if (age <= LaserVisionHandlers.SWEEP_TICKS) {
						float progress = age / (LaserVisionHandlers.SWEEP_TICKS - 1);
						Vec3 dir = LaserVisionHandlers.sweepDirection(player.getViewYRot(partial), player.getViewXRot(partial), progress);
						beam(client, level, player, partial, Kind.SWEEP, dir, 1f, age, ribbons);
					}
				}
				default -> {
				}
			}
		}
		if (ribbons.isEmpty()) {
			return;
		}
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose pose = poseStack.last();
		// every glow first, every core second: asking the buffer source for the second render type closes the first
		VertexConsumer glow = consumers.getBuffer(RenderType.debugQuads());
		for (Ribbon r : ribbons) {
			if (r.glow()) {
				BeamDraw.segment(glow, pose, r.a(), r.b(), cam, r.widthA(), r.widthB(), r.rgb(), r.alphaA(), r.alphaB());
			}
		}
		VertexConsumer core = consumers.getBuffer(RenderType.lightning());
		for (Ribbon r : ribbons) {
			if (!r.glow()) {
				BeamDraw.segment(core, pose, r.a(), r.b(), cam, r.widthA(), r.widthB(), r.rgb(), r.alphaA(), r.alphaB());
			}
		}
		poseStack.popPose();
	}

	private static Vec3 look(Player player, float partial) {
		return player.getViewVector(partial);
	}

	/** Adds the ribbons of one twin beam fired by {@code player} along {@code dir}. */
	private static void beam(Minecraft client, ClientLevel level, Player player, float partial, Kind kind, Vec3 dir, float fade,
			float age, List<Ribbon> out) {
		if (fade <= 0.01f) {
			return;
		}
		Vec3 eye = player.getEyePosition(partial);
		Vec3 end = hitPoint(level, player, eye, dir, kind != Kind.PIERCE);
		Vec3 right = dir.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(dir).normalize();
		boolean firstPerson = player == client.getCameraEntity() && client.options.getCameraType().isFirstPerson();
		boolean max = kind == Kind.MAX;
		float pulse = max ? 0.8f + 0.2f * Mth.sin(age * 0.9f) : 1f;

		// first person: from just below / ahead of the camera, spread a little so they frame the crosshair
		Vec3 base = firstPerson ? eye.add(dir.scale(0.45)).add(up.scale(-0.17)) : eye.add(dir.scale(0.28)).add(up.scale(0.02));
		double spread = firstPerson ? 0.075 : 0.065;
		// near the camera the first person beam is thin and see-through: it frames the view, never fills it
		double nearLen = firstPerson ? Math.min(3.0, base.distanceTo(end) * 0.5) : 0.0;
		float nearAlpha = firstPerson ? 0.12f : 1f;
		float nearWidth = firstPerson ? 0.3f : 1f;
		float alpha = fade * pulse;
		float fpGlow = firstPerson ? 0.7f : 1f;

		for (int side = -1; side <= 1; side += 2) {
			Vec3 a = base.add(right.scale(spread * side));
			addLayer(out, a, end, dir, nearLen, kind.glow * pulse, nearWidth, RED, 0.42f * alpha * fpGlow, nearAlpha, true);
			addLayer(out, a, end, dir, nearLen, kind.core * pulse, nearWidth, CORE, 0.95f * alpha, nearAlpha, false);
		}
		if (max) {
			// Maximum Output: a white-hot core and a wide pulsing halo down the middle of the pair
			addLayer(out, base, end, dir, nearLen, 0.42f * pulse, nearWidth * 0.6f, HALO, (firstPerson ? 0.10f : 0.22f) * alpha, nearAlpha,
					true);
			addLayer(out, base, end, dir, nearLen, 0.05f * pulse, nearWidth, WHITE, 0.9f * alpha, nearAlpha, false);
		}
		// a small flare where it lands
		float flare = (max ? 0.55f : kind == Kind.IGNITE ? 0.12f : 0.25f) * pulse;
		Vec3 camUp = new Vec3(0, 1, 0);
		out.add(new Ribbon(end.subtract(camUp.scale(flare)), end.add(camUp.scale(flare)), flare, flare, RED, 0.35f * alpha,
				0.35f * alpha, true));
		out.add(new Ribbon(end.subtract(camUp.scale(flare * 0.4)), end.add(camUp.scale(flare * 0.4)), flare * 0.4f, flare * 0.4f,
				WHITE, 0.8f * alpha, 0.8f * alpha, false));
	}

	/**
	 * One layer of a beam from {@code a} to {@code end}. With {@code nearLen > 0} the first stretch fades in from
	 * {@code nearAlpha} and widens from {@code nearWidth} of full width, so nothing thick sits in front of the eye.
	 */
	private static void addLayer(List<Ribbon> out, Vec3 a, Vec3 end, Vec3 dir, double nearLen, float width, float nearWidth, int rgb,
			float alpha, float nearAlpha, boolean glow) {
		if (nearLen > 0.05 && a.distanceTo(end) > nearLen + 0.05) {
			Vec3 toEnd = end.subtract(a).normalize();
			Vec3 mid = a.add(toEnd.scale(nearLen));
			out.add(new Ribbon(a, mid, width * nearWidth, width, rgb, alpha * nearAlpha, alpha, glow));
			out.add(new Ribbon(mid, end, width, width, rgb, alpha, alpha, glow));
		} else {
			out.add(new Ribbon(a, end, width, width, rgb, alpha, alpha, glow));
		}
	}

	/** Where a beam from {@code from} along {@code dir} lands: the first block, or (optionally) creature, within range. */
	private static Vec3 hitPoint(ClientLevel level, Player player, Vec3 from, Vec3 dir, boolean entities) {
		Vec3 to = from.add(dir.scale(LaserVisionHandlers.RANGE));
		BlockHitResult bhr = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? to : bhr.getLocation();
		if (entities) {
			AABB box = new AABB(from, end).inflate(1.0);
			EntityHitResult ehr = ProjectileUtil.getEntityHitResult(player, from, end, box,
					e -> e != player && e.isPickable() && e instanceof LivingEntity && !(e instanceof ArmorStand) && !e.isSpectator(),
					from.distanceToSqr(end));
			if (ehr != null) {
				end = ehr.getLocation();
			}
		}
		return end;
	}
}
