package com.herocraft.mod.ironman;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.ironman.data.TonyStarkState;
import com.herocraft.mod.ironman.item.IronManItems;
import com.herocraft.mod.ironman.suit.IronManSuit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The single server-side API for the Tony Stark Hero-Tier power and the Iron Man technology tree.
 * Nothing else pokes {@link TonyStarkState} directly. Every mutator re-saves via
 * {@link ServerPlayer#setAttached} so the change is persisted and synced to the owning client.
 *
 * <p>Peer to {@code com.herocraft.mod.hero.ExperimentalPowers} and {@code ThorPassives}: the Tony
 * Stark power lives in its own attachment, is granted server-authoritatively, and survives logout /
 * restart / world reload / dimension change / death for the exact same reason worthiness does
 * ({@code persistent} + {@code copyOnDeath}).
 */
public final class TonyStark {
	/** How much Fabricator/suit energy one plain Arc Reactor is worth when inserted into a machine. */
	public static final float ARC_REACTOR_ENERGY = 25_000f;

	private TonyStark() {
	}

	// ---------------- state ----------------

	public static TonyStarkState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.TONY_STARK_STATE);
	}

	private static void save(ServerPlayer player, TonyStarkState state) {
		player.setAttached(ModAttachments.TONY_STARK_STATE, state);
	}

	/**
	 * Whether the player has the Tony Stark power. Safe on the client too -- the attachment is synced
	 * target-only, so a client can read its own copy but a modified client cannot make the server
	 * believe it has the power (every gameplay check runs against the server-side attachment).
	 */
	public static boolean hasPower(Player player) {
		TonyStarkState s = player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		return s != null && s.hasPower;
	}

	// ---------------- granting the power ----------------

	/**
	 * Grant the permanent Tony Stark power. Returns false if the player already had it (the caller
	 * -- {@link com.herocraft.mod.ironman.item.ArcReactorItem} -- then leaves the Arc Reactor
	 * uncconsumed). On success: sets tech level 0, hands the player the Mark III Blueprint (spec
	 * section 20: gaining Tony Stark unlocks the Fabricator, basic components, and the Mark III
	 * blueprint), plays a technological activation cue, and briefly illuminates the player.
	 */
	public static boolean grant(ServerPlayer player) {
		if (hasPower(player)) {
			return false;
		}
		TonyStarkState s = state(player).copy();
		s.hasPower = true;
		s.techLevel = 0;
		save(player, s);

		// "changes 21": the power hands a single Blank Blueprint. Shift-right-click it to pick a mark's
		// blueprint -- Mark 1 first, every later mark unlocked only once the whole previous suit is built.
		ItemStack blueprint = new ItemStack(IronManItems.BLANK_BLUEPRINT);
		if (!player.getInventory().add(blueprint)) {
			player.drop(blueprint, false);
		}

		activationFx(player);
		return true;
	}

	/** Testing/admin only -- see {@link com.herocraft.mod.command.IronManCommand}. */
	public static void revoke(ServerPlayer player) {
		TonyStarkState s = state(player).copy();
		s.hasPower = false;
		s.techLevel = 0;
		s.activeSuit = "";
		s.builtSuits.clear();
		s.suitEnergy.clear();
		s.suitIntegrity.clear();
		s.abilityReadyAt.clear();
		save(player, s);
	}

	private static void activationFx(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.3f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 0.4f);
		// blue-white technological particles rising around the player
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
				60, 0.35, 0.9, 0.35, 0.04);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(),
				40, 0.4, 0.8, 0.4, 0.15);
		level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
		// brief illumination
		player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false, false));
		player.displayClientMessage(Component.translatable("message.herocraft.tony_stark.acquired")
				.withStyle(net.minecraft.ChatFormatting.AQUA), false);
	}

	// ---------------- technology progression ----------------

	public static int techLevel(ServerPlayer player) {
		return state(player).techLevel;
	}

	/** Raise the technology level to at least {@code level} (never lowers it). */
	public static void unlockTech(ServerPlayer player, int level) {
		TonyStarkState s = state(player);
		if (level <= s.techLevel) {
			return;
		}
		TonyStarkState c = s.copy();
		c.techLevel = level;
		save(player, c);
	}

	/** True once the player has built every piece of {@code suitId} (the bare id is in {@code builtSuits}). */
	public static boolean hasBuilt(ServerPlayer player, String suitId) {
		return state(player).builtSuits.contains(suitId);
	}

	/** "changes 21": the four piece names of a full suit. */
	private static final String[] PIECE_NAMES = { "helmet", "chestplate", "leggings", "boots" };

	/** "changes 21": true once all four per-piece markers for {@code suitId} are present. Equivalent to
	 *  {@link #hasBuilt} once the mark has been promoted, but also usable mid-build. */
	public static boolean hasFabricatedFullSuit(ServerPlayer player, String suitId) {
		TonyStarkState s = state(player);
		if (s.builtSuits.contains(suitId)) {
			return true;
		}
		for (String piece : PIECE_NAMES) {
			if (!s.builtSuits.contains(suitId + "/" + piece)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * "changes 21": record that the player has built one armour piece (a Stark Fabricator completion, or
	 * a Mark 1 piece crafted at a table). Stores a {@code suitId/piece} marker; when the fourth piece of
	 * a mark lands the bare {@code suitId} is added too -- which unlocks the next mark's blueprint in the
	 * Blank Blueprint picker -- the tech level is advanced to that suit's tier for the systems that still
	 * read it (suit-up gate, passives), and the "%s developed" message fires once.
	 */
	public static void recordSuitPiece(ServerPlayer player, String suitId, net.minecraft.world.item.ArmorItem.Type type) {
		String key = suitId + "/" + type.getName();
		if (state(player).builtSuits.contains(key)) {
			return;
		}
		TonyStarkState c = state(player).copy();
		c.builtSuits.add(key);
		boolean nowComplete = true;
		for (String piece : PIECE_NAMES) {
			if (!c.builtSuits.contains(suitId + "/" + piece)) {
				nowComplete = false;
				break;
			}
		}
		boolean promoted = false;
		if (nowComplete && c.builtSuits.add(suitId)) {
			promoted = true;
			IronManSuit suit = com.herocraft.mod.ironman.suit.IronManSuits.byId(suitId);
			if (suit != null && suit.techLevel() > c.techLevel) {
				c.techLevel = suit.techLevel();
			}
		}
		save(player, c);
		if (promoted) {
			IronManSuit suit = com.herocraft.mod.ironman.suit.IronManSuits.byId(suitId);
			player.displayClientMessage(Component.translatable("message.herocraft.ironman.suit_developed",
					suit != null ? Component.translatable(suit.nameKey()) : Component.literal(suitId))
					.withStyle(net.minecraft.ChatFormatting.GOLD), false);
		}
	}

	/**
	 * Records that the player has a completed set of {@code suit} -- marks all four pieces built,
	 * promotes the mark into {@code builtSuits} and advances the tech level. Convenience entry point
	 * for commands and tests; the natural in-game path is four {@link #recordSuitPiece} calls.
	 */
	public static void markBuilt(ServerPlayer player, IronManSuit suit) {
		TonyStarkState c = state(player).copy();
		for (String piece : PIECE_NAMES) {
			c.builtSuits.add(suit.id() + "/" + piece);
		}
		boolean isNew = c.builtSuits.add(suit.id());
		if (suit.techLevel() > c.techLevel) {
			c.techLevel = suit.techLevel();
		}
		save(player, c);
		if (isNew) {
			player.displayClientMessage(Component.translatable("message.herocraft.ironman.suit_developed",
					Component.translatable(suit.nameKey())).withStyle(net.minecraft.ChatFormatting.GOLD), false);
		}
	}

	/** Suit ids the player has fully built (bare ids only -- the per-piece markers are filtered out). */
	public static java.util.Set<String> builtSuitIds(ServerPlayer player) {
		java.util.Set<String> out = new java.util.HashSet<>();
		for (String entry : state(player).builtSuits) {
			if (entry.indexOf('/') < 0) {
				out.add(entry);
			}
		}
		return out;
	}

	/**
	 * "changes 21": the player chose {@code suitId} in the Blank Blueprint picker. Re-validates the
	 * unlock (whole previous mark's suit built), consumes one Blank Blueprint from the inventory and
	 * hands over that mark's blueprint.
	 */
	public static void stampBlueprint(ServerPlayer player, String suitId) {
		if (!hasPower(player)) {
			return;
		}
		net.minecraft.world.item.Item blueprint = IronManItems.blueprintFor(suitId);
		if (blueprint == null) {
			return;
		}
		IronManSuit suit = com.herocraft.mod.ironman.suit.IronManSuits.byId(suitId);
		Component markName = suit != null ? Component.translatable(suit.nameKey()) : Component.literal(suitId);
		String prereq = IronManItems.prerequisiteSuit(suitId);
		if (prereq != null && !hasFabricatedFullSuit(player, prereq)) {
			IronManSuit pre = com.herocraft.mod.ironman.suit.IronManSuits.byId(prereq);
			player.displayClientMessage(Component.translatable("message.herocraft.ironman.blueprint_locked",
					markName, pre != null ? Component.translatable(pre.nameKey()) : Component.literal(prereq))
					.withStyle(net.minecraft.ChatFormatting.RED), true);
			return;
		}
		ItemStack blank = null;
		for (ItemStack candidate : player.getInventory().items) {
			if (candidate.is(IronManItems.BLANK_BLUEPRINT)) {
				blank = candidate;
				break;
			}
		}
		if (blank == null) {
			return;
		}
		blank.shrink(1);
		ItemStack out = new ItemStack(blueprint);
		if (!player.getInventory().add(out)) {
			player.drop(out, false);
		}
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
				SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.4f);
		player.displayClientMessage(Component.translatable("message.herocraft.ironman.blueprint_stamped", markName)
				.withStyle(net.minecraft.ChatFormatting.AQUA), true);
	}

	// ---------------- active suit ----------------

	public static String activeSuitId(ServerPlayer player) {
		return state(player).activeSuit;
	}

	public static void setActiveSuit(ServerPlayer player, String suitId) {
		TonyStarkState c = state(player).copy();
		c.activeSuit = suitId == null ? "" : suitId;
		save(player, c);
	}

	// ---------------- synced suit timers ("changes 15") ----------------

	/** Set the absolute game-time a Mark 1 timed-flight burst ends (synced for the HUD bar). */
	public static void setTimedFlightUntil(ServerPlayer player, long gameTime) {
		TonyStarkState c = state(player).copy();
		c.timedFlightUntil = gameTime;
		save(player, c);
	}

	/** Set the absolute game-time the Mark 4 wrist laser stops firing (synced). */
	public static void setWristLaserUntil(ServerPlayer player, long gameTime) {
		TonyStarkState c = state(player).copy();
		c.wristLaserUntil = gameTime;
		save(player, c);
	}

	/** Set the absolute game-time a Mark 4 systems-overload lockout ends (synced for the HUD bar). */
	public static void setOverloadUntil(ServerPlayer player, long gameTime) {
		TonyStarkState c = state(player).copy();
		c.overloadUntil = gameTime;
		save(player, c);
	}

	public static boolean overloaded(ServerPlayer player) {
		return player.level().getGameTime() < state(player).overloadUntil;
	}

	public static boolean wristLaserSpent(ServerPlayer player, String suitId) {
		return state(player).wristLaserSpent.contains(suitId);
	}

	/** "changes 16": which ability the Mark 7 weapon wheel has bound to slot 3 (X). */
	public static String weaponWheelChoice(ServerPlayer player) {
		return state(player).weaponWheelChoice;
	}

	public static void setWeaponWheelChoice(ServerPlayer player, String abilityId) {
		if (abilityId == null || abilityId.equals(state(player).weaponWheelChoice)) {
			return;
		}
		TonyStarkState c = state(player).copy();
		c.weaponWheelChoice = abilityId;
		save(player, c);
	}

	/** "changes 16": absolute game-time a Mark 7 supersonic-flight burst ends (transient, server-only). */
	public static void setSupersonicUntil(ServerPlayer player, long gameTime) {
		state(player).supersonicUntil = gameTime; // transient -- direct mutation is fine, no sync needed
	}

	// ---------------- Protocol Phoenix ("changes 17") ----------------

	/** True if the emergency resurrection ability is off cooldown. */
	public static boolean phoenixReady(ServerPlayer player) {
		return player.level().getGameTime() >= state(player).phoenixReadyAt;
	}

	/** Ticks left on the Protocol Phoenix cooldown (0 when ready). */
	public static long phoenixCooldownRemaining(ServerPlayer player) {
		return Math.max(0L, state(player).phoenixReadyAt - player.level().getGameTime());
	}

	/** Start (or clear, with {@code gameTime <= now}) the persistent Protocol Phoenix cooldown. */
	public static void setPhoenixReadyAt(ServerPlayer player, long gameTime) {
		TonyStarkState c = state(player).copy();
		c.phoenixReadyAt = gameTime;
		save(player, c);
	}

	/** True while the player is in the Protocol Phoenix incapacitated "emergency suit inbound" state. */
	public static boolean phoenixEmergency(ServerPlayer player) {
		return player.level().getGameTime() < state(player).phoenixEmergencyUntil;
	}

	public static void setSuitAir(ServerPlayer player, float fraction) {
		float clamped = Math.max(0f, Math.min(1f, fraction));
		if (Math.abs(state(player).suitAir - clamped) < 1.0E-4f) {
			return;
		}
		TonyStarkState c = state(player).copy();
		c.suitAir = clamped;
		save(player, c);
	}

	public static void setWristLaserSpent(ServerPlayer player, String suitId, boolean spent) {
		TonyStarkState s = state(player);
		if (s.wristLaserSpent.contains(suitId) == spent) {
			return;
		}
		TonyStarkState c = s.copy();
		if (spent) {
			c.wristLaserSpent.add(suitId);
		} else {
			c.wristLaserSpent.remove(suitId);
		}
		save(player, c);
	}

	// ---------------- iron man ability cooldowns (absolute ready-at game time) ----------------

	public static boolean abilityReady(ServerPlayer player, String suitId, String abilityId) {
		Long readyAt = state(player).abilityReadyAt.get(suitId + "/" + abilityId);
		return readyAt == null || player.level().getGameTime() >= readyAt;
	}

	public static int abilityCooldownRemaining(ServerPlayer player, String suitId, String abilityId) {
		Long readyAt = state(player).abilityReadyAt.get(suitId + "/" + abilityId);
		return readyAt == null ? 0 : (int) Math.max(0L, readyAt - player.level().getGameTime());
	}

	public static void triggerCooldown(ServerPlayer player, String suitId, String abilityId, int ticks) {
		if (ticks <= 0) {
			return;
		}
		TonyStarkState c = state(player).copy();
		c.abilityReadyAt.put(suitId + "/" + abilityId, player.level().getGameTime() + ticks);
		save(player, c);
	}

	// ---------------- lifecycle ----------------

	/** Nothing to reconcile for the bare power itself yet; suit passives reconcile via
	 *  {@link com.herocraft.mod.ironman.IronManArmor#enforce}. Kept as a hook. */
	public static void onPlayerJoin(ServerPlayer player) {
	}

	public static void onPlayerRespawn(ServerPlayer player) {
	}
}
