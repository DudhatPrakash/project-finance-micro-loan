package net.cordapp.pf.commons.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration
import java.util.*

/**
 * Flow logic to create/update the network time offset.
 * */
@InitiatingFlow
@StartableByRPC
class SetNetworkTimeInitiatorFlow(val millis: Long) : FlowLogic<SecureHash>() {
    companion object {
        object GET_PARTICIPANTS : ProgressTracker.Step("Get participants list from NetworkMap.")
        object QUERY_VAULT : ProgressTracker.Step("Query vault to get existing state.")
        object BUILD_TRANSACTION : ProgressTracker.Step("Build transaction for Network Offset.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying Network Offset.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction")
        object NOTIFY_TX_PROPOSAL : ProgressTracker.Step("Notify transaction proposal to counterparties")
        object FINALISING_TRANSACTION : ProgressTracker.Step("Finalising transaction") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(GET_PARTICIPANTS, QUERY_VAULT, BUILD_TRANSACTION,
                VERIFYING_TRANSACTION, SIGNING_TRANSACTION, NOTIFY_TX_PROPOSAL, FINALISING_TRANSACTION)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SecureHash {
        logger.info("Initiate SetNetworkTimeFlow.")
        progressTracker.currentStep = GET_PARTICIPANTS
        val participants = serviceHub.networkMapCache.allNodes.filter {
            it.legalIdentities.first().name.organisation.contains("Controller", true)
                    || !it.legalIdentities.first().name.organisation.contains("Notary", true)
        }.map { p -> p.legalIdentities.first() }

        progressTracker.currentStep = QUERY_VAULT
        val inStates = serviceHub.vaultService.queryBy(NetworkTime::class.java).states

        progressTracker.currentStep = BUILD_TRANSACTION
        val txBuilder = TransactionBuilder()
        //Calculate timeOffset between two millis values.
        val timeOffset = millis - System.currentTimeMillis()

        val outState = if (inStates.isNotEmpty()) { //If existing state present then create new one.
            txBuilder.notary = inStates.first().state.notary
            val inState = inStates.first()
            txBuilder.withItems(inState)
            inState.state.data.copy(millis = timeOffset, participants = participants)
        } else { //Update the offset in existing NetworkTime state.
            txBuilder.notary = serviceHub.networkMapCache.notaryIdentities.first()
            NetworkTime(timeOffset, participants = participants)
        }
        val createNetworkOffsetCmd = Command(NetworkTimeContract.Commands.CreateNetworkOffset(), ourIdentity.owningKey)
        txBuilder.withItems(StateAndContract(outState, NetworkTimeContract.NETWORK_TIME_CONTRACT_ID))
        txBuilder.withItems(createNetworkOffsetCmd)

        progressTracker.currentStep = VERIFYING_TRANSACTION
        txBuilder.setTimeWindow(serviceHub.clock.instant(), Duration.ofMinutes(10))
        txBuilder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        progressTracker.currentStep = NOTIFY_TX_PROPOSAL
        val participantsSession = (participants - ourIdentity).toSet().map { initiateFlow(it) }
        participantsSession.forEach {
            subFlow(SendTransactionFlow(it, signedTx))
        }

        progressTracker.currentStep = FINALISING_TRANSACTION
        val finalizedTx = subFlow(FinalityFlow(signedTx, participantsSession, FINALISING_TRANSACTION.childProgressTracker()))

        subFlow(TxNoteFlow(finalizedTx.id, "Network Time has been updated to ${Date(millis)}.", participants))
        logger.info("Complete SetNetworkTimeFlow.")
        return finalizedTx.id
    }
}
