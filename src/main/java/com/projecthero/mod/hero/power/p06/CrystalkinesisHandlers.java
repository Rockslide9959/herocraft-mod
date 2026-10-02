package com.projecthero.mod.hero.power.p06;

import java.util.ArrayList;
import java.util.List;

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
import com.projecthero.mod.hero.power.ModeMeter;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.StanceMode;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.revamp.BatchBScheduler;
import com.projecthero.mod.hero.revamp.BatchBUtil;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 06 -- Crystalkinesis (v0.13.22 revamp). Signature: <b>plant crystal nodes, then shatter them</b> (see
 * {@link CrystalNodeEntity}).
 *
 * <h2>Keys</h2>
 * <ul>
 *   <li>R Crystal Shard -- a real flying shard; a node grows where it lands. Sneak: a 5-shard volley.</li>
 *   <li>G Shatter -- detonate every node you own.</li>
 *   <li>X Crystal Path -- glide along a crystal rail that grows beneath you (toggle).</li>
 *   <li>Z Crystal Eruption -- hold to charge; a pillar field, and every node erupts too. Sneak: Colossal Crystal.</li>
 *   <li>V Crystal Prison -- seal a target in amethyst (and plant a node beside it).</li>
 *   <li>C Crystal Armor -- amethyst plating that turns projectiles back as shards.</li>
 *   <li>H Resonance Spire -- a node turret that fires shards at hostiles for 12 s.</li>
 *   <li>N Refract -- a beam; a node it strikes refracts it into three and splits into three nodes.</li>
 * </ul>
 */
