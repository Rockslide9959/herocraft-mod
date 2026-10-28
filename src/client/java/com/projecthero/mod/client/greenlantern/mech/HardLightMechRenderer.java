package com.projecthero.mod.client.greenlantern.mech;

import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.projecthero.mod.client.greenlantern.HardLightDraw;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.greenlantern.mech.GreenLanternMechConfig;
import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

/**
 * v0.17.1: draws the Hard-Light Mech -- the whole model translucent and full-bright ({@code entityTranslucentEmissive}: the
 * dark inner frame and the pilot show through the plates), then an additive pass of the edge-glow texture on top, so
 * the edge lines, the lantern on the chest, the visor and the thrusters burn bright. Rank 5 (Corps Legend) turns it
 * golden-green. It fades out while dissolving and flashes while it assembles.
 *
 * <p>Plus the hard light that is not part of the body: the Chest Lantern Beam, the Hard-Light Shield wall and the Ground
 * Slam's shockwave ring ({@link HardLightDraw}). In first person the pilot sees out of the cockpit: the torso, head and
 * backpack are not drawn for them (the shoulders, arms and fists still are).
 */
public class HardLightMechRenderer extends GeoEntityRenderer<HardLightMechEntity> {
	private static final Set<String> COCKPIT_BONES = Set.of("torso", "head", "thrusters");
	private boolean hideCockpit;

	public HardLightMechRenderer(EntityRendererProvider.Context context) {
		super(context, new HardLightMechModel());
		this.shadowRadius = 1.6f;
		addRenderLayer(new GlowLayer(this));
	}

	@Override
	public boolean shouldRender(HardLightMechEntity entity, Frustum frustum, double x, double y, double z) {
		return true; // the beam reaches far outside the body
	}

	@Override
	public RenderType getRenderType(HardLightMechEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource,
			float partialTick) {
		return RenderType.entityTranslucentEmissive(texture);
	}

	@Override
	public void render(HardLightMechEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		Minecraft mc = Minecraft.getInstance();
		hideCockpit = mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() != null && e.hasPassenger(mc.getCameraEntity());
		// v0.17.1 construct combos: the drill replaces the right fist; the bones' poses are caught for the weapons
		hideFist = MechComboRender.drillScale(e, partial) > 0.3f;
		haveFist = haveForearm = false;
		super.render(e, yaw, partial, pose, buffers, LightTexture.FULL_BRIGHT);
		hideCockpit = false;
		hideFist = false;
		HardLightDraw.golden = e.golden();
		try {
			renderFx(e, partial, pose, buffers);
			MechComboRender.render(e, partial, pose, buffers, haveFist ? fistPose : null, haveForearm ? forearmPose : null);
		} finally {
			HardLightDraw.golden = false;
		}
	}

	private boolean hideFist;
	private boolean haveFist;
	private boolean haveForearm;
	private final PoseStack.Pose fistPose = new PoseStack().last();
	private final PoseStack.Pose forearmPose = new PoseStack().last();

	private static void copy(PoseStack.Pose from, PoseStack.Pose to) {
		to.pose().set(from.pose());
		to.normal().set(from.normal());
	}

	/** 0..1 how solid the mech is: it builds up while assembling and fades out while dissolving. */
	public static float solidity(HardLightMechEntity e, float partial) {
		float t = e.stateTicks() + partial;
		return switch (e.state()) {
			case HardLightMechEntity.STATE_ASSEMBLING -> Mth.clamp(0.35f + t / GreenLanternMechConfig.ASSEMBLE_TICKS, 0f, 1f);
			case HardLightMechEntity.STATE_DISSOLVING -> Mth.clamp(1f - t / GreenLanternMechConfig.DISSOLVE_TICKS, 0f, 1f);
			default -> 1f;
		};
	}

	@Override
	public Color getRenderColor(HardLightMechEntity e, float partialTick, int packedLight) {
		float a = solidity(e, partialTick);
		if (e.golden()) {
			return Color.ofRGBA(1.0f, 0.95f, 0.5f, a);
		}
		return Color.ofRGBA(1.0f, 1.0f, 1.0f, a);
	}

