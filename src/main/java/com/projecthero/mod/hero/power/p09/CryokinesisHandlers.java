package com.projecthero.mod.hero.power.p09;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.ConjuredStructures;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.TempBlocks;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
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
	private static final int AZ_CHARGE = 100;
	private static final int AZ_CD = 45 * 20;
	private static final double AZ_RANGE = 20.0;
	private static final net.minecraft.resources.ResourceLocation ARMOR_KB = com.projecthero.mod.ProjectHeroMod.id("frozen_armor_kb");
	private static final net.minecraft.resources.ResourceLocation ARMOR_ATK = com.projecthero.mod.ProjectHeroMod.id("frozen_armor_atk");
	private static final net.minecraft.resources.ResourceLocation SLIDE_SPD = com.projecthero.mod.ProjectHeroMod.id("ice_slide_speed");

	private CryokinesisHandlers() {
	}

	private static void freeze(LivingEntity e, int ticks) {
		e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 200, e.getTicksFrozen() + ticks));
	}

	/** Snap a target straight to the maximum freeze value (Frozen Armor / shift+V make every hit do this). */
	private static void maxFreeze(LivingEntity e) {
		e.setTicksFrozen(e.getTicksRequiredToFreeze() + 200);
	}

	/** Freeze a target — the ordinary amount, or straight to maximum while Frozen Armor is worn. */
	private static void chill(ServerPlayer p, LivingEntity e, int ticks) {
		if (frozenArmorActive(p)) {
			maxFreeze(e);
		} else {
			freeze(e, ticks);
		}
	}

	/** Frozen Armor adds a flat bonus to every Cryokinesis attack (see {@link com.projecthero.mod.hero.power.StanceMode}). */
	private static float armorBonus(ServerPlayer p) {
		return frozenArmorActive(p) ? com.projecthero.mod.hero.power.StanceMode.ABILITY_BONUS : 0.0f;
	}

	/** Client-safe: is Cryokinesis this player's active power (used by the powder-snow-walking mixin). */
	public static boolean active(net.minecraft.world.entity.player.Player p) {
		var st = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && KEY.equals(st.activePower) && st.ownedPowers.contains(KEY);
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
		AbilityHandlers.register(KEY, "ice_bolt", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					// sneak + hold R for 2 s: shape a tool out of ice
					ctx.setResource("icetool_ticks", 40, 40);
					AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 0.7f, 1.4f);
					return;
				}
				if (!ctx.cooldownReady()) {
					return;
				}
				LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 24.0),
						ParticleTypes.SNOWFLAKE, 3.0);
				if (t != null) {
					AbilityHelpers.hurt(p, t, 7.0f + coldBonus(p) + armorBonus(p));
					chill(p, t, 200); // ~7 s of the freeze effect
					AbilityHelpers.slow7s(t); // Slowness III, 7 s
				}
				AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 0.8f, 1.6f);
				ctx.triggerCooldown();
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ctx.setResource("icetool_ticks", 0, 40);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				float left = ctx.resource("icetool_ticks");
				if (left <= 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (!p.isShiftKeyDown()) {
					ctx.setResource("icetool_ticks", 0, 40);
					return;
				}
				left -= 1.0f;
				ctx.setResource("icetool_ticks", left, 40);
				if (p.tickCount % 4 == 0) {
					ctx.level().sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.3, 0.3, 0.01);
				}
				if (left <= 0.5f) {
					giveIceTool(p);
					AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 1.0f, 0.8f);
					ctx.level().sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.4, 0.4, 0.03);
				}
			}
		});

		AbilityHandlers.register(KEY, "freeze_beam", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				// Cold-buildup gauge: too close to frostbite to start channelling again yet.
				if (ctx.resource("freeze_beam") >= MAX_COLD - COLD_MIN) {
					ctx.actionBar("message.projecthero.cryo.exhausted");
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
				// The beam smothers fire it is aimed at, turns lava it washes over to stone, and lays
				// powder snow on the ground where it lands (v0.10.9).
				BlockHitResult bhr = AbilityHelpers.raycastBlock(p, 16.0);
				if (bhr.getType() == HitResult.Type.BLOCK) {
					BlockPos hitPos = bhr.getBlockPos();
					for (BlockPos bp : BlockPos.betweenClosed(hitPos.offset(-1, -1, -1), hitPos.offset(1, 1, 1))) {
						BlockState st = ctx.level().getBlockState(bp);
						if (st.is(BlockTags.FIRE)) {
							ctx.level().removeBlock(bp, false);
							ctx.level().sendParticles(ParticleTypes.SMOKE,
									bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.01);
						} else if (AbilityHelpers.canGrief() && st.getFluidState().is(Fluids.LAVA)) {
							ctx.level().setBlockAndUpdate(bp, st.getFluidState().isSource()
									? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
							ctx.level().sendParticles(ParticleTypes.LARGE_SMOKE,
									bp.getX() + 0.5, bp.getY() + 0.9, bp.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
						}
					}
					BlockPos face = hitPos.relative(bhr.getDirection());
					if (AbilityHelpers.canGrief() && bhr.getDirection() == Direction.UP
							&& ctx.level().getBlockState(face).isAir() && p.tickCount % 4 == 0) {
						TempBlocks.place(ctx.level(), face, Blocks.POWDER_SNOW.defaultBlockState(), 300);
					}
				}
				if (t != null) {
					t.clearFire();
					chill(p, t, 40); // much stronger freezing than Ice Bolt
					AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 4); // Slowness V, better than Ice Bolt
					if (p.tickCount % 10 == 0) {
						AbilityHelpers.hurt(p, t, AbilityHelpers.freeze(p), 5.0f + coldBonus(p) + armorBonus(p));
					}
				}
				ctx.addResource("freeze_beam", COLD_PER_BEAM_TICK, MAX_COLD);
				if (ctx.resource("freeze_beam") >= MAX_COLD) {
					ctx.setResource("beaming", 0, 1);
					ctx.actionBar("message.projecthero.cryo.exhausted");
				}
			}
		});

		AbilityHandlers.register(KEY, "ice_slide", Handlers.toggle(Handlers.noop(),
				CryokinesisHandlers::slideOff, CryokinesisHandlers::slideTick));

		// Absolute Zero: hold Z for 5 s to charge (snow swirls up around the caster), then a 20-block
		// flash-freeze that blankets the whole radius in snow and seals every target in ice (v0.10.9).
		AbilityHandlers.register(KEY, "absolute_zero", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				azPress(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				azRelease(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				azTick(ctx);
			}
		});

		// Look up → dome (owner left-clicks to dismiss). Look down → bridge. Otherwise → 4×4 wall.
		AbilityHandlers.register(KEY, "ice_wall", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
			if (p.isShiftKeyDown()) {
				if (p.getXRot() > 60.0f) {
					// sneak + look down: erupt a field of jagged ice spikes (the old sneak behaviour).
					boolean spiked = iceSpikes(p, level);
					level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 50, 3, 1, 3, 0.06);
					AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 1.2f, 0.7f);
					if (spiked || !AbilityHelpers.canGrief()) {
						ctx.triggerCooldown();
					}
					return;
				}
				// sneak: flash-freeze every target within 10 blocks — locked in place for 8 s, fires out.
				massFreeze(ctx);
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
					if (com.projecthero.mod.hero.power.StanceMode.blockedByCooldown(ctx)) {
						return;
					}
					seedCold(ctx, "frozen_armor");
					if (ctx.resource("frozen_armor") <= COLD_MIN) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.cryo.exhausted");
						return;
					}
					armorOn(ctx);
				},
				ctx -> {
					armorOff(ctx);
					com.projecthero.mod.hero.power.StanceMode.startDeactivateCooldown(ctx);
				},
				ctx -> {
					ServerPlayer p = ctx.player();
					armorOn(ctx);
					AbilityHelpers.modeAura(p, ParticleTypes.SNOWFLAKE, 4);
					frostWalk(p);
					// every hit maxes freeze while worn; and everything within 6 blocks is flash-frozen too
					if (p.tickCount % 10 == 0) {
						for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 6.0)) {
							AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 2);
							maxFreeze(e);
						}
					}
					// leave a dusting of snow on the blocks walked over
					snowWalk(p);
					ctx.addResource("frozen_armor", -COLD_PER_ARMOR_TICK, MAX_COLD);
					if (ctx.resource("frozen_armor") <= 0.0f) {
						ctx.setToggled(false);
						armorOff(ctx);
						com.projecthero.mod.hero.power.StanceMode.startDeactivateCooldown(ctx);
						ctx.actionBar("message.projecthero.cryo.exhausted");
					}
				}));

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && frozenArmorActive(sp)
					&& source.getEntity() instanceof LivingEntity attacker && sp.distanceToSqr(attacker) < 9.0) {
				freeze(attacker, 80);
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			// powder-snow immunity / reduced freeze on the cryokinetic themselves
			if (player.getTicksFrozen() > 0) {
				player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - 3));
			}
			var power = Powers.byKey(KEY);
			boolean beaming = com.projecthero.mod.hero.ExperimentalPowers.getResource(player, power, "beaming") > 0.5f;
			// freeze_beam is a build-up gauge: it climbs in the channel tick and thaws back down here.
			com.projecthero.mod.hero.power.ModeMeter.cool(player, power, "freeze_beam", MAX_COLD, COLD_REGEN, beaming);
			com.projecthero.mod.hero.power.ModeMeter.regen(player, power, "frozen_armor", MAX_COLD, COLD_REGEN,
					frozenArmorActive(player));
		});
	}

	/**
	 * Shape a tool out of ice matching whatever tool the caster is holding (pickaxe if none): iron
	 * mining level, but only gold-tier durability (32 uses). v0.10.9 — rendered as the plain iron tool
	 * with a shortened lifespan; a bespoke icy model is a later polish item.
	 */
	private static void giveIceTool(ServerPlayer p) {
		Item h = p.getMainHandItem().getItem();
		Item base;
		String label;
		if (h instanceof AxeItem) {
			base = Items.IRON_AXE;
			label = "Axe";
		} else if (h instanceof SwordItem) {
			base = Items.IRON_SWORD;
			label = "Sword";
		} else if (h instanceof ShovelItem) {
			base = Items.IRON_SHOVEL;
			label = "Shovel";
		} else if (h instanceof HoeItem) {
			base = Items.IRON_HOE;
			label = "Hoe";
		} else {
			base = Items.IRON_PICKAXE;
			label = "Pickaxe";
		}
		ItemStack tool = new ItemStack(base);
		tool.set(DataComponents.MAX_DAMAGE, 32);
		tool.set(DataComponents.DAMAGE, 0);
		tool.set(DataComponents.CUSTOM_NAME, Component.literal("Ice " + label)
				.withStyle(s -> s.withColor(ChatFormatting.AQUA).withItalic(false)));
		if (!p.getInventory().add(tool)) {
			p.drop(tool, false);
		}
	}

	/** Fill a cold reserve the first time it is referenced. */
	private static void seedCold(AbilityContext ctx, String name) {
		if (!com.projecthero.mod.hero.ExperimentalPowers.state(ctx.player()).resources.containsKey(KEY + "/" + name)) {
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

	/** Sneak + Ice Wall (V): flash-freeze every enemy within 10 blocks, pinning them for 8 s (v0.10.9). */
	private static void massFreeze(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 10.0)) {
			maxFreeze(e);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 160, 9);
			AbilityHelpers.applyControl(e, MobEffects.JUMP, 160, -10);
			e.setDeltaMovement(0, 0, 0);
			e.hurtMarked = true;
			e.clearFire();
			AbilityHelpers.hurt(p, e, AbilityHelpers.freeze(p), 6.0f + coldBonus(p) + armorBonus(p));
		}
		BlockPos c = p.blockPosition();
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-10, -4, -10), c.offset(10, 4, 10))) {
			if (level.getBlockState(bp).is(BlockTags.FIRE)) {
				level.removeBlock(bp, false);
			}
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 120, 6, 1.2, 6, 0.05);
		level.playSound(null, c, SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.3f, 0.5f);
		ctx.triggerCooldown(20 * 20);
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
		float dmg = 8.0f + coldBonus(p) + armorBonus(p);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, center, 5.0)) {
			AbilityHelpers.hurt(p, e, AbilityHelpers.freeze(p), dmg);
			chill(p, e, 120);
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

	// ---------------- Absolute Zero (Z, hold 5 s) ----------------

	private static void azPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("az_start") > 0.5f) {
			return;
		}
		if (!ctx.cooldownReady()) {
			ctx.actionBar("message.projecthero.ability.on_cooldown",
					Component.translatable(ctx.ability().nameKey()),
					String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
			return;
		}
		ctx.setResource("az_start", p.level().getGameTime(), 1e12f);
		ctx.setResource("ult_charge", 0, 100);
		AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 0.8f, 0.4f);
	}

	private static void azRelease(AbilityContext ctx) {
		if (ctx.resource("az_start") <= 0.5f) {
			return;
		}
		long held = ctx.player().level().getGameTime() - (long) ctx.resource("az_start");
		if (held >= AZ_CHARGE) {
			azFire(ctx);
		} else {
			azCancel(ctx);
		}
	}

	private static void azTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float start = ctx.resource("az_start");
		if (start <= 0.5f) {
			return;
		}
		long held = p.level().getGameTime() - (long) start;
		if (held < 0 || held > AZ_CHARGE + 100) {
			azCancel(ctx);
			return;
		}
		ctx.setResource("ult_charge", Math.min(100f, held * 100f / AZ_CHARGE), 100);
		ServerLevel level = ctx.level();
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 1.0, 0.3));
		p.hurtMarked = true;
		double frac = Math.min(1.0, held / (double) AZ_CHARGE);
		int n = 4 + (int) (frac * 16);
		for (int i = 0; i < n; i++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double rad = 0.6 + level.random.nextDouble() * (1.0 + frac * 3.0);
			double hy = level.random.nextDouble() * (p.getBbHeight() + 1.0);
			level.sendParticles(ParticleTypes.SNOWFLAKE,
					p.getX() + Math.cos(a) * rad, p.getY() + hy, p.getZ() + Math.sin(a) * rad, 1, 0, 0, 0, 0);
		}
		if (held % 15 == 0) {
			AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 0.7f, 0.4f + (float) frac * 0.8f);
		}
		if (held >= AZ_CHARGE) {
			azFire(ctx);
		}
	}

	private static void azCancel(AbilityContext ctx) {
		ctx.setResource("az_start", 0, 1e12f);
		ctx.setResource("ult_charge", 0, 100);
		AbilityHelpers.sound(ctx.player(), SoundEvents.FIRE_EXTINGUISH, 0.4f, 1.4f);
	}

	private static void azFire(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		ctx.setResource("az_start", 0, 1e12f);
		ctx.setResource("ult_charge", 0, 100);

		float dmg = 35.0f + coldBonus(p) + armorBonus(p);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), AZ_RANGE)) {
			maxFreeze(e);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 25 * 20, 9);
			AbilityHelpers.applyControl(e, MobEffects.JUMP, 25 * 20, -10);
			AbilityHelpers.hurt(p, e, AbilityHelpers.freeze(p), dmg);
			e.clearFire();
			e.setDeltaMovement(0, 0, 0);
			e.hurtMarked = true;
			if (AbilityHelpers.canGrief() && !(e instanceof net.minecraft.world.entity.player.Player)) {
				encaseInIce(level, e);
			}
		}
		if (AbilityHelpers.canGrief()) {
			int r = (int) AZ_RANGE;
			for (BlockPos bp : BlockPos.betweenClosed(p.blockPosition().offset(-r, -1, -r),
					p.blockPosition().offset(r, 0, r))) {
				if (bp.distToCenterSqr(p.getX(), p.getY(), p.getZ()) <= AZ_RANGE * AZ_RANGE
						&& level.getBlockState(bp).getFluidState().is(Fluids.WATER)) {
					TempBlocks.place(level, bp.immutable(), Blocks.ICE.defaultBlockState(), 300);
				}
			}
			layerSnow(p, level, r);
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 240,
				AZ_RANGE / 2, 1.0, AZ_RANGE / 2, 0.06);
		level.playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.6f, 0.4f);
		level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, net.minecraft.sounds.SoundSource.PLAYERS, 1.4f, 0.5f);
		ctx.triggerCooldown(AZ_CD);
	}

	/** Seal a target inside a shell of ice (a 3-tall box around its feet). */
	private static void encaseInIce(ServerLevel level, LivingEntity e) {
		BlockPos base = e.blockPosition();
		for (int dy = 0; dy <= 2; dy++) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				TempBlocks.place(level, base.above(dy).relative(d), Blocks.PACKED_ICE.defaultBlockState(), 300);
			}
		}
		TempBlocks.place(level, base.above(3), Blocks.PACKED_ICE.defaultBlockState(), 300);
		TempBlocks.place(level, base.below(), Blocks.PACKED_ICE.defaultBlockState(), 300);
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
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK, com.projecthero.mod.hero.power.StanceMode.MELEE_BONUS,
				AttributeModifier.Operation.ADD_VALUE);
	}

	private static void armorOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.clearEffect(p, MobEffects.DAMAGE_RESISTANCE);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK);
	}

	private static boolean frozenArmorActive(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6));
	}
}
