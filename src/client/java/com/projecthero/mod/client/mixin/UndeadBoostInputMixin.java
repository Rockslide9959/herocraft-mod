package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.undead.UndeadFlight;

import net.minecraft.client.Minecraft;

/**
 * v0.17.3: while Undead flies, right click is the boost ({@code UndeadClient} sends it from the key's held state): it must not also
 * use the held item, place a block, open a door or right-click an entity (his hands are blown off in flight anyway). The ability
 * keys are separate key mappings, so no other move fires from it; the Shift + right click Shoulder Sword is skipped while flying
 * ({@code UndeadKitFx.shoulderKey}).
 */
@Mixin(Minecraft.class)
public abstract class UndeadBoostInputMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$undeadBoostNoUse(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		// (and a click still held from a boost that ended in a landing: nothing until it is let go)
		if (mc.player != null && (UndeadFlight.isFlying(mc.player) || com.projecthero.mod.client.undead.UndeadClient.useLatched())) {
			ci.cancel();
		}
	}
}
