package com.projecthero.mod.hero.power.p25;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.BatchBUtil;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 25 -- Water Manipulation (v0.16.5 rework, docs/V0165_POWERS_PROPOSAL.md section 12): a Katara / Aquaman water
 * bender on a 0-100 <b>Water</b> meter that refills 10 a second -- 25 in rain, 40 standing in water. Every move is a
 * real translucent water body ({@link WaterFxEntity}: a bolt, a whip, a prison sphere, a rolling wall, a geyser column,
 * a healing swirl) or a body drawn round the player from the synced {@code p25.*} flags (the Hydro Jet beam, the
 * orbiting shield, the surf wave) -- never water blocks, so nothing is left behind.
 *
 * <p>R Water Shot / Shift+R Hydro Jet (held). G Water Whip / Shift+G Water Prison. X Riptide / Shift+X Wave Ride. Z Tidal
 * Wave / Shift+Z Geyser. V Water Shield (held) / Shift+V Healing Water. C Aquatic Form (toggle) / Shift+C Draw Water. No H
 * / N. All six slots are HOLD, so the handlers own their tap / Shift / release logic; the two real cooldowns (Water
 * Prison 12 s, Healing Water 20 s) are the handlers' own timers.
 *
 * <p>Passives: water breathing, fast swimming (full water movement efficiency), 30% less fire damage, and fire on you
 * goes out at once.
 */
public final class WaterHandlers {
	public static final String KEY = "power_25_water_manipulation";

	// ---- the Water meter ----
	public static final String METER = "water";
	public static final float MAX_WATER = 100.0f;
	public static final float REGEN_PER_SECOND = 10.0f;
	public static final float REGEN_RAIN_PER_SECOND = 25.0f;
	public static final float REGEN_WATER_PER_SECOND = 40.0f;

	public static final float COST_SHOT = 8.0f;
	public static final float COST_JET_PER_SECOND = 12.0f;
	public static final float COST_WHIP = 10.0f;
	public static final float COST_PRISON = 35.0f;
	public static final float COST_RIPTIDE = 15.0f;
	public static final float COST_SURF = 25.0f;
	public static final float COST_TIDAL = 40.0f;
	public static final float COST_GEYSER = 20.0f;
	public static final float COST_SHIELD_PER_SECOND = 6.0f;
	public static final float COST_HEAL = 40.0f;
	public static final float COST_PLACE = 5.0f;
	public static final float DRAW_REFILL = 30.0f;

	// ---- R ----
	public static final float SHOT_DAMAGE = 12.0f;
	public static final float JET_DAMAGE_PER_SECOND = 8.0f;
	/** The jet's damage lands in two pulses a second (its own count, so hit invulnerability can't eat it). */
	public static final int JET_PULSE = 10;
	public static final double JET_RANGE = 16.0;
	private static final double JET_RADIUS = 0.7;

	// ---- G ----
	public static final double WHIP_RANGE = 14.0;
	public static final float WHIP_DAMAGE = 12.0f;
	public static final double PRISON_RANGE = 16.0;
	public static final int PRISON_TICKS = 4 * 20;
	public static final float PRISON_DAMAGE_PER_SECOND = 4.0f;
	public static final int PRISON_CD = 12 * 20;

	// ---- X ----
	public static final double RIPTIDE_SPEED = 3.0; // a Riptide III trident
	public static final float RIPTIDE_DAMAGE = 8.0f;
	public static final int RIPTIDE_SPIN_TICKS = 20;
	public static final int SURF_TICKS = 6 * 20;
	/** Wave Ride's speed (blocks a tick), applied on the riding client. */
	public static final double SURF_SPEED = 0.62;

	// ---- Z ----
	public static final float TIDAL_DAMAGE = 14.0f;
	public static final double GEYSER_RANGE = 24.0;
	public static final double GEYSER_HEIGHT = 10.0;

	// ---- V ----
	public static final double SHIELD_RADIUS = 2.3;
	public static final float HEAL_TOTAL = 16.0f; // 8 hearts over 4 s
	public static final double HEAL_RANGE = 6.0;
	public static final int HEAL_CD = 20 * 20;

	// ---- C ----
	public static final double AQUATIC_MELEE = 6.0;
	public static final double DRAW_RANGE = 12.0;

	// ---- passives ----
	public static final float FIRE_FACTOR = 0.7f;

