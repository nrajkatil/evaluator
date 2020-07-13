package evaluator;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

public class Evaluator {
  private XPathSelector evaluator;

  private String xpath;

  Evaluator(XPathSelector evaluator, String xpath) {
    this.evaluator = evaluator;
    this.xpath = xpath;
  }

  public XPathSelector getEvaluator() {
    return this.evaluator;
  }

  public String getXPath() {
    return this.xpath;
  }

  public XdmValue evaluate() throws SaxonApiException {
    return this.evaluator.evaluate();
  }

  public void setContextItem(XdmItem item) throws SaxonApiException {
    this.evaluator.setContextItem(item);
  }
}
