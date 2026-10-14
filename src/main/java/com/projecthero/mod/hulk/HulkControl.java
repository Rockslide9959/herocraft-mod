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
import net.minecraft.util.Mth;
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
 * {@code rampageRange} blocks -- squad-mates and other players included -- always the nearest: sprints and leaps to it and
 * punches it, with a ground smash every third blow. He only breaks blocks when he is truly stuck. The player wins control back
 * with the ring minigame: a circle and a ring closing on it; Space as they meet, {@code ringHitsNeeded} times in a row (a miss
 * resets the streak). The client times the presses; the server only counts them, and never faster than {@code ringMinGapTicks}.
 * A rampage nobody wins back still ends after {@code rampageMaxTicks}.
 *
 * <p>The per-player rampage bookkeeping is a static world-object cache, cleared by {@code ServerStateReset} through
 * {@link Hulk#clearSessionState}.
 */
public final class HulkControl {
	/** Arrow directions as sent by the client (and drawn by the HUD): 1 up, 2 left, 3 down, 4 right. */
	public static final int ARROW_UP = 1;
	public static final int ARROW_LEFT = 2;
	public static final int ARROW_DOWN = 3;
	public static final int ARROW_RIGHT = 4;

	/** Rampage bookkeeping (server only): the current victim, the next blow, the next leap, the stuck check. */
	private static final class Rampage {
		final long startedAt;
		int targetId = -1;
		long nextSwing;
		long nextLeap;
		int blows;
		int stuckTicks;
		Vec3 lastPos;
		long airborneUntil;
		long lastRingAt = Long.MIN_VALUE / 2;

		Rampage(long startedAt) {
			this.startedAt = startedAt;
		}
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

	/** Client-safe: is the arrow-key warning showing? */
	public static boolean warning(HulkState s, long now) {
		return s.hulk && s.rules.warnLen > 0 && now <= s.rules.warnUntil && !s.rampaging(now);
	}

	/** The {@code i}-th arrow (1 up .. 4 right) of a packed warning string. */
	public static int arrowAt(int seq, int i) {
		return ((seq >> (2 * i)) & 3) + 1;
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
				n.rules.nextRampageAt = now + HulkConfig.control().rampageEveryTicks;
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
		Hulk.save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.hulk.warning").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				true);
	}

	// ---------------------------------------------------------------- tick

	/** Every tick for a Gamma player (from {@link Hulk#tick}). */
	static void tick(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		long now = player.level().getGameTime();
		if (!s.hulk || s.dying() || !HulkConfig.control().enabled) {
			if (s.combat.rampageUntil != 0L || s.rules.warnLen != 0 || s.rules.ringStreak != 0 || s.combat.promptKey != 0) {
				HulkState n = s.copy();
				n.combat.rampageUntil = 0L;
				n.combat.promptKey = 0;
				n.rules.warnLen = 0;
				n.rules.warnPos = 0;
				n.rules.ringStreak = 0;
				Hulk.save(player, n);
			}
			RAMPAGES.remove(player.getUUID());
			return;
		}
		if (s.combat.rampageUntil != 0L) {
			if (now >= s.combat.rampageUntil) {
				endRampage(player, false);
			} else {
				tickRampage(player, now);
			}
			return;
		}
		if (Hulk.changing(s, now)) {
			return;
		}
		if (s.rules.warnLen > 0) {
			if (now > s.rules.warnUntil) {
				startRampage(player); // too slow
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

	// ---------------------------------------------------------------- the rampage

	public static void startRampage(ServerPlayer player) {
		long now = player.level().getGameTime();
		HulkAbilities.endCharge(player);
		HulkGrab.release(player);
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.clear(player);
		player.ejectPassengers();
		HulkState n = Hulk.state(player).copy();
		n.combat.rampageUntil = now + Math.max(40, HulkConfig.control().rampageMaxTicks);
		n.combat.promptKey = 0;
		n.combat.control = 0.0f;
		n.combat.smashChargeStart = 0L;
		n.combat.strainStart = 0L;
		n.leapChargeStart = 0L;
		n.rules.warnLen = 0;
		n.rules.warnPos = 0;
		n.rules.ringStreak = 0;
		n.animId = HulkState.ANIM_RAMPAGE;
		n.animStart = now;
		Hulk.save(player, n);
		RAMPAGES.put(player.getUUID(), new Rampage(now));
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.5f, 0.8f);
		HulkCombat.shake(level, player.position(), 0.7f, 30, 32.0);
		player.displayClientMessage(Component.translatable("message.projecthero.hulk.rampage_start")
				.withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD), true);
	}

	/** {@code won}: the player won control back with the ring (otherwise the safety time ran out). */
	static void endRampage(ServerPlayer player, boolean won) {
		RAMPAGES.remove(player.getUUID());
		long now = player.level().getGameTime();
		HulkState n = Hulk.state(player).copy();
		n.combat.rampageUntil = 0L;
		n.combat.control = 100.0f;
		n.combat.lastDealtAt = now;
		n.rules.ringStreak = 0;
		n.rules.nextRampageAt = now + HulkConfig.control().rampageEveryTicks;
		if (n.animId == HulkState.ANIM_RAMPAGE) {
			n.animId = HulkState.ANIM_NONE;
		}
		Hulk.save(player, n);
		player.setSprinting(false);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.2f, 0.6f);
		player.displayClientMessage(Component.translatable(won ? "message.projecthero.hulk.rampage_end" : "message.projecthero.hulk.rampage_timeout")
				.withStyle(ChatFormatting.GREEN), true);
	}

	/**
	 * The client's verdict on one Space press of the ring minigame ({@code hit}: the ring was on the circle). The server only
	 * counts: a hit adds to the streak, a miss resets it, {@code ringHitsNeeded} in a row end the rampage. Reports closer
	 * together than {@code ringMinGapTicks} are ignored.
	 */
	public static void ringResult(ServerPlayer player, boolean hit) {
		HulkState s = Hulk.state(player);
		long now = player.level().getGameTime();
		if (!s.rampaging(now)) {
			return;
		}
		Rampage r = RAMPAGES.computeIfAbsent(player.getUUID(), k -> new Rampage(now));
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
				0.9f + 0.2f * n.rules.ringStreak);
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
	 * v0.16.4 (user): every creature is fair game -- players and squad-mates included -- except his own pets and what he can't
	 * get at (down in a cave under him: he would only dig).
	 */
	static boolean fairGame(ServerPlayer player, LivingEntity e) {
		if (e == player || !e.isAlive() || e.isSpectator() || e.distanceToSqr(player) > range() * range()) {
			return false;
		}
		if (e instanceof OwnableEntity pet && player.getUUID().equals(pet.getOwnerUUID())) {
			return false;
		}
		if (e.isPassenger() && e.getVehicle() == player) {
			return false;
		}
		return aboveGround(e) || Math.abs(e.getY() - player.getY()) < 6.0;
	}

	private static LivingEntity findTarget(ServerPlayer player) {
		double r = range();
		return HulkCombat.targets(player, player.getBoundingBox().inflate(r, 48.0, r)).stream()
				.filter(e -> fairGame(player, e))
				.min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
	}

	private static void face(ServerPlayer player, Vec3 dir) {
		float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0f;
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.yBodyRot = yaw;
	}

	private static void tickRampage(ServerPlayer player, long now) {
		Rampage r = RAMPAGES.computeIfAbsent(player.getUUID(), k -> new Rampage(now));
		ServerLevel level = (ServerLevel) player.level();
		HulkConfig.Control cfg = HulkConfig.control();
		player.setSprinting(false); // never the Sprint Smash -- he only breaks blocks when he is stuck
		if (now % 50L == 0L) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.5f, 0.6f);
		}
		if (now % 10L == 0L) {
			level.sendParticles(HulkAbilities.GAMMA, player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(), 4, 0.5,
					player.getBbHeight() * 0.3, 0.5, 0.02);
		}
		if (now - r.startedAt < cfg.rampageRoarTicks) {
			return; // he stands and roars first
		}
		LivingEntity target = r.targetId < 0 || !(level.getEntity(r.targetId) instanceof LivingEntity l) || !fairGame(player, l) ? null : l;
		if (target == null || now % 20L == 0L) {
			target = findTarget(player);
			r.targetId = target == null ? -1 : target.getId();
		}
		boolean airborne = !player.onGround() && now < r.airborneUntil;
		if (target == null) {
			// nothing alive in range: he stands and heaves, roaring now and then
			if (player.onGround()) {
				Vec3 v = player.getDeltaMovement();
				AbilityHelpers.launchSelf(player, new Vec3(v.x * 0.5, Math.min(v.y, 0.0), v.z * 0.5));
			}
			r.stuckTicks = 0;
			return;
		}
		Vec3 to = target.position().subtract(player.position());
		Vec3 flat = new Vec3(to.x, 0, to.z);
		double dist = flat.length();
		Vec3 dir = dist < 1.0e-3 ? HulkAbilities.flatLook(player) : flat.scale(1.0 / dist);
		face(player, dir);
		double reach = 2.2 + player.getBbWidth() * 0.5 + target.getBbWidth() * 0.5;
		boolean inReach = dist <= reach && Math.abs(to.y) <= player.getBbHeight();
		if (inReach && now >= r.nextSwing) {
			r.nextSwing = now + Math.max(4, cfg.rampageSwingTicks);
			r.blows++;
			if (r.blows % 3 == 0 && player.onGround()) {
				// every third blow: both fists into the ground
				HulkAbilities.anim(player, HulkState.ANIM_GROUND_SMASH);
				Vec3 at = target.position();
				HulkAbilities.schedule(player, 8, () -> {
					if (rampaging(player)) {
						HulkAbilities.shockwave(player, at, 4.5, 14.0f, 1.2, 0.6, 0.5f);
					}
				});
			} else {
				player.resetAttackStrengthTicker();
				player.attack(target);
				player.swing(com.projecthero.mod.hulk.gladiator.GladiatorSwing.next(player, net.minecraft.world.InteractionHand.MAIN_HAND), true);
			}
		}
		if (airborne) {
			return; // mid-leap: let the arc carry him
		}
		if (dist > 14.0 && player.onGround() && now >= r.nextLeap) {
			// too far to run: leap at it
			Vec3 aim = new Vec3(dir.x, Math.max(0.15, to.y / Math.max(1.0, dist)), dir.z);
			AbilityHelpers.launchSelf(player, AbilityHelpers.ballisticLaunch(aim, Math.min(dist - 3.0, 40.0), true));
			r.nextLeap = now + 50;
			r.airborneUntil = now + 60;
			r.stuckTicks = 0;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.5f, 0.6f);
			return;
		}
		if (inReach) {
			Vec3 v = player.getDeltaMovement();
			AbilityHelpers.launchSelf(player, new Vec3(v.x * 0.3, Math.min(v.y, 0.0), v.z * 0.3));
			r.stuckTicks = 0;
			return;
		}
		// run at it, faster the bigger he is
		double speed = 0.4 + 0.06 * Math.max(0.0, Hulk.scaleBonus(player));
		double vy = player.onGround() ? -0.05 : Math.max(player.getDeltaMovement().y - 0.08, -1.2);
		Vec3 here = player.position();
		if (r.lastPos != null && player.onGround()) {
			double moved = Math.sqrt(Math.pow(here.x - r.lastPos.x, 2) + Math.pow(here.z - r.lastPos.z, 2));
			r.stuckTicks = moved < speed * 0.25 ? r.stuckTicks + 1 : Math.max(0, r.stuckTicks - 2);
		}
		r.lastPos = here;
		if (r.stuckTicks >= 12 && player.onGround()) {
			if (r.stuckTicks < 30) {
				vy = 0.75; // a hop over it first
			} else {
				// truly stuck: smash through what is in the way
				HulkAbilities.breakAhead(player, dir, true);
				r.stuckTicks = 12;
			}
		}
		AbilityHelpers.launchSelf(player, new Vec3(dir.x * speed, vy, dir.z * speed));
	}
}
