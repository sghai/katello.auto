(ns katello.providers
  (:require [com.redhat.qe.auto.selenium.selenium :refer [browser]] 
            (katello [locators :as locators] 
                     [notifications :as notification] 
                     [ui-tasks :refer [navigate fill-ajax-form in-place-edit]])))

;;
;; Providers
;;

;; Locators

(swap! locators/uimap merge
  {:new-provider                        "new"
   :provider-name-text                  "provider[name]"
   :provider-description-text           "provider[description]"
   :provider-repository-url-text        "provider[repository_url]"
   :provider-cert-text                  (locators/textbox "provider[certificate_attributes][contents]")
   :provider-create-save                "provider_submit"
   :remove-provider                     (locators/link "Remove Provider")
   :subscriptions                       (locators/link "Subscriptions")
   :import-manifest                     "new"
   :redhat-provider-repository-url-text "provider[repository_url]"
   :choose-file                         "provider_contents"
   :upload                              "upload_form_button"
   :force-import-checkbox               "force_import"
   :products-and-repositories           "//nav[contains(@class,'subnav')]//a[contains(.,'Products')]"
                
   ;;add product
   :add-product                         (locators/button-div "Add Product")
   :create-product                      "//input[@value='Create']"
   :product-name-text                   "//*[@name='product[name]']"
   :product-label-text                  "//*[@name='product[label]']"
   :product-description-text            "//*[@name='product[description]']"
   :remove-product                      (locators/link "Remove Product")
   ;;add repo
   :repo-name-text                      "repo[name]"
   :repo-label-text                     "repo[label]"
   :repo-url-text                       "repo[feed]" 
   :save-repository                     "//input[@value='Create']"
   :remove-repository                   (locators/link "Remove Repository")
   :repo-gpg-select                     "//select[@id='repo_gpg_key']"

   ;;redhat page
   :subscriptions-items                 "//table[@id='redhatSubscriptionTable']/tbody/tr"

   ;;gpg keys
   :gpg-key-name-text                   "gpg_key_name"
   :gpg-key-file-upload-text            "gpg_key_content_upload"
   :gpg-key-upload-button               "upload_gpg_key"
   :gpg-key-content-text                "gpg_key_content"
   :gpg-keys                            "//a[.='GPG Keys']"
   :gpg-keys-save                       "save_gpg_key"
   :new-gpg-key                         "new"
   :remove-gpg-key                      (locators/link "Remove GPG Key")


   ;;Package Filters
   :create-new-package-filter                (locators/link "+ New Filter")
   :new-package-filter-name                  "filter[name]"
   :new-package-filter-description           "filter[description]"
   :save-new-package-filter                  "filter_submit"
   :remove-package-filter-key                (locators/link "Remove Filter")})

(defn create
  "Creates a custom provider with the given name and description."
  [{:keys [name description]}]
  (navigate :new-provider-page)
  (fill-ajax-form {:provider-name-text name
                   :provider-description-text description}
                  :provider-create-save)
  (notification/check-for-success {:match-pred (notification/request-type? :prov-create)}))

(defn add-product
  "Adds a product to a provider, with the given name and description."
  [{:keys [provider-name name description]}]
  (navigate :provider-products-repos-page {:provider-name provider-name})
  (browser click :add-product)
  (fill-ajax-form {:product-name-text name
                  :product-description-text description}
                  :create-product)
  (notification/check-for-success {:match-pred (notification/request-type? :prod-create)}))

(defn delete-product
  "Deletes a product from the given provider."
  [{:keys [name provider-name]}]
  (navigate :named-product-page {:provider-name provider-name
                                 :product-name name})
  (browser click :remove-product)
  (browser click :confirmation-yes)
  (notification/check-for-success {:match-pred (notification/request-type? :prod-destroy)}))

(defn add-repo
  "Adds a repository under the given provider and product. Requires a
   name and url be given for the repo."
  [{:keys [provider-name product-name name url]}]
  (navigate :provider-products-repos-page {:provider-name provider-name})
  (browser click (locators/add-repository product-name))
  (fill-ajax-form {:repo-name-text name
                   :repo-url-text url}
                  :save-repository)
  (notification/check-for-success {:match-pred (notification/request-type? :repo-create)}))

(defn add-repo-with-key
  "Adds a repository under the given provider and product. Requires a
   name and url be given for the repo."
  [{:keys [provider-name product-name name url gpgkey]}]
  (navigate :provider-products-repos-page {:provider-name provider-name})
  (browser click (locators/add-repository product-name))
  (browser select :repo-gpg-select gpgkey)
  (fill-ajax-form {:repo-name-text name
                   :repo-url-text url}
                  :save-repository)
  (notification/check-for-success {:match-pred (notification/request-type? :repo-create)}))

(defn delete-repo
  "Deletes a repository from the given provider and product."
  [{:keys [name provider-name product-name]}]
  (navigate :named-repo-page {:provider-name provider-name
                              :product-name product-name
                              :repo-name name})
  (browser click :remove-repository)
  (browser click :confirmation-yes)
  (notification/check-for-success {:match-pred (notification/request-type? :repo-destroy)}))

(defn delete
  "Deletes the named custom provider."
  [name]
  (navigate :named-provider-page {:provider-name name})
  (browser click :remove-provider)
  (browser click :confirmation-yes)
  (notification/check-for-success {:match-pred (notification/request-type? :prov-destroy)}))

(defn edit
  "Edits the named custom provider. Takes an optional new name, and
  new description." [{:keys [name new-name description]}]
  (navigate :provider-details-page {:provider-name name})
  (in-place-edit {:provider-name-text new-name
                  :provider-description-text description}))
