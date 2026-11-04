package com.projecthero.mod.client.galactus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.galactus.orbit.DevourerEntity;
import com.projecthero.mod.galactus.orbit.GalactusRig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * v0.17.2 Galactus raid v2: draws the Devourer. The model is our own {@code galactus} GeckoLib asset at whatever height the
 * raid sets; its waist, head, shoulders, arms and forearms are posed every frame from {@link GalactusRig} (the same solve
 * the server uses for his hit-boxes and fist impacts), from inputs interpolated between the last two ticks so the huge
 * motions stay smooth. His fingers close into fists when he punches. Drawn full-bright in orbit (lit by the Power Cosmic),
 * with burning eyes ({@link DevourerEntity#eyes}) and the Planet Cracker sphere between his hands.
 */
public final class DevourerRenderer extends GalactusRenderers.Mob<DevourerEntity> {
	private static final ResourceLocation GLOW = ResourceLocation.withDefaultNamespace("textures/particle/glow.png");

	public DevourerRenderer(EntityRendererProvider.Context context) {
		super(context, 0f, 0f, new RigModel());
	}

	@Override
	protected float targetHeight(DevourerEntity animatable) {
		return animatable.height();
	}

	@Override
	public boolean shouldRender(DevourerEntity entity, Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	protected int getBlockLightLevel(DevourerEntity e, BlockPos pos) {
		return e.flag(DevourerEntity.FLAG_FULLBRIGHT) ? 15 : 12;
	}

	@Override
	protected int getSkyLightLevel(DevourerEntity e, BlockPos pos) {
		return e.flag(DevourerEntity.FLAG_FULLBRIGHT) ? 15 : super.getSkyLightLevel(e, pos);
	}

	@Override
	public void render(DevourerEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
		drawGlows(entity, partialTick, poseStack, buffer);
	}

	/** Interpolated rig inputs for this frame. */
	static GalactusRig.Input frameInput(DevourerEntity e, float pt) {
		GalactusRig.Input a = e.prevInput, b = e.curInput;
		GalactusRig.Input in = new GalactusRig.Input();
		if (b.feet == null || a.feet == null || b.feet == Vec3.ZERO) {
			return e.input(in);
		}
		// the entity stands at his chest (DevourerEntity.DROP): his feet are that far below
		in.feet = new Vec3(Mth.lerp(pt, e.xOld, e.getX()), Mth.lerp(pt, e.yOld, e.getY()) - Mth.lerp(pt, a.height, b.height) * DevourerEntity.DROP,
				Mth.lerp(pt, e.zOld, e.getZ()));
		in.yawDeg = Mth.rotLerp(pt, a.yawDeg, b.yawDeg);
		in.height = Mth.lerp(pt, a.height, b.height);
		in.lean = Mth.lerp(pt, a.lean, b.lean);
		in.look = lerp(a.look, b.look, pt);
		in.targetR = lerp(a.targetR, b.targetR, pt);
		in.weightR = Mth.lerp(pt, a.weightR, b.weightR);
		in.targetL = lerp(a.targetL, b.targetL, pt);
		in.weightL = Mth.lerp(pt, a.weightL, b.weightL);
		return in;
	}

	private static Vec3 lerp(Vec3 a, Vec3 b, float t) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return a.lerp(b, t);
	}

	/** Eyes (two burning points), and the Planet Cracker sphere between his fists. */
	private void drawGlows(DevourerEntity e, float pt, PoseStack ps, MultiBufferSource buffer) {
		float eyes = Mth.lerp(pt, e.prevEyes, e.eyes());
		float charge = Mth.lerp(pt, e.prevCharge, e.charge());
		if (eyes < 0.01f && charge < 0.01f) {
			return;
		}
		GalactusRig.Pose p = GalactusRig.solve(frameInput(e, pt));
		Vec3 base = new Vec3(Mth.lerp(pt, e.xOld, e.getX()), Mth.lerp(pt, e.yOld, e.getY()), Mth.lerp(pt, e.zOld, e.getZ()));
		VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
		var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
		if (eyes > 0.01f) {
			// the eyes sit either side of the face point
			Vec3 right = new Vec3(-Mth.cos(p.headY + e.getYRot() * Mth.DEG_TO_RAD), 0, -Mth.sin(p.headY + e.getYRot() * Mth.DEG_TO_RAD));
			for (int s = -1; s <= 1; s += 2) {
				Vec3 at = p.face.add(right.scale(s * 3.4 * p.k)).add(0, 1.6 * p.k, 0).subtract(base);
				float size = (2.2f + 1.2f * eyes) * p.k * (0.85f + 0.15f * Mth.sin((e.tickCount + pt) * 0.6f + s));
				quad(ps, vc, cam, at, size * 2.4f, 255, 245, 200, (int) (eyes * 170));
				quad(ps, vc, cam, at, size, 255, 255, 255, (int) (eyes * 255));
			}
		}
		if (charge > 0.01f) {
			Vec3 mid = p.fistR.lerp(p.fistL, 0.5).subtract(base);
			float r = (6f + 22f * charge) * p.k;
			float pulse = 0.9f + 0.1f * Mth.sin((e.tickCount + pt) * 0.9f);
			quad(ps, vc, cam, mid, r * 2.2f * pulse, 170, 90, 255, (int) (120 * charge));
			quad(ps, vc, cam, mid, r * pulse, 235, 210, 255, (int) (230 * charge));
			quad(ps, vc, cam, mid, r * 0.45f, 255, 255, 255, 255);
		}
	}

	private static void quad(PoseStack ps, VertexConsumer vc, net.minecraft.client.Camera cam, Vec3 at, float size, int r, int g, int b, int a) {
		ps.pushPose();
		ps.translate(at.x, at.y, at.z);
		ps.mulPose(cam.rotation());
		Matrix4f m = ps.last().pose();
		var n = ps.last();
		int light = 0xF000F0;
		vc.addVertex(m, -size, -size, 0).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n, 0, 0, 1);
		vc.addVertex(m, size, -size, 0).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n, 0, 0, 1);
		vc.addVertex(m, size, size, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n, 0, 0, 1);
		vc.addVertex(m, -size, size, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n, 0, 0, 1);
		ps.popPose();
	}

	/** The rig on the model's bones. */
	static final class RigModel extends GalactusRenderers.MobModel<DevourerEntity> {
		@Override
		public void setCustomAnimations(DevourerEntity e, long instanceId, AnimationState<DevourerEntity> state) {
			GalactusRig.Pose p = GalactusRig.solve(frameInput(e, state.getPartialTick()));
			set("waist", p.waistX, 0f, 0f);
			set("neck", 0f, 0f, 0f);
			set("head", p.headX, p.headY, 0f);
			set("shoulder_r", 0f, 0f, 0f);
			set("shoulder_l", 0f, 0f, 0f);
			set("arm_r", p.armRX, 0f, p.armRZ);
			set("forearm_r", p.foreRX, 0f, p.foreRZ);
			set("arm_l", p.armLX, 0f, p.armLZ);
			set("forearm_l", p.foreLX, 0f, p.foreLZ);
			set("hand_r", 0f, 0f, 0f);
			set("hand_l", 0f, 0f, 0f);
			float fr = e.flag(DevourerEntity.FLAG_FIST_R) ? 1f : 0.15f;
			float fl = e.flag(DevourerEntity.FLAG_FIST_L) ? 1f : 0.15f;
			// the fingers curl forward into a fist (they hang down at rest; +X swings toward the palm side)
			set("fingers_r", 1.35f * fr, 0f, 0f);
			set("fingertips_r", 1.5f * fr, 0f, 0f);
			set("fingers_l", 1.35f * fl, 0f, 0f);
			set("fingertips_l", 1.5f * fl, 0f, 0f);
			GeoBone flinch = getAnimationProcessor().getBone("flinch");
			if (flinch != null) {
				flinch.setRotX(0f);
				flinch.setRotZ(0f);
			}
		}

		private void set(String name, float x, float y, float z) {
			GeoBone b = getAnimationProcessor().getBone(name);
			if (b != null) {
				b.setRotX(x);
				b.setRotY(y);
				b.setRotZ(z);
			}
		}
	}

	@Override
	public void preRender(PoseStack poseStack, DevourerEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
			boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		if (!isReRender) {
			float h = Mth.lerp(partialTick, animatable.prevInput.height > 0 ? animatable.prevInput.height : animatable.height(), animatable.height());
			poseStack.translate(0, -h * DevourerEntity.DROP, 0);
		}
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
	}
}
