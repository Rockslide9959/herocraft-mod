package com.projecthero.mod.client.hideout;

import java.util.EnumMap;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hideout.mob.HideoutSoldier;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.7: the Crimson Thralls and Oath-bound Knights -- the player model in a detailed skin per kind
 * ({@code textures/entity/hideout/soldier/<kind>.png}). Thralls lurch with their arms out when they hunt (the zombie
 * pose), Arbalists hold their crossbow up, everything else holds its weapon / shield like a player.
 */
public class HideoutSoldierRenderer extends HumanoidMobRenderer<HideoutSoldier, HideoutSoldierRenderer.Model> {
	private static final Map<HideoutSoldier.Kind, ResourceLocation> SKINS = new EnumMap<>(HideoutSoldier.Kind.class);

	static {
		for (HideoutSoldier.Kind k : HideoutSoldier.Kind.values()) {
			SKINS.put(k, ProjectHeroMod.id("textures/entity/hideout/soldier/" + k.skin() + ".png"));
		}
	}

	public HideoutSoldierRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(HideoutSoldier entity) {
		return SKINS.get(entity.kind());
	}

	static final class Model extends PlayerModel<HideoutSoldier> {
		Model(ModelPart root) {
			super(root, false);
		}

		@Override
		public void prepareMobModel(HideoutSoldier e, float limbSwing, float limbSwingAmount, float partialTick) {
			rightArmPose = HumanoidModel.ArmPose.EMPTY;
			leftArmPose = HumanoidModel.ArmPose.EMPTY;
			if (e.kind() == HideoutSoldier.Kind.ARBALIST && e.isAggressive()) {
				rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
			} else if (!e.getMainHandItem().isEmpty()) {
				rightArmPose = HumanoidModel.ArmPose.ITEM;
			}
			if (!e.getOffhandItem().isEmpty()) {
				leftArmPose = HumanoidModel.ArmPose.ITEM;
			}
			super.prepareMobModel(e, limbSwing, limbSwingAmount, partialTick);
		}

		@Override
		public void setupAnim(HideoutSoldier e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
			super.setupAnim(e, limbSwing, limbSwingAmount, age, headYaw, headPitch);
			if (e.kind().crimson() && e.isAggressive() && e.getMainHandItem().isEmpty()) {
				AnimationUtils.animateZombieArms(leftArm, rightArm, true, attackTime, age);
			}
			rightSleeve.copyFrom(rightArm);
			leftSleeve.copyFrom(leftArm);
			hat.copyFrom(head);
			jacket.copyFrom(body);
			rightPants.copyFrom(rightLeg);
			leftPants.copyFrom(leftLeg);
		}
	}
}
