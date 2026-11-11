package com.projecthero.mod.client.omnitrix;

import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.sequence.ShowSequenceClient;
import com.projecthero.mod.client.omnitrix.sequence.ShowSequencesClient;
import com.projecthero.mod.client.omnitrix.sequence.Stage;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixLongTransform;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: the client half of {@link OmnitrixLongTransform}, the show-accurate right-click transformation. v0.17.3: a dispatcher --
 * each alien's show is its own {@link ShowSequenceClient} ({@code client/omnitrix/sequence/<Pascal>Show}, found by
 * {@link ShowSequencesClient}); this class finds the playing one and runs the parts every sequence shares:
 * <ul>
 *   <li>the holder's scripted camera ({@link #camera}, {@code OmnitrixSequenceCameraMixin}) and field of view, the HUD hidden;</li>
 *   <li>the body held to the yaw it faced at the start; where the watch was last drawn ({@link #wrist}, for close-ups and glows);</li>
 *   <li>the holder's screen ({@link #renderLast}): the sequence's stage drawn over the world, the depth cleared, their body drawn again
 *       on top, lit flat like a cel, then the world glows and the full-screen flashes;</li>
 *   <li>everyone else's view: the poses, part scales and body geometry, and the world glows round the body.</li>
 * </ul>
 * Guide: {@code docs/OMNITRIX_SEQUENCES.md}.
 */
public final class OmnitrixSequenceClient {
	/** The holder's HUD was hidden for it (and whether it was hidden before). */
	private static boolean hidHud, hudWasHidden;
	/** Where each player's watch was last drawn (world), for the close-up and the dial's light. */
	private static final Map<Integer, Vec3> WRIST = new HashMap<>();

	/** Set only by the screenshot harness: freezes the sequence at this many ticks in (-1 = off). */
	public static volatile float freezeAt = -1f;
	/** Set only by the screenshot harness: a fixed seed for every sequence's random layouts (0 = off), so two films match. */
	public static volatile long seedOverride = 0L;
	/** Set only by the screenshot harness: the holder watches as a bystander would (no scripted camera, no stage). */
	public static volatile boolean bystander;

	private OmnitrixSequenceClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(OmnitrixSequenceClient::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(OmnitrixSequenceClient::renderWorld);
		WorldRenderEvents.LAST.register(OmnitrixSequenceClient::renderLast);
	}

	public static void clear() {
		WRIST.clear();
	}

	// ================================================================ which show, how far in

	/** The client half of {@code p}'s playing sequence, or null. */
	public static ShowSequenceClient show(Player p) {
		return ShowSequencesClient.get(OmnitrixLongTransform.sequenceId(p));
	}

	/** Ticks into {@code p}'s long transformation, or -1. */
	public static float age(Player p, float partial) {
		float t = OmnitrixLongTransform.age(p, partial);
		if (t >= 0f && freezeAt >= 0f) {
			return freezeAt;
		}
		return t;
	}

	/** Ticks into {@code p}'s long transformation if it is {@code alienId}'s, else -1. */
	public static float age(Player p, float partial, String alienId) {
		return alienId.equals(OmnitrixLongTransform.sequenceId(p)) ? age(p, partial) : -1f;
	}

	/** A seed fixed for the whole of {@code p}'s sequence (its start tick and their entity id), for random layouts. */
	public static long seed(Player p) {
		if (seedOverride != 0L) {
			return seedOverride;
		}
		Long start = Omnitrix.state(p).timers.get(OmnitrixLongTransform.TIMER);
		return (start == null ? 0L : start) * 31L + p.getId();
	}

	/** Where {@code p}'s watch dial was last drawn (world position), or null. */
	public static Vec3 wrist(Player p) {
		return WRIST.get(p.getId());
	}

	// ================================================================ tick: the body held to its mark, the HUD

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			WRIST.clear();
			return;
		}
		boolean mine = camera(mc.player, 0f) != null;
		if (mine && !hidHud) {
			hudWasHidden = mc.options.hideGui;
			mc.options.hideGui = true;
			hidHud = true;
		} else if (!mine && hidHud) {
			mc.options.hideGui = hudWasHidden;
			hidHud = false;
		}
		for (Player p : mc.level.players()) {
			if (age(p, 0f) < 0f) {
				WRIST.remove(p.getId());
				continue;
			}
			float yaw = OmnitrixLongTransform.yaw(Omnitrix.state(p));
			p.yBodyRotO = p.yBodyRot = yaw;
			p.yHeadRotO = p.yHeadRot = yaw;
		}
	}

	/** From {@code OmnitrixLayer}, inside the left arm's bone, just before the watch is drawn: remembers where the dial is. */
	public static void noteWrist(AbstractClientPlayer p, PoseStack pose) {
		if (age(p, 0f) < 0f) {
			return;
		}
		Vector3f v = pose.last().pose().transformPosition(new Vector3f(1f / 16f, 8.6f / 16f, -0.6f / 16f));
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		WRIST.put(p.getId(), new Vec3(cam.x + v.x, cam.y + v.y, cam.z + v.z));
	}

	// ================================================================ the camera

	/** Where the holder's camera is: {x, y, z, yaw, pitch}, or null when it is not theirs to show (the world view again). */
	public static double[] camera(Player p, float partial) {
		if (p != Minecraft.getInstance().player || bystander) {
			return null;
		}
		float t = age(p, partial);
		if (t < 0f) {
			return null;
		}
		ShowSequenceClient show = show(p);
		if (show == null || t >= show.worldAt()) {
			return null;
		}
		return show.camera(p, t, partial);
	}

	/** The field of view while the holder watches it (fixed: the root's slowness would otherwise zoom it), or -1. */
	public static double fov(float partial) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || camera(p, partial) == null) {
			return -1;
		}
		return show(p).fov(age(p, partial));
	}

	// ================================================================ the body (everyone)

	/** Called last by {@link OmnitrixPose#apply}: the playing show's pose. */
	public static void pose(AbstractClientPlayer p, HumanoidModel<?> m, float partial) {
		float t = age(p, partial);
		if (t < 0f) {
			return;
		}
		ShowSequenceClient show = show(p);
		if (show != null) {
			show.pose(p, m, t);
		}
	}

	/** The part scales through the model's own parts (from {@code HumanoidModelMixin}, after the scales are reset). */
	public static void applyBulge(Player p, HumanoidModel<?> m, boolean firstPersonHand) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = age(p, partial);
		if (t < 0f || firstPersonHand) {
			return;
		}
		ShowSequenceClient show = show(p);
		if (show != null) {
			show.bulge(p, m, t);
		}
	}

	/** From {@code OmnitrixLayer} (the human body): the playing show's geometry on the body (Four Arms' swelling lumps). */
	public static void renderSwell(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float partial) {
		float t = age(p, partial);
		if (t < 0f) {
			return;
		}
		ShowSequenceClient show = show(p);
		if (show != null) {
			show.renderBody(pose, buffers, p, model, t);
		}
	}

	// ================================================================ in the world (everyone): the show's glows

	private static void renderWorld(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Camera cam = ctx.camera();
		for (Player p : mc.level.players()) {
			float t = age(p, partial);
			if (t < 0f || p.isInvisible() || p.distanceToSqr(cam.getPosition()) > 64 * 64) {
				continue;
			}
			// the holder's own are drawn over the stage instead (renderLast)
			if (p == mc.player && camera(p, partial) != null) {
				continue;
			}
			ShowSequenceClient show = show(p);
			if (show != null) {
				show.renderWorldFx(new PoseStack(), ctx.consumers(), cam, p, t, partial);
			}
		}
	}

	// ================================================================ the holder's screen: the stage, the body again, the flashes

	private static void renderLast(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer me = mc.player;
		if (me == null || mc.level == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float t = age(me, partial);
		ShowSequenceClient show = t < 0f ? null : show(me);
		if (show == null) {
			return;
		}
		Camera cam = ctx.camera();
		MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
		PoseStack pose = new PoseStack();
		Stage st = new Stage(bs, pose, cam, seed(me), fov(mc.getTimer().getGameTimeDeltaPartialTick(false)));
		if (camera(me, partial) != null) {
			show.drawStage(st, t);
			bs.endBatch();
			RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
			// the body again, over the stage, lit flat like a cel
			Vec3 at = me.getPosition(partial).subtract(cam.getPosition());
			// no shadow: the stage has no ground (the user: the floor must not show)
			var erd = mc.getEntityRenderDispatcher();
			erd.setRenderShadow(false);
			erd.render(me, at.x, at.y, at.z, Mth.lerp(partial, me.yRotO, me.getYRot()), partial, pose, bs, LightTexture.FULL_BRIGHT);
			erd.setRenderShadow(mc.options.entityShadows().get());
			bs.endBatch();
			RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
			show.renderWorldFx(pose, bs, cam, me, t, partial);
			bs.endBatch();
		}
		show.drawOverlay(st, t);
		bs.endBatch();
	}

	/** The playing sequence's start tick, or null (for the burst timing). */
	public static Long start(OmnitrixState s) {
		return s.timers.get(OmnitrixLongTransform.TIMER);
	}

	/** The tick (after the slap) the usual green burst plays for {@code p}'s sequence. */
	public static int burstAt(Player p) {
		ShowSequenceClient show = show(p);
		return show == null ? 238 : show.burstAt();
	}
}
