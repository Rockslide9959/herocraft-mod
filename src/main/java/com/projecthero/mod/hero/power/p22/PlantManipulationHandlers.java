package com.projecthero.mod.hero.power.p22;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Power 22 — Plant Manipulation / Chlorokinesis.
 *
 * <p>v0.13.22 revamp (batch E) -- growth and plant minions: every move animates, damage is ~+20% and cooldowns
 * ~-15%, Nature’s Blessing wraps you in a thin bark-and-leaf skin, and two new utility moves: H <b>Thorn Sentry</b>
 * (plant a living turret -- {@link ThornSentryEntity} -- that shoots thorns at hostiles for 15 s, never at you or your
 * squad) and N <b>Spore Cloud</b> (a drifting cloud that poisons and blinds enemies inside it while healing you and
 * your allies). The always-on growth aura now samples random spots instead of scanning ~37k blocks every 5 s.
 */
public final class PlantManipulationHandlers {
	private static final String KEY = "power_22_plant_manipulation_chlorokinesis";
	private static final java.util.Map<java.util.UUID, Long> ROOTED_UNTIL = new java.util.HashMap<>();
	private static final java.util.List<PendingTree> PENDING_TREES = new java.util.ArrayList<>();

	private record PendingTree(ServerLevel level, BlockPos pos, long readyAt) {
	}

	private PlantManipulationHandlers() {
	}

	/**
	 * Whether {@code player} currently has Chlorokinesis selected as their active experimental power --
	 * public so {@code ClipContextMixin} can gate the "swing through grass" fix to only these players
	 * (v0.11.9). Safe to call from either side: {@link com.projecthero.mod.attachment.ModAttachments#EXPERIMENTAL_STATE}
	 * is synced, so a client-side raycast (the one that actually matters for crosshair targeting) reads
	 * the same value a server-side one would.
	 */
	public static boolean isActiveFor(net.minecraft.world.entity.player.Player player) {
		com.projecthero.mod.hero.data.ExperimentalState state =
				player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return state != null && KEY.equals(state.activePower);
	}

