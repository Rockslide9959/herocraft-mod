package com.projecthero.mod.client.vision;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.beamclash.BeamClashClient;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.client.eyebeam.EyeBeamFx;
import com.projecthero.mod.vision.Vision;
import com.projecthero.mod.vision.VisionConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Vision's beams from the Mind Stone on his brow, drawn since v0.17.5 by the shared eye-beam look ({@link EyeBeamFx#MIND},
 * one beam from the brow: {@link EyeBeamFx#brow}). R's Mind Stone Beam is a gold-yellow beam; Shift+Z's Mind Stone Unleashed a far
 * wider one that swells in and goes through creatures; Shift+R's Solar Lance charging is a growing flare on the stone (drawn here).
 * Driven by the synced state, so every player sees everyone's; each client ray-casts the far end along the shooter's interpolated
 * look. Hidden while the beam is locked in a clash ({@link BeamClashClient#hides}).
 */
public final class VisionBeamRenderer {
	private static final int GOLD = 0xFFC830;
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
		List<Ribbon> ribbons = new ArrayList<>();
		for (Player player : level.players()) {
			if (!Vision.hasPower(player)) {
				continue;
			}
			boolean hidden = BeamClashClient.hides(player);
			Vec3 eye = player.getEyePosition(partial);
			Vec3 dir = player.getViewVector(partial);
			if (Vision.unleashing(player) && !hidden) {
				int age = Vision.unleashAge(player);
				float swell = Mth.clamp((age + partial) / 6f, 0f, 1f);
				EyeBeamFx.brow(player, 1, EyeBeamFx.MIND, dir, EyeBeamFx.ray(level, player, eye, dir, VisionConfig.UNLEASH_RANGE, false),
						1f + 3.2f * swell, 1f);
			} else if (Vision.beaming(player) && !hidden) {
				EyeBeamFx.brow(player, 0, EyeBeamFx.MIND, dir, EyeBeamFx.ray(level, player, eye, dir, VisionConfig.BEAM_RANGE, true), 1.15f, 1f);
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

	/** The Mind Stone on his brow. */
	private static Vec3 origin(Player player, float partial) {
		Vec3 eye = player.getEyePosition(partial);
		Vec3 dir = player.getViewVector(partial);
		Vec3 right = dir.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(dir).normalize();
		return eye.add(dir.scale(0.3)).add(up.scale(0.17));
	}

	/** A bright spot on the Mind Stone (the Solar Lance charging). */
	private static void flare(Minecraft client, Player player, float partial, float size, List<Ribbon> out) {
		boolean firstPerson = player == client.getCameraEntity() && client.options.getCameraType().isFirstPerson();
		if (firstPerson) {
			return;
		}
		Vec3 at = origin(player, partial);
		Vec3 camUp = new Vec3(0, 1, 0);
		out.add(new Ribbon(at.subtract(camUp.scale(size)), at.add(camUp.scale(size)), size, size, GOLD, 0.45f, 0.45f, true));
		out.add(new Ribbon(at.subtract(camUp.scale(size * 0.4)), at.add(camUp.scale(size * 0.4)), size * 0.4f, size * 0.4f, WHITE, 0.9f, 0.9f,
				false));
	}
}
