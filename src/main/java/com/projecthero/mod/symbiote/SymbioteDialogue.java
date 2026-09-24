package com.projecthero.mod.symbiote;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.phys.Vec3;

/**
 * The Symbiote's context-aware voice. Rather than firing generic status lines, the bonded organism
 * watches the world -- the host's health, its own Biomass, hunger, nearby enemies and bosses,
 * incoming fire, hazards, terrain, whether the host is winning or losing, whether it is even in a
 * fight at all -- and, every few seconds, says the single most relevant thing.
 *
 * <p>Rules are checked in strict priority order (emergencies first) and the first match wins. Each
 * priority tier has its own minimum gap; a higher tier may interrupt a lower one sooner, and the same
 * line never repeats back-to-back. This layers on top of {@link SymbioteVitalsManager}'s short
 * {@code warn_1..4} action-bar lines rather than replacing them.
 *
 * <p>v0.10.2: heavily expanded from the v0.9.24 skeleton -- it now covers the full situation table
 * (health tiers, retreat/escape reads, ability suggestions, terrain, water/drowning, hunger,
 * inventory, stealth, threat sense, kill streaks) and talks far more often, including a rotating
 * ambient patrol commentary while nothing is wrong.
 */
public final class SymbioteDialogue {
	private static final int EVAL_INTERVAL = 10; // twice a second

	private enum Tier {
		EMERGENCY(25), CRITICAL(45), COMBAT(55), STATE(70), AMBIENT(120);

		final int gapTicks;

		Tier(int gapTicks) {
			this.gapTicks = gapTicks;
		}
	}

	private record Line(String id, Tier tier) {
	}

	private static final Map<Integer, Long> LAST_SPOKEN = new ConcurrentHashMap<>();
	private static final Map<Integer, String> LAST_ID = new ConcurrentHashMap<>();
	private static final Map<Integer, Float> PREV_BIOMASS = new ConcurrentHashMap<>();
	private static final Map<Integer, Float> PREV_HP = new ConcurrentHashMap<>();
	private static final Map<Integer, Boolean> PREV_REGEN = new ConcurrentHashMap<>();
	private static final Map<Integer, Boolean> PREV_COMBAT = new ConcurrentHashMap<>();
	private static final Map<Integer, Boolean> PREV_BOSS = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> PREV_KILLS = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> LAST_KILL_TICK = new ConcurrentHashMap<>();
	private static final Map<Integer, long[]> KILL_WINDOW = new ConcurrentHashMap<>(); // {count, windowStartTick}
	private static final long KILL_WINDOW_TICKS = 120L;
	private static final Map<Integer, Integer> AMBIENT_ROTATION = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> BOND_ROTATION = new ConcurrentHashMap<>();

	/** What the Symbiote says while it is still spreading through a new host (the settling phase). */
	private static final String[] BONDING_LINES = {
			"bond_hold_still", "bond_do_not_fight", "bond_almost_part_of_you", "bond_your_pain_is_ours",
			"bond_we_will_protect_you", "bond_stronger_together", "bond_breathe", "bond_nearly_whole"
	};

	/** Rotating "nothing is wrong" patrol lines -- keeps the Symbiote present during exploration. */
	private static final String[] AMBIENT_PATROL = {
			"patrol_watching", "patrol_hunt", "patrol_stronger", "patrol_quiet_ours",
			"patrol_where_next", "patrol_together", "patrol_listen", "patrol_content"
	};

	private SymbioteDialogue() {
	}

