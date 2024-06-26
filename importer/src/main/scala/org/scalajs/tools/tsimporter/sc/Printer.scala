/* TypeScript importer for Scala.js
 * Copyright 2013-2014 LAMP/EPFL
 * @author  Sébastien Doeraene
 */

package org.scalajs.tools.tsimporter.sc

import java.io.PrintWriter

import org.scalajs.tools.tsimporter.Config
import org.scalajs.tools.tsimporter.Trees.Modifier

class Printer(private val output: PrintWriter, config: Config) {
  import Printer._

  private val outputPackage = config.packageName

  private implicit val self = this

  private var currentJSNamespace = ""
  
  private val traitFactoryBuffer = scala.collection.mutable.Map.empty[Name, List[FieldSymbol]]
  
  def printSymbol(sym: Symbol, enclosing: Option[Symbol]): Unit = {
    val name = sym.name
    sym match {
      case comment: CommentSymbol =>
        pln"/* ${comment.text} */"

      case sym: PackageSymbol =>
        val isRootPackage = name == Name.EMPTY

        val parentPackage :+ thisPackage =
          if (isRootPackage) outputPackage.split("\\.").toList.map(Name(_))
          else List(name)

        if (!parentPackage.isEmpty) {
          pln"package ${parentPackage.mkString(".")}"
          pln"";
        }

        if (isRootPackage) {
          pln"import scala.scalajs.js"
          pln"import js.annotation._"
          pln"import js.|"
        }

        val oldJSNamespace = currentJSNamespace
        if (!isRootPackage)
          currentJSNamespace += name.name + "."

        if (!sym.members.isEmpty) {
          val (topLevels, packageObjectMembers) =
            sym.members.partition(canBeTopLevel)

          pln"";
          pln"package $thisPackage {"

          for (sym <- topLevels)
            printSymbol(sym, enclosing)

          if (!packageObjectMembers.isEmpty) {
            val packageObjectName =
              Name(s"${thisPackage.name.head.toUpper}${thisPackage.name.tail}")

            pln"";
            if (currentJSNamespace.isEmpty) {
              pln"@js.native"
              pln"@JSGlobalScope"
              pln"object $packageObjectName extends js.Object {"
            } else {
              val jsName = currentJSNamespace.init
              pln"@js.native"
              pln"""@JSGlobal("$jsName")"""
              pln"object $packageObjectName extends js.Object {"
            }
            val packageObject = sym
            for (sym <- packageObjectMembers)
              printSymbol(sym, Some(packageObject))
            pln"}"
          }

          pln"";
          pln"}"
        }

        currentJSNamespace = oldJSNamespace

      case sym: ClassSymbol =>
        val sealedKw = if (sym.isSealed) "sealed " else ""
        val abstractKw = if (sym.isAbstract && !sym.isTrait) "abstract " else ""
        val kw = if (sym.isTrait) "trait" else "class"
        val constructorStr =
          if (sym.isTrait) ""
          else if (sym.members.exists(isParameterlessConstructor)) ""
          else " protected ()"
        val parents =
          if (sym.parents.isEmpty) List(TypeRef.Object)
          else sym.parents.toList

        pln"";
        if (!sym.isAbstractTrait()) {
          pln"@js.native"
        }
        if (!sym.isTrait) {
          if (currentJSNamespace.isEmpty)
            pln"@JSGlobal"
          else
            pln"""@JSGlobal("$currentJSNamespace${name.name}")"""
        }
        p"$sealedKw$abstractKw$kw $name"
        if (!sym.tparams.isEmpty)
          p"[${sym.tparams}]"

        {
          implicit val withSep = ListElemSeparator.WithKeyword
          pln"$constructorStr extends $parents {"
        }

        printMemberDecls(sym, bufferSymbol = sym.isTrait && config.factoryConfig.generateFactory)
        pln"}"

      case sym: ModuleSymbol =>
        pln"";
        if (sym.isGlobal) {
          pln"@js.native"
          if (currentJSNamespace.isEmpty)
            pln"@JSGlobal"
          else
            pln"""@JSGlobal("$currentJSNamespace${name.name}")"""
          pln"object $name extends js.Object {"
        } else {
          pln"object $name {"
        }
        printFactory(sym)
        printMemberDecls(sym, bufferSymbol = false)
        pln"}"

      case sym: TypeAliasSymbol =>
        p"  type $name"
        if (!sym.tparams.isEmpty)
          p"[${sym.tparams}]"
        pln" = ${sym.alias}"

      case sym: FieldSymbol =>
        val decl =
          if (sym.modifiers(Modifier.Const)) "val"
          else if (sym.modifiers(Modifier.ReadOnly)) "def"
          else "var"
        if (sym.needsOverride && decl == "var") {
          // var cannot be override, so should be omitted
        } else {
          sym.jsName foreach { jsName =>
            pln"""  @JSName("$jsName")"""
          }
          val access =
            if (sym.modifiers(Modifier.Protected)) "protected "
            else ""
          val modifiers =
            if (sym.needsOverride) "override " else ""

          val inline =
            if (sym.modifiers(Modifier.Inline)) "@inline "
            else ""

          p"  $inline$access$modifiers$decl $name: ${ sym.tpe }"

          if (sym.rhs.isDefined)
            p" = ${sym.rhs.get}"
          else if (!sym.modifiers(Modifier.Abstract))
            p" = js.native"
          pln""
        }
      case sym: MethodSymbol =>
        val params = sym.params

        if (name == Name.CONSTRUCTOR) {
          if (!params.isEmpty)
            pln"  def this($params) = this()"
        } else {
          sym.jsName foreach { jsName =>
            pln"""  @JSName("$jsName")"""
          }
          if (sym.isBracketAccess)
            pln"""  @JSBracketAccess"""

          val modifiers =
            if (sym.needsOverride) "override " else ""
          p"  ${modifiers}def $name"
          if (!sym.tparams.isEmpty)
            p"[${sym.tparams}]"
          if (sym.modifiers(Modifier.Set))
            p"_="
          p"($params): ${sym.resultType}"
          if (!sym.modifiers(Modifier.Abstract))
            p" = js.native"
          pln""
        }

      case sym: ParamSymbol =>
        p"$name: ${sym.tpe}${if (sym.optional) " = ???" else ""}"

      case sym: TypeParamSymbol =>
        p"$name"
        sym.upperBound.foreach(bound => p" <: $bound")
    }
  }

