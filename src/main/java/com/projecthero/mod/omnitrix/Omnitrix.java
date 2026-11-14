package com.projecthero.mod.omnitrix;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Omnitrix, Ben 10's watch, as a Hero-Tier power ({@link #KEY} {@code "omnitrix"}).
 *
 * <h2>The lock</h2>
 * Once it is on the wrist it cannot be swapped out: while it is worn every way of gaining another power refuses with "The
 * Omnitrix rejects it." -- {@code UniquePowers.refuse} (every hero's grant routine and every acquisition item ask it first),
 * {@code PowerGrants.grantHero} / {@code grantExperimental} (the random serums, the admin grant), the admin command without
 * {@code force}, mutation exposure ({@code MutationManager}) and the power wheel's swap. It survives death (the attachment is
 * {@code copyOnDeath}). The only way off is the Omnitrix Containment Pod ({@code OmnitrixExtraction}), which pops it off as an
 * item; whoever puts that on is locked in the same way. An operator's {@code force} grant is the one override.
 *
 * <h2>Transforming (Alien Evo style: everything happens on the watch, no screen)</h2>
 * V as a human raises the wrist: the core pops up and the face shows the selected alien's silhouette as a little hologram
 * ({@link #activate}). The scroll wheel turns the dial through the aliens ({@link #cycle}); a left-click (or right-click) slaps
 * the core down ({@link #slap}) -- the green flash and Kirby dots burst (client OmnitrixTransformFx) and the wearer becomes that alien. V again lowers the
 * watch ({@link #deactivate}); V while transformed reverts early (Shift+V is an alias). A transformation lasts {@link OmnitrixConfig#TRANSFORM_TICKS} (3 min);
 * the watch light flashes red for the last 15 s, then the wearer times out with the chime and a red flash, and the watch
 * recharges for 90 s. The alien's body ({@code AlienStats}) applies while transformed and health converts by fraction.
 */
public final class Omnitrix {
	public static final String KEY = "omnitrix";

	/** Framework poses (kit poses use 1..999). */
	public static final int ANIM_TRANSFORM = 1000;
	public static final int ANIM_REVERT = 1001;
	public static final int ANIM_TIMEOUT = 1002;
	public static final int ANIM_SLAP = 1003;
	/** The dial turning one alien on (the scroll wheel). */
	public static final int ANIM_TURN = 1004;
	/** V during the recharge: the face flashes red with the time left. */
	public static final int ANIM_DENIED = 1005;
	/**
	 * v0.17.1 (user: "the player then does an animation trying to shake the Omnitrix off"): the watch has just leapt out of its
	 * pod onto the wrist ({@link #latch}) -- the wearer grabs it, yanks at it, shakes the arm, gives up. Runs
	 * {@link OmnitrixConfig#LATCH_TICKS}; meanwhile they are slowed, cannot jump and cannot raise the watch.
	 */
	public static final int ANIM_LATCH = 1006;
	/** v0.17.2 (user: "Switching alien forms without reverting to human first should drain some of the timer, for example switching alien forms should drain like 30 seconds from the timer, also the alien switcher for recalibrated should only work while in alien form unless the user has master control, if the player chooses another alien via the alien switcher with recalibrated they need to hit their omnitrix badge first and then transform"): the right hand slaps the chest badge to take the alien picked on the wheel. */
	public static final int ANIM_BADGE = 1007;
	/** How long after the slap starts the switch lands. */
	public static final int BADGE_HIT_TICKS = 5;
	/** A wheel pick waiting for the badge (1 = waiting; the alien is {@code selected}), and the slap's start tick. */
	public static final String PENDING_SWITCH = "omnitrix.pending_switch", BADGE_HIT = "omnitrix.badge_hit";
	/** v0.17.1: the shake-off's slow (transient modifiers, so a save or relog mid-way never keeps them). */
	private static final net.minecraft.resources.ResourceLocation LATCH_SLOW_ID = com.projecthero.mod.ProjectHeroMod.id("omnitrix_latch_slow");
	private static final net.minecraft.resources.ResourceLocation LATCH_JUMP_ID = com.projecthero.mod.ProjectHeroMod.id("omnitrix_latch_jump");
	/** Ticks between the slap and the transformation (the core going down). */
	public static final int SLAP_TICKS = 5;
	private static final String SLAP_TIMER = "omnitrix.slap";
	/** A click before the core is up: the slap waits for it ({@link #CORE_UP_TICKS}). */
	private static final String SLAP_QUEUE = "omnitrix.slap_queued";
	/** v0.17.2: the queued click was the right-click (the long transformation). */
	private static final String SLAP_QUEUE_LONG = "omnitrix.slap_queued_long";
	/** The activation's button press is due (its click sound plays then). */
	private static final String PRESS_TIMER = "omnitrix.press";

	/**
	 * v0.17.1 (user: "make the player bring their arm up and then use their right hand to press the button on the omnitrix to
	 * pop up the dial"): the activation's beats, ticks after R (shared by the client's animation): the wrist comes up and the
	 * right hand reaches the side button, which it presses at {@link #PRESS_TICKS} -- the core pops then -- and the core is up
	 * (the dial turns, the slap lands) from {@link #CORE_UP_TICKS}. A scroll before that is ignored; a click is held until then.
	 */
	public static final int PRESS_TICKS = 7, CORE_UP_TICKS = 11;

	private static void clearSlap(OmnitrixState n) {
		n.timers.remove(SLAP_TIMER);
		n.timers.remove(SLAP_QUEUE);
		n.timers.remove(SLAP_QUEUE_LONG);
		n.timers.remove(PRESS_TIMER);
		n.timers.remove(FORCE_GLITCH); // v0.17.4
	}

	/**
	 * v0.17.1 (user: "when the omnitrix is primed but timed out and then times in while the player is still holding it make the
	 * red regular badge transition to the regular green badge and then have that badge transition to the aliens. Also dont have
	 * the Dial pop up unless the omnitrix is timed in"): raised during the recharge, the core stays down on the red face; when it
	 * times in the face turns green over {@link #TIME_IN_TICKS}, and only then does the core pop (the press-to-core-up beat).
	 */
	public static final int TIME_IN_TICKS = 12;
	/** The recharge has been announced as over (kept while a raised watch finishes timing in). */
	private static final String TIMED_IN = "omnitrix.timed_in";

	/** The tick the core pops (the press beat of the raise, held back until the recharge is over and the face has gone green). */
	public static long popAt(OmnitrixState s) {
		if (OmnitrixRecal.busy(s)) {
			return Long.MAX_VALUE / 4; // v0.17.2: recalibrating -- raised to look at, the dial never pops
		}
		long pop = s.selectStart + PRESS_TICKS;
		if (s.rechargeUntil > s.selectStart) {
			pop = Math.max(pop, s.rechargeUntil + TIME_IN_TICKS);
		}
		return pop;
	}

	/** The tick the raised watch's core is up (the dial turns and the slap lands from here). */
	public static long coreUpAt(OmnitrixState s) {
		return popAt(s) + (CORE_UP_TICKS - PRESS_TICKS);
	}

	/** Whether the raised watch's core is up (the dial can be turned and slapped). */
	public static boolean coreUp(OmnitrixState s, long now) {
		return s.selecting && now >= coreUpAt(s);
	}

	/** Gametests: the watch's raise / button press over at once (game time does not advance in a test's own tick). */
	public static void finishRaise(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (s.selecting) {
			OmnitrixState n = s.copy();
			n.selectStart -= CORE_UP_TICKS;
			save(p, n);
		}
	}

	/**
	 * v0.17.1 (user: "when detransforming the watch should first beep 5 times like how it does in the mp3 file and then
	 * detransform"): turning back (V while transformed, and the 3-minute timeout) is a short timed sequence synced to the user's
	 * {@code omnitrix_power_down_user} sound. The ticks are after the sound starts, measured from the shipped (trimmed) OGG by
	 * {@code scratchpad/detect_omnitrix_beeps.js} and rounded to the nearest tick (docs/OMNITRIX_SOUNDS_0171.md): the beeps at
	 * 0.050 / 0.469 / 0.958 / 1.487 / 2.015 s, the power-down's low clunk starting at 2.724 s and loudest at 2.83-2.88 s. On each
	 * beep tick the watch face and the HUD's Time bar blink red; the body changes back (with the red Kirby-dot flash) on
	 * {@link #POWER_DOWN_REVERT_TICK}, so the flash's hot opening (its first 2.5 ticks) covers the loudest part.
	 */
	public static final int[] POWER_DOWN_BEEPS = { 1, 9, 19, 30, 40 };
	/** Ticks from the start of the power-down sound to the actual revert (2.80 s). */
	public static final int POWER_DOWN_REVERT_TICK = 56;
	/** How long a beep's red blink lasts (ticks). */
	public static final float BEEP_BLINK_TICKS = 4f;
	/** Timer keys: the power-down's start (game time) and whether the timeout began it (1) or V (0). */
	public static final String POWER_DOWN_TIMER = "omnitrix.power_down";
	public static final String POWER_DOWN_TIMEOUT = "omnitrix.power_down_timeout";

	/** Why the wearer turned back. */
	public enum Reason {
		SHIFT_V, TIMEOUT, DEATH, LOGOUT, EXTRACTION, REVOKE, SUPPRESSED,
		/** v0.17.1: the failsafe threw him back to human instead of letting a hit kill the alien. */
		FAILSAFE,
		/** v0.17.2: alien straight to alien on the wheel -- a silent turn-back, no recharge. */
		SWITCH
	}

	// ---------------------------------------------------------------- v0.17.1: Master Control, the failsafe, the glitch

	/** Master Control is on (no time limit, no recharge, no glitches). Persisted in the timers (1 = on). */
	private static final String MASTER = "omnitrix.master";
	/** The Master Control sequence: its start tick and how many dial steps it has turned. */
	private static final String MC_SEQ = "omnitrix.mc_seq", MC_STEP = "omnitrix.mc_step";
	/** The game time the failsafe can save him again. */
	private static final String FAILSAFE_READY = "omnitrix.failsafe";
	/** Gametests: this wearer's slaps never glitch. */
	private static final String NO_GLITCH = "omnitrix.no_glitch";
	/** v0.17.4: the next slap lands on a wrong alien whatever the watch (the Master Control ritual's slap off the beat). */
	public static final String FORCE_GLITCH = "omnitrix.force_glitch";
	/**
	 * v0.17.4: he has woken Master Control at least once (1) -- the Guidebook's hidden Master Control page shows from then on.
	 * Belongs to the player, not the watch ({@link #keepPlayerData}).
	 */
	public static final String MASTER_KNOWN = "omnitrix.master_known";

	/** v0.17.4: whether {@code p} has ever woken Master Control (client-safe: the synced state). */
	public static boolean masterKnown(Player p) {
		return p != null && p.getAttached(OmnitrixAttachments.STATE) != null && state(p).readyAt(MASTER_KNOWN) == 1L;
	}

	/**
	 * v0.17.2: the Recalibrated Omnitrix (Ben 10: Alien Force; user: "come up with a way for the player to recalibrate the omnitrix").
	 * A flag in the persisted timers (1 = recalibrated), so it needs no codec change, survives death and syncs like the rest. Its
	 * dial is {@code Aliens.recalibrated()}; the watch, the sounds and the face change with it. Made by using a Recalibration Chip
	 * ({@link OmnitrixRecal}: the insert, 3 minutes of recalibration mode, the reform).
	 */
	public static final String RECAL = "omnitrix.recal";
	/** v0.17.2: transformations made with the prototype (the Recalibration Chip asks for {@code OmnitrixConfig.RECAL_MIN_TRANSFORMS}). */
	public static final String TRANSFORMS = "omnitrix.transforms";
	/** v0.17.2: mistransforms the prototype has made (every {@code OmnitrixConfig.UNLOCK_EVERY_MISTRANSFORMS}th finds a hidden alien). */
	public static final String MISTRANSFORMS = "omnitrix.mistransforms";

	public static int mistransforms(Player p) {
		return hasPower(p) ? (int) state(p).readyAt(MISTRANSFORMS) : 0;
	}

	public static void setMistransforms(ServerPlayer p, int count) {
		OmnitrixState n = state(p).copy();
		n.timers.put(MISTRANSFORMS, (long) Math.max(0, count));
		save(p, n);
	}

	/** v0.17.2: {@code p} wears the Recalibrated Omnitrix (client-safe: the synced state). */
	public static boolean recalibrated(Player p) {
		return p != null && hasPower(p) && state(p).readyAt(RECAL) == 1L;
	}

	/** v0.17.2 (user: "The recalibrated omnitrix should have a battery system instead of timer like the prototype, player should be able to stay an alien for about 4 minutes in total this time and detransform at will without the omnitrix going into recharge mode, full recharge of the battery should be 90 seconds"): the battery's charge in milli-ticks, and the tick it was stored at (no entry = full). */
	public static final String BATTERY = "omnitrix.battery", BATTERY_AT = "omnitrix.battery_at";
	/**
	 * v0.17.2 (user: "if you transform into alien X you watch battery stops depleting and you can basically stay as him for as long
	 * as you like until you detransform"): while this timer is set (by AlienXKit on transforming) the battery reads this many ticks,
	 * the time limit is held off like Master Control's, and turning back stores exactly this. Cleared on revert.
	 */
	public static final String HOLD_BATTERY = "omnitrix.hold_battery";

	/** v0.17.2: the Recalibrated Omnitrix's battery, in ticks of alien time left (client-safe: the synced state). */
	public static int batteryTicks(Player p) {
		return hasPower(p) ? batteryTicks(state(p), p.level().getGameTime()) : 0;
	}

	/** Transformed it drains with the time limit (endsAt); human it refills from what was stored; Master Control keeps it full. */
	public static int batteryTicks(OmnitrixState s, long now) {
		long max = OmnitrixConfig.BATTERY_TICKS;
		if (s.readyAt(MASTER) == 1L) {
			return (int) max;
		}
		if (s.transformed() && s.timers.containsKey(HOLD_BATTERY)) {
			return (int) net.minecraft.util.Mth.clamp(s.readyAt(HOLD_BATTERY), 0L, max); // v0.17.2: Alien X -- held where it was
		}
		if (s.transformed()) {
			long until = poweringDown(s) ? Math.min(now, s.readyAt(POWER_DOWN_TIMER)) : now;
			return (int) net.minecraft.util.Mth.clamp(s.endsAt - until, 0L, max);
		}
		if (!s.timers.containsKey(BATTERY)) {
			return (int) max;
		}
		long milli = s.readyAt(BATTERY) + Math.max(0L, now - s.readyAt(BATTERY_AT)) * max * 1000L / OmnitrixConfig.BATTERY_RECHARGE_TICKS;
		return (int) Math.min(max, milli / 1000L);
	}

	/** v0.17.2: how many times {@code p} has transformed with the prototype. */
	public static int prototypeTransforms(Player p) {
		return hasPower(p) ? (int) state(p).readyAt(TRANSFORMS) : 0;
	}

	/**
	 * v0.17.2: the recalibration lands -- the watch becomes the Recalibrated Omnitrix, its dial the Alien Force ten (the first
	 * one selected), with the user's transform sound. False if not worn, already recalibrated or transformed.
	 */
	public static boolean recalibrate(ServerPlayer p) {
		return recalibrate(p, false);
	}

	/**
	 * v0.17.2: {@code reformed} -- the Recalibration Chip's finish ({@link OmnitrixRecal}) already played the reform on the wrist and its
	 * sound: no slap pose, no transform sound on top.
	 */
	public static boolean recalibrate(ServerPlayer p, boolean reformed) {
		OmnitrixState s = state(p);
		if (!s.hasPower || s.transformed() || s.readyAt(RECAL) == 1L) {
			return false;
		}
		OmnitrixState n = s.copy();
		n.timers.put(RECAL, 1L);
		// v0.17.2 (user: "if player had master control with prototype and then recalibrated then master control shouldnt be activated
		// automatically for the recalibrated, it should have to be reactivated manually"); the battery starts full
		n.timers.remove(MASTER);
		n.timers.remove(MC_SEQ);
		n.timers.remove(MC_STEP);
		n.timers.remove(BATTERY);
		n.timers.remove(BATTERY_AT);
		n.selecting = false;
		n.rechargeUntil = 0L;
		clearSlap(n);
		if (!reformed) {
			n.animId = ANIM_SLAP;
			n.animStart = p.level().getGameTime();
		}
		save(p, n);
		java.util.List<Aliens.Alien> pool = Aliens.playable(p);
		OmnitrixState m = state(p).copy();
		m.selected = pool.isEmpty() ? Aliens.SWAMPFIRE.id() : pool.get(0).id();
		save(p, m);
		if (!reformed) {
			OmnitrixSounds.playOn(p, OmnitrixSounds.RECAL_TRANSFORM);
		}
		return true;
	}

	/** v0.17.2: the prototype's cue, or the Recalibrated Omnitrix's own when {@code p} wears it. */
	public static OmnitrixSfx.Cue cue(Player p, OmnitrixSfx.Cue prototype, OmnitrixSfx.Cue recal) {
		return recalibrated(p) ? recal : prototype;
	}

	public static boolean masterControl(Player p) {
		return hasPower(p) && state(p).readyAt(MASTER) == 1L;
	}

	/**
	 * v0.17.2: Master Control's instant switch (the alien wheel on H, or the alien's name said in chat) -- straight into
	 * {@code alienId}, from human or from another alien (no beeps, no recharge under Master Control). False without Master
	 * Control, for an alien he cannot be, or when the transform is refused (the pod, suppression).
	 */
	public static boolean masterSwitch(ServerPlayer p, String alienId) {
		if (!canSwitch(p) || alienId == null || !Aliens.available(p, alienId)) {
			return false;
		}
		OmnitrixState s = state(p);
		if (alienId.equals(s.active)) {
			return true;
		}
		if (!masterControl(p)) {
			// v0.17.2 (user: "Switching alien forms without reverting to human first should drain some of the timer, for example switching alien forms should drain like 30 seconds from the timer, also the alien switcher for recalibrated should only work while in alien form unless the user has master control, if the player chooses another alien via the alien switcher with recalibrated they need to hit their omnitrix badge first and then transform"): the pick waits for a hit on the badge (N)
			OmnitrixState n = s.copy();
			n.selected = alienId;
			n.timers.put(PENDING_SWITCH, 1L);
			save(p, n);
			Aliens.Alien a = Aliens.byId(alienId);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.hit_badge",
					Component.translatable(a == null ? "projecthero.omnitrix.alien.unknown" : a.nameKey())).withStyle(ChatFormatting.GREEN), true);
			OmnitrixSounds.notify(p, cue(p, OmnitrixSounds.DIAL, OmnitrixSounds.RECAL_DIAL), 1.0f);
			return true;
		}
		return switchNow(p, alienId);
	}

	/** N with a wheel pick waiting: the slap on the badge starts; the switch lands {@link #BADGE_HIT_TICKS} later ({@link #tick}). */
	public static boolean hitBadge(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (!s.transformed() || s.readyAt(PENDING_SWITCH) != 1L || s.timers.containsKey(BADGE_HIT)) {
			return false;
		}
		long now = p.level().getGameTime();
		OmnitrixState n = s.copy();
		n.timers.put(BADGE_HIT, now);
		n.animId = ANIM_BADGE;
		n.animStart = now;
		save(p, n);
		return true;
	}

	/** Alien straight to alien. The recalibrated watch without Master Control pays {@code OmnitrixConfig.SWITCH_COST_TICKS} for it. */
	private static boolean switchNow(ServerPlayer p, String alienId) {
		OmnitrixState s = state(p);
		long now = p.level().getGameTime();
		boolean pay = s.transformed() && s.readyAt(RECAL) == 1L && s.readyAt(MASTER) != 1L;
		int battery = batteryTicks(s, now);
		if (pay && battery < OmnitrixConfig.SWITCH_COST_TICKS + OmnitrixConfig.BATTERY_MIN_TICKS) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.switch_battery_low").withStyle(ChatFormatting.RED), true);
			OmnitrixSounds.notify(p, OmnitrixSounds.DENIED);
			return false;
		}
		if (s.transformed() && heldBack(p)) {
			return false; // v0.17.2: Alien X's council must agree first
		}
		if (s.transformed()) {
			// v0.17.2 (user: "player should be able to switch to alien form without reverting to human"): straight across -- no
			// turn-back effects, no recharge
			revert(p, Reason.SWITCH);
		}
		if (pay) {
			// v0.17.2 (user: "Switching alien forms without reverting to human first should drain some of the timer, for example switching alien forms should drain like 30 seconds from the timer, also the alien switcher for recalibrated should only work while in alien form unless the user has master control, if the player chooses another alien via the alien switcher with recalibrated they need to hit their omnitrix badge first and then transform"): the switch costs 30 s of battery
			OmnitrixState n = state(p).copy();
			n.timers.put(BATTERY, (battery - OmnitrixConfig.SWITCH_COST_TICKS) * 1000L);
			n.timers.put(BATTERY_AT, now);
			save(p, n);
		}
		return select(p, alienId) && transform(p);
	}

	/**
	 * v0.17.2 (user: "the H alien selector wheel should be default for the recalibrated"): the alien wheel and its alien-to-alien
	 * switch -- Master Control, or the Recalibrated Omnitrix with or without it.
	 */
	public static boolean canSwitch(Player p) {
		// v0.17.2 (user: "Switching alien forms without reverting to human first should drain some of the timer, for example switching alien forms should drain like 30 seconds from the timer, also the alien switcher for recalibrated should only work while in alien form unless the user has master control, if the player chooses another alien via the alien switcher with recalibrated they need to hit their omnitrix badge first and then transform"): without Master Control, only as an alien
		return masterControl(p) || recalibrated(p) && state(p).transformed();
	}

	/**
	 * v0.17.2: the alien a chat message names, or null -- the whole message is the name, any case, spaces / dashes /
	 * punctuation ignored ("Four Arms", "fourarms!", "XLR8", "Gray Matter").
	 */
	public static String alienNamed(String message) {
		return alienNamed(null, message);
	}

	/** v0.17.2: the alien a chat message names on the dial of the watch {@code p} wears ({@code null} = the prototype's). */
	public static String alienNamed(Player p, String message) {
		String said = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
		if (said.isEmpty()) {
			return null;
		}
		if (said.equals("graymatter")) {
			said = "greymatter";
		}
		for (Aliens.Alien a : p == null ? Aliens.playable() : Aliens.playable(p)) {
			if (a.id().replace("_", "").equals(said)) {
				return a.id();
			}
		}
		return null;
	}

	/** The Master Control sequence is running (the dial turning on its own). */
	public static boolean masterSequence(Player p) {
		return hasPower(p) && state(p).timers.containsKey(MC_SEQ);
	}

	/**
	 * v0.17.1 (user: "make master control able to be activated by shifting and holding N while the omnitrix is activated for 10
	 * seconds ... the dial must turn on its own flipping through all the aliens the player has in the omnitrix and then finally
	 * deactivating with master control activated, master control can be deactivated the same way, when you activate master
	 * control tell the player on the hud"): the core is forced up, the face lights, and {@link #tick} turns the dial through every
	 * alien, lowers the watch and flips Master Control. v0.17.4: turned ON only by the ritual's slap on the third beep
	 * ({@link OmnitrixRitual}); turned OFF by Shift+N held 3 s with the watch up (OmnitrixAbilityManager).
	 */
	public static boolean startMasterSequence(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (!s.selecting || s.transformed() || s.timers.containsKey(MC_SEQ)) {
			return false;
		}
		long now = p.level().getGameTime();
		OmnitrixState n = s.copy();
		clearSlap(n);
		n.rechargeUntil = 0L; // the watch comes alive even mid-recharge
		n.timers.remove(TIMED_IN);
		if (!coreUp(n, now)) {
			n.selectStart = now - CORE_UP_TICKS;
		}
		n.timers.put(MC_SEQ, now);
		n.timers.put(MC_STEP, 0L);
		save(p, n);
		OmnitrixSounds.play(p.serverLevel(), p.position(), cue(p, OmnitrixSounds.CORE_UP, OmnitrixSounds.RECAL_CORE_UP), 1.0f, 1.2f);
		return true;
	}

	/** One tick of the Master Control sequence: a dial step every MASTER_STEP_TICKS, then the watch down and the flip. */
	private static void tickMasterSequence(ServerPlayer p, OmnitrixState s, long now) {
		long start = s.readyAt(MC_SEQ);
		long steps = s.readyAt(MC_STEP);
		java.util.List<Aliens.Alien> pool = Aliens.playable(p);
		if (!s.selecting) {
			OmnitrixState n = s.copy();
			n.timers.remove(MC_SEQ);
			n.timers.remove(MC_STEP);
			save(p, n);
			return;
		}
		if ((now - start) % OmnitrixConfig.MASTER_STEP_TICKS != 0 || now == start) {
			return;
		}
		if (steps < pool.size()) {
			int i = 0;
			for (int k = 0; k < pool.size(); k++) {
				if (pool.get(k).id().equals(s.selected)) {
					i = k;
				}
			}
			OmnitrixState n = s.copy();
			n.selected = pool.get(Math.floorMod(i + 1, pool.size())).id();
			n.animId = ANIM_TURN;
			n.animStart = now;
			n.timers.put(MC_STEP, steps + 1);
			save(p, n);
			OmnitrixSounds.notify(p, cue(p, OmnitrixSounds.DIAL, OmnitrixSounds.RECAL_DIAL), 1.0f);
			return;
		}
		// every alien shown: the flip, the watch down, the HUD told
		boolean on = s.readyAt(MASTER) != 1L;
		OmnitrixState n = s.copy();
		n.timers.remove(MC_SEQ);
		n.timers.remove(MC_STEP);
		if (on) {
			n.timers.put(MASTER, 1L);
			n.timers.put(MASTER_KNOWN, 1L); // v0.17.4: the Guidebook's hidden page
		} else {
			n.timers.remove(MASTER);
		}
		save(p, n);
		deactivate(p);
		if (on && recalibrated(p)) {
			// v0.17.2 (user): the recalibrated watch announces it -- "Master Control unlocked"
			OmnitrixSounds.playOn(p, OmnitrixSounds.RECAL_MASTER);
		} else {
			OmnitrixSounds.notify(p, OmnitrixSounds.RECHARGED);
		}
		p.displayClientMessage(Component.translatable(on ? "message.projecthero.omnitrix.master_on" : "message.projecthero.omnitrix.master_off")
				.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY, ChatFormatting.BOLD), true);
	}

	// ---------------------------------------------------------------- v0.17.2: /projecthero power tune omnitrix (OmnitrixCommand)

	/** Master Control on or off at once, without the ritual (or the off-hold) and the dial sequence. */
	public static void setMasterControl(ServerPlayer p, boolean on) {
		OmnitrixState n = state(p).copy();
		n.timers.remove(MC_SEQ);
		n.timers.remove(MC_STEP);
		if (on) {
			n.timers.put(MASTER, 1L);
			n.timers.put(MASTER_KNOWN, 1L); // v0.17.4: the Guidebook's hidden page
			n.rechargeUntil = 0L;
		} else {
			n.timers.remove(MASTER);
		}
		save(p, n);
	}

	/** Back to the prototype: the original dial, no Master Control, the battery gone. False while transformed or not recalibrated. */
	public static boolean unrecalibrate(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (!s.hasPower || s.transformed() || s.readyAt(RECAL) != 1L) {
			return false;
		}
		OmnitrixState n = s.copy();
		for (String k : new String[] { RECAL, MASTER, MC_SEQ, MC_STEP, BATTERY, BATTERY_AT, HOLD_BATTERY }) {
			n.timers.remove(k);
		}
		n.selecting = false;
		n.rechargeUntil = 0L;
		save(p, n);
		java.util.List<Aliens.Alien> pool = Aliens.playable(p);
		OmnitrixState m = state(p).copy();
		m.selected = pool.isEmpty() ? "heatblast" : pool.get(0).id();
		save(p, m);
		return true;
	}

	/** The failsafe armed again in {@code ticks} (0 = now). */
	public static void setFailsafeIn(ServerPlayer p, int ticks) {
		OmnitrixState n = state(p).copy();
		n.timers.put(FAILSAFE_READY, p.level().getGameTime() + Math.max(0, ticks));
		save(p, n);
	}

	/** The prototype's mistransform chance on (the normal watch) or off. */
	public static void setGlitches(ServerPlayer p, boolean on) {
		OmnitrixState n = state(p).copy();
		if (on) {
			n.timers.remove(NO_GLITCH);
		} else {
			n.timers.put(NO_GLITCH, 1L);
		}
		save(p, n);
	}

	public static boolean glitches(Player p) {
		return state(p).readyAt(NO_GLITCH) != 1L;
	}

	/** Ticks until the failsafe is armed (0 = armed). */
	public static int failsafeLeft(Player p) {
		return (int) Math.max(0L, state(p).readyAt(FAILSAFE_READY) - p.level().getGameTime());
	}

	/**
	 * Transformed: the alien time (the battery, on the Recalibrated Omnitrix) set to {@code ticks}, any power-down beeps stopped.
	 * Human on the Recalibrated Omnitrix: the stored battery charge. False otherwise (a human prototype has no time to set).
	 */
	public static boolean setTimeLeft(ServerPlayer p, int ticks) {
		OmnitrixState s = state(p);
		long now = p.level().getGameTime();
		OmnitrixState n = s.copy();
		if (s.transformed()) {
			n.endsAt = now + Math.max(1, ticks);
			n.timers.remove(POWER_DOWN_TIMER);
			n.timers.remove(POWER_DOWN_TIMEOUT);
			if (n.timers.containsKey(HOLD_BATTERY)) {
				n.timers.put(HOLD_BATTERY, (long) ticks);
			}
		} else if (s.readyAt(RECAL) == 1L) {
			n.timers.put(BATTERY, (long) net.minecraft.util.Mth.clamp(ticks, 0, OmnitrixConfig.BATTERY_TICKS) * 1000L);
			n.timers.put(BATTERY_AT, now);
		} else {
			return false;
		}
		save(p, n);
		return true;
	}

	/** The recharge set to {@code ticks} from now (0 = ready). */
	public static void setRecharge(ServerPlayer p, int ticks) {
		OmnitrixState n = state(p).copy();
		n.rechargeUntil = ticks <= 0 ? 0L : p.level().getGameTime() + ticks;
		save(p, n);
	}

	/** How many prototype transformations he has made (the Recalibration Chip asks for {@link OmnitrixConfig#RECAL_MIN_TRANSFORMS}). */
	public static void setPrototypeTransforms(ServerPlayer p, int count) {
		OmnitrixState n = state(p).copy();
		n.timers.put(TRANSFORMS, (long) Math.max(0, count));
		save(p, n);
	}

	/** Gametests: keep this wearer's slaps on the selected alien. */
	public static void noGlitch(ServerPlayer p) {
		OmnitrixState n = state(p).copy();
		n.timers.put(NO_GLITCH, 1L);
		save(p, n);
	}

	/**
	 * v0.17.2 (user: "the omnitrix failsafe doesnt seem the save the player from death, lets try and build a good system"): the death
	 * save, run from {@code ALLOW_DEATH} (after armour, so every death counts: hits, fire, lava, poison, wither, falls, explosions,
	 * drowning, the void) -- {@code /kill} still kills. The loop:
	 * <ul>
	 *   <li>human, failsafe armed: the death does not happen -- the watch transforms him at once into the alien that fits the
	 *       situation ({@link OmnitrixAuto#pick}), a normal 3-minute transformation, the alien on half health, any recharge ended;</li>
	 *   <li>an alien: back to human instead of dying, on {@link OmnitrixConfig#FAILSAFE_HEALTH}, and the normal 90 s recharge;</li>
	 *   <li>for {@link OmnitrixConfig#FAILSAFE_OFFLINE_TICKS} after that the failsafe is offline (he can die); then it is armed again,
	 *       even while still recharging.</li>
	 * </ul>
	 * Both saves wrap him in {@link OmnitrixConfig#FAILSAFE_SHIELD_TICKS} of Resistance V and put out any fire. True when it saved him.
	 */
	public static boolean failsafe(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
		OmnitrixState s = state(p);
		if (!s.hasPower || source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) {
			return false;
		}
		long now = p.level().getGameTime();
		if (s.transformed()) {
			// v0.17.2 (user: "when player is in alien form and dies they revert back to base form, dont make that the omnitrix failsafe
			// activating just make that a normal feature, have the player detransform back to a player at full hp"): an alien that
			// would die always turns back into a human on full health, with the normal recharge -- not the failsafe, which stays armed
			revert(p, Reason.FAILSAFE);
			p.setHealth(p.getMaxHealth());
			shield(p);
			return true;
		}
		// v0.17.2 (user: "Make the omnitrix failsafe activate when the player isnt transformed and give it a 20 seconds cooldown"):
		// the failsafe saves a human; a recalibrating watch has none (user: "if the player dies it wont halt anything")
		if (now < s.readyAt(FAILSAFE_READY) || extracting(p) || com.projecthero.mod.hero.PowerSuppression.isSuppressed(p) || OmnitrixRecal.busy(s)) {
			return false;
		}
		// human: straight into the alien that fits, no hold, no slap
		OmnitrixAuto.Pick pick = OmnitrixAuto.pick(p);
		if (Aliens.kit(pick.alien()) == null) {
			return false;
		}
		p.setHealth(1f); // the body change keeps the health fraction -- never from zero
		OmnitrixState n = state(p).copy();
		n.selected = pick.alien();
		n.selecting = false;
		n.rechargeUntil = 0L;
		n.timers.remove(OmnitrixAuto.HOLD_TIMER);
		n.timers.put(FAILSAFE_READY, now + OmnitrixConfig.FAILSAFE_OFFLINE_TICKS); // 20 s before it can save him again
		clearSlap(n);
		save(p, n);
		if (!transform(p)) {
			return false;
		}
		p.setHealth(p.getMaxHealth() * OmnitrixConfig.FAILSAFE_ALIEN_HEALTH);
		p.setAirSupply(p.getMaxAirSupply());
		shield(p);
		Aliens.Alien a = Aliens.byId(pick.alien());
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.failsafe_transform",
				Component.translatable("message.projecthero.omnitrix.auto." + pick.reason(),
						Component.translatable(a == null ? "projecthero.omnitrix.alien.unknown" : a.nameKey())))
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
		return true;
	}

	/** Gametests / the loop: whether the failsafe would save {@code p} right now. */
	public static boolean failsafeArmed(Player p) {
		OmnitrixState s = state(p);
		return s.hasPower && p.level().getGameTime() >= s.readyAt(FAILSAFE_READY);
	}

	private static void shield(ServerPlayer p) {
		p.invulnerableTime = OmnitrixConfig.FAILSAFE_SHIELD_TICKS;
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,
				OmnitrixConfig.FAILSAFE_SHIELD_TICKS, 4, false, false, true));
		p.clearFire();
		p.resetFallDistance();
		OmnitrixSounds.notify(p, OmnitrixSounds.DENIED);
	}

	private static final OmnitrixState DEFAULT = new OmnitrixState();
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.25f), 0.9f);
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.12f), 0.9f);
	/** player -> last game time the "rejects it" line was shown (a Mjolnir on the ground is touched every tick). */
	private static final Map<UUID, Long> LAST_REJECT = new ConcurrentHashMap<>();

	private Omnitrix() {
	}

	// ---------------------------------------------------------------- state

	/** Client-safe: the synced state (a shared empty default when there is none -- never mutate it, always {@code copy()}). */
	public static OmnitrixState state(Player p) {
		return p.getAttachedOrElse(OmnitrixAttachments.STATE, DEFAULT);
	}

	public static void save(ServerPlayer p, OmnitrixState s) {
		p.setAttached(OmnitrixAttachments.STATE, s);
	}

	public static boolean hasPower(Player p) {
		return p != null && state(p).hasPower;
	}

	/** Client-safe: the alien the wearer is right now, or "" (human / no watch). */
	public static String active(Player p) {
		return hasPower(p) ? state(p).active : "";
	}

	public static boolean transformed(Player p) {
		return !active(p).isEmpty();
	}

	/** Ticks left in the current transformation (0 when human). */
	public static int timeLeft(Player p) {
		return displayLeft(state(p), p.level().getGameTime());
	}

	/** Ticks of recharge left (0 = ready). */
	public static int rechargeLeft(Player p) {
		return (int) Math.max(0L, state(p).rechargeUntil - p.level().getGameTime());
	}

	/** Client-safe: whether the power-down sequence (the five beeps before turning back) is running. */
	public static boolean poweringDown(OmnitrixState s) {
		return s.transformed() && s.timers.containsKey(POWER_DOWN_TIMER);
	}

	public static boolean poweringDown(Player p) {
		return hasPower(p) && poweringDown(state(p));
	}

	/** Ticks since the power-down began (only meaningful while {@link #poweringDown}). */
	public static float powerDownAge(OmnitrixState s, long now, float partial) {
		return now + partial - s.readyAt(POWER_DOWN_TIMER);
	}

	/** Client-safe: how many of the five beeps have sounded so far (0 when not powering down). */
	public static int beepsDone(OmnitrixState s, long now) {
		if (!poweringDown(s)) {
			return 0;
		}
		long t = now - s.readyAt(POWER_DOWN_TIMER);
		int n = 0;
		for (int b : POWER_DOWN_BEEPS) {
			if (t >= b) {
				n++;
			}
		}
		return n;
	}

	/** Client-safe: the red blink on the watch / HUD, 1 on a beep's tick fading to 0 over {@link #BEEP_BLINK_TICKS}. */
	public static float beepBlink(OmnitrixState s, long now, float partial) {
		if (!poweringDown(s)) {
			return 0f;
		}
		float t = powerDownAge(s, now, partial);
		float best = 0f;
		for (int b : POWER_DOWN_BEEPS) {
			float d = t - b;
			if (d >= 0f && d < BEEP_BLINK_TICKS) {
				best = Math.max(best, 1f - d / BEEP_BLINK_TICKS);
			}
		}
		return best;
	}

	/** Client-safe: the Time bar's ticks left -- frozen while powering down (0 for the timeout). */
	public static int displayLeft(OmnitrixState s, long now) {
		if (!s.transformed()) {
			return 0;
		}
		long until = poweringDown(s) ? Math.min(now, s.readyAt(POWER_DOWN_TIMER)) : now;
		return (int) Math.max(0L, s.endsAt - until);
	}

	/** Client-safe: whether the pod is pulling the watch off this player right now. */
	public static boolean extracting(Player p) {
		return p.getAttached(OmnitrixAttachments.EXTRACTION) != null;
	}

	// ---------------------------------------------------------------- the lock

	/**
	 * The lock: true (and "The Omnitrix rejects it." shown, throttled) if {@code p} wears the Omnitrix and {@code key} is any
	 * other power. An operator's {@code force} ({@code UniquePowers.forced}) lets it through.
	 */
	public static boolean rejects(ServerPlayer p, String key) {
		if (p == null || !hasPower(p) || KEY.equals(key) || com.projecthero.mod.hero.UniquePowers.isForced()) {
			return false;
		}
		tellRejected(p);
		return true;
	}

	/** {@link #rejects} for anything that is not a named power (a mutation, a power-wheel swap). */
	public static boolean rejectsAny(ServerPlayer p) {
		return rejects(p, "");
	}

	public static net.minecraft.network.chat.MutableComponent rejectMessage() {
		return Component.translatable("message.projecthero.omnitrix.rejects").withStyle(ChatFormatting.GREEN);
	}

	private static void tellRejected(ServerPlayer p) {
		long now = p.server == null ? 0L : p.server.getTickCount();
		Long last = LAST_REJECT.get(p.getUUID());
		if (last == null || now - last >= 20 || now < last) {
			LAST_REJECT.put(p.getUUID(), now);
			p.displayClientMessage(rejectMessage(), false);
			OmnitrixSounds.notify(p, OmnitrixSounds.DENIED);
		}
	}

	// ---------------------------------------------------------------- grant / revoke

	/**
	 * The watch jumps onto the wrist (the pod's touch, the popped item, the command). Claims the one Primary slot (whatever was
	 * held goes). False if already worn or the power is taken (unique powers).
	 */
	public static boolean grant(ServerPlayer p) {
		return grant(p, false);
	}

	/** v0.17.2: {@code recal} -- the watch put on is the Recalibrated Omnitrix (its popped-off item). */
	public static boolean grant(ServerPlayer p, boolean recal) {
		if (hasPower(p)) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(p, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(p, KEY);
		OmnitrixState s = new OmnitrixState();
		keepPlayerData(state(p), s); // v0.17.2: the aliens he unlocked are his, not the watch's
		s.hasPower = true;
		if (recal) {
			s.timers.put(RECAL, 1L);
		}
		java.util.List<Aliens.Alien> first = new java.util.ArrayList<>();
		for (Aliens.Alien a : recal ? Aliens.recalibrated() : Aliens.all()) {
			if (Aliens.playable(a.id())) {
				first.add(a);
			}
		}
		s.selected = first.isEmpty() ? (recal ? Aliens.SWAMPFIRE.id() : Aliens.HEATBLAST.id()) : first.get(0).id();
		s.animId = ANIM_SLAP;
		s.animStart = p.level().getGameTime();
		save(p, s);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(GREEN, c.x, c.y, c.z, 30, 0.6, 0.9, 0.6, 0.0);
		OmnitrixSounds.play(level, c, OmnitrixSounds.GRANT, 1.0f, 1.0f);
		p.connection.send(new ClientboundSetTitlesAnimationPacket(6, 60, 20));
		p.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.omnitrix.subtitle").withStyle(ChatFormatting.GRAY)));
		p.connection.send(new ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.omnitrix.title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.acquired").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	/**
	 * v0.17.1 (user: "the Omnitrix jumps out in a smooth arc onto the player's wrist ... play the Omnitrix activation sound ...
	 * grant the power at that moment"): the pod's watch lands on {@code p}'s wrist -- {@link #grant}, then the user's activation
	 * sound and the shake-off ({@link #ANIM_LATCH}, the movement slowed for {@link OmnitrixConfig#LATCH_TICKS}). False if the
	 * grant was refused.
	 */
	public static boolean latch(ServerPlayer p) {
		return latch(p, false);
	}

	public static boolean latch(ServerPlayer p, boolean recal) {
		if (!grant(p, recal)) {
			return false;
		}
		OmnitrixState n = state(p).copy();
		n.animId = ANIM_LATCH;
		n.animStart = p.level().getGameTime();
		save(p, n);
		latchLock(p, true);
		OmnitrixSounds.playOn(p, OmnitrixSounds.LATCH);
		return true;
	}

	/** v0.17.1: the shake-off is playing on {@code p} (client-safe: the synced state and the game time). */
	public static boolean latching(Player p) {
		OmnitrixState s = state(p);
		long t = p.level().getGameTime() - s.animStart;
		return s.hasPower && s.animId == ANIM_LATCH && t >= 0 && t < OmnitrixConfig.LATCH_TICKS;
	}

	private static void latchLock(ServerPlayer p, boolean on) {
		var speed = net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
		var jump = net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH;
		if (on) {
			var op = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
			com.projecthero.mod.hero.power.PowerToggles.modifier(p, speed, LATCH_SLOW_ID, OmnitrixConfig.LATCH_SLOW, op);
			com.projecthero.mod.hero.power.PowerToggles.modifier(p, jump, LATCH_JUMP_ID, -1.0, op);
			p.setSprinting(false);
		} else {
			com.projecthero.mod.hero.power.PowerToggles.clearModifier(p, speed, LATCH_SLOW_ID);
			com.projecthero.mod.hero.power.PowerToggles.clearModifier(p, jump, LATCH_JUMP_ID);
		}
	}

	/** v0.17.1: whether the shake-off's slow is on {@code p} (gametests). */
	public static boolean latchSlowed(ServerPlayer p) {
		var a = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		return a != null && a.getModifier(LATCH_SLOW_ID) != null;
	}

	/**
	 * v0.17.2 (user: "when unlocking these aliens save the data to the player and not the omnitrix so if the player takes off the
	 * omnitrix and puts it back on they still have all these aliens unlocked please"): what belongs to the player, not the watch --
	 * every alien he has unlocked ({@code Aliens.foundKey}: a mistransform or the To'kustar Shard) and his mistransform count -- is
	 * carried from {@code from} into the fresh state when the watch comes off ({@link #revoke}) and when one goes back on
	 * ({@link #grant}). The state attachment is persistent and copied on death, so it survives logging out and dying too.
	 */
	public static void keepPlayerData(OmnitrixState from, OmnitrixState to) {
		for (var e : from.timers.entrySet()) {
			if (e.getKey().startsWith("omnitrix.found.") || e.getKey().equals(MISTRANSFORMS) || e.getKey().equals(MASTER_KNOWN)) {
				to.timers.put(e.getKey(), e.getValue());
			}
		}
	}

	/** Tears the power down (the pod's success, an admin revoke, a forced replacement). Turns the wearer back first. */
	public static void revoke(ServerPlayer p) {
		if (!hasPower(p)) {
			return;
		}
		if (state(p).transformed()) {
			revert(p, Reason.REVOKE);
		}
		OmnitrixState bare = new OmnitrixState();
		keepPlayerData(state(p), bare); // v0.17.2: taking the watch off keeps the aliens he unlocked
		save(p, bare);
		p.removeAttached(OmnitrixAttachments.EXTRACTION);
		latchLock(p, false);
		forget(p.getUUID());
	}

	// ---------------------------------------------------------------- transform / revert

	/** V: become the alien picked on the dial. False (with the reason on the action bar) if it cannot happen now. */
	public static boolean transform(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (!s.hasPower || s.transformed()) {
			return false;
		}
		long now = p.level().getGameTime();
		if (OmnitrixRecal.busy(s)) {
			OmnitrixRecal.tellBusy(p); // v0.17.2: recalibrating -- no aliens until it is done
			return false;
		}
		if (extracting(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.held_by_pod").withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.suppressed").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (now < s.rechargeUntil) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.recharging",
					(s.rechargeUntil - now + 19) / 20).withStyle(ChatFormatting.RED), true);
			OmnitrixSounds.notify(p, OmnitrixSounds.DENIED);
			return false;
		}
		if (s.readyAt(RECAL) == 1L && batteryTicks(s, now) < OmnitrixConfig.BATTERY_MIN_TICKS) {
			// v0.17.2 (user: "The recalibrated omnitrix should have a battery system instead of timer like the prototype, player should be able to stay an alien for about 4 minutes in total this time and detransform at will without the omnitrix going into recharge mode, full recharge of the battery should be 90 seconds")
			long need = (OmnitrixConfig.BATTERY_MIN_TICKS - batteryTicks(s, now)) * (long) OmnitrixConfig.BATTERY_RECHARGE_TICKS
					/ OmnitrixConfig.BATTERY_TICKS;
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.battery_low", (need + 19) / 20).withStyle(ChatFormatting.RED), true);
			OmnitrixSounds.notify(p, OmnitrixSounds.DENIED);
			return false;
		}
		AlienKit kit = Aliens.heldBack(s.selected) ? null : Aliens.kit(s.selected); // v0.17.3: a held-back alien is "coming soon"
		if (kit == null) {
			Aliens.Alien a = Aliens.byId(s.selected);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.coming_soon",
					Component.translatable(a == null ? "projecthero.omnitrix.alien.unknown" : a.nameKey())).withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		float frac = p.getMaxHealth() > 0f ? p.getHealth() / p.getMaxHealth() : 1f;
		// the slap already started the user's transform sound (its swell peaks as the body changes); a direct transform starts it now
		boolean slapped = s.timers.containsKey(SLAP_TIMER) || s.timers.containsKey(OmnitrixLongTransform.TIMER);
		OmnitrixState n = s.copy();
		n.active = kit.id();
		n.transformedAt = now;
		// v0.17.2: the recalibrated watch's time limit is what its battery has left
		n.endsAt = now + (n.readyAt(RECAL) == 1L ? batteryTicks(s, now) : OmnitrixConfig.TRANSFORM_TICKS);
		if (s.timers.containsKey(OmnitrixLongTransform.TIMER)) {
			// v0.17.2: mid long transformation -- the 3 minutes start when it has finished
			n.endsAt += Math.max(0L, OmnitrixLongTransform.total(s) - (now - s.readyAt(OmnitrixLongTransform.TIMER)));
		}
		n.animId = ANIM_TRANSFORM;
		n.animStart = now;
		n.humanHealth = frac;
		if (n.readyAt(RECAL) != 1L) {
			n.timers.put(TRANSFORMS, n.readyAt(TRANSFORMS) + 1); // v0.17.2: the Recalibration Chip counts these
		}
		n.kitFlags = 0;
		n.heldEntity = 0;
		n.selecting = false;
		clearSlap(n);
		OmnitrixRitual.clear(n); // v0.17.4: transforming ends the ritual
		save(p, n);
		OmnitrixBody.apply(p, kit.stats(), frac);
		if (!slapped) {
			OmnitrixSounds.playOn(p, cue(p, OmnitrixSounds.TRANSFORM_USER, OmnitrixSounds.RECAL_TRANSFORM));
		}
		kit.onTransform(p);
		refreshName(p);
		Aliens.Alien a = Aliens.byId(kit.id());
		p.displayClientMessage(Component.translatable(a.nameKey()).withStyle(st -> st.withColor(a.colour()).withBold(true)), true);
		return true;
	}

	/**
	 * Turns the wearer back to human, right now. A timeout starts the 90 s recharge; Shift+V a short one. V and the timeout go
	 * through {@link #beginPowerDown} (the five beeps) and land here on {@link #POWER_DOWN_REVERT_TICK}; anything else (death,
	 * logout, a dimension change, suppression, the pod, a revoke) mid-sequence cuts it short here and silences the sound.
	 */
	/**
	 * v0.17.2 (user: "when the player transforms into a ben 10 alien make their name change to the alien's name"): a transformed
	 * player is known by the alien's name, in its colour -- name tag, chat, death messages, the tab list (the identity mixins ask
	 * this after the Miraculous). Reads only the synced state, so every client and the server agree. Null when not an alien.
	 */
	public static net.minecraft.network.chat.MutableComponent alienName(net.minecraft.world.entity.player.Player p) {
		OmnitrixState s = state(p);
		if (!s.transformed()) {
			return null;
		}
		com.projecthero.mod.omnitrix.alien.Aliens.Alien a = com.projecthero.mod.omnitrix.alien.Aliens.byId(s.active);
		if (a == null) {
			return null;
		}
		return net.minecraft.network.chat.Component.translatable(a.nameKey())
				.withStyle(st -> st.withColor(net.minecraft.network.chat.TextColor.fromRgb(a.colour() & 0xFFFFFF)));
	}

	/** The tab list's names are cached on every client: re-send this player's after a transform or a revert. */
	static void refreshName(ServerPlayer p) {
		if (p.getServer() != null) {
			p.getServer().getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(
					java.util.EnumSet.of(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), java.util.List.of(p)));
		}
	}

	/** Takes off the framework's hidden Resistance I (a potion's or a beacon's -- visible, or stronger -- is left alone). */
	static void dropAlienResistance(ServerPlayer p) {
		var res = p.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
		if (res != null && res.getAmplifier() == 0 && res.isAmbient() && !res.showIcon() && res.getDuration() <= 100) {
			p.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
		}
	}

	public static void revert(ServerPlayer p, Reason reason) {
		revert(p, reason, false);
	}

	private static void revert(ServerPlayer p, Reason reason, boolean sequenced) {
		OmnitrixState s = state(p);
		if (!s.transformed()) {
			return;
		}
		OmnitrixLongTransform.finish(p, true); // v0.17.2: a revert mid long transformation cuts it off
		s = state(p);
		if (poweringDown(s) && !sequenced) {
			OmnitrixSounds.stopPowerDown(p);
		}
		AlienKit kit = Aliens.kit(s.active);
		if (kit != null) {
			try {
				kit.onRevert(p);
				dropAlienResistance(p);
				refreshName(p);
			} catch (RuntimeException e) {
				com.projecthero.mod.ProjectHeroMod.LOGGER.error("[Omnitrix] {} onRevert failed", s.active, e);
			}
		}
		boolean masterRevert = s.readyAt(MASTER) == 1L;
		int battery = batteryTicks(s, p.level().getGameTime()); // v0.17.2: what the recalibrated battery has left
		OmnitrixBody.remove(p, reason != Reason.DEATH);
		long now = p.level().getGameTime();
		OmnitrixState n = state(p).copy();
		n.active = "";
		n.kitFlags = 0;
		n.heldEntity = 0;
		n.selecting = false;
		n.animId = reason == Reason.TIMEOUT || reason == Reason.FAILSAFE ? ANIM_TIMEOUT : ANIM_REVERT;
		n.animStart = now;
		n.endsAt = 0L;
		n.timers.remove(POWER_DOWN_TIMER);
		n.timers.remove(POWER_DOWN_TIMEOUT);
		n.timers.remove(PENDING_SWITCH);
		n.timers.remove(BADGE_HIT);
		n.timers.remove(HOLD_BATTERY); // v0.17.2: Alien X's held battery
		boolean recal = n.readyAt(RECAL) == 1L;
		if (recal) {
			// v0.17.2 (user: "The recalibrated omnitrix should have a battery system instead of timer like the prototype, player should be able to stay an alien for about 4 minutes in total this time and detransform at will without the omnitrix going into recharge mode, full recharge of the battery should be 90 seconds"): the battery keeps what it had left (flat after a timeout), refilling from now
			int left = reason == Reason.TIMEOUT ? 0 : battery;
			n.timers.put(BATTERY, left * 1000L);
			n.timers.put(BATTERY_AT, now);
		}
		n.rechargeUntil = n.readyAt(MASTER) == 1L ? 0L : recal ? switch (reason) {
			// run flat (or thrown out by the failsafe): recharge mode until the battery is full again
			case TIMEOUT, FAILSAFE -> now + (long) (OmnitrixConfig.BATTERY_TICKS - (reason == Reason.TIMEOUT ? 0 : battery))
					* OmnitrixConfig.BATTERY_RECHARGE_TICKS / OmnitrixConfig.BATTERY_TICKS;
			default -> 0L;
		} : switch (reason) {
			case TIMEOUT -> now + OmnitrixConfig.RECHARGE_TICKS;
			case SHIFT_V -> now + OmnitrixConfig.EARLY_RECHARGE_TICKS;
			case FAILSAFE -> now + OmnitrixConfig.RECHARGE_TICKS; // v0.17.2: the normal recharge
			default -> 0L;
		};
		save(p, n);
		if (reason == Reason.DEATH || reason == Reason.LOGOUT || reason == Reason.SWITCH) {
			return;
		}
		// the sequence's sound is already playing (this is its power-down); an immediate revert keeps the short whir
		if (!sequenced) {
			// v0.17.2 (user): Master Control's N turn-back plays the power-down recording without its beeps
			// v0.17.2: the Recalibrated Omnitrix's N turn-back is the user's detransform recording
			helixFx(p, reason == Reason.TIMEOUT && recalibrated(p) ? OmnitrixSounds.RECAL_REVERT // v0.17.2: its own detransform, no beeps
					: reason == Reason.TIMEOUT || reason == Reason.FAILSAFE ? OmnitrixSounds.TIMEOUT
					: reason == Reason.SHIFT_V && recalibrated(p) ? OmnitrixSounds.RECAL_REVERT
					: reason == Reason.SHIFT_V && masterRevert ? OmnitrixSounds.POWER_DOWN_MASTER : OmnitrixSounds.REVERT);
		} else if (recalibrated(p)) {
			// v0.17.2: the timeout's beeps end on the recalibrated watch's detransform
			OmnitrixSounds.playOn(p, OmnitrixSounds.RECAL_REVERT);
		}
		if (reason == Reason.TIMEOUT && !sequenced) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.timeout").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
					true);
		}
	}

	// ---------------------------------------------------------------- the watch up (selection)

	public static boolean selecting(Player p) {
		return hasPower(p) && state(p).selecting;
	}

	/** V as a human: the wrist comes up, the core pops, the face shows the selected alien. False (and why) if it cannot. */
	public static boolean activate(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (!s.hasPower || s.transformed() || s.selecting) {
			return false;
		}
		// v0.17.1: not while still trying to shake it off
		if (latching(p)) {
			return false;
		}
		long now = p.level().getGameTime();
		// v0.17.2 (user: "while the omnitrix is recalibrating make it so that the player can still lift the omnitrix by pressing R and
		// look at the watch similar to how it is in when the omnitrix is timed out"): raised to look at the blue face -- the dial stays
		// down (popAt) and nothing can be picked; only not mid insert / reform
		if (OmnitrixRecal.busy(s)) {
			OmnitrixRecal.tellBusy(p);
			if (OmnitrixRecal.insertAge(s, now, 0f) >= 0f || OmnitrixRecal.finishAge(s, now, 0f) >= 0f) {
				return false;
			}
		}
		// v0.17.1 (user: "even if the omnitrix is timed out let the player press r to bring their arm up, they can turn the dial
		// but the omnitrix wont let them transform"): the recharge no longer stops the raise -- it refuses the slap ({@link #slap})
		if (extracting(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.held_by_pod").withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.suppressed").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		OmnitrixState n = s.copy();
		if (!Aliens.available(p, n.selected) && !Aliens.playable(p).isEmpty()) {
			n.selected = Aliens.playable(p).get(0).id();
		}
		n.selecting = true;
		n.selectStart = now;
		clearSlap(n);
		OmnitrixRitual.clear(n); // v0.17.4: every raise starts the ritual from nothing
		// the core-up sound plays when the core pops ({@link #tick}): the button press -- or, raised while timed out, not until it
		// times in (v0.17.1 user: "when you activate the omnitrix and the omnitrix is timed out then the omnitrix sound effect doesnt
		// play")
		n.timers.put(PRESS_TIMER, popAt(n));
		save(p, n);
		showSelected(p, n.selected);
		return true;
	}

	/** V again: the core sinks back, the face goes back to normal, the arm comes down. No cost. */
	public static boolean deactivate(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (!s.selecting) {
			return false;
		}
		OmnitrixState n = s.copy();
		n.selecting = false;
		n.selectStart = p.level().getGameTime();
		clearSlap(n);
		OmnitrixRitual.clear(n); // v0.17.4: the watch down ends the ritual
		save(p, n);
		// v0.17.1 (user: "when deactivating the omnitrix ... remove the sound effect that plays"): lowered silently
		return true;
	}

	/** Gametests: lets the dial turn again at once (the 0.2 s cooldown runs on game time, which a test does not advance). */
	public static void clearDialCooldown(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (s.animId == ANIM_TURN) {
			OmnitrixState n = s.copy();
			n.animStart -= OmnitrixConfig.DIAL_COOLDOWN_TICKS;
			save(p, n);
		}
	}

	/** The scroll wheel while the watch is up: turns the dial {@code dir} (+1 / -1) to the next alien that has a kit. */
	public static boolean cycle(ServerPlayer p, int dir) {
		OmnitrixState s = state(p);
		java.util.List<Aliens.Alien> pool = Aliens.playable(p);
		if (!s.selecting || pool.isEmpty() || dir == 0 || s.timers.containsKey(MC_SEQ) || s.timers.containsKey(OmnitrixLongTransform.TIMER)) {
			return false;
		}
		// v0.17.1 (user): a 0.2 s cooldown on the dial so it cannot be spun too fast
		long now = p.level().getGameTime();
		if (now < s.rechargeUntil) {
			// v0.17.1 (user: "while the omnitrix is timed out the omnitrix shouldnt show aliens ... the player can only select
			// aliens once the omnitrix has timed in"): the dial stays locked on the red face until the recharge is over
			return false;
		}
		if (!coreUp(s, now)) {
			return false; // the dial is still coming up under the right hand
		}
		if (s.animId == ANIM_TURN && now - s.animStart < OmnitrixConfig.DIAL_COOLDOWN_TICKS) {
			return false;
		}
		int i = 0;
		for (int k = 0; k < pool.size(); k++) {
			if (pool.get(k).id().equals(s.selected)) {
				i = k;
			}
		}
		String next = pool.get(Math.floorMod(i + Integer.signum(dir), pool.size())).id();
		OmnitrixState n = s.copy();
		n.selected = next;
		n.animId = ANIM_TURN;
		n.animStart = p.level().getGameTime();
		save(p, n);
		OmnitrixSounds.notify(p, cue(p, OmnitrixSounds.DIAL, OmnitrixSounds.RECAL_DIAL), 1.0f);
		showSelected(p, next);
		OmnitrixRitual.onDial(p, dir); // v0.17.4: the Master Control ritual's two rounds of the dial
		return true;
	}

	/** Left-click (or right-click) while the watch is up: the core is slapped down; {@link #SLAP_TICKS} later, the alien. */
	public static boolean slap(ServerPlayer p) {
		return slap(p, false);
	}

	/**
	 * v0.17.2: {@code longForm} = the right-click -- the long, show-accurate transformation ({@link OmnitrixLongTransform}) for an
	 * alien that has one (Four Arms); any other alien gets the quick slap.
	 */
	public static boolean slap(ServerPlayer p, boolean longForm) {
		OmnitrixState s = state(p);
		if (!s.selecting || s.timers.containsKey(SLAP_TIMER) || s.timers.containsKey(SLAP_QUEUE) || s.timers.containsKey(MC_SEQ)
				|| s.timers.containsKey(OmnitrixLongTransform.TIMER)) {
			return false;
		}
		if (!Aliens.available(p, s.selected)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.coming_soon",
					Component.translatable(Aliens.byId(s.selected) == null ? "projecthero.omnitrix.alien.unknown" : Aliens.byId(s.selected).nameKey()))
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		long now = p.level().getGameTime();
		if (now < s.rechargeUntil) {
			// still recharging: the face flashes red with the time left and the core will not go down
			OmnitrixState n = s.copy();
			n.animId = ANIM_DENIED;
			n.animStart = now;
			save(p, n);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.recharging",
					(s.rechargeUntil - now + 19) / 20).withStyle(ChatFormatting.RED), true);
			OmnitrixSounds.notify(p, OmnitrixSounds.DENIED);
			return false;
		}
		// v0.17.4: the Master Control ritual's beeps -- on the third beep the slap wakes Master Control; off the beat it mistransforms
		switch (OmnitrixRitual.onSlap(p)) {
			case WAKE -> {
				p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
				OmnitrixSounds.play(p.serverLevel(), p.position(), OmnitrixSounds.SLAP, 0.8f, 1.2f);
				return startMasterSequence(p);
			}
			case MISS -> {
				OmnitrixState g = state(p).copy();
				g.timers.put(FORCE_GLITCH, 1L);
				save(p, g);
				s = state(p);
				longForm = false; // the glitch rides the quick slap
			}
			case NONE -> {
			}
		}
		if (!coreUp(s, now)) {
			// clicked while the dial is still coming up: the slap lands as soon as it is ({@link #tick})
			OmnitrixState q = s.copy();
			q.timers.put(SLAP_QUEUE, coreUpAt(s));
			if (longForm) {
				q.timers.put(SLAP_QUEUE_LONG, 1L);
			}
			save(p, q);
			return true;
		}
		OmnitrixState n = s.copy();
		n.timers.remove(SLAP_QUEUE);
		n.animId = ANIM_SLAP;
		n.animStart = now;
		if (longForm && OmnitrixLongTransform.has(s.selected)) {
			// the episode's soundtrack carries the slap, the swell and everything after it
			OmnitrixLongTransform.start(p, n, now);
			save(p, n);
			p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
			return true;
		}
		n.timers.put(SLAP_TIMER, now + SLAP_TICKS);
		save(p, n);
		OmnitrixSounds.play(p.serverLevel(), p.position(), OmnitrixSounds.SLAP, 1.0f, 1.0f);
		// v0.17.1: the user's transform sound starts with the slap -- its swell peaks ~0.28 s in, as the body changes (SLAP_TICKS)
		OmnitrixSounds.playOn(p, cue(p, OmnitrixSounds.TRANSFORM_USER, OmnitrixSounds.RECAL_TRANSFORM));
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		return true;
	}

	/** v0.17.2: the alien kit takes a player's own turn-back over (Alien X: the council votes on it). */
	private static boolean heldBack(ServerPlayer p) {
		OmnitrixState s = state(p);
		AlienKit kit = s.transformed() ? Aliens.kit(s.active) : null;
		return kit != null && kit.holdsRevert(p);
	}

	private static void showSelected(ServerPlayer p, String id) {
		// v0.17.1 (user: "when selecting an alien remove the alien name that pops up"): the dial icon is the only cue
	}

	/** V while transformed (Shift+V is an alias): the five beeps, then back to human. A second press during the beeps does nothing. */
	public static boolean revertEarly(ServerPlayer p) {
		if (heldBack(p)) {
			return false; // v0.17.2: Alien X's council must agree first
		}
		// v0.17.1 (user: "for master control remove the initial beeps when player wants to detransform just make it happen
		// instantly"): under Master Control N turns him back at once -- no five beeps first
		if (state(p).transformed() && (state(p).readyAt(MASTER) == 1L || recalibrated(p))) { // v0.17.2: the recalibrated watch too (its own detransform sound)
			revert(p, Reason.SHIFT_V);
			return true;
		}
		return beginPowerDown(p, false);
	}

	/**
	 * Starts the power-down: the user's power-down sound (following the wearer), the beeps blinking on the watch and the HUD, and
	 * {@link #POWER_DOWN_REVERT_TICK} later the revert ({@link #tick}). The wearer is still the alien meanwhile and can still
	 * fight; the time limit stops. False if not transformed or already powering down.
	 */
	public static boolean beginPowerDown(ServerPlayer p, boolean timeout) {
		OmnitrixState s = state(p);
		if (!s.transformed() || poweringDown(s)) {
			return false;
		}
		OmnitrixState n = s.copy();
		n.timers.put(POWER_DOWN_TIMER, p.level().getGameTime());
		n.timers.put(POWER_DOWN_TIMEOUT, timeout ? 1L : 0L);
		save(p, n);
		OmnitrixSounds.playOn(p, OmnitrixSounds.POWER_DOWN_USER);
		if (timeout) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.timeout").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
					true);
		}
		return true;
	}

	/** Picks {@code alienId} on the dial (the dial screen's payload). False for an unknown or "coming soon" alien. */
	public static boolean select(ServerPlayer p, String alienId) {
		OmnitrixState s = state(p);
		if (!s.hasPower || Aliens.byId(alienId) == null || Aliens.byId(alienId).recalibrated() != recalibrated(p)) {
			return false;
		}
		if (!Aliens.playable(alienId) || !Aliens.found(p, alienId)) { // v0.17.2: a hidden alien (Cannonbolt) only once a mistransform found it
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.coming_soon",
					Component.translatable(Aliens.byId(alienId).nameKey())).withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!alienId.equals(s.selected)) {
			OmnitrixState n = s.copy();
			n.selected = alienId;
			save(p, n);
		}
		OmnitrixSounds.notify(p, OmnitrixSounds.SELECT);
		return true;
	}

	/** The pod's "fighting back": the wearer flickers into {@code alienId} for {@code ticks} (shown only: no body change). */
	public static void flicker(ServerPlayer p, String alienId, int ticks) {
		OmnitrixState n = state(p).copy();
		n.flicker = alienId;
		n.flickerUntil = p.level().getGameTime() + ticks;
		save(p, n);
		helixFx(p, OmnitrixSounds.FLICKER);
	}

	/**
	 * The helix flash: server particles + a sound (v0.17.1: the transform sweep, Shift+V's wind-down, the timeout's red
	 * power-down or the pod's flicker); the client draws the strands from the anim. v0.17.1 polish: a transformation's flash is
	 * the client's green / red Kirby-dot burst ({@code OmnitrixTransformFx}, from the synced anim), so only the pod's flicker
	 * (no anim) still sends the white flash.
	 */
	private static void helixFx(ServerPlayer p, OmnitrixSfx.Cue sound) {
		ServerLevel level = p.serverLevel();
		if (sound == OmnitrixSounds.FLICKER) {
			double h = p.getBbHeight();
			level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + h * 0.5, p.getZ(), 1, 0, 0, 0, 0);
		}
		OmnitrixSounds.play(level, p.position(), sound, 1.0f, 1.0f);
	}

	// ---------------------------------------------------------------- tick

	/** Every server tick (from {@code AbilityRouter.serverTick}). */
	public static void tick(ServerPlayer p) {
		OmnitrixState s = p.getAttached(OmnitrixAttachments.STATE);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = p.level().getGameTime();
		// v0.17.3: a world that already had him in a held-back alien (OmnitrixFeature) turns him back; the dial falls back on the next raise
		if (s.transformed() && Aliens.heldBack(s.active)) {
			revert(p, Reason.REVOKE);
			s = state(p);
			if (!s.hasPower) {
				return;
			}
		}
		// v0.17.1: the shake-off is over -- the slow comes off
		if (!latching(p) && latchSlowed(p)) {
			latchLock(p, false);
		}
		// v0.17.2: the Recalibration Chip's insert, recalibration mode and the reform run before anything else of the watch's
		if (OmnitrixRecal.busy(s)) {
			OmnitrixRecal.tick(p, now);
			s = state(p);
		}
		// v0.17.2 (user: "Switching alien forms without reverting to human first should drain some of the timer, for example switching alien forms should drain like 30 seconds from the timer, also the alien switcher for recalibrated should only work while in alien form unless the user has master control, if the player chooses another alien via the alien switcher with recalibrated they need to hit their omnitrix badge first and then transform"): the hand lands on the badge -- the switch
		if (s.transformed() && s.timers.containsKey(BADGE_HIT) && now >= s.readyAt(BADGE_HIT) + BADGE_HIT_TICKS) {
			OmnitrixState n = s.copy();
			n.timers.remove(BADGE_HIT);
			n.timers.remove(PENDING_SWITCH);
			save(p, n);
			switchNow(p, n.selected);
			s = state(p);
		}
		if (s.selecting && s.timers.containsKey(PRESS_TIMER) && now >= s.readyAt(PRESS_TIMER)) {
			OmnitrixState n = s.copy();
			n.timers.remove(PRESS_TIMER);
			save(p, n);
			OmnitrixSounds.play(p.serverLevel(), p.position(), cue(p, OmnitrixSounds.CORE_UP, OmnitrixSounds.RECAL_CORE_UP), 1.0f, 1.0f);
			s = n;
		}
		if (s.selecting && s.timers.containsKey(SLAP_QUEUE) && coreUp(s, now)) {
			OmnitrixState n = s.copy();
			n.timers.remove(SLAP_QUEUE);
			boolean longForm = n.timers.remove(SLAP_QUEUE_LONG) != null;
			save(p, n);
			slap(p, longForm);
			return;
		}
		if (s.timers.containsKey(OmnitrixLongTransform.TIMER)) {
			// v0.17.2: the long transformation runs its own clock (the body changes on its beat, not the slap's)
			OmnitrixLongTransform.tick(p, s, now);
			s = state(p);
			if (!s.transformed()) {
				OmnitrixAbilityManager.tick(p);
				return;
			}
		}
		if (s.timers.containsKey(MC_SEQ)) {
			tickMasterSequence(p, s, now);
			OmnitrixAbilityManager.tick(p);
			return;
		}
		// v0.17.4: the Master Control ritual (the look, the crouches, the timeouts, the beeps)
		if (s.selecting || s.timers.containsKey(OmnitrixRitual.STAGE) || s.timers.containsKey(OmnitrixRitual.LOOK)) {
			OmnitrixRitual.tick(p, s, now);
			s = state(p);
		}
		if (s.selecting && s.timers.containsKey(SLAP_TIMER) && now >= s.readyAt(SLAP_TIMER)) {
			// v0.17.1 (user: the failsafe "glitch"): now and then the Omnitrix lands on another alien than the one dialled -- never
			// under Master Control
			String dialled = s.selected;
			java.util.List<Aliens.Alien> pool = Aliens.playable(p);
			// v0.17.2 (user: "the recalibrated omnitrix didnt mistransform so remove the 50% chance to get the wrong alien for the recalibrated")
			// v0.17.2 (user: "for the canonbolt alien i want ben to accidentally transform into the alien like a mistransformation"):
			// a hidden alien (Cannonbolt) is never dialled -- the first glitch that fires always lands on the first one he has not
			// found yet, and that puts it on his dial for good
			// v0.17.2 (user: "make it so that on the 11th mistransformation the player unlocks a new alien which for now is just
			// canonbolt"): every mistransform is counted; the 11th (then the 22nd... for later hidden aliens) lands on the next
			// hidden alien and finds it -- the rest land on a random alien from the dial
			// v0.17.2 (user: "make it an even chance to unlock either cannonbolt or wildvine if you dont unlock any of them"): which one is
			// an even pick among every hidden alien he has not found yet
			Aliens.Alien hidden = Aliens.pickHidden(p, p.getRandom());
			String found = null;
			boolean forced = s.timers.containsKey(FORCE_GLITCH);
			if (forced) {
				// v0.17.4: the ritual's slap off the beat -- a wrong alien on any watch (not counted, finds nothing)
				OmnitrixState g = state(p).copy();
				g.timers.remove(FORCE_GLITCH);
				if (pool.size() > 1) {
					String other = dialled;
					while (other.equals(dialled)) {
						other = pool.get(p.getRandom().nextInt(pool.size())).id();
					}
					g.selected = other;
				}
				save(p, g);
			} else if (s.readyAt(MASTER) != 1L && s.readyAt(RECAL) != 1L && s.readyAt(NO_GLITCH) != 1L && (pool.size() > 1 || hidden != null)
					&& p.getRandom().nextFloat() < OmnitrixConfig.GLITCH_CHANCE) {
				String other = dialled;
				long count = s.readyAt(MISTRANSFORMS) + 1L;
				OmnitrixState c = state(p).copy();
				c.timers.put(MISTRANSFORMS, count);
				save(p, c);
				if (hidden != null && (count % OmnitrixConfig.UNLOCK_EVERY_MISTRANSFORMS == 0 || pool.size() <= 1)) {
					other = found = hidden.id();
					Aliens.discover(p, found, true);
				}
				while (other.equals(dialled)) {
					other = pool.get(p.getRandom().nextInt(pool.size())).id();
				}
				OmnitrixState g = state(p).copy();
				g.selected = other;
				save(p, g);
			}
			boolean glitched = !state(p).selected.equals(dialled);
			if (transform(p) && glitched) {
				// v0.17.2 (user: "when the player mistransforms into the wrong alien dont make the text show up"): no message --
				// except the one time a mistransform finds a new alien, so he knows it is on his dial now
				OmnitrixState g = state(p).copy();
				g.selected = dialled; // the dial stays where he put it
				save(p, g);
				if (found != null) {
					p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.found",
							Component.translatable(hidden.nameKey())).withStyle(ChatFormatting.YELLOW), true);
				}
				return;
			}
			if (glitched && !state(p).transformed()) {
				// the glitch's transform was refused: back to the dialled alien, and nothing found (nor counted)
				OmnitrixState g = state(p).copy();
				g.selected = dialled;
				if (!forced) {
					g.timers.put(MISTRANSFORMS, Math.max(0L, g.readyAt(MISTRANSFORMS) - 1L));
				}
				save(p, g);
				if (found != null) {
					Aliens.discover(p, found, false);
				}
			}
			if (state(p).transformed()) {
				return;
			}
			if (!transform(p)) {
				OmnitrixState n = state(p).copy();
				clearSlap(n);
				n.selecting = false;
				save(p, n);
			}
			return;
		}
		if (s.transformed()) {
			AlienKit kit = Aliens.kit(s.active);
			if (kit == null) {
				revert(p, Reason.REVOKE);
				return;
			}
			if (!OmnitrixBody.applied(p)) {
				OmnitrixBody.apply(p, kit.stats(), p.getHealth() / Math.max(1f, p.getMaxHealth()));
			}
			OmnitrixBody.keepArmourOff(p); // v0.17.2 (user: "the aliens arent supposed to wear armour")
			if (com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
				revert(p, Reason.SUPPRESSED);
				return;
			}
			// v0.17.1 (user: "when ben transforms into an alien give all his alien forms regen 1"): Regeneration I for as long as he
			// is an alien (refreshed before it runs out, no particles, so it never flickers off between refreshes)
			if (now % 40 == 0 || !p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)) {
				p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 80, 0, true, false, true));
			}
			// v0.17.2 (user: "give every alien resistance level 1"): Resistance I the same way -- hidden (no particles, no icon), never
			// over a stronger Resistance he already has, and not while his kit says his body is bare (Ghostfreak's True Form)
			if (kit.toughBody(p)) {
				var res = p.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
				if (res == null || (res.getAmplifier() == 0 && res.getDuration() < 60)) {
					p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 0, true, false, false));
				}
			} else {
				dropAlienResistance(p);
			}
			if (poweringDown(s)) {
				// the five beeps: still the alien (the kit keeps ticking), the time limit stopped, no warning blips
				long start = s.readyAt(POWER_DOWN_TIMER);
				if (now - start >= POWER_DOWN_REVERT_TICK || now < start) {
					revert(p, s.readyAt(POWER_DOWN_TIMEOUT) == 1L ? Reason.TIMEOUT : Reason.SHIFT_V, true);
					OmnitrixAbilityManager.tick(p);
					return;
				}
				kit.tick(p, state(p), now);
				OmnitrixAbilityManager.tick(p);
				return;
			}
			if (s.readyAt(MASTER) == 1L || s.timers.containsKey(HOLD_BATTERY)) { // v0.17.2: Alien X holds the time limit off too
				// v0.17.1 Master Control: no time limit -- the clock is held full
				if (now % 20 == 0 && s.endsAt - now < OmnitrixConfig.TRANSFORM_TICKS - 10) {
					OmnitrixState n = state(p).copy();
					n.endsAt = now + OmnitrixConfig.TRANSFORM_TICKS;
					save(p, n);
				}
			} else if (now >= s.endsAt && s.readyAt(RECAL) == 1L) {
				// v0.17.2 (user: "when the recalibrated omnitrix times out dont play the prototype sound effect with the beeps and stuff, just
				// detransform with the regular recalibrated detransformation sound effect"): straight back, no power-down beeps
				revert(p, Reason.TIMEOUT);
				OmnitrixAbilityManager.tick(p);
				return;
			} else if (now >= s.endsAt) {
				beginPowerDown(p, true);
				kit.tick(p, state(p), now);
				OmnitrixAbilityManager.tick(p);
				return;
			}
			long left = s.endsAt - now;
			// v0.17.1 (user: "remove the beeping that starts ... before a transformation ends"): no warning blips -- the only
			// detransform sound is the user's power-down; the watch still flashes red in the last WARN_TICKS
			kit.tick(p, state(p), now);
		} else if (s.rechargeUntil > 0L && now >= s.rechargeUntil) {
			// v0.17.1: timed in -- announced at once; but raised through it, the recharge mark is kept until the core is up, so
			// the red-to-green fade and the held-back pop (popAt) play out instead of the dial snapping up
			boolean held = s.selecting && s.selectStart < s.rechargeUntil && now < popAt(s) + 20; // (the alien screen has morphed in by then)
			OmnitrixState n = s.copy();
			if (!s.timers.containsKey(TIMED_IN)) {
				n.timers.put(TIMED_IN, now);
				OmnitrixSounds.notify(p, OmnitrixSounds.RECHARGED);
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.recharged").withStyle(ChatFormatting.GREEN), true);
			}
			if (!held) {
				n.rechargeUntil = 0L;
				n.timers.remove(TIMED_IN);
			}
			save(p, n);
		}
		OmnitrixState cur = state(p);
		if (cur.flickerUntil != 0L && now >= cur.flickerUntil) {
			OmnitrixState n = cur.copy();
			n.flicker = "";
			n.flickerUntil = 0L;
			save(p, n);
		}
		OmnitrixAbilityManager.tick(p);
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onDeath(ServerPlayer p) {
		OmnitrixLongTransform.finish(p, true);
		OmnitrixRecal.interrupt(p); // v0.17.2: the sequence on screen stops; recalibration mode carries on
		if (hasPower(p) && state(p).transformed()) {
			revert(p, Reason.DEATH);
		}
		OmnitrixState s = state(p);
		if (s.hasPower && (s.flickerUntil != 0L || s.rechargeUntil != 0L || s.selecting)) {
			OmnitrixState n = s.copy();
			n.selecting = false;
			clearSlap(n);
			n.flicker = "";
			n.flickerUntil = 0L;
			n.rechargeUntil = 0L;
			save(p, n);
		}
		p.removeAttached(OmnitrixAttachments.EXTRACTION);
		latchLock(p, false);
		forget(p.getUUID());
	}

	public static void onLogout(ServerPlayer p) {
		OmnitrixLongTransform.finish(p, true);
		OmnitrixRecal.interrupt(p); // v0.17.2
		if (hasPower(p) && state(p).transformed()) {
			revert(p, Reason.LOGOUT);
		}
		if (selecting(p)) {
			OmnitrixState n = state(p).copy();
			n.selecting = false;
			clearSlap(n);
			save(p, n);
		}
		p.removeAttached(OmnitrixAttachments.EXTRACTION);
		forget(p.getUUID());
	}

	public static void onDimensionChange(ServerPlayer p) {
		OmnitrixLongTransform.finish(p, true);
		// mid power-down: turn back now (with the recharge it was heading for) rather than carry the beeps into the new world
		if (poweringDown(p)) {
			revert(p, state(p).readyAt(POWER_DOWN_TIMEOUT) == 1L ? Reason.TIMEOUT : Reason.SHIFT_V);
		}
		forget(p.getUUID());
	}

	/** Per-player static state (the key map, the message throttle). */
	public static void forget(UUID id) {
		LAST_REJECT.remove(id);
		OmnitrixAbilityManager.forget(id);
	}

	/** {@code ServerStateReset}. */
	public static void clearSessionState() {
		LAST_REJECT.clear();
		OmnitrixAbilityManager.clearSessionState();
	}
}
