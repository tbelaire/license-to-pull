(ns license-to-pull.test.utils
  (:use [license-to-pull.utils]
        [clojure.test]))

(deftest str->int-test []
  (is (= 5 (str->int "5")))
  (is (= nil (str->int "asdfasdf"))))

(deftest base-64-decode-test []
  (is (= "Hello" (base-64-decode "SGVsbG8="))))
