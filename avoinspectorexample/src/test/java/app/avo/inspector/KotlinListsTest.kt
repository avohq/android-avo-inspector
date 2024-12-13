package app.avo.inspector

import android.app.Application
import android.content.ContentResolver
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.avo.inspector.AvoEventSchemaType.AvoInt
import app.avo.inspector.AvoEventSchemaType.AvoList
import app.avo.inspector.AvoEventSchemaType.AvoNull
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.util.*

class KotlinListsTest {

    private lateinit var sut: AvoInspector

    @Mock
    lateinit var mockApplication: Application

    @Mock
    lateinit var mockPackageManager: PackageManager

    @Mock
    lateinit var mockPackageInfo: PackageInfo

    @Mock
    lateinit var mockApplicationInfo: ApplicationInfo

    @Mock
    lateinit var mockSharedPrefs: SharedPreferences

    @Mock
    lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        mockPackageInfo.versionName = "myVersion"

        Mockito.`when`(mockApplication.packageManager).thenReturn(mockPackageManager)
        Mockito.`when`(mockApplication.packageName).thenReturn("")
        Mockito.`when`(mockPackageManager.getPackageInfo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())).thenReturn(mockPackageInfo)
        Mockito.`when`(mockApplication.applicationInfo).thenReturn(mockApplicationInfo)
        Mockito.`when`(mockApplication.getSharedPreferences(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())).thenReturn(mockSharedPrefs)
        Mockito.`when`(mockSharedPrefs.getString(ArgumentMatchers.anyString(), ArgumentMatchers.eq<Any?>(null) as String?)).thenReturn("")
        Mockito.`when`(mockSharedPrefs.edit()).thenReturn(mockEditor)
        Mockito.`when`(mockEditor.putLong(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong())).thenReturn(mockEditor)
        Mockito.`when`(mockEditor.putString(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn(mockEditor)
        Mockito.`when`(mockApplication.applicationContext).thenReturn(mockApplication)
        Mockito.`when`(mockApplication.contentResolver).thenReturn(Mockito.mock(ContentResolver::class.java))

        sut = AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev)
    }

    @org.junit.Test
    fun canExtractJSONArrayOfOptionalLists() {
        val testJsonObj = JSONObject()
        try {
            val items: MutableList<List<*>?> = mutableListOf()
            val nonOptionalList = listOf(1)
            val optionalList: List<Int>? = null

            items.add(nonOptionalList)
            items.add(optionalList)

            testJsonObj.put("list_key", items)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        val schema: Map<String, AvoEventSchemaType> = sut.extractSchema(testJsonObj)
        Assert.assertEquals(testJsonObj.length().toLong(), schema.size.toLong())
        for (key in schema.keys) {
            val value = schema[key]
            val expected = AvoList(HashSet())
            expected.subtypes.add(AvoList(HashSet(listOf(AvoInt())) as Set<AvoEventSchemaType>))
            expected.subtypes.add(AvoNull())
            Assert.assertEquals(expected, value)
        }
    }
}
