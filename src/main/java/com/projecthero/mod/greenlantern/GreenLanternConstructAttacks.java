package com.projecthero.mod.greenlantern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity.Shape;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.14.3: the Green Lantern's hard-light attacks that are real, visible models ({@link HardLightConstructEntity})
 * rather than particles -- plus the gameplay behind each one. Everything is server-side; the entities only carry what
 * the renderer needs.
 *
 * <ul>
 *   <li><b>G</b> Construct Fist now <em>flies</em> (a giant fist, {@link #launchFist}); <b>Shift+G</b> swings a
 *   real war hammer down onto the ground ahead ({@link #swingHammer}). R's bolt and Shift+R's beam are drawn as
 *   streaks of hard light ({@link #boltStreak}, {@link #beamVisual}).</li>
 *   <li><b>Shift+X (hold)</b> Emerald Gatling -- a spinning minigun on the ring fist ({@link #gatlingStart}).</li>
 *   <li><b>Shift+C</b> Missile Barrage -- six homing missiles at the hostiles in front of you ({@link #missileBarrage}).</li>
 *   <li><b>H</b> Giant Hand -- grab the creature under the crosshair in a huge hand, crush it, H again to hurl it
 *   ({@link #giantHand}).</li>
 *   <li>Wheel constructs: Buzzsaw, Anvil Drop, Chain Snare, Launch Pad, Emerald Warrior ({@link #deploy}).</li>
 * </ul>
 * Pads, the warrior and chains are "live" and end with N (dismiss all), death, logout or a dimension change like
 * every other construct ({@link #dismissAll}).
 */
public final class GreenLanternConstructAttacks {
	public static final String GATLING_CD = "emerald_gatling";
	public static final String MISSILE_CD = "missile_barrage";
	public static final String HAND_CD = "giant_hand";

	/** Player -> ticks the Gatling has been spinning. */
	private static final Map<UUID, Integer> GATLING = new ConcurrentHashMap<>();
	/** Player -> the Continuous Beam's visible streak while it is channelled. */
	private static final Map<UUID, HardLightConstructEntity> BEAMS = new ConcurrentHashMap<>();
	/** Player -> the Giant Hand while it is holding something. */
	private static final Map<UUID, HardLightConstructEntity> HANDS = new ConcurrentHashMap<>();
	/** Player -> lasting hard light: launch pads, the warrior, chains. */
	private static final Map<UUID, List<HardLightConstructEntity>> LIVE = new ConcurrentHashMap<>();
	/** Entity -> game time until which a fall is forgiven (the owner and squadmates thrown by a Launch Pad). */
	private static final Map<UUID, Long> FALL_SAFE = new ConcurrentHashMap<>();

	private static final ParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.6f);
	private static final ParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.55f, 1.0f, 0.62f), 0.9f);

	private GreenLanternConstructAttacks() {
	}

	public static void clearSessionState() {
		GATLING.clear();
		BEAMS.clear();
		HANDS.clear();
		LIVE.clear();
		FALL_SAFE.clear();
	}

	/** Death / logout / dimension change / power loss. */
	public static void onCleanup(ServerPlayer player) {
		gatlingStop(player, false);
		beamVisualEnd(player);
		HardLightConstructEntity hand = HANDS.remove(player.getUUID());
		if (hand != null) {
			hand.discard();
		}
		dismissAll(player.getUUID());
		GreenLanternVisuals.clear(player);
	}

	/** N / death: every lasting hard-light entity this player owns dissolves. */
	public static void dismissAll(UUID owner) {
		List<HardLightConstructEntity> list = LIVE.remove(owner);
		if (list == null) {
			return;
		}
		for (HardLightConstructEntity e : list) {
			if (!e.isRemoved()) {
				dissolve(e);
				e.discard();
			}
		}
	}

	/** How many lasting constructs of {@code shape} this player has up. */
	public static int liveCount(UUID owner, Shape shape) {
		int n = 0;
		for (HardLightConstructEntity e : LIVE.getOrDefault(owner, List.of())) {
			if (!e.isRemoved() && e.shape() == shape) {
				n++;
			}
		}
		return n;
	}

	/** v0.14.3: a Launch Pad forgives the fall of whoever it threw, if they are its owner or the owner's squad. */
	public static boolean consumeFallSafe(Entity e) {
		Long until = FALL_SAFE.get(e.getUUID());
		if (until == null) {
			return false;
		}
		if (e.level().getGameTime() > until) {
			FALL_SAFE.remove(e.getUUID());
			return false;
		}
		FALL_SAFE.remove(e.getUUID());
		return true;
	}

	// ---------------------------------------------------------------- helpers

	private static ServerPlayer owner(HardLightConstructEntity e) {
		if (e.owner == null || e.getServer() == null) {
			return null;
		}
		ServerPlayer p = e.getServer().getPlayerList().getPlayer(e.owner);
		return p != null && p.level() == e.level() && p.isAlive() ? p : null;
	}

	/** Whether this Green Lantern's hard light may hurt {@code e} at all. */
	static boolean canHit(ServerPlayer owner, LivingEntity e) {
		if (e == owner || !e.isAlive() || e instanceof ArmorStand) {
			return false;
		}
		if (e instanceof Player p) {
			return !p.isSpectator() && !p.isCreative() && owner.getServer() != null && owner.getServer().isPvpAllowed()
					&& !Squads.areAllies(owner, p);
		}
		return !(e instanceof TamableAnimal tame && tame.isTame() && owner.getUUID().equals(tame.getOwnerUUID()));
	}

	private static float power(ServerPlayer owner) {
		return GreenLanternOath.multiplier(owner);
	}

	private static void hit(ServerPlayer owner, LivingEntity target, float damage) {
		AbilityHelpers.hurtBurst(owner, target, damage);
	}

	/** The first creature the segment {@code from -> to} passes within {@code radius} of, not already in {@code skip}. */
	private static LivingEntity firstAlong(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to, double radius,
			java.util.Set<Integer> skip) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(radius))) {
			if (!canHit(owner, e) || (skip != null && skip.contains(e.getId()))) {
				continue;
			}
			AABB box = e.getBoundingBox().inflate(radius * 0.5);
			var clip = box.clip(from, to);
			boolean inside = box.contains(from);
			if (clip.isEmpty() && !inside) {
				continue;
			}
			double d = inside ? 0.0 : clip.get().distanceToSqr(from);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private static BlockHitResult clip(ServerLevel level, Vec3 from, Vec3 to, Entity ctx) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx));
	}

	private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static void dissolve(HardLightConstructEntity e) {
		if (e.level() instanceof ServerLevel level) {
			level.sendParticles(SPARK, e.getX(), e.getY() + 0.5, e.getZ(), 18, 0.4, 0.5, 0.4, 0.04);
			play(level, e.position(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.5f, 1.4f);
		}
	}

	private static void track(ServerPlayer owner, HardLightConstructEntity e) {
		LIVE.computeIfAbsent(owner.getUUID(), k -> new ArrayList<>()).add(e);
	}

	private static boolean ready(ServerPlayer player, String cd) {
		if (GreenLantern.abilityReady(player, cd)) {
			return true;
		}
		int seconds = (int) Math.ceil(GreenLantern.cooldownRemaining(player, cd) / 20.0);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.construct_cooldown", seconds), true);
		return false;
	}

	private static boolean pay(ServerPlayer player, float cost) {
		if (GreenLanternEnergy.spend(player, cost)) {
			GreenLanternBattery.onAbilityUsed(player);
			return true;
		}
		GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
		return false;
	}

	private static Vec3 hand(ServerPlayer player) {
		return AbilityHelpers.handPosition(player);
	}

	// ---------------------------------------------------------------- R: bolt / beam streaks

	/** A short-lived streak of hard light from {@code from} to {@code to} (Ring Bolt, the Gatling, turret shots). */
	public static void boltStreak(ServerPlayer owner, Vec3 from, Vec3 to, float width, int life) {
		ServerLevel level = owner.serverLevel();
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.BOLT, owner.getUUID(), from, width, life);
		e.setEnd(to);
		level.addFreshEntity(e);
	}

	/** Continuous Beam: keeps one beam streak glued to the hand and the aim point while it is channelled. */
	public static void beamVisual(ServerPlayer owner, Vec3 from, Vec3 to) {
		HardLightConstructEntity e = BEAMS.get(owner.getUUID());
		if (e == null || e.isRemoved()) {
			e = HardLightConstructEntity.create(owner.serverLevel(), Shape.BEAM, owner.getUUID(), from, 1.0f, 0);
			e.setEnd(to);
			owner.serverLevel().addFreshEntity(e);
			BEAMS.put(owner.getUUID(), e);
		}
		e.setPos(from.x, from.y, from.z);
		e.setEnd(to);
	}

	public static void beamVisualEnd(ServerPlayer owner) {
		HardLightConstructEntity e = BEAMS.remove(owner.getUUID());
		if (e != null) {
			e.discard();
		}
	}

	// ---------------------------------------------------------------- G: flying fist

	public static void launchFist(ServerPlayer owner, float damage) {
		ServerLevel level = owner.serverLevel();
		Vec3 start = hand(owner);
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.FIST, owner.getUUID(), start, 1.0f, 40);
		Vec3 aim = AbilityHelpers.aimPoint(owner, GreenLanternConfig.FIST_RANGE);
		e.velocity = aim.subtract(start).normalize();
		e.damage = damage;
		e.face(e.velocity);
		level.addFreshEntity(e);
	}

	private static void tickFist(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		Vec3 pos = e.position();
		double step = Math.min(GreenLanternConfig.FIST_SPEED_PER_TICK, GreenLanternConfig.FIST_RANGE - e.travelled);
		Vec3 next = pos.add(e.velocity.scale(step));
		BlockHitResult bh = clip(level, pos, next, e);
		Vec3 end = bh.getType() == HitResult.Type.MISS ? next : bh.getLocation();
		LivingEntity target = firstAlong(level, owner, pos, end, 1.0, null);
		if (target != null) {
			fistImpact(e, owner, target.position().add(0, target.getBbHeight() * 0.5, 0), target);
			return;
		}
		e.setPos(end.x, end.y, end.z);
		e.face(e.velocity);
		e.travelled += step;
		level.sendParticles(GREEN_DUST, pos.x, pos.y, pos.z, 2, 0.15, 0.15, 0.15, 0.0);
		if (bh.getType() != HitResult.Type.MISS || e.travelled >= GreenLanternConfig.FIST_RANGE - 1.0e-3) {
			fistImpact(e, owner, end, null);
		}
	}

	private static void fistImpact(HardLightConstructEntity e, ServerPlayer owner, Vec3 at, LivingEntity direct) {
		ServerLevel level = (ServerLevel) e.level();
		if (direct != null) {
			hit(owner, direct, e.damage);
			if (!com.projecthero.mod.titanshifter.TitanCombat.isBoss(direct)) {
				AbilityHelpers.knockbackFrom(direct, owner.position(), GreenLanternConfig.FIST_KNOCKBACK);
				AbilityHelpers.push(direct, new Vec3(0, 0.35, 0));
			}
		}
		for (LivingEntity o : AbilityHelpers.living(level, at, GreenLanternConfig.FIST_SPLASH_RADIUS, x -> canHit(owner, x))) {
			if (o != direct) {
				hit(owner, o, e.damage * 0.5f);
				AbilityHelpers.knockbackFrom(o, at, 1.0);
			}
		}
		level.sendParticles(SPARK, at.x, at.y, at.z, 30, 0.5, 0.5, 0.5, 0.2);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		play(level, at, SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 0.9f);
		play(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8f, 0.8f);
		e.setPos(at.x, at.y, at.z);
		e.velocity = Vec3.ZERO;
		e.phase = 1;
		e.markAction();
		e.setLife(e.tickCount + 6);
	}

	// ---------------------------------------------------------------- Shift+G: war hammer

	public static void swingHammer(ServerPlayer owner, float damage) {
		ServerLevel level = owner.serverLevel();
		Vec3 look = owner.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, owner.getYRot()) : flat.normalize();
		Vec3 at = owner.position().add(flat.scale(3.0));
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.HAMMER, owner.getUUID(), at, 1.0f,
				GreenLanternConfig.HAMMER_SWING_TICKS + 10);
		e.damage = damage;
		e.setYRot(owner.getYRot());
		e.setXRot(0f);
		level.addFreshEntity(e);
		play(level, owner.position(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.6f);
	}

	private static void tickHammer(HardLightConstructEntity e, ServerPlayer owner) {
		if (e.tickCount != GreenLanternConfig.HAMMER_SWING_TICKS) {
			return;
		}
		ServerLevel level = (ServerLevel) e.level();
		Vec3 center = e.position();
		for (LivingEntity t : AbilityHelpers.living(level, center, GreenLanternConfig.HAMMER_RADIUS, x -> canHit(owner, x))) {
			hit(owner, t, e.damage);
			boolean boss = t.getMaxHealth() >= GreenLanternConfig.HAMMER_BOSS_MAX_HEALTH_THRESHOLD;
			AbilityHelpers.knockbackFrom(t, center, boss ? 0.25 : 1.0);
			if (!boss) {
				t.setDeltaMovement(t.getDeltaMovement().x, GreenLanternConfig.HAMMER_KNOCKUP, t.getDeltaMovement().z);
				t.hurtMarked = true;
			}
		}
		// a ring of light racing out across the ground, and chips of the ground itself
		BlockState ground = level.getBlockState(BlockPos.containing(center).below());
		for (int ring = 1; ring <= 3; ring++) {
			double r = GreenLanternConfig.HAMMER_RADIUS * ring / 3.0;
			int points = 14 * ring;
			for (int i = 0; i < points; i++) {
				double a = i * Math.PI * 2 / points;
				level.sendParticles(GREEN_DUST, center.x + Math.cos(a) * r, center.y + 0.15, center.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), center.x, center.y + 0.2, center.z, 40,
					1.5, 0.2, 1.5, 0.3);
		}
		level.sendParticles(SPARK, center.x, center.y + 0.5, center.z, 40, 1.0, 0.4, 1.0, 0.25);
		play(level, center, SoundEvents.ANVIL_LAND, 1.0f, 0.55f);
		play(level, center, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.3f);
		e.markAction();
	}

	// ---------------------------------------------------------------- Shift+X: Emerald Gatling

	public static boolean isGatling(ServerPlayer player) {
		return GATLING.containsKey(player.getUUID());
	}

	public static void gatlingStart(ServerPlayer player) {
		if (isGatling(player) || !ready(player, GATLING_CD)) {
			return;
		}
		if (!GreenLanternEnergy.canSpend(player, GreenLanternConfig.GATLING_COST_PER_SHOT)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GATLING.put(player.getUUID(), 0);
		GreenLanternVisuals.channel(player, GreenLanternFx.CH_GATLING, true);
		play(player.serverLevel(), player.position(), SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.8f);
	}

	public static void gatlingStop(ServerPlayer player, boolean cooldown) {
		if (GATLING.remove(player.getUUID()) == null) {
			return;
		}
		GreenLanternVisuals.channel(player, GreenLanternFx.CH_GATLING, false);
		if (cooldown) {
			GreenLantern.triggerCooldown(player, GATLING_CD, GreenLanternConfig.GATLING_COOLDOWN_TICKS);
		}
		play(player.serverLevel(), player.position(), SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.9f);
	}

	public static void gatlingTick(ServerPlayer player) {
		Integer t = GATLING.get(player.getUUID());
		if (t == null) {
			return;
		}
		if (t >= GreenLanternConfig.GATLING_MAX_TICKS) {
			gatlingStop(player, true);
			return;
		}
		GATLING.put(player.getUUID(), t + 1);
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 0, false, false, false));
		int fired = t - GreenLanternConfig.GATLING_SPINUP_TICKS;
		if (fired < 0) {
			if (t % 3 == 0) {
				play(player.serverLevel(), player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4f, 1.0f + t * 0.1f);
			}
			return;
		}
		if (fired % GreenLanternConfig.GATLING_INTERVAL_TICKS != 0) {
			return;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.GATLING_COST_PER_SHOT)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			gatlingStop(player, true);
			return;
		}
		GreenLanternBattery.onAbilityUsed(player);
		ServerLevel level = player.serverLevel();
		Vec3 eye = player.getEyePosition();
		float spread = 1.6f;
		Vec3 dir = Vec3.directionFromRotation(player.getXRot() + (player.getRandom().nextFloat() - 0.5f) * spread,
				player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * spread);
		Vec3 far = eye.add(dir.scale(GreenLanternConfig.GATLING_RANGE));
		BlockHitResult bh = clip(level, eye, far, player);
		Vec3 end = bh.getType() == HitResult.Type.MISS ? far : bh.getLocation();
		LivingEntity target = firstAlong(level, player, eye, end, 0.3, null);
		if (target != null) {
			end = target.position().add(0, target.getBbHeight() * 0.6, 0);
			target.invulnerableTime = 0;
			AbilityHelpers.hurt(player, target, GreenLanternConfig.GATLING_DAMAGE * power(player));
			level.sendParticles(SPARK, end.x, end.y, end.z, 3, 0.15, 0.15, 0.15, 0.08);
		} else if (bh.getType() != HitResult.Type.MISS) {
			level.sendParticles(SPARK, end.x, end.y, end.z, 2, 0.05, 0.05, 0.05, 0.05);
		}
		boltStreak(player, hand(player), end, 0.35f, 3);
		play(level, player.position(), SoundEvents.AMETHYST_CLUSTER_HIT, 0.45f, 1.7f + player.getRandom().nextFloat() * 0.3f);
	}

	// ---------------------------------------------------------------- Shift+C: Missile Barrage

	public static void missileBarrage(ServerPlayer player) {
		if (!ready(player, MISSILE_CD) || !pay(player, GreenLanternConfig.MISSILE_COST)) {
			return;
		}
		GreenLantern.triggerCooldown(player, MISSILE_CD, GreenLanternConfig.MISSILE_COOLDOWN_TICKS);
		ServerLevel level = player.serverLevel();
		Vec3 look = player.getLookAngle();
		List<LivingEntity> targets = new ArrayList<>();
		LivingEntity aimed = AbilityHelpers.raycastEntity(player, GreenLanternConfig.MISSILE_TARGET_RANGE);
		if (aimed != null && canHit(player, aimed)) {
			targets.add(aimed);
		}
		Vec3 eye = player.getEyePosition();
		AbilityHelpers.living(level, eye, GreenLanternConfig.MISSILE_TARGET_RANGE,
				e -> e != aimed && GreenLanternConstructs.isHostileTarget(player, e)
						&& e.position().subtract(player.position()).normalize().dot(look) > 0.25
						&& GreenLanternConstructs.canSee(level, eye, e, player)).stream()
				.sorted(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
				.limit(GreenLanternConfig.MISSILE_COUNT)
				.forEach(targets::add);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		for (int i = 0; i < GreenLanternConfig.MISSILE_COUNT; i++) {
			float side = (i % 2 == 0 ? 1 : -1) * (0.35f + 0.25f * (i / 2));
			Vec3 start = eye.add(right.scale(side * 0.6)).add(0, -0.2 + 0.15 * (i / 2), 0);
			HardLightConstructEntity m = HardLightConstructEntity.create(level, Shape.MISSILE, player.getUUID(), start, 1.0f,
					GreenLanternConfig.MISSILE_LIFE_TICKS);
			m.velocity = look.scale(0.5).add(right.scale(side * 0.45)).add(0, 0.35 + 0.1 * (i / 2), 0);
			m.homingId = targets.isEmpty() ? -1 : targets.get(i % targets.size()).getId();
			m.origin = AbilityHelpers.aimPoint(player, GreenLanternConfig.MISSILE_TARGET_RANGE);
			m.damage = GreenLanternConfig.MISSILE_DAMAGE * power(player);
			m.face(m.velocity);
			level.addFreshEntity(m);
		}
		GreenLanternVisuals.anim(player, GreenLanternFx.ANIM_MISSILES);
		play(level, player.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.8f);
		play(level, player.position(), SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.6f);
	}

	private static void tickMissile(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		if (e.tickCount >= e.life() - 1) {
			missileBoom(e, owner, e.position());
			return;
		}
		Entity target = e.homingId >= 0 ? level.getEntity(e.homingId) : null;
		Vec3 goal = target instanceof LivingEntity l && l.isAlive() ? l.position().add(0, l.getBbHeight() * 0.55, 0) : e.origin;
		double speed = Math.min(GreenLanternConfig.MISSILE_SPEED_PER_TICK, 0.5 + e.tickCount * 0.08);
		Vec3 want = goal.subtract(e.position());
		if (want.lengthSqr() > 1.0e-4 && e.tickCount >= 4) {
			double steer = Math.min(0.35, 0.12 + e.tickCount * 0.012);
			e.velocity = e.velocity.normalize().scale(1.0 - steer).add(want.normalize().scale(steer)).normalize();
		} else if (e.tickCount < 4) {
			e.velocity = e.velocity.add(0, -0.04, 0);
		}
		Vec3 pos = e.position();
		Vec3 next = pos.add(e.velocity.normalize().scale(speed));
		BlockHitResult bh = clip(level, pos, next, e);
		Vec3 end = bh.getType() == HitResult.Type.MISS ? next : bh.getLocation();
		LivingEntity struck = firstAlong(level, owner, pos, end, 0.7, null);
		if (struck != null) {
			missileBoom(e, owner, struck.position().add(0, struck.getBbHeight() * 0.5, 0));
			return;
		}
		e.setPos(end.x, end.y, end.z);
		e.face(e.velocity);
		level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.0);
		level.sendParticles(GREEN_DUST, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.0);
		if (bh.getType() != HitResult.Type.MISS || (target == null && end.distanceToSqr(e.origin) < 1.0)) {
			missileBoom(e, owner, end);
		}
	}

	private static void missileBoom(HardLightConstructEntity e, ServerPlayer owner, Vec3 at) {
		ServerLevel level = (ServerLevel) e.level();
		for (LivingEntity t : AbilityHelpers.living(level, at, GreenLanternConfig.MISSILE_BLAST_RADIUS, x -> canHit(owner, x))) {
			t.invulnerableTime = 0;
			AbilityHelpers.hurt(owner, t, e.damage);
			AbilityHelpers.knockbackFrom(t, at, 0.6);
		}
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(SPARK, at.x, at.y, at.z, 24, 0.5, 0.5, 0.5, 0.25);
		level.sendParticles(GREEN_DUST, at.x, at.y, at.z, 16, 0.8, 0.8, 0.8, 0.05);
		play(level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 0.9f + level.random.nextFloat() * 0.3f);
		e.discard();
	}

	// ---------------------------------------------------------------- H: Giant Hand

	public static boolean isHolding(ServerPlayer player) {
		HardLightConstructEntity h = HANDS.get(player.getUUID());
		return h != null && !h.isRemoved() && h.phase == 0;
	}

	public static void giantHand(ServerPlayer player) {
		if (isHolding(player)) {
			handThrow(player, HANDS.get(player.getUUID()));
			return;
		}
		if (!ready(player, HAND_CD)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, GreenLanternConfig.HAND_RANGE);
		if (target == null || !AbilityHelpers.isValidGrabTarget(target, player) || Squads.areAllies(player, target)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.invalid_target");
			return;
		}
		if (!pay(player, GreenLanternConfig.HAND_COST)) {
			return;
		}
		ServerLevel level = player.serverLevel();
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.HAND, player.getUUID(),
				target.position().add(0, target.getBbHeight() * 0.5, 0), Math.max(1.0f, target.getBbHeight() / 1.6f), 0);
		e.setTargetId(target.getId());
		e.homingId = target.getId();
		e.face(player.getLookAngle());
		level.addFreshEntity(e);
		HANDS.put(player.getUUID(), e);
		GreenLanternVisuals.anim(player, GreenLanternFx.ANIM_GRAB);
		GreenLanternVisuals.channel(player, GreenLanternFx.CH_HAND, true);
		AbilityHelpers.line(level, hand(player), e.position(), SPARK, 1.5);
		play(level, e.position(), SoundEvents.BEACON_POWER_SELECT, 0.9f, 1.2f);
		play(level, e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.7f);
	}

	private static void handThrow(ServerPlayer player, HardLightConstructEntity e) {
		Entity t = e.level().getEntity(e.homingId);
		if (t instanceof LivingEntity target && target.isAlive()) {
			Vec3 v = player.getLookAngle().scale(GreenLanternConfig.HAND_THROW_SPEED).add(0, 0.3, 0);
			target.setDeltaMovement(v);
			target.hurtMarked = true;
			if (target instanceof ServerPlayer sp) {
				sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
			}
		}
		e.phase = 1;
		e.markAction();
		e.velocity = player.getLookAngle();
		e.damage = GreenLanternConfig.HAND_THROW_DAMAGE * power(player);
		e.setLife(e.tickCount + 40);
		HANDS.remove(player.getUUID());
		GreenLanternVisuals.channel(player, GreenLanternFx.CH_HAND, false);
		GreenLanternVisuals.anim(player, GreenLanternFx.ANIM_THROW);
		GreenLantern.triggerCooldown(player, HAND_CD, GreenLanternConfig.HAND_COOLDOWN_TICKS);
		play(player.serverLevel(), e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
	}

	private static void tickHand(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		Entity t = level.getEntity(e.homingId);
		LivingEntity target = t instanceof LivingEntity l && l.isAlive() ? l : null;
		if (e.phase == 1) {
			// thrown: the first thing the flung creature slams into hurts it (and whatever it hits)
			if (target == null || e.travelled > 0) {
				return;
			}
			boolean landed = e.tickCount - e.actionTick() > 3 && (target.horizontalCollision || target.onGround()
					|| target.verticalCollision);
			LivingEntity bowled = landed ? null : firstNear(level, owner, target);
			if (landed || bowled != null) {
				Vec3 at = target.position();
				hit(owner, target, e.damage);
				for (LivingEntity o : AbilityHelpers.living(level, at, 2.5, x -> x != target && canHit(owner, x))) {
					hit(owner, o, e.damage * 0.5f);
					AbilityHelpers.knockbackFrom(o, at, 1.0);
				}
				level.sendParticles(SPARK, at.x, at.y + 0.5, at.z, 24, 0.5, 0.4, 0.5, 0.2);
				play(level, at, SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 0.7f);
				e.travelled = 1; // impact spent
			}
			return;
		}
		if (target == null || owner.distanceToSqr(target) > 40.0 * 40.0) {
			releaseHand(owner, e);
			return;
		}
		Vec3 eye = owner.getEyePosition();
		Vec3 want = eye.add(owner.getLookAngle().scale(GreenLanternConfig.HAND_HOLD_DISTANCE + target.getBbWidth()));
		BlockHitResult bh = clip(level, eye, want, owner);
		if (bh.getType() != HitResult.Type.MISS) {
			want = bh.getLocation().subtract(owner.getLookAngle().scale(target.getBbWidth() * 0.5 + 0.3));
		}
		Vec3 cur = e.position();
		Vec3 pos = e.tickCount < 6 ? cur.lerp(want, 0.35) : want;
		e.setPos(pos.x, pos.y, pos.z);
		e.face(owner.getLookAngle());
		target.setPos(pos.x, pos.y - target.getBbHeight() * 0.5, pos.z);
		target.setDeltaMovement(Vec3.ZERO);
		target.resetFallDistance();
		target.hurtMarked = true;
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (e.tickCount > 0 && e.tickCount % GreenLanternConfig.HAND_SQUEEZE_INTERVAL_TICKS == 0) {
			target.invulnerableTime = 0;
			AbilityHelpers.hurt(owner, target, GreenLanternConfig.HAND_SQUEEZE_DAMAGE * power(owner));
			level.sendParticles(SPARK, pos.x, pos.y, pos.z, 6, 0.3, 0.3, 0.3, 0.05);
			play(level, pos, SoundEvents.AMETHYST_BLOCK_HIT, 0.8f, 0.6f);
		}
		if (e.tickCount >= GreenLanternConfig.HAND_MAX_HOLD_TICKS) {
			handThrow(owner, e);
		}
	}

	private static LivingEntity firstNear(ServerLevel level, ServerPlayer owner, LivingEntity flung) {
		for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, flung.getBoundingBox().inflate(0.4),
				x -> x != flung && canHit(owner, x))) {
			return o;
		}
		return null;
	}

	private static void releaseHand(ServerPlayer owner, HardLightConstructEntity e) {
		HANDS.remove(owner.getUUID());
		GreenLanternVisuals.channel(owner, GreenLanternFx.CH_HAND, false);
		dissolve(e);
		e.discard();
	}

	// ---------------------------------------------------------------- wheel constructs

	/** Cooldown id for the v0.14.3 wheel constructs, null if it has none. */
	public static String cooldownIdFor(ConstructType type) {
		return switch (type) {
			case BUZZSAW -> "construct_buzzsaw";
			case ANVIL_DROP -> "construct_anvil";
			case CHAIN_SNARE -> "construct_chains";
			case EMERALD_WARRIOR -> "construct_warrior";
			default -> null;
		};
	}

	private static int cooldownTicksFor(ConstructType type) {
		return switch (type) {
			case BUZZSAW -> GreenLanternConfig.BUZZSAW_COOLDOWN_TICKS;
			case ANVIL_DROP -> GreenLanternConfig.ANVIL_COOLDOWN_TICKS;
			case CHAIN_SNARE -> GreenLanternConfig.CHAINS_COOLDOWN_TICKS;
			case EMERALD_WARRIOR -> GreenLanternConfig.WARRIOR_COOLDOWN_TICKS;
			default -> 0;
		};
	}

	/** Deploys one of the v0.14.3 entity constructs. Returns false if {@code type} is not one of them. */
	public static boolean deploy(ServerPlayer player, ConstructType type) {
		if (type.kind() != ConstructType.Kind.ATTACK && type.kind() != ConstructType.Kind.SUMMON) {
			return false;
		}
		String cd = cooldownIdFor(type);
		if (cd != null && !ready(player, cd)) {
			return true;
		}
		if (type == ConstructType.LAUNCH_PAD && liveCount(player.getUUID(), Shape.LAUNCH_PAD) >= GreenLanternConfig.PAD_MAX_LIVE) {
			GreenLanternEnergy.feedback(player, "message.projecthero.green_lantern.pad_limit");
			return true;
		}
		// find targets before paying, so a miss costs nothing
		Vec3 spot = null;
		LivingEntity sawTarget = null;
		List<LivingEntity> snared = null;
		switch (type) {
			case ANVIL_DROP -> spot = groundUnder(player, AbilityHelpers.aimPoint(player, GreenLanternConfig.ANVIL_RANGE));
			case LAUNCH_PAD -> {
				BlockHitResult bh = AbilityHelpers.raycastBlock(player, GreenLanternConfig.PAD_RANGE);
				spot = bh.getType() == HitResult.Type.MISS ? null : groundUnder(player, bh.getLocation().add(0, 0.1, 0));
			}
			case BUZZSAW -> {
				sawTarget = AbilityHelpers.raycastEntity(player, GreenLanternConfig.BUZZSAW_RANGE);
				if (sawTarget != null && !canHit(player, sawTarget)) {
					sawTarget = null;
				}
			}
			case CHAIN_SNARE -> {
				snared = AbilityHelpers.living(player.serverLevel(), player.position(), GreenLanternConfig.CHAINS_RADIUS,
						e -> GreenLanternConstructs.isHostileTarget(player, e));
				if (snared.isEmpty()) {
					GreenLanternEnergy.feedback(player, "message.projecthero.green_lantern.no_targets");
					return true;
				}
			}
			default -> {
			}
		}
		if ((type == ConstructType.ANVIL_DROP || type == ConstructType.LAUNCH_PAD) && spot == null) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.invalid_target");
			return true;
		}
		if (!pay(player, type.initialCost())) {
			return true;
		}
		if (cd != null) {
			GreenLantern.triggerCooldown(player, cd, cooldownTicksFor(type));
		}
		switch (type) {
			case BUZZSAW -> throwBuzzsaw(player, sawTarget);
			case ANVIL_DROP -> dropAnvil(player, spot);
			case CHAIN_SNARE -> snare(player, snared);
			case LAUNCH_PAD -> placePad(player, spot);
			case EMERALD_WARRIOR -> summonWarrior(player);
			default -> {
			}
		}
		GreenLanternVisuals.anim(player, GreenLanternFx.ANIM_CONSTRUCT);
		ServerLevel level = player.serverLevel();
		Vec3 h = hand(player);
		level.sendParticles(GREEN_DUST, h.x, h.y, h.z, 12, 0.15, 0.15, 0.15, 0.02);
		play(level, player.position(), SoundEvents.BEACON_AMBIENT, 0.5f, 1.4f);
		return true;
	}

	/** The top of the ground at or below {@code at} (up to 24 blocks down), or null over a void. */
	private static Vec3 groundUnder(ServerPlayer player, Vec3 at) {
		BlockHitResult down = clip(player.serverLevel(), at.add(0, 0.5, 0), at.add(0, -24, 0), player);
		return down.getType() == HitResult.Type.MISS ? null : down.getLocation();
	}

	// ---- Buzzsaw

	private static void throwBuzzsaw(ServerPlayer player, LivingEntity first) {
		ServerLevel level = player.serverLevel();
		Vec3 start = hand(player);
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.BUZZSAW, player.getUUID(), start, 1.0f, 140);
		e.velocity = first != null ? first.position().add(0, first.getBbHeight() * 0.5, 0).subtract(start).normalize()
				: player.getLookAngle();
		e.homingId = first != null ? first.getId() : -1;
		e.damage = GreenLanternConfig.BUZZSAW_DAMAGE * power(player);
		e.setYRot(player.getYRot());
		level.addFreshEntity(e);
		play(level, start, SoundEvents.TRIDENT_THROW.value(), 1.0f, 1.4f);
	}

	private static void tickBuzzsaw(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		Vec3 pos = e.position();
		if (e.returning) {
			Vec3 home = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
			Vec3 to = home.subtract(pos);
			if (to.lengthSqr() < 1.6 * 1.6) {
				play(level, home, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
				e.discard();
				return;
			}
			e.velocity = to.normalize();
		} else {
			Entity t = e.homingId >= 0 ? level.getEntity(e.homingId) : null;
			if (t instanceof LivingEntity l && l.isAlive()) {
				e.velocity = l.position().add(0, l.getBbHeight() * 0.5, 0).subtract(pos).normalize();
			} else if (e.homingId >= 0) {
				e.returning = true;
				return;
			}
		}
		double speed = GreenLanternConfig.BUZZSAW_SPEED_PER_TICK * (e.returning ? 1.3 : 1.0);
		Vec3 next = pos.add(e.velocity.scale(speed));
		BlockHitResult bh = e.returning ? null : clip(level, pos, next, e);
		Vec3 end = bh == null || bh.getType() == HitResult.Type.MISS ? next : bh.getLocation();
		LivingEntity struck = e.returning ? null : firstAlong(level, owner, pos, end, 0.9, e.hit);
		e.setPos(end.x, end.y, end.z);
		e.travelled += speed;
		if (e.tickCount % 3 == 0) {
			play(level, end, SoundEvents.GRINDSTONE_USE, 0.35f, 1.9f);
		}
		if (struck != null) {
			e.hit.add(struck.getId());
			hit(owner, struck, e.damage);
			AbilityHelpers.knockbackFrom(struck, pos, 0.5);
			Vec3 at = struck.position().add(0, struck.getBbHeight() * 0.5, 0);
			level.sendParticles(SPARK, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.2);
			play(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.5f);
			e.bounces++;
			LivingEntity next2 = e.bounces >= GreenLanternConfig.BUZZSAW_BOUNCES ? null
					: AbilityHelpers.living(level, at, GreenLanternConfig.BUZZSAW_BOUNCE_RANGE,
							x -> !e.hit.contains(x.getId()) && GreenLanternConstructs.isHostileTarget(owner, x)
									&& GreenLanternConstructs.canSee(level, at, x, owner)).stream()
							.min(Comparator.comparingDouble(x -> x.distanceToSqr(at))).orElse(null);
			if (next2 == null) {
				e.returning = true;
			} else {
				e.homingId = next2.getId();
			}
			return;
		}
		if ((bh != null && bh.getType() != HitResult.Type.MISS) || (!e.returning && e.travelled > GreenLanternConfig.BUZZSAW_RANGE)) {
			level.sendParticles(SPARK, end.x, end.y, end.z, 8, 0.2, 0.2, 0.2, 0.1);
			e.returning = true;
		}
	}

	// ---- Anvil Drop

	private static void dropAnvil(ServerPlayer player, Vec3 ground) {
		ServerLevel level = player.serverLevel();
		BlockHitResult up = clip(level, ground.add(0, 0.5, 0), ground.add(0, GreenLanternConfig.ANVIL_DROP_HEIGHT, 0), player);
		double h = up.getType() == HitResult.Type.MISS ? GreenLanternConfig.ANVIL_DROP_HEIGHT
				: Math.max(2.0, up.getLocation().y - ground.y - 2.0);
		Vec3 start = ground.add(0, h, 0);
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.ANVIL, player.getUUID(), start, 1.6f, 100);
		e.origin = ground;
		e.damage = power(player);
		e.setYRot(player.getYRot());
		level.addFreshEntity(e);
		AbilityHelpers.line(level, hand(player), start, SPARK, 1.2);
		// a warning ring where it will land
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 12;
			level.sendParticles(GREEN_DUST, ground.x + Math.cos(a) * GreenLanternConfig.ANVIL_RADIUS, ground.y + 0.1,
					ground.z + Math.sin(a) * GreenLanternConfig.ANVIL_RADIUS, 1, 0, 0, 0, 0);
		}
		play(level, start, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.5f);
	}

	private static void tickAnvil(HardLightConstructEntity e, ServerPlayer owner) {
		if (e.phase == 1) {
			return;
		}
		ServerLevel level = (ServerLevel) e.level();
		double vy = Math.max(-2.8, e.velocity.y - 0.16);
		e.velocity = new Vec3(0, vy, 0);
		Vec3 pos = e.position();
		Vec3 next = pos.add(0, vy, 0);
		BlockHitResult bh = clip(level, pos, next, e);
		boolean landed = bh.getType() != HitResult.Type.MISS || next.y <= e.origin.y;
		Vec3 end = landed ? new Vec3(pos.x, Math.max(e.origin.y, bh.getType() != HitResult.Type.MISS ? bh.getLocation().y : e.origin.y), pos.z) : next;
		e.setPos(end.x, end.y, end.z);
		if (!landed) {
			return;
		}
		float mult = e.damage;
		for (LivingEntity t : AbilityHelpers.living(level, end.add(0, 0.5, 0), GreenLanternConfig.ANVIL_RADIUS, x -> canHit(owner, x))) {
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(t, end));
			hit(owner, t, (d <= 1.8 ? GreenLanternConfig.ANVIL_CENTER_DAMAGE : GreenLanternConfig.ANVIL_OUTER_DAMAGE) * mult);
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 60, 3);
			if (!com.projecthero.mod.titanshifter.TitanCombat.isBoss(t)) {
				AbilityHelpers.knockbackFrom(t, end, 0.8);
			}
		}
		BlockState ground = level.getBlockState(BlockPos.containing(end).below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), end.x, end.y + 0.2, end.z, 60, 1.6, 0.2, 1.6, 0.3);
		}
		level.sendParticles(SPARK, end.x, end.y + 0.4, end.z, 50, 1.4, 0.4, 1.4, 0.2);
		level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y + 0.5, end.z, 2, 0.6, 0.2, 0.6, 0);
		play(level, end, SoundEvents.ANVIL_LAND, 1.4f, 0.5f);
		play(level, end, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.2f);
		e.phase = 1;
		e.markAction();
		e.setLife(e.tickCount + 14);
	}

	// ---- Chain Snare

	private static void snare(ServerPlayer player, List<LivingEntity> targets) {
		ServerLevel level = player.serverLevel();
		for (LivingEntity t : targets) {
			HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.CHAINS, player.getUUID(), t.position(),
					Math.max(0.8f, t.getBbWidth()), GreenLanternConfig.CHAINS_DURATION_TICKS);
			e.setTargetId(t.getId());
			e.homingId = t.getId();
			e.origin = t.position();
			level.addFreshEntity(e);
			track(player, e);
			AbilityHelpers.line(level, player.position().add(0, 0.2, 0), t.position().add(0, 0.2, 0), SPARK, 1.0);
			play(level, t.position(), SoundEvents.CHAIN_PLACE, 1.0f, 0.7f);
		}
		play(level, player.position(), SoundEvents.ANVIL_PLACE, 0.5f, 1.8f);
	}

	private static void tickChains(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		Entity t = level.getEntity(e.homingId);
		if (!(t instanceof LivingEntity target) || !target.isAlive()) {
			dissolve(e);
			e.discard();
			return;
		}
		// pinned where it stood: no walking, no jumping, it may still fall
		Vec3 v = target.getDeltaMovement();
		target.setDeltaMovement(0, Math.min(0, v.y), 0);
		if (!(target instanceof Player)) {
			target.setPos(e.origin.x, target.getY(), e.origin.z);
		}
		target.hurtMarked = true;
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (e.tickCount % 10 == 0) {
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 12, 6);
			target.invulnerableTime = 0;
			AbilityHelpers.hurt(owner, target, GreenLanternConfig.CHAINS_DAMAGE * power(owner));
		}
		e.setPos(target.getX(), target.getY(), target.getZ());
	}

	// ---- Launch Pad

	private static void placePad(ServerPlayer player, Vec3 at) {
		ServerLevel level = player.serverLevel();
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.LAUNCH_PAD, player.getUUID(), at, 1.0f,
				GreenLanternConfig.PAD_DURATION_TICKS);
		e.setYRot(player.getYRot());
		level.addFreshEntity(e);
		track(player, e);
		AbilityHelpers.line(level, hand(player), at, SPARK, 1.5);
		play(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
	}

	private static void tickPad(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		if (e.tickCount % 10 == 0) {
			e.hit.clear();
		}
		AABB top = new AABB(e.getX() - 0.95, e.getY(), e.getZ() - 0.95, e.getX() + 0.95, e.getY() + 0.7, e.getZ() + 0.95);
		for (LivingEntity l : level.getEntitiesOfClass(LivingEntity.class, top, x -> x.isAlive() && !(x instanceof ArmorStand)
				&& !x.isSpectator() && x.getDeltaMovement().y <= 0.1)) {
			if (!e.hit.add(l.getId())) {
				continue;
			}
			Vec3 v = l.getDeltaMovement();
			Vec3 launch = new Vec3(v.x * 1.5, GreenLanternConfig.PAD_LAUNCH_SPEED, v.z * 1.5);
			if (l instanceof ServerPlayer sp) {
				AbilityHelpers.launchSelf(sp, launch);
				if (sp == owner || Squads.areAllies(owner, sp)) {
					FALL_SAFE.put(sp.getUUID(), level.getGameTime() + 240);
				}
			} else {
				l.setDeltaMovement(launch);
				l.hurtMarked = true;
			}
			e.markAction();
			level.sendParticles(SPARK, e.getX(), e.getY() + 0.4, e.getZ(), 20, 0.5, 0.2, 0.5, 0.2);
			play(level, e.position(), SoundEvents.PISTON_EXTEND, 0.9f, 1.3f);
			play(level, e.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);
		}
	}

	// ---- Emerald Warrior

	private static void summonWarrior(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		for (HardLightConstructEntity old : new ArrayList<>(LIVE.getOrDefault(player.getUUID(), List.of()))) {
			if (old.shape() == Shape.WARRIOR) {
				dissolve(old);
				old.discard();
			}
		}
		Vec3 at = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.WARRIOR, player.getUUID(), at, 1.0f,
				GreenLanternConfig.WARRIOR_DURATION_TICKS);
		e.setYRot(player.getYRot());
		e.damage = GreenLanternConfig.WARRIOR_DAMAGE;
		level.addFreshEntity(e);
		track(player, e);
		level.sendParticles(SPARK, at.x, at.y + 1.0, at.z, 40, 0.4, 0.9, 0.4, 0.1);
		play(level, at, SoundEvents.ANVIL_USE, 0.7f, 1.6f);
		play(level, at, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.5f);
	}

	private static void tickWarrior(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		if (!GreenLanternEnergy.drainTick(owner, GreenLanternConfig.WARRIOR_UPKEEP_PER_SEC / 20f)) {
			dissolve(e);
			e.discard();
			return;
		}
		Entity current = e.homingId >= 0 ? level.getEntity(e.homingId) : null;
		LivingEntity target = current instanceof LivingEntity l && l.isAlive() && canHit(owner, l)
				&& l.distanceToSqr(owner) <= (GreenLanternConfig.WARRIOR_HUNT_RANGE + 6) * (GreenLanternConfig.WARRIOR_HUNT_RANGE + 6)
				? l : null;
		if (target == null || e.tickCount % 10 == 0) {
			LivingEntity attacker = owner.getLastHurtByMob();
			if (attacker != null && attacker.isAlive() && canHit(owner, attacker) && attacker.distanceToSqr(owner) < 400) {
				target = attacker;
			} else if (target == null) {
				target = AbilityHelpers.living(level, owner.position(), GreenLanternConfig.WARRIOR_HUNT_RANGE,
						x -> GreenLanternConstructs.isHostileTarget(owner, x)).stream()
						.min(Comparator.comparingDouble(x -> x.distanceToSqr(e))).orElse(null);
			}
			e.homingId = target == null ? -1 : target.getId();
		}
		Vec3 pos = e.position();
		Vec3 goal;
		if (target != null) {
			Vec3 away = pos.subtract(target.position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
			goal = target.position().add(away.scale(target.getBbWidth() * 0.5 + 1.1));
		} else {
			Vec3 look = owner.getLookAngle().multiply(1, 0, 1);
			look = look.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : look.normalize();
			Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
			goal = owner.position().add(right.scale(1.4)).subtract(look.scale(1.2)).add(0, 0.2, 0);
		}
		Vec3 to = goal.subtract(pos);
		double dist = to.length();
		double speed = Math.min(dist, GreenLanternConfig.WARRIOR_SPEED_PER_TICK * (dist > 12 ? 2.5 : 1.0));
		Vec3 next = dist < 1.0e-3 ? pos : pos.add(to.scale(speed / dist));
		if (dist > 48) {
			next = goal; // left far behind: re-forms at the owner's side
		}
		e.setPos(next.x, next.y, next.z);
		Vec3 faceDir = target != null ? target.position().subtract(next) : (dist > 0.3 ? to : owner.getLookAngle());
		e.face(new Vec3(faceDir.x, 0, faceDir.z));
		if (target != null && e.tickCount >= e.nextHitTick
				&& Math.sqrt(AbilityHelpers.distanceSqToBox(target, next.add(0, 1.0, 0))) <= 2.4) {
			e.nextHitTick = e.tickCount + 14;
			e.markAction();
			hit(owner, target, e.damage * power(owner));
			AbilityHelpers.knockbackFrom(target, next, 0.6);
			Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
			level.sendParticles(SPARK, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.15);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			play(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.3f);
		}
		if (e.tickCount % 8 == 0) {
			level.sendParticles(GREEN_DUST, next.x, next.y + 1.0, next.z, 1, 0.3, 0.5, 0.3, 0.0);
		}
	}

	// ---------------------------------------------------------------- the entity tick

	/** Called from {@link HardLightConstructEntity#tick} on the server. */
	public static void tickEntity(HardLightConstructEntity e) {
		ServerPlayer owner = owner(e);
		Shape shape = e.shape();
		if (owner == null) {
			if (shape != Shape.BOLT) {
				e.discard();
			}
			return;
		}
		switch (shape) {
			case BEAM -> {
				if (BEAMS.get(owner.getUUID()) != e) {
					e.discard();
				}
			}
			case FIST -> {
				if (e.phase == 0) {
					tickFist(e, owner);
				}
			}
			case HAMMER -> tickHammer(e, owner);
			case MISSILE -> tickMissile(e, owner);
			case BUZZSAW -> tickBuzzsaw(e, owner);
			case ANVIL -> tickAnvil(e, owner);
			case HAND -> tickHand(e, owner);
			case CHAINS -> tickChains(e, owner);
			case LAUNCH_PAD -> tickPad(e, owner);
			case WARRIOR -> tickWarrior(e, owner);
			default -> {
			}
		}
	}

	/** Once a second: drop dead entries from the live lists. */
	public static void pruneLive(net.minecraft.server.MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, List<HardLightConstructEntity>>> it = LIVE.entrySet().iterator(); it.hasNext();) {
			List<HardLightConstructEntity> list = it.next().getValue();
			list.removeIf(Entity::isRemoved);
			if (list.isEmpty()) {
				it.remove();
			}
		}
		HANDS.values().removeIf(Entity::isRemoved);
		BEAMS.values().removeIf(Entity::isRemoved);
		if (!FALL_SAFE.isEmpty()) {
			long now = server.overworld().getGameTime();
			FALL_SAFE.entrySet().removeIf(en -> en.getValue() < now);
		}
	}

	/** This player's live pads / warrior / chains (tests, and the construct count). */
	public static List<HardLightConstructEntity> live(UUID owner) {
		return LIVE.getOrDefault(owner, List.of());
	}
}
