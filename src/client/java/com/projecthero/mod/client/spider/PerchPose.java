package com.projecthero.mod.client.spider;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.util.Mth;

/**
 * v0.16.1: the ledge perch as editable numbers -- every part's rotation (degrees) and pivot position (model pixels, for a
 * sneaking player, whose model the renderer drops 2 px), plus whole-body drop / shift / extra lean and the first-person
 * camera drop / tilt. {@link SpiderPose} draws the perch from {@link #current()}; the in-game editor
 * ({@code /spiderposeeditor}, {@link PerchPoseEditorScreen}) edits it live.
 *
 * <p>The built-in defaults are {@link #defaults()} (perch v4). {@code config/projecthero_perch_pose.json}, written by the
 * editor's Save, overrides them for this client only (delete it to go back). Export writes the same numbers as compact
 * JSON to the clipboard and {@code config/projecthero_perch_pose_export.txt}, to be baked in as the default.
 */
public final class PerchPose {
	/** One model part: rotations in degrees, pivot in model pixels. Head rotations are added to where he looks. */
	public static final class Part {
		public float xRot;
		public float yRot;
		public float zRot;
		public float x;
		public float y;
		public float z;

		public Part() {
		}

		Part(float xRot, float yRot, float zRot, float x, float y, float z) {
			this.xRot = xRot;
			this.yRot = yRot;
			this.zRot = zRot;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public Part copy() {
			return new Part(xRot, yRot, zRot, x, y, z);
		}

		public float get(int i) {
			return switch (i) {
				case 0 -> xRot;
				case 1 -> yRot;
				case 2 -> zRot;
				case 3 -> x;
				case 4 -> y;
				default -> z;
			};
		}

		public void set(int i, float v) {
			switch (i) {
				case 0 -> xRot = v;
				case 1 -> yRot = v;
				case 2 -> zRot = v;
				case 3 -> x = v;
				case 4 -> y = v;
				default -> z = v;
			}
		}

		/** The same part on the other side of the body: x, yRot and zRot flip. */
		public Part mirrored() {
			return new Part(xRot, -yRot, -zRot, -x, y, z);
		}
	}

	public int version = 1;
	public Part head = new Part();
	public Part body = new Part();
	public Part rightArm = new Part();
	public Part leftArm = new Part();
	public Part rightLeg = new Part();
	public Part leftLeg = new Part();
	/** Whole body: + moves everything down (pixels). */
	public float drop;
	/** Whole body: + moves everything back, - forward (pixels). */
	public float shift;
	/** Extra forward lean of the torso about the hips (degrees; hip-anchored, the torso never detaches). */
	public float lean;
	/** First person: the eye sinks this far (blocks) and tips this many degrees toward the drop. */
	public float cameraDrop = 0.32f;
	public float cameraTilt = 6.0f;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Gson COMPACT = new Gson();
	private static PerchPose current = defaults();

	public PerchPose() {
	}

	public static PerchPose current() {
		return current;
	}

	public static void set(PerchPose pose) {
		current = pose == null ? defaults() : pose;
	}

	public PerchPose copy() {
		PerchPose c = new PerchPose();
		c.head = head.copy();
		c.body = body.copy();
		c.rightArm = rightArm.copy();
		c.leftArm = leftArm.copy();
		c.rightLeg = rightLeg.copy();
		c.leftLeg = leftLeg.copy();
		c.drop = drop;
		c.shift = shift;
		c.lean = lean;
		c.cameraDrop = cameraDrop;
		c.cameraTilt = cameraTilt;
		return c;
	}

	/** Part by index: 0 head, 1 body, 2 right arm, 3 left arm, 4 right leg, 5 left leg. */
	public Part part(int i) {
		return switch (i) {
			case 0 -> head;
			case 1 -> body;
			case 2 -> rightArm;
			case 3 -> leftArm;
			case 4 -> rightLeg;
			default -> leftLeg;
		};
	}

	public void setPart(int i, Part p) {
		switch (i) {
			case 0 -> head = p;
			case 1 -> body = p;
			case 2 -> rightArm = p;
			case 3 -> leftArm = p;
			case 4 -> rightLeg = p;
			default -> leftLeg = p;
		}
	}

