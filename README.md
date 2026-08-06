# spring-kafka

Minimal Spring Boot 4 + Kafka sample built with Gradle 9 and a version catalog (`gradle/libs.versions.toml`).

## Stack

| Piece | Version |
| --- | --- |
| Gradle | 9.6.1 (wrapper) |
| Java toolchain | 21 |
| Spring Boot | 4.1.0 (Spring Framework 7) |
| Spring for Apache Kafka | managed by the Boot BOM |

## Run

```bash
docker compose up -d          # single-node Kafka in KRaft mode on localhost:9092
./gradlew bootRun             # gradlew.bat bootRun on Windows
```

## Try it

```bash
curl http://localhost:8080/hello
curl -X POST "http://localhost:8080/hello?message=ola"
curl http://localhost:8080/hello/last
```

`POST /hello` publishes to `hello-topic`; `HelloConsumer` (`@KafkaListener`) consumes it and
`GET /hello/last` returns the last message it saw.

## Layout

```
build.gradle.kts               plugins + deps via libs.* aliases
gradle/libs.versions.toml      single place for versions
src/main/java/com/soutelloit/springkafka/
  SpringKafkaApplication.java
  config/KafkaTopicConfig.java   creates hello-topic on startup
  service/HelloProducer.java     KafkaTemplate wrapper
  service/HelloConsumer.java     @KafkaListener
  controller/HelloController.java
src/main/resources/application.yml
docker-compose.yml
```

## Postman

`postman/spring-kafka.postman_collection.json` + `postman/local.postman_environment.json`.

Import both into Postman and run the collection with the Runner, or from the CLI:

```bash
npm install -g newman
newman run postman/spring-kafka.postman_collection.json ^
  -e postman/local.postman_environment.json --delay-request 300
```

Covers: actuator health, `GET /hello`, `POST /hello` (publishes a unique
`postman-<timestamp>` message), `GET /hello/last` (retries up to 10x while the
consumer catches up), and a 404 negative case — 15 assertions total.

Run it as a *collection*, not as individual requests: the publish/consume
assertion depends on the requests running in order.

## Bumping versions

Everything version-related lives in `gradle/libs.versions.toml`. For Gradle itself:

```bash
./gradlew wrapper --gradle-version <version>
```
