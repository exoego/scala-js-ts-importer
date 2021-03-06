package org.scalajs.tools.tsimporter

import io.scalajs.nodejs.fs.Fs
import org.scalatest.funspec.AnyFunSpec

class ImporterConfigSpec extends AnyFunSpec {

  private def contentOf(file: String) = Fs.readFileSync(file, "utf-8")

  describe("generateFactory") {
    it("should generate factory with trailing comma") {
      val generateFactory = GenerateFactoryType.generate
      val sourceTypeScript = s"samples/config/generateFactory/${generateFactory}.d.ts"
      val expectedContent = contentOf(sourceTypeScript + ".scala")
      val outputContent = Kicker.translate(new Input(
        source = contentOf(sourceTypeScript),
        outputPackage = "factory",
        generateFactory = generateFactory,
      ))
      assert(!outputContent.hasError, s"hasError: ${ outputContent.text }")
      assert(outputContent.text === expectedContent)
    }

    it("should generate factory with NO trailing comma") {
      val generateFactory = GenerateFactoryType.generateNoTrailingComma
      val sourceTypeScript = s"samples/config/generateFactory/${generateFactory}.d.ts"
      val expectedContent = contentOf(sourceTypeScript + ".scala")
      val outputContent = Kicker.translate(new Input(
        source = contentOf(sourceTypeScript),
        outputPackage = "factory",
        generateFactory = generateFactory,
      ))
      assert(!outputContent.hasError, s"hasError: ${ outputContent.text }")
      assert(outputContent.text === expectedContent)
    }

    it("should NOT generate factory") {
      val generateFactory = GenerateFactoryType.donot
      val sourceTypeScript = s"samples/config/generateFactory/${generateFactory}.d.ts"
      val expectedContent = contentOf(sourceTypeScript + ".scala")
      val outputContent = Kicker.translate(new Input(
        source = contentOf(sourceTypeScript),
        outputPackage = "factory",
        generateFactory = generateFactory,
      ))
      assert(!outputContent.hasError, s"hasError: ${ outputContent.text }")
      assert(outputContent.text === expectedContent)
    }
  }
  
  describe("interface-variables") {
    it("js-native implemented") {
      val sourceTypeScript = "samples/config/interface-variables/source.d.ts"
      val expectedContent = contentOf("samples/config/interface-variables/implemented.scala")
      val outputContent = Kicker.translate(new Input(
        source = contentOf(sourceTypeScript),
        outputPackage = "source",
        interfaceImplementation = InterfaceImplementation.implemented
      ))
      assert(!outputContent.hasError, s"hasError: ${ outputContent.text }")
      assert(outputContent.text === expectedContent)
    }
    
    it("abstract") {
      val sourceTypeScript = "samples/config/interface-variables/source.d.ts"
      val expectedContent = contentOf("samples/config/interface-variables/abstract.scala")
      val outputContent = Kicker.translate(new Input(
        source = contentOf(sourceTypeScript),
        outputPackage = "source",
        interfaceImplementation = InterfaceImplementation.`abstract`
      ))
      assert(!outputContent.hasError, s"hasError: ${ outputContent.text }")
      assert(outputContent.text === expectedContent)
    }
  }

  describe("type alias enum") {
    it("js-native implemented") {
      val sourceTypeScript = "samples/config/type-alias-enum/source.d.ts"
      val expectedContent = contentOf("samples/config/type-alias-enum/generate.scala")
      val outputContent = Kicker.translate(new Input(
        source = contentOf(sourceTypeScript),
        outputPackage = "source",
        generateTypeAliasEnums = GenerateTypeAliasEnums.generate
      ))
      assert(!outputContent.hasError, s"hasError: ${ outputContent.text }")
      assert(outputContent.text === expectedContent)
    }

  }
}
