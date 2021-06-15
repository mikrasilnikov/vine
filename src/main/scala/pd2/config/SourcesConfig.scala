package pd2.config

import pd2.config.ConfigModel._


case class SourcesConfig(
   previewsFolder : String,
   filtersConfig  : FiltersConfig,
   feeds          : List[Feed])
