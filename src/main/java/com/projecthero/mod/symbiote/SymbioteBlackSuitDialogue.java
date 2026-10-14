package com.projecthero.mod.symbiote;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.spider.SpiderClimb;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderPerfectDodge;
import com.projecthero.mod.spider.SpiderStealth;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.SpiderTakedown;
import com.projecthero.mod.spider.SpiderWrap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): the symbiote's voice when its host is Spider-Man (the Black Suit). The Normal host's
 * {@link SymbioteDialogue} talks about Biomass, tendrils and shields every few seconds; this one talks about
 * what Spider-Man actually does -- swinging, walls and ceilings, webs and cocoons, Spider-Sense, takedowns, and
 * the Hunger meter -- and it talks <em>much</em> less:
 *
 * <ul>
 * <li>Every situation is <b>edge-triggered</b> (it can speak when the situation starts, never again while it
 * lasts), and most edges only speak on a chance roll.</li>
 * <li><b>Urgent</b> (near death, fire / lava, Overdrive, Take Over, the crash, the revive, a sound attack): always
 * speaks, at least 10 s after the last line.</li>
 * <li><b>Moment</b> (swinging fast or high, a cocoon, a Spider-Sense warning, prey below a hang, a takedown, a kill
 * streak, a boss, the meter tiers, a heavy wrap): 35% per edge, at least 60 s after the last line.</li>
 * <li><b>Idle</b> (patrol musing, the suit taken off): 2-3 minutes after the last line, 25% per check.</li>
 * </ul>
 * Lines the action bar already shows a status for (Overdrive, Take Over, crash) are held back a couple of seconds
 * so the voice does not overwrite the status.
 */
final class SymbioteBlackSuitDialogue {
	private static final int EVAL_INTERVAL = 10;

	private enum Tier {
		URGENT(200, 1.0f), MOMENT(1200, 0.35f), IDLE(2400, 0.25f);

		final int gapTicks;
		final float chance;

		Tier(int gapTicks, float chance) {
			this.gapTicks = gapTicks;
			this.chance = chance;
		}
	}

	/** Extra random wait on top of {@link Tier#IDLE}'s gap, so the musing never lands on a schedule. */
	private static final int IDLE_JITTER = 1200;
	/** Status lines (Overdrive / Take Over / crash) hold the action bar this long before the voice speaks. */
	private static final long STATUS_DELAY = 40L;

	// situation bits, edge-detected against the previous evaluation
	private static final int F_SWING_FAST = 1;
	private static final int F_SWING_HIGH = 1 << 1;
	private static final int F_WALL = 1 << 2;
	private static final int F_PREY_BELOW = 1 << 3;
	private static final int F_HIDDEN_HUNT = 1 << 4;
	private static final int F_SENSE = 1 << 5;
	private static final int F_TAKEDOWN = 1 << 6;
	private static final int F_BOSS = 1 << 7;
	private static final int F_HP_LOW = 1 << 8;
	private static final int F_HP_DYING = 1 << 9;
	private static final int F_FIRE = 1 << 10;
	private static final int F_LAVA = 1 << 11;
	private static final int F_STARVED_FIGHT = 1 << 12;
	private static final int F_FULL = 1 << 13;
	private static final int F_FOOD_LOW = 1 << 14;
	private static final int F_CRASH = 1 << 15;

	private static final String[] SWING_FAST = {"bs_swing_fast_1", "bs_swing_fast_2", "bs_swing_fast_3"};
	private static final String[] SWING_HIGH = {"bs_swing_high_1", "bs_swing_high_2", "bs_swing_high_3"};
	private static final String[] WALL = {"bs_wall_1", "bs_wall_2"};
	private static final String[] PREY_BELOW = {"bs_prey_below_1", "bs_prey_below_2", "bs_prey_below_3"};
	private static final String[] HIDDEN_HUNT = {"bs_hidden_1", "bs_hidden_2"};
	private static final String[] SENSE = {"bs_sense_1", "bs_sense_2", "bs_sense_3"};
	private static final String[] COCOON = {"bs_cocoon_1", "bs_cocoon_2", "bs_cocoon_3"};
	private static final String[] TAKEDOWN = {"bs_takedown_1", "bs_takedown_2"};
	private static final String[] STREAK = {"bs_streak_1", "bs_streak_2"};
	private static final String[] BOSS = {"bs_boss_1", "bs_boss_2"};
	private static final String[] HP_LOW = {"bs_hp_low_1", "bs_hp_low_2"};
	private static final String[] HP_DYING = {"bs_hp_dying_1", "bs_hp_dying_2"};
	private static final String[] FIRE = {"bs_fire_1", "bs_fire_2"};
	private static final String[] LAVA = {"bs_lava"};
	private static final String[] STARVED = {"bs_starved_1", "bs_starved_2", "bs_starved_3"};
	private static final String[] BONDED = {"bs_bonded_1", "bs_bonded_2"};
	private static final String[] OVERDRIVE = {"bs_overdrive_1", "bs_overdrive_2", "bs_overdrive_3"};
	private static final String[] FULL = {"bs_full_1", "bs_full_2"};
	private static final String[] FOOD_LOW = {"bs_food_low"};
	private static final String[] TAKEOVER = {"bs_takeover_1", "bs_takeover_2"};
	private static final String[] CRASH = {"bs_crash_1", "bs_crash_2"};
	private static final String[] PATROL = {
			"bs_patrol_1", "bs_patrol_2", "bs_patrol_3", "bs_patrol_4", "bs_patrol_5", "bs_patrol_6"
	};
	private static final String[] SUIT_OFF = {"bs_suit_off_1", "bs_suit_off_2", "bs_suit_off_3"};
	private static final String[] BONDING = {"bs_bonding_1", "bs_bonding_2", "bs_bonding_3"};

