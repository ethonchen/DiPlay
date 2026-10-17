package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class FytKeyFocusTest {
    @Test fun claimsAndRestoresTheExactPreviousValue() {
        for (before in listOf("", "0", "1")) {
            var property = before
            val notes = mutableListOf<String>()
            val focus = FytKeyFocus({ property }, { property = it }, { notes.add(it) }, {})
            focus.claim()
            assertEquals("2", property)
            assertTrue(notes.last().endsWith("verified=true"))
            focus.release()
            assertEquals(before, property)
        }
    }
    @Test fun aSuccessfulBinderTransactionIsNotProofOfKeyFocus() {
        val notes = mutableListOf<String>()
        val focus = FytKeyFocus({ "0" }, {}, { notes.add(it) }, {})
        focus.claim()
        assertTrue(notes.last(), notes.last().endsWith("verified=false"))
    }
    @Test fun doesNotOverwriteAnotherOwnersNewValueOrAnExistingClaim() {
        var property = "2"
        val writes = mutableListOf<String>()
        val focus = FytKeyFocus({ property }, { writes.add(it); property = it }, {}, {})
        focus.claim(); focus.release()
        assertTrue(writes.isEmpty())
        property = "0"
        focus.claim()
        property = "1"
        focus.release()
        assertEquals("1", property)
        assertEquals(listOf("2"), writes)
    }
    @Test fun failedWriteDoesNotPretendFocusWasGranted() {
        val notes = mutableListOf<String>()
        val focus = FytKeyFocus({ "0" }, { throw SecurityException() }, { notes.add(it) }, {})
        focus.claim()
        assertEquals("key focus request failed: SecurityException", notes.single())
        focus.release()
    }
}
