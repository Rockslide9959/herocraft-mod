package com.projecthero.mod.ironman;

import com.projecthero.mod.ironman.ability.IronManComicState;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * Iron Man suit damage rules + suit integrity.
 *
 * <p><b>v0.16.1 comic rework (explicit user design): the suit ABSORBS its Mark's share of every hit while it has
 * integrity</b> ({@link IronManSuit#absorbShare()}: Mark 1 20%, Mark 1 V2 25%, Mark 2 30%, Mark 3 35%, Hydro 40%, Stealth
 * 30%, Centurion 45%, Space 50%, Hulkbuster 60%). That portion is removed from the hit the wearer takes and comes off the
 * suit's integrity instead, 1:1 ({@link #absorb}); a hit bigger than what is left of the integrity lands the rest on the
 * wearer. Ultron's hits still wear the hull 10% harder ({@code UltronCombat.drainSuit}). At 0 integrity the suit is
 * disabled ({@link IronManIntegrity}) -- it absorbs nothing until repaired. The v0.15.3 / v0.15.9 "integrity bleeds the
 * whole hit while the wearer takes it in full" rule is gone.
 *
 * <p>Outgoing (also here, one cancel-and-reissue): the Hydro armour's hits land x1.5 while its wearer is in water, the
 * Hulkbuster's x1.5 on bosses and the Hulk, plus the comic kit's own marks (JARVIS-scanned targets, the Stealth Marked
 * Shot) -- {@link IronManComicState#outgoingMultiplier}.
 *
 * <p>Special rules kept: no fall damage in any Iron Man piece, bulletproof against the mod's guns (chestplate on),
 * immunity while a suit is assembling / during a Sentry step-in / Protocol Phoenix. New: the Space armour ignores fire,
 * lava and freezing; the comic Energy Shields and the Hulkbuster's Hydraulic Brace reduce hits
 * ({@link IronManComicState#incomingFactor}).
 */
public final class IronManDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	/** v0.16.1: the Hydro underwater and Hulkbuster boss / Hulk damage multiplier. */
	public static final float HYDRO_WATER_MULT = 1.5f;
	public static final float HULKBUSTER_BOSS_MULT = 1.5f;

	private IronManDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(IronManDamage::onAllowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && !blocked) {
				onDamageTaken(player, source, base);
			}
			// v0.16.1: the combat clock (Mark 6 / 7 self-repair waits for 10 s without taking or dealing damage)
			if (!blocked && source.getEntity() instanceof ServerPlayer attacker && attacker != entity) {
				IronManIntegrity.markCombat(attacker);
			}
		});
		IronManCombo.initialize(); // v0.14.29 agent F: repulsor -> melee combo
		IronManComicState.initialize(); // v0.16.1: the comic kit's areas / follow-ups tick on the server tick
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			java.util.UUID id = handler.getPlayer().getUUID();
			IronManComicState.forget(id);
			com.projecthero.mod.ironman.ability.IronManComicRouter.forget(id);
			IronManIntegrity.forget(id);
			HulkbusterShield.forget(id); // v0.16.7 (user)
			com.projecthero.mod.ironman.ability.era1.EraOneEject.forget(id); // v0.16.7
		});
	}

	/** v0.14.27: wearing any Iron Man piece = no fall damage. */
	public static boolean fallImmune(net.minecraft.world.entity.player.Player player) {
		return IronManArmor.wearingAnyIronMan(player);
	}

	/** v0.15.3: nothing hurts a Tony Stark player while a suit is assembling onto them. */
	public static boolean suitUpImmune(ServerPlayer player) {
		return TonyStark.hasPower(player) && com.projecthero.mod.ironman.suit.IronManSuitUpManager.assembling(player);
	}

	/** Fabric {@code ALLOW_DAMAGE}: false cancels the hit. Public for the gametests (mock players never fire it). */
	public static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get()) {
			return true;
		}
		// ---- outgoing: an Iron Man wearer's hits (comic multipliers), any target
		float scaled = amount;
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && TonyStark.hasPower(attacker)) {
			IronManComicState.onOwnerAttack(attacker);
			scaled = amount * outgoingMultiplier(attacker, entity);
		}
		if (!(entity instanceof ServerPlayer player) || !TonyStark.hasPower(player)) {
			return settle(entity, source, amount, scaled);
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return true;
		}
		if (ProtocolPhoenix.incapacitated(player)) {
			return false;
		}
		if (suitUpImmune(player)) {
			return false;
		}
		if (com.projecthero.mod.ironman.entity.IronManSentryEntity.steppingIn(player)) {
			return false;
		}
		if (source.is(DamageTypeTags.IS_FALL) && fallImmune(player)) {
			player.resetFallDistance();
			return false;
		}

		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		if (suit == null || !IronManArmor.hasChestplate(player, suitId)) {
			return settle(player, source, amount, scaled);
		}
		// v0.14.31: bulletproof against the mod's guns
		if (com.projecthero.mod.firearm.Gunfire.active()) {
			return false;
		}
		// v0.16.1: the Space armour shrugs off fire, lava and freezing
		if (suit.hazardImmune() && isHazard(source)) {
			player.clearFire();
			return false;
		}
		// v0.16.7 (user) Era II: the Mk IX Inferno -- fire and lava do nothing, with the whole suit on
		if (fireProof(player, suit, source)) {
			player.clearFire();
			com.projecthero.mod.ironman.ability.IronManMark9.onFireShrugged(player, source, amount); // v0.16.7: the heat sink
			return false;
		}
		// v0.16.1: the comic Energy Shields / Hydraulic Brace (a share of the hit is stopped outright)
		float factor = IronManComicState.incomingFactor(player, suit, source);
		// v0.16.7 (user): a working Hulkbuster takes the whole hit itself, like a Titan Shifter's Titan (HulkbusterShield)
		if (HulkbusterShield.take(player, suit, source, scaled * factor)) {
			IronManIntegrity.markCombat(player);
			return false;
		}
		// v0.16.1: the suit absorbs its share of what is left, from integrity
		float landed = absorb(player, suit, source, scaled * factor);
		if (landed < 0.05f && amount > 0f) {
			IronManFaceplate.autoClose(player);
			IronManIntegrity.markCombat(player);
			return false; // fully soaked by the suit / shield
		}
		return settle(player, source, amount, landed);
	}

	/** Let the hit through untouched ({@code landed == amount}) or cancel it and land {@code landed} instead. */
	private static boolean settle(LivingEntity target, DamageSource source, float amount, float landed) {
		if (landed == amount) {
			return true;
		}
		reissue(target, source, landed);
		return false;
	}

	/**
	 * v0.16.1: the integrity absorption. Takes the worn suit's {@link IronManSuit#absorbShare()} of {@code amount} off its
	 * integrity (1:1, never more than it has left) and returns what lands on the wearer. A disabled suit (0 integrity)
	 * absorbs nothing. Falls, starvation, drowning, suffocation, the world border and /kill are never absorbed. Public so
	 * the gametests can drive it (mock players never fire the damage events).
	 */
	public static float absorb(ServerPlayer player, IronManSuit suit, DamageSource source, float amount) {
		if (amount <= 0f || suit == null || !absorbable(source)) {
			return amount;
		}
		float integrity = IronManEnergy.integrity(player, suit.id());
		if (integrity <= 0f) {
			return amount;
		}
		float share = Math.max(0f, Math.min(1f, suit.absorbShare()));
		float soaked = Math.min(integrity, amount * share);
		if (soaked <= 0f) {
			return amount;
		}
		IronManEnergy.setIntegrity(player, suit.id(), integrity - soaked);
		if (integrity - soaked <= 0f) {
			ServerLevel level = (ServerLevel) player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), IronManSounds.POWER_FAIL, SoundSource.PLAYERS, 1.0f, 1.0f);
			player.displayClientMessage(net.minecraft.network.chat.Component
					.translatable("message.projecthero.ironman.integrity_failed")
					.withStyle(net.minecraft.ChatFormatting.RED), true);
			com.projecthero.mod.ironman.IronManSuitTicker.onDisabled(player, suit);
		}
		return amount - soaked;
	}

	/** What the suit's integrity may soak: everything but the "no armour helps" environmental sources. */
	public static boolean absorbable(DamageSource source) {
		return !(source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.STARVE) || source.is(DamageTypes.DROWN)
				|| source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.OUTSIDE_BORDER) || source.is(DamageTypes.GENERIC_KILL)
				|| source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.CRAMMING));
	}

	/** v0.16.1: fire, lava, hot floors and freezing -- what the Space armour ignores. */
	public static boolean isHazard(DamageSource source) {
		return source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FREEZING);
	}

	/**
	 * v0.16.7 (user) Era II: does the Mk IX Inferno's full-set immunity stop this hit? Fire, lava, magma blocks (all in
	 * {@code #is_fire}), only while all four pieces of a {@link IronManSuit#fireImmune()} suit are worn. Public for the
	 * gametests (mock players never fire the damage events).
	 */
	public static boolean fireProof(ServerPlayer player, IronManSuit suit, DamageSource source) {
		return suit != null && suit.fireImmune() && source.is(DamageTypeTags.IS_FIRE)
				&& IronManArmor.wearingFullSuit(player, suit.id());
	}

	/**
	 * v0.16.1: the multiplier on a hit an Iron Man wearer deals: Hydro x1.5 in water, Hulkbuster x1.5 on bosses / the Hulk,
	 * times the comic kit's marks. 1 = untouched.
	 */
	public static float outgoingMultiplier(ServerPlayer attacker, LivingEntity target) {
		String suitId = IronManArmor.wornSuitId(attacker);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		if (suit == null || !IronManArmor.hasChestplate(attacker, suitId)) {
			return 1f;
		}
		float mult = 1f;
		if (suit.hydro() && attacker.isInWater()) {
			mult *= HYDRO_WATER_MULT;
		}
		if (suit.hulkbuster() && (com.projecthero.mod.kryptonian.KryptonianCombat.isBoss(target)
				|| target instanceof net.minecraft.world.entity.player.Player p && com.projecthero.mod.hulk.Hulk.isHulk(p))) {
			mult *= HULKBUSTER_BOSS_MULT;
		}
		mult *= IronManComicState.outgoingMultiplier(attacker, target);
		return mult;
	}

	/**
	 * A hit has landed on the player ({@code AFTER_DAMAGE}). v0.16.1: integrity was already taken in {@link #absorb}; this
	 * only seals an open faceplate and starts the combat clock. Public so the gametests can drive it.
	 */
	public static void onDamageTaken(ServerPlayer player, DamageSource source, float taken) {
		if (taken <= 0f || !TonyStark.hasPower(player)) {
			return;
		}
		IronManIntegrity.markCombat(player);
		if (IronManArmor.wearingAnyIronMan(player)) {
			IronManFaceplate.autoClose(player);
		}
	}

	/** v0.14.27: an arrow or any fire / lava / hot-floor source. */
	public static boolean isArrowOrFire(DamageSource source) {
		return source.is(DamageTypeTags.IS_FIRE)
				|| source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow;
	}

	private static void reissue(LivingEntity target, DamageSource source, float amount) {
		if (amount < 0.05f) {
			return;
		}
		REENTRANT.set(true);
		try {
			target.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}
}
