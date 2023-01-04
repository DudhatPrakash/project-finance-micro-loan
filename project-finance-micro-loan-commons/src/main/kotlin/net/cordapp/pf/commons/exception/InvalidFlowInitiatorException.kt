package net.cordapp.pf.commons.exception

import net.corda.core.CordaRuntimeException

class InvalidFlowInitiatorException(override val message: String) : CordaRuntimeException(message)