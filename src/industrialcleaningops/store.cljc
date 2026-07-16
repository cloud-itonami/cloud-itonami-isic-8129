(ns industrialcleaningops.store
  "SSoT for the ISIC-8129 'Other building and industrial cleaning
  activities' operations-COORDINATION actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every
  `cloud-itonami-isic-*` actor in this fleet uses.

  This actor coordinates the back-office operations of an industrial/
  building-cleaning services contractor: job/site cleaning-completion
  data logging, cleaning-crew/equipment dispatch scheduling, cleaning-
  chemical/equipment supply-order coordination with registered vendors,
  and hazmat/confined-space/chemical-exposure safety-concern flagging. It
  never directly finalizes a hazmat-handling-safety clearance or a
  confined-space-entry authorization -- see
  `industrialcleaningops.governor`'s `scope-exclusion-violations`, a
  HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `sites` directory keyed by `:site-id` STRING and a
  `vendors` directory keyed by `:vendor-id` STRING (never keywords --
  consistent keying from the start, avoiding the silent-miss bug that has
  plagued earlier sibling actors).

  A registered/verified client-site-access record (the contractor's own
  site-access authorization for the client facility) must exist before
  ANY proposal targeting that site may ever commit or escalate --
  `industrialcleaningops.governor`'s `site-unverified-violations`
  re-derives this from the site's own `:registered?`/`:verified?` fields,
  never from proposal self-report. A `:coordinate-supply-order` proposal
  additionally names a registered cleaning-chemical/equipment vendor via
  its own `:vendor-id`; the SAME 'ground truth, not self-report'
  discipline applies via `vendor-unverified-violations`.

  The ledger stays append-only: which site a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by whom
  is always a query over an immutable log.")

(defprotocol Store
  (site-record [s site-id] "Registered contractor/site-access record, or
    nil. Site map: {:site-id .. :name .. :registered? bool :verified?
    bool}.")
  (all-site-records [s])
  (vendor-record [s vendor-id] "Registered vendor record, or nil.
    Vendor map: {:vendor-id .. :name .. :registered? bool :verified? bool}.")
  (all-vendor-records [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-site-records [s sites] "replace/seed the site directory (map site-id->site)")
  (with-vendor-records [s vendors] "replace/seed the vendor directory (map vendor-id->vendor)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained site/vendor directory covering both the happy
  path and the governor's own hard checks, so the actor + tests run
  offline."
  []
  {:sites
   {"site-1" {:site-id "site-1" :name "Riverside Distribution Center Warehouse Floors"
              :registered? true :verified? true}
    "site-2" {:site-id "site-2" :name "Sunset Industrial Park Loading Docks"
              :registered? true :verified? true}
    "site-3" {:site-id "site-3" :name "Downtown Municipal Facility (access pending)"
              :registered? true :verified? false}}
   :vendors
   {"vendor-1" {:vendor-id "vendor-1" :name "Northgate Industrial Chemicals & Equipment Supply"
                :registered? true :verified? true}
    "vendor-2" {:vendor-id "vendor-2" :name "Unverified Bulk Degreaser Import Broker Co."
                :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (site-record [_ site-id] (get-in @a [:sites site-id]))
  (all-site-records [_] (sort-by :site-id (vals (:sites @a))))
  (vendor-record [_ vendor-id] (get-in @a [:vendors vendor-id]))
  (all-vendor-records [_] (sort-by :vendor-id (vals (:vendors @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-site-records [s sites] (when (seq sites) (swap! a assoc :sites sites)) s)
  (with-vendor-records [s vendors] (when (seq vendors) (swap! a assoc :vendors vendors)) s))

(defn seed-db
  "A MemStore seeded with the demo site/vendor directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with explicit `sites`/`vendors` maps (site-id/
  vendor-id string -> record map) -- the primary test/dev entry point.
  Either may be empty (an unregistered-everywhere site)."
  ([sites] (mem-store sites {}))
  ([sites vendors]
   (->MemStore (atom {:sites (or sites {}) :vendors (or vendors {})
                       :ledger [] :coordination-log []}))))
