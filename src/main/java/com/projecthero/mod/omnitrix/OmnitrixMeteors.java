package com.projecthero.mod.omnitrix;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.omnitrix.entity.OmnitrixMeteorEntity;
import com.projecthero.mod.omnitrix.entity.OmnitrixPodEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: how the Omnitrix reaches the world -- modelled on {@code CarnageSpawner}.
 * <ul>
 *   <li>(a) a very rare natural meteor: once a minute each surface player in the Overworld (never Peaceful, never creative /
 *       spectator) rolls {@link OmnitrixConfig#NATURAL_METEOR_CHANCE_PER_MINUTE}; after any Omnitrix meteor there is a
 *       {@link OmnitrixConfig#NATURAL_METEOR_COOLDOWN_MINUTES}-minute lull;</li>
 *   <li>(b) the Meteor Scanner ({@link #scannerDrop}): within {@link OmnitrixConfig#SCANNER_RADIUS} blocks, on the surface.</li>
 * </ul>
 * With the {@code projectheroUniquePowers} gamerule on, neither happens while an Omnitrix already exists ({@link OmnitrixWorld}).
 * The cooldown map holds only a game time per dimension and is cleared by {@code ServerStateReset}.
 */
public final class OmnitrixMeteors {
	private static final Map<ResourceKey<Level>, Long> COOLDOWN_UNTIL = new HashMap<>();

	private OmnitrixMeteors() {
	}

	public static void clearSessionState() {
		COOLDOWN_UNTIL.clear();
	}

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % 1200 != 900 || !OmnitrixFeature.enabled()) {
			return;
		}
		ServerLevel level = server.overworld();
		if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL || OmnitrixWorld.oneExists(server)) {
			return;
		}
		Long cd = COOLDOWN_UNTIL.get(level.dimension());
		if (cd != null && level.getGameTime() < cd) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.isCreative() || !level.canSeeSky(p.blockPosition().above())) {
				continue;
			}
			if (level.random.nextDouble() < OmnitrixConfig.NATURAL_METEOR_CHANCE_PER_MINUTE && dropNear(level, p, 24, 48) != null) {
				com.projecthero.mod.ProjectHeroMod.LOGGER.info("[Omnitrix] A meteor is falling near {}", p.getName().getString());
				return;
			}
		}
	}

	/**
	 * The Meteor Scanner: drops the meteor within {@link OmnitrixConfig#SCANNER_RADIUS} blocks of {@code p}, on the surface.
	 * Null (and the reason on the action bar) if an Omnitrix pod or meteor is already active nearby, an Omnitrix already exists
	 * under the unique-powers rule, or no open ground was found.
	 */
	@Nullable
	public static Vec3 scannerDrop(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		if (activeNear(level, p.position(), OmnitrixConfig.SCANNER_BLOCK_RADIUS)) {
			p.displayClientMessage(Component.translatable("message.projecthero.meteor_scanner.already").withStyle(ChatFormatting.YELLOW), true);
			return null;
		}
		if (OmnitrixWorld.oneExists(p.server)) {
			p.displayClientMessage(Component.translatable("message.projecthero.meteor_scanner.unique").withStyle(ChatFormatting.YELLOW), true);
			return null;
		}
		Vec3 at = dropNear(level, p, 10, OmnitrixConfig.SCANNER_RADIUS - 2);
		if (at == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.meteor_scanner.no_ground").withStyle(ChatFormatting.YELLOW), true);
		}
		return at;
	}

	/** Any Omnitrix meteor or pod within {@code radius} of {@code at}. */
	public static boolean activeNear(ServerLevel level, Vec3 at, double radius) {
		AABB box = new AABB(at, at).inflate(radius, 400, radius);
		return !level.getEntitiesOfClass(OmnitrixMeteorEntity.class, box).isEmpty()
				|| !level.getEntitiesOfClass(OmnitrixPodEntity.class, box, pod -> !pod.empty()).isEmpty();
	}

	/** Sends a meteor down {@code min}-{@code max} blocks from {@code player} onto open, dry, sky-lit ground. Null if none. */
	@Nullable
	public static Vec3 dropNear(ServerLevel level, ServerPlayer player, int min, int max) {
		for (int attempt = 0; attempt < 24; attempt++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double d = min + level.random.nextDouble() * (max - min);
			int x = Mth.floor(player.getX() + Math.cos(a) * d);
			int z = Mth.floor(player.getZ() + Math.sin(a) * d);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (top.getY() <= level.getMinBuildHeight() + 1 || !level.getFluidState(top.below()).isEmpty()
					|| !level.canSeeSky(top) || Math.abs(top.getY() - player.getY()) > 24) {
				continue;
			}
			Vec3 impact = Vec3.atBottomCenterOf(top);
			if (OmnitrixMeteorEntity.launch(level, impact) != null) {
				OmnitrixWorld.markLoose(level.getServer());
				COOLDOWN_UNTIL.put(level.dimension(), level.getGameTime() + OmnitrixConfig.NATURAL_METEOR_COOLDOWN_MINUTES * 1200L);
				for (ServerPlayer p : level.players()) {
					if (p.distanceToSqr(x, top.getY(), z) < 160 * 160) {
						p.displayClientMessage(Component.translatable("event.projecthero.omnitrix.meteor").withStyle(ChatFormatting.GREEN), true);
					}
				}
				return impact;
			}
		}
		return null;
	}
}
