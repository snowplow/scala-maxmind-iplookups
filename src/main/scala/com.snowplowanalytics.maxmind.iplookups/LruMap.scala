/*
 * Copyright (c) 2012-2018 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.maxmind.iplookups

import cats.effect.Sync
import java.{util => ju}
import java.util.LinkedHashMap
import scala.collection.JavaConverters._
import scala.collection.mutable.{Map, MapLike, SynchronizedMap}

// Based on com.twitter.util.LruMap
// https://github.com/twitter/util/blob/develop/util-collection/src/main/scala/com/twitter/util/LruMap.scala

/**
 * A wrapper trait for java.util.Map implementations to make them behave as scala Maps.
 * This is useful if you want to have more specifically-typed wrapped objects instead
 * of the generic maps returned by JavaConverters
 */
trait JMapWrapperLike[A, B, +Repr <: MapLike[A, B, Repr] with Map[A, B]]
    extends Map[A, B]
    with MapLike[A, B, Repr] {
  def underlying: ju.Map[A, B]

  override def size = underlying.size

  override def get(k: A) = underlying.asScala.get(k)

  override def +=(kv: (A, B)): this.type = { underlying.put(kv._1, kv._2); this }
  override def -=(key: A): this.type     = { underlying remove key; this }

  override def put(k: A, v: B): Option[B] = underlying.asScala.put(k, v)

  override def update(k: A, v: B): Unit = underlying.put(k, v)

  override def remove(k: A): Option[B] = underlying.asScala.remove(k)

  override def clear() = underlying.clear()

  override def empty: Repr = null.asInstanceOf[Repr]

  override def iterator = underlying.asScala.iterator
}

object LruMap {
  def create[F[_]: Sync, K, V](
    size: Int
  ): F[LruMap[K, V]] = implicitly[Sync[F]].delay {
    new LruMap[K, V](size)
  }

  def put[F[_]: Sync, K, V](
    lruMap: LruMap[K, V],
    key: K,
    value: V
  ): F[Unit] = implicitly[Sync[F]].delay {
    lruMap.put(key, value)
  }

  def get[F[_]: Sync, K, V](lruMap: LruMap[K, V], key: K): F[Option[V]] =
    implicitly[Sync[F]].delay {
      lruMap.get(key)
    }

  // initial capacity and load factor are the normal defaults for LinkedHashMap
  def makeUnderlying[K, V](maxSize: Int): ju.Map[K, V] =
    new LinkedHashMap[K, V](
      16, /* initial capacity */
      0.75f, /* load factor */
      true /* access order (as opposed to insertion order) */
    ) {
      override protected def removeEldestEntry(eldest: ju.Map.Entry[K, V]): Boolean =
        this.size() > maxSize
    }
}

/**
 * A scala `Map` backed by a [[java.util.LinkedHashMap]]
 */
class LruMap[K, V](val maxSize: Int, val underlying: ju.Map[K, V])
    extends JMapWrapperLike[K, V, LruMap[K, V]] {
  override def empty: LruMap[K, V] = new LruMap[K, V](maxSize)
  def this(maxSize: Int) = this(maxSize, LruMap.makeUnderlying(maxSize))
}
