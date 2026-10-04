package com.projecthero.mod.client.kryptonian;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianConfig;

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
 * v0.14.8: Heat Vision, drawn the way the v0.14.5 Laser Vision rework draws its beams ({@code LaserBeamRenderer}): two
 * camera-facing ribbons from the eyes to whatever they hit, a translucent red glow ({@code debugQuads}) around a bright
 * additive core ({@code lightning}). Driven only by the synced {@code heatVision} flag, so every player sees everyone's
 * beams; each client ray-casts the far end itself along the shooter's interpolated look.
 *
 * <p>The beam is the same in every camera: in first person it starts right at the camera, one ribbon per eye, and comes
 * in from the lower screen edge to the crosshair -- see-through (lower alpha near the eye), so the target stays visible;
 * in third person (either way round) it leaves the model's eyes. Nothing here gates on the camera type except that
 * placement: the hold itself lives on the server.
 */
public final class KryptonianBeamRenderer {
	private static final int RED = 0xFF2A10;
	private static final int CORE = 0xFF7A3A;
	private static final int WHITE = 0xFFE8C8;

	private record Ribbon(Vec3 a, Vec3 b, float widthA, float widthB, int rgb, float alphaA, float alphaB, boolean glow) {
	}

	/**
	 * Beams the server sent explicitly ({@code LaserBeamPayload.KIND_HEAT_VISION}, received by {@code LaserBeamRenderer}):
	 * Kryptonians this client does not track -- their heatVision flag never arrives here.
	 */
	private record SentBeam(Vec3 start, Vec3 end, long startTick, int ticks) {
	}

	private static final List<SentBeam> SENT = new ArrayList<>();
	private static final int MAX_SENT = 64;

	private KryptonianBeamRenderer() {
	}

	/** A held beam's per-tick refresh: replaces that beam's previous refresh (same eyes) instead of stacking. */
	public static void addSent(Vec3 start, Vec3 end, long now, int ticks) {
		SENT.removeIf(b -> b.start().distanceToSqr(start) < 2.25);
		if (SENT.size() >= MAX_SENT) {
			SENT.remove(0);
		}
		SENT.add(new SentBeam(start, end, now, ticks));
	}

	public static void clearSent() {
		SENT.clear();
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(KryptonianBeamRenderer::render);
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
		List<Ribbon> ribbons = new ArrayList<>();
		for (Player player : level.players()) {
			if (Kryptonian.heatVisionActive(player)) {
				beam(client, level, player, partial, level.getGameTime() + partial, ribbons);
			}
		}
		long gameTime = level.getGameTime();
		SENT.removeIf(b -> gameTime - b.startTick() >= b.ticks() || gameTime < b.startTick() - 40);
		for (SentBeam b : SENT) {
			Vec3 dir = b.end().subtract(b.start());
			if (dir.lengthSqr() > 1.0e-6) {
				draw(b.start(), b.end(), dir.normalize(), gameTime + partial, false, ribbons);
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

	private static void beam(Minecraft client, ClientLevel level, Player player, float partial, float time, List<Ribbon> out) {
		Vec3 eye = player.getEyePosition(partial);
		Vec3 dir = player.getViewVector(partial);
		Vec3 end = hitPoint(level, player, eye, dir);
		boolean firstPerson = player == client.getCameraEntity() && client.options.getCameraType().isFirstPerson();
		draw(eye, end, dir, time, firstPerson, out);
	}

	/** The twin ribbons from the eyes at {@code eye} to {@code end} (third-person placement unless {@code firstPerson}). */
	private static void draw(Vec3 eye, Vec3 end, Vec3 dir, float time, boolean firstPerson, List<Ribbon> out) {
		Vec3 right = dir.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(dir).normalize();
		float pulse = 0.85f + 0.15f * Mth.sin(time * 1.3f);
		// first person: straight out of your eyes, from the lower edges of the screen to the crosshair; see-through
		Vec3 base = firstPerson ? eye.add(dir.scale(0.12)).add(up.scale(-0.045)) : eye.add(dir.scale(0.28)).add(up.scale(0.02));
		double spread = firstPerson ? 0.06 : 0.065;
		double nearLen = firstPerson ? Math.min(4.0, base.distanceTo(end) * 0.5) : 0.0;
		float nearAlpha = firstPerson ? 0.5f : 1f;
		float nearWidth = firstPerson ? 0.8f : 1f;
		float fpGlow = firstPerson ? 0.7f : 1f;
		float fpCore = firstPerson ? 0.6f : 1f;
		for (int side = -1; side <= 1; side += 2) {
			Vec3 a = base.add(right.scale(spread * side));
			addLayer(out, a, end, nearLen, 0.085f * pulse, nearWidth, RED, 0.42f * pulse * fpGlow, nearAlpha, true);
			addLayer(out, a, end, nearLen, 0.032f * pulse, nearWidth, CORE, 0.95f * pulse * fpCore, nearAlpha, false);
		}
		// the glowing spot where it burns
		float flare = 0.3f * pulse;
		Vec3 camUp = new Vec3(0, 1, 0);
		out.add(new Ribbon(end.subtract(camUp.scale(flare)), end.add(camUp.scale(flare)), flare, flare, RED, 0.35f, 0.35f, true));
		out.add(new Ribbon(end.subtract(camUp.scale(flare * 0.4)), end.add(camUp.scale(flare * 0.4)), flare * 0.4f, flare * 0.4f, WHITE,
				0.8f, 0.8f, false));
	}

	private static void addLayer(List<Ribbon> out, Vec3 a, Vec3 end, double nearLen, float width, float nearWidth, int rgb, float alpha,
			float nearAlpha, boolean glow) {
		if (nearLen > 0.05 && a.distanceTo(end) > nearLen + 0.05) {
			Vec3 mid = a.add(end.subtract(a).normalize().scale(nearLen));
			out.add(new Ribbon(a, mid, width * nearWidth, width, rgb, alpha * nearAlpha, alpha, glow));
			out.add(new Ribbon(mid, end, width, width, rgb, alpha, alpha, glow));
		} else {
			out.add(new Ribbon(a, end, width, width, rgb, alpha, alpha, glow));
		}
	}

	/** Where the beam lands: the first block or creature within range (the same ray the server burns along). */
	private static Vec3 hitPoint(ClientLevel level, Player player, Vec3 from, Vec3 dir) {
		Vec3 to = from.add(dir.scale(KryptonianConfig.HEAT_RANGE));
		BlockHitResult bhr = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? to : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(player, from, end, new AABB(from, end).inflate(1.0),
				e -> e != player && e.isPickable() && e instanceof LivingEntity && !(e instanceof ArmorStand) && !e.isSpectator(),
				from.distanceToSqr(end));
		return ehr != null ? ehr.getLocation() : end;
	}
}
