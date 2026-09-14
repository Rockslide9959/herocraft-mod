package com.projecthero.mod.hero.power.p20;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.ModeMeter;
import com.projecthero.mod.hero.power.PowerToggles;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/** Power 20 — Energy Absorption. "energy" meter 0..500, half of all incoming damage becomes energy. */
public final class EnergyAbsorptionHandlers {
	public static final String KEY = "power_20_energy_absorption";
	public static final String METER = "energy";
	public static final float MAX = 500.0f;
	private static final Vector3f ENERGY_COLOR = new Vector3f(0.55f, 0.85f, 1.0f);
	private static final float FIELD_MAX = 100.0f;
	private static final net.minecraft.resources.ResourceLocation SUPER_ATK =
			com.projecthero.mod.ProjectHeroMod.id("energy_supercharged_atk");

	private EnergyAbsorptionHandlers() {
	}

	private static boolean superchargedActive(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6));
	}

	/** +10 ability damage while Supercharged. */
	public static float superchargedBonus(ServerPlayer p) {
		return superchargedActive(p) ? 10.0f : 0.0f;
	}

	/** False for two minutes after an Overload Release finishes -- the body cannot take on more charge. */
	public static boolean canAbsorb(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power == null || ExperimentalPowers.getResource(p, power, "absorb_lock_until") <= p.level().getGameTime();
	}

	/** Called from HeroDamageRules whenever this passive splits incoming damage into energy. */
	public static void markAbsorbed(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		if (power != null) {
			ExperimentalPowers.setResource(p, power, "last_absorb", p.level().getGameTime(), 1.0e12f);
		}
	}

	/** True while V's Absorption Field is held -- boosts the passive soak from 50% to 90%. */
	public static boolean fieldActive(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.getResource(p, power, "field_active") > 0.5f;
	}

	/** A hand-fired origin point so R/G's beam particles never spawn right in front of the caster's eyes. */
	private static Vec3 handOrigin(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0e-6) {
			double yaw = Math.toRadians(p.getYRot());
			right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		} else {
			right = right.normalize();
		}
		return p.getEyePosition().add(look.scale(0.8)).add(right.scale(0.4)).add(0, -0.35, 0);
	}

	public static void register() {
		// R -- Energy Blast. Hold to charge (max 3s): +5 damage / +2s cooldown per second held. Shows a
		// charge bar + building particles while held.
		AbilityHandlers.register(KEY, "energy_blast", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!ctx.cooldownReady()) {
					onCooldownMessage(ctx);
					return;
				}
				if (!ctx.spendResource(METER, 10.0f)) {
					ctx.actionBar("message.projecthero.energy.empty");
					return;
				}
				ctx.setResource("blast_charging", 1, 1);
				ctx.setResource("blast_charge_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("blast_charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) ctx.resource("blast_charge_start");
				double seconds = Math.min(3.0, held / 20.0);
				ctx.setResource("blast_charging", 0, 1);
				ctx.setResource("blast_charge", 0, 100);
				ServerLevel level = ctx.level();
				LivingEntity t = AbilityHelpers.raycastEntity(p, 26.0);
				AbilityHelpers.line(level, handOrigin(p), AbilityHelpers.aimPoint(p, 26.0), ParticleTypes.END_ROD, 3.0);
				if (t != null) {
					AbilityHelpers.hurt(p, t, 13.0f + (float) (seconds * 5.0) + superchargedBonus(p));
					AbilityHelpers.knockbackFrom(t, p.position(), 0.6);
				}
				AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.4f);
				ctx.triggerCooldown((int) Math.round(2 * 20 + seconds * 2 * 20));
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("blast_charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) ctx.resource("blast_charge_start");
				ctx.setResource("blast_charge", (float) Math.min(100.0, held / (3.0 * 20) * 100.0), 100);
				if (held % 3 == 0) {
					Vec3 at = handOrigin(p);
					ctx.level().sendParticles(new DustParticleOptions(ENERGY_COLOR, 1.6f), at.x, at.y, at.z,
							3, 0.1, 0.1, 0.1, 0.01);
				}
			}
		});

		// G -- Energy Beam. Shift starts an AoE burst instead of the channel.
		AbilityHandlers.register(KEY, "energy_beam", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (!ctx.cooldownReady()) {
						onCooldownMessage(ctx);
						return;
					}
					if (!ctx.spendResource(METER, 50.0f)) {
						ctx.actionBar("message.projecthero.energy.empty");
						return;
					}
					for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 9.0)) {
						AbilityHelpers.hurt(p, e, 15.0f + superchargedBonus(p));
						AbilityHelpers.knockbackFrom(e, p.position(), 1.0);
					}
					ctx.level().sendParticles(new DustParticleOptions(ENERGY_COLOR, 2.5f), p.getX(), p.getY() + 1, p.getZ(),
							60, 4.5, 1.0, 4.5, 0.05);
					AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.8f);
					ctx.triggerCooldown(20 * 20);
					return;
				}
				ctx.setResource("beaming", 1, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("beaming") > 0.5f) {
					ctx.setResource("beaming", 0, 1);
					ctx.triggerCooldown(5 * 20);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("beaming") < 0.5f) {
					return;
				}
				if (!ctx.spendResource(METER, 1.0f)) { // 20/s
					ctx.setResource("beaming", 0, 1);
					return;
				}
				ServerPlayer p = ctx.player();
				LivingEntity t = AbilityHelpers.raycastEntity(p, 22.0);
				AbilityHelpers.line(ctx.level(), handOrigin(p),
						AbilityHelpers.aimPoint(p, 22.0), ParticleTypes.END_ROD, 2.5);
				if (t != null) {
					AbilityHelpers.hurt(p, t, 9.0f + superchargedBonus(p));
				}
			}
		});

		// X -- Energy Dash.
		AbilityHandlers.register(KEY, "absorption_shield", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (!ctx.spendResource(METER, 5.0f)) {
				ctx.actionBar("message.projecthero.energy.empty");
				return;
			}
			AbilityHelpers.launchSelf(p, p.getLookAngle().scale(1.7).add(0, 0.1, 0));
			ServerLevel level = ctx.level();
			for (int i = 0; i < 10; i++) {
				Vec3 pt = p.position().add(0, 1, 0).subtract(p.getLookAngle().scale(i * 0.6));
				level.sendParticles(new DustParticleOptions(ENERGY_COLOR, 1.5f), pt.x, pt.y, pt.z, 2, 0.15, 0.15, 0.15, 0.0);
			}
			AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.2f);
			ctx.triggerCooldown(2 * 20);
		}));

		// Z -- hold 5s for Maximum Pulse. Shift+Z instead launches Overload Release (runs to completion).
		AbilityHandlers.register(KEY, "overload", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("pulse_charging") > 0.5f || ctx.resource("overload_running") > 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (ctx.resource(METER) < 40.0f) {
						ctx.actionBar("message.projecthero.energy.empty");
						return;
					}
					ctx.setResource("overload_running", 1, 1);
					AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 1.4f, 0.5f);
					return;
				}
				if (!ctx.cooldownReady()) {
					onCooldownMessage(ctx);
					return;
				}
				ctx.setResource("pulse_charging", 1, 1);
				ctx.setResource("pulse_charge_start", p.level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("pulse_charging") > 0.5f) {
					ctx.setResource("pulse_charging", 0, 1);
					ctx.setResource("pulse_charge", 0, 100);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				ServerLevel level = ctx.level();
				if (ctx.resource("overload_running") > 0.5f) {
					if (!ctx.spendResource(METER, 2.0f)) { // 40/s
						endOverloadRelease(ctx);
						return;
					}
					if (p.tickCount % 20 == 0) {
						for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 20.0)) {
							AbilityHelpers.hurt(p, e, 20.0f);
						}
					}
					if (p.tickCount % 3 == 0) {
						level.sendParticles(new DustParticleOptions(ENERGY_COLOR, 2.0f), p.getX(), p.getY() + 1, p.getZ(),
								10, 3.0, 1.0, 3.0, 0.05);
					}
					return;
				}
				if (ctx.resource("pulse_charging") < 0.5f) {
					return;
				}
				long held = p.level().getGameTime() - (long) ctx.resource("pulse_charge_start");
				ctx.setResource("pulse_charge", (float) Math.min(100.0, held / (5.0 * 20) * 100.0), 100);
				if (p.tickCount % 2 == 0) {
					level.sendParticles(new DustParticleOptions(ENERGY_COLOR, 2.0f), p.getX(), p.getY() + 2.2, p.getZ(),
							6, 0.4, 0.3, 0.4, 0.02);
				}
				if (held >= 5 * 20) {
					ctx.setResource("pulse_charging", 0, 1);
					ctx.setResource("pulse_charge", 0, 100);
					if (!ctx.spendResource(METER, 100.0f)) {
						ctx.actionBar("message.projecthero.energy.empty");
						return;
					}
					fireMaximumPulse(ctx);
					ctx.triggerCooldown(30 * 20);
				}
			}
		});

		// V -- Absorption Field (hold): 90% of damage taken becomes energy, up to 15s, 7s cooldown on
		// release. Shift+V is Energy Conversion: 10 energy every 0.5s becomes 2 HP while held.
		AbilityHandlers.register(KEY, "energy_drain", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!ctx.cooldownReady()) {
					onCooldownMessage(ctx);
					return;
				}
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					ctx.setResource("converting", 1, 1);
					ctx.setResource("convert_timer", 0, 1);
					return;
				}
				ModeMeter.ensureSeeded(ctx, "field_bar", FIELD_MAX);
				if (!ModeMeter.hasCharge(ctx, "field_bar", 5.0f)) {
					ctx.actionBar("message.projecthero.energy.field_low");
					return;
				}
				ctx.setResource("field_active", 1, 1);
				AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.4f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("converting") > 0.5f) {
					ctx.setResource("converting", 0, 1);
					ctx.triggerCooldown(10 * 20);
				}
				if (ctx.resource("field_active") > 0.5f) {
					ctx.setResource("field_active", 0, 1);
					ctx.triggerCooldown(7 * 20);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (ctx.resource("converting") > 0.5f) {
					float timer = ctx.resource("convert_timer") + 1;
					if (timer >= 10) { // every 0.5s
						if (ctx.spendResource(METER, 10.0f)) {
							p.heal(2.0f);
							ctx.level().sendParticles(new DustParticleOptions(ENERGY_COLOR, 1.6f), p.getX(), p.getY() + 1,
									p.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
							AbilityHelpers.sound(p, SoundEvents.PLAYER_LEVELUP, 0.4f, 1.8f);
						}
						timer = 0;
					}
					ctx.setResource("convert_timer", timer, 1);
					return;
				}
				if (ctx.resource("field_active") < 0.5f) {
					return;
				}
				if (!ModeMeter.drain(ctx, "field_bar", FIELD_MAX, FIELD_MAX / (15 * 20))) {
					ctx.setResource("field_active", 0, 1);
					ctx.actionBar("message.projecthero.energy.field_out");
					ctx.triggerCooldown(7 * 20);
					return;
				}
				if (p.tickCount % 3 == 0) {
					ctx.level().sendParticles(new DustParticleOptions(ENERGY_COLOR, 1.8f), p.getX(), p.getY() + 1, p.getZ(),
							10, 1.6, 1.0, 1.6, 0.0);
				}
			}
		});

		// C -- Supercharged mode.
		AbilityHandlers.register(KEY, "absorption_mode", Handlers.toggle(
				ctx -> {
					PowerToggles.modifier(ctx.player(), Attributes.ATTACK_DAMAGE, SUPER_ATK, 8.0, AttributeModifier.Operation.ADD_VALUE);
					AbilityHelpers.sound(ctx.player(), SoundEvents.BEACON_ACTIVATE, 1.0f, 1.6f);
				},
				ctx -> {
					PowerToggles.clearModifier(ctx.player(), Attributes.ATTACK_DAMAGE, SUPER_ATK);
					ctx.triggerCooldown(20 * 20);
				},
				ctx -> {
					ServerPlayer p = ctx.player();
					AbilityHelpers.modeAura(p, new DustParticleOptions(ENERGY_COLOR, 1.5f), 3);
					p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 2, false, false, false));
					p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 1, false, false, false));
					p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20, 1, false, false, false));
					if (!ctx.spendResource(METER, 0.4f)) { // 8/s
						PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, SUPER_ATK);
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.energy.empty");
					}
				}));

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, SUPER_ATK);
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, EnergyAbsorptionHandlers::passiveTick);
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player ->
				ModeMeter.regen(player, Powers.byKey(KEY), "field_bar", FIELD_MAX, FIELD_MAX / (20 * 20),
						fieldActive(player)));

		// Absorption Mode: melee hits carry an energy jolt while Supercharged.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && superchargedActive(sp)
					&& entity instanceof LivingEntity le) {
				if (world instanceof ServerLevel sl) {
					sl.sendParticles(new DustParticleOptions(ENERGY_COLOR, 1.4f), le.getX(), le.getY() + le.getBbHeight() / 2,
							le.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
				}
			}
			return InteractionResult.PASS;
		});

		registerRedstoneInteractions();
	}

	private static void passiveTick(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		var power = Powers.byKey(KEY);
		if (power == null) {
			return;
		}
		// Standing on a redstone source charges the meter.
		BlockPos below = player.blockPosition().below();
		var belowState = level.getBlockState(below);
		if (belowState.is(Blocks.REDSTONE_BLOCK)) {
			gain(player, 0.4f); // 8/s
		} else if (belowState.is(Blocks.REDSTONE_ORE) || belowState.is(Blocks.DEEPSLATE_REDSTONE_ORE)) {
			gain(player, 0.25f); // 5/s
		}

		// Passive trickle regen: only while nothing has been absorbed for 10s, capped at 25%. 1.3/s.
		long now = level.getGameTime();
		float lastAbsorb = ExperimentalPowers.getResource(player, power, "last_absorb");
		float energy = ExperimentalPowers.getResource(player, power, METER);
		if (now - (long) lastAbsorb >= 200 && energy < MAX * 0.25f) {
			ExperimentalPowers.addResource(player, power, METER, 1.3f / 20f, MAX);
		}

		// Overcharge: 80% strength/speed + glow; 100% self-damage.
		if (energy >= MAX * 0.8f) {
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 30, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, false, false, false));
			if (player.tickCount % 4 == 0) {
				level.sendParticles(new DustParticleOptions(ENERGY_COLOR, 1.2f), player.getX(), player.getY() + 1,
						player.getZ(), 2, 0.35, 0.6, 0.35, 0.01);
			}
		}
		if (energy >= MAX - 0.5f && player.tickCount % 40 == 0) {
			player.hurt(player.damageSources().magic(), 1.0f);
		}
	}

	private static void gain(ServerPlayer player, float amount) {
		var power = Powers.byKey(KEY);
		if (power != null && canAbsorb(player)) {
			ExperimentalPowers.addResource(player, power, METER, amount, MAX);
			markAbsorbed(player);
		}
	}

	private static void registerRedstoneInteractions() {
		// Right-click redstone dust: harvest it for a flat 5 energy.
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)
					|| !ExperimentalPowers.owns(sp, Powers.byKey(KEY)) || !canAbsorb(sp)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			var state = world.getBlockState(pos);
			if (state.is(Blocks.REDSTONE_WIRE)) {
				world.removeBlock(pos, false);
				gain(sp, 5.0f);
				AbilityHelpers.sound(sp, SoundEvents.ITEM_PICKUP, 0.6f, 1.6f);
				return InteractionResult.SUCCESS;
			}
			// A quick "button press" trigger on common redstone machines -- costs 1 energy.
			if (ExperimentalPowers.getResource(sp, Powers.byKey(KEY), METER) >= 1.0f
					&& triggerRedstoneMachine(world, pos, state)) {
				ExperimentalPowers.addResource(sp, Powers.byKey(KEY), METER, -1.0f, MAX);
				AbilityHelpers.sound(sp, SoundEvents.STONE_BUTTON_CLICK_ON, 1.0f, 1.2f);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// Sneak + use a Redstone Block in hand: "consume" it for 40 energy instead of placing it.
		UseItemCallback.EVENT.register((player, world, hand) -> {
			var stack = player.getItemInHand(hand);
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
					|| !stack.is(Items.REDSTONE_BLOCK) || !(player instanceof ServerPlayer sp)
					|| !ExperimentalPowers.owns(sp, Powers.byKey(KEY)) || !canAbsorb(sp)) {
				return InteractionResultHolder.pass(stack);
			}
			stack.shrink(1);
			gain(sp, 40.0f);
			AbilityHelpers.sound(sp, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.6f);
			((ServerLevel) sp.level()).sendParticles(new DustParticleOptions(ENERGY_COLOR, 2.0f), sp.getX(), sp.getY() + 1, sp.getZ(),
					20, 0.4, 0.6, 0.4, 0.05);
			return InteractionResultHolder.success(stack);
		});

		// Right-click redstone dust also grants a small charge when it's on an item, not a wire block --
		// handled by the pickup path above; also let a dust ITEM be "burned" for energy while sneaking.
		UseItemCallback.EVENT.register((player, world, hand) -> {
			var stack = player.getItemInHand(hand);
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
					|| !stack.is(Items.REDSTONE) || !(player instanceof ServerPlayer sp)
					|| !ExperimentalPowers.owns(sp, Powers.byKey(KEY)) || !canAbsorb(sp)) {
				return InteractionResultHolder.pass(stack);
			}
			stack.shrink(1);
			gain(sp, 5.0f);
			AbilityHelpers.sound(sp, SoundEvents.ITEM_PICKUP, 0.8f, 1.4f);
			return InteractionResultHolder.success(stack);
		});
	}

	/** A restrained, non-destructive "button press" on a handful of common redstone machines. */
	private static boolean triggerRedstoneMachine(net.minecraft.world.level.Level world, BlockPos pos,
			net.minecraft.world.level.block.state.BlockState state) {
		if (state.is(Blocks.NOTE_BLOCK)) {
			world.blockEvent(pos, state.getBlock(), 0, 0);
			return true;
		}
		if (state.is(Blocks.REDSTONE_LAMP) && !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
			world.setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true), 3);
			world.scheduleTick(pos, state.getBlock(), 20);
			return true;
		}
		return false;
	}

	private static void endOverloadRelease(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ctx.setResource("overload_running", 0, 1);
		AbilityHelpers.applyControl(p, MobEffects.BLINDNESS, 200, 0);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 1, false, false, false));
		p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0, false, false, false));
		ctx.setResource("absorb_lock_until", p.level().getGameTime() + 2 * 60 * 20, 1.0e12f);
		AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.5f);
	}

	private static void fireMaximumPulse(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 above = p.position().add(0, p.getBbHeight() + 1.0, 0);
		level.sendParticles(new DustParticleOptions(ENERGY_COLOR, 3.5f), above.x, above.y, above.z, 80, 1.2, 1.2, 1.2, 0.08);
		level.sendParticles(ParticleTypes.FLASH, above.x, above.y, above.z, 1, 0, 0, 0, 0);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, above, 7.0)) {
			AbilityHelpers.hurt(p, e, 70.0f + superchargedBonus(p));
			AbilityHelpers.knockbackFrom(e, above, 2.0);
		}
		if (AbilityHelpers.canGrief()) {
			BlockPos c = BlockPos.containing(above);
			int r = 4;
			for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
				if (bp.distToCenterSqr(above.x, above.y, above.z) <= (double) (r * r)) {
					var bs = level.getBlockState(bp);
					if (!bs.isAir() && bs.getDestroySpeed(level, bp) >= 0 && bs.getDestroySpeed(level, bp) < 50.0f) {
						level.destroyBlock(bp, false);
					}
				}
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, above.x, above.y, above.z, 1, 0, 0, 0, 0);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.5f);
	}

	private static void onCooldownMessage(AbilityContext ctx) {
		ctx.actionBar("message.projecthero.ability.on_cooldown",
				net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
	}
}
