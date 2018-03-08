package nl.anchormen.sbt

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path

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

	override lazy val projectSettings: Seq[Setting[_]] = inConfig(Compile)(
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
	private def process(contractFiles: List[AbiBin], useJavaNativeTypes: Boolean, outputDir: File): Seq[File] = {
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
				case e @ (_ : IOException | _ : ClassNotFoundException) =>
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
