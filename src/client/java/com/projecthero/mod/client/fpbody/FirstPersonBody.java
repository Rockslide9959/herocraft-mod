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

	/**
	 * Called around the world render of the local player -- or of a {@link #companion} -- while {@link #active}. The head
	 * frame is kept between frames (a companion may be drawn before the player in the entity loop; the player's own
	 * earliest quads come before this frame's capture) and dropped once the view ends ({@link #inactive}).
	 */
	public static void begin(Entity entity, CullingBufferSource source) {
		rendering = entity;
		renderingSource = source;
	}

	public static void end() {
		rendering = null;
		renderingSource = null;
	}

	/**
	 * The culling source while the body (or a companion) is being drawn, else null. GeckoLib's armour renderer ignores the
	 * buffer source it is handed and takes the game's main one, so {@code mixin.FirstPersonBodyGeoArmorMixin} swaps this in.
	 */
	public static CullingBufferSource renderingSource() {
		return renderingSource;
	}

	private static CullingBufferSource renderingSource;

	/** The full-body view is off this frame: forget the head frame. */
	public static void inactive() {
		headInverse = null;
		neckFrame = null;
	}

	/**
	 * An entity drawn in the wearer's space while the view is on: anything standing in (or hugging) the wearer's body --
	 * the suit being stepped into (Mark 8 Sentry Mode), Max Steel's Steel merging in, a courier locking a piece on. It is
	 * shifted with the body and has the head volume culled too, so it stays lined up with the body and never sits over the
	 * lens.
	 */
	public static boolean companion(Entity entity, Entity self) {
		if (entity == self || entity instanceof net.minecraft.world.entity.player.Player || entity == self.getVehicle()) {
			return false;
		}
		double dx = entity.getX() - self.getX();
		double dz = entity.getZ() - self.getZ();
		double dy = entity.getY() - self.getY();
		return dx * dx + dz * dz < COMPANION_RADIUS * COMPANION_RADIUS && dy > -0.75 && dy < self.getBbHeight() + 0.5;
	}

	/** How close (horizontally, blocks) an entity must be to the wearer to count as a {@link #companion}. */
	private static final double COMPANION_RADIUS = 0.8;

	public static boolean renderingSelf(Entity entity) {
		return entity != null && entity == rendering && entity == Minecraft.getInstance().player;
	}

	/** After setupAnim: remember the head's frame so the head volume can be culled ({@link CullingBufferSource}). */
	public static void captureHead(PoseStack modelSpace, ModelPart head, Entity entity, float partialTick) {
		PoseStack tmp = new PoseStack();
		tmp.last().pose().set(modelSpace.last().pose());
		head.translateAndRotate(tmp);
		Matrix4f headFrame = tmp.last().pose();
		headInverse = headFrame.invert(new Matrix4f());
		org.joml.Vector3f neck = headFrame.transformPosition(0f, 0f, 0f, new org.joml.Vector3f());
		double r = Math.toRadians(bodyYaw(entity, partialTick));
		// upright neck frame: origin at the neck pivot, z = the body's facing, y = world up
		neckFrame = new Matrix4f().translation(neck).rotateY((float) -r).invert();
	}

	/** The head frame's inverse (this frame's, or the last one's), or null before the first capture. */
	static Matrix4f headInverse() {
		return headInverse;
	}

	/** World -> upright neck frame (z forward along the body, y up), or null before the first capture. */
	static Matrix4f neckFrame() {
		return neckFrame;
	}

	private static Matrix4f neckFrame;

	private static float bodyYaw(Entity entity, float partialTick) {
		return entity instanceof net.minecraft.world.entity.LivingEntity le
				? Mth.rotLerp(partialTick, le.yBodyRotO, le.yBodyRot) : entity.getViewYRot(partialTick);
	}

	/** Where (relative to its true spot) the body is drawn: {@link #BACK} blocks behind, along the body's facing. */
	public static Vec3 offset(Entity entity, float partialTick) {
		double r = Math.toRadians(bodyYaw(entity, partialTick));
		// facing (yaw) -> look vector (-sin, 0, cos); behind is the opposite
		return new Vec3(Math.sin(r) * BACK, 0.0, -Math.cos(r) * BACK);
	}
}
