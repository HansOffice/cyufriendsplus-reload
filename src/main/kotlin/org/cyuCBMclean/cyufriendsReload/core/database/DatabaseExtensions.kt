package org.cyuCBMclean.cyufriendsReload.core.database

import java.sql.Connection
import java.sql.ResultSet

inline fun <T> Connection.query(sql: String, vararg params: Any?, handler: (ResultSet) -> T): T {
    return this.prepareStatement(sql).use { stmt ->
        params.forEachIndexed { index, param ->
            stmt.setObject(index + 1, param)
        }
        stmt.executeQuery().use { rs ->
            handler(rs)
        }
    }
}

fun Connection.update(sql: String, vararg params: Any?): Int {
    return this.prepareStatement(sql).use { stmt ->
        params.forEachIndexed { index, param ->
            stmt.setObject(index + 1, param)
        }
        stmt.executeUpdate()
    }
}

fun Connection.executeBatch(sql: String, batchParams: Iterable<Array<Any?>>): IntArray {
    return this.prepareStatement(sql).use { stmt ->
        for (params in batchParams) {
            params.forEachIndexed { index, param ->
                stmt.setObject(index + 1, param)
            }
            stmt.addBatch()
        }
        stmt.executeBatch()
    }
}