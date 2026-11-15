package com.projecthero.mod.client.hideout;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hideout.boss.HiveMotherBoss;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.5: the Hive Mother -- Carnage's wide-arm player model (the same body his renderer uses) in the black
 * {@code hideout/hive_mother} skin, posed from her synced action: arms thrust forward for the Tendril Lash, flung wide
 * for the Spike Volley, hunched with arms raised while the Brood crawls out.
 */
public class HiveMotherRenderer extends HumanoidMobRenderer<HiveMotherBoss, HiveMotherRenderer.Model> {
	static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/hideout/hive_mother.png");

	public HiveMotherRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.9f);
	}

	@Override
	public ResourceLocation getTextureLocation(HiveMotherBoss entity) {
		return SKIN;
	}

	@Override
	protected void scale(HiveMotherBoss entity, PoseStack pose, float partialTick) {
		super.scale(entity, pose, partialTick);
		pose.scale(0.9375f, 0.9375f, 0.9375f);
	}

	static final class Model extends PlayerModel<HiveMotherBoss> {
		Model(ModelPart root) {
			super(root, false);
		}

		@Override
		public void setupAnim(HiveMotherBoss e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
			byte action = e.action();
			this.crouching = action == HiveMotherBoss.ACTION_BROOD;
			super.setupAnim(e, limbSwing, limbSwingAmount, age, headYaw, headPitch);
			float t = Mth.clamp((age - e.actionStartTick) / 8f, 0f, 1f);
			switch (action) {
				case HiveMotherBoss.ACTION_LASH -> {
					rightArm.xRot = Mth.lerp(t, rightArm.xRot, -1.6f);
					leftArm.xRot = Mth.lerp(t, leftArm.xRot, -1.6f);
					rightArm.zRot = 0.15f;
					leftArm.zRot = -0.15f;
				}
				case HiveMotherBoss.ACTION_VOLLEY -> {
					rightArm.zRot = Mth.lerp(t, 0f, 1.3f);
					leftArm.zRot = Mth.lerp(t, 0f, -1.3f);
					rightArm.xRot = -0.3f;
					leftArm.xRot = -0.3f;
				}
				case HiveMotherBoss.ACTION_BROOD -> {
					rightArm.xRot = Mth.lerp(t, rightArm.xRot, -2.6f);
					leftArm.xRot = Mth.lerp(t, leftArm.xRot, -2.6f);
					rightArm.zRot = 0.4f;
					leftArm.zRot = -0.4f;
					head.xRot = -0.5f;
				}
				default -> {
					// claws out a little all the time
					rightArm.zRot += 0.12f;
					leftArm.zRot -= 0.12f;
				}
			}
			rightSleeve.copyFrom(rightArm);
			leftSleeve.copyFrom(leftArm);
			hat.copyFrom(head);
		}
	}
}
