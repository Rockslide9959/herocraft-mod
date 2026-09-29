package com.projecthero.mod.hero.power.p25;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.attachment.ModAttachments;
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
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.StanceMode;
import com.projecthero.mod.hero.revamp.BatchBScheduler;
import com.projecthero.mod.hero.revamp.BatchBUtil;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 25 -- Water Manipulation (v0.13.22 revamp). Signature: <b>a carried water supply.</b> A Water bar (the SLAB
 * on the HUD) that every move spends and nothing refills for free: stand in water (fast), in rain (slowly), or
 * right-click a water bottle (+20%) -- Sneak + right-click a water bucket (+50%) -- to top it up. A trickle of
 * moisture from the air refills it very slowly anywhere but the Nether. Aquatic Form halves every cost.
 *
 * <h2>Keys</h2>
 * R Water Shot (Sneak + hold: spray), G Water Whip (a visible tendril that lashes and pulls), X Riptide, Z Tidal
 * Wave (hold to charge; a rolling wall of real water), V Water Prison (Sneak + hold: shape a temporary water source /
 * siphon one), C Aquatic Form (shimmering wet shell), H Healing Water, N Geyser.
 */
public final class WaterHandlers {
	public static final String KEY = "power_25_water_manipulation";

	/** The Water bar: +15% over v0.12's 500. */
	public static final float MAX_WATER = 575.0f;
	public static final String SUPPLY = "water_supply";
	private static final float REFILL_WATER = 10.0f;
	private static final float REFILL_RAIN = 2.0f;
	private static final float REFILL_AIR = 0.15f;
	public static final float BOTTLE_REFILL = MAX_WATER * 0.2f;
	public static final float BUCKET_REFILL = MAX_WATER * 0.5f;

	public static final float COST_SHOT = 25.0f;
	private static final float COST_SPRAY_TICK = 2.5f;
	public static final float COST_WHIP = 40.0f;
	private static final float COST_RIPTIDE = 30.0f;
	public static final float COST_TIDAL = 200.0f;
	private static final float COST_PRISON = 60.0f;
	public static final float COST_HEAL = 120.0f;
	public static final float COST_GEYSER = 50.0f;

	public static final float SHOT_DAMAGE = 10.0f;
	private static final float SPRAY_DAMAGE = 6.0f;
	public static final float WHIP_DAMAGE = 19.0f;
	private static final float RIPTIDE_DAMAGE = 7.0f;
	public static final int TIDAL_CHARGE = 85;
	public static final float TIDAL_DAMAGE = 42.0f;
	private static final int TIDAL_CD = 38 * 20;
	private static final int PRISON_TICKS = 8 * 20;
	private static final int CREATE_TICKS = 80;
	private static final int CREATED_WATER_TTL = 60 * 20;
	public static final float HEAL_AMOUNT = 6.0f;
	private static final double HEAL_RANGE = 6.0;
	public static final float GEYSER_DAMAGE = 12.0f;

	private static final ResourceLocation AQUATIC_ATK = com.projecthero.mod.ProjectHeroMod.id("aquatic_atk");
	private static final DustParticleOptions WATER_DUST = new DustParticleOptions(new org.joml.Vector3f(0.22f, 0.5f, 1.0f), 1.3f);
	private static final DustParticleOptions FOAM = new DustParticleOptions(new org.joml.Vector3f(0.85f, 0.95f, 1.0f), 1.0f);

	private WaterHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	public static boolean aquaticActive(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}

	/** +5 to every Water ability's damage in water / rain / snow. */
	private static float waterBonus(ServerPlayer p) {
		boolean snow = p.isInPowderSnow
				|| p.level().getBlockState(p.blockPosition()).is(Blocks.SNOW)
				|| p.level().getBlockState(p.blockPosition().below()).is(Blocks.SNOW);
		return (p.isInWaterOrRain() || snow) ? 5.0f : 0.0f;
	}

	private static float bonus(ServerPlayer p) {
		return waterBonus(p) + (aquaticActive(p) ? StanceMode.ABILITY_BONUS : 0.0f);
	}

	// ---------------- the supply ----------------

	public static float supply(ServerPlayer p) {
		Power power = power();
		return power == null ? 0.0f : ExperimentalPowers.getResource(p, power, SUPPLY);
	}

