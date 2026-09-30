package com.projecthero.mod.moonknight;

import com.projecthero.mod.moonknight.ability.MoonKnightAlters;
import com.projecthero.mod.moonknight.ability.MoonKnightCape;
import com.projecthero.mod.moonknight.ability.MoonKnightDarts;
import com.projecthero.mod.moonknight.ability.MoonKnightKhonshu;
import com.projecthero.mod.moonknight.ability.MoonKnightTruncheon;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Every damage rule Moon Knight has, in one place, with the per-key numbers living in the ability classes:
 * <ul>
 *   <li>invulnerable while the suit is forming;</li>
 *   <li>incoming: the suit itself takes 20% off every hit (v0.13.21), the Cape Block (hold right click) another 30%,
 *       the Steven alter less from melee; a glide takes no fall damage, and any other fall is halved (v0.14.4);</li>
 *   <li>v0.14.4: every hit dealt or taken by a pact-holder marks him "in combat" (Vengeance only regenerates out of
 *       combat), and damage he deals a target under his Judgement heals him;</li>
 *   <li>outgoing: Moon Mark (R), the Marc / Jake alter bonuses;</li>
 *   <li>a melee hit feeds the Truncheon combo;</li>
 *   <li>out of the suit, a hard hit calls it (v0.13.21, {@link MoonKnightTransform#autoSuit});</li>
 *   <li>a fatal hit asks Khonshu's Resurrection first (from the mod's single {@code ALLOW_DEATH} hook).</li>
 * </ul>
 * Fabric's {@code ALLOW_DAMAGE} can only veto, so a changed amount is applied the same way Thor's passives do it:
 * cancel the hit and re-issue it at the new amount, with a re-entrancy guard letting the re-issued hit through.
 */
public final class MoonKnightDamage {
	private static final ThreadLocal<Boolean> REISSUING = ThreadLocal.withInitial(() -> false);

	private MoonKnightDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(MoonKnightDamage::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0f && source.getEntity() instanceof ServerPlayer attacker && attacker != entity
					&& MoonKnight.isTransformed(attacker) && source.getDirectEntity() == attacker) {
				MoonKnightTruncheon.onMeleeHit(attacker, entity, taken);
			}
			// v0.14.4: combat tracking for the out-of-combat Vengeance regen, and Khonshu's Judgement lifesteal
			if (base > 0.0f && entity instanceof ServerPlayer victim && MoonKnight.hasPower(victim)) {
				MoonKnight.markCombat(victim);
			}
			if (base > 0.0f && source.getEntity() instanceof ServerPlayer dealer && dealer != entity && MoonKnight.hasPower(dealer)) {
				MoonKnight.markCombat(dealer);
				if (taken > 0.0f) {
					MoonKnightKhonshu.onJudgedDamaged(dealer, entity, taken);
				}
			}
			// v0.13.21: an unsuited pact-holder hit hard (or left under 4 hearts) suits up on his own
			if (entity instanceof ServerPlayer victim && !blocked && base > 0.0f && MoonKnight.hasPower(victim)
					&& !MoonKnight.isTransformed(victim)) {
				MoonKnightTransform.autoSuit(victim, base);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(MoonKnightKhonshu::onEntityKilled);
	}

	/**
	 * Incoming-damage multiplier for a suited Moon Knight (the suit, the cape block, the alter, and v0.14.4's halved
	 * fall damage). Public for the tests.
	 */
	public static float incomingFactor(ServerPlayer player, DamageSource source) {
		if (!MoonKnight.isTransformed(player)) {
			return 1.0f;
		}
		float fall = source.is(DamageTypeTags.IS_FALL) ? MoonKnightConfig.SUIT_FALL_DAMAGE_TAKEN : 1.0f;
		return MoonKnightConfig.SUIT_DAMAGE_TAKEN * fall * MoonKnightCape.incomingFactor(player, source)
				* MoonKnightAlters.incomingFactor(player, source);
	}

	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (REISSUING.get() || amount <= 0.0f || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		float factor = 1.0f;
		if (victim instanceof ServerPlayer player && MoonKnight.hasPower(player)) {
			if (MoonKnightTransform.isTransforming(player)) {
				return false; // the suit is forming: untouchable
			}
			if (MoonKnight.isTransformed(player)) {
				if (source.is(DamageTypeTags.IS_FALL) && MoonKnightCape.negatesFall(player)) {
					return false;
				}
				factor *= incomingFactor(player, source);
			}
		}
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim && MoonKnight.isTransformed(attacker)) {
			factor *= MoonKnightDarts.outgoingFactor(attacker, victim);
			factor *= MoonKnightAlters.outgoingFactor(attacker, victim, source);
		}
		if (Math.abs(factor - 1.0f) < 1.0e-4f) {
			return true;
		}
		REISSUING.set(true);
		try {
			victim.hurt(source, amount * factor);
		} finally {
			REISSUING.set(false);
		}
		return false;
	}

	/** From the mod's {@code ALLOW_DEATH} hook: true if Khonshu's Resurrection averted this death. */
	public static boolean tryResurrect(ServerPlayer player) {
		return MoonKnight.isTransformed(player) && MoonKnightKhonshu.tryResurrect(player);
	}
}
