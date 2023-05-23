(ns a-frame.log.timbre
  (:require
   [taoensso.timbre :as timbre]
   [clojure.string :as str]))

(defn add-thread-name-middleware
  "Adds :thread-name entry"
  [data]

  #?(:clj
     (assoc data :thread-name (.getName (Thread/currentThread)))

     :cljs data))

(defn output-context
  [context]
  (when (not-empty context)
    (str
     " ["
     (->> context
          (map (fn [[k v]]
                 (str (name k) ":" v)))
          (interpose " ")
          (apply str))
     "]")))

(defn color-output-fn
  "Default (fn [data]) -> string output fn.
  Use`(partial color-output-fn <opts-map>)` to modify default opts."
  ([     data] (color-output-fn nil data))
  ([opts data] ; For partials
   (let [{:keys [no-stacktrace? _stacktrace-fonts]} opts
         {:keys [level ?err #_vargs msg_ ?ns-str _hostname_
                 timestamp_ ?line context]} data
         color {:info :green :warn :yellow :error :red}]
     (str
      (force timestamp_) " "
      (timbre/color-str
       (get color level :white)
       (str/upper-case (name level))) " "
      (timbre/color-str :cyan "[" (or ?ns-str "?") ":" (or ?line "?") "]") " "
      (if-let [tn (:thread-name data)]
        (str "[" tn "]")
        "[?]")
      (output-context context)
      " - "
      (force msg_)
      (when-not no-stacktrace?
        (when-let [err ?err]
          (str "\n" (timbre/stacktrace err opts))))))))

(defn plain-output-fn
  "Default (fn [data]) -> string output fn.
  Use`(partial default-output-fn <opts-map>)` to modify default opts."
  ([     data] (color-output-fn nil data))
  ([opts data] ; For partials
   (let [{:keys [no-stacktrace? _stacktrace-fonts]} opts
         {:keys [level ?err #_vargs msg_ ?ns-str _hostname_
                 timestamp_ ?line context]} data]
     (str
      (force timestamp_) " "
      (str/upper-case (name level)) " "
      "[" (or ?ns-str "?") ":" (or ?line "?") "] "
      (if-let [tn (:thread-name data)]
        (str "[" tn "]")
        "[?]")
      (output-context context)
      " - "
      (force msg_)
      (when-not no-stacktrace?
        (when-let [err ?err]
          (str "\n" (timbre/stacktrace err opts))))))))

(defn assoc-output-fn
  "put one of the predefinedd output-fns into the :println appender"
  [timbre-config
   {no-colors-in-logs? :no-colors-in-logs?
    :as opts}]
  (assoc-in timbre-config
            [:appenders :println :output-fn]
            (if no-colors-in-logs?
              (partial plain-output-fn opts)
              (partial color-output-fn opts))))

(defn add-middleware
  [middleware f]
  (if (some #(= % f) middleware)
    middleware
    (conj middleware f)))

(defn timbre-config
  [opts]
  (let [base-config timbre/example-config]
    (-> base-config
        (assoc-output-fn opts)
        (update :middleware add-middleware add-thread-name-middleware))))

(defn configure-timbre
  "configures timbre with a custom output-fn for the println appender,
   which prints any context object"
  ([] (configure-timbre nil))
  ([opts]
   (let [cfg (timbre-config opts)]
     (timbre/set-config! cfg))))
