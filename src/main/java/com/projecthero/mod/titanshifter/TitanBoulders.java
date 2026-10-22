package com.projecthero.mod.titanshifter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.math.Transformation;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * v0.16.7 (user, Titan revamp): the Beast Titan's boulders. Each boulder is a scaled-up vanilla block display (the ground
 * it was ripped out of, else cobblestone) the server flies itself: it rides the Beast's right hand until the throw, then
 * arcs toward the aim point and bursts on the first mob or block it meets -- full damage in its radius, then a ring of
 * shrapnel (half damage) out to twice that. Nothing about it is saved: the displays carry {@link #TAG}; one loaded that
 * no flight owns is removed, and SERVER_STOPPING removes them all. Hits are the shifter's own (HeroTargets rule 1, the
 * boss cap, the shared threat table through {@code AbilityHelpers.hurtBurst}).
 */
public final class TitanBoulders {
	public static final String TAG = "projecthero_titan_boulder";
	private static final double GRAVITY = 0.05;
	private static final int MAX_AGE = 100;

	private static final class Boulder {
		final ServerLevel level;
		final UUID displayId;
		final UUID ownerId;
		final int formId;
		final float scale;
		final float damage;
		final double radius;
		final BlockState state;
		Vec3 pos;
		Vec3 vel = Vec3.ZERO;
		/** Game time it leaves the hand; until then it follows the hand. */
		long releaseAt;
		Vec3 aim;
		double speed;
		int age;

		Boulder(ServerLevel level, UUID displayId, UUID ownerId, int formId, float scale, float damage, double radius, BlockState state,
				Vec3 pos) {
			this.level = level;
			this.displayId = displayId;
			this.ownerId = ownerId;
			this.formId = formId;
			this.scale = scale;
			this.damage = damage;
			this.radius = radius;
			this.state = state;
			this.pos = pos;
		}
	}

	private static final List<Boulder> LIVE = new ArrayList<>();
	private static final Set<UUID> LIVE_IDS = new HashSet<>();

	private TitanBoulders() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(TitanBoulders::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Display.BlockDisplay && entity.getTags().contains(TAG) && !LIVE_IDS.contains(entity.getUUID())) {
				entity.discard();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(TitanBoulders::removeEverything);
	}

	/** ServerStateReset: forget every flight (the displays themselves went with the world). */
	public static void clearSessionState() {
		LIVE.clear();
		LIVE_IDS.clear();
	}

	public static int liveCount() {
		return LIVE.size();
	}

