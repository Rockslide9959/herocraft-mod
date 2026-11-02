package com.projecthero.mod.client.omnitrix.alien.wildmutt;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.ironman.IronManHighlight;
import com.projecthero.mod.network.WildmuttPingPayload;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * v0.17.1: Wildmutt's Scent Sense, on HIS client only (the v0.15.4 privacy rule: only client-side entities are answered, see
 * {@link IronManHighlight#mayDecide}). He has no eyes: every creature within {@link WildmuttKit#senseRange} is outlined in a
 * warm amber -- invisible ones too (vanilla draws an invisible creature's outline when it glows) -- dropped items as well
 * while he sniffs, and the creature he tracks in red at any distance.
 * <p>v0.17.1 (user: "make his smelling power better"): he tells scents apart -- monsters smell red-orange, animals green, players
 * pale gold, anything badly hurt of blood (deep red), the rest the old amber.
 * <p>v0.17.2 revamp: motion matters -- a moving creature is outlined bright, one standing still or sneaking faintly and only
 * within {@link WildmuttKit#STILL_RANGE}. And a Savage Roar's ping outlines what it reached in amber for him and his squad
 * ({@code WildmuttPingPayload}; on any viewer's client it was sent to, still never set on the creature).
 */
public final class WildmuttSense {
	public static final int COLOUR = 0xFFB060;
	public static final int ITEM_COLOUR = 0xFFE8A0;
	public static final int TRACK_COLOUR = 0xFF2A1A;
	public static final int MONSTER_COLOUR = 0xFF6A30;
	public static final int ANIMAL_COLOUR = 0x8EE05A;
	public static final int PLAYER_COLOUR = 0xFFE9A8;
	public static final int BLOOD_COLOUR = 0xB0102A;
	/** Below this share of its health a creature smells of blood. */
	public static final float BLOOD_BELOW = 0.35f;

	private WildmuttSense() {
	}

	/** The wearer's Wildmutt kit, or null when this client is not Wildmutt. */
	private static WildmuttKit me(LocalPlayer me) {
		return me != null && AlienKit.activeKit(me) instanceof WildmuttKit k ? k : null;
	}

	public static boolean outlined(Entity entity) {
		return colour(entity) != 0;
	}

	/** The outline colour for {@code entity}, or 0 for none. */
	public static int colour(Entity entity) {
		if (entity == null || !IronManHighlight.mayDecide(entity)) {
			return 0;
		}
		LocalPlayer me = Minecraft.getInstance().player;
		if (me != null && entity != me && pinged(entity)) {
			return COLOUR; // v0.17.2: a Savage Roar's ping (his or a squad mate's) -- for any viewer it was sent to
		}
		WildmuttKit k = me(me);
		if (k == null || entity == me) {
			return 0;
		}
		Entity tracked = k.tracked(me);
		if (tracked != null && tracked == entity) {
			return TRACK_COLOUR;
		}
		double r = k.senseRange(me);
		if (r <= 0 || entity.distanceToSqr(me) > r * r) {
			return 0;
		}
		if (entity instanceof ItemEntity) {
			return k.flag(me, WildmuttKit.F_SNIFF) ? ITEM_COLOUR : 0;
		}
		if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand || !living.isAlive()) {
			return 0;
		}
		// v0.17.2 revamp (W4): he senses movement -- bright while it moves, faint while it stands still or sneaks (only within 8)
		int strength = WildmuttKit.senseStrength(Math.sqrt(entity.distanceToSqr(me)), r, moving(living), living.isCrouching());
		if (strength == 0) {
			return 0;
		}
		int c;
		if (living.getHealth() < living.getMaxHealth() * BLOOD_BELOW) {
			c = BLOOD_COLOUR;
		} else if (entity instanceof net.minecraft.world.entity.monster.Enemy) {
			c = MONSTER_COLOUR;
		} else if (entity instanceof net.minecraft.world.entity.player.Player) {
			c = PLAYER_COLOUR;
		} else if (entity instanceof net.minecraft.world.entity.animal.Animal || entity instanceof net.minecraft.world.entity.animal.WaterAnimal) {
			c = ANIMAL_COLOUR;
		} else {
			c = COLOUR;
		}
		return strength == 2 ? c : faint(c);
	}

	/** A faint outline: the scent colour at {@link #FAINT} brightness (vanilla outlines have no alpha). */
	static int faint(int rgb) {
		int r = (int) (((rgb >> 16) & 255) * FAINT), g = (int) (((rgb >> 8) & 255) * FAINT), b = (int) ((rgb & 255) * FAINT);
		return Math.max(1, (r << 16) | (g << 8) | b);
	}

	public static final float FAINT = 0.38f;

	// ---------------------------------------------------------------- movement (v0.17.2 revamp)

	/** Client: per entity id, the tick it last moved -- a walker's interpolated steps can stand still for a tick. */
	private static final Map<Integer, Integer> MOVED_AT = new HashMap<>();
	private static final int MOVE_GRACE = 10;

	/** Moving now (or within the last {@link #MOVE_GRACE} ticks), or swinging / hurt -- what his hearing and smell pick up. */
	static boolean moving(LivingEntity e) {
		double dx = e.getX() - e.xo, dy = e.getY() - e.yo, dz = e.getZ() - e.zo;
		boolean now = dx * dx + dy * dy + dz * dz > 4.0e-4 || e.swinging || e.hurtTime > 0;
		if (now) {
			if (MOVED_AT.size() > 2048) {
				MOVED_AT.clear();
			}
			MOVED_AT.put(e.getId(), e.tickCount);
			return true;
		}
		Integer at = MOVED_AT.get(e.getId());
		return at != null && e.tickCount - at <= MOVE_GRACE && e.tickCount >= at;
	}

	// ---------------------------------------------------------------- the roar's ping (v0.17.2 revamp)

	/** Client: entity id -> the game time its ping outline ends (fed by {@code WildmuttPingPayload}). */
	private static final Map<Integer, Long> PINGED = new HashMap<>();

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(WildmuttPingPayload.TYPE, (payload, context) -> context.client().execute(() -> {
			if (context.client().level != null) {
				long until = context.client().level.getGameTime() + payload.ticks();
				for (int id : payload.ids()) {
					PINGED.merge(id, until, Math::max);
				}
			}
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			PINGED.clear();
			MOVED_AT.clear();
			WildmuttFootprints.clear();
		});
	}

	static boolean pinged(Entity e) {
		if (PINGED.isEmpty()) {
			return false;
		}
		Long until = PINGED.get(e.getId());
		if (until == null) {
			return false;
		}
		if (until < e.level().getGameTime() || !e.isAlive()) {
			PINGED.remove(e.getId());
			return false;
		}
		return true;
	}
}
