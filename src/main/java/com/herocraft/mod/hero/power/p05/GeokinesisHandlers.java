package com.herocraft.mod.hero.power.p05;

import com.herocraft.mod.hero.Ability;
import com.herocraft.mod.hero.AbilityContext;
import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.AbilitySlot;
import com.herocraft.mod.hero.Power;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.hero.PowerPassives;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.ConjuredStructures;
import com.herocraft.mod.hero.power.Handlers;
import com.herocraft.mod.hero.power.PowerToggles;
import com.herocraft.mod.hero.power.TempBlocks;
import com.herocraft.mod.hero.power.TimedSelfFlight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Power 05 — Geokinesis. */
public final class GeokinesisHandlers {
	private static final String KEY = "power_05_geokinesis";
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockParticleOption STONE_DUST = new BlockParticleOption(ParticleTypes.BLOCK, STONE);
	private static final net.minecraft.resources.ResourceLocation ARMOR_KB =
			com.herocraft.mod.HeroCraftMod.id("earth_armor_kb");
	private static final net.minecraft.resources.ResourceLocation ARMOR_SPD =
			com.herocraft.mod.HeroCraftMod.id("earth_armor_slow");
	private static final net.minecraft.resources.ResourceLocation ARMOR_ATK =
			com.herocraft.mod.HeroCraftMod.id("earth_armor_atk");

	private static final float MAX_STRAIN = 500.0f;
	private static final float STRAIN_DRAIN = MAX_STRAIN / (25 * 20); // earth armor holds ~25 s
	private static final float STRAIN_REGEN = MAX_STRAIN / (40 * 20); // recharges over ~40 s while off

	private GeokinesisHandlers() {
	}

	private static boolean armorActive(ServerPlayer p) {
		Power power = Powers.byKey(KEY);
		return power != null && com.herocraft.mod.hero.ExperimentalPowers.owns(p, power)
				&& com.herocraft.mod.hero.ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}

	/** Earth Armor adds a flat +8 to every Geokinesis attack. */
	private static float armorBonus(ServerPlayer p) {
		return armorActive(p) ? 8.0f : 0.0f;
	}

