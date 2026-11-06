package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;

/** v0.17.2: the Stand copies its user: how they hold what is in each hand (a bow drawn, a shield up, a spyglass). */
@Mixin(PlayerRenderer.class)
public interface StandArmPoseInvoker {
	@Invoker("getArmPose")
	static HumanoidModel.ArmPose projecthero$armPose(AbstractClientPlayer player, InteractionHand hand) {
		throw new AssertionError();
	}
}
