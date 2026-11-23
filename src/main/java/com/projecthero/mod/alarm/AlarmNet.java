package com.projecthero.mod.alarm;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.alarm.network.AlarmStatusPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** v0.17.7: sends each player in a zone its {@link AlarmStatusPayload} (HUD eye, "?" / "!" marks, sniper lasers). */
final class AlarmNet {
	static final double MARK_RANGE = 48.0;

	private AlarmNet() {
	}

	static void sync(ServerLevel level, AlarmZones zs) {
		for (ServerPlayer p : level.players()) {
			AlarmZone z = zs.zoneAt(p.blockPosition());
			if (z == null) {
				if (zs.lastSynced.remove(p.getUUID()) != null && ServerPlayNetworking.canSend(p, AlarmStatusPayload.TYPE)) {
					ServerPlayNetworking.send(p, new AlarmStatusPayload(AlarmStatusPayload.NONE, List.of(), List.of()));
				}
				continue;
			}
			zs.lastSynced.put(p.getUUID(), z.id);
			if (!ServerPlayNetworking.canSend(p, AlarmStatusPayload.TYPE)) {
				continue;
			}
			List<Integer> marks = new ArrayList<>();
			List<Integer> lasers = new ArrayList<>();
			for (Mob g : z.guards) {
				if (!g.isAlive() || g.distanceToSqr(p) > MARK_RANGE * MARK_RANGE) {
					continue;
				}
				int mark = z.alerted.contains(g.getUUID()) ? 2 : z.suspicion(g) >= AlarmDirector.QUESTION ? 1 : 0;
				if (mark > 0 && marks.size() < 250) {
					marks.add(g.getId() * 4 + mark);
				}
				if (AlarmGuards.hasRole(g, GuardRole.SNIPER) && lasers.size() < 90) {
					Player t = AlarmRoles.laserTarget(g);
					if (t != null) {
						lasers.add(g.getId());
						lasers.add(t.getId());
						lasers.add(AlarmRoles.aimOf(z, g) >= AlarmRoles.SNIPER_BRIGHT ? 1 : 0);
					}
				}
			}
			ServerPlayNetworking.send(p, new AlarmStatusPayload(z.state.ordinal(), marks, lasers));
		}
	}
}
