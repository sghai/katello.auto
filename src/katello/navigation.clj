(ns katello.navigation
  (:require (katello [conf :as conf]
                     [ui :as ui]
                     [notifications :as notif])
            [ui.navigate :as nav]
            [slingshot.slingshot :refer [throw+ try+]]
            [webdriver :as browser])
  (:import [org.openqa.selenium NoSuchElementException]))
  
(defn environment-breadcrumb
  "Locates a link in the environment breadcrumb UI widget. If there
  are multiple environment paths, and you wish to select Library,
  'next' is required."
  [name & [next]]
  (let [prefix "//a[normalize-space(.)='%s' and contains(@class, 'path_link')"]
    (format
     (str prefix (if next " and ../../..//a[normalize-space(.)='%s']" "") "]")
     name next)))

(defn select-environment-widget [env & [{:keys [next-env wait]}]]
  (do (when (browser/exists? ::ui/expand-path)
        (browser/click ::ui/expand-path))
      (browser/click (environment-breadcrumb (:name env) (:name next-env)))))

(defn search-here [search-term]
  (browser/quick-fill [::ui/search-bar search-term
                       ::ui/search-submit browser/click]))

(def ^{:doc "Returns a selenium locator for an item in a left pane
             list (by the name of the item) - truncate to 32 chars to
             match ellipsis behavior."}
  left-pane-item
  (browser/template "//div[@id='list']//div[starts-with(normalize-space(.),'%1.32s')]"))

(defn scroll-left-pane-more
  "Loads another group of 25 items in the left pane, by scrolling down" []
  (browser/execute-script (str "window.scrollTo(0,1000000);"))
  (browser/ajax-wait))

(defn scroll-left-pane-until
  "Scroll the left pane down until (side-effecty) no-arg function f
   returns truthy, or the end of the list is hit."
  [f]
  (loop [prev-current -1]
    (let [current-items (ui/current-items)]
      (cond (= current-items prev-current)
            (throw+ {:type ::infinite-scroll-failed
                     :msg "Infinite scroll failed to load more items"})

            (and (< current-items (ui/total-items))
                 (not (f)))
            (do (scroll-left-pane-more)
                (recur current-items))

            :else nil))))

(defn scroll-to-left-pane-item [ent]
  (scroll-left-pane-until #(browser/exists? (left-pane-item (:name ent)))))

(defn scroll-org-switcher
  []
  (browser/execute-script "$('#allowed-orgs').data('jsp').scrollByY(300);"))

(defn scroll-right-pane-more
  []
  (browser/execute-script "$('#panel_main').data('jsp').scrollByY(300);"))

(defn scroll-to-right-pane-item [ent]
  (while (not (browser/visible? ent))
    (scroll-right-pane-more)))

(defn choose-left-pane
  "Selects an entity in the left pane. If the entity is not found, a
   search is performed and the select is attempted again."
  ([entity]
     (choose-left-pane left-pane-item entity))
  ([templ entity]
     (let [loc (templ (:name entity))]
       (if-let [el (browser/element loc)]
         (browser/click el)
         (do (search-here (format "\"%s\"" (:name entity)))
             (browser/click loc))))))

;; Define navigation pages
;; Note, it's designed this way, rather than one big tree, so that
;; errors in one component namespace (eg activation keys) don't
;; prevent others from being worked on.

(defmulti pages
  "The navigation layout of the UI. Each item in the tree is a new
   page or tab, that you can drill down into from its parent
   item. Each item contains a keyword to refer to the location in the
   UI, and a function to navigate to the location from its parent
   location. Other namespaces can add their structure here."
  #'ui/component-deployment-dispatch)

(defmethod pages [(-> *ns* .getName symbol) :katello.deployment/any] [& _]
  (nav/nav-tree
   [::top-level (fn [& _] (if (or (not (browser/exists? ::ui/log-out))
                                  (browser/exists? ::ui/confirmation-dialog))
                            (browser/to (@conf/config :server-url))
                            (do (browser/execute-script "window.scrollTo(0,0)")
                                (notif/dismiss-all-ui))))]))

(defmacro defpages
  "Define the pages needed to navigate to in this namespace, and
   dependent namespaces.  sym is a symbol for a function that returns
   these pages, that can be referred to in other calls to
   defpages. basenav is the page tree you want to graft onto, branches
   is a list of branches you want to graft.  branch should be
   formatted as [parent-graft-point child1 child2 ...]"
  [deployment basens & branches]
  `(defmethod pages [(-> *ns* .getName symbol) ~deployment] [_# cur-deployment#]
     (reduce nav/add-subnav-multiple
             (pages (quote ~basens) cur-deployment#)
             (for [[parent-graft-point# & children#] ~(vec branches)]
               (vector parent-graft-point#
                       (map nav/nav-tree children#))))))

(defprotocol Destination
  (go-to [dest] [dest arg] [dest start arg]
    "Navigates to a given destination (with optional starting point"))

(extend clojure.lang.Keyword
  Destination
  {:go-to (fn go-to*
            ([dest-kw start-kw arg]
               (nav/navigate start-kw dest-kw (-> dest-kw
                                                  namespace
                                                  symbol
                                                  (pages (ui/current-session-deployment))
                                                  nav/page-zip)
                             (list arg)))
            ([dest-kw arg]
               (go-to* dest-kw nil arg))
            ([dest-kw] (go-to* dest-kw nil nil)))})

(defn returns-403? [url]
  (= (clojure.string/join "\n "
                          ["403 - Permission denied You are not authorised to perform this action."
                           "Please request the required privileges from an administrator."
                           "Back"])
     ((browser/to url)
      (browser/text "//article[@id='maincontent']"))))

(defn current-org
  "Return the currently active org (a string) shown in the org switcher."
  []
  (let [org-text (browser/attribute ::ui/active-org :title)]
    (if (empty? org-text) nil org-text)))

(defn go-top []
  (browser/click "//a[@href='dashboard']"))

(defn switch-org
  "Switches to the given org. Other org-switcher functionality (eg
   setting default orgs) see katello.organizations/switch."
  ([{:keys [name]}]
     {:pre [name]}
     (go-to ::top-level)
     (when-not (= name (current-org))
       (browser/click (browser/find-element-under ::ui/switcher {:tag :a}))
       (while (not (browser/visible? (ui/switcher-link name)))
         (scroll-org-switcher))
       (browser/click (ui/switcher-link name)))))
