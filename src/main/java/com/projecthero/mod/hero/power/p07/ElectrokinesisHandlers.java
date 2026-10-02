package com.projecthero.mod.hero.power.p07;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerCombos;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.revamp.BatchCFx;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 07 -- Electrokinesis (v0.13.22 revamp). Deliberately weaker than Thor's lightning: every hit is
 * {@code PLAYER_ATTACK} (armour applies) and nothing here touches Mjolnir / worthiness state.
 *
 * <h2>Signature: static stacks</h2>
 * Every Electrokinesis hit leaves a <b>static stack</b> on its target (max {@value #MAX_STACKS}, 8 s, refreshed by each
 * new stack). Stacked targets crackle with sparks everyone can see, take +10% Electrokinesis damage per stack, and at
 * {@value #MAX_STACKS} stacks they are <b>stunned</b> for 1.5 s (then immune to a re-stun for 4 s). Chain Lightning
 * jumps much further to a stacked target, Overcharge (H) detonates every stack in range and Ion Pull (N) yanks the
 * stacked enemies in.
 *
 * <h2>Charge cell</h2>
 * Every ability spends from one shared cell (0..{@link #MAX_CHARGE}); it regenerates on its own, far faster in a
 * thunderstorm, and refills instantly near a lightning strike. An electrokinetic takes no lightning damage.
 */
public final class ElectrokinesisHandlers {
	public static final String KEY = "power_07_electrokinesis";

	/** Charge cell (+15% over the pre-revamp 1000). */
	public static final float MAX_CHARGE = 1150.0f;
	private static final float REGEN_CALM = MAX_CHARGE / (40 * 20);   // ~40 s from empty
	private static final float REGEN_STORM = MAX_CHARGE / (9 * 20);   // ~9 s in a thunderstorm
	private static final float CHARGED_MODE_DRAIN = MAX_CHARGE / (28 * 20); // Charged Mode lasts ~28 s

	private static final float COST_BOLT = 45.0f;
	private static final float COST_CHAIN = 85.0f;
	private static final float COST_DASH = 60.0f;
	private static final float COST_EMP = 200.0f;
	public static final float COST_STORM = 575.0f;
	private static final float COST_POWER_SURGE = 70.0f;
	private static final float COST_OVERCHARGE = 140.0f;
	private static final float COST_ION_PULL = 80.0f;

	/** Damage (all +20% over the pre-revamp kit). */
	public static final float BOLT_DAMAGE = 12.0f;
	public static final float CHAIN_DAMAGE = 11.0f;
	private static final float DASH_DAMAGE = 8.0f;
	public static final float STORM_STRIKE = 36.0f;
	private static final float STORM_FOLLOW_UP = 10.0f;
	public static final float OVERCHARGE_PER_STACK = 8.0f;
	private static final float CHARGED_ABILITY_BONUS = 10.0f;
	private static final double CHARGED_MELEE_BONUS = 8.0;

	/** Static stacks. */
	public static final int MAX_STACKS = 3;
	private static final int STACK_TICKS = 160;
	private static final int STUN_TICKS = 30;
	private static final int RESTUN_IMMUNE_TICKS = 80;
	private static final double HOP_RANGE = 8.0;
	private static final double HOP_RANGE_STACKED = 14.0;

	private static final int STORM_CHARGE_TICKS = 80;

	static final int BLUE = 0x4FB8FF;
	static final int WHITE_BLUE = 0xC8EEFF;

	private static final net.minecraft.resources.ResourceLocation CHARGED_MELEE =
			com.projecthero.mod.ProjectHeroMod.id("electro_charged_melee");

	/** One target's static stacks. Mutable, only touched on the server thread. */
	private static final class Stack {
		final ServerLevel level;
		int count;
		long expires;
		long stunReadyAt;

		Stack(ServerLevel level) {
			this.level = level;
		}
	}

	/** entityId -> static stacks. Expired entries are dropped by {@link #pruneExpired} and the spark tick. */
	private static final Map<Integer, Stack> STACKS = new ConcurrentHashMap<>();
	/** player -> a running Electrical Storm (follow-up strikes still to land). */
	private static final Map<UUID, Storm> STORMS = new ConcurrentHashMap<>();
	/** blockpos long -> game time an EMP suppression expires. */
	private static final Map<Long, Long> EMP_UNTIL = new ConcurrentHashMap<>();
	/** blockpos long -> game time a rail we energised should revert. */
	private static final Map<Long, Long> RAIL_REVERT = new ConcurrentHashMap<>();

	private static final class Storm {
		final ServerLevel level;
		final Vec3 centre;
		int strikesLeft;
		long nextAt;

		Storm(ServerLevel level, Vec3 centre, int strikes, long nextAt) {
			this.level = level;
			this.centre = centre;
			this.strikesLeft = strikes;
			this.nextAt = nextAt;
		}
	}

	private ElectrokinesisHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	// ---- charge cell ------------------------------------------------------------------------

	private static boolean seeded(AbilityContext ctx) {
		return ExperimentalPowers.state(ctx.player()).resources.containsKey(KEY + "/ecell");
	}

	private static void seed(AbilityContext ctx) {
		if (!seeded(ctx)) {
			ctx.setResource("ecell", MAX_CHARGE, MAX_CHARGE);
		}
	}

	/** Spend {@code amount} from the cell, or refuse (with a message) if it is too low. */
	private static boolean spend(AbilityContext ctx, float amount) {
		seed(ctx);
		if (ctx.resource("ecell") < amount) {
			ctx.actionBar("message.projecthero.electro.charge_low");
			return false;
		}
		ctx.addResource("ecell", -amount, MAX_CHARGE);
		return true;
	}

	public static boolean chargedModeActive(Player p) {
		return p instanceof ServerPlayer sp && BatchCFx.toggledQuick(sp, KEY, "charged_mode");
	}

	/** Charged Mode adds a flat +10 to every Electrokinesis attack. */
	private static float chargedBonus(ServerPlayer p) {
		return chargedModeActive(p) ? CHARGED_ABILITY_BONUS : 0.0f;
	}

	/** The full damage of an Electrokinesis hit on {@code target}: base + Charged Mode, x wet combo, +10% per stack. */
	private static float damage(ServerPlayer p, LivingEntity target, float base) {
		float dmg = (base + chargedBonus(p)) * PowerCombos.wetElectricMultiplier(p, target);
		return dmg * (1.0f + 0.1f * stacks(target));
	}

	/** One Electrokinesis hit: damage, then a static stack. Returns true if it landed. */
	private static boolean zap(ServerPlayer p, LivingEntity target, float base) {
		boolean hit = AbilityHelpers.hurt(p, target, AbilityHelpers.kinetic(p), damage(p, target, base));
		addStack(p, target, 1);
		return hit;
	}

	// ---- static stacks ----------------------------------------------------------------------

	/** Current static stacks on {@code e} (0 when none / expired). */
	public static int stacks(LivingEntity e) {
		Stack s = STACKS.get(e.getId());
		if (s == null || s.level != e.level() || s.expires <= e.level().getGameTime()) {
			return 0;
		}
		return s.count;
	}

	/**
	 * Adds {@code n} static stacks to {@code target} (capped at {@value #MAX_STACKS}), refreshing their timer. The
	 * transition to full stacks stuns the target (unless it was stunned in the last 4 s). Every stack gain also
	 * leaves the old "static shock" slow behind. Returns the new count.
	 */
	public static int addStack(ServerPlayer src, LivingEntity target, int n) {
		if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || target == src) {
			return 0;
		}
		long now = level.getGameTime();
		Stack s = STACKS.get(target.getId());
		if (s == null || s.level != level || s.expires <= now) {
			s = new Stack(level);
			STACKS.put(target.getId(), s);
		}
		int before = s.count;
		s.count = Math.min(MAX_STACKS, s.count + n);
		s.expires = now + STACK_TICKS;
		AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.5,
				target.getZ(), 6 + 4 * s.count, 0.3, target.getBbHeight() * 0.35, 0.3, 0.12);
		if (before < MAX_STACKS && s.count >= MAX_STACKS && now >= s.stunReadyAt) {
			stun(level, target);
			s.stunReadyAt = now + RESTUN_IMMUNE_TICKS;
		}
		return s.count;
	}

	/** Removes and returns {@code target}'s stacks. */
	public static int consumeStacks(LivingEntity target) {
		int n = stacks(target);
		STACKS.remove(target.getId());
		return n;
	}

	/** A short full stun: rooted, weakened, can't mine / swing properly, AI navigation cut. */
	private static void stun(ServerLevel level, LivingEntity target) {
		AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 9);
		AbilityHelpers.applyControl(target, MobEffects.WEAKNESS, STUN_TICKS, 2);
		AbilityHelpers.applyControl(target, MobEffects.DIG_SLOWDOWN, STUN_TICKS, 2);
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		Vec3 c = target.position().add(0, target.getBbHeight() + 0.2, 0);
		BatchCFx.flatRing(level, c, 0.6, 14, BatchCFx.dust(BLUE, 1.0f), 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y - target.getBbHeight() * 0.5, c.z, 30, 0.4,
				target.getBbHeight() * 0.4, 0.4, 0.25);
		level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5f, 1.9f);
	}

	/** Server tick (from RevampBatchC): keeps every stacked target visibly crackling, and runs storm follow-ups. */
	public static void serverTick(MinecraftServer server) {
		long tick = server.getTickCount();
		if (tick % 4 == 0 && !STACKS.isEmpty()) {
			STACKS.entrySet().removeIf(e -> {
				Stack s = e.getValue();
				long now = s.level.getGameTime();
				if (s.expires <= now) {
					return true;
				}
				if (!(s.level.getEntity(e.getKey()) instanceof LivingEntity le) || !le.isAlive()) {
					return true;
				}
				double h = le.getBbHeight();
				s.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, le.getX(), le.getY() + h * 0.5, le.getZ(),
						2 * s.count, 0.3, h * 0.35, 0.3, 0.05);
				if (s.count >= 2) {
					// an orbiting blue ring at 2+ stacks, a double ring at full stacks
					BatchCFx.flatRing(s.level, le.position().add(0, h * 0.55, 0), le.getBbWidth() * 0.9 + 0.2,
							8 + 4 * s.count, BatchCFx.dust(BLUE, 0.7f), 0);
					if (s.count >= MAX_STACKS) {
						BatchCFx.flatRing(s.level, le.position().add(0, h * 0.95, 0), le.getBbWidth() * 0.7 + 0.1, 10,
								BatchCFx.dust(WHITE_BLUE, 0.6f), 0);
					}
				}
				return false;
			});
		}
		if (!STORMS.isEmpty()) {
			STORMS.entrySet().removeIf(e -> {
				Storm st = e.getValue();
				if (st.level.getGameTime() < st.nextAt) {
					return false;
				}
				ServerPlayer owner = server.getPlayerList().getPlayer(e.getKey());
				if (owner == null || owner.level() != st.level) {
					return true;
				}
				stormFollowUp(owner, st);
				st.strikesLeft--;
				st.nextAt = st.level.getGameTime() + 12;
				return st.strikesLeft <= 0;
			});
		}
	}

	// ---- redstone ---------------------------------------------------------------------------

	/** Briefly (5 s) power any redstone within range by dropping a temporary redstone block near it. */
	private static void powerRedstone(ServerLevel level, Vec3 centre) {
		if (!AbilityHelpers.canGrief()) {
			return;
		}
		BlockPos c = BlockPos.containing(centre);
		BlockPos best = null;
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-4, -3, -4), c.offset(4, 3, 4))) {
			if (respondsToRedstone(level.getBlockState(bp))) {
				BlockPos side = bp.above();
				if (level.getBlockState(side).canBeReplaced()) {
					best = side.immutable();
					break;
				}
			}
		}
		if (best != null) {
			TempBlocks.place(level, best, Blocks.REDSTONE_BLOCK.defaultBlockState(), 100);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5,
					10, 0.3, 0.3, 0.3, 0.05);
		}
	}

	private static boolean respondsToRedstone(BlockState st) {
		return st.is(Blocks.REDSTONE_LAMP) || st.is(Blocks.REDSTONE_WIRE) || st.is(Blocks.DISPENSER)
				|| st.is(Blocks.DROPPER) || st.is(Blocks.PISTON) || st.is(Blocks.STICKY_PISTON)
				|| st.is(Blocks.NOTE_BLOCK) || st.is(Blocks.TNT) || st.is(Blocks.HOPPER)
				|| st.getBlock() instanceof DoorBlock || st.is(Blocks.IRON_DOOR) || st.is(Blocks.IRON_TRAPDOOR)
				|| st.is(Blocks.POWERED_RAIL) || st.is(Blocks.BELL);
	}

	private static ParticleOptions blueDust(float size) {
		return BatchCFx.dust(BLUE, size);
	}

	/** A visible lightning arc: sparks + a blue dust core along a jagged path. */
	private static void bolt(ServerLevel level, Vec3 a, Vec3 b) {
		BatchCFx.arc(level, a, b, ParticleTypes.ELECTRIC_SPARK, 0.6);
		BatchCFx.arc(level, a, b, blueDust(0.8f), 0.35);
	}

	private static Vec3 hand(ServerPlayer p) {
		return AbilityHelpers.handPosition(p);
	}

	// ---- registration -----------------------------------------------------------------------

	public static void register() {
		// R -- Electric Bolt: 12 damage + a static stack, briefly powers nearby redstone.
		AbilityHandlers.register(KEY, "electric_bolt", Handlers.instant(ctx -> {
			if (!spend(ctx, COST_BOLT)) {
				return;
			}
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 26.0);
			Vec3 end = AbilityHelpers.aimPoint(p, 26.0);
			bolt(ctx.level(), hand(p), end);
			if (t != null) {
				zap(p, t, BOLT_DAMAGE);
				powerRedstone(ctx.level(), t.position());
			} else {
				powerRedstone(ctx.level(), end);
			}
			MutationVisuals.play(p, "point_right");
			AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.8f);
			ctx.triggerCooldown();
		}));

		// G -- Chain Lightning (Shift + G: EMP).
		AbilityHandlers.register(KEY, "chain_lightning", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (p.isShiftKeyDown()) {
				if (!spend(ctx, COST_EMP)) {
					return;
				}
				emp(ctx, 20.0);
				MutationVisuals.play(p, "cast_raise_both");
				ctx.triggerCooldown(25 * 20);
				return;
			}
			LivingEntity first = AbilityHelpers.raycastEntity(p, 24.0);
			if (first == null) {
				ctx.actionBar("message.projecthero.electro.no_target");
				return;
			}
			if (!spend(ctx, COST_CHAIN)) {
				return;
			}
			chainLightning(p, first, 6);
			MutationVisuals.play(p, "cast_two_hand");
			ctx.triggerCooldown();
		}));

		// X -- Bolt Form: become a streak of lightning for up to 10 blocks.
		AbilityHandlers.register(KEY, "electric_dash", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			seed(ctx);
			if (ctx.resource("ecell") < COST_DASH) {
				ctx.actionBar("message.projecthero.electro.charge_low");
				return;
			}
			if (!boltForm(ctx)) {
				ctx.actionBar("message.projecthero.electro.no_room");
				return;
			}
			ctx.addResource("ecell", -COST_DASH, MAX_CHARGE);
			MutationVisuals.play(p, "dash_forward");
			ctx.triggerCooldown();
		}));

		// Z -- Electrical Storm: hold 4 s, then an opening strike and four follow-up bolts around the aim point.
		AbilityHandlers.register(KEY, "electrical_storm", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("storm_start") > 0.5f) {
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
					return;
				}
				seed(ctx);
				if (ctx.resource("ecell") < COST_STORM) {
					ctx.actionBar("message.projecthero.electro.charge_low");
					return;
				}
				ctx.setResource("storm_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				float start = ctx.resource("storm_start");
				if (start <= 0.5f) {
					return;
				}
				long held = ctx.player().level().getGameTime() - (long) start;
				if (held >= STORM_CHARGE_TICKS) {
					fireStorm(ctx);
				} else {
					ctx.setResource("storm_start", 0, 1.0e12f);
					ctx.setResource("storm_charge", 0, 100);
					MutationVisuals.stopIf(ctx.player(), "p07.storm_call");
					AbilityHelpers.sound(ctx.player(), SoundEvents.FIRE_EXTINGUISH, 0.6f, 0.8f);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				float start = ctx.resource("storm_start");
				if (start <= 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) start;
				if (held > STORM_CHARGE_TICKS + 40 || held < 0) {
					fireStorm(ctx);
					return;
				}
				MutationVisuals.ensure(p, "p07.storm_call");
				double frac = Math.min(1.0, held / (double) STORM_CHARGE_TICKS);
				if (held % 4 == 0) {
					ctx.setResource("storm_charge", (float) (frac * 100.0), 100);
				}
				int n = 3 + (int) (frac * 14);
				for (int i = 0; i < n; i++) {
					double a = p.level().random.nextDouble() * Math.PI * 2;
					double r = 0.6 + p.level().random.nextDouble() * (1.6 * frac);
					ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK,
							p.getX() + Math.cos(a) * r, p.getY() + 0.4 + p.level().random.nextDouble() * 2.2,
							p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
				}
				if (held % 6 == 0) {
					Vec3 top = p.position().add(0, 2.6, 0);
					bolt(ctx.level(), top, top.add((p.getRandom().nextDouble() - 0.5) * 3, 1.5 + frac * 2,
							(p.getRandom().nextDouble() - 0.5) * 3));
				}
				if (held % 10 == 0) {
					AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 0.6f + (float) frac);
				}
			}
		});

		// V -- Power Surge: plant a short-lived redstone block against whatever you are looking at.
		AbilityHandlers.register(KEY, "electromagnetic_pull", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			BlockHitResult hit = AbilityHelpers.raycastBlock(p, 50.0);
			if (hit.getType() != HitResult.Type.BLOCK) {
				ctx.actionBar("message.projecthero.electro.no_target");
				return;
			}
			if (!spend(ctx, COST_POWER_SURGE)) {
				return;
			}
			BlockPos face = hit.getBlockPos().relative(hit.getDirection());
			if (level.getBlockState(face).isAir() || level.getBlockState(face).canBeReplaced()) {
				TempBlocks.place(level, face, Blocks.REDSTONE_BLOCK.defaultBlockState(), 120);
			}
			bolt(level, hand(p), Vec3.atCenterOf(face));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, face.getX() + 0.5, face.getY() + 0.5, face.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.1);
			MutationVisuals.play(p, "point_right");
			AbilityHelpers.sound(p, SoundEvents.LODESTONE_COMPASS_LOCK, 1.0f, 1.4f);
			ctx.triggerCooldown();
		}));

		// C -- Charged Mode: +10 ability damage, +8 melee (each punch adds a stack), Speed III, a crackling blue shell.
		AbilityHandlers.register(KEY, "charged_mode", Handlers.toggle(
				ctx -> {
					seed(ctx);
					if (ctx.resource("ecell") < 100.0f) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.electro.charge_low");
						return;
					}
					chargedOn(ctx);
					MutationVisuals.play(ctx.player(), "power_up");
					AbilityHelpers.sound(ctx.player(), SoundEvents.BEACON_ACTIVATE, 0.8f, 1.8f);
				},
				ElectrokinesisHandlers::chargedOff,
				ctx -> {
					chargedOn(ctx);
					AbilityHelpers.modeAura(ctx.player(), ParticleTypes.ELECTRIC_SPARK, 4);
					if (ctx.player().tickCount % 5 == 0) {
						ctx.addResource("ecell", -CHARGED_MODE_DRAIN * 5, MAX_CHARGE);
						if (ctx.resource("ecell") <= 0.0f) {
							ctx.setToggled(false);
							chargedOff(ctx);
							ctx.actionBar("message.projecthero.electro.charge_out");
						}
					}
				}));

		// H -- Overcharge: detonate every static stack within 16 blocks.
		AbilityHandlers.register(KEY, "overcharge", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			List<LivingEntity> stacked = new ArrayList<>();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 16.0)) {
				if (stacks(e) > 0) {
					stacked.add(e);
				}
			}
			if (stacked.isEmpty()) {
				ctx.actionBar("message.projecthero.electro.no_stacks");
				return;
			}
			if (!spend(ctx, COST_OVERCHARGE)) {
				return;
			}
			for (LivingEntity e : stacked) {
				int n = consumeStacks(e);
				float dmg = (OVERCHARGE_PER_STACK * n + chargedBonus(p) * 0.5f) * PowerCombos.wetElectricMultiplier(p, e);
				AbilityHelpers.hurtBurst(p, e, AbilityHelpers.kinetic(p), dmg);
				AbilityHelpers.knockbackFrom(e, p.position(), 0.4 + 0.4 * n);
				if (n >= MAX_STACKS) {
					stun(level, e);
				}
				Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
				bolt(level, p.position().add(0, 1.2, 0), c);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20 + 10 * n, 0.4, 0.5, 0.4, 0.3);
				level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			}
			BatchCFx.flatRing(level, p.position().add(0, 1.0, 0), 1.2, 24, blueDust(1.2f), 0.4);
			MutationVisuals.play(p, "p07.overcharge");
			level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.9f, 1.7f);
			ctx.triggerCooldown();
		}));

		// N -- Ion Pull: magnetise every stacked enemy within 20 blocks and yank it to you.
		AbilityHandlers.register(KEY, "ion_pull", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			List<LivingEntity> targets = new ArrayList<>();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 20.0)) {
				if (stacks(e) > 0) {
					targets.add(e);
				}
			}
			boolean fallback = false;
			if (targets.isEmpty()) {
				LivingEntity aimed = AbilityHelpers.raycastEntity(p, 24.0);
				if (aimed == null) {
					ctx.actionBar("message.projecthero.electro.no_stacks");
					return;
				}
				targets.add(aimed);
				fallback = true;
			}
			if (!spend(ctx, COST_ION_PULL)) {
				return;
			}
			for (LivingEntity e : targets) {
				ionYank(p, e);
				if (fallback) {
					addStack(p, e, 1);
				}
			}
			MutationVisuals.play(p, "grab_pull");
			AbilityHelpers.sound(p, SoundEvents.LODESTONE_COMPASS_LOCK, 1.2f, 0.6f);
			AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 2.0f);
			ctx.triggerCooldown();
		}));

		// Charged Mode reactive backlash: hitting a charged hero jolts *them* for 3 and staggers them.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && chargedModeActive(sp) && source.getEntity() instanceof LivingEntity) {
				sp.setHealth(Math.max(1.0f, sp.getHealth() - 3.0f));
				sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true, true));
				if (sp.level() instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, sp.getX(), sp.getY() + 1, sp.getZ(), 16, 0.4, 0.7, 0.4, 0.15);
				}
			}
		});

		// Charged melee: a bonus jolt and a static stack on every punch.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && chargedModeActive(sp)
					&& entity instanceof LivingEntity le) {
				AbilityHelpers.hurt(sp, le, AbilityHelpers.kinetic(sp), 3.0f);
				addStack(sp, le, 1);
			}
			return InteractionResult.PASS;
		});

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, owned) -> {
			if (!owned) {
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, CHARGED_MELEE);
				PowerToggles.clearEffect(player, MobEffects.MOVEMENT_SPEED);
				STORMS.remove(player.getUUID());
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, ElectrokinesisHandlers::passiveTick);
	}

	// ---- attack routines ----------------------------------------------------------------------

	/**
	 * Chain Lightning from {@code first}: up to {@code maxTargets} targets, each hop preferring a stacked enemy within
	 * {@value #HOP_RANGE_STACKED} blocks over the nearest unstacked one within {@value #HOP_RANGE}. Every target hit gains
	 * a stack. Returns the chain (for tests).
	 */
	public static List<LivingEntity> chainLightning(ServerPlayer p, LivingEntity first, int maxTargets) {
		ServerLevel level = (ServerLevel) p.level();
		List<LivingEntity> chain = new ArrayList<>();
		Set<UUID> struck = new HashSet<>();
		chain.add(first);
		struck.add(first.getUUID());
		LivingEntity cur = first;
		while (chain.size() < maxTargets) {
			LivingEntity next = null;
			double best = Double.MAX_VALUE;
			boolean bestStacked = false;
			for (LivingEntity c : AbilityHelpers.hostilesAround(p, cur.position(), HOP_RANGE_STACKED)) {
				if (struck.contains(c.getUUID())) {
					continue;
				}
				boolean stacked = stacks(c) > 0;
				double d = c.distanceToSqr(cur);
				if (!stacked && d > HOP_RANGE * HOP_RANGE) {
					continue;
				}
				if ((stacked && !bestStacked) || (stacked == bestStacked && d < best)) {
					best = d;
					next = c;
					bestStacked = stacked;
				}
			}
			if (next == null) {
				break;
			}
			chain.add(next);
			struck.add(next.getUUID());
			cur = next;
		}
		Vec3 prev = hand(p);
		for (LivingEntity target : chain) {
			Vec3 tp = target.position().add(0, target.getBbHeight() * 0.5, 0);
			bolt(level, prev, tp);
			zap(p, target, CHAIN_DAMAGE);
			prev = tp;
		}
		powerRedstone(level, first.position());
		AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.5f);
		return chain;
	}

	/**
	 * Bolt Form: trace up to 10 blocks along the look direction (stopping short of anything solid), jump there as a
	 * streak of lightning, and zap everything the path passed through. False (nothing spent) if there is no room.
	 */
	private static boolean boltForm(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 dir = p.getLookAngle().normalize();
		Vec3 start = p.position();
		Vec3 last = start;
		for (double d = 0.5; d <= 10.0; d += 0.5) {
			Vec3 pos = start.add(dir.scale(d));
			AABB box = p.getBoundingBox().move(pos.subtract(start));
			if (!level.noCollision(p, box)) {
				break;
			}
			last = pos;
		}
		double dist = last.distanceTo(start);
		if (dist < 1.5) {
			return false;
		}
		Vec3 mid = start.lerp(last, 0.5);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, mid, dist * 0.5 + 2.0)) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			if (distanceToSegment(c, start.add(0, 0.9, 0), last.add(0, 0.9, 0)) <= 1.6) {
				zap(p, e, DASH_DAMAGE);
			}
		}
		bolt(level, start.add(0, 1.0, 0), last.add(0, 1.0, 0));
		level.sendParticles(ParticleTypes.FLASH, start.x, start.y + 1, start.z, 1, 0, 0, 0, 0);
		p.teleportTo(last.x, last.y, last.z);
		AbilityHelpers.launchSelf(p, dir.scale(0.35));
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 4, false, false, false));
		ctx.setResource("no_fall_until", p.level().getGameTime() + 40, 1.0e12f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, last.x, last.y + 1, last.z, 30, 0.4, 0.8, 0.4, 0.3);
		level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8f, 2.0f);
		return true;
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		if (len2 < 1.0e-9) {
			return p.distanceTo(a);
		}
		double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	private static void ionYank(ServerPlayer p, LivingEntity e) {
		Vec3 to = p.position().subtract(e.position());
		double dist = to.length();
		if (dist < 1.5) {
			return;
		}
		Vec3 v = to.normalize().scale(Math.min(2.2, 0.35 + dist * 0.16)).add(0, 0.35, 0);
		AbilityHelpers.push(e, v);
		AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 30, 2);
		bolt((ServerLevel) p.level(), hand(p), e.position().add(0, e.getBbHeight() * 0.5, 0));
	}

	private static void fireStorm(AbilityContext ctx) {
		ctx.setResource("storm_start", 0, 1.0e12f);
		ctx.setResource("storm_charge", 0, 100);
		ServerPlayer p = ctx.player();
		MutationVisuals.stopIf(p, "p07.storm_call");
		if (!spend(ctx, COST_STORM)) {
			return;
		}
		ServerLevel level = ctx.level();
		Vec3 at = AbilityHelpers.aimPoint(p, 40.0);
		strikeVisual(level, at, p);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 5.0)) {
			AbilityHelpers.hurtBurst(p, e, AbilityHelpers.kinetic(p), damage(p, e, STORM_STRIKE));
			addStack(p, e, 2);
		}
		if (AbilityHelpers.canGrief()) {
			BlockPos c = BlockPos.containing(at);
			for (BlockPos bp : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 0, 1))) {
				float hardness = level.getBlockState(bp).getDestroySpeed(level, bp);
				if (bp.distToCenterSqr(at.x, at.y, at.z) <= 3.0 && hardness >= 0 && hardness < 50.0f) {
					level.destroyBlock(bp, false);
				}
			}
		}
		powerRedstone(level, at);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1, at.z, 120, 2.0, 2.0, 2.0, 0.4);
		BatchCFx.flatRing(level, at.add(0, 0.3, 0), 1.0, 32, blueDust(1.5f), 0.6);
		level.playSound(null, BlockPos.containing(at), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 0.8f);
		STORMS.put(p.getUUID(), new Storm(level, at, 4, level.getGameTime() + 12));
		MutationVisuals.play(p, "slam_two_hand");
		ctx.triggerCooldown();
	}

	private static void strikeVisual(ServerLevel level, Vec3 at, ServerPlayer cause) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			bolt.setCause(cause);
			level.addFreshEntity(bolt);
		}
	}

	/** One of the storm's follow-up bolts: a random enemy within 9 blocks of the storm centre (stacked ones first). */
	private static void stormFollowUp(ServerPlayer p, Storm st) {
		List<LivingEntity> near = AbilityHelpers.hostilesAround(p, st.centre, 9.0);
		if (near.isEmpty()) {
			Vec3 r = st.centre.add((st.level.random.nextDouble() - 0.5) * 8, 0, (st.level.random.nextDouble() - 0.5) * 8);
			strikeVisual(st.level, r, p);
			return;
		}
		LivingEntity pick = near.get(st.level.random.nextInt(near.size()));
		for (LivingEntity e : near) {
			if (stacks(e) > stacks(pick)) {
				pick = e;
			}
		}
		strikeVisual(st.level, pick.position(), p);
		AbilityHelpers.hurtBurst(p, pick, AbilityHelpers.kinetic(p), damage(p, pick, STORM_FOLLOW_UP));
		addStack(p, pick, 1);
	}

	/** EMP: suppress redstone contraptions, powered doors, minecarts and mod electronics for 6 s. */
	private static void emp(AbilityContext ctx, double range) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		long until = level.getGameTime() + 6 * 20;
		BlockPos c = p.blockPosition();
		int r = (int) range;
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, -6, -r), c.offset(r, 6, r))) {
			if (bp.distSqr(c) > range * range) {
				continue;
			}
			BlockState st = level.getBlockState(bp);
			boolean tech = respondsToRedstone(st) || st.is(Blocks.OBSERVER) || st.is(Blocks.REPEATER)
					|| st.is(Blocks.COMPARATOR) || st.hasProperty(BlockStateProperties.POWERED);
			if (tech) {
				EMP_UNTIL.put(bp.asLong(), until);
				if (st.hasProperty(BlockStateProperties.POWERED) && st.getValue(BlockStateProperties.POWERED)) {
					level.setBlock(bp, st.setValue(BlockStateProperties.POWERED, false), 3);
				}
				if (st.hasProperty(BlockStateProperties.OPEN)
						&& (st.getBlock() instanceof DoorBlock || st.is(Blocks.IRON_TRAPDOOR))
						&& st.getValue(BlockStateProperties.OPEN)) {
					level.setBlock(bp, st.setValue(BlockStateProperties.OPEN, false), 10);
				}
			}
		}
		for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(range))) {
			if (e instanceof AbstractMinecart mc) {
				mc.setDeltaMovement(Vec3.ZERO);
			}
			if (e instanceof LivingEntity le && isTechnological(e)) {
				AbilityHelpers.applyControl(le, MobEffects.MOVEMENT_SLOWDOWN, 6 * 20, 6);
				AbilityHelpers.applyControl(le, MobEffects.WEAKNESS, 6 * 20, 3);
				addStack(p, le, MAX_STACKS);
			}
		}
		for (int i = 1; i <= 4; i++) {
			BatchCFx.flatRing(level, p.position().add(0, 0.8, 0), i * range / 4.0, 20 + i * 6, blueDust(1.0f), 0);
		}
		AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 1.4f, 0.5f);
		p.displayClientMessage(Component.translatable("message.projecthero.electro.emp"), true);
	}

	private static boolean isTechnological(Entity e) {
		net.minecraft.resources.ResourceLocation id = EntityType.getKey(e.getType());
		String path = id.getPath();
		return e.getType() == EntityType.IRON_GOLEM || path.contains("iron_man") || path.contains("stark")
				|| path.contains("sentinel") || path.contains("drone") || path.contains("turret")
				|| path.contains("robot") || path.contains("mech");
	}

	/** True while the block at {@code pos} is under an active EMP. Consulted by mod machinery if needed. */
	public static boolean empSuppressed(ServerLevel level, BlockPos pos) {
		Long until = EMP_UNTIL.get(pos.asLong());
		return until != null && until > level.getGameTime();
	}

	// ---- passive tick ----------------------------------------------------------------------

	private static final Set<String> STALE = Set.of("storm_bolt", "surge_ready");

	private static void passiveTick(ServerPlayer player) {
		Power power = power();
		if (power == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (!ExperimentalPowers.state(player).resources.containsKey(KEY + "/ecell")) {
			ExperimentalPowers.setResource(player, power, "ecell", MAX_CHARGE, MAX_CHARGE);
		}
		if (player.tickCount % 100 == 0) {
			BatchCFx.purge(player, KEY, STALE, Set.of());
		}

		// Standing near a lightning strike refills the cell instantly.
		if (player.tickCount % 5 == 0) {
			float cell = ExperimentalPowers.getResource(player, power, "ecell");
			boolean nearBolt = !level.getEntitiesOfClass(LightningBolt.class, player.getBoundingBox().inflate(8.0)).isEmpty();
			if (nearBolt) {
				if (cell < MAX_CHARGE) {
					ExperimentalPowers.setResource(player, power, "ecell", MAX_CHARGE, MAX_CHARGE);
				}
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(),
						20, 0.5, 1.0, 0.5, 0.2);
			} else if (cell < MAX_CHARGE && !chargedModeActive(player)) {
				ExperimentalPowers.addResource(player, power, "ecell", (level.isThundering() ? REGEN_STORM : REGEN_CALM) * 5,
						MAX_CHARGE);
			}
			// Super Speed + Electrokinesis combo: the static built up while sprinting tops up the cell.
			float stat = ExperimentalPowers.getResource(player, power, "static_charge");
			if (stat >= 1.0f) {
				ExperimentalPowers.addResource(player, power, "ecell", stat * 3.0f, MAX_CHARGE);
				ExperimentalPowers.setResource(player, power, "static_charge", 0.0f, 100.0f);
			}
		}

		// Charged Mode: +8 melee.
		if (chargedModeActive(player)) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, CHARGED_MELEE, CHARGED_MELEE_BONUS,
					AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, CHARGED_MELEE);
		}

		// Powered rails energise briefly as you run over them, reverting a few seconds later.
		if (player.getDeltaMovement().horizontalDistanceSqr() > 0.02) {
			for (BlockPos bp : new BlockPos[] { player.blockPosition().below(), player.blockPosition() }) {
				BlockState st = level.getBlockState(bp);
				if (st.is(Blocks.POWERED_RAIL) && st.hasProperty(BlockStateProperties.POWERED)
						&& !st.getValue(BlockStateProperties.POWERED)) {
					level.setBlock(bp, st.setValue(BlockStateProperties.POWERED, true), 3);
					RAIL_REVERT.put(bp.asLong(), level.getGameTime() + 60);
				}
			}
		}
		if (!RAIL_REVERT.isEmpty() && player.tickCount % 10 == 0) {
			long now = level.getGameTime();
			RAIL_REVERT.entrySet().removeIf(e -> {
				if (e.getValue() > now) {
					return false;
				}
				BlockPos bp = BlockPos.of(e.getKey());
				BlockState st = level.getBlockState(bp);
				if (st.is(Blocks.POWERED_RAIL) && st.hasProperty(BlockStateProperties.POWERED)
						&& st.getValue(BlockStateProperties.POWERED)) {
					level.setBlock(bp, st.setValue(BlockStateProperties.POWERED, false), 3);
				}
				return true;
			});
		}
	}

	// ---- housekeeping --------------------------------------------------------------------------

	public static void pruneExpired(long now) {
		if (!STACKS.isEmpty()) {
			STACKS.values().removeIf(s -> s.expires <= s.level.getGameTime());
		}
		if (!EMP_UNTIL.isEmpty()) {
			EMP_UNTIL.values().removeIf(expiry -> expiry <= now);
		}
		if (!RAIL_REVERT.isEmpty()) {
			RAIL_REVERT.values().removeIf(expiry -> expiry <= now);
		}
	}

	public static void clearSessionState() {
		STACKS.clear();
		STORMS.clear();
		EMP_UNTIL.clear();
		RAIL_REVERT.clear();
	}

	private static void chargedOn(AbilityContext ctx) {
		PowerToggles.effect(ctx.player(), MobEffects.MOVEMENT_SPEED, 2, true); // Speed III
	}

	private static void chargedOff(AbilityContext ctx) {
		PowerToggles.clearEffect(ctx.player(), MobEffects.MOVEMENT_SPEED);
		PowerToggles.clearModifier(ctx.player(), Attributes.ATTACK_DAMAGE, CHARGED_MELEE);
	}
}
