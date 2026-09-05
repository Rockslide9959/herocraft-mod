package com.herocraft.mod.hero.power.p07;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.herocraft.mod.hero.AbilityContext;
import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.Power;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.Handlers;
import com.herocraft.mod.hero.power.ModeMeter;
import com.herocraft.mod.hero.power.PowerToggles;
import com.herocraft.mod.hero.power.TempBlocks;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 07 — Electrokinesis. Deliberately weaker than Thor's lightning: uses
 * {@code DamageTypes.PLAYER_ATTACK} (armour applies, and Thor is <em>not</em> immune to it the way he
 * is to real lightning). Never uses Mjolnir/worthiness state.
 *
 * <p>Nearly every attack leaves the target with a "static shock" — a hard slow plus a weakness, with
 * a visible crackle of sparks arcing around them so the effect reads at a glance.
 */
public final class ElectrokinesisHandlers {
	private static final String KEY = "power_07_electrokinesis";
	private static final float MAX_CHARGE = 500.0f;
	private static final float CHARGE_DRAIN = MAX_CHARGE / (25 * 20); // Charged Mode lasts ~25 s
	private static final float CHARGE_REGEN = MAX_CHARGE / (35 * 20);
	private static final float CHARGE_MIN_ENTER = 50.0f;

	/** entityId -> game time the static shock visual expires. Ticked from the power's passive tick. */
	private static final Map<Integer, Long> SHOCKED = new ConcurrentHashMap<>();

	private ElectrokinesisHandlers() {
	}

