package com.pambrose.jev4k.internal

import io.ktor.http.cio.ParserException
import java.security.GeneralSecurityException

internal actual fun platformGetenv(name: String): String? = System.getenv(name)

// CIO reports most failures to connect as an IOException or an UnresolvedAddressException. Two aren't: a server
// certificate the JDK doesn't trust surfaces as a raw CertificateException from the TLS handshake, and a response
// CIO can't parse (a garbled status line, say) as a ParserException. Any GeneralSecurityException counts, so a local
// TLS setup failure is retried and reported the same way, with the cause kept. ParserException lives in
// ktor-http-cio, which ktor-client-core needs anyway, so this doesn't tie the check to CIO being on the classpath.
internal actual fun isPlatformConnectionError(cause: Throwable): Boolean =
    cause is GeneralSecurityException || cause is ParserException

internal actual fun Enum<*>.enumTypeName(): String? = declaringJavaClass.simpleName
