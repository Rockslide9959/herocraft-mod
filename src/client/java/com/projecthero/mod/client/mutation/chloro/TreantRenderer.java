package com.projecthero.mod.client.mutation.chloro;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.p22.TreantEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * v0.17.1 Chlorokinesis, Shift+Z: the Treant -- the player model in a generated bark-and-moss skin, scaled up 1.35x,
 * a crown of leaf cards bursting out of its head and glowing sap-green eyes. It grows up out of the ground when it is
 * summoned and sinks back when its time is up.
 */
public class TreantRenderer extends HumanoidMobRenderer<TreantEntity, PlayerModel<TreantEntity>> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/mutation/p22_treant.png");
	private static final ResourceLocation EYES = ProjectHeroMod.id("textures/entity/mutation/p22_treant_eyes.png");
	private static final RenderType EYES_TYPE = RenderType.eyes(EYES);
	public static final float SCALE = 1.35f;

	public TreantRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.55f);
		this.model.hat.visible = false;
		addLayer(new CrownLayer(this));
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return EYES_TYPE;
			}
		});
	}

	@Override
	protected void scale(TreantEntity e, PoseStack pose, float partial) {
		pose.scale(SCALE, SCALE, SCALE);
	}

	@Override
	public void render(TreantEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		// grows up out of the ground over its first 15 ticks, sinks back over its last 15
		float life = e.lifeTicks() + partial;
		float rise = ChloroMesh.smooth(life / 15f) * (1 - ChloroMesh.smooth((life - (TreantEntity.LIFETIME - 15)) / 15f));
		pose.pushPose();
		pose.translate(0, -(1 - rise) * e.getBbHeight(), 0);
		super.render(e, yaw, partial, pose, buffers, light);
		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(TreantEntity entity) {
		return TEXTURE;
	}

	/** A bushy crown of leaves sprouting out of the head, and a few twigs of leaves on the shoulders. */
	static final class CrownLayer extends RenderLayer<TreantEntity, PlayerModel<TreantEntity>> {
		CrownLayer(RenderLayerParent<TreantEntity, PlayerModel<TreantEntity>> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, TreantEntity e, float limbSwing, float limbSwingAmount,
				float partial, float ageInTicks, float netHeadYaw, float headPitch) {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.LEAF));
			RandomSource r = RandomSource.create(99173L);
			pose.pushPose();
			getParentModel().head.translateAndRotate(pose);
			PoseStack.Pose last = pose.last();
			// model space: y points down, the head's top is at y = -0.5
			float sway = Mth.sin(ageInTicks * 0.08f) * 0.05f;
			for (int ring = 0; ring < 3; ring++) {
				int n = 7 + ring * 2;
				float lift = 0.15f + ring * 0.12f;
				for (int i = 0; i < n; i++) {
					float a = Mth.TWO_PI * i / n + ring * 0.4f + r.nextFloat() * 0.3f;
					Vector3f out = new Vector3f(Mth.cos(a), 0, Mth.sin(a));
					Vector3f base = new Vector3f(out).mul(0.18f + ring * 0.04f).add(0, -0.5f - ring * 0.05f, 0);
					Vector3f along = new Vector3f(out).mul(0.9f - ring * 0.25f).add(sway, -(lift + 0.5f + ring * 0.35f), 0).normalize();
					Vector3f side = new Vector3f(-out.z, 0, out.x);
					float len = 0.42f + r.nextFloat() * 0.18f - ring * 0.05f;
					int shade = 210 + r.nextInt(45);
					ChloroMesh.leaf(vc, last, base, along, side, len, len * 0.8f, shade, 255, shade, 255, light);
				}
			}
			pose.popPose();
			// twigs on the shoulders
			for (int s = 0; s < 2; s++) {
				pose.pushPose();
				(s == 0 ? getParentModel().rightArm : getParentModel().leftArm).translateAndRotate(pose);
				PoseStack.Pose arm = pose.last();
				float sx = s == 0 ? -1 : 1;
				for (int i = 0; i < 3; i++) {
					Vector3f base = new Vector3f(sx * 0.12f, -0.1f + i * 0.08f, 0.05f * (i - 1));
					Vector3f along = new Vector3f(sx * 0.6f, -1.0f, (i - 1) * 0.4f).normalize();
					ChloroMesh.leaf(vc, arm, base, along, new Vector3f(0, 0, 1), 0.3f, 0.24f, 255, 255, 255, 255, light);
				}
				pose.popPose();
			}
		}
	}
}
