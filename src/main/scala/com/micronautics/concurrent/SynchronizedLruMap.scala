package com.micronautics.concurrent

class SynchronizedLruMap[Key<:Object, Value<:Object]
                (val concurrencyLevel: Int=4)
                (implicit ec: scala.concurrent.ExecutionContext) {
  import java.util.concurrent.Callable
  import com.google.common.cache.{Cache, CacheBuilder}
  import scala.concurrent.Future

  lazy val underlying: Cache[Key, Value] = CacheBuilder.newBuilder()
    .concurrencyLevel(concurrencyLevel)
    .softValues()
    .build[Key, Value]

  @inline def getWithDefault(key: Key, defaultValue: => Value): Value = underlying.get(key,
    new Callable[Value] {
      override def call: Value = defaultValue
    }
  )

  @inline def getAsyncWithDefault(key: Key, defaultValue: => Value): Future[Value] =
    Future { getWithDefault(key, defaultValue) }

  @inline def put(key: Key, value: Value): Unit = underlying.put(key, value)

  @inline def putAsync(key: Key, value: => Value): Future[Unit] = Future { underlying.put(key, value) }
}

object SynchronizedLruMap {
  @inline def apply[Key<:Object, Value<:Object]
                   (concurrencyLevel: Int=4)
                   (implicit ec: scala.concurrent.ExecutionContext): SynchronizedLruMap[Key, Value] =
    new SynchronizedLruMap[Key, Value](concurrencyLevel) { }
}

