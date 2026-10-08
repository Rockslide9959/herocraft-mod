package com.projecthero.mod.client.fpbody;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15, user request ("during every suitup sequence ... the player's entire character model becomes visible in first
 * person so that the player can watch the suitup"): the <b>full-body first person</b> view.
 *
 * <p>While the local player is in first person and any registered suit-up / suit-down sequence reports itself running
 * ({@link #register}, all of them in {@link FirstPersonBodySequences}), the camera stays at the eyes but:
 * <ul>
 *   <li>the player's own model is drawn in the world pass like a third-person view ({@code mixin.FirstPersonBodyLevelMixin}
 *       flips the camera's "detached" test for the entity loop only), with every layer -- armour, GeckoLib suits, the
 *       forming effects, held items, keyframed poses -- exactly as everyone else sees it;</li>
 *   <li>it is drawn {@link #BACK} blocks behind its true spot along the body's facing, so looking down shows the chest,
 *       arms and legs instead of the top of the shoulders (at the true spot the camera sits right over the neck);</li>
 *   <li>everything inside the head's volume -- the head, hat, any helmet, glasses, masks, GeckoLib head bones -- is
 *       culled quad by quad ({@link CullingBufferSource}, in the head's own frame captured by
 *       {@code mixin.FirstPersonBodyHeadMixin}), so nothing sits over the lens;</li>
 *   <li>the normal first-person hands / held item are not drawn ({@code mixin.FirstPersonBodyHandsMixin}) -- the real
 *       arms are in view instead.</li>
 * </ul>
 */
public final class FirstPersonBody {
	/** How far behind its true spot (along the body's facing) the body is drawn, in blocks. */
	public static final double BACK = 0.32;

	private static final List<Predicate<AbstractClientPlayer>> SEQUENCES = new CopyOnWriteArrayList<>();

	/** The entity being drawn as the full first-person body right now (render thread), or null. */
	private static Entity rendering;
	/** Head frame -> model space inverse, captured after the body's setupAnim this frame (null until then). */
	private static Matrix4f headInverse;

	private FirstPersonBody() {
	}

	/** Registers one suit-up / suit-down sequence: true while it is running on that player. */
	public static void register(Predicate<AbstractClientPlayer> sequenceRunning) {
		SEQUENCES.add(sequenceRunning);
	}

	/** Is any registered sequence running on {@code player}? */
	public static boolean sequenceRunning(AbstractClientPlayer player) {
		for (Predicate<AbstractClientPlayer> p : SEQUENCES) {
			try {
				if (p.test(player)) {
					return true;
				}
			} catch (RuntimeException e) {
				ProjectHeroMod.LOGGER.debug("first-person body predicate failed", e);
			}
		}
		return false;
	}

	/** Should the local player's body be drawn in first person this frame? */
	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.getCameraEntity() != p || mc.options.getCameraType() != CameraType.FIRST_PERSON
				|| p.isSpectator() || p.isSleeping()) {
			return false;
		}
		return sequenceRunning(p);
	}

	// ------------------------------------------------------------------ render-thread state (mixins)

	/** Called around the local player's world render while {@link #active}. */
	public static void begin(Entity entity) {
		rendering = entity;
		headInverse = null;
	}

	public static void end() {
		rendering = null;
		headInverse = null;
	}

	public static boolean renderingSelf(Entity entity) {
		return entity != null && entity == rendering;
	}

	/** After setupAnim: remember the head's frame so the head volume can be culled ({@link CullingBufferSource}). */
	public static void captureHead(PoseStack modelSpace, ModelPart head) {
		PoseStack tmp = new PoseStack();
		tmp.last().pose().set(modelSpace.last().pose());
		head.translateAndRotate(tmp);
		headInverse = tmp.last().pose().invert(new Matrix4f());
	}

	/** The head frame's inverse, or null if it has not been captured (yet) this render. */
	static Matrix4f headInverse() {
		return headInverse;
	}

	/** Where (relative to its true spot) the body is drawn: {@link #BACK} blocks behind, along the body's facing. */
	public static Vec3 offset(Entity entity, float partialTick) {
		float bodyYaw = entity instanceof net.minecraft.world.entity.LivingEntity le
				? Mth.rotLerp(partialTick, le.yBodyRotO, le.yBodyRot) : entity.getViewYRot(partialTick);
		double r = Math.toRadians(bodyYaw);
		// facing (yaw) -> look vector (-sin, 0, cos); behind is the opposite
		return new Vec3(Math.sin(r) * BACK, 0.0, -Math.cos(r) * BACK);
	}
}
