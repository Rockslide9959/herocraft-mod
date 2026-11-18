package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.squad.SquadKnockdownClient;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;

/**
 * v0.17.5 squad knockdown: a knocked player cannot jump or sneak (and so cannot toggle flight with a double jump either). The
 * jump key is still read raw by {@link SquadKnockdownClient} for the hold-to-give-up.
 */
@Mixin(KeyboardInput.class)
public abstract class SquadKnockdownInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void projecthero$knockedInput(CallbackInfo ci) {
		if (SquadKnockdownClient.selfKnocked()) {
			Input self = (Input) (Object) this;
			self.jumping = false;
			self.shiftKeyDown = false;
		}
	}
}
