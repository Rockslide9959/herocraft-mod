package com.projecthero.mod.cyborg.block;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** v0.16.5: Cyborg's one block, the Circuit Link's invisible signal ({@link CyborgSignalBlock}). No item. */
public final class CyborgBlocks {
	public static CyborgSignalBlock CYBORG_SIGNAL;

	private CyborgBlocks() {
	}

	public static void initialize() {
		CYBORG_SIGNAL = Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id("cyborg_signal")),
				new CyborgSignalBlock(BlockBehaviour.Properties.of()
						.mapColor(MapColor.NONE)
						.noCollission()
						.noOcclusion()
						.noLootTable()
						.replaceable()
						.instabreak()
						.pushReaction(PushReaction.DESTROY)));
	}
}
