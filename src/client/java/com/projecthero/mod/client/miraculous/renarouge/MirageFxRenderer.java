package com.projecthero.mod.client.miraculous.renarouge;

import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Vector3f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.miraculous.renarouge.RenaRougeConfig;
import com.projecthero.mod.miraculous.renarouge.entity.MirageFxEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.7: the Mirage wheel's big visuals, all drawn on the client from one {@link MirageFxEntity} (no particle
 * packets): the giant rearing fox-beast (Monster), the burning, cracking ground (Mirage Zone), the heap of treasure in
 * its halo (Golden Lure), the illusory floor over the Pitfall, the dome of night (Eclipse) and the slowly turning ring
 * of mirrors (Hall of Mirrors). Everything fades in and out over 10 ticks.
 */
public class MirageFxRenderer extends EntityRenderer<MirageFxEntity> {
	private static final ResourceLocation WHITE = ProjectHeroMod.id("dynamic/rena_mirage_white");
	private static boolean whiteMade;
	/** Per-entity cached ground heights of the Zone's flames (they never change while it burns). */
	private static final Map<MirageFxEntity, float[]> FLAMES = new WeakHashMap<>();
	private static final int FLAME_COUNT = 34;
	private static final int CRACKS = 14;

	private final RenaFoxRenderer.TintedFoxModel<Fox> beast;
	private Fox dummy;
	private Level dummyLevel;

