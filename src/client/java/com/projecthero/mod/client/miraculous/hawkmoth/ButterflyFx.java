package com.projecthero.mod.client.miraculous.hawkmoth;

import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothConfig;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothKit;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: Hawk Moth's white butterflies and the akuma masks, drawn in the world after the entities (every viewer: all
 * of it comes from synced state or the broadcast payloads). Every butterfly is a {@link ButterflyModel}, each on its own
 * path and wing beat.
 * <ul>
 *   <li>Swarm Flight: a cloud swirling round his legs and body, carrying him.</li>
 *   <li>Swarm Dash: he is hidden in a cloud that darts with him, then pulls back together into his shape.</li>
 *   <li>Butterfly Shield: a fluttering wall of them in front of him.</li>
 *   <li>Swarm Blind: a swarm round each creature's head.</li>
 *   <li>RISE, AKUMA!: one white butterfly in his cupped hands, darkening into an akuma.</li>
 *   <li>Every akumatized creature and Champion: a glowing violet butterfly mask over its face.</li>
 * </ul>
 */
public final class ButterflyFx {
	public static final ResourceLocation MASK = ProjectHeroMod.id("textures/entity/miraculous/hawk_moth/mask.png");
	private static final float GOLDEN = 2.39996f;

	private ButterflyFx() {
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		long now = mc.level.getGameTime();
		float t = now + partial;
		VertexConsumer white = buffers.getBuffer(RenderType.entityCutoutNoCull(ButterflyModel.WHITE));

		for (Player p : mc.level.players()) {
			MiraculousState s = Miraculous.peek(p);
			if (s == null || !Miraculous.transformedAs(p, Kwami.NOOROO) || p.distanceToSqr(cam) > 96 * 96) {
				continue;
			}
			Vec3 pos = p.getPosition(partial);
			boolean self = p == mc.player && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == p;
			if (s.val(HawkMothKit.V_FLIGHT_UNTIL) > now) {
				int n = self ? 18 : 34;
				for (int i = 0; i < n; i++) {
					float a = i * GOLDEN + t * (0.10f + 0.03f * (i % 3));
					float r = 0.45f + 0.35f * ((i * 7) % 5) / 5f;
					float y = 0.05f + (self ? 0.25f : 1.15f) * ((i * 13) % 11) / 11f + 0.08f * Mth.sin(t * 0.3f + i);
					Vec3 at = pos.add(Mth.cos(a) * r, y, Mth.sin(a) * r);
					float heading = -(a * Mth.RAD_TO_DEG) - 180f;
					one(pose, white, mc, cam, at, heading, 10f, t * 1.7f + i, 0.6f, 0xFFFFFFFF);
				}
			}
			long dashAt = s.val(HawkMothKit.V_DASH_AT);
			if (dashAt > 0L) {
				float age = now - dashAt + partial;
				float total = HawkMothConfig.DASH_TICKS;
				float spread = age <= total ? 0.9f : 0.9f * Mth.clamp(1f - (age - total) / HawkMothConfig.DASH_REFORM_TICKS, 0f, 1f);
				Vec3 c = pos.add(0, 0.95, 0);
				for (int i = 0; i < 64; i++) {
					// a fixed spot in a body-shaped cloud for each butterfly, fluttering about it
					float u = ((i * 37) % 64) / 64f;
					float a = i * GOLDEN + t * 0.4f;
					float r = (0.25f + 0.75f * u) * (0.2f + spread);
					float y = ((i * 53) % 64) / 64f * 1.9f - 0.95f;
					Vec3 at = c.add(Mth.cos(a) * r, y * (0.35f + 0.65f * Math.min(1f, spread + 0.5f)), Mth.sin(a) * r);
					one(pose, white, mc, cam, at, -(a * Mth.RAD_TO_DEG), 0f, t * 1.9f + i, 0.55f, 0xFFFFFFFF);
				}
			}
			if (s.val(HawkMothKit.V_SHIELD_AT) > 0L) {
				Vec3 c = HawkMothKit.shieldCentre(p);
				Vec3 look = p.getViewVector(partial);
				Vec3 flat = new Vec3(look.x, 0, look.z);
				flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
				Vec3 side = new Vec3(-flat.z, 0, flat.x);
				float heading = (float) (Mth.atan2(flat.x, flat.z) * Mth.RAD_TO_DEG);
				float grow = Mth.clamp((now - s.val(HawkMothKit.V_SHIELD_AT) + partial) / 5f, 0f, 1f);
				int cols = 8, rows = 7;
				for (int iy = 0; iy < rows; iy++) {
					for (int ix = 0; ix < cols; ix++) {
						int i = iy * cols + ix;
						float jx = 0.08f * Mth.sin(t * 0.5f + i * 1.3f);
						float jy = 0.07f * Mth.cos(t * 0.45f + i * 0.7f);
						double ox = ((ix + 0.5) / cols - 0.5) * HawkMothConfig.SHIELD_WIDTH * grow + jx;
						double oy = ((iy + 0.5) / rows - 0.5) * HawkMothConfig.SHIELD_HEIGHT * grow + jy;
						Vec3 at = c.add(side.scale(ox)).add(0, oy, 0).add(flat.scale(0.06 * Mth.sin(i)));
						// facing back at him, wings spread flat into the wall
						one(pose, white, mc, cam, at, heading + 180f, 80f, t * 1.2f + i * 0.9f, 0.62f, 0xFFFFFFFF);
					}
				}
			}
			if (s.animId == HawkMothKit.ANIM_RISE) {
				float age = now - s.animStart + partial;
				if (age >= 0f && age < HawkMothConfig.RISE_CUP_TICKS) {
					float k = Mth.clamp(age / HawkMothConfig.RISE_CUP_TICKS, 0f, 1f);
					float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
					Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
					Vec3 at = pos.add(fwd.scale(0.62)).add(0, 1.2 + 0.03 * Mth.sin(age * 0.5f), 0);
					int col = FastColor.ARGB32.color(255, (int) Mth.lerp(k, 255, 70), (int) Mth.lerp(k, 255, 22), (int) Mth.lerp(k, 255, 100));
					one(pose, white, mc, cam, at, Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) + 180f, 65f, age * 0.5f, 0.8f, col);
				}
			}
		}

