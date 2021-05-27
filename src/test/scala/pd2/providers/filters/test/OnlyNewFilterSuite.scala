package pd2.providers.filters.test

import pd2.data.{DatabaseService, Track}
import pd2.data.test.{TestBackend, TestDatabaseService}
import zio.ZIO
import zio.test.Assertion.{anything, equalTo}
import zio.test.DefaultRunnableSpec
import zio.test.{DefaultRunnableSpec, assert}

object OnlyNewFilterSuite extends DefaultRunnableSpec {
  override def spec =
    suite("TraxsourceDataProviderSuite")(

      suite("Parsing")(
        testM("!") {

          val trackInDb = Track("Artist", "Title", "Artist - Title", None, None, None, None)

          val testDbLayer = TestDatabaseService.makeLayer { db =>
            import db.profile.api._
            db.tracks.schema.create >>
            (db.tracks += trackInDb)
          }

          val reader = for {
            actual <- ZIO.service[DatabaseService].flatMap { db =>
              import db.profile.api._
              db.run(db.tracks.take(1).result).map(_.head)
            }
          } yield assert(actual)(equalTo(trackInDb))

          reader.provideCustomLayer(TestBackend.makeLayer >>> testDbLayer)
        }
      )
    )
}
