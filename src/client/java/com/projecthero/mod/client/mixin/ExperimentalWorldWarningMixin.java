package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.serialization.Lifecycle;

import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.WorldData;

/**
 * v0.17.5 (user: "why whenever i enter the test worlds do I have to select ... 'I know what I'm doing'"): Project Hero adds its
 * dimensions (Hulk mind, Galactus orbit, Celestial Council, Speed Force) from data files, and vanilla marks any world whose world
 * generation comes from data as {@link Lifecycle#experimental()} -- so every load of a modded world stopped on the "Experimental
 * Settings" backup screen, and every new world on the "experimental" confirm screen, with no way to remember the answer.
 *
 * <p>This treats an <em>experimental</em> world-gen lifecycle as stable in exactly those two checks. A <em>deprecated</em> one
 * (a really old customised world) still warns, and nothing else about loading or saving changes -- only which screen shows.
 */
@Mixin(WorldOpenFlows.class)
public abstract class ExperimentalWorldWarningMixin {

	/** Opening an existing world: skip the backup prompt that only exists because of data-driven dimensions. */
	@Redirect(method = "openWorldCheckWorldStemCompatibility", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/storage/WorldData;worldGenSettingsLifecycle()Lcom/mojang/serialization/Lifecycle;"))
	private Lifecycle projecthero$stableModdedWorldGen(WorldData data) {
		Lifecycle life = data.worldGenSettingsLifecycle();
		return life == Lifecycle.experimental() ? Lifecycle.stable() : life;
	}

	/** Creating a new world: skip the "experimental settings" confirm screen for the same reason. */
	@ModifyVariable(method = "confirmWorldCreation", at = @At("HEAD"), argsOnly = true)
	private static Lifecycle projecthero$stableOnCreate(Lifecycle life) {
		return life == Lifecycle.experimental() ? Lifecycle.stable() : life;
	}
}
