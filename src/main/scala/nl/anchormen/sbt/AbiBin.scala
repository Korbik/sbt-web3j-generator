package nl.anchormen.sbt

import java.io.{File, IOException, UncheckedIOException}
import java.nio.file.{Files, Path}

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

	private def findListImpl(basePath: Path): List[AbiBin] = {
		val baseSearchPath = basePath.toAbsolutePath

		try {
			import scala.collection.JavaConverters._

			val pf: PartialFunction[Path, (String, Path)] = {
				case p: Path => (removeExtension(p.toFile.getName), p)
			}

			Files.walk(baseSearchPath)
					.iterator()
					.asScala
					.filter(path => path.getFileName.toString.endsWith(AbiExtension) || path.getFileName.toString.endsWith(BinExtension))
					.collect(pf)
					.toSeq
					.groupBy(_._1)
					.mapValues(_.map(_._2))
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

class AbiBin(val name: String, val abiBinPath: Path, val searchBase: Path) {
	lazy private val _basePath: Path = abiBinPath.getParent
	lazy private val _abi: Path = _basePath.resolve(name + AbiBin.AbiExtension)
	lazy private val _bin: Path = _basePath.resolve(name + AbiBin.BinExtension)

	def abi: Path = _abi
	def basePath: Path = _basePath
	def bin: Path = _bin

	lazy val packageName: String = {
		val pathNames = for (i <- 0 until relativePath.getNameCount) yield relativePath.getName(i)
		pathNames.mkString(".")
	}

	def isValid: Boolean = Files.isRegularFile(_abi) && Files.isRegularFile(_bin)

	def newLocation(out: File): File = out.toPath.resolve(relativePath).resolve(name + ".java").toFile

	private lazy val relativePath: Path = {
		val path = searchBase.relativize(_basePath)
		val initialPathName = searchBase.getName(searchBase.getNameCount - 1)

		if (Files.isSameFile(searchBase.toAbsolutePath, _basePath.toAbsolutePath)) {
			initialPathName
		} else {
			initialPathName.resolve(path)
		}
	}
}
