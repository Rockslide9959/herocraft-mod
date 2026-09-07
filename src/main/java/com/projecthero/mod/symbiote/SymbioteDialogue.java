package com.projecthero.mod.symbiote;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * The Symbiote's context-aware voice (v0.9.24). Rather than firing generic status lines, the bonded
 * organism watches the fight -- the host's health, its own Biomass, hunger, nearby enemies and bosses,
 * incoming fire, hazards, terrain, whether the host is winning or losing -- and, at most once every few
 * seconds, says the single most relevant thing.
 *
 * <p>Rules are checked in strict priority order (emergencies first) and the first match wins. Each
 * priority tier has its own minimum gap; a higher tier may interrupt a lower one sooner, and the same
 * line never repeats back-to-back. This layers on top of {@link SymbioteVitalsManager}'s short
 * {@code warn_1..4} action-bar lines rather than replacing them.
 */
public final class SymbioteDialogue {
	private static final int EVAL_INTERVAL = 10; // twice a second

	private enum Tier {
		EMERGENCY(40), CRITICAL(90), COMBAT(120), STATE(170), AMBIENT(420);

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
	private static final Map<Integer, Boolean> PREV_REGEN = new ConcurrentHashMap<>();
	private static final Map<Integer, Boolean> PREV_COMBAT = new ConcurrentHashMap<>();

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

		Line pick = choose(player, s);

		// Remember this frame's values for the next edge-detected rule.
		PREV_BIOMASS.put(player.getId(), s.biomassFrac);
		PREV_REGEN.put(player.getId(), s.regenerating);
		PREV_COMBAT.put(player.getId(), s.inCombat);

		if (pick == null) {
			return;
		}
		long last = LAST_SPOKEN.getOrDefault(player.getId(), Long.MIN_VALUE);
		if (now - last < pick.tier.gapTicks) {
			return;
		}
		if (pick.id.equals(LAST_ID.get(player.getId())) && now - last < pick.tier.gapTicks * 3L) {
			return;
		}
		speak(player, pick.id);
		LAST_SPOKEN.put(player.getId(), now);
		LAST_ID.put(player.getId(), pick.id);
	}

