package com.projecthero.mod.client.starfire;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.starfire.Starfire;
import com.projecthero.mod.starfire.StarfireConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

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
 * v0.17.3 Starfire, Shift+G Eye Beams: two green camera-facing ribbons from her eyes to whatever they hit, drawn the way the
 * Kryptonian's Heat Vision is ({@code KryptonianBeamRenderer}): a translucent glow ({@code debugQuads}) round a bright additive
 * core ({@code lightning}). Driven by the synced {@code beams} flag, so every player sees everyone's beams; each client ray-casts
 * the far end along the shooter's interpolated look. Hidden while the beam is locked in a clash ({@code BeamClashClient.hides}).
 */
public final class StarfireBeamRenderer {
	private static final int GREEN = 0x3CFF3A;
	private static final int CORE = 0xB8FFA8;
	private static final int WHITE = 0xF0FFE8;

	private record Ribbon(Vec3 a, Vec3 b, float widthA, float widthB, int rgb, float alphaA, float alphaB, boolean glow) {
	}

	private StarfireBeamRenderer() {
	}

	static void render(WorldRenderContext context) {
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
			if (Starfire.beaming(player) && !com.projecthero.mod.client.beamclash.BeamClashClient.hides(player)) {
				beam(client, level, player, partial, level.getGameTime() + partial, ribbons);
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
		Vec3 right = dir.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(dir).normalize();
		float pulse = 0.85f + 0.15f * Mth.sin(time * 1.5f);
		Vec3 base = firstPerson ? eye.add(dir.scale(0.12)).add(up.scale(-0.045)) : eye.add(dir.scale(0.28)).add(up.scale(0.02));
		double spread = firstPerson ? 0.06 : 0.065;
		float fpGlow = firstPerson ? 0.6f : 1f;
		float fpCore = firstPerson ? 0.55f : 1f;
		for (int side = -1; side <= 1; side += 2) {
			Vec3 a = base.add(right.scale(spread * side));
			out.add(new Ribbon(a, end, 0.09f * pulse, 0.11f * pulse, GREEN, 0.4f * pulse * fpGlow, 0.45f * pulse * fpGlow, true));
			out.add(new Ribbon(a, end, 0.034f * pulse, 0.04f * pulse, CORE, 0.95f * pulse * fpCore, 0.95f * pulse * fpCore, false));
		}
		float flare = 0.32f * pulse;
		Vec3 camUp = new Vec3(0, 1, 0);
		out.add(new Ribbon(end.subtract(camUp.scale(flare)), end.add(camUp.scale(flare)), flare, flare, GREEN, 0.4f, 0.4f, true));
		out.add(new Ribbon(end.subtract(camUp.scale(flare * 0.4)), end.add(camUp.scale(flare * 0.4)), flare * 0.4f, flare * 0.4f, WHITE, 0.85f,
				0.85f, false));
	}

	private static Vec3 hitPoint(ClientLevel level, Player player, Vec3 from, Vec3 dir) {
		Vec3 to = from.add(dir.scale(StarfireConfig.BEAM_RANGE));
		BlockHitResult bhr = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? to : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(player, from, end, new AABB(from, end).inflate(1.0),
				e -> e != player && e.isPickable() && e instanceof LivingEntity && !(e instanceof ArmorStand) && !e.isSpectator(),
				from.distanceToSqr(end));
		return ehr != null ? ehr.getLocation() : end;
	}
}
