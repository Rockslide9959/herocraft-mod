package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.item.LegacyItemIds;

import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.15.6, user request: the Iron Man armour item ids use the Mark's number ({@code iron_man_mark_7_leggings}, not
 * {@code iron_man_mark_vii_leggings}). 1.21.1 has no registry aliases, so every id lookup that would miss on one of the
 * old roman-numeral ids is pointed at the new id instead ({@link LegacyItemIds}; v0.17.7: also the removed Superman Suit boots -> its leggings) -- armour saved in worlds, chests
 * and platforms before the rename loads as the renamed item rather than vanishing. Only ever rewrites
 * {@code projecthero:iron_man_mark_(iii|v|vii)_*}; every other lookup passes through untouched.
 */
@Mixin(MappedRegistry.class)
public abstract class LegacyItemIdMixin {
	@ModifyVariable(method = "getHolder(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;", at = @At("HEAD"), argsOnly = true)
	private ResourceLocation projecthero$legacyHolderId(ResourceLocation id) {
		return LegacyItemIds.redirect(id);
	}

	@ModifyVariable(method = "getHolder(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", at = @At("HEAD"), argsOnly = true)
	private ResourceKey<?> projecthero$legacyHolderKey(ResourceKey<?> key) {
		return LegacyItemIds.redirect(key);
	}

	@ModifyVariable(method = "get(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;", at = @At("HEAD"), argsOnly = true)
	private ResourceLocation projecthero$legacyGetId(ResourceLocation id) {
		return LegacyItemIds.redirect(id);
	}

	@ModifyVariable(method = "containsKey(Lnet/minecraft/resources/ResourceLocation;)Z", at = @At("HEAD"), argsOnly = true)
	private ResourceLocation projecthero$legacyContains(ResourceLocation id) {
		return LegacyItemIds.redirect(id);
	}
}
