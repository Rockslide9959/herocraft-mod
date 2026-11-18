package com.projecthero.mod.sentry;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.sentry.data.SentryState;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 Sentry: bridges the universal ability slots to the Sentry (or, merged, the Void). Shift is read server-side. During the takeover
 * every key is dead ({@link #handle} ignores them: the Void has the body). Round 3 (2026-11-18, user: "Make Solar Gaze his G move ... Make
 * let it out his H ability ... Make V his shield ability. Make shift+G his disassemble ability. Make shift+R an aoe ability ... if sonic
 * boom is used while flying ... makes player fly 50% faster ... Remove the players calm ability and replace it with a different ability"):
 *
 * <pre>
 *   Sentry                                               The Void (merged)
 *   H        Let It Out (the trial)                       H        Return to the Sentry (below 90 Void)
 *   R        Golden Fury (combo, 3rd = Haymaker)          R        Tendril Lash (combo, 3rd = Impale)
 *   Shift+R  Solar Flare (AoE burst)                      Shift+R  Erase
 *   G        Solar Gaze (hold: eye beams)                 G        Shadow Pool
 *   Shift+G  Disassemble (hold: tear apart; let go: throw)Shift+G  Swallow (the void sphere)
 *   Z        Sunfall (leap / dive slam)                   Z        Nightmare
 *   Shift+Z  A Million Suns (4 s wind-up)                 Shift+Z  Shadow Eclipse
 *   C        Golden Guardian                              C        Blot Out
 *   Shift+C  Skyward Hurl                                 Shift+C  Shadow Clones
 *   X        Sonic Boom (dash); flying: Flight Boost      X        Shadow Step (blink)
 *   V        Molecular Shield                             V        Umbral Shroud
 *   Shift+V  Golden Sense                                 Shift+V  Shadow Meld
 *   2x Space  Flight (both forms)
 * </pre>
 * H reaches the server through {@code SentryActionPayload.LET_IT_OUT} ({@link #pressH}); Shift+H still opens the power wheel. Above 75
 * Void a Sentry attack key sometimes misfires into a Void move ({@link SentryConfig#MISFIRE_CHANCE}).
 */
public final class SentryAbilityManager {
	// the Sentry
	public static final String GOLDEN_FURY = "golden_fury";
	public static final String SOLAR_FLARE = "solar_flare";
	public static final String SOLAR_GAZE = "solar_gaze";
	public static final String DISASSEMBLE = "disassemble";
	public static final String SKYWARD_HURL = "skyward_hurl";
	public static final String SUNFALL = "sunfall";
	public static final String MILLION_SUNS = "million_suns";
	public static final String GOLDEN_GUARDIAN = "golden_guardian";
	public static final String MOLECULAR_SHIELD = "molecular_shield";
	public static final String GOLDEN_SENSE = "golden_sense";
	public static final String FLIGHT = "flight";
	public static final String DASH = "sonic_dash";
	public static final String FLIGHT_BOOST = "flight_boost";
	public static final String LET_IT_OUT = "let_it_out";

	// the Void
	public static final String TENDRIL_LASH = "tendril_lash";
	public static final String ERASE = "erase";
	public static final String SHADOW_POOL = "shadow_pool";
	public static final String SWALLOW = "void_sphere";
	public static final String NIGHTMARE = "nightmare";
	public static final String SHADOW_ECLIPSE = "shadow_eclipse";
	public static final String BLOT_OUT = "blot_out";
	public static final String SHADOW_CLONES = "shadow_clones";
	public static final String SHADOW_STEP = "shadow_step";
	public static final String UMBRAL_SHROUD = "umbral_shroud";
	public static final String SHADOW_MELD = "shadow_meld";
	public static final String RETURN = "return";

	/** Every Void attack (the takeover picks from these). */
	public static final String[] VOID_ATTACKS = { TENDRIL_LASH, ERASE, SHADOW_POOL, SWALLOW, NIGHTMARE, SHADOW_ECLIPSE, BLOT_OUT, SHADOW_CLONES };

	private SentryAbilityManager() {
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Sentry.hasPower(player)) {
			return false;
		}
		if (Sentry.inTakeover(player)) {
			return true; // the keys are the Void's (dead) while it has the body
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (slot.ordinal() >= AbilitySlot.CORE_COUNT || !player.isAlive() || player.isSpectator()) {
			return;
		}
		SentryState s = Sentry.state(player);
		if (s.inTakeover()) {
			return;
		}
		boolean shift = player.isShiftKeyDown();
		if (!pressed) {
			SentryMoves.release(player, slot);
			return;
		}
		if (s.voidForm) {
			String id = voidMoveFor(slot, shift);
			if (id != null) {
				VoidMoves.use(player, id);
			}
			return;
		}
		String id = sentryMoveFor(slot, shift);
		if (id == null) {
			return;
		}
		// above 75 Void the hand isn't always his: an attack key misfires into a Void move
		if (s.voidLevel >= SentryConfig.VOID_MISFIRE && isAttack(id) && player.getRandom().nextFloat() < SentryConfig.MISFIRE_CHANCE) {
			String dark = misfireOf(id);
			Sentry.say(player, "message.projecthero.sentry.misfire", ChatFormatting.DARK_GRAY);
			VoidMoves.misfire(player, dark);
			return;
		}
		SentryMoves.use(player, id, slot);
	}

	/**
	 * H (round 3, user: "Make let it out his H ability"): as the Sentry, Let It Out (the trial); merged with the Void, back to the Sentry
	 * (only below 90 Void). Nothing in the takeover, or while A Million Suns charges. From {@code SentryActionPayload.LET_IT_OUT}.
	 */
	public static void pressH(ServerPlayer player) {
		if (!Sentry.hasPower(player) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		SentryState s = Sentry.state(player);
		if (s.inTakeover() || s.sunsStart != 0L) {
			return;
		}
		if (s.voidForm) {
			VoidMoves.returnToSentry(player);
		} else {
			LetItOut.begin(player);
		}
	}

	/** The Sentry move a key sends. */
	public static String sentryMoveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? SOLAR_FLARE : GOLDEN_FURY;
			case SLOT_2 -> shift ? DISASSEMBLE : SOLAR_GAZE;
			case SLOT_3 -> DASH; // X: the dash on the ground, the Flight Boost while flying; flight is double-tap Space
			case SLOT_4 -> shift ? MILLION_SUNS : SUNFALL;
			case SLOT_5 -> shift ? GOLDEN_SENSE : MOLECULAR_SHIELD;
			case SLOT_6 -> shift ? SKYWARD_HURL : GOLDEN_GUARDIAN;
			default -> null;
		};
	}

	/** The Void move a key sends. */
	public static String voidMoveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? ERASE : TENDRIL_LASH;
			case SLOT_2 -> shift ? SWALLOW : SHADOW_POOL;
			case SLOT_3 -> SHADOW_STEP;
			case SLOT_4 -> shift ? SHADOW_ECLIPSE : NIGHTMARE;
			case SLOT_5 -> shift ? SHADOW_MELD : UMBRAL_SHROUD;
			case SLOT_6 -> shift ? SHADOW_CLONES : BLOT_OUT;
			default -> null;
		};
	}

	static boolean isAttack(String id) {
		return GOLDEN_FURY.equals(id) || SOLAR_FLARE.equals(id) || SOLAR_GAZE.equals(id) || DISASSEMBLE.equals(id) || SKYWARD_HURL.equals(id)
				|| SUNFALL.equals(id) || MILLION_SUNS.equals(id);
	}

	/** What a misfiring Sentry key does instead. */
	static String misfireOf(String id) {
		return switch (id) {
			case SOLAR_GAZE, DISASSEMBLE, SKYWARD_HURL -> SHADOW_POOL;
			case SUNFALL, MILLION_SUNS -> NIGHTMARE;
			default -> TENDRIL_LASH;
		};
	}

	/** The HUD's ids per slot: plain, then Shift (one id when Shift does the same). */
	public static String[] idsOf(AbilitySlot slot, boolean voidForm) {
		String a = voidForm ? voidMoveFor(slot, false) : sentryMoveFor(slot, false);
		String b = voidForm ? voidMoveFor(slot, true) : sentryMoveFor(slot, true);
		if (a == null) {
			return new String[0];
		}
		return a.equals(b) ? new String[] { a } : new String[] { a, b };
	}

	/** Sun a move costs (the HUD greys a move he can't pay for). Void moves cost no Sun. */
	public static float cost(String id) {
		return switch (id) {
			case GOLDEN_FURY -> SentryConfig.FURY_COST;
			case SOLAR_FLARE -> SentryConfig.FLARE_COST;
			case SOLAR_GAZE -> SentryConfig.GAZE_COST_PER_SECOND / 4f;
			case DISASSEMBLE -> SentryConfig.UNMAKE_COST;
			case SKYWARD_HURL -> SentryConfig.HURL_COST;
			case SUNFALL -> SentryConfig.SUNFALL_COST;
			case MILLION_SUNS -> SentryConfig.SUNS_COST;
			case GOLDEN_GUARDIAN -> SentryConfig.GUARDIAN_COST;
			case MOLECULAR_SHIELD -> SentryConfig.MSHIELD_COST;
			case GOLDEN_SENSE -> SentryConfig.SENSE_COST;
			case DASH -> SentryConfig.DASH_COST;
			case FLIGHT_BOOST -> SentryConfig.BOOST_COST;
			default -> 0f;
		};
	}

	/** Usable as far as the meters go (the HUD's grey-out). Client-safe. */
	public static boolean allowed(Player player, String id) {
		SentryState s = Sentry.peek(player);
		if (s == null) {
			return false;
		}
		if (LET_IT_OUT.equals(id)) {
			return s.letOutLockedUntil <= player.level().getGameTime() && s.letOutTrialStart == 0L;
		}
		if (RETURN.equals(id)) {
			return s.voidLevel < SentryConfig.RETURN_MAX_VOID;
		}
		return player.getAbilities().instabuild || s.sun + 1.0e-3f >= cost(id);
	}

	public static int cooldownRemaining(Player player, String id) {
		return Sentry.cooldownRemaining(player, id);
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case GOLDEN_FURY -> SentryConfig.FURY_COOLDOWN;
			case SOLAR_FLARE -> SentryConfig.FLARE_COOLDOWN;
			case SOLAR_GAZE -> SentryConfig.GAZE_COOLDOWN;
			case DISASSEMBLE -> SentryConfig.UNMAKE_COOLDOWN;
			case SKYWARD_HURL -> SentryConfig.HURL_COOLDOWN;
			case SUNFALL -> SentryConfig.SUNFALL_COOLDOWN;
			case MILLION_SUNS -> SentryConfig.SUNS_COOLDOWN;
			case GOLDEN_GUARDIAN -> SentryConfig.GUARDIAN_COOLDOWN;
			case MOLECULAR_SHIELD -> SentryConfig.MSHIELD_COOLDOWN;
			case GOLDEN_SENSE -> SentryConfig.SENSE_COOLDOWN;
			case DASH -> SentryConfig.DASH_COOLDOWN;
			case FLIGHT_BOOST -> SentryConfig.BOOST_COOLDOWN;
			case TENDRIL_LASH -> SentryConfig.LASH_COOLDOWN;
			case ERASE -> SentryConfig.ERASE_COOLDOWN;
			case SHADOW_POOL -> SentryConfig.POOL_COOLDOWN;
			case SWALLOW -> SentryConfig.SPHERE_COOLDOWN;
			case NIGHTMARE -> SentryConfig.NIGHTMARE_COOLDOWN;
			case SHADOW_ECLIPSE -> SentryConfig.ECLIPSE_COOLDOWN;
			case BLOT_OUT -> SentryConfig.BLOT_COOLDOWN;
			case SHADOW_CLONES -> SentryConfig.CLONE_COOLDOWN;
			case SHADOW_STEP -> SentryConfig.STEP_COOLDOWN;
			case UMBRAL_SHROUD -> SentryConfig.SHROUD_COOLDOWN;
			case SHADOW_MELD -> SentryConfig.MELD_COOLDOWN;
			default -> 1;
		};
	}
}
