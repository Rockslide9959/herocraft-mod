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
