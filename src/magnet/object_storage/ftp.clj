;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns magnet.object-storage.ftp
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [integrant.core :as ig]
            [magnet.object-storage.core :as core]
            [miner.ftp :as ftp])
  (:import [java.io InputStream File]
           [java.net URL]
           [java.util UUID]
           [org.apache.commons.net.ftp FTPClient]))

(s/def ::input-stream #(instance? InputStream %))
(s/def ::object-names (s/coll-of ::core/object-id :kind vector?))
(s/def ::ftp-client #(instance? FTPClient %))
(s/def ::ftp-uri (s/and string?
                        (fn [str]
                          (try
                            (URL. str)
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
(s/def ::wrap-ftp-operation-ret (s/keys :req-un [::success?]
                                        :opt-un [::error-details]))
(s/fdef wrap-ftp-operation
  :args ::wrap-ftp-operation-args
  :ret ::wrap-ftp-operation-ret)

(defn- wrap-ftp-operation
  [{:keys [ftp-uri ftp-options] :as ftp} f & args]
  {:pre [(and (s/valid? ::FTP ftp)
              (fn? f))]}
  (try
    (with-custom-ftp [client ftp-uri ftp-options]
      (apply f client args))
    (catch Exception e
      {:success? false
       :error-details (.getMessage e)})))

(s/def ::get-object*-args (s/cat :ftp-client ::ftp-client
                                 :object-id ::core/object-id
                                 :opts ::core/get-object-opts))
(s/def ::get-object*-ret (s/keys :req-un [::success?]
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
    ;;TODO: DOCUMENT WHY WE DO THIS!!
    (.delete tmp-file)
    (if result
      {:success? true
       :object input-stream}
      {:success? false})))

(s/def ::put-object*-args (s/cat :ftp-client ::ftp-client
                                 :object-id ::core/object-id
                                 :object ::core/object
                                 :opts ::core/get-object-opts))
(s/def ::put-object*-ret (s/keys :req-un [::success?]))

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
    (let [local-path (.getPath object)]
      {:success? (ftp/client-put client local-path object-id)})
    {:success? (ftp/client-put-stream client object object-id)}))

(s/def ::delete-object*-args (s/cat :ftp-client ::ftp-client
                                    :object-id ::core/object-id
                                    :opts ::core/get-object-opts))
(s/def ::delete-object*-ret (s/keys :req-un [::success?]))

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
(s/def ::rename-object*-ret (s/keys :req-un [::success?]))

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
(s/def ::list-objects*-ret (s/keys :req-un [::success? ::object-names]))

(s/fdef list-objects*
  :args ::list-objects*-args
  :ret ::list-objects*-ret)

(defn list-objects*
  [client]
  {:pre [(s/valid? ::ftp-client client)]}
  (let [result (ftp/client-file-names client)]
    {:success? (vector? result)
     :object-names result}))

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
    (let [object-adapter (update this :ftp-uri #(str % "/" parent-object-id))]
      (wrap-ftp-operation object-adapter list-objects*))))

(defmethod ig/init-key :magnet.object-storage/ftp [_ {:keys [ftp-uri ftp-options]
                                                      :or {ftp-options {}}}]
  (->FTP ftp-uri ftp-options))
