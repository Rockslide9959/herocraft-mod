package com.projecthero.mod.sentry;

import com.projecthero.mod.sentry.data.SentryState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * v0.17.5 rework (user: "make the let it out move a toggle ability, when player uses the move they have to win a minigame and if they
 * lose then their void bar goes up but if they win they get the new moveset and buffed abilities while being merged with the void,
 * during this mode the void bar goes up faster"): Let It Out (Shift+V) as a toggle won through a trial.
 *
 * <ul>
 *   <li><b>Shift+V as the Sentry</b> starts the trial ({@link #begin}): {@code letOutTrialStart} is set and synced, the owner's client
 *   opens "Hold the Light" ({@code LetItOutScreen}, 8 s) and reports how it went ({@code SentryActionPayload.TRIAL_RESULT}).</li>
 *   <li><b>The server decides</b> ({@link #report}), loosely like the Shame Rooms: only while a trial runs; a win counts only
 *   {@link SentryConfig#TRIAL_MIN_WIN_TICKS} or more into it on the server's clock (an honest win can't be quicker), a too-quick "win"
 *   counts as a loss. No report by {@link SentryConfig#TRIAL_TICKS} + {@link SentryConfig#TRIAL_GRACE_TICKS}: lost ({@link #tick}).</li>
 *   <li><b>Lost</b>: the Void jumps {@link SentryConfig#TRIAL_LOSE_VOID} (flat) and nothing else.</li>
 *   <li><b>Won</b>: merged with the Void (Void Form): the Void kit, every hit +25%, the Void moves recharge 25% faster, and every Void
 *   gain x1.5 (plus 0.5 a second of its own).</li>
 *   <li><b>Shift+V (or V) while merged</b> goes back to the Sentry, only below 90 Void ({@code VoidMoves.returnToSentry}).</li>
 * </ul>
 * The old 50-Void gate is gone (the trial replaces it); the 5-minute lock after a takeover stays.
 */
public final class LetItOut {
	/** What a report did. */
	public enum Result {
		IGNORED, WON, LOST
	}

	private LetItOut() {
	}

	/** Whether a trial is running for him (client-safe: the state is synced). */
	public static boolean running(SentryState s) {
		return s != null && s.hasPower && s.letOutTrialStart != 0L && !s.voidForm && !s.inTakeover();
	}

	/** Shift+V as the Sentry: start the trial. False if locked, already running, or merged / taken. */
	public static boolean begin(ServerPlayer p) {
		SentryState s = Sentry.state(p);
		long now = Sentry.now(p);
		if (!s.hasPower || s.voidForm || s.inTakeover() || s.letOutTrialStart != 0L) {
			return false;
		}
		if (s.letOutLockedUntil > now) {
			Sentry.say(p, "message.projecthero.sentry.let_out_locked", ChatFormatting.GRAY, (s.letOutLockedUntil - now + 19) / 20);
			return false;
		}
		SentryState n = s.copy();
		n.letOutTrialStart = now;
		n.calming = false;
		n.beamStart = 0L;
		n.gripTarget = -1;
		Sentry.save(p, n);
		Sentry.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.8f);
		Sentry.say(p, "message.projecthero.sentry.trial_start", ChatFormatting.DARK_GRAY);
		return true;
	}

	/** The client's word on the trial (from the payload; gametests call it directly). */
	public static Result report(ServerPlayer p, boolean won) {
		SentryState s = Sentry.state(p);
		if (!running(s)) {
			return Result.IGNORED;
		}
		long ran = Sentry.now(p) - s.letOutTrialStart;
		if (won && ran >= SentryConfig.TRIAL_MIN_WIN_TICKS) {
			win(p);
			return Result.WON;
		}
		lose(p);
		return Result.LOST;
	}

	/** Every tick of a Sentry with a trial running: no report long after it should have ended counts as a loss. */
	static void tick(ServerPlayer p) {
		SentryState s = Sentry.state(p);
		if (s.letOutTrialStart == 0L) {
			return;
		}
		if (!running(s)) {
			SentryState n = s.copy();
			n.letOutTrialStart = 0L;
			Sentry.save(p, n);
			return;
		}
		long ran = Sentry.now(p) - s.letOutTrialStart;
		if (ran > SentryConfig.TRIAL_TICKS + SentryConfig.TRIAL_GRACE_TICKS || ran < 0L) {
			lose(p);
		}
	}

	private static void win(ServerPlayer p) {
		Sentry.setVoidForm(p, true); // (clears the trial)
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.5, 1.0, 0.5, 0.05);
		level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 1.0, 0.5, 0.05);
		Sentry.sound(p, SoundEvents.WARDEN_EMERGE, 0.8f, 0.8f);
		Sentry.say(p, "message.projecthero.sentry.let_out", ChatFormatting.DARK_GRAY);
	}

	private static void lose(ServerPlayer p) {
		SentryState n = Sentry.state(p).copy();
		n.letOutTrialStart = 0L;
		Sentry.save(p, n);
		Sentry.sound(p, SoundEvents.WARDEN_ROAR, 0.5f, 1.2f);
		Sentry.say(p, "message.projecthero.sentry.trial_lost", ChatFormatting.DARK_RED, Math.round(SentryConfig.TRIAL_LOSE_VOID));
		if (!Sentry.free(p)) {
			Sentry.setVoid(p, Sentry.state(p).voidLevel + SentryConfig.TRIAL_LOSE_VOID); // flat: the suit / merging don't scale it
		}
	}
}
