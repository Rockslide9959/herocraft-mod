package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.moonknight.ability.MoonKnightAlters;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.4: an invisible Jake Lockley cannot be targeted. Vanish used to drop aggro once and cut detection range, but any
 * mob he then struck (or one that wandered close enough) targeted him straight back. Refusing him at the one choke point
 * every target goal, retaliation and command goes through closes that; {@code MoonKnightAlters#tick} sweeps the
 * brain-driven mobs that keep their target in a memory instead.
 */
@Mixin(Mob.class)
public abstract class MoonKnightVanishTargetMixin {
	@ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
	private LivingEntity projecthero$cannotTargetAVanishedJake(LivingEntity target) {
		if (target instanceof Player player && !player.level().isClientSide() && MoonKnightAlters.isVanished(player)) {
			return null;
		}
		return target;
	}
}
