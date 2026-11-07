# Admin EXP adjustment

User approved: administrative EXP additions/removals update cumulative EXP, and removals decrease levels.

- Restore total counting for addAdminExp, keeping normal progression and one celebration per grant.
- Add applyExpReduction: subtract current progress across level boundaries and separately decrement total_exp, saturating both at their floors. Never infer progression from total_exp after set-level.
- Add `/neow level admin removeexp <amount> [player]`, dedicated OP2 permission, command help and server-to-client sync.
- Preserve existing set-level semantics and ranking order. Cap-level reduction uses current progression, not excess lifetime total.
- Dedicated regression first failed for missing reduction; cover exact boundaries, multi-level loss, level-one floor, cap, maximum amounts, invalid amounts and administrator-set levels. Run compileJava and relevant checks. In-game verification remains pending.

Verification: `compileJava playerExperienceRegressionTest check` passed on the completed implementation. Existing set-level behavior and the ranking order are unchanged. No full build/package or in-game verification was performed.
