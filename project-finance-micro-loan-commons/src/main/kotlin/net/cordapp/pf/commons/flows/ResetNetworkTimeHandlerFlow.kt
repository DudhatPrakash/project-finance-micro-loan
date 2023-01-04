package net.cordapp.pf.commons.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow

/**
 * Handler flow logic to reset the network time offset to zero.
 * */
@InitiatedBy(ResetNetworkTimeInitiatorFlow::class)
class ResetNetworkTimeHandlerFlow(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        //Receive transaction proposal.
        val stx = subFlow(ReceiveTxSignedByInitiatingPartyFlow(otherSideSession))

        //Record the transaction proposal on finality after notary signature.
        subFlow(ReceiveFinalityFlow(otherSideSession, stx.id))
    }
}