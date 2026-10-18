package me.kavishdevar.librepods.bluetooth

import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothSocketConnectTimeoutTest {
    @Test
    fun closesSocketAtDeadlineAndReturnsTimeout() {
        val closeCalled = AtomicBoolean(false)
        val closeSignal = CountDownLatch(1)

        val error = runCatching {
            BluetoothSocketConnectTimeout.connect(
                timeoutMs = 50,
                closeSocket = {
                    closeCalled.set(true)
                    closeSignal.countDown()
                },
                connectSocket = {
                    assertTrue("connect should be released by close", closeSignal.await(2, TimeUnit.SECONDS))
                },
            )
        }.exceptionOrNull()

        assertTrue(error is SocketTimeoutException)
        assertTrue(closeCalled.get())
    }

    @Test
    fun successfulConnectionCancelsDeadlineWithoutClosingSocket() {
        val closeCalled = AtomicBoolean(false)

        BluetoothSocketConnectTimeout.connect(
            timeoutMs = 1_000,
            closeSocket = { closeCalled.set(true) },
            connectSocket = {},
        )

        assertFalse(closeCalled.get())
    }

    @Test
    fun failedConnectionClosesSocketAndPreservesFailure() {
        val closeCalled = AtomicBoolean(false)
        val expected = IllegalStateException("connect failed")

        val error = runCatching {
            BluetoothSocketConnectTimeout.connect(
                timeoutMs = 1_000,
                closeSocket = { closeCalled.set(true) },
                connectSocket = { throw expected },
            )
        }.exceptionOrNull()

        assertTrue(error === expected)
        assertTrue(closeCalled.get())
    }
}
