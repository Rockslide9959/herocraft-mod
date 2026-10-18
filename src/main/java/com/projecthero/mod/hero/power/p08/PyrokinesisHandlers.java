package com.projecthero.mod.hero.power.p08;

import java.util.Optional;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.revamp.BatchBUtil;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 08 -- Pyrokinesis (v0.16.5 rework, docs/V0165_POWERS_PROPOSAL.md, the Pyrokinesis section). One <b>Heat</b>
 * meter (0-100) that every move RAISES: the hotter you run the harder your fire hits (up to +40% from 80 Heat). Reach
 * 100 and you <b>overheat</b> -- a forced nova of fire bursts out of you (12 to everything near) and you can't use a
 * move for 5 s. Heat cools 10 a second while nothing hot is running (three times faster in water or rain).
 *
 * <p>R Fireball / Shift+R Flamethrower (held). G Fire Whip / Shift+G Fire Lasso. X Flame Dash / Shift+X Jet Flight
 * (toggle). Z Meteor (hold to charge) / Shift+Z Inferno. V Flame Wall / Shift+V Flame Body (toggle). C Heat Wave /
 * Shift+C Cauterize. No H / N. All six slots are HOLD so the handlers own their tap / Shift / release logic.
 *
 * <p>v0.16.6 (user: hold moves for the elemental powers): every key is tap vs hold ({@link com.projecthero.mod.hero.HoldTap}) --
 * a tap is the old move (on the release), holding 0.6 s fires the key's hold move: R Blue Flame, G Flame Serpent, X Phoenix
 * Dive, Z Meteor Shower, V Ring of Fire, C Solar Flare, each with its own cooldown. The Z Meteor used to be held to charge,
 * so it is a toggle now: tap to start charging, tap again to throw (it throws itself after 5 s).
 *
 * <p>Passives: immune to fire and lava, punches set things alight, lava doesn't slow you ({@code PyroLavaMixin}).
 */
public final class PyrokinesisHandlers {
	public static final String KEY = "power_08_pyrokinesis";

	// ---- Heat ----
	public static final float MAX_HEAT = 100.0f;
	/** 10 a second. */
	public static final float COOL_PER_TICK = 10.0f / 20.0f;
	/** In water or rain Heat bleeds off three times as fast. */
	public static final float WET_COOL_MULT = 3.0f;
	/** The fire bonus grows evenly to its +40% at this much Heat. */
	public static final float FULL_BONUS_HEAT = 80.0f;
	public static final float MAX_BONUS = 0.4f;
	public static final int LOCK_TICKS = 5 * 20;
	public static final float OVERHEAT_DAMAGE = 12.0f;
	public static final double OVERHEAT_RADIUS = 5.0;
	public static final int IGNITE_TICKS = 5 * 20;

	// ---- R: Fireball / Flamethrower ----
	public static final float FIREBALL_DAMAGE = 14.0f;
	public static final float HEAT_FIREBALL = 8.0f;
	private static final double FIREBALL_SPEED = 1.8;
	private static final int FIREBALL_CD = 8;
	public static final double THROWER_RANGE = 8.0;
	public static final float THROWER_DAMAGE = 4.0f;
	/** One burst of the stream every this many ticks (4 bursts a second). */
	public static final int THROWER_INTERVAL = 5;
	public static final float HEAT_THROWER_PER_SECOND = 10.0f;
	private static final double THROWER_CONE = 0.82; // cos ~35 degrees

	// ---- G: Fire Whip / Fire Lasso ----
	public static final double WHIP_RANGE = 12.0;
	public static final float WHIP_DAMAGE = 12.0f;
	public static final float HEAT_WHIP = 10.0f;
	private static final int WHIP_CD = 20;
	/** How long the lash is drawn (out in 3 ticks, crack, back). */
	public static final int WHIP_DRAW_TICKS = 12;
	public static final double LASSO_RANGE = 12.0;
	public static final int LASSO_TICKS = 3 * 20;
	public static final float LASSO_DAMAGE_PER_SECOND = 3.0f;
	public static final float HEAT_LASSO = 20.0f;
	public static final int LASSO_CD = 6 * 20;

	// ---- X: Flame Dash / Jet Flight ----
	public static final double DASH_DISTANCE = 10.0;
	public static final int DASH_TICKS = 5;
	public static final float HEAT_DASH = 10.0f;
	private static final int DASH_CD = 30;
	public static final float HEAT_JET_PER_SECOND = 2.0f; // v0.16.5 (user): less heat, so you can fly for a while (was 5)
	private static final String JET_LANDING = "pyro_jet";
	private static final float JET_FLY_SPEED = HeroFlight.HERO_FLYING_SPEED;

	// ---- Z: Meteor / Inferno ----
	public static final int METEOR_CHARGE_TICKS = 40;
	public static final float METEOR_DAMAGE_MIN = 16.0f;
	public static final float METEOR_DAMAGE_MAX = 30.0f;
	public static final double METEOR_RADIUS = 5.0;
	public static final float HEAT_METEOR = 25.0f;
	public static final int METEOR_CD = 3 * 20;
	public static final float INFERNO_DAMAGE_MAX = 30.0f;
	public static final double INFERNO_RADIUS = 8.0;
	public static final float INFERNO_MIN_HEAT = 10.0f;

	// ---- V: Flame Wall / Flame Body ----
	public static final double WALL_WIDTH = 8.0;
	public static final int WALL_TICKS = 8 * 20;
	/** Every half second to whatever stands in the fire. */
	public static final float WALL_DAMAGE = 3.0f;
	public static final float HEAT_WALL = 15.0f;
	public static final int WALL_CD = 3 * 20;
	public static final float HEAT_BODY_PER_SECOND = 4.0f;
	public static final float BODY_MELEE_FACTOR = 0.7f;

	// ---- C: Heat Wave / Cauterize ----
	public static final double WAVE_RANGE = 8.0;
	private static final double WAVE_CONE = 0.5; // cos 60 degrees
	public static final float HEAT_WAVE = 10.0f;
	private static final int WAVE_CD = 30;
	public static final float CAUTERIZE_HEAT = 40.0f;
	public static final float CAUTERIZE_HEAL = 16.0f;
	public static final int CAUTERIZE_CD = 20 * 20;

	// ---- v0.16.6 hold moves (HoldTap: hold the key 0.6 s; a tap is the old move) ----
	/** Hold R: Blue Flame -- a short wind-up, then a blue-white fireball that ignores Fire Resistance. */
	public static final float BLUE_DAMAGE = 24.0f;
	public static final float HEAT_BLUE = 20.0f;
	public static final int BLUE_BURN_TICKS = 8 * 20;
	public static final int BLUE_WINDUP = 10;
	public static final int BLUE_CD = 6 * 20;
	private static final double BLUE_SPEED = 2.1;
	/** Hold G: Flame Serpent -- coils off the arm, then hunts the nearest enemy within 25 blocks. */
	public static final float SERPENT_DAMAGE = 18.0f;
	public static final double SERPENT_RANGE = 25.0;
	public static final float HEAT_SERPENT = 25.0f;
	public static final int SERPENT_CD = 10 * 20;
	public static final int SERPENT_BURN_TICKS = 8 * 20;
	/** Hold X: Phoenix Dive -- rise wrapped in a firebird, then dive along the aim into a blast. */
	public static final float PHOENIX_DAMAGE = 20.0f;
	/** 8 blocks across. */
	public static final double PHOENIX_RADIUS = 4.0;
	public static final float HEAT_PHOENIX = 25.0f;
	public static final int PHOENIX_CD = 8 * 20;
	public static final int PHOENIX_RISE_TICKS = 10;
	public static final int PHOENIX_DIVE_MAX_TICKS = 30;
	private static final double PHOENIX_DIVE_SPEED = 1.7;
	/** Hold Z: Meteor Shower -- six smaller meteors on the aimed spot (up to 30 away). */
	public static final int SHOWER_COUNT = 6;
	public static final float SHOWER_DAMAGE = 10.0f;
	public static final double SHOWER_RANGE = 30.0;
	public static final double SHOWER_SPREAD = 4.0;
	public static final double SHOWER_BLAST = 2.5;
	public static final float HEAT_SHOWER = 35.0f;
	public static final int SHOWER_CD = 15 * 20;
	/** Z: the Meteor is a toggle now (tap to charge, tap to throw); it throws itself this long after the tap. */
	public static final int METEOR_AUTO_THROW_TICKS = 5 * 20;
	/** Hold V: Ring of Fire -- a circle of flame round you; whatever crosses it burns. */
	public static final double RING_RADIUS = 6.0;
	public static final int RING_TICKS = 10 * 20;
	public static final float RING_DAMAGE = 4.0f;
	public static final float HEAT_RING = 20.0f;
	public static final int RING_CD = 15 * 20;
	/** Hold C: Solar Flare -- a blinding burst 12 blocks round you. */
	public static final double FLARE_RADIUS = 12.0;
	public static final int FLARE_BLIND_TICKS = 4 * 20;
	public static final int FLARE_IGNITE_TICKS = 6 * 20;
	public static final float HEAT_FLARE = 15.0f;
	public static final int FLARE_CD = 12 * 20;

	/** Pre-v0.16.5 Flame Body melee modifier, cleared out of old saves. */
	private static final ResourceLocation LEGACY_BODY_ATK = com.projecthero.mod.ProjectHeroMod.id("flame_body_atk");
	/** Resources an older version of the kit used. */
	private static final String[] LEGACY = { "flaming", "jetting", "overheated", "pyro_start", "flamethrower", "flame_body",
			"flame_body_regen_until", "pyro_lightning", "no_fall_until", "flameflight", "ult_charge" };

	/** Resources cannot go below 0: the dash direction is stored shifted up by this much. */
	private static final float V_SHIFT = 16.0f;

	private PyrokinesisHandlers() {
	}

	// ================================================================ shared helpers / queries

	private static float res(ServerPlayer p, String name) {
		return BatchA.res(p, KEY, name);
	}

