package com.projecthero.mod.horde.entity;

import com.projecthero.mod.hero.power.TempBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * v0.14.12: the web a Spider Horde spider (or the Brood Queen) spits. A small hit, a heavy slow, and a cobweb that
 * pins whoever it lands on for a few seconds -- placed through {@link TempBlocks}, so every web cleans itself up and
 * none outlives a raid. Never hurts spiders.
 *
 * <p>v0.14.16: four kinds ({@link Mode}) -- the plain web, the Queen's big web (a 3x3 patch), the Queen's
 * <b>cocoon</b> (wraps whoever it hits in a 3x3x2 web cocoon, blinds and all but roots them) and a Venom Spitter's
 * <b>venom glob</b> (no web: a green glob that poisons). The mode is saved, and the glob shows as a slime ball.
 */
public class WebShotEntity extends ThrowableItemProjectile {
	/** v0.14.16: what this shot does when it lands. */
	public enum Mode { WEB, QUEEN, COCOON, VENOM }

	private static final float DAMAGE = 2.0f;
	private static final int WEB_TICKS = 5 * 20;
	private static final int COCOON_TICKS = 4 * 20;
	private static final DustParticleOptions VENOM_DUST = new DustParticleOptions(new org.joml.Vector3f(0.45f, 0.95f, 0.25f), 1.2f);
	private Mode mode = Mode.WEB;

	public WebShotEntity(EntityType<? extends WebShotEntity> type, Level level) {
		super(type, level);
	}

	public WebShotEntity(Level level, LivingEntity shooter, boolean queen) {
		this(level, shooter, queen ? Mode.QUEEN : Mode.WEB);
	}

	public WebShotEntity(Level level, LivingEntity shooter, Mode mode) {
		super(HordeEntityTypes.WEB_SHOT, shooter, level);
		this.mode = mode;
		if (mode == Mode.VENOM) {
			setItem(new ItemStack(Items.SLIME_BALL));
		}
	}

	public Mode mode() {
		return mode;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.COBWEB;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server && tickCount % 2 == 0) {
			if (mode == Mode.VENOM) {
				server.sendParticles(VENOM_DUST, getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.0);
			} else {
				server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)),
						getX(), getY(), getZ(), mode == Mode.COCOON ? 3 : 1, 0.03, 0.03, 0.03, 0.0);
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		Entity target = result.getEntity();
		if (target == getOwner() || target instanceof Spider) {
			return;
		}
		float damage = switch (mode) {
			case WEB -> DAMAGE;
			case QUEEN -> DAMAGE * 3;
			case COCOON -> DAMAGE * 4;
			case VENOM -> 3.0f;
		};
		target.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity l ? l : null), damage);
		if (target instanceof LivingEntity living) {
			switch (mode) {
				case WEB -> living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2));
				case QUEEN -> living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
				case COCOON -> {
					living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, COCOON_TICKS, 4));
					living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
					living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, COCOON_TICKS, 1));
				}
				case VENOM -> living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
			}
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (mode == Mode.VENOM) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 0.8f, 1.4f);
			server.sendParticles(VENOM_DUST, getX(), getY(), getZ(), 14, 0.35, 0.25, 0.35, 0.0);
			server.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 8, 0.3, 0.2, 0.3, 0.05);
			discard();
			return;
		}
		boolean entityHit = result instanceof EntityHitResult;
		BlockPos at = result instanceof EntityHitResult e ? e.getEntity().blockPosition() : BlockPos.containing(result.getLocation());
		int r = mode == Mode.WEB ? 0 : 1;
		int up = mode == Mode.WEB ? 0 : 1;
		int ticks = mode == Mode.COCOON && entityHit ? COCOON_TICKS : WEB_TICKS;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, 0, -r), at.offset(r, up, r))) {
			if (server.getBlockState(p).isAir()) {
				TempBlocks.place(server, p.immutable(), Blocks.COBWEB.defaultBlockState(), ticks);
			}
		}
		if (mode == Mode.COCOON && entityHit) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_PLACE, SoundSource.HOSTILE, 1.2f, 0.6f);
			server.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 16, 0.6, 0.6, 0.6, 0.02);
		}
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 0.7f, 1.6f);
		server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)),
				getX(), getY(), getZ(), 12, 0.3, 0.3, 0.3, 0.05);
		discard();
	}

	@Override
	public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putString("WebMode", mode.name());
	}

	@Override
	public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		try {
			mode = Mode.valueOf(tag.getString("WebMode"));
		} catch (IllegalArgumentException e) {
			mode = Mode.WEB;
		}
	}
}
