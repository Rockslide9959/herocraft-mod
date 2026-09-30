package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.WalkAnimationState;

/** Super Speed Time Slow: lets a held-back creature's limb swing creep on smoothly instead of jumping once a second. */
@Mixin(WalkAnimationState.class)
public interface SuperSpeedWalkAnimationAccessor {
	@Accessor("speedOld")
	void projecthero$setSpeedOld(float speedOld);

	@Accessor("position")
	float projecthero$position();

	@Accessor("position")
	void projecthero$setPosition(float position);
}