	private static final Map<Integer, Long> LAST_SPOKEN = new ConcurrentHashMap<>();
	private static final Map<Integer, String> LAST_ID = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> NEXT_IDLE = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> PREV_FLAGS = new ConcurrentHashMap<>();
	private static final Map<Integer, SymbioteHunger.Tier> PREV_TIER = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> PREV_WRAPPED = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> PREV_KILLS = new ConcurrentHashMap<>();
	private static final Map<Integer, long[]> KILL_WINDOW = new ConcurrentHashMap<>(); // {count, windowStart}
	private static final Map<Integer, Vec3> PREV_POS = new ConcurrentHashMap<>();
	private static final Map<Integer, Pending> PENDING = new ConcurrentHashMap<>();

	private record Pending(String id, long due) {
	}

	private SymbioteBlackSuitDialogue() {
	}

	static void tick(ServerPlayer player) {
		if (player.tickCount % EVAL_INTERVAL != 0 || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		int pid = player.getId();
		long now = level.getGameTime();

		Pending p = PENDING.get(pid);
		if (p != null && now >= p.due) {
			PENDING.remove(pid);
			speakNow(player, p.id, now);
			return;
		}

		if (SymbioteVitalsManager.bonding(player)) {
			attempt(player, BONDING, Tier.MOMENT, now, true);
			return;
		}

		boolean suited = SymbioteHunger.blackSuit(player);
		SymbioteHunger.Tier tier = suited ? SymbioteHunger.tier(player) : null;
		SymbioteHunger.Tier prevTier = tier == null ? PREV_TIER.remove(pid) : PREV_TIER.put(pid, tier);
		boolean crashing = suited && SymbioteHunger.crashing(player);

		// ---- gather (cheap: a 16-block hostile scan twice a second) ----
		float hp = player.getMaxHealth() > 0 ? player.getHealth() / player.getMaxHealth() : 1.0f;
		Vec3 pos = player.position();
		Vec3 prevPos = PREV_POS.put(pid, pos);
		double speed = prevPos == null ? 0.0 : pos.distanceTo(prevPos) / EVAL_INTERVAL; // blocks per tick
		boolean swinging = SpiderSwing.isSwinging(player);
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
		boolean hanging = SpiderMoves.hangingUpsideDown(player) || SpiderClimb.onCeiling(player);

		List<Monster> hostiles = level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(16.0),
				m -> m.isAlive() && m.distanceToSqr(player) <= 16.0 * 16.0);
		boolean aware = false;
		boolean preyBelow = false;
		boolean boss = false;
		int wrapped = 0;
		for (Monster m : hostiles) {
			if (m.getTarget() == player) {
				aware = true;
			}
			if (SpiderWrap.isWrapped(m)) {
				wrapped++;
			}
			if (m.getMaxHealth() >= 80.0f) {
				boss = true;
			}
			if (hanging && player.getY() - m.getY() > 2.0 && m.distanceToSqr(player) <= 9.0 * 9.0
					&& m instanceof Mob mob && SpiderTakedown.unaware(mob, player)) {
				preyBelow = true;
			}
		}
		boolean fighting = aware || player.tickCount - player.getLastHurtByMobTimestamp() < 100;

		int flags = 0;
		if (swinging && speed > 1.2) {
			flags |= F_SWING_FAST;
		}
		if (swinging && player.getY() - ground > 30.0) {
			flags |= F_SWING_HIGH;
		}
		if (SpiderClimb.attachedToWall(player) && !swinging) {
			flags |= F_WALL;
		}
		if (preyBelow) {
			flags |= F_PREY_BELOW;
		}
		if (!hanging && !hostiles.isEmpty() && !aware && SpiderStealth.level(player) == SpiderStealth.HIDDEN) {
			flags |= F_HIDDEN_HUNT;
		}
		if (SpiderPerfectDodge.warned(player)) {
			flags |= F_SENSE;
		}
		if (SpiderTakedown.snatching(player)) {
			flags |= F_TAKEDOWN;
		}
		if (boss) {
			flags |= F_BOSS;
		}
		if (hp <= 0.30f) {
			flags |= F_HP_LOW;
		}
		if (hp <= 0.12f) {
			flags |= F_HP_DYING;
		}
		if (player.isOnFire() && !player.fireImmune()) {
			flags |= F_FIRE;
		}
		if (player.isInLava()) {
			flags |= F_LAVA;
		}
		if (suited && tier == SymbioteHunger.Tier.RESTRAINED && fighting && !crashing) {
			flags |= F_STARVED_FIGHT;
		}
		if (suited && tier != SymbioteHunger.Tier.TAKEOVER && !crashing && SymbioteHunger.get(player) >= SymbioteHunger.MAX) {
			flags |= F_FULL;
		}
		if (tier == SymbioteHunger.Tier.OVERDRIVE && player.getFoodData().getFoodLevel() <= 6) {
			flags |= F_FOOD_LOW;
		}
		if (crashing) {
			flags |= F_CRASH;
		}
		Integer prevBox = PREV_FLAGS.put(pid, flags);
		int rising = prevBox == null ? 0 : flags & ~prevBox; // nothing "starts" on the very first look

		int prevWrapped = PREV_WRAPPED.getOrDefault(pid, wrapped);
		PREV_WRAPPED.put(pid, wrapped);
		boolean newCocoon = wrapped > prevWrapped;

		int kills = player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
		int prevKills = PREV_KILLS.getOrDefault(pid, kills);
		PREV_KILLS.put(pid, kills);
		boolean streakStarts = false;
		if (kills > prevKills) {
			long[] win = KILL_WINDOW.computeIfAbsent(pid, k -> new long[]{0L, now});
			if (now - win[1] > 120L) {
				win[0] = 0L;
				win[1] = now;
			}
			long before = win[0];
			win[0] += kills - prevKills;
			streakStarts = before < 3 && win[0] >= 3;
		}

		// ---- urgent: the moments that matter (always speak, 10 s gap) ----
		if ((rising & F_LAVA) != 0 && attempt(player, LAVA, Tier.URGENT, now, true)) {
			return;
		}
		if ((rising & F_HP_DYING) != 0 && attempt(player, HP_DYING, Tier.URGENT, now, true)) {
			return;
		}
		if ((rising & F_FIRE) != 0 && attempt(player, FIRE, Tier.URGENT, now, true)) {
			return;
		}
		if (tier != prevTier && prevTier != null && tier != null) {
			if (tier == SymbioteHunger.Tier.TAKEOVER) {
				queue(player, TAKEOVER, now);
				return;
			}
			if (tier == SymbioteHunger.Tier.OVERDRIVE && prevTier.ordinal() < tier.ordinal()) {
				queue(player, OVERDRIVE, now);
				return;
			}
		}
		if ((rising & F_CRASH) != 0) {
			queue(player, CRASH, now);
			return;
		}

		// ---- moments: one chance when the situation starts (35%, 60 s gap) ----
		if ((rising & F_HP_LOW) != 0 && attempt(player, HP_LOW, Tier.MOMENT, now, false)) {
			return;
		}
		if ((rising & F_SENSE) != 0 && attempt(player, SENSE, Tier.MOMENT, now, false)) {
			return;
		}
		if ((rising & F_BOSS) != 0 && attempt(player, BOSS, Tier.MOMENT, now, false)) {
			return;
		}
		if ((rising & F_TAKEDOWN) != 0 && attempt(player, TAKEDOWN, Tier.MOMENT, now, false)) {
			return;
		}
		if (newCocoon && attempt(player, COCOON, Tier.MOMENT, now, false)) {
			return;
		}
		if (streakStarts && attempt(player, STREAK, Tier.MOMENT, now, false)) {
			return;
		}
		if ((rising & F_PREY_BELOW) != 0 && attempt(player, PREY_BELOW, Tier.MOMENT, now, false)) {
			return;
		}
		if ((rising & F_HIDDEN_HUNT) != 0 && attempt(player, HIDDEN_HUNT, Tier.MOMENT, now, false)) {
			return;
		}
		if ((rising & F_FULL) != 0 && attempt(player, FULL, Tier.MOMENT, now, false)) {
			return;
		}
		if (tier == SymbioteHunger.Tier.BONDED && prevTier == SymbioteHunger.Tier.RESTRAINED
				&& attempt(player, BONDED, Tier.MOMENT, now, false)) {
			return;
		}
		if ((rising & F_STARVED_FIGHT) != 0 && attempt(player, STARVED, Tier.MOMENT, now, false)) {
			return;
		}
		if ((rising & F_FOOD_LOW) != 0 && attempt(player, FOOD_LOW, Tier.MOMENT, now, false)) {
			return;
		}
		if ((rising & F_SWING_HIGH) != 0 && attempt(player, SWING_HIGH, Tier.MOMENT, now, false)) {
			return;
		}
		if ((rising & F_SWING_FAST) != 0 && attempt(player, SWING_FAST, Tier.MOMENT, now, false)) {
			return;
		}
		if ((rising & F_WALL) != 0 && attempt(player, WALL, Tier.MOMENT, now, false)) {
			return;
		}

		// ---- idle: a rare musing while nothing is happening ----
		// the first musing waits a full idle gap after the symbiote starts listening
		long nextIdle = NEXT_IDLE.computeIfAbsent(pid, k -> now + Tier.IDLE.gapTicks);
		if (!fighting && hostiles.isEmpty() && now >= nextIdle) {
			attempt(player, suited ? PATROL : SUIT_OFF, Tier.IDLE, now, false);
		}
	}

