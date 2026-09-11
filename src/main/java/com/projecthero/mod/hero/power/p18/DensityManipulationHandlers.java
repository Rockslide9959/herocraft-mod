package com.projecthero.mod.hero.power.p18;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Power 18 — Density Manipulation (v0.10.13 rebuild).
 *
 * <p>Density is a continuous value from 25% to 300% (default 100%), nudged 5% at a time with R / G,
 * slammed to the extremes by Zero Density (C) and Density Anchor (X). The current density drives a
 * table of movement / damage-dealt / damage-taken / knockback modifiers (see {@link #chart}). Z is a
 * Heavy Impact ground pound, V is Phase (unchanged intangibility).
 */
public final class DensityManipulationHandlers {
	private static final String KEY = "power_18_density_manipulation";
	private static final net.minecraft.resources.ResourceLocation SPD = com.projecthero.mod.ProjectHeroMod.id("density_spd");
	private static final net.minecraft.resources.ResourceLocation ATK = com.projecthero.mod.ProjectHeroMod.id("density_atk");
	private static final net.minecraft.resources.ResourceLocation KB = com.projecthero.mod.ProjectHeroMod.id("density_kb");
	private static final net.minecraft.resources.ResourceLocation JUMP = com.projecthero.mod.ProjectHeroMod.id("density_jump");

	private static final float MIN_DENSITY = 25.0f;
	private static final float MAX_DENSITY = 300.0f;
	private static final float DEFAULT_DENSITY = 100.0f;
	private static final float STEP = 5.0f;

	private static final int ANCHOR_TICKS = 10 * 20;
	private static final int ANCHOR_CD = 30 * 20;

	private static final float MAX_PHASE = 100.0f;
	private static final float PHASE_DRAIN = 0.15f;
	private static final float PHASE_REGEN = 0.2f;
	private static final double PHASE_MAX_HEIGHT = 2.0;
	private static final int GROUND_SEARCH = 32;

	/** Heavy Impact: minimum height to slam from, and the launch height when used on the ground. */
	private static final double HEAVY_MIN_HEIGHT = 6.0;

	private DensityManipulationHandlers() {
	}

	// ---- density value + chart ---------------------------------------------------------------

	/** The player's current density percentage (25..300), reading from the synced attachment. */
	public static float density(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains(KEY)) {
			return DEFAULT_DENSITY;
		}
		Float v = st.resources.get(KEY + "/density");
		return v == null || v <= 0.0f ? DEFAULT_DENSITY : Math.max(MIN_DENSITY, Math.min(MAX_DENSITY, v));
	}

	/** True while phasing -- read from the synced attachment so the client mixin can use it. */
	public static boolean phasing(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY) && st.activeToggles.contains(KEY + "/phase");
	}

	/** True while Density Anchor is holding the player in place. */
	public static boolean anchored(ServerPlayer p) {
		return ExperimentalPowers.getResource(p, Powers.byKey(KEY), "anchor_until") > p.level().getGameTime();
	}

	/** Damage-taken multiplier from density + Density Anchor's 60% reduction. Read by HeroDamageRules. */
	public static float damageTakenFactor(ServerPlayer p) {
		float f = 1.0f + chart(density(p)).taken();
		if (anchored(p)) {
			f *= 0.4f;
		}
		return f;
	}

	private record Chart(float speed, float damage, float taken, float kbResist) {
	}

	private static final float[] PCT = {25, 50, 100, 150, 200, 300};
	private static final float[] SPEED = {0.35f, 0.20f, 0.0f, -0.10f, -0.20f, -0.40f};
	private static final float[] DAMAGE = {-0.40f, -0.20f, 0.0f, 0.15f, 0.30f, 0.50f};
	private static final float[] TAKEN = {0.20f, 0.10f, 0.0f, -0.15f, -0.25f, -0.40f};
	private static final float[] KBRES = {0.0f, 0.0f, 0.0f, 0.40f, 0.70f, 0.90f};

	private static Chart chart(float pct) {
		pct = Math.max(MIN_DENSITY, Math.min(MAX_DENSITY, pct));
		int i = 0;
		while (i < PCT.length - 1 && pct > PCT[i + 1]) {
			i++;
		}
		float lo = PCT[i];
		float hi = PCT[i + 1];
		float t = hi == lo ? 0.0f : (pct - lo) / (hi - lo);
		return new Chart(
				lerp(SPEED[i], SPEED[i + 1], t),
				lerp(DAMAGE[i], DAMAGE[i + 1], t),
				lerp(TAKEN[i], TAKEN[i + 1], t),
				lerp(KBRES[i], KBRES[i + 1], t));
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	// ---- registration ----------------------------------------------------------------------------

	public static void register() {
		AbilityHandlers.register(KEY, "increase_density", Handlers.instant(ctx -> {
			adjustDensity(ctx, STEP);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "decrease_density", Handlers.instant(ctx -> {
			adjustDensity(ctx, -STEP);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "zero_density", Handlers.instant(ctx -> {
			setDensity(ctx, MIN_DENSITY);
			ctx.actionBar("message.projecthero.density.set", (int) MIN_DENSITY);
			AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
			ctx.level().sendParticles(ParticleTypes.CLOUD, ctx.player().getX(), ctx.player().getY() + 1,
					ctx.player().getZ(), 20, 0.3, 0.6, 0.3, 0.02);
			ctx.triggerCooldown();
		}));

		// X -- Density Anchor. Slam to maximum density and root: no knockback, 60% damage reduction, no
		// sprint or jump, for 10 s. A 30 s cooldown starts when it ends.
		AbilityHandlers.register(KEY, "density_anchor", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (anchored(ctx.player())) {
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
					return;
				}
				ServerPlayer p = ctx.player();
				ctx.setResource("anchor_prev", Math.round(density(p)), MAX_DENSITY);
				setDensity(ctx, MAX_DENSITY);
				ctx.setResource("anchor_until", p.level().getGameTime() + ANCHOR_TICKS, 1.0e12f);
				p.setDeltaMovement(0, 0, 0);
				p.hurtMarked = true;
				AbilityHelpers.sound(p, SoundEvents.ANVIL_LAND, 1.2f, 0.5f);
				ctx.level().sendParticles(ParticleTypes.CRIT, p.getX(), p.getY(), p.getZ(), 40, 0.5, 0.1, 0.5, 0.1);
				ctx.actionBar("message.projecthero.density.anchor_on");
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				long until = (long) ctx.resource("anchor_until");
				if (until <= 0L) {
					return;
				}
				if (p.level().getGameTime() >= until) {
					// end: restore density, start the 30 s cooldown now
					ctx.setResource("anchor_until", 0, 1.0e12f);
					float prev = ctx.resource("anchor_prev");
					setDensity(ctx, prev <= 0.0f ? DEFAULT_DENSITY : prev);
					PowerToggles.clearModifier(p, Attributes.JUMP_STRENGTH, JUMP);
					ctx.triggerCooldown(ANCHOR_CD);
					ctx.actionBar("message.projecthero.density.anchor_off");
					AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.7f);
					return;
				}
				// hold in place
				p.setSprinting(false);
				PowerToggles.modifier(p, Attributes.JUMP_STRENGTH, JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
				if (p.tickCount % 4 == 0) {
					ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(),
							3, 0.4, 0.6, 0.4, 0.0);
				}
			}
		});

		// Z -- Heavy Impact.
		AbilityHandlers.register(KEY, "heavy_impact", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			ctx.setResource("hi_start", p.level().getGameTime(), 1.0e12f);
			if (heightAboveGround(p) > HEAVY_MIN_HEIGHT) {
				ctx.setResource("hi_state", 2, 3);
			} else {
				ctx.setResource("hi_state", 1, 3);
				p.setDeltaMovement(p.getDeltaMovement().x * 0.3, 1.9, p.getDeltaMovement().z * 0.3);
				AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.4f);
			}
			p.hurtMarked = true;
			p.hasImpulse = true;
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
			ctx.triggerCooldown();
		}, DensityManipulationHandlers::heavyImpactTick));

		// V -- Phase (unchanged).
		AbilityHandlers.register(KEY, "phase", phaseHandler());

		registerPassives();
	}

	// ---- R / G / C: density value -----------------------------------------------------------

	private static void adjustDensity(AbilityContext ctx, float delta) {
		if (anchored(ctx.player())) {
			ctx.actionBar("message.projecthero.density.anchor_locked");
			return;
		}
		float now = density(ctx.player());
		float next = Math.max(MIN_DENSITY, Math.min(MAX_DENSITY, now + delta));
		setDensity(ctx, next);
		ctx.actionBar("message.projecthero.density.set", (int) next);
		AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_HIT, 0.7f,
				0.5f + next / MAX_DENSITY * 1.2f);
	}

	private static void setDensity(AbilityContext ctx, float pct) {
		ctx.setResource("density", Math.max(MIN_DENSITY, Math.min(MAX_DENSITY, pct)), MAX_DENSITY);
		applyDensity(ctx.player());
	}

	private static void applyDensity(ServerPlayer p) {
		Chart c = chart(density(p));
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SPD, c.speed(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ATK, c.damage(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, KB, c.kbResist(), AttributeModifier.Operation.ADD_VALUE);
	}

	// ---- Z: Heavy Impact ------------------------------------------------------------------------

	private static void heavyImpactTick(AbilityContext ctx) {
		int state = (int) ctx.resource("hi_state");
		if (state == 0) {
			return;
		}
		ServerPlayer p = ctx.player();
		// Safety: never leave the player hanging in the sky. Five seconds after the cast, force the
		// plunge regardless of what state the launch is in.
		float start = ctx.resource("hi_start");
		if (start > 0.5f && p.level().getGameTime() - (long) start > 100L && state == 1) {
			ctx.setResource("hi_state", 2, 3);
			state = 2;
		}
		if (state == 1) {
			// rising -- keep boosting for a moment, then flip to the plunge once we top out
			Vec3 v = p.getDeltaMovement();
			if (v.y > 0.1) {
				if (v.y < 2.2) {
					p.setDeltaMovement(v.x, Math.min(2.4, v.y + 0.35), v.z);
					p.hasImpulse = true;
					p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
				}
				p.resetFallDistance();
				ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 4, 0.2, 0.2, 0.2, 0.0);
				return;
			}
			ctx.setResource("hi_state", 2, 3);
			return;
		}
		// state 2: plunge straight down
		if (!p.onGround() && !p.isInWater()) {
			p.setDeltaMovement(p.getDeltaMovement().x * 0.2, Math.min(p.getDeltaMovement().y, -2.8),
					p.getDeltaMovement().z * 0.2);
			p.hasImpulse = true;
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
			p.resetFallDistance();
			ctx.level().sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
			return;
		}
		ctx.setResource("hi_state", 0, 3);
		ctx.setResource("hi_start", 0, 1.0e12f);
		double r = 20.0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
			double d = Math.sqrt(e.distanceToSqr(p));
			float dmg = (float) (45.0 * (1.0 - Math.min(0.8, d / r)));
			AbilityHelpers.hurt(p, e, dmg);
			AbilityHelpers.knockbackFrom(e, p.position(), 1.6);
			AbilityHelpers.push(e, new Vec3(0, 0.4, 0));
		}
		ServerLevel level = ctx.level();
		if (AbilityHelpers.canGrief()) {
			level.explode(p, null, null, p.getX(), p.getY(), p.getZ(), 4.0f, false,
					Level.ExplosionInteraction.MOB, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
					SoundEvents.GENERIC_EXPLODE);
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY(), p.getZ(), 2, 1.0, 0.2, 1.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 80, 4.0, 0.2, 4.0, 0.1);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.4f, 0.4f);
	}

	// ---- V: Phase (carried over from the previous implementation) ------------------------------

	private static AbilityHandler phaseHandler() {
		return new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (!phaseSeeded(ctx)) {
					ctx.setResource("phase", MAX_PHASE, MAX_PHASE);
				}
				if (ctx.resource("phase") < 10.0f) {
					ctx.setToggled(false);
					ctx.actionBar("message.projecthero.density.phase_out");
					return;
				}
				setPhaseAbilities(p, true);
				p.setDeltaMovement(p.getDeltaMovement().x, 0.0, p.getDeltaMovement().z);
				p.hasImpulse = true;
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
				AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.5f);
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				setPhaseAbilities(ctx.player(), false);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				setPhaseAbilities(p, true);
				p.noPhysics = true;
				p.resetFallDistance();
				clampPhaseHeight(p);
				ctx.addResource("phase", -PHASE_DRAIN, MAX_PHASE);
				if (ctx.resource("phase") <= 0.0f) {
					ctx.setToggled(false);
					setPhaseAbilities(p, false);
					ctx.actionBar("message.projecthero.density.phase_out");
					return;
				}
				if (p.tickCount % 2 == 0) {
					ctx.level().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.25, 0.5, 0.25, 0.0);
					ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.2, 0.4, 0.2, 0.0);
				}
				BlockPos eye = BlockPos.containing(p.getEyePosition());
				if (p.level().getBlockState(eye).isSuffocating(p.level(), eye)) {
					p.setAirSupply(p.getAirSupply() - 4);
					if (p.getAirSupply() <= -20) {
						p.setAirSupply(0);
						p.hurt(p.damageSources().drown(), 2.0f);
					}
				}
			}
		};
	}

	private static void clampPhaseHeight(ServerPlayer p) {
		double ground = groundYBelow(p);
		if (Double.isNaN(ground)) {
			return;
		}
		double cap = ground + PHASE_MAX_HEIGHT;
		if (p.getY() <= cap + 0.05) {
			return;
		}
		p.setDeltaMovement(p.getDeltaMovement().x, 0.0, p.getDeltaMovement().z);
		p.teleportTo(p.getX(), cap, p.getZ());
		p.resetFallDistance();
	}

	private static double heightAboveGround(ServerPlayer p) {
		double ground = groundYBelow(p);
		return Double.isNaN(ground) ? 0.0 : p.getY() - ground;
	}

	private static double groundYBelow(ServerPlayer p) {
		BlockPos.MutableBlockPos cursor = p.blockPosition().mutable();
		for (int i = 0; i <= GROUND_SEARCH; i++) {
			if (!p.level().getBlockState(cursor).getCollisionShape(p.level(), cursor).isEmpty()) {
				return cursor.getY() + 1.0;
			}
			cursor.move(0, -1, 0);
			if (cursor.getY() < p.level().getMinBuildHeight()) {
				break;
			}
		}
		return Double.NaN;
	}

	// ---- passives ---------------------------------------------------------------------------------

	private static void registerPassives() {
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, owned) -> {
			if (!owned) {
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPD);
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATK);
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KB);
				PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP);
				setPhaseAbilities(player, false);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			applyDensity(player);
			var power = Powers.byKey(KEY);
			if (power != null && !phasing(player)
					&& ExperimentalPowers.getResource(player, power, "phase") < MAX_PHASE) {
				ExperimentalPowers.addResource(player, power, "phase", PHASE_REGEN, MAX_PHASE);
			}
		});
	}

	private static boolean phaseSeeded(AbilityContext ctx) {
		return ExperimentalPowers.state(ctx.player()).resources.containsKey(KEY + "/phase");
	}

	private static void setPhaseAbilities(ServerPlayer p, boolean on) {
		if (p.getAbilities().instabuild) {
			return;
		}
		boolean changed = false;
		if (p.getAbilities().mayfly != on) {
			p.getAbilities().mayfly = on;
			changed = true;
		}
		if (on && !p.getAbilities().flying) {
			p.getAbilities().flying = true;
			changed = true;
		}
		if (!on) {
			if (p.getAbilities().flying) {
				p.getAbilities().flying = false;
				changed = true;
			}
			p.noPhysics = false;
		}
		if (changed) {
			p.onUpdateAbilities();
		}
	}
}
