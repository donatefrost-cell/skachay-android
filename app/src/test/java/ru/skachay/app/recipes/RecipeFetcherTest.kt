package ru.skachay.app.recipes

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeFetcherTest {
    @Test
    fun acceptsValidatedDataAndRejectsInvalidOrOversizedResponses() {
        val good = File("../recipe-defaults/recipes.json").readBytes()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/good") { response ->
            response.sendResponseHeaders(200, good.size.toLong())
            response.responseBody.use { it.write(good) }
        }
        server.createContext("/bad") { response ->
            val data = "{\"schema\":2}".toByteArray()
            response.sendResponseHeaders(200, data.size.toLong())
            response.responseBody.use { it.write(data) }
        }
        server.createContext("/big") { response ->
            val data = ByteArray(65_537) { 65 }
            response.sendResponseHeaders(200, data.size.toLong())
            response.responseBody.use { it.write(data) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertEquals(Recipes(), RecipeFetcher(OkHttpClient(), "$base/good").fetch()?.recipes)
            assertNull(RecipeFetcher(OkHttpClient(), "$base/bad").fetch())
            assertNull(RecipeFetcher(OkHttpClient(), "$base/big").fetch())
        } finally {
            server.stop(0)
        }
    }
}
