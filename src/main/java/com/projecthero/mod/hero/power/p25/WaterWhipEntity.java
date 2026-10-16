package com.projecthero.mod.hero.power.p25;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * G -- Water Whip: the lash itself, a purely visual body. The entity sits at the whip's tip (on the target, or the aim
 * point it cracked at); the client draws a translucent rope of water from the caster's hand to it, uncurling along a
 * travelling S-curve, cracking at the tip and pulling back ({@code WaterFxRenderer}). The damage and the pull are dealt
 * by {@link WaterHandlers} the moment it is cast.
 */
public class WaterWhipEntity extends WaterFxEntity {
	public static final int LIFE = 12;

	public WaterWhipEntity(EntityType<? extends WaterWhipEntity> type, Level level) {
		super(type, level);
	}

	public static WaterWhipEntity lash(ServerPlayer owner, LivingEntity target, Vec3 tip) {
		ServerLevel level = AbilityHelpers.level(owner);
		WaterWhipEntity e = new WaterWhipEntity(WaterEntities.WATER_WHIP, level);
		e.setup(owner, target, (float) tip.distanceTo(owner.getEyePosition()));
		e.setPos(tip.x, tip.y, tip.z);
		level.addFreshEntity(e);
		return e;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		LivingEntity t = target();
		if (t != null) {
			Vec3 m = mid(t); // the tip stays wrapped round the target while it is reeled in
			setPos(m.x, m.y, m.z);
		}
		if (owner() == null || tickCount > LIFE) {
			discard();
		}
	}
}