	/** Apply the signature static shock / paralysis to a target and start its spark visual. */
	private static void staticShock(ServerLevel level, LivingEntity target, int ticks) {
		AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, ticks, 3); // Slowness IV
		AbilityHelpers.applyControl(target, MobEffects.WEAKNESS, ticks, 1);
		SHOCKED.put(target.getId(), level.getGameTime() + ticks);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
				target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12, 0.35, 0.5, 0.35, 0.12);
	}

	private static boolean chargedModeActive(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(com.herocraft.mod.hero.AbilitySlot.SLOT_6));
	}

	/** Charged Mode adds a flat +10 to every Electrokinesis attack. */
	private static float chargedBonus(ServerPlayer p) {
		return chargedModeActive(p) ? 10.0f : 0.0f;
	}

	public static void register() {
		AbilityHandlers.register(KEY, "electric_bolt", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
			Vec3 end = AbilityHelpers.aimPoint(p, 24.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), end, ParticleTypes.ELECTRIC_SPARK, 3.0);
			if (t != null) {
				float dmg = (8.0f + chargedBonus(p)) * com.herocraft.mod.hero.power.PowerCombos.wetElectricMultiplier(p, t)
						+ Math.min(6.0f, ctx.resource("static_charge") / 15.0f);
				ctx.setResource("static_charge", 0, 100.0f);
				AbilityHelpers.hurt(p, t, AbilityHelpers.kinetic(p), dmg);
				staticShock(ctx.level(), t, 60); // ~3 s of static shock
			}
			AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.8f);
			ctx.triggerCooldown();
		}));

		// Chain Lightning: strike the first target, then arc to as many other targets as it can find
		// within 8 blocks of each link (no fixed cap). Flat 8 damage per hop, static shock on every hit.
		AbilityHandlers.register(KEY, "chain_lightning", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			LivingEntity first = AbilityHelpers.raycastEntity(p, 22.0);
			if (first == null) {
				return;
			}
			List<LivingEntity> chain = new ArrayList<>();
			Set<UUID> struck = new HashSet<>();
			chain.add(first);
			struck.add(first.getUUID());
			LivingEntity cur = first;
			while (chain.size() < 24) { // hard safety cap only
				LivingEntity next = null;
				double best = Double.MAX_VALUE;
				for (LivingEntity c : AbilityHelpers.enemiesAround(p, cur.position(), 8.0)) {
					if (struck.contains(c.getUUID())) {
						continue;
					}
					double d = c.distanceToSqr(cur);
					if (d < best) {
						best = d;
						next = c;
					}
				}
				if (next == null) {
					break;
				}
				chain.add(next);
				struck.add(next.getUUID());
				cur = next;
			}
			Vec3 prev = p.getEyePosition();
			float dmg = 8.0f + chargedBonus(p);
			for (LivingEntity target : chain) {
				Vec3 tp = target.position().add(0, target.getBbHeight() * 0.5, 0);
				AbilityHelpers.line(level, prev, tp, ParticleTypes.ELECTRIC_SPARK, 3.0);
				AbilityHelpers.hurt(p, target, AbilityHelpers.kinetic(p),
						dmg * com.herocraft.mod.hero.power.PowerCombos.wetElectricMultiplier(p, target));
				staticShock(level, target, 40);
				prev = tp;
			}
			AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.5f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "electric_dash", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			AbilityHelpers.addImpulse(p, p.getLookAngle().scale(1.7));
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 8, 4, false, false, false));
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.ELECTRIC_SPARK, 20, 0.3);
			AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 2.0f);
			ctx.triggerCooldown();
		}));

		// Electrical Storm: one enormous bolt of lightning at the aimed point for 60 damage, blowing a
		// small crater and leaving whatever it hits hard-slowed for at least 8 seconds.
		AbilityHandlers.register(KEY, "electrical_storm", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			Vec3 at = AbilityHelpers.aimPoint(p, 40.0);
			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
			if (bolt != null) {
				bolt.moveTo(at.x, at.y, at.z);
				bolt.setVisualOnly(true); // we deal our own damage; no wildfire
				bolt.setCause(p);
				level.addFreshEntity(bolt);
			}
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 4.0)) {
				AbilityHelpers.hurt(p, e, AbilityHelpers.kinetic(p), 60.0f);
				staticShock(level, e, 160); // 8 s
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 160, 4);
			}
			if (AbilityHelpers.canGrief()) {
				BlockPos c = BlockPos.containing(at);
				for (BlockPos bp : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 1, 2))) {
					if (bp.distToCenterSqr(at.x, at.y, at.z) <= 5.5
							&& level.getBlockState(bp).getDestroySpeed(level, bp) >= 0
							&& level.getBlockState(bp).getDestroySpeed(level, bp) < 50.0f) {
						level.destroyBlock(bp, false);
					}
				}
			}
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1, at.z, 120, 2.0, 2.0, 2.0, 0.4);
			level.playSound(null, BlockPos.containing(at), SoundEvents.LIGHTNING_BOLT_THUNDER,
					net.minecraft.sounds.SoundSource.PLAYERS, 3.0f, 0.7f);
			ctx.triggerCooldown();
		}));

		// Power Surge (replaces Electromagnetic Pull): energise redstone from up to 50 blocks away by
		// planting a short-lived block of redstone against whatever you are looking at.
		AbilityHandlers.register(KEY, "electromagnetic_pull", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			BlockHitResult hit = AbilityHelpers.raycastBlock(p, 50.0);
			if (hit.getType() != HitResult.Type.BLOCK) {
				ctx.actionBar("message.herocraft.electro.no_target");
				return;
			}
			BlockPos face = hit.getBlockPos().relative(hit.getDirection());
			if (level.getBlockState(face).isAir() || level.getBlockState(face).canBeReplaced()) {
				TempBlocks.place(level, face, Blocks.REDSTONE_BLOCK.defaultBlockState(), 120); // ~6 s of power
			}
			AbilityHelpers.line(level, p.getEyePosition(), Vec3.atCenterOf(face), ParticleTypes.ELECTRIC_SPARK, 2.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, face.getX() + 0.5, face.getY() + 0.5, face.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.1);
			AbilityHelpers.sound(p, SoundEvents.LODESTONE_COMPASS_LOCK, 1.0f, 1.4f);
			ctx.triggerCooldown();
		}));

		// Charged Mode: +10 to every move, Speed III, and a body full of live current. It drains a
		// dedicated charge bar (~25 s); getting hit while charged costs you 5 health and staggers you
		// with your own static shock.
		AbilityHandlers.register(KEY, "charged_mode", Handlers.toggle(
				ctx -> {
					ModeMeter.ensureSeeded(ctx, "charged_mode", MAX_CHARGE);
					if (!ModeMeter.hasCharge(ctx, "charged_mode", CHARGE_MIN_ENTER)) {
						ctx.setToggled(false);
						ctx.actionBar("message.herocraft.electro.charge_low");
						return;
					}
					chargedOn(ctx);
				},
				ElectrokinesisHandlers::chargedOff,
				ctx -> {
					chargedOn(ctx);
					AbilityHelpers.modeAura(ctx.player(), ParticleTypes.ELECTRIC_SPARK, 4);
					if (!ModeMeter.drain(ctx, "charged_mode", MAX_CHARGE, CHARGE_DRAIN)) {
						ctx.setToggled(false);
						chargedOff(ctx);
						ctx.actionBar("message.herocraft.electro.charge_out");
					}
				}));

		// charged_mode reactive backlash: hitting a charged hero jolts *them* for 5 and static-shocks them.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && chargedModeActive(sp) && source.getEntity() instanceof LivingEntity) {
				sp.setHealth(Math.max(1.0f, sp.getHealth() - 5.0f)); // setHealth: never re-enters the damage pipeline
				sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2, false, true, true));
				if (sp.level() instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, sp.getX(), sp.getY() + 1, sp.getZ(), 16, 0.4, 0.7, 0.4, 0.15);
				}
			}
		});

		// charged melee still delivers a bonus jolt
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && chargedModeActive(sp)
					&& entity instanceof LivingEntity le) {
				AbilityHelpers.hurt(sp, le, AbilityHelpers.kinetic(sp), 3.0f);
				if (world instanceof ServerLevel sl) {
					staticShock(sl, le, 40);
				}
			}
			return InteractionResult.PASS;
		});

		com.herocraft.mod.hero.PowerPassives.registerTick(KEY, player -> {
			ModeMeter.regen(player, Powers.byKey(KEY), "charged_mode", MAX_CHARGE, CHARGE_REGEN, chargedModeActive(player));
			if (!(player.level() instanceof ServerLevel level)) {
				return;
			}
			if (player.tickCount % 5 == 0 && !SHOCKED.isEmpty()) {
				long now = level.getGameTime();
				SHOCKED.entrySet().removeIf(e -> {
					if (e.getValue() <= now) {
						return true;
					}
					if (level.getEntity(e.getKey()) instanceof LivingEntity le && le.isAlive()) {
						level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
								le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 6, 0.35, 0.5, 0.35, 0.08);
						return false;
					}
					return true;
				});
			}
		});
	}

	/**
	 * Expire stale static-shock markers. The passive tick above only runs while some player actually
	 * has Electrokinesis selected, so without an unconditional owner an entry left behind by a player
	 * who switched power or logged out would never be removed -- see {@code ServerStateReset}.
	 */
	public static void pruneExpired(long now) {
		if (!SHOCKED.isEmpty()) {
			SHOCKED.values().removeIf(expiry -> expiry <= now);
		}
	}

	public static void clearSessionState() {
		SHOCKED.clear();
	}

	private static void chargedOn(AbilityContext ctx) {
		PowerToggles.effect(ctx.player(), MobEffects.MOVEMENT_SPEED, 2, true); // Speed III
	}

	private static void chargedOff(AbilityContext ctx) {
		PowerToggles.clearEffect(ctx.player(), MobEffects.MOVEMENT_SPEED);
	}
}
