package com.r3.corda.sdk.token.workflow.flows

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.location
import java.util.concurrent.ConcurrentHashMap


val attachmentCache = ConcurrentHashMap<Class<*>, SecureHash>()

fun <T : TokenType> Amount<T>.getAttachmentIdForGenericParam(): SecureHash {
    return this.token.getAttachmentIdForGenericParam()
}

fun TokenType.getAttachmentIdForGenericParam(): SecureHash {
    return attachmentCache.computeIfAbsent(this.javaClass) {
        //this is an external jar
        if (it.location != TokenType::class.java.location){
            it.location.readBytes().sha256()
        }else{
            TokenType::class.java.location.readBytes().sha256()
        }
    }
}

fun EvolvableTokenType.getAttachmentIdForGenericParam(): SecureHash {
    return attachmentCache.computeIfAbsent(this.javaClass) {
        //this is an external jar
        if (it.location != TokenType::class.java.location){
            it.location.readBytes().sha256()
        }else{
            EvolvableTokenType::class.java.location.readBytes().sha256()
        }
    }
}