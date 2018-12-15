(ns origin-proxy.core
  (:require [uswitch.lambada.core :refer [deflambdafn]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.set :refer [rename-keys]]
            [clojure.string :refer [split]]
            [clj-http.client :as http]
            [environ.core :refer [env]]))

(defn get-env-list [variable]
  (if-let [value (env variable)] (split value #",") '()))

(defn filter-request-headers [headers]
  (apply dissoc headers (get-env-list :unwanted-request-headers)))

(defn filter-response-headers [headers]
  (let [unwanted-response-headers (get-env-list :unwanted-response-headers)]
    (into {} (filter (fn [[k v]]
                       (not (or
                              (some #(= % v) unwanted-response-headers)
                              (coll? v))))
                     headers))))

(defn url-decode [encoded-url]
  (if encoded-url (java.net.URLDecoder/decode encoded-url) nil))

(defn get-url [event]
  (url-decode (get-in event ["queryStringParameters" "url"])))

(defn get-request-config [event]
  (let [headers (get event "headers") body (get event "body")]
    {:throw-exceptions false
     :headers (filter-request-headers headers)
     :body (if (= (get headers "Content-Type") "application/json")
             (json/write-str body)
             (str body))}))

(defn error-response [status error]
  {:statusCode status
   :body {:error error}
   :headers {"content-type" "application/json"}
   :isBase64Encoded false})

(defn handle-response [response]
  (assoc
    (rename-keys
      (select-keys response [:body :status])
      {:status :statusCode})
    :isBase64Encoded false
    :headers (filter-response-headers (response :headers))))

(defn make-request [url event]
  (let [request-config (get-request-config event)]
    (case (get event "httpMethod")
      "GET" (handle-response (http/get url request-config))
      "POST" (handle-response (http/post url request-config))
      (error-response 405 "Method not allowed."))))

(defn is-valid-host [url]
  (let [allowed-hosts (map re-pattern (get-env-list :allowed-hosts))]
    (if (empty? allowed-hosts)
      true
      (some some? (map #(re-find % url) allowed-hosts)))))

(defn handle [event]
  (if-let [url (get-url event)]
    (if (is-valid-host url)
      (make-request url event)
      (error-response 403 "Invalid host."))
    (error-response 403 "No host supplied.")))

(deflambdafn origin-proxy.core.OriginProxy
  [in out context]
  (let [event (json/read (io/reader in))
        result (handle event)]
    (with-open [w (io/writer out)]
      (json/write result w))))
