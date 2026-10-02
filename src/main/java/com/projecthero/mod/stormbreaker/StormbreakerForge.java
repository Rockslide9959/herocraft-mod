package com.projecthero.mod.stormbreaker;

import com.projecthero.mod.item.ModItems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.19: "forged in the Nether". An {@link UnforgedStormbreakerItem} dropped into lava IN THE NETHER becomes
 * Stormbreaker after {@link #FORGE_TICKS} ticks in the lava. Outside the Nether, lava does nothing but earn nearby
 * players a hint now and then. Driven from {@code ItemEntityMixin}'s tick, server side only; the progress lives on
 * the item entity itself ({@link Forgeable}), so there is no static cache to leak.
 *
 * <p>Lava makes a fire-resistant item bob at the surface, which reads as briefly "out of the lava" every so often, so
 * progress only resets after {@link #OUT_OF_LAVA_GRACE} consecutive ticks out of it -- a real pull-out, not a bob.
 */
public final class StormbreakerForge {
	/** 10 seconds in the lava. */
	public static final int FORGE_TICKS = 200;
	/** Consecutive ticks out of the lava before the heat is lost and the count starts over. */
	public static final int OUT_OF_LAVA_GRACE = 10;
	/** How often the "only the Nether is hot enough" hint is shown, in lava ticks. */
	private static final int HINT_INTERVAL = 100;
	private static final double HINT_RADIUS = 12.0;
	private static final double LAUNCH_RADIUS = 16.0;

	/** Implemented by {@code ItemEntityMixin}: per-item-entity forging progress. */
	public interface Forgeable {
		int projecthero$forgeTicks();

		void projecthero$setForgeTicks(int ticks);

		int projecthero$outOfLavaTicks();

		void projecthero$setOutOfLavaTicks(int ticks);
	}

	private StormbreakerForge() {
	}

	/** The real-world entry point: is it in lava, and is that lava in the Nether? */
	public static void serverTick(ItemEntity entity) {
		if (!entity.getItem().is(ModItems.UNFORGED_STORMBREAKER)) {
			return;
		}
		tickForging(entity, entity.isInLava(), entity.level().dimension() == Level.NETHER);
	}

	/**
	 * One tick of the forging rule, with the environment passed in so it can be tested without building a Nether.
	 *
	 * @return true on the tick the axe is forged
	 */
	public static boolean tickForging(ItemEntity entity, boolean inLava, boolean inNether) {
		if (!(entity instanceof Forgeable forge) || !entity.getItem().is(ModItems.UNFORGED_STORMBREAKER)
				|| !(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!inLava) {
			int out = forge.projecthero$outOfLavaTicks() + 1;
			forge.projecthero$setOutOfLavaTicks(out);
			if (out > OUT_OF_LAVA_GRACE && forge.projecthero$forgeTicks() > 0) {
				forge.projecthero$setForgeTicks(0);
				if (inNether) {
					level.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 0.2, entity.getZ(), 12, 0.15, 0.15, 0.15, 0.02);
					level.playSound(null, entity.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.2f);
				}
			}
			return false;
		}
		forge.projecthero$setOutOfLavaTicks(0);
		int ticks = forge.projecthero$forgeTicks() + 1;
		forge.projecthero$setForgeTicks(ticks);

		if (!inNether) {
			if (ticks % HINT_INTERVAL == 1) {
				hint(level, entity);
			}
			return false;
		}

		// It is being forged: hold it in the lava rather than letting it bob away, and let it build.
		entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.5, 0.0, 0.5));
		forgingEffects(level, entity, ticks);
		if (ticks < FORGE_TICKS) {
			return false;
		}
		forge(level, entity);
		return true;
	}

	private static void hint(ServerLevel level, ItemEntity entity) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(entity) <= HINT_RADIUS * HINT_RADIUS) {
				player.displayClientMessage(Component.translatable("message.projecthero.stormbreaker.not_hot_enough"), true);
			}
		}
	}

	/** Rising flame, lava and smoke every few ticks; a fire roar and anvil ring every second that build as it heats. */
	private static void forgingEffects(ServerLevel level, ItemEntity entity, int ticks) {
		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		float heat = Math.min(1.0f, ticks / (float) FORGE_TICKS);
		if (ticks % 3 == 0) {
			level.sendParticles(ParticleTypes.FLAME, x, y + 0.3, z, 2 + (int) (heat * 6), 0.2, 0.2, 0.2, 0.03 + heat * 0.05);
			level.sendParticles(ParticleTypes.SMOKE, x, y + 0.6, z, 1 + (int) (heat * 3), 0.2, 0.3, 0.2, 0.02);
		}
		if (ticks % 7 == 0) {
			level.sendParticles(ParticleTypes.LAVA, x, y + 0.2, z, 1 + (int) (heat * 3), 0.2, 0.1, 0.2, 0.0);
		}
		if (ticks % 20 == 0) {
			level.playSound(null, x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.6f + heat, 0.6f + heat * 0.4f);
			level.playSound(null, x, y, z, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.25f + heat * 0.35f, 0.5f + heat * 0.5f);
			if (heat > 0.5f) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.5, z, (int) (heat * 12), 0.25, 0.35, 0.25, 0.1);
			}
		}
	}

	/** Done: the stack becomes Stormbreaker, the lava bursts and throws it up toward the nearest player. */
	private static void forge(ServerLevel level, ItemEntity entity) {
		entity.setItem(new ItemStack(ModItems.STORMBREAKER));
		entity.setUnlimitedLifetime();
		entity.setPickUpDelay(10);
		if (entity instanceof Forgeable forge) {
			forge.projecthero$setForgeTicks(0);
			forge.projecthero$setOutOfLavaTicks(0);
		}

		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		level.sendParticles(ParticleTypes.FLAME, x, y + 0.5, z, 80, 0.4, 0.6, 0.4, 0.2);
		level.sendParticles(ParticleTypes.LAVA, x, y + 0.3, z, 30, 0.6, 0.2, 0.6, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 1.0, z, 60, 0.5, 1.0, 0.5, 0.3);
		level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.2f, 0.9f);
		level.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.9f, 0.7f);
		level.playSound(null, x, y, z, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0f, 0.6f);

		Player nearest = level.getNearestPlayer(x, y, z, LAUNCH_RADIUS, p -> !p.isSpectator());
		Vec3 velocity = new Vec3(0.0, 0.9, 0.0);
		if (nearest != null) {
			Vec3 flat = new Vec3(nearest.getX() - x, 0.0, nearest.getZ() - z);
			double distance = flat.length();
			if (distance > 0.5) {
				velocity = flat.scale(Math.min(0.9, distance * 0.07) / distance).add(0.0, 0.85, 0.0);
			}
		}
		entity.setDeltaMovement(velocity);
		entity.hurtMarked = true;
		entity.hasImpulse = true;
	}
}
