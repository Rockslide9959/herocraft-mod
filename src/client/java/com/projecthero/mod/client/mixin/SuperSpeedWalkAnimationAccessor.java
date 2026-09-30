package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.WalkAnimationState;

/** Super Speed Time Slow: lets a skipped client tick freeze a creature's limb swing instead of jittering it. */
@Mixin(WalkAnimationState.class)
public interface SuperSpeedWalkAnimationAccessor {
	@Accessor("speedOld")
	void projecthero$setSpeedOld(float speedOld);
}
