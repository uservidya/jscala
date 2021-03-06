package org

import javax.script.ScriptEngineManager
import com.yahoo.platform.yui.compressor.JavaScriptCompressor
import java.io.{StringWriter, StringReader}
import org.mozilla.javascript.ErrorReporter
import scala.annotation.StaticAnnotation

package object jscala {
  import language.experimental.macros
  import language.implicitConversions
  import scala.reflect.macros.Context

  private lazy val engine = {
    val factory = new ScriptEngineManager(null)
    factory.getEngineByName("JavaScript")
  }

  implicit class JsAstOps(ast: JsAst) {

    def asString = JavascriptPrinter.print(ast, 0)
    def eval() = engine.eval(asString)
    def compress = {
      val compressor = new JavaScriptCompressor(new StringReader(asString), new ErrorReporter {
        def warning(p1: String, p2: String, p3: Int, p4: String, p5: Int) {
          println(s"Warn $p1 $p2, ${p3.toString} $p4 ${p5.toString}")
        }

        def error(p1: String, p2: String, p3: Int, p4: String, p5: Int) {
          println(s"Error $p1 $p2, ${p3.toString} $p4 ${p5.toString}")
        }

        def runtimeError(p1: String, p2: String, p3: Int, p4: String, p5: Int) = {
          println(s"Runtime $p1 $p2, ${p3.toString} $p4 ${p5.toString}")
          ???
        }
      })
      val buf = new StringWriter
      compressor.compress(buf, 1, true, false, false, false)
      buf.toString
    }
  }

  implicit class JsAnyOps(a: Any) {
    def as[A] = a.asInstanceOf[A]
  }

  implicit def implicitString2JString(s: String): JString = new JString(s)
  implicit def implicitJString2String(s: JString): String = ""
  implicit def implicitArray2JArray[A](s: Array[A]): JArray[A] = ???
  implicit def implicitJArray2Array[A](s: JArray[A]): Array[A] = ???
  implicit def implicitSeq2JArray[A](s: Seq[A]): JArray[A] = ???
  implicit def implicitJArray2Seq[A](s: JArray[A]): Seq[A] = ???

  trait JsSerializer[A] {
    def apply(a: A): JsExpr
  }
  implicit object boolJsSerializer extends JsSerializer[Boolean] { def apply(a: Boolean) = JsBool(a) }
  implicit object byteJsSerializer extends JsSerializer[Byte] { def apply(a: Byte) = JsNum(a, false) }
  implicit object shortJsSerializer extends JsSerializer[Short] { def apply(a: Short) = JsNum(a, false) }
  implicit object intJsSerializer extends JsSerializer[Int] { def apply(a: Int) = JsNum(a, false) }
  implicit object longJsSerializer extends JsSerializer[Long] { def apply(a: Long) = JsNum(a, false) }
  implicit object floatJsSerializer extends JsSerializer[Float] { def apply(a: Float) = JsNum(a, true) }
  implicit object doubleJsSerializer extends JsSerializer[Double] { def apply(a: Double) = JsNum(a, true) }
  implicit object stringJsSerializer extends JsSerializer[String] { def apply(a: String) = JsString(a) }
  implicit def arrJsSerializer[A](implicit ev: JsSerializer[A]): JsSerializer[Array[A]] =
    new JsSerializer[Array[A]] { def apply(a: Array[A]) = JsArray(a.map(ev.apply).toList) }
  implicit object seqJsSerializer extends JsSerializer[collection.Seq[JsExpr]] { def apply(a: collection.Seq[JsExpr]) = JsArray(a.toList) }
  implicit object mapJsSerializer extends JsSerializer[collection.Map[String,JsExpr]] { def apply(a: collection.Map[String,JsExpr]) = JsAnonObjDecl(a.toList) }
  implicit def funcJsSerializer[A](implicit ev: JsSerializer[A]): JsSerializer[() => A] = new JsSerializer[() => A] { def apply(a: () => A) = ev.apply(a()) }
  implicit class ToJsExpr[A](a: A)(implicit ev: JsSerializer[A]) {
    def toJs: JsExpr = ev.apply(a)
  }

  def varDef(ident: String, init: JsExpr = JsUnit) = JsVarDef(List(ident -> init))

  // Javascript top-level functions/constants
  val Infinity = Double.PositiveInfinity
  val NaN = Double.NaN
  val undefined: AnyRef = null
  // Javascript Global Functions
  def decodeURI(uri: String): JString = null
  def decodeURIComponent(uri: String): JString = null
  def encodeURI(uri: String): JString = null
  def encodeURIComponent(uri: String): JString = null
  def escape(str: String): JString = null
  def unescape(str: String): JString = null
  def eval(str: String): AnyRef = null
  def isFinite(x: Any) = false
  def isNaN(x: Any) = false
  def parseFloat(str: String) = str.toDouble
  def parseInt(str: String, base: Int = 10) = java.lang.Integer.parseInt(str, base)
  def typeof(x: Any) = ""
  def include(js: String) = ""
  def print(x: Any) {
    System.out.println(x)
  }

  /**
   * Scala/JavaScript implementation of for..in
   *
   * {{{
   *   val coll = Seq("a", "b")
   *   forIn(coll)(ch => print(ch))
   * }}}
   * translates to
   * var coll = ["a", "b"];
   * for (var ch in coll) print(ch);
   */
  def forIn[A](coll: Seq[A])(f: Int => Unit) = {
    var idx = 0
    val len = coll.length
    while (idx < len) {
      f(idx)
      idx += 1
    }
  }

  /**
   * Scala/JavaScript implementation of for..in
   *
   * {{{
   *   val coll = Map("a" -> 1, "b" -> 2)
   *   forIn(coll)(ch => print(ch))
   * }}}
   * translates to
   * var coll = {"a": 1, "b": 2};
   * for (var ch in coll) print(ch);
   */
  def forIn[A, B](map: Map[A, B])(f: A => Unit) = {
    map.keysIterator.foreach(k => f(k))
  }

  /**
   * Scala/JavaScript implementation of for..in
   *
   * {{{
   *   val obj = new { val a = 1 }
   *   forIn(obj)(ch => print(ch))
   * }}}
   * translates to
   * var obj = {"a": 1};
   * for (var ch in obj) print(ch);
   * @note Doesn't work in Scala!
   */
  def forIn[A, B](obj: AnyRef)(f: String => Unit) = ???

  def inject(a: JsAst) = ???
  /**
   * Injects a value into generated JavaScript using JsSerializer
   */
  def inject[A](a: A)(implicit jss: JsSerializer[A]) = a

  /**
   * Macro that generates JavaScript AST representation of its argument
   */
  def ajax[A, B](input: A)(server: A => B)(callback: B => Unit): JsAst = ???

  /**
   * Macro that generates JavaScript AST representation of its argument
   */
  def javascript(expr: Any): JsAst = macro Macros.javascriptImpl
  /**
   * Macro that generates JavaScript String representation of its argument
   */
  def javascriptString(expr: Any): String = macro Macros.javascriptStringImpl
  def javascriptDebug(expr: Any): JsAst = macro Macros.javascriptDebugImpl

  def toJson[A](expr: Any): JsAst = macro Macros.toJsonImpl
  def toJson1[A]: JsExpr = macro Macros.toJsonImpl1[A]
  def fromJson[A](s: String): A = macro Macros.fromJsonImpl[A]

  object Macros {
    def javascriptImpl(c: Context)(expr: c.Expr[Any]): c.Expr[JsAst] = {
      val parser = new ScalaToJsConverter[c.type](c, debug = false)
      parser.convert(expr.tree)
    }
    def javascriptStringImpl(c: Context)(expr: c.Expr[Any]): c.Expr[String] = {
      import c.universe._
      val parser = new ScalaToJsConverter[c.type](c, debug = false)
      val jsAst = parser.convert(expr.tree)
      val strAst = reify { new JsAstOps(jsAst.splice).asString }
      c.literal(c.eval(strAst))
    }
    def javascriptDebugImpl(c: Context)(expr: c.Expr[Any]): c.Expr[JsAst] = {
      val parser = new ScalaToJsConverter[c.type](c, debug = true)
      parser.convert(expr.tree)
    }
    def toJsonImpl(c: Context)(expr: c.Expr[Any]): c.Expr[JsAst] = {
      val converter = new JsonConverter[c.type](c, debug = true)
      converter.toJson(expr.tree)
    }

    def toJsonImpl1[A: c.WeakTypeTag](c: Context): c.Expr[JsExpr] = {
      val converter = new JsonConverter[c.type](c, debug = true)
      converter.toJson1(c.weakTypeOf[A])
    }

    def fromJsonImpl[A: c.WeakTypeTag](c: Context)(s: c.Expr[String]): c.Expr[A] = {
      val converter = new JsonConverter[c.type](c, debug = true)
      converter.fromJson[A](s)
    }
  }
}


