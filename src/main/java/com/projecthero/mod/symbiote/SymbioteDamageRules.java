package com.projecthero.mod.symbiote;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import com.projecthero.mod.combat.SonicVulnerability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Symbiote's damage-side weaknesses and defences: fire/lava vulnerability, a small passive
 * explosion resistance, and Symbiote Shield's active reduction. One
 * {@link ServerLivingEntityEvents#ALLOW_DAMAGE} listener, following the exact "cancel the original,
 * re-apply a scaled amount behind a re-entrancy guard" pattern
 * {@code com.projecthero.mod.hero.power.HeroDamageRules#reduce} already uses (Fabric's ALLOW_DAMAGE is
 * a yes/no veto with no "change the amount" hook).
 *
 * <p>Base fall-damage/knockback resistance is handled separately as plain attribute modifiers
 * ({@link SymbiotePassives}) -- only rules that need to look at the {@link DamageSource} itself live
 * here. Applies to both host variants: the fire weakness and Shield are properties of the Symbiote
 * organism itself, not of which host it is wearing.
 */
public final class SymbioteDamageRules {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	/** Fire and lava deal 50% more while the Symbiote is active, per the spec's fire weakness. */
	private static final float FIRE_MULTIPLIER = 1.5f;
	/** Sound / sonic attacks deal 50% more too -- the Warden's boom, and anything landing while the
	 *  host is still sonically disrupted (bell, goat horn, boom -- see {@link SonicVulnerability}). */
	private static final float SOUND_MULTIPLIER = 1.5f;
	/** A small passive cut to explosion damage while active -- "reduce... minor explosion damage". */
	private static final float EXPLOSION_FACTOR = 0.8f;
	/** Symbiote Shield: like a real shield, it only stops what comes at your front -- and it stops
	 *  almost all of it (-90%). A hit from the side or behind gets through untouched. */
	private static final float SHIELD_FRONT_FACTOR = 0.1f;

	private SymbioteDamageRules() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SymbioteDamageRules::onAllowDamage);
	}

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player) || !Symbiote.isNormalHost(player)) {
			return true;
		}
		long now = player.level().getGameTime();
		boolean active = Symbiote.isActive(player);

		// Symbiote Leap / Grapple: the launch's OWN fall damage is negated outright (a short grace
		// window after launch) -- no Biomass cost for a landing the Symbiote deliberately set up.
		if (source.is(DamageTypeTags.IS_FALL) && SymbioteAbilityManager.leapFallProtected(player, now)) {
			player.resetFallDistance();
			return false;
		}

		// v0.10.2: a bonded host takes NO fall damage at all -- the Symbiote catches every landing. It
		// costs a little Biomass to do it (scaled to the fall, always small), and nothing when the host
		// is invulnerable (creative / spectator).
		if (source.is(DamageTypeTags.IS_FALL)) {
			player.resetFallDistance();
			if (!player.getAbilities().invulnerable) {
				SymbioteVitalsManager.spendBiomass(player, Math.min(5.0f, 1.0f + amount * 0.25f));
			}
			return false;
		}

		float factor = 1.0f;
		if (active) {
			if (source.is(DamageTypeTags.IS_FIRE)) {
				// IS_FIRE also covers vanilla lava damage.
				factor *= FIRE_MULTIPLIER;
			} else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
				factor *= EXPLOSION_FACTOR;
			}
			if (source.is(DamageTypes.SONIC_BOOM) || SonicVulnerability.isDisrupted(player, now)) {
				factor *= SOUND_MULTIPLIER;
			}
			if (SymbioteAbilityManager.shieldActive(player) && blockedFromFront(player, source)) {
				factor *= SHIELD_FRONT_FACTOR;
			}
		}

		float scaled = amount * factor;
		// v0.11.15: a heavy, dangerous or fatal hit makes the Symbiote wrap its host on its own.
		Symbiote.onIncomingHit(player, scaled);
		// v0.9.24: the host takes the hit IN FULL -- no redirect. Separately, the Symbiote loses a
		// fraction of that hit from its own Biomass bar, in parallel.
		SymbioteVitalsManager.onHostHit(player, scaled);
		if (Math.abs(scaled - amount) < 0.01f) {
			return true;
		}
		return reapply(player, source, scaled);
	}

	/**
	 * Whether {@code source} is coming at the player's front, the same test vanilla's own shield uses
	 * ({@code LivingEntity#isDamageSourceBlocked}): the attacker is in the forward 180-degree arc of
	 * where the player is looking. Sources with no position (starvation, magic, ...) are treated as
	 * unblockable, again matching vanilla.
	 */
	private static boolean blockedFromFront(ServerPlayer player, DamageSource source) {
		net.minecraft.world.phys.Vec3 sourcePos = source.getSourcePosition();
		if (sourcePos == null) {
			return false;
		}
		net.minecraft.world.phys.Vec3 view = player.getViewVector(1.0f);
		net.minecraft.world.phys.Vec3 toSource = sourcePos.vectorTo(player.position()).normalize();
		toSource = new net.minecraft.world.phys.Vec3(toSource.x, 0.0, toSource.z);
		return toSource.dot(view) < 0.0;
	}

	private static boolean reapply(ServerPlayer player, DamageSource source, float amount) {
		if (amount < 0.5f) {
			return false;
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}
}
