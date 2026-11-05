package com.projecthero.mod.client.omnitrix;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;

/**
 * v0.17.2: an {@link AlienClientKit} that also moves the WHOLE body in a move -- a spin, a dive, a squash -- which limb poses cannot do (a
 * model pose only turns the torso: user, of Ditto's kick, "right now his torso just twists"). {@code mixin/OmnitrixBodyTurnMixin} calls
 * it at the end of the renderer's rotations: y up, feet at the origin, 1.8 tall, facing -z. Driven by the synced anim, so every
 * viewer sees it; the first-person camera is untouched.
 */
public interface AlienBodyTurn {
	void bodyTurn(AbstractClientPlayer p, PoseStack pose, float partial);
}
