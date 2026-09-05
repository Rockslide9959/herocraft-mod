package com.herocraft.mod.hero.power.p09;

import com.herocraft.mod.hero.AbilityContext;
import com.herocraft.mod.hero.AbilityHandler;
import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.ConjuredStructures;
import com.herocraft.mod.hero.power.Handlers;
import com.herocraft.mod.hero.power.PowerToggles;
import com.herocraft.mod.hero.power.TempBlocks;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** Power 09 — Cryokinesis. */
public final class CryokinesisHandlers {
	private static final String KEY = "power_09_cryokinesis";
	/**
	 * Two cold gauges. {@code "frozen_armor"} is a classic reserve: starts full, bled slowly (~35 s)
	 * while the plating is worn, refills while it is off. {@code "freeze_beam"} is the opposite — a
	 * cold-<em>buildup</em> gauge that starts at zero and <em>climbs</em> while the beam is channelling
	 * (the cryokinetic is chilling themselves); at max (~4 s) frostbite forces the beam off, and it
	 * bleeds back toward zero while idle before it can be fired again.
	 */
	private static final float MAX_COLD = 500.0f;
	private static final float COLD_PER_BEAM_TICK = 6.0f;
	private static final float COLD_PER_ARMOR_TICK = MAX_COLD / (35 * 20);
	private static final float COLD_REGEN = MAX_COLD / (25 * 20);
	private static final float COLD_MIN = 20.0f;
	private static final BlockState SLIDE_ICE = Blocks.PACKED_ICE.defaultBlockState();
	private static final net.minecraft.resources.ResourceLocation ARMOR_KB = com.herocraft.mod.HeroCraftMod.id("frozen_armor_kb");
	private static final net.minecraft.resources.ResourceLocation SLIDE_SPD = com.herocraft.mod.HeroCraftMod.id("ice_slide_speed");

	private CryokinesisHandlers() {
	}

