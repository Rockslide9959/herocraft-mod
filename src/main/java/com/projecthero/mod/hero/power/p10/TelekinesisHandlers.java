package com.projecthero.mod.hero.power.p10;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.GrabHelper;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 10 — Telekinesis.
 *
 * <h2>Psi (v0.10.10)</h2>
 * Every ability in the kit is paid for out of one shared <b>Psi</b> meter, and nothing in the kit works
 * without it. Psi refills on its own, but if it is driven all the way to empty the mind blows a fuse:
 * for {@link #BURNOUT_TICKS} ticks (10 s) every telekinetic ability is locked out, every toggle drops,
 * and the meter only starts refilling once that is over. That is the whole risk model of the power —
 * spend freely, but run the tank dry and you are a normal person for ten seconds.
 *
 * <h2>Slots</h2>
 * <ul>
 *   <li><b>R</b> Force Push (sneak: Force Pull — creatures <em>and</em> loose items), 1 s cooldown.</li>
 *   <li><b>G</b> Telekinetic Barrier — a toggle that makes you untouchable while it burns Psi.</li>
 *   <li><b>X</b> Psychic Flight — cheap, steady Psi drain.</li>
 *   <li><b>Z</b> Psychic Detonation — hold 5 s, hauling everything within 20 blocks off the ground and
 *       in toward you (never closer than 2 blocks), then blow it all apart for 55.</li>
 *   <li><b>V</b> Telekinetic Grab (sneak while empty-handed: Force Crush; sneak while holding: set the
 *       victim down unharmed).</li>
 *   <li><b>C</b> Block Manipulation, cursor-smooth (sneak: tear a 3x3 chunk out of the ground).</li>
 * </ul>
 */
public final class TelekinesisHandlers {
	private static final String KEY = "power_10_telekinesis";
	static final float MAX_PSI = 500.0f;
	private static final float PSI_REGEN_PER_TICK = 1.6f;

	/** How long the power is dead for after the meter is emptied. */
	private static final int BURNOUT_TICKS = 10 * 20;
	/**
	 * Psi does not regenerate for this long after anything spends it. Without a hold-off the trickle
	 * simply outruns every per-tick drain in the kit -- Psychic Flight costs 0.4/tick against 1.6/tick
	 * of regeneration, so flying would REFILL the bar and no channel would ever cost anything.
	 */
	private static final int REGEN_HOLDOFF_TICKS = 20;
	/**
	 * Continuous drains stop at this floor instead of emptying the meter. Only a deliberate spend (or a
	 * blow soaked by the barrier) can burn you out -- having Psychic Flight cut out AND lock your powers
	 * for ten seconds while you are two hundred blocks up is a death sentence, not a risk.
	 */
	private static final float SOFT_FLOOR = 12.0f;

	/** How long, and by how much, the ultimate suppresses Psi regeneration afterwards. */
	private static final int ULT_REGEN_PENALTY_TICKS = 10 * 20;
	private static final float ULT_REGEN_PENALTY = 0.2f;

	// --- costs ---
	private static final float COST_PUSH = 35.0f;
	private static final float COST_PULL = 25.0f;
	private static final float COST_GRAB = 55.0f;
	private static final float COST_BLOCK = 40.0f;
	private static final float COST_CHUNK = 90.0f;
	private static final float COST_ULTIMATE = 320.0f;
	private static final float DRAIN_FLIGHT = 0.4f;
	private static final float DRAIN_BARRIER = 1.6f;
	private static final float DRAIN_BARRIER_PER_DAMAGE = 4.0f;
	private static final float DRAIN_HOLD = 1.0f;
	private static final float DRAIN_CRUSH = 2.5f;

	/**
	 * v0.10.11: every telekinetic move reaches 50 blocks. Force Push's cone, Force Pull's grab, the
	 * detonation's radius, Telekinetic Grab, Force Crush and Block Manipulation all key off this.
	 */
	static final double RANGE = 50.0;

	// --- Z: Psychic Detonation ---
	private static final int ULT_CHARGE_TICKS = 5 * 20;
	private static final int ULT_COOLDOWN = 90 * 20;
	private static final double ULT_RANGE = RANGE;
	private static final double ULT_MIN_DISTANCE = 2.0;
	private static final float ULT_DAMAGE = 55.0f;

	// --- V: Force Crush ---
	private static final int CRUSH_TICKS = 8 * 20;
	private static final float CRUSH_DAMAGE_PER_SECOND = 10.0f;
	private static final net.minecraft.resources.ResourceLocation CRUSH_SLOW =
			com.projecthero.mod.ProjectHeroMod.id("telekinesis_crush_slow");

	// --- C: Block Manipulation ---
	private static final int CHUNK_RADIUS = 1; // 3x3
	private static final float CHUNK_IMPACT_DAMAGE = 26.0f;
	private static final int CHUNK_FLIGHT_TICKS = 40;

	private TelekinesisHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	// ================================================================================ Psi

	/** True while the power is burnt out — every ability refuses and every toggle is forced off. */
	private static boolean burntOut(AbilityContext ctx) {
		return ctx.resource("burnout_until") > ctx.player().level().getGameTime();
	}

	/**
	 * The single gate every ability goes through: refuse outright while burnt out, otherwise spend
	 * {@code amount} of Psi (refusing if there is not enough). Emptying the meter here is what trips the
	 * burnout, so a spend that lands exactly on zero still goes through — you get the ability, and then
	 * you pay for it.
	 */
	private static boolean spendPsi(AbilityContext ctx, float amount) {
		if (burntOut(ctx)) {
			burnoutMessage(ctx);
			return false;
		}
		if (ctx.resource("psi") < amount) {
			ctx.actionBar("message.projecthero.telekinesis.drained");
			return false;
		}
		ctx.addResource("psi", -amount, MAX_PSI);
		holdOffRegen(ctx.player());
		checkBurnout(ctx);
		return true;
	}

	/**
	 * Drain applied per tick by a channel or toggle that is <em>allowed</em> to burn you out -- the
	 * Barrier, Force Crush, and holding a mass aloft. Returns false the moment the meter gives out.
	 */
	private static boolean drainPsi(AbilityContext ctx, float amount) {
		if (burntOut(ctx)) {
			return false;
		}
		ctx.addResource("psi", -amount, MAX_PSI);
		holdOffRegen(ctx.player());
		return !checkBurnout(ctx);
	}

	/** As {@link #drainPsi}, but cuts out at {@link #SOFT_FLOOR} rather than emptying the meter. */
	private static boolean drainPsiSoft(AbilityContext ctx, float amount) {
		if (burntOut(ctx) || ctx.resource("psi") <= SOFT_FLOOR) {
			return false;
		}
		ctx.addResource("psi", -amount, MAX_PSI);
		holdOffRegen(ctx.player());
		return true;
	}

	/** Anything that spends Psi parks its regeneration for a second. */
	private static void holdOffRegen(ServerPlayer p) {
		Power power = power();
		if (power != null) {
			ExperimentalPowers.setResource(p, power, "spend_until",
					p.level().getGameTime() + REGEN_HOLDOFF_TICKS, 1.0e12f);
		}
	}

	/** Trip the burnout if the meter has just hit zero. Returns true if the power is now down. */
	private static boolean checkBurnout(AbilityContext ctx) {
		if (ctx.resource("psi") > 0.0f) {
			return false;
		}
		startBurnout(ctx.player());
		return true;
	}

	private static void startBurnout(ServerPlayer p) {
		Power power = power();
		if (power == null || ExperimentalPowers.getResource(p, power, "burnout_until") > p.level().getGameTime()) {
			return;
		}
		ExperimentalPowers.setResource(p, power, "burnout_until",
				p.level().getGameTime() + BURNOUT_TICKS, 1.0e12f);
		// Everything currently running stops dead: the mind has nothing left to hold any of it up.
		for (AbilitySlot slot : AbilitySlot.values()) {
			ExperimentalPowers.setToggled(p, power, power.ability(slot), false);
		}
		HeroFlight.setFlying(p, false);
		endCrush(p);
		clearChunk(p);
		ExperimentalPowers.setResource(p, power, "ult_start", 0, 1.0e12f);
		ExperimentalPowers.setResource(p, power, "ult_charge", 0, 100);
		p.displayClientMessage(Component.translatable("message.projecthero.telekinesis.burnout")
				.withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE,
				net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);
	}

	private static void burnoutMessage(AbilityContext ctx) {
		long left = (long) ctx.resource("burnout_until") - ctx.player().level().getGameTime();
		ctx.actionBar("message.projecthero.telekinesis.burnout_wait",
				String.format(Locale.ROOT, "%.0f", Math.ceil(Math.max(0L, left) / 20.0)));
	}

	// ================================================================================ registration

	public static void register() {
		// ---- R: Force Push, sneak = Force Pull -----------------------------------------------------
		AbilityHandlers.register(KEY, "force_push", Handlers.instant(ctx -> {
			if (ctx.player().isShiftKeyDown()) {
				forcePull(ctx);
			} else {
				forcePush(ctx);
			}
		}));

		// ---- G: Telekinetic Barrier ----------------------------------------------------------------
		AbilityHandlers.register(KEY, "telekinetic_barrier", Handlers.toggle(
				ctx -> {
					if (burntOut(ctx)) {
						ctx.setToggled(false);
						burnoutMessage(ctx);
						return;
					}
					if (ctx.resource("psi") < DRAIN_BARRIER * 20.0f) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.telekinesis.drained");
						return;
					}
					ctx.actionBar("message.projecthero.telekinesis.barrier_up");
					AbilityHelpers.sound(ctx.player(), SoundEvents.SHIELD_BLOCK, 1.0f, 0.5f);
				},
				ctx -> AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.8f, 0.7f),
				ctx -> {
					if (!drainPsi(ctx, DRAIN_BARRIER)) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.telekinesis.drained");
						return;
					}
					ServerPlayer p = ctx.player();
					// A visible shell, so both the wearer and whoever is shooting at them can see it is up.
					if (p.tickCount % 2 == 0) {
						for (int i = 0; i < 4; i++) {
							double a = (p.tickCount * 0.12) + i * Math.PI / 2.0;
							double h = (i % 2 == 0) ? 0.4 : 1.4;
							ctx.level().sendParticles(ParticleTypes.SCULK_SOUL,
									p.getX() + Math.cos(a) * 1.1, p.getY() + h, p.getZ() + Math.sin(a) * 1.1,
									1, 0.0, 0.0, 0.0, 0.0);
						}
					}
				}));

		// ---- X: Psychic Flight ---------------------------------------------------------------------
		AbilityHandlers.register(KEY, "psychic_flight", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				if (burntOut(ctx) || !HeroFlight.startFlying(ctx.player(), ctx.power())) {
					ctx.setToggled(false);
					if (burntOut(ctx)) {
						burnoutMessage(ctx);
					}
				}
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				HeroFlight.setFlying(ctx.player(), false);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (!HeroFlight.isFlying(p)) {
					ctx.setToggled(false);
					return;
				}
				// v0.10.10: a low, steady trickle -- around a minute of continuous flight on a full bar,
				// so flying is something you can just do, and what actually costs Psi is what you do while
				// you are up there. It cuts out at the soft floor rather than emptying the meter, so it can
				// never drop you out of the sky AND lock your powers at the same time.
				if (!drainPsiSoft(ctx, DRAIN_FLIGHT)) {
					ctx.setToggled(false);
					HeroFlight.setFlying(p, false);
					ctx.actionBar("message.projecthero.telekinesis.drained");
					return;
				}
				ctx.level().sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
			}
		});

		// ---- Z: Psychic Detonation -----------------------------------------------------------------
		AbilityHandlers.register(KEY, "telekinetic_explosion", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ultPress(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ultRelease(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ultChargeTick(ctx);
			}
		});

		// ---- V: Telekinetic Grab / Force Crush -----------------------------------------------------
		AbilityHandlers.register(KEY, "telekinetic_grab", Handlers.instantTicking(
				TelekinesisHandlers::grabPress, TelekinesisHandlers::grabTick));

		// ---- C: Block Manipulation -----------------------------------------------------------------
		AbilityHandlers.register(KEY, "block_manipulation", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.player().isShiftKeyDown()) {
					grabChunk(ctx);
				} else {
					grabSingleBlock(ctx);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				releaseBlocks(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				heldBlockTick(ctx);
				thrownChunkTick(ctx);
			}
		});

		registerPassives();
	}

	// ================================================================================ R

	private static void forcePush(AbilityContext ctx) {
		if (!spendPsi(ctx, COST_PUSH)) {
			return;
		}
		ServerPlayer p = ctx.player();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		// v0.10.11: a 50-block forward cone (~30 degrees) rather than a 4-block bubble in front of you.
		Vec3 mid = eye.add(look.scale(RANGE * 0.5));
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, mid, RANGE * 0.5 + 3.0)) {
			Vec3 toward = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (toward.lengthSqr() > 1.0e-4 && toward.normalize().dot(look) < 0.86) {
				continue;
			}
			// a hard shove -- several blocks of launch on a clear line
			Vec3 dir = e.position().subtract(eye).normalize().scale(2.0).add(0, 0.5, 0);
			AbilityHelpers.push(e, dir);
			AbilityHelpers.knockbackFrom(e, p.position(), 1.6);
			AbilityHelpers.hurt(p, e, 10.0f);
		}
		AbilityHelpers.line(ctx.level(), eye, AbilityHelpers.aimPoint(p, RANGE), ParticleTypes.SCULK_SOUL, 2.0);
		AbilityHelpers.burst(ctx.level(), eye.add(look.scale(4.0)), ParticleTypes.SCULK_SOUL, 20, 0.6);
		AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 0.7f);
		ctx.triggerCooldown();
	}

	/**
	 * v0.10.10: Force Pull is R's sneak variant and reels in <em>items</em> properly — they come to your
	 * feet and are picked up, rather than being nudged vaguely in your direction and left on the floor.
	 */
	private static void forcePull(AbilityContext ctx) {
		if (!spendPsi(ctx, COST_PULL)) {
			return;
		}
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, RANGE);
		if (t != null) {
			Vec3 dir = p.position().subtract(t.position()).normalize().scale(1.6).add(0, 0.3, 0);
			AbilityHelpers.push(t, dir);
		}
		int items = 0;
		for (ItemEntity item : ctx.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(RANGE))) {
			Vec3 to = p.position().add(0, 0.3, 0).subtract(item.position());
			double dist = to.length();
			if (dist > RANGE) {
				continue;
			}
			if (dist < 1.5) {
				item.setNoPickUpDelay(); // close enough: hand it over instead of orbiting the player
			} else {
				item.setDeltaMovement(to.normalize().scale(Math.min(1.4, 0.35 + dist * 0.08)));
				item.hasImpulse = true;
			}
			items++;
		}
		AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, RANGE), ParticleTypes.SCULK_SOUL, 3.0);
		AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 1.3f);
		if (t == null && items == 0) {
			ctx.actionBar("message.projecthero.telekinesis.nothing_to_pull");
		}
		ctx.triggerCooldown();
	}

	// ================================================================================ Z

	private static void ultPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("ult_start") > 0.5f) {
			return;
		}
		if (burntOut(ctx)) {
			burnoutMessage(ctx);
			return;
		}
		if (!ctx.cooldownReady()) {
			ctx.actionBar("message.projecthero.ability.on_cooldown",
					Component.translatable(ctx.ability().nameKey()),
					String.format(Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
			return;
		}
		if (ctx.resource("psi") < COST_ULTIMATE) {
			ctx.actionBar("message.projecthero.telekinesis.drained");
			return;
		}
		ctx.setResource("ult_start", p.level().getGameTime(), 1.0e12f);
		ctx.setResource("ult_charge", 0, 100);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.6f);
	}

	private static void ultRelease(AbilityContext ctx) {
		if (ctx.resource("ult_start") <= 0.5f) {
			return;
		}
		long held = ctx.player().level().getGameTime() - (long) ctx.resource("ult_start");
		if (held >= ULT_CHARGE_TICKS) {
			ultFire(ctx);
		} else {
			ultCancel(ctx, true);
		}
	}

	/**
	 * The 5-second wind-up. Everything alive within {@link #ULT_RANGE} is torn off the ground and dragged
	 * toward the caster — but never inside {@link #ULT_MIN_DISTANCE}, so the crowd hangs in a ring around
	 * them instead of piling into their face and shoving them around.
	 */
	private static void ultChargeTick(AbilityContext ctx) {
		float start = ctx.resource("ult_start");
		if (start <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		long held = p.level().getGameTime() - (long) start;
		if (held < 0 || held > ULT_CHARGE_TICKS + 100 || burntOut(ctx)) {
			ultCancel(ctx, true);
			return;
		}
		double frac = Math.min(1.0, held / (double) ULT_CHARGE_TICKS);
		ctx.setResource("ult_charge", (float) (frac * 100.0), 100);

		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), ULT_RANGE)) {
			Vec3 toCaster = p.position().subtract(e.position());
			double dist = toCaster.length();
			double lift = e.onGround() ? 0.42 : 0.09;
			Vec3 pull = dist > ULT_MIN_DISTANCE
					? toCaster.normalize().scale(Math.min(0.35, 0.05 + dist * 0.02))
					// already at the minimum standoff: hold them out there rather than let them close
					: toCaster.normalize().scale(-0.18);
			e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull.x, lift, pull.z));
			e.hurtMarked = true;
			e.fallDistance = 0.0f;
			e.hasImpulse = true;
		}

		int ring = 6 + (int) (frac * 26);
		for (int i = 0; i < ring; i++) {
			double a = ctx.level().random.nextDouble() * Math.PI * 2;
			double r = ULT_MIN_DISTANCE + ctx.level().random.nextDouble() * (ULT_RANGE - ULT_MIN_DISTANCE) * frac;
			ctx.level().sendParticles(ParticleTypes.SCULK_SOUL,
					p.getX() + Math.cos(a) * r, p.getY() + 0.6 + ctx.level().random.nextDouble() * 2.0,
					p.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (held % 10 == 0) {
			AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.1f, 0.5f + (float) frac);
		}
		if (held >= ULT_CHARGE_TICKS) {
			ultFire(ctx);
		}
	}

	private static void ultCancel(AbilityContext ctx, boolean announce) {
		ctx.setResource("ult_start", 0, 1.0e12f);
		ctx.setResource("ult_charge", 0, 100);
		if (announce) {
			AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.7f, 0.8f);
		}
	}

	private static void ultFire(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		ultCancel(ctx, false);
		if (!spendPsi(ctx, COST_ULTIMATE)) {
			return;
		}
		// The mind is wrung out afterwards: Psi crawls back for the next ten seconds.
		ctx.setResource("regen_slow_until", p.level().getGameTime() + ULT_REGEN_PENALTY_TICKS, 1.0e12f);

		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), ULT_RANGE)) {
			Vec3 dir = e.position().subtract(p.position()).normalize().scale(2.8).add(0, 0.6, 0);
			AbilityHelpers.push(e, dir);
			AbilityHelpers.hurtBurst(p, e, ULT_DAMAGE);
		}
		for (int i = 0; i < 5; i++) {
			double r = ULT_RANGE * (i + 1) / 5.0;
			level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, p.getX(), p.getY() + 1, p.getZ(),
					40, r / 2.0, 1.0, r / 2.0, 0.4);
		}
		level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 1.2f);
		AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.4f, 0.6f);
		ctx.triggerCooldown(ULT_COOLDOWN);
	}

	// ================================================================================ V

	private static void grabPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		boolean sneaking = p.isShiftKeyDown();

		if (ctx.resource("crush_id") > 0.5f) {
			endCrush(p); // sneak or not, a second press lets the victim go
			return;
		}
		if (GrabHelper.isHolding(ctx)) {
			if (sneaking) {
				// v0.10.10: put them down instead of throwing them. Held victims are floated to the ground
				// and given the same brief fall-immunity window the rest of the mod uses, so "I only wanted
				// to move you" no longer costs the target a fall to death.
				setDownSafely(ctx);
			} else {
				GrabHelper.throwHeld(ctx, 2.4, 4.0f);
				AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 0.6f);
				ctx.triggerCooldown();
			}
			return;
		}
		if (sneaking) {
			startCrush(ctx);
			return;
		}
		if (spendPsi(ctx, COST_GRAB) && GrabHelper.tryGrab(ctx, RANGE, 160)) {
			ctx.actionBar("message.projecthero.ability.grabbed");
		}
	}

	private static void setDownSafely(AbilityContext ctx) {
		LivingEntity le = GrabHelper.held(ctx);
		GrabHelper.clear(ctx);
		if (le == null) {
			return;
		}
		le.setDeltaMovement(0, -0.08, 0);
		le.fallDistance = 0.0f;
		le.hurtMarked = true;
		le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, false));
		ctx.level().sendParticles(ParticleTypes.SCULK_SOUL, le.getX(), le.getY() + 0.5, le.getZ(), 12, 0.3, 0.3, 0.3, 0.0);
		AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
		ctx.actionBar("message.projecthero.telekinesis.set_down");
		ctx.triggerCooldown();
	}

	/**
	 * Force Crush: hold a victim in the air, reel them in toward you, and squeeze. Ten damage a second
	 * for as long as the Psi lasts. The caster is nearly rooted while it runs — this is a commitment, not
	 * something you kite with.
	 */
	private static void startCrush(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity target = AbilityHelpers.raycastEntity(p, RANGE);
		if (target == null || !AbilityHelpers.isValidGrabTarget(target, p)) {
			ctx.actionBar("message.projecthero.telekinesis.no_target");
			return;
		}
		if (!spendPsi(ctx, COST_GRAB)) {
			return;
		}
		ctx.setResource("crush_id", target.getId(), 1.0e9f);
		ctx.setResource("crush_ticks", CRUSH_TICKS, CRUSH_TICKS);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, CRUSH_SLOW, -0.85,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ctx.actionBar("message.projecthero.telekinesis.crush");
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 1.2f);
	}

	private static void endCrush(ServerPlayer p) {
		Power power = power();
		if (power == null) {
			return;
		}
		ExperimentalPowers.setResource(p, power, "crush_id", 0, 1.0e9f);
		ExperimentalPowers.setResource(p, power, "crush_ticks", 0, CRUSH_TICKS);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, CRUSH_SLOW);
	}

	private static void grabTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();

		if (GrabHelper.isHolding(ctx)) {
			if (!drainPsi(ctx, DRAIN_HOLD)) {
				GrabHelper.throwHeld(ctx, 0.0, 0.0f);
			}
			GrabHelper.tick(ctx, 3.0);
		}

		int crushId = (int) ctx.resource("crush_id");
		if (crushId == 0) {
			return;
		}
		float left = ctx.resource("crush_ticks") - 1.0f;
		if (!(p.level().getEntity(crushId) instanceof LivingEntity victim) || !victim.isAlive()
				|| left <= 0.0f || p.distanceToSqr(victim) > RANGE * RANGE) {
			endCrush(p);
			return;
		}
		if (!drainPsi(ctx, DRAIN_CRUSH)) {
			endCrush(p);
			ctx.actionBar("message.projecthero.telekinesis.drained");
			return;
		}
		ctx.setResource("crush_ticks", left, CRUSH_TICKS);
		// reel them in to just out of arm's reach and hold them there, wringing them out
		Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(3.5));
		Vec3 to = hold.subtract(victim.position().add(0, victim.getBbHeight() * 0.5, 0));
		victim.setDeltaMovement(to.scale(0.35));
		victim.fallDistance = 0.0f;
		victim.hurtMarked = true;
		victim.hasImpulse = true;
		if ((int) left % 20 == 0) {
			AbilityHelpers.hurt(p, victim, CRUSH_DAMAGE_PER_SECOND);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 1.6f);
		}
		ctx.level().sendParticles(ParticleTypes.SCULK_CHARGE_POP,
				victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 6, 0.4, 0.4, 0.4, 0.05);
	}

	// ================================================================================ C

	/**
	 * v0.10.10: the held block tracks the cursor every single tick.
	 *
	 * <p>It always <em>was</em> repositioned every tick server-side — but {@code EntityType.FALLING_BLOCK}
	 * is registered with {@code updateInterval(20)}, so the tracker only broadcast its new position once a
	 * second and every client saw it teleport in one-second steps ("the block lags and only moves with
	 * the cursor every 2 seconds"). Pushing a teleport packet ourselves each tick is what makes it
	 * actually follow the crosshair.
	 */
	private static void broadcastPos(ServerLevel level, net.minecraft.world.entity.Entity e) {
		level.getChunkSource().broadcastAndSend(e, new ClientboundTeleportEntityPacket(e));
	}

	private static void grabSingleBlock(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!AbilityHelpers.canGrief() || heldBlockCount(ctx) > 0) {
			return;
		}
		var hit = AbilityHelpers.raycastBlock(p, RANGE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos bp = hit.getBlockPos();
		BlockState state = p.level().getBlockState(bp);
		if (!liftable(ctx.level(), bp, state)) {
			return;
		}
		if (!spendPsi(ctx, COST_BLOCK)) {
			return;
		}
		ctx.level().removeBlock(bp, false);
		spawnHeldBlock(ctx, 0, state, Vec3.ZERO);
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 0.8f);
	}

	/**
	 * The sneak variant: tear a 3x3 slab of ground out from under the aim point. Thrown, it hits far
	 * harder than a single block — it is a small landslide rather than a brick.
	 */
	private static void grabChunk(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!AbilityHelpers.canGrief() || heldBlockCount(ctx) > 0) {
			return;
		}
		var hit = AbilityHelpers.raycastBlock(p, RANGE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos centre = hit.getBlockPos();
		List<BlockPos> take = new ArrayList<>();
		for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
			for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
				BlockPos bp = centre.offset(dx, 0, dz);
				if (liftable(ctx.level(), bp, ctx.level().getBlockState(bp))) {
					take.add(bp);
				}
			}
		}
		if (take.isEmpty()) {
			ctx.actionBar("message.projecthero.telekinesis.no_chunk");
			return;
		}
		if (!spendPsi(ctx, COST_CHUNK)) {
			return;
		}
		Vec3 origin = Vec3.atCenterOf(centre);
		for (int i = 0; i < take.size(); i++) {
			BlockPos bp = take.get(i);
			BlockState state = ctx.level().getBlockState(bp);
			ctx.level().removeBlock(bp, false);
			spawnHeldBlock(ctx, i, state, Vec3.atCenterOf(bp).subtract(origin));
		}
		ctx.setResource("chunk_mode", 1, 1);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.6f);
		ctx.actionBar("message.projecthero.telekinesis.chunk");
	}

	private static boolean liftable(ServerLevel level, BlockPos bp, BlockState state) {
		return !state.isAir() && !state.liquid()
				&& state.getDestroySpeed(level, bp) >= 0
				&& state.getDestroySpeed(level, bp) <= 6.0f
				&& level.getBlockEntity(bp) == null; // never swallow a chest and its contents
	}

	private static void spawnHeldBlock(AbilityContext ctx, int index, BlockState state, Vec3 offset) {
		ServerLevel level = ctx.level();
		ServerPlayer p = ctx.player();
		FallingBlockEntity fb = new FallingBlockEntity(EntityType.FALLING_BLOCK, level);
		Vec3 at = holdPoint(p).add(offset);
		fb.setPos(at.x, at.y, at.z);
		fb.setStartPos(BlockPos.containing(at));
		fb.setNoGravity(true);
		fb.dropItem = false;
		fb.disableDrop();
		fb.time = 1;
		fb.setDeltaMovement(Vec3.ZERO);
		CompoundTag tag = new CompoundTag();
		fb.saveWithoutId(tag);
		tag.put("BlockState", NbtUtils.writeBlockState(state));
		fb.load(tag);
		level.addFreshEntity(fb);
		ctx.setResource("block_id" + index, fb.getId(), 1.0e9f);
		ctx.setResource("block_off_x" + index, (float) (offset.x + 8.0), 32.0f);
		ctx.setResource("block_off_y" + index, (float) (offset.y + 8.0), 32.0f);
		ctx.setResource("block_off_z" + index, (float) (offset.z + 8.0), 32.0f);
	}

	private static Vec3 holdPoint(ServerPlayer p) {
		return p.getEyePosition().add(p.getLookAngle().scale(3.5));
	}

	private static int maxHeldBlocks() {
		int side = CHUNK_RADIUS * 2 + 1;
		return side * side;
	}

	private static int heldBlockCount(AbilityContext ctx) {
		int n = 0;
		for (int i = 0; i < maxHeldBlocks(); i++) {
			if (ctx.resource("block_id" + i) > 0.5f) {
				n++;
			}
		}
		return n;
	}

	private static void heldBlockTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("chunk_fly") > 0.5f) {
			return; // in flight: thrownChunkTick owns the pieces now
		}
		Vec3 hold = holdPoint(p);
		boolean any = false;
		for (int i = 0; i < maxHeldBlocks(); i++) {
			int id = (int) ctx.resource("block_id" + i);
			if (id == 0) {
				continue;
			}
			if (!(p.level().getEntity(id) instanceof FallingBlockEntity fb) || !fb.isAlive()) {
				ctx.setResource("block_id" + i, 0, 1.0e9f);
				continue;
			}
			any = true;
			Vec3 off = new Vec3(ctx.resource("block_off_x" + i) - 8.0,
					ctx.resource("block_off_y" + i) - 8.0,
					ctx.resource("block_off_z" + i) - 8.0);
			fb.setPos(hold.x + off.x, hold.y + off.y, hold.z + off.z);
			fb.setDeltaMovement(Vec3.ZERO);
			fb.setNoGravity(true);
			fb.time = 1;
			broadcastPos(ctx.level(), fb);
		}
		if (!any) {
			return;
		}
		// holding a mass aloft steadily bleeds Psi
		if (!drainPsi(ctx, DRAIN_HOLD)) {
			clearChunk(p);
		}
	}

	private static void releaseBlocks(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (heldBlockCount(ctx) == 0) {
			return;
		}
		boolean chunk = ctx.resource("chunk_mode") > 0.5f;
		Vec3 dir = p.getLookAngle();
		for (int i = 0; i < maxHeldBlocks(); i++) {
			int id = (int) ctx.resource("block_id" + i);
			if (id != 0 && p.level().getEntity(id) instanceof FallingBlockEntity fb) {
				fb.setNoGravity(false);
				fb.setDeltaMovement(dir.scale(chunk ? 2.2 : 1.8));
				fb.setHurtsEntities(chunk ? 6.0f : 2.0f, chunk ? 60 : 20);
				fb.time = 1;
			}
		}
		if (chunk) {
			ctx.setResource("chunk_fly", CHUNK_FLIGHT_TICKS, CHUNK_FLIGHT_TICKS);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.5f);
		} else {
			clearBlockSlots(p);
		}
		ctx.setResource("chunk_mode", 0, 1);
	}

	/**
	 * A thrown chunk is a moving wall of rock: whatever it reaches first takes {@link #CHUNK_IMPACT_DAMAGE}
	 * — far more than the single block's landing damage — and the slab bursts apart on the hit.
	 */
	private static void thrownChunkTick(AbilityContext ctx) {
		float left = ctx.resource("chunk_fly");
		if (left <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		ctx.setResource("chunk_fly", left - 1.0f, CHUNK_FLIGHT_TICKS);
		FallingBlockEntity lead = null;
		for (int i = 0; i < maxHeldBlocks() && lead == null; i++) {
			int id = (int) ctx.resource("block_id" + i);
			if (id != 0 && p.level().getEntity(id) instanceof FallingBlockEntity fb && fb.isAlive()) {
				lead = fb;
			}
		}
		if (lead == null || left <= 1.5f) {
			clearChunkTracking(p);
			return;
		}
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, lead.position(), 2.5)) {
			AbilityHelpers.hurtBurst(p, e, CHUNK_IMPACT_DAMAGE);
			AbilityHelpers.knockbackFrom(e, lead.position(), 1.8);
			ctx.level().sendParticles(ParticleTypes.SCULK_CHARGE_POP,
					e.getX(), e.getY() + 1.0, e.getZ(), 30, 0.5, 0.5, 0.5, 0.2);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.7f);
			clearChunk(p);
			return;
		}
	}

	/** Discard every held/thrown piece (they place nothing) and forget them. */
	private static void clearChunk(ServerPlayer p) {
		Power power = power();
		if (power == null) {
			return;
		}
		for (int i = 0; i < maxHeldBlocks(); i++) {
			int id = (int) ExperimentalPowers.getResource(p, power, "block_id" + i);
			if (id != 0 && p.level().getEntity(id) instanceof FallingBlockEntity fb) {
				fb.discard();
			}
		}
		clearChunkTracking(p);
	}

	private static void clearChunkTracking(ServerPlayer p) {
		clearBlockSlots(p);
		Power power = power();
		if (power != null) {
			ExperimentalPowers.setResource(p, power, "chunk_fly", 0, CHUNK_FLIGHT_TICKS);
			ExperimentalPowers.setResource(p, power, "chunk_mode", 0, 1);
		}
	}

	private static void clearBlockSlots(ServerPlayer p) {
		Power power = power();
		if (power == null) {
			return;
		}
		for (int i = 0; i < maxHeldBlocks(); i++) {
			ExperimentalPowers.setResource(p, power, "block_id" + i, 0, 1.0e9f);
		}
	}

	// ================================================================================ passives

	private static void registerPassives() {
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			Power power = power();
			if (power == null) {
				return;
			}
			if (!active) {
				endCrush(player);
				clearChunk(player);
				return;
			}
			if (ExperimentalPowers.getResource(player, power, "psi") <= 0.0f
					&& ExperimentalPowers.getResource(player, power, "burnout_until") <= player.level().getGameTime()) {
				ExperimentalPowers.setResource(player, power, "psi", MAX_PSI, MAX_PSI);
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			Power power = power();
			if (power == null) {
				return;
			}
			long now = player.level().getGameTime();
			float burnoutUntil = ExperimentalPowers.getResource(player, power, "burnout_until");
			if (burnoutUntil > now) {
				// Nothing regenerates during the lockout, and the mind is visibly rattled.
				if (player.tickCount % 5 == 0) {
					player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0, false, false, false));
				}
				return;
			}
			if (burnoutUntil > 0.0f) {
				// the lockout has just expired -- clear it and hand back a working (empty) meter
				ExperimentalPowers.setResource(player, power, "burnout_until", 0, 1.0e12f);
				player.displayClientMessage(
						Component.translatable("message.projecthero.telekinesis.recovered"), true);
			}
			if (ExperimentalPowers.getResource(player, power, "spend_until") > now) {
				return; // something is still spending: no free top-up while a channel is running
			}
			float regen = PSI_REGEN_PER_TICK;
			if (ExperimentalPowers.getResource(player, power, "regen_slow_until") > now) {
				regen *= ULT_REGEN_PENALTY;
			}
			if (ExperimentalPowers.getResource(player, power, "psi") < MAX_PSI) {
				ExperimentalPowers.addResource(player, power, "psi", regen, MAX_PSI);
			}
			if (player.level() instanceof ServerLevel sl) {
				for (ItemEntity item : sl.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(5.0))) {
					item.setDeltaMovement(item.getDeltaMovement().add(
							player.position().subtract(item.position()).normalize().scale(0.02)));
				}
			}
		});
	}

	/**
	 * The Telekinetic Barrier's damage rule, called from {@link com.projecthero.mod.hero.power.HeroDamageRules}:
	 * while the barrier is up nothing gets through, but every blow costs Psi in proportion to what it
	 * would have done. A big enough hit can therefore break the barrier by emptying the meter.
	 */
	public static boolean barrierAbsorbs(ServerPlayer player, float amount) {
		Power power = power();
		if (power == null || !ExperimentalPowers.isToggled(player, power, power.ability(AbilitySlot.SLOT_2))) {
			return false;
		}
		if (ExperimentalPowers.getResource(player, power, "burnout_until") > player.level().getGameTime()) {
			return false;
		}
		ExperimentalPowers.addResource(player, power, "psi", -amount * DRAIN_BARRIER_PER_DAMAGE, MAX_PSI);
		holdOffRegen(player);
		if (player.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
					player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.6, 0.8, 0.6, 0.1);
		}
		if (ExperimentalPowers.getResource(player, power, "psi") <= 0.0f) {
			startBurnout(player);
		}
		return true;
	}
}
