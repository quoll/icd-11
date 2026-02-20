(ns quoll.icd11.init
  "Parses the command line and environment to create a config"
  {:author "Paula Gearon"}
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.tools.cli :as cli])
  (:import [java.util Properties]))

(def CENTRAL-HOST "id.who.int")
(def CENTRAL-PORT 443)

(def ICD-PROPERTIES-FILE ".icd")
(def DEFAULT-OUTPUT "icd11.ttl")
(def LINEAR-TRAILER "-linear.ttl")
(def DEFAULT-PORT 6382)

(def ICD-ENV-ID "ICD_ID")
(def ICD-ENV-SECRET "ICD_SECRET")

(def DEFAULT-RELEASE "2025-01")
(def MMS "Mortality and Morbidity Statistics" "mms")
(def DEFAULT-LINEARIZATION "Mortality and Morbidity Statistics" MMS)

(def local-server "localhost")  ;; my personal server

(defn get-credentials
  "Reads credentials file, when available. Overwrites with environment variables, if present."
  []
  (let [home-dir (System/getProperty "user.home")
        icd-config (io/file home-dir ICD-PROPERTIES-FILE)
        p (and (.exists icd-config)
               (with-open [r (io/reader icd-config)]
                 (into {}
                       (doto (Properties.)
                         (.load r)))))
        creds (reduce (fn [creds [k v]]
                        (case (s/lower-case k)
                          ("id" "client-id") (assoc creds :client v)
                          ("secret" "client-secret") (assoc creds :secret v)
                          creds))
                      {} p)
        id (System/getenv "ICD_ID")
        sec (System/getenv "ICD_SECRET")]
    (cond-> creds
      id (assoc :client id)
      sec (assoc :secret sec))))

(def cli-options
  [["-h" "--help" "Print usage"]
   ["-p" "--port PORT" "Port number"
    :parse-fn parse-long
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-o" "--outfile FILE" "Output filename"
    :default DEFAULT-OUTPUT]
   ["-H" "--host HOST" "Host name"
    :default local-server
    :validate [#(nil? (s/index-of % \space)) "Must not contain a space"]]
   ["-a" "--auth AUTH" "Authorization string"
    :parse-fn #(if (s/starts-with? % "Bearer ") % (str "Bearer " %))
    :validate [#(re-find #"^Bearer [a-zA-Z0-9_\-.]+$" subs) "Illegal characters in auth token"]]
   ["-r" "--release RELEASE" "Release version"
    :default DEFAULT-RELEASE
    :validate [#(re-find #"\d\d\d\d-\d\d") "Follows format: yyyy-mm"]]
   ["-l" "--linearization LINEARIZATION" "Short name for the linearization"
    :default MMS]
   ["-L" "--linear LINEARIZATIONFILE" "Linearization output filename"]
   ["-c" "--client ID" "Client ID for authentication"]
   ["-s" "--secret SEC" "Client secret for authentication"]])

(defn print-usage
  []
  (println "clj -X:main [-H host] [-p port] [-o out_file] [-L linearization_file]
            [-r release] [-l linearization] [-a auth_string]
            [-c client-id] [-s client-secret] [-h]\n")
  (println (format "  -H <host>, --host <host>
      The server to access. If this is id.who.int then credentials must be available. Defaults to '%s'.

  -h, --help
      Prints this text and exits.

  -p <port>, --port <port>
      The port to use. This defaults to 6382 for all hosts except icd.who.int, in which case it will be 443.

  -o <out_file>, --outfile <out_file>
      The file to write the foundation entity graph to. Defaults to '%s'.

  -L <linearization_file>, --linear <linearization_file>
      The file to write the linearization graph to. Defaults to <unsuffixed out_file>'%s'

  -r <release>
      Release version. Defaults to '%s'.

  -l <linearization>, --linearization <linearization>
      The linearization name. Defaults to '%s' (%s).

  -a <auth>, -auth <auth>
      An authorization token. This is for id.who.int and can be obtained from the OAUTH2 service:
        https://icdaccessmanagement.who.int/connect/token
      It can also be accesed after logging into the Swagger page at:
        https://id.who.int/swagger/index.html
      Not required when a client-id and client-secret are provided.

  -c <client_id>, --client <client_id>
      A client-id value to use for authentication to id.who.int. Overrides the config file.

  -s <client_secret>, --secret <client_secret>
      A client-secret value to use to authentication to id.who.int. Overrides the confile file.


  Client credentials for id.who.int are provided after creating an account at:
    https://icd.who.int/icdapi
  Once an account has been created, a client-id/client-secret pair can be created at:
    https://icd.who.int/icdapi/Account/AccessKey

  These values can be provided on the command line, or in a file named '%s'
  in the user's home directory. The format of this file is:

client-id=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
client-secret=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx=

  The credentials may be overriden using the environment vars %s and %s.\n"
                   local-server DEFAULT-OUTPUT LINEAR-TRAILER DEFAULT-RELEASE DEFAULT-LINEARIZATION
                   (:doc (meta #'DEFAULT-LINEARIZATION)) ICD-PROPERTIES-FILE ICD-ENV-ID ICD-ENV-SECRET)))

(defn get-setup
  "Converts command line arguments into an options map"
  [args]
  (let [opts (:options (cli/parse-opts args cli-options))]
    (cond-> opts
      (nil? (:linear opts)) (assoc :linear (s/replace (:outfile opts) #".ttl" LINEAR-TRAILER))
      (nil? (:port opts)) (assoc :port (if (= CENTRAL-HOST (:host opts)) CENTRAL-PORT DEFAULT-PORT))
      (and (= CENTRAL-HOST (:host opts))
           (nil? (:auth opts))
           (or (nil? (:client opts))
               (nil? (:secret opts)))) (merge (get-credentials)))))
