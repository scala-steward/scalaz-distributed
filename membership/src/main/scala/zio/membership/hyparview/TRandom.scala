package zio.membership.hyparview

import zio._
import zio.stm._
import zio.macros.delegate._
import com.github.ghik.silencer.silent
import zio.random.Random

import scala.collection.immutable.{ Stream => ScStream }
import zio.macros.delegate._

trait TRandom {
  val tRandom: TRandom.Service
}

object TRandom {

  trait Service {
    def selectOne[A](values: List[A]): STM[Nothing, Option[A]]
    def selectN[A](values: List[A], n: Int): STM[Nothing, List[A]]
  }

  val withTRandom = enrichWithM[TRandom](make)

  def using[R <: TRandom, E, A](f: Service => ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.environment[TRandom].flatMap(env => f(env.tRandom))

  def make: ZIO[Random, Nothing, TRandom] = {
    @silent("deprecated")
    val makePickRandom: ZIO[Random, Nothing, Int => STM[Nothing, Int]] =
      for {
        seed    <- random.nextInt
        sRandom = new scala.util.Random(seed)
        ref     <- TRef.make(ScStream.continually((i: Int) => sRandom.nextInt(i))).commit
      } yield (i: Int) => ref.modify(s => (s.head(i), s.tail))
    makePickRandom.map { pickRandom =>
      new TRandom {
        val tRandom = new Service {
          override def selectOne[A](values: List[A]): STM[Nothing, Option[A]] =
            if (values.isEmpty) STM.succeed(None)
            else {
              for {
                index    <- pickRandom(values.size)
                selected = values(index)
              } yield Some(selected)
            }

          override def selectN[A](values: List[A], n: Int): STM[Nothing, List[A]] =
            if (values.isEmpty) STM.succeed(Nil)
            else {
              def go(remaining: Vector[A], toPick: Int, acc: Vector[A]): STM[Nothing, Vector[A]] =
                (remaining, toPick) match {
                  case (Vector(), _) | (_, 0) => STM.succeed(acc)
                  case _ =>
                    pickRandom(remaining.size).flatMap { index =>
                      val x  = remaining(index)
                      val xs = remaining.patch(index, Nil, 1)
                      go(xs, toPick - 1, x +: acc)
                    }
                }
              go(values.toVector, n, Vector()).map(_.toList)
            }
        }
      }
    }
  }
}
