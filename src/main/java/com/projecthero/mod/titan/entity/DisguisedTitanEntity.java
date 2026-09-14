package com.projecthero.mod.titan.entity;

import com.projecthero.mod.event.entity.RaidUndead;
import com.projecthero.mod.titan.TitanConfig;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

import org.joml.Vector3f;

/**
 * The Titan's disguised first form -- an ordinary-looking, ordinary-sized wanderer (80 HP, ~2 blocks
 * tall, ordinary zombie-style behaviour, no boss bar, no special drops) that nobody would suspect is
 * anything more, per the spec's explicit "should not reveal that it will transform" instruction.
 *
 * <h2>Transformation, not death</h2>
 * {@link #die} is overridden so reaching 0 HP does NOT run vanilla's normal death (no drops, no death
 * sound/animation): it freezes in place for {@value #TRANSFORM_DELAY_TICKS} ticks, then a REAL
 * lightning bolt strikes it, thunder plays, and {@link TitanEntity} is spawned at the same position
 * with full health and its boss bar immediately active. {@link #transformed} guarantees this can only
 * ever happen once per entity -- checked before anything else runs, mirroring the "mark before you
 * act, never after" duplication-guard discipline this mod's Symbiote worldgen already established.
 */
public class DisguisedTitanEntity extends RaidUndead {
	private static final int TRANSFORM_DELAY_TICKS = 20; // 1s freeze before the strike

	/**
	 * v0.10.18: a faint yellow spark drifting off the disguise -- a deliberate, small "tell" so an
	 * attentive player CAN pick this one out of a crowd of ordinary zombies, without it being an
	 * obvious glowing giveaway. Sparse on purpose (every {@value #TELLTALE_INTERVAL_TICKS} ticks, one
	 * or two particles) -- this replaces the previous "no visual tell at all" design now that the user
	 * has asked for one; it does not reveal that the wanderer will transform, just that it is not quite
	 * an ordinary zombie.
	 */
	private static final DustParticleOptions TELLTALE_PARTICLE = new DustParticleOptions(
			new Vector3f(1.0f, 0.85f, 0.15f), 1.0f);
	private static final int TELLTALE_INTERVAL_TICKS = 30; // 1.5s

	private boolean transformed;
	private int transformTicksLeft = -1;

	public DisguisedTitanEntity(EntityType<? extends DisguisedTitanEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, TitanConfig.stats().disguisedHealth)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.MOVEMENT_SPEED, 0.23)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	public boolean isBaby() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false; // a rare encounter must not silently despawn before the player finds it
	}

	@Override
	public void die(DamageSource source) {
		if (transformed || level().isClientSide()) {
			super.die(source);
			return;
		}
		transformed = true;
		// Stop the normal death process outright -- no super.die() call means no drops, no death sound,
		// no vanilla death animation. Bump health back above zero so LivingEntity.tick()'s own
		// isDeadOrDying() check doesn't immediately call die() again next tick.
		setHealth(1.0f);
		setNoAi(true);
		getNavigation().stop();
		transformTicksLeft = TRANSFORM_DELAY_TICKS;
		if (level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.5, getZ(),
					20, 0.4, 0.6, 0.4, 0.02);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (transformTicksLeft < 0) {
			if (level() instanceof ServerLevel server && tickCount % TELLTALE_INTERVAL_TICKS == 0) {
				double ox = (random.nextDouble() - 0.5) * 0.6;
				double oz = (random.nextDouble() - 0.5) * 0.6;
				server.sendParticles(TELLTALE_PARTICLE, getX() + ox, getY() + getBbHeight() * 0.6, getZ() + oz,
						2, 0.15, 0.25, 0.15, 0.0);
			}
			return;
		}
		if (level() instanceof ServerLevel server && transformTicksLeft % 4 == 0) {
			server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * 0.5, getZ(),
					4, 0.3, 0.4, 0.3, 0.01);
		}
		transformTicksLeft--;
		if (transformTicksLeft == 0) {
			completeTransformation();
		}
	}

	private void completeTransformation() {
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		// setVisualOnly(true) -- the same technique this mod's ambient crater lightning already uses
		// (ThorPowers.spawnVisualBolt): renders and sounds identically to a real strike, but deals no
		// entity damage and starts no fire. Without it, the bolt's own splash damage would hit the
		// just-spawned Titan (and any player standing close enough to have just killed the disguised
		// form) a tick or two after spawn -- confirmed by a gametest that caught the Titan appearing
		// with slightly less than full health.
		LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(server);
		if (bolt != null) {
			bolt.moveTo(position());
			bolt.setVisualOnly(true);
			server.addFreshEntity(bolt);
		}
		server.playSound(null, blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 3.0f, 1.0f);
		server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.0, getZ(), 1, 0.0, 0.0, 0.0, 0.0);

		double x = getX();
		double y = getY();
		double z = getZ();
		float yaw = getYRot();
		discard();

		TitanEntity titan = TitanEntityTypes.TITAN.create(server);
		if (titan != null) {
			titan.moveTo(x, y, z, yaw, 0.0f);
			server.addFreshEntity(titan);
			titan.onTransformed();
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Transformed", transformed);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		transformed = tag.getBoolean("Transformed");
	}
}
