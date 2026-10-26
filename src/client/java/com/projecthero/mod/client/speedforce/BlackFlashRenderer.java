package com.projecthero.mod.client.speedforce;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.raid.RaidPlayerRenderers;
import com.projecthero.mod.speedforce.entity.BlackFlashEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: how the Black Flash looks -- the user's chosen skin (novaskin 1950743415, a wide-arm skin) on the player rig, its
 * red lightning lines full-bright (a glowmask cut from the skin's red), code poses for his moves, a trail of dark
 * after-images behind him (filled in along a zip, so every blink reads as a streak), see-through as a Time Remnant, and
 * the death scene: no fall-over, he fades out into black lightning (the bolts: {@code SpeedForceClient}).
 */
public class BlackFlashRenderer extends MobRenderer<BlackFlashEntity, BlackFlashRenderer.Model> {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/blackflash/black_flash.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/blackflash/black_flash_glowmask.png");
	/** After-images: how long each lasts and how far apart the fill-ins along a zip are. */
	private static final int TRAIL_LIFE = 10;
	private static final double FILL = 1.8;

	private record Snap(long time, double x, double y, double z, float yaw, float limb) {
	}

	private static final Map<Integer, Deque<Snap>> TRAILS = new HashMap<>();
	private static final Map<Integer, Integer> LAST_TICK = new HashMap<>();

	/** The rig, posed by his synced action. */
	public static final class Model extends PlayerModel<BlackFlashEntity> {
		public Model(ModelPart root) {
			super(root, false);
		}

		@Override
		public void setupAnim(BlackFlashEntity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
			super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
			float t = ageInTicks;
			switch (e.action()) {
				case BlackFlashEntity.ACTION_WINDUP, BlackFlashEntity.ACTION_MASS -> {
					// the right fist cocked back, the left out in front
					rightArm.xRot = 0.9f;
					rightArm.zRot = 0.2f;
					leftArm.xRot = -1.1f;
					body.yRot = 0.35f;
					if (e.action() == BlackFlashEntity.ACTION_MASS) {
						rightArm.xRot = 1.2f + Mth.sin(t * 2.0f) * 0.05f;
					}
				}
				case BlackFlashEntity.ACTION_PUNCH -> {
					rightArm.xRot = -Mth.HALF_PI;
					rightArm.yRot = -0.1f;
					leftArm.xRot = 0.6f;
					body.yRot = -0.3f;
				}
				case BlackFlashEntity.ACTION_FLURRY -> {
					float a = Mth.sin(t * 2.6f);
					rightArm.xRot = -Mth.HALF_PI + a * 0.5f;
					leftArm.xRot = -Mth.HALF_PI - a * 0.5f;
				}
				case BlackFlashEntity.ACTION_THROW -> {
					rightArm.xRot = -2.7f;
					rightArm.zRot = -0.2f;
					leftArm.xRot = -0.4f;
				}
				case BlackFlashEntity.ACTION_ARMS -> {
					float a = t * 1.8f;
					rightArm.xRot = -Mth.HALF_PI + Mth.sin(a) * 0.9f;
					leftArm.xRot = -Mth.HALF_PI + Mth.cos(a) * 0.9f;
					rightArm.zRot = Mth.cos(a) * 0.6f;
					leftArm.zRot = -Mth.sin(a) * 0.6f;
				}
				case BlackFlashEntity.ACTION_CROUCH, BlackFlashEntity.ACTION_VORTEX -> {
					body.xRot = 0.5f;
					rightArm.xRot = 0.8f;
					leftArm.xRot = 0.8f;
					rightArm.zRot = 0.3f;
					leftArm.zRot = -0.3f;
					rightLeg.xRot = -0.35f;
					leftLeg.xRot = 0.25f;
				}
				case BlackFlashEntity.ACTION_CHARGE -> {
					// a sprinter's lean, arms pumping
					float a = Mth.sin(t * 1.6f);
					body.xRot = 0.45f;
					rightArm.xRot = a * 1.3f;
					leftArm.xRot = -a * 1.3f;
					rightLeg.xRot = -a * 1.2f;
					leftLeg.xRot = a * 1.2f;
				}
				case BlackFlashEntity.ACTION_FLASH -> {
					rightArm.xRot = -2.9f;
					leftArm.xRot = -0.2f;
				}
				case BlackFlashEntity.ACTION_SPENT -> {
					body.xRot = 0.35f;
					head.xRot = 0.5f;
					rightArm.xRot = -0.3f;
					leftArm.xRot = -0.3f;
					rightArm.zRot = 0.1f;
					leftArm.zRot = -0.1f;
				}
				case BlackFlashEntity.ACTION_PHASE -> {
					float j = Mth.sin(t * 9f) * 0.08f;
					body.zRot = j;
					head.zRot = -j;
				}
				default -> {
				}
			}
			hat.copyFrom(head);
			jacket.copyFrom(body);
			leftSleeve.copyFrom(leftArm);
			rightSleeve.copyFrom(rightArm);
			leftPants.copyFrom(leftLeg);
			rightPants.copyFrom(rightLeg);
		}
	}

	public BlackFlashRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		addLayer(new RaidPlayerRenderers.GlowLayer<>(this, e -> GLOW));
	}

	@Override
	public ResourceLocation getTextureLocation(BlackFlashEntity e) {
		return SKIN;
	}

	@Override
	protected void scale(BlackFlashEntity e, PoseStack pose, float pt) {
		pose.scale(0.9375f, 0.9375f, 0.9375f);
	}

	@Override
	protected float getFlipDegrees(BlackFlashEntity e) {
		return 0f; // he never falls over: he dissolves
	}

	@Override
	protected int getBlockLightLevel(BlackFlashEntity e, BlockPos pos) {
		return Math.max(8, super.getBlockLightLevel(e, pos));
	}

	@Override
	public void render(BlackFlashEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
		long now = e.level().getGameTime();
		Vec3 at = e.getPosition(pt);
		record(e, now);
		// the trail behind him
		Deque<Snap> q = TRAILS.get(e.getId());
		if (q != null) {
			for (Snap s : q) {
				float age = (now - s.time()) + pt;
				float fade = 1f - age / TRAIL_LIFE;
				if (fade <= 0f || (s.x() - at.x) * (s.x() - at.x) + (s.z() - at.z) * (s.z() - at.z) < 0.25) {
					continue;
				}
				pose.pushPose();
				pose.translate(s.x() - at.x, s.y() - at.y, s.z() - at.z);
				ghost(e, pose, buffers, s.yaw(), s.limb(), pt, 0.24f * fade);
				pose.popPose();
			}
		}
		if (e.deathTime > 0) {
			// the death scene: he fades out, dissolving upward
			float k = Mth.clamp((e.deathTime + pt) / 50f, 0f, 1f);
			pose.pushPose();
			pose.translate(0, k * 0.6, 0);
			ghost(e, pose, buffers, Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot), 0f, pt, 0.9f * (1f - k));
			pose.popPose();
			return;
		}
		if (e.isRemnant()) {
			ghost(e, pose, buffers, Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot), e.walkAnimation.position(pt), pt, 0.6f);
			return;
		}
		super.render(e, yaw, pt, pose, buffers, light);
	}

	/** One see-through copy of him at the pose stack's origin, facing {@code bodyYaw}. */
	private void ghost(BlackFlashEntity e, PoseStack pose, MultiBufferSource buffers, float bodyYaw, float limb, float pt, float alpha) {
		if (alpha <= 0.01f) {
			return;
		}
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
		pose.scale(-1.0f, -1.0f, 1.0f);
		pose.scale(0.9375f, 0.9375f, 0.9375f);
		pose.translate(0.0f, -1.501f, 0.0f);
		model.attackTime = 0f;
		model.setupAnim(e, limb, 1.0f, e.tickCount + pt, 0f, 0f);
		int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255f);
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(SKIN)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				(a << 24) | 0xC8C8D0);
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				(a << 24) | 0xFFFFFF);
		pose.popPose();
	}

	/** Once a tick: where he was (a zip fills in copies along the gap). */
	private static void record(BlackFlashEntity e, long now) {
		Integer last = LAST_TICK.put(e.getId(), e.tickCount);
		if (last != null && last == e.tickCount) {
			return;
		}
		Deque<Snap> q = TRAILS.computeIfAbsent(e.getId(), k -> new ArrayDeque<>());
		q.removeIf(s -> now - s.time() > TRAIL_LIFE || s.time() > now);
		double dx = e.getX() - e.xo;
		double dz = e.getZ() - e.zo;
		double gap = Math.sqrt(dx * dx + dz * dz + (e.getY() - e.yo) * (e.getY() - e.yo));
		if (gap < 0.15) {
			return;
		}
		int n = (int) Math.max(1, Math.min(24, Math.ceil(gap / FILL)));
		float yaw = e.yBodyRotO;
		float limb = e.walkAnimation.position(0f);
		for (int i = 0; i < n; i++) {
			double t = i / (double) n;
			q.addLast(new Snap(now, e.xo + dx * t, e.yo + (e.getY() - e.yo) * t, e.zo + dz * t, yaw, limb + i * 0.8f));
		}
		if (TRAILS.size() > 64) {
			TRAILS.keySet().removeIf(id -> e.level().getEntity(id) == null);
			LAST_TICK.keySet().retainAll(TRAILS.keySet());
		}
	}

	static void reset() {
		TRAILS.clear();
		LAST_TICK.clear();
	}
}
