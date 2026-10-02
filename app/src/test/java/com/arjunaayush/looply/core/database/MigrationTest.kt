package com.arjunaayush.looply.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class MigrationTest {

    @Test
    fun migration1To2ExecutesExpectedSql() {
        val executedSql = mutableListOf<String>()

        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        VideoDatabase.MIGRATION_1_TO_2.migrate(db)

        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS pending_reels") })
        assertTrue(executedSql.any { it.contains("CREATE INDEX IF NOT EXISTS index_pending_reels_status") })
        assertTrue(executedSql.any { it.contains("CREATE INDEX IF NOT EXISTS index_pending_reels_shortcode") })
    }
}
