package com.projecthero.mod.saiyan;

import com.projecthero.mod.saiyan.kit.SaiyanKit;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.3 Saiyan revamp: the Saiyan's hits, both ways.
 * <ul>
 *   <li><b>Dealt</b>: every hit whose attacker is a Saiyan (melee and every kit move) is scaled by {@link Saiyan#damageMult}
 *   -- Super Saiyan, Kaioken, Zenkai and, for melee, a stronger enemy.</li>
 *   <li><b>Taken</b>: a stronger enemy's fight takes 10% off; no fall damage while flying.</li>
 *   <li><b>After</b> a hit lands: Rage, the melee streak (3 in a row = 5 Ki), the stronger-enemy watch ({@link Saiyan#onHurt},
 *   {@link Saiyan#onHitLanded}).</li>
 * </ul>
 * Left click on a creature feeds the kit ({@link SaiyanKit#attack}); charging Ki or being held by a move cancels the swing.
 * A changed hit is cancelled and re-issued behind a re-entrancy guard, vanilla's difficulty scaling undone first.
 */
public final class SaiyanDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private SaiyanDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SaiyanDamage::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, dealt, blocked) -> afterDamage(entity, source, dealt));
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !Saiyan.hasPower(sp)) {
				return InteractionResult.PASS;
			}
			if (Saiyan.charging(sp) || SaiyanKit.busy(sp)) {
				return InteractionResult.FAIL;
			}
			if (entity instanceof LivingEntity) {
				SaiyanAbilityManager.attack(sp);
			}
			return InteractionResult.PASS;
		});
	}

	/** A melee hit: the attacker's own swing (not a projectile or a Ki move). */
	static boolean melee(DamageSource source, ServerPlayer attacker) {
		return source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == attacker;
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0.0f) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		ServerPlayer victim = entity instanceof ServerPlayer p && Saiyan.hasPower(p) ? p : null;
		if (victim != null && (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) && SaiyanFlight.fallSafe(victim)) {
			victim.resetFallDistance();
			return false;
		}
		float mult = 1f;
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && Saiyan.hasPower(attacker)) {
			mult *= Saiyan.damageMult(attacker, melee(source, attacker));
		}
		if (victim != null) {
			mult *= 1f - Saiyan.damageResist(victim);
		}
		if (Math.abs(mult - 1f) < 1.0e-4f || entity.invulnerableTime > 10) {
			return true; // nothing to change (or vanilla will ignore this hit anyway)
		}
		reissue(entity, source, amount * mult);
		return false;
	}

	static void afterDamage(LivingEntity entity, DamageSource source, float dealt) {
		if (dealt <= 0f) {
			return;
		}
		ServerPlayer attacker = source.getEntity() instanceof ServerPlayer p && p != entity && Saiyan.hasPower(p) ? p : null;
		if (entity instanceof ServerPlayer victim && Saiyan.hasPower(victim)) {
			Saiyan.onHurt(victim, source.getEntity() instanceof LivingEntity l ? l : null, dealt);
		}
		if (attacker != null) {
			Saiyan.onHitLanded(attacker, entity, melee(source, attacker));
		}
	}

	private static void reissue(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayer && source.scalesWithDifficulty()) {
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
