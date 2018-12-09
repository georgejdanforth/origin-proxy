(ns origin-proxy.core
  (:require [uswitch.lambada.core :refer [deflambdafn]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.set :refer [rename-keys]]
            [clj-http.client :as http]))

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

(defn make-request [event]
  (let [url (get-url event) request-config (get-request-config event)]
    (case (get event "httpMethod")
      "GET" (http/get url request-config)
      "POST" (http/post url request-config)
      (error-response 405 "Method not allowed."))))

(defn handle [event]
  (select-keys (make-request event) [:headers :body]))

(deflambdafn origin-proxy.core.OriginProxy
  [in out context]
  (let [event (json/read (io/reader in))
        result (handle event)]
    (with-open [w (io/writer out)]
      (json/write result w))))
