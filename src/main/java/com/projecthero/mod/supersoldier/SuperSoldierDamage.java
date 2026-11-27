package com.projecthero.mod.supersoldier;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.supersoldier.data.SuperSoldierState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Super Soldier's damage rules (v0.14.8).
 *
 * <ul>
 *   <li><b>Taken:</b> 30% less from everything but the void, /kill and other invulnerability-bypassing sources. The
 *       Shoulder / Shield Bash's frames make him untouchable (v0.17.7; were the Tactical Roll's); his own leaps, dashes and bashes
 *       never cause fall damage, and neither does a landing on a raised shield while looking down (v0.17.7).</li>
 * </ul>
 * Fabric's {@code ALLOW_DAMAGE} is a boolean veto, so a changed amount cancels the hit and re-applies the new one behind a
 * re-entrancy guard (the same pattern as every other power's damage rules). The rejection death of the unrefined serum
 * uses its own {@link #SERUM_REJECTION} damage type (tagged to bypass invulnerability, armour and effects).
 */
public final class SuperSoldierDamage {
	public static final ResourceKey<DamageType> SERUM_REJECTION = ResourceKey.create(Registries.DAMAGE_TYPE,
			ProjectHeroMod.id("serum_rejection"));

	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private SuperSoldierDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SuperSoldierDamage::onAllowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, dealtAmount, blocked) -> {
			if (entity instanceof ServerPlayer victim && SuperSoldier.hasPower(victim)) {
				SuperSoldier.markCombat(victim);
			}
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && SuperSoldier.hasPower(attacker)) {
				SuperSoldier.markCombat(attacker);
			}
		});
	}

	public static DamageSource serumRejection(ServerLevel level) {
		return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SERUM_REJECTION));
	}

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f) {
			return true;
		}
		float factor = 1.0f;
		// incoming
		if (entity instanceof ServerPlayer player && SuperSoldier.hasPower(player)
				&& !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			SuperSoldierState s = SuperSoldier.state(player);
			long now = player.level().getGameTime();
			if (s.iframeUntil > now) {
				return false; // v0.17.7: the Bash's frames
			}
			if (CapShieldSlot.blocks(player, source)) {
				CapShieldSlot.onBlocked(player, source); // v0.17.5: the slotted shield raised (right-click, empty hand) -- a full block
				return false;
			}
			if ((source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL))
					&& (s.noFallUntil > now || SuperSoldierAbilities.slamming(player))) {
				player.resetFallDistance();
				return false;
			}
			if (source.is(DamageTypes.FALL) && SuperSoldierAbilities.shieldLanding(player)) {
				// v0.17.7: blocking with the shield and looking down -- the shield takes the landing
				SuperSoldierAbilities.shieldLandingFx(player);
				player.resetFallDistance();
				return false;
			}
			factor *= SuperSoldierConfig.DAMAGE_TAKEN_FACTOR;
		}
		if (Math.abs(factor - 1.0f) < 1.0e-3f) {
			return true;
		}
		float changed = amount * factor;
		if (changed < 0.05f) {
			return false;
		}
		REENTRANT.set(true);
		try {
			entity.hurt(source, changed);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}
}
