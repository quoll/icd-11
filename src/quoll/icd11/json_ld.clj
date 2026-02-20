(ns ^{:author "Paula Gearon"}
    quoll.icd11.json-ld
  (:require [donatello.ttl :as ttl])
  (:import [java.io StringReader]
           [java.net URI]
           [java.util Optional]
           [com.apicatalog.jsonld JsonLd]
           [com.apicatalog.jsonld.api ToRdfApi]
           [com.apicatalog.jsonld.document JsonDocument]
           [com.apicatalog.rdf RdfDataset RdfGraph RdfTriple RdfLiteral RdfResource RdfValue]))

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

(defn rsc
  [^RdfValue n]
  (let [v (.getValue n)]
    (cond
      (.isIRI n) (uri v)
      (.isLiteral n) (let [^RdfLiteral l (.asLiteral n)
                           ^Optional lango (.getLanguage l)]
                       (if (.isPresent lango)
                         (ttl/lang-literal v (.get lango))
                         (let [dt (.getDatatype l)]
                           (if (= xsd-string dt)
                             v
                             (ttl/typed-literal v (uri dt))))))
      (.isBlankNode n) (blank-node v)
      :default (throw (ex-info (str "Illegal resource type: " n) {:resource n})))))

(defn as-triples
  [^RdfGraph graph]
  (let [g (vec (.toList graph))]
    (map (fn [^RdfTriple t]
           [(rsc (.getSubject t)) (rsc (.getPredicate t)) (rsc (.getObject t))])
         g)))

(defn graphs
  "Returns a map of graph names mapped to the graphs"
  [^ToRdfApi rdf]
  (let [^RdfDataset ds (.get rdf)]
    (reduce (fn [gs ^RdfResource gn]
              (let [nm (if (.isIRI gn) (uri (.toValue gn)) gn)
                    ^Optional grapho (.getGraph ds gn)]
                (if (.isPresent grapho)
                  (assoc gs nm (as-triples (.getGraph ds gn)))
                  (throw (ex-info (str "Unexpected missing graph:" nm) {:graph gn :dataset ds})))))
            {:default (as-triples (.getDefaultGraph ds))}
            (.getGraphNames ds))))

(defn string->graphs
  "Converts a JSON-LD string to a graph"
  [s]
  (reset-context!)
  (-> (StringReader. s)
      JsonDocument/of
      JsonLd/toRdf
      graphs))
