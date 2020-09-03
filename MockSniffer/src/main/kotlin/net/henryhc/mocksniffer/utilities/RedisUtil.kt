package net.henryhc.mocksniffer.utilities

import redis.clients.jedis.Jedis
import redis.clients.jedis.ScanParams

fun Jedis.sscanSeq(key: String, pageSize: Int = 1000) = Sequence {
    iterator {
        var cursor = "0"
        while (true) {
            val pageResult = this@sscanSeq.sscan(key, cursor, ScanParams().count(pageSize))
            cursor = pageResult.cursor
            yieldAll(pageResult.result)
            if (pageResult.isCompleteIteration)
                break
        }
    }
}
