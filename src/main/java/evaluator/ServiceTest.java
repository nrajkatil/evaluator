package evaluator;

import net.sf.saxon.s9api.XdmNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ServiceTest {
    public static void main(String[] args) throws IOException {
        App app = new App();
        System.out.println("Working Directory = " +
                System.getProperty("user.dir"));
        String htmlCacheFolderName = "~/HTMLCacheDir";

        //Initialize the on-disk HTML cache

        File templateFile = new File("src/test/resources/title_template.yaml");
        File urlFile = new File("url.txt");

        Template template = app.loadTemplate(templateFile, "product");
        List<String>
                rulesNames =
                template.rules.stream().map(rule -> rule.name).collect(Collectors.toList());
        rulesNames.add(0, "url");
        System.out.println(String.join("\t", rulesNames));
        try (Stream<String> lines = Files.lines(urlFile.toPath())) {
            lines.forEachOrdered(line -> {
                if (!line.trim().isEmpty()) {
                    XdmNode node = app.fetchDocument(line);
                    if (node != null) {
                        List<String> results = app.applyTemplate(line, template, node);
                        if (results != null) {
                            System.out.println(String.join("\t", results));
                        } else {
                            System.err.println(
                                    "URL " + line + " did not match template url regex: " + template.pattern);
                        }
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
