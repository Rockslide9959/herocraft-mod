package com.projecthero.mod.client.miraculous.pegasus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.pegasus.Pegasus;
import com.projecthero.mod.miraculous.pegasus.network.PegasusWaypointsPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: Pegasus's marks in the world, client side: the golden hoof prints a galloping Pegasus leaves (Shift+X, every
 * viewer sees them, they fade over 3 s), the glowing hoof-shaped platforms that appear under his feet while he runs on
 * the air (Shift+V, fading over 1.5 s), and -- only for their owner -- the small golden markers on his saved waypoints
 * (a spinning Horseshoe over a thin beam of light).
 */
public final class PegasusWorldFx {
	public static final ResourceLocation PRINT = ProjectHeroMod.id("textures/entity/miraculous/pegasus/hoof_print.png");
	public static final ResourceLocation PLATFORM = ProjectHeroMod.id("textures/entity/miraculous/pegasus/sky_hoof.png");

	private record Mark(double x, double y, double z, float yaw, long at, boolean sky) {
	}

	private static final int PRINT_TICKS = 60;
	private static final int PLATFORM_TICKS = 30;
	private static final Map<Integer, Deque<Mark>> MARKS = new ConcurrentHashMap<>();
	private static final Map<Integer, double[]> LAST = new ConcurrentHashMap<>();
	private static final Map<Integer, Boolean> SIDE = new ConcurrentHashMap<>();
	private static volatile List<PegasusWaypointsPayload.Entry> waypoints = List.of();

	private PegasusWorldFx() {
	}

	static void reset() {
		MARKS.clear();
		LAST.clear();
		SIDE.clear();
		waypoints = List.of();
	}

	static void receive(PegasusWaypointsPayload payload) {
		waypoints = List.copyOf(payload.waypoints());
	}

	/** Test / harness read: the waypoints this client knows. */
	public static List<PegasusWaypointsPayload.Entry> waypoints() {
		return waypoints;
	}

