// v0.14.20: Stark Sorting Station "Tidy" button -- button, tooltip, status lines, chat messages, item tooltip line,
// Guidebook section. Re-runnable: only ever sets keys. Run from the repo root: node scratchpad/lang_v01420_sorter_tidy.js
require('./langset.js')([
	{ anchor: 'item.projecthero.stark_sorting_station.desc2', entries: {
		'item.projecthero.stark_sorting_station.desc3': 'Chests jumbled? Press Tidy to re-sort them.',
	} },
	{ anchor: 'screen.projecthero.stark_sorting_station.finished', entries: {
		'screen.projecthero.stark_sorting_station.tidy': 'Tidy',
		'screen.projecthero.stark_sorting_station.tidy_hint': 'Tidy the chests and barrels within 10 blocks. The Sorter Bot carries every stack sitting in the wrong category\'s chest over to the right one and merges split stacks. The station\'s own items are left alone.',
		'screen.projecthero.stark_sorting_station.tidying': 'Tidying... %s/%s stacks',
		'screen.projecthero.stark_sorting_station.tidied': 'Last tidy: %s/%s stacks moved',
	} },
	{ anchor: 'message.projecthero.stark_sorting_station.lost', entries: {
		'message.projecthero.stark_sorting_station.tidy_plan': 'Sorter Bot tidying %s containers: %s stacks to move.',
		'message.projecthero.stark_sorting_station.already_tidy': 'The chests are already tidy.',
		'message.projecthero.stark_sorting_station.tidy_no_room': '%s stacks are in the wrong chest, but their category\'s chests are full. Make room or add a chest.',
		'message.projecthero.stark_sorting_station.tidy_done': 'Tidying complete: %s stacks moved to the right chests.',
		'message.projecthero.stark_sorting_station.tidy_stuck': '%s stacks stayed put: their category\'s chests are full.',
	} },
	{ anchor: 'projecthero.guide.stark_sorter.safety.body', entries: {
		'projecthero.guide.stark_sorter.tidy': 'Tidy: re-sorting jumbled chests',
		'projecthero.guide.stark_sorter.tidy.body': 'Chests got jumbled? Press Tidy (under Sort). The bot works out what each chest is for from what it mostly holds -- the same rules as Sort -- then flies chest to chest, picking up anything in the wrong chest (up to 4 stacks a trip) and carrying it to a chest of its own category. It also merges split stacks in every chest it opens. Items stay where they are if their category\'s chests are full or no chest is meant for them. The station\'s own store is not touched, Tidy cannot start while a sort is running (or the other way round), and if the chests are already tidy it just tells you so. If a chest is broken mid-trip, the load goes back into the station.',
	} },
]);
