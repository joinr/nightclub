(ns nightclub.core
  (:require [nightcode.customizations :as custom]
            [nightcode.editors :as editors]
            [nightcode.projects :as projects]
            [nightcode.utils :as utils]
            [nightcode.sandbox :as sandbox]
            [nightcode.repl :as repl]
            [nightcode.shortcuts :as shortcuts]
            [nightcode.ui :as ui]
            [nightcode.window :as window]
            [nightclub.patches] ;;temporary patches to NC
            [seesaw.core :as s]
            [cemerick.pomegranate :refer [add-dependencies]]
            )
  (:gen-class))

;;initial expression to eval in embedded repl
(def init (atom nil))
;;A really dumb ns for defining an event system for decoupled components
;;to communicate through (like editor -> repl interactions and the like).
;;This is not meant to be a high-throughput system; we basically
;;have an observable atom, upon which we can subscribe to events.
(def noisy (atom true))
(defn toggle-noisy [] (swap! noisy (fn [n] (not n))))
;;From Stuart Sierra's blog post, for catching otherwise "slient" exceptions
;;Since we're using multithreading and the like, and we don't want
;;exceptions to get silently swallowed
(let [out *out*]
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (when @noisy 
         (binding [*out* out]
           (println ["Uncaught Exception on" (.getName thread) ex])))))))

;;creates a pane with a project pane, a file-based editor tree and a
;;repl.
(defn create-window-content
  "Returns the entire window with all panes."
  [args]
  (let [console (editors/create-console "*REPL*")
        one-touch! #(doto % (.setOneTouchExpandable true))]
    (one-touch!
      (s/left-right-split
       ;(one-touch!
        (projects/create-pane console)
;          #_(s/top-bottom-split (projects/create-pane console)
;                                (repl/create-pane console)
        ;                        :divider-location 0.8
         ;                       :resize-weight 0.5))
        (one-touch!
          (if (= (:panel args) "horizontal")
            (s/left-right-split (editors/create-pane)
                                (repl/create-pane console
                                                  :interrupt (atom nil))
                                ;(builders/create-pane)
                                :divider-location 0.5
                                :resize-weight 0.5)
            (s/top-bottom-split (editors/create-pane)
                                (repl/create-pane console :interrupt (atom nil))
                                ;(builders/create-pane)
                                :divider-location 0.8
                                :resize-weight 0.5)))
        :divider-location 0.32
        :resize-weight 0))))

;;note: we're not cleaning up well here, but we're also no longer
;;shutting down by default.
;;note: we now allow caller to pass in window-args...
(defn create-window
  "Creates the main window."
  [& {:keys [window-args cli-args]}]
  (let [_   (window/set-shutdown!  (= (:on-close window-args)
                                      :exit))
        window-args (merge
                     {:title (str "Nightclub Embedded REPL " #_(or (some-> "nightcode.core"
                                                           utils/get-project
                                                           (nth 2))
                                                   "beta"))
                      :content (create-window-content cli-args)
                      :width 1242
                      :height 768
                      :icon "images/logo_launcher.png"
                      :on-close :dispose}
                     window-args)]
    (doto (apply s/frame (flatten (seq window-args)))
                                        ; set various window properties
      window/enable-full-screen!
      window/add-listener!)))
   
(defn main-window
  "Launches the main window."
  [& {:keys [window-args cli-args]}]
  (let [parsed-args (custom/parse-args cli-args)
        _           (window/set-shutdown! false)
        init-eval (when @init (let [res @init
                                    _   (reset! init nil)]
                                res))
        ]
      (window/set-icon! "images/logo_launcher.png")
      (window/set-theme! parsed-args)
      (sandbox/create-profiles-clj!)
      (sandbox/read-file-permissions!)
      (s/invoke-later
                                        ; listen for keys while modifier is down
       (shortcuts/listen-for-shortcuts!
        (fn [key-code]
          (case key-code
                                        ; enter
            10 (projects/toggle-project-tree-selection!)
                                        ; page up
            33 (editors/move-tab-selection! -1)
                                        ; page down
            34 (editors/move-tab-selection! 1)
                                        ; up
            38 (projects/move-project-tree-selection! -1)
                                        ; down
            40 (projects/move-project-tree-selection! 1)
                                        ; Q
            81 (window/confirm-exit-app!)
                                        ; W
            87 (editors/close-selected-editor!)
                                        ; else
            false)))
                                        ; create and show the frame
       (s/show! (reset! ui/root (create-window :window-args window-args
                                               :cli-args parsed-args)))
                                        ; initialize the project pane
       (ui/update-project-tree!)
       ;;necessary hack to get linenumbers correct
       (reset! editors/font-size @editors/font-size)
       (when init-eval (repl/send-repl! (str init-eval)))
       )))

