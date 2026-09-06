package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.punisher.Punisher;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * The Punisher never spends arrows on a bow or crossbow -- an inherent "infinity" for this player
 * only (spec / user request). This is NOT the Infinity enchantment on the item: the item is
 * untouched, so nobody the Punisher hands the bow to gets the effect.
 *
 * <p>{@code useAmmo} is the single point where both {@link net.minecraft.world.item.BowItem} and
 * {@link net.minecraft.world.item.CrossbowItem} consume a projectile (via
 * {@code ProjectileWeaponItem.draw}). For a Punisher shooter we short-circuit it to exactly what
 * vanilla does for an infinite-materials / Infinity shot: hand back a single un-consumed copy of the
 * ammo flagged {@code INTANGIBLE_PROJECTILE} so the fired arrow cannot be picked up either.
 */
@Mixin(ProjectileWeaponItem.class)
public abstract class ProjectileWeaponItemMixin {
	@Inject(method = "useAmmo", at = @At("HEAD"), cancellable = true)
	private static void projecthero$punisherInfiniteAmmo(ItemStack weapon, ItemStack ammo, LivingEntity shooter,
			boolean intangeableProjectile, CallbackInfoReturnable<ItemStack> cir) {
		if (ammo.isEmpty() || !(shooter instanceof Player player) || !Punisher.hasPower(player)) {
			return;
		}
		ItemStack single = ammo.copyWithCount(1);
		single.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
		cir.setReturnValue(single);
	}
}
