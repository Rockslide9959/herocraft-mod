package com.projecthero.mod.client.ghostrider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ghostrider.GhostRiderConfig;
import com.projecthero.mod.ghostrider.data.GhostRiderState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.16.5: Ghost Rider's burning chain, drawn for every Ghost Rider any client can see, straight from the synced
 * {@link GhostRiderState} chain fields: the Chain Whip lashes out from the main hand to its tip and snaps back; the Chain
 * Spin whirls it round him at arm's length. A camera-facing ribbon of the vanilla chain texture, full-bright and tinted
 * hot; {@link #tickParticles} sheds flames off it.
 */
public final class GhostRiderChainRenderer {
	private static final ResourceLocation CHAIN = ResourceLocation.withDefaultNamespace("block/chain");
	private static final float HALF_WIDTH = 0.09f;
	/** Blocks of chain per texture repeat. */
	private static final float TILE = 0.6f;

	private GhostRiderChainRenderer() {
	}

	/** The chain's two ends this frame, or null when it is not out. [0] = hand, [1] = tip. */
	static Vec3[] ends(Minecraft mc, Player player, float partial, boolean firstPerson, Camera cam) {
		GhostRiderState s = player.getAttachedOrElse(ModAttachments.GHOST_RIDER_STATE, null);
		if (s == null || !s.transformed || mc.level == null) {
			return null;
		}
		long now = mc.level.getGameTime();
		if (s.chainUntil <= now - 1) {
			return null;
		}
		Vec3 pos = player.getPosition(partial);
		if (s.spinUntil > now) {
			float a = (now + partial) * 0.9f;
			Vec3 hand = pos.add(0, player.getBbHeight() + 0.35, 0);
			double r = GhostRiderConfig.SPIN_RADIUS * 0.85;
			Vec3 tip = pos.add(Math.cos(a) * r, 1.2 + 0.25 * Math.sin(a * 2), Math.sin(a) * r);
			return new Vec3[] { hand, tip };
		}
		if (s.chainTarget.size() < 3) {
			return null;
		}
		Vec3 target = new Vec3(s.chainTarget.get(0), s.chainTarget.get(1), s.chainTarget.get(2));
		float age = (now - (s.chainUntil - 8)) + partial;
		float out = age < 3f ? age / 3f : Math.max(0f, 1f - (age - 5f) / 3f);
		Vec3 hand = hand(mc, player, partial, firstPerson, cam);
		return new Vec3[] { hand, hand.lerp(target, Mth.clamp(out, 0f, 1f)) };
	}

	private static Vec3 hand(Minecraft mc, Player player, float partial, boolean firstPerson, Camera cam) {
		if (firstPerson && cam != null) {
			Vector3f look = cam.getLookVector();
			Vector3f up = cam.getUpVector();
			Vector3f left = cam.getLeftVector();
			return cam.getPosition().add(look.x * 0.7, look.y * 0.7, look.z * 0.7).add(-left.x * 0.42, -left.y * 0.42, -left.z * 0.42)
					.add(-up.x * 0.34, -up.y * 0.34, -up.z * 0.34);
		}
		Vec3 pos = player.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		Vec3 right = Vec3.directionFromRotation(0.0f, bodyYaw + 90.0f);
		Vec3 look = Vec3.directionFromRotation(player.getViewXRot(partial), player.getViewYRot(partial));
		return pos.add(0.0, player.getBbHeight() * 0.75, 0.0).add(right.scale(0.36)).add(look.scale(0.5));
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Camera camera = context.camera();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		TextureAtlasSprite sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(CHAIN);
		VertexConsumer vc = null;
		for (Player player : mc.level.players()) {
			boolean fp = player == mc.player && mc.options.getCameraType().isFirstPerson();
			Vec3[] e = ends(mc, player, partial, fp, camera);
			if (e == null) {
				continue;
			}
			if (vc == null) {
				vc = context.consumers().getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
			}
			draw(context.matrixStack(), vc, camera, sprite, e[0], e[1]);
		}
	}

	private static void draw(PoseStack poseStack, VertexConsumer vc, Camera camera, TextureAtlasSprite sp, Vec3 from, Vec3 to) {
		Vec3 cam = camera.getPosition();
		double len = from.distanceTo(to);
		if (len < 0.05) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose pose = poseStack.last();
		Matrix4f m = pose.pose();
		Vec3 dir = to.subtract(from).normalize();
		// the vanilla chain texture: the link strip is the left 3 of its 16 columns
		float u0 = sp.getU0();
		float u1 = sp.getU(3f / 16f);
		int pieces = Math.max(1, (int) Math.ceil(len / TILE));
		for (int i = 0; i < pieces; i++) {
			double t0 = (double) i / pieces;
			double t1 = (double) (i + 1) / pieces;
			Vec3 a = from.add(dir.scale(len * t0));
			Vec3 b = from.add(dir.scale(len * t1));
			Vec3 mid = a.add(b).scale(0.5);
			Vec3 side = dir.cross(cam.subtract(mid));
			side = side.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : side.normalize().scale(HALF_WIDTH);
			float frac = (float) ((len * (t1 - t0)) / TILE);
			float v0 = sp.getV0();
			float v1 = sp.getV(Math.min(1f, frac));
			vertex(vc, m, pose, a.subtract(side), u0, v0);
			vertex(vc, m, pose, a.add(side), u1, v0);
			vertex(vc, m, pose, b.add(side), u1, v1);
			vertex(vc, m, pose, b.subtract(side), u0, v1);
		}
		poseStack.popPose();
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose, Vec3 p, float u, float v) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(255, 196, 140, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 1f, 0f);
	}

	/** Client tick: flames shed along every visible chain. */
	static void tickParticles(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		Camera cam = mc.gameRenderer.getMainCamera();
		for (Player player : mc.level.players()) {
			boolean fp = player == mc.player && mc.options.getCameraType().isFirstPerson();
			Vec3[] e = ends(mc, player, 0f, fp, cam);
			if (e == null) {
				continue;
			}
			for (int i = 0; i < 3; i++) {
				Vec3 p = e[0].lerp(e[1], 0.25 + player.getRandom().nextDouble() * 0.75);
				mc.level.addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0.02, 0);
			}
		}
	}
}
