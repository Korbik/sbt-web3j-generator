package nl.anchormen.sbt

import org.web3j.codegen.SolidityFunctionWrapperGenerator
import sbt.Keys._
import sbt.{AutoPlugin, Def, PluginTrigger, _}

object Web3JGeneratorPlugin extends AutoPlugin {
	override def requires: Plugins = plugins.JvmPlugin
	override def trigger: PluginTrigger = allRequirements

	object autoImport {
		lazy val web3JGenerateWrapper = taskKey[Unit]("Generate Java wrapper classes")
		lazy val web3JOutputPath = settingKey[File]("The directory to output the generated classes")
		lazy val web3JSmartContracts = settingKey[File]("The directory containing the smart contracts")
		lazy val web3JUseJavaNativeTypes = settingKey[Boolean]("Use Java native types or Solidity types")
	}

	import autoImport._

	lazy val baseWeb3JSettings: Seq[Setting[_]] = Seq(
		web3JOutputPath := baseDirectory.value / "src" / "main" / "java",
		web3JSmartContracts := baseDirectory.value / "src" / "main" / "contracts",
		web3JUseJavaNativeTypes := true,
		web3JGenerateWrapper := generateWrapper.value
	)

//	override lazy val projectSettings = inConfig(Compile)(
//		sourceGenerators in Compile += Def.task {
//			val generated = Web3JGenerate(web3jContractsPath.value, web3jOutputPath.value, web3jUseJavaNativeTypes.value)
//			streams.value.log.info("Generated sources:\r\n" + generated.mkString("\r\n"))
//			generated
//		}.taskValue
//	) ++ pluginSettings

	override lazy val projectSettings: Seq[Setting[_]] = baseWeb3JSettings

	lazy val generateWrapper: Def.Initialize[Task[Unit]] = Def.task {
		Generate(web3JOutputPath.value, web3JSmartContracts.value, web3JUseJavaNativeTypes.value)
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
	def apply(outputPath: File, smartContracts: File, useJavaNativeTypes: Boolean): Unit = {
		val contractFiles = AbiBin.findList(smartContracts)
		process(outputPath, contractFiles)
	}

//	def process(contractFiles: List[AbiBin], useJavaNativeTypes: Boolean, outputDir: File): Seq[File] = {
//		val files = ListBuffer[File]()
//		val generator = new SolidityFunctionWrapper(useJavaNativeTypes)
//		for (contract <- contractFiles) {
//			val bin = new String(java.nio.file.Files.readAllBytes(contract.bin), StandardCharsets.UTF_8)
//			val abi = new String(java.nio.file.Files.readAllBytes(contract.abi), StandardCharsets.UTF_8)
//			try {
//				generator.generateJavaFiles(
//					contract.name,
//					bin,
//					abi,
//					outputDir.toString,
//					contract.packageName
//				)
//				files += contract.newLocation(outputDir)
//			} catch {
//				case e: Exception => Log.error(e.getMessage)
//			}
//		}
//		return files.toList
//	}

	/**
	  * Process the contract
	  *
	  * @param outputDir
	  * @param contractFiles
	  */
	def process(outputDir: File, contractFiles: List[AbiBin]): Unit = {
				println("Processing contract files")

				for (contract <- contractFiles) {
						println(s"Processing ${contract.bin.toFile.getName}, ${contract.abi.toFile.getName}")

						val arguments: Seq[String] = getArguments(
							contract.bin.toFile.getAbsolutePath,
							contract.abi.toFile.getAbsolutePath,
							outputDir.getAbsolutePath,
							contract.packageName
						)

						try {
							println(arguments.mkString(", "))
							SolidityFunctionWrapperGenerator.run(arguments.toArray)
						} catch {
							case e: Exception => println(e.getMessage)
						}

				}

				println("Finished processing contract files")
	}

	/**
	  * Get arguments to supply to SolidityFunctionWrapperGenerator
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
