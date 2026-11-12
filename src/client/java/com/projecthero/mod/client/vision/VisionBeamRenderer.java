package com.projecthero.mod.client.vision;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.beamclash.BeamClashClient;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.vision.Vision;
import com.projecthero.mod.vision.VisionConfig;

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
 * v0.17.3 Vision's beams, drawn the way Starfire's eye beams and the Kryptonian's Heat Vision are: camera-facing ribbons, a
 * translucent glow ({@code debugQuads}) round a bright additive core ({@code lightning}), from the Mind Stone on his brow to whatever
 * they hit. R's Mind Stone Beam is one gold-yellow ribbon; Shift+Z's Mind Stone Unleashed is a far wider one that swells in and a
 * blazing flare on the stone; Shift+R's Solar Lance charging is a growing flare on the stone. Driven by the synced state, so every
 * player sees everyone's; each client ray-casts the far end along the shooter's interpolated look. Hidden while the beam is locked in
 * a clash ({@link BeamClashClient#hides}).
 */
public final class VisionBeamRenderer {
	private static final int GOLD = 0xFFC830;
	private static final int CORE = 0xFFF0A0;
	private static final int WHITE = 0xFFFFF0;

	private record Ribbon(Vec3 a, Vec3 b, float widthA, float widthB, int rgb, float alphaA, float alphaB, boolean glow) {
	}

	private VisionBeamRenderer() {
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
		float time = level.getGameTime() + partial;
		List<Ribbon> ribbons = new ArrayList<>();
		for (Player player : level.players()) {
			if (!Vision.hasPower(player)) {
				continue;
			}
			boolean hidden = BeamClashClient.hides(player);
			if (Vision.unleashing(player) && !hidden) {
				int age = Vision.unleashAge(player);
				float swell = Mth.clamp((age + partial) / 6f, 0f, 1f);
				beam(client, level, player, partial, time, VisionConfig.UNLEASH_RANGE, 0.55f * swell, true, ribbons);
			} else if (Vision.beaming(player) && !hidden) {
				beam(client, level, player, partial, time, VisionConfig.BEAM_RANGE, 0.12f, false, ribbons);
			}
			if (Vision.chargingLance(player)) {
				var s = Vision.peek(player);
				float t = s == null ? 0f : Mth.clamp((level.getGameTime() - s.lanceStart + partial) / VisionConfig.LANCE_CHARGE_TICKS, 0f, 1f);
				flare(client, player, partial, 0.08f + 0.22f * t, ribbons);
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

	/** Where the beam leaves his brow (lower and closer in first person, so it doesn't fill the view). */
	private static Vec3 origin(Minecraft client, Player player, float partial) {
		Vec3 eye = player.getEyePosition(partial);
		Vec3 dir = player.getViewVector(partial);
		boolean firstPerson = player == client.getCameraEntity() && client.options.getCameraType().isFirstPerson();
		Vec3 right = dir.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(dir).normalize();
		return firstPerson ? eye.add(dir.scale(0.15)).add(up.scale(-0.06)) : eye.add(dir.scale(0.3)).add(up.scale(0.17));
	}

	private static void beam(Minecraft client, ClientLevel level, Player player, float partial, float time, double range, float width, boolean pierce,
			List<Ribbon> out) {
		Vec3 dir = player.getViewVector(partial);
		Vec3 a = origin(client, player, partial);
		Vec3 end = hitPoint(level, player, a, dir, range, pierce);
		boolean firstPerson = player == client.getCameraEntity() && client.options.getCameraType().isFirstPerson();
		float pulse = 0.85f + 0.15f * Mth.sin(time * 1.3f);
		float fp = firstPerson ? 0.6f : 1f;
		float w = width * pulse;
		out.add(new Ribbon(a, end, w * 0.7f, w, GOLD, 0.42f * fp, 0.48f * fp, true));
		out.add(new Ribbon(a, end, w * 0.3f, w * 0.38f, CORE, 0.95f * fp, 0.95f * fp, false));
		float flare = Math.max(0.3f, width * 2.4f) * pulse;
		Vec3 camUp = new Vec3(0, 1, 0);
		out.add(new Ribbon(end.subtract(camUp.scale(flare)), end.add(camUp.scale(flare)), flare, flare, GOLD, 0.4f, 0.4f, true));
		out.add(new Ribbon(end.subtract(camUp.scale(flare * 0.4)), end.add(camUp.scale(flare * 0.4)), flare * 0.4f, flare * 0.4f, WHITE, 0.85f,
				0.85f, false));
		if (!firstPerson) {
			flare(client, player, partial, Math.max(0.12f, width * 0.9f), out);
		}
	}

	/** A bright spot on the Mind Stone. */
	private static void flare(Minecraft client, Player player, float partial, float size, List<Ribbon> out) {
		boolean firstPerson = player == client.getCameraEntity() && client.options.getCameraType().isFirstPerson();
		if (firstPerson) {
			return;
		}
		Vec3 at = origin(client, player, partial);
		Vec3 camUp = new Vec3(0, 1, 0);
		out.add(new Ribbon(at.subtract(camUp.scale(size)), at.add(camUp.scale(size)), size, size, GOLD, 0.45f, 0.45f, true));
		out.add(new Ribbon(at.subtract(camUp.scale(size * 0.4)), at.add(camUp.scale(size * 0.4)), size * 0.4f, size * 0.4f, WHITE, 0.9f, 0.9f,
				false));
	}

	private static Vec3 hitPoint(ClientLevel level, Player player, Vec3 from, Vec3 dir, double range, boolean pierce) {
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult bhr = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? to : bhr.getLocation();
		if (pierce) {
			return end; // Unleashed goes through everything
		}
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(player, from, end, new AABB(from, end).inflate(1.0),
				e -> e != player && e.isPickable() && e instanceof LivingEntity && !(e instanceof ArmorStand) && !e.isSpectator(),
				from.distanceToSqr(end));
		return ehr != null ? ehr.getLocation() : end;
	}
}
