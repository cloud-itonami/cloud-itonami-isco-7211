(ns foundrycoord.advisor
  "Foundry Coordination Advisor — proposing a foundry
  scheduling/logistics coordination operation (log a work record,
  schedule a crew operation, flag a safety concern, coordinate a
  casting-materials supply order) from a crew roster, foundry
  registration and safety-reporting policy. Swappable mock/llm; the
  advisor ONLY proposes — `foundrycoord.governor` independently gates
  every proposal and always escalates safety concerns and
  above-threshold supply orders. The advisor never proposes to
  directly finalize a casting/pouring-execution decision (e.g.
  deciding to proceed with a specific casting pour) or to override a
  foundry safety officer's judgment — those stay permanently out of
  this actor's scope. Modeled on cloud-itonami-isco-7111's advisor
  (closest domain shape) and closely on cloud-itonami-isco-9311's
  mininglabor.advisor for the physical-safety-domain shape.

  A proposal: {:op :log-work-record|:schedule-crew-operation|
               :flag-safety-concern|:coordinate-supply-order
               :effect :propose :moulder-id str :foundry-id str
               :cost number :hazard-type kw :task str :stake kw
               :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- rationale-for [op moulder-id foundry-id hazard-type]
  (case op
    :log-work-record
    (str "logged work record for moulder " moulder-id " at foundry " foundry-id)

    :schedule-crew-operation
    (str "scheduled crew operation for coremaking task at foundry " foundry-id)

    :flag-safety-concern
    (str "flagged " (name (or hazard-type :hazard)) " concern for moulder "
         moulder-id " at foundry " foundry-id " — routed for foundry safety officer review")

    :coordinate-supply-order
    (str "coordinated supply order for moulder " moulder-id " at foundry " foundry-id)

    (str "proposed " (name op) " for moulder " moulder-id " at foundry " foundry-id)))

(defn- infer [_store {:keys [op stake moulder-id foundry-id cost hazard-type task]
                       :as request}]
  {:op op
   :effect :propose
   :moulder-id moulder-id
   :foundry-id foundry-id
   :cost cost
   :hazard-type hazard-type
   :task task
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (rationale-for op moulder-id foundry-id hazard-type)})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a metal-moulding and coremaking foundry
   scheduling/logistics coordination advisor. Given a request,
   propose an :op (one of :log-work-record, :schedule-crew-operation,
   :flag-safety-concern, :coordinate-supply-order), the :moulder-id,
   :foundry-id, and any :cost/:hazard-type/:task fields, an honest
   :confidence and a :stake. Never propose an op outside this closed
   list, and never propose to directly finalize a
   casting/pouring-execution decision (e.g. deciding to proceed with
   a specific casting pour), or to override a foundry safety
   officer's judgment — those are always out of this actor's scope;
   it coordinates foundry scheduling/logistics only and never
   performs casting or pouring work or authorizes casting operations
   itself. Safety concerns always require human sign-off regardless
   of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
