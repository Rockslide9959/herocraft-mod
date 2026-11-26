package com.projecthero.mod.symbiote;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.SonicVulnerability;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.symbiote.entity.SymbioteSpikeEntity;
import com.projecthero.mod.symbiote.entity.SymbioteTendrilEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Normal Symbiote Host's abilities. Every ability is available whenever the player is bonded -- the
 * black suit does not have to be worn ({@link SymbioteVitalsManager#usable}) -- but a spent Biomass bar
 * locks them all until it recovers.
 *
 * <pre>
 *   R  Tendril Strike (30 blocks, can miss)        Shift+R  Tendril Sweep (cone, Slow 7 s)
 *   G  Symbiote Spike (one spike, 4 s)             Shift+G  Spike Fan (five spikes, 10 s)
 *   X  Symbiote Lunge (20 blocks, ram = 15 dmg)    Shift+X  Symbiote Grapple (30 blocks)
 *   Z  Tendril Barrage / Blade Slash               Shift+hold Z  Symbiote Onslaught (ultimate)
 *   V  Symbiote Blade (toggle)                     Shift+V  Symbiote Shield (toggle)
 *   C  Symbiote Spikes (Thorns toggle)             Shift+C  Tendril Grab (C again to throw)
 * </pre>
 *
 * <p>v0.17.5 moveset v2 (user-approved, {@code docs/SYMBIOTE_MOVESET_V2_DESIGN.md}): every move above is kept and these are added --
 * <pre>
 *   R  every 3rd Tendril Strike in a row is a Tendril Slam     hold R  Impaling Spear (charge, release)
 *   G                                                         hold G  Spike Rain (3 s on the aimed area)
 *   X  in the air (2.5+ blocks up): Dive Bomb
 *   Z                                                         hold Z  Ground Eruption (a travelling line)
 *   C  Maw Bite (replaces the Thorns toggle)                  hold C  Devour (under 25%: execute; else Maw Bite)
 *   V  Form: Normal -> Blade -> Thorns (one cycle instead of the Blade and Thorns toggles)
 * </pre>
 * Tap vs hold on R / G / Z / C (no Sneak): the press only notes the time; a release under {@link #HOLD_TICKS} is the tap move,
 * still down at {@link #HOLD_TICKS} the hold move starts (from {@link #serverTick}) and the release that follows does nothing
 * (except the Spear, whose release throws it). Sneak moves still fire on the press. Passive with the suit on: +1 block melee
 * reach and every 3rd punch in a row knocks back ({@link #onMeleeSwing}).
 *
 * <p>v0.17.5 moveset v3 ({@code SYMBIOTE_REFERENCE.md} "Moveset v3"): R / G 1 s, Shift+R 4 s, every Z 8 s; Shift+G is Spike Nova (a 6-block
 * ring); Thorns form hurts everything within 1.5 blocks and its Z is Spike Surge (3.5 blocks for 6 s); a tap of V cycles the Form, a
 * hold opens the client's Form wheel; the grab has no time limit -- C throws, left click crushes, Shift+C sets down (players and
 * squadmates too).
 *
 * <p>v0.13.19: every move comes out of the host's hands ({@link SymbioteHands}) and is drawn as a real living
 * tendril ({@link SymbioteTendrilEntity}) or spike ({@link SymbioteSpikeEntity}) rather than a particle line;
 * Tendril Strike and Tendril Barrage now fire whether or not something is in the sights, so they can miss;
 * each move plays a pose ({@link SymbioteAnim}). Tendril Grab moved from Shift+G to Shift+C to make room for
 * the Spike Fan.
 */
public final class SymbioteAbilityManager {
	// v0.17.5 moveset v3 (user: "R ... lower cooldown to 1 second", "lower cooldown of tendril sweep to 4 seconds", "lower cooldown of g
	// to 1 second", "lower cooldown of z move")
	public static final int CD_TENDRIL_STRIKE = 20;    // 1 s (was 1.5 s)
	public static final int CD_TENDRIL_SWEEP = 80;     // 4 s (was 10 s)
	private static final int CD_LEAP = 40;             // 2s
	public static final int CD_BARRAGE = 160;          // 8 s (was 15 s)
	public static final int CD_TENDRIL_GRAB = 100;     // 5s
	public static final int CD_SPIKE_SHOT = 20;        // 1 s (was 4 s)
	/** v0.17.5 moveset v3: Shift+G is Spike Nova now (replaces the Spike Fan); still on the Fan's own entry (spikeConeReadyAt). */
	public static final int CD_SPIKE_NOVA = 160;       // 8 s

	private static final int LEAP_NO_FALL_TICKS = 600;
	private static final float LEAP_RAM_DAMAGE = 15.0f;

	private static final double TENDRIL_STRIKE_RANGE = 30.0;
	private static final float TENDRIL_STRIKE_DAMAGE = 12.0f; // v0.17.7 City tier: was 15 (a 1 s spam move, under the Slam)
	private static final double SWEEP_RANGE = 9.0;
	private static final double SWEEP_CONE_DOT = 0.35;
	private static final float SWEEP_DAMAGE = 11.0f; // v0.17.7 City tier: was 8 (+0-2 random)

	private static final double GRAB_RANGE = 15.0;
	private static final double GRAB_HOLD_DISTANCE = 2.6;
	/** v0.17.5 moveset v3: no time limit any more -- this is only how long each WRAP effect / tendril top-up lasts. */
	private static final int GRAB_WRAP_TICKS = 70;
	private static final float GRAB_THROW_DAMAGE = 11.0f; // v0.17.7 City tier: was 7

	private static final int BARRAGE_DURATION = 26;
	private static final int BARRAGE_HIT_INTERVAL = 3;
	private static final float BARRAGE_HIT_DAMAGE = 11.0f; // v0.17.7 City tier: was 4 (hit invulnerability lets about 3 land on one target)
	/** v0.13.19: the barrage reaches as far as Tendril Strike. */
	private static final double BARRAGE_RANGE = TENDRIL_STRIKE_RANGE;
	/** Each barrage tendril wanders up to this many degrees off the aim. */
	private static final float BARRAGE_SPREAD_DEGREES = 3.5f;

	private static final double BLADE_SLASH_RANGE = 5.0;
	private static final double BLADE_SLASH_CONE_DOT = 0.2;
	private static final float BLADE_SLASH_DAMAGE = 12.0f; // v0.17.7 City tier: was 8 (+2 past armour unchanged)

	/** v0.13.19: 60 s -> 90 s, a much bigger hit. */
	private static final int CD_ONSLAUGHT = 1800;
	private static final int ONSLAUGHT_CHARGE_TICKS = 60;
	private static final double ONSLAUGHT_RADIUS = 9.0;
	private static final float ONSLAUGHT_DAMAGE = 20.0f;
	private static final int ONSLAUGHT_DOT_TICKS = 160;

	/** v0.13.21: 25 -> 30 blocks. */
	public static final double GRAPPLE_RANGE = 30.0;
	private static final int CD_GRAPPLE = 60;
	private static final int GRAPPLE_PULL_TICKS = 20;

	// (v0.17.5 tendril-vfx: the ICHOR / SHEEN dust colours are gone -- every move effect is rendered tendrils, SymbioteVfx)

	private static final Map<Integer, Long> LEAP_NO_FALL_UNTIL = new ConcurrentHashMap<>();
	private static final Map<Integer, double[]> GRAPPLE_PULL = new ConcurrentHashMap<>();
	/** Active Tendril Barrage: {endTick, lastHitTick, shotsFired}. */
	private static final Map<Integer, long[]> BARRAGE = new ConcurrentHashMap<>();
	/** Onslaught victims to keep spraying with symbiote particles: casterId -> {endTick, victimId...}. */
	private static final Map<Integer, long[]> ONSLAUGHT_VICTIMS = new ConcurrentHashMap<>();
	/** The tendril currently holding something (grab / grapple): playerId -> tendril entity id. */
	private static final Map<Integer, Integer> HELD_TENDRIL = new ConcurrentHashMap<>();

	private SymbioteAbilityManager() {
	}

	/** v0.9.23: a bonded Normal host owns the six slots whether or not the suit is currently worn. */
	public static boolean hasContext(ServerPlayer player) {
		// v0.17.5 (a Secondary power): stacked on a Primary, the kit is only the player's while the suit is on
		return Symbiote.hasSymbiote(player) && SymbioteHostType.of(player) == SymbioteHostType.NORMAL
				&& (Symbiote.isActive(player) || !Symbiote.hasPrimaryPower(player));
	}

	public static boolean shieldActive(ServerPlayer player) {
		return Symbiote.state(player).shieldHeld;
	}

	public static boolean leapFallProtected(ServerPlayer player, long now) {
		Long until = LEAP_NO_FALL_UNTIL.get(player.getId());
		return until != null && now < until;
	}

	public static boolean onslaughtCharging(ServerPlayer player) {
		return Symbiote.state(player).onslaughtChargeStart >= 0;
	}

	/** Is the Symbiote Grapple (Shift+X) off cooldown? Feeds the Symbiote's voice. */
	public static boolean grappleReady(ServerPlayer player, long now) {
		return now >= Symbiote.state(player).abilityCooldowns.get(CDI_GRAPPLE);
	}

	/** Is Symbiote Leap (X) off cooldown? */
	public static boolean leapReady(ServerPlayer player, long now) {
		return now >= Symbiote.state(player).abilityCooldowns.get(AbilitySlot.SLOT_3.index());
	}

	/** Is Symbiote Onslaught (Shift+hold Z) off cooldown and not already charging? */
	public static boolean onslaughtReady(ServerPlayer player, long now) {
		// v0.17.5: Shift + hold Z is the Monster form now (Onslaught has no key) -- the Symbiote's voice lines ask about that
		return com.projecthero.mod.symbiote.monster.SymbioteMonster.ready(player, now);
	}

	/** Is Symbiote Shield (Shift+V) available -- not up already, and the Biomass is not spent? */
	public static boolean shieldReady(ServerPlayer player) {
		return !Symbiote.state(player).shieldHeld && SymbioteVitalsManager.usable(player);
	}

	public static void clearSessionState() {
		LEAP_NO_FALL_UNTIL.clear();
		LUNGE.clear();
		GRAPPLE_PULL.clear();
		BARRAGE.clear();
		ONSLAUGHT_VICTIMS.clear();
		HELD_TENDRIL.clear();
		KEY_DOWN.clear();
		STRIKE_CHAIN.clear();
		SPEAR.clear();
		PINNED.clear();
		RAIN.clear();
		DIVE.clear();
		ERUPTION.clear();
		DEVOUR.clear();
		PUNCH_CHAIN.clear();
		// v0.17.5 moveset v3 (GRAB holds entities)
		GRAB.clear();
		SAFE_LANDING.clear();
		THORNS_PULSE.clear();
		SymbioteWeaponMoves.clearAll();
		SymbioteWings.clearSessionState();
	}

	public static void clearFor(ServerPlayer player) {
		LEAP_NO_FALL_UNTIL.remove(player.getId());
		LUNGE.remove(player.getId());
		GRAPPLE_PULL.remove(player.getId());
		BARRAGE.remove(player.getId());
		ONSLAUGHT_VICTIMS.remove(player.getId());
		// v0.17.5 moveset v3: death / relog / dimension change lets a held target go (gravity back, no fall damage) -- it used to
		// stay floating with no gravity
		releaseGrabQuietly(player, Symbiote.state(player), player.level().getGameTime());
		dropHeldTendril(player);
		stopMovesV2(player);
		THORNS_PULSE.remove(player.getId());
	}

	/** v0.17.5: drops every moveset-v2 move in flight (a pending tap, the Spear's charge, a pin, the rain, a dive, a devour). */
	private static void stopMovesV2(ServerPlayer player) {
		int id = player.getId();
		KEY_DOWN.remove(id);
		STRIKE_CHAIN.remove(id);
		if (SPEAR.remove(id) != null) {
			SymbioteAnim.stop(player, SymbioteAnim.SPEAR_CHARGE);
		}
		PINNED.remove(id);
		RAIN.remove(id);
		if (DIVE.remove(id) != null) {
			SymbioteAnim.stop(player, SymbioteAnim.DIVE_BOMB);
		}
		ERUPTION.remove(id);
		PUNCH_CHAIN.remove(id);
		SymbioteWeaponMoves.clear(player);
		releaseDevour(player);
	}

	// ---------------- dispatch ----------------

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		long now = player.level().getGameTime();
		if (pressed && SonicVulnerability.isDisrupted(player, now)) {
			player.displayClientMessage(
					Component.translatable("message.projecthero.symbiote.sonic_disrupted"), true);
			return;
		}
		boolean sneak = player.isShiftKeyDown();
		// v0.17.5 Monster form: while transformed every key is the monster's (the normal kit is locked out)
		if (com.projecthero.mod.symbiote.monster.SymbioteMonsterMoves.intercept(player, slot, pressed, sneak)) {
			return;
		}

		// v0.17.5: Shift + hold Z is the Monster form (it replaced Onslaught): a press starts the 3 s charge, a release before it is
		// done cancels it -- both edges must reach SymbioteMonster.handleCharge. A normal (no-sneak) Z press falls through to Barrage.
		if (slot == AbilitySlot.SLOT_4) {
			if (Symbiote.state(player).onslaughtChargeStart >= 0) {
				com.projecthero.mod.symbiote.monster.SymbioteMonster.handleCharge(player, pressed, now);
				return;
			}
			if (sneak && pressed) {
				com.projecthero.mod.symbiote.monster.SymbioteMonster.handleCharge(player, true, now);
				return;
			}
			// (a release with Sneak down falls through: a plain Z press that is let go while sneaking is still a tap)
		}
		// v0.17.5: the Impaling Spear is thrown when R comes up
		if (slot == AbilitySlot.SLOT_1 && !pressed && SPEAR.containsKey(player.getId())) {
			fireSpear(player, now);
			return;
		}
		if (!pressed) {
			// v0.17.5: a release under HOLD_TICKS is the tap move (a later one -- the hold move already ran -- does nothing)
			// v0.17.5 moveset v3: V too -- a tap (under FORM_WHEEL_TICKS) cycles the Form, a hold is the client's Form wheel
			if (waitsForRelease(slot)) {
				long down = takePending(player, slot);
				if (down >= 0L && now - down < holdTicks(slot)) {
					tap(player, slot, now);
				}
			}
			return;
		}
		// Symbiote Shield (Shift + V): a toggle.
		if (slot == AbilitySlot.SLOT_5 && sneak) {
			toggleShield(player);
			return;
		}
		// C while holding something throws it, Shift+C sets it down -- whatever the Biomass says, you can always let go.
		if (slot == AbilitySlot.SLOT_6 && Symbiote.state(player).tendrilGrabHeld) {
			takePending(player, slot); // that C's release must not bite as well
			if (sneak) {
				setDownGrabbed(player, Symbiote.state(player), now);
			} else {
				throwGrabbed(player, Symbiote.state(player), now);
			}
			return;
		}
		if (!SymbioteVitalsManager.usable(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
			return;
		}
		// v0.17.5: R / G / Z / C (and v3: V) without Sneak wait to see whether this is a tap or a hold
		if (!sneak && waitsForRelease(slot)) {
			KEY_DOWN.computeIfAbsent(player.getId(), k -> newKeyDown())[slot.ordinal()] = now;
			return;
		}

		switch (slot) {
			case SLOT_1 -> tryWithCooldown(player, CDI_SWEEP, now, CD_TENDRIL_SWEEP, () -> tendrilSweep(player));
			case SLOT_2 -> spikeNova(player, now);
			case SLOT_3 -> {
				if (sneak) {
					handleGrapple(player, now);
				} else if (canDiveBomb(player)) {
					tryWithCooldown(player, CDI_DIVE, now, CD_DIVE, () -> diveBomb(player, now));
				} else {
					tryWithCooldown(player, 2, now, CD_LEAP, () -> symbioteLunge(player));
				}
			}
			case SLOT_5 -> SymbioteVitalsManager.cycleForm(player);
			case SLOT_6 -> beginTendrilGrab(player, now);
			default -> {
			}
		}
	}

	// ---------------- v0.17.5 tap vs hold ----------------

	/** A key held this long (0.4 s) is a hold; released sooner it is a tap. */
	public static final int HOLD_TICKS = 8;

	/** Cooldown list indices of the new moves ({@link SymbioteState#COOLDOWN_SLOTS}). */
	public static final int CDI_SPEAR = 6;
	public static final int CDI_RAIN = 7;
	public static final int CDI_DIVE = 8;
	public static final int CDI_ERUPTION = 9;
	public static final int CDI_MAW = 10;
	public static final int CDI_DEVOUR = 11;
	/**
	 * v0.17.5 (user: "if i use shift+R on symbiote i cant use regular R while Shift+R is on cooldown"): the Shift variants' own
	 * cooldowns -- they used to share their key's plain entry. Shift+G (Spike Fan, {@code SymbioteVitals.spikeConeReadyAt}) and
	 * Shift+C (Tendril Grab, entry 5 while C's tap is Maw Bite on 10) were already separate; Shift+V (Shield) and V (Form) have
	 * none. Still shared on purpose: Barrage and Blade Slash (both are Z's tap -- which one depends on the Form, so swapping Form
	 * must not hand out a fresh Z), and Devour's Maw Bite fallback (it is the same bite as C's tap).
	 */
	public static final int CDI_SWEEP = 12;
	public static final int CDI_ONSLAUGHT = 13;
	public static final int CDI_GRAPPLE = 14;

	/** playerId -> per slot ordinal, the game time the key went down (-1 = nothing pending). */
	private static final Map<Integer, long[]> KEY_DOWN = new ConcurrentHashMap<>();

	private static long[] newKeyDown() {
		long[] a = new long[AbilitySlot.values().length];
		java.util.Arrays.fill(a, -1L);
		return a;
	}

	/** R, G, Z and C (no Sneak) have a hold move. */
	public static boolean hasHoldMove(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_1 || slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_4 || slot == AbilitySlot.SLOT_6;
	}

	/**
	 * v0.17.5 moveset v3: V held this long (0.5 s) opens the Form wheel on the client ({@code SymbioteFormWheelScreen}); released
	 * sooner it is a tap and cycles the Form. The client never sends V's release once the wheel is open, so the server simply lets
	 * the pending press lapse here.
	 */
	public static final int FORM_WHEEL_TICKS = 10;

	/** R / G / Z / C (their hold moves) and V (the Form wheel): the press waits for the release to tell a tap from a hold. */
	public static boolean waitsForRelease(AbilitySlot slot) {
		return hasHoldMove(slot) || slot == AbilitySlot.SLOT_5;
	}

	/** Ticks a key must be held before it counts as a hold. */
	public static int holdTicks(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_5 ? FORM_WHEEL_TICKS : HOLD_TICKS;
	}

	/** Takes (clears) the pending press on {@code slot}: when it went down, or -1. */
	private static long takePending(ServerPlayer player, AbilitySlot slot) {
		long[] a = KEY_DOWN.get(player.getId());
		if (a == null) {
			return -1L;
		}
		long down = a[slot.ordinal()];
		a[slot.ordinal()] = -1L;
		return down;
	}

	/** Is a press on {@code slot} still waiting to become a tap or a hold? (tests, HUD-free) */
	public static boolean pending(ServerPlayer player, AbilitySlot slot) {
		long[] a = KEY_DOWN.get(player.getId());
		return a != null && a[slot.ordinal()] >= 0L;
	}

	private static void tap(ServerPlayer player, AbilitySlot slot, long now) {
		if (!SymbioteVitalsManager.usable(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
			return;
		}
		switch (slot) {
			case SLOT_1 -> tryWithCooldown(player, 0, now, CD_TENDRIL_STRIKE, () -> tendrilStrike(player));
			case SLOT_2 -> tryWithCooldown(player, AbilitySlot.SLOT_2.index(), now, CD_SPIKE_SHOT, () -> spikeShot(player));
			case SLOT_4 -> {
				if (SymbioteVitalsManager.bladeActive(player)) {
					// v0.17.5 weapons: each weapon's Z, all on Z's tap entry (3) so a weapon swap never hands out a fresh Z
					SymbioteWeapon w = SymbioteVitalsManager.weapon(player);
					tryWithCooldown(player, 3, now, w.zCooldown,
							() -> w == SymbioteWeapon.BLADE ? bladeSlash(player) : SymbioteWeaponMoves.start(player, w, now));
				} else if (SymbioteVitalsManager.thornsMode(player)) {
					// v0.17.5 moveset v3: Thorns form's Z is Spike Surge, on its own entry (16)
					tryWithCooldown(player, CDI_SURGE, now, CD_SURGE, () -> spikeSurge(player));
				} else {
					tryWithCooldown(player, 3, now, CD_BARRAGE, () -> startBarrage(player, now));
				}
			}
			case SLOT_5 -> SymbioteVitalsManager.cycleForm(player);
			case SLOT_6 -> tryWithCooldown(player, CDI_MAW, now, CD_MAW, () -> mawBite(player));
			default -> {
			}
		}
	}

	/**
	 * v0.17.5 moveset v3: a Form picked on the V wheel ({@code SymbioteKitPayload}). The press that opened it lapses (no tap); the
	 * Form rules are {@link SymbioteVitalsManager#selectForm}'s.
	 */
	public static void pickForm(ServerPlayer player, int form) {
		takePending(player, AbilitySlot.SLOT_5);
		if (!hasContext(player) || !SymbioteVitalsManager.usable(player)) {
			return;
		}
		if (SonicVulnerability.isDisrupted(player, player.level().getGameTime())) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.sonic_disrupted"), true);
			return;
		}
		SymbioteVitalsManager.selectForm(player, form);
	}

	/** v0.17.5 moveset v3: the client opened the Form wheel -- V's pending press must not become a tap. */
	public static void formWheelOpened(ServerPlayer player) {
		takePending(player, AbilitySlot.SLOT_5);
	}

	private static void hold(ServerPlayer player, AbilitySlot slot, long now) {
		if (!SymbioteVitalsManager.usable(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
			return;
		}
		switch (slot) {
			case SLOT_1 -> beginSpear(player, now);
			case SLOT_2 -> tryWithCooldown(player, CDI_RAIN, now, CD_RAIN, () -> spikeRain(player, now));
			case SLOT_4 -> tryWithCooldown(player, CDI_ERUPTION, now, CD_ERUPTION, () -> groundEruption(player, now));
			case SLOT_6 -> devourOrBite(player, now);
			default -> {
			}
		}
	}

	/** Every tick: a key still down at {@link #HOLD_TICKS} starts its hold move (once). */
	private static void tickHolds(ServerPlayer player, long now) {
		long[] a = KEY_DOWN.get(player.getId());
		if (a == null) {
			return;
		}
		for (AbilitySlot slot : AbilitySlot.values()) {
			long down = a[slot.ordinal()];
			if (down >= 0L && now - down >= holdTicks(slot)) { // (V's hold is the client's Form wheel: hold() does nothing)
				a[slot.ordinal()] = -1L;
				hold(player, slot, now);
			}
		}
	}

	private interface AbilityAttempt {
		boolean run();
	}

	private static void tryWithCooldown(ServerPlayer player, int index, long now, int cooldown, AbilityAttempt attempt) {
		long readyAt = Symbiote.state(player).abilityCooldowns.get(index);
		if (now < readyAt) {
			cooldownMessage(player, readyAt - now);
			return;
		}
		if (attempt.run()) {
			setCooldown(player, index, now, cooldown);
		}
	}

	private static void cooldownMessage(ServerPlayer player, long ticksLeft) {
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.ability_cooldown",
				String.format(java.util.Locale.ROOT, "%.1f", ticksLeft / 20.0f)), true);
	}

	private static void setCooldown(ServerPlayer player, int index, long now, int ticks) {
		SymbioteState c = Symbiote.state(player).copy();
		c.abilityCooldowns.set(index, now + ticks);
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
	}

	// ---------------- aiming helpers ----------------

	/**
	 * v0.13.21: {@link AbilityHelpers#enemiesAround} minus the caster's squadmates. Every Symbiote move -- the
	 * Normal host's, the Black Suit's and Agent Venom's -- picks its area targets through this, so a squadmate is
	 * never hit, slowed, withered, blinded or thrown, not merely spared the damage by the friendly-fire veto.
	 */
	public static List<LivingEntity> enemiesAround(ServerPlayer player, Vec3 center, double radius) {
		return AbilityHelpers.enemiesAround(player, center, radius).stream()
				.filter(e -> !Squads.areAllies(player, e)).toList();
	}

	/**
	 * What a tendril flying from the eyes along {@code dir} runs into first, within {@code range}: the first
	 * living thing, else the block face, else the end of its reach. Blocks stop it -- nothing through walls.
	 */
	static Vec3 traceTendril(ServerPlayer player, Vec3 dir, double range, LivingEntity[] hitOut) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 eye = player.getEyePosition();
		Vec3 far = eye.add(dir.scale(range));
		HitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
		AABB sweep = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(level, player, eye, end, sweep,
				e -> e != player && e.isPickable() && e.isAlive() && e instanceof LivingEntity && !(e instanceof ArmorStand)
						&& !Squads.areAllies(player, e)); // v0.13.21: tendrils pass a squadmate by
		if (ehr != null && ehr.getEntity() instanceof LivingEntity le) {
			hitOut[0] = le;
			return le.position().add(0, le.getBbHeight() * 0.5, 0);
		}
		hitOut[0] = null;
		return end;
	}

	private static Vec3 spread(ServerPlayer player, Vec3 dir, float degrees) {
		float yaw = (player.getRandom().nextFloat() * 2.0f - 1.0f) * degrees * ((float) Math.PI / 180.0f);
		float pitch = (player.getRandom().nextFloat() * 2.0f - 1.0f) * degrees * ((float) Math.PI / 180.0f);
		return dir.yRot(yaw).xRot(pitch).normalize();
	}

	// ---------------- Tendril Strike / Sweep ----------------

	/**
	 * R -- a tendril lashes out of the host's hand along the aim, up to 30 blocks. v0.13.19: it fires whether
	 * or not anything is in the sights, so it can miss; whatever it meets first takes 15.
	 */
	private static boolean tendrilStrike(ServerPlayer player) {
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 end = traceTendril(player, player.getLookAngle(), TENDRIL_STRIKE_RANGE, hit);
		LivingEntity target = hit[0];
		ServerLevel level = AbilityHelpers.level(player);
		// v0.17.5: two strikes in a row that found something -- this third one is the Tendril Slam
		long now = player.level().getGameTime();
		long[] chain = STRIKE_CHAIN.get(player.getId());
		int prior = chain != null && now - chain[1] <= STRIKE_CHAIN_WINDOW ? (int) chain[0] : 0;
		if (target == null) {
			STRIKE_CHAIN.remove(player.getId());
		} else if (prior >= 2) {
			STRIKE_CHAIN.remove(player.getId());
			tendrilSlam(player, target);
			return true;
		} else {
			STRIKE_CHAIN.put(player.getId(), new long[]{prior + 1, now});
		}
		SymbioteAnim.play(player, SymbioteAnim.TENDRIL_STRIKE);
		SymbioteTendrilEntity.fromHand(player, true, end, target, 11, 3, 0.14f);
		SymbioteSounds.lash(player, 0.9f, 0.8f);
		if (target != null && AbilityHelpers.hurtLands(player, target, TENDRIL_STRIKE_DAMAGE)) {
			AbilityHelpers.knockbackFrom(target, player.position(), 0.9);
			SymbioteVfx.impact(player, target, 1.0f); // v0.17.5 tendril-vfx: tendrils burst from the wound (was ink + crits)
			SymbioteSounds.organic(player, 0.8f, 0.5f);
		} else {
			SymbioteVfx.impact(player, end, end.subtract(player.getEyePosition()), 0.45f); // the tip splats where it lands
		}
		return true;
	}

	/** Shift + R -- a fan of tendrils that sweeps everything in front and slows it for 7 seconds. */
	private static boolean tendrilSweep(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 look = player.getLookAngle();
		SymbioteAnim.play(player, SymbioteAnim.TENDRIL_SWEEP);
		List<LivingEntity> hit = coneTargets(player, SWEEP_RANGE, SWEEP_CONE_DOT);
		for (LivingEntity target : hit) {
			AbilityHelpers.hurt(player, target, SWEEP_DAMAGE + player.getRandom().nextFloat() * 2.0f);
			AbilityHelpers.slow7s(target);
			AbilityHelpers.knockbackFrom(target, player.position(), 0.5);
			SymbioteVfx.impact(player, target, 0.9f);
		}
		// seven tendrils fanning out left to right, each reaching a beat after the last: the sweep itself
		Vec3 eye = player.getEyePosition();
		for (int i = 0; i < 7; i++) {
			double a = 0.9 - i * 0.3;
			Vec3 dir = look.yRot((float) a).normalize();
			HitResult block = level.clip(new ClipContext(eye, eye.add(dir.scale(SWEEP_RANGE)),
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(dir.scale(SWEEP_RANGE)) : block.getLocation();
			SymbioteTendrilEntity.fromHand(player, true, end, null, 10 + i, 2 + i, 0.12f);
		}
		SymbioteSounds.organic(player, 1.0f, 0.4f);
		SymbioteSounds.lash(player, 1.0f, 0.6f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.5f, 1.4f);
		return true;
	}

	private static List<LivingEntity> coneTargets(ServerPlayer player, double range, double dotThreshold) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		return enemiesAround(player, player.position(), range).stream().filter(e -> {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			return to.lengthSqr() > 0.01 && to.normalize().dot(look) >= dotThreshold;
		}).toList();
	}

	// ---------------- Symbiote Spike (G) / Spike Fan (Shift + G) ----------------

	/** G -- one living spike, shot from the hand straight down the aim. */
	private static boolean spikeShot(ServerPlayer player) {
		SymbioteAnim.play(player, SymbioteAnim.SPIKE_SHOT);
		SymbioteSpikeEntity.shoot(player, SymbioteHands.right(player), player.getLookAngle());
		SymbioteSounds.organic(player, 0.9f, 1.3f);
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_THROW, 0.6f, 1.5f);
		return true;
	}

	// ---------------- v0.17.5 moveset v3: Spike Nova (Shift + G, replaces the Spike Fan) ----------------

	public static final double NOVA_RADIUS = 6.0;
	public static final float NOVA_DAMAGE = 12.0f; // v0.17.7 City tier: was 10
	private static final int NOVA_WITHER_TICKS = 60;
	private static final int NOVA_SPIKES = 16;

	/**
	 * Shift + G (user: "make shift+G an aoe ability please"): spikes burst out of the whole body in a flat ring of 16 and a shockwave
	 * of tendrils rolls out over the ground. Everything within 6 blocks takes 10, Wither II for 3 s and is thrown out (not bosses).
	 * The ring's spikes are the look only (they pass through creatures; the damage is the area's, so nothing is hit twice). Its own
	 * 8 s cooldown on the old Spike Fan entry ({@code spikeConeReadyAt}).
	 */
	private static void spikeNova(ServerPlayer player, long now) {
		SymbioteVitals v = SymbioteVitalsManager.vitals(player);
		if (now < v.spikeConeReadyAt) {
			cooldownMessage(player, v.spikeConeReadyAt - now);
			return;
		}
		SymbioteVitals c = SymbioteVitalsManager.vitals(player).copy();
		c.spikeConeReadyAt = now + CD_SPIKE_NOVA;
		SymbioteVitalsManager.save(player, c);
		SymbioteAnim.play(player, SymbioteAnim.SPIKE_NOVA);
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 centre = player.position();
		Vec3 chest = centre.add(0, player.getBbHeight() * 0.55, 0);
		double radius = NOVA_RADIUS;
		float yaw0 = player.getRandom().nextFloat() * 360.0f / NOVA_SPIKES;
		for (int i = 0; i < NOVA_SPIKES; i++) {
			double a = Math.toRadians(yaw0 + i * 360.0 / NOVA_SPIKES);
			Vec3 dir = new Vec3(Math.cos(a), 0.0, Math.sin(a));
			SymbioteSpikeEntity.nova(player, chest.add(dir.scale(0.5 * player.getScale())).add(0, (i % 2) * 0.25 - 0.1, 0), dir,
					(int) Math.ceil(radius / 1.9));
		}
		// the shockwave: a ring of ground tendrils lashing outward plus the ground burst
		Vec3 ground = groundPoint(level, centre);
		for (int i = 0; i < 12; i++) {
			double a = Math.PI * 2 * (i + 0.5) / 12;
			Vec3 root = groundPoint(level, centre.add(Math.cos(a) * 1.0, 0, Math.sin(a) * 1.0));
			Vec3 tip = groundPoint(level, centre.add(Math.cos(a) * radius, 0, Math.sin(a) * radius)).add(0, 0.35 + (i % 2) * 0.3, 0);
			SymbioteTendrilEntity.fromPoint(player, root, tip, null, 12, 4, 0.13f);
		}
		SymbioteVfx.groundBurst(player, ground, (float) radius);
		SymbioteVfx.bodyBurst(player, 1.2f);
		for (LivingEntity target : enemiesAround(player, centre, radius + 1.0)) {
			Vec3 to = target.position().subtract(centre);
			if (to.horizontalDistance() > radius + target.getBbWidth() * 0.5 || Math.abs(to.y) > 3.0) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(player, target, NOVA_DAMAGE)) {
				target.addEffect(new MobEffectInstance(MobEffects.WITHER, NOVA_WITHER_TICKS, 1, false, true, true), player);
				if (!isBoss(target)) {
					AbilityHelpers.knockbackFrom(target, centre, 1.0);
					AbilityHelpers.push(target, new Vec3(0, 0.35, 0));
				}
				SymbioteVfx.impact(player, target, 0.9f);
			}
		}
		SymbioteSounds.organic(player, 1.1f, 0.8f);
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_THROW, 1.0f, 0.7f);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_ATTACK_IMPACT, 0.8f, 1.2f);
	}

	// ---------------- v0.17.5 moveset v3: the Thorns aura and Spike Surge (Z in Thorns form) ----------------

	public static final int CDI_SURGE = 16;
	public static final int CD_SURGE = 200;             // 10 s
	public static final int SURGE_TICKS = 120;          // 6 s
	public static final double THORNS_RADIUS = 1.5;
	public static final double SURGE_RADIUS = 3.5;
	public static final float THORNS_DAMAGE = 3.0f;
	public static final float SURGE_DAMAGE = 5.0f;
	public static final int THORNS_INTERVAL = 15;
	private static final int THORNS_WITHER_TICKS = 40;
	/** playerId -> game time of the last aura pulse. */
	private static final Map<Integer, Long> THORNS_PULSE = new ConcurrentHashMap<>();

	/**
	 * Is Spike Surge running? Read off the synced cooldown (it started {@link #CD_SURGE} before its entry comes ready), so every
	 * client knows too (the body spikes grow long) without a new field. Client-safe.
	 */
	public static boolean surging(Player player, long now) {
		SymbioteState s = player.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
		return surgeAge(s, now) >= 0 && SymbioteVitalsManager.form(player) == SymbioteVitalsManager.FORM_THORNS;
	}

	/** Ticks since Spike Surge started, or -1 when it is not running. Client-safe. */
	public static long surgeAge(SymbioteState s, long now) {
		if (s == null || s.abilityCooldowns.size() <= CDI_SURGE) {
			return -1L;
		}
		long start = s.abilityCooldowns.get(CDI_SURGE) - CD_SURGE;
		long age = now - start;
		return age >= 0L && age < SURGE_TICKS ? age : -1L;
	}

	/**
	 * Z in Thorns form (user: "make the z move for this to make the spikes temporarily longer so that the range increases"): the body
	 * spikes grow about 2.5x longer for 6 s -- the aura reaches 3.5 blocks and hits for 5. 10 s cooldown (entry 16).
	 */
	private static boolean spikeSurge(ServerPlayer player) {
		SymbioteAnim.play(player, SymbioteAnim.SPIKE_SURGE);
		SymbioteVfx.bodyBurst(player, 1.3f);
		THORNS_PULSE.remove(player.getId()); // the longer spikes hit at once
		SymbioteSounds.organic(player, 1.1f, 0.5f);
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.9f, 0.6f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.5f, 1.5f);
		return true;
	}

	/**
	 * Thorns form (user: "make the thorns form hurt things that get close to you aswell"): everything within 1.5 blocks (3.5 while
	 * surging) -- measured flat from the host to the target's near side, with the bodies overlapping in height -- takes 3 (5 surging)
	 * every 15 ticks, Wither I for 2 s and a small push out (not bosses). Never squadmates, own pets, or players with PvP off
	 * ({@link #enemiesAround}). The Thorns IV reflection is unchanged.
	 */
	private static void tickThornsAura(ServerPlayer player, long now) {
		if (!SymbioteVitalsManager.thornsMode(player) || SymbioteVitalsManager.vitals(player).broken) {
			THORNS_PULSE.remove(player.getId());
			return;
		}
		Long last = THORNS_PULSE.get(player.getId());
		if (last != null && now - last < THORNS_INTERVAL && now >= last) {
			return;
		}
		THORNS_PULSE.put(player.getId(), now);
		boolean surge = surging(player, now);
		double reach = surge ? SURGE_RADIUS : THORNS_RADIUS; // from the host's side, so a bulked-up host reaches as far
		float damage = surge ? SURGE_DAMAGE : THORNS_DAMAGE;
		Vec3 c = player.position();
		AABB host = player.getBoundingBox();
		double hostHalf = player.getBbWidth() * 0.5;
		for (LivingEntity target : enemiesAround(player, c, reach + hostHalf + 2.0)) {
			Vec3 to = target.position().subtract(c);
			double gap = to.horizontalDistance() - hostHalf - target.getBbWidth() * 0.5;
			AABB tb = target.getBoundingBox();
			if (gap > reach || tb.maxY < host.minY || tb.minY > host.maxY) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(player, target, damage)) {
				target.addEffect(new MobEffectInstance(MobEffects.WITHER, THORNS_WITHER_TICKS, 0, false, true, true), player);
				if (!isBoss(target)) {
					AbilityHelpers.knockbackFrom(target, c, surge ? 0.55 : 0.4);
				}
				SymbioteVfx.impact(player, target, surge ? 0.8f : 0.55f);
			}
		}
	}

	// ---------------- Tendril Grab (Shift + C; C throws, left click crushes, Shift+C sets down) ----------------

	/** v0.17.5 moveset v3: a crush squeezes for this much, at most once per {@link #CRUSH_INTERVAL} ticks. */
	public static final float CRUSH_DAMAGE = 8.0f; // v0.17.7 City tier: was 6 (a repeat squeeze, twice a second at most -- kept under the range like tick damage)
	public static final int CRUSH_INTERVAL = 10;
	/** Ticks a released target is spared fall damage (it is also spared as soon as it lands -- the entry just lapses). */
	private static final int SAFE_LANDING_TICKS = 100;

	/** A held target. Holds the entity itself (cleared per player and in {@link #clearSessionState}). */
	private static final class GrabHold {
		final LivingEntity target;
		final boolean suitAtStart;
		long lastCrush = Long.MIN_VALUE / 2;
		long lastWrap;

		GrabHold(LivingEntity target, boolean suitAtStart, long now) {
			this.target = target;
			this.suitAtStart = suitAtStart;
			this.lastWrap = now;
		}
	}

	/** playerId -> what that host is holding. */
	private static final Map<Integer, GrabHold> GRAB = new ConcurrentHashMap<>();
	/** entity id -> game time until which it takes no fall damage (a set-down target, a released player). */
	private static final Map<Integer, Long> SAFE_LANDING = new ConcurrentHashMap<>();

	/**
	 * v0.17.5 moveset v3 (user: "works on players and squad mates too"): anything alive that is not a boss, a Target Dummy, an armour
	 * stand, a spectator or a creative player -- players and squadmates included, PvP or not (only the crush's damage asks).
	 */
	public static boolean grabbable(ServerPlayer player, Entity e) {
		if (!(e instanceof LivingEntity target) || !target.isAlive() || target == player || target instanceof ArmorStand
				|| target instanceof com.projecthero.mod.targetdummy.TargetDummyEntity || isBoss(target) || target.isSpectator()) {
			return false;
		}
		return !(target instanceof Player p) || !p.getAbilities().invulnerable;
	}

	/** May this host's crush / throw hurt {@code target}? Not a squadmate, not a player PvP protects. */
	private static boolean harmable(ServerPlayer player, LivingEntity target) {
		return !Squads.areAllies(player, target) && com.projecthero.mod.combat.HeroTargets.canHarm(player, target);
	}

	private static void beginTendrilGrab(ServerPlayer player, long now) {
		SymbioteState s = Symbiote.state(player);
		long readyAt = s.abilityCooldowns.get(AbilitySlot.SLOT_6.index());
		if (now < readyAt) {
			cooldownMessage(player, readyAt - now);
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, GRAB_RANGE);
		if (target == null || !grabbable(player, target)) {
			// the tendril still lashes out and comes back empty
			SymbioteAnim.play(player, SymbioteAnim.TENDRIL_STRIKE);
			SymbioteTendrilEntity.fromHand(player, true, AbilityHelpers.aimPoint(player, GRAB_RANGE), null, 9, 3, 0.12f);
			SymbioteSounds.lash(player, 0.7f, 0.9f);
			return;
		}
		SymbioteState c = s.copy();
		c.tendrilGrabTargetId = target.getId();
		c.tendrilGrabHeld = true;
		c.tendrilGrabStartTick = now;
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		GRAB.put(player.getId(), new GrabHold(target, Symbiote.isActive(player), now));

		target.setNoGravity(true);
		target.setDeltaMovement(Vec3.ZERO);
		SymbioteAnim.play(player, SymbioteAnim.GRAB);
		holdTendril(player, SymbioteTendrilEntity.fromHand(player, true,
				target.position().add(0, target.getBbHeight() * 0.5, 0), target, GRAB_WRAP_TICKS + 10, 3, 0.15f));
		SymbioteVfx.wrap(player, target, GRAB_WRAP_TICKS, 1.0f); // tendrils coil round the prey (topped up while it is held)
		SymbioteSounds.organic(player, 0.9f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.grab_seized"), true);
	}

	private static void holdTendril(ServerPlayer player, SymbioteTendrilEntity tendril) {
		dropHeldTendril(player);
		if (tendril != null) {
			HELD_TENDRIL.put(player.getId(), tendril.getId());
		}
	}

	private static void dropHeldTendril(ServerPlayer player) {
		Integer id = HELD_TENDRIL.remove(player.getId());
		if (id != null && player.level().getEntity(id) instanceof SymbioteTendrilEntity t) {
			t.retract();
		}
	}

	/** The held target: the entity the hold remembers, else by id in the host's level. */
	private static LivingEntity grabbed(ServerPlayer player, SymbioteState s) {
		GrabHold h = GRAB.get(player.getId());
		if (h != null) {
			return h.target;
		}
		return player.level().getEntity(s.tendrilGrabTargetId) instanceof LivingEntity le ? le : null;
	}

	/**
	 * v0.17.5 moveset v3 (user: "Remove time limit of Shift+C grab move"): held in front of the host until C throws, Shift+C sets it
	 * down, or the host dies / takes the suit off (if it was on for the grab) / changes dimension, or the target dies or is a boss.
	 */
	private static void tickTendrilGrab(ServerPlayer player, long now) {
		SymbioteState s = Symbiote.state(player);
		if (!s.tendrilGrabHeld) {
			if (GRAB.containsKey(player.getId())) {
				releaseGrabQuietly(player, s, now);
			}
			return;
		}
		LivingEntity target = grabbed(player, s);
		GrabHold h = GRAB.get(player.getId());
		if (h == null && target != null) {
			h = new GrabHold(target, Symbiote.isActive(player), now); // an old save / a grab from before this version
			GRAB.put(player.getId(), h);
		}
		if (target == null || !target.isAlive() || target.isRemoved() || target.level() != player.level() || !player.isAlive()
				|| isBoss(target) || (h != null && h.suitAtStart && !Symbiote.isActive(player))
				|| player.distanceToSqr(target) > (GRAB_RANGE * 1.5) * (GRAB_RANGE * 1.5)) {
			releaseGrabQuietly(player, s, now);
			return;
		}
		Vec3 hold = player.getEyePosition().add(player.getLookAngle().scale(GRAB_HOLD_DISTANCE * Math.max(1.0, player.getScale())));
		target.setNoGravity(true);
		target.teleportTo(hold.x, hold.y - target.getBbHeight() * 0.5, hold.z);
		target.setDeltaMovement(Vec3.ZERO);
		target.fallDistance = 0;
		// keep the tendril and the coils going for as long as it is held
		Integer tid = HELD_TENDRIL.get(player.getId());
		if (tid != null && player.level().getEntity(tid) instanceof SymbioteTendrilEntity t && t.isAlive()) {
			t.keepAlive(40);
		} else {
			holdTendril(player, SymbioteTendrilEntity.fromHand(player, true, hold, target, 60, 2, 0.15f));
		}
		if (h != null && now - h.lastWrap >= GRAB_WRAP_TICKS - 10) {
			h.lastWrap = now;
			SymbioteVfx.wrap(player, target, GRAB_WRAP_TICKS, 1.0f);
		}
	}

	/** Is this host holding something with the grab? (tests) */
	public static boolean holding(ServerPlayer player) {
		return Symbiote.state(player).tendrilGrabHeld;
	}

	private static void throwGrabbed(ServerPlayer player, SymbioteState s, long now) {
		LivingEntity target = grabbed(player, s);
		if (target != null && target.isAlive()) {
			target.setNoGravity(false);
			Vec3 look = player.getLookAngle();
			AbilityHelpers.push(target, look.scale(1.9).add(0, 0.4, 0));
			if (harmable(player, target)) {
				AbilityHelpers.hurt(player, target, GRAB_THROW_DAMAGE);
			}
			if (target instanceof Player) {
				safeLanding(target, now); // "the grabbed player gets no fall damage after release"
			}
			SymbioteVfx.impact(player, target, 1.3f);
			SymbioteSounds.organic(player, 1.0f, 0.4f);
			SymbioteAnim.play(player, SymbioteAnim.THROW);
		}
		clearGrab(player, now, true);
	}

	/**
	 * Shift+C while holding (user: "presses shift+C to let them down gently"): the target is put down on the ground just in front of the
	 * host -- no damage, no fall damage. Starts the grab's cooldown like a throw.
	 */
	private static void setDownGrabbed(ServerPlayer player, SymbioteState s, long now) {
		LivingEntity target = grabbed(player, s);
		if (target != null && target.isAlive()) {
			ServerLevel level = AbilityHelpers.level(player);
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
			double out = player.getBbWidth() * 0.5 + target.getBbWidth() * 0.5 + 0.6;
			Vec3 dest = groundPoint(level, player.position().add(flat.scale(out)));
			if (!level.noCollision(target, target.getBoundingBox().move(dest.subtract(target.position())))) {
				dest = player.position(); // no room in front: at the host's feet
			}
			target.setNoGravity(false);
			target.teleportTo(dest.x, dest.y, dest.z);
			target.setDeltaMovement(Vec3.ZERO);
			target.fallDistance = 0;
			target.hurtMarked = true;
			safeLanding(target, now);
			SymbioteAnim.play(player, SymbioteAnim.GRAB_SET_DOWN);
			SymbioteSounds.organic(player, 0.6f, 0.9f);
		}
		clearGrab(player, now, true);
	}

	/**
	 * Left click while holding (user: "left clicks to crush them"): the coils squeeze -- 6 damage, at most once every 10 ticks, the jaws
	 * snarl. A squadmate or a player PvP protects is only squeezed (no damage, an action-bar hint). Detected from the client's attack
	 * key ({@code SymbioteKitPayload.CRUSH}) or, as a backstop, a melee swing that reached an entity ({@link #registerEvents}, which
	 * then cancels that punch). @return true if this click squeezed (false while it is too soon or nothing is held)
	 */
	public static boolean crush(ServerPlayer player) {
		SymbioteState s = Symbiote.state(player);
		if (!s.tendrilGrabHeld || !hasContext(player)) {
			return false;
		}
		long now = player.level().getGameTime();
		LivingEntity target = grabbed(player, s);
		GrabHold h = GRAB.get(player.getId());
		if (target == null || !target.isAlive() || (h != null && now - h.lastCrush < CRUSH_INTERVAL)) {
			return false;
		}
		if (h != null) {
			h.lastCrush = now;
		}
		SymbioteAnim.play(player, SymbioteAnim.GRAB_CRUSH);
		SymbioteVfx.wrap(player, target, 12, 1.35f);
		SymbioteSounds.organic(player, 1.0f, 0.45f);
		AbilityHelpers.sound(player, SoundEvents.SLIME_SQUISH, 0.9f, 0.6f);
		if (!harmable(player, target)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.grab_crush_protected"), true);
			return true;
		}
		if (AbilityHelpers.hurtBurst(player, target, CRUSH_DAMAGE)) {
			SymbioteVfx.impact(player, target, 0.8f);
			AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 0.5f);
		}
		return true;
	}

	private static void safeLanding(LivingEntity target, long now) {
		target.fallDistance = 0;
		SAFE_LANDING.put(target.getId(), now + SAFE_LANDING_TICKS);
	}

	/** Is {@code e} spared fall damage after a grab released it? */
	public static boolean safeLanding(LivingEntity e) {
		Long until = SAFE_LANDING.get(e.getId());
		if (until == null) {
			return false;
		}
		if (e.level().getGameTime() > until) {
			SAFE_LANDING.remove(e.getId());
			return false;
		}
		return true;
	}

	private static void releaseGrabQuietly(ServerPlayer player, SymbioteState s, long now) {
		LivingEntity target = grabbed(player, s);
		if (target != null) {
			target.setNoGravity(false);
			if (target.isAlive()) {
				safeLanding(target, now); // whatever let it go, it is not hurt by the drop
			}
		}
		if (s.tendrilGrabHeld || GRAB.containsKey(player.getId())) {
			clearGrab(player, now, false);
		}
	}

	private static void clearGrab(ServerPlayer player, long now, boolean startCooldown) {
		GRAB.remove(player.getId());
		SymbioteState c = Symbiote.state(player).copy();
		c.tendrilGrabTargetId = -1;
		c.tendrilGrabHeld = false;
		if (startCooldown) {
			c.abilityCooldowns.set(AbilitySlot.SLOT_6.index(), now + CD_TENDRIL_GRAB);
		}
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		dropHeldTendril(player);
		SymbioteAnim.stop(player, SymbioteAnim.GRAB);
		SymbioteAnim.stop(player, SymbioteAnim.GRAB_CRUSH);
	}

	// ---------------- Symbiote Lunge (X) ----------------

	/** How far one lunge impulse carries the host along the aim line. */
	private static final double LUNGE_BLOCKS = 20.0;
	/** Ticks after which a lunge stops checking for a ram, even if the host is somehow still airborne. */
	private static final int LUNGE_MAX_TICKS = 40;

	/** Active lunges: {endTick, startTick}. The flight itself is ballistic -- one launch, no held push. */
	private static final Map<Integer, double[]> LUNGE = new ConcurrentHashMap<>();

	private static boolean symbioteLunge(ServerPlayer player) {
		long now = player.level().getGameTime();
		Vec3 launch = AbilityHelpers.ballisticLaunch(player.getLookAngle(), LUNGE_BLOCKS, player.onGround());
		ServerLevel level = AbilityHelpers.level(player);
		// v0.17.5 tendril-vfx (user: "symbiote tendril come off the players legs and push off the ground launching them"): tendrils
		// sprout from the legs, plant on the ground where the host stood and push off (was an ink + poof burst). Taken before the launch
		Vec3 ground = player.onGround() ? player.position() : groundPoint(level, player.position());
		LUNGE.put(player.getId(), new double[]{now + LUNGE_MAX_TICKS, now});
		LEAP_NO_FALL_UNTIL.put(player.getId(), now + LEAP_NO_FALL_TICKS);
		AbilityHelpers.launchSelf(player, launch);
		SymbioteAnim.play(player, SymbioteAnim.LUNGE);
		SymbioteVfx.legPush(player, ground, launch);
		SymbioteSounds.lash(player, 1.0f, 0.7f);
		return true;
	}

	private static void tickLunge(ServerPlayer player, long now) {
		double[] l = LUNGE.get(player.getId());
		if (l == null) {
			return;
		}
		boolean travelled = now - (long) l[1] >= 3;
		if (now >= (long) l[0] || (travelled && player.onGround())) {
			LUNGE.remove(player.getId());
			return;
		}
		// v0.12.1: the launch is a real impulse now (vanilla gravity and drag carry it), so the only
		// per-tick work is the ram check -- the host punches through the first creature it meets.
		for (LivingEntity target : enemiesAround(player, player.position().add(0, 0.9 * player.getScale(), 0), 2.0 * player.getScale())) {
			if (AbilityHelpers.hurtLands(player, target, LEAP_RAM_DAMAGE)) {
				AbilityHelpers.knockbackFrom(target, player.position(), 1.6);
				SymbioteVfx.impact(player, target, 1.4f);
				SymbioteSounds.lash(player, 1.0f, 0.6f);
				LUNGE.remove(player.getId());
				return;
			}
		}
	}

	// ---------------- Symbiote Grapple (Shift + X) ----------------

	private static void handleGrapple(ServerPlayer player, long now) {
		long readyAt = Symbiote.state(player).abilityCooldowns.get(CDI_GRAPPLE);
		if (now < readyAt) {
			cooldownMessage(player, readyAt - now);
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();

		// An item on the ground / in the air along the aim line is reeled in, Spider-Man style.
		ItemEntity item = nearestItemAlongAim(player, eye, look);
		if (item != null) {
			setCooldown(player, CDI_GRAPPLE, now, CD_GRAPPLE);
			Vec3 toPlayer = player.position().add(0, 0.4, 0).subtract(item.position());
			item.setDeltaMovement(toPlayer.normalize().scale(Math.min(2.0, 0.5 + toPlayer.length() * 0.15)));
			item.setNoPickUpDelay();
			SymbioteAnim.play(player, SymbioteAnim.GRAPPLE);
			SymbioteTendrilEntity.fromHand(player, true, item.position(), item, 12, 3, 0.1f);
			SymbioteSounds.organic(player, 0.9f, 0.7f);
			return;
		}

		// v0.11.15: the grapple needs something to hold onto -- a block or a creature within range. Aimed at
		// open air the tendril still lashes out the full distance, finds nothing, and snaps back.
		boolean hasAnchor = AbilityHelpers.raycastEntity(player, GRAPPLE_RANGE) != null
				|| AbilityHelpers.raycastBlock(player, GRAPPLE_RANGE).getType() != HitResult.Type.MISS;
		if (!hasAnchor) {
			SymbioteAnim.play(player, SymbioteAnim.TENDRIL_STRIKE);
			SymbioteTendrilEntity.fromHand(player, true, eye.add(look.scale(GRAPPLE_RANGE)), null, 10, 4, 0.12f);
			SymbioteSounds.organic(player, 0.6f, 0.5f);
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.grapple_no_anchor"), true);
			return;
		}
		Vec3 anchor = AbilityHelpers.aimPoint(player, GRAPPLE_RANGE);
		if (anchor.distanceTo(eye) < 3.0) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.grapple_too_close"), true);
			return;
		}
		// Aim the pull at a point a little SHORT of the surface, along the line of sight, so the player
		// fetches up next to the block rather than being driven into its face -- this is what used to
		// leave you stuck against a wall when the grapple point was below you.
		Vec3 pullTarget = anchor;
		Vec3 back = eye.subtract(anchor);
		if (back.length() > 2.5) {
			pullTarget = anchor.add(back.normalize().scale(1.6));
		}
		setCooldown(player, CDI_GRAPPLE, now, CD_GRAPPLE);
		GRAPPLE_PULL.put(player.getId(),
				new double[]{pullTarget.x, pullTarget.y, pullTarget.z, now + GRAPPLE_PULL_TICKS, now});
		LEAP_NO_FALL_UNTIL.put(player.getId(), now + LEAP_NO_FALL_TICKS);
		SymbioteAnim.play(player, SymbioteAnim.GRAPPLE);
		holdTendril(player, SymbioteTendrilEntity.fromHand(player, true, anchor, null, GRAPPLE_PULL_TICKS + 4, 3, 0.13f));
		SymbioteVfx.impact(player, anchor, anchor.subtract(eye), 0.5f); // the tip latches on with splayed hooks
		SymbioteSounds.organic(player, 0.9f, 0.4f);
		SymbioteSounds.lash(player, 0.9f, 0.6f);
	}

	private static ItemEntity nearestItemAlongAim(ServerPlayer player, Vec3 eye, Vec3 look) {
		AABB box = player.getBoundingBox().inflate(GRAPPLE_RANGE);
		ItemEntity best = null;
		double bestDot = 0.94;
		for (ItemEntity item : AbilityHelpers.level(player).getEntitiesOfClass(ItemEntity.class, box)) {
			Vec3 to = item.position().subtract(eye);
			double dist = to.length();
			if (dist < 2.0 || dist > GRAPPLE_RANGE) {
				continue;
			}
			double dot = to.scale(1.0 / dist).dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = item;
			}
		}
		return best;
	}

	private static void tickGrapplePull(ServerPlayer player, long now) {
		double[] pull = GRAPPLE_PULL.get(player.getId());
		if (pull == null) {
			return;
		}
		Vec3 anchor = new Vec3(pull[0], pull[1], pull[2]);
		long startTick = pull.length > 4 ? (long) pull[4] : now;
		Vec3 toAnchor = anchor.subtract(player.getEyePosition());
		// End the pull when we arrive, when time runs out, or -- after a couple of ticks of travel --
		// the moment the player collides with terrain. That last case is the fix for "stuck on blocks":
		// re-setting the velocity into a wall/floor every tick was what pinned the player in place.
		boolean travelled = now - startTick >= 2;
		boolean fetchedUp = travelled && (player.horizontalCollision
				|| (player.onGround() && toAnchor.y < 1.0));
		if (now >= (long) pull[3] || toAnchor.length() < 2.5 || fetchedUp) {
			GRAPPLE_PULL.remove(player.getId());
			dropHeldTendril(player);
			return;
		}
		Vec3 dir = toAnchor.normalize();
		double speed = Math.min(1.7, 0.7 + toAnchor.length() * 0.08);
		Vec3 vel = dir.scale(speed);
		// Never let a downward grapple slam the host straight into the ground -- keep a slight lift so
		// it carries them ACROSS to a lower ledge instead of drilling them into it.
		double vy = Math.max(vel.y, -0.4) + 0.12;
		AbilityHelpers.launchSelf(player, new Vec3(vel.x, vy, vel.z));
	}

	// ---------------- Tendril Barrage / Blade Slash (Z) ----------------

	/**
	 * Z -- a flurry of tendrils from alternating hands for 1.3 s. v0.13.19: it fires whether or not anything is
	 * in the sights (so it can miss), every tendril flies down the aim with a little wander, and it reaches as
	 * far as Tendril Strike.
	 */
	private static boolean startBarrage(ServerPlayer player, long now) {
		BARRAGE.put(player.getId(), new long[]{now + BARRAGE_DURATION, 0L, 0L});
		SymbioteAnim.play(player, SymbioteAnim.BARRAGE);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.7f, 1.3f);
		return true;
	}

	private static void tickBarrage(ServerPlayer player, long now) {
		long[] b = BARRAGE.get(player.getId());
		if (b == null) {
			return;
		}
		if (now >= b[0] || !SymbioteVitalsManager.usable(player)) {
			BARRAGE.remove(player.getId());
			return;
		}
		if (now - b[1] < BARRAGE_HIT_INTERVAL) {
			return;
		}
		b[1] = now;
		boolean right = (b[2]++ % 2) == 0;
		ServerLevel level = AbilityHelpers.level(player);
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 end = traceTendril(player, spread(player, player.getLookAngle(), BARRAGE_SPREAD_DEGREES), BARRAGE_RANGE, hit);
		SymbioteTendrilEntity.fromHand(player, right, end, hit[0], 7, 2, 0.1f);
		if (hit[0] != null) {
			LivingEntity target = hit[0];
			target.invulnerableTime = 0;
			if (AbilityHelpers.hurtLands(player, target, BARRAGE_HIT_DAMAGE)) {
				SymbioteVfx.impact(player, target, 0.55f);
			}
		}
		SymbioteSounds.lash(player, 0.5f, 0.9f + player.getRandom().nextFloat() * 0.3f);
	}

	private static boolean bladeSlash(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		SymbioteAnim.play(player, SymbioteAnim.BLADE_SLASH);
		List<LivingEntity> targets = coneTargets(player, BLADE_SLASH_RANGE * Math.max(1.0, player.getScale()), BLADE_SLASH_CONE_DOT);
		for (LivingEntity target : targets) {
			if (AbilityHelpers.hurtLands(player, target, BLADE_SLASH_DAMAGE)) {
				target.hurt(player.damageSources().magic(), 2.0f);
				AbilityHelpers.knockbackFrom(target, player.position(), 0.6);
				SymbioteVfx.impact(player, target, 1.0f);
			}
		}
		// v0.17.5 tendril-vfx: a living blade-crescent sweeps the arc (was vanilla sweep particles)
		SymbioteVfx.slash(player, (float) (BLADE_SLASH_RANGE * Math.max(1.0, player.getScale())));
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
		SymbioteSounds.organic(player, 0.8f, 0.4f);
		return true;
	}

	// ---------------- Symbiote Onslaught (Shift + hold Z) ----------------

	private static void handleOnslaught(ServerPlayer player, boolean pressed, long now) {
		SymbioteState s = Symbiote.state(player);
		if (pressed) {
			if (s.onslaughtChargeStart >= 0) {
				return;
			}
			if (!SymbioteVitalsManager.usable(player)) {
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
				return;
			}
			long readyAt = s.abilityCooldowns.get(CDI_ONSLAUGHT);
			if (now < readyAt) {
				cooldownMessage(player, readyAt - now);
				return;
			}
			SymbioteState c = s.copy();
			c.onslaughtChargeStart = now;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
			SymbioteAnim.play(player, SymbioteAnim.ONSLAUGHT_CHARGE);
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.onslaught_charging"), true);
			AbilityHelpers.sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.5f);
		} else if (s.onslaughtChargeStart >= 0) {
			if (now - s.onslaughtChargeStart >= ONSLAUGHT_CHARGE_TICKS) {
				fireOnslaught(player, s, now);
			} else {
				cancelOnslaught(player, s, "message.projecthero.symbiote.onslaught_interrupted");
			}
		}
	}

	/**
	 * The wind-up: the host's feet spread a black pool, tendrils rise out of the ground in a closing ring and
	 * writhe there, and anything inside is already slowed.
	 */
	private static void tickOnslaught(ServerPlayer player, long now) {
		SymbioteState s = Symbiote.state(player);
		if (s.onslaughtChargeStart < 0) {
			return;
		}
		long held = now - s.onslaughtChargeStart;
		if (held >= ONSLAUGHT_CHARGE_TICKS) {
			fireOnslaught(player, s, now);
			return;
		}
		ServerLevel level = AbilityHelpers.level(player);
		double frac = held / (double) ONSLAUGHT_CHARGE_TICKS;
		double r = ONSLAUGHT_RADIUS * (1.0 - frac * 0.6);
		// v0.17.5 tendril-vfx: the closing ring is drawn by every client from the synced charge (tendrils from the legs anchor in the
		// ground, a ring of claws rises and tightens) -- no ichor particles any more
		// every few ticks a pair of tendrils claws up out of the pool
		if (held % 6 == 0) {
			for (int k = 0; k < 2; k++) {
				double ang = player.getRandom().nextDouble() * Math.PI * 2;
				Vec3 root = groundPoint(level, player.position().add(Math.cos(ang) * r, 0, Math.sin(ang) * r));
				Vec3 tip = root.add(Math.cos(ang) * -0.8, 1.8 + player.getRandom().nextDouble(), Math.sin(ang) * -0.8);
				SymbioteTendrilEntity.fromPoint(level, root, tip, null, 14, 5, 0.16f);
			}
		}
		// enemies caught in the radius are already slowed while it charges
		for (LivingEntity target : enemiesAround(player, player.position(), ONSLAUGHT_RADIUS)) {
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 20, 1);
		}
		if (player.tickCount % 6 == 0) {
			AbilityHelpers.sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.5f + (float) frac);
		}
	}

	private static void cancelOnslaught(ServerPlayer player, SymbioteState s, String messageKey) {
		SymbioteState c = s.copy();
		c.onslaughtChargeStart = -1L;
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		SymbioteAnim.stop(player, SymbioteAnim.ONSLAUGHT_CHARGE);
		if (messageKey != null) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
	}

	/**
	 * The release: the pool erupts. A crown of huge tendrils bursts outward from the host to the edge of the
	 * radius, and a tendril spears up out of the ground into every enemy inside -- 20 damage, Wither III,
	 * Blindness, Slowness IV and Weakness II, and they are thrown into the air -- under a shockwave of living
	 * black. 90 s cooldown.
	 */
	private static void fireOnslaught(ServerPlayer player, SymbioteState s, long now) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 center = player.position();
		SymbioteAnim.play(player, SymbioteAnim.ONSLAUGHT_RELEASE);
		List<LivingEntity> victims = enemiesAround(player, center, ONSLAUGHT_RADIUS);
		long[] track = new long[victims.size() + 1];
		track[0] = now + ONSLAUGHT_DOT_TICKS;
		int idx = 1;
		for (LivingEntity target : victims) {
			Vec3 mid = target.position().add(0, target.getBbHeight() * 0.5, 0);
			Vec3 root = groundPoint(level, target.position().add(
					(player.getRandom().nextDouble() - 0.5) * 1.4, 0, (player.getRandom().nextDouble() - 0.5) * 1.4));
			SymbioteTendrilEntity.fromPoint(level, root, mid, target, 34, 3, 0.2f);
			AbilityHelpers.hurtBurst(player, target, ONSLAUGHT_DAMAGE);
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, ONSLAUGHT_DOT_TICKS, 2, false, true, true), player);
			AbilityHelpers.applyControl(target, MobEffects.BLINDNESS, 100, 0);
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 120, 3);
			AbilityHelpers.applyControl(target, MobEffects.WEAKNESS, ONSLAUGHT_DOT_TICKS, 1);
			AbilityHelpers.push(target, new Vec3(0, 0.85, 0));
			SymbioteVfx.impact(player, target, 1.5f);
			SymbioteVfx.wrap(player, target, 60, 0.8f); // tendrils cling to every victim (was an ink spray for the whole DoT)
			track[idx++] = target.getId();
		}
		if (victims.isEmpty()) {
			ONSLAUGHT_VICTIMS.remove(player.getId());
		} else {
			ONSLAUGHT_VICTIMS.put(player.getId(), track);
		}
		// the crown: eighteen great tendrils thrown outward from the host, arcing down to the edge of the radius
		int crown = 18;
		Vec3 base = center.add(0, 0.9 * player.getScale(), 0);
		for (int i = 0; i < crown; i++) {
			double ang = (Math.PI * 2 * i) / crown;
			Vec3 tip = groundPoint(level, center.add(Math.cos(ang) * ONSLAUGHT_RADIUS, 0, Math.sin(ang) * ONSLAUGHT_RADIUS))
					.add(0, 0.6 + (i % 3) * 0.5, 0);
			SymbioteTendrilEntity.fromPoint(level, base, tip, null, 22, 4 + (i % 3), 0.24f);
		}
		// the shockwave: v0.17.5 tendril-vfx -- two rings of tendrils lash out across the ground (were ichor / sheen rings, a sonic
		// boom, sculk souls and 80 ink)
		SymbioteVfx.groundBurst(player, groundPoint(level, center), (float) ONSLAUGHT_RADIUS);
		SymbioteVfx.groundBurst(player, groundPoint(level, center), (float) (ONSLAUGHT_RADIUS * 0.5));
		SymbioteVfx.bodyBurst(player, 1.6f);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 0.6f);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_ROAR, 1.2f, 0.8f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.9f, 0.5f);
		SymbioteSounds.lash(player, 1.5f, 0.5f);

		SymbioteState c = s.copy();
		c.onslaughtChargeStart = -1L;
		c.abilityCooldowns.set(CDI_ONSLAUGHT, now + CD_ONSLAUGHT);
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
	}

	/** The ground under {@code p}: the first solid block surface within a few blocks up or down. */
	static Vec3 groundPoint(ServerLevel level, Vec3 p) {
		BlockPos pos = BlockPos.containing(p.x, p.y + 1.0, p.z);
		for (int i = 0; i < 5 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++) {
			pos = pos.below();
		}
		for (int i = 0; i < 3 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) {
			pos = pos.above();
		}
		return new Vec3(p.x, pos.getY() + 0.05, p.z);
	}

	private static void tickOnslaughtVictims(ServerPlayer player, long now) {
		long[] track = ONSLAUGHT_VICTIMS.get(player.getId());
		if (track == null) {
			return;
		}
		if (now >= track[0]) {
			ONSLAUGHT_VICTIMS.remove(player.getId());
			return;
		}
		// v0.17.5 tendril-vfx: the victims are wrapped by tendrils when it fires (SymbioteVfx.wrap); no ink spray here any more
	}

	// =====================================================================================================================
	// v0.17.5 moveset v2
	// =====================================================================================================================

	/** Strikes count as "in a row" while each lands within 4 s of the last (the cooldown is 1.5 s). */
	private static final int STRIKE_CHAIN_WINDOW = 80;
	public static final float SLAM_DAMAGE = 15.0f; // v0.17.7 City tier: was 22
	private static final float SLAM_SPLASH_DAMAGE = 11.0f; // v0.17.7 City tier: was 6
	private static final double SLAM_SPLASH_RADIUS = 2.5;
	/** playerId -> {strikes in a row that found something, game time of the last}. */
	private static final Map<Integer, long[]> STRIKE_CHAIN = new ConcurrentHashMap<>();

	private static final int CD_SPEAR = 160;            // 8 s
	/** Ticks of holding past the hold start for a full-power Spear; it throws itself after SPEAR_MAX_HOLD. */
	private static final int SPEAR_FULL_CHARGE = 20;
	private static final int SPEAR_MAX_HOLD = 60;
	public static final double SPEAR_RANGE = 10.0;
	public static final float SPEAR_MIN_DAMAGE = 11.0f; // v0.17.7 City tier: was 14
	public static final float SPEAR_MAX_DAMAGE = 15.0f; // v0.17.7 City tier: was 26
	private static final int SPEAR_PIN_TICKS = 20;
	/** playerId -> game time the Spear's charge began. */
	private static final Map<Integer, Long> SPEAR = new ConcurrentHashMap<>();
	/** playerId -> {pinned target id, end tick, x, y, z}: the Spear's first victim held in place. */
	private static final Map<Integer, double[]> PINNED = new ConcurrentHashMap<>();

	private static final int CD_RAIN = 280;             // 14 s
	private static final int RAIN_TICKS = 60;
	private static final int RAIN_INTERVAL = 3;
	private static final int RAIN_PER_VOLLEY = 2;
	private static final double RAIN_RADIUS = 4.0;
	private static final double RAIN_RANGE = 24.0;
	private static final double RAIN_HEIGHT = 11.0;
	public static final float RAIN_SPIKE_DAMAGE = 5.0f; // v0.17.7 City tier: was 3 (tick-like: about 6 land on one target, ~30 over the 3 s)
	/** playerId -> {centre x, y, z, end tick, last volley tick, spawn height}. */
	private static final Map<Integer, double[]> RAIN = new ConcurrentHashMap<>();

	private static final int CD_DIVE = 120;             // 6 s
	private static final double DIVE_MIN_HEIGHT = 2.5;
	private static final double DIVE_RADIUS = 4.5;
	public static final float DIVE_BASE_DAMAGE = 11.0f; // v0.17.7 City tier: was 12
	private static final float DIVE_MAX_BONUS = 4.0f; // v0.17.7 City tier: was 12 (max 15, was 24)
	/** v0.17.7 City tier: the Dive Bomb adds this a block fallen (was 0.8). */
	private static final float DIVE_PER_BLOCK = 0.4f;
	private static final int DIVE_MAX_TICKS = 80;
	/** playerId -> {start y, start tick}. */
	private static final Map<Integer, double[]> DIVE = new ConcurrentHashMap<>();

	public static final int CD_ERUPTION = 160;          // 8 s (v0.17.5 moveset v3; was 12 s)
	private static final int ERUPTION_STEPS = 8;
	private static final double ERUPTION_STEP = 1.5;
	private static final double ERUPTION_HIT_RADIUS = 1.7;
	public static final float ERUPTION_DAMAGE = 12.0f;

	private static final class Eruption {
		final Vec3 origin;
		final Vec3 dir;
		final long start;
		int step;
		final java.util.Set<Integer> hit = new java.util.HashSet<>();

		Eruption(Vec3 origin, Vec3 dir, long start) {
			this.origin = origin;
			this.dir = dir;
			this.start = start;
		}
	}

	private static final Map<Integer, Eruption> ERUPTION = new ConcurrentHashMap<>();

	private static final int CD_MAW = 100;              // 5 s
	private static final double MAW_RANGE = 5.0;
	public static final float MAW_DAMAGE = 15.0f; // v0.17.7 City tier: was 18
	public static final float MAW_BIOMASS = 12.0f;

	private static final int CD_DEVOUR = 400;           // 20 s
	private static final double DEVOUR_RANGE = 4.5;
	public static final float DEVOUR_THRESHOLD = 0.25f;
	public static final float DEVOUR_BIOMASS = 40.0f;
	private static final float DEVOUR_HOST_HEAL = 6.0f;
	public static final float HEAVY_BITE_DAMAGE = 15.0f; // v0.17.7 City tier: was 30
	private static final float HEAVY_BITE_BIOMASS = 20.0f;
	/** Ticks the tongue takes to drag the prey into the maw. */
	public static final int DEVOUR_PULL_TICKS = 14;
	/** The first ticks of a Devour the tongue spends shooting out and wrapping the prey (it is held, not yet pulled). */
	private static final int DEVOUR_WRAP_TICKS = 5;
	/** playerId -> {target id, start tick}. */
	private static final Map<Integer, long[]> DEVOUR = new ConcurrentHashMap<>();

	/** Punches count as "in a row" while each lands within 1.5 s of the last. */
	private static final int PUNCH_CHAIN_WINDOW = 30;
	public static final double PUNCH_KNOCKBACK = 1.4;
	/** playerId -> {punches in a row, game time of the last}. */
	private static final Map<Integer, long[]> PUNCH_CHAIN = new ConcurrentHashMap<>();
	private static final net.minecraft.resources.ResourceLocation REACH_ID = com.projecthero.mod.ProjectHeroMod.id("symbiote_suit_reach");
	public static final double SUIT_REACH_BONUS = 1.0;

	/** Bosses (300+ max health, the Wither, the Dragon, 200+ like the grab rule) are never devoured or yanked. */
	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= 200.0f || com.projecthero.mod.titanshifter.TitanCombat.isBoss(target);
	}

	private static Vec3 mid(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	// ---------------- Tendril Slam (R, every 3rd strike in a row) ----------------

	/**
	 * The tendril wraps the target, yanks it in to just in front of the host and smashes it into the ground: 22 damage, Slowness II
	 * for 2 s, and 6 to anything else within 2.5 blocks of the impact. A boss (or anything the grab rules refuse) is smashed where
	 * it stands instead of being yanked.
	 */
	private static void tendrilSlam(ServerPlayer player, LivingEntity target) {
		ServerLevel level = AbilityHelpers.level(player);
		SymbioteAnim.play(player, SymbioteAnim.TENDRIL_SLAM);
		SymbioteTendrilEntity.fromHand(player, true, mid(target), target, 16, 3, 0.18f);
		if (!isBoss(target) && AbilityHelpers.isValidGrabTarget(target, player)) {
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
			Vec3 dest = groundPoint(level, player.position().add(flat.scale(2.2 * Math.max(1.0, player.getScale()))));
			if (level.noCollision(target, target.getBoundingBox().move(dest.subtract(target.position())))) {
				target.teleportTo(dest.x, dest.y, dest.z);
			}
			target.setDeltaMovement(0, -0.8, 0);
			target.hurtMarked = true;
		}
		Vec3 impact = target.position();
		if (AbilityHelpers.hurtBurst(player, target, SLAM_DAMAGE)) {
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
		}
		for (LivingEntity other : enemiesAround(player, impact, SLAM_SPLASH_RADIUS)) {
			if (other != target) {
				AbilityHelpers.hurt(player, other, SLAM_SPLASH_DAMAGE);
				AbilityHelpers.knockbackFrom(other, impact, 0.6);
			}
		}
		// v0.17.5 tendril-vfx: the target is wrapped as it is yanked, and tendrils burst out of the ground where it lands (were an
		// ichor ring, ink and an explosion particle)
		SymbioteVfx.wrap(player, target, 14, 1.1f);
		SymbioteVfx.groundBurst(player, impact, 2.2f);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.4f);
		SymbioteSounds.organic(player, 1.0f, 0.4f);
		SymbioteSounds.lash(player, 1.0f, 0.6f);
	}

	// ---------------- Impaling Spear (hold R, release) ----------------

	private static void beginSpear(ServerPlayer player, long now) {
		long readyAt = Symbiote.state(player).abilityCooldowns.get(CDI_SPEAR);
		if (now < readyAt) {
			cooldownMessage(player, readyAt - now);
			return;
		}
		SPEAR.put(player.getId(), now);
		SymbioteAnim.play(player, SymbioteAnim.SPEAR_CHARGE);
		SymbioteSounds.organic(player, 0.8f, 0.5f);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_HEARTBEAT, 0.7f, 1.2f);
	}

	/** 0..1: how charged the Spear is. */
	public static float spearCharge(ServerPlayer player, long now) {
		Long start = SPEAR.get(player.getId());
		return start == null ? 0.0f : Math.min(1.0f, (now - start) / (float) SPEAR_FULL_CHARGE);
	}

	public static boolean spearCharging(ServerPlayer player) {
		return SPEAR.containsKey(player.getId());
	}

	private static void tickSpear(ServerPlayer player, long now) {
		Long start = SPEAR.get(player.getId());
		if (start == null) {
			return;
		}
		if (now - start >= SPEAR_MAX_HOLD) {
			fireSpear(player, now);
			return;
		}
		// v0.17.5 tendril-vfx: the spear growing in the fist (tendrils spiralling down the arm into it) is drawn by every client from
		// the synced SPEAR_CHARGE pose -- no ichor spiral particles any more
	}

	/**
	 * Release: a thick tendril spear rams 10 blocks down the aim (blocks stop it) and pierces everything on the line --
	 * 14 damage at the hold's start up to 26 fully charged (another second) -- and the first thing it runs through is pinned
	 * in place for 1 s (not a boss). 8 s cooldown, started on the throw.
	 */
	private static void fireSpear(ServerPlayer player, long now) {
		float charge = spearCharge(player, now);
		SPEAR.remove(player.getId());
		SymbioteAnim.play(player, SymbioteAnim.SPEAR_THROW);
		setCooldown(player, CDI_SPEAR, now, CD_SPEAR);
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 far = eye.add(look.scale(SPEAR_RANGE));
		HitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
		float damage = SPEAR_MIN_DAMAGE + (SPEAR_MAX_DAMAGE - SPEAR_MIN_DAMAGE) * charge;
		List<LivingEntity> line = new java.util.ArrayList<>(enemiesAround(player, eye.add(end).scale(0.5), SPEAR_RANGE * 0.5 + 2.0)
				.stream().filter(e -> e.getBoundingBox().inflate(0.6).clip(eye, end).isPresent()).toList());
		line.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
		SymbioteTendrilEntity.fromHand(player, true, end, null, 14, 2, 0.26f + 0.08f * charge);
		boolean first = true;
		for (LivingEntity target : line) {
			if (AbilityHelpers.hurtBurst(player, target, damage)) {
				SymbioteVfx.impact(player, target, 1.2f);
				if (first && !isBoss(target)) {
					PINNED.put(player.getId(), new double[]{target.getId(), now + SPEAR_PIN_TICKS, target.getX(), target.getY(), target.getZ()});
					AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, SPEAR_PIN_TICKS, 9);
					SymbioteTendrilEntity.fromPoint(player, groundPoint(level, target.position()), mid(target), target,
							SPEAR_PIN_TICKS, 2, 0.16f);
				} else {
					AbilityHelpers.knockbackFrom(target, player.position(), 0.5);
				}
				first = false;
			}
		}
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_THROW, 1.0f, 0.6f);
		SymbioteSounds.lash(player, 1.2f, 0.5f);
		SymbioteSounds.organic(player, 0.9f, 0.6f);
	}

	/** The entity the Spear has pinned, or -1 (tests). */
	public static int pinnedTarget(ServerPlayer player) {
		double[] pin = PINNED.get(player.getId());
		return pin == null ? -1 : (int) pin[0];
	}

	public static boolean raining(ServerPlayer player) {
		return RAIN.containsKey(player.getId());
	}

	private static void tickPinned(ServerPlayer player, long now) {
		double[] pin = PINNED.get(player.getId());
		if (pin == null) {
			return;
		}
		Entity e = AbilityHelpers.level(player).getEntity((int) pin[0]);
		if (now >= (long) pin[1] || !(e instanceof LivingEntity target) || !target.isAlive()) {
			PINNED.remove(player.getId());
			return;
		}
		if (!(target instanceof Player)) {
			target.teleportTo(pin[2], pin[3], pin[4]);
		}
		target.setDeltaMovement(Vec3.ZERO);
		target.hurtMarked = true;
	}

	// ---------------- Spike Rain (hold G) ----------------

	/**
	 * Spikes burst up out of the host's back and arms, then rain on the aimed area (up to 24 blocks away, 4-block radius) for
	 * 3 s: two spikes every 3 ticks, 4 damage and Wither I for 2 s each. Spikes respect hit invulnerability, so one target takes
	 * a hit about every half second. 14 s cooldown.
	 */
	private static boolean spikeRain(ServerPlayer player, long now) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 centre = groundPoint(level, AbilityHelpers.aimPoint(player, RAIN_RANGE));
		// rain from up to 11 blocks above -- lower under a roof
		Vec3 up = centre.add(0, 1.0, 0);
		HitResult roof = level.clip(new ClipContext(up, up.add(0, RAIN_HEIGHT, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double height = roof.getType() == HitResult.Type.MISS ? RAIN_HEIGHT : Math.max(1.5, roof.getLocation().y - centre.y - 0.8);
		RAIN.put(player.getId(), new double[]{centre.x, centre.y, centre.z, now + RAIN_TICKS, now - RAIN_INTERVAL, height});
		SymbioteAnim.play(player, SymbioteAnim.SPIKE_RAIN);
		SymbioteVfx.groundMark(player, centre, (float) RAIN_RADIUS, RAIN_TICKS); // tendrils writhe round the marked circle
		// the launch: a volley thrown up out of both hands
		for (int i = 0; i < 6; i++) {
			Vec3 dir = new Vec3((player.getRandom().nextDouble() - 0.5) * 0.4, 1.0, (player.getRandom().nextDouble() - 0.5) * 0.4).normalize();
			SymbioteSpikeEntity.rain(player, SymbioteHands.hand(player, i % 2 == 0), dir, RAIN_SPIKE_DAMAGE, 40, 2.4f);
		}
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_THROW, 0.9f, 0.8f);
		SymbioteSounds.organic(player, 1.0f, 0.9f);
		return true;
	}

	private static void tickRain(ServerPlayer player, long now) {
		double[] r = RAIN.get(player.getId());
		if (r == null) {
			return;
		}
		if (now >= (long) r[3]) {
			RAIN.remove(player.getId());
			return;
		}
		ServerLevel level = AbilityHelpers.level(player);
		// (the marked ground is a SymbioteVfx.groundMark sent once when the rain starts -- no ichor ring every 5 ticks)
		if (now - (long) r[4] < RAIN_INTERVAL) {
			return;
		}
		r[4] = now;
		Vec3 centre = new Vec3(r[0], r[1], r[2]);
		List<LivingEntity> inside = enemiesAround(player, centre, RAIN_RADIUS + 0.5).stream()
				.filter(e -> e.position().subtract(centre).horizontalDistance() <= RAIN_RADIUS).toList();
		for (int i = 0; i < RAIN_PER_VOLLEY; i++) {
			Vec3 from;
			if (i == 0 && !inside.isEmpty()) {
				// one spike a volley falls on something in the circle (the rain is the Symbiote's -- it aims)
				LivingEntity t = inside.get(player.getRandom().nextInt(inside.size()));
				from = new Vec3(t.getX() + (player.getRandom().nextDouble() - 0.5) * 0.5, r[1] + r[5],
						t.getZ() + (player.getRandom().nextDouble() - 0.5) * 0.5);
			} else {
				double a = player.getRandom().nextDouble() * Math.PI * 2;
				double d = Math.sqrt(player.getRandom().nextDouble()) * RAIN_RADIUS;
				from = new Vec3(r[0] + Math.cos(a) * d, r[1] + r[5], r[2] + Math.sin(a) * d);
			}
			Vec3 dir = new Vec3((player.getRandom().nextDouble() - 0.5) * 0.08, -1.0, (player.getRandom().nextDouble() - 0.5) * 0.08);
			SymbioteSpikeEntity.rain(player, from, dir.normalize(), RAIN_SPIKE_DAMAGE, 40, 1.9f);
		}
		if (now % 6 == 0) {
			level.playSound(null, r[0], r[1] + 2, r[2], SoundEvents.ARROW_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 0.6f);
		}
	}

	// ---------------- Dive Bomb (X in the air) ----------------

	/** X in the air: at least 2.5 blocks clear below the host (a hop still lunges), not in water. */
	public static boolean canDiveBomb(ServerPlayer player) {
		if (player.onGround() || player.isInWater() || player.isPassenger() || player.getAbilities().flying) {
			return false;
		}
		Vec3 feet = player.position();
		HitResult below = player.level().clip(new ClipContext(feet, feet.add(0, -DIVE_MIN_HEIGHT, 0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		return below.getType() == HitResult.Type.MISS;
	}

	/** The Symbiote drives the host straight down (a little along the aim); the landing is the slam ({@link #diveImpact}). */
	private static boolean diveBomb(ServerPlayer player, long now) {
		LUNGE.remove(player.getId());
		DIVE.put(player.getId(), new double[]{player.getY(), now});
		LEAP_NO_FALL_UNTIL.put(player.getId(), now + LEAP_NO_FALL_TICKS);
		Vec3 look = player.getLookAngle();
		AbilityHelpers.launchSelf(player, new Vec3(look.x * 0.35, -2.4, look.z * 0.35));
		SymbioteAnim.play(player, SymbioteAnim.DIVE_BOMB); // the clients draw the dive's wake and claws from this pose (was ink)
		SymbioteSounds.lash(player, 1.0f, 0.5f);
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_SWOOP, 0.8f, 0.6f);
		return true;
	}

	public static boolean diving(ServerPlayer player) {
		return DIVE.containsKey(player.getId());
	}

	private static void tickDive(ServerPlayer player, long now) {
		double[] d = DIVE.get(player.getId());
		if (d == null) {
			return;
		}
		boolean landed = now - (long) d[1] >= 1 && (player.onGround() || player.isInWater());
		if (landed || now - (long) d[1] >= DIVE_MAX_TICKS) {
			DIVE.remove(player.getId());
			diveImpact(player, Math.max(0.0, d[0] - player.getY()));
			return;
		}
		// keep it a dive, not a float: the push down is renewed if something slowed it
		Vec3 v = player.getDeltaMovement();
		if (v.y > -1.6) {
			AbilityHelpers.launchSelf(player, new Vec3(v.x, -2.0, v.z));
		}
	}

	/**
	 * The landing: a 4.5-block slam -- 12 damage plus 0.8 per block fallen (up to 24) -- that throws everything up and out,
	 * while tendrils burst out of the ground in a ring.
	 */
	private static void diveImpact(ServerPlayer player, double fallen) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 c = player.position();
		float damage = DIVE_BASE_DAMAGE + (float) Math.min(DIVE_MAX_BONUS, fallen * DIVE_PER_BLOCK);
		SymbioteAnim.play(player, SymbioteAnim.DIVE_IMPACT);
		for (LivingEntity target : enemiesAround(player, c, DIVE_RADIUS)) {
			if (AbilityHelpers.hurtBurst(player, target, damage) && !isBoss(target)) {
				AbilityHelpers.knockbackFrom(target, c, 1.1);
				AbilityHelpers.push(target, new Vec3(0, 0.6, 0));
			}
		}
		for (int i = 0; i < 10; i++) {
			double a = Math.PI * 2 * i / 10;
			Vec3 root = groundPoint(level, c.add(Math.cos(a) * 1.2, 0, Math.sin(a) * 1.2));
			Vec3 tip = groundPoint(level, c.add(Math.cos(a) * DIVE_RADIUS, 0, Math.sin(a) * DIVE_RADIUS)).add(0, 1.0 + (i % 2) * 0.6, 0);
			SymbioteTendrilEntity.fromPoint(player, root, tip, null, 16, 3, 0.18f);
		}
		// v0.17.5 tendril-vfx: a ring of tendrils lashes out over the ground (were sheen / ichor rings, explosions and ink)
		SymbioteVfx.groundBurst(player, c, (float) DIVE_RADIUS);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.8f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.6f, 1.2f);
		SymbioteSounds.organic(player, 1.0f, 0.4f);
	}

	// ---------------- Ground Eruption (hold Z) ----------------

	/**
	 * Tendrils burst out of the ground in a travelling line ahead -- a burst every 1.5 blocks out to 12, one a tick -- and
	 * everything they come up under takes 12, is launched up (not a boss) and slowed for 3 s. Each thing is hit once. 12 s cooldown.
	 */
	private static boolean groundEruption(ServerPlayer player, long now) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		ERUPTION.put(player.getId(), new Eruption(player.position(), flat, now));
		SymbioteAnim.play(player, SymbioteAnim.ERUPTION);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_DIG, 0.9f, 1.3f);
		SymbioteSounds.organic(player, 1.0f, 0.5f);
		return true;
	}

	private static void tickEruption(ServerPlayer player, long now) {
		Eruption e = ERUPTION.get(player.getId());
		if (e == null) {
			return;
		}
		int due = (int) Math.min(ERUPTION_STEPS, now - e.start + 1);
		ServerLevel level = AbilityHelpers.level(player);
		while (e.step < due) {
			e.step++;
			Vec3 root = groundPoint(level, e.origin.add(e.dir.scale(ERUPTION_STEP * e.step)));
			for (int k = 0; k < 3; k++) {
				Vec3 jitter = new Vec3((player.getRandom().nextDouble() - 0.5) * 1.0, 0, (player.getRandom().nextDouble() - 0.5) * 1.0);
				Vec3 tip = root.add(jitter.scale(0.6)).add(e.dir.scale(0.3)).add(0, 2.0 + player.getRandom().nextDouble() * 1.2, 0);
				SymbioteTendrilEntity.fromPoint(player, root.add(jitter), tip, null, 14, 2, 0.17f + k * 0.03f);
			}
			// v0.17.5 tendril-vfx: smaller tendrils whip out of the broken ground round each burst (was ink + ichor); a little
			// block dust stays, since that is the ground breaking, not the Symbiote
			SymbioteVfx.groundBurst(player, root, 1.3f);
			BlockPos under = BlockPos.containing(root.x, root.y - 0.5, root.z);
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(under)),
					root.x, root.y + 0.2, root.z, 5, 0.5, 0.1, 0.5, 0.1);
			for (LivingEntity target : enemiesAround(player, root, ERUPTION_HIT_RADIUS + 1.5)) {
				Vec3 to = target.position().subtract(root);
				if (e.hit.contains(target.getId()) || to.horizontalDistance() > ERUPTION_HIT_RADIUS + target.getBbWidth() * 0.5
						|| Math.abs(to.y) > 2.5) {
					continue;
				}
				e.hit.add(target.getId());
				if (AbilityHelpers.hurtBurst(player, target, ERUPTION_DAMAGE)) {
					AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
					if (!isBoss(target)) {
						AbilityHelpers.push(target, new Vec3(e.dir.x * 0.2, 1.1, e.dir.z * 0.2));
					}
				}
			}
			if (e.step % 2 == 0) {
				level.playSound(null, root.x, root.y, root.z, SoundEvents.ROOTED_DIRT_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.6f);
			}
		}
		if (e.step >= ERUPTION_STEPS) {
			ERUPTION.remove(player.getId());
		}
	}

	// ---------------- Maw Bite (C) ----------------

	/** The nearest enemy in front of the host within {@code range} (aim first, then a narrow cone). */
	private static LivingEntity biteTarget(ServerPlayer player, double range) {
		LivingEntity aimed = AbilityHelpers.raycastEntity(player, range);
		if (aimed != null && !Squads.areAllies(player, aimed) && aimed != player) {
			return aimed;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : enemiesAround(player, player.position(), range)) {
			Vec3 to = mid(e).subtract(eye);
			double d = to.length();
			if (d > 0.01 && to.scale(1.0 / d).dot(look) >= 0.75 && d < bestD) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	/**
	 * C: the host snaps forward (a short lunge toward the target, up to 5 blocks) and the Symbiote's jaws close on it: 18 damage,
	 * and the Symbiote takes back 12 Biomass when it lands. A miss still lunges. 5 s cooldown.
	 */
	private static boolean mawBite(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		LivingEntity target = biteTarget(player, MAW_RANGE);
		SymbioteAnim.play(player, SymbioteAnim.MAW_BITE);
		Vec3 look = player.getLookAngle();
		Vec3 toward = target != null ? target.position().subtract(player.position()) : new Vec3(look.x, 0, look.z).scale(2.0);
		double dist = toward.horizontalDistance();
		if (dist > 1.6) {
			Vec3 flat = new Vec3(toward.x, 0, toward.z).normalize();
			AbilityHelpers.launchSelf(player, flat.scale(Math.min(1.3, 0.35 + (dist - 1.6) * 0.3)).add(0, 0.15, 0));
		}
		AbilityHelpers.sound(player, SoundEvents.EVOKER_FANGS_ATTACK, 1.0f, 0.7f);
		SymbioteSounds.organic(player, 1.0f, 0.5f);
		// v0.17.5 tendril-vfx: two jaw-tendrils from the shoulders snap shut on the prey (or on the air ahead) -- were crits, damage
		// indicators and ink
		SymbioteVfx.jaws(player, target, target != null ? mid(target) : player.getEyePosition().add(look.scale(2.2)), 1.0f);
		if (target != null && AbilityHelpers.hurtBurst(player, target, MAW_DAMAGE)) {
			SymbioteVitalsManager.gainBiomass(player, MAW_BIOMASS);
			SymbioteVfx.impact(player, target, 0.9f);
			AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
		}
		return true;
	}

	// ---------------- Devour (hold C) ----------------

	/** Could hold-C devour {@code target} right now? Under 25% health, not a boss, not a player, not a dummy / armour stand. */
	public static boolean devourable(ServerPlayer player, LivingEntity target) {
		return target != null && target.isAlive() && !(target instanceof Player) && !isBoss(target)
				&& AbilityHelpers.isValidGrabTarget(target, player)
				&& target.getHealth() <= target.getMaxHealth() * DEVOUR_THRESHOLD;
	}

	/**
	 * Hold C: on a target under 25% health the tongue lashes out, drags it into the maw over 0.7 s and the Symbiote eats it --
	 * it dies, the Symbiote takes back 40 Biomass and the host heals 3 hearts (20 s cooldown). A boss is too big to eat: it takes a
	 * heavy bite instead (30 damage, 20 Biomass, the same 20 s cooldown). Anything else (above 25%, a player, nothing in reach, or
	 * Devour cooling down) gets a Maw Bite.
	 */
	private static void devourOrBite(ServerPlayer player, long now) {
		LivingEntity target = biteTarget(player, DEVOUR_RANGE);
		long readyAt = Symbiote.state(player).abilityCooldowns.get(CDI_DEVOUR);
		boolean devourReady = now >= readyAt && !DEVOUR.containsKey(player.getId());
		if (target != null && devourReady && isBoss(target)) {
			heavyBite(player, target, now);
			return;
		}
		if (target != null && devourReady && devourable(player, target)) {
			DEVOUR.put(player.getId(), new long[]{target.getId(), now});
			setCooldown(player, CDI_DEVOUR, now, CD_DEVOUR);
			target.setNoGravity(true);
			target.setDeltaMovement(Vec3.ZERO);
			SymbioteAnim.play(player, SymbioteAnim.DEVOUR);
			// the tongue itself: a thin red tendril from the maw to the prey (it follows the prey as it is reeled in)
			SymbioteTendrilEntity tongue = SymbioteTendrilEntity.fromPoint(player.level(), mouthPoint(player), mid(target), target,
					DEVOUR_PULL_TICKS + 1, 3, 0.07f);
			if (tongue != null) {
				tongue.setCrimson(true);
			}
			SymbioteVfx.wrap(player, target, DEVOUR_PULL_TICKS, 0.9f); // tendrils bind the prey as it is reeled in (was ink)
			AbilityHelpers.sound(player, SoundEvents.WARDEN_ROAR, 0.7f, 1.5f);
			SymbioteSounds.organic(player, 1.0f, 0.4f);
			return;
		}
		tryWithCooldown(player, CDI_MAW, now, CD_MAW, () -> mawBite(player));
	}

	private static void heavyBite(ServerPlayer player, LivingEntity target, long now) {
		setCooldown(player, CDI_DEVOUR, now, CD_DEVOUR);
		SymbioteAnim.play(player, SymbioteAnim.MAW_BITE);
		ServerLevel level = AbilityHelpers.level(player);
		if (AbilityHelpers.hurtBurst(player, target, HEAVY_BITE_DAMAGE)) {
			SymbioteVitalsManager.gainBiomass(player, HEAVY_BITE_BIOMASS);
			SymbioteVfx.jaws(player, target, mid(target), 1.4f);
			SymbioteVfx.impact(player, target, 1.3f);
		}
		AbilityHelpers.sound(player, SoundEvents.EVOKER_FANGS_ATTACK, 1.2f, 0.5f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ATTACK, 1.0f, 0.7f);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.devour_too_big"), true);
	}

	/** Just in front of the host's mouth. */
	private static Vec3 mouthPoint(ServerPlayer player) {
		return player.getEyePosition().add(player.getLookAngle().scale(0.9 * Math.max(1.0, player.getScale()))).add(0, -0.2, 0);
	}

	public static boolean devouring(ServerPlayer player) {
		return DEVOUR.containsKey(player.getId());
	}

	private static void tickDevour(ServerPlayer player, long now) {
		long[] d = DEVOUR.get(player.getId());
		if (d == null) {
			return;
		}
		ServerLevel level = AbilityHelpers.level(player);
		Entity e = level.getEntity((int) d[0]);
		if (!(e instanceof LivingEntity target) || !target.isAlive() || player.distanceToSqr(target) > 64.0) {
			releaseDevour(player);
			return;
		}
		long age = now - d[1];
		Vec3 mouth = mouthPoint(player);
		if (age < DEVOUR_PULL_TICKS) {
			// the tongue shoots out and wraps it (held where it is), then reels it into the maw
			Vec3 at = mid(target);
			double f = age < DEVOUR_WRAP_TICKS ? 0.0 : Math.min(1.0, (age - DEVOUR_WRAP_TICKS + 1) / (double) (DEVOUR_PULL_TICKS - DEVOUR_WRAP_TICKS));
			f = f * f * (3 - 2 * f);
			Vec3 next = at.add(mouth.subtract(at).scale(Math.min(1.0, f * 0.6)));
			target.teleportTo(next.x, next.y - target.getBbHeight() * 0.5, next.z);
			target.setDeltaMovement(Vec3.ZERO);
			target.fallDistance = 0;
			return;
		}
		// the jaws close
		DEVOUR.remove(player.getId());
		target.setNoGravity(false);
		Vec3 m = mid(target);
		AbilityHelpers.hurtBurst(player, target, target.getHealth() + target.getAbsorptionAmount() + 100.0f);
		if (target.isAlive()) {
			target.kill();
		}
		SymbioteVitalsManager.gainBiomass(player, DEVOUR_BIOMASS);
		player.heal(DEVOUR_HOST_HEAL);
		// v0.17.5 tendril-vfx: the jaw-tendrils close over it (were ink, sheen and damage indicators)
		SymbioteVfx.jaws(player, null, m, 1.1f);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_EAT, 1.2f, 0.5f);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_BURP, 1.0f, 0.6f);
		SymbioteSounds.organic(player, 1.2f, 0.3f);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.devoured", target.getDisplayName())
				.withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
	}

	/** Lets go of a half-eaten target (it falls again). */
	private static void releaseDevour(ServerPlayer player) {
		long[] d = DEVOUR.remove(player.getId());
		if (d != null && player.level().getEntity((int) d[0]) instanceof LivingEntity target) {
			target.setNoGravity(false);
		}
	}

	// ---------------- passive: suit-on reach and the 3rd punch ----------------

	/** The suit is on and the kit is the host's: Normal host only (Black Suit Spider-Man and Agent Venom keep their own). */
	public static boolean suitPassivesLive(ServerPlayer player) {
		return hasContext(player) && Symbiote.isActive(player);
	}

	private static void reconcileReach(ServerPlayer player) {
		if (suitPassivesLive(player)) {
			com.projecthero.mod.hero.power.PowerToggles.modifier(player, net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE,
					REACH_ID, SUIT_REACH_BONUS, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
		} else {
			com.projecthero.mod.hero.power.PowerToggles.clearModifier(player,
					net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
		}
	}

	/**
	 * A melee swing that reached a living thing (Fabric's attack callback, server side): with the suit on, every 3rd punch in a row
	 * (each within 1.5 s of the last) throws the target back. @return true if this punch was the knockback one.
	 */
	public static boolean onMeleeSwing(ServerPlayer player, Entity hit) {
		if (!(hit instanceof LivingEntity target) || !suitPassivesLive(player) || Squads.areAllies(player, target)) {
			return false;
		}
		long now = player.level().getGameTime();
		long[] c = PUNCH_CHAIN.get(player.getId());
		int count = c != null && now - c[1] <= PUNCH_CHAIN_WINDOW ? (int) c[0] + 1 : 1;
		if (count >= 3) {
			PUNCH_CHAIN.remove(player.getId());
			if (!isBoss(target)) {
				AbilityHelpers.knockbackFrom(target, player.position(), PUNCH_KNOCKBACK);
				AbilityHelpers.push(target, new Vec3(0, 0.3, 0));
			}
			SymbioteVfx.impact(player, target, 0.9f);
			SymbioteSounds.lash(player, 0.8f, 0.7f);
			return true;
		}
		PUNCH_CHAIN.put(player.getId(), new long[]{count, now});
		return false;
	}

	public static void registerEvents() {
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((p, world, hand, entity, hitResult) -> {
			// v0.17.5 moveset v3: holding a grab, a left click is the crush -- never a punch on the held target or anyone else
			// (the client normally sends the crush itself and swallows the click; this is the backstop)
			if (p != null && Symbiote.hasSymbiote(p) && p.getAttachedOrCreate(ModAttachments.SYMBIOTE_STATE).tendrilGrabHeld
					&& SymbioteHostType.of(p) == SymbioteHostType.NORMAL) {
				if (!world.isClientSide && p instanceof ServerPlayer sp) {
					crush(sp);
				}
				return net.minecraft.world.InteractionResult.FAIL;
			}
			if (!world.isClientSide && p instanceof ServerPlayer sp && !sp.isSpectator()) {
				onMeleeSwing(sp, entity);
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		// v0.17.5 moveset v3: a target the grab let go of (set down, a released player) takes no fall damage from it
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) && !SAFE_LANDING.isEmpty() && safeLanding(entity)) {
				entity.resetFallDistance();
				SAFE_LANDING.remove(entity.getId());
				return false;
			}
			return true;
		});
	}

	// ---------------- Symbiote Shield (Shift + V toggle) ----------------

	/**
	 * v0.13.19: no time limit any more -- the shield stays up until the host drops it or the Biomass runs out;
	 * it costs Biomass while it is up instead ({@link SymbioteVitalsManager#tick}).
	 */
	private static void toggleShield(ServerPlayer player) {
		SymbioteState s = Symbiote.state(player);
		if (s.shieldHeld) {
			SymbioteState c = s.copy();
			c.shieldHeld = false;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
			return;
		}
		if (!SymbioteVitalsManager.usable(player) || SymbioteVitalsManager.biomass(player) <= 0.0f) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
			return;
		}
		SymbioteState c = s.copy();
		c.shieldHeld = true;
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		AbilityHelpers.sound(player, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.5f);
		SymbioteVfx.bodyBurst(player, 0.8f); // the shield itself (a woven tendril disc) is drawn by every client from shieldHeld
	}

	private static void tickShield(ServerPlayer player) {
		SymbioteState s = Symbiote.state(player);
		if (!s.shieldHeld) {
			return;
		}
		if (!SymbioteVitalsManager.usable(player)) {
			SymbioteState c = s.copy();
			c.shieldHeld = false;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.shield_spent"), true);
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 1, true, true, true));
		// v0.17.5 tendril-vfx: the shield is a woven tendril disc drawn by every client from the synced shieldHeld (SymbioteVfxClient),
		// no more ink ring every other tick
	}

	// ---------------- sound-attack shock + resurrection ----------------

	/** A sound attack rips every active Symbiote ability out of the host's control at once. */
	public static void disrupt(ServerPlayer player) {
		long now = player.level().getGameTime();
		SymbioteState s = Symbiote.state(player);
		if (s.tendrilGrabHeld) {
			releaseGrabQuietly(player, s, now);
		}
		s = Symbiote.state(player);
		if (s.onslaughtChargeStart >= 0) {
			cancelOnslaught(player, s, null);
		}
		s = Symbiote.state(player);
		if (s.shieldHeld) {
			SymbioteState c = s.copy();
			c.shieldHeld = false;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		}
		GRAPPLE_PULL.remove(player.getId());
		BARRAGE.remove(player.getId());
		LUNGE.remove(player.getId());
		ONSLAUGHT_VICTIMS.remove(player.getId());
		dropHeldTendril(player);
		stopMovesV2(player);
	}

	private static final double RESURRECT_RADIUS = 20.0;
	private static final double RESURRECT_PUSH = 2.8;

	/**
	 * The resurrection burst: massive tendrils erupt from the host in every direction and hurl every living
	 * thing within 20 blocks away from them. Nothing takes damage -- it is pure separation, so the freshly
	 * revived host has room to breathe.
	 */
	public static void resurrectionBlast(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 origin = player.position().add(0, player.getBbHeight() * 0.6, 0);
		SymbioteAnim.play(player, SymbioteAnim.RESURRECT);

		// The tendrils: a wide crown of real tendrils reaching the full radius.
		int rays = 20;
		for (int i = 0; i < rays; i++) {
			double ang = (Math.PI * 2 * i) / rays;
			double rise = 0.05 + (i % 3) * 0.12;
			Vec3 dir = new Vec3(Math.cos(ang), rise, Math.sin(ang)).normalize();
			SymbioteTendrilEntity.fromPoint(level, origin, origin.add(dir.scale(RESURRECT_RADIUS * 0.6)), null, 24, 5, 0.22f);
		}
		SymbioteVfx.bodyBurst(player, 2.0f); // v0.17.5 tendril-vfx (was smoke and a sonic boom)
		SymbioteVfx.groundBurst(player, groundPoint(level, player.position()), 6.0f);
		SymbioteSounds.lash(player, 1.6f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WARDEN_ROAR, net.minecraft.sounds.SoundSource.PLAYERS, 1.6f, 0.9f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.RAVAGER_ROAR, net.minecraft.sounds.SoundSource.PLAYERS, 1.4f, 0.5f);

		AABB box = player.getBoundingBox().inflate(RESURRECT_RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
				x -> com.projecthero.mod.combat.HeroTargets.canHarm(player, x) && !Squads.areAllies(player, x))) {
			Vec3 away = e.position().subtract(player.position());
			Vec3 flat = new Vec3(away.x, 0, away.z);
			if (flat.lengthSqr() < 0.01) {
				flat = new Vec3(player.getRandom().nextDouble() - 0.5, 0, player.getRandom().nextDouble() - 0.5);
			}
			if (flat.length() > RESURRECT_RADIUS) {
				continue;
			}
			Vec3 vel = flat.normalize().scale(RESURRECT_PUSH).add(0, 0.9, 0);
			e.setDeltaMovement(vel);
			e.hurtMarked = true;
			e.hasImpulse = true;
			if (e instanceof ServerPlayer sp) {
				sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
			}
			SymbioteVfx.impact(player, e, 0.8f);
		}
	}

	// ---------------- per-tick upkeep ----------------

	public static void serverTick(ServerPlayer player) {
		SymbioteWings.tick(player);
		com.projecthero.mod.symbiote.monster.SymbioteMonster.tick(player); // v0.17.5 Monster form (ends itself without the suit / kit)
		if (!Symbiote.hasSymbiote(player)) {
			if (KEY_DOWN.containsKey(player.getId()) || DEVOUR.containsKey(player.getId())) {
				stopMovesV2(player);
			}
			com.projecthero.mod.hero.power.PowerToggles.clearModifier(player,
					net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
			return;
		}
		reconcileReach(player);
		long now = player.level().getGameTime();
		if (leapFallProtected(player, now) && player.onGround()) {
			LEAP_NO_FALL_UNTIL.remove(player.getId());
		}

		if (hasContext(player)) {
			wallAssist(player);
			tickShield(player);
			tickTendrilGrab(player, now);
			com.projecthero.mod.symbiote.monster.SymbioteMonster.tickCharge(player, now); // v0.17.5 (was tickOnslaught)
			tickOnslaughtVictims(player, now);
			tickGrapplePull(player, now);
			tickBarrage(player, now);
			tickLunge(player, now);
			// v0.17.5 moveset v2
			tickHolds(player, now);
			tickSpear(player, now);
			tickPinned(player, now);
			tickRain(player, now);
			tickDive(player, now);
			tickEruption(player, now);
			tickDevour(player, now);
			// v0.17.5 weapons + wings
			SymbioteWeaponMoves.tick(player, now);
			// v0.17.5 moveset v3
			tickThornsAura(player, now);
		} else {
			stopMovesV2(player);
			SymbioteState held = Symbiote.state(player);
			if (held.tendrilGrabHeld) {
				releaseGrabQuietly(player, held, now);
			}
			if (held.onslaughtChargeStart >= 0) {
				cancelOnslaught(player, held, null);
			}
			GRAPPLE_PULL.remove(player.getId());
		}
	}

	/**
	 * Wall Assistance + Wall Climb: a Normal host against a wall while airborne falls slowly, and --
	 * while crouched against it -- climbs it like a ladder (look up to rise, level to cling, down to
	 * descend). Deliberately far lighter than Spider-Man's full {@code SpiderClimb} engine.
	 */
	private static void wallAssist(ServerPlayer player) {
		if (!player.horizontalCollision || player.onGround()) {
			return;
		}
		if (player.isShiftKeyDown()) {
			float pitch = player.getXRot();
			double vy = pitch < -25.0f ? 0.16 : (pitch > 25.0f ? -0.14 : 0.0);
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x * 0.2, vy, v.z * 0.2);
			player.resetFallDistance();
			player.hurtMarked = true;
			if (player.tickCount % 8 == 0) {
				// v0.17.5 tendril-vfx: tendrils from the hands grip the wall (was ink)
				Vec3 look = player.getLookAngle();
				Vec3 flat = new Vec3(look.x, 0, look.z);
				flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
				SymbioteVfx.wallGrip(player, player.position().add(0, player.getBbHeight() * 0.6, 0).add(flat.scale(0.55)));
			}
			return;
		}
		Vec3 v = player.getDeltaMovement();
		if (v.y >= -0.1) {
			return;
		}
		player.setDeltaMovement(v.x * 0.7, Math.max(v.y, -0.10), v.z * 0.7);
		player.fallDistance *= 0.5f;
		player.hurtMarked = true;
	}
}
