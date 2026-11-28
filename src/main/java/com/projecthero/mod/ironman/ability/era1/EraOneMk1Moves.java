package com.projecthero.mod.ironman.ability.era1;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManComicAccess;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.ability.era1.EraOneMk12State.Rt;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Era I: the Mk I CAVE-FORGED moves (plan: {@code docs/IRONMAN_ERA1_PLAN.md}, kit ids {@code e1mk1_*}).
 * Grey scrap iron, no flight. v0.17.7 (user) moveset:
 * <pre>
 *   R        Iron Fist        12, big knockback
 *   Shift+R  Gauntlet Smash   hold ~1 s: 15 to the target, 10 to everything within 3 blocks of it; the ground cracks
 *   G        Flamethrower     hold: 8 a second + burning in a 6-block cone; melts snow / ice, burns cobwebs
 *   Shift+G  Arm Rocket       14 direct, 8 to everything within 3 blocks of the blast
 *   Z        Scrap Shotgun    5 pellets x 4 out to 8 blocks; knocks small mobs back
 *   Shift+Z  Fire Nova        10 + burning to everything within 5 blocks
 *   X        Rocket Jump      ~8 blocks up, no fall damage
 *   Shift+X  Rocket Leap      a ~14-block arc where you look (pitch too), no fall damage
 *   V        Iron Shell       4 s: 60% less damage, no knockback, rooted, projectiles bounce off
 *   Shift+V  Scrap Repair     one iron ingot = +150 integrity
 *   C        Smoke Screen     tap: a 6 s cloud mobs lose you in
 *   hold C   Reactor Overload held 1 s: the next hit within 10 s x2, or 3 s of double flame
 * </pre>
 * Every number the user sets is a named constant here (the damage figures are also the kit table's {@code move.damage()}).
 * What each move leaves running lives in {@link EraOneMk12State}.
 */
public final class EraOneMk1Moves {
	// ------------------------------------------------------------------ R: Iron Fist / Gauntlet Smash
	/** Iron Fist reach and knockback; v0.17.7 (user): damage 12. */
	public static final double FIST_REACH = 4.0;
	public static final double IRON_FIST_KNOCK = 2.4;
	public static final float IRON_FIST_DAMAGE = 12f;
	/** v0.17.7 (user) Gauntlet Smash: 15 to the target, 10 to everything else within 3 blocks of it. */
	public static final float GAUNTLET_SMASH_DAMAGE = 15f;
	public static final float GAUNTLET_SMASH_SPLASH_DAMAGE = 10f;
	public static final double GAUNTLET_SMASH_RADIUS = 3.0;
	/** Full power after a 1 s wind-up; let go sooner = scaled down to half; a 3 s hold slams on its own. */
	public static final int GAUNTLET_SMASH_CHARGE_TICKS = 20;
	public static final int GAUNTLET_SMASH_MAX_HOLD = 60;
	/** The target is driven down into the ground at this speed and (full charge) staggered this long. */
	public static final double GAUNTLET_SMASH_SLAM_VY = -0.9;
	public static final int GAUNTLET_SMASH_STAGGER_TICKS = 30;
	/** The others in the splash are shoved away from the impact (and popped up off the cracking ground). */
	public static final double GAUNTLET_SMASH_SPLASH_KNOCK = 1.0;
	public static final double GAUNTLET_SMASH_SPLASH_LIFT = 0.45;
	/** With nothing to hit, the fists come down on the ground this far ahead. */
	public static final double GAUNTLET_SMASH_GROUND_AHEAD = 2.0;

	// ------------------------------------------------------------------ G: Flamethrower / Arm Rocket
	/** Flamethrower: a 6-block cone (30 degrees each side), 8 damage a second (dealt every 5 ticks) + 4 s burning. */
	public static final double FLAME_RANGE = 6.0;
	public static final double FLAME_HALF_DEG = 30.0;
	public static final int FLAME_HIT_EVERY = 5;
	public static final int FLAME_FIRE_TICKS = 80;
	/** v0.17.7 (user): the stream melts snow / ice and burns cobwebs where it is aimed -- a pass every 10 ticks, up to 6 blocks. */
	public static final int FLAME_MELT_EVERY = 10;
	public static final int FLAME_MELT_MAX_BLOCKS = 6;
	/** v0.17.7 (user) Arm Rocket: 14 direct, 8 to everything within 3 blocks of the blast; flies straight at 1.6 blocks a tick. */
	public static final float ARM_ROCKET_DAMAGE = 14f;
	public static final float ARM_ROCKET_SPLASH_DAMAGE = 8f;
	public static final float ARM_ROCKET_RADIUS = 3.0f;
	public static final double ARM_ROCKET_SPEED = 1.6;

	// ------------------------------------------------------------------ Z: Scrap Shotgun / Fire Nova
	/** v0.17.7 (user) Scrap Shotgun: 5 pellets of 4 out to 8 blocks. */
	public static final int SCRAP_PELLETS = 5;
	public static final float SCRAP_PELLET_DAMAGE = 4f;
	public static final double SCRAP_RANGE = 8.0;
	/** Small mobs (no taller than this, no wider than {@link #SCRAP_SMALL_MAX_WIDTH}) are knocked back (once per blast). */
	public static final double SCRAP_SMALL_MAX_HEIGHT = 2.0;
	public static final double SCRAP_SMALL_MAX_WIDTH = 1.5;
	public static final double SCRAP_KNOCK = 1.1;
	/** v0.17.7 (user) Fire Nova: 10 + 5 s burning to everything within 5 blocks; the ring rolls out over 8 ticks. */
	public static final float FIRE_NOVA_DAMAGE = 10f;
	public static final double FIRE_NOVA_RADIUS = 5.0;
	public static final int FIRE_NOVA_FIRE_TICKS = 100;
	public static final double FIRE_NOVA_KNOCK = 0.6;
	public static final int FIRE_NOVA_RING_TICKS = 8;

	// ------------------------------------------------------------------ X: Rocket Jump / Rocket Leap
	/** Rocket Jump: 1.18 blocks/tick straight up = an ~8-block rise. */
	public static final double ROCKET_JUMP_VY = 1.18;
	/** v0.17.7 (user) Rocket Leap: a ~14-block arc where you look, pitch included (from the ground never into it). */
	public static final double ROCKET_LEAP_BLOCKS = 14.0;
	/** Rocket jump / leap: fall-safe until landing or 5 s. */
	public static final int ROCKET_SAFE_TICKS = 100;

	// ------------------------------------------------------------------ V: Iron Shell / Scrap Repair
	/** v0.17.7 (user) Iron Shell (the old Brace): 4 s, 60% of every hit stopped, no knockback, rooted, projectiles bounce off. */
	public static final float IRON_SHELL_FACTOR = 0.4f;
	public static final int IRON_SHELL_TICKS = 4 * 20;
	public static final double IRON_SHELL_BOUNCE_RADIUS = 1.8;
	/** v0.17.7 (user) Scrap Repair: one iron ingot = +150 integrity. */
	public static final float SCRAP_REPAIR_INTEGRITY = 150f;

	// ------------------------------------------------------------------ C: Smoke Screen (tap) / Reactor Overload (hold)
	/** Smoke Screen: a 6 s cloud of radius 5; its own 14 s cooldown and 15 energy (the C key's tap). */
	public static final int SMOKE_TICKS = 6 * 20;
	public static final double SMOKE_RADIUS = 5.0;
	public static final int SMOKE_COOLDOWN_TICKS = 14 * 20;
	public static final float SMOKE_ENERGY = 15f;
	/** v0.17.7 (user): C held this long (1 s) is Reactor Overload instead (its own 20 s cooldown and 50 energy). */
	public static final int OVERLOAD_HOLD_TICKS = 20;
	public static final int OVERLOAD_COOLDOWN_TICKS = 20 * 20;
	public static final float OVERLOAD_ENERGY = 50f;
	/** The C key's two cooldown keys (each move keeps its own). */
	public static final String SMOKE_ID = "e1mk1_smoke_screen";
	public static final String OVERLOAD_ID = "e1mk1_reactor_overload";

	// ------------------------------------------------------------------ passives
	/** v0.17.7 (user): the heavy Mk I's knockback resistance (full suit; the other suits get 0.10 + 0.06 x tech). */
	public static final double KNOCKBACK_RESISTANCE = 0.6;

	private EraOneMk1Moves() {
	}

	static void init() {
		EraOneMk12State.initialize();
		IronManEraOne.register("e1mk1_iron_fist", tap(EraOneMk1Moves::ironFist));
		IronManEraOne.register("e1mk1_gauntlet_smash", EraOneMk1Moves::gauntletSmash);
		IronManEraOne.register("e1mk1_flamethrower", EraOneMk1Moves::flamethrower);
		IronManEraOne.register("e1mk1_arm_rocket", tap(EraOneMk1Moves::armRocket));
		IronManEraOne.register("e1mk1_rocket_jump", tap(EraOneMk1Moves::rocketJump));
		IronManEraOne.register("e1mk1_rocket_leap", tap(EraOneMk1Moves::rocketLeap));
		IronManEraOne.register("e1mk1_scrap_shotgun", tap(EraOneMk1Moves::scrapShotgun));
		IronManEraOne.register("e1mk1_fire_nova", tap(EraOneMk1Moves::fireNova));
		IronManEraOne.register("e1mk1_iron_shell", tap(EraOneMk1Moves::ironShell));
		IronManEraOne.register("e1mk1_scrap_repair", tap(EraOneMk1Moves::scrapRepair));
		IronManEraOne.register(SMOKE_ID, EraOneMk1Moves::smokeOrOverload);
		// the old C move id stays loadable (a stale cooldown / binding): it is the hold half of C now
		IronManEraOne.register(OVERLOAD_ID, tap(EraOneMk1Moves::reactorOverload));
	}

	/** A TAP move: only the press does anything. */
	@FunctionalInterface
	interface Tap {
		boolean fire(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m);
	}

	static IronManEraOne.Handler tap(Tap t) {
		return (p, suit, m, pressed) -> pressed && t.fire(p, suit, m);
	}

	// ================================================================== R: iron fist / gauntlet smash

	private static LivingEntity fistTarget(ServerPlayer p) {
		LivingEntity t = IronManComicAccess.target(p, FIST_REACH);
		if (t == null) {
			List<LivingEntity> close = IronManComicAccess.cone(p, p.getEyePosition(), p.getLookAngle(), FIST_REACH, 40.0);
			t = close.isEmpty() ? null : close.get(0);
		}
		return t;
	}

	/** {@link #IRON_FIST_DAMAGE}, big knockback. A swing at nothing still goes off (a punch is a punch). */
	private static boolean ironFist(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		LivingEntity t = fistTarget(p);
		IronManComicAccess.pose(p, IronManAbilityFx.PUNCH, 10);
		IronManSounds.move(p, IronManSounds.PUNCH, 1.1f, 0.8f);
		if (t != null) {
			IronManComicAccess.hit(p, t, m.damage());
			IronManComicAccess.shove(t, p.position(), IRON_FIST_KNOCK, 0.45);
			impact(p, t, false);
		} else {
			IronManSounds.play(p, SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 0.7f);
		}
		return true;
	}

	/**
	 * HOLD: the press commits (pays, cooldown) and raises both fists overhead; the release brings them down -- full
	 * {@link #GAUNTLET_SMASH_DAMAGE} to the target (driven into the ground, staggered) and {@link #GAUNTLET_SMASH_SPLASH_DAMAGE}
	 * to everything else within {@link #GAUNTLET_SMASH_RADIUS} of it, after a 1 s wind-up; let go sooner and both scale down
	 * (to half). A 3 s hold slams on its own. Nothing in reach: the fists crack the ground ahead (the splash still lands).
	 */
	private static boolean gauntletSmash(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m, boolean pressed) {
		Rt r = EraOneMk12State.rt(p, suit);
		long now = p.level().getGameTime();
		if (pressed) {
			if (r.smashStart != 0L) {
				return false;
			}
			r.smashStart = now;
			IronManComicAccess.pose(p, IronManAbilityFx.MK1_SMASH_WINDUP, 8);
			IronManSounds.move(p, IronManSounds.SERVO, 1.0f, 0.55f);
			IronManSounds.play(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0f, 0.6f);
			return true;
		}
		if (r.smashStart == 0L) {
			return false;
		}
		smash(p, r, now);
		return false;
	}

	static void smashWindup(ServerPlayer p, Rt r, long now) {
		long held = now - r.smashStart;
		IronManAbilityFx.hold(p, IronManAbilityFx.MK1_SMASH_WINDUP);
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 fists = p.getEyePosition().add(0, 0.65 * p.getScale(), 0).add(EraOneMk1Moves.flatLook(p).scale(0.15));
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, fists.x, fists.y, fists.z, (int) Math.min(6, 1 + held / 4), 0.12, 0.08, 0.12, 0.02);
		if (held % 3 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, fists.x, fists.y, fists.z, 1, 0.1, 0.05, 0.1, 0.01);
		}
		if (held == GAUNTLET_SMASH_CHARGE_TICKS) {
			IronManSounds.play(p, IronManSounds.REPULSOR_READY, 0.7f, 0.55f);
			IronManSounds.play(p, SoundEvents.ANVIL_PLACE, 0.5f, 0.6f);
			level.sendParticles(ParticleTypes.FLAME, fists.x, fists.y, fists.z, 12, 0.15, 0.1, 0.15, 0.03);
		} else if (held % 6 == 0) {
			IronManSounds.loop(p, IronManSounds.SERVO, 0.4f, 0.55f + held / 60f);
		}
		if (held >= GAUNTLET_SMASH_MAX_HOLD) {
			smash(p, r, now);
		}
	}

	/** The charge share 0.5..1 for a hold of {@code held} ticks (full at {@link #GAUNTLET_SMASH_CHARGE_TICKS}). */
	public static float chargeShare(long held) {
		return (float) Math.max(0.5, Math.min(1.0, held / (double) GAUNTLET_SMASH_CHARGE_TICKS));
	}

	private static void smash(ServerPlayer p, Rt r, long now) {
		long held = now - r.smashStart;
		r.smashStart = 0L;
		boolean full = held >= GAUNTLET_SMASH_CHARGE_TICKS;
		float share = chargeShare(held);
		IronManComicKit.Move m = IronManComicKit.byId("e1mk1_gauntlet_smash");
		float dmg = (m == null ? GAUNTLET_SMASH_DAMAGE : m.damage()) * share;
		float splash = GAUNTLET_SMASH_SPLASH_DAMAGE * share;
		IronManComicAccess.pose(p, IronManAbilityFx.MK1_GAUNTLET_SMASH, IronManAbilityFx.MK1_SMASH_TICKS);
		IronManSounds.move(p, IronManSounds.PUNCH, 1.3f, full ? 0.5f : 0.7f);
		LivingEntity t = fistTarget(p);
		Vec3 at;
		if (t != null) {
			IronManComicAccess.hit(p, t, dmg);
			// hammered down into the ground
			t.setDeltaMovement(t.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, GAUNTLET_SMASH_SLAM_VY, 0));
			t.hurtMarked = true;
			if (full && t instanceof Mob mob) {
				IronManComicAccess.stun(mob, GAUNTLET_SMASH_STAGGER_TICKS);
			} else if (full) {
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, GAUNTLET_SMASH_STAGGER_TICKS, 4);
			}
			at = new Vec3(t.getX(), t.getY(), t.getZ());
			impact(p, t, full);
		} else {
			at = groundAt(p, p.position().add(flatLook(p).scale(GAUNTLET_SMASH_GROUND_AHEAD)));
		}
		// the splash: everything else within 3 blocks of the impact (around() never takes the player, squadmates or pets)
		Vec3 centre = at.add(0, 0.5, 0);
		for (LivingEntity e : IronManComicAccess.around(p, centre, GAUNTLET_SMASH_RADIUS)) {
			if (e == t) {
				continue;
			}
			IronManComicAccess.hit(p, e, splash);
			IronManComicAccess.shove(e, at, GAUNTLET_SMASH_SPLASH_KNOCK * (full ? 1.0 : 0.6), GAUNTLET_SMASH_SPLASH_LIFT);
		}
		crackGround(p, at, full);
	}

	/** The cracked ground round a Gauntlet Smash: three rings of torn-up block chunks, eight crack lines and a dust shock. */
	private static void crackGround(ServerPlayer p, Vec3 at, boolean full) {
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 g = new Vec3(at.x, at.y + 0.05, at.z);
		for (double rad = 1.0; rad <= GAUNTLET_SMASH_RADIUS + 0.01; rad += 1.0) {
			IronManComicAccess.groundRing(level, g, rad, (int) (10 * rad));
		}
		BlockState ground = level.getBlockState(BlockPos.containing(g.x, g.y - 0.3, g.z));
		if (ground.isAir()) {
			ground = Blocks.STONE.defaultBlockState();
		}
		BlockParticleOption chunk = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8 + 0.2 * (i % 2);
			for (double d = 0.4; d <= GAUNTLET_SMASH_RADIUS + 0.01; d += 0.35) {
				level.sendParticles(chunk, g.x + Math.cos(a) * d, g.y + 0.05, g.z + Math.sin(a) * d, 2, 0.05, 0.02, 0.05, 0.05);
			}
		}
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, g.x, g.y + 0.2, g.z, 6, 0.8, 0.05, 0.8, 0.02);
		level.sendParticles(ParticleTypes.EXPLOSION, g.x, g.y + 0.3, g.z, full ? 3 : 1, 0.5, 0.1, 0.5, 0);
		IronManComicAccess.soundAt(level, g, SoundEvents.ANVIL_LAND, 1.0f, full ? 0.5f : 0.7f);
		IronManComicAccess.soundAt(level, g, SoundEvents.GENERIC_EXPLODE.value(), full ? 0.9f : 0.6f, 0.6f);
		IronManComicAccess.soundAt(level, g, SoundEvents.STONE_BREAK, 1.2f, 0.6f);
	}

	private static void impact(ServerPlayer p, LivingEntity t, boolean heavy) {
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 c = IronManComicAccess.centre(t);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, heavy ? 30 : 14, 0.3, 0.3, 0.3, 0.25);
		level.sendParticles(heavy ? ParticleTypes.EXPLOSION : ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		IronManComicAccess.soundAt(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.1f, heavy ? 0.6f : 0.8f);
	}

	// ================================================================== G: flamethrower / arm rocket

	/** HOLD: press lights the stream (it runs while held), release stops it. */
	private static boolean flamethrower(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m, boolean pressed) {
		Rt r = EraOneMk12State.rt(p, suit);
		if (!pressed) {
			if (r.flame) {
				r.flame = false;
				IronManSounds.play(p, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.4f);
			}
			return false;
		}
		if (r.flame) {
			return false;
		}
		r.flame = true;
		r.flameStart = p.level().getGameTime();
		IronManSounds.move(p, IronManSounds.FLAMETHROWER_IGNITE, 1.0f, 1.0f);
		return true;
	}

	/** One tick of the stream. Returns false when it has to stop. */
	static boolean flameTick(ServerPlayer p, IronManSuit suit, Rt r, long now) {
		if (!drain(p, suit, "e1mk1_flamethrower")) {
			return false;
		}
		ServerLevel level = IronManComicAccess.level(p);
		IronManAbilityFx.hold(p, IronManAbilityFx.FLAME);
		Vec3 eye = p.getEyePosition();
		Vec3 dir = IronManComicAccess.aim(p, FLAME_RANGE);
		double len = IronManComicAccess.wallDistance(p, eye, dir, FLAME_RANGE);
		long age = now - r.flameStart;
		if (age % FLAME_HIT_EVERY == 0) {
			IronManComicKit.Move m = IronManComicKit.byId("e1mk1_flamethrower");
			float perHit = (m == null ? 8f : m.damage()) * FLAME_HIT_EVERY / 20f;
			EraOneMk12State.asFlame(() -> {
				for (LivingEntity e : IronManComicAccess.cone(p, eye, dir, len + 0.5, FLAME_HALF_DEG)) {
					IronManComicAccess.burn(p, e, perHit, FLAME_FIRE_TICKS);
				}
			});
		}
		if (age % FLAME_MELT_EVERY == FLAME_MELT_EVERY - 1) {
			meltAlong(p, eye, dir, len);
		}
		Vec3 nozzle = IronManComicAccess.palm(p);
		boolean doubled = r.overloadFlameUntil > now;
		for (double d = 0.5; d <= len + 0.01; d += 0.5) {
			Vec3 fx = nozzle.add(dir.scale(d));
			double spread = 0.04 + 0.07 * d;
			level.sendParticles(ParticleTypes.FLAME, fx.x, fx.y, fx.z, 2, spread, spread, spread, 0.01);
			if (doubled && d % 1.5 < 0.01) {
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, fx.x, fx.y, fx.z, 1, spread, spread, spread, 0.01);
			}
		}
		if (now % 4 == 0) {
			IronManSounds.loop(p, IronManSounds.FLAMETHROWER, 0.8f, doubled ? 0.8f : 1.0f);
		}
		return true;
	}

	/**
	 * v0.17.7 (user): what the stream does to the blocks it is aimed at -- snow and ice melt (a snow block or ice turns to water,
	 * a snow layer / powder snow melts away; in an ultra-warm dimension the water boils off), cobwebs burn away. Packed and
	 * blue ice are left alone (they never melt in vanilla either), and fire blocks are left alone. Returns how many changed.
	 */
	public static int meltAlong(ServerPlayer p, Vec3 from, Vec3 dir, double len) {
		ServerLevel level = IronManComicAccess.level(p);
		Set<BlockPos> seen = new HashSet<>();
		int n = 0;
		for (double d = 0.5; d <= len + 0.8 && n < FLAME_MELT_MAX_BLOCKS; d += 0.4) {
			BlockPos bp = BlockPos.containing(from.add(dir.scale(d)));
			if (seen.add(bp) && melt(level, bp)) {
				n++;
			}
		}
		return n;
	}

	private static boolean melt(ServerLevel level, BlockPos bp) {
		BlockState s = level.getBlockState(bp);
		boolean web = s.is(Blocks.COBWEB);
		boolean layer = s.is(Blocks.SNOW) || s.is(Blocks.POWDER_SNOW);
		boolean solid = s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.ICE) || s.is(Blocks.FROSTED_ICE);
		if (!web && !layer && !solid) {
			return false;
		}
		boolean boils = level.dimensionType().ultraWarm();
		level.setBlockAndUpdate(bp, solid && !boils ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
		double x = bp.getX() + 0.5, y = bp.getY() + 0.5, z = bp.getZ() + 0.5;
		if (web) {
			level.sendParticles(ParticleTypes.FLAME, x, y, z, 8, 0.3, 0.3, 0.3, 0.02);
			level.sendParticles(ParticleTypes.SMOKE, x, y, z, 6, 0.3, 0.3, 0.3, 0.02);
			IronManComicAccess.soundAt(level, new Vec3(x, y, z), SoundEvents.FIRE_AMBIENT, 0.8f, 1.6f);
		} else {
			level.sendParticles(ParticleTypes.CLOUD, x, y, z, 6, 0.3, 0.3, 0.3, 0.02);
			level.sendParticles(ParticleTypes.DRIPPING_WATER, x, y, z, 4, 0.3, 0.2, 0.3, 0.0);
			IronManComicAccess.soundAt(level, new Vec3(x, y, z), SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.8f);
		}
		return true;
	}

	/**
	 * The forearm mini-rocket: flies straight where you aim at {@link #ARM_ROCKET_SPEED}, {@link #ARM_ROCKET_DAMAGE} to what it
	 * strikes and {@link #ARM_ROCKET_SPLASH_DAMAGE} to everything within {@link #ARM_ROCKET_RADIUS} of the blast (an exact
	 * blast -- no block damage). A smoke-and-flame trail behind it.
	 */
	private static boolean armRocket(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Vec3 from = IronManComicAccess.palm(p);
		Vec3 dir = IronManComicAccess.aim(p, 64.0).normalize();
		IronManMissileEntity rocket = new IronManMissileEntity(p.level(), p, dir.scale(ARM_ROCKET_SPEED))
				.withDamage(m.damage(), ARM_ROCKET_SPLASH_DAMAGE).withBlastRadius(ARM_ROCKET_RADIUS).withExactBlast();
		rocket.setPos(from.x, from.y, from.z);
		rocket.setDeltaMovement(dir.scale(ARM_ROCKET_SPEED));
		p.level().addFreshEntity(rocket);
		ServerLevel level = IronManComicAccess.level(p);
		// back-blast off the forearm, a flash at the launcher
		Vec3 back = from.subtract(dir.scale(0.6));
		level.sendParticles(ParticleTypes.LARGE_SMOKE, back.x, back.y, back.z, 8, 0.12, 0.12, 0.12, 0.04);
		level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 10, 0.06, 0.06, 0.06, 0.06);
		level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
		IronManComicAccess.pose(p, IronManAbilityFx.MK1_ARM_ROCKET, IronManAbilityFx.MK1_ROCKET_TICKS);
		IronManSounds.move(p, IronManSounds.ROCKET_LAUNCH, 1.1f, 0.9f);
		IronManSounds.play(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.7f);
		return true;
	}

	// ================================================================== Z: scrap shotgun / fire nova

	/**
	 * A close shotgun cone of scrap: {@link #SCRAP_PELLETS} pellets x {@link #SCRAP_PELLET_DAMAGE}, out to {@link #SCRAP_RANGE};
	 * each hits the first thing on its line. Small mobs it hits are knocked back (once per blast).
	 */
	private static boolean scrapShotgun(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = IronManComicAccess.aim(p, SCRAP_RANGE);
		Vec3 right = new Vec3(-look.z, 0, look.x);
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		// a fixed spread pattern (one down the middle, a diamond round it) so the cone is the same every shot
		double[][] spread = { { 0.0, 0.0 }, { 0.0, 0.05 }, { 0.0, -0.05 }, { 0.07, 0.0 }, { -0.07, 0.0 } };
		ItemParticleOption scrap = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.IRON_NUGGET));
		Set<LivingEntity> knocked = new HashSet<>();
		for (int i = 0; i < SCRAP_PELLETS; i++) {
			Vec3 dir = look.add(right.scale(spread[i % spread.length][0])).add(up.scale(spread[i % spread.length][1])).normalize();
			double len = IronManComicAccess.wallDistance(p, eye, dir, SCRAP_RANGE);
			List<LivingEntity> hits = IronManComicAccess.along(p, eye, eye.add(dir.scale(len)), 0.45);
			Vec3 end = eye.add(dir.scale(len));
			if (!hits.isEmpty()) {
				LivingEntity t = hits.get(0);
				end = IronManComicAccess.centre(t);
				IronManComicAccess.hit(p, t, m.damage());
				if (t.getBbHeight() <= SCRAP_SMALL_MAX_HEIGHT && t.getBbWidth() <= SCRAP_SMALL_MAX_WIDTH && knocked.add(t)) {
					IronManComicAccess.shove(t, eye, SCRAP_KNOCK, 0.3);
				}
			}
			IronManComicAccess.line(level, IronManComicAccess.palm(p), end, ParticleTypes.CRIT, 2.0);
			level.sendParticles(scrap, end.x, end.y, end.z, 4, 0.1, 0.1, 0.1, 0.12);
		}
		Vec3 muzzle = IronManComicAccess.palm(p);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.04);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 8, 0.08, 0.08, 0.08, 0.06);
		level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
		IronManComicAccess.pose(p, IronManAbilityFx.MK1_SCRAP_SHOTGUN, IronManAbilityFx.MK1_SHOTGUN_TICKS);
		IronManComicAccess.soundAt(level, muzzle, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.6f);
		IronManComicAccess.soundAt(level, muzzle, SoundEvents.ANVIL_PLACE, 0.6f, 1.5f);
		IronManComicAccess.soundAt(level, muzzle, SoundEvents.CHAIN_BREAK, 1.0f, 0.8f);
		return true;
	}

	/**
	 * Vents the reactor's heat: {@link #FIRE_NOVA_DAMAGE} + {@link #FIRE_NOVA_FIRE_TICKS} of burning to everything within
	 * {@link #FIRE_NOVA_RADIUS}, shoved out a little; a ring of fire rolls out to the edge (it never sets blocks alight).
	 */
	private static boolean fireNova(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Rt r = EraOneMk12State.rt(p, suit);
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		EraOneMk12State.asFlame(() -> {
			for (LivingEntity e : IronManComicAccess.around(p, c, FIRE_NOVA_RADIUS)) {
				IronManComicAccess.burn(p, e, m.damage(), FIRE_NOVA_FIRE_TICKS);
				IronManComicAccess.shove(e, p.position(), FIRE_NOVA_KNOCK, 0.25);
			}
		});
		r.novaStart = p.level().getGameTime();
		r.novaAt = p.position();
		Vec3 chest = IronManComicAccess.chest(p);
		level.sendParticles(ParticleTypes.FLASH, chest.x, chest.y, chest.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.LAVA, chest.x, chest.y, chest.z, 8, 0.4, 0.4, 0.4, 0.0);
		IronManComicAccess.pose(p, IronManAbilityFx.MK1_FIRE_NOVA, IronManAbilityFx.MK1_NOVA_TICKS);
		IronManComicAccess.soundAt(level, chest, SoundEvents.BLAZE_SHOOT, 1.2f, 0.6f);
		IronManComicAccess.soundAt(level, chest, SoundEvents.FIRECHARGE_USE, 1.2f, 0.5f);
		IronManSounds.move(p, IronManSounds.FLAMETHROWER_IGNITE, 1.2f, 0.6f);
		return true;
	}

	/** One tick of the Fire Nova's ring rolling out (server particles; the client draws its own heat rings too). */
	static void novaTick(ServerPlayer p, Rt r, long now) {
		long age = now - r.novaStart;
		if (age > FIRE_NOVA_RING_TICKS || r.novaAt == null) {
			r.novaStart = 0L;
			return;
		}
		ServerLevel level = IronManComicAccess.level(p);
		double rad = 0.6 + (FIRE_NOVA_RADIUS - 0.6) * age / FIRE_NOVA_RING_TICKS;
		int points = (int) (10 + rad * 8);
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			double x = r.novaAt.x + Math.cos(a) * rad, z = r.novaAt.z + Math.sin(a) * rad;
			level.sendParticles(ParticleTypes.FLAME, x, r.novaAt.y + 0.2, z, 1, 0.05, 0.15, 0.05, 0.01);
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.SMALL_FLAME, x, r.novaAt.y + 0.9, z, 1, 0.05, 0.2, 0.05, 0.01);
			}
		}
		if (age == FIRE_NOVA_RING_TICKS) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, r.novaAt.x, r.novaAt.y + 0.3, r.novaAt.z, 20, rad * 0.6, 0.1, rad * 0.6, 0.02);
		}
	}

	// ================================================================== X: rocket jump / rocket leap

	/** Straight up ~8 blocks on the boot rockets; fall-safe until you land. */
	private static boolean rocketJump(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Vec3 v = p.getDeltaMovement();
		IronManComicAccess.launch(p, new Vec3(v.x * 0.5, ROCKET_JUMP_VY, v.z * 0.5));
		rocketFx(p, suit, 1.0f);
		return true;
	}

	/** The Rocket Leap's launch for a look vector: a {@link #ROCKET_LEAP_BLOCKS} arc where you look (from the ground never down). */
	public static Vec3 leapVelocity(Vec3 look, boolean grounded) {
		Vec3 dir = look.normalize();
		if (grounded && dir.y < 0.0) {
			dir = new Vec3(dir.x, 0.0, dir.z);
		}
		if (dir.lengthSqr() < 1.0e-4) {
			dir = new Vec3(0, 1, 0);
		}
		return AbilityHelpers.ballisticLaunch(dir, ROCKET_LEAP_BLOCKS, grounded);
	}

	/** v0.17.7 (user): a long ~14-block arc aimed where you look, pitch included; fall-safe until you land. */
	private static boolean rocketLeap(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		IronManComicAccess.launch(p, leapVelocity(p.getLookAngle(), p.onGround()));
		rocketFx(p, suit, 0.85f);
		return true;
	}

	private static void rocketFx(ServerPlayer p, IronManSuit suit, float pitch) {
		Rt r = EraOneMk12State.rt(p, suit);
		long now = p.level().getGameTime();
		r.rocketStart = now;
		r.rocketUntil = now + ROCKET_SAFE_TICKS;
		p.resetFallDistance();
		ServerLevel level = IronManComicAccess.level(p);
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY(), p.getZ(), 24, 0.25, 0.05, 0.25, 0.06);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY(), p.getZ(), 14, 0.35, 0.05, 0.35, 0.03);
		level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
		IronManComicAccess.pose(p, IronManAbilityFx.COMIC_LAUNCH, 12);
		IronManComicAccess.sound(p, IronManSounds.FLIGHT_BURST, 1.1f, pitch);
		IronManSounds.play(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.6f);
	}

	// ================================================================== V: iron shell / scrap repair

	/** {@link #IRON_SHELL_TICKS}: 60% of every hit stopped, no knockback, rooted to the spot, projectiles bounce off. */
	private static boolean ironShell(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Rt r = EraOneMk12State.rt(p, suit);
		EraOneMk12State.startShell(p, r, p.level().getGameTime());
		ServerLevel level = IronManComicAccess.level(p);
		IronManComicAccess.groundRing(level, p.position(), 0.8, 10);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.6, 0.4, 0.1);
		IronManComicAccess.pose(p, IronManAbilityFx.MK1_IRON_SHELL, 12);
		IronManComicAccess.sound(p, IronManSounds.CLAMP, 1.2f, 0.5f);
		IronManComicAccess.sound(p, IronManSounds.SERVO, 1.0f, 0.5f);
		IronManSounds.play(p, SoundEvents.ANVIL_LAND, 0.6f, 0.7f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk1.shell"), true);
		return true;
	}

	/**
	 * Iron Shell: bounce every projectile near the wearer that is still flying at them -- turned back the way it came, slowed
	 * and kicked up, its owner unchanged. Returns how many. Public for the tests.
	 */
	public static int bounce(ServerPlayer p) {
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		int n = 0;
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(IRON_SHELL_BOUNCE_RADIUS))) {
			Entity owner = proj.getOwner();
			if (owner == p || proj.isRemoved() || proj instanceof AbstractArrow a && a.isNoPhysics()) {
				continue;
			}
			Vec3 v = proj.getDeltaMovement();
			Vec3 toMe = c.subtract(proj.position());
			if (v.lengthSqr() < 1.0e-4 || v.dot(toMe) <= 0) {
				continue; // already going away (or lying still)
			}
			Vec3 away = proj.position().subtract(c);
			away = new Vec3(away.x, 0, away.z);
			if (away.lengthSqr() < 1.0e-4) {
				away = v.scale(-1);
			}
			double speed = Math.max(0.4, v.length() * 0.6);
			proj.setDeltaMovement(away.normalize().scale(speed).add(0, 0.25, 0));
			proj.hurtMarked = true;
			proj.hasImpulse = true;
			level.sendParticles(ParticleTypes.CRIT, proj.getX(), proj.getY(), proj.getZ(), 8, 0.1, 0.1, 0.1, 0.2);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, proj.getX(), proj.getY(), proj.getZ(), 4, 0.1, 0.1, 0.1, 0.1);
			IronManSounds.play(p, SoundEvents.ANVIL_PLACE, 0.5f, 1.8f);
			n++;
		}
		return n;
	}

	/**
	 * Patch the suit in the field: one iron ingot from the inventory = +{@link #SCRAP_REPAIR_INTEGRITY} integrity (to the max).
	 * Refused -- no ingot, no cooldown, no energy -- with no iron ingot or the integrity already full. Works on a disabled suit too.
	 */
	private static boolean scrapRepair(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		float max = IronManEnergy.maxIntegrity(suit.id());
		float cur = IronManEnergy.integrity(p, suit.id());
		if (cur >= max - 0.01f) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk1.repair_full").withStyle(ChatFormatting.YELLOW), true);
			return false;
		}
		boolean free = p.getAbilities().instabuild;
		int slot = -1;
		for (int i = 0; i < p.getInventory().getContainerSize() && !free; i++) {
			if (p.getInventory().getItem(i).is(Items.IRON_INGOT)) {
				slot = i;
				break;
			}
		}
		if (!free && slot < 0) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk1.repair_no_iron").withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (!free) {
			p.getInventory().getItem(slot).shrink(1);
			p.getInventory().setChanged();
		}
		IronManEnergy.setIntegrity(p, suit.id(), Math.min(max, cur + SCRAP_REPAIR_INTEGRITY));
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 arm = p.position().add(0, p.getBbHeight() * 0.6, 0).add(flatLook(p).scale(0.45));
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, arm.x, arm.y, arm.z, 24, 0.25, 0.2, 0.25, 0.12);
		level.sendParticles(ParticleTypes.LAVA, arm.x, arm.y, arm.z, 3, 0.15, 0.1, 0.15, 0.0);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.IRON_INGOT)), arm.x, arm.y, arm.z, 8, 0.15, 0.15,
				0.15, 0.08);
		IronManComicAccess.pose(p, IronManAbilityFx.MK1_SCRAP_REPAIR, IronManAbilityFx.MK1_REPAIR_TICKS);
		IronManSounds.play(p, SoundEvents.ANVIL_USE, 1.0f, 0.9f);
		IronManSounds.play(p, SoundEvents.SMITHING_TABLE_USE, 0.8f, 1.0f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk1.repaired", Math.round(SCRAP_REPAIR_INTEGRITY)), true);
		return true;
	}

	// ================================================================== C: smoke screen (tap) / reactor overload (hold 1 s)

	/**
	 * The C key. The press marks the time; held {@link #OVERLOAD_HOLD_TICKS} the overload goes off right then (the tick does it,
	 * {@link #cHoldTick}); let go before that and it is the Smoke Screen. Only ever one of the two per press. Each keeps its own
	 * cooldown and energy (checked and paid here, so one cooling down never blocks the other). Always false for the router.
	 */
	private static boolean smokeOrOverload(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m, boolean pressed) {
		Rt r = EraOneMk12State.rt(p, suit);
		long now = p.level().getGameTime();
		if (pressed) {
			r.cDownAt = now;
			r.cHeld = true;
			r.cOverloadDone = false;
			return false;
		}
		if (!r.cHeld) {
			return false;
		}
		r.cHeld = false;
		if (r.cOverloadDone) {
			return false; // the hold already went off
		}
		if (now - r.cDownAt >= OVERLOAD_HOLD_TICKS) {
			tryOverload(p, suit);
		} else {
			trySmoke(p, suit);
		}
		return false;
	}

	/** While C is held: the charge pose from a few ticks in, and the overload the tick it reaches 1 s. */
	static void cHoldTick(ServerPlayer p, IronManSuit suit, Rt r, long now) {
		long held = now - r.cDownAt;
		if (r.cOverloadDone) {
			return;
		}
		if (held >= 4) {
			IronManAbilityFx.hold(p, IronManAbilityFx.MK1_OVERLOAD_CHARGE);
			if (held % 4 == 0) {
				Vec3 c = IronManComicAccess.chest(p);
				IronManComicAccess.level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 2 + (int) (held / 5), 0.15, 0.15, 0.15, 0.05);
			}
			if (held == 6) {
				IronManSounds.play(p, IronManSounds.REPULSOR_CHARGE, 0.6f, 0.8f);
			}
		}
		if (held >= OVERLOAD_HOLD_TICKS && suit != null) {
			r.cOverloadDone = true;
			tryOverload(p, suit);
		}
	}

	private static boolean cooledDown(ServerPlayer p, IronManSuit suit, String key) {
		if (TonyStark.abilityReady(p, suit.id(), key)) {
			return true;
		}
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.on_cooldown",
				String.format(java.util.Locale.ROOT, "%.1f", TonyStark.abilityCooldownRemaining(p, suit.id(), key) / 20.0f)), true);
		return false;
	}

	private static boolean trySmoke(ServerPlayer p, IronManSuit suit) {
		if (!cooledDown(p, suit, SMOKE_ID) || !IronManEraOne.canPayAmount(p, suit, SMOKE_ENERGY)) {
			return false;
		}
		smokeScreen(p, suit);
		TonyStark.triggerCooldown(p, suit.id(), SMOKE_ID, SMOKE_COOLDOWN_TICKS);
		IronManEraOne.payAmount(p, suit, SMOKE_ENERGY);
		return true;
	}

	private static boolean tryOverload(ServerPlayer p, IronManSuit suit) {
		if (!cooledDown(p, suit, OVERLOAD_ID) || !IronManEraOne.canPayAmount(p, suit, OVERLOAD_ENERGY)) {
			return false;
		}
		reactorOverload(p, suit, IronManComicKit.byId(SMOKE_ID));
		TonyStark.triggerCooldown(p, suit.id(), OVERLOAD_ID, OVERLOAD_COOLDOWN_TICKS);
		IronManEraOne.payAmount(p, suit, OVERLOAD_ENERGY);
		return true;
	}

	/** Gametests: C as a tap ({@code holdTicks} under 20) or a hold, released now. True when either move went off. */
	public static boolean cKeyForTests(ServerPlayer p, IronManSuit suit, int holdTicks) {
		Rt r = EraOneMk12State.rt(p, suit);
		r.cDownAt = p.level().getGameTime() - holdTicks;
		r.cHeld = false;
		r.cOverloadDone = false;
		return holdTicks >= OVERLOAD_HOLD_TICKS ? tryOverload(p, suit) : trySmoke(p, suit);
	}

	/** Gametests: arm the overload straight away (no cooldown, no energy). */
	public static void armOverloadForTests(ServerPlayer p, IronManSuit suit) {
		reactorOverload(p, suit, null);
	}

	/** Gametests: is C held down right now (pressed, not yet released)? */
	public static boolean cHeld(ServerPlayer p) {
		Rt r = EraOneMk12State.peek(p);
		return r != null && r.cHeld;
	}

	/** A 6 s smoke cloud (r 5): mobs inside lose you; while you stand in it nothing within 16 blocks keeps you targeted. */
	private static void smokeScreen(ServerPlayer p, IronManSuit suit) {
		ServerLevel level = IronManComicAccess.level(p);
		IronManComicAccess.smokeArea(p, p.position(), SMOKE_RADIUS, SMOKE_TICKS);
		Rt r = EraOneMk12State.rt(p, suit);
		r.smokeUntil = p.level().getGameTime() + SMOKE_TICKS;
		r.smokeAt = p.position();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16.0), e -> e.getTarget() == p)) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
		IronManComicAccess.cloud(level, p.position().add(0, 1, 0), ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, 30, 1.5, 0.02);
		IronManComicAccess.cloud(level, p.position().add(0, 0.5, 0), ParticleTypes.LARGE_SMOKE, 50, 2.0, 0.05);
		IronManComicAccess.pose(p, IronManAbilityFx.COMIC_FLARE, 10);
		IronManComicAccess.soundAt(level, p.position(), SoundEvents.FIRE_EXTINGUISH, 1.2f, 0.6f);
	}

	/** The next hit lands x2 (armed 10 s) -- on the Mk I, lighting the flamethrower instead gives 3 s of double flame. */
	static boolean reactorOverload(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Rt r = EraOneMk12State.rt(p, suit);
		long now = p.level().getGameTime();
		r.overloadUntil = now + EraOneMk12State.OVERLOAD_ARMED_TICKS;
		r.overloadFlameUntil = 0L;
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 c = IronManComicAccess.chest(p);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 0.4, 0.5, 0.4, 0.25);
		IronManComicAccess.ring(level, p.position().add(0, 1.0, 0), 1.2, ParticleTypes.FLAME, 20);
		IronManComicAccess.pose(p, IronManAbilityFx.COMIC_FLARE, 14);
		IronManComicAccess.sound(p, IronManSounds.POWER_UP, 1.0f, 0.7f);
		IronManComicAccess.soundAt(level, c, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.5f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk12.overload_armed"), true);
		return true;
	}

	// ================================================================== helpers

	static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : flat.normalize();
	}

	/** The ground at or below {@code at} (within 4 blocks), else {@code at}. */
	private static Vec3 groundAt(ServerPlayer p, Vec3 at) {
		var hit = p.level().clip(new net.minecraft.world.level.ClipContext(at.add(0, 1.0, 0), at.subtract(0, 4.0, 0),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, p));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? at : hit.getLocation();
	}

	/** The held / toggled move's per-second energy (the kit table's {@code energyPerSecond}). False = out of power. */
	static boolean drain(ServerPlayer p, IronManSuit suit, String moveId) {
		return IronManEraOne.drainTick(p, suit, moveId);
	}
}
