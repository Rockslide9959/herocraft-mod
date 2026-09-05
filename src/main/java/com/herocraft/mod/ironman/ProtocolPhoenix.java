package com.herocraft.mod.ironman;

import java.util.Comparator;
import java.util.List;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.ironman.data.TonyStarkState;
import com.herocraft.mod.ironman.suit.IronManSuit;
import com.herocraft.mod.ironman.suit.IronManSuitCall;
import com.herocraft.mod.ironman.suit.IronManSuitUpManager;
import com.herocraft.mod.ironman.suit.IronManSuits;
import com.herocraft.mod.network.IronManSuitListPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * <b>Protocol Phoenix</b> ("changes 17") -- a passive emergency-resurrection ability on the Tony Stark
 * power.
 *
 * <p>When a Tony Stark player would otherwise die and is <em>not</em> already wearing a complete Iron
 * Man suit, and Protocol Phoenix is off its 20-minute cooldown, the death is prevented (a Totem of
 * Undying-style pop) and the player is dropped into an incapacitated "emergency suit inbound" state:
 * invulnerable, blind, effectively frozen in place, and locked out of every action. Meanwhile the
 * system picks the best viable suit the player owns -- from their inventory or from a bound Suit
 * Platform -- and calls it in through the existing recall / suit-up systems. Once the complete suit
 * has equipped the player is restored to 5 hearts, the emergency state ends, and the suit pays the
 * cost (20% of its max energy, 10% of its max integrity).
 *
 * <p>All server-authoritative. The cooldown lives on the synced, persistent {@link TonyStarkState} so
 * it survives death / relog / dimension change / dropping armour. Fails safely: no viable suit means a
 * normal death; a suit that never arrives is cleaned up by a failsafe timeout with the player left
 * alive but vulnerable.
 */
public final class ProtocolPhoenix {
	/** 20 minutes ("changes 19", was 5). */
	public static final int COOLDOWN_TICKS = 1200 * 20;
	/** Hard cap on the incapacitated state -- if the suit has not equipped by now, clean up and release. */
	private static final int FAILSAFE_TICKS = 30 * 20;
	/** A suit is only viable for Phoenix if it still holds at least this fraction of its energy... */
	private static final float MIN_ENERGY_FRACTION = 0.25f;
	/** ...and at least this fraction of its integrity. */
	private static final float MIN_INTEGRITY_FRACTION = 0.10f;
	/** Phoenix drains this fraction of the suit's MAX energy on a successful revive. */
	private static final float ENERGY_COST_FRACTION = 0.20f;
	/** Phoenix removes this fraction of the suit's MAX integrity on a successful revive. */
	private static final float INTEGRITY_COST_FRACTION = 0.10f;
	/** Health (half-hearts) restored on a successful revive, capped at the player's real max. */
	private static final float REVIVE_HEALTH = 10.0f;

	private ProtocolPhoenix() {
	}

	/** True while the player is frozen waiting for the emergency suit -- used to veto input everywhere. */
	public static boolean incapacitated(ServerPlayer player) {
		return TonyStark.phoenixEmergency(player);
	}

	// ---------------- activation (from ServerLivingEntityEvents.ALLOW_DEATH) ----------------

