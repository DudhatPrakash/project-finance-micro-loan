package net.cordapp.pf.commons.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.transactions.SignedTransaction

/**
 * Handler flow to receive the updated network time offset.
 * */
@InitiatedBy(SetNetworkTimeInitiatorFlow::class)
class SetNetworkTimeHandlerFlow(private val otherSideSession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        //Receive transaction proposal.
        val stx = subFlow(ReceiveTxSignedByInitiatingPartyFlow(otherSideSession))
        //Record the transaction proposal on finality after notary signature.
        return subFlow(ReceiveFinalityFlow(otherSideSession, stx.id))
    }
}
