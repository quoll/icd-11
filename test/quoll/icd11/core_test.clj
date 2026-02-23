(ns quoll.icd11.core-test
  (:require [clojure.test :refer [testing is deftest]]
            [quoll.rdf :as rdf]
            [quoll.icd11.core :as core :refer [uri linearization-id entity-id fix-browser-url get-linearization-object
                                               normalize-graph into-graph headers]]))

(deftest id-test
  (testing "If IDs are extracted from entity and linearization URIs correctly"
    (is (= "1435254666" (linearization-id (uri "http://id.who.int/icd/release/11/2025-01/mms/1435254666"))))
    (is (= "1296093776/other" (linearization-id (uri "http://id.who.int/icd/release/11/2025-01/mms/1296093776/other"))))
    (is (= "1296093776/unspecified" (linearization-id (uri "http://id.who.int/icd/release/11/2025-01/mms/1296093776/unspecified"))))
    (is (= "979408586" (entity-id (uri "http://id.who.int/icd/entity/979408586"))))))

(deftest fix-url-test
  (testing "If older browse URLs are fixed correctly"
    (is (= (uri "https://icd.who.int/browse11/foundation/en#448895267") (fix-browser-url "https://icd.who.int/browse11_2023-01/foundation/en#448895267")))))

(deftest linearization-object-test
  (testing "URI creation for linearization ID"
    (is (= (uri "http://id.who.int/icd/release/11/2025-01/mms") (get-linearization-object "2025-01" "mms")))))

(deftest normalize-graph-test
  (testing "Normalization of graphs"
    (let [g {:a {:b #{(rdf/blank-node "_:1") :x}}
             :c {:b #{:x (rdf/blank-node "_:2")}}
             :d {:b #{(rdf/blank-node "_:1") :y}}
             (rdf/blank-node "_:1") {:property #{:value}}
             (rdf/blank-node "_:2") {:property #{"data"}}}
          ng (normalize-graph g)]
      (is (= (-> ng :a :b) #{{:property #{:value}} :x}))
      (is (= (-> ng :d :b) #{{:property #{:value}} :y}))
      (is (= (-> ng :c :b) #{{:property #{"data"}} :x})))))

(deftest into-graph-test
  (testing "Converting triples into a simple graph structure"
    (let [t [[:a :b :c]
             [:d :e :f]
             [:g :h :i]
             [:a :b :d]
             [:a :x :y]
             [:d :x :z]
             [:g :x :z]
             [:z :p 0]]]
      (is (= {:a {:b #{:c :d}
                  :x #{:y}}
              :d {:e #{:f}
                  :x #{:z}}
              :g {:h #{:i}
                  :x #{:z}}
              :z {:p #{0}}}
             (into-graph t))))))

(deftest headers-test
  (testing "Basic header structure"
    (is (= {:accept "application/json"
            :API-Version "v2"
            :Accept-Language "en"}
           (headers)))
    (is (= {:accept "application/json"
            :API-Version "v2"
            :Accept-Language "en"
            :Authorization "aaaaabbbbbccccc"}
           (headers {:auth (volatile! "aaaaabbbbbccccc")})))))
