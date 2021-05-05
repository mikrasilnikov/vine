
import argonaut._
import Argonaut._
import argonaut.ArgonautScalaz.jsonObjectPL
import argonaut.JsonScalaz.{jObjectPL, jStringPL}

val jsonString: Json       = jString("JSON!")
val jsonEmptyString: Json  = jEmptyString
val jsonNumber: Json       = jNumber(20)

val jsonArray: Json = Json.array(jsonNumber, jsonString)
val jsonArrayAlternative: Json = jArray(List(jsonNumber, jsonString))

val jsonObject: Json = Json("key1" -> jsonNumber, "key2" -> jsonString)
val jsonObjectExplicit: Json = Json.obj("key1" -> jsonNumber, "key2" -> jsonString)
val jsonObjectAlternative: Json = jObjectAssocList(List("key1" -> jsonNumber, "key2" -> jsonString))

val jsonArrayBuilder: Json = jsonString -->>: jsonNumber -->>: jEmptyArray
val jsonObjectBuilder: Json = ("key1", jsonNumber) ->: ("key2", jsonString) ->: jEmptyObject

val jsonObjectWithCodec: Json = Json("key1" := 3, "key2" := "hello")
val jsonObjectExplicitWithCodec: Json = Json.obj("key1" := 3, "key2" := "hello")
val jsonObjectBuilderWithCodec: Json = ("key1" := 3) ->: ("key2" := "hello") ->: jEmptyObject

val jsonObjectWithCodecAndOptionals: Json = ("key1" := 3) ->: ("option" :=? (None:Option[String])) ->?: jEmptyObject
val jsonObjectWithCodecAndNullFields: Json = ("key1" := 3) ->: ("option" := (None:Option[String])) ->: jEmptyObject

val appendedString: Json = jString("JSO").withString(_ + "N")
val modifiedObject: Json = jSingleObject("field", jTrue).withObject(_ - "field")
val modifiedArray: Json = jSingleArray(jTrue).withArray(_ :+ jFalse :+ jString("hello"))

val numberAccess: Option[JsonNumber] = jNumber(20).number
val booleanAccess: Option[Boolean] = jTrue.bool
val stringAccess: Option[String] = jTrue.string
val arrayAccess: Option[List[Json]] = jSingleArray(jTrue).array
val objectAccess: Option[JsonObject] = jSingleObject("field", jTrue).obj

val nestedObjectAccess: Option[Json] =
  jSingleObject("field", jSingleObject("nested", jTrue)) -|| List("field", "nested")

jStringPL.get(jsonString)

val innerObject: Json =
  ("innerkey1", jString("innervalue1")) ->:
  ("innerkey2", jString("innervalue2")) ->:
  jEmptyObject
val complexObject: Json =
  ("outerkey1", innerObject) ->:
  ("outerkey2", jFalse) ->:
  jEmptyObject

val innerKey2StringLens = jObjectPL >=>   // Lens composition starts with converting to object...
  jsonObjectPL("outerkey1") >=>           // ...Looking up the "outerkey1" field...
  jObjectPL >=>                           // ...Converting to an object...
  jsonObjectPL("innerkey2") >=>           // ...Looking up the "innerkey2" field...
  jStringPL                               // ...Converting to a string.

innerKey2StringLens.get(complexObject)
innerKey2StringLens.mod(_ + " is innervalue2", complexObject)

val json = """
    { "name" : "Toddler", "age" : 2, "greeting": "gurgle!" }
  """

val greeting1: String =
  Parse.parseWith(json, _.field("greeting").flatMap(_.string).getOrElse("Hello!"), msg => msg)

case class Person(name: String, age: Int, greeting: String)
implicit def PersonCodecJson: CodecJson[Person] = casecodec3(Person.apply, Person.unapply)("name", "age", "greeting")
val option: Option[Person] = Parse.decodeOption[Person](json)
val result2: Either[Either[String, (String, CursorHistory)], Person] = Parse.decode[Person](json)