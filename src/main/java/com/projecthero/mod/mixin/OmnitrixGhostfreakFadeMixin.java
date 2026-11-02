package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2 (user: "make sure he also has an invisibility move"): a faded Ghostfreak (or one inside a host) cannot be targeted by
 * a mob -- not by a target goal, not in revenge for a blow, not by a command; bosses still can ({@link GhostfreakKit#hiddenFrom}).
 * The one choke point every targeting goes through, as for Moon Knight's Vanish ({@code MoonKnightVanishTargetMixin}).
 */
@Mixin(Mob.class)
public abstract class OmnitrixGhostfreakFadeMixin {
	@ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
	private LivingEntity projecthero$cannotTargetAFadedGhostfreak(LivingEntity target) {
		if (target instanceof Player player && !player.level().isClientSide() && GhostfreakKit.hiddenFrom((Mob) (Object) this, player)) {
			return null;
		}
		return target;
	}
}
