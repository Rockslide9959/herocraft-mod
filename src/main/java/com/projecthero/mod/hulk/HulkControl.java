package com.projecthero.mod.hulk;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user, docs/HULK_V0164_REVAMP_CONTRACT.md): the rampage. Whichever way he became the Hulk, every
 * {@code control.rampageEveryTicks} (a minute) from the moment he is out a WARNING appears: a random string of 6-8 arrows on
 * screen, to be typed with the arrow keys within {@code warningTicks} (10 s). A wrong arrow starts the string over. Typed in
 * time, there is no rampage and the next minute starts; too slow, and he RAMPAGES.
 *
 * <p>During a rampage the server drives him -- the player has no movement control (forced third person, the screen shrunk into
 * a letterbox, see {@code HulkRampageClient}). He roars ({@link HulkState#ANIM_RAMPAGE}), then hunts EVERY living thing within
 * {@code rampageRange} blocks -- squad-mates and other players included -- always the nearest, with aimed punches and his kit
 * ({@link HulkRampageAI}). He only breaks blocks when he is truly stuck. The player wins control back with the ring
 * minigame: Space as the ring meets the circle, {@code ringHitsNeeded} times in a row (5; a miss resets the streak).
 *
 * <p>v0.16.4 (user) -- three more ways he takes the body, each with a HARD end time set when it starts and never moved:
 * <ul>
 *   <li><b>Takeover</b>: the {@code takeoverAfterFails}-th (3rd) Hulk-form warning lost in a row (a passed warning resets
 *       the count) -- 5 minutes, no ring: he smashes, finds a mountain, carves a cave, sits in it, then runs off, and the
 *       player wakes up as Banner wherever he ended up ({@link HulkTakeover}).</li>
 *   <li><b>Banner's fight</b>: 10 minutes as Banner (ticks lived as Banner, so it pauses while dead or offline) and the Hulk
 *       fights to get out -- the same arrow warning; passed, he tries again 2 minutes later (then every 2 minutes); lost, he
 *       changes and goes on a 3-minute FORCED rampage (no ring), then the player has the Hulk back. These don't count
 *       toward the takeover.</li>
 *   <li><b>The death change</b>: Banner's death brings the Hulk out straight into a rampage once the change is over. v0.16.4
 *       (user): a MIND rampage ({@link HulkState#RAMPAGE_MIND}, {@code control.mindScreen}) -- the same AI, but instead of the
 *       ring the player's client shuts him inside his mind: a dark hall with a giant screen showing the Hulk 100 blocks away
 *       ({@code HulkMindScreen}); right-clicking it ends the rampage ({@link #mindScreenReached}). A hard end of
 *       {@code mindRampageTicks}.</li>
 * </ul>
 * Belt and braces: {@link #watchdog} force-releases anyone still locked 10 s past the end, and a relog resumes with the same
 * end time (an expired one is cleaned up on login, {@code Hulk.onPlayerJoin}).
 *
 * <p>The per-player bookkeeping is a static world-object cache, cleared by {@code ServerStateReset} through
 * {@link Hulk#clearSessionState}.
 */
public final class HulkControl {
	/** Arrow directions as sent by the client (and drawn by the HUD): 1 up, 2 left, 3 down, 4 right. */
	public static final int ARROW_UP = 1;
	public static final int ARROW_LEFT = 2;
	public static final int ARROW_DOWN = 3;
	public static final int ARROW_RIGHT = 4;
	/** v0.16.4 (user): the watchdog's slack past a rampage's end before it force-releases the player (10 s). */
	public static final long WATCHDOG_SLACK_TICKS = 200L;

	/** Rampage bookkeeping (server only): the fight AI's memory, the takeover's plan, the ring's last report. */
	private static final class Rampage {
		final HulkRampageAI.Brain brain = new HulkRampageAI.Brain();
		final HulkTakeover.Plan plan = new HulkTakeover.Plan();
		long lastRingAt = Long.MIN_VALUE / 2;
	}

	private static final Map<UUID, Rampage> RAMPAGES = new ConcurrentHashMap<>();

	private HulkControl() {
	}

	public static void clearSessionState() {
		RAMPAGES.clear();
	}

	public static void clear(UUID id) {
		RAMPAGES.remove(id);
	}

	/** Client-safe: is he rampaging right now? */
	public static boolean rampaging(net.minecraft.world.entity.player.Player player) {
		HulkState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.HULK_STATE, null);
		return s != null && s.rampaging(player.level().getGameTime());
	}

	/** Client-safe: is the arrow-key warning showing (the Hulk-form one as the Hulk, Banner's own as Banner)? */
	public static boolean warning(HulkState s, long now) {
		return s.hasPower && !s.dying() && s.rules.warnLen > 0 && now <= s.rules.warnUntil && !s.rampaging(now)
				&& s.hulk != s.wild.warnBanner;
	}

	/** The {@code i}-th arrow (1 up .. 4 right) of a packed warning string. */
	public static int arrowAt(int seq, int i) {
		return ((seq >> (2 * i)) & 3) + 1;
	}

	/** v0.16.4 (user): how long a rampage of {@code kind} lasts -- its hard end. */
	public static int durationTicks(int kind) {
		HulkConfig.Control cfg = HulkConfig.control();
		return switch (kind) {
			case HulkState.RAMPAGE_FORCED -> Math.max(40, cfg.forcedRampageTicks);
			case HulkState.RAMPAGE_TAKEOVER -> Math.max(200, cfg.takeoverTicks);
			case HulkState.RAMPAGE_MIND -> Math.max(200, cfg.mindRampageTicks);
			default -> Math.max(40, cfg.rampageMaxTicks);
		};
	}

	/** v0.16.4: Banner's clock -- the {@code Wild.bannerTicks} at which the Hulk next fights to get out. */
	public static int bannerThreshold(HulkState s) {
		return s.wild.bannerNextAt > 0 ? s.wild.bannerNextAt : Math.max(20, HulkConfig.control().bannerFirstWarnTicks);
	}

	// ---------------------------------------------------------------- the warning

	/** The player pressed an arrow key (1 up .. 4 right) while the warning was showing. */
	public static void answer(ServerPlayer player, int arrow) {
		HulkState s = Hulk.state(player);
		long now = player.level().getGameTime();
		if (!warning(s, now) || arrow < ARROW_UP || arrow > ARROW_RIGHT) {
			return;
		}
		HulkState n = s.copy();
		if (arrow == arrowAt(s.rules.warnSeq, s.rules.warnPos)) {
			n.rules.warnPos++;
			if (n.rules.warnPos >= n.rules.warnLen) {
				// held back -- no rampage this minute
				n.rules.warnLen = 0;
				n.rules.warnPos = 0;
				if (s.wild.warnBanner) {
					// v0.16.4 (user): "every 2 minutes after this the hulk keeps trying to take control"
					n.wild.warnBanner = false;
					n.wild.bannerNextAt = n.wild.bannerTicks + Math.max(20, HulkConfig.control().bannerRepeatWarnTicks);
				} else {
					n.wild.failStreak = 0; // v0.16.4 (user): a passed warning breaks the losing streak
					n.rules.nextRampageAt = now + HulkConfig.control().rampageEveryTicks;
				}
				Hulk.save(player, n);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f,
						1.4f);
				player.displayClientMessage(Component.translatable("message.projecthero.hulk.warning_passed").withStyle(ChatFormatting.GREEN), true);
				return;
			}
			Hulk.save(player, n);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS,
					0.5f, 0.8f + 0.12f * n.rules.warnPos);
			return;
		}
		// wrong: from the start again
		n.rules.warnPos = 0;
		Hulk.save(player, n);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8f,
				0.5f);
	}

	static void startWarning(ServerPlayer player) {
		startWarning(player, false);
	}

	/** {@code banner}: Banner's own fight (the Hulk trying to get out), not the Hulk-form minute's. */
	static void startWarning(ServerPlayer player, boolean banner) {
		HulkConfig.Control cfg = HulkConfig.control();
		long now = player.level().getGameTime();
		int min = Math.max(1, Math.min(15, cfg.warningMinArrows));
		int max = Math.max(min, Math.min(15, cfg.warningMaxArrows));
		int len = min + player.getRandom().nextInt(max - min + 1);
		int seq = 0;
		for (int i = 0; i < len; i++) {
			seq |= player.getRandom().nextInt(4) << (2 * i);
		}
		HulkState n = Hulk.state(player).copy();
		n.rules.warnSeq = seq;
		n.rules.warnLen = len;
		n.rules.warnPos = 0;
		n.rules.warnUntil = now + Math.max(20, cfg.warningTicks);
		n.wild.warnBanner = banner;
		Hulk.save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.5f);
		player.displayClientMessage(Component.translatable(banner ? "message.projecthero.hulk.warning_banner" : "message.projecthero.hulk.warning")
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
	}

	// ---------------------------------------------------------------- tick

	/**
	 * v0.16.4 (user): "make this work 100%" -- every Gamma player, every tick, BEFORE anything else (from {@link Hulk#tick},
	 * even while dead or dying): anyone still locked in a rampage {@link #WATCHDOG_SLACK_TICKS} past its end, or holding one
	 * from another world's clock, or rampaging as Banner, is released and the state cleaned (a takeover ends as Banner).
	 */
	public static void watchdog(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		if (s.combat.rampageUntil == 0L) {
			return;
		}
		long now = player.level().getGameTime();
		boolean overdue = now > s.combat.rampageUntil + WATCHDOG_SLACK_TICKS;
		boolean foreign = s.combat.rampageUntil - now > durationTicks(s.wild.kind) + 20L;
		if (!overdue && !foreign && s.hulk) {
			return;
		}
		if (s.hulk && !s.dying() && player.isAlive() && s.wild.kind == HulkState.RAMPAGE_TAKEOVER) {
			endTakeover(player);
			return;
		}
		RAMPAGES.remove(player.getUUID());
		HulkState n = s.copy();
		clearRampage(n);
		n.combat.control = 100.0f;
		if (n.hulk) {
			n.rules.nextRampageAt = now + HulkConfig.control().rampageEveryTicks;
		}
		Hulk.save(player, n);
	}

	/** Clears every running-rampage field of {@code n} (not the streak or Banner's clock). */
	static void clearRampage(HulkState n) {
		n.combat.rampageUntil = 0L;
		n.combat.promptKey = 0;
		n.rules.ringStreak = 0;
		n.wild.kind = HulkState.RAMPAGE_NORMAL;
		n.wild.phase = 0;
		n.wild.target = -1;
		if (n.animId == HulkState.ANIM_RAMPAGE) {
			n.animId = HulkState.ANIM_NONE;
		}
	}

	/** Every tick for a Gamma player (from {@link Hulk#tick}). */
	static void tick(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		long now = player.level().getGameTime();
		if (s.dying() || !HulkConfig.control().enabled) {
			if (s.combat.rampageUntil != 0L || s.rules.warnLen != 0 || s.rules.ringStreak != 0 || s.combat.promptKey != 0 || s.wild.pending != 0) {
				HulkState n = s.copy();
				clearRampage(n);
				n.wild.pending = 0;
				n.rules.warnLen = 0;
				n.rules.warnPos = 0;
				Hulk.save(player, n);
			}
			RAMPAGES.remove(player.getUUID());
			return;
		}
		if (!s.hulk) {
			RAMPAGES.remove(player.getUUID());
			tickBanner(player, s, now);
			return;
		}
		if (s.combat.rampageUntil != 0L) {
			if (now >= s.combat.rampageUntil) {
				finish(player); // the hard end
			} else {
				tickRampage(player, now);
			}
			return;
		}
		if (s.wild.warnBanner && s.rules.warnLen > 0) {
			HulkState n = s.copy(); // Banner's warning, left over from before the change
			n.rules.warnLen = 0;
			n.rules.warnPos = 0;
			n.wild.warnBanner = false;
			Hulk.save(player, n);
			return;
		}
		if (Hulk.changing(s, now)) {
			return;
		}
		if (s.wild.pending != 0) {
			// v0.16.4 (user): the death change (and Banner's lost fight) ends straight in a rampage
			startRampage(player, s.wild.pending - 1);
			return;
		}
		if (s.rules.warnLen > 0) {
			if (now > s.rules.warnUntil) {
				lostWarning(player); // too slow
			}
			return;
		}
		if (s.rules.nextRampageAt == 0L || s.rules.nextRampageAt > now + HulkConfig.control().rampageEveryTicks + HulkConfig.FORCED_CHANGE_TICKS) {
			// not set (an older save), or from another world's clock
			HulkState n = s.copy();
			n.rules.nextRampageAt = now + HulkConfig.control().rampageEveryTicks;
			Hulk.save(player, n);
			return;
		}
		if (now >= s.rules.nextRampageAt) {
			startWarning(player);
		}
	}

	/** The Hulk-form warning ran out: a rampage -- or, the {@code takeoverAfterFails}-th in a row, the takeover. */
	private static void lostWarning(ServerPlayer player) {
		HulkState n = Hulk.state(player).copy();
		n.wild.failStreak++;
		int need = Math.max(1, HulkConfig.control().takeoverAfterFails);
		boolean takeover = n.wild.failStreak >= need;
		Hulk.save(player, n);
		startRampage(player, takeover ? HulkState.RAMPAGE_TAKEOVER : HulkState.RAMPAGE_NORMAL);
		if (!takeover && n.wild.failStreak == need - 1) {
			player.displayClientMessage(Component.translatable("message.projecthero.hulk.takeover_close")
					.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
		}
	}

	/**
	 * v0.16.4 (user): "if you dont transform into the hulk for 10 minutes he fights to take control" -- Banner's clock counts
	 * ticks lived as Banner (this only runs while he is alive and online), the warning, and the change it forces when lost.
	 */
	private static void tickBanner(ServerPlayer player, HulkState s, long now) {
		if (s.combat.rampageUntil != 0L || s.wild.pending != 0 || s.wild.kind != HulkState.RAMPAGE_NORMAL || s.wild.target != -1) {
			HulkState n = s.copy(); // nothing of a rampage outlives the Hulk
			clearRampage(n);
			n.wild.pending = 0;
			Hulk.save(player, n);
			s = n;
		}
		if (s.rules.warnLen > 0 && !s.wild.warnBanner) {
			HulkState n = s.copy(); // the Hulk-form warning, left over
			n.rules.warnLen = 0;
			n.rules.warnPos = 0;
			Hulk.save(player, n);
			return;
		}
		HulkConfig.Control cfg = HulkConfig.control();
		if (s.rules.warnLen > 0) {
			if (now > s.rules.warnUntil) {
				// lost: he forces the change, then 3 minutes of forced rampage
				HulkState n = s.copy();
				n.rules.warnLen = 0;
				n.rules.warnPos = 0;
				n.wild.warnBanner = false;
				n.wild.bannerNextAt = n.wild.bannerTicks + Math.max(20, cfg.bannerRepeatWarnTicks);
				Hulk.save(player, n);
				Hulk.transform(player, true);
				if (Hulk.isHulk(player)) {
					HulkState h = Hulk.state(player).copy();
					h.wild.pending = HulkState.RAMPAGE_FORCED + 1;
					Hulk.save(player, h);
					player.displayClientMessage(Component.translatable("message.projecthero.hulk.banner_lost")
							.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
				}
			}
			return;
		}
		if (s.combat.strainStart != 0L || HulkMind.inSession(player) || Hulk.changing(s, now)) {
			return; // letting him out anyway, inside his own mind, or mid-change
		}
		if (now % 100L == 0L) {
			// counted (and synced) every 5 s: only while he is here and alive, so it pauses while dead or offline
			HulkState n = s.copy();
			n.wild.bannerTicks = (int) Math.min(Integer.MAX_VALUE - 200L, n.wild.bannerTicks + 100L);
			Hulk.save(player, n);
			s = n;
		}
		if (s.wild.bannerTicks >= bannerThreshold(s)) {
			startWarning(player, true);
		}
	}

	// ---------------------------------------------------------------- the rampage

	/** A normal (ring) rampage. */
	public static void startRampage(ServerPlayer player) {
		startRampage(player, HulkState.RAMPAGE_NORMAL);
	}

	/** v0.16.4: a rampage of {@code kind} ({@code HulkState.RAMPAGE_*}); its hard end is set here and never moved. */
	public static void startRampage(ServerPlayer player, int kind) {
		long now = player.level().getGameTime();
		HulkAbilities.endCharge(player);
		HulkGrab.release(player);
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.clear(player);
		player.ejectPassengers();
		HulkState n = Hulk.state(player).copy();
		n.combat.rampageUntil = now + durationTicks(kind);
		n.combat.promptKey = 0;
		n.combat.control = 0.0f;
		n.combat.smashChargeStart = 0L;
		n.combat.strainStart = 0L;
		n.leapChargeStart = 0L;
		n.rules.warnLen = 0;
		n.rules.warnPos = 0;
		n.rules.ringStreak = 0;
		n.wild.kind = kind;
		n.wild.phase = HulkState.TAKEOVER_SMASH;
		n.wild.pending = 0;
		n.wild.target = -1;
		n.wild.startedAt = now;
		n.wild.warnBanner = false;
		n.animId = HulkState.ANIM_RAMPAGE;
		n.animStart = now;
		Hulk.save(player, n);
		RAMPAGES.put(player.getUUID(), new Rampage());
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.5f, 0.8f);
		HulkCombat.shake(level, player.position(), 0.7f, 30, 32.0);
		String key = switch (kind) {
			case HulkState.RAMPAGE_TAKEOVER -> "message.projecthero.hulk.takeover_start";
			case HulkState.RAMPAGE_FORCED -> "message.projecthero.hulk.forced_start";
			case HulkState.RAMPAGE_MIND -> "message.projecthero.hulk.mind_start";
			default -> "message.projecthero.hulk.rampage_start";
		};
		player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD), true);
	}

	/** The hard end came: a takeover ends as Banner, the others hand the Hulk back. */
	static void finish(ServerPlayer player) {
		if (Hulk.state(player).wild.kind == HulkState.RAMPAGE_TAKEOVER) {
			endTakeover(player);
		} else {
			endRampage(player, false);
		}
	}

	/** {@code won}: the player won control back with the ring (otherwise the time ran out). */
	static void endRampage(ServerPlayer player, boolean won) {
		RAMPAGES.remove(player.getUUID());
		long now = player.level().getGameTime();
		HulkState s = Hulk.state(player);
		boolean forced = s.wild.kind == HulkState.RAMPAGE_FORCED;
		boolean mind = s.wild.kind == HulkState.RAMPAGE_MIND;
		HulkState n = s.copy();
		clearRampage(n);
		n.combat.control = 100.0f;
		n.combat.lastDealtAt = now;
		n.rules.nextRampageAt = now + HulkConfig.control().rampageEveryTicks;
		Hulk.save(player, n);
		player.setSprinting(false);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.2f, 0.6f);
		if (mind && won) {
			// v0.16.4 (user): back in the body -- he takes it with a roar
			ServerLevel level = (ServerLevel) player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.5f, 0.6f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.2f, 0.9f);
			level.sendParticles(HulkAbilities.GAMMA, player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(), 40, 0.8,
					player.getBbHeight() * 0.3, 0.8, 0.08);
		}
		String key = forced ? "message.projecthero.hulk.forced_end"
				: mind && won ? "message.projecthero.hulk.mind_end"
				: won ? "message.projecthero.hulk.rampage_end" : "message.projecthero.hulk.rampage_timeout";
		player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GREEN), true);
	}

	/**
	 * v0.16.4 (user): "they need to right click the screen to regain control of the hulk" -- the client says the player walked
	 * the mind hall up to the screen and right-clicked it. Only counts during a mind rampage, and not before
	 * {@code mindMinTicks} into it (the walk alone takes longer). True if it ended the rampage.
	 */
	public static boolean mindScreenReached(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		long now = player.level().getGameTime();
		if (!s.inMindScreen(now) || s.dying() || now - s.wild.startedAt < Math.max(0, HulkConfig.control().mindMinTicks)) {
			return false;
		}
		endRampage(player, true);
		return true;
	}

	/**
	 * v0.16.4 (user): "at the end he runs away and the player wakes up as Banner" -- the change back, wherever he came down.
	 * No 3-minute lock, no 1-minute hold-H cooldown; the losing streak starts over.
	 */
	static void endTakeover(ServerPlayer player) {
		RAMPAGES.remove(player.getUUID());
		HulkState n = Hulk.state(player).copy();
		clearRampage(n);
		n.combat.control = 100.0f;
		n.wild.failStreak = 0;
		Hulk.save(player, n);
		player.setSprinting(false);
		Hulk.revert(player, false);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.4f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.hulk.takeover_end").withStyle(ChatFormatting.GREEN), false);
	}

	/**
	 * The client's verdict on one Space press of the ring minigame ({@code hit}: the ring was on the circle). The server only
	 * counts: a hit adds to the streak, a miss resets it, {@code ringHitsNeeded} in a row end the rampage. Reports closer
	 * together than {@code ringMinGapTicks} are ignored. Only a normal rampage has the ring.
	 */
	public static void ringResult(ServerPlayer player, boolean hit) {
		HulkState s = Hulk.state(player);
		long now = player.level().getGameTime();
		if (!s.rampaging(now) || s.wild.kind != HulkState.RAMPAGE_NORMAL) {
			return;
		}
		Rampage r = RAMPAGES.computeIfAbsent(player.getUUID(), k -> new Rampage());
		if (now - r.lastRingAt < HulkConfig.control().ringMinGapTicks) {
			return; // faster than the ring can close: not a real press
		}
		r.lastRingAt = now;
		HulkState n = s.copy();
		if (!hit) {
			n.rules.ringStreak = 0;
			Hulk.save(player, n);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS,
					0.8f, 0.5f);
			return;
		}
		n.rules.ringStreak = s.rules.ringStreak + 1;
		Hulk.save(player, n);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f,
				0.9f + 0.12f * n.rules.ringStreak);
		if (n.rules.ringStreak >= Math.max(1, HulkConfig.control().ringHitsNeeded)) {
			endRampage(player, true);
		}
	}

	/** v0.16.4: how far a rampaging Hulk looks for something to smash (config {@code rampageRange}, 100 by default). */
	public static final double RAMPAGE_RANGE = 100.0;

	private static double range() {
		return Math.max(8.0, HulkConfig.control().rampageRange);
	}

	/**
	 * v0.13.17: out in the open -- standing at (or above) the surface: no more than 2 blocks under the highest solid block of
	 * its column (leaves don't count, so things under trees are fair game).
	 */
	public static boolean aboveGround(LivingEntity e) {
		int surface = e.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ());
		return e.getY() >= surface - 2.0;
	}

	/**
	 * v0.16.4 (user): every creature is fair game -- players and squad-mates included -- except his own pets, what he holds
	 * and what he can't get at (down in a cave under him: he would only dig).
	 */
	static boolean fairGame(ServerPlayer player, LivingEntity e) {
		if (e == player || !e.isAlive() || e.isSpectator() || e.distanceToSqr(player) > range() * range()) {
			return false;
		}
		if (e instanceof OwnableEntity pet && player.getUUID().equals(pet.getOwnerUUID())) {
			return false;
		}
		if ((e.isPassenger() && e.getVehicle() == player) || HulkGrab.isHeld(e)) {
			return false;
		}
		return aboveGround(e) || Math.abs(e.getY() - player.getY()) < 6.0;
	}

	/** The nearest fair-game creature within {@code r}. */
	static LivingEntity findTarget(ServerPlayer player, double r) {
		double rr = Math.min(r, range());
		return HulkCombat.targets(player, player.getBoundingBox().inflate(rr, 48.0, rr)).stream()
				.filter(e -> fairGame(player, e) && e.distanceToSqr(player) <= rr * rr)
				.min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
	}

	/** Another creature than {@code not} (where to throw what he grabbed), or null. */
	static LivingEntity findOther(ServerPlayer player, LivingEntity not) {
		double r = Math.min(40.0, range());
		return HulkCombat.targets(player, player.getBoundingBox().inflate(r, 16.0, r)).stream()
				.filter(e -> e != not && fairGame(player, e))
				.min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
	}

	/** Syncs who he is after (the owner's client turns him to face it). */
	static void syncTarget(ServerPlayer player, int id) {
		HulkState s = Hulk.state(player);
		if (s.wild.target != id) {
			HulkState n = s.copy();
			n.wild.target = id;
			Hulk.save(player, n);
		}
	}

	/**
	 * v0.16.4: re-aims a rampaging Hulk at his target before the moves' scheduled impacts run this tick ({@code Hulk.tick} calls
	 * it ahead of {@code HulkAbilities.tick}), so a punch thrown a few ticks ago still lands where the target is now.
	 */
	static void preTick(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		long now = player.level().getGameTime();
		if (!s.rampaging(now)) {
			return;
		}
		if (s.wild.target >= 0 && player.level().getEntity(s.wild.target) instanceof LivingEntity t && t.isAlive()) {
			HulkRampageAI.aim(player, HulkRampageAI.aimPoint(t), false, 30.0f);
		} else if (s.wild.kind == HulkState.RAMPAGE_TAKEOVER && s.wild.target < 0) {
			HulkRampageAI.face(player, s.wild.faceYaw, 10.0f);
		}
	}

	private static void tickRampage(ServerPlayer player, long now) {
		Rampage r = RAMPAGES.computeIfAbsent(player.getUUID(), k -> new Rampage());
		ServerLevel level = (ServerLevel) player.level();
		HulkConfig.Control cfg = HulkConfig.control();
		HulkState s = Hulk.state(player);
		if (!HulkAbilities.charging(player)) {
			player.setSprinting(false); // never the Sprint Smash
		}
		boolean sitting = s.wild.kind == HulkState.RAMPAGE_TAKEOVER && s.wild.phase == HulkState.TAKEOVER_SIT;
		if (now % 50L == 0L && !sitting) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.5f, 0.6f);
		}
		if (now % 10L == 0L) {
			level.sendParticles(HulkAbilities.GAMMA, player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(), 4, 0.5,
					player.getBbHeight() * 0.3, 0.5, 0.02);
		}
		if (now - s.wild.startedAt < cfg.rampageRoarTicks && now >= s.wild.startedAt) {
			return; // he stands and roars first
		}
		if (s.wild.kind == HulkState.RAMPAGE_TAKEOVER) {
			HulkTakeover.tick(player, r.brain, r.plan, now);
			return;
		}
		LivingEntity target = s.wild.target < 0 || !(level.getEntity(s.wild.target) instanceof LivingEntity l) || !fairGame(player, l) ? null : l;
		if (target == null || now % 20L == 0L) {
			target = findTarget(player, range());
		}
		syncTarget(player, target == null ? -1 : target.getId());
		if (target == null) {
			// nothing alive in range: he stands and heaves, roaring now and then
			if (player.onGround() && !Hulk.state(player).leaping && !HulkAbilities.busy(player)) {
				Vec3 v = player.getDeltaMovement();
				AbilityHelpers.launchSelf(player, new Vec3(v.x * 0.5, Math.min(v.y, 0.0), v.z * 0.5));
			}
			r.brain.stuckTicks = 0;
			return;
		}
		LivingEntity victim = target;
		HulkRampageAI.fight(player, r.brain, victim, now, () -> findOther(player, victim));
	}

	// ---------------------------------------------------------------- tests

	/** Tests: the last move the rampage AI used for this player (null if none). */
	public static String lastMove(ServerPlayer player) {
		Rampage r = RAMPAGES.get(player.getUUID());
		return r == null ? null : r.brain.lastMove;
	}

	/** Tests: punches the rampage AI has landed for this player. */
	public static int punchesLanded(ServerPlayer player) {
		Rampage r = RAMPAGES.get(player.getUUID());
		return r == null ? 0 : r.brain.punchesLanded;
	}

	/** Tests: one aimed rampage punch at {@code target}; true if it hurt it. */
	public static boolean testPunch(ServerPlayer player, LivingEntity target) {
		return HulkRampageAI.punch(player, target);
	}

	/** Tests: why a punch would not land ("reach", "wall" or "ok"). */
	public static String testPunchCheck(ServerPlayer player, LivingEntity target) {
		return !HulkRampageAI.inReach(player, target) ? "reach" : !HulkRampageAI.clearPath(player, target) ? "wall" : "ok";
	}
}
