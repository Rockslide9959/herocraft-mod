package com.projecthero.mod.hero.power.p02;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 02 — Laser Vision. "heat" resource 0..500: builds while beaming, regens otherwise.
 *
 * <p>v0.10.14: the wielder's eyes glow whenever a laser ability is active, they see in the dark and
 * cannot be blinded, and looking at a creature paints a faint targeting reticle (client-side). R is a
 * steady 4-damage-every-half-second beam with a Shift+R utility mode that mines like a diamond tool,
 * G charges up to 3 s for 20/22/24, and Z has to be held 5 s to build the heat before firing a thick
 * Maximum Output beam.
 */
public final class LaserVisionHandlers {
	private static final String KEY = "power_02_laser_vision";
	private static final float MAX_HEAT = 500.0f;

	/** Maximum Output: hold Z this long to build the heat before the beam fires. */
	private static final int MAX_OUTPUT_CHARGE_TICKS = 5 * 20;

	private LaserVisionHandlers() {
	}

	/** True while any Laser Vision ability is actively channelling -- drives the client eye-glow. */
	public static boolean eyesActive(net.minecraft.world.entity.player.Player p) {
		var st = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains(KEY)) {
			return false;
		}
		String pre = KEY + "/";
		return st.resources.getOrDefault(pre + "beaming", 0.0f) > 0.5f
				|| st.resources.getOrDefault(pre + "fb_start", 0.0f) > 0.5f
				|| st.resources.getOrDefault(pre + "mo_start", 0.0f) > 0.5f
				|| st.resources.getOrDefault(pre + "max_ticks", 0.0f) > 0.5f
				|| st.activeToggles.contains(KEY + "/thermal_vision");
	}

	public static void register() {
		// R -- Heat Vision. Hold to beam: 4 damage every half-second. Shift + R switches to utility mode,
		// which mines blocks as fast as a diamond tool but burns heat 50% faster.
		AbilityHandlers.register(KEY, "heat_vision", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("heat") >= MAX_HEAT * 0.9f) {
					ctx.actionBar("message.projecthero.laser.overheated");
					return;
				}
				ctx.setResource("beaming", 1, 1);
				ctx.setResource("beam_util", ctx.player().isShiftKeyDown() ? 1 : 0, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ctx.setResource("beaming", 0, 1);
				ctx.setResource("beam_util", 0, 1);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				boolean beaming = ctx.resource("beaming") > 0.5f;
				if (beaming) {
					boolean util = ctx.resource("beam_util") > 0.5f;
					if (util) {
						utilityBeamTick(ctx);
						ctx.addResource("heat", 3.0f, MAX_HEAT); // 50% faster burn
					} else {
						fireBeam(ctx, 18.0, 0.0f, false, 1);
						if (ctx.player().tickCount % 10 == 0) {
							beamDamage(ctx, 18.0, 4.0f, 0.0);
						}
						ctx.addResource("heat", 2.0f, MAX_HEAT);
					}
					if (ctx.resource("heat") >= MAX_HEAT) {
						ctx.setResource("beaming", 0, 1);
						ctx.setResource("beam_util", 0, 1);
						ctx.actionBar("message.projecthero.laser.overheated");
					}
				} else if (ctx.resource("heat") > 0.0f) {
					ctx.addResource("heat", -4.0f, MAX_HEAT);
				}
			}
		});

		// G -- Focused Beam. Hold to charge, up to 3 s: 1 s = 20 dmg / 9 s cd, 2 s = 22 / 10 s,
		// 3 s = 24 / 12 s. A bar shows the charge.
		AbilityHandlers.register(KEY, "focused_beam", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
					return;
				}
				if (ctx.resource("heat") > MAX_HEAT - 100.0f) {
					ctx.actionBar("message.projecthero.laser.overheated");
					return;
				}
				ctx.setResource("fb_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				float start = ctx.resource("fb_start");
				if (start <= 0.5f) {
					return;
				}
				long held = ctx.player().level().getGameTime() - (long) start;
				if (held > 4 * 20) {
					fireFocusedBeam(ctx, held); // safety: release lost
					return;
				}
				ctx.setResource("fb_charge", (float) Math.min(100.0, held / 60.0 * 100.0), 100);
				ServerPlayer p = ctx.player();
				eyeSpark(ctx, p, 2 + (int) Math.min(8, held / 5));
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				float start = ctx.resource("fb_start");
				if (start <= 0.5f) {
					return;
				}
				fireFocusedBeam(ctx, ctx.player().level().getGameTime() - (long) start);
			}
		});

		AbilityHandlers.register(KEY, "heat_burst", Handlers.instant(ctx -> {
			if (!spendHeat(ctx, 60.0f)) {
				return;
			}
			ServerPlayer p = ctx.player();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition(), 3.5)) {
				AbilityHelpers.hurt(p, e, AbilityHelpers.fire(p), 16.0f);
				AbilityHelpers.knockbackFrom(e, p.position(), 1.1);
				e.setRemainingFireTicks(40);
			}
			AbilityHelpers.burst(ctx.level(), p.getEyePosition().add(p.getLookAngle()), ParticleTypes.FLAME, 30, 0.6);
			AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.8f);
			ctx.triggerCooldown();
		}));

		// Z -- Maximum Output. Hold Z for 5 s while heat-vision particles gather at the eyes, then a thick
		// beam tears out for ~3 s. 65 s cooldown.
		AbilityHandlers.register(KEY, "maximum_output", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("mo_start") > 0.5f || ctx.resource("max_ticks") > 0.5f) {
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
					return;
				}
				if (ctx.resource("heat") > MAX_HEAT - 250.0f) {
					ctx.actionBar("message.projecthero.laser.overheated");
					return;
				}
				ctx.setResource("mo_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				float start = ctx.resource("mo_start");
				if (start <= 0.5f) {
					return;
				}
				long held = ctx.player().level().getGameTime() - (long) start;
				ctx.setResource("mo_start", 0, 1.0e12f);
				ctx.setResource("ult_charge", 0, 100);
				if (held >= MAX_OUTPUT_CHARGE_TICKS) {
					fireMaxOutput(ctx);
				} else {
					AbilityHelpers.sound(ctx.player(), SoundEvents.FIRE_EXTINGUISH, 0.6f, 0.8f);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				float start = ctx.resource("mo_start");
				if (start > 0.5f) {
					ServerPlayer p = ctx.player();
					long held = p.level().getGameTime() - (long) start;
					if (held > MAX_OUTPUT_CHARGE_TICKS + 40) {
						fireMaxOutput(ctx);
						return;
					}
					double frac = Math.min(1.0, held / (double) MAX_OUTPUT_CHARGE_TICKS);
					ctx.setResource("ult_charge", (float) (frac * 100.0), 100);
					eyeSpark(ctx, p, 3 + (int) (frac * 16));
					if (held % 12 == 0) {
						AbilityHelpers.sound(p, SoundEvents.BLAZE_AMBIENT, 0.6f, 0.6f + (float) frac);
					}
					if (held >= MAX_OUTPUT_CHARGE_TICKS && held % 20 == 0) {
						p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
								"message.projecthero.laser.max_ready"), true);
					}
					return;
				}
				int t = (int) ctx.resource("max_ticks");
				if (t <= 0) {
					return;
				}
				ctx.setResource("max_ticks", t - 1, 60);
				fireBeam(ctx, 45.0, 0.0f, true, 3);
				beamDamage(ctx, 45.0, 8.0f, 0.6);
				if (t % 10 == 0) {
					Vec3 impact = AbilityHelpers.aimPoint(ctx.player(), 45.0);
					for (LivingEntity e : AbilityHelpers.enemiesAround(ctx.player(), impact, 5.0)) {
						AbilityHelpers.hurt(ctx.player(), e, AbilityHelpers.fire(ctx.player()), 34.0f);
						e.setRemainingFireTicks(120);
					}
					ctx.level().sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
				}
			}
		});

		AbilityHandlers.register(KEY, "precision_vision", Handlers.instant(ctx -> {
			if (!spendHeat(ctx, 20.0f)) {
				return;
			}
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			BlockHitResult hit = AbilityHelpers.raycastBlock(p, 18.0);
			if (hit.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = hit.getBlockPos();
				BlockState st = level.getBlockState(pos);
				if (st.is(Blocks.TNT)) {
					level.removeBlock(pos, false);
					net.minecraft.world.entity.item.PrimedTnt tnt = new net.minecraft.world.entity.item.PrimedTnt(
							level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p);
					level.addFreshEntity(tnt);
				} else if (AbilityHelpers.canGrief() && (st.is(Blocks.ICE) || st.is(Blocks.SNOW)
						|| st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.GLASS) || st.is(Blocks.POWDER_SNOW))) {
					level.destroyBlock(pos, false);
				} else if (AbilityHelpers.canGrief() && level.getBlockState(hit.getBlockPos().relative(hit.getDirection())).isAir()) {
					level.setBlockAndUpdate(hit.getBlockPos().relative(hit.getDirection()), Blocks.FIRE.defaultBlockState());
				}
				AbilityHelpers.line(level, p.getEyePosition(), Vec3.atCenterOf(pos), ParticleTypes.SMALL_FLAME, 2.0);
			}
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "thermal_vision", Handlers.toggle(Handlers.noop(), Handlers.noop(), ctx -> {
			// v0.10.10: no mode aura. Thermal Vision is something you do behind your own eyes -- the
			// flame particles it used to trail made the user visibly light up for everybody else, which
			// broadcast a purely private power (and, worse, told the room you were scanning them).
			// running the thermal overlay costs a slow trickle of heat
			ctx.addResource("heat", 0.5f, MAX_HEAT);
			if (ctx.resource("heat") >= MAX_HEAT) {
				ctx.setToggled(false);
				ctx.actionBar("message.projecthero.laser.overheated");
				return;
			}
			// The thermal outline itself is drawn client-side for the viewer only (EntityGlowMixin) --
			// no GLOWING effect is applied here, so other players never see the highlighted entities.
		}));

		PowerPassives.registerTick(KEY, player -> {
			// Immune to blindness / darkness, and permanently able to see in the dark.
			if (player.hasEffect(MobEffects.BLINDNESS)) {
				player.removeEffect(MobEffects.BLINDNESS);
			}
			if (player.hasEffect(MobEffects.DARKNESS)) {
				player.removeEffect(MobEffects.DARKNESS);
			}
			var nv = player.getEffect(MobEffects.NIGHT_VISION);
			if (nv == null || nv.getDuration() < 30) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
						MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
			}
			// Glowing eyes while a laser ability is running.
			if (eyesActive(player) && player.level() instanceof ServerLevel sl) {
				Vec3 look = player.getLookAngle();
				Vec3 eye = player.getEyePosition().add(look.scale(0.32));
				Vec3 right = look.cross(new Vec3(0, 1, 0));
				right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
				for (int s = -1; s <= 1; s += 2) {
					Vec3 e = eye.add(right.scale(0.13 * s));
					sl.sendParticles(ParticleTypes.FLAME, e.x, e.y, e.z, 1, 0.01, 0.01, 0.01, 0.0);
					sl.sendParticles(ParticleTypes.SMALL_FLAME, e.x, e.y, e.z, 1, 0.02, 0.02, 0.02, 0.0);
				}
			}
		});
	}

	// ---- shared beam helpers -----------------------------------------------------------------

	private static void eyeSpark(AbilityContext ctx, ServerPlayer p, int count) {
		Vec3 look = p.getLookAngle();
		Vec3 eye = p.getEyePosition().add(look.scale(0.3));
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		for (int s = -1; s <= 1; s += 2) {
			Vec3 e = eye.add(right.scale(0.13 * s));
			ctx.level().sendParticles(ParticleTypes.FLAME, e.x, e.y, e.z, count, 0.06, 0.06, 0.06, 0.02);
			ctx.level().sendParticles(ParticleTypes.SMALL_FLAME, e.x, e.y, e.z, count, 0.05, 0.05, 0.05, 0.01);
		}
	}

	/** Damage whatever the beam is pointed at (entity hit, plus a small splash if {@code splash > 0}). */
	private static void beamDamage(AbilityContext ctx, double range, float damage, double splash) {
		ServerPlayer p = ctx.player();
		LivingEntity target = AbilityHelpers.raycastEntity(p, range);
		Vec3 impact = target != null
				? target.position().add(0, target.getBbHeight() * 0.5, 0)
				: AbilityHelpers.aimPoint(p, range);
		if (target != null) {
			AbilityHelpers.hurt(p, target, AbilityHelpers.fire(p), damage);
			target.setRemainingFireTicks(60);
		}
		if (splash > 0.0) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, impact, splash)) {
				if (e == target) {
					continue;
				}
				AbilityHelpers.hurt(p, e, AbilityHelpers.fire(p), damage * 0.6f);
				e.setRemainingFireTicks(40);
			}
		}
	}

	/** Utility mode: bore through the block the beam lands on as fast as a diamond tool would. */
	private static void utilityBeamTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		fireBeam(ctx, 20.0, 0.0f, false, 1);
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, 20.0);
		if (bhr.getType() != HitResult.Type.BLOCK || !AbilityHelpers.canGrief()) {
			return;
		}
		BlockPos bp = bhr.getBlockPos();
		BlockState st = level.getBlockState(bp);
		float hardness = st.getDestroySpeed(level, bp);
		if (hardness < 0.0f || st.isAir()) {
			return; // bedrock / unbreakable / nothing there
		}
		// Roughly diamond-tool pace: a diamond pickaxe clears hardness-1.5 stone in ~4 ticks. Accumulate
		// a small budget each tick and pop the block once it clears the (hardness-scaled) threshold.
		float budget = ctx.resource("mine_budget") + 8.0f / 20.0f; // diamond base speed / ticks-per-second
		float need = Math.max(0.15f, hardness * 1.5f);
		if (budget >= need) {
			level.destroyBlock(bp, true, p);
			ctx.setResource("mine_budget", 0, 1.0e6f);
		} else {
			ctx.setResource("mine_budget", budget, 1.0e6f);
		}
	}

	private static void fireFocusedBeam(AbilityContext ctx, long heldTicks) {
		ctx.setResource("fb_start", 0, 1.0e12f);
		ctx.setResource("fb_charge", 0, 100);
		int secs = (int) Math.max(1, Math.min(3, heldTicks / 20));
		float dmg = switch (secs) {
			case 1 -> 20.0f;
			case 2 -> 22.0f;
			default -> 24.0f;
		};
		int cd = switch (secs) {
			case 1 -> 9 * 20;
			case 2 -> 10 * 20;
			default -> 12 * 20;
		};
		if (ctx.resource("heat") > MAX_HEAT - 100.0f) {
			ctx.actionBar("message.projecthero.laser.overheated");
			return;
		}
		ctx.addResource("heat", 100.0f, MAX_HEAT);
		fireBeam(ctx, 30.0 + secs * 4.0, dmg, true, 2);
		beamDamage(ctx, 30.0 + secs * 4.0, dmg, 0.0);
		AbilityHelpers.sound(ctx.player(), SoundEvents.BLAZE_SHOOT, 1.0f, 0.6f);
		ctx.triggerCooldown(cd);
	}

	private static void fireMaxOutput(AbilityContext ctx) {
		ctx.setResource("mo_start", 0, 1.0e12f);
		ctx.setResource("ult_charge", 0, 100);
		if (!spendHeat(ctx, 250.0f)) {
			return;
		}
		ctx.setResource("max_ticks", 60, 60);
		AbilityHelpers.sound(ctx.player(), SoundEvents.BLAZE_SHOOT, 1.4f, 0.35f);
		AbilityHelpers.sound(ctx.player(), SoundEvents.GENERIC_EXPLODE, 1.0f, 0.7f);
		ctx.triggerCooldown();
	}

	/** Charge {@code amount} of heat, or refuse (with an overheat message) if there is not room. */
	private static boolean spendHeat(AbilityContext ctx, float amount) {
		if (ctx.resource("heat") > MAX_HEAT - amount) {
			ctx.actionBar("message.projecthero.laser.overheated");
			return false;
		}
		ctx.addResource("heat", amount, MAX_HEAT);
		return true;
	}

	/**
	 * Draw the beam and (when {@code damage > 0}) hit whatever it lands on. {@code width} 1 is the
	 * ordinary twin eye-beam; 2-3 fan extra parallel streaks out for a visibly thicker beam.
	 */
	private static void fireBeam(AbilityContext ctx, double range, float damage, boolean cutBlocks, int width) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 start = p.getEyePosition();
		LivingEntity target = AbilityHelpers.raycastEntity(p, range);
		Vec3 end;
		if (target != null) {
			end = target.position().add(0, target.getBbHeight() * 0.5, 0);
			if (damage > 0.0f) {
				AbilityHelpers.hurt(p, target, AbilityHelpers.fire(p), damage);
				target.setRemainingFireTicks(60);
			}
		} else {
			BlockHitResult bhr = AbilityHelpers.raycastBlock(p, range);
			end = bhr.getType() == HitResult.Type.BLOCK ? bhr.getLocation() : start.add(p.getLookAngle().scale(range));
			if (cutBlocks && bhr.getType() == HitResult.Type.BLOCK && AbilityHelpers.canGrief()) {
				BlockPos bp = bhr.getBlockPos();
				if (level.getBlockState(bp).getDestroySpeed(level, bp) < 3.0f && level.getBlockState(bp).getDestroySpeed(level, bp) >= 0) {
					level.destroyBlock(bp, false, p);
				}
			}
		}
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		Vec3 eyeLine = start.add(0.0, -0.1, 0.0).add(look.scale(0.35));
		double spread = 0.13 * width;
		for (int i = -width; i <= width; i++) {
			Vec3 off = right.scale(i / (double) width * spread);
			Vec3 vOff = up.scale((Math.abs(i) == width ? 0.0 : 0.06) * (width - 1));
			Vec3 origin = eyeLine.add(off).add(vOff);
			Vec3 target2 = end.add(off).add(vOff);
			AbilityHelpers.line(level, origin, target2, ParticleTypes.FLAME, 2.5 * width);
			AbilityHelpers.line(level, origin, target2, ParticleTypes.SMALL_FLAME, 1.5 * width);
		}
	}
}
