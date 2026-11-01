package com.protctus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores a single VVIP trusted domain sourced from the Tranco Top-380K list.
 * The [domain] column is the primary key, giving it an implicit SQLite B-tree index
 * for O(log n) lookups (~0.1ms on average).
 */
@Entity(tableName = "vvip_domains")
data class VvipDomain(
    @PrimaryKey
    val domain: String
)
