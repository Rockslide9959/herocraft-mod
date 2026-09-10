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
import com.projecthero.mod.hero.power.SafeTeleport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Power 18 — Density Manipulation. C cycles Normal -> Heavy -> Light.
 *
 * <p>v0.10.10 reins in the two abilities that had turned into flight. Phase is meant to be walking
 * <em>through</em> the world, so it is now capped {@link #PHASE_MAX_HEIGHT} blocks over the ground
 * rather than handing out unlimited creative flight, and the phased body renders see-through instead of
 * looking completely ordinary. Singularity is an ultimate, so it costs a 5-second hold to start, hangs
 * no more than {@link #SINGULARITY_MAX_HEIGHT} blocks up, can be dropped early on another press, and
 * only starts its 60-second cooldown once it actually ends.
 */
public final class DensityManipulationHandlers {
	private static final String KEY = "power_18_density_manipulation";
	private static final net.minecraft.resources.ResourceLocation KB = com.projecthero.mod.ProjectHeroMod.id("density_kb");
	private static final net.minecraft.resources.ResourceLocation SPD = com.projecthero.mod.ProjectHeroMod.id("density_spd");
	private static final net.minecraft.resources.ResourceLocation ATK = com.projecthero.mod.ProjectHeroMod.id("density_atk");
	private static final net.minecraft.resources.ResourceLocation GRAV = com.projecthero.mod.ProjectHeroMod.id("density_grav");
	// cycle starts at Normal (0): Normal -> Heavy -> Light -> Normal ...
	private static final String[] MODE_NAMES = {"normal", "heavy", "light"};

	private static final float MAX_PHASE = 100.0f;
	private static final float PHASE_DRAIN = 0.15f;
	private static final float PHASE_REGEN = 0.2f;
	private static final int SINGULARITY_TICKS = 25 * 20;
	/** Hold Z this long to commit to the ultimate. */
	private static final int SINGULARITY_CHARGE = 5 * 20;
	private static final int SINGULARITY_CD = 60 * 20;
	/** Ceilings, in blocks above the ground directly below the player. */
	private static final double PHASE_MAX_HEIGHT = 2.0;
	private static final double SINGULARITY_MAX_HEIGHT = 5.0;
	/** How far down to look for "the ground" before giving up (over a chasm or the void). */
	private static final int GROUND_SEARCH = 32;

	private DensityManipulationHandlers() {
	}

	/** True while the phase toggle is on -- read from the synced attachment so the client mixin can use it. */
	public static boolean phasing(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY) && st.activeToggles.contains(KEY + "/phase");
	}

	public static void register() {
		AbilityHandlers.register(KEY, "heavy_punch", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 4.5);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 9.0f + (mode(ctx) == 1 ? 5.0f : 0.0f));
				AbilityHelpers.knockbackFrom(t, p.position(), 1.0);
			}
			AbilityHelpers.sound(p, SoundEvents.ANVIL_LAND, 0.7f, 1.2f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "density_slam", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, -2.4, p.getDeltaMovement().z));
			ctx.setResource("slamming", 1, 1);
			ctx.triggerCooldown();
		}, ctx -> {
			if (ctx.resource("slamming") < 0.5f) {
				return;
			}
			ServerPlayer p = ctx.player();
			if (p.onGround()) {
				double r = 4.0;
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
					AbilityHelpers.hurt(p, e, 11.0f);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.0);
				}
				ctx.level().sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
				AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.9f);
				ctx.setResource("slamming", 0, 1);
			}
		}));

		AbilityHandlers.register(KEY, "intangible_dash", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (SafeTeleport.blink(p, p.getLookAngle(), 6.0)) {
				AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CLOUD, 20, 0.3);
				AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.6f);
				ctx.triggerCooldown();
			}
		}));

		// Singularity: hold Z for 5 s, then hang just off the ground, invulnerable, dragging every nearby
		// creature in. Anything that gets within 6 blocks is crushed for 15 damage a second. 25 s
		// duration; press Z again at any point to drop out of it early. The 60 s cooldown starts when it
		// ENDS, not when it starts, so cutting it short is a real cost rather than a free reset.
		AbilityHandlers.register(KEY, "singularity_drop", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				singularityPress(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				singularityRelease(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				singularityChargeTick(ctx);
				singularityTick(ctx);
			}
		});

		// Phase: toggle to vibrate and walk straight through blocks. Drains the phase meter; while your
		// head is buried in a block you lose air. Jump to rise, sneak to sink -- but only ever a couple of
		// blocks off the ground: this is intangibility, not flight.
		AbilityHandlers.register(KEY, "phase", phaseHandler());

		AbilityHandlers.register(KEY, "density_mode", Handlers.cycle(ctx -> {
			ctx.advanceCycle(3);
			applyMode(ctx);
			ctx.actionBar("message.projecthero.density.mode_" + MODE_NAMES[mode(ctx)]);
			AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 0.6f + mode(ctx) * 0.4f);
		}));

		registerPassives();
	}

	// ---- Z: Singularity ------------------------------------------------------------------------

	/** True while the singularity is up -- used to answer "is this press a start or an early exit?". */
	public static boolean singularityActive(AbilityContext ctx) {
		return ctx.resource("singularity") > 0.5f;
	}

	private static void singularityPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (singularityActive(ctx)) {
			endSingularity(ctx, true);
			return;
		}
		if (ctx.resource("sing_start") > 0.5f) {
			return; // already winding up
		}
		if (!ctx.cooldownReady()) {
			ctx.actionBar("message.projecthero.ability.on_cooldown",
					net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
					String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
			return;
		}
		ctx.setResource("sing_start", p.level().getGameTime(), 1.0e12f);
		ctx.setResource("ult_charge", 0, 100);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 0.9f, 0.5f);
	}

	private static void singularityRelease(AbilityContext ctx) {
		if (ctx.resource("sing_start") <= 0.5f) {
			return;
		}
		long held = ctx.player().level().getGameTime() - (long) ctx.resource("sing_start");
		if (held >= SINGULARITY_CHARGE) {
			fireSingularity(ctx);
		} else {
			cancelSingularityCharge(ctx);
		}
	}

	private static void singularityChargeTick(AbilityContext ctx) {
		float start = ctx.resource("sing_start");
		if (start <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		long held = p.level().getGameTime() - (long) start;
		if (held < 0 || held > SINGULARITY_CHARGE + 100) {
			cancelSingularityCharge(ctx);
			return;
		}
		ctx.setResource("ult_charge", Math.min(100.0f, held * 100.0f / SINGULARITY_CHARGE), 100);
		double frac = Math.min(1.0, held / (double) SINGULARITY_CHARGE);
		// rooted while gathering -- the wind-up is what makes it dodgeable
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.2, 1.0, 0.2));
		p.hurtMarked = true;
		ctx.level().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1.0, p.getZ(),
				4 + (int) (frac * 16), 0.4 + frac, 0.8, 0.4 + frac, 0.05);
		if (held % 10 == 0) {
			AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 0.9f, 0.5f + (float) frac * 0.8f);
		}
		if (held >= SINGULARITY_CHARGE) {
			fireSingularity(ctx);
		}
	}

	private static void cancelSingularityCharge(AbilityContext ctx) {
		ctx.setResource("sing_start", 0, 1.0e12f);
		ctx.setResource("ult_charge", 0, 100);
		AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 0.8f);
	}

	private static void fireSingularity(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ctx.setResource("sing_start", 0, 1.0e12f);
		ctx.setResource("ult_charge", 0, 100);
		ctx.setResource("singularity", SINGULARITY_TICKS, SINGULARITY_TICKS);
		p.setInvulnerable(true);
		AbilityHelpers.launchSelf(p, new Vec3(0, 0.3, 0));
		ctx.level().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 80, 1.5, 1.5, 1.5, 0.2);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 0.4f);
	}

	/** Take the singularity down, and only now start its cooldown. */
	private static void endSingularity(AbilityContext ctx, boolean early) {
		ServerPlayer p = ctx.player();
		ctx.setResource("singularity", 0, SINGULARITY_TICKS);
		p.setInvulnerable(false);
		ctx.triggerCooldown(SINGULARITY_CD);
		AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.9f, 0.7f);
		if (early) {
			ctx.actionBar("message.projecthero.density.singularity_released");
		}
	}

	private static void singularityTick(AbilityContext ctx) {
			int t = (int) ctx.resource("singularity");
			if (t <= 0) {
				return;
			}
			ServerPlayer p = ctx.player();
			t--;
			ctx.setResource("singularity", t, SINGULARITY_TICKS);
			if (t <= 0) {
				endSingularity(ctx, false);
				return;
			}
			p.setInvulnerable(true); // re-assert (survives a relog mid-ability)
			// Drift upward, killing horizontal drift -- but stop climbing once it is hanging
			// SINGULARITY_MAX_HEIGHT blocks up. It used to rise for the whole 25 seconds, which carried the
			// player clean out of reach of everything it had just dragged in.
			Vec3 v = p.getDeltaMovement();
			double lift = heightAboveGround(p) < SINGULARITY_MAX_HEIGHT ? 0.06 : -0.04;
			AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.6, lift, v.z * 0.6));
			p.resetFallDistance();
			// haul everything nearby toward the singularity
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 16.0)) {
				Vec3 pull = p.position().subtract(e.position());
				double dist = pull.length();
				if (dist > 0.5) {
					e.setDeltaMovement(e.getDeltaMovement().add(pull.normalize().scale(0.18)));
					e.hurtMarked = true;
				}
				if (dist <= 6.0 && t % 20 == 0) {
					AbilityHelpers.hurt(p, e, 15.0f);
				}
			}
			if (p.tickCount % 3 == 0) {
				ctx.level().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 20, 2.0, 1.5, 2.0, 0.4);
				ctx.level().sendParticles(ParticleTypes.SCULK_CHARGE_POP, p.getX(), p.getY() + 1, p.getZ(), 6, 1.0, 1.0, 1.0, 0.1);
			}
			if (t % 20 == 0) {
				AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.5f);
			}
	}

	// ---- X: Phase --------------------------------------------------------------------------------

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
				// hold position on entry -- the player only sinks if they choose to (hold sneak)
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
				// vibrating
				if (p.tickCount % 2 == 0) {
					ctx.level().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.25, 0.5, 0.25, 0.0);
					ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.2, 0.4, 0.2, 0.0);
				}
				// buried head = losing air (noPhysics turns off vanilla suffocation, so do it by hand)
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

	/**
	 * v0.10.10: Phase is intangibility, not flight. It has to hand out creative-style flight -- with
	 * {@code noPhysics} on and no flight the player would simply fall through the world -- so the height
	 * is capped server-side instead: hold the player at most {@link #PHASE_MAX_HEIGHT} blocks over
	 * whatever solid ground is under them. Inside rock the ground IS the block they are standing in, so
	 * this never interferes with tunnelling; it only bites the moment they try to use Phase to fly.
	 */
	private static void clampPhaseHeight(ServerPlayer p) {
		double ground = groundYBelow(p);
		if (Double.isNaN(ground)) {
			return; // nothing under them for 32 blocks (a chasm, the void) -- nothing to measure against
		}
		double cap = ground + PHASE_MAX_HEIGHT;
		if (p.getY() <= cap + 0.05) {
			return;
		}
		p.setDeltaMovement(p.getDeltaMovement().x, 0.0, p.getDeltaMovement().z);
		p.teleportTo(p.getX(), cap, p.getZ());
		p.resetFallDistance();
	}

	/** Blocks between the player's feet and the ground below them, or 0 when there is none to find. */
	private static double heightAboveGround(ServerPlayer p) {
		double ground = groundYBelow(p);
		return Double.isNaN(ground) ? 0.0 : p.getY() - ground;
	}

	/**
	 * The Y of the top face of the first block with a real collision shape at or below the player, or
	 * {@code NaN} if there is none within {@link #GROUND_SEARCH} blocks. Deliberately starts at the
	 * player's own feet so a phased player buried in stone measures as being ON the ground.
	 */
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
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KB);
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPD);
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATK);
				PowerToggles.clearModifier(player, Attributes.GRAVITY, GRAV);
				setPhaseAbilities(player, false);
				player.setInvulnerable(false);
			} else {
				var power = Powers.byKey(KEY);
				applyMode(new AbilityContext(player, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6), true));
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			var power = Powers.byKey(KEY);
			if (power != null) {
				applyMode(new AbilityContext(player, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6), true));
			}
			// regenerate the phase meter whenever phasing is not active
			if (power != null && !phasing(player)
					&& ExperimentalPowers.getResource(player, power, "phase") < MAX_PHASE) {
				ExperimentalPowers.addResource(player, power, "phase", PHASE_REGEN, MAX_PHASE);
			}
		});
	}

	private static boolean phaseSeeded(AbilityContext ctx) {
		// resource map only contains "phase" once it has been written at least once
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

	private static int mode(AbilityContext ctx) {
		return ctx.cycleMode() % 3;
	}

	private static void applyMode(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		int m = mode(ctx); // 0 normal, 1 heavy, 2 light
		double kb = m == 1 ? 0.9 : 0.0;
		double spd = m == 2 ? 0.15 : (m == 1 ? -0.35 : 0.0);
		double atk = m == 1 ? 4.0 : (m == 2 ? -1.0 : 0.0);
		double grav = m == 2 ? -0.4 : (m == 1 ? 0.4 : 0.0);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, KB, kb, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SPD, spd, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ATK, atk, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.GRAVITY, GRAV, grav, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (m == 2) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
		}
	}
}