	public static void register() {
		// R -- Thorn Shot, fired from the hand. Shift+R is Branch Thrust.
		AbilityHandlers.register(KEY, "thorn_shot", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			if (p.isShiftKeyDown()) {
				boolean onNature = PlantManipulationHandlers.onNatureGround(level, p.blockPosition());
				boolean holdingPlant = p.getMainHandItem().is(Items.BONE_MEAL) || p.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem bi
						&& bi.getBlock() instanceof SaplingBlock;
				if (!onNature && !holdingPlant) {
					ctx.actionBar("message.projecthero.plant.need_nature");
					return;
				}
				LivingEntity t = AbilityHelpers.raycastEntity(p, 15.0);
				if (t == null) {
					return;
				}
				MutationVisuals.play(p, "summon_ground");
				branchThrust(ctx, t);
				ctx.triggerCooldown(170);
				return;
			}
			LivingEntity t = AbilityHelpers.raycastEntity(p, 22.0);
			MutationVisuals.play(p, "cast_right");
			Vec3 from = handOrigin(p);
			AbilityHelpers.line(level, from, AbilityHelpers.aimPoint(p, 22.0), ParticleTypes.COMPOSTER, 3.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 9.5f + natureBonus(p));
				AbilityHelpers.applyControl(t, MobEffects.POISON, 160, 2); // Poison III, 8s
			}
			AbilityHelpers.sound(p, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 0.8f);
			ctx.triggerCooldown();
		}));

		// G -- Thorn Snare: a damage-over-time root. Shift+G is an AoE cone version.
		AbilityHandlers.register(KEY, "vine_grab", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (p.isShiftKeyDown()) {
				MutationVisuals.play(p, "cast_two_hand");
				Vec3 look = p.getLookAngle();
				boolean any = false;
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 10.0)) {
					Vec3 dir = e.position().subtract(p.position());
					if (dir.lengthSqr() < 1.0e-4 || dir.normalize().dot(look) < 0.5) {
						continue;
					}
					rootAndSnare(ctx, e);
					any = true;
				}
				if (any) {
					AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_BREAK, 1.0f, 0.6f);
					ctx.triggerCooldown();
				}
				return;
			}
			LivingEntity t = AbilityHelpers.raycastEntity(p, 16.0);
			MutationVisuals.play(p, "grab_pull");
			if (t != null) {
				AbilityHelpers.line(ctx.level(), handOrigin(p), t.position().add(0, t.getBbHeight() * 0.5, 0), ParticleTypes.COMPOSTER, 3.0);
				rootAndSnare(ctx, t);
				ctx.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, t.getX(), t.getY() + 1, t.getZ(), 15, 0.4, 0.6, 0.4, 0.0);
			}
			AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_BREAK, 1.0f, 0.7f);
			ctx.triggerCooldown();
		}));

		// X -- Vine Swing pulls YOU to a block, or an aimed-at target TO you.
		AbilityHandlers.register(KEY, "vine_swing", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity target = AbilityHelpers.raycastEntity(p, 30.0);
			if (target != null) {
				MutationVisuals.play(p, "grab_pull");
				AbilityHelpers.push(target, p.position().subtract(target.position()).normalize().scale(1.6).add(0, 0.2, 0));
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), target.position(), ParticleTypes.COMPOSTER, 2.0);
				AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_HIT, 1.0f, 0.9f);
				ctx.triggerCooldown();
				return;
			}
			var hit = AbilityHelpers.raycastBlock(p, 100.0);
			if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
				return;
			}
			Vec3 anchor = Vec3.atCenterOf(hit.getBlockPos());
			MutationVisuals.play(p, "p22.swing");
			Vec3 pull = anchor.subtract(p.position());
			AbilityHelpers.launchSelf(p, pull.normalize().scale(1.8).add(0, 0.4, 0));
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), anchor, ParticleTypes.COMPOSTER, 2.0);
			AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_HIT, 1.0f, 1.2f);
			ctx.setResource("no_fall_until", p.level().getGameTime() + 200, 1.0e12f);
			ctx.triggerCooldown();
		}));

		// Z -- hold for 5 seconds to charge Overgrowth.
		AbilityHandlers.register(KEY, "overgrowth", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("og_charging") > 0.5f || !ctx.cooldownReady()) {
					return;
				}
				ctx.setResource("og_charging", 1, 1);
				MutationVisuals.play(ctx.player(), "p22.gather");
				ctx.setResource("og_charge_start", ctx.player().level().getGameTime(), 1.0e12f);
				ctx.setResource("ult_charge", 0, 100);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("og_charging") > 0.5f) {
					ctx.setResource("og_charging", 0, 1);
					ctx.setResource("ult_charge", 0, 100);
					MutationVisuals.stopIf(ctx.player(), "p22.gather");
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("og_charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) ctx.resource("og_charge_start");
				ctx.setResource("ult_charge", (float) Math.min(100.0, held * 100.0 / (5 * 20)), 100);
				MutationVisuals.ensure(p, "p22.gather");
				if (p.tickCount % 3 == 0) {
					ctx.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 4, 0.5, 0.6, 0.5, 0.0);
				}
				if (held < 5 * 20) {
					return;
				}
				ctx.setResource("og_charging", 0, 1);
				ctx.setResource("ult_charge", 0, 100);
				MutationVisuals.play(p, "summon_ground");
				ServerLevel level = ctx.level();
				double r = 20.0;
				for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), r)) {
					AbilityHelpers.hurt(p, e, 54.0f + natureBonus(p));
					AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 200, 8);
					AbilityHelpers.applyControl(e, MobEffects.POISON, 200, 9); // Poison X, 10s
					ROOTED_UNTIL.put(e.getUUID(), p.level().getGameTime() + 200);
					e.setDeltaMovement(e.getDeltaMovement().multiply(0.1, 1, 0.1));
				}
				if (AbilityHelpers.canGrief()) {
					BlockPos standing = p.blockPosition();
					for (int i = 0; i < 260; i++) {
						BlockPos bp = BlockPos.containing(p.getX() + level.random.nextGaussian() * r * 0.4, p.getY(),
								p.getZ() + level.random.nextGaussian() * r * 0.4);
						if (bp.getX() == standing.getX() && bp.getZ() == standing.getZ()) {
							continue; // never place on top of the player
						}
						if (level.getBlockState(bp).canBeReplaced() && level.getBlockState(bp.below()).isSolidRender(level, bp.below())) {
							TempBlocks.place(level, bp, level.random.nextBoolean() ? Blocks.OAK_LEAVES.defaultBlockState() : Blocks.MOSS_CARPET.defaultBlockState(), 120);
						}
					}
				}
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 150, r / 2, 1, r / 2, 0.1);
				AbilityHelpers.sound(p, SoundEvents.GRASS_BREAK, 1.6f, 0.4f);
				ctx.triggerCooldown(51 * 20);
			}
		});

		// V -- Living Wall (unchanged mechanics; sneak-charge still layers on top of the new always-on
		// double-strength bonemeal touch registered below).
		AbilityHandlers.register(KEY, "living_wall", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			if (p.isShiftKeyDown()) {
				MutationVisuals.play(p, "flex");
				ctx.setResource("bonemeal_until", p.level().getGameTime() + 600, 1.0e12f);
				ctx.actionBar("message.projecthero.plant.bonemeal_touch");
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.6, 0.4, 0.0);
				AbilityHelpers.sound(p, SoundEvents.BONE_MEAL_USE, 1.0f, 1.0f);
				ctx.triggerCooldown();
				return;
			}
			BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
			float pitch = p.getXRot();
			MutationVisuals.play(p, pitch < -75.0f ? "cast_raise_both" : "summon_ground");
			boolean built;
			if (pitch < -75.0f) {
				built = com.projecthero.mod.hero.power.ConjuredStructures.dome(p, level, leaves);
			} else if (pitch > 75.0f) {
				built = com.projecthero.mod.hero.power.ConjuredStructures.bridge(p, level, leaves);
			} else {
				built = com.projecthero.mod.hero.power.ConjuredStructures.wall(p, level, leaves);
			}
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.5, p.getZ(), 30, 1.5, 1.5, 1.5, 0.0);
			AbilityHelpers.sound(p, SoundEvents.GRASS_PLACE, 1.0f, 0.6f);
			if (built || !AbilityHelpers.canGrief()) {
				ctx.triggerCooldown();
			}
		}));

		// Always-on: right-clicking a growable plant with an empty hand bonemeals it twice. Gated to an
		// empty main hand so this never intercepts a normal block-placement right-click on grass/dirt.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide() || hand != net.minecraft.world.InteractionHand.MAIN_HAND
					|| !(player instanceof ServerPlayer sp) || !ExperimentalPowers.owns(sp, Powers.byKey(KEY))
					|| !sp.getMainHandItem().isEmpty()) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (state.getBlock() instanceof BonemealableBlock bm && world instanceof ServerLevel sl
					&& bm.isValidBonemealTarget(sl, pos, state) && bm.isBonemealSuccess(sl, sl.random, pos, state)) {
				bm.performBonemeal(sl, sl.random, pos, state);
				// v0.10.20 crash fix: re-resolve the block fresh -- the first bonemeal can turn a sapling
				// into a full tree, and handing the new block's state to the OLD SaplingBlock instance's
				// performBonemeal crashes the server (see growTreeAt for the full explanation).
				BlockState after = sl.getBlockState(pos);
				if (after.getBlock() instanceof BonemealableBlock bm2 && bm2.isValidBonemealTarget(sl, pos, after)) {
					bm2.performBonemeal(sl, sl.random, pos, after);
				}
				sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						16, 0.4, 0.4, 0.4, 0.0);
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			return net.minecraft.world.InteractionResult.PASS;
		});

		// The Living Wall sneak-charge: right-clicking a block bone-meals it (kept for the utility window).
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide() || hand != net.minecraft.world.InteractionHand.MAIN_HAND
					|| !(player instanceof ServerPlayer sp)) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			var power = Powers.byKey(KEY);
			if (power == null || !ExperimentalPowers.owns(sp, power) || !sp.getMainHandItem().isEmpty()
					|| ExperimentalPowers.getResource(sp, power, "bonemeal_until") <= sp.level().getGameTime()) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			net.minecraft.world.item.ItemStack meal = new net.minecraft.world.item.ItemStack(Items.BONE_MEAL);
			boolean grew = net.minecraft.world.item.BoneMealItem.growCrop(meal, world, pos);
			if (!grew && world.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
				grew = net.minecraft.world.item.BoneMealItem.growWaterPlant(meal, world, pos, hitResult.getDirection());
			}
			if (grew) {
				if (world instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.0);
				}
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			return net.minecraft.world.InteractionResult.PASS;
		});

		// C -- Nature's Blessing.
		AbilityHandlers.register(KEY, "natures_blessing", Handlers.toggle(
				ctx -> {
					MutationVisuals.play(ctx.player(), "power_up");
					AbilityHelpers.sound(ctx.player(), SoundEvents.AZALEA_LEAVES_PLACE, 1.0f, 0.8f);
				},
				ctx -> com.projecthero.mod.hero.power.PowerToggles.clearModifier(ctx.player(),
						net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, BLESS_ATK),
				ctx -> {
					ServerPlayer p = ctx.player();
					AbilityHelpers.modeAura(p, ParticleTypes.HAPPY_VILLAGER, 3);
					for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), 3.0)) {
						AbilityHelpers.applyControl(e, MobEffects.POISON, 60, 2); // Poison III
					}
					if (p.tickCount % 20 != 0) {
						return;
					}
					if (isBlessed((ServerLevel) p.level(), p.blockPosition())) {
						p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false, true));
						com.projecthero.mod.hero.power.PowerToggles.modifier(p,
								net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, BLESS_ATK, 10.0,
								net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
					} else {
						com.projecthero.mod.hero.power.PowerToggles.clearModifier(p,
								net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, BLESS_ATK);
					}
				}));

		// H -- Thorn Sentry: plant a living turret that shoots thorns at hostiles for 15 s.
		AbilityHandlers.register(KEY, "thorn_sentry", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			ThornSentryEntity sentry = plantSentry(p);
			if (sentry == null) {
				ctx.actionBar("message.projecthero.plant.no_room");
				return;
			}
			ctx.setResource("sentry_ticks", ThornSentryEntity.LIFETIME, ThornSentryEntity.LIFETIME);
			MutationVisuals.play(p, "summon_ground");
			ctx.triggerCooldown();
		}, ctx -> {
			float left = ctx.resource("sentry_ticks");
			if (left > 0.5f) {
				ctx.setResource("sentry_ticks", left - 1, ThornSentryEntity.LIFETIME);
			}
		}));

		// N -- Spore Cloud: a drifting cloud that poisons + blinds enemies inside it and heals you and your allies.
		// No sneak variant (Sneak+N is reserved for power combos).
		AbilityHandlers.register(KEY, "spore_cloud", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 look = p.getLookAngle();
			Vec3 at = p.position().add(new Vec3(look.x, 0, look.z).normalize().scale(2.5)).add(0, 0.8, 0);
			ExperimentalPowers.setMarker(p, ctx.power(), "spore", BlockPos.containing(at));
			ctx.setResource("spore_ticks", SPORE_TICKS, SPORE_TICKS);
			MutationVisuals.play(p, "p22.spore_blow");
			ctx.level().sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 60, 1.5, 0.8, 1.5, 0.02);
			AbilityHelpers.sound(p, SoundEvents.SPORE_BLOSSOM_PLACE, 1.2f, 0.6f);
			AbilityHelpers.sound(p, SoundEvents.PUFFER_FISH_BLOW_OUT, 0.8f, 0.7f);
			ctx.triggerCooldown();
		}, PlantManipulationHandlers::sporeTick));

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				com.projecthero.mod.hero.power.PowerToggles.clearModifier(player,
						net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, BLESS_ATK);
				ThornSentryEntity.removeFor(player);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, PlantManipulationHandlers::passiveTick);
	}

	private static void passiveTick(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (player.tickCount % 100 == 0 && player.getHealth() < player.getMaxHealth() && nearVegetation(level, player.blockPosition())) {
			player.heal(1.0f);
		}
		// Standing on, or against, living ground grants a small buff.
		if (player.tickCount % 20 == 0 && (onNatureGround(level, player.blockPosition()) || againstNatureWall(level, player.blockPosition()))) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, false, false));
		}
		// Plants within 30 blocks grow roughly 50% faster: an occasional bonemeal-tier assist.
		if (player.tickCount % 100 == 0) {
			// v0.13.22: random samples instead of walking all ~37k positions of the 30-block cylinder every 5 s
			int budget = 25;
			BlockPos origin = player.blockPosition();
			BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
			for (int i = 0; i < 900 && budget > 0; i++) {
				int dx = level.random.nextInt(61) - 30;
				int dz = level.random.nextInt(61) - 30;
				if (dx * dx + dz * dz > 900) {
					continue;
				}
				bp.set(origin.getX() + dx, origin.getY() + level.random.nextInt(13) - 6, origin.getZ() + dz);
				if (!level.isLoaded(bp)) {
					continue;
				}
				BlockState state = level.getBlockState(bp);
				if (state.getBlock() instanceof BonemealableBlock bm && bm.isValidBonemealTarget(level, bp, state)
						&& bm.isBonemealSuccess(level, level.random, bp, state)) {
					bm.performBonemeal(level, level.random, bp, state);
					budget--;
				}
			}
		}
		// Prune stale Thorn Snare / Overgrowth root markers.
		if (!ROOTED_UNTIL.isEmpty() && player.tickCount % 20 == 0) {
			long now = level.getGameTime();
			ROOTED_UNTIL.values().removeIf(t -> t <= now);
		}
		// Branch Thrust: the trail of logs vanishes on its own (TempBlocks); once its time is up, plant
		// and force-grow the tree at the cast point.
		if (!PENDING_TREES.isEmpty()) {
			long now = level.getGameTime();
			PENDING_TREES.removeIf(t -> {
				if (t.readyAt() > now) {
					return false;
				}
				growTreeAt(t.level(), t.pos());
				return true;
			});
		}
	}

	/** Spore Cloud: 8 s, 4.5-block radius, pulses every half second. */
	public static final int SPORE_TICKS = 160;
	public static final double SPORE_RADIUS = 4.5;

	/** Spore Cloud upkeep: particles, and every 10 ticks a pulse -- enemies poisoned + blinded, allies healed. */
	private static void sporeTick(AbilityContext ctx) {
		float left = ctx.resource("spore_ticks");
		if (left <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		ctx.setResource("spore_ticks", left - 1, SPORE_TICKS);
		BlockPos at = ExperimentalPowers.getMarker(p, ctx.power(), "spore");
		var dim = ExperimentalPowers.getMarkerDimension(p, ctx.power(), "spore");
		if (at == null || (dim != null && !dim.equals(p.level().dimension()))) {
			return;
		}
		if (left - 1 <= 0.5f) {
			ExperimentalPowers.clearMarker(p, ctx.power(), "spore");
		}
		ServerLevel level = ctx.level();
		Vec3 c = Vec3.atCenterOf(at);
		if (p.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y, c.z, 6, SPORE_RADIUS * 0.45, 0.7, SPORE_RADIUS * 0.45, 0.0);
			level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.55f, 0.75f, 0.25f), 1.6f),
					c.x, c.y, c.z, 4, SPORE_RADIUS * 0.4, 0.6, SPORE_RADIUS * 0.4, 0.0);
		}
		if (((int) left) % 10 != 0) {
			return;
		}
		pulseSpores(p, c);
	}

	/** One Spore Cloud pulse centred on {@code c}. Public for the gametests. */
	public static void pulseSpores(ServerPlayer p, Vec3 c) {
		ServerLevel level = AbilityHelpers.level(p);
		boolean pvp = p.getServer() != null && p.getServer().isPvpAllowed()
				&& com.projecthero.mod.hero.HeroConfig.get().abilityPvpDamage;
		for (LivingEntity e : AbilityHelpers.living(level, c, SPORE_RADIUS, e -> true)) {
			if (e == p || com.projecthero.mod.squad.Squads.areAllies(p, e)) {
				if (e.getHealth() < e.getMaxHealth()) {
					e.heal(1.5f);
				}
				e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false, true));
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 1.0, e.getZ(), 3, 0.3, 0.4, 0.3, 0.0);
				continue;
			}
			if (!com.projecthero.mod.combat.HeroTargets.isHostile(p, e)) {
				continue; // v0.14.20: a lingering cloud is rule 2 -- threats only, never the farm
			}
			AbilityHelpers.applyControl(e, MobEffects.POISON, 60, 1);
			AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 60, 0);
		}
	}

	/**
	 * Plants a Thorn Sentry on the ground where the player is looking (up to 6 blocks), or at their feet. Any older
	 * sentry of theirs withers away first -- one at a time. Returns null if there is no room.
	 */
	public static ThornSentryEntity plantSentry(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		Vec3 spot;
		var hit = AbilityHelpers.raycastBlock(p, 6.0);
		if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getDirection() == net.minecraft.core.Direction.UP) {
			spot = hit.getLocation();
		} else {
			Vec3 look = p.getLookAngle();
			spot = p.position().add(new Vec3(look.x, 0, look.z).normalize().scale(1.5));
		}
		ThornSentryEntity.removeFor(p);
		ThornSentryEntity sentry = new ThornSentryEntity(PlantEntities.THORN_SENTRY, level);
		sentry.setOwner(p);
		sentry.moveTo(spot.x, spot.y, spot.z, p.getYRot(), 0.0f);
		if (!level.noCollision(sentry)) {
			sentry.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
		}
		level.addFreshEntity(sentry);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.x, spot.y + 0.5, spot.z, 20, 0.4, 0.5, 0.4, 0.0);
		level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
				Blocks.MOSS_BLOCK.defaultBlockState()), spot.x, spot.y + 0.1, spot.z, 24, 0.4, 0.1, 0.4, 0.1);
		AbilityHelpers.sound(p, SoundEvents.ROOTED_DIRT_PLACE, 1.2f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.AZALEA_LEAVES_PLACE, 1.0f, 0.9f);
		return sentry;
	}

	/** Nature’s Blessing is on (the leafy/bark shell overlay). Synced -- safe on either side. */
	public static boolean blessingOn(net.minecraft.world.entity.player.Player p) {
		com.projecthero.mod.hero.data.ExperimentalState st =
				p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY) && st.activeToggles.contains(KEY + "/natures_blessing");
	}

	/** Overgrowth is charging (the root-gathering overlay). */
	public static boolean overgrowthCharging(net.minecraft.world.entity.player.Player p) {
		com.projecthero.mod.hero.data.ExperimentalState st =
				p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY) && st.resources.getOrDefault(KEY + "/og_charging", 0.0f) > 0.5f;
	}

	public static void clearSessionState() {
		ROOTED_UNTIL.clear();
		PENDING_TREES.clear();
	}

	private static void rootAndSnare(AbilityContext ctx, LivingEntity t) {
		ServerPlayer p = ctx.player();
		AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 160, 8);
		AbilityHelpers.applyControl(t, MobEffects.WEAKNESS, 160, 1);
		t.setDeltaMovement(t.getDeltaMovement().multiply(0, 1, 0));
		t.hurtMarked = true;
		ROOTED_UNTIL.put(t.getUUID(), p.level().getGameTime() + 160);
		AbilityHelpers.hurt(p, t, 3.6f + natureBonus(p));
	}

	private static void branchThrust(AbilityContext ctx, LivingEntity target) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 from = p.position();
		Vec3 to = target.position();
		Block log = biomeLog(level, p.blockPosition());
		java.util.List<BlockPos> trail = new java.util.ArrayList<>();
		double dist = from.distanceTo(to);
		int steps = Math.max(1, (int) dist);
		for (int i = 0; i <= steps; i++) {
			Vec3 pt = from.lerp(to, (double) i / steps);
			BlockPos bp = BlockPos.containing(pt);
			if (level.getBlockState(bp).canBeReplaced() && AbilityHelpers.canGrief()) {
				TempBlocks.place(level, bp, log.defaultBlockState(), 100);
				trail.add(bp);
			}
		}
		AbilityHelpers.hurt(p, target, 18.0f + natureBonus(p));
		AbilityHelpers.knockbackFrom(target, from, 1.2);
		AbilityHelpers.sound(p, SoundEvents.WOOD_BREAK, 1.0f, 0.7f);
		if (AbilityHelpers.canGrief()) {
			BlockPos plant = BlockPos.containing(from);
			PENDING_TREES.add(new PendingTree(level, plant, level.getGameTime() + 100));
		}
	}

	/**
	 * Best-effort tree grow: plant the matching sapling at ground level, then force it up with a few
	 * bonemeal ticks.
	 *
	 * <p>v0.10.21 fix: this used to plant one block ABOVE {@code pos} (the cast point, already the air
	 * block a standing player occupies at ground level), leaving the tree floating with a visible gap
	 * -- vanilla's tree feature then converts whatever sits directly under the trunk into dirt, which
	 * is the "dirt block floating one above the ground" the sapling used to leave behind. Plant
	 * straight at {@code pos} instead so the trunk starts right on the ground, and only lay down a
	 * substrate block first when the ground the player was standing on isn't already plantable.
	 *
	 * <p>v0.10.20 crash fix: once a bonemeal application actually grows the sapling into a tree, the
	 * block at {@code pos} is no longer a sapling (it's a log or an air pocket under the canopy) but the
	 * loop kept calling the ORIGINAL {@code SaplingBlock} instance's {@code performBonemeal} against
	 * that new, unrelated block state -- e.g. handing a {@code minecraft:oak_log} state to
	 * {@code SaplingBlock#performBonemeal}, which reads sapling-only block-state properties that the
	 * log state doesn't have and throws, crashing the server a few ticks after the tree finished
	 * growing. Re-resolve the block (not just the state) fresh every iteration and stop the moment it
	 * is no longer a bonemealable target of some kind.
	 */
	private static void growTreeAt(ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos).canBeReplaced()) {
			return;
		}
		if (!onNatureGround(level, pos)) {
			level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 3);
		}
		Block sapling = biomeSapling(level, pos);
		level.setBlock(pos, sapling.defaultBlockState(), 3);
		for (int i = 0; i < 8; i++) {
			BlockState cur = level.getBlockState(pos);
			if (!(cur.getBlock() instanceof BonemealableBlock bm) || !bm.isValidBonemealTarget(level, pos, cur)) {
				break;
			}
			bm.performBonemeal(level, level.random, pos, cur);
		}
	}

	private static Block biomeLog(ServerLevel level, BlockPos pos) {
		var biome = level.getBiome(pos);
		if (biome.is(net.minecraft.world.level.biome.Biomes.BIRCH_FOREST) || biome.is(net.minecraft.world.level.biome.Biomes.OLD_GROWTH_BIRCH_FOREST)) {
			return Blocks.BIRCH_LOG;
		}
		if (biome.is(net.minecraft.world.level.biome.Biomes.TAIGA) || biome.is(net.minecraft.world.level.biome.Biomes.OLD_GROWTH_SPRUCE_TAIGA)
				|| biome.is(net.minecraft.world.level.biome.Biomes.SNOWY_TAIGA)) {
			return Blocks.SPRUCE_LOG;
		}
		if (biome.is(net.minecraft.world.level.biome.Biomes.JUNGLE) || biome.is(net.minecraft.world.level.biome.Biomes.SPARSE_JUNGLE)) {
			return Blocks.JUNGLE_LOG;
		}
		if (biome.is(net.minecraft.world.level.biome.Biomes.SAVANNA) || biome.is(net.minecraft.world.level.biome.Biomes.SAVANNA_PLATEAU)) {
			return Blocks.ACACIA_LOG;
		}
		if (biome.is(net.minecraft.world.level.biome.Biomes.DARK_FOREST)) {
			return Blocks.DARK_OAK_LOG;
		}
		if (biome.is(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE)) {
			return Blocks.CHERRY_LOG;
		}
		if (biome.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP)) {
			return Blocks.MANGROVE_LOG;
		}
		return Blocks.OAK_LOG;
	}

	private static Block biomeSapling(ServerLevel level, BlockPos pos) {
		Block log = biomeLog(level, pos);
		if (log == Blocks.BIRCH_LOG) {
			return Blocks.BIRCH_SAPLING;
		}
		if (log == Blocks.SPRUCE_LOG) {
			return Blocks.SPRUCE_SAPLING;
		}
		if (log == Blocks.JUNGLE_LOG) {
			return Blocks.JUNGLE_SAPLING;
		}
		if (log == Blocks.ACACIA_LOG) {
			return Blocks.ACACIA_SAPLING;
		}
		if (log == Blocks.DARK_OAK_LOG) {
			return Blocks.DARK_OAK_SAPLING;
		}
		if (log == Blocks.CHERRY_LOG) {
			return Blocks.CHERRY_SAPLING;
		}
		if (log == Blocks.MANGROVE_LOG) {
			return Blocks.MANGROVE_PROPAGULE;
		}
		return Blocks.OAK_SAPLING;
	}

	private static Vec3 handOrigin(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0e-6) {
			double yaw = Math.toRadians(p.getYRot());
			right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		} else {
			right = right.normalize();
		}
		return p.getEyePosition().add(look.scale(0.8)).add(right.scale(0.4)).add(0, -0.35, 0);
	}

	private static final net.minecraft.resources.ResourceLocation BLESS_ATK =
			com.projecthero.mod.ProjectHeroMod.id("natures_blessing_atk");

	/** +12 to Plant abilities while Nature's Blessing is on and the player is on living ground. */
	static float natureBonus(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power,
						power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6))
				&& isBlessed((ServerLevel) p.level(), p.blockPosition())
				? 12.0f : 0.0f;
	}

	/** Standing on grass/moss, or within 5 blocks of any plant or leaf. */
	private static boolean isBlessed(ServerLevel level, BlockPos center) {
		var below = level.getBlockState(center.below());
		if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.MOSS_BLOCK) || below.is(Blocks.PODZOL)
				|| below.is(Blocks.MYCELIUM)) {
			return true;
		}
		for (BlockPos bp : BlockPos.betweenClosed(center.offset(-5, -2, -5), center.offset(5, 2, 5))) {
			var st = level.getBlockState(bp);
			if (st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.FLOWERS)
					|| st.is(net.minecraft.tags.BlockTags.SAPLINGS) || st.is(net.minecraft.tags.BlockTags.CROPS)
					|| st.getBlock() instanceof net.minecraft.world.level.block.BushBlock
						|| st.is(Blocks.SHORT_GRASS) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.FERN)
					|| st.is(Blocks.LARGE_FERN) || st.is(Blocks.VINE) || st.is(Blocks.MOSS_CARPET)
					|| st.is(Blocks.MOSS_BLOCK) || st.is(Blocks.BIG_DRIPLEAF) || st.is(Blocks.SMALL_DRIPLEAF)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True when the player is standing on living / natural ground -- grass, moss, dirt family, leaves,
	 * farmland, or any bush/plant. Used by {@link com.projecthero.mod.hero.power.HeroDamageRules} to
	 * cancel fall damage: a chlorokinetic lands softly on anything that grows.
	 */
	public static boolean onNatureGround(ServerLevel level, BlockPos feet) {
		var below = level.getBlockState(feet.below());
		if (below.is(net.minecraft.tags.BlockTags.DIRT) || below.is(net.minecraft.tags.BlockTags.LEAVES)
				|| below.is(Blocks.MOSS_BLOCK) || below.is(Blocks.MOSS_CARPET) || below.is(Blocks.FARMLAND)
				|| below.is(Blocks.HAY_BLOCK) || below.is(Blocks.PINK_PETALS)
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
