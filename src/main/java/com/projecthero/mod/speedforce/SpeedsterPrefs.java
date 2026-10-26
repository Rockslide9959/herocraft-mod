package com.projecthero.mod.speedforce;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.speedforce.data.SpeedForceState;
import com.projecthero.mod.speedforce.network.SpeedsterPrefsPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;

/**
 * v0.17.1 (user): the speedsters' own settings on the server -- Ground-Stick (both speedsters) and the Speed Force
 * Speedster's lightning colour. Ground-Stick pulls a runner down onto the slope client-side, which the server used to count
 * as a fall ("the player takes fall damage sometimes, please remove that"): while it is on, a fall no longer than its 5-block
 * reach (plus vanilla's own 3 safe blocks) does no damage.
 */
public final class SpeedsterPrefs {
	/** Most fall distance a Ground-Stick pull can rack up: its 5-block reach, the 3 free blocks, a little slack. */
	public static final double STICK_SAFE_FALL = 5.0 + 3.0 + 1.0;

	/** Plain Speedsters (the Super Speed mutation) with Ground-Stick on. The Speed Force Speedster's lives in its state. */
	private static final Set<UUID> PLAIN_STICK = ConcurrentHashMap.newKeySet();

	private SpeedsterPrefs() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(SpeedsterPrefsPayload.TYPE, SpeedsterPrefsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpeedsterPrefsPayload.TYPE,
				(payload, context) -> context.server().execute(() -> apply(context.player(), payload)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PLAIN_STICK.remove(handler.getPlayer().getUUID()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p
				&& source.is(DamageTypeTags.IS_FALL) && groundStick(p) && p.fallDistance <= STICK_SAFE_FALL));
	}

	public static void clearSessionState() {
		PLAIN_STICK.clear();
	}

	public static void apply(ServerPlayer p, SpeedsterPrefsPayload msg) {
		if (msg.suitStyle() >= 0) {
			// v0.17.1 (user): the Shift+N menu's Suit row -- restyle the Flash Suit (worn, or in the worn / held ring)
			if (com.projecthero.mod.flash.FlashSuit.mayWear(p)) {
				com.projecthero.mod.flash.FlashSuitStyle.select(p, com.projecthero.mod.flash.FlashSuitStyle.byOrdinal(msg.suitStyle()),
						p.getMainHandItem().is(com.projecthero.mod.flash.FlashSuit.RING) ? p.getMainHandItem() : p.getOffhandItem());
			}
			return; // a style pick carries no other change
		}
		if (SpeedForceSpeedster.hasPower(p)) {
			SpeedForceState n = SpeedForceSpeedster.state(p).copy();
			n.groundStick = msg.groundStick();
			if (msg.colour() >= 0) {
				n.colour = SpeedForceColours.clamp(msg.colour());
			}
			SpeedForceSpeedster.save(p, n);
			return;
		}
		if (msg.groundStick()) {
			PLAIN_STICK.add(p.getUUID());
		} else {
			PLAIN_STICK.remove(p.getUUID());
		}
	}

	/** Ground-Stick is on for this speedster. */
	public static boolean groundStick(ServerPlayer p) {
		SpeedForceState s = SpeedForceSpeedster.peek(p);
		if (s != null && s.hasPower) {
			return s.groundStick;
		}
		if (!PLAIN_STICK.contains(p.getUUID())) {
			return false;
		}
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && SuperSpeedHandlers.KEY.equals(st.activePower);
	}
}
