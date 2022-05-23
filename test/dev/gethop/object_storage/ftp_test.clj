;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns dev.gethop.object-storage.ftp-test
  (:require [clojure.java.io :as io]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer :all]
            [dev.gethop.object-storage.core :as core]
            [dev.gethop.object-storage.ftp]
            [digest]
            [integrant.core :as ig])
  (:import [dev.gethop.object_storage.ftp FTP]
           [java.io File]
           [java.util UUID]))

(defn enable-instrumentation [f]
  (-> (stest/enumerate-namespace 'dev.gethop.object-storage.ftp) stest/instrument)
  (f))

(def config {:ftp-uri (System/getenv "TEST_OBJECT_STORAGE_FTP_URI")})
(def ftp-root-path (System/getenv "TEST_OBJECT_STORAGE_FTP_ROOT_PATH"))

(def test-file-1-path "test-file-1")
(def test-file-2-path "test-file-2")

(defn setup []
  (spit test-file-1-path {:hello :world})
  (spit test-file-2-path [:apples :bananas]))

(defn teardown []
  (io/delete-file test-file-1-path)
  (io/delete-file test-file-2-path))

(defn with-test-files [f]
  (setup)
  (f)
  (teardown))

(use-fixtures :once enable-instrumentation)
(use-fixtures :each with-test-files)

(defn random-object-id []
  (str "integration-test-" (UUID/randomUUID)))

(deftest record-test
  (testing "ig/init-key returns the right type of record"
    (let [ftp-record (ig/init-key :dev.gethop.object-storage/ftp config)]
      (is (instance? FTP ftp-record)))))

(deftest ftps-record-test
  (testing "ftps uri returns right type of record"
    (let [config {:ftp-uri "ftps://localhost"}
          ftp-record (ig/init-key :dev.gethop.object-storage/ftp config)]
      (is (instance? FTP ftp-record)))))

(deftest ^:integration put-get-file-test
  (let [ftp-record (ig/init-key :dev.gethop.object-storage/ftp config)
        object-id (random-object-id)
        put-result (core/put-object ftp-record object-id (io/file test-file-1-path))]
    (testing "testing put-object"
      (is (:success? put-result)))
    (testing "testing get-object"
      (let [get-result (core/get-object ftp-record object-id)]
        (is (:success? get-result))
        (is (= (digest/sha-256 (File. ^String test-file-1-path))
               (digest/sha-256 (:object get-result))))))
    (core/delete-object ftp-record object-id)))

(deftest ^:integration put-get-stream-test
  (let [ftp-record (ig/init-key :dev.gethop.object-storage/ftp config)
        object-id (random-object-id)
        bytes (.getBytes "Test message")
        stream (io/input-stream bytes)
        put-result (core/put-object ftp-record
                                    object-id
                                    stream)]
    (testing "testing put object stream"
      (is (:success? put-result)))
    (testing "testing get object stream"
      (let [get-result (core/get-object ftp-record object-id)]
        (is (:success? get-result))
        (is (= (digest/sha-256 bytes)
               (digest/sha-256 (slurp (:object get-result)))))))
    (core/delete-object ftp-record object-id)))

(deftest ^:integration delete-test
  (let [ftp-record (ig/init-key :dev.gethop.object-storage/ftp config)
        object-id (random-object-id)]
    (core/put-object ftp-record object-id (io/file test-file-1-path))
    (testing "test object is deleted succesfully"
      (let [delete-result (core/delete-object ftp-record object-id)
            get-result (core/get-object ftp-record object-id)]
        (is (:success? delete-result))
        (is (not (:success? get-result)))))))

(deftest ^:integration replace-object-test
  (let [ftp-record (ig/init-key :dev.gethop.object-storage/ftp config)
        object-id (random-object-id)
        f1 (File. ^String test-file-1-path)
        f2 (File. ^String test-file-2-path)]
    (testing "It should be possible to replace an object."
      (let [file-upload-result (core/put-object ftp-record object-id f1)
            file-2-upload-result (core/put-object ftp-record object-id f2)
            get-result (core/get-object ftp-record object-id)]
        (is (:success? file-upload-result))
        (is (:success? file-2-upload-result))
        (is (:success? get-result))
        (is (= (digest/sha-256 f2)
               (digest/sha-256 (slurp (:object get-result)))))))
    (core/delete-object ftp-record object-id)))

(deftest ^:integration rename-test
  (let [ftp-record (ig/init-key :dev.gethop.object-storage/ftp config)
        object-id (random-object-id)
        new-object-id (random-object-id)]
    (core/put-object ftp-record object-id (io/file test-file-1-path))
    (testing "Test object is renamed succesfully"
      (let [rename-result (core/rename-object ftp-record object-id new-object-id)
            get-result (core/get-object ftp-record object-id)
            new-get-result (core/get-object ftp-record new-object-id)]
        (is (:success? rename-result))
        (is (not (:success? get-result)))
        (is (:success? new-get-result))))))

(deftest ^:integration list-test
  (let [ftp-record (ig/init-key :dev.gethop.object-storage/ftp config)
        parent-id ftp-root-path
        object-id-1 (random-object-id)
        object-id-2 (str parent-id (UUID/randomUUID))
        object-id-3 (str parent-id (UUID/randomUUID))]
    (core/put-object ftp-record object-id-1 (io/file test-file-1-path))
    (core/put-object ftp-record object-id-2 (io/file test-file-2-path))
    (core/put-object ftp-record object-id-3 (io/file test-file-1-path))
    (testing "Test objects are listed succesfully"
      (let [{:keys [success? objects]} (core/list-objects ftp-record parent-id)]
        (is success?)
        (is (coll? objects))
        (is (< 2 (count objects)))
        (are [k] (some #{k} (map :object-id objects))
          object-id-2
          object-id-3)))))

(deftest ^:integration unexistent-object-test
  (let [ftp-record (ig/init-key :dev.gethop.object-storage/ftp config)
        object-id (random-object-id)]
    (testing "Test methods with an unexistent object-id"
      (are [method] (not (:success? (method ftp-record object-id)))
        #(core/rename-object %1 %2 (random-object-id))
        core/delete-object
        core/get-object))))
