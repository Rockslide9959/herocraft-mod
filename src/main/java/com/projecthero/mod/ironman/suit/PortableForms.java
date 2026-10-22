package com.projecthero.mod.ironman.suit;

import static com.projecthero.mod.ironman.gantry.GantryTimeline.FACEPLATE;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.HELMET;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.JACKET;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.L_ARM;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.L_BOOT;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.L_GAUNTLET;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.L_SLEEVE;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.PANTS;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.R_ARM;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.R_BOOT;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.R_GAUNTLET;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.R_SLEEVE;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.THIGH_BOTTOM;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.THIGH_TOP;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.TORSO_BOTTOM;
import static com.projecthero.mod.ironman.gantry.GantryTimeline.TORSO_TOP;
import static com.projecthero.mod.ironman.suit.PortableForm.poseKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.projecthero.mod.ironman.item.IronManItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7 (user) Iron Man Era II stage 2: the registry of {@link PortableForm portable forms}, one per suit id. Forms
 * are registered statically, in a fixed order, on both sides -- a form's index is part of its synced pose kind
 * ({@link #poseKind}), so no extra packet is needed for other players to know which prop to draw.
 *
 * <p>Add a suit's form in the static block below (the order must never change between client and server; append only).
 * See {@code docs/IRONMAN_ERA2_FOLD.md}.
 */
public final class PortableForms {
	/** Pose kinds {@code POSE_BASE + 2 * index} (deploy) and {@code + 1} (fold) belong to the portable forms. */
	public static final int POSE_BASE = 100;

	private static final List<PortableForm> ALL = new ArrayList<>();

	/**
	 * The Mk VI Suitcase (mark_2): a red / gold briefcase carried in the hand. Deploy: it drops in front of you, flips
	 * open on the floor and the suit climbs on boots first -- legs, then arms + chest, the helmet, the faceplate last;
	 * 3.6 s. The fold is the same frames backwards, ending with the case back in your hand.
	 */
	public static final PortableForm MK6_BRIEFCASE = register(new PortableForm("mark_2", () -> IronManItems.MARK_V_SUITCASE,
			PortableForm.Place.HAND, PortableForm.Place.FLOOR,
			new PortableForm.Beats(4, 12, 20, 60, 62, 72),
			PortableForm.stages(PortableForm.noStages(),
					R_BOOT, 16, 25, L_BOOT, 17, 26,
					PANTS, 20, 38,
					THIGH_TOP, 24, 29, THIGH_BOTTOM, 28, 34,
					R_GAUNTLET, 32, 37, L_GAUNTLET, 32, 37,
					TORSO_TOP, 34, 39, TORSO_BOTTOM, 38, 44,
					R_ARM, 36, 44, L_ARM, 36, 44,
					JACKET, 40, 52, R_SLEEVE, 38, 50, L_SLEEVE, 38, 50,
					HELMET, 48, 56, FACEPLATE, 50, 57),
			PortableForm.Direction.CLIMB, "mk6_briefcase",
			new float[][] {
					// carrying the case at the side
					poseKey(0, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0f, 0f, 0f, 0f, 0.15f, 0f),
					// let go: the hand swings a little forward, eyes on the case
					poseKey(5, -0.35f, 0f, 0.12f, 0f, 0f, -0.08f, 0f, 0.03f, 0f, -0.03f, 0.5f, 0f),
					poseKey(14, -0.1f, 0f, 0.22f, -0.05f, 0f, -0.22f, 0f, 0.08f, 0f, -0.08f, 0.62f, 0f),
					// boots and legs: feet apart, watching the plates climb
					poseKey(28, -0.05f, 0f, 0.38f, -0.05f, 0f, -0.38f, 0f, 0.12f, 0f, -0.12f, 0.5f, 0f),
					// arms + chest: arms out as the plates climb them
					poseKey(38, -0.1f, 0f, 0.8f, -0.1f, 0f, -0.8f, 0f, 0.12f, 0f, -0.12f, 0.2f, 0f),
					poseKey(50, 0.05f, 0f, 0.95f, 0.05f, 0f, -0.95f, 0f, 0.12f, 0f, -0.12f, -0.12f, 0f),
					// helmet + seal: chin up, arms down
					poseKey(58, 0f, 0f, 0.3f, 0f, 0f, -0.3f, 0f, 0.08f, 0f, -0.08f, -0.15f, 0f),
					poseKey(72, 0f, 0f, 0.1f, 0f, 0f, -0.1f, 0f, 0.04f, 0f, -0.04f, 0f, 0f) }));

	// ---- v0.16.7 (user) Era II stage 2: the Mk IX heat case (append-only: keep this block after MK6_BRIEFCASE) ----
	/**
	 * The Mk IX Inferno (mark_9): a squat charcoal heat-proof case carried in the hand. Deploy: it is set down in front
	 * of you, both leaves swing open sideways on a glow of heat, the black plate climbs on boots first and the orange eyes
	 * ignite last; 4.2 s. The fold is the same frames backwards, ending with the case in your hand. Works in fire and
	 * lava: the pieces stay in their slots (full-set immunity) until the fold's last tick, a deploy is damage-immune, and
	 * {@link PortableDeploy#heatGrace} covers the moment after (see {@code docs/IRONMAN_ERA2_FOLD.md}).
	 */
	public static final PortableForm MK9_HEAT_CASE = register(new PortableForm("mark_9", () -> IronManItems.MARK_9_HEAT_CASE,
			PortableForm.Place.HAND, PortableForm.Place.FLOOR,
			new PortableForm.Beats(6, 18, 28, 70, 72, 84),
			PortableForm.stages(PortableForm.noStages(),
					R_BOOT, 26, 34, L_BOOT, 27, 35,
					PANTS, 30, 46,
					THIGH_TOP, 32, 38, THIGH_BOTTOM, 36, 42,
					R_GAUNTLET, 40, 45, L_GAUNTLET, 40, 45,
					TORSO_TOP, 42, 47, TORSO_BOTTOM, 46, 52,
					R_ARM, 44, 52, L_ARM, 44, 52,
					JACKET, 48, 60, R_SLEEVE, 46, 58, L_SLEEVE, 46, 58,
					HELMET, 56, 64, FACEPLATE, 60, 67),
			PortableForm.Direction.CLIMB, "mk9_heat_case",
			new float[][] {
					// carrying the case at the side
					poseKey(0, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0f, 0f, 0f, 0f, 0.15f, 0f),
					// lowering it forward, eyes on it
					poseKey(6, -0.5f, 0f, 0.06f, 0.08f, 0f, -0.08f, 0f, 0.02f, 0f, -0.02f, 0.55f, 0f),
					// set down: the hand follows through, then comes back
					poseKey(12, -0.38f, 0f, 0.14f, 0.05f, 0f, -0.14f, 0f, 0.05f, 0f, -0.05f, 0.65f, 0f),
					poseKey(20, -0.05f, 0f, 0.25f, 0f, 0f, -0.25f, 0f, 0.08f, 0f, -0.08f, 0.6f, 0f),
					// the leaves swing open on the heat: the left forearm up against the glow, head turned a little
					poseKey(28, -0.15f, 0f, 0.42f, -1.05f, 0.35f, -0.22f, 0f, 0.13f, 0f, -0.13f, 0.38f, 0.18f),
					// boots and legs: feet apart, watching the plates climb
					poseKey(38, -0.05f, 0f, 0.42f, -0.1f, 0f, -0.42f, 0f, 0.14f, 0f, -0.14f, 0.45f, 0f),
					// arms + chest: arms out as the plates climb them
					poseKey(48, -0.1f, 0f, 0.85f, -0.1f, 0f, -0.85f, 0f, 0.14f, 0f, -0.14f, 0.18f, 0f),
					poseKey(58, 0.05f, 0f, 1.0f, 0.05f, 0f, -1.0f, 0f, 0.14f, 0f, -0.14f, -0.1f, 0f),
					// helmet + eyes: chin up, fists down
					poseKey(68, 0f, 0f, 0.28f, 0f, 0f, -0.28f, 0f, 0.09f, 0f, -0.09f, -0.2f, 0f),
					// the settle
					poseKey(76, 0f, 0f, 0.1f, 0f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, -0.03f, 0f),
					poseKey(84, 0f, 0f, 0.08f, 0f, 0f, -0.08f, 0f, 0.04f, 0f, -0.04f, 0f, 0f) }));
	// ---- end Mk IX heat case ----

	private PortableForms() {
	}

	/** Appends a form (static init only -- the order is part of the synced pose kinds). */
	public static PortableForm register(PortableForm form) {
		for (PortableForm f : ALL) {
			if (f.suitId().equals(form.suitId())) {
				throw new IllegalStateException("two portable forms for " + form.suitId());
			}
		}
		ALL.add(form);
		return form;
	}

	public static List<PortableForm> all() {
		return Collections.unmodifiableList(ALL);
	}

	/** The form of suit {@code suitId}, or null if it has none. */
	public static PortableForm byId(String suitId) {
		if (suitId == null) {
			return null;
		}
		for (PortableForm f : ALL) {
			if (f.suitId().equals(suitId)) {
				return f;
			}
		}
		return null;
	}

	/** The form whose packed item {@code item} is, or null. */
	public static PortableForm byItem(Item item) {
		for (PortableForm f : ALL) {
			if (f.packedItem().get() == item) {
				return f;
			}
		}
		return null;
	}

	/** The form whose packed item {@code stack} is, or null. */
	public static PortableForm byStack(ItemStack stack) {
		return stack == null || stack.isEmpty() ? null : byItem(stack.getItem());
	}

	/** The synced pose kind of {@code form}'s deploy ({@code up}) or fold. */
	public static int poseKind(PortableForm form, boolean up) {
		return POSE_BASE + 2 * ALL.indexOf(form) + (up ? 0 : 1);
	}

	/** The form a pose kind belongs to, or null. */
	public static PortableForm byPoseKind(int kind) {
		int i = (kind - POSE_BASE) / 2;
		return kind >= POSE_BASE && i < ALL.size() ? ALL.get(i) : null;
	}

	/** Is {@code kind} a portable deploy (true) -- false for a fold or anything else. */
	public static boolean deployPose(int kind) {
		return byPoseKind(kind) != null && (kind - POSE_BASE) % 2 == 0;
	}

	/** Is {@code kind} a portable fold? */
	public static boolean foldPose(int kind) {
		return byPoseKind(kind) != null && (kind - POSE_BASE) % 2 == 1;
	}
}
