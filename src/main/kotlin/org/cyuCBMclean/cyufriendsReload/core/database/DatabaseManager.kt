package org.cyuCBMclean.cyufriendsReload.core.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bukkit.plugin.Plugin
import org.cyuCBMclean.cyufriendsReload.core.debug.DebugLogger
import java.io.File
import java.sql.Connection
import java.util.concurrent.Executors
import java.util.UUID

class DatabaseManager(private val plugin: Plugin) {

    private lateinit var dataSource: HikariDataSource
    private val dbDispatcher = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
    ).asCoroutineDispatcher()

    fun connect() {
        val config = HikariConfig()
        val type = plugin.config.getString("database.type", "SQLite") ?: "SQLite"

        if (type.equals("SQLite", ignoreCase = true)) {
            prepareSqliteRuntime()
            val dbFile = File(plugin.dataFolder, "data.db")
            if (!dbFile.exists()) {
                dbFile.parentFile.mkdirs()
                dbFile.createNewFile()
            }
            config.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
            config.driverClassName = "org.sqlite.JDBC"
            config.maximumPoolSize = 1
            DebugLogger.debug(1) { "数据库连接模式: SQLite -> ${dbFile.absolutePath}" }
        } else {
            val host = plugin.config.getString("database.host", "localhost")
            val port = plugin.config.getInt("database.port", 3306)
            val dbName = plugin.config.getString("database.database", "cyufriends")
            val user = plugin.config.getString("database.username", "root")
            val pass = plugin.config.getString("database.password", "")

            config.jdbcUrl = "jdbc:mysql://$host:$port/$dbName?useSSL=false&autoReconnect=true&characterEncoding=utf8"
            config.username = user
            config.password = pass
            config.maximumPoolSize = 10
            config.addDataSourceProperty("cachePrepStmts", "true")
            config.addDataSourceProperty("prepStmtCacheSize", "250")
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            DebugLogger.debug(1) { "数据库连接模式: MySQL -> $host:$port/$dbName (user=$user)" }
        }

        dataSource = HikariDataSource(config)
        DebugLogger.debug(0) { "数据库连接池已建立，maximumPoolSize=${dataSource.maximumPoolSize}" }
    }

    suspend fun <T> execute(block: Connection.() -> T): T = withContext(dbDispatcher) {
        executeSync(block)
    }

    fun <T> executeSync(block: Connection.() -> T): T {
        val shouldProfile = DebugLogger.isEnabled()
        val caller = if (shouldProfile) resolveCaller() else "unknown"
        val startedAt = if (shouldProfile) System.nanoTime() else 0L
        dataSource.connection.use { connection ->
            try {
                return connection.block()
            } finally {
                if (shouldProfile) {
                    val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                    if (DebugLogger.isLevelEnabled(2)) {
                        DebugLogger.debug(2) { "数据库操作: ${elapsedMs}ms <- $caller" }
                    } else if (elapsedMs >= slowQueryThresholdMs()) {
                        DebugLogger.warning("数据库操作偏慢: ${elapsedMs}ms <- $caller")
                    }
                }
            }
        }
    }

    suspend fun <T> transaction(block: Connection.() -> T): T = withContext(dbDispatcher) {
        transactionSync(block)
    }

    fun <T> transactionSync(block: Connection.() -> T): T {
        return dataSource.connection.use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val result = connection.block()
                connection.commit()
                result
            } catch (exception: Throwable) {
                runCatching { connection.rollback() }
                throw exception
            } finally {
                runCatching { connection.autoCommit = previousAutoCommit }
            }
        }
    }
    suspend fun ping(): Boolean = execute {
        runCatching { isValid(2) }.getOrElse {
            prepareStatement("SELECT 1").use { statement ->
                runCatching { statement.queryTimeout = 2 }
                statement.execute()
            }
            true
        }
    }

    fun pingSync(): Boolean = executeSync {
        runCatching { isValid(2) }.getOrElse {
            prepareStatement("SELECT 1").use { statement ->
                runCatching { statement.queryTimeout = 2 }
                statement.execute()
            }
            true
        }
    }

    fun maximumPoolSize(): Int {
        return if (::dataSource.isInitialized) dataSource.maximumPoolSize else 0
    }

    fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
            DebugLogger.debug(0) { "数据库连接池已关闭。" }
        }
        dbDispatcher.close()
    }

    private fun prepareSqliteRuntime() {
        val baseDir = File(plugin.dataFolder, "native/sqlite")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        val sessionDir = File(baseDir, "session-${System.currentTimeMillis()}-${UUID.randomUUID()}")
        sessionDir.mkdirs()

        baseDir.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && it.name != sessionDir.name }
            ?.forEach { runCatching { it.deleteRecursively() } }

        System.setProperty("org.sqlite.tmpdir", sessionDir.absolutePath)
        DebugLogger.debug(2) { "SQLite 临时目录已准备: ${sessionDir.absolutePath}" }
    }

    private fun slowQueryThresholdMs(): Long {
        return plugin.config.getLong("debugLog.slow-db-ms", 150L).coerceAtLeast(0L)
    }

    private fun resolveCaller(): String {
        return Throwable().stackTrace
            .firstOrNull {
                it.className.startsWith("org.cyuCBMclean.cyufriendsReload") &&
                    !it.className.contains("DatabaseManager")
            }
            ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            ?: "unknown"
    }
}
