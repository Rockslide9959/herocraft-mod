package com.projecthero.mod.ironman.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1 comic kit: everything a comic move leaves RUNNING -- per-player statuses (the Energy Shields, the Hydraulic
 * Brace, the cloaks, held targets, the glide, the meteor dive, channelled beams, combo steps, JARVIS-scanned and marked
 * targets, pulse-bolt stacks), world areas (smoke, oil slicks, whirlpools, zero-G fields, holo-decoys) and delayed
 * follow-ups (missile volleys, the orbital strike, the depth charge, the replacement pod).
 *
 * <p>All of it is static server state keyed by player UUID, cleared by {@link #clearSessionState} (ServerStateReset) and
 * per player on {@link #forget}. Per-player statuses tick from {@code IronManSuitTicker} while the suit is powered
 * ({@link #tick}) and stop with {@link #shutDown}; areas and delayed tasks tick on the server tick.
 */
public final class IronManComicState {
	private IronManComicState() {
	}

	// ------------------------------------------------------------------ per-player runtime

	enum HoldKind { TRACTOR, TETHER, GRAB_POUND, GRAB_THROW, ANCHOR }

	static final class Rt {
		long shieldUntil;
		float shieldFactor = 1f;
		long braceUntil;
		long cloakUntil;
		long jamUntil;
		long glideUntil;
		long skatesUntil;
		boolean diving;
		double diveStartY;
		long diveStart;
		int comboStep;
		long comboAt;
		final Map<Integer, Long> scanned = new HashMap<>();
		int markedTarget = -1;
		int markedHits;
		long markedUntil;
		final Map<Integer, Integer> pulseStacks = new HashMap<>();
		final Map<Integer, Long> pulseUntil = new HashMap<>();
		// a held target (tractor beam, gravity tether, grabs, ballast anchor)
		HoldKind hold;
		int holdTarget = -1;
		long holdStart;
		long holdUntil;
		Vec3 holdAnchor;
		// a thrown target that hurts when it hits something
		int thrownTarget = -1;
		long thrownUntil;
		float thrownDamage;
		// a channelled beam (Hulkbuster mega-beam)
		String channel;
		long channelUntil;
	}

	private static final Map<UUID, Rt> RT = new HashMap<>();

	static Rt rt(ServerPlayer p) {
		return RT.computeIfAbsent(p.getUUID(), k -> new Rt());
	}

	static Rt peek(ServerPlayer p) {
		return RT.get(p.getUUID());
	}

	// ------------------------------------------------------------------ areas + delayed tasks

	enum AreaKind { SMOKE, SLICK, BURNING_SLICK, WHIRLPOOL, ZERO_G, DECOY, JAM_FIELD }

	static final class Area {
		final UUID owner;
		final ResourceKey<Level> dim;
		Vec3 centre;
		final double radius;
		long until;
		AreaKind kind;
		final long born;

		Area(UUID owner, ResourceKey<Level> dim, Vec3 centre, double radius, long born, long until, AreaKind kind) {
			this.owner = owner;
			this.dim = dim;
			this.centre = centre;
			this.radius = radius;
			this.born = born;
			this.until = until;
			this.kind = kind;
		}
	}

	private record Task(long due, UUID owner, Consumer<ServerPlayer> run) {
	}

	private record Stun(Mob mob, long until) {
	}

	private static final List<Area> AREAS = new ArrayList<>();
	private static final List<Task> TASKS = new ArrayList<>();
	private static final List<Stun> STUNS = new ArrayList<>();

	private static boolean initialized;

	/** Registers the server tick (areas, delayed tasks, stuns). Called once from {@code IronManDamage.initialize}. */
	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(IronManComicState::serverTick);
	}

	public static void clearSessionState() {
		RT.clear();
		AREAS.clear();
		TASKS.clear();
		STUNS.clear();
	}

	public static void forget(UUID player) {
		RT.remove(player);
		AREAS.removeIf(a -> a.owner.equals(player));
		TASKS.removeIf(t -> t.owner.equals(player));
	}

	/** Run {@code run} for {@code p} in {@code ticks} ticks (if they are still online then). */
	static void later(ServerPlayer p, int ticks, Consumer<ServerPlayer> run) {
		TASKS.add(new Task(p.level().getGameTime() + Math.max(1, ticks), p.getUUID(), run));
	}

	static Area area(ServerPlayer p, AreaKind kind, Vec3 centre, double radius, int ticks) {
		long now = p.level().getGameTime();
		Area a = new Area(p.getUUID(), p.level().dimension(), centre, radius, now, now + ticks, kind);
		AREAS.add(a);
		return a;
	}

	/** The owner's live areas of {@code kind} in their dimension. */
	static List<Area> areasOf(ServerPlayer p, AreaKind kind) {
		List<Area> out = new ArrayList<>();
		long now = p.level().getGameTime();
		for (Area a : AREAS) {
			if (a.owner.equals(p.getUUID()) && a.kind == kind && a.until > now && a.dim.equals(p.level().dimension())) {
				out.add(a);
			}
		}
		return out;
	}

	/** EMP-stun a mob: it loses its target and barely moves until {@code ticks} run out. */
	static void stun(Mob mob, int ticks) {
		STUNS.add(new Stun(mob, mob.level().getGameTime() + ticks));
		mob.setTarget(null);
		mob.getNavigation().stop();
		mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, true, true));
		mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 3, false, true, true));
	}

	/** Tell the wearer's own client to outline these entities for {@code ticks} (private -- see IronManHighlight). */
	static void privateMark(ServerPlayer p, List<? extends Entity> entities, int ticks) {
		if (entities.isEmpty()) {
			return;
		}
		List<Integer> ids = new ArrayList<>();
		for (Entity e : entities) {
			if (ids.size() < 256) {
				ids.add(e.getId());
			}
		}
		ServerPlayNetworking.send(p, new com.projecthero.mod.network.IronManComicMarkPayload(ids, ticks));
	}

	private static void serverTick(MinecraftServer server) {
		if (TASKS.isEmpty() && AREAS.isEmpty() && STUNS.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		if (!TASKS.isEmpty()) {
			List<Task> due = new ArrayList<>();
			for (Iterator<Task> it = TASKS.iterator(); it.hasNext();) {
				Task t = it.next();
				if (t.due <= now) {
					it.remove();
					due.add(t);
				}
			}
			for (Task t : due) {
				ServerPlayer owner = server.getPlayerList().getPlayer(t.owner);
				if (owner != null && owner.isAlive()) {
					try {
						t.run.accept(owner);
					} catch (RuntimeException e) {
						com.projecthero.mod.ProjectHeroMod.LOGGER.warn("[ProjectHero] comic move follow-up failed", e);
					}
				}
			}
		}
		for (Iterator<Stun> it = STUNS.iterator(); it.hasNext();) {
			Stun s = it.next();
			if (!s.mob.isAlive() || s.mob.isRemoved() || s.until <= s.mob.level().getGameTime()) {
				it.remove();
			} else {
				s.mob.setTarget(null);
				if (s.mob.tickCount % 5 == 0 && s.mob.level() instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, s.mob.getX(), s.mob.getY() + s.mob.getBbHeight() * 0.8, s.mob.getZ(),
							3, 0.3, 0.2, 0.3, 0.05);
				}
			}
		}
		for (Iterator<Area> it = AREAS.iterator(); it.hasNext();) {
			Area a = it.next();
			ServerLevel level = server.getLevel(a.dim);
			if (level == null || a.until <= level.getGameTime()) {
				it.remove();
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(a.owner);
			tickArea(level, owner, a);
		}
	}

	private static void tickArea(ServerLevel level, ServerPlayer owner, Area a) {
		long now = level.getGameTime();
		long age = now - a.born;
		Vec3 c = a.centre;
		List<LivingEntity> inside = owner == null ? List.of() : IronManComicFx.around(owner, c, a.radius);
		switch (a.kind) {
			case SMOKE -> {
				if (age % 2 == 0) {
					level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 1.0, c.z, 6, a.radius * 0.5, 0.8, a.radius * 0.5, 0.01);
					level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.8, c.z, 10, a.radius * 0.55, 0.9, a.radius * 0.55, 0.01);
				}
				for (LivingEntity e : inside) {
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, true));
					if (e instanceof Mob m && owner != null && m.getTarget() == owner) {
						m.setTarget(null);
					}
				}
			}
			case SLICK, BURNING_SLICK -> {
				boolean burning = a.kind == AreaKind.BURNING_SLICK;
				if (age % 3 == 0) {
					level.sendParticles(burning ? ParticleTypes.FLAME : ParticleTypes.SQUID_INK, c.x, c.y + 0.1, c.z,
							burning ? 14 : 8, a.radius * 0.5, 0.02, a.radius * 0.5, 0.01);
					if (burning) {
						level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.1, c.z, 2, a.radius * 0.4, 0.02, a.radius * 0.4, 0.0);
					}
				}
				for (LivingEntity e : inside) {
					if (Math.abs(e.getY() - c.y) > 2.0) {
						continue;
					}
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false, true));
					if (e.onGround() && e.getRandom().nextInt(10) == 0) {
						// slipping about on the slick
						e.setDeltaMovement(e.getDeltaMovement().add((e.getRandom().nextDouble() - 0.5) * 0.6, 0,
								(e.getRandom().nextDouble() - 0.5) * 0.6));
						e.hurtMarked = true;
					}
					if (burning && owner != null && age % 10 == 0) {
						IronManComicFx.burn(owner, e, 3f, 80);
					}
				}
			}
			case WHIRLPOOL -> {
				boolean wet = level.getFluidState(net.minecraft.core.BlockPos.containing(c)).isSource()
						|| level.isWaterAt(net.minecraft.core.BlockPos.containing(c));
				double pull = wet ? 0.35 : 0.2;
				double spin = age * 0.35;
				for (int i = 0; i < 3; i++) {
					double r = a.radius * (1.0 - ((age + i * 7) % 20) / 20.0);
					double ang = spin + i * 2.1;
					level.sendParticles(wet ? ParticleTypes.BUBBLE_COLUMN_UP : ParticleTypes.CLOUD, c.x + Math.cos(ang) * r, c.y + 0.3,
							c.z + Math.sin(ang) * r, 2, 0.05, 0.1, 0.05, 0.01);
				}
				if (age % 4 == 0) {
					level.sendParticles(wet ? ParticleTypes.SPLASH : ParticleTypes.SWEEP_ATTACK, c.x, c.y + 0.4, c.z, 4, a.radius * 0.3, 0.1,
							a.radius * 0.3, 0.0);
				}
				for (LivingEntity e : inside) {
					Vec3 to = c.subtract(e.position());
					Vec3 tangent = new Vec3(-to.z, 0, to.x).normalize().scale(0.12);
					e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(to.normalize().scale(pull)).add(tangent));
					e.hurtMarked = true;
					if (owner != null && age % 20 == 0) {
						IronManComicFx.hit(owner, e, 3f);
					}
				}
			}
			case ZERO_G -> {
				if (age % 3 == 0) {
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1.0, c.z, 10, a.radius * 0.5, 1.2, a.radius * 0.5, 0.02);
					IronManComicFx.ring(level, c.add(0, 0.2, 0), a.radius, ParticleTypes.END_ROD, 20);
				}
				for (LivingEntity e : inside) {
					e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 15, 0, false, false, true));
					e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
					e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 1, false, false, true));
					if (e instanceof Mob m) {
						m.getNavigation().stop();
					}
				}
			}
			case DECOY -> {
				// a shimmering hologram of the suit: a column of light where the decoy stands
				if (age % 2 == 0) {
					level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 3, 0.25, 0.8, 0.25, 0.0);
					level.sendParticles(ParticleTypes.GLOW, c.x, c.y + 1.2, c.z, 2, 0.2, 0.6, 0.2, 0.0);
				}
				if (owner != null) {
					for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(20.0),
							m -> m.isAlive() && m.getTarget() == owner)) {
						m.setTarget(null);
						m.getNavigation().moveTo(c.x, c.y, c.z, 1.1);
					}
				}
			}
			case JAM_FIELD -> {
				if (owner != null) {
					a.centre = owner.position();
					for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(owner.position(), owner.position()).inflate(a.radius),
							m -> m.isAlive() && m.getTarget() == owner)) {
						m.setTarget(null);
					}
					if (age % 6 == 0) {
						IronManComicFx.ring(level, owner.position().add(0, 1.0, 0), 1.2 + (age % 18) / 6.0, ParticleTypes.ELECTRIC_SPARK, 12);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ damage hooks (IronManDamage)

	/** Outgoing: JARVIS-scanned targets take +25%, a Marked Shot target crits (x1.5) for its next 3 hits. */
	public static float outgoingMultiplier(ServerPlayer attacker, LivingEntity target) {
		Rt r = peek(attacker);
		if (r == null) {
			return 1f;
		}
		long now = attacker.level().getGameTime();
		float mult = 1f;
		Long scannedUntil = r.scanned.get(target.getId());
		if (scannedUntil != null) {
			if (scannedUntil > now) {
				mult *= 1.25f;
			} else {
				r.scanned.remove(target.getId());
			}
		}
		if (r.markedTarget == target.getId() && r.markedHits > 0 && r.markedUntil > now) {
			r.markedHits--;
			mult *= 1.5f;
			if (attacker.level() instanceof ServerLevel sl) {
				sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 14, 0.3, 0.3,
						0.3, 0.2);
				sl.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 8,
						0.3, 0.3, 0.3, 0.2);
			}
		}
		return mult;
	}

	/** Incoming: the share of a hit that gets past the comic Energy Shield / Hydraulic Brace (1 = all of it). */
	public static float incomingFactor(ServerPlayer player, IronManSuit suit, DamageSource source) {
		Rt r = peek(player);
		if (r == null || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return 1f;
		}
		long now = player.level().getGameTime();
		float f = 1f;
		if (r.shieldUntil > now) {
			f *= r.shieldFactor;
			IronManSounds.play(player, IronManSounds.SHIELD_DEFLECT, 0.6f, 1.1f);
		}
		if (r.braceUntil > now) {
			f *= IronManComicMoves.BRACE_FACTOR;
		}
		return f;
	}

	/** The wearer attacked something: a cloak breaks. */
	public static void onOwnerAttack(ServerPlayer player) {
		Rt r = peek(player);
		if (r != null && r.cloakUntil > player.level().getGameTime()) {
			endCloak(player, r);
		}
	}

	public static boolean shielded(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.shieldUntil > p.level().getGameTime();
	}

	public static boolean braced(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.braceUntil > p.level().getGameTime();
	}

	public static boolean cloaked(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.cloakUntil > p.level().getGameTime();
	}

	public static boolean gliding(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.glideUntil > p.level().getGameTime();
	}

	public static boolean holding(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.hold != null;
	}

	public static boolean diving(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.diving;
	}

	/** Gametest / HUD: is {@code target} JARVIS-scanned by {@code p} right now? */
	public static boolean scanned(ServerPlayer p, LivingEntity target) {
		Rt r = peek(p);
		Long until = r == null ? null : r.scanned.get(target.getId());
		return until != null && until > p.level().getGameTime();
	}

	static void endCloak(ServerPlayer p, Rt r) {
		r.cloakUntil = 0L;
		MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
		if (inv != null && inv.isAmbient() && !inv.isVisible()) {
			p.removeEffect(MobEffects.INVISIBILITY);
		}
		if (p.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.3, 0.6, 0.3, 0.02);
		}
	}

	// ------------------------------------------------------------------ per-player tick

	/** Per tick while the suit is worn and powered. */
	public static void tick(ServerPlayer p, IronManSuit suit) {
		Rt r = peek(p);
		if (r == null) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		long now = level.getGameTime();

		if (r.shieldUntil > now) {
			IronManAbilityFx.hold(p, IronManAbilityFx.BARRIER);
			if (now % 20 == 0) {
				IronManSounds.play(p, IronManSounds.SHIELD_HUM, 0.4f, 1.0f);
			}
		} else if (r.shieldUntil != 0L) {
			r.shieldUntil = 0L;
			IronManSounds.play(p, IronManSounds.SHIELD_DOWN, 0.7f, 1.0f);
		}
		if (r.braceUntil > now) {
			IronManAbilityFx.hold(p, IronManAbilityFx.COMIC_GUARD);
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 1, true, false, false));
			if (now % 8 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + p.getBbHeight() * 0.5, p.getZ(), 4, 0.6, 0.8, 0.6, 0.02);
			}
		}
		if (r.cloakUntil > now) {
			p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10, 0, true, false, false));
			for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32.0), m -> m.getTarget() == p)) {
				m.setTarget(null);
			}
		} else if (r.cloakUntil != 0L) {
			endCloak(p, r);
		}
		if (r.skatesUntil > now && p.onGround() && now % 2 == 0) {
			level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.05, p.getZ(), 2, 0.2, 0.0, 0.2, 0.01);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.05, p.getZ(), 1, 0.2, 0.0, 0.2, 0.05);
		}
		if (r.glideUntil > now) {
			IronManComicMoves.tickGlide(p, r);
		} else if (r.glideUntil != 0L) {
			IronManComicMoves.endGlide(p, r);
		}
		if (r.diving) {
			IronManComicMoves.tickDive(p, suit, r);
		}
		if (r.hold != null) {
			IronManComicMoves.tickHold(p, suit, r);
		}
		if (r.thrownTarget >= 0) {
			IronManComicMoves.tickThrown(p, r);
		}
		if (r.channel != null) {
			IronManComicMoves.tickChannel(p, suit, r);
		}
		if (r.comboStep > 0 && now - r.comboAt > 40) {
			r.comboStep = 0;
		}
		if (now % 100 == 0) {
			r.scanned.values().removeIf(t -> t <= now);
			r.pulseUntil.entrySet().removeIf(en -> {
				if (en.getValue() <= now) {
					r.pulseStacks.remove(en.getKey());
					return true;
				}
				return false;
			});
		}
	}

	/** The suit came off / lost power: drop everything running (statuses end, a held target is let go). */
	public static void shutDown(ServerPlayer p) {
		Rt r = peek(p);
		if (r == null) {
			return;
		}
		if (r.cloakUntil != 0L) {
			endCloak(p, r);
		}
		if (r.glideUntil != 0L) {
			IronManComicMoves.endGlide(p, r);
		}
		r.shieldUntil = 0L;
		r.braceUntil = 0L;
		r.skatesUntil = 0L;
		r.diving = false;
		r.hold = null;
		r.holdTarget = -1;
		r.channel = null;
		r.comboStep = 0;
		IronManComicRouter.releaseAll(p);
	}

	/** Pull metal items toward {@code to} (the electromagnet / magnet pull). */
	static int pullItems(ServerPlayer p, Vec3 to, double radius, double speed) {
		int n = 0;
		for (ItemEntity item : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(radius))) {
			IronManComicFx.pull(item, to, speed);
			n++;
		}
		return n;
	}

	/** Is the wearer actually wearing a suit right now (statuses only matter then). */
	static boolean suited(ServerPlayer p) {
		return IronManArmor.wornSuitId(p) != null;
	}

	/** Gametest helper: run every due follow-up and area once, as the server tick would. */
	public static void tickServerForTests(MinecraftServer server) {
		serverTick(server);
	}

	/** Gametest helper: give {@code p}'s Energy Shield / Brace state a known value. */
	public static void setShieldForTests(ServerPlayer p, int ticks, float factor) {
		Rt r = rt(p);
		r.shieldUntil = p.level().getGameTime() + ticks;
		r.shieldFactor = factor;
	}
}
