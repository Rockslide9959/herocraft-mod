package com.projecthero.mod.client.render;

import org.joml.Vector3f;

/**
 * v0.15.11, explicit user request: with an Iron Man faceplate open, only the FRONT face of the wearer's skin hat layer
 * (the overlay over their face) shows -- the hat layer's top, sides and back stay hidden under the helmet shell, which
 * is still on. Implemented on {@code ModelPart} by {@code ModelPartFrontFaceMixin}; {@code PlayerModelMixin} sets it on
 * the player model's {@code hat} part every frame (the model is shared between players, so it is always written).
 */
public interface FrontFaceOnlyPart {
	/** Render-thread flag: a front-face-only part is compiling its cubes right now. */
	boolean[] ACTIVE = { false };

	void projecthero$setFrontFaceOnly(boolean on);

	boolean projecthero$frontFaceOnly();

	/** A front face: the model-space normal points along -Z (the way a player model's face looks). */
	static boolean isFront(Vector3f normal) {
		return normal.z() < -0.5f;
	}

	/** Set the flag on any {@code ModelPart} (no-op if the mixin is somehow absent). */
	static void set(Object modelPart, boolean on) {
		if (modelPart instanceof FrontFaceOnlyPart p) {
			p.projecthero$setFrontFaceOnly(on);
		}
	}
}
