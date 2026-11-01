package com.projecthero.mod.client.render;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.render.workbench.WorkbenchBuildFx;
import com.projecthero.mod.client.render.workbench.WorkbenchHolo;
import com.projecthero.mod.client.render.workbench.WorkbenchHoloRenderType;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.fabricator.FabricationRecipe;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorBlock;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorBlockEntity;
import com.projecthero.mod.ironman.fabricator.StarkWorkbench;
import com.projecthero.mod.ironman.fabricator.WorkbenchPart;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.16.7 (user): the Stark Workbench's holograms (replaces the v0.14.21 GeckoLib Fabricator rig). Over the projector disc
 * in the middle of the two-block desk: a ring turning round the job and two floating screens (stat bars left; the selected
 * piece's recipe slots + the progress bar right). Idle (no blueprint): a slowly turning arc-reactor hologram.
 *
 * <p>v0.17.2 (user): what stands over the lens is the EXACT piece -- the real armour model of the blueprint's suit and the
 * selected piece ({@link WorkbenchBuildFx#drawPiece}): a cyan blueprint of it while idle, and while Fabricate runs it
 * assembles plate by plate to the job's real progress (the per-piece choreography in {@link WorkbenchBuildFx}) inside a
 * hologram frame with a scan line on the build front, welding sparks and a clank at every plate that locks, a beacon hum
 * underneath, the piece's lights coming on at the end, and a flourish (a ring, a burst of light, the piece dropping into
 * the desk) when it pops into the output slot. A component job shows the real item growing in the frame. All of it is
 * read off the synced job ({@link StarkFabricatorBlockEntity#getUpdateTag}: piece, item, progress, advancing, the
 * completion counter) and run on smoothly with the partial tick between syncs.
 *
 * <p>Cheap by construction: drawn only within {@value #RANGE} blocks (and fading out towards that); the holograms go into
 * {@link WorkbenchHoloRenderType} (additive, full-bright, depth-tested without depth writes), so they never z-fight and
 * walls in front still hide them.
 */
public class StarkWorkbenchRenderer implements BlockEntityRenderer<StarkFabricatorBlockEntity> {
	public static final int RANGE = 12;
	private static final float S = 1f / 16f;
	private static final float FIGURE_SCALE = 0.62f;
	private static final float FIGURE_BASE = 1.6f; // px above the lens top
	/** How far above the lens the piece's middle floats (blocks), and how big it is drawn on a full / one-block desk. */
	private static final float PIECE_LIFT = 0.46f;
	private static final float PIECE_SCALE = 0.7f;
	private static final float PIECE_SCALE_SINGLE = 0.55f;
	/** The finish flourish's length (ticks). */
	private static final float FLOURISH_TICKS = 24f;

	private final boolean[] built = new boolean[4];
	private static final float[] NO_BARS = new float[4];

	/** Per desk, client only: the smoothed progress drawn, the sound / spark beats already played, the flourish clock. */
	private static final class Fx {
		Item job;
		float drawn;
		int beat;
		long lastHum = Long.MIN_VALUE;
		long lastSpark = Long.MIN_VALUE;
		int completions = -1;
		float flourishAt = Float.NaN;
		Item flourishItem;
	}

	private static final Map<StarkFabricatorBlockEntity, Fx> FX = new WeakHashMap<>();

	/**
	 * The moments a plate locks home, per piece (progress fractions): a clank and a spray of sparks each. The last one is
	 * the piece's lights coming on (a chime / the reactor / the repulsors).
	 */
	private static final float[][] BEATS = {
			{ 0.13f, 0.27f, 0.4f, 0.52f, 0.79f, 0.86f },
			{ 0.2f, 0.3f, 0.42f, 0.52f, 0.62f, 0.74f, 0.88f },
			{ 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f },
			{ 0.1f, 0.26f, 0.38f, 0.5f, 0.7f, 0.84f } };
	private static final float[] COMPONENT_BEATS = { 0.2f, 0.4f, 0.6f, 0.8f };

	@Override
	public void render(StarkFabricatorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		Minecraft mc = Minecraft.getInstance();
		if (be.getLevel() == null || mc.gameRenderer == null) {
			return;
		}
		BlockState state = be.getBlockState();
		if (!state.hasProperty(StarkFabricatorBlock.FACING)) {
			return;
		}
		Vec3 centre = StarkFabricatorBlock.deskCentre(state, be.getBlockPos());
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		double dist = cam.distanceTo(centre);
		Fx fx = FX.computeIfAbsent(be, b -> new Fx());
		if (dist > RANGE) {
			fx.completions = be.completions(); // out of range: no flourish owed for jobs finished meanwhile
			return;
		}
		// subtle at a distance: full strength within 5 blocks, fading to a third at the edge of the range
		float k = (float) Math.max(0.33, Math.min(1.0, 1.0 - (dist - 5.0) / (RANGE - 5.0) * 0.67));
		float time = (be.getLevel().getGameTime() % 24000L) + partialTick;
		Direction facing = state.getValue(StarkFabricatorBlock.FACING);
		boolean single = state.getValue(StarkFabricatorBlock.PART) == WorkbenchPart.SINGLE;

		VertexConsumer vc = buffers.getBuffer(WorkbenchHoloRenderType.HOLO);
		pose.pushPose();
		pose.translate(centre.x - be.getBlockPos().getX(), centre.y - be.getBlockPos().getY(), centre.z - be.getBlockPos().getZ());
		pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot())); // local +Z = the desk's front, +X = the viewer's right
		pose.scale(S, S, S);

		float flourishT = flourishT(be, fx, time);
		String suitId = be.holoSuit();
		Item job = be.jobItem();
		if (job != null && be.maxProgress() > 0) {
			drawJob(vc, pose, buffers, be, fx, job, suitId, centre, facing, time, partialTick, k, single);
		} else if (flourishT >= 0f && fx.flourishItem != null) {
			drawFlourish(vc, pose, buffers, fx, flourishT, time, partialTick, k, single);
			drawScreens(buffers, pose, be, suitId, 1f, k, single);
		} else if (suitId == null || IronManSuits.byId(suitId) == null) {
			fx.job = null;
			drawIdle(vc, pose, time, k);
		} else {
			fx.job = null;
			drawLoaded(vc, pose, buffers, be, suitId, time, partialTick, k, single);
		}
		pose.popPose();
	}

	/** The finish flourish clock (0..1), started when the synced completion counter goes up; -1 when none is playing. */
	private static float flourishT(StarkFabricatorBlockEntity be, Fx fx, float time) {
		int done = be.completions();
		if (fx.completions < 0) {
			fx.completions = done; // first sight of this desk: nothing to celebrate
		} else if (done != fx.completions) {
			fx.completions = done;
			fx.flourishAt = time;
			fx.flourishItem = be.doneItem();
			finishBurst(be);
		}
		if (Float.isNaN(fx.flourishAt)) {
			return -1f;
		}
		float t = (time - fx.flourishAt) / FLOURISH_TICKS;
		if (t < 0f || t >= 1f) {
			fx.flourishAt = Float.NaN;
			return -1f;
		}
		return t;
	}

	private void drawIdle(VertexConsumer vc, PoseStack pose, float time, float k) {
		Matrix4f m = pose.last().pose();
		WorkbenchHolo.beam(vc, m, 2.2f, 3.0f, 6f, k * 0.7f);
		WorkbenchHolo.ring(vc, m, time * 0.02f, 7.5f, 9f, k * 0.6f);
		pose.pushPose();
		pose.translate(0, 9.5f, 0);
		pose.mulPose(Axis.YP.rotation(time * 0.025f));
		WorkbenchHolo.reactor(vc, pose.last().pose(), time * 0.04f, k);
		pose.popPose();
	}

	/** A blueprint loaded, nothing running: the selected piece as a cyan blueprint (or the whole stylised suit if none). */
	private void drawLoaded(VertexConsumer vc, PoseStack pose, MultiBufferSource buffers, StarkFabricatorBlockEntity be, String suitId,
			float time, float partialTick, float k, boolean single) {
		int piece = be.selectedPiece();
		ArmorItem.Type type = StarkWorkbench.pieceType(piece);
		Item armour = type == null ? null : IronManItems.armor(suitId, type);
		Matrix4f m = pose.last().pose();
		WorkbenchHolo.beam(vc, m, 2.2f, 4.0f, FIGURE_BASE + 2f, k);
		if (armour != null) {
			WorkbenchHolo.ring(vc, m, time * 0.02f, 9.5f, PIECE_LIFT * 16f, k * 0.85f);
			boolean done = StarkWorkbench.pieceBuilt(StarkWorkbench.state(Minecraft.getInstance().player), suitId, piece);
			drawArmour(pose, buffers, armour, piece, done ? 1f : 0f, !done, time, partialTick, k, single, 1f);
		} else {
			TonyStarkState s = StarkWorkbench.state(Minecraft.getInstance().player);
			for (int p = 0; p < 4; p++) {
				built[p] = StarkWorkbench.pieceBuilt(s, suitId, p + 1);
			}
			WorkbenchHolo.ring(vc, m, time * 0.02f, 9.5f, FIGURE_BASE + 19f * FIGURE_SCALE, k * 0.85f);
			pose.pushPose();
			pose.translate(0, FIGURE_BASE, 0);
			pose.mulPose(Axis.YP.rotation(time * 0.02f));
			pose.scale(FIGURE_SCALE, FIGURE_SCALE, FIGURE_SCALE);
			WorkbenchHolo.suit(vc, pose.last().pose(), built, -1, 0f, k, (time * 0.35f) % 44f - 6f);
			pose.popPose();
		}
		drawScreens(buffers, pose, be, suitId, 0f, k, single);
	}

	/** The job running: the real piece (or item) assembling to the job's progress, with its sparks and sounds. */
	private void drawJob(VertexConsumer vc, PoseStack pose, MultiBufferSource buffers, StarkFabricatorBlockEntity be, Fx fx, Item job,
			String suitId, Vec3 centre, Direction facing, float time, float partialTick, float k, boolean single) {
		float prog = smoothProgress(be, fx, job, partialTick);
		boolean armour = job instanceof IronManArmorItem;
		int piece = armour ? pieceOf((IronManArmorItem) job) : 0;
		Matrix4f m = pose.last().pose();
		WorkbenchHolo.beam(vc, m, 2.2f, 4.0f, FIGURE_BASE + 2f, k * (be.advancing() ? 1.2f : 0.7f));
		WorkbenchHolo.ring(vc, m, time * (be.advancing() ? 0.06f : 0.02f), 9.5f, PIECE_LIFT * 16f, k * 0.85f);
		float spin = time * 0.6f;
		if (armour) {
			drawArmour(pose, buffers, job, piece, prog, false, time, partialTick, k, single, 1f);
		} else {
			drawItem(vc, pose, buffers, job, prog, time, k, single);
		}
		if (suitId != null && IronManSuits.byId(suitId) != null) {
			drawScreens(buffers, pose, be, suitId, prog, k, single);
		}
		beats(be, fx, piece, prog, centre, facing, spin, time, single);
	}

	/**
	 * The real piece over the lens: turning slowly, its middle {@link #PIECE_LIFT} above the lens, with the hologram frame,
	 * scan line and lights round it ({@code pop} scales it for the flourish).
	 */
	private static void drawArmour(PoseStack pose, MultiBufferSource buffers, Item armour, int piece, float prog, boolean ghost,
			float time, float partialTick, float k, boolean single, float pop) {
		float sc = (single ? PIECE_SCALE_SINGLE : PIECE_SCALE) * WorkbenchBuildFx.displayScale(piece) * pop;
		pose.pushPose();
		pose.scale(16f, 16f, 16f); // back to block units
		pose.translate(0, PIECE_LIFT, 0);
		pose.mulPose(Axis.YP.rotationDegrees(time * 0.6f));
		pose.scale(sc, sc, sc);
		pose.translate(0, -WorkbenchBuildFx.centreY(piece), 0);
		if (pop > 0.01f) {
			WorkbenchBuildFx.drawPiece(pose, buffers, LightTexture.FULL_BRIGHT, armour, piece, prog, ghost, partialTick);
		}
		VertexConsumer vc = buffers.getBuffer(WorkbenchHoloRenderType.HOLO);
		if (ghost) {
			WorkbenchBuildFx.cage(vc, pose.last().pose(), piece, 0.5f, time, k * 0.35f);
		} else if (prog < 1f) {
			WorkbenchBuildFx.cage(vc, pose.last().pose(), piece, prog, time, k);
		}
		if (!ghost) {
			WorkbenchBuildFx.flare(vc, pose.last().pose(), piece, prog, time, k);
		}
		pose.popPose();
	}

	/** A component job: the real item growing in a small frame. */
	private static void drawItem(VertexConsumer vc, PoseStack pose, MultiBufferSource buffers, Item item, float prog, float time, float k,
			boolean single) {
		Minecraft mc = Minecraft.getInstance();
		float sc = (single ? 0.32f : 0.42f) * (0.25f + 0.75f * smooth(prog));
		pose.pushPose();
		pose.scale(16f, 16f, 16f);
		pose.translate(0, PIECE_LIFT, 0);
		pose.mulPose(Axis.YP.rotationDegrees(time * 3f));
		pose.pushPose();
		pose.scale(sc, sc, sc);
		mc.getItemRenderer().renderStatic(new ItemStack(item), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
		pose.popPose();
		pose.translate(0, -WorkbenchBuildFx.centreY(4), 0); // a small box round the item (the boots' frame)
		WorkbenchBuildFx.cage(buffers.getBuffer(WorkbenchHoloRenderType.HOLO), pose.last().pose(), 4, prog, time, k * 0.7f);
		pose.popPose();
	}

	private void drawFlourish(VertexConsumer vc, PoseStack pose, MultiBufferSource buffers, Fx fx, float t, float time, float partialTick,
			float k, boolean single) {
		Matrix4f m = pose.last().pose();
		WorkbenchHolo.beam(vc, m, 2.2f, 4.0f, FIGURE_BASE + 2f, k * (1.6f - t));
		// a little pop (to 112%), then it shrinks away down into the desk -- into the output slot
		float pop = t < 0.3f ? 1f + 0.12f * (float) Math.sin(Math.PI * t / 0.3f) : Math.max(0f, 1f - (t - 0.3f) / 0.7f);
		if (fx.flourishItem instanceof IronManArmorItem a) {
			int piece = pieceOf(a);
			pose.pushPose();
			pose.translate(0, -PIECE_LIFT * 16f * smooth((t - 0.3f) / 0.7f), 0);
			drawArmour(pose, buffers, a, piece, 1f, false, time, partialTick, k, single, pop);
			pose.popPose();
			pose.pushPose();
			pose.scale(16f, 16f, 16f);
			pose.translate(0, PIECE_LIFT, 0);
			float sc = (single ? PIECE_SCALE_SINGLE : PIECE_SCALE) * WorkbenchBuildFx.displayScale(piece);
			pose.scale(sc, sc, sc);
			pose.translate(0, -WorkbenchBuildFx.centreY(piece), 0);
			WorkbenchBuildFx.flourish(buffers.getBuffer(WorkbenchHoloRenderType.HOLO), pose.last().pose(), piece, t, k);
			pose.popPose();
		} else if (fx.flourishItem != null) {
			drawItem(vc, pose, buffers, fx.flourishItem, 1f, time, k * pop, single);
			pose.pushPose();
			pose.scale(16f, 16f, 16f);
			pose.translate(0, PIECE_LIFT - WorkbenchBuildFx.centreY(4), 0);
			WorkbenchBuildFx.flourish(buffers.getBuffer(WorkbenchHoloRenderType.HOLO), pose.last().pose(), 4, t, k);
			pose.popPose();
		}
	}

	/** The floating screens, tilted towards the user (smaller and closer in on a one-block desk). */
	private void drawScreens(MultiBufferSource buffers, PoseStack pose, StarkFabricatorBlockEntity be, String suitId, float prog, float k,
			boolean single) {
		if (suitId == null || IronManSuits.byId(suitId) == null) {
			return;
		}
		// fetched here, after the piece: drawing the armour stand switches render types, which ends an earlier HOLO buffer
		VertexConsumer vc = buffers.getBuffer(WorkbenchHoloRenderType.HOLO);
		float off = single ? 9.5f : 12.5f;
		float w = single ? 9f : 12f;
		float h = single ? 6f : 8f;
		float y = single ? 7f : 7.5f;
		float[] bars = StarkWorkbench.statBars(IronManSuits.byId(suitId));
		pose.pushPose();
		pose.translate(-off, y, 2.5f);
		pose.mulPose(Axis.YP.rotationDegrees(28f));
		pose.translate(-w / 2f, 0, 0);
		WorkbenchHolo.statsScreen(vc, pose.last().pose(), w, h, bars == null ? NO_BARS : bars, k * 0.9f);
		pose.popPose();

		FabricationRecipe recipe = StarkWorkbench.recipe(suitId, be.selectedPiece());
		int slots = recipe == null ? 0 : be.slotCount();
		pose.pushPose();
		pose.translate(off, y, 2.5f);
		pose.mulPose(Axis.YP.rotationDegrees(-28f));
		pose.translate(-w / 2f, 0, 0);
		WorkbenchHolo.recipeScreen(vc, pose.last().pose(), w, h, slots, be.slotMask(), prog, k * 0.9f);
		pose.popPose();
	}

	/** The job's progress, run on between the 2 s syncs while it advances, never drawn backwards within one job. */
	private static float smoothProgress(StarkFabricatorBlockEntity be, Fx fx, Item job, float partialTick) {
		int max = be.maxProgress();
		float ticks = be.progress() + (be.advancing() ? Math.max(0, be.getLevel().getGameTime() - be.syncedAt()) + partialTick : 0f);
		float raw = Math.min(1f, ticks / Math.max(1, max));
		if (fx.job != job || raw + 0.15f < fx.drawn) {
			fx.job = job; // a new job (or a restart): start over
			fx.drawn = raw;
			fx.beat = 0;
			while (fx.beat < beatsOf(be, job).length && beatsOf(be, job)[fx.beat] <= raw) {
				fx.beat++; // joining mid-build: no burst of every beat already passed
			}
		}
		fx.drawn = Math.max(fx.drawn, raw);
		return fx.drawn;
	}

	private static float[] beatsOf(StarkFabricatorBlockEntity be, Item job) {
		return job instanceof IronManArmorItem a ? BEATS[pieceOf(a) - 1] : COMPONENT_BEATS;
	}

	private static int pieceOf(IronManArmorItem a) {
		return switch (a.getType()) {
			case HELMET -> 1;
			case CHESTPLATE -> 2;
			case LEGGINGS -> 3;
			default -> 4;
		};
	}

	/** Sparks along the build front every tick, a clank + a spray at each beat, the lights' cue, the hum under it all. */
	private static void beats(StarkFabricatorBlockEntity be, Fx fx, int piece, float prog, Vec3 centre, Direction facing, float spinDeg,
			float time, boolean single) {
		Level level = be.getLevel();
		long now = level.getGameTime();
		RandomSource rand = level.random;
		Vec3 mid = centre.add(0, PIECE_LIFT, 0);
		float sc = (single ? PIECE_SCALE_SINGLE : PIECE_SCALE) * (piece > 0 ? WorkbenchBuildFx.displayScale(piece) : 0.6f);
		int pc = Math.max(1, piece == 0 ? 4 : piece);
		float[] b = WorkbenchBuildFx.bounds(pc);
		float front = (WorkbenchBuildFx.frontHeight(pc, prog) - WorkbenchBuildFx.centreY(pc)) * sc;
		double yaw = Math.toRadians(-facing.toYRot() + spinDeg);
		if (be.advancing() && now != fx.lastSpark) {
			fx.lastSpark = now;
			if (rand.nextInt(2) == 0) {
				spark(level, rand, mid, b, sc, front, yaw, ParticleTypes.ELECTRIC_SPARK, 1);
			}
			if (now - fx.lastHum >= 70) {
				fx.lastHum = now;
				play(level, mid, SoundEvents.BEACON_AMBIENT, 0.3f, 1.7f);
			}
		}
		float[] beats = piece > 0 ? BEATS[piece - 1] : COMPONENT_BEATS;
		while (fx.beat < beats.length && prog >= beats[fx.beat]) {
			boolean last = fx.beat == beats.length - 1 && piece > 0;
			fx.beat++;
			if (last) {
				lights(level, rand, piece, mid, sc, yaw);
				continue;
			}
			// the plates lock: anvil-ish clank, smithing hiss, sparks off the weld
			boolean heavy = (piece == 1 && fx.beat == beats.length - 1) || (piece == 2 && fx.beat == 1);
			play(level, mid, heavy ? SoundEvents.ANVIL_PLACE : piece == 3 ? SoundEvents.CHAIN_PLACE : SoundEvents.SMITHING_TABLE_USE,
					heavy ? 0.25f : 0.4f, 1.3f + rand.nextFloat() * 0.35f);
			if (rand.nextInt(2) == 0) {
				play(level, mid, SoundEvents.IRON_GOLEM_STEP, 0.25f, 1.8f);
			}
			spark(level, rand, mid, b, sc, front, yaw, ParticleTypes.ELECTRIC_SPARK, 8 + rand.nextInt(5));
			spark(level, rand, mid, b, sc, front, yaw, ParticleTypes.SMALL_FLAME, 2);
		}
	}

	/** The piece's lights come on: eyes (a chime), the arc reactor (the beacon power-up) or the boot repulsors (a flare). */
	private static void lights(Level level, RandomSource rand, int piece, Vec3 mid, float sc, double yaw) {
		switch (piece) {
			case 1 -> play(level, mid, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.6f);
			case 2 -> play(level, mid, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.8f);
			case 4 -> {
				play(level, mid, SoundEvents.FIRECHARGE_USE, 0.35f, 1.6f);
				Vec3 soles = mid.add(0, (-0.02f - WorkbenchBuildFx.centreY(4)) * sc, 0);
				for (int i = 0; i < 14; i++) {
					level.addParticle(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.ELECTRIC_SPARK, soles.x + (rand.nextDouble() - 0.5) * 0.2,
							soles.y, soles.z + (rand.nextDouble() - 0.5) * 0.2, (rand.nextDouble() - 0.5) * 0.04, -0.06 - rand.nextDouble() * 0.05,
							(rand.nextDouble() - 0.5) * 0.04);
				}
			}
			default -> play(level, mid, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
		}
	}

	/** {@code n} particles at random points on the build front of the piece's box (turned with the piece). */
	private static void spark(Level level, RandomSource rand, Vec3 mid, float[] b, float sc, float front, double yaw, ParticleOptions type,
			int n) {
		for (int i = 0; i < n; i++) {
			double lx = (b[0] + (b[3] - b[0]) * rand.nextDouble()) * sc;
			double lz = (rand.nextBoolean() ? b[2] : b[5]) * sc;
			double wx = lx * Math.cos(yaw) + lz * Math.sin(yaw);
			double wz = -lx * Math.sin(yaw) + lz * Math.cos(yaw);
			level.addParticle(type, mid.x + wx, mid.y + front, mid.z + wz, (rand.nextDouble() - 0.5) * 0.18, rand.nextDouble() * 0.1,
					(rand.nextDouble() - 0.5) * 0.18);
		}
	}

	/** The output pop: a burst of light round the lens. */
	private static void finishBurst(StarkFabricatorBlockEntity be) {
		Level level = be.getLevel();
		Vec3 mid = StarkFabricatorBlock.deskCentre(be.getBlockState(), be.getBlockPos()).add(0, PIECE_LIFT, 0);
		RandomSource rand = level.random;
		for (int i = 0; i < 18; i++) {
			level.addParticle(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, (rand.nextDouble() - 0.5) * 0.25, rand.nextDouble() * 0.15,
					(rand.nextDouble() - 0.5) * 0.25);
		}
		for (int i = 0; i < 12; i++) {
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y - 0.2, mid.z, (rand.nextDouble() - 0.5) * 0.4, rand.nextDouble() * 0.2,
					(rand.nextDouble() - 0.5) * 0.4);
		}
		play(level, mid, SoundEvents.AMETHYST_CLUSTER_PLACE, 0.8f, 1.5f);
	}

	private static void play(Level level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch, false);
	}

	private static float smooth(float x) {
		x = Math.max(0f, Math.min(1f, x));
		return x * x * (3f - 2f * x);
	}

	@Override
	public int getViewDistance() {
		return RANGE;
	}

	@Override
	public boolean shouldRenderOffScreen(StarkFabricatorBlockEntity be) {
		return true; // the hologram stands well above its block, so it must not vanish when the desk block is just off screen
	}
}
