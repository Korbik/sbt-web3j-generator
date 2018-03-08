package nl.anchormen.sbt

import java.io.{File, IOException, UncheckedIOException}
import java.nio.file.{Files, Path, Paths}
import java.util.regex.Pattern

object AbiBin {
	val AbiExtension: String = ".abi"
	val BinExtension: String = ".bin"
	val ExtensionLength: Int = 4

	def findList(f: File): List[AbiBin] = {
		findListImpl(f.toPath)
	}

	def findList(path: Path): List[AbiBin] = {
		findListImpl(path)
	}

	def convertPathToPackageWithFileName(path: Path): String = {
		val nameCount: Int = path.getNameCount

		val pathNames: Seq[Path] = for (index <- 0 until nameCount) yield {
			val name: Path = path.getName(index)

			if (index == nameCount - 1) {
				Paths.get(removeExtension(name.toString))
			} else {
				name
			}
		}

		pathNames.mkString(".")
	}

	def getFileNameFromPackageName(packageName: String): String = {
		packageName.split(Pattern.quote(".")).last
	}

	private def hasCorrectExtension(path: Path): Boolean = {
		path.getFileName.toString.endsWith(AbiExtension) || path.getFileName.toString.endsWith(BinExtension)
	}

	private def findListImpl(basePath: Path): List[AbiBin] = {
		val baseSearchPath = basePath.toAbsolutePath

		try {
			val pf: PartialFunction[Path, (String, Path)] = {
				case p: Path => {
					val relativePath: Path = baseSearchPath.getParent.relativize(p)
					(convertPathToPackageWithFileName(relativePath), p)
				}
			}

			import scala.collection.JavaConverters._

			Files.walk(baseSearchPath)
					.iterator()
					.asScala
					.filter(hasCorrectExtension)
					//Collect the file paths based on their package name, creating a tuple (name, path)
					.collect(pf)
					.toSeq
					//Group by package name
					.groupBy(_._1)
					.mapValues(_.map(_._2))
					//Filter on map entries that have 2 values, since we want both a .abi and a .bin file
					.filter(k => k._2.lengthCompare(2) == 0)
					.map {
						entry: (String, Seq[Path]) => new AbiBin(entry._1, entry._2.head, baseSearchPath)
					}.toList
		} catch {
			case ex: IOException => throw new UncheckedIOException(ex)
		}
	}

	private def removeExtension(name: String) = {
		name.substring(0, name.length - ExtensionLength)
	}
}

class AbiBin(val packageWithFileName: String, val abiBinPath: Path, val searchBase: Path) {
	lazy private val _name: String = AbiBin.getFileNameFromPackageName(packageWithFileName)
	lazy private val _packageName: String = getPackageNameFromPackageWithFileName(packageWithFileName)
	lazy private val _basePath: Path = abiBinPath.getParent
	lazy private val _abi: Path = _basePath.resolve(_name + AbiBin.AbiExtension)
	lazy private val _bin: Path = _basePath.resolve(_name + AbiBin.BinExtension)

	def name: String = _name
	def packageName: String = _packageName
	def abi: Path = _abi
	def basePath: Path = _basePath
	def bin: Path = _bin

	def isValid: Boolean = Files.isRegularFile(_abi) && Files.isRegularFile(_bin)
	def newLocation(out: File): File = out.toPath.resolve(relativePath).resolve(_name + ".java").toFile

	private lazy val relativePath: Path = {
		val path = searchBase.relativize(_basePath)
		val initialPathName = searchBase.getName(searchBase.getNameCount - 1)

		if (Files.isSameFile(searchBase.toAbsolutePath, _basePath.toAbsolutePath)) {
			initialPathName
		} else {
			initialPathName.resolve(path)
		}
	}

	private def getPackageNameFromPackageWithFileName(packageWithFileName: String): String = {
		packageWithFileName.split(Pattern.quote(".")).dropRight(1).mkString(".")
	}
}
