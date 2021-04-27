package pd2

object Application {

  def main(args: Array[String]): Unit = {
    println("main")
  }

  /*
  val tracks = TableQuery[TrackTable]

  //val db = Database.forConfig("chapter01")
  val db = Database.forURL("jdbc:sqlite:d:\\_temp\\testdb.db")

  def main(args: Array[String]): Unit = {

    val dataPath = "c:\\Music-Sly\\PreviewsDownloader\\data\\tracks\\";

    val newTracks = Files.list(Path.of(dataPath))
      .flatMap(path => Files.lines(path))
      .toScala(List)
      .map(line => line.split('\t'))
      .map { parts =>
        if (parts.length == 5)
          Track(0, parts(0), parts(1), parts(2),
            LocalDate.parse(parts(3), DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            parts(4))
        else
          Track(0, parts(0), parts(1), "", LocalDate.MIN, "")
      }

    println(s"loaded ${newTracks.length} lines")

    val create = tracks.schema.create
    val insert = tracks ++= newTracks

    Await.result(db.run(create >> insert), Inf)

  }*/
}
