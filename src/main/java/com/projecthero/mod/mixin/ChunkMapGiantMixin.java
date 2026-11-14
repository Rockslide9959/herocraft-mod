package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.projecthero.mod.entity.GiantTracking;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.minecraft.server.level.ChunkMap;

/** v0.17.4: lets tests reach an entity's tracker ({@link GiantTracking.Map}). */
@Mixin(ChunkMap.class)
public abstract class ChunkMapGiantMixin implements GiantTracking.Map {
	@Shadow
	@Final
	private Int2ObjectMap<?> entityMap;

	@Override
	public GiantTracking.Tracked projecthero$tracked(int entityId) {
		Object t = entityMap.get(entityId);
		return t instanceof GiantTracking.Tracked tracked ? tracked : null;
	}
}
