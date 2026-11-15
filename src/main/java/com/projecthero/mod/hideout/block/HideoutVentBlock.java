package com.projecthero.mod.hideout.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.5: a hideout floor <b>vent</b> trap. A player who steps on it hears a hiss and sees it smoke ({@link #PHASE} 1);
 * {@link #ARM_TICKS} later it erupts straight up ({@link #erupt}): flame (Apokolips), plasma (Ultron), black biomass
 * (Symbiote) or frost (Frost Giants), hurting whatever stands on it. Then it cools down ({@link #PHASE} 2) before it can
 * fire again. Step off quickly and it misses. No block entity: the timing is two scheduled ticks.
 */
public class HideoutVentBlock extends Block {
	public enum Kind implements StringRepresentable {
		FIRE("fire"), PLASMA("plasma"), BIOMASS("biomass"), FROST("frost");

		private final String name;

		Kind(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final MapCodec<HideoutVentBlock> CODEC = simpleCodec(HideoutVentBlock::new);
	public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
	/** 0 idle, 1 armed (about to erupt), 2 cooling down. */
	public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 2);
	public static final int ARM_TICKS = 12;
	public static final int COOL_TICKS = 40;

	public HideoutVentBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(KIND, Kind.FIRE).setValue(PHASE, 0));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KIND, PHASE);
	}

	private static boolean triggers(Entity entity) {
		return entity instanceof Player p && !p.isCreative() && !p.isSpectator();
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (level instanceof ServerLevel server && state.getValue(PHASE) == 0 && triggers(entity)) {
			arm(server, pos, state);
		}
		super.stepOn(level, pos, state, entity);
	}

	/** Arms the vent now (it erupts {@link #ARM_TICKS} later). Also a test hook. */
	public static void arm(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state.setValue(PHASE, 1), 3);
		level.scheduleTick(pos, state.getBlock(), ARM_TICKS);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 1.6f);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 8, 0.25, 0.05, 0.25, 0.01);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int phase = state.getValue(PHASE);
		if (phase == 1) {
			erupt(level, pos, state.getValue(KIND));
			level.setBlock(pos, state.setValue(PHASE, 2), 3);
			level.scheduleTick(pos, this, COOL_TICKS);
		} else if (phase == 2) {
			level.setBlock(pos, state.setValue(PHASE, 0), 3);
		}
	}

	/** The burst: hurts every living thing standing in the 1x2.5 column above the vent. */
	public static void erupt(ServerLevel level, BlockPos pos, Kind kind) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		AABB column = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 3.5, pos.getZ() + 1);
		switch (kind) {
			case FIRE -> {
				level.sendParticles(ParticleTypes.FLAME, x, y, z, 40, 0.2, 1.2, 0.2, 0.05);
				level.sendParticles(ParticleTypes.LAVA, x, y, z, 4, 0.2, 0.2, 0.2, 0.0);
				level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.0f, 0.7f);
			}
			case PLASMA -> {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 40, 0.2, 1.2, 0.2, 0.2);
				level.sendParticles(ParticleTypes.END_ROD, x, y, z, 10, 0.15, 1.0, 0.15, 0.02);
				level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2f, 1.8f);
			}
			case BIOMASS -> {
				level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 40, 0.25, 1.0, 0.25, 0.05);
				level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 1.2f, 0.5f);
			}
			case FROST -> {
				level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 40, 0.2, 1.2, 0.2, 0.05);
				level.playSound(null, pos, SoundEvents.POWDER_SNOW_BREAK, SoundSource.BLOCKS, 1.2f, 0.6f);
			}
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, column, LivingEntity::isAlive)) {
			if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
				continue;
			}
			switch (kind) {
				case FIRE -> {
					e.hurt(level.damageSources().inFire(), 5.0f);
					e.igniteForSeconds(4.0f);
				}
				case PLASMA -> {
					e.hurt(level.damageSources().lightningBolt(), 5.0f);
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
				}
				case BIOMASS -> {
					e.hurt(level.damageSources().magic(), 3.0f);
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
					e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
				}
				case FROST -> {
					e.hurt(level.damageSources().freeze(), 4.0f);
					e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 120, e.getTicksFrozen() + 160));
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
				}
			}
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(state.getValue(PHASE) == 1 ? 1 : 6) != 0) {
			return;
		}
		var particle = switch (state.getValue(KIND)) {
			case FIRE -> ParticleTypes.SMOKE;
			case PLASMA -> ParticleTypes.ELECTRIC_SPARK;
			case BIOMASS -> ParticleTypes.SQUID_INK;
			case FROST -> ParticleTypes.SNOWFLAKE;
		};
		level.addParticle(particle, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02, pos.getZ() + 0.3 + random.nextDouble() * 0.4,
				0, 0.03, 0);
	}
}
