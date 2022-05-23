# object-storage.ftp
[![ci-cd](https://github.com/gethop-dev/object-storage.ftp/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/gethop-dev/object-storage.ftp/actions/workflows/ci-cd.yml)
[![Clojars Project](https://img.shields.io/clojars/v/dev.gethop/object-storage.ftp.svg)](https://clojars.org/dev.gethop/object-storage.ftp)

A library that provides an [Integrant](https://github.com/weavejester/integrant) key for managing objects in a FTP server.
This library is a wrapper around [miner/clj-ftp](https://github.com/miner/clj-ftp).

## Table of contents
* [Installation](#installation)
* [Usage](#usage)
  * [Configuration](#configuration)
  * [Obtaining a FTP record](#obtaining-a-ftp-record)
  * [FTP object operations](#ftp-object-operations)
* [Testing](#testing)

## Installation

[![Clojars Project](https://clojars.org/dev.gethop/object-storage.ftp/latest-version.svg)](https://clojars.org/dev.gethop/object-storage.ftp)

## Usage

### Configuration
To use this library add the following key to your configuration:

`:dev.gethop.object-storage/ftp`

This key expects a configuration map with one unique mandatory key, plus other optional one.
These are the mandatory keys:

* `:ftp-uri` : FTP server URI to connect to.

These are the optional keys:
* `:ftp-options`: a map of options for configuring the FTP client.
  * Since this library wraps around [miner/clj-ftp](https://github.com/miner/clj-ftp)
  all configuration options found there is also valid here.

Key initialization returns a `FTP` record that can be used to perform the FTP operations described below.

#### Configuration example
Basic configuration:
```edn
  :dev.gethop.object-storage/ftp
   {:ftp-uri #duct/env ["FTP_URI" Str :or "ftp://user:mypassword@my-ftp-server"]}
```

Configuration with custom FTP client configuration:
```edn
  :dev.gethop.object-storage/ftp
   {:ftp-uri #duct/env ["FTP_URI" Str :or "ftp://user:mypassword@my-ftp-server"]
    :ftp-options {:default-timeout-ms 30000
                  :security-mode :explicit
                  :local-data-connection-mode :active
                  :file-type :binary}]}
```

### Obtaining a `FTP` record

If you are using the library as part of a [Duct](https://github.com/duct-framework/duct)-based project, adding any of the previous configurations to your `config.edn` file will perform all the steps necessary to initialize the key and return a `FTP` record for the associated configuration. In order to show a few interactive usages of the library, we will do all the steps manually in the REPL.

First we require the relevant namespaces:

```clj
user> (require '[integrant.core :as ig]
               '[dev.gethop.object-storage.core :as core])
nil
user>
```

Next we create the configuration var holding the FTP integration configuration details:

```clj
user> (def config {:ftp-uri #duct/env ["FTP_URI" Str :or "ftp://user:mypassword@my-ftp-server"]})
#'user/config
user>
```

Now that we have all pieces in place, we can initialize the `:dev.gethop.object-storage/ftp` Integrant key to get a `FTP` record. As we are doing all this from the REPL, we have to manually require `dev.gethop.object-storage.ftp` namespace, where the `init-key` multimethod for that key is defined (this is not needed when Duct takes care of initializing the key as part of the application start up):

``` clj
user> (require '[dev.gethop.object-storage.ftp :as ftp])
nil
user>
```

And we finally initialize the key with the configuration defined above, to get our `FTP` record:

``` clj
user> (def ftp-record (->
                        config
                        (->> (ig/init-key :dev.gethop.object-storage/ftp))))
#'user/ftp-record
user> ftp-record
#dev.gethop.object-storage.ftp.FTP{{:ftp-uri "ftp://user:mypassword@my-ftp-server"
                                :ftp-options nil}
user>
```
Now that we have our `FTP` record, we are ready to use the methods defined by the protocol [ObjectStorage](https://github.com/gethop-dev/object-storage.core).

### FTP object operations

**(get-object ftp-record object-id)**
* description: Retrieves an object from a FTP server
* parameters:
  * `ftp-record`: a `FTP` record
  * `object-id`: the object
* return value: a map with the following keys
  * `:success?`: boolean stating if the operation was successful or not
  * `:object`: If the operation was successful, this key contains an
    InputStream-compatible stream, on the desired object. Note that
    the InputStream returned by get-object should be closed (.e.g, via
    slurp).
  * `error-details`: a map with additional details on the problem
    encountered while trying to retrieve the object.

Lets see an example. First a successful invocation:

``` clojure
user> (core/get-object ftp-record "test1.txt")
{:success? true,
 :object #object[java.io.BufferedInputStream 0x7cc725c "java.io.BufferedInputStream@7cc725c"]}
```
Invocation with a non-existing object-id:
``` clojure
user> (core/get-object ftp-record "i-dont-exist")
{:success? false}
```

**(put-object ftp-record object-id object)**
* description: uploads an object to the FTP server with `object-id` as its filename
* parameters:
  * `ftp-record`: a `FTP` record
  * `object-id`: the object identifier in the FTP server in other words the filename
  * `object`: the object we want to upload can be a file or an input stream.
* return value: a map with the following keys
  * `:success?`: boolean stating if the operation was successful or not
  * `error-details`: a map with additional details on the problem
    encountered while trying to retrieve the object.

Example:

``` clojure
user> (core/put-object ftp-record "test2.txt" (io/file "files/test2.txt"))
{:success? true}
```
Invocation with a non-existing object:
``` clojure
user> (core/put-object ftp-record "test2.txt" (io/file "files/i-dont-exist.txt"))
{:success? false,
 :error-details "files/i-dont-exist.txt (No such file or directory)"}
```

**(delete-object ftp-record object-id object)**
* description: deletes an object with `object-id` in the FTP server.
* parameters:
  * `ftp-record`: a `FTP` record
  * `object-id`: the object identifier in the FTP server in other words the filename
* return value: a map with the following keys
  * `:success?`: boolean stating if the operation was successful or not
  * `error-details`: a map with additional details on the problem
    encountered while trying to retrieve the object.

Example:
``` clojure
user> (core/delete-object ftp-record "test2.txt")
{:success? true}
```
Invocation with a non-existing object:
``` clojure
user> (core/delete-object ftp-record "i-dont-exist.txt")
{:success? false}
```

**(rename-object ftp-record object-id new-object-id)**
* description: renames an object with `object-id` to the
  `new-object-id` in the FTP server.
* parameters:
  * `ftp-record`: a `FTP` record
  * `object-id`: the object identifier in the FTP server in other words the filename
  * `new-object-id`: the new object identifier
* return value: a map with the following keys
  * `:success?`: boolean stating if the operation was successful or not
  * `error-details`: a map with additional details on the problem
    encountered while trying to retrieve the object.

Example:
``` clojure
user> (core/rename-object ftp-record "test1.txt" "new-test1.txt")
{:success? true}
```
Invocation with a non-existing object:
``` clojure
user> (core/rename-object ftp-record "i-dont-exist.txt" "new-i-dont-exist.txt")
{:success? false}
```

**(list-objects ftp-record parent-object-id)**
* description: lists all objects under the `parent-object-id`
* parameters:
  * `ftp-record`: a `FTP` record
  * `parent-object-id`: the identifier of a folder within the FTP server.
* return value: a map with the following keys
  * `:success?`: boolean stating if the operation was successful or not
  * `:objects`: A collection of maps each representing an object with the following attributes:
    - `object-id`: object's identifier
    - `last-modified`: an instant object
    - `size`: size in bytes
    - `type`: the type of the object that can be: `:file`, `:directory`, `:symbolic-link` or `:unknown`
  * `error-details`: a map with additional details on the problem
    encountered while trying to retrieve the object.

Example:
``` clojure
user> (core/list-objects ftp-record "")
{:success? true,
 :objects
 ({:object-id "/files/folder-1/file-1",
   :last-modified #inst "2019-12-05T19:06:00.000+01:00",
   :size 15,
   :type :file}
  {:object-id "/files/folder-1/folder-1-1",
   :last-modified #inst "2020-02-28T10:13:00.000+01:00",
   :size 4096,
   :type :directory}
  {:object-id "/files/folder-1/folder-1-1/file-2",
   :last-modified #inst "2020-02-28T10:13:00.000+01:00",
   :size 6,
   :type :file}
  {:object-id "/files/folder-1/folder-1-1/file-3",
   :last-modified #inst "2020-02-28T09:52:00.000+01:00",
   :size 8,
   :type :file})}
```
## Testing

The library includes self-contained units tests, including some integration tests that depend on a FTP server. Those tests have the `^:integration` metadata keyword associated to them, so you can exclude them from our unit tests runs.

If you want to run the integration tests, the following environment variable is needed:

* `TEST_OBJECT_STORAGE_FTP_URI`: The uri of the FTP server used the integration tests. Be aware that the tests will leave trash files in the server. It may corrupt or delete files, so don't execute it against a real server.

## License

Copyright (c) 2022 HOP Technologies

This Source Code Form is subject to the terms of the Mozilla Public License,
v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain
one at https://mozilla.org/MPL/2.0/
