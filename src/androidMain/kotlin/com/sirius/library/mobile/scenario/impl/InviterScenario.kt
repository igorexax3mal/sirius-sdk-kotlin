package com.sirius.library.mobile.scenario.impl


import com.sirius.library.agent.aries_rfc.feature_0160_connection_protocol.messages.ConnRequest
import com.sirius.library.agent.aries_rfc.feature_0160_connection_protocol.messages.Invitation
import com.sirius.library.agent.aries_rfc.feature_0160_connection_protocol.state_machines.Inviter
import com.sirius.library.agent.connections.Endpoint
import com.sirius.library.agent.listener.Event
import com.sirius.library.agent.pairwise.Pairwise
import com.sirius.library.messaging.Message
import com.sirius.library.mobile.SiriusSDK
import com.sirius.library.mobile.scenario.BaseScenario

abstract class InviterScenario() : BaseScenario() {

    var connectionKey : String? =null


    open fun generateInvitation()  : String{
        val verkey = SiriusSDK.getInstance().context.crypto.createKey()
        connectionKey =  verkey
        val myEndpoint : Endpoint = SiriusSDK.getInstance().context.endpointWithEmptyRoutingKeys
            ?: return ""
        val invitation = Invitation.builder()
            .setLabel(SiriusSDK.getInstance().label)
            .setRecipientKeys(listOfNotNull(verkey)).setEndpoint(myEndpoint.address).build()
        val qrContent = SiriusSDK.getInstance().context.currentHub.serverUri + invitation.invitationUrl()
        return qrContent
    }

    override fun initMessages(): List<Class<out Message>> {
       return listOf(ConnRequest::class.java)
    }



    override fun start(event: Event) : Pair<Boolean,String?> {
        val request = event.message() as ConnRequest
        val didVerkey = SiriusSDK.getInstance().context.did.createAndStoreMyDid()
        var did = didVerkey.first
        var verkey = didVerkey.second
        val inviterMe = Pairwise.Me(did, verkey)
        val machine = Inviter(SiriusSDK.getInstance().context, inviterMe, connectionKey?:"", SiriusSDK.getInstance().context.endpointWithEmptyRoutingKeys?: Endpoint(""))
        val pairwise : Pairwise? = machine.createConnection(request)
        pairwise?.let {
            SiriusSDK.getInstance().context.pairwiseList.ensureExists(it)
        }
        return Pair(pairwise!=null,null)
    }


}