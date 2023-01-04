package net.cordapp.pf.exception

import net.corda.core.CordaRuntimeException

class StateNotFoundOnVaultException(override val message: String) : CordaRuntimeException(message)