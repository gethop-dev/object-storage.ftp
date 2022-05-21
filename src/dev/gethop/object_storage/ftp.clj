;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns dev.gethop.object-storage.ftp
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [integrant.core :as ig]
            [magnet.object-storage.core :as core]
            [miner.ftp :as ftp])
  (:import [java.io InputStream File]
           [java.net URI]
           [org.apache.commons.net.ftp FTPClient FTPFile]))

(s/def ::input-stream #(instance? InputStream %))
(s/def ::object-names (s/coll-of ::core/object-id :kind vector?))
(s/def ::ftp-client #(instance? FTPClient %))
(s/def ::ftp-uri (s/and string?
                        (fn [str]
                          (try
                            (URI. str)
                            (catch Exception e false)))))
(s/def ::ftp-options map?)
(s/def ::FTP (s/keys :req-un [::ftp-uri]
                     :opt-un [::ftp-options]))

(defmacro with-custom-ftp
  "Wraps `with-ftp` allowing to pass custom FTP configuration
  dynamically."
  [[client uri opts] & body]
  `(ftp/with-ftp [~client ~uri
                  :security-mode (:security-mode ~opts :explicit)
                  :data-timeout-ms (:data-timeout-ms ~opts -1)
                  :connect-timeout-ms (:connect-timeout-ms ~opts 30000)
                  :default-timeout-ms (:default-timeout-ms ~opts nil)
                  :control-keep-alive-timeout-sec (:control-keep-alive-timeout-sec ~opts 300)
                  :control-keep-alive-reply-timeout-ms (:control-keep-alive-reply-timeout-ms ~opts 1000)
                  :local-data-connection-mode (:local-data-connection-mode ~opts :passive)
                  :control-encoding (:control-encoding ~opts "UTF-8")
                  :file-type (:file-type ~opts :ascii)]
     ~@body))

(s/def ::wrap-ftp-operation-args (s/cat :ftp ::FTP :f fn? :args (s/* any?)))
(s/def ::wrap-ftp-operation-ret (s/keys :req-un [::core/success?]
                                        :opt-un [::core/error-details]))
(s/fdef wrap-ftp-operation
  :args ::wrap-ftp-operation-args
  :ret ::wrap-ftp-operation-ret)

(defn- wrap-ftp-operation
  [{:keys [ftp-uri ftp-options] :as ftp} f & args]
  {:pre [(and (s/valid? ::FTP ftp)
              (fn? f))]}
  (try
    (with-custom-ftp [client ftp-uri ftp-options]
      (when (str/starts-with? ftp-uri "ftps://")
        (ftp/encrypt-channel client))
      (apply f client args))
    (catch Exception e
      {:success? false
       :error-details (.getMessage e)})))

(s/def ::get-object*-args (s/cat :ftp-client ::ftp-client
                                 :object-id ::core/object-id
                                 :opts ::core/get-object-opts))
(s/def ::get-object*-ret (s/keys :req-un [::core/success?]
                                 :opt-un [::core/object]))
(s/fdef get-object*
  :args ::get-object*-args
  :ret ::get-object*-ret)

(defn get-object*
  [client object-id opts]
  {:pre [(and (s/valid? ::ftp-client client)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/get-object-opts opts))]}
  (let [tmp-file (File/createTempFile "ftp" nil)
        file-path (.getPath tmp-file)
        result (ftp/client-get client object-id file-path)
        input-stream (io/input-stream file-path)]
    ;; We delete the files because otherwise we would leave wandering
    ;; tempfiles in the file system. In Unix based systems if you try
    ;; to delete an opened file (in this case because we created an
    ;; input stream) it's kept until the file is closed.  Which means
    ;; the input stream can be consumed even though the file was
    ;; deleted.
    (.delete tmp-file)
    (if result
      {:success? true
       :object input-stream}
      {:success? false})))

(s/def ::put-object*-args (s/cat :ftp-client ::ftp-client
                                 :object-id ::core/object-id
                                 :object ::core/object
                                 :opts ::core/get-object-opts))
(s/def ::put-object*-ret (s/keys :req-un [::core/success?]))

(s/fdef put-object*
  :args ::put-object*-args
  :ret ::put-object*-ret)

(defn put-object*
  [client object-id object opts]
  {:pre [(and (s/valid? ::ftp-client client)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/object object)
              (s/valid? ::core/put-object-opts opts))]}
  (if (instance? File object)
    (let [local-path (.getPath ^File object)]
      {:success? (ftp/client-put client local-path object-id)})
    {:success? (ftp/client-put-stream client object object-id)}))

(s/def ::delete-object*-args (s/cat :ftp-client ::ftp-client
                                    :object-id ::core/object-id
                                    :opts ::core/get-object-opts))
(s/def ::delete-object*-ret (s/keys :req-un [::core/success?]))

(s/fdef delete-object*
  :args ::delete-object*-args
  :ret ::delete-object*-ret)

(defn delete-object*
  [client object-id opts]
  {:pre [(and (s/valid? ::ftp-client client)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/delete-object-opts opts))]}
  {:success? (ftp/client-delete client object-id)})

(s/def ::rename-object*-args (s/cat :ftp-client ::ftp-client
                                    :object-id ::core/object-id
                                    :new-object-id ::core/object-id))
(s/def ::rename-object*-ret (s/keys :req-un [::core/success?]))

(s/fdef rename-object*
  :args ::rename-object*-args
  :ret ::rename-object*-ret)

(defn rename-object*
  [client object-id new-object-id]
  {:pre [(and (s/valid? ::ftp-client client)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/object-id new-object-id))]}
  {:success? (ftp/client-rename client object-id new-object-id)})

(s/def ::list-objects*-args (s/cat :client ::ftp-client))
(s/def ::list-objects*-ret (s/keys :req-un [::core/success? ::object-names]))

(s/fdef list-objects*
  :args ::list-objects*-args
  :ret ::list-objects*-ret)

(defn- get-object-type-name [^long type-id]
  (case type-id
    0 :file
    1 :directory
    2 :symbolic-link
    3 :unknown))

(defn- with-slash [s]
  (if (str/ends-with? s "/")
    s
    (str s "/")))

(defn list-objects*
  [client]
  {:pre [(s/valid? ::ftp-client client)]}
  (let [result (ftp/client-FTPFiles-all client)
        current-path (with-slash (ftp/client-pwd client))]
    {:success? (vector? result)
     :objects (keep (fn [^FTPFile ftp-file]
                      (let [name (.getName ftp-file)]
                        (when-not (re-matches #".|.." name)
                          {:object-id (str current-path name)
                           :last-modified (.getTimestamp ftp-file)
                           :size (.getSize ftp-file)
                           :type (get-object-type-name (.getType ftp-file))})))
                    result)}))

(defn- get-partial-directory-list [client path]
  (->> (ftp/client-directory-names client)
       (remove #{"." ".."})
       (map #(str (with-slash path) %))))

(defn- path->absolute-path [client path]
  (if (str/starts-with? path "/")
    path
    (str (with-slash (ftp/client-pwd client)) path)))

(defn list-objects-recursively [client parent-object-id]
  (let [initial-path (path->absolute-path client parent-object-id)]
    (loop [object-list []
           directory-list [initial-path]]
      (if-let [path (first directory-list)]
        (if (ftp/client-cd client path)
          (let [partial-directory-list (get-partial-directory-list client path)
                partial-object-list (:objects (list-objects* client))
                new-object-list (concat object-list partial-object-list)
                new-directory-list (concat (rest directory-list) partial-directory-list)]
            (recur new-object-list new-directory-list))
          (recur object-list (rest directory-list)))
        {:success? true :objects object-list}))))

(defn list-objects
  [client parent-object-id]
  (let [path (path->absolute-path client parent-object-id)]
    (if-not (ftp/client-cd client path)
      {:success? true :objects []}
      (list-objects* client))))

(defn list-objects-with-opts
  [object-adapter parent-object-id {:keys [recursive?]
                                    :or {recursive? true}}]
  (if recursive?
    (wrap-ftp-operation object-adapter list-objects-recursively parent-object-id)
    (wrap-ftp-operation object-adapter list-objects parent-object-id)))

(defrecord FTP [ftp-uri ftp-options]
  core/ObjectStorage
  (get-object [this object-id]
    (wrap-ftp-operation this get-object* object-id {}))
  (get-object [this object-id opts]
    (wrap-ftp-operation this get-object* object-id opts))

  (put-object [this object-id object]
    (wrap-ftp-operation this put-object* object-id object {}))
  (put-object [this object-id object opts]
    (wrap-ftp-operation this put-object* object-id object opts))

  (delete-object [this object-id]
    (wrap-ftp-operation this delete-object* object-id {}))
  (delete-object [this object-id opts]
    (wrap-ftp-operation this delete-object* object-id opts))

  (rename-object [this object-id new-object-id]
    (wrap-ftp-operation this rename-object* object-id new-object-id))

  (list-objects [this parent-object-id]
    (list-objects-with-opts this parent-object-id {}))
  (list-objects [this parent-object-id opts]
    (list-objects-with-opts this parent-object-id opts)))

(defmethod ig/init-key :dev.gethop.object-storage/ftp [_ {:keys [ftp-uri ftp-options]
                                                          :or {ftp-options {}}}]
  (->FTP ftp-uri ftp-options))
