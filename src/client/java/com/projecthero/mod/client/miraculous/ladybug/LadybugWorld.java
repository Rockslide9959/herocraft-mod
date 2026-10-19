package com.projecthero.mod.client.miraculous.ladybug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ladybug.LadybugConfig;
import com.projecthero.mod.miraculous.ladybug.LadybugKeys;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.6: everything of Ladybug's drawn in the world, for every Ladybug any client can see, straight from her synced
 * state ({@link LadybugKeys}): the Yo-yo in flight and its string (whipped out and back, bouncing through a ricochet,
 * wound round a bound creature, hooked for a swing / zip, planted for a vault, strung as a trip wire, thrown up for the
 * Lucky Charm), the Spinning Shield, the open compact, the parachute canopy and the red light bursts. Taut lines are
 * straight; loose ones sag and sway.
 *
 * <p>Her right hand in third person comes from {@link LadybugLayer} (it records where the posed hand was drawn this
 * frame); in first person (the local player) a point at the bottom right of the view.
 */
public final class LadybugWorld {
	/** player id -> [world x, y, z, frame] of the posed right hand, written by the layer. */
	private static final Map<Integer, double[]> HAND = new ConcurrentHashMap<>();
	private static long frame;

	private LadybugWorld() {
	}

	static void recordHand(Player p, Vec3 world) {
		HAND.put(p.getId(), new double[] { world.x, world.y, world.z, frame });
	}

	static void reset() {
		HAND.clear();
	}

	/** Anim lengths (shared with the poses). */
	static int length(int animId) {
		return switch (animId) {
			case LadybugKeys.ANIM_STRIKE -> LadybugConfig.STRIKE_OUT_TICKS + 1 + LadybugConfig.STRIKE_BACK_TICKS + 2;
			case LadybugKeys.ANIM_RICOCHET -> LadybugConfig.RICOCHET_HOP_TICKS * (LadybugConfig.RICOCHET_TARGETS + 1) + 4;
			case LadybugKeys.ANIM_BIND -> 12;
			case LadybugKeys.ANIM_YANK -> 10;
			case LadybugKeys.ANIM_FLING -> 12;
			case LadybugKeys.ANIM_ZIP, LadybugKeys.ANIM_SWING -> 8;
			case LadybugKeys.ANIM_CHARM -> 30;
			case LadybugKeys.ANIM_MIRACULOUS -> 40;
			case LadybugKeys.ANIM_VAULT -> 16;
			case LadybugKeys.ANIM_WIRE -> 10;
			case LadybugKeys.ANIM_SCAN -> LadybugConfig.COMPACT_TICKS;
			default -> 0;
		};
	}

	/** Ticks into her running Ladybug anim, or -1. */
	static float animAge(MiraculousState s, long now, float partial) {
		if (s.animId < 20 || s.animId > 39) {
			return -1f;
		}
		float age = (now - s.animStart) + partial;
		return age >= 0f && age < length(s.animId) ? age : -1f;
	}

	/** Whether the Yo-yo is off her belt (any move running). */
	public static boolean yoyoOut(Player p) {
		MiraculousState s = LadybugKeys.state(p);
		if (s == null) {
			return false;
		}
		long now = p.level().getGameTime();
		return animAge(s, now, 0f) >= 0f || s.val(LadybugKeys.V_LINE) != 0L || s.val(LadybugKeys.V_SHIELD) != 0L
				|| LadybugKeys.bound(p) >= 0 || s.val(LadybugKeys.V_WIRE_UNTIL) > now;
	}

