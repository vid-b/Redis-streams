package lco.redis.perf.tests

import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.*
import lco.config.redis.RedisServiceConfig.RedisService
import zio.{Task, ZIO}

import scala.jdk.CollectionConverters.*

object RedisStreamsPerfUtils {

  def redisClient(redisService: RedisService): ZIO[Any, Nothing, RedisClient] = {
    val cOptions = ClientOptions.builder().autoReconnect(true).build()
//    ZIO.logError(s"HostName: ${redisService.credentials.hostname}  port: ${redisService.credentials.port.toInt}")
//    val client: RedisClient =
//      RedisClient.create(RedisURI.Builder
//          .redis(redisService.credentials.hostname, redisService.credentials.port.toInt)
//          .withPassword(redisService.credentials.password)
//          .withSsl(true)
//          .build())
    val client: RedisClient = RedisClient.create(RedisURI.Builder.redis("localhost", 6379).build())
    client.setOptions(cOptions)
    ZIO.succeed(client)
  }

  def streamKeyName(environment: String, coPackageName: String, coName: String, suffix: String, tenantId: String = "global"): String =
    s"$environment.co.$tenantId.$coPackageName.$coName.$suffix"

  def publishMessage(streamKeyName: String, commands: RedisAsyncCommands[String, String]): ZIO[Any, Throwable, Unit] = {
    val publisher =
      ZIO.fromCompletionStage(commands.xadd(streamKeyName, "whatsapp", "10001", "insta", "trial")) <* ZIO.logError("published data")
    for {
      _ <- ZIO.logError("Starting redis streams publisher")

      pFiber = (for {
                 publish <- publisher
               } yield publish).forever.fork.as(ZIO.unit)

      allFibers <- ZIO.foreach((1 to 2).toList)(_ => pFiber)
      _         <- ZIO.collectAllParDiscard(allFibers)
    } yield ()
  }

  def createConsumerGroup(streamKeyName: String, commands: RedisAsyncCommands[String, String], groupName: String): Task[String] = {
    ZIO.fromCompletionStage(commands.xgroupCreate(XReadArgs.StreamOffset.from(streamKeyName, "$"), groupName))
  }

  def processMessage(streamKeyName: String, commands: RedisAsyncCommands[String, String], groupName: String, consumerName: String)
      : ZIO[Any, Nothing, Unit] = {
    val messageList = ZIO.fromCompletionStage(commands.xreadgroup(Consumer.from(groupName, consumerName),
        XReadArgs.StreamOffset.lastConsumed(streamKeyName))) <* ZIO.logError("consumed data")
    val messageProcessor = for {
      ml <- messageList
      _ <- ZIO.foreachDiscard(ml.asScala.toList) { message =>
             val messageId = message.getId
             for {
               _ <- ZIO.fromCompletionStage(commands.xack(streamKeyName, groupName, messageId))
               _ <- ZIO.logError("ack data")
             } yield ()
           }
    } yield ()

    for {
      _ <- ZIO.logError("Starting redis streams consumer")

      pFiber = (for {
                 publish <- messageProcessor
               } yield publish).forever.fork.as(ZIO.unit)

      allFibers <- ZIO.foreach((1 to 2).toList)(_ => pFiber)
      _         <- ZIO.collectAllParDiscard(allFibers)
    } yield ()
  }

  def deleteStream(streamKeyName: String, commands: RedisAsyncCommands[String, String]) = {
    ZIO.fromCompletionStage(commands.del(streamKeyName))
  }

}
