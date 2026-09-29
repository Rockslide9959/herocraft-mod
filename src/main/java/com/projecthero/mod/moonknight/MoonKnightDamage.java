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
 *   <li>incoming: Cape Shroud (X) and the Steven alter reduce hits; a glide takes no fall damage;</li>
 *   <li>outgoing: Moon Mark (R), the Marc / Jake alter bonuses;</li>
 *   <li>a melee hit feeds the Truncheon combo;</li>
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
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(MoonKnightKhonshu::onEntityKilled);
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
				factor *= MoonKnightCape.incomingFactor(player, source);
				factor *= MoonKnightAlters.incomingFactor(player, source);
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