	/**
	 * An event the symbiote reacts to directly ({@link SymbioteDialogue#say}): the auto-wrap, the revive, a sound
	 * attack. The Normal host's ids map onto Black Suit lines, and only the life-or-death ones skip the throttle.
	 */
	static void event(ServerPlayer player, String id) {
		long now = player.level().getGameTime();
		switch (id) {
			case "resurrect" -> speakNow(player, "bs_resurrect", now);
			case "equip_fatal" -> speakNow(player, "bs_wrap_fatal", now);
			case "sonic_stunned", "sonic_stunned_bare" -> attempt(player, new String[]{"bs_sonic"}, Tier.URGENT, now, true);
			case "equip_impact" -> attempt(player, new String[]{"bs_wrap_impact"}, Tier.MOMENT, now, true);
			case "equip_low" -> attempt(player, new String[]{"bs_wrap_low"}, Tier.MOMENT, now, true);
			case "sonic_recovered" -> attempt(player, new String[]{"bs_sonic_recovered"}, Tier.MOMENT, now, false);
			default -> {
				// anything else (another host's lines) stays quiet on the Black Suit
			}
		}
	}

	/** Speak one of {@code ids} if the tier's gap has passed and (unless {@code certain}) its chance rolls. */
	private static boolean attempt(ServerPlayer player, String[] ids, Tier tier, long now, boolean certain) {
		Long last = LAST_SPOKEN.get(player.getId());
		if (last != null && now - last < tier.gapTicks) {
			return false;
		}
		if (!certain && player.getRandom().nextFloat() >= tier.chance) {
			return false;
		}
		speakNow(player, pick(player, ids), now);
		return true;
	}

