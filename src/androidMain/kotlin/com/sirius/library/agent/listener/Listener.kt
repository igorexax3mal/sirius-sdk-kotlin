package com.sirius.library.agent.listener

import com.sirius.library.agent.AbstractAgent
import com.sirius.library.agent.connections.AgentEvents
import com.sirius.library.agent.pairwise.AbstractPairwiseList
import com.sirius.library.agent.pairwise.Pairwise
import java.util.concurrent.CompletableFuture

actual class Listener actual constructor(actual var source: AgentEvents, actual var agent: AbstractAgent) {

    actual var pairwiseResolver: AbstractPairwiseList?
        get() = agent.pairwiseList
        set(value) {}


    val one: CompletableFuture<Event>?
        get() {
            try {
                return source.pull()?.thenApply { msg ->
                    val theirVerkey: String? = msg?.getStringFromJSON("sender_verkey")
                    var pairwise: Pairwise? = null
                    if (pairwiseResolver != null && theirVerkey != null) {
                        pairwise = pairwiseResolver?.loadForVerkey(theirVerkey)
                    }
                    Event(pairwise, msg?.serialize()?:"")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

    fun unsubscribe() {
        agent.unsubscribe(this)
    }


}