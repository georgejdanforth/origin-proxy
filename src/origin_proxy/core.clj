(ns origin-proxy.core
  (:require [uswitch.lambada.core :refer [deflambdafn]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.set :refer [rename-keys]]
            [clojure.string :refer [split]]
            [clj-http.client :as http]
            [environ.core :refer [env]]))

(defn url-decode [encoded-url]
  (java.net.URLDecoder/decode encoded-url))

(defn get-url [event]
  (url-decode (get-in event ["queryStringParameters" "url"])))

(defn get-request-config [event]
  (let [headers (get event "headers") body (get event "body")]
    {:throw-exceptions false
     :headers headers
     :body(if (= (get headers "Content-Type") "application/json")
             (json/write-str body)
             (str body))}))

(defn error-response [status error]
  {:statusCode status :error error})

(defn make-request [url event]
  (let [request-config (get-request-config event)]
    (case (get event "httpMethod")
      "GET" (http/get url request-config)
      "POST" (http/post url request-config)
      (error-response 405 "Method not allowed."))))

(defn is-valid-host [url]
  (let [allowed-hosts (env :allowed-hosts)]
    (if (not-empty allowed-hosts)
      (some
        identity
        (map #(re-matches % url) (map re-pattern (split allowed-hosts #","))))
      true)))

(defn handle [event]
  (let [url (get-url event)]
    (if (is-valid-host url)
      (select-keys (make-request url event) [:headers :body])))
      (error-response 403 "Invalid host."))

(deflambdafn origin-proxy.core.OriginProxy
  [in out context]
  (let [event (json/read (io/reader in))
        result (handle event)]
    (with-open [w (io/writer out)]
      (json/write result w))))
