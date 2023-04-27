package lco.redis.perf.tests

import io.lettuce.core.api.async.RedisAsyncCommands
import zio.logging.backend.SLF4J
import zio.{Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}
import lco.config.redis.RedisServiceConfig
import lco.config.redis.RedisServiceConfig.RedisService

object MainApp extends ZIOAppDefault {
  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] = {

    val groupName    = "myGroup"
    val consumerName = "myConsumer"

    val layers = ZLayer.makeSome[Scope, RedisService](ZLayer.fromZIO(RedisServiceConfig.config()), ZLayer.Debug.mermaid)

    (for {
      _                                           <- ZIO.logError("Redis streams perf test execution started")
      redisService                                <- ZIO.service[RedisService]
      client                                      <- RedisStreamsPerfUtils.redisClient(redisService: RedisService)
      streamName                                   = RedisStreamsPerfUtils.streamKeyName("devtest", "ain", "perf", "global", "perf")
      commands: RedisAsyncCommands[String, String] = client.connect.async
      _                                           <- RedisStreamsPerfUtils.deleteStream(streamName, commands)
      _                                           <- RedisStreamsPerfUtils.publishMessage(streamName, commands)
      _                                           <- RedisStreamsPerfUtils.createConsumerGroup(streamName, commands, groupName)
      _                                           <- RedisStreamsPerfUtils.processMessage(streamName, commands, groupName, consumerName)
    } yield ()).provideSome[Scope](layers ++ (Runtime.removeDefaultLoggers >>> SLF4J.slf4j)) *> ZIO.never
//    } yield ()).provide(Runtime.removeDefaultLoggers >>> SLF4J.slf4j) *> ZIO.never
  }
}
