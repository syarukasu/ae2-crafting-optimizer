# ISSUE-218: Funded producers during missing simulation

## Scope and observation

The second user screenshot is a later standalone confirmation order. It does
not establish that its gold was reserved when the first order was calculated.
The user reports insufficient printed circuits but sufficient gold and a
registered manufacturing pattern. Installed refs.6 logs show completed missing
simulations, not a timeout or cancellation. They do not contain the complete
item-level inventory and producer graph needed to replay the exact 480 shortage.

## Reproduction and correction

The initial feasibility attempt stops on a missing input, then missing simulation
builds the shortage plan. Previously its first alternate producer could commit
hypothetical missing inputs before a later funded route was tried. An unrelated
true shortage could therefore hide a feasible route for another ingredient.

VmInstructionExecution.request/craft now tries funded routes first during that
pass, preserving rollback of failed trials and existing producer accounting.
Only the remaining demand is simulated with the preferred producer. No extra
world query, planner, quantity cap or execution throttle was added.

Independent fixtures verify:

- 12480 requested circuits, 12000 stocked and 480 gold: the funded route supplies
  the remaining 480; only an unrelated genuinely missing input remains missing.
- 10 requested, 2 stocked, gold for 3 and iron for 4: only one further unit is
  unfunded, not the previous five.
- Failed routes do not spend stock; successful routes credit all byproducts.
- Empty, partial and sufficient supply remains exact for orders 1, 2, 480,
  Long.MAX_VALUE, 2^63 and 10^1024-1, in ordered and aggregated execution.
- A real AEProcessingPattern fixture and captured NativeVmPlanningContext run
  without further world access. Paper stands in for the circuit key in this
  fixture; this is not a captured production recipe graph.

Fail-before tests and logs are retained in
`build/issue218-missing-routes/before/`.

## Verification and oracle boundary

Both upstream and UELM test runs completed with 929 tests, zero failures/errors
and two skips for unavailable optional Neo ECO jar contracts. Installed EAEP,
MEGACells and AQE jar contracts passed, as did verifyIssueRegressionManifest
and verifyExactVmBundle. Reports and built artifacts are retained under
`build/issue218-missing-routes/upstream/` and `uelm/`.

Two historical native AE2 comparisons retain its first-route missing behavior.
Those explicitly selected fixed-input randomized tests now check missing plans
against independent BigInteger material balance and actual stock bounds. Their
successful-plan comparisons and the historical planner comparisons remain
exact. Other missing-plan comparisons are unchanged. This balance check is not
a scheduling proof or an exact missing-plan capacity proof; focused regressions
assert independently derived quantities and producer counts. The checker itself
rejects overspending and unfunded crafts. Original oracle failures are retained
in `build/issue218-missing-routes/oracle-before/`.

Build command: `gradlew.bat test verifyIssueRegressionManifest
verifyExactVmBundle --no-daemon -Pae2Variant=<upstream|uelm>`, with the installed
EAEP 1.5.5, MEGACells 2.4.6 and AQE 2.2.7 jar properties. Java was Temurin
17.0.17.10. No Minecraft server, client or GameTest was launched.

## Candidate artifact

- Version: `2.0.0-rc.7-vm-refs.7-local`.
- JAR: `build/distributions/2.0.0-rc.7-vm-refs.7-local/aco2.0.0-rc.7-vm-refs.7-local_1.20.1.jar`.
- SHA-256: `35AB053B5E0255007D23E735896437E432D9D73F00B03BAAA475E9F499B97326`.
- Size: 1368387 bytes, 207 bytes above installed refs.6.
- Matching bundled sources: the distribution's `sources/` directory.
- Nested exact VM: 0.1.6; nested native VM: 0.3.12-vm-refs.7.
- Artifact audit: `build/issue218-missing-routes/artifact-audit.json`.

All 531 outer ACO classes are byte-identical to installed refs.6. Changed native
classes are limited to VmInstructionExecution and its associated inner classes.
No classes were removed. BigInteger API and physical delivery code are unchanged.

No installation, restart, publication or live performance claim is made. The
original reported 480 shortage still needs a repeat order with comparable
available inventory. Wide execution, cancellation/refund, restart recovery,
concurrent-order acceptance and the live sub-10-second target remain pending.