	private static void speak(ServerPlayer player, String id) {
		player.sendSystemMessage(Component.literal("“")
				.append(Component.translatable("message.projecthero.symbiote.talk." + id))
				.append(Component.literal("”"))
				.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
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
		boolean spiderMan;
		boolean sleeping;
		boolean crouchedHidden;
		boolean inCombat;
		boolean regenerating;
		boolean incomingProjectile;
		boolean creeperClose;
		boolean enemyBehind;
		boolean surrounded;
		boolean tookBigHit;
		int enemyCount;
		float nearestEnemyHpFrac;
		boolean bossNear;
		float bossHpFrac;
		boolean enemiesUnaware;
		Float prevBiomass;
		boolean prevRegen;
		boolean prevCombat;
	}

	private static Signals gather(ServerPlayer player, ServerLevel level, long now) {
		Signals s = new Signals();
		s.hpFrac = player.getMaxHealth() > 0 ? player.getHealth() / player.getMaxHealth() : 1.0f;
		s.biomassFrac = SymbioteVitalsManager.biomassFraction(player);
		s.broken = !SymbioteVitalsManager.usable(player);
		s.hunger = player.getFoodData().getFoodLevel();
		s.onFire = player.isOnFire() && !player.fireImmune();
		s.inLava = player.isInLava();
		s.falling = !player.onGround() && player.getDeltaMovement().y < -0.5 && player.fallDistance > 3.0f;
		s.spiderMan = SymbioteHostType.of(player) == SymbioteHostType.SPIDER_MAN;
		s.sleeping = player.isSleeping();
		s.inCombat = SymbioteVitalsManager.ticksSinceCombat(player, now) < 60L;
		s.regenerating = SymbioteVitalsManager.regenerating(player, now);
		s.tookBigHit = SymbioteVitalsManager.tookBigHitRecently(player, now);
		s.prevBiomass = PREV_BIOMASS.get(player.getId());
		s.prevRegen = PREV_REGEN.getOrDefault(player.getId(), false);
		s.prevCombat = PREV_COMBAT.getOrDefault(player.getId(), false);

		Vec3 look = player.getLookAngle();

		List<Monster> hostiles = level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(18.0),
				m -> m.isAlive() && m.distanceToSqr(player) <= 18.0 * 18.0);
		s.enemyCount = hostiles.size();
		s.nearestEnemyHpFrac = 1.0f;
		double nearest = Double.MAX_VALUE;
		int quadrants = 0;
		boolean[] quad = new boolean[4];
		boolean anyAware = false;
		for (Monster m : hostiles) {
			double d = m.distanceToSqr(player);
			if (d < nearest) {
				nearest = d;
				s.nearestEnemyHpFrac = m.getMaxHealth() > 0 ? m.getHealth() / m.getMaxHealth() : 1.0f;
			}
			if (m instanceof Creeper && d <= 6.0 * 6.0) {
				s.creeperClose = true;
			}
			Vec3 to = m.position().subtract(player.position());
			if (to.lengthSqr() > 0.01) {
				Vec3 n = to.normalize();
				if (d <= 8.0 * 8.0 && n.dot(look) < -0.15) {
					s.enemyBehind = true;
				}
				int q = (n.x >= 0 ? 1 : 0) + (n.z >= 0 ? 2 : 0);
				if (d <= 8.0 * 8.0) {
					quad[q] = true;
				}
			}
			if (m.getMaxHealth() >= 80.0f && d <= 32.0 * 32.0) {
				s.bossNear = true;
				s.bossHpFrac = m.getMaxHealth() > 0 ? m.getHealth() / m.getMaxHealth() : 1.0f;
			}
			if (m instanceof Mob mob && mob.getTarget() == player) {
				anyAware = true;
			}
		}
		for (boolean b : quad) {
			if (b) {
				quadrants++;
			}
		}
		s.surrounded = quadrants >= 3 || (s.enemyCount >= 4 && nearest <= 6.0 * 6.0);
		s.enemiesUnaware = s.enemyCount > 0 && !anyAware;
		s.crouchedHidden = player.isShiftKeyDown()
				&& player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);

		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(10.0))) {
			if (proj.getOwner() == player || proj.getDeltaMovement().lengthSqr() < 0.05) {
				continue;
			}
			Vec3 toMe = player.position().add(0, 1, 0).subtract(proj.position());
			if (toMe.lengthSqr() > 0.01 && proj.getDeltaMovement().normalize().dot(toMe.normalize()) > 0.6) {
				s.incomingProjectile = true;
				break;
			}
		}
		return s;
	}

	// ---------------- rule priority ----------------

	private static Line choose(ServerPlayer player, Signals s) {
		// ----- EMERGENCY -----
		if (s.inLava) {
			return new Line("lava_get_out", Tier.EMERGENCY);
		}
		if (s.onFire && s.hpFrac < 0.25f) {
			return new Line("fire_dying", Tier.EMERGENCY);
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
		if (s.falling && s.biomassFrac < 0.25f) {
			return new Line("falling_low_biomass", Tier.EMERGENCY);
		}

		// ----- CRITICAL -----
		if (s.biomassFrac <= 0.05f) {
			return new Line("biomass_dying", Tier.CRITICAL);
		}
		if (s.biomassFrac <= 0.10f) {
			return new Line("biomass_critical", Tier.CRITICAL);
		}
		if (s.broken) {
			return new Line("form_collapsing", Tier.CRITICAL);
		}
		if (s.surrounded && s.biomassFrac < 0.35f) {
			return new Line("surrounded_break_through", Tier.CRITICAL);
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
		if (!s.prevCombat && s.inCombat && s.biomassFrac < 0.25f) {
			return new Line("fight_carefully", Tier.CRITICAL);
		}

		// ----- COMBAT / TACTICAL -----
		if (s.creeperClose) {
			return new Line("creeper", Tier.COMBAT);
		}
		if (s.bossNear && s.bossHpFrac < 0.15f) {
			return new Line("boss_almost", Tier.COMBAT);
		}
		if (s.bossNear && s.biomassFrac < 0.4f) {
			return new Line("boss_withdraw", Tier.COMBAT);
		}
		if (s.bossNear) {
			return new Line("boss", Tier.COMBAT);
		}
		if (s.incomingProjectile) {
			return new Line("incoming_fire", Tier.COMBAT);
		}
		if (s.surrounded) {
			return new Line("surrounded", Tier.COMBAT);
		}
		if (s.enemyBehind) {
			return new Line("behind_you", Tier.COMBAT);
		}
		if (s.enemyCount >= 3) {
			return new Line("multiple_threats", Tier.COMBAT);
		}
		if (s.enemyCount > 0 && s.nearestEnemyHpFrac < 0.2f) {
			return new Line("finish_it", Tier.COMBAT);
		}

		// ----- STATE (edge-triggered on the Biomass bar) -----
		Line state = biomassCrossing(s);
		if (state != null) {
			return state;
		}
		if (s.prevRegen && !s.regenerating && s.inCombat && s.biomassFrac < 0.99f) {
			return new Line("stop_getting_hit", Tier.STATE);
		}

		// ----- AMBIENT -----
		if (s.spiderMan && s.falling) {
			return new Line("spiderman_swing", Tier.AMBIENT);
		}
		if (s.sleeping) {
			return new Line("rest_we_watch", Tier.AMBIENT);
		}
		if (s.hunger <= 2) {
			return new Line("starving", Tier.AMBIENT);
		}
		if (s.hunger <= 6 && s.biomassFrac < 1.0f) {
			return new Line("need_food", Tier.AMBIENT);
		}
		if (s.crouchedHidden && s.enemiesUnaware) {
			return new Line("unseen", Tier.AMBIENT);
		}
		if (s.enemyCount == 0 && !s.inCombat && s.biomassFrac >= 0.999f && s.hpFrac >= 0.999f) {
			return new Line("we_are_whole", Tier.AMBIENT);
		}
		if (s.enemyCount == 0 && !s.inCombat && SymbioteVitalsManager.ticksSinceCombat(player,
				player.level().getGameTime()) > 1200L) {
			return new Line("it_is_quiet", Tier.AMBIENT);
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
		// downward
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
		if (p > 0.25f && c <= 0.25f) {
			return new Line("biomass_25_down", Tier.STATE);
		}
		// upward (recovering)
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

	// ---------------- lifecycle ----------------

	public static void clearFor(ServerPlayer player) {
		int id = player.getId();
		LAST_SPOKEN.remove(id);
		LAST_ID.remove(id);
		PREV_BIOMASS.remove(id);
		PREV_REGEN.remove(id);
		PREV_COMBAT.remove(id);
	}

	public static void clearSessionState() {
		LAST_SPOKEN.clear();
		LAST_ID.clear();
		PREV_BIOMASS.clear();
		PREV_REGEN.clear();
		PREV_COMBAT.clear();
	}
}
