package com.projecthero.mod.sentry;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.sentry.data.SentryState;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 Sentry: bridges the universal ability slots to the Sentry (or, in Void Form, the Void). H stays the power selector and N does
 * nothing for him. Shift is read server-side. During the takeover every key is dead ({@link #handle} ignores them: the Void has the
 * body).
 *
 * <pre>
 *   Sentry                                           Void Form
 *   R  Golden Strike     Shift+R  Sunbeam (hold)      R  Shadow Lash   Shift+R  Erase
 *   G  Molecular Grip (hold: tear)  Shift+G Unmake    G  Grasp
 *   Z  Sun Dive          Shift+Z  A Million Suns      Z  Void Rift     Shift+Z  Nightfall
 *   C  Healing Light     Shift+C  Sentinel Shield     X  Shadow Step
 *   X  Flight (2x X: sonic-boom dash)                 V  Return (below 90 Void)
 *   V  Calm (hold)       Shift+V  Let it out (50+ Void)
 * </pre>
 * Above 75 Void a Sentry attack key sometimes misfires into its Void move ({@link SentryConfig#MISFIRE_CHANCE}).
 */
public final class SentryAbilityManager {
	public static final String GOLDEN_STRIKE = "golden_strike";
	public static final String SUNBEAM = "sunbeam";
	public static final String GRIP = "molecular_grip";
	public static final String UNMAKE = "unmake";
	public static final String SUN_DIVE = "sun_dive";
	public static final String MILLION_SUNS = "million_suns";
	public static final String HEALING_LIGHT = "healing_light";
	public static final String SHIELD = "sentinel_shield";
	public static final String FLIGHT = "flight";
	public static final String DASH = "sonic_dash";
	public static final String CALM = "calm";
	public static final String LET_IT_OUT = "let_it_out";

	public static final String SHADOW_LASH = "shadow_lash";
	public static final String ERASE = "erase";
	public static final String GRASP = "grasp";
	public static final String VOID_RIFT = "void_rift";
	public static final String NIGHTFALL = "nightfall";
	public static final String SHADOW_STEP = "shadow_step";
	public static final String RETURN = "return";

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
		// above 75 Void the hand isn't always his: an attack key misfires into its Void move
		if (s.voidLevel >= SentryConfig.VOID_MISFIRE && isAttack(id) && !s.calming
				&& player.getRandom().nextFloat() < SentryConfig.MISFIRE_CHANCE) {
			String dark = misfireOf(id);
			Sentry.say(player, "message.projecthero.sentry.misfire", ChatFormatting.DARK_GRAY);
			VoidMoves.misfire(player, dark);
			return;
		}
		SentryMoves.use(player, id, slot);
	}

	/** The Sentry move a key sends. */
	public static String sentryMoveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? SUNBEAM : GOLDEN_STRIKE;
			case SLOT_2 -> shift ? UNMAKE : GRIP;
			case SLOT_3 -> FLIGHT;
			case SLOT_4 -> shift ? MILLION_SUNS : SUN_DIVE;
			case SLOT_5 -> shift ? LET_IT_OUT : CALM;
			case SLOT_6 -> shift ? SHIELD : HEALING_LIGHT;
			default -> null;
		};
	}

	/** The Void Form move a key sends (C has none). */
	public static String voidMoveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? ERASE : SHADOW_LASH;
			case SLOT_2 -> GRASP;
			case SLOT_3 -> SHADOW_STEP;
			case SLOT_4 -> shift ? NIGHTFALL : VOID_RIFT;
			case SLOT_5 -> RETURN;
			default -> null;
		};
	}

	static boolean isAttack(String id) {
		return GOLDEN_STRIKE.equals(id) || SUNBEAM.equals(id) || GRIP.equals(id) || SUN_DIVE.equals(id) || MILLION_SUNS.equals(id);
	}

	/** What a misfiring Sentry key does instead. */
	static String misfireOf(String id) {
		return switch (id) {
			case GRIP -> GRASP;
			case SUN_DIVE, MILLION_SUNS -> VOID_RIFT;
			default -> SHADOW_LASH;
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
			case GOLDEN_STRIKE -> SentryConfig.STRIKE_COST;
			case SUNBEAM -> SentryConfig.BEAM_COST_PER_SECOND / 4f;
			case GRIP -> SentryConfig.GRIP_COST;
			case UNMAKE -> SentryConfig.UNMAKE_COST;
			case SUN_DIVE -> SentryConfig.DIVE_COST;
			case MILLION_SUNS -> SentryConfig.SUNS_COST;
			case HEALING_LIGHT -> SentryConfig.HEAL_COST;
			case SHIELD -> SentryConfig.SHIELD_COST;
			case DASH -> SentryConfig.DASH_COST;
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
			return s.voidLevel >= SentryConfig.LET_OUT_MIN_VOID && s.letOutLockedUntil <= player.level().getGameTime();
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
			case GOLDEN_STRIKE -> SentryConfig.STRIKE_COOLDOWN;
			case SUNBEAM -> SentryConfig.BEAM_COOLDOWN;
			case GRIP -> SentryConfig.GRIP_COOLDOWN;
			case UNMAKE -> SentryConfig.UNMAKE_COOLDOWN;
			case SUN_DIVE -> SentryConfig.DIVE_COOLDOWN;
			case MILLION_SUNS -> SentryConfig.SUNS_COOLDOWN;
			case HEALING_LIGHT -> SentryConfig.HEAL_COOLDOWN;
			case SHIELD -> SentryConfig.SHIELD_COOLDOWN;
			case FLIGHT, DASH -> SentryConfig.DASH_COOLDOWN;
			case SHADOW_LASH -> SentryConfig.LASH_COOLDOWN;
			case ERASE -> SentryConfig.ERASE_COOLDOWN;
			case GRASP -> SentryConfig.GRASP_COOLDOWN;
			case VOID_RIFT -> SentryConfig.RIFT_COOLDOWN;
			case NIGHTFALL -> SentryConfig.NIGHTFALL_COOLDOWN;
			case SHADOW_STEP -> SentryConfig.STEP_COOLDOWN;
			default -> 1;
		};
	}
}
