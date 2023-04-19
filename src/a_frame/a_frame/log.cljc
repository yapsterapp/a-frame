(ns prpr3.a-frame.log
  "overrides of the timbre logging macros
   which set a context from an a-frame
   interceptor-context key, or app-ctx key"
  #?(:cljs (:require-macros [prpr3.a-frame.log]))
  (:require
   [taoensso.timbre :as timbre]
   [prpr3.a-frame.schema :as af.schema]))

(defmacro with-a-frame-log-context
  [context-src & body]
  `(do
     (when (not (map? ~context-src))
       (throw
        (ex-info
         "context-src must be a map"
         {:context-src ~context-src})))

     (timbre/with-context (get ~context-src af.schema/a-frame-log-ctx)
       ~@body)))

;;; Log using print-style args
(defmacro log*
  [context-src config level & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! ~level  :p ~args ~{:?line (@#'timbre/fline &form) :config config})))

(defmacro log
  [context-src level & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! ~level  :p ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro trace
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :trace  :p ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro debug
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :debug  :p ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro info
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :info   :p ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro warn
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :warn   :p ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro error
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :error  :p ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro fatal
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :fatal  :p ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro report
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :report :p ~args ~{:?line (@#'timbre/fline &form)})))

;;; Log using format-style args
(defmacro logf*
  [context-src config level & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! ~level  :f ~args ~{:?line (@#'timbre/fline &form) :config config})))

(defmacro logf
  [context-src level & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! ~level  :f ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro tracef
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :trace  :f ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro debugf
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :debug  :f ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro infof
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :info   :f ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro warnf
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :warn   :f ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro errorf
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :error  :f ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro fatalf
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :fatal  :f ~args ~{:?line (@#'timbre/fline &form)})))

(defmacro reportf
  [context-src & args]
  `(with-a-frame-log-context ~context-src
     (timbre/log! :report :f ~args ~{:?line (@#'timbre/fline &form)})))
