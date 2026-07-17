# cloud-itonami-isco-7211

Open Occupation Blueprint for **ISCO-08 7211**: Metal Moulders and Coremakers.

This repository designs a forkable OSS business for a foundry scheduling and logistics coordination practice: a foundry scheduling and supply-coordination robot manages crew/task records under a governor-gated actor, so a metal-moulding and coremaking crew keeps its own operating records instead of renting a closed workforce-management SaaS.

**Maturity: `:implemented`.** `src/foundrycoord/` implements the
`FoundryCoordActor` as a `langgraph.graph/state-graph`
(`foundrycoord.actor`) wired to a `Foundry Coordination Advisor`
(`foundrycoord.advisor`) and an independent `FoundryCoordGovernor`
(`foundrycoord.governor`), following the itonami actor pattern
(ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 21 tests / 45 assertions green (`clojure -M:test`).
HARD invariants (always hold, never overridable): moulder provenance,
foundry provenance, no-actuation (`:effect` must be `:propose`), a closed
op-allowlist (`:log-work-record`, `:schedule-crew-operation`,
`:flag-safety-concern`, `:coordinate-supply-order` — nothing else may
ever be proposed), and a permanent, unconditional block on any
proposal that would directly finalize a casting/pouring-execution
decision (e.g. deciding to proceed with a specific casting pour) or
override a foundry safety officer's judgment. Always-escalate paths
(human sign-off regardless of confidence, mapping this repo's Trust
Controls in [`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (always) and `:coordinate-supply-order` above
the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a foundry scheduling/logistics coordination robot performs crew scheduling, casting-batch/materials-usage/progress-record logging and casting-materials supply-order coordination for a metal-moulding and coremaking crew, under an actor that proposes actions and an independent **Foundry Coordination Governor** that gates them. The governor never
dispatches hardware itself, never performs casting or pouring work on the foundry floor, and never finalizes a casting/pouring-execution decision or overrides a foundry safety officer's judgment; `:high`/`:safety-critical` actions (such as a flagged heat-exposure/fume-hazard/equipment-condition concern, or an above-threshold supply order) require human sign-off. **This actor coordinates foundry scheduling/logistics only — it never performs casting or moulding/coremaking work itself.**

## Core Contract

```text
crew roster + foundry registration + safety-reporting policy
        |
        v
Foundry Coordination Advisor -> Foundry Coordination Governor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, finalize
a casting/pouring-execution decision, override a foundry safety officer's
judgment, suppress an operating record, or disclose sensitive data
without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `7211`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
