package com.projecthero.mod.hero.power.p09;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.ModeMeter;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.StanceMode;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.revamp.BatchBItems;
import com.projecthero.mod.hero.revamp.BatchBUtil;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 09 -- Cryokinesis (v0.13.22 revamp). Signature: <b>frost stacks, then frozen solid</b> -- see
 * {@link FrostStacks}.
 *
 * <h2>Keys</h2>
 * R Ice Bolt (Sneak: the ice-tool wheel), G Freeze Beam (hold), X Ice Ramp (the Iceman slide -- look up to build a
 * rising ramp), Z Absolute Zero (hold to charge; freezes everything solid), V Glacier Wall (a curved, crested ice wall;
 * Sneak: an ice-spike field), C Frozen Armor (frost shell, frost aura), H Flash Freeze (freeze water and lava around
 * you, douse fire, frost every creature near), N Ice Blade (a 30 s sword of ice that can't be kept).
 */
public final class CryokinesisHandlers {
	public static final String KEY = "power_09_cryokinesis";
	/** Frostbite gauge (the beam chills its user) and the Frozen Armor reserve: +15% over v0.12's 500. */
	public static final float MAX_COLD = 575.0f;
	private static final float COLD_PER_BEAM_TICK = 6.0f;
	private static final float COLD_PER_ARMOR_TICK = MAX_COLD / (35 * 20);
	private static final float COLD_REGEN = MAX_COLD / (25 * 20);
	private static final float COLD_MIN = 20.0f;
	private static final BlockState SLIDE_ICE = Blocks.PACKED_ICE.defaultBlockState();
	private static final BlockParticleOption ICE_DUST = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());
	private static final DustParticleOptions FROST_BEAM = new DustParticleOptions(new org.joml.Vector3f(0.62f, 0.9f, 1.0f), 1.0f);

	public static final float ICE_BOLT_DAMAGE = 8.5f;
	public static final float BEAM_DAMAGE = 6.0f;
	public static final int AZ_CHARGE = 85;
	private static final int AZ_CD = 38 * 20;
	private static final double AZ_RANGE = 20.0;
	public static final float AZ_DAMAGE = 42.0f;
	private static final int WALL_TTL = 15 * 20;
	private static final float SPIKES_DAMAGE = 10.0f;
	private static final double FLASH_RANGE = 8.0;
	public static final float FLASH_DAMAGE = 5.0f;
	private static final int FLASH_ICE_TTL = 20 * 20;
	private static final int BLADE_CD = 32 * 20;

	private static final net.minecraft.resources.ResourceLocation ARMOR_KB = com.projecthero.mod.ProjectHeroMod.id("frozen_armor_kb");
	private static final net.minecraft.resources.ResourceLocation ARMOR_ATK = com.projecthero.mod.ProjectHeroMod.id("frozen_armor_atk");
	private static final net.minecraft.resources.ResourceLocation SLIDE_SPD = com.projecthero.mod.ProjectHeroMod.id("ice_slide_speed");

	private CryokinesisHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	/** Frozen Armor adds a flat bonus to every Cryokinesis attack (see {@link StanceMode}). */
	private static float armorBonus(ServerPlayer p) {
		return frozenArmorActive(p) ? StanceMode.ABILITY_BONUS : 0.0f;
	}

	/** Client-safe: is Cryokinesis this player's active power (used by the powder-snow-walking mixin). */
	public static boolean active(Player p) {
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

	private static float bonus(ServerPlayer p) {
		return coldBonus(p) + armorBonus(p);
	}

	public static boolean frozenArmorActive(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}

	public static boolean sliding(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_3));
	}

	public static void register() {
		AbilityHandlers.register(KEY, "ice_bolt", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (!ctx.cooldownReady()) {
				return;
			}
			if (p.isShiftKeyDown()) {
				// Sneak + R opens the ice-weapon wheel (CryoWheelOpenPayload -> CryoWeaponPayload -> giveIceToolChoice)
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
						com.projecthero.mod.network.CryoWheelOpenPayload.INSTANCE);
				AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 0.7f, 1.5f);
				ctx.level().sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.4, 0.4, 0.04);
				return;
			}
			iceBolt(ctx);
		}));

		AbilityHandlers.register(KEY, "freeze_beam", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("freeze_beam") >= MAX_COLD - COLD_MIN) {
					ctx.actionBar("message.projecthero.cryo.exhausted");
					return;
				}
				ctx.setResource("beaming", 1, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("beaming") > 0.5f) {
					ctx.setResource("beaming", 0, 1);
				}
				MutationVisuals.stopIf(ctx.player(), "channel_right");
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("beaming") >= 0.5f) {
					beamTick(ctx);
				}
			}
		});

		AbilityHandlers.register(KEY, "ice_slide", Handlers.toggle(ctx -> {
			MutationVisuals.play(ctx.player(), "p09.slide");
			AbilityHelpers.sound(ctx.player(), SoundEvents.GLASS_PLACE, 1.0f, 0.8f);
		}, CryokinesisHandlers::slideOff, CryokinesisHandlers::slideTick));

		AbilityHandlers.register(KEY, "absolute_zero", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (BatchBUtil.chargeStart(ctx, "az_start")) {
					AbilityHelpers.sound(ctx.player(), SoundEvents.GLASS_PLACE, 0.8f, 0.4f);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				long held = BatchBUtil.chargeHeld(ctx, "az_start");
				if (held < 0) {
					return;
				}
				if (held >= AZ_CHARGE) {
					azFire(ctx);
				} else {
					azCancel(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				azTick(ctx);
			}
		});

		AbilityHandlers.register(KEY, "ice_wall", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			boolean placed;
			if (p.isShiftKeyDown()) {
				placed = iceSpikes(p, level);
				level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 50, 3, 1, 3, 0.06);
				AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 1.2f, 0.7f);
				MutationVisuals.play(p, "ground_pound");
			} else {
				placed = glacierWall(p, level);
				AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 1.2f, 0.5f);
				AbilityHelpers.sound(p, SoundEvents.POWDER_SNOW_PLACE, 1.0f, 0.6f);
				MutationVisuals.play(p, "summon_ground");
			}
			if (placed || !AbilityHelpers.canGrief()) {
				ctx.triggerCooldown();
			}
		}));

		AbilityHandlers.register(KEY, "frozen_armor", Handlers.toggle(
				ctx -> {
					if (StanceMode.blockedByCooldown(ctx)) {
						return;
					}
					ModeMeter.ensureSeeded(ctx, "frozen_armor", MAX_COLD);
					if (ctx.resource("frozen_armor") <= COLD_MIN) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.cryo.exhausted");
						return;
					}
					armorOn(ctx);
					MutationVisuals.play(ctx.player(), "flex");
					AbilityHelpers.sound(ctx.player(), SoundEvents.GLASS_PLACE, 1.2f, 0.5f);
					ctx.level().sendParticles(ICE_DUST, ctx.player().getX(), ctx.player().getY() + 1.0,
							ctx.player().getZ(), 40, 0.4, 0.9, 0.4, 0.08);
				},
				ctx -> {
					armorOff(ctx);
					StanceMode.startDeactivateCooldown(ctx);
				},
				ctx -> {
					ServerPlayer p = ctx.player();
					armorOn(ctx);
					AbilityHelpers.modeAura(p, ParticleTypes.SNOWFLAKE, 3);
					frostWalk(p);
					// the frost aura: a stack on everything within 4 blocks every 2 s
					if (p.tickCount % 40 == 0) {
						for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 4.0)) {
							FrostStacks.add(p, e, 1);
							AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
						}
					}
					snowWalk(p);
					ctx.addResource("frozen_armor", -COLD_PER_ARMOR_TICK, MAX_COLD);
					if (ctx.resource("frozen_armor") <= 0.0f) {
						ctx.setToggled(false);
						armorOff(ctx);
						StanceMode.startDeactivateCooldown(ctx);
						ctx.actionBar("message.projecthero.cryo.exhausted");
					}
				}));

		AbilityHandlers.register(KEY, "flash_freeze", Handlers.instant(CryokinesisHandlers::flashFreeze));
		AbilityHandlers.register(KEY, "ice_blade", Handlers.instant(CryokinesisHandlers::iceBlade));

		// Frozen Armor: whatever strikes you in melee range takes a frost stack.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && frozenArmorActive(sp)
					&& source.getEntity() instanceof LivingEntity attacker && sp.distanceToSqr(attacker) < 9.0) {
				FrostStacks.add(sp, attacker, 1);
			}
		});

		PowerPassives.registerTick(KEY, CryokinesisHandlers::passiveTick);
		PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				// only this power's own modifiers: the shared Resistance effect is cleared by the toggle's own off
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB);
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ARMOR_ATK);
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SLIDE_SPD);
				IceBladeItem.removeAll(player);
			}
		});
	}

	private static void passiveTick(ServerPlayer player) {
		Power power = power();
		if (power == null) {
			return;
		}
		// a cryokinetic shrugs off their own cold
		if (player.getTicksFrozen() > 0 && !FrostStacks.frozen(player)) {
			player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - 3));
		}
		boolean beaming = BatchBUtil.get(player, power, "beaming") > 0.5f;
		ModeMeter.cool(player, power, "freeze_beam", MAX_COLD, COLD_REGEN, beaming);
		ModeMeter.regen(player, power, "frozen_armor", MAX_COLD, COLD_REGEN, frozenArmorActive(player));
		// Ice Blade upkeep: the HUD countdown, and the blade can never be stashed anywhere
		float bladeLeft = BatchBUtil.get(player, power, "ice_blade");
		if (bladeLeft > 0.5f) {
			ItemStack hand = player.getMainHandItem();
			if (!IceBladeItem.isBlade(hand)) {
				IceBladeItem.removeAll(player);
				ExperimentalPowers.setResource(player, power, "ice_blade", 0, IceBladeItem.LIFE_TICKS);
			} else if (player.tickCount % 10 == 0) {
				long left = IceBladeItem.meltAt(hand) - player.level().getGameTime();
				ExperimentalPowers.setResource(player, power, "ice_blade", Math.max(0, left), IceBladeItem.LIFE_TICKS);
			}
			IceBladeItem.sweepMenu(player);
			if (player.tickCount % 3 == 0 && player.level() instanceof ServerLevel sl) {
				Vec3 h = AbilityHelpers.handPosition(player);
				sl.sendParticles(ParticleTypes.SNOWFLAKE, h.x, h.y, h.z, 1, 0.1, 0.2, 0.1, 0.0);
			}
		}
	}

	// ---------------- R: Ice Bolt ----------------

	private static void iceBolt(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
		Vec3 end = AbilityHelpers.aimPoint(p, 24.0);
		Vec3 hand = AbilityHelpers.handPosition(p);
		AbilityHelpers.line(level, hand, end, FROST_BEAM, 3.0);
		AbilityHelpers.line(level, hand, end, ParticleTypes.SNOWFLAKE, 1.5);
		if (t != null) {
			AbilityHelpers.hurtLands(p, t, ICE_BOLT_DAMAGE + bonus(p));
			FrostStacks.add(p, t, 1);
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
			level.sendParticles(ICE_DUST, end.x, end.y, end.z, 12, 0.2, 0.2, 0.2, 0.08);
		}
		MutationVisuals.play(p, "cast_right");
		AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 0.8f, 1.6f);
		ctx.triggerCooldown();
	}

	// ---------------- G: Freeze Beam ----------------

	private static void beamTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		MutationVisuals.ensure(p, "channel_right");
		LivingEntity t = AbilityHelpers.raycastEntity(p, 16.0);
		Vec3 hand = AbilityHelpers.handPosition(p);
		Vec3 end = AbilityHelpers.aimPoint(p, 16.0);
		AbilityHelpers.line(level, hand, end, FROST_BEAM, 3.0);
		if (p.tickCount % 2 == 0) {
			AbilityHelpers.line(level, hand, end, ParticleTypes.SNOWFLAKE, 1.0);
		}
		// the beam smothers fire, turns lava to stone and lays powder snow where it lands
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, 16.0);
		if (bhr.getType() == HitResult.Type.BLOCK) {
			BlockPos hitPos = bhr.getBlockPos();
			for (BlockPos bp : BlockPos.betweenClosed(hitPos.offset(-1, -1, -1), hitPos.offset(1, 1, 1))) {
				BlockState st = level.getBlockState(bp);
				if (st.is(BlockTags.FIRE)) {
					level.removeBlock(bp, false);
					level.sendParticles(ParticleTypes.SMOKE, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.01);
				} else if (AbilityHelpers.canGrief() && st.getFluidState().is(Fluids.LAVA)) {
					level.setBlockAndUpdate(bp, st.getFluidState().isSource()
							? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
					level.sendParticles(ParticleTypes.LARGE_SMOKE, bp.getX() + 0.5, bp.getY() + 0.9, bp.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
				}
			}
			BlockPos face = hitPos.relative(bhr.getDirection());
			if (AbilityHelpers.canGrief() && bhr.getDirection() == Direction.UP
					&& level.getBlockState(face).isAir() && p.tickCount % 4 == 0) {
				TempBlocks.place(level, face, Blocks.POWDER_SNOW.defaultBlockState(), 300);
			}
		}
		if (t != null) {
			t.clearFire();
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 3);
			if (p.tickCount % 10 == 0) {
				AbilityHelpers.hurt(p, t, AbilityHelpers.freeze(p), BEAM_DAMAGE + bonus(p));
				FrostStacks.add(p, t, 1);
			}
		}
		if (p.tickCount % 8 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 0.8f, 1.6f);
		}
		ctx.addResource("freeze_beam", COLD_PER_BEAM_TICK, MAX_COLD);
		if (ctx.resource("freeze_beam") >= MAX_COLD) {
			ctx.setResource("beaming", 0, 1);
			MutationVisuals.stopIf(p, "channel_right");
			ctx.actionBar("message.projecthero.cryo.exhausted");
		}
	}

	// ---------------- X: Ice Ramp ----------------

	/**
	 * X: the Iceman slide. A 3x3 skating platform of packed ice forms under you wherever you go (even over air and
	 * water) and you skate 150% faster. Look up (above 20 degrees) while moving and the ice builds into a rising
	 * ramp; tap Sneak to drop one level. No fall damage while sliding.
	 */
	private static void slideTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		if (p.getAbilities().flying || p.isInWater()) {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SLIDE_SPD);
			return;
		}
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SLIDE_SPD, 1.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		MutationVisuals.ensure(p, "p09.slide");

		boolean sneak = p.isShiftKeyDown();
		boolean sneakPrev = ctx.resource("slide_sneak_prev") > 0.5f;
		boolean descendNow = sneak && !sneakPrev;
		if (sneak != sneakPrev) {
			ctx.setResource("slide_sneak_prev", sneak ? 1 : 0, 1);
		}
		Vec3 v = p.getDeltaMovement();
		boolean moving = v.horizontalDistanceSqr() > 0.004;
		boolean ramp = !sneak && moving && p.getXRot() < -20.0f;
		if (descendNow && v.y > -0.45) {
			p.setDeltaMovement(v.x, -0.45, v.z);
			p.hurtMarked = true;
		} else if (ramp) {
			// climb: a steady lift, with the platform laid under the rising feet each tick
			AbilityHelpers.launchSelf(p, new Vec3(v.x, Math.max(v.y, 0.28), v.z));
		}
		int dy = sneak ? -2 : -1;
		BlockPos base = ramp ? BlockPos.containing(p.getX(), p.getY() - 1.0, p.getZ()) : p.blockPosition().offset(0, dy, 0);
		if (AbilityHelpers.canGrief()) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					BlockPos bp = base.offset(x, 0, z);
					BlockState cur = level.getBlockState(bp);
					if (cur.isAir() || cur.canBeReplaced() || cur.getFluidState().is(Fluids.WATER)) {
						TempBlocks.place(level, bp, SLIDE_ICE, ramp ? 80 : 30);
					}
				}
			}
		}
		p.resetFallDistance();
		if (p.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 0.1, p.getZ(), 4, 0.25, 0.05, 0.25, 0.0);
			if (moving) {
				level.sendParticles(ICE_DUST, p.getX(), p.getY() + 0.05, p.getZ(), 2, 0.2, 0.02, 0.2, 0.02);
			}
		}
	}

	private static void slideOff(AbilityContext ctx) {
		PowerToggles.clearModifier(ctx.player(), Attributes.MOVEMENT_SPEED, SLIDE_SPD);
		ctx.setResource("slide_sneak_prev", 0, 1);
		MutationVisuals.stopIf(ctx.player(), "p09.slide");
	}

	// ---------------- Z: Absolute Zero ----------------

	private static void azTick(AbilityContext ctx) {
		long held = BatchBUtil.chargeHeld(ctx, "az_start");
		if (held < 0) {
			return;
		}
		ServerPlayer p = ctx.player();
		if (held > AZ_CHARGE + 100) {
			azCancel(ctx);
			return;
		}
		BatchBUtil.chargeMeter(ctx, held, AZ_CHARGE);
		MutationVisuals.ensure(p, "float_arms");
		ServerLevel level = ctx.level();
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 1.0, 0.3));
		p.hurtMarked = true;
		double frac = Math.min(1.0, held / (double) AZ_CHARGE);
		int n = 4 + (int) (frac * 16);
		for (int i = 0; i < n; i++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double rad = 0.6 + level.random.nextDouble() * (1.0 + frac * 3.0);
			double hy = level.random.nextDouble() * (p.getBbHeight() + 1.0);
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX() + Math.cos(a) * rad, p.getY() + hy,
					p.getZ() + Math.sin(a) * rad, 1, 0, 0, 0, 0);
		}
		if (held % 15 == 0) {
			AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 0.7f, 0.4f + (float) frac * 0.8f);
		}
		if (held >= AZ_CHARGE) {
			azFire(ctx);
		}
	}

	private static void azCancel(AbilityContext ctx) {
		BatchBUtil.chargeClear(ctx, "az_start");
		MutationVisuals.stopIf(ctx.player(), "float_arms");
		AbilityHelpers.sound(ctx.player(), SoundEvents.FIRE_EXTINGUISH, 0.4f, 1.4f);
	}

	/** Z: 42 damage across 20 blocks and every target frozen solid; a snow blanket and ice sheets on water. */
	private static void azFire(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		BatchBUtil.chargeClear(ctx, "az_start");
		MutationVisuals.play(p, "cast_raise_both");
		float dmg = AZ_DAMAGE + bonus(p);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), AZ_RANGE)) {
			AbilityHelpers.hurtBurst(p, e, AbilityHelpers.freeze(p), dmg);
			e.clearFire();
			FrostStacks.freezeNow(p, e);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 10 * 20, 2);
		}
		if (AbilityHelpers.canGrief()) {
			int r = (int) AZ_RANGE;
			for (BlockPos bp : BlockPos.betweenClosed(p.blockPosition().offset(-r, -1, -r), p.blockPosition().offset(r, 0, r))) {
				if (bp.distToCenterSqr(p.getX(), p.getY(), p.getZ()) <= AZ_RANGE * AZ_RANGE
						&& level.getBlockState(bp).getFluidState().is(Fluids.WATER)) {
					TempBlocks.place(level, bp.immutable(), Blocks.ICE.defaultBlockState(), 300);
				}
			}
			layerSnow(p, level, r);
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 240, AZ_RANGE / 2, 1.0, AZ_RANGE / 2, 0.06);
		level.sendParticles(ICE_DUST, p.getX(), p.getY() + 1, p.getZ(), 80, AZ_RANGE / 3, 1.0, AZ_RANGE / 3, 0.1);
		level.playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.6f, 0.4f);
		level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.4f, 0.5f);
		ctx.triggerCooldown(AZ_CD);
	}

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
					TempBlocks.place(level, bp, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 600);
					break;
				}
			}
		}
	}

	// ---------------- V: Glacier Wall / Ice Spikes ----------------

	/**
	 * V: a curved glacier wall -- a 7-wide arc of packed ice three high, crested with blue ice, 3.5 blocks out and
	 * bowed around you, for 15 s. Anything standing where it rises is shoved out and takes a frost stack.
	 * (Geokinesis builds flat walls, domes and bridges of stone; this is a shield, not a building tool.)
	 */
	private static boolean glacierWall(ServerPlayer p, ServerLevel level) {
		Vec3 fwd = BatchBUtil.flatLook(p);
		Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
		boolean placed = false;
		java.util.Set<Long> cells = new java.util.HashSet<>();
		for (int i = -3; i <= 3; i++) {
			double bow = 3.5 - (i * i) * 0.12; // the ends curl back toward you
			Vec3 at = p.position().add(fwd.scale(bow)).add(side.scale(i));
			BlockPos col = BlockPos.containing(at.x, p.getY(), at.z);
			if (!cells.add(col.asLong())) {
				continue;
			}
			// settle onto the ground (up to 2 down / 1 up)
			for (int k = 0; k < 2 && level.getBlockState(col.below()).isAir(); k++) {
				col = col.below();
			}
			if (!level.getBlockState(col).isAir() && level.getBlockState(col.above()).isAir()) {
				col = col.above();
			}
			for (int h = 0; h < 3; h++) {
				BlockState st = (h == 2 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState();
				if (TempBlocks.place(level, col.above(h), st, WALL_TTL)) {
					placed = true;
				}
			}
			level.sendParticles(ICE_DUST, col.getX() + 0.5, col.getY() + 1.5, col.getZ() + 0.5, 6, 0.3, 0.8, 0.3, 0.05);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, Vec3.atBottomCenterOf(col).add(0, 1, 0), 1.3)) {
				FrostStacks.add(p, e, 1);
				AbilityHelpers.push(e, fwd.scale(0.9).add(0, 0.35, 0));
			}
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX() + fwd.x * 3.5, p.getY() + 1.5, p.getZ() + fwd.z * 3.5,
				40, 2.5, 1.0, 2.5, 0.04);
		return placed;
	}

	/** Sneak + V: a burst of jagged ice spikes ahead -- 10 damage, +2 frost stacks, a toss into the air. */
	private static boolean iceSpikes(ServerPlayer p, ServerLevel level) {
		Vec3 fwd = BatchBUtil.flatLook(p);
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
						TempBlocks.place(level, bp, (y == h - 1 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState(), 200);
						placed = true;
					}
				}
			}
		}
		float dmg = SPIKES_DAMAGE + bonus(p);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, center, 5.0)) {
			AbilityHelpers.hurt(p, e, AbilityHelpers.freeze(p), dmg);
			FrostStacks.add(p, e, 2);
			AbilityHelpers.push(e, new Vec3(0, 0.85, 0));
		}
		return placed;
	}

	// ---------------- H: Flash Freeze ----------------

	/**
	 * H: an instant cold snap 8 blocks round -- water surfaces freeze to ice and lava to obsidian (both for 20 s),
	 * every fire goes out (yours and your allies' burning too), and every creature caught takes 5 and two frost stacks.
	 */
	private static void flashFreeze(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		BlockPos c = p.blockPosition();
		int r = (int) FLASH_RANGE;
		int frozen = 0;
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, -3, -r), c.offset(r, 2, r))) {
			if (bp.distToCenterSqr(p.getX(), p.getY(), p.getZ()) > FLASH_RANGE * FLASH_RANGE) {
				continue;
			}
			BlockState st = level.getBlockState(bp);
			if (st.is(BlockTags.FIRE)) {
				level.removeBlock(bp, false);
				level.sendParticles(ParticleTypes.SMOKE, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
				continue;
			}
			if (!AbilityHelpers.canGrief() || !level.getBlockState(bp.above()).isAir()) {
				continue; // only exposed surfaces
			}
			if (st.getFluidState().is(Fluids.WATER) && st.getFluidState().isSource() && st.is(Blocks.WATER)) {
				if (TempBlocks.place(level, bp.immutable(), Blocks.ICE.defaultBlockState(), FLASH_ICE_TTL)) {
					frozen++;
				}
			} else if (st.getFluidState().is(Fluids.LAVA) && st.is(Blocks.LAVA)) {
				if (TempBlocks.place(level, bp.immutable(), (st.getFluidState().isSource() ? Blocks.OBSIDIAN
						: Blocks.COBBLESTONE).defaultBlockState(), FLASH_ICE_TTL)) {
					frozen++;
				}
			}
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(FLASH_RANGE))) {
			if (e.getRemainingFireTicks() > 0 && (e == p || e instanceof Player)) {
				e.clearFire(); // douses you and anyone standing with you
			}
		}
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), FLASH_RANGE)) {
			e.clearFire();
			AbilityHelpers.hurt(p, e, AbilityHelpers.freeze(p), FLASH_DAMAGE + bonus(p));
			FrostStacks.add(p, e, 2);
		}
		for (int i = 0; i < 48; i++) {
			double a = i / 48.0 * Math.PI * 2;
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX() + Math.cos(a) * FLASH_RANGE * 0.8, p.getY() + 0.3,
					p.getZ() + Math.sin(a) * FLASH_RANGE * 0.8, 2, 0.3, 0.1, 0.3, 0.02);
		}
		level.sendParticles(ICE_DUST, p.getX(), p.getY() + 0.5, p.getZ(), 60, 3.0, 0.3, 3.0, 0.1);
		level.playSound(null, c, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.3f, 0.5f);
		level.playSound(null, c, SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.4f, 0.6f);
		ctx.setResource("flash_frozen", frozen, 100000);
		MutationVisuals.play(p, "slam_two_hand");
		ctx.triggerCooldown();
	}

	// ---------------- N: Ice Blade ----------------

	/**
	 * N: conjure the Ice Blade into your main hand (whatever was there moves to a free slot; with no free slot it
	 * refuses). 10 attack damage, a frost stack per hit, melts after 30 s -- or the moment it leaves your hand. Press
	 * N again to shatter it early.
	 */
	private static void iceBlade(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		if (IceBladeItem.isBlade(p.getMainHandItem()) || ctx.resource("ice_blade") > 0.5f) {
			IceBladeItem.removeAll(p);
			ctx.setResource("ice_blade", 0, IceBladeItem.LIFE_TICKS);
			IceBladeItem.melt(level, p);
			MutationVisuals.play(p, "slash_right");
			return;
		}
		if (!ctx.cooldownReady()) {
			BatchBUtil.onCooldown(ctx);
			return;
		}
		Inventory inv = p.getInventory();
		ItemStack held = inv.getSelected();
		if (!held.isEmpty()) {
			int free = inv.getFreeSlot();
			if (free < 0) {
				ctx.actionBar("message.projecthero.cryo.hands_full");
				return;
			}
			inv.setItem(free, held);
		}
		IceBladeItem.removeAll(p);
		long meltAt = level.getGameTime() + IceBladeItem.LIFE_TICKS;
		inv.setItem(inv.selected, IceBladeItem.create(BatchBItems.ICE_BLADE, p, meltAt));
		inv.setChanged();
		ctx.setResource("ice_blade", IceBladeItem.LIFE_TICKS, IceBladeItem.LIFE_TICKS);
		Vec3 h = AbilityHelpers.handPosition(p);
		level.sendParticles(ICE_DUST, h.x, h.y, h.z, 20, 0.15, 0.3, 0.15, 0.05);
		level.sendParticles(ParticleTypes.SNOWFLAKE, h.x, h.y, h.z, 16, 0.2, 0.4, 0.2, 0.03);
		AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 1.0f, 1.3f);
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.8f);
		MutationVisuals.play(p, "p09.blade_draw");
		ctx.triggerCooldown(BLADE_CD);
	}

	// ---------------- ice tools (Sneak + R wheel) ----------------

	/** The choices on the ice-weapon wheel. Order is the wire index in {@code CryoWeaponPayload}. */
	public enum IceWeapon {
		PICKAXE(Items.IRON_PICKAXE, "Pickaxe"),
		SWORD(Items.IRON_SWORD, "Sword"),
		AXE(Items.IRON_AXE, "Axe"),
		SHOVEL(Items.IRON_SHOVEL, "Shovel"),
		HOE(Items.IRON_HOE, "Hoe");

		public final Item base;
		public final String label;

		IceWeapon(Item base, String label) {
			this.base = base;
			this.label = label;
		}
	}

	/** Shape the chosen ice tool: iron mining level, only 32 uses. Re-validated here (owns Cryokinesis). */
	public static void giveIceToolChoice(ServerPlayer p, int weaponIndex) {
		if (!ExperimentalPowers.owns(p, KEY)) {
			return;
		}
		IceWeapon[] all = IceWeapon.values();
		IceWeapon w = all[Math.floorMod(weaponIndex, all.length)];
		ItemStack tool = new ItemStack(w.base);
		tool.set(DataComponents.MAX_DAMAGE, 32);
		tool.set(DataComponents.DAMAGE, 0);
		tool.set(DataComponents.CUSTOM_NAME, Component.literal("Ice " + w.label)
				.withStyle(s -> s.withColor(ChatFormatting.AQUA).withItalic(false)));
		if (!p.getInventory().add(tool)) {
			p.drop(tool, false);
		}
		p.displayClientMessage(Component.translatable("message.projecthero.cryo.ice_tool"), true);
		AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 0.8f, 1.4f);
		AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 0.7f, 1.7f);
		if (p.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.4, 0.4, 0.03);
		}
		Power power = power();
		if (power != null) {
			ExperimentalPowers.triggerCooldown(p, power, power.ability(AbilitySlot.SLOT_1),
					com.projecthero.mod.hero.HeroConfig.get().scaledCooldown(40));
		}
	}

	// ---------------- frozen armor ----------------

	/** Frozen Armor: lay a single snow layer on solid ground the player walks across. */
	private static void snowWalk(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level) || !p.onGround() || !AbilityHelpers.canGrief() || p.tickCount % 4 != 0) {
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
			// never dust snow onto ice the cryokinetic conjured (it flickered against the slide's own TTL)
			if (level.getBlockState(imm).isAir() && !below.is(BlockTags.ICE)
					&& below.isFaceSturdy(level, imm.below(), Direction.UP)) {
				TempBlocks.place(level, imm, Blocks.SNOW.defaultBlockState(), 400);
			}
		}
	}

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
			if (st.getBlock() == Blocks.WATER && st.getFluidState().isSource() && level.getBlockState(bp.above()).isAir()) {
				level.setBlockAndUpdate(bp, Blocks.FROSTED_ICE.defaultBlockState());
				level.scheduleTick(bp, Blocks.FROSTED_ICE, Mth.nextInt(p.getRandom(), 60, 120));
			}
		}
	}

	private static void armorOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.effect(p, MobEffects.DAMAGE_RESISTANCE, 0, true);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB, 0.5, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK, StanceMode.MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void armorOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.clearEffect(p, MobEffects.DAMAGE_RESISTANCE);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK);
	}
}
