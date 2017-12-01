package com.snowplowanalytics.maxmind.iplookups

import java.net.InetAddress

import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.AddressNotFoundException
import com.snowplowanalytics.maxmind.iplookups.ReaderFunctions.ReaderFunction

trait GeoIp2Db
object Isp extends GeoIp2Db
object Org extends GeoIp2Db
object Domain extends GeoIp2Db
object NetSpeed extends GeoIp2Db

case class SpecializedReader(db: DatabaseReader, f: ReaderFunction) {
  def getValue(ip: InetAddress): Option[String] = try {
    Option(f(db, ip))
  } catch {
    case e: AddressNotFoundException => None
  }
}

object ReaderFunctions {
  type ReaderFunction = (DatabaseReader, InetAddress) => String

  val isp = (db: DatabaseReader, ip: InetAddress) => db.isp(ip).getIsp
  val org = (db: DatabaseReader, ip: InetAddress) => db.isp(ip).getOrganization
  val domain = (db: DatabaseReader, ip: InetAddress) => db.domain(ip).getDomain
  val netSpeed = (db: DatabaseReader, ip: InetAddress) => db.connectionType(ip).getConnectionType.toString
}
