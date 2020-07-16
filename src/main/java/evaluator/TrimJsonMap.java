package evaluator;

import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmValue;

/**
 * XPath extension function that trims the input string to the largest valid JSON map starting from
 * the first open brace found.
 *
 * pin:trim-json-map("...({\"a\": {\"b\": [1,, 2]}})...{}") == {\"a\": {\"b\": [1,, 2]}}
 *
 * TODO: Currently this fails for maps with strings that contain braces. To fix this we'd need to
 * use a proper JSON tokenizer.
 */
public class TrimJsonMap implements ExtensionFunction {
  public QName getName() {
    return new QName("http://www.pinterest.com/", "trim-json-map");
  }

  public SequenceType getResultType() {
    return SequenceType.makeSequenceType(
        ItemType.STRING, OccurrenceIndicator.ONE
    );
  }

  public net.sf.saxon.s9api.SequenceType[] getArgumentTypes() {
    return new SequenceType[]{
        SequenceType.makeSequenceType(
            ItemType.STRING, OccurrenceIndicator.ONE)};
  }

  public XdmValue call(XdmValue[] arguments) throws SaxonApiException {
    String arg = arguments[0].itemAt(0).getStringValue();
    int start = arg.indexOf("{");
    if (start >= 0) {
      int end = start;
      int numBrackets = 0;
      while (end < arg.length()) {
        if (arg.charAt(end) == '{') {
          numBrackets += 1;
        } else if (arg.charAt(end) == '}') {
          numBrackets -= 1;
        }
        if (numBrackets == 0) {
          break;
        } else {
          end += 1;
        }
      }
      if (numBrackets == 0) {
        return new XdmAtomicValue(arg.substring(start, end + 1));
      }
    }
    throw new IllegalArgumentException("Cannot find valid JSON map in : " + arg);
  }
}
