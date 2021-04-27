package pd2.data

import slick.jdbc.SQLiteProfile.api._

object Schema {
  def createSchema(fileName: String): Unit = {
    val db = Database.forURL("jdbc:sqlite:d:\\_temp\\testdb.db")
  }

}
