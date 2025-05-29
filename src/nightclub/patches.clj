(ns nightclub.patches
  (:require [nightcode.window]))

(in-ns 'nightcode.window)

;;simple interrupt to toggle avoiding automatic shutdowns
;;when we close the window...
(def  shutdown-on-exit (atom true))

(defn show-shut-down-dialog!
  "Displays a dialog confirming whether the program should shut down."
  []
  (and (dialogs/show-shut-down-dialog! (editors/unsaved-paths))
       @shutdown-on-exit))

(defn set-shutdown! [x]
  (reset! nightcode.window/shutdown-on-exit x))

;;we rewrite this to have a fallback for java > 8, we just
;;use nimbus dark theme.
(defn set-theme!
  "Sets the theme based on the command line arguments."
  [args]
  (s/invoke-now
   (s/native!)
   (if (not= (System/getProperty "java.vm.specification.version") "1.8")
     (set-nimbus!)
     (let [{:keys [shade skin-object theme-resource]} args]
       (when theme-resource (reset! ui/theme-resource theme-resource))
       (set-substance!)
       (SubstanceLookAndFeel/setSkin (or #_skin-object (GraphiteSkin.)))
       (substance/enforce-event-dispatch)))))


(in-ns 'nightclub.patches)

