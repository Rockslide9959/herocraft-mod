package com.projecthero.mod.mixin;

import com.mojang.datafixers.util.Either;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** v0.16.2 City part B: the City blueprint reads which template (and processors) a placed jigsaw piece used. */
@Mixin(SinglePoolElement.class)
public interface CitySinglePoolElementAccessor {
	@Accessor("template")
	Either<ResourceLocation, StructureTemplate> projecthero$template();

	@Accessor("processors")
	Holder<StructureProcessorList> projecthero$processors();
}
