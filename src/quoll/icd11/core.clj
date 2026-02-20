(ns ^{:author "Paula Gearon"}
    quoll.icd11.core
  (:require [quoll.icd11.json-ld :as jsld]
            [quoll.icd11.init :as init]
            [quoll.icd11.access :as access]
            [clojure.string :as s]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [donatello.ttl :as ttl]
            [babashka.http-client :as http]
            [clojure.tools.cli :refer [parse-opts]])
  (:import [java.net URI]
           [donatello.ttl BlankNode]))

(def limit nil) ;; set to the number of entities to traverse

(def RETRIES 1)

(def LINEAR-DOMAIN "http://id.who.int/icd/release/11/")

(def EMPTY-QUEUE clojure.lang.PersistentQueue/EMPTY)

(def entity-context (slurp "docs/foundationContext.json"))
(def top-context (slurp "docs/topLevelContext.json"))
(def linearization-context (slurp "docs/linearizationContext.json"))

(defn uri [s] (URI. s))

(def entity-object (uri "http://id.who.int/icd/entity"))
(def top-concept (uri "http://www.w3.org/2004/02/skos/core#hasTopConcept"))
(def child-rel (uri "http://www.w3.org/2004/02/skos/core#narrower"))
(def browser-url (uri "http://id.who.int/icd/schema/browserUrl"))

(def PREFIXES {:skos "http://www.w3.org/2004/02/skos/core#"
               :skos-xl "http://www.w3.org/2008/05/skos-xl#"
               :icds "http://id.who.int/icd/schema/"
               :icd "http://id.who.int/icd/entity/"})

(def LINEAR-PREFIXES (assoc PREFIXES
                            :icdlin "http://id.who.int/icd/release/11/2023-01/mms/"))


(defn get-linearization-object
  [release linearization]
  (uri (str LINEAR-DOMAIN release "/" linearization)))

