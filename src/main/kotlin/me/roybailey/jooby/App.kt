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
import org.neo4j.graphdb.GraphDatabaseService
import org.pac4j.core.profile.CommonProfile
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration
import org.pac4j.jwt.profile.JwtGenerator
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.profile.OidcProfile

// neo4j module for connecting via conf properties to server (when mode = null)
// or connecting to embedded instance (when mode = "mem")
val NEO4JMODE: String? = "mem"

/**
 * Kotlin stater project.
 */
class App : Kooby({

    val logger = KotlinLogging.logger {}

    logger.info { "Starting..." }
    // handlebars for basic html templating pages
    use(Hbs())
    use(when (NEO4JMODE) {
        null -> Neo4j()
        else -> Neo4j(NEO4JMODE)
    })
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
        val name = req.param("name").value("Jooby")
        val total = runNeo4jCypher(NEO4JMODE, req, "match (n:Todo) return count(n) as total", emptyMap())?.get(0).get("total")

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
        val name = req.param("name").value("Jooby")
        val total = runNeo4jCypher(NEO4JMODE, req, "match (n:Todo) return count(n) as total", emptyMap())?.get(0).get("total")

        Results.html("templates/profile")
                .put("profile", profile)
                .put("name", name)
                .put("total", total)
                .put("jwtToken", jwtToken)

    }

    // generate a JWT token
    get("/generate-token") {
        val token = generateToken(require(Config::class.java), require(CommonProfile::class.java))
        Results.ok(token)
                .type(MediaType.text)
    }

    // declare our todos api routes
    use(TodoController::class.java)

})


// utility method to run an embedded or remote cypher query
private fun runNeo4jCypher(neo4jMode: String?, req: Request, cypher: String, params: Map<String, Any>): List<Map<String, Any>> {
    val normalized = mutableListOf<Map<String,Any>>()
    when (neo4jMode) {
        null -> {
            val neo4j = req.require(Session::class.java)
            val result = neo4j.run(Statement(cypher, params))
            while(result.hasNext()) normalized.add(result.next().asMap())
        }
        else -> {
            val neo4j = req.require(GraphDatabaseService::class.java)
            val result = neo4j.execute(cypher, params)
            while(result.hasNext()) normalized.add(result.next())
        }
    }
    return normalized
}


/**
 * Neo4j backed task store
 */
data class Todo(val id: String, val title: String, val completed: Boolean)

@Path("/api/todos")
class TodoController {

    private val logger = KotlinLogging.logger {}


    @GET
    fun todos(req: Request): Result {
        val todos = runNeo4jCypher(NEO4JMODE, req, """
            match (n:Todo)
            return n.guid as guid, n.title as title, n.completed as completed
            """, emptyMap())
                .map { logger.debug { it }; it }
                .map { Todo(it.get("guid") as String, it.get("title") as String, it.get("completed") as Boolean) }

        todos.forEach { logger.debug { "Loaded $it" } }
        return Results.with(todos, 200).type(MediaType.json)
    }


    @PUT
    fun createTodo(req: Request, @Body newTodos: Array<Todo>): Result {

        // the web 'put's the entire list every time, so we delete and re-create in the store
        // ideally the server would be the master as per usual pattern and we would get individual deletes instead
        runNeo4jCypher(NEO4JMODE, req, "match (n:Todo) delete n", emptyMap())
        newTodos.map { logger.debug { "Creating $it" }; it }
                .map {
                    runNeo4jCypher(NEO4JMODE, req, """
                            merge (n:Todo { guid: ${'$'}id })
                              set n.title = ${'$'}title, n.completed = ${'$'}completed
                            return n
                            """, mapOf(Pair("id", it.id), Pair("title", it.title), Pair("completed", it.completed)))
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
