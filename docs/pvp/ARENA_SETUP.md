# PvP arena templates

PvP uses vanilla Structure Blocks, independently of dungeon code and saved data.
Arena templates and their DATA markers are read when validating and placing a match.

1. Build a complete arena with solid floors and enough space for both players.
2. Place two Structure Blocks in DATA mode. Set their metadata to:
   - `pvp_spawn_a`: player A's feet position.
   - `pvp_spawn_b`: player B's feet position.
3. The saved structure size automatically defines the arena bounds; no bounds
   markers are required. Put spawn markers inside the saved area, with two blocks of height available.
   Leave air above each spawn marker and a solid floor immediately below it.
   Each spawn marker must occur exactly once. Include the floor and enough vertical
   space for combat/jumping in the saved structure. Legacy bounds markers are ignored.
4. Save the whole arena, including both spawn markers, with a SAVE-mode Structure Block,
   for example `neoworld:pvp_arena_1`. Exclude the SAVE block itself from the template.
   Save blocks from the world, not entities.
5. As an operator (permission level 2), run:
   - `/neow pvp arena add arena_1 neoworld:pvp_arena_1`
   - `/neow pvp arena validate arena_1`
   - `/neow pvp arena list`

`/neoworld` is the full-name alias for `/neow` and supports the same commands.

Arena registrations persist in the world's `neoworld_pvp_arenas` SavedData. Structure
templates saved with Structure Blocks belong to that world. Keep them when moving it.
After changing a template, save it again and run validation again.

The placement helper excludes Structure Blocks and clears both spawn marker cells.
This stage provides template registration, marker validation and a placement helper.
Private room allocation, occupied slots, boundary enforcement and practice combat
are described in [ROOM_USAGE.md](ROOM_USAGE.md). Registering an arena does not
start a match or place it automatically.