	/** The other side's part index for a limb (2<->3, 4<->5), or -1 for the head / body. */
	public static int mirrorOf(int i) {
		return switch (i) {
			case 2 -> 3;
			case 3 -> 2;
			case 4 -> 5;
			case 5 -> 4;
			default -> -1;
		};
	}

	private static float deg(float rad) {
		return rad * Mth.RAD_TO_DEG;
	}

	/**
	 * Perch v4 (the user's sketch, front view / | \): legs splayed ~41 degrees out and ~14 forward beside the hips, feet
	 * on the block's top; the body on top of them leaning ~20 degrees forward; the arms an inverted V meeting on the
	 * ledge at the bottom centre in front. Positions for a sneaking player (block top at model y 22).
	 */
	public static PerchPose defaults() {
		PerchPose p = new PerchPose();
		final float ground = 22.0f;
		final float legOut = 0.72f;
		final float legFwd = 0.25f;
		float legDrop = 12.0f * Mth.cos(legFwd) * Mth.cos(legOut);
		float legY = ground - legDrop - 2.0f * Mth.sin(legOut);
		final float legX = 3.0f;
		p.rightLeg = new Part(deg(-legFwd), 0f, deg(legOut), -legX, legY, 0f);
		p.leftLeg = p.rightLeg.mirrored();
		final float hipY = legY + 1.0f;
		final float lean = 0.35f;
		float neckY = hipY - 12.0f * Mth.cos(lean);
		float neckZ = -12.0f * Mth.sin(lean);
		p.body = new Part(deg(lean), 0f, 0f, 0f, neckY, neckZ);
		p.head = new Part(0f, 0f, 0f, 0f, neckY, neckZ - 0.3f);
		float shoulderY = Math.max(neckY + 3.0f * Mth.cos(lean), ground - 10.5f);
		float shoulderZ = neckZ * Mth.clamp((hipY - shoulderY) / 12.0f, 0.0f, 1.0f) - 2.6f;
		float dx = 4.2f;
		float dy = ground - shoulderY - 0.2f;
		float dz = (float) Math.sqrt(Math.max(0.0f, 144.0f - dx * dx - dy * dy));
		float armFwd = (float) Math.asin(Mth.clamp(dz / 12.0f, 0.0f, 1.0f));
		float armIn = (float) Math.atan2(dx, Math.max(0.1f, dy));
		p.rightArm = new Part(deg(-armFwd), 0f, deg(-armIn), -5.0f, shoulderY, shoulderZ);
		p.leftArm = p.rightArm.mirrored();
		return p;
	}

	// ---------------- files ----------------

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("projecthero_perch_pose.json");
	}

	public static Path exportFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("projecthero_perch_pose_export.txt");
	}

	/** Load the saved pose if there is one (else the defaults). Never throws. */
	public static void load() {
		try {
			Path f = file();
			if (Files.exists(f)) {
				PerchPose p = GSON.fromJson(Files.readString(f), PerchPose.class);
				if (p != null && p.head != null && p.body != null && p.rightArm != null && p.leftArm != null
						&& p.rightLeg != null && p.leftLeg != null) {
					current = p;
					return;
				}
			}
		} catch (Exception e) {
			com.projecthero.mod.ProjectHeroMod.LOGGER.warn("[perch pose] could not read {}: {}", file(), e.toString());
		}
		current = defaults();
	}

	/** Save {@link #current()} to the config file. Returns true on success. */
	public static boolean save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(current));
			return true;
		} catch (Exception e) {
			com.projecthero.mod.ProjectHeroMod.LOGGER.warn("[perch pose] could not save {}: {}", file(), e.toString());
			return false;
		}
	}

	/** {@link #current()} as compact JSON (also written to the export file). */
	public static String export() {
		String json = COMPACT.toJson(current);
		try {
			Files.createDirectories(exportFile().getParent());
			Files.writeString(exportFile(), json);
		} catch (Exception e) {
			com.projecthero.mod.ProjectHeroMod.LOGGER.warn("[perch pose] could not write {}: {}", exportFile(), e.toString());
		}
		return json;
	}
}
