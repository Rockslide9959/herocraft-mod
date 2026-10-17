package com.projecthero.mod.hero.power.p05;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.BatchBScheduler;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationMeters.Kind;
import com.projecthero.mod.hero.visual.MutationMeters.Spec;
import com.projecthero.mod.hero.visual.MutationMeters.Style;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Power 05 -- Geokinesis (v0.16.5 rework, docs/V0165_POWERS_PROPOSAL.md section 9): an earthbender on an <b>Earth</b>
 * meter (0-100) that refills 15 a second standing on natural ground (stone, dirt, sand, gravel -- {@link GeoTerrain#natural})
 * and 5 a second anywhere else.
 *
 * <p>R Rock Shot / Shift+R Boulder Toss (hold to lift). G Earth Grip / Shift+G Sinkhole. X Rock Surf / Shift+X Tectonic
 * Pillar. Z Earthquake / Shift+Z Earth Spikes. V Stone Wall / Shift+V Earth Armor (toggle). C Tunnel (hold) / Shift+C
 * Seismic Sense. No H / N. All six slots are HOLD, so the handlers own their tap / Shift / release logic.
 *
 * <p>Passives: 50% knockback resistance on the ground, stone and dirt break twice as fast ({@link GeoBareHands}), no fall
 * damage onto natural ground. Every rock, hand, pillar, spike and ripple is drawn as a model in the texture of the block it
 * came from ({@link GeoRockEntity}, {@link GeoBoulderEntity}, {@link GeoFormEntity}); the only real terrain changes are
 * the Stone Wall and the Sinkhole (temporary, in {@link GeoTerrain}'s ledger, always put back) and the Tunnel (mining:
 * you keep the blocks). All of them respect build permission.
 */
public final class GeokinesisHandlers {
	public static final String KEY = "power_05_geokinesis";

	// ---- Earth ----
	public static final float MAX_EARTH = 100.0f;
	public static final float REGEN_NATURAL = 15.0f / 20.0f;
	public static final float REGEN_ELSEWHERE = 5.0f / 20.0f;

	public static final float COST_ROCK = 8.0f;
	public static final float COST_BOULDER = 30.0f;
	public static final float COST_GRIP = 15.0f;
	public static final float COST_SINKHOLE = 35.0f;
	public static final float COST_SURF = 20.0f;
	public static final float COST_PILLAR = 15.0f;
	public static final float COST_QUAKE = 30.0f;
	public static final float COST_SPIKES = 25.0f;
	public static final float COST_WALL = 15.0f;
	public static final float ARMOR_PER_SECOND = 4.0f;
	public static final float TUNNEL_PER_SECOND = 3.0f;
	public static final float COST_SEISMIC = 20.0f;

	// ---- R ----
	public static final float ROCK_DAMAGE = 12.0f;
	public static final float BOULDER_DAMAGE = 26.0f;
	/** A lifted boulder is thrown by itself after this long. */
	public static final int BOULDER_MAX_HOLD = 8 * 20;
	// ---- G ----
	public static final double TARGET_RANGE = 20.0;
	public static final int GRIP_TICKS = 3 * 20;
	public static final int SINKHOLE_DEPTH = 3;
	public static final int SINKHOLE_CD = 15 * 20;
	/** A buried creature's pocket is refilled after this long at most, even if it is still down there. */
	public static final int SINKHOLE_POCKET_MAX = 5 * 60 * 20;
	/** Ticks the hole stays open before the ground closes over it. */
	public static final int SINKHOLE_CLOSE_TICKS = 10;
	/** Ticks the target takes to sink to the bottom of the hole. */
	public static final int SINKHOLE_SINK_TICKS = 8;
	// ---- X ----
	/** v0.16.5 (user): Rock Surf has no time limit -- a toggle that drains Earth while you ride (like the Ice Slide). */
	public static final float COST_SURF_PER_SECOND = 1.0f;
	/** ...and a 4-block step assist: the slab rides straight up ledges up to 4 blocks tall. */
	public static final double SURF_STEP_HEIGHT = 4.0;
	private static final net.minecraft.resources.ResourceLocation SURF_STEP = com.projecthero.mod.ProjectHeroMod.id("geo_surf_step");
	public static final double SURF_SPEED = 0.78;
	/** Vertical launch that carries a creature ~12 blocks up (vanilla gravity and drag). */
	public static final double PILLAR_LAUNCH = 1.49;
	// ---- Z ----
	public static final float QUAKE_DAMAGE = 14.0f;
	public static final double QUAKE_RADIUS = 10.0;
	public static final int QUAKE_SLOW_TICKS = 4 * 20;
	public static final float SPIKE_DAMAGE = 18.0f;
	public static final double SPIKE_LENGTH = 12.0;
	// ---- V ----
	public static final int WALL_WIDTH = 5;
	public static final int WALL_HEIGHT = 4;
	public static final int WALL_TICKS = 20 * 20;
	public static final float ARMOR_DAMAGE_FACTOR = 0.5f;
	public static final double ARMOR_SPEED = -0.2;
	// ---- C ----
	public static final int SEISMIC_TICKS = 10 * 20;
	public static final double SEISMIC_RANGE = 32.0;
	/** The Tunnel bores the next 3x3 slice this often. */
	public static final int TUNNEL_INTERVAL = 2;
	public static final double TUNNEL_REACH = 4.0;

	// ---- passives ----
	public static final double GROUND_KNOCKBACK_RESIST = 0.5;

	private static final ResourceLocation GROUND_KB = com.projecthero.mod.ProjectHeroMod.id("geo_ground_kb");
	private static final ResourceLocation ARMOR_SPD = com.projecthero.mod.ProjectHeroMod.id("geo_armor_speed");
	/** Pre-v0.16.5 modifiers, cleared out of old saves. */
	private static final ResourceLocation[] LEGACY = { com.projecthero.mod.ProjectHeroMod.id("earth_armor_kb"),
			com.projecthero.mod.ProjectHeroMod.id("earth_armor_slow"), com.projecthero.mod.ProjectHeroMod.id("earth_armor_atk") };

	private static ItemStack tunnelPick;

	private GeokinesisHandlers() {
	}

	// ================================================================ shared helpers / queries

	private static Power power() {
		return Powers.byKey(KEY);
	}

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

	public static float earth(ServerPlayer p) {
		seed(p);
		return res(p, "earth");
	}

	public static boolean armorOn(Player p) {
		return owns(p) && resAny(p, "armor") > 0.5f;
	}

	public static boolean surfing(Player p) {
		return owns(p) && resAny(p, "surf") > 0.5f;
	}

	public static boolean tunnelling(Player p) {
		return owns(p) && resAny(p, "tunnel") > 0.5f;
	}

	public static boolean lifting(Player p) {
		return owns(p) && resAny(p, "boulder_id") > 0.5f;
	}

	/** Seismic Sense is running (the client lights up every creature within 32 for this player alone). */
	public static boolean sensing(Player p) {
		return owns(p) && resAny(p, "seismic") > 0.5f;
	}

	private static void seed(ServerPlayer p) {
		if (res(p, "seeded") < 0.5f) {
			set(p, "seeded", 1);
			set(p, "earth", MAX_EARTH);
		}
	}

	/** Refuses (with a message) when the meter cannot pay {@code cost}. */
	private static boolean canPay(AbilityContext ctx, float cost) {
		ServerPlayer p = ctx.player();
		seed(p);
		if (res(p, "earth") + 1.0e-3f < cost) {
			ctx.actionBar("message.projecthero.geo.low_earth", String.format(java.util.Locale.ROOT, "%.0f", cost));
			return false;
		}
		return true;
	}

	public static void spend(ServerPlayer p, float amount) {
		seed(p);
		set(p, "earth", Math.max(0.0f, res(p, "earth") - amount));
	}

	private static boolean noBoss(LivingEntity t) {
		return !com.projecthero.mod.titanshifter.TitanCombat.isBoss(t) && t.getMaxHealth() <= 200.0f;
	}

	private static BlockParticleOption dust(BlockState s) {
		return new BlockParticleOption(ParticleTypes.BLOCK, s);
	}

	/** The surface cell (first air above solid ground) at {@code (x, z)} within a few blocks of height {@code y}. */
	static BlockPos surfaceCell(ServerLevel level, double x, double y, double z) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		int top = Mth.floor(y) + 2;
		for (int yy = top; yy >= top - 5; yy--) {
			BlockPos bp = new BlockPos(bx, yy, bz);
			BlockState here = level.getBlockState(bp);
			BlockState below = level.getBlockState(bp.below());
			if ((here.isAir() || here.canBeReplaced()) && here.getFluidState().isEmpty() && !below.isAir()
					&& below.isFaceSturdy(level, bp.below(), Direction.UP)) {
				return bp;
			}
		}
		return null;
	}

	/** The block a model torn from {@code cell}'s ground looks like: the block itself, else the hero's ground. */
	private static BlockState surfaceBlock(ServerLevel level, BlockPos cell, ServerPlayer fallback) {
		if (cell != null) {
			BlockState s = level.getBlockState(cell.below());
			if (!s.isAir() && s.getFluidState().isEmpty() && s.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) {
				return s;
			}
		}
		return GeoTerrain.groundBlock(fallback);
	}

	/**
	 * What a raised wall (and the armour plates) is built of: the stone you stand on; the stone it would be for dirt, ores
	 * and anything that would fall (sand becomes sandstone).
	 */
	public static BlockState buildBlock(BlockState ground) {
		if (ground.is(Blocks.RED_SAND)) {
			return Blocks.RED_SANDSTONE.defaultBlockState();
		}
		if (ground.is(BlockTags.SAND) || ground.is(Blocks.SANDSTONE)) {
			return Blocks.SANDSTONE.defaultBlockState();
		}
		if (ground.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) || ground.is(Blocks.COBBLED_DEEPSLATE)) {
			return Blocks.DEEPSLATE.defaultBlockState();
		}
		if (ground.is(Blocks.DEEPSLATE) || ground.is(Blocks.TUFF) || ground.is(Blocks.GRANITE) || ground.is(Blocks.DIORITE)
				|| ground.is(Blocks.ANDESITE) || ground.is(Blocks.CALCITE) || ground.is(BlockTags.TERRACOTTA)
				|| ground.is(Blocks.RED_SANDSTONE)) {
			return ground.getBlock().defaultBlockState();
		}
		return Blocks.STONE.defaultBlockState();
	}

	// ================================================================ registration

	public static void register() {
		// R -- Rock Shot / Boulder Toss (Shift, hold to lift). Also runs the power-wide upkeep.
		AbilityHandlers.register(KEY, "rock_shot", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (res(p, "boulder_id") < 0.5f && canPay(ctx, COST_BOULDER)) {
						liftBoulder(p);
					}
					return;
				}
				if (canPay(ctx, COST_ROCK)) {
					rockShot(p);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				upkeep(ctx.player(), ctx.level());
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "boulder_id") > 0.5f) {
					set(p, "boulder_release", 1); // thrown as soon as it is up overhead
				}
			}
		});

		// G -- Earth Grip / Sinkhole (Shift)
		AbilityHandlers.register(KEY, "earth_grip", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (res(p, "sink_cd") > 0.5f) {
						cooling(ctx, "sink_cd");
						return;
					}
					if (canPay(ctx, COST_SINKHOLE)) {
						sinkhole(ctx);
					}
					return;
				}
				if (canPay(ctx, COST_GRIP)) {
					earthGrip(ctx);
				}
			}
		});

		// X -- Rock Surf (press again to step off) / Tectonic Pillar (Shift)
		AbilityHandlers.register(KEY, "rock_surf", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (canPay(ctx, COST_PILLAR)) {
						tectonicPillar(ctx);
					}
					return;
				}
				if (res(p, "surf") > 0.5f) {
					endSurf(p);
				} else if (canPay(ctx, COST_SURF)) {
					startSurf(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				surfTick(ctx.player(), ctx.level());
			}
		});

		// Z -- Earthquake / Earth Spikes (Shift)
		AbilityHandlers.register(KEY, "earthquake", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (canPay(ctx, COST_SPIKES)) {
						earthSpikes(p);
					}
					return;
				}
				if (canPay(ctx, COST_QUAKE)) {
					earthquake(p);
				}
			}
		});

		// V -- Stone Wall / Earth Armor (Shift, toggle)
		AbilityHandlers.register(KEY, "stone_wall", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (res(p, "armor") > 0.5f) {
						setArmor(p, false);
					} else if (canPay(ctx, 1.0f)) {
						setArmor(p, true);
					}
					return;
				}
				if (canPay(ctx, COST_WALL)) {
					stoneWall(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				armorTick(ctx.player());
			}
		});

		// C -- Tunnel (held) / Seismic Sense (Shift)
		AbilityHandlers.register(KEY, "tunnel", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (canPay(ctx, COST_SEISMIC)) {
						seismicSense(p, ctx.level());
					}
					return;
				}
				if (canPay(ctx, 0.5f)) {
					set(p, "tunnel", 1);
					set(p, "tunnel_t", 0);
					MutationVisuals.play(p, "p05.tunnel");
					AbilityHelpers.sound(p, SoundEvents.GRINDSTONE_USE, 0.8f, 0.5f);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				tunnelTick(ctx.player(), ctx.level());
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				endTunnel(ctx.player());
			}
		});

		// temporary blocks (the wall, the sinkhole) never drop anything when broken -- they were never really yours
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (level instanceof ServerLevel sl && GeoTerrain.isTemp(sl, pos) && !state.isAir()) {
				GeoTerrain.restoreNow(sl, pos); // puts back what was there -- and the break is cancelled, so nothing drops
				return false;
			}
			return true;
		});
		ServerTickEvents.END_SERVER_TICK.register(GeoTerrain::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> GeoTerrain.restoreAll());

		registerPassives();
	}

	private static void cooling(AbilityContext ctx, String timer) {
		ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(res(ctx.player(), timer) / 20.0f)));
	}

	// ================================================================ upkeep (every tick for an owner)

	private static void upkeep(ServerPlayer p, ServerLevel level) {
		seed(p);
		for (String timer : new String[] { "sink_cd", "seismic", "nofall" }) {
			float v = res(p, timer);
			if (v > 0) {
				set(p, timer, v - 1);
			}
		}
		// Earth refills -- fast on natural ground, slowly anywhere else -- except while it drains (armour, tunnel)
		float e = res(p, "earth");
		if (e < MAX_EARTH && res(p, "armor") < 0.5f && res(p, "tunnel") < 0.5f && res(p, "surf") < 0.5f) {
			set(p, "earth", Math.min(MAX_EARTH, e + (GeoTerrain.onNaturalGround(p) ? REGEN_NATURAL : REGEN_ELSEWHERE)));
		}
		boulderTick(p, level);
		if (p.tickCount % 100 == 37) {
			dropLegacyState(p);
		}
	}

	/** Pre-v0.16.5 Earth Armor / Earth Swim leftovers are inert now: clear them out of old saves. */
	private static void dropLegacyState(ServerPlayer p) {
		for (ResourceLocation id : LEGACY) {
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, id);
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, id);
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, id);
		}
		Power power = power();
		if (power != null && ExperimentalPowers.getResource(p, power, "earthswim_on") > 0.5f) {
			ExperimentalPowers.setResource(p, power, "earthswim_on", 0, 1);
			ExperimentalPowers.setResource(p, power, "earthswim_until", 0, 1e12f);
			p.noPhysics = false;
		}
	}

	// ================================================================ R: Rock Shot / Boulder Toss

	/** The ground a rock is torn out of: a step in front of you (where your aim meets the ground nearby). */
	private static BlockPos tearCell(ServerPlayer p, double ahead) {
		Vec3 flat = flatLook(p);
		Vec3 at = p.position().add(flat.scale(ahead));
		return surfaceCell(AbilityHelpers.level(p), at.x, p.getY(), at.z);
	}

	static Vec3 flatLook(Player p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : f.normalize();
	}

	/** R: a stone rips up out of the ground in front of you, rises to your hand, and flies (12). */
	public static GeoRockEntity rockShot(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		BlockPos cell = tearCell(p, 1.4);
		BlockState block = surfaceBlock(level, cell, p);
		Vec3 from = cell != null ? Vec3.atBottomCenterOf(cell).add(0, -0.2, 0) : p.position().add(flatLook(p).scale(1.0));
		GeoRockEntity rock = GeoRockEntity.rockShot(level, p, ROCK_DAMAGE, block, from);
		level.addFreshEntity(rock);
		spend(p, COST_ROCK);
		MutationVisuals.play(p, "p05.rock");
		AbilityHelpers.sound(p, block.getSoundType().getBreakSound(), 1.0f, 0.7f);
		return rock;
	}

	/** Shift+R: a boulder heaves up out of the ground in front of you and is held overhead until you let go. */
	public static GeoBoulderEntity liftBoulder(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		BlockPos cell = tearCell(p, 2.2);
		BlockState block = surfaceBlock(level, cell, p);
		Vec3 from = cell != null ? Vec3.atBottomCenterOf(cell).add(0, -0.8, 0) : p.position().add(flatLook(p).scale(2.0));
		GeoBoulderEntity b = GeoBoulderEntity.lift(level, p, BOULDER_DAMAGE, block, from);
		level.addFreshEntity(b);
		spend(p, COST_BOULDER);
		set(p, "boulder_id", b.getId());
		set(p, "boulder_t", 0);
		set(p, "boulder_release", 0);
		MutationVisuals.play(p, "p05.lift");
		AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 0.6f, 0.5f);
		level.playSound(null, from.x, from.y, from.z, block.getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.4f, 0.5f);
		return b;
	}

	private static GeoBoulderEntity heldBoulder(ServerPlayer p, ServerLevel level) {
		int id = (int) res(p, "boulder_id");
		return id != 0 && level.getEntity(id) instanceof GeoBoulderEntity b && b.isAlive() && b.held() ? b : null;
	}

	private static void boulderTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "boulder_id") < 0.5f) {
			return;
		}
		GeoBoulderEntity b = heldBoulder(p, level);
		if (b == null || !p.isAlive()) {
			if (b != null) {
				b.discard();
			}
			clearBoulder(p);
			return;
		}
		float t = res(p, "boulder_t") + 1;
		set(p, "boulder_t", t);
		set(p, "lift", b.liftFraction() * 100.0f);
		MutationVisuals.ensure(p, "p05.lift");
		if (b.fullyLifted() && (res(p, "boulder_release") > 0.5f || t > BOULDER_MAX_HOLD)) {
			tossBoulder(p, b);
		}
	}

	/** Throws the held boulder now (for the gametests and the release). */
	public static void tossBoulder(ServerPlayer p, GeoBoulderEntity b) {
		b.toss();
		clearBoulder(p);
		MutationVisuals.play(p, "p05.heave");
	}

	private static void clearBoulder(ServerPlayer p) {
		set(p, "boulder_id", 0);
		set(p, "boulder_t", 0);
		set(p, "boulder_release", 0);
		set(p, "lift", 0);
		MutationVisuals.stopIf(p, "p05.lift");
	}

	// ================================================================ G: Earth Grip / Sinkhole

	/** The aimed creature within 20 blocks, or null (with a message). */
	private static LivingEntity aimedTarget(AbilityContext ctx) {
		LivingEntity t = AbilityHelpers.raycastEntity(ctx.player(), TARGET_RANGE);
		if (t == null || !HeroTargets.canHarm(ctx.player(), t) || Squads.areAllies(ctx.player(), t)) {
			ctx.actionBar("message.projecthero.geo.no_target");
			return null;
		}
		return t;
	}

	/** The ground block right under {@code e} (within 1.5 blocks), or null when it stands on nothing. */
	private static BlockPos groundUnder(LivingEntity e) {
		for (int dy = 0; dy <= 2; dy++) {
			BlockPos bp = BlockPos.containing(e.getX(), e.getY() - 0.2 - dy * 0.65, e.getZ());
			BlockState s = e.level().getBlockState(bp);
			if (!s.isAir() && s.isFaceSturdy(e.level(), bp, Direction.UP)) {
				return bp;
			}
		}
		return null;
	}

	/** G: two stone hands burst out of the ground round the target's legs and hold it fast for 3 s. */
	private static void earthGrip(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity t = aimedTarget(ctx);
		if (t == null) {
			return;
		}
		BlockPos ground = groundUnder(t);
		if (ground == null) {
			ctx.actionBar("message.projecthero.geo.not_grounded");
			return;
		}
		grip(p, t, ground);
	}

	public static GeoFormEntity grip(ServerPlayer p, LivingEntity t, BlockPos ground) {
		ServerLevel level = AbilityHelpers.level(p);
		spend(p, COST_GRIP);
		BlockState block = level.getBlockState(ground);
		Vec3 at = new Vec3(t.getX(), ground.getY() + 1.0, t.getZ());
		GeoFormEntity hands = GeoFormEntity.create(level, GeoFormEntity.GRIP, p, at, block, Math.max(0.6f, t.getBbWidth()), GRIP_TICKS);
		// bosses are held back hard (Slowness IV) instead of pinned to the spot
		hands.target(t).pin(noBoss(t));
		if (!noBoss(t)) {
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, GRIP_TICKS, 3);
		}
		level.addFreshEntity(hands);
		if (t instanceof Mob m) {
			m.getNavigation().stop();
		}
		MutationVisuals.play(p, "p05.grip");
		level.sendParticles(dust(block), t.getX(), at.y + 0.1, t.getZ(), 24, 0.5, 0.1, 0.5, 0.12);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.4f, 0.6f);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), block.getSoundType().getPlaceSound(), SoundSource.PLAYERS, 1.2f, 0.6f);
		return hands;
	}

	/**
	 * Shift+G: the ground under the target (natural ground, 3 deep, the target's whole footprint) drops it 3 blocks, then
	 * closes over it -- the ground is put back as it was, with the creature inside, suffocating until it digs out. The air
	 * pocket at its feet is refilled once it is out (or dead). No bosses.
	 */
	private static void sinkhole(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity t = aimedTarget(ctx);
		if (t == null) {
			return;
		}
		if (!noBoss(t)) {
			ctx.actionBar("message.projecthero.geo.too_big");
			return;
		}
		if (!sink(p, t)) {
			ctx.actionBar("message.projecthero.geo.hard_ground");
		}
	}

	/** The columns of ground under {@code t}'s footprint. */
	private static List<BlockPos> footprint(LivingEntity t) {
		List<BlockPos> cols = new ArrayList<>();
		AABB box = t.getBoundingBox();
		int y = Mth.floor(t.getY() + 0.01);
		for (int x = Mth.floor(box.minX + 0.01); x <= Mth.floor(box.maxX - 0.01); x++) {
			for (int z = Mth.floor(box.minZ + 0.01); z <= Mth.floor(box.maxZ - 0.01); z++) {
				cols.add(new BlockPos(x, y, z));
			}
		}
		return cols;
	}

	/** Sinks {@code t} 3 blocks into the ground and closes it over. Returns false (and changes nothing) if it can't. */
	public static boolean sink(ServerPlayer p, LivingEntity t) {
		ServerLevel level = AbilityHelpers.level(p);
		if (!t.onGround() && groundUnder(t) == null) {
			return false;
		}
		List<BlockPos> cols = footprint(t);
		AABB caster = p.getBoundingBox().inflate(0.3);
		for (BlockPos c : cols) {
			for (int d = 1; d <= SINKHOLE_DEPTH; d++) {
				BlockPos bp = c.below(d);
				if (!GeoTerrain.diggable(level, bp) || !GeoTerrain.canEdit(p, level, bp) || GeoTerrain.isTemp(level, bp)
						|| caster.intersects(new AABB(bp).expandTowards(0, 4, 0))) {
					return false;
				}
			}
		}
		BlockState look = level.getBlockState(cols.get(0).below());
		// the bottom cell is the creature's pocket (waits for it); the two above close over it after a moment
		for (BlockPos c : cols) {
			GeoTerrain.pocket(level, c.below(SINKHOLE_DEPTH), t, SINKHOLE_POCKET_MAX, SINKHOLE_CLOSE_TICKS + 10);
			for (int d = 1; d < SINKHOLE_DEPTH; d++) {
				GeoTerrain.bury(level, c.below(d), SINKHOLE_CLOSE_TICKS);
			}
		}
		// it is dragged down the hole over a moment (so the drop reads), then the ground closes over it
		double startY = t.getY();
		double x = t.getX();
		double z = t.getZ();
		BatchBScheduler.schedule(level, age -> {
			if (!t.isAlive() || age >= SINKHOLE_SINK_TICKS) {
				return false;
			}
			float f = (age + 1) / (float) SINKHOLE_SINK_TICKS;
			t.teleportTo(x, startY - SINKHOLE_DEPTH * f * f, z);
			t.setDeltaMovement(Vec3.ZERO);
			t.hurtMarked = true;
			return true;
		});
		t.setDeltaMovement(0, -0.6, 0);
		t.hurtMarked = true;
		AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, SINKHOLE_CLOSE_TICKS + 10, 9);
		if (t instanceof Mob m) {
			m.getNavigation().stop();
		}
		spend(p, COST_SINKHOLE);
		set(p, "sink_cd", SINKHOLE_CD);
		Vec3 top = new Vec3(t.getX(), cols.get(0).getY(), t.getZ());
		level.addFreshEntity(GeoFormEntity.create(level, GeoFormEntity.SINK, p, top, look, Math.max(1.0f, t.getBbWidth() + 0.6f), 26));
		level.sendParticles(dust(look), top.x, top.y + 0.2, top.z, 40, 0.7, 0.2, 0.7, 0.15);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, top.x, top.y, top.z, 4, 0.6, 0.2, 0.6, 0.01);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.4f, 0.4f);
		MutationVisuals.play(p, "p05.sinkhole");
		return true;
	}

	// ================================================================ X: Rock Surf / Tectonic Pillar

	private static void startSurf(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!p.onGround()) {
			ctx.actionBar("message.projecthero.geo.not_grounded");
			return;
		}
		spend(p, COST_SURF);
		BlockState under = buildBlock(GeoTerrain.groundBlock(p));
		set(p, "surf", 1);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, SURF_STEP, SURF_STEP_HEIGHT - 0.6, AttributeModifier.Operation.ADD_VALUE);
		set(p, "surf_air", 0);
		set(p, "surf_block", Block.getId(under) + 1);
		ctx.level().sendParticles(dust(under), p.getX(), p.getY() + 0.1, p.getZ(), 30, 0.6, 0.1, 0.6, 0.12);
		AbilityHelpers.sound(p, SoundEvents.STONE_PLACE, 1.0f, 0.5f);
		AbilityHelpers.sound(p, under.getSoundType().getBreakSound(), 1.2f, 0.6f);
		MutationVisuals.play(p, "p05.surf");
	}

	public static void endSurf(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, SURF_STEP);
		if (res(p, "surf") < 0.5f) {
			return;
		}
		set(p, "surf", 0);
		MutationVisuals.stopIf(p, "p05.surf");
		set(p, "nofall", 40);
		if (p.level() instanceof ServerLevel sl) {
			BlockState s = Block.stateById(Math.max(0, (int) res(p, "surf_block") - 1));
			sl.sendParticles(dust(s.isAir() ? Blocks.STONE.defaultBlockState() : s), p.getX(), p.getY() + 0.1, p.getZ(), 24, 0.5,
					0.1, 0.5, 0.1);
		}
		AbilityHelpers.sound(p, SoundEvents.STONE_BREAK, 0.9f, 0.8f);
	}

	/**
	 * Rock Surf: carried along the way you face on a slab of rock, stepping straight up ledges up to 4 blocks, until a second
	 * press, the Earth runs out (1 a second, no refill while you ride), water, flight, or 1.5 s in the air. v0.16.5 (user):
	 * no time limit any more.
	 */
	private static void surfTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "surf") < 0.5f) {
			return;
		}
		float air = p.onGround() ? 0 : res(p, "surf_air") + 1;
		if (air != res(p, "surf_air")) {
			set(p, "surf_air", air);
		}
		if (air > 30 || p.isInWater() || p.getAbilities().flying || p.isPassenger() || !p.isAlive()) {
			endSurf(p);
			return;
		}
		if (res(p, "earth") <= 0.01f) {
			endSurf(p);
			p.displayClientMessage(Component.translatable("message.projecthero.geo.tunnel_out"), true);
			return;
		}
		spend(p, COST_SURF_PER_SECOND / 20.0f);
		Vec3 flat = flatLook(p);
		double vy = p.getDeltaMovement().y;
		BlockPos ahead = BlockPos.containing(p.getX() + flat.x * 0.9, p.getY() + 0.1, p.getZ() + flat.z * 0.9);
		if (p.onGround() && !level.getBlockState(ahead).getCollisionShape(level, ahead).isEmpty()
				&& level.getBlockState(ahead.above()).getCollisionShape(level, ahead.above()).isEmpty()
				&& level.getBlockState(p.blockPosition().above(2)).getCollisionShape(level, p.blockPosition().above(2)).isEmpty()) {
			vy = 0.46; // hop the step
		}
		AbilityHelpers.launchSelf(p, new Vec3(flat.x * SURF_SPEED, vy, flat.z * SURF_SPEED));
		p.resetFallDistance();
		MutationVisuals.ensure(p, "p05.surf");
		BlockState s = Block.stateById(Math.max(0, (int) res(p, "surf_block") - 1));
		if (p.tickCount % 2 == 0 && p.onGround()) {
			level.sendParticles(dust(GeoTerrain.groundBlock(p)), p.getX() - flat.x * 0.8, p.getY() + 0.05, p.getZ() - flat.z * 0.8, 4,
					0.3, 0.05, 0.3, 0.06);
		}
		if (p.tickCount % 8 == 0) {
			level.playSound(null, p.blockPosition(), s.getSoundType().getStepSound(), SoundSource.PLAYERS, 0.7f, 0.6f);
		}
	}

	/**
	 * Shift+X: a pillar of rock erupts under the aimed creature (20 blocks) -- or under you, aiming at nothing -- and launches
	 * it ~12 blocks straight up.
	 */
	private static void tectonicPillar(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, TARGET_RANGE);
		if (t != null && (!HeroTargets.canHarm(p, t) || Squads.areAllies(p, t))) {
			t = null;
		}
		LivingEntity rider = t != null ? t : p;
		BlockPos ground = groundUnder(rider);
		if (ground == null) {
			ctx.actionBar("message.projecthero.geo.not_grounded");
			return;
		}
		pillar(p, rider, ground);
	}

	public static GeoFormEntity pillar(ServerPlayer p, LivingEntity rider, BlockPos ground) {
		ServerLevel level = AbilityHelpers.level(p);
		spend(p, COST_PILLAR);
		BlockState block = level.getBlockState(ground);
		Vec3 base = new Vec3(rider.getX(), ground.getY() + 1.0, rider.getZ());
		level.addFreshEntity(GeoFormEntity.create(level, GeoFormEntity.PILLAR, p, base, block, Math.max(1.0f, rider.getBbWidth() + 0.5f), 50));
		if (rider == p) {
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x * 0.3, PILLAR_LAUNCH, p.getDeltaMovement().z * 0.3));
			set(p, "nofall", 8 * 20);
			MutationVisuals.play(p, "p05.pillar_ride");
		} else {
			rider.setDeltaMovement(rider.getDeltaMovement().x * 0.3, PILLAR_LAUNCH, rider.getDeltaMovement().z * 0.3);
			rider.hurtMarked = true;
			rider.hasImpulse = true;
			rider.setOnGround(false);
			MutationVisuals.play(p, "p05.pillar");
		}
		level.sendParticles(dust(block), base.x, base.y + 0.1, base.z, 40, 0.8, 0.1, 0.8, 0.15);
		level.playSound(null, base.x, base.y, base.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.playSound(null, base.x, base.y, base.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 0.8f);
		return null;
	}

	// ================================================================ Z: Earthquake / Earth Spikes

	/** Z: the ground shakes in a 10-block ring round you -- a ripple runs out, and everything it passes takes 14. */
	public static GeoFormEntity earthquake(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		spend(p, COST_QUAKE);
		BlockState block = GeoTerrain.groundBlock(p);
		GeoFormEntity wave = GeoFormEntity.create(level, GeoFormEntity.QUAKE, p, p.position(), block, (float) QUAKE_RADIUS, 22);
		level.addFreshEntity(wave);
		MutationVisuals.play(p, "p05.quake");
		level.sendParticles(dust(block), p.getX(), p.getY() + 0.2, p.getZ(), 60, 1.5, 0.1, 1.5, 0.2);
		level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.4f);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.25f, 0.4f);
		level.playSound(null, p.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.6f, 0.4f);
		return wave;
	}

	/** The quake wave reaching a creature: 14, a stagger (a jolt up and a moment's stun) and Slowness II for 4 s. */
	public static void quakeHit(ServerPlayer p, LivingEntity e, Vec3 centre) {
		if (AbilityHelpers.hurtLands(p, e, QUAKE_DAMAGE)) {
			AbilityHelpers.knockbackFrom(e, centre, 0.35);
			AbilityHelpers.push(e, new Vec3(0, 0.38, 0));
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 15, 5); // the stagger
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, QUAKE_SLOW_TICKS, 1);
			if (e instanceof Mob m) {
				m.getNavigation().stop();
			}
		}
	}

	/** Shift+Z: a line of stone spikes stabs up along the ground, 12 blocks the way you face -- 18 to each creature. */
	public static void earthSpikes(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		spend(p, COST_SPIKES);
		Vec3 flat = flatLook(p);
		Vec3 origin = p.position();
		Set<Integer> hit = new HashSet<>();
		MutationVisuals.play(p, "p05.spikes");
		AbilityHelpers.sound(p, SoundEvents.POINTED_DRIPSTONE_LAND, 1.2f, 0.6f);
		BatchBScheduler.schedule(level, age -> {
			for (int k = 0; k < 2; k++) {
				double d = 1.5 + age * 2 + k;
				if (d > SPIKE_LENGTH + 0.5) {
					return false;
				}
				spikeAt(p, level, origin, flat, d, hit);
			}
			return true;
		});
	}

	/** One spike of the line, {@code d} blocks out. Returns how many it hit. */
	public static int spikeAt(ServerPlayer p, ServerLevel level, Vec3 origin, Vec3 flat, double d, Set<Integer> hit) {
		Vec3 side = new Vec3(-flat.z, 0, flat.x);
		Vec3 pt = origin.add(flat.scale(d)).add(side.scale((level.random.nextDouble() - 0.5) * 0.5));
		BlockPos cell = surfaceCell(level, pt.x, origin.y, pt.z);
		if (cell == null) {
			return 0;
		}
		BlockState block = level.getBlockState(cell.below());
		Vec3 c = new Vec3(pt.x, cell.getY(), pt.z);
		GeoFormEntity spike = GeoFormEntity.create(level, GeoFormEntity.SPIKE, p, c, block, 0.8f + level.random.nextFloat() * 0.5f, 26);
		spike.setYRot(level.random.nextFloat() * 360.0f);
		level.addFreshEntity(spike);
		level.sendParticles(dust(block), c.x, c.y + 0.3, c.z, 10, 0.3, 0.3, 0.3, 0.1);
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(0, 0.8, 0), 1.4)) {
			if (hit.add(e.getId()) && AbilityHelpers.hurtLands(p, e, SPIKE_DAMAGE)) {
				AbilityHelpers.push(e, new Vec3(0, 0.7, 0));
				n++;
			}
		}
		if (((int) d) % 3 == 0) {
			level.playSound(null, cell, SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.PLAYERS, 1.0f, 0.6f + level.random.nextFloat() * 0.3f);
		}
		return n;
	}

	// ================================================================ V: Stone Wall / Earth Armor

	/** V: a 5-wide, 4-tall wall of the local stone rises in front of you, row by row; it crumbles after 20 s. */
	private static void stoneWall(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!AbilityHelpers.canGrief() || !p.mayBuild()) {
			ctx.actionBar("message.projecthero.geo.no_build");
			return;
		}
		if (wall(p) == 0) {
			ctx.actionBar("message.projecthero.geo.no_room");
		}
	}

	/** Raises the wall (the first row now, the rest over the next three ticks). Returns the bottom row's block count. */
	public static int wall(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		BlockState block = buildBlock(GeoTerrain.groundBlock(p));
		Vec3 flat = flatLook(p);
		// snap to the nearest axis so the wall is a clean straight line of blocks
		Direction facing = Direction.getNearest(flat.x, 0, flat.z);
		Direction along = facing.getClockWise();
		BlockPos centre = BlockPos.containing(p.position().add(flat.scale(2.6)));
		BlockPos baseCell = surfaceCell(level, centre.getX() + 0.5, p.getY(), centre.getZ() + 0.5);
		BlockPos base = baseCell != null ? baseCell : centre;
		List<BlockPos> row0 = new ArrayList<>();
		for (int i = -WALL_WIDTH / 2; i <= WALL_WIDTH / 2; i++) {
			BlockPos col = base.relative(along, i);
			BlockPos s = surfaceCell(level, col.getX() + 0.5, base.getY(), col.getZ() + 0.5);
			row0.add(s != null && Math.abs(s.getY() - base.getY()) <= 1 ? s : col);
		}
		long start = level.getGameTime();
		int placed = 0;
		for (BlockPos c : row0) {
			if (wallCell(p, level, c, block, 0, start)) {
				placed++;
			}
		}
		if (placed == 0) {
			return 0;
		}
		spend(p, COST_WALL);
		MutationVisuals.play(p, "p05.wall");
		AbilityHelpers.sound(p, SoundEvents.STONE_PLACE, 1.2f, 0.5f);
		level.playSound(null, base, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
		BatchBScheduler.schedule(level, age -> {
			int row = age + 1;
			if (row >= WALL_HEIGHT) {
				return false;
			}
			for (BlockPos c : row0) {
				wallCell(p, level, c.above(row), block, row, start);
			}
			level.playSound(null, base.above(row), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 0.8f, 0.6f + row * 0.1f);
			return true;
		});
		return placed;
	}

	/** One block of the wall; a row's blocks stand 20 s, the top row crumbling first. */
	private static boolean wallCell(ServerPlayer p, ServerLevel level, BlockPos c, BlockState block, int row, long start) {
		if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(c)).isEmpty()) {
			return false; // never inside anybody
		}
		long restoreAt = start + WALL_TICKS + (WALL_HEIGHT - 1 - row) * 3L;
		int ttl = (int) Math.max(1, restoreAt - level.getGameTime());
		boolean ok = GeoTerrain.placeTemp(p, level, c, block, ttl);
		if (ok) {
			level.sendParticles(dust(block), c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.08);
		}
		return ok;
	}

	public static void setArmor(ServerPlayer p, boolean on) {
		if (on) {
			BlockState block = buildBlock(GeoTerrain.groundBlock(p));
			set(p, "armor", 1);
			set(p, "armor_block", Block.getId(block) + 1);
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, ARMOR_SPD, ARMOR_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			MutationVisuals.play(p, "p05.armor_up");
			AbilityHelpers.level(p).sendParticles(dust(block), p.getX(), p.getY() + 1.0, p.getZ(), 50, 0.5, 0.9, 0.5, 0.1);
			AbilityHelpers.sound(p, SoundEvents.DEEPSLATE_PLACE, 1.2f, 0.6f);
			AbilityHelpers.sound(p, SoundEvents.ANVIL_PLACE, 0.4f, 0.6f);
		} else {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, ARMOR_SPD);
			if (res(p, "armor") < 0.5f) {
				return;
			}
			set(p, "armor", 0);
			BlockState s = Block.stateById(Math.max(0, (int) res(p, "armor_block") - 1));
			AbilityHelpers.level(p).sendParticles(dust(s.isAir() ? Blocks.STONE.defaultBlockState() : s), p.getX(), p.getY() + 1.0,
					p.getZ(), 40, 0.5, 0.9, 0.5, 0.12);
			AbilityHelpers.sound(p, SoundEvents.STONE_BREAK, 1.2f, 0.7f);
		}
	}

	private static void armorTick(ServerPlayer p) {
		if (res(p, "armor") < 0.5f) {
			return;
		}
		if (!p.isAlive()) {
			setArmor(p, false);
			return;
		}
		spend(p, ARMOR_PER_SECOND / 20.0f);
		if (res(p, "earth") <= 0.01f) {
			setArmor(p, false);
			p.displayClientMessage(Component.translatable("message.projecthero.geo.armor_out"), true);
		}
	}

	// ================================================================ C: Tunnel / Seismic Sense

	public static void endTunnel(ServerPlayer p) {
		if (res(p, "tunnel") < 0.5f) {
			return;
		}
		set(p, "tunnel", 0);
		MutationVisuals.stopIf(p, "p05.tunnel");
	}

	private static void tunnelTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "tunnel") < 0.5f) {
			return;
		}
		float t = res(p, "tunnel_t") + 1;
		set(p, "tunnel_t", t);
		if (!p.isAlive() || t > 60 * 20) {
			endTunnel(p);
			return;
		}
		spend(p, TUNNEL_PER_SECOND / 20.0f);
		if (res(p, "earth") <= 0.01f) {
			endTunnel(p);
			p.displayClientMessage(Component.translatable("message.projecthero.geo.tunnel_out"), true);
			return;
		}
		MutationVisuals.ensure(p, "p05.tunnel");
		if (((int) t) % TUNNEL_INTERVAL == 0) {
			boreSlice(p, level);
		}
	}

	/** The 3x3 slice of the tunnel {@code d} blocks ahead, across the look direction's main axis. */
	public static List<BlockPos> slice(ServerPlayer p, int d) {
		Vec3 look = p.getLookAngle();
		List<BlockPos> cells = new ArrayList<>(9);
		Vec3 centre;
		if (Math.abs(look.y) > 0.75) {
			// straight down / up: a 3x3 floor or ceiling slice
			int y = look.y < 0 ? Mth.floor(p.getY() - 0.5) - (d - 1) : Mth.floor(p.getY() + p.getBbHeight() + 0.5) + (d - 1);
			BlockPos c = BlockPos.containing(p.getX(), y, p.getZ());
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					cells.add(c.offset(dx, 0, dz));
				}
			}
			return cells;
		}
		Direction facing = Direction.getNearest(look.x, 0, look.z);
		Direction side = facing.getClockWise();
		// a walking-height tunnel: centred a block above the feet, tilted up or down a block with the look
		centre = p.position().add(0, 1.0 + Math.round(look.y * d * 1.2), 0);
		BlockPos c = BlockPos.containing(centre).relative(facing, d);
		for (int s = -1; s <= 1; s++) {
			for (int h = -1; h <= 1; h++) {
				cells.add(c.relative(side, s).above(h));
			}
		}
		return cells;
	}

	/** Bores out the nearest 3x3 slice ahead with any stone in it (up to 4 blocks); you keep the blocks. */
	public static int boreSlice(ServerPlayer p, ServerLevel level) {
		for (int d = 1; d <= TUNNEL_REACH; d++) {
			List<BlockPos> cells = slice(p, d);
			List<BlockPos> dig = new ArrayList<>();
			for (BlockPos c : cells) {
				if (GeoTerrain.diggable(level, c) && !GeoTerrain.isTemp(level, c) && GeoTerrain.canEdit(p, level, c)) {
					dig.add(c);
				}
			}
			if (dig.isEmpty()) {
				continue;
			}
			for (BlockPos c : dig) {
				mineKeep(p, level, c);
			}
			BlockPos mid = cells.get(4);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, mid.getX() + 0.5, mid.getY() + 0.5, mid.getZ() + 0.5, 1, 0.5, 0.5, 0.5,
					0.01);
			return dig.size();
		}
		return 0;
	}

	/** Breaks a block as an iron pickaxe would and puts the drops straight into your inventory (the rest at your feet). */
	private static void mineKeep(ServerPlayer p, ServerLevel level, BlockPos pos) {
		if (tunnelPick == null) {
			tunnelPick = new ItemStack(Items.IRON_PICKAXE);
		}
		BlockState s = level.getBlockState(pos);
		List<ItemStack> drops = Block.getDrops(s, level, pos, null, p, tunnelPick);
		level.levelEvent(2001, pos, Block.getId(s));
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		for (ItemStack st : drops) {
			if (!p.getInventory().add(st) && !st.isEmpty()) {
				Block.popResource(level, p.blockPosition(), st);
			}
		}
	}

	/** Shift+C: every creature and player within 32 lights up through walls for 10 s -- on your screen only. */
	public static int seismicSense(ServerPlayer p, ServerLevel level) {
		spend(p, COST_SEISMIC);
		set(p, "seismic", SEISMIC_TICKS);
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SEISMIC_RANGE),
				e -> e != p && e.isAlive() && !e.isSpectator())) {
			if (e.distanceToSqr(p) <= SEISMIC_RANGE * SEISMIC_RANGE) {
				n++;
			}
		}
		MutationVisuals.play(p, "p05.seismic");
		BlockState ground = GeoTerrain.groundBlock(p);
		for (int i = 0; i < 40; i++) {
			double a = i / 40.0 * Math.PI * 2;
			level.sendParticles(dust(ground), p.getX() + Math.cos(a) * 1.5, p.getY() + 0.1, p.getZ() + Math.sin(a) * 1.5, 1, 0, 0, 0, 0.05);
		}
		level.playSound(null, p.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.4f, 0.6f);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 0.8f);
		p.displayClientMessage(Component.translatable("message.projecthero.geo.seismic", n), true);
		return n;
	}

	// ================================================================ damage

	/**
	 * Read by {@link com.projecthero.mod.hero.power.HeroDamageRules}: a negative result means "immune" (a fall onto natural
	 * ground, or the landing after your own pillar / surf), otherwise a multiplier -- Earth Armor's 0.5.
	 */
	public static float damageFactor(ServerPlayer p, DamageSource source) {
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
			BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
			if (GeoTerrain.natural(p.level().getBlockState(below)) || res(p, "nofall") > 0.5f) {
				p.resetFallDistance();
				return -1.0f;
			}
		}
		if (armorOn(p) && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) {
			return ARMOR_DAMAGE_FACTOR;
		}
		return 1.0f;
	}

	// ================================================================ passives

	private static void registerPassives() {
		PowerPassives.register(KEY, (player, owned) -> {
			if (!owned) {
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, GROUND_KB);
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, ARMOR_SPD);
				Power power = power();
				if (power != null && ExperimentalPowers.owns(player, power)) {
					// still owned while forget() runs its teardown: drop the transient states too
					setArmor(player, false);
					endSurf(player);
					endTunnel(player);
					if (player.level() instanceof ServerLevel sl) {
						GeoBoulderEntity b = heldBoulder(player, sl);
						if (b != null) {
							b.discard();
						}
					}
					clearBoulder(player);
					set(player, "seismic", 0);
				}
				MutationVisuals.stop(player);
			}
		});
		PowerPassives.registerTick(KEY, player -> {
			// 50% knockback resistance while your feet are on the ground
			if (player.onGround()) {
				PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, GROUND_KB, GROUND_KNOCKBACK_RESIST,
						AttributeModifier.Operation.ADD_VALUE);
			} else {
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, GROUND_KB);
			}
			// respawn-safe re-apply of the armour's slow
			if (res(player, "armor") > 0.5f) {
				PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, ARMOR_SPD, ARMOR_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			} else {
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, ARMOR_SPD);
			}
		});
	}

	// ================================================================ synced visuals + HUD (called from RevampBatchB)

	private static float blockValue(Player p, String name) {
		return owns(p) ? Math.max(0, resAny(p, name) - 1) : 0;
	}

	public static void registerVisuals() {
		MutationVisuals.registerFlag("p05.armor", GeokinesisHandlers::armorOn);
		MutationVisuals.registerValue("p05.armor_block", p -> armorOn(p) ? blockValue(p, "armor_block") : 0);
		MutationVisuals.registerFlag("p05.surf", GeokinesisHandlers::surfing);
		MutationVisuals.registerValue("p05.surf_block", p -> surfing(p) ? blockValue(p, "surf_block") : 0);
		MutationVisuals.registerFlag("p05.tunnel", GeokinesisHandlers::tunnelling);
		MutationVisuals.registerFlag("p05.lifting", GeokinesisHandlers::lifting);
	}

	public static void registerMeters() {
		int text = 0xFFE6D6B8;
		MutationMeters.register(new Spec(KEY, "earth", Kind.RESERVE, Style.HAIRLINE, "Earth", MAX_EARTH, 0xFFB08A55, true, true, false, text));
		MutationMeters.register(new Spec(KEY, "lift", Kind.BUILD, Style.HAIRLINE, "Boulder", 100, 0xFF9C8E7A, false, false, true, text));
		MutationMeters.register(new Spec(KEY, "armor", Kind.BUILD, Style.HAIRLINE, "EARTH ARMOR", 1.0f, 0xFF8A7A66, false, false, true, text));
		MutationMeters.register(new Spec(KEY, "seismic", Kind.TIMER, Style.HAIRLINE, "Seismic Sense", SEISMIC_TICKS, 0xFFC9B27A, false, false, true, text));
		MutationMeters.register(new Spec(KEY, "sink_cd", Kind.TIMER, Style.HAIRLINE, "Sinkhole", SINKHOLE_CD, 0xFF9A7A5A, false, false, true, text));
	}

	/** For the gametests: the Earth meter set directly. */
	public static void setEarth(ServerPlayer p, float value) {
		seed(p);
		set(p, "earth", Math.max(0, Math.min(MAX_EARTH, value)));
	}

	/**
	 * Earth Swim was removed in the v0.16.5 rework: never true now. Kept so the shared flight / noclip code that still asks
	 * (DirectionalFlight, LocalPlayerMixin) needs no change.
	 */
	public static boolean earthSwimming(Player p) {
		return false;
	}

	/** A guaranteed-air point ~2.5 blocks along the player's aim (used by Crystalkinesis). */
	public static Vec3 airSpawn(ServerLevel level, ServerPlayer p, Vec3 dir) {
		Vec3 eye = p.getEyePosition();
		for (double d = 2.5; d >= 0.6; d -= 0.5) {
			Vec3 c = eye.add(dir.scale(d));
			if (level.getBlockState(BlockPos.containing(c)).isAir()) {
				return c;
			}
		}
		return eye;
	}

	/** True when the entity is within Seismic Sense range of {@code viewer} (the client glow rule, shared for tests). */
	public static boolean sensedBy(Player viewer, Entity e) {
		return e != viewer && e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
				&& e.distanceToSqr(viewer) <= SEISMIC_RANGE * SEISMIC_RANGE;
	}
}
