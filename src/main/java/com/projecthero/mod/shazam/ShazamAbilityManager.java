package com.projecthero.mod.shazam;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Shazam: bridges the universal ability slots to Shazam while he is transformed (champion or family).
 * {@link com.projecthero.mod.hero.AbilityRouter} hands him the slots when {@link #hasContext} is true -- early, like Moon Knight's
 * suit, so a family member's own power steps aside while the borrowed lightning is on. Shift is read server-side.
 *
 * <pre>
 *   R  Lightning Hand      Shift+R  Call the Lightning
 *   G  Hercules Punch      Shift+G  Atlas Lift (again: hurl)
 *   Z  Zeus Storm          Shift+Z  Eternity Strike (not the family)
 *   X  Mercury Dash        Shift+X  Thunder Tackle
 *   C  Thunderclap         Shift+C  Achilles Stand
 *   V  Wisdom of Solomon   Shift+V  Share the Power (the champion only)
 * </pre>
 * The word is chat; flight is double-tap Space. H stays the power selector and N does nothing for him.
 */
public final class ShazamAbilityManager {
	public static final String LIGHTNING_HAND = "lightning_hand";
	public static final String CALL_LIGHTNING = "call_lightning";
	public static final String HERCULES_PUNCH = "hercules_punch";
	public static final String ATLAS_LIFT = "atlas_lift";
	public static final String ZEUS_STORM = "zeus_storm";
	public static final String ETERNITY_STRIKE = "eternity_strike";
	public static final String MERCURY_DASH = "mercury_dash";
	public static final String THUNDER_TACKLE = "thunder_tackle";
	public static final String THUNDERCLAP = "thunderclap";
	public static final String ACHILLES_STAND = "achilles_stand";
	public static final String WISDOM = "wisdom";
	public static final String SHARE_POWER = "share_power";

	private ShazamAbilityManager() {
	}

	static void clearSessionState() {
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Shazam.transformed(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	static void tick(ServerPlayer player) {
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (!pressed || slot.ordinal() >= AbilitySlot.CORE_COUNT) {
			return; // every move fires on the press; H and N are never moves
		}
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		String id = moveFor(slot, player.isShiftKeyDown());
		if (id != null) {
			ShazamMoves.use(player, id);
		}
	}

	/** The move a key sends. */
	public static String moveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? CALL_LIGHTNING : LIGHTNING_HAND;
			case SLOT_2 -> shift ? ATLAS_LIFT : HERCULES_PUNCH;
			case SLOT_3 -> shift ? THUNDER_TACKLE : MERCURY_DASH;
			case SLOT_4 -> shift ? ETERNITY_STRIKE : ZEUS_STORM;
			case SLOT_5 -> shift ? SHARE_POWER : WISDOM;
			case SLOT_6 -> shift ? ACHILLES_STAND : THUNDERCLAP;
			default -> null;
		};
	}

	/** The HUD's ids per slot: plain, then Shift. */
	public static String[] idsOf(AbilitySlot slot) {
		String a = moveFor(slot, false);
		return a == null ? new String[0] : new String[] { a, moveFor(slot, true) };
	}

	/** Lightning a move costs (the HUD greys a move he can't pay for). */
	public static float cost(String id) {
		return switch (id) {
			case LIGHTNING_HAND -> ShazamConfig.HAND_COST;
			case CALL_LIGHTNING -> ShazamConfig.CALL_COST;
			case HERCULES_PUNCH -> ShazamConfig.PUNCH_COST;
			case ATLAS_LIFT -> ShazamConfig.LIFT_COST;
			case ZEUS_STORM -> ShazamConfig.STORM_COST;
			case ETERNITY_STRIKE -> ShazamConfig.ETERNITY_COST;
			case THUNDER_TACKLE -> ShazamConfig.TACKLE_COST;
			case THUNDERCLAP -> ShazamConfig.CLAP_COST;
			case ACHILLES_STAND -> ShazamConfig.STAND_COST;
			default -> 0f;
		};
	}

	/** A family member can't use it (Eternity Strike, Share the Power). Client-safe. */
	public static boolean championOnly(String id) {
		return ETERNITY_STRIKE.equals(id) || SHARE_POWER.equals(id);
	}

	/** Usable as far as who he is goes (the HUD's grey-out). Client-safe. */
	public static boolean allowed(Player player, String id) {
		return !championOnly(id) || !Shazam.isFamily(player);
	}

	public static int cooldownRemaining(Player player, String id) {
		return Shazam.cooldownRemaining(player, id);
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case LIGHTNING_HAND -> ShazamConfig.HAND_COOLDOWN;
			case CALL_LIGHTNING -> ShazamConfig.CALL_COOLDOWN;
			case HERCULES_PUNCH -> ShazamConfig.PUNCH_COOLDOWN;
			case ATLAS_LIFT -> ShazamConfig.LIFT_COOLDOWN;
			case ZEUS_STORM -> ShazamConfig.STORM_COOLDOWN;
			case ETERNITY_STRIKE -> ShazamConfig.ETERNITY_COOLDOWN;
			case MERCURY_DASH -> ShazamConfig.DASH_COOLDOWN;
			case THUNDER_TACKLE -> ShazamConfig.TACKLE_COOLDOWN;
			case THUNDERCLAP -> ShazamConfig.CLAP_COOLDOWN;
			case ACHILLES_STAND -> ShazamConfig.STAND_COOLDOWN;
			case WISDOM -> ShazamConfig.WISDOM_COOLDOWN;
			case SHARE_POWER -> ShazamConfig.SHARE_COOLDOWN;
			default -> 1;
		};
	}
}
