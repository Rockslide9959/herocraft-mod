package com.projecthero.mod.client.beastboy.model;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/** v0.17.3 Beast Boy: small rigging helpers shared by the bodies (body matrices, gait, limbs). */
final class Rig {
	private Rig() {
	}

	/** A body bone as a matrix: translate, then yaw, pitch (+ = nose down), roll. */
	static Matrix4f mat(float x, float y, float z, float yaw, float pitch, float roll) {
		return new Matrix4f().translation(x, y, z).rotateYXZ(yaw, pitch, roll);
	}

	/** A point given in the bone's space, in the parent's space. */
	static float[] tp(Matrix4f m, float x, float y, float z) {
		Vector3f v = m.transformPosition(x, y, z, new Vector3f());
		return new float[] { v.x, v.y, v.z };
	}

	static void push(PoseStack pose, Matrix4f m) {
		pose.pushPose();
		pose.mulPose(m);
	}

	/** Gait: the foot's {forward offset, lift} at phase {@code ph}; stance slides back on the ground, swing lifts it forward. */
	static float[] step(float ph, float stride, float lift) {
		float c = Mth.cos(ph);
		return new float[] { Mth.sin(ph) * stride, Math.max(0f, c) * lift };
	}

	/** A limb through three joints (hip / knee / foot), radii at each, smoothly bent at the middle. */
	static void limb(PoseStack pose, float[] a, float[] b, float[] c, float ra, float rb, float rc, int sw, float[] up) {
		BeastMesh.loft(pose, new float[][] { a, BeastMesh.lerp(a, b, 0.55f), b, BeastMesh.lerp(b, c, 0.5f), c },
				new float[] { ra, (ra + rb) * 0.5f * 1.04f, rb, (rb + rc) * 0.5f, rc }, new float[] { ra, (ra + rb) * 0.5f * 1.04f, rb, (rb + rc) * 0.5f, rc },
				3, 12, sw, 0, up);
	}

	/** A limb through four joints. */
	static void limb4(PoseStack pose, float[] a, float[] b, float[] c, float[] d, float ra, float rb, float rc, float rd, int sw, float[] up) {
		BeastMesh.loft(pose, new float[][] { a, BeastMesh.lerp(a, b, 0.5f), b, c, d }, new float[] { ra, (ra + rb) * 0.52f, rb, rc, rd },
				new float[] { ra, (ra + rb) * 0.52f, rb, rc, rd }, 3, 12, sw, 0, up);
	}

	/** Yaw (about +Y, + toward +X) of a horizontal direction. */
	static float yawOf(float[] d) {
		return (float) Math.atan2(d[0], d[2]);
	}

	static float sin(float x) {
		return Mth.sin(x);
	}

	static float cos(float x) {
		return Mth.cos(x);
	}
}
