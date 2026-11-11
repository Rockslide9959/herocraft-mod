package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.omnitrix.sequence.ShowSequence;
import com.projecthero.mod.omnitrix.sequence.ShowSequences;

import net.minecraft.client.Camera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: one alien's show transformation, the client half -- the show, shot for shot. Guide: {@code docs/OMNITRIX_SEQUENCES.md}.
 * <p>
 * A subclass is named {@code <Pascal>Show} after its alien id ({@code grey_matter} -> {@code GreyMatterShow}), lives in this package,
 * has a public no-argument constructor, and is picked up by {@link ShowSequencesClient} on its own (its common half,
 * {@code <Pascal>Sequence}, must exist too). Every hook gets {@code t}: ticks since the slap, with the partial tick. The dispatcher
 * ({@code OmnitrixSequenceClient}) calls them only while this alien's sequence plays:
 * <ul>
 *   <li>{@link #track} / {@link #camera}: the holder's scripted camera, until {@link #worldAt} (then their own view again);</li>
 *   <li>{@link #fov}: the holder's field of view;</li>
 *   <li>{@link #pose}: the body's pose (after the vanilla / watch poses; rigid limbs -- the model has no elbows or knees);</li>
 *   <li>{@link #bulge}: part scales (from the model mixin, after the scales are reset);</li>
 *   <li>{@link #renderBody}: extra geometry on the body (swelling lumps), from the watch layer, everyone sees it;</li>
 *   <li>{@link #renderWorldFx}: glows in the world round the body, everyone sees it (the holder's are drawn over the stage);</li>
 *   <li>{@link #drawStage}: the holder's cartoon background (the body is drawn again over it), while the camera is scripted;</li>
 *   <li>{@link #drawOverlay}: the holder's full-screen flashes, over everything, the whole sequence.</li>
 * </ul>
 */
public abstract class ShowSequenceClient {
	private final ShowSequence common;

	protected ShowSequenceClient(String alienId) {
		ShowSequence s = ShowSequences.get(alienId);
		if (s == null) {
			throw new IllegalStateException("no ShowSequence for " + alienId + " (add " + ShowSequences.pascal(alienId) + "Sequence)");
		}
		this.common = s;
	}

	public final String alienId() {
		return common.alienId();
	}

	/** The common half's length. */
	public final int total() {
		return common.total();
	}

	/** The common half's body-change tick. */
	public final int transformAt() {
		return common.transformAt();
	}

	/** From this tick the holder sees the world through their own camera again (the HUD stays hidden to the end). */
	public abstract float worldAt();

	/** The tick the usual green transformation burst plays in the world for everyone (under the final green flash). */
	public int burstAt() {
		return Math.round(worldAt()) - 11;
	}

	/** The scripted camera's keys, or null for none. */
	public CameraTrack track() {
		return null;
	}

	/** The holder's camera: {x, y, z, yaw, pitch}, or null. By default {@link #track} at {@link #cameraScale}. */
	public double[] camera(Player p, float t, float partial) {
		CameraTrack tr = track();
		return tr == null ? null : tr.at(p, t, partial, cameraScale(p, t));
	}

	/** How much the camera keys grow with the body: 1 before the change, the alien's height / 1.8 after it. */
	public float cameraScale(Player p, float t) {
		return t >= transformAt() ? Math.max(1f, p.getBbHeight() / 1.8f) : 1f;
	}

	/** The holder's field of view (degrees) while the camera is scripted. */
	public double fov(float t) {
		return 62;
	}

	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
	}

	/** {@code firstPersonHand} renders are skipped before this is called. */
	public void bulge(Player p, HumanoidModel<?> m, float t) {
	}

	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
	}

	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
	}

	public void drawStage(Stage st, float t) {
	}

	public void drawOverlay(Stage st, float t) {
	}
}
