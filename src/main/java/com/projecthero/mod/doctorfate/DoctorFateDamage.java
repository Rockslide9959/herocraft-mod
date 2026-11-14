package com.projecthero.mod.doctorfate;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate: hits, both ways.
 * <ul>
 *   <li>Helmet on: 30% off every hit; Wither damage never lands (the effects themselves are cleared in the tick).</li>
 *   <li>Ankh Shield up: everything from the front is blocked (Kent's own shield, helmet off: 60% of it).</li>
 *   <li>Ward of Order: the caster and squadmates standing in it take 30% less.</li>
 *   <li>Death's door: a hit that leaves him at 3 hearts or fewer brings Nabu's offer ({@link DoctorFateBargains#onHurt}).</li>
 *   <li>A creature of Chaos he kills counts for Order's command.</li>
 * </ul>
 * A changed hit is cancelled and re-issued behind a re-entrancy guard, vanilla's difficulty scaling undone first (as the Spin's).
 */
public final class DoctorFateDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private DoctorFateDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(DoctorFateDamage::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(DoctorFateDamage::allowDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, dealt, blocked) -> {
			if (entity instanceof ServerPlayer p && DoctorFate.hasPower(p)) {
				DoctorFateBargains.onHurt(p);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer p && p != entity && DoctorFate.hasPower(p)) {
				DoctorFateBargains.onKill(p, entity);
			}
		});
	}

	/**
	 * v0.17.4 round 2 (user: "when the player dies and that menu pops up to save them, they can still die before choosing an option,
	 * please fix this"): the root cause was that Death's door was only <em>offered</em> (a screen with a 10 s timer) while every
	 * later hit still landed, and a killing blow never offered it at all. Now:
	 * <ul>
	 *   <li>while the offer is open he takes no damage at all (only /kill gets through) and can't die, until he chooses or it times
	 *   out (a refusal);</li>
	 *   <li>a blow that would kill him opens the offer at 1 health instead, if Nabu would offer it then (helmet on, not offered in the
	 *   last minute);</li>
	 *   <li>possessed, he can't die at all (user: "player cant die in this state"): held at 1 health.</li>
	 * </ul>
	 */
	public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer p) || !DoctorFate.hasPower(p) || source.is(DamageTypes.GENERIC_KILL)) {
			return true;
		}
		if (DoctorFate.possessed(p) || DoctorFateBargains.pending(p) == com.projecthero.mod.doctorfate.data.FateState.Bargain.DEATHS_DOOR
				|| DoctorFateBargains.offerAtDeath(p)) {
			p.setHealth(1f);
			return false;
		}
		return true;
	}

	/** v0.17.4 round 2 (user: "doctor fate shouldn't take fall damage"): with the helmet on (or going on), possessed, or in the lockout after. */
	public static boolean ignoresFalls(ServerPlayer p) {
		com.projecthero.mod.doctorfate.data.FateState s = DoctorFate.peek(p);
		boolean helmet = s != null && s.hasPower && (s.helmetOn || s.helmetChangeAt > 0L);
		return helmet || DoctorFate.possessed(p) || DoctorFate.lockoutTicksLeft(p) > 0;
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f || !(entity instanceof ServerPlayer victim)) {
			return true;
		}
		if (DoctorFate.hasPower(victim) && !source.is(DamageTypes.GENERIC_KILL)) {
			// v0.17.4 round 2: Death's door open: Nabu holds every blow off until he chooses
			if (DoctorFateBargains.pending(victim) == com.projecthero.mod.doctorfate.data.FateState.Bargain.DEATHS_DOOR) {
				return false;
			}
			if (source.is(DamageTypeTags.IS_FALL) && ignoresFalls(victim)) {
				victim.resetFallDistance();
				return false;
			}
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		boolean fate = DoctorFate.hasPower(victim);
		boolean warded = DoctorFateSpells.inWard(victim);
		if (!fate && !warded) {
			return true;
		}
		float mult = 1f;
		if (fate) {
			boolean helmet = DoctorFate.wearsHelmet(victim) || DoctorFate.possessed(victim);
			if (helmet && source.is(DamageTypes.WITHER)) {
				return false;
			}
			if (DoctorFate.shieldUp(victim)) {
				Vec3 from = source.getSourcePosition();
				if (from == null && source.getEntity() != null) {
					from = source.getEntity().position();
				}
				if (from != null && DoctorFateSpells.shieldCovers(victim, from)) {
					if (helmet) {
						victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BLOCK,
								net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.3f);
						return false; // blocks everything from the front
					}
					mult *= 1f - DoctorFateConfig.WEAK_SHIELD_BLOCK;
				}
			}
			if (helmet) {
				mult *= 1f - DoctorFateConfig.HELMET_RESIST;
			}
		}
		if (warded) {
			mult *= 1f - DoctorFateConfig.WARD_RESIST;
		}
		if (mult >= 0.999f) {
			return true;
		}
		if (victim.invulnerableTime > 10) {
			return true; // vanilla will ignore this hit anyway
		}
		reissue(victim, source, amount * mult);
		return false;
	}

	private static void reissue(LivingEntity target, DamageSource source, float amount) {
		if (source.scalesWithDifficulty()) {
			amount = switch (target.level().getDifficulty()) {
				case HARD -> amount / 1.5f;
				case EASY -> amount >= 2f ? 2f * (amount - 1f) : amount;
				default -> amount;
			};
		}
		REENTRANT.set(true);
		try {
			target.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}
}
