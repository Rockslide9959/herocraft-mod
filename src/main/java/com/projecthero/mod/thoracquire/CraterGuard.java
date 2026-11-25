package com.projecthero.mod.thoracquire;

import java.util.List;

import com.projecthero.mod.worldgen.CraterAmbience;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7 Mjolnir's Crater, server side: while a crater's hammer is still in it ({@link CraterAmbience#activeCraters}), the
 * crater can't be dug, blown up or built over -- a player breaking or placing a block inside it gets a message instead, and an
 * explosion just skips its blocks ({@code ThorAcquireExplosionMixin}). Creative players may still edit it. Once the hammer is
 * lifted the crater is ordinary ground again.
 *
 * <p>Also sends each player the craters near them every {@link #CLOUD_INTERVAL} ticks ({@link CraterCloudPayload}) so the
 * client can draw the storm cloud. No state of its own -- everything comes from {@code CraterAmbience}'s active map, which
 * {@code ServerStateReset} already clears.
 */
public final class CraterGuard {
	/** Horizontal protection radius around the hammer spot (the bowl is about 9 blocks in radius). */
	public static final int RADIUS = 11;
	public static final int BELOW = 9;
	public static final int ABOVE = 16;
	public static final int CLOUD_INTERVAL = 40;
	/** Craters within this many blocks of a player are sent for the cloud. */
	public static final double CLOUD_RANGE = 384.0;

	private CraterGuard() {
	}

	public static void initialize() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (level instanceof ServerLevel server && !player.getAbilities().instabuild && isProtected(server, pos)) {
				refuse(player);
				return false;
			}
			return true;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel server) || player.getAbilities().instabuild) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (!(held.getItem() instanceof BlockItem) && !(held.getItem() instanceof BucketItem)) {
				return InteractionResult.PASS;
			}
			BlockPos at = hit.getBlockPos().relative(hit.getDirection());
			if (isProtected(server, at) || isProtected(server, hit.getBlockPos())) {
				refuse(player);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(CraterGuard::tick);
	}

	public static boolean anyActive(ServerLevel level) {
		return CraterAmbience.anyActive(level);
	}

	/** Whether {@code pos} is inside a crater whose hammer is still there. */
	public static boolean isProtected(ServerLevel level, BlockPos pos) {
		for (BlockPos c : CraterAmbience.activeCraters(level)) {
			if (inside(c, pos)) {
				return true;
			}
		}
		return false;
	}

	public static boolean inside(BlockPos center, BlockPos pos) {
		int dx = pos.getX() - center.getX();
		int dz = pos.getZ() - center.getZ();
		int dy = pos.getY() - center.getY();
		return dx * dx + dz * dz <= RADIUS * RADIUS && dy >= -BELOW && dy <= ABOVE;
	}

	private static void refuse(Player player) {
		player.displayClientMessage(Component.translatable("message.projecthero.crater.protected").withStyle(ChatFormatting.GOLD), true);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % (TrialOfAsgard.anyRunning() ? 10 : CLOUD_INTERVAL) != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			List<BlockPos> craters = CraterAmbience.activeCraters(level);
			List<BlockPos> trials = TrialOfAsgard.centers(level);
			if (craters.isEmpty() && trials.isEmpty()) {
				continue;
			}
			for (ServerPlayer p : level.players()) {
				List<BlockPos> near = craters.stream()
						.filter(c -> p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) <= CLOUD_RANGE * CLOUD_RANGE)
						.limit(64).toList();
				List<BlockPos> nearTrials = trials.stream()
						.filter(c -> p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) <= CLOUD_RANGE * CLOUD_RANGE).limit(16).toList();
				if ((!near.isEmpty() || !nearTrials.isEmpty()) && ServerPlayNetworking.canSend(p, CraterCloudPayload.TYPE)) {
					ServerPlayNetworking.send(p, new CraterCloudPayload(near, nearTrials));
				}
			}
		}
	}
}
