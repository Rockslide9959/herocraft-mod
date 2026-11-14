package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.stand.TimeStop;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 Star Platinum: The World -- a speedster moving through someone else's stopped time is held to a normal player's
 * pace: the Super Speed mutation walks, XLR8 and Speed Force speedsters sprint ({@link TimeStop#capMove}). Only a player's
 * own moves ({@code MoverType.SELF}); on the client that is the local player, on the server a server-moved player.
 */
@Mixin(Entity.class)
public abstract class StandTimeStopMoveMixin {
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
	private Vec3 projecthero$timeStopCap(Vec3 movement, MoverType type) {
		if (type != MoverType.SELF) {
			return movement;
		}
		return TimeStop.capMove((Entity) (Object) this, movement);
	}
}
