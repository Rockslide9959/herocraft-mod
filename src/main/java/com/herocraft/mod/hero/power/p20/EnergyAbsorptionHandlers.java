package com.herocraft.mod.hero.power.p20;

import com.herocraft.mod.hero.AbilityContext;
import com.herocraft.mod.hero.AbilityHandler;
import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.Handlers;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Power 20 — Energy Absorption. "energy" meter 0..500, filled by {@code HeroDamageRules} soak. */
public final class EnergyAbsorptionHandlers {
	public static final String KEY = "power_20_energy_absorption";
	public static final String METER = "energy";
	public static final float MAX = 500.0f;

	private EnergyAbsorptionHandlers() {
	}

	private static boolean absorbModeActive(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(com.herocraft.mod.hero.AbilitySlot.SLOT_6));
	}

	private static boolean isEnergySource(net.minecraft.world.level.block.state.BlockState st) {
		boolean litCampfire = st.is(net.minecraft.tags.BlockTags.CAMPFIRES)
				&& st.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT).orElse(false);
		return st.is(Blocks.REDSTONE_BLOCK) || st.is(Blocks.REDSTONE_TORCH) || st.is(Blocks.REDSTONE_LAMP)
				|| st.is(Blocks.GLOWSTONE) || st.is(Blocks.SEA_LANTERN) || st.is(Blocks.LAVA) || litCampfire;
	}

	public static void register() {
		AbilityHandlers.register(KEY, "energy_blast", Handlers.instant(ctx -> {
			if (!ctx.spendResource(METER, 8.0f)) {
				ctx.actionBar("message.herocraft.energy.empty");
				return;
			}
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 26.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 26.0), ParticleTypes.END_ROD, 3.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 8.0f);
				AbilityHelpers.knockbackFrom(t, p.position(), 0.6);
			}
			AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.4f);
		}));

		AbilityHandlers.register(KEY, "energy_beam", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ctx.setResource("beaming", 1, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ctx.setResource("beaming", 0, 1);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("beaming") < 0.5f) {
					return;
				}
				if (!ctx.spendResource(METER, 2.0f)) {
					ctx.setResource("beaming", 0, 1);
					return;
				}
				ServerPlayer p = ctx.player();
				LivingEntity t = AbilityHelpers.raycastEntity(p, 22.0);
				AbilityHelpers.line(ctx.level(), p.getEyePosition().add(p.getLookAngle().scale(0.4)),
						AbilityHelpers.aimPoint(p, 22.0), ParticleTypes.END_ROD, 2.5);
				if (t != null && p.tickCount % 3 == 0) {
					AbilityHelpers.hurt(p, t, 2.0f);
				}
			}
		});

		// Energy Dash (replaces Absorption Shield): a short charged lunge. 3 s cooldown.
		AbilityHandlers.register(KEY, "absorption_shield", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			AbilityHelpers.addImpulse(p, p.getLookAngle().scale(1.9).add(0, 0.15, 0));
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 12, 2, false, false, false));
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.END_ROD, 24, 0.4);
			AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.9f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "overload", Handlers.instant(ctx -> {
			float energy = ctx.resource(METER);
			if (energy < 10.0f) {
				ctx.actionBar("message.herocraft.energy.empty");
				return;
			}
			ctx.setResource(METER, 0, MAX);
			ServerPlayer p = ctx.player();
			float percent = energy / MAX * 100.0f; // 0..100
			double r = 3.0 + percent * 0.12;        // up to ~15 blocks at a full bar
			float dmg = percent;                    // 1 damage per percent charged
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
				AbilityHelpers.hurt(p, e, dmg);
				AbilityHelpers.knockbackFrom(e, p.position(), 1.5 + percent / 25.0);
			}
			if (AbilityHelpers.canGrief()) {
				int cr = (int) Math.ceil(1.0 + percent * 0.05); // crater grows with charge
				BlockPos c = p.blockPosition();
				for (BlockPos bp : BlockPos.betweenClosed(c.offset(-cr, -cr, -cr), c.offset(cr, cr, cr))) {
					if (bp.distToCenterSqr(p.getX(), p.getY(), p.getZ()) <= (cr + 0.5) * (cr + 0.5)) {
						var bs = p.level().getBlockState(bp);
						if (bs.getDestroySpeed(p.level(), bp) >= 0 && bs.getDestroySpeed(p.level(), bp) < 50.0f && !bs.isAir()) {
							ctx.level().destroyBlock(bp, false);
						}
					}
				}
			}
			ctx.level().sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
			ctx.level().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(),
					(int) (percent * 3), r / 2, 0.5, r / 2, 0.3);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.4f, 0.6f);
			ctx.triggerCooldown();
		}));

		// Energy Drain: now HOLD. Point at a powered block (redstone/glowstone/lava/lit campfire) and
		// hold to siphon it into your meter. It doubles as an absorption shield the whole time it is
		// held. On release: a 3 s cooldown.
		AbilityHandlers.register(KEY, "energy_drain", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ctx.setResource("draining", 1, 1);
				ctx.setResource("shielding", 1, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ctx.setResource("draining", 0, 1);
				ctx.setResource("shielding", 0, 1);
				ctx.triggerCooldown(3 * 20);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("draining") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				ServerLevel level = ctx.level();
				BlockHitResult hit = AbilityHelpers.raycastBlock(p, 12.0);
				if (hit.getType() == HitResult.Type.BLOCK && isEnergySource(level.getBlockState(hit.getBlockPos()))) {
					ctx.addResource(METER, 4.0f, MAX);
					if (p.tickCount % 2 == 0) {
						AbilityHelpers.line(level, p.getEyePosition(), Vec3.atCenterOf(hit.getBlockPos()),
								ParticleTypes.ELECTRIC_SPARK, 2.0);
					}
				} else {
					// also pull from powered blocks in a small area around the crosshair
					BlockPos c = hit.getBlockPos();
					for (BlockPos bp : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 2, 2))) {
						if (isEnergySource(level.getBlockState(bp))) {
							ctx.addResource(METER, 1.0f, MAX);
							break;
						}
					}
				}
				if (p.tickCount % 4 == 0) {
					AbilityHelpers.sound(p, SoundEvents.CONDUIT_AMBIENT, 0.4f, 1.4f);
				}
			}
		});

		// Absorption Mode: passive partial soak of energy attacks (in HeroDamageRules) plus a slow
		// trickle-drain of the meter and an energy sheen on your melee hits.
		AbilityHandlers.register(KEY, "absorption_mode", Handlers.toggle(Handlers.noop(), Handlers.noop(), ctx -> {
			AbilityHelpers.modeAura(ctx.player(), ParticleTypes.END_ROD, 3);
			if (ctx.resource(METER) > 0.0f) {
				ctx.addResource(METER, -0.05f, MAX); // a very small upkeep
			}
			if (ctx.resource(METER) > 300.0f && ctx.player().tickCount % 5 == 0) {
				ctx.level().sendParticles(ParticleTypes.GLOW, ctx.player().getX(), ctx.player().getY() + 1,
						ctx.player().getZ(), 2, 0.4, 0.6, 0.4, 0.01);
			}
		}));

		// Absorption Mode: melee hits carry an energy jolt.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && absorbModeActive(sp)
					&& entity instanceof LivingEntity le) {
				AbilityHelpers.hurt(sp, le, 3.0f);
				ExperimentalPowers.addResource(sp, Powers.byKey(KEY), METER, -2.0f, MAX);
				if (world instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.END_ROD, le.getX(), le.getY() + le.getBbHeight() / 2, le.getZ(),
							8, 0.2, 0.2, 0.2, 0.05);
				}
			}
			return InteractionResult.PASS;
		});
	}
}
