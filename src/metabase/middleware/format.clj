(ns metabase.middleware.format
  (:require [cheshire.factory :require :all]
            [clojure.core.match :refer [match]]
            [medley.core :refer [filter-vals map-vals]]
            [metabase.util :as util]))

(declare -format-response)

;; # SHADY HACK
;; Tell the JSON middleware to use a date format that includes milliseconds
(intern 'cheshire.factory 'default-date-format "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

;; # FORMAT RESPONSE MIDDLEWARE
(defn format-response
  "Middleware that recurses over Clojure object before it gets converted to JSON and makes adjustments neccessary so the formatter doesn't barf.
   e.g. functions and delays are stripped and H2 Clobs are converted to strings."
  [handler]
  (fn [request]
    (-format-response (handler request))))

(defn- remove-fns-and-delays
  "Remove values that are fns or delays from map M."
  [m]
  (filter-vals #(and (not (fn? %))
                     (not (delay? %)))
               m))

(defn- type-key
  [obj]
  (cond (map? obj) :map
        (coll? obj) :coll
        (= (type obj) org.h2.jdbc.JdbcClob) :jdbc-clob
        :else :obj))

(defn- -format-response [obj]
  (case (type-key obj)
    :obj obj
    :map (->> (remove-fns-and-delays obj)
              (map-vals -format-response))       ; recurse over all vals in the map
    :coll (map -format-response obj)             ; recurse over all items in the collection
    :jdbc-clob (util/jdbc-clob->str obj)))
