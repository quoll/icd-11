(ns ^{:author "Paula Gearon"}
    quoll.icd11.json-ld
  (:require [donatello.ttl :as ttl])
  (:import [java.io ByteArrayInputStream]
           [java.net URI]
           [com.fasterxml.jackson.databind ObjectMapper]
           [com.apicatalog.tree.io.jakcson Jackson2Parser]
           [com.apicatalog.jsonld JsonLd Options]
           [com.apicatalog.jsonld Document]
           [com.apicatalog.rdf.api RdfQuadConsumer]))

(def blank-node-context (volatile! {}))

(def xsd-string "http://www.w3.org/2001/XMLSchema#string")

(defn reset-context! [] (vreset! blank-node-context {}))

(defn blank-node
  [id]
  (or (@blank-node-context id)
      (let [n (ttl/blank-node)]
        (vswap! blank-node-context assoc id n)
        n)))

(defn uri
  [s]
  ;;(str "<" s ">")
  (URI. s))

(defn to-subject
  [s]
  (if (RdfQuadConsumer/isBlank s) (blank-node s) (uri s)))

(defn to-object
  [s dt l]
  (when-not (RdfQuadConsumer/isValidObject dt l nil)
    (throw (ex-info "Unexpected Object data" {:object s :datatype dt :language l})))
  (cond
    (RdfQuadConsumer/isLiteral dt l nil) (if (RdfQuadConsumer/isLangString dt l nil)
                                           (ttl/lang-literal s l)
                                           (if dt
                                             (if (= xsd-string dt) s (ttl/typed-literal s (uri dt)))
                                             s))
    (RdfQuadConsumer/isBlank s) (blank-node s)
    :default (uri s)))

(defrecord QuadReceiver [vtriples]
  RdfQuadConsumer
  (quad [this subject predicate object datatype language _ _]
    (vswap! vtriples conj!
            [(to-subject subject) (uri predicate) (to-object object datatype language)])
    this))

(def mapper (ObjectMapper.))

(defn input-stream-string
  [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

(defn string->graphs
  "Converts a JSON-LD string to a graph"
  [s]
  (reset-context!)
  (let [parse #(.parse (Jackson2Parser. mapper) %)
        options (Options/newOptions)
        vtriples (volatile! (transient []))
        rdf-consumer (->QuadReceiver vtriples)] 
    (-> (input-stream-string s)
        parse
        Document/of
        (JsonLd/toRdf rdf-consumer options))
    (persistent! @vtriples)))

