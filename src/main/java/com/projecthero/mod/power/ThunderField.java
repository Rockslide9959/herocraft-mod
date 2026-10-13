package com.projecthero.mod.power;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.sound.ProjectHeroSounds;
import com.projecthero.mod.stormbreaker.StormbreakerEntity;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: "Thunder Field" (Shift + G -- plain G stays Lightning Strike). Thor hurls the weapon in his hand
 * (Mjolnir or Stormbreaker; with both, the main-hand one; the off-hand one if only that hand holds one) at the ground
 * he is aiming at, up to {@link ThorConfig.ThunderFieldCfg#range} (24) blocks away. Where it lands it slams in and
 * stays, a bolt from the sky hits it, and the ground round it is covered in lightning for
 * {@link ThorConfig.ThunderFieldCfg#durationTicks} (4 s), radius {@link ThorConfig.ThunderFieldCfg#radius} (5): every
 * {@link ThorConfig.ThunderFieldCfg#pulseTicks} (10) ticks each threat standing in it takes
 * {@link ThorConfig.ThunderFieldCfg#pulseDamage} (4, + the storm buff) and is slowed. Rule 2 (hostiles only) and
 * never Thor, his pets, squad-mates or their pets; no fire and no block damage (the bolts are visual-only). Then the
 * weapon flies home exactly as a thrown one always does (or at once on the call key). Cooldown
 * {@link ThorConfig.ThunderFieldCfg#cooldownTicks} (15 s), started on the throw.
 *
 * <p>The field's look (crackling arcs across the ground, short bolts, a glowing rim) is drawn by the client from the
 * synced {@link ThorFx} field slot; the server adds sparks. Throws in flight and live fields are static lists ticked
 * once per server tick and cleared by {@code ServerStateReset}.
 */
public final class ThunderField {
	/** A throw that has not landed after this long lands where it was aimed. */
	private static final int MAX_FLIGHT_TICKS = 40;
	/** "Standing in it": this far above / below the field's centre. */
	private static final double FIELD_HEIGHT = 2.0;

	private record Throw(UUID weapon, UUID owner, ResourceKey<Level> dim, Vec3 aim, long start) {
	}

	private static final class Field {
		final UUID owner;
		final ResourceKey<Level> dim;
		final Vec3 center;
		final long start;
		final int duration;
		final double radius;

		Field(UUID owner, ResourceKey<Level> dim, Vec3 center, long start, int duration, double radius) {
			this.owner = owner;
			this.dim = dim;
			this.center = center;
			this.start = start;
			this.duration = duration;
			this.radius = radius;
		}
	}

	private static final List<Throw> THROWS = new ArrayList<>();
	private static final List<Field> FIELDS = new ArrayList<>();

	private ThunderField() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(ThunderField::tick);
	}

	// ---------------- the throw ----------------

	/** Shift + G. */
	public static void cast(ServerPlayer player) {
		if (!Worthiness.isWorthy(player)) {
			return;
		}
		InteractionHand hand = WeaponCombo.weaponOf(player.getMainHandItem()) != WeaponComboState.WEAPON_NONE
				? InteractionHand.MAIN_HAND
				: WeaponCombo.weaponOf(player.getOffhandItem()) != WeaponComboState.WEAPON_NONE ? InteractionHand.OFF_HAND : null;
		if (hand == null) {
			player.displayClientMessage(Component.translatable("projecthero.thor.thunder_field.no_weapon"), true);
			return;
		}
		long now = player.level().getGameTime();
		var cds = player.getAttachedOrCreate(ModAttachments.COOLDOWNS);
		if (!cds.isReady(ThorAbility.THUNDER_FIELD, now)) {
			return;
		}
		ThorConfig.ThunderFieldCfg cfg = ThorConfig.get().thunderField;
		Vec3 aim = aimPoint(player, cfg.range);
		if (aim == null) {
			player.displayClientMessage(Component.translatable("projecthero.thor.thunder_field.no_ground"), true);
			return;
		}
		ItemStack stack = player.getItemInHand(hand);
		ItemStack thrown = stack.copy();
		Entity weapon;
		Vec3 eye = player.getEyePosition();
		Vec3 dir = aim.subtract(eye);
		if (stack.is(ModItems.MJOLNIR)) {
			MjolnirEntity e = new MjolnirEntity(player.level(), player);
			e.setItem(thrown);
			e.throwFromPlayer(player, dir);
			// throwFromPlayer starts it a block out in front of the eyes: re-aim from there so it lands on the spot
			e.setDeltaMovement(aim.subtract(e.position()).normalize().scale(e.getDeltaMovement().length()));
			weapon = e;
		} else {
			StormbreakerEntity e = new StormbreakerEntity(player.level(), player);
			e.setItem(thrown);
			e.launch(player, dir);
			e.setDeltaMovement(aim.subtract(e.position()).normalize().scale(StormbreakerEntity.THROW_SPEED));
			weapon = e;
		}
		player.setItemInHand(hand, ItemStack.EMPTY);
		player.level().addFreshEntity(weapon);
		ThorPowers.triggerCooldown(player, ThorAbility.THUNDER_FIELD, cfg.cooldownTicks);
		ThorPowers.grantThrowFlightGrace(player);
		ThorVisuals.anim(player, ThorFx.ANIM_FIELD_THROW, aim);
		Level level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), ProjectHeroSounds.MJOLNIR_THROW,
				SoundSource.PLAYERS, 0.5f, 0.9f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_THROW,
				SoundSource.PLAYERS, 0.45f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
				SoundSource.PLAYERS, 0.2f, 1.5f);
		THROWS.add(new Throw(weapon.getUUID(), player.getUUID(), level.dimension(), aim, now));
	}

	/** The ground point Thor is aiming at within {@code range} blocks, or null if there is none. */
	public static Vec3 aimPoint(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(range));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			return null;
		}
		Vec3 at = hit.getLocation();
		if (hit.getDirection() == Direction.UP) {
			return at;
		}
		// a wall or a ceiling: the ground at its foot
		return ThorTwinThunder.groundAt((ServerLevel) player.level(),
				at.add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.3)));
	}

	// ---------------- the field ----------------

	/** Forms a field of {@code owner}'s at {@code center} now (the landing; public for tests). */
	public static void spawnField(ServerPlayer owner, ServerLevel level, Vec3 center) {
		ThorConfig.ThunderFieldCfg cfg = ThorConfig.get().thunderField;
		FIELDS.add(new Field(owner.getUUID(), level.dimension(), center, level.getGameTime(), cfg.durationTicks, cfg.radius));
		ThorVisuals.field(owner, center, cfg.durationTicks, (float) cfg.radius);
		ThorTwinThunder.visualBolt(level, owner, center);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 0.3, center.z, 50, cfg.radius * 0.4, 0.15,
				cfg.radius * 0.4, 0.25);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0f,
				0.9f);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f,
				1.1f);
		ThorTwinThunder.shake(level, center, 0.7f, 8, 16.0);
	}

	/** Does {@code owner} have a live field (for tests / debugging)? */
	public static boolean hasField(ServerPlayer owner) {
		for (Field f : FIELDS) {
			if (f.owner.equals(owner.getUUID())) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (!THROWS.isEmpty()) {
			tickThrows(server);
		}
		if (FIELDS.isEmpty()) {
			return;
		}
		Iterator<Field> it = FIELDS.iterator();
		while (it.hasNext()) {
			Field f = it.next();
			ServerLevel level = server.getLevel(f.dim);
			ServerPlayer owner = server.getPlayerList().getPlayer(f.owner);
			if (level == null || owner == null) {
				it.remove();
				continue;
			}
			long t = level.getGameTime() - f.start;
			if (t >= f.duration) {
				it.remove();
				continue;
			}
			ThorConfig.ThunderFieldCfg cfg = ThorConfig.get().thunderField;
			if (t % Math.max(1, cfg.pulseTicks) == 0) {
				pulse(level, owner, f, cfg);
			}
			if (t % 2 == 0) {
				// sparks dancing over the ground
				for (int i = 0; i < 6; i++) {
					double a = level.random.nextDouble() * Math.PI * 2.0;
					double r = Math.sqrt(level.random.nextDouble()) * f.radius;
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, f.center.x + Math.cos(a) * r, f.center.y + 0.1,
							f.center.z + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.05);
				}
			}
		}
	}

	private static void pulse(ServerLevel level, ServerPlayer owner, Field f, ThorConfig.ThunderFieldCfg cfg) {
		for (LivingEntity e : ThorTargets.hostilesInRadius(level, owner, f.center, f.radius + FIELD_HEIGHT)) {
			if (Math.abs(e.getY() - f.center.y) > FIELD_HEIGHT || WeaponCombo.friendly(owner, e)) {
				continue;
			}
			double dx = e.getX() - f.center.x;
			double dz = e.getZ() - f.center.z;
			if (dx * dx + dz * dz > f.radius * f.radius) {
				continue;
			}
			if (AbilityHelpers.hurt(owner, e, (float) cfg.pulseDamage)) {
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, cfg.pulseTicks + 20,
						Math.max(0, cfg.slowAmplifier)), owner);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 8,
						0.25, 0.4, 0.25, 0.15);
			}
		}
		level.playSound(null, f.center.x, f.center.y, f.center.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
				0.3f, 1.6f + level.random.nextFloat() * 0.3f);
	}

	private static void tickThrows(MinecraftServer server) {
		Iterator<Throw> it = THROWS.iterator();
		while (it.hasNext()) {
			Throw th = it.next();
			ServerLevel level = server.getLevel(th.dim());
			ServerPlayer owner = server.getPlayerList().getPlayer(th.owner());
			if (level == null || owner == null) {
				it.remove();
				continue;
			}
			Entity weapon = level.getEntity(th.weapon());
			boolean timedOut = level.getGameTime() - th.start() > MAX_FLIGHT_TICKS;
			boolean landed = weapon == null || weapon.isRemoved() || timedOut
					|| (weapon instanceof MjolnirEntity m && m.getState() != MjolnirEntity.State.THROWN)
					|| (weapon instanceof StormbreakerEntity s && (s.isReturning() || s.isResting()));
			if (!landed) {
				continue;
			}
			it.remove();
			ThorConfig.ThunderFieldCfg cfg = ThorConfig.get().thunderField;
			Vec3 center = weapon != null && !weapon.isRemoved() && !timedOut
					? ThorTwinThunder.groundAt(level, weapon.position())
					: th.aim();
			if (weapon instanceof MjolnirEntity m && !m.isRemoved()) {
				m.pinAt(m.getState() == MjolnirEntity.State.IMPACT ? m.position() : center.add(0, 0.3, 0), cfg.durationTicks);
			} else if (weapon instanceof StormbreakerEntity s && !s.isRemoved() && !s.isResting()) {
				s.pinAt(center.add(0, 0.05, 0), cfg.durationTicks);
			}
			spawnField(owner, level, center);
		}
	}

	/** World unload. */
	public static void clearSessionState() {
		THROWS.clear();
		FIELDS.clear();
	}
}
