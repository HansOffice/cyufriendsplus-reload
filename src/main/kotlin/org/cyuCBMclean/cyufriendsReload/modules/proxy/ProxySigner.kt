package org.cyuCBMclean.cyufriendsReload.modules.proxy

import java.nio.charset.StandardCharsets
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
        return sign(canonical) == signature
    }
}
