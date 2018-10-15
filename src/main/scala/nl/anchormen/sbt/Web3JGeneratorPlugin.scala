package nl.anchormen.sbt

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{FileVisitOption, Files, Path, Paths}
import java.util.regex.Pattern

import nl.anchormen.sbt.Web3JGeneratorPlugin.ContractType
import nl.anchormen.sbt.Web3JGeneratorPlugin.ContractType.ContractType
import org.web3j.codegen.{
  SolidityFunctionWrapper,
  TruffleJsonFunctionWrapperGenerator
}
import sbt.Keys._
import sbt.{AutoPlugin, Def, PluginTrigger, _}

import scala.collection.JavaConverters._

object Web3JGeneratorPlugin extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  object ContractType {
    sealed trait ContractType
    object Solidity extends ContractType
    object Truffle extends ContractType
  }

  import ContractType._

  object autoImport {
    lazy val web3JGenerateWrapper =
      taskKey[Seq[File]]("Generate Java wrapper classes")
    lazy val web3JOutputPath =
      settingKey[File]("The directory to output the generated classes")
    lazy val web3JContractsPath =
      settingKey[File]("The directory containing the smart contracts")
    lazy val web3JUseJavaNativeTypes =
      settingKey[Boolean]("Use Java native types or Solidity types")
    lazy val web3JContractTypes = settingKey[ContractType](
      "Type of sources ContractType.Solidity or ContractType.Truffle")
    lazy val web3JDefaultPackageName =
      settingKey[Option[String]]("Default package of the generated code")
  }

  import autoImport._

  lazy val pluginSettings: Seq[Setting[_]] = Seq(
    web3JOutputPath := baseDirectory.value / "src" / "main" / "java",
    web3JContractsPath := baseDirectory.value / "src" / "main" / "contracts",
    web3JUseJavaNativeTypes := true,
    web3JContractTypes := ContractType.Solidity,
    web3JDefaultPackageName := None
  )

  override lazy val projectSettings: Seq[Setting[_]] = inConfig(Compile)(
    sourceGenerators in Compile += Def.task {
      lazy val sources = generateWrapper.value
      streams.value.log.info(
        "Generated sources:\r\n" + sources.mkString("\r\n"))
      sources
    }.taskValue
  ) ++ pluginSettings

  lazy val generateWrapper: Def.Initialize[Task[Seq[File]]] = Def.task {
    Generate(web3JContractTypes.value,
             web3JContractsPath.value,
             web3JUseJavaNativeTypes.value,
             web3JOutputPath.value,
             web3JDefaultPackageName.value)
  }
}

private object Generate {

  /**
	  * Generate Java classes from smart contract files
	  *
	  * @param outputPath The path to output the generated classes
	  * @param smartContracts Directory containing *.bin and *.abi files to be converted
	  * @param useJavaNativeTypes Use java native types in the generated classes if set to true, use Solidity types if
	  *                           set to false
	  */
  def apply(contractType: ContractType,
            smartContracts: File,
            useJavaNativeTypes: Boolean,
            outputPath: File,
            optDefaultPackageName: Option[String]): Seq[File] = {
    contractType match {
      case ContractType.Solidity =>
        val contractFiles = AbiBin.findList(smartContracts)
        process(contractFiles, useJavaNativeTypes, outputPath)
      case ContractType.Truffle =>
        processTruffle(smartContracts,
                       useJavaNativeTypes,
                       outputPath,
                       optDefaultPackageName)

    }

  }

  private def processTruffle(
      smartContracts: File,
      useJavaNativeTypes: Boolean,
      outputPath: File,
      optDefaultPackageName: Option[String]): Seq[File] = {
    val packageNodes =
      optDefaultPackageName.map(pn => pn.split(Pattern.quote(".")).toList)
    val inputSeqFiles = Files
      .walk(smartContracts.toPath.toAbsolutePath, FileVisitOption.FOLLOW_LINKS)
      .iterator()
      .asScala
      .toSeq
    val filteredInputSeqFiles = inputSeqFiles
      .collect {
        case p: Path if p.getFileName.toString.endsWith(".json") && !p.getFileName.toString.endsWith("Migrations.json") =>
          p
      }
    val generatedOutputFile = filteredInputSeqFiles.map { (scPath: Path) =>
      val firstArg =
        if (useJavaNativeTypes) "--javaTypes" else "-- solidityTypes"
      val file: String = scPath
        .getName(scPath.getNameCount - 1)
        .toString
        .replace(".json", ".java")
      val outputFile = packageNodes
        .map(pn => Paths.get(outputPath.getAbsolutePath, pn: _*))
        .getOrElse(Paths.get(outputPath.getAbsolutePath))
        .resolve(file)
      val params: Array[String] = Array(firstArg,
                                        scPath.toString,
                                        "-p",
                                        optDefaultPackageName.getOrElse(""),
                                        "-o",
                                        outputPath.getAbsolutePath)
      TruffleJsonFunctionWrapperGenerator.main(params)
      outputFile.toFile
    }
    generatedOutputFile
  }

  /**
	  * Process contract files
	  *
	  * @param contractFiles The contract files to generate Java classes from
	  * @param useJavaNativeTypes Use Java types if true, Solidity types if false
	  * @param outputDir The directory to output the generated classes to
	  * @return
	  */
  private def process(contractFiles: List[AbiBin],
                      useJavaNativeTypes: Boolean,
                      outputDir: File): Seq[File] = {
    val generator = new SolidityFunctionWrapper(useJavaNativeTypes)

    val files: Seq[Option[File]] = for (contract <- contractFiles) yield {
      try {
        val bin = readFile(contract.bin)
        val abi = readFile(contract.abi)

        generator.generateJavaFiles(
          contract.name,
          bin,
          abi,
          outputDir.toString,
          contract.packageName
        )

        Some(contract.newLocation(outputDir))
      } catch {
        case e @ (_: IOException | _: ClassNotFoundException) =>
          println(e.getMessage)
          None
      }
    }

    files.flatten[File]
  }

  @throws(classOf[IOException])
  private def readFile(path: Path): String = {
    new String(java.nio.file.Files.readAllBytes(path), StandardCharsets.UTF_8)
  }
}
