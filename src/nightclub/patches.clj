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

(in-ns 'nightclub.patches)

