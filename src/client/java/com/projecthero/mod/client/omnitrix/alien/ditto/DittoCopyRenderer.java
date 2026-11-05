package com.projecthero.mod.client.omnitrix.alien.ditto;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixLayer;
import com.projecthero.mod.client.omnitrix.alien.DittoClient;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.ditto.DittoCopyEntity;
import com.projecthero.mod.omnitrix.alien.ditto.DittoKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: one of Ditto's copies -- the wide player model in Ditto's skin at exactly his size, the Omnitrix badge on its forehead
 * like his (in the show every copy wears the symbol), running when it moves, punching with either fist.
 * <p>
 * v0.17.2 round 3: every part of a copy's life reads. Coming out it strains away from him, stretched long, a {@link DittoStrand} of his
 * own skin necking between them until it snaps and the copy stumbles clear (user: "make the clones almost stretch and pull themselves
 * out of the player and separate from the player"). Going back in it walks up, reaches out and is drawn in along the same strand.
 * Thrown it tumbles; leaping it flies; on a Dogpile it hangs on. The whole pack spins with his Pinwheel Kick.
 */
public class DittoCopyRenderer extends EntityRenderer<DittoCopyEntity> {
	/** How far a copy's middle sits over its feet, and his (blocks). */
	private static final double MIDDLE = 0.5;

	private final PlayerModel<AbstractClientPlayer> model;

	public DittoCopyRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.model = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
		this.shadowRadius = 0.2f;
	}

	@Override
	public ResourceLocation getTextureLocation(DittoCopyEntity e) {
		return DittoClient.SKIN;
	}

	private void resetPose() {
		model.young = false; // a model's young flag starts TRUE (only the living renderer clears it): left on, a copy is drawn as a baby
		for (ModelPart part : new ModelPart[] { model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg,
				model.jacket, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants }) {
			part.resetPose();
			part.visible = true;
		}
	}

	/** Its run, or a slow walk (a copy on its way back in walks). */
	private void gait(DittoCopyEntity e, float age) {
		double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
		float speed = (float) Math.min(1.0, Math.sqrt(dx * dx + dz * dz) * 5.0);
		boolean walking = e.state() == DittoCopyEntity.RETURNING;
		float phase = age * (walking ? 0.6f : 0.9f);
		float swing = walking ? 0.75f : 1.3f;
		model.rightArm.xRot = Mth.cos(phase + Mth.PI) * swing * speed;
		model.leftArm.xRot = Mth.cos(phase) * swing * speed;
		model.rightLeg.xRot = Mth.cos(phase) * (swing + 0.1f) * speed;
		model.leftLeg.xRot = Mth.cos(phase + Mth.PI) * (swing + 0.1f) * speed;
		model.rightArm.zRot = Mth.cos(age * 0.09f) * 0.05f + 0.05f;
		model.leftArm.zRot = -(Mth.cos(age * 0.09f) * 0.05f + 0.05f);
	}

	/** A jab with whichever fist is next, the shoulder behind it (6 ticks). */
	private void jab(DittoCopyEntity e, float swing) {
		float k = Mth.sin(swing / 6f * Mth.PI);
		boolean left = (e.swingAt + e.slot()) % 2L == 0L;
		ModelPart arm = left ? model.leftArm : model.rightArm;
		arm.xRot = -1.7f * k;
		arm.yRot = (left ? -0.2f : 0.2f) * k;
		model.body.yRot = (left ? -0.35f : 0.35f) * k;
	}

	/** The limbs for its state; false when it is just the pack's run. */
	private boolean statePose(DittoCopyEntity e, int state, float t, float u) {
		switch (state) {
			case DittoCopyEntity.EMERGING -> DittoClient.pulling(model, u);
			case DittoCopyEntity.ABSORBING -> DittoClient.sinking(model, u);
			case DittoCopyEntity.HOISTED -> {
				// stiff as a plank on his hands, then arms out for the throw
				model.rightArm.xRot = -2.9f;
				model.leftArm.xRot = -2.9f;
				model.rightLeg.xRot = 0.05f;
				model.leftLeg.xRot = -0.05f;
			}
			case DittoCopyEntity.FLYING -> {
				// flung: spread like a star, limbs flailing
				model.rightArm.zRot = 1.9f + Mth.sin(t * 1.3f) * 0.35f;
				model.leftArm.zRot = -1.9f - Mth.sin(t * 1.1f) * 0.35f;
				model.rightLeg.zRot = 0.5f + Mth.cos(t * 1.2f) * 0.2f;
				model.leftLeg.zRot = -0.5f - Mth.cos(t * 1.4f) * 0.2f;
			}
			case DittoCopyEntity.ARC -> {
				// up with its knees tucked, then arms out in front for the dive
				float dive = Mth.clamp((u - 0.4f) / 0.3f, 0f, 1f);
				model.rightArm.xRot = Mth.lerp(dive, -0.7f, -2.9f);
				model.leftArm.xRot = Mth.lerp(dive, -0.7f, -2.9f);
				model.rightLeg.xRot = Mth.lerp(dive, -1.2f, 0.25f);
				model.leftLeg.xRot = Mth.lerp(dive, -0.9f, 0.05f);
			}
			case DittoCopyEntity.CLINGING -> {
				// hanging on with everything, its legs kicking
				model.rightArm.xRot = -1.9f;
				model.rightArm.zRot = -0.25f;
				model.leftArm.xRot = -1.9f;
				model.leftArm.zRot = 0.25f;
				model.rightLeg.xRot = -0.6f + Mth.sin(t * 0.8f + e.slot()) * 0.4f;
				model.leftLeg.xRot = -0.6f - Mth.sin(t * 0.8f + e.slot()) * 0.4f;
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** The whole body for its state: y up, feet at the origin, 1.8 tall, facing -z. */
	private void stateTurn(DittoCopyEntity e, PoseStack pose, int state, float t, float u) {
		switch (state) {
			case DittoCopyEntity.EMERGING -> {
				// it starts small inside him, comes out stretched long, straining forward; once it parts it bounces back into shape
				float grow = Mth.lerp(Mth.clamp(u / 0.45f, 0f, 1f), 0.62f, 1f);
				float stretch = Mth.sin(Mth.clamp((u - 0.08f) / 0.74f, 0f, 1f) * Mth.PI);
				float lean = Mth.sin(Mth.clamp((u - 0.05f) / 0.85f, 0f, 1f) * Mth.PI) * 26f;
				float bounce = u > 0.8f ? Mth.sin((u - 0.8f) / 0.2f * Mth.TWO_PI) * (1f - (u - 0.8f) / 0.2f) * 0.2f : 0f;
				pose.scale(grow * (1f - 0.12f * stretch + bounce * 0.5f), grow * (1f + 0.06f * stretch - bounce), grow * (1f + 0.42f * stretch + bounce * 0.5f));
				pivot(pose, -lean);
			}
			case DittoCopyEntity.ABSORBING -> {
				// drawn in head first, thinner and thinner, into his side
				float k = u * u;
				pose.scale(1f - 0.62f * k, 1f - 0.62f * k, (1f - 0.62f * k) * (1f + 0.9f * Mth.sin(u * Mth.PI)));
				pivot(pose, -30f * Mth.sin(u * Mth.PI));
			}
			case DittoCopyEntity.HOISTED -> pivot(pose, -90f * Mth.clamp(u * 2f, 0f, 1f));
			case DittoCopyEntity.FLYING -> pivot(pose, -t * 34f);
			case DittoCopyEntity.ARC -> {
				// the climb, then head first into the dive
				pivot(pose, Mth.lerp(Mth.clamp((u - 0.35f) / 0.4f, 0f, 1f), 12f, -80f));
			}
			default -> {
			}
		}
	}

	/** A pitch about its middle (negative = forward). */
	private static void pivot(PoseStack pose, float degrees) {
		pose.translate(0f, 0.9f, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(degrees));
		pose.translate(0f, -0.9f, 0f);
	}

	@Override
	public void render(DittoCopyEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		int state = e.state();
		float t = e.stateTicks(partial);
		if (state == DittoCopyEntity.EMERGING && t < 0f) {
			return; // waiting its turn to come out (the Ditto Army)
		}
		float u = Mth.clamp(t / Math.max(1, e.stateLength()), 0f, 1f);
		Player owner = e.owner();
		// looking out of his own eyes: nothing drawn round the camera, the strand shows it coming out
		boolean close = owner == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson()
				&& e.getPosition(partial).distanceToSqr(owner.getPosition(partial)) < 0.09;
		resetPose();
		float age = e.tickCount + partial;
		gait(e, age);
		boolean posed = statePose(e, state, t, u);
		if (!posed) {
			if (e.level().getGameTime() - e.heaveAt + partial < 8f) {
				DittoClient.heave(model, e.level().getGameTime() - e.heaveAt + partial);
			} else if (!e.onGround() && e.getY() - e.yo > 0.05) {
				// climbing: hand over hand
				float climb = age * 0.8f;
				model.rightArm.xRot = -2.6f + Mth.sin(climb) * 0.4f;
				model.leftArm.xRot = -2.6f - Mth.sin(climb) * 0.4f;
				model.rightLeg.xRot = -Mth.sin(climb) * 0.6f;
				model.leftLeg.xRot = Mth.sin(climb) * 0.6f;
			}
		}
		// a punch
		float swing = e.level().getGameTime() - e.swingAt + partial;
		if (swing >= 0 && swing < 6) {
			jab(e, swing);
		}
		// his Pinwheel Kick: the pack spins with him
		float spinTicks = -1f;
		if (!posed && owner instanceof AbstractClientPlayer cp && Omnitrix.hasPower(cp)) {
			OmnitrixState s = Omnitrix.state(cp);
			if (DittoKit.ID.equals(s.active) && s.transformed() && s.animId == DittoKit.ANIM_KICK) {
				spinTicks = cp.level().getGameTime() - s.animStart + partial;
				DittoClient.posed(model, s.animId, spinTicks);
			}
		}
		model.hat.copyFrom(model.head);
		model.jacket.copyFrom(model.body);
		model.leftSleeve.copyFrom(model.leftArm);
		model.rightSleeve.copyFrom(model.rightArm);
		model.leftPants.copyFrom(model.leftLeg);
		model.rightPants.copyFrom(model.rightLeg);

		float size = owner != null && DittoKit.isDitto(owner) ? owner.getScale() : DittoClient.SIZE;
		// the strand between them, in world axes
		if (owner != null && (state == DittoCopyEntity.EMERGING || state == DittoCopyEntity.ABSORBING)) {
			strand(e, owner, state, t, u, partial, size, pose, buffers, light);
		}
		if (close) {
			return;
		}
		pose.pushPose();
		float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
		pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
		pose.scale(size, size, size);
		stateTurn(e, pose, state, t, u);
		if (spinTicks >= 0f) {
			DittoClient.turn(pose, DittoKit.ANIM_KICK, spinTicks);
		}
		pose.scale(-0.9375f, -0.9375f, 0.9375f);
		pose.translate(0.0, -1.501, 0.0);
		int overlay = e.hurtTime() > 0 ? OverlayTexture.pack(0, 3) : OverlayTexture.NO_OVERLAY;
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(DittoClient.SKIN)), light, overlay, -1);
		// the Omnitrix badge on its forehead, like his (green on the recalibrated watch)
		boolean recal = owner != null && Omnitrix.recalibrated(owner);
		float[] b = DittoClient.BADGE;
		AlienParts.on(pose, model.head, () -> {
			var vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(recal ? OmnitrixLayer.BADGE_RECAL : OmnitrixLayer.BADGE));
			float h = b[3] / 2f / 16f, y = b[1] / 16f, x = b[0] / 16f, z = b[2] / 16f;
			AlienParts.quad(vc, pose.last(), 0xFFFFFFFF, LightTexture.FULL_BRIGHT, x - h, y - h, z, x + h, y - h, z, x + h, y + h, z, x - h, y + h, z, 0, 0, -1,
					0f, 0f, 1f, 1f);
		});
		pose.popPose();
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	/** The strand from his middle to the copy's: thick as it starts, necking as it pulls, snapping near the end. */
	private void strand(DittoCopyEntity e, Player owner, int state, float t, float u, float partial, float size, PoseStack pose, MultiBufferSource buffers,
			int light) {
		float scale = size / DittoClient.SIZE;
		Vec3 me = e.getPosition(partial);
		Vec3 a = owner.getPosition(partial).subtract(me).add(0, MIDDLE * scale, 0);
		Vec3 b = new Vec3(0, MIDDLE * scale * 0.95, 0);
		float neck, snap;
		float radius = 0.17f * scale;
		if (state == DittoCopyEntity.EMERGING) {
			neck = Mth.lerp(Mth.clamp(u / 0.68f, 0f, 1f), 0.95f, 0.3f);
			snap = Mth.clamp((u - 0.68f) / 0.16f, 0f, 1f);
			radius *= 1f + 0.25f * Mth.sin(Mth.clamp(u / 0.3f, 0f, 1f) * Mth.PI); // the bulge in his side as it starts
		} else {
			// it reaches for him and thickens as it goes in
			neck = Mth.lerp(u, 0.3f, 1f);
			snap = 0f;
			radius *= Mth.lerp(u, 0.75f, 0.45f);
		}
		DittoStrand.draw(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(DittoClient.SKIN)), a, b, radius, neck, snap, t, light);
	}
}
