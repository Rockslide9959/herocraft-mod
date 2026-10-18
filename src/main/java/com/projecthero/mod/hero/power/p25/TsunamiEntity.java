package com.projecthero.mod.hero.power.p25;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * v0.16.6 hold Z -- Tsunami: the Tidal Wave grown huge -- {@link #HALF_WIDTH_T 12} wide and {@link #HEIGHT_T 6} tall, it
 * rears up for longer and rolls {@link #DISTANCE_T 30} blocks, {@link WaterHandlers#TSUNAMI_DAMAGE} to everything it
 * reaches, sweeping them along with it (bosses take the hit but are not carried) and putting out fire. The same curling
 * wall model as the Tidal Wave, scaled up ({@code WaterFxRenderer}); an entity, so it leaves no water blocks behind.
 */
public class TsunamiEntity extends TidalWaveEntity {
	public static final double HALF_WIDTH_T = 6.0;
	public static final double HEIGHT_T = 6.0;
	public static final double DISTANCE_T = 30.0;
	public static final int RISE_T = 8;
	public static final int RUN_T = 30;
	public static final int COLLAPSE_T = 12;

	public TsunamiEntity(EntityType<? extends TsunamiEntity> type, Level level) {
		super(type, level);
	}

	public static TsunamiEntity raise(ServerPlayer owner) {
		return launch(owner, new TsunamiEntity(WaterEntities.TSUNAMI, AbilityHelpers.level(owner)));
	}

	@Override
	public double halfWidth() {
		return HALF_WIDTH_T;
	}

	@Override
	public double height() {
		return HEIGHT_T;
	}

	@Override
	public int rise() {
		return RISE_T;
	}

	@Override
	public int run() {
		return RUN_T;
	}

	@Override
	public int collapse() {
		return COLLAPSE_T;
	}

	@Override
	public double speed() {
		return DISTANCE_T / RUN_T;
	}

	@Override
	public float damage() {
		return WaterHandlers.TSUNAMI_DAMAGE;
	}

	@Override
	protected boolean carries(Entity e) {
		return !(e instanceof LivingEntity le) || !WaterHandlers.isBossLike(le);
	}
}
