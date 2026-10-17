package com.projecthero.mod.hero.power.p09;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.projecthero.mod.attachment.ModAttachments;
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
import com.projecthero.mod.hero.revamp.BatchBUtil;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 09 -- Cryokinesis (v0.16.5 rework, docs/V0165_POWERS_PROPOSAL.md section 9): an Iceman / Frozone ice caster on a
 * <b>Cold</b> meter, built around {@link FrostStacks}: ice hits add Frost, five Frost freezes a creature solid for 2.5 s,
 * and Shatter cashes the ice in.
 *
 * <p>Cold (0-100): moves spend it; it refills 15 a second (25 in a snowy biome or standing on snow / ice) while neither
 * the Freeze Beam nor Frozen Armor is draining it. A move you cannot afford is refused.
 *
 * <p>R Ice Bolt / Shift+R Ice Shards. G Freeze Beam (held) / Shift+G Ice Prison. X Ice Slide / Shift+X Ice Pillar.
 * Z Glacier Spikes / Shift+Z Absolute Zero. V Glacier Wall / Shift+V Frozen Armor (toggle). C Ice Blade (toggle) /
 * Shift+C Shatter. No H / N. All six slots are HOLD, so the handlers own their Shift / release logic and the few real
 * cooldowns (Ice Prison 12 s, Absolute Zero 20 s) are their own timers.
 *
 * <p>Passives: water freezes under your feet, immune to freezing and powder snow, no slipping on ice.
 */
public final class CryokinesisHandlers {
	public static final String KEY = "power_09_cryokinesis";

	// ---- Cold ----
	public static final float MAX_COLD = 100.0f;
	public static final float COLD_REGEN_PER_TICK = 15.0f / 20.0f;
	public static final float COLD_REGEN_SNOW_PER_TICK = 25.0f / 20.0f;

	public static final float COST_BOLT = 8.0f;
	public static final float COST_SHARDS = 15.0f;
	public static final float COST_BEAM_PER_SECOND = 10.0f;
	public static final float COST_PRISON = 30.0f;
	public static final float COST_SLIDE = 20.0f;
	public static final float COST_PILLAR = 15.0f;
	public static final float COST_SPIKES = 25.0f;
	public static final float COST_ZERO = 50.0f;
	public static final float COST_WALL = 15.0f;
	public static final float COST_ARMOR_PER_SECOND = 3.0f;
	public static final float COST_SHATTER = 20.0f;

	// ---- R: Ice Bolt / Ice Shards ----
	public static final float BOLT_DAMAGE = 10.0f;
	public static final int BOLT_FROST = 1;
	private static final float BOLT_SPEED = 1.6f;
	public static final int SHARD_COUNT = 7;
	public static final float SHARD_DAMAGE = 4.0f;
	public static final int SHARD_FROST = 1;
	private static final float SHARD_SPEED = 1.7f;

	// ---- G: Freeze Beam / Ice Prison ----
	public static final double BEAM_RANGE = 20.0;
	/** One Frost every half second on the beam's target. */
	public static final int BEAM_FROST_TICKS = 10;
	public static final double PRISON_RANGE = 16.0;
	public static final int PRISON_TICKS = 4 * 20;
	public static final int PRISON_CD = 12 * 20;

	// ---- X: Ice Slide / Ice Pillar ----
	/** v0.16.5 (user): the slide is a toggle now -- no time limit, it drains Cold while you ride. */
	public static final float COST_SLIDE_PER_SECOND = 4.0f;
	/** Step up a block on the slide without stopping (the client drives the ride; this is the attribute it reads). */
	private static final ResourceLocation SLIDE_STEP = com.projecthero.mod.ProjectHeroMod.id("cryo_slide_step");
	/** The slide's speed in blocks a tick (the client drives it). */
	public static final double SLIDE_SPEED = 0.72;
	private static final int SLIDE_ICE_TTL = 40;
	public static final double PILLAR_HEIGHT = 10.0;
	private static final int PILLAR_TTL = 8 * 20;
	private static final int FALL_GRACE_TICKS = 3 * 20;

	// ---- Z: Glacier Spikes / Absolute Zero ----
	public static final float SPIKES_DAMAGE = 16.0f;
	public static final int SPIKES_FROST = 2;
	public static final int SPIKES_LENGTH = 12;
	public static final double ZERO_RADIUS = 8.0;
	public static final int ZERO_CD = 20 * 20;

	// ---- V: Glacier Wall / Frozen Armor ----
	public static final int WALL_WIDTH = 5;
	public static final int WALL_HEIGHT = 4;
	public static final int WALL_TTL = 15 * 20;
	/** 8 hearts of ice. */
	public static final float ARMOR_ABSORPTION = 16.0f;

	// ---- C: Ice Blade / Shatter ----
	public static final float BLADE_DAMAGE = 8.0f;
	public static final double SHATTER_RADIUS = 10.0;
	public static final float SHATTER_DAMAGE = 20.0f;

	private static final ResourceLocation BLADE_ATK = com.projecthero.mod.ProjectHeroMod.id("cryo_ice_blade");
	private static final ResourceLocation ARMOR_ABS = com.projecthero.mod.ProjectHeroMod.id("cryo_frozen_armor");
	/** Pre-v0.16.5 modifiers, cleared out of old saves. */
	private static final ResourceLocation LEGACY_KB = com.projecthero.mod.ProjectHeroMod.id("frozen_armor_kb");
	private static final ResourceLocation LEGACY_ATK = com.projecthero.mod.ProjectHeroMod.id("frozen_armor_atk");
	private static final ResourceLocation LEGACY_SLIDE = com.projecthero.mod.ProjectHeroMod.id("ice_slide_speed");

