import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import net.ruippeixotog.scalascraper.model._

import java.time.LocalDate

val domain = "https://traxsource.com"
val urlTemplate = "/just-added?cn=tracks&ipp=100&period={0},{1}" // 2021-05-02,2021-05-08
val date1 = LocalDate.of(2021, 5, 2)
val date2 = LocalDate.of(2021, 5, 8)

val uri = domain ++
  urlTemplate
    .replace("{0}", date1.toString)
    .replace("{1}", date2.toString)

val browser = JsoupBrowser()
val doc1 = browser.get("https://www.traxsource.com/just-added?cn=tracks&ipp=100&period=2021-05-02,2021-05-08")
val doc2 = browser.get("https://www.traxsource.com/just-added?cn=tracks&ipp=100&period=2021-05-02,2021-05-08&page=213")
val doc3 = browser.get("https://www.traxsource.com/just-added?cn=tracks&ipp=100&period=today&gf=4")
val doc4 = browser.get("https://www.traxsource.com/just-added?cn=tracks&ipp=100&period=today&gf=7")








parseTraxsourcePage(doc1)
parseTraxsourcePage(doc2)
parseTraxsourcePage(doc3)
parseTraxsourcePage(doc4)