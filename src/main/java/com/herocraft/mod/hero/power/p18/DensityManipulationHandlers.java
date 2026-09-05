package com.herocraft.mod.hero.power.p18;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.hero.AbilityContext;
import com.herocraft.mod.hero.AbilityHandler;
import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.hero.data.ExperimentalState;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.Handlers;
import com.herocraft.mod.hero.power.PowerToggles;
import com.herocraft.mod.hero.power.SafeTeleport;

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

/** Power 18 — Density Manipulation. C cycles Normal -> Heavy -> Light. */
public final class DensityManipulationHandlers {
	private static final String KEY = "power_18_density_manipulation";
	private static final net.minecraft.resources.ResourceLocation KB = com.herocraft.mod.HeroCraftMod.id("density_kb");
	private static final net.minecraft.resources.ResourceLocation SPD = com.herocraft.mod.HeroCraftMod.id("density_spd");
	private static final net.minecraft.resources.ResourceLocation ATK = com.herocraft.mod.HeroCraftMod.id("density_atk");
	private static final net.minecraft.resources.ResourceLocation GRAV = com.herocraft.mod.HeroCraftMod.id("density_grav");
	// cycle starts at Normal (0): Normal -> Heavy -> Light -> Normal ...
	private static final String[] MODE_NAMES = {"normal", "heavy", "light"};

	private static final float MAX_PHASE = 100.0f;
	private static final float PHASE_DRAIN = 0.15f;
	private static final float PHASE_REGEN = 0.2f;
	private static final int SINGULARITY_TICKS = 25 * 20;

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

		// Singularity: rise slowly, invulnerable, dragging every nearby creature in. Anything that gets
		// within 6 blocks is crushed for 15 damage a second. 25 s duration, 60 s cooldown.
		AbilityHandlers.register(KEY, "singularity_drop", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			ctx.setResource("singularity", SINGULARITY_TICKS, SINGULARITY_TICKS);
			p.setInvulnerable(true);
			AbilityHelpers.launchSelf(p, new Vec3(0, 0.3, 0));
			ctx.level().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 80, 1.5, 1.5, 1.5, 0.2);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 0.4f);
			ctx.triggerCooldown();
		}, ctx -> {
			int t = (int) ctx.resource("singularity");
			if (t <= 0) {
				return;
			}
			ServerPlayer p = ctx.player();
			t--;
			ctx.setResource("singularity", t, SINGULARITY_TICKS);
			if (t <= 0) {
				p.setInvulnerable(false);
				return;
			}
			p.setInvulnerable(true); // re-assert (survives a relog mid-ability)
			// drift upward, killing horizontal drift
			Vec3 v = p.getDeltaMovement();
			AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.6, 0.06, v.z * 0.6));
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
		}));

		// Phase: toggle to vibrate and walk straight through blocks. Drains the phase meter; while your
		// head is buried in a block you lose air. Jump to rise, sneak to sink (creative-style control).
		AbilityHandlers.register(KEY, "phase", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (!phaseSeeded(ctx)) {
					ctx.setResource("phase", MAX_PHASE, MAX_PHASE);
				}
				if (ctx.resource("phase") < 10.0f) {
					ctx.setToggled(false);
					ctx.actionBar("message.herocraft.density.phase_out");
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
				ctx.addResource("phase", -PHASE_DRAIN, MAX_PHASE);
				if (ctx.resource("phase") <= 0.0f) {
					ctx.setToggled(false);
					setPhaseAbilities(p, false);
					ctx.actionBar("message.herocraft.density.phase_out");
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
		});

		AbilityHandlers.register(KEY, "density_mode", Handlers.cycle(ctx -> {
			ctx.advanceCycle(3);
			applyMode(ctx);
			ctx.actionBar("message.herocraft.density.mode_" + MODE_NAMES[mode(ctx)]);
			AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 0.6f + mode(ctx) * 0.4f);
		}));

		com.herocraft.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KB);
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPD);
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATK);
				PowerToggles.clearModifier(player, Attributes.GRAVITY, GRAV);
				setPhaseAbilities(player, false);
				player.setInvulnerable(false);
			} else {
				var power = Powers.byKey(KEY);
				applyMode(new AbilityContext(player, power, power.ability(com.herocraft.mod.hero.AbilitySlot.SLOT_6), true));
			}
		});
		com.herocraft.mod.hero.PowerPassives.registerTick(KEY, player -> {
			var power = Powers.byKey(KEY);
			if (power != null) {
				applyMode(new AbilityContext(player, power, power.ability(com.herocraft.mod.hero.AbilitySlot.SLOT_6), true));
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
