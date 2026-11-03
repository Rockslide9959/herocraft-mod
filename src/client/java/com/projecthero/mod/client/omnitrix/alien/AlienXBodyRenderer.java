package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXBodyEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 (user: "when the player transforms into alien x i want his body to remain in place, make alien x 2.5 blocks tall"):
 * Alien X's body left in the world while Ben is before the council -- the player model in the Alien X skin, 2.5 blocks tall, with
 * his horns. It stands perfectly still like the show's frozen Alien X (arms a little out, palms open, only the faintest breath),
 * and when a motion is carried it lifts a hand toward the effect for a second.
 */
public class AlienXBodyRenderer extends LivingEntityRenderer<AlienXBodyEntity, PlayerModel<AlienXBodyEntity>> {
	public AlienXBodyRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.6f);
		addLayer(new Horns(this));
	}

	@Override
	public ResourceLocation getTextureLocation(AlienXBodyEntity entity) {
		return AlienXClient.SKIN;
	}

	@Override
	protected void scale(AlienXBodyEntity entity, PoseStack pose, float partialTick) {
		float s = 0.9375f * AlienXBodyEntity.SCALE; // the player renderer's own scale, times 2.5 / 1.8
		pose.scale(s, s, s);
	}

	@Override
	protected boolean shouldShowName(AlienXBodyEntity entity) {
		return false;
	}

	private static final class Horns extends RenderLayer<AlienXBodyEntity, PlayerModel<AlienXBodyEntity>> {
		Horns(RenderLayerParent<AlienXBodyEntity, PlayerModel<AlienXBodyEntity>> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, AlienXBodyEntity e, float limbSwing, float limbSwingAmount, float partial,
				float age, float netHeadYaw, float headPitch) {
			AlienXClient.horns(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(AlienXClient.SKIN)), light, getParentModel().head);
		}
	}

	/** The frozen stance, and the raised hand of a carried motion. */
	private static final class Model extends PlayerModel<AlienXBodyEntity> {
		Model(ModelPart root) {
			super(root, false);
		}

		@Override
		public void setupAnim(AlienXBodyEntity e, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
			super.setupAnim(e, 0f, 0f, age, 0f, e.getXRot());
			float breath = 0.015f * Mth.sin(age * 0.05f);
			this.rightArm.xRot = 0.05f;
			this.leftArm.xRot = 0.05f;
			this.rightArm.zRot = 0.18f + breath;
			this.leftArm.zRot = -0.18f - breath;
			this.rightArm.yRot = 0f;
			this.leftArm.yRot = 0f;
			float act = e.tickCount - e.lastAct();
			if (act >= 0 && act < 24) {
				float k = act < 4 ? act / 4f : act > 16 ? (24 - act) / 8f : 1f;
				this.rightArm.xRot = Mth.lerp(k, 0.05f, -1.55f) + e.getXRot() * Mth.DEG_TO_RAD * k;
				this.rightArm.zRot = Mth.lerp(k, 0.18f, 0.0f);
			}
			this.leftSleeve.copyFrom(this.leftArm);
			this.rightSleeve.copyFrom(this.rightArm);
			this.leftPants.copyFrom(this.leftLeg);
			this.rightPants.copyFrom(this.rightLeg);
			this.jacket.copyFrom(this.body);
			this.hat.copyFrom(this.head);
		}
	}
}
