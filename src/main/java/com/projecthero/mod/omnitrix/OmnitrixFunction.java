package com.projecthero.mod.omnitrix;

import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixSecrets;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.5 (user: "after priming the omnitrix the player can shift+right click to go into function mode of the omnitrix, the dial
 * goes down, the watch face returns to normal and the player can enter codes, scrolling up or down changes the number for the
 * current code and pressing middle mouse button goes onto the next number, this is similar to how it works in the alien evo mod"):
 * the watch's function mode, server side.
 *
 * <ul>
 *   <li><b>In / out</b> ({@link #enter} / {@link #leave}, the client's {@code OmnitrixActionPayload.FUNCTION}): only with the watch
 *       primed (raised, core up, nothing else going on). The core sinks and the face goes back to the plain hourglass; the client
 *       draws the six-slot readout on it. Lowering the watch, transforming or raising it again ends it.</li>
 *   <li><b>Typing</b> is the client's ({@link OmnitrixCodeEntry}); only the finished code comes here ({@link #submit}). The server
 *       checks it against the player's secrets ({@link OmnitrixCodes}) and rate-limits it.</li>
 *   <li><b>Codes:</b> his Master Control code (on -- or off if it is on -- via {@code Omnitrix.startMasterSequence}); a hidden
 *       alien's code (puts it on his dial); {@link OmnitrixConfig#SELF_DESTRUCT_CODE} (the harmless countdown; the same code
 *       cancels it). Anything else: the error buzz, the digits that match his Master Control code in place flash green
 *       (Mastermind), and function mode locks for {@link OmnitrixConfig#CODE_LOCK_TICKS}.</li>
 * </ul>
 * All state is in the wearer's synced {@link OmnitrixState} timers (no static maps, nothing for {@code ServerStateReset}).
 */
public final class OmnitrixFunction {
	/** In function mode since (game time). */
	public static final String FUNC = "omnitrix.func";
	/** Left function mode at (game time; the core rising again). */
	public static final String FUNC_OUT = "omnitrix.func_out";
	/** Function mode is locked until (game time) after a wrong code. */
	public static final String LOCK = "omnitrix.func_lock";
	/** The last code's outcome ({@link Outcome#ordinal}) and when (game time). */
	public static final String RESULT = "omnitrix.func_result", RESULT_AT = "omnitrix.func_result_at";
	/** A wrong code's hint: bit i set = digit i matched his Master Control code in place. */
	public static final String HINT = "omnitrix.func_hint";
	/** The self-destruct countdown started at (game time). */
	public static final String SELF_DESTRUCT = "omnitrix.self_destruct";

	/** What a submitted code did. */
	public enum Outcome {
		REFUSED, LOCKED, MASTER, ALIEN, SELF_DESTRUCT_ARMED, SELF_DESTRUCT_CANCELLED, WRONG, FIZZLED
	}

	private OmnitrixFunction() {
	}

	// ---------------------------------------------------------------- client-safe reads

	public static boolean active(OmnitrixState s) {
		return s.timers.containsKey(FUNC);
	}

	public static boolean active(net.minecraft.world.entity.player.Player p) {
		return Omnitrix.hasPower(p) && active(Omnitrix.state(p));
	}

	/** 0 (primed: core up, the alien on the face) .. 1 (function mode: core down, the plain face) at {@code t} (game time + partial). */
	public static float fold(OmnitrixState s, float t) {
		if (s.timers.containsKey(FUNC)) {
			return smooth((t - s.readyAt(FUNC)) / OmnitrixConfig.FUNCTION_FOLD_TICKS);
		}
		if (s.selecting && s.timers.containsKey(FUNC_OUT) && s.readyAt(FUNC_OUT) >= s.selectStart) {
			return 1f - smooth((t - s.readyAt(FUNC_OUT)) / OmnitrixConfig.FUNCTION_FOLD_TICKS);
		}
		return 0f;
	}

	public static boolean locked(OmnitrixState s, long now) {
		return now < s.readyAt(LOCK);
	}

	/** Ticks of the lock left (0 = open). */
	public static int lockLeft(OmnitrixState s, long now) {
		return (int) Math.max(0L, s.readyAt(LOCK) - now);
	}

	public static boolean selfDestructing(OmnitrixState s) {
		return s.timers.containsKey(SELF_DESTRUCT);
	}

	/** Ticks of the self-destruct countdown left (0 = none). */
	public static int selfDestructLeft(OmnitrixState s, long now) {
		if (!selfDestructing(s)) {
			return 0;
		}
		return (int) Math.max(0L, OmnitrixConfig.SELF_DESTRUCT_TICKS - (now - s.readyAt(SELF_DESTRUCT)));
	}

	/** The last outcome, or null if there was none. */
	public static Outcome lastOutcome(OmnitrixState s) {
		if (!s.timers.containsKey(RESULT)) {
			return null;
		}
		int i = (int) s.readyAt(RESULT);
		return i >= 0 && i < Outcome.values().length ? Outcome.values()[i] : null;
	}

	/** Ticks since the last outcome ({@code Float.MAX_VALUE} = none). */
	public static float resultAge(OmnitrixState s, long now, float partial) {
		return s.timers.containsKey(RESULT_AT) ? now + partial - s.readyAt(RESULT_AT) : Float.MAX_VALUE;
	}

	/** The last wrong code's hint mask (bit i = digit i right). */
	public static int hint(OmnitrixState s) {
		return (int) s.readyAt(HINT);
	}

	private static float smooth(float x) {
		x = Math.max(0f, Math.min(1f, x));
		return x * x * (3f - 2f * x);
	}

	// ---------------------------------------------------------------- in and out

	/** Whether {@code p} could go into function mode now (primed, nothing else using the dial). */
	public static boolean canEnter(ServerPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		return s.hasPower && s.selecting && !s.transformed() && !active(s) && Omnitrix.coreUp(s, now) && !Omnitrix.dialBusy(s)
				&& !OmnitrixRecal.busy(s) && !Omnitrix.latching(p);
	}

	/** Shift + right-click with the watch primed: the core sinks, the face goes plain, the readout comes up. */
	public static boolean enter(ServerPlayer p) {
		if (!canEnter(p)) {
			return false;
		}
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(FUNC, now);
		n.timers.remove(FUNC_OUT);
		n.timers.remove(OmnitrixAuto.HOLD_TIMER); // the R-hold auto-transform has no say in here
		Omnitrix.save(p, n);
		OmnitrixSounds.play(p.serverLevel(), p.position(), OmnitrixSounds.CORE_DOWN, 0.8f, 1.15f);
		return true;
	}

	/** Shift + right-click again: the readout goes, the core pops back up with the alien on it. */
	public static boolean leave(ServerPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		if (!active(s)) {
			return false;
		}
		OmnitrixState n = s.copy();
		n.timers.remove(FUNC);
		n.timers.put(FUNC_OUT, p.level().getGameTime());
		Omnitrix.save(p, n);
		OmnitrixSounds.play(p.serverLevel(), p.position(), Omnitrix.cue(p, OmnitrixSounds.CORE_UP, OmnitrixSounds.RECAL_CORE_UP), 0.7f, 1.0f);
		return true;
	}

	/** The client's FUNCTION request: in, or out again. */
	public static boolean toggle(ServerPlayer p) {
		return active(Omnitrix.state(p)) ? leave(p) : enter(p);
	}

	/** Function mode over without a sound (the watch lowered or raised again, a transformation). Keeps the lock and the countdown. */
	static void clear(OmnitrixState n) {
		n.timers.remove(FUNC);
		n.timers.remove(FUNC_OUT);
	}

	/** Death / logout: everything of function mode goes, the self-destruct countdown with it. */
	static void reset(OmnitrixState n) {
		clear(n);
		n.timers.remove(LOCK);
		n.timers.remove(SELF_DESTRUCT);
		n.timers.remove(HINT);
	}

	/** Gametests: the lock and the submit gap over at once (game time does not move inside one test tick). */
	public static void clearLock(ServerPlayer p) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(LOCK);
		n.timers.remove(RESULT_AT);
		Omnitrix.save(p, n);
	}

	// ---------------------------------------------------------------- the codes

	/** The client's CODE request: the six digits typed in function mode. */
	public static Outcome submit(ServerPlayer p, String code) {
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		if (!active(s) || !OmnitrixCodeEntry.valid(code) || s.transformed()) {
			return Outcome.REFUSED;
		}
		if (locked(s, now)) {
			OmnitrixSounds.notify(p, OmnitrixSounds.DENIED);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.code.locked", (lockLeft(s, now) + 19) / 20)
					.withStyle(ChatFormatting.RED), true);
			return Outcome.LOCKED;
		}
		long at = s.readyAt(RESULT_AT);
		if (s.timers.containsKey(RESULT_AT) && now >= at && now - at < OmnitrixConfig.CODE_SUBMIT_GAP_TICKS) {
			return Outcome.REFUSED;
		}
		OmnitrixSecrets secrets = OmnitrixCodes.secrets(p);
		if (code.equals(OmnitrixConfig.SELF_DESTRUCT_CODE)) {
			return selfDestruct(p, now);
		}
		if (code.equals(secrets.master())) {
			OmnitrixCodes.learnAll(p); // he typed it: he knows it
			OmnitrixState n = Omnitrix.state(p).copy();
			result(n, Outcome.MASTER, now);
			n.timers.put(HINT, (1L << OmnitrixConfig.CODE_LENGTH) - 1);
			clear(n);
			n.timers.put(FUNC_OUT, now);
			Omnitrix.save(p, n);
			OmnitrixSounds.notify(p, OmnitrixSounds.SELECT, 1.6f);
			// the dial turns through every alien, the watch goes down and Master Control flips -- on, or off if it was on
			Omnitrix.startMasterSequence(p);
			return Outcome.MASTER;
		}
		for (Aliens.Alien a : OmnitrixCodes.codeable()) {
			if (code.equals(secrets.aliens().get(a.id()))) {
				return alien(p, a, now);
			}
		}
		// wrong: Mastermind against his Master Control code only, then the lock
		int mask = 0;
		for (int i = 0; i < OmnitrixConfig.CODE_LENGTH; i++) {
			if (code.charAt(i) == secrets.master().charAt(i)) {
				mask |= 1 << i;
			}
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		result(n, Outcome.WRONG, now);
		n.timers.put(HINT, (long) mask);
		n.timers.put(LOCK, now + OmnitrixConfig.CODE_LOCK_TICKS);
		Omnitrix.save(p, n);
		OmnitrixSounds.notify(p, OmnitrixSounds.DENIED);
		OmnitrixSounds.notify(p, OmnitrixSounds.WARNING, 0.6f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.code.wrong", OmnitrixConfig.CODE_LOCK_TICKS / 20)
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
		return Outcome.WRONG;
	}

	private static void result(OmnitrixState n, Outcome o, long now) {
		n.timers.put(RESULT, (long) o.ordinal());
		n.timers.put(RESULT_AT, now);
	}

	/** A hidden alien's code: it goes on his dial (and the dial jumps to it when it is on this watch). */
	private static Outcome alien(ServerPlayer p, Aliens.Alien a, long now) {
		OmnitrixCodes.learnAlien(p, a.id());
		boolean fresh = !Aliens.found(p, a.id());
		if (fresh) {
			Aliens.discover(p, a.id(), true);
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		result(n, Outcome.ALIEN, now);
		n.timers.put(HINT, 0L);
		clear(n);
		n.timers.put(FUNC_OUT, now);
		if (Aliens.available(p, a.id())) {
			n.selected = a.id();
			n.animId = Omnitrix.ANIM_TURN;
			n.animStart = now;
		}
		Omnitrix.save(p, n);
		OmnitrixSounds.notify(p, OmnitrixSounds.RECHARGED);
		OmnitrixSounds.notify(p, OmnitrixSounds.SELECT, 1.6f);
		p.displayClientMessage(Component.translatable(fresh ? "message.projecthero.omnitrix.code.alien_found" : "message.projecthero.omnitrix.code.alien_known",
				Component.translatable(a.nameKey())).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
		return Outcome.ALIEN;
	}

	// ---------------------------------------------------------------- the self-destruct (an easter egg: it never destroys anything)

	private static Outcome selfDestruct(ServerPlayer p, long now) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(HINT, 0L);
		if (n.timers.containsKey(SELF_DESTRUCT)) {
			n.timers.remove(SELF_DESTRUCT);
			result(n, Outcome.SELF_DESTRUCT_CANCELLED, now);
			Omnitrix.save(p, n);
			OmnitrixSounds.notify(p, OmnitrixSounds.RECHARGED);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.self_destruct.cancelled").withStyle(ChatFormatting.GREEN), true);
			return Outcome.SELF_DESTRUCT_CANCELLED;
		}
		n.timers.put(SELF_DESTRUCT, now);
		result(n, Outcome.SELF_DESTRUCT_ARMED, now);
		Omnitrix.save(p, n);
		OmnitrixSounds.play(p.serverLevel(), p.position(), OmnitrixSounds.SELF_DESTRUCT, 0.9f, 1.0f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.self_destruct.armed").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
		return Outcome.SELF_DESTRUCT_ARMED;
	}

	/** Gametests: the countdown as if it started {@code ticksAgo} ago. */
	public static void setSelfDestructElapsed(ServerPlayer p, int ticksAgo) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(SELF_DESTRUCT, p.level().getGameTime() - ticksAgo);
		Omnitrix.save(p, n);
	}

	/** Every server tick for a wearer (from {@code Omnitrix.tick}). */
	static void tick(ServerPlayer p, long now) {
		OmnitrixState s = Omnitrix.state(p);
		if (active(s) && (!s.selecting || s.transformed())) {
			OmnitrixState n = s.copy();
			clear(n);
			Omnitrix.save(p, n);
			s = n;
		}
		if (!selfDestructing(s)) {
			return;
		}
		long elapsed = now - s.readyAt(SELF_DESTRUCT);
		if (elapsed >= OmnitrixConfig.SELF_DESTRUCT_TICKS || elapsed < 0) {
			fizzle(p);
			return;
		}
		long left = OmnitrixConfig.SELF_DESTRUCT_TICKS - elapsed;
		// the beeps quicken: one a second, two a second in the last five, the siren every five seconds
		int every = left <= 5 * 20 ? 10 : 20;
		if (elapsed > 0 && elapsed % every == 0) {
			OmnitrixSounds.notify(p, OmnitrixSounds.WARNING, 1.0f + 0.9f * elapsed / (float) OmnitrixConfig.SELF_DESTRUCT_TICKS);
		}
		if (elapsed > 0 && elapsed % 100 == 0) {
			OmnitrixSounds.play(p.serverLevel(), p.position(), OmnitrixSounds.SELF_DESTRUCT, 0.7f, 1.0f + elapsed / (float) OmnitrixConfig.SELF_DESTRUCT_TICKS);
		}
	}

	/**
	 * Zero: nothing explodes. The watch sputters, puffs a little smoke and overheats -- it drops, its face goes red and it will
	 * not transform for {@link OmnitrixConfig#SELF_DESTRUCT_FIZZLE_TICKS}. No damage, no blocks, nothing lost.
	 */
	public static void fizzle(ServerPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		if (s.transformed()) {
			Omnitrix.revert(p, Omnitrix.Reason.TIMEOUT);
		}
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(SELF_DESTRUCT);
		result(n, Outcome.FIZZLED, now);
		n.rechargeUntil = Math.max(n.rechargeUntil, now + OmnitrixConfig.SELF_DESTRUCT_FIZZLE_TICKS);
		Omnitrix.save(p, n);
		Omnitrix.deactivate(p);
		ServerLevel level = p.serverLevel();
		double x = p.getX(), y = p.getY() + p.getBbHeight() * 0.55, z = p.getZ();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 18, 0.25, 0.2, 0.25, 0.02);
		level.sendParticles(ParticleTypes.POOF, x, y, z, 8, 0.2, 0.15, 0.2, 0.03);
		level.sendParticles(ParticleTypes.SMALL_FLAME, x, y, z, 4, 0.15, 0.1, 0.15, 0.01);
		OmnitrixSounds.play(level, p.position(), OmnitrixSounds.FIZZLE, 1.0f, 1.0f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.self_destruct.fizzle").withStyle(ChatFormatting.YELLOW), true);
		p.sendSystemMessage(Component.translatable("message.projecthero.omnitrix.self_destruct.fizzle_chat").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
