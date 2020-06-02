package evaluator;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.sf.saxon.functions.ConstantFunction;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import picocli.CommandLine;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.xml.transform.stream.StreamSource;


@Command(
        description = "Fetches HTML for the supplied URLs and applies the provided template",
        name = "template_eval",
        mixinStandardHelpOptions = true,
        version = "0.1")
public class App implements Callable<Void> {

    private static final Processor processor = new Processor(false);

    @Parameters(index = "0", description = "File containing sample URLs. One URL per line")
    private File inputFile;

    @Parameters(index = "1", description = "Template file")
    private File templateFile;

    @Parameters(index = "2", description = "Folder for HTML cache")
    private String htmlCacheFolderName;

    @Parameters(index = "3", description = "Page type to extract: {product, general}",
            defaultValue = "product")
    private String type;

    @Option(names = "js_rendering", description = "Apply Js rendering", defaultValue = "False")
    private boolean js_rendering;

    @Option(names = "raw_html", description = "Use downloaded Raw Html File", defaultValue = "False")
    private boolean use_raw_html;

    private HTMLCache htmlCache = null;
    private final String PINTEREST_USER_AGENT = "Mozilla/5.0 (compatible; Pinterestbot/1.0; +http://www.pinterest.com/bot.html)";
    private final String BROWSER_USER_AGENT = "Mozilla/5.0";

    public static void main(String[] args) {
        CommandLine.call(new App(), args);
    }

    @Override
    public Void call() throws Exception {
        //Initialize the on-disk HTML cache
        htmlCache = new HTMLCache(htmlCacheFolderName);

        Template template = loadTemplate(templateFile, type);
        List<String>
                rulesNames =
                template.rules.stream().map(rule -> rule.name).collect(Collectors.toList());
        rulesNames.add(0, "url");
        System.out.println(String.join("\t", rulesNames));

        if (!use_raw_html) {
            try (Stream<String> lines = Files.lines(inputFile.toPath())) {
                lines.forEachOrdered(line -> {
                    if (!line.trim().isEmpty()) {
                        XdmNode node = fetchDocument(line);
                        if (node != null) {
                            List<String> results = applyTemplate(line, template, node);
                            if (results != null) {
                                System.out.println(String.join("\t", results));
                            } else {
                                System.err.println(
                                        "URL " + line + " did not match template url regex: " + template.pattern);
                            }
                        }
                    }
                });
            }
        }

        if (use_raw_html) {
            if (inputFile.isDirectory()) {
                //System.out.println(inputFile.getName() + " is a directory containing the files: \n");
                File[] HTMLfiles = inputFile.listFiles();
                for (File file: HTMLfiles) {
                    if (file.isDirectory()) {
                        System.out.println("Skipping directory " + file.getName());
                    } else {                
                        //System.out.println(file.getCanonicalPath());
                        processRawHTML(file, template);                    
                    }
                }                
            } else if (inputFile.isFile()) {
                //System.out.println(inputFile.getName() + " is a file\n");
                processRawHTML(inputFile, template);
            }            
        }

        return null;
    }


    public static XdmNode buildNode(byte[] html) throws SaxonApiException {
        processor.setConfigurationProperty(
                Feature.SOURCE_PARSER_CLASS, "org.ccil.cowan.tagsoup.Parser");
        processor.setConfigurationProperty(
                Feature.ENTITY_RESOLVER_CLASS, "evaluator.NoOpEntityResolver");
        return processor.newDocumentBuilder().build(
                new StreamSource(new ByteArrayInputStream(html)));
    }

