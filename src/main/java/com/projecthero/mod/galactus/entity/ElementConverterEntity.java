package com.projecthero.mod.galactus.entity;

import com.projecthero.mod.galactus.GalactusAnims;
import com.projecthero.mod.galactus.GalactusAnims.Clip;
import com.projecthero.mod.galactus.GalactusCombat;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.GalactusFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: an <b>Element Converter</b> -- one of the towers the Worldship drops round the arena in Act 2. It crashes down
 * ({@link #beginFall}), then eats the land round it: the event converts natural surface blocks in a radius that grows to
 * {@link GalactusConfig.Converters#maxRadius} (every original recorded, see {@code GalactusTerrain}) while the World
 * Consumption bar fills. <b>Shielded</b> while any of its Punisher guards lives. Destroyed, it drops an Ultimate Nullifier
 * part and the ground it ate comes back.
 */
public class ElementConverterEntity extends GalactusMob {
	/**
	 * v0.16.4: the radius (blocks) of the converter as drawn -- its 25-block model's base plate is ~13 blocks across and the
	 * shield bubble ~15 -- though its hit-box is only the 3-block core. Nothing of the event spawns inside it, and the
	 * Punishers are kept out of it ({@link PunisherRobotEntity}).
	 */
	public static final double FOOTPRINT_RADIUS = 7.5;

	private static final EntityDataAccessor<Boolean> DATA_SHIELDED = SynchedEntityData.defineId(ElementConverterEntity.class,
			EntityDataSerializers.BOOLEAN);

	private int index;
	private boolean landed;
	private int fallTicks;
	private int fallTotal;
	private double fallFromY;
	private double fallToY;
	/** The consumed radius so far, and the spiral cursor of the column walk (both saved). */
	public int radius;
	public int cursor;
	public long nextGrowAt = -1;
	private Clip shown;

	public ElementConverterEntity(EntityType<? extends ElementConverterEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, GalactusConfig.converters().health)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.ARMOR, GalactusConfig.converters().armor);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SHIELDED, true);
	}

	@Override
	public String model() {
		return GalactusAnims.CONVERTER;
	}

	@Override
	protected Clip idleClip() {
		return isShielded() ? GalactusAnims.CONVERTER_SHIELDED : landed ? GalactusAnims.CONVERTER_CONSUME : GalactusAnims.CONVERTER_IDLE;
	}

	public void configure(int index, double health) {
		this.index = index;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		setHealth((float) health);
	}

	public int index() {
		return index;
	}

	public boolean landed() {
		return landed;
	}

	public boolean isShielded() {
		return entityData.get(DATA_SHIELDED);
	}

	public void setShielded(boolean shielded) {
		if (shielded != isShielded()) {
			entityData.set(DATA_SHIELDED, shielded);
		}
	}

	/** Falls from {@code fromY} to its current position over {@code ticks}. */
	public void beginFall(double fromY, int ticks) {
		fallToY = getY();
		fallFromY = fromY;
		fallTotal = Math.max(1, ticks);
		fallTicks = 0;
		landed = false;
		setPos(getX(), fromY, getZ());
	}

	/** Gametest hook: down now. */
	public void landNow() {
		if (!landed && fallTotal > 0) {
			setPos(getX(), fallToY, getZ());
		}
		landed = true;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		setDeltaMovement(Vec3.ZERO);
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (!landed) {
			if (fallTotal <= 0) {
				landed = true;
			} else {
				fallTicks++;
				double t = Math.min(1.0, fallTicks / (double) fallTotal);
				setPos(getX(), fallFromY + (fallToY - fallFromY) * t * t, getZ());
				if (tickCount % 2 == 0) {
					GalactusFx.forced(server, GalactusFx.COSMIC, getX(), getY() + 4, getZ(), 6, 0.6, 2.0, 0.6, 0.05);
					GalactusFx.forced(server, ParticleTypes.FLAME, getX(), getY(), getZ(), 4, 0.6, 0.2, 0.6, 0.05);
				}
				if (fallTicks >= fallTotal) {
					landed = true;
					Vec3 at = position();
					GalactusFx.burst(server, at.add(0, 1, 0), 3.0);
					GalactusFx.shake(server, at, 1.0f, 20, 64);
					server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.5f);
				}
			}
		}
		Clip want = isDeadOrDying() ? GalactusAnims.CONVERTER_DESTROYED : idleClip();
		if (want != shown) {
			shown = want;
			setAnim(want); // every state is its own clip (the client has no landed flag)
		}
		if (isShielded() && tickCount % 5 == 0) {
			server.sendParticles(GalactusFx.BLUE, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, getBbWidth() * 0.7, getBbHeight() * 0.4,
					getBbWidth() * 0.7, 0.0);
		} else if (landed && tickCount % 4 == 0) {
			server.sendParticles(GalactusFx.COSMIC, getX(), getY() + getBbHeight() * 0.9, getZ(), 3, 0.4, 0.4, 0.4, 0.02);
			server.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 4, 2.0, 0.2, 2.0, 0.05);
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.getEntity() instanceof LivingEntity attacker && GalactusCombat.isCosmic(attacker)) {
			return false;
		}
		if (isShielded() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (level() instanceof ServerLevel server) {
				server.sendParticles(GalactusFx.BLUE, getX(), getY() + getBbHeight() * 0.5, getZ(), 12, getBbWidth() * 0.6, getBbHeight() * 0.3,
						getBbWidth() * 0.6, 0.02);
				server.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, 0.6f);
				if (source.getEntity() instanceof Player p) {
					p.displayClientMessage(Component.translatable("message.projecthero.galactus.converter_shielded").withStyle(ChatFormatting.LIGHT_PURPLE),
							true);
				}
			}
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			GalactusFx.burst(server, position().add(0, getBbHeight() * 0.5, 0), 4.0);
			GalactusFx.shake(server, position(), 0.8f, 16, 64);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.7f);
			GalactusEvent e = event();
			if (e != null) {
				e.onConverterDestroyed(server, this);
			}
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("ConverterIndex", index);
		tag.putBoolean("Landed", landed || fallTicks >= fallTotal);
		if (!landed) {
			tag.putDouble("FallToY", fallToY);
		}
		tag.putInt("Radius", radius);
		tag.putInt("Cursor", cursor);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		index = tag.getInt("ConverterIndex");
		landed = tag.getBoolean("Landed");
		if (!landed && tag.contains("FallToY")) {
			setPos(getX(), tag.getDouble("FallToY"), getZ());
		}
		landed = true; // a reloaded converter is down
		radius = tag.getInt("Radius");
		cursor = tag.getInt("Cursor");
	}
}