	private static final ResourceLocation SWIM = com.projecthero.mod.ProjectHeroMod.id("water_swim");
	private static final ResourceLocation AQUATIC_MELEE_ID = com.projecthero.mod.ProjectHeroMod.id("water_aquatic_melee");
	private static final ResourceLocation SURF_STEP = com.projecthero.mod.ProjectHeroMod.id("water_surf_step");
	/** The pre-v0.16.5 Aquatic Form modifier, cleared out of old saves. */
	private static final ResourceLocation LEGACY_AQUATIC = com.projecthero.mod.ProjectHeroMod.id("aquatic_atk");
	/** Pre-v0.16.5 resources (the 575 supply, the spray, the charge, the shaping), zeroed in old saves. */
	private static final String[] LEGACY = { "water_supply", "spraying", "create_ticks", "prison_until", "tidal_start", "healed",
			"no_fall_until" };

	private WaterHandlers() {
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

	public static float water(ServerPlayer p) {
		seed(p);
		return res(p, METER);
	}

	/** For the gametests: the meter set directly. */
	public static void setWater(ServerPlayer p, float value) {
		seed(p);
		set(p, METER, Math.max(0, Math.min(MAX_WATER, value)));
	}

	public static boolean jetting(Player p) {
		return owns(p) && resAny(p, "jet") > 0.5f;
	}

	public static boolean shielding(Player p) {
		return owns(p) && resAny(p, "shield") > 0.5f;
	}

	public static boolean surfing(Player p) {
		return owns(p) && resAny(p, "surf") > 0.5f;
	}

	public static boolean aquaticActive(Player p) {
		return owns(p) && resAny(p, "aquatic") > 0.5f;
	}

	private static void seed(ServerPlayer p) {
		if (res(p, "seeded") < 0.5f) {
			set(p, "seeded", 1);
			set(p, METER, MAX_WATER);
		}
	}

	/** Spends Water; false (with the "out of water" line) if there isn't enough. */
	public static boolean spend(ServerPlayer p, float amount) {
		seed(p);
		float have = res(p, METER);
		if (have + 1.0e-3f < amount) {
			p.displayClientMessage(Component.translatable("message.projecthero.water.dry"), true);
			return false;
		}
		set(p, METER, Math.max(0.0f, have - amount));
		return true;
	}

	/** Adds Water (capped at 100); returns how much went in. */
	public static float refill(ServerPlayer p, float amount) {
		seed(p);
		float have = res(p, METER);
		float next = Math.min(MAX_WATER, have + amount);
		set(p, METER, next);
		return next - have;
	}

	/** The Water a second the meter refills right now: 40 standing in water, 25 in rain, otherwise 10. */
	public static float regenPerSecond(ServerPlayer p) {
		if (p.isInWater()) {
			return REGEN_WATER_PER_SECOND;
		}
		if (p.level().isRainingAt(p.blockPosition()) || p.level().isRainingAt(BlockPos.containing(p.getX(), p.getBoundingBox().maxY, p.getZ()))) {
			return REGEN_RAIN_PER_SECOND;
		}
		return REGEN_PER_SECOND;
	}

	private static boolean noBoss(LivingEntity t) {
		return !com.projecthero.mod.titanshifter.TitanCombat.isBoss(t) && t.getMaxHealth() <= 200.0f;
	}

	private static Vec3 mid(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	private static void cooling(AbilityContext ctx, String timer) {
		ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(res(ctx.player(), timer) / 20.0f)));
	}