  private def printMemberDecls(owner: ContainerSymbol, bufferSymbol: Boolean): Unit = {
    val (constructors, others) =
      owner.members.toList.partition(_.name == Name.CONSTRUCTOR)
    if (bufferSymbol) {
      traitFactoryBuffer.update(owner.name, others.collect {
        case sym: FieldSymbol => sym
      })
    }
    for (sym <- constructors ++ others)
      printSymbol(sym, Some(owner))
  }
  
  private def printFactory(owner: ContainerSymbol): Unit = {
    traitFactoryBuffer.get(owner.name) match {
      case Some(others) if others.nonEmpty =>
        val (requiredProps, optionalProps) = others.partition(_.tpe.typeName != QualifiedName.UndefOr)
        val (nonNullableProps, nullableProps) = requiredProps.partition { prop =>
          !(prop.tpe.typeName == QualifiedName.Union && prop.tpe.targs.contains(TypeRef.Null))
        }
        val props =
          nonNullableProps.map(sym => (sym, "")) ++
          nullableProps.map(sym => (sym, (" = null"))) ++
          optionalProps.map(sym => (sym, (" = js.undefined")))
        traitFactoryBuffer.remove(owner.name)

        pln""
        pln"def apply("
        props.zipWithIndex.foreach { case ((sym, rhs), i) =>
          val comma = if (config.factoryConfig.useTrailingComma || i < props.length - 1) "," else ""
          pln"  ${sym.name}: ${sym.tpe}${rhs}${comma}"
        }
        pln"): ${owner.name} = {"

        pln"  val _obj$$ = js.Dynamic.literal("
        requiredProps.zipWithIndex.foreach { case (sym, i) =>
          val comma = if (config.factoryConfig.useTrailingComma || i < requiredProps.length - 1) "," else ""
          pln"""    "${sym.name.name}" -> ${sym.name}.asInstanceOf[js.Any]${comma}"""
        }
        pln"  )"
        for (sym <- optionalProps)
          pln"""  ${sym.name}.foreach(_v => _obj$$.updateDynamic("${sym.name.name}")(_v.asInstanceOf[js.Any]))"""
        pln"  _obj$$.asInstanceOf[${owner.name}]"
        pln"}"
      case _ => ()
    }
  }

