package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.ironman.item.IronManItems;

import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.15.6: the item registry is a {@link DefaultedMappedRegistry}, which overrides the by-id lookups of
 * {@link LegacyItemIdMixin} (a miss returns air instead of null) -- the same old-id -> new-id redirect for those.
 */
@Mixin(DefaultedMappedRegistry.class)
public abstract class LegacyItemIdDefaultedMixin {
	@ModifyVariable(method = "get(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;", at = @At("HEAD"), argsOnly = true)
	private ResourceLocation projecthero$legacyGetId(ResourceLocation id) {
		return IronManItems.legacyId(id);
	}
}
