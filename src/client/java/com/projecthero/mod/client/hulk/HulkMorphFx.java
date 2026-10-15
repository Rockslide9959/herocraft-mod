package com.projecthero.mod.client.hulk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import com.projecthero.mod.hulk.Hulk;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.16.4: the client-side effects of the Hulk morph ({@link HulkMorph}), for every Gamma player in view:
 * <ul>
 *   <li>while the green spreads, his shirt tears in a few beats -- shreds in his shirt's own colours (wool-crack particles of
 *       the nearest dye plus exact-colour dust) burst off his chest and arms;</li>
 *   <li>the swap: a bright gamma flash (a flash particle on him; a screen flash for him, a fainter one for anyone close), a big
 *       burst of torn cloth and green, and a deep roar over a heartbeat;</li>
 *   <li>after it, a green glow coming off the Hulk, fading;</li>
 *   <li>the change back's swap: a dim green puff and a slow heartbeat.</li>
 * </ul>
 * It also reads each morphing player's skin ahead of the render ({@link HulkMorphSkin#prepare}) and releases finished morph
 * textures ({@link HulkMorphSkin#sweep}).
 */
public final class HulkMorphFx {
	/** Green coverage at which a strip of shirt tears off. */
	private static final float[] TEAR_AT = { 0.28f, 0.52f, 0.76f };
	/** Screen flash length (ticks) and how far other viewers still get a (fainter) one. */
	private static final float FLASH_TICKS = 9.0f;
	private static final double FLASH_RANGE = 20.0;

	private static final Block[] WOOL = { Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL, Blocks.LIGHT_BLUE_WOOL,
			Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.PINK_WOOL, Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL, Blocks.CYAN_WOOL,
			Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL, Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL, Blocks.BLACK_WOOL };
	private static final DustParticleOptions GAMMA = new DustParticleOptions(new Vector3f(0.42f, 1.0f, 0.3f), 1.4f);
	private static final DustParticleOptions DEEP = new DustParticleOptions(new Vector3f(0.14f, 0.5f, 0.12f), 1.2f);

	private static final class Track {
		HulkMorph.Phase phase = HulkMorph.Phase.NONE;
		float green = -1.0f;
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();
	private static long flashAt = Long.MIN_VALUE;
	private static float flashStrength;

	private HulkMorphFx() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(HulkMorphFx::tick);
		HudRenderCallback.EVENT.register(HulkMorphFx::renderFlash);
	}

	private static void tick(Minecraft mc) {
		HulkMorphSkin.sweep(mc);
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused()) {
			if (level == null) {
				TRACKS.clear();
			}
			return;
		}
		TRACKS.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
		for (Player p : level.players()) {
			if (!Hulk.hasPower(p)) {
				TRACKS.remove(p.getUUID());
				continue;
			}
			HulkMorph.Phase now = HulkMorph.phase(p, 0.0f);
			Track tr = TRACKS.get(p.getUUID());
			if (tr == null) {
				// first sight of him (or he just got the power): no effects for what already happened
				tr = new Track();
				tr.phase = now;
				tr.green = HulkMorph.green(p, 0.0f);
				TRACKS.put(p.getUUID(), tr);
			}
			if (now != HulkMorph.Phase.NONE) {
				HulkMorphSkin.prepare(p);
			}
			float green = HulkMorph.green(p, 0.0f);
			if (now == HulkMorph.Phase.GREENING && tr.phase == HulkMorph.Phase.GREENING) {
				for (float at : TEAR_AT) {
					if (tr.green < at && green >= at) {
						tear(mc, level, p, 10, 0.18);
						play(level, p, SoundEvents.WOOL_BREAK, 0.9f, 0.55f + level.random.nextFloat() * 0.2f);
						play(level, p, SoundEvents.WARDEN_HEARTBEAT, 0.8f, 0.7f);
					}
				}
			}
			if (tr.phase == HulkMorph.Phase.GREENING && now != HulkMorph.Phase.GREENING && Hulk.isHulk(p)) {
				swap(mc, level, p);
			}
			if (tr.phase == HulkMorph.Phase.SHRINKING && now == HulkMorph.Phase.RECEDING) {
				puff(level, p);
			}
			float glow = HulkMorph.glow(p, 0.0f);
			if (glow > 0.02f && !p.isInvisible()) {
				int n = Math.round(4.0f * glow);
				for (int i = 0; i < n; i++) {
					spawnAround(level, p, GAMMA, 0.55, 0.02);
				}
			}
			tr.phase = now;
			tr.green = green;
		}
	}

	/** The swap into the Hulk: flash, a burst of cloth and green, the roar. */
	private static void swap(Minecraft mc, ClientLevel level, Player p) {
		double cy = p.getY() + p.getBbHeight() * 0.55;
		if (!p.isInvisible()) {
			level.addParticle(ParticleTypes.FLASH, p.getX(), cy, p.getZ(), 0.0, 0.0, 0.0);
			tear(mc, level, p, 34, 0.35);
			for (int i = 0; i < 36; i++) {
				spawnAround(level, p, GAMMA, 0.5, 0.25);
			}
		}
		play(level, p, SoundEvents.RAVAGER_ROAR, 1.4f, 0.55f);
		play(level, p, SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.5f);
		play(level, p, SoundEvents.WOOL_BREAK, 1.2f, 0.5f);
		if (mc.player != null) {
			float strength = 0.0f;
			if (p == mc.player) {
				strength = 1.0f;
			} else {
				double d = mc.player.distanceTo(p);
				if (d < FLASH_RANGE) {
					strength = (float) (0.5 * (1.0 - d / FLASH_RANGE));
				}
			}
			if (strength > 0.0f) {
				flashAt = level.getGameTime();
				flashStrength = strength;
			}
		}
	}

	/** The change back's swap: a dim green puff. */
	private static void puff(ClientLevel level, Player p) {
		if (!p.isInvisible()) {
			for (int i = 0; i < 30; i++) {
				spawnAround(level, p, DEEP, 0.6, 0.06);
			}
		}
		play(level, p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.75f);
	}

	/** Shreds of his shirt flying off the chest and arms. */
	private static void tear(Minecraft mc, ClientLevel level, Player p, int count, double speed) {
		if (p.isInvisible()) {
			return;
		}
		int[] cloth = HulkMorphSkin.clothColours(p);
		RandomSource r = level.random;
		double h = p.getBbHeight();
		double w = p.getBbWidth();
		for (int i = 0; i < count; i++) {
			int rgb = cloth.length == 0 ? 0x8A8A8A : cloth[r.nextInt(cloth.length)];
			double a = r.nextDouble() * Math.PI * 2.0;
			double x = p.getX() + Math.cos(a) * w * 0.45;
			double z = p.getZ() + Math.sin(a) * w * 0.45;
			double y = p.getY() + h * (0.45 + r.nextDouble() * 0.3);
			double vx = Math.cos(a) * speed * (0.6 + r.nextDouble());
			double vz = Math.sin(a) * speed * (0.6 + r.nextDouble());
			double vy = speed * (0.3 + r.nextDouble() * 0.8);
			if (i % 2 == 0) {
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, WOOL[nearestDye(rgb)].defaultBlockState()), x, y, z, vx, vy, vz);
			} else {
				Vector3f c = new Vector3f(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f);
				// dust slows its start velocity to a tenth -- hand it ten times as much
				level.addParticle(new DustParticleOptions(c, 1.3f), x, y, z, vx * 10.0, vy * 10.0, vz * 10.0);
			}
		}
	}

	private static void spawnAround(ClientLevel level, Player p, DustParticleOptions dust, double spread, double speed) {
		RandomSource r = level.random;
		double w = p.getBbWidth() * spread;
		double x = p.getX() + (r.nextDouble() - 0.5) * 2.0 * w;
		double y = p.getY() + r.nextDouble() * p.getBbHeight();
		double z = p.getZ() + (r.nextDouble() - 0.5) * 2.0 * w;
		double s = speed * 10.0;
		level.addParticle(dust, x, y, z, (r.nextDouble() - 0.5) * s, r.nextDouble() * s, (r.nextDouble() - 0.5) * s);
	}

	private static void play(ClientLevel level, Player p, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
	}

	private static int nearestDye(int rgb) {
		int best = 0;
		long bestD = Long.MAX_VALUE;
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		DyeColor[] dyes = DyeColor.values();
		for (int i = 0; i < dyes.length && i < WOOL.length; i++) {
			int c = dyes[i].getTextureDiffuseColor();
			long dr = r - ((c >> 16) & 0xFF), dg = g - ((c >> 8) & 0xFF), db = b - (c & 0xFF);
			long d = dr * dr + dg * dg + db * db;
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	/** The swap's screen flash: pale gamma green, gone in under half a second. */
	private static void renderFlash(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || flashAt == Long.MIN_VALUE) {
			return;
		}
		float since = (mc.level.getGameTime() - flashAt) + delta.getGameTimeDeltaPartialTick(false);
		if (since < 0.0f || since >= FLASH_TICKS) {
			if (since >= FLASH_TICKS) {
				flashAt = Long.MIN_VALUE;
			}
			return;
		}
		float f = 1.0f - since / FLASH_TICKS;
		int alpha = Math.round(235 * flashStrength * f * f);
		if (alpha > 0) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | 0xE6FFD2);
		}
	}
}
