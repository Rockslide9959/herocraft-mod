package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.beastboy.BeastBoy;
import com.projecthero.mod.beastboy.BeastBoyForms;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.4 Beast Boy sea forms (user: "make the sea creatures easier to control"): in the water the body eases toward where he looks
 * (vanilla's 0.3/tick body turn), instead of toward the direction of travel and only catching up with the head past 50-75 degrees.
 * That snap-to-catch-up was half of the swimming "shake": a sea animal is drawn along the body yaw, so it jerked round whenever the
 * look got far enough ahead. Runs on every side (server, own client, other clients), so the whale's rider seats, which hang off the
 * body yaw, stay on its back. Same hook as the Repulsor Boots' body-follows-look.
 */
@Mixin(LivingEntity.class)
public abstract class BeastBoySwimTurnMixin {
	@ModifyVariable(method = "tickHeadTurn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float projecthero$seaBodyFollowsLook(float travelYaw) {
		if ((Object) this instanceof Player player && player.isInWater() && BeastBoyForms.aquatic(BeastBoy.form(player))) {
			return player.getYHeadRot();
		}
		return travelYaw;
	}
}
