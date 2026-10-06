package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.ironman.RepulsorBoots;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.9 Repulsor Boots flight (a bare Repulsor worn in the boots slot), both halves keyed off the synced
 * {@code REPULSOR_BOOTS_FLYING} flag so they hold on the server and on every client:
 * <ul>
 *   <li><b>No sprint flying</b> (explicit user request): any {@code setSprinting(true)} while boots-flying becomes
 *   {@code false} -- the local player's sprint key, the server's sprint packet, anything else. No sprint flag means no
 *   sprint speed modifier and no sprint FOV kick; the boots' own cruise speed is untouched.</li>
 *   <li><b>Body follows the look</b>: vanilla turns a moving entity's body toward its direction of travel, which made
 *   a strafing flier fly crabwise with the body twisted 45 degrees off the head. While boots-flying the body eases
 *   toward the head yaw instead (same 0.3/tick rate vanilla uses), so strafing reads as a clean sideways bank
 *   ({@link com.projecthero.mod.ironman.RepulsorFlightLook}).</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRepulsorFlightMixin {
	@ModifyVariable(method = "setSprinting", at = @At("HEAD"), argsOnly = true)
	private boolean projecthero$noRepulsorSprint(boolean sprinting) {
		if (sprinting && (Object) this instanceof Player player && RepulsorBoots.blocksSprint(player)) {
			return false;
		}
		return sprinting;
	}

	@ModifyVariable(method = "tickHeadTurn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float projecthero$repulsorBodyFollowsLook(float travelYaw) {
		if ((Object) this instanceof Player player && RepulsorBoots.blocksSprint(player)) {
			return player.getYRot();
		}
		return travelYaw;
	}
}
