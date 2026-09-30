package com.projecthero.mod.client.moonknight;

import java.util.UUID;

import com.projecthero.mod.client.squad.SquadClient;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.network.SquadInfoPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

import org.joml.Vector3f;

/**
 * v0.14.4: shows the suited Moon Knight who his Grapple Kick (G) would hit before he presses it. Every other client
 * tick it runs the server's own selection ({@link MoonKnightAim#kickTarget}: the crosshair ray, else the best target
 * within 9 degrees of the crosshair, in sight, up to 100 blocks) and marks the pick with a small turning ring of
 * moonlight over its head (white while the kick is ready, grey while it recharges), which only he sees; the HUD adds
 * its name and distance under the crosshair ({@link #target}). Squad-mates (from the synced squad roster) and his own
 * pets are skipped, as the server skips them.
 */
public final class MoonKnightKickPreviewClient {
	private static final DustParticleOptions READY = new DustParticleOptions(new Vector3f(0.92f, 0.95f, 1.0f), 0.8f);
	private static final DustParticleOptions COOLING = new DustParticleOptions(new Vector3f(0.45f, 0.45f, 0.5f), 0.7f);

	private static int targetId = -1;

	private MoonKnightKickPreviewClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(MoonKnightKickPreviewClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> targetId = -1);
	}

	/** The previewed Grapple Kick target, or null (client-side). */
	public static LivingEntity target() {
		Minecraft mc = Minecraft.getInstance();
		if (targetId < 0 || mc.level == null) {
			return null;
		}
		Entity e = mc.level.getEntity(targetId);
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	private static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || !MoonKnight.isTransformed(p) || p.isSpectator()) {
			targetId = -1;
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		if (p.tickCount % 2 == 0) {
			LivingEntity t = MoonKnightAim.kickTarget(p, MoonKnightConfig.GRAPPLE_RANGE, e -> allowed(p, e));
			targetId = t == null ? -1 : t.getId();
		}
		LivingEntity t = target();
		if (t == null || p.tickCount % 2 != 0) {
			return;
		}
		boolean ready = MoonKnight.cooldownRemaining(p, "kick") <= 0;
		double r = Math.max(0.45, t.getBbWidth() * 0.7);
		double y = t.getY() + t.getBbHeight() + 0.45;
		double spin = p.tickCount * 0.18;
		for (int i = 0; i < 8; i++) {
			double a = spin + i * Math.PI / 4.0;
			mc.level.addAlwaysVisibleParticle(ready ? READY : COOLING, t.getX() + Math.cos(a) * r, y, t.getZ() + Math.sin(a) * r,
					0.0, 0.0, 0.0);
		}
	}

	/** Mirrors the server's {@code MoonKnightCombat.friendly} as far as the client can know it. */
	private static boolean allowed(LocalPlayer self, LivingEntity e) {
		if (e instanceof OwnableEntity own && self.getUUID().equals(own.getOwnerUUID())) {
			return false;
		}
		if (e instanceof Player other) {
			UUID id = other.getUUID();
			for (SquadInfoPayload.Member m : SquadClient.get().members()) {
				if (m.id().equals(id)) {
					return false;
				}
			}
		}
		return true;
	}
}
