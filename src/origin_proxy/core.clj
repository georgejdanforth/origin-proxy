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
     :body (if (= (get headers "Content-Type") "application/json")
             (json/write-str body)
             (str body))}))

(defn error-response [status error]
  {:statusCode status
   :body {:error error}
   :headers {"content-type" "application/json"}
   :isBase64Encoded false})

(defn filter-headers [headers]
  (apply hash-map (flatten (filter (fn [[k v]] (not (coll? v))) headers))))

(defn handle-response [response]
  (assoc
    (rename-keys
      (select-keys response [:body :status])
      {:status :statusCode})
    :isBase64Encoded false :headers (filter-headers (response :headers))))

(defn make-request [url event]
  (let [request-config (get-request-config event)]
    (case (get event "httpMethod")
      "GET" (handle-response (http/get url request-config))
      "POST" (handle-response (http/post url request-config))
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
      (make-request url event)
      (error-response 403 "Invalid host."))))

(deflambdafn origin-proxy.core.OriginProxy
  [in out context]
  (let [event (json/read (io/reader in))
        result (handle event)]
    (with-open [w (io/writer out)]
      (json/write result w))))
