package com.projecthero.mod.client.spider;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.spider.entity.SpiderTripMineEntity;

import net.minecraft.client.model.geom.ModelLayerLocation;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: the Suit v2's Trip Mine -- a squat octagonal gun-metal disc with a red spider dome, a white lens and a red
 * light that blinks once a second (twice as fast while it reels something in), stuck flat on its block face. From the
 * lens a thin white web laser runs straight out to its synced length; while it reels a catch in, the line runs to the
 * catch instead.
 */
public class SpiderTripMineRenderer extends EntityRenderer<SpiderTripMineEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectHeroMod.id("spider_trip_mine"), "main");
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/spider_trip_mine.png");
	private static final ResourceLocation LINE = ProjectHeroMod.id("textures/effect/web_line.png");

	private final ModelPart body;
	private final ModelPart light;

	public SpiderTripMineRenderer(EntityRendererProvider.Context context) {
		super(context);
		ModelPart root = context.bakeLayer(LAYER);
		this.body = root.getChild("body");
		this.light = root.getChild("light");
		this.shadowRadius = 0f;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-3f, 0f, -3f, 6f, 1f, 6f)
				.texOffs(0, 8).addBox(-3.5f, 0f, -2f, 7f, 1f, 4f)
				.texOffs(0, 14).addBox(-2f, 0f, -3.5f, 4f, 1f, 7f)
				.texOffs(0, 23).addBox(-2f, 1f, -2f, 4f, 1f, 4f)
				.texOffs(16, 23).addBox(-1f, 1.5f, -1f, 2f, 1f, 2f), PartPose.ZERO);
		root.addOrReplaceChild("light", CubeListBuilder.create().texOffs(24, 0).addBox(2.3f, 0.6f, -0.5f, 1f, 1f, 1f), PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void render(SpiderTripMineEntity mine, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int packedLight) {
		Vec3 base = mine.position();
		Vec3 anchor = mine.anchor();
		float age = mine.tickCount + partial;
		boolean reeling = mine.reelTarget() >= 0;
		pose.pushPose();
		pose.translate(anchor.x - base.x, anchor.y - base.y, anchor.z - base.z);
		pose.mulPose(mine.face().getRotation());
		body.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
		boolean on = ((int) (age / (reeling ? 5f : 10f))) % 2 == 0;
		light.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), on ? LightTexture.FULL_BRIGHT : packedLight,
				OverlayTexture.NO_OVERLAY, on ? 0xFFFFFFFF : 0xFF502020);
		pose.popPose();

		// the laser (or, mid-catch, the line to what it caught)
		Vec3 from = mine.beamStart();
		Vec3 to = mine.beamEnd();
		if (reeling) {
			Entity caught = mine.level().getEntity(mine.reelTarget());
			if (caught != null) {
				to = caught.getPosition(partial).add(0, caught.getBbHeight() * 0.5, 0);
			}
		}
		if (from.distanceToSqr(to) > 1.0e-4) {
			float pulse = 0.75f + 0.25f * Mth.sin(age * 0.35f);
			beam(pose, buffers.getBuffer(RenderType.entityTranslucent(LINE)), from.subtract(base), to.subtract(base),
					reeling ? 0.03f : 0.018f, (int) (255 * (reeling ? 1f : 0.7f + 0.25f * pulse)));
		}
		super.render(mine, yaw, partial, pose, buffers, packedLight);
	}

	/** A thin glowing line: two crossed ribbons from {@code a} to {@code b} (entity-relative). */
	private static void beam(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float halfWidth, int alpha) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		Vec3 dir = d.scale(1.0 / len);
		Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 s1 = dir.cross(up).normalize().scale(halfWidth);
		Vec3 s2 = dir.cross(s1).normalize().scale(halfWidth);
		Matrix4f m = pose.last().pose();
		float v1 = (float) (len / 0.45);
		int col = (alpha << 24) | 0xF4F8FF;
		for (Vec3 s : new Vec3[] { s1, s2 }) {
			Vector3f n = new Vector3f((float) s.x, (float) s.y, (float) s.z).normalize();
			vert(vc, m, a.add(s), 0f, 0f, col, n);
			vert(vc, m, b.add(s), 0f, v1, col, n);
			vert(vc, m, b.subtract(s), 1f, v1, col, n);
			vert(vc, m, a.subtract(s), 1f, 0f, col, n);
		}
	}

	private static void vert(VertexConsumer vc, Matrix4f m, Vec3 p, float u, float v, int col, Vector3f n) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(col).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(n.x, n.y, n.z);
	}

	@Override
	public ResourceLocation getTextureLocation(SpiderTripMineEntity mine) {
		return TEXTURE;
	}
}