	@Override
	public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
		String name = bone.getName();
		if ("fist_r".equals(name)) {
			copy(poseStack.last(), fistPose);
			haveFist = true;
			if (hideFist) {
				return;
			}
		} else if ("forearm_r".equals(name)) {
			copy(poseStack.last(), forearmPose);
			haveForearm = true;
		}
		if (hideCockpit && COCKPIT_BONES.contains(name)) {
			return;
		}
		super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
	}

	// ---------------------------------------------------------------- beam / shield / shockwave

	private void renderFx(HardLightMechEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		VertexConsumer vc = HardLightDraw.buffer(buffers);
		float age = e.tickCount + partial;
		Vec3 origin = e.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
		if (e.hasFlag(HardLightMechEntity.FLAG_BEAM) && e.isActive()) {
			beam(e, vc, pose, origin, bodyYaw, age);
		}
		if (e.hasFlag(HardLightMechEntity.FLAG_SHIELD) && e.isActive()) {
			shield(vc, pose, bodyYaw, age);
		}
		float since = (float) (e.level().getGameTime() - e.slamAt()) + partial;
		if (since >= 0f && since < 16f) {
			slamRing(vc, pose, bodyYaw, since);
		}
	}

	private static Vec3 local(Vec3 origin, float yawDeg, double x, double y, double z) {
		double yaw = Math.toRadians(yawDeg);
		double sin = Math.sin(yaw), cos = Math.cos(yaw);
		return new Vec3(x * cos - z * sin, y, x * sin + z * cos);
	}

	private static void beam(HardLightMechEntity e, VertexConsumer vc, PoseStack pose, Vec3 origin, float yaw, float age) {
		Vec3 chest = local(origin, yaw, 0, 4.58, 1.0);
		Vec3 end = e.beamEnd().subtract(origin);
		Vec3 d = end.subtract(chest);
		float len = (float) d.length();
		if (len < 0.5f) {
			return;
		}
		pose.pushPose();
		pose.translate(chest.x, chest.y, chest.z);
		float ry = (float) Math.atan2(d.x, d.z);
		float rx = (float) -Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z));
		pose.mulPose(Axis.YP.rotation(ry));
		pose.mulPose(Axis.XP.rotation(rx));
		float pulse = 1f + 0.12f * Mth.sin(age * 1.3f);
		HardLightDraw.streak(vc, pose, len, 0.62f * pulse, age, 0.95f);
		HardLightDraw.streak(vc, pose, len, 1.05f * pulse, -age, 0.35f);
		// the lantern on the chest blazes, and a flare where the beam lands
		HardLightDraw.emblem(vc, pose, 1.9f * pulse, 0.9f);
		HardLightDraw.glow(vc, pose, 0f, 0f, 0.05f, 0.5f * pulse, 0.5f * pulse, 0.08f, 0.9f);
		pose.translate(0f, 0f, len);
		pose.mulPose(Axis.ZP.rotationDegrees(age * 12f));
		HardLightDraw.glow(vc, pose, 0f, 0f, 0f, 0.55f * pulse, 0.55f * pulse, 0.55f * pulse, 0.85f);
		TurboDraw.box(vc, pose.last(), 1.2f * pulse, 1.2f * pulse, 0.04f, HardLightDraw.tint(HardLightDraw.GREEN), 0.3f);
		pose.popPose();
	}

	/** A wall of hard-light hexes in front of the mech, the size of its body, shimmering. */
	private static void shield(VertexConsumer vc, PoseStack pose, float yaw, float age) {
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.translate(0f, 3.2f, 2.7f);
		float w = 2.9f, h = 3.3f;
		// the frame
		HardLightDraw.part(vc, pose, 0f, h, 0f, w, 0.09f, 0.09f, 0.9f);
		HardLightDraw.part(vc, pose, 0f, -h, 0f, w, 0.09f, 0.09f, 0.9f);
		HardLightDraw.part(vc, pose, -w, 0f, 0f, 0.09f, h, 0.09f, 0.9f);
		HardLightDraw.part(vc, pose, w, 0f, 0f, 0.09f, h, 0.09f, 0.9f);
		// the cells: a staggered honeycomb of thin panes, each breathing on its own
		int cols = 6, rows = 7;
		float cw = w * 2f / cols, ch = h * 2f / rows;
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				float x = -w + cw * (c + 0.5f) + (r % 2 == 0 ? 0f : cw * 0.25f) - cw * 0.125f;
				float y = -h + ch * (r + 0.5f);
				float k = 0.55f + 0.45f * Mth.sin(age * 0.25f + c * 1.3f + r * 0.9f);
				pose.pushPose();
				pose.translate(x, y, 0f);
				TurboDraw.box(vc, pose.last(), cw * 0.40f, ch * 0.38f, 0.03f, HardLightDraw.tint(HardLightDraw.GREEN), 0.16f + 0.14f * k);
				TurboDraw.box(vc, pose.last(), cw * 0.46f, ch * 0.44f, 0.035f, HardLightDraw.tint(HardLightDraw.PALE), 0.08f + 0.1f * k);
				pose.popPose();
			}
		}
		HardLightDraw.emblem(vc, pose, 2.2f, 0.55f + 0.2f * Mth.sin(age * 0.3f));
		pose.popPose();
	}

	/** The Ground Slam's shockwave: a ring of hard light racing out along the ground and fading. */
	private static void slamRing(VertexConsumer vc, PoseStack pose, float yaw, float since) {
		float k = Math.min(1f, since / GreenLanternMechConfig.SLAM_WAVE_TICKS);
		float r = (float) (1.2 + (GreenLanternMechConfig.SLAM_RADIUS - 1.2) * (1f - (1f - k) * (1f - k)));
		float alpha = since < GreenLanternMechConfig.SLAM_WAVE_TICKS ? 0.95f : Math.max(0f, 0.95f * (1f - (since - GreenLanternMechConfig.SLAM_WAVE_TICKS) / 6f));
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.translate(0f, 0.15f, 1.6f);
		int n = 40;
		float seg = (float) (Math.PI * 2 * r / n) * 0.55f;
		for (int i = 0; i < n; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotation((float) (i * Math.PI * 2 / n)));
			pose.translate(0f, 0f, r);
			float hgt = 0.35f + 0.5f * (1f - k);
			HardLightDraw.part(vc, pose, 0f, hgt * 0.5f, 0f, seg, hgt * 0.5f, 0.07f, alpha);
			TurboDraw.box(vc, pose.last(), seg, 0.03f, 0.5f * (1f - k) + 0.12f, HardLightDraw.tint(HardLightDraw.PALE), 0.35f * alpha);
			pose.popPose();
		}
		pose.popPose();
	}

	/** The edge lines, the lantern, the visor and the thrusters, additive on top (a real glow over the translucent body). */
	private static class GlowLayer extends GeoRenderLayer<HardLightMechEntity> {
		GlowLayer(GeoRenderer<HardLightMechEntity> renderer) {
			super(renderer);
		}

		@Override
		public void render(PoseStack poseStack, HardLightMechEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
				MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
			float a = solidity(animatable, partialTick);
			float flash = 1f;
			if (animatable.state() == HardLightMechEntity.STATE_ASSEMBLING) {
				// plates arriving: the glow flickers bright as each one snaps in
				flash = 0.7f + 0.3f * Mth.abs(Mth.sin((animatable.stateTicks() + partialTick) * 0.9f));
			}
			float pulse = 0.85f + 0.15f * Mth.sin((animatable.tickCount + partialTick) * 0.12f);
			float k = Mth.clamp(a * flash * pulse, 0f, 1f);
			int c = animatable.golden()
					? Color.ofRGBA(1.0f * k, 0.9f * k, 0.35f * k, 1f).argbInt()
					: Color.ofRGBA(k, k, k, 1f).argbInt();
			RenderType type = RenderType.eyes(HardLightMechModel.GLOW);
			getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type), partialTick,
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, c);
		}
	}
}
