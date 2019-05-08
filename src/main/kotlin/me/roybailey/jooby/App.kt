package me.roybailey.jooby

import com.typesafe.config.Config
import mu.KotlinLogging
import org.jooby.*
import org.jooby.Results.redirect
import org.jooby.hbs.Hbs
import org.jooby.json.Jackson
import org.jooby.mvc.Body
import org.jooby.mvc.GET
import org.jooby.mvc.PUT
import org.jooby.mvc.Path
import org.jooby.neo4j.Neo4j
import org.jooby.pac4j.Pac4j
import org.neo4j.driver.v1.Session
import org.neo4j.driver.v1.Statement
import org.pac4j.core.profile.CommonProfile
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration
import org.pac4j.jwt.profile.JwtGenerator
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.profile.OidcProfile


/**
 * Kotlin stater project.
 */
class App : Kooby({

    val logger = KotlinLogging.logger {}

    logger.info { "Starting..." }
    // handlebars for basic html templating pages
    use(Hbs())
    // neo4j module for connecting via conf properties to server
    use(Neo4j())
    // jackson to convert objects from/into json
    use(Jackson())

    // declare where static asset files can be accessed (e.g. raw html,css,js,images etc.)
    assets("/assets/**")

    // simple redirect of base url to simple public page
    get("/") {
        redirect("/public")
    }

    // simple public page getting values from neo4j and displaying via a template html resource file
    get("/public") { req ->
        val neo4j = require(Session::class.java)
        val name = req.param("name").value("Jooby")
        val total = neo4j.run(Statement("match (n:Todo) return count(n) as total", emptyMap())).single().get("total", 0)

        Results.html("templates/public")
                .put("name", name)
                .put("total", total)

    }

    // ensure any api calls are protected by requiring the CommonProfile
    // 406 Forbidden is returned when this isn't available
    before("/api/**") { req, rsp ->
        require(CommonProfile::class.java)
    }

    // setup for OpenID authentication (google in this sample)
    // any routes declared above this are unauthenticated,
    // and authenticated if declared after this (ordering matters in Jooby)
    use(Pac4j()
            .client("*") { conf ->
                val oidc = OidcConfiguration()
                oidc.clientId = conf.getString("oidc.clientId")
                oidc.secret = conf.getString("oidc.secret")
                oidc.discoveryURI = conf.getString("oidc.discoveryURI")
                oidc.addCustomParam("prompt", "consent")
                OidcClient<OidcProfile>(oidc)
            })

    fun generateToken(conf: Config, profile: CommonProfile): String {
        val jwtGenerator = JwtGenerator<CommonProfile>(
                SecretSignatureConfiguration(conf.getString("jwt.salt")))
        profile.removeAttribute("sub") // needs to be removed as pac4 asserts this is null for some reason?
        logger.info { "generating token" }
        return jwtGenerator.generate(profile)
    }

    // map our vue based web application from URI to resource location root
    assets("/private/**", "/vue/{0}")

    // map our private profile page (same as public page but with users email and jwt exposed
    get("/private") { req ->
        val conf = require(Config::class.java)
        val profile = require(CommonProfile::class.java)
        val jwtToken = generateToken(conf, profile)

        val neo4j = require(Session::class.java)
        val name = req.param("name").value("Jooby")
        val total = neo4j.run(Statement("match (n:Todo) return count(n) as total", emptyMap())).single().get("total", 0)

        Results.html("templates/profile")
                .put("profile", profile)
                .put("name", name)
                .put("total", total)
                .put("jwtToken", jwtToken)

    }.consumes("*").produces("text/html")

    // generate a JWT token
    get("/generate-token") {
        val token = generateToken(require(Config::class.java), require(CommonProfile::class.java))
        Results.ok(token)
                .type(MediaType.text)
    }

    // declare our todos api routes
    use(TodoController::class.java)

})



/**
 * Neo4j backed task store
 */
data class Todo(val id: String, val title: String, val completed: Boolean)

@Path("/api/todos")
class TodoController {

    private val logger = KotlinLogging.logger {}


    @GET
    fun todos(req: Request): Result {
        val neo4j = req.require(Session::class.java)
        val todos = neo4j.run(Statement("""
            match (n:Todo)
            return n.guid as guid, n.title as title, n.completed as completed
            """, emptyMap()))
                .list()
                .map { logger.debug { it }; it }
                .map { Todo(it.get("guid", ""), it.get("title", "unknown"), it.get("completed", false)) }

        todos.forEach { logger.debug { "Loaded $it" } }
        return Results.with(todos, 200).type(MediaType.json)
    }


    @PUT
    fun createTodo(req: Request, @Body newTodos: Array<Todo>): Result {
        val neo4j = req.require(Session::class.java)

        newTodos.map { logger.debug { "Creating $it" }; it }
                .map {
                    neo4j.run(Statement("""
                            merge (n:Todo { guid: ${'$'}id })
                              set n.title = ${'$'}title, n.completed = ${'$'}completed
                            return n
                            """, mapOf(Pair("id", it.id), Pair("title", it.title), Pair("completed", it.completed))))
                            .single()
                }
                .forEach { logger.debug { it } }
        return Results.ok()
    }
}


/**
 * Run application:
 */
fun main(args: Array<String>) {
    run(::App, *args)
}
