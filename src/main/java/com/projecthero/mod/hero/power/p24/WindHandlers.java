package com.projecthero.mod.hero.power.p24;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 24 -- Wind Manipulation (v0.16.5 rework, docs/V0165_POWERS_PROPOSAL.md, "Wind Manipulation Rework"): an
 * air-bender on a <b>Gust</b> meter instead of cooldowns.
 *
 * <p>Gust (0-100): every move spends some; it refills 15 a second (25 above y 100) while no held move is running. A move
 * needs its full cost; a held move ends when the meter runs dry.
 *
 * <p>R Wind Blade / Shift+R Blade Flurry (held). G Vacuum / Shift+G Wind Push. X Tailwind (dash) / Shift+X Updraft. Z
 * Tornado / Shift+Z Hurricane. V Wind Barrier (held) / Shift+V Glide (toggle). C Rideable Tornado (toggle) / Shift+C Air
 * Bubble. No H / N. All six slots are HOLD, so the handlers own their tap / Shift / release logic.
 *
 * <p>Passives: no fall damage, projectiles aimed at you veer off 30% of the time, cobwebs do not slow you.
 */
public final class WindHandlers {
	public static final String KEY = "power_24_wind_manipulation";

	// ---- Gust ----
	public static final float MAX_GUST = 100.0f;
	public static final float GUST_REGEN_PER_SECOND = 15.0f;
	/** Above this height the air is yours: 25 a second. */
	public static final double HIGH_AIR_Y = 100.0;
	public static final float GUST_REGEN_HIGH_PER_SECOND = 25.0f;

	// ---- R: Wind Blade / Blade Flurry ----
	public static final float COST_BLADE = 8.0f;
	public static final float BLADE_DAMAGE = 12.0f;
	public static final double BLADE_SPEED = 1.6;
	public static final double BLADE_RANGE = 30.0;
	public static final float FLURRY_DAMAGE = 6.0f;
	public static final float FLURRY_PER_SECOND = 4.0f;
	public static final float COST_FLURRY_PER_SECOND = 12.0f;
	public static final double FLURRY_RANGE = 24.0;

	// ---- G: Vacuum / Wind Push ----
	public static final float COST_VACUUM = 20.0f;
	public static final double VACUUM_RADIUS = 10.0;
	/** How far away the vortex can open (the aim point). */
	public static final double VACUUM_RANGE = 18.0;
	public static final float COST_PUSH = 15.0f;
	public static final double PUSH_RANGE = 10.0;
	/** cos of the push cone's half-angle (~40 degrees). */
	public static final double PUSH_CONE = 0.766;
	public static final float PUSH_DAMAGE = 6.0f;
	public static final float WALL_SLAM_DAMAGE = 8.0f;
	public static final double PUSH_SPEED = 3.0;
	/** A pushed creature slams into a wall within this long for the extra damage. */
	private static final int SLAM_WINDOW = 30;

	// ---- X: Tailwind / Updraft ----
	public static final float COST_DASH = 10.0f;
	public static final double DASH_BLOCKS = 14.0;
	public static final int DASH_TICKS = 7;
	public static final float COST_UPDRAFT = 15.0f;
	/**
	 * v0.16.5 (user): X while gliding is a Gust Boost -- a firework rocket for the glide. It spends ALL your Gust (at least
	 * this much), and the more it spends the longer it pushes: {@link #boostTicks}.
	 */
	public static final float BOOST_MIN_GUST = 10.0f;
	/** The launch that rises 12 blocks (vanilla gravity and drag). */
	public static final double UPDRAFT_SPEED = 1.5;
	/** ~the top of the 12-block rise: the glide opens. */
	private static final int UPDRAFT_APEX_TICKS = 16;

	// ---- Z: Tornado / Hurricane ----
	public static final float COST_TORNADO = 35.0f;
	public static final double TORNADO_PULL_RADIUS = 7.0;
	public static final int TORNADO_SPIN_TICKS = 6 * 20;
	public static final float TORNADO_DPS = 4.0f;
	public static final float COST_HURRICANE = 30.0f;
	public static final int HURRICANE_TICKS = 5 * 20;
	public static final double HURRICANE_RADIUS = 4.5;

	// ---- V: Wind Barrier / Glide ----
	public static final float COST_BARRIER_PER_SECOND = 6.0f;
	public static final double BARRIER_RANGE = 5.0;
	/** The barrier covers about 120 degrees in front. */
	private static final double BARRIER_CONE = 0.5;

	// ---- C: Rideable Tornado / Air Bubble ----
	public static final float COST_RIDE_PER_SECOND = 8.0f;
	public static final float RIDE_BOWL_DAMAGE = 6.0f;
	public static final double RIDE_BOWL_RADIUS = 2.2;
	public static final float COST_BUBBLE = 20.0f;
	public static final int BUBBLE_TICKS = 20 * 20;
	public static final double BUBBLE_RADIUS = 4.0;

	// ---- v0.16.6 hold moves (HoldTap: hold the key 0.6 s). Each has its own cooldown (ticks, from the cast). ----
	/** R hold: Cyclone Blade -- one huge spinning blade of air, 3 blocks across. */
	public static final float COST_CYCLONE = 25.0f;
	public static final float CYCLONE_DAMAGE = 22.0f;
	public static final double CYCLONE_RANGE = 40.0;
	public static final double CYCLONE_SPEED = 1.4;
	/** Half its width: everything within 1.5 blocks of its path is cut. */
	public static final double CYCLONE_HALF_WIDTH = 1.5;
	public static final int CYCLONE_COOLDOWN = 5 * 20;
	/** G hold: Suffocate -- the air round the target is stolen for 5 s. */
	public static final float COST_SUFFOCATE = 30.0f;
	public static final double SUFFOCATE_RANGE = 20.0;
	public static final int SUFFOCATE_TICKS = 5 * 20;
	public static final float SUFFOCATE_DPS = 3.0f;
	public static final int SUFFOCATE_COOLDOWN = 10 * 20;
	/** X hold: Sky Step -- 10 s of walking on air, up to 5 mid-air jumps. */
	public static final float COST_SKYSTEP = 20.0f;
	public static final int SKYSTEP_TICKS = 10 * 20;
	public static final int SKYSTEP_JUMPS = 5;
	/** The client's mid-air jump (a little higher than a normal 0.42 jump). */
	public static final double SKYSTEP_JUMP_SPEED = 0.62;
	public static final int SKYSTEP_COOLDOWN = 12 * 20;
	/** Z hold: Twin Tornadoes -- two funnels orbit you for 8 s. */
	public static final float COST_TWIN = 45.0f;
	public static final int TWIN_TICKS = 8 * 20;
	public static final float TWIN_DPS = 4.0f;
	public static final double TWIN_ORBIT_RADIUS = 3.5;
	/** A creature this close to a twin funnel's axis is touched by it. */
	public static final double TWIN_TOUCH = 1.6;
	public static final int TWIN_COOLDOWN = 14 * 20;
	/** V hold: Eye of the Storm -- a calm bubble round you for 8 s. */
	public static final float COST_EYE = 35.0f;
	public static final int EYE_TICKS = 8 * 20;
	public static final double EYE_RADIUS = 5.0;
	public static final int EYE_COOLDOWN = 16 * 20;
	/** C hold: Downburst -- a column of air slammed down on the aim point. */
	public static final float COST_DOWNBURST = 30.0f;
	public static final double DOWNBURST_RANGE = 24.0;
	/** 6 blocks across. */
	public static final double DOWNBURST_RADIUS = 3.0;
	public static final float DOWNBURST_DAMAGE = 18.0f;
	public static final int DOWNBURST_COOLDOWN = 8 * 20;
	private static final String[] HOLD_COOLDOWNS = { "cyclone", "suffocate", "skystep", "twin", "eye", "downburst" };

	// ---- passives ----
	public static final float ARROW_VEER_CHANCE = 0.3f;
	private static final String VEER_TAG = "projecthero.wind_checked";

	/** Resources cannot go below 0: a direction is stored shifted up by this much. */
	private static final float DIR_SHIFT = 2.0f;

