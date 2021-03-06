(ns cassandra.cassandra-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [jepsen.core :as jepsen]
            [cassandra.collections.map :as map]
            [cassandra.collections.set :as set]
            [cassandra
             [core    :as cassandra]
             [batch   :as batch]
             [counter :as counter]
             [lwt     :as lwt]
             [nemesis :as can]
             [runner  :as runner]]))

;; Skip these tests for unit testing.
;; They take a long time and needs Cassandra cluster
(comment

  (def nodes ["n1" "n2" "n3" "n4" "n5"])

  (defn check
    [test-fn nemesis joining clock]
    (-> {:nodes nodes :rf 3 :db (cassandra/db "3.11.3") :concurrency 5 :time-limit 60}
        (runner/combine-nemesis nemesis joining clock)
        test-fn
        jepsen/run!
        :results
        :valid?
        is))

  (defmacro add-nemesis
    [name suffix test-fn joining clock]
    `(do
       (deftest ~(symbol (str name "-steady"      suffix))
         (check ~test-fn `(can/none)                ~joining ~clock))
       (deftest ~(symbol (str name "-flush"       suffix))
         (check ~test-fn `(can/flush-and-compacter) ~joining ~clock))
       (deftest ~(symbol (str name "-bridge"      suffix))
         (check ~test-fn `(can/bridge)              ~joining ~clock))
       (deftest ~(symbol (str name "-halves"      suffix))
         (check ~test-fn `(can/halves)              ~joining ~clock))
       (deftest ~(symbol (str name "-isolation"   suffix))
         (check ~test-fn `(can/isolation)           ~joining ~clock))
       (deftest ~(symbol (str name "-crash"       suffix))
         (check ~test-fn `(can/crash)               ~joining ~clock))))

  (defmacro add-joining
    [name suffix test-fn clock]
    `(do
       (add-nemesis ~name ~(str ""                 suffix) ~test-fn
                    {:name ""                 :bootstrap false :decommission false} ~clock)
       (add-nemesis ~name ~(str "-bootstrap"       suffix) ~test-fn
                    {:name "-bootstrap"       :bootstrap true  :decommission false} ~clock)
       (add-nemesis ~name ~(str "-decommissioning" suffix) ~test-fn
                    {:name "-decommissioning" :bootstrap false :decommission true}  ~clock)
       (add-nemesis ~name ~(str "-rejoining"       suffix) ~test-fn
                    {:name "-rejoining"       :bootstrap true  :decommission true}  ~clock)))

  (defmacro def-tests
    [test-fn]
    (let [name# (-> test-fn name (string/replace "-test" ""))]
      `(do
         (add-joining name# ""              ~test-fn {:name ""              :bump false :strobe false})
         (add-joining name# "-clock-bump"   ~test-fn {:name "-clock-bump"   :bump true  :strobe false})
         (add-joining name# "-clock-strobe" ~test-fn {:name "-clock-strobe" :bump false :strobe true})
         (add-joining name# "-clock-drift"  ~test-fn {:name "-clock-drift"  :bump true  :strobe true}))))

  (def-tests batch/batch-test)
  (def-tests map/map-test)
  (def-tests set/set-test)
  (def-tests counter/cnt-inc-test)
  (def-tests lwt/lwt-test))