	/** The block the Beast rips up: the ground under it when that is plain natural stuff, else cobblestone. */
	static BlockState groundState(TitanFormEntity form) {
		ServerLevel level = (ServerLevel) form.level();
		BlockPos below = BlockPos.containing(form.getX(), form.getY() - 0.5, form.getZ());
		BlockState s = level.getBlockState(below);
		if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, below) < 0.3f
				|| s.getDestroySpeed(level, below) > 3.0f) {
			return Blocks.COBBLESTONE.defaultBlockState();
		}
		return s;
	}

	/** Where the boulder sits in the Beast's raised right hand. */
	private static Vec3 handPos(TitanFormEntity form) {
		Vec3 right = Vec3.directionFromRotation(0, form.getYRot() + 90.0f);
		Vec3 back = Vec3.directionFromRotation(0, form.getYRot()).scale(-1.0);
		return form.position().add(right.scale(form.getBbWidth() * 0.55)).add(back.scale(form.getBbWidth() * 0.2))
				.add(0, form.getBbHeight() * 0.95, 0);
	}

	/**
	 * A boulder appears in the Beast's hand now and is thrown after {@code holdTicks} at {@code aim}, at {@code speed}
	 * blocks a tick. Returns false if the display could not be made.
	 */
	public static boolean pickUp(TitanFormEntity form, ServerPlayer owner, float scale, float damage, double radius, int holdTicks,
			Vec3 aim, double speed) {
		ServerLevel level = (ServerLevel) form.level();
		BlockState state = groundState(form);
		Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level);
		if (d == null) {
			return false;
		}
		Vec3 at = handPos(form);
		d.setBlockState(state);
		d.setPosRotInterpolationDuration(2);
		spin(d, new Quaternionf(), scale);
		d.moveTo(at.x, at.y, at.z, 0f, 0f);
		d.addTag(TAG);
		Boulder b = new Boulder(level, d.getUUID(), owner.getUUID(), form.getId(), scale, damage, radius, state, at);
		b.releaseAt = level.getGameTime() + holdTicks;
		b.aim = aim;
		b.speed = speed;
		LIVE.add(b);
		LIVE_IDS.add(d.getUUID());
		level.addFreshEntity(d);
		// ripped out of the ground: a burst of the block it came from
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), form.getX(), form.getY() + 0.3, form.getZ(), 40,
				form.getBbWidth() * 0.5, 0.2, form.getBbWidth() * 0.5, 0.15);
		level.playSound(null, form.getX(), form.getY(), form.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0f, 0.5f);
		form.setHoldingBoulder(true);
		return true;
	}

	private static void spin(Display.BlockDisplay d, Quaternionf rot, float scale) {
		Vector3f half = new Vector3f(-scale / 2f, -scale / 2f, -scale / 2f);
		rot.transform(half);
		d.setTransformationInterpolationDelay(0);
		d.setTransformationInterpolationDuration(3);
		d.setTransformation(new Transformation(half, new Quaternionf(rot), new Vector3f(scale, scale, scale), null));
	}

	private static void tick(MinecraftServer server) {
		if (LIVE.isEmpty()) {
			return;
		}
		Iterator<Boulder> it = LIVE.iterator();
		List<Boulder> burst = new ArrayList<>();
		while (it.hasNext()) {
			Boulder b = it.next();
			if (!step(b)) {
				it.remove();
				LIVE_IDS.remove(b.displayId);
				burst.add(b);
			}
		}
		for (Boulder b : burst) {
			Entity d = b.level.getEntity(b.displayId);
			if (d != null) {
				d.discard();
			}
		}
	}

	/** One tick of one boulder; false once it is done (burst, dropped, or its Titan went away before the throw). */
	private static boolean step(Boulder b) {
		ServerLevel level = b.level;
		long now = level.getGameTime();
		Entity display = level.getEntity(b.displayId);
		Entity fe = level.getEntity(b.formId);
		TitanFormEntity form = fe instanceof TitanFormEntity f && f.isAlive() ? f : null;
		if (display == null) {
			return false;
		}
		if (now < b.releaseAt) {
			if (form == null || form.isDefeated() || form.formState() != TitanFormEntity.FORM_NORMAL) {
				return false; // dropped: the Titan is gone or changing back
			}
			b.pos = handPos(form);
			display.moveTo(b.pos.x, b.pos.y, b.pos.z, 0f, 0f);
			return true;
		}
		if (b.age == 0) {
			if (form != null) {
				form.setHoldingBoulder(false);
			}
			Vec3 dir = b.aim.subtract(b.pos);
			double dist = Math.max(1.0, dir.length());
			// aim a little high so the arc comes down on the point
			double t = dist / b.speed;
			b.vel = dir.scale(1.0 / t).add(0, GRAVITY * t * 0.5, 0);
			level.playSound(null, b.pos.x, b.pos.y, b.pos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0f, 0.3f);
		}
		b.age++;
		Vec3 from = b.pos;
		Vec3 to = from.add(b.vel);
		b.vel = b.vel.add(0, -GRAVITY, 0);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(b.ownerId);
		// the first mob in the way
		double r = b.scale * 0.6;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(r + 0.5), e -> e != form
				&& owner != null && e != owner && com.projecthero.mod.combat.HeroTargets.canHarm(owner, e))) {
			AABB grown = e.getBoundingBox().inflate(r);
			if (grown.contains(from) || grown.clip(from, to).isPresent()) {
				impact(b, form, owner, e.position().add(0, e.getBbHeight() * 0.5, 0));
				return false;
			}
		}
		var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, display));
		if (hit.getType() != HitResult.Type.MISS) {
			impact(b, form, owner, hit.getLocation());
			return false;
		}
		b.pos = to;
		display.moveTo(to.x, to.y, to.z, 0f, 0f);
		if (b.age % 3 == 0 && display instanceof Display.BlockDisplay bd) {
			float a = b.age * 0.25f;
			spin(bd, new Quaternionf().rotateXYZ(a, a * 0.6f, a * 0.3f), b.scale);
		}
		if (b.age % 2 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, b.state), to.x, to.y, to.z, 2, r * 0.5, r * 0.5, r * 0.5, 0.0);
		}
		if (b.age >= MAX_AGE || to.y < level.getMinBuildHeight()) {
			if (b.age >= MAX_AGE) {
				impact(b, form, owner, to);
			}
			return false;
		}
		return true;
	}

	/** The burst: full damage in the radius, shrapnel (half damage) out to twice that, and a spray of the boulder's block. */
	private static void impact(Boulder b, TitanFormEntity form, ServerPlayer owner, Vec3 at) {
		ServerLevel level = b.level;
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, b.radius * 0.3, 0.3, b.radius * 0.3, 0.0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, b.state), at.x, at.y, at.z, 120,
				b.radius * 0.5, 0.6, b.radius * 0.5, 0.35);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 20, b.radius * 0.4, 0.4, b.radius * 0.4, 0.08);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 3.0f, 0.5f);
		if (owner == null) {
			return;
		}
		double shrapnel = b.radius * 2.0;
		for (LivingEntity e : AbilityHelpers.living(level, at, shrapnel, e -> e != form && e != owner
				&& com.projecthero.mod.combat.HeroTargets.canHarm(owner, e))) {
			boolean core = e.getBoundingBox().distanceToSqr(at) <= b.radius * b.radius;
			float base = core ? b.damage : b.damage * 0.5f;
			if (form != null) {
				TitanCombat.hit(form, owner, e, base, core ? 1.8 : 0.8, core ? 0.5 : 0.2, at);
			} else {
				float dmg = TitanCombat.isBoss(e) ? Math.min(base, e.getMaxHealth() * (float) TitanShifterConfig.damage().bossMaxFractionPerHit) : base;
				AbilityHelpers.hurtBurst(owner, e, dmg);
			}
		}
		TitanCombat.impactBlocks(level, at, b.radius);
		TitanCombat.shake(level, at, 0.5f, 10);
	}

	private static void removeEverything(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			List<Entity> mine = new ArrayList<>();
			for (Entity e : level.getAllEntities()) {
				if (e instanceof Display.BlockDisplay && e.getTags().contains(TAG)) {
					mine.add(e);
				}
			}
			mine.forEach(Entity::discard);
		}
		clearSessionState();
	}
}
