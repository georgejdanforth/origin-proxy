(ns origin-proxy.core
  (:require [uswitch.lambada.core :refer [deflambdafn]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.set :refer [rename-keys]]
            [org.httpkit.client :as http]))

(defn url-decode [encoded-url]
  (java.net.URLDecoder/decode encoded-url))

(defn get-url [event]
  (url-decode (get-in event ["queryStringParameters" "url"])))

(defn handle [event]
  (rename-keys
    (dissoc @(http/get (get-url event)) :opts)
    {:status :statusCode}))

(deflambdafn origin-proxy.core.OriginProxy
  [in out context]
  (let [event (json/read (io/reader in))
        result (handle event)]
    (with-open [w (io/writer out)]
      (json/write result w))))
