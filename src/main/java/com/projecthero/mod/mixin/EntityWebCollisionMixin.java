package com.projecthero.mod.mixin;

import java.util.ArrayList;
import java.util.List;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import com.projecthero.mod.spider.web.WebStructures;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * v0.16.1: Spider-Man's web structures are solid for him and his squad -- they stand, walk and climb on tightropes,
 * woven webs and web walls -- without a single block being placed. {@code Entity.collide} asks the level for the
 * entity collision shapes near the move; this adds the web's small strand boxes to that list for the people allowed to
 * use it ({@link WebStructures#mayUse}). It runs on both sides (the owning client simulates, the server re-checks the
 * move with the same boxes), so walking a tightrope never rubber-bands. Everyone else gets nothing here and passes into
 * the web, where {@link WebStructures} sticks them fast.
 */
@Mixin(Entity.class)
public abstract class EntityWebCollisionMixin {
	@ModifyExpressionValue(method = "collide", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
	private List<VoxelShape> projecthero$webCollision(List<VoxelShape> original, Vec3 movement) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof net.minecraft.world.entity.player.Player)) {
			return original;
		}
		AABB query = self.getBoundingBox().expandTowards(movement).inflate(0.1);
		List<VoxelShape> webs = WebStructures.collisionShapes(self, query);
		if (webs.isEmpty()) {
			return original;
		}
		List<VoxelShape> merged = new ArrayList<>(original.size() + webs.size());
		merged.addAll(original);
		merged.addAll(webs);
		return merged;
	}
}
