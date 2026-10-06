package com.projecthero.mod.sentinel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.sentinel.entity.SentinelRobot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.1: how the Sentinel Program sees people. Every robot scores the players around it and goes for the biggest
 * threat to "humanity" first: a <b>mutant</b> (anyone holding a mutation -- the Experimental powers, Super Speed
 * included), then any other <b>superhuman</b> (a Hero-Tier power or a Symbiote), and only then ordinary humans who got
 * in the way. The "MUTANT DETECTED" line a robot announces when it locks on is throttled per player here; that small
 * map is cleared by {@code ServerStateReset} with the rest of the mod's static server state.
 */
public final class SentinelTargets {
	public enum Threat {
		HUMAN, SUPERHUMAN, MUTANT;

		public String langKey() {
			return "sentinel.projecthero.threat." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Game time each player last heard a lock-on line. */
	private static final Map<UUID, Long> LAST_ANNOUNCED = new HashMap<>();
	private static final int ANNOUNCE_GAP_TICKS = 160;

	private SentinelTargets() {
	}

	public static void clearSessionState() {
		LAST_ANNOUNCED.clear();
	}

	/** What the Sentinels make of {@code player}. */
	public static Threat classify(Player player) {
		if (!(player instanceof ServerPlayer sp)) {
			return Threat.HUMAN;
		}
		if (HeroTiers.hasExperimental(sp)) {
			return Threat.MUTANT;
		}
		if (HeroTiers.hasHeroTier(sp) || com.projecthero.mod.symbiote.Symbiote.hasSymbiote(sp)) {
			return Threat.SUPERHUMAN;
		}
		return Threat.HUMAN;
	}

	/** True for anyone the Sentinels hunt on sight (natural trigger + the purge's announcements). */
	public static boolean isPowered(Player player) {
		return classify(player) != Threat.HUMAN;
	}

	/** Who a Sentinel may attack: anything alive that isn't a Sentinel, except players in creative or spectator. */
	public static boolean canTarget(Entity e) {
		if (!(e instanceof LivingEntity l) || !l.isAlive() || e.isSpectator() || e instanceof SentinelRobot) {
			return false;
		}
		// abilities, not isCreative(): creative and spectator both set invulnerable, and the gametest mock player
		// (which always reports creative) can then stand in for a survival player
		return !(e instanceof Player p) || (!p.getAbilities().invulnerable && !p.isSpectator());
	}

	/**
	 * The best player for {@code robot} to hunt within {@code range}: mutants first, then superhumans, then humans, and
	 * the nearest within each class. Null if nobody is in range.
	 */
	public static Player pickTarget(ServerLevel level, LivingEntity robot, double range) {
		Player best = null;
		double bestScore = Double.MAX_VALUE;
		double r2 = range * range;
		for (ServerPlayer p : level.players()) {
			if (!canTarget(p)) {
				continue;
			}
			double d = p.distanceToSqr(robot);
			if (d > r2) {
				continue;
			}
			// a mutant 40 blocks away still beats a human standing beside it
			double score = Math.sqrt(d) - classify(p).ordinal() * 100.0;
			if (score < bestScore) {
				bestScore = score;
				best = p;
			}
		}
		return best;
	}

	/** The robot's lock-on line, sent to the player's action bar at most once every eight seconds. */
	public static void announceLock(ServerLevel level, Component speaker, Player target) {
		if (!(target instanceof ServerPlayer sp)) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST_ANNOUNCED.get(sp.getUUID());
		if (last != null && now - last < ANNOUNCE_GAP_TICKS && now >= last) {
			return;
		}
		LAST_ANNOUNCED.put(sp.getUUID(), now);
		Threat threat = classify(sp);
		sp.displayClientMessage(Component.translatable("sentinel.projecthero.chat", speaker,
				Component.translatable("sentinel.projecthero.say.lock." + threat.name().toLowerCase(java.util.Locale.ROOT)))
				.withStyle(threat == Threat.MUTANT ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_PURPLE), true);
	}
}
