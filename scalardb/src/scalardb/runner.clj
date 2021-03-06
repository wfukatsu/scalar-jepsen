(ns scalardb.runner
  (:gen-class)
  (:require [cassandra
             [core :as cassandra]
             [runner :as car]
             [nemesis :as can]]
            [jepsen
             [core :as jepsen]
             [cli :as cli]]
            [scalardb.transfer]
            [scalardb.transfer_append]))

(def tests
  "A map of test names to test constructors."
  {"transfer"        scalardb.transfer/transfer-test
   "transfer-append" scalardb.transfer-append/transfer-append-test})

(def test-opt-spec
  [(cli/repeated-opt nil "--test NAME" "Test(s) to run" [] tests)])

(defn test-cmd
  []
  {"test" {:opt-spec (->> test-opt-spec
                          (into car/cassandra-opt-spec)
                          (into cli/test-opt-spec))
           :opt-fn   (fn [parsed] (-> parsed cli/test-opt-fn))
           :usage    (cli/test-usage)
           :run      (fn [{:keys [options]}]
                       (doseq [i (range (:test-count options))
                               test-fn (:test options)
                               nemesis (:nemesis options)
                               joining (:join options)
                               clock (:clock options)]
                         (let [test (-> options
                                        (car/combine-nemesis nemesis joining clock)
                                        (assoc :db (cassandra/db))
                                        (dissoc :test)
                                        test-fn
                                        jepsen/run!)]
                           (when-not (:valid? (:results test))
                             (System/exit 1)))))}})

(defn -main
  [& args]
  (cli/run! (test-cmd)
            args))
