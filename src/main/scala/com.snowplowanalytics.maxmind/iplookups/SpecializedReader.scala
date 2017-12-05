package com.snowplowanalytics.maxmind.iplookups

import java.net.InetAddress

import com.maxmind.geoip2.DatabaseReader
import com.snowplowanalytics.maxmind.iplookups.ReaderFunctions.ReaderFunction

import scala.util.Try

case class SpecializedReader(db: DatabaseReader, f: ReaderFunction) {
  def getValue(ip: InetAddress): Option[String] = Try(f(db,ip)).toOption
}

object ReaderFunctions {
  type ReaderFunction = (DatabaseReader, InetAddress) => String

  val isp = (db: DatabaseReader, ip: InetAddress) => db.isp(ip).getIsp
  val org = (db: DatabaseReader, ip: InetAddress) => db.isp(ip).getOrganization
  val domain = (db: DatabaseReader, ip: InetAddress) => db.domain(ip).getDomain
  val netSpeed = (db: DatabaseReader, ip: InetAddress) => db.connectionType(ip).getConnectionType.toString
}
