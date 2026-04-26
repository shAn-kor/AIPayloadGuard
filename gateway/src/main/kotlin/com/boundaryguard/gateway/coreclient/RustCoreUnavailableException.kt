package com.boundaryguard.gateway.coreclient

class RustCoreUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
