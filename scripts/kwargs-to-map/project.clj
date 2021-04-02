(defproject kwargs-to-map "0.1.0-SNAPSHOT"
  :description "Script to convert re-com codebases to map/hiccup syntax"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [rewrite-clj "1.0.579-alpha"]]
  :repl-options {:init-ns kwargs-to-map.core}
  :main kwargs-to-map.core)
