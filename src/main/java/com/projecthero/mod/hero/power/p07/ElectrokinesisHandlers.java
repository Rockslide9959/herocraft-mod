package com.projecthero.mod.hero.power.p07;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.TempBlocks;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 07 — Electrokinesis. Deliberately weaker than Thor's lightning: uses
 * {@code DamageTypes.PLAYER_ATTACK} (armour applies). Never uses Mjolnir/worthiness state.
 *
 * <h2>Charge (v0.10.14)</h2>
 * Every ability spends from one shared <b>Charge</b> cell (0..{@link #MAX_CHARGE} = 1000). It
 * regenerates on its own, far faster during a thunderstorm, and refills instantly if you stand near a
 * lightning strike. An electrokinetic takes no lightning damage at all. Attacks stun their targets,
 * every ability crackles power into nearby redstone for five seconds, and powered rails energise as
 * you run over them.
 */
public final class ElectrokinesisHandlers {
	private static final String KEY = "power_07_electrokinesis";

	/** Charge cell. */
	static final float MAX_CHARGE = 1000.0f;
	private static final float REGEN_CALM = MAX_CHARGE / (40 * 20);   // ~40 s from empty
	private static final float REGEN_STORM = MAX_CHARGE / (9 * 20);   // ~9 s in a thunderstorm
	private static final float CHARGED_MODE_DRAIN = MAX_CHARGE / (25 * 20); // Charged Mode lasts ~25 s

	private static final float COST_BOLT = 45.0f;
	private static final float COST_CHAIN = 90.0f;
	private static final float COST_DASH = 60.0f;
	private static final float COST_SURGE = 160.0f;
	private static final float COST_EMP = 220.0f;
	private static final float COST_STORM = 550.0f;
	private static final float COST_POWER_SURGE = 80.0f;

	private static final net.minecraft.resources.ResourceLocation CHARGED_MELEE =
			com.projecthero.mod.ProjectHeroMod.id("electro_charged_melee");

	/** entityId -> game time the static shock visual expires. Ticked from the power's passive tick. */
	private static final Map<Integer, Long> SHOCKED = new ConcurrentHashMap<>();
	/** blockpos long -> game time an EMP suppression expires. */
	private static final Map<Long, Long> EMP_UNTIL = new ConcurrentHashMap<>();
	/** blockpos long -> game time a rail we energised should revert. */
	private static final Map<Long, Long> RAIL_REVERT = new ConcurrentHashMap<>();

	private ElectrokinesisHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	// ---- charge cell ------------------------------------------------------------------------

	private static boolean seeded(AbilityContext ctx) {
		return ExperimentalPowers.state(ctx.player()).resources.containsKey(KEY + "/ecell");
	}

	/** Spend {@code amount} from the cell, or refuse (with a message) if it is too low. */
	private static boolean spend(AbilityContext ctx, float amount) {
		if (!seeded(ctx)) {
			ctx.setResource("ecell", MAX_CHARGE, MAX_CHARGE);
		}
		if (ctx.resource("ecell") < amount) {
			ctx.actionBar("message.projecthero.electro.charge_low");
			return false;
		}
		ctx.addResource("ecell", -amount, MAX_CHARGE);
		return true;
	}

	private static boolean chargedModeActive(Player p) {
		Power power = power();
		return power != null && p instanceof ServerPlayer sp && ExperimentalPowers.owns(sp, power)
				&& ExperimentalPowers.isToggled(sp, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6));
	}

	/** Charged Mode adds a flat +10 to every Electrokinesis attack. */
	private static float chargedBonus(ServerPlayer p) {
		return chargedModeActive(p) ? 10.0f : 0.0f;
	}

	/** Apply the signature static shock / paralysis to a target and start its spark visual. */
	private static void staticShock(ServerLevel level, LivingEntity target, int ticks) {
		AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, ticks, 5); // near-stun
		AbilityHelpers.applyControl(target, MobEffects.WEAKNESS, ticks, 1);
		SHOCKED.put(target.getId(), level.getGameTime() + ticks);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
				target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12, 0.35, 0.5, 0.35, 0.12);
	}

	/** Briefly (5 s) power any redstone within range by dropping a temporary redstone block near it. */
	private static void powerRedstone(ServerLevel level, Vec3 centre) {
		if (!AbilityHelpers.canGrief()) {
			return;
		}
		BlockPos c = BlockPos.containing(centre);
		BlockPos best = null;
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-4, -3, -4), c.offset(4, 3, 4))) {
			if (respondsToRedstone(level.getBlockState(bp))) {
				BlockPos side = bp.above();
				if (level.getBlockState(side).canBeReplaced()) {
					best = side.immutable();
					break;
				}
			}
		}
		if (best != null) {
			TempBlocks.place(level, best, Blocks.REDSTONE_BLOCK.defaultBlockState(), 100);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5,
					10, 0.3, 0.3, 0.3, 0.05);
		}
	}

	private static boolean respondsToRedstone(BlockState st) {
		return st.is(Blocks.REDSTONE_LAMP) || st.is(Blocks.REDSTONE_WIRE) || st.is(Blocks.DISPENSER)
				|| st.is(Blocks.DROPPER) || st.is(Blocks.PISTON) || st.is(Blocks.STICKY_PISTON)
				|| st.is(Blocks.NOTE_BLOCK) || st.is(Blocks.TNT) || st.is(Blocks.HOPPER)
				|| st.getBlock() instanceof DoorBlock || st.is(Blocks.IRON_DOOR) || st.is(Blocks.IRON_TRAPDOOR)
				|| st.is(Blocks.POWERED_RAIL) || st.is(Blocks.BELL);
	}

	public static void register() {
		// R -- Electric Bolt (10). Shift + R -- Chain Bolt (9 per hop).
		AbilityHandlers.register(KEY, "electric_bolt", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (p.isShiftKeyDown()) {
				if (!spend(ctx, COST_CHAIN)) {
					return;
				}
				chainBolt(ctx, 9.0f);
				ctx.triggerCooldown(8 * 20);
				return;
			}
			if (!spend(ctx, COST_BOLT)) {
				return;
			}
			LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
			Vec3 end = AbilityHelpers.aimPoint(p, 24.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), end, ParticleTypes.ELECTRIC_SPARK, 3.0);
			if (t != null) {
				float dmg = 10.0f + chargedBonus(p);
				AbilityHelpers.hurt(p, t, AbilityHelpers.kinetic(p),
						dmg * com.projecthero.mod.hero.power.PowerCombos.wetElectricMultiplier(p, t));
				staticShock(ctx.level(), t, 60);
				powerRedstone(ctx.level(), t.position());
			} else {
				powerRedstone(ctx.level(), end);
			}
			AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.8f);
			ctx.triggerCooldown();
		}));

		// G -- Electrical Surge (18 in a 20-block radius). Shift + G -- EMP (disable nearby tech for 6 s).
		AbilityHandlers.register(KEY, "chain_lightning", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			if (p.isShiftKeyDown()) {
				if (!spend(ctx, COST_EMP)) {
					return;
				}
				emp(ctx, 20.0);
				ctx.triggerCooldown(30 * 20);
				return;
			}
			if (!spend(ctx, COST_SURGE)) {
				return;
			}
			float dmg = 18.0f + chargedBonus(p);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 20.0)) {
				AbilityHelpers.hurt(p, e, AbilityHelpers.kinetic(p),
						dmg * com.projecthero.mod.hero.power.PowerCombos.wetElectricMultiplier(p, e));
				AbilityHelpers.knockbackFrom(e, p.position(), 1.2);
				staticShock(level, e, 60);
			}
			for (int i = 0; i < 40; i++) {
				double a = level.random.nextDouble() * Math.PI * 2;
				double r = level.random.nextDouble() * 20.0;
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + Math.cos(a) * r,
						p.getY() + 0.3 + level.random.nextDouble() * 2.0, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
			powerRedstone(level, p.position());
			AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, 1.4f);
			ctx.triggerCooldown(10 * 20);
		}));

		AbilityHandlers.register(KEY, "electric_dash", Handlers.instant(ctx -> {
			if (!spend(ctx, COST_DASH)) {
				return;
			}
			ServerPlayer p = ctx.player();
			AbilityHelpers.addImpulse(p, p.getLookAngle().scale(1.7));
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 8, 4, false, false, false));
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.ELECTRIC_SPARK, 20, 0.3);
			AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 2.0f);
			ctx.triggerCooldown();
		}));

		// Z -- Storm Bolt. Hold Z for 5 s (lightning particles gathering) before a single 55-damage
		// strike. 65 s cooldown.
		AbilityHandlers.register(KEY, "electrical_storm", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("storm_start") > 0.5f) {
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
					return;
				}
				if (!seeded(ctx)) {
					ctx.setResource("ecell", MAX_CHARGE, MAX_CHARGE);
				}
				if (ctx.resource("ecell") < COST_STORM) {
					ctx.actionBar("message.projecthero.electro.charge_low");
					return;
				}
				ctx.setResource("storm_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				float start = ctx.resource("storm_start");
				if (start <= 0.5f) {
					return;
				}
				long held = ctx.player().level().getGameTime() - (long) start;
				ctx.setResource("storm_start", 0, 1.0e12f);
				ctx.setResource("storm_charge", 0, 100);
				if (held >= 5 * 20) {
					fireStorm(ctx);
				} else {
					AbilityHelpers.sound(ctx.player(), SoundEvents.FIRE_EXTINGUISH, 0.6f, 0.8f);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				float start = ctx.resource("storm_start");
				if (start <= 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) start;
				if (held > 5 * 20 + 40) {
					fireStorm(ctx);
					return;
				}
				double frac = Math.min(1.0, held / 100.0);
				ctx.setResource("storm_charge", (float) (frac * 100.0), 100);
				int n = 3 + (int) (frac * 18);
				for (int i = 0; i < n; i++) {
					double a = p.level().random.nextDouble() * Math.PI * 2;
					double r = 0.6 + p.level().random.nextDouble() * (1.6 * frac);
					ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK,
							p.getX() + Math.cos(a) * r, p.getY() + 0.4 + p.level().random.nextDouble() * 2.2,
							p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
				}
				if (held % 10 == 0) {
					AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 0.6f + (float) frac);
				}
			}
		});

		// V -- Power Surge: plant a short-lived redstone block against whatever you are looking at.
		AbilityHandlers.register(KEY, "electromagnetic_pull", Handlers.instant(ctx -> {
			if (!spend(ctx, COST_POWER_SURGE)) {
				return;
			}
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			BlockHitResult hit = AbilityHelpers.raycastBlock(p, 50.0);
			if (hit.getType() != HitResult.Type.BLOCK) {
				ctx.actionBar("message.projecthero.electro.no_target");
				return;
			}
			BlockPos face = hit.getBlockPos().relative(hit.getDirection());
			if (level.getBlockState(face).isAir() || level.getBlockState(face).canBeReplaced()) {
				TempBlocks.place(level, face, Blocks.REDSTONE_BLOCK.defaultBlockState(), 120);
			}
			AbilityHelpers.line(level, p.getEyePosition(), Vec3.atCenterOf(face), ParticleTypes.ELECTRIC_SPARK, 2.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, face.getX() + 0.5, face.getY() + 0.5, face.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.1);
			AbilityHelpers.sound(p, SoundEvents.LODESTONE_COMPASS_LOCK, 1.0f, 1.4f);
			ctx.triggerCooldown();
		}));

		// C -- Charged Mode: +10 ability damage, +8 melee, Speed III, a body full of current. Drains the
		// charge cell (~25 s); getting hit while charged costs 5 health and staggers you.
		AbilityHandlers.register(KEY, "charged_mode", Handlers.toggle(
				ctx -> {
					if (!seeded(ctx)) {
						ctx.setResource("ecell", MAX_CHARGE, MAX_CHARGE);
					}
					if (ctx.resource("ecell") < 100.0f) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.electro.charge_low");
						return;
					}
					chargedOn(ctx);
				},
				ElectrokinesisHandlers::chargedOff,
				ctx -> {
					chargedOn(ctx);
					AbilityHelpers.modeAura(ctx.player(), ParticleTypes.ELECTRIC_SPARK, 4);
					ctx.addResource("ecell", -CHARGED_MODE_DRAIN, MAX_CHARGE);
					if (ctx.resource("ecell") <= 0.0f) {
						ctx.setToggled(false);
						chargedOff(ctx);
						ctx.actionBar("message.projecthero.electro.charge_out");
					}
				}));

		// charged_mode reactive backlash: hitting a charged hero jolts *them* for 5 and static-shocks them.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && chargedModeActive(sp) && source.getEntity() instanceof LivingEntity) {
				sp.setHealth(Math.max(1.0f, sp.getHealth() - 5.0f));
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

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, owned) -> {
			if (!owned) {
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, CHARGED_MELEE);
				PowerToggles.clearEffect(player, MobEffects.MOVEMENT_SPEED);
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, ElectrokinesisHandlers::passiveTick);
	}

	// ---- shared attack routines --------------------------------------------------------------

	private static void chainBolt(AbilityContext ctx, float perHop) {
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
		while (chain.size() < 24) {
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
		float dmg = perHop + chargedBonus(p);
		for (LivingEntity target : chain) {
			Vec3 tp = target.position().add(0, target.getBbHeight() * 0.5, 0);
			AbilityHelpers.line(level, prev, tp, ParticleTypes.ELECTRIC_SPARK, 3.0);
			AbilityHelpers.hurt(p, target, AbilityHelpers.kinetic(p),
					dmg * com.projecthero.mod.hero.power.PowerCombos.wetElectricMultiplier(p, target));
			staticShock(level, target, 40);
			prev = tp;
		}
		powerRedstone(level, first.position());
		AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.5f);
	}

	private static void fireStorm(AbilityContext ctx) {
		ctx.setResource("storm_start", 0, 1.0e12f);
		ctx.setResource("storm_charge", 0, 100);
		if (!spend(ctx, COST_STORM)) {
			return;
		}
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 at = AbilityHelpers.aimPoint(p, 40.0);
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			bolt.setCause(p);
			level.addFreshEntity(bolt);
		}
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 5.0)) {
			AbilityHelpers.hurtBurst(p, e, 55.0f + chargedBonus(p));
			staticShock(level, e, 160);
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
		powerRedstone(level, at);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1, at.z, 120, 2.0, 2.0, 2.0, 0.4);
		level.playSound(null, BlockPos.containing(at), SoundEvents.LIGHTNING_BOLT_THUNDER,
				net.minecraft.sounds.SoundSource.PLAYERS, 3.0f, 0.7f);
		ctx.triggerCooldown();
	}

	/** EMP: suppress redstone contraptions, powered doors, minecarts and mod electronics for 6 s. */
	private static void emp(AbilityContext ctx, double range) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		long until = level.getGameTime() + 6 * 20;
		BlockPos c = p.blockPosition();
		int r = (int) range;
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, -6, -r), c.offset(r, 6, r))) {
			if (bp.distSqr(c) > range * range) {
				continue;
			}
			BlockState st = level.getBlockState(bp);
			boolean tech = respondsToRedstone(st) || st.is(Blocks.OBSERVER) || st.is(Blocks.REPEATER)
					|| st.is(Blocks.COMPARATOR) || st.hasProperty(BlockStateProperties.POWERED);
			if (tech) {
				EMP_UNTIL.put(bp.asLong(), until);
				if (st.hasProperty(BlockStateProperties.POWERED) && st.getValue(BlockStateProperties.POWERED)) {
					level.setBlock(bp, st.setValue(BlockStateProperties.POWERED, false), 3);
				}
				if (st.hasProperty(BlockStateProperties.OPEN)
						&& (st.getBlock() instanceof DoorBlock || st.is(Blocks.IRON_TRAPDOOR))
						&& st.getValue(BlockStateProperties.OPEN)) {
					level.setBlock(bp, st.setValue(BlockStateProperties.OPEN, false), 10);
				}
			}
		}
		for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(range))) {
			if (e instanceof AbstractMinecart mc) {
				mc.setDeltaMovement(Vec3.ZERO);
			}
			if (e instanceof LivingEntity le && isTechnological(e)) {
				AbilityHelpers.applyControl(le, MobEffects.MOVEMENT_SLOWDOWN, 6 * 20, 6);
				AbilityHelpers.applyControl(le, MobEffects.WEAKNESS, 6 * 20, 3);
				staticShock(level, le, 6 * 20);
			}
		}
		for (int i = 0; i < 60; i++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double rr = level.random.nextDouble() * range;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + Math.cos(a) * rr,
					p.getY() + 0.5 + level.random.nextDouble() * 1.5, p.getZ() + Math.sin(a) * rr, 1, 0, 0, 0, 0);
		}
		AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 1.4f, 0.5f);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.electro.emp"), true);
	}

	private static boolean isTechnological(Entity e) {
		net.minecraft.resources.ResourceLocation id = EntityType.getKey(e.getType());
		String path = id.getPath();
		return e.getType() == EntityType.IRON_GOLEM || path.contains("iron_man") || path.contains("stark")
				|| path.contains("sentinel") || path.contains("drone") || path.contains("turret")
				|| path.contains("robot") || path.contains("mech");
	}

	/** True while the block at {@code pos} is under an active EMP. Consulted by mod machinery if needed. */
	public static boolean empSuppressed(ServerLevel level, BlockPos pos) {
		Long until = EMP_UNTIL.get(pos.asLong());
		return until != null && until > level.getGameTime();
	}

	// ---- passive tick ----------------------------------------------------------------------

	private static void passiveTick(ServerPlayer player) {
		Power power = power();
		if (power == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (!ExperimentalPowers.state(player).resources.containsKey(KEY + "/ecell")) {
			ExperimentalPowers.setResource(player, power, "ecell", MAX_CHARGE, MAX_CHARGE);
		}
		float cell = ExperimentalPowers.getResource(player, power, "ecell");

		// Standing near a lightning strike refills the cell instantly.
		boolean nearBolt = !level.getEntitiesOfClass(LightningBolt.class, player.getBoundingBox().inflate(8.0)).isEmpty();
		if (nearBolt) {
			ExperimentalPowers.setResource(player, power, "ecell", MAX_CHARGE, MAX_CHARGE);
			if (player.tickCount % 4 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(),
						20, 0.5, 1.0, 0.5, 0.2);
			}
		} else if (cell < MAX_CHARGE && !chargedModeActive(player)) {
			boolean storm = level.isThundering();
			ExperimentalPowers.addResource(player, power, "ecell", storm ? REGEN_STORM : REGEN_CALM, MAX_CHARGE);
		}

		// Charged Mode: +8 melee.
		if (chargedModeActive(player)) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, CHARGED_MELEE, 8.0,
					AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, CHARGED_MELEE);
		}

		// Powered rails energise briefly as you run over them, reverting a few seconds later.
		if (player.getDeltaMovement().horizontalDistanceSqr() > 0.02) {
			for (BlockPos bp : new BlockPos[] { player.blockPosition().below(), player.blockPosition() }) {
				BlockState st = level.getBlockState(bp);
				if (st.is(Blocks.POWERED_RAIL) && st.hasProperty(BlockStateProperties.POWERED)
						&& !st.getValue(BlockStateProperties.POWERED)) {
					level.setBlock(bp, st.setValue(BlockStateProperties.POWERED, true), 3);
					RAIL_REVERT.put(bp.asLong(),
							level.getGameTime() + 60);
				}
			}
		}
		if (!RAIL_REVERT.isEmpty() && player.tickCount % 10 == 0) {
			long now = level.getGameTime();
			RAIL_REVERT.entrySet().removeIf(e -> {
				if (e.getValue() > now) {
					return false;
				}
				BlockPos bp = BlockPos.of(e.getKey());
				BlockState st = level.getBlockState(bp);
				if (st.is(Blocks.POWERED_RAIL) && st.hasProperty(BlockStateProperties.POWERED)
						&& st.getValue(BlockStateProperties.POWERED)) {
					level.setBlock(bp, st.setValue(BlockStateProperties.POWERED, false), 3);
				}
				return true;
			});
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
	}

	// ---- housekeeping --------------------------------------------------------------------------

	public static void pruneExpired(long now) {
		if (!SHOCKED.isEmpty()) {
			SHOCKED.values().removeIf(expiry -> expiry <= now);
		}
		if (!EMP_UNTIL.isEmpty()) {
			EMP_UNTIL.values().removeIf(expiry -> expiry <= now);
		}
		if (!RAIL_REVERT.isEmpty()) {
			RAIL_REVERT.values().removeIf(expiry -> expiry <= now);
		}
	}

	public static void clearSessionState() {
		SHOCKED.clear();
		EMP_UNTIL.clear();
		RAIL_REVERT.clear();
	}

	private static void chargedOn(AbilityContext ctx) {
		PowerToggles.effect(ctx.player(), MobEffects.MOVEMENT_SPEED, 2, true); // Speed III
	}

	private static void chargedOff(AbilityContext ctx) {
		PowerToggles.clearEffect(ctx.player(), MobEffects.MOVEMENT_SPEED);
		PowerToggles.clearModifier(ctx.player(), Attributes.ATTACK_DAMAGE, CHARGED_MELEE);
	}
}
