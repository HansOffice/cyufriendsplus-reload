package org.cyuCBMclean.cyufriendsReload.modules.proxy

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ProxySigner(secret: String) {

    private val key = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")

    fun sign(canonical: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return Base64.getEncoder().encodeToString(mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)))
    }

    fun verify(canonical: String, signature: String): Boolean {
        val expected = sign(canonical).toByteArray(StandardCharsets.UTF_8)
        val actual = signature.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(expected, actual)
    }
}