	static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		long now = mc.level.getGameTime();
		for (Player p : mc.level.players()) {
			boolean standing = standing(p);
			boolean gallop = Pegasus.galloping(p) && standing;
			boolean sky = Pegasus.skyGalloping(p) && !standing;
			double[] last = LAST.get(p.getId());
			if (!gallop && !sky) {
				LAST.remove(p.getId());
				continue;
			}
			double step = sky ? 0.9 : 0.85;
			if (last != null && Math.hypot(p.getX() - last[0], p.getZ() - last[2]) < step && Math.abs(p.getY() - last[1]) < 0.6) {
				continue;
			}
			LAST.put(p.getId(), new double[] { p.getX(), p.getY(), p.getZ() });
			float yaw = p.getYRot();
			Deque<Mark> q = MARKS.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
			if (sky) {
				q.addLast(new Mark(p.getX(), p.getY() - 0.03, p.getZ(), yaw, now, true));
			} else {
				boolean left = SIDE.merge(p.getId(), true, (a, b) -> !a);
				Vec3 side = Vec3.directionFromRotation(0f, yaw + 90f).scale(left ? 0.17 : -0.17);
				q.addLast(new Mark(p.getX() + side.x, p.getY() + 0.015, p.getZ() + side.z, yaw, now, false));
			}
			while (q.size() > 48) {
				q.pollFirst();
			}
		}
		for (Deque<Mark> q : MARKS.values()) {
			q.removeIf(m -> now - m.at() > (m.sky() ? PLATFORM_TICKS : PRINT_TICKS));
		}
		MARKS.values().removeIf(Deque::isEmpty);
	}

	/** On solid ground (from the world, not the flag: another player's onGround isn't always right on this client). */
	public static boolean standing(Player p) {
		if (p.onGround()) {
			return true;
		}
		net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(p.getX(), p.getY() - 0.08, p.getZ());
		return !p.level().getBlockState(below).getCollisionShape(p.level(), below).isEmpty();
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Vec3 cam = context.camera().getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		MultiBufferSource buffers = context.consumers();
		PoseStack pose = context.matrixStack();
		for (Deque<Mark> q : MARKS.values()) {
			for (Mark m : new ArrayList<>(q)) {
				float age = now - m.at() + partial;
				float life = m.sky() ? PLATFORM_TICKS : PRINT_TICKS;
				float alpha = Mth.clamp(1f - age / life, 0f, 1f);
				if (alpha <= 0f) {
					continue;
				}
				pose.pushPose();
				pose.translate(m.x() - cam.x, m.y() - cam.y, m.z() - cam.z);
				pose.mulPose(Axis.YP.rotationDegrees(-m.yaw()));
				float s = m.sky() ? 0.55f * (0.85f + 0.15f * Mth.clamp(age / 4f, 0f, 1f)) : 0.27f;
				flat(buffers, pose, m.sky() ? PLATFORM : PRINT, s, (int) (235 * alpha));
				pose.popPose();
			}
		}
		// the owner's waypoint markers
		Player me = mc.player;
		if (me != null && Miraculous.holds(me, Kwami.KAALKI) && !waypoints.isEmpty()) {
			String dim = mc.level.dimension().location().toString();
			float t = mc.level.getGameTime() + partial;
			for (PegasusWaypointsPayload.Entry w : waypoints) {
				if (!w.dim().equals(dim) || me.distanceToSqr(w.x(), w.y(), w.z()) > 256 * 256) {
					continue;
				}
				pose.pushPose();
				pose.translate(w.x() - cam.x, w.y() - cam.y, w.z() - cam.z);
				beam(buffers, pose, t);
				pose.translate(0, 1.45 + 0.12 * Mth.sin(t * 0.08f + w.slot()), 0);
				pose.mulPose(Axis.YP.rotationDegrees(t * 3f));
				pose.scale(1.1f, 1.1f, 1.1f);
				PegasusRender.renderShoeModel(pose, buffers, LightTexture.FULL_BRIGHT);
				pose.popPose();
			}
		}
	}

	/** A flat horizontal square of {@code tex}, {@code s} from the middle to a side, toe pointing the way he went. */
	private static void flat(MultiBufferSource buffers, PoseStack pose, ResourceLocation tex, float s, int alpha) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(tex));
		Matrix4f m = pose.last().pose();
		int l = LightTexture.FULL_BRIGHT;
		// texture top (v 0, the closed toe) forward (+Z after the yaw turn), the open heels behind
		v(vc, pose, m, -s, s, 0f, 0f, alpha, l);
		v(vc, pose, m, s, s, 1f, 0f, alpha, l);
		v(vc, pose, m, s, -s, 1f, 1f, alpha, l);
		v(vc, pose, m, -s, -s, 0f, 1f, alpha, l);
	}

	private static void v(VertexConsumer vc, PoseStack pose, Matrix4f m, float x, float z, float u, float vv, int alpha, int light) {
		vc.addVertex(m, x, 0f, z).setColor(255, 255, 255, alpha).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose.last(), 0f, 1f, 0f);
	}

	/** A thin beam of golden light, three blocks up from the waypoint (two crossed quads, turning). */
	private static void beam(MultiBufferSource buffers, PoseStack pose, float t) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(PegasusPortalRenderer.RING));
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(t * 1.5f));
		Matrix4f m = pose.last().pose();
		float w = 0.06f, h = 3.0f;
		int l = LightTexture.FULL_BRIGHT;
		for (int k = 0; k < 2; k++) {
			float cx = k == 0 ? w : 0f, cz = k == 0 ? 0f : w;
			vc.addVertex(m, -cx, 0f, -cz).setColor(255, 240, 200, 170).setUv(0f, 0.45f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose.last(), 0, 1, 0);
			vc.addVertex(m, cx, 0f, cz).setColor(255, 240, 200, 170).setUv(1f, 0.45f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose.last(), 0, 1, 0);
			vc.addVertex(m, cx, h, cz).setColor(255, 240, 200, 0).setUv(1f, 0.55f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose.last(), 0, 1, 0);
			vc.addVertex(m, -cx, h, -cz).setColor(255, 240, 200, 0).setUv(0f, 0.55f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose.last(), 0, 1, 0);
		}
		pose.popPose();
	}
}
