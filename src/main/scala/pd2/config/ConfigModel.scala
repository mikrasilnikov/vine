package pd2.config


object ConfigModel {

  case class FiltersConfig(
    my        : FilterConfig.My,
    noShit    : FilterConfig.NoShit,
    noEdits   : FilterConfig.NoEdits)

  sealed trait FeedTag
  object FeedTag {
    case object BeatportFeed extends FeedTag
    case object TraxsourceFeed extends FeedTag
  }

  sealed trait FilterConfig
  object FilterConfig {
    case class My(artistsFile: String, labelsFile: String) extends FilterConfig
    case class NoShit(dataFiles: List[String]) extends FilterConfig
    case class NoEdits(minTrackDurationSeconds: Int) extends FilterConfig
  }

  sealed trait FilterTag
  object FilterTag {
    case object My              extends FilterTag
    case object IgnoredLabels   extends FilterTag
    case object NoEdits         extends FilterTag
    case object Empty           extends FilterTag
  }

  final case class Feed(
    tag: FeedTag,
    name: String,
    urlTemplate: String,
    filterTags: List[FilterTag],
    explicitPriority : Option[Int])
  {
    /** Приоритет для скачивания. Параллельно скачиваются только фиды, имеющие одинаковый приоритет. */
    val priority : Int = explicitPriority match {
      case Some(p) => p
      case None =>
        val prefix = raw"(\d+).+".r
        name match {
          case prefix(s) => s.toInt
          case _ => Int.MaxValue
        }
    }

    /** Зависят ли результаты от диапазона дат. Например, у топа выборка от дат не зависит. */
    val dependsOnDate : Boolean = urlTemplate.contains("{0}") && urlTemplate.contains("{1}")
  }
}
