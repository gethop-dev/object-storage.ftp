# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## 0.1.3 - 2020-03-02

### Changed
- Changed list-objects method to accept optional configuration
  - It accepts `recursive?` option to either list objects recursively or not.
  - The default behaviour is recursive.

### Breaking Change
- list-objects method return values have changed
  - Now by default it is recursive
  - Instead of returning a vector with object-ids, now it returns a collection of maps
  with keys: `:object-id`, `:last-modified`,`:size` and `:type`. Check the README for examples.

## 0.1.2 - 2020-01-21

### Changed
- Fix ftp-uri spec throwing an exception when ftps URI was provided

## 0.1.1 - 2019-12-05

### Changed
- Fix wrong spec keys ::success? and ::error-details
- Improve and correct comment on why we delete tempfiles when getting an object input stream
- Fix several typos in the README

## 0.1.0 - 2019-12-05
- Initial commit
