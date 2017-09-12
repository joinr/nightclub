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
            [seesaw.core :as s])
  (:gen-class))

;; ;;Note:
;; ;;java.util.WindowsPreferences is getting pissed at us,
;; ;;not letting us open.  Recommend going to a file instead...

;; (def  editor (atom nil))

;; (defn ed []
;;   (reset! editor
;;     (editors/create-console "clj")))

;; (defn kill-ed []
;;   (println "exiting")
;;   (reset!   editor nil)
;;   (s/invoke-later
;;    (throw (Exception. "Killed REPL!"))))

;; (in-ns 'nightcode.window)
;; ;custom listener to avoid killing the process for testing...
;; (defn add-listener!
;;   "Sets callbacks for window events."
;;   ([window kill-on-exit?]
;;    (override-quit-handler!)
;;    (.addWindowListener window
;;     (proxy [WindowAdapter] []
;;       (windowActivated [e]
;;         ; force hints to hide
;;         (reset! shortcuts/down? false)
;;         (shortcuts/toggle-hint! @editors/tabs false)
;;         (shortcuts/toggle-hints! @ui/root false)
;;         ; update the project tree and various panes
;;         (ui/update-project-tree!)
;;         (file-browser/update-card!)
;;         (git/update-sidebar!))
;;       (windowClosing [e]
;;         (when (and (show-shut-down-dialog!) kill-on-exit?)
;;           (System/exit 0))))))
;;   ([window] (add-listener! window true)))

;; (in-ns 'nightrepl.core)

;; ;;create commands to clear the repl...
;; (defn create-window
;;   []
;;   (doto (s/frame :title "Nightrepl"
;;                  :content (repl/create-pane (ed))
;;                  :on-close :hide;:exit
;;                  :size [800 :by 600])
;;     ; set various window properties
;;     window/enable-full-screen!
;;     #(window/add-listener! % false)))

;; (defn -main [& args]
;;   ; listen for keys while modifier is down
;;   (shortcuts/listen-for-shortcuts!
;;     (fn [key-code]
;;       (case key-code
;;         ; Q
;;         81  (do (kill-ed) false) #_(window/confirm-exit-app!)
;;         ; else
;;         false)))
;;   ; this will give us a nice dark theme by default, or allow a lighter theme
;;   ; by adding "-s light" to the command line invocation
;;   (window/set-theme! (custom/parse-args args))
;;   ; create and display the window
;;   ; it's important to save the window in the ui/root atom
;;   (s/invoke-later
;;     (s/show! (reset! ui/root (create-window)))))

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
                                (repl/create-pane console)
                                ;(builders/create-pane)
                                :divider-location 0.5
                                :resize-weight 0.5)
            (s/top-bottom-split (editors/create-pane)
                                (repl/create-pane console)
                                ;(builders/create-pane)
                                :divider-location 0.8
                                :resize-weight 0.5)))
        :divider-location 0.32
        :resize-weight 0))))

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
                 :on-close :nothing)
    ; set various window properties
    window/enable-full-screen!
    window/add-listener!))

(defn -main
  "Launches the main window."
  [& args]
  (let [parsed-args (custom/parse-args args)]
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
