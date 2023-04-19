(ns prpr3.a-frame.interceptor-chain.data.protocols)

(defprotocol IResolveData
  (-resolve-data [spec data]))