	private static float cost(ServerPlayer p, float base) {
		return aquaticActive(p) ? base * 0.5f : base;
	}

	/** Spends {@code base} (halved in Aquatic Form); false (with feedback) if the bar is too low. */
	private static boolean spend(ServerPlayer p, float base) {
		Power power = power();
		float c = cost(p, base);
		float have = supply(p);
		if (have < c) {
			p.displayClientMessage(Component.translatable("message.projecthero.water.dry"), true);
			return false;
		}
		ExperimentalPowers.setResource(p, power, SUPPLY, have - c, MAX_WATER);
		return true;
	}

	/** Adds to the supply; returns how much actually went in. */
	public static float refill(ServerPlayer p, float amount) {
		Power power = power();
		float have = supply(p);
		float next = Math.min(MAX_WATER, have + amount);
		if (next != have) {
			ExperimentalPowers.setResource(p, power, SUPPLY, next, MAX_WATER);
		}
		return next - have;
	}

	/** Either side: the player owns Water Manipulation and has room in the bar (reads the synced state). */
	private static boolean canDrink(Player player) {
		var st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains(KEY)) {
			return false;
		}
		Float v = st.resources.get(KEY + "/" + SUPPLY);
		return v == null || v < MAX_WATER - 1.0f;
	}

	private static boolean isWaterBottle(ItemStack stack) {
		return stack.is(Items.POTION)
				&& stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
	}

	// ---------------- registration ----------------

	public static void register() {
		// R: tap -- a high-pressure jet. Sneak + hold -- spray a stream (puts out fire).
		AbilityHandlers.register(KEY, "water_shot", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (supply(p) < cost(p, COST_SPRAY_TICK * 4)) {
						ctx.actionBar("message.projecthero.water.dry");
						return;
					}
					ctx.setResource("spraying", 1, 1);
					return;
				}
				if (BatchBUtil.onCooldown(ctx) || !spend(p, COST_SHOT)) {
					return;
				}
				waterShot(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("spraying") > 0.5f) {
					ctx.setResource("spraying", 0, 1);
				}
				MutationVisuals.stopIf(ctx.player(), "channel_right");
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("spraying") >= 0.5f) {
					sprayTick(ctx);
				}
			}
		});

		AbilityHandlers.register(KEY, "water_whip", Handlers.instant(ctx -> {
			if (spend(ctx.player(), COST_WHIP)) {
				waterWhip(ctx);
			}
		}));

		AbilityHandlers.register(KEY, "riptide", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			boolean wet = p.isInWaterOrRain();
			if (!wet && !spend(p, COST_RIPTIDE)) {
				return;
			}
			riptide(ctx, wet);
		}));

		AbilityHandlers.register(KEY, "tidal_wave", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (ctx.resource("tidal_start") <= 0.5f && supply(p) < cost(p, COST_TIDAL)) {
					ctx.actionBar("message.projecthero.water.dry");
					return;
				}
				if (BatchBUtil.chargeStart(ctx, "tidal_start")) {
					AbilityHelpers.sound(p, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.0f, 0.5f);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				long held = BatchBUtil.chargeHeld(ctx, "tidal_start");
				if (held < 0) {
					return;
				}
				if (held >= TIDAL_CHARGE) {
					tidalFire(ctx);
				} else {
					tidalCancel(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				tidalChargeTick(ctx);
			}
		});

		AbilityHandlers.register(KEY, "water_prison", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					ctx.setResource("create_ticks", CREATE_TICKS, CREATE_TICKS);
					AbilityHelpers.sound(p, SoundEvents.BUCKET_FILL, 0.7f, 0.9f);
					return;
				}
				if (ctx.resource("prison_until") > ctx.level().getGameTime()) {
					ctx.setResource("prison_until", 0, 1e12f); // press again: let them go early
					ctx.triggerCooldown();
					return;
				}
				if (BatchBUtil.onCooldown(ctx)) {
					return;
				}
				LivingEntity t = AbilityHelpers.raycastEntity(p, 16.0);
				if (t == null) {
					ctx.actionBar("message.projecthero.geo.no_target");
					return;
				}
				if (!spend(p, COST_PRISON)) {
					return;
				}
				startPrison(ctx, t);
				MutationVisuals.play(p, "grab_pull");
				AbilityHelpers.sound(p, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0f, 0.8f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("create_ticks") > 0.5f) {
					ctx.setResource("create_ticks", 0, CREATE_TICKS);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				createTick(ctx);
			}
		});

		AbilityHandlers.register(KEY, "aquatic_form", Handlers.toggle(
				ctx -> {
					if (StanceMode.blockedByCooldown(ctx)) {
						return;
					}
					MutationVisuals.play(ctx.player(), "power_up");
					AbilityHelpers.sound(ctx.player(), SoundEvents.CONDUIT_ACTIVATE, 0.8f, 1.2f);
					ctx.level().sendParticles(ParticleTypes.SPLASH, ctx.player().getX(), ctx.player().getY() + 1.0,
							ctx.player().getZ(), 40, 0.4, 0.8, 0.4, 0.1);
				},
				ctx -> {
					PowerToggles.clearModifier(ctx.player(), Attributes.ATTACK_DAMAGE, AQUATIC_ATK);
					StanceMode.startDeactivateCooldown(ctx);
				},
				ctx -> {
					ServerPlayer p = ctx.player();
					PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, AQUATIC_ATK, StanceMode.MELEE_BONUS,
							AttributeModifier.Operation.ADD_VALUE);
					p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 20, 2, false, false, false));
					p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 20, 0, false, false, false));
					if (p.isInWater()) {
						p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 2, false, false, false));
					}
					AbilityHelpers.modeAura(p, ParticleTypes.FALLING_WATER, 4);
					if (p.tickCount % 10 == 0) {
						AbilityHelpers.modeAura(p, ParticleTypes.DRIPPING_WATER, 3);
					}
				}));

		AbilityHandlers.register(KEY, "healing_water", Handlers.instant(ctx -> {
			if (spend(ctx.player(), COST_HEAL)) {
				healingWater(ctx);
			}
		}));

		AbilityHandlers.register(KEY, "geyser", Handlers.instant(ctx -> {
			if (spend(ctx.player(), COST_GEYSER)) {
				geyser(ctx);
			}
		}));

		// Right-click a water source while shaping water (Sneak + hold V) to siphon it into your supply.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			Power power = power();
			if (power == null || ExperimentalPowers.getResource(sp, power, "create_ticks") <= 0.5f) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (level.getBlockState(pos).getFluidState().is(Fluids.WATER) && level.getBlockState(pos).getFluidState().isSource()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				((ServerLevel) level).sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						12, 0.3, 0.3, 0.3, 0.0);
				level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.8f, 1.1f);
				refill(sp, BOTTLE_REFILL);
				ExperimentalPowers.setResource(sp, power, "create_ticks", 0, CREATE_TICKS);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// Drink water into the supply: a water bottle (right-click), or a water bucket (Sneak + right-click).
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			boolean bottle = isWaterBottle(stack);
			boolean bucket = stack.is(Items.WATER_BUCKET) && player.isShiftKeyDown();
			if ((!bottle && !bucket) || !canDrink(player)) {
				return InteractionResultHolder.pass(stack);
			}
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
				refill(sp, bottle ? BOTTLE_REFILL : BUCKET_REFILL);
				ItemStack empty = new ItemStack(bottle ? Items.GLASS_BOTTLE : Items.BUCKET);
				if (!sp.getAbilities().instabuild) {
					if (stack.getCount() <= 1) {
						sp.setItemInHand(hand, empty);
					} else {
						stack.shrink(1);
						if (!sp.getInventory().add(empty)) {
							sp.drop(empty, false);
						}
					}
				}
				sl.sendParticles(ParticleTypes.SPLASH, sp.getX(), sp.getY() + 1.2, sp.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
				sl.playSound(null, sp.blockPosition(), bottle ? SoundEvents.BOTTLE_EMPTY : SoundEvents.BUCKET_EMPTY,
						SoundSource.PLAYERS, 1.0f, 1.0f);
				sl.playSound(null, sp.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6f, 1.2f);
			}
			return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
		});

		PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, AQUATIC_ATK);
			}
		});

		PowerPassives.registerTick(KEY, WaterHandlers::passiveTick);
	}

	private static void passiveTick(ServerPlayer player) {
		Power power = power();
		if (power == null) {
			return;
		}
		// v0.13.22: the old "water" build-up gauge is replaced by the carried supply -- start new owners full
		BatchBUtil.retire(player, power, "water");
		BatchBUtil.seed(player, power, SUPPLY, MAX_WATER);
		float gain;
		if (player.isInWater()) {
			gain = REFILL_WATER;
		} else if (player.isInWaterOrRain()) {
			gain = REFILL_RAIN;
		} else {
			gain = player.level().dimension() == Level.NETHER ? 0.0f : REFILL_AIR;
		}
		if (aquaticActive(player)) {
			gain *= 1.5f;
		}
		boolean spraying = BatchBUtil.get(player, power, "spraying") > 0.5f;
		if (gain > 0 && !spraying) {
			float have = supply(player);
			if (have < MAX_WATER) {
				BatchBUtil.set(player, power, SUPPLY, have + gain, MAX_WATER, 1.0f);
			}
		}
		player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 25, 0, false, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 25, 0, false, false, false));
		if (player.isInWater()) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 25, 1, false, false, false));
			player.setAirSupply(player.getMaxAirSupply());
		}
	}

	// ---------------- R: Water Shot / spray ----------------

	private static void waterShot(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
		Vec3 hand = AbilityHelpers.handPosition(p);
		Vec3 end = AbilityHelpers.aimPoint(p, 24.0);
		AbilityHelpers.line(level, hand, end, WATER_DUST, 4.0);
		AbilityHelpers.line(level, hand, end, ParticleTypes.FALLING_WATER, 1.0);
		level.sendParticles(ParticleTypes.SPLASH, end.x, end.y, end.z, 20, 0.3, 0.3, 0.3, 0.1);
		if (t != null) {
			if (AbilityHelpers.hurtLands(p, t, SHOT_DAMAGE + bonus(p))) {
				AbilityHelpers.knockbackFrom(t, p.position(), 1.0);
			}
			t.clearFire();
			markWet(t);
		}
		MutationVisuals.play(p, "cast_right");
		AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH, 1.0f, 0.9f);
		ctx.triggerCooldown();
	}

	private static void sprayTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!p.isShiftKeyDown() || !spendQuiet(p, COST_SPRAY_TICK)) {
			ctx.setResource("spraying", 0, 1);
			MutationVisuals.stopIf(p, "channel_right");
			return;
		}
		MutationVisuals.ensure(p, "channel_right");
		ServerLevel level = ctx.level();
		Vec3 origin = p.getEyePosition().add(0, -0.3, 0);
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, origin.add(look.scale(2.5)), 3.0)) {
			if (e.position().subtract(origin).normalize().dot(look) > 0.6) {
				AbilityHelpers.hurt(p, e, SPRAY_DAMAGE + bonus(p) * 0.4f);
				e.clearFire();
				markWet(e);
				AbilityHelpers.push(e, look.scale(0.12));
			}
		}
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, 7.0);
		double streamLen = bhr.getType() == HitResult.Type.BLOCK ? origin.distanceTo(bhr.getLocation()) : 7.0;
		for (double d = 0.6; d <= streamLen + 0.01; d += 0.5) {
			Vec3 pt = origin.add(look.scale(d));
			level.sendParticles(ParticleTypes.SPLASH, pt.x, pt.y, pt.z, 3, 0.08 * d, 0.08 * d, 0.08 * d, 0.01);
			if (d % 1.0 < 0.5) {
				level.sendParticles(WATER_DUST, pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
			BlockPos bp = BlockPos.containing(pt);
			if (level.getBlockState(bp).is(BlockTags.FIRE)) {
				level.removeBlock(bp, false);
			}
		}
		if (p.tickCount % 6 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.WEATHER_RAIN, SoundSource.PLAYERS, 0.5f, 1.6f);
		}
	}

	private static boolean spendQuiet(ServerPlayer p, float base) {
		float c = cost(p, base);
		float have = supply(p);
		if (have < c) {
			return false;
		}
		BatchBUtil.set(p, power(), SUPPLY, have - c, MAX_WATER, 0.0f);
		return true;
	}

	// ---------------- G: Water Whip ----------------

	/**
	 * G: a tendril of water uncurls from your hand along your aim (12 blocks), sways through the air and snaps. Every
	 * creature it touches takes 19 and is hauled toward you; it leaves them soaked (the Water + Electro combo).
	 */
	private static void waterWhip(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 hand = AbilityHelpers.handPosition(p);
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		float damage = WHIP_DAMAGE + bonus(p);
		Set<LivingEntity> hit = new HashSet<>();
		Vec3 sway = right;
		Vec3 lift = up;
		double range = 12.0;
		BatchBScheduler.schedule(level, age -> {
			if (!p.isAlive()) {
				return false;
			}
			double reach = Math.min(range, (age + 1) * (range / 5.0));
			int segs = (int) (reach * 2.5);
			Vec3 tip = hand;
			for (int i = 1; i <= segs; i++) {
				double s = i / (double) segs;
				double wave = Math.sin(s * Math.PI * 1.5 - age * 0.8) * 0.8 * s;
				Vec3 pt = hand.add(look.scale(reach * s)).add(sway.scale(wave)).add(lift.scale(Math.sin(s * Math.PI) * 0.5));
				level.sendParticles(WATER_DUST, pt.x, pt.y, pt.z, 2, 0.04, 0.04, 0.04, 0.0);
				if (i % 2 == 0) {
					level.sendParticles(ParticleTypes.FALLING_WATER, pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0.0);
				}
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, pt, 1.0)) {
					if (hit.add(e)) {
						if (AbilityHelpers.hurtLands(p, e, damage)) {
							Vec3 pull = p.position().subtract(e.position()).normalize();
							AbilityHelpers.push(e, new Vec3(pull.x * 1.2, 0.35, pull.z * 1.2));
						}
						markWet(e);
						level.sendParticles(ParticleTypes.SPLASH, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
								16, 0.3, 0.3, 0.3, 0.1);
					}
				}
				tip = pt;
			}
			if (age == 4) {
				level.sendParticles(ParticleTypes.SPLASH, tip.x, tip.y, tip.z, 30, 0.4, 0.4, 0.4, 0.2);
				level.sendParticles(FOAM, tip.x, tip.y, tip.z, 10, 0.3, 0.3, 0.3, 0.0);
				level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0f, 1.3f);
			}
			return age < 6;
		});
		MutationVisuals.play(p, "whip_right");
		AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.0f);
		ctx.triggerCooldown();
	}

	// ---------------- X: Riptide ----------------

	private static void riptide(AbilityContext ctx, boolean wet) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 look = p.getLookAngle();
		double strength = wet ? 3.4 : 2.6;
		Vec3 impulse = look.scale(strength).add(0.0, look.y < -0.1 ? 0.05 : 0.4, 0.0);
		AbilityHelpers.addImpulse(p, impulse);
		p.resetFallDistance();
		ctx.setResource("no_fall_until", level.getGameTime() + 60, 1.0e12f);
		p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 80, 1, false, false, false));
		AbilityHelpers.line(level, p.getEyePosition(), p.getEyePosition().add(look.scale(6.0)), ParticleTypes.SPLASH, 3.0);
		AbilityHelpers.line(level, p.position(), p.position().subtract(look.scale(3.0)), WATER_DUST, 3.0);
		AbilityHelpers.burst(level, p.position(), ParticleTypes.FALLING_WATER, 24, 0.5);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(look.scale(2.0)), 2.5)) {
			AbilityHelpers.hurtLands(p, e, RIPTIDE_DAMAGE + bonus(p));
			AbilityHelpers.push(e, look.scale(1.2).add(0, 0.2, 0));
			markWet(e);
		}
		MutationVisuals.play(p, "dash_forward");
		AbilityHelpers.sound(p, SoundEvents.TRIDENT_RIPTIDE_1, 1.0f, 1.1f);
		ctx.triggerCooldown();
	}

	// ---------------- Z: Tidal Wave ----------------

	private static void tidalChargeTick(AbilityContext ctx) {
		long held = BatchBUtil.chargeHeld(ctx, "tidal_start");
		if (held < 0) {
			return;
		}
		ServerPlayer p = ctx.player();
		if (held > TIDAL_CHARGE + 100) {
			tidalCancel(ctx);
			return;
		}
		BatchBUtil.chargeMeter(ctx, held, TIDAL_CHARGE);
		MutationVisuals.ensure(p, "channel_two_hand");
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 1.0, 0.3));
		double frac = Math.min(1.0, held / (double) TIDAL_CHARGE);
		// the water gathers into a swelling sphere in front of you
		Vec3 ball = p.getEyePosition().add(BatchBUtil.flatLook(p).scale(1.8)).add(0, 0.3, 0);
		ctx.level().sendParticles(WATER_DUST, ball.x, ball.y, ball.z, 4 + (int) (frac * 12), 0.2 + frac * 0.6,
				0.2 + frac * 0.6, 0.2 + frac * 0.6, 0.0);
		ctx.level().sendParticles(ParticleTypes.FALLING_WATER, p.getX(), p.getY() + 1.0, p.getZ(),
				4 + (int) (frac * 10), 0.6 * frac + 0.4, 0.6, 0.6 * frac + 0.4, 0.02);
		if (held % 16 == 0) {
			AbilityHelpers.sound(p, SoundEvents.AMBIENT_UNDERWATER_LOOP, 0.7f, 0.4f + (float) frac);
		}
		if (held >= TIDAL_CHARGE) {
			tidalFire(ctx);
		}
	}

	private static void tidalCancel(AbilityContext ctx) {
		BatchBUtil.chargeClear(ctx, "tidal_start");
		MutationVisuals.stopIf(ctx.player(), "channel_two_hand");
		AbilityHelpers.sound(ctx.player(), SoundEvents.GENERIC_SPLASH, 0.4f, 1.2f);
	}

	/**
	 * Z: a wall of real water, 7 wide and 2 high, rolls 18 blocks out along your aim (1.2 blocks a tick). Everything it
	 * sweeps over takes 42 and is carried along with it. The water only ever fills air and drains 6 ticks behind the
	 * crest, so it leaves nothing behind.
	 */
	private static void tidalFire(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		BatchBUtil.chargeClear(ctx, "tidal_start");
		if (!spend(p, COST_TIDAL)) {
			MutationVisuals.stopIf(p, "channel_two_hand");
			return;
		}
		Vec3 dir = BatchBUtil.flatLook(p);
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Vec3 origin = p.position();
		float dmg = TIDAL_DAMAGE + bonus(p);
		Set<LivingEntity> hit = new HashSet<>();
		BlockState water = Blocks.WATER.defaultBlockState();
		BatchBScheduler.schedule(level, age -> {
			double d = 2.0 + age * 1.2;
			if (d > 20.0) {
				return false;
			}
			Vec3 front = origin.add(dir.scale(d));
			for (int w = -3; w <= 3; w++) {
				Vec3 col = front.add(side.scale(w));
				BlockPos cell = surface(level, col.x, origin.y, col.z);
				if (cell == null) {
					continue;
				}
				if (Math.abs(w) < 3 || age % 2 == 0) {
					BatchBScheduler.placeTemporarily(level, cell, water, 6);
					if (Math.abs(w) < 2) {
						BatchBScheduler.placeTemporarily(level, cell.above(), water, 4);
					}
				}
				level.sendParticles(ParticleTypes.SPLASH, cell.getX() + 0.5, cell.getY() + 1.6, cell.getZ() + 0.5,
						6, 0.4, 0.3, 0.4, 0.15);
				if (w % 2 == 0) {
					level.sendParticles(FOAM, cell.getX() + 0.5, cell.getY() + 2.2, cell.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.0);
				}
			}
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, front.add(0, 1, 0), 4.0)) {
				if (hit.add(e)) {
					AbilityHelpers.hurtLands(p, e, dmg);
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true, true));
					markWet(e);
				}
				AbilityHelpers.push(e, dir.scale(0.45).add(0, 0.12, 0));
			}
			if (age % 4 == 0) {
				level.playSound(null, BlockPos.containing(front), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS,
						1.4f, 0.5f);
			}
			return true;
		});
		MutationVisuals.play(p, "p25.wave_push");
		level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.6f, 0.4f);
		ctx.triggerCooldown(TIDAL_CD);
	}

	/** The first air cell above solid (or water-free) ground near {@code (x, z)} around height {@code y}. */
	private static BlockPos surface(ServerLevel level, double x, double y, double z) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		int top = Mth.floor(y) + 2;
		for (int yy = top; yy >= top - 5; yy--) {
			BlockPos bp = new BlockPos(bx, yy, bz);
			BlockState below = level.getBlockState(bp.below());
			if (level.getBlockState(bp).isAir() && !below.isAir()) {
				return bp;
			}
		}
		return null;
	}

	// ---------------- V: Water Prison + water shaping ----------------

	/**
	 * V: seal a target (16 blocks) in a shell of water for 8 s -- pinned in place, unable to jump, weakened, but kept
	 * breathing. The shell is real water that never flows and drains the tick the prison ends. Press V again to let
	 * them go early; the cooldown starts when it ends.
	 */
	private static void startPrison(AbilityContext ctx, LivingEntity t) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Power power = ctx.power();
		var ability = ctx.ability();
		long until = level.getGameTime() + PRISON_TICKS;
		ctx.setResource("prison_until", until, 1e12f);
		Vec3 pin = t.position();
		boolean isPlayer = t instanceof Player;
		BlockState water = Blocks.WATER.defaultBlockState();
		BatchBScheduler.schedule(level, age -> {
			boolean held = ExperimentalPowers.getResource(p, power, "prison_until") > level.getGameTime();
			if (!t.isAlive() || !p.isAlive() || !held || !ExperimentalPowers.owns(p, power)) {
				if (ExperimentalPowers.getResource(p, power, "prison_until") > 0.5f) {
					ExperimentalPowers.setResource(p, power, "prison_until", 0, 1e12f);
				}
				// an early release already started the cooldown; otherwise it starts now
				if (ExperimentalPowers.cooldownReady(p, power, ability)) {
					ExperimentalPowers.triggerCooldown(p, power, ability,
							com.projecthero.mod.hero.HeroConfig.get().scaledCooldown(ability.cooldownTicks()));
				}
				return false;
			}
			t.setDeltaMovement(0, 0, 0);
			t.hurtMarked = true;
			t.fallDistance = 0;
			t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, isPlayer ? 4 : 9, false, false, false));
			t.addEffect(new MobEffectInstance(MobEffects.JUMP, 10, -10, false, false, false));
			t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 2, false, false, false));
			t.setAirSupply(t.getMaxAirSupply()); // the prison traps, it does not drown
			if (!isPlayer && t.distanceToSqr(pin) > 0.04) {
				t.teleportTo(pin.x, pin.y, pin.z);
			}
			BlockPos base = t.blockPosition();
			for (Direction d : Direction.values()) {
				BatchBScheduler.placeTemporarily(level, base.relative(d), water, 3);
				BatchBScheduler.placeTemporarily(level, base.above().relative(d), water, 3);
			}
			if (age % 4 == 0) {
				level.sendParticles(ParticleTypes.BUBBLE, t.getX(), t.getY() + 1, t.getZ(), 20, 0.5, 1.0, 0.5, 0.0);
				level.sendParticles(WATER_DUST, t.getX(), t.getY() + 1, t.getZ(), 10, 0.6, 1.0, 0.6, 0.0);
			}
			return true;
		});
	}

	/** Sneak + hold V for 4 s: shape a (temporary, 60 s) water source where you look. */
	private static void createTick(AbilityContext ctx) {
		float ct = ctx.resource("create_ticks");
		if (ct <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		if (!p.isShiftKeyDown()) {
			ctx.setResource("create_ticks", 0, CREATE_TICKS);
			return;
		}
		ct -= 1.0f;
		ctx.setResource("create_ticks", ct, CREATE_TICKS);
		MutationVisuals.ensure(p, "channel_right");
		if (p.tickCount % 4 == 0) {
			Vec3 aim = AbilityHelpers.aimPoint(p, 12.0);
			ctx.level().sendParticles(ParticleTypes.SPLASH, aim.x, aim.y, aim.z, 4, 0.2, 0.2, 0.2, 0.0);
		}
		if (ct <= 0.5f) {
			MutationVisuals.stopIf(p, "channel_right");
			BlockHitResult hit = AbilityHelpers.raycastBlock(p, 12.0);
			BlockPos target = hit.getType() == HitResult.Type.BLOCK
					? hit.getBlockPos().relative(hit.getDirection())
					: BlockPos.containing(AbilityHelpers.aimPoint(p, 12.0));
			if (spend(p, COST_PRISON) && BatchBScheduler.placeTemporarily(ctx.level(), target,
					Blocks.WATER.defaultBlockState(), CREATED_WATER_TTL)) {
				AbilityHelpers.sound(p, SoundEvents.BUCKET_EMPTY, 1.0f, 1.0f);
			}
		}
	}

	// ---------------- H: Healing Water ----------------

	/**
	 * H: pour 120 water (60 in Aquatic Form) over yourself and everyone on your side within 6 blocks -- squadmates and
	 * your own tamed animals: 6 health (3 hearts) each, 5 s of Regeneration I, and any fire put out.
	 */
	private static void healingWater(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		int healed = 0;
		var squads = com.projecthero.mod.squad.SquadManager.get(level.getServer());
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(HEAL_RANGE))) {
			boolean ally = e == p
					|| e instanceof ServerPlayer other && squads.sameSquad(p.getUUID(), other.getUUID())
					|| e instanceof OwnableEntity own && p.getUUID().equals(own.getOwnerUUID());
			if (!ally || !e.isAlive() || e.distanceToSqr(p) > HEAL_RANGE * HEAL_RANGE) {
				continue;
			}
			e.heal(HEAL_AMOUNT);
			e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, true, true));
			e.clearFire();
			healed++;
			level.sendParticles(ParticleTypes.FALLING_WATER, e.getX(), e.getY() + e.getBbHeight() + 0.4, e.getZ(),
					20, 0.35, 0.1, 0.35, 0.0);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
					6, 0.3, 0.4, 0.3, 0.0);
		}
		for (int i = 0; i < 32; i++) {
			double a = i / 32.0 * Math.PI * 2;
			level.sendParticles(WATER_DUST, p.getX() + Math.cos(a) * 1.5, p.getY() + 0.2, p.getZ() + Math.sin(a) * 1.5,
					1, 0.0, 0.3, 0.0, 0.0);
		}
		level.playSound(null, p.blockPosition(), SoundEvents.CONDUIT_AMBIENT_SHORT, SoundSource.PLAYERS, 1.2f, 1.2f);
		level.playSound(null, p.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8f, 1.3f);
		ctx.setResource("healed", healed, 1000);
		MutationVisuals.play(p, "cast_raise_both");
		ctx.triggerCooldown();
	}

	// ---------------- N: Geyser ----------------

	/**
	 * N: a geyser erupts. Looking at a creature (20 blocks): under them -- 12 damage and a launch straight up, soaked.
	 * Otherwise under you -- it throws you ~8 blocks up with 8 s of no fall damage.
	 */
	private static void geyser(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		LivingEntity t = AbilityHelpers.raycastEntity(p, 20.0);
		Entity rider = t != null ? t : p;
		Vec3 base = rider.position();
		if (t != null) {
			AbilityHelpers.hurtLands(p, t, GEYSER_DAMAGE + bonus(p));
			AbilityHelpers.push(t, new Vec3(0, 1.4, 0));
			t.clearFire();
			markWet(t);
			MutationVisuals.play(p, "summon_ground");
		} else {
			Vec3 look = BatchBUtil.flatLook(p);
			AbilityHelpers.launchSelf(p, new Vec3(look.x * 0.2, 1.4, look.z * 0.2));
			ctx.setResource("no_fall_until", level.getGameTime() + 160, 1.0e12f);
			MutationVisuals.play(p, "leap");
		}
		BatchBScheduler.schedule(level, age -> {
			double h = Math.min(8.0, 1.0 + age * 1.5);
			for (double y = 0; y < h; y += 0.5) {
				level.sendParticles(WATER_DUST, base.x, base.y + y, base.z, 2, 0.25, 0.1, 0.25, 0.0);
			}
			level.sendParticles(ParticleTypes.SPLASH, base.x, base.y + h, base.z, 12, 0.5, 0.2, 0.5, 0.2);
			level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, base.x, base.y + 0.5, base.z, 6, 0.3, 0.3, 0.3, 0.1);
			if (age == 0) {
				level.playSound(null, BlockPos.containing(base), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE,
						SoundSource.PLAYERS, 1.6f, 0.7f);
			}
			return age < 12;
		});
		level.sendParticles(ParticleTypes.SPLASH, base.x, base.y + 0.2, base.z, 40, 1.0, 0.1, 1.0, 0.2);
		level.playSound(null, BlockPos.containing(base), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.4f, 0.7f);
		ctx.triggerCooldown();
	}

	/** Combo: Water + Electrokinesis -- mark a target "wet" for bonus electrical damage. */
	private static void markWet(LivingEntity e) {
		e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, false));
		if (e.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.FALLING_WATER, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 8, 0.3, 0.1, 0.3, 0.0);
		}
	}
}
