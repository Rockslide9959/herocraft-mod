package com.herocraft.mod.hero.power.p06;

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

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Power 06 — Crystalkinesis. */
public final class CrystalkinesisHandlers {
	private static final String KEY = "power_06_crystalkinesis";
	private static final BlockState CRYSTAL = Blocks.AMETHYST_BLOCK.defaultBlockState();
	private static final BlockState BARRIER = Blocks.TINTED_GLASS.defaultBlockState();
	private static final net.minecraft.core.particles.BlockParticleOption CRYSTAL_DUST =
			new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
					Blocks.AMETHYST_BLOCK.defaultBlockState());
	private static final net.minecraft.resources.ResourceLocation ARMOR_KB = com.herocraft.mod.HeroCraftMod.id("crystal_armor_kb");
	private static final net.minecraft.resources.ResourceLocation ARMOR_ATK = com.herocraft.mod.HeroCraftMod.id("crystal_armor_atk");

	private static final float MAX_STRAIN = 500.0f;
	private static final float STRAIN_DRAIN = MAX_STRAIN / (25 * 20); // crystal armor holds ~25 s
	private static final float STRAIN_REGEN = MAX_STRAIN / (40 * 20); // recharges over ~40 s while off

	private CrystalkinesisHandlers() {
	}

	private static boolean armorActive(ServerPlayer p) {
		Power power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(com.herocraft.mod.hero.AbilitySlot.SLOT_6));
	}

	/** Crystal Armor adds a flat +10 to every Crystalkinesis attack. */
	private static float armorBonus(ServerPlayer p) {
		return armorActive(p) ? 10.0f : 0.0f;
	}

	public static void register() {
		AbilityHandlers.register(KEY, "crystal_shard", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
			Vec3 end = AbilityHelpers.aimPoint(p, 24.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), end, CRYSTAL_DUST, 4.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), end, ParticleTypes.END_ROD, 1.5);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 10.0f + armorBonus(p));
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 0);
			}
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "crystal_spikes", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 at = AbilityHelpers.aimPoint(p, 18.0);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 3.0)) {
				AbilityHelpers.hurt(p, e, 19.0f + armorBonus(p));
				AbilityHelpers.push(e, new Vec3(0, 0.7, 0));
			}
			ServerLevel level = ctx.level();
			if (AbilityHelpers.canGrief()) {
				for (int i = 0; i < 5; i++) {
					BlockPos bp = BlockPos.containing(at.x + level.random.nextGaussian(), at.y, at.z + level.random.nextGaussian());
					TempBlocks.place(level, bp, Blocks.AMETHYST_CLUSTER.defaultBlockState(), 120);
				}
			}
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 30, 0.5, 0.6, 0.5, 0.1);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 0.8f);
			ctx.triggerCooldown();
		}));

		// Look up → dome (owner left-clicks to dismiss). Look down → bridge. Otherwise → 4×4 wall.
		// Every construct lasts 25 seconds.
		AbilityHandlers.register(KEY, "crystal_barrier", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			float pitch = p.getXRot();
			boolean built;
			if (pitch < -75.0f) {
				built = com.herocraft.mod.hero.power.ConjuredStructures.dome(p, level, BARRIER);
			} else if (pitch > 75.0f) {
				built = com.herocraft.mod.hero.power.ConjuredStructures.bridge(p, level, BARRIER);
			} else {
				built = com.herocraft.mod.hero.power.ConjuredStructures.wall(p, level, BARRIER);
			}
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.5, p.getZ(), 30, 1.2, 1.2, 1.2, 0.05);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.9f);
			if (built || !AbilityHelpers.canGrief()) {
				ctx.triggerCooldown();
			}
		}));

		AbilityHandlers.register(KEY, "crystal_eruption", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			double r = 8.0; // an 8-block ring of amethyst erupts from the ground around you
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
				AbilityHelpers.hurt(p, e, 34.0f + armorBonus(p));
				AbilityHelpers.knockbackFrom(e, p.position(), 1.3);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
			}
			if (AbilityHelpers.canGrief()) {
				// scatter jagged amethyst shards across the ground in range (like Crystal Spikes),
				// each fading away after a few seconds
				for (int i = 0; i < 22; i++) {
					double ang = level.random.nextDouble() * Math.PI * 2;
					double dist = level.random.nextDouble() * r;
					int bx = net.minecraft.util.Mth.floor(p.getX() + Math.cos(ang) * dist);
					int bz = net.minecraft.util.Mth.floor(p.getZ() + Math.sin(ang) * dist);
					int gy = p.blockPosition().getY() + 2;
					while (gy > level.getMinBuildHeight() + 1 && level.getBlockState(new BlockPos(bx, gy - 1, bz)).isAir()) {
						gy--;
					}
					int h = 1 + level.random.nextInt(3);
					for (int y = 0; y < h; y++) {
						BlockPos bp = new BlockPos(bx, gy + y, bz);
						if (level.getBlockState(bp).isAir() || level.getBlockState(bp).canBeReplaced()) {
							TempBlocks.place(level, bp,
									(y == h - 1 ? Blocks.AMETHYST_CLUSTER : Blocks.AMETHYST_BLOCK).defaultBlockState(), 120);
						}
					}
				}
			}
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.5, p.getZ(), 80, r / 2, 0.4, r / 2, 0.15);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 1.4f, 0.6f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "crystal_prison", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 16.0);
			if (t == null) {
				return;
			}
			// 7 s of near-total lockdown: Slowness X plus a jump lock so the target cannot move at all,
			// with an amethyst cage sealed around them for the same 7 s.
			boolean applied = AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 140, 9);
			AbilityHelpers.applyControl(t, MobEffects.JUMP, 140, -10); // negative Jump Boost = cannot jump
			if (AbilityHelpers.canGrief() && !(t instanceof net.minecraft.world.entity.player.Player)) {
				BlockPos base = t.blockPosition();
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
					TempBlocks.place(ctx.level(), base.relative(d), CRYSTAL, 140);
					TempBlocks.place(ctx.level(), base.relative(d).above(), CRYSTAL, 140);
				}
				TempBlocks.place(ctx.level(), base.above(2), CRYSTAL, 140);
			}
			ctx.level().sendParticles(ParticleTypes.END_ROD, t.getX(), t.getY() + 1, t.getZ(), 40, 0.5, 1.0, 0.5, 0.05);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 0.5f);
			if (applied || AbilityHelpers.canGrief()) {
				ctx.triggerCooldown();
			}
		}));

		AbilityHandlers.register(KEY, "crystal_armor", Handlers.toggle(
				ctx -> {
					ModeMeter.ensureSeeded(ctx, "crystal_armor", MAX_STRAIN);
					if (!ModeMeter.hasCharge(ctx, "crystal_armor", 40.0f)) {
						ctx.setToggled(false);
						ctx.actionBar("message.herocraft.crystal.strain_low");
						return;
					}
					armorOn(ctx);
				},
				CrystalkinesisHandlers::armorOff,
				ctx -> {
					armorOn(ctx);
					AbilityHelpers.modeAura(ctx.player(), CRYSTAL_DUST, 3);
					if (ctx.player().tickCount % 12 == 0) {
						AbilityHelpers.modeAura(ctx.player(), ParticleTypes.END_ROD, 1);
					}
					if (!ModeMeter.drain(ctx, "crystal_armor", MAX_STRAIN, STRAIN_DRAIN)) {
						ctx.setToggled(false);
						armorOff(ctx);
						ctx.actionBar("message.herocraft.crystal.strain_out");
					}
				}));

		com.herocraft.mod.hero.PowerPassives.registerTick(KEY, player ->
				ModeMeter.regen(player, Powers.byKey(KEY), "crystal_armor", MAX_STRAIN, STRAIN_REGEN, armorActive(player)));
	}

	private static void armorOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.effect(p, MobEffects.DAMAGE_RESISTANCE, 0, true);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB, 0.4, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK, 10.0, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void armorOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.clearEffect(p, MobEffects.DAMAGE_RESISTANCE);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK);
	}
}
