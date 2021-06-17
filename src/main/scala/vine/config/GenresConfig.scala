package vine.config

import vine.config.ConfigModel._

case class Genre(name : String, feeds : List[Feed])

case class GenresConfig(
  previewsFolder  : String,
  filtersConfig   : FiltersConfig,
  genres          : List[Genre])
