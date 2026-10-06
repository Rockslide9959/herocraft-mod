package com.projecthero.mod.ironman.ability;

import com.projecthero.mod.ironman.IronManSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.29 (agent C): the held chest Unibeam the Mark 6 and Mark 7 share -- it fires for as long as the key is held,
 * paid per tick, and its cooldown starts when it stops (release, empty suit, chestplate off). The same damage model as
 * the Mark III's held beam ({@link IronManMark3}): per-tick, i-frame paced (a hit lands every ~0.5 s), soft blocks
 * break where the beam hits when griefing is on. Each suit passes its own {@link Spec}.
 */
public final class IronManHeldBeam {
	/** One suit's beam: which suit + cooldown id, damage per damage tick, energy per tick, cooldown, range. */
	public record Spec(String suitId, String cooldownId, float damage, float energyPerTick, int cooldownTicks, double range) {
	}

	private static final Map<UUID, Spec> FIRING = new HashMap<>();

	private IronManHeldBeam() {
	}

	public static void clearSessionState() {
		FIRING.clear();
	}

	public static boolean firing(ServerPlayer player) {
		return FIRING.containsKey(player.getUUID());
	}

	/** Key-down: start the beam (needs the chestplate, the cooldown and one tick's energy). */
	public static void start(ServerPlayer player, IronManSuit suit, Spec spec) {
		if (FIRING.containsKey(player.getUUID())) {
			return; // key repeat
		}
		if (!IronManArmor.hasChestplate(player, spec.suitId())) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_chest"), true);
			return;
		}
		if (!IronManAbilities.cooldownReady(player, spec.suitId(), spec.cooldownId())) {
			return;
		}
		float perTick = spec.energyPerTick() * suit.energyCostMultiplier();
		if (!IronManEnergy.has(player, spec.suitId(), perTick)) {
			IronManAbilities.noEnergy(player, spec.energyPerTick() * 20f * suit.energyCostMultiplier());
			return;
		}
		FIRING.put(player.getUUID(), spec);
		IronManSounds.move(player, IronManSounds.UNIBEAM_CHARGE, 1.1f, 1.0f);
		IronManSounds.loop(player, IronManSounds.REPULSOR_CHARGE, 0.7f, 0.7f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.unibeam_firing"), true);
	}

	/** Key-up / shutdown: stop the beam; starts its cooldown if it was firing and {@code cooldown}. */
	public static void stop(ServerPlayer player, boolean cooldown) {
		Spec spec = FIRING.remove(player.getUUID());
		if (spec == null) {
			return;
		}
		if (cooldown) {
			TonyStark.triggerCooldown(player, spec.suitId(), spec.cooldownId(), spec.cooldownTicks());
		}
		IronManSounds.play(player, IronManSounds.UNIBEAM_END, 1.0f, 1.0f);
	}

	/** {@link #stop}, but only if the running beam belongs to {@code suitId} (each kit's shutdown leaves the others' alone). */
	public static void stopFor(ServerPlayer player, String suitId, boolean cooldown) {
		Spec spec = FIRING.get(player.getUUID());
		if (spec != null && spec.suitId().equals(suitId)) {
			stop(player, cooldown);
		}
	}

	/** Per-tick while a powered suit is worn (from the suit's own kit tick). */
	public static void tick(ServerPlayer player, IronManSuit suit) {
		Spec spec = FIRING.get(player.getUUID());
		if (spec == null) {
			return;
		}
		if (!spec.suitId().equals(suit.id()) || !IronManArmor.hasChestplate(player, spec.suitId())
				|| !IronManEnergy.spend(player, spec.suitId(), spec.energyPerTick() * suit.energyCostMultiplier())) {
			stop(player, true);
			return;
		}
		long now = player.level().getGameTime();
		ServerLevel level = (ServerLevel) player.level();
		double range = spec.range();
		Vec3 chest0 = player.position().add(0, player.getBbHeight() * 0.62, 0);
		Vec3 dir = IronManTargeting.aim(player, chest0, player.getLookAngle(), range);
		Vec3 chest = chest0.add(dir.scale(0.4));
		Vec3 end = chest.add(dir.scale(range));
		IronManAbilityFx.hold(player, IronManAbilityFx.UNIBEAM);
		if (now % 8 == 0) {
			IronManSounds.play(player, IronManSounds.UNIBEAM_LOOP, 0.8f, 1.0f);
		}
		if (now % 2 == 0) {
			IronManAbilities.broadcastBeam(player, chest, end, 2);
		}
		if (now % 10 == 0) {
			var bh = AbilityHelpers.raycastBlock(player, range);
			if (bh.getType() == HitResult.Type.BLOCK && AbilityHelpers.canGrief()) {
				BlockPos bp = bh.getBlockPos();
				float speed = level.getBlockState(bp).getDestroySpeed(level, bp);
				if (speed >= 0f && speed < 3.0f) {
					level.destroyBlock(bp, true, player);
				}
			}
			IronManSounds.play(player, IronManSounds.BEAM_CRACKLE, 0.7f, 1.0f);
		}
		double half = range * 0.5;
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, chest.add(dir.scale(half)), half)) {
			if (e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(chest).normalize().dot(dir) > 0.9) {
				AbilityHelpers.hurt(player, e, spec.damage());
				e.igniteForSeconds(2);
			}
		}
	}
}
