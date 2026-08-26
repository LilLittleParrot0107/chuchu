package com.jossephus.chuchu.service.ssh

fun interface HostKeyPolicy {
    // suspend de prompt host-key co the treo cho user tra loi bang await()
    // thay vi runBlocking chiem chet thread cua engine dispatcher.
    suspend fun verify(host: String, port: Int, algorithm: String, keyBytes: ByteArray): Boolean
}
