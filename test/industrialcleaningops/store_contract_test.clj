(ns industrialcleaningops.store-contract-test
  "Contract tests for `industrialcleaningops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [industrialcleaningops.store :as store]))

(deftest mem-store-site-lookup
  (testing "MemStore can store and retrieve sites by ID (string keys)"
    (let [sites {"s1" {:site-id "s1" :name "Alice's Industrial Cleaning Site" :registered? true :verified? true}}
          s (store/mem-store sites)]
      (is (some? (store/site-record s "s1")))
      (is (nil? (store/site-record s "s99"))))))

(deftest mem-store-all-site-records
  (testing "MemStore returns all sites in sorted order"
    (let [sites {"s2" {:site-id "s2" :name "Bob's Warehouse Floors"}
                 "s1" {:site-id "s1" :name "Alice's Industrial Cleaning Site"}
                 "s3" {:site-id "s3" :name "Carol's Loading Docks"}}
          s (store/mem-store sites)
          all-s (store/all-site-records s)]
      (is (= 3 (count all-s)))
      (is (= "s1" (:site-id (first all-s))))
      (is (= "s3" (:site-id (last all-s)))))))

(deftest mem-store-vendor-lookup
  (testing "MemStore can store and retrieve vendors by ID (string keys)"
    (let [vendors {"v1" {:vendor-id "v1" :name "Acme Chemicals & Equipment Supply" :registered? true :verified? true}}
          s (store/mem-store {} vendors)]
      (is (some? (store/vendor-record s "v1")))
      (is (nil? (store/vendor-record s "v99"))))))

(deftest mem-store-all-vendor-records
  (testing "MemStore returns all vendors in sorted order"
    (let [vendors {"v2" {:vendor-id "v2" :name "Beta Degreaser Supply"}
                   "v1" {:vendor-id "v1" :name "Acme Chemicals & Equipment Supply"}}
          s (store/mem-store {} vendors)
          all-v (store/all-vendor-records s)]
      (is (= 2 (count all-v)))
      (is (= "v1" (:vendor-id (first all-v)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-coordination-log
  (testing "MemStore commit-record! appends to coordination-log"
    (let [s (store/mem-store {})
          record {:op :log-service-record :site-id "s1" :value {:areas-cleaned 12}}]
      (is (= 0 (count (store/coordination-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/coordination-log s))))
      (is (= record (first (store/coordination-log s)))))))

(deftest mem-store-with-site-records
  (testing "MemStore with-site-records replaces the site directory"
    (let [s (store/mem-store {})
          new-sites {"s1" {:site-id "s1" :name "Alice's Industrial Cleaning Site"}}]
      (is (= 0 (count (store/all-site-records s))))
      (store/with-site-records s new-sites)
      (is (= 1 (count (store/all-site-records s)))))))

(deftest mem-store-with-vendor-records
  (testing "MemStore with-vendor-records replaces the vendor directory"
    (let [s (store/mem-store {})
          new-vendors {"v1" {:vendor-id "v1" :name "Acme Chemicals & Equipment Supply"}}]
      (is (= 0 (count (store/all-vendor-records s))))
      (store/with-vendor-records s new-vendors)
      (is (= 1 (count (store/all-vendor-records s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo sites and vendors"
    (let [s (store/seed-db)]
      (is (> (count (store/all-site-records s)) 0))
      (is (some? (store/site-record s "site-1")))
      (is (some? (store/site-record s "site-2")))
      (is (some? (store/site-record s "site-3")))
      (is (> (count (store/all-vendor-records s)) 0))
      (is (some? (store/vendor-record s "vendor-1")))
      (is (some? (store/vendor-record s "vendor-2"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for site-id/vendor-id"
    (let [demo (store/demo-data)
          sites (:sites demo)
          vendors (:vendors demo)]
      (doseq [[k v] sites]
        (is (string? k) "site keys must be strings")
        (is (string? (:site-id v)) "site-id must be string")
        (is (= k (:site-id v)) "key must match site-id"))
      (doseq [[k v] vendors]
        (is (string? k) "vendor keys must be strings")
        (is (string? (:vendor-id v)) "vendor-id must be string")
        (is (= k (:vendor-id v)) "key must match vendor-id")))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
