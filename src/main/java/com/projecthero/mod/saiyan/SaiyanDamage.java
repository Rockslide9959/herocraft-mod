package com.projecthero.mod.saiyan;

import java.util.UUID;

import com.projecthero.mod.saiyan.data.SaiyanState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * What hurts a Saiyan (v0.17.3):
 * <ul>
 *   <li>every hit is cut by the form's {@link SaiyanForm#damageReduction} (Base 10%, Super Saiyan 25%);</li>
 *   <li>no fall damage while flying or just after a Ki Dash.</li>
 * </ul>
 * And charging Ki: no melee (the swing does nothing). Left click on a creature otherwise feeds the combo
 * ({@link SaiyanAbilityManager#attack}). A changed hit is cancelled and re-issued behind a re-entrancy guard, vanilla's
 * difficulty scaling undone first (as Black Panther's).
 */
public final class SaiyanDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private SaiyanDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SaiyanDamage::allowDamage);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !Saiyan.hasPower(sp)) {
				return InteractionResult.PASS;
			}
			if (Saiyan.charging(sp)) {
				return InteractionResult.FAIL;
			}
			if (entity instanceof LivingEntity) {
				SaiyanAbilityManager.attack(sp);
			}
			return InteractionResult.PASS;
		});
	}

	static void clearSessionState() {
	}

	static void forget(UUID id) {
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0.0f || !(entity instanceof ServerPlayer player) || !Saiyan.hasPower(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if ((source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) && SaiyanFlight.fallSafe(player)) {
			player.resetFallDistance();
			return false;
		}
		if (player.invulnerableTime > 10) {
			return true; // vanilla will ignore this hit anyway
		}
		SaiyanState s = Saiyan.state(player);
		SaiyanForm f = s.form();
		if (f.damageReduction > 0f) {
			reissue(player, source, amount * (1f - f.damageReduction));
			return false;
		}
		return true;
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
