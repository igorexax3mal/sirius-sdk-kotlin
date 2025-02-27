package com.sirius.library.agent.coprotocols


import com.sirius.library.agent.MobileAgent
import com.sirius.library.agent.listener.Event
import com.sirius.library.agent.listener.Listener
import com.sirius.library.agent.pairwise.Pairwise
import com.sirius.library.messaging.Message
import java.util.concurrent.TimeUnit

class PairwiseMobileCoProtocolTransport(agent: MobileAgent, pw: Pairwise) :
    AbstractCoProtocolTransport() {
    var agent: MobileAgent
    var pw: Pairwise
    var listener: Listener
    override fun start() {}
    override fun stop() {
        listener.unsubscribe()
    }

    override fun sendAndWait(message: Message): Pair<Boolean, Message?> {
        send(message)
        val r: GetOneResult? = one
        if (r != null) {
            if (r.senderVerkey.equals(pw.their.verkey)) {
                return Pair(true, r.message)
            }
        }
        return Pair(false, null)
    }



    override val one: GetOneResult?
        get() {
            try {
                val event: Event? = listener.one?.get(timeToLiveSec.toLong(), TimeUnit.SECONDS)
                if(event!=null){
                    if(event.message()!=null && event.senderVerkey!=null && event.recipientVerkey!=null){
                        return GetOneResult(event.message()!!, event.senderVerkey!!, event.recipientVerkey!!)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

    override fun send(message: Message) {
        agent.sendTo(message, pw)
    }

    override fun sendMany(message: Message, to: List<Pairwise>): List<Pair<Boolean, String?>> {
        return listOf()
    }


    init {
        this.agent = agent
        this.pw = pw
        listener = this.agent.subscribe()
    }
}