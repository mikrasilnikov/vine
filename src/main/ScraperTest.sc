import io.circe.{Decoder, HCursor}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import net.ruippeixotog.scalascraper.model._

import java.time.LocalDate

val xml = "<root>\n<data>\n<![CDATA[ [{title_id: \"1579866\", track_id: \"8750747\", artist: [[515003, 1, \"Daniele Andriani\", \"daniele-andriani\"], [614750, 1, \"Cosimo Sasso\", \"cosimo-sasso\"]], title: \"Hey Mr Dj\", title_url: \"/title/1579866/hey-mr-dj\", track_url: \"/track/8750747/hey-mr-dj\", label: [57684, \"Famillia Recordings\", \"famillia-recordings\"], genre: \"Tech House\", genre_url: \"/genre/18/tech-house\", catnumber: \"FR011\", promo: true, duration: \"6:42\", r_date: \"2021-05-14\", price: {hbr: 2.49, wav: 3.24}, preorder: 0, bought: false, image: \"https://geo-static.traxsource.com/files/images/b1ad3731707278bcea1c66fccd29a939.jpg\", thumb: \"https://geo-static.traxsource.com/scripts/image.php/52x52/c225c03a1f1272f791a7f349c7d8c000.jpg\", mp3: \"https://geo-preview.traxsource.com/files/previews/57684/fecc9926243dc3ad012fc7f9547f13d2.mp3?ps=182\", waveform: \"https://geo-static.traxsource.com/waveform/preview/8750747/182\", bpm: \"126\", keysig: \"Fmin\"}] ]]>\n</data>\n</root>"

val parsed = scala.xml.XML.loadString(xml)

val json = (parsed \ "data").head.text


