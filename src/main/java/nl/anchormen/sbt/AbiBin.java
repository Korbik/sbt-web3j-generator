package nl.anchormen.sbt;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AbiBin
{

    private final Path searchBase;

    public final String name;
    public final Path basePath;


    private Path abi;
    private Path bin;
    private Path relativePath;

    public AbiBin(Path searchBase, Path abiBinPath)
    {
        this.searchBase = searchBase;
        this.basePath = abiBinPath.getParent();
        this.name = removeExtension(abiBinPath.getFileName().toString());
    }

    public Path abi()
    {
        if (this.abi == null)
        {
            this.abi = this.basePath.resolve(this.name + ".abi");
        }
        return this.abi;
    }

    public Path bin()
    {
        if (this.bin == null)
        {
            this.bin = this.basePath.resolve(this.name + ".bin");
        }
        return this.bin;
    }

    public Path relativePath()
    {
        if (this.relativePath == null)
        {
            this.relativePath = this.searchBase.relativize(this.basePath);
        }
        return this.relativePath;
    }

    public String packageName()
    {
        Path p = relativePath();
        int l = p.getNameCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l; i++)
        {
            sb.append(p.getName(i));
            if (i < l - 1)
            {
                sb.append('.');
            }
        }
        return sb.toString();
    }


    public boolean isValid()
    {
        return Files.isRegularFile(abi()) && Files.isRegularFile(bin());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        AbiBin abiBin = (AbiBin) o;
        return Objects.equals(name, abiBin.name) &&
                Objects.equals(basePath, abiBin.basePath);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, basePath);
    }

    public static Stream<AbiBin> find(Path basePath)
    {
        Path ensureFullPath = basePath.toAbsolutePath();
        try
        {
            return Files.walk(ensureFullPath)
                    .filter(p -> p.getFileName().toString().endsWith(".abi") || p.getFileName().toString().endsWith(".bin"))
                    .map(p -> new AbiBin(ensureFullPath, p))
                    .distinct()
                    .filter(AbiBin::isValid);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
    }

    public static List<AbiBin> findList(File f)
    {
        return findList(f.toPath());
    }

    public static List<AbiBin> findList(Path path)
    {
        return find(path)
                .collect(Collectors.toList());
    }

    private static String removeExtension(String name)
    {
        return name.substring(0, name.length() - 4);
    }
}