	public static void tick(ServerPlayer player) {
		if (!Symbiote.hasSymbiote(player)) {
			clearFor(player);
			return;
		}
		if (player.tickCount % EVAL_INTERVAL != 0 || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Signals s = gather(player, level, now);

		Line pick = choose(player, s, now);

		// Remember this frame's values for the next edge-detected rule.
		PREV_BIOMASS.put(player.getId(), s.biomassFrac);
		PREV_HP.put(player.getId(), s.hpFrac);
		PREV_REGEN.put(player.getId(), s.regenerating);
		PREV_COMBAT.put(player.getId(), s.inCombat);
		PREV_BOSS.put(player.getId(), s.bossNear);

		if (pick == null) {
			return;
		}
		// NOTE: a real bug lived here for two versions -- the sentinel was Long.MIN_VALUE and
		// `now - last` overflowed to a huge negative, which is always < gapTicks, so the Symbiote
		// never spoke a single line. `spoken` tells "never" apart from "recently".
		Long lastBox = LAST_SPOKEN.get(player.getId());
		boolean spoken = lastBox != null;
		long last = spoken ? lastBox : 0L;
		if (spoken && now - last < pick.tier.gapTicks) {
			return;
		}
		if (spoken && pick.id.equals(LAST_ID.get(player.getId())) && now - last < pick.tier.gapTicks * 3L) {
			return;
		}
		speak(player, pick.id);
		LAST_SPOKEN.put(player.getId(), now);
		LAST_ID.put(player.getId(), pick.id);
	}

	/**
	 * Speak a specific line right now (an event the Symbiote must react to -- auto-equip, a sound attack, a
	 * resurrection), bypassing the ambient gap timers but stamping them so the patrol chatter does not
	 * immediately talk over it.
	 */
	public static void say(ServerPlayer player, String id) {
		speak(player, id);
		LAST_SPOKEN.put(player.getId(), player.level().getGameTime());
		LAST_ID.put(player.getId(), id);
	}

	/** v0.11.15: the Symbiote's voice shows above the hotbar (action bar) instead of filling the chat. */
	private static void speak(ServerPlayer player, String id) {
		player.displayClientMessage(Component.literal("“")
				.append(Component.translatable("message.projecthero.symbiote.talk." + id))
				.append(Component.literal("”"))
				.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
	}

	// ---------------- signal gathering ----------------

	private static final class Signals {
		float hpFrac;
		float biomassFrac;
		boolean broken;
		int hunger;
		boolean onFire;
		boolean inLava;
		boolean falling;
		boolean bigFallIncoming;
		boolean inWater;
		boolean drowning;
		boolean spiderMan;
		boolean bonding;
		boolean sleeping;
		boolean crouchedHidden;
		boolean crouched;
		boolean inCombat;
		boolean regenerating;
		boolean regenInterrupted;
		boolean incomingProjectile;
		boolean creeperClose;
		boolean enemyBehind;
		boolean enemyAbove;
		boolean surrounded;
		boolean retreating;
		boolean tookBigHit;
		int enemyCount;
		int prevEnemyCount;
		float nearestEnemyHpFrac;
		double nearestEnemyDist;
		boolean bossNear;
		float bossHpFrac;
		boolean enemiesUnaware;
		boolean loneUnawareEnemy;
		boolean recentKill;
		boolean killStreak;
		boolean weaponNearlyBroken;
		boolean noWeapon;
		boolean hasHealingItem;
		boolean hasGoldenApple;
		boolean shieldReady;
		boolean grappleReady;
		boolean leapReady;
		boolean onslaughtReady;
		long idleTicks;
		Float prevBiomass;
		Float prevHp;
		boolean prevRegen;
		boolean prevCombat;
		boolean prevBoss;
	}

	private static final Map<Integer, Integer> PREV_ENEMY_COUNT = new ConcurrentHashMap<>();

	private static Signals gather(ServerPlayer player, ServerLevel level, long now) {
		Signals s = new Signals();
		s.hpFrac = player.getMaxHealth() > 0 ? player.getHealth() / player.getMaxHealth() : 1.0f;
		s.biomassFrac = SymbioteVitalsManager.biomassFraction(player);
		s.broken = !SymbioteVitalsManager.usable(player);
		s.hunger = player.getFoodData().getFoodLevel();
		s.onFire = player.isOnFire() && !player.fireImmune();
		s.inLava = player.isInLava();
		s.falling = !player.onGround() && player.getDeltaMovement().y < -0.5 && player.fallDistance > 3.0f;
		s.bigFallIncoming = !player.onGround() && player.getDeltaMovement().y < -0.6 && player.fallDistance > 6.0f;
		s.inWater = player.isInWater();
		s.drowning = player.getAirSupply() < 60 && player.getAirSupply() < player.getMaxAirSupply();
		s.spiderMan = SymbioteHostType.of(player) == SymbioteHostType.SPIDER_MAN;
		s.bonding = SymbioteVitalsManager.bonding(player);
		s.sleeping = player.isSleeping();
		s.crouched = player.isShiftKeyDown();
		s.inCombat = SymbioteVitalsManager.ticksSinceCombat(player, now) < 80L;
		s.regenerating = SymbioteVitalsManager.regenerating(player, now);
		s.tookBigHit = SymbioteVitalsManager.tookBigHitRecently(player, now);
		s.prevBiomass = PREV_BIOMASS.get(player.getId());
		s.prevHp = PREV_HP.get(player.getId());
		s.prevRegen = PREV_REGEN.getOrDefault(player.getId(), false);
		s.prevCombat = PREV_COMBAT.getOrDefault(player.getId(), false);
		s.prevBoss = PREV_BOSS.getOrDefault(player.getId(), false);
		s.regenInterrupted = s.prevRegen && !s.regenerating && s.inCombat && s.biomassFrac < 0.99f;

		s.shieldReady = SymbioteAbilityManager.shieldReady(player);
		s.grappleReady = SymbioteAbilityManager.grappleReady(player, now);
		s.leapReady = SymbioteAbilityManager.leapReady(player, now);
		s.onslaughtReady = SymbioteAbilityManager.onslaughtReady(player, now);

		// Held weapon condition.
		ItemStack main = player.getMainHandItem();
		if (main.isEmpty()) {
			s.noWeapon = true;
		} else if (main.isDamageableItem() && main.getMaxDamage() > 0) {
			s.weaponNearlyBroken = main.getDamageValue() >= main.getMaxDamage() * 0.9;
		}

		// Inventory scan for emergency consumables (cheap: 36 slots, only when it might matter).
		if (s.hpFrac < 0.5f) {
			for (ItemStack st : player.getInventory().items) {
				if (st.isEmpty()) {
					continue;
				}
				if (st.is(Items.ENCHANTED_GOLDEN_APPLE) || st.is(Items.GOLDEN_APPLE)) {
					s.hasGoldenApple = true;
				}
				if (st.getItem() instanceof PotionItem || st.is(Items.HONEY_BOTTLE)) {
					s.hasHealingItem = true;
				}
			}
		}

		Vec3 look = player.getLookAngle();

		List<Monster> hostiles = level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(20.0),
				m -> m.isAlive() && m.distanceToSqr(player) <= 20.0 * 20.0);
		s.enemyCount = hostiles.size();
		s.prevEnemyCount = PREV_ENEMY_COUNT.getOrDefault(player.getId(), 0);
		PREV_ENEMY_COUNT.put(player.getId(), s.enemyCount);
		s.nearestEnemyHpFrac = 1.0f;
		s.nearestEnemyDist = Double.MAX_VALUE;
		double nearest = Double.MAX_VALUE;
		boolean[] quad = new boolean[4];
		boolean anyAware = false;
		Monster nearestMob = null;
		for (Monster m : hostiles) {
			double d = m.distanceToSqr(player);
			if (d < nearest) {
				nearest = d;
				nearestMob = m;
				s.nearestEnemyHpFrac = m.getMaxHealth() > 0 ? m.getHealth() / m.getMaxHealth() : 1.0f;
				s.nearestEnemyDist = Math.sqrt(d);
			}
			if (m instanceof Creeper && d <= 6.0 * 6.0) {
				s.creeperClose = true;
			}
			if (m.getY() - player.getY() > 2.5 && d <= 14.0 * 14.0) {
				s.enemyAbove = true;
			}
			Vec3 to = m.position().subtract(player.position());
			if (to.lengthSqr() > 0.01) {
				Vec3 n = to.normalize();
				if (d <= 8.0 * 8.0 && n.dot(look) < -0.15) {
					s.enemyBehind = true;
				}
				int q = (n.x >= 0 ? 1 : 0) + (n.z >= 0 ? 2 : 0);
				if (d <= 9.0 * 9.0) {
					quad[q] = true;
				}
			}
			if (m.getMaxHealth() >= 80.0f && d <= 40.0 * 40.0) {
				s.bossNear = true;
				s.bossHpFrac = m.getMaxHealth() > 0 ? m.getHealth() / m.getMaxHealth() : 1.0f;
			}
			if (m instanceof Mob mob && mob.getTarget() == player) {
				anyAware = true;
			}
		}
		int quadrants = 0;
		for (boolean b : quad) {
			if (b) {
				quadrants++;
			}
		}
		s.surrounded = quadrants >= 3 || (s.enemyCount >= 4 && nearest <= 6.0 * 6.0);
		s.enemiesUnaware = s.enemyCount > 0 && !anyAware;
		s.loneUnawareEnemy = s.enemyCount == 1 && !anyAware;
		s.crouchedHidden = player.isShiftKeyDown()
				&& player.hasEffect(MobEffects.INVISIBILITY);

		// Retreating: actively moving away from the nearest enemy while it is aware of us.
		if (nearestMob != null && anyAware) {
			Vec3 toEnemy = nearestMob.position().subtract(player.position());
			Vec3 vel = player.getDeltaMovement();
			if (toEnemy.lengthSqr() > 0.01 && vel.horizontalDistanceSqr() > 0.02
					&& new Vec3(vel.x, 0, vel.z).normalize().dot(new Vec3(toEnemy.x, 0, toEnemy.z).normalize()) < -0.3) {
				s.retreating = true;
			}
		}

		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(12.0))) {
			if (proj.getOwner() == player || proj.getDeltaMovement().lengthSqr() < 0.05) {
				continue;
			}
			Vec3 toMe = player.position().add(0, 1, 0).subtract(proj.position());
			if (toMe.lengthSqr() > 0.01 && proj.getDeltaMovement().normalize().dot(toMe.normalize()) > 0.6) {
				s.incomingProjectile = true;
				break;
			}
		}

