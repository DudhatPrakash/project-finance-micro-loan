package net.cordapp.pf.exception

import net.corda.core.CordaRuntimeException

class NotaryNotFoundException(override val message: String) : CordaRuntimeException(message)