package net.henryhc.mocksniffer.utilities

import java.util.concurrent.ConcurrentHashMap

abstract class ConcurrentCachingResolver<K, out V> {
    private val cache = ConcurrentHashMap<K, V>()

    protected abstract fun resolve(key: K): V

    operator fun get(k: K) = cache.getOrPut(k) { resolve(k) }

    companion object {
        fun <K, V> of(resolver: (K) -> V) =
            object : ConcurrentCachingResolver<K, V>() {
                override fun resolve(key: K): V = resolver(key)
            }
    }
}
