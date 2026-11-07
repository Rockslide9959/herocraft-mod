package com.projecthero.mod.blackpanther.entity;

import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.BlackPantherCombat;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** v0.17.3: Kimoyo Flash (Shift+Z). A thrown bead that bursts on impact, blinding and slowing everything within 6 blocks for 3 s. */
public class KimoyoBeadEntity extends ThrowableItemProjectile {
	public KimoyoBeadEntity(EntityType<? extends KimoyoBeadEntity> type, Level level) {
		super(type, level);
	}

	public static KimoyoBeadEntity shoot(ServerPlayer owner) {
		KimoyoBeadEntity b = new KimoyoBeadEntity(BlackPantherEntityTypes.KIMOYO_BEAD, owner.level());
		b.setOwner(owner);
		Vec3 from = owner.getEyePosition().add(0, -0.2, 0).add(owner.getLookAngle().scale(0.5));
		b.setPos(from.x, from.y, from.z);
		b.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0f, 1.4f, 0.5f);
		owner.level().addFreshEntity(b);
		return b;
	}

	@Override
	protected Item getDefaultItem() {
		return BlackPantherItems.KIMOYO_BEAD;
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 c = result.getLocation();
		if (this.getOwner() instanceof ServerPlayer owner) {
			for (LivingEntity e : BlackPantherCombat.within(owner, c, BlackPantherConfig.FLASH_RADIUS, e -> BlackPantherCombat.isTarget(owner, e))) {
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, BlackPantherConfig.FLASH_TICKS, 0);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, BlackPantherConfig.FLASH_TICKS, 2);
			}
		}
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(BlackPanther.PURPLE_BIG, c.x, c.y, c.z, 30, 1.6, 0.8, 1.6, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0f, 1.5f);
		this.discard();
	}
}
