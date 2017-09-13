(defproject nightclub "0.0.1-SNAPSHOT"
  :description "This is a revisitation of the nightcode swing database.
                Based on Zach Oakes's body of work.  The intent is to 
                extend his working example from NightCode, and provide
                a powerful facility for embedded swing-based editing in 
                clojure applications.  Similar to nightlite, but  
                theoretically more robust.  At a minimum, re-use 
                his widgets in a modular capacity.  Decouple the 
                lein dependency and other build components..."
  :dependencies [[joinr/nightcode "1.3.3-SNAPSHOT"]
                 [org.clojure/clojure "1.8.0"]
                 [seesaw "1.4.5"]]
  :source-paths  ["src" "../NightCode/src/clojure/"]   
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :profiles {:uberjar {:aot [nightclub.core]
                       :main nightclub.core
                       :jvm-opts ^:replace ["-Xmx1000m" "-XX:NewSize=200m" "-server"]
                       }}

  )