	public MirageFxRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0f;
		this.beast = new RenaFoxRenderer.TintedFoxModel<>(ctx.bakeLayer(ModelLayers.FOX));
	}

	private static ResourceLocation white() {
		if (!whiteMade) {
			whiteMade = true;
			NativeImage img = new NativeImage(4, 4, false);
			img.fillRect(0, 0, 4, 4, 0xFFFFFFFF);
			Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(img));
		}
		return WHITE;
	}

	@Override
	public void render(MirageFxEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float fade = e.fade(partial);
		if (fade <= 0f) {
			return;
		}
		float age = e.tickCount + partial;
		switch (e.kind()) {
			case MirageFxEntity.MONSTER -> monster(e, pose, buffers, age, fade, partial);
			case MirageFxEntity.ZONE -> zone(e, pose, buffers, age, fade);
			case MirageFxEntity.LURE -> lure(e, pose, buffers, age, fade);
			case MirageFxEntity.PIT -> pit(e, pose, buffers, age, fade);
			case MirageFxEntity.ECLIPSE -> eclipse(e, pose, buffers, age, fade);
			case MirageFxEntity.MIRRORS -> mirrors(e, pose, buffers, age, fade);
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- Monster

	private void monster(MirageFxEntity e, PoseStack pose, MultiBufferSource buffers, float age, float fade, float partial) {
		if (dummy == null || dummyLevel != e.level()) {
			dummy = EntityType.FOX.create(e.level());
			dummyLevel = e.level();
			if (dummy == null) {
				return;
			}
		}
		float s = RenaRougeConfig.MONSTER_SCALE;
		// it rears up over the first 15 ticks, then sways and breathes
		float rise = Mth.clamp(age / 15f, 0f, 1f);
		float rear = (38f + 5f * Mth.sin(age * 0.08f)) * (1f - (1f - rise) * (1f - rise));
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180f - e.facing()));
		pose.translate(0f, 0f, 0.25f * s);
		pose.mulPose(Axis.XP.rotationDegrees(-rear));
		pose.scale(s, s, s);
		pose.scale(-1f, -1f, 1f);
		pose.translate(0f, -1.501f, 0f);
		beast.prepareMobModel(dummy, 0f, 0f, partial);
		beast.setupAnim(dummy, age * 0.25f, 0.35f, age, 0f, -25f + 10f * Mth.sin(age * 0.11f));
		int a = Math.round(255 * 0.62f * fade * (0.9f + 0.1f * Mth.sin(age * 0.5f)));
		beast.tint = FastColor.ARGB32.color(a, 255, 150, 70);
		beast.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(RenaFoxRenderer.TEXTURE)), LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();
		// a ground glow under it
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(white()));
		disk(vc, pose.last(), 0.05f, 4.5f, FastColor.ARGB32.color(Math.round(90 * fade), 255, 120, 40), FastColor.ARGB32.color(0, 255, 120, 40));
	}

	// ---------------------------------------------------------------- Mirage Zone

	private void zone(MirageFxEntity e, PoseStack pose, MultiBufferSource buffers, float age, float fade) {
		float r = e.radius();
		PoseStack.Pose last = pose.last();
		VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(white()));
		float pulse = 0.85f + 0.15f * Mth.sin(age * 0.2f);
		disk(glow, last, 0.04f, r, FastColor.ARGB32.color(Math.round(120 * fade * pulse), 255, 70, 10), FastColor.ARGB32.color(Math.round(30 * fade), 120, 20, 0));
		// glowing cracks running out from the middle
		RandomSource rnd = RandomSource.create(e.getId() * 31L + 7L);
		int crack = FastColor.ARGB32.color(Math.round(230 * fade * pulse), 255, 190, 60);
		for (int i = 0; i < CRACKS; i++) {
			double a = Math.PI * 2 * i / CRACKS + rnd.nextDouble() * 0.3;
			float x = 0f, z = 0f;
			float d0 = (float) (rnd.nextDouble() * 1.5);
			x = (float) Math.cos(a) * d0;
			z = (float) Math.sin(a) * d0;
			for (int k = 0; k < 5; k++) {
				a += (rnd.nextDouble() - 0.5) * 0.9;
				float len = r / 5f * (0.7f + rnd.nextFloat() * 0.5f);
				float nx = x + (float) Math.cos(a) * len, nz = z + (float) Math.sin(a) * len;
				if (nx * nx + nz * nz > r * r) {
					break;
				}
				strip(glow, last, x, z, nx, nz, 0.07f, 0.22f * (1f - k * 0.12f), crack);
				x = nx;
				z = nz;
			}
		}
		// the flames, crossed sprites standing on the ground
		float[] hs = FLAMES.computeIfAbsent(e, k -> groundHeights(k, r));
		TextureAtlasSprite fire = Minecraft.getInstance().getBlockRenderer().getBlockModel(Blocks.FIRE.defaultBlockState()).getParticleIcon();
		VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
		RandomSource fr = RandomSource.create(e.getId() * 131L + 3L);
		for (int i = 0; i < FLAME_COUNT; i++) {
			double a = fr.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(fr.nextDouble()) * r * 0.95;
			float fx = (float) (Math.cos(a) * d), fz = (float) (Math.sin(a) * d);
			float h = (1.0f + fr.nextFloat() * 1.3f) * fade * (0.85f + 0.15f * Mth.sin(age * 0.4f + i));
			float w = 0.5f + fr.nextFloat() * 0.4f;
			float y = hs[i];
			cross(vc, last, fire, fx, y, fz, w, h);
		}
	}

	private static float[] groundHeights(MirageFxEntity e, float r) {
		float[] out = new float[FLAME_COUNT];
		RandomSource fr = RandomSource.create(e.getId() * 131L + 3L);
		Level level = e.level();
		for (int i = 0; i < FLAME_COUNT; i++) {
			double a = fr.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(fr.nextDouble()) * r * 0.95;
			fr.nextFloat();
			fr.nextFloat();
			double x = e.getX() + Math.cos(a) * d, z = e.getZ() + Math.sin(a) * d;
			BlockPos.MutableBlockPos p = BlockPos.containing(x, e.getY() + 4, z).mutable();
			float y = 0f;
			for (int k = 0; k < 10; k++) {
				BlockPos below = p.below();
				if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
					y = (float) (p.getY() - e.getY());
					break;
				}
				p.move(0, -1, 0);
			}
			out[i] = y;
		}
		return out;
	}

	// ---------------------------------------------------------------- Golden Lure

	private void lure(MirageFxEntity e, PoseStack pose, MultiBufferSource buffers, float age, float fade) {
		var items = Minecraft.getInstance().getItemRenderer();
		float bob = 0.08f * Mth.sin(age * 0.15f);
		ItemStack[] heap = { new ItemStack(Items.GOLD_BLOCK), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.GOLDEN_APPLE),
				new ItemStack(Items.DIAMOND), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.EMERALD), new ItemStack(Items.GOLD_NUGGET),
				new ItemStack(Items.GOLDEN_HELMET), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.RAW_GOLD) };
		for (int i = 0; i < heap.length; i++) {
			pose.pushPose();
			if (i == 0) {
				pose.translate(0f, 0.55f + bob, 0f);
				pose.mulPose(Axis.YP.rotationDegrees(age * 2f));
				pose.scale(1.6f * fade, 1.6f * fade, 1.6f * fade);
			} else {
				double a = Math.PI * 2 * i / (heap.length - 1) + age * 0.01;
				pose.translate((float) Math.cos(a) * 0.85f, 0.2f + bob * 0.5f + (i % 3) * 0.12f, (float) Math.sin(a) * 0.85f);
				pose.mulPose(Axis.YP.rotationDegrees(i * 47f + age * 1.5f));
				pose.scale(0.9f * fade, 0.9f * fade, 0.9f * fade);
			}
			items.renderStatic(heap[i], ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, e.level(), e.getId() + i);
			pose.popPose();
		}
		// its halo, and a ring of light on the ground
		float pulse = 0.8f + 0.2f * Mth.sin(age * 0.25f);
		pose.pushPose();
		pose.translate(0f, 1.0f, 0f);
		pose.mulPose(entityRenderDispatcher.cameraOrientation());
		float s = 3.6f * pulse;
		pose.scale(s, s, s);
		VertexConsumer halo = buffers.getBuffer(RenderType.entityTranslucentEmissive(RenaRougeRender.BUBBLE));
		quad(halo, pose.last(), -0.5f, -0.5f, 0.5f, 0.5f, FastColor.ARGB32.color(Math.round(200 * fade), 255, 220, 90));
		pose.popPose();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(white()));
		disk(vc, pose.last(), 0.04f, 2.6f, FastColor.ARGB32.color(Math.round(110 * fade * pulse), 255, 215, 80), FastColor.ARGB32.color(0, 255, 215, 80));
	}

	// ---------------------------------------------------------------- Pitfall

	private void pit(MirageFxEntity e, PoseStack pose, MultiBufferSource buffers, float age, float fade) {
		Level level = e.level();
		BlockState look = e.look();
		int size = e.size();
		var dispatcher = Minecraft.getInstance().getBlockRenderer();
		var model = dispatcher.getBlockModel(look);
		int x0 = Mth.floor(e.getX()), y0 = Mth.floor(e.getY()), z0 = Mth.floor(e.getZ());
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				BlockPos bp = new BlockPos(x0 + x, y0, z0 + z);
				if (!level.getBlockState(bp).isAir()) {
					continue;
				}
				pose.pushPose();
				pose.translate(bp.getX() - e.getX(), bp.getY() - e.getY(), bp.getZ() - e.getZ());
				dispatcher.getModelRenderer().tesselateBlock(level, model, look, bp, pose,
						buffers.getBuffer(net.minecraft.client.renderer.ItemBlockRenderTypes.getMovingBlockRenderType(look)), false, RandomSource.create(),
						look.getSeed(bp), OverlayTexture.NO_OVERLAY);
				pose.popPose();
			}
		}
		// the faintest orange shimmer -- the only tell
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(white()));
		float a = (0.07f + 0.05f * Mth.sin(age * 0.2f)) * fade;
		float top = (float) (y0 + 1 - e.getY()) + 0.01f;
		float lx = (float) (x0 - e.getX()), lz = (float) (z0 - e.getZ());
		hquad(vc, pose.last(), lx, top, lz, lx + size, lz + size, FastColor.ARGB32.color(Math.round(255 * a), 255, 140, 40));
	}

	// ---------------------------------------------------------------- Eclipse

	private void eclipse(MirageFxEntity e, PoseStack pose, MultiBufferSource buffers, float age, float fade) {
		float r = e.radius();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(white()));
		PoseStack.Pose last = pose.last();
		int lat = 12, lon = 24;
		int col = FastColor.ARGB32.color(Math.round(175 * fade), 10, 8, 30);
		int rim = FastColor.ARGB32.color(Math.round(120 * fade), 70, 30, 90);
		for (int i = 0; i < lat; i++) {
			// only the upper half and a little below the ground line
			double t0 = -0.15 * Math.PI + (0.65 * Math.PI) * i / lat;
			double t1 = -0.15 * Math.PI + (0.65 * Math.PI) * (i + 1) / lat;
			for (int j = 0; j < lon; j++) {
				double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
				Vector3f a = sph(r, t0, p0), b = sph(r, t0, p1), c = sph(r, t1, p1), d = sph(r, t1, p0);
				int ca = i == 0 ? rim : col;
				v(vc, last, a, ca);
				v(vc, last, b, ca);
				v(vc, last, c, col);
				v(vc, last, d, col);
			}
		}
		// stars
		VertexConsumer st = buffers.getBuffer(RenderType.entityTranslucentEmissive(white()));
		RandomSource rnd = RandomSource.create(e.getId() * 17L);
		for (int i = 0; i < 48; i++) {
			double t = rnd.nextDouble() * 0.45 * Math.PI + 0.03;
			double p = rnd.nextDouble() * Math.PI * 2;
			Vector3f s = sph(r * 0.97f, t, p);
			float tw = 0.5f + 0.5f * Mth.sin(age * 0.3f + i * 1.7f);
			pose.pushPose();
			pose.translate(s.x, s.y, s.z);
			pose.mulPose(entityRenderDispatcher.cameraOrientation());
			float sz = 0.12f + 0.08f * tw;
			quad(st, pose.last(), -sz, -sz, sz, sz, FastColor.ARGB32.color(Math.round(230 * fade * tw), 255, 245, 220));
			pose.popPose();
		}
	}

	private static Vector3f sph(float r, double theta, double phi) {
		return new Vector3f((float) (Math.cos(theta) * Math.cos(phi) * r), (float) (Math.sin(theta) * r), (float) (Math.cos(theta) * Math.sin(phi) * r));
	}

	// ---------------------------------------------------------------- Hall of Mirrors

	private void mirrors(MirageFxEntity e, PoseStack pose, MultiBufferSource buffers, float age, float fade) {
		float r = e.radius();
		TextureAtlasSprite glass = Minecraft.getInstance().getBlockRenderer().getBlockModel(Blocks.WHITE_STAINED_GLASS.defaultBlockState()).getParticleIcon();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));
		VertexConsumer frame = buffers.getBuffer(RenderType.entityTranslucentEmissive(white()));
		int n = 8;
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + age * 0.006;
			float rise = Mth.clamp(age / 12f, 0f, 1f);
			pose.pushPose();
			pose.translate((float) Math.cos(a) * r, 0f, (float) Math.sin(a) * r);
			pose.mulPose(Axis.YP.rotation((float) (-a - Math.PI / 2)));
			PoseStack.Pose last = pose.last();
			float w = 1.1f, h = 3.2f * rise;
			int tint = FastColor.ARGB32.color(Math.round(150 * fade), 255, 225, 200);
			float u0 = glass.getU0(), u1 = glass.getU1(), v0 = glass.getV0(), v1 = glass.getV1();
			vert(vc, last, -w, 0f, 0f, u0, v1, tint);
			vert(vc, last, w, 0f, 0f, u1, v1, tint);
			vert(vc, last, w, h, 0f, u1, v0, tint);
			vert(vc, last, -w, h, 0f, u0, v0, tint);
			int fc = FastColor.ARGB32.color(Math.round(220 * fade), 255, 150, 50);
			float t = 0.07f;
			rect(frame, last, -w - t, -t, -w, h + t, fc);
			rect(frame, last, w, -t, w + t, h + t, fc);
			rect(frame, last, -w, h, w, h + t, fc);
			// a glint sweeping down the glass
			float gy = h * (1f - ((age * 0.02f + i * 0.13f) % 1f));
			rect(frame, last, -w, gy, w, gy + 0.05f, FastColor.ARGB32.color(Math.round(160 * fade), 255, 255, 255));
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- geometry

	private static void vert(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, float u, float v, int argb) {
		vc.addVertex(last, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
				.setNormal(last, 0f, 1f, 0f);
	}

	private static void v(VertexConsumer vc, PoseStack.Pose last, Vector3f p, int argb) {
		vert(vc, last, p.x, p.y, p.z, 0.5f, 0.5f, argb);
	}

	/** A camera-or-pose-facing square (x0,y0)-(x1,y1) at z 0, full texture. */
	private static void quad(VertexConsumer vc, PoseStack.Pose last, float x0, float y0, float x1, float y1, int argb) {
		vert(vc, last, x0, y0, 0f, 0f, 1f, argb);
		vert(vc, last, x1, y0, 0f, 1f, 1f, argb);
		vert(vc, last, x1, y1, 0f, 1f, 0f, argb);
		vert(vc, last, x0, y1, 0f, 0f, 0f, argb);
	}

	/** A flat rectangle in the pose's XY plane. */
	private static void rect(VertexConsumer vc, PoseStack.Pose last, float x0, float y0, float x1, float y1, int argb) {
		vert(vc, last, x0, y0, 0f, 0.5f, 0.5f, argb);
		vert(vc, last, x1, y0, 0f, 0.5f, 0.5f, argb);
		vert(vc, last, x1, y1, 0f, 0.5f, 0.5f, argb);
		vert(vc, last, x0, y1, 0f, 0.5f, 0.5f, argb);
	}

	/** A horizontal rectangle at height y. */
	private static void hquad(VertexConsumer vc, PoseStack.Pose last, float x0, float y, float z0, float x1, float z1, int argb) {
		vert(vc, last, x0, y, z0, 0.5f, 0.5f, argb);
		vert(vc, last, x0, y, z1, 0.5f, 0.5f, argb);
		vert(vc, last, x1, y, z1, 0.5f, 0.5f, argb);
		vert(vc, last, x1, y, z0, 0.5f, 0.5f, argb);
	}

	/** A flat disc on the ground at height y, its colour fading from {@code inner} to {@code outer}. */
	private static void disk(VertexConsumer vc, PoseStack.Pose last, float y, float r, int inner, int outer) {
		int seg = 32;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
			vert(vc, last, 0f, y, 0f, 0.5f, 0.5f, inner);
			vert(vc, last, 0f, y, 0f, 0.5f, 0.5f, inner);
			vert(vc, last, (float) Math.cos(a1) * r, y, (float) Math.sin(a1) * r, 0.5f, 0.5f, outer);
			vert(vc, last, (float) Math.cos(a0) * r, y, (float) Math.sin(a0) * r, 0.5f, 0.5f, outer);
		}
	}

	/** A thin flat strip on the ground from (x0,z0) to (x1,z1), {@code w0} wide at the start, {@code w1}... */
	private static void strip(VertexConsumer vc, PoseStack.Pose last, float x0, float z0, float x1, float z1, float y, float w, int argb) {
		float dx = x1 - x0, dz = z1 - z0;
		float l = Mth.sqrt(dx * dx + dz * dz);
		if (l < 1.0e-3f) {
			return;
		}
		float nx = -dz / l * w * 0.5f, nz = dx / l * w * 0.5f;
		vert(vc, last, x0 - nx, y, z0 - nz, 0.5f, 0.5f, argb);
		vert(vc, last, x0 + nx, y, z0 + nz, 0.5f, 0.5f, argb);
		vert(vc, last, x1 + nx, y, z1 + nz, 0.5f, 0.5f, argb);
		vert(vc, last, x1 - nx, y, z1 - nz, 0.5f, 0.5f, argb);
	}

	/** Two crossed quads of {@code sp} standing at (x,y,z): a flame. */
	private static void cross(VertexConsumer vc, PoseStack.Pose last, TextureAtlasSprite sp, float x, float y, float z, float w, float h) {
		float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
		int c = -1;
		for (int k = 0; k < 2; k++) {
			float ax = k == 0 ? w : 0f, az = k == 0 ? 0f : w;
			vert(vc, last, x - ax, y, z - az, u0, v1, c);
			vert(vc, last, x + ax, y, z + az, u1, v1, c);
			vert(vc, last, x + ax, y + h, z + az, u1, v0, c);
			vert(vc, last, x - ax, y + h, z - az, u0, v0, c);
			// back face
			vert(vc, last, x - ax, y + h, z - az, u0, v0, c);
			vert(vc, last, x + ax, y + h, z + az, u1, v0, c);
			vert(vc, last, x + ax, y, z + az, u1, v1, c);
			vert(vc, last, x - ax, y, z - az, u0, v1, c);
		}
	}

	@Override
	public boolean shouldRender(MirageFxEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
		return frustum.isVisible(e.getBoundingBoxForCulling());
	}

	@Override
	public ResourceLocation getTextureLocation(MirageFxEntity e) {
		return WHITE;
	}
}
