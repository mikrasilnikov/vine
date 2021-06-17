package vine.config

import vine.config.ConfigModel._


case class SourcesConfig(
   previewsFolder : String,
   filtersConfig  : FiltersConfig,
   feeds          : List[Feed])
