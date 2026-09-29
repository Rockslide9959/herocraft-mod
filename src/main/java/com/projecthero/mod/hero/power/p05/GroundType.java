package com.projecthero.mod.hero.power.p05;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 Geokinesis signature: <b>the ground under you is your ammo.</b> Every rock you throw, spike you raise and
 * quake you start is made of whatever you are standing on, and the material changes what it does:
 *
 * <table>
 *   <tr><th>ground</th><th>damage</th><th>cooldowns</th><th>on hit</th></tr>
 *   <tr><td>stone / dirt (default)</td><td>x1.0</td><td>x1.0</td><td>-</td></tr>
 *   <tr><td>deepslate, basalt, blackstone, obsidian</td><td>x1.35</td><td>x1.25</td><td>+50% knockback</td></tr>
 *   <tr><td>sand, gravel, sandstone</td><td>x0.85</td><td>x0.9</td><td>blinding dust cloud (Blindness 3 s)</td></tr>
 *   <tr><td>netherrack, magma, soul sand, nylium</td><td>x1.0</td><td>x1.0</td><td>sets the target alight (5 s)</td></tr>
 *   <tr><td>any ore, raw-ore / amethyst blocks</td><td>x1.45</td><td>x1.0</td><td>glittering shrapnel</td></tr>
 *   <tr><td>ice, snow, packed / blue ice</td><td>x1.0</td><td>x1.0</td><td>Slowness II 4 s</td></tr>
 *   <tr><td>nothing earthy within 3 blocks</td><td>x0.75</td><td>x1.0</td><td>-</td></tr>
 * </table>
 */
public enum GroundType {
	NONE("none", 0.75f, 1.0f),
	STONE("stone", 1.0f, 1.0f),
	DEEPSLATE("deepslate", 1.35f, 1.25f),
	SAND("sand", 0.85f, 0.9f),
	NETHER("nether", 1.0f, 1.0f),
	ORE("ore", 1.45f, 1.0f),
	FROST("frost", 1.0f, 1.0f);

	/** The sampled ground: its type and the actual block (for particles, the thrown rock and conjured walls). */
	public record Ground(GroundType type, BlockState state) {
		public BlockParticleOption dust() {
			return new BlockParticleOption(ParticleTypes.BLOCK, state);
		}

		public float damage(float base) {
			return base * type.damageMult;
		}

		public int cooldown(int baseTicks) {
			return Math.max(1, Math.round(baseTicks * type.cooldownMult));
		}
	}

	public final String id;
	public final float damageMult;
	public final float cooldownMult;

	GroundType(String id, float damageMult, float cooldownMult) {
		this.id = id;
		this.damageMult = damageMult;
		this.cooldownMult = cooldownMult;
	}

	/** The resource name used to show the current ground on the HUD ({@code ground_<id>}). */
	public String resource() {
		return "ground_" + id;
	}