	/**
	 * Called the instant a player would die. Returns true if Protocol Phoenix took over (the caller
	 * then cancels the death and skips ordinary suit recovery); false to let the death proceed.
	 */
	public static boolean tryActivate(ServerPlayer player, DamageSource source) {
		if (!TonyStark.hasPower(player)) {
			return false;
		}
		// Can't trigger recursively, and truly un-survivable damage always kills.
		if (TonyStark.phoenixEmergency(player)
				|| source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return false;
		}
		// Condition: does nothing if the player is already in a full suit.
		String worn = IronManArmor.wornSuitId(player);
		if (worn != null && IronManArmor.wearingFullSuit(player, worn)) {
			return false;
		}
		if (!TonyStark.phoenixReady(player)) {
			return false;
		}

		Pick pick = selectBestSuit(player);
		if (pick == null) {
			HeroCraftMod.LOGGER.debug("[Phoenix] {} would die but no viable emergency suit -- normal death",
					player.getGameProfile().getName());
			return false;
		}

		long now = player.level().getGameTime();
		ServerLevel level = (ServerLevel) player.level();

		// Prevent the death.
		player.setHealth(Math.max(1.0f, Math.min(2.0f, player.getMaxHealth())));
		player.clearFire();
		player.setDeltaMovement(0, 0, 0);
		player.hurtMarked = true;
		player.fallDistance = 0f;
		player.removeAllEffects();

		// Totem-of-Undying style activation.
		level.broadcastEntityEvent(player, (byte) 35);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);

		TonyStarkState s = TonyStark.state(player);
		s.phoenixEmergencyUntil = now + FAILSAFE_TICKS;
		s.phoenixSuitId = pick.suitId();
		s.phoenixSuitSource = pick.source();
		// Cooldown starts on a successful activation (the death was prevented), per spec.
		TonyStark.setPhoenixReadyAt(player, now + COOLDOWN_TICKS);

		beginRecall(player, pick);

		player.displayClientMessage(Component.translatable("message.herocraft.ironman.phoenix_activated")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
		HeroCraftMod.LOGGER.info("[Phoenix] {} activated -- calling {} (source {})",
				player.getGameProfile().getName(), pick.suitId(),
				pick.source() == IronManSuitListPayload.SOURCE_INVENTORY ? "inventory" : "platform");
		return true;
	}

	private static void beginRecall(ServerPlayer player, Pick pick) {
		try {
			if (pick.source() == IronManSuitListPayload.SOURCE_INVENTORY
					&& IronManSuitCall.fullyInInventory(player, pick.suitId())) {
				IronManSuitUpManager.beginSuitUp(player, pick.suitId());
			} else {
				IronManSuitCall.execute(player, pick.suitId(), pick.source());
			}
		} catch (RuntimeException e) {
			HeroCraftMod.LOGGER.warn("[Phoenix] recall of {} for {} threw -- failsafe will clean up",
					pick.suitId(), player.getGameProfile().getName(), e);
		}
	}

	// ---------------- per-tick (from IronManSuitTicker) ----------------

	public static void tick(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		if (s.phoenixEmergencyUntil == 0L) {
			return;
		}
		long now = player.level().getGameTime();
		String suitId = s.phoenixSuitId;
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);

		// Success: the complete suit is on and the suit-up sequence has finished.
		if (suit != null && IronManArmor.wearingFullSuit(player, suitId)
				&& !IronManSuitUpManager.inTransition(player)) {
			succeed(player, suit);
			return;
		}

		// Failsafe: nothing has arrived in time.
		if (now >= s.phoenixEmergencyUntil) {
			if (IronManSuitUpManager.inTransition(player) || IronManSuitCall.hasPendingCall(player)) {
				// Something is still genuinely in progress -- give it a little longer rather than abort.
				s.phoenixEmergencyUntil = now + 60;
			} else {
				failSafe(player);
				return;
			}
		}

