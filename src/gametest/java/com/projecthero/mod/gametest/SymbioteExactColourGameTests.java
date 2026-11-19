package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.SymbioteColourPayload;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteColourPreset;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteSuitColours;
import com.projecthero.mod.symbiote.SymbioteTintMath;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * v0.17.5 tendril-vfx round (user: "you cant make the emblem fully black or the eyes fully black ... make sure the symbiote suit is
 * fully customizable"): every colour part takes any exact RGB, pure black and pure white included -- through storage, the network
 * and the colour maths the renderer uses ({@link SymbioteTintMath}) -- and the new fourth part, the mouth.
 */
public class SymbioteExactColourGameTests implements FabricGameTest {
	/** Mask texels as the Lethal Protector mask has them: the emblem / eyes sit at shade 255 (the emblem with gloss 94). */
	private static final int SHADE_TOP = 255, EMBLEM_GLOSS = 94, BODY_MID = 128;

	private static ServerPlayer host(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Symbiote.grant(p);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void blackAndWhiteEmblemAndEyesBakeExactly(GameTestHelper helper) {
		SymbioteColours black = new SymbioteColours(0x14161F, 0x000000, 0x000000, 0x000000);
		SymbioteColours white = new SymbioteColours(0x14161F, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF);
		// emblem (B 128) and eyes (B 255) at their brightest, glossy texels: the colour picked, not a lifted grey
		helper.assertTrue(SymbioteTintMath.bake(SHADE_TOP, EMBLEM_GLOSS, 128, 255, black) == 0x000000, "black emblem is black");
		helper.assertTrue(SymbioteTintMath.bake(SHADE_TOP, 0, 255, 255, black) == 0x000000, "black eyes are black");
		helper.assertTrue(SymbioteTintMath.bake(SHADE_TOP, EMBLEM_GLOSS, 128, 255, white) == 0xFFFFFF, "white emblem is white");
		helper.assertTrue(SymbioteTintMath.bake(SHADE_TOP, 0, 255, 255, white) == 0xFFFFFF, "white eyes are white");
		// any colour at the top shade without gloss comes out exactly as picked
		for (int rgb : new int[] { 0x102030, 0xC81E2A, 0x33FF66, 0x7F7F7F, 0x010101, 0xFEFEFE }) {
			SymbioteColours c = new SymbioteColours(0, rgb, rgb);
			helper.assertTrue(SymbioteTintMath.bake(SHADE_TOP, 0, 128, 255, c) == rgb, "emblem exact: " + SymbioteColours.hex(rgb));
			helper.assertTrue(SymbioteTintMath.bake(SHADE_TOP, 0, 255, 255, c) == rgb, "eyes exact: " + SymbioteColours.hex(rgb));
		}
		// shade 0.5 is the body colour exactly, black and white included
		helper.assertTrue(SymbioteTintMath.bake(BODY_MID, 0, 0, 255, new SymbioteColours(0x000000, 0, 0)) == 0x000000, "black body");
		helper.assertTrue(SymbioteTintMath.bake(BODY_MID, 0, 0, 255, new SymbioteColours(0xFFFFFF, 0, 0)) == 0xFFFFFF, "white body");
		// the mouth channel (B 32) uses the mouth colour
		helper.assertTrue(SymbioteTintMath.channelOf(32) == SymbioteTintMath.MOUTH && SymbioteTintMath.channelOf(0) == SymbioteTintMath.BODY
				&& SymbioteTintMath.channelOf(128) == SymbioteTintMath.EMBLEM && SymbioteTintMath.channelOf(255) == SymbioteTintMath.EYES, "channels");
		helper.assertTrue(SymbioteTintMath.bake(BODY_MID, 0, 32, 255, black) == 0x000000, "black mouth");
		helper.assertTrue(SymbioteTintMath.bake(BODY_MID, 0, 32, 255, new SymbioteColours(0, 0, 0, 0x2F6FD6)) == 0x2F6FD6, "blue mouth");
		helper.assertTrue(SymbioteTintMath.bake(BODY_MID, 0, 32, 0, black) == 0, "transparent stays transparent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theEyeGlowNeverBrightensDarkEyes(GameTestHelper helper) {
		helper.assertTrue(SymbioteTintMath.eyeGlowStrength(0x000000) == 0f, "black eyes: no glow");
		helper.assertTrue(SymbioteTintMath.eyeGlowStrength(0x401010) == 0f, "dark red eyes: no glow");
		helper.assertTrue(Math.abs(SymbioteTintMath.eyeGlowStrength(0xFFFFFF) - 0.4f) < 1.0e-4f, "white eyes: the old 40% glow");
		float last = -1f;
		for (int v = 0; v <= 255; v += 5) {
			float k = SymbioteTintMath.eyeGlowStrength(v << 16 | v << 8 | v);
			helper.assertTrue(k >= last, "the glow grows with brightness");
			last = k;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pureBlackAndWhiteRoundTripEverywhere(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		for (SymbioteColours c : new SymbioteColours[] { new SymbioteColours(0x000000, 0x000000, 0x000000, 0x000000),
				new SymbioteColours(0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF), new SymbioteColours(0x000000, 0xFFFFFF, 0x000000, 0xFFFFFF) }) {
			// the screen's payload, applied server side (bonded)
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			SymbioteColourPayload.CODEC.encode(buf, new SymbioteColourPayload(c));
			SymbioteColours sent = SymbioteColourPayload.CODEC.decode(buf).colours();
			helper.assertTrue(sent.equals(c), "payload keeps " + c.key());
			helper.assertTrue(SymbioteSuitColours.apply(p, sent), "bonded player can apply it");
			helper.assertTrue(SymbioteSuitColours.get(p).equals(c), "stored exactly");
			// the save
			Tag saved = SymbioteColours.CODEC.encodeStart(NbtOps.INSTANCE, c).getOrThrow();
			helper.assertTrue(SymbioteColours.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow().equals(c), "save keeps " + c.key());
			// the attachment sync
			RegistryFriendlyByteBuf sync = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			SymbioteColours.STREAM_CODEC.encode(sync, c);
			helper.assertTrue(SymbioteColours.STREAM_CODEC.decode(sync).equals(c), "sync keeps " + c.key());
		}
		helper.assertTrue(SymbioteColours.parseHex("#000000").orElse(-1) == 0 && SymbioteColours.parseHex("ffffff").orElse(-1) == 0xFFFFFF,
				"the hex field reads black and white");
		helper.assertTrue(ModAttachments.SYMBIOTE_COLOURS.isSynced(), "synced to everyone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theMouthIsAFourthColour(GameTestHelper helper) {
		// an old save without a mouth reads the natural mouth
		CompoundTag old = new CompoundTag();
		old.putInt("primary", 0x123456);
		old.putInt("secondary", 0x000000);
		old.putInt("eyes", 0xFFFFFF);
		SymbioteColours read = SymbioteColours.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(read.mouth() == SymbioteColours.NATURAL_MOUTH && read.secondary() == 0, "old save: natural mouth, black emblem");
		helper.assertTrue(SymbioteColours.CLASSIC.mouth() == SymbioteColours.NATURAL_MOUTH, "Classic has the natural mouth");
		helper.assertTrue(read.channel(3) == read.mouth() && read.withChannel(3, 0x00FF00).mouth() == 0x00FF00, "channel 3 is the mouth");
		helper.assertTrue(new SymbioteColours(1, 2, 3, 0xAB000000).mouth() == 0, "the mouth is masked to 24 bits");
		helper.assertTrue(!new SymbioteColours(1, 2, 3, 4).key().equals(new SymbioteColours(1, 2, 3, 5).key()), "the mouth is in the bake key");
		helper.assertTrue(SymbioteColours.CLASSIC.key().equals("14161f_f2f4f8_f4f6fa"), "natural-mouth keys unchanged");
		// presets / the colour commands keep a player's mouth
		ServerPlayer p = host(helper);
		SymbioteSuitColours.set(p, SymbioteColours.CLASSIC.withMouth(0x00FF00));
		SymbioteSuitColours.setPreset(p, SymbioteColourPreset.CARNAGE);
		SymbioteColours now = SymbioteSuitColours.get(p);
		helper.assertTrue(now.sameColours(SymbioteColourPreset.CARNAGE.colours()) && now.mouth() == 0x00FF00, "a preset keeps the mouth");
		helper.assertTrue(SymbioteColourPreset.matching(now).orElse(null) == SymbioteColourPreset.CARNAGE, "and still matches the preset");
		helper.succeed();
	}
}
