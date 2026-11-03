package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Upgrade revamp. Both sides (worked out from the synced Omnitrix state so they agree):
 * <ul>
 * <li><b>Liquid Metal Body</b>: an arrow or thrown thing passes straight through him a quarter of the time
 * ({@link UpgradeKit#passesThrough}) -- a "deflection" that leaves it flying on as it was, so vanilla never hits him with
 * it.</li>
 * <li><b>Ally Upgrade</b>: merged onto a squad mate as living armour, he rides on their upper back ({@link UpgradeKit#allySeat}),
 * clear of their aim.</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class OmnitrixUpgradeEntityMixin {
	@Inject(method = "deflection", at = @At("HEAD"), cancellable = true)
	private void projecthero$upgradePassThrough(Projectile projectile, CallbackInfoReturnable<ProjectileDeflection> cir) {
		if (UpgradeKit.passesThrough((Entity) (Object) this, projectile)) {
			cir.setReturnValue(UpgradeKit.PASS_THROUGH);
		}
	}

	@Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
	private void projecthero$upgradeAllySeat(Entity passenger, EntityDimensions dims, float scale, CallbackInfoReturnable<Vec3> cir) {
		Vec3 seat = UpgradeKit.allySeat((Entity) (Object) this, passenger);
		if (seat != null) {
			cir.setReturnValue(seat);
		}
	}

	/**
	 * v0.17.2 fix (user: "he also starts slowly sliding across the floor for some reason"): Upgrade and the golem he pilots
	 * share one spot, so the golem (a tick behind him on the client) kept shoving him -- and dragged after him, shoving again.
	 * They never push each other.
	 */
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void projecthero$upgradePilotNoPush(Entity other, CallbackInfo ci) {
		if (UpgradeKit.pilotPair((Entity) (Object) this, other)) {
			ci.cancel();
		}
	}
}
