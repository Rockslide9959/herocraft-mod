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
	/** v0.17.1: the shake-off's slow (transient modifiers, so a save or relog mid-way never keeps them). */
	private static final net.minecraft.resources.ResourceLocation LATCH_SLOW_ID = com.projecthero.mod.ProjectHeroMod.id("omnitrix_latch_slow");
	private static final net.minecraft.resources.ResourceLocation LATCH_JUMP_ID = com.projecthero.mod.ProjectHeroMod.id("omnitrix_latch_jump");
	/** Ticks between the slap and the transformation (the core going down). */
	public static final int SLAP_TICKS = 5;
	private static final String SLAP_TIMER = "omnitrix.slap";
	/** A click before the core is up: the slap waits for it ({@link #CORE_UP_TICKS}). */
	private static final String SLAP_QUEUE = "omnitrix.slap_queued";
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
		n.timers.remove(PRESS_TIMER);
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
		SHIFT_V, TIMEOUT, DEATH, LOGOUT, EXTRACTION, REVOKE, SUPPRESSED
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
		if (hasPower(p)) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(p, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(p, KEY);
		OmnitrixState s = new OmnitrixState();
		s.hasPower = true;
		s.selected = Aliens.playable().isEmpty() ? Aliens.HEATBLAST.id() : Aliens.playable().get(0).id();
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
		if (!grant(p)) {
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

	/** Tears the power down (the pod's success, an admin revoke, a forced replacement). Turns the wearer back first. */
	public static void revoke(ServerPlayer p) {
		if (!hasPower(p)) {
			return;
		}
		if (state(p).transformed()) {
			revert(p, Reason.REVOKE);
		}
		save(p, new OmnitrixState());
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
		AlienKit kit = Aliens.kit(s.selected);
		if (kit == null) {
			Aliens.Alien a = Aliens.byId(s.selected);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.coming_soon",
					Component.translatable(a == null ? "projecthero.omnitrix.alien.unknown" : a.nameKey())).withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		float frac = p.getMaxHealth() > 0f ? p.getHealth() / p.getMaxHealth() : 1f;
		// the slap already started the user's transform sound (its swell peaks as the body changes); a direct transform starts it now
		boolean slapped = s.timers.containsKey(SLAP_TIMER);
		OmnitrixState n = s.copy();
		n.active = kit.id();
		n.transformedAt = now;
		n.endsAt = now + OmnitrixConfig.TRANSFORM_TICKS;
		n.animId = ANIM_TRANSFORM;
		n.animStart = now;
		n.humanHealth = frac;
		n.kitFlags = 0;
		n.heldEntity = 0;
		n.selecting = false;
		clearSlap(n);
		save(p, n);
		OmnitrixBody.apply(p, kit.stats(), frac);
		if (!slapped) {
			OmnitrixSounds.playOn(p, OmnitrixSounds.TRANSFORM_USER);
		}
		kit.onTransform(p);
		Aliens.Alien a = Aliens.byId(kit.id());
		p.displayClientMessage(Component.translatable(a.nameKey()).withStyle(st -> st.withColor(a.colour()).withBold(true)), true);
		return true;
	}

	/**
	 * Turns the wearer back to human, right now. A timeout starts the 90 s recharge; Shift+V a short one. V and the timeout go
	 * through {@link #beginPowerDown} (the five beeps) and land here on {@link #POWER_DOWN_REVERT_TICK}; anything else (death,
	 * logout, a dimension change, suppression, the pod, a revoke) mid-sequence cuts it short here and silences the sound.
	 */
	public static void revert(ServerPlayer p, Reason reason) {
		revert(p, reason, false);
	}

	private static void revert(ServerPlayer p, Reason reason, boolean sequenced) {
		OmnitrixState s = state(p);
		if (!s.transformed()) {
			return;
		}
		if (poweringDown(s) && !sequenced) {
			OmnitrixSounds.stopPowerDown(p);
		}
		AlienKit kit = Aliens.kit(s.active);
		if (kit != null) {
			try {
				kit.onRevert(p);
			} catch (RuntimeException e) {
				com.projecthero.mod.ProjectHeroMod.LOGGER.error("[Omnitrix] {} onRevert failed", s.active, e);
			}
		}
		OmnitrixBody.remove(p, reason != Reason.DEATH);
		long now = p.level().getGameTime();
		OmnitrixState n = state(p).copy();
		n.active = "";
		n.kitFlags = 0;
		n.heldEntity = 0;
		n.selecting = false;
		n.animId = reason == Reason.TIMEOUT ? ANIM_TIMEOUT : ANIM_REVERT;
		n.animStart = now;
		n.endsAt = 0L;
		n.timers.remove(POWER_DOWN_TIMER);
		n.timers.remove(POWER_DOWN_TIMEOUT);
		n.rechargeUntil = switch (reason) {
			case TIMEOUT -> now + OmnitrixConfig.RECHARGE_TICKS;
			case SHIFT_V -> now + OmnitrixConfig.EARLY_RECHARGE_TICKS;
			default -> 0L;
		};
		save(p, n);
		if (reason == Reason.DEATH || reason == Reason.LOGOUT) {
			return;
		}
		// the sequence's sound is already playing (this is its power-down); an immediate revert keeps the short whir
		if (!sequenced) {
			helixFx(p, reason == Reason.TIMEOUT ? OmnitrixSounds.TIMEOUT : OmnitrixSounds.REVERT);
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
		if (!Aliens.playable(n.selected) && !Aliens.playable().isEmpty()) {
			n.selected = Aliens.playable().get(0).id();
		}
		n.selecting = true;
		n.selectStart = now;
		clearSlap(n);
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
		java.util.List<Aliens.Alien> pool = Aliens.playable();
		if (!s.selecting || pool.isEmpty() || dir == 0) {
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
		OmnitrixSounds.notify(p, OmnitrixSounds.DIAL, 1.0f);
		showSelected(p, next);
		return true;
	}

	/** Left-click (or right-click) while the watch is up: the core is slapped down; {@link #SLAP_TICKS} later, the alien. */
	public static boolean slap(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (!s.selecting || s.timers.containsKey(SLAP_TIMER) || s.timers.containsKey(SLAP_QUEUE)) {
			return false;
		}
		if (!Aliens.playable(s.selected)) {
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
		if (!coreUp(s, now)) {
			// clicked while the dial is still coming up: the slap lands as soon as it is ({@link #tick})
			OmnitrixState q = s.copy();
			q.timers.put(SLAP_QUEUE, coreUpAt(s));
			save(p, q);
			return true;
		}
		OmnitrixState n = s.copy();
		n.timers.remove(SLAP_QUEUE);
		n.animId = ANIM_SLAP;
		n.animStart = now;
		n.timers.put(SLAP_TIMER, now + SLAP_TICKS);
		save(p, n);
		OmnitrixSounds.play(p.serverLevel(), p.position(), OmnitrixSounds.SLAP, 1.0f, 1.0f);
		// v0.17.1: the user's transform sound starts with the slap -- its swell peaks ~0.28 s in, as the body changes (SLAP_TICKS)
		OmnitrixSounds.playOn(p, OmnitrixSounds.TRANSFORM_USER);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		return true;
	}

	private static void showSelected(ServerPlayer p, String id) {
		// v0.17.1 (user: "when selecting an alien remove the alien name that pops up"): the dial icon is the only cue
	}

	/** V while transformed (Shift+V is an alias): the five beeps, then back to human. A second press during the beeps does nothing. */
	public static boolean revertEarly(ServerPlayer p) {
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
		if (!s.hasPower || Aliens.byId(alienId) == null) {
			return false;
		}
		if (!Aliens.playable(alienId)) {
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
		// v0.17.1: the shake-off is over -- the slow comes off
		if (!latching(p) && latchSlowed(p)) {
			latchLock(p, false);
		}
		if (s.selecting && s.timers.containsKey(PRESS_TIMER) && now >= s.readyAt(PRESS_TIMER)) {
			OmnitrixState n = s.copy();
			n.timers.remove(PRESS_TIMER);
			save(p, n);
			OmnitrixSounds.play(p.serverLevel(), p.position(), OmnitrixSounds.CORE_UP, 1.0f, 1.0f);
			s = n;
		}
		if (s.selecting && s.timers.containsKey(SLAP_QUEUE) && coreUp(s, now)) {
			OmnitrixState n = s.copy();
			n.timers.remove(SLAP_QUEUE);
			save(p, n);
			slap(p);
			return;
		}
		if (s.selecting && s.timers.containsKey(SLAP_TIMER) && now >= s.readyAt(SLAP_TIMER)) {
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
			if (com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
				revert(p, Reason.SUPPRESSED);
				return;
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
			if (now >= s.endsAt) {
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
