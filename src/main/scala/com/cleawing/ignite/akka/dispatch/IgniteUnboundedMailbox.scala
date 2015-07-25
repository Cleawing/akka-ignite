package com.cleawing.ignite.akka.dispatch

import akka.actor.{ActorRef, ActorSystem}
import akka.dispatch.{MessageQueue, ProducesMessageQueue, MailboxType}
import com.cleawing.ignite.akka.dispatch.MessageQueues.IgniteUnboundedQueueBasedMessageQueue
import com.cleawing.ignite.akka.IgniteConfig
import com.typesafe.config.Config
import org.apache.ignite.cache.{CacheMode, CacheMemoryMode}
import com.cleawing.ignite.Implicits.ConfigOps

case class IgniteUnboundedMailbox(_memoryMode: CacheMemoryMode)
  extends MailboxType with ProducesMessageQueue[IgniteUnboundedQueueBasedMessageQueue] {

  import com.cleawing.ignite.grid

  def this(settings: ActorSystem.Settings, config: Config) = {
    this(config.getCacheMemoryMode("cache-memory-mode"))
  }

  final override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = {
    (owner, system) match {
      case (Some(o), Some(s)) =>
        implicit val ignite = grid
        val cfg = IgniteConfig.CollectionBuilder()
          .setCacheMode(CacheMode.LOCAL)
          .setMemoryMode(_memoryMode)
          .setOffHeapMaxMemory(0)
          .build()
        new IgniteUnboundedQueueBasedMessageQueue(o.path.toStringWithoutAddress, cfg)
      case _ => throw new IllegalStateException("ActorRef and ActorSystem should be defined.")
    }
  }
}
