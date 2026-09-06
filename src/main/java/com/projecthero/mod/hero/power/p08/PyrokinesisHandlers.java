package com.projecthero.mod.hero.power.p08;

import java.util.Optional;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.power.TimedSelfFlight;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 08 — Pyrokinesis. Every ability leaves fire in its wake where that makes sense, the wielder
 * is completely fire-immune while this is the active power, and Flame Dash doubles as a 20-second
 * flight (sneak + look down).
 */
public final class PyrokinesisHandlers {
	private static final String KEY = "power_08_pyrokinesis";
	/**
	 * Two heat gauges. {@code "flame_body"} is a classic reserve: a pool you spend (~35 s of the
	 * flame-body stance) that refills on its own while dropped. {@code "flamethrower"} is the opposite —
	 * a heat-<em>buildup</em> gauge that starts at zero and <em>climbs</em> while the stream is firing;
	 * at max (~13 s) it overheats and cuts out, then vents back toward zero while not firing.
	 */
	private static final float MAX_HEAT = 500.0f;
	private static final float HEAT_PER_FIRE_TICK = 1.9f; // 500 / 1.9 ≈ 263 ticks ≈ 13 s to overheat
	private static final float HEAT_PER_BODY_TICK = MAX_HEAT / (35 * 20); // Flame Body holds ~35 s
	private static final float HEAT_REGEN = MAX_HEAT / (25 * 20); // full refill over ~25 s
	private static final float HEAT_MIN = 20.0f;
	private static final ResourceLocation FLAME_BODY_ATK = com.projecthero.mod.ProjectHeroMod.id("flame_body_atk");

	private PyrokinesisHandlers() {
	}

	private static boolean fireOk() {
		return HeroConfig.get().abilityFireSpread && AbilityHelpers.canGrief();
	}

	/**
	 * "changes 22": the flamethrower's own stream is exempt from {@link HeroConfig#abilityFireSpread}.
	 * That flag is about <em>incidental</em> fire -- an Inferno fireball's trail, a Flame Dash's wake --
	 * and it defaults to off, which meant a flamethrower aimed at a wall lit nothing at all in a
	 * default install. Directly spraying a surface is the ability; only {@code abilityTerrainDamage}
	 * (via {@link AbilityHelpers#canGrief}) still gates it.
	 */
	private static boolean streamFireOk() {
		return AbilityHelpers.canGrief();
	}

	/** As {@link #placeFire}, but for the flamethrower's directly-sprayed surfaces. */
	private static void placeStreamFire(ServerLevel level, BlockPos pos, int ttl) {
		if (streamFireOk() && level.getBlockState(pos).isAir()) {
			TempBlocks.place(level, pos, BaseFireBlock.getState(level, pos), ttl);
		}
	}

	/** Fill a heat reserve the first time it is referenced (a fresh mutation starts with a full tank). */
	private static void seedHeat(AbilityContext ctx, String name) {
		if (!ExperimentalPowers.state(ctx.player()).resources.containsKey(KEY + "/" + name)) {
			ctx.setResource(name, MAX_HEAT, MAX_HEAT);
		}
	}

	/** +5 to every Pyrokinesis ability's damage while in the Nether. */
	private static float netherBonus(ServerPlayer p) {
		return p.level().dimension() == Level.NETHER ? 5.0f : 0.0f;
	}

	private static void placeFire(ServerLevel level, BlockPos pos, int ttl) {
		if (fireOk() && level.getBlockState(pos).isAir()) {
			TempBlocks.place(level, pos, BaseFireBlock.getState(level, pos), ttl);
		}
	}

