package com.projecthero.mod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * v0.17.3: the parts of a humanoid model's pose that vanilla's {@code setupAnim} never writes, put back to the rest pose
 * before every player's animation ({@code HumanoidModelMixin#projecthero$resetRoll}). Vanilla 1.21.1 writes head x/y, body
 * x/y, all four limbs' x/y/z rotations, the arms' x/z pivots and every y pivot each frame -- not the head's or body's roll
 * ({@code zRot}). The player model is shared, so a pose that rolls them (Queen Bee's haughty head tilt at the end of her long
 * transformation, the Miraculous / Omnitrix / Stand / move poses that lean the torso sideways) would otherwise keep the roll
 * forever -- the tester's "broken neck". A new pose that writes anything else vanilla leaves alone belongs here too.
 */
public final class PoseRest {
	private PoseRest() {
	}

	public static void resetUnmanaged(HumanoidModel<?> m) {
		rest(m.head);
		rest(m.hat);
		rest(m.body);
	}

	private static void rest(ModelPart part) {
		part.zRot = part.getInitialPose().zRot;
	}
}
