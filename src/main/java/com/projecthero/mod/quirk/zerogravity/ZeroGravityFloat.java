package com.projecthero.mod.quirk.zerogravity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.squad.Squads;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: what Zero Gravity floats -- creatures, players and dropped items -- the one place that turns their gravity off and,
 * without exception, back on. (Blocks are {@link ZeroGravityDebris}.)
 *
 * <p>Every floated thing is a {@link Floated} in its floater's session, keyed by entity id, and its UUID is in {@link #FLOATED}
 * (one floater at a time). {@code setNoGravity(true)} is only ever called here, on things that had gravity, and every way out
 * puts it back: {@link #release} (Release, the Slam, the Field ending, Nausea, {@code stopAll} on revoke / death / logout /
 * Erasure / world change), the leash (too far, another world), {@link #restoreEverything} on {@code SERVER_STOPPING}. A thing
 * that leaves the world while floating (chunk unload) carries {@link ZeroGravity#TAG_FLOAT}; {@link #cleanup} puts its gravity
 * back the moment it is loaded again. A floated player who logs out is let go ({@link #releaseTarget}).
 *
 * <p>Floating mobs hang helpless: their path is cancelled every tick and they are slowed and weakened so they cannot really
 * fight back; a floated player drifts and cannot jump (there is no floor). A floated item cannot be picked up while it floats.
 */
public final class ZeroGravityFloat {
	static final class Floated {
		final UUID uuid;
		final int id;
		final ServerLevel level;
		final double weight;
		double homeY;
		final boolean ally;
		final boolean player;
		boolean grantedMayfly;
		long flungUntil;
		/** Let go by itself at this game time (the Field); 0 = when she says. */
		long releaseAt;
		/** Rises this fast to {@link #homeY} (the Slam's lift); 0 = the gentle float. */
		double riseSpeed;
		int ticks;
		/** v0.17.1 Herding: a floated animal follows her until this game time (0 = not herded), then is set down softly. */
		long herdUntil;
		/** v0.17.1: let down softly whenever it is let go (a herded animal). */
		boolean soft;

		Floated(Entity e, double weight, boolean ally, double rise) {
			this.uuid = e.getUUID();
			this.id = e.getId();
			this.level = (ServerLevel) e.level();
			this.weight = weight;
			this.homeY = e.getY() + rise;
			this.ally = ally;
			this.player = e instanceof Player;
		}
	}

	/** Floater -> what she floats (insertion order). */
	private static final Map<UUID, LinkedHashMap<Integer, Floated>> SESSIONS = new ConcurrentHashMap<>();
	/** Floated thing -> its floater. */
	private static final Map<UUID, UUID> FLOATED = new ConcurrentHashMap<>();
	/** Floaters whose own body floats (Float Self) and whether they were handed {@code mayfly} for it. */
	private static final Map<UUID, Boolean> SELF = new ConcurrentHashMap<>();

	private ZeroGravityFloat() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
		FLOATED.clear();
		SELF.clear();
	}

	private static LinkedHashMap<Integer, Floated> session(ServerPlayer owner) {
		return SESSIONS.computeIfAbsent(owner.getUUID(), k -> new LinkedHashMap<>());
	}

	// ================================================================ queries

	/** How many things she floats. */
	public static int count(ServerPlayer owner) {
		LinkedHashMap<Integer, Floated> s = SESSIONS.get(owner.getUUID());
		return s == null ? 0 : s.size();
	}

	/** How many creatures (not items) she floats. */
	public static int creatures(ServerPlayer owner) {
		int n = 0;
		LinkedHashMap<Integer, Floated> s = SESSIONS.get(owner.getUUID());
		if (s != null) {
			for (Floated f : s.values()) {
				if (resolve(f) instanceof LivingEntity) {
					n++;
				}
			}
		}
		return n;
	}

	/** Anyone floats it right now. */
	public static boolean isFloating(Entity e) {
		return FLOATED.containsKey(e.getUUID());
	}

	/** {@code owner} floats it right now. */
	public static boolean floatedBy(ServerPlayer owner, Entity e) {
		return owner.getUUID().equals(FLOATED.get(e.getUUID()));
	}

	/** The things she floats (live ones only). */
	public static List<Entity> floating(ServerPlayer owner) {
		List<Entity> out = new ArrayList<>();
		LinkedHashMap<Integer, Floated> s = SESSIONS.get(owner.getUUID());
		if (s != null) {
			for (Floated f : s.values()) {
				Entity e = resolve(f);
				if (e != null) {
					out.add(e);
				}
			}
		}
		return out;
	}

	/** The weight she carries (every floated thing; her own body is counted by the caller). */
	public static double weight(ServerPlayer owner) {
		double w = 0.0;
		LinkedHashMap<Integer, Floated> s = SESSIONS.get(owner.getUUID());
		if (s != null) {
			for (Floated f : s.values()) {
				w += f.weight;
			}
		}
		return w;
	}

	public static double weightOf(Entity e) {
		if (e instanceof ItemEntity) {
			return ZeroGravityConfig.WEIGHT_ITEM;
		}
		double v = e.getBbWidth() * e.getBbWidth() * e.getBbHeight() * ZeroGravityConfig.WEIGHT_PER_VOLUME;
		return Mth.clamp(v, ZeroGravityConfig.WEIGHT_MIN, ZeroGravityConfig.WEIGHT_MAX);
	}

	/**
	 * Can she float {@code e}? A creature, a player or a dropped item -- never a boss, herself, an armour stand, something that
	 * already has no gravity (it would be wrong to "give it back"), anything another floater holds, a creative / spectator
	 * player. {@code area} moves take only creatures she may harm (never a squadmate); a touch may also float a squadmate (a
	 * rescue lift). Items always float.
	 */
	public static boolean canFloat(ServerPlayer owner, Entity e, boolean area) {
		if (e == owner || !e.isAlive() || e.isRemoved() || e.isNoGravity() || isFloating(e) || e.level() != owner.level() || e.isSpectator()) {
			return false;
		}
		if (e instanceof ItemEntity) {
			return true;
		}
		if (!(e instanceof LivingEntity le) || e instanceof ArmorStand || ZeroGravityCombat.isBoss(le)) {
			return false;
		}
		if (e instanceof Player tp && (tp.getAbilities().instabuild || tp.getAbilities().invulnerable)) {
			return false;
		}
		if (ZeroGravityCombat.isTarget(owner, le)) {
			return true;
		}
		return !area && Squads.areAllies(owner, le);
	}

	private static @Nullable Entity resolve(Floated f) {
		Entity e = f.level.getEntity(f.id);
		if (e != null && e.getUUID().equals(f.uuid) && e.isAlive() && !e.isRemoved()) {
			return e;
		}
		return null;
	}

	// ================================================================ float / release

	/** Floats {@code e} (it rises {@link ZeroGravityConfig#FLOAT_RISE} and hangs there). False if refused. */
	public static boolean start(ServerPlayer owner, Entity e, boolean area) {
		return start(owner, e, area, ZeroGravityConfig.FLOAT_RISE);
	}

	/** Floats {@code e}, rising {@code rise} blocks. False if refused. */
	public static boolean start(ServerPlayer owner, Entity e, boolean area, double rise) {
		if (!canFloat(owner, e, area)) {
			return false;
		}
		boolean ally = e instanceof LivingEntity le && Squads.areAllies(owner, le);
		if (e.isPassenger()) {
			e.stopRiding();
		}
		Floated f = new Floated(e, weightOf(e), ally, rise);
		e.resetFallDistance();
		e.setNoGravity(true);
		e.addTag(ZeroGravity.TAG_FLOAT);
		e.setOnGround(false);
		Vec3 v = e.getDeltaMovement();
		// a gentle, even lift (a hit's own knock-up is replaced, or it would overshoot its hover height)
		e.setDeltaMovement(v.x * 0.3, ZeroGravityConfig.FLOAT_LIFT_SPEED, v.z * 0.3);
		e.hurtMarked = true;
		if (e instanceof Mob m) {
			m.getNavigation().stop();
		}
		if (e instanceof ItemEntity item) {
			item.setPickUpDelay(40);
		}
		if (e instanceof ServerPlayer tp) {
			f.grantedMayfly = floatTolerance(tp, true);
		}
		// v0.17.1 Herding: livestock she floats follows her for 20 s (and is never dropped hard)
		if (!area && e instanceof net.minecraft.world.entity.animal.Animal && !(e instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame())) {
			f.herdUntil = owner.level().getGameTime() + ZeroGravityConfig.HERD_TICKS;
			f.soft = true;
		}
		session(owner).put(e.getId(), f);
		FLOATED.put(e.getUUID(), owner.getUUID());
		ZeroGravitySounds.play(owner.serverLevel(), e.position(), ZeroGravitySounds.FLOAT, 0.5f, 1.0f + owner.getRandom().nextFloat() * 0.2f);
		return true;
	}

	/** v0.17.1 Herding: {@code e} follows her right now (tests). */
	public static boolean herded(ServerPlayer owner, Entity e) {
		Floated f = get(owner, e);
		return f != null && f.herdUntil > owner.level().getGameTime();
	}

	/** Tests: the herd timer runs out now. */
	public static void endHerdForTest(ServerPlayer owner, Entity e) {
		Floated f = get(owner, e);
		if (f != null) {
			f.herdUntil = owner.level().getGameTime();
		}
	}

	/** Gives {@code e} its gravity back (it falls: fall damage from the height; a touched squadmate lands softly). */
	public static boolean release(ServerPlayer owner, Entity e) {
		return release(owner, e, false);
	}

	/** As {@link #release(ServerPlayer, Entity)}; {@code soft}: it floats down with no fall damage (Settle). */
	public static boolean release(ServerPlayer owner, Entity e, boolean soft) {
		LinkedHashMap<Integer, Floated> s = SESSIONS.get(owner.getUUID());
		if (s == null) {
			return false;
		}
		Floated f = s.remove(e.getId());
		if (f == null) {
			return false;
		}
		restore(f, e, soft);
		return true;
	}

	/** Lets everything she floats go; returns what was let go (live ones). */
	public static List<Entity> releaseAll(ServerPlayer owner, boolean soft) {
		List<Entity> out = new ArrayList<>();
		LinkedHashMap<Integer, Floated> s = SESSIONS.remove(owner.getUUID());
		if (s == null) {
			return out;
		}
		for (Floated f : s.values()) {
			Entity e = resolve(f);
			restore(f, e, soft);
			if (e != null) {
				out.add(e);
			}
		}
		return out;
	}

	private static void restore(Floated f, @Nullable Entity e, boolean soft) {
		FLOATED.remove(f.uuid);
		if (e == null) {
			// unloaded / dead: if it comes back it carries the tag and cleanup() gives it its gravity back
			Entity any = f.level.getEntity(f.uuid);
			if (any != null) {
				any.setNoGravity(false);
				any.removeTag(ZeroGravity.TAG_FLOAT);
			}
			return;
		}
		e.setNoGravity(false);
		e.removeTag(ZeroGravity.TAG_FLOAT);
		if (f.player && e instanceof ServerPlayer tp && f.grantedMayfly) {
			floatTolerance(tp, false);
		}
		if (e instanceof ItemEntity item) {
			item.setPickUpDelay(10);
		}
		if ((f.ally || soft || f.soft) && e instanceof LivingEntity le) {
			le.resetFallDistance();
			le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ZeroGravityConfig.ALLY_SLOW_FALL_TICKS, 0, false, false, true));
		}
	}

	/** Something thrown or flung keeps its momentum for a while (no hover). */
	public static void markFlung(ServerPlayer owner, Entity e, int ticks) {
		Floated f = get(owner, e);
		if (f != null) {
			f.flungUntil = owner.level().getGameTime() + ticks;
		}
	}

	/** It lets go by itself at {@code at} (the Field). */
	public static void releaseAt(ServerPlayer owner, Entity e, long at) {
		Floated f = get(owner, e);
		if (f != null) {
			f.releaseAt = at;
		}
	}

	/** It rises to {@code height} above where it is, {@code speed} a tick (the Slam). */
	public static void liftTo(ServerPlayer owner, Entity e, double height, double speed) {
		Floated f = get(owner, e);
		if (f != null) {
			f.homeY = e.getY() + height;
			f.riseSpeed = speed;
		}
	}

	private static @Nullable Floated get(ServerPlayer owner, Entity e) {
		LinkedHashMap<Integer, Floated> s = SESSIONS.get(owner.getUUID());
		return s == null ? null : s.get(e.getId());
	}

	// ================================================================ her own body

	public static boolean selfFloating(ServerPlayer owner) {
		return SELF.containsKey(owner.getUUID());
	}

	public static void startSelf(ServerPlayer p) {
		if (SELF.containsKey(p.getUUID())) {
			return;
		}
		p.resetFallDistance();
		p.setNoGravity(true);
		p.addTag(ZeroGravity.TAG_FLOAT);
		SELF.put(p.getUUID(), floatTolerance(p, true));
	}

	public static void stopSelf(ServerPlayer p) {
		Boolean granted = SELF.remove(p.getUUID());
		if (granted == null) {
			return;
		}
		p.setNoGravity(false);
		p.removeTag(ZeroGravity.TAG_FLOAT);
		if (granted) {
			floatTolerance(p, false);
		}
	}

	// ================================================================ the tick

	/** Every tick of the floater: drift, hover, leash, the Field's timers, the helpless mobs, the pink trails. */
	public static void tick(ServerPlayer owner) {
		if (SELF.containsKey(owner.getUUID())) {
			owner.resetFallDistance();
			if (!owner.isNoGravity()) {
				owner.setNoGravity(true); // something else gave it back: she still floats
			}
			if (owner.getAbilities().flying) {
				owner.getAbilities().flying = false;
				owner.onUpdateAbilities();
			}
			if (owner.tickCount % 3 == 0) {
				ZeroGravityCombat.trail(owner.serverLevel(), owner.position());
			}
		}
		LinkedHashMap<Integer, Floated> s = SESSIONS.get(owner.getUUID());
		if (s == null || s.isEmpty()) {
			return;
		}
		long now = owner.level().getGameTime();
		Iterator<Floated> it = s.values().iterator();
		while (it.hasNext()) {
			Floated f = it.next();
			Entity e = resolve(f);
			if (e == null) {
				it.remove();
				restore(f, null, false);
				continue;
			}
			if (e.level() != owner.level() || e.distanceToSqr(owner) > ZeroGravityConfig.FLOAT_LEASH * ZeroGravityConfig.FLOAT_LEASH
					|| (e instanceof LivingEntity le && ZeroGravityCombat.isBoss(le)) || (f.releaseAt != 0L && now >= f.releaseAt)
					|| (f.herdUntil != 0L && now >= f.herdUntil)) {
				it.remove();
				restore(f, e, f.herdUntil != 0L);
				continue;
			}
			if (!e.isNoGravity()) {
				e.setNoGravity(true); // a command or another power gave it back: it is still hers
			}
			f.ticks++;
			if (f.herdUntil != 0L && now >= f.flungUntil) {
				// Herding: it drifts after her, a little behind and above the ground
				Vec3 back = owner.getLookAngle().multiply(1, 0, 1);
				back = back.lengthSqr() < 1.0e-4 ? Vec3.ZERO : back.normalize().scale(-ZeroGravityConfig.HERD_DISTANCE);
				double fan = ((f.id % 5) - 2) * 0.9;
				Vec3 side = new Vec3(-back.z, 0, back.x).normalize().scale(back.lengthSqr() < 1.0e-4 ? 0 : fan);
				Vec3 goal = owner.position().add(back).add(side).add(0, 1.0, 0);
				Vec3 d = goal.subtract(e.position());
				Vec3 v = d.length() > 0.4 ? d.scale(0.12) : e.getDeltaMovement().scale(0.6);
				if (v.length() > 0.45) {
					v = v.normalize().scale(0.45);
				}
				e.setDeltaMovement(v);
				e.hurtMarked = true;
			} else if (now >= f.flungUntil && !f.player) {
				Vec3 v = e.getDeltaMovement();
				double gap = f.homeY - e.getY();
				double vy;
				if (f.riseSpeed > 0.0 && gap > 0.25) {
					vy = Math.min(f.riseSpeed, gap * 0.5 + 0.1);
				} else {
					vy = v.y * 0.85 + Mth.clamp(gap * 0.02, -0.02, 0.03);
				}
				double t = (f.ticks + f.id) * 0.07;
				double jx = Math.sin(t) * 0.004;
				double jz = Math.cos(t * 1.3) * 0.004;
				e.setDeltaMovement(v.x * 0.93 + jx, vy, v.z * 0.93 + jz);
				e.hurtMarked = true;
			}
			if (e instanceof LivingEntity le) {
				le.resetFallDistance();
				if (!f.player && now >= f.flungUntil) {
					// v0.17.1: everything she floats turns slowly
					float yaw = le.getYRot() + ZeroGravityConfig.FLOAT_SPIN_DEGREES;
					le.setYRot(yaw);
					le.yBodyRot = yaw;
					le.setYHeadRot(yaw);
				}
			}
			if (e instanceof Mob m) {
				m.getNavigation().stop();
				m.setZza(0f);
				m.setXxa(0f);
				m.setJumping(false);
				if (f.ticks % 10 == 1) {
					m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3, false, false, false));
					m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 1, false, false, false));
				}
			}
			if (e instanceof ItemEntity item && f.ticks % 20 == 0) {
				item.setPickUpDelay(40);
			}
			if (e instanceof ServerPlayer tp && tp.getAbilities().flying) {
				tp.getAbilities().flying = false;
				tp.onUpdateAbilities();
			}
			if (f.ticks % 3 == 0) {
				ZeroGravityCombat.trail(f.level, e.position());
			}
		}
	}

	// ================================================================ the safety nets

	/** ENTITY_LOAD / JOIN / respawn: a body that still carries the float tag but nobody floats gets its gravity back. */
	public static void cleanup(Entity e) {
		if (e.getTags().contains(ZeroGravity.TAG_FLOAT) && !FLOATED.containsKey(e.getUUID()) && !SELF.containsKey(e.getUUID())) {
			e.setNoGravity(false);
			e.removeTag(ZeroGravity.TAG_FLOAT);
		}
	}

	/** A floated player logs out: let them go first (their saved data must not keep NoGravity). */
	public static void releaseTarget(ServerPlayer target) {
		UUID owner = FLOATED.get(target.getUUID());
		if (owner == null) {
			return;
		}
		LinkedHashMap<Integer, Floated> s = SESSIONS.get(owner);
		if (s != null) {
			Iterator<Floated> it = s.values().iterator();
			while (it.hasNext()) {
				Floated f = it.next();
				if (f.uuid.equals(target.getUUID())) {
					it.remove();
					restore(f, target, false);
				}
			}
		}
		FLOATED.remove(target.getUUID());
	}

	/** SERVER_STOPPING: every float ends while the levels still exist (so nothing is saved weightless). */
	public static void restoreEverything(MinecraftServer server) {
		for (LinkedHashMap<Integer, Floated> s : SESSIONS.values()) {
			for (Floated f : s.values()) {
				try {
					restore(f, resolve(f), false);
				} catch (RuntimeException ignored) {
					// the level may be half torn down
				}
			}
		}
		SESSIONS.clear();
		FLOATED.clear();
		for (UUID id : Set.copyOf(SELF.keySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				stopSelf(p);
			}
		}
		SELF.clear();
	}

	/** Forget one floater's bookkeeping (after {@code stopAll} let everything go). */
	static void forget(UUID id) {
		SESSIONS.remove(id);
		SELF.remove(id);
	}

	/**
	 * Vanilla kicks a player who floats for 4 s ("flying is not enabled"). The Spider-Man / Ladybug / Pegasus fix:
	 * {@code mayfly} for the duration, {@code flying} kept off, handed back after unless another power still flies them.
	 *
	 * @return true if this call handed {@code mayfly} out (only then is it taken back)
	 */
	static boolean floatTolerance(ServerPlayer p, boolean on) {
		if (p.getAbilities().instabuild || p.isSpectator()) {
			return false;
		}
		if (on) {
			if (!p.getAbilities().mayfly) {
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
				return true;
			}
			return false;
		}
		if (p.getAbilities().mayfly && !com.projecthero.mod.hero.power.HeroFlight.isFlying(p) && !com.projecthero.mod.power.ThorPowers.isFlying(p)
				&& !p.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false) && !p.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		return false;
	}
}
