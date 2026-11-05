package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltKit;
import com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltRoll;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** v0.17.2 round 3: Cannonbolt's ball moves by {@link CannonboltRoll} on the owner's client instead of walking. */
@Mixin(Player.class)
public abstract class CannonboltRollMixin {
	@ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
	private Vec3 projecthero$cannonboltRoll(Vec3 input) {
		Player self = (Player) (Object) this;
		return self.isLocalPlayer() && CannonboltKit.rolling(self) ? CannonboltRoll.travel(self, input) : input;
	}
}
