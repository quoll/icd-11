(ns quoll.icd11.json-ld-test
  (:require [clojure.test :refer [testing is deftest]]
            [quoll.icd11.json-ld :as json-ld :refer [uri]]))

(def person "{
  \"@context\": {
    \"name\": \"http://schema.org/name\",
    \"homepage\": {
      \"@id\": \"http://schema.org/url\",
      \"@type\": \"@id\"
    }
  },
  \"@id\": \"http://example.com/wilma\",
  \"name\": \"Wilma Flintstone\",
  \"homepage\": \"https://example.com/\"
}")

(deftest basic-doc-test
  (testing "Simple documents parsing through Titanium correctly"
    (let [triples (json-ld/string->triples person)
          wilma (uri "http://example.com/wilma")
          homepage (uri "http://schema.org/url")
          sname (uri "http://schema.org/name")]
      (is (= triples [[wilma sname "Wilma Flintstone"]
                      [wilma homepage (uri "https://example.com/")]])))))