	public static GroundType of(BlockState st) {
		if (st.isAir() || !st.getFluidState().isEmpty() && !st.isSolid()) {
			return NONE;
		}
		if (isOre(st) || st.is(Blocks.AMETHYST_BLOCK) || st.is(Blocks.BUDDING_AMETHYST) || st.is(Blocks.RAW_IRON_BLOCK)
				|| st.is(Blocks.RAW_GOLD_BLOCK) || st.is(Blocks.RAW_COPPER_BLOCK)) {
			return ORE;
		}
		if (st.is(Blocks.DEEPSLATE) || st.is(Blocks.COBBLED_DEEPSLATE) || st.is(Blocks.POLISHED_DEEPSLATE)
				|| st.is(Blocks.DEEPSLATE_BRICKS) || st.is(Blocks.DEEPSLATE_TILES) || st.is(Blocks.CHISELED_DEEPSLATE)
				|| st.is(Blocks.REINFORCED_DEEPSLATE) || st.is(Blocks.BASALT) || st.is(Blocks.SMOOTH_BASALT)
				|| st.is(Blocks.POLISHED_BASALT) || st.is(Blocks.BLACKSTONE) || st.is(Blocks.POLISHED_BLACKSTONE)
				|| st.is(Blocks.OBSIDIAN) || st.is(Blocks.CRYING_OBSIDIAN) || st.is(Blocks.TUFF)) {
			return DEEPSLATE;
		}
		if (st.is(Blocks.NETHERRACK) || st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.SOUL_SAND) || st.is(Blocks.SOUL_SOIL)
				|| st.is(BlockTags.NYLIUM) || st.is(Blocks.NETHER_BRICKS) || st.is(Blocks.RED_NETHER_BRICKS)
				|| st.is(Blocks.GLOWSTONE)) {
			return NETHER;
		}
		if (st.is(BlockTags.SAND) || st.is(Blocks.GRAVEL) || st.is(Blocks.SANDSTONE) || st.is(Blocks.RED_SANDSTONE)
				|| st.is(Blocks.SMOOTH_SANDSTONE) || st.is(Blocks.CUT_SANDSTONE) || st.is(Blocks.SUSPICIOUS_GRAVEL)) {
			return SAND;
		}
		if (st.is(BlockTags.ICE) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.POWDER_SNOW) || st.is(Blocks.SNOW)) {
			return FROST;
		}
		if (GeoBareHands.isEarth(st) || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.DIRT)
				|| st.is(Blocks.COBBLESTONE) || st.is(Blocks.MOSSY_COBBLESTONE) || st.is(Blocks.STONE_BRICKS)
				|| st.is(Blocks.CALCITE) || st.is(Blocks.DRIPSTONE_BLOCK) || st.is(Blocks.MUD) || st.is(Blocks.CLAY)
				|| st.is(Blocks.TERRACOTTA) || st.is(BlockTags.TERRACOTTA)) {
			return STONE;
		}
		return NONE;
	}

	/** What the player is standing on (or hovering within 3 blocks of). */
	public static Ground under(ServerPlayer p) {
		return under((ServerLevel) p.level(), p.position());
	}

	public static Ground under(ServerLevel level, Vec3 at) {
		BlockPos feet = BlockPos.containing(at.x, at.y - 0.2, at.z);
		for (int dy = 0; dy <= 3; dy++) {
			BlockPos bp = feet.below(dy);
			BlockState st = level.getBlockState(bp);
			if (st.isAir() || st.canBeReplaced() && !st.is(Blocks.SNOW)) {
				continue;
			}
			GroundType t = of(st);
			if (t == NONE) {
				return new Ground(NONE, Blocks.STONE.defaultBlockState());
			}
			return new Ground(t, st);
		}
		return new Ground(NONE, Blocks.STONE.defaultBlockState());
	}

	/** The block a Stone Wall / pillar / slab is conjured from on this ground. */
	public BlockState buildBlock(BlockState ground) {
		return switch (this) {
			case NONE, STONE, ORE -> Blocks.STONE.defaultBlockState();
			case DEEPSLATE -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
			case SAND -> Blocks.SANDSTONE.defaultBlockState();
			case NETHER -> Blocks.NETHERRACK.defaultBlockState();
			case FROST -> Blocks.PACKED_ICE.defaultBlockState();
		};
	}

	/** Applies this ground's on-hit rider to {@code e} (the damage multiplier is applied by the caller). */
	public void onHit(ServerPlayer p, LivingEntity e, Vec3 from) {
		ServerLevel level = (ServerLevel) p.level();
		switch (this) {
			case DEEPSLATE -> AbilityHelpers.knockbackFrom(e, from, 0.7);
			case SAND -> {
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 60, 0);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
						e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 30, 0.7, 0.6, 0.7, 0.05);
			}
			case NETHER -> {
				e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 100));
				level.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(),
						10, 0.3, 0.4, 0.3, 0.02);
			}
			case ORE -> level.sendParticles(ParticleTypes.WAX_OFF, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
					12, 0.4, 0.4, 0.4, 0.4);
			case FROST -> AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 80, 1);
			default -> {
			}
		}
	}

	/** A sand ground's dust cloud also blinds everything near the impact point. */
	public void onImpact(ServerPlayer p, Vec3 at) {
		if (this != SAND) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
				at.x, at.y + 0.5, at.z, 50, 1.4, 0.8, 1.4, 0.03);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5, at.z, 6, 1.0, 0.4, 1.0, 0.01);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 2.5)) {
			AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 60, 0);
		}
	}

	static boolean isOre(BlockState state) {
		return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.GOLD_ORES)
				|| state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)
				|| state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.REDSTONE_ORES)
				|| state.is(Blocks.NETHER_GOLD_ORE) || state.is(Blocks.NETHER_QUARTZ_ORE)
				|| state.is(Blocks.ANCIENT_DEBRIS);
	}
}
