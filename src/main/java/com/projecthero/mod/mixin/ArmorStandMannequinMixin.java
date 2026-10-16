package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.wardrobe.MannequinEntity;
import com.projecthero.mod.wardrobe.WardrobeMod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * v0.16.5: a {@link MannequinEntity} broken by a player drops a Mannequin, not the armour stand vanilla's private
 * {@code brokenByPlayer} hard-codes. Everything it wore still drops through vanilla's {@code brokenByAnything}.
 */
@Mixin(ArmorStand.class)
public abstract class ArmorStandMannequinMixin {
	@Shadow
	private void brokenByAnything(ServerLevel level, DamageSource source) {
		throw new AssertionError();
	}

	@Inject(method = "brokenByPlayer", at = @At("HEAD"), cancellable = true)
	private void projecthero$dropMannequin(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if ((Object) this instanceof MannequinEntity mannequin) {
			ItemStack drop = new ItemStack(WardrobeMod.MANNEQUIN_ITEM);
			drop.set(DataComponents.CUSTOM_NAME, mannequin.getCustomName());
			Block.popResource(mannequin.level(), mannequin.blockPosition(), drop);
			brokenByAnything(level, source);
			ci.cancel();
		}
	}
}
