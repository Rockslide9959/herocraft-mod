package com.projecthero.mod.omnitrix.alien.upgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Upgrade revamp (user: "Yes, build them" -- docs/OMNITRIX_REVAMP_UPGRADE_GREYMATTER_XLR8.md): Tech Takeover. Upgrade's
 * nanites get into a machine -- an iron or snow golem, a Sentinel, a Sentinel drone, Master Mold's minions, any of Ultron's
 * robots, a Galactus Punisher -- and it <b>fights for him</b>: it drops whoever it was after, turns on the nearest foe of his
 * (anything hostile, or whatever is after him), and every second its systems fire a green bolt into that foe in his name (so
 * a robot can hit its own kind, which its own weapons never may). It never hurts him, his squad or his other machines.
 *
 * <p>Held per machine; let go cleanly on every path: its time running out, its death or unloading, Upgrade turning back
 * (timeout, N, death, logout -- {@link #releaseAll} from {@code UpgradeKit.onRevert}), him leaving its dimension, and the
 * server stopping ({@link #clearSessionState}). Another player's Iron Man suit is never a target (the parent's U2 default);
 * bosses are only stunned (the caller's rule).
 */
public final class UpgradeTakeover {
	/** Tech Takeover's hold (15 s), EMP Burst's (10 s), and how many Shift+C holds he keeps at once. */
	public static final int TICKS = 300, EMP_TICKS = 200, MAX = 2;
	/** How far a held machine looks for a foe, how far its bolt reaches, and the bolt's damage range (its own attack, clamped). */
	public static final double HUNT_RANGE = 16, FIGHT_RANGE = 12;
	public static final float BOLT_MIN = 4f, BOLT_MAX = 10f;
	public static final int BOLT_EVERY = 20;

	/** One held machine. {@code capped}: from Shift+C (counts towards {@link #MAX}); EMP holds don't. */
	private record Hold(UUID owner, ResourceKey<Level> dim, int entityId, long start, long until, boolean capped, long seq) {
	}

	/** Taking order (the oldest Shift+C hold is the first let go). */
	private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();
	/** machine UUID -> its hold. */
	private static final Map<UUID, Hold> HELD = new ConcurrentHashMap<>();

	private UpgradeTakeover() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(UpgradeTakeover::tick);
		// a held machine never hurts the one who holds it, his squad, or his other machines
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> !friendlyHit(victim, source));
	}

	public static void clearSessionState() {
		HELD.clear();
	}

	// ---------------------------------------------------------------- what counts

	/** A machine Upgrade can take over (and merge with from range): golems, Sentinels, Ultron's robots, Galactus' Punishers. */
	public static boolean mechanical(@Nullable Entity e) {
		return e instanceof IronGolem || e instanceof SnowGolem || e instanceof com.projecthero.mod.sentinel.entity.SentinelRobot
				|| e instanceof com.projecthero.mod.ultron.entity.UltronRobot || e instanceof com.projecthero.mod.galactus.entity.PunisherRobotEntity;
	}

	/** Who holds {@code e} right now, or null. */
	@Nullable
	public static UUID controller(@Nullable Entity e) {
		if (e == null || HELD.isEmpty()) {
			return null;
		}
		Hold h = HELD.get(e.getUUID());
		return h == null ? null : h.owner();
	}

	public static boolean controlledBy(@Nullable Entity e, Player p) {
		return p != null && p.getUUID().equals(controller(e));
	}

	/** How many machines {@code owner} holds (all of them, or only the Shift+C ones). */
	public static int count(Player owner, boolean cappedOnly) {
		int n = 0;
		for (Hold h : HELD.values()) {
			if (h.owner().equals(owner.getUUID()) && (!cappedOnly || h.capped())) {
				n++;
			}
		}
		return n;
	}

	/** Game time {@code e}'s hold ends, or 0. */
	public static long until(Entity e) {
		Hold h = HELD.get(e.getUUID());
		return h == null ? 0L : h.until();
	}

	// ---------------------------------------------------------------- taking / letting go

	/**
	 * {@code owner} takes {@code mob} over for {@code ticks}. A Shift+C hold ({@code capped}) beyond {@link #MAX} lets the oldest
	 * one go. The machine forgets its target, shakes off any short-out, and its green lines go out to every client.
	 */
	public static boolean take(ServerPlayer owner, Mob mob, int ticks, boolean capped) {
		if (!mob.isAlive() || !mechanical(mob)) {
			return false;
		}
		long now = owner.level().getGameTime();
		Hold old = HELD.get(mob.getUUID());
		if (old != null && !old.owner().equals(owner.getUUID())) {
			release(owner.getServer(), mob.getUUID(), old, false);
		}
		if (capped && (old == null || !old.capped())) {
			while (count(owner, true) >= MAX) {
				UUID oldest = null;
				Hold oh = null;
				for (Map.Entry<UUID, Hold> en : HELD.entrySet()) {
					Hold h = en.getValue();
					if (h.owner().equals(owner.getUUID()) && h.capped() && (oh == null || h.seq() < oh.seq())) {
						oldest = en.getKey();
						oh = h;
					}
				}
				if (oldest == null) {
					break;
				}
				release(owner.getServer(), oldest, oh, true);
			}
		}
		long until = Math.max(now + ticks, old != null && old.owner().equals(owner.getUUID()) ? old.until() : 0L);
		boolean mine = old != null && old.owner().equals(owner.getUUID());
		HELD.put(mob.getUUID(), new Hold(owner.getUUID(), mob.level().dimension(), mob.getId(), now, until, capped || (mine && old.capped()),
				mine ? old.seq() : SEQ.incrementAndGet()));
		mob.setTarget(null);
		mob.getNavigation().stop();
		mob.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		mob.removeEffect(MobEffects.WEAKNESS);
		mob.removeEffect(MobEffects.DIG_SLOWDOWN);
		if (mob.getLastHurtByMob() == owner) {
			mob.setLastHurtByMob(null);
		}
		broadcast(mob, owner.getId(), (int) (until - now));
		return true;
	}

	/** Lets {@code e} go (no-op if nobody holds it). */
	public static void release(Entity e) {
		Hold h = HELD.get(e.getUUID());
		if (h != null && e.getServer() != null) {
			release(e.getServer(), e.getUUID(), h, true);
		}
	}

	/** Lets every machine {@code owner} holds go (he turned back, logged out, died). */
	public static void releaseAll(ServerPlayer owner) {
		if (HELD.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Hold> en : new ArrayList<>(HELD.entrySet())) {
			if (en.getValue().owner().equals(owner.getUUID())) {
				release(owner.getServer(), en.getKey(), en.getValue(), true);
			}
		}
	}

	private static void release(@Nullable MinecraftServer server, UUID id, Hold h, boolean fx) {
		HELD.remove(id);
		if (server == null) {
			return;
		}
		ServerLevel level = server.getLevel(h.dim());
		Entity e = level == null ? null : level.getEntity(h.entityId());
		if (e instanceof Mob mob && mob.getUUID().equals(id)) {
			mob.setTarget(null);
			mob.getNavigation().stop();
			broadcast(mob, -1, 0);
			if (fx && mob.isAlive()) {
				Vec3 c = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
				QuirkFx.ring(level, c).style(QuirkFx.RING_SOFT).colours(0xB8FFB0, UpgradeKit.COLOUR).size(mob.getBbWidth() + 1.2f).thickness(0.15f).life(10).spawn();
			}
		}
	}

	private static void broadcast(Mob mob, int ownerId, int ticks) {
		UpgradeTakeoverPayload pl = new UpgradeTakeoverPayload(mob.getId(), ownerId, ticks);
		if (!(mob.level() instanceof ServerLevel)) {
			return;
		}
		List<ServerPlayer> to = new ArrayList<>(PlayerLookup.tracking(mob));
		for (ServerPlayer sp : ((ServerLevel) mob.level()).players()) {
			if (sp.getId() == ownerId && !to.contains(sp)) {
				to.add(sp);
			}
		}
		for (ServerPlayer sp : to) {
			if (ServerPlayNetworking.canSend(sp, UpgradeTakeoverPayload.TYPE)) {
				ServerPlayNetworking.send(sp, pl);
			}
		}
	}

	// ---------------------------------------------------------------- every tick

	public static void tick(MinecraftServer server) {
		if (HELD.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Hold> en : new ArrayList<>(HELD.entrySet())) {
			UUID id = en.getKey();
			Hold h = en.getValue();
			ServerLevel level = server.getLevel(h.dim());
			Entity e = level == null ? null : level.getEntity(h.entityId());
			ServerPlayer owner = server.getPlayerList().getPlayer(h.owner());
			if (!(e instanceof Mob mob) || !mob.getUUID().equals(id) || !mob.isAlive() || mob.isRemoved()) {
				HELD.remove(id); // dead or unloaded: nothing left to let go of
				if (e instanceof Mob gone && gone.getUUID().equals(id)) {
					broadcast(gone, -1, 0);
				}
				continue;
			}
			long now = level.getGameTime();
			if (owner == null || !owner.isAlive() || owner.level() != level || now >= h.until() || !UpgradeKit.ID.equals(Omnitrix.active(owner))
					|| !Omnitrix.state(owner).transformed()) {
				release(server, id, h, true);
				continue;
			}
			drive(owner, mob, h, level, now);
		}
	}

	/** A held machine: off him and his, onto his foes, a bolt into them every second; with no foe it keeps close to him. */
	private static void drive(ServerPlayer owner, Mob mob, Hold h, ServerLevel level, long now) {
		LivingEntity t = mob.getTarget();
		if (t != null && (!t.isAlive() || friendly(owner, t))) {
			mob.setTarget(null);
			t = null;
		}
		if (t == null || (now - h.start()) % 10 == 0) {
			LivingEntity foe = foe(owner, mob);
			if (foe != null && foe != t) {
				mob.setTarget(foe);
				t = foe;
			}
		}
		if (t == null) {
			if ((now - h.start()) % 20 == 0 && mob.distanceToSqr(owner) > 36) {
				mob.getNavigation().moveTo(owner, 1.1);
			}
			return;
		}
		if ((now - h.start()) % BOLT_EVERY == BOLT_EVERY - 1 && mob.distanceToSqr(t) <= FIGHT_RANGE * FIGHT_RANGE && mob.hasLineOfSight(t)) {
			bolt(owner, mob, t, level);
		}
	}

	/** The machine's systems fire one green bolt into {@code t} in {@code owner}'s name (its own attack, 4 to 10). */
	static float bolt(ServerPlayer owner, Mob mob, LivingEntity t, ServerLevel level) {
		double atk = mob.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 6.0 : mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
		float dmg = (float) Math.max(BOLT_MIN, Math.min(BOLT_MAX, atk));
		t.invulnerableTime = 0;
		AlienKit.hurt(owner, t, dmg, false);
		Vec3 a = mob.position().add(0, mob.getBbHeight() * 0.7, 0);
		Vec3 b = t.position().add(0, t.getBbHeight() * 0.5, 0);
		QuirkFx.ribbon(level, a, b).style(QuirkFx.RIBBON_LIGHT).colours(0xEFFFF0, UpgradeKit.COLOUR).size(0.1f).life(6).spawn();
		OmnitrixSounds.play(level, a, UpgradeKit.SND_BEAM, 0.6f, 1.4f);
		return dmg;
	}

	/** The nearest foe of {@code owner}'s to {@code mob}: anything hostile, or whatever is after him or he last fought. */
	@Nullable
	static LivingEntity foe(ServerPlayer owner, Mob mob) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		AABB box = mob.getBoundingBox().inflate(HUNT_RANGE);
		for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			if (e == mob || friendly(owner, e)) {
				continue;
			}
			boolean hostile = (e instanceof Enemy && !(e instanceof Player)) || (e instanceof Mob m && m.getTarget() == owner)
					|| e == owner.getLastHurtByMob() || e == owner.getLastHurtMob();
			if (!hostile) {
				continue;
			}
			double d = e.distanceToSqr(mob);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** {@code e} is {@code owner}, his squad / team, or another machine he holds. */
	static boolean friendly(ServerPlayer owner, Entity e) {
		return e == owner || com.projecthero.mod.squad.Squads.areAllies(owner, e) || (e instanceof Player && owner.isAlliedTo(e))
				|| controlledBy(e, owner);
	}

	/** Damage from a held machine (or its shots) to its holder, his squad or his other machines. */
	private static boolean friendlyHit(LivingEntity victim, DamageSource source) {
		if (HELD.isEmpty()) {
			return false;
		}
		Entity attacker = source.getEntity();
		Hold h = attacker == null ? null : HELD.get(attacker.getUUID());
		if (h == null || victim.getServer() == null) {
			return false;
		}
		ServerPlayer owner = victim.getServer().getPlayerList().getPlayer(h.owner());
		return owner != null && friendly(owner, victim);
	}
}
