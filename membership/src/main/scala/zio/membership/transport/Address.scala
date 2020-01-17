package zio.membership.transport

import zio.IO
import zio.nio.SocketAddress
import zio.nio.InetSocketAddress
import zio.membership.ResolutionFailed

final case class Address(
  host: String,
  port: Int
) { self =>

  val toInetSocketAddres: IO[ResolutionFailed, InetSocketAddress] =
    SocketAddress.inetSocketAddress(host, port).mapError(ResolutionFailed(self, _))

  override def toString() =
    s"$host:$port"

}
