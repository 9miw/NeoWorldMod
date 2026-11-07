# Approved level-system review fixes

User approved the review recommendations and excluding admin EXP from cumulative totals.

- Separate administrative grants from gameplay grants; keep admin progression but exclude its total EXP.
- Display a distinct totalExp field in ranking snapshots/payload/UI; retain level/current-exp sorting. Increment the network protocol for the additional field.
- Run level-up effects once for the final level of a grant.
- Replace under-level armor inventory/drop fallback with a persistent copy-on-death Attachment return buffer, preserving partial inventory returns.
- Roll monster EXP before item creation; save a fixed value on the item. Roll legacy ranged stacks per item with safe integer sampling and saturation.
- Verify actual admin/progression rules, ranking ordering/network/offline values, saved armor returns and integer reward bounds; compileJava and relevant checks. No formula changes or full build.

Implemented and reviewed. Dedicated tests cover admin grants through cap, total-vs-progress ranking order and codec/offline round trips, full/partial ItemStack recovery with metadata, bounded EXP sampling and saturation. Review found Inventory.add discards leftovers in Creative; replaced it with direct safe insertion and tested a real full Inventory without consulting player abilities. Removed the duplicate client level-up sound; server audio and final-level celebration are emitted once per grant.

Full `compileJava`, playerExperienceRegressionTest, levelSafetyRegressionTest, rankingRegressionTest and `check` passed. After the final client audio cleanup, compileJava and all three focused regression tasks passed again. In-game and visual verification remain pending.