	/** Her right hand, world space, this frame. */
	static Vec3 hand(Minecraft mc, Player p, float partial, Camera cam) {
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
			return cam.getPosition().add(look.x * 0.62, look.y * 0.62, look.z * 0.62).add(-left.x * 0.36, -left.y * 0.36, -left.z * 0.36)
					.add(-up.x * 0.30, -up.y * 0.30, -up.z * 0.30);
		}
		double[] h = HAND.get(p.getId());
		if (h != null && frame - (long) h[3] <= 1) {
			return new Vec3(h[0], h[1], h[2]);
		}
		Vec3 pos = p.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		Vec3 right = Vec3.directionFromRotation(0f, bodyYaw + 90f);
		return pos.add(0, p.getBbHeight() * 0.72, 0).add(right.scale(-0.36)).add(Vec3.directionFromRotation(0f, bodyYaw).scale(0.3));
	}

	static void render(WorldRenderContext context) {
		frame++;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		MultiBufferSource buffers = context.consumers();
		PoseStack pose = context.matrixStack();
		long now = mc.level.getGameTime();
		for (Player p : mc.level.players()) {
			MiraculousState s = LadybugKeys.state(p);
			if (s == null || p.isInvisible() || p.distanceToSqr(cam) > 128 * 128) {
				continue;
			}
			pose.pushPose();
			try {
				draw(mc, p, s, pose, buffers, cam, camera, partial, now);
			} finally {
				pose.popPose();
			}
		}
		LadybugSwarm.render(pose, buffers, cam, camera, partial);
	}

	private static int light(Minecraft mc, Vec3 at) {
		return LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
	}

	private static float spin(long now, float partial, float speed) {
		return (now + partial) * speed;
	}

	/** The Yo-yo at {@code at}, its axle across the line from {@code from}. */
	private static void yoyoAt(Minecraft mc, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 at, Vec3 from, float spin, float open) {
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		Vec3 d = at.subtract(from);
		float yaw = (float) Math.atan2(d.x, d.z);
		pose.mulPose(com.mojang.math.Axis.YP.rotation(yaw));
		LadybugGeometry.yoyo(pose, buffers, light(mc, at), spin, open, 1f);
		pose.popPose();
	}

	private static void line(Minecraft mc, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 a, Vec3 b, float sag, float sway) {
		LadybugGeometry.string(pose, buffers, light(mc, a.lerp(b, 0.5)), a, b, cam, sag, sway, 0xFFF2ECEC);
	}

	private static void draw(Minecraft mc, Player p, MiraculousState s, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Camera camera,
			float partial, long now) {
		Vec3 hand = hand(mc, p, partial, camera);
		float age = animAge(s, now, partial);
		float t = now + partial;
		boolean fp = p == mc.player && mc.options.getCameraType().isFirstPerson();

		// ---- the trip wire (independent of her hand)
		if (s.val(LadybugKeys.V_WIRE_UNTIL) > now) {
			Vec3 a = Miraculous.vec(s, LadybugKeys.P_WIRE_A), b = Miraculous.vec(s, LadybugKeys.P_WIRE_B);
			if (a != null && b != null) {
				line(mc, pose, buffers, cam, a, b, 0f, 0f);
				yoyoAt(mc, pose, buffers, cam, b, a, 0f, 0f);
			}
		}
		// ---- the parachute
		if (s.val(LadybugKeys.V_CHUTE) != 0L) {
			Vec3 pos = p.getPosition(partial);
			float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
			Vec3 right = Vec3.directionFromRotation(0f, yaw + 90f);
			Vec3 l = pos.add(0, 1.95, 0).add(right.scale(0.42)), r = pos.add(0, 1.95, 0).add(right.scale(-0.42));
			LadybugGeometry.canopy(pose, buffers, light(mc, pos.add(0, 4, 0)), pos.add(0, 3.6 + 0.05 * Math.sin(t * 0.1), 0), 1.7f,
					(float) Math.sin(t * 0.05) * 0.08f, l, r, cam);
		}
		// ---- the line (swing / zip): hooked, taut
		if (s.val(LadybugKeys.V_LINE) != 0L) {
			Vec3 hook = Miraculous.vec(s, LadybugKeys.P_HOOK);
			if (hook != null) {
				double len = LadybugKeys.rope(s);
				float sag = s.val(LadybugKeys.V_LINE) == 1L ? LadybugGeometry.sagFor(hand.distanceTo(hook), len) * 0.6f : 0f;
				line(mc, pose, buffers, cam, hand, hook, sag, 0f);
				yoyoAt(mc, pose, buffers, cam, hook, hand, spin(now, partial, 0.9f), 0f);
			}
			return;
		}
		// ---- the Spinning Shield
		if (s.val(LadybugKeys.V_SHIELD) != 0L) {
			float since = now - s.val(LadybugKeys.V_SHIELD) + partial;
			float grow = Mth.clamp(since / 4f, 0f, 1f);
			Vec3 look = p.getViewVector(partial);
			Vec3 center = fp ? camera.getPosition().add(look.scale(1.5)).add(0, -0.32, 0)
						.add(new Vec3(-camera.getLeftVector().x, -camera.getLeftVector().y, -camera.getLeftVector().z).scale(0.28))
					: p.getPosition(partial).add(0, p.getBbHeight() * 0.62, 0).add(look.scale(0.75));
			pose.pushPose();
			pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
			float yaw = (float) Math.atan2(look.x, look.z);
			float pitch = (float) Math.asin(Mth.clamp(look.y, -1, 1));
			pose.mulPose(com.mojang.math.Axis.YP.rotation(yaw));
			pose.mulPose(com.mojang.math.Axis.XP.rotation(-pitch));
			pose.mulPose(com.mojang.math.Axis.YP.rotation((float) (Math.PI / 2))); // disc normal (+X) along the look
			LadybugGeometry.shield(pose, buffers, light(mc, center), spin(now, partial, 1.6f), (fp ? 0.42f : 0.72f) * grow);
			pose.popPose();
			if (!fp) {
				line(mc, pose, buffers, cam, hand, center, 0f, 0f);
			}
			return;
		}
		// ---- the bind: coils round the target, the line held loose
		int bound = LadybugKeys.bound(p);
		if (bound >= 0) {
			Entity e = mc.level.getEntity(bound);
			if (e != null) {
				Vec3 base = e.getPosition(partial);
				float since = s.animId == LadybugKeys.ANIM_BIND ? (now - s.animStart) + partial : 99f;
				float wound = Mth.clamp((since - 3f) / 7f, 0f, 1f);
				Vec3 start = since < 3f ? hand.lerp(base.add(0, e.getBbHeight() * 0.6, 0), since / 3f)
						: LadybugGeometry.coils(pose, buffers, light(mc, base), base, e.getBbWidth(), e.getBbHeight(), wound, cam, 0f);
				double chord = hand.distanceTo(start);
				line(mc, pose, buffers, cam, hand, start, since < 3f ? 0f : LadybugGeometry.sagFor(chord, chord + 1.2) * 0.7f,
						(float) Math.sin(t * 0.15) * 0.08f);
				if (since < 3f) {
					yoyoAt(mc, pose, buffers, cam, start, hand, spin(now, partial, 1.2f), 0f);
				}
			}
			return;
		}
		if (age < 0f) {
			return;
		}
		switch (s.animId) {
			case LadybugKeys.ANIM_STRIKE -> {
				Vec3 tip = Miraculous.vec(s, LadybugKeys.P_TIP);
				if (tip == null) {
					return;
				}
				int out = LadybugConfig.STRIKE_OUT_TICKS, back = LadybugConfig.STRIKE_BACK_TICKS;
				float f = age < out ? age / out : age < out + 1 ? 1f : 1f - Mth.clamp((age - out - 1) / back, 0f, 1f);
				Vec3 at = hand.lerp(tip, f);
				line(mc, pose, buffers, cam, hand, at, age > out + 1 ? 0.25f * f : 0f, age > out + 1 ? 0.15f * f : 0f);
				yoyoAt(mc, pose, buffers, cam, at, hand, spin(now, partial, 1.4f), 0f);
			}
			case LadybugKeys.ANIM_RICOCHET -> {
				List<Vec3> pts = LadybugKeys.points(s, LadybugKeys.P_RICO);
				if (pts.isEmpty()) {
					return;
				}
				List<Vec3> path = new ArrayList<>();
				path.add(hand);
				path.addAll(pts);
				path.add(hand);
				float seg = age / LadybugConfig.RICOCHET_HOP_TICKS;
				int i = Math.min(path.size() - 2, (int) seg);
				float f = Mth.clamp(seg - i, 0f, 1f);
				Vec3 at = path.get(i).lerp(path.get(i + 1), f);
				List<Vec3> shown = new ArrayList<>(path.subList(0, i + 1));
				shown.add(at);
				LadybugGeometry.polyline(pose, buffers, light(mc, at), shown, cam, 0xFFF2ECEC);
				yoyoAt(mc, pose, buffers, cam, at, path.get(i), spin(now, partial, 1.6f), 0f);
			}
			case LadybugKeys.ANIM_YANK, LadybugKeys.ANIM_FLING -> {
				Entity e = s.val(LadybugKeys.V_FLUNG) == 0L ? null : mc.level.getEntity((int) (s.val(LadybugKeys.V_FLUNG) - 1L));
				if (e != null && age < 7f) {
					Vec3 at = e.getPosition(partial).add(0, e.getBbHeight() * 0.55, 0);
					line(mc, pose, buffers, cam, hand, at, 0f, 0f);
					LadybugGeometry.coils(pose, buffers, light(mc, at), e.getPosition(partial), e.getBbWidth(), e.getBbHeight(), 1f, cam, 0f);
				}
			}
			case LadybugKeys.ANIM_VAULT -> {
				Vec3 plant = Miraculous.vec(s, LadybugKeys.P_VAULT);
				if (plant != null && age < 9f) {
					line(mc, pose, buffers, cam, hand, plant, 0f, 0f);
					yoyoAt(mc, pose, buffers, cam, plant, hand, 0f, 0f);
				}
			}
			case LadybugKeys.ANIM_WIRE -> {
				Vec3 b = Miraculous.vec(s, LadybugKeys.P_WIRE_B);
				if (b != null && age < 4f) {
					Vec3 at = hand.lerp(b, age / 4f);
					line(mc, pose, buffers, cam, hand, at, 0f, 0f);
					yoyoAt(mc, pose, buffers, cam, at, hand, spin(now, partial, 1.4f), 0f);
				}
			}
			case LadybugKeys.ANIM_SCAN -> {
				float open = Mth.clamp(age / 5f, 0f, 1f) * Mth.clamp((LadybugConfig.COMPACT_TICKS - age) / 5f, 0f, 1f);
				Vec3 at = fp ? camera.getPosition().add(p.getViewVector(partial).scale(0.55)).add(0, -0.1, 0) : hand;
				pose.pushPose();
				pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
				Vec3 look = p.getViewVector(partial);
				pose.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(look.x, look.z)));
				// the compact faces her: the inner half's screen (+X) toward her eyes
				pose.mulPose(com.mojang.math.Axis.YP.rotation((float) (Math.PI / 2)));
				pose.mulPose(com.mojang.math.Axis.ZP.rotation(0.5f));
				LadybugGeometry.yoyo(pose, buffers, light(mc, at), 0f, open, 1.25f);
				pose.popPose();
			}
			case LadybugKeys.ANIM_CHARM -> {
				Vec3 top = Miraculous.vec(s, LadybugKeys.P_ML);
				Vec3 up = hand.add(0, 5.5, 0);
				float f = age < 8f ? age / 8f : age < 14f ? 1f : 1f - Mth.clamp((age - 14f) / 8f, 0f, 1f);
				Vec3 at = hand.lerp(up, f);
				line(mc, pose, buffers, cam, hand, at, 0f, 0f);
				yoyoAt(mc, pose, buffers, cam, at, hand, spin(now, partial, 1.8f), 0f);
				if (top != null && age > 6f) {
					float b = (age - 6f) / 20f;
					LadybugGeometry.burst(pose, buffers, top, cam, camera.getLeftVector(), camera.getUpVector(), 0.6f + 3.2f * b,
							1f - Mth.clamp(b, 0f, 1f), t * 0.05f);
				}
			}
			case LadybugKeys.ANIM_MIRACULOUS -> {
				Vec3 top = Miraculous.vec(s, LadybugKeys.P_ML);
				if (top != null) {
					float b = age / 40f;
					LadybugGeometry.burst(pose, buffers, top, cam, camera.getLeftVector(), camera.getUpVector(), 1.0f + 6f * b,
							0.9f * (1f - b), -t * 0.04f);
				}
			}
			default -> {
			}
		}
	}
}