	public static void register() {
		AbilityHandlers.register(KEY, "rock_shot", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
			Vec3 end = AbilityHelpers.aimPoint(p, 24.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), end, STONE_DUST, 3.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 8.0f + armorBonus(p));
				AbilityHelpers.knockbackFrom(t, p.position(), 1.4);
				AbilityHelpers.slow7s(t);
			}
			AbilityHelpers.sound(p, SoundEvents.STONE_BREAK, 1.0f, 0.7f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "earth_spike", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 at = AbilityHelpers.aimPoint(p, 18.0);
			LivingEntity direct = AbilityHelpers.raycastEntity(p, 18.0);
			if (direct != null) {
				AbilityHelpers.hurt(p, direct, 17.0f + armorBonus(p));
				AbilityHelpers.push(direct, new Vec3(0, 0.9, 0));
				AbilityHelpers.slow7s(direct);
			}
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 2.0)) {
				if (e == direct) {
					continue;
				}
				AbilityHelpers.hurt(p, e, 10.0f + armorBonus(p));
				AbilityHelpers.push(e, new Vec3(0, 0.7, 0));
				AbilityHelpers.slow7s(e);
			}
			ServerLevel level = ctx.level();
			BlockPos base = BlockPos.containing(at).below();
			if (AbilityHelpers.canGrief() && isEarth(level.getBlockState(base))) {
				// a 3-block-tall dripstone spike instead of a plain stone pillar
				TempBlocks.place(level, base.above(1), Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 160);
				TempBlocks.place(level, base.above(2), Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 160);
				TempBlocks.place(level, base.above(3), Blocks.POINTED_DRIPSTONE.defaultBlockState(), 160);
			}
			level.sendParticles(STONE_DUST, at.x, at.y, at.z, 30, 0.4, 0.6, 0.4, 0.1);
			AbilityHelpers.sound(p, SoundEvents.POINTED_DRIPSTONE_LAND, 1.0f, 0.7f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "stone_wall", Handlers.instant(GeokinesisHandlers::stoneWall));

		AbilityHandlers.register(KEY, "earthquake", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			double r = 8.0;
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
				AbilityHelpers.hurt(p, e, 30.0f + armorBonus(p));
				AbilityHelpers.knockbackFrom(e, p.position(), 1.2);
				AbilityHelpers.push(e, new Vec3(0, 0.6, 0));
				AbilityHelpers.slow7s(e);
				AbilityHelpers.applyControl(e, MobEffects.CONFUSION, 100, 0);
			}
			for (int i = 0; i < 40; i++) {
				double a = i / 40.0 * Math.PI * 2;
				double d = 2 + (i % 5);
				double bx = p.getX() + Math.cos(a) * d;
				double bz = p.getZ() + Math.sin(a) * d;
				BlockPos bp = BlockPos.containing(bx, p.getY() - 1, bz);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(bp)),
						bx, p.getY() + 0.1, bz, 4, 0.2, 0.15, 0.2, 0.05);
			}
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.4f);
			level.playSound(null, p.blockPosition(), SoundEvents.STONE_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.5f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "boulder_lift", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (ctx.resource("boulder") > 0.5f) {
				Vec3 at = AbilityHelpers.aimPoint(p, 20.0);
				float geoBonus = com.herocraft.mod.hero.power.PowerCombos.geoStrengthBonus(p);
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 3.0 + geoBonus * 0.3)) {
					AbilityHelpers.hurt(p, e, 15.0f + geoBonus + armorBonus(p));
					AbilityHelpers.knockbackFrom(e, p.getEyePosition(), 1.6);
					AbilityHelpers.slow7s(e);
				}
				ctx.level().sendParticles(STONE_DUST, at.x, at.y, at.z, 50, 1.0, 1.0, 1.0, 0.2);
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), at, STONE_DUST, 2.0);
				AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.7f);
				ctx.setResource("boulder", 0, 1);
				ctx.triggerCooldown();
			} else {
				ctx.setResource("boulder", 1, 1);
				ctx.setResource("boulder_ticks", 120, 120);
				ctx.actionBar("message.herocraft.ability.boulder_ready");
			}
		}, GeokinesisHandlers::boulderTick));

		AbilityHandlers.register(KEY, "earth_armor", Handlers.toggle(
				ctx -> {
					com.herocraft.mod.hero.power.ModeMeter.ensureSeeded(ctx, "earth_armor", MAX_STRAIN);
					if (!com.herocraft.mod.hero.power.ModeMeter.hasCharge(ctx, "earth_armor", 40.0f)) {
						ctx.setToggled(false);
						ctx.actionBar("message.herocraft.geo.strain_low");
						return;
					}
					earthArmorOn(ctx);
				},
				GeokinesisHandlers::earthArmorOff,
				ctx -> {
					earthArmorOn(ctx);
					AbilityHelpers.modeAura(ctx.player(),
							new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), 4);
					if (!com.herocraft.mod.hero.power.ModeMeter.drain(ctx, "earth_armor", MAX_STRAIN, STRAIN_DRAIN)) {
						ctx.setToggled(false);
						earthArmorOff(ctx);
						ctx.actionBar("message.herocraft.geo.strain_out");
					}
				}));

		PowerPassives.registerTick(KEY, player -> {
			Power power = Powers.byKey(KEY);
			Ability wall = power.ability(AbilitySlot.SLOT_3);
			TimedSelfFlight.tick(player, power, wall, "rock", STONE_DUST);
			com.herocraft.mod.hero.power.ModeMeter.regen(player, power, "earth_armor", MAX_STRAIN, STRAIN_REGEN, armorActive(player));
			if (player.onGround() && player.level() instanceof ServerLevel sl
					&& isEarth(sl.getBlockState(player.blockPosition().below()))) {
				player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, false, false, false));
			}
		});
	}

	/**
	 * Look straight up → a rock dome you (and only you) can left-click away. Look straight down → a
	 * stone bridge ahead. Sneak + look straight down → 20 seconds of rock flight. Otherwise → a
	 * 4×4 rock wall. Every construct lasts 25 seconds.
	 */
	private static void stoneWall(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Power power = ctx.power();

		if (TimedSelfFlight.isActive(p, power, "rock")) {
			return; // rock flight in progress -- the move is locked out
		}

		float pitch = p.getXRot();
		boolean lookUp = pitch < -75.0f;
		boolean lookDown = pitch > 75.0f;

		if (lookDown && p.isShiftKeyDown()) {
			if (TimedSelfFlight.start(p, power, ctx.ability(), "rock")) {
				AbilityHelpers.sound(p, SoundEvents.STONE_PLACE, 1.0f, 0.5f);
				AbilityHelpers.burst(level, p.position(), STONE_DUST, 40, 0.6);
			}
			return; // cooldown starts when the flight ends, not now
		}

		boolean built;
		if (lookUp) {
			built = ConjuredStructures.dome(p, level, STONE);
		} else if (lookDown) {
			built = ConjuredStructures.bridge(p, level, STONE);
		} else {
			built = ConjuredStructures.wall(p, level, STONE);
		}
		level.sendParticles(STONE_DUST, p.getX(), p.getY() + 1.0, p.getZ(), 40, 1.5, 1.5, 1.5, 0.1);
		AbilityHelpers.sound(p, SoundEvents.STONE_PLACE, 1.0f, 0.5f);
		if (built || !AbilityHelpers.canGrief()) {
			ctx.triggerCooldown();
		}
	}

	private static void boulderTick(AbilityContext ctx) {
		if (ctx.resource("boulder") < 0.5f) {
			return;
		}
		int t = (int) ctx.resource("boulder_ticks") - 1;
		if (t <= 0) {
			ctx.setResource("boulder", 0, 1);
			return;
		}
		ctx.setResource("boulder_ticks", t, 120);
		ServerPlayer p = ctx.player();
		Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(2.5));
		((ServerLevel) p.level()).sendParticles(STONE_DUST, hold.x, hold.y, hold.z, 4, 0.4, 0.4, 0.4, 0.0);
	}

	private static void earthArmorOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.effect(p, MobEffects.DAMAGE_RESISTANCE, 1, true);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB, 0.6, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, ARMOR_SPD, -0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK, 8.0, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void earthArmorOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.clearEffect(p, MobEffects.DAMAGE_RESISTANCE);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, ARMOR_SPD);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK);
	}

	private static boolean isEarth(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
				|| state.is(Blocks.GRAVEL) || state.is(Blocks.DEEPSLATE);
	}
}
