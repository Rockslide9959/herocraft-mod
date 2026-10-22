package com.projecthero.mod.client.ironman;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.HulkbusterParts;
import com.projecthero.mod.ironman.entity.HulkbusterPodEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.16.7 (user) Hulkbuster "Veronica" overhaul: the Hulkbuster's support pod -- the Mark VII delivery pod's model and
 * clips ({@code fly} / {@code open} / {@code close}) at 1.6x, placed exactly on its synced clock
 * ({@link HulkbusterPodEntity#podAt}) so the fly-in, the hover bob and the climb away are smooth on every client.
 *
 * <p>REPAIR: each replacement plate (a red / gold Hulkbuster plate cut from the body texture, 0.6 x 0.45 blocks) leaves
 * the open hatch, tumbles along an arc to its part and lies flush on it while it is welded (shrinking into the armour as
 * the weld finishes). BUILD in first person: the arrived and flying pieces of the body round the camera, minus the
 * head and torso (they would fill the view); in third person / for other players the body is drawn with the player
 * ({@code PlayerRendererHulkbusterMixin}).
 */
public class HulkbusterPodRenderer extends GeoEntityRenderer<HulkbusterPodEntity> {
	public static final float SCALE = 1.6f;
	private static final ResourceLocation PLATE_TEX = IronManHulkbusterModel.TEXTURE;

	public HulkbusterPodRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = 0.8f;
		withScale(SCALE);
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	@Override
	protected void applyRotations(HulkbusterPodEntity pod, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick,
			float nativeScale) {
		super.applyRotations(pod, pose, ageInTicks, Mth.rotLerp(partialTick, pod.yRotO, pod.getYRot()), partialTick, nativeScale);
	}

	@Override
	public void render(HulkbusterPodEntity pod, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
			int packedLight) {
		float age = pod.age(partialTick);
		Vec3 lerped = pod.getPosition(partialTick);
		Vec3 exact = pod.podAt(age);
		poseStack.pushPose();
		// on the clock, not the network position (smooth even when packets are late)
		poseStack.translate(exact.x - lerped.x, exact.y - lerped.y, exact.z - lerped.z);
		super.render(pod, entityYaw, partialTick, poseStack, buffer, LightTexture.FULL_BRIGHT);
		poseStack.popPose();
		if (pod.mode() == HulkbusterPodEntity.MODE_REPAIR) {
			renderPlates(pod, age, lerped, poseStack, buffer, packedLight, partialTick);
		} else {
			renderFirstPersonBuild(pod, lerped, partialTick, poseStack, buffer, packedLight);
		}
	}

	// ======================================================================== REPAIR plates

	private void renderPlates(HulkbusterPodEntity pod, float age, Vec3 origin, PoseStack poseStack, MultiBufferSource buffer, int light,
			float partial) {
		if (pod.done()) {
			return;
		}
		Entity target = pod.level().getEntity(pod.targetId());
		if (target == null) {
			return;
		}
		Vec3 hatch = pod.podAt(age).add(0, 1.2, 0);
		VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(PLATE_TEX));
		Minecraft mc = Minecraft.getInstance();
		boolean selfFp = target == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
		for (int j = 0; j < pod.plates(); j++) {
			float launch = pod.plateLaunch(j);
			float land = launch + HulkbusterPodEntity.PLATE_FLIGHT;
			float weld = pod.weld(j, age);
			if (age < launch || weld >= 1f) {
				continue;
			}
			int part = pod.plateTarget(j);
			if (selfFp && part <= HulkbusterParts.TORSO && age >= land) {
				continue; // don't paste a plate over your own eyes
			}
			Vec3 dest = HulkbusterVeronicaClient.plateSpot(target, part, partial);
			Vec3 out = HulkbusterVeronicaClient.outward(target, part, partial);
			Vec3 at;
			float tumble;
			float scale = 1f;
			if (age < land) {
				float u = (age - launch) / HulkbusterPodEntity.PLATE_FLIGHT;
				float e = u * u * (3f - 2f * u);
				Vec3 mid = hatch.add(dest).scale(0.5).add(0, 1.8, 0);
				// quadratic bezier hatch -> mid -> part
				at = hatch.scale((1 - e) * (1 - e)).add(mid.scale(2 * (1 - e) * e)).add(dest.scale(e * e));
				tumble = (1f - e) * 540f + j * 47f;
			} else {
				at = dest;
				tumble = 0f;
				scale = 1f - 0.85f * weld * weld; // welded in: it melts into the armour
			}
			poseStack.pushPose();
			poseStack.translate(at.x - origin.x, at.y - origin.y, at.z - origin.z);
			// lying flat on the part's outside (facing out of it), tumbling while in flight
			float yaw = (float) Math.toDegrees(Math.atan2(-out.x, out.z));
			poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
			poseStack.mulPose(Axis.XP.rotationDegrees(tumble));
			poseStack.mulPose(Axis.ZP.rotationDegrees(tumble * 0.6f));
			poseStack.scale(scale, scale, scale);
			plate(poseStack, vc, light, j);
			poseStack.popPose();
		}
	}

	/** A 0.6 x 0.45 x 0.08 plate, the chest plating of the Hulkbuster skin on its faces and gold on its edges. */
	private static void plate(PoseStack poseStack, VertexConsumer vc, int light, int j) {
		float hx = 0.36f, hy = 0.28f, hz = 0.05f;
		// skin UVs (64x64): the body front (20..28, 20..32) for the faces, a gold strip of the arms for the edges
		float u0 = 20f / 64f, u1 = 28f / 64f, v0 = (20f + (j % 3)) / 64f, v1 = (26f + (j % 3)) / 64f;
		float eu0 = 44f / 64f, eu1 = 47f / 64f, ev0 = 20f / 64f, ev1 = 21f / 64f;
		Matrix4f m = poseStack.last().pose();
		PoseStack.Pose pose = poseStack.last();
		quad(vc, pose, m, light, -hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz, u0, v1, u1, v1, u1, v0, u0, v0, 0, 0, 1);
		quad(vc, pose, m, light, hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz, u0, v1, u1, v1, u1, v0, u0, v0, 0, 0, -1);
		quad(vc, pose, m, light, -hx, hy, hz, hx, hy, hz, hx, hy, -hz, -hx, hy, -hz, eu0, ev0, eu1, ev0, eu1, ev1, eu0, ev1, 0, 1, 0);
		quad(vc, pose, m, light, -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz, eu0, ev0, eu1, ev0, eu1, ev1, eu0, ev1, 0, -1, 0);
		quad(vc, pose, m, light, hx, -hy, hz, hx, -hy, -hz, hx, hy, -hz, hx, hy, hz, eu0, ev0, eu1, ev0, eu1, ev1, eu0, ev1, 1, 0, 0);
		quad(vc, pose, m, light, -hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz, eu0, ev0, eu1, ev0, eu1, ev1, eu0, ev1, -1, 0, 0);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, int light, float x0, float y0, float z0, float x1, float y1,
			float z1, float x2, float y2, float z2, float x3, float y3, float z3, float ua, float va, float ub, float vb, float uc, float vc2,
			float ud, float vd, float nx, float ny, float nz) {
		vertex(vc, pose, m, light, x0, y0, z0, ua, va, nx, ny, nz);
		vertex(vc, pose, m, light, x1, y1, z1, ub, vb, nx, ny, nz);
		vertex(vc, pose, m, light, x2, y2, z2, uc, vc2, nx, ny, nz);
		vertex(vc, pose, m, light, x3, y3, z3, ud, vd, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, int light, float x, float y, float z, float u, float v,
			float nx, float ny, float nz) {
		vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	// ======================================================================== BUILD, first person

	private void renderFirstPersonBuild(HulkbusterPodEntity pod, Vec3 origin, float partial, PoseStack poseStack, MultiBufferSource buffer,
			int light) {
		Minecraft mc = Minecraft.getInstance();
		Entity e = pod.level().getEntity(pod.targetId());
		if (!(e instanceof AbstractClientPlayer p) || p != mc.getCameraEntity() || !mc.options.getCameraType().isFirstPerson()) {
			return; // third person / other players: drawn with the player (PlayerRendererHulkbusterMixin)
		}
		IronManHulkbusterRenderer r = IronManHulkbusterRenderer.get();
		if (r == null || HulkbusterParts.wearing(p) || HulkbusterVeronicaClient.buildPose(p, partial) == null) {
			return;
		}
		Vec3 at = p.getPosition(partial);
		poseStack.pushPose();
		poseStack.translate(at.x - origin.x, at.y - origin.y, at.z - origin.z);
		IronManHulkbusterRenderer.hideNext = (1 << HulkbusterParts.HEAD) | (1 << HulkbusterParts.TORSO);
		int bodyLight = net.minecraft.client.renderer.LevelRenderer.getLightColor(p.level(), p.blockPosition());
		r.renderBody(p, pod.buildYaw(), partial, poseStack, buffer, bodyLight);
		poseStack.popPose();
	}

	static final class Model extends GeoModel<HulkbusterPodEntity> {
		private static final ResourceLocation GEO = ProjectHeroMod.id("geo/iron_man_delivery_pod.geo.json");
		private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/iron_man_delivery_pod.png");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/iron_man_delivery_pod.animation.json");

		@Override
		public ResourceLocation getModelResource(HulkbusterPodEntity pod) {
			return GEO;
		}

		@Override
		public ResourceLocation getTextureResource(HulkbusterPodEntity pod) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(HulkbusterPodEntity pod) {
			return ANIMATION;
		}
	}
}
