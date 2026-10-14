package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.model.geom.ModelPart;

/** v0.16.2: a cube's faces, for {@code JointedLimbs} (bent arms / legs). */
@Mixin(ModelPart.Cube.class)
public interface ModelPartCubeAccessor {
	@Accessor("polygons")
	ModelPart.Polygon[] projecthero$polygons();
}
