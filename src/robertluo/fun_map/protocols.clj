(ns robertluo.fun-map.protocols
  "Protocols that sharing with other namespaces")

(defprotocol ValueWrapper
  "A wrapper for a value."
  (-wrapped? [this]
    "is this a wrapper?")
  (-unwrap [this m k]
    "unwrap the real value from a wrapper on the key of k"))
