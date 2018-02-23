package nl.anchormen.sbt

import java.nio.charset.StandardCharsets

import org.web3j.codegen.SolidityFunctionWrapper
import sbt.Keys._
import sbt.{AutoPlugin, Def, PluginTrigger, _}

object Web3JGeneratorPlugin extends AutoPlugin {
	override def requires: Plugins = plugins.JvmPlugin
	override def trigger: PluginTrigger = allRequirements

	object autoImport {
		lazy val web3JGenerateWrapper = taskKey[Seq[File]]("Generate Java wrapper classes")
		lazy val web3JOutputPath = settingKey[File]("The directory to output the generated classes")
		lazy val web3JContractsPath = settingKey[File]("The directory containing the smart contracts")
		lazy val web3JUseJavaNativeTypes = settingKey[Boolean]("Use Java native types or Solidity types")
	}

	import autoImport._

	lazy val pluginSettings: Seq[Setting[_]] = Seq(
		web3JOutputPath := baseDirectory.value / "src" / "main" / "java",
		web3JContractsPath := baseDirectory.value / "src" / "main" / "contracts",
		web3JUseJavaNativeTypes := true
	)

	override lazy val projectSettings = inConfig(Compile)(
		sourceGenerators in Compile += Def.task {
			lazy val sources = generateWrapper.value
			streams.value.log.info("Generated sources:\r\n" + sources.mkString("\r\n"))
			sources
		}.taskValue
	) ++ pluginSettings

	lazy val generateWrapper: Def.Initialize[Task[Seq[File]]] = Def.task {
		Generate(web3JContractsPath.value, web3JUseJavaNativeTypes.value, web3JOutputPath.value)
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
	def apply(smartContracts: File, useJavaNativeTypes: Boolean, outputPath: File): Seq[File] = {
		val contractFiles = AbiBin.findList(smartContracts)
		process(contractFiles, useJavaNativeTypes, outputPath)
	}

	/**
	  * Process contract files
	  *
	  * @param contractFiles The contract files to generate Java classes from
	  * @param useJavaNativeTypes Use Java types if true, Solidity types if false
	  * @param outputDir The directory to output the generated classes to
	  * @return
	  */
	def process(contractFiles: List[AbiBin], useJavaNativeTypes: Boolean, outputDir: File): Seq[File] = {
		val generator = new SolidityFunctionWrapper(useJavaNativeTypes)

		val files: Seq[Option[File]] = for (contract <- contractFiles) yield {
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

				Some(contract.newLocation(outputDir))
			} catch {
				case e: Exception =>
					println(e.getMessage)
					None
			}
		}

		files.flatten[File]
	}
}
