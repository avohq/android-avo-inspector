package app.avo.avoinspectorexample

import android.app.Activity
import android.os.Bundle
import app.avo.androidanalyticsdebugger.DebuggerMode
import app.avo.inspector.AvoEventSchemaType
import app.avo.inspector.AvoEventSchemaType.*
import app.avo.inspector.AvoInspector
import app.avo.inspector.AvoInspectorEnv


@SuppressWarnings("ALL")
class KotlinActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val avoInspector = AvoInspector("MY_API_KEY",
                application, AvoInspectorEnv.Dev,this)

        AvoInspector.enableLogging(true)

        avoInspector.trackSchemaFromEvent("Event name",
                mapOf("String Prop" to "Prop Value",
                       "Float Name" to 1.0,
                        "Bool Name" to true))

        avoInspector.trackSchema("Event name",
                mapOf("String Prop" to AvoString(),
                        "Float Name" to AvoFloat(),
                        "Bool Name" to AvoBoolean()))

        val schema = avoInspector.extractSchema(mapOf("String Prop" to "Prop Value",
                "Float Name" to 1.0,
                "Bool Name" to true))

        avoInspector.showVisualInspector(this, DebuggerMode.bubble)

        avoInspector.hideVisualInspector(this)

        val visualInspector = avoInspector.visualInspector

        AvoInspector.setBatchSize(15)
        AvoInspector.setBatchFlushSeconds(10)

        val eventSchema = mutableMapOf<String, AvoEventSchemaType>().apply {
            put("userId", AvoInt())
            put("emailAddress", AvoString())
            put("key", AvoString())
        }

        /*** Segment middleware code snippet
        val avoInspectorMiddleware = Middleware { chain ->
            val payload = chain.payload()
            if (payload.type() == BasePayload.Type.track) {
                val trackPayload = payload as TrackPayload
                avoInspector.trackSchemaFromEvent(trackPayload.event(), trackPayload.properties())
            }
            chain.proceed(payload)
        }
        val analytics = Analytics.Builder(applicationContext, "SEGMENT_ANALYTICS_WRITE_KEY")
                .middleware(avoInspectorMiddleware)
                .build()
        ***/
    }

}