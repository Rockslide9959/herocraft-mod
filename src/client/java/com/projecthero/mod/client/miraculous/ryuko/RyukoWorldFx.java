package com.projecthero.mod.client.miraculous.ryuko;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ryuko.Ryuko;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.17.3: what Ryuko leaves in the world: the thrown sword spinning end over end, and Dragon Form -- she is gone, and a long
 * serpent of her element follows where she flies: a coloured body tapering behind a bright head.
 */
public final class RyukoWorldFx {
	private static final int TRAIL = 26;
	private static final Map<Integer, Deque<Vec3>> TRAILS = new ConcurrentHashMap<>();
	private static long lastTick;

	private RyukoWorldFx() {
	}

	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		boolean newTick = now != lastTick;
		lastTick = now;
		PoseStack pose = ctx.matrixStack();
		Vec3 cam = ctx.camera().getPosition();
		for (Player p : mc.level.players()) {
			if (!Ryuko.is(p)) {
				TRAILS.remove(p.getId());
				continue;
			}
			MiraculousState s = Miraculous.peek(p);
			if (s == null) {
				continue;
			}
			if (Ryuko.swordAway(p)) {
				Vec3 at = Miraculous.vec(s, Ryuko.X_SWORD);
				if (at != null) {
					pose.pushPose();
					pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
					pose.mulPose(Axis.YP.rotationDegrees((now + partial) * 40f));
					pose.mulPose(Axis.XP.rotationDegrees((now + partial) * 55f));
					pose.scale(1.2f, 1.2f, 1.2f);
					int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
					RyukoRender.renderSword(pose, ctx.consumers(), light, Ryuko.color(Ryuko.element(p)));
					pose.popPose();
				}
			}
			if (Ryuko.inForm(p)) {
				Deque<Vec3> trail = TRAILS.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
				if (newTick) {
					trail.addFirst(p.position().add(0, 0.9, 0));
					while (trail.size() > TRAIL) {
						trail.removeLast();
					}
				}
				serpent(ctx, cam, p, trail, Ryuko.color(Ryuko.element(p)), partial, now + partial);
			} else {
				TRAILS.remove(p.getId());
			}
		}
	}

	private static void serpent(WorldRenderContext ctx, Vec3 cam, Player p, Deque<Vec3> trail, int rgb, float partial, float age) {
		PoseStack pose = ctx.matrixStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = pose.last().pose();
		VertexConsumer vc = ctx.consumers().getBuffer(RenderType.debugQuads());
		Vec3 prev = p.getPosition(partial).add(0, 0.9, 0);
		int i = 0;
		for (Vec3 q : trail) {
			float k = 1f - i / (float) TRAIL;
			Vec3 bob = q.add(0, 0.28 * Mth.sin(age * 0.5f + i * 0.6f) * k, 0);
			ribbon(vc, m, cam, prev, bob, 0.15f + 1.0f * k, rgb, 0.8f * k);
			prev = bob;
			i++;
		}
		pose.popPose();
	}

	static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0e-5) {
			return;
		}
		Vec3 side = dir.cross(cam.subtract(a));
		if (side.lengthSqr() < 1.0e-8) {
			return;
		}
		side = side.normalize().scale(width * 0.5);
		quad(vc, m, a.subtract(side), a.add(side), b.add(side), b.subtract(side), rgb, alpha);
	}

	/** Both windings. */
	static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha) {
		v(vc, m, a, rgb, alpha);
		v(vc, m, b, rgb, alpha);
		v(vc, m, c, rgb, alpha);
		v(vc, m, d, rgb, alpha);
		v(vc, m, d, rgb, alpha);
		v(vc, m, c, rgb, alpha);
		v(vc, m, b, rgb, alpha);
		v(vc, m, a, rgb, alpha);
	}

	private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, int rgb, float alpha) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha);
	}
}
