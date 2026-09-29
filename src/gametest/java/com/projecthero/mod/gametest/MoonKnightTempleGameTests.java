package com.projecthero.mod.gametest;

import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightLunar;
import com.projecthero.mod.moonknight.temple.KhonshuAltarBlock;
import com.projecthero.mod.moonknight.temple.KhonshuAltarBlockEntity;
import com.projecthero.mod.moonknight.temple.KhonshuRitual;
import com.projecthero.mod.moonknight.temple.KhonshuTemple;
import com.projecthero.mod.moonknight.temple.TempleOfKhonshuPiece;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Moon Knight Phase 7: the Temple of Khonshu piece, and the pact ritual on the Altar of Khonshu. Mock players are not
 * ticked reliably, so the altar is put in test-driven mode ({@code testDriven}) and {@link KhonshuRitual#tick} is
 * called directly; night / sky are forced per altar ({@code forcedNight} / {@code forcedSky}) instead of changing the
 * shared world clock, which would leak into tests running alongside.
 */
public class MoonKnightTempleGameTests implements FabricGameTest {
	private static final BlockPos ALTAR = new BlockPos(3, 1, 3);

	private record Rig(ServerLevel level, BlockPos altar, KhonshuAltarBlockEntity be, ServerPlayer player) {
	}

	private static Rig rig(GameTestHelper helper, boolean night) {
		ServerLevel level = helper.getLevel();
		BlockPos altar = helper.absolutePos(ALTAR);
		level.setBlock(altar, KhonshuTemple.KHONSHU_ALTAR.defaultBlockState(), 3);
		KhonshuAltarBlockEntity be = (KhonshuAltarBlockEntity) level.getBlockEntity(altar);
		be.testDriven = true;
		be.forcedNight = night;
		be.forcedSky = true;
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.moveTo(altar.getX() + 0.5, altar.getY() + 1.0, altar.getZ() + 0.5, 0.0f, 0.0f);
		return new Rig(level, altar, be, p);
	}

	private static ItemStack scarab() {
		return new ItemStack(KhonshuTemple.SCARAB_OF_KHONSHU);
	}

	private static void tick(Rig r, int n) {
		for (int i = 0; i < n; i++) {
			KhonshuRitual.tick(r.level(), r.altar(), r.be());
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true, timeoutTicks = 200)
	public void templePieceBuildsAnAltarUnderOpenSkyAndAChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos lo = helper.absolutePos(new BlockPos(0, 0, 0));
		BlockPos hi = helper.absolutePos(new BlockPos(7, 7, 7));
		BlockPos centre = helper.absolutePos(new BlockPos(4, 0, 4));
		// only build inside this test's own 8 x 8 column (the piece is 31 wide): the altar and chest are both central
		BoundingBox clip = new BoundingBox(Math.min(lo.getX(), hi.getX()), level.getMinBuildHeight(), Math.min(lo.getZ(), hi.getZ()),
				Math.max(lo.getX(), hi.getX()), level.getMaxBuildHeight() - 1, Math.max(lo.getZ(), hi.getZ()));
		TempleOfKhonshuPiece piece = new TempleOfKhonshuPiece(centre.getX(), centre.getY() + 9, centre.getZ());
		piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.random, clip,
				new ChunkPos(centre), centre);

		BlockPos altar = piece.altarPos();
		helper.assertTrue(level.getBlockState(altar).is(KhonshuTemple.KHONSHU_ALTAR), "the altar stands at the temple's heart");
		helper.assertFalse(level.getBlockState(altar).getValue(KhonshuAltarBlock.SPENT), "a new temple's altar is unspent");
		helper.assertTrue(level.getBlockEntity(altar) instanceof KhonshuAltarBlockEntity be
				&& be.altarState() == KhonshuAltarBlockEntity.AltarState.DORMANT_READY, "and waiting for a scarab");
		for (int y = altar.getY() + 1; y <= altar.getY() + 24; y++) {
			helper.assertTrue(level.getBlockState(new BlockPos(altar.getX(), y, altar.getZ())).isAir(),
					"the oculus leaves the column above the altar open (y+" + (y - altar.getY()) + ")");
		}
		helper.assertTrue(level.getBlockState(altar.above(8)).isAir() && level.getBlockState(altar.offset(0, 6, -4)).isSolid(),
				"a roof all around, a hole straight above");
		BlockPos chest = piece.chestPos();
		helper.assertTrue(chest.getY() < altar.getY() - 3, "the chest is in the hidden chamber below the hall");
		helper.assertTrue(level.getBlockEntity(chest) instanceof ChestBlockEntity c && TempleOfKhonshuPiece.LOOT.equals(c.getLootTable()),
				"the chamber's chest rolls the temple loot table (always the Scarab)");
		helper.assertTrue(level.getBlockState(chest.above()).isAir(), "the chest can be opened");

		// the sky light has to catch up with the new roof before the altar's moonlight check can be trusted
		helper.succeedWhen(() -> {
			helper.assertTrue(MoonKnightLunar.hasSky(level, altar.above()), "the altar sees the sky");
			helper.assertFalse(MoonKnightLunar.hasSky(level, altar.offset(0, 1, -4)), "the rest of the hall is roofed");
			// tidy the column so nothing lingers over later tests
			for (BlockPos p : BlockPos.betweenClosed(clip.minX(), lo.getY(), clip.minZ(), clip.maxX(), lo.getY() + 40, clip.maxZ())) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true)
	public void ritualCompletesAfterTenSecondsOfKneelingAtNight(GameTestHelper helper) {
		Rig r = rig(helper, true);
		ItemStack stack = scarab();
		helper.assertTrue(KhonshuRitual.placeScarab(r.player(), r.level(), r.altar(), stack), "the scarab is laid at night");
		helper.assertTrue(stack.isEmpty(), "and consumed into the altar");
		helper.assertTrue(r.be().altarState() == KhonshuAltarBlockEntity.AltarState.HOLDING_SCARAB, "the altar holds it");
		helper.assertFalse(KhonshuRitual.placeScarab(helper.makeMockServerPlayerInLevel(), r.level(), r.altar(), scarab()),
				"one ritual at a time per altar");

		r.player().setShiftKeyDown(true);
		helper.assertTrue(KhonshuRitual.isOnAltar(r.player(), r.altar()), "standing on the altar");
		tick(r, KhonshuRitual.KNEEL_TICKS - 1);
		helper.assertFalse(r.be().inRebirth(), "not yet -- 199 ticks of kneeling");
		helper.assertTrue(r.be().progress() == KhonshuRitual.KNEEL_TICKS - 1, "progress counts kneeling ticks");
		tick(r, 1);
		helper.assertTrue(r.be().inRebirth(), "200 ticks: the flash and the 'death'");
		helper.assertTrue(KhonshuRitual.isBeingReborn(r.player()), "the player is held between death and rebirth");
		helper.assertFalse(r.player().hurt(r.level().damageSources().generic(), 5.0f), "and cannot be hurt meanwhile");
		helper.assertFalse(MoonKnight.hasPower(r.player()), "no pact until they rise");

		tick(r, KhonshuRitual.REBIRTH_TICKS);
		helper.assertTrue(MoonKnight.hasPower(r.player()), "risen: the pact is sealed");
		helper.assertFalse(KhonshuRitual.isBeingReborn(r.player()), "the rebirth is over");
		helper.assertTrue(r.player().isAlive(), "and nobody actually died");
		helper.assertTrue(r.be().altarState() == KhonshuAltarBlockEntity.AltarState.SPENT, "the altar is spent");
		helper.assertTrue(r.level().getBlockState(r.altar()).getValue(KhonshuAltarBlock.SPENT), "and cracked");
		helper.assertTrue(Math.abs(r.player().getX() - (r.altar().getX() + 0.5)) < 0.01
				&& Math.abs(r.player().getY() - (r.altar().getY() + 1.0)) < 0.01, "they rise on top of the altar");
		helper.assertItemEntityNotPresent(KhonshuTemple.SCARAB_OF_KHONSHU, ALTAR.above(), 3.0);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true)
	public void standingUpCancelsAndThePopsTheScarab(GameTestHelper helper) {
		Rig r = rig(helper, true);
		helper.assertTrue(KhonshuRitual.placeScarab(r.player(), r.level(), r.altar(), scarab()), "the scarab is laid");
		// waiting to kneel is fine for a while
		r.player().setShiftKeyDown(false);
		tick(r, 40);
		helper.assertTrue(r.be().altarState() == KhonshuAltarBlockEntity.AltarState.HOLDING_SCARAB, "the altar waits for a kneel");
		r.player().setShiftKeyDown(true);
		tick(r, 60);
		helper.assertTrue(r.be().progress() == 60, "kneeling");
		r.player().setShiftKeyDown(false);
		tick(r, 1);
		helper.assertTrue(r.be().altarState() == KhonshuAltarBlockEntity.AltarState.DORMANT_READY, "standing up breaks the ritual");
		helper.assertFalse(MoonKnight.hasPower(r.player()), "no pact");
		helper.assertItemEntityPresent(KhonshuTemple.SCARAB_OF_KHONSHU, ALTAR.above(), 2.5);
		helper.assertFalse(r.level().getBlockState(r.altar()).getValue(KhonshuAltarBlock.SPENT), "the altar can be tried again");

		// ...and so does stepping off, after a fresh start
		helper.assertTrue(KhonshuRitual.placeScarab(r.player(), r.level(), r.altar(), scarab()), "a second attempt");
		r.player().setShiftKeyDown(true);
		tick(r, 20);
		r.player().moveTo(r.altar().getX() + 3.5, r.altar().getY(), r.altar().getZ() + 0.5);
		tick(r, 1);
		helper.assertTrue(r.be().altarState() == KhonshuAltarBlockEntity.AltarState.DORMANT_READY, "stepping off breaks it too");
		helper.assertItemEntityCountIs(KhonshuTemple.SCARAB_OF_KHONSHU, ALTAR.above(), 3.0, 2);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true)
	public void aSpentAltarRefuses(GameTestHelper helper) {
		Rig r = rig(helper, true);
		KhonshuRitual.spend(r.level(), r.altar(), r.be());
		ItemStack stack = scarab();
		helper.assertFalse(KhonshuRitual.placeScarab(r.player(), r.level(), r.altar(), stack), "a spent altar refuses the scarab");
		helper.assertTrue(stack.getCount() == 1, "which stays in hand");
		helper.assertTrue(r.level().getBlockState(r.altar()).getValue(KhonshuAltarBlock.SPENT), "the spent altar looks cracked");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true)
	public void theAltarRefusesByDayAndBreaksAtDawn(GameTestHelper helper) {
		Rig r = rig(helper, false);
		ItemStack stack = scarab();
		helper.assertFalse(KhonshuRitual.placeScarab(r.player(), r.level(), r.altar(), stack), "by day the altar is dark");
		helper.assertTrue(stack.getCount() == 1, "the scarab stays in hand");

		r.be().forcedNight = true;
		helper.assertTrue(KhonshuRitual.placeScarab(r.player(), r.level(), r.altar(), stack), "night falls");
		r.player().setShiftKeyDown(true);
		tick(r, 30);
		r.be().forcedNight = false;
		tick(r, 1);
		helper.assertTrue(r.be().altarState() == KhonshuAltarBlockEntity.AltarState.DORMANT_READY, "dawn cancels the ritual");
		helper.assertItemEntityPresent(KhonshuTemple.SCARAB_OF_KHONSHU, ALTAR.above(), 2.5);

		// a pact-holder is refused, and so is an altar that cannot see the sky
		r.be().forcedNight = true;
		MoonKnight.grant(r.player(), false);
		helper.assertFalse(KhonshuRitual.placeScarab(r.player(), r.level(), r.altar(), scarab()), "the pact is only made once");
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		r.be().forcedSky = false;
		helper.assertFalse(KhonshuRitual.placeScarab(other, r.level(), r.altar(), scarab()), "no sky, no moonlight");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true)
	public void aRitualReadBackFromDiskIsCancelled(GameTestHelper helper) {
		Rig r = rig(helper, true);
		helper.assertTrue(KhonshuRitual.placeScarab(r.player(), r.level(), r.altar(), scarab()), "the scarab is laid");
		r.player().setShiftKeyDown(true);
		tick(r, 50);
		// simulate a save + reload of the block entity mid-ritual
		var tag = r.be().saveWithoutMetadata(r.level().registryAccess());
		r.be().loadWithComponents(tag, r.level().registryAccess());
		tick(r, 1);
		helper.assertTrue(r.be().altarState() == KhonshuAltarBlockEntity.AltarState.DORMANT_READY, "a reloaded ritual is cancelled");
		helper.assertItemEntityPresent(KhonshuTemple.SCARAB_OF_KHONSHU, ALTAR.above(), 2.5);
		helper.assertFalse(MoonKnight.hasPower(r.player()), "and grants nothing");
		helper.succeed();
	}
}