  private def canBeTopLevel(sym: Symbol): Boolean =
    sym.isInstanceOf[ContainerSymbol]

  private def isParameterlessConstructor(sym: Symbol): Boolean = {
    sym match {
      case sym: MethodSymbol =>
        sym.name == Name.CONSTRUCTOR && sym.params.isEmpty
      case _ =>
        false
    }
  }

  def printTypeRef(tpe: TypeRef): Unit = {
    tpe match {
      case TypeRef(typeName, Nil) =>
        p"$typeName"

      case TypeRef.Union(types) if types.contains(TypeRef.Unit) =>
        implicit val withPipe = ListElemSeparator.Pipe
        p"js.UndefOr[${types.filterNot(_ == TypeRef.Unit)}]"

      case TypeRef.Union(types)  =>
        implicit val withPipe = ListElemSeparator.Pipe
        p"${types}"

      case TypeRef.Intersection(types) =>
        implicit val withWith = ListElemSeparator.WithKeyword
        p"$types"

      case TypeRef.This =>
        p"this.type"

      case TypeRef.Singleton(termRef) =>
        p"Any /* $termRef.type */"

      case TypeRef.Repeated(underlying) =>
        p"$underlying*"

      case TypeRef(QualifiedName.UndefOr, List(u @ TypeRef.Union(types))) if types.contains(TypeRef.Unit) =>
        p"$u"

      case TypeRef(typeName, targs) =>
        p"$typeName[$targs]"
    }
  }

  def printWildcard(wc: Wildcard): Unit = {
    wc match {
      case Wildcard(None) =>
        p"_"

      case Wildcard(Some(typeRefOrWildcard)) =>
        p"_ <: $typeRefOrWildcard"
    }
  }

  private def print(x: Any): Unit = {
    x match {
      case x: Symbol => printSymbol(x, null)
      case x: TypeRef => printTypeRef(x)
      case x: Wildcard => printWildcard(x)
      case QualifiedName(Name.scala, Name.scalajs, Name.js, name) =>
        output.print("js.")
        output.print(name)
      case QualifiedName(Name.scala, name) => output.print(name)
      case QualifiedName(Name.java, Name.lang, name) => output.print(name)
      case _ => output.print(x)
    }
  }
}

object Printer {
  private class ListElemSeparator(val s: String) extends AnyVal

  private object ListElemSeparator {
    val Comma = new ListElemSeparator(", ")
    val Pipe = new ListElemSeparator(" | ")
    val WithKeyword = new ListElemSeparator(" with ")
  }

  private implicit class OutputHelper(val sc: StringContext) extends AnyVal {
    def p(args: Any*)(implicit printer: Printer,
        sep: ListElemSeparator = ListElemSeparator.Comma): Unit = {
      val strings = sc.parts.iterator
      val expressions = args.iterator

      val output = printer.output
      output.print(strings.next())
      
      def printIterator(iter: Iterator[_]): Unit = {
        if (iter.hasNext) {
          printer.print(iter.next())
          while (iter.hasNext) {
            output.print(sep.s)
            printer.print(iter.next())
          }
        }
      }
      
      while (strings.hasNext) {
        expressions.next() match {
          case seq: scala.collection.immutable.Seq[_] =>
            val iter = seq.iterator
            printIterator(iter)
          case seq: scala.collection.mutable.Seq[_] =>
            val iter = seq.iterator
            printIterator(iter)
          case expr =>
            printer.print(expr)
        }
        output.print(strings.next())
      }
    }

    def pln(args: Any*)(implicit printer: Printer,
        sep: ListElemSeparator = ListElemSeparator.Comma): Unit = {
      p(args:_*)
      printer.output.println()
    }
  }
}
