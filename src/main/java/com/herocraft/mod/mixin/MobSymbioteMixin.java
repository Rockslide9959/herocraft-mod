package com.herocraft.mod.mixin;

import com.herocraft.mod.symbiote.SymbioteHost;
import com.herocraft.mod.symbiote.SymbioteHostSpawns;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The two hooks the rare Symbiote Host needs, both on the common {@code Mob} path:
 * <ul>
 *   <li>{@code finalizeSpawn} RETURN -- delivers the {@link MobSpawnType} so a naturally spawning
 *       hostile can, very rarely, be taken over ({@link SymbioteHostSpawns#consider}). Every other
 *       decision (the odds, the eligibility exclusions) lives in that class.</li>
 *   <li>{@code aiStep} TAIL -- runs {@link SymbioteHost#serverTick} for the black particle aura. It is
 *       a single {@code getAttachedOrElse} null-check for a mob that is not a host.</li>
 * </ul>
 */
@Mixin(Mob.class)
public abstract class MobSymbioteMixin {
	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void herocraft$maybeSymbioteHost(ServerLevelAccessor level, DifficultyInstance difficulty,
			MobSpawnType reason, @Nullable SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
		SymbioteHostSpawns.consider((Mob) (Object) this, level, reason);
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void herocraft$symbioteHostTick(CallbackInfo ci) {
		SymbioteHost.serverTick((Mob) (Object) this);
	}
}