public final class CrystalkinesisHandlers {
	public static final String KEY = "power_06_crystalkinesis";
	private static final BlockState CRYSTAL = Blocks.AMETHYST_BLOCK.defaultBlockState();
	private static final BlockParticleOption CRYSTAL_DUST =
			new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState());
	private static final DustParticleOptions BEAM =
			new DustParticleOptions(new org.joml.Vector3f(0.78f, 0.52f, 1.0f), 1.1f);
	private static final net.minecraft.resources.ResourceLocation ARMOR_KB = com.projecthero.mod.ProjectHeroMod.id("crystal_armor_kb");
	private static final net.minecraft.resources.ResourceLocation ARMOR_ATK = com.projecthero.mod.ProjectHeroMod.id("crystal_armor_atk");

	public static final float MAX_STRAIN = 575.0f;
	private static final float STRAIN_DRAIN = MAX_STRAIN / (29 * 20);
	private static final float STRAIN_REGEN = MAX_STRAIN / (36 * 20);
	private static final float REFLECT_COST = 30.0f;

	public static final float SHARD_DAMAGE = 13.0f;
	private static final float VOLLEY_DAMAGE = 7.0f;
	private static final int VOLLEY_CD = 170;
	public static final float SHATTER_DAMAGE = 16.0f;
	public static final double SHATTER_RADIUS = 3.2;
	public static final int ERUPT_CHARGE = 85;
	public static final float ERUPT_DAMAGE = 48.0f;
	private static final int ERUPT_CD = 60 * 20;
	private static final float COLOSSAL_DAMAGE = 66.0f;
	private static final int COLOSSAL_CD = 85 * 20;
	private static final int PATH_MAX_TICKS = 10 * 20;
	private static final int PATH_CD = 102;
	private static final int PRISON_TICKS = 140;
	public static final float REFRACT_DAMAGE = 11.0f;
	public static final float REFRACT_SPLIT_DAMAGE = 9.0f;
	private static final double REFRACT_RANGE = 32.0;

	private CrystalkinesisHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	public static boolean armorActive(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}

	public static boolean onPath(ServerPlayer p) {
		Power power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_3));
	}

	private static float armorBonus(ServerPlayer p) {
		return armorActive(p) ? StanceMode.ABILITY_BONUS : 0.0f;
	}

	public static void register() {
		AbilityHandlers.register(KEY, "crystal_shard", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (p.isShiftKeyDown()) {
				volley(ctx);
				return;
			}
			fireShard(p, SHARD_DAMAGE + armorBonus(p), 0.0f);
			MutationVisuals.play(p, "cast_right");
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "shatter", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			int nodes = shatterAll(p, SHATTER_DAMAGE + armorBonus(p));
			if (nodes == 0) {
				ctx.actionBar("message.projecthero.crystal.no_nodes");
				return;
			}
			MutationVisuals.play(p, "p06.shatter");
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "crystal_path", Handlers.toggle(CrystalkinesisHandlers::pathOn,
				CrystalkinesisHandlers::pathOff, CrystalkinesisHandlers::pathTick));

		AbilityHandlers.register(KEY, "crystal_eruption", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (BatchBUtil.chargeStart(ctx, "crystal_start")) {
					ctx.setResource("crystal_colossal", ctx.player().isShiftKeyDown() ? 1 : 0, 1);
					AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.5f);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				long held = BatchBUtil.chargeHeld(ctx, "crystal_start");
				if (held < 0) {
					return;
				}
				if (held >= ERUPT_CHARGE) {
					eruptFire(ctx);
				} else {
					eruptCancel(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				eruptTick(ctx);
			}
		});

		AbilityHandlers.register(KEY, "crystal_prison", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 16.0);
			if (t == null) {
				ctx.actionBar("message.projecthero.geo.no_target");
				return;
			}
			boolean applied = AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, PRISON_TICKS, 9);
			AbilityHelpers.applyControl(t, MobEffects.JUMP, PRISON_TICKS, -10);
			ServerLevel level = ctx.level();
			if (AbilityHelpers.canGrief() && !(t instanceof Player)) {
				BlockPos base = t.blockPosition();
				for (Direction d : Direction.Plane.HORIZONTAL) {
					TempBlocks.place(level, base.relative(d), CRYSTAL, PRISON_TICKS);
					TempBlocks.place(level, base.relative(d).above(), CRYSTAL, PRISON_TICKS);
				}
				TempBlocks.place(level, base.above(2), CRYSTAL, PRISON_TICKS);
			}
			// a node grows beside the prison -- Shatter it for a follow-up
			Direction side = Direction.fromYRot(p.getYRot()).getOpposite();
			BlockPos nodeCell = t.blockPosition().relative(side, 2);
			if (level.getBlockState(nodeCell).isAir() || level.getBlockState(nodeCell).canBeReplaced()) {
				CrystalNodeEntity.spawn(level, p, Vec3.atBottomCenterOf(nodeCell), Direction.UP, false);
			}
			level.sendParticles(CRYSTAL_DUST, t.getX(), t.getY() + 1, t.getZ(), 40, 0.5, 1.0, 0.5, 0.05);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 0.5f);
			MutationVisuals.play(p, "grab_pull");
			if (applied || AbilityHelpers.canGrief()) {
				ctx.triggerCooldown();
			}
		}));

		AbilityHandlers.register(KEY, "crystal_armor", Handlers.toggle(
				ctx -> {
					if (StanceMode.blockedByCooldown(ctx)) {
						return;
					}
					ModeMeter.ensureSeeded(ctx, "crystal_armor", MAX_STRAIN);
					if (!ModeMeter.hasCharge(ctx, "crystal_armor", 40.0f)) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.crystal.strain_low");
						return;
					}
					armorOn(ctx);
					MutationVisuals.play(ctx.player(), "power_up");
					AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.8f);
				},
				ctx -> {
					armorOff(ctx);
					StanceMode.startDeactivateCooldown(ctx);
				},
				ctx -> {
					armorOn(ctx);
					AbilityHelpers.modeAura(ctx.player(), CRYSTAL_DUST, 2);
					if (!ModeMeter.drain(ctx, "crystal_armor", MAX_STRAIN, STRAIN_DRAIN)) {
						ctx.setToggled(false);
						armorOff(ctx);
						StanceMode.startDeactivateCooldown(ctx);
						ctx.actionBar("message.projecthero.crystal.strain_out");
					}
				}));

		AbilityHandlers.register(KEY, "resonance_spire", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			var bhr = AbilityHelpers.raycastBlock(p, 16.0);
			BlockPos cell;
			if (bhr.getType() == HitResult.Type.BLOCK) {
				cell = bhr.getBlockPos().relative(bhr.getDirection());
				// settle onto the ground below the aimed spot
				for (int i = 0; i < 6 && level.getBlockState(cell.below()).getCollisionShape(level, cell.below()).isEmpty(); i++) {
					cell = cell.below();
				}
			} else {
				cell = p.blockPosition().relative(p.getDirection(), 2);
			}
			if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
				cell = cell.above();
			}
			CrystalNodeEntity.spawn(level, p, Vec3.atBottomCenterOf(cell), Direction.UP, true);
			AbilityHelpers.line(level, AbilityHelpers.handPosition(p), Vec3.atCenterOf(cell), BEAM, 3.0);
			MutationVisuals.play(p, "summon_ground");
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.6f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "refract", Handlers.instant(CrystalkinesisHandlers::refract));

		// Crystal Armor turns projectiles back as crystal shards.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer sp) || !(source.getDirectEntity() instanceof Projectile proj)
					|| source.getEntity() == sp || !armorActive(sp)) {
				return true;
			}
			Power power = power();
			if (ExperimentalPowers.getResource(sp, power, "crystal_armor") < REFLECT_COST) {
				return true;
			}
			ExperimentalPowers.addResource(sp, power, "crystal_armor", -REFLECT_COST, MAX_STRAIN);
			reflect(sp, proj, source.getEntity(), amount);
			return false;
		});

		PowerPassives.registerTick(KEY, player -> {
			Power power = power();
			if (power == null) {
				return;
			}
			ModeMeter.regen(player, power, "crystal_armor", MAX_STRAIN, STRAIN_REGEN, armorActive(player));
			colossalTick(player, power);
			// v0.13.22: Crystal Skate is gone -- clear any left running by an older save
			if (BatchBUtil.get(player, power, "skating") > 0.5f) {
				ExperimentalPowers.setResource(player, power, "skating", 0, 1);
			}
			if (player.tickCount % 10 == 0) {
				int n = CrystalNodeEntity.owned(player, false).size();
				BatchBUtil.set(player, power, "nodes", n, CrystalNodeEntity.MAX_NODES, 0.5f);
			}
		});
		PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				// only this power's own modifiers: the shared Resistance effect is cleared by the toggle's own off
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB);
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ARMOR_ATK);
			}
		});
	}

	// ---- R: shards -------------------------------------------------------------------------

	private static void fireShard(ServerPlayer p, float damage, float spread) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 dir = p.getLookAngle();
		Vec3 hand = AbilityHelpers.handPosition(p);
		CrystalShardEntity shard = new CrystalShardEntity(level, p, damage, true);
		shard.setPos(hand.x, hand.y, hand.z);
		shard.shoot(dir.x, dir.y, dir.z, 2.6f, spread);
		level.addFreshEntity(shard);
		level.sendParticles(CRYSTAL_DUST, hand.x, hand.y, hand.z, 4, 0.05, 0.05, 0.05, 0.05);
	}

	/** Sneak + R: five shards in quick succession, each planting its own node. */
	private static void volley(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float dmg = VOLLEY_DAMAGE + armorBonus(p);
		BatchBScheduler.schedule(ctx.level(), age -> {
			if (!p.isAlive() || p.isRemoved()) {
				return false;
			}
			if (age % 4 == 0) {
				fireShard(p, dmg, 4.0f);
				MutationVisuals.play(p, (age / 4) % 2 == 0 ? "cast_right" : "cast_left");
				AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 1.5f);
			}
			return age < 16;
		});
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
		ctx.triggerCooldown(VOLLEY_CD);
	}

	// ---- G: Shatter ------------------------------------------------------------------------

	/** Detonates every node (and spire) the caster owns. Returns how many went off. */
	public static int shatterAll(ServerPlayer p, float damage) {
		List<CrystalNodeEntity> nodes = CrystalNodeEntity.owned(p, true);
		if (nodes.isEmpty()) {
			return 0;
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 hand = AbilityHelpers.handPosition(p);
		for (CrystalNodeEntity n : nodes) {
			AbilityHelpers.line(level, hand, n.position().add(0, 0.4, 0), ParticleTypes.END_ROD, 0.6);
			n.shatter(p, damage, SHATTER_RADIUS);
		}
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3f, 1.6f);
		return nodes.size();
	}

	// ---- X: Crystal Path -------------------------------------------------------------------

	private static void pathOn(AbilityContext ctx) {
		if (BatchBUtil.onCooldown(ctx)) {
			ctx.setToggled(false);
			return;
		}
		ServerPlayer p = ctx.player();
		ctx.setResource("path_start", p.level().getGameTime(), 1e12f);
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.6f);
		MutationVisuals.play(p, "p06.glide");
	}

	private static void pathOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		MutationVisuals.stopIf(p, "p06.glide");
		if (ctx.resource("path_start") > 0.5f) {
			ctx.setResource("path_start", 0, 1e12f);
			ctx.setResource("no_fall_until", p.level().getGameTime() + 60, 1.0e12f);
			ctx.triggerCooldown(PATH_CD);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8f, 1.2f);
		}
	}

	/**
	 * X: you glide along your aim (climbing or diving up to 35 degrees) and an amethyst rail grows beneath you as you
	 * go -- solid for 3 s, so others can follow on it -- with a line of light running ahead. 10 s at most.
	 */
	private static void pathTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		long now = level.getGameTime();
		float start = ctx.resource("path_start");
		if (start <= 0.5f) {
			ctx.setResource("path_start", now, 1e12f);
			start = now;
		}
		if (now - (long) start > PATH_MAX_TICKS || p.isInWater() || p.getAbilities().flying || p.isPassenger()
				|| !p.isAlive()) {
			ctx.setToggled(false);
			pathOff(ctx);
			return;
		}
		float pitch = Mth.clamp(p.getXRot(), -35.0f, 35.0f);
		Vec3 dir = Vec3.directionFromRotation(pitch, p.getYRot());
		AbilityHelpers.launchSelf(p, dir.scale(0.72));
		MutationVisuals.ensure(p, "p06.glide");
		if (dir.y > -0.05 && AbilityHelpers.canGrief()) {
			// the rail cell's top face never rises above the feet, so it can't catch the glider
			BlockPos cell = BlockPos.containing(p.getX(), p.getY() - 1.0, p.getZ());
			TempBlocks.placeStatic(level, cell, CRYSTAL, 60);
		}
		Vec3 feet = p.position();
		AbilityHelpers.line(level, feet.add(dir.scale(0.8)), feet.add(dir.scale(4.0)), BEAM, 1.5);
		if (p.tickCount % 2 == 0) {
			level.sendParticles(CRYSTAL_DUST, feet.x, feet.y - 0.2, feet.z, 3, 0.25, 0.05, 0.25, 0.02);
		}
		if (p.tickCount % 6 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.PLAYERS, 0.8f, 1.4f);
		}
	}

	// ---- Z: Crystal Eruption / Colossal Crystal -------------------------------------------

	private static void eruptTick(AbilityContext ctx) {
		long held = BatchBUtil.chargeHeld(ctx, "crystal_start");
		if (held < 0) {
			return;
		}
		ServerPlayer p = ctx.player();
		if (held > ERUPT_CHARGE + 100) {
			eruptCancel(ctx);
			return;
		}
		BatchBUtil.chargeMeter(ctx, held, ERUPT_CHARGE);
		MutationVisuals.ensure(p, "channel_two_hand");
		ServerLevel level = ctx.level();
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.25, 1.0, 0.25));
		p.hurtMarked = true;
		double frac = Math.min(1.0, held / (double) ERUPT_CHARGE);
		level.sendParticles(CRYSTAL_DUST, p.getX(), p.getY() + 1.0, p.getZ(),
				4 + (int) (frac * 10), 0.6 * frac + 0.3, 0.5, 0.6 * frac + 0.3, 0.02);
		if (held % 20 == 0) {
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 0.6f + (float) frac);
		}
		if (held >= ERUPT_CHARGE) {
			eruptFire(ctx);
		}
	}

	private static void eruptCancel(AbilityContext ctx) {
		BatchBUtil.chargeClear(ctx, "crystal_start");
		ctx.setResource("crystal_colossal", 0, 1);
		MutationVisuals.stopIf(ctx.player(), "channel_two_hand");
		AbilityHelpers.sound(ctx.player(), SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.4f);
	}

	private static void eruptFire(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		boolean colossal = ctx.resource("crystal_colossal") > 0.5f;
		BatchBUtil.chargeClear(ctx, "crystal_start");
		ctx.setResource("crystal_colossal", 0, 1);

		if (colossal) {
			MutationVisuals.play(p, "throw_right");
			launchColossalCrystal(p, level);
			ctx.triggerCooldown(COLOSSAL_CD);
			return;
		}
		MutationVisuals.play(p, "cast_raise_both");
		double r = 20.0;
		for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), r)) {
			double d = e.position().distanceTo(p.position());
			float dmg = (float) ((ERUPT_DAMAGE + armorBonus(p)) * (1.0 - Math.min(0.55, d / r)));
			AbilityHelpers.hurtBurst(p, e, dmg);
			AbilityHelpers.knockbackFrom(e, p.position(), 1.3);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 80, 2);
		}
		// every node you own erupts along with you
		shatterAll(p, SHATTER_DAMAGE + armorBonus(p));
		if (AbilityHelpers.canGrief()) {
			for (int i = 0; i < 60; i++) {
				double ang = level.random.nextDouble() * Math.PI * 2;
				double dist = 4.0 + level.random.nextDouble() * (r - 4.0);
				int bx = Mth.floor(p.getX() + Math.cos(ang) * dist);
				int bz = Mth.floor(p.getZ() + Math.sin(ang) * dist);
				int gy = p.blockPosition().getY() + 3;
				while (gy > level.getMinBuildHeight() + 1 && level.getBlockState(new BlockPos(bx, gy - 1, bz)).isAir()) {
					gy--;
				}
				int h = 1 + level.random.nextInt(4);
				for (int y = 0; y < h; y++) {
					BlockPos bp = new BlockPos(bx, gy + y, bz);
					BlockState st = level.getBlockState(bp);
					if (st.isAir() || st.canBeReplaced()) {
						TempBlocks.placeStatic(level, bp, CRYSTAL, 160);
					}
				}
			}
		}
		level.sendParticles(CRYSTAL_DUST, p.getX(), p.getY() + 0.5, p.getZ(), 160, r / 2, 0.6, r / 2, 0.2);
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.6f, 0.5f);
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4f, 0.4f);
		ctx.triggerCooldown(ERUPT_CD);
	}

	private static void launchColossalCrystal(ServerPlayer p, ServerLevel level) {
		Vec3 dir = p.getLookAngle().normalize();
		Vec3 spawn = com.projecthero.mod.hero.power.p05.GeokinesisHandlers.airSpawn(level, p, dir);
		FallingBlockEntity crystal = FallingBlockEntity.fall(level, BlockPos.containing(spawn), CRYSTAL);
		crystal.setPos(spawn.x, spawn.y - 0.5, spawn.z);
		crystal.setNoGravity(true);
		crystal.time = 1;
		crystal.setHurtsEntities(0.0f, 0);
		crystal.disableDrop();
		crystal.setDeltaMovement(dir.scale(2.4));
		ExperimentalPowers.setResource(p, power(), "ccrys_id", crystal.getId(), 1e12f);
		ExperimentalPowers.setResource(p, power(), "ccrys_ticks", 70, 70);
		AbilityHelpers.burst(level, spawn, CRYSTAL_DUST, 90, 1.2);
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.6f, 0.4f);
	}

	private static void colossalTick(ServerPlayer player, Power power) {
		float ticks = ExperimentalPowers.getResource(player, power, "ccrys_ticks");
		if (ticks <= 0.5f) {
			return;
		}
		ExperimentalPowers.setResource(player, power, "ccrys_ticks", ticks - 1, 70);
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		Entity e = level.getEntity((int) ExperimentalPowers.getResource(player, power, "ccrys_id"));
		if (!(e instanceof FallingBlockEntity crystal) || !crystal.isAlive()) {
			ExperimentalPowers.setResource(player, power, "ccrys_ticks", 0, 70);
			return;
		}
		Vec3 dir = crystal.getDeltaMovement().normalize();
		crystal.setDeltaMovement(dir.scale(2.4));
		crystal.setNoGravity(true);
		Vec3 c = crystal.position();
		level.sendParticles(CRYSTAL_DUST, c.x, c.y, c.z, 20, 0.6, 0.6, 0.6, 0.02);
		boolean impact = crystal.horizontalCollision || crystal.verticalCollision || crystal.onGround();
		List<LivingEntity> hits = AbilityHelpers.living(level, c, 2.6,
				le -> le != player && le.isAlive() && !(le instanceof net.minecraft.world.entity.decoration.ArmorStand)
						&& (!(le instanceof Player) || (player.getServer() != null && player.getServer().isPvpAllowed()
								&& HeroConfig.get().abilityPvpDamage)));
		if (!hits.isEmpty()) {
			impact = true;
		}
		if (impact || ticks <= 1.5f) {
			for (LivingEntity le : AbilityHelpers.enemiesAround(player, c, 4.0)) {
				AbilityHelpers.hurtBurst(player, le, COLOSSAL_DAMAGE + armorBonus(player));
				AbilityHelpers.knockbackFrom(le, c, 2.4);
				AbilityHelpers.applyControl(le, MobEffects.MOVEMENT_SLOWDOWN, 60, 2);
			}
			// the colossal crystal leaves a ring of nodes where it shatters
			for (int i = 0; i < 3; i++) {
				double a = i * (Math.PI * 2 / 3);
				BlockPos cell = BlockPos.containing(c.x + Math.cos(a) * 2.0, c.y, c.z + Math.sin(a) * 2.0);
				if (level.getBlockState(cell).isAir()) {
					CrystalNodeEntity.spawn(level, player, Vec3.atBottomCenterOf(cell), Direction.UP, false);
				}
			}
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(CRYSTAL_DUST, c.x, c.y, c.z, 200, 2.0, 2.0, 2.0, 0.2);
			level.playSound(null, BlockPos.containing(c), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.8f, 0.4f);
			crystal.discard();
			ExperimentalPowers.setResource(player, power, "ccrys_ticks", 0, 70);
		}
	}

	// ---- N: Refract ------------------------------------------------------------------------

	/**
	 * N: a hitscan beam of refracted light (32 blocks, 11 damage). If it passes through one of your nodes first, the
	 * node refracts it into three beams (9 each) aimed at the nearest enemies it can see -- or fanned out ahead if
	 * there are none -- and the node itself splits into three.
	 */
	private static void refract(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		var bhr = level.clip(new ClipContext(eye, eye.add(look.scale(REFRACT_RANGE)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		double blockDist = bhr.getType() == HitResult.Type.MISS ? REFRACT_RANGE : bhr.getLocation().distanceTo(eye);
		LivingEntity direct = AbilityHelpers.raycastEntity(p, blockDist);
		double directDist = direct == null ? blockDist : direct.getEyePosition().distanceTo(eye);

		// the first of your nodes the beam passes within reach of, before it strikes anything else
		CrystalNodeEntity prism = null;
		double prismDist = Double.MAX_VALUE;
		for (CrystalNodeEntity n : CrystalNodeEntity.owned(p, true)) {
			Vec3 c = n.position().add(0, 0.4, 0);
			double along = c.subtract(eye).dot(look);
			if (along < 0.5 || along > directDist + 0.5 || along >= prismDist) {
				continue;
			}
			double off = eye.add(look.scale(along)).distanceTo(c);
			if (off < 1.1) {
				prism = n;
				prismDist = along;
			}
		}
		Vec3 hand = AbilityHelpers.handPosition(p);
		MutationVisuals.play(p, "point_right");
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.8f);
		ctx.triggerCooldown();
		if (prism == null) {
			Vec3 end = direct != null ? direct.position().add(0, direct.getBbHeight() * 0.6, 0) : eye.add(look.scale(blockDist));
			beam(level, hand, end);
			if (direct != null) {
				AbilityHelpers.hurtLands(p, direct, REFRACT_DAMAGE + armorBonus(p));
			}
			return;
		}
		Vec3 c = prism.position().add(0, 0.4, 0);
		beam(level, hand, c);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, 1.5f);

		// three refracted beams: nearest visible enemies first, then a fan ahead
		List<LivingEntity> targets = new ArrayList<>();
		for (LivingEntity e : AbilityHelpers.hostilesAround(p, c, 16.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0);
			if (level.clip(new ClipContext(c, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, prism))
					.getType() == HitResult.Type.MISS) {
				targets.add(e);
			}
		}
		targets.sort((a, b) -> Double.compare(a.distanceToSqr(c), b.distanceToSqr(c)));
		float split = REFRACT_SPLIT_DAMAGE + armorBonus(p);
		for (int i = 0; i < 3; i++) {
			if (i < targets.size()) {
				LivingEntity t = targets.get(i);
				beam(level, c, t.position().add(0, t.getBbHeight() * 0.5, 0));
				AbilityHelpers.hurtLands(p, t, split);
			} else {
				double yaw = Math.atan2(look.z, look.x) + (i - 1) * 0.7;
				Vec3 d = new Vec3(Math.cos(yaw), look.y * 0.5, Math.sin(yaw)).normalize();
				var h = level.clip(new ClipContext(c, c.add(d.scale(16.0)), ClipContext.Block.COLLIDER,
						ClipContext.Fluid.NONE, prism));
				beam(level, c, h.getLocation());
			}
		}
		// the node splits into three
		Direction face = prism.facing();
		Vec3 base = prism.position();
		Vec3 axisA = face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 axisB = face.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 1)
				: face.getAxis() == Direction.Axis.Z ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
		if (!prism.isSpire()) {
			for (int i = 0; i < 2; i++) {
				Vec3 off = (i == 0 ? axisA : axisB).scale(i == 0 ? 0.9 : -0.9);
				Vec3 at = base.add(off);
				BlockPos cell = BlockPos.containing(at.x, at.y + 0.1, at.z);
				if (level.getBlockState(cell).isAir() || level.getBlockState(cell).canBeReplaced()) {
					CrystalNodeEntity.spawn(level, p, at, face, false);
				}
			}
		}
	}

	private static void beam(ServerLevel level, Vec3 a, Vec3 b) {
		AbilityHelpers.line(level, a, b, BEAM, 3.0);
		AbilityHelpers.line(level, a, b, ParticleTypes.END_ROD, 0.7);
	}

	// ---- C: Crystal Armor ------------------------------------------------------------------

	private static void reflect(ServerPlayer sp, Projectile incoming, Entity attacker, float amount) {
		ServerLevel level = (ServerLevel) sp.level();
		Vec3 from = sp.getEyePosition().add(0, -0.3, 0);
		Vec3 dir;
		if (attacker != null && attacker.isAlive()) {
			dir = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0).subtract(from).normalize();
		} else {
			Vec3 v = incoming.getDeltaMovement();
			dir = v.lengthSqr() < 1.0e-4 ? sp.getLookAngle() : v.normalize().scale(-1);
		}
		CrystalShardEntity shard = new CrystalShardEntity(level, sp, Math.min(20.0f, Math.max(6.0f, amount)), false);
		shard.setPos(from.x + dir.x * 0.8, from.y + dir.y * 0.8, from.z + dir.z * 0.8);
		shard.shoot(dir.x, dir.y, dir.z, 2.4f, 0.0f);
		level.addFreshEntity(shard);
		incoming.discard();
		level.sendParticles(CRYSTAL_DUST, from.x, from.y, from.z, 16, 0.3, 0.3, 0.3, 0.1);
		level.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.2f, 1.8f);
		level.playSound(null, sp.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6f, 1.6f);
	}

	private static void armorOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.effect(p, MobEffects.DAMAGE_RESISTANCE, 0, true);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB, 0.4, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK, StanceMode.MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void armorOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.clearEffect(p, MobEffects.DAMAGE_RESISTANCE);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, ARMOR_KB);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, ARMOR_ATK);
	}
}
