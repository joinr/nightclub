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
            [seesaw.core :as s])
  (:gen-class))

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
(defn create-window
  "Creates the main window."
  [args]
  (doto (s/frame :title (str "Nightcode " (or (some-> "nightcode.core"
                                                      utils/get-project
                                                      (nth 2))
                                              "beta"))
                 :content (create-window-content args)
                 :width 1242
                 :height 768
                 :icon "images/logo_launcher.png"
                 :on-close :dispose)
    ; set various window properties
    window/enable-full-screen!
    window/add-listener!))

(defn main-window
  "Launches the main window."
  [& args]
  (let [parsed-args (custom/parse-args args)
        _           (window/set-shutdown! false)
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
       (s/show! (reset! ui/root (create-window parsed-args)))
                                        ; initialize the project pane
       (ui/update-project-tree!))))

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
  
(defn eval-selection! []
  (when-let [selected (editor-selection)]
    (do (repl/println-repl! "")
        (repl/send-repl! (clojure.string/join \space (string->forms selected))))))

(defn eval-selected-file []
  (when-let [txt (editor-text)]
    (repl/send-repl! txt)))
  
(defn load-selected-file! []
  (when-let [p (selected-path)]    
    (repl/println-repl! (str [:loading-file p]))
    (repl/echo-repl! `(load-file ~p))))

;;hook up the plumbing...
(defn register-handlers! []
  (editors/set-handler :eval-selection (fn [_] (eval-selection!)))
  (editors/set-handler :load-in-repl   (fn [_] (load-selected-file!)))
  )

(defn attach!
    "Creates a nightclub window, with project, repl, and file editor(s).
     Caller may supply an initial form for evaluation via :init-eval, 
     and behavior for closing."
  [& {:keys [init-eval on-close args]
       :or {on-close :dispose args ""}}]
  (let [_ (case on-close
            :exit (window/set-shutdown! true)
            (window/set-shutdown! false))]
    (do (main-window args)
        (register-handlers!)
        (when init-eval
          (repl/send-repl! (str init-eval))))))

