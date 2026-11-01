package com.protctus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.protctus.app.data.local.entity.VvipDomain

@Dao
interface VvipDomainDao {

    /**
     * Single-shot check: does ANY of the candidate domains exist in the VVIP table?
     * Using IN clause allows checking a full subdomain chain in one DB roundtrip.
     * e.g., for "mail.google.com" we pass ["mail.google.com", "google.com"]
     */
    @Query("SELECT EXISTS(SELECT 1 FROM vvip_domains WHERE domain IN (:candidates) LIMIT 1)")
    suspend fun isAnyDomainSafe(candidates: List<String>): Boolean

    /**
     * Returns the row count. Used to check if the table has been pre-populated.
     */
    @Query("SELECT COUNT(*) FROM vvip_domains")
    suspend fun count(): Int

    /**
     * Batch insert for initial population from the Tranco asset file.
     * IGNORE strategy silently skips duplicates during population.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(domains: List<VvipDomain>)
}
