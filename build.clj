(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b] ; for b/git-count-revs
            [org.corfield.build :as bb]
            [clojure.string :as str]))

(def lib 'com.github.yapsterapp/a-frame)
(def web-url "https://github.com/yapsterapp/a-frame")
(def scm-url "git@github.com/yapsterapp/a-frame.git")
(def version "3.0.0-alpha2-SNAPSHOT")
#_ ; alternatively, use MAJOR.MINOR.COMMITS:
(def version (format "3.0.%s-SNAPSHOT" (b/git-count-revs nil)))

(defn sha
  "the git sha is needed to tag a release in the pom.xml for cljdocs"
  [{:keys [dir path] :or {dir "."}}]
  (-> {:command-args (cond-> ["git" "rev-parse" "HEAD"]
                       path (conj "--" path))
       :dir (.getPath (b/resolve-path dir))
       :out :capture}
      b/process
      :out
      str/trim))

(defn test "Run the tests." [opts]
  (bb/run-tests opts))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      (assoc :lib lib
             :version version
             :scm {:tag (sha nil)
                   :connection (str "scm:git:" scm-url)
                   :developerConnection (str "scm:git:" scm-url)
                   :url web-url})
      (bb/run-tests)
      (bb/clean)
      (bb/jar)))

(defn install "Install the JAR locally." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/install)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/deploy)))
