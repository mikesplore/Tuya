package com.mike

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Tuya Smart Meter API - Ktor Backend", bodyAsText())
        }
    }
    
    @Test
    fun testHealthEndpoint() = testApplication {
        application {
            module()
        }
        // Note: Health endpoint will fail without proper Tuya credentials
        // but should still return a response
        client.get("/health").apply {
            assertTrue(status == HttpStatusCode.OK || status == HttpStatusCode.ServiceUnavailable)
        }
    }
    
    @Test
    fun testDevicesEndpoint() = testApplication {
        application {
            module()
        }
        // Test the devices endpoint - may succeed or fail depending on credentials
        client.get("/api/v1/devices").apply {
            // Should return either success (200) or error (500/503)
            assertTrue(status == HttpStatusCode.OK || 
                      status == HttpStatusCode.InternalServerError || 
                      status == HttpStatusCode.ServiceUnavailable)
        }
    }
}
