package org.cyuCBMclean.cyufriendsReload.core.database

interface BaseRepository {
    val tableName: String
    suspend fun createTable(databaseManager: DatabaseManager)
}