	private static void freeze(LivingEntity e, int ticks) {
		e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 200, e.getTicksFrozen() + ticks));
	}

	/** +10 to every Cryokinesis ability's damage while in a cold biome or standing in snow. */
	private static float coldBonus(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel sl)) {
			return 0.0f;
		}
		BlockPos pos = p.blockPosition();
		boolean coldBiome = sl.getBiome(pos).value().coldEnoughToSnow(pos);
		boolean inSnow = p.isInPowderSnow
				|| sl.getBlockState(pos).is(Blocks.SNOW) || sl.getBlockState(pos).is(Blocks.POWDER_SNOW)
				|| sl.getBlockState(pos.below()).is(Blocks.SNOW_BLOCK) || sl.getBlockState(pos.below()).is(Blocks.SNOW)
				|| sl.getBlockState(pos.below()).is(Blocks.POWDER_SNOW);
		return (coldBiome || inSnow) ? 10.0f : 0.0f;
	}

	public static void register() {
		AbilityHandlers.register(KEY, "ice_bolt", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 24.0), ParticleTypes.SNOWFLAKE, 3.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 7.0f + coldBonus(p));
				freeze(t, 200); // ~7 s of the freeze effect
				AbilityHelpers.slow7s(t); // Slowness III, 7 s
			}
			AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 0.8f, 1.6f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "freeze_beam", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				// Cold-buildup gauge: too close to frostbite to start channelling again yet.
				if (ctx.resource("freeze_beam") >= MAX_COLD - COLD_MIN) {
					ctx.actionBar("message.herocraft.cryo.exhausted");
					return;
				}
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
				ServerPlayer p = ctx.player();
				LivingEntity t = AbilityHelpers.raycastEntity(p, 16.0);
				AbilityHelpers.line(ctx.level(), p.getEyePosition().add(p.getLookAngle().scale(0.4)),
						AbilityHelpers.aimPoint(p, 16.0), ParticleTypes.SNOWFLAKE, 3.0);
				// The beam smothers fire it is aimed at.
				BlockHitResult bhr = AbilityHelpers.raycastBlock(p, 16.0);
				if (bhr.getType() == HitResult.Type.BLOCK) {
					BlockPos hitPos = bhr.getBlockPos();
					for (BlockPos bp : BlockPos.betweenClosed(hitPos.offset(-1, -1, -1), hitPos.offset(1, 1, 1))) {
						if (ctx.level().getBlockState(bp).is(BlockTags.FIRE)) {
							ctx.level().removeBlock(bp, false);
							ctx.level().sendParticles(ParticleTypes.SMOKE,
									bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.01);
						}
					}
				}
				if (t != null) {
					t.clearFire();
					freeze(t, 40); // much stronger freezing than Ice Bolt
					AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 4); // Slowness V, better than Ice Bolt
					if (p.tickCount % 5 == 0) {
						AbilityHelpers.hurt(p, t, AbilityHelpers.freeze(p), 1.5f);
					}
					if (p.tickCount % 20 == 0) {
						AbilityHelpers.hurt(p, t, AbilityHelpers.freeze(p), coldBonus(p));
					}
				}
				ctx.addResource("freeze_beam", COLD_PER_BEAM_TICK, MAX_COLD);
				if (ctx.resource("freeze_beam") >= MAX_COLD) {
					ctx.setResource("beaming", 0, 1);
					ctx.actionBar("message.herocraft.cryo.exhausted");
				}
			}
		});

		AbilityHandlers.register(KEY, "ice_slide", Handlers.toggle(Handlers.noop(),
				CryokinesisHandlers::slideOff, CryokinesisHandlers::slideTick));

		AbilityHandlers.register(KEY, "absolute_zero", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			double r = 8.0;
			float dmg = 20.0f + coldBonus(p);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
				freeze(e, 25 * 20); // the freeze effect for 25 s
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 25 * 20, 3);
				AbilityHelpers.hurt(p, e, AbilityHelpers.freeze(p), dmg);
				e.clearFire();
			}
			if (AbilityHelpers.canGrief()) {
				for (BlockPos bp : BlockPos.betweenClosed(p.blockPosition().offset((int) -r, -1, (int) -r),
						p.blockPosition().offset((int) r, 0, (int) r))) {
					if (bp.distToCenterSqr(p.getX(), p.getY(), p.getZ()) <= r * r
							&& level.getBlockState(bp).getFluidState().is(Fluids.WATER)) {
						TempBlocks.place(level, bp, Blocks.ICE.defaultBlockState(), 200);
					}
				}
				layerSnow(p, level, (int) r);
			}
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 120, r / 2, 0.5, r / 2, 0.05);
			AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 1.4f, 0.5f);
			ctx.triggerCooldown();
		}));

		// Look up → dome (owner left-clicks to dismiss). Look down → bridge. Otherwise → 4×4 wall.
		AbilityHandlers.register(KEY, "ice_wall", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
			// Sneak: erupt a field of jagged ice spikes in front of you instead of a flat wall.
			if (p.isShiftKeyDown()) {
				boolean spiked = iceSpikes(p, level);
				level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 50, 3, 1, 3, 0.06);
				AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 1.2f, 0.7f);
				if (spiked || !AbilityHelpers.canGrief()) {
					ctx.triggerCooldown();
				}
				return;
			}
			float pitch = p.getXRot();
			boolean built;
			if (pitch < -75.0f) {
				built = ConjuredStructures.dome(p, level, ice);
			} else if (pitch > 75.0f) {
				built = ConjuredStructures.bridge(p, level, ice);
			} else {
				built = ConjuredStructures.wall(p, level, ice);
			}
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 2, p.getZ(), 40, 2, 2, 2, 0.05);
			AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 1.0f, 0.6f);
			if (built || !AbilityHelpers.canGrief()) {
				ctx.triggerCooldown();
			}
		}));

		AbilityHandlers.register(KEY, "frozen_armor", Handlers.toggle(
				ctx -> {
					seedCold(ctx, "frozen_armor");
					if (ctx.resource("frozen_armor") <= COLD_MIN) {
						ctx.setToggled(false);
						ctx.actionBar("message.herocraft.cryo.exhausted");
						return;
					}
					armorOn(ctx);
				},
				CryokinesisHandlers::armorOff,
				ctx -> {
					ServerPlayer p = ctx.player();
					armorOn(ctx);
					AbilityHelpers.modeAura(p, ParticleTypes.SNOWFLAKE, 4);
					frostWalk(p);
					// chill everything within 5 blocks
					if (p.tickCount % 10 == 0) {
						for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 5.0)) {
							AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
							freeze(e, 20);
						}
					}
					// leave a dusting of snow on the blocks walked over
					snowWalk(p);
					ctx.addResource("frozen_armor", -COLD_PER_ARMOR_TICK, MAX_COLD);
					if (ctx.resource("frozen_armor") <= 0.0f) {
						ctx.setToggled(false);
						armorOff(ctx);
						ctx.actionBar("message.herocraft.cryo.exhausted");
					}
				}));

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && frozenArmorActive(sp)
					&& source.getEntity() instanceof LivingEntity attacker && sp.distanceToSqr(attacker) < 9.0) {
				freeze(attacker, 80);
			}
		});

		com.herocraft.mod.hero.PowerPassives.registerTick(KEY, player -> {
			// powder-snow immunity / reduced freeze on the cryokinetic themselves
			if (player.getTicksFrozen() > 0) {
				player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - 3));
			}
			var power = Powers.byKey(KEY);
			boolean beaming = com.herocraft.mod.hero.ExperimentalPowers.getResource(player, power, "beaming") > 0.5f;
			// freeze_beam is a build-up gauge: it climbs in the channel tick and thaws back down here.
			com.herocraft.mod.hero.power.ModeMeter.cool(player, power, "freeze_beam", MAX_COLD, COLD_REGEN, beaming);
			com.herocraft.mod.hero.power.ModeMeter.regen(player, power, "frozen_armor", MAX_COLD, COLD_REGEN,
					frozenArmorActive(player));
		});
	}

	/** Fill a cold reserve the first time it is referenced. */
	private static void seedCold(AbilityContext ctx, String name) {
		if (!com.herocraft.mod.hero.ExperimentalPowers.state(ctx.player()).resources.containsKey(KEY + "/" + name)) {
			ctx.setResource(name, MAX_COLD, MAX_COLD);
		}
	}

	/** Frozen Armor: lay a single snow layer on solid ground the player walks across. */
	private static void snowWalk(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level) || !p.onGround() || !AbilityHelpers.canGrief()) {
			return;
		}
		int r = 2;
		BlockPos feet = p.blockPosition();
		for (BlockPos bp : BlockPos.betweenClosed(feet.offset(-r, 0, -r), feet.offset(r, 0, r))) {
			if (bp.distToCenterSqr(p.getX(), feet.getY(), p.getZ()) > (r + 0.5) * (r + 0.5)) {
				continue;
			}
			BlockPos imm = bp.immutable();
			BlockState below = level.getBlockState(imm.below());
			// Don't dust snow onto ice the cryokinetic just conjured underfoot (Ice Slide's packed-ice
			// platform, Frost Walk's frosted ice, Absolute Zero's ice sheet). Frozen Armor + Ice Slide
			// used to fight over the same blocks -- snow placed on the slide ice, then both TTLs expired
			// out of sync, which read as the ice constantly flickering in and out.
			if (level.getBlockState(imm).isAir() && !below.is(BlockTags.ICE)
					&& below.isFaceSturdy(level, imm.below(), Direction.UP)) {
				TempBlocks.place(level, imm, Blocks.SNOW.defaultBlockState(), 400);
			}
		}
	}

	/** Sneak + Ice Wall: a burst of jagged packed-ice spikes in front of the caster. */
	private static boolean iceSpikes(ServerPlayer p, ServerLevel level) {
		Vec3 look = p.getLookAngle();
		Vec3 fwd = new Vec3(look.x, 0, look.z);
		fwd = fwd.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : fwd.normalize();
		Vec3 center = p.position().add(fwd.scale(3.0));
		boolean placed = false;
		if (AbilityHelpers.canGrief()) {
			for (int i = 0; i < 7; i++) {
				double ang = level.random.nextDouble() * Math.PI * 2;
				double dist = level.random.nextDouble() * 4.0;
				int bx = Mth.floor(center.x + Math.cos(ang) * dist);
				int bz = Mth.floor(center.z + Math.sin(ang) * dist);
				int gy = p.blockPosition().getY();
				while (gy > level.getMinBuildHeight() + 1 && level.getBlockState(new BlockPos(bx, gy - 1, bz)).isAir()) {
					gy--;
				}
				int h = 2 + level.random.nextInt(4);
				for (int y = 0; y < h; y++) {
					BlockPos bp = new BlockPos(bx, gy + y, bz);
					BlockState cur = level.getBlockState(bp);
					if (cur.isAir() || cur.canBeReplaced()) {
						TempBlocks.place(level, bp,
								(y == h - 1 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState(), 200);
						placed = true;
					}
				}
			}
		}
		float dmg = 8.0f + coldBonus(p);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, center, 5.0)) {
			AbilityHelpers.hurt(p, e, AbilityHelpers.freeze(p), dmg);
			freeze(e, 120);
			AbilityHelpers.push(e, new Vec3(0, 0.85, 0));
		}
		return placed;
	}

	/** Absolute Zero: blanket the affected area in layered snow, deepest at the centre. */
	private static void layerSnow(ServerPlayer p, ServerLevel level, int r) {
		BlockPos origin = p.blockPosition();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > r) {
					continue;
				}
				for (int dy = 3; dy >= -3; dy--) {
					BlockPos bp = origin.offset(dx, dy, dz);
					if (!level.getBlockState(bp).isAir()) {
						continue;
					}
					BlockState below = level.getBlockState(bp.below());
					if (!below.isFaceSturdy(level, bp.below(), Direction.UP) || below.is(Blocks.SNOW)) {
						continue;
					}
					int layers = (int) Math.round(8 * (1.0 - dist / (r + 1.0)));
					layers = Mth.clamp(layers + level.random.nextInt(3) - 1, 1, 8);
					TempBlocks.place(level, bp,
							Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 600);
					break;
				}
			}
		}
	}

	// ---------------- ice slide ----------------

	private static void slideTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		if (p.getAbilities().flying || p.isInWater()) {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SLIDE_SPD);
			return;
		}

		// 150% faster while riding the slide -- and nothing that slows or pins the player.
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SLIDE_SPD, 1.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

		// Sneak = descend one block: once on the press, then one more per second while held.
		boolean sneak = p.isShiftKeyDown();
		boolean sneakPrev = ctx.resource("slide_sneak_prev") > 0.5f;
		float held = ctx.resource("slide_hold_ticks");
		boolean descendNow = false;
		if (sneak) {
			if (!sneakPrev) {
				descendNow = true;
				held = 0.0f;
			} else {
				held += 1.0f;
				if (held >= 20.0f) {
					descendNow = true;
					held = 0.0f;
				}
			}
		} else {
			held = 0.0f;
		}
		ctx.setResource("slide_hold_ticks", held, 100000.0f);
		ctx.setResource("slide_sneak_prev", sneak ? 1 : 0, 1);

		Vec3 v = p.getDeltaMovement();
		if (descendNow && v.y > -0.45) {
			// a firm one-block drop; the platform below is placed a block lower so it catches you
			p.setDeltaMovement(v.x, -0.45, v.z);
			p.hurtMarked = true;
		}

		// Conjure a 3x3 ice platform: one block under the feet normally, two below while descending
		// (so there is a gap to drop into). Never at foot level, so the player is never embedded.
		int dy = (descendNow || sneak) ? -2 : -1;
		BlockPos base = p.blockPosition().offset(0, dy, 0);
		if (AbilityHelpers.canGrief()) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					BlockPos bp = base.offset(x, 0, z);
					BlockState cur = level.getBlockState(bp);
					if (cur.isAir() || cur.canBeReplaced() || cur.getFluidState().is(Fluids.WATER)) {
						TempBlocks.place(level, bp, SLIDE_ICE, 30);
					}
				}
			}
		}

		p.resetFallDistance();
		if (p.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 0.1, p.getZ(), 4, 0.25, 0.05, 0.25, 0.0);
		}
	}

	private static void slideOff(AbilityContext ctx) {
		PowerToggles.clearModifier(ctx.player(), Attributes.MOVEMENT_SPEED, SLIDE_SPD);
		ctx.setResource("slide_hold_ticks", 0, 1);
		ctx.setResource("slide_sneak_prev", 0, 1);
	}

	// ---------------- frozen armor ----------------

	/** Frost Walker III: freeze the water the player walks over into (melting) frosted ice. */
	private static void frostWalk(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level) || !p.onGround()) {
			return;
		}
		int r = 3;
		BlockPos feet = p.blockPosition();
		for (BlockPos bp : BlockPos.betweenClosed(feet.offset(-r, -1, -r), feet.offset(r, -1, r))) {
			if (bp.distToCenterSqr(p.getX(), feet.getY(), p.getZ()) > (r + 0.5) * (r + 0.5)) {
				continue;
			}
			BlockState st = level.getBlockState(bp);
			if (st.getBlock() == Blocks.WATER && st.getFluidState().isSource()
					&& level.getBlockState(bp.above()).isAir()) {
				level.setBlockAndUpdate(bp, Blocks.FROSTED_ICE.defaultBlockState());
				level.scheduleTick(bp, Blocks.FROSTED_ICE, net.minecraft.util.Mth.nextInt(p.getRandom(), 60, 120));
			}
		}
	}

	private static void armorOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.effect(p, MobEffects.DAMAGE_RESISTANCE, 0, true);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB, 0.5, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void armorOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.clearEffect(p, MobEffects.DAMAGE_RESISTANCE);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB);
	}

	private static boolean frozenArmorActive(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(com.herocraft.mod.hero.AbilitySlot.SLOT_6));
	}
}