	/** Wraps the in-flight Inferno fireball in a rolling ball of flame and lays a light fire trail. */
	private static void infernoTick(AbilityContext ctx) {
		float ticks = ctx.resource("inferno_ticks");
		if (ticks <= 0.5f) {
			return;
		}
		ctx.setResource("inferno_ticks", ticks - 1.0f, 160.0f);
		ServerLevel level = ctx.level();
		Entity fb = level.getEntity((int) ctx.resource("inferno_id"));
		if (fb == null || !fb.isAlive()) {
			ctx.setResource("inferno_ticks", 0, 160.0f);
			return;
		}
		double x = fb.getX();
		double y = fb.getY();
		double z = fb.getZ();
		level.sendParticles(ParticleTypes.FLAME, x, y, z, 40, 1.2, 1.2, 1.2, 0.03);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 8, 0.9, 0.9, 0.9, 0.01);
		level.sendParticles(ParticleTypes.LAVA, x, y, z, 4, 0.7, 0.7, 0.7, 0.0);
		placeFire(level, BlockPos.containing(x, y, z), 60);
	}

	public static void register() {
		AbilityHandlers.register(KEY, "fireball", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 dir = p.getLookAngle();
			LargeFireball fb = new LargeFireball(p.level(), p, dir.scale(1.0), netherBonus(p) > 0 ? 3 : 2);
			fb.setPos(p.getX() + dir.x * 1.0, p.getEyeY() - 0.1, p.getZ() + dir.z * 1.0);
			p.level().addFreshEntity(fb);
			AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 0.9f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "flamethrower", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				// Heat-buildup gauge: still too hot from the last burst to fire again.
				if (ctx.resource("flamethrower") >= MAX_HEAT - HEAT_MIN) {
					ctx.actionBar("message.projecthero.pyro.no_fuel");
					return;
				}
				ctx.setResource("flaming", 1, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ctx.setResource("flaming", 0, 1);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("flaming") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				ServerLevel level = ctx.level();
				Vec3 look = p.getLookAngle();
				Vec3 origin = p.getEyePosition();
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, origin.add(look.scale(2.5)), 3.0)) {
					Vec3 to = e.position().subtract(origin).normalize();
					if (to.dot(look) > 0.6) {
						AbilityHelpers.hurt(p, e, AbilityHelpers.fire(p), 2.0f + netherBonus(p));
						e.setRemainingFireTicks(80);
					}
				}
				// The stream only reaches as far as the first wall/floor it meets.
				double reach = 6.0;
				BlockHitResult bhr = AbilityHelpers.raycastBlock(p, reach);
				double streamLen = bhr.getType() == HitResult.Type.BLOCK ? origin.distanceTo(bhr.getLocation()) : reach;
				for (double d = 0.5; d <= streamLen + 0.01; d += 0.5) {
					Vec3 pt = origin.add(look.scale(d));
					level.sendParticles(ParticleTypes.FLAME, pt.x, pt.y, pt.z, 3, 0.12 * d, 0.12 * d, 0.12 * d, 0.02);
					// fire clings to any solid surface the spray washes across -- the floor it skims
					// over, or a wall/ceiling right next to the stream
					if (p.tickCount % 2 == 0) {
						BlockPos bp = BlockPos.containing(pt);
						boolean nearSurface = !level.getBlockState(bp.below()).isAir()
								|| !level.getBlockState(bp.above()).isAir()
								|| !level.getBlockState(bp.north()).isAir() || !level.getBlockState(bp.south()).isAir()
								|| !level.getBlockState(bp.east()).isAir() || !level.getBlockState(bp.west()).isAir();
						if (nearSurface) {
							placeStreamFire(level, bp, 100);
						}
					}
				}
				// a patch of fire where the stream actually lands
				if (bhr.getType() == HitResult.Type.BLOCK) {
					placeStreamFire(level, bhr.getBlockPos().relative(bhr.getDirection()), 120);
				}
				ctx.addResource("flamethrower", HEAT_PER_FIRE_TICK, MAX_HEAT);
				if (ctx.resource("flamethrower") >= MAX_HEAT) {
					ctx.setResource("flaming", 0, 1);
					ctx.actionBar("message.projecthero.pyro.no_fuel");
				}
			}
		});

		// Flame Dash: a fiery lunge, or -- sneaking + looking down -- 20 seconds of flame flight.
		AbilityHandlers.register(KEY, "flame_dash", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Power power = ctx.power();
			if (TimedSelfFlight.isActive(p, power, "flame")) {
				return; // flame flight in progress -- the move is locked out until it ends (like rock flight)
			}
			if (p.isShiftKeyDown() && p.getXRot() > 75.0f) {
				if (TimedSelfFlight.start(p, power, ctx.ability(), "flame")) {
					AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.7f);
					AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.FLAME, 40, 0.6);
				}
				return;
			}
			AbilityHelpers.addImpulse(p, p.getLookAngle().scale(1.6).add(0, 0.2, 0));
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, false, false, false));
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.FLAME, 24, 0.3);
			placeFire(ctx.level(), p.blockPosition(), 60);
			AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.8f);
			ctx.triggerCooldown();
		}));

		// Inferno: a colossal fireball that flies through the air rather than detonating an area
		// instantly. It starts slow and accelerates, so at range it can be side-stepped -- that dodge
		// window is the balance for how devastating a direct hit (a power-5 explosion) is.
		AbilityHandlers.register(KEY, "inferno", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			Vec3 dir = p.getLookAngle();
			LargeFireball fb = new LargeFireball(p.level(), p, dir, netherBonus(p) > 0 ? 6 : 5);
			fb.accelerationPower = 0.05; // half the vanilla ghast ramp -- slower, easier to miss
			fb.setPos(p.getX() + dir.x * 2.0, p.getEyeY() + dir.y * 2.0 - 0.1, p.getZ() + dir.z * 2.0);
			p.level().addFreshEntity(fb);
			ctx.setResource("inferno_id", fb.getId(), 1.0e9f);
			ctx.setResource("inferno_ticks", 160, 160);
			AbilityHelpers.burst(level, fb.position(), ParticleTypes.FLAME, 90, 1.4);
			level.sendParticles(ParticleTypes.LAVA, fb.getX(), fb.getY(), fb.getZ(), 20, 0.8, 0.8, 0.8, 0.0);
			AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.6f, 0.4f);
			AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.5f, 0.4f);
			ctx.triggerCooldown();
		}, PyrokinesisHandlers::infernoTick));

		// Flame Spark (replaces Flame Wall): a flint-and-steel in your hand. Lights fires, TNT,
		// campfires and nether portals; sneak + use cooks the food you are holding.
		AbilityHandlers.register(KEY, "flame_wall", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			if (p.isShiftKeyDown()) {
				// pull in nearby fire, smelt held wood, and cook every raw item in your hand at once
				int absorbed = absorbNearbyFlames(level, p);
				boolean smelted = smeltHeldWood(p);
				boolean cooked = !smelted && cookHeldFood(p, level);
				if (cooked || smelted || absorbed > 0) {
					AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 0.8f, 1.4f);
					level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.2, p.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
				}
				ctx.triggerCooldown();
				return;
			}
			BlockHitResult hit = AbilityHelpers.raycastBlock(p, 6.0);
			if (hit.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = hit.getBlockPos();
				BlockState st = level.getBlockState(pos);
				BlockPos face = pos.relative(hit.getDirection());
				if (st.is(Blocks.TNT)) {
					level.removeBlock(pos, false);
					level.addFreshEntity(new net.minecraft.world.entity.item.PrimedTnt(
							level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p));
				} else if (st.is(Blocks.CAMPFIRE) || st.is(Blocks.SOUL_CAMPFIRE)) {
					if (!st.getValue(CampfireBlock.LIT)) {
						level.setBlockAndUpdate(pos, st.setValue(BlockStateProperties.LIT, true));
					}
				} else if (level.getBlockState(face).isAir() || level.getBlockState(face).canBeReplaced()) {
					// a real fire block: also ignites nether portals and spreads to adjacent TNT
					level.setBlockAndUpdate(face, BaseFireBlock.getState(level, face));
				}
			}
			AbilityHelpers.sound(p, SoundEvents.FLINTANDSTEEL_USE, 1.0f, 1.0f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "flame_body", Handlers.toggle(
				ctx -> {
					seedHeat(ctx, "flame_body");
					if (ctx.resource("flame_body") <= HEAT_MIN) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.pyro.no_fuel");
						return;
					}
					AbilityHelpers.sound(ctx.player(), SoundEvents.FIRECHARGE_USE, 1.0f, 0.6f);
					ctx.player().level().playSound(null, ctx.player().blockPosition(), SoundEvents.BLAZE_AMBIENT,
							net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.0f);
				},
				ctx -> PowerToggles.clearModifier(ctx.player(), Attributes.ATTACK_DAMAGE, FLAME_BODY_ATK),
				ctx -> {
					ServerPlayer p = ctx.player();
					ServerLevel level = ctx.level();
					PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, FLAME_BODY_ATK, 2.0 + netherBonus(p),
							AttributeModifier.Operation.ADD_VALUE);
					AbilityHelpers.modeAura(p, ParticleTypes.FLAME, 4);
					// leave a trail of fire underfoot as you move
					if (p.tickCount % 2 == 0 && p.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
						placeFire(level, p.blockPosition(), 60);
					}
					// everything within 3 blocks catches fire
					if (p.tickCount % 10 == 0) {
						for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 3.0)) {
							e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 80));
						}
					}
					if (p.tickCount % 30 == 0) {
						p.level().playSound(null, p.blockPosition(), SoundEvents.CAMPFIRE_CRACKLE,
								net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.0f);
					}
					// bleed its own reserve; overheat = the form drops
					ctx.addResource("flame_body", -HEAT_PER_BODY_TICK, MAX_HEAT);
					if (ctx.resource("flame_body") <= 0.0f) {
						ctx.setToggled(false);
						PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, FLAME_BODY_ATK);
						ctx.actionBar("message.projecthero.pyro.no_fuel");
					}
				}));

		// Melee hits from a flame-bodied player set the target alight.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && flameBodyActive(sp) && entity instanceof LivingEntity) {
				entity.setRemainingFireTicks(100);
			}
			return InteractionResult.PASS;
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && flameBodyActive(sp)
					&& source.getEntity() instanceof LivingEntity attacker && sp.distanceToSqr(attacker) < 9.0) {
				attacker.setRemainingFireTicks(80);
			}
		});

		// Passive: complete fire immunity while Pyrokinesis is active (see also HeroDamageRules).
		PowerPassives.register(KEY, (player, active) -> {
			if (active) {
				PowerToggles.effect(player, MobEffects.FIRE_RESISTANCE, 0, false);
			} else {
				PowerToggles.clearEffect(player, MobEffects.FIRE_RESISTANCE);
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, FLAME_BODY_ATK);
			}
		});
		PowerPassives.registerTick(KEY, player -> {
			Power power = Powers.byKey(KEY);
			Ability dash = power.ability(AbilitySlot.SLOT_3);
			TimedSelfFlight.tick(player, power, dash, "flame", ParticleTypes.FLAME);
			PowerToggles.effect(player, MobEffects.FIRE_RESISTANCE, 0, false);
			// A pyrokinetic simply cannot burn -- snuff any ignition the same tick it happens so the
			// player never even visually catches fire (fire immunity already covers the damage).
			if (player.getRemainingFireTicks() > 0) {
				player.clearFire();
			}
			// each reserve refills independently while its own ability is not running
			boolean flaming = ExperimentalPowers.getResource(player, power, "flaming") > 0.5f;
			// flamethrower is a build-up gauge: it climbs in the channel tick and vents back down here.
			com.projecthero.mod.hero.power.ModeMeter.cool(player, power, "flamethrower", MAX_HEAT, HEAT_REGEN, flaming);
			com.projecthero.mod.hero.power.ModeMeter.regen(player, power, "flame_body", MAX_HEAT, HEAT_REGEN,
					flameBodyActive(player));
		});
	}

	/**
	 * Sneak-smelt held wood directly: a stack of logs becomes a block of coal ("charcoal block"),
	 * a stack of planks becomes charcoal. Returns true if it converted the held stack.
	 */
	private static boolean smeltHeldWood(ServerPlayer p) {
		ItemStack held = p.getMainHandItem();
		if (held.isEmpty()) {
			return false;
		}
		int count = held.getCount();
		ItemStack out;
		if (held.is(ItemTags.LOGS)) {
			out = new ItemStack(Blocks.COAL_BLOCK, count);
		} else if (held.is(ItemTags.PLANKS)) {
			out = new ItemStack(Items.CHARCOAL, count);
		} else {
			return false;
		}
		held.setCount(0);
		while (!out.isEmpty()) {
			ItemStack chunk = out.split(Math.min(out.getMaxStackSize(), out.getCount()));
			if (!p.getInventory().add(chunk)) {
				p.drop(chunk, false);
			}
		}
		return true;
	}

	/** Cook the entire held stack of a raw food in one go. */
	private static boolean cookHeldFood(ServerPlayer p, ServerLevel level) {
		ItemStack held = p.getMainHandItem();
		if (held.isEmpty()) {
			return false;
		}
		Optional<net.minecraft.world.item.crafting.RecipeHolder<SmeltingRecipe>> recipe =
				level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(held), level);
		if (recipe.isEmpty()) {
			return false;
		}
		ItemStack single = recipe.get().value().getResultItem(level.registryAccess());
		if (single.isEmpty()) {
			return false;
		}
		int count = held.getCount();
		held.setCount(0);
		ItemStack result = single.copyWithCount(count);
		while (!result.isEmpty()) {
			ItemStack chunk = result.split(Math.min(result.getMaxStackSize(), result.getCount()));
			if (!p.getInventory().add(chunk)) {
				p.drop(chunk, false);
			}
		}
		return true;
	}

	/** Snuff out every fire block (and the caster's own burning) within 5 blocks. */
	private static int absorbNearbyFlames(ServerLevel level, ServerPlayer p) {
		int count = 0;
		BlockPos c = p.blockPosition();
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-5, -3, -5), c.offset(5, 3, 5))) {
			if (level.getBlockState(bp).is(net.minecraft.tags.BlockTags.FIRE)) {
				level.removeBlock(bp, false);
				level.sendParticles(ParticleTypes.SMOKE, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
				count++;
			}
		}
		p.clearFire();
		return count;
	}

	private static boolean flameBodyActive(ServerPlayer p) {
		Power power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}
}