	/** An urgent line that waits for the action bar's status message to be read first. */
	private static void queue(ServerPlayer player, String[] ids, long now) {
		Long last = LAST_SPOKEN.get(player.getId());
		if (last != null && now - last < Tier.URGENT.gapTicks) {
			return;
		}
		PENDING.put(player.getId(), new Pending(pick(player, ids), now + STATUS_DELAY));
	}

	private static String pick(ServerPlayer player, String[] ids) {
		String id = ids[player.getRandom().nextInt(ids.length)];
		if (ids.length > 1 && id.equals(LAST_ID.get(player.getId()))) {
			id = ids[(java.util.Arrays.asList(ids).indexOf(id) + 1) % ids.length];
		}
		return id;
	}

	private static void speakNow(ServerPlayer player, String id, long now) {
		SymbioteDialogue.speak(player, id);
		LAST_SPOKEN.put(player.getId(), now);
		LAST_ID.put(player.getId(), id);
		NEXT_IDLE.put(player.getId(), now + Tier.IDLE.gapTicks + player.getRandom().nextInt(IDLE_JITTER));
	}

	// ---------------- lifecycle ----------------

	static void clearFor(ServerPlayer player) {
		int id = player.getId();
		LAST_SPOKEN.remove(id);
		LAST_ID.remove(id);
		NEXT_IDLE.remove(id);
		PREV_FLAGS.remove(id);
		PREV_TIER.remove(id);
		PREV_WRAPPED.remove(id);
		PREV_KILLS.remove(id);
		KILL_WINDOW.remove(id);
		PREV_POS.remove(id);
		PENDING.remove(id);
	}

	static void clearSessionState() {
		LAST_SPOKEN.clear();
		LAST_ID.clear();
		NEXT_IDLE.clear();
		PREV_FLAGS.clear();
		PREV_TIER.clear();
		PREV_WRAPPED.clear();
		PREV_KILLS.clear();
		KILL_WINDOW.clear();
		PREV_POS.clear();
		PENDING.clear();
	}
}
