package com.projecthero.mod.client.sentry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.sentry.VoidMoves;
import com.projecthero.mod.sentry.network.SentryFxPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 round 3 (user: "Make the shadow clones models look better please, make them look like really thin dark transparent player models
 * with tiny white eyes"): the Void's shadow figures -- the Shadow Clones and the Nightmare's manifestations -- drawn as real player models
 * (the regular-mob rule): slim-armed, squeezed to 60% width and depth so every limb is thin, a near-black translucent body with a drifting
 * smoky grain (a generated texture, its alpha breathing a little), and two tiny full-bright eye specks ({@link SentrySuitLayer#eyeSpecks}).
 *
 * <p>A clone's position comes every 2 ticks ({@code SentryFxPayload.SHADE}, {@code owner} = {@link VoidMoves#CLONE_FX_ID} + its zone id):
 * each clone is tracked by that id and slid between the last two positions, its yaw eased toward what it faces, its legs walking by the
 * distance it moved and its arms reaching. It fades in when it rises and out when the updates stop. A Nightmare figure (owner -1) stands
 * where it was sent, stalking in place, for its life. All client-only, cleared on disconnect.
 */
public final class ShadeFigures {
	private static final class Figure {
		Vec3 prev;
		Vec3 cur;
		Vec3 face;
		long stamp;
		long born;
		float yaw;
		float walk;
		float height;
		/** A nightmare figure: fixed, dies at {@code until}. */
		boolean fixed;
		long until;
	}

	private static final Map<Integer, Figure> CLONES = new HashMap<>();
	private static final List<Figure> FIXED = new ArrayList<>();
	private static PlayerModel<LivingEntity> model;
	private static ResourceLocation texture;

	private ShadeFigures() {
	}

	public static void reset() {
		CLONES.clear();
		FIXED.clear();
	}

	/** A SHADE payload: a clone update (by id) or a nightmare figure. */
	static void receive(SentryFxPayload p, long now) {
		Vec3 at = new Vec3(p.x(), p.y(), p.z());
		Vec3 to = new Vec3(p.tx(), p.ty(), p.tz());
		if (p.owner() >= VoidMoves.CLONE_FX_ID) {
			Figure f = CLONES.get(p.owner());
			if (f == null) {
				f = new Figure();
				f.prev = at;
				f.cur = at;
				f.born = now;
				f.yaw = yawTo(at, to);
				CLONES.put(p.owner(), f);
			}
			f.walk += (float) Math.sqrt((at.x - f.cur.x) * (at.x - f.cur.x) + (at.z - f.cur.z) * (at.z - f.cur.z));
			f.prev = f.cur;
			f.cur = at;
			f.face = to;
			f.stamp = now;
			f.height = p.size();
			return;
		}
		Figure f = new Figure();
		f.prev = at;
		f.cur = at;
		f.face = to;
		f.stamp = now;
		f.born = now;
		f.fixed = true;
		f.until = now + Math.max(1, p.ticks());
		f.height = p.size();
		f.yaw = yawTo(at, to);
		if (FIXED.size() > 24) {
			FIXED.remove(0);
		}
		FIXED.add(f);
	}

	private static float yawTo(Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dz = to.z - from.z;
		return dx * dx + dz * dz < 1.0e-6 ? 0f : (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
	}

	private static PlayerModel<LivingEntity> model() {
		if (model == null) {
			model = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
		}
		return model;
	}

	/** The shadow body: near-black, translucent, a smoky grain that runs up the figure (generated once). */
	private static ResourceLocation texture() {
		if (texture == null) {
			NativeImage img = new NativeImage(64, 64, true);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					int h = (x * 73856093) ^ (y * 19349663);
					h = (h ^ (h >>> 13)) * 0x5bd1e995;
					float n = ((h >>> 8) & 0xFF) / 255f;
					float streak = 0.5f + 0.5f * Mth.sin(x * 0.9f + y * 0.35f);
					int a = (int) (150 + 70 * n * streak);
					int r = 6 + (int) (10 * n), g = 3 + (int) (5 * n), b = 12 + (int) (16 * n);
					img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r); // ABGR
				}
			}
			texture = Minecraft.getInstance().getTextureManager().register("projecthero_sentry_shade", new DynamicTexture(img));
		}
		return texture;
	}

	/** Draws every live figure (from {@code SentryFxClient.render}, after the ribbons). */
	static void render(PoseStack pose, MultiBufferSource buffers, Vec3 cam, long gameTime, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		float now = gameTime + partial;
		for (Iterator<Figure> it = CLONES.values().iterator(); it.hasNext();) {
			Figure f = it.next();
			float since = now - f.stamp;
			if (since > 8f) {
				it.remove();
				continue;
			}
			float k = Mth.clamp(since / 2f, 0f, 1f);
			Vec3 at = f.prev.lerp(f.cur, k);
			float want = f.face != null ? yawTo(at, f.face) : f.yaw;
			f.yaw += Mth.wrapDegrees(want - f.yaw) * 0.25f;
			float in = Mth.clamp((now - f.born) / 6f, 0f, 1f);
			float out = since <= 3f ? 1f : Mth.clamp(1f - (since - 3f) / 4f, 0f, 1f);
			float moving = (float) Mth.clamp(f.prev.distanceTo(f.cur) / 0.5, 0.0, 1.0);
			draw(pose, buffers, cam, mc, at, f.yaw, f.height, f.walk + moving * k * 0.6f, moving, in * out, now, f.face);
		}
		for (Iterator<Figure> it = FIXED.iterator(); it.hasNext();) {
			Figure f = it.next();
			if (gameTime >= f.until) {
				it.remove();
				continue;
			}
			float age = now - f.born;
			float life = f.until - f.born;
			float a = Math.min(1f, age / 4f) * Math.min(1f, (life - age) / 6f);
			draw(pose, buffers, cam, mc, f.cur, f.yaw, f.height, age * 0.12f, 0.35f, Math.max(0f, a), now, f.face);
		}
	}

	private static void draw(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Minecraft mc, Vec3 at, float yaw, float h, float walk,
			float moving, float alpha, float t, Vec3 face) {
		if (alpha <= 0.02f) {
			return;
		}
		PlayerModel<LivingEntity> m = model();
		m.setAllVisible(true);
		m.hat.visible = false;
		m.jacket.visible = false;
		m.leftSleeve.visible = false;
		m.rightSleeve.visible = false;
		m.leftPants.visible = false;
		m.rightPants.visible = false;
		m.young = false;
		m.riding = false;
		m.crouching = false;
		m.attackTime = 0f;
		// a stalking walk: thin legs scissoring by the distance moved, arms reaching ahead, the head hunched forward a little
		float stride = Mth.sin(walk * 2.2f);
		float swing = 0.55f * moving + 0.12f;
		resetPart(m.head);
		resetPart(m.body);
		resetPart(m.rightArm);
		resetPart(m.leftArm);
		resetPart(m.rightLeg);
		resetPart(m.leftLeg);
		m.rightLeg.x = -1.9f;
		m.rightLeg.y = 12f;
		m.leftLeg.x = 1.9f;
		m.leftLeg.y = 12f;
		m.rightArm.x = -5f;
		m.rightArm.y = 2.5f;
		m.leftArm.x = 5f;
		m.leftArm.y = 2.5f;
		m.rightLeg.xRot = stride * swing;
		m.leftLeg.xRot = -stride * swing;
		float reach = 0.04f * Mth.sin(t * 0.3f);
		m.rightArm.xRot = -0.85f - stride * 0.25f * moving + reach;
		m.leftArm.xRot = -0.85f + stride * 0.25f * moving - reach;
		m.rightArm.zRot = 0.12f;
		m.leftArm.zRot = -0.12f;
		m.body.xRot = 0.18f;
		m.head.xRot = 0.15f;
		m.head.zRot = 0.06f * Mth.sin(t * 0.11f);
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
		float sy = h / 1.8f;
		pose.scale(-0.6f * sy, -sy, 0.6f * sy); // thin: 60% wide and deep
		pose.translate(0f, -1.501f, 0f);
		int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at.add(0, 1, 0)));
		float breathe = 0.85f + 0.15f * Mth.sin(t * 0.21f + (float) at.x);
		int a = Math.round(255f * Mth.clamp(alpha * 0.82f * breathe, 0f, 1f));
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(texture())), light, OverlayTexture.NO_OVERLAY, (a << 24) | 0xFFFFFF);
		if (alpha > 0.3f) {
			SentrySuitLayer.eyeSpecks(pose, buffers, m.head, t);
		}
		pose.popPose();
	}

	private static void resetPart(net.minecraft.client.model.geom.ModelPart p) {
		p.xRot = 0f;
		p.yRot = 0f;
		p.zRot = 0f;
		p.x = 0f;
		p.y = 0f;
		p.z = 0f;
	}
}
