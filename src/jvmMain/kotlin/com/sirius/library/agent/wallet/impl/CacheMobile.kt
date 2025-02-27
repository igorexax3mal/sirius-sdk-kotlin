
package com.sirius.library.agent.wallet.impl

import com.sirius.library.agent.wallet.abstract_wallet.AbstractCache
import com.sirius.library.agent.wallet.abstract_wallet.model.CacheOptions
import com.sirius.library.agent.wallet.abstract_wallet.model.PurgeOptions
import org.hyperledger.indy.sdk.cache.Cache
import org.hyperledger.indy.sdk.pool.Pool
import org.hyperledger.indy.sdk.wallet.Wallet

class CacheMobile(wallet: Wallet) : AbstractCache() {
    var wallet: Wallet
    var timeoutSec = 60
    var poolMobile: PoolMobile
    override fun getSchema(poolName: String?, submitter_did: String?, id: String?, options: CacheOptions?): String? {
        try {
            val pool: Pool? = poolMobile.getPoolHandle(poolName?:"")
            val optionsStr: String? = options?.serialize()
            return Cache.getSchema(pool, wallet, submitter_did, id, optionsStr)
                .get(timeoutSec.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun getCredDef(poolName: String?, submitter_did: String?, id: String?, options: CacheOptions?): String? {
        try {
            val pool: Pool? = poolMobile.getPoolHandle(poolName?:"")
            val optionsStr: String? = options?.serialize()
            return Cache.getCredDef(pool, wallet, submitter_did, id, optionsStr)
                .get(timeoutSec.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun purgeSchemaCache(options: PurgeOptions?) {
        try {
            Cache.purgeSchemaCache(wallet, options?.serialize()).get(timeoutSec.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun purgeCredDefCache(options: PurgeOptions?) {
        try {
            Cache.purgeCredDefCache(wallet, options?.serialize()).get(timeoutSec.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    init {
        this.wallet = wallet
        poolMobile = PoolMobile()
    }


}

