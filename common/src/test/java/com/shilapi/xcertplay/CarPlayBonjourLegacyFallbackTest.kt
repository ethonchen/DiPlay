package com.shilapi.xcertplay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.network.CarPlayBonjour
import java.io.IOException
import java.net.BindException
import java.net.Inet6Address
import java.net.InetAddress
import javax.jmdns.JmDNS
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class CarPlayBonjourLegacyFallbackTest {
    private val context = mock(Context::class.java)
    private val wifi = mock(WifiManager::class.java)
    private val lock = mock(WifiManager.MulticastLock::class.java)
    private val nsd = mock(NsdManager::class.java)
    private val ipv4 = InetAddress.getByName("192.0.2.10")
    private val ipv6 = Inet6Address.getByAddress(null, InetAddress.getByName("fe80::1234").address, 7)
    private val config = AirPlayConfig(deviceName = "DiPlay", deviceId = "02:00:00:00:00:02",
        btMac = "02:00:00:00:00:02", sourceVersion = "366.0",
        main = AirPlayDisplayConfig(widthPixels = 1024, heightPixels = 600))
    private val identity = AirPlayIdentity(ByteArray(32), ByteArray(32), "test-pairing")

    private fun setup() {
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSystemService(WifiManager::class.java)).thenReturn(wifi)
        `when`(context.getSystemService(Context.NSD_SERVICE)).thenReturn(nsd)
        `when`(wifi.createMulticastLock("carplay-bonjour")).thenReturn(lock)
        `when`(lock.isHeld).thenReturn(true)
    }

    @Test fun api23PublishesOnlyIpv4EvenWithIpv6PrimaryAndAnApAddressInEachFamily() {
        setup()
        val dns = mock(JmDNS::class.java)
        mockStatic(JmDNS::class.java).use { factory ->
            factory.`when`<JmDNS> { JmDNS.create(ipv4, "carplay-020000000002") }.thenReturn(dns)
            CarPlayBonjour(context, config, identity, ipv6.hostAddress, true,
                additionalAddresses = listOf(ipv4)).use { bonjour ->
                bonjour.start()
                assertTrue(bonjour.diagnosticSnapshot().contains("mdnsFamilies=IPv4 mdnsMode=interface"))
                factory.verify({ JmDNS.create(ipv4, "carplay-020000000002") }, times(1))
                factory.verifyNoMoreInteractions()
                verifyNoInteractions(nsd)
            }
            verify(dns).close()
            verify(lock).release()
        }
    }

    @Test fun occupiedPortFallsBackAndItsWorkerActuallyConsumesSystemDiscovery() {
        setup()
        val diagnostics = mutableListOf<String>()
        mockStatic(JmDNS::class.java).use { factory ->
            factory.`when`<JmDNS> { JmDNS.create(ipv4, "carplay-020000000002") }
                .thenThrow(BindException("bind failed: EADDRINUSE"))
            val bonjour = CarPlayBonjour(context, config, identity, ipv4.hostAddress, true,
                additionalAddresses = listOf(ipv6), onDiagnostic = { diagnostics.add(it) })
            try {
                bonjour.start()
                assertTrue(bonjour.diagnosticSnapshot().contains("mdnsFamilies=system mdnsMode=system-fallback"))
                assertTrue(diagnostics.any { it.contains("reason=port-in-use udpPort=5353") })
                val discovery = ArgumentCaptor.forClass(NsdManager.DiscoveryListener::class.java)
                verify(nsd).registerService(any(NsdServiceInfo::class.java), eq(NsdManager.PROTOCOL_DNS_SD), any())
                verify(nsd).discoverServices(eq("_carplay-ctrl._tcp"), eq(NsdManager.PROTOCOL_DNS_SD), discovery.capture())
                val phone = NsdServiceInfo().apply {
                    serviceName = "test-phone"
                    serviceType = "_carplay-ctrl._tcp"
                }
                discovery.value.onServiceFound(phone)
                verify(nsd, timeout(2000)).resolveService(eq(phone), any())
            } finally { bonjour.close() }
            verify(nsd).unregisterService(any())
            verify(nsd).stopServiceDiscovery(any())
            verify(lock).release()
        }
    }

    @Test fun firmwareWrappedBindFailureAlsoUsesSystemFallback() {
        setup()
        mockStatic(JmDNS::class.java).use { factory ->
            factory.`when`<JmDNS> { JmDNS.create(ipv4, "carplay-020000000002") }
                .thenThrow(IOException("socket initialization", BindException("EADDRINUSE")))
            CarPlayBonjour(context, config, identity, ipv4.hostAddress, true).use { bonjour ->
                bonjour.start()
                assertTrue(bonjour.diagnosticSnapshot().contains("mdnsMode=system-fallback"))
            }
            verify(nsd).stopServiceDiscovery(any())
        }
    }

    @Test fun otherIoFailuresRemainVisibleAndReleaseTheMulticastLock() {
        setup()
        val failure = IOException("multicast join refused")
        mockStatic(JmDNS::class.java).use { factory ->
            factory.`when`<JmDNS> { JmDNS.create(ipv4, "carplay-020000000002") }.thenThrow(failure)
            val bonjour = CarPlayBonjour(context, config, identity, ipv4.hostAddress, true)
            assertSame(failure, assertThrows(IOException::class.java) { bonjour.start() })
            verifyNoInteractions(nsd)
            verify(lock).release()
        }
    }

    @Test fun unavailableApAddressRemainsAnErrorInsteadOfBeingReportedAsPortOccupation() {
        setup()
        val failure = BindException("bind failed: EADDRNOTAVAIL")
        mockStatic(JmDNS::class.java).use { factory ->
            factory.`when`<JmDNS> { JmDNS.create(ipv4, "carplay-020000000002") }.thenThrow(failure)
            val bonjour = CarPlayBonjour(context, config, identity, ipv4.hostAddress, true)
            assertSame(failure, assertThrows(IOException::class.java) { bonjour.start() })
            verifyNoInteractions(nsd)
            verify(lock).release()
        }
    }

    @Test fun failedSystemFallbackCleansUpItsAlreadyRequestedPublication() {
        setup()
        mockStatic(JmDNS::class.java).use { factory ->
            factory.`when`<JmDNS> { JmDNS.create(ipv4, "carplay-020000000002") }
                .thenThrow(BindException("EADDRINUSE"))
            doThrow(IllegalStateException("system discovery unavailable")).`when`(nsd)
                .discoverServices(anyString(), anyInt(), any())
            val bonjour = CarPlayBonjour(context, config, identity, ipv4.hostAddress, true)
            assertThrows(IllegalStateException::class.java) { bonjour.start() }
            verify(nsd).unregisterService(any())
            verify(nsd, never()).stopServiceDiscovery(any())
            verify(lock).release()
        }
    }
}
