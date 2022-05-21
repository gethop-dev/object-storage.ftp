(defproject dev.gethop/object-storage.ftp "0.1.8"
  :description "A library that provides an Integrant key for managing objects in a FTP server"
  :url "https://github.com/gethop-dev/object-storage.ftp"
  :license {:name "Mozilla Public License 2.0"
            :url "https://www.mozilla.org/en-US/2.0/"}
  :min-lein-version "2.9.8"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [integrant "0.7.0"]
                 [magnet/object-storage.core "0.1.1"]
                 [com.velisco/clj-ftp "0.3.15"]]
  :deploy-repositories [["snapshots" {:url "https://clojars.org/repo"
                                      :username :env/CLOJARS_USERNAME
                                      :password :env/CLOJARS_PASSWORD
                                      :sign-releases false}]
                        ["releases"  {:url "https://clojars.org/repo"
                                      :username :env/CLOJARS_USERNAME
                                      :password :env/CLOJARS_PASSWORD
                                      :sign-releases false}]]
  :profiles
  {:dev [:project/dev :profiles/dev]
   :repl {:repl-options {:host "0.0.0.0"
                         :port 4001}}
   :profiles/dev {}
   :project/dev {:dependencies [[digest "1.4.9"]]
                 :plugins [[jonase/eastwood "1.2.3"]
                           [lein-cljfmt "0.8.0"]]
                 :eastwood {:linters [:all]
                            :ignored-faults {:unused-namespaces {dev.gethop.object-storage.ftp-test true}}
                            :debug [:progress :time]}}})
