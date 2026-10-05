package com.projecthero.mod.client.render;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * v0.14.23: hand-tuned placement for the {@link HandRing} rings, one per ring and per view (third / first person).
 * v0.14.24: fixed shipped values only -- the in-game ring editor and its config file are gone.
 *
 * <p>Each placement is applied in the arm's pixel space on top of the built-in ring position: the ring is rotated and
 * scaled about its own centre (the gem on the knuckles), then moved by the offset. All zeros / scale 1 = the built-in
 * look. The values were tuned on a slim arm; on a wide arm they are moved out by {@link #WIDE_ARM_SHIFT}.
 */
public final class RingPlacement {
	public enum View {
		THIRD_PERSON, FIRST_PERSON
	}

	/** One ring in one view: offset in arm pixels, rotation in degrees, uniform scale. */
	public static final class Placement {
		public float x, y, z;
		public float pitch, yaw, roll;
		public float scale = 1f;

		public Placement() {
		}

		public Placement(float x, float y, float z, float pitch, float yaw, float roll, float scale) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.pitch = pitch;
			this.yaw = yaw;
			this.roll = roll;
			this.scale = scale;
		}

		public Placement copy() {
			return new Placement(x, y, z, pitch, yaw, roll, scale);
		}

		public void set(Placement o) {
			x = o.x;
			y = o.y;
			z = o.z;
			pitch = o.pitch;
			yaw = o.yaw;
			roll = o.roll;
			scale = o.scale;
		}

		boolean isIdentity() {
			return x == 0 && y == 0 && z == 0 && pitch == 0 && yaw == 0 && roll == 0 && scale == 1f;
		}
	}

	/** The rings that can be placed, by {@link HandRing.Palette#id()}. */
	public static final String[] RINGS = { HandRing.GREEN_LANTERN.id(), HandRing.FLASH.id() };

	/** Shipped defaults per ring id and view; anything missing is the built-in look. */
	private static final Placement IDENTITY = new Placement();

	private static final Map<String, Placement[]> DEFAULTS = new LinkedHashMap<>();

	static {
		// v0.14.23: tuned in the ring editor (third person, first person)
		DEFAULTS.put(HandRing.GREEN_LANTERN.id(), new Placement[] {
				new Placement(-2.4f, 0.9f, 2.0f, 0f, 90f, 0f, 1f),
				new Placement(-2.5f, 0.5f, 2.1f, 0f, 90f, 0f, 1f) });
		DEFAULTS.put(HandRing.FLASH.id(), new Placement[] {
				new Placement(-2.4f, 0.9f, 1.15f, 0f, 90f, 0f, 1f),
				new Placement(-2.5f, 0.5f, 1.2f, 0f, 90f, 0f, 1f) });
	}

	/** v0.14.24: x shift on a wide arm, whose outer face is one pixel further out than a slim arm's. */
	public static final float WIDE_ARM_SHIFT = -1f;

	private RingPlacement() {
	}

	/** The placement for {@code ring} in {@code view}. */
	public static Placement get(String ring, View view) {
		Placement[] d = DEFAULTS.get(ring);
		return d == null ? IDENTITY : d[view.ordinal()];
	}

	public static View currentView() {
		return HandRing.firstPerson ? View.FIRST_PERSON : View.THIRD_PERSON;
	}

	/**
	 * Applies {@code ring}'s placement for the view being drawn. {@code pose} must be in arm pixels (already scaled by
	 * 1/16); {@code px, py, pz} is the pivot -- the ring's centre.
	 */
	public static void apply(PoseStack pose, String ring, float px, float py, float pz) {
		Placement p = get(ring, currentView());
		if (p.isIdentity()) {
			return;
		}
		// v0.14.24: the shipped placements were tuned on a slim arm -- on a wide arm the outer face is a pixel further out,
		// and without this the ring sat buried inside the hand (invisible) for everyone with a Steve-style skin
		float wide = HandRing.slimArm ? 0f : WIDE_ARM_SHIFT;
		pose.translate(p.x + wide + px, p.y + py, p.z + pz);
		pose.mulPose(Axis.YP.rotationDegrees(p.yaw));
		pose.mulPose(Axis.XP.rotationDegrees(p.pitch));
		pose.mulPose(Axis.ZP.rotationDegrees(p.roll));
		pose.scale(p.scale, p.scale, p.scale);
		pose.translate(-px, -py, -pz);
	}
}
