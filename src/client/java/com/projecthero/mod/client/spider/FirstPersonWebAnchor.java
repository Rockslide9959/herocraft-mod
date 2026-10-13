package com.projecthero.mod.client.spider;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * v0.16.1: where the web shooter of the drawn FIRST-PERSON hand is, so every web line the local player fires leaves
 * exactly from it on screen.
 *
 * <p>The first-person arm is drawn in its own pass with its own projection (a fixed field of view, its own view bob and
 * arm sway), so no estimate in world view space can match it at every FOV / bob setting. Instead the arm render itself
 * reports the posed wrist: {@code PlayerRendererHandMixin} hands us the arm part and pose stack exactly as the arm was
 * drawn, we push the wrist point through that model-view and the hand pass's projection to normalised screen
 * coordinates, and the world line renderer turns that screen point back into a world point a little in front of the
 * camera using the world pass's own projection and view rotation (captured at the start of the world render). The line
 * then lands on the wrist whatever the FOV, view bobbing, swing raise, perch or arm animation.
 *
 * <p>The arm pass runs after the world pass, so the screen point used is the one from the previous frame -- the hand is
 * fixed to the view, so between two frames it moves by at most a pixel or so. A side not drawn for 150 ms has no
 * point and the caller falls back to its estimate.
 */
public final class FirstPersonWebAnchor {
	/** Arm-local wrist / web-shooter point (blocks): 10 px down the 12 px arm, on its palm side. */
	private static final float WRIST_Y = 0.62f;
	private static final float WRIST_Z = -0.08f;
	/** How far in front of the camera the line's near end is placed (world blocks; past the 0.05 near plane). */
	private static final double DEPTH = 0.6;
	private static final long STALE_NS = 150_000_000L;

	private static final float[][] NDC = new float[2][2];
	private static final long[] AT = new long[2];
	private static final Matrix4f WORLD_INV = new Matrix4f();
	private static boolean worldReady;

	private FirstPersonWebAnchor() {
	}

	public static void initialize() {
		WorldRenderEvents.START.register(context -> {
			Matrix4f m = new Matrix4f(context.projectionMatrix()).mul(context.positionMatrix());
			WORLD_INV.set(m).invert();
			worldReady = true;
		});
	}

	/** From the first-person arm render (pose = the stack the arm was drawn with, arm = its posed part). */
	public static void captureArm(PoseStack pose, ModelPart arm, boolean right) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		Vector4f v = new Vector4f(0.0f, WRIST_Y, WRIST_Z, 1.0f);
		v.mul(pose.last().pose());
		pose.popPose();
		// the hand pass also has the view rotation on the model-view stack (its pose stack carries the inverse) --
		// the full chain is projection * model-view * pose
		Vector4f clip = new Vector4f(v);
		clip.mul(RenderSystem.getModelViewMatrix());
		clip.mul(RenderSystem.getProjectionMatrix());
		if (clip.w <= 1.0e-5f) {
			return;
		}
		int i = right ? 0 : 1;
		NDC[i][0] = clip.x / clip.w;
		NDC[i][1] = clip.y / clip.w;
		AT[i] = System.nanoTime();
	}

	/** Has this side's hand been drawn recently enough to anchor a line? */
	public static boolean has(boolean right) {
		return worldReady && System.nanoTime() - AT[right ? 0 : 1] < STALE_NS;
	}

	/** The world point (this frame's world projection) that sits on the drawn wrist of {@code right}, or null. */
	public static Vec3 worldPoint(boolean right, Camera cam) {
		if (!has(right)) {
			return null;
		}
		float x = NDC[right ? 0 : 1][0];
		float y = NDC[right ? 0 : 1][1];
		Vector4f near = new Vector4f(x, y, -1.0f, 1.0f).mul(WORLD_INV);
		Vector4f far = new Vector4f(x, y, 1.0f, 1.0f).mul(WORLD_INV);
		if (Math.abs(near.w) < 1.0e-9f || Math.abs(far.w) < 1.0e-9f) {
			return null;
		}
		Vec3 a = new Vec3(near.x / near.w, near.y / near.w, near.z / near.w);
		Vec3 b = new Vec3(far.x / far.w, far.y / far.w, far.z / far.w);
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0e-12) {
			return null;
		}
		// camera-relative space: the camera sits at the origin
		return cam.getPosition().add(dir.normalize().scale(DEPTH));
	}

	public static void reset() {
		AT[0] = 0L;
		AT[1] = 0L;
		worldReady = false;
	}
}
