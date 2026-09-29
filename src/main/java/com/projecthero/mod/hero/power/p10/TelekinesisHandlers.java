package com.projecthero.mod.hero.power.p10;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.d.BatchDFx;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 10 — Telekinesis. v0.13.22 revamp (batch D): <b>juggling</b>.
 *
 * <h2>Psi</h2>
 * Every ability in the kit is paid for out of one shared <b>Psi</b> meter ({@link #MAX_PSI}, +15% in the revamp).
 * Psi refills on its own, but if it is driven all the way to empty the mind blows a fuse: for
 * {@link #BURNOUT_TICKS} ticks every telekinetic ability is locked out, every toggle drops, the orbit falls, and
 * the meter only starts refilling once that is over.
 *
 * <h2>The orbit</h2>
 * Up to {@link #ORBIT_MAX} grabbed objects -- creatures, dropped items, lifted blocks -- circle the telekinetic
 * at chest height, each costing a trickle of Psi per tick. V throws them one at a time at the crosshair, H
 * launches the whole orbit at once. Thrown objects are tracked and hit the first creature they reach.
 *
 * <h2>Slots</h2>
 * <ul>
 *   <li><b>R</b> Force Push (sneak: Force Pull -- creatures and loose items).</li>
 *   <li><b>G</b> Telekinetic Barrier -- untouchable while it burns Psi.</li>
 *   <li><b>X</b> Psychic Flight.</li>
 *   <li><b>Z</b> Psychic Detonation -- hold 5 s, haul everything in, blow it apart.</li>
 *   <li><b>V</b> Telekinetic Grab -- add the creature/item under the crosshair to the orbit; with the orbit full
 *       or nothing grabbable aimed at, throw one. Sneak: set the orbit down gently, or (empty) Force Crush.</li>
 *   <li><b>C</b> Block Manipulation -- hold to lift and steer, release to throw; a quick tap plucks the block
 *       into the orbit instead. Sneak: tear a 3x3 chunk out of the ground.</li>
 *   <li><b>H</b> Launch Orbit -- everything orbiting flies at the crosshair together.</li>
 *   <li><b>N</b> Mind Lock -- freeze a target in mid-air for 4 s.</li>
 * </ul>
 */
public final class TelekinesisHandlers {
	private static final String KEY = "power_10_telekinesis";
	public static final float MAX_PSI = 1150.0f;
	private static final float PSI_REGEN_PER_TICK = 1.1f;

	/** How long the power is dead for after the meter is emptied. */
	private static final int BURNOUT_TICKS = 10 * 20;
	/** Psi does not regenerate for this long after anything spends it (see the v0.10.14 notes). */
	private static final int REGEN_HOLDOFF_TICKS = 20;
	/** Continuous drains stop at this floor instead of emptying the meter. */
	private static final float SOFT_FLOOR = 12.0f;

	private static final int ULT_REGEN_PENALTY_TICKS = 10 * 20;
	private static final float ULT_REGEN_PENALTY = 0.2f;

	// --- costs ---
	private static final float COST_PUSH = 35.0f;
	private static final float COST_PULL = 25.0f;
	private static final float COST_GRAB = 55.0f;
	private static final float COST_GRAB_ITEM = 20.0f;
	private static final float COST_BLOCK = 40.0f;
	private static final float COST_CHUNK = 90.0f;
	private static final float COST_ULTIMATE = 320.0f;
	private static final float COST_LAUNCH = 30.0f;
	private static final float COST_MIND_LOCK = 90.0f;
	private static final float DRAIN_FLIGHT = 0.4f;
	private static final float DRAIN_BARRIER = 1.6f;
	private static final float DRAIN_BARRIER_PER_DAMAGE = 4.0f;
	private static final float DRAIN_HOLD = 1.0f;
	private static final float DRAIN_ORBIT_EACH = 0.45f;
	private static final float DRAIN_CRUSH = 2.5f;

	/** Every telekinetic move reaches 50 blocks. */
	static final double RANGE = 50.0;

	// --- R ---
	private static final float PUSH_DAMAGE = 12.0f;
	private static final double PUSH_SPLASH = 3.0;

	// --- Z: Psychic Detonation ---
	private static final int ULT_CHARGE_TICKS = 5 * 20;
	private static final int ULT_COOLDOWN = 76 * 20;
	private static final double ULT_RANGE = RANGE;
	private static final double ULT_MIN_DISTANCE = 2.0;
	private static final float ULT_DAMAGE = 66.0f;

	// --- V: Force Crush ---
	private static final int CRUSH_TICKS = 8 * 20;
	private static final float CRUSH_DAMAGE_PER_SECOND = 12.0f;
	private static final net.minecraft.resources.ResourceLocation CRUSH_SLOW =
			com.projecthero.mod.ProjectHeroMod.id("telekinesis_crush_slow");

	// --- the orbit (V / C tap / H) ---
	public static final int ORBIT_MAX = 3;
	private static final double ORBIT_RADIUS = 2.4;
	private static final float THROW_DAMAGE_CREATURE = 14.0f;
	private static final float THROW_DAMAGE_ITEM = 9.0f;
	private static final float THROW_DAMAGE_BLOCK = 16.0f;
	/** A creature thrown into something (or a wall) takes this itself. */
	private static final float THROWN_SELF_DAMAGE = 8.0f;
	private static final float LAUNCH_BONUS = 1.2f;
	private static final int THROWN_LIFE = 50;

	// --- N: Mind Lock ---
	public static final int MIND_LOCK_TICKS = 4 * 20;

	// --- C: Block Manipulation ---
	private static final int CHUNK_RADIUS = 1; // 3x3
	private static final float CHUNK_IMPACT_DAMAGE = 31.0f;
	private static final int CHUNK_FLIGHT_TICKS = 40;
	/** A C press released within this many ticks is a "tap": the block goes into the orbit instead of flying. */
	private static final int PLUCK_TAP_TICKS = 7;

	/** owner -> entity ids circling them (insertion order = oldest first). */
	private static final Map<UUID, List<Integer>> ORBIT = new HashMap<>();
	/** Objects in flight from a throw, until they hit something or run out of life. */
	private static final List<Thrown> THROWN = new ArrayList<>();
	/** locked entity id -> lock. */
	private static final Map<Integer, Lock> LOCKS = new HashMap<>();

	private TelekinesisHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	public static void clearSessionState() {
		ORBIT.clear();
		THROWN.clear();
		LOCKS.clear();
	}

	// ================================================================================ Psi

	private static boolean burntOut(AbilityContext ctx) {
		return ctx.resource("burnout_until") > ctx.player().level().getGameTime();
	}

	private static boolean spendPsi(AbilityContext ctx, float amount) {
		if (burntOut(ctx)) {
			burnoutMessage(ctx);
			return false;
		}
		if (ctx.resource("psi") < amount) {
			ctx.actionBar("message.projecthero.telekinesis.drained");
			return false;
		}
		ctx.addResource("psi", -amount, MAX_PSI);
		holdOffRegen(ctx.player());
		checkBurnout(ctx);
		return true;
	}

	private static boolean drainPsi(AbilityContext ctx, float amount) {
		if (burntOut(ctx)) {
			return false;
		}
		ctx.addResource("psi", -amount, MAX_PSI);
		holdOffRegen(ctx.player());
		return !checkBurnout(ctx);
	}

	private static boolean drainPsiSoft(AbilityContext ctx, float amount) {
		if (burntOut(ctx) || ctx.resource("psi") <= SOFT_FLOOR) {
			return false;
		}
		ctx.addResource("psi", -amount, MAX_PSI);
		holdOffRegen(ctx.player());
		return true;
	}

	private static void holdOffRegen(ServerPlayer p) {
		Power power = power();
		if (power != null) {
			ExperimentalPowers.setResource(p, power, "spend_until",
					p.level().getGameTime() + REGEN_HOLDOFF_TICKS, 1.0e12f);
		}
	}

	private static boolean checkBurnout(AbilityContext ctx) {
		if (ctx.resource("psi") > 0.0f) {
			return false;
		}
		startBurnout(ctx.player());
		return true;
	}

	private static void startBurnout(ServerPlayer p) {
		Power power = power();
		if (power == null || ExperimentalPowers.getResource(p, power, "burnout_until") > p.level().getGameTime()) {
			return;
		}
		ExperimentalPowers.setResource(p, power, "burnout_until",
				p.level().getGameTime() + BURNOUT_TICKS, 1.0e12f);
		ExperimentalPowers.setResource(p, power, "burnout_left", BURNOUT_TICKS, BURNOUT_TICKS);
		// Everything currently running stops dead: the mind has nothing left to hold any of it up.
		for (com.projecthero.mod.hero.Ability a : power.abilities()) {
			ExperimentalPowers.setToggled(p, power, a, false);
		}
		HeroFlight.setFlying(p, false);
		endCrush(p);
		clearChunk(p);
		releaseOrbit(p, true);
		ExperimentalPowers.setResource(p, power, "ult_start", 0, 1.0e12f);
		ExperimentalPowers.setResource(p, power, "ult_charge", 0, 100);
		MutationVisuals.stop(p);
		p.displayClientMessage(Component.translatable("message.projecthero.telekinesis.burnout")
				.withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE,
				net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);
	}

	private static void burnoutMessage(AbilityContext ctx) {
		long left = (long) ctx.resource("burnout_until") - ctx.player().level().getGameTime();
		ctx.actionBar("message.projecthero.telekinesis.burnout_wait",
				String.format(Locale.ROOT, "%.0f", Math.ceil(Math.max(0L, left) / 20.0)));
	}

	// ================================================================================ registration

	public static void register() {
		// ---- R: Force Push, sneak = Force Pull -----------------------------------------------------
		AbilityHandlers.register(KEY, "force_push", Handlers.instant(ctx -> {
			if (ctx.player().isShiftKeyDown()) {
				forcePull(ctx);
			} else {
				forcePush(ctx);
			}
		}));

		// ---- G: Telekinetic Barrier ----------------------------------------------------------------
		AbilityHandlers.register(KEY, "telekinetic_barrier", Handlers.toggle(
				ctx -> {
					if (burntOut(ctx)) {
						ctx.setToggled(false);
						burnoutMessage(ctx);
						return;
					}
					if (ctx.resource("psi") < DRAIN_BARRIER * 20.0f) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.telekinesis.drained");
						return;
					}
					ctx.actionBar("message.projecthero.telekinesis.barrier_up");
					MutationVisuals.play(ctx.player(), "p10.barrier");
					AbilityHelpers.sound(ctx.player(), SoundEvents.SHIELD_BLOCK, 1.0f, 0.5f);
				},
				ctx -> AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.8f, 0.7f),
				ctx -> {
					if (!drainPsi(ctx, DRAIN_BARRIER)) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.telekinesis.drained");
						return;
					}
					ServerPlayer p = ctx.player();
					if (p.tickCount % 2 == 0) {
						for (int i = 0; i < 4; i++) {
							double a = (p.tickCount * 0.12) + i * Math.PI / 2.0;
							double h = (i % 2 == 0) ? 0.4 : 1.4;
							ctx.level().sendParticles(ParticleTypes.SCULK_SOUL,
									p.getX() + Math.cos(a) * 1.1, p.getY() + h, p.getZ() + Math.sin(a) * 1.1,
									1, 0.0, 0.0, 0.0, 0.0);
							ctx.level().sendParticles(BatchDFx.PSI,
									p.getX() + Math.cos(a + 0.8) * 1.15, p.getY() + 0.9, p.getZ() + Math.sin(a + 0.8) * 1.15,
									1, 0.0, 0.3, 0.0, 0.0);
						}
					}
				}));

		// ---- X: Psychic Flight ---------------------------------------------------------------------
		AbilityHandlers.register(KEY, "psychic_flight", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				if (burntOut(ctx) || !HeroFlight.startFlying(ctx.player(), ctx.power())) {
					ctx.setToggled(false);
					if (burntOut(ctx)) {
						burnoutMessage(ctx);
					}
					return;
				}
				MutationVisuals.play(ctx.player(), "leap");
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				HeroFlight.setFlying(ctx.player(), false);
				MutationVisuals.stopIf(ctx.player(), "float_arms");
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (!HeroFlight.isFlying(p)) {
					ctx.setToggled(false);
					MutationVisuals.stopIf(p, "float_arms");
					return;
				}
				if (!drainPsiSoft(ctx, DRAIN_FLIGHT)) {
					ctx.setToggled(false);
					HeroFlight.setFlying(p, false);
					MutationVisuals.stopIf(p, "float_arms");
					ctx.actionBar("message.projecthero.telekinesis.drained");
					return;
				}
				BatchDFx.ensureIdle(p, "float_arms");
				ctx.level().sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
				if (p.tickCount % 3 == 0) {
					ctx.level().sendParticles(BatchDFx.PSI, p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.35, 0.05, 0.35, 0.0);
				}
			}
		});

		// ---- Z: Psychic Detonation -----------------------------------------------------------------
		AbilityHandlers.register(KEY, "telekinetic_explosion", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ultPress(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ultRelease(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ultChargeTick(ctx);
			}
		});

		// ---- V: Telekinetic Grab (orbit) / Force Crush ---------------------------------------------
		AbilityHandlers.register(KEY, "telekinetic_grab", Handlers.instantTicking(
				TelekinesisHandlers::grabPress, TelekinesisHandlers::crushTick));

		// ---- C: Block Manipulation -----------------------------------------------------------------
		AbilityHandlers.register(KEY, "block_manipulation", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.player().isShiftKeyDown()) {
					grabChunk(ctx);
				} else {
					grabSingleBlock(ctx);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				releaseBlocks(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				heldBlockTick(ctx);
				thrownChunkTick(ctx);
			}
		});

		// ---- H: Launch Orbit -----------------------------------------------------------------------
		AbilityHandlers.register(KEY, "launch_orbit", Handlers.instant(TelekinesisHandlers::launchOrbit));

		// ---- N: Mind Lock --------------------------------------------------------------------------
		AbilityHandlers.register(KEY, "mind_lock", Handlers.instant(TelekinesisHandlers::mindLock));

		registerPassives();
	}

	// ================================================================================ R

	private static void forcePush(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		Vec3 eye = p.getEyePosition();
		LivingEntity aimed = AbilityHelpers.raycastEntity(p, RANGE);
		if (aimed == null) {
			ctx.actionBar("message.projecthero.telekinesis.no_target");
			return;
		}
		if (!spendPsi(ctx, COST_PUSH)) {
			return;
		}
		Vec3 focus = aimed.position();
		java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
		hit.add(aimed);
		hit.addAll(AbilityHelpers.enemiesAround(p, focus, PUSH_SPLASH));
		for (LivingEntity e : hit) {
			if (Squads.areAllies(p, e)) {
				continue;
			}
			Vec3 dir = e.position().subtract(eye).normalize().scale(2.0).add(0, 0.5, 0);
			AbilityHelpers.push(e, dir);
			AbilityHelpers.knockbackFrom(e, p.position(), 1.6);
			AbilityHelpers.hurt(p, e, PUSH_DAMAGE);
		}
		Vec3 mid = focus.add(0, aimed.getBbHeight() * 0.5, 0);
		AbilityHelpers.line(ctx.level(), eye, mid, ParticleTypes.SCULK_SOUL, 2.0);
		AbilityHelpers.line(ctx.level(), AbilityHelpers.handPosition(p), mid, BatchDFx.PSI, 1.5);
		AbilityHelpers.burst(ctx.level(), mid, ParticleTypes.SCULK_SOUL, 20, PUSH_SPLASH);
		ctx.level().sendParticles(BatchDFx.PSI_BIG, mid.x, mid.y, mid.z, 16, 0.6, 0.6, 0.6, 0.0);
		MutationVisuals.play(p, "cast_right");
		AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 0.7f);
		ctx.triggerCooldown();
	}

	private static void forcePull(AbilityContext ctx) {
		if (!spendPsi(ctx, COST_PULL)) {
			return;
		}
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, RANGE);
		if (t != null && !Squads.areAllies(p, t)) {
			Vec3 dir = p.position().subtract(t.position()).normalize().scale(1.6).add(0, 0.3, 0);
			AbilityHelpers.push(t, dir);
		}
		int items = 0;
		for (ItemEntity item : ctx.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(RANGE))) {
			if (inAnyOrbit(item.getId())) {
				continue;
			}
			Vec3 to = p.position().add(0, 0.3, 0).subtract(item.position());
			double dist = to.length();
			if (dist > RANGE) {
				continue;
			}
			if (dist < 1.5) {
				item.setNoPickUpDelay();
			} else {
				item.setDeltaMovement(to.normalize().scale(Math.min(1.4, 0.35 + dist * 0.08)));
				item.hasImpulse = true;
			}
			items++;
		}
		AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, RANGE), ParticleTypes.SCULK_SOUL, 3.0);
		MutationVisuals.play(p, "grab_pull");
		AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 1.3f);
		if (t == null && items == 0) {
			ctx.actionBar("message.projecthero.telekinesis.nothing_to_pull");
		}
		ctx.triggerCooldown();
	}

	// ================================================================================ Z

	private static void ultPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("ult_start") > 0.5f) {
			return;
		}
		if (burntOut(ctx)) {
			burnoutMessage(ctx);
			return;
		}
		if (!ctx.cooldownReady()) {
			ctx.actionBar("message.projecthero.ability.on_cooldown",
					Component.translatable(ctx.ability().nameKey()),
					String.format(Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
			return;
		}
		if (ctx.resource("psi") < COST_ULTIMATE) {
			ctx.actionBar("message.projecthero.telekinesis.drained");
			return;
		}
		ctx.setResource("ult_start", p.level().getGameTime(), 1.0e12f);
		ctx.setResource("ult_charge", 0, 100);
		MutationVisuals.play(p, "p10.detonate_charge");
		AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.6f);
	}

	private static void ultRelease(AbilityContext ctx) {
		if (ctx.resource("ult_start") <= 0.5f) {
			return;
		}
		long held = ctx.player().level().getGameTime() - (long) ctx.resource("ult_start");
		if (held >= ULT_CHARGE_TICKS) {
			ultFire(ctx);
		} else {
			ultCancel(ctx, true);
		}
	}

	private static void ultChargeTick(AbilityContext ctx) {
		float start = ctx.resource("ult_start");
		if (start <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		long held = p.level().getGameTime() - (long) start;
		if (held < 0 || held > ULT_CHARGE_TICKS + 100 || burntOut(ctx)) {
			ultCancel(ctx, true);
			return;
		}
		double frac = Math.min(1.0, held / (double) ULT_CHARGE_TICKS);
		ctx.setResource("ult_charge", (float) (frac * 100.0), 100);
		MutationVisuals.ensure(p, "p10.detonate_charge");

		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), ULT_RANGE)) {
			if (Squads.areAllies(p, e)) {
				continue;
			}
			Vec3 toCaster = p.position().subtract(e.position());
			double dist = toCaster.length();
			double lift = e.onGround() ? 0.42 : 0.09;
			Vec3 pull = dist > ULT_MIN_DISTANCE
					? toCaster.normalize().scale(Math.min(0.35, 0.05 + dist * 0.02))
					: toCaster.normalize().scale(-0.18);
			e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull.x, lift, pull.z));
			e.hurtMarked = true;
			e.fallDistance = 0.0f;
			e.hasImpulse = true;
		}

		int ring = 6 + (int) (frac * 26);
		for (int i = 0; i < ring; i++) {
			double a = ctx.level().random.nextDouble() * Math.PI * 2;
			double r = ULT_MIN_DISTANCE + ctx.level().random.nextDouble() * (ULT_RANGE - ULT_MIN_DISTANCE) * frac;
			ctx.level().sendParticles(ParticleTypes.SCULK_SOUL,
					p.getX() + Math.cos(a) * r, p.getY() + 0.6 + ctx.level().random.nextDouble() * 2.0,
					p.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		BatchDFx.ring(ctx.level(), p.position().add(0, 1.0, 0), 1.2 + frac, BatchDFx.PSI, 10, held * 0.3);
		if (held % 10 == 0) {
			AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.1f, 0.5f + (float) frac);
		}
		if (held >= ULT_CHARGE_TICKS) {
			ultFire(ctx);
		}
	}

	private static void ultCancel(AbilityContext ctx, boolean announce) {
		ctx.setResource("ult_start", 0, 1.0e12f);
		ctx.setResource("ult_charge", 0, 100);
		MutationVisuals.stopIf(ctx.player(), "p10.detonate_charge");
		if (announce) {
			AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.7f, 0.8f);
		}
	}

	private static void ultFire(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		ultCancel(ctx, false);
		if (!spendPsi(ctx, COST_ULTIMATE)) {
			return;
		}
		ctx.setResource("regen_slow_until", p.level().getGameTime() + ULT_REGEN_PENALTY_TICKS, 1.0e12f);

		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), ULT_RANGE)) {
			if (Squads.areAllies(p, e)) {
				continue;
			}
			Vec3 dir = e.position().subtract(p.position()).normalize().scale(2.8).add(0, 0.6, 0);
			AbilityHelpers.push(e, dir);
			AbilityHelpers.hurtBurst(p, e, ULT_DAMAGE);
		}
		for (int i = 0; i < 5; i++) {
			double r = ULT_RANGE * (i + 1) / 5.0;
			level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, p.getX(), p.getY() + 1, p.getZ(),
					40, r / 2.0, 1.0, r / 2.0, 0.4);
		}
		level.sendParticles(BatchDFx.PSI_BIG, p.getX(), p.getY() + 1, p.getZ(), 60, 3.0, 1.2, 3.0, 0.0);
		level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
		MutationVisuals.play(p, "p10.detonate");
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 1.2f);
		AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.4f, 0.6f);
		ctx.triggerCooldown(ULT_COOLDOWN);
	}

	// ================================================================================ V: the orbit

	/** The ids currently orbiting {@code p} (live list; never null). */
	private static List<Integer> orbit(ServerPlayer p) {
		return ORBIT.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
	}

	public static int orbitSize(ServerPlayer p) {
		List<Integer> l = ORBIT.get(p.getUUID());
		return l == null ? 0 : l.size();
	}

	private static boolean inAnyOrbit(int id) {
		for (List<Integer> l : ORBIT.values()) {
			if (l.contains(id)) {
				return true;
			}
		}
		return LOCKS.containsKey(id);
	}

	private static void grabPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		boolean sneaking = p.isShiftKeyDown();

		if (ctx.resource("crush_id") > 0.5f) {
			endCrush(p);
			return;
		}
		List<Integer> orbit = orbit(p);
		if (sneaking) {
			if (!orbit.isEmpty()) {
				releaseOrbit(p, false);
				ctx.actionBar("message.projecthero.telekinesis.set_down");
				AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
				MutationVisuals.play(p, "p10.set_down");
				ctx.triggerCooldown();
				return;
			}
			startCrush(ctx);
			return;
		}
		if (burntOut(ctx)) {
			burnoutMessage(ctx);
			return;
		}
		Entity target = orbit.size() < ORBIT_MAX ? grabbableUnderCrosshair(p) : null;
		if (target != null) {
			float cost = target instanceof ItemEntity ? COST_GRAB_ITEM : COST_GRAB;
			if (!spendPsi(ctx, cost)) {
				return;
			}
			addToOrbit(p, target);
			ctx.actionBar("message.projecthero.telekinesis.orbit_add", orbit.size(), ORBIT_MAX);
			MutationVisuals.play(p, "grab_pull");
			AbilityHelpers.line(ctx.level(), AbilityHelpers.handPosition(p), BatchDFx.centre(target), BatchDFx.PSI, 2.0);
			AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.9f, 1.3f);
			ctx.triggerCooldown();
			return;
		}
		if (!orbit.isEmpty()) {
			throwOne(p, AbilityHelpers.aimPoint(p, RANGE), 1.0f);
			MutationVisuals.play(p, "throw_right");
			AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 0.6f);
			ctx.triggerCooldown();
			return;
		}
		ctx.actionBar("message.projecthero.telekinesis.no_target");
	}

	/** The creature or loose item under the crosshair that can join the orbit, or null. */
	private static Entity grabbableUnderCrosshair(ServerPlayer p) {
		LivingEntity le = AbilityHelpers.raycastEntity(p, RANGE);
		if (le != null && AbilityHelpers.isValidGrabTarget(le, p) && !Squads.areAllies(p, le) && !inAnyOrbit(le.getId())
				&& !(le instanceof com.projecthero.mod.hero.revamp.d.MirrorImageEntity)) {
			return le;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(RANGE));
		AABB box = p.getBoundingBox().expandTowards(p.getLookAngle().scale(RANGE)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
				e -> e instanceof ItemEntity ie && ie.isAlive() && !inAnyOrbit(ie.getId()), 0.6f);
		if (hit != null && AbilityHelpers.raycastBlock(p, RANGE).getLocation().distanceToSqr(eye)
				+ 1.0 >= hit.getLocation().distanceToSqr(eye)) {
			return hit.getEntity();
		}
		return null;
	}

	/** Puts {@code e} into {@code p}'s orbit (public for the C-tap pluck and tests). */
	public static void addToOrbit(ServerPlayer p, Entity e) {
		List<Integer> orbit = orbit(p);
		if (orbit.size() >= ORBIT_MAX || orbit.contains(e.getId())) {
			return;
		}
		orbit.add(e.getId());
		if (e instanceof ItemEntity ie) {
			ie.setNoGravity(true);
			ie.setPickUpDelay(32767);
		} else if (e instanceof FallingBlockEntity fb) {
			fb.setNoGravity(true);
			fb.time = 1;
		} else if (e instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		syncOrbitCount(p);
	}

	private static void syncOrbitCount(ServerPlayer p) {
		Power power = power();
		if (power != null && ExperimentalPowers.owns(p, power)) {
			ExperimentalPowers.setResource(p, power, "orbit", orbitSize(p), ORBIT_MAX);
		}
	}

	/** Lets everything in the orbit go gently (no damage): creatures float down, items become pick-up-able again. */
	public static void releaseOrbit(ServerPlayer p, boolean announce) {
		List<Integer> orbit = ORBIT.remove(p.getUUID());
		if (orbit == null || !(p.level() instanceof ServerLevel level)) {
			return;
		}
		for (int id : orbit) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive()) {
				setDown(level, e);
			}
		}
		syncOrbitCount(p);
		if (announce && !orbit.isEmpty()) {
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8f, 0.6f);
		}
	}

	private static void setDown(ServerLevel level, Entity e) {
		if (e instanceof ItemEntity ie) {
			ie.setNoGravity(false);
			ie.setPickUpDelay(10);
			ie.setDeltaMovement(0, -0.05, 0);
		} else if (e instanceof FallingBlockEntity fb) {
			fb.setNoGravity(false);
			fb.setDeltaMovement(0, -0.1, 0);
		} else if (e instanceof LivingEntity le) {
			le.setDeltaMovement(0, -0.08, 0);
			le.fallDistance = 0.0f;
			le.hurtMarked = true;
			le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, false));
		}
		level.sendParticles(BatchDFx.PSI, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.3, 0.3, 0.3, 0.0);
	}

	/** Flings the orbiter nearest the look direction at {@code aim}. Returns false if the orbit was empty. */
	public static boolean throwOne(ServerPlayer p, Vec3 aim, float damageMult) {
		List<Integer> orbit = ORBIT.get(p.getUUID());
		if (orbit == null || orbit.isEmpty() || !(p.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 look = p.getLookAngle();
		int bestIdx = 0;
		double best = -2;
		for (int i = 0; i < orbit.size(); i++) {
			Entity e = level.getEntity(orbit.get(i));
			if (e == null) {
				continue;
			}
			double dot = e.position().subtract(p.position()).normalize().dot(look);
			if (dot > best) {
				best = dot;
				bestIdx = i;
			}
		}
		int id = orbit.remove(bestIdx);
		syncOrbitCount(p);
		Entity e = level.getEntity(id);
		if (e == null || !e.isAlive()) {
			return true;
		}
		launch(level, p, e, aim, damageMult);
		return true;
	}

	private static void launch(ServerLevel level, ServerPlayer p, Entity e, Vec3 aim, float damageMult) {
		Vec3 from = BatchDFx.centre(e);
		Vec3 dir = aim.subtract(from);
		dir = dir.lengthSqr() < 1.0e-4 ? p.getLookAngle() : dir.normalize();
		float damage;
		double speed;
		if (e instanceof ItemEntity ie) {
			ie.setNoGravity(true);
			ie.setPickUpDelay(40);
			speed = 2.6;
			damage = THROW_DAMAGE_ITEM;
		} else if (e instanceof FallingBlockEntity fb) {
			fb.setNoGravity(false);
			fb.time = 1;
			fb.setHurtsEntities(2.0f, 20);
			speed = 2.1;
			damage = THROW_DAMAGE_BLOCK;
		} else {
			speed = 2.2;
			damage = THROW_DAMAGE_CREATURE;
		}
		e.setDeltaMovement(dir.scale(speed));
		e.hasImpulse = true;
		e.hurtMarked = true;
		if (e instanceof LivingEntity le) {
			le.fallDistance = 0.0f;
		}
		broadcastPos(level, e);
		THROWN.add(new Thrown(level, e, p.getUUID(), damage * damageMult, THROWN_LIFE));
		AbilityHelpers.line(level, from, from.add(dir.scale(3.0)), BatchDFx.PSI, 3.0);
	}

	/** Per-tick upkeep of every orbit, every throw and every Mind Lock (from {@code RevampBatchD.serverTick}). */
	public static void worldTick(MinecraftServer server) {
		if (!ORBIT.isEmpty()) {
			Iterator<Map.Entry<UUID, List<Integer>>> it = ORBIT.entrySet().iterator();
			List<ServerPlayer> dropAll = new ArrayList<>();
			List<ServerPlayer> burnouts = new ArrayList<>();
			while (it.hasNext()) {
				var entry = it.next();
				if (entry.getValue().isEmpty()) {
					continue;
				}
				ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
				Power power = power();
				if (p == null || !p.isAlive() || power == null || !ExperimentalPowers.owns(p, power)) {
					if (p != null) {
						dropAll.add(p);
					} else {
						it.remove(); // owner logged out: the objects simply stop being held
					}
					continue;
				}
				if (!tickOrbit(p, entry.getValue())) {
					burnouts.add(p);
				}
			}
			for (ServerPlayer p : dropAll) {
				releaseOrbit(p, false);
			}
			for (ServerPlayer p : burnouts) {
				startBurnout(p);
			}
		}
		if (!THROWN.isEmpty()) {
			THROWN.removeIf(t -> !t.tick(server));
		}
		if (!LOCKS.isEmpty()) {
			LOCKS.entrySet().removeIf(e -> !e.getValue().tick(server, e.getKey()));
		}
	}

	/** @return false when holding the orbit just emptied the Psi meter (the caller trips the burnout). */
	private static boolean tickOrbit(ServerPlayer p, List<Integer> orbit) {
		ServerLevel level = p.serverLevel();
		boolean lost = orbit.removeIf(id -> {
			Entity e = level.getEntity(id);
			return e == null || !e.isAlive() || e.distanceToSqr(p) > 40 * 40;
		});
		if (lost) {
			syncOrbitCount(p);
		}
		int n = orbit.size();
		if (n == 0) {
			return true;
		}
		Power power = power();
		// the Psi cost of holding them all aloft
		ExperimentalPowers.addResource(p, power, "psi", -DRAIN_ORBIT_EACH * n, MAX_PSI);
		holdOffRegen(p);
		if (ExperimentalPowers.getResource(p, power, "psi") <= 0.0f) {
			return false;
		}
		double t = level.getGameTime() * 0.16;
		Vec3 c = p.position().add(0, 1.25, 0);
		for (int i = 0; i < n; i++) {
			Entity e = level.getEntity(orbit.get(i));
			double a = t + i * (Math.PI * 2.0 / n);
			double r = ORBIT_RADIUS + e.getBbWidth() * 0.5;
			double y = c.y + Math.sin(t * 0.7 + i * 2.1) * 0.25 - e.getBbHeight() * 0.5;
			Vec3 at = new Vec3(c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r);
			if (e instanceof ServerPlayer sp) {
				sp.connection.teleport(at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
			} else {
				e.setPos(at.x, at.y, at.z);
			}
			e.setDeltaMovement(Vec3.ZERO);
			e.fallDistance = 0.0f;
			if (e instanceof FallingBlockEntity fb) {
				fb.time = 1;
				fb.setNoGravity(true);
				broadcastPos(level, fb);
			} else if (e instanceof ItemEntity ie) {
				ie.setNoGravity(true);
				ie.setPickUpDelay(32767);
				broadcastPos(level, ie);
			} else if (e instanceof LivingEntity le) {
				le.hurtMarked = true;
				if (le instanceof Mob mob) {
					mob.getNavigation().stop();
				}
			}
			if (p.tickCount % 2 == 0) {
				level.sendParticles(BatchDFx.PSI, at.x, at.y + e.getBbHeight() * 0.5, at.z, 2, 0.2, 0.2, 0.2, 0.0);
			}
		}
		if (p.tickCount % 5 == 0) {
			BatchDFx.ring(level, c, ORBIT_RADIUS, ParticleTypes.SCULK_SOUL, 6, t);
		}
		return true;
	}

	private static void broadcastPos(ServerLevel level, Entity e) {
		level.getChunkSource().broadcastAndSend(e, new ClientboundTeleportEntityPacket(e));
	}

	/** One thrown orbit object in flight. */
	private static final class Thrown {
		private final ServerLevel level;
		private final Entity entity;
		private final UUID owner;
		private final float damage;
		private int life;
		private int age;

		Thrown(ServerLevel level, Entity entity, UUID owner, float damage, int life) {
			this.level = level;
			this.entity = entity;
			this.owner = owner;
			this.damage = damage;
			this.life = life;
		}

		boolean tick(MinecraftServer server) {
			age++;
			ServerPlayer p = server.getPlayerList().getPlayer(owner);
			if (!entity.isAlive() || --life <= 0 || p == null || entity.level() != level) {
				settle();
				return false;
			}
			if (age % 2 == 0) {
				level.sendParticles(BatchDFx.PSI, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
						2, 0.1, 0.1, 0.1, 0.0);
			}
			if (entity instanceof ItemEntity ie && ie.getDeltaMovement().lengthSqr() < 0.05) {
				settle();
				return false;
			}
			for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(0.6),
					x -> x.isAlive() && x != entity && x != p && !(x instanceof ArmorStand) && !Squads.areAllies(p, x))) {
				AbilityHelpers.hurtBurst(p, le, damage);
				AbilityHelpers.knockbackFrom(le, entity.position(), 1.2);
				if (entity instanceof LivingEntity thrown) {
					AbilityHelpers.hurtBurst(p, thrown, THROWN_SELF_DAMAGE);
				}
				level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(),
						16, 0.4, 0.4, 0.4, 0.1);
				level.sendParticles(BatchDFx.PSI_BIG, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(),
						8, 0.4, 0.4, 0.4, 0.0);
				level.playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
						net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f);
				settle();
				return false;
			}
			if (entity instanceof LivingEntity thrown && age > 3 && (thrown.horizontalCollision || thrown.onGround())) {
				if (thrown.horizontalCollision) {
					AbilityHelpers.hurtBurst(p, thrown, THROWN_SELF_DAMAGE);
				}
				settle();
				return false;
			}
			return true;
		}

		private void settle() {
			if (entity instanceof ItemEntity ie && ie.isAlive()) {
				ie.setNoGravity(false);
				ie.setPickUpDelay(10);
			}
		}
	}

	// ================================================================================ H

	private static void launchOrbit(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (orbitSize(p) == 0) {
			ctx.actionBar("message.projecthero.telekinesis.orbit_empty");
			return;
		}
		if (!spendPsi(ctx, COST_LAUNCH)) {
			return;
		}
		Vec3 aim = AbilityHelpers.aimPoint(p, RANGE);
		int n = 0;
		while (throwOne(p, aim, LAUNCH_BONUS)) {
			n++;
		}
		ctx.level().sendParticles(BatchDFx.PSI_BIG, p.getX(), p.getY() + 1.2, p.getZ(), 20 + n * 6, 1.2, 0.6, 1.2, 0.0);
		MutationVisuals.play(p, "p10.launch");
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.6f);
		ctx.triggerCooldown();
	}

	// ================================================================================ N

	private static void mindLock(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, RANGE);
		if (t == null || !AbilityHelpers.isValidGrabTarget(t, p) || Squads.areAllies(p, t) || inAnyOrbit(t.getId())) {
			ctx.actionBar("message.projecthero.telekinesis.no_target");
			return;
		}
		if (!spendPsi(ctx, COST_MIND_LOCK)) {
			return;
		}
		lock(p, t);
		MutationVisuals.play(p, "p10.mind_lock");
		AbilityHelpers.line(ctx.level(), p.getEyePosition(), BatchDFx.centre(t), BatchDFx.PSI, 2.0);
		AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 0.8f);
		ctx.triggerCooldown();
	}

	/** Freezes {@code t} 1.5 blocks up for {@link #MIND_LOCK_TICKS} (public for tests). */
	public static void lock(ServerPlayer owner, LivingEntity t) {
		Vec3 anchor = t.position().add(0, 1.5, 0);
		LOCKS.put(t.getId(), new Lock((ServerLevel) t.level(), owner.getUUID(), anchor, MIND_LOCK_TICKS));
	}

	public static boolean isLocked(LivingEntity t) {
		return LOCKS.containsKey(t.getId());
	}

	private static boolean locksSomething(ServerPlayer p) {
		for (Lock l : LOCKS.values()) {
			if (l.owner.equals(p.getUUID())) {
				return true;
			}
		}
		return false;
	}

	private static final class Lock {
		private final ServerLevel level;
		private final UUID owner;
		private final Vec3 anchor;
		private int left;

		Lock(ServerLevel level, UUID owner, Vec3 anchor, int left) {
			this.level = level;
			this.owner = owner;
			this.anchor = anchor;
			this.left = left;
		}

		boolean tick(MinecraftServer server, int id) {
			if (!(level.getEntity(id) instanceof LivingEntity le) || !le.isAlive() || --left <= 0) {
				if (level.getEntity(id) instanceof LivingEntity le2 && le2.isAlive()) {
					le2.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, false));
					level.sendParticles(BatchDFx.PSI, le2.getX(), le2.getY() + 1.0, le2.getZ(), 16, 0.4, 0.5, 0.4, 0.0);
				}
				return false;
			}
			// the rise into the lock, then held perfectly still
			Vec3 cur = le.position();
			Vec3 next = cur.add(anchor.subtract(cur).scale(0.35));
			if (le instanceof ServerPlayer sp) {
				sp.connection.teleport(next.x, next.y, next.z, sp.getYRot(), sp.getXRot());
			} else {
				le.setPos(next.x, next.y, next.z);
			}
			le.setDeltaMovement(Vec3.ZERO);
			le.fallDistance = 0.0f;
			le.hurtMarked = true;
			if (le instanceof Mob mob) {
				mob.getNavigation().stop();
				mob.setTarget(null);
			}
			if (left % 10 == 0) {
				AbilityHelpers.applyControl(le, MobEffects.MOVEMENT_SLOWDOWN, 20, 9);
				AbilityHelpers.applyControl(le, MobEffects.WEAKNESS, 20, 9);
			}
			if (left % 2 == 0) {
				BatchDFx.ring(level, le.position().add(0, le.getBbHeight() * 0.5, 0), le.getBbWidth() * 0.8 + 0.4,
						BatchDFx.PSI, 8, left * 0.25);
			}
			return true;
		}
	}

	// ================================================================================ V sneak: Force Crush

	private static void startCrush(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity target = AbilityHelpers.raycastEntity(p, RANGE);
		if (target == null || !AbilityHelpers.isValidGrabTarget(target, p) || Squads.areAllies(p, target)) {
			ctx.actionBar("message.projecthero.telekinesis.no_target");
			return;
		}
		if (!spendPsi(ctx, COST_GRAB)) {
			return;
		}
		ctx.setResource("crush_id", target.getId(), 1.0e9f);
		ctx.setResource("crush_ticks", CRUSH_TICKS, CRUSH_TICKS);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, CRUSH_SLOW, -0.85,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		MutationVisuals.play(p, "p10.crush");
		ctx.actionBar("message.projecthero.telekinesis.crush");
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 1.2f);
	}

	private static void endCrush(ServerPlayer p) {
		Power power = power();
		if (power == null) {
			return;
		}
		if (ExperimentalPowers.getResource(p, power, "crush_id") > 0.5f) {
			MutationVisuals.stopIf(p, "p10.crush");
		}
		ExperimentalPowers.setResource(p, power, "crush_id", 0, 1.0e9f);
		ExperimentalPowers.setResource(p, power, "crush_ticks", 0, CRUSH_TICKS);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, CRUSH_SLOW);
	}

	private static void crushTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		int crushId = (int) ctx.resource("crush_id");
		if (crushId == 0) {
			return;
		}
		float left = ctx.resource("crush_ticks") - 1.0f;
		if (!(p.level().getEntity(crushId) instanceof LivingEntity victim) || !victim.isAlive()
				|| left <= 0.0f || p.distanceToSqr(victim) > RANGE * RANGE) {
			endCrush(p);
			return;
		}
		if (!drainPsi(ctx, DRAIN_CRUSH)) {
			endCrush(p);
			ctx.actionBar("message.projecthero.telekinesis.drained");
			return;
		}
		ctx.setResource("crush_ticks", left, CRUSH_TICKS);
		MutationVisuals.ensure(p, "p10.crush");
		Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(3.5));
		Vec3 to = hold.subtract(victim.position().add(0, victim.getBbHeight() * 0.5, 0));
		victim.setDeltaMovement(to.scale(0.35));
		victim.fallDistance = 0.0f;
		victim.hurtMarked = true;
		victim.hasImpulse = true;
		if ((int) left % 20 == 0) {
			AbilityHelpers.hurt(p, victim, CRUSH_DAMAGE_PER_SECOND);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 1.6f);
		}
		ctx.level().sendParticles(ParticleTypes.SCULK_CHARGE_POP,
				victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 6, 0.4, 0.4, 0.4, 0.05);
		ctx.level().sendParticles(BatchDFx.PSI,
				victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 4, 0.35, 0.35, 0.35, 0.0);
	}

	// ================================================================================ C

	private static void grabSingleBlock(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!AbilityHelpers.canGrief() || heldBlockCount(ctx) > 0) {
			return;
		}
		var hit = AbilityHelpers.raycastBlock(p, RANGE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos bp = hit.getBlockPos();
		BlockState state = p.level().getBlockState(bp);
		if (!liftable(ctx.level(), bp, state)) {
			return;
		}
		if (!spendPsi(ctx, COST_BLOCK)) {
			return;
		}
		ctx.level().removeBlock(bp, false);
		spawnHeldBlock(ctx, 0, state, Vec3.ZERO);
		ctx.setResource("block_grab_at", p.level().getGameTime(), 1.0e12f);
		MutationVisuals.play(p, "channel_right");
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 0.8f);
	}

	private static void grabChunk(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!AbilityHelpers.canGrief() || heldBlockCount(ctx) > 0) {
			return;
		}
		var hit = AbilityHelpers.raycastBlock(p, RANGE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos centre = hit.getBlockPos();
		List<BlockPos> take = new ArrayList<>();
		for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
			for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
				BlockPos bp = centre.offset(dx, 0, dz);
				if (liftable(ctx.level(), bp, ctx.level().getBlockState(bp))) {
					take.add(bp);
				}
			}
		}
		if (take.isEmpty()) {
			ctx.actionBar("message.projecthero.telekinesis.no_chunk");
			return;
		}
		if (!spendPsi(ctx, COST_CHUNK)) {
			return;
		}
		Vec3 origin = Vec3.atCenterOf(centre);
		for (int i = 0; i < take.size(); i++) {
			BlockPos bp = take.get(i);
			BlockState state = ctx.level().getBlockState(bp);
			ctx.level().removeBlock(bp, false);
			spawnHeldBlock(ctx, i, state, Vec3.atCenterOf(bp).subtract(origin));
		}
		ctx.setResource("chunk_mode", 1, 1);
		MutationVisuals.play(p, "carry_overhead");
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.6f);
		ctx.actionBar("message.projecthero.telekinesis.chunk");
	}

	private static boolean liftable(ServerLevel level, BlockPos bp, BlockState state) {
		return !state.isAir() && !state.liquid()
				&& state.getDestroySpeed(level, bp) >= 0
				&& state.getDestroySpeed(level, bp) <= 6.0f
				&& level.getBlockEntity(bp) == null; // never swallow a chest and its contents
	}

	private static void spawnHeldBlock(AbilityContext ctx, int index, BlockState state, Vec3 offset) {
		ServerLevel level = ctx.level();
		ServerPlayer p = ctx.player();
		FallingBlockEntity fb = new FallingBlockEntity(EntityType.FALLING_BLOCK, level);
		Vec3 at = holdPoint(p).add(offset);
		fb.setPos(at.x, at.y, at.z);
		fb.setStartPos(BlockPos.containing(at));
		fb.setNoGravity(true);
		fb.dropItem = false;
		fb.disableDrop();
		fb.time = 1;
		fb.setDeltaMovement(Vec3.ZERO);
		CompoundTag tag = new CompoundTag();
		fb.saveWithoutId(tag);
		tag.put("BlockState", NbtUtils.writeBlockState(state));
		fb.load(tag);
		level.addFreshEntity(fb);
		ctx.setResource("block_id" + index, fb.getId(), 1.0e9f);
		ctx.setResource("block_off_x" + index, (float) (offset.x + 8.0), 32.0f);
		ctx.setResource("block_off_y" + index, (float) (offset.y + 8.0), 32.0f);
		ctx.setResource("block_off_z" + index, (float) (offset.z + 8.0), 32.0f);
	}

	private static Vec3 holdPoint(ServerPlayer p) {
		return p.getEyePosition().add(p.getLookAngle().scale(3.5));
	}

	private static int maxHeldBlocks() {
		int side = CHUNK_RADIUS * 2 + 1;
		return side * side;
	}

	private static int heldBlockCount(AbilityContext ctx) {
		int n = 0;
		for (int i = 0; i < maxHeldBlocks(); i++) {
			if (ctx.resource("block_id" + i) > 0.5f) {
				n++;
			}
		}
		return n;
	}

	private static void heldBlockTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("chunk_fly") > 0.5f) {
			return;
		}
		Vec3 hold = holdPoint(p);
		boolean any = false;
		for (int i = 0; i < maxHeldBlocks(); i++) {
			int id = (int) ctx.resource("block_id" + i);
			if (id == 0) {
				continue;
			}
			if (!(p.level().getEntity(id) instanceof FallingBlockEntity fb) || !fb.isAlive()) {
				ctx.setResource("block_id" + i, 0, 1.0e9f);
				continue;
			}
			any = true;
			Vec3 off = new Vec3(ctx.resource("block_off_x" + i) - 8.0,
					ctx.resource("block_off_y" + i) - 8.0,
					ctx.resource("block_off_z" + i) - 8.0);
			fb.setPos(hold.x + off.x, hold.y + off.y, hold.z + off.z);
			fb.setDeltaMovement(Vec3.ZERO);
			fb.setNoGravity(true);
			fb.time = 1;
			broadcastPos(ctx.level(), fb);
		}
		if (!any) {
			return;
		}
		MutationVisuals.ensure(p, ctx.resource("chunk_mode") > 0.5f ? "carry_overhead" : "channel_right");
		if (p.tickCount % 3 == 0) {
			AbilityHelpers.line(ctx.level(), AbilityHelpers.handPosition(p), hold, BatchDFx.PSI, 1.2);
		}
		if (!drainPsi(ctx, DRAIN_HOLD)) {
			clearChunk(p);
		}
	}

	private static void releaseBlocks(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (heldBlockCount(ctx) == 0) {
			return;
		}
		boolean chunk = ctx.resource("chunk_mode") > 0.5f;
		MutationVisuals.stopIf(p, "channel_right");
		MutationVisuals.stopIf(p, "carry_overhead");
		long heldFor = p.level().getGameTime() - (long) ctx.resource("block_grab_at");
		if (!chunk && heldFor <= PLUCK_TAP_TICKS && orbitSize(p) < ORBIT_MAX) {
			// a quick tap plucks the block into the orbit instead of throwing it
			int id = (int) ctx.resource("block_id0");
			if (p.level().getEntity(id) instanceof FallingBlockEntity fb) {
				addToOrbit(p, fb);
				ctx.actionBar("message.projecthero.telekinesis.orbit_add", orbitSize(p), ORBIT_MAX);
			}
			clearBlockSlots(p);
			ctx.setResource("chunk_mode", 0, 1);
			return;
		}
		Vec3 dir = p.getLookAngle();
		for (int i = 0; i < maxHeldBlocks(); i++) {
			int id = (int) ctx.resource("block_id" + i);
			if (id != 0 && p.level().getEntity(id) instanceof FallingBlockEntity fb) {
				fb.setNoGravity(false);
				fb.setDeltaMovement(dir.scale(chunk ? 2.2 : 1.8));
				fb.setHurtsEntities(chunk ? 6.0f : 2.4f, chunk ? 72 : 24);
				fb.time = 1;
			}
		}
		MutationVisuals.play(p, "throw_right");
		if (chunk) {
			ctx.setResource("chunk_fly", CHUNK_FLIGHT_TICKS, CHUNK_FLIGHT_TICKS);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.5f);
		} else {
			clearBlockSlots(p);
		}
		ctx.setResource("chunk_mode", 0, 1);
	}

	private static void thrownChunkTick(AbilityContext ctx) {
		float left = ctx.resource("chunk_fly");
		if (left <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		ctx.setResource("chunk_fly", left - 1.0f, CHUNK_FLIGHT_TICKS);
		FallingBlockEntity lead = null;
		for (int i = 0; i < maxHeldBlocks() && lead == null; i++) {
			int id = (int) ctx.resource("block_id" + i);
			if (id != 0 && p.level().getEntity(id) instanceof FallingBlockEntity fb && fb.isAlive()) {
				lead = fb;
			}
		}
		if (lead == null || left <= 1.5f) {
			clearChunkTracking(p);
			return;
		}
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, lead.position(), 2.5)) {
			if (Squads.areAllies(p, e)) {
				continue;
			}
			AbilityHelpers.hurtBurst(p, e, CHUNK_IMPACT_DAMAGE);
			AbilityHelpers.knockbackFrom(e, lead.position(), 1.8);
			ctx.level().sendParticles(ParticleTypes.SCULK_CHARGE_POP,
					e.getX(), e.getY() + 1.0, e.getZ(), 30, 0.5, 0.5, 0.5, 0.2);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.7f);
			clearChunk(p);
			return;
		}
	}

	private static void clearChunk(ServerPlayer p) {
		Power power = power();
		if (power == null) {
			return;
		}
		for (int i = 0; i < maxHeldBlocks(); i++) {
			int id = (int) ExperimentalPowers.getResource(p, power, "block_id" + i);
			if (id != 0 && p.level().getEntity(id) instanceof FallingBlockEntity fb) {
				fb.discard();
			}
		}
		clearChunkTracking(p);
	}

	private static void clearChunkTracking(ServerPlayer p) {
		clearBlockSlots(p);
		Power power = power();
		if (power != null) {
			ExperimentalPowers.setResource(p, power, "chunk_fly", 0, CHUNK_FLIGHT_TICKS);
			ExperimentalPowers.setResource(p, power, "chunk_mode", 0, 1);
		}
	}

	private static void clearBlockSlots(ServerPlayer p) {
		Power power = power();
		if (power == null) {
			return;
		}
		for (int i = 0; i < maxHeldBlocks(); i++) {
			ExperimentalPowers.setResource(p, power, "block_id" + i, 0, 1.0e9f);
		}
	}

	// ================================================================================ visuals

	/** The purple-eyes overlay: on while the telekinetic is channelling or holding anything at all. */
	public static boolean channelling(ServerPlayer p) {
		Power power = power();
		if (power == null || !ExperimentalPowers.owns(p, power)) {
			return false;
		}
		if (orbitSize(p) > 0 || locksSomething(p)) {
			return true;
		}
		if (ExperimentalPowers.getResource(p, power, "crush_id") > 0.5f
				|| ExperimentalPowers.getResource(p, power, "block_id0") > 0.5f
				|| ExperimentalPowers.getResource(p, power, "ult_start") > 0.5f) {
			return true;
		}
		return ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_2))
				|| ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_3));
	}

	// ================================================================================ passives

	private static void registerPassives() {
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			Power power = power();
			if (power == null) {
				return;
			}
			if (!active) {
				endCrush(player);
				clearChunk(player);
				releaseOrbit(player, false);
				return;
			}
			if (ExperimentalPowers.getResource(player, power, "psi") <= 0.0f
					&& ExperimentalPowers.getResource(player, power, "burnout_until") <= player.level().getGameTime()) {
				ExperimentalPowers.setResource(player, power, "psi", MAX_PSI, MAX_PSI);
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			Power power = power();
			if (power == null) {
				return;
			}
			long now = player.level().getGameTime();
			float burnoutUntil = ExperimentalPowers.getResource(player, power, "burnout_until");
			if (burnoutUntil > now) {
				ExperimentalPowers.setResource(player, power, "burnout_left", burnoutUntil - now, BURNOUT_TICKS);
				if (player.tickCount % 5 == 0) {
					player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0, false, false, false));
				}
				return;
			}
			if (burnoutUntil > 0.0f) {
				ExperimentalPowers.setResource(player, power, "burnout_until", 0, 1.0e12f);
				ExperimentalPowers.setResource(player, power, "burnout_left", 0, BURNOUT_TICKS);
				player.displayClientMessage(
						Component.translatable("message.projecthero.telekinesis.recovered"), true);
			}
			if (ExperimentalPowers.getResource(player, power, "spend_until") > now) {
				return;
			}
			float regen = PSI_REGEN_PER_TICK;
			if (ExperimentalPowers.getResource(player, power, "regen_slow_until") > now) {
				regen *= ULT_REGEN_PENALTY;
			}
			if (ExperimentalPowers.getResource(player, power, "psi") < MAX_PSI) {
				ExperimentalPowers.addResource(player, power, "psi", regen, MAX_PSI);
			}
			if (player.level() instanceof ServerLevel sl && player.tickCount % 2 == 0) {
				for (ItemEntity item : sl.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(5.0))) {
					if (!inAnyOrbit(item.getId())) {
						item.setDeltaMovement(item.getDeltaMovement().add(
								player.position().subtract(item.position()).normalize().scale(0.04)));
					}
				}
			}
		});
	}

	private static final float FALL_PSI_PER_DAMAGE = 6.0f;

	/**
	 * A telekinetic never takes fall damage -- they catch themselves -- but the reflex costs Psi in proportion to the
	 * fall it absorbed. Called from {@link com.projecthero.mod.hero.power.HeroDamageRules}.
	 */
	public static void absorbFall(ServerPlayer player, float fallAmount) {
		Power power = power();
		if (power == null) {
			return;
		}
		if (ExperimentalPowers.getResource(player, power, "burnout_until") > player.level().getGameTime()) {
			return;
		}
		ExperimentalPowers.addResource(player, power, "psi", -fallAmount * FALL_PSI_PER_DAMAGE, MAX_PSI);
		holdOffRegen(player);
		if (ExperimentalPowers.getResource(player, power, "psi") <= 0.0f) {
			startBurnout(player);
		}
		if (player.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.1, player.getZ(),
					8, 0.4, 0.05, 0.4, 0.02);
		}
	}

	/**
	 * The Telekinetic Barrier's damage rule, called from {@link com.projecthero.mod.hero.power.HeroDamageRules}:
	 * while the barrier is up nothing gets through, but every blow costs Psi in proportion to what it would have done.
	 */
	public static boolean barrierAbsorbs(ServerPlayer player, float amount) {
		Power power = power();
		if (power == null || !ExperimentalPowers.isToggled(player, power, power.ability(AbilitySlot.SLOT_2))) {
			return false;
		}
		if (ExperimentalPowers.getResource(player, power, "burnout_until") > player.level().getGameTime()) {
			return false;
		}
		ExperimentalPowers.addResource(player, power, "psi", -amount * DRAIN_BARRIER_PER_DAMAGE, MAX_PSI);
		holdOffRegen(player);
		if (player.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
					player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.6, 0.8, 0.6, 0.1);
		}
		if (ExperimentalPowers.getResource(player, power, "psi") <= 0.0f) {
			startBurnout(player);
		}
		return true;
	}
}
