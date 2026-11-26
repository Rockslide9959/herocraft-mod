package com.projecthero.mod.client.mutation.v0145;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.7 (user: "it should strictly be 40% for the afterimages, make sure that it stays 40% even when wearing armour"): the
 * speedsters' after-images (plain Super Speed, XLR8, the Speed Force Speedster -- trail, zip streaks and decoys) come out at
 * exactly {@link #ALPHA} and never denser.
 *
 * <p>A see-through copy drawn the plain way ({@code entityTranslucent}) blends once per overlapping surface: the hat over the
 * head, the jacket/sleeves/trousers over the body, armour over the skin, the model's own inner and back faces, and one copy over
 * the next -- each of those spots came out darker/lighter than the rest of the copy. So every copy is drawn twice, and all the
 * copies of the frame together:
 * <ol>
 *   <li><b>depth pass</b>: every copy (skin + armour) into the depth buffer only, colour writes off, back faces culled, through the
 *       entity-translucent shader so its alpha cutout ({@code a < 0.1} discards) keeps empty texels -- the blank parts of a hat
 *       layer -- out of the depth. It is pushed back a hair with polygon offset;</li>
 *   <li><b>colour pass</b>: every copy again at its alpha with a strict {@code LESS} depth test that also writes depth. Only the
 *       frontmost surface of all the copies at a pixel is in front of that (offset) depth, and once it has blended it writes
 *       its own un-offset depth, so nothing else -- not even an exactly coplanar surface (a leather overlay, a second copy in the
 *       very same spot) -- can blend on that pixel again.</li>
 * </ol>
 * Each pixel of the ghosts is therefore the frontmost surface blended over the world once, at the copy's alpha: 40% for a fresh
 * copy and only fainter as it fades. Colour is untouched: full-bright, the same tint as before, no glow.
 *
 * <p>Copies are queued during the {@code AFTER_ENTITIES} handlers and drawn in a later phase of the same event through this
 * class's own immediate buffer source, so both passes are flushed exactly when and in the order this class says. The queue
 * holds lambdas that capture players; it is emptied every frame it is drawn and whenever no level is loaded.
 */
public final class GhostCopies {
	/** The after-image opacity: strictly 40%, never denser (copies only fade below it). */
	public static final float ALPHA = 0.40f;

	/** One copy: draws itself (skin + armour) with {@code pass}'s render types into {@code out} at the pose given. */
	@FunctionalInterface
	public interface Copy {
		void draw(PoseStack pose, Pass pass, MultiBufferSource out);
	}

	/** The two passes; {@link #type} is the render type a copy uses for a texture in that pass. */
	public enum Pass {
		DEPTH, COLOUR;

		public RenderType type(ResourceLocation texture) {
			return (this == DEPTH ? GhostRenderType.DEPTH : GhostRenderType.COLOUR).apply(texture);
		}
	}

	private record Queued(PoseStack.Pose at, Copy copy) {
	}

	private static final ResourceLocation PHASE = ResourceLocation.fromNamespaceAndPath("projecthero", "ghost_copies");
	private static final List<Queued> QUEUE = new ArrayList<>();
	private static boolean registered;
	private static MultiBufferSource.BufferSource source;

	private GhostCopies() {
	}

	/** Registers the draw (idempotent: both speedster renderers call it). */
	public static void initialize() {
		if (registered) {
			return;
		}
		registered = true;
		WorldRenderEvents.AFTER_ENTITIES.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
		WorldRenderEvents.AFTER_ENTITIES.register(PHASE, GhostCopies::flush);
	}

	/** Queues a copy at {@code pose}'s current transform, to be drawn later this frame. */
	public static void add(PoseStack pose, Copy copy) {
		QUEUE.add(new Queued(pose.last().copy(), copy));
	}

	private static void flush(WorldRenderContext context) {
		if (QUEUE.isEmpty()) {
			return;
		}
		try {
			if (context.world() == null) {
				return;
			}
			if (source == null) {
				source = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
			}
			for (Pass pass : Pass.values()) {
				for (Queued q : QUEUE) {
					PoseStack pose = new PoseStack();
					pose.last().pose().set(q.at().pose());
					pose.last().normal().set(q.at().normal());
					q.copy().draw(pose, pass, source);
				}
				source.endBatch(); // the whole depth pass is down before any colour is drawn
			}
		} finally {
			QUEUE.clear();
		}
	}

	/** The ghost render types, one per texture and pass. The subclass only exists to reach the protected state shards. */
	private static final class GhostRenderType extends RenderType {
		static final Function<ResourceLocation, RenderType> DEPTH = Util.memoize(t -> new GhostRenderType(t, true));
		static final Function<ResourceLocation, RenderType> COLOUR = Util.memoize(t -> new GhostRenderType(t, false));

		private GhostRenderType(ResourceLocation texture, boolean depth) {
			super("projecthero_ghost_" + (depth ? "depth" : "colour"), DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536,
					false, false, setup(texture, depth), clear(depth));
		}

		private static Runnable setup(ResourceLocation texture, boolean depth) {
			TextureStateShard tex = new TextureStateShard(texture, false, false);
			return () -> {
				RENDERTYPE_ENTITY_TRANSLUCENT_SHADER.setupRenderState();
				tex.setupRenderState();
				LIGHTMAP.setupRenderState();
				OVERLAY.setupRenderState();
				RenderSystem.enableCull();
				RenderSystem.enableDepthTest();
				if (depth) {
					NO_TRANSPARENCY.setupRenderState();
					RenderSystem.depthFunc(GL11.GL_LEQUAL);
					DEPTH_WRITE.setupRenderState();
					RenderSystem.enablePolygonOffset();
					RenderSystem.polygonOffset(1.0f, 2.0f);
				} else {
					TRANSLUCENT_TRANSPARENCY.setupRenderState();
					RenderSystem.depthFunc(GL11.GL_LESS);
					COLOR_DEPTH_WRITE.setupRenderState();
				}
			};
		}

		private static Runnable clear(boolean depth) {
			return () -> {
				if (depth) {
					RenderSystem.polygonOffset(0.0f, 0.0f);
					RenderSystem.disablePolygonOffset();
					DEPTH_WRITE.clearRenderState();
					NO_TRANSPARENCY.clearRenderState();
				} else {
					COLOR_DEPTH_WRITE.clearRenderState();
					TRANSLUCENT_TRANSPARENCY.clearRenderState();
				}
				RenderSystem.depthFunc(GL11.GL_LEQUAL);
				RenderSystem.disableDepthTest();
				OVERLAY.clearRenderState();
				LIGHTMAP.clearRenderState();
				RENDERTYPE_ENTITY_TRANSLUCENT_SHADER.clearRenderState();
			};
		}
	}
}
