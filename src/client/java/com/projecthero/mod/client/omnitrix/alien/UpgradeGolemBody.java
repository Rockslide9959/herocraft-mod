package com.projecthero.mod.client.omnitrix.alien;

import static com.projecthero.mod.client.omnitrix.alien.UpgradeMachineBody.BAND;
import static com.projecthero.mod.client.omnitrix.alien.UpgradeMachineBody.CIRCUIT;
import static com.projecthero.mod.client.omnitrix.alien.UpgradeMachineBody.CIRCUIT2;
import static com.projecthero.mod.client.omnitrix.alien.UpgradeMachineBody.GRILLE;
import static com.projecthero.mod.client.omnitrix.alien.UpgradeMachineBody.JOINT;
import static com.projecthero.mod.client.omnitrix.alien.UpgradeMachineBody.LENS;
import static com.projecthero.mod.client.omnitrix.alien.UpgradeMachineBody.SCREEN;
import static com.projecthero.mod.client.omnitrix.alien.UpgradeMachineBody.WHITE;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.omnitrix.alien.UpgradeMachineBody.Draw;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "with upgrade when the player merges with the iron golem cant you create a cool new iron golem model? right now
 * its just a weird texture overlay with the golem ... make it look cool and good please"): the iron golem Upgrade pilots, once
 * his coat has flowed over it, is drawn as its UPGRADED form instead -- a war mech in the machines' style
 * ({@link UpgradeMachineBody}'s atlas: white plating, black panels with his lime circuit traces, green lenses, the glowing copy
 * on top). A head with his single green eye and twin antennae, a plated chest round a glowing core, heavy pauldrons, long
 * gauntlet arms with lit knuckles, piston legs with knee lights, two exhaust stacks on the back. It moves like the golem: the
 * legs and arms swing with its walk, both arms come down on a punch (the golem's attack tick), the head follows the pilot's look.
 * It assembles out of the coat with a little overshoot. In first person the pilot sees only its arms and legs (he is inside it).
 */
public final class UpgradeGolemBody {
	/** Ticks the mech takes to assemble out of the coat. */
	private static final float ASSEMBLE = 7f;

	private UpgradeGolemBody() {
	}

	/**
	 * Draws the mech for {@code golem} (world-space render, {@code cam} the camera), {@code since} ticks after it took over from the
	 * plain golem; {@code pilot} gives the look pitch (may be null).
	 */
	static void render(IronGolem golem, @Nullable Player pilot, PoseStack pose, MultiBufferSource buffers, Vec3 cam, float partial, float since) {
		Minecraft mc = Minecraft.getInstance();
		boolean inside = pilot != null && pilot == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
		int light = mc.getEntityRenderDispatcher().getPackedLightCoords(golem, partial);
		Vec3 at = golem.getPosition(partial).subtract(cam);
		float k = Mth.clamp(since / ASSEMBLE, 0f, 1f);
		float grow = k < 1f ? 0.9f + 0.14f * k * k * (3f - 2f * k) : 1f + 0.04f * Math.max(0f, 1f - (since - ASSEMBLE) / 4f);
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		float body = Mth.rotLerp(partial, golem.yBodyRotO, golem.yBodyRot);
		pose.mulPose(Axis.YP.rotationDegrees(180f - body));
		pose.scale(grow, grow, grow);
		pose.scale(-1f, -1f, 1f);
		pose.translate(0f, -1.501f, 0f);
		Frame f = new Frame(golem, pilot, partial, inside);
		model(new Draw(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(UpgradeMachineBody.MACHINE)), light, false), f);
		model(new Draw(pose, buffers.getBuffer(RenderType.eyes(UpgradeMachineBody.MACHINE_GLOW)), LightTexture.FULL_BRIGHT, true), f);
		pose.popPose();
	}

	/** What a frame of the model needs (the vanilla iron golem's own animation inputs). */
	private record Frame(IronGolem g, @Nullable Player pilot, float partial, boolean inside) {
		float walk() {
			return g.walkAnimation.position(partial);
		}

		float speed() {
			return Math.min(1f, g.walkAnimation.speed(partial));
		}

		/** Leg swing like the vanilla golem's. */
		float leg(boolean right) {
			return (right ? -1.5f : 1.5f) * Mth.triangleWave(walk(), 13f) * speed();
		}

		/** Arm swing: the punch (both arms come down, as vanilla) or the walk (opposite the legs). */
		float arm(boolean right) {
			int attack = g.getAttackAnimationTick();
			if (attack > 0) {
				return -2.0f + 1.5f * Mth.triangleWave(attack - partial, 10f);
			}
			return ((right ? -0.2f : -0.2f) + (right ? 1.5f : -1.5f) * Mth.triangleWave(walk(), 13f)) * speed();
		}

		float headYaw() {
			return Mth.wrapDegrees(Mth.rotLerp(partial, g.yHeadRotO, g.yHeadRot) - Mth.rotLerp(partial, g.yBodyRotO, g.yBodyRot)) * Mth.DEG_TO_RAD;
		}

		float headPitch() {
			float x = pilot != null ? Mth.lerp(partial, pilot.xRotO, pilot.getXRot()) : Mth.lerp(partial, g.xRotO, g.getXRot());
			return Mth.clamp(x, -40f, 40f) * Mth.DEG_TO_RAD;
		}

		/** A slow breath for the torso (pixels). */
		float breathe() {
			return Mth.sin((g.tickCount + partial) * 0.08f) * 0.25f;
		}
	}

	private static void model(Draw d, Frame f) {
		leg(d, 4.6f, f.leg(false));
		leg(d, -4.6f, f.leg(true));
		arm(d, 1, f.arm(false));
		arm(d, -1, f.arm(true));
		if (f.inside()) {
			return; // he is inside it: the torso and the head would wall off his view
		}
		pelvis(d);
		d.lift(f.breathe(), () -> {
			torso(d);
			d.at(0f, -13f, -1.5f, f.headPitch(), f.headYaw(), () -> head(d));
		});
	}

	/** A piston leg from the hip (y 8) to the ground (y 24): thigh, knee band and light, armoured shin, wide foot. */
	private static void leg(Draw d, float x, float swing) {
		d.at(x, 8f, 0f, swing, 0f, () -> {
			d.box(x - 2.2f, 8f, -2.2f, x + 2.2f, 15f, 2.2f, JOINT);
			d.box(x - 2.6f, 9f, -2.8f, x + 2.6f, 13.6f, -2.2f, CIRCUIT);
			d.box(x - 3.1f, 14.5f, -3.4f, x + 3.1f, 17f, 3.1f, BAND);
			d.box(x - 1.2f, 14.1f, -4f, x + 1.2f, 16.4f, -3.4f, LENS);
			d.box(x - 3.3f, 17f, -3.3f, x + 3.3f, 22.5f, 3.3f, WHITE);
			d.box(x - 2.6f, 17.8f, -3.7f, x + 2.6f, 21.8f, -3.3f, CIRCUIT2);
			d.box(x + (x > 0 ? 3.3f : -3.7f), 18f, -2f, x + (x > 0 ? 3.7f : -3.3f), 21.5f, 2f, GRILLE);
			d.box(x - 3.8f, 22.5f, -5.2f, x + 3.8f, 24f, 3.7f, JOINT);
			d.box(x - 3.9f, 22.1f, -5.6f, x + 3.9f, 23.1f, -4.2f, BAND);
		});
	}

	/** A long golem arm hanging from the shoulder: upper arm, elbow band, gauntlet with a circuit panel, fist with lit knuckles. */
	private static void arm(Draw d, int side, float swing) {
		float px = 12f * side;
		d.at(px, -10.5f, 0f, swing, 0f, () -> {
			box(d, side, 10.2f, -10f, -2.6f, 13.8f, 0.5f, 2.6f, JOINT);
			box(d, side, 9.6f, -0.4f, -3f, 14.4f, 1.6f, 3f, BAND);
			box(d, side, 9f, 1.6f, -3.6f, 15f, 13f, 3.6f, WHITE);
			box(d, side, 15f, 3f, -2.4f, 15.4f, 11.6f, 2.4f, CIRCUIT);
			box(d, side, 9.8f, 3f, -4f, 14.2f, 11f, -3.6f, CIRCUIT2);
			box(d, side, 9.3f, 13f, -3.3f, 14.7f, 18f, 3.3f, SCREEN);
			box(d, side, 9.7f, 14f, -3.8f, 14.3f, 16.2f, -3.3f, LENS);
		});
	}

	/** A box given for the +x side, mirrored for {@code side} -1. */
	private static void box(Draw d, int side, float x0, float y0, float z0, float x1, float y1, float z1, int tile) {
		if (side > 0) {
			d.box(x0, y0, z0, x1, y1, z1, tile);
		} else {
			d.box(-x1, y0, z0, -x0, y1, z1, tile);
		}
	}

	private static void pelvis(Draw d) {
		d.box(-7f, 5.5f, -3.6f, 7f, 10f, 3.6f, JOINT);
		d.box(-7.4f, 5f, -4f, 7.4f, 6.6f, 4f, BAND);
		d.box(-2f, 6.8f, -4.1f, 2f, 9.4f, -3.6f, CIRCUIT);
	}

	/** Chest plating round the glowing core, abdomen circuits, pauldrons, side vents, the power pack and its exhaust stacks. */
	private static void torso(Draw d) {
		d.box(-5.6f, 0.5f, -3.6f, 5.6f, 5.5f, 3.6f, CIRCUIT);
		d.box(-9f, -13f, -5.5f, 9f, 1f, 5.5f, WHITE);
		d.box(-6.2f, -11.2f, -6f, 6.2f, -1f, -5.5f, CIRCUIT2);
		d.box(-3.2f, -9f, -6.4f, 3.2f, -3f, -6f, JOINT);
		d.box(-2.2f, -8.1f, -6.9f, 2.2f, -3.9f, -6.4f, LENS);
		d.box(-9.2f, -0.6f, -5.7f, 9.2f, 1.2f, 5.7f, BAND);
		for (int s = -1; s <= 1; s += 2) {
			box(d, s, 9f, -10.5f, -3f, 9.4f, -2f, 3f, GRILLE);
			// the pauldron
			box(d, s, 8.4f, -15.4f, -5.8f, 15.4f, -8.6f, 5.8f, WHITE);
			box(d, s, 8.2f, -9.4f, -6f, 15.6f, -8.2f, 6f, BAND);
			box(d, s, 15.4f, -14f, -3f, 15.8f, -10f, 3f, CIRCUIT);
			box(d, s, 10.6f, -16.2f, -1.2f, 13.2f, -15.4f, 1.2f, LENS);
			// the exhaust stack
			box(d, s, 2.8f, -18f, 5.6f, 5.6f, -8f, 8.4f, CIRCUIT);
			box(d, s, 2.5f, -18.8f, 5.3f, 5.9f, -18f, 8.7f, GRILLE);
		}
		d.box(-6f, -12f, 5.5f, 6f, -1f, 7.6f, JOINT);
		d.box(-4f, -10f, 7.6f, 4f, -3f, 8f, SCREEN);
		d.box(-6f, -14.2f, -4.6f, 6f, -13f, 4.6f, BAND);
	}

	/** The head: plated, a black visor band with his single green eye, a jaw, ear pods and two antennae with lit tips. */
	private static void head(Draw d) {
		d.box(-4.5f, -21f, -7f, 4.5f, -13f, 1.5f, WHITE);
		d.box(-4.8f, -19f, -7.4f, 4.8f, -15.4f, -6.8f, CIRCUIT);
		d.box(-1.9f, -18.4f, -7.9f, 1.9f, -16.1f, -7.4f, LENS);
		d.box(-4.9f, -20.4f, -7.5f, 4.9f, -19.4f, -6.6f, BAND);
		d.box(-3.4f, -14f, -7.2f, 3.4f, -12.4f, -1f, JOINT);
		for (int s = -1; s <= 1; s += 2) {
			box(d, s, 4.5f, -19f, -5f, 5.6f, -15f, -0.6f, CIRCUIT2);
			box(d, s, 2.6f, -25f, -2.4f, 3.4f, -21f, -1.6f, JOINT);
			box(d, s, 2.3f, -26f, -2.7f, 3.7f, -24.8f, -1.3f, LENS);
		}
	}
}
