package com.projecthero.mod.client.moonknight;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.data.MoonKnightAction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Moon Knight's grappling line (G), drawn for every Moon Knight any client can see, straight from the synced
 * {@link MoonKnightAction} line fields: from the right hand to the fixed anchor (TAP) or to the target's body (HOLD dive
 * kick, SNEAK yank), with a slight sag, shooting out over {@link MoonKnightConfig#GRAPPLE_LINE_TRAVEL_TICKS} ticks
 * when it is fired. Purely a render (the same technique as {@code SpiderWebLineRenderer}): a few line segments into the
 * world buffer, nothing spawned, nothing to leak -- when the server clears {@code lineStart} it simply stops drawing.
 */
public final class MoonKnightLineRenderer {
	private static final int SEGMENTS = 12;
	/** A pale, slightly silvered cord. */
	private static final float R = 0.9f;
	private static final float G = 0.92f;
	private static final float B = 0.96f;

	private MoonKnightLineRenderer() {
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Camera camera = context.camera();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		for (Player player : mc.level.players()) {
			MoonKnightAction a = player.getAttachedOrElse(ModAttachments.MOON_KNIGHT_ACTION, null);
			if (a == null || a.lineStart < 0 || !MoonKnight.isTransformed(player) || now - a.lineStart > 200) {
				continue;
			}
			Vec3 anchor = new Vec3(a.lineX, a.lineY, a.lineZ);
			if (a.lineTargetId >= 0) {
				Entity target = mc.level.getEntity(a.lineTargetId);
				if (target != null) {
					anchor = target.getPosition(partial).add(0.0, target.getBbHeight() * 0.55, 0.0);
				}
			}
			Vec3 hand = handPosition(mc, player, anchor, partial, camera);
			float out = Mth.clamp((now - a.lineStart + partial) / (float) MoonKnightConfig.GRAPPLE_LINE_TRAVEL_TICKS, 0f, 1f);
			Vec3 end = hand.lerp(anchor, out);
			draw(context.matrixStack(), context.consumers().getBuffer(RenderType.lines()), camera, hand, end, out);
		}
	}

	/** The right hand: view-space in first person, otherwise the fist of an arm pointing at the anchor. */
	private static Vec3 handPosition(Minecraft mc, Player player, Vec3 anchor, float partial, Camera cam) {
		if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
			Vector3f look = cam.getLookVector();
			Vector3f up = cam.getUpVector();
			Vector3f left = cam.getLeftVector();
			return cam.getPosition()
					.add(look.x * 0.7, look.y * 0.7, look.z * 0.7)
					.add(-left.x * 0.42, -left.y * 0.42, -left.z * 0.42)
					.add(-up.x * 0.34, -up.y * 0.34, -up.z * 0.34);
		}
		Vec3 pos = player.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		Vec3 right = Vec3.directionFromRotation(0.0f, bodyYaw + 90.0f);
		Vec3 shoulder = pos.add(0.0, player.getBbHeight() * 0.77, 0.0).add(right.scale(0.36));
		Vec3 toAnchor = anchor.subtract(shoulder);
		return toAnchor.lengthSqr() < 1.0e-4 ? shoulder : shoulder.add(toAnchor.normalize().scale(0.65));
	}

	private static void draw(PoseStack poseStack, VertexConsumer buffer, Camera camera, Vec3 from, Vec3 to, float taut) {
		Vec3 cam = camera.getPosition();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose pose = poseStack.last();
		// the line sags while it is still flying out, and pulls nearly taut once it bites
		double slack = Math.min(0.8, from.distanceTo(to) * 0.03) * (1.3 - 0.9 * taut);
		Vec3 prev = null;
		for (int i = 0; i <= SEGMENTS; i++) {
			double t = (double) i / SEGMENTS;
			Vec3 p = from.lerp(to, t).subtract(0.0, slack * 4.0 * t * (1.0 - t), 0.0);
			if (prev != null) {
				Vec3 d = p.subtract(prev);
				double len = Math.max(1.0e-6, d.length());
				float nx = (float) (d.x / len);
				float ny = (float) (d.y / len);
				float nz = (float) (d.z / len);
				buffer.addVertex(pose.pose(), (float) prev.x, (float) prev.y, (float) prev.z)
						.setColor(R, G, B, 1.0f).setNormal(pose, nx, ny, nz);
				buffer.addVertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
						.setColor(R, G, B, 1.0f).setNormal(pose, nx, ny, nz);
			}
			prev = p;
		}
		poseStack.popPose();
	}
}
