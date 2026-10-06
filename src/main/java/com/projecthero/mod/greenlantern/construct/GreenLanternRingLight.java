package com.projecthero.mod.greenlantern.construct;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

/**
 * v0.15.9, explicit user request ("change the lantern light construct, make it spawn light from the ring and give the
 * player night vision"): the Lantern Light construct no longer places an orb out in the world -- while it is on, the
 * light comes from the caster's ring and goes wherever they go.
 *
 * <ul>
 *   <li><b>Light that follows</b>: one invisible vanilla {@code minecraft:light} block (level 15) sits in the open air
 *   cell at the caster's head (or feet, if the head is in something), moved whenever they move -- the new one placed,
 *   the old one taken away. It only ever goes into air (or a water source, waterlogged), never over a real block, and
 *   it is only ever removed if it is still our light.</li>
 *   <li><b>Night Vision</b> for the caster: ambient, no particles, topped back up to {@value #NIGHT_VISION_TICKS} ticks
 *   whenever it drops under {@value #NIGHT_VISION_REFRESH_BELOW} (never into vanilla's last-10-seconds flicker), and
 *   taken away when the light goes out -- only if it is still ours (a potion's longer / non-ambient one is left).</li>
 *   <li>A soft green glow of particles at the ring hand.</li>
 * </ul>
 * Toggled by the construct key like before (a second press turns it off -- see
 * {@code GreenLanternConstructs#deploy}); it keeps its 1-charge cast and 0.2/s upkeep, and ends with every other
 * construct on N, death, logout, dimension change or running dry. {@link #LIGHTS} is cleared by
 * {@code ServerStateReset} (via {@link GreenLanternConstructs#clearSessionState}) and every light is taken down on
 * {@code SERVER_STOPPING}, so none is ever saved into the world.
 */
public final class GreenLanternRingLight {
	public static final int NIGHT_VISION_TICKS = 15 * 20;
	public static final int NIGHT_VISION_REFRESH_BELOW = 12 * 20;

	private static final DustParticleOptions RING_GLOW = new DustParticleOptions(new Vector3f(0.30f, 1.0f, 0.45f), 0.7f);

	private record Placed(ResourceKey<Level> dimension, BlockPos pos, boolean waterlogged) {
	}

	/** caster -> the light block currently standing in for their ring's glow (absent = none placed right now). */
	private static final Map<UUID, Placed> LIGHTS = new ConcurrentHashMap<>();

	private GreenLanternRingLight() {
	}

	static void initialize() {
		ServerLifecycleEvents.SERVER_STOPPING.register(GreenLanternRingLight::removeAll);
	}

	public static void clearSessionState() {
		LIGHTS.clear();
	}

	/** The light goes on: night vision and the first light block right away. */
	static void start(ServerPlayer player) {
		refreshNightVision(player);
		follow(player);
	}

	/** Every tick while on. */
	static void tick(ServerPlayer player) {
		refreshNightVision(player);
		follow(player);
		if (player.tickCount % 3 == 0) {
			var hand = AbilityHelpers.handPosition(player);
			player.serverLevel().sendParticles(RING_GLOW, hand.x, hand.y, hand.z, 1, 0.06, 0.06, 0.06, 0.0);
		}
	}

	/** The light goes out (toggle off, N, death, logout, dimension change, out of charge). {@code player} may be null. */
	static void stop(UUID owner, MinecraftServer server, ServerPlayer player) {
		Placed placed = LIGHTS.remove(owner);
		if (placed != null && server != null) {
			remove(server.getLevel(placed.dimension()), placed);
		}
		if (player != null) {
			MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
			if (nv != null && nv.isAmbient() && nv.getDuration() <= NIGHT_VISION_TICKS) {
				player.removeEffect(MobEffects.NIGHT_VISION);
			}
		}
	}

	/** Where the caster's light currently is, or null (gametests). */
	public static BlockPos lightPos(UUID owner) {
		Placed p = LIGHTS.get(owner);
		return p == null ? null : p.pos();
	}

	private static void refreshNightVision(ServerPlayer player) {
		MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
		if (nv == null || (nv.getDuration() < NIGHT_VISION_REFRESH_BELOW && !nv.isInfiniteDuration())) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, true));
		}
	}

	private static void follow(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		BlockPos head = BlockPos.containing(player.getEyePosition());
		BlockPos feet = player.blockPosition();
		Placed current = LIGHTS.get(player.getUUID());
		boolean sameLevel = current != null && current.dimension() == level.dimension();
		// still standing in our light? nothing to do
		if (sameLevel && (current.pos().equals(head) || current.pos().equals(feet)) && isOurs(level, current)) {
			return;
		}
		BlockPos target = null;
		boolean water = false;
		for (BlockPos cand : new BlockPos[] { head, feet }) {
			if (sameLevel && cand.equals(current.pos())) {
				target = cand; // the old light is still a valid spot (re-placed below if something took it)
				water = current.waterlogged();
				break;
			}
			BlockState s = level.getBlockState(cand);
			if (s.isAir()) {
				target = cand;
				break;
			}
			if (s.is(Blocks.WATER) && s.getFluidState().isSource()) {
				target = cand;
				water = true;
				break;
			}
		}
		if (current != null && (target == null || !current.pos().equals(target) || !sameLevel)) {
			remove(sameLevel ? level : level.getServer().getLevel(current.dimension()), current);
			LIGHTS.remove(player.getUUID());
		}
		if (target == null) {
			return; // head and feet both inside something solid: no light this tick
		}
		Placed placed = new Placed(level.dimension(), target.immutable(), water);
		if (!isOurs(level, placed)) {
			BlockState here = level.getBlockState(target);
			if (!here.isAir() && !(here.is(Blocks.WATER) && here.getFluidState().isSource())) {
				return;
			}
			level.setBlock(target, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15)
					.setValue(BlockStateProperties.WATERLOGGED, water), Block.UPDATE_ALL);
		}
		LIGHTS.put(player.getUUID(), placed);
	}

	private static boolean isOurs(ServerLevel level, Placed placed) {
		return level != null && level.isLoaded(placed.pos()) && level.getBlockState(placed.pos()).is(Blocks.LIGHT);
	}

	private static void remove(ServerLevel level, Placed placed) {
		if (!isOurs(level, placed)) {
			return;
		}
		BlockState back = placed.waterlogged() ? Fluids.WATER.defaultFluidState().createLegacyBlock() : Blocks.AIR.defaultBlockState();
		level.setBlock(placed.pos(), back, Block.UPDATE_ALL);
	}

	private static void removeAll(MinecraftServer server) {
		for (Placed placed : LIGHTS.values()) {
			remove(server.getLevel(placed.dimension()), placed);
		}
		LIGHTS.clear();
	}
}
