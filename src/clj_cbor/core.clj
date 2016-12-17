(ns clj-cbor.core
  (:require
    [clj-cbor.codec :as codec]
    [clj-cbor.data.model :as data]
    (clj-cbor.tags
      [numbers :as numbers]
      [time :as time]
      [text :as text])
    [clojure.java.io :as io])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream
      DataInputStream
      DataOutputStream
      EOFException
      OutputStream)))


(defn cbor-codec
  "Constructs a new CBOR codec.

  Valid options are:

  - `:formatter-dispatch` function which is called to provide a dispatch value
    based on the data to be rendered. (default: `class`)
  - `:formatters` map of dispatch values to _formatting functions_ which take
    some data to be encoded and return a transformed version of it (typically
    tagged values).
  - `:tag-handlers` map of integer tags to _handler functions_ which take the
    embedded item and return the transformed data value."
  [& {:as opts}]
  (codec/map->CBORCodec
    (merge
      {:formatter-dispatch class
       :formatters (merge numbers/bignum-formatters
                          time/time-epoch-formatters)
       :tag-handlers (merge numbers/bignum-handlers
                            time/instant-handlers)}
      opts)))


(def default-codec
  "Default CBOR codec to use when none is specified."
  (cbor-codec))


(defn encode
  "Encodes a value as CBOR data. In the general form, this writes a value to an
  output stream and returns the number of bytes written. If omitted, the
  function returns a byte array instead. Uses the `default-codec` if none is
  provided."
  ([value]
   (encode default-codec value))
  ([encoder value]
   (let [buffer (ByteArrayOutputStream.)]
     (encode encoder buffer value)
     (.toByteArray buffer)))
  ([encoder ^OutputStream output value]
   (let [data-output (DataOutputStream. output)]
     (codec/write-value encoder data-output value))))


(defn decode
  "Decodes a sequence of CBOR values from the given input data, which may be a
  byte array or coercible to `input-stream`. Uses the `default-codec` if none
  is provided.

  This returns a lazy sequence, so take care that the input stream is not
  closed before the entries are realized."
  ([input]
   (decode default-codec input))
  ([decoder input]
   (let [eof-guard (Object.)
         data-input (DataInputStream.
                      (if (data/bytes? input)
                        (java.io.ByteArrayInputStream. ^bytes input)
                        (io/input-stream input)))]
     (->>
       (repeatedly
         #(try
            (codec/read-value decoder data-input)
            (catch EOFException ex
              eof-guard)))
       (take-while #(not (identical? eof-guard %)))))))
