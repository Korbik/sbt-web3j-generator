package nl.anchormen.sbt

import java.io.{BufferedReader, IOException, InputStreamReader}

import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbt.{AutoPlugin, PluginTrigger, _}

object Web3JSolidityPlugin extends AutoPlugin {
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

		for (values <- contractFiles.values) {
			val arguments: Seq[String] = getArguments(binaryPath = values.head.absolutePath,
				absPath = values.last.absolutePath,
				outputDir.getAbsolutePath,
				packageName)

			process(arguments, smartContracts)
		}
	}

	def process(arguments: Seq[String], smartContracts: File): Unit = {
		import scala.collection.JavaConverters._
		val builder = new ProcessBuilder(arguments.toList.asJava)
		builder.directory(smartContracts)

		val process = builder.start
		val is = process.getInputStream
		val isr = new InputStreamReader(is)
		val br = new BufferedReader(isr)
		var line: String = null

		try {
			while ({line = br.readLine; line != null}) {
				System.out.println(line)
			}
		} catch {
			case e: IOException => System.out.println(e.getMessage)
		}
	}

	/**
	  * Groups binary and abs files by their filenames
	  *
	  * @param smartContracts The path to the directory containing the smart contracts
	  * @return
	  */
	private def groupAndFilterContractFiles(smartContracts: File): Map[String, Seq[File]] = {
		val files: Seq[File] = getContractFiles(smartContracts)
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

	private def getBinaryFiles(contractPath: File): Seq[File] = {
		val finder: PathFinder = contractPath ** "*.bin"
		finder.get
	}

	private def getAbsFiles(contractPath: File): Seq[File] = {
		val finder: PathFinder = contractPath ** "*.abi"
		finder.get
	}

	private def getContractFiles(contractPath: File): Seq[File] = {
		getBinaryFiles(contractPath) ++ getAbsFiles(contractPath)
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
		file.getName
				.split(".")
				.drop(1)
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
		Seq("org.web3j.codegen.SolidityFunctionWrapperGenerator", binaryPath, absPath, "-o", outputPath, "-p", packageName)
	}
}
