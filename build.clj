(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api]
            [org.corfield.build :as bb]))

;; clojure -T:build test
(defn test "Run the tests." [opts]
  (bb/run-tests opts))

;; clojure -T:build ci
(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      (assoc :lib lib :version version :src-pom pom)
      (bb/run-tests)
      (bb/clean)
      (bb/jar)))

