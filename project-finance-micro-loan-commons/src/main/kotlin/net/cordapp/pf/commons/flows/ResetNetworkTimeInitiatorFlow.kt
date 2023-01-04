package net.cordapp.pf.commons.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration

/**
 * Flow logic to reset the network time offset to zero.
 * */
@InitiatingFlow
@StartableByRPC
class ResetNetworkTimeInitiatorFlow : FlowLogic<SecureHash>() {
    companion object {
        object QUERY_VAULT : ProgressTracker.Step("Query vault to get existing state.")
        object GET_ALL_PARTIES : ProgressTracker.Step("Get all parties from the networkMap.")
        object BUILD_TRANSACTION : ProgressTracker.Step("Build transaction for Network Offset.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying Network Offset.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction")
        object NOTIFY_TX_PROPOSAL : ProgressTracker.Step("Notify transaction proposal to counterparties")
        object FINALISING_TRANSACTION : ProgressTracker.Step("Finalising transaction") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(QUERY_VAULT, GET_ALL_PARTIES, BUILD_TRANSACTION, VERIFYING_TRANSACTION, SIGNING_TRANSACTION,
                NOTIFY_TX_PROPOSAL, FINALISING_TRANSACTION)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SecureHash {
        logger.info("Initiate ResetNetworkTimeFlow.")
        progressTracker.currentStep = QUERY_VAULT
        val networkTimeInStates = serviceHub.vaultService.queryBy(NetworkTime::class.java).states
        check(networkTimeInStates.isNotEmpty()) { "Time is already in sync with host system." }

        progressTracker.currentStep = GET_ALL_PARTIES
        //TODO Implement Business Network Membership and BNO node to manage network participants.
        val participants = serviceHub.networkMapCache.allNodes.filter {
            it.legalIdentities.first().name.organisation.contains("Controller", true)
                    || !it.legalIdentities.first().name.organisation.contains("Notary", true)
        }.map { p -> p.legalIdentities.first() }

        progressTracker.currentStep = BUILD_TRANSACTION
        val inputState = networkTimeInStates.first()
        val outputState = inputState.state.data.copy(millis = 0L, participants = participants)
        val resetNetworkOffsetCmd = Command(NetworkTimeContract.Commands.ResetNetworkOffset(), ourIdentity.owningKey)

        val txBuilder = TransactionBuilder(inputState.state.notary)
        txBuilder.addInputState(inputState)
                .addOutputState(outputState, NetworkTimeContract.NETWORK_TIME_CONTRACT_ID)
                .addCommand(resetNetworkOffsetCmd)

        progressTracker.currentStep = VERIFYING_TRANSACTION
        txBuilder.setTimeWindow(serviceHub.clock.instant(), Duration.ofSeconds(60))
        txBuilder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        progressTracker.currentStep = NOTIFY_TX_PROPOSAL
        val participantsSession = (participants - ourIdentity).asSequence().toSet().map { initiateFlow(it) }
        participantsSession.forEach {
            subFlow(SendTransactionFlow(it, signedTx))
        }

        progressTracker.currentStep = FINALISING_TRANSACTION
        val finalizedTx = subFlow(FinalityFlow(signedTx, participantsSession))

        subFlow(TxNoteFlow(finalizedTx.id, "Network millis has been reset by $ourIdentity.", participants))
        logger.info("Complete ResetNetworkTimeFlow.")
        return finalizedTx.id
    }
}