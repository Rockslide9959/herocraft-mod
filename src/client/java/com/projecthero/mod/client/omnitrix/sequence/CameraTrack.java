package com.projecthero.mod.client.omnitrix.sequence;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixLongTransform;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: a show sequence's scripted camera, as keys (moved out of the v0.17.2 Four Arms code). Each key is
 * <pre>
 *   { tick, distance, angle, camera height, look height, look right, look forward }
 * </pre>
 * -- the distance from the point looked at, the angle round the body (degrees; 0 = in front, + = toward their right), the camera's
 * height, then the point looked at (height, right, forward); all in a 1.8-block human's blocks, scaled with the body after the
 * change ({@code ShowSequenceClient.cameraScale}). A key a hair after the one before it (under 0.05 ticks) is a CUT; each cut starts
 * a new shot ({@link #shot}). {@link #W} for the camera height / look point = the watch (a close-up looking down onto the dial).
 * Keys ease into each other (smoothstep) except inside a {@link #linear} span (a whip pan runs at speed through its middle key).
 */
public final class CameraTrack {
	/** "The watch": as the camera height and the look point, frames the dial from just above it. */
	public static final float W = -1f;

	private final float[][] keys;
	private final List<float[]> linear = new ArrayList<>();
	private final List<float[]> shakes = new ArrayList<>();

	public CameraTrack(float[][] keys) {
		this.keys = keys;
	}

	/** Joins key lists (e.g. a shared slap / dial close-up from {@link ShowShots} and the alien's own). */
	public static float[][] keys(float[][]... parts) {
		List<float[]> all = new ArrayList<>();
		for (float[][] part : parts) {
			for (float[] k : part) {
				all.add(k);
			}
		}
		return all.toArray(new float[0][]);
	}

	/** Segments lying inside {@code from..to} interpolate linearly (a whip pan). */
	public CameraTrack linear(float from, float to) {
		linear.add(new float[] { from, to });
		return this;
	}

	/**
	 * A hand-held shake from {@code from} to {@code to}: {@code amp} blocks (scaled with the body), dying away over {@code decay}
	 * ticks.
	 */
	public CameraTrack shake(float from, float to, float amp, float decay) {
		shakes.add(new float[] { from, to, amp, decay });
		return this;
	}

	public float[][] keys() {
		return keys;
	}

	/** The shot number at {@code t} (0 = the first; +1 at every cut). */
	public int shot(float t) {
		int n = 0;
		for (int i = 1; i < keys.length; i++) {
			if (keys[i][0] <= t && keys[i][0] - keys[i - 1][0] < 0.05f) {
				n++;
			}
		}
		return n;
	}

	/** The tick the shot at {@code t} began. */
	public float shotStart(float t) {
		float s = keys[0][0];
		for (int i = 1; i < keys.length; i++) {
			if (keys[i][0] <= t && keys[i][0] - keys[i - 1][0] < 0.05f) {
				s = keys[i][0];
			}
		}
		return s;
	}

	/** Where the camera is at {@code t}: {x, y, z, yaw, pitch}. {@code s} = the body scale. */
	public double[] at(Player p, float t, float partial, float s) {
		int i = 0;
		while (i < keys.length - 2 && t > keys[i + 1][0]) {
			i++;
		}
		float[] a = keys[i], b = keys[i + 1];
		float f = (t - a[0]) / Math.max(0.001f, b[0] - a[0]);
		boolean lin = false;
		for (float[] span : linear) {
			if (a[0] >= span[0] && b[0] <= span[1]) {
				lin = true;
			}
		}
		f = lin ? Mth.clamp(f, 0f, 1f) : ShowMath.smooth(f);
		float yaw = OmnitrixLongTransform.yaw(Omnitrix.state(p));
		Vec3 base = p.getPosition(partial);
		Vec3 fwd = ShowMath.forward(yaw), right = new Vec3(-fwd.z, 0, fwd.x);
		Vec3 look;
		if (a[4] == W) {
			Vec3 w = OmnitrixSequenceClient.wrist(p);
			look = w != null ? w : base.add(0, 1.08, 0).add(fwd.scale(0.36)).add(right.scale(-0.02));
		} else {
			float lh = Mth.lerp(f, a[4], b[4]), lr = Mth.lerp(f, a[5], b[5]), lf = Mth.lerp(f, a[6], b[6]);
			look = base.add(0, lh * s, 0).add(right.scale(lr * s)).add(fwd.scale(lf * s));
		}
		float dist = Mth.lerp(f, a[1], b[1]) * s;
		float ang = Mth.lerp(f, a[2], b[2]) * Mth.DEG_TO_RAD;
		Vec3 dir = fwd.scale(Mth.cos(ang)).add(right.scale(Mth.sin(ang)));
		double camY;
		if (a[3] == W) {
			// over the dial: a little above it, looking down onto the face
			camY = look.y + dist * 0.75;
			dist *= 0.66f;
		} else {
			camY = base.y + Mth.lerp(f, a[3], b[3]) * s;
		}
		Vec3 pos = new Vec3(look.x + dir.x * dist, camY, look.z + dir.z * dist);
		for (float[] sh : shakes) {
			if (t >= sh[0] && t < sh[1]) {
				float k = sh[2] * s * (1f - ShowMath.smooth((t - sh[0]) / sh[3]));
				pos = pos.add(Mth.sin(t * 2.7f) * k, Mth.cos(t * 3.1f) * k, 0);
			}
		}
		double dx = look.x - pos.x, dy = look.y - pos.y, dz = look.z - pos.z;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float camYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float camPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
		return new double[] { pos.x, pos.y, pos.z, camYaw, camPitch };
	}
}
