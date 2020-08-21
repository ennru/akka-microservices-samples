package sample.shoppinganalytics

import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.kafka.scaladsl.Consumer.Control
import akka.kafka.scaladsl.{ Committer, Consumer, DiscoverySupport }
import akka.kafka.{ CommitterSettings, ConsumerSettings, Subscriptions }
import akka.stream.scaladsl.RestartSource
import com.google.protobuf.any.{ Any => ScalaPBAny }
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, StringDeserializer }
import org.slf4j.LoggerFactory
import sample.shoppingcart.proto

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal

object ShoppingCartEventConsumer {

  private val log = LoggerFactory.getLogger("sample.shoppinganalytics.ShoppingCartEventConsumer")

  def init(system: ActorSystem[_]): Unit = {
    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContext = system.executionContext

    val topic = system.settings.config.getString("shopping-analytics.shopping-cart-kafka-topic")
    val config = system.settings.config.getConfig("shopping-analytics.kafka.consumer")
    val consumerSettings =
      ConsumerSettings(config, new StringDeserializer, new ByteArrayDeserializer)
        .withEnrichAsync(DiscoverySupport.consumerBootstrapServers(config)(system.toClassic))
        .withGroupId("shopping-cart-analytics")
        .withStopTimeout(0.seconds)
    val committerSettings = CommitterSettings(system)

    val controlReference = new AtomicReference[Control]()

    val streamCompletion = RestartSource
      .onFailuresWithBackoff(minBackoff = 1.second, maxBackoff = 30.seconds, randomFactor = 0.1) { () =>
        val (control, source) = Consumer
          .committableSource(consumerSettings, Subscriptions.topics(topic))
          .mapAsync(1) { msg =>
            handleRecord(msg.record).map(_ => msg.committableOffset)
          }
          .via(Committer.flow(committerSettings))
          .preMaterialize()
        controlReference.set(control)
        source
      }
      .run()

    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseServiceUnbind, "event-consumer") { () =>
      val control = controlReference.get()
      if (control != null) {
        control.drainAndShutdown(streamCompletion)
      } else Future.successful(Done)
    }
  }

  private def handleRecord(record: ConsumerRecord[String, Array[Byte]]): Future[Done] = {
    val bytes = record.value()
    val x = ScalaPBAny.parseFrom(bytes)
    val typeUrl = x.typeUrl
    try {
      val inputBytes = x.value.newCodedInput()
      val event =
        typeUrl match {
          case "shopping-cart-service/shoppingcart.ItemAdded" =>
            proto.ItemAdded.parseFrom(inputBytes)
          case "shopping-cart-service/shoppingcart.ItemQuantityAdjusted" =>
            proto.ItemQuantityAdjusted.parseFrom(inputBytes)
          case "shopping-cart-service/shoppingcart.ItemRemoved" =>
            proto.ItemRemoved.parseFrom(inputBytes)
          case "shopping-cart-service/shoppingcart.CheckedOut" =>
            proto.CheckedOut.parseFrom(inputBytes)
          case _ =>
            throw new IllegalArgumentException(s"unknown record type [$typeUrl]")
        }

      event match {
        case proto.ItemAdded(cartId, itemId, quantity, _) =>
          log.info("ItemAdded: {} of {} to cart {}", quantity, itemId, cartId)
        case proto.ItemQuantityAdjusted(cartId, itemId, quantity, _) =>
          log.info("ItemQuantityAdjusted: {} of {} to cart {}", quantity, itemId, cartId)
        case proto.ItemRemoved(cartId, itemId, _) =>
          log.info("ItemQuantityAdjusted: {} removed from cart {}", itemId, cartId)
        case proto.CheckedOut(cartId, _) =>
          log.info("CheckedOut: cart {} checked out", cartId)
      }

      Future.successful(Done)
    } catch {
      case NonFatal(e) =>
        log.error("Could not process event of type [{}]", typeUrl, e)
        // continue with next
        Future.successful(Done)
    }
  }

}
