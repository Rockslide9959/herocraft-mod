package com.projecthero.mod.cyborg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.cyborg.data.CyborgState;
import com.projecthero.mod.cyborg.network.CyborgBoomActionPayload;
import com.projecthero.mod.cyborg.network.CyborgBoomScreenPayload;
import com.projecthero.mod.darkseid.entity.BoomTubeEntity;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.stormbreaker.Bifrost;
import com.projecthero.mod.stormbreaker.BifrostWaypoints;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 (user: "make Shift+V open up a boom tube menu that works the same as the bifrost"): Cyborg's Boom Tube menu.
 *
 * <p>Shift+V opens it ({@link CyborgBoomScreenPayload}; the client draws it with the Bifrost's screen in Mother Box colours):
 * type X / Y / Z and open a Boom Tube there in the world he is in, or use one of three saved waypoints -- and unlike the
 * Bifrost a Boom Tube crosses worlds, so a waypoint in the Nether or the End works from anywhere. The server re-checks
 * everything ({@link #travelTo}): he still has the power, the cooldown ({@link CyborgConfig#BOOM_COOLDOWN 60 s}), the
 * Power Core ({@link CyborgConfig#BOOM_COST 10}), the world border and build height, and a safe landing (the Bifrost's
 * {@link Bifrost#findSafeLanding}) -- a failure costs nothing. Squadmates within {@link CyborgConfig#BOOM_SQUAD_RADIUS}
 * blocks come too (one can sneak to stay behind); a Boom Tube opens at both ends.
 */
public final class CyborgBoomTube {
	public enum Result {
		OK, NO_POWER, COOLDOWN, LOW_CORE, OUT_OF_BOUNDS, NO_LANDING, EMPTY_WAYPOINT, LOST_DIMENSION;

		public String messageKey() {
			return "message.projecthero.cyborg.boom." + name().toLowerCase(Locale.ROOT);
		}
	}

	private CyborgBoomTube() {
	}

	public static BifrostWaypoints waypoints(ServerPlayer p) {
		return p.getAttachedOrElse(ModAttachments.CYBORG_BOOM_WAYPOINTS, BifrostWaypoints.EMPTY);
	}

	/** Shift+V: opens the menu (nothing is spent until a Boom Tube actually opens). */
	public static void openMenu(ServerPlayer p) {
		if (!Cyborg.hasPower(p) || !p.isAlive() || p.isSpectator()) {
			return;
		}
		sendScreen(p, true);
	}

	public static void sendScreen(ServerPlayer p, boolean open) {
		if (!ServerPlayNetworking.canSend(p, CyborgBoomScreenPayload.TYPE)) {
			return; // gametest mock players
		}
		ServerPlayNetworking.send(p, new CyborgBoomScreenPayload(open, waypoints(p), Cyborg.cooldownRemaining(p, CyborgAbilities.BOOM),
				p.level().dimension().location().toString()));
	}

	public static boolean saveWaypoint(ServerPlayer p, int slot, String name) {
		if (slot < 0 || slot >= BifrostWaypoints.SLOTS) {
			return false;
		}
		BlockPos at = p.blockPosition();
		p.setAttached(ModAttachments.CYBORG_BOOM_WAYPOINTS, waypoints(p).with(slot, new BifrostWaypoints.Waypoint(
				BifrostWaypoints.cleanName(name, slot), at.getX(), at.getY(), at.getZ(), p.level().dimension().location().toString())));
		return true;
	}

	/** Server side of every menu button; ignores anyone without the power. */
	public static void handleAction(ServerPlayer p, CyborgBoomActionPayload payload) {
		if (p == null || !Cyborg.hasPower(p)) {
			return;
		}
		switch (payload.action()) {
			case CyborgBoomActionPayload.SAVE -> {
				if (saveWaypoint(p, payload.slot(), payload.name())) {
					p.displayClientMessage(Component.translatable("message.projecthero.cyborg.boom.saved", waypoints(p).get(payload.slot()).name())
							.withStyle(ChatFormatting.AQUA), true);
				}
				sendScreen(p, false);
			}
			case CyborgBoomActionPayload.CLEAR -> {
				p.setAttached(ModAttachments.CYBORG_BOOM_WAYPOINTS, waypoints(p).with(payload.slot(), BifrostWaypoints.Waypoint.EMPTY));
				sendScreen(p, false);
			}
			case CyborgBoomActionPayload.TRAVEL_COORDS -> report(p, travelTo(p, p.serverLevel(), payload.x(), payload.y(), payload.z()));
			case CyborgBoomActionPayload.TRAVEL_WAYPOINT -> report(p, travelToWaypoint(p, payload.slot()));
			default -> {
			}
		}
	}

	private static void report(ServerPlayer p, Result r) {
		if (r == Result.OK) {
			return;
		}
		Component msg = r == Result.COOLDOWN
				? Component.translatable(r.messageKey(), (Cyborg.cooldownRemaining(p, CyborgAbilities.BOOM) + 19) / 20)
				: Component.translatable(r.messageKey());
		p.displayClientMessage(msg.copy().withStyle(ChatFormatting.GRAY), true);
		p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.4f);
		sendScreen(p, false);
	}

	public static Result travelToWaypoint(ServerPlayer p, int slot) {
		BifrostWaypoints.Waypoint w = waypoints(p).get(slot);
		if (w.isEmpty()) {
			return Result.EMPTY_WAYPOINT;
		}
		ResourceLocation dim = ResourceLocation.tryParse(w.dimension());
		ServerLevel level = dim == null ? null : p.server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
		if (level == null) {
			return Result.LOST_DIMENSION;
		}
		return travelTo(p, level, w.x(), w.y(), w.z());
	}

	/** Validates and, if everything checks out, opens a Boom Tube to {@code x y z} in {@code dest}. */
	public static Result travelTo(ServerPlayer p, ServerLevel dest, int x, int y, int z) {
		if (!Cyborg.hasPower(p) || !p.isAlive() || p.isSpectator() || Cyborg.changing(p)) {
			return Result.NO_POWER;
		}
		if (Cyborg.cooldownRemaining(p, CyborgAbilities.BOOM) > 0) {
			return Result.COOLDOWN;
		}
		if (Cyborg.core(p) + 1.0e-3f < CyborgConfig.BOOM_COST) {
			return Result.LOW_CORE;
		}
		if (!inBounds(dest, x, y, z)) {
			return Result.OUT_OF_BOUNDS;
		}
		loadChunk(dest, x, z, p);
		BlockPos landing = Bifrost.findSafeLanding(dest, x, y, z);
		if (landing == null) {
			return Result.NO_LANDING;
		}
		Cyborg.dropHologram(p);
		Cyborg.spendCore(p, CyborgConfig.BOOM_COST);
		CyborgAbilities.cooldown(p, CyborgAbilities.BOOM, CyborgConfig.BOOM_COOLDOWN);
		Cyborg.setAnim(p, CyborgState.ANIM_BOOM_TUBE);
		ServerLevel from = p.serverLevel();
		Vec3 start = p.position();
		Vec3 to = Vec3.atBottomCenterOf(landing);
		List<ServerPlayer> riders = new ArrayList<>();
		for (ServerPlayer o : from.players()) {
			if (o != p && o.isAlive() && !o.isSpectator() && !o.isShiftKeyDown() && o.distanceToSqr(p) <= CyborgConfig.BOOM_SQUAD_RADIUS
					* CyborgConfig.BOOM_SQUAD_RADIUS && Squads.areAllies(p, o)) {
				riders.add(o);
			}
		}
		BoomTubeEntity.open(from, start.add(0, 1, 0), BoomTubeEntity.Kind.MELEE, 2.6f, 30, null);
		BoomTubeEntity.open(dest, to.add(0, 1, 0), BoomTubeEntity.Kind.MELEE, 2.6f, 30, null);
		from.playSound(null, start.x, start.y, start.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 0.6f);
		send(p, dest, to);
		int i = 1;
		for (ServerPlayer r : riders) {
			// a little ring round him, each on their own safe spot if there is one close by
			double a = i * 1.3;
			int ax = Mth.floor(to.x + Math.cos(a) * 1.2);
			int az = Mth.floor(to.z + Math.sin(a) * 1.2);
			BlockPos own = inBounds(dest, ax, landing.getY(), az) ? Bifrost.findSafeLanding(dest, ax, landing.getY(), az) : null;
			Vec3 spot = own != null && Math.abs(own.getY() - landing.getY()) <= 3 ? Vec3.atBottomCenterOf(own) : to;
			send(r, dest, spot);
			r.displayClientMessage(Component.translatable("message.projecthero.cyborg.boom.carried", p.getDisplayName())
					.withStyle(ChatFormatting.AQUA), true);
			i++;
		}
		dest.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.2f, 0.6f);
		return Result.OK;
	}

	private static void send(ServerPlayer r, ServerLevel dest, Vec3 to) {
		if (r.isPassenger()) {
			r.stopRiding();
		}
		r.teleportTo(dest, to.x, to.y, to.z, Set.of(), r.getYRot(), r.getXRot());
		r.setDeltaMovement(Vec3.ZERO);
		r.hurtMarked = true;
		r.resetFallDistance();
	}

	static boolean inBounds(Level level, int x, int y, int z) {
		return y >= level.getMinBuildHeight() && y < level.getMaxBuildHeight() && level.getWorldBorder().isWithinBounds(new BlockPos(x, y, z));
	}

	private static void loadChunk(ServerLevel level, int x, int z, ServerPlayer holder) {
		ChunkPos chunk = new ChunkPos(new BlockPos(x, 0, z));
		level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunk, 1, holder.getId());
		level.getChunk(chunk.x, chunk.z);
	}
}
