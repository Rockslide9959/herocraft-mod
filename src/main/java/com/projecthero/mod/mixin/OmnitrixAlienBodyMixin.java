package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.AlienKit;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 Omnitrix framework (Wildmutt): an alien kit's own body box ({@link AlienKit#body}) in place of the player's for the
 * poses it answers, on whichever side asks. The answer can change without a pose change (transforming, turning back), so the
 * player re-measures itself whenever the kit's box for its pose differs from last tick's -- the hitbox and the eye height
 * follow both ways, and a human never pays more than one null check per tick.
 */
@Mixin(Player.class)
public abstract class OmnitrixAlienBodyMixin {
	@Unique
	private EntityDimensions projecthero$alienBody;

	@Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		Player self = (Player) (Object) this;
		AlienKit kit = AlienKit.activeKit(self);
		if (kit != null) {
			EntityDimensions d = kit.body(self, pose);
			if (d != null) {
				cir.setReturnValue(d);
			}
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void projecthero$alienBodyRefresh(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		AlienKit kit = AlienKit.activeKit(self);
		EntityDimensions want = kit == null ? null : kit.body(self, self.getPose());
		if (!java.util.Objects.equals(want, projecthero$alienBody)) {
			projecthero$alienBody = want;
			self.refreshDimensions();
		}
	}
}