	/** Puts out fire (fire blocks, lit campfires) in the cube of {@code radius} round {@code pos}. */
	public static void douse(ServerLevel level, BlockPos pos, int radius) {
		for (BlockPos b : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
			BlockState s = level.getBlockState(b);
			if (s.is(BlockTags.FIRE)) {
				level.removeBlock(b, false);
				level.playSound(null, b, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.6f);
			} else if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) {
				CampfireBlock.dowse(null, level, b, s);
				level.setBlock(b, s.setValue(CampfireBlock.LIT, false), 11);
			}
		}
	}

	// ================================================================ registration

	public static void register() {
		WaterEntities.register();

		// R -- Water Shot / Hydro Jet (Shift, held)
		AbilityHandlers.register(KEY, "water_shot", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				seed(p);
				if (p.isShiftKeyDown()) {
					startJet(ctx);
				} else {
					waterShot(p);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				endJet(ctx.player());
			}
		});

		// G -- Water Whip / Water Prison (Shift)
		AbilityHandlers.register(KEY, "water_whip", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				seed(p);
				if (p.isShiftKeyDown()) {
					if (res(p, "prison_cd") > 0.5f) {
						cooling(ctx, "prison_cd");
						return;
					}
					prison(ctx);
				} else {
					whip(ctx);
				}
			}
		});

		// X -- Riptide / Wave Ride (Shift; again to step off)
		AbilityHandlers.register(KEY, "riptide", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				seed(p);
				if (p.isShiftKeyDown()) {
					if (res(p, "surf") > 0.5f) {
						endSurf(p);
					} else {
						startSurf(p);
					}
				} else {
					riptide(p);
				}
			}
		});

		// Z -- Tidal Wave / Geyser (Shift)
		AbilityHandlers.register(KEY, "tidal_wave", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				seed(p);
				if (p.isShiftKeyDown()) {
					geyser(ctx);
				} else {
					tidalWave(p);
				}
			}
		});

		// V -- Water Shield (held) / Healing Water (Shift)
		AbilityHandlers.register(KEY, "water_shield", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				seed(p);
				if (p.isShiftKeyDown()) {
					if (res(p, "heal_cd") > 0.5f) {
						cooling(ctx, "heal_cd");
						return;
					}
					healingWater(p);
				} else {
					startShield(p);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				endShield(ctx.player());
			}
		});

		// C -- Aquatic Form (toggle) / Draw Water (Shift)
		AbilityHandlers.register(KEY, "aquatic_form", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				seed(p);
				if (p.isShiftKeyDown()) {
					drawWater(ctx);
				} else {
					setAquatic(p, res(p, "aquatic") < 0.5f);
					ctx.actionBar(res(p, "aquatic") > 0.5f ? "message.projecthero.water.aquatic_on" : "message.projecthero.water.aquatic_off");
				}
			}
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register(WaterHandlers::allowDamage);
		registerPassives();
	}

	// ================================================================ upkeep (every tick for an owner)

	private static void upkeep(ServerPlayer p) {
		seed(p);
		ServerLevel level = AbilityHelpers.level(p);
		for (String timer : new String[] { "prison_cd", "heal_cd", "nofall", "prison" }) {
			float v = res(p, timer);
			if (v > 0) {
				set(p, timer, v - 1);
			}
		}
		// the meter refills -- not while a held move (the jet, the shield) is pouring it out
		boolean held = res(p, "jet") > 0.5f || res(p, "shield") > 0.5f;
		float w = res(p, METER);
		if (w < MAX_WATER && !held) {
			set(p, METER, Math.min(MAX_WATER, w + regenPerSecond(p) / 20.0f));
		}
		ripAirTick(p);
		jetTick(p, level);
		shieldTick(p, level);
		surfTick(p, level);
		if (p.tickCount % 100 == 41) {
			BatchBUtil.retire(p, Powers.byKey(KEY), LEGACY);
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, LEGACY_AQUATIC);
		}
	}

	// ================================================================ R: Water Shot / Hydro Jet

	public static WaterBoltEntity waterShot(ServerPlayer p) {
		if (!spend(p, COST_SHOT)) {
			return null;
		}
		MutationVisuals.play(p, "p25.shot");
		AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8f, 1.5f);
		return WaterBoltEntity.fire(p);
	}

	private static void startJet(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, METER) < COST_JET_PER_SECOND / 20.0f * 4) {
			ctx.actionBar("message.projecthero.water.dry");
			return;
		}
		set(p, "jet", 1);
		set(p, "jet_t", 0);
		MutationVisuals.play(p, "p25.jet");
		AbilityHelpers.sound(p, SoundEvents.BUCKET_EMPTY, 1.0f, 1.4f);
	}

	public static void endJet(ServerPlayer p) {
		if (res(p, "jet") < 0.5f) {
			return;
		}
		set(p, "jet", 0);
		MutationVisuals.stopIf(p, "p25.jet");
	}

	private static void jetTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "jet") < 0.5f) {
			return;
		}
		float w = res(p, METER);
		float cost = COST_JET_PER_SECOND / 20.0f;
		if (!p.isAlive() || w < cost) {
			endJet(p);
			if (p.isAlive()) {
				p.displayClientMessage(Component.translatable("message.projecthero.water.dry"), true);
			}
			return;
		}
		set(p, METER, w - cost);
		MutationVisuals.ensure(p, "p25.jet");
		int t = (int) res(p, "jet_t") + 1;
		set(p, "jet_t", t);
		jetBeam(p, level, t % JET_PULSE == 0);
		if (t % 5 == 0) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WEATHER_RAIN, SoundSource.PLAYERS, 0.6f, 1.8f);
		}
	}

	/** Where the jet ends: the first block within 16 along the aim. */
	public static Vec3 jetEnd(Player p) {
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(JET_RANGE));
		BlockHitResult hit = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
	}

	/**
	 * One tick of the beam: everything hostile along it is pushed back (and doused); on a pulse (twice a second) each takes
	 * 4, for 8 a second. It cuts through every creature in line, up to the first block. Returns how many it touched.
	 */
	public static int jetBeam(ServerPlayer p, ServerLevel level, boolean pulse) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = jetEnd(p);
		double len = end.distanceTo(eye);
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(len * 0.5)), len * 0.5 + 1.5)) {
			Vec3 to = mid(e).subtract(eye);
			double along = to.dot(look);
			if (along < 0.3 || along > len + 0.6) {
				continue;
			}
			double off = to.subtract(look.scale(along)).length();
			if (off > JET_RADIUS + e.getBbWidth() * 0.6) {
				continue;
			}
			n++;
			if (pulse) {
				AbilityHelpers.hurtBurst(p, e, JET_DAMAGE_PER_SECOND * JET_PULSE / 20.0f);
			}
			e.clearFire();
			if (noBoss(e)) {
				Vec3 v = e.getDeltaMovement();
				e.setDeltaMovement(v.x * 0.6 + look.x * 0.28, Math.max(v.y, look.y * 0.2), v.z * 0.6 + look.z * 0.28);
				e.hurtMarked = true;
			}
		}
		if (p.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.SPLASH, end.x, end.y, end.z, 6, 0.25, 0.25, 0.25, 0.25);
		}
		if (p.tickCount % 4 == 0) {
			douse(level, BlockPos.containing(end.subtract(look.scale(0.3))), 1);
		}
		return n;
	}

	// ================================================================ G: Water Whip / Water Prison

	private static void whip(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!spend(p, COST_WHIP)) {
			return;
		}
		LivingEntity t = AbilityHelpers.raycastEntity(p, WHIP_RANGE);
		MutationVisuals.play(p, "p25.whip");
		AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.2f);
		if (t == null) {
			WaterWhipEntity.lash(p, null, AbilityHelpers.aimPoint(p, WHIP_RANGE));
			return;
		}
		WaterWhipEntity.lash(p, t, mid(t));
		whipHit(p, t);
	}

	/** The lash lands: 12, and the target is hauled in to a couple of blocks in front of you. */
	public static void whipHit(ServerPlayer p, LivingEntity t) {
		AbilityHelpers.hurtLands(p, t, WHIP_DAMAGE);
		t.clearFire();
		if (noBoss(t) && HeroTargets.canHarm(p, t)) {
			Vec3 to = p.position().subtract(t.position());
			double d = Math.sqrt(to.x * to.x + to.z * to.z);
			if (d > 2.5) {
				// enough speed to slide ~(d - 2) blocks under ground friction / air drag
				double speed = Math.min(2.2, 0.15 + (d - 2.0) * 0.16);
				Vec3 h = new Vec3(to.x / d, 0, to.z / d).scale(speed);
				t.setDeltaMovement(h.x, 0.32 + Math.max(0, to.y) * 0.08, h.z);
				t.hurtMarked = true;
				t.hasImpulse = true;
			}
		}
		AbilityHelpers.level(p).sendParticles(ParticleTypes.SPLASH, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 20, 0.3, 0.3, 0.3,
				0.15);
	}

	private static void prison(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, PRISON_RANGE);
		if (t == null || !AbilityHelpers.isValidGrabTarget(t, p) || !noBoss(t) || !HeroTargets.canHarm(p, t) || Squads.areAllies(p, t)) {
			ctx.actionBar("message.projecthero.water.no_target");
			return;
		}
		if (!spend(p, COST_PRISON)) {
			return;
		}
		imprison(p, t);
	}

	/** Seals {@code t} in a water sphere for 4 s (no checks; the caller has made them). */
	public static WaterPrisonEntity imprison(ServerPlayer p, LivingEntity t) {
		set(p, "prison_cd", PRISON_CD);
		set(p, "prison", PRISON_TICKS);
		MutationVisuals.play(p, "p25.prison");
		AbilityHelpers.sound(p, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.2f, 0.8f);
		return WaterPrisonEntity.trap(p, t);
	}

	// ================================================================ X: Riptide / Wave Ride

	/** Launches you like a Riptide trident (anywhere, wet or not), spinning through whatever you hit. */
	public static void riptide(ServerPlayer p) {
		if (!spend(p, COST_RIPTIDE)) {
			return;
		}
		endSurf(p);
		Vec3 look = p.getLookAngle();
		Vec3 v = look.scale(RIPTIDE_SPEED);
		if (p.onGround() && v.y < 0.4) {
			v = v.add(0, 0.42, 0); // vanilla lifts a grounded riptide off the floor first
		}
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(v));
		p.startAutoSpinAttack(RIPTIDE_SPIN_TICKS, RIPTIDE_DAMAGE, ItemStack.EMPTY);
		set(p, "nofall", 60);
		// v0.16.5 (user): no fall damage after a Riptide -- whatever the launch, until you are down again
		set(p, "rip_air", 1);
		set(p, "rip_t", 0);
		AbilityHelpers.level(p).sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.8, p.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
		p.level().playSound(null, p, SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	/** After a Riptide: falls do nothing until you are back on the ground (or in water), however long the flight. */
	private static void ripAirTick(ServerPlayer p) {
		if (res(p, "rip_air") < 0.5f) {
			return;
		}
		float t = res(p, "rip_t") + 1;
		set(p, "rip_t", t);
		p.resetFallDistance();
		if (t > 5 && (p.onGround() || p.isInWater() || p.onClimbable() || !p.isAlive())) {
			set(p, "rip_air", 0);
			set(p, "nofall", Math.max(res(p, "nofall"), 10)); // the landing tick itself
		}
	}

	private static void startSurf(ServerPlayer p) {
		if (!spend(p, COST_SURF)) {
			return;
		}
		set(p, "surf", SURF_TICKS);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, SURF_STEP, 0.65, AttributeModifier.Operation.ADD_VALUE);
		MutationVisuals.play(p, "p25.surf");
		AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.7f);
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.3, 0)); // the wave lifts you onto it
		}
	}

	public static void endSurf(ServerPlayer p) {
		var step = p.getAttribute(Attributes.STEP_HEIGHT);
		if (res(p, "surf") < 0.5f && (step == null || step.getModifier(SURF_STEP) == null)) {
			return;
		}
		set(p, "surf", 0);
		set(p, "nofall", Math.max(res(p, "nofall"), 30));
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, SURF_STEP);
		MutationVisuals.stopIf(p, "p25.surf");
	}

	private static void surfTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "surf");
		if (left < 0.5f) {
			return;
		}
		if (left <= 1.0f || !p.isAlive()) {
			endSurf(p);
			return;
		}
		set(p, "surf", left - 1);
		p.resetFallDistance();
		MutationVisuals.ensure(p, "p25.surf");
		knockAside(p, level);
		if (p.tickCount % 2 == 0) {
			Vec3 back = p.position().subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(0.8));
			level.sendParticles(ParticleTypes.SPLASH, back.x, p.getY() + 0.3, back.z, 6, 0.5, 0.1, 0.5, 0.2);
		}
	}

	/** The wave shoulders mobs out of the way: anything hostile just in front is thrown aside. Returns how many. */
	public static int knockAside(ServerPlayer p, ServerLevel level) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		Vec3 dir = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(dir.scale(1.2)).add(0, 0.8, 0), 1.9)) {
			if (Squads.areAllies(p, e) || !noBoss(e)) {
				continue;
			}
			double s = e.position().subtract(p.position()).dot(side) >= 0 ? 1.0 : -1.0;
			Vec3 push = side.scale(0.9 * s).add(dir.scale(0.35));
			e.setDeltaMovement(push.x, 0.38, push.z);
			e.hurtMarked = true;
			e.clearFire();
			n++;
		}
		return n;
	}

	// ================================================================ Z: Tidal Wave / Geyser

	public static TidalWaveEntity tidalWave(ServerPlayer p) {
		if (!spend(p, COST_TIDAL)) {
			return null;
		}
		MutationVisuals.play(p, "p25.tidal");
		AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.6f, 0.4f);
		return TidalWaveEntity.roll(p);
	}

	private static void geyser(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, GEYSER_RANGE);
		Vec3 at;
		if (t != null) {
			at = t.position();
		} else {
			BlockHitResult hit = AbilityHelpers.raycastBlock(p, GEYSER_RANGE);
			if (hit.getType() != HitResult.Type.BLOCK) {
				ctx.actionBar("message.projecthero.water.no_target");
				return;
			}
			BlockPos b = hit.getBlockPos();
			at = new Vec3(hit.getLocation().x, b.getY() + 1.0, hit.getLocation().z);
		}
		if (!spend(p, COST_GEYSER)) {
			return;
		}
		MutationVisuals.play(p, "p25.geyser");
		GeyserEntity.under(p, t, at);
	}

	// ================================================================ V: Water Shield / Healing Water

	private static void startShield(ServerPlayer p) {
		if (res(p, METER) < COST_SHIELD_PER_SECOND / 20.0f * 4) {
			p.displayClientMessage(Component.translatable("message.projecthero.water.dry"), true);
			return;
		}
		set(p, "shield", 1);
		MutationVisuals.play(p, "p25.shield");
		AbilityHelpers.sound(p, SoundEvents.BUCKET_FILL, 1.0f, 0.8f);
	}

	public static void endShield(ServerPlayer p) {
		if (res(p, "shield") < 0.5f) {
			return;
		}
		set(p, "shield", 0);
		MutationVisuals.stopIf(p, "p25.shield");
		shieldBurst(p);
	}

	private static void shieldTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "shield") < 0.5f) {
			return;
		}
		float w = res(p, METER);
		float cost = COST_SHIELD_PER_SECOND / 20.0f;
		if (!p.isAlive() || w < cost) {
			endShield(p);
			return;
		}
		set(p, METER, w - cost);
		MutationVisuals.ensure(p, "p25.shield");
		stopProjectiles(p, level);
		if (p.tickCount % 5 == 0) {
			douse(level, p.blockPosition(), 2);
		}
		if (p.tickCount % 20 == 0) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS, SoundSource.PLAYERS, 0.6f, 1.4f);
		}
	}

	/** Every projectile reaching the sphere (not your own) is swallowed by it. Returns how many. */
	public static int stopProjectiles(ServerPlayer p, ServerLevel level) {
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		int n = 0;
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(SHIELD_RADIUS + 2.5),
				pr -> pr.isAlive() && pr.getOwner() != p)) {
			Vec3 next = pr.position().add(pr.getDeltaMovement());
			if (pr.position().distanceTo(c) > SHIELD_RADIUS + 0.4 && next.distanceTo(c) > SHIELD_RADIUS + 0.4) {
				continue;
			}
			level.sendParticles(ParticleTypes.SPLASH, pr.getX(), pr.getY(), pr.getZ(), 14, 0.2, 0.2, 0.2, 0.15);
			level.sendParticles(ParticleTypes.BUBBLE, pr.getX(), pr.getY(), pr.getZ(), 6, 0.15, 0.15, 0.15, 0.05);
			level.playSound(null, pr.getX(), pr.getY(), pr.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.7f, 1.5f);
			pr.discard();
			n++;
		}
		return n;
	}

	/** You and every squadmate (and your tamed pets) within 6: 8 hearts over 4 s, and every harmful effect washed off. */
	public static int healingWater(ServerPlayer p) {
		if (!spend(p, COST_HEAL)) {
			return 0;
		}
		ServerLevel level = AbilityHelpers.level(p);
		set(p, "heal_cd", HEAL_CD);
		int healed = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(HEAL_RANGE))) {
			boolean ally = e == p || (e instanceof ServerPlayer other && Squads.areAllies(p, other)) || HeroTargets.isFriendlyPet(p, e);
			if (!ally || !e.isAlive() || e.distanceToSqr(p) > HEAL_RANGE * HEAL_RANGE) {
				continue;
			}
			cleanse(e);
			HealingWaterEntity.wrap(p, e, e == p ? (float) HEAL_RANGE : 0.0f);
			healed++;
		}
		MutationVisuals.play(p, "p25.heal");
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.3f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8f, 1.2f);
		return healed;
	}

	/** Washes every harmful effect off {@code e}. */
	public static void cleanse(LivingEntity e) {
		for (MobEffectInstance inst : new java.util.ArrayList<>(e.getActiveEffects())) {
			if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				e.removeEffect(inst.getEffect());
			}
		}
		e.clearFire();
	}

	// ================================================================ C: Aquatic Form / Draw Water

	public static void setAquatic(ServerPlayer p, boolean on) {
		set(p, "aquatic", on ? 1 : 0);
		if (on) {
			MutationVisuals.play(p, "p25.draw");
			AbilityHelpers.sound(p, SoundEvents.CONDUIT_ACTIVATE, 0.8f, 1.2f);
			AbilityHelpers.level(p).sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, AQUATIC_MELEE_ID);
			AbilityHelpers.sound(p, SoundEvents.CONDUIT_DEACTIVATE, 0.6f, 1.2f);
		}
	}

	private static void aquaticTick(ServerPlayer p) {
		if (res(p, "aquatic") < 0.5f) {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, AQUATIC_MELEE_ID);
			return;
		}
		refresh(p, MobEffects.DOLPHINS_GRACE);
		if (p.isInWater()) {
			var nv = p.getEffect(MobEffects.NIGHT_VISION);
			if (nv == null || (!nv.isInfiniteDuration() && nv.getDuration() < 220)) {
				p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false, false));
			}
			PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, AQUATIC_MELEE_ID, AQUATIC_MELEE, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, AQUATIC_MELEE_ID);
		}
	}

	/**
	 * Shift+C: looking at a water source (12 blocks), you pull it out of the world into the meter (+30, free); otherwise a
	 * source of water is placed where you aim (5 Water). Waterlogged blocks are drained / filled the same way.
	 */
	private static void drawWater(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(DRAW_RANGE));
		BlockHitResult fluid = level.clip(new ClipContext(eye, far, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, p));
		if (fluid.getType() == HitResult.Type.BLOCK && pullSource(p, level, fluid.getBlockPos())) {
			return;
		}
		BlockHitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		if (hit.getType() != HitResult.Type.BLOCK) {
			ctx.actionBar("message.projecthero.water.no_aim");
			return;
		}
		placeSource(ctx, hit);
	}

	/** Pulls the water source at {@code pos} out (+30). False if there is none there. */
	public static boolean pullSource(ServerPlayer p, ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!s.getFluidState().is(Fluids.WATER) || !s.getFluidState().isSource() || !level.mayInteract(p, pos)) {
			return false;
		}
		if (s.is(Blocks.WATER)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
		} else if (s.hasProperty(BlockStateProperties.WATERLOGGED)) {
			level.setBlock(pos, s.setValue(BlockStateProperties.WATERLOGGED, false), 11);
		} else {
			return false; // kelp / seagrass and the like: not a source you can lift
		}
		refill(p, DRAW_REFILL);
		MutationVisuals.play(p, "p25.draw");
		level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 30, 0.4, 0.3, 0.4, 0.2);
		Vec3 a = Vec3.atCenterOf(pos);
		AbilityHelpers.line(level, a, p.position().add(0, 1.2, 0), ParticleTypes.FALLING_WATER, 3.0);
		level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0f, 1.0f);
		p.displayClientMessage(Component.translatable("message.projecthero.water.drawn"), true);
		return true;
	}

	private static void placeSource(AbilityContext ctx, BlockHitResult hit) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		BlockPos at = hit.getBlockPos();
		BlockState s = level.getBlockState(at);
		boolean log = s.hasProperty(BlockStateProperties.WATERLOGGED) && !s.getValue(BlockStateProperties.WATERLOGGED);
		if (!log) {
			at = at.relative(hit.getDirection());
			s = level.getBlockState(at);
			if (!s.isAir() && !s.canBeReplaced(Fluids.WATER)) {
				ctx.actionBar("message.projecthero.water.no_aim");
				return;
			}
		}
		if (!level.mayInteract(p, at) || !p.mayUseItemAt(at, hit.getDirection(), ItemStack.EMPTY)) {
			return;
		}
		if (level.dimensionType().ultraWarm()) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 2.0f);
			ctx.actionBar("message.projecthero.water.evaporates");
			return;
		}
		if (!spend(p, COST_PLACE)) {
			return;
		}
		if (log) {
			level.setBlock(at, s.setValue(BlockStateProperties.WATERLOGGED, true), 11);
			level.scheduleTick(at, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		} else {
			if (!s.isAir()) {
				level.destroyBlock(at, true);
			}
			level.setBlock(at, Blocks.WATER.defaultBlockState(), 11);
		}
		MutationVisuals.play(p, "p25.shot");
		level.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 20, 0.3, 0.2, 0.3, 0.1);
		level.playSound(null, at, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	// ================================================================ damage

	/** The shield swallows projectiles outright. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		return !(entity instanceof ServerPlayer sp && source.is(DamageTypeTags.IS_PROJECTILE) && shielding(sp));
	}

	/**
	 * The water body's damage rule, read by {@link com.projecthero.mod.hero.power.HeroDamageRules}: a negative result means
	 * "immune" (falls just after a Riptide or a Wave Ride; projectiles into the shield), otherwise a multiplier -- 0.7 for
	 * fire and lava.
	 */
	public static float damageFactor(ServerPlayer p, DamageSource source) {
		if (source.is(DamageTypeTags.IS_FALL) && (res(p, "nofall") > 0.5f || res(p, "surf") > 0.5f || res(p, "rip_air") > 0.5f)) {
			p.resetFallDistance();
			return -1.0f;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE) && shielding(p)) {
			return -1.0f;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return FIRE_FACTOR;
		}
		return 1.0f;
	}

	// ================================================================ passives

	/** Keeps a hidden passive effect up, re-sending it only when it runs low (not every tick). */
	private static void refresh(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
		MobEffectInstance cur = p.getEffect(effect);
		if (cur == null || (!cur.isInfiniteDuration() && cur.getDuration() < 15)) {
			p.addEffect(new MobEffectInstance(effect, 60, 0, false, false, false));
		}
	}

	private static void registerPassives() {
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, owned) -> {
			if (!owned) {
				PowerToggles.clearModifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM);
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, AQUATIC_MELEE_ID);
				PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, SURF_STEP);
				Power power = Powers.byKey(KEY);
				if (power != null && ExperimentalPowers.owns(player, power)) {
					// still owned while forget() runs its teardown: drop the held / timed states too
					endJet(player);
					endShield(player);
					endSurf(player);
					set(player, "aquatic", 0);
				}
				MutationVisuals.stop(player);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			upkeep(player);
			// water breathing, fast swimming, and fire on you goes out at once
			refresh(player, MobEffects.WATER_BREATHING);
			PowerToggles.modifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM, 1.0, AttributeModifier.Operation.ADD_VALUE);
			if (player.isOnFire()) {
				player.clearFire();
				if (player.tickCount % 10 == 0) {
					AbilityHelpers.level(player).sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.3,
							0.5, 0.3, 0.02);
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
							0.5f, 1.6f);
				}
			}
			aquaticTick(player);
		});
	}

	// ================================================================ synced visuals + HUD (called from RevampBatchB)

	public static void registerVisuals() {
		MutationVisuals.registerFlag("p25.jet", WaterHandlers::jetting);
		MutationVisuals.registerFlag("p25.shield", WaterHandlers::shielding);
		MutationVisuals.registerFlag("p25.surf", WaterHandlers::surfing);
		MutationVisuals.registerFlag("p25.aquatic", WaterHandlers::aquaticActive);
	}

	public static void registerMeters() {
		int text = 0xFFBFE3FF;
		MutationMeters.register(new MutationMeters.Spec(KEY, METER, MutationMeters.Kind.RESERVE, MutationMeters.Style.HAIRLINE, "Water",
				MAX_WATER, 0xFF3A8DFF, true, true, false, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "surf", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE, "Wave Ride",
				SURF_TICKS, 0xFF7FC4FF, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "prison", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Water Prison", PRISON_TICKS, 0xFF7FC4FF, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "prison_cd", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Prison cooldown", PRISON_CD, 0xFF5A7A9A, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "heal_cd", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Healing cooldown", HEAL_CD, 0xFF5A7A9A, false, false, true, text));
		for (String legacy : LEGACY) {
			BatchBUtil.hideMeter(KEY, legacy);
		}
	}

	/** The splash a shield makes as it lets go. */
	private static void shieldBurst(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		level.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.9, 0.8, 0.9, 0.1);
		level.sendParticles(ParticleTypes.FALLING_WATER, p.getX(), p.getY() + 1.6, p.getZ(), 16, 0.9, 0.4, 0.9, 0.0);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_SPLASH, 0.7f, 1.3f);
	}
}
