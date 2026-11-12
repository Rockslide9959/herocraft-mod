package com.projecthero.mod.starfire;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.beamclash.BeamClash;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.starfire.data.StarfireState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Starfire: every move ({@code docs/TEEN_TITANS_DESIGN.md}). The bolts ride the shared {@link AlienProjectileEntity}
 * (kinds {@link #BOLT_KIND} / {@link #BURST_KIND}; the client draws them, {@code client.starfire.StarfireFx}); their damage is
 * hers ({@link Starfire#hit}) and read from {@link #BOLTS} when they land. The running moves (dash, dive, charge, pound, grab,
 * barrage, the Supernova charge, the eye beams) live in {@link Run} per player and step in {@link #tick}. All static maps are
 * dropped by {@link #clearSessionState} ({@code ServerStateReset}).
 */
public final class StarfireMoves {
	public static final String BOLT_KIND = "starfire_bolt";
	public static final String BURST_KIND = "starfire_burst";

	/** One bolt in flight: what it does when it lands. */
	record Bolt(UUID owner, float damage, float radius, boolean burst, long born) {
	}

	/** The running moves of one player. */
	public static final class Run {
		int hand = 1;
		// Star Dash
		int dashLeft;
		Vec3 dashDir = Vec3.ZERO;
		// Comet Dive: 0 none, 1 launch, 2 dive
		int diveStage;
		int diveTicks;
		Vec3 diveDir = Vec3.ZERO;
		// Tamaranean Charge
		int chargeLeft;
		Vec3 chargeDir = Vec3.ZERO;
		LivingEntity carried;
		boolean wallHit;
		// Ground Pound
		boolean pounding;
		int poundTicks;
		// Tamaranean Grab
		LivingEntity grabbed;
		long grabStart;
		// Barrage
		long barrageStart;
		int barrageFired = -1;

		/** Barrage bolts fired so far (-1 = no barrage running). */
		public int barrageFired() {
			return barrageFired;
		}
		// Eye Beams
		long beamStart;
		// Supernova charge
		boolean novaRise;
	}

	private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();
	/** Projectile UUID -> what it does. */
	private static final Map<UUID, Bolt> BOLTS = new ConcurrentHashMap<>();
	/** Barrage bolts that home in a little. */
	private static final List<AlienProjectileEntity> HOMING = new ArrayList<>();

	private StarfireMoves() {
	}

	static void clearSessionState() {
		RUNS.clear();
		BOLTS.clear();
		synchronized (HOMING) {
			HOMING.clear();
		}
	}

	static void forget(UUID id) {
		RUNS.remove(id);
	}

	static Run run(ServerPlayer p) {
		return RUNS.computeIfAbsent(p.getUUID(), k -> new Run());
	}

	/** Called once at init: what the bolts do when they hit. */
	static void initialize() {
		AlienProjectileEntity.onHit(BOLT_KIND, StarfireMoves::boltLanded);
		AlienProjectileEntity.onHit(BURST_KIND, StarfireMoves::boltLanded);
	}

	/** Ends every running move (death, a world change, leaving, revoke). A carried creature is let go. */
	static void endAll(ServerPlayer p) {
		Run r = RUNS.get(p.getUUID());
		if (r != null) {
			dropGrabbed(p, r, false);
			r.carried = null;
			r.dashLeft = 0;
			r.diveStage = 0;
			r.chargeLeft = 0;
			r.pounding = false;
			r.barrageFired = -1;
		}
		StarfireRadiance.stop(p.getUUID(), p.getServer());
	}

	// ================================================================ routing

	/** A press. True if the move went off. */
	public static boolean use(ServerPlayer p, String id) {
		return switch (id) {
			case StarfireAbilityManager.STARBOLT -> starbolt(p);
			case StarfireAbilityManager.GRAB -> grab(p);
			case StarfireAbilityManager.BARRAGE -> barrage(p);
			case StarfireAbilityManager.SUPERNOVA -> supernova(p);
			case StarfireAbilityManager.STAR_DASH -> starDash(p);
			case StarfireAbilityManager.COMET_DIVE -> cometDive(p);
			case StarfireAbilityManager.CHARGE -> charge(p);
			case StarfireAbilityManager.GROUND_POUND -> groundPound(p);
			case StarfireAbilityManager.KISS -> kiss(p);
			case StarfireAbilityManager.RADIANCE -> radiance(p);
			default -> false;
		};
	}

	/** A hold begins (Shift+R Star Burst, Shift+G Eye Beams). False if refused (its release is then ignored). */
	public static boolean startHold(ServerPlayer p, String id) {
		if (StarfireAbilityManager.STAR_BURST.equals(id)) {
			return startBurst(p);
		}
		if (StarfireAbilityManager.EYE_BEAMS.equals(id)) {
			return startBeams(p);
		}
		return false;
	}

	public static void release(ServerPlayer p, String id) {
		if (StarfireAbilityManager.STAR_BURST.equals(id)) {
			releaseBurst(p);
		} else if (StarfireAbilityManager.EYE_BEAMS.equals(id)) {
			stopBeams(p);
		}
	}

	private static boolean ready(ServerPlayer p, String id) {
		return Starfire.cooldownRemaining(p, id) <= 0;
	}

	// ================================================================ R: Starbolt

	/** The damage one starbolt does right now (Fury x1..x1.6). */
	public static float boltDamage(Player p) {
		return StarfireConfig.BOLT_DAMAGE * Starfire.boltMult(p);
	}

	public static boolean starbolt(ServerPlayer p) {
		if (!ready(p, StarfireAbilityManager.STARBOLT) || !Starfire.spend(p, Starfire.boltCost(p, StarfireConfig.BOLT_COST))) {
			return false;
		}
		Run r = run(p);
		r.hand = -r.hand;
		fireBolt(p, Starfire.handPos(p, r.hand), aim(p, Starfire.handPos(p, r.hand)), boltDamage(p), StarfireConfig.BOLT_BLAST_RADIUS, false, false);
		Starfire.startCooldown(p, StarfireAbilityManager.STARBOLT, StarfireConfig.BOLT_COOLDOWN);
		Starfire.sound(p, SoundEvents.FIRECHARGE_USE, 0.22f, 1.7f + p.getRandom().nextFloat() * 0.2f);
		return true;
	}

	/** From a hand toward whatever the crosshair is on (so the alternating hands still converge). */
	private static Vec3 aim(ServerPlayer p, Vec3 from) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 far = eye.add(look.scale(48));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e != p && e.isPickable() && e instanceof LivingEntity && !e.isSpectator(), eye.distanceToSqr(end));
		if (ehr != null) {
			end = ehr.getLocation();
		}
		Vec3 d = end.subtract(from);
		return d.lengthSqr() < 1.0 ? look : d.normalize();
	}

	static AlienProjectileEntity fireBolt(ServerPlayer p, Vec3 from, Vec3 dir, float damage, float radius, boolean burst, boolean homing) {
		AlienProjectileEntity e = AlienProjectileEntity.of(p, burst ? BURST_KIND : BOLT_KIND)
				.colour(Starfire.GREEN).size(burst ? 0.35f + radius * 0.12f : 0.28f).damage(0f)
				.speed(burst ? StarfireConfig.BURST_SPEED : StarfireConfig.BOLT_SPEED).life(StarfireConfig.BOLT_LIFE).direction(dir);
		e.moveTo(from.x, from.y, from.z, p.getYRot(), p.getXRot());
		BOLTS.put(e.getUUID(), new Bolt(p.getUUID(), damage, radius, burst, p.level().getGameTime()));
		e.spawn();
		if (homing) {
			synchronized (HOMING) {
				HOMING.add(e);
			}
		}
		return e;
	}

	/** A bolt or burst landed: the direct hit, then the blast round it. */
	private static void boltLanded(AlienProjectileEntity proj, HitResult hit) {
		Bolt b = BOLTS.remove(proj.getUUID());
		if (b == null || !(proj.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(b.owner());
		Vec3 at = hit.getLocation();
		if (owner != null) {
			boltDamageAt(owner, at, hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le ? le : null, b.damage(),
					b.radius(), b.burst());
		}
		if (b.burst()) {
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, b.radius() * 0.3, b.radius() * 0.3, b.radius() * 0.3, 0.0);
			level.sendParticles(Starfire.dust(Starfire.GREEN, 2.2f), at.x, at.y, at.z, 50, b.radius() * 0.45, b.radius() * 0.45, b.radius() * 0.45, 0.0);
			Starfire.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.3f);
		} else {
			level.sendParticles(Starfire.dust(Starfire.GREEN, 1.3f), at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.0);
			level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			Starfire.sound(level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 0.25f, 1.6f);
		}
	}

	/**
	 * The damage of a bolt landing at {@code at}: {@code direct} takes {@code damage}; everyone else in {@code radius} takes half (a
	 * bolt) or full at the centre down to half at the edge (a burst). Public for the tests.
	 */
	public static int boltDamageAt(ServerPlayer owner, Vec3 at, LivingEntity direct, float damage, float radius, boolean burst) {
		int n = 0;
		if (direct != null && Starfire.hit(owner, direct, damage)) {
			n++;
		}
		for (LivingEntity e : HeroTargets.harmable(owner.level(), owner, new AABB(at, at).inflate(radius))) {
			if (e == direct || e == owner || e instanceof ArmorStand) {
				continue;
			}
			double d = Math.sqrt(e.distanceToSqr(at));
			if (d > radius + e.getBbWidth() * 0.5) {
				continue;
			}
			float f = burst ? (float) (damage * (1.0 - 0.5 * Math.min(1.0, d / radius))) : damage * StarfireConfig.BOLT_SPLASH;
			if (Starfire.hit(owner, e, f)) {
				n++;
			}
		}
		return n;
	}

	// ================================================================ Shift+R: Star Burst

	private static boolean startBurst(ServerPlayer p) {
		if (!ready(p, StarfireAbilityManager.STAR_BURST) || Starfire.charging(p) != StarfireState.CHARGE_NONE) {
			return false;
		}
		if (!Starfire.hasEnergy(p, Starfire.boltCost(p, StarfireConfig.BURST_COST))) {
			Starfire.say(p, "message.projecthero.starfire.no_starbolt", ChatFormatting.GRAY);
			return false;
		}
		StarfireState n = Starfire.state(p).copy();
		n.chargeMove = StarfireState.CHARGE_BURST;
		n.chargeStart = Starfire.now(p);
		Starfire.save(p, n);
		Starfire.sound(p, SoundEvents.BEACON_AMBIENT, 0.35f, 1.6f);
		return true;
	}

	/** Star Burst's damage and blast radius after {@code ticks} of charge: 10-28 dmg, 2-4 blocks. */
	public static float burstDamage(int ticks) {
		float f = Mth.clamp(ticks / (float) StarfireConfig.BURST_CHARGE_TICKS, 0f, 1f);
		return StarfireConfig.BURST_MIN_DAMAGE + (StarfireConfig.BURST_MAX_DAMAGE - StarfireConfig.BURST_MIN_DAMAGE) * f;
	}

	public static float burstRadius(int ticks) {
		float f = Mth.clamp(ticks / (float) StarfireConfig.BURST_CHARGE_TICKS, 0f, 1f);
		return StarfireConfig.BURST_MIN_RADIUS + (StarfireConfig.BURST_RADIUS - StarfireConfig.BURST_MIN_RADIUS) * f;
	}

	static void releaseBurst(ServerPlayer p) {
		if (Starfire.charging(p) != StarfireState.CHARGE_BURST) {
			return;
		}
		int ticks = Starfire.chargeElapsed(p);
		StarfireState n = Starfire.state(p).copy();
		n.chargeMove = StarfireState.CHARGE_NONE;
		n.chargeStart = 0L;
		Starfire.save(p, n);
		if (!Starfire.spend(p, Starfire.boltCost(p, StarfireConfig.BURST_COST))) {
			return;
		}
		Vec3 from = p.getEyePosition().add(0, -0.4, 0).add(p.getLookAngle().scale(0.8));
		fireBolt(p, from, aim(p, from), burstDamage(ticks) * Starfire.boltMult(p), burstRadius(ticks), true, false);
		Starfire.startCooldown(p, StarfireAbilityManager.STAR_BURST, StarfireConfig.BURST_COOLDOWN);
		Starfire.sound(p, SoundEvents.BLAZE_SHOOT, 0.35f, 1.3f);
	}

	// ================================================================ G: Tamaranean Grab

	public static LivingEntity grabbed(ServerPlayer p) {
		Run r = RUNS.get(p.getUUID());
		return r == null ? null : r.grabbed;
	}

	static boolean grab(ServerPlayer p) {
		Run r = run(p);
		if (r.grabbed != null) {
			hurl(p, r);
			return true;
		}
		if (!ready(p, StarfireAbilityManager.GRAB)) {
			return false;
		}
		LivingEntity target = lookTarget(p, StarfireConfig.GRAB_REACH);
		if (target == null || !grabbable(p, target)) {
			Starfire.say(p, "message.projecthero.starfire.grab_none", ChatFormatting.GRAY);
			return false;
		}
		if (!Starfire.spend(p, StarfireConfig.GRAB_COST)) {
			return false;
		}
		r.grabbed = target;
		r.grabStart = Starfire.now(p);
		Starfire.sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.5f, 0.8f);
		return true;
	}

	public static boolean grabbable(ServerPlayer p, LivingEntity t) {
		return t.isAlive() && !(t instanceof ArmorStand) && t.getMaxHealth() <= StarfireConfig.GRAB_MAX_HEALTH && !t.isPassenger()
				&& HeroTargets.canHarm(p, t) && t.getBbHeight() <= 3.5f;
	}

	private static void holdGrabbed(ServerPlayer p, Run r) {
		LivingEntity t = r.grabbed;
		long now = Starfire.now(p);
		if (t == null) {
			return;
		}
		if (!t.isAlive() || t.isRemoved() || t.level() != p.level() || t.distanceToSqr(p) > 100 || now - r.grabStart > StarfireConfig.GRAB_MAX_TICKS) {
			dropGrabbed(p, r, true);
			return;
		}
		Vec3 at = carryPos(p, t);
		if (t instanceof ServerPlayer sp) {
			sp.teleportTo(at.x, at.y, at.z);
		} else {
			t.setPos(at.x, at.y, at.z);
		}
		t.setDeltaMovement(Vec3.ZERO);
		t.resetFallDistance();
		t.hurtMarked = true;
	}

	/** Held out in front of her, at chest height. */
	static Vec3 carryPos(ServerPlayer p, LivingEntity t) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		double reach = 1.2 + t.getBbWidth() * 0.6;
		return p.position().add(flat.scale(reach)).add(0, Math.max(0.2, 1.2 - t.getBbHeight() * 0.5), 0);
	}

	/** Release: hurled along her look. The fall it takes after counts. */
	static void hurl(ServerPlayer p, Run r) {
		LivingEntity t = r.grabbed;
		r.grabbed = null;
		if (t == null || !t.isAlive()) {
			return;
		}
		Vec3 v = p.getLookAngle().scale(StarfireConfig.HURL_SPEED).add(0, 0.25, 0);
		t.setDeltaMovement(v);
		t.hurtMarked = true;
		t.resetFallDistance();
		Starfire.startCooldown(p, StarfireAbilityManager.GRAB, StarfireConfig.GRAB_COOLDOWN);
		Starfire.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 0.8f);
		((ServerLevel) p.level()).sendParticles(Starfire.dust(Starfire.GREEN, 1.2f), t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 12, 0.3, 0.3,
				0.3, 0.0);
	}

	private static void dropGrabbed(ServerPlayer p, Run r, boolean cooldown) {
		LivingEntity t = r.grabbed;
		r.grabbed = null;
		if (t != null && t.isAlive()) {
			t.setDeltaMovement(Vec3.ZERO);
			t.hurtMarked = true;
		}
		if (cooldown) {
			Starfire.startCooldown(p, StarfireAbilityManager.GRAB, StarfireConfig.GRAB_COOLDOWN);
		}
	}

	// ================================================================ Shift+G: Eye Beams

	private static boolean startBeams(ServerPlayer p) {
		if (!ready(p, StarfireAbilityManager.EYE_BEAMS) || Starfire.beaming(p)) {
			return false;
		}
		if (!Starfire.hasEnergy(p, StarfireConfig.BEAM_COST_PER_SECOND / 4f)) {
			Starfire.say(p, "message.projecthero.starfire.no_starbolt", ChatFormatting.GRAY);
			return false;
		}
		StarfireState n = Starfire.state(p).copy();
		n.beams = true;
		Starfire.save(p, n);
		run(p).beamStart = Starfire.now(p);
		Starfire.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.3f, 1.8f);
		return true;
	}

	static void stopBeams(ServerPlayer p) {
		if (!Starfire.beaming(p)) {
			return;
		}
		StarfireState n = Starfire.state(p).copy();
		n.beams = false;
		Starfire.save(p, n);
		Starfire.startCooldown(p, StarfireAbilityManager.EYE_BEAMS, StarfireConfig.BEAM_COOLDOWN);
	}

	private static void tickBeams(ServerPlayer p, Run r, long now) {
		if (!Starfire.beaming(p)) {
			return;
		}
		StarfireState s = Starfire.state(p);
		if (!p.getAbilities().instabuild) {
			s.starbolt = Math.max(0f, s.starbolt - StarfireConfig.BEAM_COST_PER_SECOND / 20f);
		}
		if ((s.starbolt <= 0f && !p.getAbilities().instabuild) || now - r.beamStart > StarfireConfig.BEAM_MAX_TICKS) {
			stopBeams(p);
			return;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		BeamClash.report(p, eye, look, StarfireConfig.BEAM_RANGE, 1.2f, Starfire.GREEN);
		if ((now - r.beamStart) % StarfireConfig.BEAM_HIT_INTERVAL != 0) {
			return;
		}
		Vec3 end = beamEnd(p, eye, look);
		LivingEntity target = beamTarget(p, eye, end);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 at = target != null ? target.position().add(0, target.getBbHeight() * 0.6, 0) : end;
		if (target != null && !BeamClash.inClash(p)) {
			beamHit(p, target);
		}
		level.sendParticles(Starfire.dust(Starfire.GREEN, 1.4f), at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.0);
		level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.01);
		if ((now - r.beamStart) % 10 == 0) {
			Starfire.sound(level, eye, SoundEvents.FIRE_AMBIENT, 0.3f, 1.6f);
		}
	}

	/** One beam hit: 6 (both beams) and fire. Public for the tests. */
	public static boolean beamHit(ServerPlayer p, LivingEntity target) {
		boolean hit = Starfire.hit(p, target, StarfireConfig.BEAM_DAMAGE, p.damageSources().source(net.minecraft.world.damagesource.DamageTypes.IN_FIRE, p));
		if (hit && !target.fireImmune()) {
			target.igniteForSeconds(StarfireConfig.BEAM_FIRE_SECONDS);
		}
		return hit;
	}

	static Vec3 beamEnd(ServerPlayer p, Vec3 eye, Vec3 look) {
		Vec3 to = eye.add(look.scale(StarfireConfig.BEAM_RANGE));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return bhr.getType() == HitResult.Type.MISS ? to : bhr.getLocation();
	}

	static LivingEntity beamTarget(ServerPlayer p, Vec3 eye, Vec3 end) {
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e != p && e.isPickable() && e instanceof LivingEntity && !(e instanceof ArmorStand) && !e.isSpectator(), eye.distanceToSqr(end));
		return ehr != null && ehr.getEntity() instanceof LivingEntity le ? le : null;
	}

	// ================================================================ Z: Starbolt Barrage

	static boolean barrage(ServerPlayer p) {
		if (!ready(p, StarfireAbilityManager.BARRAGE) || !Starfire.spend(p, Starfire.boltCost(p, StarfireConfig.BARRAGE_COST))) {
			return false;
		}
		Run r = run(p);
		r.barrageStart = Starfire.now(p);
		r.barrageFired = 0;
		Starfire.startCooldown(p, StarfireAbilityManager.BARRAGE, StarfireConfig.BARRAGE_COOLDOWN);
		Starfire.sound(p, SoundEvents.BEACON_ACTIVATE, 0.3f, 1.9f);
		return true;
	}

	/** How many of the 12 bolts are out {@code elapsed} ticks after Z (evenly over 1.5 s). */
	public static int barrageDue(long elapsed) {
		return (int) Math.min(StarfireConfig.BARRAGE_BOLTS, (elapsed * StarfireConfig.BARRAGE_BOLTS) / StarfireConfig.BARRAGE_TICKS + 1);
	}

	/** Bolt {@code i}'s yaw offset in the arc (degrees, left to right). */
	public static float barrageYaw(int i) {
		return -StarfireConfig.BARRAGE_ARC_DEGREES / 2f + StarfireConfig.BARRAGE_ARC_DEGREES * i / (StarfireConfig.BARRAGE_BOLTS - 1);
	}

	private static void tickBarrage(ServerPlayer p, Run r, long now) {
		if (r.barrageFired < 0) {
			return;
		}
		int due = barrageDue(now - r.barrageStart);
		while (r.barrageFired < due) {
			int i = r.barrageFired++;
			int side = i % 2 == 0 ? -1 : 1;
			float yaw = p.getYRot() + barrageYaw(i);
			float pitch = p.getXRot() - 4f + (p.getRandom().nextFloat() - 0.5f) * 10f;
			Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
			fireBolt(p, Starfire.handPos(p, side), dir, boltDamage(p), StarfireConfig.BOLT_BLAST_RADIUS, false, true);
			Starfire.sound(p, SoundEvents.FIRECHARGE_USE, 0.15f, 1.9f);
		}
		if (r.barrageFired >= StarfireConfig.BARRAGE_BOLTS) {
			r.barrageFired = -1;
		}
	}

	/** Once a server tick: barrage bolts bend a little toward the nearest foe ahead of them. */
	static void tickHoming() {
		synchronized (HOMING) {
			if (HOMING.isEmpty()) {
				return;
			}
			Iterator<AlienProjectileEntity> it = HOMING.iterator();
			while (it.hasNext()) {
				AlienProjectileEntity e = it.next();
				if (e.isRemoved() || !(e.getOwner() instanceof ServerPlayer owner)) {
					it.remove();
					continue;
				}
				Vec3 v = e.getDeltaMovement();
				double speed = v.length();
				if (speed < 1.0e-3) {
					continue;
				}
				Vec3 dir = v.scale(1.0 / speed);
				LivingEntity best = null;
				double bestD = Double.MAX_VALUE;
				for (LivingEntity t : HeroTargets.hostiles(e.level(), owner, e.getBoundingBox().inflate(StarfireConfig.BARRAGE_SEEK_RANGE))) {
					Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(e.position());
					double d = to.length();
					if (d < 0.5 || to.scale(1.0 / d).dot(dir) < 0.5) {
						continue;
					}
					if (d < bestD) {
						bestD = d;
						best = t;
					}
				}
				if (best != null) {
					Vec3 to = best.position().add(0, best.getBbHeight() * 0.5, 0).subtract(e.position()).normalize();
					e.setDeltaMovement(dir.add(to.scale(StarfireConfig.BARRAGE_HOMING)).normalize().scale(speed));
				}
			}
		}
	}

	// ================================================================ Shift+Z: Supernova

	static boolean supernova(ServerPlayer p) {
		if (!Starfire.fullFury(p)) {
			Starfire.say(p, "message.projecthero.starfire.nova_fury", ChatFormatting.GRAY);
			return false;
		}
		if (!ready(p, StarfireAbilityManager.SUPERNOVA) || Starfire.charging(p) != StarfireState.CHARGE_NONE) {
			return false;
		}
		if (!Starfire.spend(p, StarfireConfig.NOVA_COST)) {
			return false;
		}
		StarfireState n = Starfire.state(p).copy();
		n.chargeMove = StarfireState.CHARGE_NOVA;
		n.chargeStart = Starfire.now(p);
		Starfire.save(p, n);
		run(p).novaRise = p.onGround();
		Starfire.startCooldown(p, StarfireAbilityManager.SUPERNOVA, StarfireConfig.NOVA_COOLDOWN);
		Starfire.sound(p, SoundEvents.BEACON_ACTIVATE, 0.5f, 0.6f);
		return true;
	}

	private static void tickNova(ServerPlayer p, Run r, long now) {
		if (Starfire.charging(p) != StarfireState.CHARGE_NOVA) {
			return;
		}
		int e = Starfire.chargeElapsed(p);
		double vy = r.novaRise && e < StarfireConfig.NOVA_RISE_TICKS ? StarfireConfig.NOVA_RISE : 0.0;
		p.setDeltaMovement(0, vy, 0);
		p.hurtMarked = true;
		p.resetFallDistance();
		ServerLevel level = (ServerLevel) p.level();
		if (e % 4 == 0) {
			Vec3 c = p.position().add(0, 1.0, 0);
			double s = 0.4 + e * 0.03;
			level.sendParticles(Starfire.dust(Starfire.SUN, 2.0f), c.x, c.y, c.z, 10, s, s, s, 0.0);
			level.sendParticles(Starfire.dust(Starfire.GREEN, 1.6f), c.x, c.y, c.z, 6, s, s, s, 0.0);
		}
		if (e >= StarfireConfig.NOVA_CHARGE_TICKS) {
			novaBlast(p);
		}
	}

	/** The explosion: 45 to everyone she may hurt within 10 blocks, fire and blindness; Fury empties into Joy. Public for tests. */
	public static int novaBlast(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		int n = 0;
		for (LivingEntity e : HeroTargets.harmable(level, p, new AABB(c, c).inflate(StarfireConfig.NOVA_RADIUS))) {
			if (e == p || e instanceof ArmorStand || e.distanceToSqr(c) > StarfireConfig.NOVA_RADIUS * StarfireConfig.NOVA_RADIUS) {
				continue;
			}
			if (Starfire.hit(p, e, StarfireConfig.NOVA_DAMAGE)) {
				n++;
			}
			if (!e.fireImmune()) {
				e.igniteForSeconds(StarfireConfig.NOVA_FIRE_SECONDS);
			}
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, StarfireConfig.NOVA_BLIND_TICKS, 0), p);
			Vec3 out = e.position().subtract(c);
			Vec3 h = out.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : out.normalize();
			e.push(h.x * 1.2, 0.5, h.z * 1.2);
			e.hurtMarked = true;
		}
		StarfireState s = Starfire.state(p).copy();
		s.chargeMove = StarfireState.CHARGE_NONE;
		s.chargeStart = 0L;
		s.mood = -StarfireConfig.MOOD_MAX;
		s.novaAt = Starfire.now(p);
		Starfire.save(p, s);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0.5, 0.5, 0.5, 0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(Starfire.dust(Starfire.SUN, 3.0f), c.x, c.y, c.z, 200, 4.5, 3.0, 4.5, 0.0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 120, 4.0, 2.5, 4.0, 0.08);
		level.sendParticles(Starfire.dust(Starfire.GREEN, 2.4f), c.x, c.y, c.z, 80, 3.0, 2.0, 3.0, 0.0);
		Starfire.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 0.7f);
		Starfire.sound(level, c, SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.5f);
		return n;
	}

	// ================================================================ X: Star Dash

	static boolean starDash(ServerPlayer p) {
		if (!ready(p, StarfireAbilityManager.STAR_DASH) || !Starfire.spend(p, StarfireConfig.DASH_COST)) {
			return false;
		}
		Run r = run(p);
		r.dashDir = p.getLookAngle().normalize();
		r.dashLeft = StarfireConfig.DASH_TICKS;
		StarfireState n = Starfire.state(p).copy();
		n.dashUntil = Starfire.now(p) + StarfireConfig.DASH_IFRAMES;
		Starfire.save(p, n);
		Starfire.startCooldown(p, StarfireAbilityManager.STAR_DASH, StarfireConfig.DASH_COOLDOWN);
		Starfire.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.3f, 1.4f);
		return true;
	}

	/** Blocks a tick: 10 blocks over the dash. */
	public static double dashStep() {
		return StarfireConfig.DASH_DISTANCE / StarfireConfig.DASH_TICKS;
	}

	private static void tickDash(ServerPlayer p, Run r) {
		if (r.dashLeft <= 0) {
			return;
		}
		r.dashLeft--;
		Vec3 v = r.dashDir.scale(r.dashLeft > 0 ? dashStep() : 0.4);
		p.setDeltaMovement(v);
		p.hurtMarked = true;
		p.resetFallDistance();
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(Starfire.dust(Starfire.GREEN, 1.6f), c.x, c.y, c.z, 8, 0.3, 0.5, 0.3, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 2, 0.2, 0.3, 0.2, 0.0);
	}

	// ================================================================ Shift+X: Comet Dive

	static boolean cometDive(ServerPlayer p) {
		if (!ready(p, StarfireAbilityManager.COMET_DIVE) || !Starfire.spend(p, StarfireConfig.DIVE_COST)) {
			return false;
		}
		Run r = run(p);
		r.diveTicks = 0;
		if (p.onGround()) {
			r.diveStage = 1; // launch first
		} else {
			r.diveStage = 2;
			r.diveDir = p.getLookAngle().normalize();
		}
		StarfireState n = Starfire.state(p).copy();
		n.diveUntil = Starfire.now(p) + StarfireConfig.DIVE_LAUNCH_TICKS + StarfireConfig.DIVE_MAX_TICKS + 2;
		Starfire.save(p, n);
		Starfire.startCooldown(p, StarfireAbilityManager.COMET_DIVE, StarfireConfig.DIVE_COOLDOWN);
		Starfire.sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.35f, 0.9f);
		return true;
	}

	private static void tickDive(ServerPlayer p, Run r) {
		if (r.diveStage == 0) {
			return;
		}
		r.diveTicks++;
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(Starfire.dust(Starfire.GREEN, 2.0f), c.x, c.y, c.z, 10, 0.4, 0.6, 0.4, 0.0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 3, 0.3, 0.4, 0.3, 0.01);
		p.resetFallDistance();
		if (r.diveStage == 1) {
			p.setDeltaMovement(0, StarfireConfig.DIVE_LAUNCH_SPEED, 0);
			p.hurtMarked = true;
			if (r.diveTicks >= StarfireConfig.DIVE_LAUNCH_TICKS) {
				r.diveStage = 2;
				r.diveTicks = 0;
				Vec3 look = p.getLookAngle();
				// from the ground she launches up, then rushes along her look -- downward at least a little
				r.diveDir = new Vec3(look.x, Math.min(look.y, -0.35), look.z).normalize();
			}
			return;
		}
		p.setDeltaMovement(r.diveDir.scale(StarfireConfig.DIVE_SPEED));
		p.hurtMarked = true;
		boolean struck = r.diveTicks > 1 && (p.horizontalCollision || p.verticalCollision || p.onGround());
		if (!struck) {
			struck = !HeroTargets.harmable(level, p, p.getBoundingBox().inflate(0.8)).stream().filter(e -> e != p && !(e instanceof ArmorStand))
					.toList().isEmpty();
		}
		if (struck || r.diveTicks > StarfireConfig.DIVE_MAX_TICKS) {
			cometImpact(p);
		}
	}

	/** The dive lands: 18 and a heavy knockback round her. Public for the tests. */
	public static int cometImpact(ServerPlayer p) {
		Run r = run(p);
		r.diveStage = 0;
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position();
		int n = 0;
		for (LivingEntity e : HeroTargets.harmable(level, p, new AABB(c, c).inflate(StarfireConfig.DIVE_RADIUS))) {
			if (e == p || e instanceof ArmorStand || e.distanceToSqr(c) > (StarfireConfig.DIVE_RADIUS + 1) * (StarfireConfig.DIVE_RADIUS + 1)) {
				continue;
			}
			if (Starfire.hit(p, e, StarfireConfig.DIVE_DAMAGE)) {
				n++;
			}
			Vec3 out = e.position().subtract(c);
			Vec3 h = out.lengthSqr() < 1.0e-4 ? r.diveDir : new Vec3(out.x, 0, out.z).normalize();
			e.push(h.x * 1.4, 0.55, h.z * 1.4);
			e.hurtMarked = true;
		}
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		StarfireState s = Starfire.state(p).copy();
		s.diveUntil = Starfire.now(p) + 3;
		Starfire.save(p, s);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 3, 1.0, 0.3, 1.0, 0.0);
		level.sendParticles(Starfire.dust(Starfire.GREEN, 2.4f), c.x, c.y + 0.5, c.z, 60, 2.0, 0.5, 2.0, 0.0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.3, c.z, 30, 1.5, 0.3, 1.5, 0.05);
		Starfire.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.45f, 1.1f);
		return n;
	}

	// ================================================================ C: Tamaranean Charge

	static boolean charge(ServerPlayer p) {
		if (!ready(p, StarfireAbilityManager.CHARGE) || !Starfire.spend(p, StarfireConfig.CHARGE_COST)) {
			return false;
		}
		Run r = run(p);
		Vec3 look = p.getLookAngle();
		r.chargeDir = new Vec3(look.x, Mth.clamp(look.y, -0.5, 0.5), look.z).normalize();
		r.chargeLeft = StarfireConfig.CHARGE_TICKS;
		r.carried = null;
		r.wallHit = false;
		Starfire.startCooldown(p, StarfireAbilityManager.CHARGE, StarfireConfig.CHARGE_COOLDOWN);
		Starfire.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.3f, 0.8f);
		return true;
	}

	private static void tickCharge(ServerPlayer p, Run r) {
		if (r.chargeLeft <= 0) {
			return;
		}
		r.chargeLeft--;
		ServerLevel level = (ServerLevel) p.level();
		double step = StarfireConfig.CHARGE_DISTANCE / StarfireConfig.CHARGE_TICKS;
		p.setDeltaMovement(r.chargeDir.scale(r.chargeLeft > 0 ? step : 0.3));
		p.hurtMarked = true;
		p.resetFallDistance();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(Starfire.dust(Starfire.GREEN, 1.4f), c.x, c.y, c.z, 5, 0.3, 0.4, 0.3, 0.0);
		if (r.carried == null) {
			for (LivingEntity e : HeroTargets.harmable(level, p, p.getBoundingBox().inflate(1.2).expandTowards(r.chargeDir.scale(step)))) {
				if (e != p && !(e instanceof ArmorStand) && e.isAlive()) {
					chargeHit(p, r, e);
					break;
				}
			}
		}
		if (r.carried != null) {
			LivingEntity t = r.carried;
			if (!t.isAlive()) {
				r.carried = null;
				return;
			}
			Vec3 at = p.position().add(r.chargeDir.scale(1.0 + t.getBbWidth() * 0.5));
			if (!r.wallHit && chargeWall(level, t, r.chargeDir)) {
				chargeWallHit(p, r);
				r.chargeLeft = 0;
				return;
			}
			t.setPos(at.x, at.y, at.z);
			t.setDeltaMovement(r.chargeDir.scale(step));
			t.hurtMarked = true;
		}
		if (r.chargeLeft == 0 && r.carried != null) {
			r.carried.setDeltaMovement(r.chargeDir.scale(0.8).add(0, 0.2, 0));
			r.carried.hurtMarked = true;
			r.carried = null;
		}
	}

	/** The tackle connects: 8, and it is carried. Public for the tests. */
	public static void chargeHit(ServerPlayer p, Run r, LivingEntity e) {
		r.carried = e;
		Starfire.hit(p, e, StarfireConfig.CHARGE_DAMAGE);
		Starfire.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.5f, 0.8f);
	}

	/** Is there something solid right ahead of the carried creature? */
	public static boolean chargeWall(ServerLevel level, LivingEntity t, Vec3 dir) {
		Vec3 ahead = t.position().add(dir.scale(t.getBbWidth() * 0.5 + 0.6)).add(0, t.getBbHeight() * 0.5, 0);
		BlockPos pos = BlockPos.containing(ahead);
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** Slammed into a wall: +10. Public for the tests. */
	public static void chargeWallHit(ServerPlayer p, Run r) {
		r.wallHit = true;
		LivingEntity t = r.carried;
		r.carried = null;
		if (t == null) {
			return;
		}
		Starfire.hit(p, t, StarfireConfig.CHARGE_WALL_BONUS);
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 1, 0, 0, 0, 0);
		Starfire.sound(level, t.position(), SoundEvents.ANVIL_LAND, 0.3f, 0.7f);
		p.setDeltaMovement(r.chargeDir.scale(-0.3));
		p.hurtMarked = true;
	}

	/** For the tests: the player's Run. */
	public static Run runOf(ServerPlayer p) {
		return run(p);
	}

	// ================================================================ Shift+C: Ground Pound

	static boolean groundPound(ServerPlayer p) {
		if (!ready(p, StarfireAbilityManager.GROUND_POUND) || !Starfire.spend(p, StarfireConfig.POUND_COST)) {
			return false;
		}
		Starfire.startCooldown(p, StarfireAbilityManager.GROUND_POUND, StarfireConfig.POUND_COOLDOWN);
		if (p.onGround()) {
			poundSlam(p);
			return true;
		}
		if (Starfire.isFlying(p)) {
			StarfireFlight.stop(p);
		}
		Run r = run(p);
		r.pounding = true;
		r.poundTicks = 0;
		Starfire.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.3f, 0.6f);
		return true;
	}

	private static void tickPound(ServerPlayer p, Run r) {
		if (!r.pounding) {
			return;
		}
		r.poundTicks++;
		p.setDeltaMovement(0, -StarfireConfig.POUND_DIVE_SPEED, 0);
		p.hurtMarked = true;
		p.resetFallDistance();
		if ((p.onGround() && r.poundTicks > 1) || r.poundTicks > StarfireConfig.POUND_MAX_FALL_TICKS) {
			r.pounding = false;
			poundSlam(p);
		}
	}

	/** The double-fist slam: 12 within 5 blocks, launched up. Public for the tests. */
	public static int poundSlam(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position();
		int n = 0;
		for (LivingEntity e : HeroTargets.harmable(level, p, new AABB(c, c).inflate(StarfireConfig.POUND_RADIUS))) {
			if (e == p || e instanceof ArmorStand || e.distanceToSqr(c) > StarfireConfig.POUND_RADIUS * StarfireConfig.POUND_RADIUS) {
				continue;
			}
			if (Starfire.hit(p, e, StarfireConfig.POUND_DAMAGE)) {
				n++;
			}
			Vec3 v = e.getDeltaMovement();
			e.setDeltaMovement(v.x, StarfireConfig.POUND_LAUNCH, v.z);
			e.hurtMarked = true;
		}
		BlockPos below = p.blockPosition().below();
		var floor = level.getBlockState(below);
		if (!floor.isAir()) {
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, floor), c.x, c.y + 0.1, c.z, 80, 2.5, 0.1, 2.5,
					0.2);
		}
		level.sendParticles(Starfire.dust(Starfire.GREEN, 2.0f), c.x, c.y + 0.2, c.z, 40, 2.5, 0.2, 2.5, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 2, 1.5, 0.1, 1.5, 0.0);
		Starfire.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 0.9f);
		return n;
	}

	// ================================================================ V: Language Kiss

	static boolean kiss(ServerPlayer p) {
		if (!ready(p, StarfireAbilityManager.KISS)) {
			return false;
		}
		LivingEntity t = lookTarget(p, StarfireConfig.KISS_REACH);
		if (t instanceof Villager v) {
			kissVillager(p, v);
		} else if (t instanceof ServerPlayer other) {
			kissPlayer(p, other);
		} else {
			Starfire.say(p, "message.projecthero.starfire.kiss_none", ChatFormatting.GRAY);
			return false;
		}
		Starfire.startCooldown(p, StarfireAbilityManager.KISS, StarfireConfig.KISS_COOLDOWN);
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(ParticleTypes.HEART, t.getX(), t.getEyeY() + 0.4, t.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
		Starfire.sound(level, t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
		return true;
	}

	/** She learns the villager's tongue: better trades with that villager, and a gift once a day. Public for the tests. */
	public static boolean kissVillager(ServerPlayer p, Villager v) {
		StarfireState s = Starfire.state(p);
		String key = v.getUUID().toString();
		long today = p.level().getDayTime() / StarfireConfig.DAY_TICKS;
		boolean first = !s.villagers.containsKey(key);
		Long last = s.villagers.get(key);
		boolean gift = last == null || last < today;
		if (first) {
			v.getGossips().add(p.getUUID(), GossipType.MAJOR_POSITIVE, StarfireConfig.KISS_GOSSIP);
		}
		StarfireState n = s.copy();
		if (gift) {
			n.villagers.put(key, today);
		} else if (first) {
			n.villagers.put(key, today);
		}
		Starfire.save(p, n);
		if (gift) {
			giveGift(p, v);
		}
		v.playSound(SoundEvents.VILLAGER_YES, 0.6f, 1.2f);
		p.displayClientMessage(Component.translatable(first ? "message.projecthero.starfire.kiss_villager" : gift
				? "message.projecthero.starfire.kiss_gift" : "message.projecthero.starfire.kiss_again").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		return gift;
	}

	private static void giveGift(ServerPlayer p, Villager v) {
		ServerLevel level = (ServerLevel) p.level();
		var key = giftTable(v.getVillagerData().getProfession());
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, v.position())
				.withParameter(LootContextParams.THIS_ENTITY, v).create(LootContextParamSets.GIFT);
		for (ItemStack stack : table.getRandomItems(params)) {
			net.minecraft.world.entity.ai.behavior.BehaviorUtils.throwItem(v, stack, p.position());
		}
	}

	/** The Hero of the Village gift table for a profession. */
	private static net.minecraft.resources.ResourceKey<LootTable> giftTable(VillagerProfession prof) {
		if (prof == VillagerProfession.ARMORER) return BuiltInLootTables.ARMORER_GIFT;
		if (prof == VillagerProfession.BUTCHER) return BuiltInLootTables.BUTCHER_GIFT;
		if (prof == VillagerProfession.CARTOGRAPHER) return BuiltInLootTables.CARTOGRAPHER_GIFT;
		if (prof == VillagerProfession.CLERIC) return BuiltInLootTables.CLERIC_GIFT;
		if (prof == VillagerProfession.FARMER) return BuiltInLootTables.FARMER_GIFT;
		if (prof == VillagerProfession.FISHERMAN) return BuiltInLootTables.FISHERMAN_GIFT;
		if (prof == VillagerProfession.FLETCHER) return BuiltInLootTables.FLETCHER_GIFT;
		if (prof == VillagerProfession.LEATHERWORKER) return BuiltInLootTables.LEATHERWORKER_GIFT;
		if (prof == VillagerProfession.LIBRARIAN) return BuiltInLootTables.LIBRARIAN_GIFT;
		if (prof == VillagerProfession.MASON) return BuiltInLootTables.MASON_GIFT;
		if (prof == VillagerProfession.SHEPHERD) return BuiltInLootTables.SHEPHERD_GIFT;
		if (prof == VillagerProfession.TOOLSMITH) return BuiltInLootTables.TOOLSMITH_GIFT;
		if (prof == VillagerProfession.WEAPONSMITH) return BuiltInLootTables.WEAPONSMITH_GIFT;
		return BuiltInLootTables.FARMER_GIFT; // (a nitwit or jobless villager gives what a farmer would)
	}

	/** Each sees the other's name tag (outline) through walls for 60 s. Public for the tests. */
	public static void kissPlayer(ServerPlayer p, ServerPlayer other) {
		StarfireState n = Starfire.state(p).copy();
		n.bondWith = other.getUUID().toString();
		n.bondUntil = Starfire.now(p) + StarfireConfig.BOND_TICKS;
		Starfire.save(p, n);
		p.displayClientMessage(Component.translatable("message.projecthero.starfire.kiss_player", other.getDisplayName())
				.withStyle(ChatFormatting.LIGHT_PURPLE), true);
		other.displayClientMessage(Component.translatable("message.projecthero.starfire.kissed_by", p.getDisplayName())
				.withStyle(ChatFormatting.LIGHT_PURPLE), true);
	}

	// ================================================================ Shift+V: Glorious Radiance

	static boolean radiance(ServerPlayer p) {
		if (!ready(p, StarfireAbilityManager.RADIANCE)) {
			return false;
		}
		StarfireState n = Starfire.state(p).copy();
		n.radiance = !n.radiance;
		Starfire.save(p, n);
		if (n.radiance) {
			StarfireRadiance.tick(p);
			Starfire.sound(p, SoundEvents.BEACON_ACTIVATE, 0.3f, 1.7f);
		} else {
			StarfireRadiance.stop(p.getUUID(), p.getServer());
			Starfire.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.3f, 1.7f);
		}
		Starfire.startCooldown(p, StarfireAbilityManager.RADIANCE, StarfireConfig.RADIANCE_COOLDOWN);
		return true;
	}

	// ================================================================ shared

	/** The creature under her crosshair within {@code reach}. */
	static LivingEntity lookTarget(ServerPlayer p, double reach) {
		Vec3 eye = p.getEyePosition();
		Vec3 to = eye.add(p.getLookAngle().scale(reach));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? to : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e != p && e.isPickable() && e instanceof LivingEntity && !e.isSpectator(), eye.distanceToSqr(end));
		return ehr != null && ehr.getEntity() instanceof LivingEntity le ? le : null;
	}

	/** Per tick for every Starfire. */
	static void tick(ServerPlayer p) {
		Run r = run(p);
		long now = Starfire.now(p);
		tickDash(p, r);
		tickDive(p, r);
		tickCharge(p, r);
		tickPound(p, r);
		holdGrabbed(p, r);
		tickBarrage(p, r, now);
		tickBeams(p, r, now);
		tickNova(p, r, now);
		if (Starfire.charging(p) == StarfireState.CHARGE_BURST && Starfire.chargeElapsed(p) > StarfireConfig.BURST_MAX_HOLD) {
			releaseBurst(p);
		}
	}

	/** Once a server tick: barrage homing; old bolt records pruned. */
	static void serverTick(net.minecraft.server.MinecraftServer server) {
		tickHoming();
		if (!BOLTS.isEmpty() && server.getTickCount() % 100 == 0) {
			long now = server.overworld().getGameTime();
			BOLTS.values().removeIf(b -> Math.abs(now - b.born()) > 200);
		}
	}

	/** Exposed for {@link Entity}-free checks in the tests. */
	public static boolean hasBolt(UUID projectile) {
		return BOLTS.containsKey(projectile);
	}
}
