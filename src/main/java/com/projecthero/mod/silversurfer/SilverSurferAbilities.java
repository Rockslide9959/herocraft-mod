package com.projecthero.mod.silversurfer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.silversurfer.data.SilverSurferState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Silver Surfer's twelve moves (v0.17.1). Each is gated by {@link #begin} (off cooldown, enough Power Cosmic), runs as
 * a small per-player session ticked from {@link #tick}, and deals damage through {@link SilverSurferCombat}.
 *
 * <pre>
 *   R (1)  Power Cosmic Blast (tap bolt / hold beam)   Shift+R  Cosmic Barrage
 *   G (2)  Board Strike (in flight: ram charge)        Shift+G  Transmutation
 *   X (3)  Cosmic Dash                                 Shift+X  Light-Speed
 *   Z (4)  Cosmic Barrier (hold)                       Shift+Z  Molecular Phase
 *   V (5)  Cosmic Awareness (toggle)                   Shift+V  Healing Touch
 *   C (6)  Cosmic Nova                                 Shift+C  Herald's Meteor Storm
 * </pre>
 * Board flight is double-tap jump (no key); no H or N moves.
 */
public final class SilverSurferAbilities {
	public static final String BLAST = "cosmic_blast";
	public static final String BEAM = "cosmic_beam";
	public static final String BARRAGE = "cosmic_barrage";
	public static final String BOARD = "board_strike";
	public static final String TRANSMUTE = "transmutation";
	public static final String DASH = "cosmic_dash";
	public static final String LIGHT_SPEED = "light_speed";
	public static final String BARRIER = "cosmic_barrier";
	public static final String PHASE = "molecular_phase";
	public static final String AWARENESS = "cosmic_awareness";
	public static final String HEAL = "healing_touch";
	public static final String NOVA = "cosmic_nova";
	public static final String METEOR = "meteor_storm";
	/** Not a key: the board flight (guide row only). */
	public static final String SURF = "surf";

	private static final class Bolt {
		Vec3 pos;
		final Vec3 vel;
		double travelled;

		Bolt(Vec3 pos, Vec3 vel) {
			this.pos = pos;
			this.vel = vel;
		}
	}

	private static final class Orb {
		Vec3 pos;
		Vec3 vel;
		final int target;
		int life;

		Orb(Vec3 pos, Vec3 vel, int target) {
			this.pos = pos;
			this.vel = vel;
			this.target = target;
		}
	}

	private static final class Nova {
		final Vec3 centre;
		final long start;
		final Set<Integer> hit = new HashSet<>();

		Nova(Vec3 centre, long start) {
			this.centre = centre;
			this.start = start;
		}
	}

	private static final class Meteor {
		Vec3 pos;
		final Vec3 vel;
		final Vec3 land;

		Meteor(Vec3 pos, Vec3 vel, Vec3 land) {
			this.pos = pos;
			this.vel = vel;
			this.land = land;
		}
	}

	private static final class Storm {
		final Vec3 centre;
		final long end;
		final List<Meteor> live = new ArrayList<>();

		Storm(Vec3 centre, long end) {
			this.centre = centre;
			this.end = end;
		}
	}

	private static final Map<UUID, List<Bolt>> BOLTS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Orb>> ORBS = new ConcurrentHashMap<>();
	private static final Map<UUID, Nova> NOVAS = new ConcurrentHashMap<>();
	private static final Map<UUID, Storm> STORMS = new ConcurrentHashMap<>();
	/** The ram charge's victims (once each). */
	private static final Map<UUID, Set<Integer>> RAM_HIT = new ConcurrentHashMap<>();

	private SilverSurferAbilities() {
	}

	static void clearSessionState() {
		BOLTS.clear();
		ORBS.clear();
		NOVAS.clear();
		STORMS.clear();
		RAM_HIT.clear();
	}

	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		BOLTS.remove(id);
		ORBS.remove(id);
		NOVAS.remove(id);
		STORMS.remove(id);
		RAM_HIT.remove(id);
	}

	public static boolean running(ServerPlayer p, String id) {
		UUID u = p.getUUID();
		SilverSurferState s = SilverSurfer.state(p);
		long now = p.level().getGameTime();
		return switch (id) {
			case BLAST -> s.beamStart != 0L;
			case BARRAGE -> ORBS.containsKey(u);
			case BOARD -> s.ramUntil > now;
			case LIGHT_SPEED -> s.lightSpeedUntil > now;
			case BARRIER -> SilverSurfer.barrierUp(p);
			case PHASE -> s.phaseUntil > now;
			case AWARENESS -> s.awareness;
			case NOVA -> NOVAS.containsKey(u);
			case METEOR -> STORMS.containsKey(u);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- the gate

	static boolean begin(ServerPlayer p, String id, float cost) {
		if (!SilverSurfer.canAct(p)) {
			return false;
		}
		int cd = SilverSurfer.cooldownRemaining(p, id);
		if (cd > 0) {
			SilverSurfer.say(p, "message.projecthero.silver_surfer.cooldown", ChatFormatting.GRAY,
					Component.translatable("projecthero.silver_surfer.ability." + id), String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		if (!SilverSurfer.spendCosmic(p, cost)) {
			SilverSurfer.say(p, "message.projecthero.silver_surfer.low_cosmic", ChatFormatting.AQUA, (int) Math.ceil(cost),
					(int) Math.floor(SilverSurfer.cosmic(p)));
			return false;
		}
		return true;
	}

	static void cooldown(ServerPlayer p, String id, int ticks) {
		SilverSurferState n = SilverSurfer.state(p).copy();
		n.abilityReadyAt.put(id, p.level().getGameTime() + ticks);
		SilverSurfer.save(p, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float volume, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, volume, pitch);
	}

	private static Vec3 hand(ServerPlayer p) {
		return AbilityHelpers.handPosition(p);
	}

	// ================================================================ R: Power Cosmic Blast (tap bolt / hold beam)

	/** R pressed: starts the hold (a release before {@link SilverSurferConfig#BEAM_HOLD_TICKS} fires a bolt). */
	public static void pressBlast(ServerPlayer p) {
		SilverSurferState s = SilverSurfer.state(p);
		if (!SilverSurfer.canAct(p) || s.beamStart != 0L) {
			return;
		}
		if (SilverSurfer.cooldownRemaining(p, BLAST) > 0) {
			return;
		}
		SilverSurferState n = s.copy();
		n.beamStart = p.level().getGameTime();
		SilverSurfer.save(p, n);
	}

	/** R released: a quick tap is the bolt; past the hold it ends the beam. */
	public static void releaseBlast(ServerPlayer p) {
		SilverSurferState s = SilverSurfer.state(p);
		if (s.beamStart == 0L) {
			return;
		}
		long held = p.level().getGameTime() - s.beamStart;
		SilverSurferState n = s.copy();
		n.beamStart = 0L;
		n.beamEnd = List.of();
		SilverSurfer.save(p, n);
		if (held < SilverSurferConfig.BEAM_HOLD_TICKS) {
			bolt(p);
		} else {
			cooldown(p, BLAST, SilverSurferConfig.BEAM_COOLDOWN);
		}
	}

	/** The tap: a dodgeable bolt of Power Cosmic from the hand. */
	public static void bolt(ServerPlayer p) {
		if (!SilverSurfer.spendCosmic(p, SilverSurferConfig.BOLT_COST)) {
			SilverSurfer.say(p, "message.projecthero.silver_surfer.low_cosmic", ChatFormatting.AQUA, (int) SilverSurferConfig.BOLT_COST,
					(int) Math.floor(SilverSurfer.cosmic(p)));
			return;
		}
		cooldown(p, BLAST, SilverSurferConfig.BOLT_COOLDOWN);
		SilverSurfer.setAnim(p, SilverSurferState.ANIM_BLAST);
		Vec3 from = hand(p);
		Vec3 aim = AbilityHelpers.aimPoint(p, SilverSurferConfig.BOLT_RANGE);
		Vec3 dir = aim.subtract(from);
		dir = dir.lengthSqr() < 1.0e-4 ? p.getLookAngle() : dir.normalize();
		BOLTS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Bolt(from, dir.scale(SilverSurferConfig.BOLT_SPEED)));
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.7f, 2.0f);
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
	}

	private static void tickBolts(ServerPlayer p) {
		List<Bolt> list = BOLTS.get(p.getUUID());
		if (list == null) {
			return;
		}
		ServerLevel level = level(p);
		Iterator<Bolt> it = list.iterator();
		while (it.hasNext()) {
			Bolt b = it.next();
			LivingEntity[] out = new LivingEntity[1];
			Vec3 dir = b.vel.normalize();
			Vec3 end = SilverSurferCombat.ray(p, b.pos, dir, b.vel.length(), 0.3, out);
			AbilityHelpers.line(level, b.pos, end, SilverSurfer.COSMIC, 3.0);
			level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 1, 0.02, 0.02, 0.02, 0.0);
			b.travelled += b.pos.distanceTo(end);
			if (out[0] != null) {
				SilverSurferCombat.strike(p, out[0], b.pos, SilverSurferConfig.BOLT_DAMAGE, 0.5, 0.1);
				level.sendParticles(SilverSurfer.SILVER_BIG, end.x, end.y, end.z, 8, 0.2, 0.2, 0.2, 0.0);
				it.remove();
				continue;
			}
			if (end.distanceToSqr(b.pos.add(b.vel)) > 1.0e-3 || b.travelled >= SilverSurferConfig.BOLT_RANGE) {
				level.sendParticles(SilverSurfer.SILVER, end.x, end.y, end.z, 6, 0.15, 0.15, 0.15, 0.0);
				it.remove();
				continue;
			}
			b.pos = end;
		}
		if (list.isEmpty()) {
			BOLTS.remove(p.getUUID());
		}
	}

	private static void tickBeam(ServerPlayer p, long now) {
		SilverSurferState s = SilverSurfer.state(p);
		if (s.beamStart == 0L) {
			return;
		}
		long held = now - s.beamStart;
		if (held < SilverSurferConfig.BEAM_HOLD_TICKS) {
			return;
		}
		if (held > SilverSurferConfig.BEAM_HOLD_TICKS + SilverSurferConfig.BEAM_MAX_TICKS
				|| (now % 5 == 0 && !SilverSurfer.spendCosmic(p, SilverSurferConfig.BEAM_COST_PER_SECOND / 4f))) {
			releaseBlast(p);
			return;
		}
		if (held == SilverSurferConfig.BEAM_HOLD_TICKS) {
			SilverSurfer.setAnim(p, SilverSurferState.ANIM_BLAST);
			sound(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.9f);
		}
		LivingEntity[] out = new LivingEntity[1];
		Vec3 from = hand(p);
		Vec3 end = SilverSurferCombat.ray(p, p.getEyePosition(), p.getLookAngle(), SilverSurferConfig.BEAM_RANGE, 0.25, out);
		if (held % 2 == 0) {
			SilverSurferState n = SilverSurfer.state(p).copy();
			n.beamEnd = List.of(end.x, end.y, end.z);
			SilverSurfer.save(p, n);
		}
		ServerLevel level = level(p);
		if (held % 3 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 3, 0.1, 0.1, 0.1, 0.05);
		}
		if (out[0] != null && (held - SilverSurferConfig.BEAM_HOLD_TICKS) % SilverSurferConfig.BEAM_INTERVAL == 0) {
			SilverSurferCombat.strike(p, out[0], from, SilverSurferConfig.BEAM_DAMAGE, 0.15, 0.0);
		}
		if (held % 20 == 0) {
			sound(p, SoundEvents.BEACON_AMBIENT, 0.8f, 2.0f);
		}
	}

	// ================================================================ Shift+R: Cosmic Barrage

	public static void barrage(ServerPlayer p) {
		if (!begin(p, BARRAGE, SilverSurferConfig.BARRAGE_COST)) {
			return;
		}
		cooldown(p, BARRAGE, SilverSurferConfig.BARRAGE_COOLDOWN);
		SilverSurfer.setAnim(p, SilverSurferState.ANIM_BARRAGE);
		// up to 8 targets in front, nearest first (the leftover orbs double up on them)
		List<LivingEntity> targets = new ArrayList<>();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : SilverSurferCombat.within(p, eye, SilverSurferConfig.BARRAGE_RANGE)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.normalize().dot(look) > 0.5) {
				targets.add(e);
			}
		}
		targets.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
		List<Orb> orbs = ORBS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		for (int i = 0; i < SilverSurferConfig.BARRAGE_ORBS; i++) {
			double a = Math.PI * 2 * i / SilverSurferConfig.BARRAGE_ORBS;
			Vec3 spread = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
			Vec3 pos = eye.add(look.scale(0.8)).add(spread.scale(0.9));
			int target = targets.isEmpty() ? -1 : targets.get(i % targets.size()).getId();
			orbs.add(new Orb(pos, look.scale(0.5).add(spread.scale(0.45)), target));
		}
		sound(p, SoundEvents.EVOKER_CAST_SPELL, 1.0f, 1.6f);
		sound(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0f, 1.4f);
	}

	private static void tickOrbs(ServerPlayer p) {
		List<Orb> list = ORBS.get(p.getUUID());
		if (list == null) {
			return;
		}
		ServerLevel level = level(p);
		Iterator<Orb> it = list.iterator();
		while (it.hasNext()) {
			Orb o = it.next();
			o.life++;
			Entity t = o.target == -1 ? null : level.getEntity(o.target);
			if (t instanceof LivingEntity le && le.isAlive()) {
				Vec3 want = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(o.pos).normalize().scale(SilverSurferConfig.BARRAGE_SPEED);
				o.vel = o.vel.add(want.subtract(o.vel).scale(0.25));
			} else if (o.life > 6) {
				o.vel = o.vel.normalize().scale(SilverSurferConfig.BARRAGE_SPEED);
			}
			Vec3 next = o.pos.add(o.vel);
			BlockHitResult bhr = level.clip(new ClipContext(o.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			boolean done = bhr.getType() != HitResult.Type.MISS || o.life > SilverSurferConfig.BARRAGE_LIFE;
			if (!done) {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(o.pos, next).inflate(0.4),
						e -> SilverSurferCombat.isTarget(p, e))) {
					SilverSurferCombat.strike(p, e, o.pos, SilverSurferConfig.BARRAGE_DAMAGE, 0.3, 0.05);
					done = true;
					break;
				}
			}
			level.sendParticles(SilverSurfer.COSMIC, next.x, next.y, next.z, 2, 0.05, 0.05, 0.05, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, next.x, next.y, next.z, 1, 0, 0, 0, 0.0);
			if (done) {
				level.sendParticles(SilverSurfer.SILVER_BIG, next.x, next.y, next.z, 5, 0.15, 0.15, 0.15, 0.0);
				it.remove();
				continue;
			}
			o.pos = next;
		}
		if (list.isEmpty()) {
			ORBS.remove(p.getUUID());
		}
	}

	// ================================================================ G: Board Strike (in flight: the ram)

	public static void boardStrike(ServerPlayer p) {
		if (!begin(p, BOARD, SilverSurferConfig.BOARD_COST)) {
			return;
		}
		cooldown(p, BOARD, SilverSurferConfig.BOARD_COOLDOWN);
		SilverSurferState s = SilverSurfer.state(p);
		long now = p.level().getGameTime();
		if (s.surfing) {
			// the ram: the board drives him forward and hits everything on the way
			SilverSurferState n = s.copy();
			n.ramUntil = now + SilverSurferConfig.RAM_TICKS;
			SilverSurfer.save(p, n);
			RAM_HIT.put(p.getUUID(), ConcurrentHashMap.newKeySet());
			AbilityHelpers.launchSelf(p, p.getLookAngle().scale(SilverSurferConfig.RAM_SPEED));
			sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.7f);
			sound(p, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.0f, 1.2f);
			return;
		}
		SilverSurfer.setAnim(p, SilverSurferState.ANIM_THROW);
		LivingEntity[] out = new LivingEntity[1];
		Vec3 far = SilverSurferCombat.ray(p, SilverSurferConfig.BOARD_RANGE, 0.8, out);
		ThrownSurfboardEntity board = SilverSurferMod.THROWN_BOARD.create(p.level());
		if (board == null) {
			return;
		}
		board.launch(p, out[0], far);
		p.level().addFreshEntity(board);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 1.0f, 0.8f);
	}

	private static void tickRam(ServerPlayer p, long now) {
		SilverSurferState s = SilverSurfer.state(p);
		if (s.ramUntil == 0L) {
			return;
		}
		if (s.ramUntil <= now) {
			SilverSurferState n = s.copy();
			n.ramUntil = 0L;
			SilverSurfer.save(p, n);
			RAM_HIT.remove(p.getUUID());
			return;
		}
		Set<Integer> hit = RAM_HIT.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet());
		Vec3 v = p.getKnownMovement();
		AABB box = p.getBoundingBox().inflate(1.2).expandTowards(v.scale(-1));
		for (LivingEntity e : level(p).getEntitiesOfClass(LivingEntity.class, box, e -> SilverSurferCombat.isTarget(p, e))) {
			if (hit.add(e.getId())) {
				SilverSurferCombat.strike(p, e, p.position(), SilverSurferConfig.RAM_DAMAGE, 1.6, 0.4);
				level(p).playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.4f);
			}
		}
		level(p).sendParticles(SilverSurfer.SILVER_BIG, p.getX(), p.getY() + 0.2, p.getZ(), 4, 0.4, 0.2, 0.4, 0.0);
	}

	// ================================================================ Shift+G: Transmutation

	public static void transmute(ServerPlayer p) {
		LivingEntity[] out = new LivingEntity[1];
		Vec3 end = SilverSurferCombat.ray(p, SilverSurferConfig.TRANSMUTE_RANGE, 0.6, out);
		if (out[0] == null) {
			SilverSurfer.say(p, "message.projecthero.silver_surfer.no_target", ChatFormatting.GRAY);
			return;
		}
		if (!begin(p, TRANSMUTE, SilverSurferConfig.TRANSMUTE_COST)) {
			return;
		}
		cooldown(p, TRANSMUTE, SilverSurferConfig.TRANSMUTE_COOLDOWN);
		SilverSurfer.setAnim(p, SilverSurferState.ANIM_TRANSMUTE);
		AbilityHelpers.line(level(p), hand(p), end, SilverSurfer.SILVER, 4.0);
		if (!SilverSurferStatues.transmute(p, out[0])) {
			// a boss (or a player the server shields from hard crowd control): only slowed, and a little hurt
			SilverSurferCombat.strike(p, out[0], p.position(), SilverSurferConfig.BOSS_TRANSMUTE_DAMAGE, 0.0, 0.0);
		}
		sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 1.3f);
	}

	// ================================================================ X: Cosmic Dash

	public static void dash(ServerPlayer p) {
		if (!begin(p, DASH, SilverSurferConfig.DASH_COST)) {
			return;
		}
		cooldown(p, DASH, SilverSurferConfig.DASH_COOLDOWN);
		SilverSurfer.setAnim(p, SilverSurferState.ANIM_DASH);
		ServerLevel level = level(p);
		Vec3 look = p.getLookAngle();
		Vec3 start = p.position();
		// stop short of the first wall (the body's height swept along the aim)
		double dist = SilverSurferConfig.DASH_DISTANCE;
		for (double y : new double[] { 0.1, p.getBbHeight() * 0.5, p.getBbHeight() - 0.1 }) {
			Vec3 a = start.add(0, y, 0);
			BlockHitResult bhr = level.clip(new ClipContext(a, a.add(look.scale(dist)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (bhr.getType() != HitResult.Type.MISS) {
				dist = Math.max(0.0, a.distanceTo(bhr.getLocation()) - 0.7);
			}
		}
		Vec3 end = start.add(look.scale(dist));
		// everything it passes takes the streak
		AABB sweep = new AABB(start, end).inflate(1.0).expandTowards(0, p.getBbHeight(), 0);
		Vec3 dir = end.subtract(start);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> SilverSurferCombat.isTarget(p, e))) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			double t = dir.lengthSqr() < 1.0e-6 ? 0 : Math.max(0, Math.min(1, c.subtract(start).dot(dir) / dir.lengthSqr()));
			if (start.add(dir.scale(t)).add(0, p.getBbHeight() * 0.5, 0).distanceToSqr(c) <= 2.6 * 2.6) {
				SilverSurferCombat.strike(p, e, start, SilverSurferConfig.DASH_DAMAGE, 0.8, 0.25);
			}
		}
		for (double y : new double[] { 0.3, 1.0, 1.6 }) {
			AbilityHelpers.line(level, start.add(0, y, 0), end.add(0, y, 0), SilverSurfer.SILVER, 2.5);
		}
		AbilityHelpers.line(level, start.add(0, 1.0, 0), end.add(0, 1.0, 0), ParticleTypes.END_ROD, 1.0);
		p.connection.teleport(end.x, end.y, end.z, p.getYRot(), p.getXRot());
		p.resetFallDistance();
		level.playSound(null, start.x, start.y, start.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.8f);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8f, 1.6f);
	}

	// ================================================================ Shift+X: Light-Speed

	public static void lightSpeed(ServerPlayer p) {
		if (!begin(p, LIGHT_SPEED, SilverSurferConfig.LIGHT_SPEED_COST)) {
			return;
		}
		if (!SilverSurferFlight.lightSpeed(p)) {
			SilverSurfer.setCosmic(p, SilverSurfer.cosmic(p) + SilverSurferConfig.LIGHT_SPEED_COST); // refund
			return;
		}
		cooldown(p, LIGHT_SPEED, SilverSurferConfig.LIGHT_SPEED_COOLDOWN);
	}

	// ================================================================ Z: Cosmic Barrier (held)

	public static void raiseBarrier(ServerPlayer p) {
		if (SilverSurfer.barrierUp(p) || !begin(p, BARRIER, 0f)) {
			return;
		}
		if (SilverSurfer.cosmic(p) < SilverSurferConfig.BARRIER_COST_PER_SECOND / 2f) {
			SilverSurfer.say(p, "message.projecthero.silver_surfer.low_cosmic", ChatFormatting.AQUA, (int) SilverSurferConfig.BARRIER_COST_PER_SECOND,
					(int) Math.floor(SilverSurfer.cosmic(p)));
			return;
		}
		SilverSurferState n = SilverSurfer.state(p).copy();
		n.barrierUntil = p.level().getGameTime() + SilverSurferConfig.BARRIER_MAX_TICKS;
		n.barrierHp = SilverSurferConfig.BARRIER_HP;
		SilverSurfer.save(p, n);
		sound(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.8f);
		sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);
	}

	public static void lowerBarrier(ServerPlayer p) {
		SilverSurferState s = SilverSurfer.state(p);
		if (s.barrierUntil == 0L) {
			return;
		}
		boolean broke = s.barrierHp <= 0f;
		SilverSurferState n = s.copy();
		n.barrierUntil = 0L;
		n.barrierHp = 0f;
		if (!broke) {
			n.abilityReadyAt.put(BARRIER, p.level().getGameTime() + SilverSurferConfig.BARRIER_COOLDOWN);
		}
		SilverSurfer.save(p, n);
		sound(p, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.9f);
	}

	private static void tickBarrier(ServerPlayer p, long now) {
		SilverSurferState s = SilverSurfer.state(p);
		if (s.barrierUntil == 0L) {
			return;
		}
		if (s.barrierUntil <= now || s.barrierHp <= 0f
				|| (now % 5 == 0 && !SilverSurfer.spendCosmic(p, SilverSurferConfig.BARRIER_COST_PER_SECOND / 4f))) {
			lowerBarrier(p);
			return;
		}
		// projectiles coming at the front are turned around and sent back as his
		ServerLevel level = level(p);
		Vec3 eye = p.getEyePosition();
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(SilverSurferConfig.REFLECT_RANGE),
				e -> e.getOwner() != p && e.isAlive())) {
			Vec3 v = proj.getDeltaMovement();
			Vec3 toMe = eye.subtract(proj.position());
			if (v.lengthSqr() < 1.0e-3 || v.dot(toMe) <= 0 || !SilverSurferDamage.inFront(p, proj.position())) {
				continue;
			}
			proj.setDeltaMovement(v.scale(-1.2));
			proj.setOwner(p);
			proj.hurtMarked = true;
			proj.hasImpulse = true;
			level.sendParticles(ParticleTypes.END_ROD, proj.getX(), proj.getY(), proj.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
			level.playSound(null, proj.getX(), proj.getY(), proj.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 1.8f);
		}
	}

	// ================================================================ Shift+Z: Molecular Phase

	public static void phase(ServerPlayer p) {
		if (SilverSurfer.phasing(p) || !begin(p, PHASE, SilverSurferConfig.PHASE_COST)) {
			return;
		}
		cooldown(p, PHASE, SilverSurferConfig.PHASE_COOLDOWN);
		SilverSurferState n = SilverSurfer.state(p).copy();
		n.phaseUntil = p.level().getGameTime() + SilverSurferConfig.PHASE_TICKS;
		SilverSurfer.save(p, n);
		level(p).sendParticles(SilverSurfer.SILVER, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
		sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.4f);
	}

	private static void tickPhase(ServerPlayer p, long now) {
		SilverSurferState s = SilverSurfer.state(p);
		if (s.phaseUntil == 0L) {
			return;
		}
		if (s.phaseUntil <= now) {
			SilverSurferState n = s.copy();
			n.phaseUntil = 0L;
			SilverSurfer.save(p, n);
			com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.settleOutsideBlocks(p);
			sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8f, 0.9f);
			return;
		}
		if (now % 4 == 0) {
			level(p).sendParticles(SilverSurfer.SILVER, p.getX(), p.getY() + 1, p.getZ(), 2, 0.3, 0.6, 0.3, 0.0);
		}
	}

	// ================================================================ V: Cosmic Awareness / Shift+V: Healing Touch

	public static void toggleAwareness(ServerPlayer p) {
		if (!SilverSurfer.canAct(p) || SilverSurfer.cooldownRemaining(p, AWARENESS) > 0) {
			return;
		}
		cooldown(p, AWARENESS, SilverSurferConfig.AWARENESS_COOLDOWN);
		setAwareness(p, !SilverSurfer.state(p).awareness);
	}

	static void setAwareness(ServerPlayer p, boolean on) {
		SilverSurferState n = SilverSurfer.state(p).copy();
		if (n.awareness == on) {
			return;
		}
		n.awareness = on;
		SilverSurfer.save(p, n);
		sound(p, on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 0.6f, 2.0f);
		SilverSurfer.say(p, on ? "message.projecthero.silver_surfer.awareness_on" : "message.projecthero.silver_surfer.awareness_off",
				ChatFormatting.AQUA);
	}

	public static void heal(ServerPlayer p) {
		if (!begin(p, HEAL, SilverSurferConfig.HEAL_COST)) {
			return;
		}
		cooldown(p, HEAL, SilverSurferConfig.HEAL_COOLDOWN);
		SilverSurfer.setAnim(p, SilverSurferState.ANIM_HEAL);
		ServerLevel level = level(p);
		List<LivingEntity> healed = new ArrayList<>();
		healed.add(p);
		for (Player other : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(SilverSurferConfig.HEAL_RADIUS),
				o -> o != p && o.isAlive() && !com.projecthero.mod.combat.HeroTargets.isHostile(p, o))) {
			healed.add(other);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SilverSurferConfig.HEAL_RADIUS),
				e -> !(e instanceof Player) && e.isAlive() && com.projecthero.mod.combat.HeroTargets.isOwnedBy(e, p))) {
			healed.add(e);
		}
		for (LivingEntity e : healed) {
			e.heal(SilverSurferConfig.HEAL_AMOUNT);
			cure(e);
			level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 3, 0.3, 0.1, 0.3, 0.0);
			level.sendParticles(SilverSurfer.SILVER, e.getX(), e.getY() + 1, e.getZ(), 16, 0.4, 0.7, 0.4, 0.0);
		}
		sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.8f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.5f);
	}

	/** Cures every harmful effect (poison, wither, slowness, blindness ...). */
	private static void cure(LivingEntity e) {
		List<net.minecraft.core.Holder<MobEffect>> bad = new ArrayList<>();
		for (MobEffectInstance inst : e.getActiveEffects()) {
			if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				bad.add(inst.getEffect());
			}
		}
		for (var h : bad) {
			e.removeEffect(h);
		}
		e.setTicksFrozen(0);
		e.clearFire();
	}

	// ================================================================ C: Cosmic Nova / Shift+C: Herald's Meteor Storm

	public static void nova(ServerPlayer p) {
		if (NOVAS.containsKey(p.getUUID()) || !begin(p, NOVA, SilverSurferConfig.NOVA_COST)) {
			return;
		}
		cooldown(p, NOVA, SilverSurferConfig.NOVA_COOLDOWN);
		SilverSurfer.setAnim(p, SilverSurferState.ANIM_NOVA);
		NOVAS.put(p.getUUID(), new Nova(p.position().add(0, 1.0, 0), p.level().getGameTime()));
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 0.2, 0.3, 0.2, 0.45);
		sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.7f);
		sound(p, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.6f);
	}

	private static void tickNova(ServerPlayer p, long now) {
		Nova nv = NOVAS.get(p.getUUID());
		if (nv == null) {
			return;
		}
		double r = Math.min(SilverSurferConfig.NOVA_RADIUS, (now - nv.start + 1) * 1.25);
		ServerLevel level = level(p);
		int points = (int) (r * 10);
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			double x = nv.centre.x + Math.cos(a) * r;
			double z = nv.centre.z + Math.sin(a) * r;
			com.projecthero.mod.galactus.GalactusFx.forced(level, i % 2 == 0 ? ParticleTypes.END_ROD : SilverSurfer.SILVER, x, nv.centre.y - 0.6, z, 1, 0, 0.3, 0, 0.0);
			if (i % 2 == 0) {
				com.projecthero.mod.galactus.GalactusFx.forced(level, SilverSurfer.COSMIC, x, nv.centre.y + 0.4, z, 1, 0, 0.4, 0, 0.0);
			}
		}
		for (LivingEntity e : SilverSurferCombat.within(p, nv.centre, r + 0.5)) {
			if (nv.hit.add(e.getId())) {
				double d = Math.sqrt(e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(nv.centre));
				float k = (float) Math.max(0, Math.min(1, d / SilverSurferConfig.NOVA_RADIUS));
				float dmg = SilverSurferConfig.NOVA_DAMAGE_CENTRE + (SilverSurferConfig.NOVA_DAMAGE_EDGE - SilverSurferConfig.NOVA_DAMAGE_CENTRE) * k;
				SilverSurferCombat.strike(p, e, nv.centre, dmg, 1.4, 0.5);
			}
		}
		if (r >= SilverSurferConfig.NOVA_RADIUS) {
			NOVAS.remove(p.getUUID());
		}
	}

	public static void meteorStorm(ServerPlayer p) {
		if (STORMS.containsKey(p.getUUID())) {
			return;
		}
		Vec3 eye = p.getEyePosition();
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(SilverSurferConfig.METEOR_RANGE)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 at = bhr.getType() == HitResult.Type.MISS ? groundBelow(p.level(), eye.add(p.getLookAngle().scale(SilverSurferConfig.METEOR_RANGE)))
				: bhr.getLocation();
		if (!begin(p, METEOR, SilverSurferConfig.METEOR_COST)) {
			return;
		}
		cooldown(p, METEOR, SilverSurferConfig.METEOR_COOLDOWN);
		SilverSurfer.setAnim(p, SilverSurferState.ANIM_METEOR);
		STORMS.put(p.getUUID(), new Storm(at, p.level().getGameTime() + SilverSurferConfig.METEOR_TICKS));
		sound(p, SoundEvents.EVOKER_PREPARE_SUMMON, 1.2f, 0.7f);
		level(p).playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 1.6f);
	}

	private static Vec3 groundBelow(Level level, Vec3 from) {
		BlockHitResult down = level.clip(new ClipContext(from, from.add(0, -48, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return down.getType() == HitResult.Type.MISS ? from : down.getLocation();
	}

	private static void tickStorm(ServerPlayer p, long now) {
		Storm st = STORMS.get(p.getUUID());
		if (st == null) {
			return;
		}
		ServerLevel level = level(p);
		if (now < st.end && now % SilverSurferConfig.METEOR_INTERVAL == 0) {
			double a = p.getRandom().nextDouble() * Math.PI * 2;
			double r = Math.sqrt(p.getRandom().nextDouble()) * SilverSurferConfig.METEOR_SPREAD;
			Vec3 land = groundBelow(level, st.centre.add(Math.cos(a) * r, 6, Math.sin(a) * r));
			Vec3 from = land.add(-8 + p.getRandom().nextDouble() * 4, 30, -8 + p.getRandom().nextDouble() * 4);
			st.live.add(new Meteor(from, land.subtract(from).scale(1.0 / 10.0), land));
		}
		Iterator<Meteor> it = st.live.iterator();
		while (it.hasNext()) {
			Meteor m = it.next();
			Vec3 next = m.pos.add(m.vel);
			AbilityHelpers.line(level, m.pos, next, ParticleTypes.FLAME, 2.0);
			com.projecthero.mod.galactus.GalactusFx.forced(level, ParticleTypes.LARGE_SMOKE, next.x, next.y, next.z, 2, 0.2, 0.2, 0.2, 0.0);
			com.projecthero.mod.galactus.GalactusFx.forced(level, SilverSurfer.COSMIC, next.x, next.y, next.z, 3, 0.25, 0.25, 0.25, 0.0);
			if (next.distanceToSqr(m.land) <= m.vel.lengthSqr() || next.y <= m.land.y) {
				impact(p, level, m.land);
				it.remove();
			} else {
				m.pos = next;
			}
		}
		if (now >= st.end && st.live.isEmpty()) {
			STORMS.remove(p.getUUID());
		}
	}

	private static void impact(ServerPlayer p, ServerLevel level, Vec3 at) {
		if (AbilityHelpers.canGrief()) {
			level.explode(p, at.x, at.y, at.z, 1.6f, false, Level.ExplosionInteraction.MOB);
		} else {
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.3f);
		}
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, 6, 0.5, 0.1, 0.5, 0.0);
		level.sendParticles(SilverSurfer.SILVER_BIG, at.x, at.y + 0.3, at.z, 10, 0.8, 0.2, 0.8, 0.0);
		for (LivingEntity e : SilverSurferCombat.within(p, at.add(0, 0.8, 0), SilverSurferConfig.METEOR_RADIUS)) {
			SilverSurferCombat.strike(p, e, at, SilverSurferConfig.METEOR_DAMAGE, 0.6, 0.35);
		}
	}

	// ================================================================ the tick

	static void tick(ServerPlayer p, long now) {
		tickBeam(p, now);
		tickBolts(p);
		tickOrbs(p);
		tickRam(p, now);
		tickBarrier(p, now);
		tickPhase(p, now);
		tickNova(p, now);
		tickStorm(p, now);
		SilverSurferState s = SilverSurfer.state(p);
		if (s.animId != SilverSurferState.ANIM_NONE && now - s.animStart > 30) {
			SilverSurferState n = s.copy();
			n.animId = SilverSurferState.ANIM_NONE;
			SilverSurfer.save(p, n);
		}
	}
}