		applyIncapacitation(player);
	}

	private static void applyIncapacitation(ServerPlayer player) {
		// Effectively unable to move: extreme Slowness + Blindness, refreshed every tick (hidden).
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 250, true, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0, true, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 250, true, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 250, true, false, false));
		// Pin them: kill horizontal drift, veto any upward (jump) velocity.
		var dm = player.getDeltaMovement();
		player.setDeltaMovement(0, Math.min(0.0, dm.y), 0);
		player.hurtMarked = true;
		player.fallDistance = 0f;
		player.setJumping(false);
		if (player.getAbilities().flying && !player.getAbilities().instabuild) {
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
		if (player.tickCount % 6 == 0) {
			player.displayClientMessage(Component.translatable("message.herocraft.ironman.phoenix_incapacitated")
					.withStyle(ChatFormatting.GRAY), true);
			((ServerLevel) player.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
					player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
		}
	}

	private static void succeed(ServerPlayer player, IronManSuit suit) {
		TonyStarkState s = TonyStark.state(player);
		s.phoenixEmergencyUntil = 0L;
		s.phoenixSuitId = "";
		clearEmergencyEffects(player);

		player.setHealth(Math.min(REVIVE_HEALTH, player.getMaxHealth()));

		// Costs are on the suit's MAX capacity, never below zero (setEnergy / setIntegrity clamp).
		IronManEnergy.addEnergy(player, suit.id(), -suit.energyCapacity() * ENERGY_COST_FRACTION);
		IronManEnergy.damageIntegrity(player, suit.id(), suit.maxIntegrity() * INTEGRITY_COST_FRACTION);

		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.4f);
		player.displayClientMessage(Component.translatable("message.herocraft.ironman.phoenix_restored")
				.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
		HeroCraftMod.LOGGER.info("[Phoenix] {} restored in {}", player.getGameProfile().getName(), suit.id());
	}

	private static void failSafe(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		s.phoenixEmergencyUntil = 0L;
		s.phoenixSuitId = "";
		clearEmergencyEffects(player);
		// The death was already prevented -- leave the player alive but vulnerable rather than freezing
		// them forever. Give them a sliver more than the 1 HP they were pinned at.
		player.setHealth(Math.max(player.getHealth(), Math.min(6.0f, player.getMaxHealth())));
		player.displayClientMessage(Component.translatable("message.herocraft.ironman.phoenix_failed")
				.withStyle(ChatFormatting.RED), true);
		HeroCraftMod.LOGGER.warn("[Phoenix] {} failsafe -- emergency suit never completed suit-up",
				player.getGameProfile().getName());
	}

	private static void clearEmergencyEffects(ServerPlayer player) {
		player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		player.removeEffect(MobEffects.BLINDNESS);
		player.removeEffect(MobEffects.WEAKNESS);
		player.removeEffect(MobEffects.DIG_SLOWDOWN);
		player.setTicksFrozen(0);
	}

	/** Cleanup for the join / respawn safety nets: a fresh player instance must never be left frozen. */
	public static void clearEmergency(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		if (s.phoenixEmergencyUntil != 0L || !s.phoenixSuitId.isEmpty()) {
			s.phoenixEmergencyUntil = 0L;
			s.phoenixSuitId = "";
			clearEmergencyEffects(player);
		}
	}

	// ---------------- suit selection ----------------

	private record Pick(String suitId, int source) {
	}

	/**
	 * The best viable suit for an emergency call. Considers both inventory sets and suits on the
	 * player's own bound platforms (the same list the C-key picker builds), then filters to those with
	 * enough integrity and energy and ranks them: highest mark, then highest remaining integrity, then
	 * highest remaining energy, then the greatest combined integrity + energy percentage.
	 */
	private static Pick selectBestSuit(ServerPlayer player) {
		List<IronManSuitListPayload.Option> options = IronManSuitCall.assemblableSuits(player);
		return options.stream()
				.filter(o -> IronManSuits.byId(o.suitId()) != null)
				.filter(o -> o.integrityFrac() >= MIN_INTEGRITY_FRACTION)
				.filter(o -> o.energyFrac() >= MIN_ENERGY_FRACTION)
				.max(Comparator
						.comparingInt((IronManSuitListPayload.Option o) -> IronManSuits.byId(o.suitId()).markNumber())
						.thenComparingDouble(IronManSuitListPayload.Option::integrityFrac)
						.thenComparingDouble(IronManSuitListPayload.Option::energyFrac)
						.thenComparingDouble(o -> o.integrityFrac() + o.energyFrac()))
				.map(o -> new Pick(o.suitId(), o.source()))
				.orElse(null);
	}
}
