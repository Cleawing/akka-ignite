package com.cleawing.ignite.akka

import java.util.concurrent.ExecutorService

import akka.actor.{ActorRef, ExtendedActorSystem}
import com.cleawing.ignite.plugins.GridNameValidationPluginConfiguration

import org.apache.ignite._
import org.apache.ignite.cache.affinity.Affinity
import org.apache.ignite.cluster.ClusterGroup
import org.apache.ignite.configuration._
import org.apache.ignite.internal.IgnitionEx
import org.apache.ignite.lang.IgniteProductVersion
import org.apache.ignite.lifecycle.{LifecycleBean, LifecycleEventType}
import org.apache.ignite.plugin.IgnitePlugin

import scala.concurrent.Await
import scala.concurrent.duration.Duration


private[ignite] trait ExtensionAdapter {
  import scala.collection.JavaConversions._
  import com.cleawing.ignite.NullableField
  import com.cleawing.ignite.akka.LocalNodeWatcher.Restart

  protected def system: ExtendedActorSystem

  def name : String = ignite().name()
  def log() : IgniteLogger = ignite().log()
  def configuration() : IgniteConfiguration = ignite().configuration()
  def cluster() : IgniteCluster = ignite().cluster()
  def compute() : IgniteCompute = ignite().compute()
  def compute(grp: ClusterGroup) : IgniteCompute = ignite().compute(grp)
  def messages() : IgniteMessaging = ignite().message()
  def messages(grp: ClusterGroup) : IgniteMessaging = ignite().message(grp)
  def events() : IgniteEvents = ignite().events()
  def events(grp: ClusterGroup) : IgniteEvents = ignite().events(grp: ClusterGroup)
  def services() : IgniteServices = ignite().services()
  def services(grp: ClusterGroup) : IgniteServices = ignite().services(grp)
  def executorService() : ExecutorService = ignite().executorService()
  def executorService(grp: ClusterGroup) : ExecutorService = ignite().executorService(grp)
  def version() : IgniteProductVersion = ignite().version()
  def scheduler() : IgniteScheduler = ignite().scheduler()

  object Cache {
    object config {
      def apply[K, V]() : CacheConfiguration[K, V] = new CacheConfiguration()
      def apply[K, V](name: String) : CacheConfiguration[K, V] = new CacheConfiguration(name)
      def apply[K, V](cfg: CacheConfiguration[K, V]) : CacheConfiguration[K, V] = new CacheConfiguration(cfg)
      def near[K, V] : NearCacheConfiguration[K, V] = new NearCacheConfiguration()
      def add[K, V](cacheCfg: CacheConfiguration[K, V]) = ignite().addCacheConfiguration(cacheCfg)
    }

    def getOrCreate[K, V](cacheCfg: CacheConfiguration[K, V]) : IgniteCache[K, V] = ignite().getOrCreateCache(cacheCfg)
    def getOrCreate[K, V](cacheCfg: CacheConfiguration[K, V], nearCfg: NearCacheConfiguration[K, V]) : IgniteCache[K, V] = {
      ignite().getOrCreateCache(cacheCfg, nearCfg)
    }
    def getOrCreate[K, V](cacheName: String) : IgniteCache[K, V] = ignite().getOrCreateCache[K, V](cacheName)
    def createNear[K, V](@NullableField cacheName: String, nearCfg: NearCacheConfiguration[K, V]) : IgniteCache[K, V] = {
      ignite().createNearCache(cacheName, nearCfg)
    }
    def getOrCreateNear[K, V](@NullableField cacheName: String, nearCfg: NearCacheConfiguration[K, V]) : IgniteCache[K, V] = {
      ignite().createNearCache(cacheName, nearCfg)
    }
    def destroy(cacheName: String) : Unit = ignite().destroyCache(cacheName)

    def apply[K, V](@NullableField cacheName: String) : IgniteCache[K, V] = ignite().cache[K, V](cacheName)
    def apply[K, V](cacheCfg: CacheConfiguration[K, V]) : IgniteCache[K, V] = ignite().createCache(cacheCfg)
    def apply[K, V](cacheCfg: CacheConfiguration[K, V], nearCfg: NearCacheConfiguration[K, V]) : IgniteCache[K, V] = {
      ignite().createCache(cacheCfg, nearCfg)
    }
  }

  def transactions() : IgniteTransactions = ignite().transactions()
  def dataStreamer[K, V](@NullableField cacheName: String) : IgniteDataStreamer[K, V] = ignite().dataStreamer(cacheName)

  object IGFS {
    def config() : FileSystemConfiguration = new FileSystemConfiguration()
    def config(cfg: FileSystemConfiguration) = new FileSystemConfiguration(cfg)
    def apply() : Iterable[IgniteFileSystem] = ignite().fileSystems()
    def apply(name: String) : IgniteFileSystem = ignite().fileSystem(name)
  }

  object Atomic {
    def sequence(name: String, initVal: Long, create: Boolean) : IgniteAtomicSequence = ignite().atomicSequence(name, initVal, create)
    def long(name: String, initVal: Long, create: Boolean) : IgniteAtomicLong = ignite().atomicLong(name, initVal, create)
    def reference[T](name: String, @NullableField initVal: T, create: Boolean) : IgniteAtomicReference[T] = {
      ignite().atomicReference(name, initVal, create)
    }
    def stamped[T, S](name: String, @NullableField initVal: T, @NullableField initStamp: S, create: Boolean) : IgniteAtomicStamped[T, S] = {
      ignite().atomicStamped(name, initVal, initStamp, create)
    }
  }

  def countDownLatch(name: String, cnt: Int, autoDel: Boolean, create: Boolean) : IgniteCountDownLatch = {
    ignite().countDownLatch(name, cnt, autoDel, create)
  }

  object Collection {
    def config() : CollectionConfiguration = new CollectionConfiguration()
    def queue[T](name: String, cap: Int, @NullableField cfg : CollectionConfiguration) : IgniteQueue[T] = ignite().queue(name, cap, cfg)
    def set[T](name: String, @NullableField cfg : CollectionConfiguration) : IgniteSet[T] = ignite().set(name, cfg)
  }

  def plugin[T <: IgnitePlugin](name: String) : T = ignite().plugin(name)
  def affinity[K](cacheName: String) : Affinity[K] = ignite().affinity(cacheName)
  def state() : IgniteState = Ignition.state(system.name)

  def restart() : Unit = {
    // Due lifeCycle listener will be restart automatically
    stop0()
  }

  def stop() : Unit = {
    Await.ready(system.terminate(), Duration.Inf)
  }

  private def ignite() : Ignite = Ignition.ignite(system.name)

  protected[ignite] def init() : Unit = {
    start(system.actorOf(LocalNodeWatcher()))
    system.registerOnTermination {
      stop0()
      IgniteExtension.systems.remove(system.name)
    }
  }

  // TODO. Implement idiomatic TypeSafe akka config (and do not depend on Spring Beans)
  protected[ignite] def start(monitor: ActorRef) : Unit = {
    val config = IgnitionEx.loadConfigurations(getClass.getResourceAsStream("/reference_ignite.xml"))
      .get1().toArray.apply(0).asInstanceOf[IgniteConfiguration]
    config.
      setGridName(system.name).
      setLifecycleBeans(lifeCycleBean(monitor))
    IgnitionEx.start(config)
  }

  private def stop0(): Unit = {
    Ignition.stop(system.name, true)
  }

  private def lifeCycleBean(monitor: ActorRef) = new LifecycleBean {
    override def onLifecycleEvent(evt: LifecycleEventType): Unit = {
      evt match {
        case LifecycleEventType.AFTER_NODE_STOP => monitor ! Restart
        case _ => // ignore others
      }
    }
  }
}

