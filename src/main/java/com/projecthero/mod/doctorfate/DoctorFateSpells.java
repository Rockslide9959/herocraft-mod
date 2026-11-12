package com.projecthero.mod.doctorfate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.doctorfate.data.FateState;
import com.projecthero.mod.doctorfate.network.FateFxPayload;
import com.projecthero.mod.squad.Squads;


import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate: every spell. {@link #use} is a key press (helmet checks, cooldowns, Nabu's Override, the Dominion and the
 * Taking-hold health cost); the {@code cast*} methods are the spells themselves, also used by Nabu during a Possession
 * ({@code nabu} = true: no cost, no cooldown, x{@link DoctorFateConfig#POSSESSION_DAMAGE_MULT} damage, never a squadmate).
 *
 * <p>Static state (cleared by {@code ServerStateReset} through {@link DoctorFate#clearSessionState}): the chained creatures, the
 * Glyph Storms and the Wards, each by level key and entity UUID (never the level or entity itself).
 */
public final class DoctorFateSpells {
	/** Golden Chains: who is bound, where, until when. */
	record Bind(ResourceKey<Level> level, UUID entity, Vec3 at, long until) {
	}

	record Storm(ResourceKey<Level> level, UUID owner, Vec3 center, long until, float mult, boolean nabu) {
	}

	record Ward(ResourceKey<Level> level, UUID owner, Vec3 center, long until) {
	}

	private static final Map<UUID, Bind> BOUND = new ConcurrentHashMap<>();
	private static final List<Storm> STORMS = new CopyOnWriteArrayList<>();
	private static final List<Ward> WARDS = new CopyOnWriteArrayList<>();

	/** Tests only: force the next Override roll (null = the 1-in-6 roll). */
	public static volatile Boolean testOverride;

	private DoctorFateSpells() {
	}

	static void clearSessionState() {
		BOUND.clear();
		STORMS.clear();
		WARDS.clear();
		testOverride = null;
	}

	static void forget(UUID id) {
		STORMS.removeIf(s -> s.owner.equals(id));
		WARDS.removeIf(w -> w.owner.equals(id));
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= DoctorFateConfig.BOSS_HEALTH || com.projecthero.mod.titanshifter.TitanCombat.isBoss(e);
	}

	public static boolean isBound(Entity e) {
		Bind b = BOUND.get(e.getUUID());
		return b != null && b.until > e.level().getGameTime();
	}

	public static int storms() {
		return STORMS.size();
	}

	// ---------------------------------------------------------------- key presses

	/** A key press. False (with the reason) when nothing was cast. */
	public static boolean use(ServerPlayer p, String id) {
		if (!DoctorFate.hasPower(p) || DoctorFate.possessed(p)) {
			return false;
		}
		if (DoctorFateAbilityManager.needsHelmet(id) && !DoctorFate.wearsHelmet(p)) {
			DoctorFate.say(p, "message.projecthero.doctor_fate.needs_helmet", ChatFormatting.GRAY);
			return false;
		}
		if (DoctorFate.cooldownRemaining(p, id) > 0) {
			return false;
		}
		switch (id) {
			case DoctorFateAbilityManager.HELMET -> {
				if (DoctorFate.toggleHelmet(p)) {
					DoctorFate.startCooldown(p, id, DoctorFateConfig.HELMET_COOLDOWN);
					return true;
				}
				return false;
			}
			case DoctorFateAbilityManager.AVATAR -> {
				return DoctorFateBargains.offerForbidden(p); // asks Nabu first, every time
			}
			default -> {
				return cast(p, id);
			}
		}
	}

	/** A spell from a key, with Nabu's Override: 1 in 6 attack spells come out as his pick (25+ Dominion, helmet on). */
	static boolean cast(ServerPlayer p, String id) {
		boolean full = DoctorFate.wearsHelmet(p);
		String actual = id;
		LivingEntity forced = null;
		if (full && DoctorFateAbilityManager.overridable(id) && DoctorFate.dominion(p) >= DoctorFateConfig.TIER_OVERRIDE && rollOverride(p)) {
			DoctorFateDominion.Pick pick = DoctorFateDominion.nabuPick(p);
			if (pick != null) {
				actual = pick.id();
				forced = pick.target();
				DoctorFate.say(p, "message.projecthero.doctor_fate.override", ChatFormatting.GOLD,
						net.minecraft.network.chat.Component.translatable("projecthero.doctor_fate.ability." + actual));
			}
		}
		boolean ok = castSpell(p, actual, forced, full, false);
		if (!ok) {
			return false;
		}
		DoctorFate.startCooldown(p, id, DoctorFateAbilityManager.maxCooldown(id));
		if (full) {
			pay(p, DoctorFateAbilityManager.dominionOf(actual));
		}
		return true;
	}

	private static boolean rollOverride(ServerPlayer p) {
		Boolean t = testOverride;
		if (t != null) {
			testOverride = null;
			return t;
		}
		return p.getRandom().nextInt(DoctorFateConfig.OVERRIDE_ONE_IN) == 0;
	}

	/** The spell itself: {@code forced} is Nabu's own target (null = where the caster aims). */
	static boolean castSpell(ServerPlayer p, String id, LivingEntity forced, boolean full, boolean nabu) {
		float mult = nabu ? DoctorFateConfig.POSSESSION_DAMAGE_MULT : 1f;
		return switch (id) {
			case DoctorFateAbilityManager.ANKH_BOLT -> castBolt(p, aimDir(p, forced), full, mult, nabu);
			case DoctorFateAbilityManager.GOLDEN_CHAINS -> castChains(p, forced != null ? forced : aim(p, DoctorFateConfig.AIM_RANGE, nabu), mult, nabu);
			case DoctorFateAbilityManager.BANISHMENT -> castBanish(p, forced != null ? forced : aim(p, DoctorFateConfig.AIM_RANGE, nabu));
			case DoctorFateAbilityManager.GLYPH_STORM -> castGlyphStorm(p, forced != null ? forced.position() : aimPoint(p, DoctorFateConfig.AIM_RANGE), mult, nabu);
			case DoctorFateAbilityManager.ANKH_STEP -> castStep(p, full ? DoctorFateConfig.STEP_DISTANCE : DoctorFateConfig.WEAK_STEP_DISTANCE);
			case DoctorFateAbilityManager.GATE_DROP -> castGateDrop(p, forced != null ? forced : aim(p, DoctorFateConfig.AIM_RANGE, nabu), nabu);
			case DoctorFateAbilityManager.ORDER_BLAST -> castBlast(p, aimDir(p, forced), mult, nabu);
			case DoctorFateAbilityManager.WARD -> castWard(p);
			case DoctorFateAbilityManager.SIGHT -> castSight(p);
			default -> false;
		};
	}

	/** Pays a spell's Dominion: 10% less at Pull and up; at Taking hold it also costs health. */
	static void pay(ServerPlayer p, float dominion) {
		if (dominion <= 0f || p.getAbilities().instabuild && dominion <= 0f) {
			return;
		}
		float d = DoctorFate.dominion(p);
		int tier = DoctorFate.tier(d);
		float gain = tier >= 2 ? dominion * DoctorFateConfig.PULL_DISCOUNT : dominion;
		DoctorFate.addDominion(p, gain);
		if (tier >= 3 && !p.getAbilities().instabuild) {
			float cost = dominion >= 8f ? DoctorFateConfig.HOLD_HEALTH_COST * 2f : DoctorFateConfig.HOLD_HEALTH_COST;
			p.setHealth(Math.max(1f, p.getHealth() - cost));
		}
	}

	// ---------------------------------------------------------------- aiming

	static Vec3 aimDir(ServerPlayer p, LivingEntity forced) {
		if (forced != null) {
			Vec3 d = forced.getBoundingBox().getCenter().subtract(p.getEyePosition());
			if (d.lengthSqr() > 1.0e-4) {
				return d.normalize();
			}
		}
		return p.getLookAngle();
	}

	/** Where the look ray meets a block (or {@code range} ahead). */
	static Vec3 aimPoint(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		HitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/** Every creature the ray from {@code from} along {@code dir} passes through before a block, nearest first. */
	static List<LivingEntity> along(ServerPlayer p, Vec3 from, Vec3 dir, double range, boolean nabu) {
		Vec3 end = from.add(dir.scale(range));
		HitResult block = p.level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		Vec3 stop = end;
		AABB box = new AABB(from, stop).inflate(1.0);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> targetable(p, e, nabu))) {
			if (e.getBoundingBox().inflate(0.4).clip(from, stop).isPresent()) {
				out.add(e);
			}
		}
		out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
		return out;
	}

	/** The creature under the crosshair (a forgiving ray), or null. */
	static LivingEntity aim(ServerPlayer p, double range, boolean nabu) {
		List<LivingEntity> hits = along(p, p.getEyePosition(), p.getLookAngle(), range, nabu);
		return hits.isEmpty() ? null : hits.get(0);
	}

	static boolean targetable(ServerPlayer p, LivingEntity e, boolean nabu) {
		if (e == p || !HeroTargets.canHarm(p, e)) {
			return false;
		}
		return !nabu || (!Squads.areAllies(p, e) && (!(e instanceof Player) || HeroTargets.isHostile(p, e)));
	}

	// ---------------------------------------------------------------- hits

	/**
	 * A spell hits {@code target}. Nabu won't let it hurt villagers, golems or tamed animals once he has a say (Override and up, or
	 * his own casts): it fizzles. Possession never hurts a squadmate. False when nothing landed.
	 */
	public static boolean hit(ServerPlayer p, LivingEntity target, float amount, boolean nabu) {
		if (target == null || !targetable(p, target, nabu)) {
			return false;
		}
		if (fizzles(p, target, nabu)) {
			fizzle(p, target);
			return false;
		}
		float before = target.getHealth();
		boolean landed = com.projecthero.mod.hero.power.AbilityHelpers.hurtBurst(p, target, p.damageSources().playerAttack(p), amount);
		return landed || target.getHealth() < before;
	}

	/** Would Nabu stop a spell on {@code target}? */
	public static boolean fizzles(ServerPlayer p, LivingEntity target, boolean nabu) {
		return DoctorFateChaos.isProtected(target) && (nabu || DoctorFate.dominion(p) >= DoctorFateConfig.TIER_OVERRIDE);
	}

	private static void fizzle(ServerPlayer p, LivingEntity target) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.01);
		level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 0.8f), c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.0);
		DoctorFate.sound(level, c, SoundEvents.FIRE_EXTINGUISH, 0.3f, 1.6f);
		DoctorFate.say(p, "message.projecthero.doctor_fate.fizzle", ChatFormatting.GOLD);
	}

	// ---------------------------------------------------------------- R: Ankh Bolt

	static boolean castBolt(ServerPlayer p, Vec3 dir, boolean full, float mult, boolean nabu) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 from = p.getEyePosition().add(0, -0.15, 0);
		List<LivingEntity> hits = along(p, from, dir, DoctorFateConfig.BOLT_RANGE, nabu);
		int pierce = full ? 2 : 1; // passes through one creature
		float dmg = (full ? DoctorFateConfig.BOLT_DAMAGE : DoctorFateConfig.WEAK_BOLT_DAMAGE) * mult;
		Vec3 end = from.add(dir.scale(DoctorFateConfig.BOLT_RANGE));
		HitResult block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		int n = 0;
		for (LivingEntity e : hits) {
			if (n >= pierce) {
				break;
			}
			hit(p, e, dmg, nabu);
			n++;
			if (n >= pierce) {
				end = e.getBoundingBox().getCenter();
			}
		}
		line(level, from, end, full ? 1.0f : 0.7f);
		ankh(level, end, dir, full ? 0.6 : 0.4, false);
		DoctorFate.sound(level, from, SoundEvents.ILLUSIONER_CAST_SPELL, 0.35f, full ? 1.4f : 1.7f);
		return true;
	}

	// ---------------------------------------------------------------- Shift+R: Golden Chains

	static boolean castChains(ServerPlayer p, LivingEntity target, float mult, boolean nabu) {
		if (target == null) {
			DoctorFate.say(p, "message.projecthero.doctor_fate.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (fizzles(p, target, nabu)) {
			fizzle(p, target);
			return false;
		}
		hit(p, target, DoctorFateConfig.CHAINS_DAMAGE * mult, nabu);
		long now = p.level().getGameTime();
		int ticks = isBoss(target) ? DoctorFateConfig.CHAINS_BIND_BOSS_TICKS : DoctorFateConfig.CHAINS_BIND_TICKS;
		BOUND.put(target.getUUID(), new Bind(p.level().dimension(), target.getUUID(), target.position(), now + ticks));
		target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false));
		ServerLevel level = (ServerLevel) p.level();
		chains(level, target);
		DoctorFate.sound(level, target.position(), SoundEvents.CHAIN_PLACE, 0.8f, 0.7f);
		return true;
	}

	private static void chains(ServerLevel level, LivingEntity target) {
		Vec3 c = target.getBoundingBox().getCenter();
		double r = target.getBbWidth() * 0.5 + 1.2;
		for (int k = 0; k < 4; k++) {
			double a = k * Math.PI / 2 + Math.PI / 4;
			Vec3 base = new Vec3(target.getX() + Math.cos(a) * r, target.getY(), target.getZ() + Math.sin(a) * r);
			line(level, base, c, 0.7f);
		}
	}

	// ---------------------------------------------------------------- G: Ankh Shield (hold)

	static boolean raiseShield(ServerPlayer p) {
		if (DoctorFate.cooldownRemaining(p, DoctorFateAbilityManager.ANKH_SHIELD) > 0 || DoctorFate.shieldUp(p)) {
			return false;
		}
		FateState n = DoctorFate.state(p).copy();
		n.shieldUp = true;
		n.shieldSince = p.level().getGameTime();
		DoctorFate.save(p, n);
		DoctorFate.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.4f, 1.6f);
		return true;
	}

	static void lowerShield(ServerPlayer p) {
		FateState s = DoctorFate.state(p);
		if (!s.shieldUp) {
			return;
		}
		FateState n = s.copy();
		n.shieldUp = false;
		n.abilityReadyAt.put(DoctorFateAbilityManager.ANKH_SHIELD, p.level().getGameTime() + DoctorFateConfig.SHIELD_COOLDOWN);
		DoctorFate.save(p, n);
		DoctorFate.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.3f, 1.8f);
	}

	/** Is damage coming from {@code from} in front of the raised shield? */
	public static boolean shieldCovers(Player p, Vec3 from) {
		Vec3 to = from.subtract(p.getEyePosition());
		to = new Vec3(to.x, to.y * 0.5, to.z);
		if (to.lengthSqr() < 1.0e-4) {
			return false;
		}
		return to.normalize().dot(p.getLookAngle()) > 0.3;
	}

	private static void tickShield(ServerPlayer p, FateState s, long now) {
		if (!s.shieldUp) {
			return;
		}
		boolean full = s.helmetOn;
		if (!full && now - s.shieldSince >= DoctorFateConfig.WEAK_SHIELD_MAX_TICKS) {
			lowerShield(p);
			return;
		}
		if (full) {
			float gain = DoctorFateConfig.SHIELD_DOMINION_PER_TICK * (DoctorFate.tier(s.dominion) >= 2 ? DoctorFateConfig.PULL_DISCOUNT : 1f);
			s.dominion = Math.min(DoctorFateConfig.DOMINION_MAX, s.dominion + gain);
		}
		// projectiles in front: reflected (helmet on) or stopped dead (Kent's own shield)
		ServerLevel level = (ServerLevel) p.level();
		AABB box = p.getBoundingBox().inflate(3.5);
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, box, pr -> pr.getOwner() != p && pr.isAlive())) {
			Vec3 v = pr.getDeltaMovement();
			Vec3 toMe = p.getBoundingBox().getCenter().subtract(pr.position());
			if (v.dot(toMe) <= 0 || !shieldCovers(p, pr.position())) {
				continue;
			}
			if (full) {
				pr.setDeltaMovement(v.scale(-1.2));
				pr.setOwner(p);
				pr.hurtMarked = true;
				level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 1.0f), pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.0);
				DoctorFate.sound(level, pr.position(), SoundEvents.SHIELD_BLOCK, 0.5f, 1.4f);
			} else {
				pr.setDeltaMovement(Vec3.ZERO);
				pr.hurtMarked = true;
			}
		}
		if (now % 4 == 0) {
			Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(1.4)).add(0, -0.3, 0);
			ankh(level, front, p.getLookAngle(), full ? 0.9 : 0.6, true);
		}
	}

	// ---------------------------------------------------------------- Shift+G: Banishment

	static boolean castBanish(ServerPlayer p, LivingEntity target) {
		if (target == null) {
			DoctorFate.say(p, "message.projecthero.doctor_fate.no_target", ChatFormatting.GRAY);
			return false;
		}
		ServerLevel level = (ServerLevel) p.level();
		if (isBoss(target)) {
			DoctorFate.say(p, "message.projecthero.doctor_fate.boss_immune", ChatFormatting.GRAY);
			return false;
		}
		if (fizzles(p, target, false)) {
			fizzle(p, target);
			return false;
		}
		gate(level, target.position(), target.getBbWidth() * 0.5 + 1.0);
		DoctorFate.sound(level, target.position(), SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.6f);
		if (strong(target)) {
			throwAway(p, target);
		} else {
			boolean chaos = DoctorFateChaos.isChaos(target);
			target.discard(); // gone, no drops (never a boss or a player: those are strong)
			if (chaos) {
				DoctorFateBargains.onChaosBanished(p);
			}
		}
		return true;
	}

	/** Thrown 40 blocks away rather than banished: players, strong (60+ health), named, tamed, villagers and golems. */
	static boolean strong(LivingEntity e) {
		return e instanceof Player || e.getMaxHealth() >= DoctorFateConfig.BANISH_STRONG_HEALTH || e.hasCustomName()
				|| DoctorFateChaos.isProtected(e) || (e instanceof Mob m && m.isPersistenceRequired() && m.hasCustomName());
	}

	private static void throwAway(ServerPlayer p, LivingEntity target) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 d = target.position().subtract(p.position());
		d = new Vec3(d.x, 0, d.z);
		if (d.lengthSqr() < 1.0e-4) {
			d = p.getLookAngle().multiply(1, 0, 1);
		}
		d = d.normalize();
		double x = target.getX() + d.x * DoctorFateConfig.BANISH_THROW;
		double z = target.getZ() + d.z * DoctorFateConfig.BANISH_THROW;
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, target.getY(), z));
		double y = Math.max(top.getY(), level.getMinBuildHeight() + 1);
		if (target instanceof ServerPlayer sp) {
			sp.teleportTo(level, x, y, z, sp.getYRot(), sp.getXRot());
		} else {
			target.teleportTo(x, y, z);
		}
		target.resetFallDistance();
		gate(level, new Vec3(x, y, z), 1.2);
	}

	// ---------------------------------------------------------------- Z: Glyph Storm

	static boolean castGlyphStorm(ServerPlayer p, Vec3 at, float mult, boolean nabu) {
		ServerLevel level = (ServerLevel) p.level();
		STORMS.add(new Storm(level.dimension(), p.getUUID(), at, level.getGameTime() + DoctorFateConfig.GLYPH_TICKS, mult, nabu));
		DoctorFate.sound(level, at, SoundEvents.EVOKER_PREPARE_ATTACK, 0.5f, 1.3f);
		ring(level, at.add(0, 0.1, 0), DoctorFateConfig.GLYPH_RADIUS, 48);
		return true;
	}

	/** Every server tick: the storms and the wards (they outlive a key press). */
	public static void tickWorld(MinecraftServer server) {
		if (!STORMS.isEmpty()) {
			for (Iterator<Storm> it = STORMS.iterator(); it.hasNext();) {
				Storm s = it.next();
				ServerLevel level = server.getLevel(s.level);
				ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
				long now = level == null ? Long.MAX_VALUE : level.getGameTime();
				if (level == null || owner == null || owner.level() != level || now >= s.until) {
					STORMS.remove(s);
					continue;
				}
				long left = s.until - now;
				for (int k = 0; k < 4; k++) { // golden hieroglyph blades falling
					double a = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * DoctorFateConfig.GLYPH_RADIUS;
					double x = s.center.x + Math.cos(a) * r, z = s.center.z + Math.sin(a) * r;
					level.sendParticles(ParticleTypes.END_ROD, x, s.center.y + 7, z, 0, 0, -1, 0, 0.8);
					level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 1.3f), x, s.center.y + 5, z, 2, 0.05, 0.6, 0.05, 0.0);
				}
				if (left % DoctorFateConfig.GLYPH_HIT_EVERY == 0) {
					AABB box = new AABB(s.center, s.center).inflate(DoctorFateConfig.GLYPH_RADIUS, 4, DoctorFateConfig.GLYPH_RADIUS);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
							e -> horizontal(e.position(), s.center) <= DoctorFateConfig.GLYPH_RADIUS && targetable(owner, e, s.nabu) && autoTarget(owner, e))) {
						hit(owner, e, DoctorFateConfig.GLYPH_DAMAGE * s.mult, s.nabu);
					}
					DoctorFate.sound(level, s.center, SoundEvents.TRIDENT_HIT_GROUND, 0.25f, 1.6f);
				}
			}
		}
		if (!WARDS.isEmpty()) {
			for (Ward w : WARDS) {
				ServerLevel level = server.getLevel(w.level);
				ServerPlayer owner = server.getPlayerList().getPlayer(w.owner);
				if (level == null || owner == null || level.getGameTime() >= w.until) {
					WARDS.remove(w);
					continue;
				}
				AABB box = new AABB(w.center, w.center).inflate(DoctorFateConfig.WARD_RADIUS, 4, DoctorFateConfig.WARD_RADIUS);
				for (Projectile pr : level.getEntitiesOfClass(Projectile.class, box,
						pr -> horizontal(pr.position(), w.center) <= DoctorFateConfig.WARD_RADIUS && hostileProjectile(owner, pr))) {
					level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 1.0f), pr.getX(), pr.getY(), pr.getZ(), 6, 0.15, 0.15, 0.15, 0.0);
					level.sendParticles(ParticleTypes.SMOKE, pr.getX(), pr.getY(), pr.getZ(), 3, 0.1, 0.1, 0.1, 0.0);
					pr.discard();
				}
				if (level.getGameTime() % 5 == 0) {
					ring(level, w.center.add(0, 0.1, 0), DoctorFateConfig.WARD_RADIUS, 40);
					level.sendParticles(ParticleTypes.ENCHANT, w.center.x, w.center.y + 1, w.center.z, 6, 3.0, 0.3, 3.0, 0.4);
				}
			}
		}
	}

	/** An area spell picks only real threats among neutral creatures; anything Chaos is fair game. */
	static boolean autoTarget(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.isHostile(owner, e) || DoctorFateChaos.isChaos(e) || owner.getLastHurtMob() == e;
	}

	static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	static boolean hostileProjectile(ServerPlayer owner, Projectile pr) {
		Entity shooter = pr.getOwner();
		if (shooter == owner) {
			return false;
		}
		return !(shooter instanceof Player sp && Squads.areAllies(owner, sp));
	}

	// ---------------------------------------------------------------- Shift+Z: Avatar of Order

	/** After the Forbidden-spell bargain: the 40-block golden projection rises behind him for 12 s. */
	static void raiseAvatar(ServerPlayer p) {
		long now = p.level().getGameTime();
		FateState n = DoctorFate.state(p).copy();
		n.avatarUntil = now + DoctorFateConfig.AVATAR_TICKS;
		n.avatarSlamAt = 0L;
		n.abilityReadyAt.put(DoctorFateAbilityManager.AVATAR, now + DoctorFateConfig.AVATAR_COOLDOWN);
		DoctorFate.save(p, n);
		ServerLevel level = (ServerLevel) p.level();
		DoctorFate.sound(level, p.position(), SoundEvents.BEACON_ACTIVATE, 0.7f, 0.5f);
		DoctorFate.sound(level, p.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 0.25f, 1.6f);
		level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 2.0f), p.getX(), p.getY() + 10, p.getZ(), 80, 3, 10, 3, 0.0);
	}

	/** An attack key while the Avatar stands: its giant hand slams where he looks (40 damage, 8 blocks). */
	static boolean avatarSlam(ServerPlayer p) {
		FateState s = DoctorFate.state(p);
		long now = p.level().getGameTime();
		if (s.avatarUntil <= now || now - s.avatarSlamAt < DoctorFateConfig.AVATAR_SLAM_GAP) {
			return false;
		}
		FateState n = s.copy();
		n.avatarSlamAt = now;
		DoctorFate.save(p, n);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 at = aimPoint(p, 30.0);
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
		if (ground.getY() <= at.y + 1.0 && at.y - ground.getY() < 12) {
			at = new Vec3(at.x, ground.getY(), at.z);
		}
		final Vec3 c = at;
		AABB box = new AABB(c, c).inflate(DoctorFateConfig.AVATAR_SLAM_RADIUS, 5, DoctorFateConfig.AVATAR_SLAM_RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.position().distanceTo(c) <= DoctorFateConfig.AVATAR_SLAM_RADIUS + e.getBbWidth() && targetable(p, e, false))) {
			if (hit(p, e, DoctorFateConfig.AVATAR_SLAM_DAMAGE, false)) {
				Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
				push = push.lengthSqr() < 1.0e-4 ? Vec3.ZERO : push.normalize().scale(1.2);
				e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.6, push.z));
				e.hurtMarked = true;
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
		ring(level, c.add(0, 0.2, 0), DoctorFateConfig.AVATAR_SLAM_RADIUS, 64);
		DoctorFate.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.45f, 0.6f);
		return true;
	}

	private static void tickAvatar(ServerPlayer p, FateState s, long now) {
		if (s.avatarUntil <= now) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		AABB box = p.getBoundingBox().inflate(DoctorFateConfig.AVATAR_PROJECTILE_RADIUS);
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, box, pr -> hostileProjectile(p, pr))) {
			level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 1.2f), pr.getX(), pr.getY(), pr.getZ(), 6, 0.15, 0.15, 0.15, 0.0);
			pr.discard();
		}
	}

	// ---------------------------------------------------------------- X: Ankh Step

	static boolean castStep(ServerPlayer p, double distance) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 start = p.position();
		Vec3 dir = p.getLookAngle();
		Vec3 eye = p.getEyePosition();
		HitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(distance)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		double reach = hit.getType() == HitResult.Type.MISS ? distance : Math.max(0.0, hit.getLocation().distanceTo(eye) - 0.7);
		Vec3 dest = null;
		for (double d = reach; d >= 1.0; d -= 0.5) {
			Vec3 cand = start.add(dir.scale(d));
			AABB box = p.getBoundingBox().move(cand.subtract(start));
			if (level.noCollision(p, box)) {
				dest = cand;
				break;
			}
		}
		if (dest == null) {
			return false;
		}
		gate(level, start.add(0, 1, 0), 0.8);
		p.teleportTo(level, dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
		p.resetFallDistance();
		gate(level, dest.add(0, 1, 0), 0.8);
		DoctorFate.sound(level, dest, SoundEvents.ENDERMAN_TELEPORT, 0.3f, 1.5f);
		return true;
	}

	// ---------------------------------------------------------------- Shift+X: Gate Drop

	static boolean castGateDrop(ServerPlayer p, LivingEntity target, boolean nabu) {
		if (target == null) {
			DoctorFate.say(p, "message.projecthero.doctor_fate.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (isBoss(target)) {
			DoctorFate.say(p, "message.projecthero.doctor_fate.boss_immune", ChatFormatting.GRAY);
			return false;
		}
		if (fizzles(p, target, nabu)) {
			fizzle(p, target);
			return false;
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 base = target.position();
		double lift = DoctorFateConfig.GATE_LIFT;
		while (lift > 3 && !level.noCollision(target, target.getBoundingBox().move(0, lift, 0))) {
			lift -= 1;
		}
		gate(level, base, target.getBbWidth() * 0.5 + 0.8);
		if (target instanceof ServerPlayer sp) {
			sp.teleportTo(level, base.x, base.y + lift, base.z, sp.getYRot(), sp.getXRot());
		} else {
			target.teleportTo(base.x, base.y + lift, base.z);
		}
		target.setDeltaMovement(0, -0.2, 0);
		target.hurtMarked = true;
		gate(level, base.add(0, lift, 0), target.getBbWidth() * 0.5 + 0.8);
		// he blinks beside where it lands
		if (!nabu) {
			Vec3 side = base.subtract(p.position()).multiply(1, 0, 1);
			side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
			Vec3 beside = base.subtract(side.scale(target.getBbWidth() * 0.5 + 1.2));
			BlockPos g = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(beside));
			Vec3 dest = new Vec3(beside.x, Math.abs(g.getY() - base.y) <= 4 ? g.getY() : base.y, beside.z);
			if (level.noCollision(p, p.getBoundingBox().move(dest.subtract(p.position())))) {
				gate(level, p.position().add(0, 1, 0), 0.7);
				p.teleportTo(level, dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
				p.resetFallDistance();
			}
		}
		DoctorFate.sound(level, base, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 0.8f);
		return true;
	}

	// ---------------------------------------------------------------- C: Order Blast

	static boolean castBlast(ServerPlayer p, Vec3 dir, float mult, boolean nabu) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 eye = p.getEyePosition();
		AABB box = p.getBoundingBox().inflate(DoctorFateConfig.BLAST_RANGE);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> targetable(p, e, nabu))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double dist = to.length();
			if (dist > DoctorFateConfig.BLAST_RANGE + e.getBbWidth() || dist < 1.0e-3 || to.normalize().dot(dir) < 0.55) {
				continue;
			}
			if (hit(p, e, DoctorFateConfig.BLAST_DAMAGE * mult, nabu)) {
				Vec3 push = to.normalize().scale(DoctorFateConfig.BLAST_KNOCKBACK * (isBoss(e) ? 0.2 : 1.0));
				e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.35 + Math.max(0, push.y), push.z));
				e.hurtMarked = true;
			}
		}
		for (int k = 1; k <= 8; k++) { // the cone of golden force
			Vec3 c = eye.add(dir.scale(k));
			double spread = k * 0.35;
			level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 1.4f), c.x, c.y, c.z, 6 + k, spread, spread, spread, 0.0);
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, eye.x + dir.x * 2, eye.y + dir.y * 2, eye.z + dir.z * 2, 1, 0, 0, 0, 0);
		DoctorFate.sound(level, eye, SoundEvents.EVOKER_CAST_SPELL, 0.5f, 0.7f);
		return true;
	}

	// ---------------------------------------------------------------- Shift+C: Ward of Order

	static boolean castWard(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 at = p.position();
		WARDS.removeIf(w -> w.owner.equals(p.getUUID()));
		WARDS.add(new Ward(level.dimension(), p.getUUID(), at, level.getGameTime() + DoctorFateConfig.WARD_TICKS));
		ring(level, at.add(0, 0.1, 0), DoctorFateConfig.WARD_RADIUS, 60);
		DoctorFate.sound(level, at, SoundEvents.BEACON_POWER_SELECT, 0.5f, 0.8f);
		return true;
	}

	/** Is {@code victim} (the caster or a squadmate) standing in one of the caster's live wards? */
	public static boolean inWard(LivingEntity victim) {
		if (WARDS.isEmpty()) {
			return false;
		}
		long now = victim.level().getGameTime();
		for (Ward w : WARDS) {
			if (w.level != victim.level().dimension() || now >= w.until || horizontal(victim.position(), w.center) > DoctorFateConfig.WARD_RADIUS
					|| Math.abs(victim.getY() - w.center.y) > 4) {
				continue;
			}
			if (victim.getUUID().equals(w.owner)) {
				return true;
			}
			Player owner = victim.level().getPlayerByUUID(w.owner);
			if (owner != null && victim instanceof Player vp && Squads.areAllies(owner, vp)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- Shift+V: Sight of Nabu

	static boolean castSight(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(DoctorFateConfig.SIGHT_RANGE),
				e -> e != p && e.isAlive() && e.distanceTo(p) <= DoctorFateConfig.SIGHT_RANGE)) {
			ids.add(e.getId());
			ids.add(DoctorFateChaos.isChaos(e) ? FateFxPayload.SIGHT_CHAOS : DoctorFateChaos.isOrder(e) ? FateFxPayload.SIGHT_ORDER : FateFxPayload.SIGHT_NEUTRAL);
		}
		int[] arr = ids.stream().mapToInt(Integer::intValue).toArray();
		DoctorFate.send(p, new FateFxPayload(FateFxPayload.SIGHT, p.getId(), DoctorFateConfig.SIGHT_TICKS, arr));
		DoctorFate.sound(p, SoundEvents.ENDER_EYE_DEATH, 0.4f, 1.4f);
		return true;
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		FateState s = DoctorFate.state(p);
		long now = p.level().getGameTime();
		tickShield(p, s, now);
		tickAvatar(p, s, now);
	}

	/** Every server tick: keeps the chained in place. */
	public static void tickBound(MinecraftServer server) {
		if (BOUND.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Bind> en : BOUND.entrySet()) {
			Bind b = en.getValue();
			ServerLevel level = server.getLevel(b.level);
			Entity e = level == null ? null : level.getEntity(b.entity);
			if (level == null || e == null || !e.isAlive() || level.getGameTime() >= b.until) {
				BOUND.remove(en.getKey());
				continue;
			}
			if (e.position().distanceToSqr(b.at) > 0.04) {
				if (e instanceof ServerPlayer sp) {
					sp.teleportTo(level, b.at.x, b.at.y, b.at.z, sp.getYRot(), sp.getXRot());
				} else {
					e.teleportTo(b.at.x, b.at.y, b.at.z);
				}
			}
			e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
			if (e instanceof Mob m) {
				m.getNavigation().stop();
			}
			if (level.getGameTime() % 5 == 0 && e instanceof LivingEntity le) {
				chains(level, le);
			}
		}
	}

	// ---------------------------------------------------------------- effects

	static void line(ServerLevel level, Vec3 a, Vec3 b, float size) {
		Vec3 d = b.subtract(a);
		int n = Math.max(2, (int) (d.length() * 2.5));
		for (int i = 0; i <= n; i++) {
			Vec3 c = a.add(d.scale(i / (double) n));
			level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, size), c.x, c.y, c.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** A golden ankh outline facing {@code dir}, {@code size} blocks tall-ish. */
	static void ankh(ServerLevel level, Vec3 c, Vec3 dir, double size, boolean big) {
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		Vec3 right = flat.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(-flat.z, 0, flat.x).normalize();
		Vec3 up = new Vec3(0, 1, 0);
		float ps = big ? 1.1f : 0.8f;
		// the loop
		for (int k = 0; k < 14; k++) {
			double a = k * Math.PI * 2 / 14;
			Vec3 p = c.add(up.scale(size * 0.55 + Math.sin(a) * size * 0.32)).add(right.scale(Math.cos(a) * size * 0.25));
			level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, ps), p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
		}
		// the bar and the stem
		for (int k = -3; k <= 3; k++) {
			Vec3 p = c.add(up.scale(size * 0.15)).add(right.scale(k * size * 0.15));
			level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, ps), p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
		}
		for (int k = 0; k <= 5; k++) {
			Vec3 p = c.add(up.scale(size * 0.15 - k * size * 0.2));
			level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, ps), p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
		}
	}

	/** A flat golden ring (an ankh gate, a ward circle). */
	static void ring(ServerLevel level, Vec3 c, double r, int n) {
		for (int k = 0; k < n; k++) {
			double a = k * Math.PI * 2 / n;
			level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 1.2f), c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0.0);
		}
	}

	/** An ankh gate opening at {@code c}. */
	static void gate(ServerLevel level, Vec3 c, double r) {
		ring(level, c.add(0, 0.1, 0), r, 24);
		ring(level, c.add(0, 0.1, 0), r * 0.6, 16);
		level.sendParticles(ParticleTypes.PORTAL, c.x, c.y + 0.5, c.z, 20, r * 0.4, 0.5, r * 0.4, 0.3);
	}

	/** Tests: a Glyph Storm at {@code at}. */
	public static boolean castGlyphStormForTest(ServerPlayer p, Vec3 at) {
		return castGlyphStorm(p, at, 1f, false);
	}

	/** Tests: raise the Ankh Shield. */
	public static boolean raiseShieldForTest(ServerPlayer p) {
		return raiseShield(p);
	}

	/** Tests: are there wards live? */
	public static int wards() {
		return WARDS.size();
	}
}
