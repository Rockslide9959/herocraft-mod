package com.projecthero.mod.hero.power.p22;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.hero.visual.ShiftStrips;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Power 22 -- Plant Manipulation / Chlorokinesis.
 *
 * <p><b>v0.17.1 revamp</b> (docs/CHLOROKINESIS_REVAMP_0171.md, user: "make it good, make it have survival use, make it fun to
 * play, give it its own custom vfx ... i want every move to have a shifted ability variant"): a green-thumb nature
 * controller on a <b>Chlorophyll</b> meter that the sun fills. Six keys, each with a Shift move; no H (the power wheel) and
 * no N. All six slots are HOLD so the handlers own tap / Shift / release; a tap move uses the key's own cooldown (the HUD
 * box), a Shift move its own countdown resource (the HUD's Shift strip, {@link ShiftStrips}).
 * <ul>
 * <li>R Thorn Volley / Shift+R Vine Lash</li>
 * <li>G Root Snare / Shift+G Spore Cloud / hold G Thorn Sentry</li>
 * <li>X Vine Grapple / Shift+X Leaf Glide (toggle)</li>
 * <li>Z Overgrowth / Shift+Z Treant</li>
 * <li>V Bloom Burst / Shift+V Living Architecture (vine ladder, root bridge, grow shelter, bramble wall)</li>
 * <li>C Verdant Form (toggle) / Shift+C Root Sense</li>
 * </ul>
 * The moves live in {@link ChloroMoves}; the survival side (Green Thumb, Tree Feller, fruit, Root Sense, the buildings) in
 * {@link ChloroSurvival}.
 */
public final class PlantManipulationHandlers {
	public static final String KEY = "power_22_plant_manipulation_chlorokinesis";

	// ---- Chlorophyll ----
	public static final float MAX_CHLOROPHYLL = 100.0f;
	public static final float REGEN_SUN = 8.0f;
	public static final float REGEN_SUN_RAIN = 5.0f;
	public static final float REGEN_NIGHT_SKY = 3.0f;
	public static final float REGEN_LAMP = 4.0f;
	public static final float REGEN_DARK = 2.0f;
	public static final float GROUND_FACTOR = 1.5f;
	public static final float LUSH_FACTOR = 1.25f;
	/** Living ground also makes every move hit 20% harder. */
	public static final float GROUND_DAMAGE_FACTOR = 1.2f;

	// ---- R: Thorn Volley / Vine Lash ----
	public static final float COST_VOLLEY = 5.0f;
	public static final float VOLLEY_DAMAGE = 5.0f;
	public static final int VOLLEY_THORNS = 3;
	public static final float COST_LASH = 12.0f;
	public static final double LASH_RANGE = 14.0;
	public static final float LASH_DAMAGE = 12.0f;
	public static final float LASH_BOSS_BONUS = 4.0f;
	public static final int LASH_CD = 3 * 20;

	// ---- G: Root Snare / Spore Cloud / hold: Thorn Sentry ----
	public static final float COST_SNARE = 18.0f;
	public static final double SNARE_RANGE = 20.0;
	public static final double SNARE_SPLASH = 2.5;
	public static final float SNARE_DAMAGE = 6.0f;
	public static final float SNARE_DPS = 2.0f;
	public static final int SNARE_TICKS = 4 * 20;
	public static final float COST_SPORES = 22.0f;
	public static final int SPORE_TICKS = 160;
	public static final double SPORE_RADIUS = 4.5;
	public static final int SPORE_CD = 14 * 20;
	public static final float COST_SENTRY = 30.0f;
	public static final int SENTRY_CD = 20 * 20;

	// ---- X: Vine Grapple / Leaf Glide ----
	public static final float COST_GRAPPLE = 6.0f;
	public static final double GRAPPLE_RANGE = 48.0;
	public static final double GRAPPLE_ENTITY_RANGE = 30.0;
	public static final float COST_GLIDE_OPEN = 5.0f;
	public static final float GLIDE_DRAIN_PER_SECOND = 2.0f;

	// ---- Z: Overgrowth / Treant ----
	public static final float COST_OVERGROWTH = 60.0f;
	public static final int OVERGROWTH_GATHER = 30;
	public static final double OVERGROWTH_RADIUS = 12.0;
	public static final float OVERGROWTH_DAMAGE = 30.0f;
	public static final float COST_TREANT = 50.0f;
	public static final int TREANT_CD = 60 * 20;
	public static final double TREANT_HEALTH = 80.0;
	public static final double TREANT_DAMAGE = 10.0;

	// ---- V: Bloom Burst / Living Architecture ----
	public static final float COST_BLOOM = 25.0f;
	public static final int BLOOM_TICKS = 6 * 20;
	public static final double BLOOM_RADIUS = 6.0;
	public static final float BLOOM_HEAL_PER_SECOND = 2.0f;
	public static final float COST_LADDER = 8.0f;
	public static final float COST_BRIDGE = 12.0f;
	public static final float COST_SHELTER = 40.0f;
	public static final float COST_BRAMBLE = 15.0f;
	public static final int ARCH_CD = 30;
	public static final int SHELTER_CD = 30 * 20;
	public static final int LADDER_MAX = 16;
	public static final int BRIDGE_LENGTH = 10;
	public static final int BRAMBLE_TICKS = 12 * 20;
	public static final float BRAMBLE_DPS = 3.0f;

	// ---- C: Verdant Form / Root Sense ----
	public static final double VERDANT_ARMOR = 6.0;
	public static final double VERDANT_TOUGHNESS = 2.0;
	public static final double VERDANT_KB = 0.25;
	public static final float VERDANT_THORNS = 3.0f;
	public static final float VERDANT_DRAIN_PER_SECOND = 1.0f;
	public static final float COST_SENSE = 15.0f;
	public static final int SENSE_RADIUS = 24;
	public static final int SENSE_CD = 8 * 20;
	public static final int SENSE_TICKS = 6 * 20;

	// ---- survival ----
	public static final float COST_BONEMEAL = 1.0f;
	public static final float COST_FLOWER = 2.0f;
	public static final float COST_FEED = 3.0f;
	public static final float COST_PURIFY = 4.0f;
	public static final float COST_FELL = 8.0f;
	public static final int FELL_MAX_LOGS = 96;
	public static final float FRUIT_CHLOROPHYLL = 12.0f;

	/** Shift moves' own countdowns (ticks); each also keeps its full length in {@code <name>_full} for the HUD strip. */
	public static final String LASH_CD_RES = "lash_cd";
	public static final String SPORE_CD_RES = "spore_cd";
	public static final String TREANT_CD_RES = "treant_cd";
	public static final String ARCH_CD_RES = "arch_cd";
	public static final String SENSE_CD_RES = "sense_cd";
	public static final String SENTRY_CD_RES = "cd_sentry";
	private static final String[] COUNTDOWNS = { LASH_CD_RES, SPORE_CD_RES, TREANT_CD_RES, ARCH_CD_RES, SENSE_CD_RES, SENTRY_CD_RES,
			"sentry_ticks", "bloom_ticks", "treant_ticks", "sense_ticks" };

	private PlantManipulationHandlers() {
	}

	// ================================================================ queries (both sides where noted)

	static float res(ServerPlayer p, String name) {
		return BatchA.res(p, KEY, name);
	}

	static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, KEY, name, v);
	}

	private static float resAny(Player p, String name) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null ? 0.0f : st.resources.getOrDefault(KEY + "/" + name, 0.0f);
	}

	public static boolean owns(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY);
	}

	/**
	 * Whether {@code player} currently has Chlorokinesis selected as their active experimental power -- public so
	 * {@code ClipContextMixin} can gate the "swing through grass" fix to only these players (v0.11.9). Synced: safe on
	 * either side.
	 */
	public static boolean isActiveFor(Player player) {
		ExperimentalState state = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return state != null && KEY.equals(state.activePower);
	}

	static Power power() {
		return Powers.byKey(KEY);
	}

	/** Verdant Form is on (the bark-and-leaf skin; the {@code p22.blessing} flag). Both sides. */
	public static boolean blessingOn(Player p) {
		return owns(p) && resAny(p, "verdant") > 0.5f;
	}

	/** Overgrowth is gathering (the root-coil overlay, green eyes). Both sides. */
	public static boolean overgrowthCharging(Player p) {
		return owns(p) && resAny(p, "og_gather") > 0.5f;
	}

	/** The Leaf Glide canopy is out. Both sides (the elytra hook asks on the client too). */
	public static boolean gliding(Player p) {
		return owns(p) && resAny(p, "glide") > 0.5f;
	}

	public static float chlorophyll(ServerPlayer p) {
		seed(p);
		return res(p, "chlorophyll");
	}

	/** For the gametests / harness: the meter set directly. */
	public static void setChlorophyll(ServerPlayer p, float v) {
		seed(p);
		set(p, "chlorophyll", Mth.clamp(v, 0, MAX_CHLOROPHYLL));
	}

	/** Ticks left on one of the countdowns (Shift move cooldowns, timers). */
	public static int countdown(ServerPlayer p, String name) {
		return Math.round(res(p, name));
	}

	static void seed(ServerPlayer p) {
		if (res(p, "seeded") < 0.5f) {
			set(p, "seeded", 1);
			set(p, "chlorophyll", MAX_CHLOROPHYLL);
		}
	}

	public static void spend(ServerPlayer p, float amount) {
		seed(p);
		set(p, "chlorophyll", Math.max(0.0f, res(p, "chlorophyll") - amount));
	}

	public static void gain(ServerPlayer p, float amount) {
		seed(p);
		set(p, "chlorophyll", Math.min(MAX_CHLOROPHYLL, res(p, "chlorophyll") + amount));
	}

	/** True when {@code cost} Chlorophyll is there; otherwise the "not enough" message. */
	static boolean afford(ServerPlayer p, float cost) {
		seed(p);
		if (res(p, "chlorophyll") + 1.0e-3f < cost) {
			p.displayClientMessage(Component.translatable("message.projecthero.chloro.low", Math.round(cost)), true);
			return false;
		}
		return true;
	}

	/** The tap move's own cooldown is over (the "on cooldown" message otherwise). */
	static boolean tapReady(AbilityContext ctx) {
		if (!ctx.cooldownReady()) {
			ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
					String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
			return false;
		}
		return true;
	}

	/** A Shift (or hold) move's own countdown is over; the message names it ({@code <ability>.shift} / {@code .hold}). */
	static boolean countdownReady(AbilityContext ctx, String res, String suffix) {
		float left = res(ctx.player(), res);
		if (left > 0.5f) {
			ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey() + "." + suffix),
					String.format(java.util.Locale.ROOT, "%.1f", left / 20.0f));
			return false;
		}
		return true;
	}

	/** Starts a countdown of {@code baseTicks} (config-scaled), keeping its full length for the HUD strip. */
	static void startCountdown(ServerPlayer p, String res, int baseTicks) {
		int t = HeroConfig.get().scaledCooldown(baseTicks);
		set(p, res, t);
		set(p, res + "_full", t);
	}

	/** Damage +20% on living ground. */
	static float dmg(ServerPlayer p, float base) {
		return p.level() instanceof ServerLevel sl && onNatureGround(sl, p.blockPosition()) ? base * GROUND_DAMAGE_FACTOR : base;
	}

	/** Whether the plants may carry / pin {@code e}: never a boss, a huge creature, an armour stand or a creative player. */
	public static boolean movable(LivingEntity e) {
		return !(e instanceof ArmorStand) && !com.projecthero.mod.titanshifter.TitanCombat.isBoss(e) && e.getMaxHealth() <= 200.0f
				&& !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator()));
	}

	// ================================================================ Chlorophyll refill

	/** Open sky over the head, daytime, no rain: the sun reaches you. */
	public static boolean inSunlight(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		BlockPos head = BlockPos.containing(p.getEyePosition());
		return level.dimensionType().hasSkyLight() && level.isDay() && level.canSeeSky(head) && !level.isRainingAt(head);
	}

	/** Chlorophyll per second where {@code p} stands right now. */
	public static float regenPerSecond(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		BlockPos head = BlockPos.containing(p.getEyePosition());
		float base;
		boolean sky = level.dimensionType().hasSkyLight() && level.canSeeSky(head);
		if (sky && level.isDay()) {
			base = level.isRainingAt(head) ? REGEN_SUN_RAIN : REGEN_SUN;
		} else if (sky) {
			base = REGEN_NIGHT_SKY;
		} else {
			base = level.getBrightness(LightLayer.BLOCK, head) >= 12 ? REGEN_LAMP : REGEN_DARK;
		}
		if (onNatureGround(level, p.blockPosition())) {
			base *= GROUND_FACTOR;
		}
		if (lushBiome(level, p.blockPosition())) {
			base *= LUSH_FACTOR;
		}
		return base;
	}

	public static boolean lushBiome(ServerLevel level, BlockPos pos) {
		var b = level.getBiome(pos);
		return b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_JUNGLE) || b.is(BiomeTags.IS_TAIGA)
				|| b.is(net.minecraft.world.level.biome.Biomes.SWAMP) || b.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP)
				|| b.is(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE) || b.is(net.minecraft.world.level.biome.Biomes.FLOWER_FOREST)
				|| b.is(net.minecraft.world.level.biome.Biomes.LUSH_CAVES);
	}

	// ================================================================ registration

	public static void register() {
		// Shift+X Leaf Glide: a custom elytra -- vanilla elytra flight for as long as the canopy is out
		net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents.CUSTOM.register((entity, tickElytra) ->
				entity instanceof Player pl && gliding(pl) && !pl.getAbilities().flying);

		// R -- Thorn Volley / Vine Lash (Shift). Also runs the power-wide upkeep.
		AbilityHandlers.register(KEY, "thorn_volley", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (countdownReady(ctx, LASH_CD_RES, "shift") && afford(p, COST_LASH)) {
						ChloroMoves.vineLash(p);
					}
					return;
				}
				if (tapReady(ctx) && afford(p, COST_VOLLEY)) {
					ChloroMoves.thornVolley(p);
					ctx.triggerCooldown();
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				upkeep(ctx.player());
			}
		});

		// G -- Root Snare / Spore Cloud (Shift) / Thorn Sentry (hold)
		AbilityHandlers.register(KEY, "root_snare", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (countdownReady(ctx, SPORE_CD_RES, "shift") && afford(p, COST_SPORES)) {
						ChloroMoves.sporeCloud(p);
						startCountdown(p, SPORE_CD_RES, SPORE_CD);
					}
					return;
				}
				if (tapReady(ctx) && afford(p, COST_SNARE)) {
					if (ChloroMoves.rootSnare(p) > 0) {
						ctx.triggerCooldown();
					} else {
						ctx.actionBar("message.projecthero.chloro.no_target");
					}
				}
			}

			@Override
			public void onHoldMove(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (countdownReady(ctx, SENTRY_CD_RES, "hold") && afford(p, COST_SENTRY)) {
					ThornSentryEntity sentry = plantSentry(p);
					if (sentry == null) {
						ctx.actionBar("message.projecthero.plant.no_room");
						return;
					}
					spend(p, COST_SENTRY);
					set(p, "sentry_ticks", ThornSentryEntity.LIFETIME);
					startCountdown(p, SENTRY_CD_RES, SENTRY_CD);
					MutationVisuals.play(p, "p22.summon");
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ChloroMoves.sporeTick(ctx.player());
			}
		});

		// X -- Vine Grapple / Leaf Glide (Shift, toggle)
		AbilityHandlers.register(KEY, "vine_grapple", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (gliding(p)) {
						ChloroMoves.endGlide(p);
					} else if (afford(p, COST_GLIDE_OPEN)) {
						ChloroMoves.startGlide(p);
					}
					return;
				}
				if (tapReady(ctx) && afford(p, COST_GRAPPLE)) {
					if (ChloroMoves.vineGrapple(p)) {
						ctx.triggerCooldown();
					}
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ChloroMoves.glideTick(ctx.player());
			}
		});

		// Z -- Overgrowth / Treant (Shift)
		AbilityHandlers.register(KEY, "overgrowth", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (countdownReady(ctx, TREANT_CD_RES, "shift") && afford(p, COST_TREANT)) {
						if (ChloroMoves.summonTreant(p) != null) {
							startCountdown(p, TREANT_CD_RES, TREANT_CD);
						}
					}
					return;
				}
				if (res(p, "og_gather") > 0.5f) {
					return;
				}
				if (tapReady(ctx) && afford(p, COST_OVERGROWTH)) {
					ChloroMoves.startOvergrowth(p);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ChloroMoves.overgrowthTick(ctx.player())) {
					ctx.triggerCooldown();
				}
			}
		});

		// V -- Bloom Burst / Living Architecture (Shift)
		AbilityHandlers.register(KEY, "bloom_burst", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (countdownReady(ctx, ARCH_CD_RES, "shift")) {
						ChloroSurvival.livingArchitecture(p);
					}
					return;
				}
				if (tapReady(ctx) && afford(p, COST_BLOOM)) {
					ChloroMoves.bloomBurst(p);
					ctx.triggerCooldown();
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ChloroMoves.bloomTick(ctx.player());
				ChloroSurvival.brambleTick(ctx.player());
			}
		});

		// C -- Verdant Form (toggle) / Root Sense (Shift)
		AbilityHandlers.register(KEY, "verdant_form", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (countdownReady(ctx, SENSE_CD_RES, "shift") && afford(p, COST_SENSE)) {
						ChloroSurvival.rootSense(p);
						startCountdown(p, SENSE_CD_RES, SENSE_CD);
					}
					return;
				}
				ChloroMoves.setVerdant(p, !blessingOn(p));
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ChloroMoves.verdantTick(ctx.player());
				ChloroSurvival.senseTick(ctx.player());
			}
		});

		// hold G = Thorn Sentry
		com.projecthero.mod.hero.HoldTap.register(KEY, "root_snare");

		ChloroSurvival.register();

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				ChloroMoves.setVerdant(player, false);
				ChloroMoves.endGlide(player);
				ThornSentryEntity.removeFor(player);
				ChloroMoves.removeTreant(player);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, PlantManipulationHandlers::passiveTick);
	}

	// ================================================================ upkeep (every tick for an owner)

	private static void upkeep(ServerPlayer p) {
		seed(p);
		for (String cd : COUNTDOWNS) {
			BatchA.countDown(p, KEY, cd);
		}
		// another power took the keys: the glide and Verdant Form end
		Power active = ExperimentalPowers.getActive(p);
		if (active == null || !KEY.equals(active.key())) {
			if (gliding(p)) {
				ChloroMoves.endGlide(p);
			}
			if (blessingOn(p)) {
				ChloroMoves.setVerdant(p, false);
			}
		}
		float c = res(p, "chlorophyll");
		if (c < MAX_CHLOROPHYLL && p.isAlive()) {
			set(p, "chlorophyll", Math.min(MAX_CHLOROPHYLL, c + regenPerSecond(p) / 20.0f));
		}
	}

	private static void passiveTick(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		ChloroSurvival.fruitTick(player);
		if (player.tickCount % 100 == 0 && player.getHealth() < player.getMaxHealth() && nearVegetation(level, player.blockPosition())) {
			player.heal(1.0f);
		}
		// standing on, or against, living ground grants a small buff
		if (player.tickCount % 20 == 0 && (onNatureGround(level, player.blockPosition()) || againstNatureWall(level, player.blockPosition()))) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, false, false, false));
		}
		// Growth Aura: plants within 24 blocks get an occasional bonemeal-tier assist (random samples, never a full scan)
		if (player.tickCount % 100 == 0) {
			int budget = 20;
			BlockPos origin = player.blockPosition();
			BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
			for (int i = 0; i < 700 && budget > 0; i++) {
				int dx = level.random.nextInt(49) - 24;
				int dz = level.random.nextInt(49) - 24;
				if (dx * dx + dz * dz > 576) {
					continue;
				}
				bp.set(origin.getX() + dx, origin.getY() + level.random.nextInt(13) - 6, origin.getZ() + dz);
				if (!level.isLoaded(bp)) {
					continue;
				}
				BlockState state = level.getBlockState(bp);
				if ((state.getBlock() instanceof net.minecraft.world.level.block.CropBlock || state.is(net.minecraft.tags.BlockTags.SAPLINGS)
						|| state.getBlock() instanceof net.minecraft.world.level.block.StemBlock)
						&& state.getBlock() instanceof BonemealableBlock bm && bm.isValidBonemealTarget(level, bp, state)
						&& bm.isBonemealSuccess(level, level.random, bp, state)) {
					bm.performBonemeal(level, level.random, bp, state);
					budget--;
				}
			}
		}
	}

	// ================================================================ visuals / meters (from RevampBatchE)

	public static void registerVisuals() {
		MutationVisuals.registerFlag("p22.blessing", PlantManipulationHandlers::blessingOn);
		MutationVisuals.registerFlag("p22.overgrowth", PlantManipulationHandlers::overgrowthCharging);
		MutationVisuals.registerFlag("p22.glide", PlantManipulationHandlers::gliding);
	}

	public static void registerMeters() {
		int text = 0xFFD8F5C8;
		MutationMeters.register(new MutationMeters.Spec(KEY, "chlorophyll", MutationMeters.Kind.RESERVE, MutationMeters.Style.HAIRLINE,
				"Chlorophyll", MAX_CHLOROPHYLL, 0xFF6BD14A, true, true, false, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "ult_charge", MutationMeters.Kind.BUILD, MutationMeters.Style.SLAB, "Overgrowth",
				100, 0xFF5FCB4A, false, true));
		MutationMeters.register(new MutationMeters.Spec(KEY, "sentry_ticks", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Thorn Sentry", ThornSentryEntity.LIFETIME, 0xFF8FD16A, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "spore_ticks", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Spore Cloud", SPORE_TICKS, 0xFFB6C96A, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "bloom_ticks", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Bloom Burst", BLOOM_TICKS, 0xFFF29BC8, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "treant_ticks", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Treant", TreantEntity.LIFETIME, 0xFFB08A55, false, false, true, text));
		// the Shift moves' cooldown strips along the bottom of the R / G / Z / V / C boxes
		ShiftStrips.register(KEY, AbilitySlot.SLOT_1, LASH_CD_RES);
		ShiftStrips.register(KEY, AbilitySlot.SLOT_2, SPORE_CD_RES);
		ShiftStrips.register(KEY, AbilitySlot.SLOT_4, TREANT_CD_RES);
		ShiftStrips.register(KEY, AbilitySlot.SLOT_5, ARCH_CD_RES);
		ShiftStrips.register(KEY, AbilitySlot.SLOT_6, SENSE_CD_RES);
	}

	// ================================================================ Thorn Sentry (hold G)

	/**
	 * Plants a Thorn Sentry on the ground where the player is looking (up to 6 blocks), or at their feet. Any older sentry of
	 * theirs withers away first -- one at a time. Returns null if there is no room.
	 */
	public static ThornSentryEntity plantSentry(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		net.minecraft.world.phys.Vec3 spot;
		var hit = AbilityHelpers.raycastBlock(p, 6.0);
		if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getDirection() == net.minecraft.core.Direction.UP) {
			spot = hit.getLocation();
		} else {
			net.minecraft.world.phys.Vec3 look = p.getLookAngle();
			spot = p.position().add(new net.minecraft.world.phys.Vec3(look.x, 0, look.z).normalize().scale(1.5));
		}
		ThornSentryEntity.removeFor(p);
		ThornSentryEntity sentry = new ThornSentryEntity(PlantEntities.THORN_SENTRY, level);
		sentry.setOwner(p);
		sentry.moveTo(spot.x, spot.y, spot.z, p.getYRot(), 0.0f);
		if (!level.noCollision(sentry)) {
			sentry.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
		}
		level.addFreshEntity(sentry);
		level.sendParticles(ChloroParticles.LEAF, spot.x, spot.y + 0.8, spot.z, 14, 0.4, 0.5, 0.4, 0.02);
		level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
				Blocks.MOSS_BLOCK.defaultBlockState()), spot.x, spot.y + 0.1, spot.z, 24, 0.4, 0.1, 0.4, 0.1);
		AbilityHelpers.sound(p, net.minecraft.sounds.SoundEvents.ROOTED_DIRT_PLACE, 1.2f, 0.7f);
		AbilityHelpers.sound(p, net.minecraft.sounds.SoundEvents.AZALEA_LEAVES_PLACE, 1.0f, 0.9f);
		return sentry;
	}

	/** One Spore Cloud pulse centred on {@code c}. Public for the gametests. */
	public static void pulseSpores(ServerPlayer p, net.minecraft.world.phys.Vec3 c) {
		ChloroMoves.pulseSpores(p, c);
	}

	public static void clearSessionState() {
		ChloroSurvival.clearSessionState();
	}

	// ================================================================ ground checks

	/**
	 * True when the player is standing on living / natural ground -- grass, moss, dirt family, leaves, farmland, or any
	 * bush/plant. Used by {@link com.projecthero.mod.hero.power.HeroDamageRules} to cancel fall damage: a chlorokinetic lands
	 * softly on anything that grows.
	 */
	public static boolean onNatureGround(ServerLevel level, BlockPos feet) {
		var below = level.getBlockState(feet.below());
		if (below.is(net.minecraft.tags.BlockTags.DIRT) || below.is(net.minecraft.tags.BlockTags.LEAVES)
				|| below.is(Blocks.MOSS_BLOCK) || below.is(Blocks.MOSS_CARPET) || below.is(Blocks.FARMLAND)
				|| below.is(Blocks.HAY_BLOCK) || below.is(Blocks.PINK_PETALS) || below.is(Blocks.MANGROVE_ROOTS)
				|| below.getBlock() instanceof net.minecraft.world.level.block.BushBlock) {
			return true;
		}
		var at = level.getBlockState(feet);
		return at.getBlock() instanceof net.minecraft.world.level.block.BushBlock
				|| at.is(Blocks.SHORT_GRASS) || at.is(Blocks.TALL_GRASS) || at.is(Blocks.FERN)
				|| at.is(Blocks.LARGE_FERN) || at.is(Blocks.VINE) || at.is(Blocks.MOSS_CARPET);
	}

	/** True when a living-ground block forms a wall on any horizontal side of the player. */
	private static boolean againstNatureWall(ServerLevel level, BlockPos feet) {
		for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			var st = level.getBlockState(feet.relative(dir));
			if (st.is(net.minecraft.tags.BlockTags.DIRT) || st.is(Blocks.MOSS_BLOCK) || st.is(Blocks.GRASS_BLOCK)) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearVegetation(ServerLevel level, BlockPos center) {
		int count = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
			var st = level.getBlockState(p);
			if (st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.FLOWERS)
					|| st.is(Blocks.MOSS_BLOCK) || st.is(Blocks.MOSS_CARPET) || st.is(Blocks.SHORT_GRASS)
					|| st.is(Blocks.VINE) || st.is(Blocks.GRASS_BLOCK)) {
				if (++count >= 4) {
					return true;
				}
			}
		}
		return false;
	}
}