(defn ^java.io.Closeable string-push-back-reader 
  "Creates a PushbackReader from a given string"
  [^String s]
  (java.io.PushbackReader. (java.io.StringReader. s)))

(defn string->forms [txt]
  (let [rdr (string-push-back-reader txt)]
    (loop [acc []]      
      (let [res (clojure.edn/read {:eof :end-of-file} rdr )]
        (if (identical? res :end-of-file)
          acc
          (recur (conj acc res)))))))

(defn selected-path [] @nightcode.ui/tree-selection)
(defn editor-selection []
  (some->> (get  @editors/editors (selected-path))
           (:text-area)
           (.getSelectedText)))

(defn editor-text []
  (some->> (get  @editors/editors (selected-path))
           (:text-area)
           (.getText)))

(defn editor-pane []
  (some->> (get  @editors/editors (selected-path))
           (:view)
           (.getLayout)
           (#(.getLayoutComponent % "Center"))))

(defn stringify [frm]
  (cond (string? frm)  (str \" frm \")
        (char? frm)    (str "\\" frm)
        :else  (str frm)))

(defn eval-selection! []
  (when-let [selected (editor-selection)]
    (do (repl/println-repl! "")
        (->> (string->forms selected)
             (map stringify)
             (clojure.string/join \space)             
             (repl/send-repl!)))))

(defn eval-selected-file []
  (when-let [txt (editor-text)]
    (repl/send-repl! txt)))
  
(defn load-selected-file! []
  (when-let [p (selected-path)]    
    (repl/println-repl! (str [:loading-file p]))
    (repl/echo-repl! `(load-file ~p))))

;;hook up the plumbing...
(defn register-handlers! []
  (editors/set-handler :eval-selection (fn [_] (future (eval-selection!))))
  (editors/set-handler :load-in-repl   (fn [_] (future (load-selected-file!))))
  )

(defn resize-plaf-font [nm size]
  (let [fnt (javax.swing.UIManager/get nm)
        size (float size)
        newfont (.deriveFont fnt (float size))]
    (javax.swing.UIManager/put nm 
        (javax.swing.plaf.FontUIResource. newfont))))

(defn update-look []
  (s/invoke-now
   (fn [] (javax.swing.SwingUtilities/updateComponentTreeUI @ui/root))))

;;in case we're not in a lein project, i.e. an embedded repl...
(def default-repositories
  {"central" {:url "https://repo1.maven.org/maven2/", :snapshots false},
   "clojars" {:url "https://repo.clojars.org/"}})

(defn add-dependencies!
  "Given a vector of clojure dependency vectors - or a single dependency 
   vector - dynamically resolves the dependencies and adds them to the 
   class path using alembic.still/distill.  Tries to determine if 
   the jvm session was launch local to a project, if not will use 
   default clojure repositories by default.
   
   usage:
   (add-dependencies! '[incanter \"1.5.6\"])
   (add-dependencies! '[[incanter \"1.5.6\"][seesaw \"1.4.5\"]])"
  [deps & {:keys [repositories verbose proxy]
           :or {verbose true}
           :as options}]
  (let [repositories (or repositories
                         (when-not
                           (.exists (clojure.java.io/file "project.clj"))
                           default-repositories))
        opts         (assoc options :repositories repositories)]        
    (add-dependencies :coordinates deps
                      :repositories default-repositories)))



(defn attach!
    "Creates a nightclub window, with project, repl, and file editor(s).
     Caller may supply an initial form for evaluation via :init-eval, 
     and behavior for closing."
  [& {:keys [init-eval on-close window-args cli-args]
       :or {on-close :dispose cli-args ""}}]
  (let [_ (case on-close
            :exit (window/set-shutdown! true)
            (window/set-shutdown! false))
        _   (reset! init (or init-eval
                             (str "(ns " *ns* ")")))]
    (do (main-window :window-args window-args
                     :cli-args cli-args)
        (register-handlers!)
        )))

