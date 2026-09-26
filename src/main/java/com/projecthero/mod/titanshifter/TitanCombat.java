package com.projecthero.mod.titanshifter;

import java.util.List;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared hit / area / terrain / shake helpers for every Titan action. Damage is dealt <em>as the
 * shifter</em> (a player attack), so kills, loot, PvP settings and the squad friendly-fire rule all
 * behave exactly as they do for every other hero power.
 */
public final class TitanCombat {
	private TitanCombat() {
	}

	// ---------------- targeting ----------------

	/** Everything a Titan action may hit inside {@code box}: no self, no rider, no squad-mates, no pets of the shifter. */
	public static List<LivingEntity> targetsIn(ServerLevel level, AABB box, TitanFormEntity form, ServerPlayer owner) {
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> validTarget(e, form, owner));
	}

	public static List<LivingEntity> targetsAround(ServerLevel level, Vec3 center, double radius, TitanFormEntity form,
			ServerPlayer owner) {
		return AbilityHelpers.living(level, center, radius, e -> validTarget(e, form, owner));
	}

	/**
	 * Everything within {@code radius} blocks horizontally of the Titan's body, from well below its feet (pits, ledges) up to
	 * well above its head -- the roar is a shout, not a sphere centred on the feet, so ground-level mobs are always inside it.
	 */
	public static List<LivingEntity> targetsInCylinder(ServerLevel level, TitanFormEntity form, double radius,
			ServerPlayer owner) {
		Vec3 c = form.position();
		double reachDown = 12.0;
		double reachUp = form.getBbHeight() + 12.0;
		AABB box = new AABB(c.x - radius, c.y - reachDown, c.z - radius, c.x + radius, c.y + reachUp, c.z + radius);
		double r2 = radius * radius;
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> {
			double dx = Math.max(Math.max(e.getBoundingBox().minX - c.x, c.x - e.getBoundingBox().maxX), 0.0);
			double dz = Math.max(Math.max(e.getBoundingBox().minZ - c.z, c.z - e.getBoundingBox().maxZ), 0.0);
			return dx * dx + dz * dz <= r2 && validTarget(e, form, owner);
		});
	}

	private static boolean validTarget(LivingEntity e, TitanFormEntity form, ServerPlayer owner) {
		if (e == form || e == owner || !e.isAlive() || e instanceof ArmorStand || e.isPassengerOfSameVehicle(form)) {
			return false;
		}
		if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return false;
		}
		if (e instanceof OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID())) {
			return false;
		}
		var server = owner.getServer();
		if (server != null) {
			java.util.UUID other = e instanceof TitanFormEntity t ? t.ownerId() : e.getUUID();
			if (other != null && SquadManager.get(server).sameSquad(owner.getUUID(), other)) {
				return false;
			}
		}
		return true;
	}

	// ---------------- damage ----------------

	/** Base damage scaled for the target: players softer, bosses capped to a fraction of their health per hit. */
	public static float scaled(TitanFormEntity form, LivingEntity target, float base) {
		var d = TitanShifterConfig.damage();
		float dmg = base * form.titanType().damageScale;
		dmg *= (float) (target instanceof Player ? d.playerFactor : d.mobFactor);
		if (isBoss(target)) {
			dmg = Math.min(dmg, (float) (target.getMaxHealth() * d.bossMaxFractionPerHit));
		}
		return Math.max(1.0f, dmg);
	}

	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= TitanShifterConfig.damage().bossHealthThreshold
				|| target instanceof net.minecraft.world.entity.boss.wither.WitherBoss
				|| target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon;
	}

	/** One Titan hit on one target: damage, then knockback away from {@code origin}. True if it landed. */
	public static boolean hit(TitanFormEntity form, ServerPlayer owner, LivingEntity target, float base, double knock,
			double lift, Vec3 origin) {
		float dmg = scaled(form, target, base);
		boolean landed = AbilityHelpers.hurtBurst(owner, target, dmg);
		if (!landed && !AbilityHelpers.hurtLands(owner, target, dmg)) {
			return false;
		}
		if (knock > 0.0 && !isBoss(target)) {
			AbilityHelpers.knockbackFrom(target, origin, knock);
			if (lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
			}
		}
		return true;
	}

	/** Hit everything in {@code box}; returns how many were hit. */
	public static int hitBox(TitanFormEntity form, ServerPlayer owner, AABB box, float base, double knock, double lift,
			Vec3 origin) {
		int n = 0;
		for (LivingEntity t : targetsIn((ServerLevel) form.level(), box, form, owner)) {
			if (hit(form, owner, t, base, knock, lift, origin)) {
				n++;
			}
		}
		return n;
	}

	public static int hitRadius(TitanFormEntity form, ServerPlayer owner, Vec3 center, double radius, float base,
			double knock, double lift) {
		int n = 0;
		for (LivingEntity t : targetsAround((ServerLevel) form.level(), center, radius, form, owner)) {
			if (hit(form, owner, t, base, knock, lift, center)) {
				n++;
			}
		}
		return n;
	}

	/** The punch volume in front of the Titan: {@code reach} blocks past its body, {@code spread} half-width. */
	public static AABB fistBox(TitanFormEntity form, double reach, double spread) {
		Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
		Vec3 c = form.position().add(fwd.scale(form.getBbWidth() * 0.5 + reach * 0.5))
				.add(0.0, form.getBbHeight() * 0.5, 0.0);
		double hx = spread + Math.abs(fwd.x) * reach * 0.5;
		double hz = spread + Math.abs(fwd.z) * reach * 0.5;
		return new AABB(c.x - hx, c.y - form.getBbHeight() * 0.5, c.z - hz, c.x + hx, c.y + form.getBbHeight() * 0.5,
				c.z + hz);
	}

	// ---------------- named actions used outside TitanAbilities ----------------

	public static void leapLanding(TitanFormEntity form, ServerPlayer owner) {
		if (!(form.level() instanceof ServerLevel sl)) {
			return;
		}
		var a = TitanShifterConfig.abilities();
		Vec3 c = form.position();
		hitRadius(form, owner, c, a.leapLandingRadius, (float) TitanShifterConfig.damage().leapLanding,
				TitanShifterConfig.damage().punchKnockback, 0.5);
		impactFx(sl, c, a.leapLandingRadius, 1.4f);
		impactBlocks(sl, c, a.leapLandingRadius);
		form.footstep(sl, 1.8f, false);
		form.play("landing");
		shake(sl, c, 0.9f, 16);
	}

	/** Ground-impact particles, sound and a dust ring. */
	public static void impactFx(ServerLevel sl, Vec3 c, double radius, float power) {
		BlockState ground = sl.getBlockState(BlockPos.containing(c.x, c.y - 0.3, c.z));
		sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f * power, 0.6f);
		sl.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 2.5f * power, 0.4f);
		sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.4, c.z, 3, radius * 0.25, 0.1, radius * 0.25, 0.0);
		ring(sl, c, radius * 0.5, ParticleTypes.POOF, 16);
		ring(sl, c, radius, ParticleTypes.CAMPFIRE_COSY_SMOKE, 24);
		if (!ground.isAir()) {
			ring(sl, c, radius * 0.8, new BlockParticleOption(ParticleTypes.BLOCK, ground), 28);
		}
	}

	public static void ring(ServerLevel sl, Vec3 c, double radius, ParticleOptions particle, int count) {
		for (int i = 0; i < count; i++) {
			double ang = (Math.PI * 2.0 * i) / count;
			sl.sendParticles(particle, c.x + Math.cos(ang) * radius, c.y + 0.2, c.z + Math.sin(ang) * radius, 1,
					0.15, 0.1, 0.15, 0.03);
		}
	}

	// ---------------- terrain ----------------

	/** Optional (config) block damage for impacts: weak blocks only, capped. Off by default. */
	public static void impactBlocks(ServerLevel sl, Vec3 c, double radius) {
		if (!TitanShifterConfig.world().abilityBlockDestruction) {
			return;
		}
		breakWeakBlocks(sl, new AABB(c.x - radius, c.y - 1.0, c.z - radius, c.x + radius, c.y + 2.0, c.z + radius),
				TitanShifterConfig.world().maxBlocksPerAction);
	}

	/**
	 * Breaks foliage (leaves, tall grass, flowers, saplings...) inside {@code box}, at most {@code cap} blocks,
	 * no drops. Used for trampling and to clear a Titan's footprint when it forms -- never solid terrain.
	 */
	public static int breakWeakBlocks(ServerLevel sl, AABB box, int cap) {
		int broken = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
			for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
				for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
					if (broken >= cap) {
						return broken;
					}
					pos.set(x, y, z);
					BlockState state = sl.getBlockState(pos);
					if (isFoliage(state)) {
						sl.destroyBlock(pos, false);
						broken++;
					}
				}
			}
		}
		return broken;
	}

	public static boolean isFoliage(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
				&& (state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS)
						|| state.is(BlockTags.SAPLINGS));
	}

	// ---------------- shake / sound ----------------

	/** Camera tremor for everyone near {@code at}, fading with distance. */
	public static void shake(ServerLevel sl, Vec3 at, float intensity, int ticks) {
		if (!TitanShifterConfig.effects().cameraShake) {
			return;
		}
		double radius = TitanShifterConfig.effects().shakeRadius;
		double r2 = radius * radius;
		for (ServerPlayer p : sl.players()) {
			double d2 = p.distanceToSqr(at);
			if (d2 > r2) {
				continue;
			}
			float falloff = 1.0f - (float) (Math.sqrt(d2) / radius);
			ServerPlayNetworking.send(p, new TitanShakePayload(intensity * falloff, ticks));
		}
	}

	public static void sound(ServerLevel sl, Vec3 at, SoundEvent sound, float volume, float pitch) {
		sl.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

}
