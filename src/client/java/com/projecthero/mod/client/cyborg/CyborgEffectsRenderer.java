package com.projecthero.mod.client.cyborg;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.cyborg.CyborgConfig;
import com.projecthero.mod.cyborg.data.CyborgState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.16.5: Cyborg's world effects for every viewer, from the synced state: the Sonic Cannon's beam (a white core in a
 * blue glow, fading over its 6 ticks) and the Defense Matrix (a pulsing blue octagon a block in front of him that dims as
 * its 30 points are used up). Additive {@code lightning} quads, nothing spawned.
 */
public final class CyborgEffectsRenderer {
	private CyborgEffectsRenderer() {
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Camera camera = context.camera();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		VertexConsumer vc = null;
		PoseStack pose = context.matrixStack();
		Vec3 cam = camera.getPosition();
		for (Player player : mc.level.players()) {
			CyborgState s = player.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
			if (s == null || !s.hasPower || !s.combat) {
				continue;
			}
			boolean beam = s.beamUntil > now - 1 && s.beamEnd.size() == 3;
			boolean matrix = s.shieldUntil > now && s.shieldHp > 0f;
			if (!beam && !matrix) {
				continue;
			}
			if (vc == null) {
				vc = context.consumers().getBuffer(RenderType.lightning());
			}
			pose.pushPose();
			pose.translate(-cam.x, -cam.y, -cam.z);
			Matrix4f m = pose.last().pose();
			boolean fp = player == mc.player && mc.options.getCameraType().isFirstPerson();
			if (beam) {
				float life = Mth.clamp((s.beamUntil - now - partial) / 6f, 0f, 1f);
				Vec3 from = hand(mc, player, partial, fp, camera);
				Vec3 to = new Vec3(s.beamEnd.get(0), s.beamEnd.get(1), s.beamEnd.get(2));
				ribbon(vc, m, cam, from, to, 0.32f * life, 0.30f, 0.65f, 1.0f, 0.55f * life);
				ribbon(vc, m, cam, from, to, 0.12f * life, 0.85f, 0.97f, 1.0f, 0.9f * life);
			}
			if (matrix) {
				float hp = s.shieldHp / CyborgConfig.MATRIX_HP;
				float pulse = 0.75f + 0.25f * Mth.sin((now + partial) * 0.5f);
				Vec3 look = Vec3.directionFromRotation(player.getViewXRot(partial), player.getViewYRot(partial));
				Vec3 c = player.getEyePosition(partial).add(look.scale(1.0)).add(0, -0.35, 0);
				octagon(vc, m, c, look, 0.95f, 0.3f, 0.7f, 1.0f, 0.22f * pulse * (0.4f + 0.6f * hp));
				octagon(vc, m, c, look, 1.0f, 0.5f, 0.85f, 1.0f, 0.14f * pulse);
			}
			pose.popPose();
		}
	}

	private static Vec3 hand(Minecraft mc, Player player, float partial, boolean fp, Camera cam) {
		if (fp) {
			Vector3f look = cam.getLookVector();
			Vector3f up = cam.getUpVector();
			Vector3f left = cam.getLeftVector();
			return cam.getPosition().add(look.x * 0.8, look.y * 0.8, look.z * 0.8).add(-left.x * 0.38, -left.y * 0.38, -left.z * 0.38)
					.add(-up.x * 0.3, -up.y * 0.3, -up.z * 0.3);
		}
		Vec3 pos = player.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		Vec3 right = Vec3.directionFromRotation(0.0f, bodyYaw + 90.0f);
		Vec3 look = Vec3.directionFromRotation(player.getViewXRot(partial), player.getViewYRot(partial));
		return pos.add(0.0, player.getBbHeight() * 0.76, 0.0).add(right.scale(0.36)).add(look.scale(0.75));
	}

	/** A camera-facing strip from {@code a} to {@code b}. */
	private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0e-4 || alpha <= 0.01f) {
			return;
		}
		Vec3 mid = a.add(b).scale(0.5);
		Vec3 side = dir.cross(cam.subtract(mid));
		if (side.lengthSqr() < 1.0e-6) {
			return;
		}
		side = side.normalize().scale(width * 0.5);
		vertex(vc, m, a.subtract(side), r, g, bl, alpha);
		vertex(vc, m, a.add(side), r, g, bl, alpha);
		vertex(vc, m, b.add(side), r, g, bl, alpha);
		vertex(vc, m, b.subtract(side), r, g, bl, alpha);
	}

	/** A flat octagon of radius {@code radius} centred on {@code c}, facing {@code normal}. */
	private static void octagon(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 normal, float radius, float r, float g, float b, float alpha) {
		Vec3 n = normal.normalize();
		Vec3 u = n.cross(new Vec3(0, 1, 0));
		if (u.lengthSqr() < 1.0e-4) {
			u = new Vec3(1, 0, 0);
		}
		u = u.normalize();
		Vec3 v = u.cross(n).normalize();
		for (int i = 0; i < 8; i++) {
			double a0 = Math.PI * 2 * i / 8 + Math.PI / 8;
			double a1 = Math.PI * 2 * (i + 1) / 8 + Math.PI / 8;
			Vec3 p0 = c.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius));
			Vec3 p1 = c.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius));
			vertex(vc, m, c, r, g, b, alpha * 0.6f);
			vertex(vc, m, p0, r, g, b, alpha);
			vertex(vc, m, p1, r, g, b, alpha);
			vertex(vc, m, c, r, g, b, alpha * 0.6f);
		}
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
	}
}
