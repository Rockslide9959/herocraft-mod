package com.projecthero.mod.greenlantern.bind;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternAbilityManager;
import com.projecthero.mod.greenlantern.GreenLanternArsenal;
import com.projecthero.mod.greenlantern.GreenLanternCombat;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternConstructAttacks;
import com.projecthero.mod.greenlantern.GreenLanternOath;
import com.projecthero.mod.greenlantern.GreenLanternScan;
import com.projecthero.mod.greenlantern.GreenLanternShield;
import com.projecthero.mod.greenlantern.GreenLanternSuit;
import com.projecthero.mod.greenlantern.GreenLanternVisuals;
import com.projecthero.mod.greenlantern.bind.GreenLanternBinding.Entry;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.network.GreenLanternMenuPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.1 Green Lantern revamp, server side of the Construct Menu ({@link GreenLanternBinding} is the catalogue):
 * <ul>
 *   <li>the twelve bindable keys -- G / X / Z / V arrive as ability slots, C through its tap-or-hold-for-the-menu
 *   gesture, H through {@link GreenLanternMenuPayload} -- fire whatever entry is on them;</li>
 *   <li>the menu's binding edits (bind, clear, reset) and "spawn once" (hold left click on an entry).</li>
 * </ul>
 * A hold entry's release goes to the entry the key started, even if Shift was let go first ({@link #HELD}). A hold
 * entry spawned from the menu lets go by itself after {@link GreenLanternConfig#MENU_SPAWN_HOLD_TICKS}.
 */
public final class GreenLanternBindings {
	/** Player -> base key ('G', 'H', ...) -> the hold entry that key's press started. */
	private static final Map<UUID, Map<Character, Entry>> HELD = new ConcurrentHashMap<>();
	/** Player -> a hold entry spawned from the menu, and the game time it lets go. */
	private static final Map<UUID, Map<String, Long>> MENU_RELEASE = new ConcurrentHashMap<>();

	private GreenLanternBindings() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(GreenLanternMenuPayload.TYPE, GreenLanternMenuPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GreenLanternMenuPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!GreenLanternAbilityManager.hasContext(p)) {
				return;
			}
			switch (payload.action()) {
				case GreenLanternMenuPayload.BIND -> bind(p, payload.key(), payload.entry());
				case GreenLanternMenuPayload.CLEAR -> clearEntry(p, payload.entry());
				case GreenLanternMenuPayload.RESET -> reset(p);
				case GreenLanternMenuPayload.SPAWN -> {
					if (!com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
						spawnOnce(p, payload.entry());
					}
				}
				case GreenLanternMenuPayload.KEY_H -> {
					if (!com.projecthero.mod.hero.PowerSuppression.isSuppressed(p) || !payload.pressed()) {
						key(p, 'H', payload.key().startsWith("S"), payload.pressed());
					}
				}
				default -> {
				}
			}
		});
	}

	public static void clearSessionState() {
		HELD.clear();
		MENU_RELEASE.clear();
	}

	public static void onCleanup(UUID id) {
		HELD.remove(id);
		MENU_RELEASE.remove(id);
	}

	// ---------------- keys ----------------

	/** A bindable key went down or up. {@code shift} only matters on the press. */
	public static void key(ServerPlayer p, char base, boolean shift, boolean pressed) {
		// v0.17.1: inside the Hard-Light Mech the keys drive the mech (H does nothing)
		if (pressed && com.projecthero.mod.greenlantern.mech.GreenLanternMech.isPiloting(p)) {
			return;
		}
		Map<Character, Entry> held = HELD.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>());
		if (!pressed) {
			Entry e = held.remove(base);
			if (e != null) {
				release(p, e);
			}
			return;
		}
		Entry e = GreenLanternBinding.resolve(GreenLantern.state(p), GreenLanternBinding.key(base, shift));
		if (e == null && shift) {
			// v0.17.1: an empty Shift key under a Drone / Gateway key is that construct's second action
			Entry plain = GreenLanternBinding.resolve(GreenLantern.state(p), GreenLanternBinding.key(base, false));
			if (GreenLanternBinding.isPair(plain)) {
				com.projecthero.mod.greenlantern.recon.GreenLanternRecon.press(p, plain.type(), true);
			}
			return;
		}
		if (e == null) {
			return;
		}
		if (GreenLanternBinding.isPair(e)) {
			// v0.17.1: the key = its main action, Shift + the key = its second one -- while both keys hold it (a Drone left
			// on a Shift key alone does its main action there)
			boolean second = shift && e.equals(GreenLanternBinding.resolve(GreenLantern.state(p), GreenLanternBinding.key(base, false)));
			com.projecthero.mod.greenlantern.recon.GreenLanternRecon.press(p, e.type(), second);
			return;
		}
		if (e.hold()) {
			Entry was = held.put(base, e);
			if (was != null && was != e) {
				release(p, was);
			}
		}
		press(p, e);
	}

	/** Plain C was tapped (released before the menu opened): fire the C entry -- always a tap entry. */
	public static void tapC(ServerPlayer p) {
		Entry e = GreenLanternBinding.resolve(GreenLantern.state(p), "C");
		if (e != null && !e.hold()) {
			press(p, e);
		}
	}

	// ---------------- what each entry does ----------------

	public static void press(ServerPlayer p, Entry e) {
		if (e.type() != null) {
			if (e.type().isHold()) {
				GreenLanternArsenal.holdStart(p, e.type());
			} else {
				GreenLanternConstructs.deploy(p, e.type());
			}
			return;
		}
		switch (e.id()) {
			case GreenLanternBinding.RING_BOLT -> GreenLanternCombat.ringBolt(p);
			case GreenLanternBinding.BEAM -> GreenLanternCombat.beamStart(p);
			case GreenLanternBinding.BLAST_WAVE -> GreenLanternCombat.blastWave(p);
			case GreenLanternBinding.OATH -> GreenLanternOath.onPress(p);
			case GreenLanternBinding.SUIT -> GreenLanternSuit.toggle(p);
			case GreenLanternBinding.SCAN -> {
				GreenLanternScan.scan(p);
				GreenLanternVisuals.anim(p, GreenLanternFx.ANIM_SCAN);
			}
			case GreenLanternBinding.QUICK_DEPLOY -> GreenLanternConstructs.deploy(p, GreenLantern.state(p).selectedConstructType());
			case GreenLanternBinding.FIST -> GreenLanternCombat.constructFist(p);
			case GreenLanternBinding.HAMMER -> GreenLanternCombat.warHammerSlam(p);
			case GreenLanternBinding.GATLING -> GreenLanternConstructAttacks.gatlingStart(p);
			case GreenLanternBinding.MISSILES -> GreenLanternConstructAttacks.missileBarrage(p);
			case GreenLanternBinding.GIANT_HAND -> GreenLanternConstructAttacks.giantHand(p);
			case GreenLanternBinding.SHIELD -> GreenLanternShield.startShield(p);
			case GreenLanternBinding.DOME -> {
				// a second press drops a dome that is already up (v0.11.2)
				if (GreenLanternShield.isActive(p) && GreenLanternShield.isDome(p)) {
					GreenLanternShield.dismissDomeVoluntarily(p);
				} else {
					GreenLanternShield.deployDome(p);
					if (GreenLanternShield.isActive(p) && GreenLanternShield.isDome(p)) {
						GreenLanternVisuals.anim(p, GreenLanternFx.ANIM_DOME);
					}
				}
			}
			default -> {
			}
		}
	}

	public static void release(ServerPlayer p, Entry e) {
		if (e.type() != null) {
			if (e.type().isHold()) {
				GreenLanternArsenal.holdRelease(p, e.type());
			}
			return;
		}
		switch (e.id()) {
			case GreenLanternBinding.BEAM -> {
				if (GreenLanternCombat.isChannellingBeam(p)) {
					GreenLanternCombat.beamStop(p);
				}
			}
			case GreenLanternBinding.OATH -> GreenLanternOath.onRelease(p);
			case GreenLanternBinding.GATLING -> GreenLanternConstructAttacks.gatlingStop(p, true);
			case GreenLanternBinding.SHIELD -> GreenLanternShield.stopShield(p);
			default -> {
			}
		}
	}

	// ---------------- the menu ----------------

	/** Hover an entry and press a key: the key now holds it (whatever held it before loses it). */
	public static void bind(ServerPlayer p, String key, String entryId) {
		Entry e = GreenLanternBinding.byId(entryId);
		if (e == null || !GreenLanternBinding.KEYS.contains(key)) {
			return;
		}
		String refused = GreenLanternBinding.refusalKey(key, e);
		if (refused != null) {
			p.displayClientMessage(Component.translatable(refused), true);
			return;
		}
		GreenLanternState c = GreenLantern.state(p).copy();
		c.bindings.put(key, e.id());
		if (GreenLanternBinding.isPair(e)) {
			c.bindings.put(GreenLanternBinding.twin(key), e.id()); // v0.17.1: the Drone / Gateway takes the Shift twin too
		}
		GreenLantern.save(p, c);
	}

	/** Right-click an entry: every key holding it is cleared. */
	public static void clearEntry(ServerPlayer p, String entryId) {
		GreenLanternState c = GreenLantern.state(p).copy();
		for (String k : GreenLanternBinding.keysOf(c, entryId)) {
			c.bindings.put(k, "");
		}
		GreenLantern.save(p, c);
	}

	public static void reset(ServerPlayer p) {
		GreenLanternState c = GreenLantern.state(p).copy();
		c.bindings.clear();
		GreenLantern.save(p, c);
	}

	/**
	 * Hold left click on an entry: it goes off once, where you are aiming, at its normal cost. A construct spawned this way
	 * becomes the Quick Deploy construct; a hold entry lets go by itself after a moment.
	 */
	public static void spawnOnce(ServerPlayer p, String entryId) {
		Entry e = GreenLanternBinding.byId(entryId);
		if (e == null) {
			return;
		}
		if (e.type() != null && !e.type().isHold()) {
			GreenLanternState c = GreenLantern.state(p).copy();
			c.selectedConstruct = e.type().ordinal();
			GreenLantern.save(p, c);
		}
		press(p, e);
		if (e.hold()) {
			MENU_RELEASE.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>())
					.put(e.id(), p.level().getGameTime() + GreenLanternConfig.MENU_SPAWN_HOLD_TICKS);
		}
	}

	/** A hold construct deployed with a tap (Quick Deploy): it lets go by itself like a menu spawn. */
	public static void spawnOnceRelease(ServerPlayer p, com.projecthero.mod.greenlantern.construct.ConstructType type) {
		Entry e = GreenLanternBinding.forConstruct(type);
		if (e != null) {
			MENU_RELEASE.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>())
					.put(e.id(), p.level().getGameTime() + GreenLanternConfig.MENU_SPAWN_HOLD_TICKS);
		}
	}

	/** Per tick (from the ability manager): menu-spawned hold entries let go when their time is up. */
	public static void tick(ServerPlayer p) {
		Map<String, Long> pending = MENU_RELEASE.get(p.getUUID());
		if (pending == null || pending.isEmpty()) {
			return;
		}
		long now = p.level().getGameTime();
		pending.entrySet().removeIf(en -> {
			if (now < en.getValue()) {
				return false;
			}
			Entry e = GreenLanternBinding.byId(en.getKey());
			if (e != null) {
				release(p, e);
			}
			return true;
		});
	}
}
