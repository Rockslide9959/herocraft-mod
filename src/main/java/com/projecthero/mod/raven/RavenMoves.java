package com.projecthero.mod.raven;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.raven.data.RavenState;
import com.projecthero.mod.raven.entity.DarkBoltEntity;
import com.projecthero.mod.raven.entity.RavenDebrisEntity;
import com.projecthero.mod.raven.entity.SoulSelfEntity;
import com.projecthero.mod.raven.network.RavenFxPayload;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven: the main keys ({@code docs/RAVEN_REVAMP_DESIGN.md}). {@link RavenAbilityManager} routes the keys here; the wheel
 * spells are {@link RavenSpells}' (which borrows the debris and the pins from here). The running parts (the telekinetic hold,
 * thrown creatures, pins, Telekinetic Crush's and the Wrath's lifts, the Dash and the Step, the orbiting debris, the Soul Shield, the
 * charged wave, the soul-self, the Wrath's tentacles, the chant) tick in {@link #tick}. All of it is per player in maps that hold
 * entities, cleared by {@link #clearSessionState} (from {@code ServerStateReset}).
 */
public final class RavenMoves {
	private RavenMoves() {
	}

	// ---------------------------------------------------------------- running state

	/** Telekinesis: what she holds and how far out. */
	private static final class Hold {
		Entity target;
		double dist;
		long start;
	}

	/** A creature she threw: it takes the hit when it lands or strikes something. */
	private static final class Thrown {
		final LivingEntity e;
		int ticks;
		double speed;

		Thrown(LivingEntity e) {
			this.e = e;
		}
	}

	/** Claws (or a stun) holding a creature in place. */
	private record Pin(LivingEntity e, Vec3 at, long until) {
	}

	/** A creature in Telekinetic Crush's (or the Wrath's) grip: up, held, slammed. */
	private static final class Lift {
		final LivingEntity e;
		final double baseY;
		final long start;
		final int rise;
		final int hold;
		final float damage;
		final double height;
		final boolean wrath;
		final int stun;
		long slamAt = -1L;
		double groundY;
		boolean done;

		Lift(LivingEntity e, long start, int rise, int hold, double height, float damage, boolean wrath, int stun) {
			this.e = e;
			this.baseY = e.getY();
			this.start = start;
			this.rise = rise;
			this.hold = hold;
			this.height = height;
			this.damage = damage;
			this.wrath = wrath;
			this.stun = stun;
		}
	}

	private record Dash(Vec3 from, Vec3 to, long start) {
	}

	private record Step(LivingEntity target, long at) {
	}

	/** Dark Energy Wave: the charge, then the front rolling out. */
	private static final class Wave {
		final Vec3 origin;
		final Vec3 dir;
		final long start;
		final Set<Integer> struck = new HashSet<>();
		double lastFront;

		Wave(Vec3 origin, Vec3 dir, long start) {
			this.origin = origin;
			this.dir = dir;
			this.start = start;
		}
	}

	private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Thrown>> THROWN = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Pin>> PINS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Lift>> LIFTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Dash> DASHES = new ConcurrentHashMap<>();
	private static final Map<UUID, Step> STEPS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<RavenDebrisEntity>> DEBRIS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> DEBRIS_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> WRATH_START = new ConcurrentHashMap<>();
	/** Soul Shield: damage soaked this raise (20+ and it bursts when it ends). */
	private static final Map<UUID, Float> GUARD_SOAK = new ConcurrentHashMap<>();
	/** Dark Energy Wave: fires at this time. */
	private static final Map<UUID, Long> CHARGES = new ConcurrentHashMap<>();
	private static final Map<UUID, Wave> WAVES = new ConcurrentHashMap<>();
	/** The soul-self she is steering. */
	private static final Map<UUID, SoulSelfEntity> SOULS = new ConcurrentHashMap<>();

	static void clearSessionState() {
		HOLDS.clear();
		THROWN.clear();
		PINS.clear();
		LIFTS.clear();
		DASHES.clear();
		STEPS.clear();
		DEBRIS.clear();
		DEBRIS_UNTIL.clear();
		WRATH_START.clear();
		GUARD_SOAK.clear();
		CHARGES.clear();
		WAVES.clear();
		SOULS.clear();
	}

	static void forget(UUID id) {
		HOLDS.remove(id);
		THROWN.remove(id);
		PINS.remove(id);
		LIFTS.remove(id);
		DASHES.remove(id);
		STEPS.remove(id);
		DEBRIS.remove(id);
		DEBRIS_UNTIL.remove(id);
		WRATH_START.remove(id);
		GUARD_SOAK.remove(id);
		CHARGES.remove(id);
		WAVES.remove(id);
		SOULS.remove(id);
	}

	/** Death, a new world, a revoke: everything running ends (held things drop, debris shatters, the soul fades). */
	static void endAll(ServerPlayer p) {
		UUID id = p.getUUID();
		Hold h = HOLDS.remove(id);
		if (h != null && h.target instanceof RavenDebrisEntity d && d.isAlive() && d.level() instanceof ServerLevel sl) {
			d.shatter(sl);
		}
		List<RavenDebrisEntity> list = DEBRIS.remove(id);
		if (list != null) {
			for (RavenDebrisEntity d : list) {
				if (d.isAlive() && d.level() instanceof ServerLevel sl) {
					d.shatter(sl);
				}
			}
		}
		SoulSelfEntity soul = SOULS.remove(id);
		if (soul != null && soul.isAlive()) {
			soul.discard();
		}
		THROWN.remove(id);
		PINS.remove(id);
		LIFTS.remove(id);
		DASHES.remove(id);
		STEPS.remove(id);
		DEBRIS_UNTIL.remove(id);
		WRATH_START.remove(id);
		GUARD_SOAK.remove(id);
		CHARGES.remove(id);
		WAVES.remove(id);
	}

	// ---------------------------------------------------------------- shared

	static boolean ready(ServerPlayer p, String id) {
		if (Raven.meditating(p) && !RavenAbilityManager.MEDITATE.equals(id)) {
			Raven.say(p, "message.projecthero.raven.meditating", ChatFormatting.GRAY);
			return false;
		}
		int cd = Raven.cooldownRemaining(p, id);
		if (cd > 0) {
			RavenSpell spell = RavenSpell.byId(id);
			Raven.say(p, "message.projecthero.raven.cooldown", ChatFormatting.GRAY,
					Component.translatable(spell != null ? spell.langKey() : "projecthero.raven.ability." + id), (cd + 19) / 20);
			return false;
		}
		if (!RavenAbilityManager.allowed(p, id)) {
			Raven.say(p, RavenAbilityManager.TRIGONS_WRATH.equals(id) ? "message.projecthero.raven.need_rage"
					: "message.projecthero.raven.already_ascended", ChatFormatting.DARK_RED);
			return false;
		}
		RavenSpell spell = RavenSpell.byId(id);
		float cost = spell != null ? RavenSpells.costNow(p, spell) : RavenAbilityManager.costNow(p, id);
		if (!Raven.hasEnergy(p, cost)) {
			Raven.say(p, "message.projecthero.raven.no_energy", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	/**
	 * Pays for a move: its Dark Energy (Demonic Ascension's discount applied), its cooldown, the white eyes. {@code attack}: an attack
	 * ends Shadow Veil.
	 */
	static void pay(ServerPlayer p, String id, float cost, int cooldown, boolean attack) {
		Raven.spendEnergy(p, cost * Raven.costMult(p));
		if (cooldown > 0) {
			Raven.startCooldown(p, id, cooldown);
		}
		Raven.cast(p);
		if (attack) {
			RavenSpells.breakVeil(p);
		}
	}

	public static void fx(ServerPlayer p, RavenFxPayload payload) {
		for (ServerPlayer viewer : PlayerLookup.tracking(p)) {
			if (viewer != p && ServerPlayNetworking.canSend(viewer, RavenFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
		if (ServerPlayNetworking.canSend(p, RavenFxPayload.TYPE)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/** The first creature she may harm along her aim within {@code range}, walls in the way. */
	public static LivingEntity aimed(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(range));
		BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
				e -> e instanceof LivingEntity le && le.isAlive() && e.isPickable() && HeroTargets.canHarm(p, e), 0.4f);
		return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
	}

	/** Where her aim lands within {@code range}: a creature, a block, or the end of the range. */
	static Vec3 aimPoint(ServerPlayer p, double range) {
		LivingEntity t = aimed(p, range);
		if (t != null) {
			return t.position().add(0, t.getBbHeight() * 0.55, 0);
		}
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(range));
		BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return block.getType() != HitResult.Type.MISS ? block.getLocation() : end;
	}

	/** {@link #aimPoint} dropped onto the ground under it (at most 8 blocks down): where a spell on the ground goes. */
	static Vec3 groundAim(ServerPlayer p, double range) {
		Vec3 at = aimPoint(p, range);
		LivingEntity t = aimed(p, range);
		if (t != null) {
			return t.position();
		}
		Vec3 from = at.add(0, 0.5, 0);
		BlockHitResult down = p.level().clip(new ClipContext(from, from.add(0, -8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return down.getType() == HitResult.Type.MISS ? at : down.getLocation();
	}

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float volume, float pitch) {
		Raven.sound(p, s, volume, pitch);
	}

	// ---------------------------------------------------------------- taps

	public static boolean use(ServerPlayer p, String id) {
		// Shift+G with the soul-self out: step through to it (free, no cooldown check)
		if (RavenAbilityManager.SOUL_SELF.equals(id) && soulOut(p) && !Raven.meditating(p)) {
			return soulTeleport(p);
		}
		if (!ready(p, id)) {
			return false;
		}
		return switch (id) {
			case RavenAbilityManager.DARK_BOLT -> darkBolt(p);
			case RavenAbilityManager.SOUL_SHIELD -> soulShield(p);
			case RavenAbilityManager.SOUL_SELF -> soulSelf(p);
			case RavenAbilityManager.TELEKINETIC_CRUSH -> telekineticCrush(p);
			case RavenAbilityManager.TRIGONS_WRATH -> trigonsWrath(p);
			case RavenAbilityManager.SHADOW_DASH -> shadowDash(p);
			case RavenAbilityManager.SHADOW_STEP -> shadowStep(p);
			case RavenAbilityManager.ENERGY_WAVE -> energyWave(p);
			case RavenAbilityManager.ASCENSION -> demonicAscension(p);
			default -> false;
		};
	}

	/** R: a bolt of black energy that bursts in a small purple pop where it lands. Unsettled, it drifts off her aim. */
	static boolean darkBolt(ServerPlayer p) {
		Vec3 dir = p.getViewVector(1f);
		if (Raven.unsettled(p)) {
			dir = drift(p, dir);
		}
		DarkBoltEntity.fire(p, dir, RavenConfig.BOLT_DAMAGE);
		sound(p, RavenSounds.BOLT, 0.35f, 1.5f);
		pay(p, RavenAbilityManager.DARK_BOLT, RavenConfig.BOLT_COST, RavenConfig.BOLT_COOLDOWN, true);
		Raven.unsettle(p, RavenConfig.BOLT_COMPOSURE);
		return true;
	}

	/** {@code dir} turned a random few degrees (at most {@link RavenConfig#DRIFT_DEGREES}). */
	static Vec3 drift(ServerPlayer p, Vec3 dir) {
		double max = Math.toRadians(RavenConfig.DRIFT_DEGREES);
		double yaw = (p.getRandom().nextDouble() * 2 - 1) * max;
		double pitch = (p.getRandom().nextDouble() * 2 - 1) * max;
		return dir.yRot((float) yaw).xRot((float) pitch).normalize();
	}

	// ---------------------------------------------------------------- G: Soul Shield

	/** G: a close purple barrier for 5 s: soaks 30 (50 ascended, half at Rage), reflects projectiles, shoves melee attackers. */
	static boolean soulShield(ServerPlayer p) {
		long now = Raven.now(p);
		float absorb = (Raven.ascended(p) ? RavenConfig.GUARD_ABSORB_ASCENDED : RavenConfig.GUARD_ABSORB) * Raven.shieldMult(p);
		RavenState n = Raven.state(p).copy();
		n.guardUntil = now + RavenConfig.GUARD_TICKS;
		n.guardHp = absorb;
		Raven.save(p, n);
		GUARD_SOAK.put(p.getUUID(), 0f);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		fx(p, new RavenFxPayload(RavenFxPayload.GUARD_UP, p.getId(), c, Vec3.ZERO, 12));
		level.sendParticles(Raven.dust(Raven.VIOLET, 1.2f), c.x, c.y, c.z, 24, 0.7, 0.9, 0.7, 0.0);
		sound(p, RavenSounds.SHIELD_UP, 0.35f, 1.4f);
		sound(p, RavenSounds.GUARD_HUM, 0.5f, 0.7f);
		pay(p, RavenAbilityManager.SOUL_SHIELD, RavenConfig.GUARD_COST, RavenConfig.GUARD_COOLDOWN, false);
		return true;
	}

	/**
	 * The Soul Shield takes a hit first: returns what gets through. A melee attacker standing close is knocked back off it.
	 */
	static float guardAbsorb(ServerPlayer p, DamageSource source, float amount) {
		RavenState s = Raven.state(p);
		if (s.guardUntil <= Raven.now(p) || s.guardHp <= 0f || amount <= 0f) {
			return amount;
		}
		float soak = Math.min(amount, s.guardHp);
		RavenState n = s.copy();
		n.guardHp = s.guardHp - soak;
		Raven.save(p, n);
		GUARD_SOAK.merge(p.getUUID(), soak, Float::sum);
		ServerLevel level = (ServerLevel) p.level();
		Entity direct = source.getDirectEntity();
		if (direct instanceof LivingEntity attacker && direct == source.getEntity() && attacker != p && attacker.distanceTo(p) < 5.0
				&& !Raven.isBoss(attacker)) {
			Vec3 away = attacker.position().subtract(p.position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0e-4 ? p.getViewVector(1f).multiply(1, 0, 1) : away;
			away = away.normalize().scale(RavenConfig.GUARD_KNOCKBACK);
			attacker.push(away.x, 0.35, away.z);
			attacker.hurtMarked = true;
		}
		Vec3 at = direct != null ? direct.position().add(0, direct.getBbHeight() * 0.5, 0) : p.position().add(0, 1.0, 0);
		level.sendParticles(Raven.dust(Raven.VIOLET, 1.4f), at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.0);
		sound(p, RavenSounds.SHIELD_HIT, 0.4f, 0.8f);
		if (n.guardHp <= 0.01f) {
			endGuard(p, true);
		}
		return amount - soak;
	}

	/** The Soul Shield ends (ran out or broke): it bursts if it soaked 20 or more. */
	static void endGuard(ServerPlayer p, boolean broken) {
		RavenState s = Raven.state(p);
		if (s.guardUntil <= 0L && s.guardHp <= 0f) {
			return;
		}
		RavenState n = s.copy();
		n.guardUntil = 0L;
		n.guardHp = 0f;
		Raven.save(p, n);
		float soaked = GUARD_SOAK.getOrDefault(p.getUUID(), 0f);
		GUARD_SOAK.remove(p.getUUID());
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		if (soaked + 1.0e-3f >= RavenConfig.GUARD_BURST_AT) {
			double r = RavenConfig.GUARD_BURST_RADIUS;
			for (LivingEntity t : HeroTargets.harmable(level, p, p.getBoundingBox().inflate(r))) {
				if (t.distanceTo(p) <= r + t.getBbWidth() * 0.5) {
					Raven.hit(p, t, RavenConfig.GUARD_BURST_DAMAGE);
					Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1);
					if (away.lengthSqr() > 1.0e-4 && !Raven.isBoss(t)) {
						away = away.normalize();
						t.push(away.x * 0.8, 0.3, away.z * 0.8);
						t.hurtMarked = true;
					}
				}
			}
			fx(p, new RavenFxPayload(RavenFxPayload.BURST, p.getId(), c, new Vec3(r, 0, 0), 12));
			level.sendParticles(Raven.dust(Raven.VIOLET, 1.8f), c.x, c.y, c.z, 40, 1.4, 0.8, 1.4, 0.0);
			level.playSound(null, c.x, c.y, c.z, RavenSounds.BURST, SoundSource.PLAYERS, 0.45f, 0.7f);
			bursts++;
		} else {
			level.sendParticles(Raven.dust(Raven.VIOLET, 1.0f), c.x, c.y, c.z, 16, 0.6, 0.8, 0.6, 0.0);
			if (broken) {
				sound(p, RavenSounds.SHIELD_BREAK, 0.4f, 0.7f);
			}
		}
	}

	/** For the tests: Soul Shield bursts so far, and projectiles it reflected. */
	public static int bursts;
	public static int reflected;

	private static void guardTick(ServerPlayer p, long now, ServerLevel level) {
		RavenState s = Raven.state(p);
		if (s.guardUntil <= 0L) {
			return;
		}
		if (now >= s.guardUntil || s.guardHp <= 0f) {
			endGuard(p, false);
			return;
		}
		double r = RavenConfig.GUARD_RADIUS * p.getScale() + 1.0;
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r + 2.0),
				e -> e.position().distanceTo(c) <= r && !(e instanceof DarkBoltEntity) && !e.getTags().contains(REFLECTED))) {
			Entity owner = proj.getOwner();
			if (owner == p || (owner != null && Squads.areAllies(p, owner))) {
				continue;
			}
			Vec3 v = proj.getDeltaMovement();
			if (v.lengthSqr() < 0.01) {
				continue; // stuck in the ground
			}
			// back where it came from, and now it's hers
			Vec3 back = owner != null ? owner.position().add(0, owner.getBbHeight() * 0.6, 0).subtract(proj.position()).normalize().scale(v.length())
					: v.scale(-1);
			proj.setDeltaMovement(back);
			proj.setOwner(p);
			proj.addTag(REFLECTED);
			proj.hasImpulse = true;
			proj.hurtMarked = true;
			Vec3 at = proj.position();
			level.sendParticles(Raven.dust(Raven.VIOLET, 1.4f), at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.0);
			level.playSound(null, at.x, at.y, at.z, RavenSounds.SHIELD_HIT, SoundSource.PLAYERS, 0.4f, 1.3f);
			reflected++;
		}
	}

	private static final String REFLECTED = "projecthero_raven_reflected";

	// ---------------------------------------------------------------- Shift+G: Soul-Self

	/** Shift+G: her raven soul flies out and she steers it with her look for 5 s; Shift+G again steps through to it. */
	static boolean soulSelf(ServerPlayer p) {
		SoulSelfEntity old = SOULS.remove(p.getUUID());
		if (old != null && old.isAlive()) {
			old.discard();
		}
		SoulSelfEntity soul = SoulSelfEntity.release(p, Raven.ascended(p));
		SOULS.put(p.getUUID(), soul);
		sound(p, RavenSounds.SOUL, 0.5f, 0.6f);
		pay(p, RavenAbilityManager.SOUL_SELF, RavenConfig.SOUL_COST, RavenConfig.SOUL_COOLDOWN, true);
		Raven.unsettle(p, RavenConfig.SOUL_COMPOSURE);
		return true;
	}

	/** Her soul-self is out (steering or flying home), so Shift+G would step through to it. Client-safe. */
	public static boolean soulOut(Player p) {
		if (p instanceof ServerPlayer) {
			SoulSelfEntity s = SOULS.get(p.getUUID());
			return s != null && s.isAlive() && s.fade() == 0;
		}
		return !p.level().getEntitiesOfClass(SoulSelfEntity.class, p.getBoundingBox().inflate(RavenConfig.SOUL_LEASH + 8),
				e -> e.ownerId() == p.getId() && e.fade() == 0).isEmpty();
	}

	/** For the tests and the harness: the soul she is steering (null = none). */
	public static SoulSelfEntity soul(ServerPlayer p) {
		SoulSelfEntity s = SOULS.get(p.getUUID());
		return s != null && s.isAlive() ? s : null;
	}

	/** Shift+G again: she steps through into her soul-self's place (if there is room for her there). */
	static boolean soulTeleport(ServerPlayer p) {
		SoulSelfEntity soul = SOULS.get(p.getUUID());
		if (soul == null || !soul.isAlive()) {
			return false;
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 spot = roomAt(p, soul.position());
		if (spot == null) {
			Raven.say(p, "message.projecthero.raven.no_room", ChatFormatting.GRAY);
			return false;
		}
		Vec3 from = p.position();
		RavenSpells.endChannel(p);
		endHold(p, false);
		p.teleportTo(level, spot.x, spot.y, spot.z, java.util.Set.of(), p.getYRot(), p.getXRot());
		p.resetFallDistance();
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		soul.dissolve();
		SOULS.remove(p.getUUID());
		fx(p, new RavenFxPayload(RavenFxPayload.STREAK, p.getId(), from.add(0, 1.0, 0), spot.add(0, 1.0, 0), 14));
		Raven.darkPuff(level, from.add(0, 1.0, 0), 24, 0.6);
		Raven.darkPuff(level, spot.add(0, 1.0, 0), 30, 0.7);
		level.playSound(null, spot.x, spot.y, spot.z, RavenSounds.DASH, SoundSource.PLAYERS, 0.4f, 0.55f);
		Raven.cast(p);
		return true;
	}

	/**
	 * A spot near {@code at} where she fits, or null: right there, a little down, then up to 8 blocks up (a soul-self steered into
	 * the ground lets her out on the surface above it).
	 */
	static Vec3 roomAt(ServerPlayer p, Vec3 at) {
		ServerLevel level = (ServerLevel) p.level();
		for (double dy : new double[] { 0, 0.5, 1.0, -0.5, 1.5, -1.0, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0 }) {
			Vec3 spot = at.add(0, dy, 0);
			if (level.noCollision(p, p.getBoundingBox().move(spot.subtract(p.position())))) {
				return spot;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- Z: Telekinetic Crush

	/** Z: up to 6 creatures in front of her go up, hang for 2 s, and are slammed down (14, a 1 s stun). Bosses only take the hit. */
	static boolean telekineticCrush(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1f);
		List<LivingEntity> found = new ArrayList<>();
		for (LivingEntity t : HeroTargets.harmable(level, p, p.getBoundingBox().inflate(RavenConfig.CRUSH_RANGE))) {
			Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d > RavenConfig.CRUSH_RANGE + t.getBbWidth() * 0.5 || (d > 1.5 && to.normalize().dot(look) < RavenConfig.CRUSH_CONE)
					|| !p.hasLineOfSight(t)) {
				continue;
			}
			found.add(t);
		}
		if (found.isEmpty()) {
			Raven.say(p, "message.projecthero.raven.no_target", ChatFormatting.GRAY);
			return false;
		}
		found.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
		long now = Raven.now(p);
		List<Lift> lifts = LIFTS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		Vec3 hand = p.getEyePosition().add(0, -0.4, 0).add(look.scale(0.6));
		for (int i = 0; i < Math.min(RavenConfig.CRUSH_MAX, found.size()); i++) {
			LivingEntity t = found.get(i);
			lifts.add(new Lift(t, now, RavenConfig.CRUSH_RISE_TICKS, RavenConfig.CRUSH_HOLD_TICKS, RavenConfig.CRUSH_LIFT, RavenConfig.CRUSH_DAMAGE,
					false, RavenConfig.CRUSH_STUN_TICKS));
			fx(p, new RavenFxPayload(RavenFxPayload.TENTACLE, t.getId(), hand, new Vec3(1, 0, 0), RavenConfig.CRUSH_RISE_TICKS + RavenConfig.CRUSH_HOLD_TICKS));
		}
		sound(p, RavenSounds.GRAB, 0.4f, 0.6f);
		sound(p, RavenSounds.WAVE, 0.35f, 0.6f);
		pay(p, RavenAbilityManager.TELEKINETIC_CRUSH, RavenConfig.CRUSH_COST, RavenConfig.CRUSH_COOLDOWN, true);
		Raven.unsettle(p, RavenConfig.CRUSH_COMPOSURE);
		return true;
	}

	// ---------------------------------------------------------------- Shift+Z: Trigon's Wrath

	/** Shift+Z (Rage only): Trigon's side breaks through -- a giant cloaked shadow for 15 s. */
	static boolean trigonsWrath(ServerPlayer p) {
		long now = Raven.now(p);
		RavenFlight.stopMeditation(p);
		endHold(p, false);
		RavenState n = Raven.state(p).copy();
		n.wrathUntil = now + RavenConfig.WRATH_TICKS;
		n.composure = 0f;
		Raven.save(p, n);
		WRATH_START.put(p.getUUID(), now);
		Raven.reconcile(p);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.WRATH, SoundSource.PLAYERS, 0.5f, 0.7f);
		Raven.darkPuff(level, c, 80, 1.6);
		level.sendParticles(Raven.dust(Raven.RED, 2.0f), c.x, c.y + 1.0, c.z, 30, 1.2, 1.5, 1.2, 0.0);
		pay(p, RavenAbilityManager.TRIGONS_WRATH, 0f, RavenConfig.WRATH_COOLDOWN, true);
		p.displayClientMessage(Component.translatable("message.projecthero.raven.wrath").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
		return true;
	}

	/** The Wrath ran out: drained -- calm again and no Dark Energy. */
	static void endWrath(ServerPlayer p) {
		WRATH_START.remove(p.getUUID());
		RavenState n = Raven.state(p).copy();
		n.wrathUntil = 0L;
		n.composure = RavenConfig.COMPOSURE_MAX;
		n.energy = 0f;
		Raven.save(p, n);
		Raven.reconcile(p);
		ServerLevel level = (ServerLevel) p.level();
		Raven.darkPuff(level, p.position().add(0, 1.0, 0), 30, 0.8);
		p.displayClientMessage(Component.translatable("message.projecthero.raven.drained").withStyle(ChatFormatting.GRAY), true);
	}

	// ---------------------------------------------------------------- X / Shift+X

	/** X: a streak of shadow 10 blocks along her look, through creatures, taking no damage. */
	static boolean shadowDash(ServerPlayer p) {
		Vec3 from = p.position();
		Vec3 look = p.getViewVector(1f);
		Vec3 to = from;
		for (double d = 0.5; d <= RavenConfig.DASH_DISTANCE + 1.0e-6; d += 0.5) {
			Vec3 at = from.add(look.scale(d));
			if (!p.level().noCollision(p, p.getBoundingBox().move(at.subtract(from)))) {
				break;
			}
			to = at;
		}
		long now = Raven.now(p);
		DASHES.put(p.getUUID(), new Dash(from, to, now));
		RavenState n = Raven.state(p).copy();
		n.shadowUntil = now + RavenConfig.DASH_TICKS + 2;
		Raven.save(p, n);
		fx(p, new RavenFxPayload(RavenFxPayload.STREAK, p.getId(), from.add(0, 1.0, 0), to.add(0, 1.0, 0), 14));
		sound(p, RavenSounds.DASH, 0.3f, 0.6f);
		pay(p, RavenAbilityManager.SHADOW_DASH, RavenConfig.DASH_COST, RavenConfig.DASH_COOLDOWN, false);
		return true;
	}

	/** Shift+X: sink into her shadow and erupt under the creature she looks at; claws launch it. */
	static boolean shadowStep(ServerPlayer p) {
		LivingEntity t = aimed(p, RavenConfig.STEP_RANGE);
		if (t == null) {
			Raven.say(p, "message.projecthero.raven.no_target", ChatFormatting.GRAY);
			return false;
		}
		long now = Raven.now(p);
		STEPS.put(p.getUUID(), new Step(t, now + RavenConfig.STEP_SINK_TICKS));
		RavenState n = Raven.state(p).copy();
		n.shadowUntil = now + RavenConfig.STEP_SINK_TICKS + 4;
		Raven.save(p, n);
		fx(p, new RavenFxPayload(RavenFxPayload.SINK, p.getId(), p.position(), Vec3.ZERO, RavenConfig.STEP_SINK_TICKS + 4));
		sound(p, RavenSounds.DASH, 0.3f, 0.5f);
		pay(p, RavenAbilityManager.SHADOW_STEP, RavenConfig.STEP_COST, RavenConfig.STEP_COOLDOWN, true);
		Raven.unsettle(p, RavenConfig.STEP_COMPOSURE);
		return true;
	}

	// ---------------------------------------------------------------- C / Shift+C

	/** C: 0.6 s gathering dark energy, then a wide wave rolls 12 blocks out ahead of her. */
	static boolean energyWave(ServerPlayer p) {
		long now = Raven.now(p);
		CHARGES.put(p.getUUID(), now + RavenConfig.WAVE_CHARGE_TICKS);
		fx(p, new RavenFxPayload(RavenFxPayload.CHARGE, p.getId(), p.position(), Vec3.ZERO, RavenConfig.WAVE_CHARGE_TICKS));
		sound(p, RavenSounds.CHARGE, 0.4f, 0.6f);
		pay(p, RavenAbilityManager.ENERGY_WAVE, RavenConfig.WAVE_COST, RavenConfig.WAVE_COOLDOWN, true);
		Raven.unsettle(p, RavenConfig.WAVE_COMPOSURE);
		RavenState n = Raven.state(p).copy();
		n.castUntil = now + RavenConfig.WAVE_CHARGE_TICKS + 20;
		Raven.save(p, n);
		return true;
	}

	/** For the tests: the wave is still gathering. */
	public static boolean charging(ServerPlayer p) {
		return CHARGES.containsKey(p.getUUID());
	}

	private static void waveTick(ServerPlayer p, long now, ServerLevel level) {
		Long due = CHARGES.get(p.getUUID());
		if (due != null) {
			if (now < due) {
				if (p.tickCount % 2 == 0) {
					Vec3 hand = p.getEyePosition().add(0, -0.45, 0).add(p.getViewVector(1f).scale(0.7));
					level.sendParticles(Raven.dust(Raven.VOID, 1.3f), hand.x, hand.y, hand.z, 4, 0.5, 0.5, 0.5, 0.0);
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, hand.x, hand.y, hand.z, 3, 0.6, 0.6, 0.6, 0.05);
				}
			} else {
				CHARGES.remove(p.getUUID());
				Vec3 dir = p.getViewVector(1f).multiply(1, 0, 1);
				dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
				Vec3 origin = p.position().add(dir.scale(0.6));
				WAVES.put(p.getUUID(), new Wave(origin, dir, now));
				int life = (int) Math.ceil(RavenConfig.WAVE_RANGE / RavenConfig.WAVE_SPEED);
				fx(p, new RavenFxPayload(RavenFxPayload.WAVE_FRONT, p.getId(), origin, dir, life));
				level.playSound(null, origin.x, origin.y, origin.z, RavenSounds.WAVE_FIRE, SoundSource.PLAYERS, 0.5f, 0.55f);
				level.playSound(null, origin.x, origin.y, origin.z, RavenSounds.WAVE, SoundSource.PLAYERS, 0.4f, 0.5f);
			}
		}
		Wave w = WAVES.get(p.getUUID());
		if (w == null) {
			return;
		}
		double front = Math.min(RavenConfig.WAVE_RANGE, (now - w.start + 1) * RavenConfig.WAVE_SPEED);
		AABB box = new AABB(w.origin, w.origin).inflate(RavenConfig.WAVE_RANGE + 1, 3, RavenConfig.WAVE_RANGE + 1);
		for (LivingEntity t : HeroTargets.harmable(level, p, box)) {
			if (w.struck.contains(t.getId()) || !inWave(w, t.position(), front, t.getBbWidth())) {
				continue;
			}
			w.struck.add(t.getId());
			Raven.hit(p, t, RavenConfig.WAVE_DAMAGE);
			t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RavenConfig.WAVE_SLOW_TICKS, 1), p);
			if (!Raven.isBoss(t)) {
				Vec3 k = w.dir.scale(RavenConfig.WAVE_KNOCKBACK);
				t.push(k.x, 0.4, k.z);
				t.hurtMarked = true;
			}
			Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
			level.sendParticles(Raven.dust(Raven.VOID, 1.6f), c.x, c.y, c.z, 14, 0.4, 0.5, 0.4, 0.0);
		}
		if (RavenRestore.griefing(level)) {
			breakFragile(level, w, w.lastFront, front);
		}
		w.lastFront = front;
		if (front >= RavenConfig.WAVE_RANGE) {
			WAVES.remove(p.getUUID());
		}
	}

	/** {@code pos} is inside the wave's widening fan, out to {@code front}. */
	private static boolean inWave(Wave w, Vec3 pos, double front, double width) {
		Vec3 rel = pos.subtract(w.origin);
		if (Math.abs(rel.y) > 3.0) {
			return false;
		}
		Vec3 flat = rel.multiply(1, 0, 1);
		double along = flat.dot(w.dir);
		if (along < -0.8 || along > front + width * 0.5) {
			return false;
		}
		double side = flat.subtract(w.dir.scale(along)).length();
		double half = 1.5 + (RavenConfig.WAVE_HALF_WIDTH - 1.5) * Math.max(0, along) / RavenConfig.WAVE_RANGE;
		return side <= half + width * 0.5;
	}

	/** Mob griefing on: glass and leaves the front rolls through shatter. */
	private static void breakFragile(ServerLevel level, Wave w, double from, double to) {
		BlockPos o = BlockPos.containing(w.origin);
		int r = (int) Math.ceil(RavenConfig.WAVE_RANGE) + 1;
		for (BlockPos pos : BlockPos.betweenClosed(o.offset(-r, 0, -r), o.offset(r, 3, r))) {
			Vec3 c = Vec3.atBottomCenterOf(pos);
			Vec3 flat = c.subtract(w.origin).multiply(1, 0, 1);
			double along = flat.dot(w.dir);
			if (along <= from || along > to) {
				continue;
			}
			BlockState st = level.getBlockState(pos);
			if ((RavenLeak.fragile(st) || st.is(BlockTags.LEAVES)) && inWave(w, c, to, 1.0)) {
				level.destroyBlock(pos, false);
			}
		}
	}

	/** Shift+C: Demonic Ascension -- 15 s of white eyes, a purple aura, stronger, cheaper, faster spells. Costs her calm. */
	static boolean demonicAscension(ServerPlayer p) {
		long now = Raven.now(p);
		pay(p, RavenAbilityManager.ASCENSION, RavenConfig.ASCEND_COST, RavenConfig.ASCEND_COOLDOWN, false);
		RavenState n = Raven.state(p).copy();
		n.ascendUntil = now + RavenConfig.ASCEND_TICKS;
		Raven.save(p, n);
		Raven.unsettle(p, RavenConfig.ASCEND_COMPOSURE);
		Raven.reconcile(p);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		fx(p, new RavenFxPayload(RavenFxPayload.ASCEND, p.getId(), p.position(), Vec3.ZERO, 24));
		level.sendParticles(Raven.dust(Raven.VIOLET, 1.8f), c.x, c.y, c.z, 50, 0.6, 1.0, 0.6, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 40, 0.5, 1.0, 0.5, 0.15);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.ASCEND, SoundSource.PLAYERS, 0.45f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.CHANT, SoundSource.PLAYERS, 0.5f, 0.45f);
		p.displayClientMessage(Component.translatable("message.projecthero.raven.ascended").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
		return true;
	}

	static void endAscension(ServerPlayer p) {
		RavenState n = Raven.state(p).copy();
		n.ascendUntil = 0L;
		Raven.save(p, n);
		Raven.reconcile(p);
		Raven.darkPuff((ServerLevel) p.level(), p.position().add(0, 1.0, 0), 20, 0.6);
		Raven.say(p, "message.projecthero.raven.ascension_ends", ChatFormatting.GRAY);
	}

	// ---------------------------------------------------------------- debris (the Debris Storm spell)

	/** Debris Storm: tear up 8 nearby blocks into an orbit (energy copies with mob griefing off). False if nothing is loose. */
	static boolean tearUpDebris(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		boolean real = RavenRestore.griefing(level);
		BlockPos feet = p.blockPosition();
		List<BlockPos> found = new ArrayList<>();
		int r = RavenConfig.DEBRIS_SEARCH;
		for (int dy = -2; dy <= 1; dy++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
						continue; // never the ground right under her
					}
					BlockPos pos = feet.offset(dx, dy, dz);
					if (dx * dx + dz * dz > r * r || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
						continue;
					}
					if (real ? RavenRestore.liftable(level, pos) : copyable(level, pos)) {
						found.add(pos);
					}
				}
			}
		}
		if (found.isEmpty()) {
			Raven.say(p, "message.projecthero.raven.no_debris", ChatFormatting.GRAY);
			return false;
		}
		found.sort((a, b) -> Double.compare(a.distSqr(feet), b.distSqr(feet)));
		List<RavenDebrisEntity> list = DEBRIS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		int n = Math.min(RavenConfig.DEBRIS_COUNT, found.size());
		for (int i = 0; i < n; i++) {
			BlockPos pos = found.get(i);
			BlockState state = level.getBlockState(pos);
			if (real) {
				RavenRestore.take(level, pos);
			}
			RavenDebrisEntity d = RavenDebrisEntity.spawn(p, state, Vec3.atBottomCenterOf(pos), !real, RavenDebrisEntity.MODE_ORBIT);
			d.orbitSlot = i;
			list.add(d);
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1.0,
					pos.getZ() + 0.5, 10, 0.3, 0.1, 0.3, 0.1);
		}
		DEBRIS_UNTIL.put(p.getUUID(), Raven.now(p) + RavenConfig.DEBRIS_ORBIT_TICKS);
		syncDebris(p);
		sound(p, RavenSounds.DEBRIS, 0.5f, 0.6f);
		sound(p, RavenSounds.GRAB, 0.3f, 0.6f);
		Raven.unsettle(p, RavenConfig.DEBRIS_COMPOSURE);
		return true;
	}

	/** A block her energy can copy (any solid full block, mob griefing off). */
	static boolean copyable(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return !s.isAir() && s.getFluidState().isEmpty() && s.isCollisionShapeFullBlock(level, pos);
	}

	/** Fires one orbiting block at her aim (V again with Debris Storm picked, or a left click). */
	public static boolean fireDebris(ServerPlayer p) {
		List<RavenDebrisEntity> list = DEBRIS.get(p.getUUID());
		if (list == null) {
			return false;
		}
		list.removeIf(d -> !d.isAlive() || d.mode != RavenDebrisEntity.MODE_ORBIT);
		if (list.isEmpty()) {
			syncDebris(p);
			return false;
		}
		Vec3 aim = aimPoint(p, 48.0);
		RavenDebrisEntity best = list.get(0);
		double bestDot = -2;
		Vec3 look = p.getViewVector(1f);
		for (RavenDebrisEntity d : list) {
			Vec3 to = d.position().subtract(p.position()).normalize();
			double dot = to.dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = d;
			}
		}
		list.remove(best);
		Vec3 from = best.position().add(0, 0.49, 0);
		best.throwAlong(aim.subtract(from), RavenConfig.DEBRIS_SPEED, RavenConfig.DEBRIS_DAMAGE * Raven.spellMult(p));
		sound(p, RavenSounds.THROW, 0.3f, 1.2f);
		Raven.cast(p);
		RavenSpells.breakVeil(p);
		syncDebris(p);
		return true;
	}

	public static int debrisCount(Player p) {
		List<RavenDebrisEntity> list = DEBRIS.get(p.getUUID());
		if (list == null) {
			return 0;
		}
		int n = 0;
		for (RavenDebrisEntity d : list) {
			if (d.isAlive() && d.mode == RavenDebrisEntity.MODE_ORBIT) {
				n++;
			}
		}
		return n;
	}

	private static void syncDebris(ServerPlayer p) {
		int n = debrisCount(p);
		if (Raven.state(p).debris != n) {
			RavenState s = Raven.state(p).copy();
			s.debris = n;
			Raven.save(p, s);
		}
	}

	/** Holds {@code t} where it is for {@code ticks} (the Shadow Binding's claws, the Crush's stun). */
	static void pin(ServerPlayer p, LivingEntity t, int ticks) {
		PINS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Pin(t, t.position(), Raven.now(p) + ticks));
	}

	/** For the tests: {@code t} is pinned by her right now. */
	public static boolean pinned(ServerPlayer p, LivingEntity t) {
		List<Pin> pins = PINS.get(p.getUUID());
		if (pins == null) {
			return false;
		}
		long now = Raven.now(p);
		for (Pin pin : pins) {
			if (pin.e == t && now < pin.until) {
				return true;
			}
		}
		return false;
	}

	/** The squadmate she's aiming at within 16 blocks (null = none). */
	public static LivingEntity healTarget(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(RavenConfig.HEAL_RANGE));
		BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
				e -> e instanceof Player other && other != p && other.isAlive() && Squads.areAllies(p, other), 0.5f);
		return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
	}

	// ---------------------------------------------------------------- holds

	public static boolean startHold(ServerPlayer p, String id) {
		if (RavenAbilityManager.MEDITATE.equals(id)) {
			if (Raven.inWrath(p)) {
				return false;
			}
			endHold(p, false);
			RavenSpells.endChannel(p);
			RavenFlight.startMeditation(p);
			sound(p, RavenSounds.CHANT, 0.3f, 0.6f);
			return true;
		}
		if (!ready(p, id)) {
			return false;
		}
		if (RavenAbilityManager.TELEKINESIS.equals(id)) {
			return grab(p);
		}
		return false;
	}

	public static void release(ServerPlayer p, String id) {
		switch (id) {
			case RavenAbilityManager.MEDITATE -> RavenFlight.stopMeditation(p);
			case RavenAbilityManager.TELEKINESIS -> endHold(p, true);
			default -> {
			}
		}
	}

	/** Shift+R down: wrap what she looks at -- a creature, an item, or a block -- in black energy. */
	static boolean grab(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1f);
		Vec3 end = eye.add(look.scale(RavenConfig.TK_RANGE));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 stop = block.getType() != HitResult.Type.MISS ? block.getLocation() : end;
		AABB box = p.getBoundingBox().expandTowards(stop.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, p, eye, stop, box,
				e -> (e instanceof LivingEntity le && le.isAlive() && e.isPickable() && HeroTargets.canHarm(p, e)) || e instanceof ItemEntity, 0.5f);
		Entity target = null;
		if (hit != null) {
			if (hit.getEntity() instanceof LivingEntity le && Raven.isBoss(le)) {
				Raven.say(p, "message.projecthero.raven.too_strong", ChatFormatting.GRAY);
				return false;
			}
			target = hit.getEntity();
		} else if (block.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = block.getBlockPos();
			boolean real = RavenRestore.griefing(level);
			if (real ? RavenRestore.liftable(level, pos) : copyable(level, pos)) {
				BlockState state = level.getBlockState(pos);
				if (real) {
					RavenRestore.take(level, pos);
				}
				target = RavenDebrisEntity.spawn(p, state, Vec3.atBottomCenterOf(pos), !real, RavenDebrisEntity.MODE_HELD);
			}
		}
		if (target == null) {
			Raven.say(p, "message.projecthero.raven.nothing_to_lift", ChatFormatting.GRAY);
			return false;
		}
		Hold h = new Hold();
		h.target = target;
		h.dist = Math.max(RavenConfig.TK_MIN_HOLD, Math.min(RavenConfig.TK_MAX_HOLD, eye.distanceTo(target.position())));
		h.start = Raven.now(p);
		HOLDS.put(p.getUUID(), h);
		Raven.spendEnergy(p, RavenConfig.TK_COST * Raven.costMult(p));
		RavenState n = Raven.state(p).copy();
		n.tkTarget = target.getId();
		Raven.save(p, n);
		Raven.cast(p);
		if (target instanceof LivingEntity) {
			Raven.unsettle(p, RavenConfig.TK_COMPOSURE);
			RavenSpells.breakVeil(p);
		}
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		Raven.darkPuff(level, c, 16, 0.5);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.GRAB, SoundSource.PLAYERS, 0.35f, 0.7f);
		return true;
	}

	/** The hold ends: {@code throwIt} throws along her look, otherwise it just lets go. */
	static void endHold(ServerPlayer p, boolean throwIt) {
		Hold h = HOLDS.remove(p.getUUID());
		RavenState s = Raven.state(p);
		if (s.tkTarget >= 0) {
			RavenState n = s.copy();
			n.tkTarget = -1;
			Raven.save(p, n);
		}
		if (h == null) {
			return;
		}
		Raven.startCooldown(p, RavenAbilityManager.TELEKINESIS, RavenConfig.TK_COOLDOWN);
		Entity t = h.target;
		if (t == null || !t.isAlive()) {
			return;
		}
		Vec3 look = p.getViewVector(1f);
		if (t instanceof RavenDebrisEntity d) {
			if (throwIt) {
				d.throwAlong(look, RavenConfig.TK_THROW_SPEED, RavenConfig.TK_THROW_DAMAGE * Raven.spellMult(p));
			} else {
				d.throwAlong(new Vec3(0, -1, 0), 0.2, RavenConfig.TK_THROW_DAMAGE);
			}
		} else if (throwIt) {
			Vec3 v = look.scale(RavenConfig.TK_THROW_SPEED).add(0, 0.15, 0);
			t.setDeltaMovement(v);
			t.hurtMarked = true;
			if (t instanceof LivingEntity le) {
				THROWN.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Thrown(le));
			}
		}
		if (throwIt) {
			sound(p, RavenSounds.THROW, 0.35f, 0.9f);
			Raven.cast(p);
		}
	}

	/** For the tests: the entity Telekinesis holds (null = none). */
	public static Entity held(ServerPlayer p) {
		Hold h = HOLDS.get(p.getUUID());
		return h == null ? null : h.target;
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		long now = Raven.now(p);
		UUID id = p.getUUID();
		ServerLevel level = (ServerLevel) p.level();
		holdTick(p, now);
		// thrown creatures
		List<Thrown> thrown = THROWN.get(id);
		if (thrown != null) {
			for (Iterator<Thrown> it = thrown.iterator(); it.hasNext();) {
				Thrown t = it.next();
				t.ticks++;
				if (!t.e.isAlive()) {
					it.remove();
					continue;
				}
				double sp = t.e.getDeltaMovement().length();
				boolean landed = t.ticks > 2 && (t.e.horizontalCollision || t.e.onGround() || t.e.verticalCollision);
				if (landed || t.ticks > RavenConfig.TK_THROWN_TICKS) {
					float impact = (float) Math.min(RavenConfig.TK_IMPACT_MAX, Math.max(t.speed, sp) * RavenConfig.TK_IMPACT_PER_SPEED);
					Raven.hit(p, t.e, RavenConfig.TK_THROW_DAMAGE + impact);
					Raven.darkPuff(level, t.e.position().add(0, 0.5, 0), 14, 0.5);
					it.remove();
				} else {
					t.speed = Math.max(t.speed * 0.9, sp);
				}
			}
			if (thrown.isEmpty()) {
				THROWN.remove(id);
			}
		}
		// pins (claws, stuns)
		List<Pin> pins = PINS.get(id);
		if (pins != null) {
			pins.removeIf(pin -> !pin.e.isAlive() || now >= pin.until);
			for (Pin pin : pins) {
				pin.e.setDeltaMovement(Vec3.ZERO);
				if (pin.e.position().distanceToSqr(pin.at) > 0.01) {
					pin.e.teleportTo(pin.at.x, pin.at.y, pin.at.z);
				}
				pin.e.hurtMarked = true;
			}
			if (pins.isEmpty()) {
				PINS.remove(id);
			}
		}
		liftTick(p, now, level);
		dashTick(p, now);
		stepTick(p, now, level);
		debrisTick(p, now);
		guardTick(p, now, level);
		waveTick(p, now, level);
		wrathTick(p, now, level);
		SoulSelfEntity soul = SOULS.get(id);
		if (soul != null && (!soul.isAlive() || soul.fade() > 0)) {
			SOULS.remove(id);
		}
		// the soft chant while meditating (its words are on her HUD)
		RavenState s = Raven.state(p);
		if (s.meditating && p.tickCount % 30 == 0) {
			int line = (int) ((now / 30) % 3);
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), RavenSounds.CHANT, SoundSource.PLAYERS, 0.2f, 0.5f + line * 0.08f);
		}
		// Demonic Ascension: the aura's motes off her cloak
		if (s.ascendUntil > now && p.tickCount % 3 == 0) {
			Vec3 c = p.position().add(0, 0.9, 0);
			level.sendParticles(Raven.dust(Raven.VIOLET, 1.0f), c.x, c.y, c.z, 2, 0.35, 0.6, 0.35, 0.0);
			if (p.tickCount % 9 == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y - 0.6, c.z, 2, 0.3, 0.1, 0.3, 0.02);
			}
		}
	}

	private static void holdTick(ServerPlayer p, long now) {
		Hold h = HOLDS.get(p.getUUID());
		if (h == null) {
			return;
		}
		Entity t = h.target;
		RavenState s = Raven.state(p);
		boolean creative = p.getAbilities().instabuild;
		if (t == null || !t.isAlive() || t.level() != p.level() || t.distanceTo(p) > RavenConfig.TK_RANGE + 8
				|| now - h.start > RavenConfig.TK_MAX_HOLD_TICKS || (!creative && s.energy <= 0f)) {
			endHold(p, false);
			return;
		}
		if (!creative) {
			s.energy = Math.max(0f, s.energy - RavenConfig.TK_DRAIN_PER_TICK * Raven.costMult(p));
		}
		Vec3 point = p.getEyePosition().add(p.getViewVector(1f).scale(h.dist));
		if (t instanceof RavenDebrisEntity d) {
			d.steerTo(point);
		} else {
			Vec3 centre = t.position().add(0, t.getBbHeight() * 0.5, 0);
			Vec3 v = point.subtract(centre).scale(0.35);
			if (v.length() > 1.0) {
				v = v.normalize();
			}
			t.setDeltaMovement(v.add(0, t.isNoGravity() ? 0 : 0.08, 0));
			t.hurtMarked = true;
			t.resetFallDistance();
		}
		if (p.tickCount % 2 == 0) {
			Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
			((ServerLevel) p.level()).sendParticles(Raven.dust(Raven.VOID, 1.2f), c.x, c.y, c.z, 3, t.getBbWidth() * 0.6, t.getBbHeight() * 0.5,
					t.getBbWidth() * 0.6, 0.0);
		}
	}

	private static void liftTick(ServerPlayer p, long now, ServerLevel level) {
		List<Lift> lifts = LIFTS.get(p.getUUID());
		if (lifts == null) {
			return;
		}
		for (Lift l : lifts) {
			if (l.done || !l.e.isAlive()) {
				l.done = true;
				continue;
			}
			long age = now - l.start;
			boolean boss = Raven.isBoss(l.e);
			if (age < l.rise + l.hold) {
				if (!boss) {
					double k = Math.min(1.0, (age + 1) / (double) l.rise);
					double wantY = l.baseY + l.height * k;
					AABB moved = l.e.getBoundingBox().move(0, wantY - l.e.getY(), 0);
					if (wantY > l.e.getY() && level.noCollision(l.e, moved)) {
						l.e.teleportTo(l.e.getX(), wantY, l.e.getZ());
					}
					l.e.setDeltaMovement(0, 0, 0);
					l.e.hurtMarked = true;
					l.e.resetFallDistance();
				}
				if (age % 4 == 0) {
					Vec3 c = l.e.position().add(0, l.e.getBbHeight() * 0.5, 0);
					level.sendParticles(Raven.dust(l.wrath ? Raven.RED : Raven.VOID, 1.3f), c.x, c.y, c.z, 4, l.e.getBbWidth() * 0.5,
							l.e.getBbHeight() * 0.5, l.e.getBbWidth() * 0.5, 0.0);
				}
			} else if (l.slamAt < 0) {
				l.slamAt = now;
				if (!boss) {
					// slammed: half way down this tick, onto the ground the next (driven, so no fall damage on top of the slam)
					l.groundY = groundBelow(level, l.e);
					double mid = (l.e.getY() + l.groundY) * 0.5;
					l.e.teleportTo(l.e.getX(), mid, l.e.getZ());
					l.e.setDeltaMovement(0, -1.5, 0);
					l.e.hurtMarked = true;
					l.e.resetFallDistance();
				}
			}
			if (l.slamAt >= 0 && (boss || now - l.slamAt >= 1)) {
				if (!boss) {
					l.e.teleportTo(l.e.getX(), l.groundY, l.e.getZ());
					l.e.setDeltaMovement(0, 0, 0);
					l.e.hurtMarked = true;
				}
				l.e.resetFallDistance();
				if (l.wrath) {
					Raven.hitRaw(p, l.e, l.damage);
				} else {
					Raven.hit(p, l.e, l.damage);
				}
				if (l.stun > 0) {
					l.e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, l.stun, 6), p);
					l.e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, l.stun, 2), p);
					if (!boss) {
						pin(p, l.e, l.stun);
					}
				}
				Vec3 c = l.e.position();
				level.sendParticles(Raven.dust(l.wrath ? Raven.RED : Raven.VOID, 1.8f), c.x, c.y + 0.2, c.z, 20, 0.6, 0.1, 0.6, 0.0);
				level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 1, 0, 0, 0, 0);
				if (!l.wrath) {
					fx(p, new RavenFxPayload(RavenFxPayload.WAVE, l.e.getId(), c.add(0, 0.05, 0), new Vec3(1.8, 0, 0), 10));
				}
				level.playSound(null, c.x, c.y, c.z, l.wrath ? RavenSounds.WRATH_SLAM : RavenSounds.SLAM, SoundSource.PLAYERS, 0.3f, 0.8f);
				if (l.wrath && l.e instanceof ServerPlayer victim && !com.projecthero.mod.shazam.Shazam.fearImmune(victim)) { // the fear (Shazam's courage shrugs it off)
					victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, RavenConfig.WRATH_FEAR_TICKS, 0), p);
					victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RavenConfig.WRATH_FEAR_TICKS, 1), p);
					victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, RavenConfig.WRATH_FEAR_TICKS, 1), p);
				}
				l.done = true;
			}
		}
		lifts.removeIf(l -> l.done);
		if (lifts.isEmpty()) {
			LIFTS.remove(p.getUUID());
		}
	}

	/** For the tests: creatures in her grip right now (Crush or the Wrath). */
	public static int lifting(ServerPlayer p) {
		List<Lift> lifts = LIFTS.get(p.getUUID());
		return lifts == null ? 0 : lifts.size();
	}

	/** The top of the ground under {@code e} (its feet land there), at most 24 blocks down. */
	private static double groundBelow(ServerLevel level, Entity e) {
		Vec3 from = e.position().add(0, 0.5, 0);
		BlockHitResult hit = level.clip(new ClipContext(from, from.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
		return hit.getType() == HitResult.Type.MISS ? from.y : hit.getLocation().y;
	}

	private static void dashTick(ServerPlayer p, long now) {
		Dash d = DASHES.get(p.getUUID());
		if (d == null) {
			return;
		}
		long step = now - d.start + 1;
		double k = Math.min(1.0, step / (double) RavenConfig.DASH_TICKS);
		Vec3 at = d.from.add(d.to.subtract(d.from).scale(k));
		p.teleportTo(at.x, at.y, at.z);
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
		Raven.darkPuff((ServerLevel) p.level(), at.add(0, 1.0, 0), 6, 0.3);
		if (k >= 1.0) {
			DASHES.remove(p.getUUID());
		}
	}

	private static void stepTick(ServerPlayer p, long now, ServerLevel level) {
		Step st = STEPS.get(p.getUUID());
		if (st == null) {
			return;
		}
		if (now < st.at) {
			p.setDeltaMovement(Vec3.ZERO);
			p.hurtMarked = true;
			level.sendParticles(Raven.dust(Raven.VOID, 1.6f), p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.5, 0.05, 0.5, 0.0);
			return;
		}
		STEPS.remove(p.getUUID());
		LivingEntity t = st.target;
		if (!t.isAlive() || t.level() != p.level() || t.distanceTo(p) > RavenConfig.STEP_RANGE + 6) {
			return;
		}
		Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1);
		Vec3 dir = away.lengthSqr() < 1.0e-4 ? p.getViewVector(1f).multiply(1, 0, 1).normalize() : away.normalize();
		Vec3 spot = t.position().add(dir.scale(t.getBbWidth() * 0.5 + 0.9));
		AABB box = p.getBoundingBox().move(spot.subtract(p.position()));
		if (!level.noCollision(p, box)) {
			spot = t.position().subtract(dir.scale(t.getBbWidth() * 0.5 + 0.9));
			if (!level.noCollision(p, p.getBoundingBox().move(spot.subtract(p.position())))) {
				spot = t.position();
			}
		}
		Vec3 from = p.position();
		Vec3 face = t.position().subtract(spot);
		float yaw = (float) (Math.toDegrees(Math.atan2(face.z, face.x)) - 90.0);
		p.teleportTo(level, spot.x, spot.y, spot.z, java.util.Set.of(), yaw, p.getXRot());
		p.resetFallDistance();
		Raven.hit(p, t, RavenConfig.STEP_DAMAGE);
		if (!Raven.isBoss(t)) {
			t.setDeltaMovement(t.getDeltaMovement().x * 0.2, RavenConfig.STEP_LAUNCH, t.getDeltaMovement().z * 0.2);
			t.hurtMarked = true;
		}
		fx(p, new RavenFxPayload(RavenFxPayload.CLAWS, t.getId(), t.position(), new Vec3(t.getBbWidth(), t.getBbHeight(), 1), 12));
		fx(p, new RavenFxPayload(RavenFxPayload.STREAK, p.getId(), from.add(0, 0.1, 0), spot.add(0, 0.1, 0), 10));
		level.playSound(null, spot.x, spot.y, spot.z, RavenSounds.CLAWS, SoundSource.PLAYERS, 0.5f, 0.5f);
		Raven.darkPuff(level, spot.add(0, 0.5, 0), 24, 0.6);
	}

	private static void debrisTick(ServerPlayer p, long now) {
		List<RavenDebrisEntity> list = DEBRIS.get(p.getUUID());
		if (list == null) {
			return;
		}
		Long until = DEBRIS_UNTIL.get(p.getUUID());
		if (until != null && now >= until) {
			for (RavenDebrisEntity d : list) {
				if (d.isAlive() && d.mode == RavenDebrisEntity.MODE_ORBIT) {
					d.throwAlong(new Vec3(0, -1, 0), 0.3, RavenConfig.DEBRIS_DAMAGE);
				}
			}
			list.clear();
		}
		list.removeIf(d -> !d.isAlive() || d.mode != RavenDebrisEntity.MODE_ORBIT);
		syncDebris(p);
		if (list.isEmpty()) {
			DEBRIS.remove(p.getUUID());
			DEBRIS_UNTIL.remove(p.getUUID());
		}
	}

	private static void wrathTick(ServerPlayer p, long now, ServerLevel level) {
		Long start = WRATH_START.get(p.getUUID());
		if (start == null) {
			return;
		}
		long age = now - start;
		if (age % 5 == 0) {
			Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
			level.sendParticles(Raven.dust(Raven.VOID, 2.5f), c.x, c.y, c.z, 8, p.getBbWidth() * 0.7, p.getBbHeight() * 0.4, p.getBbWidth() * 0.7, 0.0);
		}
		for (int slam : RavenConfig.WRATH_SLAMS) {
			if (age != slam) {
				continue;
			}
			double r = RavenConfig.WRATH_RADIUS;
			List<Lift> lifts = LIFTS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
			Vec3 from = p.position().add(0, p.getBbHeight() * 0.45, 0);
			for (LivingEntity t : HeroTargets.harmable(level, p, p.getBoundingBox().inflate(r))) {
				if (t.distanceTo(p) > r + p.getBbWidth()) {
					continue;
				}
				lifts.add(new Lift(t, now, 6, RavenConfig.WRATH_LIFT_TICKS - 6, 3.0, RavenConfig.WRATH_DAMAGE, true, 0));
				fx(p, new RavenFxPayload(RavenFxPayload.TENTACLE, t.getId(), from, Vec3.ZERO, RavenConfig.WRATH_LIFT_TICKS + 8));
			}
			level.playSound(null, p.getX(), p.getY(), p.getZ(), RavenSounds.WRATH_SLAM, SoundSource.PLAYERS, 0.35f, 0.6f);
		}
	}
}
