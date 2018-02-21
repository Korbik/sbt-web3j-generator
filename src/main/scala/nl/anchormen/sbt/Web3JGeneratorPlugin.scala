package nl.anchormen.sbt

import org.web3j.codegen.SolidityFunctionWrapperGenerator
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbt.{AutoPlugin, PluginTrigger, _}

object Web3JGeneratorPlugin extends AutoPlugin {
	override def requires: JvmPlugin.type = plugins.JvmPlugin
	override def trigger: PluginTrigger = allRequirements

	object autoImport {
		lazy val generateWrapper = TaskKey[Unit]("generate-wrapper", "Generate Java wrapper classes")
		lazy val outputPath = SettingKey[File]("output-path", "The directory to output the generated classes")
		lazy val packageName = SettingKey[String]("package-name", "The package name for the generated classes")
		lazy val smartContracts = SettingKey[File]("smart-contracts", "The directory containing the smart contracts")
		lazy val useJavaNativeTypes = SettingKey[Boolean]("use-java-native-type", "Use Java native types or Solidity types")
	}

	import autoImport._

	lazy val baseSettings: Seq[Setting[_]] = Seq(
		outputPath := baseDirectory.value / "src" / "java",
		packageName := s"${organization.value}.web3j.generated",
		smartContracts := baseDirectory.value / "src" / "contracts",
		useJavaNativeTypes := true,
		generateWrapper := Generate(outputPath.value, packageName.value, smartContracts.value, useJavaNativeTypes.value)
	)

	override lazy val projectSettings: Seq[Setting[_]] = inConfig(Compile)(baseSettings)
}

object Generate {
	/**
	  * Generate Java classes from smart contract files
	  *
	  * @param outputPath The path to output the generated classes
	  * @param packageName The package name for the the generated classes
	  * @param smartContracts Directory containing *.bin and *.abi files to be converted
	  * @param useJavaNativeTypes Use java native types in the generated classes if set to true, use Solidity types if
	  *                           set to false
	  */
	def apply(outputPath: File, packageName: String, smartContracts: File, useJavaNativeTypes: Boolean): Unit = {
		val outputDir: File = getPackagePath(outputPath, packageName)
		val contractFiles: Map[String, Seq[File]] = groupAndFilterContractFiles(smartContracts)
		process(outputDir, packageName, contractFiles)
	}

	def process(outputDir: File, packageName: String, contractFiles: Map[String, Seq[File]]): Unit = {
		val thread: Thread = new Thread(
			() => {
				for (values <- contractFiles.values) {
					val binaryFile = getBinaryFile(values)
					val absFile = getAbsFile(values)

					if (binaryFile.isDefined && absFile.isDefined) {
						val arguments: Seq[String] = getArguments(binaryFile.get.getAbsolutePath,
							absFile.get.getAbsolutePath,
							outputDir.getAbsolutePath,
							packageName)

						SolidityFunctionWrapperGenerator.run (arguments.toArray)
					}
				}
			}
		)

		thread.start()
	}

	/**
	  * Groups binary and abs files by their filenames
	  *
	  * @param smartContracts The path to the directory containing the smart contracts
	  * @return
	  */
	private def groupAndFilterContractFiles(smartContracts: File): Map[String, Seq[File]] = {
		val files: Seq[File] = findContractFiles(smartContracts)
		val fileNames: Seq[(String, File)] = filterContractFileNames(files)

		fileNames.groupBy(_._1)
				.mapValues(_.map(_._2))
				.filter(k => k._2.lengthCompare(2) == 0)
	}

	/**
	  * This function takes a package name such as nl.anchormen.sbt, and returns a file with path
	  * <basePath>/nl/anchormen/sbt
	  */
	private def getPackagePath(basePath: File, packageName: String): File = {
		packageName.split(".")
				.foldLeft(basePath)((a: File, b: String) => a / b)
	}

	private def findBinaryFiles(contractPath: File): Seq[File] = {
		val finder: PathFinder = contractPath ** "*.bin"
		finder.get
	}

	private def getBinaryFile(contractFiles: Seq[File]): Option[File] = {
		contractFiles.find(file => hasFileExtension(file, "bin"))
	}

	private def getAbsFile(contractFiles: Seq[File]): Option[File] = {
		contractFiles.find(file => hasFileExtension(file, "abi"))
	}

	private def hasFileExtension(file: File, extension: String): Boolean = {
		getFileExtension(file) match {
			case Some(`extension`) => true
			case _ => false
		}
	}

	private def findAbsFiles(contractPath: File): Seq[File] = {
		val finder: PathFinder = contractPath ** "*.abi"
		finder.get
	}

	private def findContractFiles(contractPath: File): Seq[File] = {
		findBinaryFiles(contractPath) ++ findAbsFiles(contractPath)
	}

	/**
	  * Returns a list of tuples consisting of the file name without extension and the corresponding file. Only files
	  * that have a non-empty filename (with or without extension) are returned
	  *
	  * @param files The input files
	  * @return The filtered list of filenames mapped to their corresponding files
	  */
	private def filterContractFileNames(files: Seq[File]): Seq[(String, File)] = {
		for {
			file <- files
			fileName <- getFileName(file)
			if fileName.nonEmpty
		} yield (fileName, file)
	}

	private def getFileName(file: File): Option[String] = {
		val filename = file.getName
				.split(".")
				.dropRight(1)
	        	.mkString(".")

		Option(filename)
	}

	private def getFileExtension(file: File): Option[String] = {
		file.getName
				.split(".")
				.lastOption
	}

	/**
	  * org.web3j.codegen.SolidityFunctionWrapperGenerator /path/to/<smart-contract>.bin /path/to/<smart-contract>.abi -o /path/to/src/main/java -p com.your.organisation.name
	  *
	  * @param binaryPath
	  * @param absPath
	  * @param outputPath
	  * @param packageName
	  * @return
	  */
	private def getArguments(binaryPath: String, absPath: String, outputPath: String, packageName: String): Seq[String] = {
		Seq("generate", binaryPath, absPath, "-o", outputPath, "-p", packageName)
	}
}
