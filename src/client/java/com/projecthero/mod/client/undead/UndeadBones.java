package com.projecthero.mod.client.undead;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * v0.17.3: shared bone geometry for the Undead's Rib Shotgun (tap V: the ribs springing out of his chest, the spikes in flight)
 * and Spine Whip (tap Shift+V: the vertebrae of the whip). Everything is built out of small boxes turned to lie along a curve,
 * in model px (1/16 block) round whatever the pose stack is at, tinted from the palette's white texel like
 * {@link UndeadBoneSwordRender#box}.
 */
final class UndeadBones {
	/** Bone, lit and in shadow, a dry break, the marrow at a fresh break. */
	static final int BONE_HI = 0xF3EEDD, BONE = UndeadBoneSwordRender.BONE, BONE_SHADE = UndeadBoneSwordRender.BONE_SHADE,
			BONE_DARK = UndeadBoneSwordRender.BONE_DARK, MARROW = 0x7E2A22;

	private UndeadBones() {
	}

	/** {@code a} to {@code b} by {@code t} (0..1), per channel. */
	static int mix(int a, int b, float t) {
		t = Mth.clamp(t, 0f, 1f);
		int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return (r << 16) | (g << 8) | bl;
	}

	/**
	 * A bar from {@code a} to {@code b} (px), {@code w} across and {@code h} tall, its top toward {@code up} (the top face is the
	 * lit one), each end pushed out by {@code over} so a chain of them has no gaps at the bends.
	 */
	static void bar(VertexConsumer vc, PoseStack pose, Vector3f a, Vector3f b, Vector3f up, float w, float h, float over, int rgb, int light) {
		Vector3f d = new Vector3f(b).sub(a);
		float len = d.length();
		if (len < 1.0e-4f) {
			return;
		}
		d.div(len);
		Vector3f x = new Vector3f(d).cross(up);
		if (x.lengthSquared() < 1.0e-6f) {
			x = new Vector3f(d).cross(Math.abs(d.x) < 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 0, 1));
		}
		x.normalize();
		Vector3f y = new Vector3f(d).cross(x).normalize(); // about -up: box()'s -y face is the bright one
		pose.pushPose();
		pose.translate(a.x / 16f, a.y / 16f, a.z / 16f);
		pose.mulPose(new Quaternionf().setFromNormalized(new Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, d.x, d.y, d.z)));
		UndeadBoneSwordRender.box(vc, pose.last(), -w / 2f, -h / 2f, -over, w / 2f, h / 2f, len + over, rgb, light);
		pose.popPose();
	}

	/** A box of size {@code w x h x l} centred on {@code c}, lying along {@code d}, its top toward {@code up}. */
	static void lump(VertexConsumer vc, PoseStack pose, Vector3f c, Vector3f d, Vector3f up, float w, float h, float l, int rgb, int light) {
		Vector3f n = new Vector3f(d).normalize().mul(l / 2f);
		bar(vc, pose, new Vector3f(c).sub(n), new Vector3f(c).add(n), up, w, h, 0f, rgb, light);
	}

	/**
	 * A rib along {@code pts} (px, root first): a flat band of bone {@code h0 -> h1} tall and {@code w0 -> w1} thick, pale on top
	 * and shaded along its lower edge, going a little darker toward the end. From {@code bloodFrom} (0..1 along it) it is soaked
	 * red toward a broken end -- splintered, the marrow showing, a drop hanging off it -- or, {@code pointed}, it narrows to a
	 * sharp point instead. {@code up} is the band's broad side's "up".
	 */
	static void rib(VertexConsumer vc, PoseStack pose, Vector3f[] pts, Vector3f up, float h0, float h1, float w0, float w1, float bloodFrom, boolean pointed,
			int light) {
		int n = pts.length - 1;
		Vector3f dn = new Vector3f(up).normalize();
		for (int j = 0; j < n; j++) {
			float t = (j + 0.5f) / n;
			float h = Mth.lerp(t, h0, h1), w = Mth.lerp(t, w0, w1);
			if (pointed && j == n - 1) {
				h *= 0.75f;
				w *= 0.85f;
			}
			int col = mix(BONE, BONE_SHADE, t * 0.8f);
			int hi = mix(BONE_HI, BONE, t * 0.7f);
			int lo = mix(BONE_SHADE, BONE_DARK, 0.35f + t * 0.4f);
			if (t > bloodFrom) {
				float k = (t - bloodFrom) / Math.max(0.01f, 1f - bloodFrom);
				col = mix(col, UndeadBoneSwordRender.BLOOD, 0.2f + 0.6f * k);
				hi = mix(hi, UndeadBoneSwordRender.BLOOD_WET, 0.15f + 0.6f * k);
				lo = mix(lo, UndeadBoneSwordRender.BLOOD_DARK, 0.3f + 0.6f * k);
			}
			Vector3f a = pts[j], b = pts[j + 1];
			bar(vc, pose, a, b, up, w, h, 0.06f, col, light);
			// the lit top edge and the shaded under edge (a band, not a stick)
			Vector3f top = new Vector3f(dn).mul(h * 0.36f), bot = new Vector3f(dn).mul(-h * 0.36f);
			bar(vc, pose, new Vector3f(a).add(top), new Vector3f(b).add(top), up, w * 1.04f, h * 0.22f, 0.04f, hi, light);
			bar(vc, pose, new Vector3f(a).add(bot), new Vector3f(b).add(bot), up, w * 1.06f, h * 0.26f, 0.04f, lo, light);
		}
		Vector3f end = pts[n];
		Vector3f dir = new Vector3f(end).sub(pts[n - 1]).normalize();
		Vector3f side = new Vector3f(dir).cross(dn).normalize();
		if (pointed) {
			// the point: two ever smaller steps
			float h = h1 * 0.55f, w = w1 * 0.6f;
			Vector3f p1 = new Vector3f(end).add(new Vector3f(dir).mul(h1 * 1.1f));
			bar(vc, pose, end, p1, up, w, h, 0.03f, BONE_SHADE, light);
			Vector3f p2 = new Vector3f(p1).add(new Vector3f(dir).mul(h1 * 0.9f));
			bar(vc, pose, p1, p2, up, w * 0.5f, h * 0.45f, 0.02f, BONE_DARK, light);
			return;
		}
		// the break: a longer and a shorter splinter, the marrow between, wet blood over it and a drop hanging off
		float h = h1, w = w1;
		Vector3f s1 = new Vector3f(end).add(new Vector3f(dir).mul(h * 1.0f)).add(new Vector3f(dn).mul(h * 0.22f)).add(new Vector3f(side).mul(w * 0.1f));
		bar(vc, pose, end, s1, up, w * 0.7f, h * 0.38f, 0.02f, mix(BONE_SHADE, UndeadBoneSwordRender.BLOOD_WET, 0.45f), light);
		Vector3f s2 = new Vector3f(end).add(new Vector3f(dir).mul(h * 0.55f)).add(new Vector3f(dn).mul(-h * 0.25f)).add(new Vector3f(side).mul(-w * 0.1f));
		bar(vc, pose, end, s2, up, w * 0.75f, h * 0.36f, 0.02f, mix(BONE_DARK, UndeadBoneSwordRender.BLOOD, 0.5f), light);
		lump(vc, pose, new Vector3f(end).add(new Vector3f(dir).mul(h * 0.12f)), dir, up, w * 0.8f, h * 0.55f, h * 0.3f, MARROW, light);
		lump(vc, pose, new Vector3f(end).add(new Vector3f(dn).mul(-h * 0.55f)).add(new Vector3f(dir).mul(-h * 0.1f)), new Vector3f(dn).negate(), up, w * 0.55f,
				w * 0.55f, h * 0.6f, UndeadBoneSwordRender.BLOOD_WET, light);
	}
}
