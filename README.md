# sample-jooby

Sample project for exploring [Jooby](https://jooby.org/)

## Getting Started

You'll need an instance of Neo4j database to connect to 

* build `mvn clean install`
* run `mvn jooby:run`
* tests `mvn clean package`

1. goto `http://localhost:8080` to view public unauthenticated page
1. click `Login` button, and login to your google account, you should be redirected to private page showing user details and JWT
1. click `Todos` button to show embedded Vue based todos talking to Jooby api endpoints

## Notes

Overall a nice framework (Jooby v1.6) but the documentation is light which makes building more complex routing, authentication, module flows problematic.
Like the module approach and supported plugins, goes beyond other similar frameworks (excluding Spring)

* DONE: Wire up Pac4 security
* DONE: Wire up embedded Vue pages for todo list handling 
* DONE: Add Neo4j module and connect to local database or memory database 
* DONE: Basic build and run from Jooby starter sample 

## Reference Links

* Read the [module documentation](http://jooby.org/doc/lang-kotlin)
* Join the [channel](https://gitter.im/jooby-project/jooby)
* Join the [group](https://groups.google.com/forum/#!forum/jooby-project)
* Graph Database [neo4j desktop](https://neo4j.com/download/)