    //Fetch the URL's HTML contents, either from HTML cache-on-disk, or from the internet.
    //Also, store a copy of the HTML contents on the cache, if it's enabled.
    //Returns the document (or null, in case of error).
    XdmNode fetchDocument(String url) {
        byte[] html = null;

        if (htmlCache.enabled()) {
            html = htmlCache.fetchDocument(url);
        }

        if (html == null) {
            try {
                html = innerFetch(url, true, true);
            } catch (MalformedURLException e) {
                System.err.println("URL: " + url + " is not a valid URL: " + e.getMessage());
                return null;
            } catch (IOException e) {
                System.err.println("Failed to fetch HTML (certificate validation on) from " + url + " : "
                        + e.getMessage());
            }
            if (html == null) {
                try {
                    html = innerFetch(url, false, true);
                    System.err.println("Successfully fetched HTML from " + url
                            + " with certificate validation off");
                } catch (IOException e) {
                    System.err.println("Failed to fetch HTML (certificate validation off) from " + url + " : "
                            + e.getMessage());
                }
            }
            if (html == null) {
                try {
                    html = innerFetch(url, false, false);
                    System.err.println("Successfully fetched HTML from " + url
                            + " with None Pinterest User Agent");
                } catch (IOException e) {
                    System.err.println("Failed to fetch HTML (certificate validation off) with None Pinterest User Agent from " + url + " : "
                            + e.getMessage());
                }
            }
        }

        if (html == null) {
            System.err.println("Failed to fetch HTML from " + url);
            return null;
        }

        try {
            return buildNode(html);
        } catch (SaxonApiException e) {
            System.err.println("Failed to parse HTML from " + url + " : "
                    + e.getMessage());
            return null;
        }
    }

    private byte[] innerFetch(String url, boolean validateCertificates, boolean usePinterestUserAgent) throws IOException {
        String userAgent = PINTEREST_USER_AGENT;
        if (!usePinterestUserAgent) {
            userAgent = BROWSER_USER_AGENT;
        }
        byte[] html;
        if (js_rendering) {
            html = fetchWithNode(url);
        } else {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(
                            userAgent)
                    .validateTLSCertificates(validateCertificates)
                    .method(Connection.Method.GET)
                    .execute();
            html = response.bodyAsBytes();
        }

        if (htmlCache.enabled()) {
            htmlCache.insertDocument(url, html);
        }
        return html;
    }

    private byte[] fetchWithNode(String url) {
        ProcessBuilder pb = new ProcessBuilder("node", "src/main/node/index.js", url);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        try {
            Process p = pb.start();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringJoiner sj = new StringJoiner(System.getProperty("line.separator"));
            reader.lines().iterator().forEachRemaining(sj::add);
            String result = sj.toString();
            p.waitFor();
            p.destroy();
            return result.getBytes();
        } catch (Exception e) {
            return null;
        }
    }

    List<String> applyTemplate(String url, Template template, XdmNode node) {
        List<String> results = null;


        if (url == null || template.urlMatch.test(url)) {
            results = new ArrayList<>();
            results.add(url);
            for (Rule rule : template.rules) {
                XdmValue result;
                try {
                    rule.evaluator.setContextItem(node);
                    result = rule.evaluator.evaluate();
                } catch (SaxonApiException e) {
                    System.err.println("Failed to evaluate XPath " + rule.xPath +
                            " on " + url + ":" + e.getMessage());
                    results.add("");
                    continue;
                }
                if (result.size() == 0) {
                    results.add("");
                } else if ("LIST_TEXT".equals(rule.outputFormat)) {
                    results.add(StreamSupport.stream(result.spliterator(), false)
                            .map(item -> normalizeWhitespace(item.getStringValue()))
                            .collect(Collectors.joining(",")));
                } else if ("TEXT".equals(rule.outputFormat)) {
                    results.add(normalizeWhitespace(result.itemAt(0).getStringValue()));
                }
            }
        }
        return results;
    }

    private String normalizeWhitespace(String input) {
        return input.trim().replaceAll("\\s+", " ");
    }

    Template loadTemplate(File templateFile, String type) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Template template = mapper.readValue(templateFile, Template.class);
        XPathCompiler compiler = processor.newXPathCompiler();
        compiler.declareNamespace("", "http://www.w3.org/1999/xhtml");
        compiler.setLanguageVersion("3.1");
        template.validateAndInitialize(compiler, type);
        return template;
    }


    private void processRawHTML(File htmlFile, Template template) throws IOException
    {
        byte[] htmlBytes = Files.readAllBytes(htmlFile.toPath());
        try {
            XdmNode node = buildNode(htmlBytes);
            if (node != null) {
                List<String> results = applyTemplate(null, template, node);
                if (results != null) {
                    if (results.get(0) == null) {
                        results.set(0, htmlFile.getName());
                    }
                    System.out.println(String.join("\t", results));
                }
            }
        } catch (SaxonApiException e) {
            System.err.println("Failed to parse HTML from file " + htmlFile.getName() + " "
                    + e.getMessage());            
        }
    }
}
