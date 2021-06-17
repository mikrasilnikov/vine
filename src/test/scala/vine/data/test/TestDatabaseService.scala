package vine.data.test

import izumi.reflect.Tag
import vine.data.VineDatabaseImpl
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.{JdbcBackend, JdbcProfile}
import zio.{Has, Semaphore, ZIO, ZLayer}
import scala.concurrent.ExecutionContext.global

object TestDatabaseService {
  def makeLayer : ZLayer[Has[JdbcBackend#Database], Throwable, Has[VineDatabaseImpl]] = {
    val make = ZIO.service[JdbcBackend#Database].flatMap { backend =>
      for {
        semaphore <- Semaphore.make(1)
      } yield VineDatabaseImpl(slick.jdbc.H2Profile, backend, semaphore)
    }

    make.flatMap { db =>
      import db.profile.api._
      db.run {
        db.tracks.schema.create
      }.as(db)
    }.toLayer
  }
}