(ns foundrycoord.store
  "SSoT for the ISCO-08 7211 metal moulding and coremaking foundry
  scheduling/logistics coordination actor (itonami actor pattern,
  ADR-2607121000 / CLAUDE.md Actors section; README's 'Robotics
  premise' — a foundry scheduling/logistics coordination robot
  performs crew scheduling, casting-batch/materials-usage/progress
  record logging and casting-materials supply-order coordination for
  a metal-moulding and coremaking crew under this advisor/governor
  pair, which never dispatches hardware itself, never performs
  casting or pouring work itself, and never finalizes a
  casting/pouring-execution decision or overrides a foundry safety
  officer's judgment — those remain the foundry safety officer's
  exclusive judgment). Modeled on cloud-itonami-isco-7111's
  housebuilder.store (closest domain shape) and closely on
  cloud-itonami-isco-9311's mininglabor.store for the
  physical-safety-domain shape.

  Domain:

    moulder — a registered metal-moulding/coremaking crew member
              (:moulder-id, :name)
    foundry — a registered foundry site {:foundry-id :name
              :max-supply-cost number}. `:max-supply-cost` is an
              informational registered ceiling used only to decide
              whether a `:coordinate-supply-order` proposal escalates
              to human sign-off (the governor never blocks a
              within-threshold order outright; it only decides
              commit vs. escalate).
    record  — a committed operating record (a logged casting-batch/
              materials-usage/progress entry, a scheduled crew/
              furnace operation, a flagged safety concern, or a
              coordinated casting-materials supply order) — written
              ONLY via commit-record!.
    ledger  — append-only audit trail, commit or hold.")

(defprotocol Store
  (moulder [s moulder-id])
  (foundry [s foundry-id])
  (records-of [s moulder-id])
  (ledger [s])
  (register-moulder! [s moulder])
  (register-foundry! [s foundry])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (moulder [_ moulder-id] (get-in @a [:moulders moulder-id]))
  (foundry [_ foundry-id] (get-in @a [:foundries foundry-id]))
  (records-of [_ moulder-id] (filter #(= moulder-id (:moulder-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-moulder! [s m]
    (swap! a assoc-in [:moulders (:moulder-id m)] m) s)
  (register-foundry! [s f]
    (swap! a assoc-in [:foundries (:foundry-id f)] f) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:moulders {} :foundries {} :records [] :ledger []}
                                    seed)))))
