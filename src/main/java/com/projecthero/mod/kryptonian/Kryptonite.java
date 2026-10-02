package com.projecthero.mod.kryptonian;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.kryptonian.data.KryptonianState;
import com.projecthero.mod.kryptonian.item.KryptonianItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

/**
 * The one weakness (v0.14.8). Every 10 ticks each Kryptonian looks for kryptonite: ore or a block within
 * {@link KryptonianConfig#KRYPTONITE_BLOCK_RADIUS}, a shard lying on the ground or in anyone's hand within
 * {@link KryptonianConfig#KRYPTONITE_ENTITY_RADIUS}, or a shard in his own inventory. Exposed, he is
 * {@link KryptonianState#weakened}: the passives go (so no damage reduction, no bonus health), flight drops, no moves,
 * Weakness II and Slowness II, 1 damage a second and Solar Energy drains 5 a second. It lingers 2 s after the source is
 * gone.
 */
public final class Kryptonite {
	/** Player -> game time he was last found near kryptonite. */
	private static final Map<UUID, Long> LAST_EXPOSED = new ConcurrentHashMap<>();

	private Kryptonite() {
	}

	public static void clearSessionState() {
		LAST_EXPOSED.clear();
	}

	static void clear(UUID id) {
		LAST_EXPOSED.remove(id);
	}

	static void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		KryptonianState s = Kryptonian.state(player);
		if (player.tickCount % 10 == 0 && !player.isSpectator() && exposed(player)) {
			LAST_EXPOSED.put(player.getUUID(), now);
			if (!s.weakened) {
				Kryptonian.setWeakened(player, true);
				s = Kryptonian.state(player);
			}
		}
		if (!s.weakened) {
			return;
		}
		Long last = LAST_EXPOSED.get(player.getUUID());
		if (last == null || now - last > KryptonianConfig.KRYPTONITE_LINGER_TICKS) {
			Kryptonian.setWeakened(player, false);
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		if (player.tickCount % 20 == 0) {
			Kryptonian.keepEffect(player, MobEffects.WEAKNESS, 1, 60);
			Kryptonian.keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 1, 60);
			if (!player.isCreative()) {
				player.hurt(level.damageSources().magic(), KryptonianConfig.KRYPTONITE_DAMAGE_PER_SECOND);
			}
			if (s.solar > 0f) {
				KryptonianState n = Kryptonian.state(player).copy();
				n.solar = Math.max(0f, n.solar - KryptonianConfig.KRYPTONITE_SOLAR_DRAIN_PER_SECOND);
				n.lastDrain = now;
				Kryptonian.save(player, n);
			}
		}
		if (player.tickCount % 4 == 0) {
			level.sendParticles(Kryptonian.KRYPTONITE_GREEN, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
					2, 0.3, player.getBbHeight() * 0.35, 0.3, 0.0);
		}
	}

	/** Is there kryptonite close enough to hurt this player? */
	public static boolean exposed(ServerPlayer player) {
		if (carries(player)) {
			return true;
		}
		ServerLevel level = (ServerLevel) player.level();
		double r = KryptonianConfig.KRYPTONITE_ENTITY_RADIUS;
		AABB box = player.getBoundingBox().inflate(r);
		if (!level.getEntitiesOfClass(ItemEntity.class, box, e -> isKryptonite(e.getItem())).isEmpty()) {
			return true;
		}
		if (!level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player
				&& (isKryptonite(e.getMainHandItem()) || isKryptonite(e.getOffhandItem()))).isEmpty()) {
			return true;
		}
		return blockNearby(level, player.blockPosition(), KryptonianConfig.KRYPTONITE_BLOCK_RADIUS);
	}

	private static boolean carries(Player player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isKryptonite(inv.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	public static boolean isKryptonite(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(KryptonianItems.KRYPTONITE_SHARD) || stack.is(KryptonianItems.KRYPTONITE_ORE_ITEM)
				|| stack.is(KryptonianItems.KRYPTONITE_BLOCK_ITEM));
	}

	public static boolean isKryptonite(BlockState state) {
		return state.is(KryptonianItems.KRYPTONITE_ORE) || state.is(KryptonianItems.KRYPTONITE_BLOCK);
	}

	/** A cube search, skipping any 16^3 chunk section that holds no kryptonite at all (the palette check is cheap). */
	static boolean blockNearby(ServerLevel level, BlockPos center, int radius) {
		int minSx = (center.getX() - radius) >> 4;
		int maxSx = (center.getX() + radius) >> 4;
		int minSy = (center.getY() - radius) >> 4;
		int maxSy = (center.getY() + radius) >> 4;
		int minSz = (center.getZ() - radius) >> 4;
		int maxSz = (center.getZ() + radius) >> 4;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int sx = minSx; sx <= maxSx; sx++) {
			for (int sz = minSz; sz <= maxSz; sz++) {
				if (!level.hasChunk(sx, sz)) {
					continue;
				}
				var chunk = level.getChunk(sx, sz);
				for (int sy = minSy; sy <= maxSy; sy++) {
					int idx = chunk.getSectionIndexFromSectionY(sy);
					if (idx < 0 || idx >= chunk.getSections().length) {
						continue;
					}
					LevelChunkSection section = chunk.getSections()[idx];
					if (section == null || section.hasOnlyAir() || !section.maybeHas(Kryptonite::isKryptonite)) {
						continue;
					}
					int x0 = Math.max(center.getX() - radius, sx << 4);
					int x1 = Math.min(center.getX() + radius, (sx << 4) + 15);
					int y0 = Math.max(center.getY() - radius, sy << 4);
					int y1 = Math.min(center.getY() + radius, (sy << 4) + 15);
					int z0 = Math.max(center.getZ() - radius, sz << 4);
					int z1 = Math.min(center.getZ() + radius, (sz << 4) + 15);
					for (int x = x0; x <= x1; x++) {
						for (int y = y0; y <= y1; y++) {
							for (int z = z0; z <= z1; z++) {
								if (isKryptonite(section.getBlockState(x & 15, y & 15, z & 15))) {
									return true;
								}
							}
						}
					}
				}
			}
		}
		return false;
	}
}
