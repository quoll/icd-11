(ns ^{:author "Paula Gearon"}
    quoll.icd11.access
  (:require [clojure.string :as s]
            [clojure.data.json :as json]
            [babashka.http-client :as http])
  (:import [java.io StringReader]))

(def AUTH-URI "https://icdaccessmanagement.who.int/connect/token")

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
      json/read-str
      :access_token))