	private static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, KEY, name, v);
	}

	/** Reads a resource from the synced attachment (works on both sides; the client only has its own player's). */
	private static float resAny(Player p, String name) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null ? 0.0f : st.resources.getOrDefault(KEY + "/" + name, 0.0f);
	}

	public static boolean owns(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY);
	}

	/** Kept for {@code LargeFireballMixin} (a vanilla fireball owned by a pyrokinetic). */
	public static boolean ownsPyrokinesis(ServerPlayer p) {
		return owns(p);
	}

	/** Kept for {@code LargeFireballMixin}: a vanilla large fireball owned by a pyrokinetic hits like an R Fireball. */
	public static float fireballImpactDamage(ServerPlayer p) {
		return dmg(p, FIREBALL_DAMAGE);
	}

	public static float heat(Player p) {
		return owns(p) ? resAny(p, "heat") : 0.0f;
	}

	/** Overheated: the 5 s after the forced nova, with no moves. */
	public static boolean locked(Player p) {
		return owns(p) && resAny(p, "lock") > 0.5f;
	}

	/** Old name, kept for the older tests / callers. */
	public static boolean overheated(ServerPlayer p) {
		return locked(p);
	}

	public static boolean throwing(Player p) {
		return owns(p) && resAny(p, "thrower") > 0.5f;
	}

	public static boolean jetting(Player p) {
		return owns(p) && resAny(p, "jet") > 0.5f;
	}

	public static boolean flameBodyActive(Player p) {
		return owns(p) && resAny(p, "body") > 0.5f;
	}

	public static boolean dashing(Player p) {
		return owns(p) && resAny(p, "dash") > 0.5f;
	}

	public static boolean charging(Player p) {
		return owns(p) && resAny(p, "meteor") > 0.5f;
	}

	/** The Meteor's charge, 0..1. */
	public static float meteorCharge(Player p) {
		return charging(p) ? resAny(p, "meteor_charge") / 100.0f : 0.0f;
	}

	/** The creature caught in the Fire Lasso, or 0. */
	public static float lassoId(Player p) {
		return owns(p) && resAny(p, "lasso_ticks") > 0.5f ? resAny(p, "lasso_id") : 0.0f;
	}

	/** The fire-damage multiplier at {@code heat}: x1.0 cold, rising evenly to x1.4 at 80+ Heat. */
	public static float heatMultiplier(float heat) {
		return 1.0f + MAX_BONUS * Math.max(0.0f, Math.min(1.0f, heat / FULL_BONUS_HEAT));
	}

	public static float heatMult(ServerPlayer p) {
		return heatMultiplier(heat(p));
	}

	/** {@code base} with the Heat bonus the player has right now. */
	public static float dmg(ServerPlayer p, float base) {
		return base * heatMult(p);
	}

	/** Inferno damage for {@code heat}: up to 30 at full Heat. */
	public static float infernoDamage(float heat) {
		return INFERNO_DAMAGE_MAX * Math.max(0.0f, Math.min(1.0f, heat / MAX_HEAT));
	}

	/** Meteor base damage (before the Heat bonus) for a charge of {@code frac} (0..1). */
	public static float meteorDamage(float frac) {
		return METEOR_DAMAGE_MIN + (METEOR_DAMAGE_MAX - METEOR_DAMAGE_MIN) * Math.max(0.0f, Math.min(1.0f, frac));
	}

	private static void seed(ServerPlayer p) {
		if (res(p, "seeded") < 0.5f) {
			set(p, "seeded", 1);
			set(p, "heat", 0);
		}
	}

	/** For the gametests and admin: Heat set directly (no overheat). */
	public static void setHeat(ServerPlayer p, float v) {
		seed(p);
		BatchA.set(p, KEY, "heat", Math.max(0.0f, Math.min(MAX_HEAT, v)), MAX_HEAT);
	}

	/** Raises Heat; reaching 100 overheats. */
	public static void addHeat(ServerPlayer p, float amount) {
		seed(p);
		float h = Math.min(MAX_HEAT, res(p, "heat") + amount);
		BatchA.set(p, KEY, "heat", h, MAX_HEAT);
		if (h >= MAX_HEAT - 1.0e-3f && res(p, "lock") < 0.5f) {
			overheat(p);
		}
	}

	/** Refuses (with the overheated message) during the 5 s after an overheat. */
	private static boolean canAct(AbilityContext ctx) {
		seed(ctx.player());
		if (locked(ctx.player())) {
			ctx.actionBar("message.projecthero.pyro.overheated");
			return false;
		}
		return true;
	}

	private static boolean cooling(AbilityContext ctx, String timer) {
		float left = res(ctx.player(), timer);
		if (left < 0.5f) {
			return false;
		}
		ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(left / 20.0f)));
		return true;
	}

	/** v0.16.6: {@link #cooling} for a hold move (the message names the hold move). */
	private static boolean coolingHold(AbilityContext ctx, String timer) {
		float left = res(ctx.player(), timer);
		if (left < 0.5f) {
			return false;
		}
		ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey() + ".hold"),
				String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(left / 20.0f)));
		return true;
	}

	/** Any held / toggled fire that holds the Heat up (cooling pauses while one runs). */
	private static boolean burning(ServerPlayer p) {
		return res(p, "thrower") > 0.5f || res(p, "jet") > 0.5f || res(p, "body") > 0.5f || res(p, "meteor") > 0.5f;
	}

	private static Vec3 mid(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	private static boolean enemy(ServerPlayer p, LivingEntity e) {
		return e != p && e.isAlive() && HeroTargets.canHarm(p, e) && !Squads.areAllies(p, e);
	}

	private static boolean noBoss(LivingEntity t) {
		return !com.projecthero.mod.titanshifter.TitanCombat.isBoss(t) && t.getMaxHealth() <= 200.0f;
	}

	private static void ignite(LivingEntity e, int ticks) {
		if (e.getRemainingFireTicks() < ticks) {
			e.setRemainingFireTicks(ticks);
		}
	}

	/** Whether this player may set fire to / change {@code pos}: terrain damage on, and they may build there. */
	public static boolean mayBurn(ServerPlayer p, ServerLevel level, BlockPos pos) {
		return AbilityHelpers.canGrief() && p.mayBuild() && level.mayInteract(p, pos) && level.getWorldBorder().isWithinBounds(pos);
	}

	/** A temporary fire block at {@code pos}, only where the player may build and fire can sit. */
	public static boolean placeFire(ServerPlayer p, ServerLevel level, BlockPos pos, int ttl) {
		if (!mayBurn(p, level, pos) || !level.getBlockState(pos).isAir() || !BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
			return false;
		}
		return TempBlocks.place(level, pos, BaseFireBlock.getState(level, pos), ttl);
	}

	/** Player-relative pose start for the client models (game time mod 8192, +1 so it is never 0). */
	private static float stamp(ServerPlayer p) {
		return (float) (p.level().getGameTime() % 8192L) + 1.0f;
	}

	// ================================================================ registration

	/**
	 * v0.16.5 (user: "add a way for pyrokinesis players to cook raw food"): the cooked form of a food stack -- whatever a
	 * furnace would make of it, if that is food -- or empty. Works on both sides (the client has the recipes too).
	 */
	public static ItemStack searedForm(Level level, ItemStack raw) {
		if (raw.isEmpty() || raw.get(net.minecraft.core.component.DataComponents.FOOD) == null) {
			return ItemStack.EMPTY;
		}
		Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
				new SingleRecipeInput(raw), level);
		if (recipe.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack out = recipe.get().value().getResultItem(level.registryAccess());
		if (out.isEmpty() || out.get(net.minecraft.core.component.DataComponents.FOOD) == null) {
			return ItemStack.EMPTY;
		}
		return out.copyWithCount(Math.min(out.getMaxStackSize(), raw.getCount() * out.getCount()));
	}

	/** Sneak + use raw food: the whole stack is seared in your hand (a little Heat for it). */
	private static net.minecraft.world.InteractionResultHolder<ItemStack> searInHand(Player player, Level level,
			net.minecraft.world.InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!player.isShiftKeyDown() || player.isSpectator() || !owns(player)) {
			return net.minecraft.world.InteractionResultHolder.pass(held);
		}
		ItemStack cooked = searedForm(level, held);
		if (cooked.isEmpty()) {
			return net.minecraft.world.InteractionResultHolder.pass(held);
		}
		if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
			sp.setItemInHand(hand, cooked);
			set(sp, "heat", Math.min(MAX_HEAT, res(sp, "heat") + 2.0f));
			Vec3 at = sp.position().add(sp.getLookAngle().scale(0.6)).add(0, 1.1, 0);
			sl.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 10, 0.12, 0.12, 0.12, 0.02);
			sl.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.1, at.z, 6, 0.1, 0.1, 0.1, 0.01);
			sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f);
			sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.CAMPFIRE_CRACKLE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
		}
		player.swing(hand);
		return net.minecraft.world.InteractionResultHolder.sidedSuccess(held, level.isClientSide());
	}

	public static void register() {
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register(PyrokinesisHandlers::searInHand);
		PyroEntities.initialize();

		// R -- Fireball / Flamethrower (Shift, held). Also runs the power-wide upkeep.
		AbilityHandlers.register(KEY, "fireball", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!canAct(ctx)) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					startThrower(ctx);
					return;
				}
				if (res(p, "fireball_cd") > 0.5f) {
					return;
				}
				fireball(p);
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				if (canAct(ctx) && !coolingHold(ctx, "blue_cd")) {
					startBlueFlame(ctx.player());
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				upkeep(ctx);
				blueTick(ctx.player());
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				stopThrower(ctx.player());
			}
		});

		// G -- Fire Whip / Fire Lasso (Shift)
		AbilityHandlers.register(KEY, "fire_whip", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown() && res(p, "lasso_ticks") > 0.5f) {
					endLasso(p); // a second Shift+G lets go
					return;
				}
				if (!canAct(ctx)) {
					return;
				}
				if (p.isShiftKeyDown()) {
					if (!cooling(ctx, "lasso_cd")) {
						lasso(ctx);
					}
				} else if (res(p, "whip_cd") < 0.5f) {
					fireWhip(p);
				}
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				if (canAct(ctx) && !coolingHold(ctx, "serpent_cd")) {
					flameSerpent(ctx.player());
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				lassoTick(ctx.player(), ctx.level());
			}
		});

		// X -- Flame Dash / Jet Flight (Shift, toggle)
		AbilityHandlers.register(KEY, "flame_dash", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (res(p, "jet") > 0.5f) {
						stopJet(p);
					} else if (canAct(ctx)) {
						startJet(ctx);
					}
					return;
				}
				if (canAct(ctx) && res(p, "dash_cd") < 0.5f) {
					flameDash(p);
				}
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "phoenix") > 0.5f || !canAct(ctx) || coolingHold(ctx, "phoenix_cd")) {
					return;
				}
				if (p.isInWater()) {
					ctx.actionBar("message.projecthero.pyro.doused");
					return;
				}
				startPhoenix(p);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				dashTick(ctx.player(), ctx.level());
				jetTick(ctx.player(), ctx.level());
				phoenixTick(ctx.player(), ctx.level());
			}
		});

		// Z -- Meteor (v0.16.6: a toggle -- tap to charge, tap again to throw) / Inferno (Shift) / hold: Meteor Shower
		AbilityHandlers.register(KEY, "meteor", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (!p.isShiftKeyDown() && res(p, "meteor") > 0.5f) {
					releaseMeteor(p); // the second tap throws it
					return;
				}
				if (!canAct(ctx)) {
					return;
				}
				if (p.isShiftKeyDown()) {
					inferno(ctx);
					return;
				}
				if (!cooling(ctx, "meteor_cd")) {
					startMeteor(p);
				}
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				if (canAct(ctx) && !coolingHold(ctx, "shower_cd")) {
					meteorShower(ctx.player());
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				meteorTick(ctx.player());
			}
		});

		// V -- Flame Wall / Flame Body (Shift, toggle)
		AbilityHandlers.register(KEY, "flame_wall", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (res(p, "body") > 0.5f) {
						setFlameBody(p, false);
					} else if (canAct(ctx)) {
						setFlameBody(p, true);
					}
					return;
				}
				if (canAct(ctx) && !cooling(ctx, "wall_cd")) {
					flameWall(p);
				}
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				if (canAct(ctx) && !coolingHold(ctx, "ring_cd")) {
					ringOfFire(ctx.player());
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				bodyTick(ctx.player(), ctx.level());
			}
		});

		// C -- Heat Wave / Cauterize (Shift)
		AbilityHandlers.register(KEY, "heat_wave", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!canAct(ctx)) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (!cooling(ctx, "cauterize_cd")) {
						cauterize(ctx);
					}
				} else if (res(p, "wave_cd") < 0.5f) {
					heatWave(p);
				}
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				if (canAct(ctx) && !coolingHold(ctx, "flare_cd")) {
					solarFlare(ctx.player());
				}
			}
		});

		// v0.16.6 (user: hold moves for the elemental powers): every key is tap vs hold
		for (String id : new String[] { "fireball", "fire_whip", "flame_dash", "meteor", "flame_wall", "heat_wave" }) {
			com.projecthero.mod.hero.HoldTap.register(KEY, id);
		}
		// a logout mid-move leaves nothing half-done (the summoned serpent / ring see their owner gone and go out)
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer sp = handler.getPlayer();
			if (owns(sp)) {
				endHoldMoves(sp);
			}
		});

		// punches set things alight
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && owns(sp) && entity instanceof LivingEntity le && !player.isSpectator()) {
				ignite(le, 4 * 20);
				if (level instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.FLAME, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
				}
			}
			return InteractionResult.PASS;
		});
		// Flame Body: whoever hits you in melee catches fire
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && flameBodyActive(sp) && meleeAttacker(sp, source) instanceof LivingEntity attacker) {
				ignite(attacker, IGNITE_TICKS);
			}
		});

		registerPassives();
	}

	// ================================================================ upkeep (every tick for an owner)

	private static void upkeep(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		seed(p);
		for (String timer : new String[] { "fireball_cd", "whip_cd", "whip_ticks", "lasso_cd", "dash_cd", "meteor_cd", "wall_cd",
				"wave_cd", "cauterize_cd", "cauterize_fx", "nofall", "blue_cd", "serpent_cd", "phoenix_cd", "shower_cd", "ring_cd",
				"flare_cd", "flare_fx", "serpent_fx" }) {
			float v = res(p, timer);
			if (v > 0) {
				set(p, timer, v - 1);
			}
		}
		// overheated: no moves for 5 s, smoking
		float lock = res(p, "lock");
		if (lock > 0.5f) {
			set(p, "lock", lock - 1);
			if (lock <= 1.5f) {
				MutationVisuals.stopIf(p, "p08.overheat");
				AbilityHelpers.sound(p, SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.6f);
			} else {
				MutationVisuals.ensure(p, "p08.overheat");
				if (p.tickCount % 3 == 0) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.4, p.getZ(), 2, 0.25, 0.3, 0.25, 0.02);
				}
			}
		}
		throwerTick(p, level);
		// cooling: only while nothing hot is running
		if (!burning(p)) {
			float h = res(p, "heat");
			if (h > 0.0f) {
				float rate = COOL_PER_TICK * (p.isInWaterOrRain() ? WET_COOL_MULT : 1.0f);
				BatchA.set(p, KEY, "heat", Math.max(0.0f, h - rate), MAX_HEAT);
			}
		}
		// heat shimmer at high heat
		float h = res(p, "heat");
		if (h >= FULL_BONUS_HEAT && p.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.3, 0.6, 0.3, 0.01);
		}
		if (p.tickCount % 100 == 41) {
			dropLegacyState(p);
		}
	}

	private static void dropLegacyState(ServerPlayer p) {
		Power power = Powers.byKey(KEY);
		if (power == null) {
			return;
		}
		BatchBUtil.retire(p, power, LEGACY);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, LEGACY_BODY_ATK);
		if (res(p, "jet") < 0.5f && ExperimentalPowers.getResource(p, power, "flame_fly_ticks") > 0.5f) {
			ExperimentalPowers.setResource(p, power, "flame_fly_ticks", 0, 1);
		}
		// the old Flame Body was a TOGGLE ability in slot 6; its toggle flag is inert now
		ExperimentalPowers.state(p).activeToggles.removeIf(t -> t.startsWith(KEY + "/"));
	}

	/** Everything held or toggled stops (an overheat, or the power going away). */
	private static void endAll(ServerPlayer p) {
		stopThrower(p);
		stopJet(p);
		setFlameBody(p, false);
		cancelMeteor(p);
		endLasso(p);
		set(p, "dash", 0);
		endHoldMoves(p);
	}

	/** v0.16.6: the hold moves that run on the player (the Blue Flame wind-up, the Phoenix Dive) stop. */
	public static void endHoldMoves(ServerPlayer p) {
		if (res(p, "blue_charge") > 0.5f) {
			set(p, "blue_charge", 0);
			MutationVisuals.stopIf(p, "p08.blue_charge");
		}
		if (res(p, "phoenix") > 0.5f) {
			set(p, "phoenix", 0);
			set(p, "phoenix_t", 0);
			set(p, "nofall", 60);
			p.resetFallDistance();
			MutationVisuals.stopIf(p, "p08.phoenix_rise");
			MutationVisuals.stopIf(p, "p08.phoenix_dive");
		}
	}

	/** At 100 Heat: a forced nova of fire (12 to everything near), Heat vented to 0, no moves for 5 s. */
	public static int overheat(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		endAll(p);
		BatchA.set(p, KEY, "heat", 0, MAX_HEAT);
		set(p, "lock", LOCK_TICKS);
		int hit = 0;
		Vec3 c = p.position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(0, 1, 0), OVERHEAT_RADIUS)) {
			if (AbilityHelpers.hurtBurst(p, e, AbilityHelpers.fire(p), OVERHEAT_DAMAGE)) {
				hit++;
			}
			ignite(e, IGNITE_TICKS);
			AbilityHelpers.knockbackFrom(e, c, 1.2);
		}
		PyroFxEntity.spawn(level, p, PyroFxEntity.NOVA, c.add(0, 0.1, 0), p.getYRot(), (float) OVERHEAT_RADIUS, 16);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 1, c.z, 80, 1.2, 0.8, 1.2, 0.2);
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 1, c.z, 16, 0.8, 0.5, 0.8, 0.0);
		p.displayClientMessage(Component.translatable("message.projecthero.pyro.overheat"), true);
		MutationVisuals.play(p, "p08.nova");
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.4f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.3f);
		AbilityHelpers.sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.5f);
		return hit;
	}

	// ================================================================ R: Fireball / Flamethrower

	/** Throws a Fireball: 14 (+Heat bonus) and burning on a direct hit. +8 Heat. */
	public static PyroBoltEntity fireball(ServerPlayer p) {
		float damage = dmg(p, FIREBALL_DAMAGE);
		PyroBoltEntity b = PyroBoltEntity.launch(p, p.getLookAngle(), FIREBALL_SPEED, damage, false, 0.45f);
		set(p, "fireball_cd", FIREBALL_CD);
		MutationVisuals.play(p, "p08.throw");
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 1.1f);
		addHeat(p, HEAT_FIREBALL);
		return b;
	}

	/** A Fireball lands: the direct hit takes its damage and burns; a block it strikes catches fire. */
	public static void fireballImpact(ServerPlayer p, ServerLevel level, Vec3 at, LivingEntity direct, float damage, BlockHitResult block) {
		if (direct != null && enemy(p, direct)) {
			AbilityHelpers.hurt(p, direct, AbilityHelpers.fire(p), damage);
			ignite(direct, IGNITE_TICKS);
		}
		if (block != null) {
			placeFire(p, level, block.getBlockPos().relative(block.getDirection()), 100);
		}
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 24, 0.3, 0.3, 0.3, 0.08);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.02);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 3, 0.2, 0.2, 0.2, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	private static void startThrower(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (p.isUnderWater()) {
			ctx.actionBar("message.projecthero.pyro.doused");
			return;
		}
		set(p, "thrower", 1);
		set(p, "thrower_t", 0);
		MutationVisuals.play(p, "p08.thrower");
		AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.7f);
	}

	public static void stopThrower(ServerPlayer p) {
		if (res(p, "thrower") < 0.5f) {
			return;
		}
		set(p, "thrower", 0);
		MutationVisuals.stopIf(p, "p08.thrower");
	}

	private static void throwerTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "thrower") < 0.5f) {
			return;
		}
		if (!p.isAlive() || p.isUnderWater()) {
			stopThrower(p);
			return;
		}
		float t = res(p, "thrower_t") + 1;
		set(p, "thrower_t", Math.min(t, 100000));
		MutationVisuals.ensure(p, "p08.thrower");
		if (Math.round(t) % THROWER_INTERVAL == 1) {
			throwerBurst(p, level);
		}
		if (Math.round(t) % 4 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.4f);
		}
		// the stream (the model is drawn by the client; this is the spray of embers around it)
		Vec3 eye = p.getEyePosition().add(0, -0.3, 0);
		Vec3 look = p.getLookAngle();
		for (int i = 0; i < 3; i++) {
			double d = 1.0 + p.getRandom().nextDouble() * (THROWER_RANGE - 1.0);
			Vec3 pt = eye.add(look.scale(d));
			level.sendParticles(ParticleTypes.FLAME, pt.x, pt.y, pt.z, 1, 0.08 * d, 0.08 * d, 0.08 * d, 0.02);
		}
		addHeat(p, HEAT_THROWER_PER_SECOND / 20.0f);
	}

	/** One burst of the Flamethrower: 4 (+Heat) and burning to everything in the 8-block cone. Returns how many were hit. */
	public static int throwerBurst(ServerPlayer p, ServerLevel level) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		float damage = dmg(p, THROWER_DAMAGE);
		int hit = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(THROWER_RANGE * 0.5)), THROWER_RANGE * 0.5 + 1.5)) {
			Vec3 to = mid(e).subtract(eye);
			double d = to.length();
			if (d > THROWER_RANGE + e.getBbWidth() || d < 1.0e-3 || to.scale(1.0 / d).dot(look) < THROWER_CONE) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(p, e, AbilityHelpers.fire(p), damage)) {
				hit++;
			}
			ignite(e, IGNITE_TICKS);
		}
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, THROWER_RANGE);
		if (bhr.getType() == HitResult.Type.BLOCK) {
			placeFire(p, level, bhr.getBlockPos().relative(bhr.getDirection()), 80);
		}
		return hit;
	}

	// ================================================================ G: Fire Whip / Fire Lasso

	/** The lash: 12 (+Heat) to what it catches within 12 blocks, which is yanked in to you. +10 Heat. */
	public static LivingEntity fireWhip(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		LivingEntity t = AbilityHelpers.raycastEntity(p, WHIP_RANGE);
		if (t != null && !enemy(p, t)) {
			t = null;
		}
		double len = t != null ? Math.max(1.5, mid(t).distanceTo(p.getEyePosition())) : AbilityHelpers.aimPoint(p, WHIP_RANGE).distanceTo(p.getEyePosition());
		set(p, "whip_t", stamp(p));
		set(p, "whip_len", (float) len);
		set(p, "whip_ticks", WHIP_DRAW_TICKS);
		set(p, "whip_cd", WHIP_CD);
		MutationVisuals.play(p, "p08.whip");
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 0.8f, 1.6f);
		if (t != null) {
			AbilityHelpers.hurt(p, t, AbilityHelpers.fire(p), dmg(p, WHIP_DAMAGE));
			ignite(t, 4 * 20);
			if (noBoss(t)) {
				Vec3 pull = p.position().subtract(t.position());
				double d = pull.length();
				if (d > 1.0e-3) {
					Vec3 v = pull.scale(1.0 / d).scale(Math.min(1.8, 0.3 + d * 0.13)).add(0, 0.35, 0);
					t.setDeltaMovement(v);
					t.hurtMarked = true;
					t.hasImpulse = true;
				}
			}
			level.sendParticles(ParticleTypes.FLAME, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 16, 0.3, 0.4, 0.3, 0.05);
			level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 1.5f);
		}
		addHeat(p, HEAT_WHIP);
		return t;
	}

	private static void lasso(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, LASSO_RANGE);
		if (t == null || !enemy(p, t) || !AbilityHelpers.isValidGrabTarget(t, p) || !noBoss(t)) {
			ctx.actionBar("message.projecthero.pyro.no_target");
			return;
		}
		lassoTarget(p, t);
	}

	/** Binds {@code t} in a ring of fire for 3 s: it can't move and takes 3 (+Heat) a second. +20 Heat. */
	public static void lassoTarget(ServerPlayer p, LivingEntity t) {
		set(p, "lasso_dmg", dmg(p, LASSO_DAMAGE_PER_SECOND));
		set(p, "lasso_id", t.getId());
		set(p, "lasso_ticks", LASSO_TICKS);
		set(p, "lasso_cd", LASSO_CD);
		ExperimentalPowers.setMarker(p, Powers.byKey(KEY), "lasso_at", t.blockPosition());
		ignite(t, LASSO_TICKS + 20);
		if (t instanceof Mob m) {
			m.getNavigation().stop();
		}
		MutationVisuals.play(p, "p08.lasso");
		AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 0.7f, 1.3f);
		addHeat(p, HEAT_LASSO);
	}

	private static void lassoTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "lasso_ticks");
		if (left < 0.5f) {
			return;
		}
		Entity e = level.getEntity((int) res(p, "lasso_id"));
		if (!(e instanceof LivingEntity t) || !t.isAlive() || left <= 1.0f || t.distanceToSqr(p) > 24 * 24 || !p.isAlive()) {
			endLasso(p);
			return;
		}
		set(p, "lasso_ticks", left - 1);
		BlockPos pin = ExperimentalPowers.getMarker(p, Powers.byKey(KEY), "lasso_at");
		if (pin != null) {
			t.setPos(pin.getX() + 0.5, Math.min(t.getY(), pin.getY() + 0.5), pin.getZ() + 0.5);
		}
		t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
		t.hurtMarked = true;
		if (t instanceof Mob m) {
			m.getNavigation().stop();
		}
		int age = LASSO_TICKS - (int) left + 1;
		if (age % 20 == 0) {
			lassoBurn(p, t);
		}
		MutationVisuals.ensure(p, "p08.lasso");
	}

	/** One second of the lasso's burn. */
	public static void lassoBurn(ServerPlayer p, LivingEntity t) {
		float d = res(p, "lasso_dmg");
		AbilityHelpers.hurtBurst(p, t, AbilityHelpers.fire(p), d > 0 ? d : LASSO_DAMAGE_PER_SECOND);
		ignite(t, 40);
		AbilityHelpers.level(p).sendParticles(ParticleTypes.FLAME, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 10, 0.4, 0.3, 0.4, 0.02);
	}

	public static void endLasso(ServerPlayer p) {
		if (res(p, "lasso_ticks") < 0.5f && res(p, "lasso_id") < 0.5f) {
			return;
		}
		set(p, "lasso_ticks", 0);
		set(p, "lasso_id", 0);
		MutationVisuals.stopIf(p, "p08.lasso");
	}

	/** True while {@code e} is bound in any pyrokinetic's lasso. */
	public static boolean isLassoed(Entity e) {
		if (!(e.level() instanceof ServerLevel sl)) {
			return false;
		}
		for (ServerPlayer p : sl.players()) {
			if (owns(p) && res(p, "lasso_ticks") > 0.5f && (int) res(p, "lasso_id") == e.getId()) {
				return true;
			}
		}
		return false;
	}

	// ================================================================ X: Flame Dash / Jet Flight

	/** A 10-block burst along your aim, leaving a trail of fire. +10 Heat. */
	public static void flameDash(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x, Math.max(-0.5, Math.min(0.6, look.y)), look.z);
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
		Vec3 v = dir.scale(DASH_DISTANCE / DASH_TICKS);
		set(p, "dash", DASH_TICKS);
		set(p, "dash_vx", (float) v.x + V_SHIFT);
		set(p, "dash_vy", (float) v.y + V_SHIFT);
		set(p, "dash_vz", (float) v.z + V_SHIFT);
		set(p, "dash_cd", DASH_CD);
		AbilityHelpers.launchSelf(p, v);
		MutationVisuals.play(p, "p08.dash");
		AbilityHelpers.level(p).sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.9, p.getZ(), 30, 0.3, 0.5, 0.3, 0.12);
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 1.2f);
		addHeat(p, HEAT_DASH);
	}

	private static void dashTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "dash");
		if (left < 0.5f) {
			return;
		}
		set(p, "dash", left - 1);
		set(p, "nofall", 40);
		p.resetFallDistance();
		Vec3 v = new Vec3(res(p, "dash_vx") - V_SHIFT, res(p, "dash_vy") - V_SHIFT, res(p, "dash_vz") - V_SHIFT);
		if (left > 1.5f && !p.horizontalCollision) {
			AbilityHelpers.launchSelf(p, v);
		} else {
			AbilityHelpers.launchSelf(p, v.scale(0.25)); // the burst ends: drop most of the speed
		}
		trailFire(p, level);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(0, 0.9, 0), 1.8)) {
			ignite(e, IGNITE_TICKS);
		}
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.8, p.getZ(), 10, 0.25, 0.5, 0.25, 0.02);
	}

	/** Fire on the ground under and just behind the dash. */
	private static void trailFire(ServerPlayer p, ServerLevel level) {
		BlockPos feet = p.blockPosition();
		for (int dy = 0; dy >= -2; dy--) {
			BlockPos at = feet.offset(0, dy, 0);
			if (level.getBlockState(at).isAir() && !level.getBlockState(at.below()).isAir()) {
				placeFire(p, level, at, 100);
				break;
			}
		}
	}

	private static void startJet(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (p.isInWater()) {
			ctx.actionBar("message.projecthero.pyro.doused");
			return;
		}
		if (p.getAbilities().instabuild || com.projecthero.mod.power.ThorPowers.isFlying(p)) {
			return;
		}
		Power power = Powers.byKey(KEY);
		set(p, "jet", 1);
		// HeroFlight leaves a flight alone while a timed self-flight resource is up: Jet Flight is its own flight
		ExperimentalPowers.setResource(p, power, "flame_fly_ticks", 1, 1);
		com.projecthero.mod.flight.FlightLanding.started(p, JET_LANDING);
		p.setAttached(ModAttachments.HERO_FLYING, true); // the shared directional flight steers it
		p.getAbilities().mayfly = true;
		p.getAbilities().flying = true;
		p.getAbilities().setFlyingSpeed(JET_FLY_SPEED);
		p.onUpdateAbilities();
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, 0.6, p.getDeltaMovement().z));
		}
		MutationVisuals.play(p, "p08.jet");
		AbilityHelpers.level(p).sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.1, p.getZ(), 40, 0.4, 0.1, 0.4, 0.1);
		AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 0.8f, 0.6f);
	}

	public static void stopJet(ServerPlayer p) {
		if (res(p, "jet") < 0.5f) {
			return;
		}
		Power power = Powers.byKey(KEY);
		set(p, "jet", 0);
		ExperimentalPowers.setResource(p, power, "flame_fly_ticks", 0, 1);
		com.projecthero.mod.flight.FlightLanding.ended(p, JET_LANDING);
		p.setAttached(ModAttachments.HERO_FLYING, false);
		if (!p.getAbilities().instabuild && !p.isSpectator()) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
		}
		p.getAbilities().setFlyingSpeed(0.05f);
		p.onUpdateAbilities();
		set(p, "nofall", 60);
		p.resetFallDistance();
		MutationVisuals.stopIf(p, "p08.jet");
		AbilityHelpers.sound(p, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.4f);
	}

	private static void jetTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "jet") < 0.5f) {
			return;
		}
		if (!p.isAlive() || p.isInWater() || p.isPassenger() || p.getAbilities().instabuild
				|| com.projecthero.mod.flight.FlightLanding.landed(p, JET_LANDING)) {
			if (p.isInWater()) {
				level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
			}
			stopJet(p);
			return;
		}
		Power power = Powers.byKey(KEY);
		ExperimentalPowers.setResource(p, power, "flame_fly_ticks", 1, 1);
		p.setAttached(ModAttachments.HERO_FLYING, true);
		if (!p.getAbilities().flying || !p.getAbilities().mayfly) {
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
		p.resetFallDistance();
		MutationVisuals.ensure(p, "p08.jet");
		if (p.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() - 0.1, p.getZ(), 3, 0.15, 0.05, 0.15, 0.03);
			level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() - 0.3, p.getZ(), 1, 0.1, 0.05, 0.1, 0.01);
		}
		if (p.tickCount % 8 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 0.6f, 0.7f);
		}
		addHeat(p, HEAT_JET_PER_SECOND / 20.0f);
	}

	// ================================================================ Z: Meteor / Inferno

	private static void startMeteor(ServerPlayer p) {
		set(p, "meteor", 1);
		set(p, "meteor_start", p.level().getGameTime());
		set(p, "meteor_charge", 0);
		MutationVisuals.play(p, "p08.meteor_charge");
		AbilityHelpers.sound(p, SoundEvents.BLAZE_AMBIENT, 0.9f, 0.6f);
	}

	private static float meteorFraction(ServerPlayer p) {
		float start = res(p, "meteor_start");
		if (start <= 0.5f) {
			return 0.0f;
		}
		return (float) Math.min(1.0, (p.level().getGameTime() - (long) start) / (double) METEOR_CHARGE_TICKS);
	}

	private static void meteorTick(ServerPlayer p) {
		if (res(p, "meteor") < 0.5f) {
			return;
		}
		if (!p.isAlive()) {
			cancelMeteor(p);
			return;
		}
		float f = meteorFraction(p);
		set(p, "meteor_charge", f * 100.0f);
		MutationVisuals.ensure(p, "p08.meteor_charge");
		long held = p.level().getGameTime() - (long) res(p, "meteor_start");
		Vec3 over = p.position().add(0, p.getBbHeight() + 0.5 + f * 0.5, 0);
		ServerLevel level = AbilityHelpers.level(p);
		if (held % 3 == 0) {
			level.sendParticles(ParticleTypes.FLAME, over.x, over.y, over.z, 2 + (int) (f * 6), 0.2 + f * 0.5, 0.2 + f * 0.5,
					0.2 + f * 0.5, 0.01);
		}
		if (held % 12 == 0) {
			AbilityHelpers.sound(p, SoundEvents.FIRE_AMBIENT, 1.0f, 0.5f + f);
		}
		if (held >= METEOR_AUTO_THROW_TICKS) {
			releaseMeteor(p); // v0.16.6: a toggle now -- it throws itself at full charge after 5 s
		}
	}

	private static void cancelMeteor(ServerPlayer p) {
		if (res(p, "meteor") < 0.5f) {
			return;
		}
		set(p, "meteor", 0);
		set(p, "meteor_start", 0);
		set(p, "meteor_charge", 0);
		MutationVisuals.stopIf(p, "p08.meteor_charge");
	}

	/** Lets the Meteor fly with the charge built so far. */
	public static PyroBoltEntity releaseMeteor(ServerPlayer p) {
		if (res(p, "meteor") < 0.5f) {
			return null;
		}
		float f = meteorFraction(p);
		cancelMeteor(p);
		return throwMeteor(p, f);
	}

	/** A Meteor charged {@code frac} (0..1): 16-30 (+Heat) in a 5-block blast, setting fires. +25 Heat. */
	public static PyroBoltEntity throwMeteor(ServerPlayer p, float frac) {
		float damage = dmg(p, meteorDamage(frac));
		PyroBoltEntity b = PyroBoltEntity.launch(p, p.getLookAngle(), 1.15, damage, true, 0.7f + 0.7f * frac);
		set(p, "meteor_cd", METEOR_CD);
		MutationVisuals.play(p, "p08.meteor_throw");
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.6f, 0.4f);
		AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.4f, 0.4f);
		addHeat(p, HEAT_METEOR);
		return b;
	}

	/** The Meteor bursts: {@code damage} to everything within 5 blocks, burning, knockback, fires. Returns how many were hit. */
	public static int meteorImpact(ServerPlayer p, ServerLevel level, Vec3 at, float damage, float size, BlockHitResult block) {
		return meteorImpact(p, level, at, damage, size, block, METEOR_RADIUS);
	}

	/** v0.16.6: any meteor blast of {@code radius} (a Meteor Shower's are 2.5). */
	public static int meteorImpact(ServerPlayer p, ServerLevel level, Vec3 at, float damage, float size, BlockHitResult block,
			double radius) {
		int hit = 0;
		boolean small = radius < METEOR_RADIUS - 0.5;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, radius)) {
			if (AbilityHelpers.hurtBurst(p, e, AbilityHelpers.fire(p), damage)) {
				hit++;
			}
			ignite(e, 8 * 20);
			AbilityHelpers.knockbackFrom(e, at, small ? 0.6 : 1.3);
			AbilityHelpers.push(e, new Vec3(0, small ? 0.2 : 0.35, 0));
		}
		PyroFxEntity.spawn(level, p, PyroFxEntity.BLAST, at, 0.0f, (float) radius, small ? 14 : 18);
		BlockPos c = BlockPos.containing(at);
		int span = small ? 2 : 3;
		for (int i = 0; i < (small ? 5 : 14); i++) {
			BlockPos b = c.offset(level.random.nextInt(span * 2 + 1) - span, level.random.nextInt(3) - 1, level.random.nextInt(span * 2 + 1) - span);
			for (int dy = 1; dy >= -2; dy--) {
				BlockPos q = b.offset(0, dy, 0);
				if (level.getBlockState(q).isAir() && !level.getBlockState(q.below()).isAir()) {
					placeFire(p, level, q, 200);
					break;
				}
			}
		}
		double k = small ? 0.5 : 1.0;
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, small ? 1 : 3, 1.0 * k, 0.4, 1.0 * k, 0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, small ? 40 : 120, 2.0 * k, 1.0 * k, 2.0 * k, 0.15);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, small ? 8 : 30, 1.5 * k, 0.6, 1.5 * k, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, small ? 1.2f : 2.0f, small ? 1.0f : 0.7f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 0.4f);
		return hit;
	}

	private static void inferno(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "heat") < INFERNO_MIN_HEAT) {
			ctx.actionBar("message.projecthero.pyro.too_cold");
			return;
		}
		infernoNova(p);
	}

	/** Dumps all Heat in a nova: up to 30 (at full Heat) to everything within 8 blocks; Heat to 0. Returns how many were hit. */
	public static int infernoNova(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		float heat = res(p, "heat");
		float damage = infernoDamage(heat);
		Vec3 c = p.position();
		int hit = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(0, 1, 0), INFERNO_RADIUS)) {
			if (AbilityHelpers.hurtBurst(p, e, AbilityHelpers.fire(p), damage)) {
				hit++;
			}
			ignite(e, 6 * 20);
			AbilityHelpers.knockbackFrom(e, c, 1.0 + heat / MAX_HEAT);
			AbilityHelpers.push(e, new Vec3(0, 0.3, 0));
		}
		BatchA.set(p, KEY, "heat", 0, MAX_HEAT);
		PyroFxEntity.spawn(level, p, PyroFxEntity.NOVA, c.add(0, 0.1, 0), p.getYRot(), (float) INFERNO_RADIUS, 18);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 1, c.z, 100, 1.5, 0.8, 1.5, 0.25);
		MutationVisuals.play(p, "p08.nova");
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.6f, 0.4f);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.7f, 1.4f);
		return hit;
	}

	// ================================================================ V: Flame Wall / Flame Body

	/** An 8-wide wall of fire 3 blocks ahead, across your aim, for 8 s. +15 Heat. */
	public static PyroFxEntity flameWall(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		Vec3 flat = p.getLookAngle().multiply(1, 0, 1);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 at = p.position().add(flat.scale(3.0));
		// settle onto the ground (up to 3 down, 2 up)
		BlockPos b = BlockPos.containing(at);
		double y = at.y;
		for (int dy = 2; dy >= -3; dy--) {
			BlockPos q = b.offset(0, dy, 0);
			if (level.getBlockState(q).getCollisionShape(level, q).isEmpty() && !level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty()) {
				y = q.getY();
				break;
			}
		}
		float yaw = (float) (Math.atan2(flat.z, flat.x) * 180.0 / Math.PI) - 90.0f;
		PyroFxEntity wall = PyroFxEntity.spawn(level, p, PyroFxEntity.WALL, new Vec3(at.x, y, at.z), yaw, (float) WALL_WIDTH, WALL_TICKS);
		wall.setWallDamage(dmg(p, WALL_DAMAGE));
		set(p, "wall_cd", WALL_CD);
		MutationVisuals.play(p, "p08.wall");
		level.sendParticles(ParticleTypes.FLAME, at.x, y + 0.5, at.z, 40, 2.5, 0.4, 2.5, 0.05);
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.2f, 0.6f);
		level.playSound(null, at.x, y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2f, 0.5f);
		addHeat(p, HEAT_WALL);
		return wall;
	}

	/** The wall burns everything standing in it (every half second). Returns how many were hit. */
	public static int wallBurn(ServerPlayer p, ServerLevel level, PyroFxEntity wall, float damage) {
		int hit = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, wall.wallBox(), e -> enemy(p, e))) {
			if (Math.abs(wall.acrossWall(e.position())) > 0.9 + e.getBbWidth() * 0.5) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(p, e, AbilityHelpers.fire(p), damage > 0 ? damage : WALL_DAMAGE)) {
				hit++;
			}
			ignite(e, IGNITE_TICKS);
		}
		return hit;
	}

	public static void setFlameBody(ServerPlayer p, boolean on) {
		boolean was = res(p, "body") > 0.5f;
		if (on == was) {
			return;
		}
		set(p, "body", on ? 1 : 0);
		if (on) {
			MutationVisuals.play(p, "p08.ignite");
			AbilityHelpers.level(p).sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.4, 0.9, 0.4, 0.05);
			AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.6f);
			AbilityHelpers.sound(p, SoundEvents.BLAZE_AMBIENT, 0.8f, 1.0f);
			p.displayClientMessage(Component.translatable("message.projecthero.pyro.body_on"), true);
		} else {
			AbilityHelpers.sound(p, SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.2f);
		}
	}

	private static void bodyTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "body") < 0.5f) {
			return;
		}
		if (!p.isAlive()) {
			setFlameBody(p, false);
			return;
		}
		if (p.tickCount % 30 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.CAMPFIRE_CRACKLE, SoundSource.PLAYERS, 0.6f, 1.0f);
		}
		if (p.tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.3, 0.7, 0.3, 0.02);
		}
		addHeat(p, HEAT_BODY_PER_SECOND / 20.0f);
	}

	/** The attacker of a melee hit (a mob or player striking in person), else null. */
	private static Entity meleeAttacker(ServerPlayer p, DamageSource source) {
		Entity atk = source.getEntity();
		if (atk instanceof LivingEntity && source.getDirectEntity() == atk && !source.is(DamageTypeTags.IS_PROJECTILE)
				&& atk.distanceToSqr(p) < 64.0) {
			return atk;
		}
		return null;
	}

	/**
	 * The fire body's damage rule, read by {@link com.projecthero.mod.hero.power.HeroDamageRules}: a negative result means
	 * "immune" (fire and lava; a fall straight after a dash or the jets), otherwise a multiplier -- Flame Body takes 30%
	 * less from melee.
	 */
	public static float damageFactor(ServerPlayer p, DamageSource source) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			p.clearFire();
			return -1.0f;
		}
		if (source.is(DamageTypeTags.IS_FALL) && (res(p, "nofall") > 0.5f || res(p, "jet") > 0.5f || res(p, "dash") > 0.5f
				|| res(p, "phoenix") > 0.5f)) {
			p.resetFallDistance();
			return -1.0f;
		}
		if (flameBodyActive(p) && meleeAttacker(p, source) != null) {
			return BODY_MELEE_FACTOR;
		}
		return 1.0f;
	}

	// ================================================================ C: Heat Wave / Cauterize

	/** A cone of heat: knockback, melts snow and ice, smelts dropped items. +10 Heat. Returns how many were pushed. */
	public static int heatWave(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		int pushed = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(WAVE_RANGE * 0.5)), WAVE_RANGE * 0.5 + 1.5)) {
			if (!inCone(eye, look, mid(e), WAVE_RANGE + e.getBbWidth())) {
				continue;
			}
			Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0e-4 ? look.multiply(1, 0, 1) : away.normalize();
			double k = 1.0 - Math.min(1.0, e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			e.setDeltaMovement(away.scale(1.8 * Math.max(0.25, k)).add(0, 0.45, 0));
			e.hurtMarked = true;
			e.hasImpulse = true;
			pushed++;
		}
		meltCone(p, level, eye, look);
		smeltCone(p, level, eye, look);
		set(p, "wave_cd", WAVE_CD);
		PyroFxEntity.spawn(level, p, PyroFxEntity.WAVE, eye.add(0, -0.4, 0), p.getYRot(), p.getXRot(), (float) WAVE_RANGE, 12);
		MutationVisuals.play(p, "p08.wave");
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 0.8f, 0.5f);
		addHeat(p, HEAT_WAVE);
		return pushed;
	}

	private static boolean inCone(Vec3 eye, Vec3 look, Vec3 at, double range) {
		Vec3 to = at.subtract(eye);
		double d = to.length();
		return d <= range && (d < 1.0 || to.scale(1.0 / d).dot(look) >= WAVE_CONE);
	}

	/** Snow and ice in the cone melt (only where the player may build). Returns how many blocks changed. */
	public static int meltCone(ServerPlayer p, ServerLevel level, Vec3 eye, Vec3 look) {
		int changed = 0;
		BlockPos c = BlockPos.containing(eye);
		int r = (int) Math.ceil(WAVE_RANGE);
		for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -3, -r), c.offset(r, 2, r))) {
			BlockState s = level.getBlockState(pos);
			BlockState to = melted(level, s);
			if (to == null || !inCone(eye, look, Vec3.atCenterOf(pos), WAVE_RANGE) || !mayBurn(p, level, pos)) {
				continue;
			}
			level.setBlockAndUpdate(pos, to);
			level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
			changed++;
		}
		return changed;
	}

	private static BlockState melted(ServerLevel level, BlockState s) {
		if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) {
			return Blocks.AIR.defaultBlockState();
		}
		if (s.is(Blocks.ICE) || s.is(Blocks.FROSTED_ICE)) {
			return level.dimensionType().ultraWarm() ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState();
		}
		if (s.is(Blocks.PACKED_ICE)) {
			return Blocks.ICE.defaultBlockState();
		}
		return null;
	}

	/** Dropped items in the cone are smelted, the whole stack at once. Returns how many stacks changed. */
	public static int smeltCone(ServerPlayer p, ServerLevel level, Vec3 eye, Vec3 look) {
		int changed = 0;
		for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(WAVE_RANGE), ItemEntity::isAlive)) {
			if (!inCone(eye, look, it.position(), WAVE_RANGE)) {
				continue;
			}
			ItemStack stack = it.getItem();
			Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
					new SingleRecipeInput(stack), level);
			if (recipe.isEmpty()) {
				continue;
			}
			ItemStack out = recipe.get().value().getResultItem(level.registryAccess());
			if (out.isEmpty()) {
				continue;
			}
			it.setItem(out.copyWithCount(Math.min(out.getMaxStackSize(), stack.getCount() * out.getCount())));
			level.sendParticles(ParticleTypes.FLAME, it.getX(), it.getY() + 0.2, it.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
			level.sendParticles(ParticleTypes.SMOKE, it.getX(), it.getY() + 0.3, it.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
			changed++;
		}
		return changed;
	}

	private static void cauterize(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "heat") < CAUTERIZE_HEAT) {
			ctx.actionBar("message.projecthero.pyro.need_heat");
			return;
		}
		cauterizeNow(p);
	}

	/** Burns 40 Heat to heal 8 hearts. 20 s cooldown. */
	public static void cauterizeNow(ServerPlayer p) {
		BatchA.set(p, KEY, "heat", Math.max(0.0f, res(p, "heat") - CAUTERIZE_HEAT), MAX_HEAT);
		p.heal(CAUTERIZE_HEAL);
		set(p, "cauterize_cd", CAUTERIZE_CD);
		set(p, "cauterize_fx", 20);
		p.removeEffect(MobEffects.POISON);
		p.removeEffect(MobEffects.WITHER);
		MutationVisuals.play(p, "p08.cauterize");
		ServerLevel level = AbilityHelpers.level(p);
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.1, p.getZ(), 30, 0.25, 0.4, 0.25, 0.03);
		level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 10, 0.2, 0.3, 0.2, 0.02);
		AbilityHelpers.sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.8f);
		AbilityHelpers.sound(p, SoundEvents.BLAZE_HURT, 0.6f, 1.4f);
	}

	// ================================================================ v0.16.6 hold moves

	// ---------------- hold R: Blue Flame

	/** The wind-up: a blue-white ball swells in the drawn-back hand for half a second, then flies. */
	private static void startBlueFlame(ServerPlayer p) {
		if (p.isUnderWater()) {
			p.displayClientMessage(Component.translatable("message.projecthero.pyro.doused"), true);
			return;
		}
		set(p, "blue_charge", BLUE_WINDUP);
		set(p, "blue_cd", BLUE_CD);
		MutationVisuals.play(p, "p08.blue_charge");
		AbilityHelpers.sound(p, SoundEvents.SOUL_ESCAPE.value(), 1.2f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 0.7f, 1.6f);
	}

	private static void blueTick(ServerPlayer p) {
		float left = res(p, "blue_charge");
		if (left < 0.5f) {
			return;
		}
		if (!p.isAlive()) {
			set(p, "blue_charge", 0);
			return;
		}
		set(p, "blue_charge", left - 1);
		MutationVisuals.ensure(p, "p08.blue_charge");
		Vec3 h = AbilityHelpers.handPosition(p);
		AbilityHelpers.level(p).sendParticles(ParticleTypes.SOUL_FIRE_FLAME, h.x, h.y, h.z, 3, 0.12, 0.12, 0.12, 0.02);
		if (left <= 1.5f) {
			blueFlame(p);
		}
	}

	/** The Blue Flame flies: 24 (+Heat) on a direct hit -- not fire damage, so Fire Resistance doesn't stop it -- and 8 s of burning. +20 Heat. */
	public static PyroBoltEntity blueFlame(ServerPlayer p) {
		set(p, "blue_charge", 0);
		float damage = dmg(p, BLUE_DAMAGE);
		PyroBoltEntity b = PyroBoltEntity.launch(p, p.getLookAngle(), BLUE_SPEED, damage, false, 0.55f);
		b.setBlue(true);
		set(p, "blue_cd", BLUE_CD);
		MutationVisuals.play(p, "p08.throw");
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.2f, 1.5f);
		AbilityHelpers.sound(p, SoundEvents.SOUL_ESCAPE.value(), 1.4f, 1.2f);
		addHeat(p, HEAT_BLUE);
		return b;
	}

	/** A Blue Flame lands. */
	public static void blueImpact(ServerPlayer p, ServerLevel level, Vec3 at, LivingEntity direct, float damage, BlockHitResult block) {
		if (direct != null && enemy(p, direct)) {
			AbilityHelpers.hurtBurst(p, direct, AbilityHelpers.kinetic(p), damage);
			ignite(direct, BLUE_BURN_TICKS);
		}
		if (block != null) {
			placeFire(p, level, block.getBlockPos().relative(block.getDirection()), 100);
		}
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 34, 0.35, 0.35, 0.35, 0.09);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2f, 1.4f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.6f, 0.8f);
	}

	// ---------------- hold G: Flame Serpent

	/** A serpent of fire coils off your arm and hunts the nearest enemy within 25 blocks: 18 (+Heat) and 8 s of burning. +25 Heat. */
	public static PyroSerpentEntity flameSerpent(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		PyroSerpentEntity s = PyroSerpentEntity.spawn(level, p, dmg(p, SERPENT_DAMAGE));
		set(p, "serpent_cd", SERPENT_CD);
		set(p, "serpent_fx", 14);
		MutationVisuals.play(p, "p08.serpent");
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.2f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.ENDER_DRAGON_GROWL, 0.35f, 1.7f);
		addHeat(p, HEAT_SERPENT);
		return s;
	}

	/** The serpent's quarry: what you aim at (25 blocks), else the nearest threat within 25 blocks of you. */
	public static LivingEntity serpentQuarry(ServerPlayer p) {
		LivingEntity aimed = AbilityHelpers.raycastEntity(p, SERPENT_RANGE);
		if (aimed != null && enemy(p, aimed)) {
			return aimed;
		}
		LivingEntity best = null;
		double bd = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), SERPENT_RANGE)) {
			if (!enemy(p, e)) {
				continue;
			}
			double d = e.distanceToSqr(p);
			if (d < bd) {
				bd = d;
				best = e;
			}
		}
		return best;
	}

	/** The serpent strikes {@code t}. */
	public static boolean serpentStrike(ServerPlayer p, ServerLevel level, LivingEntity t, float damage) {
		boolean dealt = AbilityHelpers.hurtBurst(p, t, AbilityHelpers.fire(p), damage);
		ignite(t, SERPENT_BURN_TICKS);
		if (noBoss(t)) {
			AbilityHelpers.push(t, new Vec3(0, 0.3, 0));
		}
		PyroFxEntity.spawn(level, p, PyroFxEntity.BLAST, t.position(), 0.0f, 1.6f, 12);
		level.sendParticles(ParticleTypes.FLAME, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 40, 0.4, 0.5, 0.4, 0.12);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4f, 0.6f);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BLAZE_HURT, SoundSource.PLAYERS, 1.0f, 0.5f);
		return dealt;
	}

	// ---------------- hold X: Phoenix Dive

	private static void startPhoenix(ServerPlayer p) {
		stopJet(p);
		set(p, "dash", 0);
		set(p, "phoenix", 1);
		set(p, "phoenix_t", 0);
		set(p, "phoenix_dmg", dmg(p, PHOENIX_DAMAGE));
		set(p, "phoenix_cd", PHOENIX_CD);
		set(p, "nofall", 80);
		p.resetFallDistance();
		AbilityHelpers.launchSelf(p, new Vec3(0, 0.8, 0));
		MutationVisuals.play(p, "p08.phoenix_rise");
		AbilityHelpers.level(p).sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.3, p.getZ(), 50, 0.6, 0.2, 0.6, 0.12);
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.3f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_FLAP, 1.6f, 0.6f);
		addHeat(p, HEAT_PHOENIX);
	}

	/** True while the Phoenix Dive runs (rising or diving). */
	public static boolean phoenix(Player p) {
		return owns(p) && resAny(p, "phoenix") > 0.5f;
	}

	public static boolean phoenixDiving(Player p) {
		return owns(p) && resAny(p, "phoenix") > 1.5f;
	}

	private static void phoenixTick(ServerPlayer p, ServerLevel level) {
		float phase = res(p, "phoenix");
		if (phase < 0.5f) {
			return;
		}
		if (!p.isAlive() || p.isPassenger()) {
			endHoldMoves(p);
			return;
		}
		float t = res(p, "phoenix_t") + 1;
		set(p, "phoenix_t", t);
		set(p, "nofall", 80);
		p.resetFallDistance();
		if (t <= PHOENIX_RISE_TICKS) {
			MutationVisuals.ensure(p, "p08.phoenix_rise");
			double up = 0.75 * (1.0 - (t - 1) / PHOENIX_RISE_TICKS) + 0.08; // ~4-5 blocks up
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x * 0.5, up, p.getDeltaMovement().z * 0.5));
			if (p.tickCount % 2 == 0) {
				level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.5, 0.1, 0.5, 0.04);
			}
			return;
		}
		if (phase < 1.5f) {
			// the turn: down along the aim (always at least a little downward)
			Vec3 look = p.getLookAngle();
			Vec3 dir = new Vec3(look.x, Math.min(-0.35, look.y), look.z).normalize();
			set(p, "phoenix", 2);
			set(p, "phoenix_vx", (float) dir.x + V_SHIFT);
			set(p, "phoenix_vy", (float) dir.y + V_SHIFT);
			set(p, "phoenix_vz", (float) dir.z + V_SHIFT);
			MutationVisuals.play(p, "p08.phoenix_dive");
			AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.4f, 0.7f);
			AbilityHelpers.sound(p, SoundEvents.PHANTOM_SWOOP, 1.6f, 0.8f);
		}
		MutationVisuals.ensure(p, "p08.phoenix_dive");
		boolean landed = t > PHOENIX_RISE_TICKS + 1 && (p.onGround() || p.horizontalCollision || p.verticalCollision || p.isInWater());
		if (landed || t > PHOENIX_RISE_TICKS + PHOENIX_DIVE_MAX_TICKS) {
			phoenixImpact(p, level, p.position());
			return;
		}
		Vec3 dir = new Vec3(res(p, "phoenix_vx") - V_SHIFT, res(p, "phoenix_vy") - V_SHIFT, res(p, "phoenix_vz") - V_SHIFT);
		AbilityHelpers.launchSelf(p, dir.scale(PHOENIX_DIVE_SPEED));
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.8, p.getZ(), 10, 0.4, 0.4, 0.4, 0.03);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(0, 0.9, 0), 2.0)) {
			ignite(e, IGNITE_TICKS);
		}
	}

	/** The Phoenix lands: 20 (+Heat) to everything within 4 blocks (8 across), burning, knockback, and the ground on fire. */
	public static int phoenixImpact(ServerPlayer p, ServerLevel level, Vec3 at) {
		float damage = res(p, "phoenix_dmg");
		if (damage <= 0.0f) {
			damage = dmg(p, PHOENIX_DAMAGE);
		}
		endHoldMoves(p);
		int hit = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, at.add(0, 0.5, 0), PHOENIX_RADIUS)) {
			if (AbilityHelpers.hurtBurst(p, e, AbilityHelpers.fire(p), damage)) {
				hit++;
			}
			ignite(e, 8 * 20);
			if (noBoss(e)) {
				AbilityHelpers.knockbackFrom(e, at, 1.2);
				AbilityHelpers.push(e, new Vec3(0, 0.4, 0));
			}
		}
		PyroFxEntity.spawn(level, p, PyroFxEntity.NOVA, at.add(0, 0.1, 0), p.getYRot(), (float) PHOENIX_RADIUS, 18);
		PyroFxEntity.spawn(level, p, PyroFxEntity.BLAST, at, 0.0f, (float) PHOENIX_RADIUS, 16);
		// the ground burns
		BlockPos c = BlockPos.containing(at);
		for (int i = 0; i < 18; i++) {
			BlockPos b = c.offset(level.random.nextInt(7) - 3, 0, level.random.nextInt(7) - 3);
			for (int dy = 1; dy >= -2; dy--) {
				BlockPos q = b.offset(0, dy, 0);
				if (level.getBlockState(q).isAir() && !level.getBlockState(q.below()).isAir()) {
					placeFire(p, level, q, 160);
					break;
				}
			}
		}
		AbilityHelpers.launchSelf(p, new Vec3(0, 0.05, 0));
		MutationVisuals.play(p, "p08.nova");
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 3, 1.2, 0.3, 1.2, 0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 120, 2.0, 0.6, 2.0, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6f, 0.8f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 0.4f);
		return hit;
	}

	// ---------------- hold Z: Meteor Shower

	/** The ground under {@code at} (up to 30 blocks down), or {@code at} itself. */
	private static Vec3 groundUnder(ServerLevel level, Vec3 at) {
		BlockPos b = BlockPos.containing(at);
		for (int dy = 0; dy < 30; dy++) {
			BlockPos q = b.below(dy);
			if (!level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty()) {
				return new Vec3(at.x, q.getY(), at.z);
			}
		}
		return at;
	}

	/** Six smaller meteors rain on the aimed spot (up to 30 away): 10 (+Heat) each in 2.5-block blasts, setting fires. +35 Heat. */
	public static java.util.List<PyroBoltEntity> meteorShower(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		LivingEntity aimed = AbilityHelpers.raycastEntity(p, SHOWER_RANGE);
		Vec3 target = aimed != null && enemy(p, aimed) ? aimed.position() : groundUnder(level, AbilityHelpers.aimPoint(p, SHOWER_RANGE));
		float damage = dmg(p, SHOWER_DAMAGE);
		Vec3 flat = p.getLookAngle().multiply(1, 0, 1);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		java.util.List<PyroBoltEntity> out = new java.util.ArrayList<>();
		double spin = level.random.nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < SHOWER_COUNT; i++) {
			double a = spin + i * 2.39996;
			double r = i == 0 ? 0.0 : SHOWER_SPREAD * Math.sqrt(i / (double) (SHOWER_COUNT - 1));
			Vec3 spot = target.add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
			// they come down out of the sky from behind you, one after another
			Vec3 start = spot.subtract(flat.scale(7.0)).add(0, 20.0 + i * 4.5, 0);
			Vec3 dir = spot.subtract(start).normalize();
			out.add(PyroBoltEntity.launchFrom(p, start, dir, 1.3, damage, 0.55f, SHOWER_BLAST));
		}
		set(p, "shower_cd", SHOWER_CD);
		MutationVisuals.play(p, "p08.shower");
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.6f, 0.4f);
		level.playSound(null, target.x, target.y, target.z, SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 2.0f, 0.4f);
		addHeat(p, HEAT_SHOWER);
		return out;
	}

	// ---------------- hold V: Ring of Fire

	/** A circle of flame 6 blocks round you for 10 s: whatever crosses it burns (4 (+Heat) every half second); you don't. +20 Heat. */
	public static PyroFxEntity ringOfFire(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		Vec3 at = p.position();
		PyroFxEntity ring = PyroFxEntity.spawn(level, p, PyroFxEntity.RING, at, p.getYRot(), (float) RING_RADIUS, RING_TICKS);
		ring.setWallDamage(dmg(p, RING_DAMAGE));
		set(p, "ring_cd", RING_CD);
		MutationVisuals.play(p, "p08.ring");
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 80, RING_RADIUS * 0.6, 0.2, RING_RADIUS * 0.6, 0.05);
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.4f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.4f, 0.4f);
		addHeat(p, HEAT_RING);
		return ring;
	}

	/** The ring burns everything standing in its flames. Returns how many were hit. */
	public static int ringBurn(ServerPlayer p, ServerLevel level, PyroFxEntity ring, float damage) {
		int hit = 0;
		double r = ring.size();
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(ring.getX() - r - 1.5, ring.getY() - 1.0, ring.getZ() - r - 1.5,
				ring.getX() + r + 1.5, ring.getY() + 3.0, ring.getZ() + r + 1.5);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> enemy(p, e))) {
			double dx = e.getX() - ring.getX();
			double dz = e.getZ() - ring.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			if (Math.abs(d - r) > 0.9 + e.getBbWidth() * 0.5) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(p, e, AbilityHelpers.fire(p), damage > 0 ? damage : RING_DAMAGE)) {
				hit++;
			}
			ignite(e, IGNITE_TICKS);
		}
		return hit;
	}

	// ---------------- hold C: Solar Flare

	/** A sun bursts out of you: everything hostile within 12 blocks is ignited and blinded for 4 s (bosses only burn). +15 Heat. */
	public static int solarFlare(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		Vec3 c = p.position().add(0, 1.2, 0);
		java.util.Set<LivingEntity> caught = new java.util.LinkedHashSet<>(AbilityHelpers.hostilesAround(p, c, FLARE_RADIUS));
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, FLARE_RADIUS)) {
			if (e instanceof Player) {
				caught.add(e); // a PvP foe is caught in the flash too
			}
		}
		int hit = 0;
		for (LivingEntity e : caught) {
			if (!enemy(p, e)) {
				continue;
			}
			ignite(e, FLARE_IGNITE_TICKS);
			if (noBoss(e)) {
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, FLARE_BLIND_TICKS, 0);
				if (e instanceof Mob m) {
					m.setTarget(null); // dazzled: it loses you
					m.getNavigation().stop();
				}
			}
			hit++;
		}
		PyroFxEntity.spawn(level, p, PyroFxEntity.FLARE, c, p.getYRot(), (float) FLARE_RADIUS, 24);
		set(p, "flare_cd", FLARE_CD);
		set(p, "flare_fx", 12);
		MutationVisuals.play(p, "p08.flare");
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 90, 1.2, 1.2, 1.2, 0.35);
		AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 1.6f, 1.8f);
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.4f, 0.4f);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.6f);
		addHeat(p, HEAT_FLARE);
		return hit;
	}

	// ================================================================ passives

	private static void registerPassives() {
		PowerPassives.register(KEY, (player, owned) -> {
			if (owned) {
				PowerToggles.effect(player, MobEffects.FIRE_RESISTANCE, 0, false);
				return;
			}
			PowerToggles.clearEffect(player, MobEffects.FIRE_RESISTANCE);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, LEGACY_BODY_ATK);
			Power power = Powers.byKey(KEY);
			if (power != null && ExperimentalPowers.owns(player, power)) {
				// still owned while forget() runs its teardown: drop the transient states too
				endAll(player);
				set(player, "lock", 0);
			} else if (player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)
					&& !player.getAbilities().instabuild && power != null
					&& ExperimentalPowers.getResource(player, power, "jet") > 0.5f) {
				player.setAttached(ModAttachments.HERO_FLYING, false);
				player.getAbilities().mayfly = false;
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
			}
			MutationVisuals.stop(player);
		});
		PowerPassives.registerTick(KEY, player -> {
			PowerToggles.effect(player, MobEffects.FIRE_RESISTANCE, 0, false);
			// a pyrokinetic never burns: snuff any ignition the tick it happens
			if (player.getRemainingFireTicks() > 0) {
				player.clearFire();
			}
		});
	}

	// ================================================================ synced visuals + HUD (called from RevampBatchB)

	public static void registerVisuals() {
		MutationVisuals.registerValue("p08.heat", p -> Math.round(heat(p) / MAX_HEAT * 20.0) / 20.0);
		MutationVisuals.registerFlag("p08.glow", p -> heat(p) >= 20.0f);
		MutationVisuals.registerFlag("p08.flame_body", PyrokinesisHandlers::flameBodyActive);
		MutationVisuals.registerFlag("p08.jet", PyrokinesisHandlers::jetting);
		MutationVisuals.registerFlag("p08.thrower", PyrokinesisHandlers::throwing);
		MutationVisuals.registerFlag("p08.charging", PyrokinesisHandlers::charging);
		MutationVisuals.registerValue("p08.charge", PyrokinesisHandlers::meteorCharge);
		MutationVisuals.registerFlag("p08.dash", PyrokinesisHandlers::dashing);
		MutationVisuals.registerFlag("p08.locked", PyrokinesisHandlers::locked);
		MutationVisuals.registerFlag("p08.cauterize", p -> owns(p) && resAny(p, "cauterize_fx") > 0.5f);
		MutationVisuals.registerFlag("p08.whip", p -> owns(p) && resAny(p, "whip_ticks") > 0.5f);
		MutationVisuals.registerValue("p08.whip_t", p -> owns(p) && resAny(p, "whip_ticks") > 0.5f ? resAny(p, "whip_t") : 0.0);
		MutationVisuals.registerValue("p08.whip_len", p -> owns(p) && resAny(p, "whip_ticks") > 0.5f ? resAny(p, "whip_len") : 0.0);
		MutationVisuals.registerValue("p08.lasso", PyrokinesisHandlers::lassoId);
		// v0.16.6 hold moves
		MutationVisuals.registerFlag("p08.blue_charge", p -> owns(p) && resAny(p, "blue_charge") > 0.5f);
		MutationVisuals.registerFlag("p08.phoenix", PyrokinesisHandlers::phoenix);
		MutationVisuals.registerFlag("p08.phoenix_dive", PyrokinesisHandlers::phoenixDiving);
		MutationVisuals.registerFlag("p08.serpent_cast", p -> owns(p) && resAny(p, "serpent_fx") > 0.5f);
		MutationVisuals.registerFlag("p08.flare", p -> owns(p) && resAny(p, "flare_fx") > 0.5f);
	}

	public static void registerMeters() {
		String k = KEY;
		int text = 0xFFFFD2A8;
		MutationMeters.register(new MutationMeters.Spec(k, "heat", MutationMeters.Kind.BUILD, MutationMeters.Style.HAIRLINE, "Heat",
				MAX_HEAT, 0xFFFF7A1E, true, true, false, text));
		MutationMeters.register(new MutationMeters.Spec(k, "lock", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"OVERHEATED", LOCK_TICKS, 0xFFE03A2A, false, false, false, text));
		MutationMeters.register(new MutationMeters.Spec(k, "meteor_charge", MutationMeters.Kind.BUILD, MutationMeters.Style.HAIRLINE,
				"Meteor", 100, 0xFFFFB347, false, true, true, text));
		MutationMeters.register(new MutationMeters.Spec(k, "lasso_ticks", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Fire Lasso", LASSO_TICKS, 0xFFFF9A3C, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(k, "meteor_cd", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Meteor", METEOR_CD, 0xFF9A7A5A, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(k, "wall_cd", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Flame Wall", WALL_CD, 0xFF9A7A5A, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(k, "lasso_cd", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Fire Lasso", LASSO_CD, 0xFF9A7A5A, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(k, "cauterize_cd", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Cauterize", CAUTERIZE_CD, 0xFF9A7A5A, false, false, true, text));
		// v0.16.6: the hold moves' own cooldowns
		Object[][] holds = { { "blue_cd", "Blue Flame", BLUE_CD }, { "serpent_cd", "Flame Serpent", SERPENT_CD },
				{ "phoenix_cd", "Phoenix Dive", PHOENIX_CD }, { "shower_cd", "Meteor Shower", SHOWER_CD }, { "ring_cd", "Ring of Fire", RING_CD },
				{ "flare_cd", "Solar Flare", FLARE_CD } };
		for (Object[] h : holds) {
			MutationMeters.register(new MutationMeters.Spec(k, (String) h[0], MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
					(String) h[1], (int) h[2], 0xFF9A7A5A, false, false, true, text));
		}
		// pre-v0.16.5 resources: never drawn
		for (String old : LEGACY) {
			BatchBUtil.hideMeter(k, old);
		}
	}
}
