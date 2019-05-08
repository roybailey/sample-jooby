package me.roybailey.jooby

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import me.roybailey.jooby.App
import org.jooby.Mutant
import org.jooby.Request
import org.junit.Test
import org.jooby.test.MockRouter
import org.junit.Assert.assertEquals

class AppUnitTest {
    @Test
    fun helloUniTest () {
        val value = "UnitTest"

        val name = mock<Mutant> {
            on {value("default")} doReturn value
        }

        val req = mock<Request> {
            on {param("name") } doReturn name
        }

        val result = MockRouter(App(), req)
                .get<String>("/")

        assertEquals("Hello UnitTest!", result)

    }
}
