package com.projecthero.mod.client.render;

import static com.projecthero.mod.client.render.TitanAnimations.*;

import com.projecthero.mod.titan.entity.TitanEntity;

import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * v0.14.4: the Titan's animated body. Still the vanilla zombie rig and texture (so the art can be swapped
 * without touching this), but posed every frame from {@link TitanAnimations}:
 * <ul>
 * <li><b>Walking</b>: a slowed, heavy gait -- vanilla's cycle scaled for an 18-block stride (it used to scurry
 * like an ordinary zombie), with a side-to-side sway, a bob on each footfall and a slight counter-swing of the
 * outstretched arms.</li>
 * <li><b>Attacks</b>: the synced clip ({@link TitanEntity#clientAnim()}) layered on top and cross-faded from
 * whatever was playing before.</li>
 * </ul>
 * The torso controls pivot the head, body and arms around the hips so bends, twists and side-leans read as one
 * body moving, not parts sliding.
 */
public class TitanModel extends ZombieModel<TitanEntity> {
	/** 18-block legs take ~2.5x longer per stride than a zombie's at the same limb-swing value. */
	private static final float GAIT_SCALE = 0.4f;
	private static final float HIP_Y = 12.0f;

	private final float[] base = new float[N];
	private final float[] pose = new float[N];

	public TitanModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(TitanEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch) {
		float gait = limbSwing * GAIT_SCALE;
		float amount = Math.min(1.0f, limbSwingAmount);
		super.setupAnim(entity, gait, amount * 0.85f, ageInTicks, netHeadYaw, headPitch);

		float phase = gait * 0.6662f;
		base[HX] = head.xRot;
		base[HY] = head.yRot;
		base[HZ] = 0.0f;
		base[RX] = rightArm.xRot - Mth.cos(phase) * 0.25f * amount;
		base[RY] = rightArm.yRot;
		base[RZ] = rightArm.zRot;
		base[LX] = leftArm.xRot + Mth.cos(phase) * 0.25f * amount;
		base[LY] = leftArm.yRot;
		base[LZ] = leftArm.zRot;
		base[RLX] = rightLeg.xRot;
		base[RLY] = rightLeg.yRot;
		base[RLZ] = rightLeg.zRot;
		base[LLX] = leftLeg.xRot;
		base[LLY] = leftLeg.yRot;
		base[LLZ] = leftLeg.zRot;
		base[BEND] = 0.06f * amount; // a slight forward lean into the walk
		base[TWIST] = Mth.cos(phase) * 0.08f * amount;
		base[ROLL] = Mth.cos(phase) * 0.05f * amount;
		base[DROP] = Math.abs(Mth.cos(phase)) * 0.7f * amount + Mth.sin(ageInTicks * 0.08f) * 0.12f;
		base[W] = 0.0f;

		TitanAnimations.evaluate(entity, ageInTicks, base, pose);
		apply(pose);
	}

	private void apply(float[] p) {
		float bend = p[BEND];
		float twist = p[TWIST];
		float roll = p[ROLL];
		float drop = p[DROP];

		place(body, 0.0f, 0.0f, 0.0f, bend, twist, roll, drop);
		body.xRot = bend;
		body.yRot = twist;
		body.zRot = roll;

		place(head, 0.0f, 0.0f, 0.0f, bend, twist, roll, drop);
		head.xRot = p[HX] + bend;
		head.yRot = p[HY]; // the head keeps tracking its target rather than turning with the shoulders
		head.zRot = p[HZ] + roll;

		place(rightArm, -5.0f, 2.0f, 0.0f, bend, twist, roll, drop);
		rightArm.xRot = p[RX] + bend;
		rightArm.yRot = p[RY] + twist;
		rightArm.zRot = p[RZ] + roll;

		place(leftArm, 5.0f, 2.0f, 0.0f, bend, twist, roll, drop);
		leftArm.xRot = p[LX] + bend;
		leftArm.yRot = p[LY] + twist;
		leftArm.zRot = p[LZ] + roll;

		// Legs stay planted: only a fraction of the drop, so a crouch sinks the hips without burying the feet.
		rightLeg.x = -1.9f;
		rightLeg.y = HIP_Y + drop * 0.25f;
		rightLeg.z = 0.0f;
		rightLeg.xRot = p[RLX];
		rightLeg.yRot = p[RLY];
		rightLeg.zRot = p[RLZ];
		leftLeg.x = 1.9f;
		leftLeg.y = HIP_Y + drop * 0.25f;
		leftLeg.z = 0.0f;
		leftLeg.xRot = p[LLX];
		leftLeg.yRot = p[LLY];
		leftLeg.zRot = p[LLZ];

		hat.copyFrom(head);
	}

	/**
	 * Position an upper-body part whose rest pivot is (x, y, z): rotate it about the hip joint by the torso's
	 * twist (Y), then bend (X), then roll (Z), and lower it by {@code drop}.
	 */
	private static void place(ModelPart part, float x, float y, float z, float bend, float twist, float roll, float drop) {
		float vx = x;
		float vy = y - HIP_Y;
		float vz = z;
		// twist about Y
		float cy = Mth.cos(twist);
		float sy = Mth.sin(twist);
		float tx = vx * cy + vz * sy;
		float tz = -vx * sy + vz * cy;
		vx = tx;
		vz = tz;
		// bend about X
		float cx = Mth.cos(bend);
		float sx = Mth.sin(bend);
		float by = vy * cx - vz * sx;
		float bz = vy * sx + vz * cx;
		vy = by;
		vz = bz;
		// roll about Z
		float cz = Mth.cos(roll);
		float sz = Mth.sin(roll);
		float rx = vx * cz - vy * sz;
		float ry = vx * sz + vy * cz;
		part.x = rx;
		part.y = HIP_Y + ry + drop;
		part.z = vz;
	}
}
