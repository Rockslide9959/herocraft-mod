package com.projecthero.mod.client.beastboy.model;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3 Beast Boy HQ round: the big animals (rhino, elephant, gorilla, the Beast) are blocky Minecraft-style models -- vanilla
 * {@link ModelPart} boxes baked at runtime from a generated {@code *Geo} class (box layout, pivots and UVs written by
 * {@code scratchpad/beastboy/gen_big_animals_tex.js}, which also paints each animal's own pixel-art texture). The model classes pose the
 * parts every frame (vanilla convention: y down, front -Z, xRot + = nose down) and draw them through {@link #render}.
 */
public final class BigBox {
	private BigBox() {
	}

	/** A baked model: its root, every part by name, and its texture. */
	public static final class Rig {
		public final ModelPart root;
		private final Map<String, ModelPart> parts = new HashMap<>();
		public final ResourceLocation tex;

		Rig(LayerDefinition def, String[][] tree, ResourceLocation tex) {
			this.root = def.bakeRoot();
			this.tex = tex;
			parts.put("", root);
			for (String[] e : tree) {
				ModelPart parent = parts.get(e[1]);
				parts.put(e[0], parent.getChild(e[0]));
			}
		}

		public ModelPart p(String name) {
			ModelPart m = parts.get(name);
			if (m == null) {
				throw new IllegalArgumentException("no part " + name);
			}
			return m;
		}

		/** Puts every part back to its rest pose (call before posing each frame). */
		public void reset() {
			for (ModelPart m : parts.values()) {
				m.resetPose();
			}
		}
	}

	/** A lazily baked rig (bakes on first use, on the render thread). */
	public static final class Lazy {
		private final Supplier<LayerDefinition> def;
		private final String[][] tree;
		private final String tex;
		private Rig rig;

		public Lazy(Supplier<LayerDefinition> def, String[][] tree, String tex) {
			this.def = def;
			this.tree = tree;
			this.tex = tex;
		}

		public Rig get() {
			if (rig == null) {
				rig = new Rig(def.get(), tree, ProjectHeroMod.id("textures/entity/beastboy/art/" + tex + ".png"));
			}
			return rig;
		}
	}

	/**
	 * Draws the posed rig. The pose stack is at the feet, +Z forward, blocks (BeastFormArt's contract); the parts are in the vanilla model
	 * space (y down from 24 px above the ground, front -Z). Tint as {@link BeastMesh#draw}: white / green keeps the paint (scaled by its
	 * brightness), red-dominant is the hurt flash (+ vanilla red overlay), alpha below 255 draws see-through; {@code flash} 0..1 adds the
	 * green-white morph glow.
	 */
	public static void render(Rig rig, PoseStack pose, MultiBufferSource buffers, int light, int tint, float flash) {
		int r = (tint >> 16) & 255, g = (tint >> 8) & 255, b = tint & 255, a = (tint >>> 24) & 255;
		if (a == 0) {
			a = 255;
		}
		int overlay;
		int cr, cg, cb;
		if (r > g + 24 && r > b) {
			cr = 255;
			cg = Math.max(70, g * 255 / Math.max(1, r));
			cb = Math.max(70, b * 255 / Math.max(1, r));
			overlay = OverlayTexture.pack(0, OverlayTexture.v(true));
		} else {
			int m = Math.max(60, Math.max(r, Math.max(g, b)));
			cr = cg = cb = m;
			overlay = OverlayTexture.NO_OVERLAY;
		}
		pose.pushPose();
		pose.scale(1f, -1f, -1f);
		pose.translate(0f, -1.501f, 0f);
		int color = (a << 24) | (cr << 16) | (cg << 8) | cb;
		rig.root.render(pose, buffers.getBuffer(a < 250 ? RenderType.entityTranslucent(rig.tex) : RenderType.entityCutoutNoCull(rig.tex)), light, overlay,
				color);
		if (flash > 0.003f) {
			float k = Math.min(1f, flash) * a / 255f;
			int glow = 0xFF000000 | ((int) (150 * k) << 16) | ((int) (255 * k) << 8) | (int) (120 * k);
			rig.root.render(pose, buffers.getBuffer(RenderType.eyes(rig.tex)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow);
		}
		pose.popPose();
	}
}
