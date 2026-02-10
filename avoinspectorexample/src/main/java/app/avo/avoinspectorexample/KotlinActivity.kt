package app.avo.avoinspectorexample

import android.app.Activity
import android.os.Bundle
import app.avo.androidanalyticsdebugger.DebuggerManager
import app.avo.inspector.AvoEventSchemaType
import app.avo.inspector.AvoEventSchemaType.*
import app.avo.inspector.AvoInspector
import app.avo.inspector.AvoInspectorEnv
import app.avo.inspector.VisualInspectorMode

@SuppressWarnings("unused")
class KotlinActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AvoInspector.setBatchSize(0)

        val apiKey = BuildConfig.AVO_API_KEY.ifEmpty { "MYEfq8E4FZ6Xkxlo9mTc" }
        val avoInspector = AvoInspector(apiKey,
                application, AvoInspectorEnv.Dev, null,
                "024ec9c17ea2fb3e727d2815941eeb7d7c6e551536c9e2dde37fbbf0ffb9850579")

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

        avoInspector.showVisualInspector(this, VisualInspectorMode.BUBBLE)

        avoInspector.hideVisualInspector(this)

        val visualInspector = avoInspector.visualInspector as DebuggerManager

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
                .useSourceMiddleware(avoInspectorMiddleware)
                .build()
         ***/
    }

}
