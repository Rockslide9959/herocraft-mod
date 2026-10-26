package com.projecthero.mod.greenlantern;

import java.util.ArrayList;
import java.util.Comparator;
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
import com.projecthero.mod.hero.power.PowerToggles;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Green Lantern revamp: the fifteen new constructs ({@link ConstructType.Kind#ARSENAL}). Every one is a
 * {@link HardLightConstructEntity} with its own {@link Shape}; the gameplay runs here, server-side, and the client only
 * draws the result ({@code HardLightArsenalRenderer}).
 * <ul>
 *   <li><b>Boxing Glove</b> -- a spring-loaded glove punches 12 blocks straight out, hitting everything in the line.</li>
 *   <li><b>Fly Swatter</b> -- a huge swatter slams down on the aim point: 28 in a 4-block area and a 1 s stun.</li>
 *   <li><b>Catapult</b> -- a catapult beside you lobs a boulder at the aim point: 30 in a 3-block blast.</li>
 *   <li><b>Emerald Tentacles</b> -- a tentacle grabs every enemy within 6 blocks, holds it up 2 s, then slams it (18).</li>
 *   <li><b>Wrecking Ball</b> -- swings round you for 2 s: 16 to everything it hits, flung outward.</li>
 *   <li><b>Emerald Cannon</b> (hold) -- charge up to 2 s, release: 15-40 damage by charge, piercing 3.</li>
 *   <li><b>Emerald Bow</b> (hold) -- draw, release: a fan of 5 arrows (8 each).</li>
 *   <li><b>Bulldozer</b> -- drives 15 blocks forward shoving every enemy ahead (12), then dumps them.</li>
 *   <li><b>Bear Trap</b> -- up to 3 on the ground; the first enemy in is rooted 4 s and bitten for 16.</li>
 *   <li><b>Mirror Shield</b> (hold) -- a curved shield that sends projectiles straight back at the shooter.</li>
 *   <li><b>Grappling Claw</b> -- aim at a creature and it is pulled to you; aim at a block and you are pulled to it.</li>
 *   <li><b>Fighter Jet</b> -- circles the aim point for 10 s firing bolts (6) at enemies.</li>
 *   <li><b>Emerald Dragon</b> -- flies beside you for 15 s breathing light (4 every half second) at your foes.</li>
 *   <li><b>Emerald Mace</b> -- a heavy melee buff (toggle): 18 per hit plus a small shockwave.</li>
 *   <li><b>Med Bay</b> -- a hard-light tent: Regeneration II for you and your squad for 12 s.</li>
 * </ul>
 * Everything lasting ends with N (dismiss all), death, logout or a dimension change ({@link #dismissAll}); every damage
 * number goes through {@link GreenLanternRanks#power} (the Oath, the Lantern Rank and the Willpower Surge).
 */
public final class GreenLanternArsenal {
	private static final ParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.6f);
	private static final ParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.55f, 1.0f, 0.62f), 0.9f);
	private static final ResourceLocation MACE_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath("projecthero", "gl_emerald_mace");
	private static final ResourceLocation MACE_SPEED_ID = ResourceLocation.fromNamespaceAndPath("projecthero", "gl_emerald_mace_speed");

	/** Player -> lasting hard light: traps, the jet, the dragon, the Med Bay, the mace, held weapons. */
	private static final Map<UUID, List<HardLightConstructEntity>> LIVE = new ConcurrentHashMap<>();
	/** Player -> a hold construct being held (charge / draw / shield up), and when it started. */
	private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();

	private record Hold(ConstructType type, long since, HardLightConstructEntity model) {
	}

	private GreenLanternArsenal() {
	}

	public static void clearSessionState() {
		LIVE.clear();
		HOLDS.clear();
	}

	/** The mace's shockwave rides on a real melee swing (left click), never on the ring's own hits. */
	public static void initialize() {
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && target instanceof LivingEntity living
					&& GreenLantern.hasPower(sp)) {
				onMeleeHit(sp, living);
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
	}

	/** N / death / logout: everything this player has out dissolves. */
	public static void dismissAll(UUID owner) {
		Hold h = HOLDS.remove(owner);
		if (h != null && h.model() != null) {
			h.model().discard();
		}
		List<HardLightConstructEntity> list = LIVE.remove(owner);
		if (list != null) {
			for (HardLightConstructEntity e : list) {
				if (!e.isRemoved()) {
					dissolve(e);
					e.discard();
				}
			}
		}
	}

	public static void onCleanup(ServerPlayer player) {
		dismissAll(player.getUUID());
		maceModifiers(player, false);
	}

	public static List<HardLightConstructEntity> live(UUID owner) {
		return LIVE.getOrDefault(owner, List.of());
	}

	public static int liveCount(UUID owner, Shape shape) {
		int n = 0;
		for (HardLightConstructEntity e : live(owner)) {
			if (!e.isRemoved() && e.shape() == shape) {
				n++;
			}
		}
		return n;
	}

	private static HardLightConstructEntity first(UUID owner, Shape shape) {
		for (HardLightConstructEntity e : live(owner)) {
			if (!e.isRemoved() && e.shape() == shape) {
				return e;
			}
		}
		return null;
	}

	private static void keep(ServerPlayer owner, HardLightConstructEntity e) {
		LIVE.computeIfAbsent(owner.getUUID(), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(e);
	}

	// ---------------------------------------------------------------- deploy (tap constructs)

	public static void deploy(ServerPlayer p, ConstructType type) {
		if (type.isHold()) {
			// a tap (Quick Deploy, the old wheel) on a hold construct: a short hold that lets go by itself
			holdStart(p, type);
			com.projecthero.mod.greenlantern.bind.GreenLanternBindings.spawnOnceRelease(p, type);
			return;
		}
		switch (type) {
			case BOXING_GLOVE -> boxingGlove(p);
			case FLY_SWATTER -> flySwatter(p);
			case CATAPULT -> catapult(p);
			case EMERALD_TENTACLES -> tentacles(p);
			case WRECKING_BALL -> wreckingBall(p);
			case BULLDOZER -> bulldozer(p);
			case BEAR_TRAP -> bearTrap(p);
			case GRAPPLING_CLAW -> grapplingClaw(p);
			case FIGHTER_JET -> fighterJet(p);
			case EMERALD_DRAGON -> dragon(p);
			case EMERALD_MACE -> mace(p);
			case MED_BAY -> medBay(p);
			default -> {
			}
		}
	}

	// ---- Boxing Glove

	private static void boxingGlove(ServerPlayer p) {
		if (!pay(p, GreenLanternConfig.GLOVE_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = clipTo(level, eye, eye.add(look.scale(GreenLanternConfig.GLOVE_REACH)), p);
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.GLOVE, p.getUUID(), hand(p), 1.0f, 14);
		e.face(look);
		e.setEnd(end);
		level.addFreshEntity(e);
		float dmg = GreenLanternConfig.GLOVE_DAMAGE * GreenLanternRanks.power(p);
		for (LivingEntity t : alongLine(level, p, eye, end, 1.2)) {
			float homeRun = GreenLanternCombos.strike(p, t, GreenLanternCombos.Move.GLOVE); // v0.17.1: Home Run (swatted)
			hit(p, t, dmg * homeRun);
			if (homeRun <= 1f) {
				push(t, look.scale(1.6).add(0, 0.35, 0));
			}
		}
		constructFx(p);
		sound(level, end, SoundEvents.PISTON_EXTEND, 1.0f, 0.8f);
		sound(level, end, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
	}

	// ---- Fly Swatter

	private static void flySwatter(ServerPlayer p) {
		Vec3 spot = groundUnder(p, AbilityHelpers.aimPoint(p, GreenLanternConfig.SWATTER_RANGE));
		if (spot == null) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.invalid_target");
			return;
		}
		if (!pay(p, GreenLanternConfig.SWATTER_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.SWATTER, p.getUUID(), spot, 1.0f, 26);
		e.setYRot(p.getYRot());
		e.damage = GreenLanternConfig.SWATTER_DAMAGE * GreenLanternRanks.power(p);
		level.addFreshEntity(e);
		ring(level, spot, GreenLanternConfig.SWATTER_RADIUS);
		constructFx(p);
		sound(level, spot, SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.6f);
	}

	private static void tickSwatter(HardLightConstructEntity e, ServerPlayer owner) {
		if (e.tickCount != 6) {
			return;
		}
		ServerLevel level = (ServerLevel) e.level();
		Vec3 c = e.position();
		for (LivingEntity t : AbilityHelpers.living(level, c.add(0, 0.5, 0), GreenLanternConfig.SWATTER_RADIUS, x -> canHit(owner, x))) {
			GreenLanternCombos.mark(owner, t, GreenLanternCombos.Move.SWATTER); // v0.17.1: opens Home Run
			hit(owner, t, e.damage);
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, GreenLanternConfig.SWATTER_STUN_TICKS, 9);
			t.setDeltaMovement(t.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, -0.4, 0));
			t.hurtMarked = true;
		}
		e.markAction();
		level.sendParticles(SPARK, c.x, c.y + 0.3, c.z, 50, 2.0, 0.2, 2.0, 0.15);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 2, 1.0, 0.1, 1.0, 0);
		sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.6f);
		sound(level, c, SoundEvents.SLIME_BLOCK_FALL, 1.5f, 0.6f);
	}

	// ---- Catapult

	private static void catapult(ServerPlayer p) {
		Vec3 target = AbilityHelpers.aimPoint(p, GreenLanternConfig.CATAPULT_RANGE);
		if (!pay(p, GreenLanternConfig.CATAPULT_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 side = new Vec3(-Mth.cos(p.getYRot() * Mth.DEG_TO_RAD), 0, -Mth.sin(p.getYRot() * Mth.DEG_TO_RAD));
		Vec3 base = p.position().add(side.scale(1.8));
		HardLightConstructEntity cat = HardLightConstructEntity.create(level, Shape.CATAPULT, p.getUUID(), base, 1.0f, 34);
		cat.setYRot(p.getYRot());
		level.addFreshEntity(cat);
		Vec3 start = base.add(0, 2.6, 0);
		double t = 22.0;
		double g = 0.08;
		Vec3 d = target.subtract(start);
		Vec3 v = new Vec3(d.x / t, (d.y + 0.5 * g * t * t) / t, d.z / t);
		HardLightConstructEntity rock = HardLightConstructEntity.create(level, Shape.BOULDER, p.getUUID(), start, 1.0f, 120);
		rock.velocity = v;
		rock.damage = GreenLanternConfig.CATAPULT_DAMAGE * GreenLanternRanks.power(p);
		rock.phase = 4; // flies once the arm has swung (4 ticks)
		level.addFreshEntity(rock);
		constructFx(p);
		sound(level, base, SoundEvents.CROSSBOW_SHOOT, 1.2f, 0.5f);
	}

	private static void tickBoulder(HardLightConstructEntity e, ServerPlayer owner) {
		if (e.phase > 0) {
			e.phase--;
			return;
		}
		ServerLevel level = (ServerLevel) e.level();
		Vec3 pos = e.position();
		e.velocity = e.velocity.add(0, -0.08, 0);
		Vec3 next = pos.add(e.velocity);
		BlockHitResult bh = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
		LivingEntity struck = GreenLanternConstructAttacks.firstAlong(level, owner, pos, next, 1.0, e.hit);
		Vec3 end = bh.getType() == HitResult.Type.MISS ? next : bh.getLocation();
		e.setPos(end.x, end.y, end.z);
		e.face(e.velocity);
		if (bh.getType() == HitResult.Type.MISS && struck == null) {
			return;
		}
		Vec3 at = struck != null ? struck.position() : end;
		for (LivingEntity t : AbilityHelpers.living(level, at.add(0, 0.5, 0), GreenLanternConfig.CATAPULT_RADIUS, x -> canHit(owner, x))) {
			hit(owner, t, e.damage);
			AbilityHelpers.knockbackFrom(t, at, 0.9);
		}
		level.sendParticles(SPARK, at.x, at.y + 0.5, at.z, 60, 1.5, 0.6, 1.5, 0.25);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 3, 1.0, 0.4, 1.0, 0);
		sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.2f);
		e.discard();
	}

	// ---- Emerald Tentacles

	private static void tentacles(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		List<LivingEntity> foes = AbilityHelpers.living(level, p.position(), GreenLanternConfig.TENTACLES_RADIUS,
				x -> GreenLanternConstructs.isHostileTarget(p, x));
		if (foes.isEmpty()) {
			GreenLanternEnergy.feedback(p, "message.projecthero.green_lantern.no_targets");
			return;
		}
		if (!pay(p, GreenLanternConfig.TENTACLES_COST)) {
			return;
		}
		foes.sort(Comparator.comparingDouble(x -> x.distanceToSqr(p)));
		float dmg = GreenLanternConfig.TENTACLES_DAMAGE * GreenLanternRanks.power(p);
		for (LivingEntity foe : foes.subList(0, Math.min(6, foes.size()))) {
			Vec3 base = foe.position();
			HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.TENTACLE, p.getUUID(), base, 1.0f,
					GreenLanternConfig.TENTACLES_HOLD_TICKS + 14);
			e.setTargetId(foe.getId());
			e.homingId = foe.getId();
			e.damage = dmg;
			e.setYRot(level.random.nextFloat() * 360f);
			level.addFreshEntity(e);
		}
		constructFx(p);
		sound(level, p.position(), SoundEvents.SCULK_BLOCK_SPREAD, 1.5f, 0.6f);
	}

	private static void tickTentacle(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		Entity t = level.getEntity(e.homingId);
		if (!(t instanceof LivingEntity foe) || !foe.isAlive()) {
			if (e.phase == 0) {
				e.setLife(e.tickCount + 6);
				e.phase = 2;
			}
			return;
		}
		Vec3 base = e.position();
		int hold = GreenLanternConfig.TENTACLES_HOLD_TICKS;
		if (e.tickCount < hold) {
			// up and held: the foe is lifted to ~2.5 blocks over the tentacle's root
			double lift = Math.min(2.5, e.tickCount * 0.25);
			foe.teleportTo(base.x, base.y + lift, base.z);
			foe.setDeltaMovement(Vec3.ZERO);
			foe.resetFallDistance();
		} else if (e.phase == 0) {
			e.phase = 1;
			foe.teleportTo(base.x, base.y + 0.1, base.z);
			hit(owner, foe, e.damage);
			foe.setDeltaMovement(0, -0.5, 0);
			foe.hurtMarked = true;
			e.markAction();
			level.sendParticles(SPARK, base.x, base.y + 0.3, base.z, 24, 0.8, 0.2, 0.8, 0.15);
			sound(level, base, SoundEvents.SLIME_BLOCK_FALL, 1.2f, 0.7f);
		}
	}

	// ---- Wrecking Ball

	private static void wreckingBall(ServerPlayer p) {
		if (first(p.getUUID(), Shape.WRECKING_BALL) != null || !pay(p, GreenLanternConfig.WRECKING_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.WRECKING_BALL, p.getUUID(), p.position(), 1.0f,
				GreenLanternConfig.WRECKING_TICKS);
		e.setYRot(p.getYRot());
		e.damage = GreenLanternConfig.WRECKING_DAMAGE * GreenLanternRanks.power(p);
		level.addFreshEntity(e);
		keep(p, e);
		constructFx(p);
		sound(level, p.position(), SoundEvents.CHAIN_PLACE, 1.2f, 0.6f);
	}

	/** Where the ball is {@code age} ticks into its swing (shared with the renderer's maths). */
	public static double wreckingAngle(float yaw, float age) {
		return Math.toRadians(yaw + 90.0) + age * 0.42;
	}

	private static void tickWrecking(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		Vec3 c = owner.position();
		e.setPos(c.x, c.y, c.z);
		double a = wreckingAngle(e.getYRot(), e.tickCount);
		Vec3 ball = c.add(Math.cos(a) * GreenLanternConfig.WRECKING_RADIUS, 1.2, Math.sin(a) * GreenLanternConfig.WRECKING_RADIUS);
		for (LivingEntity t : AbilityHelpers.living(level, ball, 1.5, x -> canHit(owner, x) && !e.hit.contains(x.getId()))) {
			e.hit.add(t.getId());
			hit(owner, t, e.damage);
			Vec3 out = new Vec3(t.getX() - c.x, 0, t.getZ() - c.z);
			out = out.lengthSqr() < 1.0e-4 ? owner.getLookAngle() : out.normalize();
			push(t, out.scale(1.8).add(0, 0.6, 0));
			sound(level, ball, SoundEvents.ANVIL_LAND, 0.6f, 1.4f);
		}
		if (e.tickCount % 6 == 0) {
			sound(level, ball, SoundEvents.CHAIN_STEP, 0.8f, 0.7f);
		}
	}

	// ---- Bulldozer

	private static void bulldozer(ServerPlayer p) {
		if (!pay(p, GreenLanternConfig.DOZER_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 dir = flatLook(p);
		Vec3 start = p.position().add(dir.scale(1.5));
		int life = (int) (GreenLanternConfig.DOZER_DISTANCE / GreenLanternConfig.DOZER_SPEED_PER_TICK) + 12;
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.BULLDOZER, p.getUUID(), start, 1.0f, life);
		e.face(dir);
		e.velocity = dir;
		e.damage = GreenLanternConfig.DOZER_DAMAGE * GreenLanternRanks.power(p);
		level.addFreshEntity(e);
		constructFx(p);
		sound(level, start, SoundEvents.IRON_GOLEM_STEP, 1.5f, 0.5f);
	}

	private static void tickBulldozer(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		Vec3 pos = e.position();
		boolean driving = e.phase == 0 && e.travelled < GreenLanternConfig.DOZER_DISTANCE;
		if (driving) {
			Vec3 next = pos.add(e.velocity.scale(GreenLanternConfig.DOZER_SPEED_PER_TICK));
			BlockHitResult bh = level.clip(new ClipContext(pos.add(0, 0.6, 0), next.add(0, 0.6, 0), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, e));
			if (bh.getType() != HitResult.Type.MISS) {
				driving = false;
			} else {
				e.setPos(next.x, next.y, next.z);
				e.travelled += GreenLanternConfig.DOZER_SPEED_PER_TICK;
				pos = next;
			}
		}
		Vec3 blade = pos.add(e.velocity.scale(1.6));
		for (LivingEntity t : AbilityHelpers.living(level, blade.add(0, 0.8, 0), 1.8, x -> canHit(owner, x))) {
			if (e.hit.add(t.getId())) {
				hit(owner, t, e.damage);
			}
			if (driving) {
				Vec3 at = blade.add(e.velocity.scale(0.6));
				t.teleportTo(at.x, Math.max(t.getY(), pos.y), at.z);
				t.setDeltaMovement(e.velocity.scale(GreenLanternConfig.DOZER_SPEED_PER_TICK));
			}
		}
		if (!driving && e.phase == 0) {
			// the end of the run: the blade tips up and dumps everything it was shoving
			e.phase = 1;
			e.markAction();
			e.setLife(e.tickCount + 10);
			for (LivingEntity t : AbilityHelpers.living(level, blade.add(0, 0.8, 0), 2.6, x -> canHit(owner, x))) {
				push(t, e.velocity.scale(0.8).add(0, 0.9, 0));
			}
			sound(level, blade, SoundEvents.ANVIL_PLACE, 0.8f, 0.6f);
		}
		if (driving && e.tickCount % 4 == 0) {
			level.sendParticles(GREEN_DUST, blade.x, pos.y + 0.1, blade.z, 4, 0.8, 0.05, 0.8, 0.02);
			sound(level, pos, SoundEvents.MINECART_RIDING, 0.3f, 0.6f);
		}
	}

	// ---- Bear Trap

	private static void bearTrap(ServerPlayer p) {
		if (liveCount(p.getUUID(), Shape.BEAR_TRAP) >= GreenLanternConfig.TRAP_MAX_LIVE) {
			GreenLanternEnergy.feedback(p, "message.projecthero.green_lantern.trap_limit");
			return;
		}
		BlockHitResult bh = AbilityHelpers.raycastBlock(p, GreenLanternConfig.TRAP_RANGE);
		Vec3 spot = bh.getType() == HitResult.Type.MISS ? null : groundUnder(p, bh.getLocation().add(0, 0.1, 0));
		if (spot == null) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.invalid_target");
			return;
		}
		if (!pay(p, GreenLanternConfig.TRAP_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.BEAR_TRAP, p.getUUID(), spot, 1.0f, 0);
		e.setYRot(p.getYRot());
		e.damage = GreenLanternConfig.TRAP_DAMAGE * GreenLanternRanks.power(p);
		level.addFreshEntity(e);
		keep(p, e);
		constructFx(p);
		sound(level, spot, SoundEvents.IRON_TRAPDOOR_CLOSE, 0.8f, 0.6f);
	}

	private static void tickTrap(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		Vec3 c = e.position();
		if (e.phase == 0) {
			for (LivingEntity t : AbilityHelpers.living(level, c.add(0, 0.4, 0), 0.9, x -> canHit(owner, x))) {
				e.phase = 1;
				e.homingId = t.getId();
				e.setTargetId(t.getId());
				e.nextHitTick = e.tickCount + GreenLanternConfig.TRAP_ROOT_TICKS;
				e.markAction();
				GreenLanternCombos.mark(owner, t, GreenLanternCombos.Move.BEAR_TRAP); // v0.17.1: opens Bullseye
				hit(owner, t, e.damage);
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, GreenLanternConfig.TRAP_ROOT_TICKS, 9);
				level.sendParticles(SPARK, c.x, c.y + 0.3, c.z, 18, 0.3, 0.2, 0.3, 0.15);
				sound(level, c, SoundEvents.IRON_TRAPDOOR_CLOSE, 1.4f, 1.6f);
				sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
				break;
			}
			return;
		}
		Entity t = level.getEntity(e.homingId);
		if (e.tickCount >= e.nextHitTick || !(t instanceof LivingEntity foe) || !foe.isAlive()) {
			dissolve(e);
			e.discard();
			return;
		}
		foe.teleportTo(c.x, c.y, c.z);
		foe.setDeltaMovement(Vec3.ZERO);
		if (e.tickCount % 10 == 0) {
			GreenLanternCombos.mark(owner, foe, GreenLanternCombos.Move.BEAR_TRAP); // v0.17.1: rooted = Bullseye open
		}
	}

	// ---- Grappling Claw

	private static void grapplingClaw(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		LivingEntity mob = AbilityHelpers.raycastEntity(p, GreenLanternConfig.CLAW_RANGE);
		Vec3 from = hand(p);
		Vec3 to;
		if (mob != null && canHit(p, mob)) {
			if (!pay(p, GreenLanternConfig.CLAW_COST)) {
				return;
			}
			to = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
			Vec3 pull = p.position().subtract(mob.position());
			double dist = pull.length();
			push(mob, pull.normalize().scale(Math.min(2.4, 0.35 + dist * 0.16)).add(0, 0.35, 0));
		} else {
			BlockHitResult bh = AbilityHelpers.raycastBlock(p, GreenLanternConfig.CLAW_RANGE);
			if (bh.getType() == HitResult.Type.MISS) {
				GreenLanternEnergy.feedback(p, "message.projecthero.ability.invalid_target");
				return;
			}
			if (!pay(p, GreenLanternConfig.CLAW_COST)) {
				return;
			}
			to = bh.getLocation();
			Vec3 pull = to.subtract(p.position());
			double dist = pull.length();
			AbilityHelpers.launchSelf(p, pull.normalize().scale(Math.min(3.0, 0.5 + dist * 0.14)).add(0, 0.4, 0));
			p.resetFallDistance();
		}
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.CLAW, p.getUUID(), from, 1.0f, 12);
		e.setEnd(to);
		level.addFreshEntity(e);
		constructFx(p);
		sound(level, from, SoundEvents.CROSSBOW_SHOOT, 1.0f, 1.4f);
		sound(level, to, SoundEvents.CHAIN_HIT, 1.0f, 1.0f);
	}

	// ---- Fighter Jet

	private static void fighterJet(ServerPlayer p) {
		if (first(p.getUUID(), Shape.JET) != null) {
			GreenLanternEnergy.feedback(p, "message.projecthero.green_lantern.already_out");
			return;
		}
		Vec3 aim = AbilityHelpers.aimPoint(p, 48.0);
		Vec3 ground = groundUnder(p, aim);
		Vec3 centre = ground != null ? ground : aim;
		if (!pay(p, GreenLanternConfig.JET_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.JET, p.getUUID(), centre.add(0, 10, 0), 1.0f,
				GreenLanternConfig.JET_TICKS);
		e.origin = centre;
		e.damage = GreenLanternConfig.JET_DAMAGE * GreenLanternRanks.power(p);
		level.addFreshEntity(e);
		keep(p, e);
		constructFx(p);
		sound(level, e.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 2.0f, 0.5f);
	}

	private static void tickJet(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		double a = e.tickCount * 0.12;
		Vec3 c = e.origin;
		Vec3 pos = c.add(Math.cos(a) * GreenLanternConfig.JET_RADIUS, 10.0, Math.sin(a) * GreenLanternConfig.JET_RADIUS);
		Vec3 tangent = new Vec3(-Math.sin(a), 0, Math.cos(a));
		e.setPos(pos.x, pos.y, pos.z);
		e.face(tangent);
		if (e.tickCount % 8 == 0) {
			LivingEntity t = AbilityHelpers.living(level, c, GreenLanternConfig.JET_RADIUS + 4.0,
					x -> GreenLanternConstructs.isHostileTarget(owner, x)).stream()
					.min(Comparator.comparingDouble(x -> x.distanceToSqr(pos))).orElse(null);
			if (t != null) {
				Vec3 at = t.position().add(0, t.getBbHeight() * 0.5, 0);
				GreenLanternConstructAttacks.boltStreak(owner, pos, at, 0.25f, 5);
				hit(owner, t, e.damage);
				sound(level, pos, SoundEvents.FIREWORK_ROCKET_BLAST, 0.8f, 1.6f);
			}
		}
		if (e.tickCount % 20 == 0) {
			sound(level, pos, SoundEvents.ELYTRA_FLYING, 0.4f, 1.5f);
		}
	}

	// ---- Emerald Dragon

	private static void dragon(ServerPlayer p) {
		if (first(p.getUUID(), Shape.DRAGON) != null) {
			GreenLanternEnergy.feedback(p, "message.projecthero.green_lantern.already_out");
			return;
		}
		if (!pay(p, GreenLanternConfig.DRAGON_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.DRAGON, p.getUUID(), dragonPerch(p), 1.0f,
				GreenLanternConfig.DRAGON_TICKS);
		e.setYRot(p.getYRot());
		e.damage = GreenLanternConfig.DRAGON_DAMAGE * GreenLanternRanks.power(p);
		level.addFreshEntity(e);
		keep(p, e);
		constructFx(p);
		sound(level, e.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 1.6f);
	}

	private static Vec3 dragonPerch(ServerPlayer p) {
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		// up and ahead on the right shoulder, where the Lantern (and their camera) can see it
		return p.position().add(right.scale(2.0)).add(fwd.scale(1.5)).add(0, 2.8, 0);
	}

	private static void tickDragon(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		Vec3 perch = dragonPerch(owner).add(0, Math.sin(e.tickCount * 0.15) * 0.3, 0);
		Vec3 pos = e.position().lerp(perch, 0.25);
		e.setPos(pos.x, pos.y, pos.z);
		LivingEntity t = AbilityHelpers.living(level, pos, GreenLanternConfig.DRAGON_RANGE,
				x -> GreenLanternConstructs.isHostileTarget(owner, x) && GreenLanternConstructs.canSee(level, pos, x, owner)).stream()
				.min(Comparator.comparingDouble(x -> x.distanceToSqr(pos))).orElse(null);
		if (t == null) {
			e.face(owner.getLookAngle().multiply(1, 0, 1));
			e.setTargetId(-1);
			return;
		}
		Vec3 at = t.position().add(0, t.getBbHeight() * 0.5, 0);
		Vec3 dir = at.subtract(pos).normalize();
		e.face(dir);
		e.setTargetId(t.getId());
		if (e.tickCount % 2 == 0) {
			for (int i = 1; i <= 6; i++) {
				Vec3 q = pos.add(dir.scale(i * 1.2));
				level.sendParticles(GREEN_DUST, q.x, q.y, q.z, 2, 0.15 * i, 0.15 * i, 0.15 * i, 0.01);
			}
		}
		if (e.tickCount % 10 == 0) {
			for (LivingEntity x : AbilityHelpers.living(level, pos, 8.0, y -> canHit(owner, y))) {
				Vec3 to = x.position().add(0, x.getBbHeight() * 0.5, 0).subtract(pos);
				if (to.normalize().dot(dir) > 0.86) {
					hit(owner, x, e.damage);
				}
			}
			e.markAction();
			sound(level, pos, SoundEvents.BLAZE_SHOOT, 0.5f, 1.4f);
		}
	}

	// ---- Emerald Mace

	public static boolean maceOn(Player p) {
		for (HardLightConstructEntity e : live(p.getUUID())) {
			if (!e.isRemoved() && e.shape() == Shape.MACE) {
				return true;
			}
		}
		return false;
	}

	private static void mace(ServerPlayer p) {
		HardLightConstructEntity out = first(p.getUUID(), Shape.MACE);
		if (out != null) {
			out.discard();
			maceModifiers(p, false);
			sound(p.serverLevel(), p.position(), SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.4f);
			return;
		}
		if (!pay(p, GreenLanternConfig.MACE_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.MACE, p.getUUID(), hand(p), 1.0f, 0);
		level.addFreshEntity(e);
		keep(p, e);
		maceModifiers(p, true);
		constructFx(p);
		sound(level, p.position(), SoundEvents.ANVIL_USE, 0.6f, 1.4f);
	}

	private static void maceModifiers(ServerPlayer p, boolean on) {
		if (on) {
			PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, MACE_DAMAGE_ID, GreenLanternConfig.MACE_DAMAGE - 1.0,
					AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(p, Attributes.ATTACK_SPEED, MACE_SPEED_ID, -0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, MACE_DAMAGE_ID);
			PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, MACE_SPEED_ID);
		}
	}

	/** A melee hit by a Lantern with the mace out: a small shockwave round the target. */
	public static void onMeleeHit(ServerPlayer p, LivingEntity target) {
		if (!maceOn(p)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 c = target.position();
		float dmg = GreenLanternConfig.MACE_SHOCKWAVE_DAMAGE * GreenLanternRanks.power(p);
		for (LivingEntity t : AbilityHelpers.living(level, c.add(0, 0.5, 0), GreenLanternConfig.MACE_SHOCKWAVE_RADIUS,
				x -> x != target && canHit(p, x))) {
			hit(p, t, dmg);
			AbilityHelpers.knockbackFrom(t, c, 0.5);
		}
		ring(level, c, GreenLanternConfig.MACE_SHOCKWAVE_RADIUS);
		sound(level, c, SoundEvents.MACE_SMASH_GROUND, 0.8f, 1.2f);
	}

	private static void tickMace(HardLightConstructEntity e, ServerPlayer owner) {
		Vec3 h = hand(owner);
		e.setPos(h.x, h.y, h.z);
		e.setYRot(owner.getYRot());
		e.setXRot(owner.getXRot());
	}

	// ---- Med Bay

	private static void medBay(ServerPlayer p) {
		Vec3 spot = groundUnder(p, p.position().add(0, 0.5, 0));
		if (!pay(p, GreenLanternConfig.MEDBAY_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 at = spot != null ? spot : p.position();
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.MED_BAY, p.getUUID(), at, 1.0f,
				GreenLanternConfig.MEDBAY_TICKS);
		e.setYRot(p.getYRot());
		level.addFreshEntity(e);
		keep(p, e);
		constructFx(p);
		sound(level, at, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.6f);
	}

	private static void tickMedBay(HardLightConstructEntity e, ServerPlayer owner) {
		if (e.tickCount % 20 != 0) {
			return;
		}
		ServerLevel level = (ServerLevel) e.level();
		Vec3 c = e.position();
		for (Player pl : level.getEntitiesOfClass(Player.class, e.getBoundingBox().inflate(GreenLanternConfig.MEDBAY_RADIUS),
				x -> x.distanceToSqr(c) <= GreenLanternConfig.MEDBAY_RADIUS * GreenLanternConfig.MEDBAY_RADIUS
						&& (x == owner || com.projecthero.mod.squad.Squads.areAllies(owner, x)))) {
			pl.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, true, true));
			level.sendParticles(ParticleTypes.HEART, pl.getX(), pl.getY() + 2.1, pl.getZ(), 1, 0.2, 0.1, 0.2, 0);
		}
		level.sendParticles(GREEN_DUST, c.x, c.y + 0.2, c.z, 10, GreenLanternConfig.MEDBAY_RADIUS * 0.5, 0.05,
				GreenLanternConfig.MEDBAY_RADIUS * 0.5, 0.0);
	}

	// ---------------------------------------------------------------- hold constructs (Cannon, Bow, Mirror Shield)

	public static boolean holding(Player p, ConstructType type) {
		Hold h = HOLDS.get(p.getUUID());
		return h != null && h.type() == type;
	}

	public static void holdStart(ServerPlayer p, ConstructType type) {
		if (HOLDS.containsKey(p.getUUID())) {
			return;
		}
		if (type == ConstructType.MIRROR_SHIELD && !pay(p, GreenLanternConfig.MIRROR_COST)) {
			return;
		}
		if (type != ConstructType.MIRROR_SHIELD && !GreenLanternEnergy.canSpend(p, type == ConstructType.EMERALD_CANNON
				? GreenLanternConfig.CANNON_COST_MIN : GreenLanternConfig.BOW_COST)) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			return;
		}
		ServerLevel level = p.serverLevel();
		Shape shape = switch (type) {
			case EMERALD_CANNON -> Shape.CANNON;
			case EMERALD_BOW -> Shape.BOW;
			default -> Shape.MIRROR;
		};
		HardLightConstructEntity model = HardLightConstructEntity.create(level, shape, p.getUUID(), hand(p), 1.0f, 0);
		level.addFreshEntity(model);
		HOLDS.put(p.getUUID(), new Hold(type, level.getGameTime(), model));
		GreenLanternVisuals.anim(p, GreenLanternFx.ANIM_CONSTRUCT);
		sound(level, p.position(), type == ConstructType.EMERALD_BOW ? SoundEvents.CROSSBOW_LOADING_START.value()
				: SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.4f);
	}

	public static void holdRelease(ServerPlayer p, ConstructType type) {
		Hold h = HOLDS.get(p.getUUID());
		if (h == null || h.type() != type) {
			return;
		}
		HOLDS.remove(p.getUUID());
		if (h.model() != null) {
			h.model().discard();
		}
		long held = p.level().getGameTime() - h.since();
		switch (type) {
			case EMERALD_CANNON -> fireCannon(p, Mth.clamp(held / (float) GreenLanternConfig.CANNON_FULL_CHARGE_TICKS, 0.15f, 1f));
			case EMERALD_BOW -> fireBow(p, Mth.clamp(held / (float) GreenLanternConfig.BOW_DRAW_TICKS, 0.3f, 1f));
			default -> sound(p.serverLevel(), p.position(), SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.6f);
		}
	}

	/** Shared with the HUD: how charged a held Cannon / drawn Bow is, 0..1 (0 when not holding one). */
	public static float holdCharge(ServerPlayer p) {
		Hold h = HOLDS.get(p.getUUID());
		if (h == null || h.type() == ConstructType.MIRROR_SHIELD) {
			return 0f;
		}
		int full = h.type() == ConstructType.EMERALD_CANNON ? GreenLanternConfig.CANNON_FULL_CHARGE_TICKS : GreenLanternConfig.BOW_DRAW_TICKS;
		return Mth.clamp((p.level().getGameTime() - h.since()) / (float) full, 0f, 1f);
	}

	private static void fireCannon(ServerPlayer p, float charge) {
		float cost = Mth.lerp(charge, GreenLanternConfig.CANNON_COST_MIN, GreenLanternConfig.CANNON_COST_MAX);
		if (!pay(p, cost)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = clipTo(level, eye, eye.add(look.scale(GreenLanternConfig.CANNON_RANGE)), p);
		float dmg = Mth.lerp(charge, GreenLanternConfig.CANNON_DAMAGE_MIN, GreenLanternConfig.CANNON_DAMAGE_MAX) * GreenLanternRanks.power(p);
		List<LivingEntity> line = alongLine(level, p, eye, end, 0.9);
		int n = 0;
		for (LivingEntity t : line) {
			if (n++ >= GreenLanternConfig.CANNON_PIERCE) {
				end = t.position().add(0, t.getBbHeight() * 0.5, 0);
				break;
			}
			hit(p, t, dmg);
			push(t, look.scale(0.6 + charge).add(0, 0.25, 0));
		}
		HardLightConstructEntity shot = HardLightConstructEntity.create(level, Shape.CANNON_SHOT, p.getUUID(), hand(p),
				0.5f + charge, 10);
		shot.setEnd(end);
		level.addFreshEntity(shot);
		level.sendParticles(SPARK, end.x, end.y, end.z, (int) (20 + 30 * charge), 0.5, 0.5, 0.5, 0.2);
		sound(level, p.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.6f + charge * 0.6f, 1.8f - charge * 0.6f);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(look.scale(-0.25 * charge)));
	}

	private static void fireBow(ServerPlayer p, float draw) {
		if (!pay(p, GreenLanternConfig.BOW_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 from = hand(p);
		float dmg = GreenLanternConfig.BOW_ARROW_DAMAGE * GreenLanternRanks.power(p);
		int n = GreenLanternConfig.BOW_ARROWS;
		for (int i = 0; i < n; i++) {
			float off = (i - (n - 1) / 2f) * 7f;
			Vec3 dir = Vec3.directionFromRotation(p.getXRot(), p.getYRot() + off);
			HardLightConstructEntity a = HardLightConstructEntity.create(level, Shape.ARROW, p.getUUID(), from, 1.0f, 30);
			a.velocity = dir.scale(1.6 + 1.4 * draw);
			a.damage = dmg;
			a.face(dir);
			level.addFreshEntity(a);
		}
		sound(level, from, SoundEvents.CROSSBOW_SHOOT, 1.0f, 1.2f);
		sound(level, from, SoundEvents.ARROW_SHOOT, 1.0f, 1.4f);
	}

	private static void tickArrow(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		Vec3 pos = e.position();
		Vec3 next = pos.add(e.velocity);
		BlockHitResult bh = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
		Vec3 end = bh.getType() == HitResult.Type.MISS ? next : bh.getLocation();
		LivingEntity struck = GreenLanternConstructAttacks.firstAlong(level, owner, pos, end, 0.5, e.hit);
		if (struck != null) {
			hit(owner, struck, e.damage);
			Vec3 at = struck.position().add(0, struck.getBbHeight() * 0.5, 0);
			level.sendParticles(SPARK, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.1);
			e.discard();
			return;
		}
		e.setPos(end.x, end.y, end.z);
		if (bh.getType() != HitResult.Type.MISS) {
			level.sendParticles(SPARK, end.x, end.y, end.z, 4, 0.1, 0.1, 0.1, 0.05);
			e.discard();
		}
	}

	private static void tickHeldModel(HardLightConstructEntity e, ServerPlayer owner) {
		Hold h = HOLDS.get(owner.getUUID());
		if (h == null || h.model() != e) {
			e.discard();
			return;
		}
		Vec3 at = e.shape() == Shape.MIRROR ? owner.getEyePosition().add(owner.getLookAngle().scale(1.3)).add(0, -0.4, 0) : hand(owner);
		e.setPos(at.x, at.y, at.z);
		e.setYRot(owner.getYRot());
		e.setXRot(owner.getXRot());
		if (e.shape() == Shape.MIRROR) {
			reflect(owner);
		} else if (e.shape() == Shape.CANNON && e.tickCount % 4 == 0) {
			((ServerLevel) e.level()).sendParticles(GREEN_DUST, at.x, at.y, at.z, 2, 0.15, 0.15, 0.15, 0.01);
		}
	}

	/** Mirror Shield: anything flying at you from the front goes straight back to whoever threw it. */
	private static void reflect(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 look = p.getLookAngle();
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.5))) {
			if (proj.getOwner() == p) {
				continue;
			}
			Vec3 v = proj.getDeltaMovement();
			Vec3 toMe = p.getEyePosition().subtract(proj.position());
			if (v.lengthSqr() < 1.0e-4 || v.dot(toMe) <= 0 || toMe.normalize().dot(look) > -0.2) {
				continue; // standing still, flying away, or coming from behind
			}
			Entity shooter = proj.getOwner();
			Vec3 back = shooter != null ? shooter.getEyePosition().subtract(proj.position()).normalize().scale(v.length() * 1.2)
					: v.scale(-1.2);
			proj.setDeltaMovement(back);
			if (proj instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile hurting) {
				hurting.accelerationPower = Math.abs(hurting.accelerationPower);
			}
			proj.setOwner(p);
			proj.hurtMarked = true;
			level.sendParticles(SPARK, proj.getX(), proj.getY(), proj.getZ(), 10, 0.2, 0.2, 0.2, 0.15);
			sound(level, proj.position(), SoundEvents.SHIELD_BLOCK, 1.0f, 1.6f);
		}
	}

	// ---------------------------------------------------------------- ticks

	/** Per player per tick: upkeeps, and the mace's attribute guard. */
	public static void tick(ServerPlayer p) {
		Hold h = HOLDS.get(p.getUUID());
		if (h != null && h.type() == ConstructType.MIRROR_SHIELD
				&& !GreenLanternEnergy.drainTick(p, GreenLanternConfig.MIRROR_UPKEEP_PER_SEC / 20f)) {
			holdRelease(p, ConstructType.MIRROR_SHIELD);
		}
		List<HardLightConstructEntity> list = LIVE.get(p.getUUID());
		if (list != null) {
			list.removeIf(Entity::isRemoved);
			for (HardLightConstructEntity e : list) {
				float upkeep = switch (e.shape()) {
					case JET -> GreenLanternConfig.JET_UPKEEP_PER_SEC;
					case DRAGON -> GreenLanternConfig.DRAGON_UPKEEP_PER_SEC;
					case MACE -> GreenLanternConfig.MACE_UPKEEP_PER_SEC;
					case MED_BAY -> GreenLanternConfig.MEDBAY_UPKEEP_PER_SEC;
					default -> 0f;
				};
				if (upkeep > 0f && !GreenLanternEnergy.drainTick(p, upkeep / 20f)) {
					dissolve(e);
					e.discard();
				}
			}
		}
		if (!maceOn(p) && p.getAttribute(Attributes.ATTACK_DAMAGE) != null
				&& p.getAttribute(Attributes.ATTACK_DAMAGE).hasModifier(MACE_DAMAGE_ID)) {
			maceModifiers(p, false);
		}
	}

	/** From {@code GreenLanternConstructAttacks#tickEntity} for the arsenal's shapes. */
	public static void tickEntity(HardLightConstructEntity e, ServerPlayer owner) {
		switch (e.shape()) {
			case SWATTER -> tickSwatter(e, owner);
			case BOULDER -> tickBoulder(e, owner);
			case TENTACLE -> tickTentacle(e, owner);
			case WRECKING_BALL -> tickWrecking(e, owner);
			case BULLDOZER -> tickBulldozer(e, owner);
			case BEAR_TRAP -> tickTrap(e, owner);
			case JET -> tickJet(e, owner);
			case DRAGON -> tickDragon(e, owner);
			case MACE -> tickMace(e, owner);
			case MED_BAY -> tickMedBay(e, owner);
			case ARROW -> tickArrow(e, owner);
			case CANNON, BOW, MIRROR -> tickHeldModel(e, owner);
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	private static boolean pay(ServerPlayer p, float cost) {
		if (GreenLanternEnergy.spend(p, cost)) {
			GreenLanternBattery.onAbilityUsed(p);
			return true;
		}
		GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
		return false;
	}

	private static boolean canHit(ServerPlayer owner, LivingEntity e) {
		return GreenLanternConstructAttacks.canHit(owner, e);
	}

	private static void hit(ServerPlayer owner, LivingEntity t, float dmg) {
		AbilityHelpers.hurtBurst(owner, t, dmg);
	}

	private static void push(LivingEntity t, Vec3 v) {
		if (com.projecthero.mod.titanshifter.TitanCombat.isBoss(t)) {
			v = v.scale(0.2);
		}
		AbilityHelpers.push(t, v);
	}

	private static Vec3 hand(ServerPlayer p) {
		return AbilityHelpers.handPosition(p);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	private static Vec3 clipTo(ServerLevel level, Vec3 from, Vec3 to, Entity source) {
		BlockHitResult bh = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
		return bh.getType() == HitResult.Type.MISS ? to : bh.getLocation();
	}

	/** The top of the ground at or below {@code at} (up to 24 blocks down), or null over a void. */
	private static Vec3 groundUnder(ServerPlayer p, Vec3 at) {
		BlockHitResult down = p.serverLevel().clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -24, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		return down.getType() == HitResult.Type.MISS ? null : down.getLocation();
	}

	/** Every creature the owner may hit within {@code radius} of the segment, nearest first. */
	private static List<LivingEntity> alongLine(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to, double radius) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		List<LivingEntity> out = new ArrayList<>();
		if (len < 1.0e-3) {
			return out;
		}
		Vec3 dir = d.scale(1.0 / len);
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(from, to).inflate(radius + 1.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> canHit(owner, x))) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			double t = Mth.clamp(c.subtract(from).dot(dir), 0, len);
			Vec3 closest = from.add(dir.scale(t));
			double reach = radius + e.getBbWidth() * 0.5;
			if (c.distanceToSqr(closest) <= reach * reach) {
				out.add(e);
			}
		}
		out.sort(Comparator.comparingDouble(x -> x.distanceToSqr(from)));
		return out;
	}

	private static void constructFx(ServerPlayer p) {
		GreenLanternVisuals.anim(p, GreenLanternFx.ANIM_CONSTRUCT);
		Vec3 h = hand(p);
		p.serverLevel().sendParticles(GREEN_DUST, h.x, h.y, h.z, 12, 0.15, 0.15, 0.15, 0.02);
	}

	private static void ring(ServerLevel level, Vec3 c, double r) {
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 12;
			level.sendParticles(GREEN_DUST, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	private static void dissolve(HardLightConstructEntity e) {
		if (e.level() instanceof ServerLevel level) {
			level.sendParticles(SPARK, e.getX(), e.getY() + 0.5, e.getZ(), 14, 0.4, 0.4, 0.4, 0.05);
		}
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}
}
