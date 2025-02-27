/*
package com.sirius.library

import com.sirius.library.agent.CloudAgent
import com.sirius.library.agent.consensus.simple.messages.*
import com.sirius.library.agent.consensus.simple.state_machines.MicroLedgerSimpleConsensus
import com.sirius.library.agent.listener.Event
import com.sirius.library.agent.listener.Listener
import com.sirius.library.agent.microledgers.AbstractMicroledger
import com.sirius.library.agent.microledgers.Transaction
import com.sirius.library.agent.pairwise.Pairwise
import com.sirius.library.encryption.P2PConnection
import com.sirius.library.errors.sirius_exceptions.SiriusContextError
import com.sirius.library.errors.sirius_exceptions.SiriusValidationError
import com.sirius.library.helpers.ConfTest
import com.sirius.library.helpers.ServerTestSuite
import com.sirius.library.hub.CloudContext
import com.sirius.library.models.AgentParams
import com.sirius.library.utils.JSONObject
import kotlin.test.*

class TestSimpleConsensus {
    lateinit var confTest: ConfTest
    @BeforeTest
    fun configureTest() {
        confTest = ConfTest.newInstance()
    }

    @Test
    @Throws(SiriusContextError::class, SiriusValidationError::class)
    fun testInitLedgerMessaging() {
        val agentA: CloudAgent = confTest.getAgent("agent1")
        val agentB: CloudAgent = confTest.getAgent("agent2")
        val ledgerName: String = confTest.ledgerName()
        agentA.open()
        agentB.open()
        try {
            val a2b: Pairwise = confTest.getPairwise(agentA, agentB)
            val b2a: Pairwise = confTest.getPairwise(agentB, agentA)
            a2b.me.did = "did:peer:" + a2b.me.did
            b2a.me.did = "did:peer:" + b2a.me.did
            val genesisTxns: MutableList<Transaction> = ArrayList<Transaction>()
            genesisTxns.add(
                Transaction(
                    JSONObject().put("reqId", 1)
                        .put("identifier", "5rArie7XKukPCaEwq5XGQJnM9Fc5aZE3M9HAPVfMU2xC").put("op", "op1")
                )
            )
            val request: InitRequestLedgerMessage = InitRequestLedgerMessage.builder()
                .setParticipants(listOfNotNull(a2b.me.did, b2a.me.did))
                .setLedgerName(ledgerName).setGenesis(genesisTxns).setRootHash("xxx").build()
            request.addSignature(agentA.getWalleti().crypto, a2b.me)
            request.addSignature(agentB.getWalleti().crypto, b2a.me)
            assertEquals(2, request.signatures().length())
            request.checkSignatures(agentA.getWalleti().crypto, a2b.me.did)
            request.checkSignatures(agentA.getWalleti().crypto, b2a.me.did)
            request.checkSignatures(agentA.getWalleti().crypto)
            request.checkSignatures(agentB.getWalleti().crypto, a2b.me.did)
            request.checkSignatures(agentB.getWalleti().crypto, b2a.me.did)
            request.checkSignatures(agentB.getWalleti().crypto)
            val response: InitResponseLedgerMessage = InitResponseLedgerMessage.builder().build()
            response.assignFrom(request)
            val payload1: JSONObject = request.getMessageObjec()
            val payload2: JSONObject = response.getMessageObjec()
            assertFalse(payload1.similar(payload2))
            payload1.remove("@id")
            payload1.remove("@type")
            payload2.remove("@id")
            payload2.remove("@type")
            assertTrue(payload1.similar(payload2))
        } finally {
            agentA.close()
            agentB.close()
        }
    }

    @Test
    @Throws(SiriusValidationError::class)
    fun testTransactionMessaging() {
        val agentA: CloudAgent = confTest.getAgent("agent1")
        val agentB: CloudAgent = confTest.getAgent("agent2")
        val ledgerName: String = confTest.ledgerName()
        agentA.open()
        agentB.open()
        try {
            val a2b: Pairwise = confTest.getPairwise(agentA, agentB)
            val b2a: Pairwise = confTest.getPairwise(agentB, agentA)
            a2b.me.did = "did:peer:" + a2b.me.did
            b2a.me.did = "did:peer:" + b2a.me.did
            val genesisTxns: MutableList<Transaction> = ArrayList<Transaction>()
            genesisTxns.add(
                Transaction(
                    JSONObject().put("reqId", 1)
                        .put("identifier", "5rArie7XKukPCaEwq5XGQJnM9Fc5aZE3M9HAPVfMU2xC").put("op", "op1")
                )
            )
            val (ledgerForA) = agentA.getMicroledgersi().create(ledgerName, genesisTxns)
            val (ledgerForB) = agentB.getMicroledgersi().create(ledgerName, genesisTxns)
            val newTransactions: List<Transaction> = listOf(
                Transaction(
                    JSONObject().put("reqId", 2)
                        .put("identifier", "5rArie7XKukPCaEwq5XGQJnM9Fc5aZE3M9HAPVfMU2xC").put("op", "op2")
                ),
                Transaction(
                    JSONObject().put("reqId", 3)
                        .put("identifier", "5rArie7XKukPCaEwq5XGQJnM9Fc5aZE3M9HAPVfMU2xC").put("op", "op3")
                )
            )
            val (_, _, newTxns) = ledgerForA.append(newTransactions)

            // A->B
            val stateA = MicroLedgerState(ConfTest.getState(ledgerForA))
            val x: MicroLedgerState = MicroLedgerState.fromLedger(ledgerForA)
            assertTrue(stateA.similar(x))
            assertEquals(stateA.getHash(), x.getHash())
            val propose: ProposeTransactionsMessage =
                ProposeTransactionsMessage.builder().setTransactions(newTxns).setState(stateA).build()
            propose.validate()

            // B -> A
            ledgerForB.append(propose.transactions())
            val preCommit: PreCommitTransactionsMessage =
                PreCommitTransactionsMessage.builder().setState(MicroLedgerState(ConfTest.getState(ledgerForA))).build()
            preCommit.signState(agentB.getWalleti().crypto, b2a.me)
            preCommit.validate()
            val (first, second) = preCommit.verifyState(agentA.getWalleti().crypto, a2b.their.verkey)
            assertTrue(first)
            assertEquals(second, stateA.hash)

            // A -> B
            val commit: CommitTransactionsMessage = CommitTransactionsMessage.builder().build()
            commit.addPreCommit(a2b.their.did, preCommit)
            commit.validate()
            val states: JSONObject = commit.verifyPreCommits(agentA.getWalleti().crypto, stateA)
            assertTrue(states.toString().contains(a2b.their.did))
            assertTrue(states.toString().contains(a2b.their.verkey))

            // B -> A (post commit)
            val postCommit: PostCommitTransactionsMessage = PostCommitTransactionsMessage.builder().build()
            postCommit.addCommitSign(agentB.getWalleti().crypto, commit, b2a.me)
            postCommit.validate()
            assertTrue(
                postCommit.verifyCommits(
                    agentA.getWalleti().crypto,
                    commit,
                    listOf(a2b.their.verkey)
                )
            )
        } finally {
            agentA.close()
            agentB.close()
        }
    }

    private fun routineOfLedgerCreator(
        uri: String, credentials: ByteArray, p2p: P2PConnection, me: Pairwise.Me,
        participants: List<String>, ledgerName: String, genesis: List<Transaction>
    ): java.util.function.Function<java.lang.Void, Pair<Boolean, AbstractMicroledger>> {
        return label@ java.util.function.Function<java.lang.Void, Pair<Boolean, AbstractMicroledger>> { unused: java.lang.Void? ->
            CloudContext.builder().setServerUri(uri).setCredentials(credentials).setP2p(p2p).build().use { c ->
                val machine = MicroLedgerSimpleConsensus(c, me)
                return@label machine.initMicroledger(ledgerName, participants, genesis)
            }
        }
    }

    private fun routineOfLedgerCreationAcceptor(
        uri: String,
        credentials: ByteArray,
        p2p: P2PConnection
    ): java.util.function.Function<java.lang.Void, Pair<Boolean, AbstractMicroledger>> {
        return label@ java.util.function.Function<java.lang.Void, Pair<Boolean, AbstractMicroledger>> { unused: java.lang.Void? ->
            try {
                CloudContext.builder().setServerUri(uri).setCredentials(credentials).setP2p(p2p).build().use { c ->
                    val listener: Listener = c.subscribe()
                    val event: Event = listener.getOne().get(30, java.util.concurrent.TimeUnit.SECONDS)
                    val propose: Message = event.message()
                    assertTrue(propose is InitRequestLedgerMessage)
                    val machine = MicroLedgerSimpleConsensus(c, event.getPairwisei().getMe())
                    return@label machine.acceptMicroledger(event.getPairwisei(), propose as InitRequestLedgerMessage)
                }
            } catch (e: java.lang.InterruptedException) {
                e.printStackTrace()
            } catch (e: java.util.concurrent.ExecutionException) {
                e.printStackTrace()
            } catch (e: java.util.concurrent.TimeoutException) {
                e.printStackTrace()
            }
            null
        }
    }

    @Test
    fun testSimpleConsensusInitLedger() {
        val agentA: CloudAgent = confTest.getAgent("agent1")
        val agentB: CloudAgent = confTest.getAgent("agent2")
        val agentC: CloudAgent = confTest.getAgent("agent3")
        val ledgerName: String = confTest.ledgerName()
        val testSuite = confTest.suiteSingleton
        val aParams: AgentParams = testSuite.getAgentParams("agent1")
        val bParams: AgentParams = testSuite.getAgentParams("agent2")
        val cParams: AgentParams = testSuite.getAgentParams("agent3")
        agentA.open()
        agentB.open()
        agentC.open()
        try {
            val a2b: Pairwise = confTest.getPairwise(agentA, agentB)
            val a2c: Pairwise = confTest.getPairwise(agentA, agentC)
            assertEquals(a2b.me, a2c.me)
            val b2a: Pairwise = confTest.getPairwise(agentB, agentA)
            val b2c: Pairwise = confTest.getPairwise(agentB, agentC)
            assertEquals(b2a.me, b2c.me)
            val c2a: Pairwise = confTest.getPairwise(agentC, agentA)
            val c2b: Pairwise = confTest.getPairwise(agentC, agentB)
            assertEquals(c2a.me, c2b.me)
            val participants: List<String> =
               listOfNotNull(a2b.me.did, a2b.their.did, a2c.their.did)
            val genesis: List<Transaction> = listOf(
                Transaction(
                    JSONObject().put("reqId", 1)
                        .put("identifier", "5rArie7XKukPCaEwq5XGQJnM9Fc5aZE3M9HAPVfMU2xC").put("op", "op1")
                ),
                Transaction(
                    JSONObject().put("reqId", 2)
                        .put("identifier", "2btLJAAb1S3x6hZYdVyAePjqtQYi2ZBSRGy4569RZu8h").put("op", "op2")
                )
            )
            val creatorRoutine: java.util.function.Function<java.lang.Void, Pair<Boolean, AbstractMicroledger>> =
                routineOfLedgerCreator(
                    aParams.serverAddress,
                    aParams.credentials.encodeToByteArray(),
                    aParams.getConnection(),
                    a2b.me,
                    participants,
                    ledgerName,
                    genesis
                )
            val acceptorRoutine1: java.util.function.Function<java.lang.Void, Pair<Boolean, AbstractMicroledger>> =
                routineOfLedgerCreationAcceptor(
                    bParams.serverAddress,
                    bParams.credentials.encodeToByteArray(), bParams.getConnection()
                )
            val acceptorRoutine2: java.util.function.Function<java.lang.Void, Pair<Boolean, AbstractMicroledger>> =
                routineOfLedgerCreationAcceptor(
                    cParams.serverAddress,
                    cParams.credentials.encodeToByteArray(), cParams.getConnection()
                )
            val stamp1: Long = java.lang.System.currentTimeMillis()
            println("> begin")
            val cf1: java.util.concurrent.CompletableFuture<Pair<Boolean, AbstractMicroledger>> =
                java.util.concurrent.CompletableFuture.supplyAsync<Pair<Boolean, AbstractMicroledger>>(
                    java.util.function.Supplier<Pair<Boolean, AbstractMicroledger>> { creatorRoutine.apply(null) },
                    java.util.concurrent.Executor { r: java.lang.Runnable? -> java.lang.Thread(r).start() })
            val cf2: java.util.concurrent.CompletableFuture<Pair<Boolean, AbstractMicroledger>> =
                java.util.concurrent.CompletableFuture.supplyAsync<Pair<Boolean, AbstractMicroledger>>(
                    java.util.function.Supplier<Pair<Boolean, AbstractMicroledger>> { acceptorRoutine1.apply(null) },
                    java.util.concurrent.Executor { r: java.lang.Runnable? -> java.lang.Thread(r).start() })
            val cf3: java.util.concurrent.CompletableFuture<Pair<Boolean, AbstractMicroledger>> =
                java.util.concurrent.CompletableFuture.supplyAsync<Pair<Boolean, AbstractMicroledger>>(
                    java.util.function.Supplier<Pair<Boolean, AbstractMicroledger>> { acceptorRoutine2.apply(null) },
                    java.util.concurrent.Executor { r: java.lang.Runnable? -> java.lang.Thread(r).start() })
            cf1.get(30, java.util.concurrent.TimeUnit.SECONDS)
            cf2.get(30, java.util.concurrent.TimeUnit.SECONDS)
            cf3.get(30, java.util.concurrent.TimeUnit.SECONDS)
            println("> end")
            val stamp2: Long = java.lang.System.currentTimeMillis()
            println("***** Consensus timeout: " + (stamp2 - stamp1) / 1000 + " sec")
            assertTrue(agentA.getMicroledgersi().isExists(ledgerName))
            assertTrue(agentB.getMicroledgersi().isExists(ledgerName))
            assertTrue(agentC.getMicroledgersi().isExists(ledgerName))
            for (agent in listOf(agentA, agentB, agentC)) {
                val ledger: AbstractMicroledger = agent.getMicroledgersi().getLedger(ledgerName)
                val txns: List<Transaction> = ledger.allTransactions
                assertEquals(2, txns.size.toLong())
            }
        } finally {
            agentA.close()
            agentB.close()
            agentC.close()
        }
    }

    private fun routineOfTxnCommitter(
        uri: String, credentials: ByteArray, p2p: P2PConnection,
        me: Pairwise.Me, participants: List<String>,
        ledger: AbstractMicroledger, txns: List<Transaction>
    ): java.util.function.Function<java.lang.Void, Pair<Boolean, List<Transaction>>> {
        return label@ java.util.function.Function<java.lang.Void, Pair<Boolean, List<Transaction>>> { unused: java.lang.Void? ->
            CloudContext.builder().setServerUri(uri).setCredentials(credentials).setP2p(p2p).build().use { c ->
                val machine = MicroLedgerSimpleConsensus(c, me)
                return@label machine.commit(ledger, participants, txns)
            }
        }
    }

    private fun routineOfTxnAcceptor(
        uri: String,
        credentials: ByteArray,
        p2p: P2PConnection
    ): java.util.function.Function<java.lang.Void, Boolean> {
        return label@ java.util.function.Function<java.lang.Void, Boolean> { unused: java.lang.Void? ->
            try {
                CloudContext.builder().setServerUri(uri).setCredentials(credentials).setP2p(p2p).build().use { c ->
                    val listener: Listener = c.subscribe()
                    val event: Event = listener.getOne().get(30, java.util.concurrent.TimeUnit.SECONDS)
                    assertNotNull(event.getPairwisei())
                    if (event.message() is ProposeTransactionsMessage) {
                        val machine =
                            MicroLedgerSimpleConsensus(c, event.getPairwisei().getMe())
                        return@label machine.acceptCommit(
                            event.getPairwisei(),
                            event.message() as ProposeTransactionsMessage
                        )
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                fail()
            }
            false
        }
    }

    @Test
    fun testSimpleConsensusCommit() {
        val agentA: CloudAgent = confTest.getAgent("agent1")
        val agentB: CloudAgent = confTest.getAgent("agent2")
        val agentC: CloudAgent = confTest.getAgent("agent3")
        val ledgerName: String = confTest.ledgerName()
        val testSuite: ServerTestSuite = confTest.suiteSingleton
        val aParams: AgentParams = testSuite.getAgentParams("agent1")
        val bParams: AgentParams = testSuite.getAgentParams("agent2")
        val cParams: AgentParams = testSuite.getAgentParams("agent3")
        agentA.open()
        agentB.open()
        agentC.open()
        try {
            val a2b: Pairwise = confTest.getPairwise(agentA, agentB)
            val a2c: Pairwise = confTest.getPairwise(agentA, agentC)
            assertEquals(a2b.me, a2c.me)
            val b2a: Pairwise = confTest.getPairwise(agentB, agentA)
            val b2c: Pairwise = confTest.getPairwise(agentB, agentC)
            assertEquals(b2a.me, b2c.me)
            val c2a: Pairwise = confTest.getPairwise(agentC, agentA)
            val c2b: Pairwise = confTest.getPairwise(agentC, agentB)
            assertEquals(c2a.me, c2b.me)
            val participants: List<String> =
                listOfNotNull(a2b.me.did, a2b.their.did, a2c.their.did)
            val genesis: List<Transaction> = listOf(
                Transaction(
                    JSONObject().put("reqId", 1)
                        .put("identifier", "5rArie7XKukPCaEwq5XGQJnM9Fc5aZE3M9HAPVfMU2xC").put("op", "op1")
                ),
                Transaction(
                    JSONObject().put("reqId", 2)
                        .put("identifier", "2btLJAAb1S3x6hZYdVyAePjqtQYi2ZBSRGy4569RZu8h").put("op", "op2")
                )
            )
            val t1: Pair<AbstractMicroledger, List<Transaction>> = agentA.getMicroledgersi().create(ledgerName, genesis)
            var ledgerForA: AbstractMicroledger = t1.first
            agentB.getMicroledgersi().create(ledgerName, genesis)
            agentC.getMicroledgersi().create(ledgerName, genesis)
            val txns: List<Transaction> = listOf(
                Transaction(
                    JSONObject().put("reqId", 3)
                        .put("identifier", "5rArie7XKukPCaEwq5XGQJnM9Fc5aZE3M9HAPVfMU2xC").put("op", "op3")
                ),
                Transaction(
                    JSONObject().put("reqId", 4)
                        .put("identifier", "2btLJAAb1S3x6hZYdVyAePjqtQYi2ZBSRGy4569RZu8h").put("op", "op4")
                ),
                Transaction(
                    JSONObject().put("reqId", 5)
                        .put("identifier", "2btLJAAb1S3x6hZYdVyAePjqtQYi2ZBSRGy4569RZu8h").put("op", "op5")
                )
            )
            val committer: java.util.function.Function<java.lang.Void, Pair<Boolean, List<Transaction>>> =
                routineOfTxnCommitter(
                    aParams.serverAddress,
                    aParams.credentials.encodeToByteArray(),
                    aParams.getConnection(),
                    a2b.me,
                    participants,
                    ledgerForA,
                    txns
                )
            val acceptor1: java.util.function.Function<java.lang.Void, Boolean> = routineOfTxnAcceptor(
                bParams.serverAddress,
                bParams.credentials.encodeToByteArray(), bParams.getConnection()
            )
            val acceptor2: java.util.function.Function<java.lang.Void, Boolean> = routineOfTxnAcceptor(
                cParams.serverAddress,
                cParams.credentials.encodeToByteArray(), cParams.getConnection()
            )
            val stamp1: Long = java.lang.System.currentTimeMillis()
            println("> begin")
            val cf1: java.util.concurrent.CompletableFuture<Pair<Boolean, List<Transaction>>> =
                java.util.concurrent.CompletableFuture.supplyAsync<Pair<Boolean, List<Transaction>>>(
                    java.util.function.Supplier<Pair<Boolean, List<Transaction>>> {
                        committer.apply(
                            null
                        )
                    },
                    java.util.concurrent.Executor { r: java.lang.Runnable? -> java.lang.Thread(r).start() })
            val cf2: java.util.concurrent.CompletableFuture<Boolean> =
                java.util.concurrent.CompletableFuture.supplyAsync<Boolean>(
                    java.util.function.Supplier<Boolean> { acceptor1.apply(null) },
                    java.util.concurrent.Executor { r: java.lang.Runnable? -> java.lang.Thread(r).start() })
            val cf3: java.util.concurrent.CompletableFuture<Boolean> =
                java.util.concurrent.CompletableFuture.supplyAsync<Boolean>(
                    java.util.function.Supplier<Boolean> { acceptor2.apply(null) },
                    java.util.concurrent.Executor { r: java.lang.Runnable? -> java.lang.Thread(r).start() })
            cf1.get(30, java.util.concurrent.TimeUnit.SECONDS)
            cf2.get(30, java.util.concurrent.TimeUnit.SECONDS)
            cf3.get(30, java.util.concurrent.TimeUnit.SECONDS)
            println("> end")
            val stamp2: Long = java.lang.System.currentTimeMillis()
            println("***** Consensus timeout: " + (stamp2 - stamp1) / 1000 + " sec")
            ledgerForA = agentA.getMicroledgersi().getLedger(ledgerName)
            val ledgerForB: AbstractMicroledger = agentB.getMicroledgersi().getLedger(ledgerName)
            val ledgerForC: AbstractMicroledger = agentC.getMicroledgersi().getLedger(ledgerName)
            val ledgers: List<AbstractMicroledger> =
                java.util.Arrays.asList<AbstractMicroledger>(ledgerForA, ledgerForB, ledgerForC)
            for (ledger in ledgers) {
                val allTxns: List<Transaction> = ledger.allTransactions
                assertEquals(5, allTxns.size.toLong())
                assertTrue(allTxns.toString().contains("op3"))
                assertTrue(allTxns.toString().contains("op4"))
                assertTrue(allTxns.toString().contains("op5"))
            }
        } finally {
            agentA.close()
            agentB.close()
            agentC.close()
        }
    }
}
*/
