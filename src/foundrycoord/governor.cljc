(ns foundrycoord.governor
  "FoundryCoordGovernor — the independent safety/scope layer gating
  every foundry scheduling/logistics proposal an advisor may make for
  a metal-moulding and coremaking crew. The governor never dispatches
  hardware itself, never performs casting or pouring work itself, and
  never finalizes a casting/pouring-execution decision (e.g. deciding
  to proceed with a specific casting pour) or overrides a foundry
  safety officer's judgment — those are permanently out of this
  actor's scope and remain a foundry safety officer's exclusive
  judgment (README's 'Robotics premise': this actor coordinates
  FOUNDRY SCHEDULING/LOGISTICS ONLY — it never performs casting or
  moulding/coremaking work itself). Modeled on
  cloud-itonami-isco-7111's housebuilder.governor (closest domain
  shape) and closely on cloud-itonami-isco-9311's
  mininglabor.governor for the physical-safety-domain shape.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. moulder provenance    — the crew member must be independently
                                verified/registered before any action.
    2. foundry provenance    — the foundry site must be independently
                                verified/registered before any action.
    3. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never performs casting work itself; it
                                only gates what the advisor may
                                coordinate).
    4. closed op-allowlist    — only :log-work-record,
                                :schedule-crew-operation,
                                :flag-safety-concern and
                                :coordinate-supply-order may ever be
                                proposed; anything else is refused.
    5. scope-excluded action  — any proposal to directly finalize a
                                casting/pouring-execution decision
                                (e.g. deciding to proceed with a
                                specific casting pour), or to override
                                a foundry safety officer's judgment,
                                is a hard, permanent block (checked
                                both against the proposed :op and,
                                defense-in-depth, against the
                                proposal's :rationale text — matched
                                as full finalization/execution ACTION
                                phrases such as \"proceed with the
                                casting pour\" / \"authorize the
                                casting pour\" / \"override the
                                foundry safety officer's judgment\",
                                never as bare nouns like \"casting\",
                                \"pour\" or \"safety\", so the check
                                can never self-trip on the advisor's
                                own routine rationale text, e.g.
                                \"logged work record for moulder …\"
                                or \"scheduled crew operation for
                                coremaking task …\" or \"…routed for
                                foundry safety officer review\" — all
                                three legitimately contain those bare
                                nouns but none is a finalization
                                action, and all are exercised by
                                `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off
  regardless of confidence):
    6. :op :flag-safety-concern (a heat-exposure / fume-hazard /
                                equipment-condition concern always
                                escalates to a human, never
                                auto-commits).
    7. :op :coordinate-supply-order above `supply-cost-threshold`.
    8. low confidence (< `confidence-floor`)."
  (:require [clojure.string :as str]
            [foundrycoord.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 2000)

(def allowed-ops
  #{:log-work-record :schedule-crew-operation
    :flag-safety-concern :coordinate-supply-order})

;; Defense-in-depth: none of these ops are ever in `allowed-ops`
;; above, so they are already refused by the closed-allowlist check
;; below; they are named again here — as explicit finalization/
;; execution ACTIONS, never bare nouns — so a future allowlist edit
;; cannot silently re-open this specific out-of-scope path without
;; also touching this list.
(def ^:private scope-excluded-ops
  #{:finalize-pour-decision :authorize-casting-pour
    :proceed-with-casting-pour :finalize-casting-decision
    :override-foundry-safety-officer-judgment
    :override-safety-officer-judgment})

;; Full finalization/execution ACTION phrases only — never bare nouns
;; ("casting", "pour", "safety", "foundry", "officer") — so this can
;; never match inside the mock advisor's own default rationale text
;; (which legitimately contains those bare nouns, e.g. "coremaking
;; task" / "foundry safety officer review"). See
;; `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`.
(def ^:private scope-excluded-phrases
  ["proceed with the casting pour" "proceed with the pour"
   "authorize the casting pour" "authorize the pour"
   "finalize the pour decision" "finalize the casting decision"
   "override the foundry safety officer's judgment"
   "override the safety officer's judgment"
   "override foundry safety officer judgment"])

(defn- contains-excluded-phrase? [s]
  (let [s (str/lower-case (or s ""))]
    (boolean (some #(str/includes? s %) scope-excluded-phrases))))

(defn- hard-violations [proposal moulder-record foundry-record]
  (let [{:keys [op rationale]} proposal]
    (cond-> []
      (nil? moulder-record)
      (conj {:rule :no-moulder
             :detail "未登録 moulder への提案は不可（moulder record は独立して検証・登録済みでなければならない）"})

      (nil? foundry-record)
      (conj {:rule :no-foundry
             :detail "未登録 foundry への提案は不可（foundry record は独立して検証・登録済みでなければならない）"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は鋳造作業を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op
             :detail (str op " は closed op-allowlist に無い — 提案不可")})

      (or (contains? scope-excluded-ops op) (contains-excluded-phrase? rationale))
      (conj {:rule :scope-excluded-action
             :detail "鋳造・注湯（キャスティング/ポア）実行判断の確定・foundry safety officer の判断の上書きは、この actor の権限外 — 常に永続ブロック"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `foundrycoord.store/Store`. Pure — never
  mutates the store, never dispatches a foundry operation."
  [request _context proposal store]
  (let [moulder-record (store/moulder store (:moulder-id request))
        foundry-record (some->> (:foundry-id proposal) (store/foundry store))
        hard (hard-violations proposal moulder-record foundry-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        supply-order-over-threshold?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-threshold))
        always-risky? (or (= :flag-safety-concern (:op proposal))
                           supply-order-over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
