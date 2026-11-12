package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.beastboy.BeastBoy;
import com.projecthero.mod.beastboy.BeastBoyForms;
import com.projecthero.mod.beastboy.BeastForm;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Beast Boy: in an animal form the player's box is the animal's ({@link BeastBoyForms#dimensions}) in every pose but
 * sleeping, on both sides -- the mouse and the fly slip through 1-block gaps, the whale fills a corridor. Like the Omnitrix
 * aliens' body mixin, the player re-measures himself whenever the form's box differs from last tick's.
 */
@Mixin(Player.class)
public abstract class BeastBoyBodyMixin {
	@Unique
	private BeastForm projecthero$beastForm = BeastForm.HUMAN;

	@Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
	private void projecthero$beastDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if (pose == Pose.SLEEPING) {
			return;
		}
		BeastForm f = BeastBoy.form((Player) (Object) this);
		if (f != BeastForm.HUMAN) {
			cir.setReturnValue(BeastBoyForms.dimensions(f));
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void projecthero$beastBodyRefresh(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		BeastForm f = BeastBoy.form(self);
		if (f != projecthero$beastForm) {
			projecthero$beastForm = f;
			self.refreshDimensions();
		}
	}
}
