package com.projecthero.mod.ironman;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.4: the Iron Man mob / entity highlight decision, in one place.
 *
 * <h2>THE RULE -- the highlight is PRIVATE to the wearer. Do not regress this.</h2>
 * The user has had to ask for this repeatedly: the outline the suit draws around mobs must only ever be visible to
 * the player wearing / using that suit -- never to any other player, never to squad members, never on a LAN host's
 * guests. Therefore:
 * <ul>
 *   <li>it is decided <b>only on the viewer's own client</b>, for that client's own {@code LocalPlayer}, from that
 *       player's own synced {@link TonyStarkState} and own helmet ({@code EntityGlowMixin} calls {@link #decision});</li>
 *   <li>it is <b>never</b> applied server-side: no {@code MobEffects.GLOWING}, no {@code Entity#setGlowingTag}, no
 *       shared-flag 6, no team colour, nothing on the mob at all -- all of those are synced to every tracking player;</li>
 *   <li>no packet ever tells another player who is highlighted, or even that the highlight is on: the targeting lock
 *       payload goes to the wearer only, the all-players {@code TonyStarkState} sync masks {@code mobHighlightOn} off
 *       ({@code TonyStarkState#forSync}) and the wearer alone gets it through the {@code targetOnly}
 *       {@code ModAttachments#IRON_MAN_HIGHLIGHT_ON} (mirrored by {@code IronManSuitTicker#mirrorPrivateHighlight});</li>
 *   <li>and the client hook must refuse to answer for a <b>server-side</b> entity ({@link #mayDecide}). In
 *       single-player / LAN the integrated server runs in the same JVM as the host's client, and vanilla's
 *       {@code LivingEntity#updateGlowingStatus} asks {@code isCurrentlyGlowing()} on the server thread to set shared
 *       flag 6 -- which is broadcast to everyone. A client hook that answered there (it did, before v0.15.4) leaked the
 *       host's highlight to every other player and squad mate.</li>
 * </ul>
 * {@code IronManHighlightPrivacyGameTests} pins all of this down.
 */
public final class IronManHighlight {
	private IronManHighlight() {
	}

	/**
	 * Only a <b>client-side</b> entity may ever be outlined by the highlight. A server-side entity (the integrated
	 * server's copy, in single-player / LAN) must always get the vanilla answer -- see the class javadoc.
	 */
	public static boolean mayDecide(Entity target) {
		return target != null && target.level() != null && target.level().isClientSide();
	}

	/** The Iron Man suit the viewer has on their head, or null. */
	public static IronManSuit helmetSuit(Player viewer) {
		if (!(viewer.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof IronManArmorItem helmet)) {
			return null;
		}
		return IronManSuits.byId(helmet.suitId());
	}

	/**
	 * {@code true}/{@code false} when the viewer's own Iron Man highlight owns the decision for {@code target} (the
	 * viewer wears an Iron Man helmet and the target is something the highlight could outline), or {@code null} to
	 * leave it to vanilla / the other powers. Only ever reads the VIEWER's own state.
	 */
	public static Boolean decision(Player viewer, Entity target) {
		if (viewer == null || target == viewer || !(target instanceof LivingEntity)
				|| target instanceof net.minecraft.world.entity.decoration.ArmorStand) { // v0.15.9: never a rack's display stand
			return null;
		}
		if (!(viewer.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof IronManArmorItem)) {
			return null;
		}
		IronManSuit wornSuit = helmetSuit(viewer);
		boolean coloured = wornSuit != null && wornSuit.coloredEntityGlow();
		// candidate = an entity this suit's highlight could plausibly light up. A coloured-glow suit (Mark 6/7) can light
		// ANY living entity; every other mark only ever lights hostiles.
		boolean candidate = coloured || target instanceof Enemy;
		if (!candidate) {
			return null;
		}
		if (outlines(viewer, target)) {
			return Boolean.TRUE;
		}
		// Not highlighted right now: take the authoritative "off" inside the scan radius, where a stale outline could
		// linger; anything further out is left to vanilla / the other powers.
		double range = wornSuit == null ? 34.0 : wornSuit.targetScanRange();
		return inSphere(viewer, target, range) ? Boolean.FALSE : null;
	}

	/** Does the viewer's OWN powered helmet outline {@code target} right now? */
	public static boolean outlines(Player viewer, Entity target) {
		if (viewer == null || target == viewer || !(target instanceof LivingEntity living) || !living.isAlive()
				|| target instanceof net.minecraft.world.entity.decoration.ArmorStand) {
			return false;
		}
		if (!(viewer.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof IronManArmorItem helmet)) {
			return false;
		}
		IronManSuit wornSuit = IronManSuits.byId(helmet.suitId());
		TonyStarkState st = viewer.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		// "powered on": the worn suit still has some energy in the pool
		if (st == null || !st.hasPower || st.suitEnergy.getOrDefault(helmet.suitId(), 0.0f) <= 0.0f) {
			return false;
		}
		double passive = wornSuit == null ? 0.0 : wornSuit.passiveHighlightRange();
		if (passive > 0.0) {
			return inSphere(viewer, target, passive);
		}
		// the client is only ever told its OWN highlight state, through the wearer-only attachment (the all-players
		// TonyStarkState sync masks it off); the server reads its own truth
		boolean on = viewer.level().isClientSide()
				? viewer.getAttachedOrElse(ModAttachments.IRON_MAN_HIGHLIGHT_ON, false) : st.mobHighlightOn;
		if (!on) {
			return false;
		}
		double range = wornSuit == null ? 34.0 : wornSuit.targetScanRange();
		if (!inSphere(viewer, target, range)) {
			return false;
		}
		// a coloured-glow suit (Mark 6 / Mark 7) outlines every nearby entity; every other mark only hostiles
		if (wornSuit != null && wornSuit.coloredEntityGlow()) {
			return true;
		}
		return target instanceof Enemy;
	}

	/**
	 * v0.15.11, explicit user request: the highlight covers a full SPHERE of {@code range} blocks around the wearer -- a
	 * mob far above (on a cliff, in the air) or below (in a cave) is outlined exactly like one at eye level. Measured
	 * from the middle of the wearer's body to the nearest point of the target's hit-box, in all three axes.
	 */
	public static boolean inSphere(Entity viewer, Entity target, double range) {
		return com.projecthero.mod.hero.power.AbilityHelpers.distanceSqToBox(target, sphereCentre(viewer)) <= range * range;
	}

	/** The centre of the highlight sphere: the middle of the wearer's body. */
	public static net.minecraft.world.phys.Vec3 sphereCentre(Entity viewer) {
		return viewer.position().add(0.0, viewer.getBbHeight() * 0.5, 0.0);
	}

	/**
	 * v0.15.11: everything the highlight could outline around {@code viewer} -- a CUBE search box of {@code range} in every
	 * direction (never a flat slab), then the {@link #inSphere} test. Same answer as {@link #outlines} per entity; used by
	 * the gametests and anything that wants the list.
	 */
	public static java.util.List<Entity> outlinedAround(Player viewer, double range) {
		net.minecraft.world.phys.Vec3 c = sphereCentre(viewer);
		net.minecraft.world.phys.AABB cube = new net.minecraft.world.phys.AABB(c.x - range, c.y - range, c.z - range,
				c.x + range, c.y + range, c.z + range);
		return viewer.level().getEntities(viewer, cube, e -> outlines(viewer, e));
	}

	/** The coloured-glow suits' outline colour: players yellow, hostiles red, everything else blue. */
	public static int colour(Entity target) {
		if (target instanceof Player) {
			return 0xFFE64A;
		}
		if (target instanceof Enemy) {
			return 0xFF4A4A;
		}
		return 0x5AA0FF;
	}
}
