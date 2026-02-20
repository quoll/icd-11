(ns ^{:author "Paula Gearon"}
    quoll.icd11.access
  (:require [clojure.string :as s]
            [babashka.http-client :as http])
  (:import [java.io StringReader]
           [java.util Optional]
           [com.apicatalog.jsonld.document JsonDocument]
           [jakarta.json JsonObject JsonValue JsonValue$ValueType]))

(def AUTH-URI "https://icdaccessmanagement.who.int/connect/token")

(defn json-value
  [^JsonValue jv]
  (case (.name (.getValueType jv))
    "ARRAY" (mapv json-value jv)
    "OBJECT" (->> jv
                  (map (fn [[k v]] [(keyword k) (json-value v)]))
                  (into {}))
    "STRING" (let [s (str jv)]
               (subs s 1 (dec (count s))))
    "NUMBER" (let [s (str jv)]
               (if (s/index-of s \.)
                 (parse-double s)
                 (parse-long s)))
    "TRUE" true
    "FALSE" false
    "NULL" nil))

(defn parse-json
  "This uses the existing apicatalog objects to do the parsing, since they're already in the project."
  [s]
  (with-open [r (StringReader. s)]
    (let [^Optional ocontent (.getJsonContent ^JsonDocument (JsonDocument/of r))]
      (if (.isPresent ocontent)
        (json-value ^JsonObject (.get ocontent))
        (throw (ex-info "Unable to parse JSON string" {:data s}))))))

(defn get-access
  "Retrieves an access token from an ICD-11 service, using OAuth2"
  [client-id client-secret]
  (-> (http/request {:method :post
                     :uri AUTH-URI
                     :form-params {"client_id" client-id
                                   "client_secret" client-secret
                                   "grant_type" "client_credentials"
                                   "scope" "icdapi_access"}})
      :body
      parse-json
      :access_token))
