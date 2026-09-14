package com.projecthero.mod.hero.power.p26;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 26 — Magnetic Manipulation. Every ability works on <em>actual</em> magnetic metal in the
 * world or on an enemy (see {@link MagneticMaterials}); with nothing valid in reach an ability fails
 * with subtle feedback and does <b>not</b> spend its cooldown. Mjolnir is hard-excluded everywhere.
 *
 * <p>Launched objects are real entities ({@link FallingBlockEntity} for blocks, {@link ItemEntity}
 * for items), tracked in {@link #PROJECTILES} / {@link #STORMS} and ticked from
 * {@link #tick(MinecraftServer)} (wired in {@code ProjectHeroMod}). Controlled objects never damage
 * their own caster.
 */
public final class MagneticHandlers {
	private static final String KEY = "power_26_magnetic_manipulation";

	private static final double SELECT_RANGE = 30.0;
	private static final double LEAP_RANGE = 24.0;
	private static final double CRUSH_RANGE = 18.0;
	private static final double STORM_RANGE = 12.0;
	private static final int STORM_MAX_OBJECTS = 8;
	private static final float STORM_PER_TARGET_CAP = 30.0f;

	/** Live magnetic projectiles: entity id -> flight/damage bookkeeping. */
	private static final Map<Integer, Proj> PROJECTILES = new HashMap<>();
	/** Live Metal Storms, one per caster. */
	private static final Map<UUID, Storm> STORMS = new HashMap<>();

	private MagneticHandlers() {
	}

	// ================================================================= registration

	public static void register() {
		AbilityHandlers.register(KEY, "ferrous_shot", Handlers.instant(MagneticHandlers::ferrousShot));

		AbilityHandlers.register(KEY, "magnetic_grip", Handlers.instantTicking(
				MagneticHandlers::gripActivate, MagneticHandlers::gripTick));

		AbilityHandlers.register(KEY, "polarity_leap", Handlers.instant(MagneticHandlers::polarityLeap));

		AbilityHandlers.register(KEY, "metal_storm", Handlers.instant(MagneticHandlers::metalStorm));

		AbilityHandlers.register(KEY, "magnetic_crush", Handlers.instant(MagneticHandlers::magneticCrush));

		AbilityHandlers.register(KEY, "magnetic_sense", Handlers.toggle(
				ctx -> AbilityHelpers.sound(ctx.player(), SoundEvents.LODESTONE_COMPASS_LOCK, 0.7f, 1.4f),
				Handlers.noop(),
				ctx -> {
					AbilityHelpers.modeAura(ctx.player(), ParticleTypes.ELECTRIC_SPARK, 2);
					// the actual highlight is client-only + performance-bounded (EntityGlowMixin +
					// MagneticSenseClient) so the server does zero scanning here.
				}));

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, MagneticHandlers::passiveTick);
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				releaseGrip(player, false);
			}
		});
	}

	// ================================================================= R — Ferrous Shot

	private static void ferrousShot(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 look = p.getLookAngle();

		BlockPos blockPos = lookedAtMagneticBlock(p, SELECT_RANGE);
		ItemEntity drop = blockPos == null ? nearestMagneticDrop(level, p, SELECT_RANGE) : null;
		if (blockPos == null && drop == null) {
			blockPos = nearestMagneticBlock(level, p, SELECT_RANGE);
		}
		ItemStack fromInventory = null;
		if (blockPos == null && drop == null) {
			fromInventory = inventoryMagneticAmmo(p);
			if (fromInventory == null) {
				ctx.actionBar("message.projecthero.magnetic.no_metal");
				return;
			}
		}

		Vec3 from = p.getEyePosition().add(look.scale(0.6));
		if (fromInventory != null) {
			MagneticMass mass = MagneticMass.of(fromInventory);
			ItemStack one = fromInventory.copyWithCount(1);
			fromInventory.shrink(1);
			ItemEntity proj = new ItemEntity(level, from.x, from.y, from.z, one);
			proj.setDeltaMovement(look.scale(mass.launchSpeed + 0.4));
			proj.setNoGravity(true);
			proj.setPickUpDelay(60);
			level.addFreshEntity(proj);
			track(level, proj, p, mass.damage, mass.knockback, 45);
			streak(level, from, look, ParticleTypes.ELECTRIC_SPARK);
			AbilityHelpers.sound(p, SoundEvents.IRON_GOLEM_HURT, 0.6f, 1.7f);
		} else if (blockPos != null) {
			BlockState state = level.getBlockState(blockPos);
			MagneticMass mass = MagneticMass.of(state);
			boolean nether = MagneticMaterials.isNetherite(state);
			FallingBlockEntity fbe = FallingBlockEntity.fall(level, blockPos, state);
			fbe.time = 1;
			fbe.setNoGravity(false);
			fbe.setDeltaMovement(look.scale(mass.launchSpeed * (nether ? 0.6 : 1.0)));
			fbe.hasImpulse = true;
			track(level, fbe, p, mass.damage * (nether ? 1.15f : 1.0f), mass.knockback, 60);
			streak(level, from, look, ParticleTypes.CRIT);
			AbilityHelpers.sound(p, nether ? SoundEvents.NETHERITE_BLOCK_PLACE : SoundEvents.ANVIL_LAND,
					0.9f, mass == MagneticMass.LIGHT ? 1.4f : 0.7f);
		} else {
			MagneticMass mass = MagneticMass.of(drop.getItem());
			ItemStack one = drop.getItem().copyWithCount(1);
			drop.getItem().shrink(1);
			if (drop.getItem().isEmpty()) {
				drop.discard();
			}
			ItemEntity proj = new ItemEntity(level, from.x, from.y, from.z, one);
			proj.setDeltaMovement(look.scale(mass.launchSpeed + 0.4));
			proj.setNoGravity(true);
			proj.setPickUpDelay(60);
			level.addFreshEntity(proj);
			track(level, proj, p, mass.damage, mass.knockback, 45);
			streak(level, from, look, ParticleTypes.ELECTRIC_SPARK);
			AbilityHelpers.sound(p, SoundEvents.IRON_GOLEM_HURT, 0.6f, 1.7f);
		}
		ctx.triggerCooldown();
	}

	// ================================================================= G — Magnetic Grip

	private static void gripActivate(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();

		if (gripKind(ctx) != 0) {
			launchGrip(ctx);
			return;
		}

		Vec3 look = p.getLookAngle();
		BlockPos bp = lookedAtMagneticBlock(p, SELECT_RANGE);
		if (bp == null && !AbilityHelpers.canGrief()) {
			// no world edits allowed: only item / entity grips
			bp = null;
		}
		if (bp != null) {
			BlockState state = level.getBlockState(bp);
			FallingBlockEntity fbe = FallingBlockEntity.fall(level, bp, state);
			fbe.setNoGravity(true);
			fbe.time = 1;
			setGrip(ctx, 1, fbe.getId(), 200);
			gripFx(level, p, fbe.position());
			return;
		}
		ItemEntity drop = nearestMagneticDrop(level, p, SELECT_RANGE);
		if (drop != null) {
			drop.setNoGravity(true);
			drop.setPickUpDelay(32767);
			setGrip(ctx, 2, drop.getId(), 200);
			gripFx(level, p, drop.position());
			return;
		}
		Entity ent = raycastMagneticEntity(p, SELECT_RANGE);
		if (ent instanceof net.minecraft.world.entity.vehicle.AbstractMinecart
				|| ent instanceof net.minecraft.world.entity.animal.IronGolem) {
			setGrip(ctx, 3, ent.getId(), 60); // heavy living/vehicle grips are short so golems aren't trivialised
			gripFx(level, p, ent.position());
			return;
		}
		ctx.actionBar("message.projecthero.magnetic.no_metal");
	}

	private static void gripTick(AbilityContext ctx) {
		int kind = gripKind(ctx);
		if (kind == 0) {
			return;
		}
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		int ticks = (int) ctx.resource("grip_ticks") - 1;
		Entity g = level.getEntity((int) ctx.resource("grip_id"));
		if (g == null || !g.isAlive() || ticks <= 0) {
			releaseGrip(p, false);
			return;
		}
		ctx.setResource("grip_ticks", ticks, 1.0e9f);

		MagneticMass mass = MagneticMass.of(g);
		Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(3.2));
		Vec3 cur = g.position();
		Vec3 next = cur.add(hold.subtract(cur).scale(Math.min(1.0, mass.gripFollow + 0.15)));
		g.setPos(next.x, next.y - (kind == 3 ? g.getBbHeight() * 0.5 : 0.0), next.z);
		g.setDeltaMovement(Vec3.ZERO);
		g.fallDistance = 0.0f;
		g.hasImpulse = true;
		if (g instanceof FallingBlockEntity fbe) {
			fbe.time = 1;
		}
		if (g instanceof LivingEntity le) {
			le.hurtMarked = true;
		}
		if (p.tickCount % 4 == 0) {
			AbilityHelpers.line(level, p.getEyePosition(), g.position().add(0, g.getBbHeight() * 0.5, 0),
					ParticleTypes.ELECTRIC_SPARK, 1.5);
		}
	}

	private static void launchGrip(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		int kind = gripKind(ctx);
		Entity g = level.getEntity((int) ctx.resource("grip_id"));
		Vec3 look = p.getLookAngle();
		if (g != null && g.isAlive()) {
			MagneticMass mass = MagneticMass.of(g);
			if (g instanceof FallingBlockEntity fbe) {
				fbe.setNoGravity(false);
				fbe.setDeltaMovement(look.scale(mass.launchSpeed));
				fbe.hasImpulse = true;
				track(level, fbe, p, mass.damage, mass.knockback, 60);
			} else if (g instanceof ItemEntity ie) {
				ie.setNoGravity(true);
				ie.setDeltaMovement(look.scale(mass.launchSpeed + 0.4));
				ie.setPickUpDelay(40);
				track(level, ie, p, mass.damage, mass.knockback, 45);
			} else {
				g.setDeltaMovement(look.scale(mass.launchSpeed + 0.3));
				g.hasImpulse = true;
				if (g instanceof LivingEntity le) {
					le.hurtMarked = true;
					AbilityHelpers.hurt(p, le, mass.damage * 0.5f);
				}
			}
			AbilityHelpers.sound(p, SoundEvents.IRON_GOLEM_REPAIR, 0.8f, 0.9f);
			AbilityHelpers.burst(level, g.position(), ParticleTypes.ELECTRIC_SPARK, 12, 0.3);
		}
		clearGrip(ctx);
		ctx.triggerCooldown(30); // ~1.5 s between grips
	}

	private static void releaseGrip(ServerPlayer player, boolean unused) {
		var power = Powers.byKey(KEY);
		if (power == null) {
			return;
		}
		int kind = (int) com.projecthero.mod.hero.ExperimentalPowers.getResource(player, power, "grip_kind");
		if (kind == 0) {
			return;
		}
		int id = (int) com.projecthero.mod.hero.ExperimentalPowers.getResource(player, power, "grip_id");
		if (player.level() instanceof ServerLevel level && level.getEntity(id) instanceof Entity g && g.isAlive()) {
			if (g instanceof FallingBlockEntity fbe) {
				fbe.setNoGravity(false);
			} else if (g instanceof ItemEntity ie) {
				ie.setNoGravity(false);
				ie.setPickUpDelay(10);
			}
		}
		com.projecthero.mod.hero.ExperimentalPowers.setResource(player, power, "grip_kind", 0, 1.0e9f);
		com.projecthero.mod.hero.ExperimentalPowers.setResource(player, power, "grip_id", 0, 1.0e9f);
		com.projecthero.mod.hero.ExperimentalPowers.setResource(player, power, "grip_ticks", 0, 1.0e9f);
	}

	private static int gripKind(AbilityContext ctx) {
		return (int) ctx.resource("grip_kind");
	}

	private static void setGrip(AbilityContext ctx, int kind, int id, int ticks) {
		ctx.setResource("grip_kind", kind, 1.0e9f);
		ctx.setResource("grip_id", id, 1.0e9f);
		ctx.setResource("grip_ticks", ticks, 1.0e9f);
	}

	private static void clearGrip(AbilityContext ctx) {
		ctx.setResource("grip_kind", 0, 1.0e9f);
		ctx.setResource("grip_id", 0, 1.0e9f);
		ctx.setResource("grip_ticks", 0, 1.0e9f);
	}

	private static void gripFx(ServerLevel level, ServerPlayer p, Vec3 at) {
		AbilityHelpers.line(level, p.getEyePosition(), at, ParticleTypes.ELECTRIC_SPARK, 2.0);
		AbilityHelpers.sound(p, SoundEvents.IRON_DOOR_OPEN, 0.6f, 1.3f);
	}

	// ================================================================= H — Polarity Leap

	private static void polarityLeap(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 eye = p.getEyePosition();

		Vec3 anchor = null;
		Entity ent = raycastMagneticEntity(p, LEAP_RANGE);
		if (ent != null) {
			anchor = ent.position().add(0, ent.getBbHeight() * 0.5, 0);
		} else {
			BlockHitResult bhr = AbilityHelpers.raycastBlock(p, LEAP_RANGE);
			if (bhr.getType() == HitResult.Type.BLOCK && MagneticMaterials.isMagnetic(level.getBlockState(bhr.getBlockPos()))) {
				anchor = bhr.getLocation();
			}
		}
		if (anchor == null) {
			ctx.actionBar("message.projecthero.magnetic.leap_no_target");
			return;
		}

		Vec3 to = anchor.subtract(eye);
		double dist = to.length();
		double strength = Math.max(1.0, Math.min(2.4, 0.9 + dist * 0.06));
		AbilityHelpers.addImpulse(p, to.normalize().scale(strength));
		p.resetFallDistance();
		ctx.setResource("no_fall_until", p.level().getGameTime() + 20, 1.0e12f);
		AbilityHelpers.line(level, eye, anchor, ParticleTypes.ELECTRIC_SPARK, 2.5);
		AbilityHelpers.burst(level, p.position(), ParticleTypes.CRIT, 16, 0.3);
		AbilityHelpers.sound(p, SoundEvents.IRON_GOLEM_REPAIR, 0.9f, 1.3f);
		ctx.triggerCooldown();
	}

	// ================================================================= Z — Metal Storm

	private static void metalStorm(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();

		List<ItemEntity> drops = new ArrayList<>();
		for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(STORM_RANGE),
				i -> i.isAlive() && MagneticMaterials.isMagnetic(i.getItem()))) {
			drops.add(ie);
			if (drops.size() >= STORM_MAX_OBJECTS) {
				break;
			}
		}
		List<BlockPos> blocks = new ArrayList<>();
		if (AbilityHelpers.canGrief() && drops.size() < STORM_MAX_OBJECTS) {
			BlockPos c = p.blockPosition();
			int r = 6;
			for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
				if (bp.distToCenterSqr(p.getX(), p.getY(), p.getZ()) <= (double) (r * r)
						&& MagneticMaterials.canDisplace(level, bp)) {
					blocks.add(bp.immutable());
					if (drops.size() + blocks.size() >= STORM_MAX_OBJECTS) {
						break;
					}
				}
			}
		}
		if (drops.size() + blocks.size() < 2) {
			ctx.actionBar("message.projecthero.magnetic.storm_short");
			return;
		}

		List<Entity> objs = new ArrayList<>();
		for (ItemEntity ie : drops) {
			ie.setNoGravity(true);
			ie.setPickUpDelay(32767);
			objs.add(ie);
		}
		for (BlockPos bp : blocks) {
			BlockState state = level.getBlockState(bp);
			if (!MagneticMaterials.canDisplace(level, bp)) {
				continue;
			}
			FallingBlockEntity fbe = FallingBlockEntity.fall(level, bp, state);
			fbe.setNoGravity(true);
			fbe.time = 1;
			objs.add(fbe);
		}
		if (objs.size() < 2) {
			ctx.actionBar("message.projecthero.magnetic.storm_short");
			return;
		}

		STORMS.put(p.getUUID(), new Storm(level, p, objs));
		AbilityHelpers.sound(p, SoundEvents.IRON_GOLEM_DAMAGE, 1.1f, 0.6f);
		AbilityHelpers.burst(level, p.position().add(0, 1, 0), ParticleTypes.ELECTRIC_SPARK, 30, 1.5);
		ctx.triggerCooldown();
	}

	// ================================================================= X — Magnetic Crush

	private static void magneticCrush(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		LivingEntity t = AbilityHelpers.raycastEntity(p, CRUSH_RANGE);
		if (t == null) {
			ctx.actionBar("message.projecthero.magnetic.no_metal");
			return;
		}
		MagneticMaterials.Loadout lo = MagneticMaterials.loadout(t);
		if (!lo.any()) {
			ctx.actionBar("message.projecthero.magnetic.no_metal");
			return;
		}
		int pieces = lo.pieces();
		float dmg = 3.0f + pieces * 4.0f; // 1->7 ... 4->19 ... 6->27
		if (lo.netherite()) {
			dmg *= 0.6f; // netherite resists the force -- but the lockdown still lands
		}
		AbilityHelpers.hurt(p, t, dmg);
		AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 100, Math.min(4, pieces + 1));
		AbilityHelpers.applyControl(t, MobEffects.WEAKNESS, 100, 1);
		if (pieces >= 3) {
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 50, 7);
			t.setDeltaMovement(0, t.getDeltaMovement().y, 0);
			t.hurtMarked = true;
		}
		// its own gear crushes inward + nearby loose metal is dragged onto it
		for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, t.getBoundingBox().inflate(8.0),
				i -> MagneticMaterials.isMagnetic(i.getItem()))) {
			ie.setDeltaMovement(t.position().subtract(ie.position()).normalize().scale(1.1));
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(),
				24, 0.35, 0.5, 0.35, 0.12);
		level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(),
				12, 0.2, 0.3, 0.2, 0.0);
		AbilityHelpers.sound(p, SoundEvents.ANVIL_LAND, 0.7f, 1.3f);
		ctx.triggerCooldown();
	}

	// ================================================================= passives

	private static void passiveTick(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		// Metal Attraction: magnetic drops within 30 blocks drift toward the player, with a little lift
		// so they climb a one-block step instead of getting stuck against it.
		for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(30.0),
				i -> MagneticMaterials.isMagnetic(i.getItem()))) {
			Vec3 pull = player.position().subtract(ie.position()).normalize().scale(0.045);
			double stepAssist = ie.horizontalCollision ? 0.1 : 0.02;
			ie.setDeltaMovement(ie.getDeltaMovement().add(pull.x, stepAssist, pull.z));
		}
		// Magnetic Awareness: a faint tick on the metal object you are looking at.
		if (player.tickCount % 8 == 0) {
			BlockPos bp = lookedAtMagneticBlock(player, SELECT_RANGE);
			if (bp != null) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5,
						2, 0.25, 0.25, 0.25, 0.0);
			}
		}
	}

	// ================================================================= global tick (ProjectHeroMod)

	/**
	 * Drop every tracked magnetic projectile / Metal Storm. Only called once the owning server has
	 * stopped ({@code ServerStateReset}) -- both records hold a {@link ServerLevel}, its entities and
	 * the caster, none of which may survive into the next world opened in the same JVM.
	 */
	public static void clearSessionState() {
		PROJECTILES.clear();
		STORMS.clear();
	}

	public static void tick(MinecraftServer server) {
		if (!PROJECTILES.isEmpty()) {
			Iterator<Map.Entry<Integer, Proj>> it = PROJECTILES.entrySet().iterator();
			while (it.hasNext()) {
				Proj proj = it.next().getValue();
				if (!proj.tick()) {
					it.remove();
				}
			}
		}
		if (!STORMS.isEmpty()) {
			Iterator<Map.Entry<UUID, Storm>> it = STORMS.entrySet().iterator();
			while (it.hasNext()) {
				if (!it.next().getValue().tick()) {
					it.remove();
				}
			}
		}
	}

	private static void track(ServerLevel level, Entity entity, ServerPlayer owner, float damage, double knockback, int life) {
		PROJECTILES.put(entity.getId(), new Proj(level, entity, owner, damage, knockback, life));
	}

	// ================================================================= tracked projectile

	private static final class Proj {
		private final ServerLevel level;
		private final Entity entity;
		private final ServerPlayer owner;
		private final float damage;
		private final double knockback;
		private int life;

		Proj(ServerLevel level, Entity entity, ServerPlayer owner, float damage, double knockback, int life) {
			this.level = level;
			this.entity = entity;
			this.owner = owner;
			this.damage = damage;
			this.knockback = knockback;
			this.life = life;
		}

		/** @return true to keep tracking. */
		boolean tick() {
			if (!entity.isAlive() || --life <= 0 || owner.hasDisconnected() || !owner.isAlive()) {
				settle();
				return false;
			}
			if (entity instanceof ItemEntity && entity.getDeltaMovement().lengthSqr() < 0.02) {
				settle();
				return false;
			}
			for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(0.55),
					e -> e.isAlive() && e != owner && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
				AbilityHelpers.hurt(owner, le, damage);
				AbilityHelpers.knockbackFrom(le, entity.position(), knockback);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(),
						10, 0.3, 0.3, 0.3, 0.1);
				settle();
				return false;
			}
			return true;
		}

		private void settle() {
			if (entity instanceof FallingBlockEntity fbe && fbe.isAlive()) {
				fbe.setNoGravity(false);
			} else if (entity instanceof ItemEntity ie && ie.isAlive()) {
				ie.setNoGravity(false);
				ie.setPickUpDelay(10);
			}
		}
	}

	// ================================================================= Metal Storm state

	private static final class Storm {
		private final ServerLevel level;
		private final ServerPlayer owner;
		private final List<Entity> objs;
		private final Map<Integer, Float> dealt = new HashMap<>();
		private int age;

		Storm(ServerLevel level, ServerPlayer owner, List<Entity> objs) {
			this.level = level;
			this.owner = owner;
			this.objs = objs;
		}

		private static final int ORBIT_TICKS = 30;
		private static final int MAX_AGE = ORBIT_TICKS + 80;

		/** @return true to keep the storm alive. */
		boolean tick() {
			objs.removeIf(e -> e == null || !e.isAlive());
			if (objs.isEmpty() || !owner.isAlive() || owner.hasDisconnected() || age > MAX_AGE) {
				release();
				return false;
			}
			age++;
			if (age <= ORBIT_TICKS) {
				orbit();
			} else if (age == ORBIT_TICKS + 1) {
				fire();
			} else {
				resolveHits();
			}
			if (age % 3 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, owner.getX(), owner.getY() + 1, owner.getZ(),
						4, 3.0, 1.0, 3.0, 0.0);
			}
			return true;
		}

		private void orbit() {
			int n = objs.size();
			for (int i = 0; i < n; i++) {
				Entity e = objs.get(i);
				double a = age * 0.32 + i * (Math.PI * 2.0 / n);
				Vec3 target = owner.position().add(Math.cos(a) * 2.6, 1.1 + Math.sin(age * 0.2 + i) * 0.3, Math.sin(a) * 2.6);
				Vec3 cur = e.position();
				Vec3 next = cur.add(target.subtract(cur).scale(0.5));
				e.setPos(next.x, next.y, next.z);
				e.setDeltaMovement(Vec3.ZERO);
				e.setNoGravity(true);
				e.fallDistance = 0.0f;
				if (e instanceof FallingBlockEntity fbe) {
					fbe.time = 1;
				}
			}
		}

		/** One-shot: hurl every object toward the aim point, then let physics carry them. */
		private void fire() {
			Vec3 aimPoint = AbilityHelpers.aimPoint(owner, 24.0);
			AbilityHelpers.sound(owner, SoundEvents.IRON_GOLEM_ATTACK, 1.1f, 0.7f);
			for (Entity e : objs) {
				MagneticMass mass = MagneticMass.of(e);
				Vec3 dir = aimPoint.subtract(e.position());
				dir = dir.lengthSqr() < 1.0e-4 ? owner.getLookAngle() : dir.normalize();
				e.setNoGravity(false);
				e.setDeltaMovement(dir.scale(mass.launchSpeed + 0.5));
				e.hasImpulse = true;
			}
		}

		private void resolveHits() {
			Iterator<Entity> it = objs.iterator();
			while (it.hasNext()) {
				Entity e = it.next();
				MagneticMass mass = MagneticMass.of(e);
				LivingEntity hit = null;
				for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(0.7),
						x -> x.isAlive() && x != owner
								&& !(x instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
					if (dealt.getOrDefault(le.getId(), 0.0f) < STORM_PER_TARGET_CAP) {
						hit = le;
						break;
					}
				}
				if (hit == null) {
					continue;
				}
				float done = dealt.getOrDefault(hit.getId(), 0.0f);
				float d = Math.min(mass.damage, STORM_PER_TARGET_CAP - done);
				AbilityHelpers.hurt(owner, hit, d);
				AbilityHelpers.knockbackFrom(hit, e.position(), mass.knockback);
				dealt.put(hit.getId(), done + d);
				if (e instanceof ItemEntity ie) {
					ie.setNoGravity(false);
					ie.setPickUpDelay(10);
				}
				// FallingBlockEntity keeps flying and lands / drops on its own
				it.remove();
			}
		}

		private void release() {
			for (Entity e : objs) {
				if (e instanceof FallingBlockEntity fbe && fbe.isAlive()) {
					fbe.setNoGravity(false);
				} else if (e instanceof ItemEntity ie && ie.isAlive()) {
					ie.setNoGravity(false);
					ie.setPickUpDelay(10);
				}
			}
		}
	}

	// ================================================================= selection helpers

	private static BlockPos lookedAtMagneticBlock(ServerPlayer p, double range) {
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, range);
		if (bhr.getType() == HitResult.Type.BLOCK
				&& MagneticMaterials.canDisplace(p.serverLevel(), bhr.getBlockPos())) {
			return bhr.getBlockPos();
		}
		return null;
	}

	private static BlockPos nearestMagneticBlock(ServerLevel level, ServerPlayer p, double range) {
		BlockPos origin = p.blockPosition();
		int r = (int) Math.ceil(range);
		BlockPos best = null;
		double bestD = range * range;
		for (BlockPos bp : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
			double d = bp.distToCenterSqr(p.getX(), p.getY(), p.getZ());
			if (d < bestD && MagneticMaterials.canDisplace(level, bp)) {
				bestD = d;
				best = bp.immutable();
			}
		}
		return best;
	}

	private static ItemEntity nearestMagneticDrop(ServerLevel level, ServerPlayer p, double range) {
		ItemEntity best = null;
		double bestD = range * range;
		for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(range),
				i -> i.isAlive() && MagneticMaterials.isMagnetic(i.getItem()))) {
			double d = ie.distanceToSqr(p);
			if (d < bestD) {
				bestD = d;
				best = ie;
			}
		}
		return best;
	}

	/** The first magnetic item stack in the player's inventory, used as ammo when nothing else is in reach. */
	private static ItemStack inventoryMagneticAmmo(ServerPlayer p) {
		for (ItemStack stack : p.getInventory().items) {
			if (!stack.isEmpty() && MagneticMaterials.isMagnetic(stack)) {
				return stack;
			}
		}
		return null;
	}

	private static Entity raycastMagneticEntity(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		AABB box = p.getBoundingBox().expandTowards(p.getLookAngle().scale(range)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
				e -> e != p && e.isPickable() && MagneticMaterials.isMagneticEntity(e) && !MagneticMaterials.isMjolnir(e));
		return hit != null ? hit.getEntity() : null;
	}

	private static void streak(ServerLevel level, Vec3 from, Vec3 look, ParticleOptions particle) {
		AbilityHelpers.line(level, from, from.add(look.scale(2.5)), particle, 3.0);
	}
}