	static final BlockParticleOption ICE_DUST = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());
	private static final BlockState PACKED = Blocks.PACKED_ICE.defaultBlockState();
	private static final BlockState BLUE = Blocks.BLUE_ICE.defaultBlockState();

	private CryokinesisHandlers() {
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

	/** Client-safe: is Cryokinesis this player's active power (the powder-snow mixins). */
	public static boolean active(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && KEY.equals(st.activePower) && st.ownedPowers.contains(KEY);
	}

	/** Client-safe: no slipping on ice for any owner (the friction mixin). */
	public static boolean surefooted(Entity e) {
		return e instanceof Player p && owns(p);
	}

	public static float cold(ServerPlayer p) {
		seed(p);
		return res(p, "cold");
	}

	/** For the gametests: the Cold meter set directly. */
	public static void setCold(ServerPlayer p, float value) {
		seed(p);
		set(p, "cold", Mth.clamp(value, 0.0f, MAX_COLD));
	}

	public static boolean beaming(Player p) {
		return owns(p) && resAny(p, "beaming") > 0.5f;
	}

	public static boolean sliding(Player p) {
		return owns(p) && resAny(p, "slide") > 0.5f;
	}

	public static boolean armored(Player p) {
		return owns(p) && resAny(p, "armor") > 0.5f;
	}

	public static boolean bladed(Player p) {
		return owns(p) && resAny(p, "blade") > 0.5f;
	}

	public static boolean rising(Player p) {
		return owns(p) && resAny(p, "pillar") > 0.5f;
	}

	/** No fall damage while sliding and for 3 s after a slide or a pillar launch. */
	public static boolean fallSafe(Player p) {
		return owns(p) && (resAny(p, "slide") > 0.5f || resAny(p, "fall_grace") > 0.5f);
	}

	private static void seed(ServerPlayer p) {
		if (res(p, "seeded") < 0.5f) {
			set(p, "seeded", 1);
			set(p, "cold", MAX_COLD);
		}
	}

	/** Spends {@code cost} Cold, or refuses with a message (nothing spent). */
	private static boolean pay(AbilityContext ctx, float cost) {
		ServerPlayer p = ctx.player();
		seed(p);
		float c = res(p, "cold");
		if (c + 1.0e-3f < cost) {
			ctx.actionBar("message.projecthero.cryo.no_cold", String.format(java.util.Locale.ROOT, "%.0f", cost));
			AbilityHelpers.sound(p, SoundEvents.GLASS_HIT, 0.5f, 0.6f);
			return false;
		}
		set(p, "cold", c - cost);
		return true;
	}

	private static void drain(ServerPlayer p, float amount) {
		set(p, "cold", Math.max(0.0f, res(p, "cold") - amount));
	}

	private static boolean noBoss(LivingEntity t) {
		return !com.projecthero.mod.titanshifter.TitanCombat.isBoss(t) && t.getMaxHealth() <= 200.0f;
	}

	private static Vec3 mid(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	private static void cooling(AbilityContext ctx, String timer, String nameKey) {
		ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(nameKey),
				String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(res(ctx.player(), timer) / 20.0f)));
	}

	private static String ab(String id) {
		return "projecthero.power." + KEY + ".ability." + id;
	}

	/** A snowy biome, or standing in / on snow, powder snow or ice. */
	public static boolean inTheCold(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel sl)) {
			return false;
		}
		BlockPos pos = p.blockPosition();
		if (sl.getBiome(pos).value().coldEnoughToSnow(pos) || p.isInPowderSnow) {
			return true;
		}
		BlockState at = sl.getBlockState(pos);
		BlockState below = sl.getBlockState(pos.below());
		return at.is(Blocks.SNOW) || at.is(Blocks.POWDER_SNOW) || below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.SNOW)
				|| below.is(Blocks.POWDER_SNOW) || below.is(BlockTags.ICE);
	}

	// ================================================================ registration

	public static void register() {
		CryoEntities.initialize();
		FrostStacks.register();
		ServerTickEvents.END_SERVER_TICK.register(CryoIce::tick);
		// conjured ice never outlives the session: melted back while the levels are still loaded
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> CryoIce.meltAll());

		// R -- Ice Bolt / Ice Shards (Shift). Also runs the power-wide upkeep.
		AbilityHandlers.register(KEY, "ice_bolt", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.player().isShiftKeyDown()) {
					iceShards(ctx);
				} else {
					iceBolt(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				upkeep(ctx.player(), ctx.level());
			}
		});

		// G -- Freeze Beam (held) / Ice Prison (Shift)
		AbilityHandlers.register(KEY, "freeze_beam", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					icePrison(ctx);
					return;
				}
				seed(p);
				if (res(p, "cold") < 1.0f) {
					ctx.actionBar("message.projecthero.cryo.no_cold", "10/s");
					return;
				}
				set(p, "beaming", 1);
				set(p, "beam_t", 0);
				MutationVisuals.play(p, "p09.beam");
				AbilityHelpers.sound(p, SoundEvents.POWDER_SNOW_PLACE, 1.0f, 1.6f);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				beamTick(ctx.player(), ctx.level());
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				endBeam(ctx.player());
			}
		});

		// X -- Ice Slide / Ice Pillar (Shift)
		AbilityHandlers.register(KEY, "ice_slide", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					icePillar(ctx);
				} else if (res(p, "slide") > 0.5f) {
					endSlide(p); // X again steps off early
				} else if (pay(ctx, COST_SLIDE)) {
					startSlide(p);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				slideTick(ctx.player(), ctx.level());
				pillarTick(ctx.player(), ctx.level());
			}
		});

		// Z -- Glacier Spikes / Absolute Zero (Shift)
		AbilityHandlers.register(KEY, "glacier_spikes", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (res(p, "zero_cd") > 0.5f) {
						cooling(ctx, "zero_cd", ab("glacier_spikes.zero"));
						return;
					}
					if (pay(ctx, COST_ZERO)) {
						absoluteZero(p, ctx.level());
					}
					return;
				}
				if (pay(ctx, COST_SPIKES)) {
					glacierSpikes(p, ctx.level());
				}
			}
		});

		// V -- Glacier Wall / Frozen Armor (Shift, toggle)
		AbilityHandlers.register(KEY, "glacier_wall", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (res(p, "armor") > 0.5f) {
						armorOff(p, false);
					} else {
						seed(p);
						if (res(p, "cold") < 1.0f) {
							ctx.actionBar("message.projecthero.cryo.no_cold", "3/s");
							return;
						}
						armorOn(p);
					}
					return;
				}
				if (pay(ctx, COST_WALL)) {
					glacierWall(p, ctx.level());
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				armorTick(ctx.player(), ctx.level());
			}
		});

		// C -- Ice Blade (toggle) / Shatter (Shift)
		AbilityHandlers.register(KEY, "ice_blade", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					shatter(ctx);
					return;
				}
				setBlade(p, res(p, "blade") < 0.5f);
			}
		});

		// Frozen Armor: whatever strikes you in melee takes a Frost
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && armored(sp) && source.getEntity() instanceof LivingEntity attacker
					&& source.getDirectEntity() == attacker && attacker != sp && sp.distanceToSqr(attacker) < 25.0) {
				FrostStacks.add(sp, attacker, 1);
				if (sp.level() instanceof ServerLevel sl) {
					sl.sendParticles(ICE_DUST, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(),
							8, 0.3, 0.3, 0.3, 0.05);
				}
			}
		});
		// Ice Blade: a Frost on every melee hit
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (player instanceof ServerPlayer sp && bladed(sp) && target instanceof LivingEntity le && !sp.isSpectator()
					&& com.projecthero.mod.combat.HeroTargets.canHarm(sp, le)) {
				FrostStacks.add(sp, le, 1);
				AbilityHelpers.sound(sp, SoundEvents.GLASS_HIT, 0.8f, 1.5f);
			}
			return InteractionResult.PASS;
		});

		registerPassives();
	}

	// ================================================================ upkeep (every tick for an owner)

	private static void upkeep(ServerPlayer p, ServerLevel level) {
		seed(p);
		for (String timer : new String[] { "prison_cd", "zero_cd", "fall_grace" }) {
			float v = res(p, timer);
			if (v > 0) {
				set(p, timer, v - 1);
			}
		}
		// Cold refills while neither drain is running
		float c = res(p, "cold");
		if (c < MAX_COLD && res(p, "beaming") < 0.5f && res(p, "armor") < 0.5f && res(p, "slide") < 0.5f) {
			set(p, "cold", Math.min(MAX_COLD, c + (inTheCold(p) ? COLD_REGEN_SNOW_PER_TICK : COLD_REGEN_PER_TICK)));
		}
		if (p.tickCount % 100 == 41) {
			dropLegacyState(p);
		}
	}

	/** Pre-v0.16.5 Cryokinesis state is inert now: clear it out of old saves. */
	private static void dropLegacyState(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, LEGACY_KB);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, LEGACY_ATK);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, LEGACY_SLIDE);
		IceBladeItem.removeAll(p);
	}

	// ================================================================ R: Ice Bolt / Ice Shards

	private static Vec3 castPoint(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		return eye.add(look.scale(0.6)).add(right.scale(0.28)).add(0, -0.2, 0);
	}

	private static void iceBolt(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!pay(ctx, COST_BOLT)) {
			return;
		}
		ServerLevel level = ctx.level();
		IceBoltEntity bolt = new IceBoltEntity(level, p, IceBoltEntity.BOLT, BOLT_DAMAGE, BOLT_FROST, 40);
		Vec3 at = castPoint(p);
		bolt.setPos(at.x, at.y, at.z);
		bolt.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0f, BOLT_SPEED, 0.0f);
		level.addFreshEntity(bolt);
		level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 4, 0.08, 0.08, 0.08, 0.03);
		MutationVisuals.play(p, "p09.cast");
		AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 0.8f, 1.7f);
		AbilityHelpers.sound(p, SoundEvents.SNOW_GOLEM_SHOOT, 0.9f, 0.9f);
	}

	/** Seven shards in a fan (the outer ones a little up / down). Each lands its own hit and Frost. */
	private static void iceShards(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!pay(ctx, COST_SHARDS)) {
			return;
		}
		ServerLevel level = ctx.level();
		Vec3 at = castPoint(p);
		for (int i = 0; i < SHARD_COUNT; i++) {
			float yawOff = (i - (SHARD_COUNT - 1) / 2.0f) * 4.5f;
			float pitchOff = (i % 2 == 0 ? -1.5f : 1.5f) + (level.random.nextFloat() - 0.5f) * 2.0f;
			IceBoltEntity shard = new IceBoltEntity(level, p, IceBoltEntity.SHARD, SHARD_DAMAGE, SHARD_FROST, 11);
			shard.setPos(at.x, at.y, at.z);
			shard.shootFromRotation(p, p.getXRot() + pitchOff, p.getYRot() + yawOff, 0.0f, SHARD_SPEED, 0.0f);
			level.addFreshEntity(shard);
		}
		level.sendParticles(ICE_DUST, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.1);
		MutationVisuals.play(p, "p09.shards");
		AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 0.9f, 1.4f);
		AbilityHelpers.sound(p, SoundEvents.SNOW_GOLEM_SHOOT, 1.0f, 0.7f);
	}

	// ================================================================ G: Freeze Beam / Ice Prison

	/** Where the beam stops: the first creature, solid block or water surface within 20 blocks. */
	public static Vec3 beamEnd(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(BEAM_RANGE));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		LivingEntity t = AbilityHelpers.raycastEntity(p, eye.distanceTo(end));
		return t != null ? mid(t) : end;
	}

	private static void beamTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "beaming") < 0.5f) {
			return;
		}
		if (!p.isAlive() || res(p, "cold") <= 0.01f) {
			endBeam(p);
			if (p.isAlive()) {
				p.displayClientMessage(Component.translatable("message.projecthero.cryo.out_of_cold"), true);
			}
			return;
		}
		MutationVisuals.ensure(p, "p09.beam");
		drain(p, COST_BEAM_PER_SECOND / 20.0f);
		float t = res(p, "beam_t") + 1;
		set(p, "beam_t", t);
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(BEAM_RANGE));
		BlockHitResult bhr = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		LivingEntity target = AbilityHelpers.raycastEntity(p, eye.distanceTo(end));
		if (target != null) {
			end = mid(target);
			target.clearFire();
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 30, 2);
			if (Math.round(t) % BEAM_FROST_TICKS == 0) {
				FrostStacks.add(p, target, 1);
				level.sendParticles(ICE_DUST, end.x, end.y, end.z, 6, 0.2, 0.2, 0.2, 0.05);
			}
		} else if (bhr.getType() == HitResult.Type.BLOCK) {
			freezeAt(level, bhr.getBlockPos(), bhr.getDirection());
		}
		set(p, "beam_len", (float) end.distanceTo(eye));
		if (p.tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, end.x, end.y, end.z, 3, 0.2, 0.2, 0.2, 0.02);
		}
		if (p.tickCount % 8 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 0.8f, 1.7f);
		}
	}

	/** The beam's splash: water around the spot freezes over, fire goes out. */
	private static void freezeAt(ServerLevel level, BlockPos hit, Direction face) {
		for (BlockPos bp : BlockPos.betweenClosed(hit.offset(-1, -1, -1), hit.offset(1, 1, 1))) {
			BlockState st = level.getBlockState(bp);
			if (st.is(BlockTags.FIRE)) {
				level.removeBlock(bp, false);
			} else if (st.is(Blocks.WATER) && st.getFluidState().isSource() && !level.getBlockState(bp.above()).getFluidState().is(Fluids.WATER)) {
				CryoIce.place(level, bp, Blocks.ICE.defaultBlockState(), 20 * 20);
			}
		}
	}

	public static void endBeam(ServerPlayer p) {
		if (res(p, "beaming") > 0.5f) {
			set(p, "beaming", 0);
			set(p, "beam_len", 0);
			MutationVisuals.stopIf(p, "p09.beam");
		}
	}

	private static void icePrison(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "prison_cd") > 0.5f) {
			cooling(ctx, "prison_cd", ab("freeze_beam.prison"));
			return;
		}
		LivingEntity t = AbilityHelpers.raycastEntity(p, PRISON_RANGE);
		if (t == null || !com.projecthero.mod.combat.HeroTargets.canHarm(p, t)) {
			ctx.actionBar("message.projecthero.cryo.no_target");
			return;
		}
		if (!noBoss(t)) {
			ctx.actionBar("message.projecthero.cryo.too_big");
			return;
		}
		if (!pay(ctx, COST_PRISON)) {
			return;
		}
		if (!FrostStacks.imprison(t, PRISON_TICKS)) {
			ctx.actionBar("message.projecthero.cryo.no_target");
			set(p, "cold", res(p, "cold") + COST_PRISON); // refund: the target could not be frozen
			return;
		}
		set(p, "prison_cd", PRISON_CD);
		MutationVisuals.play(p, "p09.prison");
		AbilityHelpers.line(ctx.level(), castPoint(p), mid(t), ParticleTypes.SNOWFLAKE, 2.0);
		AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 1.2f, 0.5f);
	}

	// ================================================================ X: Ice Slide / Ice Pillar

	private static void startSlide(ServerPlayer p) {
		set(p, "slide", 1);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, SLIDE_STEP, 0.65, AttributeModifier.Operation.ADD_VALUE);
		MutationVisuals.play(p, "p09.slide");
		AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 1.0f, 0.8f);
		if (p.onGround()) {
			// a little hop so the first ice goes in under you
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, 0.25, p.getDeltaMovement().z));
		}
	}

	public static void endSlide(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, SLIDE_STEP);
		if (res(p, "slide") > 0.5f) {
			set(p, "slide", 0);
			set(p, "fall_grace", FALL_GRACE_TICKS);
			MutationVisuals.stopIf(p, "p09.slide");
		}
	}

	/**
	 * The ice ramp: every tick a 3-wide strip of packed ice under you and back along the way you came (the client drives
	 * the ride itself -- forward along your look, climbing when you look up), melting 2 s behind you.
	 *
	 * <p>v0.16.5 (user: "remove the time limit ... should be toggleable", "ice blocks spawn on me then i cant move",
	 * "descending with the power is hard"): it runs until X again or the Cold runs out (4 a second, no refill while
	 * riding); looking down lays no ice under you, so you ride down; and no ice ever goes where it would box anyone in.
	 */
	private static void slideTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "slide") < 0.5f) {
			return;
		}
		if (!p.isAlive() || p.getAbilities().flying || p.isPassenger()) {
			endSlide(p);
			return;
		}
		if (res(p, "cold") <= 0.01f) {
			endSlide(p); // sets the landing grace (it only acts while the slide is still on)
			p.displayClientMessage(Component.translatable("message.projecthero.cryo.out_of_cold"), true);
			return;
		}
		drain(p, COST_SLIDE_PER_SECOND / 20.0f);
		MutationVisuals.ensure(p, "p09.slide");
		p.resetFallDistance();
		Vec3 mv = p.getKnownMovement();
		Vec3 flat = new Vec3(mv.x, 0, mv.z);
		Vec3 dir = flat.lengthSqr() > 1.0e-4 ? flat.normalize() : BatchBUtil.flatLook(p);
		if (p.getXRot() <= DESCEND_PITCH) {
			layRamp(level, p.position(), dir, mv.length());
		}
		if (p.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.3, 0.05, 0.3, 0.0);
			level.sendParticles(ICE_DUST, p.getX(), p.getY() + 0.05, p.getZ(), 2, 0.25, 0.02, 0.25, 0.04);
		}
		if (p.tickCount % 6 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.GLASS_STEP, SoundSource.PLAYERS, 0.6f, 1.4f);
		}
	}

	/** Looking further down than this (degrees) the slide rides down instead of laying ice under you. */
	public static final float DESCEND_PITCH = 15.0f;

	/**
	 * Packed ice under {@code feet}, 3 wide across {@code dir}, and back along the last {@code travelled} blocks. Never
	 * above the feet, and never in a cell where a creature stands (v0.16.5: ice used to close round the rider).
	 */
	static int layRamp(ServerLevel level, Vec3 feet, Vec3 dir, double travelled) {
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		int placed = 0;
		double back = Math.min(2.0, travelled);
		for (double b = 0; b <= back + 1.0e-3; b += 0.5) {
			Vec3 at = feet.subtract(dir.scale(b));
			BlockPos under = BlockPos.containing(at.x, at.y - 0.6, at.z);
			for (int s = -1; s <= 1; s++) {
				BlockPos cell = BlockPos.containing(at.x + side.x * s, under.getY(), at.z + side.z * s);
				if (cell.getY() + 1 > feet.y + 0.01 || !level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(cell)).isEmpty()) {
					continue;
				}
				if (CryoIce.place(level, cell, PACKED, SLIDE_ICE_TTL)) {
					placed++;
				}
			}
		}
		return placed;
	}

	/** Ice Pillar: a column of ice bursts up under you and throws you 10 blocks into the air. */
	private static void icePillar(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!pay(ctx, COST_PILLAR)) {
			return;
		}
		ServerLevel level = ctx.level();
		endSlide(p);
		set(p, "pillar", 14);
		set(p, "fall_grace", FALL_GRACE_TICKS + 40);
		Vec3 v = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.3, pillarLaunchSpeed(), v.z * 0.3));
		MutationVisuals.play(p, "p09.pillar");
		level.sendParticles(ICE_DUST, p.getX(), p.getY() + 0.2, p.getZ(), 30, 0.5, 0.1, 0.5, 0.15);
		AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 1.2f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.POWDER_SNOW_PLACE, 1.0f, 0.5f);
		pillarColumn(p, level);
	}

	/** The column grows under the rising player: ice from the ground up to just below the feet, a blue-ice cap. */
	private static void pillarTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "pillar");
		if (left < 0.5f) {
			return;
		}
		set(p, "pillar", left - 1);
		p.resetFallDistance();
		pillarColumn(p, level);
		if (left - 1 < 0.5f) {
			MutationVisuals.stopIf(p, "p09.pillar");
		}
	}

	private static void pillarColumn(ServerPlayer p, ServerLevel level) {
		BlockPos top = BlockPos.containing(p.getX(), p.getY() - 1.0, p.getZ());
		int built = 0;
		for (int dy = 0; dy < (int) PILLAR_HEIGHT + 2; dy++) {
			BlockPos cell = top.below(dy);
			BlockState st = level.getBlockState(cell);
			if (CryoIce.isConjured(level, cell)) {
				continue;
			}
			if (!CryoIce.replaceable(st)) {
				break; // reached the ground
			}
			CryoIce.place(level, cell, dy == 0 ? BLUE : PACKED, PILLAR_TTL);
			built++;
		}
		if (built > 0) {
			level.sendParticles(ICE_DUST, p.getX(), p.getY() - 0.5, p.getZ(), 6, 0.3, 0.3, 0.3, 0.06);
		}
	}

	private static double pillarSpeed = -1;

	/** The take-off speed that peaks {@value #PILLAR_HEIGHT} blocks up (vanilla gravity 0.08, drag 0.98). */
	public static double pillarLaunchSpeed() {
		if (pillarSpeed < 0) {
			double lo = 0.3;
			double hi = 3.0;
			for (int i = 0; i < 30; i++) {
				double mid = (lo + hi) / 2;
				if (apex(mid) < PILLAR_HEIGHT) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			pillarSpeed = (lo + hi) / 2;
		}
		return pillarSpeed;
	}

	/** Height reached by a jump of initial vertical speed {@code v}. */
	public static double apex(double v) {
		double y = 0;
		for (int t = 0; t < 200 && v > 0; t++) {
			y += v;
			v = (v - 0.08) * 0.98;
		}
		return y;
	}

	// ================================================================ Z: Glacier Spikes / Absolute Zero

	/** The ground top under {@code at} (searching 3 up and 5 down), or NaN over a drop. */
	private static double groundAt(ServerLevel level, Vec3 at) {
		BlockPos start = BlockPos.containing(at.x, at.y + 3, at.z);
		for (int dy = 0; dy <= 8; dy++) {
			BlockPos cell = start.below(dy);
			BlockState st = level.getBlockState(cell);
			if (!st.getCollisionShape(level, cell).isEmpty() || st.getFluidState().is(Fluids.WATER)) {
				if (level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()) {
					return cell.getY() + Math.max(0.0, st.getCollisionShape(level, cell).isEmpty() ? 0.9 : st.getCollisionShape(level, cell).max(Direction.Axis.Y));
				}
			}
		}
		return Double.NaN;
	}

	/** A 12-block line of spikes racing out from your feet: 16 + 2 Frost to everything over them. Returns spikes made. */
	public static int glacierSpikes(ServerPlayer p, ServerLevel level) {
		Vec3 dir = BatchBUtil.flatLook(p);
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Set<LivingEntity> hit = Collections.newSetFromMap(new IdentityHashMap<>());
		int made = 0;
		Vec3 origin = p.position();
		for (int i = 0; i < SPIKES_LENGTH; i++) {
			double along = 1.5 + i;
			double wiggle = (i % 2 == 0 ? 0.35 : -0.35) + (level.random.nextDouble() - 0.5) * 0.2;
			Vec3 at = origin.add(dir.scale(along)).add(side.scale(wiggle));
			double gy = groundAt(level, at);
			if (Double.isNaN(gy)) {
				continue;
			}
			float h = 1.7f + i * 0.06f + level.random.nextFloat() * 0.6f;
			IceSpikeEntity.spawn(level, p, new Vec3(at.x, gy, at.z), origin, i, i + 40, h, SPIKES_DAMAGE, SPIKES_FROST, hit);
			made++;
		}
		MutationVisuals.play(p, "p09.spikes");
		AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 1.2f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.POWDER_SNOW_BREAK, 1.0f, 0.6f);
		return made;
	}

	/** Everything within 8 takes all five Frost (frozen solid) inside a crown of spikes; water around you freezes. */
	public static int absoluteZero(ServerPlayer p, ServerLevel level) {
		set(p, "zero_cd", ZERO_CD);
		int frozen = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), ZERO_RADIUS)) {
			e.clearFire();
			FrostStacks.freezeNow(p, e);
			if (FrostStacks.frozen(e)) {
				frozen++;
			}
		}
		// the crown: three rings of harmless spikes bursting outward
		double[] radii = { 2.5, 5.0, 7.5 };
		int[] counts = { 8, 14, 20 };
		Vec3 c = p.position();
		for (int r = 0; r < radii.length; r++) {
			for (int i = 0; i < counts[r]; i++) {
				double a = (i + (r % 2) * 0.5) / counts[r] * Math.PI * 2;
				Vec3 at = c.add(Math.cos(a) * radii[r], 0, Math.sin(a) * radii[r]);
				double gy = groundAt(level, at);
				if (!Double.isNaN(gy)) {
					IceSpikeEntity.spawn(level, p, new Vec3(at.x, gy, at.z), c, 3 + r * 3 + level.random.nextInt(2), 50 + r * 3,
							2.4f - r * 0.5f + level.random.nextFloat() * 0.4f, 0.0f, 0, null);
				}
			}
		}
		if (AbilityHelpers.canGrief()) {
			int rr = (int) ZERO_RADIUS;
			for (BlockPos bp : BlockPos.betweenClosed(p.blockPosition().offset(-rr, -2, -rr), p.blockPosition().offset(rr, 1, rr))) {
				if (bp.distToCenterSqr(p.getX(), p.getY(), p.getZ()) <= ZERO_RADIUS * ZERO_RADIUS) {
					BlockState st = level.getBlockState(bp);
					if (st.is(Blocks.WATER) && st.getFluidState().isSource() && level.getBlockState(bp.above()).isAir()) {
						CryoIce.place(level, bp, Blocks.ICE.defaultBlockState(), 20 * 20);
					} else if (st.is(BlockTags.FIRE)) {
						level.removeBlock(bp, false);
					}
				}
			}
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 120, ZERO_RADIUS / 2, 1.0, ZERO_RADIUS / 2, 0.05);
		BatchA.ring(level, p.position().add(0, 0.2, 0), ZERO_RADIUS, ICE_DUST, 48, 0.05);
		level.playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.6f, 0.4f);
		level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.4f, 0.5f);
		MutationVisuals.play(p, "p09.zero");
		return frozen;
	}

	// ================================================================ V: Glacier Wall / Frozen Armor

	/**
	 * A 5-wide, 4-tall wall of packed ice (blue-ice crest) 3 blocks ahead, across your look, rising a row every 2 ticks;
	 * melts after 15 s. Anything standing where it rises is shoved out. Returns the cells claimed for the wall.
	 */
	public static int glacierWall(ServerPlayer p, ServerLevel level) {
		Vec3 dir = BatchBUtil.flatLook(p);
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Vec3 centre = p.position().add(dir.scale(3.0));
		java.util.List<BlockPos> bases = new java.util.ArrayList<>();
		for (int i = -(WALL_WIDTH / 2); i <= WALL_WIDTH / 2; i++) {
			Vec3 at = centre.add(side.scale(i));
			double gy = groundAt(level, at);
			int y = Double.isNaN(gy) ? Mth.floor(p.getY()) : Mth.ceil(gy - 0.01);
			BlockPos base = BlockPos.containing(at.x, y, at.z);
			if (!bases.contains(base)) {
				bases.add(base);
			}
		}
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, centre.add(0, 1, 0), 2.6)) {
			AbilityHelpers.push(e, dir.scale(0.9).add(0, 0.35, 0));
		}
		BatchBScheduler.schedule(level, age -> {
			if (age % 2 != 0) {
				return age < WALL_HEIGHT * 2;
			}
			int row = age / 2;
			for (BlockPos base : bases) {
				BlockPos cell = base.above(row);
				CryoIce.place(level, cell, row == WALL_HEIGHT - 1 ? BLUE : PACKED, WALL_TTL - age);
				level.sendParticles(ICE_DUST, cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
			}
			level.playSound(null, bases.get(bases.size() / 2).above(row), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.9f,
					0.5f + row * 0.15f);
			return row < WALL_HEIGHT - 1;
		});
		MutationVisuals.play(p, "p09.wall");
		AbilityHelpers.sound(p, SoundEvents.POWDER_SNOW_PLACE, 1.0f, 0.6f);
		return bases.size() * WALL_HEIGHT;
	}

	private static void armorOn(ServerPlayer p) {
		set(p, "armor", 1);
		PowerToggles.modifier(p, Attributes.MAX_ABSORPTION, ARMOR_ABS, ARMOR_ABSORPTION, AttributeModifier.Operation.ADD_VALUE);
		p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), ARMOR_ABSORPTION));
		MutationVisuals.play(p, "p09.armor");
		AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 1.2f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0f, 1.4f);
		if (p.level() instanceof ServerLevel sl) {
			sl.sendParticles(ICE_DUST, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.8, 0.4, 0.08);
		}
	}

	/** Drops the armour; {@code broken} when the ice was smashed or the Cold ran out. */
	public static void armorOff(ServerPlayer p, boolean broken) {
		if (res(p, "armor") < 0.5f) {
			return;
		}
		set(p, "armor", 0);
		PowerToggles.clearModifier(p, Attributes.MAX_ABSORPTION, ARMOR_ABS);
		p.setAbsorptionAmount(Math.min(p.getAbsorptionAmount(), (float) p.getAttributeValue(Attributes.MAX_ABSORPTION)));
		AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, broken ? 1.2f : 0.7f, broken ? 0.7f : 1.3f);
		if (p.level() instanceof ServerLevel sl) {
			sl.sendParticles(ICE_DUST, p.getX(), p.getY() + 1.0, p.getZ(), broken ? 40 : 16, 0.4, 0.8, 0.4, 0.12);
		}
	}

	private static void armorTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "armor") < 0.5f) {
			return;
		}
		drain(p, COST_ARMOR_PER_SECOND / 20.0f);
		if (res(p, "cold") <= 0.01f) {
			armorOff(p, true);
			p.displayClientMessage(Component.translatable("message.projecthero.cryo.out_of_cold"), true);
			return;
		}
		if (p.getAbsorptionAmount() <= 0.01f && p.tickCount % 2 == 0) {
			armorOff(p, true);
			p.displayClientMessage(Component.translatable("message.projecthero.cryo.armor_broken"), true);
			return;
		}
		// respawn-safe re-apply
		PowerToggles.modifier(p, Attributes.MAX_ABSORPTION, ARMOR_ABS, ARMOR_ABSORPTION, AttributeModifier.Operation.ADD_VALUE);
		if (p.tickCount % 10 == 0) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.35, 0.6, 0.35, 0.0);
		}
	}

	// ================================================================ C: Ice Blade / Shatter

	public static void setBlade(ServerPlayer p, boolean on) {
		boolean was = res(p, "blade") > 0.5f;
		set(p, "blade", on ? 1 : 0);
		if (on) {
			PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, BLADE_ATK, BLADE_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, BLADE_ATK);
		}
		if (on && !was) {
			MutationVisuals.play(p, "p09.blade_draw");
			AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 1.0f, 1.3f);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.8f);
			if (p.level() instanceof ServerLevel sl) {
				Vec3 h = AbilityHelpers.handPosition(p);
				sl.sendParticles(ICE_DUST, h.x, h.y, h.z, 14, 0.15, 0.3, 0.15, 0.05);
			}
		} else if (!on && was) {
			AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 0.7f, 1.5f);
			if (p.level() instanceof ServerLevel sl) {
				Vec3 h = AbilityHelpers.handPosition(p);
				sl.sendParticles(ICE_DUST, h.x, h.y, h.z, 12, 0.15, 0.3, 0.15, 0.08);
			}
		}
	}

	/**
	 * Shatter: every frosted or frozen enemy within 10 -- frozen ones take 40 (double) and burst out of their ice in a
	 * spray of shards, frosted ones take 20. Refused (nothing spent) when nothing nearby carries any ice.
	 */
	private static void shatter(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		java.util.List<LivingEntity> targets = new java.util.ArrayList<>();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), SHATTER_RADIUS)) {
			if (FrostStacks.frozen(e) || FrostStacks.stacks(e) > 0) {
				targets.add(e);
			}
		}
		if (targets.isEmpty()) {
			ctx.actionBar("message.projecthero.cryo.nothing_frozen");
			return;
		}
		if (!pay(ctx, COST_SHATTER)) {
			return;
		}
		shatterAll(p, level, targets);
	}

	/** The damage half of Shatter (also used by the gametests). Returns how many were frozen. */
	public static int shatterAll(ServerPlayer p, ServerLevel level, java.util.List<LivingEntity> targets) {
		int frozen = 0;
		for (LivingEntity e : targets) {
			boolean wasFrozen = FrostStacks.shatter(e);
			if (wasFrozen) {
				frozen++;
			}
			AbilityHelpers.hurtBurst(p, e, wasFrozen ? SHATTER_DAMAGE * 2 : SHATTER_DAMAGE);
			Vec3 c = mid(e);
			int n = wasFrozen ? 9 : 4;
			for (int i = 0; i < n; i++) {
				double a = level.random.nextDouble() * Math.PI * 2;
				Vec3 v = new Vec3(Math.cos(a) * 0.35, 0.25 + level.random.nextDouble() * 0.35, Math.sin(a) * 0.35);
				level.addFreshEntity(IceBoltEntity.debris(level, c.add(v.scale(0.8)), v));
			}
			level.sendParticles(ICE_DUST, c.x, c.y, c.z, wasFrozen ? 30 : 12, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4,
					e.getBbWidth() * 0.5, 0.15);
			level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f,
					wasFrozen ? 0.6f : 1.1f);
		}
		MutationVisuals.play(p, "p09.shatter");
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0f, 0.8f);
		return frozen;
	}

	// ================================================================ passives

	private static void registerPassives() {
		PowerPassives.register(KEY, (player, owned) -> {
			if (!owned) {
				Power power = Powers.byKey(KEY);
				if (power != null && ExperimentalPowers.owns(player, power)) {
					// still owned while forget() runs its teardown: drop the transient states too
					endBeam(player);
					endSlide(player);
					armorOff(player, false);
					setBlade(player, false);
					set(player, "pillar", 0);
				} else {
					PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, BLADE_ATK);
					PowerToggles.clearModifier(player, Attributes.MAX_ABSORPTION, ARMOR_ABS);
				}
				MutationVisuals.stop(player);
				dropLegacyState(player);
			}
		});
		PowerPassives.registerTick(KEY, player -> {
			// immune to their own cold (and any freezing, unless frozen solid by an enemy cryokinetic)
			if (player.getTicksFrozen() > 0 && !FrostStacks.frozen(player)) {
				player.setTicksFrozen(0);
			}
			frostWalk(player);
			// respawn-safe re-apply of the blade
			if (res(player, "blade") > 0.5f) {
				PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, BLADE_ATK, BLADE_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
			}
		});
	}

	/** Frost Walker: water you walk over freezes into (self-melting) frosted ice. */
	private static void frostWalk(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level) || !p.onGround() && !p.isInWater() && res(p, "slide") < 0.5f) {
			return;
		}
		if (p.isInWater() && p.getDeltaMovement().y > 0.05) {
			return; // swimming up: leave the surface open
		}
		int r = 2;
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

	// ================================================================ synced visuals + HUD (called from RevampBatchB)

	public static void registerVisuals() {
		MutationVisuals.registerFlag("p09.beam", CryokinesisHandlers::beaming);
		MutationVisuals.registerValue("p09.beam_len", p -> beaming(p) ? resAny(p, "beam_len") : 0.0);
		MutationVisuals.registerFlag("p09.slide", CryokinesisHandlers::sliding);
		MutationVisuals.registerFlag("p09.armor", CryokinesisHandlers::armored);
		MutationVisuals.registerFlag("p09.blade", CryokinesisHandlers::bladed);
		MutationVisuals.registerFlag("p09.pillar", CryokinesisHandlers::rising);
	}

	public static void registerMeters() {
		int text = 0xFFCFEFFF;
		MutationMeters.register(new MutationMeters.Spec(KEY, "cold", MutationMeters.Kind.RESERVE, MutationMeters.Style.HAIRLINE,
				"Cold", MAX_COLD, 0xFF8FDCFF, true, true, false, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "prison_cd", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Ice Prison", PRISON_CD, 0xFF6F95AE, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "zero_cd", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Absolute Zero", ZERO_CD, 0xFF6F95AE, false, false, true, text));
	}

	/** For the gametests: a slide's ramp laid at an explicit spot. */
	public static int layRampForTest(ServerLevel level, Vec3 feet, Vec3 dir) {
		return layRamp(level, feet, dir, 1.0);
	}
}
