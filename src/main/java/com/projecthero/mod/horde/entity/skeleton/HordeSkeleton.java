package com.projecthero.mod.horde.entity.skeleton;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16: the shared base of the Skeleton Horde's own skeletons -- one for each kind of zombie the Zombie Horde sends
 * (see {@link SkeletonHordeRoster}). Like the raid's {@code SwordSkeleton} it extends vanilla {@link Skeleton} (whose
 * package-private step sound a subclass can't otherwise supply) and fixes what a horde mob must never do:
 * <ul>
 *   <li>burn in daylight (a horde block is woken whenever a player likes);</li>
 *   <li>turn into a vanilla stray in powder snow (it would drop out of the wave it belongs to);</li>
 *   <li>drop its gear or pick up loot.</li>
 * </ul>
 * Each knows the wave it came out in ({@link #setHordeWave}, 0 = a spawn egg or {@code /summon}) and gets 6% more health
 * for every wave after the first.
 */
public abstract class HordeSkeleton extends Skeleton {
	private static final String TAG_WAVE = "HordeWave";
	private int hordeWave;

	protected HordeSkeleton(EntityType<? extends HordeSkeleton> type, Level level) {
		super(type, level);
		refreshDimensions(); // the Runner and the Brute are resized by their SCALE attribute
	}

	/** The wave it belongs to; set before {@code finalizeSpawn}. */
	public void setHordeWave(int wave) {
		this.hordeWave = Math.max(0, wave);
	}

	public int hordeWave() {
		return hordeWave;
	}

	/** Its kit: weapon, armour, off-hand. Called from {@code finalizeSpawn} in place of vanilla's bow. */
	protected abstract void equip(RandomSource random);

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		equip(random);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			setDropChance(slot, 0.0f);
		}
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setCanPickUpLoot(false);
		if (hordeWave > 1) {
			AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
			if (hp != null && hp.getModifier(WAVE_HEALTH) == null) {
				hp.addPermanentModifier(new AttributeModifier(WAVE_HEALTH, 0.06 * (hordeWave - 1), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
				setHealth(getMaxHealth());
			}
		}
		return out;
	}

	private static final net.minecraft.resources.ResourceLocation WAVE_HEALTH = ProjectHeroMod.id("horde_wave_health");

	@Override
	protected boolean isSunBurnTick() {
		return false;
	}

	@Override
	protected void doFreezeConversion() {
		// never becomes a vanilla stray -- it would silently leave the horde
	}

	/** The arrow its bow would fire right now (the variants' arrows are what set them apart). */
	public AbstractArrow makeArrow() {
		return getArrow(new ItemStack(Items.ARROW), 1.0f, getMainHandItem());
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt(TAG_WAVE, hordeWave);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		hordeWave = tag.getInt(TAG_WAVE);
	}

	// ---------------------------------------------------------------- shared helpers

	/** Someone a horde skeleton's area attacks should hurt: not monsters, armour stands, creative players or spectators. */
	public static boolean enemy(LivingEntity e) {
		if (!e.isAlive() || e instanceof Monster || e instanceof ArmorStand) {
			return false;
		}
		return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
	}

	/**
	 * A blast that only hurts the horde's enemies (a vanilla explosion would shred the wave): {@code damage} at the
	 * centre falling to half at {@code radius}, a shove outward, explosion particles and sound. No blocks broken.
	 */
	public static void blast(ServerLevel level, LivingEntity source, Vec3 at, double radius, float damage, double shove) {
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, radius * 0.25, 0.2, radius * 0.25, 0.0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 16, radius * 0.3, 0.3, radius * 0.3, 0.05);
		level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
				net.minecraft.sounds.SoundSource.HOSTILE, 1.2f, 1.3f);
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), HordeSkeleton::enemy);
		for (LivingEntity e : hit) {
			double d = e.position().distanceTo(at);
			if (d > radius) {
				continue;
			}
			float scaled = (float) (damage * (1.0 - 0.5 * d / radius));
			e.hurt(source == null ? level.damageSources().explosion(null, null) : level.damageSources().mobAttack(source), scaled);
			Vec3 away = e.position().subtract(at).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 0) : away.normalize();
			e.push(away.x * shove, 0.35 + shove * 0.2, away.z * shove);
			e.hurtMarked = true;
		}
	}

	/** A ring of particles on the ground round {@code at} -- every horde wind-up is drawn with one. */
	public static void ring(ServerLevel level, ParticleOptions particle, Vec3 at, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points;
			level.sendParticles(particle, at.x + Math.cos(a) * radius, at.y + 0.15, at.z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0);
		}
	}
}
