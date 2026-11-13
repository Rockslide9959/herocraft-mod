package com.projecthero.mod.client.beastboy.model;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * v0.17.3 hq round: a blocky Beast Boy animal built from vanilla box parts ({@code XxxParts}, generated with its texture by
 * {@code scratchpad/beastboy/gen_land_mammals_tex.js}). Gives the animators "friendly" helpers (+Y up, +Z forward, pitch + = nose
 * down / leg back, yaw + = toward +X, the animal's left) over vanilla's model space (Y down, front -Z), and two-bone leg IK solved in the
 * leg's parent bone, so paws stay planted however the body bobs and pitches. Units: 1 = 1/32 block.
 */
abstract class BoxBeast {
	final ModelPart root;
	private final Map<String, ModelPart> parts = new HashMap<>();
	private final Map<ModelPart, ModelPart> parents = new HashMap<>();

	BoxBeast(LayerDefinition def) {
		root = def.bakeRoot();
		index(root, null);
	}

	private void index(ModelPart p, ModelPart parent) {
		// ModelPart keeps its children private; probe them by the names the generator used through hasChild on each known part
		parents.put(p, parent);
		for (String n : names()) {
			if (p.hasChild(n) && !parts.containsKey(n)) {
				ModelPart c = p.getChild(n);
				parts.put(n, c);
				index(c, p);
			}
		}
	}

	/** Every bone name of this animal (the generator's spec). */
	abstract String[] names();

	/** Poses every bone for this frame (after {@link #reset}). */
	abstract void pose(BeastPose s);

	ModelPart p(String n) {
		ModelPart m = parts.get(n);
		if (m == null) {
			throw new IllegalArgumentException("no part " + n);
		}
		return m;
	}

	void reset() {
		root.getAllParts().forEach(ModelPart::resetPose);
	}

	void render(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
		root.render(pose, vc, light, overlay, color);
	}

	// ------------------------------------------------------------------------------------------------------------ friendly

	/** Sets a part's rotation in friendly terms. */
	static void rot(ModelPart p, float pitch, float yaw, float roll) {
		p.xRot = pitch;
		p.yRot = -yaw;
		p.zRot = -roll;
	}

	/** Adds to a part's rotation in friendly terms. */
	static void turn(ModelPart p, float pitch, float yaw, float roll) {
		p.xRot += pitch;
		p.yRot -= yaw;
		p.zRot -= roll;
	}

	/** Moves a part from its rest pivot, friendly (units). */
	static void move(ModelPart p, float dx, float dy, float dz) {
		p.x += dx;
		p.y -= dy;
		p.z -= dz;
	}

	static void scale(ModelPart p, float sx, float sy, float sz) {
		p.xScale = sx;
		p.yScale = sy;
		p.zScale = sz;
	}

	/** A bone's length: its child's rest offset (friendly, along the bone hanging down). */
	float len(String child) {
		PartPose ip = p(child).getInitialPose();
		return Mth.sqrt(ip.x * ip.x + ip.y * ip.y + ip.z * ip.z);
	}

	/** The rest position of a part's pivot in friendly model space (rest rotations are zero in these specs). */
	float[] rest(String n) {
		float x = 0f, y = 0f, z = 0f;
		for (ModelPart m = p(n); m != null; m = parents.get(m)) {
			PartPose ip = m.getInitialPose();
			x += ip.x;
			y += ip.y;
			z += ip.z;
		}
		return new float[] { x, -y, -z };
	}

	// ------------------------------------------------------------------------------------------------------------------- IK

	/** The current (posed) model-space matrix of a part's frame, vanilla coordinates, units. */
	Matrix4f frame(String n) {
		java.util.ArrayDeque<ModelPart> chain = new java.util.ArrayDeque<>();
		for (ModelPart m = p(n); m != null; m = parents.get(m)) {
			chain.push(m);
		}
		Matrix4f mat = new Matrix4f();
		for (ModelPart m : chain) {
			mat.translate(m.x, m.y, m.z);
			if (m.xRot != 0f || m.yRot != 0f || m.zRot != 0f) {
				mat.rotate(new Quaternionf().rotationZYX(m.zRot, m.yRot, m.xRot));
			}
			if (m.xScale != 1f || m.yScale != 1f || m.zScale != 1f) {
				mat.scale(m.xScale, m.yScale, m.zScale);
			}
		}
		return mat;
	}

	/**
	 * Aims a three-bone leg (top, mid, low; each hanging down along its bone at rest) so that the low bone's pivot (the joint above the
	 * paw: wrist / hock) lands on {@code joint} and the low bone points at {@code foot} (both friendly model points). {@code bendFwd}:
	 * the middle joint bends forward (a hind knee) or back (an elbow). Returns nothing; sets the three xRots.
	 */
	void aimLeg(String top, String mid, String low, String end, float[] joint, float[] foot, boolean bendFwd) {
		ModelPart a = p(top), b = p(mid), c = p(low);
		a.xRot = 0f;
		b.xRot = 0f;
		c.xRot = 0f;
		Matrix4f inv = frame(top).invert();
		// the top bone's own pivot is the origin of frame(top); its rotation is zero now, so this is the parent frame at the hip
		Vector3f j = inv.transformPosition(new Vector3f(joint[0], -joint[1], -joint[2]));
		Vector3f f = inv.transformPosition(new Vector3f(foot[0], -foot[1], -foot[2]));
		float l1 = len(mid), l2 = len(low);
		// planar solve in the leg's YZ plane (vanilla: y down, z back); the pole is forward (-z) for knees, back (+z) for elbows
		float[] root = { 0f, 0f, 0f };
		float[] tgt = { 0f, j.y, j.z };
		float[] knee = BeastMesh.ik(root, tgt, l1, l2, new float[] { 0f, 0f, bendFwd ? -1f : 1f });
		float a1 = (float) Math.atan2(knee[2], knee[1]);
		float a2 = (float) Math.atan2(tgt[2] - knee[2], tgt[1] - knee[1]);
		float a3 = (float) Math.atan2(f.z - j.z, f.y - j.y);
		a.xRot = a1;
		b.xRot = a2 - a1;
		c.xRot = a3 - a2;
	}

	/** Aims a single bone hanging down from its pivot at a friendly model point (paws, tail tips). */
	void aimDown(String n, float[] at) {
		ModelPart m = p(n);
		m.xRot = 0f;
		Matrix4f inv = frame(n).invert();
		Vector3f t = inv.transformPosition(new Vector3f(at[0], -at[1], -at[2]));
		m.xRot = (float) Math.atan2(t.z, t.y);
	}

	/** Turns a part (about its X) so it sits level with the ground (its up = model up), plus {@code tilt} (+ = toes down). */
	void level(String n, float tilt) {
		ModelPart m = p(n);
		m.xRot = 0f;
		Matrix4f inv = frame(n).invert();
		Vector3f up = inv.transformDirection(new Vector3f(0f, -1f, 0f)).normalize();
		m.xRot = (float) Math.atan2(-up.z, -up.y) + tilt;
	}

	/** The friendly model position of a point given in a part's frame (friendly offsets from its pivot). */
	float[] at(String n, float dx, float dy, float dz) {
		Vector3f v = frame(n).transformPosition(new Vector3f(dx, -dy, -dz));
		return new float[] { v.x, -v.y, -v.z };
	}
}
