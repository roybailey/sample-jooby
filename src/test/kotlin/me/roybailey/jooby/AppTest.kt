package me.roybailey.jooby

import io.restassured.RestAssured.get
import io.restassured.RestAssured.given
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jooby.Status
import org.junit.Assert.assertEquals
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith

@RunWith(JUnitPlatform::class)
object AppTest : Spek({
    jooby(App()) {
        describe("Get with query parameter") {
            given("queryParameter name=Kotlin") {
                it("should return Hello Kotlin!") {
                    val name = "Kotlin"
                    given()
                            .queryParam("name", name)
                            .`when`()
                            .get("/")
                            .then()
                            .assertThat()
                            .statusCode(Status.OK.value())
                            .extract()
                            .asString()
                            .let {
                                assertEquals(it, "Hello $name!")
                            }
                }
            }

            given("Get without query parameter") {
                it("should return Jooby as the default name") {
                    get("/")
                            .then()
                            .assertThat()
                            .statusCode(Status.OK.value())
                            .extract()
                            .asString()
                            .let {
                                assertEquals(it, "Hello Jooby!")
                            }
                }
            }
        }
    }
})
