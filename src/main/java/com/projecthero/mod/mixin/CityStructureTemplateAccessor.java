package com.projecthero.mod.mixin;

import java.util.List;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** v0.16.2 City part B: the City blueprint indexes a template's block palette by position. */
@Mixin(StructureTemplate.class)
public interface CityStructureTemplateAccessor {
	@Accessor("palettes")
	List<StructureTemplate.Palette> projecthero$palettes();
}
