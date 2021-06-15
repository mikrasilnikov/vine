package pd2.config

import pd2.config.ConfigModel._

case class Genre(name : String, feeds : List[Feed])

case class GenresConfig(
  previewsFolder  : String,
  filtersConfig   : FiltersConfig,
  genres          : List[Genre])
