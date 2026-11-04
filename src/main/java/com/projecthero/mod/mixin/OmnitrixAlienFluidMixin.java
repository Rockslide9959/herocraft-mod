package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.AlienKit;

import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2 Omnitrix framework (Way Big; user: "Does not get slowed down by water or lava and can walk safely through both"): while
 * {@link AlienKit#ignoresFluids}, water and lava do not touch his movement -- no fluid drag or float ({@code isAffectedByFluids}
 * picks {@code LivingEntity.travel}'s ground branch), no currents ({@code isPushedByFluid}), no swimming pose. Both sides (the
 * owner's client moves him).
 */
@Mixin(Player.class)
public abstract class OmnitrixAlienFluidMixin {
	@Unique
	private boolean projecthero$ignoresFluids() {
		Player self = (Player) (Object) this;
		AlienKit kit = AlienKit.activeKit(self);
		return kit != null && kit.ignoresFluids(self);
	}

	@Inject(method = "isAffectedByFluids", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienFluidDrag(CallbackInfoReturnable<Boolean> cir) {
		if (projecthero$ignoresFluids()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienFluidPush(CallbackInfoReturnable<Boolean> cir) {
		if (projecthero$ignoresFluids()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienNoSwim(CallbackInfo ci) {
		if (projecthero$ignoresFluids()) {
			((Player) (Object) this).setSwimming(false);
			ci.cancel();
		}
	}
}