	/** Pre-v0.16.5 modifiers (Tailwind / the sprint and jump passives), cleared out of old saves. */
	private static final ResourceLocation[] LEGACY = { com.projecthero.mod.ProjectHeroMod.id("wind_jump"),
			com.projecthero.mod.ProjectHeroMod.id("wind_sprint"), com.projecthero.mod.ProjectHeroMod.id("tailwind_step"),
			com.projecthero.mod.ProjectHeroMod.id("tailwind_atk") };

	/** A creature flung by Wind Push, watched for a wall slam. */
	private record Slam(ServerLevel level, UUID owner, int entityId, long until) {
	}

	private static final Map<Integer, Slam> SLAMS = new ConcurrentHashMap<>();

	/** Sky Step: per player {last y, last y-speed, was on the ground (1/0)} -- how the server spots a mid-air jump. */
	private static final Map<UUID, double[]> SKY_PREV = new ConcurrentHashMap<>();

	private WindHandlers() {
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

	public static float gust(ServerPlayer p) {
		seed(p);
		return res(p, "gust");
	}

	/** For the gametests: the Gust meter set directly. */
	public static void setGust(ServerPlayer p, float value) {
		seed(p);
		set(p, "gust", Mth.clamp(value, 0, MAX_GUST));
	}

	/** Glide is open (also read by the client's elytra hook). */
	public static boolean gliding(Player p) {
		return owns(p) && resAny(p, "glide") > 0.5f;
	}

	public static boolean barrierUp(Player p) {
		return owns(p) && resAny(p, "barrier") > 0.5f;
	}

	public static boolean flurrying(Player p) {
		return owns(p) && resAny(p, "flurry") > 0.5f;
	}

	public static boolean hurricane(Player p) {
		return owns(p) && resAny(p, "hurricane") > 0.5f;
	}

	public static boolean bubble(Player p) {
		return owns(p) && resAny(p, "bubble") > 0.5f;
	}

	public static boolean riding(Player p) {
		return owns(p) && resAny(p, "ride") > 0.5f;
	}

	public static boolean dashing(Player p) {
		return owns(p) && resAny(p, "dash") > 0.5f;
	}

	/** v0.16.6 hold moves running (synced flags; both sides). */
	public static boolean skyStepping(Player p) {
		return owns(p) && resAny(p, "skystep") > 0.5f;
	}

	public static boolean eyeOfStorm(Player p) {
		return owns(p) && resAny(p, "eye") > 0.5f;
	}

	public static boolean twinActive(Player p) {
		return owns(p) && resAny(p, "twin") > 0.5f;
	}

	/** Sky Step: mid-air jumps left. */
	public static int skyJumpsLeft(Player p) {
		return skyStepping(p) ? Math.round(resAny(p, "sky_jumps")) : 0;
	}

	/** Ticks left on a hold move's own cooldown (gametests / HUD). */
	public static int holdCooldown(ServerPlayer p, String move) {
		return Math.round(res(p, "cd_" + move));
	}

	/** Cobwebs never slow a wind user (EntityWebMixin). Works on both sides. */
	public static boolean ignoresWebs(Entity e) {
		return e instanceof Player p && owns(p);
	}

	/** Whether the wind may carry {@code e} about: never a boss, a huge creature or an armour stand. */
	public static boolean movable(LivingEntity e) {
		return !(e instanceof ArmorStand) && !com.projecthero.mod.titanshifter.TitanCombat.isBoss(e) && e.getMaxHealth() <= 200.0f
				&& !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator()));
	}

	private static void seed(ServerPlayer p) {
		if (res(p, "seeded") < 0.5f) {
			set(p, "seeded", 1);
			set(p, "gust", MAX_GUST);
		}
	}

	/** True (and nothing said) when {@code cost} Gust is there; otherwise the "not enough" message. */
	private static boolean afford(AbilityContext ctx, float cost) {
		ServerPlayer p = ctx.player();
		seed(p);
		if (res(p, "gust") + 1.0e-3f < cost) {
			ctx.actionBar("message.projecthero.wind.gust_low");
			return false;
		}
		return true;
	}

	/**
	 * v0.16.6: a hold move may go -- its own cooldown is over (the "on cooldown" message otherwise) and its Gust is there.
	 * Starts the cooldown when it says yes.
	 */
	private static boolean holdReady(AbilityContext ctx, String move, float cost, int cooldown) {
		ServerPlayer p = ctx.player();
		float left = res(p, "cd_" + move);
		if (left > 0.5f) {
			ctx.actionBar("message.projecthero.ability.on_cooldown", net.minecraft.network.chat.Component.translatable(
					ctx.ability().nameKey() + ".hold"), String.format(java.util.Locale.ROOT, "%.1f", left / 20.0f));
			return false;
		}
		if (!afford(ctx, cost)) {
			return false;
		}
		set(p, "cd_" + move, cooldown);
		return true;
	}

	public static void spend(ServerPlayer p, float amount) {
		seed(p);
		set(p, "gust", Math.max(0.0f, res(p, "gust") - amount));
	}

	/** A held move (Flurry, Barrier, the ridden tornado) is running: the meter does not refill. */
	private static boolean channelling(ServerPlayer p) {
		return res(p, "flurry") > 0.5f || res(p, "barrier") > 0.5f || res(p, "ride") > 0.5f;
	}

	/** Gust per tick refilled at {@code y}. */
	public static float regenPerTick(double y) {
		return (y > HIGH_AIR_Y ? GUST_REGEN_HIGH_PER_SECOND : GUST_REGEN_PER_SECOND) / 20.0f;
	}

	private static Vec3 mid(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	private static ServerLevel level(ServerPlayer p) {
		return AbilityHelpers.level(p);
	}

	// ================================================================ registration

	public static void register() {
		// Shift+V Glide: a custom elytra -- vanilla elytra flight (steer with your look, dive for speed, pull up to climb)
		net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents.CUSTOM.register((entity, tickElytra) ->
				entity instanceof Player pl && gliding(pl) && !pl.getAbilities().flying);

		// R -- Wind Blade / Blade Flurry (Shift, held). Also runs the power-wide upkeep.
		AbilityHandlers.register(KEY, "wind_blade", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (afford(ctx, COST_FLURRY_PER_SECOND / 20.0f * 5)) {
						startFlurry(p);
					}
					return;
				}
				if (afford(ctx, COST_BLADE)) {
					windBlade(p);
				}
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				if (holdReady(ctx, "cyclone", COST_CYCLONE, CYCLONE_COOLDOWN)) {
					cycloneBlade(ctx.player());
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				upkeep(ctx.player(), ctx.level());
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				endFlurry(ctx.player());
			}
		});

		// G -- Vacuum / Wind Push (Shift)
		AbilityHandlers.register(KEY, "vacuum", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (afford(ctx, COST_PUSH)) {
						windPush(p);
					}
				} else if (afford(ctx, COST_VACUUM)) {
					vacuum(p);
				}
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				LivingEntity t = suffocateTarget(p);
				if (t == null) {
					ctx.actionBar("message.projecthero.wind.no_target");
				} else if (holdReady(ctx, "suffocate", COST_SUFFOCATE, SUFFOCATE_COOLDOWN)) {
					suffocate(p, t);
				}
			}
		});

		// X -- Tailwind (dash) / Updraft (Shift)
		AbilityHandlers.register(KEY, "tailwind", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isPassenger()) {
					ctx.actionBar("message.projecthero.wind.riding");
					return;
				}
				if (p.isShiftKeyDown()) {
					if (afford(ctx, COST_UPDRAFT)) {
						updraft(p);
					}
				} else if (res(p, "glide") > 0.5f && p.isFallFlying()) {
					if (afford(ctx, BOOST_MIN_GUST)) {
						glideBoost(p); // gliding: X is a firework for the glide, not the dash
					}
				} else if (afford(ctx, COST_DASH)) {
					tailwind(p);
				}
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				if (ctx.player().isPassenger()) {
					ctx.actionBar("message.projecthero.wind.riding");
				} else if (holdReady(ctx, "skystep", COST_SKYSTEP, SKYSTEP_COOLDOWN)) {
					skyStep(ctx.player());
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				dashTick(ctx.player());
				updraftTick(ctx.player());
				skyStepTick(ctx.player());
			}
		});

		// Z -- Tornado / Hurricane (Shift)
		AbilityHandlers.register(KEY, "tornado", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (res(p, "hurricane") > 0.5f) {
						return; // already raging
					}
					if (afford(ctx, COST_HURRICANE)) {
						startHurricane(p);
					}
				} else if (afford(ctx, COST_TORNADO)) {
					tornado(p);
				}
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				if (holdReady(ctx, "twin", COST_TWIN, TWIN_COOLDOWN)) {
					twinTornadoes(ctx.player());
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				hurricaneTick(ctx.player(), ctx.level());
				if (res(ctx.player(), "twin") > 0.5f && BatchA.countDown(ctx.player(), KEY, "twin") < 0.5f) {
					MutationVisuals.stopIf(ctx.player(), "p24.twin");
				}
			}
		});

		// V -- Wind Barrier (v0.16.6: a toggle -- tap on, tap off) / Glide (Shift, toggle) / Eye of the Storm (hold)
		AbilityHandlers.register(KEY, "wind_barrier", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (res(p, "glide") > 0.5f) {
						endGlide(p);
					} else if (!p.isPassenger()) {
						startGlide(p);
					}
					return;
				}
				// v0.16.6 (user: keys that used holding become toggles): a tap raises it, the next tap drops it
				if (res(p, "barrier") > 0.5f) {
					endBarrier(p);
					AbilityHelpers.sound(p, SoundEvents.BREEZE_IDLE_AIR, 0.8f, 0.8f);
				} else if (afford(ctx, COST_BARRIER_PER_SECOND / 20.0f * 5)) {
					startBarrier(p);
				}
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				if (holdReady(ctx, "eye", COST_EYE, EYE_COOLDOWN)) {
					eyeOfTheStorm(ctx.player());
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				barrierTick(ctx.player());
				glideTick(ctx.player(), ctx.level());
				eyeTick(ctx.player(), ctx.level());
			}
		});

		// C -- Rideable Tornado (toggle) / Air Bubble (Shift)
		AbilityHandlers.register(KEY, "rideable_tornado", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown() && res(p, "ride") < 0.5f) {
					if (afford(ctx, COST_BUBBLE)) {
						startBubble(p);
					}
					return;
				}
				if (res(p, "ride") > 0.5f) {
					endRide(p);
				} else if (afford(ctx, COST_RIDE_PER_SECOND / 20.0f * 10)) {
					startRide(ctx);
				}
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				if (holdReady(ctx, "downburst", COST_DOWNBURST, DOWNBURST_COOLDOWN)) {
					downburst(ctx.player());
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				rideTick(ctx.player());
				bubbleTick(ctx.player(), ctx.level());
			}
		});

		// v0.16.6 (user: "extra moves that are triggered by holding down the ability key"): every key taps as before and
		// holds (0.6 s) for its hold move; Shift moves are unchanged
		for (String id : new String[] { "wind_blade", "vacuum", "tailwind", "tornado", "wind_barrier", "rideable_tornado" }) {
			com.projecthero.mod.hero.HoldTap.register(KEY, id);
		}
		// logging out ends the running hold moves (their models are never saved either)
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.player;
			if (owns(p)) {
				endHoldMoves(p);
				endBarrier(p);
			}
			SKY_PREV.remove(p.getUUID());
		});

		registerPassives();
	}

	// ================================================================ upkeep (every tick for an owner)

	private static void upkeep(ServerPlayer p, ServerLevel level) {
		seed(p);
		for (String cd : HOLD_COOLDOWNS) {
			BatchA.countDown(p, KEY, "cd_" + cd);
		}
		// v0.16.6: another power took the keys -- the toggled Barrier and the running hold moves end
		Power active = ExperimentalPowers.getActive(p);
		if (active == null || !KEY.equals(active.key())) {
			if (res(p, "barrier") > 0.5f || res(p, "skystep") > 0.5f || res(p, "eye") > 0.5f || res(p, "twin") > 0.5f) {
				endBarrier(p);
				endHoldMoves(p);
			}
		}
		float g = res(p, "gust");
		if (g < MAX_GUST && !channelling(p)) {
			set(p, "gust", Math.min(MAX_GUST, g + regenPerTick(p.getY())));
		}
		flurryTick(p, level);
	}

	// ================================================================ R: Wind Blade / Blade Flurry

	/** Fires one Wind Blade (12 to everything it passes through). Returns the blade. */
	public static WindBladeEntity windBlade(ServerPlayer p) {
		spend(p, COST_BLADE);
		float side = res(p, "slash_side") > 0.5f ? -1 : 1;
		set(p, "slash_side", side > 0 ? 1 : 0);
		WindBladeEntity b = new WindBladeEntity(p.level(), p, p.getLookAngle(), BLADE_SPEED, BLADE_RANGE, BLADE_DAMAGE, 1.0f,
				0.28f * side);
		p.level().addFreshEntity(b);
		MutationVisuals.play(p, side > 0 ? "p24.slash_r" : "p24.slash_l");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 0.9f, 1.5f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.2f);
		return b;
	}

	private static void startFlurry(ServerPlayer p) {
		set(p, "flurry", 1);
		set(p, "flurry_acc", 0.99f); // the first blade goes at once
		MutationVisuals.play(p, "p24.flurry");
	}

	private static void endFlurry(ServerPlayer p) {
		if (res(p, "flurry") > 0.5f) {
			set(p, "flurry", 0);
			MutationVisuals.stopIf(p, "p24.flurry");
		}
	}

	private static void flurryTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "flurry") < 0.5f) {
			return;
		}
		float perTick = COST_FLURRY_PER_SECOND / 20.0f;
		if (!p.isAlive() || res(p, "gust") < perTick) {
			endFlurry(p);
			return;
		}
		MutationVisuals.ensure(p, "p24.flurry");
		spend(p, perTick);
		float acc = res(p, "flurry_acc") + FLURRY_PER_SECOND / 20.0f;
		if (acc >= 1.0f) {
			acc -= 1.0f;
			flurryBlade(p);
		}
		set(p, "flurry_acc", acc);
	}

	/** One blade of the flurry: smaller, 6 each, fanned a little and tilted alternately. */
	public static WindBladeEntity flurryBlade(ServerPlayer p) {
		float n = res(p, "flurry_n") + 1;
		set(p, "flurry_n", n % 64);
		float side = ((int) n) % 2 == 0 ? 1 : -1;
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 dir = look.add(right.scale(side * 0.06)).add(0, (p.getRandom().nextFloat() - 0.5f) * 0.06, 0).normalize();
		WindBladeEntity b = new WindBladeEntity(p.level(), p, dir, BLADE_SPEED, FLURRY_RANGE, FLURRY_DAMAGE, 0.85f, 0.55f * side);
		p.level().addFreshEntity(b);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.5f + p.getRandom().nextFloat() * 0.3f);
		return b;
	}

	// ================================================================ G: Vacuum / Wind Push

	/** Where the Vacuum opens: the aim point (18 blocks), pulled back out of the block it hit and off the floor. */
	private static Vec3 vacuumCentre(ServerPlayer p) {
		LivingEntity t = AbilityHelpers.raycastEntity(p, VACUUM_RANGE);
		if (t != null) {
			return mid(t);
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, VACUUM_RANGE);
		Vec3 at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(p.getLookAngle().scale(0.8))
				: p.getEyePosition().add(p.getLookAngle().scale(VACUUM_RANGE));
		return at.add(0, 0.9, 0);
	}

	/** G: the vortex opens at the aim point and drags in every enemy within 10, then holds them for 1 s. */
	public static WindFxEntity vacuum(ServerPlayer p) {
		spend(p, COST_VACUUM);
		Vec3 c = vacuumCentre(p);
		WindFxEntity fx = new WindFxEntity(p.level(), p, WindFxEntity.VACUUM,
				WindFxEntity.VACUUM_PULL_TICKS + WindFxEntity.VACUUM_HOLD_TICKS + 8, c, p.getYRot(), 0);
		p.level().addFreshEntity(fx);
		fx.openVacuum(p);
		MutationVisuals.play(p, "p24.pull");
		level(p).playSound(null, c.x, c.y, c.z, SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.4f, 0.7f);
		return fx;
	}

	/** Shift+G: a 10-block cone of wind. 6 to each, a huge shove, +8 if it slams into a wall. Returns how many it hit. */
	public static int windPush(ServerPlayer p) {
		spend(p, COST_PUSH);
		ServerLevel level = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		int hit = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(PUSH_RANGE * 0.5)), PUSH_RANGE * 0.5 + 2.0)) {
			Vec3 to = mid(e).subtract(eye);
			double d = to.length();
			if (e == p || d > PUSH_RANGE + e.getBbWidth() || (d > 1.2 && to.scale(1.0 / d).dot(look) < PUSH_CONE)) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(p, e, PUSH_DAMAGE)) {
				hit++;
			}
			if (movable(e)) {
				Vec3 dir = new Vec3(to.x, 0, to.z);
				dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(look.x, 0, look.z) : dir;
				dir = dir.normalize().add(new Vec3(look.x, 0, look.z).normalize()).normalize();
				double power = PUSH_SPEED * (1.0 - 0.35 * Math.min(1.0, d / PUSH_RANGE));
				e.setDeltaMovement(dir.x * power, 0.45, dir.z * power);
				e.hurtMarked = true;
				e.hasImpulse = true;
				SLAMS.put(e.getId(), new Slam(level, p.getUUID(), e.getId(), level.getGameTime() + SLAM_WINDOW));
			}
		}
		// projectiles in the cone are blown away too
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(PUSH_RANGE),
				pr -> pr.isAlive() && pr.getOwner() != p)) {
			Vec3 to = pr.position().subtract(eye);
			if (to.length() < PUSH_RANGE && to.normalize().dot(look) > PUSH_CONE) {
				blowAway(p, pr, look);
			}
		}
		Vec3 at = eye.subtract(0, 0.3, 0).add(look.scale(0.8));
		level.addFreshEntity(new WindFxEntity(level, p, WindFxEntity.PUSH, 12, at, p.getYRot(), p.getXRot()));
		MutationVisuals.play(p, "p24.push");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.3f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 1.0f, 0.8f);
		return hit;
	}

	/** Wall slams: a creature Wind Push flung that runs into a wall takes the extra 8. */
	private static void slamTick(MinecraftServer server) {
		if (SLAMS.isEmpty()) {
			return;
		}
		for (Iterator<Slam> it = SLAMS.values().iterator(); it.hasNext();) {
			Slam s = it.next();
			Entity e = s.level().getEntity(s.entityId());
			if (!(e instanceof LivingEntity le) || !le.isAlive() || s.level().getGameTime() > s.until()) {
				it.remove();
				continue;
			}
			if (le.horizontalCollision && s.level().getPlayerByUUID(s.owner()) instanceof ServerPlayer p) {
				it.remove();
				wallSlam(p, le);
			}
		}
	}

	/** The wall slam itself (also called directly by the gametests). */
	public static void wallSlam(ServerPlayer p, LivingEntity le) {
		SLAMS.remove(le.getId());
		AbilityHelpers.hurtBurst(p, le, WALL_SLAM_DAMAGE);
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.GUST, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.6f);
		le.setDeltaMovement(le.getDeltaMovement().multiply(-0.2, 1, -0.2));
		le.hurtMarked = true;
	}

	/** Whether Wind Push is still watching {@code e} for a wall slam (gametests). */
	public static boolean slamPending(Entity e) {
		return SLAMS.containsKey(e.getId());
	}

	// ================================================================ X: Tailwind / Updraft

	/** X: the wind at your back throws you 14 blocks along your look (held for 7 ticks at 2 blocks a tick). */
	public static void tailwind(ServerPlayer p) {
		spend(p, COST_DASH);
		endGlide(p);
		Vec3 look = p.getLookAngle();
		Vec3 dir = p.onGround() && look.y < 0.15 ? new Vec3(look.x, 0.02, look.z) : look;
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
		set(p, "dash", DASH_TICKS);
		set(p, "dash_x", (float) dir.x + DIR_SHIFT);
		set(p, "dash_y", (float) dir.y + DIR_SHIFT);
		set(p, "dash_z", (float) dir.z + DIR_SHIFT);
		AbilityHelpers.launchSelf(p, dir.scale(DASH_BLOCKS / DASH_TICKS));
		ServerLevel level = level(p);
		level.addFreshEntity(new WindFxEntity(level, p, WindFxEntity.DASH, DASH_TICKS + 8, p.position(), p.getYRot(), p.getXRot()));
		MutationVisuals.play(p, "p24.dash");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 1.1f, 1.3f);
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0, 0, 0, 0);
	}

	/** The direction of the running Tailwind dash, or null. */
	public static Vec3 dashDir(ServerPlayer p) {
		if (res(p, "dash") < 0.5f) {
			return null;
		}
		return new Vec3(res(p, "dash_x") - DIR_SHIFT, res(p, "dash_y") - DIR_SHIFT, res(p, "dash_z") - DIR_SHIFT);
	}

	private static void dashTick(ServerPlayer p) {
		float left = res(p, "dash");
		if (left < 0.5f) {
			return;
		}
		set(p, "dash", left - 1);
		p.resetFallDistance();
		Vec3 dir = dashDir(p);
		if (left > 1.5f && dir != null && p.isAlive()) {
			// re-asserted every tick so ground friction cannot eat the dash
			AbilityHelpers.launchSelf(p, dir.scale(DASH_BLOCKS / DASH_TICKS));
		} else {
			// the last tick: drop to a normal run
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().multiply(0.3, 0.3, 0.3));
		}
	}

	/** Shift+X: a rising column throws you 12 blocks up; the glide opens at the top. */
	public static void updraft(ServerPlayer p) {
		spend(p, COST_UPDRAFT);
		endGlide(p);
		set(p, "updraft", 1);
		set(p, "updraft_t", 0);
		Vec3 mv = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(mv.x * 0.5, UPDRAFT_SPEED, mv.z * 0.5));
		ServerLevel level = level(p);
		level.addFreshEntity(new WindFxEntity(level, p, WindFxEntity.UPDRAFT, 26, p.position(), p.getYRot(), 0));
		MutationVisuals.play(p, "p24.rise");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 1.3f, 0.8f);
		AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 0.7f, 1.2f);
	}

	private static void updraftTick(ServerPlayer p) {
		if (res(p, "updraft") < 0.5f) {
			return;
		}
		float t = res(p, "updraft_t") + 1;
		set(p, "updraft_t", t);
		p.resetFallDistance();
		if (t >= UPDRAFT_APEX_TICKS || !p.isAlive()) {
			set(p, "updraft", 0);
			if (p.isAlive() && !p.onGround() && !p.isInWater()) {
				startGlide(p);
			}
		}
	}

	// ================================================================ Z: Tornado / Hurricane

	/** Z: a tornado touches down 2 blocks ahead and travels along your aim. */
	public static WindTornadoEntity tornado(ServerPlayer p) {
		spend(p, COST_TORNADO);
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 at = p.position().add(flat.scale(2.0));
		WindTornadoEntity t = new WindTornadoEntity(p.level(), p, WindTornadoEntity.TRAVEL, at, flat);
		p.level().addFreshEntity(t);
		MutationVisuals.play(p, "p24.conjure");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_WHIRL, 1.4f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_INHALE, 1.0f, 0.6f);
		return t;
	}

	private static void startHurricane(ServerPlayer p) {
		spend(p, COST_HURRICANE);
		set(p, "hurricane", HURRICANE_TICKS);
		MutationVisuals.play(p, "p24.storm");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_WHIRL, 1.5f, 0.4f);
	}

	private static void hurricaneTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "hurricane");
		if (left < 0.5f) {
			return;
		}
		set(p, "hurricane", left - 1);
		if (!p.isAlive()) {
			set(p, "hurricane", 0);
			return;
		}
		hurricanePulse(p, level);
		if (((int) left) % 24 == 0) {
			AbilityHelpers.sound(p, SoundEvents.BREEZE_WHIRL, 1.1f, 0.5f);
		}
	}

	/** One tick of the Hurricane wall: projectiles flung away, creatures pushed out past the ring. Returns projectiles turned. */
	public static int hurricanePulse(ServerPlayer p, ServerLevel level) {
		Vec3 c = p.position();
		int turned = 0;
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(HURRICANE_RADIUS + 2.5),
				pr -> pr.isAlive() && pr.getOwner() != p)) {
			Vec3 out = new Vec3(pr.getX() - c.x, 0, pr.getZ() - c.z);
			Vec3 v = pr.getDeltaMovement();
			if (v.lengthSqr() < 1.0e-4 || out.lengthSqr() > (HURRICANE_RADIUS + 2.5) * (HURRICANE_RADIUS + 2.5)) {
				continue;
			}
			if (v.dot(out) >= 0 && pr.getTags().contains("projecthero.wind_flung")) {
				continue; // already on its way out
			}
			blowAway(p, pr, out.lengthSqr() < 1.0e-4 ? p.getLookAngle() : out.normalize());
			pr.addTag("projecthero.wind_flung");
			turned++;
		}
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(0, 1, 0), HURRICANE_RADIUS + 0.5)) {
			if (e == p || !movable(e)) {
				continue;
			}
			Vec3 out = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
			out = out.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : out.normalize();
			Vec3 spin = new Vec3(-out.z, 0, out.x);
			e.setDeltaMovement(out.scale(0.9).add(spin.scale(0.35)).add(0, 0.25, 0));
			e.hurtMarked = true;
			e.hasImpulse = true;
		}
		return turned;
	}

	// ================================================================ V: Wind Barrier / Glide

	private static void startBarrier(ServerPlayer p) {
		set(p, "barrier", 1);
		MutationVisuals.play(p, "p24.barrier");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_IDLE_GROUND, 1.0f, 1.2f);
	}

	public static void endBarrier(ServerPlayer p) {
		if (res(p, "barrier") > 0.5f) {
			set(p, "barrier", 0);
			MutationVisuals.stopIf(p, "p24.barrier");
		}
	}

	private static void barrierTick(ServerPlayer p) {
		if (res(p, "barrier") < 0.5f) {
			return;
		}
		float perTick = COST_BARRIER_PER_SECOND / 20.0f;
		if (!p.isAlive() || res(p, "gust") < perTick) {
			endBarrier(p);
			return;
		}
		spend(p, perTick);
		MutationVisuals.ensure(p, "p24.barrier");
		barrierPulse(p);
	}

	/** Every enemy projectile in front of you within 5 blocks, coming in, is blown back. Returns how many. */
	public static int barrierPulse(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		int turned = 0;
		for (Projectile pr : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(BARRIER_RANGE),
				pr -> pr.isAlive() && pr.getOwner() != p)) {
			Vec3 to = pr.position().subtract(eye);
			Vec3 v = pr.getDeltaMovement();
			if (to.length() > BARRIER_RANGE || v.lengthSqr() < 1.0e-4 || v.dot(to) >= 0) {
				continue; // too far, still, or not coming in
			}
			if (to.lengthSqr() > 0.25 && to.normalize().dot(look) < BARRIER_CONE) {
				continue; // not in front
			}
			blowBack(p, pr);
			turned++;
		}
		if (turned > 0) {
			AbilityHelpers.sound(p, SoundEvents.BREEZE_DEFLECT, 1.0f, 1.1f);
		}
		return turned;
	}

	/** Back the way it came -- at its shooter when there is one in range. */
	public static void blowBack(ServerPlayer p, Projectile pr) {
		Vec3 v = pr.getDeltaMovement();
		Entity shooter = pr.getOwner();
		double speed = Math.max(0.9, v.length());
		Vec3 out;
		if (shooter instanceof LivingEntity le && le.isAlive() && le.distanceToSqr(p) < 48.0 * 48.0) {
			out = le.getEyePosition().subtract(pr.position()).normalize().scale(speed);
		} else {
			out = v.lengthSqr() < 1.0e-4 ? p.getLookAngle().scale(speed) : v.normalize().reverse().scale(speed);
		}
		turn(p, pr, out);
	}

	/** Flung off along {@code dir}. */
	public static void blowAway(ServerPlayer p, Projectile pr, Vec3 dir) {
		double speed = Math.max(1.0, pr.getDeltaMovement().length());
		turn(p, pr, dir.normalize().scale(speed).add(0, 0.15, 0));
	}

	private static void turn(ServerPlayer p, Projectile pr, Vec3 v) {
		pr.setDeltaMovement(v);
		pr.setOwner(p);
		pr.hasImpulse = true;
		pr.hurtMarked = true;
		level(p).sendParticles(ParticleTypes.CLOUD, pr.getX(), pr.getY(), pr.getZ(), 3, 0.1, 0.1, 0.1, 0.03);
	}

	public static void startGlide(ServerPlayer p) {
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, 0.85, p.getDeltaMovement().z));
		}
		set(p, "glide", 1);
		set(p, "glide_t", 0);
		if (!p.onGround() && !p.isInWater()) {
			p.startFallFlying(); // in the air: the elytra flight starts at once
		}
		MutationVisuals.play(p, "p24.glide");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_IDLE_AIR, 1.0f, 1.2f);
	}

	/** How long a Gust Boost pushes for the Gust it spends: 10 ticks plus 0.3 a point (a full 100 = 2 s, a long-fuse rocket). */
	public static int boostTicks(float gust) {
		return 10 + Math.round(gust * 0.3f);
	}

	/** Pushing on a Gust Boost right now (synced as p24.boost: the client applies the rocket thrust). */
	public static boolean boosting(Player p) {
		return owns(p) && resAny(p, "boost") > 0.5f;
	}

	/** X while gliding: all the Gust goes into a blast of wind behind you -- an elytra firework rocket. */
	public static void glideBoost(ServerPlayer p) {
		float g = res(p, "gust");
		set(p, "gust", 0);
		set(p, "boost", boostTicks(g));
		// a kick at once (the client takes over the steady thrust as soon as the flag reaches it)
		Vec3 look = p.getLookAngle();
		Vec3 v = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, v.add(look.x * 0.1 + (look.x * 1.5 - v.x) * 0.5, look.y * 0.1 + (look.y * 1.5 - v.y) * 0.5,
				look.z * 0.1 + (look.z * 1.5 - v.z) * 0.5));
		ServerLevel level = level(p);
		level.addFreshEntity(new WindFxEntity(level, p, WindFxEntity.DASH, 14, p.position(), p.getYRot(), p.getXRot()));
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0, 0, 0, 0);
		MutationVisuals.play(p, "p24.dash");
		AbilityHelpers.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.8f);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 1.2f, 0.7f);
	}

	private static void glideTick(ServerPlayer p, ServerLevel level) {
		float boost = res(p, "boost");
		if (boost > 0.5f) {
			set(p, "boost", res(p, "glide") > 0.5f && p.isFallFlying() ? boost - 1 : 0);
			if (p.tickCount % 2 == 0) {
				Vec3 back = p.position().subtract(p.getLookAngle().scale(0.8));
				level.sendParticles(ParticleTypes.CLOUD, back.x, back.y + 0.4, back.z, 2, 0.08, 0.08, 0.08, 0.01);
			}
		}
		if (res(p, "glide") < 0.5f) {
			return;
		}
		float t = res(p, "glide_t") + 1;
		set(p, "glide_t", Math.min(t, 1000));
		if (((p.onGround() || p.isInWater()) && t > 8) || !p.isAlive() || p.getAbilities().flying || p.isPassenger()) {
			endGlide(p);
			return;
		}
		p.resetFallDistance();
		if (!p.isFallFlying() && !p.onGround() && !p.isInWater() && t > 2) {
			p.startFallFlying();
		}
		MutationVisuals.ensure(p, "p24.glide");
	}

	public static void endGlide(ServerPlayer p) {
		if (res(p, "glide") < 0.5f) {
			return;
		}
		set(p, "glide", 0);
		set(p, "boost", 0);
		MutationVisuals.stopIf(p, "p24.glide");
		if (p.isFallFlying()) {
			p.stopFallFlying();
		}
		p.resetFallDistance();
	}

	// ================================================================ C: Rideable Tornado / Air Bubble

	private static void startRide(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (p.isPassenger()) {
			ctx.actionBar("message.projecthero.wind.riding");
			return;
		}
		endGlide(p);
		WindTornadoEntity t = new WindTornadoEntity(p.level(), p, WindTornadoEntity.RIDE, p.position(), p.getLookAngle());
		p.level().addFreshEntity(t);
		if (!p.startRiding(t, true)) {
			t.discard();
			return;
		}
		set(p, "ride", 1);
		set(p, "ride_id", t.getId());
		MutationVisuals.play(p, "p24.ride");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_WHIRL, 1.3f, 0.6f);
		ctx.actionBar("message.projecthero.wind.ride_on");
	}

	/** The ridden tornado, or null. */
	public static WindTornadoEntity rideTornado(ServerPlayer p) {
		if (res(p, "ride") < 0.5f) {
			return null;
		}
		return level(p).getEntity((int) res(p, "ride_id")) instanceof WindTornadoEntity t && t.isAlive() ? t : null;
	}

	public static void endRide(ServerPlayer p) {
		WindTornadoEntity t = rideTornado(p);
		set(p, "ride", 0);
		set(p, "ride_id", 0);
		MutationVisuals.stopIf(p, "p24.ride");
		if (t != null) {
			t.dissipate();
		}
		p.resetFallDistance();
	}

	/** The ridden tornado is gone (the rider got off, or it blew out): the toggle goes off. */
	static void rideEnded(ServerPlayer p, WindTornadoEntity t) {
		if (res(p, "ride") > 0.5f && (int) res(p, "ride_id") == t.getId()) {
			set(p, "ride", 0);
			set(p, "ride_id", 0);
			MutationVisuals.stopIf(p, "p24.ride");
		}
	}

	private static void rideTick(ServerPlayer p) {
		if (res(p, "ride") < 0.5f) {
			return;
		}
		float perTick = COST_RIDE_PER_SECOND / 20.0f;
		WindTornadoEntity t = rideTornado(p);
		if (t == null || !p.isAlive() || p.getVehicle() != t || res(p, "gust") < perTick) {
			endRide(p);
			return;
		}
		spend(p, perTick);
		p.resetFallDistance();
		MutationVisuals.ensure(p, "p24.ride");
	}

	/** The ridden tornado bowls {@code e} over: 6, and thrown aside. */
	public static void bowl(ServerPlayer p, LivingEntity e, Vec3 axis, Vec3 heading) {
		AbilityHelpers.hurtBurst(p, e, RIDE_BOWL_DAMAGE);
		if (movable(e)) {
			Vec3 out = new Vec3(e.getX() - axis.x, 0, e.getZ() - axis.z);
			out = out.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : out.normalize();
			Vec3 v = out.scale(1.1).add(heading.scale(1.5)).add(0, 0.55, 0);
			e.setDeltaMovement(v);
			e.hurtMarked = true;
			e.hasImpulse = true;
		}
		level(p).sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 1, e.getZ(), 6, 0.3, 0.4, 0.3, 0.06);
		level(p).playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9f, 1.1f);
	}

	private static void startBubble(ServerPlayer p) {
		spend(p, COST_BUBBLE);
		set(p, "bubble", BUBBLE_TICKS);
		MutationVisuals.play(p, "p24.bubble");
		AbilityHelpers.sound(p, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0f, 1.0f);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_INHALE, 0.8f, 1.4f);
		bubbleClean(p, level(p));
	}

	private static void bubbleTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "bubble");
		if (left < 0.5f) {
			return;
		}
		set(p, "bubble", left - 1);
		if (!p.isAlive()) {
			set(p, "bubble", 0);
			return;
		}
		p.setAirSupply(p.getMaxAirSupply());
		if (p.isOnFire()) {
			p.clearFire();
		}
		if (((int) left) % 10 == 0) {
			bubbleClean(p, level);
		}
	}

	/** The bubble's clean air: harmful effects off you and your allies within 4 blocks, gas clouds blown away. */
	public static int bubbleClean(ServerPlayer p, ServerLevel level) {
		int cleared = clearHarmful(p);
		for (Player o : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(BUBBLE_RADIUS),
				o -> o != p && o.isAlive() && Squads.areAllies(p, o))) {
			cleared += clearHarmful(o);
			o.clearFire();
		}
		for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class, p.getBoundingBox().inflate(BUBBLE_RADIUS + 1.5),
				c -> c.getOwner() != p)) {
			level.sendParticles(ParticleTypes.CLOUD, cloud.getX(), cloud.getY() + 0.3, cloud.getZ(), 8, cloud.getRadius() * 0.5, 0.1,
					cloud.getRadius() * 0.5, 0.05);
			cloud.discard();
			cleared++;
		}
		return cleared;
	}

	private static int clearHarmful(LivingEntity e) {
		int n = 0;
		for (MobEffectInstance inst : e.getActiveEffects().toArray(new MobEffectInstance[0])) {
			MobEffect eff = inst.getEffect().value();
			if (eff.getCategory() == MobEffectCategory.HARMFUL && e.removeEffect(inst.getEffect())) {
				n++;
			}
		}
		return n;
	}

	// ================================================================ v0.16.6 hold moves

	/** R hold: Cyclone Blade -- a huge spinning blade of air, 3 blocks across, 40 blocks, 22 to everything it passes. */
	public static WindBladeEntity cycloneBlade(ServerPlayer p) {
		spend(p, COST_CYCLONE);
		WindBladeEntity b = new WindBladeEntity(p.level(), p, p.getLookAngle(), CYCLONE_SPEED, CYCLONE_RANGE, CYCLONE_DAMAGE, 1.0f, 0.0f);
		b.makeCyclone(CYCLONE_HALF_WIDTH);
		p.level().addFreshEntity(b);
		MutationVisuals.play(p, "p24.cyclone");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_WHIRL, 1.3f, 1.4f);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.2f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
		return b;
	}

	/** G hold: whom Suffocate would take the air from -- the creature you aim at within 20 blocks, if you may hurt it. */
	public static LivingEntity suffocateTarget(ServerPlayer p) {
		LivingEntity t = AbilityHelpers.raycastEntity(p, SUFFOCATE_RANGE);
		if (t == null || t == p || !t.isAlive() || t instanceof ArmorStand || !com.projecthero.mod.combat.HeroTargets.canHarm(p, t)) {
			return null;
		}
		return t;
	}

	/**
	 * G hold: Suffocate -- the air round {@code t} is stolen for 5 s: 3 a second, no sprinting or jumping, a collapsing
	 * bubble of air round its head. A boss (or anything the wind cannot carry) is only slowed.
	 */
	public static WindFxEntity suffocate(ServerPlayer p, LivingEntity t) {
		spend(p, COST_SUFFOCATE);
		ServerLevel level = level(p);
		WindFxEntity fx = new WindFxEntity(level, p, WindFxEntity.SUFFOCATE, SUFFOCATE_TICKS, t.position(), t.getYRot(), 0);
		level.addFreshEntity(fx);
		fx.startSuffocate(t);
		MutationVisuals.play(p, "p24.suffocate");
		level.playSound(null, t.getX(), t.getEyeY(), t.getZ(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.4f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_INHALE, 0.8f, 1.3f);
		return fx;
	}

	/** X hold: Sky Step -- 10 s of walking on air, up to 5 mid-air jumps (the client jumps; the server counts them). */
	public static void skyStep(ServerPlayer p) {
		spend(p, COST_SKYSTEP);
		endGlide(p);
		set(p, "skystep", SKYSTEP_TICKS);
		set(p, "sky_jumps", SKYSTEP_JUMPS);
		SKY_PREV.remove(p.getUUID());
		MutationVisuals.play(p, "p24.skystep");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_IDLE_AIR, 1.2f, 1.5f);
		ServerLevel level = level(p);
		level.addFreshEntity(new WindFxEntity(level, p, WindFxEntity.STEP, 16, p.position(), p.getYRot(), 0));
	}

	public static void endSkyStep(ServerPlayer p) {
		if (res(p, "skystep") > 0.5f || res(p, "sky_jumps") > 0.5f) {
			set(p, "skystep", 0);
			set(p, "sky_jumps", 0);
		}
		SKY_PREV.remove(p.getUUID());
	}

	private static void skyStepTick(ServerPlayer p) {
		float left = res(p, "skystep");
		if (left < 0.5f) {
			return;
		}
		if (left - 1 < 0.5f || !p.isAlive()) {
			endSkyStep(p);
			return;
		}
		set(p, "skystep", left - 1);
		p.resetFallDistance();
		// a mid-air jump: the client threw itself up (y-speed leapt by more than a quarter block a tick, off the ground)
		double y = p.getY();
		double[] prev = SKY_PREV.get(p.getUUID());
		if (prev != null) {
			double vy = y - prev[0];
			boolean wasGround = prev[2] > 0.5;
			if (!p.onGround() && !wasGround && vy > 0.3 && vy - prev[1] > 0.25 && res(p, "sky_jumps") >= 1 && res(p, "dash") < 0.5f
					&& res(p, "updraft") < 0.5f && !p.isPassenger()) {
				skyJump(p);
			}
			prev[0] = y;
			prev[1] = vy;
			prev[2] = p.onGround() ? 1 : 0;
		} else {
			SKY_PREV.put(p.getUUID(), new double[] { y, 0, p.onGround() ? 1 : 0 });
		}
	}

	/** One Sky Step jump: a jump spent, a little ring of air left under the feet. */
	public static WindFxEntity skyJump(ServerPlayer p) {
		set(p, "sky_jumps", Math.max(0, res(p, "sky_jumps") - 1));
		p.resetFallDistance();
		ServerLevel level = level(p);
		WindFxEntity ring = new WindFxEntity(level, p, WindFxEntity.STEP, 16, p.position().add(0, -0.1, 0), p.getYRot(), 0);
		level.addFreshEntity(ring);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.9f, 1.6f);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 6, 0.3, 0.02, 0.3, 0.02);
		return ring;
	}

	/** Z hold: Twin Tornadoes -- two funnels orbit you for 8 s, 4 a second to whatever they touch, which they fling away. */
	public static WindTornadoEntity[] twinTornadoes(ServerPlayer p) {
		spend(p, COST_TWIN);
		set(p, "twin", TWIN_TICKS);
		WindTornadoEntity[] out = new WindTornadoEntity[2];
		for (int i = 0; i < 2; i++) {
			double phase = Math.toRadians(p.getYRot()) + i * Math.PI;
			Vec3 at = p.position().add(Math.cos(phase) * TWIN_ORBIT_RADIUS, 0, Math.sin(phase) * TWIN_ORBIT_RADIUS);
			WindTornadoEntity t = new WindTornadoEntity(p.level(), p, WindTornadoEntity.ORBIT, at, Vec3.ZERO);
			t.orbit(phase);
			p.level().addFreshEntity(t);
			out[i] = t;
		}
		MutationVisuals.play(p, "p24.twin");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_WHIRL, 1.5f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_WHIRL, 1.2f, 0.9f);
		return out;
	}

	/** Whether {@code p}'s Twin Tornadoes are still running (the funnels blow out when not). */
	public static boolean twinRunning(ServerPlayer p) {
		return res(p, "twin") > 0.5f && p.isAlive();
	}

	/** A twin funnel touched {@code e}: 4, and flung away from you. */
	public static void twinTouch(ServerPlayer p, LivingEntity e, Vec3 axis) {
		AbilityHelpers.hurtBurst(p, e, TWIN_DPS);
		if (movable(e)) {
			Vec3 out = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
			out = out.lengthSqr() < 1.0e-4 ? new Vec3(e.getX() - axis.x, 0, e.getZ() - axis.z) : out;
			out = out.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : out.normalize();
			e.setDeltaMovement(out.x * 1.4, 0.75, out.z * 1.4);
			e.hurtMarked = true;
			e.hasImpulse = true;
		}
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 2, 0.3, 0.3, 0.3, 0);
		level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.8f);
	}

	/** V hold: Eye of the Storm -- 8 s in a calm bubble (radius 5): projectiles stop dead and drop, mobs are pushed out. */
	public static void eyeOfTheStorm(ServerPlayer p) {
		spend(p, COST_EYE);
		set(p, "eye", EYE_TICKS);
		MutationVisuals.play(p, "p24.eye");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_INHALE, 1.2f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 0.6f, 1.6f);
		eyePulse(p, level(p));
	}

	private static void eyeTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "eye");
		if (left < 0.5f) {
			return;
		}
		set(p, "eye", left - 1);
		if (!p.isAlive()) {
			set(p, "eye", 0);
			return;
		}
		eyePulse(p, level);
		if (((int) left) % 30 == 0) {
			AbilityHelpers.sound(p, SoundEvents.BREEZE_IDLE_GROUND, 0.7f, 0.6f);
		}
	}

	/** One tick of the Eye: every foreign projectile inside stops dead and drops; mobs inside are pushed out. Returns stopped. */
	public static int eyePulse(ServerPlayer p, ServerLevel level) {
		Vec3 c = mid(p);
		int stopped = 0;
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(EYE_RADIUS + 1),
				pr -> pr.isAlive() && pr.getOwner() != p)) {
			if (pr.position().distanceToSqr(c) > EYE_RADIUS * EYE_RADIUS) {
				continue;
			}
			if (pr.getOwner() instanceof Player o && Squads.areAllies(p, o)) {
				continue;
			}
			Vec3 v = pr.getDeltaMovement();
			if (pr instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile) {
				// fireballs, skulls, wind charges: the calm air snuffs them out
				level.sendParticles(ParticleTypes.CLOUD, pr.getX(), pr.getY(), pr.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
				pr.discard();
				stopped++;
				continue;
			}
			if (v.horizontalDistanceSqr() < 1.0e-4 && v.y <= 0) {
				continue; // already dropping
			}
			pr.setDeltaMovement(0, Math.min(0, v.y) * 0.3, 0);
			pr.hasImpulse = true;
			pr.hurtMarked = true;
			if (!pr.getTags().contains("projecthero.wind_eye")) {
				pr.addTag("projecthero.wind_eye");
				level.sendParticles(ParticleTypes.CLOUD, pr.getX(), pr.getY(), pr.getZ(), 4, 0.1, 0.1, 0.1, 0.01);
			}
			stopped++;
		}
		Vec3 feet = p.position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, EYE_RADIUS + 0.5)) {
			if (e == p || !movable(e)) {
				continue;
			}
			Vec3 out = new Vec3(e.getX() - feet.x, 0, e.getZ() - feet.z);
			double d = out.length();
			if (d > EYE_RADIUS + 0.3) {
				continue;
			}
			out = d < 1.0e-3 ? new Vec3(1, 0, 0) : out.scale(1.0 / d);
			double push = 0.35 + 0.5 * (1 - d / EYE_RADIUS);
			e.setDeltaMovement(out.x * push, Math.max(e.getDeltaMovement().y, 0.08), out.z * push);
			e.hurtMarked = true;
			e.hasImpulse = true;
		}
		if (stopped > 0) {
			AbilityHelpers.sound(p, SoundEvents.BREEZE_DEFLECT, 0.8f, 0.6f);
		}
		return stopped;
	}

	/** C hold: where the Downburst comes down -- the aim point (24 blocks), dropped to the ground beneath it. */
	public static Vec3 downburstPoint(ServerPlayer p) {
		LivingEntity t = AbilityHelpers.raycastEntity(p, DOWNBURST_RANGE);
		Vec3 at;
		if (t != null) {
			at = t.position();
		} else {
			BlockHitResult hit = AbilityHelpers.raycastBlock(p, DOWNBURST_RANGE);
			at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(p.getLookAngle().scale(0.3))
					: p.getEyePosition().add(p.getLookAngle().scale(DOWNBURST_RANGE));
		}
		BlockHitResult down = p.level().clip(new net.minecraft.world.level.ClipContext(at.add(0, 0.5, 0), at.subtract(0, 24, 0),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		return down.getType() == HitResult.Type.BLOCK ? down.getLocation() : at;
	}

	/** C hold: Downburst -- a column of air slams down on the aim point: 18 to everything within 3 blocks, knocked flat. */
	public static WindFxEntity downburst(ServerPlayer p) {
		spend(p, COST_DOWNBURST);
		Vec3 at = downburstPoint(p);
		ServerLevel level = level(p);
		WindFxEntity fx = new WindFxEntity(level, p, WindFxEntity.DOWNBURST, WindFxEntity.DOWNBURST_DROP_TICKS + 24, at, p.getYRot(), 0);
		level.addFreshEntity(fx);
		MutationVisuals.play(p, "p24.downburst");
		level.playSound(null, at.x, at.y + 8, at.z, SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.6f, 0.4f);
		return fx;
	}

	/** The Downburst lands at {@code at}: 18 to every enemy within 3 blocks, everything that can be moved slammed flat. */
	public static int downburstImpact(ServerPlayer p, Vec3 at) {
		ServerLevel level = level(p);
		int hit = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, at.add(0, 1.5, 0), DOWNBURST_RADIUS + 2.5)) {
			if (e == p) {
				continue;
			}
			double d = Math.hypot(e.getX() - at.x, e.getZ() - at.z);
			if (d > DOWNBURST_RADIUS + e.getBbWidth() * 0.5 || e.getY() > at.y + 6 || e.getY() + e.getBbHeight() < at.y - 1.5) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(p, e, DOWNBURST_DAMAGE)) {
				hit++;
			}
			if (movable(e)) {
				// knocked flat: driven into the ground, a little outward, and slow to get back up
				Vec3 out = new Vec3(e.getX() - at.x, 0, e.getZ() - at.z);
				out = out.lengthSqr() < 1.0e-4 ? Vec3.ZERO : out.normalize().scale(0.35);
				e.setDeltaMovement(out.x, -1.4, out.z);
				e.hurtMarked = true;
				e.hasImpulse = true;
				AbilityHelpers.applyControl(e, net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 3);
				if (e instanceof Mob m) {
					m.getNavigation().stop();
				}
			}
		}
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
		BatchA.debrisRing(level, at.add(0, 0.1, 0), DOWNBURST_RADIUS, 24);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.6f, 0.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.3f);
		return hit;
	}

	/** Ends every running hold move (power switch, power removal, logout). */
	public static void endHoldMoves(ServerPlayer p) {
		endSkyStep(p);
		if (res(p, "eye") > 0.5f) {
			set(p, "eye", 0);
		}
		if (res(p, "twin") > 0.5f) {
			set(p, "twin", 0); // the funnels see it and blow out
		}
		MutationVisuals.stopIf(p, "p24.twin");
	}

	// ================================================================ passives

	/**
	 * Arrow veer: the first time a projectile heading for you comes within 6 blocks, it is decided once: with chance
	 * {@code ARROW_VEER_CHANCE} the wind turns it aside. {@code roll} is that projectile's die (0..1). Returns whether it
	 * veered.
	 */
	public static boolean considerProjectile(ServerPlayer p, Projectile pr, float roll) {
		pr.addTag(VEER_TAG);
		if (roll >= ARROW_VEER_CHANCE) {
			return false;
		}
		Vec3 v = pr.getDeltaMovement();
		Vec3 side = v.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		if (p.getRandom().nextBoolean()) {
			side = side.reverse();
		}
		Vec3 out = v.normalize().scale(0.55).add(side).add(0, 0.25, 0).normalize().scale(v.length());
		pr.setDeltaMovement(out);
		pr.hasImpulse = true;
		pr.hurtMarked = true;
		level(p).sendParticles(ParticleTypes.CLOUD, pr.getX(), pr.getY(), pr.getZ(), 4, 0.15, 0.15, 0.15, 0.04);
		return true;
	}

	/** Whether the veer roll has already been made for {@code pr}. */
	public static boolean projectileChecked(Projectile pr) {
		return pr.getTags().contains(VEER_TAG);
	}

	private static void veerTick(ServerPlayer p) {
		Vec3 c = mid(p);
		for (Projectile pr : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(6.0),
				pr -> pr.isAlive() && pr.getOwner() != p && !projectileChecked(pr))) {
			Vec3 v = pr.getDeltaMovement();
			Vec3 to = c.subtract(pr.position());
			if (v.lengthSqr() < 0.04 || v.dot(to) <= 0) {
				continue;
			}
			// aimed at you: it would pass within ~1.6 blocks of your middle
			Vec3 dir = v.normalize();
			double along = to.dot(dir);
			double miss = to.subtract(dir.scale(along)).length();
			if (miss > 1.6) {
				continue;
			}
			considerProjectile(p, pr, p.getRandom().nextFloat());
		}
	}

	private static void registerPassives() {
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, owned) -> {
			if (!owned) {
				Power power = Powers.byKey(KEY);
				if (power != null && ExperimentalPowers.owns(player, power)) {
					// still owned while forget() runs its teardown: drop the running moves too
					endFlurry(player);
					endBarrier(player);
					endGlide(player);
					endRide(player);
					set(player, "hurricane", 0);
					set(player, "bubble", 0);
					set(player, "dash", 0);
					set(player, "updraft", 0);
					endHoldMoves(player);
				}
				MutationVisuals.stop(player);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			veerTick(player);
			if (player.tickCount % 100 == 41) {
				for (ResourceLocation id : LEGACY) {
					PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, id);
					PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, id);
					PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, id);
					PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, id);
				}
			}
		});
	}

	/** No fall damage, ever (read by {@link com.projecthero.mod.hero.power.HeroDamageRules}). */
	public static boolean fallImmune(ServerPlayer p) {
		return owns(p);
	}

	// ================================================================ world tick

	/** Called once per server tick (RevampBatchC). */
	public static void serverTick(MinecraftServer server) {
		slamTick(server);
	}

	public static void clearSessionState() {
		SLAMS.clear();
		SKY_PREV.clear();
		WindFxEntity.clearSessionState();
	}

	// ================================================================ synced visuals + HUD (called from RevampBatchC)

	public static void registerVisuals() {
		MutationVisuals.registerFlag("p24.barrier", WindHandlers::barrierUp);
		MutationVisuals.registerFlag("p24.hurricane", WindHandlers::hurricane);
		MutationVisuals.registerFlag("p24.bubble", WindHandlers::bubble);
		MutationVisuals.registerFlag("p24.glide", WindHandlers::gliding);
		MutationVisuals.registerFlag("p24.boost", WindHandlers::boosting);
		MutationVisuals.registerFlag("p24.ride", WindHandlers::riding);
		MutationVisuals.registerFlag("p24.flurry", WindHandlers::flurrying);
		// v0.16.6 hold moves: Sky Step's air rings at the feet, the Eye of the Storm's calm bubble, a Suffocate victim's lock
		MutationVisuals.registerFlag("p24.skystep", WindHandlers::skyStepping);
		MutationVisuals.registerFlag("p24.eye", WindHandlers::eyeOfStorm);
		MutationVisuals.registerFlag("p24.suffocated", WindFxEntity::isSuffocated);
		MutationVisuals.registerValue("p24.sky_jumps", p -> skyJumpsLeft(p));
		MutationVisuals.registerValue("p24.eye_left", p -> eyeOfStorm(p) ? resAny(p, "eye") : 0.0);
		// the Hurricane's start, so every client can grow the wall in and fade it out
		MutationVisuals.registerValue("p24.hurricane_left", p -> hurricane(p) ? resAny(p, "hurricane") : 0.0);
		MutationVisuals.registerValue("p24.bubble_left", p -> bubble(p) ? resAny(p, "bubble") : 0.0);
	}

	public static void registerMeters() {
		int text = 0xFFDDEEFF;
		MutationMeters.register(new MutationMeters.Spec(KEY, "gust", MutationMeters.Kind.RESERVE, MutationMeters.Style.HAIRLINE,
				"Gust", MAX_GUST, 0xFFB8E6FF, true, true, false, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "hurricane", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Hurricane", HURRICANE_TICKS, 0xFF9FC8F0, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "bubble", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Air Bubble", BUBBLE_TICKS, 0xFF8FE0F0, false, false, true, text));
		// v0.16.6 hold moves with a duration
		MutationMeters.register(new MutationMeters.Spec(KEY, "skystep", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Sky Step", SKYSTEP_TICKS, 0xFFC8F0FF, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "twin", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Twin Tornadoes", TWIN_TICKS, 0xFFA8D4F4, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "eye", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Eye of the Storm", EYE_TICKS, 0xFFB0E8E0, false, false, true, text));
	}
}
