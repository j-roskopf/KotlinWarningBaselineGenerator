import example.imageviewer.model.WrappedHttpClient
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

actual fun createWrappedHttpClient(): WrappedHttpClient {
    val test = 3
    if(test != null) {
        println(test)
    }
    return object : WrappedHttpClient {
        private val ktorClient = HttpClient(JsClient())
        override suspend fun getAsBytes(urlString: String): ByteArray {
            return ktorClient.get(urlString).readBytes()
        }
    }
}
