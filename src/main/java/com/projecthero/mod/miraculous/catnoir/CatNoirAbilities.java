package com.projecthero.mod.miraculous.catnoir;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.catnoir.entity.CatBatonEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: Cat Noir's moves (everything but CATACLYSM, see {@link CatNoirCataclysm}). Each is gated by {@link #begin}
 * (transformed and done transforming, off cooldown); the timed ones run as small per-player sessions ticked from
 * {@link #tick}, landing their hits on the frames the client's poses swing on ({@code CatNoirPose}, same game-time clock).
 * Every hit goes through {@link CatNoirCombat#strike}.
 */
public final class CatNoirAbilities {
	private static final ResourceLocation ROOFTOP_SPEED_ID = PowerToggles.id("cat_noir_rooftop_sprint");

	private record Timed(long start) {
	}

	private record Extend(long start, int hitTick, int targetId, @Nullable Vec3 blockHit) {
	}

	private static final class Spin {
		final long start;
		final Map<Integer, Long> lastHit = new HashMap<>();

		Spin(long start) {
			this.start = start;
		}
	}

	private record Pounce(long start, int targetId) {
	}

	private static final Map<UUID, Timed> STRIKES = new ConcurrentHashMap<>();
	private static final Map<UUID, Timed> SWEEPS = new ConcurrentHashMap<>();
	private static final Map<UUID, Extend> EXTENDS = new ConcurrentHashMap<>();
	private static final Map<UUID, Timed> VAULTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Spin> SPINS = new ConcurrentHashMap<>();
	private static final Map<UUID, Pounce> POUNCES = new ConcurrentHashMap<>();

	private CatNoirAbilities() {
	}

	static void clearSessionState() {
		STRIKES.clear();
		SWEEPS.clear();
		EXTENDS.clear();
		VAULTS.clear();
		SPINS.clear();
		POUNCES.clear();
	}

	static void forget(UUID id) {
		STRIKES.remove(id);
		SWEEPS.remove(id);
		EXTENDS.remove(id);
		VAULTS.remove(id);
		SPINS.remove(id);
		POUNCES.remove(id);
	}

	/** Is this move's session running (tests, the HUD)? */
	public static boolean running(ServerPlayer p, String id) {
		UUID u = p.getUUID();
		return switch (id) {
			case CatNoir.STRIKE -> STRIKES.containsKey(u);
			case CatNoir.SWEEP -> SWEEPS.containsKey(u);
			case CatNoir.EXTEND -> EXTENDS.containsKey(u);
			case CatNoir.VAULT -> VAULTS.containsKey(u);
			case CatNoir.SPIN -> SPINS.containsKey(u);
			case CatNoir.POUNCE -> POUNCES.containsKey(u);
			case CatNoir.THROW -> CatNoir.thrownId(p) >= 0;
			case CatNoir.ROOFTOP -> CatNoir.rooftop(p);
			case CatNoir.NIGHT_VISION -> CatNoir.nightVision(p);
			case CatNoir.REFLEXES -> CatNoir.reflexesLeft(p) > 0;
			case CatNoir.CATACLYSM -> CatNoir.charged(p);
			default -> false;
		};
	}

	/** Ends every running move (detransformation, death, logout): the thrown baton is called home, the effects come off. */
	static void end(ServerPlayer p) {
		forget(p.getUUID());
		int thrown = CatNoir.thrownId(p);
		if (thrown >= 0 && p.level().getEntity(thrown) instanceof CatBatonEntity baton) {
			baton.discard();
		}
		if (CatNoir.nightVision(p)) {
			p.removeEffect(MobEffects.NIGHT_VISION);
		}
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, ROOFTOP_SPEED_ID);
	}

	// ---------------------------------------------------------------- the gate

	static boolean begin(ServerPlayer p, String id) {
		if (!Miraculous.canAct(p) || !CatNoir.active(p)) {
			return false;
		}
		int cd = Miraculous.cooldownRemaining(p, id);
		if (cd > 0) {
			Miraculous.say(p, "message.projecthero.cat_noir.cooldown", ChatFormatting.GRAY,
					Component.translatable("projecthero.miraculous.ability." + id), String.format(Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		return true;
	}

	static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	// ---------------------------------------------------------------- R: Baton Strike / Shift+R: Low Sweep

	public static void strike(ServerPlayer p) {
		if (!begin(p, CatNoir.STRIKE)) {
			return;
		}
		STRIKES.put(p.getUUID(), new Timed(now(p)));
		Miraculous.setAnim(p, CatNoir.ANIM_STRIKE);
		Miraculous.setCooldown(p, CatNoir.STRIKE, CatNoirConfig.STRIKE_COOLDOWN);
		sound(p, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.5f, 1.9f); // the baton snapping out to staff length
	}

	public static void sweep(ServerPlayer p) {
		if (!begin(p, CatNoir.SWEEP)) {
			return;
		}
		SWEEPS.put(p.getUUID(), new Timed(now(p)));
		Miraculous.setAnim(p, CatNoir.ANIM_SWEEP);
		Miraculous.setCooldown(p, CatNoir.SWEEP, CatNoirConfig.SWEEP_COOLDOWN);
		sound(p, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.5f, 1.9f);
	}

	private static void tickStrike(ServerPlayer p, Timed s, long age) {
		for (int i = 0; i < CatNoirConfig.STRIKE_HITS.length; i++) {
			if (age != CatNoirConfig.STRIKE_HITS[i]) {
				continue;
			}
			boolean finisher = i == CatNoirConfig.STRIKE_HITS.length - 1;
			p.swing(InteractionHand.MAIN_HAND, true);
			sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, finisher ? 0.8f : 1.2f + 0.15f * i);
			Vec3 front = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6)).add(0, 1.0, 0);
			level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z, 1, 0, 0, 0, 0);
			for (LivingEntity e : CatNoirCombat.cone(p, CatNoirConfig.STRIKE_REACH, CatNoirConfig.STRIKE_CONE)) {
				CatNoirCombat.strike(p, e, p.position(), CatNoirConfig.STRIKE_DAMAGE,
						finisher ? CatNoirConfig.STRIKE_FINISHER_KNOCKBACK : CatNoirConfig.STRIKE_KNOCKBACK, finisher ? 0.15 : 0.0);
			}
		}
	}

	private static void tickSweep(ServerPlayer p, long age) {
		if (age != CatNoirConfig.SWEEP_HIT_TICK) {
			return;
		}
		p.swing(InteractionHand.MAIN_HAND, true);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.7f);
		ServerLevel level = level(p);
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		for (int k = -4; k <= 4; k++) {
			double a = k * 0.35;
			Vec3 d = new Vec3(look.x * Math.cos(a) - look.z * Math.sin(a), 0, look.x * Math.sin(a) + look.z * Math.cos(a));
			Vec3 at = p.position().add(d.scale(2.2)).add(0, 0.2, 0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		for (LivingEntity e : CatNoirCombat.cone(p, CatNoirConfig.SWEEP_RADIUS, CatNoirConfig.SWEEP_CONE)) {
			// at the legs: only what stands about level with his feet
			if (e.getY() > p.getY() + 1.5 || e.getBoundingBox().maxY < p.getY() - 0.5) {
				continue;
			}
			if (CatNoirCombat.strike(p, e, p.position(), CatNoirConfig.SWEEP_DAMAGE, 0.25, 0.0)) {
				trip(e);
			}
		}
	}

	/** Swept off its feet: a pop into the air, its path cleared, Slowness IV. */
	static void trip(LivingEntity e) {
		AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, CatNoirConfig.SWEEP_SLOW_TICKS, CatNoirConfig.SWEEP_SLOW_AMPLIFIER);
		if (!CatNoirCombat.isBoss(e)) {
			AbilityHelpers.push(e, new Vec3(0, 0.3, 0));
		}
		if (e instanceof Mob m) {
			m.getNavigation().stop();
		}
	}

	// ---------------------------------------------------------------- G: Baton Extend / Shift+G: Baton Throw

	public static void extend(ServerPlayer p) {
		if (!begin(p, CatNoir.EXTEND)) {
			return;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 dir = p.getLookAngle();
		double reach = CatNoirConfig.EXTEND_RANGE;
		Vec3 blockHit = null;
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, eye.add(dir.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bhr.getType() != HitResult.Type.MISS) {
			reach = eye.distanceTo(bhr.getLocation());
			blockHit = bhr.getLocation();
		}
		LivingEntity target = AbilityHelpers.raycastEntity(p, reach);
		int targetId = -1;
		if (target != null && CatNoirCombat.isTarget(p, target)) {
			targetId = target.getId();
			reach = Math.min(reach, Math.sqrt(AbilityHelpers.distanceSqToBox(target, eye)) + 0.3);
			blockHit = null;
		}
		reach = Math.max(1.5, reach);
		int hitTick = Math.max(1, (int) Math.ceil(CatNoirConfig.EXTEND_OUT_TICKS * reach / CatNoirConfig.EXTEND_RANGE));
		EXTENDS.put(p.getUUID(), new Extend(now(p), hitTick, targetId, blockHit));
		long len = Math.round(reach * 100.0);
		long t = now(p);
		Miraculous.edit(p, n -> {
			n.vals.put(CatNoir.V_EXTEND_LENGTH, len);
			n.vecs.put(CatNoir.VEC_EXTEND_DIR, java.util.List.of(dir.x, dir.y, dir.z));
			n.animId = CatNoir.ANIM_EXTEND;
			n.animStart = t;
		});
		Miraculous.setCooldown(p, CatNoir.EXTEND, CatNoirConfig.EXTEND_COOLDOWN);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 0.9f, 1.6f);
	}

	private static void tickExtend(ServerPlayer p, Extend x, long age) {
		if (age == x.hitTick()) {
			ServerLevel level = level(p);
			if (x.targetId() >= 0 && level.getEntity(x.targetId()) instanceof LivingEntity target && target.isAlive()) {
				if (CatNoirCombat.strike(p, target, p.position(), CatNoirConfig.EXTEND_DAMAGE, CatNoirConfig.EXTEND_KNOCKBACK,
						CatNoirConfig.EXTEND_LIFT)) {
					Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
					level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
					level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.4f);
				}
			} else if (x.blockHit() != null) {
				Vec3 c = x.blockHit();
				level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.1, 0.1, 0.1, 0.25);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.8f);
			}
		}
		if (age >= CatNoirConfig.EXTEND_OUT_TICKS + CatNoirConfig.EXTEND_HOLD_TICKS + CatNoirConfig.EXTEND_BACK_TICKS) {
			EXTENDS.remove(p.getUUID());
			Miraculous.edit(p, n -> {
				n.vals.remove(CatNoir.V_EXTEND_LENGTH);
				n.vecs.remove(CatNoir.VEC_EXTEND_DIR);
			});
		}
	}

	public static void throwBaton(ServerPlayer p) {
		if (CatNoir.thrownId(p) >= 0) {
			Miraculous.say(p, "message.projecthero.cat_noir.baton_away", ChatFormatting.GRAY);
			return;
		}
		if (!begin(p, CatNoir.THROW)) {
			return;
		}
		Vec3 from = AbilityHelpers.handPosition(p);
		Vec3 aim = AbilityHelpers.aimPoint(p, CatNoirConfig.THROW_RANGE);
		Vec3 dir = aim.subtract(from);
		if (dir.lengthSqr() < 0.01) {
			dir = p.getLookAngle();
		}
		CatBatonEntity baton = CatBatonEntity.toss(p, from, dir, CatNoir.rotted(p));
		if (baton == null) {
			return;
		}
		int id = baton.getId();
		long t = now(p);
		Miraculous.edit(p, n -> {
			n.vals.put(CatNoir.V_THROWN, id + 1L);
			n.animId = CatNoir.ANIM_THROW;
			n.animStart = t;
		});
		Miraculous.setCooldown(p, CatNoir.THROW, CatNoirConfig.THROW_COOLDOWN);
		p.swing(InteractionHand.MAIN_HAND, true);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 1.0f, 1.2f);
	}

	/** The thrown baton is back in his hand (or lost and simply returns). */
	public static void catchBaton(ServerPlayer p, CatBatonEntity baton) {
		if (CatNoir.thrownId(p) != baton.getId()) {
			return;
		}
		long t = now(p);
		Miraculous.edit(p, n -> {
			n.vals.remove(CatNoir.V_THROWN);
			n.animId = CatNoir.ANIM_CATCH;
			n.animStart = t;
		});
		sound(p, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.8f, 1.5f);
	}

	// ---------------------------------------------------------------- X: Pole Vault / Shift+X: Rooftop Run

	/** The launch speed (blocks a tick, straight up) that peaks {@code height} blocks up under vanilla gravity and drag. */
	public static double velocityForHeight(double height) {
		double lo = 0.0, hi = 6.0;
		for (int i = 0; i < 30; i++) {
			double mid = (lo + hi) * 0.5;
			if (apex(mid) < height) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) * 0.5;
	}

	/** How high a straight-up launch at {@code vy} climbs (position += v, then gravity 0.08 and drag 0.98). */
	public static double apex(double vy) {
		double y = 0.0;
		for (int t = 0; t < 400 && vy > 0.0; t++) {
			y += vy;
			vy = (vy - 0.08) * 0.98;
		}
		return y;
	}

	public static void vault(ServerPlayer p) {
		Vec3 base;
		if (p.onGround()) {
			base = p.position();
		} else {
			Vec3 feet = p.position();
			BlockHitResult down = p.level().clip(new ClipContext(feet, feet.subtract(0, CatNoirConfig.VAULT_GROUND_RANGE, 0),
					ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
			if (down.getType() == HitResult.Type.MISS) {
				Miraculous.say(p, "message.projecthero.cat_noir.no_ground", ChatFormatting.GRAY);
				return;
			}
			base = down.getLocation();
		}
		if (!begin(p, CatNoir.VAULT)) {
			return;
		}
		VAULTS.put(p.getUUID(), new Timed(now(p)));
		Vec3 b = base;
		long t = now(p);
		Miraculous.edit(p, n -> {
			n.vecs.put(CatNoir.VEC_VAULT_BASE, java.util.List.of(b.x, b.y, b.z));
			n.animId = CatNoir.ANIM_VAULT;
			n.animStart = t;
		});
		Miraculous.setCooldown(p, CatNoir.VAULT, CatNoirConfig.VAULT_COOLDOWN);
		sound(p, SoundEvents.ANVIL_PLACE, 0.4f, 1.8f);
	}

	private static void tickVault(ServerPlayer p, long age) {
		if (age == CatNoirConfig.VAULT_PLANT_TICKS) {
			Vec3 flat = p.getLookAngle().multiply(1, 0, 1);
			flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize().scale(CatNoirConfig.VAULT_FORWARD);
			AbilityHelpers.launchSelf(p, new Vec3(flat.x, velocityForHeight(CatNoirConfig.VAULT_HEIGHT), flat.z));
			sound(p, SoundEvents.TRIDENT_RIPTIDE_3.value(), 0.8f, 1.6f);
			level(p).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 10, 0.3, 0.05, 0.3, 0.05);
		}
		if (age >= CatNoirConfig.VAULT_POLE_TICKS) {
			VAULTS.remove(p.getUUID());
			Miraculous.setVec(p, CatNoir.VEC_VAULT_BASE, null);
		}
	}

	public static void rooftop(ServerPlayer p) {
		if (!begin(p, CatNoir.ROOFTOP)) {
			return;
		}
		boolean on = !CatNoir.rooftop(p);
		long t = now(p);
		Miraculous.edit(p, n -> {
			if (on) {
				n.vals.put(CatNoir.V_ROOFTOP, 1L);
				n.animId = CatNoir.ANIM_ROOFTOP;
				n.animStart = t;
			} else {
				n.vals.remove(CatNoir.V_ROOFTOP);
			}
		});
		if (!on) {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, ROOFTOP_SPEED_ID);
		}
		Miraculous.setCooldown(p, CatNoir.ROOFTOP, CatNoirConfig.ROOFTOP_TOGGLE_COOLDOWN);
		Miraculous.say(p, on ? "message.projecthero.cat_noir.rooftop_on" : "message.projecthero.cat_noir.rooftop_off", ChatFormatting.GREEN);
		sound(p, SoundEvents.CAT_AMBIENT, 0.6f, on ? 1.3f : 0.9f);
	}

	// ---------------------------------------------------------------- Z: Baton Spin

	public static void spin(ServerPlayer p) {
		if (!begin(p, CatNoir.SPIN)) {
			return;
		}
		SPINS.put(p.getUUID(), new Spin(now(p)));
		Miraculous.setAnim(p, CatNoir.ANIM_SPIN);
		Miraculous.setCooldown(p, CatNoir.SPIN, CatNoirConfig.SPIN_COOLDOWN);
	}

	private static void tickSpin(ServerPlayer p, Spin s, long age) {
		if (age >= CatNoirConfig.SPIN_TICKS) {
			SPINS.remove(p.getUUID());
			return;
		}
		ServerLevel level = level(p);
		long now = now(p);
		if (age % 4 == 0) {
			sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.6f + 0.1f * (age % 8 == 0 ? 1 : 0));
		}
		deflect(p);
		Vec3 center = p.position().add(0, 1.0, 0);
		for (LivingEntity e : AbilityHelpers.living(level, center, CatNoirConfig.SPIN_RADIUS, e -> CatNoirCombat.isTarget(p, e))) {
			Long last = s.lastHit.get(e.getId());
			if (last != null && now - last < CatNoirConfig.SPIN_REHIT_TICKS) {
				continue;
			}
			if (CatNoirCombat.strike(p, e, p.position(), CatNoirConfig.SPIN_DAMAGE, CatNoirConfig.SPIN_KNOCKBACK, 0.1)) {
				s.lastHit.put(e.getId(), now);
			}
		}
		if (age % 3 == 0) {
			// sparks thrown off the whirling tips, round him (never in front of his own eyes)
			double a = age * 0.9;
			for (int k = 0; k < 2; k++) {
				double b = a + k * Math.PI;
				level.sendParticles(ParticleTypes.CRIT, p.getX() + Math.cos(b) * 1.6, p.getY() + 1.1, p.getZ() + Math.sin(b) * 1.6, 2, 0.1, 0.1, 0.1, 0.05);
			}
		}
	}

	/** Every projectile closing on him within {@link CatNoirConfig#SPIN_DEFLECT_RADIUS} is batted straight back -- now his. */
	public static int deflect(ServerPlayer p) {
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		AABB box = p.getBoundingBox().inflate(CatNoirConfig.SPIN_DEFLECT_RADIUS);
		int n = 0;
		for (Projectile pr : p.level().getEntitiesOfClass(Projectile.class, box, e -> e.isAlive())) {
			if (pr.getOwner() == p) {
				continue;
			}
			Vec3 v = pr.getDeltaMovement();
			Vec3 to = c.subtract(pr.position());
			if (v.lengthSqr() < 0.01 || v.dot(to) <= 0.0 || to.lengthSqr() > CatNoirConfig.SPIN_DEFLECT_RADIUS * CatNoirConfig.SPIN_DEFLECT_RADIUS) {
				continue;
			}
			Entity shooter = pr.getOwner();
			Vec3 back = shooter != null && shooter.isAlive() && shooter.distanceToSqr(p) < 64 * 64
					? shooter.position().add(0, shooter.getBbHeight() * 0.6, 0).subtract(pr.position())
					: to.scale(-1.0);
			pr.setDeltaMovement(back.normalize().scale(Math.max(0.6, v.length())));
			pr.setOwner(p);
			pr.hurtMarked = true;
			pr.hasImpulse = true;
			level(p).sendParticles(ParticleTypes.CRIT, pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.2);
			level(p).playSound(null, pr.getX(), pr.getY(), pr.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7f, 1.4f);
			n++;
		}
		return n;
	}

	// ---------------------------------------------------------------- V: Cat Leap / Shift+V: Pounce Strike

	public static void leap(ServerPlayer p) {
		if (!begin(p, CatNoir.LEAP)) {
			return;
		}
		AbilityHelpers.launchSelf(p, leapVelocity(p.getLookAngle(), p.onGround()));
		Miraculous.setAnim(p, CatNoir.ANIM_LEAP);
		Miraculous.setCooldown(p, CatNoir.LEAP, CatNoirConfig.LEAP_COOLDOWN);
		sound(p, SoundEvents.CAT_HISS, 0.5f, 1.4f);
	}

	public static Vec3 leapVelocity(Vec3 look, boolean grounded) {
		return AbilityHelpers.ballisticLaunch(look, CatNoirConfig.LEAP_DISTANCE, grounded);
	}

	public static void pounce(ServerPlayer p) {
		LivingEntity target = AbilityHelpers.raycastEntity(p, CatNoirConfig.POUNCE_RANGE);
		if (target == null || !CatNoirCombat.isTarget(p, target)) {
			Miraculous.say(p, "message.projecthero.cat_noir.no_target", ChatFormatting.GRAY);
			return;
		}
		if (!begin(p, CatNoir.POUNCE)) {
			return;
		}
		Vec3 to = target.position().subtract(p.position());
		double dist = to.length();
		if (dist > CatNoirConfig.POUNCE_HIT_DISTANCE) {
			AbilityHelpers.launchSelf(p, AbilityHelpers.ballisticLaunch(to, Math.max(1.0, dist - 0.8), p.onGround()));
		}
		POUNCES.put(p.getUUID(), new Pounce(now(p), target.getId()));
		Miraculous.setAnim(p, CatNoir.ANIM_POUNCE);
		Miraculous.setCooldown(p, CatNoir.POUNCE, CatNoirConfig.POUNCE_COOLDOWN);
		sound(p, SoundEvents.CAT_HISS, 0.7f, 1.0f);
	}

	private static void tickPounce(ServerPlayer p, Pounce s, long age) {
		Entity e = p.level().getEntity(s.targetId());
		if (!(e instanceof LivingEntity target) || !target.isAlive() || age > CatNoirConfig.POUNCE_MAX_TICKS) {
			POUNCES.remove(p.getUUID());
			return;
		}
		if (age >= 2 && AbilityHelpers.distanceSqToBox(target, p.position().add(0, 0.9, 0))
				<= CatNoirConfig.POUNCE_HIT_DISTANCE * CatNoirConfig.POUNCE_HIT_DISTANCE) {
			POUNCES.remove(p.getUUID());
			p.swing(InteractionHand.MAIN_HAND, true);
			if (CatNoirCombat.strike(p, target, p.position(), CatNoirConfig.POUNCE_DAMAGE, CatNoirConfig.POUNCE_KNOCKBACK, 0.2)) {
				Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
				level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 2, 0.3, 0.2, 0.3, 0);
				sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.9f);
			}
			// land on it, not past it
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().multiply(0.2, 0.5, 0.2));
			return;
		}
		if (age > 5 && p.onGround()) {
			POUNCES.remove(p.getUUID()); // landed short
		}
	}

	// ---------------------------------------------------------------- C: Night Vision / Shift+C: Cat Reflexes

	public static void nightVision(ServerPlayer p) {
		if (!begin(p, CatNoir.NIGHT_VISION)) {
			return;
		}
		boolean on = !CatNoir.nightVision(p);
		long t = now(p);
		Miraculous.edit(p, n -> {
			if (on) {
				n.vals.put(CatNoir.V_NIGHT, 1L);
			} else {
				n.vals.remove(CatNoir.V_NIGHT);
			}
			n.animId = CatNoir.ANIM_NIGHT_VISION;
			n.animStart = t;
		});
		if (on) {
			refreshNightVision(p);
		} else {
			p.removeEffect(MobEffects.NIGHT_VISION);
		}
		Miraculous.setCooldown(p, CatNoir.NIGHT_VISION, CatNoirConfig.NIGHT_VISION_TOGGLE_COOLDOWN);
		Miraculous.say(p, on ? "message.projecthero.cat_noir.night_on" : "message.projecthero.cat_noir.night_off", ChatFormatting.GREEN);
		sound(p, SoundEvents.CAT_PURR, 0.8f, 1.2f);
	}

	private static void refreshNightVision(ServerPlayer p) {
		MobEffectInstance cur = p.getEffect(MobEffects.NIGHT_VISION);
		if (cur == null || (!cur.isInfiniteDuration() && cur.getDuration() < CatNoirConfig.NIGHT_VISION_EFFECT_TICKS - 100)) {
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, CatNoirConfig.NIGHT_VISION_EFFECT_TICKS, 0, true, false, true));
		}
	}

	public static void reflexes(ServerPlayer p) {
		if (!begin(p, CatNoir.REFLEXES)) {
			return;
		}
		long t = now(p);
		Miraculous.edit(p, n -> {
			n.vals.put(CatNoir.V_REFLEX_UNTIL, t + CatNoirConfig.REFLEX_TICKS);
			n.vals.put(CatNoir.V_REFLEX_LEFT, (long) CatNoirConfig.REFLEX_DODGES);
			n.animId = CatNoir.ANIM_REFLEXES;
			n.animStart = t;
		});
		Miraculous.setCooldown(p, CatNoir.REFLEXES, CatNoirConfig.REFLEX_COOLDOWN);
		Miraculous.say(p, "message.projecthero.cat_noir.reflexes", ChatFormatting.GREEN, CatNoirConfig.REFLEX_DODGES);
		sound(p, SoundEvents.CAT_AMBIENT, 0.8f, 1.5f);
	}

	/** Can Cat Reflexes slip this hit? Anything something did to him (a blow, a shot, a blast) -- not falls, fire, hunger or /kill. */
	public static boolean dodgeable(DamageSource source) {
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return source.getEntity() != null || source.getDirectEntity() != null || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
	}

	/** From the damage hook: dodge this hit if Cat Reflexes has one left. True = the hit is cancelled. */
	public static boolean tryDodge(ServerPlayer p, DamageSource source) {
		int left = CatNoir.reflexesLeft(p);
		if (left <= 0 || !dodgeable(source)) {
			return false;
		}
		int now = left - 1;
		boolean leftSide = (now & 1) == 0;
		long t = now(p);
		Miraculous.edit(p, n -> {
			if (now <= 0) {
				n.vals.remove(CatNoir.V_REFLEX_LEFT);
				n.vals.remove(CatNoir.V_REFLEX_UNTIL);
			} else {
				n.vals.put(CatNoir.V_REFLEX_LEFT, (long) now);
			}
			n.animId = leftSide ? CatNoir.ANIM_DODGE_LEFT : CatNoir.ANIM_DODGE_RIGHT;
			n.animStart = t;
		});
		// the sidestep: across the line of the attack
		Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
		Vec3 line = from != null ? p.position().subtract(from.position()) : p.getLookAngle().scale(-1);
		line = new Vec3(line.x, 0, line.z);
		if (line.lengthSqr() < 1.0e-4) {
			line = p.getLookAngle().multiply(1, 0, 1);
		}
		line = line.normalize();
		Vec3 side = new Vec3(-line.z, 0, line.x).scale(leftSide ? 1 : -1);
		AbilityHelpers.launchSelf(p, side.scale(CatNoirConfig.REFLEX_SIDESTEP).add(0, 0.18, 0));
		level(p).sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.49f, 0.89f, 0.23f), 1.0f), p.getX(), p.getY() + 1.0, p.getZ(), 10,
				0.3, 0.5, 0.3, 0.0);
		sound(p, SoundEvents.CAT_HISS, 0.6f, 1.6f);
		Miraculous.say(p, "message.projecthero.cat_noir.dodged", ChatFormatting.GREEN, now);
		return true;
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		UUID id = p.getUUID();
		long now = now(p);
		Timed st = STRIKES.get(id);
		if (st != null) {
			long age = now - st.start();
			tickStrike(p, st, age);
			if (age >= CatNoirConfig.STRIKE_TICKS) {
				STRIKES.remove(id);
			}
		}
		Timed sw = SWEEPS.get(id);
		if (sw != null) {
			long age = now - sw.start();
			tickSweep(p, age);
			if (age >= CatNoirConfig.SWEEP_TICKS) {
				SWEEPS.remove(id);
			}
		}
		Extend ex = EXTENDS.get(id);
		if (ex != null) {
			tickExtend(p, ex, now - ex.start());
		}
		Timed v = VAULTS.get(id);
		if (v != null) {
			tickVault(p, now - v.start());
		}
		Spin s = SPINS.get(id);
		if (s != null) {
			tickSpin(p, s, now - s.start);
		}
		Pounce po = POUNCES.get(id);
		if (po != null) {
			tickPounce(p, po, now - po.start());
		}
		// toggles and timers
		if (CatNoir.nightVision(p) && p.tickCount % 20 == 0) {
			refreshNightVision(p);
		}
		if (CatNoir.rooftop(p) && p.isSprinting()) {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, ROOFTOP_SPEED_ID, CatNoirConfig.ROOFTOP_SPRINT_BONUS,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, ROOFTOP_SPEED_ID);
		}
		long reflexUntil = CatNoir.val(p, CatNoir.V_REFLEX_UNTIL);
		if (reflexUntil > 0L && now >= reflexUntil) {
			Miraculous.edit(p, n -> {
				n.vals.remove(CatNoir.V_REFLEX_UNTIL);
				n.vals.remove(CatNoir.V_REFLEX_LEFT);
			});
		}
		int thrown = CatNoir.thrownId(p);
		if (thrown >= 0 && !(p.level().getEntity(thrown) instanceof CatBatonEntity)) {
			Miraculous.setVal(p, CatNoir.V_THROWN, 0L); // lost (unloaded, another dimension): it is simply back
		}
	}
}
