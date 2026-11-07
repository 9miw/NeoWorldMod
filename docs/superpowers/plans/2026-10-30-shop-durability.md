# Shared shop/market durability implementation plan

**Approved goal:** Make shop buy/sell durable using the existing market journal/checkpoint and one recovery order across both systems.
**Architecture:** Retain the schema-15 journal, world identity and checkpoint keys for compatibility. Shop intent/audit and after-image join the same transaction scope. Snapshot version 2 includes all currencies; version 1 remains readable without resetting absent currencies. Commit and verified player save precede success. A server-owned UUID follows a shop confirmation so retries cannot repeat an operation.

- [x] Add fault tests for alternating market/shop operations and all currencies; observe missing behavior before implementation (wallet assertion failed before adding currencies).
- [x] Extend shared player snapshot/adapter for shops; preserve market replay compatibility.
- [x] Move shop audit and wallet/inventory mutation into the shared journal transaction; lock and validate the full offer.
- [x] Remove open/logout reconciliation that reads live sequence as persisted evidence. Old pending rows without a journal become NEEDS_REVIEW on login, with no automatic grant/refund.
- [x] Verify compileJava, regressionTest and shopRegressionTest; request focused review.

Constraints: No live DB changes, build/package, deployment, or schema bump. Historical Market* class/table names remain stable compatibility names for the shared boundary. Do not replace earlier market checkpoints or world identities. Add shop UI confirmation UUID only; do not redesign the GUI.

Validation scope: H2 file SQL plus independent NBT player adapter, currency-codec compatibility, repeated requests, failure/ambiguous commit, mixed-system replay ordering, historical pending review. Real Minecraft and MariaDB runtime remain separate verification limits.

Review outcome: Check inventoryMenu cursor/crafting slots even when a different menu is active; both adapters now reject such hidden temporary items. No other concrete important issue was found in the focused review. Server-owned confirmation UUID reaches both shop audit and shared journal. Full offer item/components, currency and prices are compared under the SQL row lock. Legacy pending rows retain item/money evidence and are never auto-replayed from the old RAM sequence.

Verification: `compileJava regressionTest shopRegressionTest` succeeds. 191 production-coordinator fault/restart checks include real ShopRepository audit insertion and rollback, buy/sell in Silver/Diamond, mixed-system recovery and retries; player adapter is simulated. 119 shop checks plus 3 shared-wallet compatibility checks pass, alongside 43 economy, 205 database and 35 legacy market-security checks. No production database initialization, Minecraft process-kill test or real MariaDB verification was performed.
