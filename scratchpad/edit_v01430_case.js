const fs=require('fs');
function edit(f,pairs){let s=fs.readFileSync(f,'utf8').replace(/\r\n/g,'\n');for(const [a,b] of pairs){const n=s.split(a).length-1;if(n!==1)throw f+': '+n+' matches for: '+a.slice(0,80);s=s.replace(a,b);}fs.writeFileSync(f,s.replace(/\n/g,'\r\n'));}
const P='src/main/java/com/projecthero/mod/ironman/fabricator/';
edit(P+'IronManSuitPlatformBlockEntity.java',[[
`	// ---------------- v0.14.21: animated deploy / retrieve ----------------`,
`	/**
	 * v0.14.30, explicit user request: a Mark 5 Suitcase handed to the platform (right-click it with the case, Sneak +
	 * right-click "retrieve" while carrying one, or shift-click it in the platform screen) unfolds into the four armour
	 * pieces on the rack. All-or-nothing: nothing moves unless every piece has a free slot on a rack that can hold the
	 * Mark 5. A never-used (legacy) case unfolds a fresh suit. Uses up the case.
	 */
	public boolean storeSuitcase(Player player, ItemStack caseStack) {
		if (!caseStack.is(com.projecthero.mod.ironman.item.IronManItems.MARK_V_SUITCASE) || sequenceRunning()
				|| !matchesStoredSuit("mark_v")) {
			return false;
		}
		java.util.List<ItemStack> contents = new java.util.ArrayList<>();
		if (com.projecthero.mod.ironman.item.SuitcaseContents.isLegacyEmpty(caseStack)) {
			for (ArmorItem.Type t : new ArmorItem.Type[] { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
					ArmorItem.Type.BOOTS }) {
				contents.add(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_v", t)));
			}
		} else {
			contents.addAll(com.projecthero.mod.ironman.item.SuitcaseContents.nonEmpty(caseStack));
		}
		if (contents.isEmpty()) {
			return false;
		}
		for (ItemStack piece : contents) {
			if (!(piece.getItem() instanceof IronManArmorItem a) || slotOf(a.getType()) < 0 || !pieces.get(slotOf(a.getType())).isEmpty()) {
				return false;
			}
		}
		for (ItemStack piece : contents) {
			pieces.set(slotOf(((IronManArmorItem) piece.getItem()).getType()), piece.copyWithCount(1));
		}
		if (owner == null) {
			owner = player.getUUID();
		}
		caseStack.shrink(1);
		afterContentsChanged();
		return true;
	}

	// ---------------- v0.14.21: animated deploy / retrieve ----------------`]]);
edit(P+'IronManSuitPlatformBlock.java',[
[`		if (stack.getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
			if (!level.isClientSide() && be.store(stack)) {`,
`		if (stack.is(IronManItems.MARK_V_SUITCASE)) { // v0.14.30: the case unfolds onto the rack
			if (!level.isClientSide()) {
				unfoldCase(level, pos, be, player, stack);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide());
		}
		if (stack.getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
			if (!level.isClientSide() && be.store(stack)) {`],
[`			if (be.retrieveFrom(serverPlayer)) {
				((ServerLevel) level).playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.9f, 1.1f);
			}
			return InteractionResult.SUCCESS;`,
`			if (be.retrieveFrom(serverPlayer)) {
				((ServerLevel) level).playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.9f, 1.1f);
			} else {
				// v0.14.30: nothing worn to retrieve -- a carried Mark 5 Suitcase unfolds onto the rack instead
				for (ItemStack carried : serverPlayer.getInventory().items) {
					if (carried.is(IronManItems.MARK_V_SUITCASE)) {
						unfoldCase(level, pos, be, serverPlayer, carried);
						break;
					}
				}
			}
			return InteractionResult.SUCCESS;`],
[`	@Override
	protected InteractionResult useWithoutItem(`,
`	/** v0.14.30: unfold a Mark 5 Suitcase into its armour on this platform, with feedback either way. */
	private static void unfoldCase(Level level, BlockPos pos, IronManSuitPlatformBlockEntity be, Player player, ItemStack caseStack) {
		if (be.storeSuitcase(player, caseStack)) {
			((ServerLevel) level).playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.9f, 0.8f);
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.case_to_platform").withStyle(ChatFormatting.AQUA), true);
		} else {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.case_platform_full").withStyle(ChatFormatting.RED), true);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(`]]);
edit(P+'IronManSuitPlatformMenu.java',[[
`		} else if (stack.getItem() instanceof IronManArmorItem) {
			if (!moveItemStackTo(stack, 0, platformSlots, false)) {`,
`		} else if (stack.is(com.projecthero.mod.ironman.item.IronManItems.MARK_V_SUITCASE)) {
			// v0.14.30: shift-clicking the Mark 5 Suitcase unfolds it onto the rack
			if (container instanceof IronManSuitPlatformBlockEntity be && be.storeSuitcase(player, stack)) {
				slot.setChanged();
			}
			return ItemStack.EMPTY;
		} else if (stack.getItem() instanceof IronManArmorItem) {
			if (!moveItemStackTo(stack, 0, platformSlots, false)) {`]]);
// lang
const L='src/main/resources/assets/projecthero/lang/en_us.json';
const add={"message.projecthero.ironman.case_to_platform":"The Mark 5 Suitcase unfolds onto the Suit Platform.",
"message.projecthero.ironman.case_platform_full":"The platform needs four free Mark 5 slots (empty it or clear another suit off it first)."};
let t=fs.readFileSync(L,'utf8');const end=t.lastIndexOf('}');let body=t.slice(0,end).replace(/\s+$/,'');
for(const [k,v] of Object.entries(add)) body+=',\r\n  '+JSON.stringify(k)+': '+JSON.stringify(v);
t=body+'\r\n}\r\n';JSON.parse(t);fs.writeFileSync(L,t);
console.log('ok');
