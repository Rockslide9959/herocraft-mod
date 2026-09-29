// v0.13.21: Hulk origin -> Gamma Serum doses you, the Gamma Reactor overload grants the power.
const fs=require('fs');
function edit(f, pairs){let raw=fs.readFileSync(f,'utf8');const crlf=raw.includes('\r\n');let s=raw.replace(/\r\n/g,'\n');
 for(const [a,b] of pairs){ if(!s.includes(a)) throw new Error(f+': not found: '+a.slice(0,80)); s=s.replace(a,b);} fs.writeFileSync(f,crlf?s.replace(/\n/g,'\r\n'):s);}
edit('src/main/java/com/projecthero/mod/hulk/GammaOverload.java',[[`		level.removeBlock(o.reactor(), false);
		level.setBlock(o.reactor(), Blocks.AIR.defaultBlockState(), 3);
`,`		level.setBlock(o.reactor(), Blocks.AIR.defaultBlockState(), 3);
`]]);
edit('src/main/java/com/projecthero/mod/attachment/ModAttachments.java',[[`	/**
	 * v0.13.11: the Hulk -- the Gamma power,`,`	/**
	 * v0.13.21: the Gamma Serum is in this player's blood -- right-clicking a Gamma Reactor now overloads it and grants
	 * the Gamma power ({@code hulk.GammaOverload}). Persistent and kept through death (the serum is loot-only).
	 */
	public static final AttachmentType<Boolean> GAMMA_DOSED = AttachmentRegistry.create(
			ProjectHeroMod.id("gamma_dosed"),
			builder -> builder.persistent(com.mojang.serialization.Codec.BOOL).copyOnDeath().initializer(() -> false));

	/**
	 * v0.13.11: the Hulk -- the Gamma power,`]]);
edit('src/main/java/com/projecthero/mod/hulk/Hulk.java',[[`		HulkCalm.clearSessionState();
	}`,`		HulkCalm.clearSessionState();
		GammaOverload.clearSessionState();
	}`]]);
edit('src/main/java/com/projecthero/mod/ProjectHeroMod.java',[[`		com.projecthero.mod.hulk.HulkDamage.initialize();`,`		com.projecthero.mod.hulk.HulkDamage.initialize();
		com.projecthero.mod.hulk.GammaOverload.initialize();`]]);
edit('src/main/java/com/projecthero/mod/hulk/item/GammaSerumItem.java',[
[`/**
 * v0.13.12 (Hulk Phase 4): the Gamma Serum. Drink it (like a potion) and the Gamma power is yours for good. Loot
 * only -- it is found in the chest of a rare, ruined Gamma Lab ({@code hulk/worldgen/GammaLabPiece}); never crafted.
 * All the rules live in {@link Hulk#grant} (it replaces the oldest Primary power like every other hero).
 */`,`/**
 * v0.13.12 (Hulk Phase 4): the Gamma Serum. Loot only -- it is found in the chest of a rare, ruined Gamma Lab
 * ({@code hulk/worldgen/GammaLabPiece}); never crafted.
 *
 * <p>v0.13.21: drinking it no longer hands the power over. It doses the drinker ({@link GammaOverload#setDosed});
 * he then has to right-click a Gamma Reactor, which overloads and explodes -- and the Gamma in his blood is what lets
 * him survive it and become the Hulk ({@link GammaOverload}).
 */`],
[`import com.projecthero.mod.hulk.Hulk;
`,`import com.projecthero.mod.hulk.GammaOverload;
import com.projecthero.mod.hulk.Hulk;
`],
[`		if (!Hulk.grant(player)) {
			return stack;
		}`,`		if (Hulk.hasPower(player) || GammaOverload.isDosed(player)) {
			return stack;
		}
		GammaOverload.setDosed(player, true);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 160, 0));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 200, 0, false, false));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT,
				net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.7f);
		player.displayClientMessage(Component.translatable("message.projecthero.gamma_serum.dosed")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.gamma_serum.dosed_hint")
				.withStyle(ChatFormatting.DARK_GREEN), false);`],
[`		if (Hulk.hasPower(player)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.gamma_serum.already")
						.withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}`,`		if (Hulk.hasPower(player)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.gamma_serum.already")
						.withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		if (player instanceof ServerPlayer sp && GammaOverload.isDosed(sp)) {
			player.displayClientMessage(Component.translatable("message.projecthero.gamma_serum.already_dosed")
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}`],
]);
edit('src/main/java/com/projecthero/mod/hulk/block/GammaReactorBlock.java',[
[` * climb ({@code Hulk#tick}); nothing else happens to anyone else. Pickaxe-mined, drops itself.
 */`,` * climb ({@code Hulk#tick}); nothing else happens to anyone else. Pickaxe-mined, drops itself.
 *
 * <p>v0.13.21: right-clicking it with the Gamma Serum in your blood overloads it -- a huge explosion you survive
 * because it makes you the Hulk ({@link com.projecthero.mod.hulk.GammaOverload}).
 */`],
[`	@Override
	public void animateTick(`,`	@Override
	protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
			com.projecthero.mod.hulk.GammaOverload.onReactorUsed(sp, pos);
		}
		return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public void animateTick(`],
]);
console.log('ok');
