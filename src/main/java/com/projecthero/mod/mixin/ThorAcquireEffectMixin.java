package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.thoracquire.Valor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.7 Valor: gaining Hero of the Village (a raid won, a crime stopped, the Supervillain Raid's Champion effect) is a heroic
 * deed. Fires only when the effect is newly added, not when an existing one is refreshed.
 */
@Mixin(LivingEntity.class)
public abstract class ThorAcquireEffectMixin {
	@Inject(method = "onEffectAdded", at = @At("HEAD"))
	private void projecthero$valorHeroOfTheVillage(MobEffectInstance effect, Entity source, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && effect.is(MobEffects.HERO_OF_THE_VILLAGE)) {
			Valor.heroOfTheVillage(player);
		}
	}
}
