package com.projecthero.mod.mixin;

import com.projecthero.mod.symbiote.SymbioteHost;
import com.projecthero.mod.symbiote.SymbioteHostSpawns;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
	private void projecthero$maybeSymbioteHost(ServerLevelAccessor level, DifficultyInstance difficulty,
			MobSpawnType reason, @Nullable SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
		SymbioteHostSpawns.consider((Mob) (Object) this, level, reason);
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void projecthero$symbioteHostTick(CallbackInfo ci) {
		SymbioteHost.serverTick((Mob) (Object) this);
		com.projecthero.mod.symbiote.SymbiotePet.serverTick((Mob) (Object) this);
	}

	/**
	 * v0.14.4: a Symbiote Pet can never take its owner, its owner's squadmates or their pets as a target --
	 * refused at the one choke point every targeting path (goals, retaliation, owner-assist) goes through.
	 */
	@ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
	private LivingEntity projecthero$symbiotePetSparesFriends(LivingEntity target) {
		Mob self = (Mob) (Object) this;
		if (target != null && !self.level().isClientSide()
				&& com.projecthero.mod.symbiote.SymbiotePet.refuses(self, target)) {
			return null;
		}
		return target;
	}
}
