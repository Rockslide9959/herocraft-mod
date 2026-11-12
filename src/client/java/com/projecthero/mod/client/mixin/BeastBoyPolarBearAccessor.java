package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.animal.PolarBear;

/** v0.17.3 Beast Boy: the bear / the Beast stand up on their (never ticked) stand-in polar bear's model. */
@Mixin(PolarBear.class)
public interface BeastBoyPolarBearAccessor {
	@Accessor("clientSideStandAnimation")
	void projecthero$setStand(float v);

	@Accessor("clientSideStandAnimationO")
	void projecthero$setStandO(float v);
}