		// Kills, from the vanilla mob-kill stat -- gives "recent kill" and "kill streak" without a hook.
		int kills = player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
		int prevKills = PREV_KILLS.getOrDefault(player.getId(), kills);
		PREV_KILLS.put(player.getId(), kills);
		if (kills > prevKills) {
			LAST_KILL_TICK.put(player.getId(), now);
			long[] win = KILL_WINDOW.computeIfAbsent(player.getId(), k -> new long[]{0L, now});
			if (now - win[1] > KILL_WINDOW_TICKS) {
				win[0] = 0L;
				win[1] = now;
			}
			win[0] += kills - prevKills;
		}
		Long lastKill = LAST_KILL_TICK.get(player.getId());
		s.recentKill = lastKill != null && now - lastKill < 40L;
		long[] win = KILL_WINDOW.get(player.getId());
		s.killStreak = win != null && win[0] >= 3 && now - win[1] <= KILL_WINDOW_TICKS;

		s.idleTicks = SymbioteVitalsManager.ticksSinceCombat(player, now);
		return s;
	}

	// ---------------- rule priority ----------------

	private static Line choose(ServerPlayer player, Signals s, long now) {
		// ----- BONDING (a fresh wild host, still being taken over) -----
		if (s.bonding) {
			int i = BOND_ROTATION.merge(player.getId(), 1, Integer::sum);
			return new Line(BONDING_LINES[Math.floorMod(i, BONDING_LINES.length)], Tier.COMBAT);
		}

		// ----- EMERGENCY -----
		if (s.inLava) {
			return new Line(s.biomassFrac < 0.35f ? "lava_get_out" : "lava_no", Tier.EMERGENCY);
		}
		if (s.onFire && s.hpFrac < 0.25f) {
			return new Line("fire_dying", Tier.EMERGENCY);
		}
		if (s.onFire && s.inWater) {
			return new Line("fire_out_now", Tier.EMERGENCY);
		}
		if (s.onFire) {
			return new Line("fire", Tier.EMERGENCY);
		}
		if (s.hpFrac < 0.15f && s.biomassFrac < 0.15f) {
			return new Line("both_dying", Tier.EMERGENCY);
		}
		if (s.hpFrac <= 0.12f) {
			return new Line("host_death_imminent", Tier.EMERGENCY);
		}
		if (s.bigFallIncoming && s.grappleReady) {
			return new Line("big_fall_grapple", Tier.EMERGENCY);
		}
		if (s.falling && s.biomassFrac < 0.25f) {
			return new Line("falling_low_biomass", Tier.EMERGENCY);
		}

		// ----- CRITICAL -----
		if (s.biomassFrac <= 0.05f) {
			return new Line("biomass_dying", Tier.CRITICAL);
		}
		if (s.biomassFrac <= 0.10f) {
			return new Line(s.inCombat ? "biomass_critical_fight" : "biomass_critical", Tier.CRITICAL);
		}
		if (s.broken) {
			return new Line("form_collapsing", Tier.CRITICAL);
		}
		if (s.hpFrac < 0.15f) {
			return new Line("host_critical", Tier.CRITICAL);
		}
		if (s.surrounded && s.biomassFrac < 0.35f && s.onslaughtReady) {
			return new Line("surrounded_onslaught", Tier.CRITICAL);
		}
		if (s.surrounded && s.biomassFrac < 0.35f) {
			return new Line("surrounded_break_through", Tier.CRITICAL);
		}
		if (s.biomassFrac < 0.30f && s.retreating) {
			return new Line("keep_moving", Tier.CRITICAL);
		}
		if (s.biomassFrac < 0.25f && s.leapReady && s.grappleReady) {
			return new Line("leap_grapple_escape", Tier.CRITICAL);
		}
		if (s.biomassFrac < 0.25f && s.grappleReady && s.inCombat) {
			return new Line("grapple_away", Tier.CRITICAL);
		}
		if (s.tookBigHit) {
			return new Line("big_hit", Tier.CRITICAL);
		}
		if (s.biomassFrac < 0.30f && s.hpFrac > 0.75f) {
			return new Line("you_are_fine_we_are_not", Tier.CRITICAL);
		}
		if (s.biomassFrac > 0.60f && s.hpFrac < 0.35f) {
			return new Line("we_can_protect_you", Tier.CRITICAL);
		}
		if (s.hpFrac < 0.35f && s.hasGoldenApple) {
			return new Line("use_golden_apple", Tier.CRITICAL);
		}
		if (s.hpFrac < 0.4f && s.hasHealingItem) {
			return new Line("use_healing", Tier.CRITICAL);
		}
		if (!s.prevCombat && s.inCombat && s.biomassFrac < 0.10f) {
			return new Line("bad_idea", Tier.CRITICAL);
		}
		if (!s.prevCombat && s.inCombat && s.biomassFrac < 0.25f) {
			return new Line("fight_carefully", Tier.CRITICAL);
		}
		if (s.hpFrac < 0.3f && s.biomassFrac < 0.3f) {
			return new Line("both_failing_retreat", Tier.CRITICAL);
		}

		// ----- COMBAT / TACTICAL -----
		if (s.creeperClose) {
			return new Line("creeper", Tier.COMBAT);
		}
		if (s.bossNear && !s.prevBoss) {
			return new Line("boss_appears", Tier.COMBAT);
		}
		if (s.bossNear && s.bossHpFrac < 0.12f) {
			return new Line("boss_almost", Tier.COMBAT);
		}
		if (s.bossNear && s.biomassFrac < 0.4f) {
			return new Line("boss_withdraw", Tier.COMBAT);
		}
		if (s.bossNear) {
			return new Line("boss", Tier.COMBAT);
		}
		if (s.incomingProjectile && s.shieldReady) {
			return new Line("incoming_shield", Tier.COMBAT);
		}
		if (s.incomingProjectile) {
			return new Line("incoming_fire", Tier.COMBAT);
		}
		if (s.surrounded && s.onslaughtReady) {
			return new Line("onslaught_now", Tier.COMBAT);
		}
		if (s.surrounded) {
			return new Line("surrounded", Tier.COMBAT);
		}
		if (s.enemyBehind) {
			return new Line("behind_you", Tier.COMBAT);
		}
		if (s.enemyAbove) {
			return new Line("above_you", Tier.COMBAT);
		}
		if (s.enemyCount >= 4) {
			return new Line("multiple_threats", Tier.COMBAT);
		}
		if (s.enemyCount >= 3 && s.onslaughtReady) {
			return new Line("clustered_onslaught", Tier.COMBAT);
		}
		if (s.enemyCount >= 3) {
			return new Line("multiple_threats", Tier.COMBAT);
		}
		if (s.enemyCount > 0 && s.nearestEnemyHpFrac < 0.2f) {
			return new Line("finish_it", Tier.COMBAT);
		}
		if (s.enemyCount >= 2 && s.retreating) {
			return new Line("stop_trading_blows", Tier.COMBAT);
		}
		if (s.noWeapon && s.enemyCount > 0) {
			return new Line("we_are_the_weapon", Tier.COMBAT);
		}
		if (s.weaponNearlyBroken && s.enemyCount > 0) {
			return new Line("weapon_damaged", Tier.COMBAT);
		}
		if (s.enemyCount == 2 && !s.retreating) {
			return new Line("two_threats", Tier.COMBAT);
		}
		if (s.enemyCount == 1 && !s.loneUnawareEnemy && s.inCombat) {
			return new Line("one_threat", Tier.COMBAT);
		}
		if (s.spiderMan && s.enemyAbove) {
			return new Line("spiderman_web_them", Tier.COMBAT);
		}

		// ----- STATE (edge-triggered on the bars) -----
		Line state = biomassCrossing(s);
		if (state != null) {
			return state;
		}
		state = healthCrossing(s);
		if (state != null) {
			return state;
		}
		if (s.regenInterrupted) {
			return new Line("stop_getting_hit", Tier.STATE);
		}
		if (!s.prevCombat && s.inCombat && s.biomassFrac >= 0.999f) {
			return new Line("we_are_ready", Tier.STATE);
		}
		if (s.prevCombat && !s.inCombat && s.regenerating && s.biomassFrac < 0.6f) {
			return new Line("give_us_time", Tier.STATE);
		}
		if (s.recentKill && s.killStreak) {
			return new Line("more", Tier.STATE);
		}
		if (s.recentKill && s.enemyCount == 0) {
			return new Line("prey_down", Tier.STATE);
		}
		if (s.prevEnemyCount >= 3 && s.enemyCount == 0) {
			return new Line("all_clear", Tier.STATE);
		}

		// ----- AMBIENT -----
		if (s.spiderMan && s.falling) {
			return new Line("spiderman_swing", Tier.AMBIENT);
		}
		if (s.sleeping) {
			return new Line("rest_we_watch", Tier.AMBIENT);
		}
		if (s.drowning) {
			return new Line("surface", Tier.AMBIENT);
		}
		if (s.hunger <= 2) {
			return new Line("starving", Tier.AMBIENT);
		}
		if (s.hunger <= 6 && s.biomassFrac < 1.0f) {
			return new Line("need_food", Tier.AMBIENT);
		}
		if (s.crouchedHidden && s.loneUnawareEnemy) {
			return new Line("lone_prey", Tier.AMBIENT);
		}
		if (s.crouchedHidden && s.enemiesUnaware) {
			return new Line("unseen", Tier.AMBIENT);
		}
		if (s.enemiesUnaware && s.enemyAbove) {
			return new Line("strike_from_above", Tier.AMBIENT);
		}
		if (s.enemiesUnaware && s.crouched) {
			return new Line("they_have_not_seen_us", Tier.AMBIENT);
		}
		if (s.enemyCount > 0 && !s.inCombat && s.enemiesUnaware) {
			return new Line("avoid_them", Tier.AMBIENT);
		}
		if (s.regenerating && s.idleTicks > 120L && s.biomassFrac < 0.999f) {
			return new Line("good_give_us_time", Tier.AMBIENT);
		}
		if (s.enemyCount == 0 && !s.inCombat) {
			// The rotating patrol commentary -- the Symbiote just being present. Fires readily so the
			// organism is a constant presence during exploration, not a rare event.
			if (s.idleTicks > 120L) {
				int i = AMBIENT_ROTATION.merge(player.getId(), 1, Integer::sum);
				return new Line(AMBIENT_PATROL[Math.floorMod(i, AMBIENT_PATROL.length)], Tier.AMBIENT);
			}
			if (s.biomassFrac >= 0.999f && s.hpFrac >= 0.999f) {
				return new Line("we_are_whole", Tier.AMBIENT);
			}
		}
		return null;
	}

	/** The Biomass bar crossing a threshold, up (recovering) or down (taking damage). */
	private static Line biomassCrossing(Signals s) {
		if (s.prevBiomass == null) {
			return null;
		}
		float p = s.prevBiomass;
		float c = s.biomassFrac;
		if (p > 0.90f && c <= 0.90f) {
			return new Line("biomass_90_down", Tier.STATE);
		}
		if (p > 0.75f && c <= 0.75f) {
			return new Line("biomass_75_down", Tier.STATE);
		}
		if (p > 0.60f && c <= 0.60f) {
			return new Line("biomass_60_down", Tier.STATE);
		}
		if (p > 0.50f && c <= 0.50f) {
			return new Line("biomass_50_down", Tier.STATE);
		}
		if (p > 0.40f && c <= 0.40f) {
			return new Line("biomass_40_down", Tier.STATE);
		}
		if (p > 0.30f && c <= 0.30f) {
			return new Line("biomass_30_down", Tier.STATE);
		}
		if (p > 0.25f && c <= 0.25f) {
			return new Line("biomass_25_down", Tier.STATE);
		}
		if (p > 0.15f && c <= 0.15f) {
			return new Line("biomass_15_down", Tier.STATE);
		}
		if (s.regenerating) {
			if (p < 0.25f && c >= 0.25f) {
				return new Line("biomass_25_up", Tier.STATE);
			}
			if (p < 0.50f && c >= 0.50f) {
				return new Line("biomass_50_up", Tier.STATE);
			}
			if (p < 0.75f && c >= 0.75f) {
				return new Line("biomass_75_up", Tier.STATE);
			}
			if (p < 0.999f && c >= 0.999f) {
				return new Line("biomass_whole", Tier.STATE);
			}
			if (!s.prevRegen) {
				return new Line("repairing", Tier.STATE);
			}
		}
		return null;
	}

	/** The host's health crossing a threshold. */
	private static Line healthCrossing(Signals s) {
		if (s.prevHp == null) {
			return null;
		}
		float p = s.prevHp;
		float c = s.hpFrac;
		if (p > 0.5f && c <= 0.5f) {
			return new Line("host_50_down", Tier.STATE);
		}
		if (p > 0.3f && c <= 0.3f) {
			return new Line("host_30_down", Tier.STATE);
		}
		if (p < 0.999f && c >= 0.999f) {
			return new Line("host_restored", Tier.STATE);
		}
		if (p < 0.6f && c >= 0.75f) {
			return new Line("host_stabilizing", Tier.STATE);
		}
		return null;
	}

	// ---------------- lifecycle ----------------

	public static void clearFor(ServerPlayer player) {
		int id = player.getId();
		LAST_SPOKEN.remove(id);
		LAST_ID.remove(id);
		PREV_BIOMASS.remove(id);
		PREV_HP.remove(id);
		PREV_REGEN.remove(id);
		PREV_COMBAT.remove(id);
		PREV_BOSS.remove(id);
		PREV_KILLS.remove(id);
		LAST_KILL_TICK.remove(id);
		KILL_WINDOW.remove(id);
		AMBIENT_ROTATION.remove(id);
		BOND_ROTATION.remove(id);
		PREV_ENEMY_COUNT.remove(id);
	}

	public static void clearSessionState() {
		LAST_SPOKEN.clear();
		LAST_ID.clear();
		PREV_BIOMASS.clear();
		PREV_HP.clear();
		PREV_REGEN.clear();
		PREV_COMBAT.clear();
		PREV_BOSS.clear();
		PREV_KILLS.clear();
		LAST_KILL_TICK.clear();
		KILL_WINDOW.clear();
		AMBIENT_ROTATION.clear();
		BOND_ROTATION.clear();
		PREV_ENEMY_COUNT.clear();
	}
}
