package com.nla.AIscanerPDF.data

import android.content.res.XmlResourceParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import ru.aiscanner.docs.R

/**
 * Гарантия приватности (п. 10 ТЗ): документы и база данных исключены
 * из Android Backup и device transfer. Тест сломается, если правила
 * бэкапа случайно изменят.
 */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {

    private data class Exclude(val domain: String?, val path: String?)

    private fun parseExcludes(xmlRes: Int): List<Exclude> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser: XmlResourceParser = context.resources.getXml(xmlRes)
        val excludes = mutableListOf<Exclude>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "exclude") {
                excludes += Exclude(
                    domain = parser.getAttributeValue(null, "domain"),
                    path = parser.getAttributeValue(null, "path"),
                )
            }
            event = parser.next()
        }
        parser.close()
        return excludes
    }

    @Test
    fun fullBackupContentExcludesDocumentsAndDatabase() {
        val excludes = parseExcludes(R.xml.backup_rules)
        assertTrue(excludes.any { it.domain == "file" && it.path == "documents/" })
        assertTrue(excludes.any { it.domain == "database" })
    }

    @Test
    fun dataExtractionRulesExcludeDocumentsAndDatabase() {
        val excludes = parseExcludes(R.xml.data_extraction_rules)
        // exclude встречается и в cloud-backup, и в device-transfer
        assertTrue(excludes.count { it.domain == "file" && it.path == "documents/" } >= 2)
        assertTrue(excludes.count { it.domain == "database" } >= 2)
    }

}
