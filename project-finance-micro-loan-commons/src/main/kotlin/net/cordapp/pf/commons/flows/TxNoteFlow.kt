package net.cordapp.pf.commons.flows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.base.Splitter
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap
import java.util.*

/**
 * [TxNoteFlow] flow logic to add the notes to a committed transaction.
 * */
@InitiatingFlow
@StartableByRPC
open class TxNoteFlow(val txId: SecureHash, val txNotes: List<String>,
                      val otherParties: Collection<Party> = listOf()) : FlowLogic<Unit>() {
    constructor(txId: SecureHash, txNote: String, otherParties: Collection<Party> = listOf()) :
            this(txId, listOf(txNote), otherParties)

    constructor(txId: SecureHash, txNote: String, counterParty: Party) :
            this(txId, listOf(txNote), listOf(counterParty))

    constructor(txId: SecureHash, txNotes: List<String>, counterParty: Party) :
            this(txId, txNotes, listOf(counterParty))

    @Suspendable
    override fun call() {
        val txNotes255: MutableList<String> = ArrayList(txNotes.size)
        //Handle the string value with length more that 255.
        //Because Corda VAULT_TRANSACTION_NOTES table, NOTE column size is 255.
        txNotes.forEach {
            if (it.length > 255) {
                Splitter.fixedLength(255).split(it).iterator().forEach {
                    txNotes255.add(it)
                }
            } else {
                txNotes255.add(it)
            }
        }

        //TODO How to handle Network Time. Won't it possible to handle at client side?
        //val networkTime = getApplicationTime(serviceHub)
        //txNotes.add(networkTime.millis.toString())

        //Add note to transaction for calling node.
        txNotes255.forEach {
            serviceHub.vaultService.addNoteToTransaction(txId, it)
        }

        //Remove flow initiator identity from otherParties list.
        val counterParties = otherParties.filter { it.name != ourIdentity.name }
        //Create flow sessions to add the note for a transaction stored in counterparties vault.
        val flowSessions = mutableSetOf<FlowSession>()
        counterParties.forEach { flowSessions.add(initiateFlow(it)) }
        flowSessions.forEach { session -> session.send(Pair(txId, txNotes)) }
    }
}

/**
 * [TxNoteHandlerFlow] is add the Handler flow for [TxNoteFlow].
 * It add the note for a transaction stored in counter party vault.
 * */
@InitiatedBy(TxNoteFlow::class)
open class TxNoteHandlerFlow(val otherPartyFlow: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        otherPartyFlow.receive<Pair<SecureHash, List<String>>>().unwrap {
            val txId = it.first
            it.second.forEach {
                serviceHub.vaultService.addNoteToTransaction(txId, it.substring(0, Math.min(it.length, 254)))
            }
        }
    }
}

