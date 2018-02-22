package nl.anchormen.sbt

import java.nio.charset.StandardCharsets

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.web3j.codegen.{SolidityFunctionWrapper, SolidityFunctionWrapperGenerator}
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbt.{AutoPlugin, PluginTrigger, _}

import scala.collection.mutable.ListBuffer

object Web3JGeneratorPlugin extends AutoPlugin {
  override def requires: JvmPlugin.type = plugins.JvmPlugin

  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val web3jGenerateWrapper = TaskKey[Unit]("web3j-generate-wrapper", "Generate Java wrapper classes")
    val web3jOutputPath = SettingKey[File]("web3j-output-path", "The directory to output the generated classes")
    val web3jContractsPath = SettingKey[File]("web3j-contract-path", "The directory containing the smart contracts")
    val web3jUseJavaNativeTypes = SettingKey[Boolean]("web3j-use-java-native-type", "Use Java native types or Solidity types")
  }

  import autoImport._

  lazy val pluginSettings: Seq[Setting[_]] = Seq(
    web3jOutputPath := baseDirectory.value / "src" / "java",
    web3jContractsPath := baseDirectory.value / "src" / "abi",
    web3jUseJavaNativeTypes := true
  )

  override lazy val projectSettings = inConfig(Compile)(
    sourceGenerators in Compile += Def.task {
      val generated = Web3JGenerate(web3jContractsPath.value, web3jOutputPath.value, web3jUseJavaNativeTypes.value)
      streams.value.log.info("Generated sources:\r\n" + generated.mkString("\r\n"))
      generated
    }.taskValue
  ) ++ pluginSettings
}

object Web3JGenerate {
  val Log: Logger = LoggerFactory.getLogger("Web3JGeneratorPlugin")

  /**
    * Generate Java classes from smart contract files.
    *
    * @param smartContracts     Directory containing *.bin and *.abi files to be converted
    * @param outputPath         The path to output the generated classes
    * @param useJavaNativeTypes Use java native types in the generated classes if set to true, use Solidity types if
    *                           set to false
    */
  def apply(smartContracts: File, outputPath: File, useJavaNativeTypes: Boolean): Seq[File] = {
    import scala.collection.JavaConverters._

    val contractFiles = AbiBin.findList(smartContracts).asScala.toList

    process(contractFiles, useJavaNativeTypes, outputPath)
  }

  def process(contractFiles: List[AbiBin], useJavaNativeTypes: Boolean, outputDir: File): Seq[File] = {
    val files = ListBuffer[File]()
    val generator = new SolidityFunctionWrapper(useJavaNativeTypes)
    for (contract <- contractFiles) {
      val arguments: Seq[String] = getArguments(
        contract.bin.toFile.getAbsolutePath,
        contract.abi.toFile.getAbsolutePath,
        outputDir.getAbsolutePath,
        contract.packageName
      )
      val bin = new String(java.nio.file.Files.readAllBytes(contract.bin), StandardCharsets.UTF_8)
      val abi = new String(java.nio.file.Files.readAllBytes(contract.abi), StandardCharsets.UTF_8)
      try {
        generator.generateJavaFiles(
          contract.name,
          bin,
          abi,
          outputDir.toString,
          contract.packageName
        )
        files += contract.newLocation(outputDir)
      } catch {
        case e: Exception => Log.error(e.getMessage)
      }
    }
    return files.toList
  }


  /**
    * org.web3j.codegen.SolidityFunctionWrapperGenerator /path/to/<smart-contract>.bin /path/to/<smart-contract>.abi -o /path/to/src/main/java -p com.your.organisation.name
    *
    * @param binaryPath
    * @param absPath
    * @param outputDir
    * @param packageName
    * @return
    */
  private def getArguments(binaryPath: String, absPath: String, outputDir: String, packageName: String): Seq[String] = {
    Seq("generate", binaryPath, absPath, "-o", outputDir, "-p", packageName)
  }
}
