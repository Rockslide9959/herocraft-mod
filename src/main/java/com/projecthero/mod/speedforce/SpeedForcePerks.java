package com.projecthero.mod.speedforce;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.speedforce.network.SpeedForceFxPayload;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user): the Speed Force Speedster's run passives -- all passive or menu-driven, no keys.
 * <ul>
 *   <li><b>Lightning charge-up</b>: running in Speed Mode builds static ({@link SpeedForceConfig#STATIC_PER_BLOCK} a block, up
 *       to {@link SpeedForceConfig#STATIC_MAX}; synced to the owner for the HUD pip). The next melee hit releases it as chain
 *       lightning: the struck foe, then up to {@link SpeedForceConfig#STATIC_CHAIN_TARGETS} more nearby, through the Speed
 *       Force damage scaling ({@link SpeedForceCombat#strike}).</li>
 *   <li><b>Speed farming</b>: running through fully grown crops in Speed Mode harvests them and replants from the drops.</li>
 *   <li><b>Speed mining</b> (menu toggle): an ore mined in Speed Mode takes up to {@link SpeedForceConfig#VEIN_MAX}
 *       connected same ores with it, each broken as the player would (drops, tool wear, protection events).</li>
 * </ul>
 * Block changes go through {@link GeoTerrain#canEdit} (the terrain-damage setting, adventure / spawn protection / claims,
 * the world border) and fire Fabric's block-break events, as the mod's other block-breaking powers do. Static state is
 * cleared through {@code SpeedForceSpeedster#clearSessionState}.
 */
public final class SpeedForcePerks {
	/** Players whose vein is being broken right now (each extra block fires the break event again). */
	private static final Set<UUID> VEINING = ConcurrentHashMap.newKeySet();

	private SpeedForcePerks() {
	}

	public static void initialize() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp && entity instanceof LivingEntity target) {
				onMelee(sp, target);
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
				vein(sp, sl, pos, state);
			}
		});
		SpeedForceWorks.initialize();
		SpeedForceRaces.initialize();
	}

	static void clearSessionState() {
		VEINING.clear();
		SpeedForceWorks.clearSessionState();
		SpeedForceRaces.clearSessionState();
	}

	/** Once a player tick (from {@code SpeedForceSpeedster#tick}): Speed Build, Speed Smelt and the race. */
	static void tick(ServerPlayer p) {
		SpeedForceWorks.tick(p);
		SpeedForceRaces.tick(p);
	}

	// ---------------------------------------------------------------- the static charge

	/** One tick of the run (from {@code runStep}): the static builds, the crops underfoot are reaped. */
	static void onRun(ServerPlayer p, SpeedForceSpeedster.Motion mo, Vec3 last, Vec3 now, double raw) {
		double horiz = Math.sqrt((now.x - last.x) * (now.x - last.x) + (now.z - last.z) * (now.z - last.z));
		if (horiz > 0.3) {
			mo.idle = 0;
			mo.charge = Math.min(SpeedForceConfig.STATIC_MAX, mo.charge + horiz * SpeedForceConfig.STATIC_PER_BLOCK);
		} else if (++mo.idle > SpeedForceConfig.STATIC_IDLE_TICKS) {
			mo.charge = Math.max(0.0, mo.charge - SpeedForceConfig.STATIC_IDLE_DECAY_PER_TICK);
		}
		syncCharge(p, mo);
		if (horiz > 0.05 && p.level() instanceof ServerLevel level) {
			farm(p, level, last, now);
		}
	}

	/** Speed Mode went off: the static with it. */
	static void dropCharge(ServerPlayer p, SpeedForceSpeedster.Motion mo) {
		mo.charge = 0.0;
		syncCharge(p, mo);
	}

	private static void syncCharge(ServerPlayer p, SpeedForceSpeedster.Motion mo) {
		int v = (int) Math.floor(mo.charge);
		if (v == mo.syncedCharge) {
			return;
		}
		mo.syncedCharge = v;
		if (v <= 0) {
			p.removeAttached(SpeedForceAttachments.STATIC_CHARGE);
		} else {
			p.setAttached(SpeedForceAttachments.STATIC_CHARGE, v);
		}
	}

	/** The static charge right now, 0..{@link SpeedForceConfig#STATIC_MAX} (the server's). */
	public static float charge(ServerPlayer p) {
		SpeedForceSpeedster.Motion mo = SpeedForceSpeedster.MOTION.get(p.getUUID());
		return mo == null ? 0f : (float) mo.charge;
	}

	/** Gametests: set the static charge. */
	public static void setCharge(ServerPlayer p, float charge) {
		SpeedForceSpeedster.Motion mo = SpeedForceSpeedster.MOTION.computeIfAbsent(p.getUUID(), k -> new SpeedForceSpeedster.Motion());
		mo.charge = Math.max(0f, Math.min(SpeedForceConfig.STATIC_MAX, charge));
		syncCharge(p, mo);
	}

	/**
	 * A melee hit on {@code target}: with enough static, it all goes -- a bolt into the target, then hopping on to the nearest
	 * foes. Returns how many it struck (0: no discharge).
	 */
	public static int onMelee(ServerPlayer p, LivingEntity target) {
		SpeedForceSpeedster.Motion mo = SpeedForceSpeedster.MOTION.get(p.getUUID());
		if (mo == null || mo.charge < SpeedForceConfig.STATIC_MIN_RELEASE || !SpeedForceSpeedster.speedActive(p)
				|| !SpeedForceCombat.isTarget(p, target) || !(p.level() instanceof ServerLevel level)) {
			return 0;
		}
		float charge = (float) mo.charge;
		mo.charge = 0.0;
		syncCharge(p, mo);
		float dmg = SpeedForceConfig.staticDamage(charge);
		Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.5)).subtract(0, 0.4, 0);
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.BOLT, hand, at);
		int struck = SpeedForceCombat.strike(p, target, hand, dmg, 0.3, 0.0) ? 1 : 0;
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 24, 0.4, 0.4, 0.4, 0.25);
		List<LivingEntity> hit = new ArrayList<>();
		hit.add(target);
		Vec3 from = at;
		float chainDmg = dmg;
		for (int i = 0; i < SpeedForceConfig.STATIC_CHAIN_TARGETS; i++) {
			LivingEntity next = SpeedForceCombat.nearest(p, from, SpeedForceConfig.STATIC_CHAIN_RADIUS, hit);
			if (next == null) {
				break;
			}
			chainDmg *= SpeedForceConfig.STATIC_CHAIN_FALLOFF;
			Vec3 to = next.position().add(0, next.getBbHeight() * 0.5, 0);
			SpeedForceSpeedster.fx(p, SpeedForceFxPayload.BOLT, from, to);
			if (SpeedForceCombat.strike(p, next, from, chainDmg, 0.2, 0.0)) {
				struck++;
			}
			hit.add(next);
			from = to;
		}
		SpeedForceSpeedster.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9f, 1.8f);
		SpeedForceSpeedster.sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.4f, 2.0f);
		return struck;
	}

	// ---------------------------------------------------------------- speed farming

	private static void farm(ServerPlayer p, ServerLevel level, Vec3 from, Vec3 to) {
		double len = from.distanceTo(to);
		int steps = (int) Math.max(1, Math.min(64, Math.ceil(len / 0.5)));
		Set<BlockPos> seen = new HashSet<>();
		int done = 0;
		int r = SpeedForceConfig.FARM_REACH;
		for (int i = 0; i <= steps && done < SpeedForceConfig.FARM_MAX_PER_TICK; i++) {
			Vec3 q = from.lerp(to, i / (double) steps);
			BlockPos feet = BlockPos.containing(q.x, q.y + 0.2, q.z);
			for (int dx = -r; dx <= r && done < SpeedForceConfig.FARM_MAX_PER_TICK; dx++) {
				for (int dz = -r; dz <= r && done < SpeedForceConfig.FARM_MAX_PER_TICK; dz++) {
					BlockPos pos = feet.offset(dx, 0, dz);
					if (seen.add(pos) && harvest(p, level, pos, done % 4 == 0)) {
						done++;
					}
				}
			}
		}
	}

	/**
	 * Harvests the crop at {@code pos} if it is fully grown -- its drops on the ground, one seed taken from them to replant
	 * (none among them: the plot is left empty). Public for the gametests.
	 */
	public static boolean harvest(ServerPlayer p, ServerLevel level, BlockPos pos, boolean effects) {
		BlockState st = level.getBlockState(pos);
		BlockState replant;
		if (st.getBlock() instanceof CropBlock crop) {
			if (!crop.isMaxAge(st)) {
				return false;
			}
			replant = crop.getStateForAge(0);
		} else if (st.getBlock() instanceof NetherWartBlock) {
			if (st.getValue(NetherWartBlock.AGE) < 3) {
				return false;
			}
			replant = st.setValue(NetherWartBlock.AGE, 0);
		} else {
			return false;
		}
		if (!GeoTerrain.canEdit(p, level, pos) || !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, p, pos, st, null)) {
			return false;
		}
		List<ItemStack> drops = Block.getDrops(st, level, pos, null, p, p.getMainHandItem());
		Item seed = st.getBlock().asItem();
		boolean planted = false;
		for (ItemStack d : drops) {
			if (!planted && seed != Items.AIR && d.is(seed)) {
				d.shrink(1);
				planted = true;
			}
		}
		level.setBlock(pos, planted ? replant : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		for (ItemStack d : drops) {
			if (!d.isEmpty()) {
				Block.popResource(level, pos, d);
			}
		}
		if (effects) {
			level.levelEvent(2001, pos, Block.getId(st));
		}
		return true;
	}

	// ---------------------------------------------------------------- speed mining

	/**
	 * An ore {@code p} just broke at {@code origin}: in Speed Mode with vein mining on (and the right tool), the connected
	 * same ores go with it -- each through the player's own block breaking (drops, tool wear, protection). Returns how many
	 * more broke.
	 */
	public static int vein(ServerPlayer p, ServerLevel level, BlockPos origin, BlockState state) {
		if (VEINING.contains(p.getUUID()) || !SpeedForceSpeedster.speedActive(p) || !SpeedForceSpeedster.state(p).veinMine
				|| !state.is(ConventionalBlockTags.ORES) || p.isSpectator()) {
			return 0;
		}
		if (state.requiresCorrectToolForDrops() && !p.hasCorrectToolForDrops(state)) {
			return 0;
		}
		Block block = state.getBlock();
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		visited.add(origin);
		queue.add(origin);
		while (!queue.isEmpty() && found.size() < SpeedForceConfig.VEIN_MAX) {
			BlockPos at = queue.poll();
			for (int dx = -1; dx <= 1 && found.size() < SpeedForceConfig.VEIN_MAX; dx++) {
				for (int dy = -1; dy <= 1 && found.size() < SpeedForceConfig.VEIN_MAX; dy++) {
					for (int dz = -1; dz <= 1 && found.size() < SpeedForceConfig.VEIN_MAX; dz++) {
						BlockPos n = at.offset(dx, dy, dz);
						if (visited.add(n) && level.isLoaded(n) && level.getBlockState(n).is(block)) {
							found.add(n);
							queue.add(n);
						}
					}
				}
			}
		}
		ItemStack tool = p.getMainHandItem();
		Item toolItem = tool.getItem();
		boolean hadTool = !tool.isEmpty();
		int broke = 0;
		VEINING.add(p.getUUID());
		try {
			for (BlockPos pos : found) {
				if (hadTool && (p.getMainHandItem().isEmpty() || p.getMainHandItem().getItem() != toolItem)) {
					break; // the tool broke
				}
				if (p.gameMode.destroyBlock(pos)) {
					broke++;
				}
			}
		} finally {
			VEINING.remove(p.getUUID());
		}
		if (broke > 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 10, 0.4, 0.4, 0.4, 0.15);
		}
		return broke;
	}
}
