package com.projecthero.mod.client.ghostrider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ghostrider.entity.HellCycleEntity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * v0.16.5: the Hell Cycle -- a chopper built from cubes in code (texture {@code hell_cycle.png}, 128x128 colour regions:
 * tyre, matte frame, chrome, a flame-licked tank, leather seat, a hot headlight, hub, engine fins), with both wheels
 * spinning from the ground speed and <b>on fire</b>: a ring of the vanilla animated fire sprite round each rim, plus a
 * burning headlight. Forward is -Z, like every vanilla mob model.
 */
public class HellCycleRenderer extends EntityRenderer<HellCycleEntity> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/ghost_rider/hell_cycle.png");
	private static final float WHEEL_Y = 19f;
	private static final float FRONT_Z = -12f;
	private static final float REAR_Z = 10f;
	private static final float WHEEL_R = 5f;

	private final ModelPart root;
	private final ModelPart frontWheel;
	private final ModelPart rearWheel;
	private final ModelPart light;

	public HellCycleRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.root = createLayer().bakeRoot();
		this.frontWheel = root.getChild("front_wheel");
		this.rearWheel = root.getChild("rear_wheel");
		this.light = root.getChild("headlight");
		this.shadowRadius = 0.6f;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		// wheels: two squares, one turned 45 degrees -> an octagon; the painted rim is round on both
		for (String name : new String[] { "front_wheel", "rear_wheel" }) {
			float z = name.startsWith("front") ? FRONT_Z : REAR_Z;
			PartDefinition w = r.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -5f, -5f, 3f, 10f, 10f),
					PartPose.offset(0f, WHEEL_Y, z));
			w.addOrReplaceChild("rim45", CubeListBuilder.create().texOffs(0, 0).addBox(-1.4f, -5f, -5f, 2.8f, 10f, 10f),
					PartPose.rotation((float) Math.PI / 4f, 0f, 0f));
			w.addOrReplaceChild("hub", CubeListBuilder.create().texOffs(0, 96).addBox(-2f, -1.5f, -1.5f, 4f, 3f, 3f), PartPose.ZERO);
		}
		// the frame / backbone, engine, tank, seat, rear fender
		r.addOrReplaceChild("frame", CubeListBuilder.create().texOffs(64, 0).addBox(-1.5f, 12f, -9f, 3f, 3f, 17f), PartPose.ZERO);
		r.addOrReplaceChild("engine", CubeListBuilder.create().texOffs(64, 96).addBox(-3f, 14f, -5f, 6f, 5f, 7f), PartPose.ZERO);
		r.addOrReplaceChild("tank", CubeListBuilder.create().texOffs(64, 32).addBox(-2.5f, 9.5f, -8.5f, 5f, 4f, 7f), PartPose.ZERO);
		r.addOrReplaceChild("seat", CubeListBuilder.create().texOffs(0, 64).addBox(-2.5f, 10.5f, -1.5f, 5f, 2f, 8f), PartPose.ZERO);
		r.addOrReplaceChild("fender", CubeListBuilder.create().texOffs(64, 0).addBox(-2f, 12.5f, 6f, 4f, 2f, 7f), PartPose.ZERO);
		r.addOrReplaceChild("skull_plate", CubeListBuilder.create().texOffs(0, 32).addBox(-2f, 12f, 12.5f, 4f, 3f, 1f), PartPose.ZERO);
		// raked front forks + handlebars
		PartDefinition fork = r.addOrReplaceChild("fork", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-2.5f, 0f, -0.5f, 1f, 11f, 1f).addBox(1.5f, 0f, -0.5f, 1f, 11f, 1f),
				PartPose.offsetAndRotation(0f, 8.5f, -9.5f, 0.32f, 0f, 0f));
		fork.addOrReplaceChild("bars", CubeListBuilder.create().texOffs(0, 32).addBox(-6f, -1f, -0.5f, 12f, 1f, 1f)
				.texOffs(0, 64).addBox(-7f, -1.5f, -1f, 2f, 2f, 2f).addBox(5f, -1.5f, -1f, 2f, 2f, 2f), PartPose.offsetAndRotation(0f, 0f, 0f, -0.32f, 0f, 0f));
		r.addOrReplaceChild("headlight", CubeListBuilder.create().texOffs(64, 64).addBox(-1.5f, -1.5f, -1f, 3f, 3f, 2f),
				PartPose.offset(0f, 10.5f, -12f));
		// twin exhausts sweeping back
		r.addOrReplaceChild("exhaust_l", CubeListBuilder.create().texOffs(0, 32).addBox(2.5f, 16.5f, -2f, 1.5f, 1.5f, 13f),
				PartPose.rotation(-0.08f, 0f, 0f));
		r.addOrReplaceChild("exhaust_r", CubeListBuilder.create().texOffs(0, 32).addBox(-4f, 16.5f, -2f, 1.5f, 1.5f, 13f),
				PartPose.rotation(-0.08f, 0f, 0f));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void render(HellCycleEntity bike, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		float bodyYaw = Mth.rotLerp(partial, bike.yRotO, bike.getYRot());
		pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
		pose.scale(-1f, -1f, 1f);
		pose.translate(0f, -1.501f, 0f);
		float spin = Mth.lerp(partial, bike.wheelSpinO, bike.wheelSpin);
		frontWheel.xRot = spin;
		rearWheel.xRot = spin;
		this.light.visible = true;
		root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
		// the headlight blazes full-bright
		this.light.render(pose, buffers.getBuffer(RenderType.eyes(TEXTURE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		float age = bike.tickCount + partial;
		if (bike.burning()) { // v0.16.5: a human rider's bike doesn't burn
			renderWheelFire(pose, buffers, FRONT_Z, age, 0f);
			renderWheelFire(pose, buffers, REAR_Z, age, 1.3f);
		}
		pose.popPose();
		super.render(bike, yaw, partial, pose, buffers, light);
	}

	/** A ring of fire tongues round one wheel, in the wheel's plane, flaring outward and licking upward. */
	private static void renderWheelFire(PoseStack pose, MultiBufferSource buffers, float wheelZ, float age, float phase) {
		TextureAtlasSprite f0 = ModelBakery.FIRE_0.sprite();
		TextureAtlasSprite f1 = ModelBakery.FIRE_1.sprite();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
		Matrix4f m = pose.last().pose();
		PoseStack.Pose last = pose.last();
		float cy = WHEEL_Y / 16f;
		float cz = wheelZ / 16f;
		float rr = (WHEEL_R - 0.6f) / 16f;
		int n = 10;
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + age * 0.08;
			float dy = (float) Math.cos(a);
			float dz = (float) Math.sin(a);
			// in model space -y is up: tongues on the upper half reach further (fire rises)
			float up = Math.max(0f, -dy);
			float len = (0.22f + 0.22f * up) * (0.8f + 0.2f * Mth.sin(age * 0.6f + i * 1.9f + phase));
			float bx = 0f;
			float by = cy + dy * rr;
			float bz = cz + dz * rr;
			// tip: outward along the radius, bent upward
			float tx = by + dy * len - len * 0.6f;
			float tz = bz + dz * len;
			float hw = 0.07f;
			// tangent in the wheel plane
			float ty = -dz * hw;
			float tzz = dy * hw;
			for (float side : new float[] { -0.12f, 0.12f }) {
				bx = side;
				quad(vc, m, last, (i & 1) == 0 ? f0 : f1,
						bx, by - ty, bz - tzz,
						bx, by + ty, bz + tzz,
						bx, tx + ty, tz + tzz,
						bx, tx - ty, tz - tzz);
			}
		}
	}

	private static void quad(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, TextureAtlasSprite sp, float x0, float y0, float z0, float x1,
			float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
		float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
		vertex(vc, m, last, x0, y0, z0, u0, v1);
		vertex(vc, m, last, x1, y1, z1, u1, v1);
		vertex(vc, m, last, x2, y2, z2, u1, v0);
		vertex(vc, m, last, x3, y3, z3, u0, v0);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, float x, float y, float z, float u, float v) {
		vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0f, -1f, 0f); // up in model space: lit like the skull fire
	}

	@Override
	public ResourceLocation getTextureLocation(HellCycleEntity entity) {
		return TEXTURE;
	}
}