(defn into-graph
  ([triples] (into-graph {} triples))
  ([graph triples]
   (reduce (fn [g [s p o]]
             (if-let [po (get g s)]
               (assoc g s (if-let [os (get po p)]
                            (assoc po p (conj os o))
                            (assoc po p #{o})))
               (assoc g s {p #{o}})))
           graph
           triples)))

(defprotocol Blank? (blank? [n]))

(extend-protocol Blank?
  BlankNode
  (blank? [n] true)
  Object
  (blank? [n] false))

(defn fix-browser-url
  "Local installations provide broken URLs, with the Release value in the path."
  [u]
  (let [s (str u)]
    (uri (s/replace s #"icd.who.int/browse11_\d\d\d\d-\d\d/" "icd.who.int/browse11/"))))

(defn normalize-graph
  "Converts a basic {subject {predicate #{object}}} graph into a form
   where blank nodes are brought inline and faulty predicates are rewritten."
  [graph]
  (reduce
   (fn [g [s po]]
     (if (blank? s)
       g
       (assoc g s
              (into {}
                    (map (fn [[p so]]
                           (if (= p browser-url)
                             [p (set (map fix-browser-url so))]
                             [p (set (map #(if (blank? %) (get graph %) %) so))]))
                         po)))))
   {}
   graph))

(defn remove-browser-url
  [s]
  (s/replace s #", *\"browserUrl\": *\"NA\"" ""))

(defn update-entity-context
  [s]
  (-> s
      (s/replace "\"http://id.who.int/icd/contexts/contextForFoundationEntity.json\"" entity-context)
      remove-browser-url))

(defn update-top-context
  [s]
  (-> s
      (s/replace "\"http://id.who.int/icd/contexts/contextForTopLevel.json\"" top-context)
      remove-browser-url))

(defn update-linearization-context
  [s]
  (-> s
      (s/replace "\"http://id.who.int/icd/contexts/contextForLinearizationEntity.json\"" linearization-context)
      remove-browser-url))

(defn init-authentication
  "Initializes authentication, returning it as an authentication object.
  If authentication is provided along with a client-id and client-secret, then it will still be used,
  but the credentials will be attached for future renewals."
  [{:keys [client secret auth] :as opts}]
  (cond
    auth (cond-> {:auth (atom auth)}
           (and client secret) (assoc :client client
                                      :secret secret))
    (and client secret) {:client client
                         :secret secret
                         :auth (atom (access/get-access client secret))}))

(defn update-auth!
  [auth]
  (and
   auth
   (let [{:keys [client secret]} auth]
     (when (and client secret)
       (reset! (:auth auth) (access/get-access client secret)))
     auth)))

(defn headers
  "Returns the appropriate header info for a request"
  ([] (headers nil))
  ([authentication]
   (cond-> {:accept "application/json"
            :API-Version "v2"
            :Accept-Language "en"}
     authentication (assoc :Authorization @(:auth authentication)))))

(defn request-object
  [get-thunk context-update authentication]
  (let [resp (loop [r (get-thunk) c 0]
               (if (or (= 200 (:status r)) (>= c RETRIES))
                 r
                 (do
                   (when (= 401 (:status r)) (update-auth! authentication))
                   (recur (get-thunk) (inc c)))))]
    (-> resp
        :body
        context-update
        jsld/string->graphs
        :default
        into-graph)))

(defn icd-entity
  ([opts] (icd-entity opts nil))
  ([{:keys [host port release authentication]} id]
   (let [context-update (if id update-entity-context update-top-context)
         get-entity #(http/request {:method :get
                                    :uri {:scheme (if authentication "https" "http")
                                          :host host
                                          :port port
                                          :path (if id (str "/icd/entity/" id) "/icd/entity")
                                          :query (str "releaseId=" release)}
                                    :headers (headers authentication)})]
     (request-object get-entity context-update authentication))))

(defn get-linearization
  ([opts] (get-linearization opts nil))
  ([{:keys [host port authentication linearization release]} id]
   (let [context-update (if id update-linearization-context update-top-context)
         get-linear #(http/request {:method :get
                                    :uri {:scheme (if authentication "https" "http")
                                          :host host
                                          :port port
                                          :path (str "/icd/release/11/" release "/" linearization
                                                     (when id (str "/" id)))}
                                    :headers (headers authentication)})]
     (request-object get-linear context-update authentication))))

(defn residual? [c] (or (= c \u) (= c \o)))

(defn linearization-id
  "Extracts the linearization ID from a linearization URI. This will include a residual value, if present"
  [^URI lin-uri]
  (let [path (.getPath lin-uri)
        end-pos (inc (s/last-index-of path \/))
        end-pos (if (residual? (nth path end-pos))
                  (inc (s/last-index-of path \/ (- end-pos 2)))
                  end-pos)]
    (subs path end-pos)))

(defn entity-id
  [^URI entity-url]
  (let [path (.getPath entity-url)]
    (subs path (inc (s/last-index-of path \/)))))

(defn load-all-entities
  [out opts]
  (let [get-entity #(icd-entity opts %)
        top-graph (icd-entity opts)
        children (into EMPTY-QUEUE (-> top-graph (get entity-object) (get top-concept)))]
    (binding [ttl/*object-list-limit* 1]
      (ttl/write-triples-map! out top-graph)
      (loop [queue children processed #{} counter 0]
        (let [child (peek queue)
              remaining (pop queue)
              ncounter (inc counter)]
          (when (zero? (mod ncounter 1000))
            (println ncounter "entities"))
          (if (and child (or (nil? limit) (< counter limit)))
            (let [child-id (entity-id child)
                  child-graph (get-entity child-id)
                  grandchildren (-> child-graph (get child) (get child-rel) (set/difference processed))]
              (ttl/write-triples-map! out (normalize-graph child-graph))
              (recur (into remaining grandchildren) (into processed grandchildren) ncounter))
            (do (println counter "entities")
                counter)))))))

(defn load-all-linear
  [out {:keys [release linearization] :as opts}]
  (let [linearization-object (get-linearization-object release linearization)
        get-linear #(get-linearization opts (linearization-id %))
        top-graph (get-linearization opts)
        top-entities (-> top-graph (get linearization-object) (get top-concept))
        initial-queue (into EMPTY-QUEUE top-entities)]
    (binding [ttl/*object-list-limit* 1]
      (ttl/write-triples-map! out top-graph)
      (loop [queue initial-queue processed #{} counter 0]
        (let [linearzn (peek queue)
              remaining (pop queue)
              ncounter (inc counter)]
          (when (zero? (mod ncounter 1000))
            (println ncounter "linearizations"))
          (if (and linearzn (or (nil? limit) (< counter limit)))
            (let [lin-graph (get-linear linearzn)
                  children (-> lin-graph (get linearzn) (get child-rel) (set/difference processed))]
              (ttl/write-triples-map! out (normalize-graph lin-graph))
              (recur (into remaining children) (into processed children) ncounter))
            (do (println counter "linearizations")
                counter)))))))

(defn entity-main
  [{:keys [outfile] :as opts}]
  (binding [ttl/*context-prefixes* PREFIXES]
    (with-open [out (io/writer outfile)]
      (ttl/write-prefixes! out PREFIXES)
      (load-all-entities out opts))))

(defn linearization-main
  [{:keys [linear] :as opts}]
  (binding [ttl/*context-prefixes* LINEAR-PREFIXES]
    (with-open [out (io/writer linear)]
      (ttl/write-prefixes! out LINEAR-PREFIXES)
      (load-all-linear out opts))))

(defn -main [& args]
  (let [{:keys [help auth outfile linear] :as opts} (init/get-setup args)
        _ (when help
            (init/print-usage)
            (System/exit 0))
        options (-> opts
                    (assoc :authentication (init-authentication opts))
                    (dissoc :auth :client :secret))]
    (if (= outfile linear)
      (binding [ttl/*context-prefixes* LINEAR-PREFIXES]
        (with-open [out (io/writer outfile)]
          (ttl/write-prefixes! out LINEAR-PREFIXES)
          (load-all-entities out options)
          (load-all-linear out options)))
      (do
        (entity-main options)
        (linearization-main options)))))
