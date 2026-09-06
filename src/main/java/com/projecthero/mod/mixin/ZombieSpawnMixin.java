package com.projecthero.mod.mixin;

import com.projecthero.mod.event.entity.CursedZombieSpawns;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The single hook that lets a rare naturally spawning zombie become a Cursed Zombie.
 *
 * <p>{@code finalizeSpawn} is the only vanilla call that knows both that a zombie is being created and
 * <em>why</em> -- the {@link MobSpawnType} is right there in the signature. Everything else about the
 * conversion (the odds, the Graveyard proximity bonus, and the deferred swap) lives in
 * {@link CursedZombieSpawns}; this mixin exists purely to deliver the spawn reason, so the injected
 * code is a single method call on a path that runs for every zombie in the world.
 *
 * <p>Injected at {@code RETURN} rather than {@code HEAD} so vanilla has already finished deciding
 * baby-ness and equipment, and the check runs against the finished zombie.
 */
@Mixin(Zombie.class)
public abstract class ZombieSpawnMixin {
	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void projecthero$maybeCursed(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
			SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> callback) {
		if (level.getLevel() instanceof ServerLevel serverLevel) {
			CursedZombieSpawns.considerNaturalSpawn((Zombie) (Object) this, serverLevel, reason);
		}
	}
}
