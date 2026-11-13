package com.projecthero.mod.raven;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.beamclash.BeamClash;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.raven.data.RavenState;
import com.projecthero.mod.raven.network.RavenFxPayload;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven revamp: the ten spells of the Spell Wheel ({@link RavenSpell}; {@code docs/RAVEN_REVAMP_DESIGN.md}). V tapped casts
 * the picked one ({@link #castSelected}); while she is Unsettled, 1 in 8 casts misfires ({@link #misfires}): a spell cast at a spot
 * lands at a random spot near it, any other spell slips out of her hands as a small burst somewhere near her (paid for, half the
 * cooldown). The running parts (the vortex, the detonation marks, the drain beam, the healing channel, the sanctuary, the rift's
 * step, the veil, the pulled items) tick in {@link #tick}; all of it per player in maps that hold entities, cleared by
 * {@link #clearSessionState} (from {@code ServerStateReset} through {@code Raven}).
 */
public final class RavenSpells {
	private RavenSpells() {
	}

	private record Vortex(Vec3 at, long start) {
	}

	private static final class Detonation {
		final long at;
		final List<LivingEntity> marked;
		float hurt;

		Detonation(long at, List<LivingEntity> marked) {
			this.at = at;
			this.marked = marked;
		}
	}

	private record Devour(LivingEntity target, long start) {
	}

	private record Channel(Vec3 at, long start) {
	}

	/** One Astral Sanctuary: where, until when, how much lighter hits are inside. */
	public record Sanctuary(UUID owner, Vec3 at, long until, float reduction) {
	}

	private record Rift(Vec3 to, long at) {
	}

	private record Pull(List<ItemEntity> items, long until) {
	}

	private static final Map<UUID, Vortex> VORTICES = new ConcurrentHashMap<>();
	private static final Map<UUID, Detonation> DETONATIONS = new ConcurrentHashMap<>();
	private static final Map<UUID, Devour> DEVOURS = new ConcurrentHashMap<>();
	private static final Map<UUID, Channel> CHANNELS = new ConcurrentHashMap<>();
	private static final Map<UUID, Sanctuary> SANCTUARIES = new ConcurrentHashMap<>();
	private static final Map<UUID, Rift> RIFTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Pull> PULLS = new ConcurrentHashMap<>();

	/** For the tests: misfires so far, projectiles stopped by a sanctuary. */
	public static int misfired;
	public static int stopped;

	static void clearSessionState() {
		VORTICES.clear();
		DETONATIONS.clear();
		DEVOURS.clear();
		CHANNELS.clear();
		SANCTUARIES.clear();
		RIFTS.clear();
		PULLS.clear();
	}

	static void forget(UUID id) {
		VORTICES.remove(id);
		DETONATIONS.remove(id);
		DEVOURS.remove(id);
		CHANNELS.remove(id);
		SANCTUARIES.remove(id);
		RIFTS.remove(id);
		PULLS.remove(id);
	}

	static void endAll(ServerPlayer p) {
		UUID id = p.getUUID();
		VORTICES.remove(id);
		DETONATIONS.remove(id);
		if (DEVOURS.remove(id) != null) {
			RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.BEAM_END, p.getId(), Vec3.ZERO, Vec3.ZERO, 0));
		}
		CHANNELS.remove(id);
		SANCTUARIES.remove(id);
		RIFTS.remove(id);
		PULLS.remove(id);
	}

	// ---------------------------------------------------------------- casting

	/** Energy a spell needs right now (Debris Storm is free while blocks orbit; Demonic Ascension's -25%). Client-safe. */
	public static float costNow(Player p, RavenSpell s) {
		if (s == RavenSpell.DEBRIS_STORM) {
			RavenState st = Raven.peek(p);
			if (st != null && st.debris > 0) {
				return 0f;
			}
		}
		return s.cost * Raven.costMult(p);
	}

	/** V tapped: the picked spell. */
	public static boolean castSelected(ServerPlayer p) {
		return cast(p, Raven.selectedSpell(p), null);
	}

	/** The wheel's pick: saved (synced) for the HUD's V box and the next tap. */
	public static void select(ServerPlayer p, int ordinal) {
		if (ordinal < 0 || ordinal >= RavenSpell.count()) {
			return;
		}
		RavenState n = Raven.state(p).copy();
		n.spell = ordinal;
		Raven.save(p, n);
	}

	/** An Unsettled cast misfires one time in {@link RavenConfig#MISFIRE_ONE_IN}; a calm one never. */
	public static boolean misfires(Player p, RandomSource random) {
		return Raven.unsettled(p) && random.nextInt(RavenConfig.MISFIRE_ONE_IN) == 0;
	}

	/** Casts {@code s}. {@code forceMisfire}: null rolls for it (the tests force it either way). */
	public static boolean cast(ServerPlayer p, RavenSpell s, Boolean forceMisfire) {
		if (s == RavenSpell.DEBRIS_STORM && RavenMoves.debrisCount(p) > 0 && !Raven.meditating(p)) {
			return RavenMoves.fireDebris(p); // tapped again: fire one
		}
		if (!RavenMoves.ready(p, s.id)) {
			return false;
		}
		boolean misfire = forceMisfire != null ? forceMisfire : misfires(p, p.getRandom());
		if (misfire && !s.placed) {
			fizzle(p, s);
			return true;
		}
		boolean ok = switch (s) {
			case VOID_VORTEX -> voidVortex(p, misfire);
			case SHADOW_BINDING -> shadowBinding(p, misfire);
			case SOUL_DETONATION -> soulDetonation(p);
			case SOUL_DEVOUR -> soulDevour(p);
			case DEBRIS_STORM -> debrisStorm(p);
			case DIMENSIONAL_RIFT -> dimensionalRift(p, misfire);
			case SOUL_RESTORATION -> soulRestoration(p);
			case TELEKINETIC_PULL -> telekineticPull(p);
			case ASTRAL_SANCTUARY -> astralSanctuary(p);
			case SHADOW_VEIL -> shadowVeil(p);
		};
		if (ok && misfire) {
			misfired++;
			Raven.say(p, "message.projecthero.raven.misfire", ChatFormatting.GOLD);
		}
		if (ok) {
			Raven.unsettle(p, RavenConfig.CAST_COMPOSURE);
		}
		return ok;
	}

	private static void pay(ServerPlayer p, RavenSpell s) {
		RavenMoves.pay(p, s.id, s.cost, s.cooldown, s.combat);
	}

	/** A misfire of a spell not cast at a spot: it slips away as a burst somewhere near her. Paid for, half the cooldown. */
	private static void fizzle(ServerPlayer p, RavenSpell s) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 at = scatter(p, p.position()).add(0, 1.0, 0);
		for (LivingEntity t : HeroTargets.harmable(level, p, new AABB(at, at).inflate(2.0))) {
			Raven.hit(p, t, RavenConfig.MISFIRE_DAMAGE);
		}
		RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.BURST, p.getId(), at, new Vec3(2.0, 1, 0), 10));
		level.sendParticles(Raven.dust(Raven.VIOLET, 1.4f), at.x, at.y, at.z, 24, 0.6, 0.6, 0.6, 0.0);
		level.playSound(null, at.x, at.y, at.z, RavenSounds.BURST, SoundSource.PLAYERS, 0.4f, 1.3f);
		RavenMoves.pay(p, s.id, s.cost, s.cooldown / 2, false);
		misfired++;
		Raven.say(p, "message.projecthero.raven.misfire_lost", ChatFormatting.GOLD, Component.translatable(s.langKey()));
	}

	/** A random spot within {@link RavenConfig#MISFIRE_SPREAD} of {@code at}, on the same level. */
	static Vec3 scatter(ServerPlayer p, Vec3 at) {
		RandomSource r = p.getRandom();
		double ang = r.nextDouble() * Math.PI * 2;
		double d = 2.0 + r.nextDouble() * (RavenConfig.MISFIRE_SPREAD - 2.0);
		return at.add(Math.cos(ang) * d, 0, Math.sin(ang) * d);
	}

	// ---------------------------------------------------------------- combat spells

	/** Void Vortex: a vortex where she looks for 4 s, pulling creatures in (3 a second), then it blows (10). */
	static boolean voidVortex(ServerPlayer p, boolean misfire) {
		Vec3 at = RavenMoves.groundAim(p, RavenConfig.VORTEX_RANGE);
		if (misfire) {
			at = scatter(p, at);
		}
		at = at.add(0, 1.2, 0);
		VORTICES.put(p.getUUID(), new Vortex(at, Raven.now(p)));
		RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.VORTEX, p.getId(), at, new Vec3(RavenConfig.VORTEX_PULL_RADIUS, 0, 0), RavenConfig.VORTEX_TICKS));
		ServerLevel level = (ServerLevel) p.level();
		level.playSound(null, at.x, at.y, at.z, RavenSounds.VORTEX, SoundSource.PLAYERS, 0.5f, 0.5f);
		pay(p, RavenSpell.VOID_VORTEX);
		return true;
	}

	private static void vortexTick(ServerPlayer p, long now, ServerLevel level) {
		Vortex v = VORTICES.get(p.getUUID());
		if (v == null) {
			return;
		}
		long age = now - v.start;
		double r = RavenConfig.VORTEX_PULL_RADIUS;
		for (LivingEntity t : HeroTargets.harmable(level, p, new AABB(v.at, v.at).inflate(r))) {
			Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
			Vec3 to = v.at.subtract(c);
			double d = to.length();
			if (d > r) {
				continue;
			}
			if (!Raven.isBoss(t) && d > 0.6) {
				Vec3 swirl = new Vec3(-to.z, 0, to.x).normalize().scale(0.05);
				Vec3 pull = to.normalize().scale(RavenConfig.VORTEX_PULL * (0.6 + 0.4 * (1 - d / r))).add(swirl);
				t.setDeltaMovement(t.getDeltaMovement().scale(0.5).add(pull));
				t.hurtMarked = true;
				t.resetFallDistance();
			}
			if (age % 20 == 19 && d <= 3.5) {
				Raven.hit(p, t, RavenConfig.VORTEX_TICK_DAMAGE);
			}
		}
		if (age % 2 == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, v.at.x, v.at.y, v.at.z, 6, 1.6, 0.6, 1.6, 0.1);
			level.sendParticles(Raven.dust(Raven.VOID, 1.5f), v.at.x, v.at.y, v.at.z, 4, 0.5, 0.4, 0.5, 0.0);
		}
		if (age % 20 == 0 && age > 0) {
			level.playSound(null, v.at.x, v.at.y, v.at.z, RavenSounds.VORTEX, SoundSource.PLAYERS, 0.3f, 0.45f);
		}
		if (age >= RavenConfig.VORTEX_TICKS) {
			VORTICES.remove(p.getUUID());
			double br = RavenConfig.VORTEX_BLAST_RADIUS;
			for (LivingEntity t : HeroTargets.harmable(level, p, new AABB(v.at, v.at).inflate(br))) {
				if (t.position().add(0, t.getBbHeight() * 0.5, 0).distanceTo(v.at) <= br + t.getBbWidth() * 0.5) {
					Raven.hit(p, t, RavenConfig.VORTEX_BLAST_DAMAGE);
					if (!Raven.isBoss(t)) {
						Vec3 out = t.position().subtract(v.at).multiply(1, 0, 1);
						out = out.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : out.normalize();
						t.push(out.x * 0.7, 0.5, out.z * 0.7);
						t.hurtMarked = true;
					}
				}
			}
			RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.BURST, p.getId(), v.at, new Vec3(br, 0, 0), 14));
			level.sendParticles(ParticleTypes.EXPLOSION, v.at.x, v.at.y, v.at.z, 2, 0.5, 0.5, 0.5, 0);
			level.sendParticles(Raven.dust(Raven.VIOLET, 2.0f), v.at.x, v.at.y, v.at.z, 40, 1.5, 1.0, 1.5, 0.0);
			level.playSound(null, v.at.x, v.at.y, v.at.z, RavenSounds.BURST, SoundSource.PLAYERS, 0.5f, 0.6f);
		}
	}

	/** For the tests: her vortex is open. */
	public static boolean vortexOpen(ServerPlayer p) {
		return VORTICES.containsKey(p.getUUID());
	}

	/** Shadow Binding: claws rise in a 4-block circle where she looks: 6, rooted 3 s (bosses only slowed). */
	static boolean shadowBinding(ServerPlayer p, boolean misfire) {
		Vec3 at = RavenMoves.groundAim(p, RavenConfig.BIND_RANGE);
		if (misfire) {
			at = scatter(p, at);
		}
		ServerLevel level = (ServerLevel) p.level();
		double r = RavenConfig.BIND_RADIUS;
		for (LivingEntity t : HeroTargets.harmable(level, p, new AABB(at, at).inflate(r, 2.5, r))) {
			if (t.position().multiply(1, 0, 1).distanceTo(at.multiply(1, 0, 1)) > r + t.getBbWidth() * 0.5) {
				continue;
			}
			Raven.hit(p, t, RavenConfig.BIND_DAMAGE);
			if (Raven.isBoss(t)) {
				t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RavenConfig.BIND_TICKS, 3), p);
			} else {
				RavenMoves.pin(p, t, RavenConfig.BIND_TICKS);
			}
			RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.CLAWS, t.getId(), t.position(), new Vec3(t.getBbWidth(), t.getBbHeight(), 0),
					RavenConfig.BIND_TICKS));
		}
		RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.BIND, p.getId(), at.add(0, 0.05, 0), new Vec3(r, 0, 0), RavenConfig.BIND_TICKS));
		level.sendParticles(Raven.dust(Raven.VOID, 1.6f), at.x, at.y + 0.2, at.z, 30, r * 0.5, 0.1, r * 0.5, 0.0);
		level.playSound(null, at.x, at.y, at.z, RavenSounds.CLAWS, SoundSource.PLAYERS, 0.6f, 0.55f);
		pay(p, RavenSpell.SHADOW_BINDING);
		return true;
	}

	/** Soul Detonation: everything within 6 blocks is marked; 1.5 s later the marks blow (up to 24, more the nearer to her). */
	static boolean soulDetonation(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		double r = RavenConfig.DETONATE_RADIUS;
		List<LivingEntity> marked = new ArrayList<>();
		for (LivingEntity t : HeroTargets.harmable(level, p, p.getBoundingBox().inflate(r))) {
			if (t.distanceTo(p) <= r + t.getBbWidth() * 0.5) {
				marked.add(t);
				RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.MARK, t.getId(), t.position(), Vec3.ZERO, RavenConfig.DETONATE_DELAY));
			}
		}
		DETONATIONS.put(p.getUUID(), new Detonation(Raven.now(p) + RavenConfig.DETONATE_DELAY, marked));
		RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.CHARGE, p.getId(), p.position(), new Vec3(r, 1, 0), RavenConfig.DETONATE_DELAY));
		Raven.sound(p, RavenSounds.DETONATE_CHARGE, 0.45f, 0.6f);
		pay(p, RavenSpell.SOUL_DETONATION);
		return true;
	}

	private static void detonationTick(ServerPlayer p, long now, ServerLevel level) {
		Detonation d = DETONATIONS.get(p.getUUID());
		if (d == null || now < d.at) {
			return;
		}
		DETONATIONS.remove(p.getUUID());
		double r = RavenConfig.DETONATE_RADIUS;
		for (LivingEntity t : d.marked) {
			if (!t.isAlive() || t.level() != p.level()) {
				continue;
			}
			double k = Math.min(1.0, t.distanceTo(p) / r);
			float dmg = RavenConfig.DETONATE_MAX * (float) (1.0 - (1.0 - RavenConfig.DETONATE_EDGE) * k);
			Raven.hit(p, t, dmg);
			Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
			RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.BURST, t.getId(), c, new Vec3(1.6, 0, 0), 10));
			level.sendParticles(Raven.dust(Raven.VIOLET, 1.8f), c.x, c.y, c.z, 20, 0.4, 0.5, 0.4, 0.0);
			level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 3, 0.2, 0.3, 0.2, 0.03);
		}
		Vec3 c = p.position().add(0, 1.0, 0);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.DETONATE, SoundSource.PLAYERS, 0.55f, 0.55f);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.BURST, SoundSource.PLAYERS, 0.4f, 0.8f);
	}

	/** For the tests: a Soul Detonation is counting down. */
	public static boolean detonating(ServerPlayer p) {
		return DETONATIONS.containsKey(p.getUUID());
	}

	/** Soul Devour: a drain beam on the creature she looks at for 3 s (4 every half second, half of it heals her). */
	static boolean soulDevour(ServerPlayer p) {
		LivingEntity t = RavenMoves.aimed(p, RavenConfig.DEVOUR_RANGE);
		if (t == null) {
			Raven.say(p, "message.projecthero.raven.no_target", ChatFormatting.GRAY);
			return false;
		}
		DEVOURS.put(p.getUUID(), new Devour(t, Raven.now(p)));
		RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.BEAM, p.getId(), new Vec3(t.getId(), 0, 0), Vec3.ZERO, RavenConfig.DEVOUR_TICKS));
		Raven.sound(p, RavenSounds.DEVOUR, 0.45f, 0.6f);
		pay(p, RavenSpell.SOUL_DEVOUR);
		return true;
	}

	private static void devourTick(ServerPlayer p, long now, ServerLevel level) {
		Devour d = DEVOURS.get(p.getUUID());
		if (d == null) {
			return;
		}
		long age = now - d.start;
		LivingEntity t = d.target;
		boolean broken = !t.isAlive() || t.level() != p.level() || t.distanceTo(p) > RavenConfig.DEVOUR_RANGE || !p.hasLineOfSight(t)
				|| Raven.meditating(p);
		if (broken || age >= RavenConfig.DEVOUR_TICKS) {
			DEVOURS.remove(p.getUUID());
			RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.BEAM_END, p.getId(), Vec3.ZERO, Vec3.ZERO, 0));
			if (broken && t.isAlive() && age < RavenConfig.DEVOUR_TICKS) {
				Raven.say(p, "message.projecthero.raven.devour_broken", ChatFormatting.GRAY);
			}
			return;
		}
		Vec3 eye = p.getEyePosition().add(0, -0.35, 0);
		Vec3 tc = t.position().add(0, t.getBbHeight() * 0.55, 0);
		BeamClash.report(p, eye, tc.subtract(eye), RavenConfig.DEVOUR_RANGE, 1.0f, 0x8A3CD8); // a beam: it can clash
		if (age % 3 == 0) {
			// motes drawn out of it toward her
			Vec3 mid = tc.lerp(eye, 0.3 + p.getRandom().nextDouble() * 0.5);
			level.sendParticles(ParticleTypes.SCULK_SOUL, mid.x, mid.y, mid.z, 1, 0.1, 0.1, 0.1, 0.0);
		}
		if (age % RavenConfig.DEVOUR_PULSE == RavenConfig.DEVOUR_PULSE - 1 && !BeamClash.inClash(p)) {
			float before = t.getHealth();
			Raven.hit(p, t, RavenConfig.DEVOUR_DAMAGE);
			float dealt = Math.max(0f, before - t.getHealth());
			if (dealt > 0f) {
				p.heal(dealt * RavenConfig.DEVOUR_HEAL_SHARE);
			}
			level.sendParticles(Raven.dust(Raven.VIOLET, 1.2f), tc.x, tc.y, tc.z, 8, 0.3, 0.4, 0.3, 0.0);
			level.playSound(null, tc.x, tc.y, tc.z, RavenSounds.SOUL_HIT, SoundSource.PLAYERS, 0.3f, 0.9f);
		}
	}

	/** For the tests: the drain beam is on. */
	public static boolean devouring(ServerPlayer p) {
		return DEVOURS.containsKey(p.getUUID());
	}

	/** Debris Storm: tear up 8 nearby blocks into an orbit; tap V again (or left click) to fire one. */
	static boolean debrisStorm(ServerPlayer p) {
		if (!RavenMoves.tearUpDebris(p)) {
			return false;
		}
		pay(p, RavenSpell.DEBRIS_STORM);
		return true;
	}

	// ---------------------------------------------------------------- utility spells

	/** Dimensional Rift: a portal where she looks (20 blocks, through a wall if there's room behind it); she steps through. */
	static boolean dimensionalRift(ServerPlayer p, boolean misfire) {
		Vec3 to = riftSpot(p);
		if (to == null) {
			Raven.say(p, "message.projecthero.raven.no_room", ChatFormatting.GRAY);
			return false;
		}
		if (misfire) {
			Vec3 wild = RavenMoves.roomAt(p, scatter(p, to));
			if (wild != null) {
				to = wild;
			}
		}
		Vec3 look = p.getViewVector(1f).multiply(1, 0, 1);
		look = look.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : look.normalize();
		Vec3 here = p.position().add(look.scale(0.9)).add(0, 1.0, 0);
		RIFTS.put(p.getUUID(), new Rift(to, Raven.now(p) + RavenConfig.RIFT_STEP_TICKS));
		RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.RIFT, p.getId(), here, look, RavenConfig.RIFT_STEP_TICKS + 14));
		RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.RIFT, p.getId(), to.add(0, 1.0, 0), look, RavenConfig.RIFT_STEP_TICKS + 14));
		ServerLevel level = (ServerLevel) p.level();
		level.playSound(null, here.x, here.y, here.z, RavenSounds.RIFT, SoundSource.PLAYERS, 0.45f, 0.7f);
		level.playSound(null, to.x, to.y, to.z, RavenSounds.RIFT, SoundSource.PLAYERS, 0.45f, 0.7f);
		pay(p, RavenSpell.DIMENSIONAL_RIFT);
		return true;
	}

	/** Where the rift lets her out (her feet), or null if there's nowhere she fits. */
	public static Vec3 riftSpot(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1f);
		Vec3 end = eye.add(look.scale(RavenConfig.RIFT_RANGE));
		double eyeH = p.getEyeHeight();
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (hit.getType() == HitResult.Type.MISS) {
			return RavenMoves.roomAt(p, end.subtract(0, eyeH, 0));
		}
		Direction face = hit.getDirection();
		Vec3 at = hit.getLocation();
		if (face == Direction.UP) {
			return RavenMoves.roomAt(p, at);
		}
		if (face != Direction.DOWN) {
			// a wall: through it if there's room behind, within the range
			for (double d = eye.distanceTo(at) + 0.5; d <= RavenConfig.RIFT_RANGE + 1.0e-6; d += 0.5) {
				Vec3 feet = eye.add(look.scale(d)).subtract(0, eyeH, 0);
				if (level.noCollision(p, p.getBoundingBox().move(feet.subtract(p.position())))) {
					return feet;
				}
			}
		}
		// against it, on this side
		Vec3 back = at.subtract(look.scale(0.7)).subtract(0, face == Direction.DOWN ? p.getBbHeight() : eyeH * 0.5, 0);
		return RavenMoves.roomAt(p, back);
	}

	private static void riftTick(ServerPlayer p, long now, ServerLevel level) {
		Rift r = RIFTS.get(p.getUUID());
		if (r == null || now < r.at) {
			return;
		}
		RIFTS.remove(p.getUUID());
		Vec3 spot = RavenMoves.roomAt(p, r.to);
		if (spot == null) {
			return;
		}
		Vec3 from = p.position();
		p.teleportTo(level, spot.x, spot.y, spot.z, java.util.Set.of(), p.getYRot(), p.getXRot());
		p.resetFallDistance();
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		Raven.darkPuff(level, from.add(0, 1.0, 0), 16, 0.5);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, spot.x, spot.y + 1.0, spot.z, 40, 0.4, 0.8, 0.4, 0.2);
		level.playSound(null, spot.x, spot.y, spot.z, RavenSounds.DASH, SoundSource.PLAYERS, 0.4f, 0.6f);
	}

	/** Soul Restoration: aimed at a squadmate, take their pain and heal them double; at no one, a 3 s channel heals her. */
	static boolean soulRestoration(ServerPlayer p) {
		LivingEntity mate = RavenMoves.healTarget(p);
		ServerLevel level = (ServerLevel) p.level();
		if (mate != null) {
			float missing = mate.getMaxHealth() - mate.getHealth();
			float take = Math.min(RavenConfig.HEAL_TAKE_MAX, (float) Math.ceil(missing / 2f));
			take = Math.min(take, Math.max(0f, p.getHealth() - 1f));
			if (take <= 0f) {
				Raven.say(p, missing <= 0f ? "message.projecthero.raven.heal_full" : "message.projecthero.raven.heal_too_weak", ChatFormatting.GRAY);
				return false;
			}
			p.setHealth(p.getHealth() - take);
			p.hurtMarked = true;
			mate.heal(take * 2f);
			Vec3 a = p.position().add(0, 1.1, 0);
			Vec3 b = mate.position().add(0, mate.getBbHeight() * 0.6, 0);
			RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.HEAL, mate.getId(), a, b, 20));
			level.sendParticles(ParticleTypes.END_ROD, b.x, b.y, b.z, 10, 0.3, 0.4, 0.3, 0.02);
			level.sendParticles(Raven.dust(Raven.VOID, 1.0f), a.x, a.y, a.z, 10, 0.3, 0.4, 0.3, 0.0);
			if (mate instanceof ServerPlayer mp) {
				mp.displayClientMessage(Component.translatable("message.projecthero.raven.healed_by", p.getDisplayName())
						.withStyle(ChatFormatting.LIGHT_PURPLE), true);
			}
			p.displayClientMessage(Component.translatable("message.projecthero.raven.heal_mate", mate.getDisplayName(), Math.round(take))
					.withStyle(ChatFormatting.LIGHT_PURPLE), true);
			Raven.sound(p, RavenSounds.HEAL, 0.4f, 1.3f);
			pay(p, RavenSpell.SOUL_RESTORATION);
			return true;
		}
		CHANNELS.put(p.getUUID(), new Channel(p.position(), Raven.now(p)));
		RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.CHANNEL, p.getId(), p.position(), Vec3.ZERO, RavenConfig.HEAL_CHANNEL_TICKS));
		Raven.sound(p, RavenSounds.HEAL, 0.4f, 1.1f);
		Raven.sound(p, RavenSounds.CHANT, 0.35f, 0.7f);
		pay(p, RavenSpell.SOUL_RESTORATION);
		RavenState n = Raven.state(p).copy();
		n.castUntil = Raven.now(p) + RavenConfig.HEAL_CHANNEL_TICKS;
		Raven.save(p, n);
		return true;
	}

	/** The channel stops (moved, hit, meditating, teleported): nothing more is healed. */
	static void endChannel(ServerPlayer p) {
		CHANNELS.remove(p.getUUID());
	}

	/** For the tests: she is channelling Soul Restoration. */
	public static boolean channelling(ServerPlayer p) {
		return CHANNELS.containsKey(p.getUUID());
	}

	private static void channelTick(ServerPlayer p, long now, ServerLevel level) {
		Channel c = CHANNELS.get(p.getUUID());
		if (c == null) {
			return;
		}
		if (p.position().multiply(1, 0, 1).distanceTo(c.at.multiply(1, 0, 1)) > 0.4) {
			CHANNELS.remove(p.getUUID());
			Raven.say(p, "message.projecthero.raven.channel_broken", ChatFormatting.GRAY);
			return;
		}
		long age = now - c.start;
		p.heal(RavenConfig.HEAL_SELF / RavenConfig.HEAL_CHANNEL_TICKS);
		if (age % 4 == 0) {
			Vec3 at = p.position().add(0, 0.2 + (age % 20) / 10.0, 0);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 2, 0.5, 0.1, 0.5, 0.01);
		}
		if (age + 1 >= RavenConfig.HEAL_CHANNEL_TICKS) {
			CHANNELS.remove(p.getUUID());
			p.removeEffect(MobEffects.POISON);
			p.removeEffect(MobEffects.WEAKNESS);
			p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
			Raven.setComposure(p, Raven.composure(p) + RavenConfig.HEAL_CALM);
			Vec3 at = p.position().add(0, 1.0, 0);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 24, 0.4, 0.7, 0.4, 0.04);
			level.playSound(null, at.x, at.y, at.z, RavenSounds.HEAL, SoundSource.PLAYERS, 0.45f, 1.5f);
		}
	}

	/** Telekinetic Pull: flips the lever or button she looks at, or yanks a light creature to her; every loose item within 20 comes. */
	static boolean telekineticPull(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		boolean did = false;
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(RavenConfig.PULL_RANGE));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		LivingEntity creature = RavenMoves.aimed(p, RavenConfig.PULL_RANGE);
		if (hit.getType() == HitResult.Type.BLOCK && (creature == null || creature.distanceTo(p) > eye.distanceTo(hit.getLocation()))
				&& flip(p, level, hit.getBlockPos())) {
			did = true;
			Vec3 c = Vec3.atCenterOf(hit.getBlockPos());
			RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.HEAL, p.getId(), eye.add(0, -0.4, 0), c, 10));
			level.sendParticles(Raven.dust(Raven.VIOLET, 1.0f), c.x, c.y, c.z, 10, 0.2, 0.2, 0.2, 0.0);
		} else if (creature != null && !Raven.isBoss(creature) && creature.getMaxHealth() <= RavenConfig.PULL_LIGHT_HEALTH) {
			did = true;
			Vec3 to = p.position().add(p.getViewVector(1f).multiply(1, 0, 1).scale(1.5)).subtract(creature.position());
			double d = to.length();
			Vec3 v = to.normalize().scale(Math.min(2.2, Math.max(0.7, d * 0.2))).add(0, 0.35, 0);
			creature.setDeltaMovement(v);
			creature.hurtMarked = true;
			RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.TENTACLE, creature.getId(), eye.add(0, -0.4, 0), new Vec3(1, 0, 0), 8));
			RavenSpells.breakVeil(p);
		}
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(RavenConfig.PULL_RANGE),
				e -> e.isAlive() && e.distanceTo(p) <= RavenConfig.PULL_RANGE);
		if (!items.isEmpty()) {
			did = true;
			PULLS.put(p.getUUID(), new Pull(new ArrayList<>(items), Raven.now(p) + 40));
			for (ItemEntity it : items) {
				level.sendParticles(Raven.dust(Raven.VOID, 0.8f), it.getX(), it.getY() + 0.2, it.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
			}
		}
		if (!did) {
			Raven.say(p, "message.projecthero.raven.nothing_to_pull", ChatFormatting.GRAY);
			return false;
		}
		Raven.sound(p, RavenSounds.GRAB, 0.4f, 0.9f);
		pay(p, RavenSpell.TELEKINETIC_PULL);
		return true;
	}

	/** A lever or button at {@code pos} is flipped / pressed from afar. */
	private static boolean flip(ServerPlayer p, ServerLevel level, BlockPos pos) {
		BlockState st = level.getBlockState(pos);
		if (st.getBlock() instanceof LeverBlock lever) {
			lever.pull(st, level, pos, p);
			level.playSound(null, pos, net.minecraft.sounds.SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 0.6f);
			return true;
		}
		if (st.getBlock() instanceof ButtonBlock button && !st.getValue(ButtonBlock.POWERED)) {
			button.press(st, level, pos, p);
			level.playSound(null, pos, net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 0.6f);
			return true;
		}
		return false;
	}

	private static void pullTick(ServerPlayer p, long now) {
		Pull pull = PULLS.get(p.getUUID());
		if (pull == null) {
			return;
		}
		Vec3 to = p.position().add(0, 0.8, 0);
		pull.items.removeIf(it -> !it.isAlive() || it.level() != p.level());
		for (ItemEntity it : pull.items) {
			Vec3 d = to.subtract(it.position());
			if (d.length() < 1.2) {
				it.setDeltaMovement(Vec3.ZERO);
				continue;
			}
			it.setDeltaMovement(d.normalize().scale(Math.min(1.1, 0.25 + d.length() * 0.08)));
			it.hasImpulse = true;
			if (p.tickCount % 3 == 0) {
				((ServerLevel) p.level()).sendParticles(Raven.dust(Raven.VOID, 0.7f), it.getX(), it.getY() + 0.2, it.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		if (now >= pull.until || pull.items.isEmpty()) {
			PULLS.remove(p.getUUID());
		}
	}

	/** Astral Sanctuary: a 5-block dome for 8 s -- projectiles stop at it, hostile creatures can't get in, hits inside are 40% lighter. */
	static boolean astralSanctuary(ServerPlayer p) {
		Vec3 at = p.position();
		float reduction = RavenConfig.SANCTUARY_REDUCTION * Raven.shieldMult(p);
		SANCTUARIES.put(p.getUUID(), new Sanctuary(p.getUUID(), at, Raven.now(p) + RavenConfig.SANCTUARY_TICKS, reduction));
		RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.SANCTUARY, p.getId(), at, new Vec3(RavenConfig.SANCTUARY_RADIUS, reduction, 0),
				RavenConfig.SANCTUARY_TICKS));
		ServerLevel level = (ServerLevel) p.level();
		level.playSound(null, at.x, at.y, at.z, RavenSounds.SHIELD_UP, SoundSource.PLAYERS, 0.45f, 0.8f);
		level.playSound(null, at.x, at.y, at.z, RavenSounds.CHANT, SoundSource.PLAYERS, 0.4f, 0.55f);
		pay(p, RavenSpell.ASTRAL_SANCTUARY);
		return true;
	}

	/** How much lighter a hit on {@code victim} is: the best sanctuary it stands in that was raised by it or a squadmate. */
	public static float sanctuaryReduction(LivingEntity victim) {
		if (SANCTUARIES.isEmpty()) {
			return 0f;
		}
		long now = victim.level().getGameTime();
		float best = 0f;
		for (Sanctuary s : SANCTUARIES.values()) {
			if (now >= s.until || victim.position().distanceTo(s.at) > RavenConfig.SANCTUARY_RADIUS + 0.3) {
				continue;
			}
			Player owner = victim.level().getPlayerByUUID(s.owner);
			if (owner == null || (owner != victim && !Squads.areAllies(owner, victim))) {
				continue;
			}
			best = Math.max(best, s.reduction);
		}
		return best;
	}

	/** For the tests: her sanctuary (null = none). */
	public static Sanctuary sanctuary(ServerPlayer p) {
		return SANCTUARIES.get(p.getUUID());
	}

	private static void sanctuaryTick(ServerPlayer p, long now, ServerLevel level) {
		Sanctuary s = SANCTUARIES.get(p.getUUID());
		if (s == null) {
			return;
		}
		if (now >= s.until) {
			SANCTUARIES.remove(p.getUUID());
			level.sendParticles(Raven.dust(Raven.VIOLET, 1.2f), s.at.x, s.at.y + 1.0, s.at.z, 30, 2.0, 1.0, 2.0, 0.0);
			return;
		}
		double r = RavenConfig.SANCTUARY_RADIUS;
		AABB box = new AABB(s.at, s.at).inflate(r + 2);
		// hostile creatures are pushed back out to the dome's edge
		for (Mob m : level.getEntitiesOfClass(Mob.class, box, e -> e.isAlive() && (e instanceof Enemy || e.getTarget() instanceof Player))) {
			if (Raven.isBoss(m) || !HeroTargets.canHarm(p, m)) {
				continue;
			}
			Vec3 rel = m.position().subtract(s.at);
			Vec3 flat = rel.multiply(1, 0, 1);
			double d = flat.length();
			double edge = r + m.getBbWidth() * 0.5 + 0.2;
			if (d < edge && Math.abs(rel.y) < r) {
				Vec3 out = d < 1.0e-3 ? new Vec3(1, 0, 0) : flat.scale(1 / d);
				Vec3 spot = s.at.add(out.scale(edge)).add(0, rel.y, 0);
				if (level.noCollision(m, m.getBoundingBox().move(spot.subtract(m.position())))) {
					m.teleportTo(spot.x, spot.y, spot.z);
				}
				m.setDeltaMovement(out.scale(0.3).add(0, 0.1, 0));
				m.hurtMarked = true;
				if (p.tickCount % 4 == 0) {
					Vec3 at = s.at.add(out.scale(r)).add(0, Math.max(0.5, rel.y + 1.0), 0);
					level.sendParticles(Raven.dust(Raven.VIOLET, 1.2f), at.x, at.y, at.z, 5, 0.2, 0.4, 0.2, 0.0);
				}
			}
		}
		// projectiles from outside stop at it
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, box, e -> e.position().distanceTo(s.at) <= r
				&& !(e instanceof com.projecthero.mod.raven.entity.DarkBoltEntity))) {
			Entity owner = proj.getOwner();
			if (owner == p || (owner != null && Squads.areAllies(p, owner)) || proj.getDeltaMovement().lengthSqr() < 0.01) {
				continue;
			}
			Vec3 at = proj.position();
			level.sendParticles(Raven.dust(Raven.VIOLET, 1.4f), at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.0);
			level.playSound(null, at.x, at.y, at.z, RavenSounds.SHIELD_HIT, SoundSource.PLAYERS, 0.35f, 1.0f);
			proj.discard();
			stopped++;
		}
	}

	/** Shadow Veil: 6 s melted into shadow -- invisible, silent, mobs lose her; any attack ends it. */
	static boolean shadowVeil(ServerPlayer p) {
		long now = Raven.now(p);
		pay(p, RavenSpell.SHADOW_VEIL);
		RavenState n = Raven.state(p).copy();
		n.veilUntil = now + RavenConfig.VEIL_TICKS;
		Raven.save(p, n);
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, RavenConfig.VEIL_TICKS, 0, true, false, false));
		p.setSilent(true);
		loseHer(p);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.VEIL, p.getId(), p.position(), Vec3.ZERO, 16));
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 20, 0.4, 0.7, 0.4, 0.01);
		level.sendParticles(Raven.dust(Raven.VOID, 1.6f), c.x, c.y, c.z, 30, 0.5, 0.8, 0.5, 0.0);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.VEIL, SoundSource.PLAYERS, 0.4f, 0.6f);
		return true;
	}

	/** Every mob after her forgets her. */
	private static void loseHer(ServerPlayer p) {
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), e -> e.getTarget() == p)) {
			m.setTarget(null);
			m.getNavigation().stop();
		}
	}

	/** An attack ends Shadow Veil. */
	public static void breakVeil(ServerPlayer p) {
		RavenState s = Raven.peek(p);
		if (s != null && s.veilUntil > Raven.now(p)) {
			RavenState n = s.copy();
			n.veilUntil = 0L;
			Raven.save(p, n);
			unveil(p);
			Raven.say(p, "message.projecthero.raven.veil_broken", ChatFormatting.GRAY);
		}
	}

	/** Visible and audible again (the Veil's invisibility only: a potion's longer one stays). */
	static void unveil(ServerPlayer p) {
		MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
		if (inv != null && inv.isAmbient() && inv.getDuration() <= RavenConfig.VEIL_TICKS) {
			p.removeEffect(MobEffects.INVISIBILITY);
		}
		p.setSilent(false);
		if (p.level() instanceof ServerLevel level) {
			Raven.darkPuff(level, p.position().add(0, 1.0, 0), 14, 0.4);
		}
	}

	private static void veilTick(ServerPlayer p, long now) {
		RavenState s = Raven.state(p);
		if (s.veilUntil <= 0L) {
			return;
		}
		if (now >= s.veilUntil) {
			RavenState n = s.copy();
			n.veilUntil = 0L;
			Raven.save(p, n);
			unveil(p);
			return;
		}
		if (!p.isSilent()) {
			p.setSilent(true);
		}
		loseHer(p);
		if (p.tickCount % 5 == 0) {
			((ServerLevel) p.level()).sendParticles(Raven.dust(Raven.VOID, 0.8f), p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.25, 0.05, 0.25, 0.0);
		}
	}

	// ---------------------------------------------------------------- hurt / tick

	/**
	 * She was hurt for {@code dealt}: a hit (something struck her) stops the healing channel -- a poison or burn tick doesn't --
	 * and 8+ of any damage while a Soul Detonation counts down breaks it.
	 */
	static void onHurt(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source, float dealt) {
		if (source.getEntity() != null || source.getDirectEntity() != null) {
			if (CHANNELS.remove(p.getUUID()) != null) {
				Raven.say(p, "message.projecthero.raven.channel_broken", ChatFormatting.GRAY);
			}
		}
		Detonation d = DETONATIONS.get(p.getUUID());
		if (d != null) {
			d.hurt += dealt;
			if (d.hurt + 1.0e-3f >= RavenConfig.DETONATE_CANCEL_AT) {
				DETONATIONS.remove(p.getUUID());
				Raven.say(p, "message.projecthero.raven.detonation_broken", ChatFormatting.GRAY);
				Raven.darkPuff((ServerLevel) p.level(), p.position().add(0, 1.0, 0), 16, 0.6);
			}
		}
	}

	static void tick(ServerPlayer p) {
		long now = Raven.now(p);
		ServerLevel level = (ServerLevel) p.level();
		vortexTick(p, now, level);
		detonationTick(p, now, level);
		devourTick(p, now, level);
		channelTick(p, now, level);
		sanctuaryTick(p, now, level);
		riftTick(p, now, level);
		veilTick(p, now);
		pullTick(p, now);
	}

	/** For the tests: the marks a Soul Detonation holds. */
	public static Set<LivingEntity> marks(ServerPlayer p) {
		Detonation d = DETONATIONS.get(p.getUUID());
		return d == null ? Set.of() : new HashSet<>(d.marked);
	}
}