		// Swarm Blind
		for (Map.Entry<Integer, Long> e : HawkMothFxClient.SWARMED.entrySet()) {
			Entity en = mc.level.getEntity(e.getKey());
			if (en == null || !en.isAlive() || e.getValue() < now) {
				HawkMothFxClient.SWARMED.remove(e.getKey());
				continue;
			}
			Vec3 c = en.getPosition(partial).add(0, en.getBbHeight() * 0.85, 0);
			float r = en.getBbWidth() * 0.6f + 0.3f;
			for (int i = 0; i < 12; i++) {
				float a = i * GOLDEN + t * 0.35f * (i % 2 == 0 ? 1 : -1);
				Vec3 at = c.add(Mth.cos(a) * r, 0.35f * Mth.sin(t * 0.4f + i * 2.1f), Mth.sin(a) * r);
				one(pose, white, mc, cam, at, -(a * Mth.RAD_TO_DEG), 0f, t * 1.8f + i, 0.55f, 0xFFFFFFFF);
			}
		}

		// the masks
		VertexConsumer mask = null;
		for (Map.Entry<Integer, Integer> e : HawkMothFxClient.marks().entrySet()) {
			Entity en = mc.level.getEntity(e.getKey());
			if (!(en instanceof LivingEntity le) || !le.isAlive() || le.isInvisible() || le.distanceToSqr(cam) > 64 * 64) {
				continue;
			}
			if (le == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
				continue;
			}
			if (mask == null) {
				mask = buffers.getBuffer(RenderType.entityTranslucentEmissive(MASK));
			}
			mask(pose, mask, cam, le, partial, e.getValue() == 2);
		}
	}

	static void one(PoseStack pose, VertexConsumer vc, Minecraft mc, Vec3 cam, Vec3 at, float yaw, float pitch, float beat, float scale,
			int argb) {
		int l = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
		// the swarm has a faint glow of its own (it reads in the dark)
		int light = LightTexture.pack(Math.max(LightTexture.block(l), 12), LightTexture.sky(l));
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		ButterflyModel.render(pose, vc, light, yaw, pitch, beat, scale, argb);
		pose.popPose();
	}

	/** A glowing violet butterfly over the creature's face, turning with its head. */
	private static void mask(PoseStack pose, VertexConsumer vc, Vec3 cam, LivingEntity e, float partial, boolean champion) {
		float yaw = Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot);
		float pitch = Mth.lerp(partial, e.xRotO, e.getXRot());
		float scale = e.getScale();
		Vec3 base = e.getPosition(partial);
		Vec3 pivot;
		float fwd, up, w;
		if (e instanceof Player p) {
			// a player's head turns on the neck; the eyes sit 4.5 px above it, the face 4.5 px in front (the hat's front)
			pivot = base.add(0, (p.isCrouching() ? 1.2 : 1.5) * scale, 0);
			fwd = 0.3f * scale;
			up = 0.29f * scale;
			w = 0.56f * scale;
		} else {
			pivot = e.getEyePosition(partial);
			fwd = e.getBbWidth() * 0.5f + 0.04f * scale;
			up = 0.02f * scale;
			w = Math.max(0.42f * scale, e.getBbWidth() * 0.62f);
		}
		float h = w * 0.5f;
		int alpha = champion ? 255 : 220;
		pose.pushPose();
		pose.translate(pivot.x - cam.x, pivot.y - cam.y, pivot.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.translate(0, up, fwd);
		PoseStack.Pose last = pose.last();
		Matrix4f m = last.pose();
		vertex(vc, m, last, w / 2, h / 2, 0f, 0f, alpha);
		vertex(vc, m, last, -w / 2, h / 2, 1f, 0f, alpha);
		vertex(vc, m, last, -w / 2, -h / 2, 1f, 1f, alpha);
		vertex(vc, m, last, w / 2, -h / 2, 0f, 1f, alpha);
		pose.popPose();
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, float x, float y, float u, float v, int alpha) {
		vc.addVertex(m, x, y, 0f).setColor(255, 255, 255, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0f, 0f, 1f);
	}
}
